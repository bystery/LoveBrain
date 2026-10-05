package com.lovebrain.app.feature.roundstate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RoundStateStore.resolveOnlyThisRound] 钉的是"长按生成"那条通路的一次性：
 * 长按带来的"只读本轮"必须**随本次请求快照传递**，不许变成后续普通点击永久忽略历史。
 *
 * 长期开关那颗状态（[RoundStateStore.onlyThisRound]）的形状这条测试刻意不改、也不该改：
 * 它是本轮工作轮次级的设置，新轮次 / 切档案才复位。长按要走的是另一条路——
 * 一次请求带一个 [RoundScope.CurrentRoundOnly]，读得到、不写回。
 *
 * 四条判据，每一条都能被一种具体的坏实现打破：
 * · "a current-round-only request reads true while the long term switch stays off" ——
 *   把 `resolveOnlyThisRound(CurrentRoundOnly)` 实现成"顺手 accept(SetOnlyThisRound(true))" 或
 *   "在 store 里记一个 pendingOnce 布尔"的写法，第二次普通读取会拿到 true，这一格红。
 * · "the request after a long press follows the switch again" ——
 *   任何把覆盖存进状态、等下一次去清的写法（尤其是"清"发生在请求真正跑起来之后：
 *   被供应商没配好 / 没有她的消息 / 前台槽位被占而当场拒掉的请求根本不会去清），
 *   都会让下一次 FollowSwitch 读到 true，这一格红。
 * · "a long press does not fire the stale coordination callback" ——
 *   把长按实现成"拨开关"的写法会触发 onOnlyThisRoundChanged，把屏幕上当前的旧结果标 stale；
 *   长按不是切换，这一格红。
 * · "the long term switch keeps its own behavior for plain requests" ——
 *   长期开关仍在（打开新轮次 / 切档案复位的语义没被这条通路顶掉）；
 *   把覆盖做成"唯一真相"、让普通请求永远走长期开关之外的另一份状态的写法在这一格红。
 */
class RoundStateStoreCurrentRoundOnlyTest {

    /** 回调被调了几次——"长按有没有被偷偷实现成拨开关"就藏在这个数里 */
    private class Harness {
        var staleChecks = 0
        val store = RoundStateStore(onOnlyThisRoundChanged = { staleChecks++ })
    }

    @Test
    fun `a current-round-only request reads true while the long term switch stays off`() {
        val h = Harness()
        assertFalse("默认长期开关就该是关着的", h.store.onlyThisRoundNow)

        assertTrue(
            "长按那一次请求要读到\"仅看本轮\"",
            h.store.resolveOnlyThisRound(RoundScope.CurrentRoundOnly)
        )
        // 同一个请求里会被读多处（生成输入、输入指纹、结果上下文），口径必须逐字一致
        val again = h.store.resolveOnlyThisRound(RoundScope.CurrentRoundOnly)
        assertTrue("一次请求内多处读取必须给出同一个值", again)

        assertFalse(
            "读了一次覆盖之后，长期开关仍必须是关着的：" +
                "长按不许把\"仅看本轮\"变成这一轮之后的默认读法",
            h.store.onlyThisRoundNow
        )
        assertFalse(
            "对外暴露的那颗状态流也必须还是 false（面板 / 结果区读的是它）",
            h.store.onlyThisRound.value
        )
    }

    @Test
    fun `the request after a long press follows the switch again`() {
        val h = Harness()
        h.store.resolveOnlyThisRound(RoundScope.CurrentRoundOnly)   // 长按那一次

        assertFalse(
            "下一次普通点击必须回到长期开关的读法，不能继承上一次长按",
            h.store.resolveOnlyThisRound(RoundScope.FollowSwitch)
        )
        // 省略实参（走默认值）也必须同样是"跟随开关"，不能被默认成"只读本轮"
        assertFalse(
            "默认参数必须是跟随长期开关",
            h.store.resolveOnlyThisRound()
        )
    }

    @Test
    fun `a long press does not fire the stale coordination callback`() {
        val h = Harness()
        h.store.resolveOnlyThisRound(RoundScope.CurrentRoundOnly)
        assertEquals(
            "长按是一次请求，不是拨开关：不该触发\"旧结果标 stale\"那条协调",
            0, h.staleChecks
        )
    }

    @Test
    fun `the long term switch keeps its own behavior for plain requests`() {
        val h = Harness()

        h.store.accept(RoundStateStore.Intent.SetOnlyThisRound(true))
        assertTrue("开关打开后普通请求要读到开", h.store.resolveOnlyThisRound(RoundScope.FollowSwitch))
        assertEquals("复位不触发 stale 协调", 0, h.staleChecks)

        h.store.accept(RoundStateStore.Intent.ToggleOnlyThisRound)
        assertFalse("切换后普通请求要读到关", h.store.resolveOnlyThisRound(RoundScope.FollowSwitch))
        assertEquals("切换要触发一次 stale 协调", 1, h.staleChecks)

        // 覆盖只在它那一次请求里有效：长期开关关着时，CurrentRoundOnly 仍读到开
        assertTrue(h.store.resolveOnlyThisRound(RoundScope.CurrentRoundOnly))
        assertFalse(h.store.onlyThisRoundNow)
    }
}
