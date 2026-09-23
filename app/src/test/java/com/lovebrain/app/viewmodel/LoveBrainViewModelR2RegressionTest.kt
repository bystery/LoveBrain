package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.SuggestStarted
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  VM 级回归（  修订表；计 2 条用例）：
 * - ：锦囊手动停止复用 _suggestError 通道给出"已手动停止"（对齐谈心停止先例，零新状态）
 * - ：editingIndex 修正下沉 VM——同帧连删串行执行永远看最新快照，无 UI 旧快照竞态
 *
 * 纯 JVM 构造沿用 LoveBrainViewModelDraftPersistTest 先例：7 依赖全 mock + setMain 接管。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoveBrainViewModelR2RegressionTest {

    private lateinit var prefs: SecurePrefs

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    /** 构造 LoveBrainViewModel：init 读取面显式桩（同 DraftPersistTest 先例） */
    private fun newViewModel(
        engine: GenerationEngine = mockk(relaxed = true),
        knowledgeRepo: KnowledgeRepository = mockk(relaxed = true)
    ): LoveBrainViewModel {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        // ：init 新增今日花费读取面（relaxed 默认返 Object 会 CCE，显式桩 null）
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            ConfigValidationResult(0, 0, emptyList())
        every { promptBuilder.assetHashOf(*anyVararg()) } returns "asset-hash"
        // S2-02: 任务由 coordinator 创建并拥有——把它挂在测试调度器上，
        // 否则 advanceUntilIdle() 推进不到生成任务里，测的就不是同一条时间线。
        val coordinatorScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.Main
        )
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = knowledgeRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = engine,
            operationCoordinator = com.lovebrain.app.domain.ForegroundOperationCoordinator(coordinatorScope)
        )
    }

    /** ：手动停止锦囊 → 错误面给出固定文案（SuggestPanel 错误卡可见），生成态关闭 */
    @Test
    fun stop_suggest_shows_manual_stop_notice() = runTest {
        // S2-02 之后锦囊是否"在生成"只由 coordinator 的在管任务派生，
        // 所以必须先真发起一个任务，再点停止——不能像旧代码那样直接调回调进生成态。
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns
            KnowledgeBase(name = "kb-a", stage = "暧昧期")
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.readFile(any(), any()) } returns ""

        val gate = CompletableDeferred<Unit>()
        val engine = mockk<GenerationEngine>(relaxed = true)
        every { engine.suggestStream(any(), any()) } answers {
            val requestId = arg<String>(0)
            flow {
                emit(SuggestStarted(requestId))
                gate.await()
            }
        }

        val vm = newViewModel(engine = engine, knowledgeRepo = knowledgeRepo)
        advanceUntilIdle()

        vm.generateSuggest()
        advanceUntilIdle()
        assertTrue("锦囊应处于生成中", vm.isSuggesting.value)

        vm.stopSuggest()
        // isSuggesting 是从 coordinator 的 activeOperations 派生的 flow，
        // 要先让调度器把这一次取消发布出去再读。
        advanceUntilIdle()

        assertEquals("已手动停止", vm.suggestError.value)
        assertEquals(false, vm.isSuggesting.value)
    }

    /** ：编辑中连删两条——先删编辑位之前（下标修正），再删编辑位本身（清零+清草稿） */
    @Test
    fun remove_by_id_corrects_editing_index_under_consecutive_deletes() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.addMessage(ChatMessage.Role.HER, "m0")
        vm.addMessage(ChatMessage.Role.ME, "m1")
        vm.addMessage(ChatMessage.Role.HER, "m2")
        val ids = vm.messages.value.map { it.id }

        vm.setEditingIndex(1)
        vm.setDraft("editing-m1")

        // 删编辑位之前的一条 → 编辑下标前移
        vm.removeMessageById(ids[0])
        assertEquals(0, vm.editingIndex.value)

        // 再删编辑位本身（同一帧内第二次回调）→ 编辑态清零 + 草稿清空
        vm.removeMessageById(ids[1])
        assertEquals(-1, vm.editingIndex.value)
        assertEquals("", vm.draftText.value)
        assertEquals(listOf("m2"), vm.messages.value.map { it.content })
    }
}
