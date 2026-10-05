package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.IntentConfig
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
 * 持续意图那颗入口（[IntentChip]）归进 `LbChip`（`Action` 一档）之后的读数。
 *
 * ## 这一格现在挂的是谁（以及少了一格什么）
 *
 * 原来这一族是**整页挂** `SuggestPanel(viewModel)`，再从语义树里捞出这颗 chip。
 * 「今日锦囊」那一页按 第12节第1条 删掉了，chip 和它身后那扇编辑浮层留下来——PRODUCT_SPEC 把
 * 持续意图列在"保留、不许借简化删"那一栏， 要并入主动发的"有效持续意图"也靠这一扇录入。
 * 所以现在**直接挂生产那一颗 [IntentChip]**：不替被测对象准备 ViewModel、状态流、知识库，
 * 那些都不是这颗 chip 自己的合同。
 *
 * ⚠ 代价要认账：原来那一格「没有知识库时这颗压根不画」**跟着锦囊页一起没了**——
 * `activeKb != null` 那道守卫属于宿主不属于这颗 chip。宿主接线时要在宿主那一层把它判回来
 * （见交接清单），这里不自造一个假的。
 *
 * ## 这一颗值得单独一棵守卫的两件事
 *
 * 它是**入口**不是**选项**——点下去开编辑器，它自己从不"在哪一格"，所以交的是
 * [com.lovebrain.app.core.designsystem.LbChipInteraction.Action]：`Role.Button` 而**不发** `Selected`。
 * "开/关"这件事原来就写在自己的名字里（`意图·xxx` / `意图·关`），因此这一族判的是
 * **名字跟着状态变**，不是判 `selected` 跟着状态变——两件事别混：给一颗按钮写"已选中"
 * 是把读屏引向一个不存在的事实。
 *
 * ⚠ 归并之前这条 `clickable` **一个角色都没声明**（读屏念得出「意图·…」、说不出它是按钮），
 *   归并后由组件发 `Role.Button`——那是 第6节第5条 :532 那一栏欠的，不是这一格顺手升级的东西。
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

    /** 那颗持续意图入口 */
    private fun intentChip(): SemanticsProbe.Target =
        probe.actionableTargets(rule, "持续意图入口")
            .firstOrNull { it.label.startsWith("意图·") }
            ?: throw AssertionError(
                "树里没有意图那颗入口；实到：" +
                    probe.actionableTargets(rule, "持续意图入口").joinToString { it.describe() }
            )

    /**
     * 挂**生产那一颗** [IntentChip]。
     *
     * 只把这颗 chip 自己的三个入参交给它——没有 ViewModel、没有知识库、没有锦囊那四条流。
     * 用 `collectAsState` 收意图，是为了改 `intent.value` 之后同一颗节点会重 composition，
     * "名字跟着状态走"才判得动（一次 setContent 就够，Compose 测试规则不许挂第二次）。
     */
    private fun mount(intent: MutableStateFlow<IntentConfig>, taps: MutableList<Int> = mutableListOf()) {
        rule.setContent {
            val cfg by intent.collectAsState()
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                IntentChip(enabled = cfg.enabled, text = cfg.text, onClick = { taps.add(taps.size) })
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 它是按钮、不播报选中，而"开/关"说在自己的名字里，且名字跟着状态走 */
    @Test
    fun `the intent entry is a button whose own name carries the state`() {
        val intent = MutableStateFlow(IntentConfig(text = "约她周末看电影", enabled = true))
        mount(intent)

        val chip = intentChip()
        assertEquals("点下去开编辑器，那它就是按钮：" + chip.describe(), "Button", chip.role)
        assertEquals(
            "入口没有「在哪一格」这件事，不该多一槽选中：" + chip.describe(),
            null, chip.selected
        )
        assertTrue("也不该有 toggle 槽：" + chip.describe(), !chip.isToggle)
        assertEquals("开着的时候名字里带着意图本身：" + chip.describe(), "意图·约她周末看电影", chip.label)

        // 状态换了，同一颗节点念出来的话要跟着换（这才是这颗的"状态播报"）。
        // 反例：把 enabled 交成 LbChip 的选中语义 → 这里多出一槽 Selected；
        //       或字面写死成"持续意图"不再跟 enabled 走 → 这一句红。
        intent.value = IntentConfig(text = "约她周末看电影", enabled = false)
        rule.mainClock.advanceTimeBy(16L)
        val off = intentChip()
        assertEquals("关掉之后名字要说「意图·关」：" + off.describe(), "意图·关", off.label)
    }

    /**
     * 归并最怕的是"形状还在、点下去没结果了"——这一格判真的按得动、真的把一次点击交出去。
     *
     * ⚠ 点的这颗是**关着**的那一档：意图关掉时入口仍然点得动、仍然要开得编辑器，
     *   那是归并之前就在的交互。把 `enabled` 当成组件的 `enabled` 交出去（看起来更"语义化"）
     *   就会在这里红——关掉意图之后这颗从"灰着还在、点得动"变成"从树上消失/按不动"。
     *   另一个反例：onClick 被接成"只有开着才回调"，这一格同样红。
     */
    @Test
    fun `tapping the intent entry hands off exactly one click even when the intent is off`() {
        val taps = mutableListOf<Int>()
        mount(MutableStateFlow(IntentConfig(text = "周末见她", enabled = false)), taps)

        rule.onNodeWithText("意图·关").performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("关着的那一档也必须点得动，一次点击就是一次点击", listOf(0), taps)
    }

    /**
     * 意图正文超过 8 个字时那颗入口收着念——这一格守的是"标题行不被一个字撑破"。
     *
     * 反例：去掉 `take(8)`（长意图把整行挤成换行块）。
     */
    @Test
    fun `a long intent is shortened in the entry name`() {
        mount(MutableStateFlow(IntentConfig(text = "约她周末去看那部新上映的电影", enabled = true)))
        assertEquals("八个字之后收口并补省略号", "意图·约她周末去看那部…", intentChip().label)
    }

    /**
     * 没超长的意图**不该**带省略号——与上一格是一对，只留上一格的话"永远拼省略号"照样绿。
     *
     * 反例：`"意图·${text.take(8)}…"` 少了那个 `if (text.length > 8)` 条件。
     */
    @Test
    fun `a short intent is not padded with an ellipsis`() {
        mount(MutableStateFlow(IntentConfig(text = "周末见她", enabled = true)))
        assertEquals("没超长的意图不该带省略号", "意图·周末见她", intentChip().label)
    }
}
