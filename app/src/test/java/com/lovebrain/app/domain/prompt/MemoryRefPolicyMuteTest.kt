package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.MemoryCorrectionPolicy
import com.lovebrain.app.domain.RoundCorrectionStore
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * 静音的时间语义（审计 #1）：**立即静音要覆盖下一次生成，跨轮静音才按时间**。
 *
 * 旧实现里 `MemoryCorrectionPolicy.transientMute` 把时间戳写进 muteTimestamp，
 * 而 `MemoryRefPolicy.isMuteExpired` 把"非空时间戳的 THIS_ROUND"统一当成
 * 旧版本持久化记录判过期——用户点"仅本轮"，界面提示"下次生成将过滤"，
 * 实际下一次生成照样注入。这里把写入点与判定两端的契约一起钉住。
 */
class MemoryRefPolicyMuteTest {

    private val id = "SCENE:moment/scene.md"

    private fun mute(duration: MuteDuration, timestamp: String) = MemoryCorrection(
        memoryId = id,
        action = CorrectionAction.MUTED,
        muteDuration = duration,
        muteTimestamp = timestamp
    )

    @Test
    fun `this round mute filters the next generation while it lives in the round store`() {
        val transient = MemoryCorrectionPolicy.transientMute(id, "", "", "2026-11-06 10:00")

        assertFalse("瞬时 mute 不许被判过期", MemoryRefPolicy.isMuteExpired(transient))
        val sb = StringBuilder()
        assertTrue("下一次生成要跳过这条原文", MemoryRefPolicy.isCorrected(id, mapOf(id to transient), sb))
        assertTrue("仍要留可被动回应的标记：$sb", sb.contains("暂停主动提及"))
    }

    @Test
    fun `a transient mute never takes the persisted shape that reads as a permanent mute`() {
        val transient = MemoryCorrectionPolicy.transientMute(id, "", "", "2026-11-06 10:00")

        // 判定侧把"空时间戳 + THIS_ROUND"当成永不过期，所以这条记录一旦进 corrections.json
        // 就是永久静音。写入侧必须两头都堵死：不带墙钟时间戳，且该组合在落盘前就被分流。
        assertEquals("瞬时 mute 不许带墙钟时间戳", "", transient.muteTimestamp)
        assertEquals("时间戳只作诊断落在 updatedAt", "2026-11-06 10:00", transient.updatedAt)
        assertTrue(
            "写入侧必须把 MUTED + THIS_ROUND 判成瞬时（VM 不调 saveCorrection）",
            MemoryCorrectionPolicy.isTransientRoundMute(CorrectionAction.MUTED, MuteDuration.THIS_ROUND)
        )
        assertFalse(
            "可持久化的组合不得被瞬时判据吞掉（否则重启后纠正丢失）",
            listOf(
                CorrectionAction.MUTED to MuteDuration.TODAY,
                CorrectionAction.MUTED to MuteDuration.UNTIL_RESTORE,
                CorrectionAction.WRONG to MuteDuration.THIS_ROUND
            ).any { (action, duration) -> MemoryCorrectionPolicy.isTransientRoundMute(action, duration) }
        )
        val store = RoundCorrectionStore()
        store.put(transient)
        store.clear()
        assertFalse(
            "下一轮/重载清空后不许再过滤——瞬时静音不得变成永久静音",
            MemoryRefPolicy.isCorrected(id, store.snapshot(), StringBuilder())
        )
    }

    @Test
    fun `a legacy persisted this round mute is expired again`() {
        val legacy = mute(MuteDuration.THIS_ROUND, "2026-11-05T09:00:00+08:00")

        assertTrue("旧版本落过盘的 THIS_ROUND 按 legacy expired 处理", MemoryRefPolicy.isMuteExpired(legacy))
        val sb = StringBuilder()
        assertFalse("过期后恢复注入", MemoryRefPolicy.isCorrected(id, mapOf(id to legacy), sb))
        assertEquals("恢复注入时一个字都不许追加", "", sb.toString())
    }

    @Test
    fun `today mute expires only after the date changes and until restore never expires`() {
        val now = OffsetDateTime.now()

        assertFalse("今天刚静音的，今天有效", MemoryRefPolicy.isMuteExpired(mute(MuteDuration.TODAY, now.toString())))
        assertTrue("昨天的静音跨天恢复", MemoryRefPolicy.isMuteExpired(mute(MuteDuration.TODAY, now.minusDays(1).toString())))
        assertFalse(
            "直到手动恢复没有时间窗口",
            MemoryRefPolicy.isMuteExpired(mute(MuteDuration.UNTIL_RESTORE, "2020-01-01T00:00:00+08:00"))
        )
        assertFalse(
            "时间戳读不出来时不许猜「已过期」：宁可继续静音，也不丢用户刚做的决定",
            MemoryRefPolicy.isMuteExpired(mute(MuteDuration.TODAY, "2026-11-06 10:00"))
        )
    }

    @Test
    fun `removing the transient record restores injection`() {
        val store = RoundCorrectionStore()
        store.put(MemoryCorrectionPolicy.transientMute(id, "", "", "2026-11-06 10:00"))

        assertTrue("在账本里就是生效", MemoryRefPolicy.isCorrected(id, store.snapshot(), StringBuilder()))
        assertTrue(store.remove(id))
        assertFalse("撤销后恢复注入", MemoryRefPolicy.isCorrected(id, store.snapshot(), StringBuilder()))
    }
}
