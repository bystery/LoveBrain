package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.KnowledgeBase
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
 * F09: 记忆纠正存储测试。
 *
 * 验证点：
 * 1. 空库读取返回空 map
 * 2. 保存后读取正确
 * 3. revision 每次保存递增
 * 4. 撤销后记录删除
 * 5. 重启后记录仍有效
 * 6. 多条纠正共存
 */
class MemoryCorrectionTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true }
    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private var activeKbName: String = "testkb"

    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = mockk<SecurePrefs>(relaxed = true)
        io.mockk.every { prefs.activeKbName } answers { activeKbName }
        io.mockk.every { prefs.activeKbName = any<String>() } answers { activeKbName = arg(0) }
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("correction_test").toFile()
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

    // ═══════════ Test 1: 空库读取返回空 map ═══════════

    @Test
    fun `empty_kb_returns_empty_corrections`() = runBlocking {
        val repo = newRepo()
        val corrections = repo.readCorrections("testkb")
        assertTrue("空库应返回空纠正记录", corrections.isEmpty())
        assertEquals("空库 revision 应为 0", 0, repo.getCorrectionsRevision("testkb"))
    }

    // ═══════════ Test 2: 保存后读取正确 ═══════════

    @Test
    fun `save_then_read_correction`() = runBlocking {
        val repo = newRepo()
        repo.saveCorrection("testkb", "PROFILE:understand/me.md:abc12345", CorrectionAction.WRONG, "正确的内容")

        val corrections = repo.readCorrections("testkb")
        assertEquals("应有1条纠正", 1, corrections.size)
        val c = corrections.values.first()
        assertEquals(CorrectionAction.WRONG, c.action)
        assertEquals("正确的内容", c.replacementText)
        assertEquals(1, c.revision)
    }

    // ═══════════ Test 3: revision 每次保存递增 ═══════════

    @Test
    fun `revision_increments_on_each_save`() = runBlocking {
        val repo = newRepo()

        repo.saveCorrection("testkb", "ref1", CorrectionAction.WRONG)
        assertEquals(1, repo.getCorrectionsRevision("testkb"))

        repo.saveCorrection("testkb", "ref2", CorrectionAction.MUTED)
        assertEquals(2, repo.getCorrectionsRevision("testkb"))

        repo.saveCorrection("testkb", "ref3", CorrectionAction.FINISHED)
        assertEquals(3, repo.getCorrectionsRevision("testkb"))
    }

    // ═══════════ Test 4: 撤销后记录删除 ═══════════

    @Test
    fun `undo_removes_correction`() = runBlocking {
        val repo = newRepo()
        repo.saveCorrection("testkb", "ref1", CorrectionAction.WRONG, "补正")
        repo.saveCorrection("testkb", "ref2", CorrectionAction.MUTED)

        assertEquals(2, repo.readCorrections("testkb").size)

        val undone = repo.undoCorrection("testkb", "ref1")
        assertTrue("撤销应成功", undone)

        val remaining = repo.readCorrections("testkb")
        assertEquals("撤销后应剩1条", 1, remaining.size)
        assertFalse("ref1 应已删除", remaining.containsKey("ref1"))
        assertTrue("ref2 应保留", remaining.containsKey("ref2"))
    }

    // ═══════════ Test 5: 重启后记录仍有效 ═══════════

    @Test
    fun `corrections_persist_across_restart`() = runBlocking {
        val repo1 = newRepo()
        repo1.saveCorrection("testkb", "ref1", CorrectionAction.WRONG, "持久化纠正")

        // 模拟重启
        appScope.cancel()
        val repo2 = newRepo()
        val corrections = repo2.readCorrections("testkb")

        assertEquals("重启后应有1条纠正", 1, corrections.size)
        val c = corrections["ref1"]
        assertNotNull("ref1 应存在", c)
        assertEquals(CorrectionAction.WRONG, c?.action)
        assertEquals("持久化纠正", c?.replacementText)
    }

    // ═══════════ Test 6: 同一 memoryId 覆盖更新 ═══════════

    @Test
    fun `same_memory_id_overwrites`() = runBlocking {
        val repo = newRepo()
        repo.saveCorrection("testkb", "ref1", CorrectionAction.WRONG)
        repo.saveCorrection("testkb", "ref1", CorrectionAction.MUTED)

        val corrections = repo.readCorrections("testkb")
        assertEquals("同一 id 只应有1条", 1, corrections.size)
        assertEquals("action 应为最新的 MUTED", CorrectionAction.MUTED, corrections["ref1"]?.action)
    }
}
