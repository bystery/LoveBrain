package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P0-RC: Generation rollback production-path tests.
 *
 * Covers:
 * 1. v1 -> v2 -> v3, rollback -> v2, generate v4, rollback -> v2 — proves v3 never reappears
 * 2. Generate v1, modify message, generate v2, modify message, rollback -> v1, inputChanged=true
 * 3. KB A has history, switch to B, stale result, canRollbackGeneration=false
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenerationRollbackTest {

    private lateinit var prefs: SecurePrefs
    private val testDispatcher = UnconfinedTestDispatcher()

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

    private fun makeVm(
        knowledgeRepo: KnowledgeRepository,
        engine: com.lovebrain.app.domain.GenerationEngine = mockk(relaxed = true)
    ): LoveBrainViewModel {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        every { prefs.totalAdoptCount } returns 0
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            PromptBuilder.ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = engine
        )
    }

    private fun defaultRepoStubs(
        knowledgeRepo: KnowledgeRepository,
        kb: KnowledgeBase = KnowledgeBase(name = "test-kb", stage = "暧昧期"),
        intent: IntentConfig = IntentConfig()
    ) {
        coEvery { knowledgeRepo.getActive() } returns kb
        coEvery { knowledgeRepo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent(any()) } returns intent
        coEvery { knowledgeRepo.listAll() } returns listOf(kb)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        coEvery { knowledgeRepo.readFile(any(), any()) } returns ""
        coEvery { knowledgeRepo.writeFile(any(), any(), any()) } returns Unit
    }

    private fun makeResponse(text: String): LoveBrainResponse {
        return LoveBrainResponse(
            response = ReplySchemes(recommended = text),
            analysis = ReplyAnalysis(topic_status = "same", topic_label = "test")
        )
    }

    /**
     * Test 1: v1 -> v2 -> v3, rollback -> v2, generate v4, rollback -> v2
     * Proves that v3 is permanently discarded and never reappears.
     */
    @Test
    fun rollback_removes_current_not_previous_and_never_reappears() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)

        var generateCount = 0
        val responses = listOf(
            makeResponse("v1-reply"),
            makeResponse("v2-reply"),
            makeResponse("v3-reply"),
            makeResponse("v4-reply")
        )

        every {
            engine.generate(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val scope = arg<CoroutineScope>(3)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(4)
            val resp = responses[generateCount.coerceAtMost(responses.lastIndex)]
            generateCount++
            scope.launch {
                callbacks.onReplyStart()
                callbacks.onReplyResult(GenerateResult.Success(resp))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyPanelState(com.lovebrain.app.model.PanelState.AI_RESULT)
            }
        }

        val vm = makeVm(knowledgeRepo, engine)
        vm.refreshKnowledgeBases()
        delay(200)

        // Generate v1
        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)
        val r1 = vm.result.value as GenerateResult.Success
        assertEquals("v1 reply", "v1-reply", r1.response.schemes[0].reply)
        val v1VersionId = vm.currentVersionId.value
        assertTrue("Should have v1 version id", v1VersionId != null)
        assertEquals("History size after v1", 1, vm.generationHistorySize)

        // Generate v2 (same input — just regenerate)
        vm.generate()
        delay(300)
        val r2 = vm.result.value as GenerateResult.Success
        assertEquals("v2 reply", "v2-reply", r2.response.schemes[0].reply)
        val v2VersionId = vm.currentVersionId.value
        assertNotEquals("v2 version differs from v1", v1VersionId, v2VersionId)
        assertEquals("History size after v2", 2, vm.generationHistorySize)

        // Generate v3
        vm.generate()
        delay(300)
        val r3 = vm.result.value as GenerateResult.Success
        assertEquals("v3 reply", "v3-reply", r3.response.schemes[0].reply)
        val v3VersionId = vm.currentVersionId.value
        assertNotEquals("v3 version differs from v2", v2VersionId, v3VersionId)
        assertEquals("History size after v3", 3, vm.generationHistorySize)

        // Rollback: should remove v3, restore v2
        assertTrue("Can rollback", vm.canRollbackGeneration)
        vm.rollbackToPreviousGeneration()
        delay(100)

        val rAfterRollback = vm.result.value as GenerateResult.Success
        assertEquals("After rollback should show v2", "v2-reply", rAfterRollback.response.schemes[0].reply)
        assertEquals("VersionId should be v2", v2VersionId, vm.currentVersionId.value)
        assertEquals("History size after rollback", 2, vm.generationHistorySize)

        // Generate v4 (based on v2)
        vm.generate()
        delay(300)
        val r4 = vm.result.value as GenerateResult.Success
        assertEquals("v4 reply", "v4-reply", r4.response.schemes[0].reply)
        val v4VersionId = vm.currentVersionId.value
        assertNotEquals("v4 differs from v3", v3VersionId, v4VersionId)
        assertNotEquals("v4 differs from v2", v2VersionId, v4VersionId)
        assertEquals("History size after v4", 3, vm.generationHistorySize)

        // Rollback again: should remove v4, restore v2 (NOT v3!)
        vm.rollbackToPreviousGeneration()
        delay(100)

        val rFinal = vm.result.value as GenerateResult.Success
        assertEquals("Final rollback should show v2 again", "v2-reply", rFinal.response.schemes[0].reply)
        assertEquals("VersionId should be v2 again", v2VersionId, vm.currentVersionId.value)
        assertEquals("History size after final rollback", 2, vm.generationHistorySize)

        // Verify v3 is gone for good
        assertNotEquals("v3 must not be current", v3VersionId, vm.currentVersionId.value)
    }

    /**
     * Test 2: Generate v1, modify message, generate v2, modify message,
     * rollback -> v1, inputChanged should be true because current input differs from v1's context.
     */
    @Test
    fun rollback_with_changed_input_marks_stale() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)

        var generateCount = 0
        val responses = listOf(makeResponse("v1-reply"), makeResponse("v2-reply"))

        every {
            engine.generate(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val scope = arg<CoroutineScope>(3)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(4)
            val resp = responses[generateCount.coerceAtMost(responses.lastIndex)]
            generateCount++
            scope.launch {
                callbacks.onReplyStart()
                callbacks.onReplyResult(GenerateResult.Success(resp))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyPanelState(com.lovebrain.app.model.PanelState.AI_RESULT)
            }
        }

        val vm = makeVm(knowledgeRepo, engine)
        vm.refreshKnowledgeBases()
        delay(200)

        // Generate v1 with "hello"
        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)
        assertFalse("Input not changed right after v1", vm.inputChanged.value)

        // Modify message and generate v2
        vm.updateMessage(0, ChatMessage.Role.HER, "hello there")
        vm.generate()
        delay(300)
        assertFalse("Input not changed right after v2", vm.inputChanged.value)

        // Modify message again — now input differs from v2's context
        vm.updateMessage(0, ChatMessage.Role.HER, "hello there again")
        assertTrue("Input changed after edit", vm.inputChanged.value)

        // Rollback -> restores v1's context (which had "hello")
        // But current input is "hello there again" -> should be stale
        vm.rollbackToPreviousGeneration()
        delay(100)

        // After rollback, result is v1 but input is different -> inputChanged should be true
        assertTrue("Rollback with different input should be stale", vm.inputChanged.value)

        val rAfterRollback = vm.result.value as GenerateResult.Success
        assertEquals("Should show v1 reply", "v1-reply", rAfterRollback.response.schemes[0].reply)
    }

    /**
     * Test 3: KB A has history, switch to B, stale A result,
     * canRollbackGeneration should be false (KB boundary).
     */
    @Test
    fun rollback_blocked_when_active_kb_differs_from_context_kb() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kbA = KnowledgeBase(name = "kb-a", stage = "暧昧期")
        val kbB = KnowledgeBase(name = "kb-b", stage = "初识期")

        coEvery { knowledgeRepo.getActive() } returns kbA
        coEvery { knowledgeRepo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
        coEvery { knowledgeRepo.listAll() } returns listOf(kbA, kbB)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        coEvery { knowledgeRepo.readFile(any(), any()) } returns ""
        coEvery { knowledgeRepo.writeFile(any(), any(), any()) } returns Unit

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        vm.refreshKnowledgeBases()
        delay(200)

        // Generate v1 and v2 on KB A
        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)
        vm.generate()
        delay(300)

        assertEquals("Should have 2 versions in history", 2, vm.generationHistorySize)
        assertTrue("Can rollback on KB A", vm.canRollbackGeneration)

        // Switch to KB B
        coEvery { knowledgeRepo.getActive() } returns kbB
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("Active KB is now B", "kb-b", vm.activeKb.value?.name)

        // The old result from KB A is still showing (stale)
        // canRollbackGeneration should be false because context.kbName=A != activeKb=B
        assertFalse("Cannot rollback when active KB differs from context KB",
            vm.canRollbackGeneration)

        // Rollback should be a no-op
        vm.rollbackToPreviousGeneration()
        delay(100)

        // Result should still be the v2 from KB A (unchanged)
        val result = vm.result.value as GenerateResult.Success
        assertEquals("Result unchanged after blocked rollback", "reply", result.response.schemes[0].reply)
    }
}
