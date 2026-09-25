package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.designsystem.Warning
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection

/**
 * §6.4 :523 第三刀——记忆纠正中心。
 *
 * 指导书原话：ResultArea「不再同时承载菜单、纠正中心、发送记录、改写、版本历史、
 * 反馈原因等所有浮层；这些拆成独立 state holder + modal host」。
 * 上一格搬走了「暂停时长／标记为错误」两颗，纠正中心是这句话里剩下的那一块。
 *
 * 搬之前的形状不是浮层，是一块**内联展开区**：`Column(fillMaxWidth)` 直接插进面板顶层
 * `Box`，所以它没有遮罩、不居中、把面板里其它内容往下顶。同一台仪器、同一个
 * 360x900dp 挂载槽量到（账本 §33）：里面每一颗可点的东西——「关闭」和两条「撤销」——
 * 全是 **28x19dp**，够不到 §6.5 :531 的 48dp 下限；标题的 y 实量 **0dp**（贴顶，
 * 遮罩根本不存在——这个数是探针 W1 把形状退回原形时报回来的）。
 * 改完复量：动作 **48x48dp**，标题落在槽位中部。
 *
 * 现在：开合归 [CorrectionCenterHolder]，渲染归 [CorrectionCenterHost] 一颗
 * [LbModalSheet]，动作走设计系统那份动作词表（[LbDialogAction]），
 * 所以它和别的浮层共用同一个 48dp 热区实现，而不是又自己画一颗。
 *
 * 数据仍由调用方喂进来：这里只持有"开没开"。VM 那侧的
 * `loadAllCorrections { … }` 是异步回调，塞进 UI 状态类里会把分层穿破
 * （`UiLayerDependencyContractTest` 那条"不许伸手进 VM"的闸会红）。
 */
class CorrectionCenterHolder {
    var isOpen: Boolean by mutableStateOf(false)
        private set

    fun open() { isOpen = true }
    fun close() { isOpen = false }
}

@Composable
fun rememberCorrectionCenterHolder(): CorrectionCenterHolder =
    remember { CorrectionCenterHolder() }

/**
 * 纠正中心的**唯一渲染处**。
 *
 * [modifier] 由调用方给：挂在面板顶层时，遮罩就盖满面板。
 */
@Composable
fun CorrectionCenterHost(
    holder: CorrectionCenterHolder,
    corrections: Map<String, MemoryCorrection>,
    onUndoCorrection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!holder.isOpen) return

    LbModalSheet(
        onDismissRequest = { holder.close() },
        modifier = modifier
    ) {
        LbModalSheetTitle("记忆纠正中心")
        Spacer(Modifier.height(Spacing.md))

        if (corrections.isEmpty()) {
            Text(
                text = "暂无纠正记录。在「本轮参考」中可对记忆发起纠正。",
                style = AppTypography.labelSmall,
                color = TextSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                corrections.forEach { (memoryId, correction) ->
                    CorrectionRecordRow(
                        memoryId = memoryId,
                        correction = correction,
                        onUndo = { onUndoCorrection(memoryId) }
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
        LbModalSheetActions(
            listOf(LbDialogAction("关闭", { holder.close() }, tone = LbDialogActionTone.Muted))
        )
    }
}

/**
 * 单条纠正记录：类型标签 + 记忆 ID + 补正内容 +（静音时的）时长，右边一颗撤销。
 *
 * 撤销必须在这里，不能只放在"已消失的那条引用的菜单"里——被过滤掉的引用
 * 根本不会再出现，用户就没有回头路了。这也是纠正中心存在的理由。
 */
@Composable
private fun CorrectionRecordRow(
    memoryId: String,
    correction: MemoryCorrection,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        CorrectionActionTag(correction)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memoryId.takeLast(30),
                style = AppTypography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
            if (correction.replacementText.isNotBlank()) {
                Text(
                    text = "→ ${correction.replacementText.take(20)}",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = 1
                )
            }
        }
        LbModalSheetActions(
            listOf(
                LbDialogAction(
                    label = "撤销",
                    onClick = onUndo,
                    tone = LbDialogActionTone.Accent
                )
            )
        )
    }
}

/**
 * 纠正类型的标签。文案与颜色都从 [CorrectionAction] 推出来——
 * 加一档纠正类型时这里会编译不过，而不是安静地少一类。
 */
@Composable
private fun CorrectionActionTag(correction: MemoryCorrection) {
    val label = correction.action.centerLabel()
    val toneColor = when (correction.action) {
        CorrectionAction.WRONG, CorrectionAction.WRONG_PERSON -> Error
        CorrectionAction.MUTED -> Warning
        CorrectionAction.FINISHED -> TextHint
    }
    val detail = if (correction.action == CorrectionAction.MUTED) {
        "·${durationLabel(correction.muteDuration)}"
    } else {
        ""
    }
    Box(
        modifier = Modifier
            .clip(LoveBrainShape.sm)
            .background(PrimaryLight, LoveBrainShape.sm)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs)
    ) {
        Text(
            text = label + detail,
            style = AppTypography.labelSmall,
            color = toneColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 中心页的说法与浮层菜单的说法同源：都从 enum 推，不各抄一份中文 */
private fun CorrectionAction.centerLabel(): String = when (this) {
    CorrectionAction.WRONG -> "不对"
    CorrectionAction.FINISHED -> "已结束"
    CorrectionAction.MUTED -> "暂时别提"
    CorrectionAction.WRONG_PERSON -> "不是她"
}
