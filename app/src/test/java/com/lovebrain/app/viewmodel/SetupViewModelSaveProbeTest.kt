package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ProviderTicket
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * URL-01 + UX-01：SetupViewModel.saveTicketWithProbe 验证逻辑测试。
 *
 * 验证保存前条件：
 * - 新建：名称 + URL + 至少一个模型 + API Key 四项齐全
 * - 编辑：名称 + URL + 至少一个模型；Key 留空保留原 Key
 * - 探测失败时弹窗不关闭、formError 有值
 * - 探测成功时保存完整 endpoint
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelSaveProbeTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun mockPrefs(
        tickets: List<ProviderTicket> = emptyList(),
        activeTicketId: String? = null,
        apiKey: String = "sk-test-key"
    ): SecurePrefs {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.getWorkerTickets() } returns tickets
        every { prefs.activeTicketId } returns activeTicketId
        every { prefs.getWorkerApiKey(any()) } returns apiKey
        every { prefs.thinkingMode } returns 0
        every { prefs.captureEnabled } returns true
        return prefs
    }

    private fun mockRepo(
        probeResult: com.lovebrain.app.data.ConnectionTestResult
    ): DeepSeekRepository {
        val repo = mockk<DeepSeekRepository>(relaxed = true)
        coEvery {
            repo.testConnectionWithProbe(any(), any(), any())
        } returns probeResult
        return repo
    }

    // ═══ UX-01：新建时缺少模型 → 失败 ═══

    @Test
    fun save_new_ticket_without_models_fails() = runTest {
        val vm = SetupViewModel(mockPrefs(), mockRepo(
            com.lovebrain.app.data.ConnectionTestResult(success = true, resolvedUrl = "https://x.com/chat/completions")
        ))

        val result = vm.saveTicketWithProbe(
            ticketId = null,
            name = "Test",
            baseUrl = "https://api.example.com",
            models = emptyList(),
            apiKey = "sk-test",
            thinkingMode = 0
        )

        assertFalse(result)
        assertNotNull(vm.formError.value)
        assertTrue("应提示缺少模型", vm.formError.value!!.contains("模型"))
    }

    // ═══ UX-01：新建时缺少 Key → 失败 ═══

    @Test
    fun save_new_ticket_without_api_key_fails() = runTest {
        val vm = SetupViewModel(mockPrefs(), mockRepo(
            com.lovebrain.app.data.ConnectionTestResult(success = true, resolvedUrl = "https://x.com/chat/completions")
        ))

        val result = vm.saveTicketWithProbe(
            ticketId = null,
            name = "Test",
            baseUrl = "https://api.example.com",
            models = listOf("deepseek-chat"),
            apiKey = "",
            thinkingMode = 0
        )

        assertFalse(result)
        assertNotNull(vm.formError.value)
        assertTrue("应提示缺少 API Key", vm.formError.value!!.contains("API Key"))
    }

    // ═══ UX-01：新建时缺少名称 → 失败 ═══

    @Test
    fun save_new_ticket_without_name_fails() = runTest {
        val vm = SetupViewModel(mockPrefs(), mockRepo(
            com.lovebrain.app.data.ConnectionTestResult(success = true, resolvedUrl = "https://x.com/chat/completions")
        ))

        val result = vm.saveTicketWithProbe(
            ticketId = null,
            name = "",
            baseUrl = "https://api.example.com",
            models = listOf("deepseek-chat"),
            apiKey = "sk-test",
            thinkingMode = 0
        )

        assertFalse(result)
        assertNotNull(vm.formError.value)
        assertTrue("应提示缺少名称", vm.formError.value!!.contains("名称"))
    }

    // ═══ URL-01：探测失败 → 不保存 ═══

    @Test
    fun save_ticket_when_probe_fails_does_not_save() = runTest {
        val prefs = mockPrefs()
        val repo = mockRepo(
            com.lovebrain.app.data.ConnectionTestResult(success = false, message = "API Key 无效，请检查密钥")
        )
        val vm = SetupViewModel(prefs, repo)

        val result = vm.saveTicketWithProbe(
            ticketId = null,
            name = "Test",
            baseUrl = "https://api.example.com",
            models = listOf("deepseek-chat"),
            apiKey = "sk-wrong",
            thinkingMode = 0
        )

        assertFalse(result)
        assertEquals("API Key 无效，请检查密钥", vm.formError.value)
        // 不应保存任何工单
        assertTrue(vm.tickets.value.isEmpty())
    }

    // ═══ URL-01：探测成功 → 保存完整 endpoint ═══

    @Test
    fun save_new_ticket_when_probe_succeeds_saves_resolved_url() = runTest {
        val prefs = mockPrefs()
        val repo = mockRepo(
            com.lovebrain.app.data.ConnectionTestResult(
                success = true,
                resolvedUrl = "https://api.deepseek.com/chat/completions"
            )
        )
        val vm = SetupViewModel(prefs, repo)

        val result = vm.saveTicketWithProbe(
            ticketId = null,
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            models = listOf("deepseek-chat"),
            apiKey = "sk-test",
            thinkingMode = 0
        )

        assertTrue(result)
        assertNull(vm.formError.value)
        assertEquals(1, vm.tickets.value.size)
        val saved = vm.tickets.value.first()
        assertEquals("https://api.deepseek.com/chat/completions", saved.baseUrl)
        assertEquals("deepseek-chat", saved.model)
    }

    // ═══ URL-01：编辑时 Key 留空保留原 Key ═══

    @Test
    fun save_edit_ticket_blank_key_preserves_original() = runTest {
        val existingTicket = ProviderTicket(
            id = "t1",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1/chat/completions",
            model = "deepseek-chat",
            thinkingMode = 0
        )
        val prefs = mockPrefs(tickets = listOf(existingTicket), activeTicketId = "t1", apiKey = "sk-original")
        val repo = mockRepo(
            com.lovebrain.app.data.ConnectionTestResult(
                success = true,
                resolvedUrl = "https://api.deepseek.com/chat/completions"
            )
        )
        val vm = SetupViewModel(prefs, repo)

        val result = vm.saveTicketWithProbe(
            ticketId = "t1",
            name = "DeepSeek Updated",
            baseUrl = "https://api.deepseek.com",
            models = listOf("deepseek-chat", "deepseek-reasoner"),
            apiKey = "",  // 留空 → 保留原 Key
            thinkingMode = 0
        )

        assertTrue(result)
        assertNull(vm.formError.value)
        val updated = vm.tickets.value.find { it.id == "t1" }!!
        assertEquals("DeepSeek Updated", updated.name)
        assertEquals("https://api.deepseek.com/chat/completions", updated.baseUrl)
    }

    // ═══ UX-02：providerReady 状态 ═══

    @Test
    fun provider_ready_when_ticket_model_and_key_present() = runTest {
        val ticket = ProviderTicket(
            id = "t1", name = "Test", baseUrl = "https://x.com",
            model = "m", thinkingMode = 0
        )
        val prefs = mockPrefs(tickets = listOf(ticket), activeTicketId = "t1", apiKey = "sk-test")
        val vm = SetupViewModel(prefs, mockk(relaxed = true))

        assertTrue("工单 + 模型 + Key 齐备应 Ready", vm.providerReady.value)
    }

    @Test
    fun provider_not_ready_when_key_missing() {
        val ticket = ProviderTicket(
            id = "t1", name = "Test", baseUrl = "https://x.com",
            model = "m", thinkingMode = 0
        )
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.getWorkerTickets() } returns listOf(ticket)
        every { prefs.activeTicketId } returns "t1"
        every { prefs.getWorkerApiKey("t1") } returns null  // 无 Key
        every { prefs.thinkingMode } returns 0
        every { prefs.captureEnabled } returns true

        val vm = SetupViewModel(prefs, mockk(relaxed = true))

        assertFalse("有工单无 Key 不得 Ready", vm.providerReady.value)
    }

    @Test
    fun provider_not_ready_when_no_active_ticket() {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        every { prefs.thinkingMode } returns 0
        every { prefs.captureEnabled } returns true

        val vm = SetupViewModel(prefs, mockk(relaxed = true))

        assertFalse("无激活工单不得 Ready", vm.providerReady.value)
    }
}
