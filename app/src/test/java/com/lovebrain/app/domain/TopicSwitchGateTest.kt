package com.lovebrain.app.domain

import com.lovebrain.app.domain.TopicSwitchGate.evaluate
import com.lovebrain.app.util.TimeFmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2.1 加固门（[TopicSwitchGate]）的判据单测——纯 JVM，不碰端口与仓库。
 *
 * 钉住的行为：
 *  - topic_status=new 的**单判据形态**（同标签 + 新鲜 scene 链 + 无标记）必须被否决——
 *    这正是旧实现 TopicRecorder.kt:123 造成场景记忆误清空的输入；
 *  - 每个佐证信号单独命中即可确认；
 *  - 否决原因只含信号名与数值，不含消息/标签正文（隐私红线）。
 *
 * 带落盘后果的端到端行为（归档完整性、保持现状）在 TopicSwitchHardeningTest 走真实仓库。
 */
class TopicSwitchGateTest {

    /** 与 FixedClock.DEFAULT_START_MS 同一天，保证 TimeFmt.parse 与字符串同一时区口径 */
    private val nowMs = TimeFmt.parse("2026-09-24 09:00")

    private fun sceneLine(minuteOffset: Long, fact: String = "她在吃沙拉") =
        "- [${fmt(nowMs - minuteOffset * 60_000L)}] 午饭：$fact|src=m1"

    private fun fmt(ms: Long): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return f.format(java.util.Date(ms))
    }

    // ─── 单判据不再触发 ───────────────────────────────────────────────

    @Test
    fun `same label with fresh scene and no marker is rejected`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10) + "\n",
            messageContents = listOf("今天好累啊"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertFalse("topic_status=new 的单判据形态必须被否决", d.confirmed)
        assertTrue("必须给出否决原因：${d.rejectionReason}", d.rejectionReason!!.contains("无佐证信号"))
        assertTrue("否决原因须含 scene 间隔数值：${d.rejectionReason}", d.rejectionReason.contains("10min"))
        assertTrue("被否决时不许带任何信号：${d.signals}", d.signals.isEmpty())
    }

    @Test
    fun `blank label is rejected regardless of status`() {
        val d = evaluate(
            topicLabel = "  ",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            messageContents = emptyList(),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertFalse(d.confirmed)
        assertTrue("空标签也要给出否决原因：${d.rejectionReason}", d.rejectionReason?.contains("topic_label") == true)
    }

    @Test
    fun `rejection reason never contains message or label text`() {
        val secretContent = "我在医院做手术"
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            messageContents = listOf(secretContent),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertFalse(d.confirmed)
        assertFalse("否决原因不许含消息正文", d.rejectionReason!!.contains("医院"))
        assertFalse("否决原因不许含标签正文之外的话题行", d.rejectionReason!!.contains("正在聊"))
    }

    // ─── 每个佐证信号单独命中即确认 ──────────────────────────────────

    @Test
    fun `label change alone confirms the switch`() {
        val d = evaluate(
            topicLabel = "雅思考试",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            messageContents = listOf("好累"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue(d.confirmed)
        assertEquals(listOf(TopicSwitchGate.SIGNAL_LABEL_CHANGE), d.signals)
    }

    @Test
    fun `whitespace around labels does not mask a label change`() {
        val d = evaluate(
            topicLabel = " 雅思考试 ",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            messageContents = emptyList(),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("比较前只做 trim，前后空白不许掩盖标签变化：${d.signals}", d.confirmed)
    }

    @Test
    fun `time gap alone confirms the switch`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 90),
            messageContents = listOf("对了问你个事"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue(d.confirmed)
        assertTrue(d.signals.contains(TopicSwitchGate.SIGNAL_TIME_GAP))
    }

    @Test
    fun `gap just below threshold does not corroborate`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 59),
            messageContents = emptyList(),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertFalse(d.confirmed)
    }

    @Test
    fun `empty scene alone confirms the switch`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = "   \n",
            messageContents = listOf("在吗"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue(d.confirmed)
        assertEquals(listOf(TopicSwitchGate.SIGNAL_SCENE_EMPTY), d.signals)
    }

    @Test
    fun `unparsable scene lines count as empty scene`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = "这段不是条目行，没有时间戳\n",
            messageContents = emptyList(),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue(d.confirmed)
        assertTrue(d.signals.contains(TopicSwitchGate.SIGNAL_SCENE_EMPTY))
    }

    @Test
    fun `explicit user marker alone confirms the switch`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            messageContents = listOf("好了好了，我们换个话题吧"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue(d.confirmed)
        assertTrue(d.signals.contains(TopicSwitchGate.SIGNAL_USER_MARKER))
    }

    @Test
    fun `marker is matched in me messages too`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            messageContents = listOf("嗯", "说点别的吧"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue(d.confirmed)
        assertTrue(d.signals.contains(TopicSwitchGate.SIGNAL_USER_MARKER))
    }

    @Test
    fun `multiple signals are all reported`() {
        val d = evaluate(
            topicLabel = "雅思考试",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 120),
            messageContents = listOf("不聊这个了"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue(d.confirmed)
        assertEquals(
            setOf(
                TopicSwitchGate.SIGNAL_TIME_GAP,
                TopicSwitchGate.SIGNAL_LABEL_CHANGE,
                TopicSwitchGate.SIGNAL_USER_MARKER
            ),
            d.signals.toSet()
        )
    }

    // ─── newestSceneTimeMs 解析口径 ─────────────────────────────────

    @Test
    fun `newest scene time takes the latest parseable entry`() {
        val content = sceneLine(minuteOffset = 120) + "\n" + sceneLine(minuteOffset = 5) + "\n"
        val newest = TopicSwitchGate.newestSceneTimeMs(content)
        assertEquals("取最新一条条目的时间", nowMs - 5 * 60_000L, newest)
    }

    @Test
    fun `newest scene time returns zero for blank content`() {
        assertEquals(0L, TopicSwitchGate.newestSceneTimeMs(""))
        assertEquals(0L, TopicSwitchGate.newestSceneTimeMs("no entries here"))
    }
}
