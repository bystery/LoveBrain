package com.lovebrain.app.domain

import android.util.Log
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.feature.proactive.ProactiveStore
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveEvent
import com.lovebrain.app.model.ProactiveFailed
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ProactiveStarted
import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.StreamEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 第4节第1条第5条 第一条（失败后就地短提示、可再生成，不许把失败伪装成"没有结果"）的**跨层**那一格。
 *
 * [ProactiveResultRetentionTest] 判的是**成功**收尾那半（结束不许把结果换走）；这一族判失败的
 * 那半，而且判的是三条**真实事件顺序**——事件由生产 [GenerationEngine.proactiveStream] 原样发出，
 * 测试自己不当编排者：
 *
 *  1. **到点收口**（慢服务吐完第一条就再无字节）⇒ `Options(1 条) → Failed → Ended`。
 *     坏实现证人：`ProactiveFailed -> copy(options = emptyList(), error = message)`
 *     （"失败了就回到干净状态"）⇒ 用户眼前那一条已经看见过的开场被撤走。
 *  2. **半途断流**（Provider 直接给 Error，尾巴上还带着已收到的正文）⇒
 *     `Failed → Options(从 partialText 补出来的那一条) → Ended`：失败文案**先到**、半成品**后补**。
 *     这一条最容易被写反，两个方向都有坏实现：Failed 里清 options（同上），
 *     或 `ProactiveOptions` 顺手 `error = null`（拿到内容就把提示擦掉）。
 *  3. **一句可用的都没生成出来**（模型交回一段散文，解析不出任何一条开场）⇒ 引擎只发
 *     `Started → Ended`，**没有任何 Failed**。"这一轮跑完了但什么都没有"只有 store 这一层能
 *     替用户认下来，于是这里要的是 [com.lovebrain.app.feature.proactive.ProactiveStore.closeRun]
 *     那句就地短提示。坏实现证人：`ProactiveEnded -> Unit`（这一格之前的写法）⇒ error 恒为 null，
 *     面板落到"还没有开场，点击下方「生成开场」"那一档，**军师没交付**被说成**用户还没生成**——
 *     这就是"把失败伪装成没有结果"本身。
 *
 * 三格再共用两条判据，因为它们是  的红线，不因失败而改变：**不发** `ExitedProactiveMode`
 * （一轮生成的结束不是"用户要看普通回复"）、模式留在 PROACTIVE——那颗「生成开场」继续挂着，
 * "可再生成"就是这一屏的默认出口，store 不必再加一颗重试按钮（面板那一格也不给，见 第6节第3条）。
 *
 * 档位换算与 http 超时那部分归 `GenerationEngineTimeoutLanesTest` /
 * `ProviderGenerateTimeoutTierTest`，这里只借它们的剧本手法，不重复量。
 */
class ProactiveFailureKeepsPartialBodyTest {

    /** 只按剧本出声的假网关；[hangsAfterScript] = 剧本走完就再无字节（那种慢服务） */
    private class ScriptedGateway(
        private val script: List<StreamEvent>,
        private val config: ProviderRequestConfig,
        private val hangsAfterScript: Boolean = false
    ) : AiGateway {
        override fun snapshotProviderConfig(): ProviderRequestConfig = config
        override fun configForTicket(ticketId: String): ProviderRequestConfig? = config
        override fun generateStream(
            systemPrompt: String,
            userPrompt: String,
            thinkingOverride: Int?,
            thinkingShapeIndex: Int,
            config: ProviderRequestConfig?
        ): Flow<StreamEvent> = flow {
            script.forEach { emit(it) }
            if (hangsAfterScript) awaitCancellation()
        }
        override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String =
            error("这一族不走非流式")
        override suspend fun generateRawWithMetadata(
            systemPrompt: String,
            userPrompt: String
        ): RawGenerationResult = error("这一族不走非流式")
        override fun parseReplyResponse(content: String): LoveBrainResponse =
            LoveBrainResponse(response = ReplySchemes(recommended = content))
    }

