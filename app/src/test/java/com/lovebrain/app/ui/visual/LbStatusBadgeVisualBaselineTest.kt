package com.lovebrain.app.ui.visual

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbStatus
import com.lovebrain.app.core.designsystem.LbStatusBadge
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
 * `LbStatusBadge` 的截图基线：五档枚举各一张（第6节第5条 :538 要求 baseline 变更走人工 review）。
 *
 * 为什么这颗特别需要像素：它是全站"状态的颜色和文案只在这里有一份"那一处，
 * 配方是**状态色 15% 透明底 + 状态色字**。这两半在语义树上都读不出来——
 * 树上只有 `contentDescription`（念得出"Running"）和一个 tag，看不见底色配比，
 * 也看不见 `WindowMissing` 与另外三档灰到底差在哪。把 `0.15f` 改成 `0.5f`、
 * 或者把 `Hidden` 那档从 Neutral300 挪成 Success，语义树测试一个字都不会红。
 *
 * 五档都是生产真会画的：`AssistantStatusCard` 交的是 `status.badge`，
 * 而 `advisorStatus` 那侧五种状态各有生产者（含账本里点名的 `WindowMissing`）。
 *
 * 胶囊上的文字来自 `R.string.status_*`。本项目已收成单一中文资源（删掉了
 * `values-en`），所以本机 JVM 的 Robolectric 默认 locale（en-US）现在回落到
 * 默认的 `values/strings.xml`，五张图里是中文词。删 `values-en` 后基线图片需重录。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbStatusBadgeVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(status: LbStatus) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                captureRoboImage { LbStatusBadge(status = status) }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun runningHasACommittedVisualBaseline() = shot(LbStatus.Running)

    @Test
    fun hiddenHasACommittedVisualBaseline() = shot(LbStatus.Hidden)

    @Test
    fun offHasACommittedVisualBaseline() = shot(LbStatus.Off)

    @Test
    fun noPermissionHasACommittedVisualBaseline() = shot(LbStatus.NoPermission)

    @Test
    fun windowMissingHasACommittedVisualBaseline() = shot(LbStatus.WindowMissing)
}
