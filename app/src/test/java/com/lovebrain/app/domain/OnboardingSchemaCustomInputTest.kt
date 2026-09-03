package com.lovebrain.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 自定义输入（v4.1 需求文档-问卷v4.1.md）契约单测：
 * answers[step] = -1 哨兵 → profile 文本取 customTexts[step]（trim 后）；
 * -1 不参与 tag 收集、不触发客户端红线判定；切回固定选项后残留 customText 无效。
 */
class OnboardingSchemaCustomInputTest {

    /** 分支 B（Q1 选 B）、Q2-Q5 全固定选项的基础答案 */
    private fun fixedAnswers(): Map<Int, Int> = mapOf(1 to 1, 2 to 0, 3 to 1, 4 to 2, 5 to 0)

    // ═══ tag 收集：-1 跳过 ═══

    @Test
    fun `collectTags skips custom sentinel minus one`() {
        val answers = fixedAnswers() + (2 to -1)
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")
        // Q3/Q4/Q5 固定 → 3 个 tag；Q2 自定义不产生 tag
        assertEquals(3, schema.tags.size)
        assertEquals(
            OnboardingBank.question(3, "B").options[1].tag,
            schema.tags[0]
        )
    }

    // ═══ profile 字段：-1 取 customTexts 文本 ═══

    @Test
    fun `custom text fills profile field with trim`() {
        val answers = fixedAnswers() + (2 to -1)
        val schema = OnboardingSchemaBuilder.build(
            answers, "我", "她", mapOf(2 to "  我们是同事，天天抬头不见低头见  ")
        )
        assertEquals("我们是同事，天天抬头不见低头见", schema.profile.interpersonal_context)
        // 其余固定选项字段不受影响
        assertEquals(
            OnboardingBank.question(3, "B").options[1].text,
            schema.profile.counterpart_feedback
        )
    }

    // ═══ 红线：-1 不触发客户端判定 ═══

    @Test
    fun `sentinel minus one never triggers redline in branch E`() {
        // 分支 E（Q1 选 E），Q2-Q5 全自定义
        val answers = mapOf(1 to 4, 2 to -1, 3 to -1, 4 to -1, 5 to -1)
        val schema = OnboardingSchemaBuilder.build(
            answers, "我", "她",
            mapOf(2 to "a", 3 to "b", 4 to "c", 5 to "d")
        )
        assertFalse(schema.redline_triggered)
        assertEquals("NORMAL_ASSIST", schema.system_directive)
    }

    @Test
    fun `redline still triggers when fixed option is redline`() {
        // 分支 E，Q3 选 D（index=3，拉黑）仍是红线；Q4 自定义不影响判定
        val answers = mapOf(1 to 4, 2 to 0, 3 to 3, 4 to -1, 5 to 2)
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她", mapOf(4 to "自定义文本"))
        assertTrue(schema.redline_triggered)
        assertEquals("SELF_REBUILD_ONLY", schema.system_directive)
    }

    // ═══ 残留 customText：切回固定选项后无效 ═══

    @Test
    fun `stale customText ignored after switching back to fixed option`() {
        val answers = fixedAnswers()  // Q2 固定选 0
        val schema = OnboardingSchemaBuilder.build(
            answers, "我", "她", mapOf(2 to "这是切回固定选项前的旧输入")
        )
        assertEquals(
            OnboardingBank.question(2, "B").options[0].text,
            schema.profile.interpersonal_context
        )
    }

    // ═══ 缺失 customText 与默认参数兼容 ═══

    @Test
    fun `missing customText yields empty string`() {
        val answers = fixedAnswers() + (2 to -1)
        val schema = OnboardingSchemaBuilder.build(answers, "我", "她")
        assertEquals("", schema.profile.interpersonal_context)
    }

    @Test
    fun `build without customTexts param stays backward compatible`() {
        val schema = OnboardingSchemaBuilder.build(fixedAnswers(), "我", "她")
        assertEquals(
            OnboardingBank.question(2, "B").options[0].text,
            schema.profile.interpersonal_context
        )
        assertEquals(4, schema.tags.size)
    }
}
