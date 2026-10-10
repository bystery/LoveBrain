package com.lovebrain.app.service

import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.ui.bubble.BubbleGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * 悬浮球那一扇窗的几何规则（FloatingService 拆出来的那一格）。
 *
 * ⚠ 三把尺**故意不一样**，这里逐条钉住，防止后来人"顺手合并成一把"：
 * - 吸边 / 回弹：横向贴边缘，纵向 clamp 在 [48, screenH-mainSize-32]（不贴屏幕上下边）；
 * - 拖拽每一帧：横纵都 clamp 在 [0, screen-mainSize]（要跟手，允许顶到状态栏下面）；
 * - 临时隐藏恢复：横向同拖拽，纵向同吸边，且把上限再兜一层 coerceAtLeast(48)。
 * 用 identity 的 dp（1dp=1px）跑，断言都是看得懂的数。
 *
 * ═══ M04 之后这些格**按档位跑**（指导书 2026-10-10 §1）═══
 * 以前每一格都硬写 `mainSize = 56`，那既是全仓唯一的尺寸真值、也是唯一被量过的那一档。
 * 现在档位有三颗（[BubbleSizeTier.ALLOWED_DP] = 48 / 56 / 64），所以：
 * - 三把尺的每一格都对**三档各跑一遍**，期望值一律写成 `f(size)` 的算式，而不是抄三个数进来；
 * - 另外保留 56 那一档的**旧读数逐字格**（1022 / 2312 / 1024 …）——
 *   它们是"默认档观感与现有版本一致"这条验收的替身：旧格钉的就是 56 这一档，
 *   所以旧数一个都不许改，只是从"唯一一档"降级成"三档之一"；
 * - 判据不许松成"存在即可"：每一格都还在断言具体的数。
 */
class OverlayBubbleWindowTest {

    private val dp: (Int) -> Int = { it }

    /** 三档直径，由被测对象自己数出来（这一格里不抄第二份 48/56/64 的字面量清单） */
    private val tiers: List<Int> = BubbleSizeTier.ALLOWED_DP

    /** 标准档 = 这一项出现之前的唯一一档，旧格的读数全从这里来 */
    private val standard = BubbleSizeTier.STANDARD_DP

    // ═══════════ 档位刻度尺本身：几何格用的三档就是这一颗数出来的 ═══════════

    @Test
    fun `the tier ruler has exactly three sizes and its default is the size on screen today`() {
        assertEquals("三档就是小/标准/大这三颗，从小到大", listOf(48, 56, 64), tiers)
        assertEquals("默认档必须是 56：§1 验收「默认外观与现有版本一致」靠这一颗成立", 56, BubbleSizeTier.DEFAULT_DP)
        assertEquals("落盘键拼写一旦上线就是盘上格式", "bubble_size_dp", BubbleSizeTier.PREF_KEY)
    }

    @Test
    fun `a dirty stored number snaps to the nearest tier and never leaves the ruler`() {
        // 盘上可能出现脏值（手写 XML、旧版本残留、别的 App 写坏）。
        // 坏实现最坏的两种：把 30 原样用出去（窗口小到点不中）、把 200 原样用出去（一坨糊住半屏）。
        assertEquals("从没写过 ⇒ 标准档", 56, BubbleSizeTier.snapDp(null))
        assertEquals("0 不等于「没有球」：钳到最近的一档", 48, BubbleSizeTier.snapDp(0))
        assertEquals("负数同样回到刻度上", 48, BubbleSizeTier.snapDp(-100))
        assertEquals("区间内就近：30 离 48 最近", 48, BubbleSizeTier.snapDp(30))
        assertEquals("51 离 48 更近", 48, BubbleSizeTier.snapDp(51))
        assertEquals("54 离 56 更近", 56, BubbleSizeTier.snapDp(54))
        assertEquals("62 离 64 更近", 64, BubbleSizeTier.snapDp(62))
        assertEquals("越上界钳到最大档", 64, BubbleSizeTier.snapDp(999))
        val escaped = (-200..400).map { BubbleSizeTier.snapDp(it) }.filter { it !in tiers }
        assertTrue("任何脏值出去后都必须是合法档位，坏样本：$escaped", escaped.isEmpty())
        // 幂等：写盘后再读回来不能再动一次
        assertTrue("对齐必须幂等", tiers.all { BubbleSizeTier.snapDp(BubbleSizeTier.snapDp(it)) == it })
        // 两档正中间（52 与 60）取较小那一档：结果必须确定，不能看遍历顺序
        assertEquals("中点取下档（48/56 的中间 52）", 48, BubbleSizeTier.snapDp(52))
        assertEquals("中点取下档（56/64 的中间 60）", 56, BubbleSizeTier.snapDp(60))
    }

