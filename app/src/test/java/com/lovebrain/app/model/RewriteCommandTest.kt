package com.lovebrain.app.model

import org.junit.Assert.*
import org.junit.Test

/**
 * DRY 回归测试：RewriteCommand 枚举是改写选项的唯一定义。
 * 验证标签数量、顺序和文案不被意外修改。
 */
class RewriteCommandTest {

    @Test
    fun `ALL_LABELS contains exactly four options`() {
        assertEquals(4, RewriteCommand.ALL_LABELS.size)
    }

    @Test
    fun `ALL_LABELS preserves expected order`() {
        assertEquals(listOf("换一种说法", "更自然", "更简短", "更温柔"), RewriteCommand.ALL_LABELS)
    }

    @Test
    fun `fromLabel returns correct command`() {
        assertEquals(RewriteCommand.REPHRASE, RewriteCommand.fromLabel("换一种说法"))
        assertEquals(RewriteCommand.NATURAL, RewriteCommand.fromLabel("更自然"))
        assertEquals(RewriteCommand.SHORTER, RewriteCommand.fromLabel("更简短"))
        assertEquals(RewriteCommand.GENTLER, RewriteCommand.fromLabel("更温柔"))
    }

    @Test
    fun `fromLabel returns null for unknown label`() {
        assertNull(RewriteCommand.fromLabel("不存在的选项"))
    }

    @Test
    fun `each label matches its instruction`() {
        RewriteCommand.entries.forEach { cmd ->
            assertEquals("label should match instruction for ${cmd.name}", cmd.label, cmd.instruction)
        }
    }
}
