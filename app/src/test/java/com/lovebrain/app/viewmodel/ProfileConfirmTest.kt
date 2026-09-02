package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.data.KnowledgeRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    /** 构造 LoveBrainViewModel：激活知识库就位（init 读取面显式桩） */
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
        knowledgeRepo = mockk(relaxed = true)
        coEvery { knowledgeRepo.getActive() } returns
            KnowledgeBase(name = "kb1", displayName = "她", active = true)
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

    @Test
    fun parse_failure_keeps_card_and_warns() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // init 内 refreshKnowledgeBases 排空（_activeKb 就位）
        vm.onProfileSuggestion("建议摘要", "not-a-json")

        vm.confirmProfileUpdate()
        advanceUntilIdle()

        // 卡片保留（可重试）+ 弱警告置位（固定文案）
        assertNotNull(vm.profileSuggestion.value, "解析失败不得清卡")
        assertEquals("建议解析失败，可重试或忽略", vm.panelWarning.value)
        vm.dispose()
    }

    @Test
    fun parse_success_clears_card_and_writes() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.onProfileSuggestion("建议摘要", """{"me":"新的我","stage_changed":false}""")

        vm.confirmProfileUpdate()
        advanceUntilIdle()
        // 成功才清卡 + 写库 + 通知（withContext(Dispatchers.IO) 走真实线程，轮询等回派虚拟主调度器）
        assertNull(vm.profileSuggestion.value, "解析成功应清卡")
        repeat(100) {
            if (vm.kbNotice.value == null) {
                Thread.sleep(10)
                advanceUntilIdle()
            }
        }
        coVerify { knowledgeRepo.writeFile("kb1", "understand/me.md", "新的我") }
        assertEquals("画像已更新", vm.kbNotice.value)
        vm.dispose()
    }
}
