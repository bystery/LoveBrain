package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prompt 预算裁剪的行为合同（`PromptBudget`）。
 *
 * 裁剪规则决定"上下文塞不下时先丢谁"，丢错方向的后果是模型看不到当前对话却
 * 带着一堆旧记忆说话。这些规则原先嵌在 1 199 行的 PromptBuilder 里，
 * 需要一个 Context + 知识库才能间接触到，所以从没有被直接测过。
 *
 * 预算数值全部从 `AppConfig` 读，用例里不写死——数值一改，用例跟着动，
 * 不会出现"代码改了、测试还在为旧数字辩护"。
 */
class PromptBudgetTest {

    private fun chatBody(lines: Int, width: Int): String {
        val inner = (1..lines).joinToString("\n") {
            """{"r":"ME","t":"${"字".padEnd(width / 2, 'x')}"}"""
        }
        return "<chat>\n$inner\n</chat>\n"
    }

    private fun blocks(knowledge: String = "", chatBody: String = "", ideaBlock: String = "") =
        PromptBudget.byBlocks(
            knowledgeBlock = knowledge,
            sceneBlock = "## 场景\n推断：约会\n",
            intentBlock = "## 持续意图\n目标：周末见\n",
            ideaBlock = ideaBlock,
            chatHeader = "以下是本轮对话：\n",
            chatBody = chatBody,
            timestampBlock = "当前时间：2026-09-24 10:00"
        )

    // ─── 不超预算时一个字符都不动 ────────────────────────────────

