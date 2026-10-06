package com.lovebrain.app.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
// AlertDialog 已替换为 PanelModalHost，避免 Service 宿主 BadTokenException
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

// ════════════════════════════════════════════════════════════════════════════
// 持续意图（"这一个阶段一直在推进的那件事"）的入口与编辑器。
//
// ## 这个文件为什么以前叫 SuggestPanel
//
// 它原先装的是「今日锦囊」整页（第12节第1条 已按用户授权删除的独立功能），而持续意图那颗入口
// 与这扇编辑浮层是**借在标题行里**长在这个页面上的——它们从来不是锦囊的一部分：
// 回复链每次生成都读 `intents.config` 拼进 prompt，PRODUCT_SPEC 把持续意图列在
// "保留、不许借简化删"那一栏， 要并入主动发的"有效持续意图"也靠这一扇录入。
// 删掉锦囊页时这两颗必须活着，所以留在原路径上；改名成 `IntentEditorSheet.kt` 是
// 跟着换宿主这一步一起做的（文件用 mv 移，不是删）。
//
// ## 宿主（ 之后补齐的那一半接线）
// 锦囊页没了之后这一族一度**没有任何调用点**画它们。现在的宿主是
// `ui/panel/LoveBrainPanelScreen.kt` 的回复那一支：
// - [IntentChip] 画在输入行的下一行，守卫仍是 `activeKb != null`（意图按库隔离，没有活动库就无处可存）；
//   点击 = `viewModel.intents.openEditor()`（先冻结"开在哪块库"再翻可见性，判据在 IntentController 里）。
// - [IntentEditorDialog] 挂在**面板根部**（与 `CorrectionCenterHost` / `MemoryCorrectionFlowHost`
//   同一层，遮罩才盖得住整屏），由 `viewModel.intents.showEditor` 这一本账翻起来；
//   保存回 `viewModel.intents.save(...)`，关闭点名到 `dismissEditor()`。
// 到期、完成、暂停、按知识库隔离这四条能力全在 `IntentController` / `KnowledgeIntentService`，
// 这一层只是它们的入口与表单，一处都没减。
// ════════════════════════════════════════════════════════════════════════════

// ═══════════ 持续意图 UI 组件（面板标题行里的紧凑入口 + 编辑浮层） ═══════════

/**
 * 持续意图 chip — 紧凑入口，复用标题行空档。归进设计系统那颗 [LbChip]。
 * enabled=true 时高亮显示，点击打开编辑弹窗。
 *
 * 它是**入口**不是**选项**：点下去开编辑器，这一颗自己从不"在哪一格" ⇒
 * [LbChipInteraction.Action]。角色这一颗原来**一个都没声明**（`clickable` 没写 `role`），
 * 归并后由组件发 `Role.Button`——那是 第6节第5条 :532 要的那一栏，不是顺手升级；
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
 * 那是换脸不是归并。这一笔是**既有欠账**，登记着，与有效期那一排同一处理。
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
 * 意图编辑浮层的长度上限——超限要**看得见地**提示，不许静默吞掉。
 */
private const val INTENT_MAX_LENGTH = 200

/**
 * 持续意图编辑弹窗（第6节第1条 归并）——浮层那一半的形状归设计系统的 `LbModalSheet`，这一层只转参数。
 *
 * 为什么这一族不能用 Material 的 `AlertDialog`：面板跑在 `TYPE_APPLICATION_OVERLAY` 窗口里，
 * 那里没有合适的 activity token，起 Dialog 窗口会直接抛 `WindowManager.BadTokenException`——
 * 那正是 `LbModalSheet` 存在的理由（见它文件头的 KDoc 与 `SheetProbeTest`（已删））。
 * 归并之前这里已经站对了所有者，但**壳里还自己排了一遍版面**：标题、表单、两颗出口
 * 全摊在这颗浮层里，于是它既画浮层又管排版。现在壳只负责"起一扇浮层"，
 * 标题与表单交给 [IntentEditorBody]——与 `ProviderEditDialog` + `ProviderFormBody` 同一分工。
 *
 * `internal` 是为了让 `IntentEditorSheetMergeTest` 挂**生产那一颗**（连同它的浮层壳），
 * 而不是给测试另开一只旁门（同一形见 `ProviderFormBody`）。
 */
