package com.lovebrain.app.viewmodel

import com.lovebrain.app.domain.FactSubjectResolver
import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.DialogueSpeaker
import com.lovebrain.app.model.EntityRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F08: FactSubjectResolver candidate rejection tests.
 *
 * 封板期策略：AI subject_candidate 不参与解析。
 * Level1/2 无法确定时返回 UNKNOWN，不信任 AI candidate。
 *
 * P1-12: subject_candidate 参数已从 FactSubjectResolver.resolve() 移除，
 * 模型不再被要求生成此字段。测试验证 Level1/2/3 逻辑不依赖 candidate。
 */
class SubjectCandidateRejectionTest {

    // Level 1: PARTNER 说"我..." → ME
    // F11: 模型按用户视角写"我今天加班到很晚"——"我"是用户视角的"我"，subject=ME
    @Test
    fun level1_partner_says_my_subject_is_her() {
        val result = FactSubjectResolver.resolve(
            factText = "我今天加班到很晚",
            speaker = EntityRef.HER,
            sourceIds = listOf("m0"),
            dialogue = listOf(DialogueMessage("m0", DialogueSpeaker.PARTNER, "我今天加班到很晚"))
        )
        assertEquals(EntityRef.ME, result)
    }

    // Level 1: PARTNER 说"你..." → HER
    // F11: 模型按用户视角写"你最近很忙吗"——"你"是用户视角的对方，subject=HER
    @Test
    fun level1_partner_says_your_subject_is_me() {
        val result = FactSubjectResolver.resolve(
            factText = "你最近很忙吗",
            speaker = EntityRef.HER,
            sourceIds = listOf("m0"),
            dialogue = listOf(DialogueMessage("m0", DialogueSpeaker.PARTNER, "你最近很忙吗"))
        )
        assertEquals(EntityRef.HER, result)
    }

    // Level 1: USER 说"我..." → ME
    @Test
    fun level1_user_says_my_subject_is_me() {
        val result = FactSubjectResolver.resolve(
            factText = "我明天要出差",
            speaker = EntityRef.ME,
            sourceIds = listOf("m0"),
            dialogue = listOf(DialogueMessage("m0", DialogueSpeaker.USER, "我明天要出差"))
        )
        assertEquals(EntityRef.ME, result)
    }

    // Level 2: 以"她"开头 → HER
    @Test
    fun level2_starts_with_she_subject_is_her() {
        val result = FactSubjectResolver.resolve(
            factText = "她喜欢猫",
            speaker = EntityRef.ME,
            sourceIds = listOf("m0"),
            dialogue = listOf(DialogueMessage("m0", DialogueSpeaker.USER, "她喜欢猫"))
        )
        assertEquals(EntityRef.HER, result)
    }

    // Level 3: 无法确定时 → UNKNOWN（不依赖 AI candidate）
    @Test
    fun level3_undetermined_returns_unknown() {
        val result = FactSubjectResolver.resolve(
            factText = "周末天气不错",
            speaker = EntityRef.HER,
            sourceIds = listOf("m0"),
            dialogue = listOf(DialogueMessage("m0", DialogueSpeaker.PARTNER, "周末天气不错"))
        )
        assertEquals(EntityRef.UNKNOWN, result)
    }

    // Level 3: 模糊文本 → UNKNOWN
    @Test
    fun level3_undetermined_returns_unknown_for_neutral_text() {
        val result = FactSubjectResolver.resolve(
            factText = "电影很好看",
            speaker = EntityRef.ME,
            sourceIds = listOf("m0"),
            dialogue = listOf(DialogueMessage("m0", DialogueSpeaker.USER, "电影很好看"))
        )
        assertEquals(EntityRef.UNKNOWN, result)
    }

    // UNKNOWN speaker 不走 Level 1 → UNKNOWN
    @Test
    fun unknown_speaker_level1_returns_null_falls_to_unknown() {
        val result = FactSubjectResolver.resolve(
            factText = "我今天去了公园",
            speaker = EntityRef.UNKNOWN,
            sourceIds = listOf("m0"),
            dialogue = listOf(DialogueMessage("m0", DialogueSpeaker.PARTNER, "我今天去了公园"))
        )
        assertEquals(EntityRef.UNKNOWN, result)
    }
}
