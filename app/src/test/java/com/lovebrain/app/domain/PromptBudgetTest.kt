package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.prompt.PromptKnowledgeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prompt 预算裁剪的行为合同（`PromptBudget`），2026-11-21 结构收敛后的口径。
 *
 * 拼接序：知识段（含尾部历史受保护段） → 当前场景 → 持续意图 → `<chat>` → 用户备注 → 当前时间。
 * - 用户备注（`replyDirective.text`）排在 chat 之后、时间之前，整段不被裁；
 * - 超预算先整块丢历史，仍超再按条目/行边界丢知识正文，最后才动对话整行；
 * - 冠军历史块只**整块保留或整块移除**，绝不按字符/按行截断；正文与对话也绝不留半行；
 * - 「未定事项（UC）」与「# 最近对话」裁剪分支已随对应内容删除。
 *
 * 预算数值全部从 `AppConfig` 读，用例里不写死——数值一改，用例跟着动，
 * 不会出现"代码改了、测试还在为旧数字辩护"。
 */
class PromptBudgetTest {

    private val scene = "## 场景\n推断：约会\n"
    private val intent = "## 持续意图\n目标：周末见\n"
    private val header = "以下是本轮对话：\n"
    private val timestamp = "当前时间：2026-09-24 10:00"

    private fun chatBody(lines: Int, width: Int): String {
        val inner = (1..lines).joinToString("\n") {
            """{"r":"ME","t":"${"字".padEnd(width / 2, 'x')}"}"""
        }
        return "<chat>\n$inner\n</chat>\n"
    }

    private fun blocks(
        knowledge: String = "",
        chatBody: String = "",
        userNote: String = "",
        historicalBlock: String = "",
        historyInKnowledge: Boolean = false,
        intentBlock: String = intent
    ) = PromptBudget.byBlocks(
        knowledgeBlock = knowledge,
        sceneBlock = scene,
        intentBlock = intentBlock,
        userNoteBlock = userNote,
        chatHeader = header,
        chatBody = chatBody,
        timestampBlock = timestamp,
        historicalBlock = historicalBlock,
        historyInKnowledge = historyInKnowledge
    )

    /** 不裁时的目标顺序全文 */
    private fun expected(
        knowledge: String,
        chat: String,
        note: String = "",
        history: String = ""
    ): String = knowledge + history + "\n\n" + scene + intent + header + chat + note + "\n\n" + timestamp

    // ─── 不超预算时一个字符都不动 ────────────────────────────────

