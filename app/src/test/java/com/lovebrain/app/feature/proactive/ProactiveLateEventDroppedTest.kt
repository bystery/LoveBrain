package com.lovebrain.app.feature.proactive

import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveFailed
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ProactiveStarted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第4节第1条第5条 第三条：晚到事件按 requestId 丢弃，**不得复活已取消的任务**。
 *
 * [ProactiveStoreTest] 里那一格 `events from a request that no longer owns the slot are dropped`
 * 判的是"归属已经交给新请求"时旧请求的**写请求**进不来。这一族补的是它没覆盖的两种形状，
 * 而这两种恰好是 第4节第1条第5条 那半句话里最容易被漏掉的：
 *
 *  - **归属那一侧是空的**（点停止之后协调器的租约已经回收，PROACTIVE 不再有任何在管任务）。
 *    这是停止那条链的真实形状：VM 先 `stopCurrent` 再补发 `ProactiveEnded`，
 *    所以这一批事件全部撞在门上。门一旦只挡 `ProactiveOptions`、放行 `ProactiveEnded`
 *    或 `ProactiveFailed`，屏幕就会在用户按下停止之后自己长出内容或长出错误。
 *  - **收尾分支也在门后面**：[ProactiveStore] 的 closeRun 会在"什么都没有"时补一句就地短提示。
 *    这句提示如果发在门**前面**（或在 Ended 里无条件写），一次普通的点停止就会被说成
 *    一次失败——"已取消的任务复活并自称失败"就是这个形状。下面第二格专门钉这一条。
 *
 * 门本身仍然只认"谁在跑"那一本账（调用方注入的闭包，真源是
 * [com.lovebrain.app.domain.ForegroundOperationCoordinator]）：本类不新造第二份请求身份，
 * 也不改取消重抛与供应商冻结那两条语义（第4节第1条第6条）。
 * 反过来，门只关事件流：[ProactiveStore.Intent.Fail] / [ProactiveStore.Intent.ToggleComposer]
 * 是用户自己的动作，取消之后照样落得下去（第四格），否则那句就地提示既挂不上、也清不掉。
 * （曾经和它们同族的 [ProactiveStore.Intent] 那颗 `Clear` 已在 / 退役，
 * "用户的清场动作"现在由切回普通回复那一颗承担——第四格跟着改成它。）
 */
class ProactiveLateEventDroppedTest {

    private class Recorder {
        val effects = mutableListOf<ProactiveStore.Effect>()
        operator fun invoke(e: ProactiveStore.Effect) { effects.add(e) }
    }

    private fun options(n: Int) = List(n) { ProactiveOption(text = "开场 $it", angle = "角度 $it") }

    /** 租约真源的手动替身：[owns] 翻成 false = 协调器那一侧已经没有这条任务了 */
    private class Slot(owns: Boolean) {
        var ownsCurrent = owns
        val recorder = Recorder()
        val store = ProactiveStore(isCurrentRequest = { ownsCurrent }, onEffect = { recorder(it) })
    }

