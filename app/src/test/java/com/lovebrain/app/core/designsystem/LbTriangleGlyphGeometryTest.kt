package com.lovebrain.app.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 基线 v1 §3.10 那颗公共件的**几何判据**：角到底圆没圆，只能用几何证。
 *
 * ⚠ 这里刻意不断言"存在一个 Canvas"，也不截图：
 * `LbTriangleGlyph` 画的是一条被**切过角**的 Path，能读出来的事实全在
 * [lbRoundedGlyphCorners] 交回的那三份值对象里（圆心、两个切点、半径、扫角）。
 * 于是这格跑在纯 JVM 上（不挂 Robolectric、不碰 `android.graphics`）——
 * 判的是数学，不是渲染；渲染那一半归真机截图那一栏（本机没有设备，已如实记为未验证）。
 *
 * 每一格都配了反例，写清"什么坏实现会把它压红"：
 * - 退回旧的三条 `lineTo` 实心尖 Path ⇒ 第 3/4/5 格全红（根本不会有圆弧与切点）；
 * - 改按"同色粗描边外扩"糊圆角（candidate-a 演示的那种手段）⇒ 第 6 格红（外形越过盒子）；
 * - 把比例写成固定 dp ⇒ 第 1 格红（10dp 那一档量不出同一个比例）；
 * - 把顶点顺手挪位（对齐 1.3.1 的那条形状线） ⇒ 第 2 格红；
 * - 半径大到塞不下还硬画 ⇒ 第 8 格红（切点在同一条边上互相越过，Skia 会打出小环）。
 */
class LbTriangleGlyphGeometryTest {

    /** 20dp 字形在 xhdpi（density 2.0）上的盒子：40px。比例判据与 dp 无关，用 px 算。 */
    private val heroSide = 40f

    /** 面板展开指示器那一档：10dp → 20px（[LB_GLYPH_CORNER_RATIO] 必须同样吃得下） */
    private val indicatorSide = 20f

    private fun corners(shape: LbTriangleGlyphShape, side: Float) =
        lbRoundedGlyphCorners(lbGlyphTriangleVertices(shape, side), side * LB_GLYPH_CORNER_RATIO)

    private fun verts(shape: LbTriangleGlyphShape, side: Float) =
        lbGlyphTriangleVertices(shape, side)

    // ═══════════ 1. 比例只有一颗数，而且是基线定下的那颗 ═══════════

    /**
     * 反例：把圆化改成固定 dp（或抄 `LoveBrainShape.sm` 那颗 6dp）——20dp 那一档看着还行，
     * 10dp 那一档（面板展开指示器）就会角更硬、形状自己变小；这里按**两个尺寸各量一次**判。
     * 停止方块与三角必须出自同一个数（同件同比例），所以两边一起量。
     */
    @Test
    fun `the rounding is one ratio and both shapes read it from the same place`() {
        assertEquals("§3.10 定版比例：边长 × 0.18", 0.18f, LB_GLYPH_CORNER_RATIO, 1e-6f)

        // 三角：名义半径 = 边长 × 比例，两档尺寸各量一次
        listOf(heroSide, indicatorSide).forEach { side ->
            val arcs = corners(LbTriangleGlyphShape.TriangleRight, side)
            assertEquals("三个角都要有圆弧", 3, arcs.size)
            arcs.forEach { arc ->
                assertEquals("${side}px 字形的圆化半径", side * LB_GLYPH_CORNER_RATIO, arc.radiusPx, 1e-3f)
            }
        }

        // 停止块：同一颗函数、同一个比例（20dp → 3.6dp），不是旧的那颗固定 6dp
        assertEquals("20dp 停止块 ≈ 3.6dp", 20f * LB_GLYPH_CORNER_RATIO, lbGlyphCornerSize(20.dp).value, 1e-4f)
        assertEquals("10dp 停止块 ≈ 1.8dp", 10f * LB_GLYPH_CORNER_RATIO, lbGlyphCornerSize(10.dp).value, 1e-4f)
        assertFalse(
            "圆角退回固定档（旧 LoveBrainShape.sm 的 6dp）了：可见字形一变就不同步，§3.10 要的是比例",
            abs(lbGlyphCornerSize(20.dp).value - 6f) < 1e-3f
        )
    }

