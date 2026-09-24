package com.lovebrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S2-07 / P3-03: 用户文案资源化与触摸区的源码级合同。
 *
 * 为什么用源码扫描而不是行为断言：
 * 复核报告 §6 S2-07 与 §7.1 给的都是**可静态核对**的事实断言
 * （"strings.xml 仅约 38 行""新增资源没有被生产组件使用""多个 20/24/28/40dp 自定义 clickable"）。
 * 触摸区在 compose 测试里要跑 emulator（本机没有 system image，跑不了），
 * 所以这里用一条能进 CI 的静态门禁锁住已经改好的数值，防止被改回去；
 * 真正的 TalkBack / 2.0x 字体 / 360dp 截图矩阵仍然是需要设备的人工验收项，
 * 在验收报告里如实标注为未执行。
 *
 * ⚠ 但别把本文件当"触摸区已验证"（独立复核 P1-02 指出的正是这个误读）：
 * 这里只能证明"源码里出现过 48 这个数字、clickable 没排在 padding 后面"。
 * PanelHeader 外面套 48dp 的 Box、真正 clickable 仍挂在 20dp 的内层标签上时，
 * 本文件的 [mode switcher hit box is at least 48dp even though the capsule stays 20dp]
 * 照样会绿。判"点得到点不到"的权威断言在
 * `androidTest/.../ui/panel/PanelHeaderTouchTargetsTest.kt`：它读组合后每个可点击节点的
 * boundsInRoot。两条都要，但只有后者能证明用户行为。
 */
class ProductionUiContractTest {

    private val mainRoot = File("src/main/java/com/lovebrain/app")
        .takeIf { it.isDirectory }
        ?: File("app/src/main/java/com/lovebrain/app")

    private fun source(vararg parts: String): String {
        val f = File(mainRoot, parts.joinToString(File.separator))
        assertTrue("production source missing: $f", f.isFile)
        return f.readText()
    }

