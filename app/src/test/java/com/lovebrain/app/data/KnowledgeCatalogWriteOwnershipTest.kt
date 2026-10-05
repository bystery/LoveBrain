package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 目录写侧那一格的**牙**（第5节第3条 完成定义「KnowledgeRepository 不再是所有知识能力的唯一入口」）。
 *
 * 这格搬对了没有，光看行为测试看不出来——`KnowledgeSeedWriteBytesBaselineTest` 钉的是字节、
 * `ReadOnlySchemaWriteGateTest` 钉的是磁盘没动，两者都不管"这些判断现在住在谁家里、
 * 落盘走的还是不是那一条链"。所以这里三族判据缺一不可：
 *
 * 1. [Recorder 族]：判断（三道拒绝、seed 恰好 13 格、正式目录删成功才删备份、
 *    删掉当前库后在同一次锁里切下一个）用一份假存储逐条点名——**一次操作开几次事务、
 *    按什么顺序问仓库**都是被判的数，把一次 create 拆成 13 次各写各的当场就红。
 * 2. [只读族]：真仓库 + schema 比本 App 还新的库，通过**新主人**调 create / setActive /
 *    updateDisplayName / ensureInitialKnowledgeBase，整棵库目录树必须逐字节不动，
 *    且 `.schema_version` 那份旧证据不许先被销毁——判定住在写链那一侧，
 *    新主人想绕也绕不过去（绕过去了这一族当场红）。
 * 3. [形状族]：源码判据。仓库不许再出现这四个成员、端口不许绑回仓库、
 *    而新主人不许长出第二份 File / Mutex / canonical 判断 / 落盘实现。
 *
 * ⚠ 第 3 族不是"用 grep 代替测试"：它钉的就是"唯一入口这条线有没有被重新画回去"这件事本身，
 * 行为那两族看不见它（把 create 原样搬回仓库，前两族照样全绿）。
 */
class KnowledgeCatalogWriteOwnershipTest {

    // ═══════════ 假存储：只记账，不落盘 ═══════════

    /**
     * 一份"仓库的替身"。
     *
     * 它按 [CatalogWriteStorage] 的口径记录每一次调用（含顺序），所以本类能判
     * "一次建库到底开了几次事务""删除时先动正式目录还是先动备份"这类**编排**事实——
     * 真仓库跑不出这些数（它只会把字节写下去）。
     */
    private inner class Recorder : CatalogWriteStorage {
        /** 按调用顺序记的事件流 */
        val events = mutableListOf<String>()
        val transactions = mutableListOf<String>()
        val writtenSlots = mutableListOf<Pair<String, String>>()
        val metaTouched = mutableListOf<String>()
        var lockDepth = 0
        var maxLockDepth = 0
        var activeKb = ""
        var dirNames = listOf<String>()
        var newest: String? = null
        var present = mutableSetOf<String>()
        var markerPresent = false
        var entries: List<KnowledgeBase> = emptyList()
        var backupsWritten = 0

        override suspend fun <T> inWriteLock(block: suspend () -> T): T {
            lockDepth++
            maxLockDepth = maxOf(maxLockDepth, lockDepth)
            events += "lock:enter"
            try {
                return block()
            } finally {
                lockDepth--
                events += "lock:exit"
            }
        }

        override fun writeCatalogTransaction(kbName: String, block: CatalogTx.() -> Unit) {
            check(lockDepth == 1) { "事务必须在一次锁里跑，实到 lockDepth=$lockDepth" }
            transactions += kbName
            events += "tx:$kbName"
            block(object : CatalogTx {
                override fun write(relativePath: String, content: String): Boolean {
                    writtenSlots += kbName to relativePath
                    events += "write:$kbName/$relativePath"
                    return true
                }

                override fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean {
                    metaTouched += kbName
                    events += "meta:$kbName"
                    return true
                }

                override fun exists(relativePath: String): Boolean = false
            })
        }

        override fun catalogDirNames(): List<String> = dirNames

        override fun newestCatalogDirName(): String? = newest

        override fun catalogDirPresent(kbName: String): Boolean = kbName in present

        override fun makeCatalogSkeleton(kbName: String) {
            events += "skeleton:$kbName"
            present += kbName
        }

        override fun removeCatalogDir(kbName: String): Boolean {
            events += "remove:$kbName"
            return kbName in present
        }

        override fun deleteCatalogBackups(kbName: String) {
            events += "backups:$kbName"
        }

        override fun initMarkerPresent(): Boolean = markerPresent

        override fun markInitialized() {
            events += "marker"
            markerPresent = true
        }

        override fun entries(): List<KnowledgeBase> = entries

        override fun encodeMeta(kb: KnowledgeBase): String = "meta-of-${kb.name}"

        override fun template(name: String): String = "template-$name"

        override fun timestamp(): String = "2026-09-29T10:00:00+08:00"

        override var activeKbName: String
            get() = activeKb
            set(value) {
                events += "active:$value"
                activeKb = value
            }

        override suspend fun migrateCatalogEntry(kbName: String) {
            events += "migrate:$kbName"
        }

        override fun scheduleBackup() {
            backupsWritten++
            events += "schedule-backup"
        }

        override suspend fun activeEntry(): KnowledgeBase? = entries.firstOrNull()

        override suspend fun writeStage(kbName: String, stage: String) {
            events += "stage:$kbName"
        }

        override suspend fun writeDocument(kbName: String, relativePath: String, content: String) {
            events += "document:$kbName/$relativePath"
        }
    }

