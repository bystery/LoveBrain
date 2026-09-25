package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
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
 * §6.1 表里 `LbModalSheet/Dialog` 的 Sheet 半边：浮层里那些"点得下去的出口"合不合 §6.5。
 *
 * 这三条断言每一条都对着一个**量出来的**旧缺陷（旧形状实测值见 `SheetProbeTest` 的注释）：
 * ① 动作按钮 26dp / 22dp 高 → 现在下限 48dp；
 * ② `confirmLabel = ""` 画出一颗 24x22dp 的无名节点 → 现在标签为空的动**不入树**；
 * ③ 遮罩与"拦截点击"的卡片各挂一次 `clickable` → 现在改用 `pointerInput`，
 *    手势照拦，但不再对外声明"我是一颗没名字的按钮"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbModalSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private fun ComposeContentTestRule.mountSheet(actions: List<LbDialogAction>) {
        setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbModalSheet(onDismissRequest = {}) {
                    LbModalSheetTitle("SHEET_TITLE_SENTINEL")
                    Text("SHEET_BODY_SENTINEL")
                    LbModalSheetActions(actions)
                }
            }
        }
        mainClock.advanceTimeBy(16L)
    }

    private val twoActions = listOf(
        LbDialogAction("SHEET_MUTE", {}, tone = LbDialogActionTone.Muted),
        LbDialogAction("SHEET_OK", {})
    )

    /** §6.5 :531——浮层里每一颗出口都得过 48dp 下限（旧形状实测 26dp / 22dp） */
    @Test
    fun `every action in a sheet meets the touch floor`() {
        rule.mountSheet(twoActions)
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "面板浮层")
        assertEquals(
            "这个形状里可交互节点就该只有那两颗出口：" + targets.joinToString { it.describe() },
            listOf("SHEET_MUTE", "SHEET_OK"), targets.map { it.label }.sorted()
        )
        targets.forEach {
            assertEquals("${it.label} 的高度应当垫到下限", 48f, it.heightDp, 0.6f)
        }
    }

    /**
     * 遮罩与卡片**不该再是可交互节点**（旧形状里它们各占一个无名节点，
     * 其中一个还把标题合并进去、被读屏念成一颗按钮）。
     */
    @Test
    fun `the scrim and the card do not masquerade as buttons`() {
        rule.mountSheet(twoActions)
        val targets = probe.actionableTargets(rule, "面板浮层")
        val impostors = targets.filter { it.widthDp > 320f }
        assertTrue(
            "整块遮罩 / 整张卡片不该出现在可交互节点里：" + impostors.joinToString { it.describe() },
            impostors.isEmpty()
        )
        probe.assertAllActionableLabeled(rule, "面板浮层")
    }

    /** 旧缺陷 ②的直接反证：空标签的动根本不该入树 */
    @Test
    fun `an action with a blank label is not rendered at all`() {
        rule.mountSheet(
            listOf(
                LbDialogAction("SHEET_CANCEL", {}, tone = LbDialogActionTone.Muted),
                LbDialogAction("", {}),                    // 旧形状在这里画出一颗 24x22 的无名按钮
                LbDialogAction("   ", {})
            )
        )
        val targets = probe.actionableTargets(rule, "面板浮层")
        assertEquals(
            "三颗里只该留下一颗有名字的：" + targets.joinToString { it.describe() },
            listOf("SHEET_CANCEL"), targets.map { it.label }
        )
        assertTrue("不许有无名节点残留：" + targets.joinToString { it.describe() },
            targets.none { it.label.isBlank() })
    }

    /** 禁用是"灰着还在"：与 LbPrimaryButton、LbDialog 同一口径 */
    @Test
    fun `a disabled action stays present, labeled and announces itself disabled`() {
        rule.mountSheet(
            listOf(
                LbDialogAction("SHEET_CANCEL", {}, tone = LbDialogActionTone.Muted),
                LbDialogAction(label = "SHEET_SAVE", enabled = false, onClick = {})
            )
        )
        val targets = probe.actionableTargets(rule, "面板浮层")
        val save = targets.first { it.label == "SHEET_SAVE" }
        assertTrue("禁用那颗要带 Disabled 语义：" + save.describe(), save.disabled)
        assertEquals("禁用那颗也得有 48dp 热区", 48f, save.heightDp, 0.6f)
    }

    /** 标题与正文都得读得到——卡片不再合并语义之后，它们是自己那两个节点 */
    @Test
    fun `title and body are readable nodes`() {
        rule.mountSheet(twoActions)
        listOf("SHEET_TITLE_SENTINEL", "SHEET_BODY_SENTINEL").forEach { want ->
            val n = rule.onAllNodesWithText(want).fetchSemanticsNodes().size
            assertEquals("浮层里「$want」应当恰好一个节点，实到 $n", 1, n)
        }
    }
}
