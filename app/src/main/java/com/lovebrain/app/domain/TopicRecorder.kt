package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.OngoingItem
import com.lovebrain.app.model.Scheme

/**
 * 话题生命周期管理器 v5。
 *
 * 核心逻辑：
 * - 每轮对话记录到 moment/recent.md（最近 2 轮，溢出→对话暂存）
 * - 场景事实写入 moment/scene.md（带时间戳的状态链）
 * - 超过 3h/3 条 的状态条目移入 memory/raw_scene.md
 * - 话题切换判定：仅凭 topic_status=new
 * - 话题切换时归档到 memory/raw_topic.md（状态倒序），重置此刻层
 * - ongoing 进行中事项合并写入 moment/plan.md（跨话题生存）
 * - 轮次/状态条目解析使用真实时间戳校验，防 schema 模板示例行混入
 */
class TopicRecorder(private val knowledgeRepo: KnowledgeRepository) {

    private val maxTopicTurns = AppConfig.MAX_TOPIC_TURNS

    /** 轮次块首行校验："- [yyyy-MM-dd HH:mm]" 真实时间戳（排除模板示例 "- [yyyy-MM-dd HH:mm]"） */
    private val roundTsRegex = Regex("^- \\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]")

    /**
     * 记录一轮对话 + 处理话题状态 + 更新场景链 + 合并进行中事项。
     *
     * @param topicStatus 从 AI 回复 JSON 中提取的话题状态（same/drift/new）
     * @param topicLabel 从 AI 回复 JSON 中提取的话题标签
     * @param sceneFacts 从 AI 回复 JSON 中提取的场景关键事实
     * @param ongoing 从 AI 回复 JSON 中提取的进行中事项变化
     * @return true 如果话题发生了切换（用于触发知识库更新）
     */
    suspend fun record(
        kb: KnowledgeBase,
        messages: List<ChatMessage>,
        scheme: Scheme,
        topicStatus: String,
        topicLabel: String,
        sceneFacts: List<String> = emptyList(),
        userHint: String = "",
        ongoing: List<OngoingItem> = emptyList()
    ): Boolean {
        val time = com.lovebrain.app.util.TimeFmt.now()
        var topicRotated = false

        // 1. 话题切换处理（仅凭 status=new 触发）
        val curTopic = knowledgeRepo.getCurrentTopic(kb.name)
        val hasTopic = curTopic.isNotBlank() && curTopic != "（等待第一次对话）"
        val shouldRotate = topicLabel.isNotBlank() && topicStatus == "new"

        if (shouldRotate) {
            if (hasTopic) {
                knowledgeRepo.rotateTopic(kb.name)
                topicRotated = true
            }
            knowledgeRepo.setCurrentTopic(kb.name, topicLabel)
        } else {
            // 非轮换：drift 更新标签 / 首次对话设默认
            val newLabel = when {
                topicStatus == "drift" && topicLabel.isNotBlank() -> topicLabel
                !hasTopic -> topicLabel.ifBlank { "日常对话" }
                else -> null
            }
            if (newLabel != null) {
                knowledgeRepo.setCurrentTopic(kb.name, newLabel)
            }
        }

        // 2. 构建本轮记录
        val schemeLabel = if (scheme.tag.contains("+")) {
            scheme.title  // 多方案组合，title 已是 "方案C-调皮+方案D-暖男" 格式
        } else {
            "方案${scheme.tag}-${scheme.title}"
        }
        val entry = buildString {
            append("- [").append(time).append("]\n")
            messages.forEach { msg ->
                append(msg.role.label).append("：").append(msg.content).append("\n")
            }
            if (userHint.isNotBlank()) {
                append("我的想法：").append(userHint.trim()).append("\n")
            }
            append("我（最终回复：").append(schemeLabel).append("）：").append(scheme.reply).append("\n")
        }

        // 3. 写入 moment/recent.md（职责拆出，见 writeRecent）
        writeRecent(kb.name, entry)

        // 4. 更新场景链（scene_facts 现为数组，join 为分号串）
        val factsStr = sceneFacts.joinToString("；").trim()
        if (factsStr.isNotBlank()) {
            updateSceneChain(kb.name, topicLabel, factsStr)
        }

        // 5. 合并进行中事项（plan.md，跨话题生存）
        mergeOngoing(kb.name, ongoing, time)

        knowledgeRepo.incrementTurnCount(kb.name)
        return topicRotated
    }

