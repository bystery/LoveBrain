package com.lovebrain.app.ui.visual

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.core.designsystem.LbMetricDensity
import com.lovebrain.app.core.designsystem.LbMetricGrid
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `LbMetricGrid` 的截图基线：第6节第1条 表第 7 行那一颗，四张（两张 Card 档、一张 Inline 档、一张单格）。
 *
 * 为什么这颗特别需要像素：它的全部职责就是**排布**——数值在上、标签在下、格与格等分、
 * 强调那一格换 Primary。这些在语义树上一个都读不出来：树上只有两条文本和一个 tag，
 * 看不见谁在谁上面、看不见三格是不是等分、也看不见 `highlight` 到底改了谁的颜色。
 * 把 `Column` 换成 `Row`、把 `SpaceEvenly` 换成 `Start`、把 highlight 的分支接反，
 * 语义树那几格（`PanelUsageMetricSemanticsTest`）一个字都不会红——像素才会。
 *
 * 两档容器都必须在：面板顶部那条挂的是 `Inline`（10sp 一行、标签在前数值在后），
 * 首页那张卡挂的是 `Card`。这一颗组件的契约就是"同一份数据、两档容器"，
 * 只钉一档的话，另一档被谁改坏都没人看见。
 *
 * 标签一律 ASCII：本机 JVM 的 Robolectric 默认 locale 是英文，而基线要跨机器同图
 * （上一批字节基线栽在默认时区那件事之后，这一族的图里不留任何环境敏感的量）。
 * 这里画的也不是页面词表——标签由调用方给，这一颗组件不认识任何一页的用词。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbMetricGridVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private val three = listOf(
        LbMetric(label = "METRIC_TODAY", value = "128"),
        LbMetric(label = "METRIC_COST", value = "CNY 3.40"),
        LbMetric(label = "METRIC_ADOPT", value = "62%", highlight = true)
    )

    private fun shot(metrics: List<LbMetric>, density: LbMetricDensity, onClick: (() -> Unit)?) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbMetricGrid(metrics = metrics, onClick = onClick, density = density)
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun cardTierWithThreeCellsAndClickHasACommittedVisualBaseline() {
        shot(three, LbMetricDensity.Card, {})
    }

    /**
     * 同一份数据、同一档容器，只把"可点"拿掉。
     *
     * 这一张存在理由：可点那一档在容器上挂 `clickable(role = Button)`，
     * 而 `indication = null`——**它不该画出任何波纹或底色变化**。
     * 哪天有人给这档加上一条按压底色，两张图一比就分叉，而这在语义树上只是多了一个 role。
     */
    @Test
    fun cardTierWithoutClickHasACommittedVisualBaseline() {
        shot(three, LbMetricDensity.Card, null)
    }

    @Test
    fun inlineTierStripHasACommittedVisualBaseline() {
        shot(three, LbMetricDensity.Inline, null)
    }

    /**
     * 单格：三格等分那一档只来一格时的样子。
     *
     * 生产里有这个形状（面板那条"首字"那一格只在有耗时时存在，缺位时整条会短一格），
     * 等分与居中在一格时最容易画歪（`weight(1f)` 让一格独占整行宽度）。
     */
    @Test
    fun singleCellHasACommittedVisualBaseline() {
        shot(listOf(LbMetric(label = "METRIC_ONLY", value = "1")), LbMetricDensity.Card, null)
    }
}
