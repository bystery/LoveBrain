package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.feature.rewrite.RewriteStore
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.ReplyStarted
import com.lovebrain.app.model.ReplyRequested
import com.lovebrain.app.model.SchemeFeedback
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "store 交出来的效果 → ViewModel 真的落到结果上"这条接缝。
 *
 * §5.2 第 5 步把改写搬进 [RewriteStore] 之后，"贴正文 / 恢复旧版 / 计数 / 停任务"
 * 全部改由 [LoveBrainViewModel.onRewriteEffect] 落地。这一段以前写在协程体里，
 * 要验证就得开真机点一次改写；现在它是显式效果，直接喂进去看结果。
 *
 * 只在没有别的办法证明时才对 store 内部动作断言：这里断言的全是**用户能看见的东西**——
 * 结果卡上的正文、那条卡的赞踩、累计次数、以及"停掉的是哪一个任务"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RewriteEffectWiringTest {

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

    private val prefs = mockk<SecurePrefs>(relaxed = true).also {
        every { it.thinkingMode } returns 0
        every { it.outputMode } returns 0
        every { it.panelMode } returns 0
        every { it.counselingDraft } returns ""
        every { it.loadCounselingResult() } returns null
        every { it.loadSuggestion() } returns null
        every { it.loadTodayCost() } returns null
        every { it.getWorkerTickets() } returns emptyList()
        every { it.activeTicketId } returns null
        every { it.totalRewriteCount } returns 0
    }

    private fun newViewModel(): LoveBrainViewModel {
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
            operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
            )
        )
    }

    private fun successResult() = GenerateResult.Success(
        LoveBrainResponse(
            response = ReplySchemes(
                recommended = "原推荐", badBoy = "原清醒", playful = "原俏皮", warm = "原温和"
            ),
            directions = listOf("F 原", "E 原", "X 原", "S 原")
        )
    )

    /** 生产路径：认领请求 → 流式开始 → 完成，结果卡在四张方案卡上 */
    private fun vmWithResult(): LoveBrainViewModel = newViewModel().also {
        it.dispatchReply(ReplyRequested("r1"))
        it.dispatchReply(ReplyStarted("r1"))
        it.dispatchReply(ReplyCompleted("r1", successResult()))
    }

    /**
     * 结果卡内容。
     *
     * `vm.result` 是 `stateIn` 派生出来的，StandardTestDispatcher 下不推进调度器它就不更新
     * ——所以读之前先 settle()，否则读到的是上一帧的 null。
     */
    private fun TestScope.schemes(vm: LoveBrainViewModel): LoveBrainResponse {
        advanceUntilIdle()
        return (vm.result.value as GenerateResult.Success).response
    }

    @Test
    fun `ApplyRewrite replaces exactly the target style card`() = runTest(testDispatcher) {
        val vm = vmWithResult()
        vm.onRewriteEffect(RewriteStore.Effect.ApplyRewrite("STYLE:A", "短一点的推荐"))

        advanceUntilIdle()
        val after = schemes(vm)
        assertEquals("短一点的推荐", after.response.recommended)
        assertEquals("别的风格卡一个字都不许动", "原清醒", after.response.badBoy)
        assertEquals("原俏皮", after.response.playful)
        assertEquals(listOf("F 原", "E 原", "X 原", "S 原"), after.directions)
    }

    @Test
    fun `ApplyRewrite on a direction card replaces that direction only`() = runTest(testDispatcher) {
        val vm = vmWithResult()
        vm.onRewriteEffect(RewriteStore.Effect.ApplyRewrite("DIRECTION:E", "换一句展开"))

        advanceUntilIdle()
        val after = schemes(vm).directions
        assertEquals(listOf("F 原", "换一句展开", "X 原", "S 原"), after)
        assertEquals("STYLE 卡不受影响", "原推荐", schemes(vm).response.recommended)
    }

    @Test
    fun `a rewrite result with no live answer patches nothing and throws nothing`() = runTest(testDispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        assertEquals(null, vm.result.value)

        vm.onRewriteEffect(RewriteStore.Effect.ApplyRewrite("STYLE:A", "无处可贴的正文"))
        advanceUntilIdle()
        assertEquals("没有结果时不得凭空造一个结果", null, vm.result.value)

        vm.onRewriteEffect(RewriteStore.Effect.ApplyRewrite("nonsense-key", "更贴不上去的正文"))
        advanceUntilIdle()
        assertEquals(null, vm.result.value)
    }

    @Test
    fun `an unparsable identity key does not silently retarget another card`() = runTest(testDispatcher) {
        val vm = vmWithResult()
        vm.onRewriteEffect(RewriteStore.Effect.ApplyRewrite("WAT:X", "贴错卡片的正文"))
        advanceUntilIdle()

        assertEquals("原推荐", schemes(vm).response.recommended)
        assertEquals(listOf("F 原", "E 原", "X 原", "S 原"), schemes(vm).directions)
    }

    @Test
    fun `the new text starts with no feedback of its own`() = runTest(testDispatcher) {
        val vm = vmWithResult()
        vm.setFeedback("STYLE:A", SchemeFeedback.LIKED)
        assertEquals(SchemeFeedback.LIKED, vm.feedbacks.value["STYLE:A"])

        vm.onRewriteEffect(RewriteStore.Effect.ApplyRewrite("STYLE:A", "新正文"))
        vm.onRewriteEffect(RewriteStore.Effect.ResetFeedback("STYLE:A"))

        assertEquals("新正文不自动继承旧版的赞", SchemeFeedback.NONE, vm.feedbacks.value["STYLE:A"])
    }

    @Test
    fun `RestoreVersion puts the old text and its feedback back`() = runTest(testDispatcher) {
        val vm = vmWithResult()
        vm.setFeedback("STYLE:A", SchemeFeedback.DISLIKED)
        vm.onRewriteEffect(RewriteStore.Effect.ApplyRewrite("STYLE:A", "新正文"))
        vm.onRewriteEffect(RewriteStore.Effect.ResetFeedback("STYLE:A"))

        vm.onRewriteEffect(
            RewriteStore.Effect.RestoreVersion("STYLE:A", "原推荐", SchemeFeedback.DISLIKED)
        )
        advanceUntilIdle()

        assertEquals("原推荐", schemes(vm).response.recommended)
        assertEquals(
            "撤销要连反馈一起回来",
            SchemeFeedback.DISLIKED, vm.feedbacks.value["STYLE:A"]
        )
    }

    @Test
    fun `counting a rewrite writes the running total once`() = runTest(testDispatcher) {
        val vm = vmWithResult()
        assertEquals(0, vm.totalRewriteCount.value)

        vm.onRewriteEffect(RewriteStore.Effect.RewriteCounted)
        assertEquals(1, vm.totalRewriteCount.value)
        verify(exactly = 1) { prefs.totalRewriteCount = 1 }

        vm.onRewriteEffect(RewriteStore.Effect.RewriteCounted)
        assertEquals(2, vm.totalRewriteCount.value)
    }

    @Test
    fun `stopping someone else's rewrite request stops nothing`() = runTest(testDispatcher) {
        val vm = vmWithResult()
        // 没有 REWRITE 在跑时，两个分支都必须是"什么都不发生"，且不能崩
        vm.onRewriteEffect(RewriteStore.Effect.StopRunningRewrite("not-running"))
        vm.onRewriteEffect(RewriteStore.Effect.StopRunningRewrite(""))
        advanceUntilIdle()
        assertEquals("原推荐", schemes(vm).response.recommended)
    }
}
