package com.lovebrain.app.feature.roundcommit

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ActualSentRecorder] 的判据（复核 §5.2 第 6 步：这一块搬的是**行为**，所以测试也按行为写）。
 *
 * 这里钉的四件事，每一件都对应一段曾经在 ViewModel 里、而 JVM 上量不到的逻辑：
 * · 两种"没能记上"是不同结果（没上下文 → `IO_ERROR`；有上下文但没激活库 → `NO_KB` + 一条面板警告）；
 * · **每次尝试都必须产生一次可观察的跳变**——`StateFlow` 对同一个值不再发射，
 *   少一次重置就是一个把用户关在浮层里的死路（取消与遮罩都是 `enabled = !saving`）；
 * · 同一版本再确认是**替换**不是追加，且 adopt 只加一次；
 * · 取消不是失败：`CancellationException` 原样上抛，不许被转成 `IO_ERROR`。
 *
 * entry 的格式只钉"头注释三件套 + 用户可见那一行"这一层，仓库那一半由
 * `data/ActualSentUpsertTest` 在真文件系统上钉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActualSentRecorderTest {

    private class Harness(
        var context: ActualSentRecorder.AttemptContext? = ActualSentRecorder.AttemptContext("小芳"),
        var versionKey: String? = "v-1",
        var candidate: (String?) -> String? = { null },
        var outcome: (String, String, String?) -> Boolean = { _, _, _ -> true },
        var failure: () -> Throwable? = { null }
    ) {
        val writes = mutableListOf<Triple<String, String, String?>>()
        val warnings = mutableListOf<String>()
        val notices = mutableListOf<String>()
        var adopted = 0
        var logged: Throwable? = null

        val emissions = mutableListOf<ActualSentState>()
        val errors = mutableListOf<Throwable>()
        lateinit var state: kotlinx.coroutines.flow.StateFlow<ActualSentState>
        /** 最后一次 record 返回的句柄——"取消有没有被吞掉"这件事只有从这里看得见 */
        var lastJob: kotlinx.coroutines.Job? = null
        /** recorder 的工作跑在这个父 Job 下（取消那一格为什么不看 children，见它自己的注释） */
        val parent = Job()

        fun recorder(dispatcher: kotlinx.coroutines.CoroutineDispatcher, handler: CoroutineExceptionHandler) =
            ActualSentRecorder(
            scope = CoroutineScope(dispatcher + parent + handler),
            readContext = { context },
            readVersionKey = { versionKey },
            readCandidateText = { key -> candidate(key) },
            writeRecord = { kb, entry, replaces ->
                failure()?.let { throw it }
                writes += Triple(kb, entry, replaces)
                outcome(kb, entry, replaces)
            },
            onWarning = { warnings += it },
            onNotice = { notices += it },
            onAdopted = { adopted++ },
            onError = { logged = it }
        ).also { state = it.state }
    }

    /**
     * 跑 `times` 次同一个动作。
     *
     * 收集器挂在 `backgroundScope` 上而不是测试协程里：`runTest` 会等它，
     * 而 recorder 自己的工作跑在注入进去的那个 scope（带 handler，取消异常要能在外面看见）。
     * 返回的 TestResult 被调用方当语句丢弃——`runTest` 是同步跑到时间耗尽的，断言放在它后面是安全的。
     */
    private fun run(
        h: Harness,
        times: Int = 2,
        body: suspend ActualSentRecorder.() -> kotlinx.coroutines.Job
    ) = runTest {
        val handler = CoroutineExceptionHandler { _, e -> h.errors += e }
        val recorder = h.recorder(StandardTestDispatcher(testScheduler), handler)
        // UNDISPATCHED：订阅必须在第一次写状态之前就成立，否则第一格跳变会被漏掉
        // （这不是形式主义——那些跳变本身就是要钉的东西，漏读会把"有跳变"读成"没跳变"）
        // 收集器用 Unconfined：写一次就要立刻记一次，否则"跳变发生了没有"会被调度器藏起来
        // （这一格钉的正是跳变本身，用 Standard 时值被合并掉，测出来只剩初始那一个）
        backgroundScope.launch(
            kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler),
            start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED
        ) {
            recorder.state.collect { h.emissions += it }
        }
        // 一次尝试跑完再按下一次：用户就是"确认→看到没看到→再确认"这么 sequential 地点的，
        // 而 StateFlow 同值不再发射那条死路正是在这种顺序下发生的
        repeat(times) { h.lastJob = recorder.body(); testScheduler.advanceUntilIdle() }
    }

    @Test
    fun `no context and no active library are two different results`() {
        // 有上下文、库名为空 → NO_KB，并给一条面板警告
        val h1 = Harness(context = ActualSentRecorder.AttemptContext(null))
        run(h1, times = 1) { record("我发出去了") }
        assertEquals(listOf(ActualSentState.IDLE, ActualSentState.NO_KB), h1.emissions)
        assertEquals(listOf("未激活知识库，无法记录已发送消息"), h1.warnings)
        assertTrue("没库就不该写盘", h1.writes.isEmpty())

        // 连上下文都没有 → IO_ERROR，且不发面板警告（这一支是"本轮还没生成过"）
        val h2 = Harness(context = null)
        run(h2, times = 1) { record("我发出去了") }
        assertEquals(listOf(ActualSentState.IDLE, ActualSentState.IO_ERROR), h2.emissions)
        assertTrue("没有上下文时不该给'未激活知识库'这种错话：${h2.warnings}", h2.warnings.isEmpty())
    }

    @Test
    fun `a repeated identical failure still produces a fresh transition`() {
        val h = Harness(outcome = { _, _, _ -> false })
        run(h, times = 3) { record("我发出去了") }
        assertEquals(
            "三次尝试必须各产生一次 IDLE→失败 的跳变；少一次 IDLE，面板就解不开「保存中」",
            listOf(
                ActualSentState.IDLE, ActualSentState.KB_NOT_FOUND,
                ActualSentState.IDLE, ActualSentState.KB_NOT_FOUND,
                ActualSentState.IDLE, ActualSentState.KB_NOT_FOUND
            ),
            h.emissions
        )
        assertEquals(3, h.warnings.size)
        // 正向对照：写失败**不是**取消——句柄不该是 cancelled。
        // 少了这一句，上面那条 isCancelled 就可能是一条"任何异常都成立"的恒真判据。
        assertTrue(
            "三次都是仓库返回 false，属于普通失败：isCancelled=${h.lastJob?.isCancelled}",
            h.lastJob?.isCancelled == false
        )
    }

    @Test
    fun `confirming the same version twice replaces the record and counts adopt once`() {
        val h = Harness()
        run(h, times = 2) { record("我发出去了") }

        assertEquals(2, h.writes.size)
        assertNull("第一次必须是追加", h.writes[0].third)
        assertTrue(
            "第二次必须带着'替换谁'——同一 generation version 不许留下两条记录",
            h.writes[1].third != null && h.writes[1].third == h.writes[0].second
        )
        assertEquals("同一版本再确认不重复计 adopt", 1, h.adopted)
        assertEquals(
            listOf("已记录实际发送的消息", "已更新实际发送记录"),
            h.notices
        )
    }

    @Test
    fun `repository reporting false means the library is gone`() {
        val h = Harness(outcome = { _, _, _ -> false })
        run(h, times = 1) { record("我发出去了") }
        assertEquals(listOf(ActualSentState.IDLE, ActualSentState.KB_NOT_FOUND), h.emissions)
        assertEquals(listOf("本轮保存失败：知识库已被删除"), h.warnings)
        assertEquals("写失败不得计 adopt", 0, h.adopted)
    }

    @Test
    fun `cancellation is rethrown instead of being reported as a failure`() {
        val h = Harness(failure = { kotlinx.coroutines.CancellationException("stopped by user") })
        run(h, times = 1) { record("我发出去了") }

        // 第一版这里只断"三个可见面都干净"（没走 onError、没警告、状态停 IDLE），
        // 当时还写下"为了测试交出 Job 不值"——反证 S4 把 `throw e` 改成空 catch 之后
        // **这一格照样全绿**，那句话当场被证伪：取消被吞掉时，状态/警告/日志三个面
        // 和"上抛"长得一模一样。所以现在改判据、交出句柄，并删掉那句自我安慰。
        assertTrue(
            "取消必须让这一次尝试以 cancelled 收场；实到 isCancelled=${h.lastJob?.isCancelled} " +
                "isCompleted=${h.lastJob?.isCompleted} logged=${h.logged} warnings=${h.warnings}",
            h.lastJob?.isCancelled == true
        )
        assertEquals("取消不得产生任何一次失败回执", listOf(ActualSentState.IDLE), h.emissions)
        assertNull("取消不得被记成一次 IO 失败（那会给出用户从没见过的回执）", h.logged)
        assertEquals(
            "取消之后状态停在 IDLE：既不假装成功，也不伪造一条失败回执",
            ActualSentState.IDLE, h.state.value
        )
        assertTrue("取消不该给面板警告：${h.warnings}", h.warnings.isEmpty())
    }

    @Test
    fun `the entry keeps a machine readable header and one human line`() {
        val h = Harness(candidate = { key -> if (key == "k-1") "候选正文" else null })
        run(h, times = 1) { record("  我发出去了  ", "k-1") }

        val entry = h.writes.single().second
        val header = entry.lineSequence().first()
        assertTrue("头注释要带时间：$header", header.startsWith("<!-- sent:2"))
        assertTrue("头注释要带关联候选：$header", header.contains(" linked:k-1"))
        assertTrue("头注释要带版本：$header", header.contains(" version:v-1"))
        assertTrue("头注释要带候选正文：$header", header.contains(" candidate:"))
        assertTrue("候选正文要编码过，不能塞裸文本：$header", !header.contains("候选正文"))
        assertEquals("用户可见那一行必须去掉首尾空白", "我（确认已发送）：我发出去了", entry.lines()[1])
    }

    @Test
    fun `blank text never reaches the repository`() {
        val h = Harness()
        run(h, times = 1) { record("   ") }
        assertTrue(h.writes.isEmpty())
        assertEquals(listOf(ActualSentState.IDLE, ActualSentState.IO_ERROR), h.emissions)
    }
}
