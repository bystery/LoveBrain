package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.feature.proactive.ProactiveStore
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.StreamEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  的**跨层**那一格：Engine 真的按这个顺序发事件、Store 真的收下并留住。
 *
 * `ProactiveStoreTest` 判的是 store 自己的归约；这一族判的是两条链接起来之后
 * 用户看得见的那件事。它要挡住的坏实现是"两边各自都自洽、接起来就丢结果"：
 *
 *  1. Engine 把 `ProactiveEnded` 排在最后一批 options **之前** —— 那 store 后面收到的
 *     空清单会把屏幕清空（这一格用 `last() is ProactiveEnded` + 末批 options 非空夹住它）。
 *  2. Store 收到 `ProactiveEnded` 就退回普通回复（**这条就是线上那次"生成完结果就没了"**：
 *     结果对象还活着，视图却被换走了）。这一格把生产那一条流原样喂进生产那颗 store，
 *     断言收尾之后 options 还是那两条、模式还在 PROACTIVE、而且**一条
 *     `ExitedProactiveMode` 都没发过**——VM 收到那条效果才会把 ResultMode 改成 REPLY。
 *
 * 两格都只用生产实现，测试自己不当 prompt 组装者、也不当 provider。
 */
class ProactiveResultRetentionTest {

    /** 一条只按剧本吐字的假网关：不联网，也不碰档位换算（那一族在 TimeoutLanes 里） */
    private class ScriptedGateway(
        private val chunks: List<String>,
        private val config: ProviderRequestConfig
    ) : AiGateway {
        /** 网关被调了几次 = 这一次主动发到底发了几条生成请求 */
        val calls = mutableListOf<Pair<String, String>>()

        override fun snapshotProviderConfig(): ProviderRequestConfig = config
        override fun configForTicket(ticketId: String): ProviderRequestConfig? = config
        override fun generateStream(
            systemPrompt: String,
            userPrompt: String,
            thinkingOverride: Int?,
            thinkingShapeIndex: Int,
            config: ProviderRequestConfig?
        ): Flow<StreamEvent> = flow {
            calls += systemPrompt to userPrompt
            chunks.forEach { emit(StreamEvent.Chunk(it)) }
            emit(StreamEvent.Complete(chunks.joinToString("")))
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
        generateTimeoutMs = 5_000L
    )

    private val kb = KnowledgeBase(name = "kb", displayName = "kb", stage = "暧昧期")

    /** 两条**内容方向不同**的开场：正是主动发该交付的东西 */
    private val firstOption = """{"options":[{"text":"今天路过那家你说想去的面馆","angle":"接她上次的话头"},"""
    private val secondOption = """{"text":"这周末有空吗，想找个地方坐坐","angle":"直接约时间"}]}"""

    private fun engineWith(gateway: AiGateway): GenerationEngine {
        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        every { promptBuilder.buildProactiveSystemPrompt() } returns "proactive system"
        return GenerationEngine(gateway, promptBuilder)
    }

    /** 生产链路端到端：Engine 的事件流原样喂进生产那颗 store。 */
    @Test
    fun `a finished proactive run leaves the openers on screen and asks for no result-area switch`() = runTest {
        val gateway = ScriptedGateway(listOf(firstOption, secondOption), config)
        val effects = mutableListOf<ProactiveStore.Effect>()
        val observing = ProactiveStore(isCurrentRequest = { true }, onEffect = { effects.add(it) })

        // 用户点了"生成开场"：VM 先进模式，再收流
        observing.accept(ProactiveStore.Intent.EnterProactive)
        val events = engineWith(gateway).proactiveStream("req-1", "", kb, emptyList()).toList()
        events.forEach { observing.accept(ProactiveStore.Intent.Apply(it)) }

        assertTrue("剧本没跑到 store 这一侧：一条事件都没收到：" + events, events.isNotEmpty())
        assertTrue("收尾事件必须是 Ended，否则面板一直转圈：" + events, events.last() is ProactiveEnded)

        val delivered = events.filterIsInstance<ProactiveOptions>().last().options
        assertEquals("生成完该有两条能发的开场，实到：" + delivered, 2, delivered.size)
        assertEquals(
            "流结束后 store 里留着的必须是**同一批**开场——用户点复制拿到的就是它：" +
                observing.currentOptions,
            delivered.map { it.text },
            observing.currentOptions.map { it.text }
        )
        assertEquals(
            "生成结束不等于用户要看普通回复：一条 ResultMode 归位通知都不许发：" + effects,
            // 首字耗时那条效果是**应该有**的（跨 feature 统计量），这里判的是"归位"这一类。
            // 反例：把 ProactiveEnded 写回"有结果就 exitProactive()"，这里立刻多出一次 ExitedProactiveMode。
            0,
            effects.count { it is ProactiveStore.Effect.ExitedProactiveMode }
        )
        assertTrue(
            "首字耗时照常上报（证明这一轮真的走完了 store，而不是被归属判断整条丢掉）：" + effects,
            effects.any { it is ProactiveStore.Effect.FirstTokenObserved }
        )
        assertEquals("输入区也留在主动发那一侧", ComposerMode.PROACTIVE, observing.composerMode)
        assertEquals(
            "一次主动发只碰网关一次—— 不许『先锦囊再开场』那种双调用",
            1, gateway.calls.size
        )
        assertEquals(
            "递给网关的 system 必须是主动开场那一套引擎（换成别的引擎这一格会拿到空串）",
            "proactive system", gateway.calls.single().first
        )
    }

    /**
     * 反向证人：把这一轮的**结果内容**换成交付物本身，断言它确实是可以直接发出去的一句话。
     *
     * 反例：解析只留 JSON 原文 / 留下空白 text 的条目 —— 那"留在屏幕上"留的是一堆没法用的东西。
     */
    @Test
    fun `what stays on the screen is copy-ready text, not raw json`() = runTest {
        val gateway = ScriptedGateway(listOf(firstOption, secondOption), config)

        val events = engineWith(gateway).proactiveStream("req-2", "", kb, emptyList()).toList()
        val delivered = events.filterIsInstance<ProactiveOptions>().last().options

        assertEquals(2, delivered.size)
        assertTrue(
            "每条 text 都得是能直接发出去的一句话，实到：" + delivered.map { it.text },
            delivered.all { it.text.isNotBlank() && !it.text.contains('{') && !it.text.contains('"') }
        )
        assertEquals(
            "两条之间要有真的内容方向区别，不是同一句换语气词",
            listOf("今天路过那家你说想去的面馆", "这周末有空吗，想找个地方坐坐"),
            delivered.map { it.text }
        )
        assertTrue(
            "角度说明也在（并入锦囊后『切入点』这件事靠它承载）",
            delivered.all { it.angle.isNotBlank() }
        )
    }

    /** 空文这条目不许被当成"留在屏幕上的结果"。 */
    @Test
    fun `a blank option never reaches the store as a deliverable`() = runTest {
        val gateway = ScriptedGateway(
            listOf("""{"options":[{"text":"","angle":"空的一条"},{"text":"能发的那句","angle":"切入"},"""),
            config
        )

        val events = engineWith(gateway).proactiveStream("req-3", "", kb, emptyList()).toList()
        val delivered = events.filterIsInstance<ProactiveOptions>().last().options

        assertEquals(
            "空白 text 不该占一个格子：" + delivered.map(ProactiveOption::text),
            listOf("能发的那句"), delivered.map { it.text }
        )
    }
}
