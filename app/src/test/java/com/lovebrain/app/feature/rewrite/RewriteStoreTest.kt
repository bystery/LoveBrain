package com.lovebrain.app.feature.rewrite

import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.SchemeFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RewriteStore 自己的合同（§5.2 第 5 步搬出来的那三本账合成一本之后该说的话）。
 *
 * 和另外四条链的 store 测试同构：不开 ViewModel、不需要 Koin / Android / 设备。
 * "这次回调还算不算数"以前是 VM 里两个裸字符串字段，谁也测不到它；
 * 现在它是 store 的在途身份，下面每一格都在没有 ViewModel 的情况下把它判一遍。
 */
class RewriteStoreTest {

    private class Recorder {
        val effects = mutableListOf<RewriteStore.Effect>()
        operator fun invoke(e: RewriteStore.Effect) { effects.add(e) }
    }

    private fun id(
        requestId: String = "r1",
        contextId: String = "kb1_111",
        key: String = "STYLE:A",
        option: String = "短一点"
    ) = RewriteStore.Identity(requestId, contextId, key, option)

    private fun begin(
        store: RewriteStore,
        identity: RewriteStore.Identity = id(),
        previousReply: String = "原文",
        previousFeedback: SchemeFeedback = SchemeFeedback.LIKED
    ) = store.accept(
        RewriteStore.Intent.Begin(identity, previousReply, previousFeedback)
    )

    private fun Recorder.only(effectKind: (RewriteStore.Effect) -> Boolean) =
        effects.filter(effectKind)

    // ─── 发起 ────────────────────────────────────────────────────

    @Test
    fun `begin marks the card loading and remembers the version being replaced`() {
        val store = RewriteStore()
        begin(store)

        assertEquals(RewriteState.Loading("短一点"), store.stateOf("STYLE:A"))
        assertEquals(id(), store.currentInFlight)
        assertEquals(1, store.historySize("STYLE:A"))
    }

