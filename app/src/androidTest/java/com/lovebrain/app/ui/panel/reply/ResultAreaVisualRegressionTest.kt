package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplySchemes
import org.junit.Rule
import org.junit.Test

/**
 * P0-9: 视觉回归 gate——默认 Success ResultArea 布局层级验证。
 *
 * 锁定默认 Success 态的布局不变量：
 * - 四张风格卡渲染
 * - 风格/方向入口存在
 * - `⋯` overlay 存在
 * - 不额外出现保存工具 Row（"记入知识库"不在默认态直接显示，只在 `⋯` 菜单内）
 * - "本轮参考"不存在（无 memoryRefs）
 *
 * 重点保护布局层级和卡片高度，不是重新锁 Color/Dimens 常量。
 * Color.kt / Dimens.kt / Theme.kt / Type.kt 与 v1.3.1 SHA 完全一致，禁止修改。
 */
class ResultAreaVisualRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeSuccessResult(): GenerateResult.Success {
        return GenerateResult.Success(
            LoveBrainResponse(
                response = ReplySchemes(
                    recommended = "推荐回复内容",
                    badBoy = "清醒回复内容",
                    playful = "俏皮回复内容",
                    warm = "温柔回复内容"
                ),
                directions = listOf("F方向内容", "E方向内容", "X方向内容", "S方向内容")
            )
        )
    }

    @Test
    fun defaultSuccessResultArea_hasFourStyleCards() {
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
        // 四张风格卡内容全部渲染
        composeRule.onNodeWithText("推荐回复内容").assertIsDisplayed()
        composeRule.onNodeWithText("清醒回复内容").assertIsDisplayed()
        composeRule.onNodeWithText("俏皮回复内容").assertIsDisplayed()
        composeRule.onNodeWithText("温柔回复内容").assertIsDisplayed()
    }

    @Test
    fun defaultSuccessResultArea_hasStyleDirectionSwitcher() {
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
        // 风格/方向入口存在
        composeRule.onNodeWithText("风格").assertIsDisplayed()
        composeRule.onNodeWithText("方向").assertIsDisplayed()
    }

    @Test
    fun defaultSuccessResultArea_hasUtilityOverlayTrigger() {
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
        // `⋯` overlay 存在
        composeRule.onNodeWithText("⋯").assertIsDisplayed()
    }

    @Test
    fun defaultSuccessResultArea_doesNotShowSaveToKbButtonDirectly() {
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
        // "记入知识库" 不在默认态直接显示——只在 `⋯` 菜单打开后才出现
        val saveNodes = composeRule.onAllNodesWithText("记入知识库")
        assert(saveNodes.fetchSemanticsNodes().isEmpty()) {
            "Save to KB button should not be directly visible in default state — only in ⋯ menu"
        }
    }

    @Test
    fun defaultSuccessResultArea_doesNotShowMemoryRefSection() {
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
        // 无 memoryRefs → "本轮参考" 不存在
        val refNodes = composeRule.onAllNodesWithText("本轮参考")
        assert(refNodes.fetchSemanticsNodes().isEmpty()) {
            "Memory ref section should not exist when memoryRefs is empty"
        }
    }
}
