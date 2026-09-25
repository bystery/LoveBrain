package com.lovebrain.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.BuildConfig
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary

/**
 * 关于页——版本信息、隐私说明与诊断信息。
 */
@Composable
fun AboutScreen(
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
            val (backInteraction, backScale) = rememberPressScale(0.94f, "aboutBack")
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
            Text("关于", style = AppTypography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }

        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("LoveBrain", style = AppTypography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "版本 v${BuildConfig.VERSION_NAME}",
                    style = AppTypography.bodyMedium,
                    color = TextSecondary
                )
                Text("帮你更自然地表达", style = AppTypography.bodySmall, color = TextHint)

                HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))

                Text("隐私说明", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "LoveBrain 在本地运行，聊天内容仅发送给你配置的 AI 模型供应商。\n知识库数据存储在本地设备，不上传到任何第三方服务器。",
                    style = AppTypography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // 诊断信息（可折叠）
        var showDiagnostics by remember { mutableStateOf(false) }
        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDiagnostics = !showDiagnostics }
        ) {
            Column(modifier = Modifier.padding(Spacing.xl)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("诊断信息", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(if (showDiagnostics) "▾" else "▸", color = TextHint)
                }
                if (showDiagnostics) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text("SHA: ${BuildConfig.GIT_SHA}", style = AppTypography.labelSmall, color = TextHint)
                    Text("Build: ${BuildConfig.BUILD_TYPE}", style = AppTypography.labelSmall, color = TextHint)
                }
            }
        }
    }
}
