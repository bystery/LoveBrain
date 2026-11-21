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
        focus.onInputFocusChanged("reply_field", "reply", focused = false)

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

        focus.onInputFocusChanged("reply_field", "reply", focused = false)

        assertNull("失焦后必须没有人持有输入所有权", focus.activeInputId)
        assertEquals(PanelFocusMode.PASSIVE, focus.mode)
    }

    @Test
    fun `获焦是确证要打字：窗口已被打回 PASSIVE 时获焦必须重新进编辑态`() {
        val (focus, window) = owner()

        focus.onInputFocusChanged("field", "scheme_adjust", focused = true)

        assertEquals("scheme_adjust", focus.activeInputId)
        assertEquals(
            "A 卡的失焦把窗口打回 PASSIVE 之后，B 卡获焦这一跳必须自己把窗口带回 EDITING——" +
                "否则光标在闪、键盘不起，要再点一次",
            PanelFocusMode.EDITING, focus.mode
        )
        assertEquals(
            "获焦这一跳要把 EDITING 的 flags 真的落到窗口上",
            "applyFlags:${panelFlagsFor(PanelFocusMode.EDITING)}",
            window.log.single { it.startsWith("applyFlags") }
        )
    }

    @Test
    fun `窗口已经是编辑态时获焦不再多写一次 flags`() {
        val (focus, window) = owner()
        focus.onInputIntent("reply")
        window.log.clear()

        focus.onInputFocusChanged("reply_field", "reply", focused = true)

        assertEquals("reply", focus.activeInputId)
        assertEquals(PanelFocusMode.EDITING, focus.mode)
        assertTrue(
            "触碰已经进过 EDITING，获焦这一跳同态直接 return：不许每次获焦都白刷一次窗口",
            window.log.none { it.startsWith("applyFlags") }
        )
    }

    // ═══════════ 同 id 多实例（方案卡多颗自定义框共用 scheme_adjust） ═══════════

    @Test
    fun `同 id 有人获焦时兄弟的失焦不许退出编辑态`() {
        val (focus, window) = owner()
        focus.onInputFocusChanged("a", "scheme_adjust", focused = true) // A 卡：落一次 EDITING flags
        focus.onInputFocusChanged("b", "scheme_adjust", focused = true) // B 卡（获焦边沿先到）：同态不写
        assertEquals(
            "两次获焦只该落一次 flags（第二次是同态）",
            1, window.log.count { it.startsWith("applyFlags") }
        )

        focus.onInputFocusChanged("a", "scheme_adjust", focused = false) // A 卡失焦

        assertEquals(
            "同 id 的 B 卡还持焦：A 这一颗 blur 不许把窗口打回 PASSIVE（键盘会消失）",
            PanelFocusMode.EDITING, focus.mode
        )
        assertEquals("所有权仍在这个 id 上", "scheme_adjust", focus.activeInputId)
        assertEquals(
            "兄弟的失焦不许写任何 flags",
            1, window.log.count { it.startsWith("applyFlags") }
        )

        focus.onInputFocusChanged("b", "scheme_adjust", focused = false) // B 卡失焦
        assertNull("这个 id 再没人持焦，才交回所有权", focus.activeInputId)
        assertEquals(PanelFocusMode.PASSIVE, focus.mode)
    }

    @Test
    fun `同 id 交接时失焦先到也不会留下需要再点一次的 PASSIVE`() {
        val (focus, _) = owner()
        focus.onInputFocusChanged("a", "scheme_adjust", focused = true) // A 卡

        // 系统把 A 的失焦排在 B 的获焦之前：这一跳按当前记账确实没人持焦
        focus.onInputFocusChanged("a", "scheme_adjust", focused = false)
        // 紧跟的 B 卡获焦边沿必须自己把窗口带回编辑态
        focus.onInputFocusChanged("b", "scheme_adjust", focused = true)

        assertEquals(
            "交接的最后一跳是获焦 ⇒ 窗口必须停在 EDITING，用户不必再点一次",
            PanelFocusMode.EDITING, focus.mode
        )
        assertEquals("scheme_adjust", focus.activeInputId)
    }

    @Test
    fun `释放输入把焦点计数一起作废`() {
        val (focus, _) = owner()
        focus.onInputFocusChanged("a", "scheme_adjust", focused = true)
        focus.onInputFocusChanged("b", "scheme_adjust", focused = true)

        focus.releaseInput("outside_touch")

        assertNull(focus.activeInputId)
        assertEquals(PanelFocusMode.PASSIVE, focus.mode)
        // 计数若没作废，下一次失焦会被当成"还有人持焦"而永不退出编辑态
        focus.onInputIntent("scheme_adjust")
        focus.onInputFocusChanged("a", "scheme_adjust", focused = false)
        assertNull("旧计数的残留不许吞掉下一次失焦", focus.activeInputId)
        assertEquals(PanelFocusMode.PASSIVE, focus.mode)
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
