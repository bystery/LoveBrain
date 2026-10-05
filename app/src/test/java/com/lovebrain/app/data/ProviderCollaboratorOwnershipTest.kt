package com.lovebrain.app.data

import android.util.Log
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.util.UsagePricer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * 拆出来的四个所有者各自的直接行为锚点。
 *
 * 搬之前这些判据只能隔着 `DeepSeekRepository` 摸（要构造仓库、要 mock 偏好存储，
 * 计费那一格还得绕开统计持久化）。搬完之后每个所有者都能被单独构造、单独判，
 * "一个变化理由一个所有者"才有机器证据，而不只是一句注释。
 *
 * 覆盖：
 * - [ProviderConfigResolver]：按指定 ticketId 取值绝不回退 active、脏地址与明文外网地址拒发；
 * - [ApiUsageTracker]：token 入账口径、计费双条件、成本事件携带 scope、统计从盘上恢复；
 * - [OpenAiChatWire]：thinking 降级链四种形状、SSE 帧读法、最小探测体；
 * - 以上都不发网络。
 */
class ProviderCollaboratorOwnershipTest {

    private lateinit var prefs: SecurePrefs

    private val ticketA = ProviderTicket(
        id = "A",
        name = "Provider-A",
        baseUrl = "https://provider-a.example.com/v1/chat",
        model = "model-a-flash",
        thinkingMode = 1
    )
    private val ticketB = ProviderTicket(
        id = "B",
        name = "Provider-B",
        baseUrl = "https://api.deepseek.com/v1/chat",
        model = "deepseek-reasoner-pro",
        thinkingMode = 0
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        prefs = mockk(relaxed = true)
        every { prefs.activeTicketId } returns "A"
        every { prefs.getWorkerTickets() } returns listOf(ticketA, ticketB)
        every { prefs.getWorkerApiKey("A") } returns "key-A"
        every { prefs.getWorkerApiKey("B") } returns "key-B"
        every { prefs.thinkingMode } returns 0
        every { prefs.loadApiStats() } returns null
        every { prefs.saveApiStats(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun resolver() = ProviderConfigResolver(prefs)

    private fun tracker() = ApiUsageTracker(prefs)

    private fun cfg(baseUrl: String, model: String, thinkingMode: Int = 1) = ProviderRequestConfig(
        ticketId = "T", apiKey = "key-T", baseUrl = baseUrl, model = model, thinkingMode = thinkingMode
    )

    private fun bodyRoot(body: String) = json.parseToJsonElement(body).jsonObject

    // ═══════════ ProviderConfigResolver：身份只认那个 ticketId ═══════════

    @Test
    fun resolveFor_never_falls_back_to_the_active_ticket() {
        val frozen = resolver().resolveFor("B")
        assertNotNull("按 ticketId 取配置应成功", frozen)
        assertEquals("B", frozen!!.ticketId)
        assertEquals("key-B", frozen.apiKey)
        assertEquals("deepseek-reasoner-pro", frozen.model)
        // active 仍是 A：解析结果里不许混进 A 的任何身份字段
        assertEquals("A", prefs.activeTicketId)
        assertTrue("baseUrl 必须是 B 的", frozen.baseUrl.contains("deepseek.com"))
    }

    @Test
    fun blank_or_unknown_ticketId_yields_null_not_the_active_config() {
        // 反证 之后补的一刀。原来这一格把 Key 查找留着不桩（relaxed mock 回空串），
        // 于是"把 ticket 查找改成回退 active"这种坏实现照样绿：
        // 拦住它的是 Key 查不到那道顺手闸，不是这条判据自己要钉的 ticket 查找。
        // 现在任何 ticketId 都拿得到 Key，回退只剩 ticket 查找这一处能拦。
        every { prefs.getWorkerApiKey(any()) } returns "key-for-whatever"
        assertNull("空 ticketId 不得回退到 active", resolver().resolveFor(""))
        assertNull("未知 ticketId 不得回退到 active", resolver().resolveFor("NOPE"))
    }

    @Test
    fun thinkingMode_falls_back_to_global_only_when_ticket_omits_it() {
        every { prefs.getWorkerTickets() } returns listOf(ticketA.copy(thinkingMode = null), ticketB)
        every { prefs.thinkingMode } returns 1
        assertEquals("工单没写才用全局", 1, resolver().resolveActive()!!.thinkingMode)

        every { prefs.thinkingMode } returns 1
        assertEquals("工单自己写了 0，全局不得盖上去", 0, resolver().resolveFor("B")!!.thinkingMode)
    }

    @Test
    fun dirty_or_plaintext_address_is_refused_before_any_request() {
        // Key 误填进地址栏
        assertThrows(IllegalArgumentException::class.java) {
            resolver().normalizeBaseUrl("https://api.deepseek.com sk-deadbeef")
        }
        // 对外明文：拒发
        assertThrows(IllegalArgumentException::class.java) {
            resolver().normalizeBaseUrl("http://api.example.com/v1")
        }
        // 本机明文：放行（本地 LLM 场景）
        assertEquals("http://127.0.0.1:11434", resolver().normalizeBaseUrl("http://127.0.0.1:11434/"))
        // 填什么用什么：不补全任何路径
        assertEquals("https://api.deepseek.com/v1/chat", resolver().normalizeBaseUrl(ticketB.baseUrl))
    }

    @Test
    fun incomplete_ticket_yields_null_from_both_entry_points() {
        every { prefs.getWorkerTickets() } returns listOf(ticketA.copy(model = ""), ticketB)
        every { prefs.getWorkerApiKey("A") } returns null
        assertNull(resolver().resolveActive())
        assertNull(resolver().resolveFor("A"))
    }

    // ═══════════ ApiUsageTracker：入账与计费口径 ═══════════

    private fun usageRoot(hit: Int, miss: Int, prompt: Int, completion: Int) =
        bodyRoot(
            """{"usage":{"prompt_cache_hit_tokens":$hit,"prompt_cache_miss_tokens":$miss,""" +
                """"prompt_tokens":$prompt,"completion_tokens":$completion}}"""
        )

    @Test
    fun token_usage_lands_in_the_snapshot_regardless_of_billing() {
        val t = tracker()
        // provider-a.example.com 不满足计费双条件，但 token 必须照入账
        val billed = t.logUsage(
            usageRoot(hit = 100, miss = 20, prompt = 120, completion = 7),
            cfg(ticketA.baseUrl, ticketA.model), CostScope.FOREGROUND
        )
        assertNull("非 deepseek 地址不该算出钱", billed?.costYuan)
        val s = t.stats
        assertEquals(120L, s.totalPromptTokens)
        assertEquals(7L, s.totalCompletionTokens)
        assertEquals(100L, s.totalCacheHitTokens)
        assertEquals(20L, s.totalCacheMissTokens)
        assertEquals(127L, s.totalTokens)
        assertEquals(120, billed?.promptTokens)
        assertEquals(7, billed?.completionTokens)
    }

    @Test
    fun cost_event_carries_the_scope_of_the_caller() = runTest {
        val t = tracker()
        val events = mutableListOf<UsageCostEvent>()
        // backgroundScope：收集器是常驻的，不该把 runTest 拖到超时
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            t.costEvents.collect { events.add(it) }
        }

        val billed = t.logUsage(
            usageRoot(hit = 1_000_000, miss = 1_000_000, prompt = 2_000_000, completion = 1_000_000),
            cfg(ticketB.baseUrl, ticketB.model), CostScope.BACKGROUND
        )
        assertEquals(1, events.size)
        assertEquals(CostScope.BACKGROUND, events[0].scope)
        assertEquals(billed?.costYuan, events[0].yuan)
        assertTrue("用量非零时金额必须大于 0", events[0].yuan > 0.0)
    }

    @Test
    fun billing_uses_the_frozen_config_model_not_the_active_ticket() {
        val t = tracker()
        val hit = 400_000L
        val miss = 300_000L
        val out = 200_000L
        val frozen = cfg("https://api.deepseek.com/v1", "some-flash-model")

        val billed = t.logUsage(
            usageRoot(hit.toInt(), miss.toInt(), (hit + miss).toInt(), out.toInt()),
            frozen, CostScope.FOREGROUND
        )
        // 高峰/低谷由真实时钟决定，两种价格都算对；错的是"档"——档位必须来自冻结 config 的模型名
        val flashPrices = setOf(
            UsagePricer.costYuan(hit, miss, out, UsagePricer.PriceTier.FLASH, peak = true),
            UsagePricer.costYuan(hit, miss, out, UsagePricer.PriceTier.FLASH, peak = false)
        )
        val proPrices = setOf(
            UsagePricer.costYuan(hit, miss, out, UsagePricer.PriceTier.PRO, peak = true),
            UsagePricer.costYuan(hit, miss, out, UsagePricer.PriceTier.PRO, peak = false)
        )
        val yuan = billed?.costYuan
        assertTrue("按冻结 config 的 flash 档计价：$yuan", yuan != null && yuan in flashPrices)
        assertTrue("不得串到 pro 档上去", yuan !in proPrices)
    }

    @Test
    fun request_counters_move_only_through_their_own_calls() {
        val t = tracker()
        t.startRequest()
        t.countRequest()
        assertEquals(0, t.stats.successCount)
        t.recordSuccess()
        t.recordFailure()
        val s = t.stats
        assertEquals(2, s.totalRequests)
        assertEquals(1, s.successCount)
        assertEquals(1, s.failCount)
        assertEquals(50, (s.successRate * 100).toInt())
    }

    @Test
    fun stats_are_restored_from_persisted_json() {
        every { prefs.loadApiStats() } returns
            """{"totalRequests":9,"successCount":7,"failCount":2,"totalPromptTokens":300,"totalCompletionTokens":40,"totalCacheHitTokens":200,"totalCacheMissTokens":100}"""
        val s = ApiUsageTracker(prefs).stats
        assertEquals(9, s.totalRequests)
        assertEquals(7, s.successCount)
        assertEquals(2, s.failCount)
        assertEquals(300L, s.totalPromptTokens)
        assertEquals(40L, s.totalCompletionTokens)
        assertEquals(340L, s.totalTokens)
    }

    // ═══════════ OpenAiChatWire：协议形状 ═══════════

    private fun bodyOf(model: String, thinkingMode: Int, shape: Int) = bodyRoot(
        OpenAiChatWire.buildRequestBody(
            cfg("https://api.deepseek.com/v1", model, thinkingMode),
            "sys", "usr", 0.7, stream = false, thinkingShapeIndex = shape
        )
    )

    @Test
    fun thinking_enabled_walks_the_four_wire_shapes_in_order() {
        val first = bodyOf("m-pro", thinkingMode = 1, shape = 0)
        assertEquals("enabled", first["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("low", first["thinking"]!!.jsonObject["reasoning_effort"]!!.jsonPrimitive.content)

        val second = bodyOf("m-pro", thinkingMode = 1, shape = 1)
        assertNull("第二形状不再包 thinking 对象", second["thinking"])
        assertEquals("low", second["reasoning_effort"]!!.jsonPrimitive.content)

        val third = bodyOf("m-pro", thinkingMode = 1, shape = 2)
        assertNull(third["thinking"])
        assertNull(third["reasoning_effort"])
        assertTrue(third["enable_thinking"]!!.jsonPrimitive.boolean)

        val fourth = bodyOf("m-pro", thinkingMode = 1, shape = 3)
        assertNull(fourth["thinking"])
        assertNull(fourth["reasoning_effort"])
        assertNull(fourth["enable_thinking"])
    }

    @Test
    fun direct_output_stops_sending_thinking_at_the_last_shape() {
        val withDisabled = bodyOf("m-flash", thinkingMode = 0, shape = 0)
        assertEquals("disabled", withDisabled["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val without = bodyOf("m-flash", thinkingMode = 0, shape = 3)
        assertNull("降级链走到底就一个 thinking 族参数都不发", without["thinking"])
    }

    @Test
    fun thinking_override_wins_over_the_frozen_config() {
        val body = bodyRoot(
            OpenAiChatWire.buildRequestBody(
                cfg("https://api.deepseek.com/v1", "m-pro", thinkingMode = 1),
                "sys", "usr", 0.7, stream = true, thinkingOverride = 0
            )
        )
        assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun sse_lines_are_read_the_way_the_provider_writes_them() {
        assertNull(OpenAiChatWire.sseDataPayload(""))
        assertNull(OpenAiChatWire.sseDataPayload(": keep-alive"))
        assertEquals(OpenAiChatWire.SSE_DONE, OpenAiChatWire.sseDataPayload("data: [DONE]"))
        assertEquals("{\"a\":1}", OpenAiChatWire.sseDataPayload("data: {\"a\":1} "))

        val contentFrame = OpenAiChatWire.parseChunk("""{"choices":[{"delta":{"content":"你好"}}]}""")
        assertEquals("你好", contentFrame.content)
        assertNull("不带 usage 的帧不得触发入账", contentFrame.usageRoot)

        val jsonNullFrame = OpenAiChatWire.parseChunk("""{"choices":[{"delta":{"content":null}}]}""")
        assertNull("JsonNull 不能读成字符串 null", jsonNullFrame.content)

        val usageFrame = OpenAiChatWire.parseChunk(
            """{"choices":[],"usage":{"prompt_tokens":1,"completion_tokens":2}}"""
        )
        assertNotNull("带 usage 的那条 chunk 要整颗交给入账方", usageFrame.usageRoot)
        assertEquals(1, usageFrame.usageRoot!!["usage"]!!.jsonObject["prompt_tokens"]!!.jsonPrimitive.int)
        assertNull(usageFrame.content)
    }

    @Test
    fun minimal_probe_body_can_be_sent_without_the_thinking_parameter() {
        val withThinking = bodyRoot(OpenAiChatWire.minimalProbeBody("m-flash", thinkingDisabled = true))
        assertEquals("disabled", withThinking["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val withoutThinking = bodyRoot(OpenAiChatWire.minimalProbeBody("m-flash", thinkingDisabled = false))
        assertNull("去掉 thinking 族参数重试时，一个都不许留", withoutThinking["thinking"])
        assertEquals("m-flash", withoutThinking["model"]!!.jsonPrimitive.content)
        assertEquals(8, withoutThinking["max_tokens"]!!.jsonPrimitive.int)
        assertEquals("hi", withoutThinking["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun probe_request_and_generated_request_carry_the_same_wire_shape() {
        val probe = OpenAiChatWire.buildBearerRequest(
            "https://api.deepseek.com/v1/chat", "key-P", OpenAiChatWire.minimalProbeBody("m", false)
        )
        val generated = OpenAiChatWire.buildRequest(
            ProviderRequestConfig(
                ticketId = "T", apiKey = "key-P", baseUrl = "https://api.deepseek.com/v1/chat",
                model = "m", thinkingMode = 0
            ),
            OpenAiChatWire.buildRequestBody(
                cfg("https://api.deepseek.com/v1/chat", "m"), "s", "u", 0.7, stream = false
            )
        )
        assertEquals("Bearer key-P", probe.header("Authorization"))
        assertEquals(generated.header("Authorization"), probe.header("Authorization"))
        assertEquals(generated.header("Content-Type"), probe.header("Content-Type"))
        assertEquals("api.deepseek.com", probe.url.host)
        assertEquals(generated.url.encodedPath, probe.url.encodedPath)
    }
}
