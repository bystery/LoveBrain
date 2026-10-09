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
 * 第四批 回归测试。
 *
 * 旧路径有内容、新路径存在且为空 → readFile 必须返回空，不能回退旧内容。
 *       新文件不存在、旧文件存在 → 应正常兼容读取旧文件。
 * 删除 active KB 后，剩余库中恰好一个 active=true 且等于 securePrefs.activeKbName。
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

    // ═══════════ 旧内容不能复活 ═══════════

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

    // ═══════════ 删除 active KB 后 active 一致性 ═══════════

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
                val ok = repo.catalogWrites.delete("kba")
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

                repo.catalogWrites.delete("only")

                assertEquals("删除最后一个库后 activeKbName 应为空", "", activeName)
            }
        }
    }

    /**
     * 删掉当前库之后，下一个当前库只能从**枚举认下来的库**里选，不能从"哪颗目录最近被碰过"里选。
     *
     * 盘上摆三颗目录：被删的当前库、一座有效库、一颗 mtime 最新的**半套目录**（没有 kb.json，
     * 永远进不了清单）。旧的「挑 mtime 最新目录」判据（成员与实现均已删）会挑中那颗半套目录，
     * 于是当前库指向一座不是库的目录，而同一趟 `activateWithin` 顺手把有效库的 kb.json 全改成
     * active=false——
     * 三级回退的第一判据（同名且 active）再也落不下去。这是 §12.1 三分法里的
     * 「当前库引用错误」那一支，且是 §12.3「不得删除后让活动引用指向不存在的库」正面禁止的那件事。
     *
     * 用真磁盘跑一遍是为了让"目录 ≠ 库"这条差别由文件系统说，而不是由替身的记账说。
     */
    @Test
    fun delete_active_kb_hands_the_choice_to_a_listed_library_not_to_the_newest_directory() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val now = "2026-09-13T10:00:00+08:00"
                val dirA = kbDir("kba")
                writeKbJson(dirA, KnowledgeBase(name = "kba", displayName = "A", updatedAt = now, active = true))
                val dirB = kbDir("kbb")
                writeKbJson(dirB, KnowledgeBase(name = "kbb", displayName = "B", updatedAt = now, active = false))
                val shell = kbDir("shell")

                // 夹具：让那颗半套目录成为"最近被碰过"的那一颗——即使它 mtime 全根最大，
                // 选择也必须落到枚举认下的库上；谁把 mtime 判据画回来，这一格就红
                val newest = System.currentTimeMillis() + 60_000L
                assertTrue("夹具：setLastModified 没生效", shell.setLastModified(newest))
                dirB.setLastModified(newest - 10_000L)
                val mtimeNewest = root.listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.maxByOrNull { it.lastModified() }?.name
                assertEquals("夹具：mtime 最新的必须是那颗进不了清单的半套目录", "shell", mtimeNewest)

                var activeName = "kba"
                val prefs = mockk<SecurePrefs>(relaxed = true)
                every { prefs.activeKbName } answers { activeName }
                every { prefs.activeKbName = any<String>() } answers { activeName = arg(0) }
                val repo = newRepoWithPrefs(prefs)

                assertTrue("删除当前库应当成功", repo.catalogWrites.delete("kba"))

                assertEquals("当前库要落到枚举认下的那一座，而不是 mtime 最新的那颗目录", "kbb", activeName)
                val bMeta = json.decodeFromString<KnowledgeBase>(File(dirB, "kb.json").readText())
                assertTrue("被选中那一座的 kb.json 也要跟着落下 active", bMeta.active)
                assertEquals("活动引用读回来的是同一身份", "kbb", repo.getActive()?.name)
            }
        }
    }
}
