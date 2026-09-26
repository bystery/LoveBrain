package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P0-03 的**前半句**：「所有 mutation 只能从 [KnowledgeTx] 取得安全路径与原子写能力」
 * （独立复核报告 §3.1、指导书 217 行；后半句"Repository 内禁止出现第二条 `atomicWriteText` 公共链"
 * 由 [ReadOnlySchemaWriteGateTest] 与本文件最后三格合起来盯）。
 *
 * 为什么这一格非有不可（2026-09-26 实测）：这条要求**原本没有任何一格在数**。
 * `ReadOnlySchemaWriteGateTest` 钉的是"字节落盘只有一条实现链"（`rawAtomicWriteText` 定义 1 / 调用 1 /
 * `FileOutputStream` 1），`StorageBoundaryOwnershipTest` 钉的是"不许自己拼 `File(File(knowledgeRoot,`"
 * （owners 确实已是空集）。两把尺都不管"仓库里谁绕过 [KnowledgeTx] 直接调 [atomicWriteText]"。
 * 上一轮把"读/写边界已清零"错当成这一条已清零，就是因为那句话说的是拼接那把尺。
 *
 * **"在唯一写链上"的口径**（写死在 [WRITE_CHAIN]，改它必须连着改基线与注入证据）：
 * 调用点落在下面登记的四个"锁内写核"之一——`writeFileUnlocked`、`appendFileUnlocked`、
 * `writeFileCheckedUnlocked`、`appendFileCheckedUnlocked`。
 * [KnowledgeTx] 的类体自己**不**直接调 [atomicWriteText]，它调这四个核，
 * 所以"作用域内"按这条唯一的委托链认，而不是按花括号包没包住认。
 * 落在登记之外的一切调用点都算**裸写**：它们确实会过 [atomicWriteText] 里那道只读判定（出口侧的保护），
 * 但没有从 [KnowledgeTx] 拿到安全路径——这正是复核报告点名的形状。
 *
 * 三条计数一律判 **==**：多了=又开了一条裸写，少了=欠账还得比登记的干净，回来把基线改小。
 * 注释、KDoc、字符串字面量里的 `atomicWriteText(` 一律不算：先把它们整段抹成空格再数（[blankNonCode]），
 * 抹完保持行列位置逐字符不变，所以报出来的行号能直接对着源文件看。
 *
 * ⚠ 本文件**只数不改**：造尺这一格不动任何生产代码。裸写怎么还（多半是走 `transactionUnlocked`）
 * 是后面每一格的事，每还一批回来把下面三个数改小。
 */
class KnowledgeTxMutationEntryTest {

    private val appDataDir: File
        get() {
            val candidates = listOf(
                File("src/main/java/com/lovebrain/app/data"),
                File("app/src/main/java/com/lovebrain/app/data")
            )
            return candidates.firstOrNull { it.isDirectory }
                ?: error(
                    "找不到 data/ 目录（试过 ${candidates.joinToString { c -> c.path }}）——" +
                        "指错地方的尺比不量更骗人：所有计数都会'恰好为 0'然后全绿"
                )
        }

    private val source: File
        get() = File(appDataDir, "KnowledgeRepository.kt").also {
            if (!it.isFile) error("扫不到 $it，下面那些计数全是假的")
        }

    /** 一处 [atomicWriteText] 调用点：源文件行号 + 它落在哪个声明里（顶层类名已剥掉） */
    private data class WriteSite(val line: Int, val scope: String)

    // ═══════════ 基线（2026-09-26 本机实测；只许往下）═══════════

    /** 唯一写链上的四个锁内写核：[KnowledgeTx.write] / [KnowledgeTx.append] 走的就是这一条 */
    private val WRITE_CHAIN = setOf(
        "writeFileUnlocked",
        "appendFileUnlocked",
        "writeFileCheckedUnlocked",
        "appendFileCheckedUnlocked"
    )

