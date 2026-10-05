package com.lovebrain.app.domain

import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.model.CounselingChunk
import com.lovebrain.app.model.CounselingEnded
import com.lovebrain.app.model.CounselingFailed
import com.lovebrain.app.model.CounselingResult
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.buildGenerationInput
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三条生成链路的等待预算**是不是真的来自这张工单**——用虚拟时间问，不碰网络。
 *
 * 已有的那一族（`ProviderGenerateTimeoutTierTest`）钉的是档位换算、http 层与
 * 主动开场那一道的"到点收口"。这一族补它没量到的三件事，判法都是**双向夹逼**：
 * 让 Provider 在 50 秒出声、在 70 或 130 秒交卷，于是"到没到这一档"变成了
 * "那条增量收没收进来"这种能直接断言的事实，而不是读一个实现自己算出来的数。
 *
 *  - **谈心那一道以前一次都没被量过**，而它和主回复走的是同一条长回复链路：
 *    60 秒档必须在 50–70 秒之间收口（[counseling closes at one minute]），
 *    300 秒档必须活着越过 45 秒与 120 秒两个旧点（[counseling runs past both old caps]）。
 *  - **主回复在 60 秒档**没被量过：小档位必须真的把这一道也收在 60 秒，
 *    只会"变长"不等于接了档位。
 *  - **请求开始即冻结快照**：档位是在这条请求已经发出去之后才被改小的，
 *    正在跑的这一轮不许改口（[the tier a request started with is the tier it waits for]），
 *    下一轮才按新档位走（[the next request runs on the tier saved after it]）。
 *    反过来说，网关收到的一切实参必须就是发起时那一份——"把档位传下去"这一步本身也被判。
 *    这一对以前挂在锦囊那一道上量；锦囊按 第12节第1条 删了，判据原样搬到主动开场这一道，
 *    **没有**因为少了一条链路就少一条判据。
 *  - **一次主动发只发一条请求**（[one proactive run hands exactly one request to the gateway]）：
 *    第4节第2条 要的是把锦囊的找话题能力并进 prompt，而不是"先锦囊再开场"两次调用。
 */
class GenerationEngineTimeoutLanesTest {

    // ═══════════════════════ 假网关：一条按剧本出声的 Provider ═══════════════════════

    /** 第 [atMs] 毫秒（虚拟时间，从本次 attempt 开始算）到达的一条流事件 */
    private class Timed(val atMs: Long, val event: StreamEvent)

    private class ScriptedGateway(
        var liveConfig: ProviderRequestConfig,
        private val timeline: List<Timed>,
        /** 剧本走完之后再无声无息地挂着 = 服务端还在生成、一个字节都不吐（那种慢服务） */
        private val hangsAfterTimeline: Boolean
    ) : AiGateway {
        /** 引擎每一次真正交到网关手上的那份快照；元素为 null = 压根没把配置传下来 */
        val configsHandedIn = mutableListOf<ProviderRequestConfig?>()

        /** 请求已经发出去的那一刻要顺手做的事（=此后改设置才叫"生成中途改档位"） */
        var onStreamEntered: (() -> Unit)? = null

        override fun snapshotProviderConfig(): ProviderRequestConfig? = liveConfig

        override fun configForTicket(ticketId: String): ProviderRequestConfig? =
            liveConfig.takeIf { it.ticketId == ticketId }

        override fun generateStream(
            systemPrompt: String,
            userPrompt: String,
            thinkingOverride: Int?,
            thinkingShapeIndex: Int,
            config: ProviderRequestConfig?
        ): Flow<StreamEvent> = flow {
            configsHandedIn.add(config)
            onStreamEntered?.let { it(); onStreamEntered = null }
            var cursor = 0L
            for (step in timeline) {
                delay((step.atMs - cursor).coerceAtLeast(0L))
                cursor = step.atMs
                emit(step.event)
            }
            if (hangsAfterTimeline) awaitCancellation()
        }

        override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String =
            error("这一族判据不走非流式路径")

        override suspend fun generateRawWithMetadata(
            systemPrompt: String,
            userPrompt: String
        ): RawGenerationResult = error("这一族判据不走非流式路径")

        override fun parseReplyResponse(content: String): LoveBrainResponse =
            LoveBrainResponse(response = ReplySchemes(recommended = content))
    }

    private fun configAt(tier: GenerationTimeoutTier) = ProviderRequestConfig(
        ticketId = "A",
        apiKey = "key-A",
        baseUrl = "https://slow-provider.example.com/v1/chat",
        model = "model-a-flash",
        thinkingMode = 0,
        generateTimeoutMs = tier.millis
    )

    private fun engineWith(gateway: AiGateway): GenerationEngine {
        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        every { promptBuilder.buildSystemPrompt() } returns "system"
        every { promptBuilder.replyPromptAssetHash() } returns "assets"
        coEvery {
            promptBuilder.buildReplyUserPromptWithRefs(any(), any(), any(), any(), any(), any())
        } returns PromptBuilder.PromptBuildResult("user", emptyList())
        return GenerationEngine(gateway, promptBuilder)
    }

