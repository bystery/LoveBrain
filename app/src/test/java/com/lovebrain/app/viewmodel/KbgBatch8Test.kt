package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.ProfileTransactionResult
import com.lovebrain.app.model.ProfileUpdate
import com.lovebrain.app.model.IntentConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 第八批定向测试：KBG-01 / KBG-02 / KBG-03。
 *
 * - KBG-01：删除 KB 后，writeFile/appendFile 不会重新创建目录
 * - KBG-02：画像建议绑定 originating KB，确认时只写目标 KB
 * - KBG-03：后台向量回调携带 kbName，非当前 KB 不更新 UI
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KbgBatch8Test {

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

    // ════════════════════════════════════════════════════════════════
    // KBG-01: 删除 KB 后 writeFile / appendFile 不会复活目录
    // ════════════════════════════════════════════════════════════════

    @Test
    fun kbg01_writeFile_does_not_recreate_deleted_kb() = runBlocking {
        val tmpRoot = File(System.getProperty("java.io.tmpdir"), "kbg01_write_${System.nanoTime()}")
        try {
            val context = mockk<android.content.Context>(relaxed = true)
            val prefs = mockk<SecurePrefs>(relaxed = true)
            val repo = KnowledgeRepository(
                knowledgeRoot = tmpRoot,
                securePrefs = prefs,
                context = context,
                appScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )

            // create KB "A"
            repo.create("a", "KB-A")
            assertTrue(File(tmpRoot, "a/kb.json").exists())

            // delete KB "A"
            repo.delete("a")
            assertFalse(File(tmpRoot, "a").exists())

            // late write attempt should NOT recreate directory
            repo.writeFile("a", "understand/me.md", "content")

            assertFalse("已删除的 KB 目录不应被 writeFile 重新创建", File(tmpRoot, "a").exists())
        } finally {
            tmpRoot.deleteRecursively()
        }
    }

    @Test
    fun kbg01_appendFile_does_not_recreate_deleted_kb() = runBlocking {
        val tmpRoot = File(System.getProperty("java.io.tmpdir"), "kbg01_append_${System.nanoTime()}")
        try {
            val context = mockk<android.content.Context>(relaxed = true)
            val prefs = mockk<SecurePrefs>(relaxed = true)
            val repo = KnowledgeRepository(
                knowledgeRoot = tmpRoot,
                securePrefs = prefs,
                context = context,
                appScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )

            repo.create("a", "KB-A")
            assertTrue(File(tmpRoot, "a/kb.json").exists())

            repo.delete("a")
            assertFalse(File(tmpRoot, "a").exists())

            // late append attempt should NOT recreate directory
            repo.appendFile("a", "memory/lessons.md", "entry")

            assertFalse("已删除的 KB 目录不应被 appendFile 重新创建", File(tmpRoot, "a").exists())
        } finally {
            tmpRoot.deleteRecursively()
        }
    }

    @Test
    fun kbg01_writeVector_does_not_recreate_deleted_kb() = runBlocking {
        val tmpRoot = File(System.getProperty("java.io.tmpdir"), "kbg01_vec_${System.nanoTime()}")
        try {
            val context = mockk<android.content.Context>(relaxed = true)
            val prefs = mockk<SecurePrefs>(relaxed = true)
            val repo = KnowledgeRepository(
                knowledgeRoot = tmpRoot,
                securePrefs = prefs,
                context = context,
                appScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )

            repo.create("a", "KB-A")
            assertTrue(File(tmpRoot, "a/kb.json").exists())

            repo.delete("a")
            assertFalse(File(tmpRoot, "a").exists())

            repo.writeVector("a", mapOf("intimacy" to 60))

            assertFalse("已删除的 KB 目录不应被 writeVector 重新创建", File(tmpRoot, "a").exists())
        } finally {
            tmpRoot.deleteRecursively()
        }
    }

    @Test
    fun kbg01_updateStage_does_not_recreate_deleted_kb() = runBlocking {
        val tmpRoot = File(System.getProperty("java.io.tmpdir"), "kbg01_stage_${System.nanoTime()}")
        try {
            val context = mockk<android.content.Context>(relaxed = true)
            val prefs = mockk<SecurePrefs>(relaxed = true)
            val repo = KnowledgeRepository(
                knowledgeRoot = tmpRoot,
                securePrefs = prefs,
                context = context,
                appScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )

            repo.create("a", "KB-A")
            repo.delete("a")
            assertFalse(File(tmpRoot, "a").exists())

            repo.updateStage("a", "暧昧期")

            assertFalse("已删除的 KB 目录不应被 updateStage 重新创建", File(tmpRoot, "a").exists())
        } finally {
            tmpRoot.deleteRecursively()
        }
    }

    @Test
    fun kbg01_create_not_affected_by_existence_guard() = runBlocking {
        val tmpRoot = File(System.getProperty("java.io.tmpdir"), "kbg01_create_${System.nanoTime()}")
        try {
            val context = mockk<android.content.Context>(relaxed = true)
            val prefs = mockk<SecurePrefs>(relaxed = true)
            val repo = KnowledgeRepository(
                knowledgeRoot = tmpRoot,
                securePrefs = prefs,
                context = context,
                appScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )

            // create should still work (not blocked by existence guard)
            repo.create("fresh", "Fresh KB")
            assertTrue("create() 应正常工作", File(tmpRoot, "fresh/kb.json").exists())
        } finally {
            tmpRoot.deleteRecursively()
        }
    }

    @Test
    fun kbg01_migrate_not_affected_by_existence_guard() = runBlocking {
        val tmpRoot = File(System.getProperty("java.io.tmpdir"), "kbg01_migrate_${System.nanoTime()}")
        try {
            val context = mockk<android.content.Context>(relaxed = true)
            val prefs = mockk<SecurePrefs>(relaxed = true)
            val repo = KnowledgeRepository(
                knowledgeRoot = tmpRoot,
                securePrefs = prefs,
                context = context,
                appScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )

            repo.create("migrate-test", "Migrate Test")
            // migrateIfNeeded should work on existing KB
            repo.migrateIfNeeded("migrate-test")
            assertTrue("migrateIfNeeded() 应正常工作", File(tmpRoot, "migrate-test/kb.json").exists())
        } finally {
            tmpRoot.deleteRecursively()
        }
    }

    @Test
    fun kbg01_write_to_existing_kb_still_works() = runBlocking {
        val tmpRoot = File(System.getProperty("java.io.tmpdir"), "kbg01_ok_${System.nanoTime()}")
        try {
            val context = mockk<android.content.Context>(relaxed = true)
            val prefs = mockk<SecurePrefs>(relaxed = true)
            val repo = KnowledgeRepository(
                knowledgeRoot = tmpRoot,
                securePrefs = prefs,
                context = context,
                appScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )

            repo.create("alive", "Alive KB")
            repo.writeFile("alive", "understand/me.md", "new content")

            val content = repo.readFile("alive", "understand/me.md")
            assertEquals("正常 KB 的 writeFile 应正常工作", "new content", content)
        } finally {
            tmpRoot.deleteRecursively()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // KBG-02: 画像建议绑定 originating KB
    // ════════════════════════════════════════════════════════════════

    private fun newViewModelWithKb(
        kbName: String = "kb-a",
        knowledgeRepoOverride: KnowledgeRepository? = null
    ): LoveBrainViewModel {
        val knowledgeRepo = knowledgeRepoOverride ?: mockk(relaxed = true)
        if (knowledgeRepoOverride == null) {
            coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = kbName, stage = "暧昧期")
            coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
            coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
            coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = kbName, stage = "暧昧期"))
            coEvery { knowledgeRepo.readCorrectionsAndRevision(any()) } returns (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
            coEvery { knowledgeRepo.readIntent(any()) } returns IntentConfig()
            coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
            coEvery { knowledgeRepo.getLessonCount(any()) } returns 0
        }
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
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true),
operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        )
    }

    @Test
    fun kbg02_profile_suggestion_writes_to_originating_kb_not_active() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-a", stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(
            KnowledgeBase(name = "kb-a", stage = "暧昧期"),
            KnowledgeBase(name = "kb-b", stage = "热恋期")
        )
        coEvery { knowledgeRepo.applyProfileUpdateAtomically(any(), any(), any(), any(), any(), any(), any()) } returns ProfileTransactionResult.Success
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0

        val vm = newViewModelWithKb(kbName = "kb-a", knowledgeRepoOverride = knowledgeRepo)
        delay(200)

        // A's suggestion arrives
        val rawJson = """{"me":"A的画像","stage_changed":false}"""
        vm.feedProfileSuggestion(ProfileSuggestion(
            kbName = "kb-a",
            display = "画像建议A",
            rawJson = rawJson,
            profileUpdate = ProfileUpdate.parse(rawJson)
        ))

        // User switches to B
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        vm.refreshKnowledgeBases()
        delay(200)
        assertEquals("kb-b", vm.activeKb.value?.name)

        // User confirms A's suggestion while looking at B
        vm.confirmProfileUpdate()
        delay(500)

        // Should write to A via applyProfileUpdateAtomically, not B
        coVerify { knowledgeRepo.applyProfileUpdateAtomically("kb-a", "A的画像", null, null, false, null, 0) }
    }

    @Test
    fun kbg02_profile_suggestion_kb_deleted_does_not_write_or_recreate() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-a", stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        // A has been deleted → listAll no longer contains A
        coEvery { knowledgeRepo.listAll() } returns emptyList()

        val vm = newViewModelWithKb(kbName = "kb-a", knowledgeRepoOverride = knowledgeRepo)
        delay(200)

        val rawJson = """{"me":"A的画像","stage_changed":false}"""
        vm.feedProfileSuggestion(ProfileSuggestion(
            kbName = "kb-a",
            display = "画像建议A",
            rawJson = rawJson,
            profileUpdate = ProfileUpdate.parse(rawJson)
        ))

        vm.confirmProfileUpdate()
        delay(500)

        // Suggestion cleared
        assertNull("建议应被清理", vm.profileReview.value.suggestion)
        // Warning shown
        assertEquals("原知识库已删除，这条画像建议已失效", vm.panelWarning.value)
        // Nothing written
        coVerify(exactly = 0) { knowledgeRepo.writeFile(any(), any(), any()) }
        coVerify(exactly = 0) { knowledgeRepo.writeVector(any(), any()) }
    }

    @Test
    fun kbg02_profile_confirm_reads_target_kb_vector_not_global_currentVector() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-a", stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        // A's vector on disk
        coEvery { knowledgeRepo.readVector("kb-a") } returns mapOf("intimacy" to 55)
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-a", stage = "暧昧期"))
        coEvery { knowledgeRepo.applyProfileUpdateAtomically(any(), any(), any(), any(), any(), any(), any()) } returns ProfileTransactionResult.Success
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0

        val vm = newViewModelWithKb(kbName = "kb-a", knowledgeRepoOverride = knowledgeRepo)
        delay(200)

        val rawJson = """{"warmth":"new warmth","stage_changed":false}"""
        vm.feedProfileSuggestion(ProfileSuggestion(
            kbName = "kb-a",
            display = "画像建议",
            rawJson = rawJson,
            profileUpdate = ProfileUpdate.parse(rawJson)
        ))

        vm.confirmProfileUpdate()
        delay(500)

        // Should have called applyProfileUpdateAtomically with A's warmth
        coVerify { knowledgeRepo.applyProfileUpdateAtomically("kb-a", null, null, "new warmth", false, null, 0) }
    }

    // ════════════════════════════════════════════════════════════════
    // KBG-03: 向量回调按 kbName 过滤 UI
    // ════════════════════════════════════════════════════════════════

    @Test
    fun kbg03_onCurrentVector_non_active_kb_does_not_update_ui() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-b", stage = "热恋期"))

        val vm = newViewModelWithKb(kbName = "kb-b", knowledgeRepoOverride = knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)
        assertEquals("kb-b", vm.activeKb.value?.name)

        val vectorA = mapOf("intimacy" to 80)
        vm.feedCurrentVector("kb-a", vectorA)

        assertEquals("非当前 KB 的向量不应更新 UI", emptyMap<String, Int>(), vm.currentVector.value)
    }

    @Test
    fun kbg03_onCurrentVector_active_kb_updates_ui() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-b", stage = "热恋期"))

        val vm = newViewModelWithKb(kbName = "kb-b", knowledgeRepoOverride = knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        val vectorB = mapOf("intimacy" to 70)
        vm.feedCurrentVector("kb-b", vectorB)

        assertEquals("当前 KB 的向量应更新 UI", vectorB, vm.currentVector.value)
    }

    @Test
    fun kbg03_onVectorUpdated_non_active_kb_does_not_update_ui() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-b", stage = "热恋期"))

        val vm = newViewModelWithKb(kbName = "kb-b", knowledgeRepoOverride = knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        val vectorA = mapOf("intimacy" to 90)
        val deltaA = mapOf("intimacy" to 10)
        vm.feedVectorUpdated("kb-a", vectorA, deltaA)

        assertEquals("非当前 KB 的向量不应更新 UI", emptyMap<String, Int>(), vm.currentVector.value)
        assertEquals("非当前 KB 的 delta 不应更新 UI", emptyMap<String, Int>(), vm.vectorDelta.value)
    }

    @Test
    fun kbg03_onVectorUpdated_active_kb_updates_ui() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-b", stage = "热恋期"))

        val vm = newViewModelWithKb(kbName = "kb-b", knowledgeRepoOverride = knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        val vectorB = mapOf("intimacy" to 75)
        val deltaB = mapOf("intimacy" to 5)
        vm.feedVectorUpdated("kb-b", vectorB, deltaB)

        assertEquals("当前 KB 的向量应更新 UI", vectorB, vm.currentVector.value)
        assertEquals("当前 KB 的 delta 应更新 UI", deltaB, vm.vectorDelta.value)
    }

    @Test
    fun kbg03_onVectorUpdateNotice_non_active_kb_does_not_update_ui() = runBlocking {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns KnowledgeBase(name = "kb-b", stage = "热恋期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns listOf(KnowledgeBase(name = "kb-b", stage = "热恋期"))

        val vm = newViewModelWithKb(kbName = "kb-b", knowledgeRepoOverride = knowledgeRepo)
        vm.refreshKnowledgeBases()
        delay(200)

        vm.feedVectorSummary("kb-a", "A's vector notice")
        assertNull("非当前 KB 的 notice 不应更新 UI", vm.vectorUpdate.value)
    }
}
