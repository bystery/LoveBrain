package com.lovebrain.app.service

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
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyRequestState
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.ReplyStarted
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM 侧等价测试——对应 instrumentation 用例
 * [OverlayGenerateSmokeTest.floatingService_destroyWhileGenerationInFlight_releasesInstanceAndChainStaysUsable]
 * 与
 * [OverlayGenerateSmokeTest.floatingService_repeatedStartAndStop_neverLeaksInstance]。
 *
 * 悬浮窗权限与 Android 14+ 后台 FGS 策略在 instrumentation 下不保证可用，
 * 那两格在设备上可能 Assume.assumeTrue 跳过；即便真跑，它们也只能断
 * 「FloatingService.instance 被释放」这一**服务壳**事实，链路本身是否还能用
 * 要看在途请求有没有被 destroy 打断所有权——那一层是 ViewModel + coordinator 的事，
 * 在 JVM 上用 mockk 就能精确驱动，不需要 system image。
 *
 * 两格各钉一件事：
 * 1. **destroy 等价于 stopGeneration + shutdownAll**——取消在途请求后，用同一个 VM
 *    再发起一次生成必须能正常完成。如果 destroy 打断了所有权却没把状态收干净
 *    （比如 replyRequestState 卡在 Streaming、或 coordinator 表里留了孤儿 Job），
 *    第二次 generate() 要么被互斥拒、要么收不到结果。
 * 2. **反复起停不泄漏**——多轮 generate/stop 之后 coordinator 的活跃操作表必须每轮归零，
 *    不许累积孤儿租约。对应 instrumentation 那格「反复起停不泄漏 instance」。
 *
 * 协程编排沿用本仓库先例（[com.lovebrain.app.viewmodel.ReplyRequestFaultInjectionTest]）：
 * Main 交给 UnconfinedTestDispatcher，前台任务在 coordinator 自己的 scope（Default）上跑，
 * 用真实 delay 等落定。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FloatingServiceChainUsabilityTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 对应 instrumentation「destroy while generation in flight」那一格。
     *
     * 链路读数：第一次生成挂在 gate 上 → stopGeneration 取消 → 状态回到 Idle →
     * 第二次生成走同一条链路并正常完成。如果 destroy / stop 打断了所有权却没把
     * coordinator 表收干净，第二次 generate() 会被互斥矩阵拒（lease == null），
     * 或者 reducer 仍认旧 owner、新结果写不进去。
     */
    @Test
    fun `after generation is cancelled the chain is still usable`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val enteredStream = CompletableDeferred<Unit>()
        val engine = mockk<GenerationEngine>(relaxed = true)

        var callCount = 0
        val successResponse = LoveBrainResponse(
            response = ReplySchemes(recommended = "chain-still-usable"),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "test")
        )
        every { engine.replyStream(any()) } answers {
            val requestId = firstArg<GenerationInput>().requestId
            callCount++
            if (callCount == 1) {
                // 第一次：挂住不结束——模拟「生成在途」
                flow {
                    emit(ReplyStarted(requestId))
                    enteredStream.complete(Unit)
                    gate.await()
                }
            } else {
                // 第二次：正常完成——证明链路仍可用
                flow {
                    emit(ReplyStarted(requestId))
                    emit(ReplyCompleted(requestId, GenerateResult.Success(successResponse)))
                }
            }
        }

        val vm = createViewModel(generationEngine = engine)
        delay(200)
        vm.addMessage(ChatMessage.Role.HER, "在吗")

        // 第一次生成
        vm.generate()
        awaitSignal(enteredStream, "第一次请求应进入 Engine", vm)

        assertTrue(
            "第一次生成应在途。Actual: ${vm.replyRequestState.value}",
            vm.isGenerating.value
        )
        assertEquals(
            "coordinator 应恰有一个 REPLY 操作",
            1,
            vm.operationCoordinator.activeOperations.value
                .count { it.type == ForegroundOperationCoordinator.OperationType.REPLY }
        )

        // destroy 等价：停止在途请求
        vm.stopGeneration()

        // 等状态回到 Idle
        awaitState(vm, "停止后应回到 Idle") { it is ReplyRequestState.Idle }
        assertFalse("停止后不得留在生成中", vm.isGenerating.value)
        assertEquals(
            "停止后 coordinator 活跃操作应归零",
            0,
            vm.operationCoordinator.activeOperations.value.size
        )

        // 释放旧 gate——被取消的旧流即使现在完成，也不该污染状态
        gate.complete(Unit)
        delay(200)

        // 第二次生成——链路必须仍可用
        vm.generate()
        delay(400)

        val result = vm.result.value
        assertNotNull("第二次生成应产出结果，链路仍可用", result)
        assertTrue(
            "第二次生成应是成功结果。Actual: $result",
            result is GenerateResult.Success
        )
        assertEquals(
            "第二次生成结果应带预期文本",
            "chain-still-usable",
            (result as GenerateResult.Success).response.schemes[0].reply
        )
        assertFalse("完成后不得留在生成中", vm.isGenerating.value)
        verify(atLeast = 2) { engine.replyStream(any()) }
    }

    /**
     * 对应 instrumentation「反复起停不泄漏 instance」那一格。
     *
     * 链路读数：每轮 generate → 等进入在途 → stopGeneration → 等 Idle。
     * 每轮结束 coordinator 活跃操作表必须归零——如果 stopCurrent 没把 Job 从
     * records 里摘干净（或 invokeOnCompletion 漏挂），表会越积越多，
     * 到某轮 generate() 会被互斥矩阵拒。
     */
    @Test
    fun `repeated start stop does not leak instances`() = runBlocking {
        val engine = mockk<GenerationEngine>(relaxed = true)
        // 每次都挂住：只有 stopGeneration 能收尾
        every { engine.replyStream(any()) } answers {
            val requestId = firstArg<GenerationInput>().requestId
            flow {
                emit(ReplyStarted(requestId))
                delay(60_000L) // 不会自然结束
            }
        }

        val vm = createViewModel(generationEngine = engine)
        delay(200)
        vm.addMessage(ChatMessage.Role.HER, "在吗")

        repeat(5) { round ->
            vm.generate()

            // 等进入在途
            awaitState(vm, "第 ${round + 1} 轮应进入在途") { it is ReplyRequestState.Streaming }
            assertEquals(
                "第 ${round + 1} 轮：coordinator 应恰有一个 REPLY 操作",
                1,
                vm.operationCoordinator.activeOperations.value
                    .count { it.type == ForegroundOperationCoordinator.OperationType.REPLY }
            )

            // 停止
            vm.stopGeneration()

            // 等回到 Idle
            awaitState(vm, "第 ${round + 1} 轮停止后应回到 Idle") {
                it is ReplyRequestState.Idle
            }
            assertFalse(
                "第 ${round + 1} 轮停止后不得留在生成中",
                vm.isGenerating.value
            )
            assertEquals(
                "第 ${round + 1} 轮停止后 coordinator 活跃操作应归零（不泄漏）",
                0,
                vm.operationCoordinator.activeOperations.value.size
            )
        }

        // 五轮之后：状态仍干净
        assertTrue(
            "五轮之后状态应为 Idle。Actual: ${vm.replyRequestState.value}",
            vm.replyRequestState.value is ReplyRequestState.Idle
        )
        assertEquals(
            "五轮之后 coordinator 应无活跃操作",
            0,
            vm.operationCoordinator.activeOperations.value.size
        )
    }

    // ═══════════════════════ 脚手架 ═══════════════════════

    /**
     * 等真实线程上的工作落定（同 [ReplyRequestFaultInjectionTest.awaitSignal]）。
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

    /** 等 replyRequestState 满足 [predicate]，带超时与状态诊断。 */
    private suspend fun awaitState(
        vm: LoveBrainViewModel,
        what: String,
        timeoutMs: Long = 10_000L,
        predicate: (ReplyRequestState) -> Boolean
    ) {
        var waitedMs = 0L
        while (!predicate(vm.replyRequestState.value) && waitedMs < timeoutMs) {
            delay(50)
            waitedMs += 50
        }
        assertTrue(
            "$what（等待 ${timeoutMs}ms 超时）。Actual: ${vm.replyRequestState.value}",
            predicate(vm.replyRequestState.value)
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
        every { securePrefs.loadTodayCost() } returns null
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
        coEvery { knowledgeRepo.getActive() } returns null
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        return LoveBrainViewModel(
            deepSeekRepo = deepSeekRepo,
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = topicRecorder,
            securePrefs = securePrefs,
            triggerCoordinator = triggerCoordinator,
            generationEngine = generationEngine,
            operationCoordinator = ForegroundOperationCoordinator(
                CoroutineScope(SupervisorJob())
            )
        )
    }
}
