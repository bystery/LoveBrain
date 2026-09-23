package com.lovebrain.app.viewmodel

import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.model.CounselingChunk
import com.lovebrain.app.model.CounselingEnded
import com.lovebrain.app.model.CounselingEvent
import com.lovebrain.app.model.CounselingFailed
import com.lovebrain.app.model.CounselingFirstToken
import com.lovebrain.app.model.CounselingResult
import com.lovebrain.app.model.CounselingStarted
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.PanelState
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveEvent
import com.lovebrain.app.model.ProactiveFailed
import com.lovebrain.app.model.ProactiveFirstToken
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ProactiveStarted
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyChunkReset
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyEvent
import com.lovebrain.app.model.ReplyFirstToken
import com.lovebrain.app.model.ReplyMemoryRefs
import com.lovebrain.app.model.ReplySchemesArrived
import com.lovebrain.app.model.ReplySchemesReset
import com.lovebrain.app.model.ReplySourceAliasMap
import com.lovebrain.app.model.ReplyStarted
import com.lovebrain.app.model.ReplyStopped
import com.lovebrain.app.model.ReplyUsage
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SuggestEnded
import com.lovebrain.app.model.SuggestEvent
import com.lovebrain.app.model.SuggestFailed
import com.lovebrain.app.model.SuggestFirstToken
import com.lovebrain.app.model.SuggestResult
import com.lovebrain.app.model.SuggestStarted
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.model.SuggestTips
import com.lovebrain.app.model.toChatMessages
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * 测试替身：用"记一笔事件"的写法模拟 Engine 的一次回复生成。
 *
 * 保留 `onReplyStart()` / `onReplyResult(...)` 这套读起来像回调的方法名，
 * 但它们做的事情是往通道里塞 typed event——不再有反向写 ViewModel 的通道。
 * 已有测试因此可以小改而不必逐个重写断言。
 */
class EventRecorder {
    /** 本次模拟所属请求身份——由 stub 从 GenerationInput 里取 */
    var requestId: String = ""

    private val channel = Channel<ReplyEvent>(Channel.UNLIMITED)
    val flow: Flow<ReplyEvent> = channel.consumeAsFlow()

    fun onReplyStart() = send(ReplyStarted(requestId))
    fun onReplyResult(result: GenerateResult) = send(ReplyCompleted(requestId, result))
    fun onReplyStreamingSchemes(schemes: List<Scheme>) = send(ReplySchemesArrived(requestId, schemes))
    fun onReplyStreamingSchemesReset() = send(ReplySchemesReset(requestId))
    fun onReplyStreamingCoreText(text: String) = send(ReplyChunk(requestId, text))
    fun onReplyStreamingCoreTextReset() = send(ReplyChunkReset(requestId))
    fun onReplyMemoryRefs(refs: List<MemoryRef>) = send(ReplyMemoryRefs(requestId, refs))
    fun onReplySourceAliasMap(map: Map<String, String>) = send(ReplySourceAliasMap(requestId, map))
    fun onReplyUsage(promptTokens: Int?, completionTokens: Int?, costYuan: Double?) =
        send(ReplyUsage(requestId, promptTokens, completionTokens, costYuan))
    fun onReplyFirstToken(elapsedMs: Long) = send(ReplyFirstToken(requestId, elapsedMs))
    fun onReplyStopped() = send(ReplyStopped(requestId))

    /** 新架构里 isGenerating/PanelState 由 reducer 派生，这两笔是 no-op，保留只为不破坏旧用例写法 */
    fun onReplyGenerating(isGenerating: Boolean, isGeneratingCore: Boolean) { }
    fun onReplyPanelState(state: PanelState) { }

    private fun send(event: ReplyEvent) {
        if (channel.trySend(event).isFailure) error("EventRecorder overflow")
    }

    /** 记账结束——关闭通道，collect 到这条流的请求才会正常收尾（否则任务永不完成） */
    fun finish() = channel.close()
}

/** 把一段"记账"代码执行完，返回可被 ViewModel 收集的事件流 */
fun EventRecorder.record(block: EventRecorder.() -> Unit): Flow<ReplyEvent> {
    block()
    finish()
    return flow
}

