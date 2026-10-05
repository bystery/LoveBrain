package com.lovebrain.app.domain

import android.content.Context
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.prompt.IntentIdeaBlock
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原始第 18 条 / §9.2 的**普通分支注入**机械闭环（可复现、无需真模型）。
 *
 * 这一族盯的是 F7 定位的丢失点：`buildProactiveUserPrompt` 普通分支以前收了 `messages`/`advisorNote`
 * 却一次都不引用，还宣称注入"表达偏好"却从不读 `understand/style.md`。
 * 这里用**真 PromptBuilder + 内存库**把 user prompt 截到手，逐格判：
 * - 本轮真实消息与军师备注是否真的拼了进去（且**排在相关记忆之前**——顺序即 §9.2 的语义）；
 * - 表达偏好是否真从 style.md 接进来了（不再是资产写了却没接入的假声明）；
 * - 删除补充后 prompt 里是否彻底没有它；
 * - 收尾/争执信号是否把对应场景带进 prompt（策略走"从当轮推断"，不靠 KB）。
 *
 * 内容质量（冷场能否回调旧话题、已读不回是否武断、特殊日子有无出处）需要真模型，本测不涉及，
 * 在交付报告里标"待验证-需真模型"。
 */
class ProactiveContextInjectionTest {

    private val kb = KnowledgeBase(
        name = "kb", displayName = "核查库", stage = "暧昧期", turnCount = 3, active = true
    )

    private fun port(withStyle: String = "KBSTYLE-表达偏好：短句、少表情"): InMemoryKnowledgePort {
        val p = InMemoryKnowledgePort()
        p.seedLibrary(kb)
        p.seed("kb", "understand/her.md", "KBHER-她最近在准备雅思")
        p.seed("kb", "moment/recent.md", "KBRECENT-上周为回消息慢吵过一次")
        p.seed("kb", "understand/style.md", withStyle)
        return p
    }

    private fun normalPrompt(
        port: InMemoryKnowledgePort,
        draft: String,
        messages: List<ChatMessage>,
        note: String
    ): String = runBlocking {
        PromptBuilder(mockk<Context>(relaxed = true), port, null, FixedClock())
            .buildProactiveUserPrompt(draft, kb, messages, onlyThisRound = false, advisorNote = note)
    }

    private fun her(text: String) = ChatMessage(id = "h", role = ChatMessage.Role.HER, content = text)
    private fun me(text: String) = ChatMessage(id = "m", role = ChatMessage.Role.ME, content = text)

