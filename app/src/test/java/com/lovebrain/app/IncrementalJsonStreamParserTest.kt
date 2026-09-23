package com.lovebrain.app

import com.lovebrain.app.domain.PartialJsonObjects
import com.lovebrain.app.util.IncrementalJsonObjectScanner
import com.lovebrain.app.util.IncrementalSingleObjectScanner
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3-04 复核证据（re-audit 2026-09-23 §7 行 P3-04 / §8 step 3 item 4）。
 *
 * 审计的指控不是「解析器写错了」，而是**没有任何测量支撑**：
 * 「无 Macrobenchmark/Perfetto/帧数据」。本文件把能被证伪的那一半补齐。
 *
 *  1. **等价性**：增量扫描器在任意（种子化的）分块方式下产出的对象集合，必须与
 *     P3-04 之前的一次性全量解析器逐字节相同；`PartialJsonObjects.extractObjects` /
 *     `extractKeyObject` 在同一输入上也必须给出同一答案。
 *  2. **代价形状**：旧路径是平方级（每 50 字 `rawBuffer.toString()` + 从头重扫），
 *     新路径必须不是。旧路径给插桩的精确字符计数，新路径给墙钟 + per-thread 分配字节，
 *     数字写进 `build/reports/perf/incremental-json-scaling.txt` 供复查。
 *  3. **边界**：对象中间断块、```json 围栏被切在两块里、字符串内的转义引号/反斜杠、
 *     尾部未闭合对象——都不允许吐出半成品对象。
 *  4. **长流回归**：约 200k 字符按生产的 50 字阈值喂入必须在有界时间内完成。
 *
 * 本文件只读生产代码，不修改它。本地复现：
 *   `./gradlew --offline :app:testDebugUnitTest --tests "*IncrementalJsonStreamParserTest*"`
 */
class IncrementalJsonStreamParserTest {

    // ══════════════════════════════════════════════════════════════════════
    // 参考实现：P3-04 之前的生产解析器（git 6d67b37~1 原样移植 + 字符计数插桩）
    // ══════════════════════════════════════════════════════════════════════

    private class Cost {
        var charsScanned = 0L
        var charsCopied = 0L
    }

    private object LegacyOneShotParser {

        fun extractObjects(raw: String, key: String, cost: Cost): List<String> {
            val buffer = raw.replace("```json", "").replace("```", "")
            // 两次 replace == 两遍全量复制，正是审计点名的开销之一
            cost.charsCopied += raw.length * 2
            val start = buffer.indexOf("\"$key\"")
            if (start < 0) return emptyList()
            val arrStart = buffer.indexOf('[', start)
            if (arrStart < 0) return emptyList()

            val result = mutableListOf<String>()
            var i = arrStart + 1
            while (i < buffer.length) {
                while (i < buffer.length &&
                    (buffer[i] == ' ' || buffer[i] == '\n' || buffer[i] == '\r' || buffer[i] == ',')
                ) {
                    cost.charsScanned++
                    i++
                }
                if (i >= buffer.length) break
                if (buffer[i] != '{') {
                    cost.charsScanned++
                    break
                }
                val objStr = completeObjectAt(buffer, i, cost) ?: break
                result.add(objStr)
                i += objStr.length
            }
            return result
        }

        fun extractKeyObject(raw: String, key: String, cost: Cost): String? {
            val buffer = raw.replace("```json", "").replace("```", "")
            cost.charsCopied += raw.length * 2
            val start = buffer.indexOf("\"$key\"")
            if (start < 0) return null
            val brace = buffer.indexOf('{', start)
            if (brace < 0) return null
            return completeObjectAt(buffer, brace, cost)
        }

        private fun completeObjectAt(buffer: String, from: Int, cost: Cost): String? {
            var depth = 0
            var j = from
            var inStr = false
            var esc = false
            while (j < buffer.length) {
                cost.charsScanned++
                val c = buffer[j]
                if (inStr) {
                    if (esc) esc = false
                    else if (c == '\\') esc = true
                    else if (c == '"') inStr = false
                } else {
                    when (c) {
                        '"' -> inStr = true
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) return buffer.substring(from, j + 1)
                        }
                    }
                }
                j++
            }
            return null
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 夹具：种子化 JSON + 种子化分块
    // ══════════════════════════════════════════════════════════════════════

    /** 值里可能出现的困难内容：引号、反斜杠、括号、制表/换行、多字节 */
    private val trickyValues = listOf(
        "普通一句话",
        "他说「你好」",
        "brace { inside } string",
        "array [ inside ] string",
        "quote \" inside",
        "backslash C:\\tmp\\x",
        "escaped quote pair \\\" both",
        "} { \" }] unbalanced inside a string",
        "tab\there",
        "newline\nhere",
        "mixed {\"a\":1} literal",
        "colon:, comma,,",
        "json-ish {\"tips\":[{\"z\":9}]}",
        "empty-ish",
        "emoji 💡",
        "quote at end\"",
        "backslash at end\\",
    )

    private fun jsonEscape(s: String): String = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    private fun randomObject(rnd: Random, index: Int): String = buildString {
        append("{\"id\":\"o").append(index).append('"')
        val fields = 1 + rnd.nextInt(3)
        repeat(fields) { f ->
            val raw = trickyValues[rnd.nextInt(trickyValues.size)]
            append(",\"k").append(f).append("\":\"").append(jsonEscape(raw)).append('"')
        }
        if (rnd.nextInt(4) == 0) append(",\"nested\":{\"deep\":{\"deeper\":[1,2,3]}}")
        append('}')
    }

    /** `{"other":"lead","<key>":[{...},...]<trailing>` —— 尾部故意不闭合外层，模拟在途流 */
    private fun arrayText(key: String, objectCount: Int, seed: Long, fence: String? = null, trailing: String = "]"): String {
        val rnd = Random(seed)
        val body = (0 until objectCount).joinToString(",") { randomObject(rnd, it) }
        return buildString {
            fence?.let { append(it) }
            append("{\"other\":\"lead\",\"").append(key).append("\":[").append(body).append(trailing)
        }
    }

    /** 种子化随机分块：含 1 字符块（必然打在任意边界）与空块（合法 no-op） */
    private fun splitChunks(text: String, seed: Long): List<String> {
        val rnd = Random(seed)
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val size = when (rnd.nextInt(10)) {
                0 -> 1
                1 -> 2
                2 -> 3
                in 3..6 -> 1 + rnd.nextInt(20)
                else -> 40 + rnd.nextInt(360)
            }
            if (rnd.nextInt(20) == 0) out += ""
            out += text.substring(i, min(i + size, text.length))
            i += size
        }
        return out
    }

    private fun feedChunks(chunks: List<String>, key: String): List<String> {
        val scanner = IncrementalJsonObjectScanner(key)
        val out = mutableListOf<String>()
        for (c in chunks) out += scanner.feed(c)
        return out
    }

    private fun feedChars(scanner: IncrementalJsonObjectScanner, text: String): List<String> {
        val out = mutableListOf<String>()
        for (c in text) out += scanner.feed(c.toString())
        return out
    }

    /** 对象字符串自身必须括号/引号闭合——用来证明从没吐过半成品 */
    private fun isSelfComplete(s: String): Boolean {
        if (!s.startsWith("{") || !s.endsWith("}")) return false
        var depth = 0
        var inStr = false
        var escNext = false
        for (c in s) {
            if (inStr) {
                if (escNext) escNext = false
                else when (c) {
                    '\\' -> escNext = true
                    '"' -> inStr = false
                }
            } else when (c) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> depth--
            }
            if (depth < 0) return false
        }
        return depth == 0 && !inStr
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1) 等价性
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `incremental scanner equals the legacy one-shot on seeded chunk splits`() {
        val legacy = Cost()
        for (case in 0 until 200) {
            val key = if (case % 3 == 0) "tips" else "options"
            val text = arrayText(
                key = key,
                objectCount = 1 + case % 9,
                seed = 1_000L + case,
                fence = if (case % 5 == 0) "```json\n" else null,
                trailing = listOf("]", "]}", "]\n", "]  ").let { it[(case * 7) % it.size] },
            )
            val expected = LegacyOneShotParser.extractObjects(text, key, legacy)
            val oneShot = PartialJsonObjects.extractObjects(text, key)
            val incremental = feedChunks(splitChunks(text, 7_000L + case), key)

            assertTrue("case $case: fixture produced no objects — test itself is broken", expected.isNotEmpty())
            assertEquals("case $case: production one-shot diverged from the legacy reference", expected, oneShot)
            assertEquals("case $case: chunked feed diverged from the one-shot result", expected, incremental)
            assertTrue("case $case: a partial object leaked", incremental.all { isSelfComplete(it) })
        }
        assertTrue("legacy reference must actually have scanned", legacy.charsScanned > 0)
    }

    @Test
    fun `single object scanner equals legacy extractKeyObject on seeded chunk splits`() {
        val legacy = Cost()
        for (case in 0 until 150) {
            val values = (0 until 1 + case % 4).joinToString(",") { f ->
                "\"k$f\":\"${jsonEscape(trickyValues[Random(case * 31L + f).nextInt(trickyValues.size)])}\""
            }
            val nested = if (case % 4 == 0) ",\"deep\":{\"a\":{\"b\":[1,2,{\"c\":\"}\"}]}}" else ""
            val text = "```json\n{\"response\":{$values$nested}" + if (case % 6 == 0) "" else "}"
            val key = "response"

            val expected = LegacyOneShotParser.extractKeyObject(text, key, legacy)
            val oneShot = PartialJsonObjects.extractKeyObject(text, key)

            val scanner = IncrementalSingleObjectScanner(key)
            var hit: String? = null
            var hits = 0
            for (c in splitChunks(text, 90_000L + case)) {
                val r = scanner.feed(c)
                if (r != null) {
                    hits++
                    hit = r
                }
            }
            assertEquals("case $case: one-shot diverged from the legacy reference", expected, oneShot)
            assertEquals("case $case: chunked feed diverged from the one-shot", expected, hit)
            assertTrue("case $case: result must be emitted once (got $hits)", hits <= 1)
            if (expected != null) assertTrue("case $case: partial object leaked", isSelfComplete(expected))
        }
    }

    @Test
    fun `both paths stop at the first bracket after the key (shared limitation, locked)`() {
        // 旧实现 indexOf('[', keyPos) / 新实现 SEEK_ARRAY 都只认 key 之后的第一个 [。
        // 这不是 P3-04 引入的回归，但把它钉住，避免有人以为新路径更宽松。
        val text = "{\"meta\":{\"tips\":[1]},\"tips\":[{\"a\":1}]}"
        assertEquals(emptyList<String>(), LegacyOneShotParser.extractObjects(text, "tips", Cost()))
        assertEquals(emptyList<String>(), PartialJsonObjects.extractObjects(text, "tips"))
        assertEquals(emptyList<String>(), feedChunks(splitChunks(text, 3L), "tips"))
    }

    @Test
    fun `mid-stream fence inside a string value survives while the legacy one-shot ate it`() {
        // 旧实现的 replace("```","") 是全局的，会把值里的围栏吃掉；
        // 新实现只容忍前导围栏，值保持原样。这是有意的改进，写死在此。
        val text = "{\"tips\":[{\"a\":\"keep ```json please\"}]}"
        assertEquals(listOf("{\"a\":\"keep  please\"}"), LegacyOneShotParser.extractObjects(text, "tips", Cost()))
        assertEquals(listOf("{\"a\":\"keep ```json please\"}"), PartialJsonObjects.extractObjects(text, "tips"))
        assertEquals(
            listOf("{\"a\":\"keep ```json please\"}"),
            feedChunks(splitChunks(text, 4_242L), "tips"),
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2) 边界条件
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `a chunk boundary in the middle of an object never yields a partial object`() {
        val text = "{\"tips\":[{\"a\":\"one\"},{\"b\":\"two\"},{\"c\":\"three\"}]}"
        val expected = listOf("{\"a\":\"one\"}", "{\"b\":\"two\"}", "{\"c\":\"three\"}")
        assertEquals(expected, LegacyOneShotParser.extractObjects(text, "tips", Cost()))
        assertEquals(expected, PartialJsonObjects.extractObjects(text, "tips"))

        // 穷举每一个断点：两块喂完必须得到同样的三个完整对象
        for (cut in 1 until text.length) {
            val scanner = IncrementalJsonObjectScanner("tips")
            val first = scanner.feed(text.substring(0, cut))
            assertTrue("split at $cut emitted a partial object", first.all { isSelfComplete(it) })
            val got = first + scanner.feed(text.substring(cut))
            assertEquals("split at char $cut changed the output", expected, got)
        }

        // 1 字符一块：断点必然落在对象中间
        val perChar = IncrementalJsonObjectScanner("tips")
        assertEquals(expected, feedChars(perChar, text))
        assertTrue("closed array must report done", perChar.isDone)
        assertEquals(3, perChar.emittedCount)
    }

    @Test
    fun `json code fence arriving split across chunks is still handled`() {
        val payload = "\n{\"tips\":[{\"a\":1},{\"b\":2}]}"
        val expected = listOf("{\"a\":1}", "{\"b\":2}")

        // ```json 被切成三块：产出必须完全正确。
        // sawCodeFence 只是诊断位——围栏被切断时它保持 false，不影响解析结果。
        val s1 = IncrementalJsonObjectScanner("tips")
        assertEquals(emptyList<String>(), s1.feed("``"))
        assertEquals(emptyList<String>(), s1.feed("jso"))
        assertEquals(expected, s1.feed("n" + payload))
        assertTrue("fence chars must not be mistaken for content", s1.emittedCount == 2)

        // 三个反引号各来一块，语言标记再单独一块
        val s2 = IncrementalJsonObjectScanner("tips")
        assertEquals(emptyList<String>(), s2.feed("`"))
        assertEquals(emptyList<String>(), s2.feed("`"))
        assertEquals(emptyList<String>(), s2.feed("`"))
        assertEquals(emptyList<String>(), s2.feed("json"))
        assertEquals(expected, s2.feed(payload))

        // 裸围栏（无语言标记）——整块到达时诊断位应当置起
        val s3 = IncrementalJsonObjectScanner("tips")
        assertEquals(emptyList<String>(), s3.feed("```"))
        assertEquals(expected, s3.feed(payload))
        assertTrue("an intact leading fence must be recorded", s3.sawCodeFence)

        // 只给到 ```js 就断：既不能吞掉内容，也不能提前产出
        val s4 = IncrementalJsonObjectScanner("tips")
        assertEquals(emptyList<String>(), s4.feed("```js"))
        assertEquals(expected, s4.feed("on" + payload))
        assertTrue(s4.sawCodeFence)

        // 一次性路径同样吃前导围栏，且与分块结果一致
        assertEquals(expected, PartialJsonObjects.extractObjects("```json" + payload, "tips"))
        assertEquals(expected, LegacyOneShotParser.extractObjects("```json" + payload, "tips", Cost()))
    }

    @Test
    fun `escaped quotes and backslashes inside string values keep objects intact`() {
        val values = listOf(
            "he said \"hi\" to me",
            "unix C:\\tmp\\x",
            "} { \" }] end",
            "backslash at end\\",
            "nested \"json-ish {\\\"a\\\":1}\" value",
        )
        val objects = values.map { "{\"v\":\"${jsonEscape(it)}\"}" }
        val text = "{\"tips\":[" + objects.joinToString(",") + "]}"

        assertEquals(objects, LegacyOneShotParser.extractObjects(text, "tips", Cost()))
        assertEquals(objects, PartialJsonObjects.extractObjects(text, "tips"))
        val perChar = IncrementalJsonObjectScanner("tips")
        assertEquals("char-by-char must survive every escape", objects, feedChars(perChar, text))

        // 穷举断点：转义状态必须跨 chunk 保留
        for (cut in 1 until text.length) {
            val s = IncrementalJsonObjectScanner("tips")
            val got = s.feed(text.substring(0, cut)) + s.feed(text.substring(cut))
            assertEquals("split at $cut lost escape state", objects, got)
        }
    }

    @Test
    fun `an incomplete trailing object is held back until it closes`() {
        //                                                          ↓ 断在第二个对象中间
        val complete = "{\"tips\":[{\"a\":1},{\"b\":2}]}"
        val closed = complete.length - 3                    // "}]" 之外的部分
        val truncated = complete.substring(0, closed)
        assertEquals("{\"tips\":[{\"a\":1},{\"b\":2", truncated)

        val scanner = IncrementalJsonObjectScanner("tips")
        assertEquals("only the closed object may be emitted", listOf("{\"a\":1}"), scanner.feed(truncated))
        assertTrue("a truncated array is not done", !scanner.isDone)
        assertEquals(listOf("{\"b\":2}"), scanner.feed(complete.substring(closed)))
        assertTrue(scanner.isDone)
        assertEquals(2, scanner.emittedCount)
        // 数组闭合之后再喂也不产出
        assertEquals(emptyList<String>(), scanner.feed(",{\"c\":3}]"))

        // 与旧实现一致：一次性喂截断文本同样只返回完整对象
        assertEquals(listOf("{\"a\":1}"), PartialJsonObjects.extractObjects(truncated, "tips"))
        assertEquals(listOf("{\"a\":1}"), LegacyOneShotParser.extractObjects(truncated, "tips", Cost()))

        // 尾部还没到第一个对象闭合：什么都不许吐
        val dangling = listOf(
            "{\"tips\":[",
            "{\"tips\":[{",
            "{\"tips\":[{\"a\"",
            "{\"tips\":[{\"a\":",
            "{\"tips\":[{\"a\":1",
            "{\"tips\":[{\"a\":1,",
        )
        for (prefix in dangling) {
            assertEquals("partial object leaked for prefix <$prefix>", emptyList<String>(), IncrementalJsonObjectScanner("tips").feed(prefix))
        }
    }

    @Test
    fun `incomplete trailing single object stays null`() {
        val full = "{\"response\":{\"a\":{\"b\":1},\"c\":\"x\"}}"
        val expected = "{\"a\":{\"b\":1},\"c\":\"x\"}"
        assertEquals(expected, PartialJsonObjects.extractKeyObject(full, "response"))
        assertEquals(expected, LegacyOneShotParser.extractKeyObject(full, "response", Cost()))

        // valueEnd = 值对象自身闭合的位置；在它之前任何断点都不许出结果
        val valueEnd = full.indexOf(expected) + expected.length
        for (cut in 1 until valueEnd) {
            val open = IncrementalSingleObjectScanner("response")
            assertNull("premature result at cut $cut", open.feed(full.substring(0, cut)))
        }
        for (cut in 1 until valueEnd) {
            val s = IncrementalSingleObjectScanner("response")
            s.feed(full.substring(0, cut))
            assertEquals("restored at cut $cut", expected, s.feed(full.substring(cut)))
            assertTrue("must close after the object completes", s.isDone)
            assertNotNull(s.result)
        }
        // 真正的未闭合尾部：差最后一个 }
        val neverClosed = IncrementalSingleObjectScanner("response")
        assertNull(neverClosed.feed(full.substring(0, valueEnd - 1)))
        assertTrue(!neverClosed.isDone)
        assertEquals(expected, neverClosed.feed("}"))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3) 代价形状：旧路径平方级 / 新路径线性
    // ══════════════════════════════════════════════════════════════════════

    /** 生产的节流阈值：每积累 50 字重新解析一次全量缓冲 */
    private val productionChunkChars = 50

    private fun chunksOf(text: String, size: Int): List<String> =
        (0 until text.length step size).map { text.substring(it, min(it + size, text.length)) }

    /**
     * per-thread 分配字节计数器。
     *
     * android.jar 里没有 java.lang.management / com.sun.management，所以这里反射
     * 真正跑单测的 JDK（Gradle 起的 JVM 是完整 JDK，反射可用）。拿不到就直接失败——
     * 不允许「测不到就跳过」，那正是审计点名的那种假绿。
     */
    private class AllocMeter {
        private val bean: Any
        private val read: java.lang.reflect.Method

        init {
            val factory = Class.forName("java.lang.management.ManagementFactory")
            val mx = factory.getMethod("getThreadMXBean").invoke(null)
            assertTrue("ManagementFactory.getThreadMXBean() returned null", mx != null)
            bean = mx!!
            val sun = Class.forName("com.sun.management.ThreadMXBean")
            assertTrue(
                "the unit-test JVM must expose com.sun.management.ThreadMXBean, got ${bean.javaClass.name}",
                sun.isInstance(bean),
            )
            assertTrue(
                "per-thread allocation counting is not supported on this JVM — the cost proof would be vacuous",
                sun.getMethod("isThreadAllocatedMemorySupported").invoke(bean) as Boolean,
            )
            sun.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(bean, true)
            read = sun.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        }

        @Suppress("DEPRECATION")
        fun bytes(): Long = read.invoke(bean, Thread.currentThread().id) as Long
    }

    /** 旧路径的流式用法：每 chunk 复制全量缓冲 + 从头重扫（真实执行、插桩计数） */
    private fun runLegacyStreaming(chunks: List<String>, key: String, cost: Cost): Pair<List<String>, Long> {
        val acc = StringBuilder()
        var last: List<String> = emptyList()
        val t0 = System.nanoTime()
        for (c in chunks) {
            acc.append(c)
            val snapshot = acc.toString() // ← 审计点名的 rawBuffer.toString()
            cost.charsCopied += snapshot.length
            last = LegacyOneShotParser.extractObjects(snapshot, key, cost)
        }
        return last to (System.nanoTime() - t0) / 1_000_000
    }

    /** 新路径：每个字符只喂一次 */
    private fun runIncremental(chunks: List<String>, key: String): List<String> {
        val scanner = IncrementalJsonObjectScanner(key)
        val out = ArrayList<String>(64)
        for (c in chunks) out += scanner.feed(c)
        assertEquals("emittedCount must match the emitted list", scanner.emittedCount, out.size)
        return out
    }

    private class Row(
        val tokens: Int,
        val chars: Int,
        val prefixCharSum: Long,
        val legacyScanned: Long?,
        val legacyCopied: Long?,
        val legacyMs: Long,
        val newMsMin: Double,
        val newAllocBytes: Long,
        val newCharsFed: Long,
        val objects: Int,
    )

    @Test
    fun `legacy one-shot streaming is quadratic while the incremental scanner is not`() {
        val bean = AllocMeter()
        val sizes = intArrayOf(1_000, 4_000, 16_000, 64_000)
        val rows = ArrayList<Row>(sizes.size)
        // 用 1k 那行标定「每前缀字符扫描数」，再验证它能预测 4k 的实测值；
        // 16k/64k 的旧路径不执行（要几十秒），用已验证的模型推算并在报告里标注。
        var alpha = 0.0

        for (tokens in sizes) {
            val text = arrayText(key = "tips", objectCount = tokens, seed = 5_150L)
            val chunks = chunksOf(text, productionChunkChars)
            var acc = 0L
            var prefixCharSum = 0L
            for (c in chunks) {
                acc += c.length
                prefixCharSum += acc
            }

            var legacyScanned: Long? = null
            var legacyCopied: Long? = null
            var legacyMs = -1L
            if (tokens <= 4_000) {
                val cost = Cost()
                val (objs, ms) = runLegacyStreaming(chunks, "tips", cost)
                assertEquals("legacy streaming must still produce every object at $tokens tokens", tokens, objs.size)
                legacyScanned = cost.charsScanned
                legacyCopied = cost.charsCopied
                legacyMs = ms
                if (tokens == 1_000) alpha = cost.charsScanned.toDouble() / prefixCharSum
                if (tokens == 4_000) {
                    val predicted = alpha * prefixCharSum
                    val err = abs(predicted - cost.charsScanned) / cost.charsScanned.toDouble()
                    assertTrue(
                        "the char-scan model calibrated at 1k tokens must predict 4k (predicted $predicted, " +
                            "measured ${cost.charsScanned}, error $err)",
                        err <= 0.15,
                    )
                }
            }

            // 新路径：真实执行，墙钟取 5 次最小，分配取 5 次最大（保守）
            repeat(2) { runIncremental(chunks, "tips") }
            var best = Double.MAX_VALUE
            var alloc = 0L
            var objects = 0
            var fed = 0L
            repeat(5) {
                val a0 = bean.bytes()
                val t0 = System.nanoTime()
                val out = runIncremental(chunks, "tips")
                val t1 = System.nanoTime()
                best = min(best, (t1 - t0) / 1e6)
                alloc = max(alloc, bean.bytes() - a0)
                objects = out.size
                fed = chunks.sumOf { it.length }.toLong()
            }
            assertEquals("incremental path must emit every object at $tokens tokens", tokens, objects)
            assertEquals("the contract is 'each character fed once'", text.length.toLong(), fed)

            rows += Row(
                tokens = tokens,
                chars = text.length,
                prefixCharSum = prefixCharSum,
                legacyScanned = legacyScanned,
                legacyCopied = legacyCopied,
                legacyMs = legacyMs,
                newMsMin = best,
                newAllocBytes = alloc,
                newCharsFed = fed,
                objects = objects,
            )
        }

        val r1k = rows[0]
        val r4k = rows[1]
        val legacy1k = requireNotNull(r1k.legacyScanned)
        val legacy4k = requireNotNull(r4k.legacyScanned)
        val legacyExponent = ln(legacy4k.toDouble() / legacy1k.toDouble()) / ln(r4k.chars.toDouble() / r1k.chars.toDouble())
        val legacyCopy1k = requireNotNull(r1k.legacyCopied)
        val legacyCopy4k = requireNotNull(r4k.legacyCopied)
        val copyExponent = ln(legacyCopy4k.toDouble() / legacyCopy1k.toDouble()) / ln(r4k.chars.toDouble() / r1k.chars.toDouble())

        // —— 断言 1：旧路径是平方级（插桩精确计数，无定时噪声）——
        assertTrue(
            "legacy streaming must look quadratic: charsScanned $legacy1k @ ${r1k.chars} -> $legacy4k @ ${r4k.chars} " +
                "gives exponent $legacyExponent (quadratic == 2.0)",
            legacyExponent >= 1.85,
        )
        assertTrue("legacy rawBuffer.toString()+replace copies must look quadratic too: exponent $copyExponent", copyExponent >= 1.85)

        // —— 断言 2：新路径不是平方级（分配 + 墙钟两个独立口径）——
        for (i in 1 until rows.size) {
            val prev = rows[i - 1]
            val cur = rows[i]
            val charRatio = cur.chars.toDouble() / prev.chars.toDouble()
            val allocExponent = ln(cur.newAllocBytes.toDouble() / prev.newAllocBytes.toDouble()) / ln(charRatio)
            val timeExponent = ln(cur.newMsMin / prev.newMsMin) / ln(charRatio)
            assertTrue(
                "incremental allocation scaling is super-linear: ${prev.chars} -> ${cur.chars} chars, " +
                    "${prev.newAllocBytes} -> ${cur.newAllocBytes} bytes (exponent $allocExponent)",
                allocExponent <= 1.30,
            )
            // 只在两边都到毫秒量级时检查墙钟指数，小规模定时噪声没有意义
            if (prev.newMsMin > 2.0) {
                assertTrue(
                    "incremental wall-clock scaling looks quadratic: ${prev.chars} -> ${cur.chars} chars, " +
                        "${prev.newMsMin}ms -> ${cur.newMsMin}ms (exponent $timeExponent)",
                    timeExponent <= 1.60,
                )
            }
        }

        val biggest = rows.last()
        assertTrue(
            "64k objects (${biggest.chars} chars) must stay in the tens of milliseconds per full pass; " +
                "measured ${biggest.newMsMin}ms (budget 400ms, a quadratic pass here is minutes)",
            biggest.newMsMin < 400.0,
        )
        // 同一规模旧路径要扫的字符数（模型推算），必须是新路径输入量的数量级以上
        val legacyModel = (alpha * biggest.prefixCharSum).toLong()
        assertTrue(
            "the model says legacy would scan $legacyModel chars for ${biggest.chars} chars of input",
            legacyModel > biggest.chars * 50L,
        )

        val report = buildScalingReport(rows, alpha, legacyExponent, copyExponent, legacyModel)
        println(report)
        writeReport(report, append = false)
    }

    private fun buildScalingReport(
        rows: List<Row>,
        alpha: Double,
        legacyExponent: Double,
        copyExponent: Double,
        legacyModelAtBiggest: Long,
    ): String {
        val legacyByRow = rows.map {
            Pair(it.legacyScanned ?: (alpha * it.prefixCharSum).toLong(), it.legacyScanned != null)
        }
        return buildString {
            appendLine("P3-04 incremental JSON stream parser — scaling evidence")
            appendLine("JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
            appendLine("workload: {\"tips\":[{...}]} re-parsed every $productionChunkChars chars (the production threshold)")
            appendLine("legacy reference: PartialJsonObjects.extractObjects as of commit 6d67b37~1, instrumented char counter")
            appendLine("new path: com.lovebrain.app.util.IncrementalJsonObjectScanner, fed chunk-by-chunk")
            appendLine()
            appendLine(
                String.format(
                    "%-10s %-10s %-20s %-16s %-14s %-16s %-10s %s",
                    "objects", "chars", "legacy charsScanned", "legacy ms", "new wall ms", "new alloc bytes", "new chars fed", "legacy basis",
                )
            )
            rows.forEachIndexed { i, r ->
                val (legacyScans, measured) = legacyByRow[i]
                appendLine(
                    String.format(
                        "%-10s %-10s %-20s %-16s %-14s %-16s %-10s %s",
                        r.tokens,
                        r.chars,
                        "%,d".format(legacyScans),
                        if (r.legacyMs >= 0) "%,d".format(r.legacyMs) else "not executed",
                        "%.3f".format(r.newMsMin),
                        "%,d".format(r.newAllocBytes),
                        "%,d".format(r.newCharsFed),
                        if (measured) "MEASURED" else "MODEL (not executed)",
                    )
                )
            }
            appendLine()
            appendLine("measured scaling exponents (2.0 == quadratic, 1.0 == linear):")
            appendLine("  legacy charsScanned  1k->4k objects : $legacyExponent")
            appendLine("  legacy charsCopied   1k->4k objects : $copyExponent")
            appendLine("  new alloc bytes      per 4x step    : " + rows.drop(1).mapIndexed { i, r -> "%.2f".format(ln(r.newAllocBytes.toDouble() / rows[i].newAllocBytes.toDouble()) / ln(r.chars.toDouble() / rows[i].chars.toDouble())) }.joinToString(", "))
            appendLine("  new wall ms          per 4x step    : " + rows.drop(1).mapIndexed { i, r -> "%.2f".format(ln(r.newMsMin / rows[i].newMsMin) / ln(r.chars.toDouble() / rows[i].chars.toDouble())) }.joinToString(", "))
            appendLine("model calibration: charsScanned per accumulated-prefix char = $alpha")
            appendLine("MODEL rows are arithmetic from that calibration, NOT executed: the legacy path needs tens of seconds at 16k/64k objects")
            appendLine("  (e.g. legacy would scan $legacyModelAtBiggest chars for the ${rows.last().chars}-char stream that the new path did in ${"%.3f".format(rows.last().newMsMin)}ms)")
            appendLine("not measured here: on-device frame timing — see :benchmark (Macrobenchmark) and BENCHMARK.md")
        }
    }

    private fun writeReport(report: String, append: Boolean) {
        val file = File("build/reports/perf/incremental-json-scaling.txt")
        val dir = file.parentFile
        assertTrue("cannot create the perf report dir: ${dir?.absolutePath}", dir != null && (dir.isDirectory || dir.mkdirs()))
        if (append) file.appendText(report) else file.writeText(report)
        assertTrue("perf report was not written: ${file.absolutePath}", file.length() > 400)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4) 长流回归
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `a 200k character stream completes inside a bounded budget`() {
        val objectCount = 2_770
        val text = arrayText(key = "tips", objectCount = objectCount, seed = 20_260_924L)
        val chunks = chunksOf(text, productionChunkChars)
        assertTrue("fixture must be ~200k chars, was ${text.length}", text.length in 180_000..260_000)

        val bean = AllocMeter()
        var emitted = runIncremental(chunks, "tips") // warmup
        var bestMs = Double.MAX_VALUE
        var alloc = 0L
        repeat(3) {
            val a0 = bean.bytes()
            val t0 = System.nanoTime()
            emitted = runIncremental(chunks, "tips")
            val t1 = System.nanoTime()
            bestMs = min(bestMs, (t1 - t0) / 1e6)
            alloc = max(alloc, bean.bytes() - a0)
        }

        assertEquals("every object must arrive exactly once", objectCount, emitted.size)
        assertEquals("no partial object may leak", 0, emitted.count { !isSelfComplete(it) })
        assertEquals("the long stream must match the one-shot result", PartialJsonObjects.extractObjects(text, "tips"), emitted)

        // 预算：实测 200k 字符 ~1.2ms / 0.8MB。把旧路径原样接回来在这个规模是
        // ~1.3s / ~2.5GB（4k-object 那行的实测值按平方外推），两条预算都会越界。
        assertTrue("200k char stream took ${bestMs}ms (budget 400ms; a quadratic regression costs ~1.3s here)", bestMs < 400.0)
        assertTrue("200k char stream allocated $alloc bytes (budget 100MB; a quadratic regression allocates ~2.5GB)", alloc < 100L * 1024 * 1024)

        val line = "long-stream: ${text.length} chars / ${chunks.size} feeds -> " +
            "%.3fms (min of 3), %,d bytes allocated, ${emitted.size} objects".format(bestMs, alloc)
        println(line)
        writeReport(line + System.lineSeparator(), append = true)
    }

    @Test
    fun `scanner survives reset and rejects input after the array closed`() {
        val text = arrayText("tips", 3, 11L)
        val scanner = IncrementalJsonObjectScanner("tips")
        val first = feedChunks(splitChunks(text, 5L), "tips")
        assertEquals(3, first.size)
        scanner.reset()
        assertEquals(3, feedChars(scanner, text).size)
        assertEquals(3, scanner.emittedCount)

        val done = IncrementalJsonObjectScanner("tips")
        feedChars(done, text)
        assertTrue(done.isDone)
        assertEquals(emptyList<String>(), done.feed(",{\"tips\":[{\"z\":99}]}]"))
    }
}
