package com.lovebrain.app.viewmodel

import com.lovebrain.app.domain.PartialJsonObjects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PartialJsonObjects 流式增量解析契约单测。
 * 验证：从不完整/带 markdown 包装的累积 JSON 中提取已完整到达的对象。
 */
class PartialJsonObjectsTest {

    // ═══ extractObjects ═══

    @Test
    fun `empty buffer returns empty list`() {
        assertTrue(PartialJsonObjects.extractObjects("", "tips").isEmpty())
    }

    @Test
    fun `key not found returns empty list`() {
        val raw = """{"other":[{"a":1}]}"""
        assertTrue(PartialJsonObjects.extractObjects(raw, "tips").isEmpty())
    }

    @Test
    fun `array start not yet arrived returns empty`() {
        val raw = """{"tips""" // key present but no [
        assertTrue(PartialJsonObjects.extractObjects(raw, "tips").isEmpty())
    }

    @Test
    fun `one complete object extracted`() {
        val raw = """{"tips":[{"example":"hi","topic":"greet"}]}"""
        val result = PartialJsonObjects.extractObjects(raw, "tips")
        assertEquals(1, result.size)
        assertTrue(result[0].contains("\"example\":\"hi\""))
    }

    @Test
    fun `two complete objects extracted`() {
        val raw = """{"tips":[{"a":1},{"b":2}]}"""
        val result = PartialJsonObjects.extractObjects(raw, "tips")
        assertEquals(2, result.size)
        assertTrue(result[0].contains("\"a\":1"))
        assertTrue(result[1].contains("\"b\":2"))
    }

    @Test
    fun `incomplete second object not returned`() {
        // 第二个对象未闭合（流式中途）
        val raw = """{"tips":[{"a":1},{"b":2"""
        val result = PartialJsonObjects.extractObjects(raw, "tips")
        assertEquals(1, result.size)
        assertTrue(result[0].contains("\"a\":1"))
    }

    @Test
    fun `markdown json fence stripped before extracting`() {
        val raw = "```json\n{\"tips\":[{\"x\":1}]}\n```"
        val result = PartialJsonObjects.extractObjects(raw, "tips")
        assertEquals(1, result.size)
        assertTrue(result[0].contains("\"x\":1"))
    }

    @Test
    fun `nested object braces counted correctly`() {
        val raw = """{"tips":[{"meta":{"deep":true},"example":"ok"}]}"""
        val result = PartialJsonObjects.extractObjects(raw, "tips")
        assertEquals(1, result.size)
        assertTrue(result[0].contains("\"deep\":true"))
    }

    @Test
    fun `braces inside string literals ignored`() {
        val raw = """{"tips":[{"example":"he said {hi}"}]}"""
        val result = PartialJsonObjects.extractObjects(raw, "tips")
        assertEquals(1, result.size)
        assertTrue(result[0].contains("he said {hi}"))
    }

    @Test
    fun `escaped quote inside string does not break parsing`() {
        val raw = """{"tips":[{"example":"a\"b"}]}"""
        val result = PartialJsonObjects.extractObjects(raw, "tips")
        assertEquals(1, result.size)
        assertTrue(result[0].contains("a\\\"b"))
    }

    @Test
    fun `whitespace and commas between objects skipped`() {
        val raw = """{"tips": [ {"a":1} , {"b":2} ] }"""
        val result = PartialJsonObjects.extractObjects(raw, "tips")
        assertEquals(2, result.size)
    }

    // ═══ extractKeyObject ═══

    @Test
    fun `extractKeyObject returns complete object when closed`() {
        val raw = """{"response":{"recommended":"hi"}}"""
        val obj = PartialJsonObjects.extractKeyObject(raw, "response")
        assertNotNull(obj)
        assertTrue(obj!!.contains("\"recommended\":\"hi\""))
    }

    @Test
    fun `extractKeyObject returns null when incomplete`() {
        val raw = """{"response":{"recommended":"hi""""
        assertNull(PartialJsonObjects.extractKeyObject(raw, "response"))
    }

    @Test
    fun `extractKeyObject returns null when key absent`() {
        assertNull(PartialJsonObjects.extractKeyObject("""{"other":1}""", "response"))
    }

    @Test
    fun `extractKeyObject handles markdown fence`() {
        val raw = "```json\n{\"response\":{\"warm\":\"stay\"}}\n```"
        val obj = PartialJsonObjects.extractKeyObject(raw, "response")
        assertNotNull(obj)
        assertTrue(obj!!.contains("\"warm\":\"stay\""))
    }

    @Test
    fun `extractKeyObject nested braces balanced`() {
        val raw = """{"analysis":{"ongoing":[{"item":"x"}]}}"""
        val obj = PartialJsonObjects.extractKeyObject(raw, "analysis")
        assertNotNull(obj)
        assertTrue(obj!!.contains("\"item\":\"x\""))
    }
}
