package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ：军师备注区块的措辞合同。
 *
 * 旧措辞 `# 用户的回复想法 / 用户想这样回：「X」 / 请基于这个方向润色出4种方案。`
 * 干了两件错事：
 * 1. 把**背景、限制、目的、提醒**读成"用户打算发出去的那句话"——
 *    "我其实知道她今天加班，别再问她忙不忙"会被当成待润色的话术；
 * 2. 把输出数量钉死成 4 种，越过了回复任务自己的格式。
 *
 * 下面每一格都判**产出的字符串**，不判源码里有没有某个符号
 * （"grep 到函数名就算过"是本项目已知的恒真形态之一）。
 */
class IntentIdeaBlockNoteTest {

    @Test
    fun noteBlockStopsCallingTheNoteAReplyToPolish() {
        val out = IntentIdeaBlock.buildAdvisorNoteBlock("我现在不想约见面")

        assertTrue("备注段没标出自己的身份：$out", out.startsWith(IntentIdeaBlock.NOTE_BLOCK_HEADER))
        assertTrue("正文没被原样带进去：$out", out.contains("我现在不想约见面"))
        assertFalse("还在把备注读成待发送话术：$out", out.contains("用户想这样回"))
        assertFalse("还在把输出数量钉成 4 种：$out", out.contains("润色出4种方案"))
        assertFalse("旧段名残留：$out", out.contains("# 用户的回复想法"))
        assertTrue("没写清数量由回复任务自己定：$out", out.contains("方案数量与输出格式按本任务既有的格式要求"))
    }

    /**
     * 空备注 = 整段不出现。
     *
     * 反例：给空正文也拼一段标题 ⇒ prompt 里挂一个空标题，模型会去找那段并不存在的补充。
     */
    @Test
    fun blankNoteEmitsNoSectionAtAll() {
        assertEquals("", IntentIdeaBlock.buildAdvisorNoteBlock(""))
        assertEquals("", IntentIdeaBlock.buildAdvisorNoteBlock("   \n  "))
    }

    /**
     * 正文**逐字全量**注入——展示侧那一行灰字省略过，发给军师的不许裁。
     *
     * 反例：区块里写 `text.take(80)`，或者按行只取第一行 ⇒ 用户那句长提醒的后半段丢了，
     * 而界面上看得到（省略号），两边读到的不是同一句话。
     */
    @Test
    fun wholeNoteBodyIsInjectedWithoutTruncation() {
        val long = "第一条要记住的话，长得足够被谁顺手裁一刀：" + "哈".repeat(200) + "结尾这半句也在"
        val out = IntentIdeaBlock.buildAdvisorNoteBlock(long)

        assertTrue("结尾那半句被裁掉了", out.contains("结尾这半句也在"))
        // 区块正文里那句完整出现一次，且不带省略号
        assertEquals("备注正文只该出现一次", 1, out.split("结尾这半句也在").size - 1)
        assertFalse("prompt 侧不该出现展示层的省略号：$out", out.contains("…"))
    }

    /**
     * 多行备注（用户提交了两条补充）整体带过去，不合并成一行、也不漏第二条。
     *
     * 反例：`lineSequence().first()` 之类的"只取第一行"写法。
     */
    @Test
    fun multiLineNoteKeepsEveryLine() {
        val out = IntentIdeaBlock.buildAdvisorNoteBlock("别再问她忙不忙\n也别提她前任")
        assertTrue(out.contains("别再问她忙不忙"))
        assertTrue("第二行丢了：$out", out.contains("也别提她前任"))
    }

    /**
     * 备注区块与持续意图区块互不代替（第6节第3条：一个说此刻的补充，一个说一段时间的目的）。
     *
     * 反例：把备注塞进 `【持续意图】` 段，或反过来——那两条生命周期不同的东西一旦合槽，
     * 到期/完成/暂停的判据就会作用到本轮备注上。
     */
    @Test
    fun noteSectionIsNotTheIntentSection() {
        val note = IntentIdeaBlock.buildAdvisorNoteBlock("先听我说完")
        val intent = IntentIdeaBlock.buildIntentBlock(
            com.lovebrain.app.model.IntentConfig(text = "这周把见面的事定下来", enabled = true)
        )
        assertFalse("备注段里混进了持续意图段：$note", note.contains("【持续意图】"))
        assertTrue("持续意图段仍按原样产出：$intent", intent.contains("【持续意图】"))
        assertFalse("意图段里混进了备注：$intent", intent.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))
    }

    /**
     * 角色标签：`Role.IDEA` 这个 enum 值与它的序列化名一个字都没动（ 只改语义与入口）。
     *
     * 反例：有人"顺手改名"成 NOTE ⇒ 历史数据/JSON 里的 `"IDEA"` 反序列化当场失败。
     */
    @Test
    fun ideaRoleNameIsUnchangedForSerializationCompatibility() {
        assertEquals("IDEA", ChatMessage.Role.IDEA.name)
        assertEquals(3, ChatMessage.Role.values().size)
    }
}
