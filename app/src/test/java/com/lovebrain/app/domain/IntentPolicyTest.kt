package com.lovebrain.app.domain

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持续意图的到期判定与保存校验（`IntentPolicy`）。
 *
 * 新规格下有效期四档：ONE_HOUR / ONE_DAY / ONE_WEEK（时间档，保存时刻算出到期时刻）
 * 与 COMPLETED（保存即标完成）。到期判定走 "yyyy-MM-dd HH:mm" 字符串比较
 * （ISO-like 格式字典序 = 时间序）。
 */
class IntentPolicyTest {

    private val now = "2026-09-24 09:00"

    private fun cfg(
        enabled: Boolean = true,
        expiry: IntentExpiry = IntentExpiry.ONE_DAY,
        expiryDate: String = "",
        status: IntentStatus = IntentStatus.ACTIVE
    ) = IntentConfig(text = "周末约她", enabled = enabled, expiry = expiry, expiryDate = expiryDate, status = status)

    // ─── 到期判定 ────────────────────────────────────────────────

    @Test
    fun `a time-based intent expires once its pinned moment is in the past`() {
        for (expiry in listOf(IntentExpiry.ONE_HOUR, IntentExpiry.ONE_DAY, IntentExpiry.ONE_WEEK)) {
            assertTrue(
                "$expiry 到期时刻已过就该判过期",
                IntentPolicy.shouldAutoExpire(cfg(expiry = expiry, expiryDate = "2026-09-24 08:00"), now)
            )
            assertEquals(
                "$expiry 未到期不判过期",
                false,
                IntentPolicy.shouldAutoExpire(cfg(expiry = expiry, expiryDate = "2026-09-25 09:00"), now)
            )
        }
    }

    @Test
    fun `COMPLETED never auto-expires whatever the date says`() {
        assertFalse(
            IntentPolicy.shouldAutoExpire(cfg(expiry = IntentExpiry.COMPLETED, expiryDate = "2020-01-01 00:00"), now)
        )
    }

    @Test
    fun `only an enabled active intent can be auto-expired`() {
        val past = "2026-09-01 00:00"
        assertTrue(IntentPolicy.shouldAutoExpire(cfg(expiry = IntentExpiry.ONE_DAY, expiryDate = past), now))
        assertEquals(
            "关闭中的意图不该被后台改写状态", false,
            IntentPolicy.shouldAutoExpire(cfg(enabled = false, expiry = IntentExpiry.ONE_DAY, expiryDate = past), now)
        )
        for (status in listOf(IntentStatus.PAUSED, IntentStatus.COMPLETED, IntentStatus.EXPIRED)) {
            assertEquals(
                "$status 不是活动态，不再判过期", false,
                IntentPolicy.shouldAutoExpire(cfg(expiry = IntentExpiry.ONE_DAY, expiryDate = past, status = status), now)
            )
        }
    }

    @Test
    fun `a legacy intent without a date is kept rather than silently expired`() {
        // 没有到期时刻就没有比较依据，宁可继续注入也不悄悄判死
        for (expiry in listOf(IntentExpiry.ONE_HOUR, IntentExpiry.ONE_DAY, IntentExpiry.ONE_WEEK)) {
            assertEquals(
                expiry.toString(), false,
                IntentPolicy.shouldAutoExpire(cfg(expiry = expiry, expiryDate = ""), now)
            )
        }
    }

    // ─── 到期时刻计算 ────────────────────────────────────────────

    @Test
    fun `each time-based expiry pins its moment relative to the save time`() {
        assertEquals("2026-09-24 10:00", IntentPolicy.effectiveExpiryDate(IntentExpiry.ONE_HOUR, "", now))
        assertEquals("2026-09-25 09:00", IntentPolicy.effectiveExpiryDate(IntentExpiry.ONE_DAY, "", now))
        assertEquals("2026-10-01 09:00", IntentPolicy.effectiveExpiryDate(IntentExpiry.ONE_WEEK, "", now))
    }

    @Test
    fun `COMPLETED carries no expiry date`() {
        assertEquals("", IntentPolicy.effectiveExpiryDate(IntentExpiry.COMPLETED, "2026-10-01 09:00", now))
    }

    @Test
    fun `an unparseable now leaves the caller-supplied date untouched`() {
        // now 坏了宁可不过期也不悄悄改写
        assertEquals("2026-10-01 09:00",
            IntentPolicy.effectiveExpiryDate(IntentExpiry.ONE_DAY, "2026-10-01 09:00", "不是日期"))
    }

    // ─── 完成状态 ────────────────────────────────────────────────

    @Test
    fun `COMPLETED forces the status to COMPLETED, other expiries keep the given status`() {
        assertEquals(IntentStatus.COMPLETED, IntentPolicy.effectiveStatus(IntentExpiry.COMPLETED, IntentStatus.ACTIVE))
        assertEquals(IntentStatus.ACTIVE, IntentPolicy.effectiveStatus(IntentExpiry.ONE_DAY, IntentStatus.ACTIVE))
        assertEquals(IntentStatus.PAUSED, IntentPolicy.effectiveStatus(IntentExpiry.ONE_WEEK, IntentStatus.PAUSED))
    }

