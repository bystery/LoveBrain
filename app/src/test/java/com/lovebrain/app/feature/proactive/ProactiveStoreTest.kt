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
 * ProactiveStore 的合同（§5.2 第 3 步：options、错误、停止/收尾语义，以及**模式**）。
 *
 * 模式这一项在前一轮是"没搬完、并且把原因写在注释里"——那时 `ComposerMode` 还长在
 * ViewModel 里，feature 包 import 不到。两个 enum 搬进 model 之后，这里开始真的断言模式：
 * 特别是"结束了且真拿到可展示开场才退回普通回复"这条规则，它现在整条落在一次赋值里。
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

    /** 这条把"生成失败/空结果被打回普通模式"的老毛病钉住：没结果就别改模式 */
    @Test
    fun `finishing without any option does not ask to leave proactive mode`() {
        val (s, r) = store()
        s.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r3")))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r3")))
        assertTrue("没产出结果就不该通知退出模式", r.effects.isEmpty())

        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r4", options(2))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r4")))
        assertEquals(
            "有可展示的开场才发一次退出通知",
            listOf(ProactiveStore.Effect.ExitedProactiveMode), r.effects
        )
    }

    // ─── 模式（§5.2 第 3 步的最后一项）────────────────────────────

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

        assertTrue("新一轮开始要清掉上一轮的开场", s.uiState.value.options.isEmpty())
        assertEquals(
            "但用户还在主动发入口里，模式不能自己跳回普通回复",
            ComposerMode.PROACTIVE, s.composerMode
        )
    }

    @Test
    fun `an opener that lands while the user has already left still resets the result area`() {
        val (s, r) = store()
        s.accept(ProactiveStore.Intent.EnterProactive)
        s.accept(ProactiveStore.Intent.ToggleComposer)   // 用户切回普通回复
        assertEquals("切出去本身通知一次", 1, r.effects.size)

        s.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(1))))
        s.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))

        assertEquals(
            "模式已经是普通回复，但结果区还挂在主动发那一类——要再通知归位一次",
            2, r.effects.count { it is ProactiveStore.Effect.ExitedProactiveMode }
        )
        assertEquals(ComposerMode.REPLY, s.composerMode)
    }

    @Test
    fun `failure text lands in the same single state object`() {
        val (s, _) = store()
        s.accept(ProactiveStore.Intent.Apply(ProactiveFailed("r5", "弱网，再试一次")))
        assertEquals("弱网，再试一次", s.uiState.value.error)
        assertTrue(s.uiState.value.options.isEmpty())

        s.accept(ProactiveStore.Intent.Clear)
        assertNull("清空要一次把 options 与 error 都清掉", s.uiState.value.error)
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
}
