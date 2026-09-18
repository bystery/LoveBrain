package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-2 回归测试：ProfileUpdateSchema 中被声明为 valid 的示例都必须 parser-valid。
 *
 * P0-4: 不再存在"声称合法、实际 parser invalid"的 fallback。
 * 所有 schema 描述中的合法 JSON 示例必须通过 parser 校验。
 */
class ProfileSchemaValidExamplesTest {

    @Test
    fun `minimal valid profile with only me is parser-valid`() {
        val json = """{"me":"新的我"}"""
        val result = ProfileUpdate.parse(json)
        assertTrue("Minimal valid profile should be parser-valid", result.valid)
    }

    @Test
    fun `valid profile with only her is parser-valid`() {
        val json = """{"her":"新的她"}"""
        val result = ProfileUpdate.parse(json)
        assertTrue(result.valid)
    }

    @Test
    fun `valid profile with only warmth is parser-valid`() {
        val json = """{"warmth":"温暖"}"""
        val result = ProfileUpdate.parse(json)
        assertTrue(result.valid)
    }

    @Test
    fun `full valid profile is parser-valid`() {
        val json = """{"me":"我","her":"她","warmth":"温","stage_changed":false,"observations":["obs"],"message_to_user":"msg"}"""
        val result = ProfileUpdate.parse(json)
        assertTrue(result.valid)
    }

    @Test
    fun `profile with stage change is parser-valid`() {
        val json = """{"me":"更新","stage_changed":true,"new_stage":"暧昧期"}"""
        val result = ProfileUpdate.parse(json)
        assertTrue(result.valid)
        assertTrue(result.stageChanged)
        assertEquals("暧昧期", result.newStage)
    }

    @Test
    fun `profile without any profile field is parser-invalid`() {
        val json = """{"stage_changed":false,"observations":[],"message_to_user":"no profile"}"""
        val result = ProfileUpdate.parse(json)
        assertFalse("Profile without me/her/warmth must be invalid", result.valid)
        assertTrue(result.error!!.contains("画像字段"))
    }

    @Test
    fun `compact schema text does not contain fake-valid fallback`() {
        val compact = ProfileUpdateSchema.compactSchemaForPrompt()
        assertFalse(compact.contains("fallback.*valid".toRegex(RegexOption.IGNORE_CASE)))
        assertTrue(compact.contains("至少提供一个"))
    }

    @Test
    fun `all schema fields are mentioned in schema description`() {
        val desc = ProfileUpdateSchema.schemaDescriptionForPrompt()
        ProfileUpdateSchema.ALL_FIELDS.forEach { field ->
            assertTrue("Schema description should mention $field", desc.contains(field))
        }
    }

    @Test
    fun `strict schema mentions at least one required field`() {
        val strict = ProfileUpdateSchema.strictSchemaForPrompt()
        assertTrue(strict.contains("至少提供"))
    }
}