    // ─── 保存校验 ────────────────────────────────────────────────

    @Test
    fun `COMPLETED is always a valid save`() {
        assertNull(IntentPolicy.validateSave(IntentExpiry.COMPLETED, "", IntentStatus.COMPLETED, now))
    }

    @Test
    fun `a time-based save is valid once its moment has been computed`() {
        assertNull(IntentPolicy.validateSave(IntentExpiry.ONE_HOUR, "2026-09-24 10:00", IntentStatus.ACTIVE, now))
        assertNull(IntentPolicy.validateSave(IntentExpiry.ONE_DAY, "2026-09-25 09:00", IntentStatus.ACTIVE, now))
        assertNull(IntentPolicy.validateSave(IntentExpiry.ONE_WEEK, "2026-10-01 09:00", IntentStatus.ACTIVE, now))
    }

    @Test
    fun `a blank moment for a time-based save is rejected`() {
        val reason = IntentPolicy.validateSave(IntentExpiry.ONE_DAY, "", IntentStatus.ACTIVE, now)
        assertTrue("空到期时刻要拒：$reason", reason!!.contains("不能为空"))
    }

    // ─── §10.2: 完成/到期后重新启用恢复 ACTIVE ───────────────────

    /**
     * §10.2：effectiveStatus 保留调用方传入的 status——如果调用方传了旧的 COMPLETED，
     * 时间档不会自动恢复 ACTIVE。这钉住了"恢复 ACTIVE 是 UI 层的职责"这条语义：
     * UI 层在重新启用/换档时必须传 ACTIVE，不能传旧的终态。
     */
    @Test
    fun `effectiveStatus preserves a stale COMPLETED when the caller passes it`() {
        // 调用方如果传了旧的 COMPLETED，effectiveStatus 不会自动修
        assertEquals(
            IntentStatus.COMPLETED,
            IntentPolicy.effectiveStatus(IntentExpiry.ONE_DAY, IntentStatus.COMPLETED)
        )
    }

    @Test
    fun `effectiveStatus keeps ACTIVE when the caller passes ACTIVE for a time-based expiry`() {
        assertEquals(
            IntentStatus.ACTIVE,
            IntentPolicy.effectiveStatus(IntentExpiry.ONE_DAY, IntentStatus.ACTIVE)
        )
    }

    @Test
    fun `effectiveStatus keeps EXPIRED when the caller passes it without switching to ACTIVE`() {
        // 这条钉住的是"不传 ACTIVE 就不会自动恢复"——UI 层的修复必须真的传 ACTIVE
        assertEquals(
            IntentStatus.EXPIRED,
            IntentPolicy.effectiveStatus(IntentExpiry.ONE_DAY, IntentStatus.EXPIRED)
        )
    }

    @Test
    fun `COMPLETED expiry always forces COMPLETED regardless of the passed status`() {
        // 换到 COMPLETED 档 = 用户点了"已完成"动作，不管旧 status 是什么
        assertEquals(
            IntentStatus.COMPLETED,
            IntentPolicy.effectiveStatus(IntentExpiry.COMPLETED, IntentStatus.ACTIVE)
        )
        assertEquals(
            IntentStatus.COMPLETED,
            IntentPolicy.effectiveStatus(IntentExpiry.COMPLETED, IntentStatus.EXPIRED)
        )
    }

    // ─── §10.2: saveDecision（到期重开要重算期限 / 只改正文保原期限）─────

    /** 到期现场：`IntentController.refreshForKb` 判过期时就是这一份形状（关着 + 旧时刻已过） */
    private fun expired(
        expiry: IntentExpiry = IntentExpiry.ONE_DAY,
        status: IntentStatus = IntentStatus.EXPIRED,
        enabled: Boolean = false
    ) = IntentConfig(
        text = "周末约她", enabled = enabled, expiry = expiry,
        expiryDate = "2026-09-01 00:00", status = status
    )

    @Test
    fun `re-enabling an expired intent restores ACTIVE and demands a recomputed deadline`() {
        val decision = IntentPolicy.saveDecision(
            current = expired(), nextEnabled = true, nextExpiry = IntentExpiry.ONE_DAY
        )
        assertEquals(IntentStatus.ACTIVE, decision.status)
        assertTrue("重新启用必须重算期限：旧时刻已是过去，带着它落盘下一刻就被判回 EXPIRED",
            decision.recomputeExpiry)
    }

