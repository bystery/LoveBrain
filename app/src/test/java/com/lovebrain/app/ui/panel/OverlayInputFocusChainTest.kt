package com.lovebrain.app.ui.panel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Magnifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbFieldInput
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.service.PanelFocusMode
import com.lovebrain.app.service.PanelFocusWindow
import com.lovebrain.app.service.PanelInputFocusOwner
import com.lovebrain.app.ui.common.OverlayTextToolbar
import com.lovebrain.app.ui.common.OverlayTextToolbarHost
import com.lovebrain.app.ui.panel.reply.MemoryCorrectionFlow
import com.lovebrain.app.ui.panel.reply.MemoryCorrectionFlowHost
import com.lovebrain.app.ui.panel.reply.SchemeAdjustingBlock
import com.lovebrain.app.ui.panel.settings.SettingsIntentEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * 悬浮窗四颗输入面（意图编辑器 / 设置页意图 / 方案自定义改写 / 标错草稿框）的焦点链路合同。
 *
 * 每颗面都挂在真的 [OverlayTextToolbarHost] 下，宿主替身是**真的焦点状态机**
 * [PanelInputFocusOwner]（假窗口只记 flags）：意图进 EDITING、失焦回 PASSIVE 判的是
 * 生产那一份判据，不是测试另抄的状态。面板按面绑定上报器的形状也照生产那一颗
 * [rememberOverlayFocusReporter]，id 与面板调用点同名。
 *
 * 量法与边界：
 *  - 意图上报走**真指针注入**（down/move/up 过真派发管线）——语义动作绕过指针系统，
 *    量不出"逐 pressed 事件上报"那颗病；每趟手势只许在按下那一跳报一次。
 *  - 获焦/失焦以边沿判：挂载不许凭空报失焦；焦点挪去面外那颗探针时报 false，
 *    状态机必须随之交回输入所有权（这就是"失焦释放输入"）。
 *  - 离树补报单独判：面关掉（退场动画播完摘树 / 开关拨关整段摘除）之后仍须报一次 false——
 *    本版 Compose 不给已摘除节点派发焦点事件，只接边沿的实现会在这里红。
 *  - 长按菜单走真 [OverlayTextToolbar]：选词出菜单、菜单开着时再长按（down 压在菜单上）
 *    不许把它收掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi",
    shadows = [ShadowMagnifier::class]
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayInputFocusChainTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** 假窗口：只记 flags，不碰 WindowManager（与 PanelInputFocusOwnerTest 同一形状） */
    private class FakePanel : PanelFocusWindow {
        var lastFlags: Int? = null
        override fun isPresent(): Boolean = true
        override fun applyFlags(flags: Int): Boolean {
            lastFlags = flags
            return true
        }
        override fun hideIme() {}
    }

    /** 宿主替身：真状态机 + 逐笔记录，id 与生产调用点同名 */
    private class Host(val id: String) {
        val owner = PanelInputFocusOwner(FakePanel())
        val focusReports = mutableListOf<Pair<String, Boolean>>()
        var intents = 0
        val onIntent: () -> Unit = {
            intents++
            owner.onInputIntent(id)
        }
        val onFocusChange: (Any, String, Boolean) -> Unit = { instanceKey, inputId, focused ->
            focusReports += inputId to focused
            owner.onInputFocusChanged(instanceKey, inputId, focused)
        }
    }

    private class Mounted(val toolbar: OverlayTextToolbar, val probe: FocusRequester)

    private val probeTag = "focus_chain_probe"

    /** 面外那颗转焦探针：不在上报通道里，只负责把焦点拿走 */
    private fun surfaceField(): SemanticsNodeInteraction =
        rule.onNode(hasSetTextAction() and hasTestTag(probeTag).not())

    private fun mountChain(host: Host, surface: @Composable () -> Unit): Mounted {
        val toolbar = OverlayTextToolbar(ctx)
        val probe = FocusRequester()
        // 剪贴板种一份：空草稿长按也出得了粘贴菜单
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("probe", "PASTED"))
        rule.setContent {
            UiMatrix(360, heightDp = 900).RenderIn(LocalDensity.current.density) {
                OverlayTextToolbarHost(toolbar = toolbar) {
                    BasicTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.testTag(probeTag).focusRequester(probe)
                    )
                    CompositionLocalProvider(
                        LocalOverlayInputFocus provides
                            rememberOverlayFocusReporter(host.id, host.onFocusChange)
                    ) {
                        surface()
                    }
                }
            }
        }
        rule.waitForIdle()
        return Mounted(toolbar, probe)
    }

    /** down + 两段 move + up：一趟手势，只许在按下那一跳报一次意图 */
    private fun tapGesture(field: SemanticsNodeInteraction) {
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
    }

    /**
     * 四颗面共用的链路判据：挂载不报失焦 → 一趟手势一次意图 + 获焦上报 + EDITING →
     * 两趟长按各报一次且菜单开着（第二趟的 down 压在开着的菜单上，不许收）→
     * 焦点挪去探针：报失焦、状态机交回输入所有权。
     */
    private fun assertIntentAndFocusChain(host: Host, mounted: Mounted): SemanticsNodeInteraction {
        val field = surfaceField()
        assertEquals("触碰前不许抢焦点", PanelFocusMode.PASSIVE, host.owner.mode)
        assertNull(host.owner.activeInputId)
        assertTrue("挂载不许凭空报失焦：${host.focusReports}", host.focusReports.isEmpty())

        tapGesture(field)
        assertEquals("down+move+move+up 是一趟手势，只许报一次", 1, host.intents)
        assertEquals("碰到输入区必须进 EDITING", PanelFocusMode.EDITING, host.owner.mode)
        assertEquals(host.id, host.owner.activeInputId)
        assertEquals("获焦边沿必须报给宿主", listOf(host.id to true), host.focusReports)

        field.performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals("长按是第二趟手势，也只许报一次", 2, host.intents)
        assertTrue("长按选词后菜单必须开着", mounted.toolbar.isShowing)
        repeat(5) { rule.mainClock.advanceTimeBy(16L) }
        assertTrue("选区还在，菜单不许自己翻收", mounted.toolbar.isShowing)

        // 菜单开着再长按一趟（落在第一个词上，重选区）：down 压在菜单上也不许收
        field.performTouchInput { longClick(Offset(20f, center.y)) }
        rule.waitForIdle()
        assertEquals("重选区是第三趟手势，仍只报一次", 3, host.intents)
        assertTrue("重选区的 down 不许收菜单", mounted.toolbar.isShowing)

        rule.runOnIdle { mounted.probe.requestFocus() }
        rule.waitForIdle()
        assertEquals("焦点挪走必须补一次失焦边沿", host.id to false, host.focusReports.last())
        assertEquals("失焦必须释放输入所有权", PanelFocusMode.PASSIVE, host.owner.mode)
        assertNull(host.owner.activeInputId)
        return field
    }

    /** 重新获焦后把面摘出树：离树补报那一次也必须交回输入（边沿回调等不到已摘除的节点） */
    private fun assertDisposeBlurReleases(
        host: Host,
        field: SemanticsNodeInteraction,
        removeSurface: () -> Unit
    ) {
        field.performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals("重新获焦必须再进 EDITING", PanelFocusMode.EDITING, host.owner.mode)
        assertEquals(host.id, host.owner.activeInputId)

        rule.runOnIdle { removeSurface() }
        rule.waitForIdle()
        assertEquals("面离树必须补报一次失焦", host.id to false, host.focusReports.last())
        assertEquals("离树失焦同样要释放输入", PanelFocusMode.PASSIVE, host.owner.mode)
        assertNull(host.owner.activeInputId)
    }

    // ═══════════ 意图编辑器 ═══════════

    @Test
    fun intentEditorReportsIntentFocusAndMenuThroughTheOverlayChain() {
        val host = Host("intent_editor")
        val visible = mutableStateOf(true)
        val mounted = mountChain(host) {
            IntentEditorDialog(
                text = "hello world",
                enabled = true,
                visible = visible.value,
                onSave = { _, _ -> },
                onDismiss = { visible.value = false },
                onInputIntent = host.onIntent
            )
        }
        val field = assertIntentAndFocusChain(host, mounted)
        // 关浮层：退场播完摘树，离树补报兜住（清焦交接在面板那一侧，这里判面内这一半）
        assertDisposeBlurReleases(host, field) { visible.value = false }
    }

    // ═══════════ 设置页持续意图 ═══════════

    @Test
    fun settingsIntentReportsIntentFocusAndMenuThroughTheOverlayChain() {
        val host = Host("settings")
        val enabled = mutableStateOf(true)
        val mounted = mountChain(host) {
            SettingsIntentEntry(
                intentEnabled = enabled.value,
                intentText = "hello world",
                onIntentChange = { _, _ -> },
                onInputIntent = host.onIntent
            )
        }
        val field = assertIntentAndFocusChain(host, mounted)
        // 拨关开关：输入整段立即摘树（没有退场动画），离树补报是唯一兜得住的那一半
        assertDisposeBlurReleases(host, field) { enabled.value = false }
    }

    // ═══════════ 方案自定义改写 ═══════════

    @Test
    fun schemeCustomRewriteReportsIntentFocusAndMenuThroughTheOverlayChain() {
        val host = Host("scheme_adjust")
        val mounted = mountChain(host) {
            SchemeAdjustingBlock(
                onRewrite = {},
                onCustomRewrite = {},
                customDraft = "hello world",
                onCustomDraftChange = {},
                onInputIntent = host.onIntent,
                modifier = Modifier.fillMaxWidth()
            )
        }
        assertEquals(
            "点开自定义之前不该有输入框",
            0, rule.onAllNodes(hasSetTextAction() and hasTestTag(probeTag).not()).fetchSemanticsNodes().size
        )
        rule.onAllNodes(hasText(RewriteCommand.CUSTOM.displayLabel)).onFirst().performClick()
        rule.waitForIdle()
        // 展开那一次点名意图 + 过一帧抢焦点：两件事都必须真的落地
        assertTrue("展开必须点名编辑意图", host.intents >= 1)
        assertEquals("展开抢焦必须进 EDITING", PanelFocusMode.EDITING, host.owner.mode)
        assertEquals(host.id, host.owner.activeInputId)
        assertEquals("自动获焦也要报边沿", host.id to true, host.focusReports.last())

        val field = surfaceField()
        val intentsBefore = host.intents
        tapGesture(field)
        assertEquals("多一趟手势多报一次（边沿触发）", intentsBefore + 1, host.intents)
        field.performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(intentsBefore + 2, host.intents)
        assertTrue("长按选词后菜单必须开着", mounted.toolbar.isShowing)

        rule.runOnIdle { mounted.probe.requestFocus() }
        rule.waitForIdle()
        assertEquals(host.id to false, host.focusReports.last())
        assertEquals("失焦必须释放输入所有权", PanelFocusMode.PASSIVE, host.owner.mode)
        assertNull(host.owner.activeInputId)

        // 收起自定义：输入框离树，补报不许漏（宿主已 PASSIVE，这一笔是空操作但必须报）
        rule.onAllNodes(hasText(RewriteCommand.CUSTOM.displayLabel)).onFirst().performClick()
        rule.waitForIdle()
        assertEquals("收起离树也要补报失焦", host.id to false, host.focusReports.last())
        assertEquals(PanelFocusMode.PASSIVE, host.owner.mode)
    }

    // ═══════════ 标错草稿框（M3） ═══════════

    @Test
    fun markWrongDraftBoxReportsTheOverlayChainAndKeepsItsCorrectionSemantics() {
        val host = Host("memory_wrong")
        val flow = MemoryCorrectionFlow()
        val mounted = mountChain(host) {
            MemoryCorrectionFlowHost(
                flow = flow,
                onMute = { _, _ -> },
                onWrong = { _, _ -> },
                onInputIntent = host.onIntent
            )
        }
        rule.runOnIdle {
            flow.requestWrong("mem-1")
            flow.editWrongDraft("hello world")
        }
        rule.waitForIdle()
        val field = assertIntentAndFocusChain(host, mounted)

        // 读屏名字仍是那句说明（与上方说明行共用一条资源），占位文案也没换
        val hint = ctx.getString(R.string.memory_wrong_input_hint)
        val descs = field.fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue("草稿框必须仍念得出那句说明：$descs", descs.contains(hint))

        // 纠正语义不变：敲进去的字进 holder 那份草稿（先清空旧稿，量"敲进去的字"本身）
        rule.runOnIdle { flow.editWrongDraft("") }
        rule.waitForIdle()
        field.performTouchInput { click(center) }
        rule.waitForIdle()
        field.performTextInput("她说明天再答")
        rule.waitForIdle()
        assertEquals("她说明天再答", flow.wrongDraft)

        // 关浮层（dismiss）：退场播完摘树，离树补报兜住
        assertDisposeBlurReleases(host, field) { flow.dismiss() }
    }

    // ═══════════ 同 id 多实例：跨卡切换焦点 / 离树补报按实例认领 ═══════════

    /**
     * 方案卡那一种形状的另一半：多颗自定义框共绑同一 id `scheme_adjust`，用户从 A 卡的自定义框
     * 直接点去 B 卡的自定义框。两条焦点边沿共用一颗 id，旧状态机"获焦只登记、不进 EDITING"
     * ⇒ A 的失焦把窗口打回 PASSIVE 之后没人再带回去，键盘消失、要点第二次。
     * 现在获焦这一跳自己确证编辑态，所以切过去之后必须仍在 EDITING。
     */
    @Test
    fun switchingBetweenTwoFieldsThatShareTheSameInputIdStaysInEditing() {
        val host = Host("scheme_adjust")
        val toolbar = OverlayTextToolbar(ctx)
        rule.setContent {
            UiMatrix(360, heightDp = 900).RenderIn(LocalDensity.current.density) {
                OverlayTextToolbarHost(toolbar = toolbar) {
                    CompositionLocalProvider(
                        LocalOverlayInputFocus provides
                            rememberOverlayFocusReporter(host.id, host.onFocusChange)
                    ) {
                        Column {
                            LbFieldInput(
                                value = "",
                                onValueChange = {},
                                placeholder = "FIELD_A",
                                onInputIntent = host.onIntent,
                                onFocusChange = rememberOverlayInputFocusReporter()
                            )
                            LbFieldInput(
                                value = "",
                                onValueChange = {},
                                placeholder = "FIELD_B",
                                onInputIntent = host.onIntent,
                                onFocusChange = rememberOverlayInputFocusReporter()
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onAllNodes(hasSetTextAction())[0].performClick()
        rule.waitForIdle()
        assertEquals("点 A 卡必须进 EDITING", PanelFocusMode.EDITING, host.owner.mode)
        assertEquals(host.id, host.owner.activeInputId)

        rule.onAllNodes(hasSetTextAction())[1].performClick()
        rule.waitForIdle()
        assertEquals(
            "A 卡 → B 卡（同 id）：窗口必须仍停在 EDITING——退回 PASSIVE 就是键盘消失、再点一次那个 bug",
            PanelFocusMode.EDITING, host.owner.mode
        )
        assertEquals("所有权仍在这颗 id 上", host.id, host.owner.activeInputId)
        // 两条获焦边沿（A 一次、B 一次）都真的报到了宿主；不判先后——进出顺序归框架，
        // 这一格判的是"切过去之后还留在 EDITING"，那一条对两种顺序都成立。
        assertEquals(
            "B 卡获焦也必须报一条 true 边沿：${host.focusReports}",
            2, host.focusReports.count { it.first == host.id && it.second }
        )
    }

    /**
     * 方案卡那一种形状：多颗自定义框共绑同一 id `scheme_adjust`。未获焦的兄弟实例
     * 被回收（横向列表滑出视口）时不许补报 false——那会把另一颗仍持焦的框的
     * 输入所有权打掉（光标还在、窗口回到 PASSIVE，键盘消失）。
     */
    @Test
    fun disposingAnUnfocusedSiblingWithTheSameInputIdKeepsTheFocusedInputOwned() {
        val host = Host("scheme_adjust")
        val showB = mutableStateOf(true)
        val toolbar = OverlayTextToolbar(ctx)
        rule.setContent {
            UiMatrix(360, heightDp = 900).RenderIn(LocalDensity.current.density) {
                OverlayTextToolbarHost(toolbar = toolbar) {
                    CompositionLocalProvider(
                        LocalOverlayInputFocus provides
                            rememberOverlayFocusReporter(host.id, host.onFocusChange)
                    ) {
                        Column {
                            LbFieldInput(
                                value = "",
                                onValueChange = {},
                                placeholder = "FIELD_A",
                                onInputIntent = host.onIntent,
                                onFocusChange = rememberOverlayInputFocusReporter()
                            )
                            if (showB.value) {
                                LbFieldInput(
                                    value = "",
                                    onValueChange = {},
                                    placeholder = "FIELD_B",
                                    onInputIntent = host.onIntent,
                                    onFocusChange = rememberOverlayInputFocusReporter()
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onAllNodes(hasSetTextAction())[0].performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals("点第一颗必须进 EDITING", PanelFocusMode.EDITING, host.owner.mode)
        assertEquals(host.id, host.owner.activeInputId)
        assertEquals(listOf(host.id to true), host.focusReports)

        rule.runOnIdle { showB.value = false }
        rule.waitForIdle()
        assertEquals(
            "未获焦的兄弟实例离树不许打掉持焦者的所有权",
            PanelFocusMode.EDITING, host.owner.mode
        )
        assertEquals(host.id, host.owner.activeInputId)
        assertEquals("兄弟离树不许补报 false：${host.focusReports}", listOf(host.id to true), host.focusReports)
    }
}

/**
 * Robolectric 起不了真窗口表面生命周期：平台放大镜建窗时 BLASTBufferQueue.nativeGetSurface
 * 拿不到 Surface（mSurface 恒 null），收起时 InternalPopupWindow.destroy() 无空判直接 NPE。
 * 放大镜只是选区 loupe，显示/收起整体空转不碰选区、菜单与焦点链路，行为断言照常量。
 */
@Implements(Magnifier::class)
class ShadowMagnifier {

    @Implementation
    protected fun show(sourceCenterX: Float, sourceCenterY: Float) {
    }

    @Implementation
    protected fun show(
        sourceCenterX: Float,
        sourceCenterY: Float,
        windowCenterX: Float,
        windowCenterY: Float
    ) {
    }

    @Implementation
    protected fun update() {
    }

    @Implementation
    protected fun dismiss() {
    }
}