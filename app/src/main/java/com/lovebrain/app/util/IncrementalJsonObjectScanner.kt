package com.lovebrain.app.util

/**
 * P3-04: 真增量 JSON 对象流解析器。
 *
 * ## 它替掉的是什么
 * 旧路径每收到 50 个字符就 `rawBuffer.toString()` 把整个累积缓冲复制一遍，
 * 再 `replace("```json","")` 复制两遍，然后从头扫描已经解析过的区域。
 * 于是一长度为 n 的流，总代价约 O(n²/50) 次字符操作——
 * 名义上"节流"了，实际上只是把全量重扫变稀，并不是增量解析。
 *
 * ## 现在的做法
 * 解析器只吃掉**新增**的那段文本；字符串态、转义态、括号深度、所处阶段都跨 chunk 保留。
 * 已经吐出去的对象不会再被看一眼，未闭合的尾部留在内部缓冲里等下一个 chunk。
 * 每个字符只被处理一次，总代价 O(n)。
 *
 * ## 与旧实现的语义对齐
 * - 容忍开头的 ```json / ``` 代码围栏
 * - 只认 `"$key"` 之后第一个数组里的 `{...}` 对象
 * - 字符串内部的 `{` `}` `"` 与 `\\` 转义正确处理
 * - 数组出现 `]` 后判定完成，不再产出
 * - 截断/未闭合的尾部对象不会提前吐出去（和旧实现一致：只返回完整对象）
 */
class IncrementalJsonObjectScanner(private val key: String) {

    private enum class Phase { PROLOG, SEEK_KEY, SEEK_ARRAY, ELEMENTS, DONE }

    private var phase = Phase.PROLOG
    private val buf = StringBuilder()

    // ELEMENTS 阶段的状态
    private var depth = 0
    private var inString = false
    private var escaped = false
    private var inObject = false
    private val current = StringBuilder()

    /** 已产出对象数 */
    var emittedCount: Int = 0
        private set

    /** 是否识别到了代码围栏（诊断用） */
    var sawCodeFence: Boolean = false
        private set

    /**
     * 喂入新增文本，返回本次新闭合的对象字符串（可能为空）。
     *
     * 空串是合法 no-op。需要重来时调用 [reset] 或直接新建一个扫描器——
     * 本类不接受"回退式"输入。
     */
    fun feed(chunk: String): List<String> {
        if (chunk.isEmpty() || phase == Phase.DONE) return emptyList()
        buf.append(chunk)

        val out = mutableListOf<String>()
        var progressed = true
        while (progressed && phase != Phase.DONE) {
            progressed = when (phase) {
                Phase.PROLOG -> stepProlog()
                Phase.SEEK_KEY -> stepSeekKey()
                Phase.SEEK_ARRAY -> stepSeekArray()
                Phase.ELEMENTS -> stepElements(out)
                Phase.DONE -> false
            }
        }
        return out
    }

    /** 数组是否已闭合 */
    val isDone: Boolean get() = phase == Phase.DONE

    /** 回到初始状态 */
    fun reset() {
        phase = Phase.PROLOG
        buf.setLength(0)
        current.setLength(0)
        depth = 0
        inString = false
        escaped = false
        inObject = false
        emittedCount = 0
        sawCodeFence = false
    }

    // ─── 各阶段：只在真正消费掉字符时返回 true ───────────────────────

    /** 开头可能包着 ```json 围栏 */
    private fun stepProlog(): Boolean {
        val ws = leadingWhitespace()
        if (ws > 0) buf.deleteRange(0, ws)
        if (buf.isEmpty()) return false

        if (!buf.startsWith("```")) {
            phase = Phase.SEEK_KEY
            return true
        }
        // 是围栏：还要再看一个字符，才能区分 ```json 还是裸 ```
        if (buf.length < FENCE.length + 1) return false
        val langTokenEnd = fencedLanguageTokenEnd()
        if (langTokenEnd == null) return false  // 语言标记还没收完整，等下一段
        buf.deleteRange(0, langTokenEnd)
        sawCodeFence = true
        phase = Phase.SEEK_KEY
        return true
    }

    private fun leadingWhitespace(): Int {
        var i = 0
        while (i < buf.length && buf[i].isWhitespace()) i++
        return i
    }

    /**
     * 围栏后语言标记的结束位置。
     *
     * ``` 之后紧跟字母（json/JSON/JSON5…）时吞掉整段字母；
     * 之后是空白/换行/其它字符时只吞 ``` 三个字符。
     * 返回 null 表示文本还没给到能判断的程度（字母段尚未遇到终止符且已到缓冲末尾）。
     */
    private fun fencedLanguageTokenEnd(): Int? {
        var i = FENCE.length
        while (i < buf.length && buf[i].isLetter()) i++
        if (i == FENCE.length) return i            // 紧跟非字母 → 只有裸围栏
        if (i >= buf.length) return null            // 字母段还没结束，等更多文本
        return i                                    // 字母段结束于非字母字符前
    }