    // ═══════════ 2. 顶点没挪：圆的是角，不是重新设计一颗键 ═══════════

    /**
     * 反例：有人"顺手"把三角画正（等边、居中、或把尖挪到中心）——屏幕上就不是 1.3.1 那一条形状线了。
     * 两条顶点表都是**从被替换掉的那两段旧 Path 逐字抄来的**：
     * `HomeComponents.kt` 旧 `moveTo(0,0) → lineTo(w, h/2) → lineTo(0, h)`；
     * `ui/panel/DragHandle.kt` 旧 `moveTo(w/2,h) → lineTo(w,0) → lineTo(0,0)`（朝下，靠调用方旋转）。
     */
    @Test
    fun `the vertex table is the old path with nothing moved`() {
        assertEquals(
            listOf(Offset(0f, 0f), Offset(40f, 20f), Offset(0f, 40f)),
            verts(LbTriangleGlyphShape.TriangleRight, heroSide)
        )
        assertEquals(
            listOf(Offset(0f, 0f), Offset(40f, 0f), Offset(20f, 40f)),
            verts(LbTriangleGlyphShape.TriangleDown, heroSide)
        )
        // 停止块不给顶点表：它走 RoundedCornerShape 那一档，同一颗比例（见第 1 格）
        assertEquals("方块不是三角", emptyList<Offset>(), verts(LbTriangleGlyphShape.StopSquare, heroSide))
        // 顺时针（屏幕坐标 y 轴朝下）：三点的叉积同号 ⇒ 遍历方向与圆弧扫向对得上
        val v = verts(LbTriangleGlyphShape.TriangleRight, heroSide)
        val cross = (v[1].x - v[0].x) * (v[2].y - v[0].y) - (v[1].y - v[0].y) * (v[2].x - v[0].x)
        assertTrue("顶点表必须是顺时针，否则切角方向会反过来：cross=$cross", cross > 0f)
    }

    // ═══════════ 3. 三个角真的各是一段圆弧（半径与两档尺寸都对得上）═══════════

    /**
     * 反例：只圆了尖角那一颗（另外两颗仍是 `lineTo` 交点）⇒ `size == 3` 与逐颗半径两句一起红。
     * [LbGlyphCornerArc.clamped] 也必须为假：20dp 与 10dp 两档都塞得下 0.18 的比例，
     * 一旦哪天有人把比例抬过头却没发觉"实际半径被夹小了"，这一格会先说出来。
     */
    @Test
    fun `all three corners carry an arc of exactly the nominal radius`() {
        listOf(heroSide, indicatorSide).forEach { side ->
            listOf(LbTriangleGlyphShape.TriangleRight, LbTriangleGlyphShape.TriangleDown).forEach { shape ->
                val arcs = corners(shape, side)
                assertEquals("$shape @ ${side}px 必须三个角全圆", 3, arcs.size)
                arcs.forEachIndexed { i, arc ->
                    val nominal = side * LB_GLYPH_CORNER_RATIO
                    assertEquals("$shape 第 $i 个角的半径", nominal, arc.radiusPx, 1e-3f)
                    assertFalse("$shape 第 $i 个角被夹过（名义比例没实现）", arc.clamped)
                    assertEquals("圆心到入边切点 = 半径", nominal, dist(arc.center, arc.tangentFrom), 1e-3f)
                    assertEquals("圆心到出边切点 = 半径", nominal, dist(arc.center, arc.tangentTo), 1e-3f)
                }
            }
        }
    }

    // ═══════════ 4. 圆弧与直边相切（垂直），不是斜着一刀 ═══════════

