package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 那一条：**真正被点到的那个节点**必须 ≥ 这一族的下限。
 *
 * 改之前实测到的红（本机，JVM 语义树）：三段的可点击盒子是
 * `84x18dp / 85x18dp / 85x18dp`——外层套了 48dp 的 Box，可点击仍挂在 20dp 胶囊里
 * 再减 1dp 内边距的子节点上。复核 第3节第2条 描述的就是这一处，本机第一次量到了。
 *
 * **本轮合同（第3节第2条）把这条窗口装饰带恢复到紧凑档**，于是这一格的尺跟着换档：
 * - 整行 30dp（模式栏外层），两段各占 1/2 宽 × **整行高**（第三段「今日锦囊」随  整删，
 *   `PanelHeader` 连 `showPlanPanel`/`onPlanVisibility` 两颗参数一起退场）；
 * - 齿轮与收起的外包盒 24dp 见方（字形分别 16dp / 20dp）；
 * - 全站那颗下限（`AppDimens.TOUCH_TARGET_MIN_DP` = 48）一处没动——这一格换的是
 *   **这一族的档**，不是把尺调松了事：档位写在调用点上（[TouchTier.PANEL_HEADER_HOTZONE]），
 *   下面另有一格按**绝对值**量 24 与 30，把外包盒缩成 20、把两段压回 18dp 高的坏实现照样红。
 *
 * 与 `androidTest/…/PanelHeaderTouchTargetsTest` 同一批断言、两把尺都留着：
 * 那一条在 CI 的 emulator 上跑（真设备 density），这一条在本机与 `testDebugUnitTest` 跑。
 * 两边用的是**同一组档位数值**——谁单独留在 48 上，两把尺就会互相打架。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelHeaderTouchTargetsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    /** 头部这一族走合同里那颗 24dp 外包盒档；全站下限那把尺不在这一条 30dp 的带上生效 */
    private val probe by lazy { SemanticsProbe(density, TouchTier.PANEL_HEADER_HOTZONE) }

    private fun mount(
        matrix: UiMatrix,
        panelMode: Int = 0,
        withSettings: Boolean = false
    ) {
        val openSettings: (() -> Unit)? = if (withSettings) ({ }) else null
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                PanelHeader(
                    panelMode = panelMode,
                    onModeChange = {},
                    onCollapse = {},
                    onOpenSettings = openSettings
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

    /** 第6节第5条：矩阵每一格里，可点击节点的边界都不得小于**这一族那一档**（头部 24dp 外包盒） */
    @Test
    fun `every clickable node meets the header tier across the width and font matrix`() {
        forEachMatrixCell { matrix ->
            probe.assertAllActionableMeetTouchFloor(rule, "PanelHeader", "（${matrix.id}）")
        }
    }

    /**
     * 本轮合同 第3节第2条 给这一条带的**绝对值**：整行 30dp、齿轮与收起外包盒 24dp 见方、模式两段均分。
     *
     * 为什么光有上一格不够：那一格只比大小（≥24），把整行钉回 48、把两段各撑成 48dp 高
     * 那种"看着更达标"的实现照样绿——而那正是本轮要拆掉的旧版式。
     * 这一格两头都判：矮过 24 红（热区缩水），高过 30 也红（把装饰带又撑回旧那一条厚顶栏）。
     * 判的是 boundsInRoot 的数值与两轴，不是"节点存不存在"。
     */
    @Test
    fun `the header band keeps the contract geometry in absolute numbers`() {
        mount(UiMatrix(412), withSettings = true)
        val targets = probe.actionableTargets(rule, "PanelHeader·绝对值")
        val segments = targets.filter { it.selected != null }
        // 第三段「今日锦囊」随  整删，头部只剩两段（PRODUCT_SPEC 第4节）
        assertEquals("两段切换应当是 2 个可交互节点：" + targets.joinToString { it.describe() }, 2, segments.size)
        val collapseLabel = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.panel_collapse)
        val gearLabel = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.settings_title)
        val collapse = targets.singleOrNull { it.label == collapseLabel }
        val gear = targets.singleOrNull { it.label == gearLabel }
        checkNotNull(collapse) { "收起那颗不在了：" + targets.joinToString { it.describe() } }
        checkNotNull(gear) { "齿轮那颗没画出来（onOpenSettings 传了 null 就不画，这一格传的是非 null）：" +
            targets.joinToString { it.describe() } }

        // 齿轮与收起：合同 24dp 见方（字形 16 / 20 是里面那颗，不在语义树里，另见报告）
        listOf(gear to "齿轮", collapse to "收起").forEach { (node, what) ->
            assertEquals("$what 的外包盒宽应当是合同那颗 24dp：" + node.describe(), 24f, node.widthDp, 0.6f)
            assertEquals("$what 的外包盒高应当是合同那颗 24dp：" + node.describe(), 24f, node.heightDp, 0.6f)
        }

        // 两段：各占 1/2 宽 × **整行高 30dp**（可点面积没丢，丢的是为了凑 48 撑出来的那条厚度）
        segments.forEach { seg ->
            assertEquals("模式段的高应当铺满整行那一档 30dp：" + seg.describe(), 30f, seg.heightDp, 0.6f)
        }
        val widths = segments.map { it.widthDp }
        assertEquals("两段必须等分（模式两段均分）：" + widths, 0.5f, widths.max() - widths.min(), 0.5f)
        assertTrue("两段每段都还得放得下一颗紧凑档热区：" + segments.joinToString { it.describe() },
            widths.min() >= TouchTier.PANEL_HEADER_HOTZONE)

        // 整条带自己：不许被撑回旧版那条 48dp 厚顶栏
        val band = targets.maxOf { it.topDp + it.heightDp }
        assertTrue(
            "头部整行的底边落在 ${band}dp，合同那一档是 30dp——把这一条撑回 48 就是本轮要拆的旧版式：" +
                targets.joinToString { it.describe() },
            band <= 30f + 0.6f
        )
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
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(412).RenderIn(deviceDensity) {
                PanelHeader(
                    panelMode = panelMode.value,
                    onModeChange = {},
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

        val replyLabel = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.panel_mode_reply)
        val counselingLabel = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.panel_mode_counseling)
        assertEquals("初始（回复模式）应选中第一段", replyLabel, selectedLabel())
        rule.runOnIdle { panelMode.value = 1 }
        assertEquals("切到谈心应选中第二段", counselingLabel, selectedLabel())
    }

    /**
     * 第6节第5条 的 role 一栏：两段是一组互斥标签页，不是两个普通按钮。
     * 不带 role 时 TalkBack 只念「按钮」，听的人不知道这是一组切换。
     */
    @Test
    fun `mode segments announce themselves as tabs not plain buttons`() {
        mount(UiMatrix(412))
        val segments = probe.actionableTargets(rule, "PanelHeader").filter { it.selected != null }
        assertEquals("两段切换应当是 2 个可交互节点", 2, segments.size)
        probe.assertSelectableAnnounceState(segments, "两段模式切换")
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
