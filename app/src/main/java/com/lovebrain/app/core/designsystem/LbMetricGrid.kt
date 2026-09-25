package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * 使用概览——3 个指标横排。
 * 默认只展示：累计生成、累计花费、采用率。
 */
@Composable
fun LbMetricGrid(
    totalGenerate: String,
    totalCost: String,
    adoptRate: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (onClick != null) mod.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ) else mod }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LbMetricCard(label = "累计生成", value = totalGenerate, modifier = Modifier.weight(1f))
            LbMetricCard(label = "累计花费", value = totalCost, modifier = Modifier.weight(1f))
            LbMetricCard(label = "采用率", value = adoptRate, modifier = Modifier.weight(1f), highlight = true)
        }
    }
}

@Composable
private fun LbMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier.testTag(LbTags.METRIC_CELL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = AppTypography.titleLarge,
            color = if (highlight) Primary else TextPrimary,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = AppTypography.labelSmall,
            color = TextHint,
            textAlign = TextAlign.Center
        )
    }
}
