package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「所有 mutation 只能从 [KnowledgeRepository.KnowledgeTx] 取得安全路径与原子写能力」这句话的
 * **全口径**尺：[KnowledgeTxMutationEntryTest] 只数 `atomicWriteText(` 一个词，所以它那格
 * 「裸写 0」成立在词面上——`data/` 里任何一处 `.writeText(` / `.appendText(` /
 * `FileOutputStream(` / `RandomAccessFile(` / `.printWriter(` 都绕过写链落盘，却一个都不在它账上。
 * 本文件把这些**落盘原语**一起数，按 (文件, 声明作用域) → 处置 登记死并判等。
 *
 * 口径与邻居逐字一致（同 [KnowledgeTxMutationEntryTest]）：
 * - **先剥注释与字符串字面量再数**（[blankNonCode] 整段复制自那格，见函数上的复制来源注释）。
 *   理由就是复核点名的「注释式修复」：一句 KDoc 就能把一条真裸写糊过去。
 * - 作用域路径**剥掉第一段顶层类名**（那格同款）。
 * - 一切计数判 **==**，而且**键集合本身也判等**：多一处没登记=红、少一处=红、
 *   登记了一个扫不到的键=红（与 `ViewModelStateOwnershipTest` 「账本不许被悄悄清空」同一口径）。
 *
 * ⚠ 本文件**只数不改**：它不动一行生产代码。
 */
class DataWritePrimitiveLedgerTest {

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