    /** 去掉注释与 KDoc，只留真正的代码，避免文档里引用的旧数值误判 */
    private fun codeOf(src: String): String = buildString {
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).takeIf { it >= 0 } ?: src.length
                    i = minOf(end + 2, src.length)
                }
                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i).takeIf { it >= 0 } ?: src.length
                    i = end
                }
                else -> { append(src[i]); i++ }
            }
        }
    }

    // ─── 触摸区下限 ────────────────────────────────────────────────

    @Test
    fun `panel header collapse hotzone is at least 48dp`() {
        val code = codeOf(source("ui", "panel", "PanelHeader.kt"))
        val value = Regex("COLLAPSE_HOTZONE_DP\\s*=\\s*(\\w+)").find(code)?.groupValues?.get(1)
        assertTrue("COLLAPSE_HOTZONE_DP must exist", value != null)
        val dp = if (value == "MIN_TOUCH_TARGET_DP") 48 else value!!.toInt()
        assertTrue("collapse hotzone must be >= 48dp, was $dp", dp >= 48)
    }

    @Test
    fun `mode switcher hit box is at least 48dp even though the capsule stays 20dp`() {
        val code = codeOf(source("ui", "panel", "PanelHeader.kt"))
        assertTrue(
            "the three-segment switcher must sit inside a 48dp hit box",
            Regex("height\\(HeaderDimens\\.MIN_TOUCH_TARGET_DP\\.dp\\)").containsMatchIn(code)
        )
    }

    @Test
    fun `result utility trigger hit box is at least 48dp`() {
        val code = codeOf(source("ui", "panel", "reply", "ResultArea.kt"))
        val value = Regex("UTILITY_HITBOX_DP\\s*=\\s*(\\d+)").find(code)?.groupValues?.get(1)?.toInt()
        assertTrue("UTILITY_HITBOX_DP must be declared and >= 48, was $value", (value ?: 0) >= 48)
    }

    @Test
    fun `generate buttons are at least 48dp and clickable is not inset by padding`() {
        val action = codeOf(source("ui", "panel", "reply", "GenerationActionButton.kt"))
        assertTrue(
            "GenerationActionButton default height must meet the 48dp floor",
            Regex("MIN_TOUCH_TARGET_DP\\s*=\\s*(\\d+)").find(action)?.groupValues?.get(1)
                ?.let { it.toInt() >= 48 } == true
        )
        // 关键顺序：padding 出现在 clickable 之前会把热区缩掉，这是 §7.1 点名的写法
        val paddingAt = action.indexOf(".padding(vertical = Spacing.xs)")
        val clickableAt = action.indexOf(".clickable(")
        assertTrue("both must exist", paddingAt >= 0 && clickableAt >= 0)
        assertTrue(
            "clickable must come before the vertical padding, otherwise the hit box is shrunk",
            clickableAt < paddingAt
        )

        val trio = codeOf(source("ui", "panel", "reply", "ReplyPrimaryActions.kt"))
        val trioHeight = Regex("TRIO_HEIGHT_DP\\s*=\\s*(\\d+)").find(trio)?.groupValues?.get(1)?.toInt()
        assertTrue("primary action row must be >= 48dp, was $trioHeight", (trioHeight ?: 0) >= 48)
    }

    @Test
    fun `home trailing text action meets the touch floor`() {
        val code = codeOf(source("ui", "common", "RowAction.kt"))
        val min = Regex("MIN_HEIGHT_DP\\s*=\\s*(\\d+)").find(code)?.groupValues?.get(1)?.toInt()
        assertTrue("RowActionButton must be >= 48dp tall, was $min", (min ?: 0) >= 48)
        val clickableAt = code.indexOf(".clickable(")
        val insetAt = code.indexOf(".padding(vertical = RowActionDimens.VISUAL_VERTICAL_INSET_DP.dp)")
        assertTrue(
            "clickable must precede the visual inset",
            clickableAt in 0 until insetAt
        )
    }

    // ─── 文案资源化：资源必须真的被组件调用 ──────────────────────

    @Test
    fun `primary reply actions resolve their labels from resources not literals`() {
        val code = codeOf(source("ui", "panel", "reply", "ReplyPrimaryActions.kt"))
        listOf(
            "R.string.panel_generate_reply",
            "R.string.panel_generate_reply_with_count",
            "R.string.panel_generate_opening",
            "R.string.panel_retry",
            "R.string.panel_save_to_kb",
            "R.string.panel_stop"
        ).forEach {
            assertTrue("$it must be used by ReplyPrimaryActions", code.contains(it))
        }
        // 反向门禁：不允许再出现中文 UI 字面量
        val literals = Regex("""text\s*=\s*"[^"]*[一-龥][^"]*"""").findAll(code).map { it.value }.toList()
        assertEquals("no hardcoded CJK Text literals left: $literals", emptyList<String>(), literals)
    }

    @Test
    fun `panel header labels come from resources`() {
        val code = codeOf(source("ui", "panel", "PanelHeader.kt"))
        listOf(
            "R.string.panel_collapse",
            "R.string.panel_mode_reply",
            "R.string.panel_mode_suggest",
            "R.string.panel_mode_counseling"
        ).forEach { assertTrue("$it must be used by PanelHeader", code.contains(it)) }
    }

    @Test
    fun `loading button text comes from a formatted resource and carries a test tag`() {
        val code = codeOf(source("ui", "panel", "reply", "GenerationActionButton.kt"))
        assertTrue("loading text must use the resource", code.contains("R.string.panel_analysing_with_seconds"))
        assertTrue("loading text must expose a stable testTag", code.contains("GENERATE_STOP_TEST_TAG"))
    }

    @Test
    fun `every zh string has an en counterpart`() {
        val zh = File("src/main/res/values/strings.xml").takeIf { it.isFile }
            ?: File("app/src/main/res/values/strings.xml")
        val en = File("src/main/res/values-en/strings.xml").takeIf { it.isFile }
            ?: File("app/src/main/res/values-en/strings.xml")
        assertTrue("values-en/strings.xml must exist", en.isFile)
        val names = { f: File ->
            Regex("<string name=\"([^\"]+)\"").findAll(f.readText()).map { it.groupValues[1] }.toSet()
        }
        val missing = names(zh) - names(en)
        val extra = names(en) - names(zh)
        assertEquals("strings missing in values-en: $missing", emptySet<String>(), missing)
        assertEquals("values-en defines strings absent from values: $extra", emptySet<String>(), extra)
    }

    // ─── 死 API 不得复活 ───────────────────────────────────────────

    @Test
    fun `dead parameters removed by the audit do not come back`() {
        val actions = codeOf(source("ui", "panel", "reply", "ReplyPrimaryActions.kt"))
        assertTrue(
            "draftText was never read by ReplyPrimaryActions; it must not return",
            !Regex("""draftText\s*:""").containsMatchIn(actions)
        )
        val resultArea = codeOf(source("ui", "panel", "reply", "ResultArea.kt"))
        assertTrue(
            "onSaveToKb was dead inside ResultUtilityTrigger; the whole chain stays deleted",
            !resultArea.contains("onSaveToKb")
        )
    }
}
