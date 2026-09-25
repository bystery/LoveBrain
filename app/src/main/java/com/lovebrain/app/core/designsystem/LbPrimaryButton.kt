package com.lovebrain.app.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * §6.1 表里的 `LbPrimaryButton`——页面唯一主动作，**四态一旋钮**。
 *
 * 它替代的是 `ui/panel/reply/GenerationActionButton.kt`。那颗按钮的状态本来由
 * **两个平行旋钮**表达：`mode: ButtonMode {NORMAL, LOADING, STOP}` 与 `enabled: Boolean`。
 * 于是 `mode = STOP, enabled = false` 这种组合在类型上完全合法、在生产里没人约定过含义，
 * 而"禁用"其实是 NORMAL 的一个分支——两张表管一件事，就必有说不清的那一格。
 * 现在收成 `LbButtonState` 一个参数：非法组合不可表达。
 *
 * 还有一件刻意**没**放进来的事：Loading 那串"分析对话 · 7s 点击停止"的**计时与阶段词**。
 * 设计系统不该知道"生成"这件事（上一格 `LbTopBar` 就是把首页文案烤进了 core，已改回）。
 * 所以这里只画四态共有的那层壳——高度下限、形状、着色、按压反馈、脉冲与进度环、
 * 停止锚点——**说什么由调用方决定**（`label` 传进来）。
 *
 * 触摸区：48dp 下限写在组件里，不再有 `heightDp` 参数。
 * 原来调用方传 `heightDp = 48`，而实现里写 `maxOf(heightDp, 48)`——
 * 那个参数只能把按钮改得**更高**，永远不能改矮，等于一个不存在的自由度（§7.1 那类死参数）。
 */

/** §6.5 :531——主动作的可点击盒子下限；比它矮的写法在这一格里不可表达 */
const val LB_PRIMARY_MIN_HEIGHT_DP = 48

/** 四态：一旋钮，替代旧的 `ButtonMode` + `enabled` 两个平行旋钮 */
enum class LbButtonState { Idle, Loading, Disabled, Stop }

/**
 * 着色调性。原来由调用方直接交 `containerColor`，现在收进这张两档小表
 * （§6.1 末句"不许只在一个页面看起来不一样"——颜色要来自词表，不是来自参数）。
 *
 * 底色写在下面那个扩展里而不是枚举构造参数里：构造参数里 `Primary(Primary)`
 * 的第二个 `Primary` 会被解析成**枚举项自己**而不是同名的顶层颜色 val（编译期就报类型不符）。
 */
enum class LbButtonTone { Primary, Deep }

internal val LbButtonTone.container: Color
    get() = when (this) {
        LbButtonTone.Primary -> Primary
        LbButtonTone.Deep -> PrimaryDark
    }

@Composable
fun LbPrimaryButton(
    state: LbButtonState,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LbButtonTone = LbButtonTone.Primary
) {
    // 可点击盒子必须是完整的 48dp：内边距放在 clickable **之后**，
    // 放在之前就等于自己把热区削掉一圈（旧组件修过一次，别再改回去）。
    val haptics = LocalHapticFeedback.current
    val (interaction, scale) = rememberPressScale(0.96f, "lbPrimaryScale")
    val base = modifier.height(LB_PRIMARY_MIN_HEIGHT_DP.dp)
    val container = tone.container

    when (state) {
        LbButtonState.Loading -> {
            val transition = rememberInfiniteTransition(label = "pulse")
            val overlayAlpha by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0.22f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "overlayAlpha"
            )
            Box(
                modifier = base
                    .clip(LoveBrainShape.md)
                    .background(container, LoveBrainShape.md)
                    .clickable(onClick = onClick)
                    .paddingVerticalInside(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(PrimaryDark, LoveBrainShape.md)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(Spacing.xl),
                        strokeWidth = Spacing.xs
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = label,
                        // 文字会变，tag 不会：自动化找的是这颗停止条，不是那句中文
                        modifier = Modifier.testTag(LbTags.PRIMARY_STOP),
                        color = Color.White,
                        style = AppTypography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LbButtonState.Stop -> Box(
            modifier = base
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(LoveBrainShape.md)
                .background(Neutral200, LoveBrainShape.md)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .paddingVerticalInside(),
            contentAlignment = Alignment.Center
        ) {
            LbPrimaryLabel(label, Color.White)
        }

        LbButtonState.Disabled -> Box(
            modifier = base
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(LoveBrainShape.md)
                .background(SurfaceInset, LoveBrainShape.md)
                .clickable(
                    enabled = false,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
                .paddingVerticalInside(),
            contentAlignment = Alignment.Center
        ) {
            LbPrimaryLabel(label, TextSecondary)
        }

        LbButtonState.Idle -> Box(
            modifier = base
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md)
                .clip(LoveBrainShape.md)
                .background(container, LoveBrainShape.md)
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .paddingVerticalInside(),
            contentAlignment = Alignment.Center
        ) {
            LbPrimaryLabel(label, Color.White)
        }
    }
}

/** 四态共用的那颗标签样式：一处改，四态一起改 */
@Composable
private fun LbPrimaryLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = AppTypography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 内容层的竖向内边距——**必须排在 `clickable` 之后**：
 * 排在之前就只有 `48 - 2*xs` 能点到，等于自己把热区削掉一圈（§7.1 点名的写法）。
 * 抽成一个函数是为了让"四态都用同一条顺序"这件事写在名字里，而不是散在四段代码里。
 */
private fun Modifier.paddingVerticalInside(): Modifier = this.padding(vertical = Spacing.xs)
