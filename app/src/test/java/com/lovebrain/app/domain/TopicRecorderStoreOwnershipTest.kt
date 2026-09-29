package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.FixedClock
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.OngoingItem
import com.lovebrain.app.model.SceneFact
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TopicRecorder] 按"一个变化理由一个所有者"拆开之后，这份用例是那次拆分**买到的东西**：
 * 四个 owner 现在可以被单独构造，各自那几条判据不再需要隔着
 * [RoundCommitJournal] 的事务走完整一轮才碰得到。
 *
 * 之前为什么测不到：过期归档要求"现在"比 scene 行晚两小时，
 * 复活禁令要求事项先在已结束区待过——这些都只能靠 [TopicRecorder.record] 反复铺数据，
 * 一旦 WAL 的幂等判据把这一轮拦下，被测分支根本没跑。
 *
 * 这里刻意**不用** mockk、不碰 `data/`、也不要临时目录：
 * owner 只依赖 [com.lovebrain.app.domain.port.KnowledgePort] 与
 * [com.lovebrain.app.domain.port.Clock] 两个端口，
 * 所以 [InMemoryKnowledgePort] + [FixedClock] 就能把整条判据走穿。
 * 落盘内容与 [TopicRecorder] 走事务时写的是同一段代码产出的，不是复刻。
 */
class TopicRecorderStoreOwnershipTest {

    private fun port(vararg files: Pair<String, String>) = InMemoryKnowledgePort().apply {
        seedLibrary(KnowledgeBase(name = "kb", displayName = "kb"))
        files.forEach { (path, content) -> seed("kb", path, content) }
    }

    // ─── RecentRoundStore：轮次正文与"留几轮"的窗口 ───────────────────

    @Test
    fun `recent window moves the oldest round to staging and keeps unrecognized text`() = runBlocking {
        val p = port(
            "moment/recent.md" to
                "这段是手写的说明，不是轮次\n\n" +
                "- [2026-09-24 07:00]\n<!-- round:msgIds:a-0 -->\n第一轮内容\n\n" +
                "- [2026-09-24 08:00]\n<!-- round:msgIds:b-0 -->\n第二轮内容\n",
            "memory/raw_chat.md" to ""
        )
        val store = RecentRoundStore(p)
        val entry = store.buildRoundEntry(
            time = "2026-09-24 09:00",
            roundMsgIds = "c-0",
            conversationalMessages = listOf(ChatMessage(id = "c-0", role = ChatMessage.Role.HER, content = "第三轮内容")),
            userHint = "顺便提一句",
            scheme = null,
            likedSchemes = emptyList()
        )
        store.writeRecent("kb", entry, "c-0")

        val recent = p.readFile("kb", "moment/recent.md")
        val staged = p.readFile("kb", "memory/raw_chat.md")
        assertTrue("块头要用给定的时间，本类不自己读钟：\n$entry", entry.startsWith("- [2026-09-24 09:00]"))
        assertTrue("本轮记录进 recent.md：\n$recent", recent.contains("第三轮内容"))
        assertTrue("未识别内容原样保留、不当垃圾丢：\n$recent", recent.contains("这段是手写的说明"))
        assertTrue("上一轮还留在此刻层：\n$recent", recent.contains("第二轮内容"))
        assertTrue("最老的一轮溢出到对话暂存：\n$staged", staged.contains("第一轮内容"))
        assertFalse("此刻层只留最近 ${AppConfig.MAX_TOPIC_TURNS} 轮：\n$recent", recent.contains("第一轮内容"))

    }

    @Test
    fun `recent marker dedups the same round inside the file`() = runBlocking {
        val p = port("moment/recent.md" to "", "memory/raw_chat.md" to "")
        val store = RecentRoundStore(p)
        val entry = store.buildRoundEntry(
            time = "2026-09-24 09:00", roundMsgIds = "c-0",
            conversationalMessages = listOf(ChatMessage(id = "c-0", role = ChatMessage.Role.HER, content = "第一轮内容")),
            userHint = "", scheme = null, likedSchemes = emptyList()
        )
        store.writeRecent("kb", entry, "c-0")
        val once = p.readFile("kb", "moment/recent.md")
        store.writeRecent("kb", entry, "c-0")
        assertEquals("同 roundMsgIds 重述不许写第二份", once, p.readFile("kb", "moment/recent.md"))
    }

    // ─── SceneChainStore：来源校验与过期归档 ─────────────────────────

    @Test
    fun `scene chain archives the expired entry instead of reviving it`() = runBlocking {
        // 起点直接由 TimeFmt.parse 给出，不写死毫秒数：FixedClock 的 DEFAULT_START_MS
        // 与本用例要造的"三小时前"必须同源，否则"过期"这条判据取决于机器时区。
        val clock = FixedClock(startMs = com.lovebrain.app.util.TimeFmt.parse("2026-09-24 09:00"))
        val p = port(
            "moment/scene.md" to "- [2026-09-24 06:00] 感冒：她感冒了|src=old-0\n",
            "memory/raw_scene.md" to ""
        )
        val store = SceneChainStore(p, clock)
        store.updateSceneChain(
            kbName = "kb",
            topicLabel = "午饭",
            sceneFacts = listOf(SceneFact(text = "她刚吃完午饭", sourceIds = listOf("m1"))),
            frozenMessages = listOf(ChatMessage(id = "m1", role = ChatMessage.Role.HER, content = "刚吃完午饭"))
        )

        val scene = p.readFile("kb", "moment/scene.md")
        val archived = p.readFile("kb", "memory/raw_scene.md")
        assertTrue("过期条目要落到状态暂存：\n$archived", archived.contains("她感冒了"))
        assertFalse("过期事实不许留在此刻层：\n$scene", scene.contains("她感冒了"))
        assertTrue("本轮合法事实进此刻层：\n$scene", scene.contains("她刚吃完午饭"))
        assertTrue("来源标记要能读回来：\n$scene", scene.contains("src=m1"))
    }

