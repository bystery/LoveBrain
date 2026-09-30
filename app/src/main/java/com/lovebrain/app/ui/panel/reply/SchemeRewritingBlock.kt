package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.*

/**
 * 方案卡改写中展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 显示一个加载圈 + "正在改写..." + 右侧"取消"按钮。
 * 视觉与原 SchemeCard 内联实现完全一致。
 */
@Composable
internal fun SchemeRewritingBlock(
    onCancelRewrite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = Primary
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "正在改写...",
            style = AppTypography.labelSmall,
            color = PrimaryDark
        )
        Spacer(Modifier.weight(1f))
        Text(
            "取消",
            style = AppTypography.labelSmall,
            color = TextHint,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancelRewrite
            ).padding(Spacing.xs)
                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
        )
    }
}