    /**
     * "看着圆"和"真圆"差别就在这两句上：半径必须**垂直于**那条边，且切点必须**落在那条边上**。
     * 反例：用 `quadraticBezierTo(顶点, 出边切点)` 糊过去——那是一条贝塞尔、没有确定的半径与切点，
     * 控制点还在尖角上，放大截图会看见一个鼓包（candidate-a 给的"双落笔描边"也是同一族）。
     */
    @Test
    fun `each arc meets both edges perpendicularly at a point on the edge`() {
        listOf(LbTriangleGlyphShape.TriangleRight, LbTriangleGlyphShape.TriangleDown).forEach { shape ->
            val vertices = verts(shape, heroSide)
            corners(shape, heroSide).forEachIndexed { i, arc ->
                val prev = vertices[(i + 2) % 3]
                val next = vertices[(i + 1) % 3]
                // 入边方向：从本顶点指向 prev；出边方向：指向 next
                val inDir = unit(prev - arc.vertex)
                val outDir = unit(next - arc.vertex)
                assertEquals("入边半径必须垂直于那条边", 0f, dot(arc.tangentFrom - arc.center, inDir), 1e-3f)
                assertEquals("出边半径必须垂直于那条边", 0f, dot(arc.tangentTo - arc.center, outDir), 1e-3f)
                // 切点在边上（不是悬在旁边）：到本顶点的距离就是切角长 t，且方向与那条边完全一致
                // 容差给到 0.02°：acos 在 cos≈1 那一端放大浮点尾差，判"同向"这件事 0.02° 已经足够严
                assertEquals("入边切点方向", 0f, angleBetween(arc.tangentFrom - arc.vertex, inDir), 0.02f)
                assertEquals("出边切点方向", 0f, angleBetween(arc.tangentTo - arc.vertex, outDir), 0.02f)
            }
        }
    }

    // ═══════════ 5. 尖角被退掉了：原来的顶点不再落在轮廓上 ═══════════

    /**
     * 这一格就是"角到底圆没圆"最直白的那一条：旧 Path 的顶点此刻必须**离轮廓最近点还差一个切角长**。
     * 反例：尖角回来（`t == 0`）⇒ 第二句红；把半径写成 0 ⇒ 三句全红。
     */
    @Test
    fun `the old apex is no longer a point on the outline`() {
        val arcs = corners(LbTriangleGlyphShape.TriangleRight, heroSide)
        arcs.forEachIndexed { i, arc ->
            val tangent = dist(arc.vertex, arc.tangentFrom)
            assertTrue("第 $i 个角一点都没退（尖角还在）：t=$tangent", tangent > 0.5f)
            assertEquals("两个切点对称地退在同一条角的两条边上", dist(arc.vertex, arc.tangentTo), tangent, 1e-3f)
            // 圆心一定比切点更靠里，所以它到顶点的距离必然 > 半径——尖角的"最外面那一格"确实没了
            assertTrue("第 $i 个角的圆心没往里收：${dist(arc.vertex, arc.center)} <= ${arc.radiusPx}",
                dist(arc.vertex, arc.center) > arc.radiusPx)
        }
    }

    // ═══════════ 6. 切角不外扩：整块形状还在交进去的那个盒子里 ═══════════

