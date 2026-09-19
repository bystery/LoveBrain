package com.lovebrain.app.ui.panel.reply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-1: Voice hold-to-talk 两事件 rendezvous 提交机制纯逻辑测试。
 *
 * 测试目标：验证 final transcript 只在 physicalReleased && finalTranscript 都满足时提交，
 * cancelled 永远不提交，submitted 保证只提交一次。
 *
 * 测试覆盖的生产 path：
 * - shouldCommitTranscript() —— VoiceRewriteController.tryCommit() 使用的决策函数
 * - reduceGesturePhase() —— 手势状态机纯函数
 * - deriveCardPresentationState() —— 卡片展示态推导
 */
class VoiceGestureCommitTest {

    // ═══ shouldCommitTranscript 两事件 rendezvous 测试 ═══

    @Test
    fun `both events satisfied should commit`() {
        // 路径 A: onResults 先来（finalTranscript 已缓存），release 后到达 → 提交
        assertTrue(shouldCommitTranscript(
            physicalReleased = true,
            finalTranscript = "改写为温柔风格",
            cancelled = false,
            submitted = false
        ))
    }

    @Test
    fun `cancelled with non-blank transcript should NOT commit`() {
        assertFalse(shouldCommitTranscript(
            physicalReleased = true,
            finalTranscript = "改写为温柔风格",
            cancelled = true,
            submitted = false
        ))
    }

    @Test
    fun `not released with transcript should NOT commit — must wait for release`() {
        // onResults 已到达但松手还没发生——不提交
        assertFalse(shouldCommitTranscript(
            physicalReleased = false,
            finalTranscript = "改写为温柔风格",
            cancelled = false,
            submitted = false
        ))
    }

    @Test
    fun `released with null transcript should NOT commit — waiting for onResults`() {
        // release 先到达，onResults 尚未到达——不提交
        assertFalse(shouldCommitTranscript(
            physicalReleased = true,
            finalTranscript = null,
            cancelled = false,
            submitted = false
        ))
    }

    @Test
    fun `released with blank transcript should NOT commit`() {
        assertFalse(shouldCommitTranscript(
            physicalReleased = true,
            finalTranscript = "   ",
            cancelled = false,
            submitted = false
        ))
    }

    @Test
    fun `already submitted should NOT commit again — guarantees single submission`() {
        // submitted 标志保证永远只提交一次
        assertFalse(shouldCommitTranscript(
            physicalReleased = true,
            finalTranscript = "改写为温柔风格",
            cancelled = false,
            submitted = true
        ))
    }

    @Test
    fun `cancel after release but before results should NOT commit`() {
        // release 已到达，但 cancel 发生在 onResults 之前
        assertFalse(shouldCommitTranscript(
            physicalReleased = true,
            finalTranscript = null,
            cancelled = true,
            submitted = false
        ))
    }

    @Test
    fun `cancel after results but before release should NOT commit`() {
        // onResults 已到达，但 cancel 发生在 release 之前
        assertFalse(shouldCommitTranscript(
            physicalReleased = false,
            finalTranscript = "改写为温柔风格",
            cancelled = true,
            submitted = false
        ))
    }

    // ═══ reduceGesturePhase 测试 ═══

    @Test
    fun `IDLE plus DOWN transitions to PRESSING`() {
        assertEquals(GesturePhase.PRESSING, reduceGesturePhase(GesturePhase.IDLE, GestureEvent.DOWN))
    }

    @Test
    fun `PRESSING plus LONG_PRESS_REACHED transitions to RECORDING when can start`() {
        assertEquals(GesturePhase.RECORDING, reduceGesturePhase(GesturePhase.PRESSING, GestureEvent.LONG_PRESS_REACHED, true))
    }

    @Test
    fun `PRESSING plus LONG_PRESS_REACHED stays IDLE when cannot start recording`() {
        // 无权限/创建失败时不应进入 RECORDING
        assertEquals(GesturePhase.IDLE, reduceGesturePhase(GesturePhase.PRESSING, GestureEvent.LONG_PRESS_REACHED, false))
    }

