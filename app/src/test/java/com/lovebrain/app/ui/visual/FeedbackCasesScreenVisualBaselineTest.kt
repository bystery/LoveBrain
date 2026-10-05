package com.lovebrain.app.ui.visual

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.LbAsyncTags
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 反馈案例页（`FeedbackCasesScreen`）的屏幕级截图基线。
 *
 * 这一页把 第6节第3条 那四态收进 `LbAsyncState`（Loading/Error/Empty 三档交共用状态件），
 * Content 那一档下面才是自家的卡片列表。组件级基线钉的是 `LbAsyncState` 那颗组件本身，
 * 这一格钉的是**那一颗摆进这一页、下面再叠一层 `Card` 列表**之后的样子——
 * 筛选芯片那一行、卡内多行文字的层级、展开后那一摞 metadata，这些在语义树上读不出来，
 * 树上只有一串 `role=Button` 与几行文字。热区与角色由 `FeedbackCasesSemanticsTest` 钉，
 * 像素由这一格钉。
 *
 * 只摆「Content·两条案例」那一档：折叠的卡片就是正文 + 时间两行（`FeedbackCasesScreen.kt` 文件头
 * 那条 ⚠ 与 `FeedbackCasesSemanticsTest` 的 「the collapsed card shows the reply and the time and
 * nothing else」一起钉死了这件事——「【类别】 原因」那一行诊断字段已从折叠态删走，类别/原因只在展开里出现）。
 * 夹具照 `FeedbackCasesSemanticsTest` 那两条。
 * Empty 那一档长得像 `LbAsyncStateVisualBaselineTest > empty…` 那张，不另开屏幕级基线重复它。
 *
 * 挂载方式逐字照 `FeedbackCasesSemanticsTest`：四条流桩住 + `ExportState.Idle`，
 * 宽槽 600dp（360 那一档装不下这族芯片、会横排裁切，那份裁切由语义测试那一侧钉）。
 *
 * ⚠ **拍法与上一版不同，是判据逼出来的**：`captureRoboImage { content }` 那个 composable
 * 形态会把 content 装进**另一颗 `RoborazziTransparentActivity` 的 composition**（本机实量：
 * 这样拍时 `rule.onRoot()` 的语义树 `children=0`，屏幕上却照样有字）——那条通道下
 * "录制前的状态断言"读的是**没被拍的那一棵**，判据就是假的。所以这里先 `setContent`
 * → `waitForIdle` → 判状态 → `rule.onRoot().captureRoboImage()`，断言与像素出自同一棵
 * 已落定的树；落盘文件名仍走 roborazzi 默认命名（`<类名>.<方法名>.png`，与其余 42 张同一规则）。
 *
 * `zh-rCN` 固定 locale（1.4 只面向中文，用户已定）：上一版没有那一段，Robolectric 落 en-US，
 * 卡首行就成了「【Misunderstood】 角色错」那种半英半中——类别名来自资源（英）、
 * 原因来自夹具（中）。固定之后同一行两头都是中文。
 *
 * 落盘之前先过 [assertTwoCasesAreOnScreen]：这一格的名字承诺的是**两条案例都在屏上**，
 * 空列表（Empty 档）与转圈（Loading 档）都长得像别的基线，钉进来就是把两格混成一格。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    // zh-rCN 固定 locale（1.4 只面向中文）：上一版没有这一段，Robolectric 落 en-US，
    // 卡首行就成了「【Misunderstood】 角色错」那种半英半中——类别名来自资源（英）、
    // 原因来自夹具（中）。固定之后同一行两头都是中文，混排这条从夹具里消失。
    qualifiers = "zh-rCN-sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackCasesScreenVisualBaselineTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

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
                FeedbackCasesScreen(viewModel = vm, onBack = {})
            }
        }
        rule.waitForIdle()
        assertTwoCasesAreOnScreen()
        rule.onRoot().captureRoboImage()
    }

    /**
     * 录制前的状态断言：这一格必须到了「Content·两条案例」那一档。
     *
     * 三条各咬一种坏实现：
     * - spinner 还在 = Loading 档；说明锚点还在 = Empty / Error 档
     *   （`LbAsyncTags` 那两个锚点"状态换了 tag 不换"，正是这里能判的原因）；
     * - 两张卡的**正文**各在树里：折叠卡片那一行就是 `candidateReply`（`CaseCard` 现只画正文 + 时间）。
     *   「【类别】 原因」那一行诊断字段已在折叠态删走（见 `FeedbackCasesSemanticsTest` ），
     *   所以这里只认正文，不再拿那一句当锚点；两条正文都在 = 列表真画了两条而不是占位一行。
     */
    private fun assertTwoCasesAreOnScreen() {
        assertRecordedLocaleIsZhCn()
        assertEquals(
            "还在转圈：那一屏是 Loading 档，不是 Content",
            0,
            rule.onAllNodesWithTag(LbAsyncTags.LOADING).fetchSemanticsNodes().size
        )
        assertEquals(
            "状态件还挂着一句说明：那一屏是 Empty / Error 档",
            0,
            rule.onAllNodesWithTag(LbAsyncTags.MESSAGE).fetchSemanticsNodes().size
        )

        rule.onNodeWithText(understandingCase.candidateReply).assertExists()
        rule.onNodeWithText(expressionCase.candidateReply).assertExists()
    }

    /** 基线固定 zh-CN：locale 一漂，屏上的字整批变，这张就不是同一格。 */
    private fun assertRecordedLocaleIsZhCn() {
        val locales = app.resources.configuration.locales
        assertEquals("截图基线要求 zh-CN", "zh-CN", locales.toLanguageTags())
    }

    /** Content·两条案例：折叠卡片的正文与时间完整出现在像素里（诊断字段那行已不在折叠态）。 */
    @Test
    fun twoCasesHasACommittedVisualBaseline() =
        mount(listOf(understandingCase, expressionCase))
}
