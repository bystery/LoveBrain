package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.R
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.testing.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import com.lovebrain.app.testing.assertIsDisplayedDiagnosed

/**
 * （审计 第2节、第5节、第8节第1条 第 3 条）：生产 [ReplyPrimaryActions] 的
 * 4 种按钮状态 + 停止动作 + 零消息禁用态行为测试。
 *
 * 第 5 组（下面那段）是这一轮新加的**手势**合同：「生成回复」那一格多点一下照旧生成、
 * 按住不放则只按本轮输入生成一次。前两格用注入的真实手指手势（`performClick()` 走的是
 * 语义动作，量不到手指那一面，也就量不到"一次手势发两条请求"这种双发）。
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
     * 生产 GeneratingLabel.kt 渲染 "$phase · ${elapsedSec}s  点击停止"，
     * phase 随秒数切换（分析对话/生成方案/深度分析），所以：
     * Compose 1.6.8 的 ui-test 没有 Regex 版 finder，先用恒定后缀「点击停止」定位节点，
     * 再取该节点 semantics 文本做整串正则校验（既不写死秒数，也不放过文案漂移）。
     */
    // 停止棒文案不写死在这里：生产拼法是 panel_analysing_with_seconds(阶段词, 秒数)，
    // 阶段词又随秒数在三个资源之间换，所以整串匹配模式也从资源现拼
    // （见 UiText.generatingBarPattern()）。模板被改动时那条正则当场红，而不是静默失配。
    private val loadingStopText: Regex get() = UiText.generatingBarPattern()

    // 按钮文案一律取自资源。写死中文的断言在英文模拟器上永远找不到节点——
    //  本文件 8 格里红 7 格就是这个原因，唯一不认文字的那格是绿的。
    private val generateReply: String get() = UiText.current(R.string.panel_generate_reply)
    private fun generateReplyWithCount(n: Int): String =
        UiText.current(R.string.panel_generate_reply_with_count, n)

    private val generateOpening: String get() = UiText.current(R.string.panel_generate_opening)
    private val retry: String get() = UiText.current(R.string.panel_retry)
    private val saveToKb: String get() = UiText.current(R.string.panel_save_to_kb)
    private val stop: String get() = UiText.current(R.string.panel_stop)

    /** 取承载「点击停止」的那条文案的完整语义文本 */
    private fun loadingStopBarText(): String {
        // 用生产留的 tag 定位（文字会变，tag 不会），再取语义文本做整串校验
        val node = composeRule
            .onNodeWithTag(LbTags.PRIMARY_STOP)
            .fetchSemanticsNode("未找到生成中的停止条（tag=${LbTags.PRIMARY_STOP}）")
        return node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()
    }

    private fun setActions(
        composerMode: ComposerMode,
        isGenerating: Boolean = false,
        isProactive: Boolean = false,
        hasReplyResult: Boolean = false,
        messageCount: Int = 0,
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
        composeRule.onNodeWithText(generateReplyWithCount(3)).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText(generateReplyWithCount(3)).performClick()
        // 审计修订：JUnit 断言，不用裸 assert()
        assertEquals("生成回复回调应恰好触发 1 次", 1, generateClicks.get())
    }

    /**
     * 审计 第2节 明确要求：「ReplyPrimaryActionsTest 的零消息用例只检查文字，
     * 没有 assertIsNotEnabled()」——本用例补上该断言。
     *
     * ⚠ 这段 KDoc 原来写的是"**预期红**：生产 `GenerationActionButton` 的禁用分支
     * 从未写 `SemanticsProperties.Disabled`……本用例以后才会转绿"。那句话**已经过期**：
     * `GenerationActionButton` 早被 `LbPrimaryButton` 取代（`d8f36d2`），而那颗的 Disabled
     * 是写进语义树的——这一格那颗 N=0 的灰按钮按 `LbButtonHeightTier.PanelPrimaryAction`
     * 那一档画（可见高度是档上的 40dp，**不是**旧注释里抄的 48dp；宽度那一轴仍钉 48dp 见方，
     * 也就是 `LB_PRIMARY_MIN_HEIGHT_DP`——本轮只降可见高度，热区与宽度下限一寸没降），
     * 读屏拿到的是 `「Generate reply」 role=Button disabled`，
     * `assertIsNotEnabled()` 现在拿得到 Disabled，这一格是**绿的**。
     * 留着那段"预期红"就是给下一窗口埋一条误判（"这格红是生产没修"）。
     * 行为那一半的证据见 [replyMode_zeroMessages_theDisabledGenerateFiresNoCallback]。
     */
    @Test
    fun replyMode_noResult_zeroMessages_buttonLabelHasNoCountAndIsNotEnabled() {
        val generateClicks = AtomicInteger(0)
        // 生产组件从来没有、也不该再有 draftText 形参：零消息的禁用判据只有消息数。
        // （这里曾经留过一个没人读的 draftText 实参，用来钉"草稿不得改变零消息禁用态"；
        //  形参没了之后那条钉子改由下面这两行直接判：不计数、且 disabled。）
        setActions(
            composerMode = ComposerMode.REPLY,
            messageCount = 0,
            onGenerateReply = { generateClicks.incrementAndGet() }
        )
        // 0 条消息：无计数后缀
        composeRule.onNodeWithText(generateReply).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText(generateReplyWithCount(0)).assertIsNotDisplayed()
        composeRule.onNodeWithText(generateReply).assertIsNotEnabled()
    }

    /**
     * 禁用那颗「行为」证据：**点它发不出回调**。
     *
     * ⚠ 原来这一格断的是 `onAllNodes(hasClickAction()).assertCountEquals(0)`，
     * 也就是拿"整棵树没有任何点击语义"当"不能生成"的代理。那个代理**已经被设计系统换掉了**：
     * 这一颗现在是 `LbPrimaryButton(state = Disabled)`，它内部写的是
     * `clickable(enabled = false, role = Role.Button)` —— 禁用态**故意**保留点击语义与角色，
     * 这样读屏才念得出"这里是一颗按钮，只是现在不能按"（:532 那条，
     * `LbPrimaryButtonStateTest."every state meets the touch floor"` 就在断言它报 `Button`）。
     * 本机语义树实测：`onAllNodes(hasClickAction()) = 1`、`OnClick 动作存在 = true`、
     * `disabled 那颗 role=Button`；它的可见高度走 `LbButtonHeightTier.PanelPrimaryAction`
     * （40dp 档），宽度那一轴仍指回 48dp 见方（`LB_PRIMARY_MIN_HEIGHT_DP`）。
     * 旧注释里那个 `129x48dp` 是**降档之前**的读数，现在照它判就已经过期了。
     *
     * ⚠ 边界要说清楚：**本文件不读 `boundsInRoot`**，所以 40（生成 / 生成中 / 停止）与
     * 36（重试 / 记入知识库）这两档的实测不在这里判，这里只判语义与回调。
     * 那条尺在 JVM 侧 `ReplyPrimaryActionsContractTest.assertTierHeight`（读实到的高度与
     * `tier.minHeightDp` 逐档比对）——生产把可见高度改成 20dp 那一头当场红。
     * 在本文件里加一条"高度存在即可"式的判据不会更严，只会把那把尺伪装成已经守住了。
     *
     * 所以这一格改成直接判**行为**，判据没有变软，反而更严：那颗必须在、必须报 Disabled、
     * 必须被真实点一次、回调必须一次都不发。"代理指标坏了就说行为坏了"是复核点名的误读之一。
     * 同一条性质在 JVM 那边也有守卫（`LbPrimaryButtonStateTest` 的四态点击那格），两边不互相顶替。
     */
    @Test
    fun replyMode_zeroMessages_theDisabledGenerateFiresNoCallback() {
        val generateClicks = AtomicInteger(0)
        setActions(
            composerMode = ComposerMode.REPLY,
            messageCount = 0,
            onGenerateReply = { generateClicks.incrementAndGet() }
        )
        val button = composeRule.onNodeWithText(generateReply)
        button.assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        button.assertIsNotEnabled()   // 1.6.8 没有 assertIsDisabled：这一条判的就是 Disabled 语义
        button.performClick()
        assertEquals("零消息时点那颗禁用按钮也不得发出回调", 0, generateClicks.get())
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
        composeRule.onNodeWithText(retry).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText(saveToKb).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        // 有结果时不得再出现「生成回复」主按钮（主动发/生成入口不能被挤掉）
        composeRule.onNodeWithText(generateReplyWithCount(3)).assertIsNotDisplayed()

        composeRule.onNodeWithText(retry).performClick()
        composeRule.onNodeWithText(saveToKb).performClick()

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
        composeRule.onNodeWithText(generateOpening).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText(generateOpening).performClick()

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

        composeRule.onNodeWithTag(LbTags.PRIMARY_STOP).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        // 整串校验真实文案（旧用例写死 onNodeWithText("停止") 就是在这里必挂）
        val bar = loadingStopBarText()
        assertTrue("生产 LOADING 停止条文案应完整匹配，实际：$bar", loadingStopText.matches(bar))
        assertEquals("只应存在一个停止条", 1,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        // 生成中不得还能点到「生成回复」
        composeRule.onNodeWithText(generateReplyWithCount(3)).assertIsNotDisplayed()
        // 回归护栏：LOADING 态从不渲染裸「停止」——旧用例正是因此必挂
        composeRule.onNodeWithText(stop).assertDoesNotExist()

        // 接点击的是带 click action 语义的父节点（文字节点自身无 action）
        composeRule.onNode(hasClickAction()).performClick()
        assertEquals("停止应恰好触发 1 次", 1, stopClicks.get())
    }

    /**
     * 主动发生成中（isProactive=true）。
     *
     * 2026-10-10 需求 §二：两条生成流程现在选**同一个 Loading 状态**（进度环＋呼吸），
     * 文案仍各自那句"停止"（`ProductionUiContractTest` 钉着这个文件里必须留着那句资源引用）。
     * 于是这一格和上面那格一样，会撞上一次"永不停顿的脉冲动画让测试一直不空闲"——
     * 修法照同一族先例：关掉 autoAdvance、手动推进固定时长，让断言落在同一帧上。
     */
    @Test
    fun proactiveGenerating_showsPlainStopButtonAndCallsOnStopOnce() {
        val stopClicks = AtomicInteger(0)
        composeRule.mainClock.autoAdvance = false
        setActions(
            composerMode = ComposerMode.PROACTIVE,
            isProactive = true,
            onGenerateProactive = {},
            onStop = { stopClicks.incrementAndGet() }
        )
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithText(stop).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText(generateOpening).assertIsNotDisplayed()
        composeRule.onNodeWithText(stop).performClick()
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
