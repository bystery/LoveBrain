package com.lovebrain.app.feature.suggest

import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.SuggestEnded
import com.lovebrain.app.model.SuggestFailed
import com.lovebrain.app.model.SuggestResult
import com.lovebrain.app.model.SuggestStarted
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.model.SuggestTips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SuggestStore 的合同（§5.2 第 2 步搬出来的四样：请求身份、缓存配对、校验结果落状态、
 * 流式 tips）。不启动 VM、不需要 Android。
 */
class SuggestStoreTest {

    private class Recorder {
        val effects = mutableListOf<SuggestStore.Effect>()
        operator fun invoke(e: SuggestStore.Effect) { effects.add(e) }
    }

    private fun identity(requestId: String, date: String = "2026-09-24", kbName: String = "kb") =
        SuggestStore.Identity(
            requestId = requestId,
            kbName = kbName,
            kb = KnowledgeBase(name = kbName, displayName = kbName),
            date = date,
            contextFingerprint = "fp-$requestId",
            promptVersion = "pv-1"
        )

    private fun tips(n: Int) = List(n) { SuggestTip(id = "tip-$it") }

    private fun store(
        owns: (String) -> Boolean = { true },
        recorder: Recorder = Recorder()
    ) = SuggestStore(isCurrentRequest = owns, onEffect = { recorder(it) }) to recorder

    @Test
    fun `beginning a request clears the previous result and error`() {
        val (s, _) = store()
        s.accept(SuggestStore.Intent.ServeFromCache(DailySuggestion(goal = "上一轮的目标")))
        s.accept(SuggestStore.Intent.Fail("上一次没配好"))
        assertEquals("前置条件：先要有旧结果和旧错误", "上一次没配好", s.uiState.value.error)

        s.accept(SuggestStore.Intent.Begin(identity("r1")))

        assertNull("Begin 必须清掉上一轮的结果", s.uiState.value.suggestion)
        assertNull("也要清掉上一轮的错误", s.uiState.value.error)
        assertEquals("r1", s.uiState.value.inFlight?.requestId)
    }

    /** Engine 每次回报的是"目前完整的清单"，晚到的短清单不许把已渲染的条目抹掉 */
    @Test
    fun `a shorter late tip list never shrinks what is already rendered`() {
        val (s, _) = store()
        s.accept(SuggestStore.Intent.Begin(identity("r2")))
        s.accept(SuggestStore.Intent.Apply(SuggestStarted("r2")))
        s.accept(SuggestStore.Intent.Apply(SuggestTips("r2", tips(6))))
        assertEquals(6, s.uiState.value.streamingTips.size)

        s.accept(SuggestStore.Intent.Apply(SuggestTips("r2", tips(3))))
        assertEquals("迟到/重发的短清单不得让列表倒退", 6, s.uiState.value.streamingTips.size)

        s.accept(SuggestStore.Intent.Apply(SuggestTips("r2", tips(8))))
        assertEquals("更长的清单要正常前进（§5.2 的 6–8 条）", 8, s.uiState.value.streamingTips.size)

        s.accept(SuggestStore.Intent.Apply(SuggestEnded("r2")))
        assertEquals("收尾后流式半成品要清掉", 0, s.uiState.value.streamingTips.size)
    }

    /** 不属于当前在途请求的事件：状态一个字都不改，也不发副作用 */
    @Test
    fun `events from a request that no longer owns the slot are dropped`() {
        var current = "rA"
        val recorder = Recorder()
        val s = SuggestStore(isCurrentRequest = { it == current }, onEffect = { recorder(it) })
        s.accept(SuggestStore.Intent.Begin(identity("rA")))
        s.accept(SuggestStore.Intent.Begin(identity("rB")))
        current = "rB"

        s.accept(SuggestStore.Intent.Apply(SuggestResult("rA", DailySuggestion(goal = "被取代的旧请求想写进来"))))
        assertNull("旧请求不得把结果写回状态", s.uiState.value.suggestion)
        assertTrue("旧请求也不得触发缓存写入", recorder.effects.isEmpty())
    }

    /**
     * 缓存写入用的是**发起时**冻结的身份。
     *
     * 这两条把 VM 旧注释的承诺变成断言：跨午夜完成仍写回发起那一天；
     * 生成期间切了 KB 也仍写给发起时那个库（旧实现是完成时回读实时状态）。
     */
    @Test
    fun `persist carries the identity frozen at start, not whatever is current now`() {
        val (s, recorder) = store()
        val started = identity("r9", date = "2026-09-24", kbName = "kb-old")
        s.accept(SuggestStore.Intent.Begin(started))
        s.accept(SuggestStore.Intent.Apply(SuggestStarted("r9")))
        // 期间"切了库、过了午夜"——store 不认识实时的 activeKb / today，所以无从被污染
        s.accept(SuggestStore.Intent.Apply(SuggestResult("r9", DailySuggestion(goal = "跨午夜才完成"))))

        val persist = recorder.effects.filterIsInstance<SuggestStore.Effect.Persist>().single()
        assertEquals("2026-09-24", persist.identity.date)
        assertEquals("kb-old", persist.identity.kbName)
        assertEquals("fp-r9", persist.identity.contextFingerprint)
        assertEquals("跨午夜才完成", persist.suggestion.goal)
    }

    @Test
    fun `failure and manual stop land in the same single state object`() {
        val (s, _) = store()
        s.accept(SuggestStore.Intent.Begin(identity("r10")))
        s.accept(SuggestStore.Intent.Apply(SuggestTips("r10", tips(5))))
        s.accept(SuggestStore.Intent.Apply(SuggestFailed("r10", "模型超时")))
        assertEquals("模型超时", s.uiState.value.error)

        s.accept(SuggestStore.Intent.StoppedByUser)
        assertEquals("已手动停止", s.uiState.value.error)
        assertEquals(0, s.uiState.value.streamingTips.size)
    }

    /** 缓存命中是"直接出结果"，不该再写一次缓存 */
    @Test
    fun `serving from cache emits no persist effect`() {
        val (s, recorder) = store()
        s.accept(SuggestStore.Intent.ServeFromCache(DailySuggestion(goal = "命中缓存")))
        assertEquals("命中缓存", s.uiState.value.suggestion?.goal)
        assertTrue(recorder.effects.isEmpty())
    }

    /** 旧请求的收尾不许把新请求的在途身份清掉 */
    @Test
    fun `settling an old request leaves the new one in flight`() {
        val (s, _) = store()
        s.accept(SuggestStore.Intent.Begin(identity("old")))
        s.accept(SuggestStore.Intent.Begin(identity("new")))

        s.accept(SuggestStore.Intent.Settle("old"))
        assertEquals("在途身份属于 new，不能被 old 的收尾清掉", "new", s.uiState.value.inFlight?.requestId)

        s.accept(SuggestStore.Intent.Settle("new"))
        assertNull(s.uiState.value.inFlight)
    }
}
