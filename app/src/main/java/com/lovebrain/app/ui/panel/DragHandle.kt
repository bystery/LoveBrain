package com.lovebrain.app.ui.panel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.theme.AppDimens
import com.lovebrain.app.ui.theme.Spacing

/**
 * 顶部拖拽条（第4轮迭代 + 4dp 极简）。
 *
 * 需求#6：去掉 <-> 图标，按住悬浮窗顶部即可拖拽移动；顶部高度 22dp → 14dp → 8dp → 4dp（用户明确要求 8→4）。
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

/** A2-2：三角折叠箭头（默认朝下）——颜色/旋转角参数化；谈心/锦囊两处 Path 原逐字相同 */
@Composable
fun TriangleArrow(
    color: Color,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(AppDimens.ARROW_SIZE_DP.dp)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, h)
            lineTo(w, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * A2-1：按压缩放标准件（27 处同构收编）——按下缩至 targetScale、松开回弹 1f；
 * tween(120, FastOutSlowInEasing) 全仓统一。返回 (interaction, scale)，
 * interaction 供 clickable(interactionSource=…) 复用，行为逐位等于原体。
 */
@Composable
fun rememberPressScale(targetScale: Float, label: String): Pair<MutableInteractionSource, Float> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) targetScale else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = label
    )
    return interaction to scale
}
