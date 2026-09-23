package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*

/**
 * 记忆纠正中心——独立列出已停用／静音／隔离项，支持撤销。
 *
 * 因为被过滤的引用会消失，不能只把撤销放在已消失的引用菜单。
 * 纠正中心提供持久入口，用户随时可查看和撤销所有纠正记录。
 */
@Composable
fun CorrectionCenter(
    corrections: Map<String, MemoryCorrection>,
    onUndoCorrection: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(SurfaceInset, LoveBrainShape.md)
            .border(1.dp, Border, LoveBrainShape.md)
            .padding(Spacing.md)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "记忆纠正中心",
                style = AppTypography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "关闭",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ).padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            )
        }
        Spacer(Modifier.height(Spacing.sm))

        if (corrections.isEmpty()) {
            Text(
                text = "暂无纠正记录。在「本轮参考」中可对记忆发起纠正。",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier.padding(vertical = Spacing.md)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                corrections.forEach { (memoryId, correction) ->
                    CorrectionRecordCard(
                        memoryId = memoryId,
                        correction = correction,
                        onUndo = { onUndoCorrection(memoryId) }
                    )
                }
            }
        }
    }
}

/**
 * 单条纠正记录卡片。
 */
@Composable
private fun CorrectionRecordCard(
    memoryId: String,
    correction: MemoryCorrection,
    onUndo: () -> Unit
) {
    val actionLabel = when (correction.action) {
        CorrectionAction.WRONG -> "不对"
        CorrectionAction.FINISHED -> "已结束"
        CorrectionAction.MUTED -> "暂时别提"
        CorrectionAction.WRONG_PERSON -> "不是她"
    }
    val actionColor = when (correction.action) {
        CorrectionAction.WRONG -> Error
        CorrectionAction.FINISHED -> TextHint
        CorrectionAction.MUTED -> Warning
        CorrectionAction.WRONG_PERSON -> Error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.sm)
            .background(SurfaceCard, LoveBrainShape.sm)
            .border(1.dp, Border, LoveBrainShape.sm)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 操作类型标签
        Box(
            modifier = Modifier
                .clip(LoveBrainShape.sm)
                .background(actionColor.copy(alpha = 0.15f), LoveBrainShape.sm)
                .padding(horizontal = Spacing.xs, vertical = 2.dp)
        ) {
            Text(
                text = actionLabel,
                style = AppTypography.labelSmall,
                color = actionColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        // 记忆 ID（截断显示）
        Text(
            text = memoryId.takeLast(30),
            style = AppTypography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        // 补正内容（如果有）
        if (correction.replacementText.isNotBlank()) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "→ ${correction.replacementText.take(20)}",
                style = AppTypography.labelSmall,
                color = TextHint,
                maxLines = 1
            )
        }
        // 静音时长（如果是 MUTED）
        if (correction.action == CorrectionAction.MUTED) {
            val durationLabel = when (correction.muteDuration) {
                MuteDuration.THIS_ROUND -> "本轮"
                MuteDuration.TODAY -> "今天"
                MuteDuration.UNTIL_RESTORE -> "恢复"
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "[$durationLabel]",
                style = AppTypography.labelSmall,
                color = TextHint
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        // 撤销按钮
        val (undoInteraction, undoScale) = rememberPressScale(0.92f, "undoCorrection_$memoryId")
        Text(
            text = "撤销",
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .graphicsLayer { scaleX = undoScale; scaleY = undoScale }
                .clip(LoveBrainShape.sm)
                .background(PrimaryLight, LoveBrainShape.sm)
                .clickable(
                    interactionSource = undoInteraction,
                    indication = null,
                    onClick = onUndo
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}