    /**
     * 登记在册的裸写：作用域 → 处数。还掉一批就删一项或把一项改小，**不许往上加**。
     *
     * 2026-09-26 本机实测（就是本文件跑红时报出来的那张表，行号对着 `KnowledgeRepository.kt`）：
     * `ensureInitialKnowledgeBase` 13 处（L684–L703）、`create` 13 处（L825–L842）、
     * `ensureKbFilesCompleteUnlocked` 2 处（L750 L757）、`RepoStorage` 的两个委托各 1 处（L166 L173）。
     */
    private val expectedRawBreakdown = mapOf(
        "create" to 13,
        "ensureInitialKnowledgeBase" to 13,
        "ensureKbFilesCompleteUnlocked" to 2,
        "RepoStorage.atomicWrite" to 1,
        "RepoStorage.guardedWrite" to 1
    )

    /** 调用点总数（写链 4 + 裸写 30）：动一条也要撞到这里 */
    private val expectedTotalSites = 34

    // ═══════════ 扫描工具 ═══════════

    /**
     * 把注释与字符串字面量整段抹成空格，长度与行列位置**逐字符不变**。
     *
     * Kotlin 的块注释可嵌套，所以要记深度；`//` 到行尾；`"…"` / `'…'` 里带转义；`"""…"""` 整段算字面量。
     * （三引号这条不是凭空加的：data/ 里就有源文件用原始字符串，不认的话那一条"别家不许调用"会直接抛。）
     */
    private fun blankNonCode(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var block = 0
        var inLine = false
        var inStr = false
        var inChr = false
        var inRaw = false
        while (i < src.length) {
            val c = src[i]
            val n = if (i + 1 < src.length) src[i + 1] else ' '
            when {
                inRaw -> {
                    if (c == '"' && src.startsWith("\"\"\"", i)) {
                        inRaw = false
                        out.append("   ")
                        i += 3
                    } else {
                        out.append(if (c == '\n') '\n' else ' ')
                        i++
                    }
                }
                inLine -> {
                    if (c == '\n') {
                        inLine = false
                        out.append(c)
                    } else out.append(' ')
                    i++
                }
                block > 0 -> when {
                    c == '/' && n == '*' -> { block++; out.append("  "); i += 2 }
                    c == '*' && n == '/' -> { block--; out.append("  "); i += 2 }
                    else -> { out.append(if (c == '\n') '\n' else ' '); i++ }
                }
                inStr || inChr -> {
                    val quote = if (inStr) '"' else '\''
                    when {
                        c == '\\' -> { out.append("  "); i += 2 }
                        c == quote -> { inStr = false; inChr = false; out.append("  "); i++ }
                        else -> { out.append(if (c == '\n') '\n' else ' '); i++ }
                    }
                }
                c == '/' && n == '/' -> { inLine = true; out.append("  "); i += 2 }
                c == '/' && n == '*' -> { block = 1; out.append("  "); i += 2 }
                c == '"' && src.startsWith("\"\"\"", i) -> { inRaw = true; out.append("   "); i += 3 }
                c == '"' -> { inStr = true; out.append(' '); i++ }
                c == '\'' -> { inChr = true; out.append(' '); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** 声明（class / object / interface / fun，含 `fun <T> name(`）→ 关键字下标 → 名字 */
    private val declRegex = Regex("""\b(?:fun|class|object|interface)\s+(?:<[^>]*>\s*)?([A-Za-z_]\w*)""")

    private fun identifierBefore(text: String, at: Int): Boolean =
        at > 0 && (text[at - 1].isLetterOrDigit() || text[at - 1] == '_' || text[at - 1] == '$')

    /**
     * 一段代码里所有**调用** `name(` 的下标。
     *
     * 两道排除缺一条数出来的就是虚的：
     * ① `rawAtomicWriteText(` 把 `atomicWriteText(` 整个包住 → 靠"前一个字符不是标识符字符"剥掉；
     * ② 定义那一行 `private fun atomicWriteText(` 自己是声明不是调用 → 靠 `fun <名字` 捕获组的下标剥掉。
     */
    private fun callIndices(text: String, name: String): List<Int> {
        val definitions = Regex("""\bfun\s+(?:<[^>]*>\s*)?([A-Za-z_]\w*)\s*\(""")
            .findAll(text)
            .filter { it.groupValues[1] == name }
            .map { it.groups[1]!!.range.first }
            .toSet()
        val hits = mutableListOf<Int>()
        var from = 0
        while (true) {
            val at = text.indexOf("$name(", from)
            if (at < 0) break
            if (!identifierBefore(text, at) && at !in definitions) hits += at
            from = at + 1
        }
        return hits
    }

    /**
     * 给每个调用点配上"它落在哪个声明里"：沿字符走一遍，记花括号深度与一张作用域栈。
     *
     * 栈项是 (名字, 声明出现时的深度)；`}` 把深度降回去时，凡是声明在**不小于**新深度的项一律出栈。
     * 压栈前也要先按同一规则清一遍：`fun x(): T = 表达式` 这种**函数体没有花括号**的声明永远等不到
     * 属于自己的那个 `}`，不清就会赖在栈上——实测把 `writeFileUnlocked` 报成
     * `isWritable.writeFileUnlocked`、把 `RepoStorage.atomicWrite` 报成三个函数名叠在一起。
     * 顶层类名从路径里剥掉（基线短一截），剥得对不对由 [the scanned file is the repository itself] 钉住。
     */
    private fun writeSites(text: String): List<WriteSite> {
        val calls = callIndices(text, "atomicWriteText").toSet()
        val decls = declRegex.findAll(text).associate { it.range.first to it.groupValues[1] }
        val stack = mutableListOf<Pair<String, Int>>()
        val out = mutableListOf<WriteSite>()
        var depth = 0
        var line = 1
        for (i in text.indices) {
            val c = text[i]
            decls[i]?.let { name ->
                while (stack.isNotEmpty() && stack.last().second >= depth) stack.removeAt(stack.size - 1)
                stack += name to depth
            }
            if (i in calls) {
                out += WriteSite(line, stack.drop(1).joinToString(".") { it.first }.ifEmpty { "<顶层>" })
            }
            when (c) {
                '\n' -> line++
                '{' -> depth++
                '}' -> {
                    depth--
                    while (stack.isNotEmpty() && stack.last().second >= depth) stack.removeAt(stack.size - 1)
                }
            }
        }
        return out.sortedBy { it.line }
    }

    private val sites: List<WriteSite> by lazy { writeSites(blankNonCode(source.readText(Charsets.UTF_8))) }

    private fun List<WriteSite>.inChain() = filter { it.scope.substringAfterLast('.') in WRITE_CHAIN }
    private fun List<WriteSite>.raw() = filterNot { it.scope.substringAfterLast('.') in WRITE_CHAIN }

    private fun List<WriteSite>.describe() = joinToString("; ") { "L${it.line} ${it.scope}" }

    // ═══════════ 断言 ═══════════

    /** 剥作用域前缀这件事要有证人：顶层声明必须就是那个类，否则剥掉的不是外层类名 */
    @Test
    fun `the scanned file is the repository itself`() {
        val text = blankNonCode(source.readText(Charsets.UTF_8))
        val topLevel = declRegex.findAll(text)
            .firstOrNull { it.value.startsWith("class") || it.value.startsWith("object") }
            ?.groupValues?.get(1)
        assertEquals(
            "KnowledgeRepository.kt 的顶层声明变了，作用域路径'剥掉第一段'的口径就不成立",
            "KnowledgeRepository", topLevel
        )
    }

    /** 扫描要有东西可扫：数到 0 处一律先怀疑尺瞎了，而不是庆祝 */
    @Test
    fun `the scanner actually found write sites`() {
        assertTrue(
            "扫到 0 处 atomicWriteText 调用——要么仓库被清空，要么这把尺瞎了",
            sites.isNotEmpty()
        )
    }

    /** 总数证人：加一条、删一条、挪一条都会撞到这里 */
    @Test
    fun `total atomicWriteText call sites are ratcheted`() {
        assertEquals(
            "KnowledgeRepository.kt 里 atomicWriteText( 调用点总数（实测 ${sites.size}：${sites.describe()}）",
            expectedTotalSites, sites.size
        )
    }

    /**
     * 主证据：唯一写链 4 处 / 裸写 N 处，两个数一起判。
     *
     * 分成两半是因为这两半各自会漂：还掉一处裸写但没接进写核 → 只有裸写变小；
     * 把写核外的调用复制进写核 → 只有链上变大（多落了一次盘）——两种都必须红。
     */
    @Test
    fun `mutations reach the write boundary only through the registered tx cores`() {
        val chain = sites.inChain()
        val raw = sites.raw()
        assertEquals(
            "唯一写链上的 atomicWriteText 调用点数（实测 ${chain.size}：${chain.describe()}）" +
                " —— 登记的写核是 $WRITE_CHAIN",
            WRITE_CHAIN.size, chain.size
        )
        assertEquals(
            "绕过 KnowledgeTx 直接调 atomicWriteText 的裸写处数（实测 ${raw.size}：${raw.describe()}）" +
                " —— 还掉一批回来把 expectedRawBreakdown 改小，只许往下",
            expectedRawBreakdown.values.sum(), raw.size
        )
    }

    /** 明细表：登记"哪些地方在裸写、各几处"；同一个作用域里多一处也拦得住（所有者集合不变但条数变） */
    @Test
    fun `the raw write sites are exactly the registered ones`() {
        val actual = sites.raw().groupingBy { it.scope }.eachCount()
        assertEquals(
            "裸写分布表变了（实测 ${actual.toSortedMap()}）—— 多出的作用域=又开了一处裸写，" +
                "少了=欠账比登记的干净，回来改小",
            expectedRawBreakdown.toSortedMap(), actual.toSortedMap()
        )
    }

    /**
     * 「禁止出现第二条 `atomicWriteText` **公共**链」的字面执行：定义只许一处，且必须一直是 `private`。
     * 这条与 [ReadOnlySchemaWriteGateTest] 的字节层绊线不重复：那格管 `rawAtomicWriteText` 只有一个调用方，
     * 这一格管这道门本身不许被放宽成公共的。
     */
    @Test
    fun `the write boundary stays private to the repository`() {
        val text = blankNonCode(source.readText(Charsets.UTF_8))
        val declarations = Regex("""(?m)^[ \t]*(?:\w+\s+)*fun\s+atomicWriteText\(""")
            .findAll(text).map { it.value.trim() }.toList()
        assertEquals(
            "atomicWriteText 的定义只许一处（多一处就是第二条写链的起点）：$declarations",
            1, declarations.size
        )
        assertTrue(
            "定义必须是 private，否则它就是复核报告说的'公共链'：${declarations.first()}",
            declarations.single().startsWith("private fun atomicWriteText(")
        )
    }

    /** 函数引用是比调用更隐蔽的第二条链：把落盘能力当值传出去，静态调用图上看不到 */
    @Test
    fun `the write boundary is never handed around as a reference`() {
        val refs = Regex("::atomicWriteText").findAll(blankNonCode(source.readText(Charsets.UTF_8))).count()
        assertEquals("不许出现 ::atomicWriteText 函数引用（那等于给唯一写出口再开一个口子）", 0, refs)
    }

    /** 跨文件：data/ 里除仓库自己之外不许有人调用（真调用编译不过，这条拦的是"顺手放宽可见性"） */
    @Test
    fun `no other data source reaches the repository writer`() {
        val offenders = appDataDir.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }
            .orEmpty()
            .filter { it.name != "KnowledgeRepository.kt" }
            .map { it.name to callIndices(blankNonCode(it.readText(Charsets.UTF_8)), "atomicWriteText").size }
            .filter { it.second > 0 }
        assertEquals(
            "atomicWriteText 只在仓库内部用；别家出现调用，先怀疑它的可见性被放宽了",
            emptyList<Pair<String, Int>>(), offenders
        )
    }
}
