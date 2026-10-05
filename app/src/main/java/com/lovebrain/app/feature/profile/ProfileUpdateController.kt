package com.lovebrain.app.feature.profile

import com.lovebrain.app.model.PreconditionReason
import com.lovebrain.app.model.ProfileTransactionResult
import com.lovebrain.app.model.ProfileUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 画像建议卡片那一段**行为**的持有者（复核 第5节第2条 第 6 步：搬行为块，不是搬字段）。
 *
 * 它接管三样东西：[ProfileReview] 那份快照（唯一写入漏斗是 [accept]）、
 * "确认"这一次尝试的全部判据、以及 dismiss。原来这些判据摊在 ViewModel 里一个 98 行的
 * `confirmProfileUpdate()` 上——那是全文件最大的单个成员，JVM 侧完全量不到（要 mock 仓库、
 * 协调器、触发器三样才能走到任何一个分支）。搬进来之后每一条分支都能单独喂。
 *
 * 为什么这些分支值得单独一格一格测（每一条都是**用户可见差别**，不是内部细节）：
 * · 三种前置条件失败给的话不一样，而且 **只读保护不许清卡**：
 *   `LIBRARY_READ_ONLY` 说的是"这个 App 比库旧"，升级之后同一份建议仍然有效；
 *   跟着 `KB_NOT_FOUND`/`REVISION_CONFLICT` 一起清掉，用户这次攒的审核内容就白丢一次
 *   （这一支是 第6节第4条 读浮层死路时抓到的，账本「追加五十八」）。
 * · `RolledBack` 与 `RollbackFailed` 是两种严重程度：前者数据已恢复、可重试；
 *   后者可能不一致——把两者合并成一句"保存失败"就是撒谎。
 *
 * 落盘与读库都靠注入：`feature` 不许 import `data`（第5节第1条 包边界），
 * 所以本类不知道背后是 `KnowledgeRepository` 还是别的东西，也不知道线程怎么切。
 */
class ProfileUpdateController(
    private val scope: CoroutineScope,
    /** 建议 originating 的那块库还在不在 */
    private val libraryExists: suspend (kbName: String) -> Boolean,
    /** 该库当前的纠正 revision——用来识别"建议生成之后资料又被改过" */
    private val readCorrectionsRevision: suspend (kbName: String) -> Int,
    /** 原子事务落盘，返回 typed result（成功 / 前置条件失败 / 已回滚 / 回滚也失败） */
    private val applyUpdate: suspend (kbName: String, payload: ProfileUpdate, expectedRevision: Int) -> ProfileTransactionResult,
    /** 盘确实改了之后要做的事（刷新库与向量），刷什么由调用方决定 */
    private val onApplied: () -> Unit = {},
    private val onWarning: (String) -> Unit = {},
    private val onNotice: (String) -> Unit = {},
    private val onError: (String, Throwable?) -> Unit = { _, _ -> },
    /** 让正在进行的重新生成让位——租约归协调器管，这里只发意图 */
    private val stopRegeneration: () -> Unit = {}
) {

    private val _review = MutableStateFlow(ProfileReview())

    /** 卡片唯一的真源："建议是什么"和"在不在确认中"必须同帧，见 [ProfileReview] */
    val review: StateFlow<ProfileReview> = _review.asStateFlow()

    /** 想动那份快照，只有这一条路 */
    fun accept(event: ProfileReview.Event) {
        _review.value = _review.value.reduce(event)
    }

    /** 用户关掉卡片：同时让在跑的重新生成让位，迟到回调就不该再把卡片翻回来 */
    fun dismiss() {
        stopRegeneration()
        accept(ProfileReview.Event.Dismissed)
    }

    /**
     * 确认这一次画像更新。
     *
     * 前置检查（建议是否作废、纠正 revision 是否已变）在这里做**一次**，
     * 仓库那边还会在自己的锁内再查一次——那是竞态兜底，不是重复代码。
     */
    fun confirm() {
        val current = _review.value
        // "有建议且没在确认中"这条判据住在 ProfileReview 里，不在这里重复写一遍
        if (!current.canAttemptConfirm) return

        val suggestion = current.suggestion ?: return
        val payload = suggestion.profileUpdate
        if (payload == null || !payload.valid) {
            onWarning("建议格式无效，请重新生成")
            accept(ProfileReview.Event.Dismissed)
            return
        }

        val kbName = suggestion.kbName
        val suggestionId = suggestion.suggestionId

        scope.launch {
            accept(ProfileReview.Event.ConfirmStarted)
            try {
                if (!libraryExists(kbName)) {
                    accept(ProfileReview.Event.Dismissed)
                    onWarning("原知识库已删除，这条画像建议已失效")
                    return@launch
                }
                if (readCorrectionsRevision(kbName) != suggestion.correctionsRevision) {
                    accept(ProfileReview.Event.Dismissed)
                    onWarning("资料已变化，请重新生成")
                    return@launch
                }

                when (val result = applyUpdate(kbName, payload, suggestion.correctionsRevision)) {
                    is ProfileTransactionResult.Success -> {
                        // 确认期间可能有新建议到达；只清"我确认的这一份"，这条判据在事件里
                        accept(ProfileReview.Event.ClearedIfCurrent(suggestionId))
                        onNotice("画像已更新")
                        onApplied()
                    }
                    is ProfileTransactionResult.PreconditionFailed -> {
                        val msg = when (result.reason) {
                            // 建议本身作废的两种：清卡
                            PreconditionReason.KB_NOT_FOUND -> {
                                accept(ProfileReview.Event.Dismissed)
                                "原知识库已删除，这条画像建议已失效"
                            }
                            PreconditionReason.REVISION_CONFLICT -> {
                                accept(ProfileReview.Event.Dismissed)
                                "资料已变化，请重新生成"
                            }
                            // 只读保护不是建议作废：那是"这个 App 比库旧"，升级之后同一份建议仍然有效。
                            // 跟着清卡会把用户这次攒的审核内容白丢一次——所以这一支**不许** Dismissed。
                            PreconditionReason.LIBRARY_READ_ONLY ->
                                "这个知识库的结构版本比本 App 还新，已被设为只读，画像没有写入（可以先升级 App 再确认）"
                        }
                        onWarning(msg)
                    }
                    is ProfileTransactionResult.RolledBack -> {
                        // 写入失败但回滚完整成功——数据已恢复，可以安全重试
                        onError("confirmProfileUpdate: transaction rolled back", result.cause)
                        onWarning("画像写入失败，已恢复原数据，可重试")
                    }
                    is ProfileTransactionResult.RollbackFailed -> {
                        // 写入失败且回滚也失败——数据可能不一致，措辞不能与上面那一支合并
                        onError("confirmProfileUpdate: CRITICAL rollback failed for paths=${result.failedPaths}", result.cause)
                        onWarning("画像写入失败且恢复异常，数据可能已损坏，请检查知识库")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 取消不是失败：原样上抛（这一支的可见性靠句柄，见 ActualSentRecorder 同样的处理）
                throw e
            } catch (e: Exception) {
                onError("confirmProfileUpdate unexpected error", e)
                onWarning("画像写入发生异常，请重试")
            } finally {
                accept(ProfileReview.Event.ConfirmFinished)
            }
        }
    }
}