    @Test
    fun `an under-budget prompt comes back byte-identical`() {
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(2, 20), ideaBlock = "想法：软一点\n")
        assertEquals(
            "# 画像\n她喜欢猫\n" + "\n\n" + "## 场景\n推断：约会\n" + "## 持续意图\n目标：周末见\n" +
                "想法：软一点\n" + "以下是本轮对话：\n" + chatBody(2, 20) + "\n\n" + "当前时间：2026-09-24 10:00",
            out
        )
    }

    // ─── 超预算时的裁剪顺序 ──────────────────────────────────────

    @Test
    fun `overflow trims the knowledge tail first and keeps the newest chat line`() {
        val knowledge = "# 旧记忆\n" + "旧事".repeat(6000)
        val chat = chatBody(3, 60)
        val out = blocks(knowledge = knowledge, chatBody = chat, ideaBlock = "想法：一定要保留\n")

        assertTrue("知识段尾部应先被裁", out.contains("…（旧记忆因长度限制已省略）…"))
        assertTrue("最新的真实消息必须留着", out.contains(chat.lines().filter { it.isNotBlank() }.last()))
        assertTrue("完整 IDEA 不能被裁掉", out.contains("想法：一定要保留"))
        assertTrue("围栏开标签还在", out.contains("<chat>"))
        assertTrue("围栏闭标签还在", out.contains("</chat>"))
        assertTrue("确实变小了", out.length < (knowledge.length + chat.length))
    }

    @Test
    fun `chat lines are dropped whole, never cut in half`() {
        // 对话段占大头时才会走按行裁剪：60 行 × ~145 字，知识段只留一点点
        val chat = chatBody(60, 250)
        val out = blocks(knowledge = "# 画像\n" + "背".repeat(1000), chatBody = chat)

        val inner = out.substringAfter("<chat>\n").substringBefore("\n</chat>")
        // 先确认"确实走了按行裁剪"，否则后面的断言只是在检查没被裁过的原文
        assertTrue("对话段应被裁过：\n${inner.take(60)}", inner.contains("…"))
        val payloadLines = inner.lines().filter { it.isNotBlank() && !it.startsWith("…") }
        assertTrue("裁完应还剩些完整行", payloadLines.isNotEmpty())
        assertTrue("不该把 60 行全留下", payloadLines.size < 60)
        val originalLines = chat.lines().toSet()
        for (line in payloadLines) {
            assertTrue("半行 JSON：${line.take(24)}", line.startsWith("{") && line.endsWith("}"))
            assertTrue("必须是原样保留的整行：${line.take(24)}", line in originalLines)
        }
    }

    @Test
    fun `when nothing fits the fence still closes with an explicit omission note`() {
        val chat = chatBody(40, 250)
        val out = blocks(knowledge = "# 画像\n" + "背".repeat(6000), chatBody = chat)
        val inner = out.substringAfter("<chat>\n").substringBefore("\n</chat>")
        assertTrue("应给出省略说明：${inner.take(60)}",
            inner.contains("对话记录因长度限制已省略") || inner.contains("较早的对话已省略"))
        assertTrue(out.contains("<chat>") && out.contains("</chat>"))
    }

    // ─── 兜底截断 ────────────────────────────────────────────────

    @Test
    fun `applyBudget keeps the head and the tail and marks the middle`() {
        val short = "画像\n阶段\n"
        assertEquals(short, PromptBudget.applyBudget(short))

        val long = "头".repeat(AppConfig.TOTAL_BUDGET) + "尾".repeat(AppConfig.TOTAL_BUDGET)
        val out = PromptBudget.applyBudget(long)
        assertTrue(out.startsWith("头".repeat(AppConfig.TOTAL_BUDGET / 2)))
        assertTrue(out.endsWith("尾".repeat((AppConfig.TOTAL_BUDGET * 0.4).toInt())))
        assertTrue(out.contains("…（中间旧记忆因长度限制已省略）…"))
    }

    // ─── 锦囊 section 裁剪 ───────────────────────────────────────

    private fun suggestText(lowPad: Int): String = buildString {
        append("时间戳前缀\n\n")
        append("## 与今天相关的事项\n- 周末旅行：已订机票\n- 见家长：待定\n\n")
        append("## 温度摘要\n").append("温".repeat(lowPad)).append("\n\n")
        append("## 表达偏好\n- 不用感叹号\n")
    }

    @Test
    fun `an under-budget suggest text is returned untouched`() {
        val text = suggestText(10)
        assertEquals(text, PromptBudget.trimSuggestToBudget(text))
    }

    @Test
    fun `suggest trimming keeps the high-priority section and drops the padded low one`() {
        val text = suggestText(4000)
        val out = PromptBudget.trimSuggestToBudget(text)

        assertTrue("边界与当前事项必须保住", out.contains("## 与今天相关的事项"))
        assertTrue("事项条目不能丢", out.contains("- 周末旅行：已订机票"))
        assertTrue("预算必须被收住", out.length <= AppConfig.SUGGEST_BUDGET)
        assertTrue("低优先段应被裁短", out.length < text.length)
        // 不做裸截断：结果里每一行都得是原文的完整行
        val originalLines = text.lines().toSet()
        for (line in out.lines()) {
            assertTrue("出现被截半的行：${line.take(20)}", line.isEmpty() || line in originalLines)
        }
    }

    @Test
    fun `trimToEntryBoundary only ever appends whole lines`() {
        val text = "第一行\n第二行\n第三行"
        assertEquals(text, PromptBudget.trimToEntryBoundary(text, 100))
        assertEquals("第一行\n第二行", PromptBudget.trimToEntryBoundary(text, 7))
        assertEquals("", PromptBudget.trimToEntryBoundary(text, 2))
    }

    @Test
    fun `lastH1Blocks keeps the newest blocks and says what it dropped`() {
        val content = (1..5).joinToString("\n\n") { "# 第${it}次提取\n内容$it" }
        assertEquals(
            "…（更早的已省略）\n\n# 第4次提取\n内容4\n\n# 第5次提取\n内容5",
            PromptBudget.lastH1Blocks(content, 2)
        )
        assertTrue(PromptBudget.lastH1Blocks(content, 2).let { !it.contains("第1次") })
        assertEquals("# 第1次提取\n内容1", PromptBudget.lastH1Blocks("# 第1次提取\n内容1", 3))
        assertEquals("不是标题的行不会混进来", "", PromptBudget.lastH1Blocks("纯文本没有标题", 3))
    }
}
