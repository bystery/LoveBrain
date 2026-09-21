package com.lovebrain.app.domain

import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.DialogueSpeaker
import com.lovebrain.app.model.EntityRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P0-4: 事实主体解析器测试——subject 与 speaker 分离。
 *
 * subject 表示事实描述的对象（谁的事），不同于 speaker（谁说的）。
 *
 * 三层解析：
 * - Level 1: 代码可确定——当前消息 speaker + 明确代词"我/你"
 * - Level 2: 实体规则——显式名字、人称引用目标
 * - Level 3: 语义不确定——UNKNOWN（宁可 UNKNOWN，不猜）
 */
class FactSubjectResolutionTest {

    private val dialogue = listOf(
        DialogueMessage(id = "m0", speaker = DialogueSpeaker.PARTNER, text = "你感冒好了吗"),
        DialogueMessage(id = "m1", speaker = DialogueSpeaker.USER, text = "好多了"),
        DialogueMessage(id = "m2", speaker = DialogueSpeaker.PARTNER, text = "我今天发烧了"),
        DialogueMessage(id = "m3", speaker = DialogueSpeaker.USER, text = "我刚下课")
    )

    // ═══ Level 1: 代码可确定——代词 + speaker ═══

    @Test
    fun `PARTNER says my X subject ME`() {
        // F11: 模型按用户视角写"我今天发烧了"——"我"是用户视角的"我"，subject=ME
        val result = FactSubjectResolver.resolve("我今天发烧了", EntityRef.HER, listOf("m2"), dialogue)
        assertEquals(EntityRef.ME, result)
    }

    @Test
    fun `PARTNER says you X subject HER`() {
        // F11: 模型按用户视角写"你感冒好了吗"——"你"是用户视角的对方，subject=HER
        val result = FactSubjectResolver.resolve("你感冒好了吗", EntityRef.HER, listOf("m0"), dialogue)
        assertEquals(EntityRef.HER, result)
    }

    @Test
    fun `USER says my X subject ME`() {
        // "我刚下课" — USER 在说自己
        val result = FactSubjectResolver.resolve("我刚下课", EntityRef.ME, listOf("m3"), dialogue)
        assertEquals(EntityRef.ME, result)
    }

    @Test
    fun `USER says you X subject HER`() {
        val result = FactSubjectResolver.resolve("你今天好点了吗", EntityRef.ME, listOf("m1"), dialogue)
        assertEquals(EntityRef.HER, result)
    }

    // ═══ Level 1: 更多代词变体 ═══

    @Test
    fun `PARTNER says I do not subject ME`() {
        // F11: speaker=HER + "我..." → subject=ME（模型按用户视角写）
        val result = FactSubjectResolver.resolve("我不太确定", EntityRef.HER, listOf("m0"), dialogue)
        assertEquals(EntityRef.ME, result)
    }

    @Test
    fun `PARTNER says I went subject ME`() {
        // F11: speaker=HER + "我..." → subject=ME（模型按用户视角写）
        val result = FactSubjectResolver.resolve("我去超市了", EntityRef.HER, listOf("m0"), dialogue)
        assertEquals(EntityRef.ME, result)
    }

    @Test
    fun `USER says I want subject ME`() {
        val result = FactSubjectResolver.resolve("我要睡了", EntityRef.ME, listOf("m1"), dialogue)
        assertEquals(EntityRef.ME, result)
    }

    // ═══ Level 2: 实体规则 ═══

    @Test
    fun `fact text starts with ta subject HER`() {
        val result = FactSubjectResolver.resolve("她喜欢猫", EntityRef.UNKNOWN, emptyList(), dialogue)
        assertEquals(EntityRef.HER, result)
    }

    // ═══ Level 3: 语义不确定 -> UNKNOWN ═══

    @Test
    fun `ambiguous text with UNKNOWN speaker yields UNKNOWN`() {
        val result = FactSubjectResolver.resolve("感觉最近确实有点累", EntityRef.UNKNOWN, emptyList(), dialogue)
        assertEquals(EntityRef.UNKNOWN, result)
    }

    @Test
    fun `neutral statement with known speaker but no pronoun yields UNKNOWN`() {
        // "今天天气不错" — 没有明确代词，speaker 无法帮助确定 subject
        val result = FactSubjectResolver.resolve("今天天气不错", EntityRef.HER, listOf("m0"), dialogue)
        assertEquals(EntityRef.UNKNOWN, result)
    }

    @Test
    fun `MULTIPLE speaker yields UNKNOWN for pronoun resolution`() {
        // speaker = MULTIPLE 时，代词解析无法确定
        val result = FactSubjectResolver.resolve("我觉得不错", EntityRef.MULTIPLE, emptyList(), dialogue)
        // Level 1: speaker=MULTIPLE -> null; Level 2: starts with 我 -> check speaker==HER? No -> null; -> UNKNOWN
        assertEquals(EntityRef.UNKNOWN, result)
    }
}
