package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
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
 * [LbFieldInput] 三轴分离的行为合同：内容保真 / 视觉档 / 焦点与意图。
 *
 * 量法与它的边界（每一轴各用各的仪器，不互相冒充）：
 *  - **内容保真**：换行被拍平这件事发生在真机 IME 里（单行编辑器的 EditorInfo 缺
 *    MULTI_LINE，IME 粘贴时自己把 \n 换成空格），Robolectric 没有 IME，performTextInput
 *    绕过它——所以 IME 那一半直接录下框架递给 TextInputService 的会话参数钉
 *    `singleLine = false`（与 ReplyInputImeContractTest 同一颗尺）；值往返那一半
 *    （宿主 String 回写不重置光标、换行原样留在值里）在 JVM 上量得到，逐字判。
 *  - **视觉档**：语义树量可编辑节点自己的高度——Row 一行、Block 随内容长高，
 *    期望值从字阶 token 的 lineHeight 派生，不抄第二份数。
 *  - **焦点/意图**：意图上报走**真指针注入**（down/move/up 过真派发管线）——语义动作
 *    绕过指针系统，量不出"逐 pressed 事件上报"那颗病；焦点边沿靠两颗输入框互相抢焦点量。
 *    工具条用一颗记录桩挂在环境 LocalTextToolbar 上：组件自己零接线，长按的菜单必须
 *    能到环境那一颗，且按下不许收菜单。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbFieldInputBehaviorTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 环境工具条的记录桩：只数 show/hide，不画菜单（组件不该知道它存在） */
    private class RecordingToolbar : TextToolbar {
        var shown = 0
        var hidden = 0
        override var status: TextToolbarStatus = TextToolbarStatus.Hidden
            private set

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?
        ) {
            shown++
            status = TextToolbarStatus.Shown
        }

        override fun hide() {
            hidden++
            status = TextToolbarStatus.Hidden
        }
    }

    /** 录下框架启动输入会话时递来的 imeOptions；其余平台动作空实现（本格只量会话参数） */
    private class RecordingPlatformInput : PlatformTextInputService {
        var imeOptions: ImeOptions? = null
        var starts = 0

        override fun startInput(
            value: TextFieldValue,
            imeOptions: ImeOptions,
            onEditCommand: (List<EditCommand>) -> Unit,
            onImeActionPerformed: (ImeAction) -> Unit
        ) {
            this.imeOptions = imeOptions
            starts++
        }

        override fun stopInput() {}
        override fun showSoftwareKeyboard() {}
        override fun hideSoftwareKeyboard() {}
        override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {}
    }

    /** 宿主替身：拿着 String 状态，回写即重组（与在册调用点同一形状） */
    private fun mountField(
        maxLines: Int = 1,
        passwordVisible: Boolean = true,
        initial: String = "",
        enabled: Boolean = true,
        onInputIntent: (() -> Unit)? = null,
        onFocusChange: ((Boolean) -> Unit)? = null,
        toolbar: TextToolbar = RecordingToolbar()
    ): MutableState<String> {
        val host = mutableStateOf(initial)
        rule.setContent {
            CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                UiMatrix(360).RenderIn(LocalDensity.current.density) {
                    LbFieldInput(
                        value = host.value,
                        onValueChange = { host.value = it },
                        placeholder = PLACEHOLDER,
                        enabled = enabled,
                        maxLines = maxLines,
                        passwordVisible = passwordVisible,
                        onInputIntent = onInputIntent,
                        onFocusChange = onFocusChange
                    )
                }
            }
        }
        rule.waitForIdle()
        return host
    }

    private fun editableText(): String =
        rule.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    // ═══════════ 内容保真：换行活下来 ═══════════

    /**
     * Row 档粘贴带换行的文本：值里一个字不许被拍扁。
     * 旧形制（singleLine = maxLines == 1）把这一颗交给 IME 拍平；新形制内容轴恒 false，
     * 行数归视觉档管——值与语义树里的 EditableText 都必须原样带着 \n。
     */
    @Test
    fun `pasted newlines survive the round trip in the row tier`() {
        val host = mountField(maxLines = 1)
        rule.onNode(hasSetTextAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("line1\nline2")
        rule.waitForIdle()

        assertEquals("宿主收到的值必须原样带换行", "line1\nline2", host.value)
        assertEquals("语义树里的编辑文本也必须原样带换行", "line1\nline2", editableText())
    }

    /** Block 档同理：多行不是换行被拍平的理由，两档共用同一条内容轴 */
    @Test
    fun `pasted newlines survive the round trip in the block tier`() {
        val host = mountField(maxLines = 3)
        rule.onNode(hasSetTextAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("line1\nline2\nline3")
        rule.waitForIdle()

        assertEquals("宿主收到的值必须原样带换行", "line1\nline2\nline3", host.value)
        assertEquals("语义树里的编辑文本也必须原样带换行", "line1\nline2\nline3", editableText())
    }

    /** Row 档：硬件回车被吞掉、不往值里注入看不见的换行（粘贴的 \n 不经键盘事件，照旧保留）。 */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `row tier swallows a hardware enter without injecting an invisible newline`() {
        val host = mountField(maxLines = 1)
        val field = rule.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("abc")
        rule.waitForIdle()
        field.performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals("单行胶囊不许被硬件回车塞进换行", "abc", host.value)
    }

    /**
     * 聚焦后启动的 IME 会话必须带 MULTI_LINE（singleLine=false）——真机粘贴保留换行的
     * 那一半在 JVM 上只能这样量。Row 档的动作键仍是 Done：回车走动作、不注入 \n。
     * 焦点由外部 focusRequester 要（顺带钉住"外部给了就用外部的"那条入口）。
     */
    @Test
    fun `focused row starts a multi line ime session with the done action`() {
        val recorder = RecordingPlatformInput()
        val focusRequester = FocusRequester()
        rule.setContent {
            CompositionLocalProvider(LocalTextInputService provides TextInputService(recorder)) {
                UiMatrix(360).RenderIn(LocalDensity.current.density) {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    LbFieldInput(
                        value = "",
                        onValueChange = {},
                        placeholder = PLACEHOLDER,
                        maxLines = 1,
                        focusRequester = focusRequester
                    )
                }
            }
        }
        awaitSessionStart(recorder)

        val imeOptions = recorder.imeOptions!!
        assertFalse(
            "IME 会话必须是多行（singleLine=false → MULTI_LINE），真机粘贴才保留换行",
            imeOptions.singleLine
        )
        assertTrue(
            "Row 档动作键保持 Done：硬件回车/IME 动作键走动作，不往值里注入换行（实到 ${imeOptions.imeAction}）",
            imeOptions.imeAction == ImeAction.Done
        )
    }

    /** Block 档的会话同样是多行，但动作键交回 Default——回车就是换行，归内容轴 */
    @Test
    fun `focused block starts a multi line ime session with enter available`() {
        val recorder = RecordingPlatformInput()
        val focusRequester = FocusRequester()
        rule.setContent {
            CompositionLocalProvider(LocalTextInputService provides TextInputService(recorder)) {
                UiMatrix(360).RenderIn(LocalDensity.current.density) {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    LbFieldInput(
                        value = "",
                        onValueChange = {},
                        placeholder = PLACEHOLDER,
                        maxLines = 3,
                        focusRequester = focusRequester
                    )
                }
            }
        }
        awaitSessionStart(recorder)

        val imeOptions = recorder.imeOptions!!
        assertFalse("Block 档的 IME 会话同样必须是多行", imeOptions.singleLine)
        assertTrue(
            "Block 档动作键是 Default：IME 显示回车键，换行归内容轴（实到 ${imeOptions.imeAction}）",
            imeOptions.imeAction == ImeAction.Default
        )
    }

    private fun awaitSessionStart(recorder: RecordingPlatformInput) {
        rule.waitForIdle()
        var pumpedMs = 0L
        while (recorder.starts == 0 && pumpedMs < 2_000L) {
            rule.mainClock.advanceTimeBy(16L)
            pumpedMs += 16
        }
        assertTrue("聚焦输入框后必须启动一次 IME 输入会话（否则量不到递给 IME 的参数）", recorder.starts >= 1)
    }

    // ═══════════ 视觉档：Row 一行、Block 长高 ═══════════

    /**
     * 同一份三行文本挂两档：Row 的可编辑节点仍是一行高（胶囊不跟着长），
     * Block 长到三行高；两档的值都原样带着三行（视觉裁切不是内容裁切）。
     * 期望行高从字阶 token 派生（mdpi + fontScale 1 ⇒ sp 与 dp 同数），不抄常数。
     */
    @Test
    fun `row stays one visual line while block grows with the content`() {
        val threeLines = "aaaa\nbbbb\ncccc"
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column {
                    LbFieldInput(
                        value = threeLines,
                        onValueChange = {},
                        placeholder = ROW_PLACEHOLDER,
                        maxLines = 1
                    )
                    LbFieldInput(
                        value = threeLines,
                        onValueChange = {},
                        placeholder = BLOCK_PLACEHOLDER,
                        maxLines = 3
                    )
                }
            }
        }
        rule.waitForIdle()

        val nodes = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals("两档各一颗可编辑节点", 2, nodes.size)
        val targets = nodes.map { probe.of(it) }
        val row = targets.single { it.contentDescriptions.contains(ROW_PLACEHOLDER) }
        val block = targets.single { it.contentDescriptions.contains(BLOCK_PLACEHOLDER) }

        val lineDp = AppTypography.bodyMedium.lineHeight.value
        // 一行可视的节点高是字体行盒，实测低于 lineHeight token（token 含段距撑高）；
        // 判据钉"不超过一行"而非凑 token 数，与 Block 的区分由下面两格保持。
        assertTrue(
            "Row 档的可编辑节点必须仍是一行高（不超过一行 lineHeight ${lineDp}dp）：${row.describe()}",
            row.heightDp > 0f && row.heightDp <= lineDp
        )
        // 渲染行盒比 lineHeight token 矮且单行/多行不成线性比例（字体 ascent/descent 与段距分布不同）：
        // 不拿 token 数或 Row 单行高去凑精确 dp，钉"长到三行"的区间——高于两行 token、不超过三行 token。
        assertTrue(
            "Block 档必须随内容长到三行高（>2 行 ${2 * lineDp}dp 且 ≤3 行 ${3 * lineDp}dp）：${block.describe()}",
            block.heightDp > 2 * lineDp && block.heightDp <= 3 * lineDp
        )
        assertTrue(
            "两档的高度必须分得开（Row 一行 / Block 多行）：${row.describe()} vs ${block.describe()}",
            block.heightDp > row.heightDp + lineDp
        )
        // 视觉裁切不是内容裁切：Row 屏上只画一行，值里三行一行不少
        nodes.forEach { node ->
            assertEquals(
                "无论哪一档，值里的换行一行不许少",
                threeLines, node.config.getOrNull(SemanticsProperties.EditableText)?.text
            )
        }
    }

    // ═══════════ Row 档横向滚动（长文本不许折行裁切） ═══════════

    /**
     * Row 档：长文本必须**横向滚得动**，不是折到第二行去（`maxLines = 1` 下第二行永远看不见）。
     *
     * 判据只用横向滚动语义的 `maxValue`：内容比视口宽 ⇒ > 0。
     * 折行的实现在这颗视口上恒为 0（没有这颗节点时本格直接失败），这正是这个 bug 的形状；
     * 不拿节点高度判——`maxLines = 1` 下折行也只画一行，高度区分不出"折了还是滚了"，
     * 所以它当初躲过了视觉断言。
     */
    @Test
    fun `long row text scrolls horizontally instead of wrapping out of view`() {
        val longText = "长".repeat(120)
        val host = mountField(initial = longText)

        assertEquals("长文本一个字都不许丢（折行裁切若发生在内容轴，这里会少字符）", longText, host.value)
        assertEquals("语义树里的编辑文本同样是全文", longText, editableText())
        val (_, max) = horizontalRange()
        assertTrue(
            "长文本必须落在一颗可横向滚动的视口里（max=$max）；折行的实现这里恒为 0",
            max > 0f
        )
    }

    /**
     * 粘贴进来的长文本 + 换行：值原样保留（内容轴），同时视口仍可横向滚动（视觉轴）。
     * 两轴各判一次——把换行拍平或把长行折掉，都会让其中一条红。
     */
    @Test
    fun `a long pasted value keeps its newline and still scrolls horizontally`() {
        val longLine = "a".repeat(100)
        val pasted = "$longLine\nsecond"
        val host = mountField()
        val field = rule.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput(pasted)
        rule.waitForIdle()

        assertEquals("粘贴值必须原样保留（含换行）", pasted, host.value)
        assertEquals("语义树里的编辑文本也原样带换行", pasted, editableText())
        val (_, max) = horizontalRange()
        assertTrue("第一行仍比视口宽、仍可横向滚动（max=$max）", max > 0f)
    }

    /** 打一长串：光标必须被带进视口（否则"看不见正在打的字"就是修完滚动后的新病） */
    @Test
    fun `typing past the row viewport keeps the caret in view`() {
        val longText = "long-text-".repeat(30)
        val host = mountField()
        val field = rule.onNode(hasSetTextAction())
        field.performClick()
        rule.waitForIdle()
        field.performTextInput(longText)
        rule.waitForIdle()
        // 光标跟随是"布局落定之后的一颗 LaunchedEffect"，多泵几帧让它真的跑完
        repeat(5) { rule.mainClock.advanceTimeBy(16L) }
        rule.waitForIdle()

        assertEquals("整段都进了值", longText, host.value)
        val (offset, max) = horizontalRange()
        assertTrue("超宽文本必须可横向滚动（max=$max）", max > 0f)
        assertTrue(
            "打完一长串之后视口必须跟着光标走（scroll=$offset）：光标留在视口外 = 看不见正在打的字",
            offset > 0f
        )
    }

    /** 横向滚动视口的语义读数：(`当前偏移`, `最大偏移`)；节点不存在/没有这颗语义就当场失败 */
    private fun horizontalRange(): Pair<Float, Float> {
        // 输入框自己的语义是 mergeDescendants 的，滚动那颗必须去未合并树里找
        val node = rule.onNode(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNode()
        val range = node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
            ?: error("找不到横向滚动语义：Row 档没有把可编辑节点包进横向滚动视口")
        return rule.runOnIdle { range.value() to range.maxValue() }
    }

    // ═══════════ 焦点/意图：边沿触发 ═══════════

    /**
     * 编辑意图只在每次手势**按下那一跳**上报一次：
     *  - down + 两段 move + up 的手势 = 1 次（旧形制对每个 pressed 事件都报，这一趟至少 3 次）；
     *  - 长按（选词出菜单）= 再 1 次，且环境工具条的菜单开着、按下没把它收掉；
     *  - 普通一点（框架的 click 自带 down+move+up 三个 pressed 事件）= 再 1 次。
     * 注入走真指针管线——语义动作绕过指针系统，量不出这颗病。
     */
    @Test
    fun `intent reports once per gesture not per pressed event`() {
        var intents = 0
        val toolbar = RecordingToolbar()
        mountField(initial = "hello world", onInputIntent = { intents++ }, toolbar = toolbar)
        val field = rule.onNode(hasSetTextAction())

        field.performTouchInput {
            down(center)
            advanceEventTime(16L)
            moveBy(Offset(6f, 0f))
            advanceEventTime(16L)
            moveBy(Offset(6f, 0f))
            advanceEventTime(16L)
            up()
        }
        rule.waitForIdle()
        assertEquals("down+move+move+up 是一趟手势，只许在按下那一跳报一次", 1, intents)

        field.performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals("长按是第二趟手势，也只许报一次", 2, intents)
        assertTrue("长按选词后环境工具条的菜单必须开着（组件零接线，菜单走环境）", toolbar.shown >= 1)
        assertEquals("按下那一跳不许收菜单——选区菜单的寿命不归输入框管", 0, toolbar.hidden)

        field.performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals("普通一点（down+move+up）是第三趟手势，仍只报一次", 3, intents)
    }

    /**
     * 获焦/失焦以边沿交给宿主：挂载时的初始焦点事件不算一次失焦（宿主不必再拿
     * "哪些输入框不上报"的名单去猜失焦）；焦点挪去另一颗时，旧的那颗报 false、新的报 true。
     */
    @Test
    fun `focus gain and loss reach the host as edges`() {
        val eventsA = mutableListOf<Boolean>()
        val eventsB = mutableListOf<Boolean>()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column {
                    LbFieldInput(
                        value = "",
                        onValueChange = {},
                        placeholder = "FIELD_A",
                        onFocusChange = { eventsA += it }
                    )
                    LbFieldInput(
                        value = "",
                        onValueChange = {},
                        placeholder = "FIELD_B",
                        onFocusChange = { eventsB += it }
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTrue("挂载不许凭空报一次失焦（A 实到 $eventsA）", eventsA.isEmpty())
        assertTrue("挂载不许凭空报一次失焦（B 实到 $eventsB）", eventsB.isEmpty())

        rule.onAllNodes(hasSetTextAction())[0].performClick()
        rule.waitForIdle()
        assertEquals("点第一颗：只报一次获焦", listOf(true), eventsA)
        assertTrue("没被点的那颗不许收到信号", eventsB.isEmpty())

        rule.onAllNodes(hasSetTextAction())[1].performClick()
        rule.waitForIdle()
        assertEquals("焦点挪走：第一颗补一次失焦边沿", listOf(true, false), eventsA)
        assertEquals("第二颗报一次获焦", listOf(true), eventsB)
    }

    // ═══════════ 密码遮蔽与光标保真 ═══════════

    /** 密码档：屏上（语义树）遮蔽，宿主拿到的仍是原文——遮蔽是视觉轴的事，不动内容 */
    @Test
    fun `password masks the display but the host keeps the raw text`() {
        val host = mountField(passwordVisible = false)
        rule.onNode(hasSetTextAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("secret123")
        rule.waitForIdle()

        assertEquals("宿主必须拿到原文（遮蔽不许吃掉内容）", "secret123", host.value)
        val shown = editableText()
        assertFalse("语义树里的编辑文本不许是原文", shown == "secret123")
        assertEquals("遮蔽逐字等长", "secret123".length, shown.length)
        assertTrue(
            "密码节点必须自报 Password（读屏才念得出这是密码框）",
            rule.onNode(hasSetTextAction()).fetchSemanticsNode()
                .config.contains(SemanticsProperties.Password)
        )
    }

    /**
     * 外部 String 回写不许重置光标：连敲两段，第二段必须接在第一段后面
     * （回写把 TextFieldValue 重建成光标 0 的话，第二段会插到最前面）。
     * 再进一步：宿主真的换了一份等长文本（不是回写回声），光标仍停在原处——
     * 选区保真到"外部更新"这一档，下一笔仍落在光标处。
     */
    @Test
    fun `external write back keeps the insertion point`() {
        val host = mountField()
        rule.onNode(hasSetTextAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("first")
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("second")
        rule.waitForIdle()
        assertEquals(
            "第二笔没接在第一笔后面 = 回写把光标打回了开头",
            "firstsecond", host.value
        )

        // 宿主主动换文本（等长、非回声）：光标停在 11，下一笔仍追加在末尾
        rule.runOnIdle { host.value = "firstSECOND" }
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("!")
        rule.waitForIdle()
        assertEquals(
            "外部更新之后光标没保住：实到「${host.value}」（光标若被打回 0，! 会跑到最前面）",
            "firstSECOND!", host.value
        )
    }

    /**
     * 禁用档：按下不许上报编辑意图——"不响应"包含不把宿主拽进 EDITING
     * （否则窗口进了可聚焦态却没有焦点目标，IME 空弹）。
     */
    @Test
    fun `disabled field reports no intent even when pressed`() {
        var intents = 0
        mountField(enabled = false, onInputIntent = { intents++ })
        val field = rule.onNode(hasContentDescription(PLACEHOLDER))
        field.performTouchInput {
            down(center)
            advanceEventTime(16L)
            up()
        }
        rule.waitForIdle()
        assertEquals("禁用框按下不许上报编辑意图", 0, intents)
    }

    /**
     * 外层热区带也算"碰到输入区"：48dp 热区框里、36dp 胶囊外的上下带，按下必须报一次意图。
     * 若意图挂回内层胶囊，这一带就漏报（窗口不进 EDITING，IME 弹不出来）——
     * 这条行为尺弥补源码清册只数挂载点、数不出挂载位置的那一格。
     */
    @Test
    fun `tapping the hot zone band outside the capsule still reports intent`() {
        var intents = 0
        mountField(onInputIntent = { intents++ })
        rule.onNode(hasSetTextAction()).performTouchInput {
            // 可编辑节点顶边再往上 4dp：仍在 48dp 热区框内、已在可见胶囊外
            down(Offset(center.x, -4f))
            advanceEventTime(16L)
            up()
        }
        rule.waitForIdle()
        assertEquals("胶囊外热区带按下也必须报一次意图", 1, intents)
    }

    private companion object {
        /** 测试哨兵文案（ASCII，不占用户可见字面量的账，也不依赖资源解析语言） */
        const val PLACEHOLDER = "FIELD_PLACEHOLDER"
        const val ROW_PLACEHOLDER = "ROW_TIER_FIELD"
        const val BLOCK_PLACEHOLDER = "BLOCK_TIER_FIELD"
    }
}