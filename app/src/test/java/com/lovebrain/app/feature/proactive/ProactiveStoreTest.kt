package com.lovebrain.app.feature.proactive

import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveFailed
import com.lovebrain.app.model.ProactiveFirstToken
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ProactiveStarted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProactiveStore 的合同（第5节第2条 第 3 步：options、错误、停止/收尾语义，以及**模式**）。
 *
 * 模式这一项已经真的搬进来了：`ComposerMode` 是 store 的 UiState 字段，本类直接断言它。
 * 断得最狠的是  那条——**结果区只在用户明确动作时归位**：`ProactiveEnded` 既不写状态、
 * 也不发 `ExitedProactiveMode`，所以生成完的开场一直留在屏幕上（旧实现是"有结果就自动
 * 退回普通回复"，那正是"生成完结果就没了"的成因）。
 */
class ProactiveStoreTest {

    private class Recorder {
        val effects = mutableListOf<ProactiveStore.Effect>()
        operator fun invoke(e: ProactiveStore.Effect) { effects.add(e) }
    }

    private fun options(n: Int) = List(n) { ProactiveOption(text = "开场 $it", angle = "角度 $it") }

    private fun store(owns: (String) -> Boolean = { true }, r: Recorder = Recorder()) =
        ProactiveStore(isCurrentRequest = owns, onEffect = { r(it) }) to r

