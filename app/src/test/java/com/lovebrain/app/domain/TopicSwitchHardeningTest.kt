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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * F2.1 话题切换加固的端到端回归（refs/version_closeout_20261106/T5_KNOWN_FAILURES_AUDIT.md F2.1）。
 *
 * 钉住三条产品行为，全部走**真实 [KnowledgeRepository]**（临时目录，同 RoundCommitJournalTest 口径），
 * 不用 InMemoryKnowledgePort——因为误判保护与归档完整性都是**落盘语义**，
 * 内存桩复刻 rotate 语义只会测到自己：
 *
 *  1. **单判据不再触发**：模型报 topic_status=new 但同标签、scene 链新鲜、无显式标记
 *     → 不归档、不清空 moment/scene.md 与 moment/recent.md（旧实现 TopicRecorder.kt:123 的误清空输入）；
 *  2. **带佐证触发**：任一佐证信号命中 → 旋转照常发生；
 *  3. **归档完整性**：确认切换后，被清的 scene/recent/raw_chat/raw_scene 内容
 *     在清空前已完整落入 memory/raw_topic.md（生产 rotate 的"先追加归档、后清四个源文件"顺序）。
 */
class TopicSwitchHardeningTest {

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

    @Before
    fun setUp() {
        root = Files.createTempDirectory("f21_gate").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun kbDir(): File = File(root, "kb").apply { mkdirs() }

    private fun File.sub(relativePath: String): File = File(this, relativePath).apply {
        parentFile?.mkdirs()
    }

    /** scene 条目时间字符串：nowMs 往前 offsetMin 分钟（与 FixedClock/TimeFmt 同一时区口径） */
    private fun sceneTime(offsetMin: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(nowMs - offsetMin * 60_000L))

    private fun setupKb(
        sceneOffsetMin: Long = 10,
        topic: String = "午饭",
        sceneFact: String = "她在吃沙拉",
        rawChat: String = "- [2026-09-24 07:00] 我：第一轮对话存档",
        rawScene: String = "- [2026-09-24 06:00] 旧话题：旧事实存档|src=old-0",
        recent: String = "- [2026-09-24 08:30]\n<!-- round:msgIds:old-0 -->\n她：上次说起的安排\n"
    ) {
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
        dir.sub("moment/scene.md").writeText("- [${sceneTime(sceneOffsetMin)}] $topic：$sceneFact|src=m1\n")
        dir.sub("moment/topic.md").writeText("- [${sceneTime(10)}] 正在聊：$topic")
        dir.sub("moment/recent.md").writeText(recent)
        dir.sub("moment/plan.md").writeText("# 事项计划\n\n## 进行中\n\n## 已结束\n")
        dir.sub("memory/raw_chat.md").writeText(rawChat)
        dir.sub("memory/raw_scene.md").writeText(rawScene)
        dir.sub("memory/raw_topic.md").writeText("")
        dir.sub("memory/lessons.md").writeText("")
    }

    private fun messages(vararg contents: Pair<String, ChatMessage.Role>) = contents.mapIndexed { i, (c, r) ->
        ChatMessage(id = "msg-$i", role = r, content = c, timestamp = nowMs)
    }

    private fun read(rel: String): String = File(kbDir(), rel).readText()

    // ─── 1. 单判据不再触发 ──────────────────────────────────────────

    @Test
    fun `same-label new without corroboration keeps scene and recent intact`() = runBlocking {
        setupKb(sceneOffsetMin = 10)
        val rotated = recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages("今天好累啊" to ChatMessage.Role.HER),
            scheme = null,
            topicStatus = "new",          // 模型主张切换……
            topicLabel = "午饭"           // ……但标签没变、scene 链新鲜、无显式标记：旧实现正是在这里误清空
        )
        assertFalse("单判据 new 不许再触发话题旋转", rotated)
        assertTrue("场景记忆必须原样保留", read("moment/scene.md").contains("她在吃沙拉"))
        assertTrue("最近对话必须原样保留", read("moment/recent.md").contains("上次说起的安排"))
        assertTrue("不许产生归档条目", read("memory/raw_topic.md").isBlank())
        assertTrue("当前话题标签保持现状", read("moment/topic.md").contains("正在聊：午饭"))
    }

