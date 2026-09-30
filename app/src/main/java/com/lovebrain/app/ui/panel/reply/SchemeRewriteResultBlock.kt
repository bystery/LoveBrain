package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.*

/**
 * 方案卡改写结果展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 包含两个 presentation state 的内容层：
 * - RewriteError：错误消息 + "重试"
 * - RewriteDone：新正文 + "返回原版"/"用这版"
 *
 * 视觉与原 SchemeCard 内联实现完全一致；modifier（含 weight）由调用方传入。
 */

/**
 * 改写错误——替换内容显示错误消息 + "重试"。
 */
@Composable
internal fun SchemeRewriteErrorBlock(
    message: String,
    onClearRewriteState: () -> Unit,
    onToggleRewriteExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = AppTypography.labelSmall,
                color = Error,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "重试",
                style = AppTypography.labelSmall,
                color = PrimaryDark,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        onClearRewriteState()
                        onToggleRewriteExpand()
                    }
                ).padding(Spacing.xs)
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            )
        }
    }
}

/**
 * 改写成功——显示新正文 + "返回原版"/"用这版"按钮。
 *
 * 注意：第一个 Box 占用 weight(1f) 撑开剩余空间（调用方传入 modifier 含 weight），
 * 第二个 Row 是兄弟节点，按原内联实现保持 fillMaxWidth + End 对齐。
 */
@Composable
internal fun SchemeRewriteDoneBlock(
    reply: String,
    bodyColor: Color,
    onUndoRewrite: () -> Unit,
    onClearRewriteState: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = reply,
            color = bodyColor,
            style = AppTypography.bodyMedium,
            fontSize = SchemeTextDimens.BODY_FONT_SIZE,
            lineHeight = SchemeTextDimens.BODY_LINE_HEIGHT
        )
    }
    // 明确的"用这版"/"返回原版"按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "返回原版",
            style = AppTypography.labelSmall,
            color = TextHint,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onUndoRewrite
            ).padding(horizontal = Spacing.xs, vertical = Spacing.xs)
                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
        )
        Text(
            "用这版",
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClearRewriteState
            ).padding(horizontal = Spacing.xs, vertical = Spacing.xs)
                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
        )
    }
}