    /** 职责2（自 record 拆出）：写入 moment/recent.md，保留最近 N 轮，溢出→对话暂存 */
    private suspend fun writeRecent(kbName: String, entry: String) {
        val recentPath = "moment/recent.md"
        val existing = knowledgeRepo.readFile(kbName, recentPath)
        val blocks = if (existing.isNotBlank()) {
            existing.split(Regex("(?=^- \\[)", RegexOption.MULTILINE))
                .map { it.trim() }
                .filter { validRoundBlock(it) }
        } else emptyList()

        val kept = blocks.takeLast(maxTopicTurns - 1)
        val overflow = blocks.dropLast(maxTopicTurns - 1)
        if (overflow.isNotEmpty()) {
            knowledgeRepo.appendFile(kbName, "memory/raw_chat.md", "\n" + overflow.joinToString("\n\n") + "\n")
        }
        val newRecent = buildString {
            kept.forEach { append(it).append("\n\n") }
            append(entry).append("\n")
        }
        knowledgeRepo.writeFile(kbName, recentPath, newRecent)
    }

    /** 轮次块校验：首行必须是真实时间戳行（防模板示例行被当作轮次流入暂存/档案） */
    private fun validRoundBlock(block: String): Boolean {
        val firstLine = block.lineSequence().firstOrNull()?.trim() ?: return false
        return roundTsRegex.matches(firstLine)
    }

    /** 场景条目行校验："- [yyyy-MM-dd HH:mm] ..." */
    private fun validSceneLine(line: String): Boolean {
        return roundTsRegex.matches(line.trim().substringBefore("] ") + "]")
    }

    /**
     * 更新场景链：在头部插入新条目（带绝对时间戳）。
     * scene.md 自管理，满足任一条件的条目归档到 memory/raw_scene.md：
     *   1) 年龄超过 SCENE_CHAIN_MAX_HOURS；
     *   2) 条目数超过 SCENE_CHAIN_MAX_ENTRIES（保留最新 N 条，更老的溢出）。
     */
    private suspend fun updateSceneChain(kbName: String, topicLabel: String, sceneFacts: String) {
        val chainPath = "moment/scene.md"
        val historyPath = "memory/raw_scene.md"
        val now = System.currentTimeMillis()
        val timeStr = com.lovebrain.app.util.TimeFmt.now()

        // 新条目（带绝对时间戳，用于计算年龄）
        val newEntry = "- [$timeStr] ${topicLabel.ifBlank { "日常" }}：$sceneFacts"

        // 读取现有链（只认带真实时间戳的行，排除模板示例行）
        val existing = knowledgeRepo.readFile(kbName, chainPath)
        val existingEntries = if (existing.isNotBlank()) {
            existing.lines().filter { it.trim().startsWith("- [") && validSceneLine(it) }
        } else {
            emptyList()
        }

        // 条件1：按年龄分离（超龄归档）
        val maxAgeMs = AppConfig.SCENE_CHAIN_MAX_HOURS * 3600_000L
        val fresh = mutableListOf<String>()
        val expired = mutableListOf<String>()
        for (entry in existingEntries) {
            val entryTime = parseEntryTime(entry)
            if (entryTime > 0 && (now - entryTime) > maxAgeMs) {
                expired.add(entry)
            } else {
                fresh.add(entry)
            }
        }

        // 组装候选链（新条目在最前），条件2：超出条数上限的最老条目归档
        val candidate = mutableListOf(newEntry)
        candidate.addAll(fresh)
        val maxEntries = AppConfig.SCENE_CHAIN_MAX_ENTRIES
        val keptEntries = candidate.take(maxEntries)
        if (candidate.size > maxEntries) {
            expired.addAll(candidate.drop(maxEntries))
        }

        // 写入更新后的 chain
        knowledgeRepo.writeFile(kbName, chainPath, keptEntries.joinToString("\n") + "\n")

        // 归档条目追加到 history
        if (expired.isNotEmpty()) {
            val historyContent = buildString {
                expired.forEach { append(it).append("\n") }
            }
            knowledgeRepo.appendFile(kbName, historyPath, historyContent)
        }
    }

