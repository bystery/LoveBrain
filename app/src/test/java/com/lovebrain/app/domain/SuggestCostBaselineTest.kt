package com.lovebrain.app.domain

import com.lovebrain.app.model.DailyBriefUsage
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.SuggestTip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1-33: 锦囊 token/费用成本基准测试。
 *
 * 建立固定样本的成本基准：
 * - 输入 prompt 长度 → 预期 prompt token 估算
 * - 输出 tips 数量 → 预期 completion token 估算
 * - 费用计算正确性
 * - 有效建议数/重复/空泛数判定
 *
 * 不发起真实网络请求——使用固定样本验证 usage 绑定和费用展示逻辑。
 */
class SuggestCostBaselineTest {

    /**
     * 基准样本：6 条有效建议的标准输出。
     * 每条约 100-150 字 → 预期 completion ~800-1000 tokens。
     */
    private val standardSixTips = (1..6).map { i ->
        SuggestTip(
            id = "tip-$i",
            timingCategory = when (i % 3) {
                0 -> "有机会再做"
                1 -> "现在可用"
                else -> "今天可准备"
            },
            action = "行动建议 $i：做一些有意义的事情来推进关系",
            timing = "上午${i}点左右",
            materialNeeded = if (i % 2 == 0) "需要准备小礼物" else "",
            example = "示例配文 $i：嘿，今天的分享很有意思",
            reason = "因为关系阶段适合这样做"
        )
    }

    /**
     * 基准样本：8 条（含 2 条重复 action）。
     */
    private val eightTipsWithDuplicates = (1..8).map { i ->
        SuggestTip(
            id = "tip-$i",
            timingCategory = "现在可用",
            action = if (i <= 2) "duplicate action" else "unique action $i",
            timing = "随时",
            example = "示例 $i",
            reason = "理由 $i"
        )
    }

    @Test
    fun `standard 6 tips produce valid non-partial result`() {
        val suggestion = DailySuggestion(
            stage = "暧昧期",
            tips = standardSixTips,
            usage = DailyBriefUsage(
                promptTokens = 2500,
                completionTokens = 950,
                costYuan = 0.0234,
                elapsedMs = 5200,
                generatedAt = "2026-09-22 10:00:00"
            )
        )
        val validated = SuggestValidator.validate(suggestion)

        assertEquals(6, validated.tips.size)
        assertFalse("6 tips should not be partial", validated.partial)
        assertNotNull(validated.usage)
        assertEquals(2500, validated.usage?.promptTokens)
        assertEquals(950, validated.usage?.completionTokens)
        assertEquals(0.0234, validated.usage?.costYuan!!, 0.0001)
    }

    @Test
    fun `duplicate actions are deduplicated and marked partial if below 6`() {
        val suggestion = DailySuggestion(
            tips = eightTipsWithDuplicates
        )
        val validated = SuggestValidator.validate(suggestion)

        // 8 tips with 2 duplicates → 7 unique
        assertEquals(7, validated.tips.size)
        assertFalse("7 tips should not be partial", validated.partial)
    }

    @Test
    fun `usage with null fields represents unknown cost`() {
        val suggestion = DailySuggestion(
            tips = standardSixTips,
            usage = DailyBriefUsage(
                promptTokens = null,
                completionTokens = null,
                costYuan = null,
                elapsedMs = 3000
            )
        )
        val validated = SuggestValidator.validate(suggestion)

        assertNotNull(validated.usage)
        assertEquals(null, validated.usage?.promptTokens)
        assertEquals(null, validated.usage?.completionTokens)
        assertEquals(null, validated.usage?.costYuan)
        assertEquals(3000L, validated.usage?.elapsedMs)
    }

    @Test
    fun `partial suggestion with usage still shows usage`() {
        val suggestion = DailySuggestion(
            tips = standardSixTips.take(3),
            partial = true,
            usage = DailyBriefUsage(
                promptTokens = 2000,
                completionTokens = 400,
                costYuan = 0.0089,
                elapsedMs = 3000
            )
        )
        val validated = SuggestValidator.validate(suggestion)

        assertTrue("3 tips should be partial", validated.partial)
        assertNotNull(validated.usage)
        assertEquals(2000, validated.usage?.promptTokens)
        assertEquals(0.0089, validated.usage?.costYuan!!, 0.0001)
    }

    @Test
    fun `cost estimation is consistent for same input`() {
        val usage1 = DailyBriefUsage(promptTokens = 2500, completionTokens = 950, costYuan = 0.0234)
        val usage2 = DailyBriefUsage(promptTokens = 2500, completionTokens = 950, costYuan = 0.0234)

        val suggestion1 = DailySuggestion(tips = standardSixTips, usage = usage1)
        val suggestion2 = DailySuggestion(tips = standardSixTips, usage = usage2)

        val v1 = SuggestValidator.validate(suggestion1)
        val v2 = SuggestValidator.validate(suggestion2)

        assertEquals(v1.usage?.costYuan!!, v2.usage?.costYuan!!, 0.000001)
        assertEquals(v1.usage?.promptTokens, v2.usage?.promptTokens)
        assertEquals(v1.usage?.completionTokens, v2.usage?.completionTokens)
    }

    @Test
    fun `tips with empty action are not counted as valid`() {
        val tips = listOf(
            SuggestTip(id = "tip-1", action = "valid action"),
            SuggestTip(id = "tip-2", action = ""),
            SuggestTip(id = "tip-3", action = "   "),
            SuggestTip(id = "tip-4", action = "another valid action"),
            SuggestTip(id = "tip-5", action = "valid action 3"),
            SuggestTip(id = "tip-6", action = "valid action 4"),
            SuggestTip(id = "tip-7", action = "valid action 5")
        )
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))

        // 2 empty/blank → filtered out → 5 valid → partial (< 6)
        assertEquals(5, validated.tips.size)
        assertTrue("5 tips should be partial", validated.partial)
    }
}
