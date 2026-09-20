package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F06/P1-D: Intent KB identity race tests.
 *
 * Verifies that fast KB switching does not corrupt intent config:
 * - slow A read + fast B read -> final UI must be B
 * - A editor + switch B + save -> A file updated, B UI not overwritten
 * - B -> A switch back correctly shows A intent
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntentKbIdentityRaceTest {

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

    private fun makeVm(knowledgeRepo: KnowledgeRepository): LoveBrainViewModel {
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
            generationEngine = mockk(relaxed = true)
        )
    }

    @Test
    fun slow_a_fast_b_final_ui_must_be_b() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kbA = KnowledgeBase(name = "kb-a", stage = "stage-a")
        val kbB = KnowledgeBase(name = "kb-b", stage = "stage-b")
        val intentA = IntentConfig(text = "intentA", enabled = true, revision = 1)
        val intentB = IntentConfig(text = "intentB", enabled = true, revision = 1)

        coEvery { knowledgeRepo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { knowledgeRepo.getActive() } returns kbA
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent("kb-a") } returns intentA
        coEvery { knowledgeRepo.readIntent("kb-b") } returns intentB
        coEvery { knowledgeRepo.listAll() } returns listOf(kbA, kbB)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0

        val vm = makeVm(knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("initial kb-a", "kb-a", vm.activeKb.value?.name)
        assertEquals("initial intent A", "intentA", vm.intentConfig.value.text)

        coEvery { knowledgeRepo.getActive() } returns kbB
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("after switch kb-b", "kb-b", vm.activeKb.value?.name)
        assertEquals("after switch intent B", "intentB", vm.intentConfig.value.text)
    }

    @Test
    fun a_editor_switch_b_save_a_does_not_overwrite_b_ui() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kbA = KnowledgeBase(name = "kb-a", stage = "stage-a")
        val kbB = KnowledgeBase(name = "kb-b", stage = "stage-b")
        val intentA = IntentConfig(text = "oldA", enabled = true, revision = 1)
        val intentB = IntentConfig(text = "intentB", enabled = true, revision = 1)
        val savedA = IntentConfig(text = "newA", enabled = true, revision = 2)

        coEvery { knowledgeRepo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { knowledgeRepo.getActive() } returns kbA
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent("kb-a") } returns intentA
        coEvery { knowledgeRepo.readIntent("kb-b") } returns intentB
        coEvery { knowledgeRepo.listAll() } returns listOf(kbA, kbB)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        coEvery { knowledgeRepo.saveIntent("kb-a", any(), any(), any(), any(), any()) } returns savedA

        val vm = makeVm(knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        vm.openIntentEditor()
        assertEquals("editor bound to kb-a", "kb-a", vm.activeKb.value?.name)

        coEvery { knowledgeRepo.getActive() } returns kbB
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("now kb-b", "kb-b", vm.activeKb.value?.name)
        assertEquals("UI intent should be B", "intentB", vm.intentConfig.value.text)

        vm.saveIntent("newA", true, IntentExpiry.UNTIL_DONE)
        delay(200)

        coVerify { knowledgeRepo.saveIntent("kb-a", "newA", true, IntentExpiry.UNTIL_DONE, any(), IntentStatus.ACTIVE) }

        assertEquals("after save A, UI still B", "kb-b", vm.activeKb.value?.name)
        assertEquals("after save A, UI intent still B", "intentB", vm.intentConfig.value.text)
    }

    @Test
    fun b_to_a_switch_back_shows_a_intent() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kbA = KnowledgeBase(name = "kb-a", stage = "stage-a")
        val kbB = KnowledgeBase(name = "kb-b", stage = "stage-b")
        val intentA = IntentConfig(text = "intentA", enabled = true, revision = 1)
        val intentB = IntentConfig(text = "intentB", enabled = true, revision = 1)

        coEvery { knowledgeRepo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { knowledgeRepo.getActive() } returns kbA
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent("kb-a") } returns intentA
        coEvery { knowledgeRepo.readIntent("kb-b") } returns intentB
        coEvery { knowledgeRepo.listAll() } returns listOf(kbA, kbB)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0

        val vm = makeVm(knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("initial kb-a", "kb-a", vm.activeKb.value?.name)
        assertEquals("initial intent A", "intentA", vm.intentConfig.value.text)

        coEvery { knowledgeRepo.getActive() } returns kbB
        vm.refreshKnowledgeBases()
        delay(200)
        assertEquals("switched to kb-b", "kb-b", vm.activeKb.value?.name)
        assertEquals("intent should be B", "intentB", vm.intentConfig.value.text)

        coEvery { knowledgeRepo.getActive() } returns kbA
        vm.refreshKnowledgeBases()
        delay(200)
        assertEquals("switched back to kb-a", "kb-a", vm.activeKb.value?.name)
        assertEquals("intent should restore A", "intentA", vm.intentConfig.value.text)
    }

    @Test
    fun intent_read_exception_does_not_crash_scope() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kbA = KnowledgeBase(name = "kb-a", stage = "stage-a")

        coEvery { knowledgeRepo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { knowledgeRepo.getActive() } returns kbA
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readIntent("kb-a") } throws RuntimeException("disk error")
        coEvery { knowledgeRepo.listAll() } returns listOf(kbA)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0

        val vm = makeVm(knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        assertEquals("kb-a", vm.activeKb.value?.name)
        assertEquals("", vm.intentConfig.value.text)
    }

    /**
     * P1-RC: True race test using CompletableDeferred.
     *
     * Scenario:
     * 1. A readIntent starts and blocks on a gate (simulating slow disk)
     * 2. Switch to B — getActive() returns kbB
     * 3. B refreshKnowledgeBases runs — B readIntent completes immediately
     * 4. B commits: activeKb = B, intentConfig = B
     * 5. Release A gate — A readIntent finally returns intentA
     * 6. A's late result must NOT overwrite B's committed UI
     *
     * Final state: activeKb = B, intentConfig = B (not A)
     */
    @Test
    fun true_race_late_a_does_not_overwrite_committed_b() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        val kbA = KnowledgeBase(name = "kb-a", stage = "stage-a")
        val kbB = KnowledgeBase(name = "kb-b", stage = "stage-b")
        val intentA = IntentConfig(text = "intentA", enabled = true, revision = 1)
        val intentB = IntentConfig(text = "intentB", enabled = true, revision = 1)

        coEvery { knowledgeRepo.ensureInitialKnowledgeBase() } returns Unit
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(kbA, kbB)
        coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns
            (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.getLessonCount(any()) } returns 0

        // Gate to block A's readIntent
        val aGate = CompletableDeferred<Unit>()

        // A's readIntent blocks on the gate
        coEvery { knowledgeRepo.readIntent("kb-a") } coAnswers {
            aGate.await()
            intentA
        }
        // B's readIntent returns immediately
        coEvery { knowledgeRepo.readIntent("kb-b") } returns intentB

        // Phase 1: getActive() returns A — refreshKnowledgeBases starts
        // A's readIntent will block on the gate
        coEvery { knowledgeRepo.getActive() } returns kbA

        val vm = makeVm(knowledgeRepo)
        vm.refreshKnowledgeBases()
        // Let A's coroutine start and block on readIntent
        delay(100)

        // Phase 2: switch to B — getActive() now returns B
        coEvery { knowledgeRepo.getActive() } returns kbB
        vm.refreshKnowledgeBases()
        // Let B's refreshKnowledgeBases complete — B readIntent is instant,
        // B commits: activeKb = B, intentConfig = B
        delay(200)

        // Verify B has committed
        assertEquals("B should be active before A completes", "kb-b", vm.activeKb.value?.name)
        assertEquals("intent should be B before A completes", "intentB", vm.intentConfig.value.text)

        // Phase 3: release A's gate — A's readIntent finally returns intentA
        aGate.complete(Unit)
        delay(200)

        // A's late result must NOT overwrite B's committed UI
        assertEquals("after A completes, active KB must still be B", "kb-b", vm.activeKb.value?.name)
        assertEquals("after A completes, intent must still be B", "intentB", vm.intentConfig.value.text)
        assertFalse("A's intent must not leak", vm.intentConfig.value.text == "intentA")
    }
}
