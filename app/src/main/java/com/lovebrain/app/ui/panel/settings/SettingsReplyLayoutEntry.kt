package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbRowLabel
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.Spacing

/** 回复卡片那一行的行根锚点（量具按它找那一行，见锚点合同） */
internal const val SETTINGS_REPLY_ROW_TAG = "settings-row-reply"

/** 回复卡片那一行那条浅底轨道容器的锚点：整条轨道一颗，两格共享它这一条底 */
internal const val SETTINGS_REPLY_PILL_TAG = "settings-pill-reply"

/**
 * 设置页里的**回复卡片**那一格：同一行左边"回复卡片"、右边一条浅底轨道里的两档分段 `上下 | 左右`。
 *
 * 两档都是**选项**，不是"开 / 关"：纵向与横向是同一件事的两种摆法，谁都不是"关闭"那一侧，
 * 所以这一格画的是一条轨道里两颗互斥单选（与图标大小那一排同一族件），不再是一行文字配一颗开关，
 * 屏上也不许再出现"纵向排列"那种把方向说成"开不开"的文案。
 *
 * 方向的真值住在 `AppConfig.kt` 的方向枚举（`VERTICAL` = 上下、`HORIZONTAL` = 左右）与盘上那一格，
 * 默认纵向；这一格只把"现在在哪一档、用户点了哪一档"交出去，
 * 落盘、回落与脏值处理都由宿主那一颗枚举管，这里不摊成两颗布尔、也不抄第二份刻度。
 * 对外形状仍是一颗 `(Boolean) -> Unit`：点「上下」交 `true`、点「左右」交 `false`，
 * 页面与宿主都按这一颗接。
 *
 * 选中态跟着宿主交下来的那份读数走，这一格自己不留局部副本：宿主那一侧的画侧读数本来就是
 * 可观察的（`ui/panel/LoveBrainPanelScreen.kt` 里那颗 `cardLayout` 状态，点下去先写盘、再换那颗读数），
 * 所以点一下当场就换亮的那一档。「图标大小」那一格要自己留一颗及时重绘的局部值，是因为那一格的
 * 宿主读数是普通取值、没有可观察通道——两格的区别在宿主那一侧，不在这两行里。
 *
 * 脸照原型 `.segments` 那一族：底与圆角归**轨道**（[SettingsSegmentedPill]），格子里只有文字与
 * 选中那一块实心（[LbChipStyles.segmentedPill] 静息不画底不画描边）；行不画外框（[settingsEntryRow]），
 * 每格最小可见宽指回 [SettingsRowDimens.PILL_CELL_MIN_WIDTH_COMPACT_DP]（原型给两字档那一族单独一档 49），可见高与 48dp 热区由分层外盒管。
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 行自己**不画外框**、只取族里那颗行骨架；标签贴左、那条轨道贴右。
        modifier = modifier.settingsEntryRow().testTag(SETTINGS_REPLY_ROW_TAG)
    ) {
        // 标签走**自然宽**、中间那颗 Spacer 吃剩下的槽（原型 `.lbl{white-space:nowrap}` +
        // `.action-area{flex:1;justify-content:flex-end}`）：轨道因此贴右缘，而标签那颗节点报回来
        // 的就是自己的自然宽——整页"行标签同一档字"那把尺量的正是这个数，撑满槽的那一颗会被
        // 读成另一种字。两档的命中区都不靠标签让位：轨道按 fit-content 先量到自己那一份宽度。
        LbRowLabel(text = stringResource(R.string.settings_reply_card_row_label))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.sm)) // 原型 `.item{gap:5px}` / `.action-area{gap:6px}`，取矮的那一档
        // 顺序就是读序：上下在前（默认档）、左右在后，两档各画一格、共用同一个写口参数
        // [onVerticalChange]——点哪一格就把哪一档的方向交出去，没有"关"这一侧。
        SettingsSegmentedPill(modifier = Modifier.testTag(SETTINGS_REPLY_PILL_TAG)) {
            LbChip(
                label = stringResource(R.string.settings_reply_layout_vertical),
                selected = vertical,
                onClick = { onVerticalChange(true) },
                interaction = LbChipInteraction.Single,
                // 最小格宽落在**看得见**那一格上（从 modifier 传 widthIn 只撑外圈热区）
                style = LbChipStyles.segmentedPill.copy(
                    pillMinWidth = SettingsRowDimens.PILL_CELL_MIN_WIDTH_COMPACT_DP.dp
                )
            )
            LbChip(
                label = stringResource(R.string.settings_reply_layout_horizontal),
                selected = !vertical,
                onClick = { onVerticalChange(false) },
                interaction = LbChipInteraction.Single,
                // 最小格宽落在**看得见**那一格上（从 modifier 传 widthIn 只撑外圈热区）
                style = LbChipStyles.segmentedPill.copy(
                    pillMinWidth = SettingsRowDimens.PILL_CELL_MIN_WIDTH_COMPACT_DP.dp
                )
            )
        }
    }
}
