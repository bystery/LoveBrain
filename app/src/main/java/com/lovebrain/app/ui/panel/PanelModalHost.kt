package com.lovebrain.app.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*

/**
 * F01: 面板内统一弹层宿主——在既有 Overlay 根容器中渲染弹层内容。
 *
 * 替代 AlertDialog：Service 宿主中使用 AlertDialog 会因缺少合适的
 * 应用窗口 token 而抛出 WindowManager.BadTokenException。
 *
 * 设计要点：
 * - 全屏半透明遮罩（点击关闭）
 * - 居中卡片容器（圆角、边框、背景一致）
 * - 内容触摸拦截（点击卡片内部不关闭）
 * - 统一的返回/取消交互
 * - 键盘弹出时按钮仍可操作（可滚动）
 *
 * 不新建 Activity Dialog 窗口——所有内容在 ComposeView composition 中渲染。
 */
@Composable
fun PanelModalHost(
    onDismiss: () -> Unit,
    dismissable: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = dismissable,
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .clip(LoveBrainShape.lg)
                .background(SurfaceCard)
                .border(1.dp, Border, LoveBrainShape.lg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* 拦截——点击卡片内部不关闭 */ }
                )
                .padding(Spacing.lg)
        ) {
            content()
        }
    }
}

/**
 * F01: 面板内弹层标题组件——统一样式。
 */
@Composable
fun PanelModalTitle(text: String) {
    Text(
        text = text,
        style = AppTypography.titleMedium,
        color = TextPrimary,
        fontWeight = FontWeight.Bold
    )
}

/**
 * F01: 面板内弹层操作行——保存/取消按钮。
 * 统一的视觉和按压反馈。
 */
@Composable
fun PanelModalActions(
    confirmLabel: String = "保存",
    dismissLabel: String = "取消",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (dismissInteraction, dismissScale) = rememberPressScale(0.96f, "panelModalDismissScale")
        Text(
            text = dismissLabel,
            style = AppTypography.labelLarge,
            color = if (confirmEnabled) TextSecondary else TextHint.copy(alpha = 0.5f),
            modifier = Modifier
                .graphicsLayer { scaleX = dismissScale; scaleY = dismissScale }
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    enabled = confirmEnabled,
                    onClick = onDismiss
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        )
        val (confirmInteraction, confirmScale) = rememberPressScale(0.96f, "panelModalConfirmScale")
        Text(
            text = confirmLabel,
            style = AppTypography.labelLarge,
            color = if (confirmEnabled) Color.White else TextHint,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .graphicsLayer { scaleX = confirmScale; scaleY = confirmScale }
                .clip(LoveBrainShape.sm)
                .background(if (confirmEnabled) Primary else SurfaceInset, LoveBrainShape.sm)
                .clickable(
                    interactionSource = confirmInteraction,
                    indication = null,
                    enabled = confirmEnabled,
                    onClick = onConfirm
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        )
    }
}
