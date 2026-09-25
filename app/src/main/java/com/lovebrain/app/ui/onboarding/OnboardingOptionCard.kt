package com.lovebrain.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.domain.SelectionMode
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary

/** 问卷选项卡尺寸令牌 */
private object OnboardingDimens {
    const val OPTION_HEIGHT_DP = 68        // 标准双列卡片高度
    const val OPTION_HEIGHT_MIN_DP = 56    // 单列最小高度（大字体自适应）
    const val OPTION_INDICATOR_DP = 20     // 左侧选择指示符尺寸
    const val OPTION_GAP_DP = 12           // 卡片间距
    const val OPTION_RADIUS_DP = 12        // 卡片圆角
    const val OPTION_BORDER_WIDTH_SELECTED_DP = 2 // 选中描边宽度
    const val SINGLE_COL_FONT_SCALE_THRESHOLD = 1.3f // fontScale 超过此值切单列
}

/**
 * 判断当前是否应使用单列布局（大字体 / 窄屏自适应）。
 */
@Composable
fun shouldUseSingleColumn(): Boolean {
    val fontScale = LocalDensity.current.fontScale
    return fontScale >= OnboardingDimens.SINGLE_COL_FONT_SCALE_THRESHOLD
}

/**
 * 问卷选择卡——纯净组件，只负责显示 / 选中状态 / 点击。
 *
 * 设计原则：
 * - 固定 68dp 高（双列），大字体时自动切单列并允许自然增长
 * - 左对齐文案 + 左侧选择指示符（Radio 圆点 / Checkbox ✓）
 * - 未选：白底 + 1dp 灰描边；选中：PrimaryLight 淡底 + 1.5dp Primary 描边
 * - 无阴影，按压 scale 0.98，150ms 过渡
 * - maxLines = 2，溢出 ellipsis
 *
 * @param text 选项文案
 * @param selected 是否选中
 * @param selectionMode SINGLE → Radio 风格；MULTIPLE → Checkbox 风格
 * @param enabled 是否可交互
 * @param onClick 点击回调
 * @param modifier 外部布局约束（weight / fillMaxWidth 等）
 * @param useSingleColumn 是否使用单列布局（高度自适应）
 */
@Composable
fun OnboardingOptionCard(
    text: String,
    selected: Boolean,
    selectionMode: SelectionMode,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useSingleColumn: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.98f else 1f,
        label = "obOptionScale"
    )

    val bgColor by animateColorAsState(
        if (selected) PrimaryLight else SurfaceCard,
        label = "obOptionBg"
    )
    val borderColor by animateColorAsState(
        if (selected) Primary else Border,
        label = "obOptionBorder"
    )
    val borderWidth = if (selected) OnboardingDimens.OPTION_BORDER_WIDTH_SELECTED_DP.dp
                      else AppDimens.BORDER_WIDTH_DP.dp

    val cardModifier = if (useSingleColumn) {
        modifier
            .fillMaxWidth()
            .heightIn(min = OnboardingDimens.OPTION_HEIGHT_MIN_DP.dp)
    } else {
        modifier.height(OnboardingDimens.OPTION_HEIGHT_DP.dp)
    }

    Box(
        modifier = cardModifier
            .clip(LoveBrainShape.md)
            .background(bgColor)
            .border(borderWidth, borderColor, LoveBrainShape.md)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                this.selected = selected
                this.role = if (selectionMode == SelectionMode.MULTIPLE)
                    Role.Checkbox else Role.RadioButton
            }
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 左侧选择指示符
            SelectionIndicator(
                selected = selected,
                selectionMode = selectionMode,
                enabled = enabled
            )
            // 文案
            Text(
                text = text,
                style = AppTypography.bodyMedium,
                color = when {
                    !enabled -> TextHint
                    selected -> PrimaryDark
                    else -> TextPrimary
                },
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 左侧选择指示符：
 * - SINGLE：圆点（选中实心 Primary，未选空心 Border）
 * - MULTIPLE：圆角方框 + ✓（选中 Primary 填充 + 白色 ✓，未选空心 Border）
 */
@Composable
private fun SelectionIndicator(
    selected: Boolean,
    selectionMode: SelectionMode,
    enabled: Boolean
) {
    val size = OnboardingDimens.OPTION_INDICATOR_DP.dp
    val tint = if (!enabled) TextHint else if (selected) Primary else Border

    when (selectionMode) {
        SelectionMode.SINGLE -> {
            // Radio 风格：外圈 + 内圈
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(AppDimens.BORDER_WIDTH_DP.dp, tint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(size * 0.5f)
                            .clip(CircleShape)
                            .background(if (!enabled) TextHint else Primary)
                    )
                }
            }
        }
        SelectionMode.MULTIPLE -> {
            // Checkbox 风格：圆角方框 + ✓
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(LoveBrainShape.sm)
                    .background(if (selected) Primary else Color.Transparent)
                    .border(
                        if (selected) 0.dp else AppDimens.BORDER_WIDTH_DP.dp,
                        tint,
                        LoveBrainShape.sm
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text(
                        "✓",
                        style = AppTypography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
