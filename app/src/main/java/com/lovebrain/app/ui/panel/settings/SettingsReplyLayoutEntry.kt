package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbSettingRow
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.ui.home.MiniSwitch

/**
 * 设置页里的**回复显示**那一格：一颗「回复卡片纵向排列」开关。
 *
 * 指导书 2026-10-10 §3 的原话口径：「用户只看到一个简洁的开关」——方向的真值（`VERTICAL` /
 * `HORIZONTAL`）住在 `AppConfig.kt` 的 `ReplyCardLayout` 与盘上那一格，**不在这页摊成两颗布尔**；
 * 这里只把"纵向开不开"这一件事交出去，落盘与回落都由那一颗枚举管。
 *
 * 开关本身不画卡片、不画分割线：容器走 [settingsEntryCard]，行与开关沿用本页同一族
 * （[LbSettingRow] 的 `trailing` 槽 + [MiniSwitch]，与「悬浮助手」「持续意图」两格同一条链）。
 *
 * ⚠ 换方向**不重新请求 AI、不重新付费、不清空当前回复**——那一侧的判据住在
 * `ui/panel/reply/ResultArea.kt`（结果集与阅读位置的主人），这一格只投一次意图，
 * 不持有、不缓存、不重建任何回复内容。
 */
@Composable
internal fun SettingsReplyLayoutEntry(
    vertical: Boolean,
    onVerticalChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.settingsEntryCard()) {
        Text(
            text = stringResource(R.string.settings_group_reply),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )
        LbSettingRow(
            title = stringResource(R.string.settings_card_vertical_label),
            subtitle = "",
            trailing = {
                MiniSwitch(
                    checked = vertical,
                    label = stringResource(R.string.settings_card_vertical_label),
                    onCheckedChange = onVerticalChange
                )
            }
        )
    }
}
