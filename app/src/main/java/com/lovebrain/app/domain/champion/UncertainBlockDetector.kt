package com.lovebrain.app.domain.champion

/**
 * 未定/待确认事项检测与注入块（T4 Kotlin 移植 §1.3，W16 增量）——
 * `candidates/window_expand_b_norecap_uc/mechanism/uncertain_rules.py` 全文件逐字移植。
 * 纯函数，零模型调用；无未定项返回空串（prompt 与无该机制的父本逐字节相同）。
 *
 * 三类结构信号（不按故事词表枚举）：
 *  A 转述/推测型：hedge 语气 + 实体锚点或第三方主体；
 *  B 未确认问句：问号 + 世界状态动词 + 实体锚点或第三方主体；
 *  C 引号转述：「」/“”/'' 内的第三方原话，且含世界状态动词或实体锚点。
 * 三条都必须"未被后续确认事件关闭"才算未定（确认线索 + 同锚点或二元组重合 ≥0.34）。
 *
 * 不移植项（规格书 §4.1）：uc_attr（归因锚定）KEEP_NO_PROMOTE——本文件不含任何
 * attribution 字段。注入块 ≤ [UNCERTAIN_BUDGET_CU]，只列逐字原文，不改写。
 */
object UncertainBlockDetector {

    const val UNCERTAIN_TITLE = "# 【未定/待确认事项：尚未核实，不得当作已发生】"
    const val UNCERTAIN_FOOTER =
        ("口径：以上事项只能用\"还不确定/等你确认\"表述；不得替对方或第三方补全金额、" +
                "时间、行为，也不得据此改写已定条款。")
    const val UNCERTAIN_BUDGET_CU = 120
    const val MAX_ITEMS = 3

    private val HEDGE_RE = Regex(
        "如果|要是|没准|说不定|可能|好像|听说|据说|让我问|回头问|再看|" +
            "还不确定|先别|等着瞧|有没有|能不能"
    )
    private val WORLD_RE = Regex(
        "转(账)?|收到|到货|到家|到了|买|下单|定(下来|好)?|答应|同意|说好|" +
            "弄好|装好|回(你|我|复)|答复|去|来|完成|退|换|修|寄|发|登记|付款|付钱|交"
    )
    private val ANCHOR_RE = Regex(
        "\\d|[零一二两三四五六七八九十百千万]+|今天|明天|后天|周末|" +
            "周[一二三四五六日天]|[0-9]{1,2}月[0-9]{1,2}"
    )
    private val THIRD_RE = Regex(
        "他|她|对方|同事|朋友|领导|老板|家人|亲戚|师傅|客服|店员|店长|邻居|同学|室友|" +
            "表弟|表妹|表哥|表姐|舅舅|伯|姨|叔|妈|爸|舅|外婆|姥姥|爷爷|奶奶"
    )
    private val CONFIRM_RE = Regex(
        "定了|确认|已经|收到|装好|到了|改好|答复|说好|登记|当场|敲定|定下来|" +
            "办好了|完成|同意|付了|转了|买了"
    )
    private val QUOTE_RE = Regex("[「“‘']([^」”’']{2,})[」”’']")
    private val ASK_RE = Regex(
        "[?？]|是不是|该不该|要不要|退不退|行不行|能不能|你看|你说|帮我|让我|怎么弄|咋办"
    )
    private val ANSWER_RE = Regex("老板说|客服说|他说|她说|答复|证实|查了|问了|结果|后来")
    private val DECISION_RE = Regex("^(不|别|就|必须|一定|不用|不要)|不退|不要了|不必|甭")
    private const val CLAUSE_SEP = "，。！？；\n"
    private val PUNCT_SET = "，。！？；：、“”「」\"'（）()《》<> \t\n\r-—…·+".toSet()
    private val JUDGE_RE = Regex("觉得|认为|说|讲|嫌|担心|怕|听说|抱怨")

    /** CU 计量：Kotlin String.length = UTF-16 码元数（与 Python utf16_len 等价）。 */
    fun utf16Len(s: String): Int = (s ?: "").length

    private fun norm(t: String?): String =
        (t ?: "").filterNot { it in PUNCT_SET }

    private fun bigrams(t: String?): Set<String> {
        val n = norm(t)
        if (n.length < 2) return emptySet()
        return (0 until n.length - 1).mapTo(HashSet()) { n.substring(it, it + 2) }
    }