    // ─── 2. 带佐证触发 ──────────────────────────────────────────────

    @Test
    fun `label change corroborates and rotation proceeds`() = runBlocking {
        setupKb(sceneOffsetMin = 10)
        val rotated = recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages("跟你说个事" to ChatMessage.Role.HER),
            scheme = null,
            topicStatus = "new",
            topicLabel = "雅思考试"       // 与当前标签不同 → LABEL_CHANGE 佐证
        )
        assertTrue("标签变化佐证的切换要照常旋转", rotated)
        assertTrue("归档条目要出现", read("memory/raw_topic.md").contains("## ["))
        assertTrue("话题标签要更新", read("moment/topic.md").contains("雅思考试"))
    }

    @Test
    fun `stale scene corroborates a same-label switch`() = runBlocking {
        setupKb(sceneOffsetMin = 90)  // scene 最新证据 90 分钟前 > 60 分钟阈值 → TIME_GAP
        val rotated = recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages("在吗" to ChatMessage.Role.HER),
            scheme = null,
            topicStatus = "new",
            topicLabel = "午饭"
        )
        assertTrue("时间间隔佐证的切换要照常旋转", rotated)
        assertTrue("切换后此刻场景链要清空（本轮未提供新场景事实）", read("moment/scene.md").isBlank())
    }

    @Test
    fun `explicit user marker corroborates a same-label switch`() = runBlocking {
        setupKb(sceneOffsetMin = 10)
        val rotated = recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages("好了好了，我们换个话题吧" to ChatMessage.Role.HER),
            scheme = null,
            topicStatus = "new",
            topicLabel = "午饭"
        )
        assertTrue("用户显式换话题标记佐证的切换要照常旋转", rotated)
        assertTrue(read("moment/scene.md").isBlank())
    }

    // ─── 3. 归档完整性（确认切换时，被清内容先完整落入 raw_topic.md）──

    @Test
    fun `confirmed rotation archives scene recent raw_chat raw_scene before clearing`() = runBlocking {
        setupKb(sceneOffsetMin = 10)
        val rotated = recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages("跟你说个事" to ChatMessage.Role.HER),
            scheme = null,
            topicStatus = "new",
            topicLabel = "雅思考试"
        )
        assertTrue(rotated)
        val archive = read("memory/raw_topic.md")
        assertTrue("scene.md 内容要先进归档：\n$archive", archive.contains("她在吃沙拉"))
        assertTrue("recent.md 内容要先进归档：\n$archive", archive.contains("上次说起的安排"))
        assertTrue("raw_chat.md 内容要先进归档：\n$archive", archive.contains("第一轮对话存档"))
        assertTrue("raw_scene.md 内容要先进归档：\n$archive", archive.contains("旧事实存档"))
        assertTrue("旧话题名要进归档标题：\n$archive", archive.contains("午饭"))
        // 清空发生在归档之后：scene/raw_chat/raw_scene 此刻为空；
        // recent.md 由投影 3 紧接着写入本轮新条目，但旧轮次必须已经不在
        assertTrue("切换后 moment/scene.md 要清空", read("moment/scene.md").isBlank())
        assertFalse("切换后旧轮次不许留在此刻层", read("moment/recent.md").contains("上次说起的安排"))
        assertTrue("切换后 memory/raw_chat.md 要清空", read("memory/raw_chat.md").isBlank())
        assertTrue("切换后 memory/raw_scene.md 要清空", read("memory/raw_scene.md").isBlank())
    }

    // ─── 4. same 状态完全不受门影响（回归保护）────────────────────

    @Test
    fun `same status never rotates regardless of gate`() = runBlocking {
        setupKb(sceneOffsetMin = 10)
        val rotated = recorder().record(
            kb = KnowledgeBase(name = "kb", displayName = "kb"),
            messages = messages("她刚吃完午饭" to ChatMessage.Role.HER),
            scheme = null,
            topicStatus = "same",
            topicLabel = "午饭"
        )
        assertFalse(rotated)
        assertTrue(read("moment/scene.md").contains("她在吃沙拉"))
    }
}
