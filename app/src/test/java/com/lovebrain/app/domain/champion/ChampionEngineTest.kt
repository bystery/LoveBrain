package com.lovebrain.app.domain.champion

import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.DialogueSpeaker
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.StreamUsage
import com.lovebrain.app.model.buildGenerationInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** LIVE_AUTO 确定性装配的零模型调用证伪：任何模型入口被触碰即失败。 */
    private class NoModelGateway : AiGateway {
        override fun snapshotProviderConfig(): ProviderRequestConfig? = null
        override fun configForTicket(ticketId: String): ProviderRequestConfig? = null
        override fun generateStream(
            systemPrompt: String, userPrompt: String, thinkingOverride: Int?,
            thinkingShapeIndex: Int, config: ProviderRequestConfig?
        ): Flow<StreamEvent> = throw AssertionError("prepareLiveContext 不允许调用模型")
        override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String =
            throw AssertionError("prepareLiveContext 不允许调用模型")
        override suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String): RawGenerationResult =
            throw AssertionError("prepareLiveContext 不允许调用模型")
        override fun parseReplyResponse(content: String): LoveBrainResponse =
            throw AssertionError("prepareLiveContext 不允许调用 parser")
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
    fun `S2 S3 tiers are not wired and clamp to OFF with a log`() {
        // 审计 R2-3：枚举保留档位名，但 SINGLE_STYLE/FULL 无实现——引擎读到按 OFF 处理
        val single = FakeSettings(mode = "SINGLE_STYLE")
        assertEquals(ChampionMode.OFF, newEngine(single).first.mode())
        assertEquals(ChampionMode.OFF, newEngine(single).first.effectiveMode("c", "s"))
        val full = FakeSettings(mode = "FULL")
        assertEquals(ChampionMode.OFF, newEngine(full).first.mode())
        assertEquals(ChampionMode.OFF, newEngine(full).first.effectiveMode("c", "s"))
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

    // ── 提交点模式门禁（审计 S1-P0-1：OFF 零副作用）──

    @Test
    fun `commitConfirmedTurn is a zero-IO no-op when the effective mode is OFF`() = runTest {
        // 用户没开 Champion（默认 OFF）：提交点必须一个字节都不写——
        // 不读事件库、不分配 uid、连 champion/events 目录都不创建
        val offEngineAndDir = newEngine(FakeSettings(mode = "OFF"))
        val committed = offEngineAndDir.first.commitConfirmedTurn(
            "c", "s", listOf(ChampionMessage("HER", "周五见？", "2026-11-06 09:59", "m1"))
        )
        assertTrue("OFF 提交返回空", committed.isEmpty())
        assertFalse("OFF 时不创建事件库目录", File(offEngineAndDir.second, "events").exists())
        // 脏值同样落 OFF（fail-closed）
        val dirty = newEngine(FakeSettings(mode = "nonsense"))
        assertTrue(
            dirty.first.commitConfirmedTurn("c", "s", listOf(ChampionMessage("HER", "x", "t", "m1"))).isEmpty()
        )
        assertFalse(File(dirty.second, "events").exists())
        // S2/S3 未接线被 mode() 收敛为 OFF——同样零 IO
        val unwired = newEngine(FakeSettings(mode = "SINGLE_STYLE"))
        assertTrue(
            unwired.first.commitConfirmedTurn("c", "s", listOf(ChampionMessage("ME", "x", "t", "m1"))).isEmpty()
        )
        assertFalse(File(unwired.second, "events").exists())
    }

    @Test
    fun `commitConfirmedTurn respects the scope whitelist`() = runTest {
        val settings = FakeSettings(mode = "SHADOW", scope = setOf("c1|s1"))
        val (engine, dir) = newEngine(settings)
        // scope 未命中 = 有效模式 OFF = 零 IO
        val missed = engine.commitConfirmedTurn(
            "c2", "s2", listOf(ChampionMessage("HER", "范围外", "t", "m1"))
        )
        assertTrue("scope 未命中提交返回空", missed.isEmpty())
        assertFalse("scope 未命中不创建事件库目录", File(dir, "events").exists())
        // scope 命中才真正入库
        val hit = engine.commitConfirmedTurn(
            "c1", "s1", listOf(ChampionMessage("HER", "范围内", "t", "m1"))
        )
        assertEquals(1, hit.size)
        assertEquals("范围内", hit.single().text)
    }

    @Test
    fun `commitConfirmedTurn honours the frozen mode snapshot without rereading settings`() = runTest {
        // guide-6 Step 2：生产链路传冻结快照（ReplyGenerationContext.championMode）——
        // 快照非 OFF：即使当前设置已改回 OFF，本轮提交按冻结快照放行（scope 命中）
        val (offNow, _) = newEngine(FakeSettings(mode = "OFF"))
        val committed = offNow.commitConfirmedTurn(
            "c", "s", listOf(ChampionMessage("HER", "按快照入库", "t", "m1")),
            modeSnapshot = "LIVE_AUTO"
        )
        assertEquals("冻结快照 LIVE_AUTO 放行（不回读设置）", 1, committed.size)
        // 快照 OFF：即使当前设置是 SHADOW，显式 OFF 快照仍零 IO（尊重显式关闭，§16 条 13）
        val (shadowNow, shadowDir) = newEngine(FakeSettings(mode = "SHADOW"))
        assertTrue(
            shadowNow.commitConfirmedTurn(
                "c", "s", listOf(ChampionMessage("HER", "x", "t", "m1")), modeSnapshot = "OFF"
            ).isEmpty()
        )
        assertFalse("快照 OFF 不创建事件库目录", File(shadowDir, "events").exists())
        // 快照脏值 fail-closed：零 IO
        assertTrue(
            offNow.commitConfirmedTurn(
                "c", "s", listOf(ChampionMessage("ME", "y", "t", "m2")), modeSnapshot = "nonsense"
            ).isEmpty()
        )
        // 快照走 scopeAllows：未命中零 IO，命中入库
        val (scoped, _) = newEngine(FakeSettings(mode = "OFF", scope = setOf("c1|s1")))
        assertTrue(
            scoped.commitConfirmedTurn(
                "c2", "s2", listOf(ChampionMessage("HER", "z", "t", "m3")), modeSnapshot = "LIVE_AUTO"
            ).isEmpty()
        )
        assertEquals(
            1, scoped.commitConfirmedTurn(
                "c1", "s1", listOf(ChampionMessage("HER", "z", "t", "m3")), modeSnapshot = "LIVE_AUTO"
            ).size
        )
    }

    // ── LIVE_AUTO 分域与确定性装配（guide-6 §17 Step 2 / §15.1）──

    @Test
    fun `turn domain table 1 23 24 guard short and 25 40 uc long`() {
        // 边界 24 单点定义在 SHORT_DOMAIN_MAX_TURN；轮次 1-24 = GUARD_SHORT，25+ = UC_LONG
        assertEquals(24, ChampionTurnStrategy.SHORT_DOMAIN_MAX_TURN)
        assertEquals(ChampionTurnStrategy.GUARD_SHORT, ChampionTurnStrategy.forTurn(1))
        assertEquals(ChampionTurnStrategy.GUARD_SHORT, ChampionTurnStrategy.forTurn(23))
        assertEquals(ChampionTurnStrategy.GUARD_SHORT, ChampionTurnStrategy.forTurn(24))
        assertEquals(ChampionTurnStrategy.UC_LONG, ChampionTurnStrategy.forTurn(25))
        assertEquals(ChampionTurnStrategy.UC_LONG, ChampionTurnStrategy.forTurn(40))
    }

    @Test
    fun `LIVE_AUTO is a wired mode but never flips the legacy guard predicate`() {
        assertEquals(ChampionMode.LIVE_AUTO, ChampionMode.fromRaw("LIVE_AUTO"))
        val live = newEngine(FakeSettings(mode = "LIVE_AUTO")).first
        assertEquals(ChampionMode.LIVE_AUTO, live.mode())
        assertEquals(ChampionMode.LIVE_AUTO, live.effectiveMode("c", "s"))
        // isMainPathGuarded 只认 S2/S3——LIVE_AUTO 的守卫判据走 strategy，旧档位语义不被偷换
        assertFalse(ChampionMode.isMainPathGuarded(ChampionMode.LIVE_AUTO))
        assertTrue(ChampionMode.isMainPathGuarded(ChampionMode.SINGLE_STYLE))
        assertTrue(ChampionMode.isMainPathGuarded(ChampionMode.FULL))
    }

    @Test
    fun `prepareLiveContext returns null when onlyThisRound`() = runTest {
        // §16 条 14：onlyThisRound 禁读任何 Champion 历史——方法内防御返回 null
        val (engine, _) = newEngine(FakeSettings(mode = "LIVE_AUTO"), gateway = NoModelGateway())
        val ctx = engine.prepareLiveContext(
            "c", "s", currentTurn = 1,
            dialogue = listOf(DialogueMessage(id = "m1", speaker = DialogueSpeaker.PARTNER, text = "hi")),
            onlyThisRound = true
        )
        assertNull("onlyThisRound 防御返回 null", ctx)
    }

    @Test
    fun `prepareLiveContext first turn has empty history guard short and zero IO`() = runTest {
        val (engine, dir) = newEngine(FakeSettings(mode = "LIVE_AUTO"), gateway = NoModelGateway())
        val ctx = engine.prepareLiveContext(
            "c", "s", currentTurn = 1,
            dialogue = listOf(DialogueMessage(id = "m1", speaker = DialogueSpeaker.PARTNER, text = "周五见？")),
            onlyThisRound = false
        )!!
        assertEquals(ChampionTurnStrategy.GUARD_SHORT, ctx.strategy)
        assertEquals("无内容不出空标题（§7.3）", "", ctx.historyBlock)
        assertEquals(0, ctx.historyCu)
        assertEquals("GUARD_SHORT 的 UC 恒空串", "", ctx.uncertainBlock)
        assertEquals(0, ctx.uncertainCu)
        assertEquals(1, ctx.currentTurn)
        // 装配零写入：不 append/不落盘，连事件库目录都不创建
        assertFalse("装配不创建事件库目录", File(dir, "events").exists())
    }

    @Test
    fun `prepareLiveContext assembles committed history and excludes current msgUids without appending`() = runTest {
        val (engine, dir) = newEngine(FakeSettings(mode = "LIVE_AUTO"), gateway = NoModelGateway())
        runBlocking {
            engine.commitConfirmedTurn(
                "c", "s",
                listOf(
                    ChampionMessage("HER", "周五见？", "2026-11-06 09:59", "m1"),
                    ChampionMessage("ME", "好", "2026-11-06 10:00", "m2")
                ),
                modeSnapshot = "LIVE_AUTO", turn = 1
            )
        }
        // 正常轮：历史块 = 段头 + 两条 m_src 行（HER→PARTNER / ME→USER），当前轮消息不在其中
        val ctx = engine.prepareLiveContext(
            "c", "s", currentTurn = 2,
            dialogue = listOf(DialogueMessage(id = "m3", speaker = DialogueSpeaker.PARTNER, text = "路上吗？")),
            onlyThisRound = false
        )!!
        assertTrue(ctx.historyBlock.startsWith("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】"))
        assertTrue(ctx.historyBlock.contains("m_src[0] | 2026-11-06 09:59 | PARTNER | 周五见？"))
        assertTrue(ctx.historyBlock.contains("m_src[1] | 2026-11-06 10:00 | USER | 好"))
        assertFalse(ctx.historyBlock.contains("路上吗？"))
        assertTrue(ctx.historyCu > 0)
        // 重复生成边缘：当前轮输入携带已提交的 msgUid（m1）→ priorEvents 按 msgUid 排除，不重计
        val retry = engine.prepareLiveContext(
            "c", "s", currentTurn = 2,
            dialogue = listOf(
                DialogueMessage(id = "m1", speaker = DialogueSpeaker.PARTNER, text = "周五见？"),
                DialogueMessage(id = "m4", speaker = DialogueSpeaker.PARTNER, text = "新消息")
            ),
            onlyThisRound = false
        )!!
        assertFalse("已提交同 msgUid 的当前轮消息不得重复进历史", retry.historyBlock.contains("m_src[0]"))
        assertTrue(retry.historyBlock.contains("m_src[1]"))
        // 装配零写入：事件库仍只有两条已提交事件（ChampionEventStore.append 零调用）
        val store = ChampionEventStore(FileChampionEventStorage(File(dir, "events")))
        assertEquals(2, runBlocking { store.events("c__s") }.size)
    }

    @Test
    fun `multi message turn still counts as one round for the domain split`() = runTest {
        // 轮次口径（§5.3 排除清单）：一轮可含多条 HER/ME 消息，正式轮数只 +1——
        // 分域只认 currentTurn 参数（committedTurnCount+1），绝不取 dialogue.size
        val (engine, _) = newEngine(FakeSettings(mode = "LIVE_AUTO"), gateway = NoModelGateway())
        val threeMsgs = listOf(
            DialogueMessage(id = "a", speaker = DialogueSpeaker.PARTNER, text = "第一条"),
            DialogueMessage(id = "b", speaker = DialogueSpeaker.PARTNER, text = "第二条"),
            DialogueMessage(id = "c", speaker = DialogueSpeaker.USER, text = "第三条")
        )
        val t24 = engine.prepareLiveContext("c", "s", currentTurn = 24, dialogue = threeMsgs, onlyThisRound = false)!!
        assertEquals("已提交 23 + 1 = 24：即使 3 条消息仍是短域", ChampionTurnStrategy.GUARD_SHORT, t24.strategy)
        val t25 = engine.prepareLiveContext("c", "s", currentTurn = 25, dialogue = threeMsgs, onlyThisRound = false)!!
        assertEquals("已提交 24 + 1 = 25：即使 3 条消息仍是长域", ChampionTurnStrategy.UC_LONG, t25.strategy)
    }

    @Test
    fun `prepareLiveContext builds uc block only in the long domain`() = runTest {
        val (engine, _) = newEngine(FakeSettings(mode = "LIVE_AUTO"), gateway = NoModelGateway())
        runBlocking {
            engine.commitConfirmedTurn(
                "c", "s",
                listOf(ChampionMessage("HER", "听说快递周五到，还没收到呢", "2026-11-06 09:59", "m1")),
                modeSnapshot = "LIVE_AUTO", turn = 24
            )
        }
        // 短域（第 24 轮）：同数据 UC 恒空——短域禁 UC（禁变相 uc_guard，§0.2）
        val short = engine.prepareLiveContext(
            "c", "s", currentTurn = 24,
            dialogue = listOf(DialogueMessage(id = "m2", speaker = DialogueSpeaker.PARTNER, text = "那周五的事呢")),
            onlyThisRound = false
        )!!
        assertEquals(ChampionTurnStrategy.GUARD_SHORT, short.strategy)
        assertEquals("", short.uncertainBlock)
        assertEquals(0, short.uncertainCu)
        // 长域（第 25 轮）：UC 按 UncertainBlockDetector 规则生成（未定事项在场才注入）
        val long = engine.prepareLiveContext(
            "c", "s", currentTurn = 25,
            dialogue = listOf(DialogueMessage(id = "m2", speaker = DialogueSpeaker.PARTNER, text = "那周五的事呢")),
            onlyThisRound = false
        )!!
        assertEquals(ChampionTurnStrategy.UC_LONG, long.strategy)
        // UC 只注入命中的分句（clause 粒度），非整条消息原文
        assertTrue("未定事项在场 → 注入逐字分句", long.uncertainBlock.contains("听说快递周五到"))
        assertTrue(long.uncertainBlock.startsWith(UncertainBlockDetector.UNCERTAIN_TITLE))
        assertTrue(long.uncertainCu > 0)
        assertEquals(long.uncertainCu, UncertainBlockDetector.utf16Len(long.uncertainBlock))
    }

    @Test
    fun `generation input freeze defaults stay backward compatible`() {
        // Step 1：旧调用方（不传新参）→ championMode=OFF、committedTurnCount=null（默认兼容）
        val legacy = buildGenerationInput(
            requestId = "r", messages = emptyList(), userHint = "",
            knowledgeBase = KnowledgeBase(name = "kb1", displayName = "kb1", stage = "S"),
            intentConfig = IntentConfig(), corrections = emptyMap(), correctionsRevision = 0,
            onlyThisRound = false, aggressive = false, providerIdentity = null,
            kbProfile = "p", kbRevision = "v", promptAssetHash = "h"
        )
        assertEquals("OFF", legacy.championMode)
        assertNull("读失败/未传 = null：不猜轮次，全链回退原生", legacy.kbContext?.committedTurnCount)
        // 生产调用方传冻结快照 → 原值写入（committedTurnCount 只在 kb != null 时进 KbContext）
        val live = buildGenerationInput(
            requestId = "r", messages = emptyList(), userHint = "",
            knowledgeBase = KnowledgeBase(name = "kb1", displayName = "kb1", stage = "S"),
            intentConfig = IntentConfig(), corrections = emptyMap(), correctionsRevision = 0,
            onlyThisRound = false, aggressive = false, providerIdentity = null,
            kbProfile = "p", kbRevision = "v", promptAssetHash = "h",
            championMode = "LIVE_AUTO", committedTurnCount = 24
        )
        assertEquals("LIVE_AUTO", live.championMode)
        assertEquals(24, live.kbContext?.committedTurnCount)
        // kb 为 null → KbContext 整体为 null（committedTurnCount 不落空壳）
        assertNull(
            buildGenerationInput(
                requestId = "r", messages = emptyList(), userHint = "", knowledgeBase = null,
                intentConfig = IntentConfig(), corrections = emptyMap(), correctionsRevision = 0,
                onlyThisRound = false, aggressive = false, providerIdentity = null,
                kbProfile = "", kbRevision = "", promptAssetHash = "h",
                championMode = "LIVE_AUTO", committedTurnCount = 24
            ).kbContext
        )
    }

    // ── shadow 全链路 ──

    @Test
    fun `shadowTurn assembles generates guards and logs both sides without touching the event store`() = runTest {
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
        // 审计 R2-1：shadow 生成路径不写事件库——未确认输入零持久痕迹
        val store = ChampionEventStore(FileChampionEventStorage(File(dir, "events")))
        assertEquals(0, runBlocking { store.events("c1__s1") }.size)
        // 正式提交点入库（uid = pending uid，与 shadow 日志 source_id 一致）
        val committed = runBlocking {
            engine.commitConfirmedTurn("c1", "s1", listOf(
                ChampionMessage("HER", "周五见？", "2026-11-06 09:59", "m1"),
                ChampionMessage("ME", "好", "2026-11-06 10:00", "m2")
            ))
        }
        assertEquals(listOf(0L, 1L), committed.map { it.uid })
        assertTrue(committed.none { it.text.contains("好的") })   // shadow 回复永不入库
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
    fun `abandoned unconfirmed input never reaches the store and re-commit stays deduped`() = runTest {
        val settings = FakeSettings(mode = "SHADOW")
        val (engine, dir) = newEngine(settings)
        // 第一次生成（输入会被用户删掉重来）——shadow 只装配
        engine.shadowTurn("r1", "c", "s", turn = -1,
            messages = listOf(Triple("HER", "错别字版本", "2026-11-06 09:00")),
            intent = IntentConfig(), advisorNote = "", originalReply = "", timeoutMs = 5_000, providerConfig = null)
        // 用户改了输入再生成一次——仍是 shadow，不落库
        engine.shadowTurn("r2", "c", "s", turn = -1,
            messages = listOf(Triple("HER", "改好的版本", "2026-11-06 09:00")),
            intent = IntentConfig(), advisorNote = "", originalReply = "", timeoutMs = 5_000, providerConfig = null)
        val store = ChampionEventStore(FileChampionEventStorage(File(dir, "events")))
        assertEquals(0, runBlocking { store.events("c__s") }.size)
        // 正式提交：只记用户最终确认的那一条；重复提交（同一 pending 轮 = 同 msgUid）去重不重复入库
        runBlocking {
            engine.commitConfirmedTurn("c", "s", listOf(ChampionMessage("HER", "改好的版本", "2026-11-06 09:00", "m1")))
            engine.commitConfirmedTurn("c", "s", listOf(ChampionMessage("HER", "改好的版本", "2026-11-06 09:00", "m1")))
        }
        val all = runBlocking { store.events("c__s") }
        assertEquals(1, all.size)
        assertEquals("改好的版本", all.first().text)
        assertEquals(1, all.first().turn)
        // 提交后下一轮 shadow 取到正确 turn（历史已确认）
        val out3 = engine.shadowTurn("r3", "c", "s", turn = -1,
            messages = listOf(Triple("ME", "新一条", "2026-11-06 10:00")),
            intent = IntentConfig(), advisorNote = "", originalReply = "", timeoutMs = 5_000, providerConfig = null)
        assertEquals(2, out3.logLine["turn"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `two identical messages in the same minute commit as two events`() = runTest {
        // 审计 F2-1 主案情走完整提交点：对方同一分钟连发两次"好"是两条真实消息——
        // 不同 ChatMessage.id ⇒ 两条事件；旧 role|text|ts 键在这里把第二条合没了。
        val settings = FakeSettings(mode = "SHADOW")
        val (engine, dir) = newEngine(settings)
        runBlocking {
            engine.commitConfirmedTurn("c", "s", listOf(
                ChampionMessage("HER", "好", "2026-11-06 10:05", "m1"),
                ChampionMessage("HER", "好", "2026-11-06 10:05", "m2")
            ))
        }
        val store = ChampionEventStore(FileChampionEventStorage(File(dir, "events")))
        val all = runBlocking { store.events("c__s") }
        assertEquals(2, all.size)
        assertEquals(listOf("m1", "m2"), all.map { it.msgUid })
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
        // 审计 R2-3：接线状态显式登记——S2/S3 未接线按 OFF 处理
        assertTrue(obj["s2_s3_status"]!!.jsonPrimitive.content.contains("NOT_WIRED"))
        // guide-6 Step 6：LIVE_AUTO 生产接线登记——wired_modes 扩到三档 + 分域候选来源
        val wired = obj["wired_modes"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("OFF", "SHADOW", "LIVE_AUTO"), wired)
        assertEquals("LIVE_AUTO", obj["default_mode"]!!.jsonPrimitive.content)
        assertEquals("LoveBrain PromptBuilder", obj["live_prompt_source"]!!.jsonPrimitive.content)
        assertTrue(obj["live_candidate_by_turn"]!!.jsonPrimitive.content.contains("<=24"))
        assertTrue(obj["live_candidate_by_turn"]!!.jsonPrimitive.content.contains(">=25"))
        assertTrue(obj["legacy_shadow_candidate"]!!.jsonPrimitive.content
            .contains("CROSS_DOMAIN_NOT_PROMOTED"))
        assertEquals(3600, obj["history_budget_cu"]!!.jsonPrimitive.int)
        assertTrue(obj["history_commit_point"]!!.jsonPrimitive.content.contains("nextRound"))
        assertEquals("BYPASS_CHAMPION_CONTEXT", obj["only_this_round"]!!.jsonPrimitive.content)
        // 2026-11-12 用户纪律：sha256 防御性分析停用——登记内容照常，哈希登记字段注明停用
        assertTrue(obj["sha256_registration"]!!.jsonPrimitive.content.contains("DISABLED"))
        // 历史 hash 表（移植/F2 批次实测值；对应源文件 guide-6 批次零改动，不重算）
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
