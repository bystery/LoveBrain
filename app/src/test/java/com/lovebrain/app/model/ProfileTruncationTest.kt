package com.lovebrain.app.model

import com.lovebrain.app.util.Jsons
import org.junit.Assert.*
import org.junit.Test

/**
 * P0-2 / P1-2: 画像更新截断相关回归测试。
 *
 * 覆盖场景：
 * A 完整裸 JSON → valid
 * B Markdown 围栏 JSON → valid
 * C 前后带说明文字但仅有一个完整对象 → valid
 * D JSON 被截断（无闭合 }）→ TRUNCATED, valid=false
 * E 字符串内部含 { } → 不误判括号
 * F 两个候选 JSON 对象 → 取第一个完整对象
 * G 合法 JSON + 非法阶段 → INVALID_SCHEMA（不是格式错误）
 * H finish_reason=length → TRUNCATED
 * I 空内容 → EMPTY
 */
class ProfileTruncationTest {

    // ═══ A: 完整裸 JSON ═══

    @Test
    fun `A - bare JSON is valid`() {
        val raw = """{"me":"他是一个温柔的人","her":"她喜欢猫","warmth":"温暖"}"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
        assertTrue(result.profileUpdate?.valid == true)
    }

    // ═══ B: Markdown 围栏 JSON ═══

    @Test
    fun `B - markdown fenced JSON is valid`() {
        val raw = """```json
{"me":"他是一个温柔的人","her":"她喜欢猫","warmth":"温暖"}
```"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
        assertTrue(result.profileUpdate?.valid == true)
    }

    // ═══ C: 前后带说明文字但仅有一个完整对象 ═══

    @Test
    fun `C - JSON with surrounding text extracts valid object`() {
        val raw = """这是画像更新建议：
{"me":"他是一个温柔的人","her":"她喜欢猫","warmth":"温暖"}
以上为本次更新内容。"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
        assertTrue(result.profileUpdate?.valid == true)
    }

    // ═══ D: JSON 被截断（无闭合 }） ═══

    @Test
    fun `D - truncated JSON without closing brace is detected as TRUNCATED`() {
        val raw = """{"me":"abc","her":"def""""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.TRUNCATED, result.status)
        // 不产生可确认写入
        val profileUpdate = result.profileUpdate
        assertNotNull("should have a ProfileUpdate with valid=false", profileUpdate)
        assertFalse("truncated JSON must not be valid", profileUpdate!!.valid)
    }

    @Test
    fun `D2 - truncated JSON in code fence is detected as TRUNCATED`() {
        val raw = """```json
{"me":"abc","her":"def"
```"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.TRUNCATED, result.status)
    }

    // ═══ E: 字符串内部含 { } 不误判括号 ═══

    @Test
    fun `E - braces inside string values do not break extraction`() {
        val raw = """{"me":"他有{特殊}性格","her":"她喜欢{猫}","warmth":"温暖"}"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
        assertTrue(result.profileUpdate?.valid == true)
    }

    @Test
    fun `E2 - nested braces in string are handled correctly`() {
        val raw = """{"me":"代码{test{nested}}","her":"她","warmth":"温"}"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
    }

    // ═══ F: 两个候选 JSON 对象（取第一个完整对象） ═══

    @Test
    fun `F - first complete JSON object is extracted when multiple exist`() {
        val raw = """{"me":"第一段","her":"她","warmth":"温"}{"me":"第二段","her":"她2","warmth":"温2"}"""
        val result = ProfileUpdate.parseWithStatus(raw)
        // 应该提取第一个完整对象
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
        assertEquals("第一段", result.profileUpdate?.me)
    }

    // ═══ G: 合法 JSON + 非法阶段 → INVALID_SCHEMA ═══

    @Test
    fun `G - valid JSON with invalid stage is INVALID_SCHEMA not TRUNCATED`() {
        val raw = """{"me":"内容","stage_changed":true,"new_stage":"约会期"}"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.INVALID_SCHEMA, result.status)
        // 不是截断，是 schema 问题
        assertNotEquals("should not be TRUNCATED for invalid stage", 
            ProfileParseStatus.TRUNCATED, result.status)
    }

    @Test
    fun `G2 - valid JSON with valid stage is SUCCESS`() {
        val raw = """{"me":"内容","stage_changed":true,"new_stage":"破冰期"}"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
    }

    // ═══ H: finish_reason=length → TRUNCATED ═══

    @Test
    fun `H - finish_reason length is detected as TRUNCATED`() {
        // 即使 content 看似完整，finish_reason=length 明确截断
        val raw = """{"me":"内容","her":"她","warmth":"温"}"""
        val result = ProfileUpdate.parseWithStatus(raw, finishReason = "length")
        assertEquals(ProfileParseStatus.TRUNCATED, result.status)
        assertEquals("length", result.finishReason)
    }

    @Test
    fun `H2 - finish_reason stop is not truncated`() {
        val raw = """{"me":"内容","her":"她","warmth":"温"}"""
        val result = ProfileUpdate.parseWithStatus(raw, finishReason = "stop")
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
    }

    @Test
    fun `H3 - finish_reason null is treated as normal`() {
        val raw = """{"me":"内容","her":"她","warmth":"温"}"""
        val result = ProfileUpdate.parseWithStatus(raw, finishReason = null)
        assertEquals(ProfileParseStatus.SUCCESS, result.status)
    }

    // ═══ I: 空内容 → EMPTY ═══

    @Test
    fun `I - empty content is EMPTY`() {
        val result = ProfileUpdate.parseWithStatus("")
        assertEquals(ProfileParseStatus.EMPTY, result.status)
    }

    @Test
    fun `I2 - blank content is EMPTY`() {
        val result = ProfileUpdate.parseWithStatus("   ")
        assertEquals(ProfileParseStatus.EMPTY, result.status)
    }

    // ═══ shouldRetry 逻辑验证 ═══

    @Test
    fun `TRUNCATED status should retry`() {
        val raw = """{"me":"abc"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertTrue("TRUNCATED should be retryable", result.shouldRetry)
    }

    @Test
    fun `EMPTY status should retry`() {
        val result = ProfileUpdate.parseWithStatus("")
        assertTrue("EMPTY should be retryable", result.shouldRetry)
    }

    @Test
    fun `INVALID_SCHEMA status should not retry`() {
        val raw = """{"me":"内容","stage_changed":true,"new_stage":"约会期"}"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertFalse("INVALID_SCHEMA should not be retryable", result.shouldRetry)
    }

    @Test
    fun `SUCCESS status should not retry`() {
        val raw = """{"me":"内容","her":"她","warmth":"温"}"""
        val result = ProfileUpdate.parseWithStatus(raw)
        assertFalse("SUCCESS should not be retryable", result.shouldRetry)
    }

    // ═══ Jsons.extractJsonObject 直接测试 ═══

    @Test
    fun `Jsons extractJsonObject returns null for truncated input`() {
        val truncated = """{"me":"abc","her":"def""""
        val result = Jsons.extractJsonObject(truncated)
        assertNull("truncated JSON should return null", result)
    }

    @Test
    fun `Jsons extractJsonObject handles escaped quotes in strings`() {
        val raw = """{"me":"他说\"你好\"","her":"她","warmth":"温"}"""
        val result = Jsons.extractJsonObject(raw)
        assertNotNull(result)
    }

    @Test
    fun `Jsons extractJsonObject handles escaped backslash`() {
        val raw = """{"me":"路径C:\\Users","her":"她","warmth":"温"}"""
        val result = Jsons.extractJsonObject(raw)
        assertNotNull(result)
    }
}
