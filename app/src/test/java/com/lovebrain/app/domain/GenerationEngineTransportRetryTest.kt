package com.lovebrain.app.domain

import android.util.Log
import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyChunkReset
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.buildGenerationInput
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 主生成链：**传输层断流**（IO/读超时/断流）即便已经收到半截正文也必须重试。
 *
 * 病根（审计 高）：仓库层用 `StreamEvent.Error(failure, partialText)` 把断流连同
 * 已累积的半截正文交回来；引擎旧判据 `if (fullText.isNotBlank()) break` 见字就收工，
 * 于是：跳过 `GENERATE_MAX_ATTEMPTS` 重试 → 截断 JSON 解析必然失败 → 真实网络故障
 * 被报成"解析回复失败"，半程结果白丢。
 *
 * 三条判据（都是跑真 [GenerationEngine.replyStream]，不手搓事件序列）：
 *  1. 断流带半截正文 ⇒ **仍然花掉重试预算**，且一次尝试的残句不粘进下一次；
 *  2. 重试成功 ⇒ 正常交付，解析器收到的是**重试那一轮**的全文（没有脏拼接）；
 *  3. 次数用尽 ⇒ 错误分类是**传输错误**（Network 文案），绝不是 Parse，
 *     且这条路上解析器一次都不该被调用。
 */
class GenerationEngineTransportRetryTest {

    /** 按剧本出声的假网关：第 n 次 collect 用第 n 份剧本（用完之后一直复用最后一份） */
    private class ScriptedGateway(
        private val config: ProviderRequestConfig,
        private val scripts: List<List<StreamEvent>>
    ) : AiGateway {
        var streamCalls = 0
            private set
        val parseInputs = mutableListOf<String>()

        override fun snapshotProviderConfig(): ProviderRequestConfig = config
        override fun configForTicket(ticketId: String): ProviderRequestConfig? = config

        override fun generateStream(
            systemPrompt: String,
            userPrompt: String,
            thinkingOverride: Int?,
            thinkingShapeIndex: Int,
            config: ProviderRequestConfig?
        ): Flow<StreamEvent> = flow {
            val script = scripts.getOrElse(streamCalls) { scripts.last() }
            streamCalls++
            script.forEach { emit(it) }
        }

        override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String =
            error("这一族不走非流式")

        override suspend fun generateRawWithMetadata(
            systemPrompt: String,
            userPrompt: String
        ): RawGenerationResult = error("这一族不走非流式")

        override fun parseReplyResponse(content: String): LoveBrainResponse {
            parseInputs += content
            return LoveBrainResponse(response = ReplySchemes(recommended = content))
        }
    }

    private val config = ProviderRequestConfig(
        ticketId = "A",
        apiKey = "key-A",
        baseUrl = "https://example.com/v1/chat",
        model = "model-a",
        thinkingMode = 0,
        generateTimeoutMs = 5_000L
    )

    /** 断流时已经吐出来的半截正文（JSON 没收口，交给解析器必然失败） */
    private val partial = "{\"response\": {\"recommended\": \"半截"

    /** 重试成功那一轮交出的完整正文（假网关的解析器只认"收到的是哪段文本"） */
    private val completeReply = "{\"response\": {\"recommended\": \"这次成了\"}}"

    private fun engineWith(gateway: AiGateway): GenerationEngine {
        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        every { promptBuilder.buildSystemPrompt() } returns "system"
        every { promptBuilder.replyPromptAssetHash() } returns "assets"
        coEvery {
            promptBuilder.buildReplyUserPromptWithRefs(any(), any(), any(), any(), any())
        } returns PromptBuilder.PromptBuildResult("user", emptyList())
        return GenerationEngine(gateway, promptBuilder)
    }

    private fun input() = buildGenerationInput(
        requestId = "req-transport-retry",
        messages = emptyList(),
        userHint = "",
        knowledgeBase = null,
        intentConfig = IntentConfig(),
        corrections = emptyMap(),
        correctionsRevision = 0,
        onlyThisRound = false,
        providerIdentity = config.toIdentity(),
        kbProfile = "",
        kbRevision = "rev",
        promptAssetHash = "assets"
    )

    private fun droppedPartial(): List<StreamEvent> = listOf(
        StreamEvent.Chunk(partial),
        StreamEvent.Error(ProviderFailure(ReplyFailureKind.Network), partial)
    )

    @Before
    fun silenceAndroidLog() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun releaseAndroidLog() = unmockkStatic(Log::class)

    @Test
    fun `a mid stream drop with partial text still spends the whole retry budget`() = runTest {
        val gateway = ScriptedGateway(config, List(1) { droppedPartial() })

        val events = engineWith(gateway).replyStream(input()).toList()

        assertEquals(
            "断流 + 半截正文也必须把尝试次数用完（旧写法见字就 break，只发一次请求）",
            AppConfig.GENERATE_MAX_ATTEMPTS, gateway.streamCalls
        )
        assertTrue(
            "截断 JSON 不该被交给解析器：这条路上必须一次都不调用，实到：${gateway.parseInputs}",
            gateway.parseInputs.isEmpty()
        )
        val done = events.filterIsInstance<ReplyCompleted>().single()
        assertTrue("次数用尽要收一条失败", done.result is GenerateResult.Error)
        assertEquals(
            "必须报传输错误，不能把网络故障误报成解析失败",
            ReplyFailureKind.Network.userMessage, (done.result as GenerateResult.Error).message
        )
        assertEquals(
            "每个 attempt 的正文只来自它自己：4 次尝试就该是 4 段一模一样的半截，不是脏拼接",
            AppConfig.GENERATE_MAX_ATTEMPTS,
            events.count { it is ReplyChunk && it.text == partial }
        )
        assertEquals(
            "重试前要把上一次的半成品从屏上清掉",
            AppConfig.GENERATE_MAX_ATTEMPTS - 1,
            events.count { it is ReplyChunkReset }
        )
    }

    @Test
    fun `a retry after a mid stream drop parses only the fresh attempt text`() = runTest {
        val gateway = ScriptedGateway(
            config,
            listOf(
                droppedPartial(),
                listOf(StreamEvent.Chunk(completeReply), StreamEvent.Complete(completeReply))
            )
        )

        val events = engineWith(gateway).replyStream(input()).toList()

        assertEquals("断流之后要再发一次", 2, gateway.streamCalls)
        assertEquals("重试那一轮正常交付", 1, events.count { it is ReplyCompleted && it.result is GenerateResult.Success })
        assertEquals(
            "解析器只能收到重试那一轮的全文：上一次的半截不许拼在前面",
            completeReply, gateway.parseInputs.single()
        )
        assertEquals("第一次断流后清屏一次", 1, events.count { it is ReplyChunkReset })
        assertEquals(
            "最终上屏的正文与解析输入是同一份",
            completeReply, events.filterIsInstance<ReplyChunk>().last().text
        )
    }

    @Test
    fun `a single healthy attempt still sends exactly one request`() = runTest {
        // 反例保护：重试判据不能把正常收尾也当成断流
        val gateway = ScriptedGateway(
            config,
            listOf(listOf(StreamEvent.Chunk(completeReply), StreamEvent.Complete(completeReply)))
        )

        val events = engineWith(gateway).replyStream(input()).toList()

        assertEquals("一次成功只发一条请求", 1, gateway.streamCalls)
        assertTrue(
            "正常收尾必须是成功",
            events.filterIsInstance<ReplyCompleted>().single().result is GenerateResult.Success
        )
        assertEquals("一次成功不改写 UI（不发多余 reset）", 0, events.count { it is ReplyChunkReset })
    }
}
