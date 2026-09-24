package com.lovebrain.app.domain

import android.content.Context
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.OngoingItem
import com.lovebrain.app.model.SceneFact
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * S2-04 round commit 故障注入矩阵。
 *
 * 指导书 §8.2.7："对 topic/recent/scene/plan/count 每个写入边界故障注入，
 * 重启后校验 exactly-once 最终状态"。
 * 口径按 2026-09-24 独立复核 P0-05 修正：这里能保证的是**最终状态收敛**，
 * effect 的执行次数是至少一次；两种说法分别由
 * `every write boundary survives interruption with a converged final state` 和
 * `an effect that landed but lost its watermark runs again on recovery` 钉住。
 *
 * 做法：对真实 [KnowledgeRepository]（临时目录）包一层 mockk 代理，
 * 让某一个投影对应的落盘调用抛异常 → 该轮中断，journal 留在 PREPARED/WRITING；
 * 然后换一套全新组件调用 [TopicRecorder.recoverIfNeeded]，模拟进程重启后打开知识库。
 *
 * 断言口径：把整个知识库目录树归一化（抹掉时间戳）后，
 * 与"一次跑通、无故障"的基线目录树逐文件比较。
 * 中断-恢复不允许多出一份归档、多一条事实、多一个事项或多一次计数。
 */
class RoundCommitJournalTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private val fault = RuntimeException("injected write failure")

    /** 六个写入边界 */
    private enum class Boundary { ROTATE, TOPIC_SET, RECENT, SCENE, PLAN, COUNT }

    @Before
    fun setUp() {
        root = freshRoot()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private fun freshRoot(): File = Files.createTempDirectory("round_commit").toFile()

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    // ─── 夹具 ───────────────────────────────────────────────────────

    private fun repo(): KnowledgeRepository = KnowledgeRepository(
        knowledgeRoot = root,
        securePrefs = mockk<SecurePrefs>(relaxed = true),
        context = mockk<Context>(relaxed = true),
        appScope = appScope
    )

    /**
     * 让代理把 [boundary] 对应的那一次落盘改成抛异常。
     *
     * 只拦截 TopicRecorder 直接调用的公开方法：
     * rotateTopic 内部清理源文件走的是 writeFileUnlocked，不会被 scene/recent 的桩误伤，
     * 因此"rotate 之前"和"scene 之前"是两个可以区分开的崩溃点。
     */
    private fun faultyRepo(boundary: Boundary): KnowledgeRepository {
        val real = repo()
        val spy = spyk(real)
        when (boundary) {
            Boundary.ROTATE -> coEvery { spy.rotateTopic("kb") } throws fault
            Boundary.TOPIC_SET -> coEvery { spy.setCurrentTopic("kb", any()) } throws fault
            Boundary.RECENT -> coEvery { spy.writeFile("kb", "moment/recent.md", any()) } throws fault
            Boundary.SCENE -> coEvery { spy.writeFile("kb", "moment/scene.md", any()) } throws fault
            Boundary.PLAN -> coEvery { spy.writeFile("kb", "moment/plan.md", any()) } throws fault
            Boundary.COUNT -> coEvery { spy.incrementTurnCountBy("kb", any()) } throws fault
        }
        return spy
    }

    private fun File.sub(relativePath: String): File = File(this, relativePath).apply {
        parentFile?.mkdirs()
    }

    private fun setupKb() {
        val dir = File(root, "kb").apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = "kb", displayName = "kb", updatedAt = "2026-09-16T09:00:00+08:00")
            ),
            Charsets.UTF_8
        )
        dir.sub("moment").mkdirs()
        dir.sub("memory").mkdirs()
        dir.sub("understand").mkdirs()
        dir.sub("moment/scene.md").writeText("")
        // 先放一轮旧对话：rotateTopic 只在"有内容可归档"时才真正写 raw_topic.md，
        // 空知识库的归档是 no-op，故障矩阵就测不到重复 rotate。
        dir.sub("moment/recent.md").writeText(
            "- [2026-09-16 08:00]\n<!-- round:msgIds:old-0 -->\n她：上次说起的安排\n"
        )
        dir.sub("moment/topic.md").writeText("- [2026-09-16 09:00] 正在聊：旧话题")
        dir.sub("moment/plan.md").writeText("# 事项计划\n\n## 进行中\n\n## 已结束\n")
        dir.sub("memory/raw_scene.md").writeText("")
        dir.sub("memory/raw_chat.md").writeText("")
        dir.sub("memory/raw_topic.md").writeText("")
        dir.sub("memory/lessons.md").writeText("")
        dir.sub("memory/counseling_log.md").writeText("")
        dir.sub("understand/me.md").writeText("")
        dir.sub("understand/her.md").writeText("")
        dir.sub("understand/warmth.md").writeText("")
    }

    private fun messages(): List<ChatMessage> = listOf(
        ChatMessage(id = "msg-0", role = ChatMessage.Role.HER, content = "下周我要去考雅思了"),
        ChatMessage(id = "msg-1", role = ChatMessage.Role.ME, content = "需要我陪你练习口语吗")
    )

    /** 一轮完整变更：话题切换 + recent + scene + plan + count 全部命中 */
    private suspend fun recordRound(recorder: TopicRecorder): Boolean = recorder.record(
        kb = KnowledgeBase(name = "kb", displayName = "kb"),
        messages = messages(),
        scheme = null,
        topicStatus = "new",
        topicLabel = "雅思考试",
        sceneFacts = listOf(SceneFact(text = "她下周考雅思", sourceIds = listOf("msg-0"))),
        ongoing = listOf(
            OngoingItem(
                name = "雅思备考",
                status = "新出现",
                state = "下周开考",
                itemId = "item-ielts",
                sourceIds = listOf("msg-0")
            )
        )
    )

    /** 目录树快照：相对路径 → 归一化内容 */
    private fun snapshot(): Map<String, String> {
        val base = File(root, "kb")
        val files = base.walkTopDown().filter { it.isFile }.toList()
        return files.associate { f ->
            f.relativeTo(base).path.replace('\\', '/') to f.readText(Charsets.UTF_8).normalized()
        }
    }

    private fun String.normalized(): String = this
        .replace(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"), "TS")
        .replace(Regex("\"updatedAt\"\\s*:\\s*\"[^\"]*\""), "\"updatedAt\":\"ISO\"")
        .replace(Regex("\\b\\d{13}\\b"), "EPOCH")

    // ─── 故障矩阵 ───────────────────────────────────────────────────

    /**
     * 对每个写入边界：中途失败 → 恢复 → 最终状态必须与一次跑通完全一致。
     *
     * 口径说明（独立复核 P0-05）：这里收敛的是**最终状态**，不是 effect 的执行次数。
     * effect 先落盘、水位后更新，所以崩在两者之间时该投影会被重跑一次；
     * 那一次重跑由下面的
     * `an effect that landed but lost its watermark runs again on recovery` 钉住。
     *
     * 这条断言同时封住旧实现的几个具体缺陷：
     * - 恢复时把 scene 的 sourceIds 写成 emptyList（语义被改变）
     * - 恢复时把 ongoing 降级成只有 name 的伪事项
     * - "已 rotate、未 setCurrentTopic" 边界重复 rotate
     * - turn count 靠 recent marker 反推，"recent 刚由恢复写入"时漏加
     */
    @Test
    fun `every write boundary survives interruption with a converged final state`() {
        setupKb()
        val baseline = runBlocking {
            val r = repo()
            val recorder = TopicRecorder(r, RoundCommitJournal(r))
            assertTrue("baseline round must rotate the topic", recordRound(recorder))
            snapshot()
        }
        check(baseline.isNotEmpty()) { "baseline snapshot must not be empty" }
        check(baseline.keys.contains("moment/recent.md")) { "baseline must contain recent.md: ${baseline.keys}" }

        for (boundary in Boundary.values()) {
            root.deleteRecursively()
            root = freshRoot()
            setupKb()

            val brokenRepo = faultyRepo(boundary)
            val brokenRecorder = TopicRecorder(brokenRepo, RoundCommitJournal(brokenRepo))
            val thrown = runCatching { runBlocking { recordRound(brokenRecorder) } }.exceptionOrNull()
            assertNotNull("interruption at $boundary must fail the round", thrown)
            assertTrue(
                "journal must be left behind after failing at $boundary",
                File(root, "kb/moment/.round_commit_journal.json").exists()
            )

            // 模拟进程重启：全新组件 + 恢复入口
            val freshRepo = repo()
            runBlocking { TopicRecorder(freshRepo, RoundCommitJournal(freshRepo)).recoverIfNeeded("kb") }

            assertSameTree(
                "recovering from interruption at $boundary must reproduce the uninterrupted state",
                baseline,
                snapshot()
            )
            assertFalse(
                "journal must be cleared after recovery from $boundary",
                File(root, "kb/moment/.round_commit_journal.json").exists()
            )
        }
    }

    /** rotate 单独有自己的水位：崩溃在 rotate 之后、写标签之前，恢复时不得再 rotate 一次 */
    @Test
    fun `rotate is never repeated when the crash happens after it`() {
        setupKb()
        val brokenRepo = faultyRepo(Boundary.TOPIC_SET)
        runCatching {
            runBlocking { recordRound(TopicRecorder(brokenRepo, RoundCommitJournal(brokenRepo))) }
        }
        // 归档已发生一次
        val afterCrash = File(root, "kb/memory/raw_topic.md").readText()
        assertEquals("rotate must have archived exactly once", 1, Regex("^# \\[", RegexOption.MULTILINE).findAll(afterCrash).count())

        val freshRepo = repo()
        runBlocking { TopicRecorder(freshRepo, RoundCommitJournal(freshRepo)).recoverIfNeeded("kb") }

        val afterRecovery = File(root, "kb/memory/raw_topic.md").readText()
        assertEquals(
            "recovery must not archive a second time",
            1, Regex("^# \\[", RegexOption.MULTILINE).findAll(afterRecovery).count()
        )
        coVerify(atMost = 1) { brokenRepo.rotateTopic("kb") }
    }

    // ─── 幂等与身份 ─────────────────────────────────────────────────

    /**
     * 独立复核 P0-05 第三条：注入"effect 已经写成、水位落盘失败"，
     * 而不是只测 effect 自己抛异常。
     *
     * 顺序是 effect → watermark，所以这个窗口被截断时磁盘上记着"这一投影没做过"，
     * 恢复必然把它再跑一遍。本测试不掩盖该行为，而是把它钉成合同：
     * 提交语义是 **at-least-once + idempotent convergence**，不是 exactly-once。
     */
    @Test
    fun `an effect that landed but lost its watermark runs again on recovery`() {
        setupKb()
        val runs = ConcurrentHashMap<String, Int>()
        fun bump(projection: String) = runs.merge(projection, 1) { a, b -> a + b }
        val body: suspend RoundCommitJournal.Tx.(RoundCommitJournal.RoundCommitEvent) -> Unit = {
            apply(RoundCommitJournal.PROJ_RECENT) { bump(RoundCommitJournal.PROJ_RECENT) }
            apply(RoundCommitJournal.PROJ_SCENE) { bump(RoundCommitJournal.PROJ_SCENE) }
            apply(RoundCommitJournal.PROJ_PLAN) { bump(RoundCommitJournal.PROJ_PLAN) }
        }

        // 第 2 次水位落盘失败 = scene 的 effect 已写成，但它的水位没留住
        val broken = watermarkFailingRepo(failOnCall = 2)
        val thrown = runCatching {
            runBlocking { RoundCommitJournal(broken).commit("kb", event("round-wm"), body) }
        }.exceptionOrNull()
        assertNotNull("losing a watermark write must fail the round", thrown)
        assertEquals("recent is before the cut", 1, runs[RoundCommitJournal.PROJ_RECENT])
        assertEquals("scene's effect landed before its watermark failed", 1, runs[RoundCommitJournal.PROJ_SCENE])
        assertNull("plan never started", runs[RoundCommitJournal.PROJ_PLAN])
        assertTrue("journal must survive for roll-forward", journalFile().exists())

        // 模拟进程重启：全新组件按磁盘上的水位补齐
        val fresh = repo()
        val journal = RoundCommitJournal(fresh)
        val recovered = runBlocking { journal.recover("kb", body) }

        assertTrue("a WRITING journal must be recoverable", recovered)
        assertEquals(
            "the watermark that did land must spare recent a second effect",
            1, runs[RoundCommitJournal.PROJ_RECENT]
        )
        assertEquals(
            "scene is at-least-once: its lost watermark replays the effect",
            2, runs[RoundCommitJournal.PROJ_SCENE]
        )
        assertEquals(1, runs[RoundCommitJournal.PROJ_PLAN])
        assertTrue(
            "the round must end up committed",
            runBlocking { journal.isRoundCommitted("kb", "round-wm") }
        )
        assertFalse("journal must be cleared after roll-forward", journalFile().exists())
    }

    /**
     * 独立复核 P0-05 第二条：两个协程同时提交同一个 round。
     *
     * `TopicRecorder.record()` 在事务锁**外面**查过一次提交清单，两个协程都会读到
     * "没提交"；只有 [RoundCommitJournal.commit] 拿到 txMutex 之后的重读才作数。
     * 锁内重读补上之前这条是红的——旧实现会让整个 round 的 effect 跑第二遍。
     *
     * 并发用两个真线程各跑一个 runBlocking 制造（而不是 async）：txMutex 本来就是跨线程
     * 的挂起锁，这样能覆盖同一条竞争路径，又不会让一个 runBlocking 的事件循环
     * 在另一个协程还没收尾时就被拆掉——那种拆法会在 kotlinx.coroutines 的
     * DefaultExecutor 上留下 ClassCastException，把同一个 JVM 里后面的测试毒成
     * "uncaught exceptions before the test started"。
     */
    @Test
    fun `two coroutines committing the same round write it once`() {
        setupKb()
        val r = repo()
        val journal = RoundCommitJournal(r)
        val effectRuns = AtomicInteger(0)
        val event = event("round-race")
        val gate = java.util.concurrent.CountDownLatch(1)
        val outcomes = java.util.concurrent.ConcurrentLinkedQueue<RoundCommitJournal.CommitOutcome<Int>>()
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()

        val threads = (1..2).map {
            Thread {
                try {
                    gate.await()
                    outcomes.add(
                        runBlocking { journal.commit("kb", event) { effectRuns.incrementAndGet() } }
                    )
                } catch (t: Throwable) {
                    failures.add(t)
                }
            }.apply { isDaemon = false; start() }
        }
        gate.countDown()
        threads.forEach { it.join(30_000L) }
        check(threads.all { !it.isAlive }) { "a committing thread hung" }

        assertTrue("neither thread may fail: ${failures.joinToString()}", failures.isEmpty())
        assertEquals("both coroutines must be accounted for", 2, outcomes.size)
        assertEquals(
            "exactly one coroutine may run the round",
            1, outcomes.count { it is RoundCommitJournal.CommitOutcome.Committed<*> }
        )
        assertEquals(
            "the loser must get a typed AlreadyCommitted instead of writing again",
            1, outcomes.count { it is RoundCommitJournal.CommitOutcome.AlreadyCommitted }
        )
        assertEquals("effects must run once in total", 1, effectRuns.get())
        assertEquals(
            "committedRounds must not hold the round twice",
            listOf("round-race"), runBlocking { journal.readState("kb") }.committedRounds
        )
        assertFalse("journal must be gone", journalFile().exists())
    }

    /**
     * 让第 [failOnCall] 次**水位文件**落盘失败。
     *
     * 与 [faultyRepo] 的区别：这里失败的不是投影的 effect，而是 journal 自己的
     * 记账写入——正是 P0-05 要求单独注入的那个窗口。
     */
    private fun watermarkFailingRepo(failOnCall: Int): KnowledgeRepository {
        val real = repo()
        val spy = spyk(real)
        val calls = AtomicInteger(0)
        coEvery { spy.writeFile("kb", RoundCommitJournal.STATE_PATH, any()) } coAnswers {
            if (calls.incrementAndGet() == failOnCall) throw fault
            // 显式转给没被拦截的那个实例。spyk 的 callOriginal() 在 suspend 函数上会把
            // continuation 留在调用方的事件循环里；实测会让同一个 JVM 后面开跑的
            // runTest 报 "uncaught exceptions before the test started"（本机复现过一次）。
            real.writeFile(firstArg(), secondArg(), thirdArg())
        }
        return spy
    }

    private fun journalFile(): File = File(root, "kb/moment/.round_commit_journal.json")

    /** 同一轮重试（消息 ID 集合相同）只产生一次写入 */
    @Test
    fun `retrying the same round does not duplicate any projection`() {
        setupKb()
        val r = repo()
        val recorder = TopicRecorder(r, RoundCommitJournal(r))

        assertTrue(runBlocking { recordRound(recorder) })
        val afterFirst = snapshot()

        val second = runBlocking { recordRound(recorder) }
        assertFalse("second call must be recognised as already committed", second)
        assertEquals(afterFirst, snapshot())

        val recent = File(root, "kb/moment/recent.md").readText()
        assertEquals("recent.md must hold exactly one round block", 1, Regex("^- \\[").findAll(recent).count())
        assertEquals("turn count must advance once", 1, turnCountOf(File(root, "kb/kb.json").readText()))
        val archive = File(root, "kb/memory/raw_topic.md").readText()
        assertEquals("topic must be archived once", 1, Regex("^# \\[", RegexOption.MULTILINE).findAll(archive).count())
    }

    /** roundId 必须由输入身份派生，而不是每次随机 UUID */
    @Test
    fun `roundId is derived from round identity and stable across retries`() {
        val a = RoundCommitJournal.stableRoundId("kb", "msg-0,msg-1")
        assertEquals(a, RoundCommitJournal.stableRoundId("kb", "msg-0,msg-1"))
        assertNotEquals(a, RoundCommitJournal.stableRoundId("kb", "msg-0,msg-2"))
        assertNotEquals(a, RoundCommitJournal.stableRoundId("kb2", "msg-0,msg-1"))
    }

    /** 无消息可标识时，用事件内容兜底，仍然是确定性的 */
    @Test
    fun `roundId falls back to content hash when there are no message ids`() {
        val a = RoundCommitJournal.stableRoundId("kb", "", "entry-text")
        assertEquals(a, RoundCommitJournal.stableRoundId("kb", "", "entry-text"))
        assertNotEquals(a, RoundCommitJournal.stableRoundId("kb", "", "other-text"))
    }

    // ─── 事务护栏 ───────────────────────────────────────────────────

    /** 上一轮没收敛时，不允许开新事务把 journal 覆盖掉（旧实现直接丢账） */
    @Test
    fun `starting a different round while one is in flight fails instead of overwriting the journal`() {
        setupKb()
        val r = repo()
        val journal = RoundCommitJournal(r)
        runBlocking { journal.commit("kb", event("round-a")) { } }

        val broken = faultyRepo(Boundary.COUNT)
        val brokenJournal = RoundCommitJournal(broken)
        runCatching { runBlocking { brokenJournal.commit("kb", event("round-b")) { throw fault } } }
        assertTrue(File(root, "kb/moment/.round_commit_journal.json").exists())

        val error = runCatching { runBlocking { journal.commit("kb", event("round-c")) { } } }.exceptionOrNull()
        assertTrue(
            "expected an in-flight guard, got: $error",
            error is IllegalStateException && error.message?.contains("in flight") == true
        )
        // 在途事务不被破坏，仍可恢复
        assertEquals("round-b", runBlocking { journal.recoverPending("kb") }?.roundId)
    }

    /** 重跑同一轮不受在途护栏阻塞——护栏只拦"另一轮" */
    @Test
    fun `restarting the same in-flight round is allowed so it can converge`() {
        setupKb()
        val broken = faultyRepo(Boundary.COUNT)
        val brokenJournal = RoundCommitJournal(broken)
        runCatching { runBlocking { brokenJournal.commit("kb", event("round-same")) { throw fault } } }
        val r = repo()
        val journal = RoundCommitJournal(r)
        val result = runCatching { runBlocking { journal.commit("kb", event("round-same")) { } } }
        assertTrue("same round must be replayable, got ${result.exceptionOrNull()}", result.isSuccess)
    }

    /** 取消也必须保留 journal，不能当作提交完成 */
    @Test
    fun `cancellation mid-round keeps the journal for roll-forward`() {
        setupKb()
        val r = repo()
        val journal = RoundCommitJournal(r)
        runCatching {
            runBlocking {
                journal.commit("kb", event("cancelled")) {
                    throw kotlinx.coroutines.CancellationException("cancelled")
                }
            }
        }
        assertTrue(
            "journal must survive cancellation so the round is not silently lost",
            File(root, "kb/moment/.round_commit_journal.json").exists()
        )
    }

    /** 损坏的 journal 不能丢账：先留痕，再清理，并且不阻塞后续提交 */
    @Test
    fun `corrupt journal is archived to a corruption log instead of vanishing`() {
        setupKb()
        val journalFile = File(root, "kb/moment/.round_commit_journal.json").apply { parentFile?.mkdirs() }
        journalFile.writeText("{ this is not json", Charsets.UTF_8)

        val journal = RoundCommitJournal(repo())
        assertNull(runBlocking { journal.recoverPending("kb") })
        assertTrue(
            "corrupt payload must be preserved for forensics",
            File(root, "kb/moment/.round_commit_corrupt.log").exists()
        )
        assertFalse(journalFile.exists())
    }

    // ─── payload 保真 ───────────────────────────────────────────────

    /**
     * WAL 必须原样保留 recentEntry 的转义结构。
     *
     * 旧实现手写 parser：遇到 `\` 时先把反斜杠丢掉、再对结果做一次 unescape，
     * 于是 `\n` 变成字面量 `n`，recent.md 的换行与 marker 结构全被破坏。
     */
    @Test
    fun `journal round-trips escaped content through disk without losing newlines or backslashes`() {
        setupKb()
        val tricky = "- [2026-09-16 09:00]\n<!-- round:msgIds:msg-0 -->\n" +
            "她：路径 C:\\new\\node 里换行\n带\"引号\"和 \\n 字面量\n尾随反斜杠\\\\"
        val ev = event("escape-disk").copy(recentEntry = tricky)

        val broken = faultyRepo(Boundary.COUNT)
        runCatching {
            runBlocking { RoundCommitJournal(broken).commit("kb", ev) { throw fault } }
        }

        val recovered = runBlocking { RoundCommitJournal(repo()).recoverPending("kb") }
        assertNotNull("journal must still hold the round", recovered)
        assertEquals("recentEntry must survive serialisation", tricky, recovered!!.recentEntry)

        // 收敛后 recent.md 里的 marker 结构必须完整
        val fresh = repo()
        runBlocking { TopicRecorder(fresh, RoundCommitJournal(fresh)).recoverIfNeeded("kb") }
        val recent = File(root, "kb/moment/recent.md").readText()
        assertTrue("marker must survive", recent.contains("<!-- round:msgIds:msg-0 -->"))
        assertTrue("literal backslash path must survive", recent.contains("C:\\new\\node"))
    }

    /** 恢复后的 ongoing 必须保留 itemId/status/state/sourceIds，不能退化成只有名字 */
    @Test
    fun `replayed ongoing items keep itemId status state and source ids`() {
        setupKb()
        val broken = faultyRepo(Boundary.PLAN)
        runCatching { runBlocking { recordRound(TopicRecorder(broken, RoundCommitJournal(broken))) } }

        val recoveredEvent = runBlocking { RoundCommitJournal(repo()).recoverPending("kb") }
        assertNotNull("round must still be in flight", recoveredEvent)
        val item = recoveredEvent!!.ongoing.single()
        assertEquals("item-ielts", item.itemId)
        assertEquals("新出现", item.status)
        assertEquals("下周开考", item.state)
        assertEquals(listOf("msg-0"), item.sourceIds)

        val fresh = repo()
        runBlocking { TopicRecorder(fresh, RoundCommitJournal(fresh)).recoverIfNeeded("kb") }
        val plan = File(root, "kb/moment/plan.md").readText()
        assertTrue("plan.md must hold the replayed item", plan.contains("雅思备考"))
        assertTrue("plan.md must hold the item's own state node", plan.contains("下周开考"))
    }

    /** 恢复后的 scene 事实必须继续带着真实来源与归属 */
    @Test
    fun `replayed scene facts keep their source ids`() {
        setupKb()
        val broken = faultyRepo(Boundary.SCENE)
        runCatching { runBlocking { recordRound(TopicRecorder(broken, RoundCommitJournal(broken))) } }

        val recoveredEvent = runBlocking { RoundCommitJournal(repo()).recoverPending("kb") }
        val fact = recoveredEvent!!.sceneFacts.single()
        assertEquals(listOf("msg-0"), fact.sourceIds)
        assertTrue("frozen snapshot must be journaled too", recoveredEvent.frozenMessages.isNotEmpty())

        val fresh = repo()
        runBlocking { TopicRecorder(fresh, RoundCommitJournal(fresh)).recoverIfNeeded("kb") }
        val scene = File(root, "kb/moment/scene.md").readText()
        assertTrue("replayed fact must keep its source id", scene.contains("src=msg-0"))
    }

    /** turn count 增量按事件里记录的值使用，恢复时不写死 +1 */
    @Test
    fun `turn count honours the journaled increment value`() {
        setupKb()
        val r = repo()
        val journal = RoundCommitJournal(r)
        val ev = event("two-steps").copy(turnCountIncrement = 2)
        runBlocking {
            journal.commit("kb", ev) {
                apply(RoundCommitJournal.PROJ_COUNT) { r.incrementTurnCountBy("kb", ev.turnCountIncrement) }
            }
        }
        assertEquals(2, turnCountOf(File(root, "kb/kb.json").readText()))
    }

    /** WRITING 阶段必须真的被写出来，否则无法区分"崩在 PREPARED"与"崩在 target 中途" */
    @Test
    fun `journal records the writing stage while targets are being applied`() {
        setupKb()
        val broken = faultyRepo(Boundary.COUNT)
        runCatching {
            runBlocking {
                RoundCommitJournal(broken).commit("kb", event("staged")) {
                    throw RuntimeException("stop right after WRITING")
                }
            }
        }
        val raw = File(root, "kb/moment/.round_commit_journal.json").readText()
        assertTrue("stage WRITING must be persisted, got: $raw", raw.contains("\"WRITING\""))
    }

    /** 水位落盘，重启后仍然认得哪些投影已生效 */
    @Test
    fun `projection watermarks are durable across process restarts`() {
        setupKb()
        val r = repo()
        val journal = RoundCommitJournal(r)
        runBlocking {
            journal.commit("kb", event("watermark")) { apply(RoundCommitJournal.PROJ_RECENT) { } }
        }
        val reopened = RoundCommitJournal(repo())
        val state = runBlocking { reopened.readState("kb") }
        assertEquals("watermark must be readable from disk", "watermark", state.projections["recent"])
        assertTrue("committed round must be registered", runBlocking { reopened.isRoundCommitted("kb", "watermark") })
    }

    /** 已提交轮次清单有上界，不随使用无限膨胀 */
    @Test
    fun `committed round history stays bounded`() {
        setupKb()
        val r = repo()
        val journal = RoundCommitJournal(r)
        runBlocking {
            repeat(300) { i -> journal.commit("kb", event("round-$i")) { } }
        }
        val state = runBlocking { journal.readState("kb") }
        assertTrue(
            "committedRounds must be trimmed, got ${state.committedRounds.size}",
            state.committedRounds.size <= 256
        )
        assertTrue(state.committedRounds.contains("round-299"))
    }

    /** 跨文件幂等判据是提交清单，不是 recent.md 的 HTML marker */
    @Test
    fun `alreadyRecorded is decided by the commit log, not by the recent marker`() {
        setupKb()
        val r = repo()
        val recorder = TopicRecorder(r, RoundCommitJournal(r))
        runBlocking { recordRound(recorder) }

        // 抹掉 recent.md 里的 marker：旧实现据此判定"已记录"，
        // 新实现看提交清单，因此仍必须识别为本轮已提交、不重复写。
        val recentFile = File(root, "kb/moment/recent.md")
        val stripped = recentFile.readText().lines().filterNot { it.startsWith("<!-- round:msgIds:") }
            .joinToString("\n")
        recentFile.writeText(stripped, Charsets.UTF_8)

        val again = runBlocking { recordRound(recorder) }
        assertFalse("commit log must still suppress the duplicate round", again)
        assertEquals("turn count must not advance again", 1, turnCountOf(File(root, "kb/kb.json").readText()))
    }

    private fun event(roundId: String) = RoundCommitJournal.RoundCommitEvent(
        roundId = roundId,
        kbName = "kb",
        inputRevision = 0,
        timestamp = "2026-09-16 09:00",
        topicStatus = "same",
        topicLabel = "雅思考试",
        roundMsgIds = "msg-0,msg-1",
        recentEntry = "- [2026-09-16 09:00]\n<!-- round:msgIds:msg-0,msg-1 -->\n"
    )

    private fun turnCountOf(kbJson: String): Int =
        Regex("\"turnCount\"\\s*:\\s*(\\d+)").find(kbJson)?.groupValues?.get(1)?.toIntOrNull() ?: -1

    /**
     * 目录树逐项比较。
     *
     * 不直接用 assertEquals(Map, Map)：整棵树一起打印时，
     * 失败信息看不出到底是哪个投影多写了一份，逐个文件比才能定位。
     */
    private fun assertSameTree(message: String, expected: Map<String, String>, actual: Map<String, String>) {
        val keys = (expected.keys + actual.keys).sorted()
        val diffs = keys.filter { expected[it] != actual[it] }
        if (diffs.isEmpty()) return
        val detail = diffs.joinToString("\n\n") { key ->
            "### $key\n--- expected ---\n${expected[key] ?: "<missing>"}\n--- actual ---\n${actual[key] ?: "<missing>"}"
        }
        fail("$message\nfiles differing: $diffs\n$detail")
    }
}
