package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.prompt.PromptKnowledgeSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Prompt 预算与裁剪算法（从 `PromptBuilder` 拆出）。
 *
 * 2026-11-21 结构收敛后的拼接序即最终 prompt 顺序：
 * ```
 * 知识段（含尾部历史受保护段） → 当前场景 → 持续意图 → <chat> → 用户备注 → 当前时间
 * ```
 * - 知识段由 `PromptKnowledgeSection` 装配；冠军历史块渲染在知识段尾部（原 recent 槽），
 *   通过 [historicalBlock] 交给本层，**只整块保留或整块移除**，绝不按字符/按行截断。
 * - 「未定事项（UC）」块已随用户拍板整块删除，本层不再有 UC 参数与裁剪步。
 * - 原「# 最近对话」锚点已从知识段删除，对应的 recent 裁剪分支一并删除。
 * - 用户备注（`replyDirective.text`）位于 `<chat>` 之后、当前时间之前，**不是**"永不裁"：
 *   它是固定块里唯一来自自由输入的文本，超过 [USER_NOTE_MAX] 必须按行/字符边界收，
 *   否则备注自己就能把 prompt 顶出 [AppConfig.TOTAL_BUDGET]，而下面所有裁剪步都动不了它。
 *
 * 裁剪优先序：
 * 1. 历史块整块移除 → 2. 知识段正文（条目/行边界，此刻优先于画像/阶段）→
 * 3. 对话头部整 JSON 行（单行超限时在 JSON 字符串内安全截断）→
 * 4. 最终总长复核：对话整行 → 知识段条目边界 → 用户备注 → 持续意图；时间戳永不动。
 *
 * 历史块是"整块保留或整块移除"的最小可丢单元，先丢它；正文与对话都只能按
 * 条目/整行丢——任何路径都不许留下半行、半个代理对或半条 JSON。
 * [AppConfig.TOTAL_BUDGET] 是硬上限：返回前所有分支都必须收敛到它以内。
 */
object PromptBudget {

    /**
     * 用户备注块的总长上限。
     *
     * 备注是用户手写的自由文本，长度不受 UI 限制；它作为固定块进入 [byBlocks] 后
     * 原先任何裁剪步都不会碰它，于是 >9000 的备注会让"收尽知识/对话"的循环空转、
     * 最终返回超帽 prompt。给一个远大于正常备注、又装得下其余固定块的上限。
     */
    const val USER_NOTE_MAX = 4000

