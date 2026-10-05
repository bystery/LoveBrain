package com.lovebrain.app.feature.rewrite

import com.lovebrain.app.model.SchemeFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 版本栈（[RewriteLedger]）的行为基线。
 *
 * 这块原先是 ViewModel 里两个私有 StateFlow + 手写 map 操作，撤销一步要连着
 * "取最后版本 / 丢掉最后一条 / 空了就删 key"三段，只能靠真机点出来。
 * 钉住的正是这三段：漏掉任何一步，撤销就会拿回错的正文或留下洗不净的历史。
 *
 * 卡片状态（Loading/Done/Error）的断言搬去 [RewriteStoreTest] —— 那是 store 的状态，
 * 台账从今往后只记"被改写掉过哪些版本"。
 */
class RewriteLedgerTest {

    @Test
    fun `push remembers the version being replaced`() {
        val ledger = RewriteLedger()
        ledger.push("STYLE:A", RewriteLedger.Version("原文", SchemeFeedback.LIKED))

        assertEquals(1, ledger.historySize("STYLE:A"))
        assertEquals(
            RewriteLedger.Version("原文", SchemeFeedback.LIKED),
            ledger.peekLast("STYLE:A")
        )
    }

    @Test
    fun `the feedback travels with the reply it belonged to`() {
        val ledger = RewriteLedger()
        ledger.push("STYLE:A", RewriteLedger.Version("v1", SchemeFeedback.DISLIKED))
        ledger.push("STYLE:A", RewriteLedger.Version("v2", SchemeFeedback.NONE))

        assertEquals(RewriteLedger.Version("v2", SchemeFeedback.NONE), ledger.pop("STYLE:A"))
        assertEquals(RewriteLedger.Version("v1", SchemeFeedback.DISLIKED), ledger.pop("STYLE:A"))
    }

    @Test
    fun `identities keep separate stacks`() {
        val ledger = RewriteLedger()
        ledger.push("STYLE:A", RewriteLedger.Version("A 原文", SchemeFeedback.NONE))
        ledger.push("DIRECTION:F", RewriteLedger.Version("F 原文", SchemeFeedback.LIKED))

        assertEquals("A 原文", ledger.peekLast("STYLE:A")?.reply)
        ledger.pop("STYLE:A")
        assertFalse(ledger.hasHistory("STYLE:A"))
        assertEquals(1, ledger.historySize("DIRECTION:F"))
    }

    @Test
    fun `popping the last version drops the key instead of leaving an empty list behind`() {
        val ledger = RewriteLedger()
        ledger.push("STYLE:A", RewriteLedger.Version("原文", SchemeFeedback.NONE))

        assertEquals(1, ledger.historySize("STYLE:A"))
        ledger.pop("STYLE:A")
        assertEquals(0, ledger.historySize("STYLE:A"))
        assertFalse("弹空后 key 必须整个消失，不能留 key -> emptyList()", ledger.hasHistory("STYLE:A"))
        assertNull(ledger.peekLast("STYLE:A"))
        assertNull("没有历史时不能再报一个可撤销版本", ledger.pop("STYLE:A"))
    }

    @Test
    fun `pop on an unknown identity is a no-op`() {
        val ledger = RewriteLedger()
        assertNull(ledger.pop("STYLE:Z"))
        assertEquals(0, ledger.historySize("STYLE:Z"))
        assertFalse(ledger.hasHistory("STYLE:Z"))
    }

    @Test
    fun `peek does not consume`() {
        val ledger = RewriteLedger()
        ledger.push("STYLE:A", RewriteLedger.Version("原文", SchemeFeedback.NONE))

        repeat(3) { assertEquals("原文", ledger.peekLast("STYLE:A")?.reply) }
        assertEquals(1, ledger.historySize("STYLE:A"))
    }

    @Test
    fun `clearAll drops every stack`() {
        val ledger = RewriteLedger()
        ledger.push("STYLE:A", RewriteLedger.Version("A 原文", SchemeFeedback.LIKED))
        ledger.push("DIRECTION:F", RewriteLedger.Version("F 原文", SchemeFeedback.NONE))

        ledger.clearAll()

        assertEquals(0, ledger.historySize("STYLE:A"))
        assertEquals(0, ledger.historySize("DIRECTION:F"))
        assertFalse(ledger.hasHistory("STYLE:A"))
    }
}
