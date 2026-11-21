package com.lovebrain.app.service

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.UiProbeApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * 面板"开合与外部触摸"这两条语义（不是几何，几何在 OverlayPanelWindowTest）。
 *
 * 钉三件事，全部对应审计核实过的回弹/残留：
 * 1. **取消淡出不许把球亮回来**：`Animator.cancel()` 会同步补发 onAnimationEnd，
 *    旧实现在那里无条件 `revealBubble()` —— 停服后把 `STOPPED` 写回 `VISIBLE_BUBBLE`，
 *    打开面板时把 `VISIBLE_PANEL` 写回球（"面板已开、全局态上报球"）。
 * 2. 临时隐藏路径同一颗判据（hideInstantly 也走 cancel），这里用停服/打开两条来路钉。
 * 3. **点面板外宿主 App 要释放输入**（否则菜单残留、Panel 停在 EDITING），
 *    但贴着面板边框的那一圈例外——菜单/选区手柄是子窗口，那里是 H2 不能拆。
 *
 * 用假宿主驱动真窗口（同一套写法见 PanelWindowFlagsRollbackTest）：真 WindowManager
 * 在 JVM 上起不来，而要判的是"这次回调该不该发生"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class OverlayPanelWindowPresentationTest {

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
        every { host.attach(any(), any(), any()) } answers {
            firstArg<View>().layoutParams = secondArg<WindowManager.LayoutParams>()
            OverlayAttach.ATTACHED
        }
        every { host.updateLayout(any(), any()) } returns true
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

    // ═══════════ 1. 取消淡出 = 不 reveal（停服 / 打开 / 临时隐藏三条来路） ═══════════

    @Test
    fun `destroy-path cancel of the exit fade never reveals the bubble`() {
        assertTrue(window.present())
        var reveals = 0
        window.fadeOut { reveals++ }
        assertTrue("淡出途中面板仍在屏上", window.isOnScreen)

        // FloatingService.onDestroy：先落 STOPPED，再 cancelExitAnimation()
        window.cancelExitAnimation()
        // 推完时钟：旧实现无论"cancel 同步补发 onAnimationEnd"还是"动画自己跑完"都会 reveal
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)

        assertEquals(
            "停服取消淡出后把球亮回来 = windowState 从 STOPPED 回弹成 VISIBLE_BUBBLE",
            0,
            reveals
        )
        assertFalse("取消之后这扇窗必须已经不在屏上（isPanelHiding 要收账）", window.isOnScreen)
    }

    @Test
    fun `reopening the panel during the exit fade never reveals the bubble`() {
        assertTrue(window.present())
        var reveals = 0
        window.fadeOut { reveals++ }

        // FloatingService.showPanel：present() 先取消残留淡出
        assertTrue(window.present())
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)

        assertEquals(
            "打开面板时取消残留淡出不许 reveal：那会把全局态写成球，而屏上是面板",
            0,
            reveals
        )
        assertEquals("面板必须真的可见（取消不许把刚才那次 present 的 VISIBLE 抹掉）", View.VISIBLE, cv.visibility)
        assertTrue(window.isShowing)
        assertTrue(window.isOnScreen)
    }

    // ═══════════ 2. ACTION_OUTSIDE：真外点释放输入，菜单子窗口例外 ═══════════

    /** rect 探针拿不到时兜底的那一圈带子，纯判据先单独量一遍 */
    @Test
    fun `the toolbar band only covers one margin around the panel frame`() {
        val m = TOOLBAR_BAND_MARGIN_DP
        assertTrue(outsideTouchWithinPanelBand(0f, 0f, 300, 420, m))
        assertTrue("菜单悬在面板上沿之外（抬高 44dp）也要算带内", outsideTouchWithinPanelBand(150f, -(m - 4f), 300, 420, m))
        assertTrue(outsideTouchWithinPanelBand(-m.toFloat(), 420f + m, 300, 420, m))
        assertFalse("再远一像素就不该算菜单", outsideTouchWithinPanelBand(150f, -m - 1f, 300, 420, m))
        assertFalse(outsideTouchWithinPanelBand(300f + m + 1f, 100f, 300, 420, m))
        assertFalse("宿主 App 别处的外点必须落在这条带外", outsideTouchWithinPanelBand(900f, 900f, 300, 420, m))
    }

    @Test
    fun `outside touch far from the panel releases input even while the menu shows`() {
        assertTrue(window.present())
        var cleared = 0
        window.registerClearFocusCallback { cleared++ }
        window.onInputIntent("reply")
        window.registerTextToolbarShowingProbe { true }
        assertEquals(panelFlagsFor(PanelFocusMode.EDITING), liveParams.flags)

        dispatchOutside(x = 900f, y = 900f)

        assertEquals("点面板外宿主 App 必须交回输入（clearFocus 顺带收起菜单）", 1, cleared)
        assertEquals(
            "panel 不许停在 EDITING：宿主 App 已经被点走了，窗口还抢着焦点就是残留",
            panelFlagsFor(PanelFocusMode.PASSIVE),
            liveParams.flags
        )
    }

    @Test
    fun `outside touch on a menu sub-window keeps the selection`() {
        assertTrue(window.present())
        var cleared = 0
        window.registerClearFocusCallback { cleared++ }
        window.onInputIntent("reply")
        window.registerTextToolbarShowingProbe { true }
        window.registerTextToolbarRectProbe { Rect(150f, 0f, 150f, 40f) }

        // 选区贴面板上沿（rect 探针交 showMenu 的同一份快照）：外点落在 rect 外扩 slop
        // 的一圈、菜单悬出面板 frame 的那一档，不许拆选区和菜单（H2 那一族）
        dispatchOutside(x = 150f, y = -20f)

        assertEquals("落在菜单/手柄上的外点不许拆选区和菜单", 0, cleared)
        assertEquals(panelFlagsFor(PanelFocusMode.EDITING), liveParams.flags)
    }

    @Test
    fun `outside touch without a menu still releases input near the edge`() {
        assertTrue(window.present())
        var cleared = 0
        window.registerClearFocusCallback { cleared++ }
        window.onInputIntent("reply")
        window.registerTextToolbarShowingProbe { false }

        dispatchOutside(x = 150f, y = -40f)

        assertEquals("没有菜单时贴着边框的外点也是真外点：输入必须交回", 1, cleared)
        assertEquals(panelFlagsFor(PanelFocusMode.PASSIVE), liveParams.flags)
    }

    @Test
    fun `outside touch while passive and menu-less leaves input alone`() {
        assertTrue(window.present())
        var cleared = 0
        window.registerClearFocusCallback { cleared++ }
        window.registerTextToolbarShowingProbe { false }
        // 面板里还有自成一扇窗的 IntentEditorDialog：落在它上面的手指同样收到 ACTION_OUTSIDE，
        // PASSIVE 时也释放会顺手把对话框正在用的输入法收掉——这一支必须保持空操作。
        assertEquals(panelFlagsFor(PanelFocusMode.PASSIVE), liveParams.flags)

        dispatchOutside(x = 900f, y = 900f)

        assertEquals("PASSIVE 且没有菜单时不许因为一次外点去动输入", 0, cleared)
    }

    private fun dispatchOutside(x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_OUTSIDE, x, y, 0)
        try {
            cv.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
