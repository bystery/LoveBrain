package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.ReplyCardLayout
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.feature.profile.ProfileReview
import com.lovebrain.app.feature.roundcommit.ActualSentState
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.ui.panel.settings.SETTINGS_OPACITY_SLIDER_TAG
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.UsageStats
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 设置页那颗「拦穿透」门与透明度滑条的**共处合同**（2026-10-15 真机报"滑条滑不动"）。
 *
 * 门的本职只有一件：落在设置层**空白处**的拍子不许穿透到 alpha(0) 的主面上去暗着改字。
 * v17 的旧写法（`awaitEachGesture` + 无条件 `it.consume()`）把「没人消费的**移动**」也顺手吃了
 * ——而真手指的拖动恰恰是一长串小步移动。本文件把这一课钉成三面镜子：
 *
 * · `the settings opacity slider still drags while the fall-through gate is armed` ←
 *   **整屏实证**：真面板 → 点齿轮 → 按住真滑条（`SETTINGS_OPACITY_SLIDER_TAG`）走一趟
 *   down/moveTo×6/up（与 `SettingsOpacityEntryTest.dragAcrossTheTrack` 同一配方），进度必须
 *   往右涨、松手必须落盘（`setPanelBackdropOpacityPercent` 恰被叫一次）。⚠ 初值桩在
 *   `panelBackdropOpacityPercent = 80`（显示轴中段 20%）：relaxed 的 0 = 全透 = 顶格 80，
 *   从顶格"往右拖变大"这句判据根本无从成立（第一版就是这么量瞎的：80→75 其实是
 *   down 跳中段后拖回右端的**正常**读数，不是"滑条被压死"）；
 * · `a small-step finger drag moves the witness slider under the tap-only gate` ←
 *   **24 小步见证**（本机量出的那颗病的等价形状）：门在场，滑条必须扛得住一长串小步移动。
 *   v17 的旧门在这一格**当场红**（24 小步全灭）；8 大步它侥幸能活——所以步数是判据，
 *   别改回大步（那等于把量具磨钝）；`the same drag … with no gate at all` 是拖法本身的
 *   正向对照（没牙的格子什么都证不了）；
 * · `a tap on the gated overlay does not reach the main page` / `without the gate …` ←
 *   门的本职与它的反向证人（门摘了同一拍必须能到主面，证明那颗 0 是门拦的不是夹具瞎）；
 * · `the production gate blocks taps and never touches moves` ← 源码尺：生产只许用
 *   `detectTapGestures` 的空实现（吃 down/up、不碰 move）；v17 的两颗旧写法
 *   （无条件 consume、`awaitFirstDown(requireUnconsumed = false)` 那颗门锚）禁回来。
 *
 * ⚠ 滑条拖动的注入走 `performTouchInput`（真 MotionEvents 过真派发管线），
 *   不是语义动作——语义动作绕过指针系统，量不出这颗病。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w360dp-h1000dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsOverlayPointerGateTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var vm: LoveBrainViewModel

    // ═══════════ 整屏实证：真面板 → 齿轮 → 真滑条 ═══════════

    /**
     * 门在场，滑条必须还能拖（预览与落盘两半都活着）。
     */
    @Test
    fun `the settings opacity slider still drags while the fall-through gate is armed`() {
        mountFullPanel()
        val before = sliderProgress()
        dragAcrossTheTrack()
        rule.waitForIdle()
        val after = sliderProgress()
        assertTrue(
            "门在场透明度滑条必须还能拖：进度 before=$before after=$after（显示轴，往右拖=更透）",
            after > before
        )
        // 松手那一拍必须落盘（onValueChangeFinished → setPanelBackdropOpacityPercent）
        verify(exactly = 1) { vm.setPanelBackdropOpacityPercent(any()) }
    }

    // ═══════════ 小步见证：24 步的拖法在门下必须活 ═══════════

    /**
     * 24 小步（每步 ≈6.8px，真手指的步幅）打在门下的见证滑条上，必须能动。
     * v17 的旧门在这一格当场红（移动被门吃掉，滑条一条都收不到）；
     * `no gate at all` 那格是同一拖法的正向对照。
     * 这台仪器一次 setContent 的规矩不可破（`Cannot call setContent twice per test`），两格各占一个用例。
     */
    @Test
    fun `a small-step finger drag moves the witness slider under the tap-only gate`() =
        dragWitnessUnder(GateMode.TAP_ONLY)

    @Test
    fun `a small-step finger drag moves the witness slider with no gate at all`() =
        dragWitnessUnder(GateMode.NONE)

    // ═══════════ 门的本职与反向证人 ═══════════

    /** 落进设置层空白处的拍子不许穿透：主面那颗计数器必须一次都不响。 */
    @Test
    fun `a tap on the gated overlay does not reach the main page`() {
        mountMechanism(GateMode.TAP_ONLY) { }
        rule.onNodeWithTag(GATE_ROOT_TAG).performTouchInput {
            // 层顶空白（不在滑条上、也不在任何控件上）
            down(Offset(centerX, 20f))
            up()
        }
        rule.waitForIdle()
        assertEquals("拦穿透本职不许丢：主面不该收到任何一拍", 0, mainTaps)
    }

    /** 反向证人：门摘了，同一拍必须能到主面——证明上一格的 0 是门拦的，不是夹具摸空。 */
    @Test
    fun `without the gate the same tap does reach the main page`() {
        mountMechanism(GateMode.NONE) { }
        rule.onNodeWithTag(GATE_ROOT_TAG).performTouchInput {
            down(Offset(centerX, 20f))
            up()
        }
        rule.waitForIdle()
        assertEquals("门摘了拍子就该落到主面：这格红=夹具没牙，上一格的 0 不算数", 1, mainTaps)
    }

    // ═══════════ 源码尺：生产门的形状 ═══════════

    /**
     * 生产那颗门（`LoveBrainPanelScreen.kt`）只许是 `detectTapGestures` 的空实现：
     * 吃 down/up、绝不碰 move。v17 的两颗旧写法都禁回来——
     * `event.changes.forEach { it.consume() }`（无条件盖章）与
     * `awaitFirstDown(requireUnconsumed = false)`（自绘门锚，移动同罪）。
     * 门整颗拆掉也会在这里红：拦穿透本职还在，要动它先想清楚拿什么接。
     */
    @Test
    fun `the production gate blocks taps and never touches moves`() {
        val panel = File("src/main/java/com/lovebrain/app/ui/panel/LoveBrainPanelScreen.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/LoveBrainPanelScreen.kt")
        val code = SourceScan.maskComments(panel.readText(Charsets.UTF_8))

        assertTrue(
            "拦穿透门整颗没了（锚 detectTapGestures 找不到）：要么它还在、要么这里需要有意识的决定",
            code.contains("detectTapGestures { }")
        )
        assertTrue(
            "无条件吃全部事件的旧写法回来了（v17 压死滑条的第一颗）：event.changes.forEach { it.consume() }",
            !code.contains("event.changes.forEach { it.consume() }")
        )
        assertTrue(
            "自绘门锚回来了（awaitEachGesture + awaitFirstDown 的门会吃移动，同一罪）：awaitFirstDown(requireUnconsumed = false)",
            !code.contains("awaitFirstDown(requireUnconsumed = false)")
        )
    }

    // ═══════════ 夹具 ═══════════

    /**
     * 整屏挂载（与 `PanelHostSemanticsTest` 同一套桩），走生产齿轮开设置页。
     * `panelBackdropOpacityPercent` 桩在 80：显示轴落在中段（20%），
     * "往右拖变大"这句判据才立得住（relaxed 的 0 = 全透 = 顶格 80，没法往右）。
     */
    private fun mountFullPanel() {
        vm = fakeVm()
        rule.setContent {
            val cell = UiMatrix(360, 1000)
            cell.RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = vm,
                        onInputFocusChange = { _, _ -> },
                        onInputIntent = { },
                        onClearComposeFocus = { },
                        onResize = { _, _ -> },
                        onMove = { _, _ -> },
                        onCopy = { },
                        onOpenAppPage = { },
                        onCollapse = { }
                    )
                }
            }
        }
        rule.waitForIdle()
        // 开设置页：走生产那颗齿轮（PanelHeader 左段的 24dp 热区），不直捅内部状态
        rule.onNodeWithContentDescription(ctx.getString(R.string.settings_title)).performClick()
        rule.waitForIdle()
    }

    /**
     * 从滑杆自己那一格里横着走一趟（与 `SettingsOpacityEntryTest.dragAcrossTheTrack` 同一配方：
     * 起手与落点各让开半颗球那一截，六步 moveTo；坐标用节点局部系，尺寸不在注入过程中读树）。
     */
    private fun dragAcrossTheTrack() {
        rule.onNodeWithTag(SETTINGS_OPACITY_SLIDER_TAG).performTouchInput {
            val y = height / 2f
            val edge = 10f
            down(Offset(edge, y))
            val steps = 6
            for (i in 1..steps) moveTo(Offset(edge + (width - 2 * edge) * i / steps, y))
            up()
        }
    }

    /** 滑条当前的进度语义读数（Material 的 sliderSemantics 交的，走显示轴） */
    private fun sliderProgress(): Float =
        rule.onNodeWithTag(SETTINGS_OPACITY_SLIDER_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
            .current

    private var mainTaps = 0

    /** 门的形状档：无门（对照）/ 只拦拍（生产形状） */
    private enum class GateMode { NONE, TAP_ONLY }

    /**
     * 最小机制见证：主面（带 spy）在底下，设置层盖在上面；门形状由 [mode] 切。
     * 层里挂一颗接 [onSlide] 的见证滑条，供小步拖动那格打点。
     */
    private fun mountMechanism(mode: GateMode, onSlide: (Float) -> Unit) {
        mainTaps = 0
        rule.setContent {
            LoveBrainTheme {
                Box(Modifier.fillMaxSize()) {
                    // 主面（被盖住的那层）：落到它身上的点击记数
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF202830))
                            .clickable { mainTaps++ }
                    )
                    // 设置层：与生产 overlay Column 同一条链的形状
                    //（fillMaxSize → 门 → 背板；生产形状由源码尺那格钉着）
                    Column(
                        Modifier
                            .fillMaxSize()
                            .then(gateModifier(mode))
                            .background(Color(0xCC101418))
                            .testTag(GATE_ROOT_TAG)
                    ) {
                        Spacer(Modifier.height(80.dp))
                        Text(
                            text = "设置层",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Slider(
                            value = 60f,
                            onValueChange = onSlide,
                            valueRange = 0f..100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag(WITNESS_SLIDER_TAG)
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun gateModifier(mode: GateMode): Modifier = when (mode) {
        GateMode.NONE -> Modifier
        // 与生产同一颗形状（源码尺那格钉着）：detectTapGestures 空实现——只拦拍，不碰移动
        GateMode.TAP_ONLY -> Modifier.pointerInput(Unit) {
            detectTapGestures { }
        }
    }

    private fun dragWitnessUnder(mode: GateMode) {
        var witness = 60f
        mountMechanism(mode) { witness = it }
        val before = witness
        rule.onNodeWithTag(WITNESS_SLIDER_TAG).performTouchInput {
            val y = height / 2f
            down(Offset(width * 0.3f, y))
            val steps = 24
            for (i in 1..steps) {
                moveTo(
                    Offset(
                        x = width * 0.3f + (width * 0.5f) * i / steps,
                        y = y + if (i % 2 == 0) 1f else 0f
                    )
                )
            }
            up()
        }
        rule.waitForIdle()
        assertTrue(
            "门形状 $mode 下 24 小步拖动必须让滑条动：before=$before after=$witness",
            witness > before
        )
    }

    /** 持有者侧桩：与 `PanelHostSemanticsTest` 同一套（整屏那些流一条条点名） */
    private fun fakeVm(): LoveBrainViewModel {
        val vm = mockk<LoveBrainViewModel>(relaxed = true)
        val composer = mockk<ComposerStore>(relaxed = true)
        every { vm.composer } returns composer
        every { composer.panelMode } returns MutableStateFlow(0)
        every { vm.panelBackdropOpacityPercent } returns 80
        every { vm.replyCardLayout } returns ReplyCardLayout.DEFAULT
        every { vm.activeKb } returns MutableStateFlow(null)
        every { vm.actualSentState } returns MutableStateFlow(ActualSentState.IDLE)
        every { vm.composerMode } returns MutableStateFlow(ComposerMode.REPLY)
        every { composer.counselingDraft } returns MutableStateFlow("")
        every { vm.counselingError } returns MutableStateFlow(null)
        every { vm.counselingResult } returns MutableStateFlow(null)
        every { vm.counselingStreaming } returns MutableStateFlow("")
        every { vm.currentNotice } returns MutableStateFlow<NoticeBoard.Notice?>(null)
        every { composer.currentRole } returns MutableStateFlow(ChatMessage.Role.HER)
        every { composer.inputKind } returns MutableStateFlow(ComposerInputKind.HER)
        every { vm.currentVector } returns MutableStateFlow(emptyMap())
        every { composer.draftText } returns MutableStateFlow("")
        every { composer.editingIndex } returns MutableStateFlow(-1)
        every { vm.feedbacks } returns MutableStateFlow(emptyMap())
        every { vm.generationRoundId } returns MutableStateFlow(1)
        every { composer.ideaComposeMode } returns MutableStateFlow(false)
        every { vm.inputChanged } returns MutableStateFlow(false)
        every { vm.intents } returns fakeIntents()
        every { vm.isCounseling } returns MutableStateFlow(false)
        every { vm.isGenerating } returns MutableStateFlow(false)
        every { vm.isGeneratingCore } returns MutableStateFlow(false)
        every { vm.isProactive } returns MutableStateFlow(false)
        every { vm.kbNotice } returns MutableStateFlow(null)
        every { composer.messages } returns MutableStateFlow(emptyList())
        every { vm.onlyThisRound } returns MutableStateFlow(false)
        every { vm.panelWarning } returns MutableStateFlow(null)
        every { vm.proactiveError } returns MutableStateFlow(null)
        every { vm.proactiveOptions } returns MutableStateFlow(emptyList())
        every { vm.profileRegenerating } returns MutableStateFlow(false)
        every { vm.profileReview } returns MutableStateFlow(ProfileReview())
        every { vm.providerReady } returns MutableStateFlow(true)
        every { vm.result } returns MutableStateFlow(null)
        every { vm.resultMode } returns MutableStateFlow(ResultMode.REPLY)
        every { vm.rewriteStates } returns MutableStateFlow(emptyMap())
        every { vm.stageSuggestion } returns MutableStateFlow(null)
        every { vm.streamingCoreText } returns MutableStateFlow("")
        every { vm.streamingSchemes } returns MutableStateFlow(emptyList())
        every { vm.usageStats } returns MutableStateFlow(UsageStats())
        every { vm.vectorDelta } returns MutableStateFlow(emptyMap())
        every { vm.vectorUpdate } returns MutableStateFlow(null)
        // 设置页会多收这几条：设置开着时 relaxed 泛型流一读就 CCE，显式给类型
        every { vm.knowledgeBasesState } returns MutableStateFlow(emptyList())
        return vm
    }

    private fun fakeIntents(): IntentController = mockk<IntentController>(relaxed = true).also {
        every { it.config } returns MutableStateFlow(IntentConfig())
        every { it.showEditor } returns MutableStateFlow(false)
        every { it.isSaving } returns MutableStateFlow(false)
        every { it.savedReceipts } returns MutableStateFlow(0)
    }

    private companion object {
        const val GATE_ROOT_TAG = "gate-probe-overlay"
        const val WITNESS_SLIDER_TAG = "gate-probe-slider"
    }
}
