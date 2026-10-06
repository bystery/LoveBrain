package com.lovebrain.app.ui.panel

import com.lovebrain.app.feature.composer.ComposerStore
import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.panel.counseling.CounselingPanel
import com.lovebrain.app.viewmodel.LoveBrainViewModel
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

/**
 * 谈心的模板 chip 归进 `LbChip`（`Action` 一档）之后的读数。
 *
 * 这一族本来就没有"选中"：点一下是把那句模板填进输入框，所以它交的是
 * `Role.Button` 而**不是** `Role.Tab`，语义树里也不该出现 `Selected`——
 * 给一颗按钮写"已选中"是把读屏引向一个不存在的事实（这一条与页头那三档模式正好相反，
 * 那边是真的互斥单选）。
 *
 * 归并动了文字的位置，没动热区——本轮（用户原话第 12 条"模板胶囊卡片太高"）在此基础上再收一层：
 * [TemplateChip] 从 `LbChipStyles.neutral`（单层、可见胶囊被 `touchFloor` 撑到 48 见方、标签钉左上）
 * 换到面板紧凑胶囊那一档（可见胶囊 [com.lovebrain.app.core.designsystem.AppDimens.CHIP_PANEL_HEIGHT_DP]=28、
 * 标签居中、`layeredTouch=true` 分两层）。
 *
 * R12 / §12.1 修复：`touchFloor` 关掉，行高从 48 降到 28——"整行 48dp 仍占高"就是这一条。
 * TEAM_RULES §3：确实无法同时满足紧凑视觉和全局热区下限时，保留用户指定的紧凑视觉。
 * 于是这一格钉三件能被量出来的事：
 * 1. **行高不再 48**：热区降到与可见胶囊同高（28dp），横滚行里 chip 宽度充足，可达性可接受；
 * 2. **标签回到中轴**：`TopStart` 的"贴顶 + 触底一大截空白"（原话第 12 条的可见成因）用几何判红——
 *    回退到 `neutral` 那一档时上隙只剩那点内边距、下隙撑到一大截，相等当场红（反向证人内建在断言里）；
 * 3. **语义一字未改**：还是按钮、不播报选中、读得出自己。
 *
 * ⚠ 仪器边界（如实记着，别读成"可见 28 已经量过"）：语义树只暴露那颗**外层可点盒**（合并了文案）
 * 和未合并树里那一条**标签**（`LbChipTierTest` 用的是同一对锚点）。内层那颗 28 胶囊带的是
 * `clip/background/border`，没有语义槽，JVM 侧读不到它的矩形——"可见高度真的从 48 降到 28"这一半
 * 只能真机/截图验，本轮登记为**未验证-需真机**（详见交付台账）。
 *
 * ⚠ 这一行是 `horizontalScroll`：滚出视口的那几颗在语义树里被压成 `0x0` 或半截，
 * 那不是热区不达标。筛法与样本下限抄 `SuggestCounselingTargetsTest`
 * （排除项连同尺寸打进失败信息，筛到只剩一颗就是这格在自证）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CounselingTemplateChipTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /** 生产里那六条模板的头两条：锚点用界面词表（与这一族其它格同一个做法：按可见文案定位，不靠 tag） */
    private val firstTemplate = "她突然冷淡了怎么办"
    private val secondTemplate = "我们吵架了该谁先低头"

    private fun fakeVm(): LoveBrainViewModel = mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
        // 第5节第2条 第 6 步：VM 上那 10 条纯转发口已删，状态归 ComposerStore 自己
        val composer = mockk<ComposerStore>(relaxed = true)
        every { vm.composer } returns composer
        every { composer.counselingDraft } returns MutableStateFlow("")
        every { vm.counselingResult } returns MutableStateFlow<String?>(null)
        every { vm.counselingError } returns MutableStateFlow<String?>(null)
        every { vm.isCounseling } returns MutableStateFlow(false)
        every { vm.counselingStreaming } returns MutableStateFlow("")
    }

    private fun mount(vm: LoveBrainViewModel) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                CounselingPanel(viewModel = vm, onFocusChange = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun chipsInViewport(): List<SemanticsProbe.Target> {
        val all = probe.actionableTargets(rule, "谈心面板·模板 chip")
        val known = listOf(firstTemplate, secondTemplate)
        val hit = all.filter { it.label in known }
        // 只留完整落在 360 槽里的那些：被滚动容器裁掉的读数不能拿来判尺寸
        val (visible, clipped) = hit.partition {
            it.widthDp > 0f && it.heightDp > 0f && it.leftDp >= 0f && it.leftDp + it.widthDp <= 360f - 0.5f
        }
        assertTrue(
            "视口内只量到 ${visible.size} 颗模板 chip（整树命中的是 ${hit.size} 颗）——" +
                "少了就是这一行没画开，断言会在近乎空的上扫绿。裁掉的：" +
                clipped.joinToString { it.describe() },
            visible.isNotEmpty()
        )
        return visible
    }

    /** `Action` 一档的语义：按钮、有名字、**不**播报选中 */
    @Test
    fun `template chips are buttons that invent no selection state`() {
        mount(fakeVm())
        chipsInViewport().forEach { chip ->
            assertEquals("${chip.label} 该报成按钮：" + chip.describe(), "Button", chip.role)
            assertEquals("${chip.label} 不该有一槽选中：" + chip.describe(), null, chip.selected)
            assertTrue("${chip.label} 不该是 toggle：" + chip.describe(), !chip.isToggle)
            assertTrue("${chip.label} 读得出自己", chip.labeled)
        }
    }

    /** R12/§12.1：行高从 48 降到 28，热区与可见胶囊同高（横滚行里宽度充足） */
    @Test
    fun `the template chips height matches the compact pill not the old touch floor`() {
        mount(fakeVm())
        chipsInViewport().forEach { chip ->
            assertTrue(
                "${chip.label} 的热区高度应 ≤ ${com.lovebrain.app.core.designsystem.AppDimens.CHIP_PANEL_HEIGHT_DP}dp（R12 收行占位）:" + chip.describe(),
                chip.heightDp <= com.lovebrain.app.core.designsystem.AppDimens.CHIP_PANEL_HEIGHT_DP + 1f
            )
        }
    }

    /**
     * 标签在可点盒的中轴上，而不是贴顶留一截空白（用户原话第 12 条"卡片太高"里能被几何看见的那一半）。
     *
     * 判法照 `LbChipTierTest`：外层那颗可点盒（合并了文案，热区 48）与未合并树里那条标签**各读各的**，
     * 比"标签上隙 == 下隙"。回退成 `LbChipStyles.neutral`（TopStart）时上隙塌到内边距、下隙撑到一大截，
     * 这一句当场红——反向证人内建在同一条断言里，不需要另注一件坏形状。
     */
    @Test
    fun `the template chip centers its label instead of pinning it to a full-height top edge`() {
        mount(fakeVm())
        // 外层可点盒（合并树里 label 就是这句模板，第一颗完整在视口内）
        val pill = probe.actionableTargets(rule, "谈心模板 chip·居中")
            .single { it.label == firstTemplate && it.widthDp > 0f && it.heightDp > 0f }
        // 标签从不合并树里读——那颗 28 胶囊本身没有语义槽，读得到的只有它里面这条字
        val inkNodes = rule.onAllNodes(hasText(firstTemplate), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("$firstTemplate 的标签节点应当唯一（不唯一 = 这格没在判那颗 chip）", 1, inkNodes.size)
        val ink = probe.of(inkNodes.single())

        val toTop = ink.topDp - pill.topDp
        val toBottom = (pill.topDp + pill.heightDp) - (ink.topDp + ink.heightDp)
        assertTrue(
            "模板 chip 的标签没落在热区盒的中轴上（上隙 ${"%.1f".format(toTop)}dp / " +
                "下隙 ${"%.1f".format(toBottom)}dp）：贴顶留空白就是原话第 12 条那处过高。" +
                pill.describe() + " / " + ink.describe(),
            kotlin.math.abs(toTop - toBottom) <= CENTER_TOLERANCE_DP
        )
        // R12：行高已收到 28，热区与可见同高；这里只判标签居中
        assertTrue(
            "居中之余高度不许回弹到 48（R12 已收行占位）：" + pill.describe(),
            pill.heightDp <= com.lovebrain.app.core.designsystem.AppDimens.CHIP_PANEL_HEIGHT_DP + 1f
        )
    }

    /** 点了真的要填进输入框——归并最怕"形状还在、结果没了" */
    @Test
    fun `tapping a template chip writes that template into the draft`() {
        val vm = fakeVm()
        mount(vm)
        rule.onAllNodes(hasText(secondTemplate))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        verify(exactly = 1) { vm.setCounselingDraft(secondTemplate) }
    }

    private companion object {
        /**
         * 标签上隙与下隙允许的差（dp）：居中都留 8 的余量给字体行盒那点非对称留白；
         * 回退成 `neutral` 的 TopStart 时这一差撑到 ~20dp（`LbChipTierTest` 里那格判的是
         * `toBottom - toTop >= 6f` 才算贴顶），8 这一档既能放过居中的抖动、又一定判红贴顶。
         */
        const val CENTER_TOLERANCE_DP = 8f
    }
}