/** 谈心流程的同类替身 */
class CounselingRecorder {
    var requestId: String = ""
    /** 发起时的倾诉原文与 KB 名——事件自带，与生产 Engine 口径一致 */
    var userMessage: String = ""
    var kbName: String? = null

    private val channel = Channel<CounselingEvent>(Channel.UNLIMITED)
    val flow: Flow<CounselingEvent> = channel.consumeAsFlow()

    fun onCounselingStart() = send(CounselingStarted(requestId))
    fun onCounselingStreaming(text: String) = send(CounselingChunk(requestId, text))
    fun onCounselingFirstToken(elapsedMs: Long) = send(CounselingFirstToken(requestId, elapsedMs))
    fun onCounselingResult(replyText: String, analysisText: String = "") =
        send(CounselingResult(requestId, replyText, analysisText, kbName, userMessage))
    fun onCounselingFailed(message: String) = send(CounselingFailed(requestId, message))
    fun onCounselingEnd() = send(CounselingEnded(requestId))

    private fun send(event: CounselingEvent) {
        if (channel.trySend(event).isFailure) error("CounselingRecorder overflow")
    }

    /** 记账结束——关闭通道，collect 到这条流的请求才会正常收尾 */
    fun finish() = channel.close()
}

fun CounselingRecorder.record(block: CounselingRecorder.() -> Unit): Flow<CounselingEvent> {
    block()
    finish()
    return flow
}

/** 锦囊流程的同类替身 */
class SuggestRecorder {
    var requestId: String = ""

    private val channel = Channel<SuggestEvent>(Channel.UNLIMITED)
    val flow: Flow<SuggestEvent> = channel.consumeAsFlow()

    fun onSuggestStart() = send(SuggestStarted(requestId))
    fun onSuggestFirstToken(elapsedMs: Long) = send(SuggestFirstToken(requestId, elapsedMs))
    fun onSuggestTips(tips: List<SuggestTip>) = send(SuggestTips(requestId, tips))
    fun onSuggestResult(suggestion: DailySuggestion?) = send(SuggestResult(requestId, suggestion))
    fun onSuggestFailed(message: String) = send(SuggestFailed(requestId, message))
    fun onSuggestEnd() = send(SuggestEnded(requestId))

    private fun send(event: SuggestEvent) {
        if (channel.trySend(event).isFailure) error("SuggestRecorder overflow")
    }

    /** 记账结束——关闭通道，collect 到这条流的请求才会正常收尾 */
    fun finish() = channel.close()
}

fun SuggestRecorder.record(block: SuggestRecorder.() -> Unit): Flow<SuggestEvent> {
    block()
    finish()
    return flow
}

/** 主动发流程的同类替身 */
class ProactiveRecorder {
    var requestId: String = ""

    private val channel = Channel<ProactiveEvent>(Channel.UNLIMITED)
    val flow: Flow<ProactiveEvent> = channel.consumeAsFlow()

    fun onProactiveStart() = send(ProactiveStarted(requestId))
    fun onProactiveFirstToken(elapsedMs: Long) = send(ProactiveFirstToken(requestId, elapsedMs))
    fun onProactiveOptions(options: List<ProactiveOption>) = send(ProactiveOptions(requestId, options))
    fun onProactiveFailed(message: String) = send(ProactiveFailed(requestId, message))
    fun onProactiveEnd() = send(ProactiveEnded(requestId))

    private fun send(event: ProactiveEvent) {
        if (channel.trySend(event).isFailure) error("ProactiveRecorder overflow")
    }

    /** 记账结束——关闭通道，collect 到这条流的请求才会正常收尾 */
    fun finish() = channel.close()
}

fun ProactiveRecorder.record(block: ProactiveRecorder.() -> Unit): Flow<ProactiveEvent> {
    block()
    finish()
    return flow
}

/**
 * 测试辅助：统一 stub GenerationEngine 的事件流入口。
 *
 * S2-02/S2-03 之后 Engine 不再有 `generateReply(input, scope, callbacks)`，
 * 它只暴露冷流：`replyStream(input): Flow<ReplyEvent>`。
 * 于是测试可以精确模拟"Provider 走到哪一步"，
 * 而不需要伪造一个会反向写 ViewModel 的回调对象。
 *
 * Engine 签名再演进只改这一处。
 */
