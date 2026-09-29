package com.lovebrain.app.ui.panel

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
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.SuggestTip
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
 * 持续意图那颗入口（`SuggestPanel.IntentChip`）归进 `LbChip`（`Action` 一档）之后的读数。
 *
 * ## 这一颗值得单独一棵守卫的两件事
 *
 * 它是**入口**不是**选项**——点下去开编辑器，它自己从不"在哪一格"，所以交的是
 * `Action`：`Role.Button` 而**不发** `Selected`。"开/关"这件事原来就写在自己的名字里
 * （`意图·xxx` / `意图·关`），因此这一族判的是**名字跟着状态变**，不是判 `selected`
 * 跟着状态变——两件事别混：给一颗按钮写"已选中"是把读屏引向一个不存在的事实。
 *
 * ⚠ 归并之前这条 `clickable` **一个角色都没声明**（读屏念得出「意图·…」、说不出它是按钮），
 *   归并后由组件发 `Role.Button`——那是 §6.5 :532 那一栏欠的，不是这一格顺手升级的东西。
 *
 * ## 一处**没**被这一格判住的东西，写明白
 *
 * 这一颗仍然**没有**接全局那颗 48dp 下限（实量 22dp 高，归并前后一样）：它复用的是标题行
 * 的行高，垫上去会把那一行的版式换掉，所以归并按原样交出去（`touchFloor = false`）。
 * **这一格因此故意不判尺寸**——判了就会把"这族全达标"读成一个从没成立过的事实。
 * 欠的这一笔连同有效期那一排（另一批的活，本轮没动）一起登记在账本里。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestIntentChipTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /**
     * 意图那两格读数现在住在 [IntentController] 里，面板经 `vm.intents` 读它们。
     * 替身要把控制器本身交出来：点下去那一下验的也是它，不是 ViewModel 上的同名门面。
     */
    private fun fakeIntents(
        intent: MutableStateFlow<IntentConfig>,
        showEditor: Boolean
    ): IntentController = mockk<IntentController>(relaxed = true).also {
        every { it.config } returns intent
        every { it.showEditor } returns MutableStateFlow(showEditor)
    }

    private fun fakeVm(
        intent: MutableStateFlow<IntentConfig>,
        activeKb: KnowledgeBase?,
        showEditor: Boolean,
        intents: IntentController = fakeIntents(intent, showEditor)
    ): LoveBrainViewModel = mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
        // 一条流都不留给 relaxed：带泛型的 StateFlow 不显式桩就会在 .value 上炸
        every { vm.suggestion } returns MutableStateFlow<DailySuggestion?>(null)
        every { vm.isSuggesting } returns MutableStateFlow(false)
        every { vm.currentVector } returns MutableStateFlow(emptyMap())
        every { vm.streamingTips } returns MutableStateFlow<List<SuggestTip>>(emptyList())
        every { vm.suggestError } returns MutableStateFlow<String?>(null)
        every { vm.intents } returns intents
        every { vm.activeKb } returns MutableStateFlow(activeKb)
    }

    private fun mount(vm: LoveBrainViewModel) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) { SuggestPanel(viewModel = vm) }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 标题行里那颗持续意图入口 */
    private fun intentChip(): SemanticsProbe.Target =
        probe.actionableTargets(rule, "锦囊面板·意图入口")
            .firstOrNull { it.label.startsWith("意图·") }
            ?: throw AssertionError(
                "树里没有意图那颗入口；实到：" +
                    probe.actionableTargets(rule, "锦囊面板·意图入口")
                        .joinToString { it.describe() }
            )

    /** 没有知识库时这颗压根不画——"看得见"这件事本身也要一格守着 */
    @Test
    fun `the intent entry is drawn when a knowledge base is active`() {
        val withKb = fakeVm(MutableStateFlow(IntentConfig()), KnowledgeBase(name = "kb"), false)
        mount(withKb)
        assertEquals(
            "有知识库时该画得出那颗入口", 1,
            probe.actionableTargets(rule, "锦囊面板·有库").count { it.label.startsWith("意图·") }
        )
    }

    /** 它是按钮、不播报选中，而"开/关"说在自己的名字里，且名字跟着状态走 */
    @Test
    fun `the intent entry is a button whose own name carries the state`() {
        val intent = MutableStateFlow(IntentConfig(text = "约她周末看电影", enabled = true))
        val vm = fakeVm(intent, KnowledgeBase(name = "kb"), false)
        mount(vm)

        val chip = intentChip()
        assertEquals("点下去开编辑器，那它就是按钮：" + chip.describe(), "Button", chip.role)
        assertEquals(
            "入口没有「在哪一格」这件事，不该多一槽选中：" + chip.describe(),
            null, chip.selected
        )
        assertTrue("也不该有 toggle 槽：" + chip.describe(), !chip.isToggle)
        assertEquals("开着的时候名字里带着意图本身：" + chip.describe(), "意图·约她周末看电影", chip.label)

        // 状态换了，同一颗节点念出来的话要跟着换（这才是这颗的"状态播报"）
        intent.value = IntentConfig(text = "约她周末看电影", enabled = false)
        rule.mainClock.advanceTimeBy(16L)
        val off = intentChip()
        assertEquals("关掉之后名字要说「意图·关」：" + off.describe(), "意图·关", off.label)
    }

    /**
     * 归并最怕的是"形状还在、点下去没结果了"——这一格判真的开了编辑器。
     *
     * ⚠ 点的这颗是**关着**的那一档：意图关掉时入口仍然点得动、仍然要开得编辑器，
     *   那是归并之前就在的交互。把 `enabled` 当成组件的 `enabled` 交出去（看起来更"语义化"）
     *   就会在这里红——关掉意图之后这颗从"灰着还在、点得动"变成"从树上消失/按不动"。
     */
    @Test
    fun `tapping the intent entry goes through the view model even when the intent is off`() {
        val intent = MutableStateFlow(IntentConfig(text = "周末见她", enabled = false))
        val intents = fakeIntents(intent, false)
        val vm = fakeVm(intent, KnowledgeBase(name = "kb"), false, intents)
        mount(vm)

        rule.onAllNodes(hasText("意图·关"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        verify(exactly = 1) { intents.openEditor() }
    }
}
