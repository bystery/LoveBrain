package com.lovebrain.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-7: 测试真实生产路径的 streaming extractor——extractStringArrayNullable。
 *
 * 验证 null 占位保留原始 index，不再把 [null, "E", "X", "S"] 压成 ["E", "X", "S"]。
 *
 * 此测试直接测试 GenerationEngine.kt 中的 PartialJsonObjects.extractStringArrayNullable，
 * 而非最终 LoveBrainResponse.directionSchemes——真正覆盖 streaming bug 所在函数。
 */
class StreamingNullIndexTest {

    @Test
    fun `null at first position preserves index`() {
        val raw = """{"directions": [null, "E reply", "X reply", "S reply"]}"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        assertEquals(4, result.size)
        assertNull(result[0])  // null 占位——不应被跳过
        assertEquals("E reply", result[1])
        assertEquals("X reply", result[2])
        assertEquals("S reply", result[3])
    }

    @Test
    fun `null in middle preserves index`() {
        val raw = """{"directions": ["F reply", null, "X reply", "S reply"]}"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        assertEquals(4, result.size)
        assertEquals("F reply", result[0])
        assertNull(result[1])
        assertEquals("X reply", result[2])
        assertEquals("S reply", result[3])
    }

    @Test
    fun `empty string preserves index`() {
        val raw = """{"directions": ["", "E reply", "X reply", "S reply"]}"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        assertEquals(4, result.size)
        assertEquals("", result[0])
        assertEquals("E reply", result[1])
        assertEquals("X reply", result[2])
        assertEquals("S reply", result[3])
    }

    @Test
    fun `all null returns list with all nulls`() {
        val raw = """{"directions": [null, null, null, null]}"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        assertEquals(4, result.size)
        result.forEach { assertNull(it) }
    }

    @Test
    fun `no nulls works correctly`() {
        val raw = """{"directions": ["F reply", "E reply", "X reply", "S reply"]}"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        assertEquals(4, result.size)
        assertEquals("F reply", result[0])
        assertEquals("E reply", result[1])
        assertEquals("X reply", result[2])
        assertEquals("S reply", result[3])
    }

    @Test
    fun `partial array returns completed elements only`() {
        // 流式中数组尚未闭合——只返回已完整到达的元素
        val raw = """{"directions": ["F reply", "E reply"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        // 第一个完整，第二个未闭合
        assertEquals(1, result.size)
        assertEquals("F reply", result[0])
    }

    @Test
    fun `mixed null and empty string preserves all indices`() {
        val raw = """{"directions": [null, "", "X reply", null]}"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        assertEquals(4, result.size)
        assertNull(result[0])
        assertEquals("", result[1])
        assertEquals("X reply", result[2])
        assertNull(result[3])
    }

    @Test
    fun `backward compatible extractStringArray filters nulls`() {
        val raw = """{"directions": [null, "E reply", "X reply", "S reply"]}"""
        val result = PartialJsonObjects.extractStringArray(raw, "directions")
        // 旧 API 过滤 null——仅返回非 null 元素
        assertEquals(3, result.size)
        assertEquals("E reply", result[0])
        assertEquals("X reply", result[1])
        assertEquals("S reply", result[2])
    }

    @Test
    fun `key not found returns empty`() {
        val raw = """{"other": [1, 2, 3]}"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `markdown code fence stripped`() {
        val raw = """```json
            {"directions": ["F", null, "X", "S"]}
        ```"""
        val result = PartialJsonObjects.extractStringArrayNullable(raw, "directions")
        assertEquals(4, result.size)
        assertEquals("F", result[0])
        assertNull(result[1])
        assertEquals("X", result[2])
        assertEquals("S", result[3])
    }
}
