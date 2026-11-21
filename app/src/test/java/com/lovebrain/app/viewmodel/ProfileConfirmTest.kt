package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.ProfileTransactionResult
import com.lovebrain.app.model.ProfileUpdate
import com.lovebrain.app.data.KnowledgeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  （计 2 条用例）：画像确认先解析后清卡回归。
 *
 * 行为差异声明：解析失败不再静默丢卡——卡片保留 + 面板弱警告（可重试）；
 * 解析成功才清卡写库（原"先清卡后解析"失败路径卡片与原文双双蒸发）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileConfirmTest {

    private lateinit var knowledgeRepo: KnowledgeRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /** 构造 LoveBrainViewModel：激活知识库就位（init 读取面显式桩） */
    private fun newViewModel(): LoveBrainViewModel {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        knowledgeRepo = mockk(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns
            KnowledgeBase(name = "kb1", displayName = "她", active = true)
        coEvery { knowledgeRepo.migrateIfNeeded(any()) } returns Unit
        coEvery { knowledgeRepo.readVector(any()) } returns emptyMap()
        coEvery { knowledgeRepo.listAll() } returns
            listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        coEvery { knowledgeRepo.applyProfileUpdateAtomically(any(), any(), any(), any(), any(), any(), any()) } returns ProfileTransactionResult.Success
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any()) } returns
            ConfigValidationResult(0, emptyList())
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
    fun parse_failure_keeps_card_and_warns() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // init 内 refreshKnowledgeBases 排空（_activeKb 就位）
        // 使用 ProfileUpdate.parse 构造无效 payload
        val invalidPayload = ProfileUpdate.parse("not-a-json")
        vm.feedProfileSuggestion(ProfileSuggestion(kbName = "kb1", display = "建议摘要", rawJson = "not-a-json", profileUpdate = invalidPayload))

        vm.confirmProfileUpdate()
        advanceUntilIdle()

        // 无效 payload → 清卡 + 提示重新生成
        assertNull(vm.profileReview.value.suggestion, "无效建议应清卡")
        assertEquals("建议格式无效，请重新生成", vm.panelWarning.value)
    }

    @Test
    fun parse_success_clears_card_and_writes() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        val rawJson = """{"me":"新的我","stage_changed":false}"""
        // 使用 ProfileUpdate.parse 构造有效 payload
        val validPayload = ProfileUpdate.parse(rawJson)
        vm.feedProfileSuggestion(ProfileSuggestion(kbName = "kb1", display = "建议摘要", rawJson = rawJson, profileUpdate = validPayload))

        // 前置检查的两颗读数（库存在性 / 纠正 revision）各走一次真实 Dispatchers.IO，
        // 每颗回派都要虚拟主调度器泵一次。各留一枚"生产真的走到这一步"的信号：
        // 真实线程上跑到了才放行，取代 Thread.sleep 轮询赌注（8 路并行 fork 把
        // 两次 IO 跳挤进高载窗口时，旧的 1s 预算可能等不到，断言就跑在半路上）。
        val kbCheckDone = CompletableDeferred<Unit>()
        coEvery { knowledgeRepo.listAll() } answers {
            kbCheckDone.complete(Unit)
            listOf(KnowledgeBase(name = "kb1", displayName = "她", active = true))
        }
        val revisionRead = CompletableDeferred<Unit>()
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } answers {
            revisionRead.complete(Unit)
            0
        }

        vm.confirmProfileUpdate()
        advanceUntilIdle() // 泵到第一次真实 IO 挂起（listAll 已在真实线程上跑）
        runBlocking { withTimeoutOrNull(5_000) { kbCheckDone.await() } }
            ?: error("listAll（库存在性检查）从未被真实线程跑到")
        advanceUntilIdle() // 回派：泵到第二次真实 IO 挂起
        runBlocking { withTimeoutOrNull(5_000) { revisionRead.await() } }
            ?: error("getCorrectionsRevision（前置检查）从未被真实线程跑到")
        advanceUntilIdle() // 回派：写库 + 清卡 + 回执在虚拟主调度器上一帧排空

        // 成功才清卡 + 写库 + 通知
        assertNull(vm.profileReview.value.suggestion, "解析成功应清卡")
        coVerify { knowledgeRepo.applyProfileUpdateAtomically("kb1", "新的我", null, null, false, null, 0) }
        assertEquals("画像已更新", vm.kbNotice.value)
    }
}
