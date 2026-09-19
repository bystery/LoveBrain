package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-13 回归测试：画像 schema 单一真源一致性。
 *
 * 确保 parser 和 prompt schema 描述一致：
 * - me/her/warmth 可选，存在时必须非空
 * - 缺失字段 = 不更新
 * - 至少一个画像字段非空才有效
 * - compact fallback 不使用空字符串
 */
class ProfileSchemaConsistencyTest {

    @Test
    fun `schema description mentions all fields`() {
        val desc = ProfileUpdateSchema.schemaDescriptionForPrompt()
        listOf("me", "her", "warmth", "stage_changed", "new_stage", "observations", "message_to_user").forEach {
            assertTrue("Schema description should mention $it", desc.contains(it))
        }
    }

    @Test
    fun `strict schema mentions non-empty requirement`() {
        val strict = ProfileUpdateSchema.strictSchemaForPrompt()
        assertTrue(strict.contains("非空字符串"))
        assertTrue(strict.contains("缺失=不更新"))
    }

    @Test
    fun `compact schema requires at least one profile field`() {
        // P0-4: compact schema 不再提供伪合法 fallback。
        // 不含 me/her/warmth 的 JSON 被 parser 正确拒绝。
        val noProfileField = """{"stage_changed":false,"observations":[],"message_to_user":"画像更新未能生成，请重试"}"""
        val result = ProfileUpdate.parse(noProfileField)
        assertFalse("JSON without any profile field must be invalid", result.valid)
        assertTrue(result.error!!.contains("画像字段"))
    }

    @Test
    fun `parser accepts valid profile with all fields`() {
        val json = """{"me":"my update","her":"her update","warmth":"warm","stage_changed":false,"observations":["test"],"message_to_user":"msg"}"""
        val result = ProfileUpdate.parse(json)
        assertTrue(result.valid)
        assertEquals("my update", result.me)
        assertEquals("her update", result.her)
        assertEquals("warm", result.warmth)
        assertEquals(listOf("test"), result.observations)
        assertEquals("msg", result.messageToUser)
    }

    @Test
    fun `parser accepts partial update - only me`() {
        val json = """{"me":"my update"}"""
        val result = ProfileUpdate.parse(json)
        assertTrue(result.valid)
        assertEquals("my update", result.me)
        assertNull(result.her)
        assertNull(result.warmth)
    }

    @Test
    fun `parser rejects empty string for me`() {
        val json = """{"me":""}"""
        val result = ProfileUpdate.parse(json)
        assertFalse(result.valid)
        assertTrue(result.error!!.contains("me"))
    }

    @Test
    fun `parser rejects all three fields missing`() {
        val json = """{"stage_changed":false,"observations":[]}"""
        val result = ProfileUpdate.parse(json)
        assertFalse(result.valid)
        assertTrue(result.error!!.contains("画像字段"))
    }

    @Test
    fun `parser accepts stage_changed with valid new_stage`() {
        val json = """{"me":"update","stage_changed":true,"new_stage":"暧昧期"}"""
        val result = ProfileUpdate.parse(json)
        assertTrue(result.valid)
        assertTrue(result.stageChanged)
        assertEquals("暧昧期", result.newStage)
    }

    @Test
    fun `parser rejects stage_changed=true without new_stage`() {
        val json = """{"me":"update","stage_changed":true}"""
        val result = ProfileUpdate.parse(json)
        assertFalse(result.valid)
        assertTrue(result.error!!.contains("new_stage"))
    }

    @Test
    fun `parser rejects stage_changed=true with invalid stage`() {
        val json = """{"me":"update","stage_changed":true,"new_stage":"不是阶段"}"""
        val result = ProfileUpdate.parse(json)
        assertFalse(result.valid)
    }

    @Test
    fun `compact schema does not provide fake-valid fallback JSON`() {
        // P0-4: compact schema 不再设计伪合法 fallback JSON。
        // schema 只描述真正合法的画像更新（me/her/warmth 至少提供一个）。
        // 达到最大重试次数仍失败时，由 Coordinator 产生 typed failure。
        val compact = ProfileUpdateSchema.compactSchemaForPrompt()
        // compact schema 不应包含伪造的 fallback JSON 示例
        assertFalse("compact schema must not contain fake fallback JSON", compact.contains("stage_changed.*observations.*message_to_user".toRegex()))
        // compact schema 应明确说明至少需要一个画像字段
        assertTrue("compact schema must mention at least one profile field required", compact.contains("至少提供一个"))
    }

    // ═══ P0-8: new_stage Presence 一致性——不能同时出现"可选"和"必填" ═══

    @Test
    fun `new_stage is conditional in normal schema - not optional`() {
        val desc = ProfileUpdateSchema.schemaDescriptionForPrompt()
        // new_stage 应该是"条件必填"，不是"可选"
        val newStageLine = desc.lines().firstOrNull { it.contains("new_stage") }
        assertNotNull("new_stage should be mentioned in normal schema", newStageLine)
        assertTrue("new_stage should be '条件必填' in normal schema: $newStageLine",
            newStageLine!!.contains("条件必填"))
        assertFalse("new_stage should NOT be '可选' in normal schema: $newStageLine",
            newStageLine.contains("可选"))
    }

    @Test
    fun `new_stage is conditional in strict schema - not optional`() {
        val strict = ProfileUpdateSchema.strictSchemaForPrompt()
        val newStageLine = strict.lines().firstOrNull { it.contains("new_stage") }
        assertNotNull("new_stage should be mentioned in strict schema", newStageLine)
        assertTrue("new_stage should be '条件必填' in strict schema: $newStageLine",
            newStageLine!!.contains("条件必填"))
        assertFalse("new_stage should NOT be '可选' in strict schema: $newStageLine",
            newStageLine.contains("可选"))
    }

    @Test
    fun `new_stage is conditional in compact schema - not optional`() {
        val compact = ProfileUpdateSchema.compactSchemaForPrompt()
        val newStageLine = compact.lines().firstOrNull { it.contains("new_stage") }
        assertNotNull("new_stage should be mentioned in compact schema", newStageLine)
        assertTrue("new_stage should be '条件必填' in compact schema: $newStageLine",
            newStageLine!!.contains("条件必填"))
    }

    @Test
    fun `normal schema toLine has balanced parentheses`() {
        val desc = ProfileUpdateSchema.schemaDescriptionForPrompt()
        // 每行字段描述的括号应该闭合
        desc.lines().filter { it.startsWith("- ") }.forEach { line ->
            val openCount = line.count { it == '（' }
            val closeCount = line.count { it == '）' }
            assertEquals("Parentheses should be balanced in: $line", openCount, closeCount)
        }
    }
}
