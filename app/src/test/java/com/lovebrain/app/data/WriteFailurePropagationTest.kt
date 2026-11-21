package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.domain.RecentRoundStore
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * P0-1 的验收格：**写失败不许报成功**，也不许回一个"新版本号"。
 *
 * 审计点名的三处旧形状：
 * - `KnowledgeRepoIO.writeFileUnlocked` / `appendFileUnlocked` 被拒时直接 `return`（Unit），
 *   调用方根本分不出"写了"与"没写"；
 * - `KnowledgeRevisionCheckedWriteService` 三处写完就 `return true`；
 * - `KnowledgeDocumentStore.writeWithVersion` 写被拒仍回新 SHA。
 *
 * 两半证据：假存储（精确控制写结果）钉形状；真仓库 + v4 只读库钉行为（一个字节都没动）。
 */
class WriteFailurePropagationTest {

    // ═══════════ 一半：假存储，写被拒时谁都不许说成功 ═══════════

    private class RefusingDocumentStorage(root: File) : DocumentStorage {
        override val root: File = root
        val notes = mutableListOf<String>()
        override fun note(message: String) {
            notes += message
        }

        override fun kbExists(kbName: String): Boolean = true

        override fun writeUnlocked(kbName: String, relativePath: String, content: String): Boolean = false
    }

    private class RefusingRevisionStorage : RevisionCheckStorage {
        val notes = mutableListOf<String>()
        override fun note(message: String) {
            notes += message
        }

        override fun kbExists(kbName: String): Boolean = true
        override fun revisionOf(kbName: String): Int = 7
        override fun appendUnlocked(kbName: String, relativePath: String, content: String): Boolean = false
        override fun writeUnlocked(kbName: String, relativePath: String, content: String): Boolean = false
        override fun writeVectorUnlocked(kbName: String, values: Map<String, Int>): Boolean = false
    }

