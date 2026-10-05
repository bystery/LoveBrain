package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回复侧那一条 prompt 总预算的数值读数（`AppConfig.TOTAL_BUDGET`）。
 *
 * 2026-10-03 锦囊整族退场（用户原话"锦囊功能可以全部删除了，删除所有的代码"）：
 * `AppConfig.SUGGEST_BUDGET` 已不在主源码里，钉它那三格比例/区间的判据的被测主体没了，
 * 那三格随之删掉。留这一格的理由是它量的是**回复**那一路：`PromptBudget.byBlocks`
 * 与 `applyBudget` 都按这一格裁上下文，数值一漂等于把回复的输入窗口改小或改大，
 * 那是另一件事，不该跟着锦囊一起走。
 */
class DailyBriefBudgetTest {

    @Test
    fun `TOTAL_BUDGET remains unchanged at 9000`() {
        assertTrue(
            "TOTAL_BUDGET should remain 9000 for reply, got ${AppConfig.TOTAL_BUDGET}",
            AppConfig.TOTAL_BUDGET == 9000
        )
    }
}
