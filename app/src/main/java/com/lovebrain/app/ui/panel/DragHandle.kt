package com.lovebrain.app.ui.panel

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbTriangleGlyph
import com.lovebrain.app.core.designsystem.LbTriangleGlyphShape
import com.lovebrain.app.core.designsystem.LbTriangleGlyphTone
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint

/**
 * 顶部拖拽条 + 4dp 极简。
 *
 * 去掉 <-> 图标，按住悬浮窗顶部即可拖拽移动；顶部高度 22dp → 14dp → 8dp → 4dp（用户明确要求 8→4）。
 * 视觉：完全透明的细条，不画任何箭头/横线装饰——更克制，把视觉焦点让给内容。
 * 热区：fillMaxWidth 整行；切换器左侧空白区的拖拽由 PanelHeader.onHeaderDrag 补充（修复 2.2 失效）。
 */
@Composable
fun DragHandle(
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Spacing.sm)
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
