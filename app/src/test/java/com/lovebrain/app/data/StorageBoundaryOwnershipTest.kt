package com.lovebrain.app.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 存储边界的**所有权**棘轮（§5.3"所有实现共用一个事务入口，不许各自 new Mutex"
 * 与 P0-03"Repository 内禁止出现第二条 `atomicWriteText` 公共链"的执行面）。
 *
 * 为什么要有这把尺：拆类拆得对不对，光看行为测试看不出来——每个新类都可以自带一份
 * "我也检查一下 canonical 吧"，各自都过自己的测试，而全仓的边界从此宽严不一。
 * 本轮拆 `KnowledgeDocumentStore` 时撞上的正是这个形状的历史欠账：无锁快速读
 * 自己拼 `File(File(root, kbName), relativePath)` 不过守门，公开读却过。
 * 而 `ReadOnlySchemaWriteGateTest` 那条源码绊线只扫 `KnowledgeRepository.kt` 一个文件，
 * 新类带第二份落盘实现时没有任何测试会拦。这里补的就是那半截。
 *
 * 三条规则各自登记"当前允许出现在谁家里"，断言**相等**而不是"不许超"：
 *  ① 多一个文件 → 红（新增所有者要显式登记，并说清为什么这件事需要两处）；
 *  ② 少一个文件 → 也红（欠账还得比登记的干净时必须回来改小，否则基线慢慢虚高）。
 * `= Mutex(` 那条由 `KnowledgeMigratorLegacyTest` 管着，这里不重复登记。
 *
 * ⚠ 判据一律**逐行字面判断**：早先一版这里用带 lookbehind 的正则，同一行代码报 0 命中，
 * 换成字面量就能查到——一把自己都会读错的尺不配当门禁，而且它报的是"没人碰了"，
 * 会诱导人把登记删小，那是**反向的假绿**。
 */
class StorageBoundaryOwnershipTest {

    private val dataDir: File
        get() {
            // 单测工作目录是模块目录；两个候选分别对应"从 app/ 跑"与"从仓库根跑"
            val candidates = listOf(
                File("src/main/java/com/lovebrain/app/data"),
                File("app/src/main/java/com/lovebrain/app/data")
            )
            return candidates.firstOrNull { it.isDirectory }
                ?: error(
                    "找不到 data/ 目录（试过 ${candidates.joinToString { c -> c.path }}）——" +
                        "这条棘轮扫空目录比不扫更糟：三条规则都会'恰好零命中'而全绿"
                )
        }

    /**
     * 一条"能力"规则：叫什么、怎么认出碰过它的代码行、当前登记在谁名下。
     *
     * `expectedTotal` 非空时还额外盯**条数**：用于"这一族写法本来就是欠账，
     * 只是还得比登记慢"的场景。不盯条数的文件级相等挡不住同一个文件里的第 N+1 处——
     * 所有者没变，命中数却悄悄长了。
     */
    private data class Rule(
        val what: String,
        val lineMatches: (String) -> Boolean,
        val owners: Set<String>,
        val expectedTotal: Int? = null
    )

    /** 剥掉块注释与行注释：注释里写 `FileOutputStream(` 不算一处实现 */
    private fun codeLines(text: String): List<String> = text
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
        .split("\n")
        .map { it.substringBefore("//") }

    private fun Rule.hitsIn(text: String): Int = codeLines(text).count { lineMatches(it) }

    /** 真字节写入：自己开流，或直接操作随机访问文件 */
    private fun touchesRawStream(line: String): Boolean =
        line.contains("FileOutputStream(") || line.contains("RandomAccessFile(")

    /**
     * 把调用方给的相对路径接成 File。
     *
     * `safeKbFile(kbName, relativePath)` / `resolve(...)` 是**委托**，正是要鼓励的写法，
     * 不能算第二个所有者；剩下的形态就是 `File(dir, relativePath)` 这一种。
     */
    private fun joinsCallerPath(line: String): Boolean {
        val t = line.trim()
        if (t.contains("safeKbFile(") || t.contains("resolve(")) return false
        return listOf("relativePath", "path.value", "oldPath").any { arg ->
            Regex("File\\(\\s*\\w+\\s*,\\s*" + Regex.escape(arg) + "\\s*\\)").containsMatchIn(line)
        }
    }

