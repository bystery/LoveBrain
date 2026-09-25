package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.ui.panel.counseling.CounselingPanel
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 :490 剩下的那几处自造按钮——**这两块面板第一次挂进 JVM 仪器**。
 *
 * 账本 §38.7 / §39.9 连着两格写着同一句："不搬不是因为它们没问题，是因为我证不了"。
 * 证不了的原因是没挂起来，而没挂起来的理由当时写的是"要 VM"。
 * 供应商页那一格早就证明过：**页面与 VM 之间的合同就是那几条流**
 * （`mockk(relaxed = true)` + 逐条 `every { vm.xxx } returns MutableStateFlow(...)`），
 * 所以这一格照那个形状挂 `SuggestPanel` 与 `CounselingPanel`。
 *
 * ⚠ relaxed 桩的坑上一格刚踩过：带泛型的 `StateFlow<String?>` 不显式桩就会
 * `ClassCastException`（relaxed 交回泛型 mock，`.value` 一取就炸）。
 * 这里凡是流都显式给值，一条不靠 relaxed。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestCounselingTargetsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun fakeVm(
        suggestion: DailySuggestion?,
        isSuggesting: Boolean,
        suggestError: String?
    ): LoveBrainViewModel = mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
        every { vm.suggestion } returns MutableStateFlow(suggestion)
        every { vm.isSuggesting } returns MutableStateFlow(isSuggesting)
        every { vm.currentVector } returns MutableStateFlow(emptyMap())
        every { vm.streamingTips } returns MutableStateFlow(emptyList())
        every { vm.suggestError } returns MutableStateFlow(suggestError)
        every { vm.intentConfig } returns MutableStateFlow(IntentConfig())
        every { vm.showIntentEditor } returns MutableStateFlow(false)
        every { vm.activeKb } returns MutableStateFlow(null)
        // 谈心那侧的几条流同理：一条都不留给 relaxed
        every { vm.counselingDraft } returns MutableStateFlow("")
        every { vm.counselingResult } returns MutableStateFlow<String?>(null)
        every { vm.counselingError } returns MutableStateFlow<String?>(null)
        every { vm.isCounseling } returns MutableStateFlow(false)
        every { vm.counselingStreaming } returns MutableStateFlow("")
    }

    private fun mountSuggest(vm: LoveBrainViewModel) {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) { SuggestPanel(viewModel = vm) }
        }
    }

    private fun mountCounseling(vm: LoveBrainViewModel) {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) {
                CounselingPanel(viewModel = vm, onFocusChange = {})
            }
        }
    }

    /**
     * 量"这屏上的控件点不点得中"，只看**完整落在视口里**的那些。
     *
     * 同 §40.3 那条：谈心的模板 chip 是一条 `horizontalScroll` 的行，
     * 只露半颗的 chip 其节点拿到的是**被视口裁过**的尺寸
     * （本机实测：右边那颗报 **10x48dp**、完全滚出去的报 **0x0dp**）。
     * 那不是热区不达标，是滚动容器在裁——把它们算进判决会淹掉真缺陷，
     * 而"修"它们会去把 chip 做宽，那是没坏的东西。
     * ⚠ 排除项**连同尺寸打进失败信息**，且要求留下的样本 >= 2：
     * 筛掉必须是看得见的动作，否则过滤条件就是把能抹红的橡皮。
     */
    private fun assertAllMeetFloor(what: String) {
        val all = probe.actionableTargets(rule, what)
        val (reachable, excluded) = all.partition { t ->
            t.widthDp > 0f && t.heightDp > 0f && t.leftDp >= 0f &&
                t.leftDp + t.widthDp <= 360f - 0.5f
        }
        assertTrue(
            "$what 视口内只量到 ${reachable.size} 个可交互节点（整树 ${all.size} 个）——" +
                "少了就是面板没渲染开，断言会在近乎空的上扫绿：" +
                all.joinToString { it.describe() },
            reachable.size >= 2
        )
        val offenders = reachable.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "$what 有 ${offenders.size}/${reachable.size} 个可交互节点小于 " +
                "${probe.floorDp.toInt()}dp：\n" +
                offenders.joinToString("\n") { "  " + it.describe() } +
                "\n  （另排除 ${excluded.size} 个被视口裁掉的：" +
                excluded.joinToString { it.describe() } + "）",
            offenders.isEmpty()
        )
        probe.assertAllActionableLabeled(rule, what)
    }

    @Test
    fun `the suggest panel's idle controls all meet the floor`() {
        // suggestion = null、没在生成 ⇒ 这一档画的是那颗「生成锦囊」
        mountSuggest(fakeVm(null, false, null))
        assertAllMeetFloor("锦囊面板（空闲）")
    }

    @Test
    fun `the suggest panel's error retry meets the floor`() {
        // suggestError 非空 ⇒ 这一档画的是那颗「点击重试」
        mountSuggest(fakeVm(null, false, "生成失败了：网络中断"))
        assertAllMeetFloor("锦囊面板（出错）")
    }

    @Test
    fun `the counseling panel's idle controls all meet the floor`() {
        // 草稿空、未在谈心 ⇒ 那颗「开始谈心」是禁用态；禁用的主动作也得点得中位置、说得出自己
        mountCounseling(fakeVm(null, false, null))
        assertAllMeetFloor("谈心面板（空闲）")
    }
}
