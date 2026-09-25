package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 表里的 `LbPrimaryButton`：四态是不是**真的**一旋钮，以及四态占不占同一个盒子。
 *
 * 替代的旧实现把状态摊在 `mode` + `enabled` 两个参数上，那种形状有两个死角：
 * `STOP + enabled=false` 类型上合法却没人定义过；`Disabled` 又只是 NORMAL 的一个 if。
 * 这一组用例钉的是**换成的那一颗旋钮**在语义树上的表现，全走 `boundsInRoot` 与
 * 语义属性，不看源码里的数字（P1-02 点名的就是这个区别）。
 *
 * 第二件事是 §6.4 那句「模式切换只改变内容区，**不移动主要输入和主操作按钮**」里
 * 属于这颗按钮的半边：四态的 bounds 必须逐像素相同。
 * 原来这句话没人能证——生成一次要真在设备上跑一次。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbPrimaryButtonStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private val state: MutableState<LbButtonState> = mutableStateOf(LbButtonState.Idle)
    private var clicks = 0

    /**
     * 一次 setContent 挂四态：本仓库的仪器要求每个测试只挂一次，换态靠 hoisted 状态。
     *
     * `fillMaxWidth()` 是**故意跟着**的：这颗按钮自己不铺满，它按内容宽。
     * 实到证据（同一台仪器、不给宽度约束时量到的）：Idle=63dp、Loading=120dp、
     * Disabled=102dp、Stop=71dp——换态就把主操作横向撑来撑去。所以 §6.4 那句
     * "模式切换不移动主操作按钮"是**调用方 + 组件**的联合性质：槽位宽度由调用方钉死，
     * 四态才谈得上"同一个盒子"。生产那五个分支全都带 `fillMaxWidth()` 或 `weight(1f)`，
     * 这里按同一种形状测。
     */
    private fun mountPrimary() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbPrimaryButton(
                    state = state.value,
                    label = labelFor(state.value),
                    onClick = { clicks++ },
                    modifier = Modifier.fillMaxWidth().testTag("PRIMARY_SLOT")
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun labelFor(s: LbButtonState) = "LBL_" + s.name.uppercase()

    private fun show(next: LbButtonState) {
        rule.runOnIdle { state.value = next }
        // Loading 带无限脉冲动画：自动时钟下 waitForIdle 永远等不到空闲，手动推几帧
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
    }

    private fun targets() = probe.actionableTargets(rule, "主动作")

    @Test
    fun `the four states share one box so switching state cannot move the primary action`() {
        mountPrimary()
        val seen = LinkedHashMap<String, String>()
        LbButtonState.values().forEach { s ->
            show(s)
            val t = targets().single()
            seen[s.name] = "%.1f/%.1f/%.1f/%.1f".format(t.leftDp, t.topDp, t.widthDp, t.heightDp)
        }
        assertEquals(
            "四态占的盒子必须完全相同（换态不许把主操作挪走）：" + seen.entries.joinToString { "${it.key}=${it.value}" },
            1, seen.values.distinct().size
        )
        // 反空跑：盒子本身也得真量到东西，不是四个 0/0/0/0 相同
        val t = targets().single()
        assertTrue("宽度应当铺满 360dp 槽位，实到 ${t.widthDp}", t.widthDp > 300f)
    }

    /** §6.5 :531 ——四态每一态的可点击盒子都得过 48dp 下限 */
    @Test
    fun `every state meets the touch floor`() {
        mountPrimary()
        LbButtonState.values().forEach { s ->
            show(s)
            val got = probe.assertAllActionableMeetTouchFloor(rule, "主动作·${s.name}")
            assertEquals("${s.name} 应当只有一颗主动作", 1, got.size)
            assertEquals("${s.name} 的高度应当正好是下限", 48f, got.single().heightDp, 0.6f)
            // ⚠ 这一条是**搬首页那颗主按钮时量出来的缺陷**补进来的：
            // Material `Button` 自带 role=Button，而本组件是手画 Box + clickable，
            // 四态**全都没声明角色**（`role=无`）。搬过来那天语义树就从 Button 掉回无——
            // "换成设计系统的组件"这一步自己引入了 §6.5 :532 的回归，
            // 只有把两边的性质都量一遍才会发现（读代码读不出来，Material 那侧的角色不在源码里）。
            assertEquals(
                "${s.name} 必须在语义树里说得出自己是按钮：" + got.single().describe(),
                "Button", got.single().role
            )
        }
    }

    /**
     * 短标签那颗也得是**见方**的热区——不靠调用方记得加 `fillMaxWidth()`。
     *
     * 起因是一条实测红：`KbEditScreen` 编辑态那颗「保存」搬进本组件之后，
     * 语义树量到 **33x48dp**。组件原来只写 `.height(48)`，宽度按内容走，
     * 于是英文短标签（"Save"）自己就不够 48——:596 那句"无小于 48dp 的热区"
     * 判的是两条边，不是只判高度。
     * 这与 `LbTextAction` 的来历同一课（它第一版也只垫高度，被自家测试量出 40x48dp），
     * 差别在于这次把它写回**组件本身**：四态共用同一个下限，调用方拿不到"只设高度"的旋钮。
     */
    @Test
    fun `a short label still leaves a square hot zone in every state`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                // 故意**不给**宽度约束：这一格判的就是组件自己的下限
                LbPrimaryButton(
                    state = state.value,
                    label = "Save",
                    onClick = { clicks++ }
                )
            }
        }
        LbButtonState.values().forEach { st ->
            show(st)
            val t = targets().single()
            assertTrue(
                "${st.name} 那一态短标签的热区不到 48dp 见方：" + t.describe(),
                !t.tooSmall(probe.floorDp)
            )
        }
    }
    /** 合同第 1 行的那半个：禁用是"灰着不能点"，不是消失 */
    @Test
    fun `disabled stays in place and announces itself disabled`() {
        mountPrimary()
        show(LbButtonState.Disabled)
        val t = targets().single()
        assertEquals("禁用态还得画得出来", labelFor(LbButtonState.Disabled), t.label)
        assertTrue("必须带 Disabled 语义，读屏才知道念\"不可用\"：" + t.describe(), t.disabled)

        show(LbButtonState.Idle)
        assertTrue("同一条链上的 Idle 不该带 Disabled：" + targets().single().describe(),
            !targets().single().disabled)
    }

    /** 三态点下去都走那颗回调；Disabled 点下去什么都不该发生 */
    @Test
    fun `tapping works in the three live states and does nothing when disabled`() {
        rule.mainClock.autoAdvance = false
        mountPrimary()
        listOf(LbButtonState.Idle, LbButtonState.Stop, LbButtonState.Loading).forEach { s ->
            show(s)
            val before = clicks
            rule.onNodeWithTag("PRIMARY_SLOT").performClick()
            repeat(3) { rule.mainClock.advanceTimeByFrame() }
            assertEquals("$s 点一次应当恰好触发一次回调", before + 1, clicks)
        }
        show(LbButtonState.Disabled)
        rule.onNodeWithTag("PRIMARY_SLOT").performClick()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        assertEquals("Disabled 点下去不许触发回调，实到 $clicks 次", 3, clicks)
    }

    /** 停止锚点只属于"生成中"那一态：别的态冒出可停止的节点，自动化就会点错 */
    @Test
    fun `only the loading state carries the stop anchor`() {
        mountPrimary()
        LbButtonState.values().forEach { s ->
            show(s)
            val found = rule.onAllNodesWithTag(LbTags.PRIMARY_STOP, useUnmergedTree = true)
                .fetchSemanticsNodes().size
            val want = if (s == LbButtonState.Loading) 1 else 0
            assertEquals("$s 的停止锚点数", want, found)
        }
    }
}
