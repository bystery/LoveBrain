package com.lovebrain.app.data

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  （计 3 条用例）：捕获事件总线兜底回归。
 *
 * ① 迟订阅可收最近一条（replay = 1 语义，去重由 FloatingService.addClipIfNew 兜底）；
 * ② 无订阅者日志报告 replay 保留而非"丢失"，且只含长度不含内容；
 * ③ emitCapturedMessage 在 replay 缓冲可用时返回 true。
 * 注意：EventBus 为单例且带重放缓存，断言一律以本用例自发值为准。
 */
class EventBusCaptureTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun late_subscriber_can_receive_last_capture() {
        val anchor = "wlk10-replay-anchor"
        EventBus.emitCapturedMessage(anchor)
        // replay = 1：未订阅时发出的最近一条仍在重放缓存内，迟订阅可消费
        assertEquals(anchor, EventBus.capturedMessages.replayCache.lastOrNull()?.text)
    }

    @Test
    fun no_subscriber_log_reports_replay_not_loss() {
        val secret = "wlk10-secret-text"
        val msg = slot<String>()
        every { Log.w(any(), capture(msg)) } returns 0

        EventBus.emitCapturedMessage(secret) // 测试全程无订阅者 → 日志应报告 replay 保留

        assertTrue(msg.isCaptured, "无订阅者应记日志")
        assertTrue(msg.captured.contains("长度=${secret.length}"), "日志应含长度")
        assertFalse(msg.captured.contains(secret), "日志不得含捕获内容")
        assertFalse(msg.captured.contains("丢失"), "日志不得写丢失，replay=1 不是丢失")
    }

    @Test
    fun emitCapturedMessage_returns_true_when_replay_accepts_event() {
        val accepted = EventBus.emitCapturedMessage("wlk10-accept-test")
        assertTrue(accepted, "replay 缓冲可用时 tryEmit 应成功返回 true")
    }
}
