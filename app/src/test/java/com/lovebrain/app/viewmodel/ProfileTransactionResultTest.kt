package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.PreconditionReason
import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.ProfileTransactionResult
import com.lovebrain.app.model.ProfileUpdate
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
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * P0-6: 画像事务失败语义测试。
 *
 * 验证 confirmProfileUpdate 在 4 种 [ProfileTransactionResult] 路径下给出正确的 UI 反馈：
 * 1. Success → 清卡 + "画像已更新"
 * 2. PreconditionFailed(KB_NOT_FOUND) → 清卡 + "原知识库已删除…"
 * 3. PreconditionFailed(REVISION_CONFLICT) → 清卡 + "资料已变化…"
 * 4. RolledBack → 保留卡片 + "画像写入失败，已恢复原数据，可重试"
 * 5. RollbackFailed → 保留卡片 + "画像写入失败且恢复异常，数据可能已损坏…"
 *
 * 关断言差异：
 * - PreconditionFailed 路径清卡（suggestion 失效，不可重试同一条）
 * - RolledBack / RollbackFailed 路径不清卡（suggestion 仍有效，用户可重试）
 * - RollbackFailed 的措辞必须包含"损坏"或"异常"——不可轻描淡写为"已恢复"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTransactionResultTest {

    private lateinit var knowledgeRepo: KnowledgeRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun newViewModel(
        repoOverride: KnowledgeRepository? = null
    ): LoveBrainViewModel {
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

        knowledgeRepo = repoOverride ?: mockk(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns
            KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns
            listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0

        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            ConfigValidationResult(0, 0, emptyList())

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

    private fun makeSuggestion(): ProfileSuggestion {
        val rawJson = """{"me":"新的我","stage_changed":false}"""
        return ProfileSuggestion(
            kbName = "kb1",
            display = "建议摘要",
            rawJson = rawJson,
            profileUpdate = ProfileUpdate.parse(rawJson)
        )
    }

    /** 轮询等待 panelWarning 或 kbNotice 出现（withContext(Dispatchers.IO) 走真实线程） */
    private fun waitForWarning(vm: LoveBrainViewModel, runTest: kotlinx.coroutines.test.TestScope) {
        repeat(100) {
            if (vm.panelWarning.value == null) {
                Thread.sleep(10)
                runTest.advanceUntilIdle()
            }
        }
    }

    private fun waitForNotice(vm: LoveBrainViewModel, runTest: kotlinx.coroutines.test.TestScope) {
        repeat(100) {
            if (vm.kbNotice.value == null) {
                Thread.sleep(10)
                runTest.advanceUntilIdle()
            }
        }
    }

    // ═══ 1. Success → 清卡 + "画像已更新" ═══

    @Test
    fun `Success clears card and shows notice`() = runTest {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { repo.getActive() } returns KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { repo.migrateIfNeeded(any()) } returns Unit
        coEvery { repo.readVector(any()) } returns emptyMap()
        coEvery { repo.listAll() } returns listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { repo.getCorrectionsRevision(any()) } returns 0
        coEvery { repo.applyProfileUpdateAtomically(any(), any(), any(), any(), any(), any(), any()) } returns
            ProfileTransactionResult.Success

        val vm = newViewModel(repoOverride = repo)
        advanceUntilIdle()

        vm.onProfileSuggestion(makeSuggestion())
        vm.confirmProfileUpdate()
        waitForNotice(vm, this)

        assertNull(vm.profileSuggestion.value, "Success 应清卡")
        assertEquals("画像已更新", vm.kbNotice.value)
    }

    // ═══ 2. PreconditionFailed(KB_NOT_FOUND) → 清卡 + "原知识库已删除…" ═══

    @Test
    fun `PreconditionFailed KB_NOT_FOUND clears card and warns`() = runTest {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { repo.getActive() } returns KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { repo.migrateIfNeeded(any()) } returns Unit
        coEvery { repo.readVector(any()) } returns emptyMap()
        coEvery { repo.listAll() } returns listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { repo.getCorrectionsRevision(any()) } returns 0
        coEvery { repo.applyProfileUpdateAtomically(any(), any(), any(), any(), any(), any(), any()) } returns
            ProfileTransactionResult.PreconditionFailed(PreconditionReason.KB_NOT_FOUND)

        val vm = newViewModel(repoOverride = repo)
        advanceUntilIdle()

        vm.onProfileSuggestion(makeSuggestion())
        vm.confirmProfileUpdate()
        waitForWarning(vm, this)

        assertNull(vm.profileSuggestion.value, "KB 不存在应清卡")
        assertEquals("原知识库已删除，这条画像建议已失效", vm.panelWarning.value)
    }

    // ═══ 3. PreconditionFailed(REVISION_CONFLICT) → 清卡 + "资料已变化…" ═══

    @Test
    fun `PreconditionFailed REVISION_CONFLICT clears card and warns`() = runTest {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { repo.getActive() } returns KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { repo.migrateIfNeeded(any()) } returns Unit
        coEvery { repo.readVector(any()) } returns emptyMap()
        coEvery { repo.listAll() } returns listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { repo.getCorrectionsRevision(any()) } returns 0
        coEvery { repo.applyProfileUpdateAtomically(any(), any(), any(), any(), any(), any(), any()) } returns
            ProfileTransactionResult.PreconditionFailed(PreconditionReason.REVISION_CONFLICT)

        val vm = newViewModel(repoOverride = repo)
        advanceUntilIdle()

        vm.onProfileSuggestion(makeSuggestion())
        vm.confirmProfileUpdate()
        waitForWarning(vm, this)

        assertNull(vm.profileSuggestion.value, "Revision 冲突应清卡")
        assertEquals("资料已变化，请重新生成", vm.panelWarning.value)
    }

    // ═══ 4. RolledBack → 保留卡片 + "已恢复原数据，可重试" ═══

    @Test
    fun `RolledBack keeps card and says recovered`() = runTest {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { repo.getActive() } returns KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { repo.migrateIfNeeded(any()) } returns Unit
        coEvery { repo.readVector(any()) } returns emptyMap()
        coEvery { repo.listAll() } returns listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { repo.getCorrectionsRevision(any()) } returns 0
        val writeError = IOException("disk full")
        coEvery { repo.applyProfileUpdateAtomically(any(), any(), any(), any(), any(), any(), any()) } returns
            ProfileTransactionResult.RolledBack(writeError)

        val vm = newViewModel(repoOverride = repo)
        advanceUntilIdle()

        val suggestion = makeSuggestion()
        vm.onProfileSuggestion(suggestion)
        vm.confirmProfileUpdate()
        waitForWarning(vm, this)

        // RolledBack——卡片保留（用户可重试同一条建议）
        assertEquals(suggestion.suggestionId, vm.profileSuggestion.value?.suggestionId,
            "RolledBack 应保留卡片供重试")
        val warning = vm.panelWarning.value
        assertEquals("画像写入失败，已恢复原数据，可重试", warning)
        // 措辞不可包含"损坏"——那是 RollbackFailed 的专属
        assertEquals(false, warning?.contains("损坏"),
            "RolledBack 措辞不应包含'损坏'")
    }

    // ═══ 5. RollbackFailed → 保留卡片 + "数据可能已损坏" ═══

    @Test
    fun `RollbackFailed keeps card and warns data may be corrupted`() = runTest {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { repo.getActive() } returns KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { repo.migrateIfNeeded(any()) } returns Unit
        coEvery { repo.readVector(any()) } returns emptyMap()
        coEvery { repo.listAll() } returns listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { repo.getCorrectionsRevision(any()) } returns 0
        val writeError = IOException("disk I/O error")
        coEvery { repo.applyProfileUpdateAtomically(any(), any(), any(), any(), any(), any(), any()) } returns
            ProfileTransactionResult.RollbackFailed(writeError, listOf("understand/me.md", "understand/warmth.md"))

        val vm = newViewModel(repoOverride = repo)
        advanceUntilIdle()

        val suggestion = makeSuggestion()
        vm.onProfileSuggestion(suggestion)
        vm.confirmProfileUpdate()
        waitForWarning(vm, this)

        // RollbackFailed——卡片保留（但建议可能已不可安全重试）
        assertEquals(suggestion.suggestionId, vm.profileSuggestion.value?.suggestionId,
            "RollbackFailed 应保留卡片")
        val warning = vm.panelWarning.value
        assertNotNull(warning, "RollbackFailed 应有警告")
        assertEquals(true, warning.contains("损坏") || warning.contains("异常"),
            "RollbackFailed 措辞必须包含'损坏'或'异常'——不可轻描淡写为'已恢复'")
        assertEquals(false, warning.contains("已恢复原数据"),
            "RollbackFailed 措辞不可声称'已恢复原数据'")
    }

    // ═══ 6. RollbackFailed 携带 failedPaths 供诊断 ═══

    @Test
    fun `RollbackFailed carries failed paths for diagnostics`() {
        val paths = listOf("understand/me.md", "kb.json")
        val result = ProfileTransactionResult.RollbackFailed(
            IOException("io error"),
            paths
        )
        assertEquals(paths, result.failedPaths)
        assertEquals(true, result.cause is IOException)
    }

    // ═══ 7. PreconditionFailed 区分两种 reason ═══

    @Test
    fun `PreconditionFailed distinguishes KB_NOT_FOUND and REVISION_CONFLICT`() {
        val kbNotFound = ProfileTransactionResult.PreconditionFailed(PreconditionReason.KB_NOT_FOUND)
        val revConflict = ProfileTransactionResult.PreconditionFailed(PreconditionReason.REVISION_CONFLICT)

        assertEquals(PreconditionReason.KB_NOT_FOUND, kbNotFound.reason)
        assertEquals(PreconditionReason.REVISION_CONFLICT, revConflict.reason)
        // 两种 reason 不相等
        assertEquals(false, kbNotFound == revConflict)
    }
}
