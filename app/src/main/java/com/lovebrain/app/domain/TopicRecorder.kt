package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.EntityRef
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.OngoingItem
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SceneFact
import com.lovebrain.app.util.L
import com.lovebrain.app.domain.FactSpeakerResolver
import com.lovebrain.app.domain.FactSubjectResolver

/**
 * 话题生命周期管理器。
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
 * 设计要点：
 * - 废弃中文关键词列表匹配
 * - 每条 scene fact 携带 sourceIds，引用本轮 HER/ME 消息 ID
 * - 客户端逐条校验 sourceId 必须属于冻结快照中的 HER/ME；非法来源只拒绝该事实
 * - 模型重述旧事实不更新时间；只有新来源的真实证据才更新
 * - "她"和"我"不合并：通过来源消息的 role 区分
 * - 相同来源重复提交幂等
 * - 无法确定同一事项时保守不覆盖
 */
class TopicRecorder(
    private val knowledgeRepo: KnowledgeRepository,
    roundCommitJournal: RoundCommitJournal? = null
) {

    /**
     * WAL 始终启用。未注入时用同一实现自建，避免出现
     * "有 journal 走事务 / 无 journal 裸写" 两套写入路径。
     */
    private val journal: RoundCommitJournal = roundCommitJournal ?: RoundCommitJournal(knowledgeRepo)

    private val maxTopicTurns = AppConfig.MAX_TOPIC_TURNS

    /** 轮次块首行校验："- [yyyy-MM-dd HH:mm]" 真实时间戳（排除模板示例 "- [yyyy-MM-dd HH:mm]"） */
    private val roundTsRegex = Regex("^- \\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]")

    /**
     * 记录一轮对话 + 处理话题状态 + 更新场景链 + 合并进行中事项。
     *
     * F03: sceneFacts 参数为带来源 ID 的 [SceneFact]，
     * 同时传入冻结的 messages 快照用于来源校验。
     *
     * S2-04: 六个写入边界（话题归档、话题标签、recent、scene、plan、轮次计数）
     * 全部包在一把 journal 事务锁里，完整事件先落 WAL 再动 target。
     * 幂等判据是 [RoundCommitJournal.isRoundCommitted] + 每投影水位，
     * 不再拿 recent.md 的 HTML marker 当跨文件提交标记。
     *
     * @param topicStatus 从 AI 回复 JSON 中提取的话题状态（same/drift/new）
     * @param topicLabel 从 AI 回复 JSON 中提取的话题标签
     * @param sceneFacts 从 AI 回复 JSON 中提取的场景关键事实（带来源 ID）
     * @param ongoing 从 AI 回复 JSON 中提取的进行中事项变化
     * @param sourceAliasMap her-0/me-1 之类别名→真实消息 ID；在进 WAL 之前解析，
     *                       使首提与崩溃恢复使用同一份 payload
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
        sourceAliasMap: Map<String, String> = emptyMap()
    ): Boolean {
        val time = com.lovebrain.app.util.TimeFmt.now()

        val conversationalMessages = messages.filter {
            it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME
        }
        val roundMsgIds = conversationalMessages.map { it.id }.sorted().joinToString(",")

        // 1. 确定本轮变更意图（不写任何 target）——必须在 WAL PREPARED 之前完成
        val curTopic = knowledgeRepo.getCurrentTopic(kb.name)
        val hasTopic = curTopic.isNotBlank() && curTopic != "（等待第一次对话）"
        val shouldRotate = topicLabel.isNotBlank() && topicStatus == "new"

        // 2. 构建本轮记录（纯函数，不写入）
        val entry = buildRoundEntry(
            time = time,
            roundMsgIds = roundMsgIds,
            conversationalMessages = conversationalMessages,
            userHint = userHint,
            scheme = scheme,
            likedSchemes = likedSchemes
        )

        // 3. 别名→真实消息 ID 在 WAL 之前解析；恢复时不再依赖调用方上下文
        val resolvedSceneFacts = sceneFacts.map { sf ->
            sf.copy(sourceIds = sf.sourceIds.map { raw -> sourceAliasMap[raw] ?: raw })
        }

        // 4. 稳定 roundId——同一轮重试得到同一身份
        val roundId = RoundCommitJournal.stableRoundId(kb.name, roundMsgIds, entry)

        // 5. 本轮是否已完整提交（跨文件幂等的唯一判据）
        if (journal.isRoundCommitted(kb.name, roundId)) {
            L.w("S2-04: round $roundId already committed, skipping all writes (roundMsgIds=$roundMsgIds)")
            return false
        }

        val event = RoundCommitJournal.RoundCommitEvent(
            roundId = roundId,
            kbName = kb.name,
            inputRevision = knowledgeRepo.getTurnCount(kb.name),
            timestamp = time,
            topicStatus = topicStatus,
            topicLabel = topicLabel,
            shouldRotate = shouldRotate,
            hadTopic = hasTopic,
            roundMsgIds = roundMsgIds,
            frozenMessages = conversationalMessages.map { RoundCommitJournal.JournalMessage.of(it) },
            recentEntry = entry,
            sceneFacts = resolvedSceneFacts.map { RoundCommitJournal.JournalSceneFact.of(it) },
            ongoing = ongoing.map { RoundCommitJournal.JournalOngoingItem.of(it) },
            turnCountIncrement = 1
        )

        return journal.commit(kb.name, event) { e -> applyRound(e) }
    }

    /**
     * 一轮提交的六个投影——首次提交与崩溃恢复走的是同一段代码。
     *
     * 顺序固定（话题归档会清空 recent/scene，必须排在它们之前），
     * 每个投影完成后由 journal 推进水位；进程中途被杀后，
     * [recoverIfNeeded] 只补做水位尚未覆盖的投影。
     *
     * 返回 true 表示本轮确实执行了话题归档。恢复路径上若归档早已生效，
     * 返回 false——调用方据此不再重复触发知识库更新。
     */
    private suspend fun RoundCommitJournal.Tx.applyRound(
        event: RoundCommitJournal.RoundCommitEvent
    ): Boolean {
        val kbName = event.kbName
        var topicRotated = false

        // 投影 1：话题归档（仅 status=new 且有有效旧话题）
        apply(RoundCommitJournal.PROJ_TOPIC_ROTATE) {
            if (event.shouldRotate && event.hadTopic) {
                knowledgeRepo.rotateTopic(kbName)
                topicRotated = true
            }
        }

        // 投影 2：话题标签
        apply(RoundCommitJournal.PROJ_TOPIC_SET) {
            val newLabel = when {
                event.shouldRotate -> event.topicLabel
                event.topicStatus == "drift" && event.topicLabel.isNotBlank() -> event.topicLabel
                !event.hadTopic -> event.topicLabel.ifBlank { "日常对话" }
                else -> null
            }
            if (newLabel != null) knowledgeRepo.setCurrentTopic(kbName, newLabel)
        }

        // 投影 3：moment/recent.md
        apply(RoundCommitJournal.PROJ_RECENT) {
            if (event.recentEntry.isNotBlank()) {
                writeRecent(kbName, event.recentEntry, event.roundMsgIds)
            }
        }

        // 投影 4：moment/scene.md（带完整来源与归属）
        apply(RoundCommitJournal.PROJ_SCENE) {
            if (event.sceneFacts.isNotEmpty()) {
                updateSceneChain(
                    kbName = kbName,
                    topicLabel = event.topicLabel,
                    sceneFacts = event.sceneFacts.map { it.toSceneFact() },
                    frozenMessages = event.frozenMessages.map { it.toChatMessage() }
                )
            }
        }

        // 投影 5：moment/plan.md（完整 OngoingItem，不再只留 name）
        apply(RoundCommitJournal.PROJ_PLAN) {
            if (event.ongoing.isNotEmpty()) {
                mergeOngoing(kbName, event.ongoing.map { it.toOngoingItem() }, event.timestamp)
            }
        }

        // 投影 6：轮次计数——按事件中的增量值使用，恢复路径同样读该字段
        apply(RoundCommitJournal.PROJ_COUNT) {
            if (event.turnCountIncrement > 0) {
                knowledgeRepo.incrementTurnCountBy(kbName, event.turnCountIncrement)
            }
        }

        return topicRotated
    }

    /**
     * 构建一轮的 recent.md 记录（纯函数）。
     *
     * P0-1：候选回复不再自动当作实际发送消息——scheme=null 表示本轮没有确认发送任何候选，
     * likedSchemes 记录用户偏好（点赞），但不写入"实际对话"段。
     * P0-FIX：IDEA（想法）不写入 recent.md——IDEA 是本轮控制信息，不是真实聊天。
     */
    private fun buildRoundEntry(
        time: String,
        roundMsgIds: String,
        conversationalMessages: List<ChatMessage>,
        userHint: String,
        scheme: Scheme?,
        likedSchemes: List<Scheme>
    ): String = buildString {
        append("- [").append(time).append("]\n")
        append(RoundCommitJournal.WRITER_MARKER_PREFIX).append(roundMsgIds).append(" -->\n")
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

    /** 职责2（自 record 拆出）：写入 moment/recent.md，保留最近 N 轮，溢出→对话暂存
     * P0-4：未识别内容（不符合时间戳格式的块）原样保留，不当垃圾丢弃。
     * S2-04：marker 只作本文件内的去重，不承担跨文件提交标记。 */
    private suspend fun writeRecent(kbName: String, entry: String, roundMsgIds: String = "") {
        val recentPath = "moment/recent.md"
        if (roundMsgIds.isNotBlank()) {
            val marker = "${RoundCommitJournal.WRITER_MARKER_PREFIX}$roundMsgIds -->"
            if (knowledgeRepo.readFile(kbName, recentPath).contains(marker)) {
                L.w("S2-04: recent.md already holds this round locally, skipping append")
                return
            }
        }
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
        val subject: EntityRef = EntityRef.UNKNOWN,  // P1-1: 事实主体（被描述的人）
        val speaker: EntityRef = EntityRef.UNKNOWN   // P1-1: 谁说的
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
                // P1-1: 使用 SceneFact 携带的 speaker/subject，不自行推导
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

            // P0-3: speaker 由代码从 source_ids 确定性推导——绝不交给 AI
            val speaker = FactSpeakerResolver.resolveFromRoles(validIds, validSourceMap)

            // P0-4: subject 与 speaker 分离——三层解析
            // Level 1: 代码可确定（代词+speaker）
            // Level 2: 实体规则
            // P1-12: subject_candidate 已从 format.md 移除，不再传入 resolver
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
            // P1-1: 未核实来源 → 追加但不覆盖、不刷新时间
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

    /** F03: 将 SceneEntry 列表写入 scene.md，过期条目追加到 raw_scene.md
     * P0-6: 事实现在持久化 speaker/subject，不再在下一次读盘时丢失。 */
    private suspend fun writeSceneAndArchive(
        kbName: String,
        chainPath: String,
        historyPath: String,
        kept: List<SceneEntry>,
        expired: List<SceneEntry>
    ) {
        // P0-6: 渲染 scene.md——事实带 speaker/subject 持久化
        val newChainContent = if (kept.isEmpty()) "" else kept.joinToString("\n") { entry ->
            val factsStr = entry.facts.joinToString("；") { f ->
                val parts = mutableListOf<String>()
                parts.add(f.text)
                if (f.sourceIds.isNotEmpty()) {
                    parts.add("src=${f.sourceIds.joinToString(",")}")
                }
                // P0-6: 持久化 speaker/subject
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

            // 解析事实列表（支持两种格式）
            // 旧格式：事实文本⟨sourceIds⟩
            // P0-6 新格式：事实文本|src=id1,id2|spk=HER|subj=ME
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

    /** P0-4: 从事实文本中提取主体——使用 FactSubjectResolver 三层解析。
     * 旧版 extractSubject 永远返回 UNKNOWN，现在改为实际解析。 */
    private fun extractSubject(text: String): EntityRef {
        // P0-4: 旧数据从 scene.md 读取时无 speaker 上下文，只能用 Level 2 实体规则
        // 如果无法确定，仍返回 UNKNOWN
        return FactSubjectResolver.resolve(
            factText = text,
            speaker = EntityRef.UNKNOWN,
            sourceIds = emptyList(),
            dialogue = emptyList()
        )
    }

    /**
     * P0-6: 解析单条事实文本为 StoredFact，兼容新旧两种格式。
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

    /**
     * 合并 ongoing 到 moment/plan.md（双二级标题重构）。
     * 结构：`# 事项计划` + `## 进行中`（注入 prompt）+ `## 已结束`（不注入，防膨胀）。
     * 匹配事项名：存在 → 追加 "→[time]新状态（当前）"；不存在 → 新增行。
     * 已完成/已取消的事项移入 `## 已结束`；已结束区超过上限时丢弃最旧（防越来越大）。
     */
    /**
     * F04: 合并 ongoing 到 moment/plan.md（幂等更新 + 稳定 ID + 防止旧任务复活）
     *
     * 核心规则：
     * 1. 稳定 ID：每个事项有一个 itemId，不靠 name 字符串匹配。
     *    - 模型返回 itemId 优先使用；无 itemId 时用 name 的 hash 作 fallback。
     * 2. 幂等更新：相同 itemId + 相同 state 文本 → 不追加状态链。
     *    只有 state 文本实质变化时才追加 delta。
     * 3. 防止旧任务复活：已完成/已取消的事项不被非终态输出重新激活。
     *    只有模型明确输出终态→非终态转换（如"已完成"→"进行中"）才允许复活，
     *    且要求携带新的真实证据来源（sourceIds 非空）。
     * 4. 活动投影有界：状态链截断到最近 MAX_CHAIN_STATES 条，旧链归档。
     */
    private suspend fun mergeOngoing(kbName: String, items: List<OngoingItem>, timeStr: String) {
        val planPath = "moment/plan.md"
        val content = knowledgeRepo.readFile(kbName, planPath)

        // F04: 解析两个分区，兼容新旧格式（新格式: itemId~name|status|chain；旧格式: name|status|chain）
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

        // F04: 解析函数——支持新格式 itemId~name 和旧格式 name
        val parse = { lines: List<String> ->
            lines.mapNotNull { l ->
                val parts = l.split("|").map { it.trim() }
                if (parts.size < 3 || parts[0].isBlank()) return@mapNotNull null
                val firstPart = parts[0]
                // F04: 检查是否有 itemId~ 前缀
                val tildeIdx = firstPart.indexOf('~')
                val (itemId, name) = if (tildeIdx > 0) {
                    firstPart.substring(0, tildeIdx) to firstPart.substring(tildeIdx + 1)
                } else {
                    "" to firstPart
                }
                val status = parts[1]
                val chain = parts.drop(2).joinToString("|").trim()
                // F04: 从 chain 中提取最后一条状态文本作为 lastState
                val lastState = extractLastStateFromChain(chain)
                PlanItemView(name = name, status = status, chain = chain, itemId = itemId, lastState = lastState)
            }.toMutableList()
        }
        val active = parse(activeLines)
        val ended = parse(endedLines)

        // F04: 构建查找映射——优先 itemId 匹配，回退 name 匹配
        fun findExisting(item: OngoingItem): Pair<PlanItemView?, Boolean> {
            val targetItemId = item.itemId.ifBlank { deriveItemId(item.name) }
            // 先在 active 中找
            val inActive = active.firstOrNull { it.itemId == targetItemId && it.itemId.isNotBlank() }
                ?: active.firstOrNull { it.name == item.name.trim() && it.itemId.isBlank() }
            if (inActive != null) return inActive to false
            // 再在 ended 中找
            val inEnded = ended.firstOrNull { it.itemId == targetItemId && it.itemId.isNotBlank() }
                ?: ended.firstOrNull { it.name == item.name.trim() && it.itemId.isBlank() }
            if (inEnded != null) return inEnded to true
            return null to false
        }

        // 合并本轮 ongoing
        for (item in items) {
            val name = item.name.trim()
            val state = item.state.trim()
            if (name.isBlank() || state.isBlank()) continue

            // FILLER_STATES 精确匹配：命中固定填充词 → skip 不写 plan
            val normalizedState = state.trim().trimEnd(',', '。', '！', '?')
            if (item.status != "已完成" && item.status != "已取消" &&
                normalizedState in FILLER_STATES) continue

            val status = item.status.trim().ifBlank { "进行中" }
            val targetItemId = item.itemId.ifBlank { deriveItemId(name) }
            val (existing, fromEnded) = findExisting(item)

            if (existing != null) {
                // F04: 防止旧任务复活——已结束事项不被非终态输出重新激活
                if (fromEnded && status != "已完成" && status != "已取消") {
                    // 事项在 ended 区，本轮输出是非终态 → 不复活
                    // 只有当 item 携带新的真实证据来源（sourceIds 非空）时才允许复活
                    if (item.sourceIds.isEmpty()) {
                        com.lovebrain.app.util.L.w("F04: skipping revive of ended item '$name' without new evidence")
                        continue
                    }
                    com.lovebrain.app.util.L.w("F04: reviving ended item '$name' with new evidence")
                }

                // F04: 幂等检查——相同 itemId + 相同 state 文本 → 不追加
                if (existing.lastState == state) {
                    com.lovebrain.app.util.L.w("F04: idempotent skip for '$name' (same state)")
                    // 即使 state 相同，status 可能变化（如 进行中→已完成）
                    if (existing.status != status) {
                        existing.status = status
                        // 如果变为终态，移到 ended
                        if (status == "已完成" || status == "已取消") {
                            ended.remove(existing)
                            if (active.none { it.itemId == targetItemId }) active.remove(existing)
                            ended.add(existing)
                        }
                    }
                    continue
                }

                // F04: 有实质变化 → 追加 delta
                existing.status = status
                val oldChain = existing.chain.replace("（当前）", "").trimEnd('→', ' ')
                // F04: 状态链截断——只保留最近 MAX_CHAIN_STATES 条状态
                val newChain = "$oldChain→[$timeStr]$state（当前）"
                existing.chain = truncateChain(newChain)
                existing.lastState = state

                // 若从 ended 重新激活（带新证据），移回 active
                if (fromEnded && status != "已完成" && status != "已取消") {
                    ended.remove(existing)
                    if (active.none { it.itemId == targetItemId || (it.itemId.isBlank() && it.name == name) }) {
                        active.add(existing)
                    }
                }
            } else {
                // F04: 新事项——分配稳定 ID
                active.add(PlanItemView(
                    name = name,
                    status = status,
                    chain = "[$timeStr]$state（当前）",
                    itemId = targetItemId,
                    lastState = state
                ))
            }
        }

        // 已完成/已取消 → 移入 ended
        val stillActive = mutableListOf<PlanItemView>()
        for (p in active) {
            if (p.status == "已完成" || p.status == "已取消") {
                if (ended.none { it.itemId == p.itemId && p.itemId.isNotBlank() } &&
                    ended.none { it.itemId.isBlank() && it.name == p.name }) {
                    ended.add(p)
                }
            } else stillActive.add(p)
        }

        knowledgeRepo.writeFile(kbName, planPath, renderPlan(stillActive, ended))
    }

    /**
     * F04: 从状态链中提取最后一条状态文本（去掉时间戳和（当前）标记）。
     * 例如 "[09-21 14:30]进行中（当前）" → "进行中"
     */
    private fun extractLastStateFromChain(chain: String): String {
        // 找到最后一个 [time] 后面的文本
        val lastBracket = chain.lastIndexOf(']')
        if (lastBracket < 0) return chain.trim().replace("（当前）", "").trim()
        val afterBracket = chain.substring(lastBracket + 1)
        return afterBracket.replace("（当前）", "").trim()
    }

    /**
     * F04: 截断状态链——只保留最近 MAX_CHAIN_STATES 条状态。
     * 旧链被截掉的部分不丢失（已被归档系统处理），这里只是活动投影。
     */
    private fun truncateChain(chain: String): String {
        val states = chain.split("→").filter { it.isNotBlank() }
        if (states.size <= MAX_CHAIN_STATES) return chain
        val kept = states.takeLast(MAX_CHAIN_STATES)
        // 保留开头的箭头连接
        return kept.joinToString("→")
    }

    /**
     * F04: 从事项名称派生稳定 ID。
     * 使用 name 的稳定 hash，不随时间变化。
     */
    private fun deriveItemId(name: String): String {
        val cleaned = name.trim().lowercase().replace(Regex("\\s+"), "")
        return "item_${cleaned.hashCode().toString(16)}"
    }

    /** 职责3（自 mergeOngoing 拆出）：把进行中/已结束两区渲染为 plan.md 文本
     * F04: 在事项行首插入 itemId，格式: itemId|name|status|chain */
    private fun renderPlan(active: List<PlanItemView>, ended: List<PlanItemView>): String = buildString {
        append("# 事项计划\n\n## 进行中\n")
        active.forEach {
            val id = if (it.itemId.isNotBlank()) "${it.itemId}~" else ""
            append(id).append(it.name).append(" | ").append(it.status).append(" | ").append(it.chain).append("\n")
        }
        append("\n## 已结束\n")
        ended.forEach {
            val id = if (it.itemId.isNotBlank()) "${it.itemId}~" else ""
            append(id).append(it.name).append(" | ").append(it.status).append(" | ").append(it.chain).append("\n")
        }
    }

    /** plan.md 行视图（name/status/chain），供 parse/apply/render 共用
     * F04: 增加 itemId（稳定身份）和 lastState（上一轮状态文本），用于幂等更新检测 */
    private data class PlanItemView(
        val name: String,
        var status: String,
        var chain: String,
        val itemId: String = "",      // F04: 稳定身份 ID
        var lastState: String = ""    // F04: 上一轮状态文本（用于检测实质变化）
    )

    companion object {
        /** 固定填充词：命中这些 state → skip 不写 plan（避免重复堆积） */
        private val FILLER_STATES = setOf(
            "本轮未提及", "本轮无进展",
            "本轮未提及，持续推进", "本轮无进展，持续推进",
            "本轮未提及，事项持续推进中", "本轮无进展，事项持续推进中"
        )

        /** F04: 活动状态链最大保留条数——超出截断，防膨胀 */
        private const val MAX_CHAIN_STATES = 10
    }

    /**
     * S2-04: 崩溃恢复——若 journal 中有 PREPARED/WRITING 的在途轮次，
     * 用与首提完全相同的 [applyRound] 补齐尚未覆盖的投影。
     *
     * 在 KB 被打开/激活时调用。恢复不再自己拼一套"简化版"重放：
     * 旧实现把 scene 的来源清空、把 ongoing 降级成只有 name 的伪事项，
     * 还会因"recent 刚由恢复写入、count 从未写入"而错误跳过计数。
     */
    suspend fun recoverIfNeeded(kbName: String) {
        journal.recover(kbName) { event -> applyRound(event) }
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
