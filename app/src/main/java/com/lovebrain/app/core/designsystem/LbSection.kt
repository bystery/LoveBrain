package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight

/** 分区标题 */
@Composable
fun LbSection(title: String) {
    Text(
        title,
        style = AppTypography.titleMedium,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(start = Spacing.sm, bottom = Spacing.sm)
            .testTag(LbTags.SECTION)
    )
}
