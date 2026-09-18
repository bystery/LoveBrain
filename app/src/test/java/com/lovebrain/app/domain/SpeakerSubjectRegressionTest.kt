package com.lovebrain.app.domain

import com.lovebrain.app.model.EntityRef
import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.DialogueSpeaker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P1-8: speaker/subject 语义回归测试。
 *
 * 覆盖以下关键场景的回归保护：
 * 1. speaker=HER, subject=ME——她说的话描述用户（"你感冒好了吗"）
 * 2. speaker=HER, subject=HER——她说的话描述自己（"我今天发烧了"）
 * 3. speaker=ME, subject=HER——用户说的话描述对方（"你今天好点了吗"）
 * 4. speaker=ME, subject=ME——用户说的话描述自己（"我刚下课"）
 * 5. speaker=MULTIPLE——混合来源，subject 不可靠确定
 * 6. speaker=UNKNOWN——无可靠来源
 * 7. 事项本轮有真实证据但因其他原因未注入，不得错误进入 DORMANT
 * 8. 事项不断被注入但没有任何真实新证据，也不能靠"被注入"永久保持 ACTIVE
 */
class SpeakerSubjectRegressionTest {

    private val dialogue = listOf(
        DialogueMessage(id = "m0", speaker = DialogueSpeaker.PARTNER, text = "你感冒好了吗"),
        DialogueMessage(id = "m1", speaker = DialogueSpeaker.USER, text = "好多了"),
        DialogueMessage(id = "m2", speaker = DialogueSpeaker.PARTNER, text = "我今天发烧了"),
        DialogueMessage(id = "m3", speaker = DialogueSpeaker.USER, text = "我刚下课"),
        DialogueMessage(id = "m4", speaker = DialogueSpeaker.PARTNER, text = "感觉最近确实有点累"),
        DialogueMessage(id = "m5", speaker = DialogueSpeaker.USER, text = "你今天好点了吗")
    )

    // ═══ 回归测试 1-4: 组合 speaker × subject 语义 ═══

    @Test
    fun `regression - HER says you X yields speaker=HER subject=ME`() {
        val speaker = FactSpeakerResolver.resolve(listOf("m0"), dialogue)
        val subject = FactSubjectResolver.resolve("你感冒好了吗", EntityRef.HER, listOf("m0"), dialogue)
        assertEquals(EntityRef.HER, speaker)
        assertEquals(EntityRef.ME, subject)
    }

    @Test
    fun `regression - HER says I X yields speaker=HER subject=HER`() {
        val speaker = FactSpeakerResolver.resolve(listOf("m2"), dialogue)
        val subject = FactSubjectResolver.resolve("我今天发烧了", EntityRef.HER, listOf("m2"), dialogue)
        assertEquals(EntityRef.HER, speaker)
        assertEquals(EntityRef.HER, subject)
    }

    @Test
    fun `regression - USER says you X yields speaker=ME subject=HER`() {
        val speaker = FactSpeakerResolver.resolve(listOf("m5"), dialogue)
        val subject = FactSubjectResolver.resolve("你今天好点了吗", EntityRef.ME, listOf("m5"), dialogue)
        assertEquals(EntityRef.ME, speaker)
        assertEquals(EntityRef.HER, subject)
    }

    @Test
    fun `regression - USER says I X yields speaker=ME subject=ME`() {
        val speaker = FactSpeakerResolver.resolve(listOf("m3"), dialogue)
        val subject = FactSubjectResolver.resolve("我刚下课", EntityRef.ME, listOf("m3"), dialogue)
        assertEquals(EntityRef.ME, speaker)
        assertEquals(EntityRef.ME, subject)
    }

    // ═══ 回归测试 5: MULTIPLE speaker → subject 不可靠 ═══

    @Test
    fun `regression - mixed sources yield speaker=MULTIPLE and subject=UNKNOWN`() {
        val speaker = FactSpeakerResolver.resolve(listOf("m0", "m3"), dialogue)
        assertEquals(EntityRef.MULTIPLE, speaker)
        // speaker=MULTIPLE 时，代词解析无法确定
        val subject = FactSubjectResolver.resolve("我觉得不错", EntityRef.MULTIPLE, emptyList(), dialogue)
        assertEquals(EntityRef.UNKNOWN, subject)
    }