    // ═══════════ 1. 编排：一次操作一次事务，顺序承重 ═══════════

    private val seedSlots = listOf(
        KnowledgeCatalogStore.META_FILE,
        "understand/me.md", "understand/her.md", "understand/warmth.md",
        "moment/topic.md", "moment/recent.md", "moment/scene.md", "moment/plan.md",
        "memory/lessons.md", "memory/raw_chat.md", "memory/raw_topic.md",
        "memory/raw_scene.md", "memory/counseling_log.md"
    )

    @Test
    fun `create writes all thirteen seed slots inside exactly one transaction and one lock`() = runBlocking {
        val rec = Recorder()
        val store = KnowledgeCatalogWriteStore(rec)

        val kb = store.create("Seed KB-1", "显示名")

        assertEquals("库名要先过 sanitizer", "seedkb-1", kb.name)
        assertEquals("一次建库只许开一次事务（多了就是 seed 被拆成几段可中断的写）",
            listOf("seedkb-1"), rec.transactions)
        assertEquals("锁只许被拿一次（拿两次中间别人能插进来）", 1, rec.maxLockDepth)
        assertEquals("13 格 seed 的路径与顺序都在这", seedSlots, rec.writtenSlots.map { it.second })
        assertTrue("kb.json 必须与 seed 同一事务", rec.events.indexOf("tx:seedkb-1") < rec.events.indexOf("write:seedkb-1/${KnowledgeCatalogStore.META_FILE}"))
        assertEquals("显示名空着不许盖上去", "显示名", kb.displayName)
        assertEquals("建库之后要排节流备份", 1, rec.backupsWritten)
        assertEquals("第一个库必须被记成当前库", "seedkb-1", rec.activeKb)
    }

    /** 三道拒绝都必须留下"什么都没发生"：不建目录、不落一格字节、不多拿一次锁 */
    @Test
    fun `create rejects bad names without touching the disk or nesting a lock`() = runBlocking {
        for (raw in listOf("   ", "☆☆☆", "a".repeat(101))) {
            val rec = Recorder()
            val store = KnowledgeCatalogWriteStore(rec)
            val thrown = runCatching { store.create(raw, "长名库") }.exceptionOrNull()
            assertTrue("空名/全被过滤名/过长名一律 IllegalArgumentException，实到 $thrown",
                thrown is IllegalArgumentException)
            assertEquals("拒绝不该留下目录：$raw", emptyList<String>(), rec.events.filter { it.startsWith("skeleton:") })
            assertEquals("拒绝不该落一格字节：$raw", emptyList<Pair<String, String>>(), rec.writtenSlots)
            assertEquals("拒绝不该开一次事务：$raw", emptyList<String>(), rec.transactions)
            assertEquals("拒绝不该改当前库：$raw", "", rec.activeKb)
            // 拒绝走的是与建库同一条持锁路径（原来就是这样，没为它开第二条无锁通道），
            // 但一次都不能套——套了就等于在锁里等别人放锁。
            assertEquals("一次调用只许进一次锁：$raw", 1, rec.maxLockDepth)
        }
    }

