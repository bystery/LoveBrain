package com.lovebrain.app.domain

import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.model.OngoingItem

/**
 * `moment/plan.md` 的所有者——"进行中事项在这份文件里怎么存、怎么合"。
 *
 * 变化理由：plan.md 的两区结构（`## 进行中` 注入 prompt / `## 已结束` 不注入）、
 * 事项的稳定身份（itemId 与 name 的匹配回退）、状态链的追加与截断、
 * 终态事项不被旧任务复活——这一族规则只跟"事项"有关，
 * 改了它们不影响 scene.md 的事实匹配，也不影响轮次事务。
 *
 * 从 [TopicRecorder] 拆出，判据与文案逐字保留。时间串仍由调用方传入
 * （就是 WAL 事件里的 `timestamp`），所以恢复路径与首提写到链上的时间一致。
 */
class OngoingPlanStore(
    private val knowledgeRepo: KnowledgePort
) {

    /**
     * 合并 ongoing 到 moment/plan.md（双二级标题重构）。
     * 结构：`# 事项计划` + `## 进行中`（注入 prompt）+ `## 已结束`（不注入，防膨胀）。
     * 匹配事项名：存在 → 追加 "→[time]新状态（当前）"；不存在 → 新增行。
     * 已完成/已取消的事项移入 `## 已结束`；已结束区超过上限时丢弃最旧（防越来越大）。
     */
    /**
     * 合并 ongoing 到 moment/plan.md（幂等更新 + 稳定 ID + 防止旧任务复活）
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
    internal suspend fun mergeOngoing(kbName: String, items: List<OngoingItem>, timeStr: String) {
        val planPath = "moment/plan.md"
        val content = knowledgeRepo.readFile(kbName, planPath)

        // 解析两个分区，兼容新旧格式（新格式: itemId~name|status|chain；旧格式: name|status|chain）
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

        // 解析函数——支持新格式 itemId~name 和旧格式 name
        val parse = { lines: List<String> ->
            lines.mapNotNull { l ->
                val parts = l.split("|").map { it.trim() }
                if (parts.size < 3 || parts[0].isBlank()) return@mapNotNull null
                val firstPart = parts[0]
                // 检查是否有 itemId~ 前缀
                val tildeIdx = firstPart.indexOf('~')
                val (itemId, name) = if (tildeIdx > 0) {
                    firstPart.substring(0, tildeIdx) to firstPart.substring(tildeIdx + 1)
                } else {
                    "" to firstPart
                }
                val status = parts[1]
                val chain = parts.drop(2).joinToString("|").trim()
                // 从 chain 中提取最后一条状态文本作为 lastState
                val lastState = extractLastStateFromChain(chain)
                PlanItemView(name = name, status = status, chain = chain, itemId = itemId, lastState = lastState)
            }.toMutableList()
        }
        val active = parse(activeLines)
        val ended = parse(endedLines)

        // 构建查找映射——优先 itemId 匹配，回退 name 匹配
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
                // 防止旧任务复活——已结束事项不被非终态输出重新激活
                if (fromEnded && status != "已完成" && status != "已取消") {
                    // 事项在 ended 区，本轮输出是非终态 → 不复活
                    // 只有当 item 携带新的真实证据来源（sourceIds 非空）时才允许复活
                    if (item.sourceIds.isEmpty()) {
                        com.lovebrain.app.util.L.w("skipping revive of ended item '$name' without new evidence")
                        continue
                    }
                    com.lovebrain.app.util.L.w("reviving ended item '$name' with new evidence")
                }

                // 幂等检查——相同 itemId + 相同 state 文本 → 不追加
                if (existing.lastState == state) {
                    com.lovebrain.app.util.L.w("idempotent skip for '$name' (same state)")
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

                // 有实质变化 → 追加 delta
                existing.status = status
                val oldChain = existing.chain.replace("（当前）", "").trimEnd('→', ' ')
                // 状态链截断——只保留最近 MAX_CHAIN_STATES 条状态
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
                // 新事项——分配稳定 ID
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
     * 从状态链中提取最后一条状态文本（去掉时间戳和（当前）标记）。
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
     * 截断状态链——只保留最近 MAX_CHAIN_STATES 条状态。
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
     * 从事项名称派生稳定 ID。
     * 使用 name 的稳定 hash，不随时间变化。
     */
    private fun deriveItemId(name: String): String {
        val cleaned = name.trim().lowercase().replace(Regex("\\s+"), "")
        return "item_${cleaned.hashCode().toString(16)}"
    }

    /** 职责3（自 mergeOngoing 拆出）：把进行中/已结束两区渲染为 plan.md 文本
     * 在事项行首插入 itemId，格式: itemId|name|status|chain */
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
     * 增加 itemId（稳定身份）和 lastState（上一轮状态文本），用于幂等更新检测 */
    private data class PlanItemView(
        val name: String,
        var status: String,
        var chain: String,
        val itemId: String = "",      // 稳定身份 ID
        var lastState: String = ""    // 上一轮状态文本（用于检测实质变化）
    )

    companion object {
        /** 固定填充词：命中这些 state → skip 不写 plan（避免重复堆积） */
        private val FILLER_STATES = setOf(
            "本轮未提及", "本轮无进展",
            "本轮未提及，持续推进", "本轮无进展，持续推进",
            "本轮未提及，事项持续推进中", "本轮无进展，事项持续推进中"
        )

        /** 活动状态链最大保留条数——超出截断，防膨胀 */
        private const val MAX_CHAIN_STATES = 10
    }
}
