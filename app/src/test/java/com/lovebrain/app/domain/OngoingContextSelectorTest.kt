package com.lovebrain.app.domain

import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ReplyDirective
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P0-7/P0-8: OngoingContextSelector 单测。
 *
 * 核心原则：
 * - plan.md 是长期存储，不是每轮必注入内容
 * - 默认拒绝注入
 * - 日期临近不得单独授权注入
 * - ReplyDirective 是真实 relevance signal
 * - DORMANT 是真实状态
 * - selector 缺失时 fail closed
 */
class OngoingContextSelectorTest {

    private lateinit var knowledgeRepo: KnowledgeRepository
    private lateinit var selector: OngoingContextSelector

    @Before
    fun setup() {
        knowledgeRepo = mockk(relaxed = true)
        selector = OngoingContextSelector(knowledgeRepo)

        // 默认无冷却记录
        coEvery { knowledgeRepo.readFile(any(), "moment/ongoing_cooldown.json") } returns ""
        // 默认无持续意图
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        // 默认允许写入冷却文件
        coEvery { knowledgeRepo.writeFile(any(), any(), any()) } returns Unit
    }

    private fun planWithItem(name: String, status: String = "进行中", chain: String = "[2026-09-15 10:00]约定（当前）"): String {
        return "# 事项计划\n\n## 进行中\n$name | $status | $chain\n"
    }

    private fun messages(vararg pairs: Pair<ChatMessage.Role, String>): List<ChatMessage> {
        return pairs.mapIndexed { i, (role, text) -> ChatMessage(id = "msg-$i", role = role, content = text) }
    }

    @Test
    fun `empty plan yields empty result`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns ""

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "你好"),
            currentTurn = 1,
            currentTime = "2026-09-17 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertTrue("空 plan 应返回空 eligible", result.eligibleItems.isEmpty())
    }

    @Test
    fun `first appearance allows injection once`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "今天吃什么"),
            currentTurn = 1,
            currentTime = "2026-09-17 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        // 第一次出现（无冷却记录）允许注入一次
        assertEquals("首次出现应允许注入", 1, result.eligibleItems.size)
    }

    @Test
    fun `unrelated message does not inject after first appearance`() = runBlocking {
        // 模拟第 2 轮——已有冷却记录
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"计划9-21见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00"}]"""

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "刚洗完澡"),
            currentTurn = 2,
            currentTime = "2026-09-16 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        // 第 2 轮、不相关、在冷却中 -> 不注入
        assertEquals("冷却中且不相关不应注入", 0, result.eligibleItems.size)
    }

    @Test
    fun `keyword match injects item`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "对了，周一几点见？"),
            currentTurn = 5,
            currentTime = "2026-09-17 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        // "见面" 关键词命中 "见面"
        assertTrue("关键词匹配应注入", result.eligibleItems.isNotEmpty())
    }

    @Test
    fun `ReplyDirective mentioning item injects it`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "今天吃什么"),
            currentTurn = 3,
            currentTime = "2026-09-17 12:00",
            replyDirective = ReplyDirective("提一下见面的事")
        )
        val result = selector.selectForInjection("kb", ctx)

        assertTrue("ReplyDirective 提到事项应注入", result.eligibleItems.isNotEmpty())
    }

    @Test
    fun `date proximity alone does NOT inject`() = runBlocking {
        // P0-7: 日期临近不得单独授权注入
        // 事项名有日期 "9-21见面"，当前时间 2026-09-20，但消息内容不相关
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")
        // 模拟已有冷却记录（不是第一次出现）
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"计划9-21见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00"}]"""

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "我刚吃完火锅"),
            currentTurn = 3,
            currentTime = "2026-09-20 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        // 日期临近但消息不相关 -> 不注入
        assertEquals("日期临近不应单独授权注入", 0, result.eligibleItems.size)
    }

    @Test
    fun `DORMANT status after 5 turns without evidence`() = runBlocking {
        // P0-7: 连续 N 轮无新证据 -> DORMANT
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"计划9-21见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00"}]"""

        // 第 7 轮（距离上次注入 6 轮，超过 DORMANT_TURNS=5）
        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "今天好累"),
            currentTurn = 7,
            currentTime = "2026-09-20 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        // DORMANT + 不相关 -> 不注入
        assertEquals("DORMANT 且不相关不应注入", 0, result.eligibleItems.size)
        // 但 allItems 中应有 DORMANT 状态
        assertTrue("应有事项记录", result.allItems.isNotEmpty())
    }

    @Test
    fun `DORMANT item reactivates on keyword match`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"计划9-21见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00"}]"""

        // 第 7 轮（DORMANT），但消息提到 "见面"
        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "对了，见面的事怎么说"),
            currentTurn = 7,
            currentTime = "2026-09-20 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertTrue("DORMANT 但关键词命中应注入", result.eligibleItems.isNotEmpty())
    }

    @Test
    fun `FINISHED items never inject`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("旧事项", "已完成")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "旧事项怎么样了"),
            currentTurn = 1,
            currentTime = "2026-09-17 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertEquals("已完成事项不应注入", 0, result.eligibleItems.size)
    }

    @Test
    fun `CANCELLED items never inject`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("取消的事项", "已取消")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "取消的事项"),
            currentTurn = 1,
            currentTime = "2026-09-17 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertEquals("已取消事项不应注入", 0, result.eligibleItems.size)
    }
}
