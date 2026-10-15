package com.lovebrain.app.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.home.MiniSwitch
import com.lovebrain.app.ui.theme.*

// ════════════════════════════════════════════════════════════════════════════
// 持续意图（"这一个阶段一直在推进的那件事"）的入口与编辑器。
//
// ## 宿主
// 持续意图入口与编辑浮层由 `ui/panel/LoveBrainPanelScreen.kt` 的回复那一支挂载：
// - [IntentChip] 画在输入行的下一行，守卫仍是 `activeKb != null`（意图按库隔离，没有活动库就无处可存）；
//   点击 = `viewModel.intents.openEditor()`（先冻结"开在哪块库"再翻可见性，判据在 IntentController 里）。
// - [IntentEditorDialog] 挂在**面板根部**（与 `CorrectionCenterHost` / `MemoryCorrectionFlowHost`
//   同一层，遮罩才盖得住整屏），由 `viewModel.intents.showEditor` 这一本账翻起来；
//   保存回 `viewModel.intents.save(正文, 启用)`，关闭点名到 `dismissEditor()`。
// 这一扇浮层与设置页那一行**同一条语义**：只有"启用开关 + 正文 + 保存"。期限三档、
// "已完成"那一批控件已经随判据一起退役——两处各留一套会长成"这页干净了、那页还是旧行为"。
// 按知识库隔离、编辑器开在哪块库这两条能力全在 `IntentController` / `KnowledgeIntentService`，
// 这一层只是它们的入口与表单。
// ════════════════════════════════════════════════════════════════════════════

// ═══════════ 持续意图 UI 组件（面板标题行里的紧凑入口 + 编辑浮层） ═══════════

/**
 * 持续意图 chip — 紧凑入口，复用标题行空档。归进设计系统那颗 [LbChip]。
 * enabled=true 时高亮显示，点击打开编辑弹窗。
 *
 * 它是**入口**不是**选项**：点下去开编辑器，这一颗自己从不"在哪一格" ⇒
 * [LbChipInteraction.Action]。角色这一颗原来**一个都没声明**（`clickable` 没写 `role`），
 * 归并后由组件发 `Role.Button`——那是无障碍那一栏要的字段，不是顺手升级；
 * 语义树里仍然**不发** `selected`：「开/关」这件事本来就写在自己的名字里
 * （`意图·xxx` / `意图·关`），那里才是它的规范位。
 *
 * ⚠ 名字里的 `enabled` 走的是组件的 `selected` **颜色分支**（选中档 = 开着那一套底/描边/字色），
 * 不能交成组件的 `enabled`：意图关掉时这颗仍然点得动、仍然要开得编辑器——那是今天的交互。
 *
 * 档位 = [LbChipStyles.pill]（标题行里那颗紧凑胶囊）再钉上它自己的高度：
 * 圆角 `LoveBrainShape.full`、胶囊高 22dp、字 `labelSmall`、文案钉一行、
 * 开着 `PrimaryLight` 底 + `PrimarySubtle` 描边 + `PrimaryDark` 字、
 * 关着 `SurfaceInset` 底 + `Border` 描边 + `TextHint` 字、左右内边距 [Spacing.sm]、
 * 竖直内边距 0、按压 0.92、不加对勾。
 *
 * ⚠ 这一颗**没接**全局那颗 48dp 下限（`touchFloor = false` 就是 pill 那一档的形状，
 * 也是它改之前的形状——那条链上从来只有一个钉死的 `height(22.dp)`，没有 `heightIn`）。
 * 热区仍是 22dp 高，归并按原样交出去：垫上去会把这张标题行的版式换掉，
 * 那是换脸不是归并。这一笔是**既有欠账**：低于 48dp 下限要修就得单开一笔，一并改会顺手改掉标题行的长相。
 */
@Composable
fun IntentChip(
    enabled: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val label = if (enabled) {
        if (text.isNotBlank()) "意图·${text.take(8)}${if (text.length > 8) "…" else ""}"
        else "意图·未设"
    } else {
        "意图·关"
    }
    LbChip(
        label = label,
        selected = enabled,
        onClick = onClick,
        interaction = LbChipInteraction.Action,
        style = LbChipStyles.pill.copy(pillHeight = 22.dp)
    )
}

/**
 * 持续意图的长度上限——超限要**看得见地**提示，不许静默吞掉。
 *
 * F05（2026-10-15 书 §6）：这颗数从 private 提为 internal——浮层与设置页那格**共用一把尺**，
 * 以前它只归浮层，设置入口的保存没有同一条约束（两处入口规则不一致就是这么来的）。
 * 超限的形状两处同口径：字不截断、话要短、保存灰着还在。
 */
internal const val INTENT_MAX_LENGTH = 200

