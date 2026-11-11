package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig

/**
 * Prompt 预算与裁剪算法（从 `PromptBuilder` 拆出）。
 *
 * 这些规则决定"上下文太长时先丢谁"，全是纯字符串运算，但原先只能通过
 * 一个要 Context 和知识库的类间接触到。拆出来后才谈得上逐个边界测：
 * - 回复侧按区块优先级裁（旧记忆 → 较旧 recent → 较旧 scene → 对话头部），
 *   IDEA 与最新真实消息最后才动，`<chat>` 围栏与整行 JSON 不被切半；
 * - 兜底的头尾保留式截断（`applyBudget`）用在没有区块结构可依据的场合。
 */
object PromptBudget {

    /**
     * 按区块优先级裁剪到 [AppConfig.TOTAL_BUDGET]。
     *
     * 拼接顺序即最终 prompt 顺序，任何一处改动都会同时影响三段裁减判断。
     *
     * guide-6 §7.3/§7.4（Step 3）：尾部追加两个 LIVE 补充区块参数——
     * [historicalBlock]（真实历史消息，EventHistoryAssembler 已按 3600 CU 整条预算）
     * 与 [uncertainBlock]（未定事项约束，≤120 CU）。两者：
     * - 拼装位 = noteBlock（ideaBlock 槽）之后、chatHeader 之前（§7.3 注入顺序）；
     * - 空默认串不加任何字符 → 旧 7 参调用输出**逐字节一致**（§7.4 条 1）；
     * - 裁剪序 = 原知识段尾 → 原 recent → 整块移除历史块 → 整块移除 UC 块 → 原对话头部
     *   （§11.2 条 1-3）。两块只**整块保留或整块移除**：不裸 take、不切半条历史消息、
     *   UC ≤120 CU 保持完整；对话头部裁剪规则原样（整 JSON 行、保 `</chat>`）。
     */
    fun byBlocks(
        knowledgeBlock: String,
        sceneBlock: String = "",
        intentBlock: String,
        ideaBlock: String,
        chatHeader: String,
        chatBody: String,
        timestampBlock: String,
        historicalBlock: String = "",
        uncertainBlock: String = ""
    ): String {
        // 拼装序（§7.3）：knowledge + "\n\n" + scene + intent + idea + historical + uncertain
        //             + chat + "\n\n" + timestamp。historical/uncertain 为空串时不加任何字符。
        fun compose(knowledge: String, historical: String, uncertain: String): String =
            knowledge + "\n\n" + sceneBlock + intentBlock + ideaBlock + historical + uncertain +
                chatHeader + chatBody + "\n\n" + timestampBlock

        val fullText = compose(knowledgeBlock, historicalBlock, uncertainBlock)
        if (fullText.length <= AppConfig.TOTAL_BUDGET) return fullText

        var knowledge = knowledgeBlock
        var remaining = fullText.length - AppConfig.TOTAL_BUDGET

        // 1. 裁知识段尾部（旧记忆 raw_topic/raw_scene/lessons 在尾部）——原有规则原样
        if (remaining > 0 && knowledge.length > remaining + 200) {
            val keepLen = knowledge.length - remaining
            knowledge = knowledge.take(keepLen) + "\n…（旧记忆因长度限制已省略）…\n"
        }

        var result = compose(knowledge, historicalBlock, uncertainBlock)
        if (result.length <= AppConfig.TOTAL_BUDGET) return result

        // 2. 裁知识段中较旧 recent（保留最新对话段）——原有规则原样（§11.3：recent.md
        //    是 LoveBrain 原有知识上下文，不因 ChampionEvent 历史块加入而被跳过/替代）
        remaining = result.length - AppConfig.TOTAL_BUDGET
        if (remaining > 0) {
            val recentIdx = knowledge.indexOf("# 最近对话\n")
            if (recentIdx >= 0 && recentIdx < knowledge.length - 200) {
                val recentEnd = knowledge.length
                val cutSize = minOf(remaining, recentEnd - recentIdx - 100)
                if (cutSize > 0) {
                    knowledge = knowledge.substring(0, recentIdx) +
                        "# 最近对话\n…（较旧的对话因长度限制已省略）…\n"
                    remaining -= cutSize
                }
            }
        }

        result = compose(knowledge, historicalBlock, uncertainBlock)
        if (result.length <= AppConfig.TOTAL_BUDGET) return result

        // 3. 历史事件块整块保留或整块移除（§7.4 条 5/6/7；§11.2 条 3/4/5）：
        //    禁止裸 take/切半条/改变时间顺序——移除整个历史块比把真实消息切成半句安全
        if (historicalBlock.isNotEmpty()) {
            result = compose(knowledge, "", uncertainBlock)
            if (result.length <= AppConfig.TOTAL_BUDGET) return result
        }

        // 4. UC 块整块保留或整块移除（§7.4 条 11：≤120 CU 保持完整，不裸截断）
        if (uncertainBlock.isNotEmpty()) {
            result = compose(knowledge, "", "")
            if (result.length <= AppConfig.TOTAL_BUDGET) return result
        }

        // 5. 裁对话记录头部（保留尾部最新消息和 </chat> 围栏闭合）——原有规则原样
        // 预算以完整 JSON 对象裁剪，不切半个 JSON 行
        val overflow = result.length - AppConfig.TOTAL_BUDGET
        val trimmedChat = if (chatBody.length > overflow + 100) {
            val chatOpen = "<chat>\n"
            val chatClose = "</chat>\n"
            val innerContent = chatBody.removePrefix(chatOpen).removeSuffix(chatClose)
            // 按行裁剪——每行是一个完整的 JSON 对象，不切半个
            val lines = innerContent.lines().filter { it.isNotBlank() }
            val keepLen = lines.size - (overflow / CHAT_LINE_ESTIMATE).coerceAtLeast(1)
            val keptLines = if (keepLen > 0) lines.takeLast(keepLen) else emptyList()
            if (keptLines.isNotEmpty()) {
                chatOpen + "…（较早的对话已省略）…\n" + keptLines.joinToString("\n") + "\n" + chatClose
            } else {
                chatOpen + "…（对话记录因长度限制已省略）…\n" + chatClose
            }
        } else {
            chatBody
        }

        return knowledge + "\n\n" + sceneBlock + intentBlock + ideaBlock + chatHeader + trimmedChat + "\n\n" + timestampBlock
    }

