package com.lovebrain.app.ui.visual

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbEmptyState
import com.lovebrain.app.core.designsystem.LbStateTone
import com.lovebrain.app.core.designsystem.ScreenAction
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
 * `LbEmptyState` 的截图基线。三格，全是生产实际会画出来的组合，没有凑数档：
 *
 * - **中性 + 无动作**：`CaptureAppsScreen` 的 `ScreenState.Empty(nothingToAuthorize)`
 *   与反馈案例页那句 `feedback_empty_hint`——空的时候就是**一句话**，不给出口。
 * - **中性 + 动作**：`KnowledgeBaseActivity` 的空库引导（`Empty(emptyMessage, newKb)`）。
 * - **错误 + 重试**：`CaptureAppsScreen` 的 `Error(scanFailed, retry)`、
 *   `KnowledgeBaseActivity` 的 `Error(errorMessage, retry)`——错误必须带自救出口，
 *   而这一格钉的正是"错误那句话真的说得更重"（`Error` 色 + 那颗重试）。
 *
 * `Error` + 无动作这一档**不测**：`ScreenState.Error` 的契约就写着"没有重试的错误态
 * 等于把用户关死"，画出来是一堵墙，不是产品要给人看的东西。
 *
 * 为什么值得占三张图：说明文字的色档、动作与说明之间那 `Spacing.md` 的间距、
 * 以及"动作是一处操作不是一行文字"这三件事，语义树全都读不出来——树上只有一句
 * contentDescription 和一个 tag。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbEmptyStateVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(message: String, tone: LbStateTone, action: ScreenAction?) {
        rule.setContent {
            UiMatrix(360, heightDp = 240).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbEmptyState(
                        message = message,
                        action = action,
                        tone = tone,
                        modifier = Modifier
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun neutralWithoutActionHasACommittedVisualBaseline() =
        shot("EMPTY_STATE_NO_RECORDS", LbStateTone.Neutral, null)

    @Test
    fun neutralWithActionHasACommittedVisualBaseline() =
        shot("EMPTY_STATE_NOTHING_YET", LbStateTone.Neutral, ScreenAction("ADD") {})

    @Test
    fun errorWithRetryHasACommittedVisualBaseline() =
        shot("EMPTY_STATE_LOAD_FAILED", LbStateTone.Error, ScreenAction("RETRY") {})
}
