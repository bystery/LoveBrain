package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.RenderIn
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
 * §6.1 / §6.3 的第一块组件：四态渲染器与它的空态版式。
 *
 * 断言全部读语义树——组件表对 `LbEmptyState` 写的是"动作热区 ≥48dp"，
 * 那就必须量到那个节点的边界，而不是在源码里搜 `heightIn`。
 * 本轮前面刚在同一台仪器上抓到两处"看着有热区、其实点不到"的（PanelHeader 18dp、
 * 空态蓝字 23dp），组件从第一天起就按量得着的方式写。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbAsyncStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun mount(
        matrix: UiMatrix = UiMatrix(360),
        state: ScreenState<String>,
        onContent: (String) -> Unit = {},
        onAction: () -> Unit = {}
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                LbAsyncState(state) { value -> onContent(value) }
            }
        }
    }

    private fun actionableCount(): Int =
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size

    @Test
    fun `loading shows the spinner and offers no action`() {
        mount(state = ScreenState.Loading)
        rule.onNodeWithTag(LbAsyncTags.LOADING).assertExists()
        assertEquals("加载中不该冒出可点的东西", 0, actionableCount())
    }

    @Test
    fun `empty without an action is a message and nothing else`() {
        mount(state = ScreenState.Empty(message = "暂无反馈案例"))
        rule.onNodeWithTag(LbAsyncTags.MESSAGE).assertExists()
        assertEquals(0, actionableCount())
    }

    /** §6.1 对空态动作的硬规定：≥48dp + 按钮角色 + 点了就执行 */
    @Test
    fun `the empty state action is a 48dp button that fires once`() {
        var fired = 0
        mount(
            state = ScreenState.Empty(
                message = "还没有聊天记录",
                action = ScreenAction("点这里发一条") { fired++ }
            )
        )
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "LbEmptyState")
        assertEquals(1, targets.size)
        assertEquals("点这里发一条", targets.single().label)
        assertEquals("动作必须带按钮角色：" + targets.single().describe(), "Button", targets.single().role)
        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        assertEquals(1, fired)
    }

    @Test
    fun `error state carries a retry action and says so in the message`() {
        var retried = 0
        mount(
            state = ScreenState.Error(
                message = "读不出反馈案例",
                retry = ScreenAction("重试") { retried++ }
            )
        )
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "LbAsyncState 错误态")
        assertEquals("错误态必须给一个重试入口：" + targets.joinToString { it.describe() }, 1, targets.size)
        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        assertEquals("重试必须真的被调一次", 1, retried)
    }

    @Test
    fun `content state renders the caller and invents no chrome`() {
        var shown: String? = null
        mount(state = ScreenState.Content("案例一"), onContent = { shown = it })
        assertEquals("案例一", shown)
        assertEquals("有内容时不该出现空态版式", 0, actionableCount())
        assertTrue(rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isEmpty())
    }

    /** 最坏那一格：最窄 + 最大字，热区不能缩 */
    @Test
    fun `the action keeps its floor at 320dp with 2x font`() {
        // 一个用例只能 setContent 一次，所以矩阵靠改 hoisted 值换格子（顺便也测了换配置后重测）
        val cell = androidx.compose.runtime.mutableStateOf(UiMatrix.FULL.first())
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            cell.value.RenderIn(deviceDensity) {
                LbAsyncState(
                    ScreenState.Empty("还没有聊天记录", ScreenAction("点这里发一条") {})
                ) { }
            }
        }
        for (matrix in UiMatrix.FULL) {  // §6.5：4 宽 × 3 字 = 12 格全跑（320/360/412/600 × 1.0/1.3/2.0）
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            probe.assertAllActionableMeetTouchFloor(rule, "LbEmptyState", "（${matrix.id}）")
        }
    }
}
