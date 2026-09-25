package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LB_SCREEN_HORIZONTAL_MARGIN
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.ui.home.CaptureAppsScreen
import com.lovebrain.app.ui.home.ProviderSection
import com.lovebrain.app.ui.panel.OnboardingFlow
import com.lovebrain.app.viewmodel.SetupViewModel
import com.lovebrain.app.viewmodel.SetupViewModel.CaptureApp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 表最后一行：`LbScreenScaffold` —— 页面背景、安全区、顶部栏、**统一水平边距**。
 *
 * 这一格先只做一件事：**把"各页边距到底是多少"量出来**。
 * 之前它在文档里只是"各页仍各写自己的 `Box + background + padding`"这句定性判断——
 * 方向没错，但没人说过那几个 padding 具体差多少，而"统一水平边距"这半句
 * 是要能判达标/不达标的。
 *
 * ## 量法（读语义树，不是源码 grep）
 *
 * 从根往下走，取所有**宽到足以代表整行内容**的节点（宽度 > 槽位一半），
 * 取它们左边缘的**最大值** —— 那就是把内容往里推的那一档水平内边距。
 *
 * 为什么是最大而不是最小：第一版取最小，三格全报 **0dp**。因为最外层那个
 * `Box(fillMaxSize).background(SurfaceBase)` 本身就是"整行宽"的节点，左边缘自然是 0——
 * 量到的是**画布**，不是内容。改成取最大之后量的才是"被 padding 推进去的那一层"。
 *
 * 挂载槽统一 360dp 宽：同一把尺，各页才有可比性。
 *
 * ⚠ 一台仪器**一个用例只能 `setContent` 一次**（本仓库踩过三次的那条），
 * 所以"各页是否一致"不能写成"挂三页再比"，只能一页一格、
 * 每格都对着**同一个期望值**断言；哪一页跑偏就红在哪一格。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenScaffoldFrameTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val slotWidthDp = 360f

    /**
     * 页面自己的水平内边距 = **最左边那颗可点节点**的左边缘。
     *
     * 三个候选量法，前两个都被实测否掉了：
     * - 取所有"整行宽"节点的最小左边缘 ⇒ 三格全报 **0dp**。量到的是那个
     *   `Box(fillMaxSize).background(…)` 画布本身，不是内容。
     * - 排除左边缘为 0 的、再取最大 ⇒ 报 **74dp / 28dp**。原因是**语义树里根本没有
     *   普通布局容器**：`Row`/`Column` 不声明语义就不进树，所以"整行节点"实际是
     *   一些 Text，量到的是页面深处某个块的缩进，不是外框。
     * - ⇒ 用**可点节点**：页头那颗返回钮就落在页面被推进来的那个位置上，
     *   而且它是语义树里必然存在、边界清晰的一颗。
     *
     * 没有返回钮的页（首次引导）这一格量的是它最左的那颗按钮——
     * 所以量出来的数仍要一格一格对，不能拿"都是可点节点的最小左边缘"糊成一个定义。
     */
    private fun contentLeftInset(): Float {
        val targets = SemanticsProbe(density).actionableTargets(rule, "页面外框")
        val leftMost = targets.minBy { it.leftDp }
        // 要说清量的是哪一颗，否则红了看不出是被哪个节点顶出来的数
        measuredAnchor = leftMost
        return leftMost.leftDp
    }

    private var measuredAnchor: SemanticsProbe.Target? = null

    private fun mount(content: @Composable () -> Unit) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) { content() }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 与 `CaptureAppsScreenStatesTest` 同款：relaxed mock 扛不住泛型流，必须点名返回真流 */
    private fun captureVm(): SetupViewModel = mockk<SetupViewModel>(relaxed = true).also {
        every { it.captureAllowedPackages } returns MutableStateFlow(setOf("PACKET_ALFRED"))
        every { it.selectableCaptureTargets(any()) } returns listOf(
            CaptureApp(packageName = "PACKET_ALFRED", displayName = "LABEL_ALFRED", secondRejected = false)
        )
    }

    /** 与 `ProviderSectionSemanticsTest` 同款 */
    private fun providerVm(): SetupViewModel = mockk<SetupViewModel>(relaxed = true).also {
        every { it.tickets } returns MutableStateFlow(emptyList<ProviderTicket>())
        every { it.activeTicket } returns MutableStateFlow<ProviderTicket?>(null)
        every { it.providerReady } returns MutableStateFlow(false)
    }

    private fun assertScaffoldMargin(page: String, measured: Float) {
        assertTrue(
            "$page 的水平边距应当是脚手架那一档 ${ExpectedPageMarginDp.toInt()}dp，" +
                "实到 ${measured.toInt()}dp（量的是这颗节点：" + (measuredAnchor?.describe() ?: "没量到") + "）" +
                " ——对不上说明这一页还在自己写外框",
            kotlin.math.abs(measured - ExpectedPageMarginDp) < 0.6f
        )
    }

    /** 走 `ScreenPage`（它已经是"半个脚手架"）的那一家 */
    @Test
    fun `the ScreenPage family insets content by the scaffold margin`() {
        mount { CaptureAppsScreen(viewModel = captureVm(), onBack = {}) }
        assertScaffoldMargin("捕获范围页", contentLeftInset())
    }

    /** 自己拼 `Column + padding(horizontal = …)` 的那一家 */
    @Test
    fun `the hand-rolled provider page insets content by the scaffold margin`() {
        mount { ProviderSection(viewModel = providerVm(), onBack = {}) }
        assertScaffoldMargin("供应商页", contentLeftInset())
    }

    /** 又一整套根（首次引导）：它连 insets 都自己加了一遍 */
    @Test
    fun `the onboarding root insets content by the scaffold margin`() {
        mount { OnboardingFlow(onSkip = {}, onComplete = {}, onOpenSettings = {}) }
        assertScaffoldMargin("首次引导", contentLeftInset())
    }

    companion object {
        /**
         * 期望的那一档水平边距——**读脚手架自己的常量**，不是测试里手写的数。
         *
         * 第一版这里写死 `24f`。写完 `LbScreenScaffold` 之后如果还留着 24f，
         * 那么"统一水平边距"就变成**我在测试里许愿**：哪天有人把脚手架那档改成 20，
         * 这几格会红着告诉他"页面错了"，而错的其实是尺子还在照着旧数读。
         * 更要紧的是另一头：像 `ProviderSection` 那样**自己拼** `padding(xxxl)` 的页，
         * 今天恰好也是 24dp，所以这三格**绿并不能证明它走了脚手架**——
         * 那件事由 `UiLayerDependencyContractTest` 里"页面外框只有一个所有者"那格判。
         * 两把尺各管各的，别拿这一把的绿去说那一把的话。
         */
        val ExpectedPageMarginDp: Float = LB_SCREEN_HORIZONTAL_MARGIN.value
    }
}
