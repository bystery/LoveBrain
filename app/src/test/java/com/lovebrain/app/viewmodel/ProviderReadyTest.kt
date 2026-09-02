package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.ProviderTicket
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  （计 2 条用例）：供应商就绪态下沉回归。
 *
 * 就绪三条件：工单存在 && 模型非空 && Key 非空（面板本地 remember 计算已删，
 * 判定下沉至 LoveBrainViewModel.refreshTicketState，面板只订阅 providerReady）。
 * 纯 JVM 构造：7 依赖全 mock + Dispatchers.setMain 接管 viewModelScope。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderReadyTest {

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

    /** 构造 LoveBrainViewModel：init 读取面显式桩（合法配置，零警告） */
    private fun newViewModel(ticket: ProviderTicket?, apiKey: String?): LoveBrainViewModel {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns listOfNotNull(ticket)
        every { prefs.activeTicketId } returns ticket?.id
        every { prefs.getWorkerApiKey(any()) } returns apiKey
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = mockk(relaxed = true),
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true)
        )
    }

    @Test
    fun ticket_without_key_not_ready() = runTest {
        val ticket = ProviderTicket(name = "t", baseUrl = "https://x.com", model = "m")
        val vm = newViewModel(ticket, apiKey = null)
        advanceUntilIdle() // init 内 refreshTicketState 排空
        assertFalse(vm.providerReady.value, "有工单无 Key 不得就绪")
        vm.dispose()
    }

    @Test
    fun ticket_with_key_ready() = runTest {
        val ticket = ProviderTicket(name = "t", baseUrl = "https://x.com", model = "m")
        val vm = newViewModel(ticket, apiKey = "sk-test")
        advanceUntilIdle()
        assertTrue(vm.providerReady.value, "工单+模型+Key 齐备应就绪")
        vm.dispose()
    }
}
