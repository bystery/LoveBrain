package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.SystemClock
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ReplyDirective
import com.lovebrain.app.model.isIntentEffective
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
 *
 * 冷却（审计 F2-2，统一语义）：同一事项在无新真实聊天证据时不重复注入，
 * 与是否"相关"无关——持续意图/本轮备注命中不能绕过冷却；冷却到期
 * （距 lastInjectedTurn ≥ COOLDOWN_TURNS）或出现新证据（HER/ME 消息命中）后
 * 恢复注入。
 */
class OngoingContextSelector(
    private val knowledgeRepo: KnowledgePort,
    /**
     * 冷却记录里存的 `lastInjectedAt` 是要落盘的时间文本；不注入时钟的话，
     * "事项休眠多久之后才允许再提"这条规则根本没法测（见文件里的 cooldown 分支）。
     */
    private val clock: Clock = SystemClock
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
        val cooldown = cooldownStore.read(kbName)

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
        // 参与检索授权的条件与注入提示词那一段**完全同一条**：开 且 正文非空
        // （判据只有 `isIntentEffective` 这一颗，两处各写一遍就会长成"一处注入、一处检索拒绝"）
        val intentConfig = context.effectiveIntent
        val intentText = if (intentConfig.isIntentEffective) intentConfig.text else ""

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

            // 冷却检查（审计 F2-2：冷却统一生效）——产品语义：
            // 同一事项在**无新真实聊天证据**时不重复注入，与是否"相关"无关。
            // 旧实现要求 `!signals.isRelevant`，而后续 ACTIVE/DORMANT 筛选本就跳过
            // 不相关项——结果相关项（意图/备注命中）每轮绕开冷却注入、不相关项本来
            // 就不注入，冷却状态形同虚设。
            // - 冷却到期（距 lastInjectedTurn ≥ COOLDOWN_TURNS）自动恢复注入；
            // - 出现新证据（HER/ME 真实消息关键词命中）立即恢复注入——这是唯一的
            //   冷却期内提前放行通道；
            // - 用户显式设置的持续意图**不构成绕过冷却的理由**：意图表达的是"保持在意"，
            //   不是"每轮必提"；冷却只是防止连发，到期自动恢复，不丢功能。
            // - lastInjectedTurn < 0（只推进过证据、从未注入过）不算冷却中。
            // 不做"每三轮禁一次"之类的新发明——只有这一条统一语义。
            val cooldownEntry = cooldown[item.name]
            val isInCooldown = cooldownEntry != null &&
                cooldownEntry.lastInjectedTurn >= 0 &&
                (context.currentTurn - cooldownEntry.lastInjectedTurn) < COOLDOWN_TURNS &&
                !signals.realMessageEvidence

            if (isInCooldown) {
                // 这里念的是事项标题（用户自己写的那条短目标），不是聊天正文；
                // 换成标题长度就没有"哪一颗被冷却挡住了"这条读数了，所以保留标题本身。
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

        // 更新冷却状态——审计 #19：这里**不再直接写盘**。
        // Prompt 构造只是"读取/准备请求"，不是"确认一次真实状态变化"：用户生成失败、
        // 取消或改写消息时，本轮输入永远不会经 TopicRecorder.record 正式提交，
        // 冷却与证据状态不许提前推进。本轮的推进量暂存为 pending（同库覆盖——
        // 新一轮的选择取代上一轮未提交的暂存），由 TopicRecorder.record 提交成功后
        // 按轮次消息指纹消费落盘（见 [OngoingCooldownStore] 与 TopicRecorder.
        // commitPendingOngoingCooldown）。
        // - injectedNames: 只在事项被注入时推进 lastInjectedTurn
        // - realEvidenceNames: 只在真实消息证据时推进 lastEvidenceTurn（持续意图/备注不算）
        if (injectedNames.isNotEmpty() || realEvidenceNames.isNotEmpty()) {
            OngoingCooldownStore.stage(
                PendingCooldownUpdate(
                    kbName = kbName,
                    roundFingerprint = OngoingCooldownStore.roundFingerprint(realMessages),
                    turn = context.currentTurn,
                    injectedNames = injectedNames.toSet(),
                    evidenceNames = realEvidenceNames.toSet()
                )
            )
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

        // F03（2026-10-15 书 §4）：匹配词表只用上面这颗 `extractKeywords` 清出来的明确词。
        // 下面这颗二字滑动窗口（`extractRawKeywords`，它自己 KDoc 就写着"太宽，会误召回"）
        // 以前挂在**全部三个判决**上——事项"周末见面"因一句"周末还要加班"就拿到真实证据、
        // 刷新 lastEvidenceTurn。泛化切片从判据里整个撤掉：宁可轻微召回下降，
        // 不许虚假相关（书点名不引分词 SDK、不加 Embedding，这里也不发明第二套滑窗）。
        // 1. 真实消息关键词匹配——唯一能更新 lastEvidenceTurn 的信号
        val lowerText = realText.lowercase()
        val matchedInMessage = keywords.any { kw -> lowerText.contains(kw.lowercase()) }

        // 2. ReplyDirective 提到该事项——相关但不刷新证据时间
        var matchedInDirective = false
        if (directiveText.isNotBlank()) {
            val lowerDirective = directiveText.lowercase()
            matchedInDirective = keywords.any { kw -> lowerDirective.contains(kw.lowercase()) }
        }

        // 3. 持续意图文本提到该事项——相关但不刷新证据时间
        var matchedInIntent = false
        if (intentText.isNotBlank()) {
            val lowerIntent = intentText.lowercase()
            matchedInIntent = keywords.any { kw -> lowerIntent.contains(kw.lowercase()) }
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

    private val cooldownStore = OngoingCooldownStore(knowledgeRepo, clock)

    companion object {
        /** 冷却轮数：如果一个事项在最近 N 轮已注入且无新证据，禁止再注入 */
        const val COOLDOWN_TURNS = 3

        /** DORMANT 阈值——连续 N 轮无新证据 → DORMANT */
        const val DORMANT_TURNS = 5
    }
}

/**
 * 一轮待确认的进行中事项冷却推进（审计 #19）。
 *
 * [OngoingContextSelector.selectForInjection] 在 Prompt 构造时产出本对象并暂存，
 * 但**不落盘**；只有 [TopicRecorder.record]（一轮提交的确认事务点）成功提交本轮后，
 * 才按轮次消息指纹消费并写入 moment/ongoing_cooldown.json。
 * 生成失败 / 取消 / 改写消息 → 本轮永不提交 → 暂存永不匹配 → 冷却与证据状态不推进。
 *
 * @param kbName 知识库（暂存按库一个槽位，新一轮选择覆盖旧暂存）
 * @param roundFingerprint 提交轮指纹（[OngoingCooldownStore.roundFingerprint]）——
 *                         改一条消息即失配，陈旧暂存不会被误消费
 * @param turn 选择时的轮次计数（与旧实现写入值一致：提交时轮次尚未自增）
 * @param injectedNames 本轮被注入的事项（推进 lastInjectedTurn）
 * @param evidenceNames 本轮有真实消息证据的事项（推进 lastEvidenceTurn）
 */
data class PendingCooldownUpdate(
    val kbName: String,
    val roundFingerprint: String,
    val turn: Int,
    val injectedNames: Set<String>,
    val evidenceNames: Set<String>
)

/**
 * 进行中事项冷却状态（moment/ongoing_cooldown.json）的唯一读写点。
 *
 * 读写语义原样继承自旧 OngoingContextSelector 私有实现（JSON 字段名、20 条上限、
 * 注入/证据两条独立时间线都不变）；变化只有一处——写不再发生在选择时，
 * 而是经 [stage] → [TopicRecorder] 确认事务点 → [applyUpdate] 的两段式（审计 #19）。
 *
 * 暂存注册表挂在 companion（进程内存）：跨实例共享，TopicRecorder 无需经由 DI
 * 拿到选择器实例即可消费；进程死亡丢失暂存的方向是"冷却不推进"，不是误推进——fail-safe。
 */
class OngoingCooldownStore(
    private val knowledgeRepo: com.lovebrain.app.domain.port.KnowledgePort,
    private val clock: com.lovebrain.app.domain.port.Clock = com.lovebrain.app.domain.port.SystemClock
) {

    /** 冷却状态的读侧视图（只暴露两条独立时间线，不暴露存储格式） */
    data class CooldownState(
        val lastInjectedTurn: Int,
        val lastEvidenceTurn: Int
    )

    @kotlinx.serialization.Serializable
    private data class CooldownEntry(
        val name: String,
        var lastInjectedTurn: Int,
        var lastInjectedTime: String,
        // 独立的证据轮次——决定 ACTIVE / DORMANT，不与注入轮次互相替代
        var lastEvidenceTurn: Int = -1
    )

    /** 读取某库的冷却状态（文件缺失/损坏按空处理——fail closed，不注入） */
    suspend fun read(kbName: String): Map<String, CooldownState> {
        val content = knowledgeRepo.readFile(kbName, "moment/ongoing_cooldown.json")
        if (content.isBlank()) return emptyMap()
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString<List<CooldownEntry>>(content)
                .associate { it.name to CooldownState(it.lastInjectedTurn, it.lastEvidenceTurn) }
        }.getOrDefault(emptyMap())
    }

    /**
     * 把一轮待确认的冷却推进写入盘（由 [TopicRecorder] 在提交成功后调用）。
     *
     * - lastInjectedTurn: 只推进 injectedNames 中的事项
     * - lastEvidenceTurn: 只推进 evidenceNames 中的事项
     *
     * 两者独立：有证据但未注入 → lastEvidenceTurn 更新但 lastInjectedTurn 不更新；
     * 被注入但无新证据 → lastInjectedTurn 更新但 lastEvidenceTurn 不更新。
     */
    suspend fun applyUpdate(pending: PendingCooldownUpdate) {
        val existing = readEntries(pending.kbName).toMutableMap()
        val now = clock.wallClock()
        for (name in pending.injectedNames) {
            val entry = existing[name] ?: CooldownEntry(name, -1, now, -1)
            entry.lastInjectedTurn = pending.turn
            entry.lastInjectedTime = now
            existing[name] = entry
        }
        for (name in pending.evidenceNames) {
            val entry = existing[name] ?: CooldownEntry(name, -1, now, -1)
            entry.lastEvidenceTurn = pending.turn
            existing[name] = entry
        }
        // 只保留最近 20 条冷却记录，防膨胀
        val toKeep = existing.values
            .sortedByDescending { maxOf(it.lastInjectedTurn, it.lastEvidenceTurn) }
            .take(20)
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(CooldownEntry.serializer()),
            toKeep
        )
        knowledgeRepo.writeFile(pending.kbName, "moment/ongoing_cooldown.json", json)
    }

    private suspend fun readEntries(kbName: String): Map<String, CooldownEntry> {
        val content = knowledgeRepo.readFile(kbName, "moment/ongoing_cooldown.json")
        if (content.isBlank()) return emptyMap()
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString<List<CooldownEntry>>(content)
                .associateBy { it.name }
        }.getOrDefault(emptyMap())
    }

    companion object {
        /** 进程内暂存注册表：kbName → 最近的待确认推进（同库单槽，新选择覆盖旧暂存） */
        private val pendingByKb = java.util.concurrent.ConcurrentHashMap<String, PendingCooldownUpdate>()

        /** Prompt 构造阶段暂存一轮待确认推进（同库覆盖：新一轮选择取代上一轮未提交的暂存） */
        fun stage(update: PendingCooldownUpdate) {
            pendingByKb[update.kbName] = update
        }

        /** 查看暂存（测试与诊断用，不消费） */
        fun peek(kbName: String): PendingCooldownUpdate? = pendingByKb[kbName]

        /**
         * 确认事务点消费：仅当提交轮指纹与暂存一致才取出并移除。
         * 指纹不匹配（用户改写消息/换了轮）→ 返回 null 且暂存原样保留，
         * 交给下一轮选择覆盖——陈旧推进永不落盘。
         */
        fun consumeIfMatch(kbName: String, roundFingerprint: String): PendingCooldownUpdate? {
            val pending = pendingByKb[kbName] ?: return null
            if (pending.roundFingerprint != roundFingerprint) return null
            return if (pendingByKb.remove(kbName, pending)) pending else null
        }

        /** 测试隔离用：清空暂存注册表 */
        fun clearPendingForTest() = pendingByKb.clear()

        /**
         * 提交轮指纹：HER/ME 消息的 id+内容。选择侧与提交侧（TopicRecorder）用同一函数，
         * 同一轮重试/重生成指纹一致；用户改写任何一条消息即失配。
         */
        fun roundFingerprint(conversationalMessages: List<ChatMessage>): String =
            conversationalMessages.joinToString("|") { "${it.id}:${it.content}" }
    }
}
