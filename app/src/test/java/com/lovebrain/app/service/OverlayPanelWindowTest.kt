package com.lovebrain.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 面板这一扇窗的几何规则（FloatingService 拆出来的那一格）。
 *
 * 四条规则各自都对应过一次真实 bug：贴边、顶部对齐、超屏裁剪、拖出屏幕。
 * 全用 identity 的 dp（1dp=1px）跑，断言写成看得懂的数——
 * 尺子本身不是这一格要审的东西，规则才是。
 */
class OverlayPanelWindowTest {

    private val dp: (Int) -> Int = { it }

    // ═══════════ 贴边定位 ═══════════

    @Test
    fun `球在左半屏时面板贴左边缘`() {
        val (px, _) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = 2, bubbleTop = 48, dp = dp
        )
        assertEquals("左半屏必须贴左 4dp，而不是落在屏幕中间", 4, px)
    }

    @Test
    fun `球在右半屏时面板贴右边缘`() {
        val (px, _) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = 700, bubbleTop = 48, dp = dp
        )
        assertEquals("右半屏贴右：1080-300-4", 776, px)
    }

    @Test
    fun `球还没建时回落到默认起点贴左`() {
        val (px, py) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = null, bubbleTop = null, dp = dp
        )
        assertEquals("没有球就没有参照，按左上默认位贴左", 4, px)
        // 默认 bTop = dp(48)，再上提 12 → 36，未触底部/顶部 clamp
        assertEquals(36, py)
    }

    @Test
    fun `面板顶部与球对齐并上提 12dp`() {
        val (_, py) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = 2, bubbleTop = 500, dp = dp
        )
        assertEquals(488, py)
    }

    @Test
    fun `面板过高时被压回底部安全区`() {
        val (_, py) = panelPositionFor(
            pw = 300, ph = 2000, screenW = 1080, screenH = 2400,
            bubbleLeft = 2, bubbleTop = 2200, dp = dp
        )
        assertEquals("2400-2000-60：底部至少留 60dp 导航区", 340, py)
    }

    @Test
    fun `面板顶边不得越过 24dp 上限`() {
        val (_, py) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = 2, bubbleTop = 5, dp = dp
        )
        assertEquals("5-12 是负数，必须被钉回 24", 24, py)
    }

    // ═══════════ 尺寸裁剪 ═══════════

    @Test
    fun `尺寸先按屏幕裁剪：左右各留 8dp、顶底共留 80dp`() {
        val (pw, ph) = panelFittedSize(
            panelW = 4000, panelH = 4000, screenW = 1080, screenH = 2400, dp = dp
        )
        assertEquals(1072, pw)
        assertEquals(2320, ph)
    }

    @Test
    fun `用户拖出来的尺寸落在允许的区间里`() {
        val (minW, minH) = panelClampedSize(newWpx = 1, newHpx = 1, dp = dp)
        assertEquals("太小的面板读不清：下限 260dp", 260, minW)
        assertEquals(300, minH)

        val (maxW, maxH) = panelClampedSize(newWpx = 99_999, newHpx = 99_999, dp = dp)
        assertEquals("太大的面板会压死宿主 App：上限 350dp", 350, maxW)
        assertEquals(680, maxH)

        val (w, h) = panelClampedSize(newWpx = 320, newHpx = 500, dp = dp)
        assertEquals(320 to 500, w to h)
    }

    // ═══════════ 拖拽移动 ═══════════

    @Test
    fun `拖拽移动不许把面板推出屏幕`() {
        val (x, y) = panelMovedOrigin(
            x = 10, y = 20, dxPx = -500f, dyPx = -500f,
            viewW = 300, viewH = 420, screenW = 1080, screenH = 2400
        )
        assertEquals(0 to 0, x to y)

        val (x2, y2) = panelMovedOrigin(
            x = 1000, y = 2000, dxPx = 500f, dyPx = 500f,
            viewW = 300, viewH = 420, screenW = 1080, screenH = 2400
        )
        assertEquals("右下角钳位按面板实际尺寸留白", 780 to 1980, x2 to y2)
    }

    @Test
    fun `屏幕比面板还小时钳位下限保住 0 不抛异常`() {
        val (x, y) = panelMovedOrigin(
            x = 500, y = 500, dxPx = 0f, dyPx = 0f,
            viewW = 2000, viewH = 3000, screenW = 1080, screenH = 2400
        )
        assertEquals(0 to 0, x to y)
    }
}
