package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  回归：kb.json name 字段路径遍历族（删除/列表双向量）。
 *  listAll 过滤 name≠目录名（载荷 ../../..）
 * T2 listAll 正常库（name==目录名）零误伤
 * T3 delete 遍历名返回 false 且 root 外目录无损
 * T4 delete 正常库行为不变
 * 每用例带 withTimeout(10s) 防挂死（沿用 ReentrancyTest 先例）。
 */
class KnowledgeRepositorySecurityTest {

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

    private fun writeKbJson(dir: File, kb: KnowledgeBase) {
        File(dir, "kb.json").writeText(Json.encodeToString(KnowledgeBase.serializer(), kb), Charsets.UTF_8)
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kr_security").toFile()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    /** ：kb.json name 字段 ≠ 目录名（遍历载荷）→ listAll 过滤，不进任何消费端 */
    @Test
    fun listAll_filters_kb_with_mismatched_name() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val evil = File(root, "gift").apply { mkdirs() }
                writeKbJson(evil, KnowledgeBase(
                    name = "../../..",
                    displayName = "TA 的小本本",
                    updatedAt = "2026-08-27T09:00:00+08:00"
                ))

                val all = newRepo().listAll()

                assertTrue("遍历库应被过滤（name）", all.none { it.name == "../../.." })
                assertTrue("遍历库应被过滤（攻击者可控 displayName 不得出现）", all.none { it.displayName == "TA 的小本本" })
                // 过滤 ≠ 删除：目录本体仍在磁盘（仅从列表掐断）
                assertTrue("过滤不应删目录", evil.exists())
            }
        }
    }

    /** T2：正常库（name==目录名）零误伤 */
    @Test
    fun listAll_keeps_normal_kb() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val dir = File(root, "kb").apply { mkdirs() }
                writeKbJson(dir, KnowledgeBase(
                    name = "kb",
                    displayName = "她的档案",
                    updatedAt = "2026-08-27T09:00:00+08:00"
                ))

                val all = newRepo().listAll()

                assertEquals("正常库应恰好 1 个", 1, all.size)
                assertEquals("kb", all[0].name)
                assertEquals("她的档案", all[0].displayName)
            }
        }
    }

    /** T3：delete 收到遍历名 → 返回 false，且 root 之外的目录/文件无损 */
    @Test
    fun delete_rejects_traversal_name_and_outside_dirs_intact() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                // 在 root 外构造"受害目录"（模拟 app 私有数据树的兄弟目录）
                val outsideDir = File(root.parentFile, "kr_security_outside_${System.nanoTime()}").apply { mkdirs() }
                val outsideFile = File(outsideDir, "precious.txt").apply { writeText("data") }
                File(root, "gift").mkdirs()
                try {
                    val ok = newRepo().delete("../${outsideDir.name}")

                    assertFalse("遍历删除必须被拒绝", ok)
                    assertTrue("root 外目录不得受损", outsideDir.exists())
                    assertTrue("root 外文件不得受损", outsideFile.exists())
                } finally {
                    runCatching { outsideDir.deleteRecursively() }
                }
            }
        }
    }

    /** T4：正常库删除行为不变（守卫对 name==目录名路径零影响） */
    @Test
    fun delete_normal_kb_still_works() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                val dir = File(root, "kb").apply { mkdirs() }
                writeKbJson(dir, KnowledgeBase(
                    name = "kb",
                    displayName = "kb",
                    updatedAt = "2026-08-27T09:00:00+08:00"
                ))
                File(dir, "understand").mkdirs()
                File(dir, "understand/me.md").writeText("x")

                val ok = newRepo().delete("kb")

                assertTrue("正常删除应成功", ok)
                assertFalse("知识库目录应被物理删除", dir.exists())
            }
        }
    }
}
