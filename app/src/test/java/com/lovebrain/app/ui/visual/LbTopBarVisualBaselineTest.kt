package com.lovebrain.app.ui.visual

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.Primary
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
 * `LbTopBar` 的截图基线：`LbTopBarLevel` 两档各一张，再加"页头那一族"的完整版式。
 *
 * 这一颗的回归风险正好是它自己 KDoc 里写的那两条，而两条都是**听不见看不见**的那种：
 * - 标题字号是一个二选一的枚举（首页 `headlineLarge` / 二级页 `titleLarge`）。
 *   有人把某一页的档挪错，语义树上标题还是同一个名字，只有版式会变。
 * - `showsDivider` 是 `ScreenPage` 那一族的规格，首页没有。分割线的有无在树上根本不存在。
 *
 * 三格都对着真实调用点取参数：
 * - `identity…` = `HomeScreen:128`（标题 + 副标题 + 尾部那颗关于入口，无返回、无分割线）；
 * - `pageWithBack…` = `AboutScreen:58` / `UsageDetailScreen:43`（页名 + 返回）；
 * - `pageWithDivider…` = `ScreenHeader:33`（`level=Page` + 返回 + 分割线）叠
 *   `FeedbackCasesScreen:159` 的副标题与尾部动作。
 * 尾部那一格交的是页面自己的槽内容（生产是 `HomeAboutEntry` / `ExportAction`），
 * 这里用一个同样占 48dp 的槽位词顶上：基线钉的是"槽在不在标题那一行右端、标题还剩多宽"，
 * 不是替某一页拍它自己的入口。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbTopBarVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(
        level: LbTopBarLevel,
        title: String,
        subtitle: String?,
        onBack: (() -> Unit)?,
        showsDivider: Boolean,
        withTrailing: Boolean
    ) {
        rule.setContent {
            UiMatrix(360, heightDp = 120).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    LbTopBar(
                        title = title,
                        level = level,
                        subtitle = subtitle,
                        onBack = onBack,
                        showsDivider = showsDivider,
                        trailing = if (withTrailing) {
                            {
                                Text(
                                    text = "TRAILING",
                                    style = AppTypography.labelLarge,
                                    color = Primary
                                )
                            }
                        } else null,
                        modifier = Modifier
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 首页那一档：产品身份 + 价值说明 + 尾部入口 */
    @Test
    fun identityHasACommittedVisualBaseline() = shot(
        LbTopBarLevel.Identity, "TOPBAR_IDENTITY", "identity subtitle", null, false, true
    )

    /** 二级页那一档：页名 + 返回（关于页 / 使用概览页的形状） */
    @Test
    fun pageWithBackHasACommittedVisualBaseline() = shot(
        LbTopBarLevel.Page, "TOPBAR_PAGE", null, {}, false, false
    )

    /** 二级页 + 副标题 + 分割线 + 尾部动作：`ScreenHeader` 那一族的完整规格 */
    @Test
    fun pageWithDividerSubtitleAndTrailingHasACommittedVisualBaseline() = shot(
        LbTopBarLevel.Page, "TOPBAR_PAGE_DETAIL", "3 items", {}, true, true
    )
}
