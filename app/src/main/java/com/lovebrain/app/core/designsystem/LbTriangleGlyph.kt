package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * 基线 v1 §3.10 那颗公共件：**实心三角 / 停止方块的圆角字形**。
 *
 * 用户原话（第 6 条）："所有朝右三角形的三个角都要真圆角，不许是尖角。"
 * 圆化半径按**比例**算，不按固定 dp：`半径 = 字形边长 × [LB_GLYPH_CORNER_RATIO]`
 * （20dp → 3.6dp）。写成固定 dp 的话，同一颗键在 10dp 那一档（面板展开指示器）就会显得
 * 角更硬，而放大截图上 20dp 那一档又糊成一团——比例才是"这颗键长什么样"的真源。
 *
 * ## 为什么走"切角"而不是"粗描边"
 *
 * 另一种做法是同一个 Path 先 fill 再同色 `Stroke(width, join = Round)`，圆角半径 = 描边宽的一半。
 * 它更短，但**形状整体向外扩 width/2**：可见字形就不再是交进去的那个数
 * （首页那颗的账是 `HomeDimens.GLYPH_VISIBLE_DP = 20dp` 可见 + 48dp 热区两轴，
 *  外扩会把可见与热区两条轴重新绑在一起，`HomeHeroActionTest` 钉的"热区垫到 48 就够了，
 *  不许把可见那一格撑大"当场被顶破）。所以这里改的是 **Path 的顶点**：盒子一寸不涨。
 *
 * ## 边界（这一颗不管的事）
 *
 * 只管**实心**三角与停止方块。返回 chevron、`PanelHeader.kt:157`、`ic_chevron_down.xml`、
 * `LbTopBar.kt:134`、`ProviderSection.kt:308`、`LbActionCard.kt` 尾部那颗
 * `Icons.AutoMirrored.Filled.KeyboardArrowRight` 都是**线性指示器**，
 * 不是播放形状，一律不许换成这一颗（§3.10 明写）。
 */

/**
 * 实心字形的圆化比例（§3.10 定版值：边长 × 0.18）。
 *
 * 这颗比例**只住在这里**：`Dimens.kt` 管的是容器与热区那几档下限，字形圆化不是间距 token，
 * 上提过去会让下一个人以为它能被别的件借走。停止方块与三角共用这一个数（同件同比例）。
 */
const val LB_GLYPH_CORNER_RATIO = 0.18f

/**
 * 字形的墨色——**颜色来自词表，不是来自参数**（与 [LbTextActionTone] 同一课）。
 *
 * 开一个 `color: Color` 口子就等于承认"任何一页都能再挑一颗蓝"，而第三颗蓝没人认得出来。
 * 两档各有去处：[LbTriangleGlyphTone.Accent] = 首页那颗 ▶/■ 与面板展开指示器（今天两处都涂
 * `Primary`）；[LbTriangleGlyphTone.Muted] = 行尾那一族次要指示（与 `LbActionCard` 尾部箭头
 * 同一个 `TextHint` 语气）。下一位想要第三种墨色时，该问的是"这一档语气住在哪"，不是加参数。
 */
enum class LbTriangleGlyphTone { Accent, Muted }

internal val LbTriangleGlyphTone.ink: Color
    get() = when (this) {
        LbTriangleGlyphTone.Accent -> Primary
        LbTriangleGlyphTone.Muted -> TextHint
    }

/**
 * 画什么形——按**方向 + 语义**命名，三档就三档，不开"顶点表"那种自由口子。
 *
 * [TriangleRight] 是用户点名那颗"朝右三角 = 开始"；[TriangleDown] 是面板那一族
 * 折叠/展开指示器（`ui/panel/DragHandle.kt` 里那颗 10dp 的就是它的收件人，
 * 旋转角仍由调用方的 `graphicsLayer { rotationZ }` 拿着，方向语义一个字不改）；
 * [StopSquare] 是停止块，圆角与三角同比例——两颗形状必须出自同一颗件、同一个数，
 * 否则"开始/停止"会变成两种语言。
 */
