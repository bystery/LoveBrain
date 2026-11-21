package com.lovebrain.app.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * overlay 文字菜单的两层判据。
 *
 * 状态层：菜单快照只认 rect + 有序 label，动作回调不参与相等判定。框架每次 showMenu 都递
 * 新 lambda，若把它们算进快照，重复调用就会不停打脏观察者；而框架读 status 的那一行就在
 * CoreTextField 的重组作用域里，读若被登记成观察，写回时又把自己打脏 —— 菜单自激闪烁。
 * 这里直接数"真正变更的状态对象"与"被打脏的观察者"，两者都必须是 0。
 * 回调随快照走：点击取的是渲染出那份 label 的快照里的回调，label 集中途变化不会下标错位。
 *
 * 组合层：真挂一颗 BasicTextField 到宿主下，长按出菜单后连推多帧，判状态不来回翻、
 * 点面板内非聚焦区域的完整轻点经清焦收起菜单（打开菜单的那次长按不受影响；此时输入框
 * 正是主动焦点，清焦必须 force，force=false 的版本过不了该族判据）、
 * 点粘贴真能把剪贴板文本插进去。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayTextToolbarTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private val anchor = Rect(12f, 40f, 220f, 80f)

    /** 每次都是新 lambda 实例 —— 框架就是这么调的。 */
    private fun show(toolbar: OverlayTextToolbar, rect: Rect = anchor) {
        toolbar.showMenu(
            rect = rect,
            onCopy = {},
            onPaste = {},
            onCut = {},
            onSelectAll = {}
        )
    }

    /**
     * 数这一段里真正变更的状态对象个数。0 变更 ⇒ 0 观察者可能被打脏，
     * 比"某个观察者没收到回调"更强：它连状态记录都没产生。
     */
    private fun publishedChanges(block: () -> Unit): Int {
        Snapshot.sendApplyNotifications()
        var changed = 0
        val handle = Snapshot.registerApplyObserver { set, _ -> changed += set.size }
        try {
            block()
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        return changed
    }

    // ═══════════ 状态层 ═══════════

    @Test
    fun repeatedIdenticalShowMenuPublishesNoStateChange() {
        val toolbar = OverlayTextToolbar(ctx)
        show(toolbar)
        Snapshot.sendApplyNotifications()
        assertEquals(TextToolbarStatus.Shown, toolbar.status)

        val changes = publishedChanges {
            repeat(100) {
                // rect 也每次新建：判的必须是结构相等，不是同一个实例
                show(toolbar, Rect(12f, 40f, 220f, 80f))
            }
        }
        assertEquals(
            "rect 与 label 都没变时，重复 showMenu 不许产生任何状态变更",
            0, changes
        )
        assertEquals(TextToolbarStatus.Shown, toolbar.status)
    }

    @Test
    fun movingRectOrChangingLabelsPublishesExactlyOneChange() {
        val toolbar = OverlayTextToolbar(ctx)
        show(toolbar)
        Snapshot.sendApplyNotifications()

        assertEquals(
            "锚定区域挪了必须发布（否则菜单不跟着选区走）",
            1, publishedChanges { show(toolbar, Rect(13f, 40f, 220f, 80f)) }
        )
        assertEquals(
            "可用动作变了必须发布（否则菜单项与实际能力不符）",
            1, publishedChanges {
                toolbar.showMenu(anchor, onCopy = {}, onPaste = {}, onCut = null, onSelectAll = null)
            }
        )
        assertEquals(
            "同一份内容再递一次仍然静默",
            0, publishedChanges {
                toolbar.showMenu(anchor, onCopy = {}, onPaste = {}, onCut = null, onSelectAll = null)
            }
        )
    }

    @Test
    fun showHideShowKeepsStatusCorrectAndRepeatedHideIsSilent() {
        val toolbar = OverlayTextToolbar(ctx)
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertFalse(toolbar.isShowing)

        show(toolbar)
        assertEquals(TextToolbarStatus.Shown, toolbar.status)
        assertTrue(toolbar.isShowing)

        assertEquals(1, publishedChanges { toolbar.hide() })
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertFalse(toolbar.isShowing)

        assertEquals(
            "已经收起时再 hide 不许再打脏观察者",
            0, publishedChanges { repeat(20) { toolbar.hide() } }
        )

        assertEquals(
            "收起后重开同一份内容仍是一次真变更（null → 快照）",
            1, publishedChanges { show(toolbar) }
        )
        assertEquals(TextToolbarStatus.Shown, toolbar.status)
        assertTrue(toolbar.isShowing)
    }

    @Test
    fun readingStatusDoesNotEnlistTheReaderAsAnObserver() {
        val toolbar = OverlayTextToolbar(ctx)
        val observer = SnapshotStateObserver { it() }
        // 阳性对照：同一台观察者必须真的能收到通知，否则这一格的绿是空的
        val control = mutableStateOf(0)
        var toolbarHits = 0
        var controlHits = 0
        observer.start()
        try {
            // 模拟框架 hideSelectionToolbar()：在观察作用域里读 status
            observer.observeReads(toolbar, { toolbarHits++ }) { toolbar.status }
            observer.observeReads(control, { controlHits++ }) { control.value }

            show(toolbar)
            control.value = 1
            Snapshot.sendApplyNotifications()

            assertEquals("阳性对照必须被打脏一次", 1, controlHits)
            assertEquals(
                "读 status 不许把调用方登记成观察者——那正是菜单自激回路的入口",
                0, toolbarHits
            )
        } finally {
            observer.stop()
        }
    }

    // ═══════════ 组合层 ═══════════

    private fun putClipboard(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("probe", text))
    }

    /**
     * 输入框 + 一块"面板内、输入框外"的区域，都挂在宿主下。
     *
     * [focusRequester] 非空时挂同一颗 requester：让用例能把**真实的主动焦点**钉在输入框上，
     * 而不是靠长按顺带要来的焦点（判清焦 must-force 时，这个前提必须是显式的）。
     */
    private fun mountField(
        toolbar: OverlayTextToolbar,
        text: String = "hello world",
        focusRequester: FocusRequester? = null
    ): MutableState<TextFieldValue> {
        val value = mutableStateOf(TextFieldValue(text))
        val fieldModifier = if (focusRequester == null) {
            Modifier.testTag("field").fillMaxWidth()
        } else {
            Modifier.testTag("field").fillMaxWidth().focusRequester(focusRequester)
        }
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                OverlayTextToolbarHost(toolbar = toolbar) {
                    Column {
                        BasicTextField(
                            value = value.value,
                            onValueChange = { value.value = it },
                            modifier = fieldModifier
                        )
                        Box(Modifier.testTag("outside").fillMaxWidth().height(80.dp))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        return value
    }

    private fun longPressField() {
        rule.onNodeWithTag("field").performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun longPressMenuStaysShownAcrossManyFrames() {
        putClipboard("PASTED")
        val toolbar = OverlayTextToolbar(ctx)
        mountField(toolbar)

        longPressField()
        assertTrue("长按选词后菜单必须开着", toolbar.isShowing)

        var flips = 0
        var last = toolbar.status
        repeat(60) {
            rule.mainClock.advanceTimeBy(16L)
            val now = toolbar.status
            if (now != last) {
                flips++
                last = now
            }
        }
        assertEquals("选区没塌陷，菜单状态不许来回翻", 0, flips)
        assertTrue(toolbar.isShowing)
    }

    /**
     * 契约有意变更：旧版断言「面板内任何新按压都保菜单」，代价是点非聚焦区域后菜单永远
     * 挂着（没人清焦，框架没有收起时机）。现在完整一次轻点落在弹窗外、且手势期间框架
     * 没有重新 showMenu 时，宿主清焦、框架在失焦时收起。打开菜单的那次长按与重选区手势
     * 被「按下时开没开 + 代号是否变化」豁免，闪烁不回归——由
     * [longPressMenuStaysShownAcrossManyFrames] 与本格共同锁住。
     */
    @Test
    fun tapOnNonFocusablePanelAreaDismissesTheMenu() {
        putClipboard("PASTED")
        val toolbar = OverlayTextToolbar(ctx)
        mountField(toolbar)

        longPressField()
        assertTrue(toolbar.isShowing)

        // 面板内、输入框外的一块非聚焦区域：轻点它不移动焦点，旧版菜单就此长挂。
        rule.onNodeWithTag("outside").performTouchInput { click() }
        repeat(5) { rule.mainClock.advanceTimeBy(16L) }

        assertFalse("点非聚焦区域应经清焦 → 框架失焦收起菜单", toolbar.isShowing)
    }

    /**
     * 同一族的最强判据：先让输入框**真的**持着主动焦点，长按弹菜单，再轻点空白一次。
     *
     * 判别性在这里：`focusManager.clearFocus()` 不带 force 只清非主动焦点 ——
     * 对一颗正在获焦的输入框是空操作，菜单会继续挂着；宿主必须
     * `clearFocus(force = true)`（与面板交回输入同一口径）才走得通
     * 清焦 → 框架失焦 → deselect → hide 这条链。本格在 force=false 时红、force=true 时绿。
     */
    @Test
    fun tapOnBlankAreaDismissesMenuWhileInputHoldsActiveFocus() {
        putClipboard("PASTED")
        val toolbar = OverlayTextToolbar(ctx)
        val fieldFocus = FocusRequester()
        mountField(toolbar, focusRequester = fieldFocus)

        // 真实 Compose 焦点：requester 直接获焦，断言语义树上的 Focused 已成立
        rule.runOnIdle { fieldFocus.requestFocus() }
        rule.waitForIdle()
        rule.onNodeWithTag("field").assertIsFocused()

        longPressField()
        rule.waitForIdle()
        assertTrue("长按选词后菜单必须开着", toolbar.isShowing)
        rule.onNodeWithTag("field").assertIsFocused()

        // 只轻点一次空白（down+up）：宿主在松手时清焦，force 才交得出主动焦点
        rule.onNodeWithTag("outside").performTouchInput { click() }
        repeat(5) { rule.mainClock.advanceTimeBy(16L) }

        assertFalse(
            "点空白必须收起菜单：不带 force 的 clearFocus 清不掉主动焦点",
            toolbar.isShowing
        )
        rule.onNodeWithTag("field").assertIsNotFocused()
    }

    @Test
    fun pasteFromTheMenuInsertsClipboardText() {
        putClipboard("PASTED")
        val toolbar = OverlayTextToolbar(ctx)
        val value = mountField(toolbar)

        longPressField()
        assertTrue(toolbar.isShowing)

        // 本仓这一版 Compose 的 waitUntil 不交回布尔，等完独立判一次
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("粘贴").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "菜单里必须有且只有一颗粘贴",
            1, rule.onAllNodesWithText("粘贴").fetchSemanticsNodes().size
        )

        rule.onNodeWithText("粘贴").performClick()
        rule.mainClock.advanceTimeBy(16L)

        assertTrue(
            "点粘贴必须把剪贴板文本插进输入框，实到「${value.value.text}」",
            value.value.text.contains("PASTED")
        )
        assertFalse("动作执行完菜单即收", toolbar.isShowing)
    }

    /** 只挂宿主与一块面板区域，不挂输入框：showMenu 完全由用例驱动，排除框架干扰。 */
    private fun mountMenu(toolbar: OverlayTextToolbar) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                OverlayTextToolbarHost(toolbar = toolbar) {
                    Box(Modifier.testTag("panel").fillMaxWidth().height(200.dp))
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /**
     * 回调必须随快照走：showMenu(复制,粘贴) 组合后，框架在点击前换入 (复制,剪切,粘贴)。
     * 旧写法回调是独立 var，屏幕上还是旧 label 时 var 已换新——点「粘贴」落到下标 1 的剪切。
     * 这里停住时钟让点击落在尚未重组的旧组合上：无论落在哪份快照，都绝不许执行剪切；
     * 重组后再点，必须执行的是新粘贴回调。
     */
    @Test
    fun pasteClickBindsToTheRenderedSnapshotNeverToCut() {
        val toolbar = OverlayTextToolbar(ctx)
        mountMenu(toolbar)
        var stalePaste = 0
        var midPaste = 0
        var freshPaste = 0
        var cut = 0

        toolbar.showMenu(anchor, onCopy = {}, onPaste = { stalePaste++ }, onCut = null, onSelectAll = null)
        // Popup 是独立子窗：组合、挂窗、布局要跨帧才进语义树。停钟前必须等它真渲染出来，
        // 否则暂停后没有任何帧能补出菜单，点击只会找不到节点。
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("粘贴").fetchSemanticsNodes().isNotEmpty()
        }

        // 停时钟：showMenu 换了 label 集，但重组还没跑，屏幕上仍是 [复制, 粘贴]。
        rule.mainClock.autoAdvance = false
        toolbar.showMenu(anchor, onCopy = {}, onPaste = { midPaste++ }, onCut = { cut++ }, onSelectAll = null)
        rule.onNodeWithText("粘贴").performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点击落在哪份快照都不许错位成剪切", 0, cut)

        // 恢复时钟：新 label 集重组生效后，点粘贴必须执行新回调。
        // rect 挪一格保证这次 showMenu 必然发布新快照（结构相等只认 rect+labels）。
        rule.mainClock.autoAdvance = true
        toolbar.showMenu(Rect(12f, 41f, 220f, 80f), onCopy = {}, onPaste = { freshPaste++ }, onCut = { cut++ }, onSelectAll = null)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("剪切").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("粘贴").performClick()
        rule.mainClock.advanceTimeBy(16L)

        assertEquals("重组后点粘贴必须执行新粘贴回调", 1, freshPaste)
        assertEquals("剪切始终不许被误触发", 0, cut)
        assertTrue("两次点击至多各执行一次粘贴", stalePaste + midPaste <= 1)
        assertFalse("动作执行完菜单即收", toolbar.isShowing)
    }
}