package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lovebrain.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.ExpressionDislikeReasons
import com.lovebrain.app.model.UnderstandingReasons
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/**
 * §6.4 :523 第四刀——点踩原因面板（那份清单里的"反馈原因"，最后一块）。
 *
 * 搬之前它是面板顶层 `Box` 里一块 `Column(fillMaxWidth)` 内联展开区。
 * 这台仪器在同一块 360x900dp 挂载槽里量到（账本 §34 记了原文数值）：
 * **16 颗可交互节点里有 14 颗不到 48dp**，清一色 **19dp 高**
 * （「其他」「跳过」只有 28dp 宽、二级原因 chip 38dp 宽、「保存反馈」56x19dp）；
 * 标题落在 y = **8dp**（贴顶、无遮罩，"盖住面板"这句话对它不成立）；
 * 而 10 颗选项 chip 的 **selected / stateDescription / toggleable 三者全无**——
 * "选中"在屏幕上靠一句 `✓ 理解错误` 的字面量加底色区分，读屏什么都听不出来。
 *
 * 现在：形状归 `LbModalSheet`（和纠正浮层、纠正中心同一个所有者），
 * 动作归 `LbModalSheetActions`（同一份动作词表、同一个 48dp 下限），
 * chip 从 `clickable` 改成 `toggleable(role = Role.Checkbox)`——
 * 那个 `✓` 继续画给眼睛看，但"选没选中"从此是语义树上的一个事实，不只是文案。
 *
 * 刻意**没有**再给这块造一个 state holder：它的显隐由
 * `viewModel.currentFeedbackCase` 驱动（点踩本身就是入口），再造一颗 `isOpen`
 * 等于把同一个事实放两处，第二天一定不同步。这里唯一真属于界面的状态是那份草稿
 * （选了哪些原因、补了什么话），按 `caseId` 记，换一条案例自动重来。
 *
 * 其余语义一字未改：原因可多选、可跳过、可补文字；理解错误与表达不喜欢不互斥；
 * 关闭弹层仍保留点踩，不强迫写作文；"同时记为偏好"必须用户主动去加，这里不自动写。
 */
