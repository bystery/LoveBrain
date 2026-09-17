package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.OngoingItem
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SceneFact

/**
 * 话题生命周期管理器 v6（F03 重构）。
 *
 * 核心逻辑：
 * - 每轮对话记录到 moment/recent.md（最近 2 轮，溢出→对话暂存）
 * - 场景事实写入 moment/scene.md（带时间戳的状态链）
 * - 超过 TTL/最大条数 的状态条目移入 memory/raw_scene.md
 * - 话题切换判定：仅凭 topic_status=new
 * - 话题切换时归档到 memory/raw_topic.md（状态倒序），重置此刻层
 * - ongoing 进行中事项合并写入 moment/plan.md（跨话题生存）
 * - 轮次/状态条目解析使用真实时间戳校验，防 schema 模板示例行混入
 *
 * F03 变更：
 * - 废弃 extractTopicKey 中文关键词列表匹配
 * - 每条 scene fact 携带 sourceIds，引用本轮 HER/ME 消息 ID
 * - 客户端逐条校验 sourceId 必须属于冻结快照中的 HER/ME；非法来源只拒绝该事实
 * - 模型重述旧事实不更新时间；只有新来源的真实证据才更新
 * - "她"和"我"不合并：通过来源消息的 role 区分
 * - 相同来源重复提交幂等
 * - 无法确定同一事项时保守不覆盖
 */
class TopicRecorder(private val knowledgeRepo: KnowledgeRepository) {

    private val maxTopicTurns = AppConfig.MAX_TOPIC_TURNS

    /** 轮次块首行校验："- [yyyy-MM-dd HH:mm]" 真实时间戳（排除模板示例 "- [yyyy-MM-dd HH:mm]"） */
    private val roundTsRegex = Regex("^- \\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]")

    /**
     * 记录一轮对话 + 处理话题状态 + 更新场景链 + 合并进行中事项。
     *
     * F03: sceneFacts 参数改为 List<SceneFact>（带来源 ID），
     * 同时传入冻结的 messages 快照用于来源校验。
     *
     * @param topicStatus 从 AI 回复 JSON 中提取的话题状态（same/drift/new）
     * @param topicLabel 从 AI 回复 JSON 中提取的话题标签
     * @param sceneFacts 从 AI 回复 JSON 中提取的场景关键事实（带来源 ID）
     * @param ongoing 从 AI 回复 JSON 中提取的进行中事项变化
     * @return true 如果话题发生了切换（用于触发知识库更新）
     */
    suspend fun record(
        kb: KnowledgeBase,
        messages: List<ChatMessage>,
        scheme: Scheme?,
        topicStatus: String,
        topicLabel: String,
        sceneFacts: List<SceneFact> = emptyList(),
        userHint: String = "",
        ongoing: List<OngoingItem> = emptyList(),
        likedSchemes: List<Scheme> = emptyList(),
        sourceAliasMap: Map<String, String> = emptyMap() // B项修复：别名→实际消息ID映射
    ): Boolean {
        val time = com.lovebrain.app.util.TimeFmt.now()
        var topicRotated = false

        // R02: 幂等保护——用消息 ID 集合检查本轮是否已写入 recent.md
        // 替代旧的正文字串子串匹配（dedupKey.take(200) contains），避免相似内容误判
        val conversationalMsgs = messages.filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
        val roundMsgIds = conversationalMsgs.map { it.id }.sorted().joinToString(",")
        val alreadyRecorded = if (roundMsgIds.isNotBlank()) {
            val existingRecent = knowledgeRepo.readFile(kb.name, "moment/recent.md")
            existingRecent.contains("<!-- round:msgIds:$roundMsgIds -->")
        } else false

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
        // P0-FIX：IDEA（想法）不写入 recent.md——IDEA 是本轮控制信息，不是真实聊天。
        // 只有 HER/ME 写入 moment/recent.md，IDEA 已在 buildReplyUserPrompt 中独立注入一次。
        val conversationalMessages = messages.filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
        val entry = buildString {
            append("- [").append(time).append("]\n")
            // R02: 写入幂等标记——消息 ID 集合，供下轮检查
            append("<!-- round:msgIds:").append(roundMsgIds).append(" -->\n")
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
                // F01: 点赞学习资料必须保存完整候选正文+风格标识，
                // 不能只存 tag 标签——否则下一轮无法知道用户喜欢了什么表达。
                append("（用户偏好，未确认发送：\n")
                likedSchemes.forEach { s ->
                    val label = if (s.tag.contains("+")) s.title else "方案${s.tag}-${s.title}"
                    append("  $label：${s.reply}\n")
                }
                append("）\n")
            }
        }

