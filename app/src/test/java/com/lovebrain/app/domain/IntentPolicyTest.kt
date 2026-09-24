package com.lovebrain.app.domain

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持续意图的到期与保存校验（`IntentPolicy`）。
 *
 * 同一把"日期是否已过"的尺此前在**发起生成**和**面板刷新**两处各写了一遍，
 * 保存校验又单独用了第二个时钟（填日期用 `TimeFmt.today()`，判过去用 `LocalDate.now()`）；
 * 搬成一处后，这些都得有用例钉着，否则下次还是靠巧合保持一致。
 */
class IntentPolicyTest {

    private val today = "2026-09-24"

    private fun cfg(
        enabled: Boolean = true,
        expiry: IntentExpiry = IntentExpiry.UNTIL_DONE,
        expiryDate: String = "",
        status: IntentStatus = IntentStatus.ACTIVE
    ) = IntentConfig(text = "周末约她", enabled = enabled, expiry = expiry, expiryDate = expiryDate, status = status)

    // ─── 到期判定 ────────────────────────────────────────────────

    @Test
    fun `a dated intent expires the day after its date, for both TODAY and DATE`() {
        for (expiry in listOf(IntentExpiry.TODAY, IntentExpiry.DATE)) {
            assertTrue(
                "$expiry 昨天到期就该判过期",
                IntentPolicy.shouldAutoExpire(cfg(expiry = expiry, expiryDate = "2026-09-23"), today)
            )
            assertEquals(
                "$expiry 当天仍然有效",
                false,
                IntentPolicy.shouldAutoExpire(cfg(expiry = expiry, expiryDate = today), today)
            )
            assertEquals(
                "$expiry 未到期不判过期",
                false,
                IntentPolicy.shouldAutoExpire(cfg(expiry = expiry, expiryDate = "2026-10-01"), today)
            )
        }
    }

    @Test
    fun `until-done never auto-expires whatever the date says`() {
        assertTrue(
            IntentPolicy.shouldAutoExpire(cfg(expiry = IntentExpiry.UNTIL_DONE, expiryDate = "2020-01-01"), today).not()
        )
    }

    @Test
    fun `only an enabled active intent can be auto-expired`() {
        val past = "2026-09-01"
        assertTrue(IntentPolicy.shouldAutoExpire(cfg(expiry = IntentExpiry.DATE, expiryDate = past), today))
        assertEquals(
            "关闭中的意图不该被后台改写状态", false,
            IntentPolicy.shouldAutoExpire(cfg(enabled = false, expiry = IntentExpiry.DATE, expiryDate = past), today)
        )
        for (status in listOf(IntentStatus.PAUSED, IntentStatus.COMPLETED, IntentStatus.EXPIRED)) {
            assertEquals(
                "$status 不是活动态，不再判过期", false,
                IntentPolicy.shouldAutoExpire(cfg(expiry = IntentExpiry.DATE, expiryDate = past, status = status), today)
            )
        }
    }

    @Test
    fun `a legacy intent without a date is kept rather than silently expired`() {
        // 自动填日期之前的老数据：没有日期就没有比较依据，宁可继续注入也不悄悄判死
        for (expiry in listOf(IntentExpiry.TODAY, IntentExpiry.DATE)) {
            assertEquals(
                expiry.toString(), false,
                IntentPolicy.shouldAutoExpire(cfg(expiry = expiry, expiryDate = ""), today)
            )
        }
    }

    // ─── 保存 ────────────────────────────────────────────────────

    @Test
    fun `TODAY always pins its date to the day being saved on`() {
        assertEquals(today, IntentPolicy.effectiveExpiryDate(IntentExpiry.TODAY, "2020-01-01", today))
        assertEquals("2026-10-01", IntentPolicy.effectiveExpiryDate(IntentExpiry.DATE, "2026-10-01", today))
        assertEquals("", IntentPolicy.effectiveExpiryDate(IntentExpiry.UNTIL_DONE, "", today))
    }

    @Test
    fun `a dated intent refuses empty malformed and past-active`() {
        val empty = IntentPolicy.validateSave(IntentExpiry.DATE, "", IntentStatus.ACTIVE, today)
        assertTrue("空日期要拒：$empty", empty!!.contains("不能为空"))

        val malformed = IntentPolicy.validateSave(IntentExpiry.DATE, "2026/10/01", IntentStatus.ACTIVE, today)
        assertTrue("格式不对要拒：$malformed", malformed!!.contains("格式"))

        val past = IntentPolicy.validateSave(IntentExpiry.DATE, "2026-09-01", IntentStatus.ACTIVE, today)
        assertTrue("过去日期配活动态要拒：$past", past!!.contains("过去"))
    }

    @Test
    fun `a past date is fine when the intent is not being kept active`() {
        for (status in listOf(IntentStatus.PAUSED, IntentStatus.COMPLETED, IntentStatus.EXPIRED)) {
            assertNull(
                "$status 归档到过去是合法的",
                IntentPolicy.validateSave(IntentExpiry.DATE, "2026-09-01", status, today)
            )
        }
    }

    @Test
    fun `only the DATE type is date-validated`() {
        assertNull(IntentPolicy.validateSave(IntentExpiry.TODAY, "", IntentStatus.ACTIVE, today))
        assertNull(IntentPolicy.validateSave(IntentExpiry.UNTIL_DONE, "乱七八糟", IntentStatus.ACTIVE, today))
        assertNull(IntentPolicy.validateSave(IntentExpiry.DATE, today, IntentStatus.ACTIVE, today))
        assertNull(IntentPolicy.validateSave(IntentExpiry.DATE, "2026-10-01", IntentStatus.ACTIVE, today))
    }

    @Test
    fun `unparseable today never turns a valid save into a false rejection`() {
        // 校验用的"今天"来自同一个时钟；万一它坏了，宁可放行也不能把用户挡在外面
        assertNull(IntentPolicy.validateSave(IntentExpiry.DATE, "2026-09-01", IntentStatus.ACTIVE, "不是日期"))
    }
}
