package com.lovebrain.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Jsons.extractJsonBlock 契约单测。
 * 验证：剥 ``` 包装 + 提取首个完整 {..} 块。
 */
class JsonsTest {

    @Test
    fun `plain json object returned as-is`() {
        val raw = """{"a":1,"b":"x"}"""
        assertEquals(raw, Jsons.extractJsonBlock(raw))
    }

    @Test
    fun `json wrapped in bare markdown fence stripped`() {
        val raw = "```\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", Jsons.extractJsonBlock(raw))
    }

    @Test
    fun `json wrapped in json markdown fence stripped`() {
        val raw = "```json\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", Jsons.extractJsonBlock(raw))
    }

    @Test
    fun `leading prose before json is ignored`() {
        val raw = "好的，这是回复：\n```json\n{\"recommended\":\"嗨\"}\n```"
        assertEquals("""{"recommended":"嗨"}""", Jsons.extractJsonBlock(raw))
    }

    @Test
    fun `nested braces captured fully`() {
        val raw = """prefix {"outer":{"inner":"v"}} trailing"""
        assertEquals("""{"outer":{"inner":"v"}}""", Jsons.extractJsonBlock(raw))
    }

    @Test
    fun `no braces returns null`() {
        assertNull(Jsons.extractJsonBlock("just text no json"))
    }

    @Test
    fun `only opening brace returns null`() {
        assertNull(Jsons.extractJsonBlock("{ incomplete"))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(Jsons.extractJsonBlock(""))
    }

    @Test
    fun `blank string returns null`() {
        assertNull(Jsons.extractJsonBlock("   \n  "))
    }

    @Test
    fun `braces in wrong order returns null`() {
        // a > b guard: lastIndexOf('}') < indexOf('{')
        assertNull(Jsons.extractJsonBlock("} text {"))
    }

    @Test
    fun `escape-unescape round trip preserves input` () {
        val cases = listOf(
            "C:\\new",               // 反斜杠+字母 n 字面形态（ 触发形态）
            "路径\\名称",             // 反斜杠+中文
            "line1\nline2",          // 真换行
            "she said \"hi\"",       // 双引号
            "a \\\" mixed \"b\"",    // 反斜杠+引号组合
            "end\\"                  // 尾部单反斜杠
        )
        for (x in cases) {
            assertEquals(x, Jsons.unescapeJsonString(Jsons.escapeJsonString(x)))
        }
    }

    @Test
    fun `single-pass unescape distinguishes escaped backslash from newline escape` () {
        // 源码字面 "\\\\n" = 字符序列 \ \ n → 还原为 反斜杠+n 两字符，不是真换行
        assertEquals("\\n", Jsons.unescapeJsonString("\\\\n"))
        // 源码字面 "\\n" = 字符序列 \ n → 还原为真换行
        assertEquals("\n", Jsons.unescapeJsonString("\\n"))
    }
}
