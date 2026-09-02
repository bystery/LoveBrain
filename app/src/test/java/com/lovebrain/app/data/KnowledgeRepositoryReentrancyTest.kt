package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  回归：KnowledgeRepository Mutex 非重入死锁家族（5 处持锁区再抢锁）。
 * 每个用例 = withTimeout(10s)（不挂死）+ 落盘内容断言（行为等价），缺一不可。
 * 修复形态：公开方法持锁壳 + 私有 *Unlocked 无锁核心（锁点数量不变）。
 */
class KnowledgeRepositoryReentrancyTest {

    private val json = Json { ignoreUnknownKeys = true }
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

    private fun kbDir(): File = File(root, "kb").apply { mkdirs() }

    private fun writeKbJson(dir: File, kb: KnowledgeBase) {
        File(dir, "kb.json").writeText(Json.encodeToString(KnowledgeBase.serializer(), kb), Charsets.UTF_8)
    }

    private fun File.sub(relativePath: String): File = File(this, relativePath).apply {
        parentFile?.mkdirs()
    }

    @org.junit.Before
    fun setUp() {
        root = Files.createTempDirectory("kr_reentrancy").toFile()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    /** ：rotateTopic 不挂死，且归档/清空/计数三件事全做对 */
    @Test
    fun rotateTopic_completes_and_archives_correctly() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
            val dir = kbDir()
            writeKbJson(dir, KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-08-27T09:00:00+08:00", topicCount = 3))
            dir.sub("memory/raw_chat.md").writeText("- [2026-08-27 10:00] 我：测试")
            dir.sub("moment/recent.md").writeText("")
            dir.sub("memory/raw_scene.md").writeText("")
            dir.sub("moment/scene.md").writeText("")
            dir.sub("moment/topic.md").writeText("- [2026-08-27 09:00] 正在聊：旧话题")

            newRepo().rotateTopic("kb")

            // ② 旧话题已归档进 raw_topic.md
            val archived = dir.sub("memory/raw_topic.md").readText()
            assertTrue("归档缺旧话题", archived.contains("旧话题"))
            // ③ raw_chat.md 已清空
            assertEquals("", dir.sub("memory/raw_chat.md").readText())
            // ④ topicCount = 预建值 3 + 1
            val kb = json.decodeFromString<KnowledgeBase>(dir.sub("kb.json").readText())
            assertEquals(4, kb.topicCount)
            }
        }
    }

    /** T2：appendCounselingEntries 不挂死，且两段式（谈心记录节/军师分析节）位置正确 */
    @Test
    fun appendCounselingEntries_completes_and_keeps_two_sections() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
            val dir = kbDir()
            dir.sub("memory/counseling_log.md").writeText("# 谈心记录\n# 军师分析\n")

            newRepo().appendCounselingEntries("kb", "R1", "A1")

            val content = dir.sub("memory/counseling_log.md").readText()
            val idxH2 = content.indexOf("# 军师分析")
            val idxR1 = content.indexOf("R1")
            val idxA1 = content.indexOf("A1")
            // ② R1 在「# 军师分析」之前（谈心记录节内）
            assertTrue("R1 位置错误", idxR1 in 0 until idxH2)
            // ③ A1 在「# 军师分析」之后（军师分析节内）
            assertTrue("A1 位置错误", idxA1 > idxH2)
            }
        }
    }

    /** T3：writeVector 不挂死，且只改指定维度、其余四维不动 */
    @Test
    fun writeVector_completes_and_updates_only_given_dim() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
            val dir = kbDir()
            dir.sub("understand/warmth.md").writeText(
                "亲密度：50/100\n信任度：50/100\n承诺度：50/100\n激情：50/100\n安全感：50/100\n"
            )

            newRepo().writeVector("kb", mapOf("intimacy" to 77))

            val warmth = dir.sub("understand/warmth.md").readText()
            // ② 亲密度写入 77
            assertTrue("亲密度未写入 77", warmth.contains("亲密度：77"))
            // ③ 其余四维仍为 50
            assertTrue(warmth.contains("信任度：50"))
            assertTrue(warmth.contains("承诺度：50"))
            assertTrue(warmth.contains("激情：50"))
            assertTrue(warmth.contains("安全感：50"))
            }
        }
    }

    /** T4：updateWarmthStageLabel 不挂死，且新阶段写入、旧阶段留历史注释 */
    @Test
    fun updateWarmthStageLabel_completes_and_keeps_history() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
            val dir = kbDir()
            dir.sub("understand/warmth.md").writeText(
                "亲密度：50/100\n信任度：50/100\n承诺度：50/100\n激情：50/100\n安全感：50/100\n- 阶段标签：暧昧期\n"
            )

            newRepo().updateWarmthStageLabel("kb", "热恋期")

            val warmth = dir.sub("understand/warmth.md").readText()
            // ② 新阶段落盘
            assertTrue("新阶段未写入", warmth.contains("热恋期"))
            // ③ 旧阶段以历史注释保留
            assertTrue("旧阶段历史丢失", warmth.contains("过去曾经是暧昧期"))
            }
        }
    }

    /** T5：migrateIfNeeded 旧阶段枚举迁移不挂死，且 kb.json 与 warmth.md 双双更新 */
    @Test
    fun migrateIfNeeded_completes_and_migrates_old_stage() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
            val dir = kbDir()
            // 旧六阶段枚举（无"期"字）触发  迁移分支
            writeKbJson(dir, KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-08-27T09:00:00+08:00", stage = "暧昧"))
            // understand/ 存在 → 跳过 v2→v3 搬迁分支；plan.md 预建 → 不触达 loadSchema
            dir.sub("understand/warmth.md").writeText("- 阶段标签：暧昧\n")
            dir.sub("moment/plan.md").writeText("# 计划\n")

            newRepo().migrateIfNeeded("kb")

            // ② kb.json 阶段已迁移为「暧昧期」
            val kb = json.decodeFromString<KnowledgeBase>(dir.sub("kb.json").readText())
            assertEquals("暧昧期", kb.stage)
            // ③ warmth.md 阶段标签同步为「暧昧期」
            assertTrue("warmth 标签未迁移", dir.sub("understand/warmth.md").readText().contains("暧昧期"))
            }
        }
    }

    /** T6：锁仍有效——双协程并发写同一文件，均能完成（不互斥死）且内容原子（无混合） */
    @Test
    fun concurrent_writes_both_complete_and_result_is_atomic() = runTest {
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
            kbDir()
            val repo = newRepo()

            val j1 = launch(Dispatchers.IO) { repo.writeFile("kb", "a.md", "ONE") }
            val j2 = launch(Dispatchers.IO) { repo.writeFile("kb", "a.md", "TWO") }
            // ① 两个写都能完成（锁活着、不死锁）
            j1.join()
            j2.join()

            // ② 最终内容是两者之一（原子性：绝不出现混合内容）
            val content = File(root, "kb/a.md").readText()
            assertTrue("并发写产生混合内容: $content", content == "ONE" || content == "TWO")
            }
        }
    }
}