    private fun replyInput(config: ProviderRequestConfig) = buildGenerationInput(
        requestId = "req-lane",
        messages = emptyList(),
        userHint = "",
        knowledgeBase = null,
        intentConfig = IntentConfig(),
        corrections = emptyMap(),
        correctionsRevision = 0,
        onlyThisRound = false,
        aggressive = false,
        providerIdentity = config.toIdentity(),
        kbProfile = "",
        kbRevision = "rev",
        promptAssetHash = "assets"
    )

    private val kb = KnowledgeBase(name = "kb", displayName = "kb", stage = "暧昧期")

    /** 剧本里的增量：前 50 秒只闭合第一条开场，70 秒才闭合第二条（主动发那一道边流边出 options） */
    private val optionChunkOne = """{"options":[{"text":"早上打个招呼","angle":"轻话题"},"""
    private val optionChunkTwo = """{"text":"晚上聊十分钟","angle":"推进"}]}"""

    private fun timed(atMs: Long, text: String) = Timed(atMs, StreamEvent.Chunk(text))

    // ═══════════════════════ 主回复在 60 秒档 ═══════════════════════

    @Test
    fun `the main reply lane waits the whole minute of a 60 second ticket, not the old 45 seconds`() = runTest {
        val config = configAt(GenerationTimeoutTier.SEC_60)
        val gateway = ScriptedGateway(
            config,
            listOf(
                timed(50_000L, "这条正文在五十秒才到达"),
                Timed(51_000L, StreamEvent.Complete("这条正文在五十秒才到达"))
            ),
            hangsAfterTimeline = false
        )

        val events = engineWith(gateway).replyStream(replyInput(config)).toList()

        assertTrue(
            "50 秒那条正文没被收进来 ⇒ 这一道还在吃被删掉的固定 45 秒：" + events,
            events.any { it is ReplyChunk && it.text == "这条正文在五十秒才到达" }
        )
        val done = events.filterIsInstance<ReplyCompleted>().single()
        assertTrue(
            "越点之后这一轮必须正常收口，而不是重试到失败：" + done.result,
            done.result is GenerateResult.Success
        )
        assertEquals("一次成功只发了一次请求，且交下去的就是这张工单的快照", 1, gateway.configsHandedIn.size)
        assertEquals(config, gateway.configsHandedIn.single())
    }

    @Test
    fun `a 60 second ticket closes the reply lane at one minute instead of keeping the old two minute default`() = runTest {
        val config = configAt(GenerationTimeoutTier.SEC_60)
        val gateway = ScriptedGateway(
            config,
            listOf(
                timed(50_000L, "五十秒的第一句"),
                timed(100_000L, "一百秒才来的第二句"),
                Timed(110_000L, StreamEvent.Complete("五十秒的第一句一百秒才来的第二句"))
            ),
            hangsAfterTimeline = true
        )

        val events = engineWith(gateway).replyStream(replyInput(config)).toList()

        assertTrue("这一道连 50 秒都等不到 ⇒ 小档位被写得更小了：" + events,
            events.any { it is ReplyChunk && it.text == "五十秒的第一句" })
        assertTrue(
            "100 秒那句居然也收了进来 ⇒ 主回复仍然按旧的 120 秒默认档在等，档位没落到这一道：" + events,
            events.none { it is ReplyChunk && it.text == "一百秒才来的第二句" }
        )
        val done = events.filterIsInstance<ReplyCompleted>().single()
        assertTrue("到档必须收口成一条失败，接档位不等于把它变成无限等待：" + done.result,
            done.result is GenerateResult.Error)
    }

    // ═══════════════════════ 谈心：这一道以前一次都没被量过 ═══════════════════════

    @Test
    fun `the counseling lane on a 300 second ticket runs past both old caps and still delivers its result`() = runTest {
        val config = configAt(GenerationTimeoutTier.SEC_300)
        val gateway = ScriptedGateway(
            config,
            listOf(
                timed(50_000L, "你这次忍住了，"),
                timed(130_000L, "这本身就是推进。"),
                Timed(131_000L, StreamEvent.Complete("你这次忍住了，这本身就是推进。"))
            ),
            hangsAfterTimeline = false
        )

        val events = engineWith(gateway)
            .counselingStream("req-counsel", "我今天没发消息", kb)
            .toList()

        assertTrue("谈心在 131 秒之前就被切了 ⇒ 它没接这张工单的档位：" + events,
            events.none { it is CounselingFailed })
        val result = events.filterIsInstance<CounselingResult>().single()
        assertEquals("你这次忍住了，这本身就是推进。", result.replyText)
        assertEquals("谈心这一道一次都没重发请求", 1, gateway.configsHandedIn.size)
        assertEquals(config, gateway.configsHandedIn.single())
    }