    @Test
    fun `scene chain rejects facts whose source is not in the frozen snapshot`() = runBlocking {
        val p = port("moment/scene.md" to "", "memory/raw_scene.md" to "")
        val store = SceneChainStore(p, FixedClock())
        store.updateSceneChain(
            kbName = "kb",
            topicLabel = "日常",
            sceneFacts = listOf(
                SceneFact(text = "她感冒了", sourceIds = listOf("m1")),
                SceneFact(text = "编造事实", sourceIds = listOf("不存在的消息"))
            ),
            // IDEA 不是有效来源
            frozenMessages = listOf(
                ChatMessage(id = "m1", role = ChatMessage.Role.HER, content = "我感冒了"),
                ChatMessage(id = "idea-0", role = ChatMessage.Role.IDEA, content = "先关心她")
            )
        )
        val scene = p.readFile("kb", "moment/scene.md")
        assertTrue("合法来源的事实要写进来：\n$scene", scene.contains("她感冒了"))
        assertFalse("非法来源只拒绝该事实：\n$scene", scene.contains("编造事实"))
    }

    // ─── OngoingPlanStore：稳定身份、幂等与防旧任务复活 ───────────────

    @Test
    fun `plan merge is idempotent on unchanged state and respects the ended section`() = runBlocking {
        val p = port("moment/plan.md" to "# 事项计划\n\n## 进行中\n\n## 已结束\n")
        val store = OngoingPlanStore(p, p)
        suspend fun merge(status: String, state: String, sourceIds: List<String> = listOf("m1")) =
            store.mergeOngoing(
                "kb",
                listOf(OngoingItem(name = "雅思备考", status = status, state = state, itemId = "item-ielts", sourceIds = sourceIds)),
                "2026-09-24 09:00"
            )

        merge("进行中", "下周开考")
        val once = p.readFile("kb", "moment/plan.md")
        assertTrue("稳定 itemId 要写进行首，供下一轮匹配：\n$once", once.contains("item-ielts~雅思备考 | 进行中 |"))
        assertTrue("状态链带时间节点：\n$once", once.contains("[2026-09-24 09:00]下周开考（当前）"))

        merge("进行中", "下周开考")
        assertEquals("相同 itemId + 相同 state 不许把链写长", once, p.readFile("kb", "moment/plan.md"))

        merge("已完成", "考完了")
        val afterDone = p.readFile("kb", "moment/plan.md")
        assertTrue("终态事项移入已结束区：\n$afterDone", afterDone.substringAfter("## 已结束").contains("雅思备考"))
        assertFalse("已结束区的事项不该还挂在进行中：\n$afterDone", afterDone.substringAfter("## 进行中").substringBefore("## 已结束").contains("雅思备考"))

        // 没有新证据 → 不许把已结束事项复活
        merge("进行中", "又要准备了", sourceIds = emptyList())
        assertEquals("非终态输出不带证据时不许复活旧任务", afterDone, p.readFile("kb", "moment/plan.md"))

        // 带新的真实证据 → 允许复活回进行中
        merge("进行中", "又要准备了", sourceIds = listOf("m2"))
        val revived = p.readFile("kb", "moment/plan.md")
        assertTrue("带新证据的终态→非终态转换要回到进行中：\n$revived",
            revived.substringAfter("## 进行中").substringBefore("## 已结束").contains("雅思备考"))
    }

    // ─── KnowledgeContextReader：读侧排版 ────────────────────────────

    @Test
    fun `context reader assembles the sections and truncates the topic archive`() = runBlocking {
        val topics = (1..6).joinToString("\n\n") { "# 话题编号$it\n内容$it" }
        val p = port(
            "moment/topic.md" to "- [2026-09-24 09:00] 日常对话",
            "moment/scene.md" to "- [2026-09-24 09:00] 午饭：她刚吃完午饭|src=m1",
            "moment/recent.md" to "- [2026-09-24 09:00]\n<!-- round:msgIds:c-0 -->\n第三轮内容",
            "memory/raw_chat.md" to "- [2026-09-24 07:00]\n第一轮内容",
            "memory/raw_scene.md" to "- [2026-09-24 06:00] 感冒：她感冒了",
            "memory/raw_topic.md" to topics
        )
        val reader = KnowledgeContextReader(p)

        val full = reader.getTopicFullContext("kb", topicCount = 2)
        assertTrue("开头是当前话题：\n$full", full.startsWith("当前话题：日常对话"))
        listOf("【此刻状态】", "【状态暂存】", "【对话暂存】", "【最近对话】", "【已结束话题（近期）】")
            .zipWithNext().forEach { (earlier, later) ->
                assertTrue("分节顺序 $earlier -> $later：\n$full",
                    full.indexOf(earlier) in 0 until full.indexOf(later))
            }
        assertTrue("只倒取最近 2 个话题：\n$full", full.contains("话题编号6") && full.contains("话题编号5"))
        assertFalse("更早的话题要截断掉：\n$full", full.contains("话题编号4"))
        assertTrue("截断要留痕：\n$full", full.contains("…（更早的话题已省略）"))

        val vector = reader.getVectorContext("kb")
        assertTrue("向量重估用默认条数 ${AppConfig.VECTOR_CONTEXT_TOPICS}：\n$vector",
            vector.contains("话题编号6") && !vector.contains("话题编号1"))
    }
}
