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

    @Test
    fun rollTodayCost_same_day_keeps_and_cross_day_resets() {
        // 同日期：保留存量
        assertEquals(3.2, LoveBrainViewModel.rollTodayCost("2026-08-30", 3.2, "2026-08-30"))
        // 同日期但金额为 null（脏数据）：兜底 0
        assertEquals(0.0, LoveBrainViewModel.rollTodayCost("2026-08-30", null, "2026-08-30"))
        // 跨天：清零
        assertEquals(0.0, LoveBrainViewModel.rollTodayCost("2026-08-29", 5.5, "2026-08-30"))
        // 无存档（首启）：清零
        assertEquals(0.0, LoveBrainViewModel.rollTodayCost(null, null, "2026-08-30"))
    }
}
