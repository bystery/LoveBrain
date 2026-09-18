package com.lovebrain.app.domain

import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P0-8: 周一见面 11 轮回归测试。
 *
 * 场景：
 * 第 1 轮：PARTNER 说"周一见吧"，USER 说"好" → 创建 meeting event
 * 第 2-10 轮：完全不相关的日常对话（吃什么/洗澡/看剧/累/下课/睡觉等）
 * 第 11 轮：PARTNER 说"对了，周一几点见？" → 重新相关
 *
 * 要求：
 * - 第 2-10 轮 OngoingContextSelector.eligibleItems 连续为 empty
 * - 最终 Prompt 不包含"周一见面"/"见面再说"等
 * - 第 11 轮 selector 应重新取出对应 meeting event
 */
class MeetingPollutionRegressionTest {

    private lateinit var knowledgeRepo: KnowledgeRepository
    private lateinit var selector: OngoingContextSelector

    @Before
    fun setup() {
        knowledgeRepo = mockk(relaxed = true)
        selector = OngoingContextSelector(knowledgeRepo)

        // plan.md 中有一个"周一见面"事项
        coEvery { knowledgeRepo.readPlanActive("kb") } returns
            "# 事项计划\n\n## 进行中\n周一见面 | 进行中 | [2026-09-15 20:00]约定（当前）\n"

        // 默认无持续意图
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.writeFile(any(), any(), any()) } returns Unit
    }

    private fun ctx(turn: Int, vararg pairs: Pair<ChatMessage.Role, String>): OngoingContextSelector.SelectionContext {
        val msgs = pairs.mapIndexed { i, (role, text) -> ChatMessage(id = "msg-$turn-$i", role = role, content = text) }
        return OngoingContextSelector.SelectionContext(
            messages = msgs,
            currentTurn = turn,
            currentTime = "2026-09-${15 + turn} 12:00"
        )
    }

    @Test
    fun `round 1 injects because message contains keyword`() = runBlocking {
        // P0-6: 第 1 轮注入是因为消息含"周一""见"关键词，不是因为"首次出现"
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns ""

        val result = selector.selectForInjection("kb", ctx(1,
            ChatMessage.Role.HER to "周一见吧",
            ChatMessage.Role.ME to "好"
        ))

        assertEquals("第 1 轮应注入（关键词命中）", 1, result.eligibleItems.size)
    }

    @Test
    fun `rounds 2-10 do not inject meeting event`() = runBlocking {
        // 模拟第 1 轮已注入（冷却记录存在）
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"周一见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 20:00"}]"""

        val unrelatedMessages = listOf(
            ChatMessage.Role.HER to "今天吃什么",
            ChatMessage.Role.HER to "刚洗完澡",
            ChatMessage.Role.HER to "这剧挺好看的",
            ChatMessage.Role.HER to "今天好累",
            ChatMessage.Role.HER to "刚下课",
            ChatMessage.Role.HER to "准备睡了",
            ChatMessage.Role.HER to "早上好",
            ChatMessage.Role.HER to "中午吃的面条",
            ChatMessage.Role.HER to "下午没课"
        )

        for ((index, msg) in unrelatedMessages.withIndex()) {
            val turn = index + 2  // 第 2-10 轮
            val result = selector.selectForInjection("kb", ctx(turn, msg))

            assertEquals("第 $turn 轮不应注入（消息: ${msg.second}）", 0, result.eligibleItems.size)
        }
    }

    @Test
    fun `round 11 reactivates meeting event on keyword match`() = runBlocking {
        // 模拟第 1 轮已注入（冷却记录存在）
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"周一见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 20:00"}]"""

        // 第 11 轮：PARTNER 说"对了，周一几点见？"
        val result = selector.selectForInjection("kb", ctx(11,
            ChatMessage.Role.HER to "对了，周一几点见？"
        ))

        assertTrue("第 11 轮关键词命中应注入", result.eligibleItems.isNotEmpty())
        assertEquals("注入的应是周一见面事项", "周一见面", result.eligibleItems[0].name)
    }

    @Test
    fun `full 11-round sequence`() = runBlocking {
        // 完整 11 轮序列测试
        // 第 1 轮（注入因为消息含关键词"周一""见"）
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns ""
        val r1 = selector.selectForInjection("kb", ctx(1,
            ChatMessage.Role.HER to "周一见吧",
            ChatMessage.Role.ME to "好"
        ))
        assertEquals("第 1 轮应注入（关键词命中）", 1, r1.eligibleItems.size)

        // 模拟第 1 轮后冷却已写入
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"周一见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 20:00"}]"""

        // 第 2-10 轮
        val rounds = listOf(
            ChatMessage.Role.HER to "今天吃什么",
            ChatMessage.Role.HER to "刚洗完澡",
            ChatMessage.Role.HER to "这剧挺好看的",
            ChatMessage.Role.HER to "今天好累",
            ChatMessage.Role.HER to "刚下课",
            ChatMessage.Role.HER to "准备睡了",
            ChatMessage.Role.HER to "早上好",
            ChatMessage.Role.HER to "中午吃的面条",
            ChatMessage.Role.HER to "下午没课"
        )

        for ((i, msg) in rounds.withIndex()) {
            val turn = i + 2
            val r = selector.selectForInjection("kb", ctx(turn, msg))
            assertEquals("第 $turn 轮不应注入", 0, r.eligibleItems.size)
        }

        // 第 11 轮
        val r11 = selector.selectForInjection("kb", ctx(11,
            ChatMessage.Role.HER to "对了，周一几点见？"
        ))
        assertTrue("第 11 轮应重新注入", r11.eligibleItems.isNotEmpty())
    }

    @Test
    fun `date proximity does not pollute unrelated conversation`() = runBlocking {
        // P0-7: 日期临近不单独授权注入
        // 事项名"周一见面"没有结构化日期（没有 M-D 格式），所以 extractEventDate 返回 null
        // 但即使有日期，也不应单独授权注入
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"周一见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 20:00"}]"""

        // 假设是见面前一天
        val result = selector.selectForInjection("kb",
            OngoingContextSelector.SelectionContext(
                messages = listOf(ChatMessage(id = "m0", role = ChatMessage.Role.HER, content = "我刚吃完火锅")),
                currentTurn = 5,
                currentTime = "2026-09-20 18:00"  // 假设见面是 9-21
            )
        )

        // 消息内容与"见面"无关 -> 不注入
        assertEquals("日期临近但消息不相关不应注入", 0, result.eligibleItems.size)
    }
}
