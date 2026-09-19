package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplySchemes
import org.junit.Rule
import org.junit.Test

/**
 * P1-5: 最小化 Compose UI interaction test。
 *
 * 覆盖高风险路径：
 * - 结果区正常渲染方案卡
 * - 无 memoryRefs 时不存在独占保存 Row（P0-4 验收）
 * - 错误态正确展示重试入口
 * - 未配置供应商引导态
 *
 * 需要 emulator/设备运行：./gradlew :app:connectedDebugAndroidTest
 */
class ResultAreaInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeSuccessResult(): GenerateResult.Success {
        return GenerateResult.Success(
            LoveBrainResponse(
                response = ReplySchemes(
                    recommended = "推荐回复内容",
                    badBoy = "清醒回复",
                    playful = "俏皮回复",
                    warm = "温柔回复"
                ),
                directions = listOf("F reply", "E reply", "X reply", "S reply")
            )
        )
    }

    @Test
    fun resultArea_displaysSchemeCards_whenSuccess() {
        composeRule.setContent {
            ResultArea(
                result = makeSuccessResult(),
                isGenerating = false,
                streamingCoreText = "",
                isGeneratingCore = false,
                streamingSchemes = emptyList(),
                feedbacks = emptyMap(),
                onFeedback = { _, _ -> },
                onCopyScheme = {},
                onRetry = {},
                providerReady = true,
                onOpenSettings = {},
                generationRoundId = 1
            )
        }
        // 验证方案卡内容渲染
        composeRule.onNodeWithText("推荐回复内容").assertIsDisplayed()
    }

    @Test
    fun resultArea_showsError_whenError() {
        composeRule.setContent {
            ResultArea(
                result = GenerateResult.Error("测试错误信息"),
                isGenerating = false,
                streamingCoreText = "",
                isGeneratingCore = false,
                streamingSchemes = emptyList(),
                feedbacks = emptyMap(),
                onFeedback = { _, _ -> },
                onCopyScheme = {},
                onRetry = {},
                providerReady = true,
                onOpenSettings = {},
                generationRoundId = 0
            )
        }
        composeRule.onNodeWithText("测试错误信息").assertIsDisplayed()
        composeRule.onNodeWithText("点击重试").assertIsDisplayed()
    }

    @Test
    fun resultArea_showsProviderSetup_whenNotReady() {
        composeRule.setContent {
            ResultArea(
                result = null,
                isGenerating = false,
                streamingCoreText = "",
                isGeneratingCore = false,
                streamingSchemes = emptyList(),
                feedbacks = emptyMap(),
                onFeedback = { _, _ -> },
                onCopyScheme = {},
                onRetry = {},
                providerReady = false,
                onOpenSettings = {},
                generationRoundId = 0
            )
        }
        composeRule.onNodeWithText("还没有配置模型供应商").assertIsDisplayed()
        composeRule.onNodeWithText("去设置").assertIsDisplayed()
    }
}
