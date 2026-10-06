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
}
