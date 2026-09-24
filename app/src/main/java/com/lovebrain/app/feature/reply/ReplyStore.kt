package com.lovebrain.app.feature.reply

import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.PanelState
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyEvent
import com.lovebrain.app.model.ReplyReducer
import com.lovebrain.app.model.ReplyUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 回复流程的状态持有者（独立复核 §5.2 迁移顺序的第 1 步）。
 *
 * 它接管三样东西：**ReplyUiState + ReplyReducer、流式增量合并缓冲、以及"这次归约
 * 到底改变了什么"的副作用判定**。ViewModel 之后只做 facade：读 `uiState`、
 * 把 `Effect` 落成自己的持久化与其他 feature 的计数。
 *
 * 为什么副作用是**同步回调**而不是一条 SharedFlow：VM 现在写 `_panelState` /
 * 计数是在归约成功的那一刻同步完成的，现有测试也是按"喂完事件就断言状态"来写的。
 * 换成 flow 会把这些写入推迟到收集器下一次调度，那等于用一次结构改进换来一批
 * 时序漂移——§7 第一步还在等真机证据，这种漂移会把可辨识的失败变成不可辨识的。
 * 接口形状（Intent / Effect）不变，将来要换成 flow 是本地可验证的一次改动。
 *
 * 增量合并必须留在这里：`dispatchReply` 的"相邻 chunk 合并成一次归约"和
 * "reducer 是唯一写入口"是同一件事的两半，拆开放就会重新变成绕过状态源的旁路。
 */
class ReplyStore(
    private val scope: CoroutineScope,
    /** 相邻增量合并的节拍。测试要能改小，否则每测一次节流都要真等 50ms。 */
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    /**
     * 归约成功后的副作用出口。放最后是为了让调用方能写尾随 lambda；
     * 它是**同步**调用的，理由见类 KDoc。
     */
    private val onEffect: (Effect) -> Unit = {}
) {

    /** 唯一入口：外部只能投意图，不能改状态 */
    sealed interface Intent {
        /** 一次生成请求的事件（身份不符的会被 reducer 拒掉） */
        data class Apply(val event: ReplyEvent) : Intent

        /** 原地替换结果文本：单条改写 / undo / 版本回退，不属于任何一次请求 */
        data class ReplaceResult(val result: GenerateResult) : Intent

        /** 丢弃尚未发布的增量并停掉合并定时器（停止生成、换请求时） */
        data object DiscardPendingChunks : Intent
    }

    /** 归约成功之后需要**别人**做的事：持久化、跨 feature 计数、外壳状态 */
    sealed interface Effect {
        /** 面板外壳状态要跟着回复状态走 */
        data class PanelStateChanged(val panelState: PanelState) : Effect

        /**
         * 一次新的成功结果落定。
         *
         * 这里带 memoryRefs / sourceAliasMap 是因为历史快照必须记"这次成功引用了哪些
         * 记忆、别名表是什么"——那两个值只在归约成功的这一刻与结果配对，
         * 让 VM 事后再去读状态就会拿到下一轮的值。
         */
        data class SuccessCommitted(
            val result: GenerateResult.Success,
            val memoryRefs: List<MemoryRef>,
            val sourceAliasMap: Map<String, String>
        ) : Effect

        /** 首次可复制回复 / 首 token 的耗时样本（只在变化时给） */
        data class TimingSampled(val firstReplyMs: Long? = null, val firstTokenMs: Long? = null) : Effect
    }

    private val _ui = MutableStateFlow(ReplyUiState())

    /** 回复流程的唯一真源。VM 暴露的 result / isGenerating / streaming… 都从这里 map。 */
    val uiState: StateFlow<ReplyUiState> = _ui.asStateFlow()

    /** 当前结果——内部读取统一走这里，不存在第二个可写的"结果账" */
    val currentResult: GenerateResult? get() = _ui.value.result

    val isBusy: Boolean get() = _ui.value.isBusy

    // ── 流式增量合并缓冲：只在 accept 及其触发的 flush 里读写 ───────────────
    private val pendingChunk = StringBuilder()
    private var pendingChunkRequestId: String? = null
    private var chunkFlushJob: Job? = null

    init {
        // 只在主线程/单协程上使用（Engine 的事件流在 viewModelScope 里收集），
        // 所以这块缓冲不需要额外加锁——这也是它原先写在 VM 里时的同一前提。
    }

    fun accept(intent: Intent) {
        when (intent) {
            is Intent.Apply -> onEvent(intent.event)
            is Intent.ReplaceResult -> replaceResult(intent.result)
            Intent.DiscardPendingChunks -> discardPendingChunks()
        }
    }

    private fun onEvent(event: ReplyEvent) {
        if (event is ReplyChunk) {
            if (pendingChunkRequestId != null && pendingChunkRequestId != event.requestId) {
                flushPendingChunk()
            }
            pendingChunkRequestId = event.requestId
            pendingChunk.append(event.text)
            scheduleFlush()
            return
        }
        flushPendingChunk()
        reduce(event)
    }

    private fun scheduleFlush() {
        if (chunkFlushJob?.isActive == true) return
        chunkFlushJob = scope.launch {
            // 还有未发布的增量才继续等；发完就自己结束。
            //
            // 这里原来是 `while (true) { delay(50); flush() }`：一旦有 chunk 进来就
            // 永远每 50ms 醒一次，只有外部调用 discardPendingChunks 才停得下来。
            // 在 VM 里它躲在 viewModelScope 的死亡后面，不容易被发现；搬进 store
            // 用 runTest 一测就当场挂住（UncompletedCoroutinesError：仍有 active child job）。
            // 现在循环的存续条件与它的职责一致——有货要发才等下一班。
            while (pendingChunk.isNotEmpty() && pendingChunkRequestId != null) {
                delay(flushIntervalMs)
                flushPendingChunk()
            }
        }
    }

    private fun flushPendingChunk() {
        val requestId = pendingChunkRequestId ?: return
        if (pendingChunk.isEmpty()) return
        val text = pendingChunk.toString()
        pendingChunk.setLength(0)
        reduce(ReplyChunk(requestId, text))
    }

    private fun discardPendingChunks() {
        chunkFlushJob?.cancel()
        chunkFlushJob = null
        pendingChunkRequestId = null
        pendingChunk.setLength(0)
    }

    private fun replaceResult(result: GenerateResult) {
        _ui.value = _ui.value.copy(result = result)
    }

    /**
     * 归约的唯一执行点。
     *
     * 被拒的事件（reducer 返回同一个对象）不发布 Effect，因此迟到的旧请求
     * 不可能递增轮次、不可能写历史、也不可能改计数。
     */
    private fun reduce(event: ReplyEvent) {
        val before = _ui.value
        val after = ReplyReducer.reduce(before, event)
        if (after === before) return
        _ui.value = after
        if (after.panelState != before.panelState) onEffect(Effect.PanelStateChanged(after.panelState))
        val result = after.result
        if (result is GenerateResult.Success && result !== before.result) {
            onEffect(Effect.SuccessCommitted(result, after.memoryRefs, after.sourceAliasMap))
        }
        val replyMs = if (after.firstReplyMs > 0L && after.firstReplyMs != before.firstReplyMs) after.firstReplyMs else null
        val tokenMs = if (after.firstTokenMs > 0L && after.firstTokenMs != before.firstTokenMs) after.firstTokenMs else null
        if (replyMs != null || tokenMs != null) onEffect(Effect.TimingSampled(replyMs, tokenMs))
    }

    companion object {
        const val DEFAULT_FLUSH_INTERVAL_MS = 50L
    }
}