    // ═══════════ 窗口宽高 = Compose 画的那颗球 ═══════════

    @Test
    fun `the window side is the same number the bubble draws at, for every tier`() {
        for (size in tiers) {
            val (w, h) = bubbleWindowSizePx(size, dp)
            assertEquals("$size 档的窗口宽必须是这一档本身（不是 56）", dp(size), w)
            assertEquals("横竖必须同一颗数：椭圆球点不准", w, h)
        }
        // 反证：identity 尺之下，48 档不可能还交出 56
        assertEquals(48, bubbleWindowSizePx(48, dp).first)
        assertEquals(64, bubbleWindowSizePx(64, dp).first)
        // 非 identity 的换算（2px/dp）也要走同一颗：宽高都跟着 dp() 走，不许有一侧自己算
        val twice: (Int) -> Int = { it * 2 }
        assertEquals(128 to 128, bubbleWindowSizePx(64, twice))
    }

    // ═══════════ 吸边 / 回弹 ═══════════

    @Test
    fun `左半屏松手贴左边缘`() {
        val (x, y) = bubbleDockTarget(
            x = 200, y = 300, screenW = 1080, screenH = 2400, mainSize = standard, dp = dp
        )
        assertEquals("中心 228 < 540 → 贴左 2dp", 2, x)
        assertEquals("纵向保持原位，只 clamp 不进吸附", 300, y)
    }

    @Test
    fun `右半屏松手贴右边缘`() {
        val (x, _) = bubbleDockTarget(
            x = 800, y = 300, screenW = 1080, screenH = 2400, mainSize = standard, dp = dp
        )
        assertEquals("1080-56-2", 1022, x)
    }

    @Test
    fun `吸边时纵向被按回状态栏与导航条之间`() {
        val (_, top) = bubbleDockTarget(
            x = 200, y = 10, screenW = 1080, screenH = 2400, mainSize = standard, dp = dp
        )
        assertEquals("低于 48 的一律压回 48：球不能盖住状态栏", 48, top)

        val (_, bottom) = bubbleDockTarget(
            x = 200, y = 2390, screenW = 1080, screenH = 2400, mainSize = standard, dp = dp
        )
        assertEquals("2400-56-32", 2312, bottom)
    }

    @Test
    fun `三档的吸边读数都按各自球径扣白`() {
        for (size in tiers) {
            val (left, _) = bubbleDockTarget(
                x = 200, y = 300, screenW = 1080, screenH = 2400, mainSize = size, dp = dp
            )
            assertEquals("$size 档贴左仍是那 2dp 留白", 2, left)

            val (right, _) = bubbleDockTarget(
                x = 800, y = 300, screenW = 1080, screenH = 2400, mainSize = size, dp = dp
            )
            assertEquals("$size 档贴右要扣掉自己的直径：1080-$size-2", 1080 - size - 2, right)

            val (_, bottom) = bubbleDockTarget(
                x = 200, y = 2390, screenW = 1080, screenH = 2400, mainSize = size, dp = dp
            )
            assertEquals("$size 档底部安全区按球径留白", 2400 - size - 32, bottom)
        }
        // 哨兵：大档一定比标准档更往里，读数相同就是尺失效了
        assertTrue(
            abs(bubbleDockTarget(800, 300, 1080, 2400, tiers.last(), dp).first -
                bubbleDockTarget(800, 300, 1080, 2400, standard, dp).first) > 1
        )
    }

    @Test
    fun `贴哪一边是拿当前半径算球心，档位不同结论会变`() {
        // x=510：48 档中心 534、56 档中心 538 都还在左半屏；64 档中心 542 越过中线 → 贴右。
        // 这一格是"半径必须真的跟着档位走"的证据：把 mainSize 写死成 56 的实现在这里当场红。
        val left48 = bubbleDockTarget(510, 300, 1080, 2400, 48, dp).first
        val left56 = bubbleDockTarget(510, 300, 1080, 2400, 56, dp).first
        val right64 = bubbleDockTarget(510, 300, 1080, 2400, 64, dp).first
        assertEquals("48 档算出的中心 534 < 540 → 贴左", 2, left48)
        assertEquals("56 档算出的中心 538 < 540 → 贴左", 2, left56)
        assertEquals("64 档算出的中心 542 > 540 → 贴右 1080-64-2", 1014, right64)
    }