    @Test
    fun `an under-budget prompt comes back byte-identical in the target order`() {
        val chat = chatBody(2, 20)
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chat, userNote = "备注：软一点\n")
        assertEquals(expected("# 画像\n她喜欢猫\n", chat, "备注：软一点\n"), out)
    }

    @Test
    fun `user note sits after the chat fence and before the timestamp`() {
        val out = blocks(
            knowledge = "# 画像\n她喜欢猫\n",
            chatBody = chatBody(2, 20),
            userNote = "备注：软一点\n"
        )
        val markers = listOf("# 画像", header, "<chat>", "</chat>", "备注：软一点", "当前时间：")
        val idx = markers.map { it to out.indexOf(it) }
        idx.forEach { (m, i) -> assertTrue("缺少段标记：$m", i >= 0) }
        for (i in 0 until idx.size - 1) {
            assertTrue(
                "顺序违例：${idx[i].first}(${idx[i].second}) 应在 ${idx[i + 1].first}(${idx[i + 1].second}) 之前",
                idx[i].second < idx[i + 1].second
            )
        }
    }

    // ─── 超预算时的裁剪顺序 ──────────────────────────────────────

    @Test
    fun `overflow trims the knowledge tail first and keeps the newest chat line`() {
        val knowledge = "# 旧记忆\n" + "旧事".repeat(6000)
        val chat = chatBody(3, 60)
        val out = blocks(knowledge = knowledge, chatBody = chat, userNote = "备注：一定要保留\n")

        assertTrue("知识段尾部应先被裁", out.contains("…（旧记忆因长度限制已省略）…"))
        assertTrue("最新的真实消息必须留着", out.contains(chat.lines().filter { it.isNotBlank() }.last()))
        assertTrue("完整用户备注不能被裁掉", out.contains("备注：一定要保留"))
        assertTrue("围栏开标签还在", out.contains("<chat>"))
        assertTrue("围栏闭标签还在", out.contains("</chat>"))
        assertTrue("确实变小了", out.length < (knowledge.length + chat.length))
    }

    @Test
    fun `chat lines are dropped whole, never cut in half`() {
        // 对话段占大头、知识段只剩一点时才会走到按行裁剪：80 行 × ~143 字
        val chat = chatBody(80, 250)
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chat)

        val inner = out.substringAfter("<chat>\n").substringBefore("\n</chat>")
        // 先确认"确实走了按行裁剪"，否则后面的断言只是在检查没被裁过的原文
        assertTrue("对话段应被裁过：\n${inner.take(60)}", inner.contains("…"))
        val payloadLines = inner.lines().filter { it.isNotBlank() && !it.startsWith("…") }
        assertTrue("裁完应还剩些完整行", payloadLines.isNotEmpty())
        assertTrue("不该把 80 行全留下", payloadLines.size < 80)
        val originalLines = chat.lines().toSet()
        for (line in payloadLines) {
            assertTrue("半行 JSON：${line.take(24)}", line.startsWith("{") && line.endsWith("}"))
            assertTrue("必须是原样保留的整行：${line.take(24)}", line in originalLines)
        }
    }

    @Test
    fun `a huge note never evicts the newest message or leaves the fence open`() {
        // 用户备注是固定块里唯一的自由文本：它超出上限时先被收短，
        // 而不是把对话整段换成占位句、把本轮待回消息弄丢。
        val note = "想".repeat(12000) + "\n"
        val chat = chatBody(4, 40)
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chat, userNote = note)
        assertTrue("总长必须回到预算内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue(out.contains("<chat>") && out.contains("</chat>"))
        assertTrue("最新的真实消息必须留着", out.contains(chat.lines().filter { it.isNotBlank() }.last()))
        assertTrue("备注被裁处要留可读交代", out.contains("用户备注过长"))
        assertTrue("备注正文只留一个可读前缀", out.contains("想".repeat(200)))
        assertFalse("不许出现半个代理对", hasLoneSurrogate(out))
    }

    // ─── 兜底截断 ────────────────────────────────────────────────

    @Test
    fun `trimToEntryBoundary only ever appends whole lines`() {
        val text = "第一行\n第二行\n第三行"
        assertEquals(text, PromptBudget.trimToEntryBoundary(text, 100))
        assertEquals("第一行\n第二行", PromptBudget.trimToEntryBoundary(text, 7))
        assertEquals("", PromptBudget.trimToEntryBoundary(text, 2))
        // 裁断处只剩段头时，段头一并去掉——不留一个没有正文的空标题
        assertEquals("", PromptBudget.trimToEntryBoundary("# 标题\n## 小节\n" + "正".repeat(50), 15))
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

    // ─── 历史块：知识段原 recent 槽，整块保护 ─────────────────────

    /** 历史块形状与生产 EventHistoryAssembler 输出一致（段头 + m_src 行） */
    private fun histBlock(lines: List<String>): String =
        "# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + lines.joinToString("\n")

    private fun histLines(count: Int): List<String> =
        (1..count).map { "m_src[$it] | 2026-11-01 09:00 | PARTNER | 旧消息$it" }

    @Test
    fun `history block sits in the knowledge slot before the current scene`() {
        val hist = histBlock(histLines(2))
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(2, 20), historicalBlock = hist)
        val markers = listOf(
            "# 画像", "# 【真实历史消息", "## 场景", "## 持续意图", header, "<chat>", "</chat>", "当前时间："
        )
        val idx = markers.map { it to out.indexOf(it) }
        idx.forEach { (m, i) -> assertTrue("缺少段标记：$m", i >= 0) }
        for (i in 0 until idx.size - 1) {
            assertTrue(
                "顺序违例：${idx[i].first}(${idx[i].second}) 应在 ${idx[i + 1].first}(${idx[i + 1].second}) 之前",
                idx[i].second < idx[i + 1].second
            )
        }
    }

    @Test
    fun `a history block already rendered into the knowledge tail is not duplicated`() {
        val hist = histBlock(histLines(2))
        // 生产有库分支：PromptKnowledgeSection 已把历史块渲染在知识段尾部
        val out = blocks(
            knowledge = "# 画像\n她喜欢猫\n\n" + hist, chatBody = chatBody(2, 20),
            historicalBlock = hist, historyInKnowledge = true
        )
        assertEquals("历史段头只该出现一次", 1, Regex("# 【真实历史消息").findAll(out).count())
        assertTrue("场景段仍在历史块之后", out.indexOf("# 【真实历史消息") < out.indexOf("## 场景"))
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
        assertFalse("超预算时历史块必须整块移除，不许留半条", out.contains("旧消息1"))
        assertFalse(out.contains("旧消息3"))
        assertFalse("历史段头也不许残留", out.contains("# 【真实历史消息"))
        assertTrue("历史整块丢完仍超时知识段按条目裁（留省略标记）",
            out.contains("…（旧记忆因长度限制已省略）…"))
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
    fun `knowledge body is trimmed on entry boundaries and never leaves half a line or a lone surrogate`() {
        // 40 条短条目 + 1 条超长 emoji 正文：装不下的那条整条丢，
        // 绝不裸 take 出半行、半个代理对或半条内容。
        val entries = (1..40).joinToString("\n") { "【第${it}条】" + "记".repeat(30) }
        val huge = "🙂".repeat(6000)
        val knowledge = "# 旧记忆\n" + entries + "\n" + huge + "\n"
        val out = blocks(knowledge = knowledge, chatBody = chatBody(2, 20))
        val kept = out.substringBefore("\n\n## 场景")

        assertTrue("总长在预算内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("整条目原样保留", kept.contains("【第1条】"))
        val originals = entries.lines().toSet()
        for (line in kept.lines().filter { it.startsWith("【第") }) {
            assertTrue("半行残留：${line.take(20)}", line in originals)
        }
        assertFalse("不许切半个 emoji 留下来", kept.contains("🙂"))
        assertFalse("不许留孤立代理字符", hasLoneSurrogate(kept))
        assertTrue("裁过的地方要留交代", kept.contains("旧记忆因长度限制已省略"))
    }

    /** 孤立的高位/低位代理字符 = 字符串被从代理对中间切开留下的残渣 */
    private fun hasLoneSurrogate(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                Character.isHighSurrogate(c) -> {
                    if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return true
                    i += 2
                }
                Character.isLowSurrogate(c) -> return true
                else -> i++
            }
        }
        return false
    }

    @Test
    fun `chat json lines stay whole when the history block is present during the overflow`() {
        // 历史块在场的溢出里，对话头部仍按整 JSON 行裁剪、围栏闭合不被裁掉；
        // 历史块在对话头部裁剪之前已整块移除——绝不允许"削历史块凑对话"
        val lines = histLines(4)
        val hist = histBlock(lines)
        val chat = chatBody(70, 250)
        val out = blocks(knowledge = "# 画像\n" + "背".repeat(100), chatBody = chat, historicalBlock = hist)
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
    fun `a fitting history block never disturbs the knowledge text`() {
        val knowledge = "# 画像\n她喜欢猫\n# 此刻\n- 当前话题：手冲咖啡\n"
        val out = blocks(knowledge = knowledge, chatBody = chatBody(2, 20), historicalBlock = histBlock(histLines(4)))
        assertTrue("知识段原文完整在场", out.contains(knowledge))
        assertTrue("历史块整块在场", out.contains("# 【真实历史消息"))
    }

    @Test
    fun `overflow drops the whole history block before it touches the knowledge body`() {
        // 知识正文满到快顶格、历史块只有两百来字：整块丢历史就能回预算。
        // 旧顺序先切正文（留省略标记），新顺序必须整块丢历史、正文一字不动。
        val line = "旧记忆：" + "水".repeat(31)
        val knowledge = List(245) { line }.joinToString("\n") + "\n"
        val chat = chatBody(2, 20)
        val out = blocks(knowledge = knowledge, chatBody = chat, historicalBlock = histBlock(histLines(2)))
        assertTrue(
            "前提：带历史块时确实超预算",
            expected(knowledge, chat, history = histBlock(histLines(2))).length > AppConfig.TOTAL_BUDGET
        )
        assertTrue(
            "前提：只丢历史就够回预算",
            expected(knowledge, chat).length <= AppConfig.TOTAL_BUDGET
        )
        assertTrue("总长在预算内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertFalse("历史块整块丢、段头不留", out.contains("# 【真实历史消息"))
        assertFalse("历史行一条都不许留", out.contains("旧消息1"))
        assertTrue("知识正文一字不动（不因历史块先被裁）", out.contains(knowledge))
        assertFalse("知识段不该出现省略标记", out.contains("旧记忆因长度限制已省略"))
    }

    @Test
    fun `oversized knowledge collapses at entry boundaries and the short chat stays intact`() {
        // 知识段远超预算、对话很短：正文按条目/行边界收（这里是两条整行，超长那条整条丢），
        // 短对话不动——绝不为了凑数去裸 take 正文。
        val note = "想".repeat(3000) + "\n"
        val knowledge = "# 旧记忆\n" + "旧".repeat(12000)
        val chat = chatBody(1, 20)
        val out = blocks(knowledge = knowledge, chatBody = chat, userNote = note)
        assertTrue("总长必须复核回预算内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("完整用户备注不能被裁", out.contains(note))
        assertTrue("围栏闭合", out.contains("</chat>"))
        assertTrue("短对话整段保留", out.contains(chat))
        assertFalse("正文装不下时不许裸留一个空段头", out.contains("# 旧记忆"))
        assertTrue("超长整行不留半行、只留省略交代", out.contains("旧记忆因长度限制已省略"))
        assertFalse("超长那条不许留半行", out.contains("旧".repeat(100)))
    }

    // ─── 最终总长复核：估算/短行分支留下的超预算必须被收回 ──────────────

    @Test
    fun `150-char json lines stay whole and within budget after the recheck`() {
        val chat = chatBody(70, 268) // 每行 15 + 268/2 + 2 = 151 字
        val out = blocks(knowledge = "# 画像\n" + "背".repeat(2000), chatBody = chat)
        assertTrue("总长在预算内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("围栏闭合", out.contains("</chat>"))
        val inner = out.substringAfter("<chat>\n").substringBefore("\n</chat>")
        assertTrue("对话段应被裁过", inner.contains("…"))
        val payloadLines = inner.lines().filter { it.isNotBlank() && !it.startsWith("…") }
        assertTrue("裁完应还剩些完整行", payloadLines.isNotEmpty())
        val originalLines = chat.lines().toSet()
        for (line in payloadLines) {
            assertTrue("半行 JSON：${line.take(24)}", line.startsWith("{") && line.endsWith("}"))
            assertEquals("每行都是原样整行", 151, line.length)
            assertTrue("必须是原样保留的整行", line in originalLines)
        }
    }

    @Test
    fun `the recheck drops extra whole lines when the estimate under-drops`() {
        // 短 JSON 行（<60 字）：按 60 估算丢行数会少裁，旧写法仍超预算；
        // 最终复核对着真实长度再丢整行，绝不裸 take 半行。
        val chat = chatBody(400, 20) // 每行 15 + 20/2 + 2 = 27 字
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chat)
        assertTrue("总长必须复核回预算内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("围栏闭合", out.contains("</chat>"))
        val inner = out.substringAfter("<chat>\n").substringBefore("\n</chat>")
        val payloadLines = inner.lines().filter { it.isNotBlank() && !it.startsWith("…") }
        val originalLines = chat.lines().toSet()
        assertTrue("裁完应还剩些完整行", payloadLines.isNotEmpty())
        for (line in payloadLines) {
            assertTrue("半行 JSON", line.startsWith("{") && line.endsWith("}"))
            assertTrue("必须是原样保留的整行", line in originalLines)
        }
        // 复核只是"从最旧整行往下丢到装得下为止"，留下的行数因此可反推：只有全文逐字相等，
        // 才钉得住段序、省略标记的位置，以及"绝不留半行/半条"这两条。
        val all = chat.lines().filter { it.startsWith("{") && it.endsWith("}") }
        val marker = "<chat>\n…（较早的对话已省略）…\n"
        val keep = (all.size downTo 1).first { k ->
            val keptChat = marker + all.takeLast(k).joinToString("\n") + "\n</chat>\n"
            expected("# 画像\n她喜欢猫\n", keptChat).length <= AppConfig.TOTAL_BUDGET
        }
        val keptChat = marker + all.takeLast(keep).joinToString("\n") + "\n</chat>\n"
        assertEquals(
            "复核后的全文必须逐字等于期望（段序、标记、整行边界都不许走样）",
            expected("# 画像\n她喜欢猫\n", keptChat),
            out
        )
    }

    @Test
    fun `an over-cap note is trimmed before knowledge or chat are sacrificed`() {
        // 备注是固定块里唯一的自由文本：超过 USER_NOTE_MAX 先收备注自己，
        // 而不是让"收尽知识/对话"的循环空转、最后返回超帽 prompt。
        val note = "想".repeat(12000) + "\n"
        val knowledge = "# 画像\n她喜欢猫\n"
        val chat = chatBody(3, 268)
        val out = blocks(knowledge = knowledge, chatBody = chat, userNote = note)
        assertTrue("总长必须回到硬上限内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("备注的可读前缀必须留住", out.contains("想".repeat(200)))
        assertTrue("备注被裁处要留可读交代", out.contains("用户备注过长"))
        assertTrue("知识段不该因备注超长被牺牲", out.contains("她喜欢猫"))
        assertTrue("最新一轮不该被牺牲", out.contains(chat.lines().filter { it.isNotBlank() }.last()))
        assertTrue("围栏闭合", out.contains("</chat>"))
    }

    @Test
    fun `a note made of surrogate pairs is trimmed on a code point boundary`() {
        val note = "🙂".repeat(5000) + "\n"
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(1, 20), userNote = note)
        assertTrue("总长必须回到硬上限内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("备注被裁处要留可读交代", out.contains("用户备注过长"))
        assertFalse("不许把代理对切成半个", hasLoneSurrogate(out))
        assertTrue("围栏闭合", out.contains("</chat>"))
    }

    @Test
    fun `a single overlong chat line is truncated inside its JSON string and stays valid JSON`() {
        // 只剩最近一轮且单条超限：整段换成占位句会把本轮待回消息弄丢，
        // 所以改为在 JSON 字符串内部截断——引号闭合、转义交给序列化器。
        val long = "长".repeat(12000)
        val rendered = com.lovebrain.app.domain.prompt.ChatTranscriptBlock.render(
            listOf(com.lovebrain.app.model.ChatMessage(id = "m0", role = com.lovebrain.app.model.ChatMessage.Role.HER, content = long))
        )
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = rendered.body)
        assertTrue("总长必须回到硬上限内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("围栏闭合", out.contains("</chat>"))
        val inner = out.substringAfter("<chat>\n").substringBefore("\n</chat>")
        val jsonLine = inner.lines().first { it.startsWith("{") }
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(jsonLine) as kotlinx.serialization.json.JsonObject
        assertEquals("来源 ID 必须还在，别名映射不失真", "m0",
            (obj["id"] as kotlinx.serialization.json.JsonPrimitive).content)
        val text = (obj["text"] as kotlinx.serialization.json.JsonPrimitive).content
        assertTrue("本轮正文必须留一个可读前缀", text.startsWith("长长长"))
        assertTrue("截断处要有交代", text.endsWith("…（本条过长已截断）"))
        assertFalse("不许把代理对切成半个", hasLoneSurrogate(out))
    }

    @Test
    fun `an over-cap intent is trimmed last so no branch exceeds the hard cap`() {
        // 持续意图也是固定块里的自由文本：走到最后一档时它是唯一还能收的块，
        // 不收就会出现"任何分支都不得 >9000"的反例。
        val hugeIntent = "【持续意图】\n" + "目".repeat(12000) + "\n\n"
        val out = blocks(knowledge = "# 画像\n她喜欢猫\n", chatBody = chatBody(2, 20), intentBlock = hugeIntent)
        assertTrue("总长必须回到硬上限内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("意图被裁处要留可读交代", out.contains("持续意图过长"))
        assertTrue("意图段头仍在", out.contains("【持续意图】"))
        assertTrue("围栏闭合", out.contains("</chat>"))
    }

    @Test
    fun `trimming knowledge keeps the moment section before the profile`() {
        // 「此刻」是本轮最相关的段：知识段要收时先收画像/阶段，能装下就整段留住「此刻」。
        val moment = PromptKnowledgeSection.MOMENT_HEADER + "\n## 场景状态链\n- [不到1小时前] 她：在加班\n"
        val profile = "# 【懂得】关系画像\n## 我\n" + "旧".repeat(20000) + "\n"
        val out = blocks(knowledge = profile + "\n" + moment, chatBody = chatBody(1, 20))
        assertTrue("总长必须回到硬上限内：实到 ${out.length}", out.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("此刻段必须优先留住", out.contains(PromptKnowledgeSection.MOMENT_HEADER))
        assertTrue("此刻正文必须优先留住", out.contains("在加班"))
        assertFalse("画像应被先收掉", out.contains("旧".repeat(100)))
    }

    @Test
    fun `fitting legacy seven-arg calls stay byte-identical after the final guard`() {
        val knowledge = "# 画像\n她喜欢猫\n"
        val chat = chatBody(3, 40)
        val want = expected(knowledge, chat)
        assertEquals("放得下的无备注无历史调用原样返回，最终复核不加任何字符", want, blocks(knowledge, chat))
    }
}
