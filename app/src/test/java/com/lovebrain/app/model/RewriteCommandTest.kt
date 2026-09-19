package com.lovebrain.app.model

import org.junit.Assert.*
import org.junit.Test

/**
 * DRY 回归测试：RewriteCommand 枚举是改写选项的唯一定义。
 * F01 v2: 扩展预设选项 + CUSTOM。
 */
class RewriteCommandTest {

    @Test
    fun `ALL_LABELS contains expected options`() {
        // F01 v2: 8 个选项（7 预设 + 1 自定义）
        assertEquals(8, RewriteCommand.ALL_LABELS.size)
    }

    @Test
    fun `PRESET_LABELS excludes CUSTOM`() {
        assertTrue(RewriteCommand.PRESET_LABELS.isNotEmpty())
        assertFalse(RewriteCommand.PRESET_LABELS.contains("自定义"))
    }

    @Test
    fun `ALL_LABELS preserves expected order`() {
        assertEquals(
            listOf("换一种说法", "更短", "更像我", "别反问", "更直接", "更温柔", "更自然", "自定义"),
            RewriteCommand.ALL_LABELS
        )
    }

    @Test
    fun `fromLabel returns correct command`() {
        assertEquals(RewriteCommand.REPHRASE, RewriteCommand.fromLabel("换一种说法"))
        assertEquals(RewriteCommand.SHORTER, RewriteCommand.fromLabel("更短"))
        assertEquals(RewriteCommand.LIKE_ME, RewriteCommand.fromLabel("更像我"))
        assertEquals(RewriteCommand.NO_QUESTION, RewriteCommand.fromLabel("别反问"))
        assertEquals(RewriteCommand.DIRECT, RewriteCommand.fromLabel("更直接"))
        assertEquals(RewriteCommand.GENTLER, RewriteCommand.fromLabel("更温柔"))
        assertEquals(RewriteCommand.NATURAL, RewriteCommand.fromLabel("更自然"))
        assertEquals(RewriteCommand.CUSTOM, RewriteCommand.fromLabel("自定义"))
    }

    @Test
    fun `fromLabel returns null for unknown label`() {
        assertNull(RewriteCommand.fromLabel("不存在的选项"))
    }

    @Test
    fun `CUSTOM has empty instruction`() {
        assertEquals("", RewriteCommand.CUSTOM.instruction)
    }

    @Test
    fun `preset commands have non-empty instructions`() {
        RewriteCommand.entries.filter { it != RewriteCommand.CUSTOM }.forEach { cmd ->
            assertTrue("instruction should not be empty for ${cmd.name}", cmd.instruction.isNotBlank())
        }
    }
}
