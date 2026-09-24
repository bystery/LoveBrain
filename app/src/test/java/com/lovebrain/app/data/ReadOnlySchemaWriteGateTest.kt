package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * P0-03（独立复核 2026-09-24 §3.1）：未来 schema 的只读保护不许被绕过。
 *
 * 报告原文点名的写路径，逐条在这里跑一遍——它们过去全部直接 `atomicWriteText`
 * 或直接构造 `File(dir, …)`，于是"v4 库在 v3 App 里显示 read-only"只是**界面**上只读，
 * 磁盘照样被 v3 代码改写。
 *
 * 两条断言口径：
 * 1. 所有公开 mutation 跑完之后，整个库目录树必须与跑之前**逐字节相同**；
 * 2. mutation 不许抛穿到调用方——只读是"这次没写成"，不是"面板崩了"。
 *
 * 最后那格源码形状测试只是防回归绊线；真正的证明是目录树比对。
 */
class ReadOnlySchemaWriteGateTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    private val futureSchema = KnowledgeSchemaVersion.CURRENT + 1

    @Before
    fun setUp() {
        root = Files.createTempDirectory("ro_schema_gate").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun newRepo(): KnowledgeRepository = KnowledgeRepository(
        knowledgeRoot = root,
        securePrefs = mockk<SecurePrefs>(relaxed = true),
        context = mockk<Context>(relaxed = true),
        appScope = appScope
    )

    /** 一个"来自未来"的库：schema 版本号比本 App 支持的还大，内容故意写得像 v4 */
    private fun seedFutureKb(name: String = "kb") {
        val dir = File(root, name).apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(
                    name = name,
                    displayName = "未来的她",
                    stage = "磨合期",
                    turnCount = 7,
                    topicCount = 0,
                    active = true
                )
            ),
            Charsets.UTF_8
        )
        File(dir, ".schema_version").writeText(futureSchema.toString(), Charsets.UTF_8)
        listOf("understand", "moment", "memory").forEach { File(dir, it).mkdirs() }
        write("moment/recent.md", "未来版本写下的此刻\n")
        write("moment/scene.md", "未来版本的场景链\n")
        write("moment/topic.md", "- [2026-09-24 09:00] 正在聊：未来的话题")
        write("moment/plan.md", "# 事项计划\n\n## 进行中\n\n## 已结束\n")
        write("moment/.archive_op.json", "{}")
        write("understand/me.md", "未来版本的画像\n")
        write("understand/warmth.md", "亲密度：61\n")
        write("memory/raw_topic.md", "## [2026-01-01] 旧\n## [2026-01-02] 旧二\n")
        write("memory/corrections.json", "[]")
        write("memory/.revision", "3")
        write("memory/lessons.md", "未来的经验\n")
        write("memory/counseling_log.md", "未来的谈心\n")
        write("memory/actual_sent.md", "未来的发送记录\n")
    }

    private fun write(relativePath: String, content: String) {
        val f = File(File(root, "kb"), relativePath)
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }

    /** 打开这个库：迁移器必须把它标成只读，且一个字节都不补 */
    private fun openReadOnly() {
        repo = newRepo()
        runBlocking { repo.migrateIfNeeded("kb") }
        assertTrue("the library must be detected as read-only", repo.isSchemaReadOnly("kb"))
        assertFalse("and therefore not writable", repo.isWritable("kb"))
    }

    private data class FileSnapshot(val sha256: String, val bytes: Long)

    private fun snapshot(): Map<String, FileSnapshot> {
        val base = File(root, "kb")
        return base.walkTopDown().filter { it.isFile }.associate { f ->
            val data = f.readBytes()
            f.relativeTo(base).path.replace('\\', '/') to
                FileSnapshot(sha(data), data.size.toLong())
        }
    }

    private fun sha(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** 报告 §3.1 点名的全部写入口，一格不许漏 */
    private fun allPublicMutations(): List<Pair<String, () -> Unit>> = listOf(
        "writeFile" to { runBlocking { repo.writeFile("kb", "moment/recent.md", "v3 想覆盖未来结构") } },
        "appendFile" to { runBlocking { repo.appendFile("kb", "moment/recent.md", "\n追加一行\n") } },
        "deleteFile" to { runBlocking { repo.deleteFile("kb", "moment/scene.md") } },
        "writeFileWithVersion" to {
            runBlocking {
                val version = repo.readFileWithVersion("kb", "moment/scene.md").second
                repo.writeFileWithVersion("kb", "moment/scene.md", "覆盖", version)
            }
        },
        "writeFileWithRevisionCheck" to {
            runBlocking { repo.writeFileWithRevisionCheck("kb", "moment/plan.md", "覆盖", 0) }
        },
        "appendFileWithRevisionCheck" to {
            runBlocking { repo.appendFileWithRevisionCheck("kb", "moment/plan.md", "追加", 0) }
        },
        "writeVectorWithRevisionCheck" to {
            runBlocking { repo.writeVectorWithRevisionCheck("kb", mapOf("intimacy" to 99), 0) }
        },
        "setActive" to { runBlocking { repo.setActive("kb") } },
        "saveIntent" to { runBlocking { repo.saveIntent("kb", "持续的意图", true) } },
        "saveCorrection" to {
            runBlocking {
                repo.saveCorrection("kb", "mem-1", CorrectionAction.WRONG, replacementText = "改成这样")
            }
        },
        "undoCorrection" to { runBlocking { repo.undoCorrection("kb", "mem-1") } },
        "incrementTurnCount" to { runBlocking { repo.incrementTurnCount("kb") } },
        "incrementTurnCountBy" to { runBlocking { repo.incrementTurnCountBy("kb", 3) } },
        "updateDisplayName" to { runBlocking { repo.updateDisplayName("kb", "被 v3 改掉的显示名") } },
        "updateStage" to { runBlocking { repo.updateStage("kb", "热恋期") } },
        "updateWarmthStageLabel" to { runBlocking { repo.updateWarmthStageLabel("kb", "热恋期") } },
        "writeVector" to { runBlocking { repo.writeVector("kb", mapOf("intimacy" to 12)) } },
        "setCurrentTopic" to { runBlocking { repo.setCurrentTopic("kb", "v3 写的新话题") } },
        "rotateTopic" to { runBlocking { repo.rotateTopic("kb") } },
        "appendCounselingEntries" to { runBlocking { repo.appendCounselingEntries("kb", "记录", "分析") } },
        "appendActualSentRecord" to { runBlocking { repo.appendActualSentRecord("kb", "发出去的一句话") } },
        "replaceActualSentRecord" to {
            runBlocking { repo.replaceActualSentRecord("kb", "未来的发送记录", "改掉") }
        },
        "getLessonCount-backfill" to { runBlocking { repo.getLessonCount("kb") } },
        "migrateIfNeeded" to { runBlocking { repo.migrateIfNeeded("kb") } }
    )

    /**
     * 主证据：把这库能被调到的每一个写入口都跑一遍，磁盘必须一点没动。
     *
     * P0-03 之前这条是红的：setActive / saveIntent / saveCorrection / undoCorrection /
     * writeMemoryRevision / incrementTurnCountBy / updateDisplayName / updateStage /
     * incrementTopicCount / archive operation state / getLessonCount 回填，
     * 每一条都绕过了只读判定。
     */
    @Test
    fun `no public mutation can change a library whose schema is newer than this build`() {
        seedFutureKb()
        openReadOnly()
        val before = snapshot()
        check(before.size >= 12) { "fixture too small, only ${before.size} file(s) captured" }

        val thrown = allPublicMutations().mapNotNull { (label, action) ->
            runCatching(action).exceptionOrNull()?.let { "$label: ${it::class.simpleName} - ${it.message}" }
        }
        assertTrue(
            "a read-only library must degrade to 'nothing written', not crash the caller:\n" +
                thrown.joinToString("\n"),
            thrown.isEmpty()
        )

        assertSameTree(before, snapshot())
    }

    /** 只读仍然可读：这是"只读保护"而不是"禁用知识库" */
    @Test
    fun `read-only still reads the future content back untouched`() {
        seedFutureKb()
        openReadOnly()
        assertEquals(
            "未来版本写下的此刻",
            runBlocking { repo.readFile("kb", "moment/recent.md") }.trim()
        )
        assertEquals(7, runBlocking { repo.getTurnCount("kb") })
        assertEquals("磨合期", runBlocking { repo.getCurrentStage("kb") })
        assertEquals("未来的她", runBlocking { repo.listAll().first().displayName })
        assertEquals(futureSchema, runBlocking { repo.schemaVersion("kb") })
    }

    /**
     * P0-03 的第二半：`readFile(kbName, relativePath)` 也必须走 safeKbFile。
     * 旧实现直接 `File(dir, relativePath)`，越界读没被拦。
     */
    @Test
    fun `the read entry point cannot be walked out of the library`() {
        seedFutureKb()
        repo = newRepo()
        File(root, "outside-secret.md").writeText("库目录之外的隐私", Charsets.UTF_8)
        File(root, "kb/moment/recent.md").writeText("库内的此刻", Charsets.UTF_8)

        for (
            escape in listOf(
                "../outside-secret.md",
                "./../outside-secret.md",
                "moment/../../outside-secret.md",
                File(root, "outside-secret.md").absolutePath
            )
        ) {
            assertEquals(
                "readFile must not resolve outside the library: $escape",
                "", runBlocking { repo.readFile("kb", escape) }
            )
        }
        // 合法路径照常能读——防止"为了安全把门全关死"
        assertEquals("库内的此刻", runBlocking { repo.readFile("kb", "moment/recent.md") })
    }

    /** 可写的库必须仍然能写：否则这条门禁就成了"所有写都失败"的假安全 */
    @Test
    fun `a current-schema library still writes through the same gate`() {
        seedFutureKb()
        File(root, "kb/.schema_version").writeText(
            KnowledgeSchemaVersion.CURRENT.toString(), Charsets.UTF_8
        )
        repo = newRepo()
        runBlocking { repo.migrateIfNeeded("kb") }
        assertFalse("current schema is writable", repo.isSchemaReadOnly("kb"))

        val before = snapshot()
        runBlocking { repo.writeFile("kb", "moment/recent.md", "本轮写下的此刻") }
        val after = snapshot()

        assertEquals(
            "only the file that was written may change, plus the backup marker at root (outside this tree)",
            setOf("moment/recent.md"),
            (before.keys + after.keys).filter { before[it] != after[it] }.toSet()
        )
        assertEquals("本轮写下的此刻", runBlocking { repo.readFile("kb", "moment/recent.md") })
    }

    /**
     * 绊线：字节写入只允许有一条实现链。
     *
     * 这不是"用 grep 代替行为测试"——UI 触摸边界那种断言才需要设备，
     * 而"Repository 里不许出现第二条写文件的实现"本身就是一条源码形状规则；
     * 行为层的证明是本文件上面的目录树比对。
     */
    @Test
    fun `raw byte writer has exactly one caller inside the repository`() {
        val src = File("src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt")
        assertTrue("cannot find $src — this test must run from the :app module", src.exists())
        val code = src.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
        val text = code.joinToString("\n")

        assertEquals(
            "rawAtomicWriteText must be defined exactly once",
            1, code.count { it.contains("private fun rawAtomicWriteText(") }
        )
        assertEquals(
            "raw bytes may only be written through atomicWriteText, which is the guarded boundary",
            1, code.count {
                it.contains("rawAtomicWriteText(") && !it.contains("private fun rawAtomicWriteText(")
            }
        )
        assertEquals(
            "no second open-write-stream implementation may appear in the repository",
            1, Regex("""FileOutputStream\(""").findAll(text).count()
        )
    }

    private fun assertSameTree(before: Map<String, FileSnapshot>, after: Map<String, FileSnapshot>) {
        val keys = (before.keys + after.keys).sorted()
        val changed = keys.filter { before[it] != after[it] }
        if (changed.isEmpty()) return
        fail(
            "a read-only library was modified on disk:\n" + changed.joinToString("\n") { key ->
                "  $key: ${before[key]?.sha256?.take(12) ?: "<absent>"} -> ${after[key]?.sha256?.take(12) ?: "<absent>"}"
            }
        )
    }
}
