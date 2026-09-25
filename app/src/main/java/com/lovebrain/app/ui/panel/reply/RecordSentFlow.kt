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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * §6.4 :523 第五刀——「记录实际发送」的状态归持有者。
 *
 * 那份清单里的"发送记录"是最后一块散状态：之前它的四样东西
 * （开不开、保存中、草稿正文、绑哪张候选）是 `LoveBrainPanelScreen` 里四颗
 * `var … by remember { mutableStateOf(…) }`，摊在一个上千行的 composable 中间。
 * 形状上一格（`5245788`）已经归了 `LbModalSheet`，这格收的是**状态的所有者**。
 *
 * ## 为什么要把它做成一个类，而不是继续放局部变量
 *
 * 这一屏有一条**承诺**写在文案里：失败时浮层不关、用户已经敲进去的正文要留着。
 * 放在局部变量里，这条承诺只能靠"读那段接线然后相信它"；放进持有者，
 * 它就是一个能直接测的不变量（`saveRejected 之后草稿还在、saving 已放开`）。
 * 上一格量到的两条死路（`NO_KB` 没人接、重复失败不再发射）正是把这类承诺
 * 散在局部变量里才写得出来的——它们会把用户关在一个 `enabled = !saving` 的浮层里。
 *
 * ## 谁调用它
 *
 * VM 那侧仍是异步的：屏幕观察到 `actualSentState` 的跳变后调用
 * [recorded] / [saveRejected]。**持有者不认识 VM**，也不自己订阅状态流——
 * 那样分层会被穿透（`UiLayerDependencyContractTest` 那条"不许伸手进 VM"的闸会红）。
 */
class RecordSentFlow {
    var isOpen: Boolean by mutableStateOf(false)
        private set
    var saving: Boolean by mutableStateOf(false)
        private set
    var draft: String by mutableStateOf("")
        private set
    var schemeKey: String? by mutableStateOf(null)
        private set

    /**
     * 打开这一次记录。
     *
     * `saving` 一定要在这里复位：上一回失败留下的 `true` 会让新的浮层
     * 一出生就是"保存中"，而两个出口都是 `enabled = !saving`——那又是一条死路。
     */
    fun open(schemeIdentityKey: String? = null, prefill: String = "") {
        schemeKey = schemeIdentityKey
        draft = prefill
        saving = false
        isOpen = true
    }

    /** 保存中不接受编辑——与输入框自己那个 `enabled = !saving` 同一个口径 */
    fun editDraft(value: String) {
        if (!saving) draft = value
    }

    fun beginSaving() { saving = true }

    /** 记成了：关掉并清空，别把上一轮的正文留给下一次 */
    fun recorded() {
        isOpen = false
        saving = false
        draft = ""
        schemeKey = null
    }

    /**
     * 被拒了（找不到库、写盘失败、未激活知识库……）。
     *
     * 放开那把"保存中"的锁，但**浮层留着、草稿留着**——用户改两个字就能重试。
     * 这两半缺一不可：不解锁用户被困住，关浮层则把他的话弄丢。
     */
    fun saveRejected() { saving = false }

    /** 保存中不许从外面关掉（`dismissable = false` 的那条规矩，在状态这边也守着） */
    fun cancel() {
        if (!saving) {
            isOpen = false
            draft = ""
            schemeKey = null
        }
    }
}

@Composable
fun rememberRecordSentFlow(): RecordSentFlow = remember { RecordSentFlow() }

/**
 * 那颗浮层的**唯一渲染处**（前身是 `RecordSentDialog`，已退役进 `_temp/`）。
 *
 * 形状上的三条历史（遮罩冒充 360x1000dp 的可点击节点、两颗出口只有 28x19dp /
 * 96x19dp、"没填内容"靠 `onClick` 里静默吞点击）记在账本 §30.2，
 * 现在分别由 [LbModalSheet]、[LbModalSheetActions] 与下面那颗 `enabled` 管着。
 *
 * 保存中两个出口**灰着还在**（与 §2.1 那条合同同一个口径），进度反馈留在正文那一行。
 */
@Composable
fun RecordSentFlowHost(
    flow: RecordSentFlow,
    onConfirm: (RecordSentFlow) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 用户自己关掉时额外要做的事（面板用它把 VM 那一侧的结果状态清回 `IDLE`）。
     *
     * 留在这里而不是塞进持有者：持有者不认识 VM。
     * 关掉浮层本身由 [RecordSentFlow.cancel] 负责，这个回调只是**附带通知**。
     */
    onDismissed: () -> Unit = {}
) {
    if (!flow.isOpen) return

    // lambda 而不是局部 fun：组合期声明局部函数会让人误读它的作用域
    val close = {
        flow.cancel()
        onDismissed()
    }

    // 占位符与读屏名共用这一条串（见下面 OutlinedTextField 的注释）
    val draftHint = stringResource(R.string.panel_record_sent_hint)

    LbModalSheet(
        onDismissRequest = { close() },
        modifier = modifier,
        dismissable = !flow.saving
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
                value = flow.draft,
                onValueChange = { flow.editDraft(it) },
                // 这一格是新守卫逼出来的：`assertAllActionableLabeled` 量到这颗输入框
                // **既没有文案也没有 contentDescription**——placeholder 不会进语义树，
                // 读屏只会念"编辑框"。与 `CompactInput`/`ReplyInput` 同一个修法：
                // 把同一句话再挂成节点自己的名字，并且走资源，中英文一起覆盖。
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = draftHint },
                enabled = !flow.saving,
                placeholder = {
                    Text(draftHint, style = AppTypography.labelSmall, color = TextHint)
                },
                textStyle = AppTypography.bodySmall.copy(color = TextPrimary),
                singleLine = false,
                maxLines = 5,
                shape = LoveBrainShape.sm
            )
            if (flow.saving) {
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
                    LbDialogAction(
                        "取消", { close() },
                        tone = LbDialogActionTone.Muted,
                        enabled = !flow.saving
                    ),
                    LbDialogAction(
                        label = "确认已发送并记录",
                        enabled = !flow.saving && flow.draft.isNotBlank(),
                        onClick = { onConfirm(flow) }
                    )
                )
            )
        }
    }
}
