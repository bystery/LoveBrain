package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.domain.prompt.IntentIdeaBlock
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主动开场 user prompt 的结构合同（2026-11-21 结构收敛后）。
 *
 * 用户拍板：主动开场**普通与「仅看本轮」两档都只留「用户草稿 + 当前时间」**。
 * 军师备注 / 当前场景 / 本轮真实对话 / 持续意图 / 对方画像 / 近期对话 / 表达偏好
 * 全部删除，且**一次知识库读取都不做**。
 *
 * 判据取 `PromptBuilder` 公开入口吐出来的字符串，不是源码里有没有出现某个符号。
 */
class ProactiveContextInjectionTest {

    private val kb = KnowledgeBase(
        name = "kb", displayName = "核查库", stage = "暧昧期", turnCount = 3, active = true
    )

    private fun port(): InMemoryKnowledgePort {
        val p = InMemoryKnowledgePort()
        p.seedLibrary(kb)
        p.seed("kb", "understand/me.md", "KBME-画像我")
        p.seed("kb", "understand/her.md", "KBHER-她最近在准备雅思")
        p.seed("kb", "understand/warmth.md", "KBWARMTH-温度")
        p.seed("kb", "moment/recent.md", "KBRECENT-上周为回消息慢吵过一次")
        p.seed("kb", "understand/style.md", "KBSTYLE-表达偏好：短句、少表情")
        p.seed("kb", "memory/lessons.md", "# KBLESSON-旧经验\n别再追问她累不累\n")
        p.seed("kb", "moment/plan.md", "KBPLAN-旧事项 ~展览门票 | 进行中 | 链")
        p.seed("kb", "moment/topic.md", "- [2026-09-24 09:00] 正在聊：KBTOPIC-旧话题")
        p.seed("kb", "moment/scene.md", "- [2026-09-24 09:00] KBSCENE-旧场景链：聊过展览")
        return p
    }

    private fun prompt(
        draft: String,
        messages: List<ChatMessage> = emptyList(),
        note: String = "",
        onlyThisRound: Boolean = false
    ): String = runBlocking {
        PromptBuilder(PromptSourcePort { "" }, port(), FixedClock())
            .buildProactiveUserPrompt(draft)
    }

    private fun her(text: String) = ChatMessage(id = "h", role = ChatMessage.Role.HER, content = text)
    private fun me(text: String) = ChatMessage(id = "m", role = ChatMessage.Role.ME, content = text)

    /** 无草稿、无任何素材：给干净的兜底，不硬塞空对话围栏 / 空场景 / 空备注 */
    @Test
    fun `no draft and no material yields a clean minimal prompt`() {
        val out = prompt("")
        assertTrue("无草稿要有固定文案：$out", out.contains("（无草稿，请主动给出开场话题）"))
        assertTrue("时间戳垫底仍在：$out", out.contains("## 当前时间"))
        assertFalse("不该出现空对话围栏：$out", out.contains("# 本次对话记录"))
        assertFalse("不该留军师备注标题：$out", out.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))
        assertFalse("不该留用户备注标题：$out", out.contains(IntentIdeaBlock.USER_NOTE_HEADER))
        assertFalse("不该留场景段：$out", out.contains("# 【当前场景】"))
    }

    /** 普通分支只带草稿：备注 / 对话 / 场景 / 意图 / 画像 / 近期 / 偏好一个都不进 */
    @Test
    fun `normal branch carries only the draft`() {
        val out = prompt(
            draft = "ROUNDDRAFT-想约看展",
            messages = listOf(her("ROUNDHER-她说她加班到十点"), me("ROUNDME-我想回这句")),
            note = "ROUNDNOTE-本轮备注别再问忙不忙",
            onlyThisRound = false
        )
        assertTrue("草稿逐字在场：$out", out.contains("ROUNDDRAFT-想约看展"))
        listOf(
            "ROUNDNOTE-本轮备注别再问忙不忙", "ROUNDHER-她说她加班到十点", "ROUNDME-我想回这句",
            "# 本次对话记录", "# 【当前场景】", "【持续意图】",
            "## 对方画像", "## 近期对话", "## 表达偏好",
            IntentIdeaBlock.NOTE_BLOCK_HEADER, IntentIdeaBlock.USER_NOTE_HEADER,
            "KBME-画像我", "KBHER-她最近在准备雅思", "KBWARMTH-温度", "KBRECENT-",
            "KBSTYLE-表达偏好", "KBLESSON-旧经验", "KBPLAN-旧事项", "KBTOPIC-旧话题", "KBSCENE-旧场景链"
        ).forEach { gone -> assertFalse("主动开场不该带「$gone」：$out", out.contains(gone)) }
    }

    /** 「仅看本轮」与普通分支现在逐字同形：只留草稿 + 当前时间 */
    @Test
    fun `only this round branch is byte-identical to the normal branch`() {
        val draft = "ROUNDDRAFT-想约看展"
        val normal = prompt(draft, listOf(her("ROUNDHER-她说她加班到十点")), "ROUNDNOTE-备注", onlyThisRound = false)
        val roundScope = prompt(draft, listOf(her("ROUNDHER-她说她加班到十点")), "ROUNDNOTE-备注", onlyThisRound = true)
        assertTrue("两档必须只由草稿 + 时间决定：\nnormal=$normal\nonly=$roundScope", normal == roundScope)
        assertTrue(roundScope.contains(draft))
        assertFalse(roundScope.contains("本轮仅看模式"))
        assertFalse(roundScope.contains("ROUNDNOTE-备注"))
    }

    /** 场景推断不再发生：本轮消息里的收尾/争执信号都不进主动开场 */
    @Test
    fun `this round scene signals are no longer injected`() {
        val closing = prompt("", listOf(her("先不聊了我去睡了")), onlyThisRound = false)
        assertFalse("收尾信号不该再带场景段：$closing", closing.contains("场景：收尾"))
        assertFalse(closing.contains("# 【当前场景】"))

        val conflict = prompt("", listOf(me("你到底想怎样"), her("随便你")), onlyThisRound = false)
        assertFalse("争执信号不该再带场景段：$conflict", conflict.contains("场景：争执"))
    }

    /**
     * 资产侧同口径：proactive.md 的输入清单只留「用户草稿 + 当前时间」，
     * 以免系统提示词还在要求模型去读早已不注入的备注 / 画像 / 近期对话 / 表达偏好。
     */
    @Test
    fun `proactive asset lists only the draft and the current time`() {
        val asset = ClassLoader.getSystemClassLoader()
            .getResourceAsStream(AssetRegistry.PROACTIVE)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("资产 ${AssetRegistry.PROACTIVE} 未在 test classpath 上")
        assertTrue("应保留输入清单：$asset", asset.contains("## 输入"))
        assertTrue("用户草稿仍是输入项", asset.contains("用户草稿"))
        assertTrue("当前时间是输入项", asset.contains("- 当前时间"))
        listOf("本轮军师备注", "对方画像", "近期对话", "表达偏好", "本轮真实对话与场景").forEach { gone ->
            assertFalse("主动开场资产仍要求「$gone」：$asset", asset.contains(gone))
        }
    }
}