enum class LbTriangleGlyphShape { TriangleRight, TriangleDown, StopSquare }

/** 停止方块那一档的圆角半径（与三角同一个比例，只在这里算一次） */
internal fun lbGlyphCornerSize(sizeDp: Dp): Dp = sizeDp * LB_GLYPH_CORNER_RATIO

/**
 * 一个被圆化之后的角：圆弧 + 两个切点。
 *
 * 这一颗是**给判据读的形状**，不是画出来的东西：`LbTriangleGlyph` 用它喂 [Path]，
 * 而 `LbTriangleGlyphGeometryTest` 拿它逐项核"角到底圆没圆"——半径、切点是否落在原边上、
 * 圆心到切点的连线是否垂直于那条边、原尖角离路径最近点多远、整块是否还在盒子内。
 * 那些读数不出自 `Path`（Path 在纯 JVM 侧根本画不动），出自这一颗值对象。
 *
 * 方向约定：沿 `[tangentFrom] → 弧 → [tangentTo]` 走就是**顺时针**遍历多边形时经过这个角的那一段
 * （[tangentFrom] 在"来自上一个顶点"的那条边上，[tangentTo] 在"去往下一个顶点"的那条边上）。
 */
internal data class LbGlyphCornerArc(
    val vertex: Offset,
    val center: Offset,
    val tangentFrom: Offset,
    val tangentTo: Offset,
    val radiusPx: Float,
    val startAngleDeg: Float,
    val sweepDeg: Float,
    /** 这条边的邻边太短、按比例塞不下时把半径夹小过——夹过就不是"名义比例"了，必须看得见 */
    val clamped: Boolean
)

/**
 * 三角形顶点表（盒子边长 [sidePx]，顺时针）。
 *
 * 两个三角档都是从**今天屏幕上那两条 Path 原样抄下来的**，只是随后被圆化：
 * - `HomeComponents.kt` 原来写 `moveTo(0,0) → lineTo(w, h/2) → lineTo(0,h)`；
 * - `DragHandle.kt` 原来写 `moveTo(w/2,h) → lineTo(w,0) → lineTo(0,0)`（默认朝下，靠调用方旋转）。
 * 顶点没挪，所以外形、重心与 1.3.1 那一条对齐关系都没被这次改造顺手动掉。
 */
internal fun lbGlyphTriangleVertices(shape: LbTriangleGlyphShape, sidePx: Float): List<Offset> =
    when (shape) {
        LbTriangleGlyphShape.TriangleRight -> listOf(
            Offset(0f, 0f),
            Offset(sidePx, sidePx / 2f),
            Offset(0f, sidePx)
        )
        LbTriangleGlyphShape.TriangleDown -> listOf(
            Offset(0f, 0f),
            Offset(sidePx, 0f),
            Offset(sidePx / 2f, sidePx)
        )
        LbTriangleGlyphShape.StopSquare -> emptyList()
    }

/**
 * 把凸多边形的每个角收成圆弧（**切角，不外扩**）。
 *
 * 数学是欧氏那一条，不是"看着差不多"：顶点内角 θ、目标半径 r 时
 * - 切点沿两条边各退 `t = r / tan(θ/2)`；
 * - 圆心在角平分线上、离顶点 `d = r / sin(θ/2)`；
 * 于是圆心到切点恰好 = r，且那条半径垂直于边——这正是"圆弧与直边相切"的定义，
 * 判据测试核的就是这两条，而不是"存在一个 Canvas"。
 *
 * 夹取规则：`t` 不许超过相邻两条边里较短那条的 1/3（[clampEdgeFraction]）。
 * 三个角各自最多吃掉邻边的 1/3，两边加起来 2/3 < 整条边 ⇒ **切点永不重叠、不会打出小环**。
 * 被夹时实际半径小于名义半径，[LbGlyphCornerArc.clamped] 会翻成 true 并如实交出夹后的半径。
 *
 * 退化输入（少于三个顶点、边长或半径 ≤ 0、重合顶点、内角接近平角）一律回空集：
 * 画不出就等于不画，**不许就地抛**——这一颗挂在首页那一屏上，一颗 NaN 不该把整屏带走。
 */
