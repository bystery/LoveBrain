package com.lovebrain.app.ui.visual

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.designsystem.TextPrimary
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
 * `LbAsyncState` 的截图基线：一个目的地的四格状态各一张（第6节第3条 那四类状态的唯一出口）。
 *
 * 为什么四格都要像素：这一颗是"每页自己画一个居中 Text"那件事的替代品，
 * 而它换掉的恰恰是**看不见的东西**——Loading 那格spinner 的色与尺寸、Empty/Error 两格
 * 是否真的居中占满、Content 那一格**不该再加任何容器**。第四格特别容易被人顺手加一层
 * `padding` 或 `Box`：语义树上内容还是那一串节点，读不出来"多套了一层"，像素读得出来。
 *
 * 四格的生产口径：`KnowledgeBaseActivity:310`（Error+retry / Empty+newKb / Content 列表）、
 * `CaptureAppsScreen:162`（Error+retry / Empty 无动作 / Content）、`ProviderSection:206`。
 * `Loading` 是那三页判据里 Loading > Error > Empty > Content 的第一格。
 *
 * 画布给的是 240dp 高而不是默认那一档 1000dp：这一颗内部是 `fillMaxSize()`，
 * 拍整屏会把四格都拍成大半张空白，比对时看不见回归的那半张。内容照样占满给定的那一格。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbAsyncStateVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(state: ScreenState<String>) {
        rule.setContent {
            UiMatrix(360, heightDp = 240).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbAsyncState(state = state, modifier = Modifier.fillMaxWidth()) { value ->
                        Text(text = value, style = AppTypography.titleMedium, color = TextPrimary)
                    }
                }
            }
        }
        // Loading 那一格是不定进度环：手动推到定帧，免得同一格每次跑差几帧
        rule.mainClock.advanceTimeBy(16L)
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun loadingHasACommittedVisualBaseline() = shot(ScreenState.Loading)

    @Test
    fun emptyHasACommittedVisualBaseline() = shot(ScreenState.Empty("ASYNC_STATE_EMPTY"))

    @Test
    fun errorHasACommittedVisualBaseline() =
        shot(ScreenState.Error("ASYNC_STATE_FAILED", ScreenAction("RETRY") {}))

    @Test
    fun contentHasACommittedVisualBaseline() = shot(ScreenState.Content("ASYNC_STATE_CONTENT"))
}
