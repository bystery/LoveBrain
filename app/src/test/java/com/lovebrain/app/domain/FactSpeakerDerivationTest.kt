package com.lovebrain.app.domain

import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.DialogueSpeaker
import com.lovebrain.app.model.EntityRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P0-3: 事实说话人推导器测试——speaker 由代码从 source_ids 确定性推导。
 *
 * 核心原则：speaker 绝不交给 AI 判断。
 * source_id → DialogueMessage → speaker 是 100% 确定的。
 *
 * 规则：
 * - 所有有效 source IDs 都是 PARTNER → speaker = HER
 * - 所有有效 source IDs 都是 USER → speaker = ME
 * - 来源混合 → speaker = MULTIPLE
 * - 无可靠来源 → speaker = UNKNOWN
 */
class FactSpeakerDerivationTest {

    private val dialogue = listOf(
        DialogueMessage(id = "m0", speaker = DialogueSpeaker.PARTNER, text = "你感冒好了吗"),
        DialogueMessage(id = "m1", speaker = DialogueSpeaker.USER, text = "好多了"),
        DialogueMessage(id = "m2", speaker = DialogueSpeaker.PARTNER, text = "我今天发烧了"),
        DialogueMessage(id = "m3", speaker = DialogueSpeaker.USER, text = "严重吗")
    )

    @Test
    fun `single PARTNER source yields HER`() {
        val result = FactSpeakerResolver.resolve(listOf("m0"), dialogue)
        assertEquals(EntityRef.HER, result)
    }

    @Test
    fun `single USER source yields ME`() {
        val result = FactSpeakerResolver.resolve(listOf("m1"), dialogue)
        assertEquals(EntityRef.ME, result)
    }

    @Test
    fun `multiple PARTNER sources yield HER`() {
        val result = FactSpeakerResolver.resolve(listOf("m0", "m2"), dialogue)
        assertEquals(EntityRef.HER, result)
    }

    @Test
    fun `multiple USER sources yield ME`() {
        val result = FactSpeakerResolver.resolve(listOf("m1", "m3"), dialogue)
        assertEquals(EntityRef.ME, result)
    }

    @Test
    fun `mixed PARTNER and USER sources yield MULTIPLE`() {
        val result = FactSpeakerResolver.resolve(listOf("m0", "m1"), dialogue)
        assertEquals(EntityRef.MULTIPLE, result)
    }

    @Test
    fun `empty source IDs yield UNKNOWN`() {
        val result = FactSpeakerResolver.resolve(emptyList(), dialogue)
        assertEquals(EntityRef.UNKNOWN, result)
    }

    @Test
    fun `non-existent source IDs yield UNKNOWN`() {
        val result = FactSpeakerResolver.resolve(listOf("nonexistent"), dialogue)
        assertEquals(EntityRef.UNKNOWN, result)
    }

    @Test
    fun `mix of valid and invalid IDs uses only valid ones`() {
        val result = FactSpeakerResolver.resolve(listOf("m0", "fake"), dialogue)
        assertEquals(EntityRef.HER, result)
    }

    @Test
    fun `resolveFromRoles with HER role yields HER`() {
        val sourceMap = mapOf("m0" to ChatMessage.Role.HER, "m1" to ChatMessage.Role.ME)
        val result = FactSpeakerResolver.resolveFromRoles(listOf("m0"), sourceMap)
        assertEquals(EntityRef.HER, result)
    }

    @Test
    fun `resolveFromRoles with ME role yields ME`() {
        val sourceMap = mapOf("m0" to ChatMessage.Role.HER, "m1" to ChatMessage.Role.ME)
        val result = FactSpeakerResolver.resolveFromRoles(listOf("m1"), sourceMap)
        assertEquals(EntityRef.ME, result)
    }

    @Test
    fun `resolveFromRoles with mixed roles yields MULTIPLE`() {
        val sourceMap = mapOf("m0" to ChatMessage.Role.HER, "m1" to ChatMessage.Role.ME)
        val result = FactSpeakerResolver.resolveFromRoles(listOf("m0", "m1"), sourceMap)
        assertEquals(EntityRef.MULTIPLE, result)
    }
}
