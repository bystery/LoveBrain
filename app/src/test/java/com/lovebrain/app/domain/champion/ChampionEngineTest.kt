package com.lovebrain.app.domain.champion

import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.StreamUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 冠军引擎 facade：模式默认 OFF / scope 白名单 / shadow 全链路（装配→生成→守卫→
 * shadow_log + PORT_IDENTITY）与失败降级。纯 JVM，gateway 用 fake 流。
 */
class ChampionEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── 测试替身 ──

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
        // 冠军引擎两颗（被测目标）
        override var championEngineMode: String
            get() = mode
            set(value) { mode = value }
        override var championEngineScope: Set<String>
            get() = scope
            set(value) { scope = value }
    }

    private class FixedClock : Clock {
        override fun wallClock() = "2026-11-06 10:00"
        override fun today() = "2026-11-06"
        override fun epochMs() = 1_790_000_000_000L
    }

    private class FakeGateway(private val text: String) : AiGateway {
        override fun snapshotProviderConfig(): ProviderRequestConfig? = null
        override fun configForTicket(ticketId: String): ProviderRequestConfig? = null
        override fun generateStream(
            systemPrompt: String, userPrompt: String, thinkingOverride: Int?,
            thinkingShapeIndex: Int, config: ProviderRequestConfig?
        ): Flow<StreamEvent> = flowOf(
            StreamEvent.Chunk(text),
            StreamEvent.Complete(text, StreamUsage(promptTokens = 11, completionTokens = 7))
        )
        override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String = text
        override suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String): RawGenerationResult =
            throw UnsupportedOperationException()
        override fun parseReplyResponse(content: String): LoveBrainResponse =
            throw UnsupportedOperationException()
    }

    private fun newEngine(
        settings: FakeSettings,
        gateway: AiGateway = FakeGateway("{\"response\": {\"recommended\": \"好的\"}}")
    ): Pair<ChampionEngine, File> {
        val dir = tmp.newFolder()
        val engine = ChampionEngine(
            gateway = gateway,
            eventStore = ChampionEventStore(FileChampionEventStorage(File(dir, "events"))),
            assembler = ChampionPromptAssembler(
                timestampBlock = { "【当前时间】2026-11-06 10:00" },
                promptSource = { "" }
            ),
            guard = ContractGuard(),
            settings = settings,
            clock = FixedClock(),
            shadowExecutor = { body -> runBlocking { body() } },   // 测试内联执行
            rootDir = dir
        )
        return engine to dir
    }

    // ── 模式与白名单 ──

    @Test
    fun `mode defaults to OFF for dirty and unwritten values`() {
        assertEquals(ChampionMode.OFF, ChampionMode.fromRaw(null))
        assertEquals(ChampionMode.OFF, ChampionMode.fromRaw("nonsense"))
        assertEquals(ChampionMode.SHADOW, ChampionMode.fromRaw("SHADOW"))
        assertEquals(ChampionMode.FULL, ChampionMode.fromRaw("FULL"))
    }

    @Test
    fun `effective mode is OFF unless the contactId-story scope hits`() {
        val settings = FakeSettings(mode = "SHADOW", scope = setOf("c1|s1"))
        val (engine, _) = newEngine(settings)
        assertEquals(ChampionMode.SHADOW, engine.effectiveMode("c1", "s1"))
        assertEquals(ChampionMode.OFF, engine.effectiveMode("c1", "s2"))
        assertEquals(ChampionMode.OFF, engine.effectiveMode("c2", "s1"))
        settings.scope = emptySet()      // 空集 = 全体
        assertEquals(ChampionMode.SHADOW, engine.effectiveMode("anything", "any"))
        settings.scope = setOf("*")
        assertEquals(ChampionMode.SHADOW, engine.effectiveMode("x", "y"))
    }

    // ── shadow 全链路 ──

    @Test
    fun `shadowTurn appends real messages assembles generates guards and logs both sides`() = runTest {
        val settings = FakeSettings(mode = "SHADOW")
        val (engine, dir) = newEngine(settings)
        val out = engine.shadowTurn(
            requestId = "req-1", contactId = "c1", story = "s1", turn = 1,
            messages = listOf(
                Triple("HER", "周五见？", "2026-11-06 09:59"),
                Triple("ME", "好", "2026-11-06 10:00")
            ),
            intent = IntentConfig(), advisorNote = "", originalReply = "原链路草稿",
            timeoutMs = 5_000, providerConfig = null
        )
        assertTrue(out.ok)
        val log = out.logLine
        assertEquals("req-1", log["requestId"]!!.jsonPrimitive.content)
        assertEquals(1, log["turn"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, log["history_cu"]!!.jsonPrimitive.content.toInt())   // 首轮无旧史
        assertEquals("原链路草稿", log["original_reply"]!!.jsonPrimitive.content)
        assertTrue(log["shadow_reply"]!!.jsonPrimitive.content.contains("好的"))
        // fake 回复缺 3 个风格槽与 directions → 守卫补齐重写（changed=true），审计随行
        assertTrue(log["guard_changed"]!!.jsonPrimitive.content == "true")
        assertTrue(log["shadow_reply"]!!.jsonPrimitive.content.contains("\"directions\": [null, null, null, null]"))
        assertEquals(11, log["prompt_tokens"]!!.jsonPrimitive.content.toInt())
        // 真实消息入库；shadow 回复不入库（reply never committed）
        val store = ChampionEventStore(FileChampionEventStorage(File(dir, "events")))
        val all = runBlocking { store.events("c1__s1") }
        assertEquals(2, all.size)
        assertTrue(all.none { it.text.contains("好的") })
        // shadow_log 落盘（jsonl，两侧输出与装配差异）
        engine.appendShadowLog(log)
        val logFile = File(dir, "shadow_log.jsonl")
        assertTrue(logFile.isFile)
        val line = logFile.readText().trim()
        // shadow_log 走 kotlinx 序列化（紧凑格式，无空格）；guard 审计走 Python dumps 形
        assertTrue(line.contains("\"requestId\":\"req-1\""))
        assertTrue(line.contains("\"original_reply\":\"原链路草稿\""))
        assertTrue(line.contains("\"shadow_reply\""))
        assertTrue(line.contains("\"guard_meta\""))
    }

    @Test
    fun `shadowTurn dedups replayed messages across turns`() = runTest {
        val settings = FakeSettings(mode = "SHADOW")
        val (engine, dir) = newEngine(settings)
        val msgs = listOf(Triple("HER", "同一条", "2026-11-06 09:00"))
        engine.shadowTurn("r1", "c", "s", turn = -1, messages = msgs, intent = IntentConfig(),
            advisorNote = "", originalReply = "", timeoutMs = 5_000, providerConfig = null)
        val out2 = engine.shadowTurn("r2", "c", "s", turn = -1, messages = msgs, intent = IntentConfig(),
            advisorNote = "", originalReply = "", timeoutMs = 5_000, providerConfig = null)
        assertEquals(2, out2.logLine["turn"]!!.jsonPrimitive.content.toInt())
        val store = ChampionEventStore(FileChampionEventStorage(File(dir, "events")))
        assertEquals(1, runBlocking { store.events("c__s") }.size)
    }

    @Test
    fun `shadow failure degrades into a log line and never throws`() = runTest {
        val settings = FakeSettings(mode = "SHADOW")
        val (engine, _) = newEngine(
            settings,
            gateway = object : AiGateway {
                override fun snapshotProviderConfig() = null
                override fun configForTicket(ticketId: String) = null
                override fun generateStream(
                    systemPrompt: String, userPrompt: String, thinkingOverride: Int?,
                    thinkingShapeIndex: Int, config: ProviderRequestConfig?
                ): Flow<StreamEvent> = flowOf(
                    StreamEvent.Error(ProviderFailure(ReplyFailureKind.Timeout), "")
                )
                override suspend fun generateRaw(systemPrompt: String, userPrompt: String) = ""
                override suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String) =
                    throw UnsupportedOperationException()
                override fun parseReplyResponse(content: String): LoveBrainResponse =
                    throw UnsupportedOperationException()
            }
        )
        val out = engine.shadowTurn(
            "r9", "c", "s", turn = 1, messages = listOf(Triple("HER", "hi", "t")),
            intent = IntentConfig(), advisorNote = "", originalReply = "orig",
            timeoutMs = 5_000, providerConfig = null
        )
        assertFalse("生成失败只记日志，绝不抛", out.ok)
        assertTrue(out.error != null || (out.logLine["generate_error"]?.jsonPrimitive?.content ?: "").isNotEmpty())
    }

    // ── PORT_IDENTITY（custody 三 hash）──

    @Test
    fun `PORT_IDENTITY carries the three custody hashes`() {
        val (_, dir) = newEngine(FakeSettings())
        val assembler = ChampionPromptAssembler(timestampBlock = { "" }, promptSource = { "[$it]" })
        ChampionIdentity.writeIdentityFile(dir, assembler)
        val file = File(dir, "PORT_IDENTITY.json")
        assertTrue(file.isFile)
        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        assertTrue(obj.containsKey("champion_asset_hash"))
        assertEquals("window_expand_b_norecap_uc_guard", obj["arena_candidate"]!!.jsonPrimitive.content)
        val arena = obj["arena_mechanism_sha256"]!!.jsonObject
        assertEquals(5, arena.size)
        assertTrue(arena["mechanism/contract_guard.py"]!!.jsonPrimitive.content.startsWith("c26c68c0"))
    }

    @Test
    fun `identity rides on every shadow log line`() = runTest {
        val settings = FakeSettings(mode = "SHADOW")
        val (engine, _) = newEngine(settings)
        val out = engine.shadowTurn("r", "c", "s", turn = 1,
            messages = listOf(Triple("HER", "x", "t")),
            intent = IntentConfig(), advisorNote = "", originalReply = "",
            timeoutMs = 5_000, providerConfig = null)
        val identity = out.logLine["identity"]!!.jsonObject
        assertTrue(identity["champion_asset_hash"]!!.jsonPrimitive.content.isNotEmpty())
        assertEquals(ChampionIdentity.PORT_VERSION, identity["port_version"]!!.jsonPrimitive.content)
    }
}
