package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.ReplyStarted
import com.lovebrain.app.model.ReplyRequested
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * P0-3 / P1-2: 真正测试 LoveBrainViewModel.generationRoundId 的稳定性。
 *
 * 测试覆盖：
 * - 初始 roundId
 * - onReplyResult(Success) 递增
 * - onReplyResult(Error) 不递增
 * - rewrite 不变
 * - undo 不变
 * - feedback 不变
 * - 下一轮成功再次递增
 *
 * S2-03 之后 Engine 不再回调 ViewModel，roundId 由 reducer 决定。
 * 因此这里用 [runOneRound] 走真实事件序列（认领请求 → Started → Completed），
 * 而不是从侧面直接写状态——否则测的就不是生产路径了。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenerationRoundIdTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun newViewModel(): LoveBrainViewModel {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null

        val knowledgeRepo = mockk<com.lovebrain.app.data.KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns
            KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns
            listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0

        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            PromptBuilder.ConfigValidationResult(0, 0, emptyList())

        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk<TopicRecorder>(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true),
operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        )
    }

    /** 走一遍 reducer 的真实序列：认领 → 流式 → 完成 */
    private var roundSeq = 0
    private fun LoveBrainViewModel.runOneRound(result: GenerateResult) {
        val requestId = "round-${++roundSeq}"
        dispatchReply(ReplyRequested(requestId))
        dispatchReply(ReplyStarted(requestId))
        dispatchReply(ReplyCompleted(requestId, result))
    }

    private fun makeSuccessResult(): GenerateResult.Success {
        return GenerateResult.Success(
            LoveBrainResponse(
                response = ReplySchemes(recommended = "A reply"),
                directions = listOf("F reply", "E reply", "X reply", "S reply")
            )
        )
    }

    @Test
    fun `initial roundId is 0`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        assertEquals(0, vm.generationRoundId.value)
    }

    @Test
    fun `onReplyResult Success increments roundId`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.runOneRound(makeSuccessResult())
        advanceUntilIdle()

        assertEquals(1, vm.generationRoundId.value)
    }

    @Test
    fun `onReplyResult Error does NOT increment roundId`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.runOneRound(GenerateResult.Error("network error"))
        advanceUntilIdle()

        assertEquals(0, vm.generationRoundId.value)
    }

    @Test
    fun `second successful round increments again`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.runOneRound(makeSuccessResult())
        advanceUntilIdle()
        assertEquals(1, vm.generationRoundId.value)

        vm.runOneRound(makeSuccessResult())
        advanceUntilIdle()
        assertEquals(2, vm.generationRoundId.value)
    }

    @Test
    fun `feedback does NOT change roundId`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.runOneRound(makeSuccessResult())
        advanceUntilIdle()
        val roundAfterReply = vm.generationRoundId.value

        // 模拟用户反馈——不应改变 roundId
        vm.setFeedback("STYLE:A", com.lovebrain.app.model.SchemeFeedback.LIKED)
        advanceUntilIdle()

        assertEquals(roundAfterReply, vm.generationRoundId.value)
    }

    @Test
    fun `undo rewrite does NOT change roundId`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.runOneRound(makeSuccessResult())
        advanceUntilIdle()
        val roundAfterReply = vm.generationRoundId.value

        // 模拟 undo——不应改变 roundId
        vm.undoRewrite("STYLE:A")
        advanceUntilIdle()

        assertEquals(roundAfterReply, vm.generationRoundId.value)
    }

    @Test
    fun `roundId changes when new successful round starts`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        // 第一轮成功
        vm.runOneRound(makeSuccessResult())
        advanceUntilIdle()
        val round1 = vm.generationRoundId.value

        // 第二轮成功
        vm.runOneRound(makeSuccessResult())
        advanceUntilIdle()
        val round2 = vm.generationRoundId.value

        assertNotEquals(round1, round2)
        assertEquals(round1 + 1, round2)
    }
}
