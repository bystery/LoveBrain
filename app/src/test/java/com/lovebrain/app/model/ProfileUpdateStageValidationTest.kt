package com.lovebrain.app.model

import org.junit.Assert.*
import org.junit.Test

/**
 * P1-02 回归测试：ProfileUpdate 阶段校验统一使用 StageCatalog 八阶段白名单。
 * 验证 StageCatalog 的合法阶段通过、非法阶段被拒、缺失字段报错。
 */
class ProfileUpdateStageValidationTest {

    // ═══ 合法阶段（StageCatalog 八阶段）必须通过 ═══

    @Test
    fun `valid stage with suffix passes validation`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":"初识期"}"""
        val update = ProfileUpdate.parse(json)
        assertTrue("valid should be true: ${update.error}", update.valid)
        assertEquals("初识期", update.newStage)
    }

    @Test
    fun `all StageCatalog stages pass validation`() {
        // 逐个验证 StageCatalog 的 8 个合法阶段
        com.lovebrain.app.domain.StageCatalog.ALL.forEach { stage ->
            val json = """{"me":"内容","stage_changed":true,"new_stage":"$stage"}"""
            val update = ProfileUpdate.parse(json)
            assertTrue("stage '$stage' should be valid: ${update.error}", update.valid)
            assertEquals(stage, update.newStage)
        }
    }

    @Test
    fun `stage without suffix gets normalized`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":"初识"}"""
        val update = ProfileUpdate.parse(json)
        assertTrue("valid should be true: ${update.error}", update.valid)
        assertEquals("初识期", update.newStage)
    }

    // ═══ 非法阶段必须被拒 ═══

    @Test
    fun `old invalid stage rejected - 认识期 not in StageCatalog`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":"认识期"}"""
        val update = ProfileUpdate.parse(json)
        assertFalse("认识期 should be rejected (not in StageCatalog)", update.valid)
        assertNull(update.newStage)
    }

    @Test
    fun `old invalid stage rejected - 试探期 not in StageCatalog`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":"试探期"}"""
        val update = ProfileUpdate.parse(json)
        assertFalse("试探期 should be rejected", update.valid)
    }

    @Test
    fun `old invalid stage rejected - 约会期 not in StageCatalog`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":"约会期"}"""
        val update = ProfileUpdate.parse(json)
        assertFalse("约会期 should be rejected", update.valid)
    }

    @Test
    fun `old invalid stage rejected - 平淡期 not in StageCatalog`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":"平淡期"}"""
        val update = ProfileUpdate.parse(json)
        assertFalse("平淡期 should be rejected", update.valid)
    }

    @Test
    fun `old invalid stage rejected - 倦怠期 not in StageCatalog`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":"倦怠期"}"""
        val update = ProfileUpdate.parse(json)
        assertFalse("倦怠期 should be rejected", update.valid)
    }

    @Test
    fun `arbitrary string rejected as stage`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":"随便一个阶段"}"""
        val update = ProfileUpdate.parse(json)
        assertFalse("arbitrary string should be rejected", update.valid)
    }

    // ═══ stage_changed=true 但字段缺失/空必须报错 ═══

    @Test
    fun `stage_changed true with missing new_stage reports error`() {
        val json = """{"me":"内容","stage_changed":true}"""
        val update = ProfileUpdate.parse(json)
        assertFalse("missing new_stage with stage_changed=true should be invalid", update.valid)
        assertNotNull(update.error)
        assertTrue("error should mention new_stage", update.error!!.contains("new_stage"))
    }

    @Test
    fun `stage_changed true with empty new_stage reports error`() {
        val json = """{"me":"内容","stage_changed":true,"new_stage":""}"""
        val update = ProfileUpdate.parse(json)
        assertFalse("empty new_stage with stage_changed=true should be invalid", update.valid)
    }

    // ═══ stage_changed=false 或缺失时 new_stage 不校验 ═══

    @Test
    fun `stage_changed false allows missing new_stage`() {
        val json = """{"me":"内容","stage_changed":false}"""
        val update = ProfileUpdate.parse(json)
        assertTrue("valid should be true: ${update.error}", update.valid)
    }

    @Test
    fun `stage_changed absent allows missing new_stage`() {
        val json = """{"me":"内容"}"""
        val update = ProfileUpdate.parse(json)
        assertTrue("valid should be true: ${update.error}", update.valid)
    }
}
