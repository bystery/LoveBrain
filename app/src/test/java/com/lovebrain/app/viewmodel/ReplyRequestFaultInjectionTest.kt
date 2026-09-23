package com.lovebrain.app.viewmodel

import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.ReplyRequestState
import com.lovebrain.app.model.ReplyStarted
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R1-05: 生成回复故障注入测试。
 *
 * 验证在生成生命周期的每个阶段（准备、Provider 快照、网络、解析）发生异常时，
 * ViewModel 不会崩溃，状态正确回到 RecoverableError 或 Idle。
 *
 * 根因分析（R1-05 闭环）：
 *   旧代码 generate() 的准备阶段（intent 读取、纠正读取、Engine.generate 调用）
 *   没有统一的 try/catch/finally。准备阶段的异常会逃出 launch 块，导致 App 崩溃。
 *   修复：R1-03 在请求协程里包裹统一的 try/catch，异常转为 RecoverableError。
 *
 * S2-02/S2-03 之后一次请求收在 runReplyRequest 这一个协程里，形状变成：
 * - 单项可读输入失败（intent / corrections）就地降级，不打断本轮；
 * - 未被就地处理的准备异常走外层 catch，落 RecoverableError；
 * - CancellationException 先落成 Idle 再重抛——既不卡 Preparing，也不伪造失败结果；
 * - 连点由 coordinator 的互斥矩阵拒绝，不再靠 ViewModel 自己记 Job。
 * 因此断言从旧的"RecoverableError 或 Idle"收紧成具体终态。
 *
 * 注入点选在 `promptBuilder.replyPromptAssetHash()`：它是准备阶段里唯一既不被就地
 * try/catch 降级、又不跨 `withContext(IO)` 的调用，异常一定走外层 catch。
 * intent / corrections 那两处就地降级的读取按 KB 才触发，由 IntentKbIdentityRaceTest 覆盖。
 *
 * 协程编排沿用本包先例：Main 交给 UnconfinedTestDispatcher，前台任务在 Default 上跑，
 * 用真实 delay 等落定。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplyRequestFaultInjectionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `prep phase exception does not crash and produces RecoverableError`() = runBlocking {
        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        every { promptBuilder.replyPromptAssetHash() } throws java.io.IOException("asset read failed")

        val engine = mockk<GenerationEngine>(relaxed = true)
        val vm = createViewModel(promptBuilder = promptBuilder, generationEngine = engine)
        delay(200)

        // 添加至少一条消息，让 generate() 不被空消息 guard 拦截
        vm.addMessage(ChatMessage.Role.HER, "test")

        // 点击生成——不应抛异常
        vm.generate()
        delay(400)

        val state = vm.replyRequestState.value
        assertTrue(
            "准备阶段异常应转成 RecoverableError。Actual: $state",
            state is ReplyRequestState.RecoverableError
        )
        assertEquals(
            "错误文案应是用户可读文案，不含裸异常",
            ReplyFailureKind.Network.userMessage,
            (state as ReplyRequestState.RecoverableError).message
        )
        assertTrue("结果通道应带上一笔 Error", vm.result.value is GenerateResult.Error)
        assertEquals("生成态应已退出", false, vm.isGenerating.value)
        // 准备阶段就失败时，Engine 的流入口不该被调用
        verify(exactly = 0) { engine.replyStream(any()) }
    }

    @Test
    fun `CancellationException is rethrown not swallowed`() = runBlocking {
        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        every { promptBuilder.replyPromptAssetHash() } throws
            kotlinx.coroutines.CancellationException("cancelled")

        val engine = mockk<GenerationEngine>(relaxed = true)
        val vm = createViewModel(promptBuilder = promptBuilder, generationEngine = engine)
        delay(200)
        vm.addMessage(ChatMessage.Role.HER, "test")

        // 点击生成——CancellationException 应被重抛，不转为 Error
        vm.generate()
        delay(400)

        // 重抛前请求先被落成 Idle：既不卡在 Preparing，也不伪造一条失败结果
        assertTrue(
            "After CancellationException, state should be Idle. Actual: ${vm.replyRequestState.value}",
            vm.replyRequestState.value is ReplyRequestState.Idle
        )
        assertNull("取消不应产出 Error 结果", vm.result.value)
        verify(exactly = 0) { engine.replyStream(any()) }
    }

    @Test
    fun `double generate does not produce two concurrent requests`() = runBlocking {
        // 在途请求挂住不结束——第二次连点才有东西可拒
        val gate = CompletableDeferred<Unit>()
        val enteredStream = CompletableDeferred<Unit>()
        val engine = mockk<GenerationEngine>(relaxed = true)
        every { engine.replyStream(any()) } answers {
            val requestId = firstArg<GenerationInput>().requestId
            flow {
                emit(ReplyStarted(requestId))
                enteredStream.complete(Unit)
                gate.await()
            }
        }

        val vm = createViewModel(generationEngine = engine)
        delay(200)
        vm.addMessage(ChatMessage.Role.HER, "test")

        // 快速连点——第二次应被 coordinator 的互斥矩阵当场拒绝
        vm.generate()
        vm.generate()

        assertEquals(
            "REPLY 在管任务只能有一个",
            1,
            vm.operationCoordinator.activeOperations.value
                .count { it.type == ForegroundOperationCoordinator.OperationType.REPLY }
        )

        // 等第一个请求真正走到 Engine（真实线程上落定，给真实时间上限），再数调用次数
        awaitSignal(enteredStream, "第一个请求进入 Engine", vm)
        verify(exactly = 1) { engine.replyStream(any()) }
        assertTrue(
            "第一个请求不该被第二次发起抹掉。Actual: ${vm.replyRequestState.value}",
            vm.replyRequestState.value is ReplyRequestState.Streaming
        )
        assertTrue("仍应在生成中", vm.isGenerating.value)

        vm.stopGeneration()
        gate.complete(Unit)
        delay(400)
        assertTrue(vm.replyRequestState.value is ReplyRequestState.Idle)
    }

    /**
     * 等真实线程上的工作落定。
     *
     * 请求体跑在 coordinator 自己的 scope（Default）上，runBlocking 里的 delay 是真实时间；
     * 超过上限就直接带状态失败——既不无限挂起，也不吞条件。
     */
    private suspend fun awaitSignal(
        signal: CompletableDeferred<Unit>,
        what: String,
        vm: LoveBrainViewModel,
        timeoutMs: Long = 10_000L
    ) {
        var waitedMs = 0L
        while (!signal.isCompleted && waitedMs < timeoutMs) {
            delay(50)
            waitedMs += 50
        }
        assertTrue(
            "$what 应发生（等待 ${timeoutMs}ms 超时）。" +
                "requestState=${vm.replyRequestState.value}, result=${vm.result.value}",
            signal.isCompleted
        )
    }

    private fun createViewModel(
        knowledgeRepo: KnowledgeRepository = mockk(relaxed = true),
        deepSeekRepo: DeepSeekRepository = mockk(relaxed = true),
        securePrefs: SecurePrefs = mockk(relaxed = true),
        promptBuilder: PromptBuilder = mockk(relaxed = true),
        topicRecorder: TopicRecorder = mockk(relaxed = true),
        triggerCoordinator: KnowledgeTriggerCoordinator = mockk(relaxed = true),
        generationEngine: GenerationEngine = mockk()
    ): LoveBrainViewModel {
        // 显式 mock 返回值，避免 relaxed mock 对 Pair<String, Double> 创建错误类型
        every { securePrefs.loadTodayCost() } returns null
        every { securePrefs.loadSuggestion() } returns null
        every { securePrefs.loadCounselingResult() } returns null
        every { securePrefs.counselingDraft } returns ""
        every { securePrefs.thinkingMode } returns 0
        every { securePrefs.outputMode } returns 0
        every { securePrefs.panelMode } returns 0
        every { securePrefs.activeTicketId } returns null
        every { securePrefs.totalGenerateCount } returns 0
        every { securePrefs.totalCostYuan } returns 0.0
        every { securePrefs.totalCopyCount } returns 0
        every { securePrefs.totalAdoptCount } returns 0
        every { securePrefs.totalRewriteCount } returns 0
        every { securePrefs.getWorkerTickets() } returns emptyList()
        // 无激活 KB：准备阶段里跨 withContext(IO) 的 intent/corrections 读取不触发，
        // 本类的注入点因此唯一确定（KB 读取路径由 IntentKbIdentityRaceTest 覆盖）
        coEvery { knowledgeRepo.getActive() } returns null
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        // 注意：这里不再给 replyStream 装默认桩——默认桩会覆盖调用方在测试里先装的桩。
        // 用例 1/2 断言 Engine 根本没被调用，用例 3 自带挂在 gate 上的流。
        return LoveBrainViewModel(
            deepSeekRepo = deepSeekRepo,
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = topicRecorder,
            securePrefs = securePrefs,
            triggerCoordinator = triggerCoordinator,
            generationEngine = generationEngine,
            operationCoordinator = ForegroundOperationCoordinator(
                CoroutineScope(kotlinx.coroutines.SupervisorJob())
            )
        )
    }
}
