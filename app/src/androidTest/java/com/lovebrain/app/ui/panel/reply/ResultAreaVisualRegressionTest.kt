package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplySchemes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * P0-9: 视觉回归 gate——默认 Success ResultArea 布局层级验证。
 *
 * 锁定默认 Success 态的布局不变量：
 * - 首张风格卡默认可见；LazyRow 可横向滚动到后续卡
 * - 风格/方向入口存在
 * - `⋯` overlay 存在
 * - 不额外出现保存工具 Row（"记入知识库"不在默认态直接显示，只在 `⋯` 菜单内）
 * - "本轮参考"不存在（无 memoryRefs）
 *
 * 重点保护布局层级和卡片高度，不是重新锁 Color/Dimens 常量。
 * Color.kt / Dimens.kt / Theme.kt / Type.kt 与 v1.3.1 SHA 完全一致，禁止修改。
 *
 * 注意：四张方案卡在 LazyRow 中——窄屏 emulator 无法同时显示四张。
 * 不通过缩小卡片或改 LazyRow 强行让四卡同时可见。
 * 改为：首张默认可见，滚动后验证后续卡可见。
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

    private fun setupResultArea() {
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
    }

    // ═══ 1. 首张风格卡默认可见，LazyRow 可滚动到后续卡 ═══

    @Test
    fun defaultSuccessResultArea_firstCardVisible_andScrollRevealsOthers() {
        setupResultArea()
        // 首张卡默认可见
        composeRule.onNodeWithText("推荐回复内容").assertIsDisplayed()

        // 滚动 LazyRow 到第二张
        composeRule.onNodeWithText("推荐回复内容").performScrollToNode(hasText("清醒回复内容"))
        composeRule.onNodeWithText("清醒回复内容").assertIsDisplayed()

        // 滚动到第三张
        composeRule.onNodeWithText("清醒回复内容").performScrollToNode(hasText("俏皮回复内容"))
        composeRule.onNodeWithText("俏皮回复内容").assertIsDisplayed()

        // 滚动到第四张
        composeRule.onNodeWithText("俏皮回复内容").performScrollToNode(hasText("温柔回复内容"))
        composeRule.onNodeWithText("温柔回复内容").assertIsDisplayed()
    }

    // ═══ 2. 四张风格卡可通过滚动访问（不要求同时 composition） ═══
    // LazyRow 的 off-screen item 不保证已在 semantics tree 中。
    // 数据层验证四个 Scheme 存在应放在 JVM/data test，不该要求四张同时 composition。
    // 这里通过滚动逐个验证每张卡可见。

    @Test
    fun defaultSuccessResultArea_allFourCardsAccessibleViaScroll() {
        setupResultArea()
        // 首张默认可见
        composeRule.onNodeWithText("推荐回复内容").assertIsDisplayed()
        // 滚动到第二张
        composeRule.onNodeWithText("推荐回复内容").performScrollToNode(hasText("清醒回复内容"))
        composeRule.onNodeWithText("清醒回复内容").assertIsDisplayed()
        // 滚动到第三张
        composeRule.onNodeWithText("清醒回复内容").performScrollToNode(hasText("俏皮回复内容"))
        composeRule.onNodeWithText("俏皮回复内容").assertIsDisplayed()
        // 滚动到第四张
        composeRule.onNodeWithText("俏皮回复内容").performScrollToNode(hasText("温柔回复内容"))
        composeRule.onNodeWithText("温柔回复内容").assertIsDisplayed()
    }

    // ═══ 3. 风格/方向入口存在 ═══

    @Test
    fun defaultSuccessResultArea_hasStyleDirectionSwitcher() {
        setupResultArea()
        composeRule.onNodeWithText("风格").assertIsDisplayed()
        composeRule.onNodeWithText("方向").assertIsDisplayed()
    }

    // ═══ 4. `⋯` overlay 存在 ═══

    @Test
    fun defaultSuccessResultArea_hasUtilityOverlayTrigger() {
        setupResultArea()
        composeRule.onNodeWithText("⋯").assertIsDisplayed()
    }

    // ═══ 5. "记入知识库"不在默认态直接显示 ═══

    @Test
    fun defaultSuccessResultArea_doesNotShowSaveToKbButtonDirectly() {
        setupResultArea()
        val saveNodes = composeRule.onAllNodesWithText("记入知识库")
        assert(saveNodes.fetchSemanticsNodes().isEmpty()) {
            "Save to KB button should not be directly visible in default state — only in ⋯ menu"
        }
    }

    // ═══ 6. 无 memoryRefs 时"本轮参考"不存在 ═══

    @Test
    fun defaultSuccessResultArea_doesNotShowMemoryRefSection() {
        setupResultArea()
        val refNodes = composeRule.onAllNodesWithText("本轮参考")
        assert(refNodes.fetchSemanticsNodes().isEmpty()) {
            "Memory ref section should not exist when memoryRefs is empty"
        }
    }
}
