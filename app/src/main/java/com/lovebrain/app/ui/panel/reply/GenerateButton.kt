package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/** 生成按钮内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object GenerateButtonDimens {
    const val BAR_HEIGHT_DP = 40    // 加载条/主按钮高度（2 文件各自私有：<3 复用方不上提）
}

@Composable
fun GenerateButton(
    modifier: Modifier = Modifier,
    // ：主动发态三参（默认值保回复态行为逐字不变）； enabled 恒真 /  不渲染 hasResult 分支
    proactiveMode: Boolean = false,
    isProactive: Boolean = false,
    count: Int,
    isGenerating: Boolean,
    hasResult: Boolean,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onNextRound: () -> Unit,
    onStop: () -> Unit = {}
) {
    if (proactiveMode && isProactive) {
        // 主动发生成中 → 停止条（点击即停，无 count 门控）
        val (stopInteraction, stopScale) = rememberPressScale(0.96f, "proactiveStopScale")
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(GenerateButtonDimens.BAR_HEIGHT_DP.dp)
                .graphicsLayer { scaleX = stopScale; scaleY = stopScale }
                .clip(LoveBrainShape.md)
                .background(Neutral200, LoveBrainShape.md)  // 停止态底色加深一档（R8- 令牌先例）
                .clickable(interactionSource = stopInteraction, indication = null, onClick = onStop),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "停止",
                color = Color.White,
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    } else if (proactiveMode) {
        // 主动发空闲 → 生成开场（enabled 恒真：零消息+留空让军师找话题是主用例）
        val haptics = LocalHapticFeedback.current
        val (genInteraction, genScale) = rememberPressScale(0.96f, "proactiveGenScale")
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(GenerateButtonDimens.BAR_HEIGHT_DP.dp)
                .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md)
                .clip(LoveBrainShape.md)
                .background(Primary, LoveBrainShape.md)
                .graphicsLayer { scaleX = genScale; scaleY = genScale }
                .clickable(interactionSource = genInteraction, indication = null, onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onGenerate()
                }),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "生成开场",
                color = Color.White,
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    } else if (isGenerating) {
        // ：实底 Primary + PrimaryDark 叠层呼吸（对比度优于整条 alpha 脉冲）
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
        // 已等待时间计时器
        var elapsedSec by remember { mutableStateOf(0) }
        LaunchedEffect(isGenerating) {
            elapsedSec = 0
            while (true) {
                kotlinx.coroutines.delay(1000)
                elapsedSec++
            }
        }
        // 精简：从 48dp 降到 40dp，保持 Fitts's Law 触控目标可接受范围
        // 整个加载条可点击 = 强行停止生成
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(GenerateButtonDimens.BAR_HEIGHT_DP.dp)
                .clip(LoveBrainShape.md)
                .background(Primary, LoveBrainShape.md)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center
        ) {
            // ：PrimaryDark 叠层呼吸（不透明度 0~0.22 循环），实底之上做明暗脉动
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
                // 阶段进度指示：根据已等待时间推断当前生成阶段（需求#22：去掉①②③表情符号）
                val phase = when {
                    elapsedSec < 5 -> "分析对话"
                    elapsedSec < 15 -> "生成方案"
                    else -> "深度分析"
                }
                Text(
                    text = "$phase · ${elapsedSec}s",
                    color = Color.White,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(Spacing.md))
                // 停止提示（克制风格，单行小字）
                Text(
                    text = if (elapsedSec >= 15) "(较慢·点击停止)" else "点击停止",
                    color = Color.White,
                    style = AppTypography.labelSmall,
                    maxLines = 1
                )
            }
        }
    } else if (hasResult) {
        // 点击《记入知识库》直接把本轮对话+采纳方案写入知识库（无二次确认；recordingRound 防连点）
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 重试（outlined）：补按压反馈（0.96 scale + 120ms）
            val (retryInteraction, retryScale) = rememberPressScale(0.96f, "retryScale")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                    .graphicsLayer { scaleX = retryScale; scaleY = retryScale }
                    .clip(LoveBrainShape.md)
                    .border(AppDimens.BORDER_WIDTH_DP.dp, Neutral300, LoveBrainShape.md)
                    .clickable(interactionSource = retryInteraction, indication = null, onClick = onRetry),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "重试",
                    color = TextSecondary,
                    style = AppTypography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            // ：记入知识库补按压反馈（复用标准件 0.96 scale + 120ms）
            val (nextInteraction, nextScale) = rememberPressScale(0.96f, "nextScale")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                    .graphicsLayer { scaleX = nextScale; scaleY = nextScale }
                    // 需求#43：shadow 在 clip 之前，圆角阴影贴合圆角
                    .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md)
                    .clip(LoveBrainShape.md)
                    .background(Primary, LoveBrainShape.md)
                    .clickable(interactionSource = nextInteraction, indication = null, onClick = { onNextRound() }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "记入知识库",
                    color = Color.White,
                    style = AppTypography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        val enabled = count > 0
        val haptics = LocalHapticFeedback.current
        // #2：生成按钮补按压反馈（标准件 0.96 scale + 120ms）
        val (genInteraction, genScale) = rememberPressScale(0.96f, "genBarScale")
        // 精简：从 48dp 降到 40dp + 减少垂直 padding
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs)
                .height(GenerateButtonDimens.BAR_HEIGHT_DP.dp)
                // 需求#43：shadow 在 clip 之前，圆角阴影贴合圆角
                .then(if (enabled) Modifier.shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md) else Modifier)
                .clip(LoveBrainShape.md)
                .background(
                    // disabled 态改 SurfaceInset 底 + TextSecondary 文字（WCAG 对比度，去白字低对比）
                    if (enabled) Primary else SurfaceInset,
                    LoveBrainShape.md
                )
                .graphicsLayer { scaleX = genScale; scaleY = genScale }
                .then(if (enabled) Modifier.clickable(
                    interactionSource = genInteraction,
                    indication = null,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onGenerate()
                    }
                ) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 0) "生成回复 · $count 条消息" else "生成回复",
                color = if (enabled) Color.White else TextSecondary,
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
