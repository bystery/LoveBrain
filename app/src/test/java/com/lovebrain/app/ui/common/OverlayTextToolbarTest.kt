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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
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
 *
 * 组合层：真挂一颗 BasicTextField 到宿主下，长按出菜单后连推多帧，判状态不来回翻、
 * 面板内新按压不拆菜单、点粘贴真能把剪贴板文本插进去。
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

    /** 输入框 + 一块"面板内、输入框外"的区域，都挂在宿主下。 */
    private fun mountField(
        toolbar: OverlayTextToolbar,
        text: String = "hello world"
    ): MutableState<TextFieldValue> {
        val value = mutableStateOf(TextFieldValue(text))
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                OverlayTextToolbarHost(toolbar = toolbar) {
                    Column {
                        BasicTextField(
                            value = value.value,
                            onValueChange = { value.value = it },
                            modifier = Modifier.testTag("field").fillMaxWidth()
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

    @Test
    fun aNewDownInsideThePanelDoesNotTearDownAnActiveMenu() {
        putClipboard("PASTED")
        val toolbar = OverlayTextToolbar(ctx)
        mountField(toolbar)

        longPressField()
        assertTrue(toolbar.isShowing)

        // 面板内、输入框外的一次新按压。旧宿主在这里无条件 hide()，
        // 与框架紧接着的重新 showMenu 对着干，表现就是菜单/手柄闪一下。
        rule.onNodeWithTag("outside").performTouchInput { click() }
        repeat(10) { rule.mainClock.advanceTimeBy(16L) }

        assertTrue("选区还活着，新按压不该把菜单拆掉", toolbar.isShowing)
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
}