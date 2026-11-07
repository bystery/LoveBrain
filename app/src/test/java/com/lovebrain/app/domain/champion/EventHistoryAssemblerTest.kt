package com.lovebrain.app.domain.champion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 3600 CU 整条历史预算的三态对拍（规格书 §5.2 验收项）：
 * 整条放下 / 放下一条越界 / 单条超限占位句，逐字钉死；
 * 另含 emoji/代理对样例钉死 CU = UTF-16 码元数（String.length）。
 */
class EventHistoryAssemblerTest {

    private fun ev(uid: Long, text: String, role: String = "HER", ts: String = "2026-11-06 10:00") =
        ChampionEvent(uid = uid, role = role, text = text, ts = ts, turn = uid.toInt() + 1)

    @Test
    fun `line format is byte-identical to the ported _line`() {
        assertEquals(
            "m_src[7] | 2026-11-06 10:00 | PARTNER | 周五见？",
            EventHistoryAssembler.line(ev(7, "周五见？"))
        )
        assertEquals(
            "m_src[8] | 2026-11-06 10:00 | USER | 好",
            EventHistoryAssembler.line(ev(8, "好", role = "ME"))
        )
        // 非 HER 角色一律 USER（Python `"PARTNER" if m["role"] == "HER" else "USER"`）
        assertEquals(
            "m_src[9] | 2026-11-06 10:00 | USER | x",
            EventHistoryAssembler.line(ev(9, "x", role = "REPLY"))
        )
    }

    @Test
    fun `all events fit under the budget and keep chronological order`() {
        val events = listOf(ev(0, "第一条"), ev(1, "第二条"), ev(2, "第三条", role = "ME"))
        val r = EventHistoryAssembler.budgetHistory(events)
        assertEquals(
            "m_src[0] | 2026-11-06 10:00 | PARTNER | 第一条\n" +
                "m_src[1] | 2026-11-06 10:00 | PARTNER | 第二条\n" +
                "m_src[2] | 2026-11-06 10:00 | USER | 第三条",
            r.body
        )
        assertTrue(r.usedCu <= EventHistoryAssembler.MAX_EVENT_HISTORY_CU)
        assertTrue(r.dropped.isEmpty())
    }

    @Test
    fun `the newest fitting suffix is kept and older lines are dropped whole`() {
        // 每条行 CU = 行文本长度 + 1；构造：第 3 条放得下、第 2 条放不下
        val long = "很长很长很长很长很长很长很长很长很长的消息"
        val line3 = "m_src[2] | 2026-11-06 10:00 | PARTNER | $long"
        val budget = line3.length + 1   // 恰好只放得下最新一条
        val events = listOf(ev(0, "旧的一"), ev(1, "旧的二"), ev(2, long))
        val r = EventHistoryAssembler.budgetHistory(events, budget)
        assertEquals(line3, r.body)
        assertEquals(budget, r.usedCu)
        assertEquals(listOf(0L, 1L), r.dropped.map { it.uid })
    }

    @Test
    fun `a single oversized event yields the placeholder sentence`() {
        val huge = "超".repeat(EventHistoryAssembler.MAX_EVENT_HISTORY_CU)
        val r = EventHistoryAssembler.budgetHistory(listOf(ev(0, huge)))
        assertEquals(EventHistoryAssembler.OVERSIZED_PLACEHOLDER, r.body)
        assertEquals(listOf(0L), r.dropped.map { it.uid })
    }

    @Test
    fun `empty history gives an empty body`() {
        val r = EventHistoryAssembler.budgetHistory(emptyList())
        assertEquals("", r.body)
        assertEquals(0, r.usedCu)
    }

    @Test
    fun `CU counting is UTF-16 code units including surrogate pairs`() {
        // 😀 = U+1F600，UTF-16 占 2 个码元：String.length = 2（与 Python utf16_len 一致）
        val emojiEvent = ev(0, "😀")
        val line = EventHistoryAssembler.line(emojiEvent)
        val r = EventHistoryAssembler.budgetHistory(listOf(emojiEvent), budget = line.length + 1)
        assertEquals(line, r.body)
        // 行长 2 CU 时预算给 2 → 整条（含 +1）越界 → 占位句
        val r2 = EventHistoryAssembler.budgetHistory(listOf(emojiEvent), budget = line.length)
        assertEquals(EventHistoryAssembler.OVERSIZED_PLACEHOLDER, r2.body)
    }

    @Test
    fun `default budget is 3600`() {
        assertEquals(3600, EventHistoryAssembler.MAX_EVENT_HISTORY_CU)
    }
}
