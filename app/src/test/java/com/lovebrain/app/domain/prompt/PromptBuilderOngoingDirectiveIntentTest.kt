package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.OngoingContextSelector
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.KnowledgeBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3（外部审计 P1 复核）：进行中事项筛选必须消费**冻结的**军师备注与有效持续意图，
 * 而不是从消息正文重新推导（F1.1：备注不是消息）。
 *
 * 修复前的坏形状：`buildKnowledgeSection` 用 `splitMessages(messages)` 从消息里猜 IDEA，
 * 但冻结路径（`GenerationInput.toChatMessages()`）根本不产生 Role.IDEA 消息——
 * directive 恒为 null，`intentConfig` 也从未传进 selector。生产路径上军师备注与
 * 持续意图对事项筛选完全不可见。
 *
 * 判据全部走 `PromptBuilder` 公开入口吐出的 prompt 文本与落盘的冷却文件，
 * 不检查源码符号（恒真形态不在此处）。
 */
class PromptBuilderOngoingDirectiveIntentTest {

    private companion object {
        const val PLAN = "# 事项计划\n\n## 进行中\n周末见面 | 进行中 | 已约→等她定日子\n"
    }

    private fun fixture(
        plan: String = PLAN,
        cooldownJson: String = ""
    ): Pair<PromptBuilder, InMemoryKnowledgePort> {
        val port = InMemoryKnowledgePort()
        port.seedLibrary(KnowledgeBase(name = "kb", displayName = "测试库", stage = "待确定", turnCount = 3, active = true))
        port.seed("kb", "understand/me.md", "我：喜欢手冲咖啡")
        port.seed("kb", "understand/her.md", "她：猫奴，最近在准备雅思")
        port.seed("kb", "understand/warmth.md", "温度：轻松，会主动找我聊")
        port.seed("kb", "understand/style.md", "偏好：短句、少表情")
        port.seed("kb", "memory/lessons.md", "")
        port.seed("kb", "moment/scene.md", "")
        port.seed("kb", "moment/recent.md", "")
        port.seed("kb", "moment/topic.md", "")
        port.seed("kb", "moment/plan.md", plan)
        if (cooldownJson.isNotBlank()) port.seed("kb", "moment/ongoing_cooldown.json", cooldownJson)
        val clock = FixedClock()
        val builder = PromptBuilder(
            // 用户 prompt 路径在 stage=待确定 且非 aggressive 时不应读任何资产；
            // 真有人给这条链路加了资产读取，这一格当场炸，不是静默绿。
            PromptSourcePort { path -> error("用户 prompt 路径不该读资产：$path") },
            port,
            OngoingContextSelector(port, clock),
            clock
        )
        return builder to port
    }

    /** 真实消息里**不出现**事项词——注入只能来自冻结备注/意图，不能来自消息正文 */
    private val unrelatedMsgs = listOf(
        ChatMessage(id = "m1", role = ChatMessage.Role.HER, content = "今天吃了什么"),
        ChatMessage(id = "m2", role = ChatMessage.Role.ME, content = "点了外卖")
    )

    @Test
    fun `frozen advisor note and effective intent make the item injectable`() = runBlocking {
        val (builder, _) = fixture()
        val prompt = builder.buildReplyUserPrompt(
            KnowledgeBase(name = "kb", displayName = "测试库", stage = "待确定", turnCount = 3, active = true),
            unrelatedMsgs,
            "军师要求：把周末见面的事安排一下",
            intentConfig = IntentConfig(text = "这周推进周末见面", enabled = true)
        )

        assertTrue("军师备注点名的事项必须进入注入：$prompt", prompt.contains("# 【进行中事项】"))
        assertTrue("事项本体要在场上：$prompt", prompt.contains("周末见面"))
    }

    @Test
    fun `without note or intent the same conversation injects nothing`() = runBlocking {
        val (builder, _) = fixture()
        val prompt = builder.buildReplyUserPrompt(
            KnowledgeBase(name = "kb", displayName = "测试库", stage = "待确定", turnCount = 3, active = true),
            unrelatedMsgs,
            ""
        )

        assertFalse("无备注无意图无消息证据时不得注入：$prompt", prompt.contains("# 【进行中事项】"))
    }

    /**
     * 「过期」在冻结快照链路上的判据是 `isIntentEffective` 的 status 防线：
     * 旧终态（EXPIRED）即使 enabled=true 带着正文，也不得给事项筛选授权
     * （归一化读口那道刀在 KnowledgeIntentService.decode，这里只认冻结快照）。
     */
    @Test
    fun `expired intent does not authorize injection`() = runBlocking {
        val (builder, _) = fixture()
        val prompt = builder.buildReplyUserPrompt(
            KnowledgeBase(name = "kb", displayName = "测试库", stage = "待确定", turnCount = 3, active = true),
            unrelatedMsgs,
            "",
            intentConfig = IntentConfig(text = "这周推进周末见面", enabled = true, status = IntentStatus.EXPIRED)
        )

        assertFalse("过期意图不得授权事项注入：$prompt", prompt.contains("# 【进行中事项】"))
    }

    @Test
    fun `disabled intent does not authorize injection`() = runBlocking {
        val (builder, _) = fixture()
        val prompt = builder.buildReplyUserPrompt(
            KnowledgeBase(name = "kb", displayName = "测试库", stage = "待确定", turnCount = 3, active = true),
            unrelatedMsgs,
            "",
            intentConfig = IntentConfig(text = "这周推进周末见面", enabled = false)
        )

        assertFalse("关着的意图不得授权事项注入：$prompt", prompt.contains("# 【进行中事项】"))
    }

    /**
     * 备注不是消息（F1.1）：备注正文只出现在军师备注段，不进对话围栏、
     * 不冒充 HER/ME 文本——因此**不得刷新 lastEvidenceTurn**（真实消息证据那一格）。
     * 冷却文件里预置 lastEvidenceTurn=1，备注点名注入之后必须原样保留；
     * 若有人把备注重新读成消息证据，它会被推进成 currentTurn=3，当场红。
     */
    @Test
    fun `note text stays out of the chat fence and never forges message evidence`() = runBlocking {
        val note = "军师要求：把周末见面的事安排一下"
        val seededCooldown =
            """[{"name":"周末见面","lastInjectedTurn":1,"lastInjectedTime":"2026-09-15 10:00","lastEvidenceTurn":1}]"""
        val (builder, port) = fixture(cooldownJson = seededCooldown)
        val prompt = builder.buildReplyUserPrompt(
            KnowledgeBase(name = "kb", displayName = "测试库", stage = "待确定", turnCount = 3, active = true),
            unrelatedMsgs,
            note
        )

        assertTrue("备注点名的事项应注入", prompt.contains("# 【进行中事项】"))
        assertEquals(
            "备注正文只能出现在军师备注段一处（进围栏=被当成消息）",
            1,
            Regex("军师要求").findAll(prompt).count()
        )
        assertTrue("军师备注段在场", prompt.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))

        val cooldownJson = port.dump()["kb/moment/ongoing_cooldown.json"].orEmpty()
        assertTrue("冷却文件应已写入", cooldownJson.contains("周末见面"))
        assertTrue(
            "lastEvidenceTurn 必须保持 1（备注不是真实消息证据）",
            cooldownJson.contains("\"lastEvidenceTurn\":1")
        )
        assertFalse(
            "lastEvidenceTurn 不得被备注推进到本轮（3）",
            cooldownJson.contains("\"lastEvidenceTurn\":3")
        )
    }
}