    @Test
    fun `versioned write returns no new version when the write chain refuses`() {
        val root = Files.createTempDirectory("doc_refuse").toFile()
        try {
            val kbDir = File(root, "kb").apply { mkdirs() }
            File(kbDir, "kb.json").writeText(
                Json.encodeToString(
                    KnowledgeBase.serializer(),
                    KnowledgeBase(name = "kb", displayName = "kb")
                ),
                Charsets.UTF_8
            )
            val storage = RefusingDocumentStorage(root)
            val store = KnowledgeDocumentStore(storage)
            val expected = store.readWithVersion("kb", "moment/recent.md").second

            val result = store.writeWithVersion("kb", "moment/recent.md", "新内容", expected)

            assertNull("写被拒时不许回新版本号（旧实现回了，调用方以为成功）", result)
            assertTrue(
                "拒写必须留痕：${storage.notes}",
                storage.notes.any { it.contains("write refused or failed") }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `revision checked writes report refusal instead of true`() {
        val storage = RefusingRevisionStorage()
        val svc = KnowledgeRevisionCheckedWriteService(storage)

        assertFalse("追加写被拒不许报 true", svc.appendFileWithRevisionCheck("kb", "memory/lessons.md", "x", 7))
        assertFalse("覆盖写被拒不许报 true", svc.writeFileWithRevisionCheck("kb", "kb.json", "x", 7))
        assertFalse("向量写被拒不许报 true", svc.writeVectorWithRevisionCheck("kb", mapOf("intimacy" to 90), 7))
        assertEquals("三次失败各留一条痕迹", 3, storage.notes.size)
    }

    // ═══════════ 另一半：真仓库 + v4 库（磁盘真相即只读）═══════════

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    private val futureSchema = KnowledgeSchemaVersion.CURRENT + 1

    @Before
    fun setUp() {
        root = Files.createTempDirectory("write_failure_gate").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun seedFutureKb() {
        val dir = File(root, "kb").apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = "kb", displayName = "未来的她", topicCount = 0)
            ),
            Charsets.UTF_8
        )
        File(dir, ".schema_version").writeText(futureSchema.toString(), Charsets.UTF_8)
        File(dir, "moment").mkdirs()
        File(dir, "moment/recent.md").writeText("未来版本写下的此刻\n", Charsets.UTF_8)
        File(dir, "understand").mkdirs()
        File(dir, "understand/warmth.md").writeText("亲密度：61\n", Charsets.UTF_8)
    }

    private fun openReadOnly() {
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
        runBlocking { repo.migrateIfNeeded("kb") }
        assertTrue("前提：v4 库必须被判成只读", repo.isSchemaReadOnly("kb"))
    }

    /** 不等迁移打开也要挡：只读判定读磁盘真相，不是只信进程内集合 */
    @Test
    fun `read-only is decided by the schema file even before any migration ran`() {
        seedFutureKb()
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
        // 刻意不调用 migrateIfNeeded：旧实现此时 isReadOnly=false，写入会真的落盘
        assertTrue("schema 文件已经写着 v$futureSchema，就不许当成可写", repo.isSchemaReadOnly("kb"))
    }

    @Test
    fun `read-only library refuses the four claiming writes without touching a byte`() {
        seedFutureKb()
        openReadOnly()
        val before = File(root, "kb").walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(File(root, "kb")).path to it.readText(Charsets.UTF_8) }

        val version = runBlocking { repo.readFileWithVersion("kb", "moment/recent.md") }.second
        val versioned = runBlocking {
            repo.writeFileWithVersion("kb", "moment/recent.md", "v3 想覆盖", version)
        }
        val revisionChecked = runBlocking {
            repo.writeFileWithRevisionCheck("kb", "moment/recent.md", "v3 想覆盖", 0)
        }
        val revisionAppended = runBlocking {
            repo.appendFileWithRevisionCheck("kb", "moment/recent.md", "\n追加", 0)
        }
        val vectorWritten = runBlocking {
            repo.writeVectorWithRevisionCheck("kb", mapOf("intimacy" to 99), 0)
        }

        assertNull("写被拒不许回新 SHA", versioned)
        assertFalse("带 revision 的覆盖写不许报成功", revisionChecked)
        assertFalse("带 revision 的追加写不许报成功", revisionAppended)
        assertFalse("向量写不许报成功", vectorWritten)

        val after = File(root, "kb").walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(File(root, "kb")).path to it.readText(Charsets.UTF_8) }
        assertEquals("只读库上这四条写路径一个字节都不许动", before, after)
    }

    /**
     * 公开的 `writeFile`/`appendFile` 在端口上交回 Boolean（false = 一个字节都没落），
     * 失败从写口原样往上传。这一格钉消费方那一跳：
     * 只读库上这两笔一个字节都没落，而依赖它们的 `RecentRoundStore.writeRecent`
     * 必须交回 false——旧形状它只发号不核对，把没写成的这一轮报成成功，
     * 上层于是照常记"已记入"、水位照常推进，这一轮的对话就永远不会再落盘。
     */
    @Test
    fun `a refused public write is reported as failure by the reading caller`() {
        seedFutureKb()
        openReadOnly()
        val recentBefore = File(root, "kb/moment/recent.md").readText(Charsets.UTF_8)

        runBlocking {
            repo.writeFile("kb", "moment/recent.md", "- [2026-09-24 10:00]\n她：v3 想写的一轮\n")
            repo.appendFile("kb", "memory/should_not_exist.log", "追加\n")
        }

        assertEquals(
            "只读库上公开写口一个字节都不许动",
            recentBefore, File(root, "kb/moment/recent.md").readText(Charsets.UTF_8)
        )
        assertFalse("被拒的追加不许造出新文件", File(root, "kb/memory/should_not_exist.log").exists())

        val landed = runBlocking {
            RecentRoundStore(repo).writeRecent(
                "kb",
                "- [2026-09-24 10:00]\n<!-- round:msgIds:new-0 -->\n她：v3 想写的一轮\n",
                "new-0"
            )
        }

        assertFalse("写口被拒时消费方必须报失败，不许把这次拒写当成功", landed)
        assertEquals(
            "失败之后磁盘仍是未来版本那篇",
            recentBefore, File(root, "kb/moment/recent.md").readText(Charsets.UTF_8)
        )
    }

    @Test
    fun `revision checked write on a writable library really lands`() {
        seedFutureKb()
        // 同一本库改成当前 schema：门必须放行（否则上面那格只是"全拒绝"的假安全）
        File(root, "kb/.schema_version").writeText(KnowledgeSchemaVersion.CURRENT.toString(), Charsets.UTF_8)
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
        runBlocking { repo.migrateIfNeeded("kb") }
        assertFalse("前提：改成当前版本后不再只读", repo.isSchemaReadOnly("kb"))

        val ok = runBlocking {
            repo.appendFileWithRevisionCheck("kb", "moment/recent.md", "\n这一行要真的进去\n", 0)
        }
        assertTrue("可写库上带 revision 的追加必须落盘并报 true", ok)
        assertTrue(
            "追加内容必须真的在文件里",
            File(root, "kb/moment/recent.md").readText(Charsets.UTF_8).contains("这一行要真的进去")
        )
    }
}
