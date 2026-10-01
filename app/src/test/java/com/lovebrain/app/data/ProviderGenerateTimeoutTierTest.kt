package com.lovebrain.app.data

import android.util.Log
import com.lovebrain.app.AppConfig
import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyEvent
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.buildGenerationInput
import com.lovebrain.app.domain.toIdentity
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Issue #5 / 外部复核 `LoveBrain_Full_Audit_4f0dc77_2026-09-30` §2 P1-1：
 * **自定义 Provider 的生成超时按工单调档**——有界白名单 60/120/180/300 秒，默认仍是 120 秒，
 * 连接超时不跟着放开。
 *
 * 这一族判据钉的是四件互相独立的事，缺一条就会长出对应的那一种坏实现：
 *
 * 1. **默认没被改动**：一张从没配过档位的工单，链路上那个数还得是 120 秒。
 *    放开这一档最坏的失败方式不是慢服务没修好，而是官方 Key 的用户从此默认等 300 秒。
 * 2. **档位真的走到线上去**：`ticket → ProviderRequestConfig → withTimeout / OkHttp 读超时`
 *    三段各有一格，不接受"存进了 prefs 就算生效"。
 * 3. **白名单与回落**：界面上只有四颗；盘上读到非法值或老数据一律落回默认档，
 *    既不许照抄 999，也不许因为读不出而变成 0 秒或无限等待。
 * 4. **有界**：最大那一档跑完四段重试也必须收口成一条失败事件，不能变成无限等待。
 * 5. **连接/写超时不跟档位走**（复核原话"连接超时不必一起放开"）——
 *    单独判一条，因为"顺手把整个 client 按档位建一遍"是这次改动最省事的写法，
 *    而那正好把"根本联系不上服务器"拖成 300 秒。
 *
 * 全部纯 JVM：resolver 用 mockk 的 SecurePrefs，Engine 用假 AiGateway + 虚拟时间，
 * 一次真实网络请求都不发。
 */
class ProviderGenerateTimeoutTierTest {

    private lateinit var prefs: SecurePrefs

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        prefs = mockk(relaxed = true)
        every { prefs.activeTicketId } returns "A"
        every { prefs.getWorkerApiKey(any()) } returns "key-A"
        every { prefs.thinkingMode } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun ticketWith(timeoutSec: Int?) = ProviderTicket(
        id = "A",
        name = "慢速兼容服务",
        baseUrl = "https://provider-a.example.com/v1/chat",
        model = "model-a-flash",
        thinkingMode = 0,
        generateTimeoutSec = timeoutSec
    )

    private fun stubTicket(ticket: ProviderTicket) {
        every { prefs.getWorkerTickets() } returns listOf(ticket)
    }

    private fun resolve(): ProviderRequestConfig? = ProviderConfigResolver(prefs).resolveFor("A")

    private fun configAt(timeoutSec: Int?): ProviderRequestConfig {
        stubTicket(ticketWith(timeoutSec))
        return resolve()!!
    }

    // ═══════════ 1. 白名单本身 + 默认档 ═══════════

    @Test
    fun `the whitelist is exactly the four bounded tiers`() {
        assertEquals(
            "档位只有四颗，且必须正好是 60/120/180/300——多一颗少一颗都是悄悄改了产品合同",
            listOf(60, 120, 180, 300),
            GenerationTimeoutTier.options.map { it.seconds }
        )
    }

    @Test
    fun `the default tier is 120 seconds and is the constant it replaced`() {
        assertEquals("默认档必须是 120 秒（复核 §2 P1-1：默认仍为 120）",
            120, GenerationTimeoutTier.DEFAULT.seconds)
        assertEquals(
            "默认档换算出来必须逐字等于这条链路原来的固定总超时，否则「默认没变」那句是假的",
            AppConfig.GENERATE_TIMEOUT_MS, GenerationTimeoutTier.DEFAULT.millis
        )
        assertEquals("秒→毫秒这一处换算本身（300 秒 = 300000 毫秒）",
            300_000L, GenerationTimeoutTier.SEC_300.millis)
    }

    // ═══════════ 2. 默认一路到底：从没配过档位的工单仍是 120 秒 ═══════════