    /**
     * 按区块优先级裁剪到 [AppConfig.TOTAL_BUDGET]。
     *
     * @param knowledgeBlock 知识段全文；[historyInKnowledge] 为真时其尾部的 [historicalBlock] 会被拆出来保护
     * @param sceneBlock 当前场景段
     * @param intentBlock 持续意图段
     * @param userNoteBlock 用户备注块（chat 之后、时间戳之前；空 = 无备注）
     * @param chatHeader `<chat>` 前的对话记录标题
     * @param chatBody `<chat>` 围栏正文
     * @param timestampBlock 当前时间块（垫底）
     * @param historicalBlock 冠军历史块；是否已渲染在 [knowledgeBlock] 尾部由 [historyInKnowledge] 显式声明（真源 [PromptKnowledgeSection.Output]）
     */
    fun byBlocks(
        knowledgeBlock: String,
        sceneBlock: String = "",
        intentBlock: String,
        userNoteBlock: String = "",
        chatHeader: String,
        chatBody: String,
        timestampBlock: String,
        historicalBlock: String = "",
        historyInKnowledge: Boolean = false
    ): String {
        // 有库分支：历史块已由 PromptKnowledgeSection 渲染进知识段尾部（拆出来单独保护）；
        // 无库分支：历史块由调用方单独传入，直接拼在知识段之后。
        var knowledge = if (historyInKnowledge) knowledgeBlock.removeSuffix(historicalBlock) else knowledgeBlock
        var history = historicalBlock
        // 备注先收到自己的上限：它是固定块里唯一的自由文本，越界的备注必须在
        // 进入拼装前就变成"可读的短备注"，否则下面的循环无块可收。
        var note = capUserNote(userNoteBlock)
        var intent = intentBlock

        fun compose(
            body: String,
            hist: String,
            chat: String = chatBody,
            noteText: String = note,
            intentText: String = intent
        ): String =
            body + hist + "\n\n" + sceneBlock + intentText + chatHeader + chat + noteText +
                "\n\n" + timestampBlock

        var result = compose(knowledge, history)
        if (result.length <= AppConfig.TOTAL_BUDGET) return result

        // 1. 历史块整块先丢：它是唯一"能整块拿走"的受保护段，拿走也不留半条消息、
        //    不改变时间顺序；而正文一旦开切就只能按条目丢，代价更高。
        if (history.isNotEmpty()) {
            history = ""
            result = compose(knowledge, "")
            if (result.length <= AppConfig.TOTAL_BUDGET) return result
        }

        // 2. 历史已整块丢完仍超，才动知识正文：按条目/行边界丢（[trimToEntryBoundary]），
        //    绝不裸 take——半行、半个代理对、半条 JSON 都是从这里来的。
        //    正文不足 200 字时丢光也填不平缺口，留给对话侧（保持"小块不动"的原有判据）。
        val remaining = result.length - AppConfig.TOTAL_BUDGET
        if (knowledge.length > remaining + 200) {
            knowledge = shrinkKnowledge(knowledge, knowledge.length - remaining)
            result = compose(knowledge, "")
            if (result.length <= AppConfig.TOTAL_BUDGET) return result
        }

        // 3. 裁对话记录头部（保留尾部最新消息和 </chat> 围栏闭合）——原有规则原样
        // 预算以完整 JSON 对象裁剪，不切半个 JSON 行
        val overflow = result.length - AppConfig.TOTAL_BUDGET
        val trimmedChat = if (chatBody.length > overflow + 100) {
            val chatOpen = "<chat>\n"
            val chatClose = "</chat>\n"
            val innerContent = chatBody.removePrefix(chatOpen).removeSuffix(chatClose)
            // 按行裁剪——每行是一个完整的 JSON 对象，不切半个
            val lines = innerContent.lines().filter { it.isNotBlank() }
            val keepLen = lines.size - (overflow / CHAT_LINE_ESTIMATE).coerceAtLeast(1)
            // 只剩一行也必须留住最近一轮（待回消息）：整行放不下时交给下面的最终复核
            // 在 JSON 字符串内部截断，而不是整段换成占位句。
            val keptLines = if (keepLen > 0) lines.takeLast(keepLen) else lines.takeLast(1)
            if (keptLines.isNotEmpty()) {
                chatOpen + "…（较早的对话已省略）…\n" + keptLines.joinToString("\n") + "\n" + chatClose
            } else {
                chatOpen + "…（对话记录因长度限制已省略）…\n" + chatClose
            }
        } else {
            chatBody
        }

        // 最终总长复核：按行数估算（CHAT_LINE_ESTIMATE）与"对话太短不值得裁"的 else 分支
        // 都可能让拼好的文本仍超预算。这里对着真实长度逐整行丢最旧对话，丢到全省略形为止；
        // 只剩最近一轮时改为在 JSON 字符串内截断（闭合引号、转义由序列化器保证）。
        // 还超就按条目边界收知识段——围栏与时间戳不动，自由文本固定块留到最后两步收。
        val chatOpen = "<chat>\n"
        val chatClose = "</chat>\n"
        var chat = trimmedChat
        var finalText = compose(knowledge, "", chat)
        while (finalText.length > AppConfig.TOTAL_BUDGET) {
            if (!chat.startsWith(chatOpen) || chat.takeLast(chatClose.length) != chatClose) break
            val payload = chat.removePrefix(chatOpen).removeSuffix(chatClose)
                .lines().filter { it.isNotBlank() && !it.startsWith("…") }
            if (payload.isEmpty()) {
                val floored = chatOpen + "…（对话记录因长度限制已省略）…\n" + chatClose
                if (chat == floored || floored.length >= chat.length) break
                chat = floored
            } else if (payload.size == 1) {
                // 最后一轮单条超限：在 JSON 字符串内部安全截断，至少保住这一轮
                val only = payload.last()
                val shrunk = shrinkChatLine(only, only.length - (finalText.length - AppConfig.TOTAL_BUDGET))
                if (shrunk == null) {
                    val floored = chatOpen + "…（对话记录因长度限制已省略）…\n" + chatClose
                    if (chat == floored) break
                    chat = floored
                } else {
                    chat = chatOpen + "…（较早的对话已省略）…\n" + shrunk + "\n" + chatClose
                }
            } else {
                val rest = payload.drop(1)
                chat = if (rest.isEmpty()) {
                    chatOpen + "…（对话记录因长度限制已省略）…\n" + chatClose
                } else {
                    chatOpen + "…（较早的对话已省略）…\n" + rest.joinToString("\n") + "\n" + chatClose
                }
            }
            finalText = compose(knowledge, "", chat)
        }
        // 条目边界收知识段也要对着真实长度复核：整行粒度与省略标记兜底都可能留下残差
        // （固定区块加上标记本身就能超预算）。每轮按残差继续收，直到预算内或知识段
        // 触底为空——绝不裸 take 对话行、不动固定区块，</chat> 始终闭合。
        while (finalText.length > AppConfig.TOTAL_BUDGET && knowledge.isNotEmpty()) {
            val residual = finalText.length - AppConfig.TOTAL_BUDGET
            val next = shrinkKnowledge(knowledge, (knowledge.length - residual).coerceAtLeast(0))
            if (next.length >= knowledge.length) break
            knowledge = next
            finalText = compose(knowledge, "", chat)
        }
        // 对话与知识都收尽后仍超，只能收用户备注：它是最后一个能优雅截断的自由文本，
        // 时间戳与围栏必须留在原地——硬上限优先于"备注整段不被裁"的旧口径。
        while (finalText.length > AppConfig.TOTAL_BUDGET && note.isNotEmpty()) {
            val residual = finalText.length - AppConfig.TOTAL_BUDGET
            val next = shrinkFixedText(note, (note.length - residual).coerceAtLeast(0), USER_NOTE_TRUNCATED_MARKER)
            if (next.length >= note.length) break
            note = next
            finalText = compose(knowledge, "", chat)
        }
        // 持续意图正文同样来自用户自由输入；走到这里说明前面全收尽了，它是最后一块可收的。
        // 不这样做时，一条超长意图能让本函数无条件返回 >9000 的 prompt。
        while (finalText.length > AppConfig.TOTAL_BUDGET && intent.isNotEmpty()) {
            val residual = finalText.length - AppConfig.TOTAL_BUDGET
            val next = shrinkFixedText(intent, (intent.length - residual).coerceAtLeast(0), INTENT_TRUNCATED_MARKER)
            if (next.length >= intent.length) break
            intent = next
            finalText = compose(knowledge, "", chat)
        }
        return finalText
    }

