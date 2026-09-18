package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-1 回归测试：风格/方向视图切换数据逻辑。
 *
 * 确保结果区始终只有一排四卡：
 * - STYLE 模式显示 A/B/C/D
 * - DIRECTION 模式显示 F/E/X/S
 * - 切换不混淆两组数据
 * - 赞踩只影响当前显示的方案组
 */
class StyleDirectionSwitchTest {

    @Test
    fun `style schemes have 4 entries with correct tags`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(
                recommended = "A reply",
                badBoy = "B reply",
                playful = "C reply",
                warm = "D reply"
            ),
            directions = listOf("F reply", "E reply", "X reply", "S reply")
        )
        val styleSchemes = response.schemes
        assertEquals(4, styleSchemes.size)
        assertEquals(listOf("A", "B", "C", "D"), styleSchemes.map { it.tag })
        styleSchemes.forEach { assertEquals(SchemeSource.STYLE, it.source) }
    }

    @Test
    fun `direction schemes have 4 entries with correct tags`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A"),
            directions = listOf("F reply", "E reply", "X reply", "S reply")
        )
        val directionSchemes = response.directionSchemes
        assertEquals(4, directionSchemes.size)
        assertEquals(listOf("F", "E", "X", "S"), directionSchemes.map { it.tag })
        directionSchemes.forEach { assertEquals(SchemeSource.DIRECTION, it.source) }
    }

    @Test
    fun `style and direction schemes are different lists`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A reply"),
            directions = listOf("F reply", "E reply", "X reply", "S reply")
        )
        assertNotEquals(response.schemes, response.directionSchemes)
        assertNotEquals(response.schemes.map { it.tag }, response.directionSchemes.map { it.tag })
    }

    @Test
    fun `empty directions still produce 4 direction schemes`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A reply")
        )
        val directionSchemes = response.directionSchemes
        assertEquals(4, directionSchemes.size)
        // All should have empty replies
        directionSchemes.forEach { scheme ->
            assertTrue("Direction ${scheme.tag} should have blank reply", scheme.reply.isBlank())
            assertEquals(SchemeSource.DIRECTION, scheme.source)
        }
    }

    @Test
    fun `partial directions produce 4 schemes with empty for missing`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A reply"),
            directions = listOf("F reply", null, "X reply", "S reply")
        )
        val directionSchemes = response.directionSchemes
        assertEquals(4, directionSchemes.size)
        assertEquals("F reply", directionSchemes[0].reply)
        assertTrue(directionSchemes[1].reply.isBlank())
        assertEquals("X reply", directionSchemes[2].reply)
        assertEquals("S reply", directionSchemes[3].reply)
    }

    @Test
    fun `liking style A does not affect direction schemes`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A reply"),
            directions = listOf("F reply", "E reply", "X reply", "S reply")
        )
        val feedbacks = mapOf("A" to SchemeFeedback.LIKED)

        val likedStyle = response.schemes.filter { feedbacks[it.tag] == SchemeFeedback.LIKED }
        val likedDirection = response.directionSchemes.filter { feedbacks[it.tag] == SchemeFeedback.LIKED }

        assertEquals(1, likedStyle.size)
        assertEquals("A", likedStyle[0].tag)
        assertEquals(0, likedDirection.size)
    }

    @Test
    fun `liking direction F does not affect style schemes`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "A reply"),
            directions = listOf("F reply", "E reply", "X reply", "S reply")
        )
        val feedbacks = mapOf("F" to SchemeFeedback.LIKED)

        val likedStyle = response.schemes.filter { feedbacks[it.tag] == SchemeFeedback.LIKED }
        val likedDirection = response.directionSchemes.filter { feedbacks[it.tag] == SchemeFeedback.LIKED }

        assertEquals(0, likedStyle.size)
        assertEquals(1, likedDirection.size)
        assertEquals("F", likedDirection[0].tag)
        assertEquals(SchemeSource.DIRECTION, likedDirection[0].source)
    }

    @Test
    fun `style and direction tags never overlap`() {
        val styleTags = setOf("A", "B", "C", "D")
        val directionTags = ReplyDirection.ALL.map { it.tag }.toSet()
        assertTrue("Tags overlap: ${styleTags intersect directionTags}",
            (styleTags intersect directionTags).isEmpty())
    }
}
