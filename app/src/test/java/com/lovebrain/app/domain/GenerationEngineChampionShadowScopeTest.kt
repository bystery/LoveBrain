package com.lovebrain.app.domain

import com.lovebrain.app.domain.champion.ChampionEngine
import com.lovebrain.app.domain.champion.ChampionEventStore
import com.lovebrain.app.domain.champion.ChampionPromptAssembler
import com.lovebrain.app.domain.champion.ContractGuard
import com.lovebrain.app.domain.champion.FileChampionEventStorage
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.StreamUsage
import com.lovebrain.app.model.buildGenerationInput
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 审计 S1-P0-2：SHADOW 额外请求不得绕过「仅看本轮」。
 *
 * - `onlyThisRound=true`（用户明确选了仅看本轮）时，**完全跳过**本次 SHADOW 额外请求：
 *   网关只见一次真实请求（主回复），shadow_log 一个字节都不写。
 * - `onlyThisRound=false` 正向对照：主回复之后照常追加一次冠军影子请求。
 *
 * 不留"无历史影子"的半套路径——影子请求是一次真实模型调用，
 * 用户的上下文范围选择对它同样是硬边界。
 */
class GenerationEngineChampionShadowScopeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── 替身 ──

    /** 计数网关：每次 generateStream 记一笔，影子与主回复走同一个可观测口 */
    private class CountingGateway : AiGateway {
        val streamCalls = mutableListOf<Pair<String, String>>()   // (system, user)

        override fun snapshotProviderConfig(): ProviderRequestConfig? = null
        override fun configForTicket(ticketId: String): ProviderRequestConfig? = ticketConfig.takeIf { it.ticketId == ticketId }
        override fun generateStream(
            systemPrompt: String, userPrompt: String, thinkingOverride: Int?,
            thinkingShapeIndex: Int, config: ProviderRequestConfig?
        ): Flow<StreamEvent> {
            streamCalls += systemPrompt to userPrompt
            return flowOf(
                StreamEvent.Chunk(REPLY_JSON),
                StreamEvent.Complete(REPLY_JSON, StreamUsage(promptTokens = 5, completionTokens = 5))
            )
        }
        override suspend fun generateRaw(systemPrompt: String, userPrompt: String) = REPLY_JSON
        override suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String) =
            throw UnsupportedOperationException()
        override fun parseReplyResponse(content: String): LoveBrainResponse =
            LoveBrainResponse(response = ReplySchemes(recommended = "辛苦了，早点休息"))

        companion object {
            val ticketConfig = ProviderRequestConfig(
                ticketId = "A", apiKey = "key", baseUrl = "https://p.example.com/v1/chat",
                model = "m", thinkingMode = 0, generateTimeoutMs = 5_000
            )
            /** 主回复合法返回（契约完好，守卫不改写） */
            const val REPLY_JSON =
                """{"response": {"recommended": "辛苦了，早点休息", "badBoy": "b", "playful": "p", "warm": "w"}, """ +
                    """"analysis": {"topic_status": "same", "topic_label": "日常"}}"""
        }
    }

    private class FakeSettings(
        var mode: String = "SHADOW",
        var scope: Set<String> = emptySet()
    ) : SettingsStorePort {
        override var thinkingMode: Int = 0
        override var outputMode: Int = 0
        override var panelMode: Int = 0
        override var counselingDraft: String = ""
        private var counselingResult: String? = null
        override fun saveCounselingResult(json: String) { counselingResult = json }
        override fun loadCounselingResult(): String? = counselingResult
        override fun clearCounselingResult() { counselingResult = null }
        private var counselingHistory: String? = null
        override fun saveCounselingHistory(json: String) { counselingHistory = json }
        override fun loadCounselingHistory(): String? = counselingHistory
        override fun clearCounselingHistory() { counselingHistory = null }
        private var todayCost: Pair<String, Double>? = null
        override fun saveTodayCost(dateStr: String, yuan: Double) { todayCost = dateStr to yuan }
        override fun loadTodayCost(): Pair<String, Double>? = todayCost
        override fun getWorkerTickets(): List<ProviderTicket> = emptyList()
        override fun setWorkerTickets(tickets: List<ProviderTicket>) {}
        override var activeTicketId: String? = null
        private val apiKeys = mutableMapOf<String, String>()
        override fun getWorkerApiKey(ticketId: String): String? = apiKeys[ticketId]
        override fun saveWorkerApiKey(ticketId: String, apiKey: String) { apiKeys[ticketId] = apiKey }
        override fun deleteWorkerApiKey(ticketId: String) { apiKeys.remove(ticketId) }
        override var totalGenerateCount: Int = 0
        override var totalCostYuan: Double = 0.0
        override var totalCopyCount: Int = 0
        override var totalAdoptCount: Int = 0
        override var totalRewriteCount: Int = 0
        override var hasCompletedOnboarding: Boolean = false
        override var introSeen: Boolean = false
        override var guideCursor: String = ""
        override var introStep: Int = 0
        override var captureEnabled: Boolean = false
        override var accessibilityDisclosureVersion: Int = 0
        override var captureAllowedPackages: Set<String> = emptySet()
        override var lastKbEditFile: String? = null
        override var connectionVerified: Boolean = false
        override var connectionVerifiedIdentity: String = ""
        override var championEngineMode: String
            get() = mode
            set(value) { mode = value }
        override var championEngineScope: Set<String>
            get() = scope
            set(value) { scope = value }
    }

    // ── 装配 ──

    private fun engineWith(gateway: CountingGateway): Pair<GenerationEngine, File> {
        val promptBuilder = mockkPromptBuilder()
        val settings = FakeSettings(mode = "SHADOW", scope = emptySet())   // 空集 = 全体
        val root = File(tmp.root, "champion")
        val champion = ChampionEngine(
            gateway = gateway,
            eventStore = ChampionEventStore(FileChampionEventStorage(File(root, "events"))),
            assembler = ChampionPromptAssembler(timestampBlock = { "" }, promptSource = { "" }),
            guard = ContractGuard(),
            settings = settings,
            clock = object : com.lovebrain.app.domain.port.Clock {
                override fun wallClock() = "2026-11-06 10:00"
                override fun today() = "2026-11-06"
                override fun epochMs() = 1_790_000_000_000L
            },
            shadowExecutor = { body -> runBlocking { body() } },   // 测试内联执行
            rootDir = root
        )
        return GenerationEngine(gateway, promptBuilder, champion) to root
    }

    private fun mockkPromptBuilder(): PromptBuilder {
        val pb = mockk<PromptBuilder>(relaxed = true)
        every { pb.buildSystemPrompt() } returns "system"
        every { pb.replyPromptAssetHash() } returns "assets"
        coEvery {
            pb.buildReplyUserPromptWithRefs(any(), any(), any(), any(), any(), any())
        } returns PromptBuilder.PromptBuildResult("user-full", emptyList())
        coEvery {
            pb.buildReplyUserPromptOnlyThisRound(any(), any())
        } returns PromptBuilder.PromptBuildResult("user-otr", emptyList())
        return pb
    }

    private fun input(onlyThisRound: Boolean) = buildGenerationInput(
        requestId = "req-shadow-scope",
        messages = listOf(
            ChatMessage(role = ChatMessage.Role.HER, content = "今天好累"),
            ChatMessage(role = ChatMessage.Role.ME, content = "辛苦了")
        ),
        userHint = "",
        knowledgeBase = KnowledgeBase(name = "kb", stage = "暧昧期"),
        intentConfig = IntentConfig(),
        corrections = emptyMap(),
        correctionsRevision = 0,
        onlyThisRound = onlyThisRound,
        aggressive = false,
        providerIdentity = CountingGateway.ticketConfig.toIdentity(),
        kbProfile = "",
        kbRevision = "rev",
        promptAssetHash = "assets"
    )

    // ── 判据 ──

    @Test
    fun `onlyThisRound skips the shadow request entirely`() = runTest {
        val gateway = CountingGateway()
        val (engine, root) = engineWith(gateway)

        val events = engine.replyStream(input(onlyThisRound = true)).toList()

        // 主回复正常完成
        assertTrue(events.any { it is com.lovebrain.app.model.ReplyCompleted })
        // 网关只见一次请求——影子请求被整块跳过，不存在"无历史影子"半套请求
        assertEquals("仅看本轮时只发主回复一次请求", 1, gateway.streamCalls.size)
        assertEquals("user-otr", gateway.streamCalls.single().second)
        // 影子日志一个字节都不写
        assertFalse(File(root, "shadow_log.jsonl").exists())
    }

    @Test
    fun `without onlyThisRound the shadow request still runs after the main reply`() = runTest {
        val gateway = CountingGateway()
        val (engine, root) = engineWith(gateway)

        val events = engine.replyStream(input(onlyThisRound = false)).toList()

        assertTrue(events.any { it is com.lovebrain.app.model.ReplyCompleted })
        // 正向对照：主回复 + 影子 = 两次请求；影子带完整上下文（full-range prompt）
        assertEquals("全量档主回复后照常追加一次影子请求", 2, gateway.streamCalls.size)
        assertEquals("user-full", gateway.streamCalls[0].second)
        assertTrue(gateway.streamCalls[1].second.length > 0)
        assertTrue("影子日志落盘", File(root, "shadow_log.jsonl").isFile)
        // R2-1 复钉：影子生成路径不写事件库（入库唯一入口在产品提交点）
        val store = ChampionEventStore(FileChampionEventStorage(File(root, "events")))
        assertEquals(0, runBlocking { store.events("kb__kb") }.size)
    }
}
