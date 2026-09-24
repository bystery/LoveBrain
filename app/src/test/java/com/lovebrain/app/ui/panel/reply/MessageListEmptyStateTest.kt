package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 空态蓝字 = 主动发唯一入口。它此前有两个"看起来没事"的问题：
 *
 * 1. 热区只有文字那一行加 4dp 内边距（实测 ≈28dp 高），§2.3 里点名的就是这条
 *    （"空态蓝字也没有明确 48dp 最小高度/按钮角色"）；
 * 2. 两种模式的文案是写在 `Text(text = if (…) "中文" else "中文")` 里的，
 *    P1-05 那把正则尺看不见这种写法（它只认 `Text("…` / `Text(text = "…`）。
 *
 * 这里读语义树量真实边界，并验证"点一下只切模式"——回调恰好一次，没有别的东西被触发。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageListEmptyStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun mount(
        proactiveActive: Boolean,
        matrix: UiMatrix = UiMatrix(360),
        onClick: () -> Unit
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                MessageList(
                    messages = emptyList(),
                    editingIndex = -1,
                    onReorder = { _, _ -> },
                    onEdit = {},
                    onDelete = {},
                    onEmptyAction = onClick,
                    proactiveActive = proactiveActive
                )
            }
        }
    }

    /** 空态必须恰好有一个可点的东西：那个蓝字入口。多一个就是有人在空态里塞了第二个入口。 */
    @Test
    fun `the empty state offers exactly one actionable entry`() {
        var clicks = 0
        mount(proactiveActive = false) { clicks++ }
        val targets = probe.actionableTargets(rule, "消息列表空态")
        assertEquals(
            "空态应当只有一个可点击入口（蓝字主动发）：" + targets.joinToString { it.describe() },
            1, targets.size
        )
    }

    /** §6.5：这个入口的触摸区 ≥48dp，并且带按钮角色 */
    @Test
    fun `the empty state entry is a 48dp button not a bare line of text`() {
        mount(proactiveActive = false) {}
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "消息列表空态")
        assertEquals(
            "取到 1 个可点击节点才对：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        assertEquals(
            "role 必须是 Button，否则 TalkBack 不会告诉用户这是一处可点的操作：" + targets[0].describe(),
            "Button", targets[0].role
        )
    }

    /** 点一下只切模式：回调恰好一次 */
    @Test
    fun `tapping the empty state fires the mode switch exactly once`() {
        var clicks = 0
        mount(proactiveActive = false) { clicks++ }
        rule.onAllNodes(androidx.compose.ui.test.hasClickAction())[0].performClick()
        rule.waitForIdle()
        assertEquals("一次点击应当只调一次回调", 1, clicks)
    }

    /**
     * 两种模式的文案都必须来自资源（P1-05）。
     *
     * 断言写成"英文环境下念到的是英文"——资源没接上时这里会拿到中文，
     * 因为 values-en 里没有这个 key 就直接回落到默认（中文）文件。
     */
    @Test
    fun `both empty state wordings come from resources and switch with the mode`() {
        val active = androidx.compose.runtime.mutableStateOf(false)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(360).RenderIn(deviceDensity) {
                MessageList(
                    messages = emptyList(),
                    editingIndex = -1,
                    onReorder = { _, _ -> },
                    onEdit = {},
                    onDelete = {},
                    onEmptyAction = {},
                    proactiveActive = active.value
                )
            }
        }
        assertEquals(
            "未开启时该念「点这里发一条」",
            "No chat history yet — tap here to send one",
            probe.actionableTargets(rule, "消息列表空态").single().label
        )
        rule.runOnIdle { active.value = true }
        rule.waitForIdle()
        assertEquals(
            "开启后该念「点击关闭」",
            "Opener mode is on — tap to turn it off",
            probe.actionableTargets(rule, "消息列表空态").single().label
        )
    }
    /**
     * §6.5 的宽度 × 字体矩阵：空态这个入口在 12 格里都得保持 ≥48dp 且念得出来。
     *
     * 一个用例只能 setContent 一次，所以矩阵靠改 hoisted 的那格值来换尺寸。
     */
    @Test
    fun `the empty state entry keeps its floor across the full width and font matrix`() {
        val cell = androidx.compose.runtime.mutableStateOf(UiMatrix.FULL.first())
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            cell.value.RenderIn(deviceDensity) {
                MessageList(
                    messages = emptyList(),
                    editingIndex = -1,
                    onReorder = { _, _ -> },
                    onEdit = {},
                    onDelete = {},
                    onEmptyAction = {},
                    proactiveActive = false
                )
            }
        }
        val widths = LinkedHashMap<String, Float>()
        for (matrix in UiMatrix.FULL) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            val targets = probe.assertAllActionableMeetTouchFloor(rule, "消息列表空态", "（${matrix.id}）")
            probe.assertAllActionableLabeled(rule, "消息列表空态", "（${matrix.id}）")
            assertEquals("每一格都只有一个入口：" + matrix.id, 1, targets.size)
            widths[matrix.id] = targets.single().widthDp
        }
        // 这行是给「矩阵循环」本身兜底的：换配置如果没真的进组合（hoisted 值没生效、
        // 或 RenderIn 读的还是旧值），12 格会量出同一个尺寸，而上面每条断言都照样绿。
        assertTrue(
            "同一宽度下 2.0 倍字必须比 1.0 倍字宽，否则这 12 格其实是同一格：" + widths,
            checkNotNull(widths["360dp-font200"]) > checkNotNull(widths["360dp-font100"])
        )
    }
}