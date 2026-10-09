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
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.model.KnowledgeBase

/**
 * 设置页里那一格**当前知识库**：列当前选择与其他有效库，点哪条切哪条。
 *
 * 容器走那一族唯一的主人 [settingsEntryCard]（与透明度、意图两格同一条链）。
 * 库里那一行保留自己的活动高亮（`PrimaryLight` 底 + `Primary` 描边）——那是这一格自己的
 * 语义（"这一块正在用"），不是容器。
 *
 * §11.1 那两条判据在这一格的落点：
 * · **选中态只在切成功之后才动**：高亮读的是宿主交下来的 [activeKbName]（VM 那份真状态），
 *   这一格不预涂、不乐观更新。点下去之后盘上没换成那块库，屏幕上就不会换成那块库；
 * · **看得见的只有有效库**：列表来自仓库那一侧的目录扫描，元数据读不出／坏 JSON／目录名与
 *   声明不符的那几本在数据层就被丢掉了（`data/KnowledgeCatalogStore.kt` 的 `readOne`），
 *   这一页不再自己筛第二遍，也不把"切库失败"涂成成功。
 */
@Composable
internal fun SettingsKbSwitcherEntry(
    knowledgeBases: List<KnowledgeBase>,
    activeKbName: String?,
    onSwitchKb: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.settingsEntryCard()) {
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
