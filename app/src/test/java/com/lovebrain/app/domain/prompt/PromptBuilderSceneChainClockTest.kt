package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场景链条目的龄标注/过期判断必须走注入的 Clock。
 *
 * 以前 `SceneChainInjection.transform` 的默认参数直接读系统钟，FixedClock 冻不住它，
 * 于是"跨 2h 边界后不再注入"这条分支只能靠真等两小时才测得到。这里用固定时钟
 * 把条目从 1.5h 拨到 2.5h，断言龄标注跟着走、过期条目整条消失。
 */
class PromptBuilderSceneChainClockTest {

    private val clock = FixedClock()
    private val kb = KnowledgeBase(name = "kb", stage = "暧昧期")

    private fun builder(port: InMemoryKnowledgePort) =
        PromptBuilder(PromptSourcePort { "" }, port, clock)

    /** 相对注入时钟的条目行；不写死日期，机器时区不同也测同一件事 */
    private fun sceneLine(minutesAgo: Long): String {
        val ts = TimeFmt.format(clock.epochMs() - minutesAgo * 60_000L)
        return "- [$ts] 她：在加班|src=m1|spk=HER|subj=她\n"
    }

    private fun port(minutesAgo: Long): InMemoryKnowledgePort {
        val port = InMemoryKnowledgePort()
        port.seedLibrary(kb)
        port.seed("kb", "understand/me.md", "我：喜欢手冲咖啡")
        port.seed("kb", "understand/her.md", "她：猫奴")
        port.seed("kb", "moment/topic.md", "")
        port.seed("kb", "moment/scene.md", sceneLine(minutesAgo))
        return port
    }

    private val messages = listOf(
        ChatMessage(id = "m1", role = ChatMessage.Role.HER, content = "今天好累")
    )

    @Test
    fun `scene age is computed from the injected clock not the wall clock`() = runBlocking {
        val builder = builder(port(minutesAgo = 90))

        val fresh = builder.buildReplyUserPrompt(kb, messages)
        assertTrue("新鲜条目应按注入时钟标成 1 小时前：\n$fresh", fresh.contains("1小时前"))
        assertTrue("事实正文仍在", fresh.contains("在加班"))

        clock.advanceHours(1) // 2.5h > SCENE_CHAIN_MAX_HOURS(2)
        val stale = builder.buildReplyUserPrompt(kb, messages)
        assertFalse("超过 2h 的条目必须整条不再注入：\n$stale", stale.contains("在加班"))
        assertFalse("此刻段没有内容时连段头都不出", stale.contains(PromptKnowledgeSection.MOMENT_HEADER))
    }
}
