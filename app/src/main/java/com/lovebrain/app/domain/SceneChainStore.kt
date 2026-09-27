package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.EntityRef
import com.lovebrain.app.model.SceneFact

/**
 * `moment/scene.md`（以及它的溢出目的地 `memory/raw_scene.md`）的所有者——
 * "此刻状态链怎么读、怎么合、怎么写、什么时候过期归档"。
 *
 * 变化理由：这条链的全部规则都是**同一族**——落盘行的格式（新格式
 * `事实|src=…|spk=…|subj=…` 与旧格式 `事实⟨ids⟩`、纯文本），以及在这份格式之上
 * 的来源校验、主语归属、幂等、过期归档。它们一起变（加一个字段就要同时改渲染和解析），
 * 而 WAL 事务、recent 窗口、plan 事项那些都不跟着变。
 *
 * 从 [TopicRecorder] 拆出，判据与文案逐字保留；调用方只有一处：
 * [RoundCommitJournal] 的 scene 投影（首次提交与崩溃恢复走同一段代码）。
 */
class SceneChainStore(
    private val knowledgeRepo: KnowledgePort,
    /**
     * 场景条目的时间戳与"这条事实多久了"的差值都从端口来：
     * 时间不可控时，过期归档那条分支永远走不到测试里。
     */
    private val clock: Clock
) {

    // ════════════════════════════════════════════════════════════════
    // 场景事实内部数据结构
    // ════════════════════════════════════════════════════════════════

    /** scene.md 中解析出的单条事实（含来源和时间戳） */
    private data class StoredFact(
        val text: String,           // 事实文本
        val sourceIds: List<String>, // 来源消息 ID（可能为空=旧数据未核实）
        val evidenceTime: Long,      // 证据时间（写入时的真实时间，模型重述不刷新）
        val subject: EntityRef = EntityRef.UNKNOWN,  // 事实主体（被描述的人）
        val speaker: EntityRef = EntityRef.UNKNOWN   // 谁说的
    )

    /** scene.md 中解析出的条目行 */
    private data class SceneEntry(
        val timeStr: String,         // 时间戳字符串
        val timeMs: Long,            // 时间戳毫秒
        val label: String,           // 话题标签
        val facts: List<StoredFact>  // 该行包含的事实
    )

    /**
     * 更新场景链——基于来源 ID 的事实匹配和状态变化。
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
    internal suspend fun updateSceneChain(
        kbName: String,
        topicLabel: String,
        sceneFacts: List<SceneFact>,
        frozenMessages: List<ChatMessage>,
        sourceAliasMap: Map<String, String> = emptyMap() // B项修复
    ) {
        val chainPath = "moment/scene.md"
        val historyPath = "memory/raw_scene.md"
        val now = clock.epochMs()
        val timeStr = clock.wallClock()

        // 构建冻结快照中 HER/ME 消息的 ID→role 映射
        val validSourceMap: Map<String, ChatMessage.Role> = frozenMessages
            .filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
            .associate { it.id to it.role }

        // B项修复：将别名（her-0, me-1）转换为实际消息 ID 后再校验
        fun resolveSourceId(rawId: String): String {
            // 先查别名映射（her-0 → UUID），找不到则认为已经是实际 ID
            return sourceAliasMap[rawId] ?: rawId
        }

        // 校验每条新事实的来源，过滤掉非法来源的事实
        val validatedFacts = mutableListOf<StoredFact>()
        for (sf in sceneFacts) {
            val text = sf.text.trim()
            if (text.isBlank()) continue

            if (sf.sourceIds.isEmpty()) {
                // 无来源（旧格式或模型未提供）→ 标记为未核实，保留但不覆盖已有事实
                // 使用 SceneFact 携带的 speaker/subject，不自行推导
                validatedFacts.add(StoredFact(
                    text = text,
                    sourceIds = emptyList(),
                    evidenceTime = now,
                    subject = sf.subject,
                    speaker = sf.speaker
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

            // speaker 由代码从 source_ids 确定性推导——绝不交给 AI
            val speaker = FactSpeakerResolver.resolveFromRoles(validIds, validSourceMap)

            // subject 与 speaker 分离——三层解析
            // Level 1: 代码可确定（代词+speaker）
            // Level 2: 实体规则
            // subject_candidate 已从 format.md 移除，不再传入 resolver
            val subject = FactSubjectResolver.resolve(
                factText = text,
                speaker = speaker,
                sourceIds = validIds,
                dialogue = frozenMessages
                    .filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
                    .map { com.lovebrain.app.model.DialogueMessage(
                        id = it.id,
                        speaker = when (it.role) {
                            ChatMessage.Role.HER -> com.lovebrain.app.model.DialogueSpeaker.PARTNER
                            ChatMessage.Role.ME -> com.lovebrain.app.model.DialogueSpeaker.USER
                            else -> com.lovebrain.app.model.DialogueSpeaker.USER
                        },
                        text = it.content
                    )}
            )

            validatedFacts.add(StoredFact(
                text = text,
                sourceIds = validIds,
                evidenceTime = now,
                subject = subject,
                speaker = speaker
            ))
        }

        if (validatedFacts.isEmpty()) return

        // 读取现有链并解析为结构化条目
        val existing = knowledgeRepo.readFile(kbName, chainPath)
        val existingEntries = parseSceneEntries(existing)

        // 收集所有已有事实
        val allExistingFacts = existingEntries.flatMap { entry ->
            entry.facts.map { fact -> fact to entry }
        }

        // /E项修复: 对每条新事实做匹配决策
        // - 如果 sourceIds 与已有事实完全相同且文本相同 → 幂等跳过
        // - 如果 sourceIds 与已有事实完全相同但文本不同 → 同一事项新状态，替换（E项修复：恢复替换）
        // - 如果 sourceIds 为空（未核实）→ 追加，不覆盖已有事实，不刷新时间
        // - 如果 sourceIds 不同 → 新事实，追加
        // - "她"和"我"的事实即使文本相似也不合并
        val toAdd = mutableListOf<StoredFact>()
        val toReplace = mutableMapOf<StoredFact, StoredFact>() // old → new

        for (nf in validatedFacts) {
            if (nf.sourceIds.isEmpty()) {
            // 未核实来源 → 追加但不覆盖、不刷新时间
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

        // 构建更新后的条目列表
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

        // 过期归档
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

    /** 将 SceneEntry 列表写入 scene.md，过期条目追加到 raw_scene.md
     * 事实现在持久化 speaker/subject，不再在下一次读盘时丢失。 */
    private suspend fun writeSceneAndArchive(
        kbName: String,
        chainPath: String,
        historyPath: String,
        kept: List<SceneEntry>,
        expired: List<SceneEntry>
    ) {
        // 渲染 scene.md——事实带 speaker/subject 持久化
        val newChainContent = if (kept.isEmpty()) "" else kept.joinToString("\n") { entry ->
            val factsStr = entry.facts.joinToString("；") { f ->
                val parts = mutableListOf<String>()
                parts.add(f.text)
                if (f.sourceIds.isNotEmpty()) {
                    parts.add("src=${f.sourceIds.joinToString(",")}")
                }
                // 持久化 speaker/subject
                if (f.speaker != EntityRef.UNKNOWN) {
                    parts.add("spk=${f.speaker.name}")
                }
                if (f.subject != EntityRef.UNKNOWN) {
                    parts.add("subj=${f.subject.name}")
                }
                parts.joinToString("|")
            }
            "- [${entry.timeStr}] ${entry.label}：${factsStr}"
        } + "\n"

        knowledgeRepo.writeFile(kbName, chainPath, newChainContent)

        // 归档过期条目
        if (expired.isNotEmpty()) {
            val historyContent = buildString {
                expired.forEach { entry ->
                    val factsStr = entry.facts.joinToString("；") { f ->
                        val parts = mutableListOf<String>()
                        parts.add(f.text)
                        if (f.sourceIds.isNotEmpty()) {
                            parts.add("src=${f.sourceIds.joinToString(",")}")
                        }
                        if (f.speaker != EntityRef.UNKNOWN) {
                            parts.add("spk=${f.speaker.name}")
                        }
                        if (f.subject != EntityRef.UNKNOWN) {
                            parts.add("subj=${f.subject.name}")
                        }
                        parts.joinToString("|")
                    }
                    append("- [${entry.timeStr}] ${entry.label}：${factsStr}\n")
                }
            }
            knowledgeRepo.appendFile(kbName, historyPath, historyContent)
        }
    }

    /** 解析 scene.md 为结构化条目列表 */
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

            // 解析事实列表（支持两种格式）
            // 旧格式：事实文本⟨sourceIds⟩
            // 新格式：事实文本|src=id1,id2|spk=HER|subj=ME
            val facts = factsRaw.split('；', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { factText ->
                    parseStoredFact(factText, timeMs)
                }

            if (facts.isNotEmpty()) {
                result.add(SceneEntry(timeStr = timeStr, timeMs = timeMs, label = label, facts = facts))
            }
        }
        return result
    }

    /** 从事实文本中提取主体——使用 FactSubjectResolver 三层解析。
     * 旧版 extractSubject 永远返回 UNKNOWN，现在改为实际解析。 */
    private fun extractSubject(text: String): EntityRef {
        // 旧数据从 scene.md 读取时无 speaker 上下文，只能用 Level 2 实体规则
        // 如果无法确定，仍返回 UNKNOWN
        return FactSubjectResolver.resolve(
            factText = text,
            speaker = EntityRef.UNKNOWN,
            sourceIds = emptyList(),
            dialogue = emptyList()
        )
    }

    /**
     * 解析单条事实文本为 StoredFact，兼容新旧两种格式。
     *
     * 旧格式：事实文本⟨sourceIds⟩
     * 新格式：事实文本|src=id1,id2|spk=HER|subj=ME
     * 无标记：纯文本（旧数据或无来源）
     */
    private fun parseStoredFact(factText: String, timeMs: Long): StoredFact {
        // 优先检查新格式（| 分隔的字段）
        if (factText.contains("|src=") || factText.contains("|spk=") || factText.contains("|subj=")) {
            val parts = factText.split("|").map { it.trim() }
            val text = parts.firstOrNull()?.trim().orEmpty()
            val srcIds = parts.firstOrNull { it.startsWith("src=") }
                ?.removePrefix("src=")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                ?: emptyList()
            val speaker = parts.firstOrNull { it.startsWith("spk=") }
                ?.removePrefix("spk=")?.let { runCatching { EntityRef.valueOf(it) }.getOrNull() }
                ?: EntityRef.UNKNOWN
            val subject = parts.firstOrNull { it.startsWith("subj=") }
                ?.removePrefix("subj=")?.let { runCatching { EntityRef.valueOf(it) }.getOrNull() }
                ?: extractSubject(text)
            return StoredFact(text = text, sourceIds = srcIds, evidenceTime = timeMs, subject = subject, speaker = speaker)
        }

        // 旧格式：⟨sourceIds⟩ 后缀
        val srcMatch = Regex("(.*)⟨(.+)⟩$").find(factText)
        if (srcMatch != null) {
            val text = srcMatch.groupValues[1].trim()
            val srcIds = srcMatch.groupValues[2].split(',').map { it.trim() }.filter { it.isNotBlank() }
            return StoredFact(text = text, sourceIds = srcIds, evidenceTime = timeMs, subject = extractSubject(text))
        }

        // 无标记：纯文本
        return StoredFact(text = factText, sourceIds = emptyList(), evidenceTime = timeMs, subject = extractSubject(factText))
    }
}
