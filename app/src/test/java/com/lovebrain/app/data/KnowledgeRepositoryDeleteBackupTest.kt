package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * RA-03 回归测试：删除 KB 时同时删除该 KB 的全部自动备份。
 *
 * 关键防线：使用 backupGroupKey 精确匹配，不用 startsWith(kbName)，
 * 防止 "kb-a" 误删 "kb-ab" 的备份。
 */
class KnowledgeRepositoryDeleteBackupTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope

    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    private fun writeKbJson(dir: File, name: String) {
        dir.mkdirs()
        File(dir, "kb.json").writeText(
            Json.encodeToString(KnowledgeBase.serializer(),
                KnowledgeBase(name = name, displayName = name, updatedAt = "2026-09-13T10:00:00+08:00")),
            Charsets.UTF_8
        )
    }

    private fun makeBackup(kbName: String, timestamp: String) {
        val backupDir = File(root, ".backup/${kbName}_$timestamp")
        backupDir.mkdirs()
        File(backupDir, "kb.json").writeText("backup of $kbName at $timestamp")
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kr_delete_backup").toFile()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    /**
     * 删除 kb-a 后：
     * - kb-a 正式目录不存在
     * - kb-a 的两个 backup 不存在
     * - kb-ab 的 backup 仍然存在（防 prefix collision 误删）
     */
    @Test
    fun delete_removes_backups_for_kb_but_not_similar_prefix() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                // 创建正式库
                val dirA = File(root, "kb-a")
                writeKbJson(dirA, "kb-a")

                // 创建备份
                makeBackup("kb-a", "20260912_1200")
                makeBackup("kb-a", "20260913_0000")
                // 关键：kb-ab 的备份不应被 kb-a 的删除误伤
                makeBackup("kb-ab", "20260913_0000")

                val ok = newRepo().delete("kb-a")

                assertTrue("删除应成功", ok)
                assertFalse("kb-a 正式目录应不存在", dirA.exists())
                assertFalse("kb-a backup 1 应被删除",
                    File(root, ".backup/kb-a_20260912_1200").exists())
                assertFalse("kb-a backup 2 应被删除",
                    File(root, ".backup/kb-a_20260913_0000").exists())
                assertTrue("kb-ab backup 不应被误删",
                    File(root, ".backup/kb-ab_20260913_0000").exists())
            }
        }
    }
}