@Composable
internal fun IntentEditorDialog(
    text: String,
    enabled: Boolean,
    expiry: com.lovebrain.app.model.IntentExpiry = com.lovebrain.app.model.IntentExpiry.UNTIL_DONE,
    expiryDate: String = "",
    status: com.lovebrain.app.model.IntentStatus = com.lovebrain.app.model.IntentStatus.ACTIVE,
    onSave: (String, Boolean, com.lovebrain.app.model.IntentExpiry, String, com.lovebrain.app.model.IntentStatus) -> Unit,
    onDismiss: () -> Unit
) {
    LbModalSheet(onDismissRequest = onDismiss) {
        IntentEditorBody(
            text = text,
            enabled = enabled,
            expiry = expiry,
            expiryDate = expiryDate,
            status = status,
            onSave = onSave,
            onDismiss = onDismiss
        )
    }
}

/**
 * 意图编辑器本体——浮层里除了遮罩与卡片那两层的**全部**内容：标题、启用开关、有效期、
 * 日期与正文输入框、两颗出口。
 *
 * 标题原来没有槽位（这一格的第一句就是那段说明文字），归并时把它抬进 `LbModalSheetTitle`，
 * 于是这一扇与 `RecordSentFlowHost`、纠正中心、点踩原因面板长成同一个形状：
 * 标题 + 正文 + `LbModalSheetActions`，动作仍是同一份 `LbDialogAction` 词表。
 * ⚠ **没有新增任何文案**：那句话只是换了槽位（账记在 `UiStringLiteralBudgetTest` 的两栏）。
 */
