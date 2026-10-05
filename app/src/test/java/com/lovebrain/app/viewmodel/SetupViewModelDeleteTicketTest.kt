package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ProviderTicket
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 *  回归（ 清偿）：deleteTicket 必须同步清理分条 Key（provider_key_$id）。
 * T5：删除工单后 deleteWorkerApiKey 被调用（激活工单分支），列表与激活态同步清空。
 */
class SetupViewModelDeleteTicketTest {

    @Before
    fun setUp() {
        // deleteTicket 末尾经 L.w 记"工单已删除"——单测环境静态 mock android.util.Log
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /** T5：删除激活工单 → deleteWorkerApiKey 恰被调用一次，工单列表清空 */
    @Test
    fun deleteTicket_clears_worker_api_key() {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        val ticket = ProviderTicket(
            id = "ticket-1",
            name = "DeepSeek",
            baseUrl = "https://api.example.com",
            model = "deepseek-chat"
        )
        every { prefs.getWorkerTickets() } returns listOf(ticket)
        every { prefs.activeTicketId } returns "ticket-1"

        val vm = SetupViewModel(prefs, mockk<DeepSeekRepository>(relaxed = true))
        vm.deleteTicket("ticket-1")

        // 分条 Key 密文必须被清理（ 核心断言）
        verify(exactly = 1) { prefs.deleteWorkerApiKey("ticket-1") }
        // 工单列表清空 + 激活态清空（既有行为不回归）
        verify { prefs.setWorkerTickets(emptyList()) }
        verify { prefs.activeTicketId = null }
        assert(vm.tickets.value.isEmpty())
        assert(vm.activeTicket.value == null)
    }
}
