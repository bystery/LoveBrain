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
 * P1-02 / §6.5：面板头部的触摸区、读屏标签、选中态——**在 JVM 上读语义树**。
 *
 * 这条通道之前不存在，所以同样的话只有两个地方能讲：
 * ①`ProductionUiContractTest` 在源码里搜 `MIN_TOUCH_TARGET_DP` 字样（复核点名它判错过绿）；
 * ②`androidTest/…/PanelHeaderTouchTargetsTest` 在 CI 的 emulator 上跑（本机没有 system image，
 *   于是本机改 PanelHeader 时永远拿不到信号）。
 * 这里补的是第三种：改完就能在本地量到真尺寸。CI 上那两条继续留着，互不替换。
 *
 * 窗口固定在 600dp（`@Config`），矩阵里更窄的那几格是靠给被测子树加固定宽度约束实现的——
 * 见 [UiMatrix] 的说明，这一点不假装成"换了台设备"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
class PanelHeaderSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    internal fun mount(matrix: UiMatrix = UiMatrix(412)) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                PanelHeader(
                    panelMode = 0,
                    onModeChange = {},
                    showPlanPanel = false,
                    onPlanVisibility = {},
                    onCollapse = {}
                )
            }
        }
    }

    /**
     * 先证明这台仪表真的量到了东西。
     *
     * 没有这一格，后面所有断言都可能因为"一个节点都没抓到"而假绿——
     * 那是复核 §9 第 4/6 条最反对的一种绿。三段模式切换 + 收起 = 4 个可交互节点。
     */
    @Test
    fun `the instrument measures four actionable nodes on the jvm`() {
        mount()
        val targets = probe.actionableTargets(rule, "PanelHeader")
        assertEquals(
            "PanelHeader 应当恰好有 4 个可交互节点（回复/锦囊/谈心 三段 + 收起）",
            4, targets.size
        )
        assertTrue(
            "尺寸全是 0，说明 Robolectric 这边根本没布局：" + targets.joinToString { it.describe() },
            targets.all { it.widthDp > 0f && it.heightDp > 0f }
        )
    }

    /** §6.5：读屏要能念出每个可交互节点是什么（这一格在当前实现下就是绿的） */
    @Test
    fun `every clickable node is labeled for talkback`() {
        mount()
        probe.assertAllActionableLabeled(rule, "PanelHeader")
    }
}
