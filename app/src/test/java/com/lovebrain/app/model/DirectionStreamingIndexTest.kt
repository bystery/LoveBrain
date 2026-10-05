package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P2 回归测试：streaming 方向 index 保留——不因 filter 空方向导致后续 index 左移。
 *
 * 修复前 extractDirectionsSchemes 先 filter { isNotBlank } 再 mapIndexed，
 * 中间某方向为空时后续方向 index 左移，存在 X 被临时标成 E 的风险。
 * 修复后 mapIndexed 保留原始位置，空方向 reply 设空。
 */
class DirectionStreamingIndexTest {

    @Test
    fun `direction schemes preserve original index when middle entry is empty`() {
        // directions 数组中 E(index=1) 为空字符串
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A"),
            directions = listOf("F reply", "", "X reply", "S reply")
        )
        val schemes = response.directionSchemes
        assertEquals(4, schemes.size)
        // F at index 0
        assertEquals("F", schemes[0].tag)
        assertEquals("跟进", schemes[0].title)
        assertEquals("F reply", schemes[0].reply)
        // E at index 1 — should be empty (本轮不适合)
        assertEquals("E", schemes[1].tag)
        assertEquals("展开", schemes[1].title)
        assertEquals("", schemes[1].reply)
        // X at index 2 — must NOT shift to index 1
        assertEquals("X", schemes[2].tag)
        assertEquals("表达", schemes[2].title)
        assertEquals("X reply", schemes[2].reply)
        // S at index 3
        assertEquals("S", schemes[3].tag)
        assertEquals("转向", schemes[3].title)
        assertEquals("S reply", schemes[3].reply)
    }

    @Test
    fun `direction schemes preserve index when first entry is empty`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A"),
            directions = listOf("", "E reply", "X reply", "S reply")
        )
        val schemes = response.directionSchemes
        assertEquals(4, schemes.size)
        assertEquals("F", schemes[0].tag)
        assertEquals("", schemes[0].reply)
        assertEquals("E", schemes[1].tag)
        assertEquals("E reply", schemes[1].reply)
        assertEquals("X", schemes[2].tag)
        assertEquals("X reply", schemes[2].reply)
        assertEquals("S", schemes[3].tag)
        assertEquals("S reply", schemes[3].reply)
    }

    @Test
    fun `direction schemes preserve index when last entry is empty`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A"),
            directions = listOf("F reply", "E reply", "X reply", "")
        )
        val schemes = response.directionSchemes
        assertEquals(4, schemes.size)
        assertEquals("F", schemes[0].tag)
        assertEquals("F reply", schemes[0].reply)
        assertEquals("E", schemes[1].tag)
        assertEquals("E reply", schemes[1].reply)
        assertEquals("X", schemes[2].tag)
        assertEquals("X reply", schemes[2].reply)
        assertEquals("S", schemes[3].tag)
        assertEquals("", schemes[3].reply)
    }

    @Test
    fun `all empty directions produce 4 schemes with correct tags`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A")
        )
        val schemes = response.directionSchemes
        assertEquals(4, schemes.size)
        assertEquals(listOf("F", "E", "X", "S"), schemes.map { it.tag })
        schemes.forEach { scheme ->
            assertEquals("", scheme.reply)
            assertEquals(SchemeSource.DIRECTION, scheme.source)
        }
    }
}
