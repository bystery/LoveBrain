package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
 * §6.1 表里 `LbModalSheet/Dialog` 的 Dialog 半边：**浮层里那颗用户要点到的按钮，到底点不点得到**。
 *
 * 这一格存在的理由是一个**量出来的缺陷**：`DialogProbeTest` 在同一台仪器上量到
 * Material `AlertDialog` 里的 `TextButton` 只有 **188x40dp**，而 §6.5 :531 的下限是 48dp。
 * 仓库原先 11 个浮层的"确定/取消/删除"全是那个 40dp 的形状，
 * 而之前那把 48dp 的尺**从没往对话框里看过**——它扫的是页面里的节点，对话框是另一扇窗。
 * `LbDialog` 给动作垫到 48dp，下面这几格逐颗读 `boundsInRoot` 钉住它。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private fun ComposeContentTestRule.mountSample() {
        setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbDialog(
                    title = "DLG_TITLE_SENTINEL",
                    onDismissRequest = {},
                    message = "DLG_MESSAGE_SENTINEL",
                    confirm = LbDialogAction(label = "DLG_CONFIRM", onClick = {}),
                    secondary = listOf(LbDialogAction(label = "DLG_SECONDARY", onClick = {})),
                    dismiss = LbDialogAction(label = "DLG_DISMISS", tone = LbDialogActionTone.Muted, onClick = {})
                )
            }
        }
        mainClock.advanceTimeBy(16L)
    }

    /** 浮层里的每一颗动作都必须过 §6.5 的下限——这一格就是那条 40dp 缺陷的直接反证 */
    @Test
    fun `every action in a dialog meets the touch floor`() {
        rule.mountSample()
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "对话框")
        val labels = targets.map { it.label }.sorted()
        assertEquals(
            "三颗出口都该在，且是同一颗组件画的：" + targets.joinToString { it.describe() },
            listOf("DLG_CONFIRM", "DLG_DISMISS", "DLG_SECONDARY"),
            labels
        )
        targets.forEach {
            assertEquals("${it.label} 的高度应当正好垫到下限", 48f, it.heightDp, 0.6f)
        }
    }

    /** 读屏得说出每颗是什么，而且只说一遍 */
    @Test
    fun `dialog actions are labeled and not announced twice`() {
        rule.mountSample()
        val targets = probe.assertAllActionableLabeled(rule, "对话框")
        probe.assertNoDuplicatedAnnouncement(rule, "对话框")
        targets.forEach {
            assertEquals("${it.label} 的角色", "Button", it.role)
        }
    }

    /** 标题与正文是**看得见的节点**：合并语义把 text 槽整个吞掉时，读屏就只剩按钮 */
    @Test
    fun `title and body are both readable nodes`() {
        rule.mountSample()
        rule.onNodeWithTextCompat("DLG_TITLE_SENTINEL")
        rule.onNodeWithTextCompat("DLG_MESSAGE_SENTINEL")
    }

    /**
     * "显示名不许为空"那类表单对话框靠 `enabled` 表达"现在还不能提交"。
     * 禁用必须是**灰着还在**，不能消失——否则用户不知道少填了什么。
     */
    @Test
    fun `a disabled action stays in place and announces itself disabled`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbDialog(
                    title = "DLG_FORM_TITLE",
                    onDismissRequest = {},
                    confirm = LbDialogAction(label = "DLG_SAVE", enabled = false, onClick = {}),
                    dismiss = LbDialogAction(label = "DLG_CANCEL", onClick = {})
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        val targets = probe.actionableTargets(rule, "表单对话框")
        val save = targets.first { it.label == "DLG_SAVE" }
        assertTrue("禁用那颗必须带 Disabled 语义：" + save.describe(), save.disabled)
        assertEquals("禁用那颗也得有 48dp 热区（灰着 ≠ 可以小）", 48f, save.heightDp, 0.6f)
    }

    /**
     * 次级出口上限：>3 颗就说明这不该是个对话框（该走 Sheet 或独立 screen）。
     * 这条 `require` 不是一句注释——它得真的拦得住。
     */
    @Test
    fun `the component refuses a fourth secondary action instead of quietly laying it out`() {
        var thrown: Throwable? = null
        try {
            rule.setContent {
                UiMatrix(360).RenderIn(LocalDensity.current.density) {
                    LbDialog(
                        title = "DLG_MANY",
                        onDismissRequest = {},
                        confirm = LbDialogAction(label = "A", onClick = {}),
                        secondary = listOf(
                            LbDialogAction(label = "B", onClick = {}), LbDialogAction(label = "C", onClick = {}),
                            LbDialogAction(label = "D", onClick = {}), LbDialogAction(label = "E", onClick = {})
                        )
                    )
                }
            }
            rule.waitForIdle()
        } catch (e: Throwable) {
            thrown = e
        }
        val msg = thrown?.message.orEmpty()
        assertTrue("超过 3 颗次级出口应当抛，实到异常：$thrown", thrown is IllegalArgumentException)
        assertTrue("报错要说出实到几颗：$msg", msg.contains("实到 4"))
    }

    /** 正文与 body 两个槽：有 body 时不许再多出一段空正文节点 */
    @Test
    fun `the body slot does not also render an empty message`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbDialog(
                    title = "DLG_BODY_ONLY",
                    onDismissRequest = {},
                    body = { Text("DLG_LONG_BODY") }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithTextCompat("DLG_LONG_BODY")
        val blanks = rule.onAllNodes(
            SemanticsMatcher("节点带空白 Text") { node ->
                node.config.getOrNull(SemanticsProperties.Text)
                    ?.any { it.text.isBlank() } == true
            }
        ).fetchSemanticsNodes().size
        assertEquals("不许留一颗空白正文节点占位", 0, blanks)
    }

    private fun ComposeContentTestRule.onNodeWithTextCompat(text: String) {
        val nodes = onAllNodesWithText(text).fetchSemanticsNodes()
        assertTrue("对话框里找不到文案「$text」（实到 ${nodes.size} 个节点）", nodes.size == 1)
    }
}
