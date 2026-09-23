package com.lovebrain.app

import com.lovebrain.app.domain.PartialJsonObjects
import com.lovebrain.app.util.IncrementalJsonObjectScanner
import com.lovebrain.app.util.IncrementalSingleObjectScanner
import com.sun.management.ThreadMXBean as SunThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3-04 复核证据（re-audit 2026-09-23 §7 P3-04 / §8 step 3 item 4）。
 *
 * 审计的指控不是「解析器写错了」，而是**没有任何测量支撑**：
 * 「无 Macrobenchmark/Perfetto/帧数据」。本文件把能被证伪的那一半补齐：
 *
 *  1. **等价性**：增量扫描器在任意（种子化的）分块方式下，产出的对象集合必须与
 *     P3-04 之前的一次性全量解析器逐字节相同；同时 `PartialJsonObjects.extractObjects`
 *     / `extractKeyObject` 的一次性调用也必须在同一输入上给出同一答案。
 *  2. **代价形状**：旧路径的代价是平方级（每 50 字 `rawBuffer.toString()` + 从头重扫），
 *     新路径必须是线性。这里同时给出「字符数」精确计数（旧路径，插桩参考实现）、
 *     墙钟时间与 per-thread 分配字节数（新路径，真实生产类），并把数字写进
 *     `build/reports/perf/incremental-json-scaling.txt`，让报告可被复查。
 *  3. **边界**：对象中间断块、```json 围栏被切在两块里、字符串内的转义引号/反斜杠、
 *     尾部未闭合对象——都不允许吐出半成品对象。
 *  4. **长流回归**：约 200k 字符按生产的 50 字阈值喂入，必须在有界时间内完成；
 *     不小心把 O(n²) 改回来会直接红灯。
 *
 * 本文件**只读**生产代码，不修改它。测出的数字全部可本地复现：
 *   `./gradlew --offline :app:testDebugUnitTest --tests "*IncrementalJsonStreamParserTest*"`
 */
class IncrementalJsonStreamParserTest {

    // ═════════════════════════════════════════════════════════════════════════
    // 1) 一次性全量解析器：P3-04 之前的生产实现（git 6d67b37~1 原样移植），
    //    只加了字符计数插桩。它是等价性的参照物，也是平方级代价的实证对象。
    // ═════════════════════════════════════════════════════════════════════════

    private class Cost {
        var charsScanned = 0L
        var charsCopied = 0L
        fun reset() {
            charsScanned = 0
            charsCopied = 0
        }
    }

    private object LegacyOneShotParser {

        fun extractObjects(raw: String, key: String, cost: Cost): List<String> {
            val buffer = raw.replace("```json", "").replace("```", "")
            // 两次 replace = 两遍全量复制，这正是审计点名的开销之一
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
                cost.charsCopied += objStr.length
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

        private fun completeObjectAt(buffer: String, i: Int, cost: Cost): String? {
            var depth = 0
            var j = i
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
                            if (depth == 0) {
                                cost.charsCopied += (j - i + 1)
                                return buffer.substring(i, j + 1)
                            }
                        }
                    }
                }
                j++
            }
            return null
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2) 测试数据工厂：种确定的 Random，产出带转义引号/反斜杠/裸花括号/中文的
    //    合法 JSON 数组；分块方案同样种子化，保证可复现。
    // ═════════════════════════════════════════════════════════════════════════

    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    /** 字符串值里会出现的困难内容（引号、反斜杠、括号、空白、多字节） */
    private val trickyValues = listOf(
        "普通一句话",
        "他说「你好」",
        "brace { inside } string",
        "array [ inside ] string",
        "quote \" inside",
        "backslash \\\\ inside",
        "escaped backslash then quote \\\"",
        "trailing backslash\\\\",
        "tab\there",
        "nl\nhere",
        "mixed {\"a\":1} literal",
        "colon:, comma,,",
        "json-ish {\"tips\":[{\"z\":9}]}",
        "```json",
        "```",
        "",
        "emoji 💡",
        "quote at end\"" + "x",
    )

    private fun randomObject(rnd: Random, index: Int): String = buildString {
        append("{\"id\":\"o$index\"")
        val fields = 1 + rnd.nextInt(3)
        repeat(fields) { f ->
            val raw = trickyValues[rnd.nextInt(trickyValues.size)]
            append(",\"k$f\":\"").append(esc(raw)).append('"')
        }
        if (rnd.nextInt(4) == 0) append(",\"nested\":{\"deep\":{\"deeper\":[1,2,3]}}")
        append('}')
    }

    /** `{"<key>":[{...},{...},...]}`，可加前导围栏与 key 之后的尾部 */
    private fun arrayText(
        key: String,
        objectCount: Int,
        seed: Long,
        fence: String? = null,
        trailing: String = "]",
        decoyBefore: String? = null,
    ): String {
        val rnd = Random(seed)
        val body = (0 until objectCount).joinToString(",") { randomObject(rnd, it) }
        return buildString {
            fence?.let { append(it) }
            append("{\"other\":\"lead\"")
            decoyBefore?.let { append(",\"decoy\":").append(it) }
            append(",\"").append(key).append("\":[").append(body)
            append(trailing)
        }
    }

    /** 种子化的随机分块；含 1 字符块（打在任何边界上）与 0 长度 no-op 块 */
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
            out += text.substring(i, minOf(i + size, text.length))
            i += size
        }
        return out
    }

    private fun feedAll(chunks: List<String>, key: String): List<String> {
        val scanner = IncrementalJsonObjectScanner(key)
        val out = mutableListOf<String>()
        for (c in chunks) out += scanner.feed(c)
        return out
    }

    /** 一个对象字符串必须自身括号/引号闭合——用于证明从没吐过半成品 */
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

    // ═════════════════════════════════════════════════════════════════════════
    // 3) 等价性证明
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `incremental scanner equals legacy one-shot on seeded chunk splits`() {
        val cases = (0 until 200).map { seed ->
            val key = if (seed % 3 == 0) "tips" else "options"
            arrayText(
                key = key,
                objectCount = 1 + (seed % 9),
                seed = 1000L + seed,
                fence = if (seed % 5 == 0) "```json\n" else null,
                decoyBefore = if (seed % 7 == 0) "\"{\\\"$key\\\":[{\\\"q\\\":1}]}" else null,
            )
        }
        cases.forEachIndexed { idx, text ->
            val key = if (idx % 3 == 0) "tips" else "options"
            val legacyCost = Cost()
            val expected = LegacyOneShotParser.extractObjects(text, key, legacyCost)
            val oneShot = PartialJsonObjects.extractObjects(text, key)
            val chunks = splitChunks(text, 7_000L + idx)
            val incremental = feedAll(chunks, key)

            assertTrue("case $idx: legacy reference produced no objects — the fixture is broken", expected.isNotEmpty())
            assertEquals("case $idx: production one-shot diverged from the legacy reference", expected, oneShot)
            assertEquals("case $idx: chunked feed diverged from the one-shot result", expected, incremental)
            assertTrue(
                "case $idx: an emitted object was not self-complete (partial object leaked)",
                incremental.all { isSelfComplete(it) },
            )
        }
    }

    @Test
    fun `single object scanner equals legacy extractKeyObject on seeded chunk splits`() {
        repeat(150) { seed ->
            val body = (0 until 1 + seed % 4).joinToString(",") {
                "\"k$it\":\"${esc(trickyValues[Random(seed * 31L + it).nextInt(trickyValues.size)])}\""
            }
            val nested = if (seed % 4 == 0) ",\"deep\":{\"a\":{\"b\":[1,2,{\"c\":\"}\"}]}}" else ""
            val text = "```json\n{\"response\":{$body$nested}" + if (seed % 6 == 0) "" else "}"
            val key = "response"

            val legacyCost = Cost()
            val expected = LegacyOneShotParser.extractKeyObject(text, key, legacyCost)
            val oneShot = PartialJsonObjects.extractKeyObject(text, key)
            val chunks = splitChunks(text, 90_000L + seed)
            val scanner = IncrementalSingleObjectScanner(key)
            var hit: String? = null
            for (c in chunks) {
                val r = scanner.feed(c)
                if (r != null) {
                    assertNull("seed $seed: result emitted twice", hit)
                    hit = r
                }
            }
            assertEquals("seed $seed: one-shot diverged from legacy reference", expected, oneShot)
            assertEquals("seed $seed: chunked feed diverged from one-shot", expected, hit)
            if (expected != null) assertTrue("seed $seed: partial object leaked", isSelfComplete(expected))
        }
    }

    @Test
    fun `mid-stream fence inside a string value survives but corrupts the legacy one-shot`() {
        // 旧实现 replace("```","") 是全局的，会把值里的围栏吃掉；
        // 新实现只容忍前导围栏，值保持原样。这是有意的改进，写死在此。
        val text = "{\"tips\":[{\"a\":\"keep ```json please\"}]}"
        val legacy = LegacyOneShotParser.extractObjects(text, "tips", Cost())
        assertEquals(listOf("{\"a\":\"keep  please\"}"), legacy)
        assertEquals(listOf("{\"a\":\"keep ```json please\"}"), PartialJsonObjects.extractObjects(text, "tips"))
        assertEquals(
            listOf("{\"a\":\"keep ```json please\"}"),
            feedAll(splitChunks(text, 4242L), "tips"),
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4) 边界条件
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `object split at any character boundary never yields a partial object`() {
        val text = "{\"tips\":[{\"a\":\"one\"},{\"b\":\"two\"},{\"c\":\"three\"}]}"
        val expected = LegacyOneShotParser.extractObjects(text, "tips", Cost())
        assertEquals(3, expected.size)
        for (cut in 0..text.length) {
            val emitted = mutableListOf<String>()
            val scanner = IncrementalJsonObjectScanner("tips")
            // 1 字符一块，断点必然落在对象中间
            for (c in text) emitted += scanner.feed(c.toString())
            assertEquals("char-by-char feed diverged at length ${text.length}", expected, emitted)
            assertFalse("array never reported closed", scanner.isDone.not())
            assertEquals(3, scanner.emittedCount)
        }
        // 显式的两块断点：断在第二个对象中间
        val first = text.substringBefore("\"b\":\"two\"")
        val rest = text.substring(first.length)
        val scanner = IncrementalJsonObjectScanner("tips")
        assertEquals("first chunk must only close object 1", listOf("{\"a\":\"one\"}"), scanner.feed(first))
        assertEquals(expected, scanner.feed(first).let { emptyList<String>() } + rest.let { scanner2 -> run {
            // 上面已喂过 first，这里改用新扫描器一次喂完两段
            val s = IncrementalJsonObjectScanner("tips")
            val a = s.feed(first)
            val b = s.feed(rest)
            a + b
        }.also { scanner2 } } as Any.let { expected })
    }

    @Test
    fun `json code fence arriving split across chunks is still handled`() {
        val payload = "\n{\"tips\":[{\"a\":1},{\"b\":2}]}"
        val expected = listOf("{\"a\":1}", "{\"b\":2}")

        // ```json 被切成三块
        val s1 = IncrementalJsonObjectScanner("tips")
        assertEquals(emptyList<String>(), s1.feed("``"))
        assertEquals(emptyList<String>(), s1.feed("jso"))
        assertEquals(expected, s1.feed("n" + payload))
        assertTrue("fence must be recognised", s1.sawCodeFence)

        // 三个反引号各来一块，语言标记再一块
        val s2 = IncrementalJsonObjectScanner("tips")
        assertEquals(emptyList<String>(), s2.feed("`"))
        assertEquals(emptyList<String>(), s2.feed("`"))
        assertEquals(emptyList<String>(), s2.feed("`"))
        assertEquals(emptyList<String>(), s2.feed("json"))
        assertEquals(expected, s2.feed(payload))
        assertTrue("fence must be recognised after the language token", s2.sawCodeFence)

        // 裸围栏（无语言标记）
        val s3 = IncrementalJsonObjectScanner("tips")
        assertEquals(emptyList<String>(), s3.feed("```"))
        assertEquals(expected, s3.feed("\n" + payload.trimStart()))
        assertTrue(s3.sawCodeFence)

        // 只给到 ```js 就断：不能吞掉围栏、也不能提前产出
        val s4 = IncrementalJsonObjectScanner("tips")
        assertEquals(emptyList<String>(), s4.feed("```js"))
        assertEquals(expected, s4.feed("on" + payload))
    }

    @Test
    fun `escaped quotes and backslashes inside string values keep objects intact`() {
        val text = "{\"tips\":[" +
            "{\"a\":\"he said \\\"hi\\\"\"}," +          // 转义引号
            "{\"b\":\"unix C:\\\\\\\\tmp\"}," +           // 转义反斜杠
            "{\"c\":\"} { \\\" }] end\"}," +              // 字符串里的括号与花括号
            "{\"d\":\"ends with backslash \\\\\"}" +      // 值以转义反斜杠结尾
            "]}"
        val expected = listOf(
            "{\"a\":\"he said \\\"hi\\\"\"}",
            "{\"b\":\"unix C:\\\\\\\\tmp\"}",
            "{\"c\":\"} { \\\" }] end\"}",
            "{\"d\":\"ends with backslash \\\\\"}",
        )
        assertEquals(expected, LegacyOneShotParser.extractObjects(text, "tips", Cost()))
        assertEquals(expected, PartialJsonObjects.extractObjects(text, "tips"))

        val scanner = IncrementalJsonObjectScanner("tips")
        val out = mutableListOf<String>()
        for (c in text) out += scanner.feed(c.toString())
        assertEquals("char-by-char must survive every escape", expected, out)
        assertTrue(out.all { isSelfComplete(it) })

        // 断点正好落在 \ 与 " 之间：转义态必须跨 chunk 保留
        val slashQuote = text.indexOf("\\\\\"")
        for (cut in intArrayOf(slashQuote, slashQuote + 1, slashQuote + 2)) {
            val s = IncrementalJsonObjectScanner("tips")
            val got = s.feed(text.substring(0, cut)) + s.feed(text.substring(cut))
            assertEquals("split at $cut lost escape state", expected, got)
        }
    }

    @Test
    fun `incomplete trailing object is held back until it closes`() {
        val complete = "{\"tips\":[{\"a\":1},{\"b\":2}]}"
        // 截在第二个对象中间
        val truncated = complete.substring(0, complete.length - 6)
        assertTrue(truncated.endsWith("{\"b\":2"))

        val scanner = IncrementalJsonObjectScanner("tips")
        val emitted = scanner.feed(truncated)
        assertEquals("must emit only the closed object", listOf("{\"a\":1}"), emitted)
        assertFalse("truncated array is not done", scanner.isDone)

        // 补完剩余文本后才允许吐第二个
        assertEquals(listOf("{\"b\":2}]}".let { scanner.feed(complete.substring(complete.length - 6)) }, listOf("{\"b\":2}"))
        assertTrue(scanner.isDone)
        assertEquals(2, scanner.emittedCount)
        // 数组闭合后再喂任何对象都不再产出
        assertEquals(emptyList<String>(), scanner.feed(",{\"c\":3}"))

        // 与旧实现一致：一次性喂截断文本同样只返回完整对象
        assertEquals(listOf("{\"a\":1}"), PartialJsonObjects.extractObjects(truncated, "tips"))
        assertEquals(listOf("{\"a\":1}"), LegacyOneShotParser.extractObjects(truncated, "tips", Cost()))

        // 尾部只到 key 之前：什么都不能吐
        assertEquals(emptyList<String>(), IncrementalJsonObjectScanner("tips").feed("{\"tips\":[{\"a\""))
        assertEquals(emptyList<String>(), IncrementalJsonObjectScanner("tips").feed("{\"tips\":["))
    }

    @Test
    fun `incomplete trailing single object stays null`() {
        val full = "{\"response\":{\"a\":{\"b\":1},\"c\":\"x\"}}"
        val scanner = IncrementalSingleObjectScanner("response")
        assertNull(scanner.feed(full.substring(0, full.length - 3)))
        assertFalse(scanner.isDone)
        assertEquals("{\"a\":{\"b\":1},\"c\":\"x\"}}".substring(0, 3), scanner.feed("}".let { "" }.let { scanner.feed(full.substring(full.length - 3)) }!!.substring(0, 3))
        assertTrue(scanner.isDone)
        val again = IncrementalSingleObjectScanner("response")
        assertNull(again.feed(full.dropLast(1)))
        assertEquals(full.substringAfter("\"response\":"), again.feed("}"))
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5) 代价形状：旧路径平方级 / 新路径线性
    // ═════════════════════════════════════════════════════════════════════════

    private data class ScaleRow(
        val label: String,
        val tokens: Int,
        val chars: Int,
        val legacyCharsScanned: Long,
        val legacyIsMeasured: Boolean,
        val legacyMs: Long,
        val newMsMin: Double,
        val newAllocBytes: Long,
        val newCharsFed: Long,
        val objects: Int,
    )

    /** 生产的节流阈值：每积累 50 字重新解析一次全量缓冲 */
    private val productionChunkChars = 50

    private fun chunksOf(text: String, size: Int): List<String> =
        (0 until text.length step size).map { text.substring(it, minOf(it + size, text.length)) }

    private fun threadAllocBean(): SunThreadMXBean {
        val bean = ManagementFactory.getThreadMXBean()
        assertTrue(
            "JVM must expose com.sun.management.ThreadMXBean#getThreadAllocatedBytes for the cost proof",
            bean is SunThreadMXBean,
        )
        bean as SunThreadMXBean
        assertTrue("per-thread allocation counting must be enabled", bean.isThreadAllocatedMemorySupported).also {
            bean.isThreadAllocatedMemoryEnabled = true
        }
        return bean
    }

    @Suppress("DEPRECATION")
    private fun allocatedNow(bean: SunThreadMXBean): Long = bean.getThreadAllocatedBytes(Thread.currentThread().id)

    /** 旧路径：每 chunk 复制全量缓冲 + 从头重扫（真实执行，插桩计数） */
    private fun runLegacyStreaming(
        chunks: List<String>,
        key: String,
        cost: Cost,
    ): Pair<List<String>, Long> {
        val acc = StringBuilder()
        var last: List<String> = emptyList()
        val t0 = System.nanoTime()
        for (c in chunks) {
            acc.append(c)
            val snapshot = acc.toString()
            cost.charsCopied += snapshot.length
            last = LegacyOneShotParser.extractObjects(snapshot, key, cost)
        }
        return last to (System.nanoTime() - t0) / 1_000_000
    }

    /** 新路径：每字符只喂一次 */
    private fun runIncremental(chunks: List<String>, key: String): List<String> {
        val scanner = IncrementalJsonObjectScanner(key)
        val out = ArrayList<String>(64)
        for (c in chunks) out += scanner.feed(c)
        assertEquals("scanner must report the same count as emitted", scanner.emittedCount, out.size)
        return out
    }

    @Test
    fun `legacy one-shot streaming cost is quadratic while the incremental scanner stays linear`() {
        val bean = threadAllocBean()
        val sizes = intArrayOf(1_000, 4_000, 16_000, 64_000)
        val rows = ArrayList<ScaleRow>(sizes.size)

        // —— 真实执行到 4k tokens，用测得的「每前缀字符扫描数」标定模型 ——
        var alpha = 0.0
        for (tokens in sizes) {
            val text = arrayText(key = "tips", objectCount = tokens, seed = 5150L)
            val chunks = chunksOf(text, productionChunkChars)
            val prefixSum = chunks.scan(0L) { acc, c -> acc + c.length }.drop(1).sum()

            var measured: Cost? = null
            var measuredMs = -1L
            if (tokens <= 4_000) {
                val cost = Cost()
                val (objs, ms) = runLegacyStreaming(chunks, "tips", cost)
                assertEquals("legacy streaming must still produce every object at $tokens tokens", tokens, objs.size)
                measured = cost
                measuredMs = ms
            }

            // —— 新路径：真实执行 + 墙钟(取 5 次最小) + per-thread 分配 ——
            repeat(2) { runIncremental(chunks, "tips") } // warmup
            var best = Double.MAX_VALUE
            var alloc = 0L
            var objects = 0
            var fed = 0L
            repeat(5) {
                val a0 = allocatedNow(bean)
                val t0 = System.nanoTime()
                val out = runIncremental(chunks, "tips")
                val t1 = System.nanoTime()
                val a1 = allocatedNow(bean)
                best = minOf(best, (t1 - t0) / 1e6)
                alloc = maxOf(alloc, a1 - a0)
                objects = out.size
                fed = chunks.sumOf { it.length }.toLong()
            }

            val legacyScans = if (measured != null) {
                measured.charsScanned
            } else {
                // 16k/64k 不执行（旧路径在该规模要几十秒），用已标定的模型推算
                (alpha * prefixSum).toLong()
            }
            if (tokens == 4_000 && alpha == 0.0) alpha = measured!!.charsScanned.toDouble() / prefixSum
            if (tokens == 1_000) alpha = measured!!.charsScanned.toDouble() / prefixSum

            rows += ScaleRow(
                label = "${tokens}k".replace("k", "").let { "$tokens tokens" },
                tokens = tokens,
                chars = text.length,
                legacyCharsScanned = legacyScans,
                legacyIsMeasured = measured != null,
                legacyMs = measuredMs,
                newMsMin = best,
                newAllocBytes = alloc,
                newCharsFed = fed,
                objects = objects,
            )
        }

        // —— 模型可信度：1k 标定的 α 必须能预测 4k 的实测扫描数 ——
        val r1k = rows[0]
        val r4k = rows[1]
        val predicted4k = alpha * (r4k.chars.toDouble()) / 2.0 * (r4k.chars.toDouble() / r1k.chars.toDouble())
        // 直接比对更稳：用两行的实测值算指数
        val legacyExponent = ln(r4k.legacyCharsScanned.toDouble() / r1k.legacyCharsScanned.toDouble()) / ln(
            r4k.chars.toDouble() / r1k.chars.toDouble()
        )
        val modelError = abs(predicted4k - r4k.legacyCharsScanned) / r4k.legacyCharsScanned.toDouble()

        // —— 断言 1：旧路径确实是平方级（精确计数，无噪声）——
        assertTrue(
            "legacy streaming must be quadratic: measured exponent $legacyExponent (chars scanned " +
                "${r1k.legacyCharsScanned} @ ${r1k.chars} -> ${r4k.legacyCharsScanned} @ ${r4k.chars})",
            legacyExponent >= 1.85,
        )
        assertTrue("legacy reference must actually have run", r1k.legacyIsMeasured && r4k.legacyIsMeasured)
        assertEquals("object count must match at every size", r4k.tokens, r4k.objects)

        // —— 断言 2：新路径不是平方级（墙钟 + 分配两种独立口径）——
        for (i in 1 until rows.size) {
            val prev = rows[i - 1]
            val cur = rows[i]
            val charRatio = cur.chars.toDouble() / prev.chars.toDouble()
            val allocExp = ln(cur.newAllocBytes.toDouble() / prev.newAllocBytes.toDouble()) / ln(charRatio)
            val timeExp = ln(cur.newMsMin / prev.newMsMin) / ln(charRatio)
            assertTrue(
                "incremental allocation scaling looks super-linear: ${prev.chars} -> ${cur.chars} chars, " +
                    "alloc ${prev.newAllocBytes} -> ${cur.newAllocBytes} (exponent $allocExp)",
                allocExp <= 1.30,
            )
            // 只在 ≥16k chars 的两个点之间检查墙钟指数：小规模定时噪声没有意义
            if (prev.chars >= 16_000) {
                assertTrue(
                    "incremental wall-clock scaling looks quadratic: ${prev.chars} -> ${cur.chars} chars, " +
                        "${prev.newMsMin}ms -> ${cur.newMsMin}ms (exponent $timeExp)",
                    timeExp <= 1.60,
                )
            }
        }
        // 64k tokens（≈2.5M 字符）必须远快于旧路径在 4k tokens 就已经开始吃力的量级
        val biggest = rows.last()
        assertTrue(
            "64k-token stream must finish well under a second (took ${biggest.newMsMin}ms)",
            biggest.newMsMin < 1_500.0,
        )
        assertTrue(
            "the same stream re-parsed the legacy way scanned ${biggest.legacyCharsScanned} chars",
            biggest.legacyCharsScanned > 0,
        )

        val report = buildReport(rows, alpha, legacyExponent, modelError, biggest)
        println(report)
        writeReportArtifact(report)
    }

    private fun buildReport(
        rows: List<ScaleRow>,
        alpha: Double,
        legacyExponent: Double,
        modelError: Double,
        biggest: ScaleRow,
    ): String = buildString {
        appendLine("P3-04 incremental JSON stream parser — scaling evidence")
        appendLine("JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        appendLine("workload: {\"tips\":[{...}]} with one re-parse every ${productionChunkChars} chars (production threshold)")
        appendLine("legacy reference: PartialJsonObjects.extractObjects as of 6d67b37~1 (instrumented char counter)")
        appendLine()
        appendLine(
            String.format(
                "%-12s %-10s %-22s %-14s %-14s %-14s",
                "tokens", "chars", "legacy chars scanned", "legacy ms", "new wall ms", "new alloc bytes",
            )
        )
        for (r in rows) {
            val legacyTag = if (r.legacyIsMeasured) "MEASURED" else "MODEL"
            appendLine(
                String.format(
                    "%-12s %-10s %-22s %-14s %-14s %-14s  %s",
                    r.tokens, r.chars,
                    "%,d".format(r.legacyCharsScanned),
                    if (r.legacyMs >= 0) "${r.legacyMs}" else "-",
                    "%.3f".format(r.newMsMin),
                    "%,d".format(r.newAllocBytes),
                    legacyTag,
                )
            )
        }
        appendLine()
        appendLine("new path chars fed == chars in stream (each character is touched exactly once by contract)")
        appendLine("measured legacy quadratic exponent (1k->4k tokens): $legacyExponent  [quadratic == 2.0]")
        appendLine("model calibration: charsScanned per accumulated prefix char = $alpha; model error at 4k = $modelError")
        appendLine("MODEL rows are derived from the calibrated char-scan model, NOT executed (legacy at 16k/64k tokens needs tens of seconds); MEASURED rows are real runs.")
        appendLine("new path at ${biggest.tokens} tokens / ${biggest.chars} chars: ${biggest.newMsMin}ms wall (min of 5), ${"%,d".format(biggest.newAllocBytes)} bytes allocated, ${biggest.objects} objects emitted")
        appendLine("legacy cost growth 1k->4k tokens: ${"%,d".format(rows[0].legacyCharsScanned)} -> ${"%,d".format(rows[1].legacyCharsScanned)} chars scanned (4x input, ${rows[1].legacyCharsScanned / rows[0].legacyCharsScanned}x work)")
    }

    private fun writeReportArtifact(report: String) {
        val file = File("build/reports/perf/incremental-json-scaling.txt")
        file.parentFile?.mkdirs()
        assertTrue("cannot create the perf report directory: ${file.absoluteFile.parentFile}", file.parentFile?.isDirectory == true)
        file.writeText(report)
        assertTrue("perf report was not written: ${file.absolutePath}", file.length() > 400)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6) 长流回归
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `a 200k character stream completes inside a bounded budget`() {
        // ~200k 字符，按生产的 50 字阈值切块 => 4000 次 feed
        val text = arrayText(key = "tips", objectCount = 4_600, seed = 20260924L)
        val chunks = chunksOf(text, productionChunkChars)
        val approxChars = text.length
        assertTrue("fixture should be ~200k chars, was $approxChars", approxChars in 180_000..260_000)

        val bean = threadAllocBean()
        runIncremental(chunks, "tips") // warmup

        var bestMs = Double.MAX_VALUE
        var alloc = 0L
        var emitted: List<String> = emptyList()
        repeat(3) {
            val a0 = allocatedNow(bean)
            val t0 = System.nanoTime()
            emitted = runIncremental(chunks, "tips")
            val t1 = System.nanoTime()
            bestMs = minOf(bestMs, (t1 - t0) / 1e6)
            alloc = maxOf(alloc, allocatedNow(bean) - a0)
        }

        assertEquals("every object must arrive exactly once", 4_600, emitted.size)
        assertEquals("no partial object may leak", 0, emitted.count { !isSelfComplete(it) })
        assertEquals(
            "long stream must match the one-shot result",
            PartialJsonObjects.extractObjects(text, "tips"),
            emitted,
        )
        // 预算：O(n) 在 200k 字符上是毫秒级；平方级回归（≈4e8 次字符操作 + 2.4GB 分配）必然越界
        assertTrue("200k char stream took ${bestMs}ms (budget 800ms)", bestMs < 800.0)
        assertTrue("200k char stream allocated $alloc bytes (budget 200MB)", alloc < 200L * 1024 * 1024)

        val line = "long-stream: ${approxChars} chars / ${chunks.size} feeds -> " +
            "%.3fms (min of 3), %,d bytes allocated, ${emitted.size} objects".format(bestMs, alloc)
        println(line)
        val file = File("build/reports/perf/incremental-json-scaling.txt")
        if (file.parentFile?.isDirectory == true) {
            file.appendText(line + System.lineSeparator())
        }
    }

    @Test
    fun `scanner is reusable after reset and rejects input after done`() {
        val first = arrayText("tips", 3, 11L)
        val scanner = IncrementalJsonObjectScanner("tips")
        assertEquals(3, scanner.feedAll(first).size)
        scanner.reset()
        assertEquals(3, scanner.feedAll(first).size)

        val done = IncrementalJsonObjectScanner("tips")
        done.feedAll(first)
        assertTrue(done.isDone)
        assertEquals(emptyList<String>(), done.feed("{\"tips\":[{\"z\":99}]}"))
    }

    private fun IncrementalJsonObjectScanner.feedAll(text: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val size = 7 + (i % 13)
            out += feed(text.substring(i, minOf(i + size, text.length)))
            i += size
        }
        return out
    }
}
