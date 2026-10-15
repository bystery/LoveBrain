package com.lovebrain.app.domain

import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ReplyDirective
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OngoingContextSelector 单测。
 *
 * 核心原则：
 * - plan.md 是长期存储，不是每轮必注入内容
 * - Stored != EligibleForCurrentTurn
 * - 默认拒绝注入
 * - 首次出现且不相关 → 不注入（删除旧的"首次允许注入一次"规则）
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

    /**
     * 首次出现且不相关 → 不注入
     * 旧测试名 `first appearance allows injection once` 已废弃
     */
    @Test
    fun `first unseen item does not inject into unrelated conversation`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "今天吃什么"),
            currentTurn = 1,
            currentTime = "2026-09-17 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        // 不相关 → 不注入，无论是否第一次出现
        assertEquals("不相关的首次出现不应注入", 0, result.eligibleItems.size)
    }

    @Test
    fun `unrelated message does not inject after first appearance`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"计划9-21见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00"}]"""

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "刚洗完澡"),
            currentTurn = 2,
            currentTime = "2026-09-16 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertEquals("冷却中且不相关不应注入", 0, result.eligibleItems.size)
    }

    // ─── F03（2026-10-15 书 §4）：二字滑窗整个退出判据 ───

    /**
     * 书点名那一例：事项"周末见面"被一句"周末还要加班"就触发——旧实现把
     * `extractRawKeywords` 的二字滑窗（它自己的 KDoc 都写着"太宽，会误召回"）挂在
     * 三个判决上，"周末"两个字重叠就够刷新证据。
     * 坏实现＝滑窗被挂回来：这一格第一句当场红（旧形状下 eligible 是 1 不是 0）。
     */
    @Test
    fun `an unrelated weekend complaint does not recall the weekend-meeting item`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("周末见面")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "周末还要加班"),
            currentTurn = 3,
            currentTime = "2026-09-19 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertEquals("不许只凭「周末」两个字重叠就注入旧事项（更不许因此刷新证据轮次）",
            0, result.eligibleItems.size)
    }

    /** 整词真的出现：照样命中——精修不是把召回降成零，只撤泛化切片 */
    @Test
    fun `the full item phrase in a message still recalls the item`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("周末见面")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "我们周末见面别迟到了"),
            currentTurn = 3,
            currentTime = "2026-09-19 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertEquals("整词出现必须照常命中", listOf("周末见面"), result.eligibleItems.map { it.name })
    }

    /**
     * 书 §4 第 3、4 条合一：持续意图**点名**该事项 ⇒ 相关、注入；
     * 但 lastEvidenceTurn **只认真实 HER/ME 文本**——注入过一次不许变成"有了新事实"，
     * 下一轮没有真实消息时它不许靠这一拍续命。
     */
    @Test
    fun `a deliberate intent mention injects without forging message evidence`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("周末见面")
        coEvery { knowledgeRepo.readIntent("kb") } returns IntentConfig(
            text = "这周把周末见面的事推进一下", enabled = true
        )

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "今天吃什么"),
            currentTurn = 7,
            currentTime = "2026-09-19 12:00",
            effectiveIntent = IntentConfig(text = "这周把周末见面的事推进一下", enabled = true)
        )
        val result = selector.selectForInjection("kb", ctx)

        assertEquals("意图点名的事项要在场上", listOf("周末见面"), result.eligibleItems.map { it.name })
        assertTrue(
            "lastEvidenceTurn 不许被意图文本推进（当前轮 7，实到 ${result.eligibleItems.single().lastEvidenceTurn}）",
            result.eligibleItems.single().lastEvidenceTurn < 7
        )
    }

    @Test
    fun `keyword match injects item`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "对了，周一几点见面？"),
            currentTurn = 5,
            currentTime = "2026-09-17 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

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
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"计划9-21见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00"}]"""

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "我刚吃完火锅"),
            currentTurn = 3,
            currentTime = "2026-09-20 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertEquals("日期临近不应单独授权注入", 0, result.eligibleItems.size)
    }

    @Test
    fun `DORMANT status after 5 turns without evidence`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"计划9-21见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00"}]"""

        val ctx = OngoingContextSelector.SelectionContext(
            messages = messages(ChatMessage.Role.HER to "今天好累"),
            currentTurn = 7,
            currentTime = "2026-09-20 12:00"
        )
        val result = selector.selectForInjection("kb", ctx)

        assertEquals("DORMANT 且不相关不应注入", 0, result.eligibleItems.size)
        assertTrue("应有事项记录", result.allItems.isNotEmpty())
    }

    @Test
    fun `DORMANT item reactivates on keyword match`() = runBlocking {
        coEvery { knowledgeRepo.readPlanActive("kb") } returns planWithItem("计划9-21见面")
        coEvery { knowledgeRepo.readFile("kb", "moment/ongoing_cooldown.json") } returns
            """[{"name":"计划9-21见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00"}]"""

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
