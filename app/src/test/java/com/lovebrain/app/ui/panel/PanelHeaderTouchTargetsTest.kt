package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.core.testing.RenderIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1-02 的那一条：**真正被点到的那个节点**必须 ≥48dp。
 *
 * 改之前实测到的红（本机，JVM 语义树）：三段的可点击盒子是
 * `84x18dp / 85x18dp / 85x18dp`——外层套了 48dp 的 Box，可点击仍挂在 20dp 胶囊里
 * 再减 1dp 内边距的子节点上。复核 §3.2 P1-02 描述的就是这一处，本机第一次量到了。
 *
 * 与 `androidTest/…/PanelHeaderTouchTargetsTest` 同一批断言、两把尺都留着：
 * 那一条在 CI 的 emulator 上跑（真设备 density），这一条在本机与 `testDebugUnitTest` 跑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
class PanelHeaderTouchTargetsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun mount(matrix: UiMatrix, planShown: Boolean = false, panelMode: Int = 0) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                PanelHeader(
                    panelMode = panelMode,
                    onModeChange = {},
                    showPlanPanel = planShown,
                    onPlanVisibility = {},
                    onCollapse = {}
                )
            }
        }
    }

    /**
     * 一次挂载、逐个矩阵格地换约束。
     *
     * `setContent` 一个用例只能调一次，所以矩阵靠改 hoisted state 换宽度/字号，
     * 再让 Compose 重新测量——顺带也测到了"换配置后会不会留在旧尺寸"。
     */
    private fun forEachMatrixCell(
        cells: List<UiMatrix> = UiMatrix.FULL,
        body: (UiMatrix) -> Unit
    ) {
        val cell = androidx.compose.runtime.mutableStateOf(cells.first())
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            cell.value.RenderIn(deviceDensity) {
                PanelHeader(
                    panelMode = 0,
                    onModeChange = {},
                    showPlanPanel = false,
                    onPlanVisibility = {},
                    onCollapse = {}
                )
            }
        }
        for (matrix in cells) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            body(matrix)
        }
    }

    /** §6.5：矩阵每一格里，可点击节点的边界都不得小于 48×48dp */
    @Test
    fun `every clickable node meets the 48dp floor across the width and font matrix`() {
        forEachMatrixCell { matrix ->
            probe.assertAllActionableMeetTouchFloor(rule, "PanelHeader", "（${matrix.id}）")
        }
    }

    /**
     * 选中态跟着模式走：换一次模式，被报成选中的那一段必须跟着换。
     *
     * 用一份 hoisted state 驱动同一个组合（`setContent` 一个用例只能调一次），
     * 这样测的是"改输入之后语义会不会重算"，而不是"重新挂载一棵树"。
     */
    @Test
    fun `selection state follows the mode instead of staying on the first segment`() {
        val panelMode = androidx.compose.runtime.mutableStateOf(0)
        val planShown = androidx.compose.runtime.mutableStateOf(false)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(412).RenderIn(deviceDensity) {
                PanelHeader(
                    panelMode = panelMode.value,
                    onModeChange = {},
                    showPlanPanel = planShown.value,
                    onPlanVisibility = {},
                    onCollapse = {}
                )
            }
        }

        fun selectedLabel(): String {
            rule.waitForIdle()
            val segments = probe.actionableTargets(rule, "PanelHeader").filter { it.selected != null }
            val on = segments.filter { it.selected == true }
            assertEquals(
                "任何模式下都必须恰好一段被报成选中：" + segments.joinToString { it.describe() },
                1, on.size
            )
            return on.first().label
        }

        assertEquals("初始（回复模式）应选中第一段", "Reply", selectedLabel())
        rule.runOnIdle { planShown.value = true }
        assertEquals("切到锦囊应选中第二段", "Brief", selectedLabel())
        rule.runOnIdle { planShown.value = false; panelMode.value = 1 }
        assertEquals("切到谈心应选中第三段", "Talk it through", selectedLabel())
    }

    /**
     * §6.5 的 role 一栏：三段是一组互斥标签页，不是三个普通按钮。
     * 不带 role 时 TalkBack 只念「按钮」，听的人不知道这是一组切换。
     */
    @Test
    fun `mode segments announce themselves as tabs not plain buttons`() {
        mount(UiMatrix(412))
        val segments = probe.actionableTargets(rule, "PanelHeader").filter { it.selected != null }
        assertEquals("三段切换应当是 3 个可交互节点", 3, segments.size)
        probe.assertSelectableAnnounceState(segments, "三段模式切换")
        assertTrue(
            "role 应当是 Tab（TalkBack 才会念成标签页）：" +
                segments.joinToString("\n") { "  " + it.describe() },
            segments.all { it.role == "Tab" }
        )
    }

    /**
     * 大字号下热区不能缩。
     *
     * 这条不是凑数：`CONTROL_HEIGHT_DP=20` 的胶囊是写死的 dp，
     * 而文字是 sp——字体 2.0 时文字比胶囊还高，历史上就是把热区压扁、文字被裁的方向。
     */
    @Test
    fun `the 2x font matrix keeps both the floor and a readable label`() {
        forEachMatrixCell(UiMatrix.WIDTHS_DP.map { UiMatrix(widthDp = it, fontScale = 2.0f) }) { matrix ->
            val targets = probe.assertAllActionableMeetTouchFloor(rule, "PanelHeader", "（${matrix.id}）")
            probe.assertAllActionableLabeled(rule, "PanelHeader", "（${matrix.id}）")
            assertTrue(
                "${matrix.id} 下文字节点没测到，可能是 2.0x 字被裁没了：" +
                    targets.joinToString { it.describe() },
                targets.count { it.label.isNotBlank() } >= 3
            )
        }
    }
}
