package com.lovebrain.app.domain.champion

import com.lovebrain.app.data.FixedClockZone
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.DialogueSpeaker
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.StreamUsage
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * 冠军侧（影子 prompt / 身份文件 / 测试时钟）的时间串不许随设备语言变。
 *
 * 主路径已经钉了 Locale.ROOT：提示词里的「当前时间」必须是公历 + 拉丁数字。
 * 冠军侧原先走 `SimpleDateFormat(..., Locale.getDefault())`：th_TH 给出佛历 2569、
 * ar_SA 给出本地数字，于是同一条消息在影子 prompt 与主 prompt 里是两个年份——
 * 研究侧对拍会把这当成输入差异。这里在**钉死的时区 + 切换 Locale** 下判产出字节，
 * 不判源码 import 了什么。
 */
class ChampionTimeLocaleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var savedZone: TimeZone? = null
    private var savedLocale: Locale? = null

    @Before
    fun setUp() {
        savedZone = FixedClockZone.install()
    }

    @After
    fun tearDown() {
        savedLocale?.let { Locale.setDefault(it) }
        savedLocale = null
        FixedClockZone.restore(savedZone)
        savedZone = null
    }

    /** 2026-09-24T01:00:00Z = Asia/Shanghai 09:00（FixedClockZone 钉的时区） */
    private val epochMs = 1_790_211_600_000L

    private fun under(tag: String, block: () -> Unit) {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(tag))
        try {
            block()
        } finally {
            Locale.setDefault(savedLocale!!)
        }
    }

    private class CapturingGateway : AiGateway {
        var prompt: String? = null

        override fun snapshotProviderConfig(): ProviderRequestConfig? = null
        override fun configForTicket(ticketId: String): ProviderRequestConfig? = null
        override fun generateStream(
            systemPrompt: String, userPrompt: String, thinkingOverride: Int?,
            thinkingShapeIndex: Int, config: ProviderRequestConfig?
        ): Flow<StreamEvent> {
            prompt = userPrompt
            return flowOf(
                StreamEvent.Chunk("{}"),
                StreamEvent.Complete("{\"response\":{\"recommended\":\"好\"}}", StreamUsage(1, 1))
            )
        }
        override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String = ""
        override suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String): RawGenerationResult =
            throw UnsupportedOperationException()
        override fun parseReplyResponse(content: String): LoveBrainResponse =
            throw UnsupportedOperationException()
    }

    private fun engine(dir: File, clock: FixedClock, gateway: AiGateway): ChampionEngine =
        ChampionEngine(
            gateway = gateway,
            eventStore = ChampionEventStore(FileChampionEventStorage(File(dir, "events"))),
            assembler = ChampionPromptAssembler(
                // 与生产同一条接线：PromptBuilder.buildTimestampPrompt 背后就是 clock.wallClock()
                timestampBlock = { "【当前时间】" + clock.wallClock() },
                promptSource = { "" }
            ),
            guard = ContractGuard(),
            settings = InMemorySettingsStore(),
            clock = clock,
            shadowExecutor = { body -> runBlocking { body() } },   // 测试内联执行
            rootDir = dir
        )

    /** 走真实的 [ChampionEngine.launchShadow]（ts 就在这条路径上格式化），拦截发给模型的 prompt。 */
    private fun captureShadowPrompt(): String {
        val dir = tmp.newFolder()
        val gateway = CapturingGateway()
        val clock = FixedClock(epochMs)
        engine(dir, clock, gateway).launchShadow(
            requestId = "r-locale",
            contactId = "c",
            story = "s",
            dialogue = listOf(
                DialogueMessage(
                    id = "m1", speaker = DialogueSpeaker.PARTNER, text = "周五见？", timestamp = epochMs
                ),
                DialogueMessage(
                    id = "m2", speaker = DialogueSpeaker.USER, text = "好", timestamp = epochMs + 60_000L
                )
            ),
            intent = IntentConfig(),
            advisorNote = "",
            originalReply = "",
            timeoutMs = 5_000,
            providerConfig = null
        )
        val prompt = gateway.prompt
        assertNotNull("shadow 必须真的走到模型入口", prompt)
        return prompt!!
    }

    @Test
    fun `thai locale still gets gregorian years in the shadow prompt`() = under("th-TH") {
        val prompt = captureShadowPrompt()
        val expected = TimeFmt.format(epochMs)
        assertEquals("Asia/Shanghai 下这一毫秒就是 09:00", "2026-09-24 09:00", expected)
        assertTrue("本轮输入的 ts 必须与主路径同串：\n$prompt", prompt.contains("\"ts\": \"$expected\""))
        assertTrue(
            "下一分钟的 ts 也必须同串：\n$prompt",
            prompt.contains("\"ts\": \"2026-09-24 09:01\"")
        )
        assertFalse("冠军侧不许出现佛历年份", prompt.contains("2569"))
    }

    @Test
    fun `arabic locale still gets latin digits in the shadow prompt`() = under("ar-SA") {
        val prompt = captureShadowPrompt()
        assertTrue(
            "本轮输入的 ts 必须是拉丁数字：\n$prompt",
            prompt.contains("\"ts\": \"2026-09-24 09:00\"")
        )
        // U+0660..0669 阿拉伯-印度数字、U+06F0..06F9 扩展阿拉伯数字都不许出现
        assertFalse(
            "冠军侧不许出现本地数字",
            Regex("[\\u0660-\\u0669\\u06F0-\\u06F9]").containsMatchIn(prompt)
        )
    }

    @Test
    fun `champion ts is the same string the main path timestamp block carries`() = under("th-TH") {
        val prompt = captureShadowPrompt()
        val clock = FixedClock(epochMs)
        val mainPath = PromptBuilder(
            PromptSourcePort { "" }, InMemoryKnowledgePort(), clock
        ).buildTimestampPrompt()
        val expected = TimeFmt.format(epochMs)
        // 同一 epoch + 同一时区：主 prompt 的「当前时间」与影子 prompt 的 ts 必须同串
        assertTrue("主路径时间块必须含 $expected：$mainPath", mainPath.contains(expected))
        assertTrue("影子 ts 必须等于主路径时间串：\n$prompt", prompt.contains("\"ts\": \"$expected\""))
    }

    @Test
    fun `shadow log ts stays gregorian under thai locale`() = under("th-TH") {
        val dir = tmp.newFolder()
        val clock = FixedClock(epochMs)
        val out = runBlocking {
            engine(dir, clock, CapturingGateway()).shadowTurn(
                requestId = "r", contactId = "c", story = "s", turn = 1,
                messages = listOf(Triple("HER", "周五见？", "2026-09-24 09:00")),
                intent = IntentConfig(), advisorNote = "", originalReply = "",
                timeoutMs = 5_000, providerConfig = null
            )
        }
        // 影子日志的 ts 也是落盘产物：它必须与主路径同一颗格式器
        assertEquals(TimeFmt.format(epochMs), out.logLine["ts"]!!.jsonPrimitive.content)
    }

    @Test
    fun `identity file written_at stays gregorian under thai locale`() = under("th-TH") {
        val dir = tmp.newFolder()
        ChampionIdentity.writeIdentityFile(
            dir, ChampionPromptAssembler(timestampBlock = { "" }, promptSource = { "" })
        )
        val obj = Json.parseToJsonElement(
            File(dir, ChampionIdentity.PORT_IDENTITY_FILE).readText()
        ).jsonObject
        val writtenAt = obj["written_at"]!!.jsonPrimitive.content
        assertTrue(
            "written_at 必须仍是 yyyy-MM-dd HH:mm：$writtenAt",
            Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}").matches(writtenAt)
        )
        assertFalse("身份文件不许出现佛历年份：$writtenAt", writtenAt.startsWith("256"))
    }

    @Test
    fun `fixed clock formats through the shared TimeFmt in every locale`() {
        for (tag in listOf("th-TH", "ar-SA", "en-US")) {
            under(tag) {
                val clock = FixedClock(epochMs)
                assertEquals("$tag: 测试时钟必须与主路径同串", TimeFmt.format(epochMs), clock.wallClock())
                assertEquals("$tag: 日期粒度同理", TimeFmt.date(epochMs), clock.today())
                assertEquals("2026-09-24 09:00", clock.wallClock())
                assertEquals("2026-09-24", clock.today())
            }
        }
    }
}
