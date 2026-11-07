package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.feature.composer.BatchImportDraft
import com.lovebrain.app.feature.composer.BatchTranscriptParser
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.theme.*

/**
 * 批量导入聊天记录的预览浮层（Issue #7）。
 *
 * 流程：粘贴多行文本 → 「解析预览」（[BatchTranscriptParser]，纯函数）→ 逐条显示
 * 发言人与正文 → 用户把每个昵称/未识别条目显式指定成 她/我 → 一次性提交。
 *
 * 三条硬约束在这颗浮层里落成行为：
 * 1. **提交前逐条必须指定发言人**——没有任何默认角色；解析器给的昵称只是昵称，
 *    她/我 的归属永远是用户的显式点击（未指定时提交键禁用）；
 * 2. **未识别格式的行不静默丢弃**——`speakerName == null` 的条目与解析警告都画出来，
 *    用户可以逐条指定发言人或点 ✕ 移除；
 * 3. **只交真实对话通道**——提交产物是 [BatchImportDraft]（构造期拒绝 IDEA），
 *    由宿主走 `ComposerStore.Intent.AddMessage`，与手工输入同一条 HER/ME 路径，
 *    军师备注/补充通道在本浮层里根本没有出口。
 *
 * 显示文案全部走资源（`batch_import_*`）：不往可见字面量预算里加新债。
 * 单行输入行布局一寸未动——入口是 ➕ 的长按（见 [ReplyInput] 的 `onBatchImport`）。
 */
@Composable
fun BatchImportSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (List<BatchImportDraft>) -> Unit
) {
    // visible 每次翻转都重置整份状态：重开浮层看到的是干净的一版，不是上一次的残稿
    var raw by remember(visible) { mutableStateOf("") }
    var parsed by remember(visible) { mutableStateOf<List<BatchTranscriptParser.Entry>?>(null) }
    var warnings by remember(visible) { mutableStateOf<List<String>>(emptyList()) }
    // 昵称 → 她/我：同一昵称的所有条目一起生效（一份归属，不是每条猜一次）
    var nameRoles by remember(visible) { mutableStateOf<Map<String, ChatMessage.Role>>(emptyMap()) }
    // 未识别发言人的条目按序号各自指定
    var orphanRoles by remember(visible) { mutableStateOf<Map<Int, ChatMessage.Role>>(emptyMap()) }
    // 预览里被用户移除的条目（解析器的边角误拆，移除比让它挡住提交更诚实）
    var removed by remember(visible) { mutableStateOf<Set<Int>>(emptySet()) }

    val entries = parsed.orEmpty().withIndex().filter { it.index !in removed }
    val allAssigned = entries.isNotEmpty() && entries.all { (i, e) -> roleOf(e, i, nameRoles, orphanRoles) != null }

    LbModalSheet(
        onDismissRequest = onDismiss,
        visible = visible
    ) {
        LbModalSheetTitle(text = stringResource(R.string.batch_import_title))

        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.batch_import_paste_hint),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(Spacing.sm))
        BatchPasteField(
            value = raw,
            onValueChange = { raw = it },
            placeholder = stringResource(R.string.batch_import_paste_placeholder)
        )

        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LbChip(
                label = stringResource(
                    if (parsed == null) R.string.batch_import_parse else R.string.batch_import_reparse
                ),
                selected = false,
                onClick = {
                    val result = BatchTranscriptParser.parse(raw)
                    parsed = result.entries
                    warnings = result.warnings
                    // 重新解析 = 一切预览态作废，不残留旧归属
                    nameRoles = emptyMap()
                    orphanRoles = emptyMap()
                    removed = emptySet()
                },
                interaction = LbChipInteraction.Action,
                enabled = raw.isNotBlank(),
                style = LbChipStyles.filled.copy(pillHeight = 36.dp)
            )
        }

        parsed?.let {
            Spacer(Modifier.height(Spacing.sm))
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.batch_import_empty_result),
                    style = AppTypography.labelMedium,
                    color = Warning
                )
            } else {
                Text(
                    text = stringResource(R.string.batch_import_pick_speaker),
                    style = AppTypography.labelMedium,
                    color = TextSecondary
                )
            }
            warnings.forEach { w ->
                Text(
                    text = w,
                    style = AppTypography.labelMedium,
                    color = Warning,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }

        entries.forEach { (index, entry) ->
            val role = roleOf(entry, index, nameRoles, orphanRoles)
            BatchEntryRow(
                entry = entry,
                role = role,
                onPickRole = { picked ->
                    val name = entry.speakerName
                    if (name != null) {
                        // 同一昵称的所有条目一起生效；再点同一颗 = 撤销该归属
                        nameRoles = if (nameRoles[name] == picked) {
                            nameRoles - name
                        } else {
                            nameRoles + (name to picked)
                        }
                    } else {
                        orphanRoles = if (orphanRoles[index] == picked) {
                            orphanRoles - index
                        } else {
                            orphanRoles + (index to picked)
                        }
                    }
                },
                onRemove = { removed = removed + index }
            )
        }

        Spacer(Modifier.height(Spacing.sm))
        LbModalSheetActions(
            actions = listOf(
                LbDialogAction(
                    label = stringResource(R.string.batch_import_cancel),
                    tone = LbDialogActionTone.Muted,
                    onClick = onDismiss
                ),
                LbDialogAction(
                    label = stringResource(R.string.batch_import_submit, entries.size),
                    enabled = allAssigned,
                    onClick = {
                        val drafts = entries.mapNotNull { (i, e) ->
                            roleOf(e, i, nameRoles, orphanRoles)
                                ?.let { r -> BatchImportDraft(r, e.content) }
                        }
                        if (drafts.size == entries.size) {
                            onSubmit(drafts)
                        }
                    }
                )
            )
        )
    }
}