    @Test
    fun `a ticket that never carried a tier still freezes 120 seconds into the request config`() {
        val config = configAt(null)
        assertEquals(AppConfig.GENERATE_TIMEOUT_MS, config.generateTimeoutMs)
        assertEquals(
            "默认档下流式读超时也必须与改动前那个固定值逐字相同（不因为这次改动而变）",
            AppConfig.STREAM_READ_TIMEOUT_SEC, config.streamReadTimeoutSec
        )
    }

    @Test
    fun `legacy ticket json without the new field decodes to null and resolves to the default`() {
        // 升级前落盘的 JSON：一个字节都没改过，generateTimeoutSec 这个 key 根本不存在。
        // 读不出必须 = null（而不是抛异常把整张工单表判成空表），再由消费侧落回默认档。
        val legacyJson = """[{"id":"A","name":"老数据","baseUrl":"https://provider-a.example.com/v1/chat",""" +
            """"model":"model-a-flash","models":["model-a-flash"],"thinkingMode":0}]"""
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(ProviderTicket.serializer()), legacyJson)
        assertEquals("老数据 JSON 必须还能解出来（整表解不开 = 用户看起来像没配供应商）",
            1, decoded.size)
        assertNull("老数据里没有这一项，必须解成 null 而不是 0", decoded.single().generateTimeoutSec)
        stubTicket(decoded.single())
        assertEquals(AppConfig.GENERATE_TIMEOUT_MS, resolve()!!.generateTimeoutMs)
    }

    // ═══════════ 3. 300 秒真的走到线上（三段各一格） ═══════════

    @Test
    fun `a ticket set to 300 reaches the request config as 300 seconds`() {
        val config = configAt(300)
        assertEquals("档位写在工单上，必须被冻结进请求配置", 300_000L, config.generateTimeoutMs)
        assertEquals(
            "总超时那一档同时决定流式读超时——不然档位给到 300，120 秒先把慢服务掐了",
            300L, config.streamReadTimeoutSec
        )
    }

    @Test
    fun `the 300 second tier really builds an okhttp client that reads for 300 seconds`() {
        val client = CancellableHttpTransport.streamClient(configAt(300).streamReadTimeoutSec)
        assertEquals("OkHttp 这一颗的读超时就是这一档：档位到了 http 层的直接证据",
            300_000, client.readTimeoutMillis)
        assertSame("一个档位一颗 client，按档位复用（每轮 new 一颗等于把连接池扔掉）",
            client, CancellableHttpTransport.streamClient(300L))
        assertNotSame("不同档位必须是不同的读超时，不能都退化成同一颗",
            client, CancellableHttpTransport.streamClient(60L))
    }

    @Test
    fun `connect and write timeouts stay fixed no matter how long the read tier is`() {
        val client = CancellableHttpTransport.streamClient(configAt(300).streamReadTimeoutSec)
        assertEquals(
            "复核 §2 P1-1：连接超时不一起放开——慢的是「它在生成」，不是「联系不上」",
            (AppConfig.CONNECT_TIMEOUT_SEC * 1000).toInt(), client.connectTimeoutMillis
        )
        assertEquals("写超时同理，不跟档位走",
            (AppConfig.WRITE_TIMEOUT_SEC * 1000).toInt(), client.writeTimeoutMillis)
        assertEquals("连接超时这一档全局只有一颗，改了要另开一格判",
            15_000, client.connectTimeoutMillis)
    }

    @Test
    fun `the streaming call is the one that takes the per ticket client`() {
        // 结构证人：真正发起流式请求的那一行必须从**冻结好的 config** 取读超时，
        // 而且不能再留一颗全局固定的 streamClient（那等于档位只在 prefs 里活着）。
        val file = File("src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt")
        assertTrue("$file 读不到——这一格会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        assertTrue(
            "generateStream 不再按工单档位取流式 client，档位就只存在 prefs 里",
            code.contains("streamClient(resolvedConfig.streamReadTimeoutSec)")
        )
        assertFalse(
            "全局那颗固定 120 秒的 streamClient 必须消失，否则上面那一行是第二份状态",
            code.contains("private val streamClient")
        )
    }

    // ═══════════ 4. Engine 的 withTimeout 真的按档位走（虚拟时间，双向判） ═══════════

    /**
     * 假网关：一条**永不吐字**的流式响应——正是 Issue #5 那个形状
     * （连接活着、服务还在生成，就是超过 120 秒没出完整内容）。
     */
    private fun engineWith(config: ProviderRequestConfig): GenerationEngine {
        val gateway = mockk<AiGateway>()
        every { gateway.configForTicket(config.ticketId) } returns config
        every { gateway.generateStream(any(), any(), any(), any(), any()) } returns
            flow { awaitCancellation() }
        every { gateway.parseReplyResponse(any()) } returns
            LoveBrainResponse(response = ReplySchemes(recommended = "r"))
        val pb = mockk<PromptBuilder>(relaxed = true)
        every { pb.buildSystemPrompt() } returns "system"
        coEvery { pb.buildReplyUserPromptWithRefs(any(), any(), any(), any(), any(), any()) } returns
            PromptBuilder.PromptBuildResult("user", emptyList())
        every { pb.replyPromptAssetHash() } returns "assets"
        return GenerationEngine(gateway, pb)
    }

    private val inputFor = { config: ProviderRequestConfig ->
        buildGenerationInput(
            requestId = "req-tier", messages = emptyList(), userHint = "", knowledgeBase = null,
            intentConfig = com.lovebrain.app.model.IntentConfig(),
            corrections = emptyMap(), correctionsRevision = 0,
            onlyThisRound = false, aggressive = false,
            providerIdentity = config.toIdentity(),
            kbProfile = "", kbRevision = "rev", promptAssetHash = "assets"
        )
    }

    /**
     * 把引擎推到"已经在 `withTimeout` 里等那条流"这一步，才允许拿虚拟时钟去量它。
     *
     * 为什么要在测试里真等一小会儿（这是本文件唯一一处 `Thread.sleep`）：
     * `replyStream` 的 prepare 阶段有两次 `withContext(Dispatchers.IO)`，那是**真实线程**，
     * `TestCoroutineScheduler` 推不过去。不等它落地就直接 `advanceTimeBy`，虚拟时间会先跑完
     * 130 秒、而那条流根本还没开始等 ⇒ `events` 恒空，两条断言一起假绿
     * （第一版就是这么红的：它报的是"300 秒到点没收口"，而真相是"什么都没开始"）。
     *
     * 判"落地"的信号是第一条事件（`ReplyStarted` 就发在 prepare 之后、超时之前），
     * 所以这里等的不是时间而是那一步本身；40 轮 × 10ms 只是上限，正常第一两轮就返回。
     */
    private fun kotlinx.coroutines.test.TestScope.settleIntoTheTimeoutWindow(events: List<ReplyEvent>) {
        repeat(40) {
            testScheduler.runCurrent()
            if (events.isNotEmpty()) return
            Thread.sleep(10L)
        }
        testScheduler.runCurrent()
        assertTrue("引擎停在 prepare 之前，一条事件都没发出来，这一格量不到超时窗口", events.isNotEmpty())
    }

    @Test
    fun `a 300 second ticket is not cut off by the old 120 second cap`() = runTest {
        val config = configAt(300)
        val engine = engineWith(config)
        val input = inputFor(config)
        val events = mutableListOf<ReplyEvent>()
        val job = launch { engine.replyStream(input).collect { events.add(it) } }
        settleIntoTheTimeoutWindow(events)

        // 越过旧的 120 秒：这一档给的是 300，流必须还活着（一条重试提示都不该发出来）
        testScheduler.advanceTimeBy(130_000L)
        testScheduler.runCurrent()
        assertTrue(
            "300 秒档在 130 秒就被老那把 120 秒的闸掐了 ⇒ withTimeout 没吃档位。实到事件：$events",
            events.none { it is ReplyChunk }
        )
        assertTrue("更不该已经收尾：" + events.filterIsInstance<ReplyCompleted>(),
            events.none { it is ReplyCompleted })

        // 越过 300 秒：有界——它必须**还是**会超时，而不是变成无限等待
        testScheduler.advanceTimeBy(200_000L)
        testScheduler.runCurrent()
        assertTrue(
            "300 秒到点必须仍然收口（档位有界，卡死的请求不许无限等）：$events",
            events.any { it is ReplyChunk }
        )
        job.cancel()
    }

    @Test
    fun `the default tier still cuts at 120 seconds`() = runTest {
        val config = configAt(null)
        val engine = engineWith(config)
        val input = inputFor(config)
        val events = mutableListOf<ReplyEvent>()
        val job = launch { engine.replyStream(input).collect { events.add(it) } }
        settleIntoTheTimeoutWindow(events)

        testScheduler.advanceTimeBy(130_000L)
        testScheduler.runCurrent()
        assertTrue(
            "没配档位的工单必须在 120 秒收口——否则这次改动把默认值一起放开了：$events",
            events.any { it is ReplyChunk }
        )
        job.cancel()
    }

    @Test
    fun `even the biggest tier ends in a failure event, never in an endless wait`() = runTest {
        val config = configAt(300)
        val events = mutableListOf<ReplyEvent>()
        engineWith(config).replyStream(inputFor(config)).collect { events.add(it) }

        val completed = events.filterIsInstance<ReplyCompleted>()
        assertEquals("四段重试跑完之后必须恰好收一条 ReplyCompleted", 1, completed.size)
        assertTrue(
            "收的必须是一条失败，不是假装成功：" + completed.single().result,
            completed.single().result is GenerateResult.Error
        )
    }

    // ═══════════ 5. 白名单回落：非法值 / 越界值 / null 一律落回默认档 ═══════════

    @Test
    fun `every value outside the whitelist falls back to the default tier`() {
        val junk = listOf(0, 1, 59, 61, 119, 121, 240, 999, -300, Int.MAX_VALUE)
        val wrong = junk.filter { GenerationTimeoutTier.fromSecondsOrDefault(it) != GenerationTimeoutTier.DEFAULT }
        assertTrue("这些非档位值全都要落回默认档，跑掉的：$wrong", wrong.isEmpty())
        assertEquals("null（升级前的老数据）也必须落回默认档",
            GenerationTimeoutTier.DEFAULT, GenerationTimeoutTier.fromSecondsOrDefault(null))
        assertNull("四档之外没有任何值能被认下来", GenerationTimeoutTier.fromSecondsOrNull(90))
        assertEquals("四档之内才谈得上有效",
            GenerationTimeoutTier.SEC_300, GenerationTimeoutTier.fromSecondsOrNull(300))
    }

    @Test
    fun `a tampered illegal value on the ticket never reaches the wire as itself`() {
        val config = configAt(999)
        assertEquals(
            "盘上被写脏了也只能按默认档发出去，绝不能把 999 秒照抄成等待预算",
            AppConfig.GENERATE_TIMEOUT_MS, config.generateTimeoutMs
        )
        assertEquals("读超时同理：脏值不能把 SSE 读开放成 999 秒",
            AppConfig.STREAM_READ_TIMEOUT_SEC, config.streamReadTimeoutSec)
        assertEquals("落到 client 上也就是默认那一颗",
            AppConfig.GENERATE_TIMEOUT_MS.toInt(),
            CancellableHttpTransport.streamClient(config.streamReadTimeoutSec).readTimeoutMillis)
    }

    @Test
    fun `saving through the view model persists a bounded tier, never the junk it was handed`() = runTest {
        val saved = mutableListOf<List<ProviderTicket>>()
        val writePrefs = mockk<SecurePrefs>(relaxed = true)
        every { writePrefs.getWorkerTickets() } returns emptyList()
        every { writePrefs.getWorkerApiKey(any()) } returns "sk-x"
        every { writePrefs.setWorkerTickets(any()) } answers { saved.add(firstArg()) }
        val repo = mockk<DeepSeekRepository>(relaxed = true)
        coEvery {
            repo.testConnectionWithProbe(any(), any(), any())
        } returns ConnectionTestResult(success = true, resolvedUrl = "https://x.example.com/chat/completions")

        val ok = SetupViewModel(writePrefs, repo).saveTicketWithProbe(
            ticketId = null, name = "慢服务", baseUrl = "https://x.example.com",
            models = listOf("m"), apiKey = "sk-x", thinkingMode = 0,
            generateTimeoutSec = 999
        )
        assertTrue("探测桩是成功的，保存不该失败", ok)
        assertEquals("落盘的必须恰好一张工单", 1, saved.size)
        assertEquals(
            "表单交来 999 这种非档位值，盘上必须存成默认档 120，而不是原样 999",
            GenerationTimeoutTier.DEFAULT.seconds, saved.single().single().generateTimeoutSec
        )

        saved.clear()
        SetupViewModel(writePrefs, repo).saveTicketWithProbe(
            ticketId = null, name = "慢服务", baseUrl = "https://x.example.com",
            models = listOf("m"), apiKey = "sk-x", thinkingMode = 0,
            generateTimeoutSec = 300
        )
        assertEquals("合法档位原样落盘", 300, saved.single().single().generateTimeoutSec)
    }
}
