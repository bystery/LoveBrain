package com.lovebrain.app.domain.champion

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * 确定性输出契约守卫（T4 Kotlin 移植 §1.5）——逐字移植
 * `window_expand_b_norecap_guard/mechanism/contract_guard.py` +
 * `_vendor/output_repair.py` + `_vendor/normalize_reply_contract.py`（内聚为一个文件，
 * 三层规则顺序固定，后层作用于前层结果；零模型调用）。
 *
 * ## 三层规则（注释保留移植来源）
 *
 * 1. **解析兜底（vendor I1–I4 + E1–E3）**：剥 markdown 围栏 → 弯/全角引号按序换直引号
 *    重试 → 抽最外层对象；键别名映射（规范槽非空绝不覆盖，别名值全文进
 *    `unmapped_preserved`）；directions 保全（多出整条进 `extra_directions_preserved`）；
 *    缺槽补 null 登记；E1 通用键名归一（去空白/不可见字符后比对）；E2 提前闭合重拼
 *    （实测样本 AK02_T01）；E3 多余包裹对象拆开（实测样本 N01_T05，五条件全过才只动括号）。
 * 2. **漂移族补修（guard 层，W17 观察族）**：未知风格键按字符相似度 ≥0.5 映射到
 *    recommended/bad_boy/playful/warm（仅目标槽空时；**见下方 FUZZY_MAP_ENABLED**）；
 *    response 少 4 槽补 null（不编造）；directions 截 4/补 null（显式 action=no_reply 不补）；
 *    顶层 action↔response 互改（反向只认 action 形状）；空 response 只标
 *    `empty_response` 原样返回，不伪造内容。
 * 3. **输出纪律**：只有对象相对原文实际变化才重新序列化（非 ASCII 不转义，
 *    对齐 `json.dumps(..., ensure_ascii=False)`）；否则**逐字节返回原文本**。
 *    每次变化写审计（turn/meta 全字段）到 `champion/contract_guard_audit.jsonl`；
 *    守卫自身异常 → 降级原样回复并记 `guard_exception`。**零模型调用。**
 *
 * ## 已登记的移植偏差（详见 W1_CHAMPION_ENGINE_IMPL.md）
 * - Python `difflib.SequenceMatcher.ratio()` 无 JDK 等价物：本文件移植了同算法
 *   （最长匹配块递归，2.0*M/T，含 autojunk）。**规格书 §1.5：未与 Python 夹具对拍前
 *   不得启用 fuzzy_map**——[FUZZY_MAP_ENABLED] 默认 false，未启用时未知风格键走
 *   保守路径 `unmapped_style_removed`（与相似度低于阈值的形状一致）。
 * - Python `json.loads` 接受 NaN/Infinity 等非严格输入：MiniJson 不接受（契约字段
 *   只会是字符串/null/数字，非严格形状按 unparseable 保守处理）。
 * - 数字序列化保留原词法（Python 会把 `1e2` 规范成 `100.0`；契约槽位数字罕见）。
 */
class ContractGuard(private val auditSink: GuardAuditSink? = null) {

    // ═══════════ 常量（照 Python 逐字） ═══════════

    private val CANON = listOf("recommended", "bad_boy", "playful", "warm")
    private val DIRECTIONS_LEN = 4
    private val STYLE_SIM_THRESHOLD = 0.5
    private val STRIP_CHARS = " \t\r\n\u200b\u200c\u200d\ufeff\u00a0".toSet()
    private val ACTION_HINT_KEYS = listOf("action", "decline_reason", "reason")
    private val STYLE_KEY_ALIASES = mapOf(
        "bad_boys" to "bad_boy", "bad_boy " to "bad_boy", "playfull" to "playful",
        "recomended" to "recommended", "recommned" to "recommended", "warm " to "warm"
    )
    private val CURLY = listOf(
        "\u201c" to "\"", "\u201d" to "\"", "\u2018" to "'",
        "\uff02" to "\"", "\uff07" to "'"
    )
    private val INVISIBLE = setOf("\u200b", "\u200c", "\u200d", "\ufeff", "\u00a0", " ", "\t", "\r", "\n")

    /** 未对拍前整体关闭（规格书 §1.5 风险登记）；关闭时走 unmapped_style_removed 保守路径。 */
    private val FUZZY_MAP_ENABLED = false

    // ═══════════ MiniJson：带位置的解析 + Python 风格序列化 ═══════════

    sealed class JVal
    class JObj(val entries: LinkedHashMap<String, JVal>) : JVal() {
        override fun equals(other: Any?) = other is JObj && other.entries == entries
        override fun hashCode() = entries.hashCode()
    }
    class JArr(val items: MutableList<JVal>) : JVal() {
        override fun equals(other: Any?) = other is JArr && other.items == items
        override fun hashCode() = items.hashCode()
    }
    data class JStr(val value: String) : JVal()
    data class JNum(val lexeme: String) : JVal()
    data class JBool(val value: Boolean) : JVal()
    object JNull : JVal()

    private class JsonParseException(msg: String) : Exception(msg)

    private fun skipWs(text: String, i: Int): Int {
        var j = i
        while (j < text.length && text[j] in " \t\n\r") j++
        return j
    }