    // ═══════════ 拖拽每一帧 ═══════════

    @Test
    fun `拖拽时整颗球不许离开屏幕`() {
        val (x, y) = bubbleInsideScreen(
            x = -80, y = -80, screenW = 1080, screenH = 2400, mainSize = standard
        )
        assertEquals(0 to 0, x to y)

        val (x2, y2) = bubbleInsideScreen(
            x = 2000, y = 3000, screenW = 1080, screenH = 2400, mainSize = standard
        )
        assertEquals("右下钳位按球径留白", 1024 to 2344, x2 to y2)
    }

    @Test
    fun `拖拽允许顶到状态栏那一条而吸边不允许`() {
        // 这一格是"两把尺别合并"的证据：同一个 y=20，拖拽放行、吸边压回 48
        val dragged = bubbleInsideScreen(x = 10, y = 20, screenW = 1080, screenH = 2400, mainSize = standard)
        val docked = bubbleDockTarget(x = 10, y = 20, screenW = 1080, screenH = 2400, mainSize = standard, dp = dp)

        assertEquals("跟手优先：拖到哪儿算哪儿（只要不出屏）", 20, dragged.second)
        assertEquals("松手归位时才回到安全区", 48, docked.second)
    }

    @Test
    fun `三档的拖拽钳位各按各的直径`() {
        for (size in tiers) {
            val (x, y) = bubbleInsideScreen(
                x = 99_999, y = 99_999, screenW = 1080, screenH = 2400, mainSize = size
            )
            assertEquals("$size 档右下角是 screen-球径：$size", (1080 - size) to (2400 - size), x to y)
        }
    }

    // ═══════════ 临时隐藏后的位置校正 ═══════════

    @Test
    fun `恢复时超屏的位置被校正回安全区`() {
        val (x, y) = bubbleCorrectedOrigin(
            x = 3000, y = 3000, screenW = 1080, screenH = 2400, mainSize = standard, dp = dp
        )
        assertEquals(1024, x)
        assertEquals(2312, y)
    }

    @Test
    fun `屏幕矮到装不下安全区时校正也不抛异常`() {
        // 120-56-32=32 已经低于顶部下限 48，区间反号；这一格要的就是"兜一层 coerceAtLeast"那句
        val (x, y) = bubbleCorrectedOrigin(
            x = 900, y = 900, screenW = 200, screenH = 120, mainSize = standard, dp = dp
        )
        assertEquals("横向下限仍是 0", 144, x)
        assertEquals("纵向区间反号时取下限，不能抛 IllegalArgumentException", 48, y)
    }

    @Test
    fun `三档的恢复校正都按当前档位`() {
        for (size in tiers) {
            val (x, y) = bubbleCorrectedOrigin(
                x = 3000, y = 3000, screenW = 1080, screenH = 2400, mainSize = size, dp = dp
            )
            assertEquals("$size 档横向贴到 1080-$size", 1080 - size, x)
            assertEquals("$size 档纵向留导航条 32", 2400 - size - 32, y)
        }
        // 极端：屏宽只有一颗大球的量 → 兜到 0，不许抛
        val (tinyX, _) = bubbleCorrectedOrigin(500, 500, 60, 2400, BubbleSizeTier.LARGE_DP, dp)
        assertEquals(0, tinyX)
    }

    // ═══════════ 角标与图标：全部跟着档位走（M04） ═══════════

    @Test
    fun `standard tier keeps today's badge offsets word for word`() {
        val (xLeft, yLeft) = BubbleGeometry.badgeOffsetDp(standard, snapLeft = true)
        assertEquals("56 档球在左的偏移与从前逐字相同", 4.6f, xLeft, 0.0001f)
        assertEquals(1.7f, yLeft, 0.0001f)

        val (xRight, yRight) = BubbleGeometry.badgeOffsetDp(standard, snapLeft = false)
        assertEquals("56 档球在右镜像", -20.6f, xRight, 0.0001f)
        assertEquals(1.7f, yRight, 0.0001f)
        assertEquals("标准档的图标直径就是从前那颗 34", 34f, BubbleGeometry.iconSizeDp(standard), 0.0001f)
    }

