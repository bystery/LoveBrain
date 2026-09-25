package com.lovebrain.app.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * 按压时的轻微缩放（全站唯一实现）。第三步-1 从 `ui/panel/DragHandle.kt` 搬进设计系统包：
 * 它不是面板专有的东西——20 个文件在用，刚按 §6.1 表搬进本包的统一组件也要用，
 * 留在 ui 下就会让 core 反向依赖 ui（PackageDependencyTest 上一格刚把这条前缀禁掉）。
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
