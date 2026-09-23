package com.lovebrain.app.viewmodel

import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.SchemeFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 改写台账（`RewriteLedger`）的行为基线。
 *
 * 这块原先是 ViewModel 里两个私有 StateFlow + 手写 map 操作，撤销一步要连着
 * "取最后版本 / 丢掉最后一条 / 空了就删 key"三段，只能靠真机点出来。
 * 钉住的正是这三段：漏掉任何一步，撤销就会拿回错的正文或留下洗不净的历史。
 */
class RewriteLedgerTest {

    // ─── begin ───────────────────────────────────────────────────

    @Test
    fun `begin marks the card loading and remembers the version being replaced`() {
        val ledger = RewriteLedger()
        ledger.begin("STYLE:A", "短一点", previousReply = "原文", previousFeedback = SchemeFeedback.LIKED)

        assertEquals(RewriteState.Loading("短一点"), ledger.stateOf("STYLE:A"))
        assertEquals(1, ledger.historySize("STYLE:A"))
        assertEquals(
            RewriteLedger.Version("原文", SchemeFeedback.LIKED),
            ledger.peekLast("STYLE:A")
        )
    }

    @Test
    fun `the feedback travels with the reply it belonged to`() {
        val ledger = RewriteLedger()
        ledger.begin("STYLE:A", "第一次改", "v1", SchemeFeedback.DISLIKED)
        ledger.begin("STYLE:A", "第二次改", "v2", SchemeFeedback.NONE)

        assertEquals(RewriteLedger.Version("v2", SchemeFeedback.NONE), ledger.pop("STYLE:A"))
        assertEquals(RewriteLedger.Version("v1", SchemeFeedback.DISLIKED), ledger.pop("STYLE:A"))
    }

    @Test
    fun `identities are tracked separately`() {
        val ledger = RewriteLedger()
        ledger.begin("STYLE:A", "改 A", "A 原文", SchemeFeedback.NONE)
        ledger.begin("DIRECTION:F", "改 F", "F 原文", SchemeFeedback.LIKED)

        assertEquals(RewriteState.Loading("改 F"), ledger.stateOf("DIRECTION:F"))
        assertEquals("A 原文", ledger.peekLast("STYLE:A")?.reply)
        ledger.removeState("STYLE:A")
        assertNull(ledger.stateOf("STYLE:A"))
        assertEquals(RewriteState.Loading("改 F"), ledger.stateOf("DIRECTION:F"))
        assertEquals(1, ledger.historySize("DIRECTION:F"))
    }

    // ─── pop / 撤销 ──────────────────────────────────────────────

    @Test
    fun `popping the last version drops the key instead of leaving an empty list behind`() {
        val ledger = RewriteLedger()
        ledger.begin("STYLE:A", "改一次", "原文", SchemeFeedback.NONE)

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

    // ─── 状态与整体清空 ──────────────────────────────────────────

    @Test
    fun `state transitions overwrite the same identity`() {
        val ledger = RewriteLedger()
        ledger.begin("STYLE:A", "改", "原文", SchemeFeedback.NONE)
        ledger.setState("STYLE:A", RewriteState.Done("新正文"))
        assertEquals(RewriteState.Done("新正文"), ledger.stateOf("STYLE:A"))

        ledger.setState("STYLE:A", RewriteState.Error("改写失败，可重试"))
        assertEquals(RewriteState.Error("改写失败，可重试"), ledger.stateOf("STYLE:A"))
        assertTrue("失败不影响已压入的历史", ledger.historySize("STYLE:A") == 1)
    }

    @Test
    fun `clearAll wipes states and history together`() {
        val ledger = RewriteLedger()
        ledger.begin("STYLE:A", "改", "原文", SchemeFeedback.LIKED)
        ledger.begin("DIRECTION:F", "改", "F 原文", SchemeFeedback.NONE)

        ledger.clearAll()

        assertNull(ledger.stateOf("STYLE:A"))
        assertNull(ledger.stateOf("DIRECTION:F"))
        assertEquals(0, ledger.historySize("STYLE:A"))
        assertEquals(0, ledger.historySize("DIRECTION:F"))
        assertTrue(ledger.states.value.isEmpty())
    }

    @Test
    fun `the exposed flow is the same map the ledger reads`() {
        val ledger = RewriteLedger()
        ledger.setState("STYLE:B", RewriteState.Loading("改"))
        assertEquals(mapOf("STYLE:B" to RewriteState.Loading("改")), ledger.states.value)
    }
}