    /** 对话行的估算宽度——只用来决定"大约要丢几行" */
    private const val CHAT_LINE_ESTIMATE = 60

    /** 对外暴露的总预算截断（谈心 user 侧过这一道；回复侧走 [byBlocks] 按区块裁） */
    fun applyBudget(text: String): String {
        if (text.length <= AppConfig.TOTAL_BUDGET) return text
        // 优先保留头部（画像/阶段/约束）和尾部（当前对话/时间戳），裁剪中间旧记忆
        val headLen = (AppConfig.TOTAL_BUDGET * 0.5).toInt()
        val tailLen = (AppConfig.TOTAL_BUDGET * 0.4).toInt()
        return text.take(headLen) + "\n\n…（中间旧记忆因长度限制已省略）…\n\n" + text.takeLast(tailLen)
    }

    /**
     * 在条目/段落边界裁剪文本，不做裸 take(N) 截断。
     * 按行保留完整条目，只追加装得下的整行。
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
        return result.toString()
    }

    /** 倒取最近 N 个 H1 标题块（经验/话题共用） */
    fun lastH1Blocks(content: String, count: Int): String {
        val blocks = content.split(Regex("(?<=\n)(?=# )")).map { it.trim() }.filter { it.startsWith("# ") }
        return if (blocks.size <= count) blocks.joinToString("\n\n")
        else "…（更早的已省略）\n\n" + blocks.takeLast(count).joinToString("\n\n")
    }
}
