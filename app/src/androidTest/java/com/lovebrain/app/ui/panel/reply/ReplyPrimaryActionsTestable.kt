package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.PrimaryDark
import com.lovebrain.app.viewmodel.LoveBrainViewModel.ComposerMode

/**
 * S1-03: ReplyPrimaryActionsTestable — 测试用包装，直接使用 GenerationActionButton。
 * 与 LoveBrainPanelScreen 中的 ReplyPrimaryActions 逻辑完全一致，
 * 验证 4 种按钮状态的可点击性和回调。
 */

private object TestPanelDimens {
    const val TRIO_HEIGHT_DP = 40
    const val GENERATE_BUTTON_GAP_DP = 8
}

@Composable
internal fun ReplyPrimaryActionsTestable(
    composerMode: ComposerMode,
    isGenerating: Boolean,
    isProactive: Boolean,
    hasReplyResult: Boolean,
    messageCount: Int,
    onGenerateReply: () -> Unit,
    onGenerateProactive: () -> Unit,
    onRetry: () -> Unit,
    onSaveToKb: () -> Unit,
    onStop: () -> Unit
) {
    when {
        isGenerating -> {
            GenerationActionButton(
                text = "",
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                mode = ButtonMode.LOADING,
                heightDp = TestPanelDimens.TRIO_HEIGHT_DP
            )
        }
        isProactive -> {
            GenerationActionButton(
                text = "停止",
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                mode = ButtonMode.STOP,
                heightDp = TestPanelDimens.TRIO_HEIGHT_DP
            )
        }
        composerMode == ComposerMode.PROACTIVE -> {
            GenerationActionButton(
                text = "生成开场",
                onClick = onGenerateProactive,
                modifier = Modifier.fillMaxWidth(),
                containerColor = PrimaryDark,
                heightDp = TestPanelDimens.TRIO_HEIGHT_DP
            )
        }
        composerMode == ComposerMode.REPLY && hasReplyResult -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TestPanelDimens.GENERATE_BUTTON_GAP_DP.dp)
            ) {
                GenerationActionButton(
                    text = "重试",
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    containerColor = Primary,
                    heightDp = TestPanelDimens.TRIO_HEIGHT_DP
                )
                GenerationActionButton(
                    text = "记入知识库",
                    onClick = onSaveToKb,
                    modifier = Modifier.weight(1f),
                    containerColor = PrimaryDark,
                    heightDp = TestPanelDimens.TRIO_HEIGHT_DP
                )
            }
        }
        else -> {
            GenerationActionButton(
                text = "生成回复 · $messageCount 条消息",
                onClick = onGenerateReply,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Primary,
                heightDp = TestPanelDimens.TRIO_HEIGHT_DP
            )
        }
    }
}

