package com.lovebrain.app.service

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Panel 输入焦点契约（FloatingService 拆出来的那一格）。
 *
 * 这一屏最贵的一条不变量是：**Panel 可见 ≠ Panel 可聚焦**。
 * 悬浮窗跑在任意宿主 App 上面，只要 FLAG_NOT_FOCUSABLE 泄漏在 PASSIVE 态之外，
 * 宿主就再也收不到键盘——所以状态机必须能脱离 Android 窗口被单独证伪，
 * 于是 flags 计算是纯函数、窗口只是一只接口。
 *
 * 全部用假窗口跑：真窗口要 WindowManager，而这里要判的是"该不该抢焦点"这个答案本身。
 */
class PanelInputFocusOwnerTest {

    /** 记下每一次调用的假窗口——顺序断言全靠它 */
    private class FakePanel : PanelFocusWindow {
        val log = mutableListOf<String>()
        var present = true
        var applyResult = true
        override fun isPresent(): Boolean = present.also { log.add("isPresent") }
        override fun applyFlags(flags: Int): Boolean {
            log.add("applyFlags:$flags")
            lastFlags = flags
            return applyResult
        }

        override fun hideIme() {
            log.add("hideIme")
        }

        var lastFlags: Int? = null
    }

    private fun owner(window: FakePanel = FakePanel()) =
        PanelInputFocusOwner(window) to window

    // ═══════════ flags：全项目唯一入口 ═══════════

    @Test
    fun `PASSIVE 永远带着 NOT_FOCUSABLE，EDITING 永远不带`() {
        val passive = panelFlagsFor(PanelFocusMode.PASSIVE)
        val editing = panelFlagsFor(PanelFocusMode.EDITING)

        assertTrue(
            "PASSIVE 必须不抢焦点，否则 Panel 一显示就吃掉宿主 App 的键盘",
            passive and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
        )
        assertFalse(
            "EDITING 是唯一允许去掉 NOT_FOCUSABLE 的状态，可见不等于可聚焦",
            editing and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
        )
    }

    @Test
    fun `两种模式都保留 NOT_TOUCH_MODAL 与 WATCH_OUTSIDE_TOUCH`() {
        for (mode in PanelFocusMode.values()) {
            val flags = panelFlagsFor(mode)
            assertTrue(
                "$mode 下 Panel 外的触摸必须继续交给底层 App",
                flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0
            )
            assertTrue(
                "$mode 下必须还能收到 ACTION_OUTSIDE（外点退编辑态的唯一来源）",
                flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH != 0
            )
        }
    }

    // ═══════════ 状态机 ═══════════

    @Test
    fun `窗口还没建起来时任何切换都是空操作`() {
        val window = FakePanel().apply { present = false }
        val focus = PanelInputFocusOwner(window)

        focus.enterEditing("too_early")

        assertEquals("窗口不存在时不该改状态源", PanelFocusMode.PASSIVE, focus.mode)
        assertTrue(
            "窗口不存在时不该去写 flags：写一次就是一次假切换",
            window.log.none { it.startsWith("applyFlags") }
        )
    }

    @Test
    fun `同模式重复切换不去动窗口`() {
        val (focus, window) = owner()

        focus.exitEditing("already_passive")
        focus.setMode(PanelFocusMode.PASSIVE, "show_panel")

        assertTrue(
            "已经是 PASSIVE 还去 updateViewLayout，等于每次开面板都白刷一次窗口",
            window.log.none { it.startsWith("applyFlags") }
        )
        assertEquals(PanelFocusMode.PASSIVE, focus.mode)
    }

    @Test
    fun `写 flags 失败必须把模式退回原值`() {
        val (focus, window) = owner()
        window.applyResult = false

        focus.enterEditing("input_intent:reply")

        assertEquals(
            "WindowManager 没接住这次更新，状态源却留在 EDITING 就是撒谎：下一次 blur 会被当成合法退出",
            PanelFocusMode.PASSIVE,
            focus.mode
        )
    }

    @Test
    fun `写 flags 成功时窗口拿到的是 EDITING 的 flags`() {
        val (focus, window) = owner()

        focus.enterEditing("input_intent:reply")

        assertEquals(panelFlagsFor(PanelFocusMode.EDITING), window.lastFlags)
        assertEquals(PanelFocusMode.EDITING, focus.mode)
    }

    // ═══════════ 输入框所有权 ═══════════

    @Test
    fun `输入意图立即接管所有权并进入编辑态`() {
        val (focus, _) = owner()

        focus.onInputIntent("reply")

        assertEquals("reply", focus.activeInputId)
        assertEquals(PanelFocusMode.EDITING, focus.mode)
    }

    @Test
    fun `新输入框接管后，旧输入框的 blur 不许退出编辑态`() {
        val (focus, _) = owner()
        focus.onInputIntent("reply")

        focus.onInputIntent("counseling")
        focus.onInputFocusChanged("reply", focused = false)

        assertEquals(
            "焦点已经在 counseling，reply 那条迟到的 blur 不能把窗口整体退回 PASSIVE",
            "counseling", focus.activeInputId
        )
        assertEquals(PanelFocusMode.EDITING, focus.mode)
    }

    @Test
    fun `当前输入框失焦才交回所有权并退回 PASSIVE`() {
        val (focus, _) = owner()
        focus.onInputIntent("reply")

        focus.onInputFocusChanged("reply", focused = false)

        assertNull("失焦后必须没有人持有输入所有权", focus.activeInputId)
        assertEquals(PanelFocusMode.PASSIVE, focus.mode)
    }

    @Test
    fun `get 焦点时只登记所有权，不擅自进编辑态`() {
        val (focus, window) = owner()

        focus.onInputFocusChanged("reply", focused = true)

        assertEquals("reply", focus.activeInputId)
        assertEquals(
            "Compose 侧报来焦点时窗口本来就应该是 EDITING；这里不该再写一次 flags",
            PanelFocusMode.PASSIVE, focus.mode
        )
        assertTrue(window.log.none { it.startsWith("applyFlags") })
    }

    // ═══════════ 关闭顺序 ═══════════

    @Test
    fun `释放输入的顺序是先清 Compose 焦点再收 IME 最后退窗口`() {
        val (focus, window) = owner()
        focus.onInputIntent("reply")
        focus.clearComposeFocusCallback = { window.log.add("clearComposeFocus") }
        window.log.clear()

        focus.releaseInput("hide_panel")

        assertEquals(
            "关闭顺序不得调换：TextField 的 Focus 必须先清掉，否则下次打开会被自动唤回键盘",
            listOf(
                "clearComposeFocus",
                "isPresent",
                "hideIme",
                "isPresent",
                "applyFlags:${panelFlagsFor(PanelFocusMode.PASSIVE)}"
            ),
            window.log
        )
        assertNull(focus.activeInputId)
        assertEquals(PanelFocusMode.PASSIVE, focus.mode)
    }

    @Test
    fun `销毁时把模式钉回 PASSIVE 并交回 Compose 焦点回调`() {
        val (focus, window) = owner()
        focus.onInputIntent("reply")
        focus.clearComposeFocusCallback = { window.log.add("clearComposeFocus") }

        focus.resetForDestroy()

        assertEquals(PanelFocusMode.PASSIVE, focus.mode)
        assertNull(
            "回调还挂在已死的 composition 上，下一次 releaseInput 就会去调它",
            focus.clearComposeFocusCallback
        )
    }
}
