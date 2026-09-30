package com.lovebrain.app.ui.panel.suggest

import com.lovebrain.app.model.SuggestTip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锦囊结果态那两处**纯算法**的格子（跟着内容块一起从 `ui/panel/SuggestPanel.kt` 搬进本包）：
 * [groupTipsByCategory] 的时机分组顺序、[vectorMean] 的五维均值。
 *
 * 为什么单独立一格而不是全交给语义树：这两件事判的是**顺序与算术**，
 * 屏幕上"看起来差不多"和"真的按 现在可用 → 今天可准备 → 有机会再做 → 更多建议 排"之间，
 * 只有纯函数这一格能一句话分辨；而它无状态、不需要挂 Compose，跑得起也红得起。
 * 内容到底有没有画到树上，归 `SuggestResultContentSemanticsTest`。
 */
class SuggestResultLogicTest {

    private fun tip(id: String, category: String) =
        SuggestTip(id = id, timingCategory = category, action = "做法-$id")

    @Test
    fun `groups follow the timing order and the leftovers land last`() {
        val grouped = groupTipsByCategory(
            listOf(
                tip("t1", "有机会再做"),
                tip("t2", ""),                       // 没分类
                tip("t3", "现在可用"),
                tip("t4", "今天可准备"),
                tip("t5", "她方便的时候")             // 词表之外的分类 → 也算没分类
            )
        )
        assertEquals(
            "分组顺序必须是 现在可用 → 今天可准备 → 有机会再做 → 更多建议，实到 " + grouped.map { it.first },
            listOf("现在可用", "今天可准备", "有机会再做", "更多建议"), grouped.map { it.first }
        )
        assertEquals(listOf("t3"), grouped[0].second.map { it.id })
        assertEquals(listOf("t4"), grouped[1].second.map { it.id })
        assertEquals(listOf("t1"), grouped[2].second.map { it.id })
        assertEquals("没分类的这两颗要落在最后一组、且保持原相对顺序", listOf("t2", "t5"), grouped[3].second.map { it.id })
    }

    /** 反向证人：某一档根本没东西时**不给它留空表头**——面板那边是"空组直接 continue" */
    @Test
    fun `an empty category does not reserve a header`() {
        val grouped = groupTipsByCategory(listOf(tip("a", "今天可准备"), tip("b", "今天可准备")))
        assertEquals(listOf("今天可准备"), grouped.map { it.first })
        assertEquals(listOf("a", "b"), grouped.single().second.map { it.id })
    }

    @Test
    fun `no tips means no groups at all`() {
        assertTrue("空输入不该凭空长出一组", groupTipsByCategory(emptyList()).isEmpty())
    }

    @Test
    fun `vector mean is the five-dimension average normalised to one`() {
        assertEquals("没有向量时温度是 0，不是 1", 0f, vectorMean(emptyMap()), 0f)
        assertEquals(0.4f, vectorMean(mapOf("亲密" to 20, "信任" to 60)), 1e-6f)
        assertEquals(1f, vectorMean(mapOf("亲密" to 100, "信任" to 100)), 1e-6f)
    }
}
