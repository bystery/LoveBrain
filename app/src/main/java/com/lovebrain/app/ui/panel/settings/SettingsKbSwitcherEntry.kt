package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.model.KnowledgeBase

/**
 * 设置页里那一格**知识库切换**：列已有库，点哪条切哪条。
 *
 * 格式复用 [SettingsOpacityEntry] / [SettingsIntentEntry] 那一族：
 * `SurfaceCard` 底 + `Border` 描边 + `Spacing.lg` 内边距。
 * 活动库高亮（`PrimaryLight` 底 + `Primary` 描边），非活动库点一下就切。
 */
@Composable
internal fun SettingsKbSwitcherEntry(
    knowledgeBases: List<KnowledgeBase>,
    activeKbName: String?,
    onSwitchKb: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.lg)
            .background(SurfaceCard, LoveBrainShape.lg)
            .border(AppDimens.BORDER_WIDTH_DP.dp, androidx.compose.ui.graphics.Color(0xFFE0E0E6), LoveBrainShape.lg)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            text = stringResource(R.string.kb_card_in_use),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.sm))
        knowledgeBases.forEach { kb ->
            val isActive = kb.name == activeKbName
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LoveBrainShape.md)
                    .background(
                        if (isActive) PrimaryLight else androidx.compose.ui.graphics.Color.Transparent,
                        LoveBrainShape.md
                    )
                    .border(
                        AppDimens.BORDER_WIDTH_DP.dp,
                        if (isActive) Primary else androidx.compose.ui.graphics.Color.Transparent,
                        LoveBrainShape.md
                    )
                    .clickable(enabled = !isActive) { onSwitchKb(kb.name) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text(
                    text = kb.displayName,
                    style = AppTypography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    Text(
                        text = stringResource(R.string.kb_card_in_use),
                        style = AppTypography.labelSmall,
                        color = Primary
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}