        // 3. 写入 moment/recent.md（职责拆出，见 writeRecent）
        // R02: 幂等——消息已记录时跳过 recent.md 重写和 turn count
        if (!alreadyRecorded) {
            writeRecent(kb.name, entry)
        }

        // 4. F03: 更新场景链——传入 SceneFact 列表和冻结消息快照做来源校验
        // B项修复：传入别名映射，将 her-0/me-1 转为实际消息 ID 后再校验
        if (sceneFacts.isNotEmpty()) {
            updateSceneChain(kb.name, topicLabel, sceneFacts, messages, sourceAliasMap)
        }

        // 5. 合并进行中事项（plan.md，跨话题生存）
        mergeOngoing(kb.name, ongoing, time)

        if (!alreadyRecorded) {
            knowledgeRepo.incrementTurnCount(kb.name)
        }
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
    // F03: 场景事实内部数据结构
    // ════════════════════════════════════════════════════════════════

    /** scene.md 中解析出的单条事实（含来源和时间戳） */
    private data class StoredFact(
        val text: String,           // 事实文本
        val sourceIds: List<String>, // 来源消息 ID（可能为空=旧数据未核实）
        val evidenceTime: Long,      // 证据时间（写入时的真实时间，模型重述不刷新）
        val subject: String          // P1-05/5.5: 事实主体（被描述的人），不等同于说话人。空=待解析/不确定
    )

    /** scene.md 中解析出的条目行 */
    private data class SceneEntry(
        val timeStr: String,         // 时间戳字符串
        val timeMs: Long,            // 时间戳毫秒
        val label: String,           // 话题标签
        val facts: List<StoredFact>  // 该行包含的事实
    )