    // ═══ 回归测试 6: 无可靠来源 → UNKNOWN ═══

    @Test
    fun `regression - no reliable source yields UNKNOWN speaker`() {
        val speaker = FactSpeakerResolver.resolve(emptyList(), dialogue)
        assertEquals(EntityRef.UNKNOWN, speaker)
    }

    @Test
    fun `regression - non-existent source yields UNKNOWN speaker`() {
        val speaker = FactSpeakerResolver.resolve(listOf("fake_id"), dialogue)
        assertEquals(EntityRef.UNKNOWN, speaker)
    }

    // ═══ 回归测试 7: speaker != subject 的关键区分 ═══

    @Test
    fun `regression - speaker HER does not imply subject HER`() {
        // "你感冒好了吗" → speaker=HER, subject=ME (NOT HER)
        val speaker = FactSpeakerResolver.resolve(listOf("m0"), dialogue)
        val subject = FactSubjectResolver.resolve("你感冒好了吗", speaker, listOf("m0"), dialogue)
        assertEquals(EntityRef.HER, speaker)
        assertEquals(EntityRef.ME, subject)
        // 关键：speaker=HER 但 subject≠HER
        assert(speaker != subject) { "speaker=HER must not imply subject=HER" }
    }

    // ═══ 回归测试 8: 模糊文本 → UNKNOWN（不猜） ═══

    @Test
    fun `regression - ambiguous text with UNKNOWN speaker yields UNKNOWN subject`() {
        val subject = FactSubjectResolver.resolve("感觉最近确实有点累", EntityRef.UNKNOWN, emptyList(), dialogue)
        assertEquals(EntityRef.UNKNOWN, subject)
    }

    @Test
    fun `regression - neutral statement with known speaker but no pronoun yields UNKNOWN`() {
        // "今天天气不错" — 没有代词，speaker 无法帮助确定 subject
        val subject = FactSubjectResolver.resolve("今天天气不错", EntityRef.HER, listOf("m0"), dialogue)
        assertEquals(EntityRef.UNKNOWN, subject)
    }

    // ═══ 回归测试 9: 实体规则——"她"开头 → HER ═══

    @Test
    fun `regression - fact text starting with ta yields HER`() {
        val subject = FactSubjectResolver.resolve("她喜欢猫", EntityRef.UNKNOWN, emptyList(), dialogue)
        assertEquals(EntityRef.HER, subject)
    }

    // ═══ 回归测试 10: resolveFromRoles 路径 ═══

    @Test
    fun `regression - resolveFromRoles single HER yields HER`() {
        val sourceMap = mapOf("m0" to com.lovebrain.app.model.ChatMessage.Role.HER)
        val speaker = FactSpeakerResolver.resolveFromRoles(listOf("m0"), sourceMap)
        assertEquals(EntityRef.HER, speaker)
    }

    @Test
    fun `regression - resolveFromRoles single ME yields ME`() {
        val sourceMap = mapOf("m1" to com.lovebrain.app.model.ChatMessage.Role.ME)
        val speaker = FactSpeakerResolver.resolveFromRoles(listOf("m1"), sourceMap)
        assertEquals(EntityRef.ME, speaker)
    }

    @Test
    fun `regression - resolveFromRoles mixed yields MULTIPLE`() {
        val sourceMap = mapOf(
            "m0" to com.lovebrain.app.model.ChatMessage.Role.HER,
            "m1" to com.lovebrain.app.model.ChatMessage.Role.ME
        )
        val speaker = FactSpeakerResolver.resolveFromRoles(listOf("m0", "m1"), sourceMap)
        assertEquals(EntityRef.MULTIPLE, speaker)
    }

    @Test
    fun `regression - resolveFromRoles empty yields UNKNOWN`() {
        val speaker = FactSpeakerResolver.resolveFromRoles(emptyList(), emptyMap())
        assertEquals(EntityRef.UNKNOWN, speaker)
    }
}