    /** 只扫 data/ 顶层的 .kt，不递归到别的包（别的包有别的账） */
    private val sources: List<File>
        get() = appDataDir.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }
            .orEmpty()
            .sortedBy { it.name }
            .also {
                assertTrue(
                    "data/ 顶层一个 .kt 都没扫到（${appDataDir.path}）——尺瞎了",
                    it.isNotEmpty()
                )
            }

    // ═══════════ 这把尺的量程 ═══════════

    /**
     * 落盘原语。带点号的按成员调用字面量数（点号本身就锚住了左边界，
     * `guardedWriteText(` 之类不会混进 `.writeText(`）；不带点号的构造名沿用邻居那两道排除
     * （前一个字符不是标识符字符、且不是本文件里的 `fun` 定义），见 [hitIndices]。
     */
    private val primitives = listOf(
        ".writeText(",
        ".appendText(",
        "FileOutputStream(",
        "RandomAccessFile(",
        ".printWriter(",
        "atomicWriteText("
    )

    /** 一处命中：哪个文件、第几行、哪个原语、落在哪个声明作用域里（顶层类名已剥掉） */
    private data class Site(val file: String, val line: Int, val primitive: String, val scope: String) {
        val key: String get() = "$file::$scope"
    }

    /** 三个处置：写核自己 / 唯一写链上登记的写核 / 其余每一处都要写一句为什么 */
    private enum class Disposition { WRITE_CORE, ON_CHAIN, REGISTERED_EXCEPTION }

    /** 账上一行：(文件, 作用域) → 该键里那个原语、处数、处置、以及（若为例外）一句为什么 */
    private data class LedgerRow(
        val file: String,
        val scope: String,
        val primitive: String,
        val count: Int,
        val disposition: Disposition,
        val reason: String = ""
    ) {
        val key: String get() = "$file::$scope"
    }

    // ═══════════ 账（首跑实测之后如实登记；只许往下）═══════════

    /**
     * 唯一写链上那四个锁内写核：与 [KnowledgeTxMutationEntryTest] 的 `WRITE_CHAIN` **逐字相同**，
     * 不是第二个名字——那格数的是「谁调 `atomicWriteText(`」，本格数的是「谁碰过落盘原语」，
     * 两边的作用域名必须能一对一对上，否则两本账会在中间裂开。
     */
    private val WRITE_CHAIN = setOf(
        "writeFileUnlocked",
        "appendFileUnlocked",
        "writeFileCheckedUnlocked",
        "appendFileCheckedUnlocked"
    )

    /**
     * 账（2026-09-30 首跑实测照抄：`the ledger keys are exactly the measured ones` 打出来的那八个键，
     * 处数与逐族合计来自 `each primitive family is tallied exactly`）。
     *
     * 处置的口径：
     * - WRITE_CORE：字节真正落下去那一处，它**就是**写核，谈不上"绕过"。
     * - ON_CHAIN：`atomicWriteText(` 那四个锁内写核里的调用，作用域名逐字取自
     *   [KnowledgeTxMutationEntryTest.WRITE_CHAIN]（链上的数还是 4，没扩）。
     * - REGISTERED_EXCEPTION：其余每一处，各带一句为什么。
     *
     * 2026-09-30 划掉了 `KnowledgeRepository.kt :: RepoStorage.markInitialized` 那一行：那颗根级
     * 标记本来就有一个已经在跑的门可走（`writeRootFileGuarded`，批次五为 `.last_backup` 造的），
     * 挂着裸 `File.writeText` 只是没去还。现在它过守门，拒收那一支抛 `IllegalStateException`
     * 而不是不出声——形态由 [the init marker write takes the root guard and does not swallow a refusal]
     * 钉住（false 那一支在产品码里触发不到，只能钉形态，理由写在那格的注释里）。
     * 端口签名一个字没动，`WRITE_CHAIN` 也没扩：链上的数还是 4。
     * 账面：`.writeText(` 3 → 2、REGISTERED_EXCEPTION 4 → 3、键 8 → 7、总处数 9 → 8。
     */
    private val ledger: List<LedgerRow> = listOf(
        LedgerRow(
            file = "KnowledgeRepository.kt",
            scope = "rawAtomicWriteText",
            primitive = "FileOutputStream(",
            count = 1,
            disposition = Disposition.WRITE_CORE
        ),
        LedgerRow(
            file = "KnowledgeRepository.kt",
            scope = "writeFileUnlocked",
            primitive = "atomicWriteText(",
            count = 1,
            disposition = Disposition.ON_CHAIN
        ),
        LedgerRow(
            file = "KnowledgeRepository.kt",
            scope = "appendFileUnlocked",
            primitive = "atomicWriteText(",
            count = 1,
            disposition = Disposition.ON_CHAIN
        ),
        LedgerRow(
            file = "KnowledgeRepository.kt",
            scope = "writeFileCheckedUnlocked",
            primitive = "atomicWriteText(",
            count = 1,
            disposition = Disposition.ON_CHAIN
        ),
        LedgerRow(
            file = "KnowledgeRepository.kt",
            scope = "appendFileCheckedUnlocked",
            primitive = "atomicWriteText(",
            count = 1,
            disposition = Disposition.ON_CHAIN
        ),
        LedgerRow(
            file = "FeedbackCaseRepository.kt",
            scope = "saveCasesSync",
            primitive = ".writeText(",
            count = 2,
            disposition = Disposition.REGISTERED_EXCEPTION,
            reason = "反馈案例的本地落盘，不在 knowledge/ 树里：目标是 context.filesDir/feedback/cases.json，" +
                "而 KnowledgeTx 只会表达「某本库里的某个相对路径」，表达不了这个落点。" +
                "与 markInitialized 那处的区别：根级门已经为 knowledge/ 造好了，这一族还没有对应的门"
        ),
        LedgerRow(
            file = "KbArchiveTransfer.kt",
            scope = "extractToStaging",
            primitive = "FileOutputStream(",
            count = 1,
            disposition = Disposition.REGISTERED_EXCEPTION,
            reason = "导入走「暂存区 → 校验 → 原子搬入」三段式，这一处写的是暂存壳（zip 逐条目解压），" +
                "按设计就不许写进 knowledge/ 树——中途磁盘满或被杀不会留下半截坏库；" +
                "暂存区在系统临时目录，没有一本库能承载它"
        )
    )

    /** 同一跑的逐族合计：今天真的一条都没有的那三族也照样判 == 0，量程不许有名无实 */
    private val expectedFamilyTally: Map<String, Int> = mapOf(
        ".writeText(" to 2,
        ".appendText(" to 0,
        "FileOutputStream(" to 2,
        "RandomAccessFile(" to 0,
        ".printWriter(" to 0,
        "atomicWriteText(" to 4
    )

    // ═══════════ 扫描工具 ═══════════

    /**
     * 复制来源：`KnowledgeTxMutationEntryTest.blankNonCode`（逐字符照抄，同一把尺的口径要一致）。
     * 把注释与字符串字面量整段抹成空格，**行位置逐字符不变**（长度会因引号成对抹掉而变，
     * 但换行符一个不多一个不少，所以报出来的行号能直接对着源文件看）。
     *
     * Kotlin 的块注释可嵌套，所以要记深度；`//` 到行尾；`"…"` / `'…'` 里带转义；`"""…"""` 整段算字面量。
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
     * 一段代码里某个原语全部命中的下标。
     *
     * ①带点号的（`.writeText(`）点号已经把左边界锚死，字面量数就是准的；
     * ②不带点号的构造名沿用邻居那两道排除：`rawAtomicWriteText(` 把 `atomicWriteText(` 整个包住
     * → 靠「前一个字符不是标识符字符」剥掉；定义行 `private fun atomicWriteText(` 自己是声明不是调用
     * → 靠 `fun <名字` 捕获组的下标剥掉。
     */
    private fun hitIndices(text: String, needle: String): List<Int> {
        val hits = mutableListOf<Int>()
        var from = 0
        if (needle.startsWith(".")) {
            while (true) {
                val at = text.indexOf(needle, from)
                if (at < 0) break
                hits += at
                from = at + 1
            }
            return hits
        }
        val name = needle.removeSuffix("(")
        val definitions = Regex("""\bfun\s+(?:<[^>]*>\s*)?([A-Za-z_]\w*)\s*\(""")
            .findAll(text)
            .filter { it.groupValues[1] == name }
            .map { it.groups[1]!!.range.first }
            .toSet()
        while (true) {
            val at = text.indexOf(needle, from)
            if (at < 0) break
            if (!identifierBefore(text, at) && at !in definitions) hits += at
            from = at + 1
        }
        return hits
    }

    /** 邻居同款：沿字符走一遍，记花括号深度与一张作用域栈，给每个命中配上「它落在哪个声明里」 */
    private fun scan(fileName: String, text: String): List<Site> {
        val hits = mutableMapOf<Int, MutableList<String>>()
        for (p in primitives) for (at in hitIndices(text, p)) hits.getOrPut(at) { mutableListOf() } += p
        val decls = declRegex.findAll(text).associate { it.range.first to it.groupValues[1] }
        val stack = mutableListOf<Pair<String, Int>>()
        val out = mutableListOf<Site>()
        var depth = 0
        var line = 1
        for (i in text.indices) {
            val c = text[i]
            decls[i]?.let { name ->
                while (stack.isNotEmpty() && stack.last().second >= depth) stack.removeAt(stack.size - 1)
                stack += name to depth
            }
            hits[i]?.forEach { p ->
                out += Site(fileName, line, p, stack.drop(1).joinToString(".") { it.first }.ifEmpty { "<顶层>" })
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

    private val sites: List<Site> by lazy {
        sources.flatMap { scan(it.name, blankNonCode(it.readText(Charsets.UTF_8))) }
    }

    private fun List<Site>.describe() = joinToString("; ") { "${it.file}:L${it.line} ${it.primitive} @${it.scope}" }

    // ═══════════ 断言 ═══════════

    /** 扫描要有东西可扫：数到 0 处一律先怀疑尺瞎了，而不是庆祝 */
    @Test
    fun `the scanner actually found flush primitives`() {
        assertTrue(
            "data/ 里扫到 0 处落盘原语——要么整包被清空，要么这把尺瞎了：" +
                "扫了 ${sources.size} 个 .kt，目录 ${appDataDir.path}",
            sites.isNotEmpty()
        )
    }

    /**
     * 量程不许有名无实：六族里今天真的一条都没有的那几族，**判 == 0** 而不是不提。
     * 这样 `RandomAccessFile(` 一旦被人拿来落盘，撞的是这一格，不是「什么都没说」。
     */
    @Test
    fun `each primitive family is tallied exactly`() {
        val measured = primitives.associateWith { p -> sites.count { it.primitive == p } }
        assertEquals(
            "逐族实测处数（${measured.entries.joinToString { "${it.key}=${it.value}" }}）" +
                " —— 账上哪一族动了都要回这里改，不许只改总数",
            expectedFamilyTally.toSortedMap(), measured.toSortedMap()
        )
    }

    /** 主证据 A：键集合判等——多一处没登记=红、少一处=红、登记了扫不到的键=红 */
    @Test
    fun `the ledger keys are exactly the measured ones`() {
        val measured = sites.map { it.key }.toSet()
        val registered = ledger.map { it.key }.toSet()
        assertEquals(
            "(文件, 作用域) 键集合变了（实测 ${measured.toSortedSet()}）—— 多出未登记的键=又开了一处落盘，" +
                "少一个=那处已经还掉，回来把账划掉；账上不存在的键=登记了一条扫不到的假账",
            registered.toSortedSet(),
            measured.toSortedSet()
        )
        assertEquals(
            "同一个键上不许登记两行（要嘛合成一行、要嘛键写错了）：" +
                ledger.groupBy { it.key }.filterValues { it.size > 1 }.keys,
            emptySet<String>(),
            ledger.groupBy { it.key }.filterValues { it.size > 1 }.keys
        )
    }

    /** 主证据 B：每个键上的原语与处数各自判等（键集合不变但同一作用域里多写一次也拦得住） */
    @Test
    fun `every registered key holds the registered primitive and count`() {
        val registeredByKey = ledger.associateBy { it.key }
        val actual = sites.groupBy { it.key }.mapValues { e -> e.value.sortedBy { s -> s.line } }
        for ((key, v) in actual.toSortedMap()) {
            val row = registeredByKey[key] ?: error("键 $key 没登记就被主证据 A 拦下了")
            assertEquals(
                "$key 上实测扫到 ${v.size} 处，登记 ${row.count} 处：${v.describe()}",
                row.count, v.size
            )
            assertEquals(
                "$key 上落盘的是 ${v.map { it.primitive }.distinct()}，账上写的却是 ${row.primitive}：${v.describe()}",
                setOf(row.primitive), v.map { it.primitive }.toSet()
            )
        }
    }

    /** 主证据 C：三条处置的计数一律判 ==（未登记的键上的命中哪一族都不进，所以一定把总数撞歪） */
    @Test
    fun `the three dispositions add up`() {
        val rowByKey = ledger.associateBy { it.key }
        for (d in Disposition.values()) {
            val measured = sites.filter { rowByKey[it.key]?.disposition == d }
            assertEquals(
                "$d 处数（实测 ${measured.size}：${measured.describe()}）—— 每一族各自判等，" +
                    "把一处从例外挪进链上、或反过来，两个数会一起歪",
                ledger.filter { it.disposition == d }.sumOf { it.count }, measured.size
            )
        }
    }

    /** 链上那一族必须逐字对上邻居 WRITE_CHAIN 的四个名字，且一个不多一个不少 */
    @Test
    fun `the on chain scopes are the same four cores the tx ledger registers`() {
        val onChainKeys = ledger.filter { it.disposition == Disposition.ON_CHAIN }.map { it.scope }
        assertEquals(
            "ON_CHAIN 登记的作用域名必须与 KnowledgeTxMutationEntryTest.WRITE_CHAIN 逐字相同" +
                "（不许起第二个名字，也不许扩链）",
            WRITE_CHAIN.toSortedSet(), onChainKeys.toSortedSet()
        )
        val measuredOnChain = sites.filter { it.scope.substringAfterLast('.') in WRITE_CHAIN }
        assertEquals(
            "落在登记写核里的落盘原语处数（实测 ${measuredOnChain.size}：${measuredOnChain.describe()}）" +
                " —— 链上的数必须还是 4，扩一条链就等于把口径改软",
            WRITE_CHAIN.size, measuredOnChain.size
        )
    }

    /** 例外不是「登记了事」：每一处都得带一句为什么，且处置不许写错 */
    @Test
    fun `every registered exception carries a reason`() {
        val voiceless = ledger
            .filter { it.disposition == Disposition.REGISTERED_EXCEPTION }
            .filter { it.reason.isBlank() }
            .map { it.key }
        assertEquals("REGISTERED_EXCEPTION 不许有空口供（每一处都要写一句为什么）", emptyList<String>(), voiceless)
        val chatty = ledger
            .filter { it.disposition != Disposition.REGISTERED_EXCEPTION }
            .filter { it.reason.isNotBlank() }
            .map { it.key }
        assertEquals(
            "写核与链上那两族的原因住在 [KnowledgeTxMutationEntryTest] 与仓库的 KDoc 里，" +
                "账上不重复记一份，免得两句话说同一件事还能各自漂",
            emptyList<String>(), chatty
        )
    }

    /**
     * 这把尺与只数一个词的那把的**差别**就在这格：注释与字符串里的命中一律不算。
     * 判据用当场造的样例，不靠源文件的运气——把 [blankNonCode] 换成直通，这格立刻红。
     */
    @Test
    fun `commented and quoted flushes are not counted`() {
        val snippet = """
            class Sample {
                fun real() { File("x").writeText("a") }
                /** KDoc 里写着 File("y").writeText("b") 也算数就是注释式修复 */
                fun alsoReal() {
                    val note = "字符串里的 z.writeText(c) 不算"
                    File("w").appendText("d")
                }
                // 行尾注释里的 f.printWriter(g) 也不算
            }
        """.trimIndent()
        assertEquals(
            "剥完之后只剩两处真落盘（.writeText( 与 .appendText(），实测：" +
                scan("Sample.kt", blankNonCode(snippet)).describe(),
            listOf(".writeText(", ".appendText("),
            scan("Sample.kt", blankNonCode(snippet)).map { it.primitive }
        )
    }

    // ═══════════ 还掉 markInitialized 那一处之后要钉住的形态 ═══════════

    /**
     * 「写完不成不许不出声」这一条只能钉形态，钉不了行为：根级守门拒收的是**畸形文件名**
     * （分隔符 / `..` / 盘符 / 空名），而 `INIT_MARKER_FILE` 是个合法裸名，产品码里那一支 false
     * 触发不到；物理写失败则由 `rawAtomicWriteText` 直接抛，走的是异常而不是 false。
     * 所以这里判的是「`writeRootFileGuarded` 的返回值有没有被真的判掉」——把生产码换成丢弃返回值，
     * 这一格立刻红。
     */
    @Test
    fun `the init marker write takes the root guard and does not swallow a refusal`() {
        val body = declarationBody("KnowledgeRepository.kt", "override fun markInitialized()")
        assertEquals(
            "markInitialized 只许从根级守门取路径落盘（一处），实测体：$body",
            1, body.split("writeRootFileGuarded(").size - 1
        )
        assertEquals(
            "返回值必须被 if 判掉再处理；`writeRootFileGuarded(...)` 光当一句执行就是丢弃 false：" + body,
            1, Regex("""if\s*\(\s*!\s*writeRootFileGuarded\s*\(""").findAll(body).count()
        )
        assertTrue(
            "拒收要留痕并且让调用方失败——体里没有 throw 就是一次不留痕的跳过：" + body,
            Regex("""\bthrow\s""").containsMatchIn(body)
        )
        val bare = primitives.filter { body.contains(it) }
        assertEquals(
            "这颗标记自己不许再碰任何落盘原语（它只许把名字交给守门）：$body",
            emptyList<String>(), bare
        )
    }

    /** 端口签名不许被这一处改动捎带上：markInitialized 还是那个无参、非 suspend 的一处定义 */
    @Test
    fun `the init marker port keeps its shape`() {
        val text = blankNonCode(File(appDataDir, "KnowledgeRepository.kt").readText(Charsets.UTF_8))
        assertEquals(
            "markInitialized 的定义在仓库里只许一处（多一处就是给第二个落盘面开转手）",
            1, Regex("""override fun markInitialized\(\)""").findAll(text).count()
        )
        assertEquals(
            "不许带参数：端口签名一动，KnowledgeCatalogWriteStore 那两处调用方就全得跟着改",
            0, Regex("""fun markInitialized\([^)]""").findAll(text).count()
        )
        assertEquals(
            "不许被改成 suspend：调用方已经在仓库那把锁里，套一层挂起就是给重入开新路径",
            0, Regex("""suspend\s+fun\s+markInitialized""").findAll(text).count()
        )
    }

    /** 剥过注释的源码里取某个声明的函数体（花括号配平；取不到就红，不留假绿） */
    private fun declarationBody(fileName: String, decl: String): String {
        val text = blankNonCode(File(appDataDir, fileName).readText(Charsets.UTF_8))
        val at = text.indexOf(decl)
        assertTrue("$fileName 里扫不到 `$decl`——名字被改了，钉它的格子要跟着改", at >= 0)
        val open = text.indexOf('{', at)
        assertTrue("$decl 得有函数体", open >= 0)
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(open, i + 1)
                }
            }
            i++
        }
        return error("$decl 的函数体花括号不闭合")
    }
}
