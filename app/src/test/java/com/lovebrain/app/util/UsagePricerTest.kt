package com.lovebrain.app.util

import com.lovebrain.app.util.UsagePricer.PriceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 *  计价器契约测试：峰谷×档位×命中态×双条件×边界时刻（演进表计划 14 条）。
 * 时间锚点：2026-08-31 = 周一（北京时间）；2026-09-05 = 周六；2026-09-06 = 周日。
 * 北京时间 = UTC+8。
 */
class UsagePricerTest {

    // ═══ 档位判定 ═══

    @Test
    fun tier_matches_flash_case_insensitive() {
        assertEquals(PriceTier.FLASH, UsagePricer.priceTier("DeepSeek-V4-Flash"))
        assertEquals(PriceTier.FLASH, UsagePricer.priceTier("deepseek-v4-flash"))
    }

    @Test
    fun tier_flash_wins_over_pro_when_both_present() {
        // ：flash 判定优先
        assertEquals(PriceTier.FLASH, UsagePricer.priceTier("some-flash-pro-hybrid"))
    }

    @Test
    fun tier_matches_pro() {
        assertEquals(PriceTier.PRO, UsagePricer.priceTier("DeepSeek-V4-Pro"))
    }

    @Test
    fun tier_unknown_for_other_models() {
        assertEquals(PriceTier.UNKNOWN, UsagePricer.priceTier("gpt-x"))
        assertEquals(PriceTier.UNKNOWN, UsagePricer.priceTier(""))
    }

    // ═══ 高峰判定（北京时间，左闭右开） ═══

    @Test
    fun peak_monday_0900_is_left_closed_true() {
        assertTrue(UsagePricer.isPeakHourBeijing(Instant.parse("2026-08-31T01:00:00Z")))
    }

    @Test
    fun peak_monday_1200_is_right_open_false() {
        assertFalse(UsagePricer.isPeakHourBeijing(Instant.parse("2026-08-31T04:00:00Z")))
        // 11:59 仍在上午高峰窗内
        assertTrue(UsagePricer.isPeakHourBeijing(Instant.parse("2026-08-31T03:59:00Z")))
    }

    @Test
    fun peak_monday_afternoon_window() {
        assertTrue(UsagePricer.isPeakHourBeijing(Instant.parse("2026-08-31T06:00:00Z")))   // 14:00
        assertTrue(UsagePricer.isPeakHourBeijing(Instant.parse("2026-08-31T09:59:00Z")))   // 17:59
    }

    @Test
    fun off_peak_outside_windows_weekday() {
        assertFalse(UsagePricer.isPeakHourBeijing(Instant.parse("2026-08-31T00:59:00Z")))  // 08:59
        assertFalse(UsagePricer.isPeakHourBeijing(Instant.parse("2026-08-31T05:59:00Z")))  // 13:59
        assertFalse(UsagePricer.isPeakHourBeijing(Instant.parse("2026-08-31T10:00:00Z")))  // 18:00
    }

    @Test
    fun weekend_is_always_off_peak() {
        // 周六 10:00 北京 = 2026-09-05T02:00Z；周日 10:00 北京 = 2026-09-06T02:00Z
        assertFalse(UsagePricer.isPeakHourBeijing(Instant.parse("2026-09-05T02:00:00Z")))
        assertFalse(UsagePricer.isPeakHourBeijing(Instant.parse("2026-09-06T02:00:00Z")))
    }

    // ═══ 金额计算（元/百万 tokens） ═══

    @Test
    fun cost_flash_off_peak_all_three_prices() {
        // 各 100 万 tokens：0.05 + 1.5 + 4.5 = 6.05
        assertEquals(6.05, UsagePricer.costYuan(1_000_000, 1_000_000, 1_000_000, PriceTier.FLASH, false), 1e-9)
    }

    @Test
    fun cost_flash_peak_all_three_prices() {
        // 0.10 + 3.0 + 9.0 = 12.10
        assertEquals(12.10, UsagePricer.costYuan(1_000_000, 1_000_000, 1_000_000, PriceTier.FLASH, true), 1e-9)
    }

    @Test
    fun cost_pro_off_peak_and_peak() {
        // 低谷 0.15 + 4.5 + 13.5 = 18.15；高峰 0.30 + 9.0 + 27.0 = 36.30
        assertEquals(18.15, UsagePricer.costYuan(1_000_000, 1_000_000, 1_000_000, PriceTier.PRO, false), 1e-9)
        assertEquals(36.30, UsagePricer.costYuan(1_000_000, 1_000_000, 1_000_000, PriceTier.PRO, true), 1e-9)
        // 分数 tokens：50 万命中 × 0.05/百万 = 0.025
        assertEquals(0.025, UsagePricer.costYuan(500_000, 0, 0, PriceTier.FLASH, false), 1e-9)
    }

    @Test
    fun cost_unknown_tier_returns_zero() {
        assertEquals(0.0, UsagePricer.costYuan(9_999_999, 9_999_999, 9_999_999, PriceTier.UNKNOWN, true), 1e-9)
    }

    // ═══ 计费双条件 ═══

    @Test
    fun should_bill_requires_both_conditions() {
        assertTrue(UsagePricer.shouldBill("https://api.deepseek.com/chat/completions", true))
        assertFalse(UsagePricer.shouldBill("https://api.example.com/chat/completions", true))
        assertFalse(UsagePricer.shouldBill("https://api.deepseek.com/chat/completions", false))
    }
}
