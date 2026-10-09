package com.lovebrain.app.ui.panel

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbTriangleGlyph
import com.lovebrain.app.core.designsystem.LbTriangleGlyphShape
import com.lovebrain.app.core.designsystem.LbTriangleGlyphTone
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint

/**
 * 顶部拖动那一格的**三轴**（§4.3：可见尺寸、父布局占位、真实触控区域，不允许再混为一谈）。
 *
 * 这颗存在的理由：用户说的是"谈心的透明拖动区域太难命中"，而三轴里被写小的只有第三颗——
 * 命中带此前等于可见那一格（[Spacing.sm] = 4dp）。§9 要的是"把周围确实空白的区域纳入连续命中带"，
 * 同时明写"不要求凭空再增加这么高的一条空白"，所以这里**只吃邻居里本来就空的像素**：
 * 可见那条细带与父布局占位一寸都不加大，热区从 4dp 变成 [DragBand.hitHeight]。
 */
internal object DragBand {

    /** 布局占位 = 可见那一格：页面顶部那一条槽位（[Spacing.sm] = 4dp），本轮不动 */
    val slot: Dp = Spacing.sm

    /**
     * 上方那一段真空白 = 面板根 `Column` 的顶部内边距（`vertical = Spacing.lg`）。
     * 那 12dp 里没有任何可点的东西（页面上第一个可交互节点在页头那一行），
     * 而面板根的 `clip` 又把带子挡在窗口圆角之内——不会在窗外凭空拦下一块触摸。
     */
    val overhangAbove: Dp = Spacing.lg

    /**
     * 下方那一段真空白 = 页头之前那颗 `Spacer(Spacing.sm)`。
     * 带子的下沿因此正好停在页头上沿：**不压**模式两段、齿轮、收起，也不压输入区。
     */
    val overhangBelow: Dp = Spacing.sm

    /**
     * 命中带总高 = 三段真实存在的空白相加 = 4 + 12 + 4 = 20dp（今天那一格的五倍）。
     *
     * ⚠ 为什么不是 §9 里"可先以 24–32dp 高的空白带调试"的那一档：再往上只有两条路，
     * 一条是把可见槽位/父布局撑高（凭空加空白，书里明写不要求），
     * 一条是让透明带盖住页头那两段的 clickable（书里明写"透明热区不能覆盖它们"）。
     * 两条都越线，不是这一席能顺手替用户做的选择。真机量到 20dp 仍然难命中的话，
     * 缺口在页面顶部那一条槽位的布局（宿主那一格），不在这里。
     */
    val hitHeight: Dp = slot + overhangAbove + overhangBelow
}

/**
 * 命中带的测试锚点。
 *
 * 挂在**里面那一颗粒**自己身上（= 带子自己的边界），不挂在外面那只占 4dp 的槽位上——
 * 仪器量到的必须就是手指能打到的那一格，否则"热区 20dp"只是注释里的一句话。
 */
internal const val DRAG_HANDLE_HIT_TAG = "panel_top_drag_hit_band"

/**
 * 顶部拖拽条：按住即可移动悬浮窗（§8.4 表末行"顶部明确空白拖动区域 = 移动悬浮窗"）。
 *
 * 三轴各归各的（数值都在 [DragBand]）：
 * - 可见：一条细而轻的带子，不画箭头/横线装饰——顶部高度 22dp → 14dp → 8dp → 4dp 是用户一路
 *   明确收下来的，这一轮同样**不新画**标记：同一个 4dp 槽位里住着顶部使用统计那一行
 *   （`stats/UsageStatBar`，按 unbounded 溢出居中画在这一格上），再压一条横线就是压在正文上。
 *   可见标记要出来，得先给统计那一行让开一格——那是页面顶槽位那一格的账，不在这颗文件里。
 * - 命中：[DragBand.hitHeight]，向上吃到面板顶部内边距、向下吃到页头之前那颗 Spacer，
 *   连成一条，**不压**模式两段、齿轮、收起，也不压输入区。
 * - 布局占位：仍等于父给的槽位（[DragBand.slot]）——面板一寸都不被顶下去，
 *   页头、滑页、输入区的位置与今天逐字相同。
 *
 * 时序（§9"按下之后立即跟手，短点击不导致窗口突然跳走"）：
 * 用 [detectDragGestures]，起手有系统 touch slop ⇒ 短点击不产生位移，窗口不会跳；
 * 过线之后每一帧的 `dragAmount` 直接交回 [onMove]，中间没有动画也没有节流（宿主的
 * `OverlayPanelWindow.move` 逐次 updateLayout），所以是跟手而不是追赶。
 * 这里**没有**给整扇窗加父手势（§8.4 正文）：横滑切页、长按重排、输入焦点仍归各自那一层。
 *
 * 页头那一行自己也是拖动宿主（`PanelHeader.onHeaderDrag`），管的是页头**内部**那些没被
 * clickable 占走的空白（齿轮/收起外包盒上下那几 dp、两段左右那 8dp）；与这条带子上下相接、不重叠。
 */
