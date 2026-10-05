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
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * （工单直出/思考开关·数据面）契约测试（演进表计划 3 条，本文件 4 条）：
 * 老格式兼容（null）/ 新建默认直出 / 翻转落盘 / 老工单继承全局后翻转（ 契约）。
 */
class TicketThinkingSwitchTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Before
    fun setUp() {
        // SetupViewModel 经 L.w 记工单日志——单测环境静态 mock android.util.Log（先例 R6-T5）
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /** 老格式工单 JSON（无 thinkingMode 字段）反序列化为 null——不崩、不误判成思考 */
    @Test
    fun old_ticket_json_deserializes_thinkingMode_null() {
        val t = json.decodeFromString<ProviderTicket>(
            """{"id":"t1","name":"DeepSeek","baseUrl":"https://api.deepseek.com","model":"deepseek-v4-flash"}"""
        )
        assertNull(t.thinkingMode)
    }

    /** 新建供应商显式默认思考模式关闭（0）并落盘（多模型批：models 列表首个 = 当前生效模型） */
    @Test
    fun addTicket_defaults_thinkingMode_to_zero() {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        val vm = SetupViewModel(prefs, mockk<DeepSeekRepository>(relaxed = true))

        vm.addTicket("DeepSeek", "https://api.deepseek.com", listOf("deepseek-v4-flash"), "")

        assertEquals(0, vm.tickets.value.single().thinkingMode)
        assertEquals(listOf("deepseek-v4-flash"), vm.tickets.value.single().models)
        assertEquals("deepseek-v4-flash", vm.tickets.value.single().model)
        verify { prefs.setWorkerTickets(match { it.singleOrNull()?.thinkingMode == 0 }) }
    }

    /** toggleTicketThinking 两态翻转并经 setWorkerTickets 落盘 */
    @Test
    fun toggleTicketThinking_flips_and_persists() {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        val ticket = ProviderTicket(
            id = "t1", name = "DeepSeek", baseUrl = "https://api.deepseek.com",
            model = "deepseek-v4-flash", thinkingMode = 0
        )
        every { prefs.getWorkerTickets() } returns listOf(ticket)
        every { prefs.activeTicketId } returns "t1"
        val vm = SetupViewModel(prefs, mockk<DeepSeekRepository>(relaxed = true))

        vm.toggleTicketThinking("t1")
        assertEquals(1, vm.tickets.value.single().thinkingMode)
        vm.toggleTicketThinking("t1")
        assertEquals(0, vm.tickets.value.single().thinkingMode)
        verify(atLeast = 2) { prefs.setWorkerTickets(any()) }
    }

    /** 老工单（null）先读全局设置作生效态再翻转——继承契约 */
    @Test
    fun toggleTicketThinking_null_inherits_global_then_flips() {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        val ticket = ProviderTicket(
            id = "t1", name = "DeepSeek", baseUrl = "https://api.deepseek.com",
            model = "deepseek-v4-flash", thinkingMode = null
        )
        every { prefs.getWorkerTickets() } returns listOf(ticket)
        every { prefs.activeTicketId } returns "t1"
        every { prefs.thinkingMode } returns 1   // 全局当前 = 思考
        val vm = SetupViewModel(prefs, mockk<DeepSeekRepository>(relaxed = true))

        vm.toggleTicketThinking("t1")
        // 生效态 1（思考）→ 翻转为 0（直出），且把翻转结果写实到工单（不再依赖全局）
        assertEquals(0, vm.tickets.value.single().thinkingMode)
    }

    /** 多模型批契约：设为当前 = 切换该供应商生效模型（model 仍 ∈ models） */
    @Test
    fun setTicketModel_switches_current_model() {
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        val vm = SetupViewModel(prefs, mockk<DeepSeekRepository>(relaxed = true))

        vm.addTicket("本地", "http://127.0.0.1:17766/v1", listOf("glm-5.2-sophnet", "deepseek-v4-flash"), "")
        val id = vm.tickets.value.single().id
        assertEquals("glm-5.2-sophnet", vm.tickets.value.single().model)

        vm.setTicketModel(id, "deepseek-v4-flash")
        assertEquals("deepseek-v4-flash", vm.tickets.value.single().model)
        assertEquals(listOf("glm-5.2-sophnet", "deepseek-v4-flash"), vm.tickets.value.single().models)
    }
}
