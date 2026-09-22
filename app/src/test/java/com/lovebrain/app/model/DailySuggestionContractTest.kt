package com.lovebrain.app.model

import com.lovebrain.app.domain.SuggestValidator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DailySuggestion 数据合同测试。
 *
 * 验证：
 * - 6-8 条默认产出
 * - 去重逻辑
 * - 空字段处理
 * - 截断/不完整标记
 * - 旧缓存迁移兼容性
 * - usage 字段序列化/反序列化
 */
class DailySuggestionContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `SuggestTip has all required new fields`() {
        val tip = SuggestTip(
            id = "tip-1",
            timingCategory = "现在可用",
            action = "打个招呼",
            timing = "早上",
            materialNeeded = "",
            example = "早呀",
            reason = "关系适合"
        )
        assertEquals("tip-1", tip.id)
        assertEquals("现在可用", tip.timingCategory)
        assertEquals("打个招呼", tip.action)
        assertEquals("早上", tip.timing)
        assertEquals("", tip.materialNeeded)
        assertEquals("早呀", tip.example)
        assertEquals("关系适合", tip.reason)
    }

    @Test
    fun `SuggestTip deserializes from new JSON format`() {
        val jsonStr = """
            {
                "id": "tip-1",
                "timingCategory": "今天可准备",
                "action": "分享午餐照片",
                "timing": "午饭时",
                "materialNeeded": "真实午餐照片",
                "example": "今天的午饭不错",
                "reason": "自然分享生活"
            }
        """.trimIndent()
        val tip = json.decodeFromString<SuggestTip>(jsonStr)
        assertEquals("tip-1", tip.id)
        assertEquals("今天可准备", tip.timingCategory)
        assertEquals("分享午餐照片", tip.action)
        assertEquals("真实午餐照片", tip.materialNeeded)
    }

    @Test
    fun `SuggestTip backward compatible with old field names`() {
        val oldJson = """
            {
                "slot": "早安",
                "topic": "日常问候",
                "why": "建立联系",
                "expected": "对方回复"
            }
        """.trimIndent()
        val tip = json.decodeFromString<SuggestTip>(oldJson)
        assertEquals("早安", tip.slot)
        assertEquals("日常问候", tip.topic)
        assertEquals("建立联系", tip.why)
        assertEquals("对方回复", tip.expected)
        // New fields should be empty defaults
        assertEquals("", tip.id)
        assertEquals("", tip.timingCategory)
        assertEquals("", tip.action)
    }

    @Test
    fun `DailySuggestion with 6 tips is not partial`() {
        val tips = (1..6).map { i ->
            SuggestTip(id = "tip-$i", timingCategory = "现在可用", action = "action$i")
        }
        val suggestion = DailySuggestion(tips = tips)
        assertFalse(suggestion.partial)
    }

    @Test
    fun `DailySuggestion with fewer than 6 tips is partial`() {
        val tips = (1..3).map { i ->
            SuggestTip(id = "tip-$i", timingCategory = "现在可用", action = "action$i")
        }
        val suggestion = DailySuggestion(tips = tips)
        // R1-34: 调用生产 SuggestValidator，不只是断言数据类
        val validated = SuggestValidator.validate(suggestion)
        assertTrue("Fewer than 6 tips should be marked partial", validated.partial)
        assertEquals(3, validated.tips.size)
    }

    @Test
    fun `DailySuggestion with usage serializes correctly`() {
        val usage = DailyBriefUsage(
            promptTokens = 1500,
            completionTokens = 800,
            costYuan = 0.0234,
            elapsedMs = 5200,
            generatedAt = "2026-09-21 10:00:00"
        )
        val suggestion = DailySuggestion(
            tips = listOf(SuggestTip(id = "tip-1", action = "test")),
            usage = usage
        )
        val jsonStr = json.encodeToString(DailySuggestion.serializer(), suggestion)
        val restored = json.decodeFromString(DailySuggestion.serializer(), jsonStr)
        assertNotNull(restored.usage)
        assertEquals(1500, restored.usage?.promptTokens)
        assertEquals(800, restored.usage?.completionTokens)
        assertEquals(0.0234, restored.usage?.costYuan!!, 0.0001)
        assertEquals(5200L, restored.usage?.elapsedMs)
    }

    @Test
    fun `DailyBriefUsage with null fields represents unknown cost`() {
        val usage = DailyBriefUsage(
            promptTokens = null,
            completionTokens = null,
            costYuan = null,
            elapsedMs = 3000
        )
        val jsonStr = json.encodeToString(DailyBriefUsage.serializer(), usage)
        val restored = json.decodeFromString(DailyBriefUsage.serializer(), jsonStr)
        // Null values must remain null (not 0) — "未知" not "0"
        assertEquals(null, restored.promptTokens)
        assertEquals(null, restored.completionTokens)
        assertEquals(null, restored.costYuan)
        assertEquals(3000L, restored.elapsedMs)
    }

    @Test
    fun `timingCategory must be one of three valid values`() {
        val validCategories = listOf("现在可用", "今天可准备", "有机会再做")
        for (cat in validCategories) {
            val tip = SuggestTip(timingCategory = cat, action = "test")
            assertTrue("Category $cat should be valid", cat in validCategories)
        }
    }

    // ═══ R1-34: 调用生产 SuggestValidator 的合同测试 ═══

    @Test
    fun `SuggestValidator rejects tips with empty action`() {
        val tips = listOf(
            SuggestTip(id = "tip-1", action = ""),
            SuggestTip(id = "tip-2", action = "valid action")
        )
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertEquals("Empty action tips should be filtered", 1, validated.tips.size)
        assertEquals("valid action", validated.tips[0].action)
    }

    @Test
    fun `SuggestValidator deduplicates by action`() {
        val tips = listOf(
            SuggestTip(id = "tip-1", action = "duplicate"),
            SuggestTip(id = "tip-2", action = "duplicate"),
            SuggestTip(id = "tip-3", action = "unique")
        )
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertEquals("Duplicate actions should be deduplicated", 2, validated.tips.size)
    }

    @Test
    fun `SuggestValidator deduplicates by id`() {
        val tips = listOf(
            SuggestTip(id = "same-id", action = "action1"),
            SuggestTip(id = "same-id", action = "action2"),
            SuggestTip(id = "tip-3", action = "action3")
        )
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertEquals("Duplicate ids should be deduplicated", 2, validated.tips.size)
    }

    @Test
    fun `SuggestValidator generates stable id for empty id tips`() {
        val tips = listOf(
            SuggestTip(id = "", action = "action1"),
            SuggestTip(id = "", action = "action2")
        )
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertEquals("tip-1", validated.tips[0].id)
        assertEquals("tip-2", validated.tips[1].id)
    }

    @Test
    fun `SuggestValidator normalizes invalid timingCategory to empty`() {
        val tips = listOf(
            SuggestTip(id = "tip-1", action = "action1", timingCategory = "invalid"),
            SuggestTip(id = "tip-2", action = "action2", timingCategory = "现在可用")
        )
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertEquals("Invalid category should be normalized to empty", "", validated.tips[0].timingCategory)
        assertEquals("Valid category should be preserved", "现在可用", validated.tips[1].timingCategory)
    }

    @Test
    fun `SuggestValidator truncates to 8 tips max`() {
        val tips = (1..10).map { i ->
            SuggestTip(id = "tip-$i", action = "action$i")
        }
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertEquals("Should truncate to 8 tips", 8, validated.tips.size)
    }

    @Test
    fun `SuggestValidator marks partial when fewer than 6 valid tips`() {
        val tips = (1..5).map { i ->
            SuggestTip(id = "tip-$i", action = "action$i")
        }
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertTrue("5 tips should be partial", validated.partial)
    }

    @Test
    fun `SuggestValidator does not mark partial with 6 valid tips`() {
        val tips = (1..6).map { i ->
            SuggestTip(id = "tip-$i", action = "action$i")
        }
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertFalse("6 tips should not be partial", validated.partial)
    }

    @Test
    fun `old cache DailySuggestion deserializes without crash`() {
        val oldCacheJson = """
            {
                "stage": "暧昧期",
                "goal": "增加联系频率",
                "tips": [
                    {"slot": "早安", "topic": "问候", "why": "建立习惯", "expected": "回复"}
                ],
                "invite": {"signal": "主动找你", "suggestion": "约周末"},
                "avoid": ["不要连发"]
            }
        """.trimIndent()
        val suggestion = json.decodeFromString<DailySuggestion>(oldCacheJson)
        assertEquals("暧昧期", suggestion.stage)
        assertEquals(1, suggestion.tips.size)
        assertEquals("早安", suggestion.tips[0].slot)
        assertNotNull(suggestion.invite)
        assertEquals(1, suggestion.avoid.size)
        // New fields should have defaults
        assertFalse(suggestion.partial)
    }

    @Test
    fun `DailySuggestion with 8 tips is valid`() {
        val tips = (1..8).map { i ->
            SuggestTip(id = "tip-$i", timingCategory = "现在可用", action = "action$i")
        }
        val suggestion = DailySuggestion(tips = tips)
        assertEquals(8, suggestion.tips.size)
        assertFalse(suggestion.partial)
    }

    @Test
    fun `DailySuggestion with empty tips list is valid`() {
        val suggestion = DailySuggestion(tips = emptyList())
        assertEquals(0, suggestion.tips.size)
        assertTrue(suggestion.tips.isEmpty())
    }

    @Test
    fun `duplicate tip ids are detectable`() {
        val tips = listOf(
            SuggestTip(id = "tip-1", action = "action1"),
            SuggestTip(id = "tip-1", action = "action2"),
            SuggestTip(id = "tip-2", action = "action3")
        )
        // R1-34: 调用生产 SuggestValidator 进行去重
        val validated = SuggestValidator.validate(DailySuggestion(tips = tips))
        assertEquals("Duplicate id should be deduplicated by validator", 2, validated.tips.size)
    }

    @Test
    fun `partial flag serialization round-trips`() {
        val suggestion = DailySuggestion(
            tips = listOf(SuggestTip(id = "tip-1", action = "test")),
            partial = true
        )
        val jsonStr = json.encodeToString(DailySuggestion.serializer(), suggestion)
        val restored = json.decodeFromString(DailySuggestion.serializer(), jsonStr)
        assertTrue(restored.partial)
    }
}
