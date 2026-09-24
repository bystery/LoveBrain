package com.lovebrain.app.domain.port

import android.content.Context
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.StreamEvent
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiGateway] 的合同：谁实现谁遵守。生产侧与 fake 侧各跑一遍同样的格子
 * （复核 §4 的 LSP 验收标准："同一 contract test suite 对 production adapter 和 fake 都通过"）。
 *
 * 这里刻意只收**不需要网络就能判定**的语义——一旦合同要求真发请求，
 * 它就不再是"端口合同"而是网络集成测试了，那种证据属于 instrumentation / 费用 dry-run。
 */
abstract class AiGatewayContract {

    protected abstract fun newGateway(): AiGateway

    /** 没有配置任何供应商时，端口必须"什么都拿不到"，而不是给个看起来能用的默认 */
    @Test
    fun `an unconfigured gateway reports missing instead of inventing a config`() {
        val gw = newGateway()
        assertNull("没有 active 工单时 snapshot 必须是 null", gw.snapshotProviderConfig())
        assertNull(gw.configForTicket("ticket-that-does-not-exist"))
        assertNull("空 ticketId 不得回退到当前 active", gw.configForTicket(""))
    }

    /** 未配置时辅助任务不得抛穿、也不得伪装成功 */
    @Test
    fun `generate without a provider returns an empty unsuccessful result and never throws`() {
        val gw = newGateway()
        assertEquals("", runBlocking { gw.generateRaw("system", "user") })
        val raw = runBlocking { gw.generateRawWithMetadata("system", "user") }
        assertEquals("", raw.content)
        assertFalse("空响应不能算成功", raw.isSuccess)
    }

    /** 解析不出结构必须抛：把它吞成"零方案"会让用户看到一次安静失败的空白回复 */
    @Test
    fun `parseReplyResponse refuses to pass garbage off as a reply`() {
        val gw = newGateway()
        val threw = runCatching { gw.parseReplyResponse("这完全不是一段 JSON 回复") }.isFailure
        assertTrue("非法正文必须抛，让调用方走错误态", threw)
    }

    /** 流式入口在没配置时也要给一个可取消的 Flow，而不是当场炸 */
    @Test
    fun `streaming entry point returns a flow rather than throwing at call time`() {
        val gw = newGateway()
        val flow = runCatching { gw.generateStream("s", "u") }
        assertTrue("generateStream 是冷流：装配时不该抛", flow.isSuccess)
        assertNotNull(flow.getOrNull())
    }
}

/** 生产侧：真实的 DeepSeekRepository + 一个空的 relaxed SecurePrefs */
class ProductionAiGatewayContractTest : AiGatewayContract() {
    override fun newGateway(): AiGateway = DeepSeekRepository(mockk<SecurePrefs>(relaxed = true))
}

/**
 * fake 侧：给 UI / instrumentation 用的假网关，同时被这套合同管着。
 *
 * requestCount 是**真链路计数器**：断言"点空态蓝字 0 次网络请求"这类合同，
 * 靠的就是它，而不是源码里搜有没有调用。
 */
class FakeAiGateway(
    private val configured: ProviderRequestConfig? = null,
    var scriptedChunks: List<String> = emptyList(),
    var parseShouldThrow: Boolean = true
) : AiGateway {

    var requestCount = 0
        private set
    var cancelled = false

    override fun snapshotProviderConfig(): ProviderRequestConfig? = configured

    override fun configForTicket(ticketId: String): ProviderRequestConfig? =
        configured?.takeIf { it.ticketId == ticketId && ticketId.isNotEmpty() }

    override fun generateStream(
        systemPrompt: String,
        userPrompt: String,
        thinkingOverride: Int?,
        thinkingShapeIndex: Int,
        config: ProviderRequestConfig?
    ): Flow<StreamEvent> {
        requestCount++
        return kotlinx.coroutines.flow.flow {
            val effective = config ?: configured
            if (effective == null) {
                throw IllegalStateException("no provider configured")
            }
            scriptedChunks.forEach { emit(StreamEvent.Chunk(it)) }
            emit(StreamEvent.Complete(fullText = scriptedChunks.joinToString("")))
        }
    }

    override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String {
        requestCount++
        return if (configured == null) "" else "fake raw"
    }

    override suspend fun generateRawWithMetadata(
        systemPrompt: String,
        userPrompt: String
    ): RawGenerationResult {
        requestCount++
        return if (configured == null) {
            RawGenerationResult(content = "", finishReason = null)
        } else {
            RawGenerationResult(content = "fake raw", finishReason = "stop")
        }
    }

    override fun parseReplyResponse(content: String): LoveBrainResponse {
        if (parseShouldThrow) throw IllegalArgumentException("fake refuses to parse garbage")
        return LoveBrainResponse()
    }

    /** 供取消审计类用例调用：证明取消被诚实传播而不是被吞 */
    fun markCancelled() { cancelled = true }
}

/** fake 必须通过同一套合同——它想蒙混只有一条路：把语义做对 */
class FakeAiGatewayContractTest : AiGatewayContract() {
    override fun newGateway(): AiGateway = FakeAiGateway(configured = null)
}
