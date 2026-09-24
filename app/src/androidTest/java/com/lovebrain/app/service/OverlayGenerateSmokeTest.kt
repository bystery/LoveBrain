package com.lovebrain.app.service

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.lovebrain.app.AppConfig
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.testing.FakeProviderServer
import com.lovebrain.app.testing.MainChainHarness
import com.lovebrain.app.testing.ProductionPanel
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.lovebrain.app.testing.assertIsDisplayedDiagnosed

/**
 * S1-02 / S1-03（审计 §5.1、§8.1 第 4-6 条）：真实生产 Panel 主链 instrumentation 测试。
 *
 * 审计原判 → 本类对应修复：
 * · 「Overlay 测试没有挂载并点击生产按钮」→ 用 [ProductionPanel] 挂载生产
 *   LoveBrainPanelScreen（与 FloatingService.setContent 同一组件、同一 LoveBrainTheme）；
 *   消息经真输入框 + 真「➕ 添加」进入 MessageList；生成由真
 *   「生成回复 · N条消息」按钮点击触发。
 * · 「没有先添加任何消息；生产 generateReply() 在 dialogue.isEmpty() 时直接返回 null，
 *   因此该测试预期 RecoverableError 与生产前置条件冲突」→ 所有无 Provider 用例都先满足
 *   「MessageList 已有消息」这一生产前置条件，再断言生产实际给出的可恢复错误
 *   （GenerateResult.Error + 结果区文案），不再断言生产永远不会产生的
 *   ReplyRequestState.RecoverableError。
 * · 「没有成功 Provider 流」→ 成功流 / 401 / 超时 / 解析失败 / 停止 / 快速双击 /
 *   旧请求迟到 / Service destroy 全部由 [FakeProviderServer]（loopback SSE）驱动，
 *   s.requestCount 就是审计要的「Engine/Provider 实际调用次数」。
 *
 * 断言只用 JUnit + Compose 断言（不用裸 Kotlin assert()：instrumentation 不保证开 -ea）。
 * 期望文案直接取生产 ReplyFailureKind.userMessage，避免测试自抄文案与生产漂移。
 *
 * ⚠ 两条「按合同断言」的用例在生产修复前预期为红（详见各自 KDoc 与交付报告）：
 *   1. [lateCallbacksFromSupersededRequest_doNotOverwriteCurrentRequest]
 *   2. [rapidDoubleTap_onRealGenerateButton_startsExactlyOneProviderRequest]
 */
@RunWith(AndroidJUnit4::class)
class OverlayGenerateSmokeTest {

    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @get:Rule
    val serviceRule = ServiceTestRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private lateinit var vm: LoveBrainViewModel
    private var server: FakeProviderServer? = null

    /** 生成中面板渲染 LOADING 无限动画，关掉自动推进时钟，由测试按帧推进 */
    private val loadingStopSuffix = "点击停止"
    private val loadingStopText = Regex("""(分析对话|生成方案|深度分析) · \d+s\s+点击停止""")

    /**
     * 断言主操作位置就是生产 LOADING 停止条，并返回该节点文本。
     * Compose 1.6.8 无 Regex finder → 用恒定后缀定位 + 整串正则校验真实文案。
     */
    private fun assertLoadingStopBar(reason: String) {
        composeRule.onNodeWithText(loadingStopSuffix, substring = true).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        val node = composeRule
            .onNodeWithText(loadingStopSuffix, substring = true)
            .fetchSemanticsNode("未找到生成中的停止条：$reason")
        val bar = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()
        assertTrue("$reason：生产停止条文案应完整匹配，实际：$bar", loadingStopText.matches(bar))
    }

    @Before
    fun setUp() {
        MainChainHarness.skipOnboarding()
        MainChainHarness.clearProviderConfig()
        // 每个用例一个新 VM 实例（appModule 里 viewModel 定义是工厂）
        vm = MainChainHarness.newViewModel()
    }

    @After
    fun tearDown() {
        runCatching { vm.stopGeneration() }
        server?.let { runCatching { it.close() } }
        server = null
        MainChainHarness.clearProviderConfig()
    }

    // ═══════════════════════ 脚手架 ═══════════════════════

