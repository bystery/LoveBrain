package com.lovebrain.app.data

import com.lovebrain.app.util.TimeFmt
import java.security.MessageDigest

/**
 * 知识库里**不碰文件系统**的文本/格式算法，从 `KnowledgeRepository` 拆出来。
 *
 * 拆的理由不是"文件太长"这一句审美判断，而是这些规则本来就该能被单独测：
 * 场景条目怎么排序、状态链怎么去重、plan 的说明行怎么包进注释——
 * 它们原先长在 2200 行的仓库类里，只有连着临时目录与互斥锁才能间接触到。
 *
 * 这里每个函数都保持与原实现逐字同构，仓库侧只做转调。
 */
internal object KbTextOps {

    /** 内容哈希（归档操作状态用它判断输入是否变化、拼 operationId） */
    fun contentHash(vararg contents: String): String = sha256(contents.joinToString("")) .take(16)

    /** SHA-256 十六进制串——版本快照与内容指纹的唯一算法 */
    fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // ═══════════ 当前话题行（moment/topic.md）═══════════

    /**
     * 话题行的标记。全仓只这一份字面量——写侧 4 处、读侧 1 处以前各写各的，
     * 任何一处改字，`topicLabel` 就静默读不出话题（`rotateTopic` 会把整行当旧话题名归档）。
     */
    const val TOPIC_MARKER = "正在聊："

    /** 话题行尾部的键值后缀（`| key：xxx`），不属于话题名 */
    const val TOPIC_KEY_SUFFIX = " | key："

    /** 新库初始化的那句话 */
    const val TOPIC_INITIAL_LABEL = "（等待第一次对话）"

    /** 唯一的话题行写法 */
    fun topicLine(time: String, label: String): String = "- [$time] $TOPIC_MARKER$label"

    /**
     * 从 topic.md 里读回话题名。
     *
     * ⚠ 保留一条既存怪癖：首行里**没有**标记时，`substringAfter` 会把整行原样返回
     * （比如旧格式 `- [2026-09-16 09:00] 旧话题` 会整条当成话题名）。
     * 这次不顺手改它——改法要么"读不到就返回空"（会让归档标题与初始态判断跟着变），
     * 要么兼容多种旧格式，那是一次独立的行为变更，得单独判断。
     * `TopicLineFormatTest` 把这条怪癖钉成断言，钉住不等于认可。
     */
    fun topicLabel(content: String): String {
        val raw = content.lines().firstOrNull()?.trim()?.substringAfter(TOPIC_MARKER) ?: ""
        return raw.substringBefore(TOPIC_KEY_SUFFIX).trim()
    }

    // ═══════════ 当下状态条目（moment/scene.md）═══════════

    /** 状态条目行校验：必须以 "- [yyyy-MM-dd HH:mm]" 真实时间戳开头（防 schema 模板示例行混入） */
    private val entryLineRegex = Regex("^- \\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]")

    /**
     * 合并多个来源的状态条目，按时间戳倒序（最新在前）。
     *
     * 无合法时间戳的行不丢弃——标记为 `[legacy]` 保留在尾部，防用户数据被静默遗漏。
     */
    fun mergeSceneEntries(vararg sources: String): String {
        val allLines = sources.flatMap { it.lines() }.map { it.trimEnd() }.filter { it.isNotBlank() }
        val validEntries = allLines.filter { entryLineRegex.containsMatchIn(it) }
        val legacyEntries = allLines.filter { !entryLineRegex.containsMatchIn(it) }
        if (validEntries.isEmpty() && legacyEntries.isEmpty()) return ""
        val sorted = validEntries.sortedByDescending { entryTimestamp(it) }
        val sb = StringBuilder()
        if (sorted.isNotEmpty()) {
            sb.append(sorted.joinToString("\n")).append("\n")
        }
        if (legacyEntries.isNotEmpty()) {
            sb.append("# [legacy] 以下为无法解析时间戳的历史内容\n")
            legacyEntries.forEach { sb.append(it).append("\n") }
        }
        return sb.toString()
    }

    /** 取条目里的时间戳；解析不出来返回 0（即永远排在最后） */
    fun entryTimestamp(entry: String): Long {
        val match = Regex("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]").find(entry) ?: return 0L
        return TimeFmt.parse(match.groupValues[1])
    }

    // ═══════════ 进行中事项的状态链（moment/plan.md）═══════════

    /** 清理状态链——合并连续重复状态（保留最新一条），并截断到最近 10 条 */
    fun cleanStateChain(chain: String): String {
        val states = chain.split("→").filter { it.isNotBlank() }
        if (states.isEmpty()) return chain

        val deduped = mutableListOf<String>()
        for (state in states) {
            val last = deduped.lastOrNull()
            if (last != null && normalizeStateForCompare(last) == normalizeStateForCompare(state)) {
                deduped[deduped.lastIndex] = state
            } else {
                deduped.add(state)
            }
        }

        val kept = if (deduped.size > 10) deduped.takeLast(10) else deduped
        return kept.joinToString("→")
    }

    /** 归一化状态文本用于比较——去掉时间戳前缀和"（当前）"标记 */
    fun normalizeStateForCompare(state: String): String {
        val afterBracket = if (state.contains("]")) {
            val lastBracket = state.lastIndexOf(']')
            if (lastBracket >= 0) state.substring(lastBracket + 1) else state
        } else state
        return afterBracket.replace("（当前）", "").trim()
    }

    // ═══════════ 事项文件里的说明行（moment/plan.md）═══════════

    /**
     * 把旧版 plan.md 的裸"格式/示例"说明行包进 HTML 注释。
     *
     * 包注释后：编辑器里仍可见、预览时隐藏、且不会被当成事项内容进 prompt。
     * 已在注释里的行原样保留，不重复包。
     */
    fun wrapPlanMetaLines(text: String): String {
        if (text.isBlank()) return text
        val sb = StringBuilder()
        var inComment = false
        for (line in text.lines()) {
            val t = line.trim()
            when {
                inComment -> {
                    sb.append(line).append("\n")
                    if (t.contains("-->")) inComment = false
                }
                t.startsWith("<!--") -> {
                    sb.append(line).append("\n")
                    if (!t.contains("-->")) inComment = true
                }
                t.startsWith("格式") || t.startsWith("示例") -> {
                    sb.append("<!-- ").append(t).append(" -->\n")
                }
                else -> sb.append(line).append("\n")
            }
        }
        return sb.toString().trimEnd('\n') + "\n"
    }
}
