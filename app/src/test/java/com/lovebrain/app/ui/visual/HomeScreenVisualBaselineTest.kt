package com.lovebrain.app.ui.visual

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.home.HomeScreen
import com.lovebrain.app.ui.home.LbHomeTags
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 *
 * ⚠ **三处与上一版不同，都是判据逼出来的**（上一版把只画到 3.1 段的半屏钉成了「四段都画全」）：
 * 1. **窗口档位**：上一版 `@Config` 写 `h640dp`，而 `UiMatrix` 要 1200dp 高的槽——
 *    `Modifier.size` 被父约束夹回窗口，`使用概览` 只剩标题、`LbMetricGrid` 整块在折线以下
 *    （CI run 36730225255 的产物）。现在窗口与槽**同格**（都 1000dp，即
 *    `HomeScreenStructureTest` 用的那一格 `UiMatrix(360)`），不用 `performScroll` 去滚到
 *    第四段：那会把基线拍成滚动中间态（滚动位置自己就进图，且每次跑都可能差一点）。
 * 2. **拍法**：`captureRoboImage { content }` 那个 composable 形态会把 content 装进
 *    **另一颗 `RoborazziTransparentActivity` 的 composition**（本机实量：这样拍时
 *    `rule.onRoot()` 的语义树 `children=0`，屏幕上却照样有字）——那条通道下"录制前的状态
 *    断言"读的是**没被拍的那一棵**，判据是假的。所以这里先 `setContent` → `waitForIdle`
 *    → 判状态 → `rule.onRoot().captureRoboImage()`，断言与像素出自同一棵已落定的树，
 *    落盘文件名仍走 roborazzi 默认命名（`<类名>.<方法名>.png`，与其余 42 张同一规则）。
 * 3. **夹具喂三份使用统计**（128 / ￥3.42 / 42%）：指标格要「有值」，
 *    而不是 relaxed mockk 的 `0 / —` 兜底形状——那样这张钉不住这一带的回归。
 *
 * `zh-rCN` 固定 locale（1.4 只面向中文，用户已定）：没有那一段时 Robolectric 落 en-US，
 * 这一屏就成了「资源英文 + Kotlin 内联中文」的混排，换机器换 locale 就换图。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density

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
            // 使用概览那三格的夹具：上屏分别念 128 / ￥3.42 / 42%
            // （金额口径 `HomeScreen.kt:271-277` 的 `costReadout` + `HOME_COST_CURRENCY`）
            every { it.totalGenerateCount } returns 128
            every { it.totalCostYuan } returns 3.42
            every { it.adoptRate } returns 0.42f
        }

    /** 全就绪那一档：四段画全，悬浮窗状态卡走「运行中·可显示」。 */
    @Test
    fun allReadyHasACommittedVisualBaseline() {
        FloatingService.setWindowState(FloatingService.WindowState.VISIBLE_BUBBLE)
        rule.setContent {
            UiMatrix(360, heightDp = 1000).RenderIn(LocalDensity.current.density) {
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
        rule.waitForIdle()
        assertFourSectionsDrawnInOneFrame()
        rule.onRoot().captureRoboImage()
    }

    /**
     * 录制前的状态断言：**四段都在树上，且第四段那三格指标整颗落在窗口里。**
     *
     * 锚点全走 tag，不靠中文字面量（同 `HomeScreenStructureTest` 那条理由：文字会变，tag 不会）：
     * ① 页头（About 入口那颗）② 军师状态主卡 ③ 快捷功能两张同颗卡片
     * ④ 服务设置两行 + 使用概览三格指标。
     * 「有值」判三格各自的数字都上屏；「画全」判三格 `boundsInRoot` 的底边不超过窗口高度、
     * 且没被压成 0 高——上一版那张就是这么被夹掉的，这条断言当场就会红。
     */
    private fun assertFourSectionsDrawnInOneFrame() {
        assertRecordedLocaleIsZhCn()

        assertEquals("第一段：页头那颗 About 入口", 1, tagCount(LbHomeTags.ABOUT))
        assertEquals("第二段：军师状态主卡", 1, tagCount(LbHomeTags.STATUS_CARD))
        assertEquals(
            "第三段：快捷功能必须是 2 张同颗卡片",
            2,
            rule.onAllNodesWithTag(LbTags.ACTION_CARD).fetchSemanticsNodes().size
        )
        assertEquals("第四段之一：服务设置两行", 2, tagCount(LbTags.SETTING_ROW))
        assertEquals(
            "分区标题必须是 3 个（快捷功能 / 服务设置 / 使用概览）",
            3,
            nodesOf(LbTags.SECTION).map { it.boundsInRoot.top }.distinct().size
        )

        val cells = nodesOf(LbTags.METRIC_CELL)
        assertEquals("第四段之二：使用概览必须三格指标，实到 ${cells.size}", 3, cells.size)
        rule.onNodeWithText(GENERATED_ON_SCREEN, useUnmergedTree = true).assertExists()
        rule.onNodeWithText(COST_ON_SCREEN, useUnmergedTree = true).assertExists()
        rule.onNodeWithText(RATE_ON_SCREEN, useUnmergedTree = true).assertExists()

        val viewportPx = app.resources.displayMetrics.heightPixels
        val outOfFrame = cells.filter { isInFrame(it.boundsInRoot, viewportPx).not() }
        assertTrue(
            "使用概览那三格没画进窗口（窗口高 ${viewportPx}px = ${viewportPx / density}dp）：" +
                cells.joinToString { node ->
                    val rect = node.boundsInRoot
                    "bottom=${rect.bottom.toInt()}px 高=${rect.height.toInt()}px"
                } +
                "；这一格的名字承诺的是「四段都画全」，画到一半就不许落盘",
            outOfFrame.isEmpty()
        )
    }

    private fun isInFrame(rect: Rect, viewportPx: Int): Boolean =
        rect.height > 0f && rect.top >= 0f && rect.bottom <= viewportPx

    private fun nodesOf(tag: String) =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun tagCount(tag: String) = nodesOf(tag).size

    /** 基线固定 zh-CN：locale 一漂，屏上的字整批变，这张就不是同一格。 */
    private fun assertRecordedLocaleIsZhCn() {
        val locales = app.resources.configuration.locales
        assertEquals("截图基线要求 zh-CN", "zh-CN", locales.toLanguageTags())
    }

    private companion object {
        const val GENERATED_ON_SCREEN = "128"
        const val COST_ON_SCREEN = "￥3.42"
        const val RATE_ON_SCREEN = "42%"
    }
}
