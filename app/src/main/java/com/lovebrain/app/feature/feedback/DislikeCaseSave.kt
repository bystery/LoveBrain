package com.lovebrain.app.feature.feedback

import com.lovebrain.app.model.FeedbackCase
import kotlinx.coroutines.CancellationException

/**
 * 点踩落盘这一步**真正**发生了什么——界面据此决定能不能说"已记录"。
 *
 * ## 为什么要有这一格
 *
 * 原因表单删掉之后（点踩直接落盘，空原因合法），这条链上只剩一件事：把案例写进库。
 * 写它的那一颗 [com.lovebrain.app.viewmodel.FeedbackCaseController.persistCase] 以前把 IO 异常
 * 吞在内部只记一条日志，调用方拿不到"到底写没写进去"，于是界面只能凭空报成功。
 * 这一格把那个缺口写成可以直接判的形状：**成功与否由落盘口的返回决定，不由界面猜**。
 *
 * 它同时管第二条：同一条方案的同一次生成被反复踩/取消/再踩时，库里不该长出重复记录。
 * 判据是"稳定 identity（`schemeIdentityKey`：`STYLE:B` / `DIRECTION:F`）+ **这一次生成**的身份"，
 * **不是**候选文案相同——同一个字面的回复在下一轮再出现是另一件事，
 * 按文案去重会把两轮不同的案例合成一条。
 *
 * ## 谁在调它（生产链路，2026-10-03 接线完成）
 *
 * `LoveBrainViewModel.setFeedback()`：`toggle()` 交回新案例之后，
 * 拿库里的快照（`FeedbackCaseController.existingCases()`）与落盘口
 * （`FeedbackCaseController.persistCase(case)`，**返回 false 时调用方把它抛出去**）喂给
 * [recordDislikeCase]，再由 `reportDislikeCaseSave()` 按 [CaseSaveOutcome] 分两格说话：
 * [CaseSaveOutcome.Recorded] / [CaseSaveOutcome.AlreadyRecorded] → 绿色成功格，
 * [CaseSaveOutcome.Failed] → 既有警告格，并且留着候选与用户刚点的那一下踩。
 * 文案用盘上已有的两条资源 `R.string.notice_recorded` / `notice_record_failed`，不新开 key。
 */
sealed interface CaseSaveOutcome {

    /** 案例已经写进盘里：这才可以说"已记录" */
    data object Recorded : CaseSaveOutcome

    /**
     * 库里已经有同一条方案 + 同一次生成的案例，所以**这次没有新增记录**。
     *
     * 它不是失败：用户看到的反馈与 [Recorded] 同一句（"已记录"），
     * 但案例库的条数不因为反复点踩而增长。[existingCaseId] 是那条已有记录的 id，
     * 界面不需要它，测试与日志需要——"没新增"这件事得说得出凭据。
     */
    data class AlreadyRecorded(val existingCaseId: String) : CaseSaveOutcome

    /**
     * 写盘失败。界面这一格必须：保留当前候选、不清空回复、也**不清掉用户刚点的那一下踩**
     * （用户 2026-10-03 原话），并且只给一行短提示（"记入失败，请重试"）。
     *
     * 踩的状态留着，意味着重试是"再点一次取消、再点一次踩"那两下，而不是一下——
     * 这是原话要的形状（不许把用户刚点的东西清掉），不是可以顺手改成一击重试的地方。
     */
    data object Failed : CaseSaveOutcome
}

