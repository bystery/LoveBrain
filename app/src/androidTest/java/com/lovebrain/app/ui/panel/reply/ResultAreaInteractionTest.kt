package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.testing.MainChainHarness
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import com.lovebrain.app.testing.assertIsDisplayedDiagnosed
import com.lovebrain.app.R
import com.lovebrain.app.testing.UiText

/**
 * Compose UI interaction test — 真实交互路径覆盖。
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
        // 审计修复（编译健壮性）：不再直接 new ReplySchemes ——
        // 生产 com.lovebrain.app.model 包内现在有两个同名 ReplySchemes
        // （Models.kt 的四风格 DTO / GenerationEvents.kt 的 typed event），
        // 测试端不依赖其构造签名。
        // 这里要的是**四风格 + 方向列表齐全**的模型，所以必须把整段文本交给
        // 生产 parseReplyResponse。以前走的是 harness 的 parsedSuccess(raw)，那个 helper
        // 只往 recommended 一格塞正文、另三格和 directions 留空——于是"推荐回复内容"
        // 这个节点根本不存在（卡上是那整段 JSON 文本），三格用例在 CI 上全红。
        val raw = "{\"response\":{\"recommended\":\"推荐回复内容\",\"bad_boy\":\"清醒回复\"," +
            "\"playful\":\"俏皮回复\",\"warm\":\"温柔回复\"}," +
            "\"directions\":[\"F reply\",\"E reply\",\"X reply\",\"S reply\"]," +
            "\"analysis\":{\"topic_status\":\"same\",\"topic_label\":\"test\"}}"
        return GenerateResult.Success(MainChainHarness.parseProviderText(raw))
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
        composeRule.onNodeWithText("推荐回复内容").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
    }

    // ═══ 2. 风格/方向切换 ═══
    // 风格/方向切换器已从 ResultArea 退场（两组方案合成一条列表）。
    // 见 ResultArea.kt:250-252 "这里没有'两组各一排、点着切'那一档了"。
    // test #2 (styleToDirection_switchChangesDisplayedSchemes) 与 test #9
    // (directionMode_staysAfterRewriteUI) 随之删除——它们测的 UI 不存在了。

    // ═══ 3. Error 渲染 ═══
    // Error 那颗重试按钮已退场（见 ResultArea.kt:345-349 "撤掉的只是那颗重复动作"）。
    // test #3 (showsError_whenError) 随之删除——它断言的重试节点不存在了。

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
        // 这一句生产仍然是**内联中文**（`ResultArea:321` 的 `Text("还没有配置模型供应商")`，
        // 字面量预算记着这笔债）⇒ 英文模拟器上也画中文，锚点按字面量配得上。
        // ⚠ 等这句搬进资源，这里的锚点必须跟着换成 `UiText.current(...)`，否则又变成
        //   下一格"永远找不到节点"——和「去设置」那一发同一个成因。
        composeRule.onNodeWithText("还没有配置模型供应商").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText(UiText.current(R.string.provider_open_settings))
            .assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
    }

    // ═══ 5. `⋯` 菜单已退场 ═══
    // `⋯` 工具菜单触发器已从 ResultArea 退场（见 ResultArea.kt:112-114）。
    // test #5 (utilityMenu_canOpenAndDoesNotContainSaveToKb) 随之删除——它断言的 ⋯ 节点不存在了。

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
        composeRule.onAllNodesWithText("撤销").assertCountEquals(0)
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
        composeRule.onAllNodesWithText("已赞 0").assertCountEquals(0)
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
    // 风格/方向切换器已退场，这条测试随 #2/#9 一起删除（它们测的切换 UI 不存在了）。
}
