package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.PrimaryDark
import com.lovebrain.app.viewmodel.LoveBrainViewModel.ComposerMode

/**
 * ReplyPrimaryActions — 生产可测试组件（不再是 private）。
 *
 * 使用 ComposerMode 驱动的 4 种按钮状态，完全匹配 v1.3.1 主动发入口语义：
 * 1. REPLY 模式 + 无结果 → 全宽"生成回复 · N 条消息"（N=0 时显示"生成回复"并禁用）
 * 2. REPLY 模式 + 有结果 → "重试 | 记入知识库"
 * 3. PROACTIVE 模式 + 空闲 → 全宽"生成开场"
 * 4. 任意模式 + 生成中 → 全宽"停止"
 *
 * 删除 ReplyPrimaryActionsTestable 复制品，测试直接使用此生产组件。
 */

private object ReplyActionsDimens {
    /** 主操作按钮触摸区下限——旧值 40dp 达不到无障碍要求 */
    const val TRIO_HEIGHT_DP = 48
    const val GENERATE_BUTTON_GAP_DP = 8
}

@Composable
fun ReplyPrimaryActions(
    modifier: Modifier = Modifier,
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
        // 生成中——回复或主动发都显示"停止"
        isGenerating -> {
            GenerationActionButton(
                text = "",
                onClick = onStop,
                modifier = modifier.fillMaxWidth(),
                mode = ButtonMode.LOADING,
                heightDp = ReplyActionsDimens.TRIO_HEIGHT_DP
            )
        }
        // 主动发生成中——显示"停止"
        isProactive -> {
            GenerationActionButton(
                text = stringResource(R.string.panel_stop),
                onClick = onStop,
                modifier = modifier.fillMaxWidth(),
                mode = ButtonMode.STOP,
                heightDp = ReplyActionsDimens.TRIO_HEIGHT_DP
            )
        }
        // PROACTIVE 模式 + 空闲 → 全宽"生成开场"
        composerMode == ComposerMode.PROACTIVE -> {
            GenerationActionButton(
                text = stringResource(R.string.panel_generate_opening),
                onClick = onGenerateProactive,
                modifier = modifier.fillMaxWidth(),
                containerColor = PrimaryDark,
                heightDp = ReplyActionsDimens.TRIO_HEIGHT_DP
            )
        }
        // REPLY 模式 + 有结果 → "重试 | 记入知识库"
        composerMode == ComposerMode.REPLY && hasReplyResult -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReplyActionsDimens.GENERATE_BUTTON_GAP_DP.dp)
            ) {
                GenerationActionButton(
                    text = stringResource(R.string.panel_retry),
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    containerColor = Primary,
                    heightDp = ReplyActionsDimens.TRIO_HEIGHT_DP
                )
                GenerationActionButton(
                    text = stringResource(R.string.panel_save_to_kb),
                    onClick = onSaveToKb,
                    modifier = Modifier.weight(1f),
                    containerColor = PrimaryDark,
                    heightDp = ReplyActionsDimens.TRIO_HEIGHT_DP
                )
            }
        }
        // REPLY 模式 + 无结果 → 全宽"生成回复 · N 条消息"
        else -> {
            val replyEnabled = messageCount > 0
            GenerationActionButton(
                text = if (messageCount > 0)
                    stringResource(R.string.panel_generate_reply_with_count, messageCount)
                else stringResource(R.string.panel_generate_reply),
                onClick = onGenerateReply,
                modifier = modifier.fillMaxWidth(),
                enabled = replyEnabled,
                containerColor = Primary,
                heightDp = ReplyActionsDimens.TRIO_HEIGHT_DP
            )
        }
    }
}
