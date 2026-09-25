package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lovebrain.app.R
import androidx.compose.ui.res.stringResource
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
 * 点踩原因面板——点踩后展开轻量原因选择。
 *
 * - 原因可多选、可跳过、可补文字。
 * - 关闭弹层仍保留点踩，不强迫写作文。
 * - 理解错误与表达不喜欢不互斥。
 * - 允许同时选择两类。
 * - 点击理解错误后，若问题在本轮消息，打开既有角色/正文编辑；
 *   若问题在旧记忆，打开同一套记忆纠正流程。
 *   修正完成后提示"输入已更新"，重新生成由用户触发。
 *   （此处只提供入口回调，具体导航由上层处理。）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DislikeReasonPanel(
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(SurfaceInset, LoveBrainShape.md)
            .border(1.dp, Border, LoveBrainShape.md)
            .padding(Spacing.md)
            .animateContentSize()
    ) {
        Text(
            text = "这条回复哪里不满意？",
            style = AppTypography.labelMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(Spacing.sm))

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
            // 纠正入口——理解错误时提供
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "去改消息",
                    style = AppTypography.labelSmall,
                    color = PrimaryDark,
                    modifier = Modifier
                        .clip(LoveBrainShape.sm)
                        .background(PrimaryLight, LoveBrainShape.sm)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigateToMessageEdit() }
                        )
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                )
                Text(
                    text = "去纠正记忆",
                    style = AppTypography.labelSmall,
                    color = PrimaryDark,
                    modifier = Modifier
                        .clip(LoveBrainShape.sm)
                        .background(PrimaryLight, LoveBrainShape.sm)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigateToMemoryCorrection() }
                        )
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                )
            }
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

        Spacer(Modifier.height(Spacing.sm))

        // 操作行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 跳过——关闭面板但保留点踩
            Text(
                text = "跳过",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDismiss() }
                ).padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            )
            // 保存——更新案例
            val (saveInteraction, saveScale) = rememberPressScale(0.96f, "dislikeSaveScale")
            Text(
                text = "保存反馈",
                style = AppTypography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .graphicsLayer { scaleX = saveScale; scaleY = saveScale }
                    .clip(LoveBrainShape.sm)
                    .background(Primary)
                    .clickable(
                        interactionSource = saveInteraction,
                        indication = null,
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
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }
    }
}

/** 一级类别选择 chip */
@Composable
private fun CategoryChipRow(
    label: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "catChip_$label")
    Row(
        modifier = Modifier
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
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
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onToggle
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSelected) "✓ $label" else label,
                style = AppTypography.labelSmall,
                color = if (isSelected) Color.White else TextSecondary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/** 二级原因 chip 网格 */
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
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onToggle(reason) }
                    )
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
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
