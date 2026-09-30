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

    /**
     * 仓库本体 = `KnowledgeRepository.kt` + 拆出的 `KnowledgeRepoIO.kt`（扩展函数）。
     * atomicWriteText 及四个锁内写核已搬进 KnowledgeRepoIO.kt，但它仍是仓库的一部分
     * （扩展函数挂在 KnowledgeRepository 上，落盘只有这一处的不变量没变）。
     */
    private val repositoryFiles = listOf("KnowledgeRepository.kt", "KnowledgeRepoIO.kt")

    private val sources: List<File> by lazy {
        repositoryFiles.map { name ->
            File(appDataDir, name).also {
                if (!it.isFile) error("扫不到 $it，下面那些计数全是假的")
            }
        }
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
     * 2026-09-26 首次实测：`ensureInitialKnowledgeBase` 13 处、`create` 13 处、
     * `ensureKbFilesCompleteUnlocked` 2 处、`RepoStorage` 的两个委托各 1 处，共 30。
     *
     * 2026-09-27 批次二还掉 `create` 那 13 处：它以前搬不动**不是没做**，是真的不等价——
     * `create` 的 sanitizer 不限长度而 `KbName` 卡 100，上链会让 101+ 字符名从"13 格照常落盘"
     * 变成"守门拒掉、一个字节不写"。用户拍板走"改行为"那条：两处改读同一个常数
     * `KB_NAME_MAX_LENGTH`，`create` 入口先拒过长名，13 处随之上链
     * （证据与新旧行为各自钉在 `KnowledgeSeedWriteBytesBaselineTest`）。
     *
     * 2026-09-27 批次三还掉 `ensureKbFilesCompleteUnlocked` 那 2 处：它以前给"已存在的库"补文件时
     * 自己拼 `File(dir, path)` 再裸写。现在路径仍由 `KnowledgeTx.pathOf` 给（同一个 `safeKbFile` 判定），
     * 写改走 `write`。**判"存在"刻意保留 `exists()`**：换成 `readTextAt().isEmpty()` 会把
     * "文件在、内容为空"误判成缺失，给一个用户故意留空的 `moment/plan.md` 盖回 schema 模板（那是改行为）。
     *
     * 一条要说清的语义变化：库名非法（>100 字符等）的**遗留目录**从今天起不再被补齐——
     * 那种库本来就每一读都被 `safeKbFile` 判非法、拿回空串，补了也没人读得到。
     * 新的非法名现在有三条入口都被堵：`create` 的 require、导入的 `KbName` 守门、以及路径守门本身。
     *
     * 2026-09-27 批次四还掉 `RepoStorage.atomicWrite` 那 1 处：它不是仓库自己的写路径，而是**第二个路径
     * 所有者**的落盘口——`KnowledgeMigrator` 自己拼 `File(root, kbName)` 再拼相对路径，然后把一个
     * `File` 交给端口写进库里（14 处）。仓库这一侧新加的不是第三条写链，而是把端口那道
     * 「交出一个 File 就想写哪儿写哪儿」的门换成 `atomicWriteAt(kbName, relativePath, content)`，
     * 实现体直接复用已有的 `safeKbFile` + `writeFileCheckedUnlocked`（所以链上的数一点没长）。
     * 端口上再没有不收路径的写口，那 14 处的字节由 `KnowledgeMigratorBytesBaselineTest` 逐格钉住。
     *
     * 2026-09-29 批次五还掉最后一笔 `RepoStorage.guardedWrite` 那 1 处。**为什么它此前还不动**：
     * 它写的是根级 marker `.last_backup`，那个文件按设计不属于任何库（`kbOwning` 对点开头条目返回 null），
     * 而 `safeKbFile(kbName, relativePath)` 只会表达「某本库里的某个路径」——所以它挂在这本账上
     * 挂的是**结构性**的缺，不是"忘了走事务"。欠账的形状其实和批次四一模一样：
     * 端口那道 `guardedWrite(target: File, content)` 收的是一个外部拼好的 `File`，
     * 备份格想写哪儿就写哪儿（`KnowledgeBackupService` 里那句 `File(root, MARKER_FILE)` 就是路径来源）。
     *
     * 这一轮还的是那半句"公共链"：端口换成 `writeRootMarker(fileName: String, content: String)`，
     * 名字先过一道**根级守门**（`KnowledgeDocumentStore.resolveRoot`：只收裸文件名，
     * 分隔符 / `..` / 绝对路径 / 盘符 / `.` / 空名一律拒，收下之后再核一次 canonical 仍在 knowledge/ 根下），
     * 落盘进登记在册的那个写核（`writeFileCheckedUnlocked` 的「路径已解析」那一支，
     * 与库内那一支同一个 `atomicWriteText`）。事务那侧同时长出 `KnowledgeTx.rootFile` 与
     * `KnowledgeTx.writeRootMarker`，根级 marker 的写由此只能从事务对象取。
     * 两条捷径都没走：`WRITE_CHAIN` 一个字没动（链上的数还是 4，不是 5），也没为根级 marker 编一个假库名。
     *
     * 账面：**总点数 5 → 4、唯一写链 4 → 4（没长）、裸写 1 → 0**。
     * 牙由 `KnowledgeRootWriteGuardTest` 那一族行为格 + 一格形状钉住（含"合法名仍然写得动"那一格，
     * 防的是把门全关死这种假安全），只读判定在分两支之后仍在原处。
     */
    private val expectedRawBreakdown = emptyMap<String, Int>()

    /** 调用点总数（写链 4 + 裸写 0——裸写这一族从今天起是空集）：动一条也要撞到这里 */
    private val expectedTotalSites = 4

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

    /** 声明（class / object / interface / fun，含 `fun <T> name(` 与扩展函数 `fun Receiver.name(`）→ 关键字下标 → (名字, 是否类型声明) */
    private val declRegex = Regex("""\b(fun|class|object|interface)\s+(?:<[^>]*>\s*)?(?:[A-Za-z_]\w*\s*\.\s*)?([A-Za-z_]\w*)""")

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
        val definitions = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_]\w*\s*\.\s*)?([A-Za-z_]\w*)\s*\(""")
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
        val decls = declRegex.findAll(text).associate {
            it.range.first to (it.groupValues[2] to (it.groupValues[1] != "fun"))
        }
        val stack = mutableListOf<Triple<String, Int, Boolean>>()
        val out = mutableListOf<WriteSite>()
        var depth = 0
        var line = 1
        for (i in text.indices) {
            val c = text[i]
            decls[i]?.let { (name, isType) ->
                while (stack.isNotEmpty() && stack.last().second >= depth) stack.removeAt(stack.size - 1)
                stack += Triple(name, depth, isType)
            }
            if (i in calls) {
                out += WriteSite(line, stack.dropWhile { it.third }.joinToString(".") { it.first }.ifEmpty { "<顶层>" })
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

    private val sites: List<WriteSite> by lazy {
        sources.flatMap { f -> writeSites(blankNonCode(f.readText(Charsets.UTF_8))) }
    }

    private fun List<WriteSite>.inChain() = filter { it.scope.substringAfterLast('.') in WRITE_CHAIN }
    private fun List<WriteSite>.raw() = filterNot { it.scope.substringAfterLast('.') in WRITE_CHAIN }

    private fun List<WriteSite>.describe() = joinToString("; ") { "L${it.line} ${it.scope}" }

    // ═══════════ 断言 ═══════════

    /** 剥作用域前缀这件事要有证人：顶层声明必须就是那个类，否则剥掉的不是外层类名 */
    @Test
    fun `the scanned file is the repository itself`() {
        val text = blankNonCode(sources[0].readText(Charsets.UTF_8))
        val topLevel = declRegex.findAll(text)
            .firstOrNull { it.value.startsWith("class") || it.value.startsWith("object") }
            ?.groupValues?.get(2)
        assertEquals(
            "KnowledgeRepository.kt 的顶层声明变了，作用域路径'剥掉第一段'的口径就不成立",
            "KnowledgeRepository", topLevel
        )
        assertTrue("KnowledgeRepoIO.kt 必须存在——atomicWriteText 已搬至此", sources[1].isFile)
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
            "仓库里 atomicWriteText( 调用点总数（实测 ${sites.size}：${sites.describe()}）",
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
        val declarations = sources.flatMap { f ->
            val text = blankNonCode(f.readText(Charsets.UTF_8))
            Regex("""(?m)^[ \t]*(?:\w+\s+)*fun\s+(?:[A-Za-z_]\w*\.)?atomicWriteText\(""")
                .findAll(text).map { it.value.trim() }.toList()
        }
        assertEquals(
            "atomicWriteText 的定义只许一处（多一处就是第二条写链的起点）：$declarations",
            1, declarations.size
        )
        assertTrue(
            "定义必须是 internal 或 private（不许放宽成公共的）：${declarations.first()}",
            declarations.single().let { decl ->
                decl.startsWith("private fun") || decl.startsWith("internal fun")
            }
        )
    }

    /** 函数引用是比调用更隐蔽的第二条链：把落盘能力当值传出去，静态调用图上看不到 */
    @Test
    fun `the write boundary is never handed around as a reference`() {
        val refs = sources.sumOf { f ->
            Regex("::atomicWriteText").findAll(blankNonCode(f.readText(Charsets.UTF_8))).count()
        }
        assertEquals("不许出现 ::atomicWriteText 函数引用（那等于给唯一写出口再开一个口子）", 0, refs)
    }

    /** 跨文件：data/ 里除仓库自己之外不许有人调用（真调用编译不过，这条拦的是"顺手放宽可见性"） */
    @Test
    fun `no other data source reaches the repository writer`() {
        val offenders = appDataDir.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }
            .orEmpty()
            .filter { it.name !in repositoryFiles }
            .map { it.name to callIndices(blankNonCode(it.readText(Charsets.UTF_8)), "atomicWriteText").size }
            .filter { it.second > 0 }
        assertEquals(
            "atomicWriteText 只在仓库内部用；别家出现调用，先怀疑它的可见性被放宽了",
            emptyList<Pair<String, Int>>(), offenders
        )
    }
}
