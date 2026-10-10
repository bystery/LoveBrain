package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextSecondary

/**
 * 设置页里的**悬浮图标大小**那一格：一行标题 + 三档分段（小 / 标准 / 大）。
 *
 * 指导书 2026-10-10 §1 点名：只做三个档位、不做连续 1dp 调节，「三个档位已覆盖日常需求，
 * 也容易保证布局和手势正确」。所以这一格**没有滑杆**——滑杆那颗归透明度，两件事不共用控件。
 *
 * 三条纪律：
 * 1. **档位真值不在这页抄一份**。可选项一律从 [BubbleSizeTier.ALLOWED_DP] 数出来（48 / 56 / 64），
 *    选中那一颗按 [BubbleSizeTier.snapDp] 后的读数比——刻度尺长在 `AppConfig`，窗口宽高、主球直径、
 *    角标偏移、面板贴边都从那里推（§1 明写不许只对 Compose 用 `scale()` 而留着 56dp 的窗口点击区）。
 * 2. **词表住资源，不内联**：三颗标签取 `settings_bubble_size_small/standard/large`，
 *    写进 `LbChip(...)` 调用里会被 `UiStringLiteralBudgetTest` 的 COMPONENT 那一栏数进去。
 * 3. **容器不新画**：卡壳走那一族唯一的主人 [settingsEntryCard]，分段档走 `LbChipStyles.segmented`
 *    （供应商表单的超时四档同一颗件、同一档），本页不加第四种形状。
 *
 * 词条与刻度按 [zip] 对齐：刻度尺哪天多出一档而词条没补，多出来那一档不会画出一颗错标签的胶囊，
 * 而会由「三档恰好三颗」那条形状闸先红（见 `OverlayBubbleWindowTest` 里的 tier ruler 那一族）。
 */
@Composable
internal fun SettingsBubbleSizeEntry(
    sizeDp: Int,
    onSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tiers = listOf(
        R.string.settings_bubble_size_small,
        R.string.settings_bubble_size_standard,
        R.string.settings_bubble_size_large
    )
    val current = BubbleSizeTier.snapDp(sizeDp)

    Column(modifier = modifier.settingsEntryCard()) {
        Text(
            text = stringResource(R.string.settings_bubble_size_label),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            (BubbleSizeTier.ALLOWED_DP zip tiers).forEach { (dp, labelRes) ->
                LbChip(
                    label = stringResource(labelRes),
                    selected = dp == current,
                    onClick = { onSizeChange(dp) },
                    interaction = LbChipInteraction.Single,
                    style = LbChipStyles.segmented,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
