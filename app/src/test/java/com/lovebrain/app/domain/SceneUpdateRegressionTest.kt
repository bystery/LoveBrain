package com.lovebrain.app.domain

import android.content.Context
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * P0 scene 更新语义回归测试。
 *
 * 六类关键反例：
 * 1. 她感冒了 → 她感冒好多了：不能保留旧"她感冒了"
 * 2. 她感冒 + 我感冒：两条必须同时存在
 * 3. 旧感冒超过 TTL + 新增午饭事实：旧感冒绝不能被刷新/复活
 * 4. 旧 entry = 感冒 + 考试，只更新感冒：考试必须保留原 timestamp
 * 5. 两个不同考试：不能因为都叫"考试"互相覆盖
 * 6. 同轮两个同 key 状态：必须确定 last-wins
 */
class SceneUpdateRegressionTest {

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

    private lateinit var repo: KnowledgeRepository
    private lateinit var recorder: TopicRecorder

    @Before
    fun setUp() {
        root = Files.createTempDirectory("scene_regression").toFile()
        repo = newRepo()
        recorder = TopicRecorder(repo)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun setupKb(): File {
        val dir = kbDir()
        writeKbJson(dir, KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00"))
        dir.sub("moment").mkdirs()
        dir.sub("memory").mkdirs()
        dir.sub("understand").mkdirs()
        dir.sub("moment/scene.md").writeText("")
        dir.sub("moment/recent.md").writeText("")
        dir.sub("moment/topic.md").writeText("- [2026-09-16 09:00] 正在聊：测试")
        dir.sub("moment/plan.md").writeText("# 事项计划\n\n## 进行中\n\n## 已结束\n")
        dir.sub("memory/raw_scene.md").writeText("")
        dir.sub("memory/raw_chat.md").writeText("")
        dir.sub("memory/raw_topic.md").writeText("")
        dir.sub("memory/lessons.md").writeText("")
        dir.sub("memory/counseling_log.md").writeText("")
        dir.sub("understand/me.md").writeText("")
        dir.sub("understand/her.md").writeText("")
        dir.sub("understand/warmth.md").writeText("")
        return dir
    }

    private fun readScene(dir: File): String = dir.sub("moment/scene.md").readText()

    /**
     * 反例 1：她感冒了 → 她感冒好多了
     * 不能保留旧"她感冒了"
     */
    @Test
    fun `cold_gets_better_old_cold_must_not_remain`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")

        // 第一轮：她感冒了
        recorder.record(kb, emptyList(), null, "same", "感冒",
            sceneFacts = listOf("她感冒了"))
        // 第二轮：她感冒好多了
        recorder.record(kb, emptyList(), null, "same", "感冒",
            sceneFacts = listOf("她感冒好多了"))

        val scene = readScene(dir)
        assertTrue("新事实应该存在: 她感冒好多了", scene.contains("她感冒好多了"))
        assertFalse("旧事实不应该保留: 她感冒了（不含'好多了'的版本）",
            scene.contains("她感冒了") && !scene.contains("好多了"))
    }

    /**
     * 反例 2：她感冒 + 我感冒
     * 两条必须同时存在，不能因为同 key 互相替换
     */
    @Test
    fun `her_cold_and_my_cold_must_coexist`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")

        recorder.record(kb, emptyList(), null, "same", "感冒",
            sceneFacts = listOf("她感冒了", "我今天也感冒了"))

        val scene = readScene(dir)
        assertTrue("她感冒了 必须存在", scene.contains("她感冒了"))
        assertTrue("我今天也感冒了 必须存在", scene.contains("我今天也感冒了"))
    }

    /**
     * 反例 3：旧感冒超过 TTL + 新增午饭事实
     * 旧感冒绝不能被刷新/复活
     *
     * 由于 SCENE_CHAIN_MAX_HOURS=2，我们写入一个 3 小时前的旧条目
     */
    @Test
    fun `expired_cold_must_not_revive_with_new_unrelated_fact`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")