/**
 * 同一条方案的**同一次生成** = 同一条踩。
 *
 * 两个条件都要成立：identity 认"是哪一条方案"，`promptVersion` 认"是哪一次生成"。
 * 故意**不看** [FeedbackCase.caseId]（每次点踩都是新 UUID，看了它就等于承认重复记录）、
 * 也故意**不看** [FeedbackCase.candidateReply]（跨轮同文案会被错误合并）。
 *
 * ## `promptVersion` 里现在装的是什么（ 接线的核心那一步）
 *
 * `<提示词资产哈希>#<GenerationVersionId>`——由
 * [com.lovebrain.app.viewmodel.LoveBrainViewModel.freezeDislikeDraft] 现拼，
 * 哈希留在前面继续做诊断读数，`#` 后面那颗才是去重要的"逐轮唯一"身份：
 * 每被接受一次成功生成就 `UUID.randomUUID()` 现 mint 一颗
 * （`ReplyStore.Effect.SuccessCommitted` → `GenerationVersionId.next()` → `ReplyVersionStack.record`）。
 *
 * 换掉的东西很具体：这一格以前只比资产哈希，而哈希**跨轮不变**（同一份 prompt 资产、
 * 同一次安装里每一轮都是同一个字符串），于是"新一轮里踩的另一条方案"被判成重复、悄悄丢掉。
 * 现在跨轮必然换号 ⇒ 各留一条；同一轮里反复踩/取消再踩同一条 ⇒ 号不变 ⇒ 库里只有一条。
 *
 * ⚠ 仍然欠着的两格（写在这里，不在这一格偷偷补）：
 * · **单条改写（rewrite）不换号**：改写走 `ReplyStore.Intent.ReplaceResult`，不经过
 *   `SuccessCommitted`，所以同一轮里改写之后再踩同一条方案仍算"同一次生成"，不再新增记录。
 *   要把它收成另一条，得让改写也 mint 一颗新的版本身份——那是 `feature/rewrite` 与版本栈那一族的事。
 * · 老案例（接线之前落盘的那些）`promptVersion` 是纯哈希、没有 `#`，
 *   与新写入的形状不同串，所以永不相等 ⇒ 接线前的历史记录不会被当成重复吞掉，
 *   但也**不能**替这一格证明身份真的会换。
 */
fun isSameGenerationDislike(existing: FeedbackCase, incoming: FeedbackCase): Boolean =
    existing.schemeIdentityKey == incoming.schemeIdentityKey &&
        existing.schemeIdentityKey.isNotBlank() &&
        existing.promptVersion == incoming.promptVersion

/**
 * 点踩落盘的唯一出口：先查重，再写，写失败就报失败。
 *
 * · [existing] 是本次点击之前库里的案例（调用方持有那份快照——这一格不读盘、不开第二个异步 owner）；
 *   调用方读不出库时交的是**空表**，那一格就失去去重依据 ⇒ 最坏多记一条，
 *   不会把用户刚点的那一下判成"已经记过了"（口径见 `FeedbackCaseController.existingCases()`）；
 *   调用方读不出库时交的是**空表**，那一格因此没有去重依据 ⇒ 最坏多记一条，不会把用户刚点的
 *   那一下判成"已经记过了"（`FeedbackCaseController.existingCases()` 那条口径）；
 * · [persist] 必须"写完再返回、写坏了就抛"；协程取消**原样上抛**，
 *   一次作用域拆除不能伪装成"保存失败"（这条判据与 `FeedbackCaseController` 同一条，
 *   搬到这里仍然要成立）。
 */
suspend fun recordDislikeCase(
    incoming: FeedbackCase,
    existing: List<FeedbackCase>,
    persist: suspend (FeedbackCase) -> Unit
): CaseSaveOutcome {
    val duplicate = existing.firstOrNull { isSameGenerationDislike(it, incoming) }
    if (duplicate != null) return CaseSaveOutcome.AlreadyRecorded(duplicate.caseId)
    return try {
        persist(incoming)
        CaseSaveOutcome.Recorded
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CaseSaveOutcome.Failed
    }
}

/**
 * 这条落盘结果该对用户说什么——两条短句，与 第10节第1条 那一格通知位同一口径。
 *
 * 单独写成一格是为了让"失败不虚报成功"有第二个判点：
 * [CaseSaveOutcome.Failed] 走到这里必须得到失败那句，而不是 null、也不是成功那句。
 */
fun caseSaveNoticeText(outcome: CaseSaveOutcome, recorded: String, failed: String): String =
    when (outcome) {
        CaseSaveOutcome.Recorded, is CaseSaveOutcome.AlreadyRecorded -> recorded
        CaseSaveOutcome.Failed -> failed
    }
