package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.OngoingItem
import com.lovebrain.app.model.Scheme

/**
 * 话题生命周期管理器 v6。
 *
 * P0 修复（scene 语义重写）：
 * - 旧事实保留原 timestamp，绝不复制到新时间戳
 * - 过期事实先过滤，再处理新事实，防止复活
 * - identity 包含 subject（她/我），不再 removePrefix 删身份
 * - 同轮同 identity last-wins
 * - 新事实只操作同 identity 的旧 fact：replace/delete/append
 *
 * 核心逻辑：
 * - 每轮对话记录到 moment/recent.md（最近 2 轮，溢出→对话暂存）
 * - 场景事实写入 moment/scene.md（带时间戳的状态链）
 * - 超过 SCENE_CHAIN_MAX_HOURS / SCENE_CHAIN_MAX_ENTRIES 的状态条目移入 memory/raw_scene.md
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
     */
    suspend fun record(
        kb: KnowledgeBase,
        messages: List<ChatMessage>,
        scheme: Scheme?,
        topicStatus: String,
        topicLabel: String,
        sceneFacts: List<String> = emptyList(),
        userHint: String = "",
        ongoing: List<OngoingItem> = emptyList(),
        likedSchemes: List<Scheme> = emptyList()
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
        // P0-1：候选回复不再自动当作实际发送消息。
        // scheme=null 表示本轮没有确认发送任何候选；
        // likedSchemes 记录用户偏好（点赞），但不写入"实际对话"段。
        // IDEA（想法）不写入 recent.md——IDEA 是本轮控制信息，不是真实聊天。
        val conversationalMessages = messages.filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
        val entry = buildString {
            append("- [").append(time).append("]\n")
            conversationalMessages.forEach { msg ->
                append(msg.role.label).append("：").append(msg.content).append("\n")
            }
            if (userHint.isNotBlank()) {
                append("我的想法：").append(userHint.trim()).append("\n")
            }
            if (scheme != null) {
                val schemeLabel = if (scheme.tag.contains("+")) {
                    scheme.title
                } else {
                    "方案${scheme.tag}-${scheme.title}"
                }
                append("我（最终回复：").append(schemeLabel).append("）：").append(scheme.reply).append("\n")
            }
            if (likedSchemes.isNotEmpty() && scheme == null) {
                val likedTags = likedSchemes.joinToString(",") { it.tag }
                append("（用户偏好：方案$likedTags，未确认发送）\n")
            }
        }

        // 3. 写入 moment/recent.md
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

    /** 职责2（自 record 拆出）：写入 moment/recent.md，保留最近 N 轮，溢出→对话暂存
     * P0-4：未识别内容（不符合时间戳格式的块）原样保留，不当垃圾丢弃。 */
    private suspend fun writeRecent(kbName: String, entry: String) {
        val recentPath = "moment/recent.md"
        val existing = knowledgeRepo.readFile(kbName, recentPath)
        if (existing.isNotBlank()) {
            val allParts = existing.split(Regex("(?=^- \\[)", RegexOption.MULTILINE)).map { it.trim() }
            val validBlocks = allParts.filter { validRoundBlock(it) }
            // P0-4：未识别内容原样保留——拼回文件头部，不被当作轮次计数或溢出处理
            val unrecognized = allParts.filter { !validRoundBlock(it) && it.isNotBlank() }

            val kept = validBlocks.takeLast(maxTopicTurns - 1)
            val overflow = validBlocks.dropLast(maxTopicTurns - 1)
            if (overflow.isNotEmpty()) {
                knowledgeRepo.appendFile(kbName, "memory/raw_chat.md", "\n" + overflow.joinToString("\n\n") + "\n")
            }
            val newRecent = buildString {
                // P0-4：未识别内容拼回头部
                if (unrecognized.isNotEmpty()) {
                    unrecognized.forEach { append(it).append("\n\n") }
                }
                kept.forEach { append(it).append("\n\n") }
                append(entry).append("\n")
            }
            knowledgeRepo.writeFile(kbName, recentPath, newRecent)
        } else {
            knowledgeRepo.writeFile(kbName, recentPath, entry + "\n")
        }
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

    // ════════════════════════════════════════════════════════════════
    // P0 scene 语义重写：基于 fact-level identity 的状态机
    // ════════════════════════════════════════════════════════════════

    /** 场景中一条事实的结构化视图 */
    private data class SceneFact(
        val identity: String,    // 主体+事件类型+必要区分信息，如 "她|健康|感冒"
        val text: String,        // 原始事实文本
        val timestamp: String,   // 原始时间戳 "yyyy-MM-dd HH:mm"
        val tsMillis: Long       // 时间戳毫秒
    )

    /** 场景条目解析后的事实列表 */
    private data class SceneEntry(
        val timestamp: String,
        val tsMillis: Long,
        val label: String,
        val facts: List<String>  // 原始事实文本列表
    )

    /**
     * P0 修复版：更新场景链。
     *
     * 核心原则：
     * 1. 先过滤已超时的旧 facts（防过期事实复活）
     * 2. 每个旧 fact 保留自己的原 timestamp（不复制到新时间戳）
     * 3. 新 fact 找到相同 identity 时只替换那一个 fact，其他 fact 留在原 entry、原时间
     * 4. 同一轮出现两个同 identity facts 时 last-wins
     * 5. identity 至少包含 subject，不能让"她感冒"和"我感冒"同 key
     * 6. 新事实才使用 now 作为时间戳
     */
    private suspend fun updateSceneChain(kbName: String, topicLabel: String, sceneFacts: String) {
        val chainPath = "moment/scene.md"
        val historyPath = "memory/raw_scene.md"
        val now = System.currentTimeMillis()
        val timeStr = com.lovebrain.app.util.TimeFmt.now()
        val maxAgeMs = AppConfig.SCENE_CHAIN_MAX_HOURS * 3600_000L

        // 拆分本轮事实为列表
        val newFactTexts = sceneFacts.split('；', ';').map { it.trim() }.filter { it.isNotBlank() }
        if (newFactTexts.isEmpty()) return

        // 构建本轮新事实列表（同 identity last-wins）
        val newFactsByKey = LinkedHashMap<String, String>()
        for (nf in newFactTexts) {
            val key = extractFactIdentity(nf)
            // last-wins：同 identity 后者覆盖前者
            newFactsByKey[key] = nf
        }
        val newIdentities = newFactsByKey.keys

        // 读取并解析现有链
        val existing = knowledgeRepo.readFile(kbName, chainPath)
        val existingEntries = parseSceneEntries(existing)

        // P0-2：先过滤已超时的旧 facts（在处理新事实之前）
        // 过期 facts 不参与 upsert，直接归档，防止被复制进新条目复活
        val freshEntries = mutableListOf<SceneEntry>()
        val expiredEntries = mutableListOf<SceneEntry>()
        for (entry in existingEntries) {
            if (entry.tsMillis > 0 && (now - entry.tsMillis) > maxAgeMs) {
                expiredEntries.add(entry)
            } else {
                freshEntries.add(entry)
            }
        }

        // 收集所有未过期旧 facts（保留各自原 timestamp）
        // 格式：identity → SceneFact
        val oldFactsByKey = LinkedHashMap<String, SceneFact>()
        for (entry in freshEntries) {
            for (factText in entry.facts) {
                val identity = extractFactIdentity(factText)
                // 同一 entry 内同 identity 的 fact 也 last-wins
                oldFactsByKey[identity] = SceneFact(identity, factText, entry.timestamp, entry.tsMillis)
            }
        }

        // P0-1：执行 fact-level upsert
        // 新事实替换同 identity 的旧事实（supersede）
        // 未被覆盖的旧事实保留原 timestamp
        val supersededIdentities = mutableSetOf<String>()
        for ((identity, _) in newFactsByKey) {
            if (oldFactsByKey.containsKey(identity)) {
                supersededIdentities.add(identity)
            }
        }

        // 构建 fact → timestamp 映射（最终结果集）
        // 新事实用 now，旧事实用各自原 timestamp
        data class FinalFact(val text: String, val timestamp: String, val tsMillis: Long)
        val finalFacts = mutableListOf<FinalFact>()

        // 新事实 → now
        for ((_, factText) in newFactsByKey) {
            finalFacts.add(FinalFact(factText, timeStr, now))
        }

        // 未被替换的旧事实 → 保留原 timestamp
        for ((identity, fact) in oldFactsByKey) {
            if (identity !in supersededIdentities && identity !in newIdentities) {
                finalFacts.add(FinalFact(fact.text, fact.timestamp, fact.tsMillis))
            }
        }

        // 按时间戳分组为 entries（同时间戳的 facts 组成同一个 entry）
        val entriesByTime = LinkedHashMap<String, MutableList<FinalFact>>()
        for (f in finalFacts) {
            entriesByTime.getOrPut(f.timestamp) { mutableListOf() }.add(f)
        }

        // 按时间倒序排列（最新在前）
        val sortedTimeKeys = entriesByTime.keys.sortedByDescending { entriesByTime[it]!!.firstOrNull()?.tsMillis ?: 0L }

        // 构建 entry 行
        val candidate = mutableListOf<String>()
        for (ts in sortedTimeKeys) {
            val factsAtTime = entriesByTime[ts]!!
            if (factsAtTime.isEmpty()) continue
            // 从原 entry 中找 label，找不到就用 topicLabel 或"日常"
            val label = findLabelForTimestamp(freshEntries, ts) ?: topicLabel.ifBlank { "日常" }
            val factsStr = factsAtTime.joinToString("；") { it.text }
            candidate.add("- [$ts] $label：$factsStr")
        }

        // 条数上限：超出的最老条目归档
        val maxEntries = AppConfig.SCENE_CHAIN_MAX_ENTRIES
        val keptEntries = candidate.take(maxEntries)
        if (candidate.size > maxEntries) {
            expiredEntries.addAll(candidate.drop(maxEntries).map { it })
        }

        // 写入 scene.md
        val newChainContent = keptEntries.joinToString("\n") + "\n"
        val oldChainContent = existingEntries.joinToString("\n") { "- [${it.timestamp}] ${it.label}：${it.facts.joinToString("；")}" } + "\n"
        if (newChainContent != oldChainContent) {
            knowledgeRepo.writeFile(kbName, chainPath, newChainContent)
        }

        // 归档条目追加到 history
        if (expiredEntries.isNotEmpty()) {
            val historyContent = buildString {
                expiredEntries.forEach { e ->
                    if (e is SceneEntry) {
                        append("- [${e.timestamp}] ${e.label}：${e.facts.joinToString("；")}\n")
                    } else {
                        append(e.toString()).append("\n")
                    }
                }
            }
            knowledgeRepo.appendFile(kbName, historyPath, historyContent)
        }
    }

    /** 从 scene.md 原文解析出 SceneEntry 列表 */
    private fun parseSceneEntries(content: String): List<SceneEntry> {
        if (content.isBlank()) return emptyList()
        val entries = mutableListOf<SceneEntry>()
        val entryRegex = Regex("^- \\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\]\\s*(.*)$")
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("- [")) continue
            val match = entryRegex.find(trimmed) ?: continue
            val ts = match.groupValues[1]
            val tsMillis = com.lovebrain.app.util.TimeFmt.parse(ts)
            val rest = match.groupValues[2]
            val colonIdx = rest.indexOf('：')
            val label = if (colonIdx >= 0) rest.substring(0, colonIdx).trim() else rest.trim()
            val factsRaw = if (colonIdx >= 0) rest.substring(colonIdx + 1) else ""
            val facts = factsRaw.split('；', ';').map { it.trim() }.filter { it.isNotBlank() }
            if (facts.isNotEmpty()) {
                entries.add(SceneEntry(ts, tsMillis, label, facts))
            }
        }
        return entries
    }

    /** 在 freshEntries 中查找对应时间戳的 label */
    private fun findLabelForTimestamp(entries: List<SceneEntry>, timestamp: String): String? {
        return entries.firstOrNull { it.timestamp == timestamp }?.label
    }

    /**
     * P0 修复版：从事实文本中提取 identity。
     *
     * identity = 主体 + 事件类型 + 必要区分信息
     *
     * 关键修复：
     * - 不再 removePrefix("她"/"我") 删身份——必须保留 subject
     * - "她感冒了" → "她|健康|感冒"
     * - "我感冒了" → "我|健康|感冒"
     * - "她周一有期末考试" → "她|考试|期末"
     * - "她周五有英语考试" → "她|考试|英语"
     */
    private fun extractFactIdentity(fact: String): String {
        val f = fact.trim()

        // 提取主体（她/我），默认"她"
        val subject = when {
            f.startsWith("她") -> "她"
            f.startsWith("我") -> "我"
            else -> "她" // 无明确主语时默认"她"
        }

        // 提取主体后的内容
        val body = f.removePrefix("她").removePrefix("我").trim()

        // 事件类型 + 区分信息
        val category: String
        val distinguishing: String

        // 健康类
        val healthKeywords = listOf(
            "感冒" to "感冒", "发烧" to "发烧", "咳嗽" to "咳嗽", "过敏" to "过敏",
            "头疼" to "头疼", "肚子疼" to "肚子疼", "胃疼" to "胃疼",
            "生理期" to "生理期", "大姨妈" to "生理期", "生病" to "生病",
            "不舒服" to "不舒服", "拉肚子" to "拉肚子", "牙疼" to "牙疼",
            "腰疼" to "腰疼", "嗓子疼" to "嗓子疼"
        )
        // 事件类——需要区分不同实例
        val eventKeywords = listOf(
            "加班" to "加班", "出差" to "出差", "搬家" to "搬家",
            "面试" to "面试", "健身" to "健身", "跑步" to "跑步",
            "开会" to "开会", "赶项目" to "赶项目", "答辩" to "答辩",
            "述职" to "述职", "团建" to "团建", "旅游" to "旅游", "旅行" to "旅行"
        )
        // 状态类
        val stateKeywords = listOf(
            "睡了" to "睡了", "起床" to "起床", "洗澡" to "洗澡",
            "化妆" to "化妆", "做饭" to "做饭", "吃饭" to "吃饭",
            "回家" to "回家", "到公司" to "到公司", "下班" to "下班",
            "上班" to "上班", "出发" to "出发", "到家" to "到家"
        )

        // 考试需要更细粒度区分
        val examMatch = Regex("(期末|期中|英语|数学|高数|物理|化学|专业课|选修课|考试)").find(body)
        if (examMatch != null) {
            val examType = examMatch.value
            // 如果能找到具体考试名称就用它，否则用"考试"
            val specificExam = listOf("期末", "期中", "英语", "数学", "高数", "物理", "化学")
                .firstOrNull { body.contains(it) }
            category = "考试"
            distinguishing = specificExam ?: examType
            return "$subject|$category|$distinguishing"
        }

        // 聚餐需要区分
        if (body.contains("聚餐")) {
            category = "聚餐"
            distinguishing = if (body.contains("公司") || body.contains("团建")) "公司" 
                else if (body.contains("家庭") || body.contains("周末")) "家庭"
                else "一般"
            return "$subject|$category|$distinguishing"
        }

        // 约会需要区分
        if (body.contains("约会") || body.contains("见面") || body.contains("约")) {
            category = "约会"
            distinguishing = if (body.contains("周末")) "周末" else "一般"
            return "$subject|$category|$distinguishing"
        }

        // 通用健康类匹配
        for ((kw, label) in healthKeywords) {
            if (body.contains(kw)) {
                category = "健康"
                distinguishing = label
                return "$subject|$category|$distinguishing"
            }
        }

        // 通用事件类匹配
        for ((kw, label) in eventKeywords) {
            if (body.contains(kw)) {
                category = "事件"
                distinguishing = label
                return "$subject|$category|$distinguishing"
            }
        }

        // 通用状态类匹配
        for ((kw, label) in stateKeywords) {
            if (body.contains(kw)) {
                category = "状态"
                distinguishing = label
                return "$subject|$category|$distinguishing"
            }
        }

        // 兜底：取前 8 字作为区分信息，保留 subject
        return "$subject|其他|${body.take(8)}"
    }

    /** 从条目中解析时间戳："- [2026-07-24 19:32] ..." → epoch millis */
    private fun parseEntryTime(entry: String): Long {
        val match = Regex("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]").find(entry) ?: return 0
        return com.lovebrain.app.util.TimeFmt.parse(match.groupValues[1])
    }

    /**
     * 合并 ongoing 到 moment/plan.md（双二级标题重构）。
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
