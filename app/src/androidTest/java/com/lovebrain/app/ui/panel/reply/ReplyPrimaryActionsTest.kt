package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.lovebrain.app.viewmodel.LoveBrainViewModel.ComposerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import com.lovebrain.app.testing.assertIsDisplayedDiagnosed

/**
 * S1-01 / S1-03（审计 §2、§5、§8.1 第 3 条）：生产 [ReplyPrimaryActions] 的
 * 4 种按钮状态 + 停止动作 + 零消息禁用态行为测试。
 *
 * 本轮针对审计的三处修订：
 * 1. 零消息用例不再只看文字：必须断言按钮真的 disabled（禁用态可见性 + 点击不可触发回调）。
 * 2. 全部改用 JUnit / Compose 断言，不用裸 Kotlin `assert(...)`
 *    —— instrumentation 环境不保证开启 JVM `-ea`，裸 assert 会静默变成永真断言。
 * 3. 生成中的「停止」按生产真实文案 + semantics 定位：
 *    LOADING 模式渲染的是「分析对话 · Ns  点击停止」（见 GenerationActionButton.LOADING 分支），
 *    裸「停止」只在 isProactive 停止态出现。旧用例 onNodeWithText("停止") 因此必挂。
 *
 * 生产无 testTag 可用（app/src/main 不在本任务改动范围），
 * 故用语义定位：可见文案正则 + hasClickAction() 锚定真正接点击的节点。
 */
class ReplyPrimaryActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 生成中 LOADING 条的真实文案。
     * 生产 GenerationActionButton.kt:105-115 渲染 "$phase · ${elapsedSec}s  点击停止"，
     * phase 随秒数切换（分析对话/生成方案/深度分析），所以：
     * Compose 1.6.8 的 ui-test 没有 Regex 版 finder，先用恒定后缀「点击停止」定位节点，
     * 再取该节点 semantics 文本做整串正则校验（既不写死秒数，也不放过文案漂移）。
     */
    private val loadingStopSuffix = "点击停止"
    private val loadingStopText = Regex("""(分析对话|生成方案|深度分析) · \d+s\s+点击停止""")

    /** 取承载「点击停止」的那条文案的完整语义文本 */
    private fun loadingStopBarText(): String {
        val node = composeRule
            .onNodeWithText(loadingStopSuffix, substring = true)
            .fetchSemanticsNode("未找到生成中的停止条")
        return node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()
    }

    private fun setActions(
        composerMode: ComposerMode,
        isGenerating: Boolean = false,
        isProactive: Boolean = false,
        hasReplyResult: Boolean = false,
        messageCount: Int = 0,
        draftText: String = "",
        onGenerateReply: () -> Unit = {},
        onGenerateProactive: () -> Unit = {},
        onRetry: () -> Unit = {},
        onSaveToKb: () -> Unit = {},
        onStop: () -> Unit = {}
    ) {
        composeRule.setContent {
            ReplyPrimaryActions(
                composerMode = composerMode,
                isGenerating = isGenerating,
                isProactive = isProactive,
                hasReplyResult = hasReplyResult,
                messageCount = messageCount,
                onGenerateReply = onGenerateReply,
                onGenerateProactive = onGenerateProactive,
                onRetry = onRetry,
                onSaveToKb = onSaveToKb,
                onStop = onStop
            )
        }
    }

    // ═══════════ 1. REPLY + 无结果 ═══════════

    @Test
    fun replyMode_noResult_showsGenerateReplyWithMessageCountAndCallsOnce() {
        val generateClicks = AtomicInteger(0)
        setActions(
            composerMode = ComposerMode.REPLY,
            messageCount = 3,
            onGenerateReply = { generateClicks.incrementAndGet() }
        )
        composeRule.onNodeWithText("生成回复 · 3条消息").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText("生成回复 · 3条消息").performClick()
        // 审计修订：JUnit 断言，不用裸 assert()
        assertEquals("生成回复回调应恰好触发 1 次", 1, generateClicks.get())
    }

    /**
     * 审计 §2 明确要求：「ReplyPrimaryActionsTest 的零消息用例只检查文字，
     * 没有 assertIsNotEnabled()」——本用例补上该断言。
     *
     * ⚠ 预期红：生产 GenerationActionButton 的禁用分支（GenerationActionButton.kt:132-151）
     * 只是「不挂 clickable」+ 换底色/文字色，从未写 `SemanticsProperties.Disabled`
     * （也没有 role / stateDescription）。所以禁用态对 TalkBack 与 Compose 断言都不可见，
     * assertIsNotEnabled() 拿不到 Disabled 属性。
     * 修复属 app/src/main 范围（本任务禁止改生产码）：在该 Box 上补
     * `.semantics { disabled = true }`（或改用 `clickable(enabled = replyEnabled)`），
     * 之后本用例即转绿。行为层面的禁用证据见
     * [replyMode_zeroMessages_noClickActionExistsAnywhere]，那条现在是绿的。
     */
    @Test
    fun replyMode_noResult_zeroMessages_buttonLabelHasNoCountAndIsNotEnabled() {
        val generateClicks = AtomicInteger(0)
        // draftText 传非空：审计 §2 指出生产 draftText 参数未被使用，
        // 这里把它钉成「草稿不得改变零消息禁用态」，防止死参数被顺手接成隐式启用
        setActions(
            composerMode = ComposerMode.REPLY,
            messageCount = 0,
            draftText = "草稿里的只言片语",
            onGenerateReply = { generateClicks.incrementAndGet() }
        )
        // 0 条消息：无计数后缀
        composeRule.onNodeWithText("生成回复").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText("生成回复 · 0条消息").assertIsNotDisplayed()
        composeRule.onNodeWithText("生成回复").assertIsNotEnabled()
    }

    /**
     * 禁用态的「行为」证据：整棵子树不存在任何可点击语义节点，
     * 所以即使点击也发不出 onGenerateReply —— 与 [replyMode_noResult_zeroMessages_buttonLabelHasNoCountAndIsNotEnabled]
     * 的语义可见性断言互补（后者当前为红，见其 KDoc）。
     */
    @Test
    fun replyMode_zeroMessages_noClickActionExistsAnywhere() {
        val generateClicks = AtomicInteger(0)
        setActions(
            composerMode = ComposerMode.REPLY,
            messageCount = 0,
            onGenerateReply = { generateClicks.incrementAndGet() }
        )
        composeRule.onNodeWithText("生成回复").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        assertEquals("零消息时不得有任何生成回调", 0, generateClicks.get())
    }

    // ═══════════ 2. REPLY + 有结果 ═══════════

    @Test
    fun replyMode_hasResult_showsRetryAndSaveToKbAndEachFiresOnce() {
        val retryClicks = AtomicInteger(0)
        val saveClicks = AtomicInteger(0)
        setActions(
            composerMode = ComposerMode.REPLY,
            hasReplyResult = true,
            messageCount = 3,
            onRetry = { retryClicks.incrementAndGet() },
            onSaveToKb = { saveClicks.incrementAndGet() }
        )
        composeRule.onNodeWithText("重试").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText("记入知识库").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        // 有结果时不得再出现「生成回复」主按钮（主动发/生成入口不能被挤掉）
        composeRule.onNodeWithText("生成回复 · 3条消息").assertIsNotDisplayed()

        composeRule.onNodeWithText("重试").performClick()
        composeRule.onNodeWithText("记入知识库").performClick()

        assertEquals("重试点击应恰好 1 次", 1, retryClicks.get())
        assertEquals("记入知识库点击应恰好 1 次", 1, saveClicks.get())
    }

    // ═══════════ 3. PROACTIVE + 空闲 ═══════════

    @Test
    fun proactiveMode_idle_showsGenerateOpeningAndCallsProactiveOnceNotReply() {
        val proactiveClicks = AtomicInteger(0)
        val replyClicks = AtomicInteger(0)
        setActions(
            composerMode = ComposerMode.PROACTIVE,
            messageCount = 0,
            onGenerateProactive = { proactiveClicks.incrementAndGet() },
            onGenerateReply = { replyClicks.incrementAndGet() }
        )
        composeRule.onNodeWithText("生成开场").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText("生成开场").performClick()

        assertEquals("生成开场应恰好 1 次", 1, proactiveClicks.get())
        assertEquals("主动发模式不得触发回复生成", 0, replyClicks.get())
    }

    // ═══════════ 4. 生成中：回复 LOADING 条 ═══════════

    /**
     * 审计修订点：生产 `isGenerating` 走 GenerationActionButton 的 LOADING 分支，
     * 渲染「<阶段词> · Ns  点击停止」，不是裸「停止」。
     *
     * LOADING 分支含 rememberInfiniteTransition + 每秒自增的 LaunchedEffect，
     * 因此关掉 autoAdvance、按帧手动推进，避免 waitForIdle 被无限动画拖着自旋。
     */
    @Test
    fun generating_showsProductionLoadingStopAffordanceAndCallsOnStopOnce() {
        val stopClicks = AtomicInteger(0)
        composeRule.mainClock.autoAdvance = false
        setActions(
            composerMode = ComposerMode.REPLY,
            isGenerating = true,
            messageCount = 3,
            onStop = { stopClicks.incrementAndGet() }
        )
        // 推进若干帧完成组合 + LaunchedEffect
        composeRule.mainClock.advanceTimeBy(120L)

        composeRule.onNodeWithText(loadingStopSuffix, substring = true).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        // 整串校验真实文案（旧用例写死 onNodeWithText("停止") 就是在这里必挂）
        val bar = loadingStopBarText()
        assertTrue("生产 LOADING 停止条文案应完整匹配，实际：$bar", loadingStopText.matches(bar))
        assertEquals("只应存在一个停止条", 1,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        // 生成中不得还能点到「生成回复」
        composeRule.onNodeWithText("生成回复 · 3条消息").assertIsNotDisplayed()
        // 回归护栏：LOADING 态从不渲染裸「停止」——旧用例正是因此必挂
        composeRule.onNodeWithText("停止").assertDoesNotExist()

        // 接点击的是带 click action 语义的父节点（文字节点自身无 action）
        composeRule.onNode(hasClickAction()).performClick()
        assertEquals("停止应恰好触发 1 次", 1, stopClicks.get())
    }

    /** 主动发生成中（isProactive=true）走 STOP 分支 —— 此时才是裸「停止」文案 */
    @Test
    fun proactiveGenerating_showsPlainStopButtonAndCallsOnStopOnce() {
        val stopClicks = AtomicInteger(0)
        setActions(
            composerMode = ComposerMode.PROACTIVE,
            isProactive = true,
            onGenerateProactive = {},
            onStop = { stopClicks.incrementAndGet() }
        )
        composeRule.onNodeWithText("停止").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText("生成开场").assertIsNotDisplayed()
        composeRule.onNodeWithText("停止").performClick()
        assertEquals("主动发停止应恰好触发 1 次", 1, stopClicks.get())
    }

    /** 回复生成中优先于 isProactive：两个标志同时为真时也必须给出停止入口，且只有一份 */
    @Test
    fun generating_takesPrecedenceOverProactiveAndShowsSingleStopAffordance() {
        val stopClicks = AtomicInteger(0)
        composeRule.mainClock.autoAdvance = false
        setActions(
            composerMode = ComposerMode.REPLY,
            isGenerating = true,
            isProactive = true,
            messageCount = 2,
            onStop = { stopClicks.incrementAndGet() }
        )
        composeRule.mainClock.advanceTimeBy(120L)

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onNode(hasClickAction()).performClick()
        assertEquals("同一帧只能有一个停止 owner，点击应恰好 1 次", 1, stopClicks.get())
    }
}