    /** 找 "key" */
    private fun stepSeekKey(): Boolean {
        val needle = "\"$key\""
        val idx = buf.indexOf(needle)
        if (idx >= 0) {
            buf.deleteRange(0, idx + needle.length)
            phase = Phase.SEEK_ARRAY
            return true
        }
        // 没找到：丢掉除"可能是被切断的 needle 前缀"以外的全部
        val keep = minOf(needle.length - 1, buf.length)
        if (buf.length > keep) buf.deleteRange(0, buf.length - keep)
        return false
    }

    /** 找 key 之后的第一个 `[` */
    private fun stepSeekArray(): Boolean {
        val br = buf.indexOf('[')
        if (br >= 0) {
            buf.deleteRange(0, br + 1)
            phase = Phase.ELEMENTS
            return true
        }
        val keep = minOf(1, buf.length)
        if (buf.length > keep) buf.deleteRange(0, buf.length - keep)
        return false
    }

    /** 逐字符扫描数组元素 */
    private fun stepElements(out: MutableList<String>): Boolean {
        var i = 0
        while (i < buf.length) {
            val c = buf[i]
            if (!inObject) {
                when {
                    c == '{' -> {
                        inObject = true
                        depth = 1
                        inString = false
                        escaped = false
                        current.setLength(0)
                        current.append(c)
                        i++
                    }
                    c == ']' -> {
                        buf.deleteRange(0, i)
                        phase = Phase.DONE
                        return true
                    }
                    else -> i++
                }
                continue
            }

            current.append(c)
            i++
            when {
                inString -> {
                    if (escaped) escaped = false
                    else when (c) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                    }
                }
                c == '"' -> inString = true
                c == '\\' -> escaped = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        out.add(current.toString())
                        emittedCount++
                        inObject = false
                        current.setLength(0)
                    }
                }
            }
        }
        // 丢掉已消费部分；未闭合对象的文字留在 current，buf 清空等下一段
        buf.deleteRange(0, i)
        return out.isNotEmpty()
    }

    private fun StringBuilder.deleteRange(from: Int, toExclusive: Int) {
        if (toExclusive <= from) return
        delete(from, minOf(toExclusive, length))
    }

    private companion object {
        const val FENCE = "```"
    }
}

/**
 * P3-04: 提取 `"$key"` 后面第一个完整 JSON 对象的增量扫描器。
 *
 * 回复链路的 `"response": { ... }` 是对象不是数组，
 * 旧实现每 50 字符 `rawBuffer.toString()` + `indexOf("\"response\"")` 从头再找一遍。
 * 本类与 [IncrementalJsonObjectScanner] 同样只消费新增文本，命中并闭合后进入 DONE。
 */
class IncrementalSingleObjectScanner(private val key: String) {

    private enum class Phase { SEEK_KEY, BRACE, BODY, DONE }

    private var phase = Phase.SEEK_KEY
    private val buf = StringBuilder()
    private val obj = StringBuilder()
    private var depth = 0
    private var inString = false
    private var escaped = false

    var result: String? = null
        private set

    val isDone: Boolean get() = phase == Phase.DONE

    fun feed(chunk: String): String? {
        if (chunk.isEmpty() || phase == Phase.DONE) return null
        buf.append(chunk)

        var progressed = true
        while (progressed && phase != Phase.DONE) {
            progressed = when (phase) {
                Phase.SEEK_KEY -> stepSeekKey()
                Phase.BRACE -> stepBrace()
                Phase.BODY -> stepBody()
                Phase.DONE -> false
            }
        }
        return result
    }

    fun reset() {
        phase = Phase.SEEK_KEY
        buf.setLength(0)
        obj.setLength(0)
        depth = 0
        inString = false
        escaped = false
        result = null
    }

    private fun stepSeekKey(): Boolean {
        val needle = "\"$key\""
        val idx = buf.indexOf(needle)
        if (idx >= 0) {
            buf.deleteRange(0, idx + needle.length)
            phase = Phase.BRACE
            return true
        }
        val keep = minOf(needle.length - 1, buf.length)
        if (buf.length > keep) buf.deleteRange(0, buf.length - keep)
        return false
    }

    private fun stepBrace(): Boolean {
        val ws = leadingWhitespace()
        if (ws > 0) buf.deleteRange(0, ws)
        if (buf.isEmpty()) return false
        if (buf[0] != '{') { phase = Phase.DONE; return false }
        phase = Phase.BODY
        return true
    }

    private fun stepBody(): Boolean {
        var i = 0
        while (i < buf.length) {
            val c = buf[i]
            obj.append(c)
            i++
            when {
                inString -> {
                    if (escaped) escaped = false
                    else when (c) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                    }
                }
                c == '"' -> inString = true
                c == '\\' -> escaped = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        buf.deleteRange(0, i)
                        result = obj.toString()
                        phase = Phase.DONE
                        return true
                    }
                }
            }
        }
        buf.deleteRange(0, i)
        return false
    }

    private fun leadingWhitespace(): Int {
        var i = 0
        while (i < buf.length && buf[i].isWhitespace()) i++
        return i
    }

    private fun StringBuilder.deleteRange(from: Int, toExclusive: Int) {
        if (toExclusive <= from) return
        delete(from, minOf(toExclusive, length))
    }
}
