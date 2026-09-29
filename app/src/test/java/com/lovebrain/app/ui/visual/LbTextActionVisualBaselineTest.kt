package com.lovebrain.app.ui.visual

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionTone
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
 * `LbTextAction` 的截图基线（§6.5 :538 那一行要求的"baseline 变更必须人工 review"）。
 *
 * 为什么这颗要单独有一格：它是全站"文字动作"唯一的主人——`LbEmptyState` 的重试、
 * 首次引导的「跳过」、结果区那颗 `panel_retry_tap` 都从这里走。它的两档语气
 * （[LbTextActionTone.Accent] / [LbTextActionTone.Muted]）**各自带着字号一起走**
 * （`labelLarge`+Primary 与 `labelMedium`+TextHint），而这两对搭配在语义树上读不出来：
 * 树上只看得到"有个可点节点"，看不到"弱化那一档到底有多弱"。颜色或字号被谁顺手改一档，
 * 只有像素会说谎说不了。
 *
 * 标签一律用大写 ASCII（与 `LbPrimaryButtonVisualBaselineTest` 同一口径）：
 * 这一格钉的是**版式**，不是某个 locale 的字形，基线要能在开发机与 CI 上拍到同一张图。
 *
 * 第三格 `shortLabel…` 不是凑数：热区下限是**见方**两条轴，短标签那一档以前只垫高度，
 * 「重试」量出过 40x48dp。这一格的存在就是为了"哪天有人把宽度那一条删掉"时像素会变。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbTextActionVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(label: String, tone: LbTextActionTone) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbTextAction(
                        label = label,
                        onClick = {},
                        tone = tone,
                        modifier = Modifier
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 生产口径：`ResultArea` 那颗 `panel_retry_tap`（引导动作那一档） */
    @Test
    fun accentHasACommittedVisualBaseline() = shot("RETRY", LbTextActionTone.Accent)

    /** 生产口径：`OnboardingFlow` 那颗 `onboarding_skip`（弱化那一档） */
    @Test
    fun mutedHasACommittedVisualBaseline() = shot("SKIP", LbTextActionTone.Muted)

    /** 两字母标签：这一档显形的是"见方"下限的第二条轴 */
    @Test
    fun shortLabelHasACommittedVisualBaseline() = shot("OK", LbTextActionTone.Accent)
}
