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

    private val dimensCode by lazy { codeOf(source("core", "designsystem", "Dimens.kt")) }

    /**
     * 读一颗尺寸常量的**数值**，允许它写成"指回全局那颗"的别名（顺着引用最多跳四跳）。
     *
     * 这一格原来写的是 `NAME = (\d+)`，也就是**要求每个文件自己把 48 再抄一遍**。
     * §6.5 那一格把 15 处抄数改成 `= AppDimens.TOUCH_TARGET_MIN_DP` 之后，
     * 本文件三条集体报 `was null`——那不是"尺寸变小了"，是"数不写在这儿了"。
     * 把它们改回抄一遍等于让两把尺互相打架，所以这里改成跟着引用走。
     *
     * ⚠ 读不出来仍然判失败（返回 null → 调用方那条 assertTrue 红）。
     * 这一格不因此变软：它从"这里必须写着 48"变成"这里必须能算出 ≥48"。
     */
    private fun dimenValue(name: String, code: String): Int? {
        var current = name
        var scope = code
        repeat(4) {
            val rhs = Regex("\\b$current\\s*=\\s*([\\w.]+)").find(scope)?.groupValues?.get(1) ?: return null
            if (rhs.isNotEmpty() && rhs.all { it.isDigit() }) return rhs.toInt()
            if (rhs.contains('.')) scope = dimensCode      // AppDimens.X → 去全局那颗里找
            current = rhs.substringAfterLast('.')
        }
        return null
    }

    /**
     * 全站那颗下限自己得是个 ≥48 的数。
     *
     * 上面那个"跟着引用读"的机制让所有页面都指回 Dimens.kt 这一处，
     * 于是**这一处就成了唯一承重点**：它要是被改成 32，全仓库的静态尺会集体变绿而集体不达标。
     * 所以这里把它单独钉住——这一条不跟着引用走，读的就是那个字面量。
     */
    @Test
    fun `the global touch floor itself is declared at least 48dp`() {
        val declared = Regex("\\bTOUCH_TARGET_MIN_DP\\s*=\\s*(\\d+)").find(dimensCode)?.groupValues?.get(1)
        assertTrue("Dimens.kt 里必须把下限写成字面量（它是全站唯一抄数的那一处）", declared != null)
        assertTrue("下限必须 >=48dp，实到 $declared", declared!!.toInt() >= 48)
    }

    @Test
    fun `panel header collapse hotzone is at least 48dp`() {
        val code = codeOf(source("ui", "panel", "PanelHeader.kt"))
        val dp = dimenValue("COLLAPSE_HOTZONE_DP", code)
        assertTrue("COLLAPSE_HOTZONE_DP must exist and be >= 48dp, was $dp", dp != null && dp >= 48)
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
        val value = dimenValue("UTILITY_HITBOX_DP", code)
        assertTrue("UTILITY_HITBOX_DP must resolve to a number >= 48, was $value", value != null && value >= 48)
    }

    /**
     * 主动作的 48dp 与"padding 不许排在 clickable 前面"。
     *
     * ⚠ 这一格是**源码级**的，只证明"常量写着 48、顺序没排反"；
     * 判"点得到点不到"的权威断言在语义树那边（`LbPrimaryButtonStateTest` 与
     * `ReplyPrimaryActionsContractTest` 真读 `boundsInRoot`）。别拿这格当已验证行为
     * ——独立复核 P1-02 点名的就是这个误读，而坑表 58 那条更是同一族：
     * 这一格原先 grep 的是 `ui/panel/reply/GenerationActionButton.kt`，
     * 组件搬进 `core/designsystem` 后它还绿着指旧路径，就会变成"看着在、其实不存在"。
     */
    @Test
    fun `generate buttons are at least 48dp and clickable is not inset by padding`() {
        val action = codeOf(source("core", "designsystem", "LbPrimaryButton.kt"))
        assertTrue(
            "LbPrimaryButton 的高度下限必须 >=48dp（可以是指回全局那颗的别名）",
            dimenValue("LB_PRIMARY_MIN_HEIGHT_DP", action)?.let { it >= 48 } == true
        )
        // 关键顺序：padding 出现在 clickable 之前会把热区缩掉，这是 §7.1 点名的写法
        val paddingAt = action.indexOf(".paddingInside()")
        val clickableAt = action.indexOf(".clickable(")
        assertTrue("both must exist", paddingAt >= 0 && clickableAt >= 0)
        assertTrue(
            "clickable 必须排在内边距之前，否则热区被削掉",
            clickableAt < paddingAt
        )
        // 四态各一条链：只有一条的话，"新加的那一态忘了垫内边距"这格就抓不到。
        // 必须锚在"换行 + 缩进 + 点"上：定义那一行写的是 `Modifier.paddingInside()`，
        // 只找 `.paddingInside(` 会把它也数进去（4 报成 5——本仓库第 4 号坑的老形状）。
        // ⚠ 这条链的名字跟着组件改过一次（`paddingVerticalInside` → `paddingInside`，
        // 因为**横向**内边距也归它了）；名字里带"方向"的私有函数一旦改了口径，
        // 这种源码级 grep 就要跟着改——所以权威判据在语义树那两格，这格只挡顺序排反。
        assertEquals(
            "四态各自走一遍这条链",
            4, Regex("\n\\s+\\.paddingInside\\(\\)").findAll(action).count()
        )
        // 调用方不再持有高度旋钮：`heightDp` 只能把按钮改高、改不矮，是个不存在的自由度
        assertTrue(
            "heightDp 这个死参数不许回来",
            !Regex("heightDp\\s*:").containsMatchIn(action)
        )
    }

    @Test
    fun `home trailing text action meets the touch floor`() {
        val code = codeOf(source("ui", "common", "RowAction.kt"))
        val min = dimenValue("MIN_HEIGHT_DP", code)
        assertTrue("RowActionButton must resolve to >= 48dp tall, was $min", min != null && min >= 48)
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

    /**
     * "生成中"那串可见文案：资源驱动 + 有稳定锚点。
     *
     * 这一格原来只 grep 一颗组件文件（文案与 tag 都在按钮里），§6.1 把壳搬进
     * `core/designsystem` 后两件事分家了：**说什么**在 reply 层（`GeneratingLabel.kt`），
     * **怎么画**在设计系统（`LbPrimaryButton.kt`）。所以判据也跟着拆两处——
     * 留着一格指旧路径的 grep，它就成了一把只会绿的尺（坑表 58）。
     */
    @Test
    fun `loading button text comes from a formatted resource and carries a test tag`() {
        val label = codeOf(source("ui", "panel", "reply", "GeneratingLabel.kt"))
        assertTrue("生成中那句必须走资源", label.contains("R.string.panel_analysing_with_seconds"))
        assertTrue(
            "阶段词也不许写死在代码里",
            listOf(
                "R.string.panel_phase_analysing",
                "R.string.panel_phase_drafting",
                "R.string.panel_phase_deep_analysing"
            ).all { label.contains(it) }
        )
        val button = codeOf(source("core", "designsystem", "LbPrimaryButton.kt"))
        assertTrue(
            "Loading 那颗标签必须带停止锚点（文字会变，tag 不会）",
            button.contains("LbTags.PRIMARY_STOP")
        )
    }

    @Test
    fun `every zh string has an en counterpart`() {
        val zh = File("src/main/res/values/strings.xml").takeIf { it.isFile }
            ?: File("app/src/main/res/values/strings.xml")
        val en = File("src/main/res/values-en/strings.xml").takeIf { it.isFile }
            ?: File("app/src/main/res/values-en/strings.xml")
        assertTrue("values-en/strings.xml must exist", en.isFile)
        // ⚠ 键表同时收 `<string name=` 与 `<plurals name=`：这一格原先只认前者，
        // 而 `feedback_case_count`（页头那句"N 条"，账本 §58）是复数档——
        // 只认 `<string` 的话，plurals 少翻一边照样绿，正是这一格要防的那件事。
        val names = { f: File ->
            Regex("<(?:string|plurals) name=\"([^\"]+)\"")
                .findAll(f.readText()).map { it.groupValues[1] }.toSet()
        }
        val missing = names(zh) - names(en)
        val extra = names(en) - names(zh)
        assertEquals("strings missing in values-en: $missing", emptySet<String>(), missing)
        assertEquals("values-en defines strings absent from values: $extra", emptySet<String>(), extra)
        assertTrue(
            "两边键数必须相等（实到 zh=${names(zh).size} en=${names(en).size}）——" +
                "上面两条差集判空之外，再钉一颗总量证人：两边同时少同一批键时差集也是空的",
            names(zh).size == names(en).size
        )
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
