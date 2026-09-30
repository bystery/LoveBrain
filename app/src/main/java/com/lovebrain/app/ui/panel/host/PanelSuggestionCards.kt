package com.lovebrain.app.ui.panel.host

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.model.StageSuggestion
import com.lovebrain.app.ui.theme.*

/** 画像建议卡与阶段建议卡共用的内部尺寸常量 */
private object SuggestionCardDimens {
    const val PROFILE_CARD_MAX_HEIGHT_DP = 180
}

private object SuggestionCardStrings {
    const val STAGE_SUGGESTION_TITLE = "阶段调整建议"
}

/**
 * 画像建议卡——原地重新生成，卡片位置不变。
 *
 * 状态：
 * - Ready: 正常展示建议，可确认/忽略/重新生成
 * - Regenerating: 原地显示"正在重新生成…"，禁用确认
 * - Confirming: 写入中，禁用所有操作
 * - Error: 建议格式无效，显示重新生成
 *
 * 禁止整张卡消失再重新出现导致页面跳动。
 *
 * 从 `LoveBrainPanelScreen.kt` 搬出——它是一个完整的行为块（画像建议的完整生命周期：
 * 正常 / 重新生成中 / 写入中 / 无效），只依赖传入的参数与回调，不共享宿主状态。
 */
@Composable
fun ProfileSuggestionCard(
    suggestion: String,
    canConfirm: Boolean = true,
    isConfirming: Boolean = false,
    isRegenerating: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrimaryLight, LoveBrainShape.lg)
            .padding(Spacing.lg)
    ) {
        Text("AI 画像更新建议", style = AppTypography.labelLarge, color = PrimaryDark)
        Spacer(Modifier.height(Spacing.md))
        // 真正的 overlay——正文始终留在 layout 中撑高度，loading 覆盖在上层
        // 不用 if/else 替换正文，避免高度跳变
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SuggestionCardDimens.PROFILE_CARD_MAX_HEIGHT_DP.dp)
                .background(SurfaceCard, LoveBrainShape.md)
        ) {
            // 正文始终存在于 layout 中——负责撑高
            Text(
                text = suggestion,
                style = AppTypography.labelMedium,
                color = if (isRegenerating) TextHint else TextPrimary,
                modifier = Modifier
                    .padding(Spacing.md)
                    .verticalScroll(rememberScrollState())
            )
            // loading 覆盖层——不替换正文，覆盖在上方
            if (isRegenerating) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(SurfaceCard.copy(alpha = 0.85f))
                        .padding(Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "正在重新生成…",
                            style = AppTypography.labelMedium,
                            color = PrimaryDark
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            // regenerating 时禁用忽略和确认
            val actionsEnabled = !isConfirming && !isRegenerating
            // press scale feedback
            val (dismissInteraction, dismissScale) = rememberPressScale(0.96f, "profileDismissScale")
            Text(
                "忽略",
                style = AppTypography.labelLarge,
                color = if (actionsEnabled) TextSecondary else TextHint,
                modifier = Modifier
                    .graphicsLayer { scaleX = dismissScale; scaleY = dismissScale }
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        enabled = actionsEnabled,
                        onClick = { onDismiss() }
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            )
            when {
                isRegenerating -> {
                    // 重新生成中原地显示 loading，不额外显示按钮
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "重新生成中…",
                            style = AppTypography.labelLarge,
                            color = TextHint,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
                isConfirming -> {
                    // 提交中：禁用并显示进度
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "写入中…",
                            style = AppTypography.labelLarge,
                            color = PrimaryDark,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
                canConfirm -> {
                    val (confirmInteraction, confirmScale) = rememberPressScale(0.96f, "profileConfirmScale")
                    Text(
                        "确认更新",
                        style = AppTypography.labelLarge,
                        color = PrimaryDark,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .graphicsLayer { scaleX = confirmScale; scaleY = confirmScale }
                            .clickable(interactionSource = confirmInteraction, indication = null, onClick = {
                                onConfirm()
                            })
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    )
                }
                else -> {
                    // 无效建议——显示重新生成，点击真正发起新的画像生成请求
                    val (regenInteraction, regenScale) = rememberPressScale(0.96f, "profileRegenScale")
                    Text(
                        "建议格式无效，重新生成",
                        style = AppTypography.labelLarge,
                        color = Error,
                        modifier = Modifier
                            .graphicsLayer { scaleX = regenScale; scaleY = regenScale }
                            .clickable(interactionSource = regenInteraction, indication = null, onClick = {
                                onRegenerate()
                            })
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    )
                }
            }
        }
    }
}

/**
 * 阶段调整建议卡。
 *
 * 从 `LoveBrainPanelScreen.kt` 搬出——与 [ProfileSuggestionCard] 同属"建议卡"行为块，
 * 只依赖 [StageSuggestion] 数据与确认/忽略回调。
 */
@Composable
fun StageSuggestionCard(
    suggestion: StageSuggestion,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrimaryLight, LoveBrainShape.lg)
            .padding(Spacing.lg)
    ) {
        Text(SuggestionCardStrings.STAGE_SUGGESTION_TITLE, style = AppTypography.labelLarge, color = PrimaryDark)
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "建议调整为「${suggestion.newStage}」\n依据：${suggestion.reason}",
            style = AppTypography.labelMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            // press scale
            val (dismissInteraction, dismissScale) = rememberPressScale(0.96f, "stageDismissScale")
            Text(
                "忽略",
                style = AppTypography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .graphicsLayer { scaleX = dismissScale; scaleY = dismissScale }
                    .clickable(interactionSource = dismissInteraction, indication = null, onClick = { onDismiss() })
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
            val (confirmInteraction, confirmScale) = rememberPressScale(0.96f, "stageConfirmScale")
            Text(
                "确认调整",
                style = AppTypography.labelLarge,
                color = PrimaryDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .graphicsLayer { scaleX = confirmScale; scaleY = confirmScale }
                    .clickable(interactionSource = confirmInteraction, indication = null, onClick = { onConfirm() })
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
        }
    }
}
