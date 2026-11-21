package com.lovebrain.app.domain.champion

/**
 * 历史真实消息段装配（T4 Kotlin 移植，delta ①）——逐字移植
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
 * emoji/代理对样例在单测钉死。
 *
 * 这是**一套独立的上下文组织哲学**（事件有序整条预算），与产品 9000 字符
 * `PromptBudget.byBlocks` 互斥：冠军链路一次都不调 `byBlocks`，
 * 3600 CU 预算自己的责任边界自己兜。
 */
object EventHistoryAssembler {

    /** delta ①：历史真实消息段的 UTF-16 码元预算 1400 → 3600。 */
    const val MAX_EVENT_HISTORY_CU = 3600

    /** 单条旧史即超上限时整段省略的占位句（逐字）。 */
    const val OVERSIZED_PLACEHOLDER =
        "（最近一条旧史单独超出历史预算，本段省略；当前输入在下方完整保留）"

    /** 历史行（照 `_line` 逐字；HER→PARTNER，其余→USER；正文先过 [sanitize]）。 */
    fun line(m: ChampionEvent): String =
        "m_src[${m.uid}] | ${m.ts} | ${if (m.role == "HER") "PARTNER" else "USER"} | ${sanitize(m.text)}"

    /**
     * 历史正文是**不可信第三方文本**：原样拼进 prompt 时，一个换行就能伪造出
     * 一条新的 `m_src[… ]` 来源行、一个 `# 【…】` 段头或一对 `<chat>` 围栏。
     * 历史行必须是单行、且不携带这些保留 token——所以换行一律折成空格（顺序不变、
     * 仍可读），保留 token 用全角字符打断（不改写其余正文字符）。正常聊天字节不变。
     */
    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        var out = text
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace("\u2028", " ")
            .replace("\u2029", " ")
            .replace("\u0085", " ")
        if (out.contains("</chat>") || out.contains("<chat>")) {
            out = out.replace("</chat>", "＜/chat＞").replace("<chat>", "＜chat＞")
        }
        if (out.contains("# 【")) out = out.replace("# 【", "＃ 【")
        if (out.contains("m_src[")) out = out.replace("m_src[", "m_src［")
        return out
    }

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
