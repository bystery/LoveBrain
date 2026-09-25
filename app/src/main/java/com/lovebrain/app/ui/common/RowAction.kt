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
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextSecondary
import androidx.compose.ui.graphics.graphicsLayer

/** 行内次级操作按钮规格 */
private object RowActionDimens {
    /**
     * 可点击盒子最小边长。
     *
     * 旧值 32dp 且 clickable 挂在 vertical padding 之后，实际热区更小；
     * 这是自定义 Box.clickable，Material 不会自动补触摸区。
     * 视觉胶囊仍按内边距画小，触摸盒补足到下限。
     * 数取自 [AppDimens.TOUCH_TARGET_MIN_DP]——这颗数在本仓库只许写一次。
     */
    const val MIN_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP
    /** 视觉胶囊的垂直内缩 */
    const val VISUAL_VERTICAL_INSET_DP = 10
}

/**
 * 行内次级操作小按钮：浅灰胶囊底 + 13sp 小字，供应商行与知识库卡片共用。
 * 内边距加厚、最小高度 32dp。
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
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = RowActionDimens.VISUAL_VERTICAL_INSET_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.full)
            .background(TextSecondary.copy(alpha = 0.08f))
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