@Composable
fun DragHandle(
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .dragHitBand(band = DragBand.hitHeight, overhangBelow = DragBand.overhangBelow)
    ) {
        // 里面这一颗粒才是热区：它自己的 bounds = 整条带，手势挂在它自己身上
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(DRAG_HANDLE_HIT_TAG)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount.x, dragAmount.y)
                        }
                    )
                }
        )
    }
}

/**
 * 把命中带做成 [band] 高，同时把父布局占位那一格留回原样（= 溢出，不是加高）。
 *
 * 溢出怎么分：下侧先按 [overhangBelow] 给（那一段是页头之前那颗 Spacer，带子下沿正好停在页头上沿），
 * 剩下的都归上侧（面板根 Column 的顶部内边距）。带子比槽位还矮、或父没给限量时都不裁带子。
 *
 * 为什么不用现成的 `wrapContentSize(unbounded = true)`：那一颗把内容**居中**溢出，上下各让一半——
 * 上侧本来空 12dp，居中就白白把 6dp 甩到下侧（那里挨着页头，一寸都不能压）。
 */
private fun Modifier.dragHitBand(band: Dp, overhangBelow: Dp): Modifier =
    this.layout { measurable, constraints ->
        val bandPx = band.roundToPx()
        val slotPx = if (constraints.hasBoundedHeight) constraints.maxHeight else bandPx
        val overflow = (bandPx - slotPx).coerceAtLeast(0)
        val belowPx = overhangBelow.roundToPx().coerceIn(0, overflow)
        val abovePx = overflow - belowPx
        val placeable = measurable.measure(constraints.copy(minHeight = bandPx, maxHeight = bandPx))
        layout(placeable.width, slotPx.coerceAtMost(placeable.height)) {
            placeable.placeRelative(0, -abovePx)
        }
    }

/**
 * 展开/收起那一族用的**实心三角**：形状交回设计系统那颗 [LbTriangleGlyph]（基线 v1 §3.10）。
 *
 * 旧体是这一颗文件自己拼的尖 Path（`moveTo(w/2,h) → lineTo(w,0) → lineTo(0,0) → close()`），
 * 三个顶点全是尖角。用户第 6 条（"所有朝右三角形的三个角都要真圆角，不许是尖角"）点名的
 * 是**实心字形**这一族，而全站页面里只剩这一颗还在自己画尖角——所以它换。
 * 公共件的顶点表正是从这段旧 Path 逐字抄过去的（`LbTriangleGlyphGeometryTest` 第 2 格钉着），
 * 于是外形与重心一寸没挪，只是三个角各往里退掉一段切角长（10dp 那一档：半径 1.8dp，不被夹取）。
 *
 * 三轴分离（§3.1）：这里只有**可见尺寸**那一轴（`AppDimens.ARROW_SIZE_DP` = 10dp），
 * 字形自己不垫热区、体里没有 clickable——热区归调用方那一排（谈心页那颗 `LbChip` 动作）。
 * 方向语义一个字不改：旋转角仍由调用方拿着，挂在字形自己的 `graphicsLayer` 上；
 * 用的是 `TriangleDown` 那一档，**不许**顺手换成播放键 `TriangleRight`（§3.10 明写：
 * 展开/折叠指示器不是播放形状）。
 *
 * ⚠ 旧的 `color: Color` 参数**本轮保留**：唯一调用方 `counseling/CounselingPanel.kt:303`
 * 交进来的是 `Primary`，而那颗文件的写入权在 S1b 席手上，本席不跨区动它。
 * 这里只做一次"收敛"——把页面自由挑的墨色折回公共件词表的两档之一，
 * **不是**把 `color` 透给字形（公共件体里没有 `color:` 旋钮，也不该有）。
 * 把这颗参数整族收掉是下一拍的账（已写进 impl-H1c 台账）。
 */
@Composable
fun TriangleArrow(
    color: Color,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    LbTriangleGlyph(
        sizeDp = AppDimens.ARROW_SIZE_DP.dp,
        tone = lbTriangleArrowTone(color),
        shape = LbTriangleGlyphShape.TriangleDown,
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    )
}

/** 旧的颜色口子折回墨色词表：认得出 `TextHint` 走 Muted，其余按今天那一档 Accent（= `Primary`） */
private fun lbTriangleArrowTone(color: Color): LbTriangleGlyphTone =
    if (color == TextHint) LbTriangleGlyphTone.Muted else LbTriangleGlyphTone.Accent
