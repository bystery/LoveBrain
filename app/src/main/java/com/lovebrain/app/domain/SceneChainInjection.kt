package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.util.TimeFmt

/**
 * 场景链注入前的转换（从 `PromptBuilder` 拆出）。
 *
 * 这一步决定"屏上看到的旧状态有多少会被当成这一轮的事实喂给模型"，四件事各有分工，
 * 混在 prompt 拼装里就没人能单独验证：
 * - 龄标注：写的是"距现在多久"，不是原始时间戳；
 * - 精确去重：同一条事实文本只保留最新版本（从新到旧遍历）；
 * - 过期过滤：超过 [AppConfig.SCENE_CHAIN_MAX_HOURS] 不再注入——**过期只表示不再注入，
 *   不代表事件已经结束**，不能在这里把它改成"已了结"；
 * - 来源身份保留：复用写入端 `|src=…|spk=…|subj=…` 格式解析，
 *   无来源标记的新输出不作为可信状态注入。
 *
 * 时钟与"今天"都是入参（默认取当前值），所以"跨小时/跨日"的分支能被固定测。
 */
object SceneChainInjection {

    /** 转换场景链；没有可注入条目时返回空串（调用方据此整段省略） */
    fun transform(
        content: String,
        now: Long = System.currentTimeMillis(),
        today: String = TimeFmt.today()
    ): String {
        val entryRegex = Regex("^- \\[(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2})]\\s*(.*)$")
        val maxAgeMs = AppConfig.SCENE_CHAIN_MAX_HOURS * 3600_000L

        data class Entry(val ts: Long, val date: String, val labelAndFacts: String)

        val entries = content.lines().mapNotNull { line ->
            val match = entryRegex.find(line.trim()) ?: return@mapNotNull null
            val ts = TimeFmt.parse("${match.groupValues[1]} ${match.groupValues[2]}")
            Entry(ts, match.groupValues[1], match.groupValues[3])
        }
        if (entries.isEmpty()) return ""

        // 过滤超龄条目
        val freshEntries = entries.filter { e ->
            e.ts <= 0 || (now - e.ts) <= maxAgeMs
        }
        if (freshEntries.isEmpty()) return ""

        // 精确文本去重——从最新到最旧，相同事实文本只保留最新版本
        val seenFactTexts = mutableSetOf<String>()
        val out = StringBuilder()
        for (e in freshEntries) {
            val ageH = if (e.ts > 0) ((now - e.ts) / 3600_000L).toInt() else 0
            val ageLabel = when {
                e.ts <= 0 -> "时间未知"
                ageH < 1 -> "不到1小时前"
                e.date != today -> "${e.date.takeLast(5)} ${ageH}小时前"
                else -> "${ageH}小时前"
            }
            val colonIdx = e.labelAndFacts.indexOf('：')
            val label = if (colonIdx >= 0) e.labelAndFacts.substring(0, colonIdx).trim() else e.labelAndFacts.trim()
            val factsRaw = if (colonIdx >= 0) e.labelAndFacts.substring(colonIdx + 1) else ""
            val facts = factsRaw.split('；', ';').map { it.trim() }.filter { it.isNotBlank() }
            val keptFacts = mutableListOf<String>()
            for (f in facts) {
                val cleanFact = cleanFact(f)
                if (cleanFact.isNotBlank() && cleanFact !in seenFactTexts) {
                    keptFacts.add(cleanFact)
                    seenFactTexts.add(cleanFact)
                }
            }
            if (keptFacts.isNotEmpty()) {
                out.append("- [").append(ageLabel).append("] ").append(label)
                out.append("：").append(keptFacts.joinToString("；"))
                out.append("\n")
            }
        }
        return out.toString().trim()
    }

    /**
     * 从事实文本里剥掉来源标记，只留事实文本用于注入。
     * 与写入端 `TopicRecorder.parseStoredFact` 用同一套格式，新旧两种都要认。
     */
    fun cleanFact(factText: String): String {
        // 新格式：事实文本|src=...|spk=...|subj=...
        if (factText.contains("|src=") || factText.contains("|spk=") || factText.contains("|subj=")) {
            val parts = factText.split("|").map { it.trim() }
            return parts.firstOrNull()?.trim().orEmpty()
        }
        // 旧格式：事实文本⟨sourceIds⟩
        val srcMatch = Regex("(.*)⟨.+⟩$").find(factText)
        if (srcMatch != null) {
            return srcMatch.groupValues[1].trim()
        }
        // 无标记：纯文本
        return factText.trim()
    }
}
