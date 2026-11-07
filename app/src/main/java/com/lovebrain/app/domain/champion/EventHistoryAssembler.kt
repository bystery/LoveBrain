package com.lovebrain.app.domain.champion

/**
 * 历史真实消息段装配（T4 Kotlin 移植 §1.2，delta ①）——逐字移植
 * `mechanism.py::_budget_history` / `_line`。
 *
 * 逐字规则：
 * - 行格式 `"m_src[%d] | %s | %s | %s".format(uid, ts, PARTNER|USER, text)`；
 * - 从最新往旧**整条**累加（每条 `utf16Len(line)+1`），越界即停；
 * - 连最近一条都放不下 → 省略整段旧史，输出占位句
 *   `（最近一条旧史单独超出历史预算，本段省略；当前输入在下方完整保留）`；
 * - **不切代理对、不压缩、无 recap、无相关性排序**。
 *
 * CU 计量：Kotlin `String.length` 天然 = UTF-16 码元数，与 Python
 * `rc.utf16_len`（`encode('utf-16-le')//2`）等价——不另写计数器；
 * emoji/代理对样例在单测钉死（规格书 §1.2）。
 *
 * 这是**一套独立的上下文组织哲学**（事件有序整条预算），与产品 9000 字符
 * `PromptBudget.byBlocks` 互斥：冠军链路一次都不调 `byBlocks`，
 * 3600 CU 预算自己的责任边界自己兜（规格书 §2.1）。
 */
object EventHistoryAssembler {

    /** delta ①：历史真实消息段的 UTF-16 码元预算 1400 → 3600。 */
    const val MAX_EVENT_HISTORY_CU = 3600

    /** 单条旧史即超上限时整段省略的占位句（逐字）。 */
    const val OVERSIZED_PLACEHOLDER =
        "（最近一条旧史单独超出历史预算，本段省略；当前输入在下方完整保留）"

    /** 历史行（照 `_line` 逐字；HER→PARTNER，其余→USER）。 */
    fun line(m: ChampionEvent): String =
        "m_src[${m.uid}] | ${m.ts} | ${if (m.role == "HER") "PARTNER" else "USER"} | ${m.text}"

    data class HistoryBudget(
        val body: String,
        val usedCu: Int,
        val dropped: List<ChampionEvent>
    )

    /**
     * 整条消息为单位，从最新往旧累加；越界即停。
     * [dropped] 是被裁掉的消息列表（arena delta ② 已删，这里只做完整性与对账用）。
     */
    fun budgetHistory(
        events: List<ChampionEvent>,
        budget: Int = MAX_EVENT_HISTORY_CU
    ): HistoryBudget {
        val lines = mutableListOf<String>()
        var used = 0
        var omitted = false
        val keptUids = HashSet<Long>()
        for (m in events.reversed()) {
            val l = line(m)
            val add = l.length + 1
            if (used + add > budget) {
                if (lines.isEmpty()) omitted = true   // 单条旧史即超上限：省略之
                break
            }
            lines += l
            keptUids += m.uid
            used += add
        }
        val body = lines.reversed().joinToString("\n")
        val finalBody = if (omitted && body.isEmpty()) OVERSIZED_PLACEHOLDER else body
        val dropped = events.filter { it.uid !in keptUids }
        return HistoryBudget(finalBody, used, dropped)
    }
}
