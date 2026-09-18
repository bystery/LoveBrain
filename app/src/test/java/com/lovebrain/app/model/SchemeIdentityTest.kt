package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P0-1 回归测试：SchemeIdentity typed identity 序列化/反序列化。
 *
 * 确保改写、反馈、历史 key 均使用 "source:tag" 格式，
 * STYLE(A) 和 DIRECTION(F) 不会因 tag 撞车导致操作串扰。
 */
class SchemeIdentityTest {

    @Test
    fun `STYLE and DIRECTION with same tag produce different keys`() {
        val styleA = SchemeIdentity(SchemeSource.STYLE, "A")
        val dirA = SchemeIdentity(SchemeSource.DIRECTION, "A")
        assertNotEquals(styleA.key, dirA.key)
        assertEquals("STYLE:A", styleA.key)
        assertEquals("DIRECTION:A", dirA.key)
    }

    @Test
    fun `STYLE A and DIRECTION F keys are distinct`() {
        val a = SchemeIdentity(SchemeSource.STYLE, "A")
        val f = SchemeIdentity(SchemeSource.DIRECTION, "F")
        assertEquals("STYLE:A", a.key)
        assertEquals("DIRECTION:F", f.key)
        assertNotEquals(a.key, f.key)
    }

    @Test
    fun `fromKey roundtrip preserves source and tag`() {
        val original = SchemeIdentity(SchemeSource.DIRECTION, "F")
        val key = original.key
        val restored = SchemeIdentity.fromKey(key)
        assertNotNull(restored)
        assertEquals(SchemeSource.DIRECTION, restored!!.source)
        assertEquals("F", restored.tag)
        assertEquals(original, restored)
    }

    @Test
    fun `fromKey returns null for invalid format`() {
        assertNull(SchemeIdentity.fromKey("invalid"))
        assertNull(SchemeIdentity.fromKey(""))
        assertNull(SchemeIdentity.fromKey("STYLE"))
        assertNull(SchemeIdentity.fromKey(":A"))
        assertNull(SchemeIdentity.fromKey("UNKNOWN:A"))
    }

    @Test
    fun `from Scheme constructs identity correctly`() {
        val styleScheme = Scheme(tag = "A", title = "推荐", reply = "test", source = SchemeSource.STYLE)
        val dirScheme = Scheme(tag = "F", title = "跟进", reply = "test", source = SchemeSource.DIRECTION)

        assertEquals(SchemeIdentity(SchemeSource.STYLE, "A"), SchemeIdentity.from(styleScheme))
        assertEquals(SchemeIdentity(SchemeSource.DIRECTION, "F"), SchemeIdentity.from(dirScheme))
    }

    @Test
    fun `all direction identities are distinct from style identities`() {
        val styleIdentities = listOf("A", "B", "C", "D").map {
            SchemeIdentity(SchemeSource.STYLE, it)
        }
        val directionIdentities = ReplyDirection.ALL.map {
            SchemeIdentity(SchemeSource.DIRECTION, it.tag)
        }
        val styleKeys = styleIdentities.map { it.key }.toSet()
        val directionKeys = directionIdentities.map { it.key }.toSet()
        assertEquals(4, styleKeys.size)
        assertEquals(4, directionKeys.size)
        assertEquals(0, styleKeys.intersect(directionKeys).size)
    }
}
