package com.lovebrain.app.ui.visual

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.home.HomeStatusHarness
import com.lovebrain.app.ui.home.HomeScreen
import com.lovebrain.app.ui.home.LbHomeTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页（`HomeScreen`）的屏幕级截图基线——重做之后那一屏在像素上的唯一证人。
 *
 * 组件级基线（`LbActionCard` / `LbTopBar` / `LbSettingRow`…）各钉自己那一颗；
 * 这一格钉的是"状态卡 + 四入口摆进同一屏之后"的样子：段间距、卡底、24dp 边距、
 * 灯与三角/方块的相对位置——这些在语义树上全读不出来。
 *
 * 这一格只摆**全都就绪**那一档（绿 + ■、无黄字、四格画全）。
 * 黄档那一行有 `HomeScreenStructureTest` 在树上钉内容与位置；
 * 它长得像组件级基线里的另一张，不为它多开一张屏幕级基线。
 *
 * ⚠ 这一屏的参照形状变了（旧的"四段都画全"里那三段统计/设置/关于已经不存在），
 * **基线 PNG 必须由主线程重录一次**（`scripts/record_visual_baseline.sh`），
 * 本轮不许跑构建，所以这里只把"落盘前状态断言"改到新结构上——
 * 断言仍是上一版那条纪律：先 setContent → waitForIdle → 判状态 → `onRoot().captureRoboImage()`，
 * 让断言与像素出自同一棵已落定的树。
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

    /** 全就绪那一档：绿 + ■，四入口画全，没有黄字那一行。 */
    @Test
    fun allReadyHasACommittedVisualBaseline() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        assertEquals("录之前状态必须是绿档", harness.vm.status.value.state,
            com.lovebrain.app.ui.home.AdvisorState.RunningReady)
        rule.setContent {
            UiMatrix(360, heightDp = 1000).RenderIn(LocalDensity.current.density) {
                HomeScreen(
                    homeStatus = harness.vm,
                    onStartService = {},
                    onNavigateFeedback = {},
                    onNavigateProviders = {},
                    onNavigateCaptureApps = {},
                    onBack = {},
                    overlayGrantedOverride = true
                )
            }
        }
        rule.waitForIdle()
        assertContractedSegmentsDrawn()
        rule.onRoot().captureRoboImage()
    }

    /**
     * 录制前的状态断言：三段都在树上、四格都画进窗口、绿档那行黄字不在。
     *
     * 锚点全走 tag（文字会变，tag 不会）。"画全"判四格各自的 `boundsInRoot` 底边不超过窗口高、
     * 且没被压成 0 高——上一版那张就是这么被夹掉的，这条断言当场会红。
     */
    private fun assertContractedSegmentsDrawn() {
        assertRecordedLocaleIsZhCn()

        assertEquals("第一段：一条状态卡", 1, tagCount(LbHomeTags.STATUS_CARD))
        assertEquals("绿档不许带那一行黄字", 0, tagCount(LbHomeTags.SETUP_HINT))
        assertEquals("第二段：2×2 四入口必须是同一颗卡", 4, tagCount(LbTags.ACTION_CARD))
        assertEquals("整屏不许再有页段标题", 0, tagCount(LbTags.SECTION))
        // 这条判据的**方向**被 2026-10-10 的新原话替代了：旧规（`requests.md` §5 与上一轮 K22）是
        // "首页不恢复内部统计"⇒ 那时"整屏不许再有统计格"= 0 是对的；本轮《1.4.1 修复指导》§五 取舍③
        // 明说"首页可以重新加入累计使用面板…维持一块简洁的小卡，不恢复大而复杂的仪表盘"。
        // ⇒ 判据从"零颗"改成"**恰好一块卡的四格**"：多一块卡、多一格、或整块没接上读数都红；
        //   规模上限由 `ui/home/HomeUsageCardTest` 那族钉（卡高 ≤ 一颗入口卡、四格同排、不许有图）。
        assertEquals("累计使用小卡恰四格（一块卡的规模上限，不许多长）", 4, tagCount(LbTags.METRIC_CELL))

        val cells = nodesOf(LbTags.ACTION_CARD)
        val viewportPx = app.resources.displayMetrics.heightPixels
        val outOfFrame = cells.filter { isInFrame(it.boundsInRoot, viewportPx).not() }
        assertTrue(
            "四入口里有格子没画进窗口（窗口高 ${viewportPx}px = ${viewportPx / density}dp）：" +
                cells.joinToString { node ->
                    val rect = node.boundsInRoot
                    "bottom=${rect.bottom.toInt()}px 高=${rect.height.toInt()}px"
                } +
                "；这一格的名字承诺的是「三段都画全」，画到一半就不许落盘",
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
}