/**
 * 持续意图编辑弹窗——浮层那一半的形状归设计系统的 `LbModalSheet`，这一层只转参数。
 *
 * 为什么这一族不能用 Material 的 `AlertDialog`：面板跑在 `TYPE_APPLICATION_OVERLAY` 窗口里，
 * 那里没有合适的 activity token，起 Dialog 窗口会直接抛 `WindowManager.BadTokenException`——
 * 那正是 `LbModalSheet` 存在的理由（见它文件头的 KDoc）。
 * 壳只负责"起一扇浮层"，标题与表单交给 [IntentEditorBody]——与 `ProviderEditDialog` +
 * `ProviderFormBody` 同一分工。
 *
 * [saving] 是宿主那一侧"这次写盘还在飞"的读数：为真时「保存」禁用。判据是
 * "禁用而不是再开一条协程写同一份草稿"——重复提交在 `IntentController.save` 里也挡了一道，
 * 屏上这一道是让用户看得见"正在存"。
 *
 * `internal` 是为了让 `IntentEditorSheetMergeTest` 挂**生产那一颗**（连同它的浮层壳），
 * 而不是给测试另开一只旁门（同一形见 `ProviderFormBody`）。
 */
@Composable
internal fun IntentEditorDialog(
    text: String,
    enabled: Boolean,
    visible: Boolean = true,
    saving: Boolean = false,
    onSave: (text: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onInputIntent: (() -> Unit)? = null
) {
    LbModalSheet(onDismissRequest = onDismiss, visible = visible) {
        IntentEditorBody(
            text = text,
            enabled = enabled,
            saving = saving,
            onSave = onSave,
            onDismiss = onDismiss,
            onInputIntent = onInputIntent
        )
    }
}

/**
 * 意图编辑器本体——浮层里除了遮罩与卡片那两层的**全部**内容：标题、启用开关、
 * 正文输入框、两颗出口。
 *
 * 标题原来没有槽位（这一格的第一句就是那段说明文字），归并时把它抬进 `LbModalSheetTitle`，
 * 于是这一扇与 `CorrectionCenterHost`、`MemoryCorrectionFlowHost` 这些同层浮行长成同一个形状：
 * 标题 + 正文 + `LbModalSheetActions`，动作仍是同一份 `LbDialogAction` 词表。
 *
 * 有效期那三档加"已完成"那一档（以及它们背后的到期时刻）**已经从这一扇摘掉**：
 * 一条意图算不算开着，只看开关与正文，两处编辑入口共用同一条语义。
 */
@Composable
internal fun IntentEditorBody(
    text: String,
    enabled: Boolean,
    saving: Boolean,
    onSave: (text: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onInputIntent: (() -> Unit)? = null
) {
    var editText by remember { mutableStateOf(text) }
    var editEnabled by remember { mutableStateOf(enabled) }
    val overLimit = editText.length > INTENT_MAX_LENGTH

    Column(modifier = Modifier.fillMaxWidth()) {
        LbModalSheetTitle(stringResource(R.string.intent_editor_title))
        Spacer(Modifier.height(Spacing.md))
        // 启用开关行——复用设计系统里同一颗 MiniSwitch（与设置页那一颗同款），
        // 不再自己画一颗 44×24 的胶囊：那一颗没有 role、热区也欠 48dp 下限。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.intent_enable_label), style = AppTypography.labelLarge, color = TextPrimary)
            MiniSwitch(
                checked = editEnabled,
                label = stringResource(R.string.intent_enable_switch_label),
                onCheckedChange = { editEnabled = it }
            )
        }
        Spacer(Modifier.height(Spacing.md))
        // 文本输入框——归设计系统 [LbFormField] + [LbFieldInput]，不再自己画 Box+BasicTextField。
        // 超限时把错误行交给字段（[LocalLbFieldHasError] 让框描边转 Error），框与文案同一份状态。
        LbFormField(
            label = stringResource(R.string.intent_content_label),
            error = if (overLimit) stringResource(R.string.intent_too_long_error, INTENT_MAX_LENGTH) else null
        ) {
            LbFieldInput(
                value = editText,
                onValueChange = { editText = it },
                placeholder = stringResource(R.string.intent_content_placeholder),
                onInputIntent = onInputIntent
            )
        }
        // 简化字数计数——只一行，超限走上面的错误行
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${editText.length}/$INTENT_MAX_LENGTH",
                style = AppTypography.labelSmall,
                color = if (overLimit) Error else TextHint
            )
        }
        // 统一操作行：超限时「保存」是**灰着还在**，不是消失——用户得知道少填了什么。
        // 正在写盘时同样灰着还在：这一拍不许再点出第二次写盘。
        Spacer(Modifier.height(Spacing.md))
        LbModalSheetActions(
            listOf(
                LbDialogAction(stringResource(R.string.a11y_action_cancel), onDismiss, tone = LbDialogActionTone.Muted),
                LbDialogAction(
                    label = stringResource(R.string.intent_save),
                    enabled = !overLimit && !saving,
                    // 交出去的是屏上那一稿（trim 掉首尾空白），**不再 take(200)**：
                    // 超限已经被上面那道门拦住，那一刀永远轮不到落下去；留着它就是留下
                    // "哪天这道门松了会静默吞字"的第二个形状（书 §6：提示要可见，不许静默截断）。
                    onClick = { onSave(editText.trim(), editEnabled) }
                )
            )
        )
    }
}
