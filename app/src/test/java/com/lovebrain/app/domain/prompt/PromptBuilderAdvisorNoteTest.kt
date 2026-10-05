package com.lovebrain.app.domain.prompt

import android.content.Context
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.model.ChatMessage
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 等 的**产出一侧**：军师备注必须真的进到发给 provider 的那份 user prompt 里，
 * 而且是全量、不带旧的那句窄解释。
 *
 * 判据取的是 `PromptBuilder` 公开入口吐出来的字符串，不是源码里有没有出现某个符号——
 * 后者是本项目已知的恒真形态（"改了函数名就算改了行为"）。
 * 只 round 那一支不读资产（无知识库），所以夹具只需要一颗 relaxed 的 Context：
 * 一旦有人给这一支塞进资产读取，这一格会因为 classpath 上取不到资产而炸，不是静默绿。
 */
class PromptBuilderAdvisorNoteTest {

    private fun builder(): PromptBuilder {
        // 这一支（kb=null / only-round）不读资产也不读库：mock 只是把构造参数填满。
        // 真有人给它加上资产读取，上面 KDoc 说的那件事就会发生——测试当场炸，不是静默绿。
        val ctx = mockk<Context>(relaxed = true)
        return PromptBuilder(ctx, InMemoryKnowledgePort(), null, FixedClock())
    }

    private val twoMsgs = listOf(
        ChatMessage(id = "m1", role = ChatMessage.Role.HER, content = "今天真的好累"),
        ChatMessage(id = "m2", role = ChatMessage.Role.ME, content = "那早点休息")
    )

    /**
     * 备注全量进 only-round prompt，且旧的"用户想这样回 / 润色出4种方案"一个字都不剩。
     *
     * 反例：区块换了名但 `PromptBuilder` 还在调旧函数（或反过来只改了旧函数留了第二份实现）
     * ⇒ 这两条 assertFalse 里必有一条红；正文尾巴那条 assertTrue 红则是"展示省略=发送也省略"。
     */
    @Test
    fun onlyRoundPromptCarriesTheWholeNoteAndNoOldNarrowWording() = runBlocking {
        val note = "我其实知道她今天加班，别再问她忙不忙；另外今晚先别提见面那件事的尾巴"
        val prompt = builder().buildReplyUserPromptOnlyThisRound(twoMsgs, note).prompt

        assertTrue("备注正文没进发给军师的 prompt", prompt.contains(note))
        assertTrue("备注段没有身份标题：$prompt", prompt.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))
        assertFalse("仍在把备注读成待发送话术", prompt.contains("用户想这样回"))
        assertFalse("仍在把输出数量钉成 4 种", prompt.contains("润色出4种方案"))
        assertFalse("旧段名残留", prompt.contains("# 用户的回复想法"))
        // 真实对话仍在围栏里，没被备注通道吞掉
        assertTrue("她的消息仍在对话围栏里", prompt.contains("今天真的好累"))
    }

    /** 没有备注 → 整段不出现（屏上不留空标题，prompt 里也不留空段） */
    @Test
    fun emptyNoteEmitsNoSectionInThePrompt() = runBlocking {
        val prompt = builder().buildReplyUserPromptOnlyThisRound(twoMsgs, "   ").prompt
        assertFalse("空备注仍画了一段标题：$prompt", prompt.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))
    }

    /**
     * 正常回复那支（无知识库分支，走 PromptBudget）同样不许裁备注正文。
     *
     * 反例：预算裁剪把 ideaBlock 也当成可裁的那一段 ⇒ 用户的长提醒后半句在预算压力下丢掉。
     */
    @Test
    fun budgetPathKeepsTheNoteBodyIntact() = runBlocking {
        val note = "第一条补充：" + "约".repeat(300) + "；最后这半句是别走开"
        val prompt = builder().buildReplyUserPrompt(null, twoMsgs, note)

        assertTrue("备注后半句被裁掉了", prompt.contains("最后这半句是别走开"))
        assertFalse("旧措辞还在正常回复那支里", prompt.contains("用户想这样回"))
        assertTrue("备注段身份标题在场", prompt.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))
    }
}
