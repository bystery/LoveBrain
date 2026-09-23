package com.lovebrain.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.AppTypography
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.SurfaceCard
import com.lovebrain.app.ui.theme.TextPrimary
import com.lovebrain.app.ui.theme.TextSecondary
import com.lovebrain.app.viewmodel.SetupViewModel

/**
 * 使用概览详情页——累计统计指标。
 */
@Composable
fun UsageDetailScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (backInteraction, backScale) = rememberPressScale(0.94f, "usageBack")
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = backScale; scaleY = backScale }
                    .clip(LoveBrainShape.md)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "←",
                    style = AppTypography.titleMedium,
                    color = Primary
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text("使用概览", style = AppTypography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }

        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("累计统计", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("生成次数：${viewModel.totalGenerateCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("复制次数：${viewModel.totalCopyCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("采用次数：${viewModel.totalAdoptCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("改写次数：${viewModel.totalRewriteCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                val costStr = if (viewModel.totalCostYuan < 0.01) "￥0" else "￥${String.format("%.2f", viewModel.totalCostYuan)}"
                Text("累计花费：$costStr", style = AppTypography.bodyMedium, color = TextSecondary)
                val rateStr = if (viewModel.totalGenerateCount > 0) "${(viewModel.adoptRate * 100).toInt()}%" else "—"
                Text("采用率：$rateStr", style = AppTypography.bodyMedium, color = TextSecondary)
            }
        }
    }
}
