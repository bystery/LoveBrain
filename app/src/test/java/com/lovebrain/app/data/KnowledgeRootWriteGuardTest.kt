package com.lovebrain.app.data

import android.content.Context
import android.util.Log
import com.lovebrain.app.model.KbName
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 根级 marker 的**守门写**——还掉「所有 mutation 只能从 [KnowledgeRepository.KnowledgeTx]
 * 取得安全路径与原子写能力」这笔账上最后的裸写那一处（`RepoStorage.guardedWrite(target: File, …)`）。
 *
 * 这笔欠账一直挂着是因为一个结构性事实：`.last_backup` **不属于任何一本库**
 * （`kbOwning` 对点开头条目返回 null），`safeKbFile(kbName, relativePath)` 表达不了它。
 * 本轮补的是那道**根级守门**——`KnowledgeTx.rootFile` / `KnowledgeTx.writeRootMarker`：
 * 判据与库内那道同宽严（只收裸文件名，收下之后再核一次 canonical 仍在 knowledge/ 根下），
 * 落盘仍走登记在册的那个写核。两条捷径都不许走：不扩 `WRITE_CHAIN`、不编一个假库名。
 *
 * 八格各自盯一件事：
 * - [escaping root names are refused by the guard and nothing lands]：守门本身——十三种逃逸形全拒，
 *   而且拒了之后整棵树逐文件对得上（只看返回值会被"退而求其次当文件名写下去"骗过去）。
 * - [the detached startup backup outlives a cancelled appScope]：**夹具自己的前提**——
 *   那一发启动备份确实活在 scope 之外，所以 [setUp] 必须预置节流标记（少了这一格，上面那格就是一次掷硬币）。
 * - [whatever the second layer hands back is strictly inside the knowledge root]：第二道门
 *   （canonical 归属复核）的独立对照物——十三种形全被第一道挡住，这一格判的是"交出来的必须严格在根内"。
 * - [a refused root name is said out loud twice and names the file]：多出来的这层判断不许做成不出声的
 *   return false。
 * - [a legitimate root marker still lands and touches no library]：防"为了安全把门全关死"的假安全。
 * - [the root write keeps the marker's own meaning]：`.last_backup` 还是那一个文件、还是那一个裸毫秒数、
 *   还是"上一次备份发生在什么时候"的唯一事实源。
 * - [the read only judgement survives the split core]：写核分成两支之后，库内那一支的只读拒绝还在原处；
 *   而根级这一支不替任何库代判，也不许往库里写。
 * - [the port no longer hands out a path and the backup cell still holds no write power]：**形状**——
 *   行为那些格看不见"端口收一个外部拼好的 File"这件事本身，所以另钉一条静态判据。
 *
 * 注：**这棵树必须靠生产自己那道节流来静，不能靠取消 scope**（这条是 2026-09-29 被 CI 逼出来的修正，
 * 原先这里写的是"夹具给的是已经取消的 scope，所以启动备份不会跑"——那句是假的，见
 * [the detached startup backup outlives a cancelled appScope]）。
 * `KnowledgeRepository.init` 里那句 `appScope.launch(Dispatchers.IO + SupervisorJob())` 往上下文里
 * 塞了一枚**全新的 Job**，启动备份因此不是 `appScope` 的孩子，`cancel()` 传不到它。
 * [setUp] 于是先把 `.last_backup` 预置成"刚刚备份过"：`backupIfNeededUnlocked` 在间隔判定处直接返回，
 * 根级写又不排 debounce（见 `writeFileCheckedUnlocked(file, content)` 的 KDoc），这棵树上除了本轮就没别人。
 * 节流与备份策略本身归 [KnowledgeBackupServiceTest]，这里不重复测。
 */
class KnowledgeRootWriteGuardTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kb_root_write_guard").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { it.cancel() }
        // 构造仓库**之前**预置节流标记：init 那一发备份是在 scope 之外跑的（上面那段说明），
        // 而它判"要不要备份"读的就是这个文件。写下"刚刚备份过"这一秒，那一发就在间隔判定处返回，
        // 一个字节都不落。用生产的机制静生产的树——不加测试专用开关、也不把判据改成"容忍多几行"。
        File(root, KnowledgeBackupService.MARKER_FILE)
            .writeText(System.currentTimeMillis().toString(), Charsets.UTF_8)
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private val kbDir get() = File(root, "kb")

    /** 整棵树的相对路径清单——"什么都没落盘"只有对着树判才算真判 */
    private fun tree(base: File = root): List<String> =
        base.walkTopDown().filter { it.isFile && it != base }
            .map { it.relativeTo(base).path.replace(File.separatorChar, '/') }
            .toList().sorted()

    /** 建一本真库：根级能力挂在事务上，得有库当门票才开得起这一次事务 */
    private fun seedKb(name: String = "kb") {
        val dir = File(root, name).apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = name, displayName = name)
            ),
            Charsets.UTF_8
        )
    }

    /**
     * 在**真事务**里跑一次根级写（能力只能从 `KnowledgeTx` 取，所以没有"事务外"的第二种打法）。
     *
     * 入口没放行（库不在 / 只读）时直接 error：那种绿是假的，宁可红在前提上。
     */
    private fun rootWrite(fileName: String, content: String, kb: String = "kb"): Boolean {
        val outcome = runBlocking { repo.transaction(KbName(kb)) { writeRootMarker(fileName, content) } }
        return when (outcome) {
            is KnowledgeRepository.WriteResult.Written -> outcome.value
            else -> error("事务入口没放行（库不在或只读）：$outcome——这一格要打的根级写没打上")
        }
    }

    private fun rootPath(fileName: String, kb: String = "kb"): File? {
        val outcome = runBlocking { repo.transaction(KbName(kb)) { rootFile(fileName) } }
        return when (outcome) {
            is KnowledgeRepository.WriteResult.Written -> outcome.value
            else -> error("事务入口没放行（库不在或只读）：$outcome")
        }
    }

    /**
     * 十三种逃逸形。前八种是"端口收一个现成 File"那个年代真能走到的写法，
     * 后五种是"名字"这一层独有的错形（空名、`.`、纯空白——`File(root, "")` 会退回根本身）。
     */
    private fun escapeShapes(tag: String): List<String> = listOf(
        "../$tag",                    // POSIX 相对上跳
        "sub/../$tag",                // 先下一层再上跳
        "$tag/../other",              // 上跳藏在中间
        "a/$tag.md",                  // 带斜杠的"文件名"
        "a\\$tag.md",                 // 带反斜杠的"文件名"（Windows 上那就是分隔符）
        "/absolute/$tag",             // POSIX 绝对路径
        "C:\\$tag",                   // Windows 盘符 + 反斜杠
        "C:$tag",                     // 盘符但一个分隔符都没有：File(root, "C:x") 在 Windows 上直接跳出根
        "\\\\server\\share\\$tag",    // UNC
        ".",                          // 当前目录不是文件
        "..",                         // 上一级不是文件
        "",                           // 空名
        "   "                         // 全空白
    )

    // ═══════════ ① 逃逸的根级名字：拒掉，而且整棵树一个字都不许多 ═══════════

    @Test
    fun `escaping root names are refused by the guard and nothing lands`() {
        seedKb()
        // 先走一次**合法**的根级写当热身：仓库第一次进事务会顺带做一次备份
        // （`.backup/<库>_<时间>/…` 与 `.last_backup` 就是这么来的）。
        // 不热身就取 `before`，那一笔合法落盘会被本轮的"什么都没多出来"当成逃逸——
        // 那是判据取错了对照点，不是守卫漏了。热身后 `before` 里已经含合法产物，
        // 后面每一颗被拒的名字都必须让树**一字不变**。
        assertTrue("热身那一次合法根级写竟然失败", rootWrite(".probe_warmup", "0"))
        val before = tree()
        assertTrue(
            "前提：热身后这棵树上只许有种子库、预置的节流标记与热身那颗文件，实到 $before",
            before.contains("kb/kb.json") && before.contains(".probe_warmup")
        )
        // 夹具自己的健康检查（ 就是红在这里没做才炸的）：
        // 树上有 `.backup/` 就说明节流没挡住那个 scope 之外的写者，那么"before == after"这条对照点
        // 根本不可信——它会把一次合法的备份算成"被拒的名字落了盘"。宁可红在这一句、说清是夹具坏了。
        assertFalse(
            "夹具失效：节流标记挡不住那个游离的启动备份，对照点不可信，实到 $before",
            before.any { it.startsWith("${KnowledgeBackupService.BACKUP_DIR_NAME}/") }
        )
        // 名字带一次运行的唯一后缀：万一反证注入真的把文件写到共享临时目录里，
        // 残骸不会让下一次跑"假红"——这一格判的是"这次有没有写出去"。
        val tag = "lb_root_escape_" + System.nanoTime()
        for (bad in escapeShapes(tag)) {
            assertNull("根级守门竟然交得出路径：[$bad]", rootPath(bad))
            assertFalse("根级写竟然成功了：[$bad]", rootWrite(bad, "越界内容"))
        }
        assertEquals("被拒的名字竟然落了盘：" + tree(), before, tree())
        assertFalse("写到 knowledge/ 外面去了：" + File(root.parentFile, tag).path,
            File(root.parentFile, tag).exists())
        assertFalse("`..` 被退而求其次当成一个库建出来了：" + tree(),
            tree().any { it.contains("lb_root_escape") })
        assertEquals("库目录一个字都不许被这条路径碰过：" + tree(kbDir),
            listOf("kb.json"), tree(kbDir))
    }

    // ═══════════ ①′ 夹具的前提：那一发启动备份真的活在 scope 之外 ═══════════

    /**
     * 这一格不测产品性质，测的是 [setUp] 那套静树手法成立与否。它的起因是 CI 上的一次掷硬币：
     * 同一份树（`daff711`+`28315e9`）连着两个 run—— 红在
     * [escaping root names are refused by the guard and nothing lands]，实到多出
     * `.backup/kb_20260929_1537/kb.json` 与 `.last_backup`； 全绿。
     * 差别只在 init 那一发备份落在 `before` 快照**之前还是之后**。
     *
     * 判据：给一个**没预置 marker** 的空根 + 已取消的 scope，构造仓库，有界地等 `.last_backup`。
     * 它落得下来 ⇒ 那个写者确实在 appScope 之外，"取消 scope 就没人写"是假的，预置才是必要的。
     * 反过来，哪天生产把 launch 接回 scope 的孩子位（那是好事），这一格先红，
     * 上面那些格与 [setUp] 的说明要一起改——红在这里比红在对照点上说得清。
     */
    @Test
    fun `the detached startup backup outlives a cancelled appScope`() {
        val looseRoot = Files.createTempDirectory("kb_root_detached_writer").toFile()
        val looseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            looseScope.cancel()
            KnowledgeRepository(
                knowledgeRoot = looseRoot,
                securePrefs = mockk<SecurePrefs>(relaxed = true),
                context = mockk<Context>(relaxed = true),
                appScope = looseScope
            )
            val marker = File(looseRoot, KnowledgeBackupService.MARKER_FILE)
            val deadline = System.currentTimeMillis() + 15_000L
            while (!marker.isFile && System.currentTimeMillis() < deadline) Thread.sleep(50L)
            assertTrue(
                "取消掉的 scope 竟然真挡得住启动备份——那 [setUp] 预置 marker 就没必要了，" +
                    "本轮与 [setUp] 的说明要一起改（等满 15s，根下没有 ${KnowledgeBackupService.MARKER_FILE}）",
                marker.isFile
            )
            assertTrue(
                "落下来这颗必须是备份自己写的时间戳（裸毫秒数），实到「${marker.readText()}」",
                marker.readText().toLongOrNull()?.let { it > 1_000_000_000_000L } == true
            )
        } finally {
            looseScope.cancel()
            looseRoot.deleteRecursively()
        }
    }

    // ═══════════ ② 拒绝要出声，而且要说清是哪个名字 ═══════════

    /**
     * 第二层（canonical 归属复核）**自己那一格**。上面那 13 种形压不到它：2026-09-30 两发反证实测——
     * 只摘掉名字判据里 `".."` 那一支、或只把 canonical 判定关掉，13 种形仍然全拒；
     * 两道判据互为冗余（只有同时关掉，`../lb_root_escape_…` 才当场落盘、本轮才红）。
     * 要让第二层单独开火，得交出一个"名字合法、文件系统却解析到别处"的形：符号链接最干净，
     * 但这台 Windows 造不出来（WinError 1314「客户端没有所需的特权」），于是拿控制字符当对照——
     * 实到 NUL 那一条走 `getOrElse`（note = "root path could not be canonicalised, refused: Invalid file path"），
     * U+0001 那一条走归属判定（note = "root marker resolves outside knowledge root, refused"）。
     * 而这两条都是 **Windows 侧**的行为：Linux 上 U+0001 是一个完全合法的文件名字符，会被收下。
     *
     * 所以本轮判的是**契约**而不是某个平台的读数：守门交得出的每一条路径都必须**严格**在 knowledge/ 根下
     * （等于根目录本身也算越界——那正是关掉第二层后 U+0001 那条被规范成的东西），交不出来就是 null。
     * ⇒ 这台 Windows 上它有牙；Linux 侧它只是契约检查。这句话同时记进账本，不写成"两层各有独立对照物"。
     */
    @Test
    fun `whatever the second layer hands back is strictly inside the knowledge root`() {
        seedKb()
        val rootCanonical = root.canonicalPath
        val inside = rootCanonical + File.separator
        val nasty = listOf(
            "a\u0000b",                       // Windows：规范化直接拒
            "a\u0001b",                       // Windows：规范化成根目录本身
            "con", "nul", "aux.txt",                      // Windows 保留名，Linux 当普通文件名
            "x:", "C:", "..", ".", "", "   ",
            "sub/../.last_backup", "C:\\etc"
        )
        val handedBack = mutableListOf<Pair<String, String>>()
        for (name in nasty) {
            val file = rootPath(name) ?: continue
            // 规范化自己也要接住：关掉第二层之后这道门会交出「连规范化都做不到」的一条路径
            // （`File(root, "a` + U+0000 + `b")` 当场抛 IOException: Invalid file path）。
            // 那同样是违例，而且必须由本轮**说人话**地红——让异常穿出去会把仪器错报成产品崩溃。
            val canonical = runCatching { file.canonicalPath }
            assertTrue(
                "守门交出了一条归属都判不出的路径（规范化就抛了）：[$name] -> " + file.path +
                    "，异常：" + canonical.exceptionOrNull(),
                canonical.isSuccess
            )
            val path = canonical.getOrNull()!!
            assertTrue(
                "守门交出了一条不在 knowledge/ 根**之内**的路径：[$name] -> " + path,
                path.startsWith(inside) && path != rootCanonical
            )
            handedBack += name to path
        }
        // `handedBack` 是读数不是判据：这台机器上它含 "con"/"nul"/"aux.txt"（名字合法、写不写得动另说），
        // Linux 上还会多一条 U+0001。真正的判据是循环里那两句——每一台机器都必须守"严格在里面"。
        assertTrue(
            "根目录本身不许当成一个 marker 交出来：" + handedBack.map { it.first }.joinToString(),
            handedBack.none { (_, path) -> path == rootCanonical }
        )
    }

    /**
     * 原来那句 `guardedWrite` 收一个现成 File，所以它没有"名字非法"这一层，也就没有这一层的声音。
     * 新入口多出来的这条判断不许做成不出声的 return false——
     * 与批次四那句"一次不留痕的拒绝等于静默跳过"同一口径（那条坑记在 `atomicWriteAt` 上）。
     */
    @Test
    fun `a refused root name is said out loud twice and names the file`() {
        seedKb()
        val errs = mutableListOf<String>()
        val warns = mutableListOf<String>()
        mockkStatic(Log::class)
        try {
            // L.e 走三参重载且第三个实参恒为 null，L.w 走两参重载；两条都接住，
            // 别把 MockKException 当成断言失败报出来（那是仪器错报）。
            every { Log.e(any<String>(), capture(errs), isNull()) } returns 0
            every { Log.w(any<String>(), capture(warns)) } returns 0
            val tag = "lb_root_silent_" + System.nanoTime()
            assertFalse("非法名字必须写不成", rootWrite("../$tag", "越界内容"))
            assertTrue("守门那一层要说出不收这个名字，实到 $warns",
                warns.any { it.contains(ROOT_GUARD) })
            assertTrue("写这一层要留一条错误级痕迹，实到 $errs",
                errs.any { it.contains(ROOT_REFUSED) })
            assertTrue("痕迹要点名到被拒的那个名字，否则排障时不知道是谁没写成：warns=$warns errs=$errs",
                (warns + errs).any { it.contains("lb_root_silent") })
        } finally {
            unmockkStatic(Log::class)
        }
    }

    // ═══════════ ③ 合法名字仍然写得动（否则上面两格是假安全）═══════════

    @Test
    fun `a legitimate root marker still lands and touches no library`() {
        seedKb()
        // 根级 marker 一族按设计都以 `.` 开头，守门不许把它们顺手挡掉
        val name = ".lb_root_probe_" + System.nanoTime()
        assertTrue("合法根级名字被自己的守门挡下——那上面两格的拒绝就成了假安全",
            rootWrite(name, "根级 marker 的内容"))
        val marker = File(root, name)
        assertTrue("返回 true 却没落盘", marker.isFile)
        assertEquals("根级 marker 的内容", marker.readText(Charsets.UTF_8))
        // 路径给得出来，而且就在 knowledge/ 根下（不是某个库里）
        val resolved = rootPath(name)
        assertNotNull(resolved)
        assertTrue("解析结果跑到了 knowledge/ 外面：" + resolved!!.canonicalPath,
            resolved.canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertEquals("根级那一支不许顺手往库里写东西：" + tree(kbDir),
            listOf("kb.json"), tree(kbDir))
    }

    // ═══════════ ④ marker 自己的语义一个字都没改 ═══════════

    /**
     * `.last_backup` 的三条老语义：同一个文件名、内容仍是那一个裸毫秒数、
     * 而且它仍是"上一次备份发生在什么时候"的唯一事实源（备份判间隔读的就是它）。
     *
     * 名字用真的那颗常量而不是字面量：有人改 marker 名就要连带撞到这里。
     */
    @Test
    fun `the root write keeps the marker_s_own_meaning`() {
        seedKb()
        val stamped = 1_700_000_000_000L
        assertTrue("marker 写不出去了", rootWrite(KnowledgeBackupService.MARKER_FILE, stamped.toString()))

        val marker = File(root, KnowledgeBackupService.MARKER_FILE)
        assertEquals("内容仍是那一个裸毫秒数", stamped.toString(), marker.readText(Charsets.UTF_8))
        // 备份判间隔就是这么读的（KnowledgeBackupService.backupIfNeededUnlocked 里那一行）：读回来要能解析
        assertEquals("写下去的数与读回来的数必须同一个", stamped, marker.readText().toLong())
        assertEquals("marker 仍然落在 knowledge/ 根下，而不是某个库里",
            root.canonicalFile.path, marker.canonicalFile.parentFile.path)
    }

    // ═══════════ ⑤ 写核分成两支之后，只读判定与原行为都还在 ═══════════

    /**
     * 这一格盯的是**本次改动唯一的结构性风险**：`writeFileCheckedUnlocked` 多了一支"路径已解析"的入口。
     * 万一有人把库内那一支的只读判定挪走（或干脆都改用新入口），盘上就会多出
     * "来自未来的库被今天的 App 改写过"——那正是这条只读保护当初写下来的理由。
     *
     * 反向的一半同样承重：根级 marker 不属于任何库，所以**别的库只读不许把它一起挡掉**。
     * 挡了就是改行为——备份间隔从此判不准，而且 `kbOwning` 对点开头条目返回 null 的原意也被改了。
     */
    @Test
    fun `the read only judgement survives the split core`() {
        val future = "future"
        val dir = File(root, future).apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = future, displayName = "未来的她", turnCount = 7, active = true)
            ),
            Charsets.UTF_8
        )
        File(dir, ".schema_version").writeText(
            (KnowledgeSchemaVersion.CURRENT + 1).toString(), Charsets.UTF_8
        )
        File(dir, "moment").mkdirs()
        File(dir, "moment/recent.md").writeText("未来版本写下的此刻\n", Charsets.UTF_8)
        seedKb()
        runBlocking { repo.migrateIfNeeded(future) }
        assertTrue("前提：这一格要有一本只读库可打", repo.isSchemaReadOnly(future))

        val before = tree(dir)
        val content = File(dir, "moment/recent.md").readText(Charsets.UTF_8)

        // ① 库内那一支：只读拒绝还在原处，拒了要说、一个字节都不许多
        // （`atomicWriteAt` 是**锁内**原语，这里单线程顺序调用，与 KnowledgeMigratorWriteGuardTest 同一打法）
        val errs = mutableListOf<String>()
        mockkStatic(Log::class)
        try {
            every { Log.e(any<String>(), capture(errs), isNull()) } returns 0
            every { Log.w(any<String>(), any<String>()) } returns 0
            assertFalse("只读库竟然写得进去",
                repo.migratorStorage.atomicWriteAt(future, "moment/recent.md", "今天的 App 想改写"))
            // ② 根级那一支：marker 不属于任何库，只读库在场也照样写得动（= 改之前的行为）
            assertTrue("根级 marker 被别的库的只读状态挡下了——那是改行为",
                rootWrite(".lb_root_with_readonly_" + System.nanoTime(), "1700000000000"))
            assertTrue("拒绝要留一条痕迹（新入口把 false 当成可以发生的事，但不许不出声），实到 $errs",
                errs.any { it.contains("migration write refused") })
        } finally {
            unmockkStatic(Log::class)
        }
        assertEquals("只读库的目录树被改动了：" + tree(dir), before, tree(dir))
        assertEquals("正文一个字都不许多", content, File(dir, "moment/recent.md").readText(Charsets.UTF_8))

        // ③ 公开写路径也一样：只读库还是写不进。分两支不能分丢那一支
        runBlocking { repo.writeFile(future, "moment/recent.md", "今天的 App 想改写") }
        assertEquals(content, File(dir, "moment/recent.md").readText(Charsets.UTF_8))
    }

    // ═══════════ ⑥ 形状：端口不再交得出路径，备份格自己仍然零写能力 ═══════════

    private val mainRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    /** 剥掉块注释与行注释：解释"这里以前有过一份"的注释不该被算成又一份实现 */
    private fun codeOf(name: String): String {
        val f = File(mainRoot, name)
        assertTrue("扫不到 $f —— 这一族的判据会恒绿，比不测更坏", f.isFile)
        val src = f.readText(Charsets.UTF_8).replace("\r\n", "\n")
        return buildString {
            var i = 0
            while (i < src.length) {
                when {
                    src.startsWith("/*", i) -> {
                        val end = src.indexOf("*/", i + 2).takeIf { it >= 0 } ?: src.length
                        i = minOf(end + 2, src.length)
                    }
                    src.startsWith("//", i) -> {
                        val end = src.indexOf('\n', i).takeIf { it >= 0 } ?: src.length
                        i = end
                    }
                    else -> { append(src[i]); i++ }
                }
            }
        }
    }

    /**
     * 行为那些格看不见这一族：把 `guardedWrite(target: File, …)` 原样搬回来，它们照样全绿
     * ——因为"端口收一个外部拼好的 File"这件事本身就不是行为，是**形状**。
     * 判据按词面判（与 `StorageBoundaryOwnershipTest` 同一套：注释先剥掉，命中数判相等）。
     */
    @Test
    fun `the port no longer hands out a path and the backup cell still holds no write power`() {
        val dataDir = File(mainRoot, "data")
        val allData = dataDir.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }.orEmpty()
            .associate { it.name to codeOf("data/${it.name}") }
        assertTrue("扫不到 data/ 目录——这一族的判据会恒绿", allData.size >= 10)
        val backup = allData["KnowledgeBackupService.kt"]
            ?: error("扫不到 KnowledgeBackupService.kt，路径指错了地方")

        // ① 旧门关上：整个 data/ 里不该再有人声明、调用、或在端口上留着那句收 File 的写口
        val oldDoor = allData.mapValues { (_, code) -> Regex("guardedWrite").findAll(code).count() }
            .filterValues { it > 0 }
        assertEquals("`guardedWrite` 那道收 File 的门不许以任何形式回来，实到 $oldDoor",
            emptyMap<String, Int>(), oldDoor)

        // ② 新门只收名字：签名必须逐字对上（写成 File 参数就当场红）
        assertTrue(
            "BackupStorage 上的根级写口必须是「收裸文件名」的形状，实到：" +
                Regex("fun writeRootMarker\\([^)]*\\)").findAll(backup).map { it.value }.toList(),
            Regex("fun writeRootMarker\\(\\s*fileName:\\s*String,\\s*content:\\s*String\\s*\\)")
                .containsMatchIn(backup)
        )
        assertEquals("备份格对根级写口只许调用一次", 1,
            Regex("storage\\.writeRootMarker\\(").findAll(backup).count())

        // ③ 备份格自己零写能力：落盘那一件必须留在仓库里
        val tells = listOf(
            "端口收 File 的旧门" to Regex("guardedWrite"),
            "第二条原子写" to Regex("atomicWriteText"),
            "自己开流或直接写文件" to Regex("""FileOutputStream\(|RandomAccessFile\(|\.writeText\(|\.printWriter\("""),
            "自己判 canonical 越界" to Regex("""\.canonicalPath""")
        )
        val hits = tells.mapNotNull { (what, regex) ->
            val found = regex.findAll(backup).map { it.value }.distinct().toList()
            if (found.isEmpty()) null else what to found
        }
        assertTrue("备份格不许长出第二份写能力，实到 $hits", hits.isEmpty())

        // 尺自己也要有证人：同一条判据必须咬得住"旧门 + 收 File + 自己落盘"那一种写法
        val bait = """
            interface Old { fun guardedWrite(target: File, content: String): Boolean }
            fun worse(root: File) {
                atomicWriteText(File(root, ".last_backup"), "x")
                File(root, ".last_backup").writeText("x")
                val p = File(root, ".last_backup").canonicalPath
            }
        """.trimIndent()
        val bitten = tells.filter { (_, regex) -> regex.containsMatchIn(bait) }.map { it.first }
        assertEquals("尺是死的：旧形状必须被这四条各咬住一次", tells.map { it.first }, bitten)

        // ④ 仓库这一侧的落盘出口仍然只有一个定义，而且没被放宽成公共的
        // 2026-10-01：atomicWriteText 从 KnowledgeRepository.kt 拆进 KnowledgeRepoIO.kt
        // （internal 扩展函数），落盘出口仍只有这一处，只是换了文件与可见性层级。
        val repoIO = allData["KnowledgeRepoIO.kt"]
            ?: error("扫不到 KnowledgeRepoIO.kt，路径指错了地方")
        assertEquals("仓库里不许出现第二个 atomicWriteText 定义", 1,
            Regex("(?m)^[ \\t]*(?:\\w+\\s+)*fun\\s+(?:[A-Za-z_]\\w*\\.)?atomicWriteText\\(").findAll(repoIO).count())
        assertTrue("唯一出口必须一直是 internal 或 private（不许放宽成公共的）",
            Regex("(?m)^[ \\t]*(?:internal|private)\\s+fun\\s+(?:[A-Za-z_]\\w*\\.)?atomicWriteText\\(").containsMatchIn(repoIO))
    }

    private companion object {
        /** 与 [KnowledgeRepository] 那两条拒绝日志对齐的关键字；改措辞要连这一格一起改 */
        const val ROOT_GUARD = "rejected root marker name"
        const val ROOT_REFUSED = "root marker write refused"
    }
}