    /** 从条目中解析时间戳："- [2026-07-24 19:32] ..." → epoch millis */
    private fun parseEntryTime(entry: String): Long {
        val match = Regex("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]").find(entry) ?: return 0
        return com.lovebrain.app.util.TimeFmt.parse(match.groupValues[1])
    }

    /**
     * 合并 ongoing 到 moment/plan.md（双二级标题重构）。
     * 结构：`# 事项计划` + `## 进行中`（注入 prompt）+ `## 已结束`（不注入，防膨胀）。
     * 匹配事项名：存在 → 追加 "→[time]新状态（当前）"；不存在 → 新增行。
     * 已完成/已取消的事项移入 `## 已结束`；已结束区超过上限时丢弃最旧（防越来越大）。
     */
    private suspend fun mergeOngoing(kbName: String, items: List<OngoingItem>, timeStr: String) {
        val planPath = "moment/plan.md"
        val content = knowledgeRepo.readFile(kbName, planPath)

        // 解析两个分区（兼容旧格式：无 ## 标题的事项行默认归入"进行中"，防迁移丢数据）
        val activeLines = mutableListOf<String>()
        val endedLines = mutableListOf<String>()
        var section = "active"
        for (line in content.lines()) {
            val t = line.trim()
            when {
                t.startsWith("## 进行中") -> section = "active"
                t.startsWith("## 已结束") -> section = "ended"
                t.startsWith("#") || t.startsWith("<!--") -> { /* 标题/注释跳过 */ }
                t.contains("|") -> when (section) {
                    "active" -> activeLines.add(t)
                    "ended" -> endedLines.add(t)
                }
            }
        }

        val parse = { lines: List<String> ->
            lines.mapNotNull { l ->
                val parts = l.split("|").map { it.trim() }
                if (parts.size >= 3 && parts[0].isNotBlank()) PlanItemView(parts[0], parts[1], parts.drop(2).joinToString("|").trim()) else null
            }.toMutableList()
        }
        val active = parse(activeLines)
        val ended = parse(endedLines)

        // 合并本轮 ongoing 到 active
        for (item in items) {
            val name = item.name.trim()
            val state = item.state.trim()
            if (name.isBlank() || state.isBlank()) continue
            // FILLER_STATES 精确匹配：命中固定填充词 → skip 不写 plan（避免重复堆积）
            val normalizedState = state.trim().trimEnd(',', '。', '！', '?')
            // 终态（已完成/已取消）豁免跳过，防闭环被吞；非终态命中固定词则跳过
            if (item.status != "已完成" && item.status != "已取消" && 
                normalizedState in FILLER_STATES) continue
            
            val status = item.status.trim().ifBlank { "进行中" }
            val existing = active.firstOrNull { it.name == name } ?: ended.firstOrNull { it.name == name }
            if (existing != null) {
                existing.status = status
                val oldChain = existing.chain.replace("（当前）", "").trimEnd('→', ' ')
                existing.chain = "$oldChain→[$timeStr]$state（当前）"
                // 若从 ended 重新激活，移回 active
                ended.remove(existing)
                if (active.none { it.name == name }) active.add(existing)
            } else {
                active.add(PlanItemView(name, status, "[$timeStr]$state（当前）"))
            }
        }

        // 已完成/已取消 → 移入 ended
        val stillActive = mutableListOf<PlanItemView>()
        for (p in active) {
            if (p.status == "已完成" || p.status == "已取消") {
                if (ended.none { it.name == p.name }) ended.add(p)
            } else stillActive.add(p)
        }

        // plan_archive 废除：不再裁剪 ended 列表，所有已结束事项永久保留在 plan.md
        knowledgeRepo.writeFile(kbName, planPath, renderPlan(stillActive, ended))
    }