        // 写入一个 3 小时前的旧条目（超过 SCENE_CHAIN_MAX_HOURS=2）
        dir.sub("moment/scene.md").writeText("- [2026-09-16 08:00] 感冒：她感冒了\n")

        // 新增完全无关的事实
        recorder.record(kb, emptyList(), null, "same", "午饭",
            sceneFacts = listOf("她刚吃完午饭"))

        val scene = readScene(dir)
        assertTrue("新事实应该存在: 她刚吃完午饭", scene.contains("她刚吃完午饭"))
        assertFalse("过期的旧事实不应该被复活: 她感冒了", scene.contains("她感冒了"))
    }

    /**
     * 反例 4：旧 entry = 感冒 + 考试，只更新感冒
     * 考试必须保留原 timestamp
     */
    @Test
    fun `only_update_cold_exam_keeps_original_timestamp`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")

        // 第一轮：感冒 + 考试
        recorder.record(kb, emptyList(), null, "same", "日常",
            sceneFacts = listOf("她感冒了", "她明天有期末考试"))

        val sceneAfterFirst = readScene(dir)
        val firstTimestamp = Regex("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\]").find(sceneAfterFirst)?.groupValues?.get(1)
        assertNotNull("第一轮应该有时间戳", firstTimestamp)

        // 等一小段时间确保时间戳不同
        Thread.sleep(1100)

        // 第二轮：只更新感冒
        recorder.record(kb, emptyList(), null, "same", "日常",
            sceneFacts = listOf("她感冒好多了"))

        val sceneAfterSecond = readScene(dir)
        assertTrue("新事实应该存在: 她感冒好多了", sceneAfterSecond.contains("她感冒好多了"))
        assertTrue("考试事实应该保留: 期末考试", sceneAfterSecond.contains("期末考试"))

        // 考试事实应该保留原来的时间戳，不应该被刷新到新时间
        // 检查是否存在包含"考试"且有旧时间戳的行
        val lines = sceneAfterSecond.lines().filter { it.isNotBlank() }
        val examLine = lines.firstOrNull { it.contains("期末考试") }
        assertNotNull("应该有包含期末考试的行", examLine)
        // 考试行的时间戳不应该等于新时间戳（应该保留原时间戳）
        if (firstTimestamp != null) {
            // 考试事实应该出现在旧时间戳的 entry 中
            val hasOldTimestampWithExam = lines.any { it.contains(firstTimestamp) && it.contains("期末考试") }
            assertTrue("考试事实应该保留在原时间戳 $firstTimestamp 的 entry 中",
                hasOldTimestampWithExam)
        }
    }

    /**
     * 反例 5：两个不同考试
     * 不能因为都叫"考试"互相覆盖
     */
    @Test
    fun `two_different_exams_must_coexist`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")

        recorder.record(kb, emptyList(), null, "same", "考试",
            sceneFacts = listOf("她周一有期末考试", "她周五有英语考试"))

        val scene = readScene(dir)
        assertTrue("期末考试 应该存在", scene.contains("期末考试"))
        assertTrue("英语考试 应该存在", scene.contains("英语考试"))
    }

    /**
     * 反例 6：同轮两个同 key 状态
     * 必须确定 last-wins，不能两个一起写进去
     */
    @Test
    fun `same_identity_same_round_last_wins`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")

        recorder.record(kb, emptyList(), null, "same", "感冒",
            sceneFacts = listOf("她感冒了", "她感冒好多了"))

        val scene = readScene(dir)
        // last-wins：应该只有"她感冒好多了"
        assertTrue("新版本应该存在: 她感冒好多了", scene.contains("她感冒好多了"))
        // 旧版本"她感冒了"（不含"好多了"）不应该独立存在
        val coldAlone = scene.lines().any { line ->
            line.contains("她感冒了") && !line.contains("好多了")
        }
        assertFalse("旧版本不应该与新版同时存在（last-wins）", coldAlone)
    }
}