    /** 知识段省略标记——"这里裁过"的交代，正文触底时它自身也会变成残差来源 */
    private const val KNOWLEDGE_MARKER = "\n…（旧记忆因长度限制已省略）…\n"

    /** 用户备注裁断标记：保留可读结尾，说明后面还有内容 */
    private const val USER_NOTE_TRUNCATED_MARKER = "\n…（用户备注过长，后面已省略）…\n"

    /** 持续意图裁断标记 */
    private const val INTENT_TRUNCATED_MARKER = "\n…（持续意图过长，后面已省略）…\n"

    /** 用户备注先收到 [USER_NOTE_MAX]；未超限原样返回，一个字节都不动 */
    private fun capUserNote(block: String): String =
        if (block.length <= USER_NOTE_MAX) block
        else shrinkFixedText(block, USER_NOTE_MAX, USER_NOTE_TRUNCATED_MARKER)

    /**
     * 固定自由文本块的有界裁剪：优先按行边界收，首行就超（单行超长备注）时退到
     * 字符边界；两者都不切断代理对，并留下 [marker] 作为"后面还有"的交代。
     * [target] 小到装不下"标记 + 可读片段"时返回空串——这里是最后一档，不能留下
     * 一个没有说明的碎片。
     */
    private fun shrinkFixedText(text: String, target: Int, marker: String): String {
        if (text.length <= target) return text
        if (target < marker.length + 16) return ""
        val room = target - marker.length
        val byLines = trimToEntryBoundary(text, room)
        // 行边界只是"更好读"；头一行就装不下时按字符收，否则整份备注会被一个长行吞掉。
        val kept = if (byLines.length >= room / 2) byLines else takeSafe(text, room)
        if (kept.length >= text.length) return text
        return kept.trimEnd() + marker
    }

