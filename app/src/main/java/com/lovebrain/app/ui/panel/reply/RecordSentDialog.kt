package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*

/**
 * F03: 记录实际发送——用户确认已发送的版本。
 *
 * 打开编辑框，可从当前候选预填，也可粘贴、改写或输入完全不同的话。
 * 按钮文案为"确认已发送并记录"，说明是用户自行确认，不代表应用检测到了发送行为。
 * 不强制每一轮完成此操作。
 *
 * 确认后写为"我"的真实消息，保存用户确认来源、关联候选版本（若有）、时间。
 * 若用户只是想收藏，继续使用点赞，不混淆两者。
 */
@Composable
fun RecordSentDialog(
    prefill: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(prefill) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Spacing.xl)
                .clip(LoveBrainShape.lg)
                .background(SurfaceCard)
                .border(1.dp, Border, LoveBrainShape.lg)
                .padding(Spacing.lg)
        ) {
            Text(
                text = "记录实际发送",
                style = AppTypography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "确认你已发送这条消息（用户自行确认，不代表应用检测到发送）",
                style = AppTypography.labelSmall,
                color = TextHint
            )
            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("粘贴或输入你实际发送的话", style = AppTypography.labelSmall, color = TextHint)
                },
                textStyle = AppTypography.bodySmall.copy(color = TextPrimary),
                singleLine = false,
                maxLines = 5,
                shape = LoveBrainShape.sm
            )

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "取消",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ).padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                )
                Spacer(Modifier.width(Spacing.sm))
                val (confirmInteraction, confirmScale) = rememberPressScale(0.96f, "sentConfirmScale")
                Text(
                    text = "确认已发送并记录",
                    style = AppTypography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .graphicsLayer { scaleX = confirmScale; scaleY = confirmScale }
                        .clip(LoveBrainShape.sm)
                        .background(Primary)
                        .clickable(
                            interactionSource = confirmInteraction,
                            indication = null,
                            onClick = {
                                if (text.isNotBlank()) onConfirm(text.trim())
                            }
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
        }
    }
}
