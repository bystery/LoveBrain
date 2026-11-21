package com.lovebrain.app.data

import android.content.Context
import android.util.Log
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 迁移器写口的**守门语义**（批次四：`KbStorageAccess` 上那道 `atomicWrite(File, …)` 换成
 * `atomicWriteAt(kbName, relativePath, content)`，实现体复用仓库已有的 `safeKbFile` +
 * `writeFileCheckedUnlocked`，没有新写第二条 `atomicWriteText`，也没有新造第二道判定）。
 *
 * 三格各自钉一件事：
 * - [an escaping path is refused by the new guarded entry and nothing lands outside the library]：
 *   **等价搬运**那一侧——迁移器写的路径本来全是写死的库内路径，越界从来没有合法来源；
 *   这一格钉的是**新入口本身**不把「交出一个 File 就能写到任何地方」这扇门留着。
 * - [a legitimate relative path still writes through the guarded entry]：防「为了安全把门全关死」的假安全。
 * - [a legacy library whose name the guard refuses is left unwritten but says so out loud]：
 *   ⚠ **这一条是行为变化**，不是等价搬运。变化是什么、为什么接受，全写在那一格的注释里。
 *
 * 注：`atomicWriteAt` 是**锁内**原语（`writeFileCheckedUnlocked` 不抢锁——Mutex 非重入，
 * 锁内再抢就是永久挂起）。生产路径上仓库一直在外层持 `fileMutex`（`migrateIfNeeded` 整段套在锁里）；
 * 测试里也必须经 [atomicWriteAtLocked] 显式持同一把锁再调，不该被读成「可以从外面裸调」：
 * 仓库构造时就会在 appScope 上启动 init 协程（启动恢复 + 备份，持同一把锁）——它是**真实的
 * 并发写者**，「单线程顺序调用不构成交错」的前提已不成立。不持锁直调时，启动恢复会在写入的
 * `.tmp` 存在窗口里把在飞的临时文件当中断残留 rename 走（POSIX 对打开中的文件 rename 照样
 * 成功，CI 冷 JVM 慢盘正好落进这个窗口；Windows 本地快机赶不上），写核的原子 move 随即拿到
 * NoSuchFileException、回退路 renameTo 失败，IOException 便从合法路径逃逸——CI/Linux 红、
 * 本地绿。过锁不改判任何一格：每一格判的还是同一个返回值与同一棵盘上树。
 */
class KnowledgeMigratorWriteGuardTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kb_write_guard").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    /**
     * 锁内原语的测试入口：直调必须过仓库那把锁（理由见类注释）。
     * 同一份 `atomicWriteAt`、同一份实参——这里只补上生产调用方一直持有的那把锁。
     */
    private fun atomicWriteAtLocked(kbName: String, relativePath: String, content: String): Boolean =
        runBlocking {
            repo.fileMutex.withLock { repo.migratorStorage.atomicWriteAt(kbName, relativePath, content) }
        }

    private fun relativeFiles(dir: File): List<String> =
        dir.walkTopDown().filter { it.isFile && it != dir }
            .map { it.relativeTo(dir).path.replace(File.separatorChar, '/') }
            .toList().sorted()

    /** 建一本真库（守门的读判定与只读判定都按真库的形状走） */
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

    // ═══════════ ① 越界路径经新入口写不出去，而且返回 false ═══════════

    @Test
    fun `an escaping path is refused by the new guarded entry and nothing lands outside the library`() {
        seedKb()
        // 逃逸目标名带一次运行的唯一后缀：万一某次反证注入真的把文件写到共享临时目录里，
        // 那堆残骸不会让下一次跑「假红」——这一格判的是「这次有没有写出去」。
        val escape = "lb_guard_escape_" + System.nanoTime() + ".md"
        // 每一形都是以前「端口收 File」时能真的走到库外的写法：POSIX 的 ..、Windows 的反斜杠、
        // 绝对路径、盘符。判据两条：返回 false，且库外库内都不许多出一个文件。
        for (
            bad in listOf(
                "../$escape",
                "./../$escape",
                "moment/../../$escape",
                "understand/../../$escape",
                "/absolute/$escape",
                "C:\\$escape",
                "..\\$escape"
            )
        ) {
            assertFalse(
                "越界相对路径竟然写成功了：[$bad]",
                atomicWriteAtLocked("kb", bad, "越界内容")
            )
        }
        assertFalse(
            "库外面落了文件：" + File(root, escape).path,
            File(root, escape).exists()
        )
        assertFalse(
            "守门退而求其次，把越界路径当成库内文件名写了——库里多出来的东西：" + relativeFiles(kbDir),
            kbDir.walkTopDown().any { it.isFile && it.name.contains("lb_guard_escape") }
        )
        assertEquals(
            "库内只许有那本种子库自己：" + relativeFiles(kbDir),
            listOf("kb.json"), relativeFiles(kbDir)
        )

        // 库名这一侧同理：kbName 里塞 `..` 或分隔符，也不许把 `.schema_version` 写到别处
        val sibling = "lb_guard_escape_dir_" + System.nanoTime()
        for (badName in listOf("../$sibling", "kb/../$sibling", "a/b", "a\\b", "")) {
            assertFalse(
                "非法库名竟然写成功了：[$badName]",
                atomicWriteAtLocked(badName, ".schema_version", "3")
            )
        }
        assertFalse("`..` 库名把兄弟目录建出来了", File(root, sibling).exists())
        assertFalse("`..` 库名写到 knowledge/ 外面去了", File(root.parentFile, sibling).exists())
    }

    // ═══════════ 合法路径仍然写得动（否则上面那格是假安全）═══════════

    @Test
    fun `a legitimate relative path still writes through the guarded entry`() {
        seedKb()
        assertTrue(
            "合法路径被自己的守门挡下——那上面那一格拒绝就成了假安全",
            atomicWriteAtLocked("kb", "moment/scene.md", "此刻的场景")
        )
        val scene = File(kbDir, "moment/scene.md")
        assertTrue("返回 true 却没落盘", scene.isFile)
        assertEquals("此刻的场景", scene.readText(Charsets.UTF_8))
        // 迁移器真的在写点开头的 marker：隐藏名不许被守门顺手挡掉
        assertTrue(atomicWriteAtLocked("kb", ".schema_version", "3"))
        assertEquals("3", File(kbDir, ".schema_version").readText(Charsets.UTF_8))
        assertEquals(listOf(".schema_version", "kb.json", "moment/scene.md"), relativeFiles(kbDir))
    }

    // ═══════════ ② 库名非法的遗留目录：拒了要说、不许崩、不许静默 ═══════════

    /**
     * ⚠ 这一格钉的是**行为变化**。三条说清：
     *
     * ① **改之前**实测是什么样：迁移器拿 `File(io.root, kbName)` 拼出目录，
     *    再把 `File(dir, ".schema_version")` 整个交给端口——守门根本不在路上，
     *    所以一本名字超过 `KB_NAME_MAX_LENGTH`（100）的遗留目录**照样被写出**
     *    `.schema_version` 与那一整批补齐文件。这一条不是靠推断写的：反证注入
     *    （把 `atomicWriteAt` 的实现体换回「自己拼 File 再调 `atomicWriteText`」）
     *    让这一格红在「盘上多出一个文件」那句断言上。
     * ② **改之后**是什么样：同一个目录走迁移，每一格写都被守门拒掉，正文一个字节都不许多。
     * ③ 为什么这不算静默跳过：每次拒绝都要落一条错误级日志，写明是哪一本库的哪一格
     *    （旧代码在 `atomicWriteText` 返回 false 时同样记 L.e，这一条语义是搬过来的）。
     *
     * 为什么接受这个变化：那种库每一次读都已经被 `safeKbFile` 判非法、拿回空串，
     * 「补齐了文件」不让用户多读到任何东西，只是盘上多一套没人读的字节。
     * 同族判定在批次二（`create` 拒长名）与批次三（`ensureKbFilesCompleteUnlocked` 不再补长名库）
     * 已各拍过一次，这次是把第三条入口对齐到同一把尺，不是新发明的宽严。
     *
     * 曾经交代之后的**下一步**（就在本轮做掉了）：
     * `writeSchemaVersion` 以前无条件清 legacy marker（`File(dir, markerName).delete()` 不经端口），
     * 于是名字非法的库会「marker 被清、版本号文件没落」——新状态没写下、旧证据先销毁。
     * 现在改成**落成才清**：`atomicWriteAt` 返回 false 时一格都没写，marker 原样留着。
     * 合法库那一条永远写得动，所以这条改动只作用在"本来就被拒"的那一类上；
     * 反面证据钉在 `a legitimate library still loses its legacy markers once the version lands`，
     * 防的是有人把"保留证据"做过头成"永远不清"。
     */
    @Test
    fun `a legacy library whose name the guard refuses is left unwritten but says so out loud`() {
        val errs = mutableListOf<String>()
        val warns = mutableListOf<String>()
        mockkStatic(Log::class)
        try {
            // L.e 走三参重载且第三个实参恒为 null，L.w 走两参重载；两条都接住，
            // 别把 MockKException 当成断言失败报出来（那是仪器错报）。
            every { Log.e(any<String>(), capture(errs), isNull()) } returns 0
            every { Log.w(any<String>(), capture(warns)) } returns 0

            val longName = "b".repeat(101)
            val dir = File(root, longName).apply { mkdirs() }
            File(dir, "kb.json").writeText(
                Json.encodeToString(
                    KnowledgeBase.serializer(),
                    KnowledgeBase(name = longName, displayName = "名字超长的旧库")
                ),
                Charsets.UTF_8
            )
            // 一本货真价实的 v2 旧库：global 目录 + legacy marker，迁移判定上它确实「该被写」
            File(dir, "global").mkdirs()
            File(dir, "global/me.md").writeText("旧画像", Charsets.UTF_8)
            File(dir, ".migrated_v2").writeText("", Charsets.UTF_8)

            val thrown = runCatching { runBlocking { repo.migrateIfNeeded(longName) } }.exceptionOrNull()
            assertNull("守门拒绝不许抛穿到调用方（那是崩，不是没写成），实到 $thrown", thrown)

            // 正文一格都不许多：`.schema_version` 与那批补齐文件都不许出现；
            // 而 legacy marker `.migrated_v2` **必须还在**——版本号没落成就不许销毁旧证据。
            assertEquals(
                "名字非法的库：迁移之后盘上只许剩原有正文与原有 marker",
                listOf(".migrated_v2", "global/me.md", "kb.json"),
                relativeFiles(dir)
            )

            // 而且要说得出是哪一本库、哪一格没写成——不是不出声的 return false
            val refused = errs.filter { it.contains(REFUSED) }
            assertTrue(
                "每一次拒绝都得留痕，实到错误级日志 $errs 条",
                refused.isNotEmpty()
            )
            assertTrue(
                "痕迹要点名到格子（库名/相对路径），实到 $refused",
                refused.any { it.contains(".schema_version") }
            )
            assertTrue(
                "痕迹要落在被拒的那本库上，实到 $refused",
                refused.all { it.contains(longName) }
            )
        } finally {
            unmockkStatic(Log::class)
        }
    }

    /**
     * 反面证据：把 `writeSchemaVersion` 改成"版本号落成了才清 marker"之后，
     * **合法库**那条路径必须照旧清掉 marker 并写下 `.schema_version`——
     * 不然"保留旧证据"就做过头成了"永远不清"，每次启动都得重新靠 marker 猜版本
     * （那正是这段代码当初写下来的理由）。
     */
    @Test
    fun `a legitimate library still loses its legacy markers once the version lands`() {
        val name = "kb"
        seedKb(name)
        val dir = File(root, name)
        File(dir, "global").mkdirs()
        File(dir, "global/me.md").writeText("旧画像", Charsets.UTF_8)
        File(dir, ".migrated_v2").writeText("", Charsets.UTF_8)

        runBlocking { repo.migrateIfNeeded(name) }

        assertTrue(
            "前提：合法库的版本号文件必须真落下去",
            File(dir, ".schema_version").isFile
        )
        assertTrue(
            "合法库：版本号落成之后 legacy marker 照旧要清掉，实到 ${relativeFiles(dir)}",
            relativeFiles(dir).none { it == ".migrated_v2" }
        )
    }

    private companion object {
        /** 与 [KnowledgeRepository] 那条拒绝日志对齐的关键字；改措辞要连这格一起改 */
        const val REFUSED = "migration write refused"
    }
}
