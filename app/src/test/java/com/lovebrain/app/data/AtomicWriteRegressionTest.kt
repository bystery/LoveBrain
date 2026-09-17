package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * P1-01 回归测试：原子写不回退到 copy 覆盖。
 * 验证正常写入路径完整可用、内容正确。
 * 在 Windows 上 renameTo 通常成功——这里验证正常路径不回归。
 */
class AtomicWriteRegressionTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: File
    private lateinit var appScope: CoroutineScope

    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = mockk<SecurePrefs>(relaxed = true)
        var activeName = ""
        every { prefs.activeKbName } answers { activeName }
        every { prefs.activeKbName = any<String>() } answers { activeName = arg(0) }
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    private fun kbDir(name: String = "kb"): File = File(root, name).apply { mkdirs() }

    private fun writeKbJson(dir: File) {
        val kb = KnowledgeBase(
            name = dir.name,
            displayName = dir.name,
            updatedAt = "2026-01-01T00:00:00+08:00",
            stage = "待确定",
            turnCount = 0,
            topicCount = 0,
            active = true
        )
        File(dir, "kb.json").writeText(
            Json.encodeToString(KnowledgeBase.serializer(), kb), Charsets.UTF_8
        )
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kr_atomic").toFile()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun `writeFile creates file with correct content`() = runTest {
        val repo = newRepo()
        val dir = kbDir("test1")
        writeKbJson(dir)

        repo.writeFile("test1", "understand/me.md", "hello world")

        val content = withContext(Dispatchers.IO) {
            File(dir, "understand/me.md").readText()
        }
        assertEquals("hello world", content)
    }

    @Test
    fun `writeFile overwrites existing content atomically`() = runTest {
        val repo = newRepo()
        val dir = kbDir("test2")
        writeKbJson(dir)
        File(dir, "understand").mkdirs()
        File(dir, "understand/me.md").writeText("old content")

        repo.writeFile("test2", "understand/me.md", "new content")

        val content = withContext(Dispatchers.IO) {
            File(dir, "understand/me.md").readText()
        }
        assertEquals("new content", content)
    }

    @Test
    fun `writeFile does not leave temp files after success`() = runTest {
        val repo = newRepo()
        val dir = kbDir("test3")
        writeKbJson(dir)

        repo.writeFile("test3", "understand/me.md", "content")

        val tempFiles = dir.walkTopDown().filter { it.name.startsWith(".") && it.name.endsWith(".tmp") }.toList()
        assertTrue("temp files should be cleaned up: $tempFiles", tempFiles.isEmpty())
    }

    @Test
    fun `appendFile preserves existing content and appends`() = runTest {
        val repo = newRepo()
        val dir = kbDir("test4")
        writeKbJson(dir)

        repo.appendFile("test4", "memory/raw_chat.md", "line1\n")
        repo.appendFile("test4", "memory/raw_chat.md", "line2\n")

        val content = withContext(Dispatchers.IO) {
            File(dir, "memory/raw_chat.md").readText()
        }
        assertEquals("line1\nline2\n", content)
    }

    @Test
    fun `writeFileWithVersion detects concurrent modification`() = runTest {
        val repo = newRepo()
        val dir = kbDir("test5")
        writeKbJson(dir)

        val (originalContent, originalVersion) = repo.readFileWithVersion("test5", "understand/me.md")
        assertEquals("", originalContent)

        // Concurrent write changes the version
        repo.writeFile("test5", "understand/me.md", "concurrent write")

        // Try to write with stale version → should fail
        val result = repo.writeFileWithVersion("test5", "understand/me.md", "my write", originalVersion)
        assertNull("write should be rejected due to version conflict", result)
    }

    @Test
    fun `writeFileWithVersion succeeds when version matches`() = runTest {
        val repo = newRepo()
        val dir = kbDir("test6")
        writeKbJson(dir)

        val (_, version) = repo.readFileWithVersion("test6", "understand/me.md")
        val newVersion = repo.writeFileWithVersion("test6", "understand/me.md", "new content", version)
        assertNotNull("write should succeed when version matches", newVersion)

        val content = withContext(Dispatchers.IO) {
            File(dir, "understand/me.md").readText()
        }
        assertEquals("new content", content)
    }
}
