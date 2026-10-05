package com.lovebrain.app.model

import org.junit.Assert.*
import org.junit.Test

/**
 * DRY 回归测试：RewriteCommand 枚举是改写选项的唯一定义。
 *  v2: 扩展预设选项 + CUSTOM。
 */
class RewriteCommandTest {

    @Test
    fun `ALL_LABELS contains expected options`() {
        //  v2: 8 个选项（7 预设 + 1 自定义）
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

    /** 卡片调整区只列四项，顺序就是屏幕上那一行的顺序 */
    @Test
    fun `the adjusting row lists exactly four options`() {
        assertEquals(
            listOf(RewriteCommand.NATURAL, RewriteCommand.REPHRASE, RewriteCommand.GENTLER, RewriteCommand.CUSTOM),
            RewriteCommand.UI_OPTIONS
        )
        assertEquals(
            listOf("更自然", "换种说话", "更温柔", "自定义"),
            RewriteCommand.UI_OPTION_LABELS
        )
        // 自定义不参与预设反查，那一格走自由文字
        assertEquals(
            listOf(RewriteCommand.NATURAL, RewriteCommand.REPHRASE, RewriteCommand.GENTLER),
            RewriteCommand.UI_PRESET_OPTIONS
        )
    }

    /**
     * 撤下的是展示，不是内部标识。
     *
     * 更短 / 更像我 / 别反问 / 更直接 不再出现在界面上，但枚举项、稳定名字、发给模型的
     * 指令与声明顺序都还在：请求侧按 label 反查 instruction，在途状态里存的也是 label，
     * 而 ordinal 参与旧数据的读回。删项或换序都会把旧读数解成新选项。
     */
    @Test
    fun `dropped presets stay in the enum with their stable names and order`() {
        val dropped = listOf(
            RewriteCommand.SHORTER,
            RewriteCommand.LIKE_ME,
            RewriteCommand.NO_QUESTION,
            RewriteCommand.DIRECT
        )
        dropped.forEach { cmd ->
            assertTrue("${cmd.name} 不该出现在调整区", cmd !in RewriteCommand.UI_OPTIONS)
            assertTrue("${cmd.name} 的稳定名字还在", cmd.label.isNotBlank())
            assertTrue("${cmd.name} 的指令还在", cmd.instruction.isNotBlank())
            // 反查不因为界面撤下它而断掉
            assertEquals(cmd, RewriteCommand.fromLabel(cmd.label))
            assertEquals(cmd, RewriteCommand.fromDisplayLabel(cmd.displayLabel))
        }
        assertEquals(
            listOf("REPHRASE", "SHORTER", "LIKE_ME", "NO_QUESTION", "DIRECT", "GENTLER", "NATURAL", "CUSTOM"),
            RewriteCommand.entries.map { it.name }
        )
        assertEquals(8, RewriteCommand.entries.size)
    }

    /** 只有"换种说话"这一项的屏幕文案与稳定名字不同；其余四项一字不差 */
    @Test
    fun `screen text differs from the stable name only for the rephrase option`() {
        RewriteCommand.entries.forEach { cmd ->
            if (cmd == RewriteCommand.REPHRASE) {
                assertEquals("换一种说法", cmd.label)
                assertEquals("换种说话", cmd.displayLabel)
            } else {
                assertEquals(cmd.name, cmd.label, cmd.displayLabel)
            }
        }
        // 屏幕换字不改变发给模型的指令：请求侧仍按稳定名字反查
        assertEquals("换一种说法", RewriteCommand.fromLabel("换一种说法")?.instruction)
        assertEquals(RewriteCommand.REPHRASE, RewriteCommand.fromDisplayLabel("换种说话"))
        assertEquals(RewriteCommand.REPHRASE, RewriteCommand.fromDisplayLabel("换一种说法"))
        assertNull(RewriteCommand.fromDisplayLabel("不存在的选项"))
    }
}
