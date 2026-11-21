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
 * P1-6 的两格：坏 `kb.json` 不许静默换库；原子写回退路中断后要能在启动时恢复。
 *
 * 两件事在旧实现里都"看起来没事"：`getActive` 的第三级兜底会把读写切到另一本库；
 * `.bak` / `.tmp` 残骸没有任何人收拾，那次写入等于消失。
 */
class CorruptMetaAndRecoveryTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private var activeName = ""

    @Before
    fun setUp() {
        root = Files.createTempDirectory("corrupt_meta_recovery").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun newRepo(prefs: SecurePrefs? = null): KnowledgeRepository {
        val p = prefs ?: mockk<SecurePrefs>(relaxed = true).also { m ->
            every { m.activeKbName } answers { activeName }
            every { m.activeKbName = any<String>() } answers { activeName = arg(0) }
        }
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = p,
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    private fun seedKb(name: String, validJson: Boolean) {
        val dir = File(root, name).apply { mkdirs() }
        val raw = if (validJson) {
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = name, displayName = "她-$name", active = true)
            )
        } else {
            "{ 这不是 json"
        }
        File(dir, "kb.json").writeText(raw, Charsets.UTF_8)
    }

    @Test
    fun `an unreadable active library is reported instead of silently switching`() = runBlocking {
        seedKb("kb-good", validJson = true)
        seedKb("kb-bad", validJson = false)
        activeName = "kb-bad"

        val repo = newRepo()
        val active = repo.getActive()

        assertNull("偏好指向的库还在、只是元数据坏了：不许静默切到别的库", active)
    }

    @Test
    fun `a deleted active library still falls back to an existing one`() = runBlocking {
        seedKb("kb-good", validJson = true)
        activeName = "kb-gone" // 目录根本不存在——正常的删除后收尾

        val repo = newRepo()

        assertEquals("库被删掉时仍按旧口径回退", "kb-good", repo.getActive()?.name)
    }

    @Test
    fun `a readable active library is returned as before`() = runBlocking {
        seedKb("kb-good", validJson = true)
        activeName = "kb-good"

        assertEquals("kb-good", newRepo().getActive()?.name)
    }

    // ═══════════ 启动恢复：`.bak` / `.tmp` 残骸 ═══════════

    @Test
    fun `a leftover backup is restored when the target never came back`() = runBlocking {
        seedKb("kb", validJson = true)
        val moment = File(File(root, "kb"), "moment").apply { mkdirs() }
        File(moment, ".recent.md.bak").writeText("旧内容（回退路中断时被改名）", Charsets.UTF_8)

        val repo = newRepo()
        runBlocking { repo.fileMutex.withLock { repo.recoverInterruptedWritesUnlocked() } }

        val target = File(moment, "recent.md")
        assertTrue("目标文件必须被恢复出来", target.isFile)
        assertEquals("旧内容（回退路中断时被改名）", target.readText(Charsets.UTF_8))
        assertFalse("恢复后不许留下 .bak", File(moment, ".recent.md.bak").exists())
    }

    @Test
    fun `a leftover tmp is promoted when the target is missing but never overwrites an existing target`() = runBlocking {
        seedKb("kb", validJson = true)
        val moment = File(File(root, "kb"), "moment").apply { mkdirs() }
        File(moment, ".scene.md.tmp").writeText("已 fsync 的新内容", Charsets.UTF_8)
        File(moment, ".recent.md.tmp").writeText("陈旧临时内容", Charsets.UTF_8)
        File(moment, "recent.md").writeText("已经在的正文", Charsets.UTF_8)

        val repo = newRepo()
        runBlocking { repo.fileMutex.withLock { repo.recoverInterruptedWritesUnlocked() } }

        assertEquals("目标缺失时 tmp 提升为正文", "已 fsync 的新内容", File(moment, "scene.md").readText(Charsets.UTF_8))
        assertEquals(
            "目标已存在时目标赢，绝不为'更新的那份'删掉可读文件",
            "已经在的正文", File(moment, "recent.md").readText(Charsets.UTF_8)
        )
        assertFalse("残骸要清干净", File(moment, ".scene.md.tmp").exists())
        assertFalse("残骸要清干净", File(moment, ".recent.md.tmp").exists())
    }
}