    private val config = ProviderRequestConfig(
        ticketId = "A",
        apiKey = "key-A",
        baseUrl = "https://example.com/v1/chat",
        model = "model-a",
        thinkingMode = 0,
        // 小预算 + runTest 的虚拟时钟：到点收口等的是这一刻，不是真实秒数
        generateTimeoutMs = 5_000L
    )

    private val kb = KnowledgeBase(name = "kb", displayName = "kb", stage = "暧昧期")

    /** 这一段里**已经闭合**的那条开场：增量解析器当场就认得出，所以它真的先上了屏 */
    private val deliveredOptionChunk = """{"options":[{"text":"早上打个招呼","angle":"轻话题"},"""

    /** 断流尾巴上那一整段：闭合的一条开场，但增量解析器当时一个字节都没收到 */
    private val tailWithOneOption = """{"options":[{"text":"早上打个招呼","angle":"轻话题"}]}"""

    private fun engineWith(gateway: AiGateway): GenerationEngine {
        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        every { promptBuilder.buildProactiveSystemPrompt() } returns "proactive system"
        return GenerationEngine(gateway, promptBuilder)
    }

    /**
     * 到点收口那一条链上，引擎会 `L.w("PROACTIVE timeout …")` 记一笔（只记长度/类型之外的
     * 诊断字段，没有聊天内容）。`android.util.Log` 在 JVM 单测里是 stub，不拦它会 "Stub!" 抛出来，
     * 与本仓 `ProviderGenerateTimeoutTierTest` 同一判法：把这条日志静音，不改变被量的事。
     */
    @Before
    fun silenceAndroidLog() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun releaseAndroidLog() = unmockkStatic(Log::class)

    /** 一次生产链路跑完的三样读数：store、它发出去的效果、引擎实际发出的事件 */
    private data class Outcome(
        val store: ProactiveStore,
        val effects: List<ProactiveStore.Effect>,
        val events: List<ProactiveEvent>
    )

    /** 生产链路端到端：Engine 的事件流原样喂进生产那颗 store（租约按"仍在管"判） */
    private suspend fun runProactive(requestId: String, gateway: AiGateway): Outcome {
        val effects = mutableListOf<ProactiveStore.Effect>()
        val store = ProactiveStore(isCurrentRequest = { true }, onEffect = { effects.add(it) })
        store.accept(ProactiveStore.Intent.EnterProactive)
        val events = engineWith(gateway).proactiveStream(requestId, "", kb, emptyList()).toList()
        events.forEach { store.accept(ProactiveStore.Intent.Apply(it)) }
        return Outcome(store, effects, events)
    }

    /** 三条失败路径共用的那一半判据：失败也不把用户请出主动发入口、不换走结果区 */
    private fun Outcome.staysInPlace(what: String) {
        assertTrue(
            "$what：结果区归位只能由用户明确动作发出，实到效果：$effects",
            effects.none { it is ProactiveStore.Effect.ExitedProactiveMode }
        )
        assertEquals(
            "$what：输入区留在主动发，那颗「生成开场」才挂得住",
            ComposerMode.PROACTIVE, store.composerMode
        )
    }

    /**
     * 顺序一：先到的一条已经上屏，到点失败只能说"这次没生成完"，不能把它撤走。
     */
    @Test
    fun `a timeout after the first opener leaves that line on screen next to the short notice`() = runTest {
        val out = runProactive(
            "req-timeout",
            ScriptedGateway(listOf(StreamEvent.Chunk(deliveredOptionChunk)), config, hangsAfterScript = true)
        )

        assertTrue("这一轮必须是**因为到点而失败**才收的口，实到事件：" + out.events,
            out.events.any { it is ProactiveFailed })
        assertEquals(
            "已经流出的那一条开场必须还在屏幕上（失败不许清屏），实到：" + out.store.currentOptions,
            listOf("早上打个招呼"), out.store.currentOptions.map(ProactiveOption::text)
        )
        val notice = out.store.uiState.value.error
        assertNotNull("失败要就地留一句短提示，否则这一屏和『还没生成』长得一样：" + out.store.uiState.value, notice)
        assertTrue("提示要短：一句话说清这次没成、还能再来一次：" + notice, notice!!.isNotBlank() && notice.length <= 40)
        out.staysInPlace("到点收口")
    }

