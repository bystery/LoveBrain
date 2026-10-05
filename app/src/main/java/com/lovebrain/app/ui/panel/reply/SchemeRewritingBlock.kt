package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.*

/**
 * 方案卡"正在调整"展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 判据是**请求在途时不许提前替换正文**：旧正文继续占着卡片那块 `weight(1f)` 的可见区、
 * 仍可内部滚动，进度与停止只挤在底部那一行里。所以这一档看到的是"这一版还是原来那条"，
 * 而不是一个转圈占位盒。
 *
 * [onCancelRewrite] 就是那一句"停止"：它停的是这一张卡在跑的那一次请求
 * （多张卡可以同时停在展开态，但同一时刻仍只有一个改写请求在跑——卡片这里不新增并发）。
 * modifier（含 weight）由调用方传入。
 */
@Composable
internal fun SchemeRewritingBlock(
    reply: String,
    bodyColor: Color,
    onCancelRewrite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = reply,
                color = bodyColor,
                style = AppTypography.bodyMedium,
                fontSize = SchemeTextDimens.BODY_FONT_SIZE,
                lineHeight = SchemeTextDimens.BODY_LINE_HEIGHT,
                maxLines = 60,
                overflow = TextOverflow.Clip
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = Primary
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "正在调整",
                style = AppTypography.labelSmall,
                color = PrimaryDark,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(Spacing.xs))
            // 停止：卡内那一族的紧凑档，不占一整行 48dp
            Text(
                "停止",
                style = AppTypography.labelSmall,
                color = TextHint,
                maxLines = 1,
                modifier = Modifier
                    .clip(LoveBrainShape.sm)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onCancelRewrite
                    )
                    .size(SchemeCardDimens.ACTION_BOX_DP.dp),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
