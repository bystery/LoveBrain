package com.lovebrain.app.ui.panel

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.lovebrain.app.ui.theme.Spacing

/**
 * 可拖拽分隔条（透明化）。
 *
 * 调节5：滑块条全部透明化——去上下箭头与手柄横条，纯手势热区，高度 24→8dp，
 * 靠上下元素的呼吸感间距自然分隔（不画任何装饰）。
 * ：热区再收窄 8→4dp（改用更小一级间距令牌，值见 Theme.kt）。
 * ：视觉高度再调低 4→2dp（Spacing.xs，主人拍板"透明区太高显得空"；手势功能保留）。
 */
@Composable
fun DraggableDivider(
    onDragDelta: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Spacing.xs)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount)
                }
            }
    )
}
