package com.lovebrain.app.domain

import android.content.Context
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.SystemClock
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clock 端口是不是摆设，用这几格说清楚。
 *
 * 复核 §4 的 DIP 目标把 `Clock` 与 AiGateway / 知识端口并列列出，但"有个接口"不等于
 * "时间可控"：如果 domain 里还是自己 `TimeFmt.now()`，注入点就是装饰，
 * 依赖时间的分支照样测不到（§9 第 3 条要求"测试必须真的走进生产路径"）。
 *
 * 所以这里断言的是**落盘内容里那个时间串就是注入的那个**，
 * 以及把时钟拨快之后，第二轮的块头跟着变。
 */
class ClockWiringTest {

    private class Harness(val port: InMemoryKnowledgePort, val recorder: TopicRecorder)

    private fun harness(clock: com.lovebrain.app.domain.port.Clock): Harness {
        val port = InMemoryKnowledgePort()
        port.seedLibrary(KnowledgeBase(name = "kb", displayName = "kb"))
        port.seed("kb", "moment/topic.md", "- [2026-09-24 08:00] 正在聊：旧话题")
        port.seed("kb", "moment/recent.md", "")
        port.seed("kb", "moment/scene.md", "")
        port.seed("kb", "moment/plan.md", "# 事项计划\n")
        return Harness(port, TopicRecorder(port, clock = clock))
    }

    private fun record(h: Harness, id: String, text: String) = runBlocking {
        h.recorder.record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = listOf(
                ChatMessage(id = id, role = ChatMessage.Role.HER, content = text),
                ChatMessage(id = id + "-me", role = ChatMessage.Role.ME, content = "好")
            ),
            scheme = null,
            topicStatus = "same",
            topicLabel = ""
        )
    }

    @Test
    fun `the round block header is the injected clock, not wall time`() {
        val clock = FixedClock()
        val h = harness(clock)
        record(h, "m1", "下周我要去考雅思了")

        val recent = runBlocking { h.port.readFile("kb", "moment/recent.md") }
        assertTrue(
            "recent.md 里必须出现注入的那个时间戳；实际内容：\n$recent",
            recent.contains(clock.wallClock())
        )
        // 反向：不能同时把真实系统时间写进去——那说明这条路径还在自己读钟
        val real = SystemClock.wallClock()
        if (real != clock.wallClock()) {
            assertFalse("落盘内容里不该出现未注入的系统时间 $real：\n$recent", recent.contains(real))
        }
    }

    @Test
    fun `advancing the clock changes what the next round stamps`() {
        val clock = FixedClock()
        val h = harness(clock)
        record(h, "m1", "第一句")
        val first = runBlocking { h.port.readFile("kb", "moment/recent.md") }
        val firstHeader = first.lines().first { it.startsWith("- [") }

        clock.advanceMinutes(45)
        record(h, "m2", "第二句")
        val second = runBlocking { h.port.readFile("kb", "moment/recent.md") }

        assertTrue("第二轮要用拨过之后的时间：\n$second", second.contains(clock.wallClock()))
        assertFalse("第一轮那次记录不该用上后来拨出来的时间", first.contains(clock.wallClock()))
        assertTrue("第一轮的原块头要留在文件里", second.contains(firstHeader))
    }

    @Test
    fun `prompt timestamp section comes from the port too`() {
        val clock = FixedClock()
        val builder = PromptBuilder(mockk<Context>(relaxed = true), InMemoryKnowledgePort(), null, clock)

        assertTrue(
            "buildTimestampPrompt 必须用注入时间：${builder.buildTimestampPrompt()}",
            builder.buildTimestampPrompt().startsWith("【当前时间】" + clock.wallClock())
        )

        clock.advanceHours(3)
        assertTrue(
            "同一个 builder 换个时间就该给出新的时间——它没有偷偷缓存墙钟",
            builder.buildTimestampPrompt().contains(clock.wallClock())
        )
    }

    /** 默认值必须是真钟：不然生产会冻在一个假时间上 */
    @Test
    fun `production default is the system clock`() {
        val h = harness(FixedClock())
        val plain = TopicRecorder(h.port)
        runBlocking {
            plain.record(
                kb = KnowledgeBase(name = "kb", displayName = "kb"),
                messages = listOf(
                    ChatMessage(id = "d1", role = ChatMessage.Role.HER, content = "没注入时钟的默认路径")
                ),
                scheme = null,
                topicStatus = "same",
                topicLabel = ""
            )
        }
        val recent = runBlocking { h.port.readFile("kb", "moment/recent.md") }
        assertTrue(
            "不注入时仍要写当下时间（默认参数没接错）：\n$recent",
            recent.contains(SystemClock.wallClock())
        )
    }
}