    /**
     * §3.10 与页面那两条账的接缝：`HomeComponents.kt` 的可见轴写着 20dp、热区轴写着 48dp，
     * 而 `HomeHeroActionTest` 钉着"热区垫到 48 就够了，不许把可见那一格撑大"（≤56dp）。
     * 走"同色粗描边 + StrokeJoin.Round"那条改法时，外形会整体向外扩 描边宽/2 ⇒ 这一句当场红。
     *
     * 反例之外还有**正向证人**：同一把尺拿去量一颗故意外扩的圆弧（模拟描边做法），必须判它越界——
     * 不然这条 containment 可能是恒真的死尺。
     */
    @Test
    fun `rounding cuts the corners instead of inflating the outline`() {
        listOf(LbTriangleGlyphShape.TriangleRight, LbTriangleGlyphShape.TriangleDown).forEach { shape ->
            corners(shape, heroSide).forEach { arc ->
                assertTrue(
                    "$shape 的圆弧越出盒子了（这就是描边外扩那一族的形状）：$arc",
                    containedInBox(arc, heroSide)
                )
            }
        }
        // 证人：把圆心推到盒子边上、半径不变 ⇒ 圆弧有一半跑到盒子外（描边外扩就是这么长出来的）
        val expanded = LbGlyphCornerArc(
            vertex = Offset(heroSide, heroSide / 2f),
            center = Offset(heroSide, heroSide / 2f),
            tangentFrom = Offset(heroSide, heroSide / 2f - 7.2f),
            tangentTo = Offset(heroSide, heroSide / 2f + 7.2f),
            radiusPx = 7.2f,
            startAngleDeg = -90f,
            sweepDeg = 180f,
            clamped = false
        )
        assertFalse(
            "containment 那把尺是死的：故意外扩的圆弧都没抓到",
            containedInBox(expanded, heroSide)
        )
    }

    // ═══════════ 7. 扫角与起点自洽：画 Path 时喂的那两个数不是凑的 ═══════════

    /**
     * `Path.arcTo` 吃的是（起点角、扫角）。这两句判的是：按这两个数走**正好**落到出边切点，
     * 而且扫过的量 = 180° − 内角（凸角的圆弧唯一确定的那一个值）。
     * 反例：把 sweep 写成固定 ±90°、或起点角按另一种坐标口径算 ⇒ 第一段红（端点差出十几 dp）。
     */
    @Test
    fun `the arc angles land exactly on the second tangent point`() {
        listOf(LbTriangleGlyphShape.TriangleRight, LbTriangleGlyphShape.TriangleDown).forEach { shape ->
            val vertices = verts(shape, heroSide)
            corners(shape, heroSide).forEachIndexed { i, arc ->
                val prev = vertices[(i + 2) % 3]
                val next = vertices[(i + 1) % 3]
                val theta = angleBetween(unit(prev - arc.vertex), unit(next - arc.vertex))
                assertEquals("$shape 第 $i 个角的扫角 = 180° − 内角", 180f - theta, abs(arc.sweepDeg), 0.2f)

                val rad = (arc.startAngleDeg + arc.sweepDeg) * PI.toFloat() / 180f
                val rebuilt = Offset(
                    arc.center.x + arc.radiusPx * cos(rad),
                    arc.center.y + arc.radiusPx * sin(rad)
                )
                assertEquals("$shape 第 $i 个角的圆弧终点没落在出边切点上", 0f, dist(rebuilt, arc.tangentTo), 0.05f)
            }
        }
    }

    // ═══════════ 8. 盒子塞不下时夹住，而不是让两个角互相越过 ═══════════

    /**
     * 极端输入也要有确定的形状：半径抬到边长的 45% 时，切角长会超过邻边的 1/3，
     * 同一条边两头各吃掉一半以上就会**重叠**（Skia 在那种情况下画出小环）。
     * 这一格判三件事：夹住了（`clamped`）、切角长不超过邻边短的那条的 1/3、
     * 每条边两端退掉的长度之和不超过那条边本身。
     * 反例：没有夹取逻辑 ⇒ 三句一起红；夹取写反（夹成 0）⇒ 第 3 句红而第 1 句绿，那种绿也算红。
     */
    @Test
    fun `a box too small for the ratio clamps instead of letting two corners overlap`() {
        val vertices = verts(LbTriangleGlyphShape.TriangleRight, heroSide)
        val arcs = lbRoundedGlyphCorners(vertices, heroSide * 0.45f)
        assertEquals(3, arcs.size)
        arcs.forEachIndexed { i, arc ->
            assertTrue("第 $i 个角该被夹住却没夹", arc.clamped)
            val lenIn = dist(arc.vertex, vertices[(i + 2) % 3])
            val lenOut = dist(arc.vertex, vertices[(i + 1) % 3])
            val maxTangent = min(lenIn, lenOut) / 3f
            assertTrue(
                "第 $i 个角的切点越过了邻边的 1/3：${dist(arc.vertex, arc.tangentFrom)} > $maxTangent",
                dist(arc.vertex, arc.tangentFrom) <= maxTangent + 1e-3f
            )
            assertTrue(arc.radiusPx < heroSide * 0.45f)
        }
        // 每条边两端各退掉的那两段，加起来必须还剩得下一段直线
        for (i in 0 until 3) {
            val from = arcs[i]
            val to = arcs[(i + 1) % 3]
            val edge = dist(from.vertex, to.vertex)
            val used = dist(from.vertex, from.tangentTo) + dist(to.vertex, to.tangentFrom)
            assertTrue("第 $i 条边被两个角吃穿了：used=$used edge=$edge", used <= edge + 1e-3f)
        }
    }