    private val rules = listOf(
        // 全 data/ 只许文档格一处：两条读入口共用同一个守门，宽严不可能再分叉
        Rule("拼接调用方给的相对路径", ::joinsCallerPath, setOf("KnowledgeDocumentStore.kt")),
        // 仓库那一处是唯一的原子写；KbArchiveTransfer 那一处写的是**导入暂存区**
        // （解包出来的文件），不是知识库正文——登记在此，而不是假装没看见
        Rule(
            "直接开流落盘",
            ::touchesRawStream,
            setOf("KnowledgeRepository.kt", "KbArchiveTransfer.kt")
        ),
        // canonical 越界判断：文档路径一处（文档格）、库目录与暂存区各一处。
        // 三处守的是不同的根，先登记，别让它悄悄变四处
        Rule(
            "自己判 canonical 越界",
            { line -> line.contains(".canonicalPath") },
            setOf("KnowledgeDocumentStore.kt", "KnowledgeRepository.kt", "KbArchiveTransfer.kt")
        ),
        // 归档那一处（archive_op 状态）已并入守门读；剩下的四条全在
        // applyProfileUpdateAtomically 的备份快照里，那一格单独处理，
        // 还掉一条就回来把这个数改小——它只许降不许升，升了必须是一次显式判断。
        Rule(
            "仓库里自己把库名与相对路径拼成文件（不过 canonical 守门）",
            { line -> line.contains("File(File(knowledgeRoot,") },
            setOf("KnowledgeRepository.kt"),
            expectedTotal = 4
        )
    )

    private fun sources(): Map<String, String> =
        dataDir.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }
            ?.associate { it.name to it.readText(Charsets.UTF_8) }
            ?: emptyMap()

    @Test
    fun dataLayerSourcesWereActuallyFound() {
        val found = sources()
        // 防"扫了个空目录于是全绿"：路径写错时下面每条都会假过
        assertTrue(
            "只扫到 ${found.size} 个 data/ 源文件，少于预期——多半是路径没找对，这条棘轮就是瞎的",
            found.size >= 10
        )
        listOf(
            "KnowledgeRepository.kt", "KnowledgeDocumentStore.kt", "KnowledgeMigrator.kt",
            "KnowledgeBackupService.kt", "KnowledgeCatalogStore.kt", "KbArchiveTransfer.kt"
        ).forEach { assertTrue("扫不到的文件说明尺指错了地方：$it", it in found) }
    }

    /**
     * 每条规则都必须恰好命中它登记的那些文件：多了是"第二个所有者"，
     * 少了（包括变成 0）要么是"欠账已还"要么是"尺没量到东西"——两种都不许静默。
     * 带 `expectedTotal` 的规则还要盯条数：同一个文件里多一处，所有者集合是不变的。
     */
    @Test
    fun eachPowerAppearsOnlyInItsRegisteredOwners() {
        val found = sources()
        val violations = mutableListOf<String>()
        for (rule in rules) {
            val perFile = found.mapValues { (_, text) -> rule.hitsIn(text) }.filterValues { it > 0 }
            val hits = perFile.keys
            if (hits != rule.owners) {
                val extra = hits - rule.owners
                val gone = rule.owners - hits
                val parts = mutableListOf<String>()
                if (extra.isNotEmpty()) parts += "新出现的所有者 $extra"
                if (gone.isNotEmpty()) {
                    parts += "$gone 命中数为 0：要么欠账还掉了（把登记改小），要么这把尺根本没量到东西（更糟）"
                }
                violations += "· ${rule.what}：登记的是 ${rule.owners}，实际命中 $perFile —— " +
                    parts.joinToString("；")
            }
            rule.expectedTotal?.let { expected ->
                val actual = perFile.values.sum()
                if (actual != expected) {
                    violations += "· ${rule.what}：登记的是 $expected 处，实际命中 $actual 处" +
                        if (actual > expected) " —— 又长了，这一族的写法只许减" else
                            " —— 欠账比登记的还少，回来把 expectedTotal 改小"
                }
            }
        }
        assertTrue(
            "存储边界的所有者或条数变了，这需要一次显式的判断：\n" + violations.joinToString("\n") +
                "\n  加所有者=又有一份独立实现，行为会像 P0-03 报的那样分叉；" +
                "少所有者=欠账还得比登记的干净，回来把名字删掉。",
            violations.isEmpty()
        )
    }

    /** 文档格自己不许有第二条落盘链：想写必须回到仓库那唯一的写出口 */
    @Test
    fun theDocumentStoreDelegatesEveryWrite() {
        val text = sources()["KnowledgeDocumentStore.kt"]
            ?: error("扫不到 KnowledgeDocumentStore.kt，上面两条也会瞎，先修路径")
        val lines = codeLines(text)
        val streamHits = lines.count { touchesRawStream(it) }
        val writeHits = lines.count { it.contains(".writeText(") || it.contains(".printWriter(") }
        val lockHits = lines.count { it.contains("withLock") }
        assertTrue(
            "文档格写文件必须经 DocumentStorage.writeUnlocked 回到仓库那唯一的写链，" +
                "否则只读 schema 拒绝与备份节流会被绕开。实到：开流 $streamHits 处、直接写文件 $writeHits 处",
            streamHits == 0 && writeHits == 0
        )
        assertTrue(
            "锁只能由仓库在外面套（本类自持锁就等于第二把锁），实到 withLock $lockHits 处",
            lockHits == 0
        )
    }
}
