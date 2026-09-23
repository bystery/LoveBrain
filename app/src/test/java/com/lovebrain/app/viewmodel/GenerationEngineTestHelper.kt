package com.lovebrain.app.viewmodel

import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.PanelState
import com.lovebrain.app.model.toChatMessages
import io.mockk.every
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * 测试辅助：统一 stub GenerationEngine.generateReply() 的 3 参数签名。
 *
 * S2-01: 生产代码 generateReply() 签名为：
 *   generateReply(input: GenerationInput, scope: CoroutineScope, callbacks: Callbacks)
 *
 * 旧测试只 mock 7 个 any()，导致 MockK 不匹配 → relaxed mock 返回 null → 连锁失败。
 * 本 helper 确保所有 stub 完整匹配 3 参数；Engine 签名再演进只改这一处。
 */
object GenerationEngineTestHelper {

    /**
     * Stub engine.generateReply 成功返回——模拟完整流式回调链。
     *
     * @param engine 被 mock 的 GenerationEngine
     * @param response 返回的 LoveBrainResponse
     * @param gate 可选：如果非 null，回调链挂起在 gate 上（用于测试生成期间操作）
     */
    fun stubReplyGenerateSuccess(
        engine: GenerationEngine,
        response: LoveBrainResponse = LoveBrainResponse(
            response = com.lovebrain.app.model.ReplySchemes(recommended = "reply"),
            analysis = com.lovebrain.app.model.ReplyAnalysis(topic_status = "same", topic_label = "test")
        ),
        gate: CompletableDeferred<Unit>? = null
    ) {
        every {
            engine.generateReply(
                any(), any(), any()
            )
        } answers {
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                if (gate != null) {
                    try { gate.await() } catch (_: Exception) {}
                } else {
                    callbacks.onReplyResult(GenerateResult.Success(response))
                    callbacks.onReplyGenerating(false, false)
                    callbacks.onReplyPanelState(PanelState.AI_RESULT)
                }
            }
        }
    }

    /**
     * Stub engine.generateReply 成功返回，并可捕获传入的 messages 快照。
     */
    fun stubReplyGenerateSuccessCapturing(
        engine: GenerationEngine,
        capturedMessages: kotlinx.coroutines.CompletableDeferred<List<com.lovebrain.app.model.ChatMessage>>,
        response: LoveBrainResponse = LoveBrainResponse(
            response = com.lovebrain.app.model.ReplySchemes(recommended = "reply"),
            analysis = com.lovebrain.app.model.ReplyAnalysis(topic_status = "same", topic_label = "test")
        ),
        gate: CompletableDeferred<Unit>? = null
    ) {
        every {
            engine.generateReply(
                any(), any(), any()
            )
        } answers {
            val input = firstArg<GenerationInput>()
            capturedMessages.complete(input.toChatMessages())
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                if (gate != null) {
                    try { gate.await() } catch (_: Exception) {}
                } else {
                    callbacks.onReplyResult(GenerateResult.Success(response))
                    callbacks.onReplyGenerating(false, false)
                    callbacks.onReplyPanelState(PanelState.AI_RESULT)
                }
            }
        }
    }

    /**
     * Stub engine.generateReply 挂起在 gate 上——用于测试生成期间操作。
     * 回调链只触发 onReplyStart，然后挂起。
     */
    fun stubReplyGenerateHanging(
        engine: GenerationEngine,
        gate: CompletableDeferred<Unit>,
        onStreamingSchemes: List<com.lovebrain.app.model.Scheme>? = null
    ) {
        every {
            engine.generateReply(
                any(), any(), any()
            )
        } answers {
            val scope = arg<CoroutineScope>(1)
            val callbacks = arg<GenerationEngine.Callbacks>(2)
            scope.launch {
                callbacks.onReplyStart()
                if (onStreamingSchemes != null) {
                    callbacks.onReplyStreamingSchemes(onStreamingSchemes)
                }
                try { gate.await() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Stub engine.generateReply 返回 null（Engine reject）。
     */
    fun stubReplyGenerateReject(engine: GenerationEngine) {
        every {
            engine.generateReply(
                any(), any(), any()
            )
        } returns null
    }
}