    // ═══════════ 9. 退化输入不画、也不抛 ═══════════

    /**
     * 这一颗挂在首页那一屏上：布局约束塌了（父给 0 高）或顶点重合时，
     * 交回空集 = 什么都不画，**不许就地抛**（抛出去就是整屏带走，比少画一颗键严重得多）。
     * 反例：不做守卫 ⇒ 这里直接以异常收场（那也是一种"红"，但报出来的是一串 NaN）。
     */
    @Test
    fun `degenerate input draws nothing instead of throwing`() {
        val vertices = verts(LbTriangleGlyphShape.TriangleRight, heroSide)
        assertEquals(0, lbRoundedGlyphCorners(emptyList(), 7.2f).size)
        assertEquals(0, lbRoundedGlyphCorners(vertices.take(2), 7.2f).size)
        assertEquals("半径 0 = 没有可圆的角", 0, lbRoundedGlyphCorners(vertices, 0f).size)
        assertEquals("负半径是坏输入，不是镜像形状", 0, lbRoundedGlyphCorners(vertices, -3f).size)
        assertEquals(
            "边长塌成 0（父约束没了）⇒ 空集，不许算出 NaN",
            0, lbRoundedGlyphCorners(listOf(Offset.Zero, Offset.Zero, Offset.Zero), 7.2f).size
        )
        assertEquals(
            "两点重合 ⇒ 有一条边长为 0，同样交回空集",
            0, lbRoundedGlyphCorners(listOf(Offset.Zero, Offset.Zero, Offset(40f, 20f)), 7.2f).size
        )
        assertEquals("边长 0 的顶点表也不该给出圆弧",
            0, corners(LbTriangleGlyphShape.TriangleRight, 0f).size)
        assertTrue("NaN 输入不许算出东西", lbRoundedGlyphCorners(vertices, Float.NaN).isEmpty())
    }

    // ── 小尺：都按 px 量，与 density 无关 ──

    /** 圆弧整圈（圆心 ± 半径四个方向）是否都还在 `[0, side]²` 那个盒子里 */
    private fun containedInBox(arc: LbGlyphCornerArc, side: Float): Boolean {
        val r = arc.radiusPx
        val eps = 1e-3f
        return arc.center.x - r >= -eps && arc.center.x + r <= side + eps &&
            arc.center.y - r >= -eps && arc.center.y + r <= side + eps
    }

    private fun dist(a: Offset, b: Offset): Float = hypot(b.x - a.x, b.y - a.y)

    private fun unit(v: Offset): Offset {
        val len = hypot(v.x, v.y)
        assertTrue("这条向量长度塌成 0，判据算不出角度：$v", len > 0f)
        return Offset(v.x / len, v.y / len)
    }

    private fun dot(a: Offset, b: Offset): Float = a.x * b.x + a.y * b.y

    /** 两个单位向量之间的夹角（度）：0 = 同向 */
    private fun angleBetween(a: Offset, b: Offset): Float {
        val cos = dot(a, b).coerceIn(-1f, 1f)
        return acos(cos) * 180f / PI.toFloat()
    }
}
