package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.ReplyCardLayout
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页「回复卡片」那一行的读数合同：**两档都是选项、画在一条浅底轨道里，不是开 / 关**。
 *
 * 替代关系（旧形状先是"组名 + 一行『回复卡片纵向排列』+ 一颗开关"，10-11 那一版又错画成"两格各自带描边的独立盒"）：
 * · 现在只有一行，左边「回复卡片」、右边一条轨道里 `上下 | 左右` 两颗互斥单选；
 * · 「纵向排列」那一串字与组名那一行都从屏上消失——它们是把一个方向说成"开不开"的那件事；
 * · 开关那一族的语义（`ToggleableState`）一颗都不许留；
 * · 整行只许有**一颗**轨道容器（`settings-pill-reply`），两格都落在它之内——两格各画各的独立描边盒
 *   那种画法没有那颗共享轨道，节点数 0 就当场红。
 *
 * 偏好那一侧不在这一格重钉：方向的真值是 `ReplyCardLayout` 那一颗枚举（默认纵向），
 * 落盘与脏值回落由 `ui/panel/reply/ReplyCardLayoutTest` 判；
 * 这里只判"这一行把两档画成了两档、点哪一档就把哪一档的方向交出去、两档同处一条轨道"。
 *
 * 文案锚点一律 `getString` 现取（这台机器的环境解析出来是英文，写死中文会假红）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsReplyLayoutEntryTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private val rowLabel: String get() = ctx.getString(R.string.settings_reply_card_row_label)

    /** 两档标签，顺序与那一行画出来的顺序一致：上下在前（默认档），左右在后 */
    private val optionLabels: List<String>
        get() = listOf(
            ctx.getString(R.string.settings_reply_layout_vertical),
            ctx.getString(R.string.settings_reply_layout_horizontal)
        )

    private fun chip(label: String): SemanticsMatcher = hasTestTag(LbTags.CHIP) and hasText(label)

    private fun tabs(): List<SemanticsProbe.Target> =
        probe.laid(probe.actionableTargets(rule, "回复卡片那一行")).filter { it.role == "Tab" }

    /** 轨道/行根那两颗只有 testTag、没有点击的容器：按未合并树读，各恰一颗 */
    private fun tagTarget(tag: String): SemanticsProbe.Target {
        val nodes = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("tag=$tag 的节点必须恰有一颗，实到 ${nodes.size}", 1, nodes.size)
        return probe.of(nodes.single())
    }

    private fun centerOf(target: SemanticsProbe.Target): Float = target.topDp + target.heightDp / 2f

    private fun assertOption(label: String, wantSelected: Boolean) {
        val nodes = rule.onAllNodes(chip(label)).fetchSemanticsNodes()
        assertEquals("「$label」那一档该恰有一颗芯片，实到 ${nodes.size}", 1, nodes.size)
        assertEquals(
            "「$label」的选中态（同一时刻只许一档亮）",
            wantSelected,
            nodes.single().config.getOrNull(SemanticsProperties.Selected)
        )
    }

    private fun mount(
        vertical: MutableState<Boolean>,
        widthDp: Int = ROW_WIDTH_DP,
        onChange: (Boolean) -> Unit
    ) {
        rule.setContent {
            UiMatrix(widthDp).RenderIn(LocalDensity.current.density) {
                SettingsReplyLayoutEntry(vertical = vertical.value, onVerticalChange = onChange)
            }
        }
        rule.waitForIdle()
    }

    /**
     * 两档各点一次：写口收到的必须是**那一档的方向**（点「左右」= 交 `false`、点「上下」= 交 `true`），
     * 而亮的那一档当场跟着手指走。宿主那一份读数是可观察的，所以选中态跟着读数移动就是真实那一屏的形状。
     * 这一格红在两种形状上：多出一颗切换状态（把选项说成开/关）、点「左右」交出去的是"关掉纵向"而不是"换成横向"。
     */
    @Test
    fun `both directions are selectable options and tapping one hands over that direction`() {
        val writes = mutableListOf<Boolean>()
        val shown = mutableStateOf(true)
        mount(shown) { vertical ->
            writes += vertical
            shown.value = vertical
        }

        // 默认纵向 = 「上下」亮着（盘上读不到时的回落也是这一档，两处同一句话）
        assertEquals("默认档必须是纵向", ReplyCardLayout.VERTICAL, ReplyCardLayout.DEFAULT)
        assertOption(optionLabels[0], true)
        assertOption(optionLabels[1], false)
        assertEquals("两档恰好两颗互斥单选：" + tabs().joinToString { it.describe() }, 2, tabs().size)

        rule.onAllNodes(chip(optionLabels[1]))[0].performClick()
        rule.waitForIdle()
        assertEquals("点「左右」交出去的是横向那一档的方向，不是『把纵向关掉』", listOf(false), writes.toList())
        assertOption(optionLabels[1], true)
        assertOption(optionLabels[0], false)

        rule.onAllNodes(chip(optionLabels[0]))[0].performClick()
        rule.waitForIdle()
        assertEquals("再点「上下」换回纵向那一档", listOf(false, true), writes.toList())
        assertOption(optionLabels[0], true)
        assertOption(optionLabels[1], false)
        assertEquals("换了档也仍然只许一档亮", 1, tabs().count { it.selected == true })
    }

    /**
     * 旧形状彻底离场：组名那一行与「纵向排列 + 开关」都不在屏上，语义树里也没有一颗切换状态（开关那一族）。
     */
    @Test
    fun `the row offers no group title and no switch anywhere`() {
        mount(mutableStateOf(true)) {}

        val seen = probe.actionableTargets(rule, "回复卡片那一行")
        val toggles = seen.filter { it.isToggle }
        assertTrue(
            "两档是选项、不是开/关：语义树里不许出现切换状态的节点，实到 " +
                toggles.joinToString { it.describe() },
            toggles.isEmpty()
        )
        // 旧的那一句「回复卡片纵向排列」与旧组名「回复显示」都已被这一行的短标签替代
        rule.onAllNodes(hasText(ctx.getString(R.string.settings_card_vertical_label))).assertCountEquals(0)
        rule.onAllNodes(hasText(ctx.getString(R.string.settings_group_reply))).assertCountEquals(0)
        // 行标签恰有一颗，而且就是「回复卡片」
        rule.onAllNodes(hasText(rowLabel)).assertCountEquals(1)
    }

    /**
     * **一条浅底轨道、两格透明、只一颗轨道容器**（不是两格各自带描边的独立盒，也不是开关）。
     *
     * 反例（10-11 那一版那颗真实坏形状）：两格各画各的独立描边盒 = 没有那颗共享轨道 ⇒
     * `tagTarget(SETTINGS_REPLY_PILL_TAG)` 第一句就在 0 上红。
     */
    @Test
    fun `the two options sit inside one shared track instead of two separate boxes`() {
        mount(mutableStateOf(true)) {}

        tagTarget(SETTINGS_REPLY_ROW_TAG)
        val track = tagTarget(SETTINGS_REPLY_PILL_TAG)

        val chips = tabs()
        assertEquals("两档恰好两颗：" + chips.joinToString { it.describe() }, 2, chips.size)
        chips.forEach {
            assertTrue(
                "这一格掉到了轨道之外（各自一盒的坏形状）：" + it.describe() + " / 轨道 " + track.describe(),
                it.leftDp >= track.leftDp - HIT_TOLERANCE_DP && it.rightEdge <= track.rightEdge + HIT_TOLERANCE_DP
            )
            assertTrue(
                "这一格的高度中心不在轨道里（没和轨道同排）：" + it.describe(),
                kotlin.math.abs(centerOf(it) - centerOf(track)) <= HIT_TOLERANCE_DP
            )
        }
        // 一颗默认亮、另一颗灭（选中档 = 宿主交下来的那份读数 vertical=true）
        assertOption(optionLabels[0], true)
        assertOption(optionLabels[1], false)
    }

    /**
     * **同一行、竖向命中不缩、显示不铺满、轨道贴右缘、三档悬浮窗宽都不溢出、不压住标签、不是开关**。
     */
    @Test
    fun `the track hugs the right edge and fits every overlay width without crushing the label`() {
        val width = mutableIntStateOf(ROW_WIDTH_DP)
        val vertical = mutableStateOf(true)
        rule.setContent {
            UiMatrix(width.intValue).RenderIn(LocalDensity.current.density) {
                SettingsReplyLayoutEntry(vertical = vertical.value, onVerticalChange = {})
            }
        }
        rule.waitForIdle()

        OVERLAY_WIDTHS_DP.forEach { w ->
            rule.runOnIdle { width.intValue = w }
            rule.waitForIdle()

            val chips = tabs()
            assertEquals("宽 $w 下两档仍恰两颗", 2, chips.size)
            val track = tagTarget(SETTINGS_REPLY_PILL_TAG)

            chips.forEach {
                assertTrue(
                    "宽 $w：高度轴命中不到 ${TouchTier.SITE_FLOOR.toInt()}dp（只缩显示不许缩命中）：" + it.describe(),
                    it.heightDp >= TouchTier.SITE_FLOOR - HIT_TOLERANCE_DP
                )
                assertTrue(
                    "宽 $w：这一档窄过了最小格宽：" + it.describe(),
                    it.widthDp >= SettingsRowDimens.PILL_CELL_MIN_WIDTH_DP - HIT_TOLERANCE_DP
                )
            }

            assertEquals("宽 $w：轨道没贴到行右缘", w.toFloat(), track.rightEdge, HIT_TOLERANCE_DP)
            assertTrue("宽 $w：轨道溢出/铺满了整行：" + track.describe(), track.rightEdge <= w + HIT_TOLERANCE_DP)
            assertTrue("宽 $w：轨道铺满整行、没给标签留位置：" + track.describe(),
                track.widthDp <= w - MIN_LABEL_ROOM_DP)

            val labels = rule.onAllNodes(hasText(rowLabel)).fetchSemanticsNodes()
            assertEquals("宽 $w：行标签「$rowLabel」必须恰有一颗，实到 ${labels.size}", 1, labels.size)
            val label = probe.of(labels.single())
            assertTrue(
                "宽 $w：标签与轨道不在同一行：" + label.describe() + " / " + track.describe(),
                kotlin.math.abs(centerOf(label) - centerOf(track)) <= HIT_TOLERANCE_DP
            )
        }

        // 这一行没有开关那一族：屏上不许出现切换状态节点
        val toggles = probe.actionableTargets(rule, "回复卡片那一行").filter { it.isToggle }
        assertTrue("这一行不许有开关：" + toggles.joinToString { it.describe() }, toggles.isEmpty())
    }

    private val SemanticsProbe.Target.rightEdge: Float get() = leftDp + widthDp

    companion object {
        /** 悬浮窗设置页最窄的一档 */
        private const val ROW_WIDTH_DP = 260

        /** 三档悬浮窗宽度：轨道在每一档都不许溢出、不许压住标签 */
        private val OVERLAY_WIDTHS_DP = listOf(260, 300, 350)

        /** 轨道右侧之外必须留给标签的最小宽度（dp） */
        private const val MIN_LABEL_ROOM_DP = 40f

        /** 语义树边界的读数容差（dp） */
        private const val HIT_TOLERANCE_DP = 1.5f
    }
}
