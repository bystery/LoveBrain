package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary

/**
 * 记录实际发送——用户确认已发送的版本。
 *
 * 打开编辑框，可从当前候选预填，也可粘贴、改写或输入完全不同的话。
 * 按钮文案为"确认已发送并记录"，说明是用户自行确认，不代表应用检测到了发送行为。
 * 不强制每一轮完成此操作。
 *
 * 确认后写为"我"的真实消息，保存用户确认来源、关联候选版本（若有）、时间。
 * 若用户只是想收藏，继续使用点赞，不混淆两者。
 *
 * ## 这一屏的形状（§6.1 / §6.4）
 *
 * 原先它自己画遮罩 + 居中卡片 + 两颗裸 `Text` 当按钮。本机语义树实量（账本 §30.2）：
 * 遮罩自己是一颗 **360x1000dp** 的可点击节点（还把标题"记录实际发送"合并成了自己的名字），
 * 两颗出口分别是 **28x19dp** 与 **96x19dp**——比 §29 那套面板浮层的 26dp 还矮一半，
 * 全都够不到 §6.5 :531 的 48dp 下限。现在窗口骨架归 [LbModalSheet]：
 * 遮罩不再冒充按钮、出口垫到 48dp、内边距排在 `clickable` 之后。
 *
 * ## 两处一并收紧的判据
 *
 * 1. 以前"没填内容"是靠 `onClick` 里写 `if (text.isNotBlank())` 静默吞掉点击 ⇒ 那颗按钮
 *    **看起来能点、点了没反应**。现在只剩 `enabled` 一处判据：空文本时它是灰的。
 * 2. 以前 `saving` 时"确认"整颗**消失**、换成 spinner + 「保存中…」。现在两个出口
 *    **灰着还在**（与 §2.1 那条合同同一个口径），进度反馈留在正文那一行——
 *    用户既看得到"正在写"，也不会遇到"按钮忽然不见了"。
 *
 * 保存中不许点空白关闭（`dismissable = false`）：那会让"到底写进去没有"变成一个猜不透的问题。
 */
@Composable
fun RecordSentDialog(
    prefill: String = "",
    saving: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(prefill) }
    // 占位符与读屏名共用这一条串（见下面 OutlinedTextField 的注释）
    val draftHint = stringResource(R.string.panel_record_sent_hint)

    LbModalSheet(
        onDismissRequest = onDismiss,
        dismissable = !saving
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LbModalSheetTitle("记录实际发送")
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "确认你已发送这条消息（用户自行确认，不代表应用检测到发送）",
                style = AppTypography.labelSmall,
                color = TextHint
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                // 这一格是新守卫逼出来的：`assertAllActionableLabeled` 量到这颗输入框
                // **既没有文案也没有 contentDescription**——placeholder 不会进语义树，
                // 读屏只会念"编辑框"。与 `CompactInput`/`ReplyInput` 同一个修法：
                // 把同一句话再挂成节点自己的名字。
                // 这句走资源而不是内联字面量：占位符与读屏名**共用同一条串**，
                // 中英文一起覆盖（内联写两遍的话，改一处忘另一处就是新的错）。
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = draftHint },
                enabled = !saving,
                placeholder = {
                    Text(draftHint, style = AppTypography.labelSmall, color = TextHint)
                },
                textStyle = AppTypography.bodySmall.copy(color = TextPrimary),
                singleLine = false,
                maxLines = 5,
                shape = LoveBrainShape.sm
            )
            if (saving) {
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Primary,
                        modifier = Modifier.size(AppDimens.LOADING_SPINNER_SIZE_DP.dp),
                        strokeWidth = Spacing.xs
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "保存中…",
                        style = AppTypography.labelSmall,
                        color = TextHint,
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            LbModalSheetActions(
                listOf(
                    LbDialogAction("取消", onDismiss, tone = LbDialogActionTone.Muted, enabled = !saving),
                    LbDialogAction(
                        label = "确认已发送并记录",
                        enabled = !saving && text.isNotBlank(),
                        onClick = { onConfirm(text.trim()) }
                    )
                )
            )
        }
    }
}