    /**
     * 顺序二：失败文案先到、半成品后补——后补那一批不许把提示擦掉，先到的提示也不许被当成清屏信号。
     */
    @Test
    fun `a mid stream drop keeps both the notice and the opener recovered from the tail`() = runTest {
        val out = runProactive(
            "req-drop",
            ScriptedGateway(
                listOf(
                    StreamEvent.Error(
                        failure = ProviderFailure(ReplyFailureKind.Network),
                        partialText = tailWithOneOption
                    )
                ),
                config
            )
        )

        assertTrue("引擎这一轮确实发了 Failed：" + out.events, out.events.any { it is ProactiveFailed })
        assertEquals(
            "partialText 里那条闭合的开场要补上屏，实到：" + out.store.currentOptions,
            listOf("早上打个招呼"), out.store.currentOptions.map(ProactiveOption::text)
        )
        assertNotNull(
            "补上内容的同一轮里，失败提示也得留着（拿到结果就顺手清 error 是把失败藏起来）：" +
                out.store.uiState.value,
            out.store.uiState.value.error
        )
        assertTrue("收尾要落在 Ended 上，不然面板一直转圈：" + out.events, out.events.last() is ProactiveEnded)
        out.staysInPlace("半途断流")
    }

    /**
     * 顺序三：一个能用的都没生成出来 ⇒ store 自己开口。
     *
     * 引擎这一段**不会**发 Failed（模型正常交卷，只是交的东西里解析不出开场），
     * 所以"这一轮失败了"这句话只能由 store 的收尾分支补——这一格就是那条分支的判据。
     */
    @Test
    fun `a run that delivers nothing says so in place instead of looking like the user never generated`() =
        runTest {
            val prose = "今天天气不错，我觉得可以直接约她出来坐坐，这样比较自然。"
            val out = runProactive(
                "req-nothing",
                ScriptedGateway(listOf(StreamEvent.Chunk(prose), StreamEvent.Complete(prose)), config)
            )

            assertTrue("这一轮真的跑完了：" + out.events, out.events.last() is ProactiveEnded)
            assertTrue(
                "引擎既没发 Failed 也没交付开场 ⇒ 屏幕上这句话只可能是 store 补的：" + out.events,
                out.events.none { it is ProactiveFailed } &&
                    out.events.none { it is ProactiveOptions && it.options.isNotEmpty() }
            )
            assertEquals("确实没有可用开场", emptyList<String>(), out.store.currentOptions.map(ProactiveOption::text))
            val notice = out.store.uiState.value.error
            assertNotNull(
                "什么都没交付的一轮必须留下就地短提示；error 为空就等于把失败说成『还没生成』。" +
                    "实到状态：" + out.store.uiState.value,
                notice
            )
            assertTrue("短提示要说清这次没成、可以再点一次：" + notice, notice!!.isNotBlank() && notice.length <= 40)
            out.staysInPlace("零交付收尾")
        }

    /**
     * 可再生成：那句就地提示不许粘在屏幕上——新一轮一开始就清干净、由 loading 接手
     * （第4节第1条第1条），否则用户会以为上一次失败还没结束，那颗「生成开场」看起来像坏了。
     */
    @Test
    fun `starting over clears the previous in place notice`() = runTest {
        val prose = "还是一段散文，一条开场都解析不出来。"
        val out = runProactive(
            "req-again",
            ScriptedGateway(listOf(StreamEvent.Chunk(prose), StreamEvent.Complete(prose)), config)
        )
        assertNotNull("先确认真的留下了提示", out.store.uiState.value.error)

        out.store.accept(ProactiveStore.Intent.Apply(ProactiveStarted("req-again-2")))

        assertNull("新一轮开始要把的失败提示一起清掉", out.store.uiState.value.error)
        assertTrue(out.store.currentOptions.isEmpty())
        assertEquals("清的是错误与旧结果，不是把用户请出主动发入口", ComposerMode.PROACTIVE, out.store.composerMode)
    }
}
