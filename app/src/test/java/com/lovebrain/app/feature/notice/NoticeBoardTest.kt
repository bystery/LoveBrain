package com.lovebrain.app.feature.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NoticeBoard] 的判据：通知位**一次一条、其余排队，排队的那条不提前倒计时**。
 *
 * 两族行为分别钉住：
 * · 老的那一族——三条通道各自的内容来源、以及"换知识库该清哪几条"（**不动面板级警告**）。
 *   这条区分是搬进这一格之前 `refreshKnowledgeBases` 的实际行为；少写一条不会报错，
 *   只会让上一块库的回执赖在下一块库的屏幕上，所以它由
 *   [volatile reset deliberately keeps the panel warning] 那一格守着，改成一锅清就红。
 * · 新的那一族——排队语义。这里的每一格都按**能被坏实现打破**来写：
 *   - "等待时不提前过期"读的是 `remainingMillis` 与 `shownAtMillis` 这两个**具体毫秒数**，
 *     不是"current 非空"这类恒真判断；在投递时就盖时间戳的实现会少掉它排队的那段，当场红。
 *   - 顺序按**到达顺序**断言，而且投递次序故意排成"向量、警告、回执"——
 *     用固定优先级 `when`（面板原来就是回执优先）隐藏后面那条的实现会拿到不同通道，当场红；
 *     后到的先出（LIFO）、重写排队项时把它挪到队尾，同样各有一格守着。
 *   - "立即出下一条"断言的是下一条**不额外消耗时间**（`shownAtMillis` 等于结束那一刻的读数），
 *     所以"等下一帧再补位"的实现会把它自己的读数写进来，同样红。
 *   这八颗坏实现（投递即倒计时、固定优先级、结束后不补位、后来者抢占、
 *   无视"不自动过期"、切库连警告一起清、后到的先出、重写排队项插到队尾）都拿生产这一格的副本
 *   真跑过：每一颗至少打断一格，没有一颗全绿通过。
 *
 * 不新增优先级体系、数量徽标、"以后不再提示"这一类东西：这条判据由"API 只有 show / dismiss /
 * endCurrent / dismissVolatileNotices 四个动作"体现，测试里因此也只能用这四个动作驱动整块屏幕。
 */
class NoticeBoardTest {

    /** 假表：只有被显示的那一刻才会被队列读取，所以"排队期间走了多久"是一眼能看出来的数。 */
    private class FakeClock(var nowMillis: Long = 1_000L) {
        fun read(): Long = nowMillis
        fun advance(millis: Long) {
            nowMillis += millis
        }
    }

    private fun board(clock: FakeClock) = NoticeBoard(clock = clock::read)

    private fun NoticeBoard.all(): List<String?> = listOf(knowledge.value, warning.value, vector.value)

    // ——————————————————————————————— 通道语义（保留）

    @Test
    fun `three channels still hold their own latest text`() {
        val board = board(FakeClock())
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        assertEquals(listOf("经验提取完成", null, null), board.all())

        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")
        assertEquals("后来的不抢占正在读的，也不吃掉另一条通道的内容", listOf("经验提取完成", null, "亲密度 +2"), board.all())

        board.dismiss(NoticeBoard.Channel.Knowledge)
        assertEquals(listOf(null, null, "亲密度 +2"), board.all())
    }

    @Test
    fun `switching knowledge base clears the per-base notices`() {
        val board = board(FakeClock())
        board.show(NoticeBoard.Channel.Knowledge, "已记录实际发送的消息")
        board.show(NoticeBoard.Channel.Vector, "本轮重估 3 项")
        board.show(NoticeBoard.Channel.Warning, "未配置模型")

        board.dismissVolatileNotices()

        assertNull("上一块库的回执不得留在新库屏幕上", board.knowledge.value)
        assertNull("上一块库的向量重估摘要不得留在新库屏幕上", board.vector.value)
    }

    @Test
    fun `volatile reset deliberately keeps the panel warning`() {
        val board = board(FakeClock())
        board.show(NoticeBoard.Channel.Warning, "未配置模型")
        board.show(NoticeBoard.Channel.Knowledge, "画像已更新")

        board.dismissVolatileNotices()

        assertNotNull(
            "面板级警告说的是**这台设备**的配置状态，与切到哪块知识库无关；" +
                "把它跟着一起清会让人以为配置好了（这条区分是搬之前的实际行为）",
            board.warning.value
        )
        assertNull(board.knowledge.value)
    }

