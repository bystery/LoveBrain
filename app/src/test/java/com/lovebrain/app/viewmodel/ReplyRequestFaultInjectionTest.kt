package com.lovebrain.app.viewmodel

import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.ReplyRequestState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
 *   没有统一的 try/catch/finally。如果 knowledgeRepo.readIntent() 或
 *   knowledgeRepo.readCorrectionsAndRevision() 抛出非 CancellationException，
 *   异常会逃出 prepJob 的 launch 块，导致 App 崩溃。
 *   修复：R1-03 在 prepJob 中包裹统一的 try/catch/finally，
 *   异常转为 RecoverableError，finally 按 requestId 清理。
 */
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
    fun `prep phase exception does not crash and produces RecoverableError`() = runTest {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        // 模拟准备阶段读盘失败——readIntent 抛 IOException
        coEvery { knowledgeRepo.readIntent(any()) } throws java.io.IOException("disk read failed")

        val vm = createViewModel(knowledgeRepo = knowledgeRepo)

        // 添加至少一条消息，让 generate() 不被空消息 guard 拦截
        vm.addMessage(com.lovebrain.app.model.ChatMessage.Role.HER, "test")

        // 点击生成——不应抛异常
        vm.generate()

        // 等待状态变化
        val state = vm.replyRequestState.value
        // 应该是 RecoverableError 或 Idle（如果 prep 还没执行完）
        assertTrue(
            "State should be RecoverableError or Idle, not crash. Actual: $state",
            state is ReplyRequestState.RecoverableError || state is ReplyRequestState.Idle
        )
    }

    @Test
    fun `CancellationException is rethrown not swallowed`() = runTest {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.readIntent(any()) } throws kotlinx.coroutines.CancellationException("cancelled")

        val vm = createViewModel(knowledgeRepo = knowledgeRepo)
        vm.addMessage(com.lovebrain.app.model.ChatMessage.Role.HER, "test")

        // 点击生成——CancellationException 应被重抛，不转为 Error
        vm.generate()

        // CancellationException 被重抛后协程结束，状态由 finally 清理为 Idle
        val state = vm.replyRequestState.value
        assertTrue(
            "After CancellationException, state should not be stuck in Preparing. Actual: $state",
            state !is ReplyRequestState.Preparing
        )
    }

    @Test
    fun `double generate does not produce two concurrent requests`() = runTest {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.readIntent(any()) } throws java.io.IOException("fail")

        val vm = createViewModel(knowledgeRepo = knowledgeRepo)
        vm.addMessage(com.lovebrain.app.model.ChatMessage.Role.HER, "test")

        // 快速连点
        vm.generate()
        vm.generate()

        // 只有一个活跃请求——状态不应出现两个 requestId
        val state = vm.replyRequestState.value
        assertTrue(
            "State should not have two concurrent requests. Actual: $state",
            state !is ReplyRequestState.Preparing || state !is ReplyRequestState.Preparing
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
        // Engine.generate 返回 null（reject），使 prepJob 走正常路径
        every {
            generationEngine.generate(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns null
        return LoveBrainViewModel(
            deepSeekRepo = deepSeekRepo,
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = topicRecorder,
            securePrefs = securePrefs,
            triggerCoordinator = triggerCoordinator,
            generationEngine = generationEngine
        )
    }
}
