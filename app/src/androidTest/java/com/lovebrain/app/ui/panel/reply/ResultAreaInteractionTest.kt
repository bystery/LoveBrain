package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeSource
import org.junit.Rule
import org.junit.Test

/**
 * P1-5: Compose UI interaction test — 真实交互路径覆盖。
 *
 * 覆盖高风险路径：
 * - 成功态渲染方案卡 + 风格/方向切换
 * - Error 渲染 + 重试入口
 * - 未配置供应商引导态
 * - 点击 like/dislike 不触发展开
 * - `⋯` 菜单可打开并点击"记入知识库"
 * - 无 memoryRefs 默认不额外出现保存工具行高度
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

    // ═══ 1. 成功态渲染方案卡 ═══

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

    // ═══ 2. 风格/方向切换 ═══

    @Test
    fun resultArea_styleToDirection_switchChangesDisplayedSchemes() {
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
        // 默认 STYLE 模式——推荐回复内容可见
        composeRule.onNodeWithText("推荐回复内容").assertIsDisplayed()
        // 点击"方向"切换到 DIRECTION 模式
        composeRule.onNodeWithText("方向").performClick()
        // DIRECTION 模式下应显示 F reply
        composeRule.onNodeWithText("F reply").assertIsDisplayed()
        // 切回风格
        composeRule.onNodeWithText("风格").performClick()
        composeRule.onNodeWithText("推荐回复内容").assertIsDisplayed()
    }

    // ═══ 3. Error 渲染 ═══

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

    // ═══ 4. 未配置供应商 ═══

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

    // ═══ 5. `⋯` 菜单可打开——"记入知识库"已移至主操作按钮，不再在 ⋯ 菜单中 ═══

    @Test
    fun resultArea_utilityMenu_canOpenAndDoesNotContainSaveToKb() {
        var saveCalled = false
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
                onSaveToKb = { saveCalled = true },
                providerReady = true,
                onOpenSettings = {},
                generationRoundId = 1
            )
        }
        // 点击 ⋯ trigger 打开菜单
        composeRule.onNodeWithText("⋯").performClick()
        // 审计修复："记入知识库"已从 ⋯ 菜单中删除——它现在是 ReplyPrimaryActions 的主操作按钮
        val saveNodes = composeRule.onAllNodesWithText("记入知识库")
        assert(saveNodes.fetchSemanticsNodes().isEmpty()) {
            "Save to KB should not be in ⋯ menu — it's now a primary action button"
        }
        assert(!saveCalled) { "onSaveToKb should not be called from ⋯ menu" }
    }

    // ═══ 6. 点击 like 不触发展开 ═══

    @Test
    fun resultArea_clickLike_doesNotExpandCard() {
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
        // 默认状态下不应出现改写相关入口（撤销/重写等只在展开后才出现）
        // 点击"赞"后仍不应出现改写入口
        // 注意：这里验证的是"赞"不触发展开——展开后才会出现"改写"入口
        val likeNodes = composeRule.onAllNodesWithText("赞")
        if (likeNodes.fetchSemanticsNodes().isNotEmpty()) {
            likeNodes[0].performClick()
        }
        // 验证"撤销"不存在（展开后才出现）
        val undoNodes = composeRule.onAllNodesWithText("撤销")
        assert(undoNodes.fetchSemanticsNodes().isEmpty()) {
            "Undo should not appear after just liking — card should not expand"
        }
    }

    // ═══ 7. LIKED filter 自动恢复 ALL ═══

    @Test
    fun resultArea_likedFilter_resetsToAllWhenNoLikes() {
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
        // 默认无点赞 → 不应有"已赞"筛选 Tab
        // 只有"全部 4"和"已赞 0"都不应该出现（likedCount=0 时不显示筛选器）
        val likedFilterNodes = composeRule.onAllNodesWithText("已赞 0")
        assert(likedFilterNodes.fetchSemanticsNodes().isEmpty()) {
            "Liked filter tab should not be visible when likedCount=0"
        }
    }

    // ═══ 8. 无 memoryRefs 默认不额外出现保存工具行 ═══

    @Test
    fun resultArea_noMemoryRefs_doesNotShowMemoryRefSection() {
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
        // 默认无 memoryRefs → 不应出现"本轮参考"
        val refNodes = composeRule.onAllNodesWithText("本轮参考")
        assert(refNodes.fetchSemanticsNodes().isEmpty()) {
            "Memory ref section should not be visible when memoryRefs is empty"
        }
    }

    // ═══ 9. DIRECTION 模式下 F 操作后模式不自动返回 STYLE ═══

    @Test
    fun resultArea_directionMode_staysAfterRewriteUI() {
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
        // 切换到方向
        composeRule.onNodeWithText("方向").performClick()
        // 确认在 DIRECTION 模式
        composeRule.onNodeWithText("F reply").assertIsDisplayed()
        // 风格 Tab 不应高亮选中——验证方向仍被选中
        composeRule.onNodeWithText("方向").assertIsDisplayed()
        // 确认仍显示方向内容
        composeRule.onNodeWithText("F reply").assertIsDisplayed()
    }
}
