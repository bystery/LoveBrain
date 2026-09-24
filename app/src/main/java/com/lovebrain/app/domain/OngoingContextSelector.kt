package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ReplyDirective
import com.lovebrain.app.util.L
import com.lovebrain.app.util.TimeFmt

/**
 * 进行中事项上下文选择器——在 PromptBuilder 之前做 relevance gating。
 *
 * 修正：
 * - SelectionContext 增加 replyDirective 字段——用户本轮想法才能成为真实 relevance signal
 * - DORMANT 成为真实状态：连续 N 轮无新证据 → DORMANT
 * - eventDate 只从事项名称提取，不从状态链记录时间提取（状态写入时间 ≠ 事件发生时间）
 * - 日期临近不得单独授权注入——只能提高 retrieval priority，不能单独进入 generation-visible context
 * - selector 缺失时 fail closed（不注入），不 fail open 回到旧 bug
 *
 * 核心原则：
 * - plan.md 是长期存储，不是每轮 Prompt 必注入内容。
 * - Stored != EligibleForCurrentTurn。
 * - 默认拒绝注入；只有满足明确相关信号才进入回复生成 Prompt。
 *
 * 相关信号（满足任一即可注入）：
 * - 当前真实消息重新提到了这个事件（关键词匹配）
 * - 用户本轮想法（ReplyDirective）明确要求围绕此事项
 * - 用户显式设置的持续意图要求推进该事项
 *
 * 日期临近只能提高 retrieval priority（使关键词匹配更宽松），
 * 但不能单独成为注入理由。
 */
