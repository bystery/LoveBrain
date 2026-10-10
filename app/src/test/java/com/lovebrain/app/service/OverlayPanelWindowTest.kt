package com.lovebrain.app.service

import com.lovebrain.app.BubbleSizeTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 面板这一扇窗的几何规则（FloatingService 拆出来的那一格）。
 *
 * 四条规则各自都对应过一次真实 bug：贴边、顶部对齐、超屏裁剪、拖出屏幕。
 * 全用 identity 的 dp（1dp=1px）跑，断言写成看得懂的数——
 * 尺子本身不是这一格要审的东西，规则才是。
 *
 * ═══ M04 之后：贴边定位多了一颗 `bubbleSizeDp` 入参 ═══
 * 面板贴哪一边是拿**球心**判的，球心 = 球的左边 + 当前档位半径。以前这颗半径藏在函数里
 * 读全仓唯一的 56，所以这一族的旧格（pw=300 那几个读数）钉的就是**标准档 56 那一档**；
 * 现在同一个 `bubbleSizeDp` 必须一路走到这里，三档各跑一遍，
 * 并且有一格专门量"档位变了、结论也会变"——写死 56 的实现会在那一格红。
 */
class OverlayPanelWindowTest {

    private val dp: (Int) -> Int = { it }

    /** 与悬浮球那一族同一颗尺：三档由被测对象自己数出来 */
    private val tiers: List<Int> = BubbleSizeTier.ALLOWED_DP

    // ═══════════ 贴边定位 ═══════════

    @Test
    fun `球在左半屏时面板贴左边缘`() {
        val (px, _) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = 2, bubbleTop = 48, bubbleSizeDp = BubbleSizeTier.STANDARD_DP, dp = dp
        )
        assertEquals("左半屏必须贴左 4dp，而不是落在屏幕中间", 4, px)
    }

    @Test
    fun `球在右半屏时面板贴右边缘`() {
        val (px, _) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = 700, bubbleTop = 48, bubbleSizeDp = BubbleSizeTier.STANDARD_DP, dp = dp
        )
        assertEquals("右半屏贴右：1080-300-4", 776, px)
    }

    @Test
    fun `球还没建时回落到默认起点贴左`() {
        val (px, py) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = null, bubbleTop = null, bubbleSizeDp = BubbleSizeTier.STANDARD_DP, dp = dp
        )
        assertEquals("没有球就没有参照，按左上默认位贴左", 4, px)
        // 默认 bTop = dp(48)，再上提 12 → 36，未触底部/顶部 clamp
        assertEquals(36, py)
    }

    @Test
    fun `面板顶部与球对齐并上提 12dp`() {
        val (_, py) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = 2, bubbleTop = 500, bubbleSizeDp = BubbleSizeTier.STANDARD_DP, dp = dp
        )
        assertEquals(488, py)
    }

    @Test
    fun `面板过高时被压回底部安全区`() {
        val (_, py) = panelPositionFor(
            pw = 300, ph = 2000, screenW = 1080, screenH = 2400,
            bubbleLeft = 2, bubbleTop = 2200, bubbleSizeDp = BubbleSizeTier.STANDARD_DP, dp = dp
        )
        assertEquals("2400-2000-60：底部至少留 60dp 导航区", 340, py)
    }

    @Test
    fun `面板顶边不得越过 24dp 上限`() {
        val (_, py) = panelPositionFor(
            pw = 300, ph = 420, screenW = 1080, screenH = 2400,
            bubbleLeft = 2, bubbleTop = 5, bubbleSizeDp = BubbleSizeTier.STANDARD_DP, dp = dp
        )
        assertEquals("5-12 是负数，必须被钉回 24", 24, py)
    }

    @Test
    fun `贴哪一边按当前档位的球心算，档位不同结论会变`() {
        // bubbleLeft=510：球心 = 510 + 半径。48 档 534、56 档 538 还在左半屏；64 档 542 越过中线。
        // 面板若还在函数里自己读那颗写死的 56，64 档这一句就会仍然贴左 → 与球贴的边分家。
        val left = panelPositionFor(
            300, 420, 1080, 2400, 510, 48, BubbleSizeTier.SMALL_DP, dp
        ).first
        val stillLeft = panelPositionFor(
            300, 420, 1080, 2400, 510, 48, BubbleSizeTier.STANDARD_DP, dp
        ).first
        val right = panelPositionFor(
            300, 420, 1080, 2400, 510, 48, BubbleSizeTier.LARGE_DP, dp
        ).first
        assertEquals("48 档球心 534 < 540 → 贴左", 4, left)
        assertEquals("56 档球心 538 < 540 → 贴左", 4, stillLeft)
        assertEquals("64 档球心 542 > 540 → 贴右 1080-300-4", 776, right)
    }

    @Test
    fun `三档下面板都贴屏幕边而不是贴某个写死的球径`() {
        // 远左 / 远右两个位置在三档下结论必须一致（贴边这件事本身不随档位翻转），
        // 但纵向读数与贴边读数都得是算式而不是抄数——这一格防的是"半径换了、面板算错边"。
        for (size in tiers) {
            val (pxLeft, _) = panelPositionFor(
                300, 420, 1080, 2400, 2, 48, size, dp
            )
            assertEquals("$size 档球在左沿时贴左 4dp", 4, pxLeft)

            val (pxRight, _) = panelPositionFor(
                300, 420, 1080, 2400, 1000, 48, size, dp
            )
            assertEquals("$size 档球在右沿时贴右：1080-300-4", 776, pxRight)

            val (_, py) = panelPositionFor(
                300, 420, 1080, 2400, 2, 500, size, dp
            )
            assertEquals("$size 档顶部与球对齐并上提 12（这条与半径无关）", 488, py)
        }
        assertTrue("三档必须真的各有半径，读数全相同说明尺失效", tiers.distinct().size == 3)
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
