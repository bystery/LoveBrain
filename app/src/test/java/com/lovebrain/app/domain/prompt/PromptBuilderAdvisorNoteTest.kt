package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.model.ChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户备注（旧称"军师备注"）必须真的进到发给 provider 的那份 user prompt 里，
 * 而且是全量、不带旧的那句窄解释；2026-11-21 起标题改为「用户备注」。
 *
 * 判据取的是 `PromptBuilder` 公开入口吐出来的字符串，不是源码里有没有出现某个符号。
 * 只 round 那一支不读资产（无知识库），所以夹具给的是一颗**一读就抛**的假资产口。
 */
class PromptBuilderAdvisorNoteTest {

    private fun builder(): PromptBuilder {
        return PromptBuilder(
            PromptSourcePort { path -> error("这一支不该读资产：$path") },
            InMemoryKnowledgePort(), FixedClock()
        )
    }

    private val twoMsgs = listOf(
        ChatMessage(id = "m1", role = ChatMessage.Role.HER, content = "今天真的好累"),
        ChatMessage(id = "m2", role = ChatMessage.Role.ME, content = "那早点休息")
    )

    /**
     * 备注全量进 only-round prompt，且旧的"用户想这样回 / 润色出4种方案"一个字都不剩。
     */
    @Test
    fun onlyRoundPromptCarriesTheWholeNoteAndNoOldNarrowWording() = runBlocking {
        val note = "我其实知道她今天加班，别再问她忙不忙；另外今晚先别提见面那件事的尾巴"
        val prompt = builder().buildReplyUserPromptOnlyThisRound(twoMsgs, note).prompt

        assertTrue("备注正文没进发给军师的 prompt", prompt.contains(note))
        assertTrue("备注段没有身份标题：$prompt", prompt.contains(IntentIdeaBlock.USER_NOTE_HEADER))
        assertFalse("仍在把备注读成待发送话术", prompt.contains("用户想这样回"))
        assertFalse("仍在把输出数量钉成 4 种", prompt.contains("润色出4种方案"))
        assertFalse("旧段名残留", prompt.contains("# 用户的回复想法"))
        // 真实对话仍在围栏里，没被备注通道吞掉
        assertTrue("她的消息仍在对话围栏里", prompt.contains("今天真的好累"))
        // 备注排在 chat 之后、时间之前
        val iClose = prompt.indexOf("</chat>")
        val iNote = prompt.indexOf(IntentIdeaBlock.USER_NOTE_HEADER)
        val iTime = prompt.indexOf("【当前时间】")
        assertTrue("备注必须在围栏之后、时间戳之前", iClose >= 0 && iClose < iNote && iNote < iTime)
    }

    /** 没有备注 → 整段不出现（屏上不留空标题，prompt 里也不留空段） */
    @Test
    fun emptyNoteEmitsNoSectionInThePrompt() = runBlocking {
        val prompt = builder().buildReplyUserPromptOnlyThisRound(twoMsgs, "   ").prompt
        assertFalse("空备注仍画了一段标题：$prompt", prompt.contains(IntentIdeaBlock.USER_NOTE_HEADER))
    }

    /**
     * 正常回复那支（无知识库分支，走 PromptBudget）同样不许裁备注正文。
     */
    @Test
    fun budgetPathKeepsTheNoteBodyIntact() = runBlocking {
        val note = "第一条补充：" + "约".repeat(300) + "；最后这半句是别走开"
        val prompt = builder().buildReplyUserPrompt(null, twoMsgs, note)

        assertTrue("备注后半句被裁掉了", prompt.contains("最后这半句是别走开"))
        assertFalse("旧措辞还在正常回复那支里", prompt.contains("用户想这样回"))
        assertTrue("备注段身份标题在场", prompt.contains(IntentIdeaBlock.USER_NOTE_HEADER))
        assertTrue("备注排在 chat 之后", prompt.indexOf("</chat>") < prompt.indexOf(IntentIdeaBlock.USER_NOTE_HEADER))
    }

    /** 影子链路默认标题不变：不传 header 时仍是「军师备注」 */
    @Test
    fun defaultHeaderStaysAdvisorNoteForTheShadowChain() {
        val out = IntentIdeaBlock.buildAdvisorNoteBlock("先听听她的")
        assertTrue(out.startsWith(IntentIdeaBlock.NOTE_BLOCK_HEADER))
        assertFalse(out.startsWith(IntentIdeaBlock.USER_NOTE_HEADER))
    }
}
