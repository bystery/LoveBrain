package com.lovebrain.app.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * 全站那条按压曲线（120ms + FastOutSlowIn）——**这个数只许写在这里一次**。
 *
 * 为什么要把它写成一颗具名的值而不是每个调用方各抄一遍：`rememberPressScale` 的默认档
 * 与 `LbChipPressFeedback.Standard` 指的是**同一条曲线**，两处必须永远相等；
 * 抄第二遍的话，改一处就会让另一处悄悄跟着别（"按压反馈"从此有两个口径）。
 */
internal val lbPressCurve: FiniteAnimationSpec<Float> = tween(120, easing = FastOutSlowInEasing)

/**
 * 按压时的轻微缩放（全站唯一实现）。第三步-1 从 `ui/panel/DragHandle.kt` 搬进设计系统包：
 * 它不是面板专有的东西——20 个文件在用，刚按 §6.1 表搬进本包的统一组件也要用，
 * 留在 ui 下就会让 core 反向依赖 ui（PackageDependencyTest 上一格刚把这条前缀禁掉）。
 *
 * 曲线由 [lbPressCurve] 定死，调用方**拿不到**换曲线的旋钮：要换档得走设计系统里
 * 有名字的档位（见 `LbChipPressFeedback`），不许在这里开一个自由的
 * `FiniteAnimationSpec` 参数——那等于把"每页自己选一条曲线"合法化。
 */
@Composable
fun rememberPressScale(targetScale: Float, label: String): Pair<MutableInteractionSource, Float> =
    rememberPressScale(targetScale, label, lbPressCurve)

/**
 * 带曲线的那一颗：**只给设计系统内部的档位用**（所以是 internal）。
 *
 * 与 [rememberPressScale] 的两参版一字不差，唯一区别是曲线由调用方（组件自己）交进来；
 * 页面拿不到这个重载，因此它不是一个自由旋钮。
 */
@Composable
internal fun rememberPressScale(
    targetScale: Float,
    label: String,
    curve: FiniteAnimationSpec<Float>
): Pair<MutableInteractionSource, Float> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) targetScale else 1f,
        animationSpec = curve,
        label = label
    )
    return interaction to scale
}
