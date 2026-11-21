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
 *  - topic_status=new 的**单判据形态**（同标签 + 新鲜 scene 链 + 无标记 + 无任何非模型佐证）
 *    必须被否决——这正是旧实现 TopicRecorder.kt:123 造成场景记忆误清空的输入；
 *  - **F1 定案规则（外部审计第三轮死锁修复）**：任一非模型源信号
 *    （SCENE_EMPTY（含 recent 非空检查）/TIME_GAP/USER_MARKER）+ 模型主张 new 即确认——
 *    旧"需凑两个弱信号"规则下 SCENE_EMPTY 与 TIME_GAP 在 newestSceneMs 上互斥，
 *    weakSignals.size>=2 永不可达，自然切换（无白名单用语）被永久否决；
 *  - scene 空但 recent 层仍有当前话题内容时根本不算"无可丢记忆"（审计 #09 防线保留）；
 *  - **R5 佐证独立性**：LABEL_CHANGE 与 topic_status=new 出自同一次模型输出（同源共误），
 *    永不参与定案，仅它命中（无任何非模型佐证）必须否决；
 *  - 否决原因只含信号名与数值，不含消息/标签正文（隐私红线）。
 *
 * 带落盘后果的端到端行为（归档完整性、保持现状）在 TopicSwitchHardeningTest 走真实仓库。
 */
class TopicSwitchGateTest {

    /** 与 FixedClock.DEFAULT_START_MS 同一天，保证 TimeFmt.parse 与字符串同一时区口径 */
    private val nowMs = TimeFmt.parse("2026-09-24 09:00")