@Composable
internal fun IntentEditorBody(
    text: String,
    enabled: Boolean,
    expiry: com.lovebrain.app.model.IntentExpiry,
    expiryDate: String,
    status: com.lovebrain.app.model.IntentStatus,
    onSave: (String, Boolean, com.lovebrain.app.model.IntentExpiry, String, com.lovebrain.app.model.IntentStatus) -> Unit,
    onDismiss: () -> Unit
) {
    var editText by remember { mutableStateOf(text) }
    var editEnabled by remember { mutableStateOf(enabled) }
    var editExpiry by remember { mutableStateOf(expiry) }
    var editExpiryDate by remember { mutableStateOf(expiryDate) }
    var editStatus by remember { mutableStateOf(status) }
    val overLimit = editText.length > INTENT_MAX_LENGTH

    Column(modifier = Modifier.fillMaxWidth()) {
        LbModalSheetTitle("设置一个持续的对话目标（如\"约她周末看电影\"），军师每轮生成时都会参考。")
        Spacer(Modifier.height(Spacing.md))
        // 开关行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用", style = AppTypography.labelLarge, color = TextPrimary)
            val (toggleInteraction, toggleScale) = rememberPressScale(0.92f, "intentToggleScale")
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(24.dp)
                    .graphicsLayer { scaleX = toggleScale; scaleY = toggleScale }
                    .clip(LoveBrainShape.full)
                    .background(if (editEnabled) Primary else SurfaceInset, LoveBrainShape.full)
                    .border(AppDimens.BORDER_WIDTH_DP.dp, if (editEnabled) Primary else Border, LoveBrainShape.full)
                    .clickable(interactionSource = toggleInteraction, indication = null) { editEnabled = !editEnabled },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    Modifier
                        .offset(x = if (editEnabled) 20.dp else 2.dp)
                        .size(20.dp)
                        .clip(LoveBrainShape.full)
                        .background(Color.White)
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        // 有效期选择
        Text("有效期", style = AppTypography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            IntentExpiryChip("直到完成", editExpiry == com.lovebrain.app.model.IntentExpiry.UNTIL_DONE) {
                editExpiry = com.lovebrain.app.model.IntentExpiry.UNTIL_DONE
            }
            IntentExpiryChip("仅今天", editExpiry == com.lovebrain.app.model.IntentExpiry.TODAY) {
                editExpiry = com.lovebrain.app.model.IntentExpiry.TODAY
            }
            IntentExpiryChip("指定日期", editExpiry == com.lovebrain.app.model.IntentExpiry.DATE) {
                editExpiry = com.lovebrain.app.model.IntentExpiry.DATE
            }
        }
        // 指定日期时显示日期输入框
        if (editExpiry == com.lovebrain.app.model.IntentExpiry.DATE) {
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, LoveBrainShape.md)
                    .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.md)
                    .padding(Spacing.md)
            ) {
                if (editExpiryDate.isEmpty()) {
                    Text("输入日期（如 2026-12-31）", color = TextHint, style = AppTypography.bodyMedium)
                }
                BasicTextField(
                    value = editExpiryDate,
                    onValueChange = { editExpiryDate = it },
                    textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // 状态操作——已完成时可标记完成
        if (editEnabled && editStatus == com.lovebrain.app.model.IntentStatus.ACTIVE) {
            Spacer(Modifier.height(Spacing.sm))
            val (completeInteraction, completeScale) = rememberPressScale(0.96f, "intentCompleteScale")
            Text(
                "标记为已完成",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier
                    .graphicsLayer { scaleX = completeScale; scaleY = completeScale }
                    .clickable(interactionSource = completeInteraction, indication = null) {
                        editStatus = com.lovebrain.app.model.IntentStatus.COMPLETED
                        editEnabled = false
                    }
                    .padding(vertical = Spacing.xs)
            )
        }
        Spacer(Modifier.height(Spacing.md))
        // 文本输入框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(SurfaceCard, LoveBrainShape.md)
                .border(
                    AppDimens.BORDER_WIDTH_DP.dp,
                    if (overLimit) Error else PrimarySubtle,
                    LoveBrainShape.md
                )
                .padding(Spacing.md)
        ) {
            if (editText.isEmpty()) {
                Text(
                    "输入你的持续意图…",
                    color = TextHint,
                    style = AppTypography.bodyMedium
                )
            }
            BasicTextField(
                value = editText,
                onValueChange = { editText = it },
                textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        // 字数 + 超限提示
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
        if (overLimit) {
            Text(
                "意图过长，请精简到 $INTENT_MAX_LENGTH 字以内",
                style = AppTypography.labelSmall,
                color = Error,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
        // 统一操作行：超限时「保存」是**灰着还在**，不是消失——用户得知道少填了什么
        Spacer(Modifier.height(Spacing.md))
        LbModalSheetActions(
            listOf(
                LbDialogAction("取消", onDismiss, tone = LbDialogActionTone.Muted),
                LbDialogAction(
                    label = "保存",
                    enabled = !overLimit,
                    onClick = {
                        onSave(editText.trim().take(INTENT_MAX_LENGTH), editEnabled, editExpiry, editExpiryDate.trim(), editStatus)
                    }
                )
            )
        )
    }
}

/**
 * 有效期选择 chip——归进设计系统那颗 [LbChip]。
 *
 * 语义是**一组里互斥的单选**（"直到完成 / 仅今天 / 指定日期"三选一）：原来自己画的
 * 那条链上把"现在选哪一档"写进字面前缀（`✓ $label`）与底色（Primary / SurfaceCard），
 * 归并后这两件事分别由 [LbChipInteraction.Single] 在语义树里发 `Selected`、由
 * [LbChipStyles.filled] 画实心选中档——同一件事只剩一份。
 *
 * 档位用 [LbChipStyles.filled] 再 `.copy` 两处：字色未选那一档原来是 `TextHint`（比
 * `filled` 默认的 `TextSecondary` 还浅一档），左右内边距原来是 `Spacing.sm`、上下 `Spacing.xs`
 * （比 `filled` 默认的 `lg / sm` 更紧凑——这组三颗挤在一行）。两处都是调用方用 `.copy`
 * 调数，不是在页面里再画一条 Modifier 链（同 `IntentChip` 用 `pill.copy(pillHeight = ...)`）。
 */
@Composable
private fun IntentExpiryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    LbChip(
        label = label,
        selected = isSelected,
        onClick = onClick,
        interaction = LbChipInteraction.Single,
        style = LbChipStyles.filled.copy(
            textColor = TextHint,
            paddingHorizontal = Spacing.sm,
            paddingVertical = Spacing.xs
        )
    )
}
