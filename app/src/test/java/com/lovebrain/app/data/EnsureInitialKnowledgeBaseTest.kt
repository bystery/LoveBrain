package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * F10 默认知识库初始化测试。
 *
 * 覆盖场景：
 * 1. 首次启动：无库无标记 → 创建恰好一个默认知识库
 * 2. 重复启动：已有库 → 沿用，不创建新库
 * 3. 写一半中断：kb.json 存在但缺文件 → 补齐缺失文件
 * 4. 仅 kb.json 存在但无标记 → 视为已有库，补齐文件
 * 5. 已有正常库 → 不影响
 * 6. 用户删除最后一个库后 → 不重复创建
 * 7. 导入优先：有导入库时不创建默认库
 */
class EnsureInitialKnowledgeBaseTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private var activeKbName: String = ""

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

    private fun writeKbJson(dir: File, kb: KnowledgeBase) {
        File(dir, "kb.json").writeText(json.encodeToString(KnowledgeBase.serializer(), kb), Charsets.UTF_8)
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("ensure_init_kb").toFile()
        activeKbName = ""
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    // ════════════════════════════════════════════════════════════════
    // Test 1: 首次启动 — 无库无标记 → 创建恰好一个默认知识库
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `first_launch_creates_exactly_one_default_kb`() = runBlocking {
        val repo = newRepo()

        repo.ensureInitialKnowledgeBase()

        val allKbs = repo.listAll()
        assertEquals("应恰好有一个库", 1, allKbs.size)
        assertEquals("库名应为 default", "default", allKbs[0].name)
        assertEquals("阶段应为待确定", "待确定", allKbs[0].stage)
        assertTrue("应为 active", allKbs[0].active)
        assertEquals("activeKbName 应为 default", "default", activeKbName)

        // 画像默认真实空内容
        val meContent = repo.readFile("default", "understand/me.md")
        assertEquals("me.md 应为空", "", meContent)
        val herContent = repo.readFile("default", "understand/her.md")
        assertEquals("her.md 应为空", "", herContent)

        // 标记文件存在
        assertTrue("标记文件应存在", File(root, ".kb_initialized").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 2: 重复启动 — 已有库 → 沿用，不创建新库
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `repeat_launch_keeps_existing_kb_no_new_default`() = runBlocking {
        // 预置一个已有库
        val dir = File(root, "mykb").apply { mkdirs() }
        File(dir, "understand").mkdirs()
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()
        writeKbJson(dir, KnowledgeBase(name = "mykb", displayName = "我的库", updatedAt = "2026-09-16T09:00:00+08:00", active = true))
        File(dir, "understand/me.md").writeText("真实画像")
        File(dir, "understand/her.md").writeText("她的画像")
        File(dir, "understand/warmth.md").writeText("")
        File(dir, "moment/topic.md").writeText("- [2026-09-16 09:00] 正在聊：测试")
        File(dir, "moment/recent.md").writeText("")
        File(dir, "moment/scene.md").writeText("")
        File(dir, "moment/plan.md").writeText("# 事项计划")
        File(dir, "memory/lessons.md").writeText("")
        File(dir, "memory/raw_chat.md").writeText("")
        File(dir, "memory/raw_topic.md").writeText("")
        File(dir, "memory/raw_scene.md").writeText("")
        File(dir, "memory/counseling_log.md").writeText("")
        activeKbName = "mykb"

        val repo = newRepo()
        repo.ensureInitialKnowledgeBase()

        val allKbs = repo.listAll()
        assertEquals("应仍然只有 1 个库", 1, allKbs.size)
        assertEquals("库名应为 mykb", "mykb", allKbs[0].name)

        // 原有内容不被覆盖
        val meContent = repo.readFile("mykb", "understand/me.md")
        assertEquals("原有画像不应被覆盖", "真实画像", meContent)

        // 不应创建 default 库
        assertFalse("不应创建 default 库", File(root, "default").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 3: 写一半中断 — kb.json 存在但缺文件 → 补齐缺失文件
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `interrupted_init_completes_missing_files`() = runBlocking {
        // 模拟写一半中断：kb.json 存在，understand/ 目录存在，但 me.md 缺失
        val dir = File(root, "partial").apply { mkdirs() }
        File(dir, "understand").mkdirs()
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()
        writeKbJson(dir, KnowledgeBase(name = "partial", displayName = "部分库", updatedAt = "2026-09-16T09:00:00+08:00", active = true))
        File(dir, "understand/her.md").writeText("已有画像")
        // me.md, warmth.md, recent.md, scene.md, plan.md, lessons.md, raw_chat.md 等缺失

        val repo = newRepo()
        repo.ensureInitialKnowledgeBase()

        // 缺失文件应被补齐
        assertTrue("me.md 应被补齐", File(dir, "understand/me.md").exists())
        assertTrue("warmth.md 应被补齐", File(dir, "understand/warmth.md").exists())
        assertTrue("recent.md 应被补齐", File(dir, "moment/recent.md").exists())
        assertTrue("scene.md 应被补齐", File(dir, "moment/scene.md").exists())
        assertTrue("plan.md 应被补齐", File(dir, "moment/plan.md").exists())
        assertTrue("lessons.md 应被补齐", File(dir, "memory/lessons.md").exists())
        assertTrue("raw_chat.md 应被补齐", File(dir, "memory/raw_chat.md").exists())
        assertTrue("raw_topic.md 应被补齐", File(dir, "memory/raw_topic.md").exists())
        assertTrue("raw_scene.md 应被补齐", File(dir, "memory/raw_scene.md").exists())
        assertTrue("counseling_log.md 应被补齐", File(dir, "memory/counseling_log.md").exists())

        // 已有内容不被覆盖
        val herContent = repo.readFile("partial", "understand/her.md")
        assertEquals("已有画像不应被覆盖", "已有画像", herContent)

        // 不应创建 default 库
        assertFalse("不应创建 default 库", File(root, "default").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 4: 用户删除最后一个库后 — 标记存在，不重复创建
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `user_deleted_last_kb_no_recreate`() = runBlocking {
        // 模拟用户已初始化过，然后删除了所有库
        File(root, ".kb_initialized").writeText("done")
        // knowledgeRoot 为空（没有库目录）

        val repo = newRepo()
        repo.ensureInitialKnowledgeBase()

        // 不应创建任何库
        val allKbs = repo.listAll()
        assertEquals("不应创建任何库", 0, allKbs.size)
        assertFalse("不应创建 default 库", File(root, "default").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 5: 导入优先 — 有导入库时不创建默认库
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `imported_kb_takes_priority_no_default_created`() = runBlocking {
        // 模拟用户导入了知识库（有 kb.json 但没有 .kb_initialized 标记）
        val dir = File(root, "imported").apply { mkdirs() }
        File(dir, "understand").mkdirs()
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()
        writeKbJson(dir, KnowledgeBase(name = "imported", displayName = "导入的库", updatedAt = "2026-09-16T09:00:00+08:00", active = true))
        File(dir, "understand/me.md").writeText("导入的画像")
        File(dir, "understand/her.md").writeText("")
        File(dir, "understand/warmth.md").writeText("")
        File(dir, "moment/topic.md").writeText("- [2026-09-16 09:00] 正在聊：导入")
        File(dir, "moment/recent.md").writeText("")
        File(dir, "moment/scene.md").writeText("")
        File(dir, "moment/plan.md").writeText("# 事项计划")
        File(dir, "memory/lessons.md").writeText("")
        File(dir, "memory/raw_chat.md").writeText("")
        File(dir, "memory/raw_topic.md").writeText("")
        File(dir, "memory/raw_scene.md").writeText("")
        File(dir, "memory/counseling_log.md").writeText("")
        activeKbName = "imported"

        val repo = newRepo()
        repo.ensureInitialKnowledgeBase()

        val allKbs = repo.listAll()
        assertEquals("应仍然只有 1 个库", 1, allKbs.size)
        assertEquals("库名应为 imported", "imported", allKbs[0].name)
        assertFalse("不应创建 default 库", File(root, "default").exists())

        // 导入内容不被覆盖
        val meContent = repo.readFile("imported", "understand/me.md")
        assertEquals("导入画像不应被覆盖", "导入的画像", meContent)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 6: 已有正常库再次调用 → 不影响，标记文件补写
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `existing_kb_second_call_no_side_effects`() = runBlocking {
        val repo = newRepo()

        // 第一次调用：创建默认库
        repo.ensureInitialKnowledgeBase()
        assertEquals("第一次调用后应有 1 个库", 1, repo.listAll().size)

        // 第二次调用：不应有副作用
        repo.ensureInitialKnowledgeBase()
        assertEquals("第二次调用后仍应只有 1 个库", 1, repo.listAll().size)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 7: 默认库文件完整性 — 所有必需文件都存在
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `default_kb_has_all_required_files`() = runBlocking {
        val repo = newRepo()
        repo.ensureInitialKnowledgeBase()

        val dir = File(root, "default")
        val requiredFiles = listOf(
            "kb.json",
            "understand/me.md",
            "understand/her.md",
            "understand/warmth.md",
            "moment/topic.md",
            "moment/recent.md",
            "moment/scene.md",
            "moment/plan.md",
            "memory/lessons.md",
            "memory/raw_chat.md",
            "memory/raw_topic.md",
            "memory/raw_scene.md",
            "memory/counseling_log.md"
        )

        requiredFiles.forEach { path ->
            assertTrue("必需文件 $path 应存在", File(dir, path).exists())
        }
    }
}
