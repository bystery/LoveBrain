package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.model.SchemeFeedback

/**
 * 方案卡默认态展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 默认态（Collapsed）：正文 + 操作行（复制/赞/踩）。
 * - 空回复：显示"本轮不适合"，不带操作行
 * - 非空：正文（可滚） + 右下角三颗操作
 *
 * 视觉与原 SchemeCard 内联实现完全一致。
 * 第一个内容 Box 占用 weight(1f) 撑开剩余空间（调用方传入 modifier 含 weight），
 * 后续 Spacer + 操作 Row 为兄弟节点，按原内联实现保持布局。
 */
@Composable
internal fun SchemeCollapsedBlock(
    isEmpty: Boolean,
    reply: String,
    bodyColor: Color,
    feedback: SchemeFeedback,
    onCopy: () -> Unit,
    onFeedback: (SchemeFeedback) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isEmpty) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "本轮不适合",
                color = TextHint,
                style = AppTypography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    } else {
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
    }

    Spacer(Modifier.height(Spacing.sm))

    // 操作行：右下角（空回复不显示操作按钮）
    //
    // ⚠ 旧读数留档（这三颗原来的形状现在归 `LbTextAction` 的图标档）：
    // 热区本机语义树实量 **20x20dp**（那句注释原先写"外扩至 28dp（触控下限友好）"，
    // 两头都是假的），而且**既没有角色也没有选中态**。无障碍那条要的是
    // "可交互控件说得清自己是什么、现在是什么状态"，而「赞/踩」被点过之后
    // 只有 `tint` 变了色——读屏用户听完那句"复制/赞/踩"之后，没有任何一处
    // 能知道这条方案已经表过态。现在两件事都由图标档一次给齐（热区见方 +
    // `Role.Button` + `selected`），字形仍是 13dp ⇒ 外观没动，
    // 动的只是"要点多准才算点到"。
    //
    // 为什么这一格当初不能只 `size(48)`：`requiredSize` 会把三颗硬塞成 144dp
    // 而**溢出**卡片，卡外那层 `clip(...)` 会把第一颗裁掉一截——热区看着够大，
    // 边上一指按不到，那是假修。真要 48 就得给卡片 48 的空间，所以那一格
    // 同时把 `CARD_WIDTH_DP` 从 158 抬到 164（放得下 3×48 + 左右各 8 内边距）。
    if (!isEmpty) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LbTextAction(
                iconRes = R.drawable.ic_copy,
                description = stringResource(R.string.panel_copy),
                tone = LbTextActionTone.RowSecondary,
                glyph = LbTextActionGlyph.Compact,
                onClick = onCopy
            )
            LbTextAction(
                iconRes = R.drawable.ic_thumb_up,
                description = stringResource(R.string.a11y_scheme_like),
                tone = if (feedback == SchemeFeedback.LIKED) {
                    LbTextActionTone.Accent
                } else {
                    LbTextActionTone.Muted
                },
                glyph = LbTextActionGlyph.Compact,
                onClick = { onFeedback(SchemeFeedback.LIKED) },
                selected = feedback == SchemeFeedback.LIKED
            )
            LbTextAction(
                iconRes = R.drawable.ic_thumb_down,
                description = stringResource(R.string.a11y_scheme_dislike),
                tone = if (feedback == SchemeFeedback.DISLIKED) {
                    LbTextActionTone.Destructive
                } else {
                    LbTextActionTone.Muted
                },
                glyph = LbTextActionGlyph.Compact,
                onClick = { onFeedback(SchemeFeedback.DISLIKED) },
                selected = feedback == SchemeFeedback.DISLIKED
            )
        }
    }
}
