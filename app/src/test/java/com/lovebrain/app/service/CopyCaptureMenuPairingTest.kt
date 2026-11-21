package com.lovebrain.app.service

import android.view.accessibility.AccessibilityEvent
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 长按 ↔「复制」菜单的配对判据（审计项 2/3）。
 *
 * 旧口径只比包名 + 30s：同包正文里出现"复制"二字就会被当成菜单确认，把长按暂存那条正文吞掉；
 * 现在配对必须同时满足 **同包 + 5s 内 + 菜单来自长按之外的新窗口**，窗口号读不到按"证不了"处理。
 *
 * 逐格"回退成什么会红"：
 * - 去掉窗口归属判据（只比包名/寿命）→ `same window` 与 `unknown window` 两格红；
 * - 把寿命放回 30s → `expires far sooner` 红；
 * - 跨 App 分支改成"清 pending"→ 只能从服务源码看，本表钉的是纯函数结论 CROSS_PKG；
 * - 无 pending 也整树扫描 → `tree scan only …` 红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class CopyCaptureMenuPairingTest {

    private val longPressWindow = 12
    private val popupMenuWindow = 34

    @Test
    fun `menu in another window right after the long press is the pair`() {
        assertEquals(
            MenuPairing.MATCH,
            menuPairingOf("com.tencent.mm", longPressWindow, "com.tencent.mm", popupMenuWindow, 900L)
        )
    }

    @Test
    fun `slow menus past three seconds still connect`() {
        assertEquals(
            MenuPairing.MATCH,
            menuPairingOf("com.tencent.mm", longPressWindow, "com.tencent.mm", popupMenuWindow, 3_500L)
        )
    }

    @Test
    fun `same window text containing copy never consumes the pending message`() {
        // 反例：同包列表/正文刷新里带"复制"二字，窗口号还是长按那个窗口
        assertEquals(
            MenuPairing.SAME_WINDOW,
            menuPairingOf("com.tencent.mm", longPressWindow, "com.tencent.mm", longPressWindow, 900L)
        )
    }

    @Test
    fun `unknown window id cannot prove a popup menu`() {
        assertEquals(
            MenuPairing.SAME_WINDOW,
            menuPairingOf("com.tencent.mm", -1, "com.tencent.mm", popupMenuWindow, 900L)
        )
        assertEquals(
            MenuPairing.SAME_WINDOW,
            menuPairingOf("com.tencent.mm", longPressWindow, "com.tencent.mm", -1, 900L)
        )
    }

    @Test
    fun `a provable popup container connects even without a distinct window id`() {
        // 有些 ROM 的弹窗事件拿不到独立窗口号，但类名明确是 PopupWindow 容器
        assertEquals(
            MenuPairing.MATCH,
            menuPairingOf(
                "com.tencent.mm",
                longPressWindow,
                "com.tencent.mm",
                longPressWindow,
                900L,
                eventClassName = "android.widget.PopupWindow\$PopupDecorView"
            )
        )
        assertEquals(
            MenuPairing.MATCH,
            menuPairingOf(
                "com.tencent.mm",
                -1,
                "com.tencent.mm",
                -1,
                900L,
                eventClassName = "android.widget.PopupWindow"
            )
        )
    }

    @Test
    fun `ordinary same window content with copy text is still skipped`() {
        // 反例：正文/列表刷新事件，类名是普通容器，窗口号仍是长按那个窗口
        assertEquals(
            MenuPairing.SAME_WINDOW,
            menuPairingOf(
                "com.tencent.mm",
                longPressWindow,
                "com.tencent.mm",
                longPressWindow,
                900L,
                eventClassName = "android.widget.FrameLayout"
            )
        )
    }

    @Test
    fun `another app's menu is not this long press`() {
        assertEquals(
            MenuPairing.CROSS_PKG,
            menuPairingOf("com.tencent.mm", longPressWindow, "com.other.app", popupMenuWindow, 500L)
        )
        assertEquals(
            MenuPairing.CROSS_PKG,
            menuPairingOf(null, longPressWindow, "com.tencent.mm", popupMenuWindow, 500L)
        )
    }

    @Test
    fun `pairing expires far sooner than the old thirty second budget`() {
        val ttl = CopyCaptureService.MENU_PENDING_MAX_AGE_MS
        // 寿命的唯一来源是服务自家这颗 5s：旧 30s 那颗已从 AppConfig 删除，这里钉实际常量本身
        assertEquals("配对寿命只有 CopyCaptureService 这一处真源", 5_000L, ttl)
        assertTrue("TTL 仍要容得下慢菜单 (>3s)", ttl >= 3_500L)
        assertEquals(
            MenuPairing.MATCH,
            menuPairingOf("com.tencent.mm", longPressWindow, "com.tencent.mm", popupMenuWindow, ttl)
        )
        assertEquals(
            MenuPairing.EXPIRED,
            menuPairingOf("com.tencent.mm", longPressWindow, "com.tencent.mm", popupMenuWindow, ttl + 1)
        )
        // 时钟回拨/未记录时刻也不能当成有效配对
        assertEquals(
            MenuPairing.EXPIRED,
            menuPairingOf("com.tencent.mm", longPressWindow, "com.tencent.mm", popupMenuWindow, -1L)
        )
    }

    @Test
    fun `tree scan only happens for window events that actually have a pending long press`() {
        val windowEvent = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val longClick = AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        assertFalse("无 pending 的窗口事件不整树扫描", shouldScanMenuTree(windowEvent, hasPending = false))
        assertFalse("长按事件本身不整树扫描", shouldScanMenuTree(longClick, hasPending = true))
        assertFalse(
            "点击等普通事件不整树扫描",
            shouldScanMenuTree(AccessibilityEvent.TYPE_VIEW_CLICKED, hasPending = true)
        )
        assertTrue("有 pending 的窗口事件才补查菜单树", shouldScanMenuTree(windowEvent, hasPending = true))
    }

    @Test
    fun `the service gates the whole-tree scan on the pending gate`() {
        val file = File("src/main/java/com/lovebrain/app/service/CopyCaptureService.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt")
        assertTrue("尺接错了文件，找不到 $file", file.isFile)
        val src = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        assertTrue(
            "整树取文必须被 shouldScanMenuTree 门住（无 pending / 非窗口事件都不遍历）",
            src.contains("if (shouldScanMenuTree(type, pendingContent != null)) {")
        )
        assertFalse(
            "不许再出现「先拼一遍、缺关键词再补扫一遍」的二次扫描补丁",
            src.contains("!joined.contains(\"复制\")")
        )
    }
}