    /**
     * F03: 更新场景链——基于来源 ID 的事实匹配和状态变化。
     *
     * 核心规则：
     * 1. 来源校验：新事实的 sourceIds 必须属于本轮冻结快照中的 HER/ME 消息。
     *    - 非法来源（IDEA/候选/不存在的 ID）→ 拒绝该事实，不影响合法事实
     *    - 无来源（旧格式/模型未提供）→ 标记为未核实，保留但不覆盖已有事实
     * 2. 时间不刷新：模型重述旧事实不更新 evidenceTime。
     *    只有来源 ID 与已有事实完全匹配且文本变化时才更新（视为同一事项的新状态）。
     * 3. "她"和"我"不合并：通过来源消息的 role 区分主语。
     * 4. 幂等：相同来源 + 相同文本 → 不重复写入。
     * 5. 保守不覆盖：无法确定是否同一事项时，追加而不覆盖。
     */
    private suspend fun updateSceneChain(
        kbName: String,
        topicLabel: String,
        sceneFacts: List<SceneFact>,
        frozenMessages: List<ChatMessage>,
        sourceAliasMap: Map<String, String> = emptyMap() // B项修复
    ) {
        val chainPath = "moment/scene.md"
        val historyPath = "memory/raw_scene.md"
        val now = System.currentTimeMillis()
        val timeStr = com.lovebrain.app.util.TimeFmt.now()

        // F03: 构建冻结快照中 HER/ME 消息的 ID→role 映射
        val validSourceMap: Map<String, ChatMessage.Role> = frozenMessages
            .filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
            .associate { it.id to it.role }

        // B项修复：将别名（her-0, me-1）转换为实际消息 ID 后再校验
        fun resolveSourceId(rawId: String): String {
            // 先查别名映射（her-0 → UUID），找不到则认为已经是实际 ID
            return sourceAliasMap[rawId] ?: rawId
        }

        // F03: 校验每条新事实的来源，过滤掉非法来源的事实
        val validatedFacts = mutableListOf<StoredFact>()
        for (sf in sceneFacts) {
            val text = sf.text.trim()
            if (text.isBlank()) continue

            if (sf.sourceIds.isEmpty()) {
                // 无来源（旧格式或模型未提供）→ 标记为未核实，保留但不覆盖已有事实
                validatedFacts.add(StoredFact(
                    text = text,
                    sourceIds = emptyList(),
                    evidenceTime = now,
                    subject = extractSubject(text)
                ))
                continue
            }

            // B项修复：先将别名转为实际消息 ID，再校验是否属于冻结快照中的 HER/ME
            val resolvedIds = sf.sourceIds.map { resolveSourceId(it) }
            val validIds = resolvedIds.filter { id -> id in validSourceMap }
            if (validIds.isEmpty()) {
                // 所有来源都不合法 → 拒绝该事实（不影响合法事实）
                com.lovebrain.app.util.L.w("SceneFact rejected (no valid source): $text")
                continue
            }

            // P1-05/5.5: 不再从来源消息的 role 强制推导 subject——
            // 说话人不等于事实主体。“她：你感冒好了吗？”说话人是她，但感冒的人可能是用户。
            // subject 留空（待解析/不确定），由后续实体解析或用户纠正决定。
            val subject = ""

            validatedFacts.add(StoredFact(
                text = text,
                sourceIds = validIds,
                evidenceTime = now,
                subject = subject
            ))
        }

        if (validatedFacts.isEmpty()) return

        // 读取现有链并解析为结构化条目
        val existing = knowledgeRepo.readFile(kbName, chainPath)
        val existingEntries = parseSceneEntries(existing)

        // F03: 收集所有已有事实
        val allExistingFacts = existingEntries.flatMap { entry ->
            entry.facts.map { fact -> fact to entry }
        }

        // F03/E项修复: 对每条新事实做匹配决策
        // - 如果 sourceIds 与已有事实完全相同且文本相同 → 幂等跳过
        // - 如果 sourceIds 与已有事实完全相同但文本不同 → 同一事项新状态，替换（E项修复：恢复替换）
        // - 如果 sourceIds 为空（未核实）→ 追加，不覆盖已有事实，不刷新时间
        // - 如果 sourceIds 不同 → 新事实，追加
        // - "她"和"我"的事实即使文本相似也不合并
        val toAdd = mutableListOf<StoredFact>()
        val toReplace = mutableMapOf<StoredFact, StoredFact>() // old → new

        for (nf in validatedFacts) {
            if (nf.sourceIds.isEmpty()) {
                // R05: 未核实来源 → 追加但不覆盖、不刷新时间
                // E项修复：无来源不标记为当前时间，保持旧时间或0
                toAdd.add(nf.copy(evidenceTime = 0L))
                continue
            }

            // E项修复：同来源的旧事实 → 检查是否需要更新
            // 同来源 + 不同文本 → 同一事项新状态，替换旧版本
            val sameSourceMatch = allExistingFacts.firstOrNull { (ef, _) ->
                ef.sourceIds.toSet() == nf.sourceIds.toSet() &&
                ef.subject == nf.subject
            }

            if (sameSourceMatch != null) {
                val (ef, _) = sameSourceMatch
                if (ef.text == nf.text) {
                    // 幂等——完全相同文本+来源 → 不重复写入
                    com.lovebrain.app.util.L.w("SceneFact idempotent skip: $nf")
                } else {
                    // E项修复：同来源新文本 → 替换旧版本（同事项新状态）
                    toReplace[ef] = nf.copy(evidenceTime = ef.evidenceTime)
                }
            } else {
                // 新事实，追加
                toAdd.add(nf)
            }
        }

        // 如果没有变化（全部幂等跳过），仍需处理过期归档
        if (toAdd.isEmpty() && toReplace.isEmpty()) {
            val maxAgeMs0 = AppConfig.SCENE_CHAIN_MAX_HOURS * 3600_000L
            val fresh0 = mutableListOf<SceneEntry>()
            val expired0 = mutableListOf<SceneEntry>()
            for (entry in existingEntries) {
                val isExpired = entry.timeMs > 0 && (now - entry.timeMs) > maxAgeMs0
                if (isExpired) expired0.add(entry) else fresh0.add(entry)
            }
            if (expired0.isNotEmpty()) {
                writeSceneAndArchive(kbName, chainPath, historyPath, fresh0, expired0)
            }
            return
        }

        // F03: 构建更新后的条目列表
        // 1. 对已有条目：执行替换（将旧事实替换为新版本），保留未涉及的事实
        // 2. 新事实追加为新条目
        val updatedEntries = existingEntries.map { entry ->
            val updatedFacts = entry.facts.map { fact ->
                toReplace[fact] ?: fact
            }.filter { fact ->
                // 保留未被替换的旧事实
                toReplace.keys.none { it === fact }
            } + toReplace.entries.filter { (_, nf) ->
                // 新版本事实回到原条目（同时间戳）
                entry.facts.any { it.sourceIds.isNotEmpty() && it.sourceIds.toSet() == nf.sourceIds.toSet() }
            }.map { it.value }

            // 去重：同一文本只保留一条
            val seen = mutableSetOf<String>()
            val dedupedFacts = updatedFacts.filter { fact ->
                val key = "${fact.subject}|${fact.text}|${fact.sourceIds.sorted()}"
                if (key in seen) false else { seen.add(key); true }
            }

            entry.copy(facts = dedupedFacts)
        }.filter { it.facts.isNotEmpty() }.toMutableList() // 移除空条目

        // 追加新事实为新条目
        if (toAdd.isNotEmpty()) {
            val newEntry = SceneEntry(
                timeStr = timeStr,
                timeMs = now,
                label = topicLabel.ifBlank { "日常" },
                facts = toAdd
            )
            updatedEntries.add(0, newEntry) // 新条目插入头部
        }

        // F03: 过期归档
        val maxAgeMs = AppConfig.SCENE_CHAIN_MAX_HOURS * 3600_000L
        val maxEntries = AppConfig.SCENE_CHAIN_MAX_ENTRIES
        val fresh = mutableListOf<SceneEntry>()
        val expired = mutableListOf<SceneEntry>()
        for (entry in updatedEntries) {
            val isExpired = entry.timeMs > 0 && (now - entry.timeMs) > maxAgeMs
            if (isExpired) {
                expired.add(entry)
            } else {
                fresh.add(entry)
            }
        }
        // 超出条数上限的最老条目归档
        val keptEntries = fresh.take(maxEntries)
        if (fresh.size > maxEntries) {
            expired.addAll(fresh.drop(maxEntries))
        }

        // 渲染并写入
        writeSceneAndArchive(kbName, chainPath, historyPath, keptEntries, expired)
    }

