package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbEmptyState
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
import com.lovebrain.app.core.designsystem.LbSettingRow
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.Warning
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection

/**
 * 第6节第4条 :523 第三刀——记忆纠正中心。
 *
 * 原话：ResultArea「不再同时承载菜单、纠正中心、发送记录、改写、版本历史、
 * 反馈原因等所有浮层；这些拆成独立 state holder + modal host」。
 * 上一格搬走了「暂停时长／标记为错误」两颗，纠正中心是这句话里剩下的那一块。
 *
 * 搬之前的形状不是浮层，是一块**内联展开区**：`Column(fillMaxWidth)` 直接插进面板顶层
 * `Box`，所以它没有遮罩、不居中、把面板里其它内容往下顶。同一台仪器、同一个
 * 360x900dp 挂载槽量到（账本 第33节）：里面每一颗可点的东西——「关闭」和两条「撤销」——
 * 全是 **28x19dp**，够不到 第6节第5条 :531 的 48dp 下限；标题的 y 实量 **0dp**（贴顶，
 * 遮罩根本不存在——这个数是探针  把形状退回原形时报回来的）。
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
 *
 * 开合**只经 [CorrectionCenterHolder.isOpen] 驱动 `visible`**，这一棵树常驻：
 * 旧写法是体首一句 `if (!holder.isOpen) return`——close 那一帧整棵树被摘掉，
 * [LbModalSheet] 里那颗 `AnimatedVisibility` 没机会播 exit，所写的退场动画从来没生效过
 * （同一形状写在 `LbModalSheet` 的 `visible` 参数 KDoc 上）。现在树在，退场才播得完；
 * 不 open 时 `AnimatedVisibility` 自己不发射任何节点，也就没有"画而不见"那一档代价。
 */
@Composable
fun CorrectionCenterHost(
    holder: CorrectionCenterHolder,
    corrections: Map<String, MemoryCorrection>,
    onUndoCorrection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LbModalSheet(
        onDismissRequest = { holder.close() },
        modifier = modifier,
        visible = holder.isOpen
    ) {
        LbModalSheetTitle("记忆纠正中心")
        Spacer(Modifier.height(Spacing.md))

        if (corrections.isEmpty()) {
            // 空态交回设计系统里那唯一一处：这里原来是一行自己画的文字，说法一个字没改，
            // 改的是所有者——同一个"没有记录"不该每页长一个样。
            // 这一档没有动作（出口就是浮层那颗「关闭」），所以不传 action：
            // 空态里再长一颗可点的东西，就是给同一个决定修第二条路。
            LbEmptyState(message = "暂无纠正记录。在「本轮参考」中可对记忆发起纠正。")
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
 * 单条纠正记录 ⇒ 归 第6节第1条 表里"行"那一族的主人 [LbSettingRow]：
 * 行首是类型标签，标题是"哪一条记忆"，说明是补正内容，尾部那颗动作是「撤销」。
 *
 * 撤销必须在这里，不能只放在"已消失的那条引用的菜单"里——被过滤掉的引用
 * 根本不会再出现，用户就没有回头路了。这也是纠正中心存在的理由。
 *
 * 归并前后交出去的三样东西逐字不变（`CorrectionRecordRowSemanticsTest` 在看着）：
 * 跟着 enum 推出来的标签文案、`→ 补正内容`那一行、尾部那颗「撤销」的可访问名与按钮角色。
 *
 * 类型标签仍然由这一页画（走 `leading` 槽，不在设计系统里）：那颗胶囊的颜色是
 * 从 [CorrectionAction] 推出来的（Error / Warning / TextHint），是纠正这个词表的事，
 * 不是"行"的事——把它搬进组件就得在组件里再抄一份 enum。
 *
 * ⚠ 两处如实的形状变化，都是为了让归并**不把 第6节第5条 那把尺改小**：
 *  - 「撤销」以前由浮层动作词表画（48 见方）。行组件的尾部槽只垫了高度，
 *    两字标签会量出 38x48dp，所以那颗的宽度下限一起补进了 [LbSettingRow]；
 *  - 没有补正内容的那一条，说明槽空白就不画（旧写法是 `if` 包着那颗文本，
 *    归并时如果组件永远画，列表里就会多出一行看不见的空白）。
 */
@Composable
private fun CorrectionRecordRow(
    memoryId: String,
    correction: MemoryCorrection,
    onUndo: () -> Unit
) {
    LbSettingRow(
        leading = { CorrectionActionTag(correction) },
        title = memoryId.takeLast(30),
        subtitle = if (correction.replacementText.isBlank()) ""
        else "→ ${correction.replacementText.take(20)}",
        trailingText = "撤销",
        onTrailingClick = onUndo
    )
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
