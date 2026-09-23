package com.lovebrain.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 建库问卷输出解析合同（S2-05 职责拆分的下沉件）。
 *
 * 这段规则原先埋在 KnowledgeBaseActivity 的私有方法里，无法在没有设备的 JVM 上被调用；
 * 拆出来之后它必须能被真实断言——尤其是「降级建库不能当成功」这条判定，
 * 它直接决定用户看到的是「画像已生成」还是「AI 画像生成不完整」。
 */
class OnboardingResultParserTest {

    private fun fullRaw() = """
        ${OnboardingResultParser.MARKER_DISPLAY}
        小雅
        ${OnboardingResultParser.MARKER_STAGE}
        dating
        ${OnboardingResultParser.MARKER_ME}
        我习惯先讲道理
        ${OnboardingResultParser.MARKER_HER}
        她需要先被听见
        ${OnboardingResultParser.MARKER_WARMTH}
        冷战第 3 天
    """.trimIndent()

    @Test
    fun `five sections split by markers`() {
        val parsed = OnboardingResultParser.parse(fullRaw())
        assertEquals("小雅", parsed.display)
        assertEquals("dating", parsed.stage)
        assertEquals("我习惯先讲道理", parsed.me)
        assertEquals("她需要先被听见", parsed.her)
        assertEquals("冷战第 3 天", parsed.warmth)
        assertTrue(parsed.hasUsableProfile)
    }

    @Test
    fun `warmth is the tail section and needs no end marker`() {
        val parsed = OnboardingResultParser.parse(
            "===ME===\nme\n===HER===\nher\n===WARMTH===\n第一段\n\n第二段"
        )
        assertEquals("第一段\n\n第二段", parsed.warmth)
    }

    @Test
    fun `empty raw yields no usable profile`() {
        val parsed = OnboardingResultParser.parse("")
        assertEquals("", parsed.display)
        assertEquals("", parsed.me)
        assertFalse(parsed.hasUsableProfile)
    }

    @Test
    fun `missing middle marker downgrades the whole profile`() {
        val withoutHer = "===DISPLAY===\nd\n===STAGE===\ns\n===ME===\nm\n===WARMTH===\nw"
        val parsed = OnboardingResultParser.parse(withoutHer)
        // HER marker 同时是 ME 的结束 marker：它缺失时 ME 也取不到边界，两段一起判空。
        // 这是保守行为——宁可整库降级为模板，也不把「读不准」的内容当成可用画像写进去。
        assertEquals("", parsed.me)
        assertEquals("", parsed.her)
        assertEquals("w", parsed.warmth)
        assertFalse("缺 HER 必须判为降级，不能报画像成功", parsed.hasUsableProfile)
    }

    @Test
    fun `blank-only section counts as missing`() {
        val parsed = OnboardingResultParser.parse(
            "===ME===\nme\n===HER===\n   \n===WARMTH===\nw\n"
        )
        assertEquals("", parsed.her)
        assertFalse(parsed.hasUsableProfile)
    }

    @Test
    fun `out of order markers yield empty instead of swallowing text`() {
        // ME 之后找不到 HER：不能把剩余全文当成 ME
        val parsed = OnboardingResultParser.parse("===ME===\nonly me\n")
        assertEquals("", parsed.me)
        assertEquals("", parsed.her)
    }

    @Test
    fun `repeated marker takes the first occurrence`() {
        val parsed = OnboardingResultParser.parse(
            "===ME===\nfirst\n===HER===\nher\n===ME===\nsecond\n===HER===\nx\n===WARMTH===\nw"
        )
        assertEquals("first", parsed.me)
    }

    @Test
    fun `unrelated text without markers is ignored`() {
        val parsed = OnboardingResultParser.parse("模型返回了一段道歉，没有任何 marker。")
        assertFalse(parsed.hasUsableProfile)
        assertEquals("", parsed.display)
    }
}
