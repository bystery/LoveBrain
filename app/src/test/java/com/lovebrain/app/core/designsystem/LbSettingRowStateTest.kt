package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * §6.1 `LbSettingRow` 的"状态"槽：修的是一个**哑参数**。
 *
 * 旧组件里写的是 `if (statusText != null) { 画一颗 6dp 的点 }`——
 * `statusText` 的**值从来没被画出来过**。所以：
 * - 捕获行认真算出来的"开"/"关"（`R.string.home_on` / `home_off`）解析完就被丢掉；
 *   那两条资源 lint 也不报未使用（它们确实被引用了），于是这件事在两边都看不见；
 * - 供应商行传 `statusText = ""`，本意是"我只要一颗点"，等于用空串替一个不存在的开关。
 *
 * 这一组用例钉的是拆开的两个旋钮：点画不画（`dot`）与词画不画（`statusText`），
 * 外加尾部那颗"管理"按钮的热区（旧值 `heightIn(min = 32dp)`，低于 §6.5 的 48dp）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbSettingRowStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private val dot: MutableState<LbRowState?> = mutableStateOf(LbRowState.Ready)
    private val word: MutableState<String?> = mutableStateOf(null)

    private fun mountRow() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box {
                    LbSettingRow(
                        title = "ROW_TITLE_SENTINEL",
                        subtitle = "ROW_SUBTITLE_SENTINEL",
                        dot = dot.value,
                        statusText = word.value,
                        trailingText = "ROW_TRAILING_SENTINEL",
                        onTrailingClick = {},
                        onClick = {}
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /**
     * 那颗点是**装饰**（6dp，没有可读名字），而整行是 `clickable` ⇒ 语义合并会把子节点藏进父节点，
     * 合并树里查不到它。这里用未合并树查，并同时核它的实际尺寸——
     * 只要求"tag 在合并树上可见"会逼着给装饰点加读屏名字，那是把 TalkBack 念得更啰嗦。
     */
    private fun dotCount(): Int {
        val nodes = rule.onAllNodesWithTag(LbRowTags.DOT, useUnmergedTree = true)
            .fetchSemanticsNodes()
        nodes.forEach { node ->
            val w = node.boundsInRoot.width / app.resources.displayMetrics.density
            val h = node.boundsInRoot.height / app.resources.displayMetrics.density
            assertEquals("点的尺寸应当是 6x6dp，实到 ${w}x${h}", 6f, w, 0.6f)
            assertEquals(6f, h, 0.6f)
        }
        return nodes.size
    }

    /** 语义树上"文本是空白"的节点数：以前那个 `statusText = ""` 就是这样一颗幽灵节点 */
    private fun blankTextNodeCount(): Int = rule.onAllNodes(
        SemanticsMatcher("节点带空白 Text") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.isBlank() } == true
        }
    ).fetchSemanticsNodes().size

    private fun show(nextDot: LbRowState?, nextWord: String?) {
        rule.runOnIdle {
            dot.value = nextDot
            word.value = nextWord
        }
        rule.waitForIdle()
    }

    @Test
    fun `the status word is drawn when the caller supplies one`() {
        mountRow()
        // 修之前这一格必红：组件里压根没有画 statusText 的那一行
        show(LbRowState.Ready, "STATUS_WORD_SENTINEL")
        rule.onNodeWithText("STATUS_WORD_SENTINEL").assertExists()
        assertEquals("点也该在", 1, dotCount())
    }

    @Test
    fun `a blank word does not leave a phantom text node`() {
        mountRow()
        show(LbRowState.Ready, "")
        assertEquals("空串不许画出一个空文本节点", 0, blankTextNodeCount())
        assertEquals("但点照旧画", 1, dotCount())
    }

    @Test
    fun `dot and word are two independent knobs`() {
        mountRow()
        show(null, "WORD_ONLY_SENTINEL")
        assertEquals("没有 dot 就不该有点", 0, dotCount())
        rule.onNodeWithText("WORD_ONLY_SENTINEL").assertExists()

        show(LbRowState.NotReady, null)
        assertEquals("供应商行那种只要一颗点的情况", 1, dotCount())
    }

    /** §6.5 :531 所有 clickable 节点 bounds ≥48×48dp —— 尾部那颗"管理"以前是 32dp */
    @Test
    fun `every interactive node in the row meets the touch and labeling floor`() {
        mountRow()
        show(LbRowState.Ready, "STATUS_WORD_SENTINEL")

        val targets = probe.assertAllActionableMeetTouchFloor(rule, "设置行")
        probe.assertAllActionableLabeled(rule, "设置行")
        assertTrue("整行 + 尾部动作都该量到：" + targets.joinToString { it.describe() },
            targets.size >= 2)
    }
}
