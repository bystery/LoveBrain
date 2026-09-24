package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig

/**
 * Prompt 预算与裁剪算法（从 `PromptBuilder` 拆出）。
 *
 * 这些规则决定"上下文太长时先丢谁"，全是纯字符串运算，但原先只能通过
 * 一个要 Context 和知识库的类间接触到。拆出来后才谈得上逐个边界测：
 * - 回复侧按区块优先级裁（旧记忆 → 较旧 recent → 较旧 scene → 对话头部），
 *   IDEA 与最新真实消息最后才动，`<chat>` 围栏与整行 JSON 不被切半；
 * - 锦囊侧按 section 裁，高优先 section 先保，只在条目/段落边界下刀；
 * - 兜底的头尾保留式截断（`applyBudget`）用在没有区块结构可依据的场合。
 */
object PromptBudget {

    /**
     * 按区块优先级裁剪到 [AppConfig.TOTAL_BUDGET]。
     *
     * 拼接顺序即最终 prompt 顺序，任何一处改动都会同时影响三段裁减判断。
     */
    fun byBlocks(
        knowledgeBlock: String,
        sceneBlock: String = "",
        intentBlock: String,
        ideaBlock: String,
        chatHeader: String,
        chatBody: String,
        timestampBlock: String
    ): String {
        val fullText = knowledgeBlock + "\n\n" + sceneBlock + intentBlock + ideaBlock + chatHeader + chatBody + "\n\n" + timestampBlock
        if (fullText.length <= AppConfig.TOTAL_BUDGET) return fullText

        var knowledge = knowledgeBlock
        var remaining = fullText.length - AppConfig.TOTAL_BUDGET

        // 1. 裁知识段尾部（旧记忆 raw_topic/raw_scene/lessons 在尾部）
        if (remaining > 0 && knowledge.length > remaining + 200) {
            val keepLen = knowledge.length - remaining
            knowledge = knowledge.take(keepLen) + "\n…（旧记忆因长度限制已省略）…\n"
        }

        var result = knowledge + "\n\n" + sceneBlock + intentBlock + ideaBlock + chatHeader + chatBody + "\n\n" + timestampBlock
        if (result.length <= AppConfig.TOTAL_BUDGET) return result

        // 2. 裁知识段中较旧 recent（保留最新对话段）
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

        result = knowledge + "\n\n" + sceneBlock + intentBlock + ideaBlock + chatHeader + chatBody + "\n\n" + timestampBlock
        if (result.length <= AppConfig.TOTAL_BUDGET) return result

        // 3. 裁对话记录头部（保留尾部最新消息和 </chat> 围栏闭合）
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

    /** 对外暴露的总预算截断（回复/谈心/锦囊 user 侧统一过） */
    fun applyBudget(text: String): String {
        if (text.length <= AppConfig.TOTAL_BUDGET) return text
        // 优先保留头部（画像/阶段/约束）和尾部（当前对话/时间戳），裁剪中间旧记忆
        val headLen = (AppConfig.TOTAL_BUDGET * 0.5).toInt()
        val tailLen = (AppConfig.TOTAL_BUDGET * 0.4).toInt()
        return text.take(headLen) + "\n\n…（中间旧记忆因长度限制已省略）…\n\n" + text.takeLast(tailLen)
    }

    /**
     * 锦囊上下文裁剪：边界与当前事项 > 温度摘要 > 表达偏好 > 经验。
     * 按完整 section 裁，不截断半个 section。
     *
     * 追加前先补回被裁掉的段间换行：`## 温度摘要` 与 `## 表达偏好` 粘连成一行，
     * 模型看到的就是一个不存在的段名（搬出来加用例时才发现的旧问题）。
     */
    fun trimSuggestToBudget(text: String): String {
        if (text.length <= AppConfig.SUGGEST_BUDGET) return text
        val sections = text.split(Regex("(?=^## )", RegexOption.MULTILINE))
        var remaining = AppConfig.SUGGEST_BUDGET
        val result = StringBuilder()
        fun appendSection(chunk: String) {
            if (chunk.isEmpty()) return
            if (result.isNotEmpty() && !result.endsWith("\n")) result.append("\n\n")
            result.append(chunk)
            remaining -= chunk.length
        }
        // 非分段前缀先加（如有）
        val prefix = sections.firstOrNull { !it.startsWith("## ") }
        if (prefix != null) {
            result.append(prefix)
            remaining -= prefix.length
        }
        val body = sections.drop(if (prefix != null) 1 else 0)

        // 高优先 section 先保留
        for (section in body) {
            val isHigh = SUGGEST_HIGH_SECTIONS.any { section.startsWith(it) }
            if (isHigh && remaining > 0) appendSection(trimToEntryBoundary(section, remaining))
        }
        // 低优先 section 按剩余预算裁剪
        for (section in body) {
            val isLow = SUGGEST_LOW_SECTIONS.any { section.startsWith(it) }
            if (isLow && remaining > 0) appendSection(trimToEntryBoundary(section, remaining))
        }
        // 其他 section（如时间戳等）按剩余预算裁剪
        for (section in body) {
            val isHandled = SUGGEST_HIGH_SECTIONS.any { section.startsWith(it) } || SUGGEST_LOW_SECTIONS.any { section.startsWith(it) }
            if (!isHandled && remaining > 0) appendSection(trimToEntryBoundary(section, remaining))
        }
        return result.toString()
    }

    private val SUGGEST_HIGH_SECTIONS = listOf("## 与今天相关的事项", "## 需要避开的经验")
    private val SUGGEST_LOW_SECTIONS = listOf("## 温度摘要", "## 表达偏好", "## 关系阶段")

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