    @Test
    fun `create refuses a name that already occupies the root including a stray file`() = runBlocking {
        val rec = Recorder()
        rec.present += "taken"
        val store = KnowledgeCatalogWriteStore(rec)
        assertTrue("占位检查必须问仓库那一道，不许新主人自己判 File 存在性",
            runCatching { store.create("taken", "重名") }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(emptyList<String>(), rec.events.filter { it.startsWith("skeleton:") })
    }

    /** 切库要逐库改 kb.json：每个目录一次事务、且都在同一次锁里，含列不进清单的目录 */
    @Test
    fun `setActive rewrites every library directory exactly once inside one lock`() = runBlocking {
        val rec = Recorder()
        rec.dirNames = listOf("a", "b", "broken-metadata")
        val store = KnowledgeCatalogWriteStore(rec)

        runBlocking { store.setActive("b") }

        assertEquals("当前库名一次写定", "b", rec.activeKb)
        assertEquals("逐库一次事务", listOf("a", "b", "broken-metadata"), rec.transactions)
        assertEquals("每库改一次 kb.json", 3, rec.metaTouched.size)
        assertEquals(1, rec.maxLockDepth)
    }

    /**
     * 删除的三条顺序是承重的：先删正式目录、删成了才动备份（反过来就是"备份没了、库还在"），
     * 只有删掉的是当前库才改激活项。这一格判的就是**事件顺序**。
     */
    @Test
    fun `delete removes the library before its backups and only then reassigns active`() = runBlocking {
        val rec = Recorder()
        rec.present += "gone"
        rec.present += "keep"
        rec.dirNames = listOf("gone", "keep")
        rec.newest = "keep"
        rec.activeKb = "gone"
        val store = KnowledgeCatalogWriteStore(rec)

        val ok = runBlocking { store.delete("gone") }

        assertTrue(ok)
        val order = rec.events.filter {
            it.startsWith("remove:") || it.startsWith("backups:") || it.startsWith("active:") || it.startsWith("meta:")
        }
        assertEquals(
            "删库的事件顺序必须是：正式目录 → 备份 → 改当前库（含逐库 kb.json）",
            listOf("remove:gone", "backups:gone", "active:keep", "meta:gone", "meta:keep"),
            order
        )
        assertEquals("重新指派当前库不许再拿一次锁", 1, rec.maxLockDepth)
    }

    @Test
    fun `delete leaves backups untouched when the guarded removal refused`() = runBlocking {
        val rec = Recorder()
        val store = KnowledgeCatalogWriteStore(rec)

        val ok = runBlocking { store.delete("../outside") }

        assertFalse("越界名必须被拒", ok)
        assertEquals("被拒时一个备份都不许动", emptyList<String>(), rec.events.filter { it.startsWith("backups:") })
        assertEquals("被拒时当前库不许改", "", rec.activeKb)
    }

    @Test
    fun `delete of the last library clears the active name instead of inventing one`() = runBlocking {
        val rec = Recorder()
        rec.present += "only"
        rec.dirNames = listOf("only")
        rec.newest = null
        rec.activeKb = "only"
        val store = KnowledgeCatalogWriteStore(rec)

        assertTrue(runBlocking { store.delete("only") })

        assertEquals("", rec.activeKb)
        assertEquals("没得切就不该逐库改 kb.json", emptyList<String>(), rec.transactions)
    }

    /** 首次启动：恰好一个默认库、一次事务、全部落成了才写标记 */
    @Test
    fun `ensureInitialKnowledgeBase seeds one default library then marks completion`() = runBlocking {
        val rec = Recorder()
        val store = KnowledgeCatalogWriteStore(rec)

        runBlocking { store.ensureInitialKnowledgeBase() }

        assertEquals(listOf("default"), rec.transactions)
        assertEquals(seedSlots, rec.writtenSlots.map { it.second })
        assertEquals("标记必须在 seed 之后（半套文件 + 标记 = 下次启动不补）",
            true, rec.events.indexOf("marker") > rec.events.indexOf("write:default/memory/counseling_log.md"))
        assertEquals("default", rec.activeKb)
        assertEquals(1, rec.maxLockDepth)
    }

    /** 用户删掉最后一个库之后不重复创建——判据是那个根级标记，不是"有没有库" */
    @Test
    fun `ensureInitialKnowledgeBase does not recreate after the user deleted the last library`() = runBlocking {
        val rec = Recorder()
        rec.markerPresent = true
        val store = KnowledgeCatalogWriteStore(rec)

        runBlocking { store.ensureInitialKnowledgeBase() }

        assertEquals(emptyList<String>(), rec.transactions)
        assertEquals(emptyList<String>(), rec.events.filter { it.startsWith("skeleton:") })
        assertEquals("没创建新库就不该改当前库", "", rec.activeKb)
    }

    /** 已有库：先迁移再补齐（顺序反了空文件会遮住迁移判定），且逐库一次事务 */
    @Test
    fun `ensureInitialKnowledgeBase migrates before completing files for existing libraries`() = runBlocking {
        val rec = Recorder()
        rec.entries = listOf(KnowledgeBase(name = "her", displayName = "她", updatedAt = "x"))
        rec.present += "her"
        val store = KnowledgeCatalogWriteStore(rec)

        runBlocking { store.ensureInitialKnowledgeBase() }

        assertTrue("迁移必须早于补齐：" + rec.events,
            rec.events.indexOf("migrate:her") < rec.events.indexOf("tx:her"))
        assertEquals("已有库不许新建默认库", listOf("her"), rec.transactions)
        assertTrue("补齐要写缺失格", rec.writtenSlots.isNotEmpty())
        assertEquals("补齐判存在问的是 exists()，不许用\"读出来是空\"", true, rec.events.contains("marker"))
    }

    /** 端口上那三员的主人各在别处：这里只许转一次手，不许自己再判一遍 */
    @Test
    fun `catalog port members owned elsewhere are handed over exactly once`() = runBlocking {
        val rec = Recorder()
        val store = KnowledgeCatalogWriteStore(rec)

        runBlocking { store.updateStage("kb", "热恋期") }
        runBlocking { store.writeFile("kb", "understand/me.md", "画像") }
        runBlocking { store.listAll() }
        runBlocking { store.getActive() }

        assertEquals(listOf("stage:kb"), rec.events.filter { it.startsWith("stage:") })
        assertEquals(listOf("document:kb/understand/me.md"), rec.events.filter { it.startsWith("document:") })
        assertEquals("阶段与正文写都不许在本类里开一次自己的事务", emptyList<String>(), rec.transactions)
        assertEquals("更不许为此多拿一次锁", 0, rec.maxLockDepth)
    }

    // ═══════════ 2. 只读语义：通过新主人调用，仍然一个字节都不写 ═══════════

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private val futureSchema = KnowledgeSchemaVersion.CURRENT + 1

    @Before
    fun setUp() {
        root = Files.createTempDirectory("catalog_write").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    /**
     * 真仓库 + 一份"来自未来"的库。
     *
     * `context.assets` 直接抛：JVM 上没有 AssetManager，而这一族只判"写没写成"，
     * 读模板得到空串就够了（读不到内容 ≠ 写进去了内容）。
     */
    private fun readOnlyRepo(): KnowledgeRepository {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.assets } throws IllegalStateException("JVM 测试没有 assets")
        val repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = ctx,
            appScope = appScope
        )
        seedFutureKb()
        runBlocking { repo.migrateIfNeeded("kb") }
        assertTrue("前提：这一族要有只读库可打", repo.isSchemaReadOnly("kb"))
        return repo
    }

