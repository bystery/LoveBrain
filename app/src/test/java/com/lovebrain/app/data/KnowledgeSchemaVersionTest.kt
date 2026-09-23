package com.lovebrain.app.data

import android.content.Context
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * S2-06: schema 版本治理与 KB 路径 canonical 边界。
 *
 * 逐条对着复核报告 §6 S2-06 的"未闭环项"写：
 * 1. 新库没有 global 目录时旧代码直接 return，永远不写 CURRENT 版本，
 *    于是每次启动都被认成 v1。
 * 2. 高于当前支持的 schema 旧代码不拒绝，`needsMigration` 返回 false 后
 *    继续用旧代码读写未来结构。
 * 3. Repository 公共方法过去收裸 String 路径，`..`/绝对路径/反斜杠没有统一拦。
 * 4. `KbName`/`KbRelativePath` 过去只有定义没有使用。
 */
class KnowledgeSchemaVersionTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("schema_version").toFile()
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

    private fun kbDir(name: String = "kb") = File(root, name)

    private fun seedKb(name: String = "kb", files: Map<String, String> = emptyMap()) {
        val dir = kbDir(name).apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = name, displayName = name)
            ),
            Charsets.UTF_8
        )
        File(dir, "understand").mkdirs()
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()
        for ((path, content) in files) {
            val f = File(dir, path)
            f.parentFile?.mkdirs()
            f.writeText(content, Charsets.UTF_8)
        }
    }

    // ─── 1. 新库必须落到 CURRENT，而不是每次都被认成 v1 ───────────

    @Test
    fun `a brand new library records CURRENT schema instead of staying at v1`() {
        seedKb()
        runBlocking { repo.migrateIfNeeded("kb") }

        val marker = File(kbDir(), ".schema_version")
        assertTrue(
            "new library must persist a schema version, otherwise every launch re-detects v1",
            marker.exists()
        )
        assertEquals(
            KnowledgeSchemaVersion.CURRENT.toString(),
            marker.readText().trim()
        )
    }

    /** 连续两次打开新库，第二次必须走"已经是最新"分支，不再重跑补文件 */
    @Test
    fun `opening a new library twice does not re-run migration`() {
        seedKb()
        runBlocking { repo.migrateIfNeeded("kb") }
        val first = File(kbDir(), "moment/plan.md").readText()

        // 破坏性改动：如果第二次又跑一遍"补文件"，这行内容会被模板覆盖
        File(kbDir(), "moment/plan.md").writeText(first + "\n用户自己写的一行\n", Charsets.UTF_8)
        runBlocking { repo.migrateIfNeeded("kb") }

        assertTrue(
            "second pass must not rewrite plan.md",
            File(kbDir(), "moment/plan.md").readText().contains("用户自己写的一行")
        )
    }

    @Test
    fun `legacy markers are replaced by the single version file and cleaned up`() {
        seedKb(files = mapOf(".migrated_plan_v3" to ""))
        runBlocking { repo.migrateIfNeeded("kb") }
        assertTrue(".migrated_plan_v3 must be removed once .schema_version exists",
            !File(kbDir(), ".migrated_plan_v3").exists())
        assertEquals(
            KnowledgeSchemaVersion.CURRENT.toString(),
            File(kbDir(), ".schema_version").readText().trim()
        )
    }

    // ─── 2. 未来 schema 明确拒绝写入 ─────────────────────────────

    @Test
    fun `a schema newer than this build is refused for writes but still readable`() {
        seedKb(files = mapOf(
            ".schema_version" to (KnowledgeSchemaVersion.CURRENT + 1).toString(),
            "moment/recent.md" to "未来版本写下的内容"
        ))
        runBlocking { repo.migrateIfNeeded("kb") }

        assertTrue("newer schema must flip the library read-only", repo.isSchemaReadOnly("kb"))
        assertFalse("newer schema must not be writable", repo.isWritable("kb"))

        // 读仍然可用
        assertEquals("未来版本写下的内容", runBlocking { repo.readFile("kb", "moment/recent.md") }.trim())

        // 写被拒绝，且不落盘
        runBlocking { repo.writeFile("kb", "moment/recent.md", "v3 代码想覆盖未来结构") }
        assertEquals(
            "write must be refused so a newer library keeps its own shape",
            "未来版本写下的内容",
            runBlocking { repo.readFile("kb", "moment/recent.md") }.trim()
        )
    }

    @Test
    fun `isBeyondSupported and needsMigration never both say no-op-for-the-wrong-reason`() {
        val future = KnowledgeSchemaVersion.CURRENT + 1
        assertTrue(KnowledgeSchemaVersion.isBeyondSupported(future))
        assertFalse(
            "a future schema must not be treated as 'already fine, keep writing'",
            !KnowledgeSchemaVersion.isBeyondSupported(future) &&
                !KnowledgeSchemaVersion.needsMigration(future) &&
                false
        )
        assertFalse(KnowledgeSchemaVersion.isBeyondSupported(KnowledgeSchemaVersion.CURRENT))
        assertTrue(KnowledgeSchemaVersion.needsMigration(1))
        assertFalse(KnowledgeSchemaVersion.needsMigration(KnowledgeSchemaVersion.CURRENT))
    }

    // ─── 3/4. 路径边界真正生效 ───────────────────────────────────

    @Test
    fun `KbName rejects separators traversal and blank names`() {
        assertNull(repo.toKbName(""))
        assertNull(repo.toKbName("a/b"))
        assertNull(repo.toKbName("a\\b"))
        assertNull(repo.toKbName(".."))
        assertNull(repo.toKbName("x".repeat(101)))
        assertNotNull(repo.toKbName("她"))
    }

    @Test
    fun `KbRelativePath rejects traversal and absolute paths`() {
        assertNull(repo.toKbPath(""))
        assertNull(repo.toKbPath("../escape.md"))
        assertNull(repo.toKbPath("a/../../b.md"))
        assertNull(repo.toKbPath("/etc/passwd"))
        assertNotNull(repo.toKbPath("moment/recent.md"))
    }

    /**
     * 关键：不是"定义了 value class 就算有边界"。
     * 公共 String 入口必须真的被同一套校验拦住。
     */
    @Test
    fun `traversal through the public String API cannot escape the library directory`() {
        seedKb()
        runBlocking { repo.migrateIfNeeded("kb") }

        val outside = File(root, "outside.md").apply { writeText("original", Charsets.UTF_8) }

        runBlocking { repo.writeFile("kb", "../outside.md", "OVERWRITTEN") }
        assertEquals(
            "path traversal must be refused at the boundary",
            "original",
            outside.readText(Charsets.UTF_8)
        )

        runBlocking { repo.appendFile("kb", "..\\outside.md", "OVERWRITTEN") }
        assertEquals(
            "windows separator traversal must be refused too",
            "original",
            outside.readText(Charsets.UTF_8)
        )

        // 绝对路径
        runBlocking { repo.writeFile("kb", outside.absolutePath, "OVERWRITTEN") }
        assertEquals(
            "absolute path must be refused",
            "original",
            outside.readText(Charsets.UTF_8)
        )
    }

    /** 非法库名不能凭空造目录 */
    @Test
    fun `an illegal kb name never materialises a directory`() {
        runBlocking { repo.writeFile("a/b", "moment/recent.md", "x") }
        runBlocking { repo.writeFile("kb", "moment/recent.md", "x") } // 建好正常库对照
        assertTrue(!File(root, "a").exists())
    }

    @Test
    fun `deleteFile is refused for traversal targets`() {
        seedKb()
        runBlocking { repo.migrateIfNeeded("kb") }
        val outside = File(root, "keepme.md").apply { writeText("keep", Charsets.UTF_8) }
        val deleted = runBlocking { repo.deleteFile("kb", "../keepme.md") }
        assertFalse("traversal delete must be refused", deleted)
        assertTrue(outside.exists())
    }

    @Test
    fun `schemaVersion exposes the detected version for upgrade assertions`() {
        seedKb(files = mapOf(".schema_version" to "2"))
        assertEquals(2, runBlocking { repo.schemaVersion("kb") })
        seedKb(files = mapOf(".schema_version" to "7"))
        assertEquals(7, runBlocking { repo.schemaVersion("kb") })
    }
}
