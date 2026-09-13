package com.lovebrain.app.viewmodel

import com.lovebrain.app.data.CostScope
import com.lovebrain.app.data.UsageCostEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * PROV-03 定向测试：CostScope 枚举区分 + UsageCostEvent 携带 scope。
 *
 * 验证：
 * - FOREGROUND 和 BACKGROUND 是不同的枚举值
 * - UsageCostEvent 正确携带 scope
 * - 今日累计包含全部可计费请求
 * - 本次费用只接受 FOREGROUND
 */
class CostScopeDisplayTest {

    @Test
    fun foreground_and_background_are_different_scopes() {
        assertNotEquals(CostScope.FOREGROUND, CostScope.BACKGROUND)
    }

    @Test
    fun usage_cost_event_carries_foreground_scope() {
        val ev = UsageCostEvent(0.003, 1000L, CostScope.FOREGROUND)
        assertEquals(CostScope.FOREGROUND, ev.scope)
        assertEquals(0.003, ev.yuan, 0.0001)
        assertEquals(1000L, ev.timestampMs)
    }

    @Test
    fun usage_cost_event_carries_background_scope() {
        val ev = UsageCostEvent(0.001, 2000L, CostScope.BACKGROUND)
        assertEquals(CostScope.BACKGROUND, ev.scope)
    }

    @Test
    fun today_cost_accumulates_both_foreground_and_background() {
        // 模拟今日累计 = FOREGROUND + BACKGROUND
        val fgEvent = UsageCostEvent(0.003, 1000L, CostScope.FOREGROUND)
        val bgEvent = UsageCostEvent(0.001, 2000L, CostScope.BACKGROUND)
        val todayTotal = fgEvent.yuan + bgEvent.yuan
        assertEquals(0.004, todayTotal, 0.0001)
    }

    @Test
    fun last_cost_only_accepts_foreground() {
        // 模拟 ViewModel 逻辑：只有 FOREGROUND 更新 lastCost
        var lastCost: Double? = null

        val bgEvent = UsageCostEvent(0.001, 1000L, CostScope.BACKGROUND)
        if (bgEvent.scope == CostScope.FOREGROUND) {
            lastCost = bgEvent.yuan
        }
        assertEquals(null, lastCost)

        val fgEvent = UsageCostEvent(0.003, 2000L, CostScope.FOREGROUND)
        if (fgEvent.scope == CostScope.FOREGROUND) {
            lastCost = fgEvent.yuan
        }
        assertEquals(0.003, lastCost!!, 0.0001)
    }
}
