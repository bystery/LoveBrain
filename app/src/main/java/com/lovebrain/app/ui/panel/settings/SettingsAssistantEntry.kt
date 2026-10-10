package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbSettingRow
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionSize
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.ui.home.MiniSwitch

/**
 * 悬浮窗齿轮那扇整窗设置页里的**悬浮助手**那一格：一颗开关 + 一个明确的「关闭悬浮助手」动作。
 *
 * 这一格是 M05/M06 的落点，四条纪律全部写在这颗件身上（判据在
 * `ui/panel/settings/SettingsAssistantEntryStopConsistencyTest.kt`）：
 *
 * 1. **它不持有状态，也不新造读数**。[assistantOn] 没有默认值、本文件里没有 `remember`／
 *    `mutableStateOf`／任何一颗盘：屏幕上那颗开关亮的唯一理由是宿主交下来的那一份
 *    **真实窗口状态**（`FloatingService.isAssistantOn(FloatingService.windowStateFlow)` 的读数，
 *    也就是 `WindowState != STOPPED`）。拨一下开关就先把界面翻成"已经开了"，是最难查的一种假象：
 *    权限没给、系统拒绝挂载、服务当场 `stopSelf()`，屏幕上却是一颗亮着的开关。
 * 2. **两颗动作只投回调，不在本页做任何平台侧的事**。开 = [onRequestEnable]（宿主接
 *    既有的那条启动链：先查悬浮窗权限、要授权就去授权、拿到权限才 `startForegroundService`——
 *    那颗链住在 `ui/SetupActivity.kt`，只有 Activity 认得授权页回来之后要续跑）；
 *    关 = [onRequestClose]（宿主接**主页停止按钮同一颗**停止入口，最终 `FloatingService.stopSelf()`
 *    走 `onDestroy` 那条清理路径）。这一格自己既不停服务、也不隐藏窗口，
 *    所以它不可能长成"第二个停止入口"。
 * 3. **「关闭」与「暂时隐藏」是两件事，这一格只有前者**。[onRequestClose] 不许被接到
 *    `ACTION_TEMP_HIDE`／`tempHide()` 那一支：隐藏时服务仍在跑、前台通知仍在、视图与 composition
 *    都还在，把它当成"已关闭"就是让用户以为窗已经没了、屏上却还留着一颗随时会亮回来的球。
 *    反过来 `TEMP_HIDDEN` 在这一格读出来仍然是**开着**（由第 1 条那颗换算保证）。
 * 4. **不加第五种状态、不加第二本账、不在狭窄顶部塞新图标**。面板顶部那颗「收起」维持原行为
 *    （收起完整面板、留下悬浮球），这一格是它之外唯一新增的出口，走的是本页已有的两颗控件
 *    （[LbSettingRow] + [MiniSwitch]，以及关闭动作那颗 [LbTextAction] 的文字档）。
 *
 * 版式：容器仍走那一族唯一的主人 [settingsEntryCard]（与透明度、知识库、意图三格同一条链）；
 * 组名用 `settings_group_assistant`，与那三格的小标题同一字阶同一墨色。
 * 关闭那颗只在 [assistantOn] 时在场：服务已经停了还画一颗"关闭"，就是那颗点了没反应的死按钮。
 *
 * 热区与名字都不在这格自拼：[MiniSwitch] 自带 ≥48 见方热区 + `Role.Switch` + 读屏名字，
 * [LbTextAction] 的文字档热区两轴也垫到全站下限、名字就是那段字（所以不另写 `contentDescription`，
 * 写两处会被读屏念两遍）。
 */
@Composable
internal fun SettingsAssistantEntry(
    assistantOn: Boolean,
    onRequestEnable: () -> Unit,
    onRequestClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.settingsEntryCard()) {
        Text(
            text = stringResource(R.string.settings_group_assistant),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )
        // 开关行：整行**不**接点击（那一颗会点的就是行尾 MiniSwitch 自己，一颗可点节点、一份名字）。
        LbSettingRow(
            title = stringResource(R.string.settings_assistant_label),
            subtitle = "",
            trailing = {
                MiniSwitch(
                    checked = assistantOn,
                    label = stringResource(R.string.settings_assistant_label),
                    onCheckedChange = { on ->
                        // 拨开只投"走既有启动路径"那一次；拨关只投"与主页同一颗停止入口"那一次。
                        // 两条各一个回调，不合并成一颗 `(Boolean) -> Unit`：
                        // 合并之后调用方就得以这一颗布尔再判一次该干什么，判据会长回页面里。
                        if (on) onRequestEnable() else onRequestClose()
                    }
                )
            }
        )
        if (assistantOn) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LbTextAction(
                    label = stringResource(R.string.settings_assistant_close),
                    onClick = onRequestClose,
                    tone = LbTextActionTone.Destructive,
                    size = LbTextActionSize.RowCapsule
                )
            }
        }
    }
}
