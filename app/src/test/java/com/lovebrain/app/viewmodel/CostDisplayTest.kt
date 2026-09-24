package com.lovebrain.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ：花费展示纯函数回归（LoveBrainViewModel companion）。
 * formatYuan 三位小数（主人 2026-08-30 纠正，原两位小数口径作废）+ 固定小数点；rollTodayCost 跨天清零/同日保持。
 */
class CostDisplayTest {

    @Test
    fun formatYuan_three_decimals_fixed_point() {
        assertEquals("0.000", LoveBrainViewModel.formatYuan(0.0))
        assertEquals("1.500", LoveBrainViewModel.formatYuan(1.5))
        assertEquals("0.010", LoveBrainViewModel.formatYuan(0.005 + 0.005))
        assertEquals("12.351", LoveBrainViewModel.formatYuan(12.351))
        assertEquals("12.344", LoveBrainViewModel.formatYuan(12.344))
    }

    /**
     * 跨天滚动的四条判据，从 `rollTodayCost` 原样搬来——那条函数与类里那个
     * `todayCostDate` var 是两把尺，现在只剩 `UsageStats.loaded` 这一处。
     */
    @Test
    fun rollTodayCost_same_day_keeps_and_cross_day_resets() {
        fun todayCost(saved: Pair<String, Double>?): Double =
            UsageStats.loaded(today = "2026-08-30", savedTodayCost = saved).todayCostYuan

        // 同日期：保留存量
        assertEquals(3.2, todayCost("2026-08-30" to 3.2))
        // 同日期、存量为 0（旧用例传的是 `Double? = null`；`loadTodayCost()` 本来就给不出
        // null 金额，那个可空参数是过宽的签名，搬过来之后这一格改成"存量为 0"）
        assertEquals(0.0, todayCost("2026-08-30" to 0.0))
        // 跨天：清零
        assertEquals(0.0, todayCost("2026-08-29" to 5.5))
        // 无存档（首启）：清零
        assertEquals(0.0, todayCost(null))
    }
}
