package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.*

/**
 * 方案卡录音/识别中展示块——从 SchemeCard 抽离的纯展示子组件。
 *
 * 显示一个加载圈 + "正在录音..."/"识别中..." + "松手后用语音修改"。
 * 视觉与原 SchemeCard 内联实现完全一致。
 */
@Composable
internal fun SchemeRecordingBlock(
    isRecognizing: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Primary
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = if (isRecognizing) "识别中..." else "正在录音...",
                style = AppTypography.labelSmall,
                color = PrimaryDark
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "松手后用语音修改",
                style = AppTypography.labelSmall,
                color = TextHint
            )
        }
    }
}
