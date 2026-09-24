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
 * P1-02（独立复核 §3.2）：48dp 必须用**语义树实测**，不许再靠源码里搜常量。
 *
 * 报告点名的就是这里：`ProductionUiContractTest` 只检查 PanelHeader.kt 里出现过
 * `height(HeaderDimens.MIN_TOUCH_TARGET_DP.dp)`，可是真正 clickable 的那三段
 * 挂在内层 20dp 高的 ModeSegmentLabel 上——于是"合同通过"和"用户点得到"是两件事，
 * 而 CI 信了前者。
 *
 * 这里读的是**组合并测量之后**每个带点击语义节点的 boundsInRoot。
 * 生产真不达标时它就该红，那正是要的信号，不是要绕过去的噪声。
 *
 * 三条都会先在 CI 上跑（本机无 system image）。预期结果不是"绿"，而是
 * "把生产到底达不达标说清楚"：如果 PanelHeader 真的把点击挂在 20dp 的子节点上，
 * 第一条就该红，红得有价值。
 */
@RunWith(AndroidJUnit4::class)
class PanelHeaderTouchTargetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    /** 复核 §6.5：所有 clickable/toggleable 的可点击框不得小于 48×48dp */
    private val minPx: Float get() = 48f * density

    private fun mount(mode: Int = 0, planShown: Boolean = false) {
        composeRule.setContent {
            PanelHeader(
                panelMode = mode,
                onModeChange = {},
                showPlanPanel = planShown,
                onPlanVisibility = {},
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
    fun everyClickableNodeInPanelHeaderMeetsThe48dpFloor() {
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
                "有 ${tooSmall.size} 个可点击节点小于 48dp" +
                    "（设备 density=$density，阈值 ${minPx.toInt()}px）：\n" +
                    tooSmall.joinToString("\n") { "  " + describe(it) } +
                    "\n  修法：把 clickable 提到那个 48dp 的外层盒子上；" +
                    "只放大容器而点击仍挂在子节点上，等于没改。"
            )
        }
    }

    /** 三段模式切换是面板最主路径的入口；选中态必须能被读屏说出来 */
    @Test
    fun modeSegmentsAnnounceSelectionToAccessibilityServices() {
        mount(mode = 0)
        val withSelection = clickableNodes().filter { node ->
            node.config.contains(SemanticsProperties.Selected) ||
                node.config.contains(SemanticsProperties.StateDescription)
        }
        assertTrue(
            "三段模式切换没有任何节点带 selected / stateDescription 语义：" +
                "TalkBack 用户听得到「点了哪一段」，但听不到「现在在哪一段」。" +
                "可点击段需要 selected = (当前模式 == 该段)，或给一个 stateDescription。",
            withSelection.isNotEmpty()
        )
    }

    /** 纯图标控件（收起、计划面板开关）必须有 contentDescription，否则读屏只念「按钮」 */
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
