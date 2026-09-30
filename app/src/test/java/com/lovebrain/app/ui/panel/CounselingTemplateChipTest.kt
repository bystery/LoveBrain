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
 * 归并时这颗的视觉只动了一处：原来那条链把 `heightIn` 排在 `background` **之后**、
 * `Box` 又没写 `contentAlignment`，于是文字贴着 48dp 胶囊的上沿；
 * `LbChip` 的胶囊一律把文字摆在中轴上，所以这一族的文字往下走了大约十 dp。
 * 这一格因此钉住两件能被量出来的事：**下限仍然过**（48 见方是文字上浮之前挣来的，
 * 不能因为"改成居中"就退回内容高度）与**语义一字未改**。
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

    /** 生产里那六条模板的头两条：锚点用界面词表，与 `DislikeReasonPanelTest` 同一做法 */
    private val firstTemplate = "她突然冷淡了怎么办"
    private val secondTemplate = "我们吵架了该谁先低头"

    private fun fakeVm(): LoveBrainViewModel = mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
        // §5.2 第 6 步：VM 上那 10 条纯转发口已删，状态归 ComposerStore 自己
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

    /** 归并动了文字的位置，没动热区：可点那颗自己仍是 48 见方 */
    @Test
    fun `the template chips still fill the touch floor`() {
        mount(fakeVm())
        chipsInViewport().forEach { chip ->
            assertTrue(
                "${chip.label} 的热区不到 ${probe.floorDp.toInt()}dp：" + chip.describe(),
                !chip.tooSmall(probe.floorDp)
            )
        }
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
}
