package com.lovebrain.app.ui.panel.counseling

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.ui.panel.AiLoadingRow

/** 谈心 CTA（脉冲条 / 开始按钮）高度常量（仅本文件使用）。 */
private object CounselingCtaDimens {
    const val CTA_HEIGHT_DP = 40
}

/**
 * 谈心 CTA：谈心中显示脉冲动画条（点击停止），非谈心状态显示"开始谈心"按钮。
 *
 * 谈心中脉冲动画（与 GenerateButton 一致的视觉反馈）——
 * 仅在 isCounseling 时创建 rememberInfiniteTransition，非谈心状态不运行动画（避免无谓重组开销）。
 */
@Composable
internal fun CounselingPulseCta(
    isCounseling: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isCounseling) {
        // 实底 Primary + PrimaryDark 叠层呼吸（对比度优于整条 alpha 脉冲）
        val pulseTransition = rememberInfiniteTransition(label = "counselingPulse")
        val overlayAlpha by pulseTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "counselingPulseOverlay"
        )
        // 整个加载条可点击 = 强行停止谈心
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(CounselingCtaDimens.CTA_HEIGHT_DP.dp)
                .clip(LoveBrainShape.md)
                .background(Primary, LoveBrainShape.md)
                .clickable { onStop() },
            contentAlignment = Alignment.Center
        ) {
            // PrimaryDark 叠层呼吸（不透明度 0~0.22 循环），实底之上做明暗脉动
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
                    text = "军师聆听中…",
                    color = Color.White,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = "点击停止",
                    color = Color.White,
                    style = AppTypography.labelSmall,
                    maxLines = 1
                )
            }
        }
    } else {
        // 开始谈心 CTA 补按压反馈（复用标准件 0.96 scale + 120ms；条件 clickable 结构保留）
        val (ctaInteraction, ctaScale) = rememberPressScale(0.96f, "ctaScale")
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(CounselingCtaDimens.CTA_HEIGHT_DP.dp)
                .graphicsLayer { scaleX = ctaScale; scaleY = ctaScale }
                .then(if (canStart) Modifier.shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md) else Modifier)
                // 禁用态对齐 GenerateButton 先例（SurfaceInset 底 + TextSecondary 文字，WCAG 对比度）
                .background(if (canStart) Primary else SurfaceInset, LoveBrainShape.md)
                .then(if (canStart) Modifier.clickable(interactionSource = ctaInteraction, indication = null, onClick = onStart) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "开始谈心",
                color = if (canStart) Color.White else TextSecondary,
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** 谈心加载动画（需求19）：统一 AiLoadingRow——三点跳动 + 轮换文案（首 token 前展示） */
@Composable
internal fun CounselingLoading(modifier: Modifier = Modifier) {
    val phrases = remember {
        listOf(
            "军师正在倾听…",
            "军师正在梳理你的情绪…",
            "军师正在还原事情的全貌…",
            "军师正在权衡公正的裁决…",
            "军师正在为你斟酌词句…"
        )
    }
    AiLoadingRow(
        phrases = phrases,
        modifier = modifier,
        background = SurfaceInset
    )
}
