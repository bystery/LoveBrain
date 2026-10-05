package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbButtonHeightTier
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbButtonTone
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import com.lovebrain.app.model.ComposerMode

/**
 * ReplyPrimaryActions — 生产可测试组件（不再是 private）。
 *
 * 使用 ComposerMode 驱动的 4 种按钮状态，完全匹配 v1.3.1 主动发入口语义：
 * 1. REPLY 模式 + 无结果 → 全宽"生成回复 · N 条消息"（N=0 时显示"生成回复"并禁用）
 * 2. REPLY 模式 + 有结果 → "重试 | 记入知识库"
 * 3. PROACTIVE 模式 + 空闲 → 全宽"生成开场"
 * 4. 任意模式 + 生成中 → 唯一停止入口
 *
 * 这一屏**只画一颗主动作**（`LbPrimaryButton`），状态由这里决定、壳由组件保证：
 * 走组件的那几个分支各自交一个 [LbButtonState]，不由调用方拿 `mode` + `enabled` 两个旋钮凑。
 * 第 2 行那"两颗"是有结果的并列出口，仍共用同一颗按钮组件——它不是第二颗主动作，
 * 而是同一个主动作槽位在"有结果"时的两种说法。
 *
 * 高度按 [LbButtonHeightTier] 的两档走，页面自己不填 Dp：
 * 生成 / 生成中 / 停止（含 N=0 那颗灰着的）走 [LbButtonHeightTier.PanelPrimaryAction]，
 * 有结果时并排的「重试」「记入知识库」走 [LbButtonHeightTier.PanelResultActionPair]。
 *
 * 「仅看本轮」的可见开关住在输入选择行右侧（`ReplyInput`），点它切长期档。
 * 长按"本轮一次生成"的隐蔽入口已按 第10节第4条 删除——只留那颗可见开关一个入口。
 */

private object ReplyActionsDimens {
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
        // 生成中——回复或主动发都显示"停止"；点下去即停止，文案由 reply 层组
        isGenerating -> {
            LbPrimaryButton(
                state = LbButtonState.Loading,
                label = generatingLabel(),
                onClick = onStop,
                modifier = modifier.fillMaxWidth(),
                heightTier = LbButtonHeightTier.PanelPrimaryAction
            )
        }
        // 主动发生成中——简洁停止条（无计时那串字）
        isProactive -> {
            LbPrimaryButton(
                state = LbButtonState.Stop,
                label = stringResource(R.string.panel_stop),
                onClick = onStop,
                modifier = modifier.fillMaxWidth(),
                heightTier = LbButtonHeightTier.PanelPrimaryAction
            )
        }
        // PROACTIVE 模式 + 空闲 → 全宽"生成开场"
        composerMode == ComposerMode.PROACTIVE -> {
            LbPrimaryButton(
                state = LbButtonState.Idle,
                label = stringResource(R.string.panel_generate_opening),
                onClick = onGenerateProactive,
                modifier = modifier.fillMaxWidth(),
                tone = LbButtonTone.Deep,
                heightTier = LbButtonHeightTier.PanelPrimaryAction
            )
        }
        // REPLY 模式 + 有结果 → "重试 | 记入知识库"
        composerMode == ComposerMode.REPLY && hasReplyResult -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReplyActionsDimens.GENERATE_BUTTON_GAP_DP.dp)
            ) {
                LbPrimaryButton(
                    state = LbButtonState.Idle,
                    label = stringResource(R.string.panel_retry),
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    heightTier = LbButtonHeightTier.PanelResultActionPair
                )
                LbPrimaryButton(
                    state = LbButtonState.Idle,
                    label = stringResource(R.string.panel_save_to_kb),
                    onClick = onSaveToKb,
                    modifier = Modifier.weight(1f),
                    tone = LbButtonTone.Deep,
                    heightTier = LbButtonHeightTier.PanelResultActionPair
                )
            }
        }
        // REPLY 模式 + 无结果 → 全宽"生成回复 · N 条消息"；N=0 是**灰着不能点**，不是消失。
        else -> {
            if (messageCount > 0) {
                LbPrimaryButton(
                    state = LbButtonState.Idle,
                    label = stringResource(R.string.panel_generate_reply_with_count, messageCount),
                    onClick = onGenerateReply,
                    modifier = modifier.fillMaxWidth(),
                    heightTier = LbButtonHeightTier.PanelPrimaryAction
                )
            } else {
                LbPrimaryButton(
                    state = LbButtonState.Disabled,
                    label = stringResource(R.string.panel_generate_reply),
                    onClick = onGenerateReply,
                    modifier = modifier.fillMaxWidth(),
                    heightTier = LbButtonHeightTier.PanelPrimaryAction
                )
            }
        }
    }
}