    /**
     * 知识段优先级：先把「此刻」之后整段留住，只收它前面的画像/阶段；
     * 「此刻」本身都装不下时才回到原来的从头收。无「此刻」时行为与原实现一致。
     */
    private fun shrinkKnowledge(knowledge: String, target: Int): String {
        if (target <= 0) return ""
        // 省略标记可能停在上一次裁剪的位置（甚至夹在段中间），先摘掉再重算，
        // 否则标记判据会各认一半、标记越叠越多。
        val markerIdx = knowledge.indexOf(KNOWLEDGE_MARKER)
        val body = if (markerIdx >= 0) knowledge.removeRange(markerIdx, markerIdx + KNOWLEDGE_MARKER.length) else knowledge
        val room = target - KNOWLEDGE_MARKER.length
        if (room <= 0) return ""
        val momentIdx = body.indexOf(PromptKnowledgeSection.MOMENT_HEADER)
        if (momentIdx > 0) {
            val tail = body.substring(momentIdx)
            if (tail.length <= room) {
                val head = body.substring(0, momentIdx)
                val headKept = trimToEntryBoundary(head, room - tail.length)
                return if (headKept.length < head.length) headKept + KNOWLEDGE_MARKER + tail
                else body + KNOWLEDGE_MARKER
            }
        }
        val kept = trimToEntryBoundary(body, room)
        return when {
            kept.length < body.length -> kept + KNOWLEDGE_MARKER
            body.isEmpty() -> ""
            markerIdx >= 0 -> body + KNOWLEDGE_MARKER
            else -> knowledge
        }
    }

    /** 对话行的估算宽度——只用来决定"大约要丢几行" */
    private const val CHAT_LINE_ESTIMATE = 60

    /** 单条对话行被内部截断时追加在 text 里的标记 */
    private const val CHAT_TRUNCATED_MARKER = "…（本条过长已截断）"

    /**
     * 在条目/段落边界裁剪文本，不做裸 take(N) 截断。
     * 按行保留完整条目，只追加装得下的整行；裁断处若只剩一个段头，把段头也去掉
     * （没有正文的段头会让模型去找那段不存在的内容）。
     */
    fun trimToEntryBoundary(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        val lines = text.split("\n")
        val result = StringBuilder()
        for (line in lines) {
            if (result.length + line.length + 1 > maxLength) break
            if (result.isNotEmpty()) result.append("\n")
            result.append(line)
        }
        var kept = result.toString()
        while (true) {
            val lastLine = kept.substringAfterLast('\n')
            if (lastLine.startsWith("#")) kept = kept.substringBeforeLast('\n', "") else break
        }
        return kept
    }

    /**
     * 单条 JSON 对话行在字符串值内部安全截断：解析 → 缩最长的字符串字段（生产形状即 `text`）
     * → 重新序列化，引号/转义交给序列化器，绝不产生非法 JSON。不是"带长字符串字段的
     * JSON 对象"或缩到头也放不下时返回 null，由调用方退到整段占位。
     */
    private fun shrinkChatLine(line: String, maxLen: Int): String? {
        if (maxLen <= 0) return null
        val obj = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return null
        // 取最长字符串字段：预算层不认识 ChatTranscriptBlock 的字段名，但"哪一格是正文"由长度决定
        val key = obj.entries
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content.length } }
            .maxByOrNull { it.second }?.first ?: return null
        val text = (obj[key] as JsonPrimitive).content
        var room = maxLen - (line.length - text.length) - CHAT_TRUNCATED_MARKER.length
        while (room > 0) {
            val head = takeSafe(text, room)
            if (head.isEmpty()) return null
            val candidate = JsonObject(
                obj.mapValues { (k, v) -> if (k == key) JsonPrimitive(head + CHAT_TRUNCATED_MARKER) else v }
            )
            val out = Json.encodeToString(JsonObject.serializer(), candidate)
            if (out.length <= maxLen) return out.takeIf { it.length < line.length }
            // 转义会让序列化结果比"原文长度"估算更长，按实际超出量再收一点
            room -= (out.length - maxLen).coerceAtLeast(1)
        }
        return null
    }

    /** 按字符边界取前缀，且不把代理对切成两半 */
    private fun takeSafe(text: String, max: Int): String {
        if (text.length <= max) return text
        var end = max.coerceAtLeast(0)
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end)
    }

    /** 倒取最近 N 个 H1 标题块（经验/话题共用） */
    fun lastH1Blocks(content: String, count: Int): String {
        val blocks = content.split(Regex("(?<=\n)(?=# )")).map { it.trim() }.filter { it.startsWith("# ") }
        return if (blocks.size <= count) blocks.joinToString("\n\n")
        else "…（更早的已省略）\n\n" + blocks.takeLast(count).joinToString("\n\n")
    }
}
