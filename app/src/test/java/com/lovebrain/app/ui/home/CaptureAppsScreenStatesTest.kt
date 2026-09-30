package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.viewmodel.SetupViewModel
import com.lovebrain.app.viewmodel.SetupViewModel.CaptureApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.3 收尾：捕获范围这一页**整页**接在共用四态出口上（此前这页一格用例都没有）。
 *
 * 挂的是 `CaptureAppsScreen` 本身而不是子组件，因为这格要证的三件事都在页面上：
 * 1. 四格真的走 [com.lovebrain.app.core.designsystem.LbAsyncState]（自造那张 inset 卡片已删）；
 * 2. 错误态那颗重试**确实又枚举了一次**（数 `selectableCaptureTargets` 的调用次数，
 *    不是"画了个按钮"就算通过——这是本仓库反复栽过的"钉磁盘不钉返回值"同族）；
 * 3. 判据搬进 mapper 之后，勾选这条路仍然落到 `setCaptureAllowed(包名, true)`。
 *
 * 状态源用一份 mockk 的 SetupViewModel：null / 空表 / 两份列表都给得出，
 * 而页面拿到的仍是真 `StateFlow`，`collectAsStateWithLifecycle()` 走的是它平时的路。
 * 断言里要比对文案时一律 `getString(资源 id)`，不抄中文字面量（中英任一边改版式都不会假红）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureAppsScreenStatesTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val density: Float
        get() = app.resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val alfred = CaptureApp(packageName = "PACKET_ALFRED", displayName = "LABEL_ALFRED", secondRejected = false)
    private val bear = CaptureApp(
        packageName = "PACKET_BEAR", displayName = "LABEL_BEAR", secondRejected = true
    )

    private fun vm(
        candidates: List<CaptureApp>?,
        allowed: Set<String> = emptySet()
    ): SetupViewModel = mockk<SetupViewModel>(relaxed = true).also {
        every { it.captureAllowedPackages } returns MutableStateFlow(allowed)
        every { it.selectableCaptureTargets(any()) } returns candidates
    }

    private fun mount(model: SetupViewModel) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                CaptureAppsScreen(viewModel = model, onBack = {})
            }
        }
        rule.waitForIdle()
    }

    /**
     * §6.5 矩阵：把这一屏从固定 360dp 推到 4 宽 × 3 字号（`UiMatrix.FULL`）。
     * 量具，不是闸：热区 < 48dp、越槽只登记不改码（工单明文），打 stdout 进交付表。
     * 硬断言只有哨兵（十二格各量到一次）+ 样本下限（防空转）。
     * 换格子靠 hoisted 状态（一个用例只 `setContent` 一次，同 `UiMatrixFullSweepTest`）。
     */
    private val matrixCell: MutableState<UiMatrix> = mutableStateOf(UiMatrix.FULL.first())

    private fun mountSweep(model: SetupViewModel) {
        rule.setContent {
            matrixCell.value.RenderIn(LocalDensity.current.density) {
                CaptureAppsScreen(viewModel = model, onBack = {})
            }
        }
        rule.waitForIdle()
    }

    private fun useCell(next: UiMatrix) {
        rule.runOnIdle { matrixCell.value = next }
        rule.waitForIdle()
    }

    @Test
    fun `the touch floor is recorded across four widths and three font scales`() {
        val model = vm(candidates = listOf(alfred, bear), allowed = setOf("PACKET_ALFRED"))
        mountSweep(model)
        val table = StringBuilder()
        val visited = LinkedHashMap<String, String>()
        var judged = 0
        for (m in UiMatrix.FULL) {
            useCell(m)
            val targets = probe.actionableTargets(rule, "捕获范围页·${m.id}")
            val inside = targets.filter {
                it.widthDp > 0f && it.heightDp > 0f &&
                    it.leftDp >= 0f && it.topDp >= 0f &&
                    it.leftDp + it.widthDp <= m.widthDp + 0.5f &&
                    it.topDp + it.heightDp <= m.heightDp + 0.5f
            }
            judged += inside.size
            val small = inside.filter { it.tooSmall(probe.floorDp) }
            val over = targets.filter { it.leftDp + it.widthDp > m.widthDp + 0.5f }
            table.append("PROBE64 捕获范围页·${m.id} 判 ${inside.size}/${targets.size} 颗；")
                .append("热区<${probe.floorDp.toInt()}dp ${small.size} 颗：")
                .append(small.joinToString(" | ") { it.describe() }).append("；")
                .append("越槽 ${over.size} 颗：")
                .append(over.joinToString(" | ") { it.describe() }).append('\n')
            visited[m.id] = "${inside.size}/${targets.size}"
        }
        println("§6.5 矩阵·捕获范围页·实到读数（4 宽 × 3 字号）")
        println(table.toString())
        assertEquals(
            "矩阵只量到 ${visited.size} 格（应为 ${UiMatrix.FULL.size}）：${visited.keys}" +
                " —— 覆盖面不能靠循环写法自称",
            UiMatrix.FULL.map { it.id }, visited.keys.toList()
        )
        assertTrue(
            "十二格一共只判到 $judged 颗，低于样本下限 $MIN_SWEEP_JUDGED —— 这格在空转，" +
                "别把它读成「全达标」",
            judged >= MIN_SWEEP_JUDGED
        )
    }

    companion object {
        /** 十二格至少该判到这么多颗；低于它就是这格没扫到东西，不是「全达标」 */
        private const val MIN_SWEEP_JUDGED = 12
    }

    private fun messageCount() =
        rule.onAllNodesWithTag(LbAsyncTags.MESSAGE).fetchSemanticsNodes().size

    private fun spinnerCount() =
        rule.onAllNodesWithTag(LbAsyncTags.LOADING).fetchSemanticsNodes().size

    @Test
    fun `an enumeration failure shows an error with a retry that really enumerates again`() {
        val model = vm(candidates = null)
        mount(model)
        assertEquals("读失败这一格要走共用错误态", 1, messageCount())
        verify(exactly = 1) { model.selectableCaptureTargets(any()) }

        val target = probe.of(
            rule.onNodeWithTag(LbAsyncTags.ACTION).fetchSemanticsNode("错误态没有重试入口")
        )
        assertEquals("重试必须带按钮角色：" + target.describe(), "Button", target.role)
        assertTrue(
            "重试热区不足 ${probe.floorDp.toInt()}dp：${target.describe()}",
            !target.tooSmall(probe.floorDp)
        )

        rule.onNodeWithTag(LbAsyncTags.ACTION).performClick()
        rule.waitForIdle()
        verify(exactly = 2) { model.selectableCaptureTargets(any()) }
    }

    /** 同一份枚举结果换成"空表"，语气必须从错误变成空——这两格分不开就是本轮修掉的那个谎 */
    @Test
    fun `no candidates at all is an empty state, not an error`() {
        mount(vm(candidates = emptyList()))
        assertEquals(1, messageCount())
        rule.onNodeWithText(app.getString(R.string.capture_apps_none_to_authorize)).assertExists()
        rule.onNodeWithTag(LbAsyncTags.ACTION).assertDoesNotExist()
        assertEquals("空态不许画转圈", 0, spinnerCount())
    }

    @Test
    fun `a query that matches nothing says so without reusing the no-apps sentence`() {
        mount(vm(candidates = listOf(alfred)))
        rule.onNodeWithText("LABEL_ALFRED").assertExists()

        rule.onNode(hasSetTextAction()).performTextInput("zzz")
        rule.waitForIdle()

        assertEquals(1, messageCount())
        rule.onNodeWithText(app.getString(R.string.capture_apps_no_results)).assertExists()
        assertNotEquals(
            "「没搜索到」与「整机没有可授权的 App」共用一句话，就等于对用户少说一件事",
            app.getString(R.string.capture_apps_none_to_authorize),
            app.getString(R.string.capture_apps_no_results)
        )
        rule.onNodeWithText("LABEL_ALFRED").assertDoesNotExist()
    }

    @Test
    fun `rows still hand the package name and the new state to the view model`() {
        val model = vm(candidates = listOf(alfred, bear), allowed = emptySet())
        mount(model)

        rule.onNodeWithText("LABEL_ALFRED").performClick()
        rule.waitForIdle()
        verify(exactly = 1) { model.setCaptureAllowed("PACKET_ALFRED", true) }
    }

    /**
     * 这一屏每个可交互节点都过 §6.5 那把尺（页头那颗 32dp 就是上一格这么量出来的，账本 §20.4）。
     *
     * 勾选行"现在有没有被勾上"念不念得出来是 §6.5 第②栏的账，**这格只挂不判**：
     * 判据写在别的账里，别在这里顺手扩范围。
     */
    @Test
    fun `every interactive node on the list screen meets the touch and labeling floor`() {
        mount(vm(candidates = listOf(alfred, bear), allowed = setOf("PACKET_ALFRED")))

        val targets = probe.assertAllActionableMeetTouchFloor(rule, "捕获范围页")
        probe.assertAllActionableLabeled(rule, "捕获范围页")
        probe.assertNoDuplicatedAnnouncement(rule, "捕获范围页")
        assertTrue(
            "只量到 ${targets.size} 个可交互节点，入口大概被画没了",
            targets.size >= 3
        )
    }
}
