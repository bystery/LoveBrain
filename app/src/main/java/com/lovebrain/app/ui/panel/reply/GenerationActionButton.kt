package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*

/**
 * F13: 统一生成操作按钮组件——替代旧 GenerateButton 和 DualGenerateRow 中的重复实现。
 *
 * 设计要点：
 * - pressed、disabled、loading、stop、retry 的尺寸/圆角/反馈共用
 * - 0.96 scale + 120ms 动画作为按压反馈基线
 * - 点击后即时启动请求，不为展示动画故意拖慢
 * - 动画节点不被状态切换过早拆掉
 *
 * 模式：
 * - NORMAL：空闲态，显示文字 + 按压反馈
 * - LOADING：生成中，显示进度脉冲 + 计时 + 点击停止
 * - STOP：主动发生成中，简洁停止条
 * - DISABLED：不可用，灰色底 + 次级文字色
 */
@Composable
fun GenerationActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Primary,
    textColor: Color = Color.White,
    mode: ButtonMode = ButtonMode.NORMAL,
    heightDp: Int = 40
) {
    val haptics = LocalHapticFeedback.current
    val (interaction, scale) = rememberPressScale(0.96f, "genActionScale")

    val baseModifier = Modifier
        .then(modifier)
        .height(heightDp.dp)

    when (mode) {
        ButtonMode.LOADING -> {
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
            var elapsedSec by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                elapsedSec = 0
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    elapsedSec++
                }
            }
            Box(
                modifier = baseModifier
                    .padding(vertical = Spacing.xs)
                    .clip(LoveBrainShape.md)
                    .background(containerColor, LoveBrainShape.md)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.matchParentSize().graphicsLayer { alpha = overlayAlpha }
                        .background(PrimaryDark, LoveBrainShape.md)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(Spacing.xl),
                        strokeWidth = Spacing.xs
                    )
                    Spacer(Modifier.width(Spacing.md))
                    val phase = when {
                        elapsedSec < 5 -> "分析对话"
                        elapsedSec < 15 -> "生成方案"
                        else -> "深度分析"
                    }
                    Text(
                        text = "$phase · ${elapsedSec}s  点击停止",
                        color = Color.White,
                        style = AppTypography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        ButtonMode.STOP -> {
            Box(
                modifier = baseModifier
                    .padding(vertical = Spacing.xs)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(LoveBrainShape.md)
                    .background(Neutral200, LoveBrainShape.md)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(text, color = Color.White, style = AppTypography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        else -> {
            // NORMAL / DISABLED
            Box(
                modifier = baseModifier
                    .padding(vertical = Spacing.xs)
                    .then(if (enabled) Modifier.shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md) else Modifier)
                    .clip(LoveBrainShape.md)
                    .background(
                        if (enabled) containerColor else SurfaceInset,
                        LoveBrainShape.md
                    )
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(if (enabled) Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        }
                    ) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = if (enabled) textColor else TextSecondary,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

enum class ButtonMode { NORMAL, LOADING, STOP }
