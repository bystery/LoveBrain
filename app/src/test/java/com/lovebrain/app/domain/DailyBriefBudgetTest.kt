package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锦囊输入预算测试。
 *
 * 验证：
 * - SUGGEST_BUDGET 小于 TOTAL_BUDGET
 * - 预算值在合理范围（3000-3500）
 * - 锦囊不再复用 buildCoreKnowledgeSubset 的 9000 字符预算
 */
class DailyBriefBudgetTest {

    @Test
    fun `SUGGEST_BUDGET is less than TOTAL_BUDGET`() {
        assertTrue(
            "SUGGEST_BUDGET (${AppConfig.SUGGEST_BUDGET}) should be less than TOTAL_BUDGET (${AppConfig.TOTAL_BUDGET})",
            AppConfig.SUGGEST_BUDGET < AppConfig.TOTAL_BUDGET
        )
    }

    @Test
    fun `SUGGEST_BUDGET is in expected range`() {
        assertTrue(
            "SUGGEST_BUDGET should be in 3000-3500 range, got ${AppConfig.SUGGEST_BUDGET}",
            AppConfig.SUGGEST_BUDGET in 3000..3500
        )
    }

    @Test
    fun `SUGGEST_BUDGET is significantly smaller than reply budget`() {
        val ratio = AppConfig.SUGGEST_BUDGET.toDouble() / AppConfig.TOTAL_BUDGET
        assertTrue(
            "Suggest budget ratio ($ratio) should be less than 0.5",
            ratio < 0.5
        )
    }

    @Test
    fun `TOTAL_BUDGET remains unchanged at 9000`() {
        assertTrue(
            "TOTAL_BUDGET should remain 9000 for reply, got ${AppConfig.TOTAL_BUDGET}",
            AppConfig.TOTAL_BUDGET == 9000
        )
    }
}
