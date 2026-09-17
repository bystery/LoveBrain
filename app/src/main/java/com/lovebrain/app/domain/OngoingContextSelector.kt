package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.util.L
import com.lovebrain.app.util.TimeFmt

/**
 * 进行中事项上下文选择器——在 PromptBuilder 之前做 relevance gating。
 *
 * 核心原则：
 * - plan.md 是长期存储，不是每轮 Prompt 必注入内容。
 * - Stored != EligibleForCurrentTurn。
 * - 默认拒绝注入；只有满足明确相关信号才进入回复生成 Prompt。
 *
 * 拆成两层：
 * - Stored: 长期保存事项。
 * - EligibleForCurrentTurn: 本轮是否允许进入回复生成 Prompt。
 *
 * 相关信号（满足任一即可注入）：
 * - 当前真实消息重新提到了这个事件（关键词匹配）
 * - 事项出现真实新进展（AI 最近一轮 ongoing 报告了该事项）
 * - 用户本轮想法明确要求围绕此事项
 * - 用户显式设置的持续意图要求推进该事项
 *
 * 冷却机制：
 * - 如果一个事项已经出现在最近 2～3 次生成候选里，而当前真实消息没有提供新的相关证据：
 *   本轮强制禁止再次注入。
 *
 * dormant 机制：
 * - 连续若干轮无新证据 → DORMANT
 * - 事件日期已过去但没有新的状态 → DORMANT / awaiting_update
 * - 当真实消息重新涉及该事项时 → DORMANT → ACTIVE
 */
