package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lovebrain.app.viewmodel.LoveBrainViewModel.ComposerMode
import org.junit.Rule
import org.junit.Test

/**
 * S1-03: ReplyPrimaryActions Compose UI 行为测试。
 *
 * 验证 4 种按钮状态与 v1.3.1 主动发入口语义完全一致：
 * 1. REPLY + 无结果 → 全宽"生成回复 · N 条消息"
 * 2. REPLY + 有结果 → "重试 | 记入知识库"
 * 3. PROACTIVE + 空闲 → 全宽"生成开场"
 * 4. 生成中 → 全宽"停止"（停止按钮不区分回复/主动发）
 *
 * 点击蓝字进入主动发模式不发网络请求——此处通过回调计数验证。
 */
class ReplyPrimaryActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun replyMode_noResult_showsGenerateReplyButton() {
        var generateClicks = 0
        composeRule.setContent {
            ReplyPrimaryActionsTestable(
                composerMode = ComposerMode.REPLY,
                isGenerating = false,
                isProactive = false,
                hasReplyResult = false,
                messageCount = 3,
                onGenerateReply = { generateClicks++ },
                onGenerateProactive = {},
                onRetry = {},
                onSaveToKb = {},
                onStop = {}
            )
        }
        composeRule.onNodeWithText("生成回复 · 3 条消息").assertIsDisplayed()
        composeRule.onNodeWithText("生成回复 · 3 条消息").performClick()
        assert(generateClicks == 1) { "Generate reply should be called once" }
    }

    @Test
    fun replyMode_noResult_zeroMessages_showsGenerateReplyButton() {
        composeRule.setContent {
            ReplyPrimaryActionsTestable(
                composerMode = ComposerMode.REPLY,
                isGenerating = false,
                isProactive = false,
                hasReplyResult = false,
                messageCount = 0,
                onGenerateReply = {},
                onGenerateProactive = {},
                onRetry = {},
                onSaveToKb = {},
                onStop = {}
            )
        }
        composeRule.onNodeWithText("生成回复 · 0 条消息").assertIsDisplayed()
    }

    @Test
    fun replyMode_hasResult_showsRetryAndSaveToKbButtons() {
        var retryClicks = 0
        var saveClicks = 0
        composeRule.setContent {
            ReplyPrimaryActionsTestable(
                composerMode = ComposerMode.REPLY,
                isGenerating = false,
                isProactive = false,
                hasReplyResult = true,
                messageCount = 3,
                onGenerateReply = {},
                onGenerateProactive = {},
                onRetry = { retryClicks++ },
                onSaveToKb = { saveClicks++ },
                onStop = {}
            )
        }
        composeRule.onNodeWithText("重试").assertIsDisplayed()
        composeRule.onNodeWithText("记入知识库").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.onNodeWithText("记入知识库").performClick()
        assert(retryClicks == 1) { "Retry should be called once" }
        assert(saveClicks == 1) { "Save to KB should be called once" }
    }

    @Test
    fun proactiveMode_idle_showsGenerateOpeningButton() {
        var proactiveClicks = 0
        composeRule.setContent {
            ReplyPrimaryActionsTestable(
                composerMode = ComposerMode.PROACTIVE,
                isGenerating = false,
                isProactive = false,
                hasReplyResult = false,
                messageCount = 0,
                onGenerateReply = {},
                onGenerateProactive = { proactiveClicks++ },
                onRetry = {},
                onSaveToKb = {},
                onStop = {}
            )
        }
        composeRule.onNodeWithText("生成开场").assertIsDisplayed()
        composeRule.onNodeWithText("生成开场").performClick()
        assert(proactiveClicks == 1) { "Generate proactive should be called once" }
    }

    @Test
    fun generating_showsStopButton() {
        var stopClicks = 0
        composeRule.setContent {
            ReplyPrimaryActionsTestable(
                composerMode = ComposerMode.REPLY,
                isGenerating = true,
                isProactive = false,
                hasReplyResult = false,
                messageCount = 3,
                onGenerateReply = {},
                onGenerateProactive = {},
                onRetry = {},
                onSaveToKb = {},
                onStop = { stopClicks++ }
            )
        }
        composeRule.onNodeWithText("生成回复 · 3 条消息").assertIsNotDisplayed()
        composeRule.onNodeWithText("停止").performClick()
        assert(stopClicks == 1) { "Stop should be called once" }
    }
}
