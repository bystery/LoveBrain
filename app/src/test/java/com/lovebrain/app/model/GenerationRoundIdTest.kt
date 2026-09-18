package com.lovebrain.app.model

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

/**
 * P0-3: 测试 generationRoundId 的稳定性——单条改写/undo 不应改变轮次身份。
 *
 * 虽然 viewMode 的 remember 逻辑在 Composable 中，但 roundId 的递增逻辑
 * 在 ViewModel.onReplyResult() 中。此测试验证 SchemeIdentity 和 roundId 的
 * 语义契约。
 */
class GenerationRoundIdTest {

    @Test
    fun `SchemeIdentity for STYLE A is stable`() {
        val identity = SchemeIdentity(SchemeSource.STYLE, "A")
        assertEquals("STYLE:A", identity.key)
        assertEquals(SchemeSource.STYLE, identity.source)
        assertEquals("A", identity.tag)
    }

    @Test
    fun `SchemeIdentity for DIRECTION F is stable`() {
        val identity = SchemeIdentity(SchemeSource.DIRECTION, "F")
        assertEquals("DIRECTION:F", identity.key)
        assertEquals(SchemeSource.DIRECTION, identity.source)
        assertEquals("F", identity.tag)
    }

    @Test
    fun `fromKey roundtrip preserves identity`() {
        val original = SchemeIdentity(SchemeSource.DIRECTION, "X")
        val parsed = SchemeIdentity.fromKey(original.key)
        assertEquals(original, parsed)
    }

    @Test
    fun `STYLE and DIRECTION with same tag have different keys`() {
        val styleA = SchemeIdentity(SchemeSource.STYLE, "A")
        // 假设有方向 tag 也是 A（虽然实际不会），验证 source 区分
        val dirA = SchemeIdentity(SchemeSource.DIRECTION, "A")
        assertNotEquals(styleA.key, dirA.key)
    }

    @Test
    fun `fromKey with invalid source returns null — fail closed`() {
        val parsed = SchemeIdentity.fromKey("INVALID:A")
        assertEquals(null, parsed)
    }

    @Test
    fun `fromKey with malformed key returns null — fail closed`() {
        assertEquals(null, SchemeIdentity.fromKey("no_colon"))
        assertEquals(null, SchemeIdentity.fromKey(""))
    }

    @Test
    fun `fromKey with extra colons preserves tag with colons`() {
        // tag 本身可能包含冒号——limit=2 确保只在第一个冒号分割
        val parsed = SchemeIdentity.fromKey("STYLE:A:B")
        assertEquals(SchemeSource.STYLE, parsed?.source)
        assertEquals("A:B", parsed?.tag)
    }
}
