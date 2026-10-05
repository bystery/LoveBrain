package com.lovebrain.app.ui.visual

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbSection
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
 * `LbSection` 的截图基线（第6节第5条 :538「screenshot baseline 变更必须人工 review」）。
 *
 * 这颗组件只有一个输入：标题串。它的全部风险都不在"有多少档"，而在**被抄**：
 * 首页三段（快捷功能 / 服务设置 / 使用概览）都从这一处取标题样式，
 * 哪天有人在某页上面再盖一层 `fontSize` 或者把 `start` 那颗内边距改掉，
 * 语义树里读出来的还是同一个 tag、同一段文字，只有像素会红。
 *
 * 折叠那一档（`LbSectionFold`）此刻还不在这颗组件上，所以这里**只有常驻档**一格；
 * 等折叠档进来，它的展开/收起两态各补一张，不在这里预先假装覆盖。
 *
 * 标签用 ASCII：这一格钉的是版式，不钉 locale 的字形。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbSectionVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(title: String) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                captureRoboImage { LbSection(title = title) }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 生产口径：`HomeScreen` 的 `LbSection("快捷功能")` 那一类（首页三段全是这一档） */
    @Test
    fun sectionTitleHasACommittedVisualBaseline() = shot("SECTION_SHORTCUTS")
}
