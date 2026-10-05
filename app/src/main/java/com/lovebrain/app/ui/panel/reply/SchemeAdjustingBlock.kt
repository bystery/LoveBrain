package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.model.RewriteCommand

/** 一排放两颗：卡内净宽 142dp 里两颗粒子各约 69dp，四颗塞同一行就点不准了 */
private const val ADJUST_OPTIONS_PER_ROW = 2

/**
 * 方案卡调整态展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 卡片进入调整态后只列四项（[RewriteCommand.UI_OPTIONS] 那份**可见名单**：
 * 更自然 / 换种说话 / 更温柔 / 自定义），在 158dp 卡内排成 2×2：
 * - 前三项走现成的 `onRewrite(command)`——传的是 enum 自己，不是屏幕上那行字，
 *   所以"换种说话"这个显示名和枚举里那条稳定名字不一致也不会把请求弄丢；
 * - 点"自定义"才出现输入框与提交，再点一次收回；
 * - 这里**不再画**常驻的"取消/收起"那颗：卡片收起归"点卡片外侧"那一侧判（见 [onCancel]）。
 * - 不显示方向 chips（方向属于 Result-level）、不往正文下方追加内容。
 *
 * 尺寸全按卡内那一档：粒子视觉高 [SchemeCardDimens.ADJUST_PILL_HEIGHT_DP]、热区同数，
 * 输入框 28–36dp、一至两行、超出内部滚动，提交是小字。卡片本体仍是固定 158x150，
 * 内容顶不下就整块纵向滚动兜底。
 *
 * **草稿与"自定义开没开"这两样在方案卡那条链路上不住在本块里**（[customDraft] /
 * [isCustomInputOpen] 由卡片行按 identity.key 持有）：横向列表会把滑出视口的 item 连同其
 * `remember` 一起丢掉，状态留在本块就是——滑出去再滑回来一句草稿都没了，或者更糟：
 * 上一张卡的草稿落到同名标签的下一张卡上。调用方没传（null）时本块自己临时记一份，
 * 免得"点了没反应"。
 *
 * [onCancel] 是留给"点卡片外侧收起"的接线点：外侧判定归卡片行——点在卡内的胶囊、
 * 输入框或滚动区域都不算外侧，本块也不往卡片外涂任何东西。
 * modifier（含 weight）由调用方传入。
 */
@Composable
internal fun SchemeAdjustingBlock(
    onRewrite: (RewriteCommand) -> Unit,
    onCustomRewrite: (String) -> Unit,
    onCancel: () -> Unit = {},
    // null = 本块自己临时记一份（调用方没接线时的兜底，不让"点了没反应"发生）；
    // 非 null = 这份草稿/开合归卡片行按 identity.key 持有，本块不私存。
    modifier: Modifier = Modifier,
    customDraft: String? = null,
    onCustomDraftChange: (String) -> Unit = {},
    isCustomInputOpen: Boolean? = null,
    onCustomInputOpenChange: (Boolean) -> Unit = {}
) {
    // 读屏名字外面取好再闭包进去：semantics 的 lambda 不是 @Composable
    val customHint = stringResource(R.string.scheme_custom_hint)
    var localDraft by remember { mutableStateOf("") }
    var localOpen by remember { mutableStateOf(false) }
    val draft = customDraft ?: localDraft
    val open = isCustomInputOpen ?: localOpen
    val writeDraft: (String) -> Unit = { text ->
        if (customDraft == null) localDraft = text
        onCustomDraftChange(text)
    }
    val writeOpen: (Boolean) -> Unit = { value ->
        if (isCustomInputOpen == null) localOpen = value
        onCustomInputOpenChange(value)
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        RewriteCommand.UI_OPTIONS.chunked(ADJUST_OPTIONS_PER_ROW).forEach { rowCommands ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                rowCommands.forEach { command ->
                    val isCustom = command == RewriteCommand.CUSTOM
                    AdjustOption(
                        command = command,
                        isSelected = isCustom && open,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isCustom) writeOpen(!open)
                            else onRewrite(command)
                        }
                    )
                }
                // 单数那一行补齐占位，保证两颗与两行那格的宽度同一把尺
                repeat(ADJUST_OPTIONS_PER_ROW - rowCommands.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (open) {
            CustomRequirementEditor(
                value = draft,
                onValueChange = writeDraft,
                name = customHint,
                onSubmit = { text ->
                    if (text.isNotBlank()) onCustomRewrite(text.trim())
                }
            )
        }
    }
}

/**
 * 调整区的一枚选项：粒子本身就是热区（[SchemeCardDimens.ADJUST_PILL_HEIGHT_DP] 见方一档），
 * 卡内这一族不借全站那颗 48dp 下限——四项各约 69dp 宽、28dp 高，2×2 摆在 158dp 卡内。
 */
@Composable
private fun AdjustOption(
    command: RewriteCommand,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.94f, "adjustOption${command.name}")
    Box(
        modifier = modifier
            .height(SchemeCardDimens.ADJUST_PILL_HEIGHT_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .background(
                if (isSelected) PrimarySubtle else PrimaryLight,
                LoveBrainShape.sm
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            // 名字就用粒子上那行字：读屏从子节点读得到，再挂一条 contentDescription 就是念两遍
            .padding(
                horizontal = SchemeCardDimens.TAG_HPAD_DP.dp,
                vertical = SchemeCardDimens.TAG_VPAD_DP.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            command.displayLabel,
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 自定义那一格展开后的输入与提交：卡内紧凑档。
 *
 * 输入框 28–36dp、最多两行，字超出这一格就内部滚动——不替卡片要高度（本体固定 158x150）；
 * 提交是小字，不铺成整行大按钮。可编辑节点自己带读屏名字（placeholder 只是举例）。
 */
@Composable
private fun CustomRequirementEditor(
    value: String,
    onValueChange: (String) -> Unit,
    name: String,
    onSubmit: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(
                    min = SchemeCardDimens.CUSTOM_FIELD_MIN_HEIGHT_DP.dp,
                    max = SchemeCardDimens.CUSTOM_FIELD_MAX_HEIGHT_DP.dp
                )
                .clip(LoveBrainShape.sm)
                .background(SurfaceInset, LoveBrainShape.sm)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            if (value.isEmpty()) {
                Text(
                    "如：保留第一句，第二句不要",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = AppTypography.labelSmall.copy(color = TextPrimary),
                cursorBrush = SolidColor(Primary),
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .semantics { contentDescription = name }
            )
        }
        Text(
            "确认改写",
            style = AppTypography.labelSmall,
            color = if (value.isNotBlank()) PrimaryDark else TextHint,
            maxLines = 1,
            modifier = Modifier
                .clip(LoveBrainShape.sm)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { onSubmit(value) }
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}