    private fun mountPanel() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { ProductionPanel(viewModel = vm) }
        composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS)
    }

    /** 装 fake Provider 并等 providerReady 变 true */
    private fun installProvider(defaultScript: FakeProviderServer.Script): FakeProviderServer {
        val s = startSilentProvider(defaultScript)
        MainChainHarness.installFakeProvider(s)
        MainChainHarness.awaitProviderReady(vm, ready = true)
        return s
    }

    /** 只起 fake Provider 服务、不配置工单：这样「零请求」是可观测的事实而不是空话 */
    private fun startSilentProvider(script: FakeProviderServer.Script): FakeProviderServer {
        val s = FakeProviderServer()
        s.defaultScript = script
        server = s
        return s
    }

    /** 推帧 + 真实时间片：让生产协程与 Compose 组合都往前走 */
    private fun pumpUntil(reason: String, timeoutMs: Long = 15_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS)
            if (condition()) {
                composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS) // 补一帧，确保最新状态已重绘
                return
            }
            Thread.sleep(20L)
        }
        throw AssertionError("等待超时（${timeoutMs}ms）：$reason")
    }

    /** 走真 UI 往 MessageList 放一条消息（生产：输入框 + ➕ 添加） */
    private fun addMessageThroughRealUi(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
        composeRule.onNodeWithContentDescription("添加").performClick()
        // mountPanel 关掉了主时钟的自动推进（LOADING 条有无限动画），所以"推一帧"
        // 不等于"VM 的协程跑完了 + 组合测量完了"。以前这里只 advanceTimeBy 一次就断言，
        // 真机上 7 个用例全死在同一句 "MessageList 应真收到 1 条消息 expected:<1> but was:<0>"。
        pumpUntil("点过➕之后 MessageList 要有这一条") { vm.messages.value.isNotEmpty() }
        composeRule.onNodeWithText(text)
            .assertIsDisplayedDiagnosed("刚添加的那条消息")
        assertEquals("MessageList 应真收到 1 条消息", 1, vm.messages.value.size)
    }

    private fun tapGenerateReplyButton(messageCount: Int) {
        composeRule.onNodeWithText("生成回复 · ${messageCount}条消息").performClick()
    }

    private fun currentError(): GenerateResult.Error? = vm.result.value as? GenerateResult.Error

    // ═══════════════ 1. 空态蓝字 = 只切模式、零 Provider 调用 ═══════════════

    /**
     * 审计 §2 / §8.1 第 4 条：「点击 MessageList 空态蓝字只切模式、Engine 调用次数仍为 0」。
     * 这里的调用次数 = fake Provider 实收请求数（真链路计数器，不是自造计数）。
     */
    @Test
    fun emptyState_blueProactiveEntryClick_onlyFlipsComposerMode_withZeroProviderCalls() {
        val s = installProvider(FakeProviderServer.Script.Stream(listOf("不该被用到的响应")))
        mountPanel()

        val entryText = "还没有聊天记录，点这里主动发一条"
        composeRule.onNodeWithText(entryText).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        assertEquals(
            "初始应为 REPLY 模式",
            ComposerMode.REPLY,
            vm.composerMode.value
        )

        composeRule.onNodeWithText(entryText).performClick()
        composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS)

        assertEquals(
            "点击蓝字应切到 PROACTIVE 模式",
            ComposerMode.PROACTIVE,
            vm.composerMode.value
        )
        composeRule.onNodeWithText("生成开场").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        assertFalse("切模式不得进入生成中", vm.isGenerating.value)
        assertNull("切模式不得产生结果", vm.result.value)
        assertEquals("切模式的 Engine/Provider 调用次数必须为 0", 0, s.requestCount)

        // 再点一次 = 关闭主动发，仍然零请求
        composeRule.onNodeWithText("主动发模式已开启，点击关闭").performClick()
        composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS)

        assertEquals(
            "再次点击应回到 REPLY 模式",
            ComposerMode.REPLY,
            vm.composerMode.value
        )
        assertEquals("两次点击后 Provider 调用次数仍为 0", 0, s.requestCount)
        assertEquals("入口点击不得产生任何消息", 0, vm.messages.value.size)
    }

    // ═══════════════════════ 2. 无 Provider ═══════════════════════

    /**
     * 生产 LoveBrainPanelScreen.kt:408-411 的入口守卫：providerReady=false 时点主按钮
     * 只给面板内可消失告警，绝不发请求。
     */
    @Test
    fun noProvider_realGenerateButtonTap_showsRecoverableWarningAndCallsNothing() {
        val silent = startSilentProvider(FakeProviderServer.Script.Stream(listOf("不该到达的响应")))
        MainChainHarness.awaitProviderReady(vm, ready = false)
        mountPanel()

        addMessageThroughRealUi("在吗")
        tapGenerateReplyButton(1)
        composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS)

        val warning = vm.panelWarning.value
        assertNotNull("未配置 Provider 时点生成应给出面板告警", warning)
        assertTrue("告警文案应指向模型供应商配置，实际：$warning", warning!!.contains("模型供应商"))
        composeRule.onNodeWithText(warning).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        assertNull("未配置 Provider 不得产生结果", vm.result.value)
        assertFalse("未配置 Provider 不得进入生成中", vm.isGenerating.value)
        assertEquals("未配置 Provider 不得发出请求", 0, silent.requestCount)
    }

    /**
     * 审计 §5.1 点名的破损用例修复版：先满足生产前置条件（dialogue 非空），
     * 再断言生产真正给出的可恢复错误。
     *
     * 旧用例断言 ReplyRequestState.RecoverableError 有两处与生产冲突：
     * · 无消息时 GenerationEngine.kt:321 直接 return null，状态只会被 CAS 清回 Idle；
     * · 有消息且无 Provider 时，生产走 GenerationEngine.kt:395-400 的
     *   GenerateResult.Error("请先配置一个可用的模型供应商")，由 ResultArea 渲染，
     *   根本不写 RecoverableError。
     * 所以旧断言永远不成立，只能靠超时失败。
     */
    @Test
    fun noProvider_afterAddingMessage_generateSurfacesProviderMissingError() {
        val silent = startSilentProvider(FakeProviderServer.Script.Stream(listOf("不该到达的响应")))
        vm.addMessage(ChatMessage.Role.ME, "在吗")
        assertEquals(
            "前置条件：必须已有 1 条消息（否则生产 Engine 直接 reject）",
            1,
            vm.messages.value.size
        )

        vm.generate()

        MainChainHarness.await("无 Provider 时应给出错误结果") { vm.result.value != null }
        val error = currentError()
        assertNotNull("结果应为 GenerateResult.Error", error)
        assertEquals(
            "无 Provider 文案应取生产 ReplyFailureKind.ProviderMissing",
            ReplyFailureKind.ProviderMissing.userMessage,
            error!!.message
        )
        assertFalse("失败后不得留在生成中", vm.isGenerating.value)
        assertEquals("未配置 Provider 时不得发出任何网络请求", 0, silent.requestCount)
    }

    // ═══════════════════════ 3. 成功流 ═══════════════════════

    @Test
    fun successStream_realGenerateChain_rendersResultWithExactlyOneProviderRequest() {
        val marker = "FAKE_OK_REPLY_MAIN_CHAIN"
        val s = installProvider(
            FakeProviderServer.Script.Stream(FakeProviderServer.validReplyJsonChunks(marker), chunkDelayMs = 20L)
        )
        mountPanel()

        addMessageThroughRealUi("你最近是不是很忙")
        tapGenerateReplyButton(1)

        pumpUntil("应进入生成中") { vm.isGenerating.value }
        // 生成中主操作位置必须是 LOADING 停止条（生产真实文案，不是裸「停止」）
        assertLoadingStopBar("成功流生成中")

        pumpUntil("应渲染成功结果") { vm.result.value is GenerateResult.Success }

        val result = vm.result.value as GenerateResult.Success
        assertTrue("成功结果必须带 fake Provider 的文本", result.response.toString().contains(marker))
        assertEquals("一次点击只允许 1 个 Provider 请求", 1, s.requestCount)
        assertTrue(
            "新加的消息必须真的进入 Provider prompt",
            s.lastRequestBody().contains("你最近是不是很忙")
        )
        // 有结果时生产主操作位换成「重试 | 记入知识库」
        composeRule.onNodeWithText("重试").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        composeRule.onNodeWithText("记入知识库").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        assertFalse("完成后不得留在生成中", vm.isGenerating.value)
    }

    // ═══════════════════════ 4. 401 认证失败 ═══════════════════════

    /** CONFIG_ERROR 族不重试（GenerationEngine.kt:476）——请求数必须恰好为 1 */
    @Test
    fun authFailure401_showsAuthErrorWithoutRetrying() {
        val s = installProvider(
            FakeProviderServer.Script.HttpStatus(
                401,
                "{\"error\":{\"message\":\"Authentication Fails, your api key is invalid\"}}"
            )
        )
        mountPanel()

        addMessageThroughRealUi("在吗")
        tapGenerateReplyButton(1)

        pumpUntil("401 应给出错误结果") { currentError() != null }
        assertEquals(
            "401 文案应取生产 ReplyFailureKind.Auth",
            ReplyFailureKind.Auth.userMessage,
            currentError()!!.message
        )
        // 生产 LOADING 分支文案由 GenerationActionButton 渲染；此处断言真实用户可见错误
        composeRule.onNodeWithText(ReplyFailureKind.Auth.userMessage).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        assertEquals("认证类配置错误不得重试", 1, s.requestCount)
        assertFalse("失败后不得留在生成中", vm.isGenerating.value)
    }

    // ═══════════════════════ 5. 超时 ═══════════════════════

    /**
     * 超时分类 + 重试预算。
     *
     * 为什么用「Provider 回 500 + timeout 文案」而不是真把连接挂死：
     * 生产硬超时是 AppConfig.GENERATE_TIMEOUT_MS = 120s，且 GENERATE_MAX_ATTEMPTS = 4，
     * 真挂连接最坏要 ~8 分钟才出终态，无法进 CI 门禁。
     * 本用例覆盖同一条终态链路：collectStream 错误 → ReplyFailureKind.fromErrorMessage
     * → Timeout → 用户可重试文案 + 用满重试预算。
     * 「socket 真超时 120s」只能人工带外验证，已写进交付报告，不伪称跑过。
     */
    @Test
    fun providerTimeout_showsRetryableTimeoutErrorAndUsesWholeRetryBudget() {
        val s = installProvider(
            FakeProviderServer.Script.HttpStatus(500, "{\"error\":{\"message\":\"upstream timeout\"}}")
        )
        mountPanel()

        addMessageThroughRealUi("在吗")
        tapGenerateReplyButton(1)

        pumpUntil("超时应给出可重试错误", timeoutMs = 20_000L) { currentError() != null }

        assertEquals(
            "超时文案应取生产 ReplyFailureKind.Timeout",
            ReplyFailureKind.Timeout.userMessage,
            currentError()!!.message
        )
        assertTrue("超时必须可重试", ReplyFailureKind.Timeout.retryable)
        assertEquals(
            "非配置类错误应用满生产重试预算",
            AppConfig.GENERATE_MAX_ATTEMPTS,
            s.requestCount
        )
        assertFalse("终态不得留在生成中", vm.isGenerating.value)
    }

    // ═══════════════════════ 6. 解析失败 ═══════════════════════

    @Test
    fun parseFailure_garbageStream_showsParseError() {
        val s = installProvider(
            FakeProviderServer.Script.Stream(listOf(FakeProviderServer.UNPARSEABLE_TEXT))
        )
        mountPanel()

        addMessageThroughRealUi("在吗")
        tapGenerateReplyButton(1)

        pumpUntil("解析失败应给出错误结果") { currentError() != null }
        assertEquals(
            "解析失败文案应取生产 ReplyFailureKind.Parse",
            ReplyFailureKind.Parse.userMessage,
            currentError()!!.message
        )
        composeRule.onNodeWithText(ReplyFailureKind.Parse.userMessage).assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
        assertEquals("解析失败发生在流之后，只应有 1 个请求", 1, s.requestCount)
        assertFalse("失败后不得留在生成中", vm.isGenerating.value)
    }

    // ═══════════════════════ 7. 停止 ═══════════════════════

    @Test
    fun stopDuringGeneration_cancelsCurrentRequestAndReturnsToIdle() {
        val s = installProvider(FakeProviderServer.Script.NoResponse)
        mountPanel()

        addMessageThroughRealUi("在吗")
        tapGenerateReplyButton(1)

        pumpUntil("应进入生成中") { vm.isGenerating.value }
        assertLoadingStopBar("停止前")
        assertEquals("停止前应已发出 1 个请求", 1, s.requestCount)

        // 点生产停止动作：点 LOADING 条文字节点，触摸注入由其可点击父节点接收
        composeRule.onNodeWithText(loadingStopSuffix, substring = true).performClick()
        composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS)

        pumpUntil("停止后应退出生成中") { !vm.isGenerating.value }
        assertFalse("停止后 isGenerating 必须为 false", vm.isGenerating.value)
        assertNull("停止不得留下结果", vm.result.value)
        assertEquals("停止不得清空消息", 1, vm.messages.value.size)
        composeRule.onNodeWithText("生成回复 · 1条消息").assertIsDisplayedDiagnosed("上一步定位到的节点必须真的显示在屏幕上")
    }

    // ═══════════════════════ 8. 快速双击 ═══════════════════════

    /**
     * 审计 §5.1「断言只启动一个网络请求」。
     *
     * ⚠ 预期红：LoveBrainViewModel.kt:844-855 —— guard 查 operationCoordinator.isActive(REPLY)，
     * 但租约要到 prep 协程 await 完 readIntent / readCorrections 之后（同文件 934 行）才注册，
     * 而 `_replyRequestState.value = Preparing(requestId)` 是无条件覆盖写。
     * 于是第二次点击：① 过得了 guard；② 覆盖掉第一个请求的 requestId 身份；
     * ③ 两个 prep 都在 GenerationEngine.kt:321 的 callbacks.isGenerating() 处被 reject
     * —— 净效果是 0 个请求（用户表现为「点了没反应」），而不是要求的 1 个。
     */
    @Test
    fun rapidDoubleTap_onRealGenerateButton_startsExactlyOneProviderRequest() {
        val marker = "FAKE_OK_DOUBLE_TAP"
        val s = installProvider(
            FakeProviderServer.Script.Stream(FakeProviderServer.validReplyJsonChunks(marker), chunkDelayMs = 30L)
        )
        mountPanel()

        addMessageThroughRealUi("在吗")
        // 同一帧内连点两次 = 真实快速双击
        tapGenerateReplyButton(1)
        tapGenerateReplyButton(1)
        composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS)

        assertEquals("快速双击只允许产生 1 个 Provider 请求", 1, s.requestCount)
        pumpUntil("双击后仍应渲染成功结果") { vm.result.value is GenerateResult.Success }
        assertEquals("最终只应有 1 个请求落网", 1, s.requestCount)
        assertFalse("完成后不得留在生成中", vm.isGenerating.value)
    }

    // ═══════════════════ 9. 被取代请求的迟到回调 ═══════════════════

    /**
     * 审计 §5.1「断言旧请求迟到 chunk/result 不覆盖新请求」。
     *
     * 生产 GenerationEngine.Callbacks 不带 requestId（审计 §6 S2-03），
     * 因此唯一确定性的注入点是直接调用 VM 的回调实现，模拟
     * 「被取消的旧请求，其最后一个在途回调在新请求已开始后才到达」。
     *
     * ⚠ 预期红：
     * · LoveBrainViewModel.kt:1996 onReplyStreamingCoreText 无任何所有权判定，
     *   直接累加进当前请求的流式缓冲；
     * · LoveBrainViewModel.kt:2060-2073 onReplyResult 只要 currentState.isBusy 就接受并直接写
     *   `_result`，无法分辨事件属于哪个 requestId。
     */
    @Test
    fun lateCallbacksFromSupersededRequest_doNotOverwriteCurrentRequest() {
        val staleChunk = "STALE_CHUNK_FROM_SUPERSEDED_REQUEST"
        val staleMessage = "STALE_RESULT_FROM_SUPERSEDED_REQUEST"
        // R1 的 requestId 在 VM 内部，测试拿不到也不需要——任何不等于当前 owner 的身份都该被拒
        val STALE_REQUEST_ID = "00000000-0000-4000-8000-000000000001"

        // 请求都挂住不下发：R1 用于被停止取代，R2 用于保持「当前请求在途」
        val s = installProvider(FakeProviderServer.Script.NoResponse)

        // R1：发起后停止 —— R1 被取代
        vm.addMessage(ChatMessage.Role.ME, "在吗")
        vm.generate()
        pumpUntil("R1 应进入生成中") { vm.isGenerating.value }
        vm.stopGeneration()
        pumpUntil("停止 R1 后应回到空闲") { !vm.isGenerating.value }

        // R2：新请求，被 fake 挂住不会自己完成
        vm.generate()
        pumpUntil("R2 应进入生成中") { vm.isGenerating.value }
        assertTrue("R2 应已向 Provider 发出请求", s.requestCount >= 1)
        assertNull("R2 在途时不得已有结果", vm.result.value)

        // 注入 R1 的迟到事件——S2-03 之后 reducer 是唯一写入口，
        // 身份不属于当前 R2 的事件必须整条被拒。
        vm.dispatchReply(ReplyChunk(STALE_REQUEST_ID, staleChunk))
        vm.dispatchReply(ReplyCompleted(STALE_REQUEST_ID, GenerateResult.Error(staleMessage)))

        assertNull("被取代请求的迟到 result 不得写进当前请求", vm.result.value)
        // 生产流式文本按 50ms 合并发布，给足时间暴露污染
        Thread.sleep(400L)
        composeRule.mainClock.advanceTimeBy(FRAME_PUMP_MS)
        assertFalse(
            "被取代请求的迟到 chunk 不得混进当前请求的流式文本，实际：${vm.streamingCoreText.value}",
            vm.streamingCoreText.value.contains(staleChunk)
        )
        assertTrue("当前请求不应被迟到回调踢出在途状态", vm.isGenerating.value)
    }

    // ═══════════════════════ 10. Service destroy ═══════════════════════

    /**
     * 审计 §8.1 第 5 条「Service destroy」。
     *
     * 悬浮窗权限与 Android 14+ 后台 FGS 策略在 instrumentation 下不保证可用，
     * 服务起不来时按环境不支持处理（Assume 明确报 skipped），不伪装成通过；
     * 起得来就断言真实合同：instance 发布 → onDestroy 释放 instance → 在途生成不受污染。
     */
    @Test
    fun floatingService_destroyWhileGenerationInFlight_releasesInstanceAndChainStaysUsable() {
        val intent = Intent(app, FloatingService::class.java)
        val started = runCatching { serviceRule.startService(intent) }.isSuccess
        Assume.assumeTrue(
            "FloatingService 未能在本 instrumentation 环境启动（悬浮窗权限 / FGS 后台策略）；" +
                "在具备条件的设备上本用例是真跑的",
            started
        )

        try {
            assertNotNull("启动后 FloatingService.instance 应存在", FloatingService.instance)

            // destroy 期间必须有一个前台生成在途（复用同一套 Koin 单例）
            val s = installProvider(
                FakeProviderServer.Script.Stream(listOf("不该完成的响应"), chunkDelayMs = 200L)
            )
            vm.addMessage(ChatMessage.Role.ME, "在吗")
            vm.generate()
            pumpUntil("destroy 前应有一个在途请求") { vm.isGenerating.value }

            app.stopService(intent)
            val deadline = System.currentTimeMillis() + 5_000L
            while (FloatingService.instance != null && System.currentTimeMillis() < deadline) {
                Thread.sleep(50L)
            }
            assertNull("onDestroy 必须释放 FloatingService.instance", FloatingService.instance)

            // destroy 不得打断在途请求的所有权：要么正常出结果，要么可干净停止
            pumpUntil("destroy 后在途请求应出结果或可停止", timeoutMs = 12_000L) {
                vm.result.value != null || !vm.isGenerating.value
            }
            runCatching { vm.stopGeneration() }
            pumpUntil("停止后必须回到空闲") { !vm.isGenerating.value }
            assertTrue("在途请求至少发过一次 Provider", s.requestCount >= 1)
        } finally {
            runCatching { app.stopService(intent) }
        }
    }

    /** 反复起停不泄漏实例（P3-02「Service 开关」的 instrumentation 侧证据） */
    @Test
    fun floatingService_repeatedStartAndStop_neverLeaksInstance() {
        val intent = Intent(app, FloatingService::class.java)
        val started = runCatching { serviceRule.startService(intent) }.isSuccess
        Assume.assumeTrue("FloatingService 在本环境不可启动，跳过（见类 KDoc）", started)
        try {
            repeat(5) { round ->
                runCatching { serviceRule.startService(intent) }
                assertNotNull("第 ${round + 1} 轮启动后应有实例", FloatingService.instance)
                runCatching { app.stopService(intent) }
                val deadline = System.currentTimeMillis() + 3_000L
                while (FloatingService.instance != null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50L)
                }
                assertNull("第 ${round + 1} 轮起停后 instance 应被释放", FloatingService.instance)
            }
        } finally {
            runCatching { app.stopService(intent) }
        }
    }

    companion object {
        /** 每次推帧推进的测试时钟毫秒数 */
        private const val FRAME_PUMP_MS = 120L
    }
}