    /** 等价 Python `JSONDecoder.raw_decode(text, pos)`：返回 (值, 结束位置)。 */
    private fun rawDecode(text: String, pos: Int): Pair<JVal, Int> {
        var i = skipWs(text, pos)
        if (i >= text.length) throw JsonParseException("Expecting value at $i")
        return when (text[i]) {
            '{' -> parseObject(text, i)
            '[' -> parseArray(text, i)
            '"' -> {
                val (s, e) = parseString(text, i); JStr(s) to e
            }
            't' -> expectLiteral(text, i, "true", JBool(true))
            'f' -> expectLiteral(text, i, "false", JBool(false))
            'n' -> expectLiteral(text, i, "null", JNull)
            else -> parseNumber(text, i)
        }
    }

    private fun expectLiteral(text: String, i: Int, lit: String, v: JVal): Pair<JVal, Int> {
        if (!text.startsWith(lit, i)) throw JsonParseException("invalid literal at $i")
        return v to (i + lit.length)
    }

    private fun parseObject(text: String, start: Int): Pair<JVal, Int> {
        val entries = LinkedHashMap<String, JVal>()
        var i = skipWs(text, start + 1)
        if (i < text.length && text[i] == '}') return JObj(entries) to (i + 1)
        while (true) {
            i = skipWs(text, i)
            if (i >= text.length || text[i] != '"') throw JsonParseException("Expecting key at $i")
            val (k, ke) = parseString(text, i)
            i = skipWs(text, ke)
            if (i >= text.length || text[i] != ':') throw JsonParseException("Expecting : at $i")
            val (v, ve) = rawDecode(text, i + 1)
            entries[k] = v
            i = skipWs(text, ve)
            if (i < text.length && text[i] == ',') { i++; continue }
            if (i < text.length && text[i] == '}') return JObj(entries) to (i + 1)
            throw JsonParseException("Expecting , or } at $i")
        }
    }

    private fun parseArray(text: String, start: Int): Pair<JVal, Int> {
        val items = mutableListOf<JVal>()
        var i = skipWs(text, start + 1)
        if (i < text.length && text[i] == ']') return JArr(items) to (i + 1)
        while (true) {
            val (v, ve) = rawDecode(text, i)
            items += v
            i = skipWs(text, ve)
            if (i < text.length && text[i] == ',') { i++; continue }
            if (i < text.length && text[i] == ']') return JArr(items) to (i + 1)
            throw JsonParseException("Expecting , or ] at $i")
        }
    }

