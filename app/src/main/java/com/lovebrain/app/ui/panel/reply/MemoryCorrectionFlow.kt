package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.model.MuteDuration

/**
 * §6.4：「本轮参考记忆」那条纠正流程的**状态持有者**。
 *
 * 指导书原话是"ResultArea 只负责结果内容，不再同时承载…浮层；这些拆成
 * 独立 state holder + modal host"。之前这两颗浮层的状态
 * （`showMuteSubmenu` / `showWrongDialog` / `wrongText`）是 `MemoryRefItem` 这一行
 * 自己 `remember` 的，浮层也就画在这一行的肚子里——`LbModalSheet` 的 `fillMaxSize()`
 * 于是只铺满**那一行**。改之前在本机量到（同一台仪器、360x400dp 挂载槽）：
 * 标题落在 y = **139–161dp**，而整槽垂直居中的话应当在 **≈190dp** 附近；
 * 也就是说"遮罩"盖不住面板其余部分，行以外那片还露在外面。
 *
 * 现在状态与渲染都从行里搬出来：行只发出"我要给这条选时长"/"这条要标错"的意图，
 * 谁持有 flow 谁渲染 [MemoryCorrectionFlowHost]。悬浮层因此可以画在面板顶层，
 * 而**不要求调用方必须提供宿主**——不传时 `ResultArea` 自己创建一个 flow 并就地渲染，
 * 少一处接线也不会变成"点了没反应"（那正是这种拆分最容易引入的新缺陷）。
 */
class MemoryCorrectionFlow {
    var muteTargetId: String? by mutableStateOf(null)
        private set
    var wrongTargetId: String? by mutableStateOf(null)
        private set
    var wrongDraft: String by mutableStateOf("")
        private set

    /** 一次只允许一颗：第二颗请求进来时，前一颗直接被顶掉（不是叠两层遮罩） */
    fun requestMute(memoryId: String) {
        wrongTargetId = null
        muteTargetId = memoryId
    }

    fun requestWrong(memoryId: String) {
        muteTargetId = null
        wrongTargetId = memoryId
        wrongDraft = ""
    }

    fun editWrongDraft(value: String) {
        wrongDraft = value
    }

    fun dismiss() {
        muteTargetId = null
        wrongTargetId = null
        wrongDraft = ""
    }
}

@Composable
fun rememberMemoryCorrectionFlow(): MemoryCorrectionFlow = remember { MemoryCorrectionFlow() }

/**
 * 那两颗浮层的**唯一渲染处**。
 *
 * 放在这一层而不是行里，是为了让"遮罩盖满它所在的容器"这句话成立：
 * 传进来越靠近面板顶层，覆盖就越完整。
 */
@Composable
fun MemoryCorrectionFlowHost(
    flow: MemoryCorrectionFlow,
    onMute: (String, MuteDuration) -> Unit,
    onWrong: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    flow.muteTargetId?.let { memoryId ->
        LbModalSheet(
            onDismissRequest = { flow.dismiss() },
            modifier = modifier
        ) {
            LbModalSheetTitle("暂停时长")
            Spacer(Modifier.height(Spacing.md))
            Column {
                MuteDuration.entries.forEach { duration ->
                    CorrectionSubmenuItem(durationLabel(duration)) {
                        onMute(memoryId, duration)
                        flow.dismiss()
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            // 这一档**没有主动作**：只有一个"取消"。空标签的动不入树（§29 修过的那条）
            LbModalSheetActions(
                listOf(LbDialogAction("取消", { flow.dismiss() }, tone = LbDialogActionTone.Muted))
            )
        }
    }

    flow.wrongTargetId?.let { memoryId ->
        LbModalSheet(
            onDismissRequest = { flow.dismiss() },
            modifier = modifier
        ) {
            LbModalSheetTitle("标记为错误")
            Spacer(Modifier.height(Spacing.sm))
            // §6.5 第②栏：屏幕上那行说明与输入框的读屏名字共用同一条资源
            val wrongHint = stringResource(R.string.memory_wrong_input_hint)
            Text(
                text = wrongHint,
                style = AppTypography.labelSmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = flow.wrongDraft,
                onValueChange = { flow.editWrongDraft(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = wrongHint },
                placeholder = {
                    Text("输入正确的内容", style = AppTypography.labelSmall, color = TextHint)
                },
                textStyle = AppTypography.labelSmall.copy(color = TextPrimary),
                singleLine = false,
                maxLines = 3,
                shape = LoveBrainShape.sm
            )
            Spacer(Modifier.height(Spacing.md))
            LbModalSheetActions(
                listOf(
                    LbDialogAction("取消", { flow.dismiss() }, tone = LbDialogActionTone.Muted),
                    LbDialogAction(
                        label = "确认",
                        onClick = {
                            onWrong(memoryId, flow.wrongDraft.trim())
                            flow.dismiss()
                        }
                    )
                )
            )
        }
    }
}

/** 时长选项的中文说法。原来散在三个手写的 `CorrectionSubmenuItem("仅本轮")` 里，
 *  少一档没人会发现；现在跟着 enum 走，加一档就多一行。
 *  `internal` 是给守卫用的：测试要拿**同一份**标签去树里找，而不是自己抄一遍中文。 */
internal fun durationLabel(duration: MuteDuration): String = when (duration) {
    MuteDuration.THIS_ROUND -> "仅本轮"
    MuteDuration.TODAY -> "今天剩余"
    MuteDuration.UNTIL_RESTORE -> "直到手动恢复"
}