    /**
     * 这一格把三条判据串成一整条链跑一遍（判据 → 时刻 → 到期判定）：
     * 断言的是"重开之后那条意图还活着"，这正是用户合同那一行的原话。
     * 旧缺陷态（`recomputeExpiry = false` ⇒ 把 `2026-10-01 08:00` 原样传下去）在这里会红。
     */
    @Test
    fun `a re-enabled intent is not immediately expired again`() {
        val current = expired()
        val decision = IntentPolicy.saveDecision(
            current = current, nextEnabled = true, nextExpiry = IntentExpiry.ONE_DAY
        )
        val written = current.copy(
            enabled = true,
            status = decision.status,
            expiryDate = IntentPolicy.effectiveExpiryDate(
                current.expiry,
                if (decision.recomputeExpiry) "" else current.expiryDate,
                now
            )
        )
        assertEquals("2026-09-25 09:00", written.expiryDate)
        assertFalse("重开即失效：落盘那一份仍被判过期", IntentPolicy.shouldAutoExpire(written, now))
        // 反向证人：旧缺陷态把已有时刻原样传下去，这里就必须判过期（否则上面那句是恒绿）
        val stale = current.copy(
            enabled = true, status = IntentStatus.ACTIVE,
            expiryDate = IntentPolicy.effectiveExpiryDate(current.expiry, current.expiryDate, now)
        )
        assertTrue("证人失效：带着旧时刻落盘居然不再被判过期，那条断言抓不住东西",
            IntentPolicy.shouldAutoExpire(stale, now))
    }

    @Test
    fun `editing only the text keeps the deadline verbatim and never rewrites the status`() {
        val current = cfg(expiryDate = "2026-09-25 09:00")
        val decision = IntentPolicy.saveDecision(
            current = current, nextEnabled = true, nextExpiry = IntentExpiry.ONE_DAY
        )
        assertFalse("只改正文（没动开关、没换档）不许重算期限", decision.recomputeExpiry)
        assertEquals("期限逐字不变", "2026-09-25 09:00",
            IntentPolicy.effectiveExpiryDate(current.expiry, current.expiryDate, now))
        assertEquals(IntentStatus.ACTIVE, decision.status)
    }

    @Test
    fun `switching off keeps the deadline and leaves a terminal status alone`() {
        val decision = IntentPolicy.saveDecision(
            current = expired(), nextEnabled = false, nextExpiry = IntentExpiry.ONE_DAY
        )
        assertFalse(decision.recomputeExpiry)
        assertEquals("关着的那一格不许被洗成活动态", IntentStatus.EXPIRED, decision.status)
    }

    @Test
    fun `changing the expiry option recomputes and brings an expired intent back`() {
        // 开关本来就拨着（内存里 EXPIRED 但未关）：换档 = 用户明确重新选有效时间
        val decision = IntentPolicy.saveDecision(
            current = expired(enabled = true), nextEnabled = true, nextExpiry = IntentExpiry.ONE_WEEK
        )
        assertTrue(decision.recomputeExpiry)
        assertEquals(IntentStatus.ACTIVE, decision.status)
    }

    @Test
    fun `a ui flagged recompute on a same-option tap still recomputes`() {
        // 设置页那颗有效期 chip 每次都传 recomputeExpiry=true，哪怕档位没变
        val decision = IntentPolicy.saveDecision(
            current = cfg(expiryDate = "2026-09-25 09:00"),
            nextEnabled = true, nextExpiry = IntentExpiry.ONE_DAY,
            callerRequestedRecompute = true
        )
        assertTrue(decision.recomputeExpiry)
        assertEquals(IntentStatus.ACTIVE, decision.status)
    }

    @Test
    fun `marking completed wins over the reactivation rule`() {
        // 到期意图重开的同时又被点成「已完成」：完成是状态动作，不是重新启用
        val decision = IntentPolicy.saveDecision(
            current = expired(), nextEnabled = true, nextExpiry = IntentExpiry.COMPLETED,
            nextStatus = IntentStatus.COMPLETED
        )
        assertEquals(IntentStatus.COMPLETED, decision.status)
        assertTrue(decision.recomputeExpiry)
        assertEquals("COMPLETED 不带日期", "",
            IntentPolicy.effectiveExpiryDate(IntentExpiry.COMPLETED, "2026-10-01 08:00", now))
    }

    @Test
    fun `a completed intent switched back on comes back ACTIVE with a fresh deadline`() {
        val current = expired(expiry = IntentExpiry.ONE_HOUR, status = IntentStatus.COMPLETED)
        val decision = IntentPolicy.saveDecision(
            current = current, nextEnabled = true, nextExpiry = IntentExpiry.ONE_HOUR
        )
        assertEquals(IntentStatus.ACTIVE, decision.status)
        assertTrue(decision.recomputeExpiry)
    }

    @Test
    fun `a brand new intent with no deadline is computed instead of being left blank`() {
        // 默认那份：没开过、也没日期。第一次启用要算出时刻，否则 validateSave 直接拒
        val decision = IntentPolicy.saveDecision(
            current = IntentConfig(), nextEnabled = true, nextExpiry = IntentExpiry.ONE_DAY
        )
        assertTrue(decision.recomputeExpiry)
        assertNull(IntentPolicy.validateSave(
            IntentExpiry.ONE_DAY,
            IntentPolicy.effectiveExpiryDate(IntentExpiry.ONE_DAY, "", now),
            decision.status, now
        ))
    }
}
