package com.lovebrain.app.ui.visual

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbActionCard
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
 * `LbActionCard` 的截图基线（§6.5 :538 那一行要求的"baseline 变更必须人工 review"）。
 *
 * 首页那两块入口卡（知识库 / 反馈案例）是全站唯一用它的地方，参数口径照
 * `HomeScreen:145` / `HomeScreen:154`：`iconRes` 走 `R.drawable.ic_feature_book`
 * 与 `ic_feature_feedback`，整卡在 `Row` 里 `weight(1f)`（这里用 `fillMaxWidth()` 顶同一档）。
 *
 * 两格：
 * - 常规一张：图标容器（48dp 的 `PrimaryLight` 方块 + 22dp 图标）、右侧箭头、
 *   标题与说明之间那 `Spacing.xs`、卡底与描边——这些在语义树上全读不出来，
 *   树上只有那张 `role=Button` 的卡。
 * - 长说明一张：`subtitle` 这一行是 `maxLines = 1`，而生产交进来的长度没有上限
 *   （「点踩记录与导出」旁边那行将来会是用户自填的供应商名）。截断点漂没漂，
 *   只有像素知道。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbActionCardVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(title: String, subtitle: String) {
        rule.setContent {
            UiMatrix(360, heightDp = 200).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbActionCard(
                        modifier = Modifier.fillMaxWidth(),
                        iconRes = R.drawable.ic_feature_book,
                        title = title,
                        subtitle = subtitle,
                        onClick = {}
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun defaultHasACommittedVisualBaseline() = shot("ACTION_CARD_KB", "her own memory")

    @Test
    fun longSubtitleHasACommittedVisualBaseline() =
        shot(
            "ACTION_CARD_LONG_TITLE",
            "a subtitle long enough to be truncated on a narrow phone row"
        )
}
