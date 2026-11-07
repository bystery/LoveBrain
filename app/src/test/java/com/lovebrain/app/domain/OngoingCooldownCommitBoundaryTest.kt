package com.lovebrain.app.domain

import android.content.Context
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.util.TimeFmt
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
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 审计 #19 回归：**读取/准备请求 ≠ 确认一次真实状态变化**。
 *
 * 旧形状：[OngoingContextSelector.selectForInjection] 在 Prompt 构造时就把
 * lastInjectedTurn / lastEvidenceTurn 写进 moment/ongoing_cooldown.json——
 * 用户生成失败、取消或改写消息时，从未确认的输入已经提前推进了持久状态。
 *
 * 修后形状（全部走真实 [KnowledgeRepository] 落盘语义，临时目录，
 * 同 TopicSwitchHardeningTest 口径）：
 *  1. Prompt 准备（选择）只暂存 pending，不写冷却文件——取消/失败（不调 record）→
 *     冷却与证据状态不推进；
 *  2. [TopicRecorder.record] 提交成功（确认事务点）→ pending 按轮次消息指纹消费落盘，
 *     注入与证据两条时间线一起推进；
 *  3. 提交前用户改写消息 → 指纹失配 → 陈旧暂存不被消费，冷却文件保持不动。
 */
class OngoingCooldownCommitBoundaryTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private val nowMs = TimeFmt.parse("2026-09-24 09:00")
    private val clock = FixedClock(startMs = nowMs)

    private fun repo(): KnowledgeRepository = KnowledgeRepository(
        knowledgeRoot = root,
        securePrefs = mockk<SecurePrefs>(relaxed = true),
        context = mockk<Context>(relaxed = true),
        appScope = appScope
    )

    private fun recorder(): TopicRecorder = TopicRecorder(repo(), RoundCommitJournal(repo()), clock)

    private fun selector(): OngoingContextSelector = OngoingContextSelector(repo(), clock)

    @Before
    fun setUp() {
        root = Files.createTempDirectory("s4_cooldown_boundary").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        setupKb()
    }

    @After
    fun tearDown() {
        OngoingCooldownStore.clearPendingForTest()
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun kbDir(): File = File(root, "kb").apply { mkdirs() }

    private fun File.sub(relativePath: String): File = File(this, relativePath).apply {
        parentFile?.mkdirs()
    }

    private fun setupKb() {
        val dir = kbDir()
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-24T09:00:00+08:00")
            ),
            Charsets.UTF_8
        )
        dir.sub("moment").mkdirs()
        dir.sub("memory").mkdirs()
        dir.sub("moment/scene.md").writeText("")
        dir.sub("moment/recent.md").writeText("")
        dir.sub("moment/topic.md").writeText("")
        dir.sub("moment/plan.md").writeText(
            "# 事项计划\n\n## 进行中\n周末见面 | 进行中 | [2026-09-24 08:00]约定（当前）\n\n## 已结束\n"
        )
        dir.sub("memory/raw_scene.md").writeText("")
        dir.sub("memory/raw_chat.md").writeText("")
        dir.sub("memory/raw_topic.md").writeText("")
        dir.sub("memory/lessons.md").writeText("")
    }

    private fun cooldownFile(): File = File(kbDir(), "moment/ongoing_cooldown.json")

    private fun messages(content: String = "我们周末见面吧"): List<ChatMessage> = listOf(
        ChatMessage(id = "msg-0", role = ChatMessage.Role.HER, content = content)
    )

    /** Prompt 准备（与 PromptBuilder.selectOngoingForInjection 同构） */
    private suspend fun preparePrompt(content: String = "我们周末见面吧", turn: Int = 3) {
        selector().selectForInjection(
            "kb",
            OngoingContextSelector.SelectionContext(
                messages = messages(content),
                currentTurn = turn,
                currentTime = "2026-09-24 09:00"
            )
        )
    }

    // ─── 1. 准备 ≠ 提交：取消/失败不推进 ────────────────────────────

    @Test
    fun `prompt preparation stages pending but never writes cooldown`() = runBlocking {
        preparePrompt()

        assertTrue("关键词命中的事项本轮应被注入（前置：pending 确实产生了）",
            OngoingCooldownStore.peek("kb")?.injectedNames?.contains("周末见面") == true)
        assertFalse("Prompt 构造不许写冷却盘（审计 #19：准备 ≠ 确认）", cooldownFile().exists())
    }

    @Test
    fun `generation failure or cancel before commit never advances cooldown or evidence`() = runBlocking {
        // 模拟"生成失败/用户取消"：只做 Prompt 准备，永远不走 record。
        // 随后一个不相关轮次正常提交——上一轮的未确认输入不得借道推进。
        preparePrompt()
        assertFalse(cooldownFile().exists())

        val nextRoundMessages = listOf(
            ChatMessage(id = "msg-x0", role = ChatMessage.Role.HER, content = "今天吃什么")
        )
        recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = nextRoundMessages,
            scheme = null,
            topicStatus = "same",
            topicLabel = "日常对话"
        )

        assertFalse("未确认轮的暂存不得被别的轮消费落盘", cooldownFile().exists())
    }

    // ─── 2. 确认事务点才推进 ────────────────────────────────────────

    @Test
    fun `confirmed commit advances both cooldown and evidence timelines`() = runBlocking {
        preparePrompt()
        assertFalse(cooldownFile().exists())

        val rotated = recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages(),                       // 同一轮冻结快照 → 指纹匹配
            scheme = null,
            topicStatus = "same",
            topicLabel = "日常对话"
        )
        assertFalse("话题没切换，但本轮已确认提交", rotated)

        assertTrue("确认提交后冷却文件要落盘", cooldownFile().exists())
        val json = cooldownFile().readText()
        assertTrue("注入时间线要推进到本轮：\n$json", json.contains("周末见面"))
        assertTrue("lastInjectedTurn 要写本轮轮次 3：\n$json", json.contains("\"lastInjectedTurn\":3"))
        assertTrue("真实消息证据要推进 lastEvidenceTurn：\n$json", json.contains("\"lastEvidenceTurn\":3"))
        assertFalse("暂存消费后要清空（同轮重提交不得重复推进）", OngoingCooldownStore.peek("kb") != null)
    }

    // ─── 3. 改写消息 → 指纹失配 → 不推进 ────────────────────────────

    @Test
    fun `edited message before commit leaves cooldown untouched`() = runBlocking {
        preparePrompt()  // 用"我们周末见面吧"做的选择暂存
        val rotated = recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages("今天吃什么"),           // 用户改写后的消息 → 指纹失配
            scheme = null,
            topicStatus = "same",
            topicLabel = "日常对话"
        )
        assertFalse(rotated)
        assertFalse("改写后的提交不得消费旧暂存（审计 #19）", cooldownFile().exists())
    }

    // ─── 4. 已确认后的再选择只暂存，不重复写盘 ──────────────────────

    @Test
    fun `re-selection after commit only stages until the next confirmed commit`() = runBlocking {
        preparePrompt(turn = 3)
        recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages(),
            scheme = null,
            topicStatus = "same",
            topicLabel = "日常对话"
        )
        val committed = cooldownFile().readText()
        assertTrue(committed.contains("\"lastInjectedTurn\":3"))

        // 下一轮再次准备 Prompt（未提交）：冷却文件必须保持上一轮确认值
        preparePrompt(turn = 4)
        assertEquals("未确认的选择不许推进冷却盘", committed, cooldownFile().readText())
        assertTrue("新一轮暂存要在场等待确认",
            OngoingCooldownStore.peek("kb")?.injectedNames?.contains("周末见面") == true)
    }
}
