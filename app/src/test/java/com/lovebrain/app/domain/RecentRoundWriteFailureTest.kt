package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.KnowledgeBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-6 的一格：`moment/recent.md` 与 `memory/raw_chat.md` 分两次加锁，
 * 溢出那笔没落盘时旧实现一声不吭（recent 照写，最老几轮直接消失）。
 *
 * 现在 `writeRecent` 消费两笔写的 Boolean 返回并交回成败；这条用例用一个"吞掉 append"的端口
 * 证明：失败被看见、recent 不被改写、重试后不出现重复溢出。
 */
class RecentRoundWriteFailureTest {

    /** 只让 append 失败，其余照常——模拟只读/IO 拒绝但读与覆盖写仍可用 */
    private class AppendFailingPort(private val inner: InMemoryKnowledgePort) : KnowledgePort by inner {
        var failAppend = false
        override suspend fun appendFile(kbName: String, relativePath: String, content: String): Boolean {
            if (failAppend) return false
            return inner.appendFile(kbName, relativePath, content)
        }
    }

    /** 只让 recent 那一次覆盖写失败——模拟"溢出已进暂存、recent 没写成"的半途状态 */
    private class RecentWriteFailingPort(private val inner: InMemoryKnowledgePort) : KnowledgePort by inner {
        var failRecent = false
        override suspend fun writeFile(kbName: String, relativePath: String, content: String): Boolean {
            if (failRecent && relativePath == "moment/recent.md") return false
            return inner.writeFile(kbName, relativePath, content)
        }
    }

    private fun port(vararg files: Pair<String, String>): AppendFailingPort {
        val inner = InMemoryKnowledgePort().apply {
            seedLibrary(KnowledgeBase(name = "kb", displayName = "kb"))
            files.forEach { (path, content) -> seed("kb", path, content) }
        }
        return AppendFailingPort(inner)
    }

    private fun entry(text: String) = "- [2026-09-24 10:00]\n<!-- round:msgIds:c-0 -->\n$text\n"

    private fun seedRecentRounds(): String =
        (1..AppConfig.MAX_TOPIC_TURNS).joinToString("\n\n") { i ->
            "- [2026-09-24 0$i:00]\n<!-- round:msgIds:old-$i -->\n第 $i 轮内容"
        } + "\n"

    @Test
    fun `a swallowed overflow append is reported and does not rewrite recent`() = runBlocking {
        val p = port("moment/recent.md" to seedRecentRounds(), "memory/raw_chat.md" to "")
        val before = p.readFile("kb", "moment/recent.md")
        val store = RecentRoundStore(p)
        p.failAppend = true

        val ok = store.writeRecent("kb", entry("新一轮内容"), "c-0")

        assertFalse("溢出没进暂存就必须报失败，不许假成功", ok)
        assertEquals("失败时 recent 一个字节都不许改（否则最老几轮静默丢失）", before, p.readFile("kb", "moment/recent.md"))
        assertEquals("暂存里也不许出现半截内容", "", p.readFile("kb", "memory/raw_chat.md"))
    }

    @Test
    fun `retrying after a failed overflow writes each round exactly once`() = runBlocking {
        val p = port("moment/recent.md" to seedRecentRounds(), "memory/raw_chat.md" to "")
        val store = RecentRoundStore(p)
        p.failAppend = true
        assertFalse(store.writeRecent("kb", entry("新一轮内容"), "c-0"))

        p.failAppend = false
        assertTrue("放开 append 后重试必须成功", store.writeRecent("kb", entry("新一轮内容"), "c-0"))

        val staged = p.readFile("kb", "memory/raw_chat.md")
        val recent = p.readFile("kb", "moment/recent.md")
        assertTrue("最老一轮必须真的进了暂存：\n$staged", staged.contains("第 1 轮内容"))
        assertEquals(
            "同一次溢出不许出现两份：\n$staged",
            1, Regex("第 1 轮内容").findAll(staged).count()
        )
        assertTrue("新轮进 recent：\n$recent", recent.contains("新一轮内容"))
        assertFalse("溢出的那一轮不许留在此刻层：\n$recent", recent.contains("第 1 轮内容"))
    }

