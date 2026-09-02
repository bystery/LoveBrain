package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*

/** 输入区内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object ReplyDimens {
    const val ROLE_CHIP_HEIGHT_DP = 28    // 她/我 角色 chip 高度（2 文件各自私有）
}

/**
 * 消息输入区（第 2 轮迭代）。
 *
 * 需求#2：《她》《我》《输入框》《添加》放在同一行；删除《粘贴》按钮。
 *
 * 设计来源（第 2 轮搜索）：
 * · 微信 8.0 聊天界面改版：功能按钮与输入框整合、单手操作、圆润边框
 * · CometChat Composer 最佳实践：single-line 输入框超长自动横向滚动
 * · MD3 Text Fields：单行输入自动左滚；Apple HIG：输入框配 clear 按钮（✕）
 */
@Composable
fun ReplyInput(
    draftText: String,
    currentRole: ChatMessage.Role,
    editingIndex: Int,
    onDraftChange: (String) -> Unit,
    onRoleChange: (ChatMessage.Role) -> Unit,
    onAdd: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null,
    // ：主动发态复用输入行三参——默认值保回复态行为逐字不变
    showRoleChips: Boolean = true,
    showAddButton: Boolean = true,
    placeholderOverride: String? = null
) {
    val isEditing = editingIndex >= 0

    // 单行布局（需求#2）：她 | 我 | 想法 | 输入框(weight 1f) | 添加；主动发态 chips/添加钮隐藏（-⑥）
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md)
    ) {
        if (showRoleChips) {
            RoleChip("她", currentRole == ChatMessage.Role.HER) {
                onRoleChange(ChatMessage.Role.HER)
            }
            Spacer(Modifier.width(Spacing.sm))
            RoleChip("我", currentRole == ChatMessage.Role.ME) {
                onRoleChange(ChatMessage.Role.ME)
            }
            Spacer(Modifier.width(Spacing.sm))
            // ：想法 = 第三种消息（Role.IDEA）；VM 侧收口保证捕获链不受此选择影响
            RoleChip("想法", currentRole == ChatMessage.Role.IDEA) {
                onRoleChange(ChatMessage.Role.IDEA)
            }
            Spacer(Modifier.width(Spacing.sm))
        }

        // 输入框（DRY：共享 PanelTextInput；高度与 chips 一致（C1）；placeholder 随角色联动）
        PanelTextInput(
            value = draftText,
            onValueChange = onDraftChange,
            placeholder = placeholderOverride ?: when (currentRole) {
                ChatMessage.Role.HER -> "输入她说的话…"
                ChatMessage.Role.IDEA -> "输入你的想法…"
                else -> "输入你说的话…"
            },
            height = ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp,
            modifier = Modifier.weight(1f),
            focusRequester = focusRequester,
            onFocusChange = onFocusChange
        )

        Spacer(Modifier.width(Spacing.sm))

        // 添加按钮（C4：改 ➕ 图标，28dp 与 chips 同高；空输入置灰）；主动发态隐藏
        if (showAddButton) {
            val canAdd = draftText.isNotBlank()
            val (addInteraction, addScale) = rememberPressScale(0.92f, "addScale")
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = addScale; scaleY = addScale }
                    .clip(LoveBrainShape.md)
                    .background(if (canAdd) Primary else SurfaceInset)
                    .then(if (canAdd) Modifier.clickable(interactionSource = addInteraction, indication = null, onClick = onAdd) else Modifier)
                    .padding(Spacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (isEditing) "保存修改" else "添加",
                    tint = if (canAdd) Color.White else TextSecondary,
                    modifier = Modifier.size(ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp - 8.dp)
                )
            }
        }
    }
}

/**
 * 面板共享输入行（DRY）：单行输入框 + placeholder。
 * 回复输入、主动发一条草稿等面板内输入场景全部复用此组件。
 * （✕ 清空按钮已按用户要求移除：热区小且易误触）
 */
@Composable
fun PanelTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = AppDimens.INPUT_ROW_HEIGHT_DP.dp,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .heightIn(min = height)  // ：height 语义降为最小高度，防系统大字号截断
            .background(SurfaceCard, LoveBrainShape.md)
            .border(AppDimens.BORDER_WIDTH_DP.dp, if (value.isNotEmpty()) PrimarySubtle else Border, LoveBrainShape.md)
            .padding(horizontal = Spacing.lg)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = TextHint,
                style = AppTypography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        var tfModifier = Modifier
            .fillMaxWidth()
            .align(Alignment.CenterStart)
        if (focusRequester != null) tfModifier = tfModifier.focusRequester(focusRequester)
        if (onFocusChange != null) tfModifier = tfModifier.onFocusChanged { onFocusChange(it.isFocused) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            // ：裸字号走排版令牌（bodyMedium = 13sp）
            textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(Primary),
            modifier = tfModifier
        )
    }
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "roleChipScale")
    Box(
        modifier = Modifier
            .heightIn(min = ReplyDimens.ROLE_CHIP_HEIGHT_DP.dp)  // 
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.md)
            .background(if (selected) Primary else SurfaceInset, LoveBrainShape.md)
            .then(
                if (!selected) Modifier.border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
                else Modifier
            )
            .semantics { this.selected = selected }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            // C4：左右内边距收窄 12→9dp（主人原话：两边各减 1/8），省出的空间让给输入框
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextSecondary,
            style = AppTypography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
