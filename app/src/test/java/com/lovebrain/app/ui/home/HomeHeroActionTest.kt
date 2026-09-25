package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbStatus
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
 * §6.1 :479「`LbPrimaryButton` = 页面唯一主动作」与 :490「不许只在一个页面看起来不一样」——
 * 这一格查的是**上一把尺漏掉的那一族**。
 *
 * :490 那份清单的锚点一直是"Modifier 链上的 `.background(品牌色)`"，而 Material 组件
 * 是从**另一扇门**涂同一层底的：`Button(colors = ButtonDefaults.buttonColors(containerColor = Primary))`。
 * 本机实扫（`_temp/scan_material_button.py`）：`Button(` 共 7 处，其中涂品牌色 5 处，
 * **一处没进过清单**——最要名的那处就是首页那颗唯一主按钮（`HomeComponents:227`）。
 *
 * 所以这一格先量再判：量的是"这颗按钮现在到底合不合格"，
 * 判的是"它表达的语义与 `LbPrimaryButton` 是不是同一件事"。
 * ⚠ 如果量出来本来就合格，那"搬"的理由就只剩语义归属——**不能拿"修了缺陷"当战果写**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeHeroActionTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun mount(matrix: UiMatrix = UiMatrix(360)) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                AssistantStatusCard(
                    status = AdvisorStatus(
                        badge = LbStatus.Running,
                        descriptionRes = R.string.home_desc_running,
                        buttonRes = R.string.home_btn_open,
                        intent = AdvisorIntent.OpenPanel
                    ),
                    onButtonClick = {},
                    // 不给右上角那颗隐藏图标：这一格要的是"整屏只剩唯一主动作一颗可点"，
                    // 那样"唯一"这件事才是量出来的，不是靠数源码标签数出来的。
                    onHideClick = null
                )
            }
        }
    }

    @Test
    fun `the home page has exactly one actionable thing and it is the primary action`() {
        mount()
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(
            "状态卡（没给隐藏图标时）应该只有唯一主动作这一颗可点，实到 ${nodes.size} 颗：" +
                probe.actionableTargets(rule, "首页状态卡").joinToString { it.describe() },
            1, nodes.size
        )
        val hero = probe.assertAllActionableMeetTouchFloor(rule, "首页状态卡").single()
        // 名字**不写死中文**：本机 Robolectric 默认 locale 是 en，那颗按钮的串走的是
        // `R.string.home_btn_open`（values=「打开军师」、values-en="Open advisor"）。
        // 第一版我照源码里那两个字断言，红成 "expected <[打开军师]> but was <[Open advisor]>"——
        // 那不是生产坏了，是我把文案抄进断言（这已经是本轮第二次撞"抄来的字符串"，
        // 前一次是坑表里那条"写死的中文与资源不一致"）。判"有没有名字"要判**非空**，
        // 判"是哪个名字"要跟宿主当前真正会渲染的那一份比。
        val expectedLabel = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.home_btn_open)
        assertTrue(
            "那颗主按钮必须自己说得出名字（实到 \"" + hero.label + "\"）：" + hero.describe(),
            hero.label.isNotBlank()
        )
        assertEquals("名字就是资源里那句，不是别处抄的", expectedLabel, hero.label)
        assertEquals(
            "主动作要有按钮角色：" + hero.describe(), "Button", hero.role
        )
        assertTrue(
            "唯一主动作不许是禁用态——禁用的话用户在这屏没有任何出口：" + hero.describe(),
            !hero.disabled
        )
    }

    /**
     * 那颗主动作的几何**不能随字档塌**。
     *
     * 它原来写死 `.height(48.dp)`，而 `LbPrimaryButton` 的高度来自
     * [com.lovebrain.app.core.designsystem.LB_PRIMARY_MIN_HEIGHT_DP]。
     * 数一样、来源不一样：这一格判的是"2.0 字 + 最窄屏"那一档还够不够，
     * 而不是去源码里比两个常量写得像不像。
     */
    @Test
    fun `the primary action keeps its floor at the worst cell of the matrix`() {
        // 一个用例只能 setContent 一次，所以这里跑矩阵里最坏那一档（最窄 + 2 倍字）。
        mount(UiMatrix(320, fontScale = 2.0f))
        val hero = probe.assertAllActionableMeetTouchFloor(rule, "首页状态卡", "（320dp + 2 倍字）").single()
        assertTrue(
            "字放大之后文字撑开了高度是好事，但**不许缩**：" + hero.describe(),
            hero.heightDp >= probe.floorDp
        )
    }
}
