package com.lovebrain.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LbDialog
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextSecondary

/**
 * 无障碍隐私披露 Dialog——用户首次开启消息捕获前展示。
 *
 * 这一屏是**法律性的长文**，不是"一句说明"，所以走 [LbDialog] 的 `body` 槽而不是 `message`；
 * 两颗出口的着色由 `LbDialogActionTone` 决定（原来这里自己写了 `color = Primary` 与字重）。
 */
@Composable
fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    LbDialog(
        title = "开启消息捕获前，请确认",
        onDismissRequest = onDismiss,
        confirm = LbDialogAction("同意并继续", onAgree),
        dismiss = LbDialogAction("取消", onDismiss, tone = LbDialogActionTone.Muted),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("LoveBrain 会接收长按与窗口变化事件，并读取相关界面节点文字，用于判断你主动长按的消息以及“复制”菜单。", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("用于把你主动选择的聊天内容加入悬浮窗，从而生成回复建议。", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("当你请求 AI 回复时，相关聊天文字和所需知识上下文会发送给你在 LoveBrain 中配置的 AI 模型供应商。", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("捕获内容可在后续操作中写入 LoveBrain 本地知识库，例如聊天归档、谈心记录和画像更新所需的数据。", style = AppTypography.bodyMedium, color = TextSecondary)
                HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))
                Text("LoveBrain 不会通过无障碍服务自动点击、自动发送消息。你可以随时关闭“消息捕获”，或在系统设置中撤销无障碍权限。", style = AppTypography.labelMedium, color = TextHint)
            }
        }
    )
}
