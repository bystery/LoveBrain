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

    /**
     * 库外的纠正记录一次都不许被读进来。
     *
     * 独立复核 P0-03 末段的原话是"读取接口也必须走 safeKbFile，当前读取接口直接
     * `File(dir, relativePath)`，canonical boundary 并未覆盖所有 String 入口"。
     * 公开的 `readCorrections` 正是这么写的：库名里带 `..` 就能把纠正文件指到知识库根外面。
     * 纠正是隐私数据（谁说过什么、哪条被判定记错了人），不能靠调用方自觉传对名字。
     */
    @Test
    fun correctionsOutsideTheKnowledgeRootAreNeverRead() = runBlocking {
        val repo = newRepo()
        repo.saveCorrection("testkb", "ref-out", CorrectionAction.WRONG, replacementText = "补正内容")
        assertEquals(
            "前置：真库里应存下一条，否则下面那格会因为「本来就没数据」而假绿",
            1, repo.readCorrections("testkb").size
        )
        val stored = File(File(root, "testkb"), "memory/corrections.json")
        assertTrue("前置：纠正文件应真的落在库里", stored.exists())

        // 同一份文件搬到知识库根**外面**，再用带 .. 的"库名"指过去
        val outside = File(root.parentFile, "corrections_outside_" + System.nanoTime())
        File(outside, "memory").mkdirs()
        stored.copyTo(File(File(outside, "memory"), "corrections.json"))
        try {
            val leaked = repo.readCorrections("../${outside.name}")
            assertEquals(
                "带 .. 的库名把库外的纠正读进来了（实到 ${leaked.size} 条，" +
                    "文件就在 ${outside.name}/memory/corrections.json）",
                0, leaked.size
            )
            assertEquals("库外那份也不该被算进 revision", 0, repo.getCorrectionsRevision("../${outside.name}"))
        } finally {
            outside.deleteRecursively()
        }
    }

    /** 撤销路径同样不许借库名跳出知识库根 */
    @Test
    fun undoingOutsideTheKnowledgeRootWritesNothing() = runBlocking {
        val repo = newRepo()
        val outside = File(root.parentFile, "corrections_target_" + System.nanoTime())
        File(outside, "memory").mkdirs()
        File(File(outside, "memory"), "corrections.json").writeText(
            """[{"memoryId":"ghost","action":"WRONG","replacementText":"外面写的","revision":7}]""",
            Charsets.UTF_8
        )
        try {
            val ok = repo.undoCorrection("../${outside.name}", "ghost")

            assertFalse("库外的纠正记录不该被「撤销」成功", ok)
            val stillThere = File(File(outside, "memory"), "corrections.json").readText(Charsets.UTF_8)
            assertTrue(
                "撤销一旦真的落到库外，就是「改掉了不属于本库的文件」：$stillThere",
                stillThere.contains("ghost")
            )
        } finally {
            outside.deleteRecursively()
        }
    }
}
