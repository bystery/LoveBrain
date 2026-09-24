package com.lovebrain.app.feature.reply

import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyEvent
import com.lovebrain.app.model.ReplyRequested
import com.lovebrain.app.model.ReplySourceAliasMap
import com.lovebrain.app.model.ReplyStarted
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.ReplyStopped
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReplyStore 自己的合同（复核 §5.2 第 1 步搬出来的那三件事）。
 *
 * VM 侧的行为另有 GenerationRoundIdTest 等覆盖；这里只测"状态持有者"本身，
 * 因此不需要 Koin、不需要 Android：这也是把状态从 VM 里搬出来的直接收益之一。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplyStoreTest {

    private fun success() = GenerateResult.Success(
        LoveBrainResponse(
            response = ReplySchemes(recommended = "当然可以呀"),
            directions = listOf("A", "B", "C", "D")
        )
    )

    private class Recorder {
        val effects = mutableListOf<ReplyStore.Effect>()
        operator fun invoke(e: ReplyStore.Effect) { effects.add(e) }
    }

    /**
     * 相邻增量合并成一次发布。
     *
     * 这条从 VM 搬出来时才真的可测：以前要验证节流就得开一整个 ViewModel，
     * 而"合并"和"reducer 是唯一写入口"两件事互相牺牲的历史（指导书 §2.2 提到的）
     * 正发生在这段代码上。
     */
    @Test
    fun `adjacent chunks are coalesced into one published update`() = runTest {
        val store = ReplyStore(scope = this, flushIntervalMs = 50L)
        store.accept(ReplyStore.Intent.Apply(ReplyRequested("r1")))
        store.accept(ReplyStore.Intent.Apply(ReplyChunk("r1", "你好")))
        store.accept(ReplyStore.Intent.Apply(ReplyChunk("r1", "，我是")))
        store.accept(ReplyStore.Intent.Apply(ReplyChunk("r1", "军师")))

        assertEquals("没到节拍前不得发布", "", store.uiState.value.streamingCoreText)

        advanceTimeBy(60L)
        runCurrent()
        assertEquals(
            "一个节拍后，三条增量应作为一次合并文本发布",
            "你好，我是军师", store.uiState.value.streamingCoreText
        )
    }

    /** 被取代请求的增量不能"顺带写个流式文本"——这是 reducer 唯一入口的真正含义 */
    @Test
    fun `a chunk from a superseded request never reaches the state`() = runTest {
        val store = ReplyStore(scope = this, flushIntervalMs = 50L)
        store.accept(ReplyStore.Intent.Apply(ReplyRequested("old")))
        store.accept(ReplyStore.Intent.Apply(ReplyRequested("new")))
        store.accept(ReplyStore.Intent.Apply(ReplyChunk("old", "上一轮的残留")))

        advanceTimeBy(60L)
        runCurrent()
        assertEquals("", store.uiState.value.streamingCoreText)
    }

    /**
     * 成功结果落定时只发一次副作用，且带上**那一刻**的引用配对。
     *
     * 迟到/重复的 Completed 不再发一次，否则历史版本会被凭空多出一条，
     * 累计生成次数也会虚增。
     */
    @Test
    fun `SuccessCommitted fires once and carries the alias map of that round`() = runTest {
        val recorder = Recorder()
        val store = ReplyStore(scope = this, onEffect = { recorder(it) })
        val events: List<ReplyEvent> = listOf(
            ReplyRequested("r9"),
            ReplyStarted("r9"),
            ReplySourceAliasMap("r9", mapOf("her-0" to "msg-0")),
            ReplyCompleted("r9", success())
        )
        events.forEach { store.accept(ReplyStore.Intent.Apply(it)) }

        val committed = recorder.effects.filterIsInstance<ReplyStore.Effect.SuccessCommitted>()
        assertEquals("一次成功只应发一条 SuccessCommitted", 1, committed.size)
        assertEquals(
            "Effect 必须携带与这次成功配对的别名表（事后再读状态会拿到下一轮的）",
            mapOf("her-0" to "msg-0"), committed.single().sourceAliasMap
        )
        assertTrue(committed.single().result is GenerateResult.Success)

        // 重复投递同一个终态：状态不变，也不再发副作用
        store.accept(ReplyStore.Intent.Apply(ReplyCompleted("r9", success())))
        assertEquals(
            "重复/迟到的 Completed 不得再发一次",
            1, recorder.effects.filterIsInstance<ReplyStore.Effect.SuccessCommitted>().size
        )
    }

    /** 停止要丢弃未发布的半截增量，否则停止后面板还会自己"长出"一段文本 */
    @Test
    fun `discarding pending chunks drops text that was never published`() = runTest {
        val store = ReplyStore(scope = this, flushIntervalMs = 50L)
        store.accept(ReplyStore.Intent.Apply(ReplyRequested("r5")))
        store.accept(ReplyStore.Intent.Apply(ReplyChunk("r5", "半截没收尾的话")))
        store.accept(ReplyStore.Intent.DiscardPendingChunks)

        advanceTimeBy(200L)
        runCurrent()
        assertEquals("", store.uiState.value.streamingCoreText)

        store.accept(ReplyStore.Intent.Apply(ReplyStopped("r5")))
        assertEquals("停止后不该再有任何流式文本", "", store.uiState.value.streamingCoreText)
    }

    /**
     * 发完就该没有常驻协程。
     *
     * 这条是给上面那个 `while (true)` 修复上的锁：合并循环的存续条件必须与它的职责一致。
     * 不写这一格，将来有人为了"简单"把它改回常驻，只有真机上才会看见
     * "每 50ms 醒一次，一直醒到 VM 死掉"。
     */
    @Test
    fun `the flush loop ends on its own once the buffer is drained`() = runTest {
        val host = kotlinx.coroutines.CoroutineScope(
            coroutineContext + kotlinx.coroutines.SupervisorJob()
        )
        try {
            val store = ReplyStore(scope = host, flushIntervalMs = 50L)
            store.accept(ReplyStore.Intent.Apply(ReplyRequested("r1")))
            store.accept(ReplyStore.Intent.Apply(ReplyChunk("r1", "发得出去的话")))

            advanceTimeBy(60L)
            runCurrent()
            assertEquals("发得出去的话", store.uiState.value.streamingCoreText)

            val job = host.coroutineContext[kotlinx.coroutines.Job]!!
            val left = job.children.toList()
            assertTrue("缓冲已空，却还有活着的子协程：$left", left.isEmpty())
        } finally {
            host.cancel()
        }
    }

    /**
     * 被拒事件从 `onStaleEvent` 出去，成功的不进这个出口。
     *
     * 这条是给"搬家时差点丢掉的观测"上的锁：VM 以前靠 reducer 原样返回同一个对象来写
     * `… rejected (stale requestId)`，归约搬进 store 之后那个信号在类外面看不见。
     * 关键点是**增量在 accept 时还没被判定**（它在下一个节拍才归约），
     * 所以拒绝发生在 flush，而不是投事件那一刻。
     */
    @Test
    fun `a rejected event is reported once, at the moment it is actually judged`() = runTest {
        val stale = mutableListOf<ReplyEvent>()
        val store = ReplyStore(
            scope = this, flushIntervalMs = 50L, onStaleEvent = { stale.add(it) }
        )
        store.accept(ReplyStore.Intent.Apply(ReplyRequested("new")))
        store.accept(ReplyStore.Intent.Apply(ReplyChunk("old", "迟到半句")))
        assertTrue("收进缓冲时不该抢先判定", stale.isEmpty())

        advanceTimeBy(60L)
        runCurrent()
        assertEquals("flush 时才被判拒", listOf("old"), stale.map { it.requestId })

        stale.clear()
        store.accept(ReplyStore.Intent.Apply(ReplyCompleted("new", success())))
        assertEquals("归约成功的事件不进这个出口", emptyList<String>(), stale.map { it.requestId })
    }

    /** 原地替换结果（改写 / undo / 版本回退）不伪装成请求事件，但仍然只能走这一个入口 */
    @Test
    fun `replacing a result in place goes through the same single writer`() = runTest {
        val store = ReplyStore(scope = this)
        store.accept(ReplyStore.Intent.Apply(ReplyRequested("r7")))
        store.accept(ReplyStore.Intent.Apply(ReplyCompleted("r7", success())))
        val first = store.currentResult

        val replaced = GenerateResult.Success(
            LoveBrainResponse(
                response = ReplySchemes(recommended = "改完之后的一句话"),
                directions = listOf("A", "B", "C", "D")
            )
        )
        store.accept(ReplyStore.Intent.ReplaceResult(replaced))

        assertEquals(replaced, store.currentResult)
        assertTrue("替换是新对象，不能原地改", first !== store.currentResult)
    }
}
