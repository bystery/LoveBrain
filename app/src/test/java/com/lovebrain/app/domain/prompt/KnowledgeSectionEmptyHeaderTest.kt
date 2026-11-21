package com.lovebrain.app.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 空段不出标题：画像/此刻整段没有可注入内容时，连段头一起省略。
 *
 * 反例：me/her/topic/scene 全空仍拼出 `# 【懂得】关系画像` + `# 【此刻】…`，
 * 模型会去找章节所说的内容，而 prompt 里根本没有。
 */
class KnowledgeSectionEmptyHeaderTest {

    private fun input(
        me: String = "",
        her: String = "",
        stageSection: String = "",
        topic: String = "",
        sceneChain: String = ""
    ) = PromptKnowledgeSection.Input(
        kbName = "kb",
        me = me,
        her = her,
        stageSection = stageSection,
        topic = topic,
        sceneChain = sceneChain,
        corrections = emptyMap(),
        nowMs = 1_790_208_000_000L,
        today = "2026-09-24"
    )

    @Test
    fun `an entirely empty knowledge section renders nothing at all`() {
        val out = PromptKnowledgeSection.build(input())
        assertEquals("没有任何可注入内容时不该留下空段头", "", out.text)
        assertTrue("也没有历史块", out.historicalBlock.isEmpty())
        assertTrue("没有引用", out.refs.isEmpty())
    }

    @Test
    fun `the profile header appears only when a profile body does`() {
        val onlyHer = PromptKnowledgeSection.build(input(her = "她：猫奴"))
        assertTrue(onlyHer.text.startsWith(PromptKnowledgeSection.PROFILE_HEADER))
        assertTrue(onlyHer.text.contains("## 她\n她：猫奴"))
        assertFalse("没有此刻内容就不出此刻段头", onlyHer.text.contains(PromptKnowledgeSection.MOMENT_HEADER))

        val topicOnly = PromptKnowledgeSection.build(input(topic = "手冲咖啡"))
        assertFalse("没有画像就不出画像段头", topicOnly.text.contains(PromptKnowledgeSection.PROFILE_HEADER))
        assertTrue(topicOnly.text.startsWith(PromptKnowledgeSection.MOMENT_HEADER))
        assertTrue(topicOnly.text.contains("当前话题：手冲咖啡"))
    }

    @Test
    fun `the core subset renders nothing when me her and warmth are all blank`() {
        assertEquals(
            "",
            PromptCoreKnowledgeSection.build(PromptCoreKnowledgeSection.Input(me = " ", her = "", warmth = "\n"))
        )
        val withWarmth = PromptCoreKnowledgeSection.build(
            PromptCoreKnowledgeSection.Input(me = "", her = "", warmth = "温度：轻松")
        )
        assertTrue(withWarmth.startsWith("# 【懂得】关系画像"))
        assertTrue(withWarmth.contains("## 我们\n温度：轻松"))
    }
}