    @Test
    fun `the counseling lane on a 60 second ticket closes at one minute and still reports the end`() = runTest {
        val config = configAt(GenerationTimeoutTier.SEC_60)
        val gateway = ScriptedGateway(
            config,
            listOf(
                timed(50_000L, "说到一半的话"),
                Timed(70_000L, StreamEvent.Complete("说到一半的话，后面那句不该被等到"))
            ),
            hangsAfterTimeline = true
        )

        val events = engineWith(gateway)
            .counselingStream("req-counsel", "我今天没发消息", kb)
            .toList()

        assertTrue("50 秒那句增量该还在 ⇒ 这一道被切得比 60 秒还短：" + events,
            events.any { it is CounselingChunk && it.text == "说到一半的话" })
        assertTrue("70 秒交卷居然成功了 ⇒ 谈心还挂在旧的 120 秒上：" + events,
            events.none { it is CounselingResult })
        assertTrue("到档要收一条失败：" + events, events.any { it is CounselingFailed })
        assertEquals("收尾事件恰好一条，且排在失败之后", 1, events.filterIsInstance<CounselingEnded>().size)
        assertTrue("流必须收口，不能挂着：" + events, events.last() is CounselingEnded)
    }

    // ═══════════════════════ 请求开始即冻结快照 ═══════════════════════

    @Test
    fun `the tier a request started with is the tier it waits for even if the setting changes mid flight`() = runTest {
        val startedWith = configAt(GenerationTimeoutTier.SEC_300)
        val savedDuring = configAt(GenerationTimeoutTier.SEC_60)
        val gateway = ScriptedGateway(
            startedWith,
            listOf(timed(50_000L, optionChunkOne), timed(70_000L, optionChunkTwo)),
            hangsAfterTimeline = true
        )
        // 用户在请求已经发出去之后把档位从 300 改到 60：这正是设置页与在飞请求会撞上的那一刻
        gateway.onStreamEntered = { gateway.liveConfig = savedDuring }

        val events = engineWith(gateway).proactiveStream("req-pro", "", kb, emptyList()).toList()

        assertEquals("交给网关的必须是发起时冻结的那一份，而不是改过之后的", startedWith, gateway.configsHandedIn.single())
        assertTrue(
            "70 秒那条开场没到达 ⇒ 正在跑的这一轮被中途改小的档位牵着走了：" + events,
            events.filterIsInstance<ProactiveOptions>().lastOrNull()?.options?.size == 2
        )
    }

    @Test
    fun `the next request after a saved tier change runs on the new tier`() = runTest {
        val slow = configAt(GenerationTimeoutTier.SEC_300)
        val fast = configAt(GenerationTimeoutTier.SEC_60)
        val gateway = ScriptedGateway(
            slow,
            listOf(timed(50_000L, optionChunkOne), timed(70_000L, optionChunkTwo)),
            hangsAfterTimeline = true
        )
        val engine = engineWith(gateway)

        val firstRound = engine.proactiveStream("req-1", "", kb, emptyList()).toList()
        assertTrue("前置条件：300 秒档下 70 秒那条该到达：" + firstRound,
            firstRound.filterIsInstance<ProactiveOptions>().lastOrNull()?.options?.size == 2)

        gateway.liveConfig = fast   // 这一轮结束之后才改设置

        val secondRound = engine.proactiveStream("req-2", "", kb, emptyList()).toList()

        assertEquals("下一轮该按新档位发请求", fast, gateway.configsHandedIn.last())
        assertTrue(
            "新档位没生效，第二条请求还按 300 秒等 ⇒ 设置页那一格只写盘不生效：" + secondRound,
            secondRound.none { it is ProactiveOptions && it.options.size == 2 }
        )
        // 到点之后这一轮仍然要收口（"还有内容可捡"不等于它还挂着）
        assertEquals(
            "按 60 秒收口 ⇒ 只捡得到 50 秒那一条",
            1,
            secondRound.filterIsInstance<ProactiveOptions>().last().options.size
        )
        assertTrue("收口要落在 Ended 上，不然面板停在转圈：" + secondRound,
            secondRound.last() is ProactiveEnded)
    }

    /**
     * 第4节第2条：一次主动发 = **一条**生成请求。锦囊那条独立请求流已经删了，
     * 它"找话题/切入点"的能力是并进这一道的 prompt 里的，不是在这里串两次模型调用。
     *
     * 反例：把合并做成"先 suggest 再 proactive"两次 `generateStream`（configsHandedIn 变 2），
     * 或者失败重试时又开一条新的 proactive 请求。
     */
    @Test
    fun `one proactive run hands exactly one request to the gateway`() = runTest {
        val config = configAt(GenerationTimeoutTier.SEC_60)
        val gateway = ScriptedGateway(
            config,
            listOf(
                timed(1_000L, optionChunkOne),
                Timed(2_000L, StreamEvent.Complete(optionChunkOne + optionChunkTwo))
            ),
            hangsAfterTimeline = false
        )

        val events = engineWith(gateway).proactiveStream("req-once", "", kb, emptyList()).toList()

        assertEquals("一次点击一次请求：" + gateway.configsHandedIn, 1, gateway.configsHandedIn.size)
        assertTrue(
            "结果必须在这一条流里就出得来，否则『只发一次』是靠砍掉内容换来的：" + events,
            events.any { it is ProactiveOptions && it.options.isNotEmpty() }
        )
    }
}