    @Test
    fun `a warning queued behind a knowledge notice survives the knowledge switch`() {
        val clock = FakeClock(1_000L)
        val board = board(clock)
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        board.show(NoticeBoard.Channel.Warning, "未配置模型")
        clock.advance(2_000L)

        board.dismissVolatileNotices()

        val current = board.current.value
        assertNotNull("清掉正在显示的回执后，留下来的警告要立即顶上，不能空着通知位", current)
        assertEquals(NoticeBoard.Channel.Warning, current!!.channel)
        assertEquals("未配置模型", current.message)
        assertEquals("排队的那条不提前倒计时：时限从顶上来这一刻起算", 3_000L, current.remainingMillis(3_000L))
    }

    // ——————————————————————————————— 单条输出与排队

    @Test
    fun `the notice slot outputs exactly one line and the rest queue in arrival order`() {
        val board = board(FakeClock())
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        board.show(NoticeBoard.Channel.Warning, "这轮没记入知识库")
        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")

        assertEquals("一次只输出一条", "经验提取完成", board.current.value?.message)
        // 队列不靠"隐藏"糊过去：面板只读 current 这一颗，读不到第二条
        assertEquals(NoticeBoard.Channel.Knowledge, board.current.value?.channel)

        board.endCurrent()
        assertEquals("第二条按到达顺序，不是固定优先级", "这轮没记入知识库", board.current.value?.message)
        board.endCurrent()
        assertEquals("亲密度 +2", board.current.value?.message)
        board.endCurrent()
        assertNull("三条都结束就该空着，不许把最后一条留在屏幕上", board.current.value)
        assertEquals(listOf(null, null, null), board.all())
    }

    @Test
    fun `queue order follows arrival, not a fixed channel priority`() {
        val board = board(FakeClock())
        // 故意按"向量、警告、回执"投递：面板原来那套 `when { kbNotice -> …; panelWarning -> …; vectorUpdate -> … }`
        // 会先给"经验提取完成"，那是优先级而不是顺序，这一格就是钉它不能变回优先级。
        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")
        board.show(NoticeBoard.Channel.Warning, "这轮没记入知识库")
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")

        assertEquals("亲密度 +2", board.current.value?.message)
        board.endCurrent()
        assertEquals("这轮没记入知识库", board.current.value?.message)
        board.endCurrent()
        assertEquals("经验提取完成", board.current.value?.message)
        board.endCurrent()
        assertNull(board.current.value)
    }

    @Test
    fun `refreshing a queued notice keeps its place in line`() {
        val board = board(FakeClock())
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        board.show(NoticeBoard.Channel.Warning, "这轮没记入知识库")
        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")

        board.show(NoticeBoard.Channel.Warning, "未配置模型")

        board.endCurrent()
        assertEquals(
            "队列里那条只是被重写，不该因此被挪到最后——一条通道仍然只占一格",
            "未配置模型",
            board.current.value?.message
        )
        board.endCurrent()
        assertEquals("亲密度 +2", board.current.value?.message)
    }

    @Test
    fun `the next notice appears the moment the current one ends`() {
        val clock = FakeClock(1_000L)
        val board = board(clock)
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")

        // 用户手动关闭（不是自动过期），且**没有**推进假表
        board.dismiss(NoticeBoard.Channel.Knowledge)

        val next = board.current.value
        assertNotNull("上一条结束后没有立即出下一条", next)
        assertEquals(NoticeBoard.Channel.Vector, next!!.channel)
        assertEquals("补位不额外消耗时间：显示时刻就是结束那一刻的读数", 1_000L, next.shownAtMillis)
        assertEquals(5_000L, next.remainingMillis(1_000L))
    }

