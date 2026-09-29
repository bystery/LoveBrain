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

    /**
     * 换了**容器**不许顺便换那颗动作（§6.1「同一形状只有一处画法」在语义树侧的证人）。
     *
     * 为什么要有这一格：`LbEmptyState` 加了 `LbStateContainer.Strip` 之后，两档容器各画各的
     * 说明与动作。语义树读不出底色与圆角（那是截图那一格的事），读得出**这一颗点不点得到、
     * 报不报得出自己是按钮**——那正是这一族每次都长歪的地方（`LbTextAction.kt` 记着的两发：
     * 只垫高度量出 40x48dp、行内那颗只垫高度量出 32x48dp）。
     * 所以这一格判"两档容器的动作节点**同一个尺寸、同一个角色**"：
     * 有人把 Strip 那一档的动作换成另一颗自画胶囊（哪怕它看起来一模一样），
     * 只要热区或角色不同，这里就红。
     */
    @Test
    fun `switching the container must not switch the action's floor or role`() {
        val container = androidx.compose.runtime.mutableStateOf(LbStateContainer.Block)
        rule.setContent {
            UiMatrix(360, 400).RenderIn(LocalDensity.current.density) {
                LbEmptyState(
                    message = "还没有聊天记录",
                    action = ScreenAction("点这里发一条") {},
                    container = container.value
                )
            }
        }
        val sizes = LinkedHashMap<String, Pair<Float, Float>>()
        for (tier in listOf(LbStateContainer.Block, LbStateContainer.Strip)) {
            rule.runOnIdle { container.value = tier }
            rule.waitForIdle()
            val target = probe.actionableTargets(rule, "LbEmptyState·${tier.name}").single()
            sizes[tier.name] = target.widthDp to target.heightDp
            assertEquals(
                "${tier.name} 那一档的动作也得报按钮角色：" + target.describe(),
                "Button", target.role
            )
            assertTrue(
                "${tier.name} 那一档也得两轴都 ≥${probe.floorDp.toInt()}dp：" + target.describe(),
                !target.tooSmall(probe.floorDp)
            )
        }
        val (blockW, blockH) = sizes.getValue("Block")
        val (stripW, stripH) = sizes.getValue("Strip")
        assertEquals("两档容器的动作热区宽度不一致：$blockW vs $stripW", blockW, stripW, 0.5f)
        assertEquals("两档容器的动作热区高度不一致：$blockH vs $stripH", blockH, stripH, 0.5f)
    }
}