internal fun lbRoundedGlyphCorners(
    vertices: List<Offset>,
    radiusPx: Float,
    clampEdgeFraction: Float = 1f / 3f
): List<LbGlyphCornerArc> {
    if (vertices.size < 3 || radiusPx <= 0f || !radiusPx.isFinite()) return emptyList()
    val out = ArrayList<LbGlyphCornerArc>(vertices.size)
    for (i in vertices.indices) {
        val v = vertices[i]
        val prev = vertices[(i + vertices.size - 1) % vertices.size]
        val next = vertices[(i + 1) % vertices.size]
        val lenIn = lbGlyphDistance(v, prev)
        val lenOut = lbGlyphDistance(v, next)
        if (lenIn <= 0f || lenOut <= 0f) return emptyList()
        val inUnit = Offset((prev.x - v.x) / lenIn, (prev.y - v.y) / lenIn)
        val outUnit = Offset((next.x - v.x) / lenOut, (next.y - v.y) / lenOut)
        val cos = (inUnit.x * outUnit.x + inUnit.y * outUnit.y).coerceIn(-1f, 1f)
        val theta = acos(cos)
        val half = theta / 2f
        val sinHalf = sin(half)
        val tanHalf = tan(half)
        // 接近平角 = 这三点几乎共线，那一格本来就没什么可圆
        if (sinHalf < 1e-4f || tanHalf <= 0f || !theta.isFinite()) return emptyList()
        val bisectorLength = hypot(inUnit.x + outUnit.x, inUnit.y + outUnit.y)
        if (bisectorLength <= 1e-4f) return emptyList()
        val bisector = Offset((inUnit.x + outUnit.x) / bisectorLength, (inUnit.y + outUnit.y) / bisectorLength)

        var radius = radiusPx
        var clamped = false
        val tangent = radius / tanHalf
        val tangentMax = min(lenIn, lenOut) * clampEdgeFraction
        if (tangent > tangentMax) {
            radius = tangentMax * tanHalf
            clamped = true
        }
        val center = Offset(v.x + bisector.x * (radius / sinHalf), v.y + bisector.y * (radius / sinHalf))
        val finalTangent = radius / tanHalf
        val tangentFrom = Offset(v.x + inUnit.x * finalTangent, v.y + inUnit.y * finalTangent)
        val tangentTo = Offset(v.x + outUnit.x * finalTangent, v.y + outUnit.y * finalTangent)
        out += LbGlyphCornerArc(
            vertex = v,
            center = center,
            tangentFrom = tangentFrom,
            tangentTo = tangentTo,
            radiusPx = radius,
            startAngleDeg = angleDeg(center, tangentFrom),
            sweepDeg = sweepDeg(center, tangentFrom, tangentTo),
            clamped = clamped
        )
    }
    return out
}

/** 两点距离（不用 `Offset.distance()`：那颗在纯 JVM 侧会把别的 android 类型一起牵进来） */
internal fun lbGlyphDistance(a: Offset, b: Offset): Float = hypot(b.x - a.x, b.y - a.y)

/** 屏幕坐标（y 轴朝下）里 `from` 相对 `center` 的极角，单位度——`arcTo` 吃的就是这个口径 */
internal fun angleDeg(center: Offset, point: Offset): Float =
    (atan2(point.y - center.y, point.x - center.x) * 180f / PI.toFloat())