    @Test
    fun `badge offset and icon size move with the radius so 48 and 64 read differently`() {
        val left = BubbleGeometry.badgeOffsetDp(BubbleSizeTier.SMALL_DP, snapLeft = true)
        val right = BubbleGeometry.badgeOffsetDp(BubbleSizeTier.LARGE_DP, snapLeft = true)
        // 写死 28/33 的实现（旧代码）在这一格红：两档读数一模一样
        assertTrue("48 与 64 档的横向偏移必须不同（实到 $left / $right）", left.first != right.first)
        assertTrue("纵向偏移也要跟着半径走", left.second != right.second)
        // 等比：48 档 = 标准档 × 48/56，64 档 = 标准档 × 64/56
        assertEquals(4.6f * 48f / 56f, left.first, 0.0001f)
        assertEquals(1.7f * 48f / 56f, left.second, 0.0001f)
        assertEquals(4.6f * 64f / 56f, right.first, 0.0001f)
        // 半径越大、外推越远（不许出现"球变大、角标反而往里"）
        assertTrue(abs(right.first) > abs(left.first))

        val icon48 = BubbleGeometry.iconSizeDp(BubbleSizeTier.SMALL_DP)
        val icon64 = BubbleGeometry.iconSizeDp(BubbleSizeTier.LARGE_DP)
        assertTrue("图标直径随档位走：$icon48 / $icon64", icon48 != icon64)
        assertEquals(34f * 48f / 56f, icon48, 0.0001f)
        assertEquals(34f * 64f / 56f, icon64, 0.0001f)
        // 图标永远比球小一圈（比例失守=画不出去）
        for (size in tiers) {
            assertTrue("$size 档图标应小于球径", BubbleGeometry.iconSizeDp(size) < size)
        }
        // 红点本身**不**缩：未读点再小就看不出"有新消息"
        assertEquals(10, BubbleGeometry.BADGE_SIZE_DP)
    }

    // ═══════════ 形状闸：档位只有一个入口，画的那一侧不许留写死的半径 ═══════════

    /** 单测的工作目录可能是模块目录也可能是仓库根，两种都接住。 */
    private fun mainSource(relative: String): File =
        File("src/main/java/com/lovebrain/app/$relative").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/$relative")

    @Test
    fun `the old single-size constant is gone and nothing reads a second truth`() {
        val window = mainSource("service/OverlayBubbleWindow.kt")
        val panel = mainSource("service/OverlayPanelWindow.kt")
        val bubble = mainSource("ui/bubble/FloatingBubble.kt")
        val config = mainSource("AppConfig.kt")
        for (f in listOf(window, panel, bubble, config)) {
            assertTrue("$f 读不到——这一格会恒绿", f.isFile)
        }
        for ((name, file) in listOf(
            "OverlayBubbleWindow.kt" to window,
            "OverlayPanelWindow.kt" to panel,
            "FloatingBubble.kt" to bubble,
            "AppConfig.kt" to config
        )) {
            val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
            assertFalse(
                "$name 里不许再读 AppConfig.BUBBLE_SIZE：那颗单一尺寸常量已被 BubbleSizeTier 取代，" +
                    "留着它就有第二份真值（窗口 56dp、球体另算就是书 §1 点名的坏法）",
                code.contains("BUBBLE_SIZE")
            )
        }
        // 窗口那侧：所有几何都从 bubbleSizePx()/bubbleWindowSizePx 这一颗走
        val windowCode = SourceScan.maskComments(window.readText(Charsets.UTF_8))
        assertTrue("窗口宽高必须走 bubbleWindowSizePx 那一个算法", windowCode.contains("bubbleWindowSizePx("))
        assertEquals("档位 dp → px 只有 bubbleSizePx 这一处算法",
            1, windowCode.lines().count { it.trim().startsWith("private fun bubbleSizePx(") })
    }

    @Test
    fun `the bubble draws its geometry from the tier instead of hardcoded offsets`() {
        val code = SourceScan.maskComments(mainSource("ui/bubble/FloatingBubble.kt").readText(Charsets.UTF_8))
        // 旧代码把 4.6 / -20.6 / 1.7 三颗偏移裸写在 Modifier 链上（那正是"钉在 28dp 半径上"的写法）
        for (literal in listOf("4.6.dp", "20.6.dp", "1.7.dp")) {
            assertFalse("角标偏移不许再裸写字面量 $literal，必须走 BubbleGeometry.badgeOffsetDp", code.contains(literal))
        }
        assertTrue("球径读的是状态里那一颗档位", code.contains("val mainSize = state.sizeDp"))
        assertTrue("偏移与图标直径都从 BubbleGeometry 推", code.contains("BubbleGeometry.badgeOffsetDp(mainSize"))
        assertTrue(code.contains("BubbleGeometry.iconSizeDp(mainSize"))
    }
}