/** 一条条目的当前归属：昵称按昵称表，未识别条目按序号表；都没给 = null（必须显式指定） */
private fun roleOf(
    entry: BatchTranscriptParser.Entry,
    index: Int,
    nameRoles: Map<String, ChatMessage.Role>,
    orphanRoles: Map<Int, ChatMessage.Role>
): ChatMessage.Role? =
    entry.speakerName?.let { nameRoles[it] } ?: orphanRoles[index]

/** 多行粘贴框（只在浮层里，不碰输入行那颗单行胶囊） */
@Composable
private fun BatchPasteField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(SurfaceInset, LoveBrainShape.md)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
            .padding(Spacing.md)
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = AppTypography.labelMedium, color = TextHint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(Primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 预览的一行：发言人/正文 + 她/我两颗指定 chip + ✕ 移除 */
@Composable
private fun BatchEntryRow(
    entry: BatchTranscriptParser.Entry,
    role: ChatMessage.Role?,
    onPickRole: (ChatMessage.Role) -> Unit,
    onRemove: () -> Unit
) {
    val unknownSpeaker = stringResource(R.string.batch_import_unknown_speaker)
    val removeDesc = stringResource(R.string.a11y_batch_import_row_remove)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm)
            .background(SurfaceCard, LoveBrainShape.md)
            .border(
                width = AppDimens.BORDER_WIDTH_DP.dp,
                color = if (entry.speakerName == null) Warning else Border,
                shape = LoveBrainShape.md
            )
            .padding(Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.speakerName ?: unknownSpeaker,
                style = AppTypography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.speakerName == null) Warning else TextSecondary,
                modifier = Modifier.weight(1f)
            )
            if (entry.timestampText != null) {
                Text(
                    text = entry.timestampText,
                    style = AppTypography.labelMedium,
                    color = TextHint
                )
            }
            RemoveButton(removeDesc, onRemove)
        }
        Text(
            text = entry.content,
            style = AppTypography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.padding(top = Spacing.xs)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Spacing.xs)
        ) {
            SpeakerPickChip(
                label = ROLE_LABEL_HER,
                selected = role == ChatMessage.Role.HER,
                onClick = { onPickRole(ChatMessage.Role.HER) }
            )
            Spacer(Modifier.width(Spacing.sm))
            SpeakerPickChip(
                label = ROLE_LABEL_ME,
                selected = role == ChatMessage.Role.ME,
                onClick = { onPickRole(ChatMessage.Role.ME) }
            )
        }
    }
}

@Composable
private fun SpeakerPickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    LbChip(
        label = label,
        selected = selected,
        onClick = onClick,
        interaction = LbChipInteraction.Single,
        style = LbChipStyles.filled.copy(
            radius = LoveBrainShape.md,
            textStyle = AppTypography.labelMedium,
            fontWeightSelected = FontWeight.Bold,
            background = SurfaceInset,
            paddingHorizontal = 9.dp,
            paddingVertical = 0.dp,
            pressedScale = 0.92f,
            pressFeedback = LbChipPressFeedback.Spring,
            markSelectedWithCheck = false,
            layeredTouch = true,
            widthFloor = false,
            pillHeight = ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp
        )
    )
}

/** 预览行里的 ✕ 移除：热区垫到全站下限，读屏名字走资源 */
@Composable
private fun RemoveButton(description: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onRemove
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = description,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}
