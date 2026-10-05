package com.lovebrain.app.data

import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * 面板**背景层**不透明度这一项配置（[PanelBackdropOpacity]）的判据，全是纯 JVM。
 *
 * 这一族钉的是四件互相独立的事，缺一条就会长出对应的那一种坏实现：
 *
 * 1. **默认没被改动**：从没滑过滑杆的机器上，这一项换算出来就是完全不透明，
 *    面板与这一笔之前逐字同形。放开"可调"最坏的失败方式不是调不动，而是所有人一上线
 *    就发现自己的面板变淡了。
 * 2. **有界且能回落**：量纲是整数百分比，区间外的脏值不能原样画出去，
 *    更不能因为读不出而变成 0（= 面板直接看不见）。
 * 3. **整数存盘这件事本身成立**：滑杆给用户看的每一个合法值都必须能
 *    `整数 → 浮点 alpha → 整数` 原样回来，否则"盘上是整数"这句只是注释。
 * 4. **它作用在背景层、不作用在整扇窗**：`ComposeView.alpha` 那一条通道归淡入淡出动画，
 *    窗口在三个地方把它复位回基准；用户设的值一旦被画在那条通道上，就会在第一次收起面板时消失。
 *    这一条是窗口文件里的形状判据，钉的是"复位只能复位动画那一条"。
 */
class PanelBackdropOpacityTest {

    /** 单测的工作目录可能是模块目录也可能是仓库根，两种都接住（与本仓其它源码级判据同一写法）。 */
    private fun mainSource(relative: String): File =
        File("src/main/java/com/lovebrain/app/$relative").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/$relative")

    // ═══════════ 1. 默认 = 今天的样子 ═══════════

    @Test
    fun `never touched means fully opaque so nobody's panel changes on its own`() {
        assertEquals("默认必须是 100%（实色），这一笔上线时滑杆没动过的用户不该看到任何变化",
            100, PanelBackdropOpacity.DEFAULT_PERCENT)
        assertEquals("老数据 / 从没写过这一项 ⇒ 换算出来正好是 1f，不多不少",
            1f, PanelBackdropOpacity.alphaOf(null))
        assertEquals("默认档换算回来的整数也得是 100，读盘→画→回写要能逐字往返",
            100, PanelBackdropOpacity.snapPercent(null))
    }

    // ═══════════ 2. 有界 + 回落 ═══════════

    @Test
    fun `the legal range is bounded and every junk value lands back inside it`() {
        assertEquals("区间下限是\u201c正文还读得清\u201d那条线", 60, PanelBackdropOpacity.MIN_PERCENT)
        assertEquals("区间上限就是完全不透明", 100, PanelBackdropOpacity.MAX_PERCENT)
        val junk = listOf(-100, -1, 0, 1, 39, 101, 999, Int.MAX_VALUE, Int.MIN_VALUE)
        val escaped = junk.map { it to PanelBackdropOpacity.snapPercent(it) }
            .filter { (_, snapped) -> snapped !in PanelBackdropOpacity.steps }
        assertTrue(
            "盘上可能出现的脏值，换算完必须仍落在合法刻度里（0 与负数绝不能变成\u201c面板看不见\u201d）：$escaped",
            escaped.isEmpty()
        )
        assertEquals("越界向下钳到可读性下限，而不是照抄 0",
            60, PanelBackdropOpacity.snapPercent(0))
        assertEquals("越界向上钳到完全不透明，999 不等于\u201c更实\u201d这一回事",
            100, PanelBackdropOpacity.snapPercent(999))
    }

    // ═══════════ 3. 整数量纲这件事本身成立 ═══════════

    @Test
    fun `the persisted dimension is an integer percent that round-trips through the float`() {
        val steps = PanelBackdropOpacity.steps
        assertEquals("刻度是 5% 一档、从下限 60 到 100 的整串",
            listOf(60, 65, 70, 75, 80, 85, 90, 95, 100), steps)
        // 选整数存盘的那条理由要能被证伪：滑杆上的每个合法值都必须活着经过一次浮点换算再回来
        val broken = steps.filter { (PanelBackdropOpacity.alphaOf(it) * 100).roundToInt() != it }
        assertTrue("整数 → alpha → 整数这一趟必须有损不到任何一个刻度，坏掉的：$broken", broken.isEmpty())
        // 就近对齐：非刻度值进不来，界面显示的数字与盘上的数字才会一模一样
        assertEquals("42 已低于下限 ⇒ 钳到 60：既不照抄 42，也不会变成看不见", 60, PanelBackdropOpacity.snapPercent(42))
        assertEquals("63 离 65 更近 ⇒ 对齐到 65", 65, PanelBackdropOpacity.snapPercent(63))
        assertTrue("对齐必须幂等（写盘后再读回来不能再动一次）",
            steps.all { PanelBackdropOpacity.snapPercent(PanelBackdropOpacity.snapPercent(it)) == it })
    }

    @Test
    fun `the transparency mirror is exactly the opacity's complement both ways`() {
        // 界面上那一行标的是"透明度"，盘上存的是"不透明度"；这层镜像只允许写在这里
        assertEquals("完全不透明就是零透明", 0, PanelBackdropOpacity.transparencyOf(100))
        assertEquals("不透明度到下限 60% ⇒ 透明 40%", 40, PanelBackdropOpacity.transparencyOf(60))
        val bad = PanelBackdropOpacity.steps.filter {
            PanelBackdropOpacity.fromTransparencyPercent(PanelBackdropOpacity.transparencyOf(it)) != it
        }
        assertTrue("来回必须逐字相等，否则滑杆会把用户设的值改口：" + bad, bad.isEmpty())
        assertEquals("界面上\u201c最透明\u201d那一头换算回来是可读性下限，不是 0",
            60, PanelBackdropOpacity.fromTransparencyPercent(60))
    }

    @Test
    fun `the wire key is a stable non empty string`() {
        // 键名一旦上线就是盘上格式，改它等于把用户设过的值全丢掉，所以要钉住拼写
        assertEquals("panel_backdrop_opacity_percent", PanelBackdropOpacity.PREF_KEY)
    }

    // ═══════════ 4. 窗口那一侧的形状：复位只复位动画通道 ═══════════

    @Test
    fun `the panel window resets only its fade channel and never borrows it for the backdrop`() {
        val file = mainSource("service/OverlayPanelWindow.kt")
        assertTrue("$file 读不到——这一格会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        assertFalse(
            "`ComposeView.alpha` 上不许再出现裸写的复位字面量，三处必须走同一个基准，",
            code.contains("cv.alpha = 1f")
        )
        assertEquals("淡出结束 / 再次打开 / 立即隐藏三处复位都走同一个出口",
            3, code.lines().count { it.trim() == "restoreFadeBaseline(cv)" })
        assertTrue("淡出动画必须从那枚命名基准起淡，而不是自己再写一个 1f",
            code.contains("ValueAnimator.ofFloat(WINDOW_SHOWN_ALPHA, 0f)"))
        assertFalse(
            "用户设的背景透明度不许被画在整扇窗的 alpha 上（那会连正文一起洗淡，也会被复位抹掉）",
            code.contains("PanelBackdropOpacity")
        )
    }
}