    private fun parseString(text: String, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> return sb.toString() to (i + 1)
                c == '\\' -> {
                    i++
                    if (i >= text.length) throw JsonParseException("bad escape")
                    when (val e = text[i]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'b' -> sb.append('\b'); 'f' -> sb.append('')
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 >= text.length) throw JsonParseException("bad \\u escape")
                            val hex = text.substring(i + 1, i + 5)
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> throw JsonParseException("bad escape \\$e")
                    }
                    i++
                }
                (c.code < 0x20) -> throw JsonParseException("unescaped control char at $i")
                else -> { sb.append(c); i++ }
            }
        }
        throw JsonParseException("unterminated string")
    }

    private val NUMBER_RE = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    private fun parseNumber(text: String, start: Int): Pair<JVal, Int> {
        val m = NUMBER_RE.matchAt(text, start)
            ?: throw JsonParseException("Expecting value at $start")
        return JNum(m.value) to (start + m.value.length)
    }

    /** Python `json.dumps(v, ensure_ascii=False)` 等价序列化（`, ` 与 `: ` 分隔）。 */
    fun dumps(v: JVal): String {
        val sb = StringBuilder()
        writeVal(sb, v)
        return sb.toString()
    }

    private fun writeVal(sb: StringBuilder, v: JVal) {
        when (v) {
            is JNull -> sb.append("null")
            is JBool -> sb.append(if (v.value) "true" else "false")
            is JNum -> sb.append(v.lexeme)
            is JStr -> writeString(sb, v.value)
            is JArr -> {
                sb.append('[')
                v.items.forEachIndexed { idx, item ->
                    if (idx > 0) sb.append(", ")
                    writeVal(sb, item)
                }
                sb.append(']')
            }
            is JObj -> {
                sb.append('{')
                var first = true
                for ((k, item) in v.entries) {
                    if (!first) sb.append(", ")
                    first = false
                    writeString(sb, k)
                    sb.append(": ")
                    writeVal(sb, item)
                }
                sb.append('}')
            }
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch.code < 0x20 -> sb.append("\\u%04x".format(ch.code))
                else -> sb.append(ch)   // 非 ASCII 不转义（ensure_ascii=False）
            }
        }
        sb.append('"')
    }

    /**
     * 严格全量解析（等价 Python `json.loads`：拒绝尾部多余数据——这正是 E2 存在的原因）。
     * 返回非 dict JSON 形状按解析失败处理（登记偏差：契约字段只会是对象，非对象形状
     * Python 侧走 not_object 留损，本侧按 unparseable 保守处理，对外行为一致）。
     */
    private fun loadsDict(text: String): JObj? = try {
        val (v, end) = rawDecode(text, 0)
        if (skipWs(text, end) == text.length) v as? JObj else null
    } catch (_: Exception) { null }

    // ═══════════ difflib.SequenceMatcher.ratio() 的 Kotlin 移植（2.0*M/T） ═══════════

    /**
     * 与 Python `difflib.SequenceMatcher(None, a, b).ratio()` 同算法：
     * 最长匹配块递归求和 → 2.0*M/T。autojunk（b 长度 ≥200 时高频元素当垃圾）
     * 一并移植；本守卫只在 ≤几十字符的风格键上调用，对拍夹具据此构造。
     */
    internal fun sequenceMatcherRatio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val bChars = b.toCharArray().toList()
        val b2j = HashMap<Char, MutableList<Int>>()
        bChars.forEachIndexed { idx, ch -> b2j.getOrPut(ch) { mutableListOf() }.add(idx) }
        // autojunk：b 长度 ≥200 时出现次数 > 1% 的元素视为垃圾（Python 同判据）
        if (bChars.size >= 200) {
            val ntest = bChars.size / 100 + 1
            val popular = b2j.filterValues { it.size > ntest }.keys
            popular.forEach { b2j.remove(it) }
        }
        val aChars = a.toCharArray()
        var total = 0
        val queue = ArrayDeque<IntArray>().apply { add(intArrayOf(0, a.length, 0, b.length)) }
        while (queue.isNotEmpty()) {
            val (alo, ahi, blo, bhi) = queue.removeFirst()
            val lm = findLongestMatch(aChars, b2j, alo, ahi, blo, bhi)
            if (lm[2] > 0) {
                total += lm[2]
                if (alo < lm[0] && blo < lm[1]) queue.add(intArrayOf(alo, lm[0], blo, lm[1]))
                if (lm[0] + lm[2] < ahi && lm[1] + lm[2] < bhi) {
                    queue.add(intArrayOf(lm[0] + lm[2], ahi, lm[1] + lm[2], bhi))
                }
            }
        }
        return 2.0 * total / (a.length + b.length)
    }

    /** SequenceMatcher.find_longest_match（无 junk 参数版本；autojunk 已并入 b2j）。 */
    private fun findLongestMatch(
        a: CharArray,
        b2j: Map<Char, List<Int>>,
        alo: Int, ahi: Int, blo: Int, bhi: Int
    ): IntArray {
        val j2len = HashMap<Int, Int>()
        var besti = alo; var bestj = blo; var bestsize = 0
        for (i in alo until ahi) {
            val newj2len = HashMap<Int, Int>()
            for (j in b2j[a[i]].orEmpty()) {
                if (j < blo || j >= bhi) continue
                val k = (j2len[j - 1] ?: 0) + 1
                newj2len[j] = k
                if (k > bestsize) { besti = i - k + 1; bestj = j - k + 1; bestsize = k }
            }
            j2len.clear(); j2len.putAll(newj2len)
        }
        return intArrayOf(besti, bestj, bestsize)
    }

    // ═══════════ 第一层：vendor 解析兜底（I1–I4 + E1–E3） ═══════════

    /** I1：剥围栏 → 弯/全角引号按序换直引号重试 → 抽最外层对象。返回 (obj, repairs)。 */
    private fun repairJson(text: String?): Pair<JObj?, List<String>> {
        val repairs = mutableListOf<String>()
        if (text.isNullOrEmpty()) return null to listOf("empty_raw")
        var t = text.trim()
        if (t.startsWith("```")) {
            t = Regex("^```[a-zA-Z]*").replace(t, "").trim()
            t = Regex("```\\r?\\n?$").replace(t, "").trim()   // Python ```$ 含结尾换行形状
            repairs += "strip_markdown_fence"
        }
        var obj = loadsDict(t)
        if (obj == null) {
            var cur = t
            for ((a, b) in CURLY) {
                if (a in cur) {
                    val trial = cur.replace(a, b)
                    val o2 = loadsDict(trial)
                    if (o2 != null) {
                        cur = trial
                        repairs += "quote_normalize:${dumps(JStr(a))}->${dumps(JStr(b))}"
                        obj = o2
                        break
                    }
                }
            }
            if (obj == null) {
                val m = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(cur)
                if (m != null) {
                    val o3 = loadsDict(m.value)
                    if (o3 != null) { obj = o3; repairs += "extract_outer_object" }
                }
            }
            if (obj == null) return null to (repairs + "unparseable")
        }
        return obj to repairs
    }

    private fun slotEmpty(v: JVal?): Boolean =
        v == null || v == JNull || (v is JStr && v.value.isBlank())

    private fun cleanKey(k: String): String =
        k.filterNot { it in STRIP_CHARS }.trim().lowercase()

    /** E1：把被写坏的 style 键名映射回规范名（去不可见字符后的全等/前缀/包含）。 */
    private fun canonKey(k: String): String? {
        if (k in CANON) return k
        val cleaned = cleanKey(k)
        if (cleaned in CANON) return cleaned
        for (c in CANON) {
            if (cleaned.isNotEmpty() && (c.startsWith(cleaned) || cleaned.startsWith(c))) return c
        }
        return null
    }

    private fun auditEntry(vararg pairs: Pair<String, JsonElement>): JsonObject =
        buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }

    private fun lossEntry(rule: String, detail: String): JsonObject =
        buildJsonObject { put("rule", rule); put("detail", detail) }

    private fun lossEntry(rule: String, vararg pairs: Pair<String, JsonElement>): JsonObject =
        buildJsonObject { put("rule", rule); pairs.forEach { (k, v) -> put(k, v) } }

    private fun jvalToJson(v: JVal): JsonElement = when (v) {
        is JNull -> JsonNull
        is JBool -> JsonPrimitive(v.value)
        is JNum -> v.lexeme.toLongOrNull()?.let { JsonPrimitive(it) }
            ?: v.lexeme.toDoubleOrNull()?.let { JsonPrimitive(it) }
            ?: JsonPrimitive(v.lexeme)
        is JStr -> JsonPrimitive(v.value)
        is JArr -> JsonArray(v.items.map { jvalToJson(it) })
        is JObj -> JsonObject(v.entries.mapValues { jvalToJson(it.value) })
    }

    /** 字符串状态感知的栈扫描：把"在 [ 内部用 } 收尾"的那一个括号改成 ]（至多 1 处）。 */
    private fun normalizeOneMismatchedCloser(text: String): Pair<String?, Int> {
        val stack = ArrayDeque<Char>()
        val out = text.toCharArray()
        var n = 0
        var inStr = false
        var esc = false
        for (idx in text.indices) {
            val ch = text[idx]
            if (inStr) {
                when {
                    esc -> esc = false
                    ch == '\\' -> esc = true
                    ch == '"' -> inStr = false
                }
                continue
            }
            when (ch) {
                '"' -> inStr = true
                '[', '{' -> stack.addLast(ch)
                ']', '}' -> {
                    if (stack.isEmpty()) return null to 0
                    val op = stack.removeLast()
                    val need = if (op == '[') ']' else '}'
                    if (ch == need) continue
                    if (op == '[' && ch == '}') {        // 唯一的窄情形：[ 被 } 收尾
                        out[idx] = ']'
                        n++
                        if (n > 1) return null to n
                        continue
                    }
                    return null to n                     // 其它错配一律不猜
                }
            }
        }
        return String(out) to n
    }

    /** E3：拆开 `...],{"analysis":{...}}` 里多余的一层花括号（五条件全过才只动括号）。 */
    private fun tryUnwrapStrayObject(rawText: String): Pair<String?, List<JsonObject>?> {
        val analysisPrefix = Regex("\\{\\s*\"analysis\"\\s*:")
        for (i in rawText.indices) {
            if (rawText[i] != ',') continue
            var j = i + 1
            while (j < rawText.length && rawText[j] in " \n\r\t") j++
            if (j >= rawText.length || rawText[j] != '{') continue
            if (analysisPrefix.find(rawText.substring(j, minOf(rawText.length, j + 32)))?.range?.first != 0) {
                continue
            }
            val (valv, end) = try { rawDecode(rawText, j) } catch (_: Exception) { continue }
            if (valv !is JObj || valv.entries["analysis"] == null) continue
            val tail = rawText.substring(end).trim()
            if (tail != "" && tail != "}") continue        // 只允许末尾还剩一个顶层右括号
            val prefix = rawText.substring(0, i + 1)
            val (fixedPrefix, nFix) = normalizeOneMismatchedCloser(prefix)
            if (nFix > 1 || fixedPrefix == null) continue
            val actions = mutableListOf<JsonObject>()
            if (nFix == 1) {
                actions += lossEntry(
                    "mismatched_closer_normalized",
                    "detail" to JsonPrimitive("directions 数组的 } 归一为 ]"),
                    "chars_moved" to JsonPrimitive(1)
                )
            }
            actions += lossEntry(
                "stray_wrapper_removed",
                "cut_index" to JsonPrimitive(j),
                "detail" to JsonPrimitive("删掉 analysis 外面多的一对 { }"),
                "chars_moved" to JsonPrimitive(2)
            )
            val inner = rawText.substring(j + 1, end - 1)   // 去掉包裹对象的左右两个花括号
            val cand = fixedPrefix + inner + (if (tail.isEmpty()) "}" else tail)
            val obj = loadsDict(cand)
            if (obj != null && obj.entries.keys.containsAll(listOf("response", "directions", "analysis"))) {
                return cand to actions
            }
        }
        return null to null
    }

    /** I1 → E2 → E3 → 再走一次 I1。返回 obj 或 None；损失写进 loss。 */
    private fun parseWithInheritance(rawText: String, loss: MutableList<JsonObject>): JObj? {
        val (obj0, repairs0) = repairJson(rawText)
        for (r in repairs0) {
            if (!r.startsWith("unparseable")) loss += lossEntry("I1_inherited_parse", r)
        }
        if (obj0 != null) return obj0

        // E2：提前闭合重拼（只在继承解析失败后启用；实测样本 AK02_T01）
        try {
            val (_, end) = rawDecode(rawText, 0)
            val tail = rawText.substring(end)
            if (tail.startsWith(",")) {
                val joined = rawText.substring(0, end - 1) + tail
                val cand = rawDecode(joined, 0)
                if (cand.second == joined.length && cand.first is JObj) {
                    loss += lossEntry(
                        "E2_premature_top_level_brace",
                        "cut_index" to JsonPrimitive(end - 1),
                        "bytes_moved" to JsonPrimitive(1),
                        "detail" to JsonPrimitive("删掉 directions 之后多余的顶层 }，analysis 原样接回")
                    )
                    // 重拼后的文本再走一遍继承解析（覆盖弯引号/围栏等情况）
                    val (obj2, repairs2) = repairJson(joined)
                    if (obj2 != null) {
                        for (r in repairs2) {
                            if (!r.startsWith("unparseable")) {
                                loss += lossEntry("I1_inherited_parse_after_E2", r)
                            }
                        }
                        return obj2
                    }
                    return cand.first as JObj
                }
            }
        } catch (e: Exception) {
            loss += lossEntry("E2_failed", e.message ?: e.javaClass.simpleName)
        }

        // E3：多余包裹对象（只在 I1 与 E2 都失败后启用；只动括号，不改任何文字）
        val (unwrapped, actions) = tryUnwrapStrayObject(rawText)
        if (unwrapped != null) {
            loss += buildJsonObject {
                put("rule", "E3_stray_wrapper_object_removed")
                put("actions", JsonArray(actions.orEmpty()))
                put("detail", JsonPrimitive("拆开 directions 之后多出来的一层 { }，analysis 回到顶层"))
            }
            val (obj3, repairs3) = repairJson(unwrapped)
            if (obj3 != null) {
                for (r in repairs3) {
                    if (!r.startsWith("unparseable")) loss += lossEntry("I1_inherited_parse_after_E3", r)
                }
                return obj3
            }
            loadsDict(unwrapped)?.let { return it }
        }

        loss += lossEntry("unparseable", "I1 与 E2/E3 均失败")
        return null
    }

    /** I2/I3/I4：键别名映射 + directions 保全 + 缺槽 null 登记。 */
    private fun normalizeContract(
        obj: JObj,
        repairs: MutableList<String>,
        unmapped: MutableList<JsonObject>,
        missing: MutableList<String>
    ): JObj {
        val resp = obj.entries["response"]
        if (resp is JObj) {
            for (k in resp.entries.keys.toList()) {
                val canon = STYLE_KEY_ALIASES[k] ?: continue
                val aliasVal = resp.entries.remove(k) ?: continue
                val cur = resp.entries[canon]
                val curEmpty = slotEmpty(cur)
                val aliasEmpty = slotEmpty(aliasVal)
                when {
                    curEmpty -> {
                        resp.entries[canon] = aliasVal      // 规范槽空 → 安全映射（非空别名不丢）
                        repairs += "style_key_fix:$k->$canon"
                    }
                    aliasEmpty -> repairs += "style_key_drop_empty_dup:$k"
                    else -> {
                        unmapped += buildJsonObject {
                            put("alias_key", k); put("alias_value", jvalToJson(aliasVal))
                            put("canon_key", canon); put("canon_value", jvalToJson(cur!!))
                        }
                        repairs += "style_key_keep_unmapped:$k"
                    }
                }
            }
            for (need in CANON) {
                if (need !in resp.entries) {
                    resp.entries[need] = JNull
                    repairs += "response_slot_add_null:$need"
                    missing += "response.$need"
                } else if (resp.entries[need] == JNull) {
                    missing += "response.$need"   // 显式 null 同样如实报告
                }
            }
        }
        val dirs = obj.entries["directions"]
        if (dirs is JArr) {
            if (dirs.items.size < DIRECTIONS_LEN) {
                while (dirs.items.size < DIRECTIONS_LEN) dirs.items.add(JNull)
                repairs += "directions_pad_to_$DIRECTIONS_LEN"
            } else if (dirs.items.size > DIRECTIONS_LEN) {
                val extra = dirs.items.drop(DIRECTIONS_LEN)
                obj.entries["_audit_extra_directions"] = JArr(extra.toMutableList())
                while (dirs.items.size > DIRECTIONS_LEN) dirs.items.removeAt(dirs.items.size - 1)
                repairs += "directions_extra_preserved_to_audit:${extra.size}"
            }
            dirs.items.forEachIndexed { i, d -> if (d == JNull) missing += "directions[$i]" }
        }
        return obj
    }

    /** vendor repair 主入口（照 output_repair.repair）。返回 (obj, loss, serialized)。 */
    private fun vendorRepair(rawText: String): Triple<JObj?, MutableList<JsonObject>, String?> {
        val loss = mutableListOf<JsonObject>()
        if (rawText.isBlank()) {
            return Triple(null, mutableListOf(lossEntry("empty_raw", "无原始文本")), null)
        }
        val obj = parseWithInheritance(rawText, loss)
            ?: return Triple(null, loss, null)

        // I2/I3/I4
        val repairs = mutableListOf<String>()
        val unmapped = mutableListOf<JsonObject>()
        val missing = mutableListOf<String>()
        normalizeContract(obj, repairs, unmapped, missing)
        if (repairs.isNotEmpty()) {
            loss += buildJsonObject {
                put("rule", "I2_I3_I4_inherited_normalize")
                put("actions", JsonArray(repairs.map { JsonPrimitive(it) }))
                put("unmapped_style_keys", JsonPrimitive(unmapped.size))
                put("missing_slots", JsonArray(missing.map { JsonPrimitive(it) }))
            }
        }

        // E1：旧别名表没覆盖的键名（夹零宽字符、截断等）再归一一遍
        val resp = obj.entries["response"]
        if (resp is JObj) {
            val newResp = LinkedHashMap<String, JVal>()
            val auditUnmapped = mutableListOf<JsonObject>()
            (obj.entries["_audit_unmapped"] as? JArr)?.items
                ?.mapNotNull { it as? JsonObject }?.let { auditUnmapped.addAll(it) }
            for ((k, v) in resp.entries) {
                val ck = canonKey(k)
                if (ck == null) {
                    loss += lossEntry(
                        "E1_unmappable_key", "key" to JsonPrimitive(k),
                        "detail" to JsonPrimitive("键名无法归一到四规范名，按原样保留（不猜）")
                    )
                    newResp[k] = v
                    continue
                }
                if (ck != k) {
                    loss += buildJsonObject {
                        put("rule", "E1_style_key_normalized")
                        put("from", k); put("to", ck)
                        put("text_unchanged", true)
                    }
                }
                val cur = newResp[ck]
                val curEmpty = slotEmpty(cur)
                val vEmpty = slotEmpty(v)
                if (newResp.containsKey(ck) && !curEmpty && !vEmpty) {
                    auditUnmapped += buildJsonObject {
                        put("alias_key", k); put("alias_value", jvalToJson(v))
                        put("canon_key", ck); put("canon_value", jvalToJson(cur!!))
                    }
                    loss += lossEntry(
                        "E1_duplicate_canon_key_preserved_to_audit",
                        "to" to JsonPrimitive(ck), "preserved_text" to jvalToJson(v)
                    )
                    continue
                }
                if (newResp.containsKey(ck) && vEmpty) continue
                newResp[ck] = v
            }
            obj.entries["response"] = JObj(newResp)
            if (auditUnmapped.isNotEmpty()) obj.entries["_audit_unmapped"] = JArr(auditUnmapped.map { toJVal(it) }.toMutableList())
        }

        // I3 的全文保全写进了 _audit_extra_directions；这里只记进留损日志
        val extra = obj.entries["_audit_extra_directions"] as? JArr
        if (extra != null && extra.items.isNotEmpty()) {
            loss += buildJsonObject {
                put("rule", "I3_directions_overflow_preserved")
                put("given", JsonPrimitive(extra.items.size + 4))
                put("preserved_directions", JsonArray(extra.items.map { jvalToJson(it) }))
            }
        }

        // R4：只登记，不代候选做决定
        val dirs = obj.entries["directions"]
        val respObj = obj.entries["response"] as? JObj
        val ks = CANON.filter { respObj?.entries?.containsKey(it) == true }
        val emptyBody = if (ks.isEmpty()) true
        else ks.all { (respObj?.entries?.get(it) as? JStr)?.value?.isBlank() ?: true }
        val allNullDirs = dirs is JArr && dirs.items.isNotEmpty() && dirs.items.all { it == JNull }
        if ("action" !in obj.entries && emptyBody && allNullDirs) {
            loss += lossEntry(
                "R4_no_reply_expression_missing",
                "detail" to JsonPrimitive("空正文+全 null 方向：不回只能由生成侧显式 action 表达，消费侧不代补"),
                "repaired_here" to JsonPrimitive(false)
            )
        }

        return Triple(obj, loss, dumps(obj))
    }

    // ═══════════ 第二层：漂移族补修（guard 层） ═══════════

    private fun bestStyleSlot(key: String): Pair<String?, Double> {
        // 未对拍前不启用 fuzzy_map（规格书 §1.5）：保守路径直接返回 None。
        if (!FUZZY_MAP_ENABLED) return null to 0.0
        val ck = cleanKey(key)
        if (ck.isEmpty()) return null to 0.0
        var best: String? = null
        var bestScore = 0.0
        for (c in CANON) {
            val score = sequenceMatcherRatio(ck, c)
            if (score > bestScore) { best = c; bestScore = score }
        }
        return if (bestScore >= STYLE_SIM_THRESHOLD) best to bestScore else null to bestScore
    }

    private fun actionShaped(d: JObj): Boolean {
        if (d.entries.isEmpty()) return false
        if (CANON.any { !slotEmpty(d.entries[it]) }) return false
        return ACTION_HINT_KEYS.any { it in d.entries }
    }

    private class Meta {
        val rules = LinkedHashSet<String>()
        var changed = false
        var unparseable = false
        var emptyResponse = false
        var noReplyAction = false
        val repairLoss = mutableListOf<JsonObject>()
        val styleKeyMap = mutableListOf<JsonObject>()
        val unmappedPreserved = mutableListOf<JsonObject>()
        val extraDirectionsPreserved = mutableListOf<JsonElement>()
        val responseSlotsAdded = mutableListOf<String>()
        var directionsPadded = 0
        var directionsTruncated = 0
        var actionToResponse = false
        var responseToAction = false
        var repairError: String? = null
        var harnessChars: Int? = null
        var responseNotObject: String? = null
        var directionsNotList: String? = null

        fun addRule(name: String) { rules.add(name) }
    }

    private fun mapStyleKeys(obj: JObj, meta: Meta) {
        // 未知 response 键 → 规范槽（仅槽空时）；不可映射的非空值全文进 meta 后移除。
        val resp = obj.entries["response"] as? JObj ?: return
        val newResp = LinkedHashMap<String, JVal>()
        var changed = false
        // 先收规范槽（含 repair 补的 null 占位），再处理未知键——避免占位 null
        // 在迭代顺序上后到、把别名映射出的正文覆盖掉。
        for ((k, v) in resp.entries) if (k in CANON) newResp[k] = v
        for ((k, v) in resp.entries) {
            if (k in CANON) continue
            if (slotEmpty(v)) {
                changed = true                      // 空别名键：删掉不丢文本
                meta.addRule("empty_alias_key_dropped")
                continue
            }
            val (target, score) = bestStyleSlot(k)
            if (target == null) {
                meta.unmappedPreserved += buildJsonObject {
                    put("key", k); put("value", jvalToJson(v)); put("reason", "no_style_match")
                }
                changed = true
                meta.addRule("unmapped_style_removed")
                continue
            }
            if (!slotEmpty(newResp[target])) {
                // 规范槽已有非空文本：绝不覆盖，别名值全文保留到审计
                meta.unmappedPreserved += buildJsonObject {
                    put("key", k); put("value", jvalToJson(v))
                    put("reason", "canonical_slot_nonempty")
                    put("canonical_key", target)
                    put("canonical_value", jvalToJson(newResp[target]!!))
                }
                changed = true
                meta.addRule("unmapped_style_preserved")
                continue
            }
            newResp[target] = v
            meta.styleKeyMap += buildJsonObject {
                put("from", k); put("to", target)
                put("similarity", JsonPrimitive((score * 1000).toInt() / 1000.0))
            }
            changed = true
            meta.addRule("style_key_fuzzy_map")
        }
        if (changed) obj.entries["response"] = JObj(newResp)
    }

    // ═══════════ 主入口 ═══════════

    data class GuardOutcome(val text: String, val meta: JsonObject) {
        val changed: Boolean get() = (meta["changed"] as? JsonPrimitive)?.content == "true"
    }

    fun interface GuardAuditSink {
        fun write(line: String)
    }

    /** 文件审计槽：追加一行 JSON 到 `<dir>/contract_guard_audit.jsonl`。 */
    class FileGuardAuditSink(private val dir: File) : GuardAuditSink {
        override fun write(line: String) {
            dir.mkdirs()
            File(dir, "contract_guard_audit.jsonl").appendText(line + "\n", Charsets.UTF_8)
        }
    }

    /** 守卫自身异常 → 降级原样回复并记 guard_exception（守卫绝不吞回复）。 */
    fun repairAndAudit(rawText: String, auditContext: JsonObject = JsonObject(emptyMap())): GuardOutcome {
        return try {
            repairAndAuditInner(rawText, auditContext)
        } catch (e: Exception) {
            val meta = buildJsonObject {
                put("raw_chars", if (rawText is String) rawText.length else 0)
                put("changed", false)
                put("rules", JsonArray(listOf(JsonPrimitive("guard_exception"))))
                put("unparseable", true)
                put("error", e.message ?: e.javaClass.simpleName)
            }
            writeAudit(meta, auditContext)
            GuardOutcome(rawText, meta)
        }
    }

    private fun writeAudit(meta: JsonObject, auditContext: JsonObject) {
        val sink = auditSink ?: return
        val changed = (meta["changed"] as? JsonPrimitive)?.content == "true"
        val unmapped = (meta["unmapped_preserved"] as? JsonArray).orEmpty().isNotEmpty()
        val extra = (meta["extra_directions_preserved"] as? JsonArray).orEmpty().isNotEmpty()
        if (!(changed || unmapped || extra)) return
        try {
            val line = LinkedHashMap<String, JsonElement>()
            line.putAll(auditContext)
            line["meta"] = meta
            sink.write(dumps(JObj(LinkedHashMap(line.mapValues { toJVal(it.value) }))))
        } catch (_: Exception) {
            // 审计落盘失败不回滚回复
        }
    }

    private fun toJVal(e: JsonElement): JVal = when (e) {
        is JsonNull -> JNull
        is JsonPrimitive -> if (e.isString) JStr(e.content) else JNum(e.content)
        is JsonArray -> JArr(e.map { toJVal(it) }.toMutableList())
        is JsonObject -> JObj(LinkedHashMap(e.mapValues { toJVal(it.value) }))
    }

    private fun repairAndAuditInner(rawText: String, auditContext: JsonObject): GuardOutcome {
        val meta = Meta()
        val rawChars = rawText.length

        // vendor repair
        val (obj, loss, _) = try {
            vendorRepair(rawText)
        } catch (exc: Exception) {
            meta.unparseable = true
            meta.repairError = exc.message
            meta.addRule("repair_exception")
            val m = metaJson(meta, rawChars)
            writeAudit(m, auditContext)
            return GuardOutcome(rawText, m)
        }
        meta.repairLoss.addAll(loss)
        if (obj == null) {
            meta.unparseable = true
            meta.addRule("unparseable_raw_left_untouched")
            val m = metaJson(meta, rawChars)
            writeAudit(m, auditContext)
            return GuardOutcome(rawText, m)
        }

        // 0) repair 的审计载荷不进入返回契约 JSON（全文进 meta）
        val extraDir = obj.entries.remove("_audit_extra_directions") as? JArr
        if (extraDir != null && extraDir.items.isNotEmpty()) {
            meta.extraDirectionsPreserved.addAll(extraDir.items.map { jvalToJson(it) })
            meta.addRule("directions_overflow")
        }
        val auditUnmapped = obj.entries.remove("_audit_unmapped") as? JArr
        if (auditUnmapped != null && auditUnmapped.items.isNotEmpty()) {
            meta.unmappedPreserved.addAll(
                auditUnmapped.items.mapNotNull { it as? JsonObject }
            )
            meta.addRule("unmapped_style_preserved")
        }

        // 1) action <-> response 顶层键互改（仅当另一个不存在；反向只认 action 形状）
        val resp = obj.entries["response"]
        val act = obj.entries["action"]
        if (act is JObj && (!obj.entries.containsKey("response") || obj.entries["response"] == JNull)) {
            obj.entries.remove("action")
            obj.entries["response"] = act
            meta.actionToResponse = true
            meta.addRule("action_to_response")
        } else if (resp is JObj && actionShaped(resp) &&
            (!obj.entries.containsKey("action") || obj.entries["action"] == JNull)
        ) {
            obj.entries.remove("response")
            obj.entries["action"] = resp
            meta.responseToAction = true
            meta.addRule("response_to_action")
        }

        // 2) 风格键漂移（W17：bad_but/bad_bo/bad_young/bad_girl/bad/...）
        mapStyleKeys(obj, meta)

        meta.noReplyAction = (obj.entries["action"] as? JStr)?.value?.trim() == "no_reply"

        // 3) response 规范槽补 null（不编造文本；合法 no_reply 不动）
        val respObj = obj.entries["response"] as? JObj
        if (respObj != null && !meta.noReplyAction) {
            val added = mutableListOf<String>()
            for (k in CANON) {
                if (k !in respObj.entries) {
                    respObj.entries[k] = JNull
                    added += k
                }
            }
            if (added.isNotEmpty()) {
                meta.responseSlotsAdded.addAll(added)
                meta.addRule("response_pad_null")
            }
        }

        // 4) directions 截到 4 / 补 null（合法 no_reply 不补缺省键）
        val dirs = obj.entries["directions"]
        if (dirs is JArr) {
            if (dirs.items.size > DIRECTIONS_LEN) {
                val overflow = dirs.items.drop(DIRECTIONS_LEN)
                while (dirs.items.size > DIRECTIONS_LEN) dirs.items.removeAt(dirs.items.size - 1)
                meta.extraDirectionsPreserved.addAll(overflow.map { jvalToJson(it) })
                meta.directionsTruncated = overflow.size
                meta.addRule("directions_truncated")
            }
            if (dirs.items.size < DIRECTIONS_LEN) {
                val pad = DIRECTIONS_LEN - dirs.items.size
                repeat(pad) { dirs.items.add(JNull) }
                meta.directionsPadded = pad
                meta.addRule("directions_pad_null")
            }
        } else if (dirs == null && !meta.noReplyAction) {
            obj.entries["directions"] = JArr(MutableList(DIRECTIONS_LEN) { JNull })
            meta.directionsPadded = DIRECTIONS_LEN
            meta.addRule("directions_added_null")
        } else if (dirs != null) {
            meta.directionsNotList = dirs.javaClass.simpleName
            meta.addRule("directions_not_list")
        }

        // 5) 空 response：只登记，不伪造内容，原文本原样返回
        val respNow = obj.entries["response"]
        val emptyResp = if (meta.noReplyAction) {
            false
        } else when (respNow) {
            null -> true
            is JObj -> CANON.all { slotEmpty(respNow.entries[it]) }
            else -> {
                meta.responseNotObject = respNow.javaClass.simpleName
                meta.addRule("response_not_object")
                false
            }
        }
        meta.emptyResponse = emptyResp
        if (emptyResp) {
            meta.addRule("empty_response_raw_kept")
            meta.changed = false
            val m = metaJson(meta, rawChars)
            writeAudit(m, auditContext)
            return GuardOutcome(rawText, m)
        }

        // 6) 只有对象实际变化才重写；否则逐字节返回原文本
        val rawObj = loadsDict(rawText)
        if (rawObj != null && rawObj == obj) {
            meta.changed = false
            val m = metaJson(meta, rawChars)
            writeAudit(m, auditContext)
            return GuardOutcome(rawText, m)
        }

        val harnessText = dumps(obj)
        meta.harnessChars = harnessText.length
        meta.changed = true
        meta.addRule("harness_text_rewritten")
        val m = metaJson(meta, rawChars)
        writeAudit(m, auditContext)
        return GuardOutcome(harnessText, m)
    }

    private fun metaJson(meta: Meta, rawChars: Int): JsonObject = buildJsonObject {
        put("raw_chars", rawChars)
        put("changed", meta.changed)
        put("rules", JsonArray(meta.rules.map { JsonPrimitive(it) }))
        put("unparseable", meta.unparseable)
        put("empty_response", meta.emptyResponse)
        put("no_reply_action", meta.noReplyAction)
        put("repair_loss", JsonArray(meta.repairLoss))
        put("style_key_map", JsonArray(meta.styleKeyMap))
        put("unmapped_preserved", JsonArray(meta.unmappedPreserved))
        put("extra_directions_preserved", JsonArray(meta.extraDirectionsPreserved))
        put("response_slots_added", JsonArray(meta.responseSlotsAdded.map { JsonPrimitive(it) }))
        put("directions_padded", meta.directionsPadded)
        put("directions_truncated", meta.directionsTruncated)
        put("action_to_response", meta.actionToResponse)
        put("response_to_action", meta.responseToAction)
        meta.repairError?.let { put("repair_error", it) }
        meta.harnessChars?.let { put("harness_chars", it) }
        meta.responseNotObject?.let { put("response_not_object", it) }
        meta.directionsNotList?.let { put("directions_not_list", it) }
    }
}
