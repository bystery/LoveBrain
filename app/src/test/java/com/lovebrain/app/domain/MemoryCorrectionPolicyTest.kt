package com.lovebrain.app.domain

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MuteDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆纠正判定与文案的合同（`MemoryCorrectionPolicy` / `RoundCorrectionStore`）。
 *
 * 这些规则原先散在 `LoveBrainViewModel` 的两个撤销入口和一段 when 里：
 * 撤销失败在纠正中心有提示、在方案卡片上没有；"哪种组合算本轮瞬时"要同时看
 * action 和 duration 两个字段，写错一处就是"重启后我以为已经纠正过的记忆又回来了"。
 */
class MemoryCorrectionPolicyTest {

    // ─── 1. 本轮瞬时的判定 ────────────────────────────────────────

    @Test
    fun `only a muted correction with this-round duration stays transient`() {
        assertTrue(
            MemoryCorrectionPolicy.isTransientRoundMute(
                CorrectionAction.MUTED, MuteDuration.THIS_ROUND
            )
        )
        for (action in CorrectionAction.values()) {
            for (duration in MuteDuration.values()) {
                val expected = action == CorrectionAction.MUTED && duration == MuteDuration.THIS_ROUND
                assertEquals(
                    "$action + $duration",
                    expected,
                    MemoryCorrectionPolicy.isTransientRoundMute(action, duration)
                )
            }
        }
    }

    @Test
    fun `the transient correction it builds carries the mute semantics not the caller's`() {
        val built = MemoryCorrectionPolicy.transientMute(
            memoryId = "m-1", replacementText = "换成‘她’而不是‘你’", targetKbId = "kb-2", timestamp = "2026-09-24 09:59"
        )
        assertEquals("m-1", built.memoryId)
        assertEquals(CorrectionAction.MUTED, built.action)
        assertEquals(MuteDuration.THIS_ROUND, built.muteDuration)
        assertEquals("2026-09-24 09:59", built.muteTimestamp)
        assertEquals("换成‘她’而不是‘你’", built.replacementText)
        assertEquals("kb-2", built.targetKbId)
    }

    // ─── 2. 文案各说各事，不复用不撞车 ────────────────────────────

    @Test
    fun `every action has its own success notice`() {
        val notices = CorrectionAction.values().map { MemoryCorrectionPolicy.applied(it) }
        assertEquals("四种动作四条文案", notices.size, notices.toSet().size)
        assertTrue(notices.all { it.isNotBlank() })
        assertTrue(MemoryCorrectionPolicy.applied(CorrectionAction.WRONG).contains("错误"))
        assertTrue(MemoryCorrectionPolicy.applied(CorrectionAction.FINISHED).contains("结束"))
        assertTrue(MemoryCorrectionPolicy.applied(CorrectionAction.WRONG_PERSON).contains("隔离"))
        assertTrue(MemoryCorrectionPolicy.applied(CorrectionAction.MUTED).contains("暂停"))
    }

    @Test
    fun `failure notices are never the same string as success notices`() {
        val failures = listOf(MemoryCorrectionPolicy.applyFailed(), MemoryCorrectionPolicy.undoFailed())
        val successes = CorrectionAction.values().map { MemoryCorrectionPolicy.applied(it) } +
            listOf(MemoryCorrectionPolicy.undone(), MemoryCorrectionPolicy.roundMuteApplied(),
                MemoryCorrectionPolicy.roundMuteUndone())
        for (f in failures) {
            for (s in successes) assertNotEquals("失败文案不能与成功文案相同：$f", s, f)
        }
    }

    // ─── 3. 本轮瞬时存储 ─────────────────────────────────────────

    @Test
    fun `the store hands out copies so a caller cannot edit view model state`() {
        val store = RoundCorrectionStore()
        assertTrue(store.isEmpty())
        store.put(
            MemoryCorrectionPolicy.transientMute("m-1", "", "", "t1")
        )

        val snapshot = store.snapshot()
        assertEquals(1, snapshot.size)

        store.put(MemoryCorrectionPolicy.transientMute("m-2", "", "", "t2"))
        assertEquals("发出去的快照必须是当时那份", 1, snapshot.size)
        assertEquals(2, store.snapshot().size)
    }

    @Test
    fun `remove reports whether the id was transient so the caller knows to fall through`() {
        val store = RoundCorrectionStore()
        store.put(MemoryCorrectionPolicy.transientMute("m-1", "", "", "t1"))

        assertTrue(store.remove("m-1"))
        assertFalse("第二次已经没有这条瞬时 mute", store.remove("m-1"))
        assertTrue(store.isEmpty())

        store.clear()
        assertFalse(store.remove("never-there"))
    }
}
