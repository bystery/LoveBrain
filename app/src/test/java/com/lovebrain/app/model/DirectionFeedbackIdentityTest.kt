package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-2 回归测试：四方向反馈身份一致性。
 *
 * 确保：
 * - 赞 F 回复最终保存的是 F 而非 A
 * - 风格和方向的 tag 不重叠
 * - Scheme.source 正确区分来源
 */
class DirectionFeedbackIdentityTest {

    @Test
    fun `style and direction tags do not overlap`() {
        val styleTags = listOf("A", "B", "C", "D")
        val directionTags = ReplyDirection.ALL.map { it.tag }
        val overlap = styleTags.intersect(directionTags.toSet())
        assertTrue("Style and direction tags overlap: $overlap", overlap.isEmpty())
    }

    @Test
    fun `direction schemes have DIRECTION source`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "style A", badBoy = "style B"),
            directions = listOf("方向F", "方向E", "方向X", "方向S")
        )
        val directionSchemes = response.directionSchemes
        assertEquals(4, directionSchemes.size)
        directionSchemes.forEach { scheme ->
            assertEquals(SchemeSource.DIRECTION, scheme.source)
        }
    }

    @Test
    fun `style schemes have STYLE source`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "style A", badBoy = "style B")
        )
        val styleSchemes = response.schemes
        assertEquals(4, styleSchemes.size)
        styleSchemes.forEach { scheme ->
            assertEquals(SchemeSource.STYLE, scheme.source)
        }
    }

    @Test
    fun `liking direction F does not save as style A`() {
        val response = LoveBrainResponse(
            response = ReplySchemes(recommended = "style A text"),
            directions = listOf("F reply", "E reply", "X reply", "S reply")
        )
        val directionSchemes = response.directionSchemes
        val fScheme = directionSchemes.find { it.tag == "F" }!!
        assertEquals("F reply", fScheme.reply)
        assertEquals(SchemeSource.DIRECTION, fScheme.source)

        // Simulate: user likes F
        val feedbacks = mapOf("F" to SchemeFeedback.LIKED)

        // When saving liked direction schemes, it should find F not A
        val likedDirectionSchemes = directionSchemes
            .filter { feedbacks[it.tag] == SchemeFeedback.LIKED }
        assertEquals(1, likedDirectionSchemes.size)
        assertEquals("F", likedDirectionSchemes[0].tag)
        assertEquals(SchemeSource.DIRECTION, likedDirectionSchemes[0].source)

        // Style A should NOT be included in liked direction schemes
        val styleSchemes = response.schemes
        val likedStyleSchemes = styleSchemes
            .filter { feedbacks[it.tag] == SchemeFeedback.LIKED }
        assertEquals(0, likedStyleSchemes.size)
    }

    @Test
    fun `ReplyDirection enum has exactly 4 entries`() {
        assertEquals(4, ReplyDirection.ALL.size)
        assertEquals(4, ReplyDirection.MAX)
    }

    @Test
    fun `ReplyDirection tags and titles are stable`() {
        assertEquals("F", ReplyDirection.FOLLOW.tag)
        assertEquals("跟进", ReplyDirection.FOLLOW.title)
        assertEquals("E", ReplyDirection.EXPAND.tag)
        assertEquals("展开", ReplyDirection.EXPAND.title)
        assertEquals("X", ReplyDirection.EXPRESS.tag)
        assertEquals("表达", ReplyDirection.EXPRESS.title)
        assertEquals("S", ReplyDirection.SHIFT.tag)
        assertEquals("转向", ReplyDirection.SHIFT.title)
    }

    @Test
    fun `ReplyDirection byIndex and byTag lookup`() {
        assertEquals(ReplyDirection.FOLLOW, ReplyDirection.byIndex(0))
        assertEquals(ReplyDirection.SHIFT, ReplyDirection.byIndex(3))
        assertNull(ReplyDirection.byIndex(4))

        assertEquals(ReplyDirection.FOLLOW, ReplyDirection.byTag("F"))
        assertNull(ReplyDirection.byTag("A"))
    }

    @Test
    fun `DirectionCatalog delegates to ReplyDirection`() {
        assertEquals(ReplyDirection.ALL.map { it.tag }, DirectionCatalog.TAGS)
        assertEquals(ReplyDirection.ALL.map { it.title }, DirectionCatalog.TITLES)
        assertEquals(ReplyDirection.MAX, DirectionCatalog.MAX)
    }

    @Test
    fun `empty direction entries produce empty reply`() {
        val response = LoveBrainResponse(
            directions = listOf("", null, "X reply", "S reply")
        )
        // Null entries become empty strings in deserialization
        val schemes = response.directionSchemes
        assertEquals(4, schemes.size)
        // Empty direction = "本轮不适合"
        assertTrue(schemes[0].reply.isBlank())
        assertTrue(schemes[1].reply.isBlank())
        assertFalse(schemes[2].reply.isBlank())
        assertFalse(schemes[3].reply.isBlank())
    }
}