    /** 职责3（自 mergeOngoing 拆出）：把进行中/已结束两区渲染为 plan.md 文本 */
    private fun renderPlan(active: List<PlanItemView>, ended: List<PlanItemView>): String = buildString {
        append("# 事项计划\n\n## 进行中\n")
        active.forEach { append(it.name).append(" | ").append(it.status).append(" | ").append(it.chain).append("\n") }
        append("\n## 已结束\n")
        ended.forEach { append(it.name).append(" | ").append(it.status).append(" | ").append(it.chain).append("\n") }
    }

    /** plan.md 行视图（name/status/chain），供 parse/apply/render 共用 */
    private data class PlanItemView(val name: String, var status: String, var chain: String)

    companion object {
        /** 固定填充词：命中这些 state → skip 不写 plan（避免重复堆积） */
        private val FILLER_STATES = setOf(
            "本轮未提及", "本轮无进展",
            "本轮未提及，持续推进", "本轮无进展，持续推进",
            "本轮未提及，事项持续推进中", "本轮无进展，事项持续推进中"
        )
    }
    /** 获取经验提取的完整上下文：当前话题 + 场景链 + 最近对话 + 暂存 + 话题档案（最近N个） */
    suspend fun getTopicFullContext(kbName: String, topicCount: Int = AppConfig.VECTOR_CONTEXT_TOPICS): String {
        val topic = knowledgeRepo.getCurrentTopic(kbName)
        val sceneChain = knowledgeRepo.readFile(kbName, "moment/scene.md")
        val recent = knowledgeRepo.readFile(kbName, "moment/recent.md")
        val rawChat = knowledgeRepo.readFile(kbName, "memory/raw_chat.md")
        val rawScene = knowledgeRepo.readFile(kbName, "memory/raw_scene.md")
        val rawTopic = knowledgeRepo.readFile(kbName, "memory/raw_topic.md")
        return buildString {
            append("当前话题：").append(topic).append("\n\n")
            if (sceneChain.isNotBlank()) {
                append("【此刻状态】\n").append(sceneChain.trim()).append("\n\n")
            }
            if (rawScene.isNotBlank()) {
                append("【状态暂存】\n").append(rawScene.trim()).append("\n\n")
            }
            if (rawChat.isNotBlank()) {
                append("【对话暂存】\n").append(rawChat.trim()).append("\n\n")
            }
            if (recent.isNotBlank()) {
                append("【最近对话】\n").append(recent.trim()).append("\n\n")
            }
            if (rawTopic.isNotBlank()) {
                append("【已结束话题（近期）】\n")
                append(lastTopics(rawTopic, topicCount))
                append("\n\n")
            }
        }
    }

    /** 向量重估专用上下文：当前话题 + 场景 + 最近对话 + 最近 N 个话题档案 */
    suspend fun getVectorContext(kbName: String): String {
        val topic = knowledgeRepo.getCurrentTopic(kbName)
        val sceneChain = knowledgeRepo.readFile(kbName, "moment/scene.md")
        val recent = knowledgeRepo.readFile(kbName, "moment/recent.md")
        val rawTopic = knowledgeRepo.readFile(kbName, "memory/raw_topic.md")
        return buildString {
            append("当前话题：").append(topic).append("\n")
            if (sceneChain.isNotBlank()) {
                append("【此刻状态】\n").append(sceneChain.trim()).append("\n\n")
            }
            if (recent.isNotBlank()) {
                append("【最近对话】\n").append(recent.trim()).append("\n\n")
            }
            if (rawTopic.isNotBlank()) {
                append("【最近话题档案】\n")
                append(lastTopics(rawTopic, AppConfig.VECTOR_CONTEXT_TOPICS))
            }
        }
    }

    /** 从话题档案中倒取最近 N 个话题（按 H1 "# " 分割） */
    private fun lastTopics(text: String, count: Int): String {
        val valid = text.split(Regex("(?<=\\n)(?=# )")).map { it.trim() }.filter { it.startsWith("# ") }
        return if (valid.size <= count) {
            valid.joinToString("\n\n")
        } else {
            "…（更早的话题已省略）\n\n" + valid.takeLast(count).joinToString("\n\n")
        }
    }
}