    @Test
    fun `PRESSING plus UP_IN_BOUNDS transitions to IDLE — normal click`() {
        assertEquals(GesturePhase.IDLE, reduceGesturePhase(GesturePhase.PRESSING, GestureEvent.UP_IN_BOUNDS))
    }

    @Test
    fun `RECORDING plus UP_IN_BOUNDS transitions to RELEASED — normal release`() {
        assertEquals(GesturePhase.RELEASED, reduceGesturePhase(GesturePhase.RECORDING, GestureEvent.UP_IN_BOUNDS))
    }

    @Test
    fun `RECORDING plus UP_OUT_OF_BOUNDS transitions to CANCELLED — move out`() {
        assertEquals(GesturePhase.CANCELLED, reduceGesturePhase(GesturePhase.RECORDING, GestureEvent.UP_OUT_OF_BOUNDS))
    }

    @Test
    fun `RECORDING plus SYSTEM_CANCEL transitions to CANCELLED`() {
        assertEquals(GesturePhase.CANCELLED, reduceGesturePhase(GesturePhase.RECORDING, GestureEvent.SYSTEM_CANCEL))
    }

    @Test
    fun `RELEASED always transitions to IDLE`() {
        assertEquals(GesturePhase.IDLE, reduceGesturePhase(GesturePhase.RELEASED, GestureEvent.DOWN))
        assertEquals(GesturePhase.IDLE, reduceGesturePhase(GesturePhase.RELEASED, GestureEvent.UP_IN_BOUNDS))
    }

    @Test
    fun `CANCELLED always transitions to IDLE`() {
        assertEquals(GesturePhase.IDLE, reduceGesturePhase(GesturePhase.CANCELLED, GestureEvent.DOWN))
        assertEquals(GesturePhase.IDLE, reduceGesturePhase(GesturePhase.CANCELLED, GestureEvent.UP_IN_BOUNDS))
    }

    // ═══ deriveCardPresentationState 测试 ═══

    @Test
    fun `PROCESSING voice state shows Recognizing`() {
        val state = deriveCardPresentationState(
            isRecording = true,
            voiceState = VoiceRewriteState.PROCESSING,
            isRewriting = false,
            rewriteError = null,
            rewriteDone = false,
            isExpanded = false
        )
        assertEquals(SchemeCardPresentationState.Recognizing, state)
    }

    @Test
    fun `RECORDING voice state shows Recording`() {
        val state = deriveCardPresentationState(
            isRecording = true,
            voiceState = VoiceRewriteState.RECORDING,
            isRewriting = false,
            rewriteError = null,
            rewriteDone = false,
            isExpanded = false
        )
        assertEquals(SchemeCardPresentationState.Recording, state)
    }

    @Test
    fun `isRewriting shows Rewriting`() {
        val state = deriveCardPresentationState(
            isRecording = false,
            voiceState = VoiceRewriteState.IDLE,
            isRewriting = true,
            rewriteError = null,
            rewriteDone = false,
            isExpanded = false
        )
        assertEquals(SchemeCardPresentationState.Rewriting, state)
    }

    @Test
    fun `rewriteError shows RewriteError`() {
        val state = deriveCardPresentationState(
            isRecording = false,
            voiceState = VoiceRewriteState.IDLE,
            isRewriting = false,
            rewriteError = "改写失败",
            rewriteDone = false,
            isExpanded = false
        )
        assertEquals(SchemeCardPresentationState.RewriteError("改写失败"), state)
    }

    @Test
    fun `isExpanded shows Adjusting when nothing else active`() {
        val state = deriveCardPresentationState(
            isRecording = false,
            voiceState = VoiceRewriteState.IDLE,
            isRewriting = false,
            rewriteError = null,
            rewriteDone = false,
            isExpanded = true
        )
        assertEquals(SchemeCardPresentationState.Adjusting, state)
    }

    @Test
    fun `default shows Collapsed`() {
        val state = deriveCardPresentationState(
            isRecording = false,
            voiceState = VoiceRewriteState.IDLE,
            isRewriting = false,
            rewriteError = null,
            rewriteDone = false,
            isExpanded = false
        )
        assertEquals(SchemeCardPresentationState.Collapsed, state)
    }
}
