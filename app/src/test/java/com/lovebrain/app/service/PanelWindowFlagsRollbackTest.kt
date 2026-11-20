package com.lovebrain.app.service

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.UiProbeApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 旗标写失败时，活着的 LayoutParams 必须跟着状态机一起回滚。
 *
 * PanelInputFocusOwner 失败只退 mode；窗口这一侧若把 EDITING flags 留在
 * cv.layoutParams 上，下一次 setMode(PASSIVE) 因 mode 已相等而早退，
 * 面板就永久抢着宿主焦点。状态机那份合同由 PanelInputFocusOwnerTest 守，
 * 这里守的是窗口这一半：用假宿主驱动真窗口，钉的就是那份活对象。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class PanelWindowFlagsRollbackTest {

    private lateinit var host: OverlayWindowHost
    private lateinit var cv: ComposeView
    private lateinit var window: OverlayPanelWindow

    private val liveParams: WindowManager.LayoutParams
        get() = cv.layoutParams as WindowManager.LayoutParams

    @Before
    fun setUp() {
        host = mockk(relaxed = true)
        every { host.screenWidth() } returns 1080
        every { host.screenHeight() } returns 2400
        every { host.dp(any()) } answers { firstArg() }
        every { host.overlayParams(any(), any(), any()) } answers {
            WindowManager.LayoutParams().apply { flags = thirdArg() }
        }
        every { host.newComposeView() } answers {
            ComposeView(ApplicationProvider.getApplicationContext<Context>()).also { cv = it }
        }
        // 真 WindowManager.addView 会把传入的 params 挂成 view.layoutParams——
        // 假宿主必须照做，否则测不到"活对象被写脏"那条别名关系。
        every { host.attach(any(), any(), any()) } answers {
            firstArg<View>().layoutParams = secondArg<WindowManager.LayoutParams>()
            OverlayAttach.ATTACHED
        }
        window = OverlayPanelWindow(
            host = host,
            bubbleOrigin = { null },
            initialWidth = 300,
            initialHeight = 420,
            persistSize = { _, _ -> },
            content = {}
        )
        assertEquals(OverlayAttach.ATTACHED, window.ensureCreated())
    }

    @Test
    fun `rejected flag write restores the live window params`() {
        val passiveFlags = panelFlagsFor(PanelFocusMode.PASSIVE)
        assertEquals("建窗即 PASSIVE", passiveFlags, liveParams.flags)

        every { host.updateLayout(any(), any()) } returns false
        window.onInputIntent("reply")

        assertEquals(
            "mode 已退回 PASSIVE，活着的 LayoutParams 也必须退回——否则面板永久抢焦点",
            passiveFlags, liveParams.flags
        )
        // 证人：写的就是那份活对象，不是副本
        verify { host.updateLayout(cv, liveParams) }
    }

    @Test
    fun `after a rejected edit attempt the focus machine still reaches editing and back`() {
        every { host.updateLayout(any(), any()) } returns false
        window.onInputIntent("reply")
        assertEquals(panelFlagsFor(PanelFocusMode.PASSIVE), liveParams.flags)

        every { host.updateLayout(any(), any()) } returns true
        window.onInputIntent("reply")
        assertEquals(
            "失败回滚后重试必须仍能进 EDITING（mode 没被假当前态卡死）",
            panelFlagsFor(PanelFocusMode.EDITING), liveParams.flags
        )

        window.onInputFocusChanged("reply", focused = false)
        assertEquals(
            "失焦必须落回 PASSIVE 那一份（带 NOT_FOCUSABLE）",
            panelFlagsFor(PanelFocusMode.PASSIVE), liveParams.flags
        )
    }

    @Test
    fun `temp hide during the exit fade sees the panel on screen and cancels the fade`() {
        every { host.updateLayout(any(), any()) } returns true
        assertTrue(window.present())
        assertTrue(window.isOnScreen)

        window.fadeOut {}
        assertFalse("淡出开始 isShowing 即 false", window.isShowing)
        assertTrue("淡出途中面板仍在屏上——临时隐藏记形态必须认这一颗", window.isOnScreen)

        window.hideInstantly()
        assertFalse(window.isOnScreen)
        assertEquals(View.GONE, cv.visibility)

        // 退出动画必须已被取消：推完时钟也不许有任何复活回调
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, cv.visibility)
        assertFalse(window.isOnScreen)
    }
}