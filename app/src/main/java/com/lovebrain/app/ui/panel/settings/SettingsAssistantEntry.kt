package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbRowLabel
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.ui.home.MiniSwitch

/**
 * 行根锚点（整页量具的锚点合同）：量具按这一颗找到"悬浮助手"那一行。
 * 只挂在**行根**自己身上（与 [Modifier.settingsEntryRow] 同一颗），别处不许再挂同名 tag。
 */
internal const val SETTINGS_ASSISTANT_ROW_TAG = "settings-row-assistant"

/**
 * 设置页里的**悬浮助手**那一行：左边一竖排 = 行标签 + 极短的状态读数（运行中 / 已停止），
 * 右边 = 全站唯一那颗开关。这一颗是整扇设置页里唯一能启停助手服务的控制。
 *
 * 四条纪律（界面半边判据在 `ui/panel/settings/SettingsAssistantEntryStopConsistencyTest.kt`，
 * 宿主形状半边在 `service/FloatingServiceStopEntryConsistencyTest.kt`）：
 *
 * 1. **它不持有状态，也不新造读数**。[assistantOn] 没有默认值、本文件里没有 `remember`／
 *    任何一颗盘：屏幕上那颗开关亮的唯一理由是宿主交下来的那一份**真实窗口状态**
 *    （`FloatingService.isAssistantOn(WindowState != STOPPED)` 的读数，由页面一层交进来）。
 *    拨一下开关就先把界面翻成"已经开了"，是最难查的一种假象：权限没给、系统拒绝挂载、
 *    服务当场停掉，屏幕上却是一颗亮着的开关。
 * 2. **两颗动作只投回调，不在本页做任何平台侧的事**。开 = [onRequestEnable]（宿主接既有那条
 *    启动链：先查悬浮窗权限、拿到权限才起服务）；关 = [onRequestClose]（宿主接**主页停止按钮
 *    同一颗**停止入口，最终经服务自己的清理路径退出）。这一行自己既不停服务、也不隐藏窗口，
 *    所以它不可能长成"第二个停止入口"。两颗动作不合并成一颗 `(Boolean) -> Unit`：
 *    合并之后调用方就得以这一颗布尔再判一次该干什么，判据会长回页面里。
 * 3. **「关闭」与「暂时隐藏」是两件事，这一行只画前者**。隐藏态（服务仍在跑、随时会亮回球）
 *    在这里读出来仍然是**开着**；页头那颗"⌄"只把面板收成悬浮球，不是停服务——那颗归页面，
 *    这一行不许把"收起"写成"停止"，也不许替它另画一颗。
 * 4. **恰好一颗开关：这一行没有尾部的「关闭悬浮助手」按钮**。曾经这里是一颗开关下面再挂一颗
 *    同义关闭动作——同一件事两个入口，用户反而看不清哪颗才真能停服务。那颗已拆掉：
 *    这一行就是设置页里唯一能停服务的入口。整行**不**接点击，会点的只有行尾那颗开关，
 *    一颗可点节点、一份读屏名字。
 *
 * 版式（对照原型 `.assistant`，也是10-11 那一版"脸不对"的那一格）：**不再借 `LbSettingRow`**——
 * 那颗是全站公共件（首页与捕获页还在用），它的 `titleMedium` 粗体让"悬浮助手"在同一页里
 * 比"持续意图""当前知识库"大一档；设置页这一族的字阶由 `LbRowLabel` 自己解决
 * （`labelLarge` + `TextPrimary`，整页唯一一种行标签）。状态字按原型
 * `.assist-state{font-size:10px;color:--light}` 取 [AppTypography.labelSmall] + [TextHint]，
 * 它只是把同一份 [assistantOn] 复述成两个词，不是第二本账。
 * 行壳走族里那颗 [Modifier.settingsEntryRow]（48 版式下限 = 原型 `min-height:48`）；组外框、
 * 组名与横向内缩仍归**页面**，这一行不自带卡壳、不画组名、不垫横向 padding。
 * 热区与名字都不在这行自拼：[MiniSwitch] 自带 ≥48 见方热区 + `Role.Switch` + 读屏名字，
 * 外面不再套第二层容器（套了等于没改）。
 */
@Composable
internal fun SettingsAssistantEntry(
    assistantOn: Boolean,
    onRequestEnable: () -> Unit,
    onRequestClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        // 行根锚点与版式下限挂在同一颗节点上（锚点合同：量具只认行根那一颗）。
        // 整行不接点击——会点的只有行尾那颗开关（纪律 4）。
        modifier = modifier.settingsEntryRow().testTag(SETTINGS_ASSISTANT_ROW_TAG)
    ) {
        // 左侧一竖排：行标签在上、状态读数在下（原型 `.assistant` 的第一颗 div）。
        Column(modifier = Modifier.weight(1f)) {
            LbRowLabel(text = stringResource(R.string.settings_assistant_label))
            Text(
                text = stringResource(
                    if (assistantOn) R.string.assistant_state_running else R.string.assistant_state_stopped
                ),
                style = AppTypography.labelSmall,
                color = TextHint,
                // 原型 `.assist-state{margin-top:3px}`：现成档位里取矮的那一颗，不新造第三档
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
        MiniSwitch(
            checked = assistantOn,
            label = stringResource(R.string.settings_assistant_label),
            onCheckedChange = { on ->
                // 拨开只投"走既有启动路径"那一次；拨关只投"与主页同一颗停止入口"那一次。
                if (on) onRequestEnable() else onRequestClose()
            }
        )
    }
}