object GenerationEngineTestHelper {

    private fun successResponse(
        text: String = "reply"
    ): LoveBrainResponse = LoveBrainResponse(
        response = com.lovebrain.app.model.ReplySchemes(recommended = text),
        analysis = com.lovebrain.app.model.ReplyAnalysis(topic_status = "same", topic_label = "test")
    )

    /**
     * Stub 一次成功生成。
     *
     * @param gate 非 null 时流挂起在 gate 上，用于测"生成期间"的行为
     */
    fun stubReplyGenerateSuccess(
        engine: GenerationEngine,
        response: LoveBrainResponse = successResponse(),
        gate: CompletableDeferred<Unit>? = null
    ) {
        every { engine.replyStream(any()) } answers {
            val requestId = firstArg<GenerationInput>().requestId
            flow {
                emit(ReplyStarted(requestId))
                if (gate != null) {
                    gate.await()
                } else {
                    emit(ReplyCompleted(requestId, GenerateResult.Success(response)))
                }
            }
        }
    }

    /** Stub 一次成功生成，并把传入的冻结消息快照回传给断言方 */
    fun stubReplyGenerateSuccessCapturing(
        engine: GenerationEngine,
        capturedMessages: CompletableDeferred<List<com.lovebrain.app.model.ChatMessage>>,
        response: LoveBrainResponse = successResponse(),
        gate: CompletableDeferred<Unit>? = null
    ) {
        every { engine.replyStream(any()) } answers {
            val input = firstArg<GenerationInput>()
            capturedMessages.complete(input.toChatMessages())
            val requestId = input.requestId
            flow {
                emit(ReplyStarted(requestId))
                if (gate != null) {
                    gate.await()
                } else {
                    emit(ReplyCompleted(requestId, GenerateResult.Success(response)))
                }
            }
        }
    }

    /** Stub 一个挂在 gate 上的流，并先推一批流式方案卡 */
    fun stubReplyGenerateHanging(
        engine: GenerationEngine,
        gate: CompletableDeferred<Unit>,
        onStreamingSchemes: List<Scheme>? = null
    ) {
        every { engine.replyStream(any()) } answers {
            val requestId = firstArg<GenerationInput>().requestId
            flow {
                emit(ReplyStarted(requestId))
                if (onStreamingSchemes != null) emit(ReplySchemesArrived(requestId, onStreamingSchemes))
                gate.await()
            }
        }
    }

    /**
     * Stub 一个被拒绝的请求：流一条事件都不发。
     *
     * 新架构下"Engine 拒绝"不再是返回 null Job，而是任务启动后流里没有任何事件——
     * 前台任务依然由 coordinator 统一持有和结束。
     */
    fun stubReplyGenerateReject(engine: GenerationEngine) {
        every { engine.replyStream(any()) } answers { emptyFlow() }
    }

    /** Stub 一次谈心：按 [block] 记账的事件顺序走完整条流 */
    fun stubCounseling(
        engine: GenerationEngine,
        block: CounselingRecorder.() -> Unit
    ) {
        every { engine.counselingStream(any(), any(), any()) } answers {
            val recorder = CounselingRecorder().apply {
                requestId = arg<String>(0)
                userMessage = arg<String>(1)
                kbName = arg<KnowledgeBase?>(2)?.name
            }
            recorder.record(block)
        }
    }

    /** Stub 一次锦囊生成 */
    fun stubSuggest(engine: GenerationEngine, block: SuggestRecorder.() -> Unit) {
        every { engine.suggestStream(any(), any()) } answers {
            val recorder = SuggestRecorder().apply { requestId = arg<String>(0) }
            recorder.record(block)
        }
    }

    /** Stub 一次主动发/润色 */
    fun stubProactive(engine: GenerationEngine, block: ProactiveRecorder.() -> Unit) {
        every { engine.proactiveStream(any(), any(), any(), any()) } answers {
            val recorder = ProactiveRecorder().apply { requestId = arg<String>(0) }
            recorder.record(block)
        }
    }
}
