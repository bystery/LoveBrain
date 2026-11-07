package com.lovebrain.app.domain.champion

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 事件存储验收（规格书 §5.4 对拍项）：
 * - 同 (role,text,ts) 重发不重复入库（uid 复用、顺序稳定）；
 * - 回复草稿入库请求被拒（reply never committed）；
 * - 存储落盘往返（第 7 个写入面：`champion_events/<contactId>__<story>.jsonl`）。
 */
class ChampionEventStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore(): ChampionEventStore =
        ChampionEventStore(FileChampionEventStorage(tmp.newFolder()))

    @Test
    fun `append assigns stable increasing uids and keeps insertion order`() = runTest {
        val store = newStore()
        val out = store.append("k", listOf(
            Triple("HER", "你好", "2026-11-06 10:00"),
            Triple("ME", "早", "2026-11-06 10:01")
        ), turn = 1)
        assertEquals(listOf(0L, 1L), out.map { it.uid })
        assertEquals(listOf("HER", "ME"), out.map { it.role })
        assertEquals(listOf(1, 1), out.map { it.turn })
    }

    @Test
    fun `same role text ts reuses the same uid and never duplicates`() = runTest {
        val store = newStore()
        val first = store.append("k", listOf(Triple("HER", "在吗", "2026-11-06 10:00")), turn = 1)
        val again = store.append("k", listOf(Triple("HER", "在吗", "2026-11-06 10:00")), turn = 2)
        assertEquals(first, again)                       // 同键复用同一 uid
        assertEquals(1, store.events("k").size)          // 不重复入库
        assertEquals(1, store.events("k").first().turn)  // turn 只在新事件落库时记录
    }

    @Test
    fun `same text with different ts is a different event`() = runTest {
        val store = newStore()
        store.append("k", listOf(Triple("ME", "在吗", "2026-11-06 10:00")), turn = 1)
        store.append("k", listOf(Triple("ME", "在吗", "2026-11-06 11:00")), turn = 2)
        assertEquals(2, store.events("k").size)
    }

    @Test
    fun `reply drafts are rejected at the entry`() = runTest {
        val store = newStore()
        // 回复是草稿，不是已发生的 ME 消息——入库请求必须当场被拒
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                store.append("k", listOf(Triple("REPLY", "草稿正文", "2026-11-06 10:00")), turn = 1)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                store.append("k", listOf(Triple("DRAFT", "草稿正文", "2026-11-06 10:00")), turn = 1)
            }
        }
        assertTrue(store.events("k").isEmpty())
    }

    @Test
    fun `state persists across store instances like the seventh write face`() = runTest {
        val dir = tmp.newFolder()
        val key = "contactA__story1"
        ChampionEventStore(FileChampionEventStorage(dir))
            .append(key, listOf(Triple("HER", "周五见？", "2026-11-06 10:00")), turn = 3)
        val reloaded = ChampionEventStore(FileChampionEventStorage(dir))
        assertEquals(1, reloaded.events(key).size)
        assertEquals(4, reloaded.nextTurn(key))          // 最大 turn + 1
    }

    @Test
    fun `file storage sanitizes unsafe key characters`() {
        val dir = tmp.newFolder()
        val storage = FileChampionEventStorage(dir)
        storage.save("a/b\\c d", ChampionEventState(uidSeq = 5))
        assertEquals(5, storage.load("a/b\\c d").uidSeq)
    }
}
