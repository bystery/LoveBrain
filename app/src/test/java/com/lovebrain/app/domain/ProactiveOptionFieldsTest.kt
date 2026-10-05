package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.AiGateway
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原始第 18 条 / §9.2：主动候选扩出 `timing`/`hold_back`/`prepare` 三颗字段后的**解析闭环**（机械可复现）。
 *
 * 关键风险是 F7 点名的 `jsonLenient`（`GenerationEngine.kt:56-60`）`ignoreUnknownKeys=true`：
 * **只改资产不改 schema，新字段会被静默丢而不是报错**。这几格正反夹住：
 * - 增量流式（`:470-477`，跨 chunk 边流边出）能带出新字段 ⇒ 模型/schema 真的接住了；
 * - 终态兜底（`:557-564`，只 Complete 无 chunk 那一路）也带出新字段 ⇒ 两条解析口共用同一 model；
 * - 未知键（"mystery"）仍被 ignoreUnknownKeys 容忍、不影响已知字段解码 ⇒ 扩字段没把兼容换成脆断；
 * - 旧 JSON（只有 text/angle）照旧解析、新字段回默认空串 ⇒ 不复活即破坏历史输出；
 * - `text` 仍只是可发送正文，策略文字全在别的字段 ⇒ 面板 `onCopy(opt.text)`（禁区，第 954 行）天然不被弄脏。
 */
class ProactiveOptionFieldsTest {

    private class ScriptedGateway(
        private val script: List<StreamEvent>,
        private val config: ProviderRequestConfig
    ) : AiGateway {
        override fun snapshotProviderConfig(): ProviderRequestConfig = config
        override fun configForTicket(ticketId: String): ProviderRequestConfig? = config
        override fun generateStream(
            systemPrompt: String,
            userPrompt: String,
            thinkingOverride: Int?,
            thinkingShapeIndex: Int,
            config: ProviderRequestConfig?
        ): Flow<StreamEvent> = flow { script.forEach { emit(it) } }
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
        ticketId = "F", apiKey = "key", baseUrl = "https://example.com/v1",
        model = "m", thinkingMode = 0, generateTimeoutMs = 5_000L
    )
    private val kb = KnowledgeBase(name = "kb", displayName = "kb", stage = "暧昧期")

    private fun engineWith(gateway: AiGateway): GenerationEngine {
        val builder = mockk<PromptBuilder>(relaxed = true)
        every { builder.buildProactiveSystemPrompt() } returns "proactive system"
        return GenerationEngine(gateway, builder)
    }

    private suspend fun deliver(script: List<StreamEvent>): List<ProactiveOption> {
        val events = engineWith(ScriptedGateway(script, config))
            .proactiveStream("req", "", kb, emptyList()).toList()
        assertTrue("收尾必须是 Ended，否则剧本没跑完：" + events, events.last() is ProactiveEnded)
        return events.filterIsInstance<ProactiveOptions>().lastOrNull()?.options ?: emptyList()
    }

    /** 增量流式带出新字段，且 text 仍是干净的可发送正文 */
    @Test
    fun `streaming carries the new strategy fields without polluting the copyable text`() = runTest {
        val c1 = """{"options":[{"text":"周六下午那展差不多收尾了","angle":"回调上次看的展",""" +
            """"timing":"她今天说加班到十点，明晚发更稳","hold_back":"她还在忙就先别追第二条",""" +
            """"prepare":"先确认自己周六真有半天空"}"""
        val c2 = """,{"text":"路过你说的那家猫咖","angle":"日常分享","timing":"","hold_back":"","prepare":""}]}"""
        val options = deliver(
            listOf(StreamEvent.Chunk(c1), StreamEvent.Chunk(c2), StreamEvent.Complete(c1 + c2))
        )

        assertEquals("两条候选都该出来：" + options, 2, options.size)
        val first = options[0]
        assertEquals("text 只装可发送正文（复制即发这句话）", "周六下午那展差不多收尾了", first.text)
        assertFalse("正文不许混进时机说明：" + first.text, first.text.contains("加班到十点"))
        assertTrue("timing 不再被静默丢：" + first.timing, first.timing.contains("加班到十点"))
        assertTrue("hold_back 承载先别发的条件：" + first.holdBack, first.holdBack.contains("还在忙"))
        assertTrue("prepare 承载要准备的素材：" + first.prepare, first.prepare.contains("半天"))
        assertEquals("留空字段就回空串，不硬造内容", "", options[1].holdBack)
    }

    /** 未知键仍被 ignoreUnknownKeys 容忍，同一条里已知新字段照样解码——扩字段没把兼容换成脆断 */
    @Test
    fun `an unknown key is still tolerated while known fields decode`() = runTest {
        val json = """{"options":[{"text":"早","angle":"问候","timing":"现在合适","mystery":"忽略我"}]}"""
        val options = deliver(listOf(StreamEvent.Chunk(json), StreamEvent.Complete(json)))
        assertEquals(1, options.size)
        assertTrue("已知 timing 仍解出：" + options[0].timing, options[0].timing.contains("现在合适"))
    }

    /** 旧输出（只有 text/angle）照旧解析，三颗新字段回默认空串 */
    @Test
    fun `legacy json with only text and angle still parses`() = runTest {
        val json = """{"options":[{"text":"早上好","angle":"轻问候"}]}"""
        val options = deliver(listOf(StreamEvent.Chunk(json), StreamEvent.Complete(json)))
        assertEquals(1, options.size)
        assertEquals("早上好", options[0].text)
        assertEquals("旧 JSON 缺 timing → 默认空串", "", options[0].timing)
        assertEquals("旧 JSON 缺 hold_back → 默认空串", "", options[0].holdBack)
        assertEquals("旧 JSON 缺 prepare → 默认空串", "", options[0].prepare)
    }

    /** 终态兜底那一口（无 chunk、只有 Complete）也共用同一 model，新字段同样带得出来 */
    @Test
    fun `terminal fallback parse path carries the new fields too`() = runTest {
        val json = """{"options":[{"text":"刚看到你发的照片","angle":"回应她的分享",""" +
            """"timing":"她刚发动态，现在接最自然","hold_back":"","prepare":"翻出你上周同类照片"}]}"""
        val options = deliver(listOf(StreamEvent.Complete(json)))
        assertEquals("终态兜底应解析出一条：" + options, 1, options.size)
        assertTrue("兜底路径也带出 timing：" + options[0].timing, options[0].timing.contains("刚发动态"))
        assertTrue("兜底路径也带出 prepare：" + options[0].prepare, options[0].prepare.contains("同类照片"))
    }
}
