package com.lovebrain.app.viewmodel

import com.lovebrain.app.data.FeedbackCaseRepository
import com.lovebrain.app.model.DialogueSnapshotEntry
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.util.L
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * 点踩时冻结下来的案例素材。
 *
 * 由 `LoveBrainViewModel` 在**用户点击那一刻**采好再交进来——控制器不回读
 * ViewModel 的实时状态，否则一次异步保存期间结果被换掉，案例会记到别的回复上。
 */
data class FeedbackCaseDraft(
    val schemeReply: String,
    val kbName: String,
    val ideaHint: String,
    val intentText: String,
    val dialogue: List<DialogueSnapshotEntry>,
    val contextMode: String,
    val promptVersion: String,
    val modelId: String,
    val costYuan: Double
)

/**
 * 方案赞/踩与点踩案例（从 `LoveBrainViewModel` 拆出）。
 *
 * 职责边界：**只**管"某条方案当前是赞还是踩"和"点踩落一份可诊断案例"这两件事的状态与落盘。
 * 方案正文、identityKey 怎么从 `GenerateResult` 里取，仍归 ViewModel——它才是结果的持有者。
 *
 * 这里**不 launch 任何协程**：对外只有同步的状态变更和显式的 suspend 落盘口，
 * 异步工作由调用方在自己的 scope 里发起。拆类因此不会多出第二个异步 owner
 * （全仓唯一性由 `SingleOwnerContractTest` 钉住）。
 *
 * 保持拆分之内的三条语义：
 * - 再点同一个按钮是取消（toggle），取消踩时不建案例、并清掉屏上的案例；
 * - 案例在点击当刻同步构造并暴露，UI 不需要异步查全库猜最后一条；
 * - 落盘 IO 失败只记日志、不打断面板，但协程取消必须原样上抛，不能伪装成"保存失败"。
 */
class FeedbackCaseController(
    private val repository: FeedbackCaseRepository?,
    private val ioContext: CoroutineContext = Dispatchers.IO,
    private val caseIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> String = { TimeFmt.now() }
) {

    private val _feedbacks = MutableStateFlow<Map<String, SchemeFeedback>>(emptyMap())

    /** identityKey → 赞/踩；STYLE 与 DIRECTION 用各自的 identity.key，互不干扰 */
    val feedbacks: StateFlow<Map<String, SchemeFeedback>> = _feedbacks.asStateFlow()

    private val _currentCase = MutableStateFlow<FeedbackCase?>(null)

    /** 最近一次点踩生成的案例；面板直接消费，不再异步查全库 */
    val currentCase: StateFlow<FeedbackCase?> = _currentCase.asStateFlow()

    /** 读单条方案的当前反馈（未记过时为 NONE） */
    fun feedbackFor(identityKey: String): SchemeFeedback =
        _feedbacks.value[identityKey] ?: SchemeFeedback.NONE

    /** 改写/撤销时按版本号恢复某条方案的反馈 */
    fun putFeedback(identityKey: String, feedback: SchemeFeedback) {
        _feedbacks.value = _feedbacks.value.toMutableMap().apply { put(identityKey, feedback) }
    }

    /** 轮次提交与版本回退：整表清空 */
    fun clearFeedbacks() {
        _feedbacks.value = emptyMap()
    }

    /**
     * 赞/踩切换。
     *
     * [draft] 由调用方在点击前冻结；只有**切换结果为 DISLIKED 且素材齐**时才建案例。
     * 返回值是"这次该落盘的新案例"（无则 null）——持久化由调用方发起，
     * 本函数保持同步，面板因此能在点击当刻就看到案例。
     */
    fun toggle(identityKey: String, feedback: SchemeFeedback, draft: FeedbackCaseDraft?): FeedbackCase? {
        val effective = if (_feedbacks.value[identityKey] == feedback) SchemeFeedback.NONE else feedback
        _feedbacks.value = _feedbacks.value.toMutableMap().apply { put(identityKey, effective) }
        if (effective != SchemeFeedback.DISLIKED || draft == null) {
            _currentCase.value = null
            return null
        }
        return buildCase(identityKey, draft)
    }

    /** 构造案例并同步暴露给 UI；是否落盘由调用方拿着返回的实例决定 */
    private fun buildCase(identityKey: String, draft: FeedbackCaseDraft): FeedbackCase {
        val case = FeedbackCase(
            caseId = caseIdProvider(),
            schemeIdentityKey = identityKey,
            candidateReply = draft.schemeReply,
            categories = emptyList(),
            reasons = emptyList(),
            kbName = draft.kbName,
            ideaHint = draft.ideaHint,
            intentText = draft.intentText,
            modelId = draft.modelId,
            timestamp = clock(),
            dialogueSnapshot = draft.dialogue,
            contextMode = draft.contextMode,
            promptVersion = draft.promptVersion,
            appVersion = com.lovebrain.app.BuildConfig.VERSION_NAME,
            buildType = com.lovebrain.app.BuildConfig.BUILD_TYPE,
            // usage 不可用时默认 0（不把默认 0 当已核实成本）
            promptTokens = 0,
            completionTokens = 0,
            costYuan = draft.costYuan
        )
        _currentCase.value = case
        return case
    }

    /**
     * 案例落盘。
     *
     * 单独是一个 suspend 出口，不只是为了测：它把"这条路径允许兜底、那条路径必须放行取消"
     * 写成可直接断言的边界——IO 失败只记日志，协程取消原样上抛；
     * 否则一次作用域拆除会被记成"保存失败"，真相就丢了。
     */
    suspend fun persistCase(case: FeedbackCase) {
        val repo = repository ?: return
        try {
            withContext(ioContext) { repo.save(case) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w("saveFeedbackCase failed: ${e::class.simpleName}")
        }
    }

    /** 更新反馈案例的分类、原因和补充说明；读—改—写整体在 IO 上，兜底规则同 [persistCase] */
    suspend fun updateCase(
        caseId: String,
        categories: List<FeedbackCategory>,
        reasons: List<String>,
        userNote: String = "",
        betterVersion: String = ""
    ) {
        val repo = repository ?: return
        try {
            withContext(ioContext) {
                val existing = repo.getAll().find { it.caseId == caseId } ?: return@withContext
                repo.save(
                    existing.copy(
                        categories = categories,
                        reasons = reasons,
                        userNote = userNote,
                        betterVersion = betterVersion
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w("updateFeedbackCase failed: ${e::class.simpleName}")
        }
    }

    /** dismiss 只收掉面板展示；已落盘的案例不动 */
    fun dismissCase() {
        _currentCase.value = null
    }
}
