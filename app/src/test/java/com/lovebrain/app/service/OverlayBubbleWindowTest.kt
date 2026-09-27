package com.lovebrain.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 悬浮球那一扇窗的几何规则（FloatingService 拆出来的那一格）。
 *
 * ⚠ 三把尺**故意不一样**，这里逐条钉住，防止后来人"顺手合并成一把"：
 * - 吸边 / 回弹：横向贴边缘，纵向 clamp 在 [48, screenH-mainSize-32]（不贴屏幕上下边）；
 * - 拖拽每一帧：横纵都 clamp 在 [0, screen-mainSize]（要跟手，允许顶到状态栏下面）；
 * - 临时隐藏恢复：横向同拖拽，纵向同吸边，且把上限再兜一层 coerceAtLeast(48)。
 * 用 identity 的 dp（1dp=1px）跑，断言都是看得懂的数。
 */
class OverlayBubbleWindowTest {

    private val dp: (Int) -> Int = { it }

    // ═══════════ 吸边 / 回弹 ═══════════

    @Test
    fun `左半屏松手贴左边缘`() {
        val (x, y) = bubbleDockTarget(
            x = 200, y = 300, screenW = 1080, screenH = 2400, mainSize = 56, dp = dp
        )
        assertEquals("中心 228 < 540 → 贴左 2dp", 2, x)
        assertEquals("纵向保持原位，只 clamp 不进吸附", 300, y)
    }

    @Test
    fun `右半屏松手贴右边缘`() {
        val (x, _) = bubbleDockTarget(
            x = 800, y = 300, screenW = 1080, screenH = 2400, mainSize = 56, dp = dp
        )
        assertEquals("1080-56-2", 1022, x)
    }

    @Test
    fun `吸边时纵向被按回状态栏与导航条之间`() {
        val (_, top) = bubbleDockTarget(
            x = 200, y = 10, screenW = 1080, screenH = 2400, mainSize = 56, dp = dp
        )
        assertEquals("低于 48 的一律压回 48：球不能盖住状态栏", 48, top)

        val (_, bottom) = bubbleDockTarget(
            x = 200, y = 2390, screenW = 1080, screenH = 2400, mainSize = 56, dp = dp
        )
        assertEquals("2400-56-32", 2312, bottom)
    }

    // ═══════════ 拖拽每一帧 ═══════════

    @Test
    fun `拖拽时整颗球不许离开屏幕`() {
        val (x, y) = bubbleInsideScreen(
            x = -80, y = -80, screenW = 1080, screenH = 2400, mainSize = 56
        )
        assertEquals(0 to 0, x to y)

        val (x2, y2) = bubbleInsideScreen(
            x = 2000, y = 3000, screenW = 1080, screenH = 2400, mainSize = 56
        )
        assertEquals("右下钳位按球径留白", 1024 to 2344, x2 to y2)
    }

    @Test
    fun `拖拽允许顶到状态栏那一条而吸边不允许`() {
        // 这一格是"两把尺别合并"的证据：同一个 y=20，拖拽放行、吸边压回 48
        val dragged = bubbleInsideScreen(x = 10, y = 20, screenW = 1080, screenH = 2400, mainSize = 56)
        val docked = bubbleDockTarget(x = 10, y = 20, screenW = 1080, screenH = 2400, mainSize = 56, dp = dp)

        assertEquals("跟手优先：拖到哪儿算哪儿（只要不出屏）", 20, dragged.second)
        assertEquals("松手归位时才回到安全区", 48, docked.second)
    }

    // ═══════════ 临时隐藏后的位置校正 ═══════════

    @Test
    fun `恢复时超屏的位置被校正回安全区`() {
        val (x, y) = bubbleCorrectedOrigin(
            x = 3000, y = 3000, screenW = 1080, screenH = 2400, mainSize = 56, dp = dp
        )
        assertEquals(1024, x)
        assertEquals(2312, y)
    }

    @Test
    fun `屏幕矮到装不下安全区时校正也不抛异常`() {
        // 120-56-32=32 已经低于顶部下限 48，区间反号；这一格要的就是"兜一层 coerceAtLeast"那句
        val (x, y) = bubbleCorrectedOrigin(
            x = 900, y = 900, screenW = 200, screenH = 120, mainSize = 56, dp = dp
        )
        assertEquals("横向下限仍是 0", 144, x)
        assertEquals("纵向区间反号时取下限，不能抛 IllegalArgumentException", 48, y)
    }
}
