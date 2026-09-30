package com.lovebrain.app.ui.visual

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.home.HomeScreen
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页（`HomeScreen`）的屏幕级截图基线——§6.2 那四段在像素上的第一份证人。
 *
 * 组件级基线（`LbSettingRow` / `LbActionCard` / `LbTopBar` / `LbMetricGrid`）各钉了
 * 自己那一颗在 360 下的样子；这一格钉的是**这四颗摆进同一屏后**的样子——
 * 段间距、卡底、分割线、`LbScreenScaffold` 那一层水平边距，这些在语义树上全读不出来，
 * 树上只有一串 `role=Button` 与几行文字。段顺序有 `HomeScreenStructureTest` 钉，
 * 像素只有这一格钉。
 *
 * 这一格只摆「全都就绪」那一档：供应商配好、捕获开、悬浮窗可显示。
 * 不是因为这一档最重要，是因为它是「四段都画全」的那一档——
 * 「没配供应商」那一档「模型供应商」那行只剩灰点，长得像组件级基线里 `notReady…` 那张，
 * 不另开一张屏幕级基线去重复它。
 *
 * 挂载方式逐字照 `HomeScreenStructureTest`：`UiProbeApplication` + `mockk<SetupViewModel>`，
 * 两个 override 显式喂，绕开 `Settings.canDrawOverlays` 与 `FloatingService.instance`
 * 这两个 JVM 上读不到的前提。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    @After
    fun tearDown() {
        // 进程内单例不许漏到下一个用例（同 HomeScreenStructureTest）
        FloatingService.setWindowState(FloatingService.WindowState.STOPPED)
    }

    private fun vm(): SetupViewModel =
        mockk<SetupViewModel>(relaxed = true).also {
            every { it.activeTicket } returns MutableStateFlow(
                ProviderTicket(
                    name = "DeepSeek",
                    baseUrl = "https://example.test/v1",
                    model = "deepseek-chat"
                )
            )
            every { it.providerReady } returns MutableStateFlow(true)
            every { it.captureEnabled } returns MutableStateFlow(true)
            every { it.captureAllowedPackages } returns MutableStateFlow(setOf("com.tencent.mm"))
            every { it.isCaptureServiceEnabled(any()) } returns true
        }

    /** 全就绪那一档：四段画全，悬浮窗状态卡走「运行中·可显示」。 */
    @Test
    fun allReadyHasACommittedVisualBaseline() {
        FloatingService.setWindowState(FloatingService.WindowState.VISIBLE_BUBBLE)
        rule.setContent {
            UiMatrix(360, heightDp = 1200).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    HomeScreen(
                        viewModel = vm(),
                        onStartService = {}, onOpenPanel = { _, _ -> }, onTempHide = {},
                        onRestore = {}, onNavigateFeedback = {}, onNavigateAbout = {},
                        onNavigateProviders = {}, onNavigateUsage = {}, onNavigateCaptureApps = {},
                        onBack = {},
                        overlayGrantedOverride = true,
                        serviceRunningOverride = true
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }
}
