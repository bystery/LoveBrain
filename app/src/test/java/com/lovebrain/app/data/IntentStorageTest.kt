package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * F07: 持续意图存储测试。
 *
 * 验证点：
 * 1. 默认读取返回 IntentConfig()（enabled=false, text="", revision=0）
 * 2. 保存后读取正确
 * 3. 每次保存 revision 递增
 * 4. 关闭后 text 保留，enabled=false
 * 5. 重启后（新 repo 实例）读取仍有数据
 */
class IntentStorageTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private var activeKbName: String = "testkb"

    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.activeKbName } answers { activeKbName }
        every { prefs.activeKbName = any<String>() } answers { activeKbName = arg(0) }
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    private fun File.sub(relativePath: String): File = File(this, relativePath).apply {
        parentFile?.mkdirs()
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("intent_storage").toFile()
        val dir = File(root, "testkb").apply { mkdirs() }
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()
        File(dir, "understand").mkdirs()
        File(dir, "kb.json").writeText(json.encodeToString(KnowledgeBase.serializer(),
            KnowledgeBase(name = "testkb", displayName = "Test", updatedAt = "2026-09-16T09:00:00+08:00", active = true)))
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    // ════════════════════════════════════════════════════════════════
    // Test 1: 默认读取返回空配置
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `default_read_returns_empty_config`() = runBlocking {
        val repo = newRepo()
        val intent = repo.readIntent("testkb")

        assertEquals("默认 text 应为空", "", intent.text)
        assertFalse("默认 enabled 应为 false", intent.enabled)
        assertEquals("默认 revision 应为 0", 0, intent.revision)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 2: 保存后读取正确
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `save_then_read_correct`() = runBlocking {
        val repo = newRepo()
        repo.saveIntent("testkb", "先恢复轻松交流", true)

        val intent = repo.readIntent("testkb")
        assertEquals("text 应匹配", "先恢复轻松交流", intent.text)
        assertTrue("enabled 应为 true", intent.enabled)
        assertEquals("revision 应为 1", 1, intent.revision)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 3: 每次保存 revision 递增
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `revision_increments_on_each_save`() = runBlocking {
        val repo = newRepo()

        val r1 = repo.saveIntent("testkb", "意图1", true)
        assertEquals("第一次保存 revision=1", 1, r1.revision)

        val r2 = repo.saveIntent("testkb", "意图2", true)
        assertEquals("第二次保存 revision=2", 2, r2.revision)

        val r3 = repo.saveIntent("testkb", "意图3", false)
        assertEquals("第三次保存 revision=3", 3, r3.revision)

        val intent = repo.readIntent("testkb")
        assertEquals("最终 revision 应为 3", 3, intent.revision)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 4: 关闭后 text 保留，enabled=false
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `close_keeps_text_disables_enabled`() = runBlocking {
        val repo = newRepo()
        repo.saveIntent("testkb", "先恢复轻松交流", true)
        repo.saveIntent("testkb", "先恢复轻松交流", false)

        val intent = repo.readIntent("testkb")
        assertEquals("关闭后 text 应保留", "先恢复轻松交流", intent.text)
        assertFalse("关闭后 enabled 应为 false", intent.enabled)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 5: 重启后读取仍有数据
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `persisted_across_restart`() = runBlocking {
        val repo1 = newRepo()
        repo1.saveIntent("testkb", "持久化意图", true)

        // 模拟重启：创建新 repo 实例
        appScope.cancel()
        val repo2 = newRepo()
        val intent = repo2.readIntent("testkb")

        assertEquals("重启后 text 应保留", "持久化意图", intent.text)
        assertTrue("重启后 enabled 应保留", intent.enabled)
        assertEquals("重启后 revision 应保留", 1, intent.revision)
    }
}