    @Test
    fun `starting a new attempt clears the previous options and error`() {
        val (s, _) = store()
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(3))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveFailed("r1", "刚才失败了")))
        assertEquals(3, s.uiState.value.options.size)

        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r2")))
        assertTrue("新一轮开始时必须清干净", s.uiState.value.options.isEmpty())
        assertNull(s.uiState.value.error)
    }

    /**
     *  / 第4节第1条第3条：生成结束**不许**把结果从屏幕上换走。
     *
     * 反例（这条会变红的实现）：`ProactiveEnded` 里有结果就 `exitProactive()` 或
     * 发 `ExitedProactiveMode` —— 那正是"生成完直接闪退/面板切成普通回复"的那条链，
     * 结果对象还活着、视图却没了。
     */
    @Test
    fun `finishing a run keeps the openers and never asks to leave proactive mode`() {
        val (s, r) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r1")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(3))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))

        assertTrue("结束之后的结果区归位只能由用户明确动作发出，实际收到：" + r.effects, r.effects.isEmpty())
        assertEquals("模式也不许自己跳回普通回复", ComposerMode.PROACTIVE, s.composerMode)
        assertEquals("三条开场一条都不许少", 3, s.uiState.value.options.size)
    }

    /**
     * 没生成出来也不写模式，而且**结束事件不许把就地失败文案抹掉**——
     * 第4节第1条第5条 要的是"失败后就地短提示、可再生成"，把 error 清了用户就只剩下一个空白面板。
     *
     * 反例：`ProactiveEnded` 里做 `copy(error = null)`（收尾顺手重置状态）或顺手退模式。
     */
    @Test
    fun `finishing without any option keeps the mode and the in-place failure text`() {
        val (s, r) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r2")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveFailed("r2", "生成超时，已保留部分内容")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r2")))

        assertTrue("没产出结果就不该通知退出模式", r.effects.isEmpty())
        assertEquals(ComposerMode.PROACTIVE, s.composerMode)
        assertEquals(
            "就地提示要留着，用户才知道该再点一次生成开场",
            "生成超时，已保留部分内容", s.uiState.value.error
        )
    }

    /**
     * 手动停止这一条链上，store 收到的其实是**一条归属已失效**的 ProactiveEnded
     * （VM 先回收租约再补发事件）。停完之后屏幕上还是已经看见过的那几条。
     *
     * 反例：store 改成"不看归属、Ended 一到就清流式半成品/退模式"（锦囊的 StoppedByUser
     * 那种写法），或者 `isCurrentRequest` 那道门被拆掉让迟到事件写进来。
     */
    @Test
    fun `a stop that revokes the lease first still leaves the generated openers on screen`() {
        val owns = booleanArrayOf(true)
        val r = Recorder()
        val s = ProactiveStore(isCurrentRequest = { owns[0] }, onEffect = { r(it) })

        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(2))))
        owns[0] = false                                   // ← operationCoordinator.stopCurrent 先回收租约
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))  // ← VM 补发的那一条
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(5))))  // 迟到的写请求

        assertEquals(2, s.uiState.value.options.size)
        assertTrue(r.effects.isEmpty())
        assertEquals(ComposerMode.PROACTIVE, s.composerMode)
    }

    // ─── 模式（第5节第2条 第 3 步的最后一项）────────────────────────────

    @Test
    fun `toggling into proactive mode touches nothing else`() {
        val (s, r) = store()
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(2))))

        s.accept(ProactiveStore.Intent.ToggleComposer)

        assertEquals(ComposerMode.PROACTIVE, s.composerMode)
        assertEquals("切换只是切模式，不顺手清结果", 2, s.uiState.value.options.size)
        assertTrue("进来这条动作不该发任何效果", r.effects.isEmpty())
    }

    @Test
    fun `toggling out of proactive mode clears the leftovers once`() {
        val (s, r) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(3))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveFailed("r1", "顺带一条错误")))

        s.accept(ProactiveStore.Intent.ToggleComposer)

        assertEquals(ComposerMode.REPLY, s.composerMode)
        assertTrue("退出主动发要把残留开场一起清掉", s.uiState.value.options.isEmpty())
        assertNull(s.uiState.value.error)
        assertEquals(
            "退出只通知一次", listOf(ProactiveStore.Effect.ExitedProactiveMode), r.effects
        )

        s.accept(ProactiveStore.Intent.ToggleComposer)
        s.accept(ProactiveStore.Intent.ToggleComposer)
        s.accept(ProactiveStore.Intent.ExitProactive)
        assertEquals(
            "再进再出、以及对普通回复点退出，都不该多发一次",
            2, r.effects.count { it is ProactiveStore.Effect.ExitedProactiveMode }
        )
    }

    @Test
    fun `exiting without the toggle keeps the opener that was already generated`() {
        val (s, _) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(2))))

        s.accept(ProactiveStore.Intent.ExitProactive)

        assertEquals(ComposerMode.REPLY, s.composerMode)
        assertEquals("停止不是清空：已经拿到的开场还要能看", 2, s.uiState.value.options.size)
    }

    @Test
    fun `a new attempt clears results but keeps the user inside proactive mode`() {
        val (s, _) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(2))))

        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r2")))

        assertTrue("新一轮开始要清掉的开场", s.uiState.value.options.isEmpty())
        assertEquals(
            "但用户还在主动发入口里，模式不能自己跳回普通回复",
            ComposerMode.PROACTIVE, s.composerMode
        )
    }

    /**
     * 开场落在用户已经切回普通回复之后：结果照样存进状态（下一次切回来还得看得见），
     * 但**不再替用户决定结果区该显示什么**。
     *
     * 反例：这里再补发一次 `ExitedProactiveMode`——那是  删掉的"自动归位"的另一半，
     * 它会在一轮普通回复刚出结果的当口，把结果区又抢回主动发、或反之抢走。
     */
    @Test
    fun `an opener that lands after the user left is stored but does not touch the result area`() {
        val (s, r) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.ToggleComposer)   // 用户切回普通回复
        assertEquals("切出去本身通知一次", 1, r.effects.size)

        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(1))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))

        assertEquals(
            "结果区已经在普通回复那一侧，结束与到达都不许再发归位通知：" + r.effects,
            1, r.effects.count { it is ProactiveStore.Effect.ExitedProactiveMode }
        )
        assertEquals(ComposerMode.REPLY, s.composerMode)
        assertEquals("内容还得留着，别用户切回来就空了", 1, s.uiState.value.options.size)
    }

    @Test
    fun `failure text lands in the same single state object`() {
        val (s, _) = store()
        s.accept(ProactiveStore.Intent.Apply(ProactiveFailed("r5", "弱网，再试一次")))
        assertEquals("弱网，再试一次", s.uiState.value.error)
        assertTrue(s.uiState.value.options.isEmpty())

        // /：`Intent.Clear` 已退役（零调用点 + 语义被有主人的出口覆盖，见 ProactiveStore 内注释）。
        // "一次把 options 与 error 都清掉"这件事由新一轮开始的 ProactiveStarted 承担——这一格顺着改钉它。
        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r6")))
        assertNull("新一轮开始必须一次把 options 与 error 都清掉", s.uiState.value.error)
        assertTrue(s.uiState.value.options.isEmpty())
    }

    @Test
    fun `events from a request that no longer owns the slot are dropped`() {
        // 归属已经交给新请求了，旧 requestId 的事件一个字都不许写进来。
        var current = "new"
        val recorder = Recorder()
        val s = ProactiveStore(isCurrentRequest = { it == current }, onEffect = { recorder(it) })

        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("new")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("old", options(4))))
        assertTrue("被取代的旧请求不得写进 options", s.uiState.value.options.isEmpty())

        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("new", options(2))))
        assertEquals("在途请求自己要正常生效", 2, s.uiState.value.options.size)

        s.accept(ProactiveStore.Intent.Apply(ProactiveFirstToken("old", 999L)))
        assertTrue("旧请求的耗时不得再发副作用", recorder.effects.isEmpty())
    }

    @Test
    fun `first token timing is reported out, not stored here`() {
        val (s, r) = store()
        s.accept(ProactiveStore.Intent.Apply(ProactiveFirstToken("r6", 412L)))
        assertEquals(
            listOf(ProactiveStore.Effect.FirstTokenObserved(412L)),
            r.effects
        )
        assertNull("它不是锦囊自己的状态", s.uiState.value.error)
    }

    // ─── §11.3：主动发 7-10 条数量检查 ─────────────────────────────

    /**
     * §11.3：不足 7 条时保留已有候选并如实提示"本次只生成 N 条，可重新生成"。
     * 反例：closeRun 清掉了 options → 已有候选被抹掉，用户看不到已生成的内容；
     * 反例：closeRun 不写 error → 用户不知道这次数量不够、不知道该不该再点一次。
     */
    @Test
    fun `fewer than seven options keeps them and notes the count for regen`() {
        val (s, _) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r1")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(3))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))

        assertEquals("候选一条都不许少", 3, s.uiState.value.options.size)
        assertEquals("本次只生成 3 条，可重新生成", s.uiState.value.error)
    }

    /**
     * §11.3：7-10 条算完整成功，不写任何提示。
     * 反例：7 条也写"不足"提示 → 用户以为生成不完整。
     */
    @Test
    fun `seven to ten options is complete success with no notice`() {
        val (s, _) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r1")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(7))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))

        assertEquals(7, s.uiState.value.options.size)
        assertNull("7 条算完整，不该念不足", s.uiState.value.error)
    }

    /**
     * §11.3：已有失败文案时不许用数量提示盖掉它。
     */
    @Test
    fun `fewer than seven with existing failure keeps the failure text`() {
        val (s, _) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r1")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(2))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveFailed("r1", "生成超时，已保留部分内容")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))

        assertEquals("已有的开场还在", 2, s.uiState.value.options.size)
        assertEquals("失败文案不许被数量提示盖掉", "生成超时，已保留部分内容", s.uiState.value.error)
    }
}