    /** bigram 重合度：交集 / 较短一方中的 |A|（关闭判定口径，阈值 0.34）。 */
    fun overlap(a: String, b: String): Double {
        val (A, B) = bigrams(a) to bigrams(b)
        if (A.isEmpty() || B.isEmpty()) return 0.0
        return (A intersect B).size.toDouble() / maxOf(1, A.size)
    }

    /** containment 口径：交集 / 较短一方（场上判定，阈值 0.50）。 */
    fun overlapCont(a: String, b: String): Double {
        val (A, B) = bigrams(a) to bigrams(b)
        if (A.isEmpty() || B.isEmpty()) return 0.0
        return (A intersect B).size.toDouble() / maxOf(1, minOf(A.size, B.size))
    }

    private fun anchors(t: String?): Set<String> =
        ANCHOR_RE.findAll(t ?: "").mapTo(HashSet()) { it.value }

    /** 按中文标点切 clause，返回 (clause, is_question)。 */
    fun splitClauses(text: String?): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        val buf = StringBuilder()
        for (ch in (text ?: "")) {
            if (ch in CLAUSE_SEP) {
                if (buf.isNotBlank()) out += buf.trim().toString() to (ch == '？' || ch == '?')
                buf.clear()
            } else {
                buf.append(ch)
            }
        }
        if (buf.isNotBlank()) out += buf.trim().toString() to false
        return out
    }

    data class UncertainItem(
        val text: String,
        val where: String,   // "本轮" 或 "T<turn>"
        val kind: String,    // "hedge" | "question" | "quote"
        val seq: Int
    )

    private fun isCandidate(clause: String, isQuestion: Boolean): String? {
        // hedge 也须有"实体锚点"或"第三方+世界/判断动词"，否则"你要是去""回头问你"这类
        // 纯条件/客套会被误当未定事项（设计修订 R3）。
        if (HEDGE_RE.containsMatchIn(clause) && (
                ANCHOR_RE.containsMatchIn(clause) ||
                    (THIRD_RE.containsMatchIn(clause) &&
                        (WORLD_RE.containsMatchIn(clause) || JUDGE_RE.containsMatchIn(clause)))
                )
        ) return "hedge"
        if (isQuestion && WORLD_RE.containsMatchIn(clause) &&
            (ANCHOR_RE.containsMatchIn(clause) || THIRD_RE.containsMatchIn(clause))
        ) return "question"
        return null
    }

    private fun closed(clause: String, laterTexts: List<String>): Boolean {
        // 后续事件含确认线索，且同锚点（金额/日期）或二元组重合 ≥0.34 → 已关闭。
        // 无实体连续性的"确认"不算关闭（保守：宁可多注入，也不把没确认的事当已确认）。
        for (t in laterTexts) {
            if (!CONFIRM_RE.containsMatchIn(t)) continue
            if ((anchors(clause) intersect anchors(t)).isNotEmpty()) return true
            if (overlap(clause, t) >= 0.34) return true
        }
        return false
    }

    private fun relevant(itemText: String, curTexts: List<String>): Boolean {
        // 未定项与本轮是否"在场上"：同锚点、同第三方、或 containment ≥0.50。
        // 不在场上就不注入（防未定项滚存成常驻噪音；设计修订 R3）。
        for (t in curTexts) {
            if ((anchors(itemText) intersect anchors(t)).isNotEmpty()) return true
            val third = THIRD_RE.findAll(itemText).map { it.value }.toSet() intersect
                THIRD_RE.findAll(t).map { it.value }.toSet()
            if (third.isNotEmpty()) return true
            if (overlapCont(itemText, t) >= 0.50) return true
        }
        return false
    }

    private fun itemsOfMessage(
        text: String?,
        kindWhere: String,
        laterTexts: List<String>,
        askNow: Boolean
    ): List<UncertainItem> {
        val out = mutableListOf<UncertainItem>()
        for ((c, q) in splitClauses(text)) {
            val kind = isCandidate(c, q)
            if (kind != null && !closed(c, laterTexts)) {
                out += UncertainItem(text = c, where = kindWhere, kind = kind, seq = 0)
            }
        }
        // C 引号转述：仅当同一消息在"征询判断/决定"（问句或该不该/退不退…），
        // 且引号之后没有当场答复（老板说/答复/查了…）时才注入——避免把赞美、已定决定、
        // 已核实的条款当成未定（设计修订 R4）。
        if (ASK_RE.containsMatchIn(text ?: "") || askNow) {
            for (m in QUOTE_RE.findAll(text ?: "")) {
                val span = m.groupValues[1]
                if (!(WORLD_RE.containsMatchIn(span) || ANCHOR_RE.containsMatchIn(span))) continue
                if (DECISION_RE.containsMatchIn(span.trim())) continue   // 引号内是已定决定/指令
                if (ANSWER_RE.containsMatchIn((text ?: "").substring(m.range.last + 1))) continue
                if (closed(span, laterTexts)) continue
                // 长引语按 clause 拆分，只取含世界动词/锚点的短句（保证 ≤120 CU 内仍能入场）
                val parts = splitClauses(span)
                    .map { it.first }
                    .filter { WORLD_RE.containsMatchIn(it) || ANCHOR_RE.containsMatchIn(it) }
                for (c in (parts.take(2).ifEmpty { listOf(span.trim()) })) {
                    out += UncertainItem(text = c.trim(), where = kindWhere, kind = "quote", seq = 0)
                }
            }
        }
        return out
    }

    /** 返回未定条目（新→旧，最多 [MAX_ITEMS]）。 */
    fun findUncertain(
        priorEvents: List<ChampionEvent>,
        curMsgs: List<ChampionEvent>,
        curTurn: Int
    ): List<UncertainItem> {
        data class Raw(val item: UncertainItem, val sortSeq: Int)

        val items = mutableListOf<Raw>()
        val curTextsAll = curMsgs.joinToString(" ") { it.text }
        val askNow = ASK_RE.containsMatchIn(curTextsAll)
        for (m in curMsgs) {
            for (it in itemsOfMessage(m.text, "本轮", emptyList(), askNow)) {
                items += Raw(it.copy(seq = priorEvents.size + 1), priorEvents.size + 1)
            }
        }
        for ((i, m) in priorEvents.withIndex()) {
            val later = priorEvents.drop(i + 1).map { it.text } + curMsgs.map { it.text }
            for (it in itemsOfMessage(m.text, "T${m.turn}", later, askNow)) {
                items += Raw(it.copy(seq = i + 1), i + 1)
            }
        }
        val curTexts = curMsgs.map { it.text }
        val seen = HashSet<String>()
        val out = mutableListOf<UncertainItem>()
        // 稳定排序：seq 降序，同 seq 保持原相对序（与 Python sorted(..., reverse=True) 一致）
        for (raw in items.sortedByDescending { it.sortSeq }) {
            val it = raw.item
            val key = norm(it.text)
            if (key in seen) continue
            seen += key
            // R3 相关性过滤：本轮条目恒在场上；历史条目须与本轮同锚点/同第三方/高重合
            if (it.where != "本轮" && !relevant(it.text, curTexts)) continue
            out += it
            if (out.size >= MAX_ITEMS) break
        }
        return out
    }

    data class UncertainResult(val block: String, val items: List<UncertainItem>, val cu: Int)

    /** 无未定项 → ("", [], 0)：prompt 与父本逐字节相同（对拍验收项）。 */
    fun buildBlock(
        priorEvents: List<ChampionEvent>,
        curMsgs: List<ChampionEvent>,
        curTurn: Int
    ): UncertainResult {
        val items = findUncertain(priorEvents, curMsgs, curTurn)
        if (items.isEmpty()) return UncertainResult("", emptyList(), 0)
        val head = UNCERTAIN_TITLE
        val foot = UNCERTAIN_FOOTER
        var used = utf16Len(head) + utf16Len(foot) + 2
        val lines = mutableListOf<String>()
        for (it in items) {
            val l = "- 「${it.text}」（${it.where}）"
            val add = utf16Len(l) + 1
            if (used + add > UNCERTAIN_BUDGET_CU) break
            lines += l
            used += add
        }
        if (lines.isEmpty()) return UncertainResult("", emptyList(), 0)
        val block = head + "\n" + lines.joinToString("\n") + "\n" + foot
        return UncertainResult(block, items.take(lines.size), utf16Len(block))
    }
}
