package com.lovebrain.app.util

import com.lovebrain.app.data.FixedClockZone
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.PromptSourcePort
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * 时间格式不能随设备语言变：th_TH 下 SimpleDateFormat 会给佛历年份（2569），
 * ar_SA 下会给本地数字——提示词里的「当前时间」直接喂给模型，它必须是同一串字节。
 *
 * 这一族在**钉死的时区 + 切换 Locale** 下判产出，不判源码是否 import 了某个类。
 */
class TimeFmtLocaleTest {

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

    /** 2026-09-24T01:00:00Z = Asia/Shanghai 09:00 */
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

    @Test
    fun `thai locale still gets the gregorian year and latin digits`() = under("th-TH") {
        assertEquals("2026-09-24 09:00", TimeFmt.format(epochMs))
        assertTrue("today 也必须是公历", Regex("\\d{4}-\\d{2}-\\d{2}").matches(TimeFmt.today()))
        assertFalse("不许出现佛历年份", TimeFmt.today().startsWith("256"))
    }

    @Test
    fun `arabic locale still gets latin digits and the same instant`() = under("ar-SA") {
        assertEquals("2026-09-24 09:00", TimeFmt.format(epochMs))
        assertEquals("同一次解析在 ar_SA 下也回到同一个时刻", epochMs, TimeFmt.parse("2026-09-24 09:00"))
    }

    @Test
    fun `the prompt timestamp shares the same formatter`() = under("th-TH") {
        val builder = PromptBuilder(PromptSourcePort { "" }, InMemoryKnowledgePort())
        val timestamp = builder.buildTimestampPrompt()
        assertTrue(
            "prompt 时间戳必须是公历 + 拉丁数字：$timestamp",
            Regex("【当前时间】\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}").containsMatchIn(timestamp)
        )
        assertFalse("prompt 时间戳不许出现佛历年份", timestamp.contains("【当前时间】256"))
    }
}
