package com.lovebrain.app.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lovebrain.app.AppConfig
import com.lovebrain.app.ui.theme.AppDimens
import com.lovebrain.app.ui.theme.Neutral400
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.SurfaceInset

/** 手柄内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object GripDimens {
    const val GRIP_SIZE_DP = 36      // 整体触控目标（语义例外：缩放热区，不与输入行 36dp 混用）
    const val LINE_STROKE_DP = 1.5f  // 斜线/箭头线宽
    const val LINE_INSET_DP = 5      // 斜线距边角内缩
    const val ARROW_PADDING_DP = 1   // 激活箭头内边距
    const val RESIZE_STEP_DP = 20f   // 读屏自定义动作单步调整量
}

/**
 * 右下角拖拽缩放手柄。
 *
 * 视觉提示增强：
 * - 拖拽激活时显示 "⇲ 调整大小" 文字提示
 * - 三条斜线加粗，非激活态也有足够辨识度
 * - 整体区域 36×36dp（增大触控目标）
 */
@Composable
fun ResizeGrip(
    onResize: (Int, Int) -> Unit,
    onResizeEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isActive by remember { mutableStateOf(false) }
    var startW by remember { mutableIntStateOf(0) }
    var startH by remember { mutableIntStateOf(0) }
    var accumX by remember { mutableFloatStateOf(0f) }
    var accumY by remember { mutableFloatStateOf(0f) }

    val gripColor = if (isActive) Primary else Neutral400

    Box(
        modifier = modifier
            .size(GripDimens.GRIP_SIZE_DP.dp)
            // ：读屏可达性——pointerInput 不产生无障碍节点，补语义 + 自定义动作提供等价缩放（拖拽原样保留）
            .semantics {
                contentDescription = "面板大小调整手柄"
                // ui 1.6.8 无 customAction(label) 帮助函数，直接用 customActions 属性等价实现
                customActions = listOf(
                    CustomAccessibilityAction("放大面板") {
                        val density = view.resources.displayMetrics.density
                        onResize(
                            (view.width + GripDimens.RESIZE_STEP_DP * density).toInt()
                                .coerceAtMost((AppConfig.PANEL_MAX_W * density).toInt()),
                            (view.height + GripDimens.RESIZE_STEP_DP * density).toInt()
                                .coerceAtMost((AppConfig.PANEL_MAX_H * density).toInt())
                        )
                        onResizeEnd()
                        true
                    },
                    CustomAccessibilityAction("缩小面板") {
                        val density = view.resources.displayMetrics.density
                        onResize(
                            (view.width - GripDimens.RESIZE_STEP_DP * density).toInt()
                                .coerceAtLeast((AppConfig.PANEL_MIN_W * density).toInt()),
                            (view.height - GripDimens.RESIZE_STEP_DP * density).toInt()
                                .coerceAtLeast((AppConfig.PANEL_MIN_H * density).toInt())
                        )
                        onResizeEnd()
                        true
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isActive = true
                        startW = view.width
                        startH = view.height
                        accumX = 0f
                        accumY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumX += dragAmount.x
                        accumY += dragAmount.y
                        val density = view.resources.displayMetrics.density
                        // ：面板边界硬编码 → AppConfig 常量（与上方无障碍动作同源，值不变）
                        val minW = (AppConfig.PANEL_MIN_W * density).toInt()
                        val maxW = (AppConfig.PANEL_MAX_W * density).toInt()
                        val minH = (AppConfig.PANEL_MIN_H * density).toInt()
                        val maxH = (AppConfig.PANEL_MAX_H * density).toInt()
                        val newW = (startW + accumX).toInt().coerceIn(minW, maxW)
                        val newH = (startH + accumY).toInt().coerceIn(minH, maxH)
                        onResize(newW, newH)
                    },
                    // : 拖拽结束才触发持久化回调，避免 onDrag 每帧写 SecurePrefs
                    onDragEnd = {
                        isActive = false
                        onResizeEnd()
                    },
                    onDragCancel = { isActive = false }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = GripDimens.LINE_STROKE_DP.dp.toPx()
            val inset = GripDimens.LINE_INSET_DP.dp.toPx()
            for (i in 1..3) {
                val offset = i * (size.width / 4.5f)
                drawLine(
                    color = gripColor,
                    start = Offset(size.width - offset, size.height - inset),
                    end = Offset(size.width - inset, size.height - offset),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
        // 拖拽激活时显示 Canvas 绘制的对角箭头（替代 Unicode "⇲" 文字符号，
        // 与 DragHandle/DraggableDivider 的 Canvas 箭头方案一致）
        if (isActive) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(AppDimens.ARROW_SIZE_DP.dp)
                    .padding(GripDimens.ARROW_PADDING_DP.dp)
            ) {
                val w = size.width
                val h = size.height
                // 绘制对角箭头（从左上到右下），表示拖拽可调整大小
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, h)
                    // 箭头头部：右下角的两条短线
                    moveTo(w, h)
                    lineTo(w * 0.4f, h)
                    moveTo(w, h)
                    lineTo(w, h * 0.4f)
                }
                drawPath(path, Primary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = GripDimens.LINE_STROKE_DP.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
