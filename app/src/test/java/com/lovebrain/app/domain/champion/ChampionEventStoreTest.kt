package com.lovebrain.app.domain.champion

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 事件存储验收（规格书 §5.4 对拍项 + 审计 F2-1）：
 * - 同一 msgUid 重发不重复入库（uid 复用、顺序稳定）——同一 pending 轮重试 = 同一事件；
 * - **同分钟同内容的不同消息（不同 msgUid）各成一条事件**——旧 `role|text|ts` 键会把
 *   对方同一分钟连发的两条"好"合并成一条，审计 F2-1 修复的正是这一丢消息形状；
 * - 回复草稿入库请求被拒（reply never committed）；空 msgUid 同样在入口被拒；
 * - 存储落盘往返（第 7 个写入面：`champion_events/<sha 命名>.json`）。
 */
class ChampionEventStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore(): ChampionEventStore =
        ChampionEventStore(FileChampionEventStorage(tmp.newFolder()))

    private fun msg(role: String, text: String, ts: String, msgUid: String) =
        ChampionMessage(role = role, text = text, ts = ts, msgUid = msgUid)

    @Test
    fun `append assigns stable increasing uids and keeps insertion order`() = runTest {
        val store = newStore()
        val out = store.append("k", listOf(
            msg("HER", "你好", "2026-11-06 10:00", "m1"),
            msg("ME", "早", "2026-11-06 10:01", "m2")
        ), turn = 1)
        assertEquals(listOf(0L, 1L), out.map { it.uid })
        assertEquals(listOf("HER", "ME"), out.map { it.role })
        assertEquals(listOf(1, 1), out.map { it.turn })
        assertEquals(listOf("m1", "m2"), out.map { it.msgUid })
    }

    @Test
    fun `same msgUid reuses the same uid and never duplicates`() = runTest {
        val store = newStore()
        val first = store.append("k", listOf(msg("HER", "在吗", "2026-11-06 10:00", "m1")), turn = 1)
        val again = store.append("k", listOf(msg("HER", "在吗", "2026-11-06 10:00", "m1")), turn = 2)
        assertEquals(first, again)                       // 同 msgUid 复用同一事件（含 uid/turn）
        assertEquals(1, store.events("k").size)          // 不重复入库
        assertEquals(1, store.events("k").first().turn)  // turn 只在新事件落库时记录
    }

    @Test
    fun `two identical texts in the same minute are two distinct events`() = runTest {
        // 审计 F2-1 主案情：提交侧时间只格式化到分钟，旧 role|text|ts 键把同一分钟
        // 连发的两条"好"合并成一条——真实对话顺序被丢弃。msgUid 不同 ⇒ 两条事件。
        val store = newStore()
        store.append("k", listOf(
            msg("HER", "好", "2026-11-06 10:05", "m1"),
            msg("HER", "好", "2026-11-06 10:05", "m2")
        ), turn = 1)
        val all = store.events("k")
        assertEquals("同分钟同内容两条真实消息必须两条事件", 2, all.size)
        assertEquals(listOf(0L, 1L), all.map { it.uid })
        assertEquals(listOf("m1", "m2"), all.map { it.msgUid })
        assertEquals(listOf("好", "好"), all.map { it.text })
    }

    @Test
    fun `same text with different ts is a different event`() = runTest {
        val store = newStore()
        store.append("k", listOf(msg("ME", "在吗", "2026-11-06 10:00", "m1")), turn = 1)
        store.append("k", listOf(msg("ME", "在吗", "2026-11-06 11:00", "m2")), turn = 2)
        assertEquals(2, store.events("k").size)
    }

    @Test
    fun `same msgUid with changed text stays the same event`() = runTest {
        // 同一 pending 轮重试 = 同一 ChatMessage.id：即使（理论上）文本被改写，
        // 去重仍按稳定身份命中——不产生第二条事件（身份是消息本身，不是内容）。
        val store = newStore()
        store.append("k", listOf(msg("HER", "初稿", "2026-11-06 10:00", "m1")), turn = 1)
        val again = store.append("k", listOf(msg("HER", "改后", "2026-11-06 10:00", "m1")), turn = 2)
        assertEquals(1, store.events("k").size)
        assertEquals(0L, again.single().uid)
        assertEquals("初稿", again.single().text)   // 复用首次落库事件，turn 不推进
        assertEquals(1, again.single().turn)
    }

    @Test
    fun `blank msgUid is rejected at the entry`() = runTest {
        val store = newStore()
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                store.append("k", listOf(msg("HER", "没身份", "2026-11-06 10:00", " ")), turn = 1)
            }
        }
        assertTrue(store.events("k").isEmpty())
    }

    @Test
    fun `reply drafts are rejected at the entry`() = runTest {
        val store = newStore()
        // 回复是草稿，不是已发生的 ME 消息——入库请求必须当场被拒
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                store.append("k", listOf(msg("REPLY", "草稿正文", "2026-11-06 10:00", "r1")), turn = 1)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                store.append("k", listOf(msg("DRAFT", "草稿正文", "2026-11-06 10:00", "r2")), turn = 1)
            }
        }
        assertTrue(store.events("k").isEmpty())
    }

    @Test
    fun `state persists across store instances like the seventh write face`() = runTest {
        val dir = tmp.newFolder()
        val key = "contactA__story1"
        ChampionEventStore(FileChampionEventStorage(dir))
            .append(key, listOf(msg("HER", "周五见？", "2026-11-06 10:00", "m1")), turn = 3)
        val reloaded = ChampionEventStore(FileChampionEventStorage(dir))
        assertEquals(1, reloaded.events(key).size)
        assertEquals(4, reloaded.nextTurn(key))          // 最大 turn + 1
    }

    @Test
    fun `dedup identity survives a store restart`() = runTest {
        // 去重键（msgUid）随事件落盘：重启后同一 msgUid 重提交仍命中，不重复入库
        val dir = tmp.newFolder()
        val key = "contactA__story1"
        val first = ChampionEventStore(FileChampionEventStorage(dir))
            .append(key, listOf(msg("HER", "周五见？", "2026-11-06 10:00", "m1")), turn = 3)
        val again = ChampionEventStore(FileChampionEventStorage(dir))
            .append(key, listOf(msg("HER", "周五见？", "2026-11-06 10:00", "m1")), turn = 4)
        assertEquals(first, again)
        assertEquals(1, ChampionEventStore(FileChampionEventStorage(dir)).events(key).size)
    }

    @Test
    fun `file storage sanitizes unsafe key characters`() {
        val dir = tmp.newFolder()
        val storage = FileChampionEventStorage(dir)
        storage.save("a/b\\c d", ChampionEventState(uidSeq = 5))
        assertEquals(5, storage.load("a/b\\c d").uidSeq)
    }

    @Test
    fun `distinct keys never collapse into one file even when sanitization collides`() {
        // 审计 R2-2：文件名 = 清洗前缀 + SHA-256(key)——清洗多对一不再导致互相覆盖
        val dir = tmp.newFolder()
        val storage = FileChampionEventStorage(dir)
        storage.save("a/b c", ChampionEventState(uidSeq = 1))
        storage.save("a_b_c", ChampionEventState(uidSeq = 2))
        assertEquals(1, storage.load("a/b c").uidSeq)
        assertEquals(2, storage.load("a_b_c").uidSeq)
    }
}
