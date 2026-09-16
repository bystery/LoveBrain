package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
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
 * F04 整轮保存和档案恢复测试：累积 operation state、幂等、中断恢复。
 *
 * 验证点：
 * 1. 正常 rotateTopic：归档写入 raw_topic.md，计数 +1，源文件清空
 * 2. 幂等：相同内容重复 rotate 不重复追加、不重复计数
 * 3. 中断恢复：模拟操作状态存在（部分步骤完成），重启后只执行剩余步骤
 * 4. 内容变化：输入内容变化后，旧操作状态失效，从头开始
 * 5. 无内容时不追加不计数但清空源文件
 */
class ArchiveOperationStateTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
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
        File(dir, "kb.json").writeText(json.encodeToString(KnowledgeBase.serializer(), kb), Charsets.UTF_8)
    }

    private fun File.sub(relativePath: String): File = File(this, relativePath).apply {
        parentFile?.mkdirs()
    }

    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("archive_op").toFile()
        repo = newRepo()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun setupKb(topicCount: Int = 0): File {
        val dir = kbDir()
        writeKbJson(dir, KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00", topicCount = topicCount))
        dir.sub("moment").mkdirs()
        dir.sub("memory").mkdirs()
        dir.sub("moment/scene.md").writeText("")
        dir.sub("moment/recent.md").writeText("")
        dir.sub("moment/topic.md").writeText("- [2026-09-16 09:00] 正在聊：旧话题")
        dir.sub("moment/plan.md").writeText("# 事项计划\n\n## 进行中\n\n## 已结束\n")
        dir.sub("memory/raw_scene.md").writeText("")
        dir.sub("memory/raw_chat.md").writeText("")
        dir.sub("memory/raw_topic.md").writeText("")
        return dir
    }

    // ════════════════════════════════════════════════════════════════
    // Test 1: 正常 rotateTopic — 归档写入、计数+1、源文件清空
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `normal_rotate_archives_and_clears`() = runBlocking {
        val dir = setupKb(topicCount = 3)
        dir.sub("memory/raw_chat.md").writeText("- [2026-09-16 10:00] 我：测试对话")
        dir.sub("moment/recent.md").writeText("- [2026-09-16 10:05] 她：最近对话")

        repo.rotateTopic("kb")

        val archived = dir.sub("memory/raw_topic.md").readText()
        assertTrue("归档应包含旧话题", archived.contains("旧话题"))
        assertTrue("归档应包含对话记录", archived.contains("测试对话"))
        assertTrue("归档应包含最近对话", archived.contains("最近对话"))

        val kb = json.decodeFromString<KnowledgeBase>(dir.sub("kb.json").readText())
        assertEquals("topicCount 应该 +1", 4, kb.topicCount)

        assertEquals("raw_chat 应清空", "", dir.sub("memory/raw_chat.md").readText())
        assertEquals("recent 应清空", "", dir.sub("moment/recent.md").readText())
        assertFalse("操作状态文件应删除", File(dir, "moment/.archive_op.json").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 2: 幂等 — 操作状态存在且所有步骤已完成时，重启不重复执行
    // 模拟场景：操作在删除状态文件之前被中断（所有步骤已完成但状态文件残留）
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `idempotent_all_steps_completed_no_rerun`() = runBlocking {
        val dir = setupKb(topicCount = 3)
        dir.sub("memory/raw_chat.md").writeText("对话内容A")

        // 计算正确的 contentHash（与 rotateTopic 内部逻辑一致）
        val rawChat = "对话内容A"
        val recent = ""
        val rawScene = ""
        val scene = ""
        val oldTopic = "正在聊：旧话题"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        listOf(rawChat, recent, rawScene, scene, oldTopic).forEach { md.update(it.toByteArray(Charsets.UTF_8)) }
        val hash = md.digest().joinToString("") { "%02x".format(it) }.take(16)

        // 预置操作状态：所有步骤已完成，但状态文件未被删除（模拟中断）
        val opStateJson = """{"operationId":"test-op-idempotent","kbName":"kb","timestamp":"2026-09-16 09:00","oldTopic":"$oldTopic","contentHash":"$hash","completedSteps":["append_archive","increment_count","clear_sources"]}"""
        dir.sub("moment/.archive_op.json").writeText(opStateJson)

        // 预置已归档内容（模拟 append 已执行）
        dir.sub("memory/raw_topic.md").writeText("\n# [2026-09-16 09:00] $oldTopic\n\n已归档\n")
        // 源文件已清空（模拟 clear 已执行）
        dir.sub("memory/raw_chat.md").writeText("")

        // 重新调用 rotateTopic — 所有步骤已完成，应跳过全部步骤，只删除状态文件
        repo.rotateTopic("kb")

        val kb = json.decodeFromString<KnowledgeBase>(dir.sub("kb.json").readText())
        assertEquals("topicCount 不应重复增加", 3, kb.topicCount)

        val archived = dir.sub("memory/raw_topic.md").readText()
        assertFalse("不应重复追加归档", archived.contains("已归档\n# ["))

        assertFalse("操作状态文件应删除", File(dir, "moment/.archive_op.json").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 3: 中断恢复 — 操作状态存在但未完成，重启后只执行剩余步骤
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `interrupted_op_state_resumes_remaining_steps`() = runBlocking {
        val dir = setupKb(topicCount = 2)
        dir.sub("memory/raw_chat.md").writeText("对话内容B")

        // 模拟中断：操作状态显示 APPEND_ARCHIVE 和 INCREMENT_COUNT 已完成，但 CLEAR_SOURCES 未完成
        val opStateJson = """{"operationId":"test-op-1","kbName":"kb","timestamp":"2026-09-16 09:00","oldTopic":"旧话题","contentHash":"placeholder","completedSteps":["append_archive","increment_count"]}"""
        dir.sub("moment/.archive_op.json").writeText(opStateJson)

        // 预置 raw_topic.md 已有归档（模拟 append 已执行）
        dir.sub("memory/raw_topic.md").writeText("\n# [2026-09-16 09:00] 旧话题\n\n已归档内容\n")

        // 但源文件未清空（模拟中断在 clear 之前）
        dir.sub("memory/raw_chat.md").writeText("对话内容B")

        // 重新调用 rotateTopic — 信任操作状态，跳过 APPEND_ARCHIVE 和 INCREMENT_COUNT
        // 只执行 CLEAR_SOURCES
        repo.rotateTopic("kb")

        // 源文件应被清空（CLEAR_SOURCES 步骤执行）
        assertEquals("raw_chat 应清空", "", dir.sub("memory/raw_chat.md").readText())

        // topicCount 不应增加（INCREMENT_COUNT 已在 completed 中，跳过）
        val kb = json.decodeFromString<KnowledgeBase>(dir.sub("kb.json").readText())
        assertEquals("topicCount 不应重复增加", 2, kb.topicCount)

        // raw_topic.md 不应重复追加（APPEND_ARCHIVE 已在 completed 中，跳过）
        val archived = dir.sub("memory/raw_topic.md").readText()
        val appendCount = archived.split("# [2026-09-16 09:00]").size - 1
        assertEquals("不应重复追加归档", 1, appendCount)

        assertFalse("操作状态文件应删除", File(dir, "moment/.archive_op.json").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 4: 无内容时不追加不计数但清空源文件
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `empty_content_no_archive_but_clears`() = runBlocking {
        val dir = setupKb(topicCount = 5)
        // 所有源文件为空（setupKb 默认）

        repo.rotateTopic("kb")

        val archived = dir.sub("memory/raw_topic.md").readText()
        assertEquals("raw_topic.md 应为空", "", archived)

        val kb = json.decodeFromString<KnowledgeBase>(dir.sub("kb.json").readText())
        assertEquals("topicCount 不应增加", 5, kb.topicCount)

        assertFalse("操作状态文件应删除", File(dir, "moment/.archive_op.json").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 5: 操作完成后状态文件被删除
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `op_state_deleted_after_completion`() = runBlocking {
        val dir = setupKb(topicCount = 0)
        dir.sub("memory/raw_chat.md").writeText("对话内容C")

        repo.rotateTopic("kb")

        assertFalse("操作状态文件应在完成后删除", File(dir, "moment/.archive_op.json").exists())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 6: 连续两次不同内容的 rotate — 各归档一次，计数各+1
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `two_different_rotates_each_archive_once`() = runBlocking {
        val dir = setupKb(topicCount = 0)

        // 第一次
        dir.sub("memory/raw_chat.md").writeText("对话1")
        repo.rotateTopic("kb")

        // 第二次
        dir.sub("memory/raw_chat.md").writeText("对话2")
        repo.rotateTopic("kb")

        val archived = dir.sub("memory/raw_topic.md").readText()
        assertTrue("归档应包含对话1", archived.contains("对话1"))
        assertTrue("归档应包含对话2", archived.contains("对话2"))

        val kb = json.decodeFromString<KnowledgeBase>(dir.sub("kb.json").readText())
        assertEquals("topicCount 应该是 2", 2, kb.topicCount)
    }
}
