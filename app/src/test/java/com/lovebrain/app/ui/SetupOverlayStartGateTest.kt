package com.lovebrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 悬浮窗授权那一趟的待续跑状态（审计项 7）。
 *
 * 旧写法把"待续跑"挂在 Activity 两颗裸字段上，且续跑判据是
 * `if (pending && Settings.canDrawOverlays(...))`——**拒绝/取消就没进那个 if**，
 * 于是旗标一直留着，之后任意一次 `onResume`（从任何子页/系统页回来）都会自动起服务。
 *
 * 这里的判据只有一条：**结算入口 [PendingOverlayStart.onResume] 无论给没给权限都先清空**。
 * 回退成"只在 granted 时清"→ `denied … never replays` 与 `granted … once` 两格红。
 */
class SetupOverlayStartGateTest {

    @Test
    fun `denied or cancelled grant is settled once and never replays later`() {
        val gate = PendingOverlayStart()
        gate.defer()
        assertNull("拒绝/取消：这一次就结算掉，不启动", gate.onResume(granted = false))
        // 之后任意一次 onResume（子页回来、别的系统页回来）都不许再自动起服务
        assertNull(gate.onResume(granted = true))
        assertNull(gate.onResume(granted = true))
    }

    @Test
    fun `granted resume runs the pending start exactly once`() {
        val gate = PendingOverlayStart()
        gate.defer()
        val resume = gate.onResume(granted = true)
        assertNotNull(resume)
        assertNull("续跑只发生一次", resume!!.panelMode)
        assertNull(gate.onResume(granted = true))
    }

    @Test
    fun `panel mode is carried through the trip and cleared afterwards`() {
        val gate = PendingOverlayStart()
        gate.defer(1)
        assertEquals(1, gate.onResume(granted = true)?.panelMode)
        assertNull(gate.onResume(granted = true))
    }

    @Test
    fun `plain start followed by a panel request keeps the mode`() {
        // startFloatingService() 先 defer()（纯启动），openPanelFromHome 再 defer(mode)
        val gate = PendingOverlayStart()
        gate.defer()
        gate.defer(2)
        assertEquals(2, gate.onResume(granted = true)?.panelMode)
    }

    @Test
    fun `denied grant drops the panel mode too`() {
        val gate = PendingOverlayStart()
        gate.defer(3)
        assertNull(gate.onResume(granted = false))
        assertNull(gate.onResume(granted = true))
    }

    @Test
    fun `no pending request means no action`() {
        assertNull(PendingOverlayStart().onResume(granted = true))
        assertNull(PendingOverlayStart().onResume(granted = false))
    }
}
