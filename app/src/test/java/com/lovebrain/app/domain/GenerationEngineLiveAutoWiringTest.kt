package com.lovebrain.app.domain

import com.lovebrain.app.domain.champion.ChampionEngine
import com.lovebrain.app.domain.champion.ChampionEventStore
import com.lovebrain.app.domain.champion.ChampionLiveContext
import com.lovebrain.app.domain.champion.ChampionMessage
import com.lovebrain.app.domain.champion.ChampionPromptAssembler
import com.lovebrain.app.domain.champion.ChampionTurnStrategy
import com.lovebrain.app.domain.champion.ContractGuard
import com.lovebrain.app.domain.champion.FileChampionEventStorage
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyChunkReset
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyEvent
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.ReplySchemesArrived
import com.lovebrain.app.model.ReplySchemesReset
import com.lovebrain.app.model.ReplyStarted
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.StreamUsage
import com.lovebrain.app.model.buildGenerationInput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * guide-6 §15.6：GenerationEngine LIVE_AUTO 生产接线（阶段 06 的**证据核心**）。
 *
 * §17 验收标准不是"Champion 代码存在"，而是：已选定的机制**真实影响正常产品回复**。
 * 这一族测试从 [GenerationEngine.replyStream]（生产调用入口）驱动：
 * - 冻结输入 [com.lovebrain.app.model.GenerationInput.championMode] /
 *   [com.lovebrain.app.model.KbContext.committedTurnCount] 分流（引擎不回读设置）；
 * - LIVE_AUTO 的历史块 / UC 块经**原生 PromptBuilder 的新可选参数**进入主请求
 *   （spy prompt builder 把收到的区块原样嵌进 user prompt，网关捕获即可对证）；
 * - 短域（≤24）Guard 执行且 UI reset 与修复文本一致；长域（>24）Guard 不执行；
 * - onlyThisRound / OFF / 轮次缺失 / null 引擎全走原路径；
 * - LIVE_AUTO 绝不调 launchShadow，正常请求模型调用恰一次；
 * - Guard 异常原文回退；取消信号原样传播。
 *
 * 全部确定性：fake gateway + spy prompt builder + 真实 ChampionEngine（本地事件库），
 * 零模型 API、不扩展为效果实验（§15.6 原文纪律）。
 */
class GenerationEngineLiveAutoWiringTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── 替身 ──

    /**
     * 记录型网关：捕获每次 (system,user) 请求与 parse 输入；应答文本可配置。
     * [FENCED_REPLY_JSON] 是契约破损形状（markdown 围栏）——短域 ContractGuard 的
     * strip_markdown_fence 规则可**确定性**修复，长域按判决不修。
     */
    private class RecordingGateway(private val replyText: String) : AiGateway {
        val streamCalls = mutableListOf<Pair<String, String>>()   // (system, user)
        val parseInputs = mutableListOf<String>()
        var hang = false                                          // 取消传播用：流永不应答

        override fun snapshotProviderConfig(): ProviderRequestConfig? = null
        override fun configForTicket(ticketId: String): ProviderRequestConfig? =
            ticketConfig.takeIf { it.ticketId == ticketId }

        override fun generateStream(
            systemPrompt: String, userPrompt: String, thinkingOverride: Int?,
            thinkingShapeIndex: Int, config: ProviderRequestConfig?
        ): Flow<StreamEvent> {
            streamCalls += systemPrompt to userPrompt
            if (hang) return flow { awaitCancellation() }
            return flowOf(
                StreamEvent.Chunk(replyText),
                StreamEvent.Complete(replyText, StreamUsage(promptTokens = 5, completionTokens = 5))
            )
        }

        override suspend fun generateRaw(systemPrompt: String, userPrompt: String) = replyText
        override suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String) =
            throw UnsupportedOperationException()
        override fun parseReplyResponse(content: String): LoveBrainResponse {
            parseInputs += content
            return LoveBrainResponse(response = ReplySchemes(recommended = "辛苦了，早点休息"))
        }

        companion object {
            val ticketConfig = ProviderRequestConfig(
                ticketId = "A", apiKey = "key", baseUrl = "https://p.example.com/v1/chat",
                model = "m", thinkingMode = 0, generateTimeoutMs = 5_000
            )

            /** 契约完好的主回复（守卫不改写） */
            const val CLEAN_REPLY_JSON =
                """{"response": {"recommended": "辛苦了，早点休息", "badBoy": "b", "playful": "p", "warm": "w"}, """ +
                    """"analysis": {"topic_status": "same", "topic_label": "日常"}}"""

            /** 带 markdown 围栏的破损契约（guard strip_markdown_fence 可确定性修复） */
            val FENCED_REPLY_JSON = "```json\n$CLEAN_REPLY_JSON\n```"
        }
    }

    private class FakeSettings(
        var mode: String = "OFF",
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
        // 冠军引擎设置（接线后收尾块不再读它——判据全部来自冻结输入）
        override var championEngineMode: String
            get() = mode
            set(value) { mode = value }
        override var championEngineScope: Set<String>
            get() = scope
            set(value) { scope = value }
    }

    private class FixedClock : Clock {
        override fun wallClock() = "2026-11-12 10:00"
        override fun today() = "2026-11-12"
        override fun epochMs() = 1_792_000_000_000L
    }

    /**
     * spy prompt builder：记录 buildReplyUserPromptWithRefs 的全部新参取值，
     * 并把收到的两个区块**原样嵌进**返回的 user prompt——网关捕获到的请求正文里
     * 出现区块文本 = "区块真的经原生 PromptBuilder 进了主请求"的直接证据。
     */
    private class PromptSpy {
        val withRefsCalls = mutableListOf<List<Any?>>()
        val builder: PromptBuilder = mockk<PromptBuilder>()

        init {
            every { builder.buildSystemPrompt() } returns "system"
            every { builder.replyPromptAssetHash() } returns "assets"
            coEvery {
                builder.buildReplyUserPromptWithRefs(
                    any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            } answers {
                withRefsCalls += listOf(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8])
                PromptBuilder.PromptBuildResult(
                    "user[hist=${args[6]}][uc=${args[7]}][turn=${args[8]}]",
                    emptyList()
                )
            }
            coEvery {
                builder.buildReplyUserPromptOnlyThisRound(any(), any())
            } returns PromptBuilder.PromptBuildResult("user-otr", emptyList())
        }

        /** WithRefs 的 (historicalBlock, uncertainBlock, currentTurn) 取值 */
        val liveArgs: List<Triple<String, String, Int?>>
            get() = withRefsCalls.map { Triple(it[6] as String, it[7] as String, it[8] as Int?) }
    }

    // ── 装配 ──

    private class Fixture(
        val engine: GenerationEngine,
        val champion: ChampionEngine,
        val store: ChampionEventStore,
        val root: File,
        val spy: PromptSpy
    )

    private fun realEngine(
        gateway: RecordingGateway,
        settingsMode: String = "OFF"
    ): Fixture {
        val spy = PromptSpy()
        val settings = FakeSettings(mode = settingsMode, scope = emptySet())
        val root = File(tmp.root, "champion")
        val store = ChampionEventStore(FileChampionEventStorage(File(root, "events")))
        val champion = ChampionEngine(
            gateway = gateway,
            eventStore = store,
            assembler = ChampionPromptAssembler(timestampBlock = { "" }, promptSource = { "" }),
            guard = ContractGuard(),
            settings = settings,
            clock = FixedClock(),
            shadowExecutor = { body -> runBlocking { body() } },   // 测试内联执行
            rootDir = root
        )
        return Fixture(GenerationEngine(gateway, spy.builder, champion), champion, store, root, spy)
    }

    /** 往真实事件库提交一轮已确认历史（生产提交点的同款入参形状） */
    private suspend fun ChampionEngine.seedCommittedTurn(
        turn: Int,
        vararg messages: ChampionMessage
    ) {
        commitConfirmedTurn("kb", "kb", messages.toList(), modeSnapshot = "LIVE_AUTO", turn = turn)
    }

    private fun input(
        championMode: String,
        committedTurnCount: Int?,
        onlyThisRound: Boolean = false
    ) = buildGenerationInput(
        requestId = "req-live-wiring",
        messages = listOf(
            ChatMessage(id = "cur1", role = ChatMessage.Role.HER, content = "那周五的事呢"),
            ChatMessage(id = "cur2", role = ChatMessage.Role.ME, content = "我看看安排")
        ),
        userHint = "",
        knowledgeBase = KnowledgeBase(name = "kb", stage = "暧昧期"),
        intentConfig = IntentConfig(),
        corrections = emptyMap(),
        correctionsRevision = 0,
        onlyThisRound = onlyThisRound,
        aggressive = false,
        providerIdentity = RecordingGateway.ticketConfig.toIdentity(),
        kbProfile = "",
        kbRevision = "rev",
        promptAssetHash = "assets",
        championMode = championMode,
        committedTurnCount = committedTurnCount
    )

    private fun completed(events: List<ReplyEvent>): ReplyCompleted =
        events.filterIsInstance<ReplyCompleted>().single()

    // ── §15.6 判据 ──

    @Test
    fun `OFF routes to the native prompt path without touching champion context`() = runTest {
        val gateway = RecordingGateway(RecordingGateway.CLEAN_REPLY_JSON)
        // mockk 引擎：任何意外调用（装配/守卫/影子）都会让 verify 爆红
        val mockChampion = mockk<ChampionEngine>()
        val spy = PromptSpy()
        val engine = GenerationEngine(gateway, spy.builder, mockChampion)

        val events = engine.replyStream(input("OFF", committedTurnCount = 23)).toList()

        assertTrue(completed(events).result is GenerateResult.Success)
        assertEquals("只发主回复一次请求", 1, gateway.streamCalls.size)
        assertEquals(
            "OFF 不装配任何区块：user prompt 里 hist/uc 为空、selector 拿冻结轮次原值",
            "user[hist=][uc=][turn=23]",
            gateway.streamCalls.single().second
        )
        coVerify(exactly = 0) { mockChampion.prepareLiveContext(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mockChampion.repairAndAudit(any(), any(), any()) }
        verify(exactly = 0) {
            mockChampion.launchShadow(
                any<String>(), any<String>(), any<String>(), any(),
                any<IntentConfig>(), any<String>(), any<String>(), any<Long>(),
                any<ProviderRequestConfig>(), any<ChampionPromptAssembler.ChampionSeed>()
            )
        }
        assertEquals("spy prompt builder 收到了正常的 WithRefs 调用", 1, spy.withRefsCalls.size)
        assertFalse(File(tmp.root, "champion/shadow_log.jsonl").exists())
    }

    @Test
    fun `SHADOW keeps its legacy shadow behaviour driven by the frozen snapshot`() = runTest {
        // 判据来源证明：settings 是 OFF、引擎收尾块不再回读设置——影子请求仍按**冻结快照**跑
        val gateway = RecordingGateway(RecordingGateway.CLEAN_REPLY_JSON)
        val fx = realEngine(gateway, settingsMode = "OFF")

        val events = fx.engine.replyStream(input("SHADOW", committedTurnCount = 23)).toList()

        assertTrue(completed(events).result is GenerateResult.Success)
        assertEquals("主回复 + 影子 = 两次请求", 2, gateway.streamCalls.size)
        assertEquals(
            "SHADOW 不装配 LIVE 区块（不调 prepareLiveContext）",
            "user[hist=][uc=][turn=23]",
            gateway.streamCalls[0].second
        )
        assertTrue("第二次请求是影子请求（冠军装配 prompt）", gateway.streamCalls[1].second.isNotEmpty())
        assertTrue("影子日志落盘", File(fx.root, "shadow_log.jsonl").isFile)
        assertEquals(
            "影子路径不写事件库（R2-1 复钉）",
            0, runBlocking { fx.store.events("kb__kb").size }
        )
        // 主链路不带旧档位守卫（SHADOW 不触发 repairAndAudit→无 reset）
        assertTrue("SHADOW 主链路不发 UI reset", events.none { it is ReplyChunkReset })
    }

    @Test
    fun `LIVE_AUTO at turn 24 puts the history block into the native prompt with no uc and runs the guard`() = runTest {
        val gateway = RecordingGateway(RecordingGateway.FENCED_REPLY_JSON)
        val fx = realEngine(gateway)
        runBlocking {
            fx.champion.seedCommittedTurn(
                turn = 23,
                ChampionMessage("HER", "听说快递周五到，还没收到呢", "2026-11-12 09:59", "h1"),
                ChampionMessage("ME", "好", "2026-11-12 10:00", "h2")
            )
        }

        val events = fx.engine.replyStream(input("LIVE_AUTO", committedTurnCount = 23)).toList()

        // ① 主请求恰好一次（LIVE_AUTO 不追加影子请求/额外模型调用，§9.2）
        assertEquals("LIVE_AUTO 只发主回复一次请求", 1, gateway.streamCalls.size)
        val user = gateway.streamCalls.single().second
        // ② 历史块经**原生 PromptBuilder** 进入主请求（spy 把收到的区块原样嵌入）
        assertTrue("历史段头进入主请求", user.contains("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】"))
        assertTrue("已提交历史行进入主请求", user.contains("m_src[0] | 2026-11-12 09:59 | PARTNER | 听说快递周五到，还没收到呢"))
        assertTrue("ME 历史行映射为 USER", user.contains("m_src[1] | 2026-11-12 10:00 | USER | 好"))
        // ③ 当前轮临时消息不在历史块里（未确认输入不提前入库/不混入历史）
        assertFalse("当前轮消息不进历史块", user.contains("那周五的事呢"))
        // ④ 短域 UC 恒空（禁变相 uc_guard）
        assertFalse("短域不注入 UC", user.contains("# 【未定/待确认事项"))
        assertTrue("selector 拿 committedTurnCount 原值（23，不加 1）", user.endsWith("[turn=23]"))
        // ⑤ Guard 执行：围栏被 strip_markdown_fence 确定性修复（守卫按 Python 形状重序列化，
        //    修复文本 ≠ 原文；判据 = 围栏消失 + 契约键在场 + 与原文不同）
        val parsed = gateway.parseInputs.single()
        assertTrue("parser 收到的是修复后文本（围栏已剥）", !parsed.contains("```") && parsed != RecordingGateway.FENCED_REPLY_JSON)
        assertTrue("修复文本仍是合法契约形状", parsed.contains("\"recommended\"") && parsed.contains("\"analysis\""))
        // ⑥ §8.3 UI 一致性：先 reset 再把修复全文经同一 reducer 重放——
        //    重放的 ReplyChunk 与 parser 输入**同一份文本**（UI 最终显示 = 最终解析结果）
        val firstResetIdx = events.indexOfFirst { it is ReplyChunkReset }
        assertTrue("Guard 改文必须发 ReplyChunkReset", firstResetIdx >= 0)
        val schemesResetIdx = events.indexOfFirst { it is ReplySchemesReset }
        assertTrue("Guard 改文必须发 ReplySchemesReset", schemesResetIdx > firstResetIdx)
        val repairedChunkIdx = events.mapIndexedNotNull { i, e -> if (e is ReplyChunk && e.text == parsed) i else null }.last()
        assertTrue("修复全文在 reset 之后重放一次 ReplyChunk", repairedChunkIdx > schemesResetIdx)
        val lastSchemesIdx = events.mapIndexedNotNull { i, e -> if (e is ReplySchemesArrived) i else null }.last()
        assertTrue("重放后必须重发 ReplySchemesArrived", lastSchemesIdx > repairedChunkIdx)
        assertTrue("最终 ReplyCompleted 携带修复文本的解析结果", completed(events).result is GenerateResult.Success)
        // ⑦ 影子路径零痕迹
        assertFalse("LIVE_AUTO 不写 shadow_log", File(fx.root, "shadow_log.jsonl").exists())
    }

    @Test
    fun `LIVE_AUTO at turn 25 puts history and uc into the native prompt and skips the guard`() = runTest {
        val gateway = RecordingGateway(RecordingGateway.FENCED_REPLY_JSON)
        val fx = realEngine(gateway)
        runBlocking {
            fx.champion.seedCommittedTurn(
                turn = 24,
                ChampionMessage("HER", "听说快递周五到，还没收到呢", "2026-11-12 09:59", "h1")
            )
        }

        val events = fx.engine.replyStream(input("LIVE_AUTO", committedTurnCount = 24)).toList()

        assertEquals("只发主回复一次请求", 1, gateway.streamCalls.size)
        val user = gateway.streamCalls.single().second
        assertTrue("历史块进入主请求", user.contains("m_src[0] | 2026-11-12 09:59 | PARTNER | 听说快递周五到，还没收到呢"))
        // UC 按规则生成：历史未定项「听说快递周五到」与本轮锚点（周五）同场 → 注入
        assertTrue("UC 段头进入主请求", user.contains("# 【未定/待确认事项：尚未核实，不得当作已发生】"))
        assertTrue("UC 注入逐字分句", user.contains("「听说快递周五到」（T24）"))
        // 长域禁 Guard（防变相 uc_guard）：破损契约原样进 parser、无 UI reset
        assertEquals("长域 parser 收到原文（守卫未执行）", RecordingGateway.FENCED_REPLY_JSON, gateway.parseInputs.single())
        assertTrue("长域不发 UI reset", events.none { it is ReplyChunkReset || it is ReplySchemesReset })
        assertTrue(completed(events).result is GenerateResult.Success)
        assertFalse("LIVE_AUTO 不写 shadow_log", File(fx.root, "shadow_log.jsonl").exists())
        assertEquals("事件库仍只有已提交那一条", 1, runBlocking { fx.store.events("kb__kb").size })
    }

    @Test
    fun `LIVE_AUTO with onlyThisRound skips champion context guard and shadow entirely`() = runTest {
        val gateway = RecordingGateway(RecordingGateway.FENCED_REPLY_JSON)
        val settings = FakeSettings(mode = "OFF")
        val root = File(tmp.root, "champion")
        val store = ChampionEventStore(FileChampionEventStorage(File(root, "events")))
        val spy = PromptSpy()
        val champion = ChampionEngine(
            gateway = gateway,
            eventStore = store,
            assembler = ChampionPromptAssembler(timestampBlock = { "" }, promptSource = { "" }),
            guard = ContractGuard(),
            settings = settings,
            clock = FixedClock(),
            shadowExecutor = { body -> runBlocking { body() } },
            rootDir = root
        )
        runBlocking {
            champion.seedCommittedTurn(
                turn = 23,
                ChampionMessage("HER", "听说快递周五到，还没收到呢", "2026-11-12 09:59", "h1")
            )
        }
        val engine = GenerationEngine(gateway, spy.builder, champion)

        val events = engine.replyStream(
            input("LIVE_AUTO", committedTurnCount = 23, onlyThisRound = true)
        ).toList()

        // §16 条 14 / §9.3：onlyThisRound 第一判据——不读历史、不注入、不守卫、不影子
        assertEquals("只发主回复一次请求", 1, gateway.streamCalls.size)
        assertEquals("仍走 buildReplyUserPromptOnlyThisRound", "user-otr", gateway.streamCalls.single().second)
        assertEquals("WithRefs（原生带记忆装配）整条未被调用", 0, spy.withRefsCalls.size)
        assertEquals("parser 收到原文（Guard 未执行）", RecordingGateway.FENCED_REPLY_JSON, gateway.parseInputs.single())
        assertTrue("无 UI reset", events.none { it is ReplyChunkReset || it is ReplySchemesReset })
        assertFalse("不写 shadow_log", File(root, "shadow_log.jsonl").exists())
        assertEquals(
            "已提交历史不受影响（生成路径零持久痕迹）",
            1, runBlocking { store.events("kb__kb").size }
        )
        assertTrue(completed(events).result is GenerateResult.Success)
    }

    @Test
    fun `LIVE_AUTO with a null committed turn count falls back to the native path`() = runTest {
        // §5.4：轮次缺失不猜（不默认第 1 轮也不默认第 25 轮）→ 整链回退原生，正常生成继续
        val gateway = RecordingGateway(RecordingGateway.FENCED_REPLY_JSON)
        val fx = realEngine(gateway)
        runBlocking {
            fx.champion.seedCommittedTurn(
                turn = 23,
                ChampionMessage("HER", "听说快递周五到，还没收到呢", "2026-11-12 09:59", "h1")
            )
        }

        val events = fx.engine.replyStream(input("LIVE_AUTO", committedTurnCount = null)).toList()

        assertEquals("只发主回复一次请求", 1, gateway.streamCalls.size)
        assertEquals(
            "历史块/UC 块均为空、selector 轮次为 null（自读口径）",
            "user[hist=][uc=][turn=null]",
            gateway.streamCalls.single().second
        )
        assertEquals("parser 收到原文（Guard 未执行）", RecordingGateway.FENCED_REPLY_JSON, gateway.parseInputs.single())
        assertTrue("无 UI reset", events.none { it is ReplyChunkReset || it is ReplySchemesReset })
        assertFalse("不写 shadow_log", File(fx.root, "shadow_log.jsonl").exists())
        assertTrue(completed(events).result is GenerateResult.Success)
    }

    @Test
    fun `a guard exception keeps the original text and never resets the ui`() = runTest {
        // R2：守卫自身异常 → 保留原文进 parser，绝不拖垮正常回复（§8.1 原文回退）
        val gateway = RecordingGateway(RecordingGateway.FENCED_REPLY_JSON)
        val spy = PromptSpy()
        val mockChampion = mockk<ChampionEngine>()
        coEvery {
            mockChampion.prepareLiveContext(any(), any(), any(), any(), any())
        } returns ChampionLiveContext(
            currentTurn = 24,
            strategy = ChampionTurnStrategy.GUARD_SHORT,
            historyBlock = "",
            uncertainBlock = "",
            historyCu = 0,
            uncertainCu = 0
        )
        every { mockChampion.repairAndAudit(any(), any(), any()) } throws RuntimeException("guard boom")
        val engine = GenerationEngine(gateway, spy.builder, mockChampion)

        val events = engine.replyStream(input("LIVE_AUTO", committedTurnCount = 23)).toList()

        assertTrue("守卫异常不崩主链路", completed(events).result is GenerateResult.Success)
        assertEquals("parser 收到原始文本（修复失败回退原文）", RecordingGateway.FENCED_REPLY_JSON, gateway.parseInputs.single())
        assertTrue("守卫异常不发 UI reset", events.none { it is ReplyChunkReset || it is ReplySchemesReset })
        assertEquals("只发主回复一次请求", 1, gateway.streamCalls.size)
    }

    @Test
    fun `cancellation propagates out of the reply stream`() {
        // §16 条 8：取消信号原样传播（不被普通异常降级逻辑吞掉、不降级成 ReplyCompleted error）
        val gateway = RecordingGateway(RecordingGateway.CLEAN_REPLY_JSON).apply { hang = true }
        val fx = realEngine(gateway)
        runBlocking {
            fx.champion.seedCommittedTurn(
                turn = 23,
                ChampionMessage("HER", "听说快递周五到，还没收到呢", "2026-11-12 09:59", "h1")
            )
        }
        val events = mutableListOf<ReplyEvent>()
        val cancelled = CompletableDeferred<Boolean>()
        val deadline = System.currentTimeMillis() + 10_000
        runBlocking {
            val job = launch {
                try {
                    fx.engine.replyStream(input("LIVE_AUTO", committedTurnCount = 23)).collect { events += it }
                    cancelled.complete(false)
                } catch (e: CancellationException) {
                    cancelled.complete(true)   // 取消原样上抛到这里 = 传播未被吞
                }
            }
            while (events.none { it is ReplyStarted } && System.currentTimeMillis() < deadline) yield()   // 等流真正打开
            job.cancel()
            job.join()
        }
        runBlocking { assertTrue("取消必须以 CancellationException 形态传出 replyStream", cancelled.await()) }
        assertTrue("ReplyStarted 已发出（流确实进入了请求阶段）", events.any { it is ReplyStarted })
        assertTrue("取消不得被降级成失败完成事件", events.none { it is ReplyCompleted })
    }
}