    /** F03: 将 SceneEntry 列表写入 scene.md，过期条目追加到 raw_scene.md */
    private suspend fun writeSceneAndArchive(
        kbName: String,
        chainPath: String,
        historyPath: String,
        kept: List<SceneEntry>,
        expired: List<SceneEntry>
    ) {
        // 渲染 scene.md
        val newChainContent = if (kept.isEmpty()) "" else kept.joinToString("\n") { entry ->
            val factsStr = entry.facts.joinToString("；") { f ->
                if (f.sourceIds.isNotEmpty()) {
                    "${f.text}⟨${f.sourceIds.joinToString(",")}⟩"
                } else {
                    f.text
                }
            }
            "- [${entry.timeStr}] ${entry.label}：${factsStr}"
        } + "\n"

        knowledgeRepo.writeFile(kbName, chainPath, newChainContent)

        // 归档过期条目
        if (expired.isNotEmpty()) {
            val historyContent = buildString {
                expired.forEach { entry ->
                    val factsStr = entry.facts.joinToString("；") { f ->
                        if (f.sourceIds.isNotEmpty()) {
                            "${f.text}⟨${f.sourceIds.joinToString(",")}⟩"
                        } else {
                            f.text
                        }
                    }
                    append("- [${entry.timeStr}] ${entry.label}：${factsStr}\n")
                }
            }
            knowledgeRepo.appendFile(kbName, historyPath, historyContent)
        }
    }

    /** F03: 解析 scene.md 为结构化条目列表 */
    private fun parseSceneEntries(content: String): MutableList<SceneEntry> {
        if (content.isBlank()) return mutableListOf()
        val entryRegex = Regex("^- \\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]\\s*(.*)$")
        val result = mutableListOf<SceneEntry>()

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("- [")) continue
            val match = entryRegex.find(trimmed) ?: continue

            val timeStr = match.groupValues[1]
            val timeMs = com.lovebrain.app.util.TimeFmt.parse(timeStr)
            val rest = match.groupValues[2]

            // 分割标签和事实
            val colonIdx = rest.indexOf('：')
            val (label, factsRaw) = if (colonIdx >= 0) {
                rest.substring(0, colonIdx).trim() to rest.substring(colonIdx + 1)
            } else {
                "" to rest
            }

            // 解析事实列表（支持 ⟨sourceIds⟩ 后缀）
            val facts = factsRaw.split('；', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { factText ->
                    // 检查是否有 ⟨sourceIds⟩ 后缀
                    val srcMatch = Regex("(.*)⟨(.+)⟩$").find(factText)
                    if (srcMatch != null) {
                        val text = srcMatch.groupValues[1].trim()
                        val srcIds = srcMatch.groupValues[2].split(',').map { it.trim() }.filter { it.isNotBlank() }
                        StoredFact(text = text, sourceIds = srcIds, evidenceTime = timeMs, subject = extractSubject(text))
                    } else {
                        // 旧格式：无来源标记
                        StoredFact(text = factText, sourceIds = emptyList(), evidenceTime = timeMs, subject = extractSubject(factText))
                    }
                }

            if (facts.isNotEmpty()) {
                result.add(SceneEntry(timeStr = timeStr, timeMs = timeMs, label = label, facts = facts))
            }
        }
        return result
    }

    /** F03: 从事实文本中提取主体标记
     * P1-05/5.5: 不再用首字“她/我”猜测事实主体——说话人不等于被描述的人。
     * 返回空字符串，由后续实体解析或用户纠正决定。 */
    private fun extractSubject(text: String): String {
        return ""
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
            append("当前话题：").append(topic as CharSequence).append("\n\n")
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
            append("当前话题：").append(topic as CharSequence).append("\n")
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
