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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.lovebrain.app.R
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*

/** 无障碍触摸区下限（dp）——所有生成按钮的可点击盒子不得低于此值 */
private const val MIN_TOUCH_TARGET_DP = 48

/** 生成中按钮的自动化锚点：点击即停止 */
const val GENERATE_STOP_TEST_TAG = "generation_stop_action"

/**
 * 统一生成操作按钮组件——替代旧 GenerateButton 和 DualGenerateRow 中的重复实现。
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
    heightDp: Int = MIN_TOUCH_TARGET_DP
) {
    // 可点击盒子必须是完整的 heightDp（默认 48dp）。
    // 旧写法是 .padding(vertical = Spacing.xs) 放在 .clickable 之前，
    // 于是真正能点到的只有 heightDp - 2*xs，等于自己把热区削掉一圈。
    // 现在把内边距放到 clickable 之后，交给内容层承担。
    val haptics = LocalHapticFeedback.current
    val (interaction, scale) = rememberPressScale(0.96f, "genActionScale")

    val baseModifier = Modifier
        .then(modifier)
        .height(maxOf(heightDp, MIN_TOUCH_TARGET_DP).dp)

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
                    .clip(LoveBrainShape.md)
                    .background(containerColor, LoveBrainShape.md)
                    .clickable(onClick = onClick)
                    .padding(vertical = Spacing.xs),
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
                    // 文案进资源，并用 testTag 给自动化一个稳定锚点。
                    //缺陷正是"测试找精确文字『停止』，而生产实际显示
                    // 『分析对话 · Ns 点击停止』"——文字会变，tag 不会。
                    val phase = when {
                        elapsedSec < 5 -> stringResource(R.string.panel_phase_analysing)
                        elapsedSec < 15 -> stringResource(R.string.panel_phase_drafting)
                        else -> stringResource(R.string.panel_phase_deep_analysing)
                    }
                    Text(
                        text = stringResource(R.string.panel_analysing_with_seconds, phase, elapsedSec),
                        modifier = Modifier.testTag(GENERATE_STOP_TEST_TAG),
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
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(LoveBrainShape.md)
                    .background(Neutral200, LoveBrainShape.md)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .padding(vertical = Spacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Text(text, color = Color.White, style = AppTypography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        else -> {
            // NORMAL / DISABLED
            //
            // modifier 只有一条顺序清晰的链：size → graphics → shadow → clip → background
            // → clickable → 内容内边距。
            //
            // 这里原先把 shadow/clip/background/graphicsLayer 在 clickable 前后**各写了一遍**
            // （2026-09-24 独立复核点名的重复 modifier 链）：同一个盒子被裁两次、着色两次、缩放两次，
            // 既多画一层，也让"这个按钮到底被什么裁掉了"没人能从代码上回答。
            // padding 放在 clickable 之后，热区才是完整的 heightDp。
            Box(
                modifier = baseModifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(
                        if (enabled) Modifier.shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md)
                        else Modifier
                    )
                    .clip(LoveBrainShape.md)
                    .background(
                        if (enabled) containerColor else SurfaceInset,
                        LoveBrainShape.md
                    )
                    .clickable(
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                    .padding(vertical = Spacing.xs),
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
