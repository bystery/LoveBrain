package com.lovebrain.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
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
        // §6.1 :479：与"关于"页同一族手拼页头，那颗返回钮的 contentDescription 实测是空串。
        LbTopBar(
            title = "使用概览",
            level = LbTopBarLevel.Page,
            onBack = onBack
        )

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
