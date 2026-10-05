package com.lovebrain.app.feature.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知位排队里**最容易写坏**的那三件事，各自一格钉住：
 *
 * 1. **时限从被看见的那一刻才起算**：读的是 `shownAtMillis` 与 `remainingMillis` 这两颗具体毫秒数。
 *    在投递时就盖章的实现会把排队那几分钟一起扣掉，第二条上屏即"只剩一半"，当场红。
 * 2. **晚到的结束通知关不掉正在读的那条**：面板的 `delay` 与用户的手会撞车，
 *    旧那条的定时器在新那条已经上屏之后才回报是常态而不是意外。这里刻意构造
 *    "同一个旧 id 再回报一次"与"旧通道自己来收尾"两种晚到形态。
 * 3. **按内容摘除只摘那一格**：条件回正（输入又与结果一致了）撤掉自己那句时，
 *    同一通道里别人刚投的一句必须原样留着——用整通道清空来实现会顺手抹掉它。
 *
 * 判据全部落在队列自己的读数上（`current` / 三条通道的最近文案），不碰像素、不碰面板组合。
 */
class NoticeQueueExpiryTest {

    /** 只有被显示的那一刻才会被队列读取；"排队期间走了多久"因此是一眼能看出来的数。 */
    private class FakeClock(var nowMillis: Long = 1_000L) {
        fun read(): Long = nowMillis
        fun advance(millis: Long) {
            nowMillis += millis
        }
    }

    private fun board(clock: FakeClock) = NoticeBoard(clock = clock::read)

    private val knowledge = NoticeBoard.Channel.Knowledge
    private val warning = NoticeBoard.Channel.Warning
    private val vector = NoticeBoard.Channel.Vector

    @Test
    fun `the second notice gets its full allowance counted from the moment the first one expires`() {
        val clock = FakeClock(1_000L)
        val board = board(clock)
        board.show(knowledge, "经验提取完成")      // 回执那一条 3 秒
        board.show(vector, "亲密度 +2")            // 向量摘要 5 秒，排在后面
        val firstId = board.current.value!!.id

        clock.advance(3_000L)                       // 推到第一条恰好过点（面板就是在这一刻回报到点）
        assertEquals("前置条件：第一条此刻确实已经没有余额", 0L, board.current.value!!.remainingMillis(4_000L))

        board.dismiss(firstId)

        val second = board.current.value!!
        assertEquals(vector, second.channel)
        assertEquals("补位就发生在到点这一刻，不额外消耗时间", 4_000L, second.shownAtMillis)
        assertEquals("排队那 3 秒不许从它的 5 秒里扣掉", 5_000L, second.remainingMillis(4_000L))
        assertEquals("它自己的表从这里开始走", 3_000L, second.remainingMillis(6_000L))
    }

    @Test
    fun `a late expiry callback for the finished notice cannot close the one now on screen`() {
        val clock = FakeClock(1_000L)
        val board = board(clock)
        board.show(knowledge, "经验提取完成")
        board.show(vector, "亲密度 +2")
        val staleId = board.current.value!!.id
        clock.advance(3_000L)
        board.dismiss(staleId)

        val reading = board.current.value!!
        assertEquals(vector, reading.channel)
        clock.advance(500L)

        // ① 旧那条的定时器因为调度晚了半秒，拿着旧 id 又回报一次
        board.dismiss(staleId)
        // ② 旧那条的通道收尾也晚到（面板上仍挂着按通道关的旧写法）
        board.dismiss(knowledge)

        assertEquals("晚到的旧通知把正在读的第二条关掉了", vector, board.current.value!!.channel)
        assertEquals(reading.id, board.current.value!!.id)
        assertEquals("亲密度 +2", board.current.value!!.message)
        assertEquals("也没偷走它的时间", 4_000L, board.current.value!!.shownAtMillis)
        assertEquals("余额仍然从到点那一刻起算", 4_500L, board.current.value!!.remainingMillis(4_500L))

        board.endCurrent()
        assertNull(board.current.value)
    }

    @Test
    fun `dropping a notice whose condition turned false again leaves the other line on the same channel`() {
        val board = board(FakeClock())
        board.show(knowledge, "经验提取完成")                                  // 正在读
        board.show(warning, "输入已变化，本轮结果可能不是最新的")                  // 排队
        board.show(warning, "未配置模型")                                      // 同通道重写 = 刷新那一格

        board.dismissMessage(warning, "输入已变化，本轮结果可能不是最新的")

        assertEquals("正在显示的那条不该被别人的收尾带走", "经验提取完成", board.current.value!!.message)
        assertEquals("排队那一格如今是另一句话，按旧文案摘除不该抹掉它", "未配置模型", board.warning.value)
        board.endCurrent()
        assertEquals("未被摘掉的那一句仍然排到了通知位", "未配置模型", board.current.value!!.message)
    }

    @Test
    fun `removing a queued line by its own text does not disturb the notice being read`() {
        val board = board(FakeClock())
        board.show(knowledge, "经验提取完成")
        board.show(warning, "输入已变化，本轮结果可能不是最新的")

        board.dismissMessage(warning, "输入已变化，本轮结果可能不是最新的")

        assertEquals("正在读的回执不该被撤掉", knowledge, board.current.value!!.channel)
        board.endCurrent()
        assertNull("排队中被撤掉的那条不该再冒出来", board.current.value)
    }

    @Test
    fun `a late callback for the previous suggestion cannot pull the current card off the board`() {
        val board = board(FakeClock())
        board.showSuggestion(NoticeBoard.SuggestionKind.Profile, "画像建议-旧")
        board.show(knowledge, "经验提取完成")   // 卡占着通知位，回执排队
        board.showSuggestion(NoticeBoard.SuggestionKind.Profile, "画像建议-新")

        val card = board.current.value!!
        assertTrue("同一格再投一次仍然是一张建议卡", card.isSuggestion)
        assertEquals("画像建议-新", card.suggestion!!.key)

        // 上一份建议的确认/忽略回调晚到：身份不符，什么都不该做
        board.dismissSuggestion(NoticeBoard.SuggestionKind.Profile, "画像建议-旧")
        assertEquals(card.id, board.current.value!!.id)
        assertEquals("画像建议-新", board.current.value!!.suggestion?.key)

        // 真正的这一份结束：队列里排着的回执立即顶上
        board.dismissSuggestion(NoticeBoard.SuggestionKind.Profile, "画像建议-新")
        assertEquals("经验提取完成", board.current.value!!.message)
        assertEquals(knowledge, board.current.value!!.channel)
    }
}
