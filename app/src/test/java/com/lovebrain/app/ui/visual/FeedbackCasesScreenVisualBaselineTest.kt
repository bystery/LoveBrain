package com.lovebrain.app.ui.visual

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.ui.feedback.FeedbackCasesScreen
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 反馈案例页（`FeedbackCasesScreen`）的屏幕级截图基线。
 *
 * 这一页把 §6.3 那四态收进 `LbAsyncState`（Loading/Error/Empty 三档交共用状态件），
 * Content 那一档下面才是自家的卡片列表。组件级基线钉的是 `LbAsyncState` 那颗组件本身，
 * 这一格钉的是**那一颗摆进这一页、下面再叠一层 `Card` 列表**之后的样子——
 * 筛选芯片那一行、卡内多行文字的层级、展开后那一摞 metadata，这些在语义树上读不出来，
 * 树上只有一串 `role=Button` 与几行文字。热区与角色由 `FeedbackCasesSemanticsTest` 钉，
 * 像素由这一格钉。
 *
 * 只摆「Content·两条案例」那一档：两条都带类别与原因，卡首行那句
 * 「【类别】 原因」才是用户真会听到的那句（夹具照 `FeedbackCasesSemanticsTest` 那两条）。
 * Empty 那一档长得像 `LbAsyncStateVisualBaselineTest > empty…` 那张，不另开屏幕级基线重复它。
 *
 * 挂载方式逐字照 `FeedbackCasesSemanticsTest`：四条流桩住 + `ExportState.Idle`，
 * 宽槽 600dp（360 那一档装不下这族芯片、会横排裁切，那份裁切由语义测试那一侧钉）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackCasesScreenVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private val understandingCase = FeedbackCase(
        caseId = "c_understand",
        schemeIdentityKey = "STYLE:B",
        candidateReply = "那你想我怎么做",
        categories = listOf(FeedbackCategory.UNDERSTANDING_ERROR),
        reasons = listOf("角色错")
    )

    private val expressionCase = FeedbackCase(
        caseId = "c_expression",
        schemeIdentityKey = "DIRECTION:F",
        candidateReply = "我今天有点累",
        categories = listOf(FeedbackCategory.EXPRESSION_DISLIKE),
        reasons = listOf("太油")
    )

    private fun mount(cases: List<FeedbackCase>) {
        val vm = mockk<SetupViewModel>(relaxed = true).also {
            every { it.feedbackCases } returns MutableStateFlow(cases)
            every { it.feedbackLoading } returns MutableStateFlow(false)
            every { it.feedbackError } returns MutableStateFlow<String?>(null)
            every { it.exportState } returns
                MutableStateFlow<SetupViewModel.ExportState>(SetupViewModel.ExportState.Idle)
        }
        rule.setContent {
            UiMatrix(600, heightDp = 1000).RenderIn(LocalDensity.current.density) {
                captureRoboImage {
                    FeedbackCasesScreen(viewModel = vm, onBack = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** Content·两条案例：每张卡首行那句「【类别】 原因」在像素里完整出现。 */
    @Test
    fun twoCasesHasACommittedVisualBaseline() =
        mount(listOf(understandingCase, expressionCase))
}