    /**
     * 调用点那一格：`writeRecent` 的 Boolean 过去被 `apply(PROJ_RECENT)` 丢掉，
     * 于是 recent 没写成、水位照常推进、轮次计数照写，最后 journal 记"已提交"——
     * 重试会被 `isRoundCommitted` 直接拦下，这一轮永久丢失。
     *
     * 现在的口径：失败即中断本轮（journal 的既有通道），水位停在 recent 之前，
     * 同一 roundId 重试只重跑这个投影；提交清单与计数都不许先动。
     */
    @Test
    fun `a failed recent projection is not committed and the retry lands it once`() = runBlocking {
        val inner = InMemoryKnowledgePort().apply {
            seedLibrary(KnowledgeBase(name = "kb", displayName = "kb"))
            seed("kb", "moment/recent.md", "")
            seed("kb", "memory/raw_chat.md", "")
        }
        val p = RecentWriteFailingPort(inner)
        val journal = RoundCommitJournal(p)
        val recorder = TopicRecorder(p, journal)
        val kb = KnowledgeBase(name = "kb", displayName = "kb")
        val messages = listOf(ChatMessage(id = "m-1", role = ChatMessage.Role.HER, content = "这一轮的内容"))
        val roundId = RoundCommitJournal.stableRoundId("kb", "m-1")

        p.failRecent = true
        val first = runCatching {
            recorder.record(
                kb = kb, messages = messages, scheme = null,
                topicStatus = "same", topicLabel = ""
            )
        }
        assertTrue("recent 没写成时这一轮必须报失败，不许记成功：$first", first.isFailure)
        assertFalse("这一轮不许进已提交清单（否则重试会被幂等判据拦下）", journal.isRoundCommitted("kb", roundId))
        assertFalse("失败时 recent 里不许出现半轮", p.readFile("kb", "moment/recent.md").contains("这一轮的内容"))
        assertEquals("失败点之后的投影（轮次计数）不许推进", 0, p.getTurnCount("kb"))
        assertTrue(
            "journal 必须留在途（水位停在 recent 之前），下一轮才能重试",
            p.readFile("kb", RoundCommitJournal.JOURNAL_PATH).isNotBlank()
        )
        assertNull(
            "recent 的投影水位不许推进（推进了重试就会跳过它）",
            journal.readState("kb").projections[RoundCommitJournal.PROJ_RECENT]
        )

        p.failRecent = false
        val retried = recorder.record(
            kb = kb, messages = messages, scheme = null,
            topicStatus = "same", topicLabel = ""
        )
        assertFalse("重试这一轮不涉及话题切换", retried)
        assertTrue("放开写之后重试必须真的提交", journal.isRoundCommitted("kb", roundId))
        assertEquals(
            "同一轮只许落一份：\n${p.readFile("kb", "moment/recent.md")}",
            1, Regex("这一轮的内容").findAll(p.readFile("kb", "moment/recent.md")).count()
        )
        assertEquals("提交时计数只加一次", 1, p.getTurnCount("kb"))
        assertEquals("提交后 journal 清空", "", p.readFile("kb", RoundCommitJournal.JOURNAL_PATH))
    }

    /**
     * 半途状态：溢出那一笔已经进暂存，recent 的覆盖写被拒。
     * 这一轮必须报失败；重试时**不许把同一批最老轮再追加一遍**（stage 前先查重），
     * 成功后暂存里每轮恰好一份。
     */
    @Test
    fun `a landed overflow is not appended twice when the recent write is retried`() = runBlocking {
        val inner = InMemoryKnowledgePort().apply {
            seedLibrary(KnowledgeBase(name = "kb", displayName = "kb"))
            seed("kb", "moment/recent.md", seedRecentRounds())
            seed("kb", "memory/raw_chat.md", "")
        }
        val p = RecentWriteFailingPort(inner)
        val store = RecentRoundStore(p)
        p.failRecent = true

        assertFalse(
            "溢出让 recent 没写成时这一轮必须报失败",
            store.writeRecent("kb", entry("新一轮内容"), "c-0")
        )
        assertTrue(
            "前提：溢出那一笔已经进了暂存",
            p.readFile("kb", "memory/raw_chat.md").contains("第 1 轮内容")
        )

        p.failRecent = false
        assertTrue("放开 recent 写后重试必须成功", store.writeRecent("kb", entry("新一轮内容"), "c-0"))

        val staged = p.readFile("kb", "memory/raw_chat.md")
        assertEquals(
            "重试不许把溢出再追加一遍：\n$staged",
            1, Regex("第 1 轮内容").findAll(staged).count()
        )
        val recent = p.readFile("kb", "moment/recent.md")
        assertTrue("新轮进 recent：\n$recent", recent.contains("新一轮内容"))
        assertFalse("溢出的那一轮不许留在此刻层：\n$recent", recent.contains("第 1 轮内容"))
    }
}
