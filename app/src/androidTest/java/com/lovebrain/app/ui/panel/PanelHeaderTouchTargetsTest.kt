package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * （第3节第2条）：面板头的可点击节点必须用**语义树实测**，不许靠源码里搜常量。
 *
 * 面板头那一排是设计基线明写的**例外档**（HeaderDimens.ROW_HEIGHT_DP = 30，
 * 齿轮/收起 24dp 容器）——它们有意低于全站 48dp 下限。JVM 那一侧的
 * `PanelHeaderTouchTargetsTest`（test/）按 24dp 档判并钉死 30dp 整行高，
 * 那颗全站下限本身没动，仍由 `LbPrimaryButtonStateTest` 等钉着。
 *
 * 这里读的是**组合并测量之后**每个带点击语义节点的 boundsInRoot，
 * 并按面板头那一档（24dp）判——不再拿 48dp 硬套这一排。
 */
@RunWith(AndroidJUnit4::class)
class PanelHeaderTouchTargetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    /** 面板头例外档：24dp（与 JVM 那一侧的 TouchTier.PANEL_HEADER_HOTZONE 同数） */
    private val minPx: Float get() = 24f * density

    private fun mount(mode: Int = 0) {
        composeRule.setContent {
            PanelHeader(
                panelMode = mode,
                onModeChange = {},
                onCollapse = {}
            )
        }
    }

    private fun clickableNodes(): List<SemanticsNode> =
        composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    private fun describe(node: SemanticsNode): String {
        val b = node.boundsInRoot
        val label = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("+")
            ?: "（无文案也无 contentDescription）"
        val role = node.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "无 Role"
        val selected = node.config.getOrNull(SemanticsProperties.Selected)
        return "「$label」 role=$role selected=$selected " +
            "尺寸 ${(b.width / density).toInt()}x${(b.height / density).toInt()}dp " +
            "@(${b.left.toInt()},${b.top.toInt()})"
    }

    @Test
    fun everyClickableNodeInPanelHeaderMeetsThePanelHeaderTier() {
        mount()
        val nodes = clickableNodes()
        assertTrue(
            "PanelHeader 里一个可点击节点都没有——那这条测试就成了空过。" +
                "要么生产把入口删了，要么点击语义没挂上，两种都是事故。",
            nodes.isNotEmpty()
        )
        val tooSmall = nodes.filter {
            it.boundsInRoot.width < minPx - 0.5f || it.boundsInRoot.height < minPx - 0.5f
        }
        if (tooSmall.isNotEmpty()) {
            fail(
                "有 ${tooSmall.size} 个可点击节点小于 ${minPx / density}dp" +
                    "（设备 density=$density，阈值 ${minPx.toInt()}px）：\n" +
                    tooSmall.joinToString("\n") { "  " + describe(it) } +
                    "\n  修法：把 clickable 提到那个外层盒子上；" +
                    "只放大容器而点击仍挂在子节点上，等于没改。"
            )
        }
    }

    /** 两段模式切换是面板最主路径的入口；选中态必须能被读屏说出来 */
    @Test
    fun modeSegmentsAnnounceSelectionToAccessibilityServices() {
        mount(mode = 0)
        val withSelection = clickableNodes().filter { node ->
            node.config.contains(SemanticsProperties.Selected) ||
                node.config.contains(SemanticsProperties.StateDescription)
        }
        assertTrue(
            "两段模式切换没有任何节点带 selected / stateDescription 语义：" +
                "TalkBack 用户听得到「点了哪一段」，但听不到「现在在哪一段」。" +
                "可点击段需要 selected = (当前模式 == 该段)，或给一个 stateDescription。",
            withSelection.isNotEmpty()
        )
    }

    /** 纯图标控件（收起那颗；锦囊开关随  整删）必须有 contentDescription，否则读屏只念「按钮」 */
    @Test
    fun everyClickableNodeIsLabeledForTalkBack() {
        mount()
        val unlabeled = clickableNodes().filter { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)
            text.isNullOrBlank() && desc.isNullOrEmpty()
        }
        if (unlabeled.isNotEmpty()) {
            fail(
                "有 ${unlabeled.size} 个可点击节点既没有文案也没有 contentDescription，" +
                    "读屏只会念「按钮」：\n" +
                    unlabeled.joinToString("\n") { "  " + describe(it) }
            )
        }
    }
}
