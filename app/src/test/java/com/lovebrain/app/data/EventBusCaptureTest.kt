package com.lovebrain.app.data

import android.util.Log
import com.lovebrain.app.AppConfig
import com.lovebrain.app.service.ClipBurstDedup
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
 *
 * 后面那三格是 §15.1（K10/K11）要的**300ms 去重边界**：判据本体是
 * `service/ClipBurstDedup`（纯函数，不碰 SystemClock，所以 JVM 就能量到边界）。
 * §18.1 明写这一族是"会造成真实行为回归"的那一类，值得钉；每条都写着**怎么坏会红**。
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

    // ═══════════ §15.1：300ms 去重边界（K10 / K11）═══════════

    /**
     * 不同文本**本来就不该被这条规则阻挡**（§15.1 原话）。
     *
     * 怎么坏会红：
     * - 把这条判据写成"所有消息的统一冷却窗"（丢掉 `text == previousText`）⇒ 第二句红
     *   （A 之后 1ms 的 B 被吞掉，正是用户报"依次捕获 A、B 只进一条"的那个形状）；
     * - 把文本比较丢掉整个方向反过来（永远放行）⇒ 第三句红（同一次手势的系统连发会入库两次）。
     */
    @Test
    fun `去重窗只认同文本，不同文本一律放行`() {
        val t0 = 2_000_000L
        // 这一条链上的第一次：盘上没有"上一条"（previousText = null）⇒ 一定进
        assertFalse(
            ClipBurstDedup.isBurstDuplicate(text = "A", previousText = null, nowMs = t0, previousMs = 0L),
            "第一次捕获不该被去重挡住"
        )
        // 快速依次捕获 A、B：相隔 1ms，文本不同 ⇒ 两条都要进
        assertFalse(
            ClipBurstDedup.isBurstDuplicate(text = "B", previousText = "A", nowMs = t0 + 1L, previousMs = t0),
            "不同文本不许被这条规则阻挡"
        )
        // 同一次手势的系统连发：同一句、窗内 ⇒ 按约定合并
        assertTrue(
            ClipBurstDedup.isBurstDuplicate(text = "B", previousText = "B", nowMs = t0 + 1L, previousMs = t0),
            "窗内的同一文本应当合并"
        )
    }

    /**
     * 窗口宽度就是用户要的那一格 300ms，而且**过一格就能再捕同一句**。
     *
     * 怎么坏会红：
     * - 常量回到 1500 ⇒ 第二句红（窗口宽度不是用户点名的那一格）；
     * - 边界比较从 `<=` 改成 `<` ⇒ 第三句红（正好压在窗口那一毫秒上的连发不再合并）；
     * - 比较方向写反 / 窗口变成"永远拦" ⇒ 第四句红（超过窗口仍捕不进来）。
     */
    @Test
    fun `去重窗口收敛到 300ms 且边界按闭区间合并`() {
        val window = ClipBurstDedup.windowMs
        assertEquals(
            AppConfig.BURST_DEDUP_WINDOW_MS,
            window,
            "窗口宽度全仓只该有 AppConfig 那一颗，判据不许自己抄一份秒数"
        )
        assertEquals(300L, window, "§15.1：这一格收敛到 300ms（1500 是改错层之前的读数）")
        assertTrue(window > 0L, "窗口写成 0 就等于把重复来源合并整条丢掉")

        val t0 = 1_000_000L
        assertTrue(
            ClipBurstDedup.isBurstDuplicate(text = "A", previousText = "A", nowMs = t0 + window, previousMs = t0),
            "恰好压在窗口那一毫秒上的同一文本仍要合并"
        )
        assertFalse(
            ClipBurstDedup.isBurstDuplicate(
                text = "A",
                previousText = "A",
                nowMs = t0 + window + 1L,
                previousMs = t0
            ),
            "超过去重窗一毫秒，同一句就该能再次捕获"
        )
    }

    /**
     * 300ms **只收这一格**：事件配对与待捕获寿命都不跟着动（§15.1 末句"不继续盲改常量"）。
     *
     * 怎么坏会红：有人把"长按 ↔ 复制菜单"那条配对预算一起收敛成 300ms ⇒ 这句红
     * （慢菜单 >3s 就捕不到了，而用户的原话只针对重复合并那一格）。
     */
    @Test
    fun `待捕获事件寿命不吃这颗 300ms`() {
        assertEquals(
            30_000L,
            AppConfig.PENDING_MAX_AGE_MS,
            "pending 寿命是事件配对预算，不是洪峰去重，本轮不跟着收敛"
        )
    }
}