/** 从 [from] 到 [to] 的最短带号扫角（凸角的圆弧一定 < 180°，走最短这条就是它） */
internal fun sweepDeg(center: Offset, from: Offset, to: Offset): Float {
    val delta = angleDeg(center, to) - angleDeg(center, from)
    return ((delta + 540f) % 360f) - 180f
}

/**
 * 把圆化后的角拼成一条闭合 Path（直线段 + 三段圆弧）。
 *
 * `forceMoveTo = false` 让圆弧从**当前笔位**接一条直线起笔——当前笔位正是那条切点，
 * 于是这段"接笔线"长度 0，不会画出多余的一道。收尾用 [close]，与旧的两条 Path 同形。
 */
internal fun Path.appendRoundedGlyphCorners(corners: List<LbGlyphCornerArc>) {
    if (corners.isEmpty()) return
    val first = corners.first()
    // 从第 0 个角的出边切点起笔，然后按顺时针依次"走到下一个角的入边切点 → 抹过那段圆弧"
    moveTo(first.tangentTo.x, first.tangentTo.y)
    for (i in 1 until corners.size) {
        val corner = corners[i]
        lineTo(corner.tangentFrom.x, corner.tangentFrom.y)
        addArc(corner)
    }
    lineTo(first.tangentFrom.x, first.tangentFrom.y)
    addArc(first)
    close()
}

private fun Path.addArc(corner: LbGlyphCornerArc) {
    val r = corner.radiusPx
    arcTo(
        rect = Rect(
            corner.center.x - r,
            corner.center.y - r,
            corner.center.x + r,
            corner.center.y + r
        ),
        startAngleDegrees = corner.startAngleDeg,
        sweepAngleDegrees = corner.sweepDeg,
        forceMoveTo = false
    )
}

/**
 * 一颗圆了角的实心字形：三角或停止块。
 *
 * 三个旋钮只有"多大、哪一档墨、什么形"，**没有 `color:`、没有 `cornerRadius:`**——
 * 比例由 [LB_GLYPH_CORNER_RATIO] 独享，页面拿不到它，也就长不出第二种圆角。
 *
 * 三轴分离（§3.1）：这一颗只管**可见尺寸**那一轴，边长就是交进来的 [sizeDp]，
 * 盒子外面一寸不涨；热区归调用方那颗可点盒子自己垫（首页那颗走 `AppDimens.TOUCH_TARGET_MIN_DP`）。
 *
 * 只读不点：体里没有 `clickable`，谁负责点击仍由挂着交互的那一层决定——
 * 首页那一格唯一的可点节点是 `AdvisorControlButton` 那颗热区盒，字形自己只是一格像素。
 */
@Composable
fun LbTriangleGlyph(
    sizeDp: Dp,
    tone: LbTriangleGlyphTone,
    shape: LbTriangleGlyphShape,
    modifier: Modifier = Modifier
) {
    val ink = tone.ink
    when (shape) {
        // 停止块：同一颗件、同一个比例（§3.10"停止方块同件同比例"），不再抄一颗固定 6dp
        LbTriangleGlyphShape.StopSquare -> Box(
            modifier = modifier
                .size(sizeDp)
                .background(ink, RoundedCornerShape(lbGlyphCornerSize(sizeDp)))
        )
        // 三角：可见盒子就是交进来的边长，切角只往里收、一寸不向外扩
        else -> Canvas(modifier = modifier.size(sizeDp)) {
            val side = min(size.width, size.height)
            val corners = lbRoundedGlyphCorners(
                vertices = lbGlyphTriangleVertices(shape, side),
                radiusPx = side * LB_GLYPH_CORNER_RATIO
            )
            // 退化输入（父约束塌了、边长 0）就什么都不画：这一颗挂在首页那一屏上，
            // 少画一个图形不丢事，抛出去会带走整屏。
            if (corners.isNotEmpty()) drawPath(Path().apply { appendRoundedGlyphCorners(corners) }, ink)
        }
    }
}