    @Test
    fun `a queued notice does not count down while it waits`() {
        val clock = FakeClock(1_000L)
        val board = board(clock)
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")

        assertEquals(3_000L, board.current.value?.remainingMillis(1_000L))
        clock.advance(2_200L)
        assertEquals("正在显示的那条走自己的表", 800L, board.current.value?.remainingMillis(3_200L))

        board.endCurrent()

        val vector = board.current.value
        assertNotNull(vector)
        assertEquals(NoticeBoard.Channel.Vector, vector!!.channel)
        // 坏实现会在投递时就盖时间戳：那样这里读到的是 3_200、剩下 2_800，两格同时红
        assertEquals(3_200L, vector.shownAtMillis)
        assertEquals(
            "排队期间不许倒计时：显示那一刻才拿到完整的五秒",
            5_000L,
            vector.remainingMillis(3_200L)
        )
        assertEquals("过点后报零不报负数，调用方拿它直接起表", 0L, vector.remainingMillis(30_200L))
    }

    @Test
    fun `a later notice does not preempt the one being read`() {
        val clock = FakeClock(1_000L)
        val board = board(clock)
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        clock.advance(2_000L)

        board.show(NoticeBoard.Channel.Warning, "输入已变化，本轮结果可能不是最新的")

        val reading = board.current.value
        assertEquals("后到的黄色输入提示不能盖掉正在读的回执", NoticeBoard.Channel.Knowledge, reading!!.channel)
        assertEquals("经验提取完成", reading.message)
        assertEquals("排队的那条内容要留着，不能丢", "输入已变化，本轮结果可能不是最新的", board.warning.value)

        board.endCurrent()
        val warning = board.current.value
        assertEquals(NoticeBoard.Channel.Warning, warning!!.channel)
        assertEquals(3_000L, warning.remainingMillis(3_000L))
    }

    @Test
    fun `a notice that needs confirmation never expires on its own`() {
        val clock = FakeClock(1_000L)
        val board = board(clock)
        board.show(NoticeBoard.Channel.Warning, "确认这条画像更新？", autoDismissMillis = null)

        val confirm = board.current.value
        assertNotNull(confirm)
        assertNull("需确认项不新增自动过期：面板不为它起表", confirm!!.remainingMillis(1_000L))
        clock.advance(3_600_000L)
        assertNull("一小时后还是没有时限", confirm.remainingMillis(clock.read()))

        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        assertEquals(
            "需确认项没被确认或忽略之前，后来的普通通知不抢占它",
            NoticeBoard.Channel.Warning,
            board.current.value?.channel
        )

        // 用户点"忽略/确认"之后由调用方回报结束
        board.endCurrent()
        assertEquals("需确认项结束后下一条立即顶上", "经验提取完成", board.current.value?.message)
        assertEquals(3_000L, board.current.value?.remainingMillis(3_601_000L))
    }

    @Test
    fun `dismissing a queued notice drops it before it ever started`() {
        val board = board(FakeClock())
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")

        board.dismiss(NoticeBoard.Channel.Vector)
        assertNull(board.vector.value)

        board.endCurrent()
        assertNull("排队中被撤掉的那条不该再冒出来", board.current.value)
    }

    @Test
    fun `rewriting the same channel refreshes that line instead of queueing a copy`() {
        val clock = FakeClock(1_000L)
        val board = board(clock)
        board.show(NoticeBoard.Channel.Knowledge, "正在提取经验")
        val first = board.current.value!!
        clock.advance(1_000L)

        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")

        val refreshed = board.current.value!!
        assertEquals("同一通道再写一次不该让第二条排在后面", "经验提取完成", refreshed.message)
        assertTrue("换文案要换号，面板的表因此重起", refreshed.id != first.id)
        assertEquals(2_000L, refreshed.shownAtMillis)
        assertEquals(3_000L, refreshed.remainingMillis(2_000L))

        // 文案与时限都没变的重复投递不算"新一条"：正在读的时限不该被续命
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        assertEquals("同样的文案重发不该重起正在读的那条的表", refreshed.id, board.current.value!!.id)
        assertEquals(2_000L, board.current.value!!.shownAtMillis)

        board.endCurrent()
        assertNull(board.current.value)
    }

    @Test
    fun `volatile reset skips channels whose text is already gone`() {
        val board = board(FakeClock())
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")
        board.show(NoticeBoard.Channel.Warning, "未配置模型")

        board.dismiss(NoticeBoard.Channel.Vector)
        board.endCurrent()

        assertEquals(
            "槽位已空的排队项不该占一格空横幅",
            "未配置模型",
            board.current.value?.message
        )
    }
}
