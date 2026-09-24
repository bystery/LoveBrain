package com.lovebrain.app.feature.proactive

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
 * ProactiveStore 的合同（§5.2 第 3 步搬出来的那三样：options、错误、停止/收尾语义）。
 * 模式那一样本轮仍在 VM 里，原因写在 LoveBrainViewModel 的 _composerMode 注释中，
 * 这里就不断言模式——不假装做了没做的事。
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
            "有可展示的开场才发一次 FinishedWithResults",
            listOf(ProactiveStore.Effect.FinishedWithResults), r.effects
        )
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
