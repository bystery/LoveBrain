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
        frozenMessages: List<ChatMessage>
    ) {
        val chainPath = "moment/scene.md"
        val historyPath = "memory/raw_scene.md"
        val now = clock.epochMs()
        val timeStr = clock.wallClock()

        // 构建冻结快照中 HER/ME 消息的 ID→role 映射
        val validSourceMap: Map<String, ChatMessage.Role> = frozenMessages
            .filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
            .associate { it.id to it.role }

        // 别名（her-0 → 真实消息 ID）由调用方 [TopicRecorder.record] 在进 WAL 之前解析完，
        // 首次提交与崩溃恢复用的是同一份 payload；这里不再收一份死参数。

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

            // 来源 ID 已由调用方解析成真实消息 ID；这里只做"是否属于冻结快照"的校验
            val validIds = sf.sourceIds.filter { id -> id in validSourceMap }
            if (validIds.isEmpty()) {
                // 所有来源都不合法 → 拒绝该事实（不影响合法事实）
                // 只记长度与来源数：被拒的正文是从聊天里抽出来的，按 util/L.kt 的隐私红线不许进日志
                com.lovebrain.app.util.L.w(
                    "SceneFact rejected (no valid source): textLen=${text.length} " +
                        "declaredSources=${sf.sourceIds.size}"
                )
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
        // - 同来源 + 同主体 + 同文本 → 幂等跳过（首次提交重复、或崩溃恢复重放都走这条）
        // - 同来源 + 同主体 + 不同文本 + 组内只有一条旧事实 → 同一事项新状态，替换
        // - 同来源但组内有多条旧事实（或已被本批认领）→ 追加，绝不覆盖已有事实
        //    （重放时同来源多条事实共用一组来源 ID，逐条替换会互相覆盖、只剩末条）
        // - 如果 sourceIds 为空（未核实）→ 追加，不覆盖已有事实，不刷新时间
        // - 如果 sourceIds 不同 → 新事实，追加
        // - "她"和"我"的事实即使文本相似也不合并
        val toAdd = mutableListOf<StoredFact>()
        val toReplace = mutableMapOf<StoredFact, StoredFact>() // old → new
        // 本批已认领（幂等命中或已用于替换）的旧事实：后续同来源事实不得再替换它们
        val claimed = mutableSetOf<StoredFact>()

        for (nf in validatedFacts) {
            if (nf.sourceIds.isEmpty()) {
            // 未核实来源 → 追加但不覆盖、不刷新时间
            // E项修复：无来源不标记为当前时间，保持旧时间或0
            toAdd.add(nf.copy(evidenceTime = 0L))
            continue
            }

            // 同来源 + 同主体的旧事实组
            val sameSourceGroup = allExistingFacts.filter { (ef, _) ->
                ef.sourceIds.toSet() == nf.sourceIds.toSet() &&
                ef.subject == nf.subject
            }

            // 幂等——组内已有完全相同文本的事实：第一次提交已写成，本次是重放
            val identical = sameSourceGroup.firstOrNull { (ef, _) -> ef.text == nf.text }
            if (identical != null) {
                claimed.add(identical.first)
                // 只记长度/来源数：正文是从聊天里抽出来的，按 util/L.kt 的隐私红线不许进日志
                com.lovebrain.app.util.L.w(
                    "SceneFact idempotent skip: textLen=${nf.text.length} sources=${nf.sourceIds.size}"
                )
                continue
            }

            // 组内只剩一条未被本批认领的旧事实 → 视为同一事项的新状态，就地替换
            val replaceable = sameSourceGroup.singleOrNull()?.first?.takeIf { it !in claimed }
            if (replaceable != null) {
                claimed.add(replaceable)
                toReplace[replaceable] = nf.copy(evidenceTime = replaceable.evidenceTime)
            } else {
                // 组内多条（或都被本批认领）→ 保守追加：宁可多一条，不许把旧事实覆盖丢
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
        val newChainContent = if (kept.isEmpty()) "" else kept.joinToString("\n") { renderEntry(it) } + "\n"

        knowledgeRepo.writeFile(kbName, chainPath, newChainContent)

        // 归档过期条目
        if (expired.isNotEmpty()) {
            val historyContent = buildString {
                expired.forEach { entry -> append(renderEntry(entry)).append("\n") }
            }
            knowledgeRepo.appendFile(kbName, historyPath, historyContent)
        }
    }

    /**
     * 条目行渲染：`- [时间] 标签：事实1；事实2`，每条事实再以 `|` 拼 `src=/spk=/subj=`。
     *
     * 事实正文里的 `|`、`；`、`;`、`：` 与换行是行内分隔符本身，原样写会把一条事实拆错、
     * 读回来丢条目——落盘前经 [SceneFactCodec] 编码成不含分隔符的 `\uXXXX` 序列，
     * 读侧 [parseStoredFact] 解码还原。不含这些字符的正文一个字节都不变（旧格式照样读）。
     */
    private fun renderEntry(entry: SceneEntry): String {
        val factsStr = entry.facts.joinToString("；") { f ->
            val parts = mutableListOf<String>()
            parts.add(SceneFactCodec.encode(f.text))
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
        return "- [${entry.timeStr}] ${entry.label}：${factsStr}"
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
     *
     * 三条路径的正文都过一遍 [SceneFactCodec.decode]：写入侧只把分隔符编成 `\uXXXX`，
     * 不含分隔符的正文编码前后逐字相同，所以旧数据解码是恒等变换。
     */
    private fun parseStoredFact(factText: String, timeMs: Long): StoredFact {
        // 优先检查新格式（| 分隔的字段）
        if (factText.contains("|src=") || factText.contains("|spk=") || factText.contains("|subj=")) {
            val parts = factText.split("|").map { it.trim() }
            val text = SceneFactCodec.decode(parts.firstOrNull()?.trim().orEmpty())
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
            val text = SceneFactCodec.decode(srcMatch.groupValues[1].trim())
            val srcIds = srcMatch.groupValues[2].split(',').map { it.trim() }.filter { it.isNotBlank() }
            return StoredFact(text = text, sourceIds = srcIds, evidenceTime = timeMs, subject = extractSubject(text))
        }

        // 无标记：纯文本
        val text = SceneFactCodec.decode(factText)
        return StoredFact(text = text, sourceIds = emptyList(), evidenceTime = timeMs, subject = extractSubject(text))
    }
}

/**
 * 场景事实正文的行内编解码。
 *
 * `moment/scene.md` 是单行条目 + 行内分隔符（`；`/`;` 分事实、`|` 分字段、首`：`分标签），
 * 正文里出现分隔符或换行时写入侧会被拆错、读回来丢条目。这里只做**最小转义**：
 * 命中 [RISKY] 里的字符才编码，正文原样时不产生任何转义（旧数据一字不动）。
 *
 * 编码形如 `\u007c`（反斜杠 + u + 4 位十六进制），自身不含任何分隔符，
 * 所以写入侧仍可按分隔符直接切分，切完再解码。
 */
internal object SceneFactCodec {

    /** 需要编码的字符：反斜杠自身、两种分号、竖线、全角冒号、换行/回车 */
    private val RISKY = charArrayOf('\\', '；', ';', '|', '：', '\n', '\r')
    private val ESCAPE_REGEX = Regex("\\\\u([0-9a-fA-F]{4})")

    fun encode(text: String): String {
        if (text.none { it in RISKY }) return text
        val sb = StringBuilder(text.length + 8)
        for (ch in text) {
            if (ch in RISKY) {
                sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * 解码。只认 `\u` + 4 位十六进制，其余反斜杠原样保留；
     * 旧数据（从未编码）里出现这种序列的概率可以忽略，这是"读取兼容旧格式"的代价。
     */
    fun decode(text: String): String {
        if (!text.contains("\\u")) return text
        return ESCAPE_REGEX.replace(text) { m ->
            val code = m.groupValues[1].toInt(16)
            if (code in 0..0xFFFF) code.toChar().toString() else m.value
        }
    }
}

