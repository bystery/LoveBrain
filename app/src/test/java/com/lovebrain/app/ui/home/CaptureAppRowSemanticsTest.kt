package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 第 6 行（`LbSettingRow` = 行这一族的统一所有者）归并 `CaptureAppRow` 之后，
 * **用户从这一行读到的东西不许变少**：App 名、包名、勾没勾上、它是不是按钮/勾选框、
 * 被二次拒绝的那一行还能不能读成"现在点不动"。
 *
 * 这一格刻意只用语义树读数，不读源码（`ProductionUiContractTest` 那种 grep 会被
 * "换个写法"骗过去，账本 §39.3 已经为这件事翻过一次车）。
 * 断言全部写成**归并前后都该成立**的形状，并且在本机对**归并之前**的实现先跑过一次：
 * 五格全绿（"LABEL_ALFRED" 那行 `role=Checkbox`、`312x56dp @(24,217)`）。
 * 所以这一组用例判的是"归并有没有把已经在的东西弄丢"，不是"顺手替这一页补判据"。
 *
 * 文案一律 `getString(资源 id)`，不抄中文（中英任一边改版式都不会假红）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureAppRowSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private val alfred = CaptureApp(
        packageName = "PACKET_ALFRED", displayName = "LABEL_ALFRED", secondRejected = false
    )
    private val cyrus = CaptureApp(
        packageName = "PACKET_CYRUS", displayName = "LABEL_CYRUS", secondRejected = false
    )
    private val bear = CaptureApp(
        packageName = "PACKET_BEAR", displayName = "LABEL_BEAR", secondRejected = true
    )

    private fun vm(
        candidates: List<CaptureApp>,
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

    /** 按行标题找那一行的可交互节点——一行必须**恰好**一颗，多出来就是有人又画了一层点击 */
    private fun rowTarget(label: String): SemanticsProbe.Target {
        val hits = probe.actionableTargets(rule, "捕获范围页").filter { it.label == label }
        assertEquals(
            "按「$label」应当正好量到一行，实到 ${hits.size} 颗：" + hits.joinToString { it.describe() },
            1, hits.size
        )
        return hits.single()
    }

    /** 标题/说明在不在树上（合并树里父节点带着子文案，所以判整棵子树都算） */
    private fun textNodes(vararg values: String): Int = values.count { v ->
        rule.onAllNodes(hasText(v)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `the row still reads its name and its package name`() {
        mount(vm(listOf(alfred), allowed = setOf("PACKET_ALFRED")))

        // 标题（App 名）与说明（包名）两处都得读得到
        assertEquals("App 名与包名都该在树里", 2, textNodes("LABEL_ALFRED", "PACKET_ALFRED"))

        val row = rowTarget("LABEL_ALFRED")
        assertEquals(
            "整行是一处操作，读屏得说出它是勾选框：" + row.describe(),
            "Checkbox", row.role
        )
        assertTrue(
            "整行热区不足 ${probe.floorDp.toInt()}dp：" + row.describe(),
            !row.tooSmall(probe.floorDp)
        )
    }

    /**
     * "状态"这一槽在这行数到的是什么，如实记在这里。
     *
     * 本机实量（归并前后同一份读数，来自 `describe()`）：整行 `role=Checkbox`、
     * `selected=null`、`state=null`，而未合并树里带 `ToggleableState` 的节点 **0 颗**——
     * 那颗 `Checkbox(onCheckedChange = null)` 只是画出来的，**勾没勾从来没进语义树**。
     *
     * 这一格因此不判"读屏听不听得出一行授权了没有"：既有账
     * `CaptureAppsScreenStatesTest` 的注释已经把这件事写成"§6.5 第②栏的账，这格只挂不判"，
     * 本格也不顺手扩范围（要修它得给那一槽一个能念出来的状态来源，那是另一格）。
     * 这里判的是归并**会不会再弄丢**已经存在的那三样：行说自己是一个勾选框、
     * 没被禁用时能按、被禁用时报得出禁用。
     */
    @Test
    fun `every allowed row still reads as an enabled checkbox`() {
        mount(vm(listOf(alfred, cyrus), allowed = setOf("PACKET_ALFRED")))

        listOf("LABEL_ALFRED", "LABEL_CYRUS").forEach { name ->
            val row = rowTarget(name)
            assertEquals("这一行要说得出自己是勾选框：" + row.describe(), "Checkbox", row.role)
            assertEquals("可授权的行不该是灰的：" + row.describe(), false, row.disabled)
        }
    }

    /**
     * 被二次拒绝的类别：**仍然显示**，但说得出"现在点不动"，且按下去真的没有回调。
     *
     * `assertAllActionable…` 那把尺把 Disabled 也算可交互节点，就是为了这一格：
     * "灰着画出来"与"干脆不画"两种实现都得被区分开。
     */
    @Test
    fun `a blocked app stays on screen, announces disabled and cannot be toggled`() {
        val model = vm(listOf(bear))
        mount(model)

        val row = rowTarget("LABEL_BEAR")
        assertTrue("被拒绝的行要报出不可用：" + row.describe(), row.disabled)
        assertEquals(
            "热区也不能因为禁用就缩掉：" + row.describe(),
            false, row.tooSmall(probe.floorDp)
        )
        // 说明那一行说的是"为什么不能选"，这句是页面上真实存在的文案
        rule.onNodeWithText(app.getString(R.string.capture_apps_allow) + " — PACKET_BEAR")
            .assertExists()

        rule.onAllNodes(hasText("LABEL_BEAR"))[0].performClick()
        rule.waitForIdle()
        verify(exactly = 0) { model.setCaptureAllowed("PACKET_BEAR", any()) }
    }

    /** 归并最容易弄丢的一件事：一行交回的是**它自己**的包名 */
    @Test
    fun `each row toggles its own package, not the first one in the list`() {
        val model = vm(listOf(alfred, cyrus))
        mount(model)

        rule.onAllNodes(hasText("LABEL_CYRUS"))[0].performClick()
        rule.waitForIdle()
        verify(exactly = 1) { model.setCaptureAllowed("PACKET_CYRUS", true) }
        verify(exactly = 0) { model.setCaptureAllowed("PACKET_ALFRED", any()) }
    }

    /**
     * 反空跑证人：这把尺得真看得见这一屏的东西，而且**每行只有一颗**可点节点。
     *
     * 归并成 `LbSettingRow` 时如果有人在行里又留了一层 `clickable`（外层壳 + 里面那一行），
     * `rows.size` 就会涨——那正是"点击挂在被合并掉的子节点上"那一族缺陷的形状。
     */
    @Test
    fun `the screen exposes exactly one actionable node per row plus the header back`() {
        mount(vm(listOf(alfred, cyrus, bear), allowed = setOf("PACKET_ALFRED")))

        val targets = probe.assertAllActionableMeetTouchFloor(rule, "捕获范围页")
        probe.assertAllActionableLabeled(rule, "捕获范围页")
        probe.assertNoDuplicatedAnnouncement(rule, "捕获范围页")
        val rows = targets.filter { it.label.startsWith("LABEL_") }
        assertEquals(
            "三行该是三颗可点节点：" + targets.joinToString { it.describe() },
            3, rows.size
        )
        // 被禁用的那一行也要还在尺里（"灰着画出来"与"干脆不画"是两种实现）
        assertEquals(
            "禁用那一行也得到尺：" + targets.joinToString { it.describe() },
            1, rows.count { it.disabled }
        )
    }
}