    /**
     * 反例证人：把门写成只挡 `ProactiveOptions`（"迟到的一批内容别贴回来，收尾总可以吧"），
     * 或写成 `current == null 时放行`——已取消任务那 5 条就会盖掉用户按停止时看到的 2 条。
     */
    @Test
    fun `a cancelled run cannot put its openers or its failure back on the screen`() {
        val slot = Slot(owns = true)
        slot.store.accept(ProactiveStore.Intent.EnterProactive)
        slot.store.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(2))))

        slot.ownsCurrent = false                     // ← operationCoordinator.stopCurrent 先回收租约
        slot.store.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(5))))
        slot.store.accept(ProactiveStore.Intent.Apply(ProactiveFailed("r1", "晚到的失败文案")))
        slot.store.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))

        assertEquals(
            "停止之后屏幕上还是用户已经看见过的那 2 条，晚到的 5 条不许盖回来：" + slot.store.currentOptions,
            2, slot.store.currentOptions.size
        )
        assertNull(
            "已取消的任务不能给自己补一句失败提示（那等于把它说成一次真失败）：" + slot.store.uiState.value,
            slot.store.uiState.value.error
        )
        assertEquals(ComposerMode.PROACTIVE, slot.store.composerMode)
        assertTrue("迟到事件一个字都不该发出效果，实到：" + slot.recorder.effects, slot.recorder.effects.isEmpty())
    }

    /**
     * 点停止时一条都还没流出来 ⇒ 屏幕上回到"还没有开场"，但**不许**出现失败提示。
     *
     * 这一格是 closeRun 那道门的专用判据：反例是把提示写成"Ended 一到就补一句"（不看归属），
     * 于是每一次干净利落的点停止都会留下"这次没生成出能用的开场"——把用户的手势冒充成供应商的故障。
     */
    @Test
    fun `a cancelled run that produced nothing does not get to call the stop a failure`() {
        val slot = Slot(owns = true)
        slot.store.accept(ProactiveStore.Intent.EnterProactive)
        slot.store.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r1")))

        slot.ownsCurrent = false                                    // 点停止：租约先回收
        slot.store.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))  // ← VM 补发的那一条

        assertTrue(slot.store.currentOptions.isEmpty())
        assertNull(
            "停止不是失败：归属已失效的 Ended 不该触发那句就地短提示，实到：" + slot.store.uiState.value,
            slot.store.uiState.value.error
        )
        assertEquals(ComposerMode.PROACTIVE, slot.store.composerMode)
        assertTrue(slot.recorder.effects.isEmpty())
    }

    /**
     * 被取代的旧请求（这一轮已经在跑新的了）：它的 Ended / Failed 都动不了新一轮。
     *
     * 反例：Ended 不看身份、只在"当前什么都没有"时补提示——旧轮次那条迟到的 Ended 到达时
     * 新一轮的 options 还没上屏，于是新一轮被抢先判成"没交付"，屏幕上多出一句假失败。
     */
    @Test
    fun `a superseded run's late end cannot rewrite the newer run`() {
        var current = "r2"
        val recorder = Recorder()
        val store = ProactiveStore(isCurrentRequest = { it == current }, onEffect = { recorder(it) })

        store.accept(ProactiveStore.Intent.EnterProactive)
        store.accept(ProactiveStore.Intent.Apply(ProactiveStarted("r2")))
        store.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r2", options(1))))

        store.accept(ProactiveStore.Intent.Apply(ProactiveEnded("r1")))
        store.accept(ProactiveStore.Intent.Apply(ProactiveFailed("r1", "旧请求的失败")))

        assertEquals(
            "新一轮那一条要原样留着，迟到的一轮不许把它换成假失败或空态：" + store.currentOptions,
            1, store.currentOptions.size
        )
        assertNull(
            "新一轮自己还没收尾，旧轮的 Ended / Failed 都不许写 error：" + store.uiState.value,
            store.uiState.value.error
        )
        assertTrue(store.uiState.value.options.isNotEmpty())
        assertEquals(ComposerMode.PROACTIVE, store.composerMode)
        assertTrue(recorder.effects.isEmpty())
    }

    /**
     * 门只关事件流，不关用户动作：取消之后"没走到模型就失败"那句短提示照样落得下去，
     * 用户自己点「切回普通回复」也照样生效（`Intent.Clear` 已在 / 退役，
     * 清场动作由这颗承担）。
     *
     * 反例：把归属判断做成"这条链已经完了就整类 Intent 都拒"——用户既清不掉残留，
     * 也没法在 Provider 压根没配好的时候拿到那句就地短提示（第4节第1条第5条 第一条的入口之一）。
     */
    @Test
    fun `the gate stops task events but not the user's own actions`() {
        val slot = Slot(owns = true)
        slot.store.accept(ProactiveStore.Intent.EnterProactive)
        slot.store.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(3))))
        slot.ownsCurrent = false

        slot.store.accept(ProactiveStore.Intent.Apply(ProactiveOptions("r1", options(6))))
        assertTrue("迟到的写请求仍然被丢掉", slot.store.currentOptions.size == 3)

        slot.store.accept(ProactiveStore.Intent.Fail("请先配置一个可用的模型供应商"))
        assertEquals(
            "没走到模型就失败时，那句就地短提示照样要落进同一个状态对象",
            "请先配置一个可用的模型供应商", slot.store.uiState.value.error
        )
        assertEquals("模式仍由用户说了算", ComposerMode.PROACTIVE, slot.store.composerMode)

        slot.store.accept(ProactiveStore.Intent.ToggleComposer)
        assertTrue("用户自己的切回（含清残留）要生效", slot.store.currentOptions.isEmpty())
        assertNull("残留的错误也一起清", slot.store.uiState.value.error)
        assertEquals(ComposerMode.REPLY, slot.store.composerMode)
    }
}