    /** 生产里 recent.md 通常仍留着当前话题轮次——默认给非空 recent */
    private fun recentContent(content: String = "- [2026-09-24 08:30]\n她：上次说起的安排\n") = content

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
            recentContent = recentContent(),
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
            recentContent = recentContent(),
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
            recentContent = recentContent(),
            messageContents = listOf(secretContent),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertFalse(d.confirmed)
        assertFalse("否决原因不许含消息正文", d.rejectionReason!!.contains("医院"))
        assertFalse("否决原因不许含标签正文之外的话题行", d.rejectionReason!!.contains("正在聊"))
    }

    // ─── F1：任一非模型佐证 + 模型主张 new 即确认（自然切换不再死锁）───

    @Test
    fun `time gap alone confirms a natural switch without user marker`() {
        // F1 死锁修复正向：场景证据陈旧（TIME_GAP 成立）+ 模型主张 new + 无白名单用语
        // → 必须确认。旧规则要求凑两个弱信号，而两个弱信号互斥、永不可达。
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 90),
            recentContent = recentContent(),
            messageContents = listOf("对了问你个事"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("TIME_GAP 是非模型佐证，单独命中即可定案：${d.rejectionReason}", d.confirmed)
        assertEquals(setOf(TopicSwitchGate.SIGNAL_TIME_GAP), d.signals.toSet())
    }

    @Test
    fun `empty scene with empty recent confirms a natural switch without user marker`() {
        // F1 死锁修复正向：scene 与 recent 此刻层都空（真·无可丢记忆，审计 #09 检查保留）
        // + 模型主张 new + 无白名单用语 → SCENE_EMPTY 单独命中即可定案。
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = "   \n",
            recentContent = "",
            messageContents = listOf("在吗"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("SCENE_EMPTY 是非模型佐证，单独命中即可定案：${d.rejectionReason}", d.confirmed)
        assertEquals(setOf(TopicSwitchGate.SIGNAL_SCENE_EMPTY), d.signals.toSet())
    }

    @Test
    fun `scene empty but recent still holds current topic is not scene-empty`() {
        // 审计 #09 核心回归：scene 链空但 recent.md 仍有当前话题内容——旋转会把 recent
        // 一并归档清空，那里有真实可丢的记忆，不许当"无可丢记忆"
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = "   \n",
            recentContent = recentContent(),
            messageContents = listOf("在吗"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertFalse("recent 层仍有内容时不得当作无可丢记忆", d.confirmed)
        assertTrue("否决原因须说明 recent 层仍有内容：${d.rejectionReason}", d.rejectionReason!!.contains("recent层仍有当前内容"))
    }

    @Test
    fun `time gap plus user marker confirms the switch`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 90),
            recentContent = recentContent(),
            messageContents = listOf("不聊这个了"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("弱信号 + 确定性用户标记可以定案：${d.rejectionReason}", d.confirmed)
        assertEquals(
            setOf(TopicSwitchGate.SIGNAL_TIME_GAP, TopicSwitchGate.SIGNAL_USER_MARKER),
            d.signals.toSet()
        )
    }

    @Test
    fun `empty scene with empty recent plus user marker confirms the switch`() {
        val d = evaluate(
            topicLabel = "雅思考试",
            currentTopic = "雅思考试",
            sceneContent = "   \n",
            recentContent = "",
            messageContents = listOf("好了，说点别的吧"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("真·无可丢记忆 + 用户显式标记可以定案：${d.rejectionReason}", d.confirmed)
        assertEquals(
            setOf(TopicSwitchGate.SIGNAL_SCENE_EMPTY, TopicSwitchGate.SIGNAL_USER_MARKER),
            d.signals.toSet()
        )
    }

    // ─── R5 佐证独立性 ──────────────────────────────────────────────

    @Test
    fun `label change alone no longer confirms the switch`() {
        // R5 佐证独立性：LABEL_CHANGE 与 topic_status=new 出自同一次模型输出——
        // 模型一起错时它反而"佐证"错误切换（同源共误），单独命中必须否决。
        val d = evaluate(
            topicLabel = "雅思考试",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            recentContent = recentContent(),
            messageContents = listOf("好累"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertFalse("模型源信号单独命中不许定案", d.confirmed)
        assertTrue(
            "否决原因须点名 LABEL_CHANGE 同源共误：${d.rejectionReason}",
            d.rejectionReason!!.contains("LABEL_CHANGE") && d.rejectionReason.contains("同源")
        )
        assertTrue("被否决时不许带任何信号：${d.signals}", d.signals.isEmpty())
    }

    @Test
    fun `label change plus time gap confirms the natural switch`() {
        // F1：TIME_GAP 是非模型佐证，在场即定案；LABEL_CHANGE（模型源）不参与定案，
        // 但也不拖累其他佐证——佐证可组合。
        val d = evaluate(
            topicLabel = "雅思考试",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 90),
            recentContent = recentContent(),
            messageContents = listOf("好累"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("TIME_GAP（非模型佐证）在场即定案：${d.rejectionReason}", d.confirmed)
        assertEquals(
            setOf(TopicSwitchGate.SIGNAL_TIME_GAP, TopicSwitchGate.SIGNAL_LABEL_CHANGE),
            d.signals.toSet()
        )
    }

    @Test
    fun `label change plus user marker confirms the switch`() {
        val d = evaluate(
            topicLabel = "雅思考试",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            recentContent = recentContent(),
            messageContents = listOf("不聊这个了"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("用户显式标记（可单独定案）在场时标签变化照常计入佐证", d.confirmed)
        assertEquals(
            setOf(TopicSwitchGate.SIGNAL_USER_MARKER, TopicSwitchGate.SIGNAL_LABEL_CHANGE),
            d.signals.toSet()
        )
    }

    @Test
    fun `whitespace around labels does not mask a label change`() {
        val d = evaluate(
            topicLabel = " 雅思考试 ",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 90),
            recentContent = recentContent(),
            messageContents = listOf("好了，不聊这个了"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("比较前只做 trim，前后空白不许掩盖标签变化：${d.signals}", d.confirmed)
        assertTrue("trim 后标签变化要被识别出来：${d.signals}", d.signals.contains(TopicSwitchGate.SIGNAL_LABEL_CHANGE))
    }

    @Test
    fun `gap just below threshold does not corroborate`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 59),
            recentContent = recentContent(),
            messageContents = emptyList(),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertFalse(d.confirmed)
    }

    @Test
    fun `unparsable scene lines count as empty scene and corroborate the switch`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = "这段不是条目行，没有时间戳\n",
            recentContent = "",
            messageContents = emptyList(),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("无可解析条目按 scene 空处理，SCENE_EMPTY 是非模型佐证即定案", d.confirmed)
        assertEquals(setOf(TopicSwitchGate.SIGNAL_SCENE_EMPTY), d.signals.toSet())
    }

    @Test
    fun `explicit user marker alone confirms the switch`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            recentContent = recentContent(),
            messageContents = listOf("好了好了，我们换个话题吧"),
            latestMessageTimeMs = nowMs,
            nowMs = nowMs
        )
        assertTrue("用户显式换话题是确定性表述，单独即可定案", d.confirmed)
        assertTrue(d.signals.contains(TopicSwitchGate.SIGNAL_USER_MARKER))
    }

    @Test
    fun `marker is matched in me messages too`() {
        val d = evaluate(
            topicLabel = "午饭",
            currentTopic = "午饭",
            sceneContent = sceneLine(minuteOffset = 10),
            recentContent = recentContent(),
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
            recentContent = recentContent(),
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

    @Test
    fun `recent layer content counts any non-blank line`() {
        assertTrue(TopicSwitchGate.hasRecentLayerContent("- [2026-09-24 08:30]\n她：上次说起的安排\n"))
        assertTrue("round marker 注释行也算内容（旋转会把它清掉）",
            TopicSwitchGate.hasRecentLayerContent("<!-- round:msgIds:old-0 -->\n"))
        assertFalse(TopicSwitchGate.hasRecentLayerContent(""))
        assertFalse(TopicSwitchGate.hasRecentLayerContent("   \n\t\n"))
    }
}
