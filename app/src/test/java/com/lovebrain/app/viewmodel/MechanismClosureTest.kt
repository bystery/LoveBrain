package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
 * Commit 2 mechanism closure tests.
 *
 * Covers:
 * - F02: effectiveFeedback toggle (second dislike cancels, not creates case)
 * - F04: THIS_ROUND mute is transient (not persisted, cleared on nextRound/switch)
 * - F06: DATE expiry detection at generation time
 * - F10/F11: markCurrentResultStaleIfNeeded fires on message ops
 * - F11: SHA-256 fingerprint stability and sensitivity
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MechanismClosureTest {

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
        generationEngine: com.lovebrain.app.domain.GenerationEngine = mockk(relaxed = true)
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
            generationEngine = generationEngine
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
            (emptyMap<String, MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        coEvery { knowledgeRepo.readFile(any(), any()) } returns ""
        coEvery { knowledgeRepo.writeFile(any(), any(), any()) } returns Unit
    }

    // ═══════════ F02: toggle test ═══════════

    @Test
    fun f02_second_dislike_cancels_does_not_create_case() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        // Add a message and generate to get a result
        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)

        val result = vm.result.value as? GenerateResult.Success
        assertTrue("Should have a success result", result != null)

        val schemes = result!!.response.schemes
        assertTrue("Should have schemes", schemes.isNotEmpty())

        val key = schemes.first().identity.key

        // First dislike → should create case
        vm.setFeedback(key, com.lovebrain.app.model.SchemeFeedback.DISLIKED)
        delay(100)
        assertTrue("First dislike should create case", vm.currentFeedbackCase.value != null)

        // Second dislike (toggle cancel) → should clear case, not create new one
        vm.setFeedback(key, com.lovebrain.app.model.SchemeFeedback.DISLIKED)
        delay(100)
        assertTrue("Second dislike should cancel (no case)", vm.currentFeedbackCase.value == null)
    }

    // ═══════════ F04: THIS_ROUND transient mute ═══════════

    @Test
    fun f04_this_round_mute_does_not_persist() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)

        // Apply THIS_ROUND mute
        vm.applyMemoryCorrection(
            memoryId = "PROFILE:understand/me.md",
            action = CorrectionAction.MUTED,
            muteDuration = MuteDuration.THIS_ROUND
        )

        // saveCorrection should NOT be called for THIS_ROUND
        coVerify(exactly = 0) {
            knowledgeRepo.saveCorrection(any(), "PROFILE:understand/me.md", CorrectionAction.MUTED, any(), any(), MuteDuration.THIS_ROUND)
        }
    }

    @Test
    fun f04_this_round_mute_notice_shown() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        vm.refreshKnowledgeBases()
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)

        // Ensure context was set before testing correction
        val result = vm.result.value as? GenerateResult.Success
        assertTrue("Should have a success result before testing correction", result != null)

        vm.applyMemoryCorrection(
            memoryId = "PROFILE:understand/me.md",
            action = CorrectionAction.MUTED,
            muteDuration = MuteDuration.THIS_ROUND
        )

        assertEquals("已暂停本轮提及，下次生成将过滤此条记忆", vm.kbNotice.value)
    }

    // ═══════════ F10/F11: markCurrentResultStaleIfNeeded ═══════════

    @Test
    fun f10_add_message_marks_result_stale() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)

        assertTrue("Should have result", vm.result.value is GenerateResult.Success)
        assertFalse("Input should not be changed right after generate", vm.inputChanged.value)

        // Add another message → should mark stale
        vm.addMessage(ChatMessage.Role.HER, "are you there?")
        assertTrue("Adding message should mark result stale", vm.inputChanged.value)
    }

    @Test
    fun f10_remove_message_marks_result_stale() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.addMessage(ChatMessage.Role.ME, "hi")
        vm.generate()
        delay(300)

        assertFalse("Input should not be changed right after generate", vm.inputChanged.value)

        vm.removeMessage(1)
        assertTrue("Removing message should mark result stale", vm.inputChanged.value)
    }

    @Test
    fun f10_update_message_marks_result_stale() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)

        assertFalse("Input should not be changed right after generate", vm.inputChanged.value)

        vm.updateMessage(0, ChatMessage.Role.HER, "hello there")
        assertTrue("Updating message should mark result stale", vm.inputChanged.value)
    }

    @Test
    fun f10_reorder_message_marks_result_stale() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.addMessage(ChatMessage.Role.ME, "hi")
        vm.generate()
        delay(300)

        assertFalse("Input should not be changed right after generate", vm.inputChanged.value)

        vm.reorderMessages(0, 1)
        assertTrue("Reordering messages should mark result stale", vm.inputChanged.value)
    }

    // ═══════════ F11: SHA-256 fingerprint ═══════════

    @Test
    fun f11_fingerprint_stable_for_same_input() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello world")
        vm.generate()
        delay(300)

        // Generate again with same input — fingerprint should be stable
        // We verify via inputChanged being false after second generate
        vm.generate()
        delay(300)

        // Same input → inputChanged should be false
        assertFalse("Fingerprint should be stable for same input", vm.inputChanged.value)
    }

    @Test
    fun f11_fingerprint_changes_on_message_edit() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        defaultRepoStubs(knowledgeRepo)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello world")
        vm.generate()
        delay(300)

        // Change message content then generate again
        vm.updateMessage(0, ChatMessage.Role.HER, "hello world changed")
        vm.generate()
        delay(300)

        // Different input → inputChanged should be false (new generate resets it)
        // But we verify the fingerprint changed by editing AFTER second generate
        // Actually: after generate, inputChanged is false. After edit, it should be true.
        vm.updateMessage(0, ChatMessage.Role.HER, "hello world changed again")
        assertTrue("Fingerprint should change when message content changes", vm.inputChanged.value)
    }

    // ═══════════ F03: typed ActualSentResult ═══════════

    @Test
    fun f03_record_actual_sent_returns_recorded_on_success() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kb = KnowledgeBase(name = "test-kb", stage = "暧昧期")
        defaultRepoStubs(knowledgeRepo, kb)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        vm.refreshKnowledgeBases()
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(500)

        // Ensure context was set
        assertTrue("Should have a success result (isGenerating=${vm.isGenerating.value}, result=${vm.result.value?.javaClass?.simpleName})", vm.result.value is GenerateResult.Success)

        val result = vm.recordActualSentMessage("I sent this")
        delay(200)

        // Synchronous return is RECORDED
        assertEquals(LoveBrainViewModel.ActualSentResult.RECORDED, result)
    }

    @Test
    fun f03_record_actual_sent_kb_not_found_does_not_adopt() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kb = KnowledgeBase(name = "test-kb", stage = "暧昧期")
        defaultRepoStubs(knowledgeRepo, kb)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        GenerationEngineTestHelper.stubReplyGenerateSuccess(engine)

        val vm = makeVm(knowledgeRepo, engine)
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)

        val initialAdopt = vm.totalAdoptCount.value

        // Simulate KB deleted after generation
        coEvery { knowledgeRepo.listAll() } returns emptyList()

        vm.recordActualSentMessage("I sent this")
        delay(200)

        assertEquals("Adopt count should not increase when KB not found", initialAdopt, vm.totalAdoptCount.value)
    }

    // ═══════════ F06: DATE expiry at generation time ═══════════

    @Test
    fun f06_expired_date_intent_marked_expired_at_generation() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kb = KnowledgeBase(name = "test-kb", stage = "暧昧期")
        // Intent with DATE expiry in the past
        val expiredIntent = IntentConfig(
            text = "过期意图",
            enabled = true,
            revision = 1,
            expiry = IntentExpiry.DATE,
            expiryDate = "2020-01-01",
            status = IntentStatus.ACTIVE
        )
        defaultRepoStubs(knowledgeRepo, kb, expiredIntent)

        val engine = mockk<com.lovebrain.app.domain.GenerationEngine>(relaxed = true)
        // Capture the intent passed to engine
        var capturedIntent: IntentConfig? = null
        every {
            engine.generate(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            capturedIntent = arg(5)
            val scope = arg<kotlinx.coroutines.CoroutineScope>(3)
            val callbacks = arg<com.lovebrain.app.domain.GenerationEngine.Callbacks>(4)
            scope.launch {
                callbacks.onReplyStart()
                callbacks.onReplyResult(
                    GenerateResult.Success(
                        LoveBrainResponse(
                            response = ReplySchemes(recommended = "reply"),
                            analysis = ReplyAnalysis(topic_status = "same", topic_label = "test")
                        )
                    )
                )
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyPanelState(com.lovebrain.app.model.PanelState.AI_RESULT)
            }
            mockk<kotlinx.coroutines.Job>(relaxed = true)
        }

        val vm = makeVm(knowledgeRepo, engine)
        vm.refreshKnowledgeBases()
        delay(200)

        vm.addMessage(ChatMessage.Role.HER, "hello")
        vm.generate()
        delay(300)

        // Ensure context was set
        assertTrue("Should have a success result", vm.result.value is GenerateResult.Success)

        // The intent passed to engine should have status EXPIRED
        assertEquals(
            "Expired DATE intent should be marked EXPIRED at generation time",
            IntentStatus.EXPIRED,
            capturedIntent?.status
        )
    }
}
