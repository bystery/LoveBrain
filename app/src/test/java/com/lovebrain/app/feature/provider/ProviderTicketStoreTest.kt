package com.lovebrain.app.feature.provider

import com.lovebrain.app.model.ProviderTicket
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 就绪三条件的唯一判据（第2节第2条"面板不再本地计算就绪态"那半句的主人）。
 *
 * 判据是 **工单在 && 模型非空 && Key 非空**——三条缺任意一条都必须是不就绪。
 * 每一格单独拿掉一条，剩下的两条仍然满足：这样才证明这三条**各自**都在起作用，
 * 而不是"恰好其中一条永远为假"把整格撑成恒假（那种格子删掉另外两条也照样绿）。
 */
class ProviderTicketStoreTest {

    private fun storeWith(
        tickets: List<ProviderTicket>,
        activeId: String?,
        apiKey: String?
    ) = ProviderTicketStore(
        readTickets = { tickets },
        readActiveTicketId = { activeId },
        readApiKey = { apiKey }
    )

    private val ticket = ProviderTicket(
        id = "t-1", name = "主力", baseUrl = "http://127.0.0.1:1", model = "deepseek-chat"
    )

    @Test
    fun `all three conditions met is ready`() = runTest {
        val store = storeWith(listOf(ticket), "t-1", "sk-abcd")
        store.refresh()
        assertEquals(ticket, store.activeTicket.value)
        assertTrue("工单在、模型非空、Key 非空——这才叫就绪", store.ready.value)
    }

    @Test
    fun `blank model is not ready even with a key`() = runTest {
        val store = storeWith(listOf(ticket.copy(model = "   ")), "t-1", "sk-abcd")
        store.refresh()
        assertEquals(
            "模型空白时 activeTicket 仍然有值，所以这一格的红只能来自判据本身",
            ticket.copy(model = "   "), store.activeTicket.value
        )
        assertFalse(store.ready.value)
    }

    @Test
    fun `missing key is not ready even with a model`() = runTest {
        for (key in listOf(null, "", "   ")) {
            val store = storeWith(listOf(ticket), "t-1", key)
            store.refresh()
            assertFalse("Key 实到 ${key?.let { "«$it»" } ?: "null"} 时不得报就绪", store.ready.value)
        }
    }

    @Test
    fun `no active id is not ready`() = runTest {
        for (id in listOf(null, "")) {
            val store = storeWith(listOf(ticket), id, "sk-abcd")
            store.refresh()
            assertNull("没有激活项就不该留着一个工单", store.activeTicket.value)
            assertFalse(store.ready.value)
        }
    }

    @Test
    fun `an active id pointing at a deleted ticket resets instead of keeping the stale value`() = runTest {
        // 先让它就绪一次，再让激活项指向一条已被删掉的工单
        val tickets = mutableListOf(ticket)
        val store = ProviderTicketStore(
            readTickets = { tickets.toList() },
            readActiveTicketId = { "t-1" },
            readApiKey = { "sk-abcd" }
        )
        store.refresh()
        assertTrue("前置条件：这一格得先看到就绪，否则后面那句断言是空的", store.ready.value)

        tickets.clear()
        store.refresh()

        assertNull("读不到工单时不许保留旧值——那会给出一个假的就绪位", store.activeTicket.value)
        assertFalse(store.ready.value)
    }
}
