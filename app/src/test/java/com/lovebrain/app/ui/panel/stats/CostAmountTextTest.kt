package com.lovebrain.app.ui.panel.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * 金额格式化只留一处实现（审计项 3）：首页与面板只选档位，不再各写一遍 `String.format`。
 *
 * 两档的显示口径必须与合并前逐字一致——这张表就是"合并实现不许顺手改显示"的证人：
 * - Home：两位小数、跟随系统 Locale、全角 ￥、取绝对值；
 * - Panel：三位小数、锁 US、半角 ¥。
 */
class CostAmountTextTest {

    @Test
    fun `home style keeps two decimals, default locale and the full-width yuan sign`() {
        assertEquals("￥" + String.format(Locale.getDefault(), "%.2f", 1.5), costAmountText(1.5, CostAmountStyle.Home))
        assertEquals("￥" + String.format(Locale.getDefault(), "%.2f", 2.5), costAmountText(-2.5, CostAmountStyle.Home))
        assertEquals(
            "￥" + String.format(Locale.getDefault(), "%.2f", 0.1234),
            costAmountText(0.1234, CostAmountStyle.Home)
        )
    }

    @Test
    fun `panel style keeps three decimals, US locale and the half-width yen sign`() {
        assertEquals("¥1.500", costAmountText(1.5, CostAmountStyle.Panel))
        assertEquals("¥0.123", costAmountText(0.1234, CostAmountStyle.Panel))
        assertEquals("¥1234.568", costAmountText(1234.5678, CostAmountStyle.Panel))
    }
}
