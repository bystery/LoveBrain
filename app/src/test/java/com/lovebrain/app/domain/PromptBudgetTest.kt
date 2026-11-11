package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun blocks(
        knowledge: String = "",
        chatBody: String = "",
        ideaBlock: String = "",
        historicalBlock: String = "",
        uncertainBlock: String = ""
    ) =
        PromptBudget.byBlocks(
            knowledgeBlock = knowledge,
            sceneBlock = "## 场景\n推断：约会\n",
            intentBlock = "## 持续意图\n目标：周末见\n",
            ideaBlock = ideaBlock,
            chatHeader = "以下是本轮对话：\n",
            chatBody = chatBody,
            timestampBlock = "当前时间：2026-09-24 10:00",
            historicalBlock = historicalBlock,
            uncertainBlock = uncertainBlock
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

    // ─── guide-6 §15.5：LIVE 两个补充区块（Step 3：字节冻结 + 注入顺序 + 整块预算）───

    /** 历史块形状与生产 EventHistoryAssembler 输出一致（段头 + m_src 行） */
    private fun histBlock(lines: List<String>): String =
        "# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + lines.joinToString("\n")

    private fun histLines(count: Int): List<String> =
        (1..count).map { "m_src[$it] | 2026-11-01 09:00 | PARTNER | 旧消息$it" }

    private val ucBlock: String =
        "# 【未定/待确认事项：尚未核实，不得当作已发生】\n" +
            "- 「周五见面的事还没定下来」（本轮）\n" +
            "口径：以上事项只能用\"还不确定/等你确认\"表述；不得替对方或第三方补全金额、" +
            "时间、行为，也不得据此改写已定条款。"

    /** 旧 7 参调用的逐字节基准（§7.4 条 1 的对照面） */
    private fun legacyBlocks(knowledge: String, chatBody: String): String =
        PromptBudget.byBlocks(
            knowledgeBlock = knowledge,
            sceneBlock = "## 场景\n推断：约会\n",
            intentBlock = "## 持续意图\n目标：周末见\n",
            ideaBlock = "想法：软一点\n",
            chatHeader = "以下是本轮对话：\n",
            chatBody = chatBody,
            timestampBlock = "当前时间：2026-09-24 10:00"
        )

    @Test
    fun `empty live blocks keep the prompt byte-identical to the legacy seven-arg assembly`() {
        // §7.4 条 1：两个新参数默认空串时最终 Prompt 逐字节一致——不超预算与超预算两条路径都要对上
        val knowledge = "# 画像\n她喜欢猫\n"
        assertEquals(
            "空区块不得加任何字符（不超预算路径）",
            legacyBlocks(knowledge, chatBody(2, 20)),
            blocks(knowledge = knowledge, chatBody = chatBody(2, 20), ideaBlock = "想法：软一点\n")
        )
        val bigKnowledge = "# 旧记忆\n" + "旧事".repeat(6000)
        assertEquals(
            "空区块不得加任何字符（超预算裁剪路径）",
            legacyBlocks(bigKnowledge, chatBody(60, 250)),
            blocks(knowledge = bigKnowledge, chatBody = chatBody(60, 250), ideaBlock = "想法：软一点\n")
        )
    }

    @Test
    fun `live blocks sit after the advisor note and before the current chat transcript`() {
        // §7.3 注入顺序：… → noteBlock → historicalBlock → uncertainBlock → chatHeader → chatBody → timestamp
        val out = blocks(
            knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(2, 20), ideaBlock = "想法：软一点\n",
            historicalBlock = histBlock(histLines(2)), uncertainBlock = ucBlock
        )
        val markers = listOf(
            "# 画像", "想法：软一点", "# 【真实历史消息", "# 【未定/待确认事项",
            "以下是本轮对话：", "<chat>", "</chat>", "当前时间："
        )
        val idx = markers.map { it to out.indexOf(it) }
        idx.forEach { (m, i) -> assertTrue("user 缺少段标记：$m", i >= 0) }
        for (i in 0 until idx.size - 1) {
            assertTrue(
                "顺序违例：${idx[i].first}(${idx[i].second}) 应在 ${idx[i + 1].first}(${idx[i + 1].second}) 之前",
                idx[i].second < idx[i + 1].second
            )
        }
    }

    @Test
    fun `live blocks reach the no-kb branch too`() {
        // §7.5：kb==null 与 kb!=null 两分支都接通区块拼接（§15.5 条 8）
        val out = blocks(
            knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(2, 20),
            historicalBlock = histBlock(histLines(2)), uncertainBlock = ucBlock
        )
        val kbOut = out
        assertTrue("无 KB 分支历史块也在场", kbOut.contains("# 【真实历史消息"))
        assertTrue("无 KB 分支 UC 块也在场", kbOut.contains("# 【未定/待确认事项"))
    }

    @Test
    fun `over budget the history block is kept whole or removed whole, never truncated`() {
        val lines = histLines(3)
        val hist = histBlock(lines)
        // 放得下：整块完整保留
        val fits = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(2, 20), historicalBlock = hist)
        for (l in lines) assertTrue("放得下时历史行必须完整在场：$l", fits.contains(l))
        // 放不下：整块移除——任何一条历史行都不许出现（不裸 take / 不切半条 / 段头不残留）
        val out = blocks(
            knowledge = "# 旧记忆\n" + "旧事".repeat(6000), chatBody = chatBody(2, 20), historicalBlock = hist
        )
        assertFalse("超预算时历史块必须整块移除，不许留半条", out.contains("第一条旧消息"))
        assertFalse(out.contains("第三条旧消息"))
        assertFalse("历史段头也不许残留", out.contains("# 【真实历史消息"))
        assertTrue("知识段仍按原规则先裁", out.contains("…（旧记忆因长度限制已省略）…"))
        assertTrue("当前聊天记录完整保留", out.contains(chatBody(2, 20)))
    }

    @Test
    fun `an oversized history block alone is dropped entirely, never cut to fit`() {
        // 历史块自身远超剩余预算的极端：整块移除，绝不出现半条/裸截断碎片
        val huge = histBlock(listOf("m_src[0] | 2026-11-01 09:00 | PARTNER | " + "长".repeat(9000)))
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(2, 20), historicalBlock = huge)
        assertFalse("半条都不许留", out.contains("长".repeat(100)))
        assertTrue("当前聊天记录完整保留", out.contains(chatBody(2, 20)))
    }

    @Test
    fun `chat json lines stay whole when the history block is present during the overflow`() {
        // §7.4 条 6/8/9：历史块在场的溢出里，对话头部仍按整 JSON 行裁剪、围栏闭合不被裁掉；
        // 按 §11.2 次序历史块在对话头部裁剪**之前**已整块移除——绝不允许"削历史块凑对话"
        val lines = histLines(4)
        val hist = histBlock(lines)
        val chat = chatBody(60, 250)
        val out = blocks(knowledge = "# 画像\n" + "背".repeat(1000), chatBody = chat, historicalBlock = hist)
        // 整块移除：历史块任何一行都不在场（不裸 take、不切半条）
        for (l in lines) assertFalse("历史块必须整块移除、不许留半条：$l", out.contains(l))
        assertFalse("历史段头不残留", out.contains("# 【真实历史消息"))
        // 对话头部按原规则整行裁剪
        val inner = out.substringAfter("<chat>\n").substringBefore("\n</chat>")
        assertTrue("对话段应被裁过：\n${inner.take(60)}", inner.contains("…"))
        val payloadLines = inner.lines().filter { it.isNotBlank() && !it.startsWith("…") }
        assertTrue("裁完应还剩些完整行", payloadLines.isNotEmpty())
        val originalLines = chat.lines().toSet()
        for (line in payloadLines) {
            assertTrue("半行 JSON：${line.take(24)}", line.startsWith("{") && line.endsWith("}"))
            assertTrue("必须是原样整行：${line.take(24)}", line in originalLines)
        }
        assertTrue("围栏闭合不许被裁掉", out.contains("</chat>"))
    }

    @Test
    fun `overflow order is recent trim first and only then the whole history block`() {
        // §7.4 条 12 / §11.2 条 1-3 的次序证明：recent 仍按**原有规则**先裁（占位句格式），
        // 仍超才整块移除历史块——历史块永远不会把 recent 挤出装配，也不替代 recent.md
        val recentBody = "近".repeat(3000)
        val knowledge = "# 画像\n" + "背".repeat(6700) + "\n# 最近对话\n" + recentBody
        val hist = histBlock(histLines(4))
        val chat = chatBody(60, 250)
        val out = blocks(knowledge = knowledge, chatBody = chat, historicalBlock = hist)
        assertTrue(
            "recent 段保留且走原有占位句规则（先于历史块移除）",
            out.contains("# 最近对话\n…（较旧的对话因长度限制已省略）…\n")
        )
        assertFalse("recent 原文按原规则被裁（不是因历史块整段删除）", out.contains(recentBody))
        assertFalse("历史块整块移除", out.contains("# 【真实历史消息"))
        assertFalse(out.contains("旧消息4"))
        assertTrue("对话头部裁剪仍给省略说明与围栏闭合", out.contains("对话记录因长度限制已省略") && out.contains("</chat>"))
    }

    @Test
    fun `a fitting history block never disturbs recent md`() {
        // §7.4 条 12 的正向面：放得下的历史块加入后 recent 原文逐字节原样
        val recentBody = "近".repeat(1000)
        val knowledge = "# 画像\n她喜欢猫\n# 最近对话\n" + recentBody
        val out = blocks(knowledge = knowledge, chatBody = chatBody(2, 20), historicalBlock = histBlock(histLines(4)))
        assertTrue("recent 原文完整在场", out.contains(recentBody))
        assertTrue("历史块整块在场", out.contains("# 【真实历史消息"))
    }

    @Test
    fun `uncertain block stays complete within its 120 cu budget or is removed whole`() {
        // §7.4 条 11：UC ≤120 CU 保持完整，不裸截断；超预算整块移除
        val fits = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(2, 20), uncertainBlock = ucBlock)
        assertTrue("预算内 UC 整块在场（逐字节完整）", fits.contains(ucBlock))
        val over = blocks(
            knowledge = "# 旧记忆\n" + "旧事".repeat(6000), chatBody = chatBody(2, 20), uncertainBlock = ucBlock
        )
        assertFalse("超预算 UC 整块移除", over.contains("# 【未定/待确认事项"))
        assertFalse("不留碎片", over.contains("周五见面的事"))
        assertTrue("当前聊天记录完整保留", over.contains(chatBody(2, 20)))
    }
}