    /** 无草稿、本轮没有任何素材：给干净的兜底，不硬塞空对话围栏 / 空场景 / 空备注 */
    @Test
    fun `no draft and no this-round material yields a clean minimal prompt`() {
        val prompt = normalPrompt(port(), "", emptyList(), "")
        assertTrue("无草稿要有固定文案：$prompt", prompt.contains("（无草稿，请主动给出开场话题）"))
        assertTrue("时间戳垫底仍在：$prompt", prompt.contains("## 当前时间"))
        assertFalse("本轮没有真实消息就不该出现空对话围栏：$prompt", prompt.contains("# 本次对话记录"))
        assertFalse("本轮没有备注就不该留军师备注标题：$prompt", prompt.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))
        assertFalse("本轮没有可推断场景就不留场景段：$prompt", prompt.contains("# 【当前场景】"))
    }

    /** 有用户补充的本轮：军师备注真的进普通分支（以前完全没引用 advisorNote） */
    @Test
    fun `advisor note reaches the normal branch`() {
        val note = "ROUNDNOTE-本轮补充别再追问她累不累"
        val prompt = normalPrompt(port(), "想找个由头聊两句", listOf(her("在忙")), note)
        assertTrue("备注区块标题必须用那颗真源：$prompt", prompt.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))
        assertTrue("备注正文逐字在场：$prompt", prompt.contains(note))
    }

    /** 删除补充后不再使用它：同一批消息/草稿、备注清空，标题与正文一并消失 */
    @Test
    fun `a removed note leaves nothing behind`() {
        val prompt = normalPrompt(port(), "想找个由头聊两句", listOf(her("在忙")), "")
        assertFalse("清空备注后不留标题：$prompt", prompt.contains(IntentIdeaBlock.NOTE_BLOCK_HEADER))
        assertFalse("清空备注后不留上一轮的正文：$prompt", prompt.contains("ROUNDNOTE-本轮补充别再追问她累不累"))
    }

    /**
     * 顺序判据（§9.2：先本轮真实消息与补充，再相关记忆）。
     * 反例：把画像/近期对话/偏好插到本轮备注或对话之前——这里的 index 比较立刻翻。
     */
    @Test
    fun `this-round inputs come before relevant memory`() {
        val note = "ROUNDNOTE-先接她那句加班"
        val prompt = normalPrompt(port(), "在吗", listOf(her("ROUNDHER-她说到家了"), me("ROUNDME-那早点休息")), note)
        val noteIdx = prompt.indexOf(IntentIdeaBlock.NOTE_BLOCK_HEADER)
        val dialogueIdx = prompt.indexOf("# 本次对话记录")
        val herProfileIdx = prompt.indexOf("## 对方画像")
        val styleIdx = prompt.indexOf("## 表达偏好")
        assertTrue("备注要在场：$prompt", noteIdx >= 0)
        assertTrue("本轮对话要在场：$prompt", dialogueIdx >= 0)
        assertTrue("画像要在场：$prompt", herProfileIdx >= 0)
        assertTrue("表达偏好要在场：$prompt", styleIdx >= 0)
        assertTrue("备注必须排在对方画像之前（本轮优先）", noteIdx < herProfileIdx)
        assertTrue("本轮对话必须排在对方画像之前（本轮优先）", dialogueIdx < herProfileIdx)
        assertTrue("相关记忆内部：画像排在表达偏好之前", herProfileIdx < styleIdx)
    }

    /** 表达偏好接了真来源：不是资产里写了却没接入的假声明 */
    @Test
    fun `expression preference is wired from style md`() {
        val prompt = normalPrompt(port(), "在吗", emptyList(), "")
        assertTrue("表达偏好段应出现：$prompt", prompt.contains("## 表达偏好"))
        assertTrue("内容来自 understand/style.md：$prompt", prompt.contains("KBSTYLE-表达偏好"))
    }

    /** 表达偏好有预算：超过 200 字截断并加省略，不把整份风格塞满 prompt */
    @Test
    fun `expression preference is budget capped`() {
        val long = "表".repeat(300)
        val prompt = normalPrompt(port(withStyle = long), "在吗", emptyList(), "")
        assertTrue("预算内保留 200 字：$prompt", prompt.contains("表".repeat(200)))
        assertFalse("不该带出超过 200 字的原文（说明没截）：$prompt", prompt.contains("表".repeat(201)))
        assertTrue("超长要加省略号：$prompt", prompt.contains("## 表达偏好\n" + "表".repeat(200) + "…（略）"))
    }

    /** 对方要休息（收尾信号）：本轮场景推断为"收尾"并进 prompt，策略只从当轮来，不读 KB */
    @Test
    fun `a rest signal injects the closing scene from this round only`() {
        val prompt = normalPrompt(port(), "", listOf(her("先不聊了我去睡了")), "")
        assertTrue("收尾场景应被推断并注入：$prompt", prompt.contains("场景：收尾"))
    }

    /** 近期冲突：本轮争执信号推断为"争执"并进 prompt，供模型走"先接情绪、不强推邀约" */
    @Test
    fun `a conflict signal injects the arguing scene from this round only`() {
        val prompt = normalPrompt(port(), "", listOf(me("你到底想怎样"), her("随便你")), "")
        assertTrue("争执场景应被推断并注入：$prompt", prompt.contains("场景：争执"))
    }

    /** 仅看本轮那一档：本轮输入到位但零 KB 读取（表达偏好这一档不接，边界不破） */
    @Test
    fun `only this round branch injects this-round inputs without reading style md`() {
        val note = "ROUNDNOTE-只按本轮想"
        val prompt = runBlocking {
            PromptBuilder(mockk<Context>(relaxed = true), port(), null, FixedClock())
                .buildProactiveUserPrompt("ROUNDDRAFT-想约看展", kb, listOf(her("ROUNDHER-周末有空吗"), me("ROUNDME-有啊")), onlyThisRound = true, advisorNote = note)
        }
        assertTrue("仅看本轮仍带本轮备注：$prompt", prompt.contains(note))
        assertTrue("仅看本轮仍带真实对话：$prompt", prompt.contains("ROUNDHER-周末有空吗"))
        assertFalse("仅看本轮不得注入表达偏好（那是 KB 来源）：$prompt", prompt.contains("## 表达偏好"))
        assertFalse("仅看本轮不得注入对方画像：$prompt", prompt.contains("## 对方画像"))
    }
}
