package com.lovebrain.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.AppTypography
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.TextSecondary
import androidx.compose.ui.graphics.graphicsLayer

/** 行内次级操作按钮规格（主人选型问题3：整体放大一号） */
private object RowActionDimens {
    const val MIN_HEIGHT_DP = 32  // 最小高度（原≈24 提到 32）
}

/**
 * 行内次级操作小按钮（/F2 统一样式）：浅灰胶囊底 + 13sp 小字，供应商行与知识库卡片共用。
 * 主人选型问题3：内边距加厚、字号 11sp→13sp、最小高度 32dp（两处调用方自动同步，无需改调用侧）。
 * 按压反馈沿用全局标准件（0.94 scale）。
 */
@Composable
fun RowActionButton(
    text: String,
    tint: Color = TextSecondary,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.94f, "rowActionScale$text")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = RowActionDimens.MIN_HEIGHT_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.full)
            .background(TextSecondary.copy(alpha = 0.08f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Text(
            text = text,
            style = AppTypography.bodyMedium, // 13sp（原 labelMedium 11sp）
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}