    @Test
    fun `the feedback travels with the reply it belonged to`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })
        begin(store, previousFeedback = SchemeFeedback.DISLIKED)
        store.accept(RewriteStore.Intent.Succeeded(id(), "新正文"))
        store.accept(RewriteStore.Intent.UndoRequested("STYLE:A"))

        val restore = recorder.effects
            .filterIsInstance<RewriteStore.Effect.RestoreVersion>().single()
        assertEquals("原文", restore.reply)
        assertEquals(
            "撤销要连反馈一起回来，不能只贴正文",
            SchemeFeedback.DISLIKED, restore.feedback
        )
    }

    // ─── 成功 ────────────────────────────────────────────────────

    @Test
    fun `a live success applies once and clears the in-flight identity`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })
        begin(store)
        store.accept(RewriteStore.Intent.Succeeded(id(), "短一点的新正文"))

        assertEquals(RewriteState.Done("短一点的新正文"), store.stateOf("STYLE:A"))
        assertNull("成功后不再有在途改写", store.currentInFlight)
        val applied = recorder.effects.filterIsInstance<RewriteStore.Effect.ApplyRewrite>().single()
        assertEquals("STYLE:A", applied.identityKey)
        assertEquals("短一点的新正文", applied.newReply)
        assertEquals(1, recorder.effects.count { it is RewriteStore.Effect.ResetFeedback })
        assertEquals(
            "计数只发一次，重复投递不得再加",
            1, recorder.effects.count { it is RewriteStore.Effect.RewriteCounted }
        )

        // 迟到的同一份成功再投一次：在途身份已经收走，不该再发任何副作用
        val before = recorder.effects.size
        store.accept(RewriteStore.Intent.Succeeded(id(), "短一点的新正文"))
        assertEquals(before, recorder.effects.size)
    }

    // ─── 三道身份闸 ──────────────────────────────────────────────

    @Test
    fun `a result from a superseded rewrite is dropped`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })
        begin(store, id("A"))
        begin(store, id("B", key = "STYLE:B"))   // 更新的一次改写接管了在途身份

        val before = recorder.effects.size
        store.accept(RewriteStore.Intent.Succeeded(id("A"), "A 的迟到结果"))

        assertEquals(before, recorder.effects.size)
        assertEquals("B 的卡片仍挂在改写中", RewriteState.Loading("短一点"), store.stateOf("STYLE:B"))
    }

    @Test
    fun `a result whose foreground lease moved on is dropped and stops the spinner`() {
        val recorder = Recorder()
        val store = RewriteStore(isCurrentRequest = { false }, onEffect = { recorder(it) })
        begin(store)

        store.accept(RewriteStore.Intent.Succeeded(id(), "没人认领的正文"))

        assertTrue(recorder.effects.isEmpty())
        assertNull("协调器已换人：这张卡不该永远转圈", store.stateOf("STYLE:A"))
        assertNull(store.currentInFlight)
    }

    @Test
    fun `a result arriving after the round turned the page is dropped`() {
        val recorder = Recorder()
        val live = mutableSetOf("kb1_111")
        val store = RewriteStore(
            isRoundAlive = { it in live },
            onEffect = { recorder(it) }
        )
        begin(store)
        live.clear() // 切库 / 新轮 / 保存清空：轮次指纹不再作数

        store.accept(RewriteStore.Intent.Succeeded(id(), "贴到别人那一轮上的正文"))

        assertTrue(recorder.effects.isEmpty())
        assertNull(store.stateOf("STYLE:A"))
    }

    // ─── 空 / 失败 / 取消 ────────────────────────────────────────

    @Test
    fun `an empty reply is reported as an error and never patched in`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })
        begin(store)
        store.accept(RewriteStore.Intent.Emptied(id()))

        assertEquals(RewriteState.Error("改写返回空结果"), store.stateOf("STYLE:A"))
        assertTrue(recorder.only { it is RewriteStore.Effect.ApplyRewrite }.isEmpty())
        assertEquals(1, store.historySize("STYLE:A"))
    }

    @Test
    fun `a failure after the card was cancelled does not resurrect an error`() {
        val store = RewriteStore()
        begin(store)
        store.accept(RewriteStore.Intent.RequestCancel("STYLE:A"))
        store.accept(RewriteStore.Intent.Failed(id()))

        assertNull("用户已经取消，异常回调不该再冒出一个错误条", store.stateOf("STYLE:A"))
    }

    @Test
    fun `a terminal event retires the in-flight identity so a second one changes nothing`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })
        begin(store)
        store.accept(RewriteStore.Intent.Emptied(id()))

        assertNull("空正文已经是这一轮的终点，不该还占着在途身份", store.currentInFlight)
        val before = recorder.effects.size
        store.accept(RewriteStore.Intent.Failed(id()))
        assertEquals("迟到的第二个终态既不发效果也不改状态", before, recorder.effects.size)
        assertEquals(RewriteState.Error("改写返回空结果"), store.stateOf("STYLE:A"))
    }

    @Test
    fun `failure while still in flight keeps the original text and offers retry`() {
        val store = RewriteStore()
        begin(store)
        store.accept(RewriteStore.Intent.Failed(id()))

        assertEquals(RewriteState.Error("改写失败，可重试"), store.stateOf("STYLE:A"))
        assertEquals(
            "失败的这一次不能把历史吃掉——上一版还要能撤",
            1, store.historySize("STYLE:A")
        )
    }

    @Test
    fun `cancelling a card that is not the in-flight one stops nothing`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })
        begin(store, id("A"))
        store.accept(RewriteStore.Intent.Begin(id("B", key = "DIRECTION:F"), "F 原文", SchemeFeedback.NONE))

        // A 已经不是在途那一个了，但它可能还挂着 Loading；点 A 的取消不许停掉 B 的任务
        store.accept(RewriteStore.Intent.RequestCancel("STYLE:A"))

        assertTrue(
            "不该发出停止在跑任务的效果",
            recorder.effects.filterIsInstance<RewriteStore.Effect.StopRunningRewrite>().isEmpty()
        )
        assertNull(store.stateOf("STYLE:A"))
        assertEquals(RewriteState.Loading("短一点"), store.stateOf("DIRECTION:F"))
        assertEquals(id("B", key = "DIRECTION:F"), store.currentInFlight)
    }

    @Test
    fun `cancelling the in-flight card asks to stop exactly that request and keeps history`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })
        begin(store, id("r9"))
        store.accept(RewriteStore.Intent.RequestCancel("STYLE:A"))

        assertEquals(
            listOf("r9"),
            recorder.effects.filterIsInstance<RewriteStore.Effect.StopRunningRewrite>()
                .map { it.requestId }
        )
        assertNull(store.currentInFlight)
        assertEquals("取消只是不再显示改写中，撤销入口要留着", 1, store.historySize("STYLE:A"))
    }

    // ─── 撤销的两步式 ────────────────────────────────────────────

    @Test
    fun `undo hands the version out before it is consumed`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })
        begin(store)
        store.accept(RewriteStore.Intent.Succeeded(id(), "新正文"))

        store.accept(RewriteStore.Intent.UndoRequested("STYLE:A"))
        val restore = recorder.effects.filterIsInstance<RewriteStore.Effect.RestoreVersion>().single()
        assertEquals("原文", restore.reply)
        assertEquals(
            "调用方还没确认，历史不能被提前弹掉",
            1, store.historySize("STYLE:A")
        )

        store.accept(RewriteStore.Intent.UndoCommitted("STYLE:A"))
        assertEquals(0, store.historySize("STYLE:A"))
        assertFalse(store.stateOf("STYLE:A") is RewriteState.Done)
    }

    @Test
    fun `undo on a card without history asks for nothing`() {
        val recorder = Recorder()
        val store = RewriteStore(onEffect = { recorder(it) })

        store.accept(RewriteStore.Intent.UndoRequested("STYLE:Z"))

        assertTrue(recorder.effects.isEmpty())
    }

    // ─── 收起 / 翻页 ─────────────────────────────────────────────

    @Test
    fun `collapse only takes away settled cards, never a running one`() {
        val store = RewriteStore()
        begin(store)
        store.accept(RewriteStore.Intent.Collapse("STYLE:A"))
        assertEquals("改写进行中没有收起这回事", RewriteState.Loading("短一点"), store.stateOf("STYLE:A"))

        store.accept(RewriteStore.Intent.Succeeded(id(), "新正文"))
        store.accept(RewriteStore.Intent.Collapse("STYLE:A"))
        assertNull(store.stateOf("STYLE:A"))
        assertEquals("收起只清状态，历史留着还能撤", 1, store.historySize("STYLE:A"))
    }

    @Test
    fun `a new page wipes states history and the in-flight identity together`() {
        val store = RewriteStore()
        begin(store, id("A"))
        begin(store, id("B", key = "STYLE:B"), previousReply = "B 原文")
        store.accept(RewriteStore.Intent.Succeeded(id("B", key = "STYLE:B"), "B 的新正文"))

        store.accept(RewriteStore.Intent.RoundReset)

        assertNull(store.stateOf("STYLE:A"))
        assertNull(store.stateOf("STYLE:B"))
        assertEquals(0, store.historySize("STYLE:B"))
        assertNull(store.currentInFlight)
        assertEquals(RewriteStore.UiState(), store.uiState.value)
    }
}