@Composable
fun DislikeReasonHost(
    case: FeedbackCase?,
    onUpdateCase: (String, List<FeedbackCategory>, List<String>, String, String) -> Unit,
    onNavigateToMessageEdit: () -> Unit = {},
    onNavigateToMemoryCorrection: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (case == null) return

    var selectedCategories by remember(case.caseId) { mutableStateOf(case.categories) }
    var selectedReasons by remember(case.caseId) { mutableStateOf(case.reasons.toSet()) }
    var userNote by remember(case.caseId) { mutableStateOf(case.userNote) }
    var betterVersion by remember(case.caseId) { mutableStateOf(case.betterVersion) }

    LbModalSheet(onDismissRequest = onDismiss, modifier = modifier) {
        LbModalSheetTitle("这条回复哪里不满意？")
        Spacer(Modifier.height(Spacing.md))

        // 一级类别——可多选，不互斥
        CategoryChipRow(
            label = "理解错误",
            isSelected = FeedbackCategory.UNDERSTANDING_ERROR in selectedCategories,
            onToggle = {
                selectedCategories = if (FeedbackCategory.UNDERSTANDING_ERROR in selectedCategories) {
                    selectedCategories - FeedbackCategory.UNDERSTANDING_ERROR
                } else {
                    selectedCategories + FeedbackCategory.UNDERSTANDING_ERROR
                }
            }
        )
        if (FeedbackCategory.UNDERSTANDING_ERROR in selectedCategories) {
            // 二级原因
            ReasonChipGrid(
                reasons = UnderstandingReasons.ALL,
                selectedReasons = selectedReasons,
                onToggle = { reason ->
                    selectedReasons = if (reason in selectedReasons) {
                        selectedReasons - reason
                    } else {
                        selectedReasons + reason
                    }
                }
            )
            // 纠正入口——理解错误时提供。走设计系统那份动作词表：
            // 之前这两颗是 `Text(...).clickable(...).padding(...)`，实量 48x19 / 58x19dp
            LbModalSheetActions(
                listOf(
                    LbDialogAction("去改消息", onNavigateToMessageEdit, tone = LbDialogActionTone.Muted),
                    LbDialogAction("去纠正记忆", onNavigateToMemoryCorrection, tone = LbDialogActionTone.Muted)
                )
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        CategoryChipRow(
            label = "表达不喜欢",
            isSelected = FeedbackCategory.EXPRESSION_DISLIKE in selectedCategories,
            onToggle = {
                selectedCategories = if (FeedbackCategory.EXPRESSION_DISLIKE in selectedCategories) {
                    selectedCategories - FeedbackCategory.EXPRESSION_DISLIKE
                } else {
                    selectedCategories + FeedbackCategory.EXPRESSION_DISLIKE
                }
            }
        )
        if (FeedbackCategory.EXPRESSION_DISLIKE in selectedCategories) {
            ReasonChipGrid(
                reasons = ExpressionDislikeReasons.ALL,
                selectedReasons = selectedReasons,
                onToggle = { reason ->
                    selectedReasons = if (reason in selectedReasons) {
                        selectedReasons - reason
                    } else {
                        selectedReasons + reason
                    }
                }
            )
            // 表达反馈可"同时记为我的偏好"
            // 必须由用户主动选择，不自动加入偏好
            Text(
                text = "提示：可在知识库表达偏好中手动添加不喜欢的表达",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier.padding(vertical = Spacing.xs)
            )
        }

        CategoryChipRow(
            label = "其他",
            isSelected = FeedbackCategory.OTHER in selectedCategories,
            onToggle = {
                selectedCategories = if (FeedbackCategory.OTHER in selectedCategories) {
                    selectedCategories - FeedbackCategory.OTHER
                } else {
                    selectedCategories + FeedbackCategory.OTHER
                }
            }
        )

        Spacer(Modifier.height(Spacing.sm))

        // 用户补充说明（可选）
        // §6.5 第②栏：标题这行字是**兄弟节点**，不进可编辑节点的语义 → 读屏只念"编辑框"。
        // 同一条串挂到节点自己身上（两处共用同一个资源，不各写一遍）
        val noteLabel = stringResource(R.string.feedback_note_label)
        Text(
            text = noteLabel,
            style = AppTypography.labelSmall,
            color = TextSecondary
        )
        OutlinedTextField(
            value = userNote,
            onValueChange = { userNote = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = noteLabel },
            placeholder = {
                Text("如：把我想约会当成她已经答应了", style = AppTypography.labelSmall, color = TextHint)
            },
            textStyle = AppTypography.labelSmall.copy(color = TextPrimary),
            singleLine = false,
            maxLines = 3,
            shape = LoveBrainShape.sm
        )

        Spacer(Modifier.height(Spacing.xs))

        // 用户提供的更好版本（可选）
        val betterLabel = stringResource(R.string.feedback_better_label)
        Text(
            text = betterLabel,
            style = AppTypography.labelSmall,
            color = TextSecondary
        )
        OutlinedTextField(
            value = betterVersion,
            onValueChange = { betterVersion = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = betterLabel },
            placeholder = {
                Text("写你心目中更好的版本", style = AppTypography.labelSmall, color = TextHint)
            },
            textStyle = AppTypography.labelSmall.copy(color = TextPrimary),
            singleLine = false,
            maxLines = 3,
            shape = LoveBrainShape.sm
        )

        Spacer(Modifier.height(Spacing.md))

        // 操作行——「跳过」关面板但保留点踩；「保存反馈」提交这份草稿。
        // 之前两颗是 `Text(...).clickable(...).padding(...)`，实量 28x19dp 与 56x19dp。
        LbModalSheetActions(
            listOf(
                LbDialogAction("跳过", { onDismiss() }, tone = LbDialogActionTone.Muted),
                LbDialogAction(
                    label = "保存反馈",
                    onClick = {
                        onUpdateCase(
                            case.caseId,
                            selectedCategories,
                            selectedReasons.toList(),
                            userNote.trim(),
                            betterVersion.trim()
                        )
                        onDismiss()
                    }
                )
            )
        )
    }
}

/**
 * 一级类别选择 chip。
 *
 * §6.5 :532 那半句在这颗上补齐：之前它只有 `clickable`，
 * "选中"完全靠 `✓ ` 前缀与底色表达，实扫 **10/10 颗 chip 的
 * selected / stateDescription / toggleable 三者全无**（账本 §34）。
 * 现在 `toggleable(role = Role.Checkbox)`——那句 `✓` 继续画给眼睛看，
 * 但选没选中同时是语义树上的一个事实。
 *
 * §6.5 :531 那半句：热区原来是 chip 文字 + 上下各 `Spacing.xs`，实量 **19dp 高**
 * （「其他」宽也只剩 28dp）。下限直接取 `AppDimens.TOUCH_TARGET_MIN_DP`，
 * 而且垫在 `toggleable` **之前**——垫在外面那颗可点节点还是 19dp。
 */
@Composable
private fun CategoryChipRow(
    label: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "catChip_$label")
    Box(
        modifier = Modifier
            .padding(vertical = Spacing.xs)
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .background(
                if (isSelected) Primary else SurfaceCard,
                LoveBrainShape.sm
            )
            .border(
                1.dp,
                if (isSelected) Primary else Border,
                LoveBrainShape.sm
            )
            .toggleable(
                value = isSelected,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSelected) "✓ $label" else label,
            style = AppTypography.labelMedium,
            color = if (isSelected) Color.White else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 二级原因 chip 网格——与一级 chip 同一套语义（`toggleable` + 48dp 下限） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonChipGrid(
    reasons: List<String>,
    selectedReasons: Set<String>,
    onToggle: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        reasons.forEach { reason ->
            val isSelected = reason in selectedReasons
            val (interaction, scale) = rememberPressScale(0.94f, "reason_$reason")
            Box(
                modifier = Modifier
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(LoveBrainShape.sm)
                    .background(
                        if (isSelected) PrimaryLight else SurfaceCard,
                        LoveBrainShape.sm
                    )
                    .border(
                        1.dp,
                        if (isSelected) Primary else Border,
                        LoveBrainShape.sm
                    )
                    .toggleable(
                        value = isSelected,
                        role = Role.Checkbox,
                        interactionSource = interaction,
                        indication = null,
                        onValueChange = { onToggle(reason) }
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSelected) "✓ $reason" else reason,
                    style = AppTypography.labelSmall,
                    color = if (isSelected) PrimaryDark else TextSecondary
                )
            }
        }
    }
}
