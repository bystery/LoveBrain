package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [KnowledgeBackupService] 的行为基线（§5.3 从 `KnowledgeRepository` 拆出的第一格）。
 *
 * 这一格以前只能靠 `KnowledgeRepository` + 真时间跑：12 小时的阈值与 5 秒的节流
 * 在测试里都等不起，于是"间隔没到不该再备份一次"这种真规则从来没被测过。
 * 拆出来之后墙钟是注入的，能拨。
 *
 * 除第一条之外全部走无锁核心——**锁的编排不在本类**（它归仓库那把唯一的 `fileMutex`），
 * 这里测的是"给它一个目录树，它该复制什么、修剪什么、删什么"。
 */
class KnowledgeBackupServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 记录每一笔经过写门的写入——写边界留在仓库，本类不许自己落盘 */
    private class FakeStorage(root: File) : BackupStorage {
        override val root: File = root
        val writes = mutableListOf<Pair<String, String>>()
        var acceptWrites = true
        override fun guardedWrite(target: File, content: String): Boolean {
            if (!acceptWrites) return false
            writes += target.name to content
            target.writeText(content)
            return true
        }
    }

    private lateinit var storage: FakeStorage
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        storage = FakeStorage(tmp.root)
    }

    /** 备份策略不拿 CoroutineScope——节流调度留在仓库（见类 KDoc 那条闸） */
    private fun service(
        root: File = tmp.root,
        maxCount: Int = 7,
        intervalMs: Long = 12L * 3600_000L
    ): KnowledgeBackupService = KnowledgeBackupService(
        storage = if (root == tmp.root) storage else FakeStorage(root),
        intervalMs = intervalMs,
        maxCount = maxCount,
        wallClockMs = { now }
    )

    private fun kb(name: String, vararg files: String) {
        val dir = File(storage.root, name)
        dir.mkdirs()
        files.forEach { rel ->
            val f = File(dir, rel)
            f.parentFile?.mkdirs()
            f.writeText("内容 of $rel @ $name")
        }
    }

    private fun backupDirs(): List<String> =
        File(storage.root, ".backup").listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted()
            ?: emptyList()

    @Test
    fun `a first run copies every library with its subdirectories and stamps the marker`() {
        kb("kb1", "kb.json", "understand/me.md")
        kb("kb2", "kb.json")

        service().backupIfNeededUnlocked()

        val groups = backupDirs()
        assertTrue("两个库都该有一份备份：$groups", groups.size == 2 && groups.all { it.startsWith("kb") })
        val copied = File(storage.root, ".backup/${groups.first { it.startsWith("kb1") }}")
        assertEquals("内容 of kb.json @ kb1", File(copied, "kb.json").readText())
        assertEquals(
            "子目录要一起复制，不能只拷顶层",
            "内容 of understand/me.md @ kb1", File(copied, "understand/me.md").readText()
        )
        assertEquals(listOf(".last_backup" to now.toString()), storage.writes)
    }

    @Test
    fun `hidden directories are never backed up into themselves`() {
        kb("kb1", "kb.json")
        File(storage.root, ".backup/kb1_20200101_0000").apply { mkdirs() }
        File(storage.root, ".hidden").mkdirs()
        File(storage.root, ".hidden/note.md").apply { parentFile?.mkdirs(); writeText("secret") }

        service().backupIfNeededUnlocked()

        val groups = backupDirs()
        assertEquals(
            "只有 kb1 该被复制，隐藏目录与 .backup 自身都不行：$groups",
            1, groups.count { !it.endsWith("20200101_0000") }
        )
        assertTrue("不该出现把 .hidden 当库备份出来的组：$groups",
            groups.none { it.startsWith(".hidden") || it.contains("hidden") })
        assertEquals(
            "旧的那一份备份不该被复制成两份",
            1, groups.count { it == "kb1_20200101_0000" }
        )
    }

    @Test
    fun `nothing is copied again before the interval has passed`() {
        kb("kb1", "kb.json")
        val s = service()
        s.backupIfNeededUnlocked()
        val first = backupDirs()

        now += 11L * 3600_000L
        s.backupIfNeededUnlocked()

        assertEquals("间隔没到就再跑一次，不该多出第二份", first, backupDirs())
    }

    @Test
    fun `a new run stamps the marker again once the interval has passed`() {
        kb("kb1", "kb.json")
        val s = service()
        s.backupIfNeededUnlocked()
        assertEquals(1, storage.writes.size)

        now += 13L * 3600_000L
        s.backupIfNeededUnlocked()

        // 断言写标记而不是断言目录数：目录名用的是真实分钟，同一分钟内跑两次会同名
        assertEquals(
            "隔够 12 小时就该再跑一次并更新标记",
            listOf(now.toString()), storage.writes.takeLast(1).map { it.second }
        )
        assertEquals(2, storage.writes.size)
    }

    /** 这条测的是"12 小时阈值不靠内存"——标记文件才是事实源，进程重启也算数 */
    @Test
    fun `the interval is judged from the marker file, not from memory`() {
        kb("kb1", "kb.json")
        File(storage.root, ".last_backup").writeText((now - 60_000L).toString())

        service().backupIfNeededUnlocked()

        assertTrue("标记显示刚备份过，就不该再备一次", backupDirs().isEmpty())
    }

    @Test
    fun `pruning keeps the newest maxCount per library and never touches another one`() {
        kb("kb1", "kb.json")
        val backupRoot = File(storage.root, ".backup").apply { mkdirs() }
        // 播种用的日期一律早于"今天"（本轮真实时间会生成一个更新的目录），
        // 并且彼此只差在日期段上，好让"留最新 7 份"这件事有唯一解
        val seeded = (1..9).map { "kb1_201908%02d_1030".format(it) }
        (seeded + listOf("kb2_20190801_1030", "kb2_20190802_1030")).forEach {
            File(backupRoot, it).mkdirs()
        }
        File(storage.root, ".last_backup").writeText((now - 13L * 3600_000L).toString())

        service(maxCount = 7, intervalMs = 0L).backupIfNeededUnlocked()

        val keptSeeded = backupDirs().filter { it.startsWith("kb1_2019") }.sorted()
        assertEquals(
            "本轮新备份占掉一个名额，播种的九份里最旧的三份该被剪掉",
            listOf("kb1_20190804_1030", "kb1_20190805_1030", "kb1_20190806_1030",
                "kb1_20190807_1030", "kb1_20190808_1030", "kb1_20190809_1030"),
            keptSeeded
        )
        assertEquals(
            "kb2 的备份一份都不许动",
            listOf("kb2_20190801_1030", "kb2_20190802_1030"),
            backupDirs().filter { it.startsWith("kb2_") }.sorted()
        )
        assertEquals(
            "kb1 这一组总共只剩 7 份（播种留下 6 份 + 本轮 1 份）",
            7, backupDirs().count { it.startsWith("kb1_") }
        )
    }

    /**
     * 恰好等于上限的一组要原样留着——这条是给 `>` / `>=` 这个边界装上的。
     *
     * 上面那格用 10 份剪到 7 份，把阈值写成 `>=` 也一样过；只有"刚好 7 份"这种
     * 不偏不倚的输入才能把差一个的错逼出来。补这条之前我注入了 `>=` 反例，
     * 结果 13 格全绿——那是一次假绿，缺口就在这。
     */
    @Test
    fun `a group exactly at the limit keeps every entry`() {
        val backupRoot = File(storage.root, ".backup").apply { mkdirs() }
        val seeded = (1..7).map { "kb1_2019080%d_1030".format(it) }
        seeded.forEach { File(backupRoot, it).mkdirs() }

        service(maxCount = 7).pruneBackups()

        assertEquals(seeded.sorted(), backupDirs().sorted())

        File(backupRoot, "kb1_20190808_1030").mkdirs()
        service(maxCount = 7).pruneBackups()

        assertEquals(
            "多出一份才动手：剪掉的必须是最旧的那份",
            seeded.drop(1).sorted() + "kb1_20190808_1030",
            backupDirs().sorted()
        )
    }

    /** 命名对不上时间戳规则的目录自成一组，永不参与修剪——宁可多留不可误删 */
    @Test
    fun `directories that do not match the timestamp naming are never pruned`() {
        File(storage.root, ".backup/manual_copy").mkdirs()
        File(storage.root, ".backup/also_manual_2026").mkdirs()
        File(storage.root, ".last_backup").writeText((now - 13L * 3600_000L).toString())

        service(maxCount = 1, intervalMs = 0L).backupIfNeededUnlocked()

        val groups = backupDirs()
        assertTrue("手工目录要原样留着：$groups",
            groups.containsAll(listOf("manual_copy", "also_manual_2026")))
    }

    /** 关键防线：前缀匹配会让删 kb-a 顺手删掉 kb-ab 的备份 */
    @Test
    fun `deleting one library's backups cannot touch a library whose name extends it`() {
        val backupRoot = File(storage.root, ".backup").apply { mkdirs() }
        listOf("kb-a_20260824_1030", "kb-a_20260825_1030", "kb-ab_20260824_1030")
            .forEach { File(backupRoot, it).mkdirs() }

        service().deleteBackupsFor("kb-a")

        assertEquals(listOf("kb-ab_20260824_1030"), backupDirs())
    }

    @Test
    fun `deleteBackupsFor on an unknown library removes nothing`() {
        val backupRoot = File(storage.root, ".backup").apply { mkdirs() }
        File(backupRoot, "kb1_20260824_1030").mkdirs()

        service().deleteBackupsFor("nope")

        assertEquals(listOf("kb1_20260824_1030"), backupDirs())
    }

    /**
     * 写边界不归本类管：备份唯一那次落盘（`.last_backup`）必须经过存储视图给的写门。
     *
     * 反过来也要成立——门拒绝时（只读 schema）本类不许绕过它自己写。
     * 这条是 P0-03 那道口在拆分之后的延伸：拆一个类出来，不能顺手多出一条不经检查的写路径。
     */
    @Test
    fun `the marker write goes through the storage gate and a refusal is not worked around`() {
        kb("kb1", "kb.json")
        val s = service()
        s.backupIfNeededUnlocked()
        assertEquals(listOf(".last_backup"), storage.writes.map { it.first })

        // 换一棵树、把门关掉：备份照样复制，但标记写不进去，也不许出现第二条写路径
        val second = FakeStorage(tmp.newFolder("second"))
        second.acceptWrites = false
        File(second.root, "kb9").apply { mkdirs() }
        File(second.root, "kb9/kb.json").writeText("x")
        val s2 = KnowledgeBackupService(storage = second, intervalMs = 0L, wallClockMs = { now })
        s2.backupIfNeededUnlocked()

        assertTrue("门关着的时候一个字都不该写进去", second.writes.isEmpty())
        assertTrue("复制动作本身不经过写门（整目录复制），但目录树里不能有 .last_backup 的副本",
            File(second.root, ".last_backup").exists().not())
    }
}
