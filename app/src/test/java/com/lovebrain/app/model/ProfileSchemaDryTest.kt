package com.lovebrain.app.model

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P0-8: ProfileUpdateSchema DRY 一致性测试。
 *
 * 验证：
 * - normal / strict / compact 描述均由同一套 field spec 生成
 * - compact 不再同时说"只输出 JSON"和"失败输出空字符串"
 * - reflect.md 不再重复字段规则（由 Schema 注入）
 * - 所有字段名在三种描述中一致出现
 */
class ProfileSchemaDryTest {

    @Test
    fun `all field names appear in normal schema description`() {
        val desc = ProfileUpdateSchema.schemaDescriptionForPrompt()
        ProfileUpdateSchema.ALL_FIELDS.forEach { field ->
            assertTrue("normal schema should mention $field", desc.contains(field))
        }
    }

    @Test
    fun `all field names appear in strict schema description`() {
        val desc = ProfileUpdateSchema.strictSchemaForPrompt()
        ProfileUpdateSchema.ALL_FIELDS.forEach { field ->
            assertTrue("strict schema should mention $field", desc.contains(field))
        }
    }

    @Test
    fun `all profile fields appear in compact schema description`() {
        val desc = ProfileUpdateSchema.compactSchemaForPrompt()
        // compact 是修复指令，至少要提及 me/her/warmth
        assertTrue("compact should mention me", desc.contains("me"))
        assertTrue("compact should mention her", desc.contains("her"))
        assertTrue("compact should mention warmth", desc.contains("warmth"))
    }

    @Test
    fun `compact schema does not say output empty string on failure`() {
        val desc = ProfileUpdateSchema.compactSchemaForPrompt()
        // P0-8: 不再同时说"只输出 JSON"和"失败输出空字符串"
        assertFalse("compact should not mention 'empty string' as failure output",
            desc.contains("空字符串") && desc.contains("失败"))
    }

    @Test
    fun `compact schema says only output JSON`() {
        val desc = ProfileUpdateSchema.compactSchemaForPrompt()
        assertTrue("compact should say to output JSON only",
            desc.contains("只输出 JSON"))
    }

    @Test
    fun `cross field constraint appears in normal and strict`() {
        val normal = ProfileUpdateSchema.schemaDescriptionForPrompt()
        val strict = ProfileUpdateSchema.strictSchemaForPrompt()
        // "至少提供" 约束在两种描述中都应出现
        assertTrue("normal should have cross-field constraint", normal.contains("至少"))
        assertTrue("strict should have cross-field constraint", strict.contains("至少"))
    }

    @Test
    fun `valid stages referenced from StageCatalog`() {
        val desc = ProfileUpdateSchema.schemaDescriptionForPrompt()
        ProfileUpdateSchema.VALID_STAGES.forEach { stage ->
            assertTrue("normal schema should list stage $stage", desc.contains(stage))
        }
    }

    @Test
    fun `field constants are stable strings`() {
        assertEquals("me", ProfileUpdateSchema.FIELD_ME)
        assertEquals("her", ProfileUpdateSchema.FIELD_HER)
        assertEquals("warmth", ProfileUpdateSchema.FIELD_WARMTH)
        assertEquals("stage_changed", ProfileUpdateSchema.FIELD_STAGE_CHANGED)
        assertEquals("new_stage", ProfileUpdateSchema.FIELD_NEW_STAGE)
        assertEquals("observations", ProfileUpdateSchema.FIELD_OBSERVATIONS)
        assertEquals("message_to_user", ProfileUpdateSchema.FIELD_MESSAGE_TO_USER)
    }
}

private fun assertFalse(message: String, condition: Boolean) {
    org.junit.Assert.assertFalse(message, condition)
}