    private fun seedFutureKb(name: String = "kb") {
        val dir = File(root, name).apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(
                    name = name, displayName = "未来的她", stage = "磨合期",
                    turnCount = 7, topicCount = 0, active = true
                )
            ),
            Charsets.UTF_8
        )
        File(dir, ".schema_version").writeText(futureSchema.toString(), Charsets.UTF_8)
        listOf("understand", "moment", "memory").forEach { File(dir, it).mkdirs() }
        listOf(
            "moment/recent.md" to "未来版本写下的此刻\n",
            "moment/scene.md" to "未来版本的场景链\n",
            "moment/topic.md" to "- [2026-09-24 09:00] 正在聊：未来的话题",
            "understand/me.md" to "未来版本的画像\n",
            "memory/lessons.md" to "未来的经验\n"
        ).forEach { (rel, text) ->
            val f = File(dir, rel)
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
        }
    }

    private fun sha(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 整棵库目录树的读数；`.schema_version` 也在里面——那份旧证据不许被先销毁 */
    private fun snapshot(): Map<String, String> {
        val base = File(root, "kb")
        return base.walkTopDown().filter { it.isFile }.associate { f ->
            f.relativeTo(base).path.replace('\\', '/') to sha(f.readBytes())
        }
    }

    private fun assertTreeUntouched(before: Map<String, String>, label: String) {
        val after = snapshot()
        assertEquals("$label：只读库的目录树被改动过：\n" +
            (before.keys + after.keys).sorted().filter { before[it] != after[it] }.joinToString(),
            before, after
        )
    }

    @Test
    fun `setActive through the new owner writes nothing into a read-only library`() {
        val repo = readOnlyRepo()
        val before = snapshot()
        runBlocking { repo.catalogWrites.setActive("kb") }
        assertTreeUntouched(before, "setActive")
        assertEquals(".schema_version 是只读判定的依据，不许被改", futureSchema.toString(),
            File(root, "kb/.schema_version").readText().trim())
    }

    @Test
    fun `updateDisplayName through the new owner writes nothing into a read-only library`() {
        val repo = readOnlyRepo()
        val before = snapshot()
        runBlocking { repo.catalogWrites.updateDisplayName("kb", "被 v3 改掉的显示名") }
        assertTreeUntouched(before, "updateDisplayName")
    }

    @Test
    fun `create through the new owner refuses an occupied read-only name and leaves nothing behind`() {
        val repo = readOnlyRepo()
        val before = snapshot()
        val thrown = runCatching { runBlocking { repo.catalogWrites.create("kb", "同名") } }.exceptionOrNull()
        assertTrue("已占位（还是个只读库）的名字必须被拒，实到 $thrown", thrown is IllegalArgumentException)
        assertTreeUntouched(before, "create 撞只读同名库")
    }

    @Test
    fun `ensureInitialKnowledgeBase through the new owner leaves a read-only library byte identical`() {
        val repo = readOnlyRepo()
        val before = snapshot()
        runBlocking { repo.ensureInitialKnowledgeBase() }
        assertTreeUntouched(before, "ensureInitialKnowledgeBase 的迁移+补齐")
    }

    /** 删库那一道越界守卫仍然只有一处口径：库名带 `..` 时一个目录都不许动 */
    @Test
    fun `delete through the new owner cannot walk out of the knowledge root`() {
        val repo = readOnlyRepo()
        val outside = File(root.parentFile, "outside_${root.name}")
        outside.mkdirs()
        File(outside, "secret.md").writeText("库外的隐私", Charsets.UTF_8)
        val before = snapshot()

        assertFalse(runBlocking { repo.catalogWrites.delete("../${outside.name}") })
        assertTrue("被拒之后库外那个目录必须还在", outside.isDirectory)
        assertEquals("库外内容一个字都不许动", "库外的隐私", File(outside, "secret.md").readText())
        assertTreeUntouched(before, "越界 delete")
        outside.deleteRecursively()
    }

    // ═══════════ 3. 形状：唯一入口这条线有没有被画回去 ═══════════

    private val mainRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private fun source(name: String): String {
        val f = File(mainRoot, name)
        assertTrue("扫不到 $f —— 这一族的判据会恒绿，比不测更坏", f.isFile)
        return f.readText(Charsets.UTF_8).replace("\r\n", "\n")
    }

    /** 剥掉块注释与行注释：解释"这里以前有过一份"的注释不该被算成又一份实现 */
    private fun codeOf(src: String): String = buildString {
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

    @Test
    fun `the repository no longer declares the catalog write operations`() {
        val code = codeOf(source("data/KnowledgeRepository.kt"))
        listOf("create", "delete", "setActive", "updateDisplayName").forEach { op ->
            val decl = Regex("(?m)^\\s*(?:private |internal |public )?suspend fun $op\\(").containsMatchIn(code)
            assertFalse("库的存在性归 $op 的主人现在是 KnowledgeCatalogWriteStore，仓库不许再挂一个同名成员", decl)
        }
        assertFalse(
            "仓库不许再声明目录写侧的那四件（枚举归枚举格、写侧归 KnowledgeCatalogWriteStore）",
            Regex("fun (listAllUnlocked|ensureKbFilesCompleteUnlocked|setActiveUnlocked)\\(").containsMatchIn(code)
        )
    }

    @Test
    fun `the catalog port is implemented by the store and bound to it`() {
        val storeCode = codeOf(source("data/KnowledgeCatalogWriteStore.kt"))
        val repoCode = codeOf(source("data/KnowledgeRepository.kt"))
        assertTrue("KnowledgeBaseCatalogPort 的实现者必须是目录写侧那一格",
            Regex("KnowledgeCatalogWriteStore\\([\\s\\S]{0,200}?:\\s*KnowledgeBaseCatalogPort")
                .containsMatchIn(storeCode))
        assertFalse("仓库不许再实现这颗端口（那等于把唯一入口画回去）",
            storeCode.isBlank() || Regex("class KnowledgeRepository[\\s\\S]*?:[^\n]*KnowledgeBaseCatalogPort")
                .containsMatchIn(repoCode))
        val di = codeOf(source("di/AppModule.kt"))
        val binding = Regex("(?m)^\\s*single<[\\w.]*KnowledgeBaseCatalogPort>\\s*\\{(.*)\\}").find(di)
        assertTrue("找不到端口绑定：$di", binding != null)
        assertTrue("绑定必须指向那位主人，实到 ${binding!!.groupValues[1]}",
            "catalogWrites" in binding.groupValues[1])
    }

    @Test
    fun `the new owner holds no second copy of path lock or write power`() {
        val code = codeOf(source("data/KnowledgeCatalogWriteStore.kt"))
        // 按**词边界**判：`writeFile(` 里含子串 `File(`，按子串数会把"转手给端口"报成"自己摸磁盘"
        // （这条坑在 OddShapeOwnershipTest 的头注里写过一次，同一个误判不许在新尺上重来）。
        val tells = listOf(
            "自己摸 File" to Regex("""(?<![\w.])File\("""),
            "自持锁" to Regex("""(?<![\w.])Mutex\(|withLock"""),
            "自己判 canonical" to Regex("""\.canonicalPath|canonicalFile"""),
            "第二条落盘实现" to Regex(
                """atomicWriteText|FileOutputStream\(|RandomAccessFile\(|\.writeText\(|\.printWriter\(|mkdirs\(|deleteRecursively\("""
            ),
            "第二个 CoroutineScope" to Regex("""CoroutineScope|\.launch\b""")
        )
        val hits = tells.mapNotNull { (what, regex) ->
            val found = regex.findAll(code).map { it.value }.distinct().toList()
            if (found.isEmpty()) null else what to found
        }
        assertTrue("新主人不许有第二份路径 / 锁 / 落盘 / 调度能力，实到 $hits", hits.isEmpty())

        // 尺自己也要有证人：同一条判据必须咬得住"自己拼路径 + 裸写"那种写法
        val bait = """
            fun bad(root: Any, kb: String) {
                val f = File(root, kb)
                f.writeText("x")
            }
        """.trimIndent()
        val baitBites = tells.filter { (_, regex) -> regex.containsMatchIn(bait) }.map { it.first }
        assertEquals(
            "尺是死的：\"自己拼 File 再裸写\"这一种写法必须被这两条各咬住",
            listOf("自己摸 File", "第二条落盘实现"), baitBites
        )
        // 反向证人：端口那次转手不许被咬
        assertFalse(
            "转手给端口的 writeFile( 不许被当成自己摸 File",
            Regex("""(?<![\w.])File\(""").containsMatchIn("    override suspend fun writeFile(kb: String) = Unit")
        )
    }

    /** 端口那颗 KDoc 里"由同一个仓库实现"那句话是历史；写侧搬走之后要说清现在的事实 */
    @Test
    fun `the store reaches the boundary only through the registered capability port`() {
        val code = codeOf(source("data/KnowledgeCatalogWriteStore.kt"))
        assertEquals("本类对磁盘的全部接触都必须经 CatalogWriteStorage / CatalogTx",
            0, Regex("com\\.lovebrain\\.app\\.data\\.KnowledgeRepository").findAll(code).count())
        assertTrue("每一次写都必须落在一次 writeCatalogTransaction 里",
            Regex("storage\\.writeCatalogTransaction").containsMatchIn(code))
        assertTrue("事务块里只许用 CatalogTx 的三件事",
            Regex("storage\\.write\\(").findAll(code).count() == 0)
    }
}
