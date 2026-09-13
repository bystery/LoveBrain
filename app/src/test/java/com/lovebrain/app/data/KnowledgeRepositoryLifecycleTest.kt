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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 第四批 KB-01 / KB-03 回归测试。
 *
 * KB-01：旧路径有内容、新路径存在且为空 → readFile 必须返回空，不能回退旧内容。
 *       新文件不存在、旧文件存在 → 应正常兼容读取旧文件。
 * KB-03：删除 active KB 后，剩余库中恰好一个 active=true 且等于 securePrefs.activeKbName。
 */
class KnowledgeRepositoryLifecycleTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: File
    private lateinit var appScope: CoroutineScope

    /** 使用真实 SecurePrefs mock：activeKbName 需要可读写 */
    private fun newRepo(activeKbName: String = ""): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = mockk<SecurePrefs>(relaxed = true)
        // 模拟可读写的 activeKbName（Mockk relaxed 默认返回 ""，需手动设值）
        var activeName = activeKbName
        every { prefs.activeKbName } answers { activeName }
        every { prefs.activeKbName = any<String>() } answers { activeName = arg(0) }
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    /** 带自定义 prefs 的 repo 构造（用于需要直接控制 mock 变量的场景） */
    private fun newRepoWithPrefs(prefs: SecurePrefs): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    private fun kbDir(name: String = "kb"): File = File(root, name).apply { mkdirs() }

    private fun writeKbJson(dir: File, kb: KnowledgeBase) {
        File(dir, "kb.json").writeText(Json.encodeToString(KnowledgeBase.serializer(), kb), Charsets.UTF_8)
    }

    private fun File.sub(relativePath: String): File = File(this, relativePath).apply {
        parentFile?.mkdirs()
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kr_lifecycle").toFile()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    // ═══════════ KB-01：旧内容不能复活 ═══════════

    /** 旧路径有内容、新路径存在且为空 → readFile 必须返回空 */
    @Test
    fun readFile_does_not_resurrect_legacy_content_when_new_file_is_empty() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val dir = kbDir()
                // 旧路径 global/me.md 有内容
                val old = dir.sub("global/me.md")
                old.writeText("旧画像")
                // 新路径 understand/me.md 存在且为空
                val current = dir.sub("understand/me.md")
                current.writeText("")

                val repo = newRepo()
                val result = repo.readFile("kb", "understand/me.md")

                assertEquals("空的新文件不应回退旧内容", "", result)
            }
        }
    }

    /** 对照：新文件不存在、旧文件存在 → 应正常兼容读取旧文件 */
    @Test
    fun readFile_falls_back_to_legacy_when_new_file_missing() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val dir = kbDir()
                // 旧路径 global/me.md 有内容
                val old = dir.sub("global/me.md")
                old.writeText("旧画像")
                // 新路径 understand/me.md 不存在（不创建）

                val repo = newRepo()
                val result = repo.readFile("kb", "understand/me.md")

                assertEquals("新文件不存在时应兼容读取旧路径", "旧画像", result)
            }
        }
    }

    /** 新文件有正常内容 → 直接返回新文件内容，不查旧路径 */
    @Test
    fun readFile_returns_new_content_when_new_file_has_content() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val dir = kbDir()
                dir.sub("global/me.md").writeText("旧画像")
                dir.sub("understand/me.md").writeText("新画像")

                val repo = newRepo()
                val result = repo.readFile("kb", "understand/me.md")

                assertEquals("新文件有内容时直接返回新内容", "新画像", result)
            }
        }
    }

    // ═══════════ KB-03：删除 active KB 后 active 一致性 ═══════════

    /** 删除 active KB 后：剩余库中恰好一个 active=true，且它等于 securePrefs.activeKbName */
    @Test
    fun delete_active_kb_keeps_prefs_and_json_active_consistent() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                // 创建两个知识库
                val dirA = kbDir("kba")
                val dirB = File(root, "kbb").apply { mkdirs() }
                val now = "2026-09-13T10:00:00+08:00"
                writeKbJson(dirA, KnowledgeBase(name = "kba", displayName = "A", updatedAt = now, active = true))
                writeKbJson(dirB, KnowledgeBase(name = "kbb", displayName = "B", updatedAt = now, active = false))

                val repo = newRepo(activeKbName = "kba")

                // 删除 active KB（kba）
                val ok = repo.delete("kba")
                assertTrue("删除应成功", ok)

                // 读取剩余库列表
                val remaining = repo.listAll()
                assertEquals("剩余应恰好 1 个库", 1, remaining.size)

                // 剩余库中恰好一个 active=true
                val activeCount = remaining.count { it.active }
                assertEquals("剩余库中恰好一个 active=true", 1, activeCount)

                // active 的那个库应该等于 securePrefs.activeKbName
                // 通过读取 kb.json 验证 B 被标记为 active=true
                val kbJson = Json.decodeFromString<KnowledgeBase>(
                    File(dirB, "kb.json").readText()
                )
                assertTrue("B 应被标记为 active=true", kbJson.active)
            }
        }
    }

    /** 删除最后一个 KB → activeKbName 应为空字符串 */
    @Test
    fun delete_last_kb_clears_active_name() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val dir = kbDir("only")
                val now = "2026-09-13T10:00:00+08:00"
                writeKbJson(dir, KnowledgeBase(name = "only", displayName = "Only", updatedAt = now, active = true))

                var activeName = "only"
                val prefs = mockk<SecurePrefs>(relaxed = true)
                every { prefs.activeKbName } answers { activeName }
                every { prefs.activeKbName = any<String>() } answers { activeName = arg(0) }
                val repo = newRepoWithPrefs(prefs)

                repo.delete("only")

                assertEquals("删除最后一个库后 activeKbName 应为空", "", activeName)
            }
        }
    }
}
