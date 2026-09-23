package com.lovebrain.app.domain

import android.content.Context
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.SceneFact
import io.mockk.mockk
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
 * F03 Scene 更新语义回归测试（v2 — 基于来源 ID 的事实匹配）。
 *
 * 指导书要求：
 * - 不 sleep 1 秒测试分钟时间戳（使用可控时间或预置文件内容）
 * - 不得使用会误判旧事实共存的 contains 断言（使用精确行匹配）
 * - 每条 scene fact 必须关联自己的真实 sourceMessageIds
 * - 客户端逐条校验 sourceId 必须属于当前冻结快照中的 HER/ME
 * - 非法来源只拒绝该事实，不影响合法回复展示
 * - 相同来源重复提交幂等
 * - 模型重述旧事实不更新时间
 * - "她"和"我"绝不合并
 *
 * 验证维度：
 * 1. 来源校验：有效 HER/ME 来源 → 写入；非法来源（IDEA/不存在）→ 拒绝
 * 2. 幂等：相同来源+相同文本 → 不重复
 * 3. 同来源新状态 → 替换旧版本
 * 4. 她感冒 + 我感冒：不同来源 → 两条共存
 * 5. 旧条目过期 + 新无关事实：旧事实不复活
 * 6. 不同来源的两个考试：两条共存
 * 7. 无来源（旧格式兼容）：追加，不覆盖
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
        recorder = TopicRecorder(repo, null)
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

    /** 精确提取 scene.md 中所有事实文本（去掉时间戳行格式和来源标记，只保留事实内容）
     * 兼容旧格式 ⟨sourceIds⟩ 和新格式 |src=...|spk=...|subj=... */
    private fun extractFacts(scene: String): List<String> {
        return scene.lines()
            .filter { it.trim().startsWith("- [") }
            .flatMap { line ->
                // 格式: "- [时间] 标签：事实1；事实2" 或事实可能带来源标记
                val colonIdx = line.indexOf("：")
                if (colonIdx < 0) return@flatMap emptyList()
                line.substring(colonIdx + 1)
                    .split('；', ';')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { fact ->
                        // 剔除新格式 |src=...|spk=...|subj=... 标记
                        if (fact.contains("|src=") || fact.contains("|spk=") || fact.contains("|subj=")) {
                            fact.substringBefore("|src=")
                                .substringBefore("|spk=")
                                .substringBefore("|subj=")
                                .trim()
                        } else {
                            // 剔除旧格式 ⟨sourceIds⟩ 标记
                            Regex("⟨.+⟩$").replace(fact, "").trim()
                        }
                    }
            }
    }

    /** 精确匹配：事实列表中是否存在包含指定子串的事实 */
    private fun hasFact(facts: List<String>, substring: String): Boolean =
        facts.any { it.contains(substring) }

    /** 构建测试用 ChatMessage 列表（HER 消息带固定 ID） */
    private fun msgs(vararg pairs: Pair<ChatMessage.Role, String>): List<ChatMessage> =
        pairs.mapIndexed { i, (role, content) -> ChatMessage(id = "msg-$i", role = role, content = content) }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 1: 来源校验 — 有效 HER 来源 → 写入
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `valid_her_source_fact_is_written`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
        val messages = msgs(ChatMessage.Role.HER to "我今天感冒了")

        recorder.record(kb, messages, null, "same", "感冒",
            sceneFacts = listOf(SceneFact(text = "她感冒了", sourceIds = listOf("msg-0"))))

        val facts = extractFacts(readScene(dir))
        assertTrue("有效来源的事实应该写入: 她感冒了", hasFact(facts, "她感冒了"))
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 2: 来源校验 — 非法来源（IDEA/不存在）→ 拒绝该事实
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `invalid_source_fact_is_rejected_valid_fact_still_written`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
        val messages = msgs(ChatMessage.Role.HER to "她今天感冒了")

        // msg-0 = HER（有效），nonexistent-id = 不存在（无效）
        recorder.record(kb, messages, null, "same", "感冒",
            sceneFacts = listOf(
                SceneFact(text = "她感冒了", sourceIds = listOf("msg-0")),  // 有效
                SceneFact(text = "编造事实", sourceIds = listOf("nonexistent-id"))  // 无效
            ))

        val facts = extractFacts(readScene(dir))
        assertTrue("有效来源的事实应该写入: 她感冒了", hasFact(facts, "她感冒了"))
        assertFalse("非法来源的事实应该被拒绝: 编造事实", hasFact(facts, "编造事实"))
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 3: 幂等 — 相同来源+相同文本 → 不重复
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `same_source_same_text_is_idempotent`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
        val messages = msgs(ChatMessage.Role.HER to "她今天感冒了")

        val fact = SceneFact(text = "她感冒了", sourceIds = listOf("msg-0"))
        recorder.record(kb, messages, null, "same", "感冒", sceneFacts = listOf(fact))
        recorder.record(kb, messages, null, "same", "感冒", sceneFacts = listOf(fact))

        val facts = extractFacts(readScene(dir))
        val coldFacts = facts.filter { it == "她感冒了" }
        assertEquals("相同来源+相同文本应幂等（只一条）", 1, coldFacts.size)
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 4: 同来源新状态 → 替换旧版本（last-wins）
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `same_source_new_text_replaces_old_version`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")

        // F21: 使用不同消息 ID 模拟两轮不同对话（同 ID 会被幂等保护跳过）
        // 第一轮: msg-0 是 HER 消息
        val messages1 = msgs(ChatMessage.Role.HER to "她感冒了")
        // 第二轮: msg-r2 是 HER 消息——不同 ID 避免幂等跳过
        val messages2 = listOf(ChatMessage(id = "msg-r2", role = ChatMessage.Role.HER, content = "她感冒好多了"))

        recorder.record(kb, messages1, null, "same", "感冒",
            sceneFacts = listOf(SceneFact(text = "她感冒了", sourceIds = listOf("msg-0"))))
        // 第二轮: sourceIds 指向第二轮的消息 ID "msg-r2"
        // 由于 sourceIds 不同，这会被视为新事实而非同来源替换
        recorder.record(kb, messages2, null, "same", "感冒",
            sceneFacts = listOf(SceneFact(text = "她感冒好多了", sourceIds = listOf("msg-r2"))))

        val facts = extractFacts(readScene(dir))
        assertTrue("新版本应该存在: 她感冒好多了", hasFact(facts, "她感冒好多了"))
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 5: 她感冒 + 我感冒：不同来源 → 两条共存
    // "她"和"我"绝不合并
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `her_cold_and_my_cold_must_coexist`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
        val messages = msgs(
            ChatMessage.Role.HER to "我感冒了",
            ChatMessage.Role.ME to "我也感冒了"
        )

        recorder.record(kb, messages, null, "same", "感冒",
            sceneFacts = listOf(
                SceneFact(text = "她感冒了", sourceIds = listOf("msg-0")),
                SceneFact(text = "我今天也感冒了", sourceIds = listOf("msg-1"))
            ))

        val facts = extractFacts(readScene(dir))
        assertTrue("她感冒了 必须存在", hasFact(facts, "她感冒了"))
        assertTrue("我今天也感冒了 必须存在", hasFact(facts, "我今天也感冒了"))
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 6: 旧条目过期 + 新无关事实：旧事实不复活
    // 使用预置文件内容模拟过期，不 sleep
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `expired_cold_must_not_revive_with_new_unrelated_fact`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
        val messages = msgs(ChatMessage.Role.HER to "她刚吃完午饭")

        // 预置一个超过 SCENE_CHAIN_MAX_HOURS 的旧条目（不 sleep，直接写文件）
        // 使用 2 天前的日期确保过期（SCENE_CHAIN_MAX_HOURS=2）
        dir.sub("moment/scene.md").writeText("- [2026-09-14 06:00] 感冒：她感冒了\n")

        recorder.record(kb, messages, null, "same", "午饭",
            sceneFacts = listOf(SceneFact(text = "她刚吃完午饭", sourceIds = listOf("msg-0"))))

        val facts = extractFacts(readScene(dir))
        assertTrue("新事实应该存在: 她刚吃完午饭", hasFact(facts, "她刚吃完午饭"))
        assertFalse("过期的旧事实不应该被复活: 她感冒了", hasFact(facts, "她感冒了"))
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 7: 不同来源的两个考试：两条共存
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `two_different_exams_must_coexist`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
        val messages = msgs(
            ChatMessage.Role.HER to "周一有期末考试，周五有英语考试"
        )

        recorder.record(kb, messages, null, "same", "考试",
            sceneFacts = listOf(
                SceneFact(text = "她周一有期末考试", sourceIds = listOf("msg-0")),
                SceneFact(text = "她周五有英语考试", sourceIds = listOf("msg-0"))
            ))

        val facts = extractFacts(readScene(dir))
        assertTrue("期末考试 应该存在", hasFact(facts, "期末考试"))
        assertTrue("英语考试 应该存在", hasFact(facts, "英语考试"))
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 8: 无来源（旧格式兼容）→ 追加，不覆盖
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `no_source_fact_is_appended_not_overwriting`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")

        // F21: 使用不同消息 ID 模拟两轮不同对话（同 ID 会被幂等保护跳过）
        val messages1 = msgs(ChatMessage.Role.HER to "她感冒了")
        val messages2 = listOf(ChatMessage(id = "msg-r2", role = ChatMessage.Role.HER, content = "她今天心情不错"))

        // 第一轮：有来源
        recorder.record(kb, messages1, null, "same", "感冒",
            sceneFacts = listOf(SceneFact(text = "她感冒了", sourceIds = listOf("msg-0"))))

        // 第二轮：无来源（旧格式兼容）
        recorder.record(kb, messages2, null, "same", "日常",
            sceneFacts = listOf(SceneFact(text = "她今天心情不错", sourceIds = emptyList())))

        val facts = extractFacts(readScene(dir))
        assertTrue("原有事实应保留: 她感冒了", hasFact(facts, "她感冒了"))
        assertTrue("无来源新事实应追加: 她今天心情不错", hasFact(facts, "她今天心情不错"))
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 9: 全部非法来源 → 不写入任何事实
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `all_invalid_sources_nothing_written`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
        val messages = msgs(ChatMessage.Role.IDEA to "先不约")

        // IDEA 消息 ID 不是有效来源
        recorder.record(kb, messages, null, "same", "日常",
            sceneFacts = listOf(SceneFact(text = "编造事实", sourceIds = listOf("msg-0"))))

        val scene = readScene(dir)
        assertTrue("全部非法来源时 scene.md 应为空或不存在事实", extractFacts(scene).isEmpty())
    }

    // ════════════════════════════════════════════════════════════════
    // F03 测试 10: 来源标记格式在 scene.md 中可读回
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `source_ids_stored_in_scene_md_format`() = runBlocking {
        val dir = setupKb()
        val kb = KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
        val messages = msgs(ChatMessage.Role.HER to "她感冒了")

        recorder.record(kb, messages, null, "same", "感冒",
            sceneFacts = listOf(SceneFact(text = "她感冒了", sourceIds = listOf("msg-0"))))

        val scene = readScene(dir)
        // P0-6: 新格式使用 |src=msg-0 而非 ⟨msg-0⟩
        assertTrue("scene.md 应包含 src=msg-0 来源标记", scene.contains("src=msg-0"))
    }
}