class OngoingContextSelector(
    private val knowledgeRepo: KnowledgePort
) {

    /** 事项内部状态 */
    enum class ItemStatus {
        ACTIVE,     // 进行中，满足注入条件时可注入
        DORMANT,    // 休眠——连续 N 轮无真实新证据，仍记得但默认不注入
        FINISHED,   // 已完成
        CANCELLED   // 已取消
    }

    /** 解析后的事项视图 */
    data class PlanItem(
        val name: String,
        val status: String,         // 原始 status 字段（新出现/进行中/已完成/已取消）
        val chain: String,          // 状态链
        val itemStatus: ItemStatus, // 内部推导状态
        val eventDate: String? = null,  // 结构化事件日期（仅从事项名称提取）
        val lastInjectedTurn: Int = -1,  // 最后一次注入的轮次（只做 prompt cooldown / 防重复注入）
        val lastEvidenceTurn: Int = -1   // 最后一次有真实新证据的轮次（决定 ACTIVE / DORMANT）
    )

    /** 本轮注入决策上下文
     *  增加 replyDirective——用户本轮想法成为真实 relevance signal
     *  增加 effectiveIntent——冻结的持续意图快照，不重新 readIntent */
    data class SelectionContext(
        val messages: List<ChatMessage>,
        val currentTurn: Int,
        val currentTime: String,
        val replyDirective: ReplyDirective? = null,  // 用户本轮想法
        val effectiveIntent: com.lovebrain.app.model.IntentConfig = com.lovebrain.app.model.IntentConfig()  // 冻结快照
    )

    /**
     * 相关性信号——区分真实消息证据和指令/意图相关。
     *
     * 只有 [realMessageEvidence] 才更新 lastEvidenceTurn（决定 ACTIVE/DORMANT）。
     * [replyDirectiveRelevant] 和 [persistentIntentRelevant] 可使事项 eligible，
     * 但不刷新证据时间——防止持续意图永远保持 ACTIVE。
     */
    data class RelevanceSignals(
        val realMessageEvidence: Boolean,
        val replyDirectiveRelevant: Boolean,
        val persistentIntentRelevant: Boolean
    ) {
        /** 综合判断是否相关（满足任一信号即相关） */
        val isRelevant: Boolean get() = realMessageEvidence || replyDirectiveRelevant || persistentIntentRelevant
    }

    /** 本轮注入决策结果 */
    data class SelectionResult(
        val eligibleItems: List<PlanItem>,
        val allItems: List<PlanItem>,
        val injectedItemNames: Set<String>
    )

    /**
     * 从 plan.md 读取进行中事项，做 relevance gating 后返回本轮允许注入的事项。
     *
     * 默认拒绝注入——只有满足明确相关信号才进入。
     * fail closed——selector 逻辑任何异常都不 fallback 到整段注入。
     */
    suspend fun selectForInjection(
        kbName: String,
        context: SelectionContext
    ): SelectionResult {
        val planContent = knowledgeRepo.readPlanActive(kbName)
        val allItems = parsePlanItems(planContent)
        if (allItems.isEmpty()) return SelectionResult(emptyList(), emptyList(), emptySet())

        // 读取冷却状态
        val cooldown = readCooldown(kbName)

        // 将冷却信息合并到 items
        // 同时读取 lastInjectedTurn 和 lastEvidenceTurn——两者独立
        val itemsWithCooldown = allItems.map { item ->
            val cd = cooldown[item.name]
            item.copy(
                lastInjectedTurn = cd?.lastInjectedTurn ?: -1,
                lastEvidenceTurn = cd?.lastEvidenceTurn ?: -1
            )
        }

        // 推导 DORMANT 状态——基于 lastEvidenceTurn 而非 lastInjectedTurn
        // 连续 DORMANT_TURNS 轮无真实新证据 → DORMANT
        // 事项有真实证据但因其他原因未注入，不得错误进入 DORMANT
        // 事项不断被注入但没有任何真实新证据，也不能靠"被注入"永久保持 ACTIVE
        val itemsWithStatus = itemsWithCooldown.map { item ->
            if (item.itemStatus == ItemStatus.ACTIVE && item.lastEvidenceTurn >= 0) {
                val turnsSinceLastEvidence = context.currentTurn - item.lastEvidenceTurn
                if (turnsSinceLastEvidence >= DORMANT_TURNS) {
                    item.copy(itemStatus = ItemStatus.DORMANT)
                } else {
                    item
                }
            } else {
                item
            }
        }

        // 提取当前真实消息文本（不含 IDEA）
        val realMessages = context.messages.filter {
            it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME
        }
        val realText = realMessages.joinToString(" ") { it.content }

        // 使用 effectiveIntent——从冻结快照读取，不重新 readIntent
        // 已暂停、已完成、已到期均不参与检索授权
        val intentConfig = context.effectiveIntent
        val intentText = if (intentConfig.enabled &&
            intentConfig.status != com.lovebrain.app.model.IntentStatus.PAUSED &&
            intentConfig.status != com.lovebrain.app.model.IntentStatus.COMPLETED &&
            intentConfig.status != com.lovebrain.app.model.IntentStatus.EXPIRED) {
            intentConfig.text
        } else ""

        // 用户本轮想法（ReplyDirective）——真实 relevance signal
        val directiveText = context.replyDirective?.text ?: ""

        val eligible = mutableListOf<PlanItem>()
        val injectedNames = mutableSetOf<String>()
        val realEvidenceNames = mutableSetOf<String>()

        for (item in itemsWithStatus) {
            // 已完成/已取消的事项不注入
            if (item.itemStatus == ItemStatus.FINISHED || item.itemStatus == ItemStatus.CANCELLED) {
                continue
            }

            // 判断相关性——返回多信号结果
            val signals = computeRelevanceSignals(
                item, realText, intentText, directiveText, context
            )

            // 只有真实消息证据才刷新 lastEvidenceTurn
            if (signals.realMessageEvidence) {
                realEvidenceNames.add(item.name)
            }

            // 冷却检查：如果最近 N 轮已注入且当前无新证据，禁止再注入
            val cooldownEntry = cooldown[item.name]
            val isInCooldown = cooldownEntry != null &&
                (context.currentTurn - cooldownEntry.lastInjectedTurn) < COOLDOWN_TURNS &&
                !signals.isRelevant

            if (isInCooldown) {
                L.w("OngoingContextSelector: '${item.name}' in cooldown (last injected ${cooldownEntry?.lastInjectedTurn}, current ${context.currentTurn})")
                continue
            }

            // DORMANT 事项只有在重新相关时才注入
            if (item.itemStatus == ItemStatus.DORMANT && !signals.isRelevant) {
                continue
            }

            // ACTIVE 事项：如果不相关 → 跳过
            if (item.itemStatus == ItemStatus.ACTIVE && !signals.isRelevant) {
                continue
            }

            // 相关 → 注入
            eligible.add(item)
            injectedNames.add(item.name)
        }

        // 更新冷却状态
        // - lastInjectedTurn: 只在事项被注入时更新（injectedNames）
        // - lastEvidenceTurn: 只在真实消息证据时更新（realEvidenceNames）
        //   持续意图和 ReplyDirective 不刷新证据时间
        if (injectedNames.isNotEmpty() || realEvidenceNames.isNotEmpty()) {
            updateCooldown(kbName, injectedNames, realEvidenceNames, context.currentTurn)
        }

        return SelectionResult(eligible, itemsWithStatus, injectedNames)
    }

    /**
     * 计算事项与当前轮次的相关性信号。
     *
     * 返回 [RelevanceSignals]——区分真实消息证据和指令/意图相关。
     * 只有真实消息匹配才更新 lastEvidenceTurn。
     */
    private fun computeRelevanceSignals(
        item: PlanItem,
        realText: String,
        intentText: String,
        directiveText: String,
        @Suppress("UNUSED_PARAMETER") context: SelectionContext
    ): RelevanceSignals {
        val itemName = item.name.trim()
        if (itemName.isBlank()) return RelevanceSignals(false, false, false)

        val keywords = extractKeywords(itemName)
        if (keywords.isEmpty()) return RelevanceSignals(false, false, false)

        val rawKeywords = extractRawKeywords(itemName)
        val allKeywords = (keywords + rawKeywords).distinct()

        // 1. 真实消息关键词匹配——唯一能更新 lastEvidenceTurn 的信号
        val lowerText = realText.lowercase()
        val matchedInMessage = allKeywords.any { kw -> lowerText.contains(kw.lowercase()) }

        // 2. ReplyDirective 提到该事项——相关但不刷新证据时间
        var matchedInDirective = false
        if (directiveText.isNotBlank()) {
            val lowerDirective = directiveText.lowercase()
            matchedInDirective = allKeywords.any { kw -> lowerDirective.contains(kw.lowercase()) }
        }

        // 3. 持续意图文本提到该事项——相关但不刷新证据时间
        var matchedInIntent = false
        if (intentText.isNotBlank()) {
            val lowerIntent = intentText.lowercase()
            matchedInIntent = allKeywords.any { kw -> lowerIntent.contains(kw.lowercase()) }
        }

        return RelevanceSignals(
            realMessageEvidence = matchedInMessage,
            replyDirectiveRelevant = matchedInDirective,
            persistentIntentRelevant = matchedInIntent
        )
    }

    /** 从事项名称中提取关键词用于匹配
     * 改进关键词提取——从事项名中去除日期/数字前缀，提取核心名词 */
    private fun extractKeywords(name: String): List<String> {
        // 去除常见前缀词和标点
        var cleaned = name.replace(Regex("[（）()【】\\[\\]「」\"'·]"), "")
            .replace(Regex("^(计划|约|定于|准备)"), "")
            .trim()

        // 去除日期模式（如 "9-21", "周一", "周二" 等），保留核心事件名词
        cleaned = cleaned.replace(Regex("\\d{1,2}[-/]\\d{1,2}"), "")
            .replace(Regex("周[一二三四五六日天]"), "")
            .replace(Regex("^[\\s-]+"), "")
            .trim()

        // 按空格/逗号分割
        val parts = cleaned.split(Regex("[\\s,，、]+"))
            .filter { it.length >= 2 }

        // 如果分割后只有一条，直接用清理后的名
        val keywords = if (parts.isEmpty()) listOf(cleaned).filter { it.length >= 2 } else parts

        // 如果清理后关键词为空（如事项名只有日期），回退到原始名（去除前缀后的）
        return if (keywords.isEmpty()) {
            val fallback = name.replace(Regex("[（）()【】\\[\\]「」\"'·]"), "")
                .replace(Regex("^(计划|约|定于|准备)"), "")
                .trim()
            if (fallback.length >= 2) listOf(fallback) else emptyList()
        } else {
            keywords
        }
    }

    /**
     * 从原始事项名称中提取 2 字滑动窗口关键词。
     *
     * 已废弃——二字滑动窗口匹配太宽，会误召回。
     * "周末见面" 包含 "周末"，消息 "周末还要加班" 即命中，但见面事项没有新证据。
     * 现在只作为 extractKeywords 的回退参考，不再单独用于 any contains 判断。
     */
    private fun extractRawKeywords(name: String): List<String> {
        val cleaned = name.replace(Regex("[（）()【】\\[\\]「」\"'·\\s,，、]+"), "")
        if (cleaned.length < 2) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0..cleaned.length - 2) {
            result.add(cleaned.substring(i, i + 2))
        }
        return result
    }

    /**
     * 读取已消费的消息 ID（防重复来源推进证据时间）。
     * 只保留最近 N 条，防文件无限增长。
     */
    private suspend fun readConsumedMessageIds(kbName: String): Set<String> {
        val content = knowledgeRepo.readFile(kbName, "moment/consumed_msg_ids.json")
        if (content.isBlank()) return emptySet()
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString<Set<String>>(content)
        }.getOrDefault(emptySet())
    }

    /**
     * 写入已消费的消息 ID，只保留最近 50 条。
     */
    private suspend fun writeConsumedMessageIds(kbName: String, ids: Set<String>) {
        val toKeep = ids.toList().takeLast(50).toSet()
        val stringSerializer = kotlinx.serialization.serializer<String>()
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.SetSerializer(stringSerializer),
            toKeep
        )
        knowledgeRepo.writeFile(kbName, "moment/consumed_msg_ids.json", json)
    }

    /**
     * 解析 plan.md 的事项行为 PlanItem，推导内部状态。
     * 兼容新格式 itemId~name|status|chain 和旧格式 name|status|chain。
     */
    private fun parsePlanItems(content: String): List<PlanItem> {
        if (content.isBlank()) return emptyList()
        val items = mutableListOf<PlanItem>()
        for (line in content.lines()) {
            val t = line.trim()
            if (!t.contains("|")) continue
            val parts = t.split("|").map { it.trim() }
            if (parts.size < 3 || parts[0].isBlank()) continue
            // 检查 itemId~ 前缀
            val firstPart = parts[0]
            val tildeIdx = firstPart.indexOf('~')
            val name = if (tildeIdx > 0) firstPart.substring(tildeIdx + 1) else firstPart
            val status = parts[1]
            val chain = parts.drop(2).joinToString("|").trim()

            val itemStatus = deriveItemStatus(status, chain)
            val eventDate = extractEventDate(name)

            items.add(PlanItem(name, status, chain, itemStatus, eventDate))
        }
        return items
    }

    /** 推导内部状态
     * DORMANT 不在此推导——DORMANT 由冷却状态 + 轮次差距在 selectForInjection 中推导 */
    private fun deriveItemStatus(status: String, @Suppress("UNUSED_PARAMETER") chain: String): ItemStatus {
        val s = status.trim()
        return when {
            s == "已完成" -> ItemStatus.FINISHED
            s == "已取消" -> ItemStatus.CANCELLED
            s == "进行中" || s == "新出现" -> ItemStatus.ACTIVE
            else -> ItemStatus.ACTIVE
        }
    }

    /** 尝试从事项名称中提取事件日期
     * 修正：不再从状态链中提取日期——状态链中的时间是"记录状态的时间"，不是"事件发生时间" */
    private fun extractEventDate(name: String): String? {
        // 仅从事项名中提取日期（如"9-21见面" → "2026-09-21"）
        val dateInName = Regex("(\\d{1,2})-(\\d{1,2})").find(name)
        if (dateInName != null) {
            val month = dateInName.groupValues[1].padStart(2, '0')
            val day = dateInName.groupValues[2].padStart(2, '0')
            val year = TimeFmt.today().take(4)
            return "$year-$month-$day"
        }
        return null
    }

    // ═══════════ 冷却状态持久化（moment/ongoing_cooldown.json） ═══════════

    @kotlinx.serialization.Serializable
    private data class CooldownEntry(
        val name: String,
        var lastInjectedTurn: Int,
        var lastInjectedTime: String,
        // 独立的证据轮次——决定 ACTIVE / DORMANT，不与注入轮次互相替代
        var lastEvidenceTurn: Int = -1
    )

    private suspend fun readCooldown(kbName: String): Map<String, CooldownEntry> {
        val content = knowledgeRepo.readFile(kbName, "moment/ongoing_cooldown.json")
        if (content.isBlank()) return emptyMap()
        return runCatching {
            val arr = kotlinx.serialization.json.Json.decodeFromString<
                List<CooldownEntry>>(content)
            arr.associateBy { it.name }
        }.getOrDefault(emptyMap())
    }

    /**
     * 更新冷却状态——同时更新 lastInjectedTurn 和 lastEvidenceTurn。
     *
     * - lastInjectedTurn: 只在事项被注入时更新（injectedNames 中的事项）
     * - lastEvidenceTurn: 在事项本轮有真实新证据时更新（evidenceNames 中的事项）
     *
     * 两者独立：有证据但未注入 → lastEvidenceTurn 更新但 lastInjectedTurn 不更新；
     * 被注入但无新证据 → lastInjectedTurn 更新但 lastEvidenceTurn 不更新。
     */
    private suspend fun updateCooldown(
        kbName: String,
        injectedNames: Set<String>,
        evidenceNames: Set<String>,
        currentTurn: Int
    ) {
        val existing = readCooldown(kbName).toMutableMap()
        val now = TimeFmt.now()
        for (name in injectedNames) {
            val entry = existing[name] ?: CooldownEntry(name, -1, now, -1)
            entry.lastInjectedTurn = currentTurn
            entry.lastInjectedTime = now
            existing[name] = entry
        }
        for (name in evidenceNames) {
            val entry = existing[name] ?: CooldownEntry(name, -1, now, -1)
            entry.lastEvidenceTurn = currentTurn
            existing[name] = entry
        }
        // 只保留最近 20 条冷却记录，防膨胀
        val toKeep = existing.values.sortedByDescending { maxOf(it.lastInjectedTurn, it.lastEvidenceTurn) }.take(20)
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(CooldownEntry.serializer()),
            toKeep
        )
        knowledgeRepo.writeFile(kbName, "moment/ongoing_cooldown.json", json)
    }

    companion object {
        /** 冷却轮数：如果一个事项在最近 N 轮已注入且无新证据，禁止再注入 */
        const val COOLDOWN_TURNS = 3

        /** DORMANT 阈值——连续 N 轮无新证据 → DORMANT */
        const val DORMANT_TURNS = 5
    }
}