class OngoingContextSelector(
    private val knowledgeRepo: KnowledgeRepository
) {

    /** 事项内部状态 */
    enum class ItemStatus {
        ACTIVE,     // 进行中，满足注入条件时可注入
        DORMANT,    // 休眠，仍记得但默认不注入
        FINISHED,   // 已完成
        CANCELLED   // 已取消
    }

    /** 解析后的事项视图 */
    data class PlanItem(
        val name: String,
        val status: String,         // 原始 status 字段（新出现/进行中/已完成/已取消）
        val chain: String,          // 状态链
        val itemStatus: ItemStatus, // 内部推导状态
        val eventDate: String? = null  // 结构化事件日期（如有）
    )

    /** 本轮注入决策上下文 */
    data class SelectionContext(
        val messages: List<ChatMessage>,
        val currentTurn: Int,
        val currentTime: String
    )

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

        // 提取当前真实消息文本（不含 IDEA）
        val realMessages = context.messages.filter {
            it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME
        }
        val realText = realMessages.joinToString(" ") { it.content }

        // 持续意图文本
        val intentText = knowledgeRepo.readIntent(kbName).let {
            if (it.enabled) it.text else ""
        }

        val eligible = mutableListOf<PlanItem>()
        val injectedNames = mutableSetOf<String>()

        for (item in allItems) {
            // 已完成/已取消的事项不注入
            if (item.itemStatus == ItemStatus.FINISHED || item.itemStatus == ItemStatus.CANCELLED) {
                continue
            }

            // DORMANT 事项默认不注入，除非满足相关信号
            val isRelevant = isRelevantToCurrentTurn(item, realText, intentText, context)

            // 冷却检查：如果最近 2-3 轮已注入且当前无新证据，禁止再注入
            val cooldownEntry = cooldown[item.name]
            val isInCooldown = cooldownEntry != null &&
                (context.currentTurn - cooldownEntry.lastInjectedTurn) < COOLDOWN_TURNS &&
                !isRelevant

            if (isInCooldown) {
                L.w("OngoingContextSelector: '$item.name' in cooldown (last injected ${cooldownEntry?.lastInjectedTurn}, current ${context.currentTurn})")
                continue
            }

            // DORMANT 事项只有在重新相关时才注入
            if (item.itemStatus == ItemStatus.DORMANT && !isRelevant) {
                continue
            }

            // ACTIVE 事项：如果不相关且在冷却中，跳过
            if (item.itemStatus == ItemStatus.ACTIVE && !isRelevant && cooldownEntry != null) {
                // 有冷却记录但不相关 → 转为 DORMANT
                continue
            }

            // ACTIVE 事项：如果不相关且无冷却记录，也默认不注入（除非是第一次出现）
            if (item.itemStatus == ItemStatus.ACTIVE && !isRelevant) {
                // 第一次出现（无冷却记录）允许注入一次
                if (cooldownEntry == null) {
                    eligible.add(item)
                    injectedNames.add(item.name)
                }
                continue
            }

            // 相关 → 注入
            eligible.add(item)
            injectedNames.add(item.name)
        }

        // 更新冷却状态
        if (injectedNames.isNotEmpty()) {
            updateCooldown(kbName, injectedNames, context.currentTurn)
        }

        return SelectionResult(eligible, allItems, injectedNames)
    }

    /**
     * 判断事项与当前轮次是否相关。
     * 满足任一条件即相关：
     * - 当前消息中出现了事项名称的关键词
     * - 持续意图文本提到了该事项
     * - 事项有结构化 eventDate 且当前时间接近事件时间
     */
    private fun isRelevantToCurrentTurn(
        item: PlanItem,
        realText: String,
        intentText: String,
        context: SelectionContext
    ): Boolean {
        val itemName = item.name.trim()
        if (itemName.isBlank()) return false

        // 1. 关键词匹配——事项名称的核心词出现在当前消息中
        val keywords = extractKeywords(itemName)
        if (keywords.isNotEmpty()) {
            val lowerText = realText.lowercase()
            val matched = keywords.any { kw -> lowerText.contains(kw.lowercase()) }
            if (matched) return true
        }

        // 2. 持续意图文本提到该事项
        if (intentText.isNotBlank()) {
            val lowerIntent = intentText.lowercase()
            val matched = keywords.any { kw -> lowerIntent.contains(kw.lowercase()) }
            if (matched) return true
        }

        // 3. 事件日期接近当前时间（前后 1 天内）
        if (item.eventDate != null) {
            val eventTs = TimeFmt.parse("${item.eventDate} 00:00")
            if (eventTs > 0) {
                val nowTs = TimeFmt.parse("${context.currentTime.take(10)} 00:00")
                val diffHours = kotlin.math.abs(nowTs - eventTs) / 3600_000L
                if (diffHours <= 24) return true
            }
        }

        return false
    }

    /** 从事项名称中提取关键词用于匹配 */
    private fun extractKeywords(name: String): List<String> {
        // 去除常见前缀词和标点
        val cleaned = name.replace(Regex("[（）()【】\\[\\]「」\"'·]"), "")
            .replace(Regex("^(计划|约|定于|准备)"), "")
            .trim()
        // 按空格/逗号分割
        val parts = cleaned.split(Regex("[\\s,，、]+"))
            .filter { it.length >= 2 }
        // 如果分割后只有一条，直接用原名
        return if (parts.isEmpty()) listOf(cleaned).filter { it.length >= 2 } else parts
    }

    /**
     * 解析 plan.md 的事项行为 PlanItem，推导内部状态。
     */
    private fun parsePlanItems(content: String): List<PlanItem> {
        if (content.isBlank()) return emptyList()
        val items = mutableListOf<PlanItem>()
        for (line in content.lines()) {
            val t = line.trim()
            if (!t.contains("|")) continue
            val parts = t.split("|").map { it.trim() }
            if (parts.size < 3 || parts[0].isBlank()) continue
            val name = parts[0]
            val status = parts[1]
            val chain = parts.drop(2).joinToString("|").trim()

            val itemStatus = deriveItemStatus(status, chain)
            val eventDate = extractEventDate(name, chain)

            items.add(PlanItem(name, status, chain, itemStatus, eventDate))
        }
        return items
    }

    /** 推导内部状态 */
    private fun deriveItemStatus(status: String, chain: String): ItemStatus {
        val s = status.trim()
        return when {
            s == "已完成" -> ItemStatus.FINISHED
            s == "已取消" -> ItemStatus.CANCELLED
            s == "进行中" || s == "新出现" -> ItemStatus.ACTIVE
            else -> ItemStatus.ACTIVE
        }
    }

    /** 尝试从事项名称和状态链中提取事件日期 */
    private fun extractEventDate(name: String, chain: String): String? {
        // 尝试从事项名中提取日期（如"9-21 周一见面" → "2026-09-21"）
        val dateInName = Regex("(\\d{1,2})-(\\d{1,2})").find(name)
        if (dateInName != null) {
            val month = dateInName.groupValues[1].padStart(2, '0')
            val day = dateInName.groupValues[2].padStart(2, '0')
            val year = TimeFmt.today().take(4)
            return "$year-$month-$day"
        }
        // 尝试从状态链中提取日期
        val dateInChain = Regex("\\[(\\d{4}-\\d{2}-\\d{2})").find(chain)
        if (dateInChain != null) {
            return dateInChain.groupValues[1]
        }
        return null
    }

    // ═══════════ 冷却状态持久化（moment/ongoing_cooldown.json） ═══════════

    @kotlinx.serialization.Serializable
    private data class CooldownEntry(
        val name: String,
        var lastInjectedTurn: Int,
        var lastInjectedTime: String
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

    private suspend fun updateCooldown(kbName: String, injectedNames: Set<String>, currentTurn: Int) {
        val existing = readCooldown(kbName).toMutableMap()
        val now = TimeFmt.now()
        for (name in injectedNames) {
            existing[name] = CooldownEntry(name, currentTurn, now)
        }
        // 只保留最近 20 条冷却记录，防膨胀
        val toKeep = existing.values.sortedByDescending { it.lastInjectedTurn }.take(20)
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(CooldownEntry.serializer()),
            toKeep
        )
        knowledgeRepo.writeFile(kbName, "moment/ongoing_cooldown.json", json)
    }

    companion object {
        /** 冷却轮数：如果一个事项在最近 N 轮已注入且无新证据，禁止再注入 */
        const val COOLDOWN_TURNS = 3
    }
}
