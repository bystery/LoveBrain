package com.lovebrain.app.ui.feedback

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 反馈案例页（§6.1 :478 页面外框 + :479 页头 + §6.5 :531/:532 热区与角色）。
 *
 * ## 为什么这一页值得单独一棵守卫
 *
 * 账本 §57 用一次性探针把这一页挂进语义树量了一遍，量到的是：**9 颗可点节点全部
 * `role=无`，其中 6 颗只有 15–19dp 高**。同一页还同时背着两笔登记着的欠账——
 * 自己画整屏底色（`the page frame has exactly one owner`）和自己拼返回那颗
 * （`the page header has exactly one owner`）——所以搬这一页必须"一次销两笔"，
 * 搬一半留一半比不搬更坏：表里会留下一条指向已改文件的旧豁免。
 *
 * ## 这一页挂得上 JVM
 *
 * `UiLayerDependencyContractTest` 的页头那一格原先写着"这一页要
 * `rememberLauncherForActivityResult`，JVM 上挂不起来"。**那条理由是错的**：
 * `createComposeRule` 下注册 launcher 不报错（§57 的探针就是这么量到 9 颗节点的），
 * 挂不起来的是**触发**那一步（`saveLauncher.launch()` 要起真 Activity 选择器）。
 * 所以这一页和别的页一样，四条流桩住就组合得起来（坑表：「要 VM」不是测不到的理由）。
 *
 * ## 夹具为什么两条案例、各带一个原因
 *
 * §57 那份探针只给了一条 `categories = emptyList()` 的案例，于是整卡在树里的名字
 * 是「【】」——那是**夹具造出来的空标题**，不是生产形状。本格两条案例都带类别与原因，
 * 量到的卡片名字才是用户真会听到的那句。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackCasesSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

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

    /**
     * 挂载槽 600dp，不是随手挑的：360 那一档**装不下这族六颗芯片**，横排会裁——
     * 滚出视口的那颗在语义树里被压成 `0x0 @(0,0)`，贴着右边的那颗压成"看得见的那半截"
     * （本机实量：「JSON」0x0、「✓ Markdown」15x48）。这一族要判的是**每颗自己的热区**，
     * 拿被裁的读数去判就会红在一个没发生过的缺陷上。
     *
     * ⚠ 但 360 那一档不能不管：换成宽槽有可能把问题藏起来（"是修好了还是终于放得下了"），
     * 所以另有 `the narrow slot still exposes…` 那格——在 360 下横扫这一行，
     * 每颗都按它**量到的最大面积**算（与 `ScrollScan` 同一条理由：裁切只会让读数变小）。
     */
    private fun mount(cases: List<FeedbackCase>, widthDp: Float = 600f): SetupViewModel {
        val vm = mockk<SetupViewModel>(relaxed = true).also {
            every { it.feedbackCases } returns MutableStateFlow(cases)
            every { it.feedbackLoading } returns MutableStateFlow(false)
            every { it.feedbackError } returns MutableStateFlow<String?>(null)
            every { it.exportState } returns
                MutableStateFlow<SetupViewModel.ExportState>(SetupViewModel.ExportState.Idle)
        }
        rule.setContent {
            UiMatrix(widthDp.toInt()).RenderIn(LocalDensity.current.density) {
                FeedbackCasesScreen(viewModel = vm, onBack = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        return vm
    }

    private fun targets(screen: String): List<SemanticsProbe.Target> =
        probe.laid(probe.actionableTargets(rule, screen))

    /**
     * 页头那一条带 = 起点落在页头行高之内的那些节点。
     *
     * 行高不另写一个数：页头那一行的高就是那颗返回钮的边长，与设计系统同一个常量
     * （[AppDimens.TOUCH_TARGET_MIN_DP]）。第一版这里写的是 `topDp < 56f`，
     * 而芯片放大成 48dp 两层之后它正好落在 52 —— 判据会把两颗芯片算进页头，
     * 红在"页头怎么有四颗节点"这种话上。
     */
    private fun headerBand(screen: String): List<SemanticsProbe.Target> =
        targets(screen).filter { it.topDp < AppDimens.TOUCH_TARGET_MIN_DP }

    private fun tapNode(matcher: androidx.compose.ui.test.SemanticsMatcher, who: String) {
        val found = rule.onAllNodes(matcher)
        val nodes = found.fetchSemanticsNodes()
        assertEquals("$who 在语义树里应当恰好一颗（0 颗=夹具没挂上，2 颗=认错人）", 1, nodes.size)
        // ⚠ `performClick()` 是 `SemanticsNodeInteraction` 的扩展，不是 `SemanticsNode` 的：
        //   先 fetchSemanticsNodes() 再 .single().performClick() 编译不过（第一版就栽在这儿）。
        found[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
    }

    // ── :531 热区 ────────────────────────────────────────────────────────────

    @Test
    fun `every control on the feedback cases page meets the 48dp floor`() {
        mount(listOf(understandingCase, expressionCase))
        probe.assertAllActionableMeetTouchFloor(rule, "反馈案例页")
    }

    @Test
    fun `every control on the feedback cases page names itself`() {
        mount(listOf(understandingCase, expressionCase))
        probe.assertAllActionableLabeled(rule, "反馈案例页")
    }

    // ── :532 角色 ────────────────────────────────────────────────────────────

    /**
     * 芯片那一族是**互斥单选**（筛哪个类别、导出哪种格式），语义该报 `Tab` + `selected`，
     * 不是把状态烤进标签字形里（「✓ Markdown」那种）。
     *
     * ⚠ `assertEquals(6, …)` 挡的是"按 role 分组分到一个空集，于是下面那条判据空过"——
     * `assertSelectableAnnounceState` 对空集合是真会绿的。
     */
    @Test
    fun `the filter chips are tabs that announce the selected one`() {
        mount(listOf(understandingCase, expressionCase))
        val tabs = targets("反馈案例页·芯片").filter { it.role == "Tab" }
        // 全部 + 三个类别 + Markdown + JSON
        assertEquals(
            "芯片应当有 6 颗报成 Tab：" + targets("反馈案例页·芯片").joinToString { it.describe() },
            6, tabs.size
        )
        probe.assertSelectableAnnounceState(tabs, "反馈案例页·芯片")
    }

    /**
     * 返回那颗的**名字**不在这儿判——它归 `PageHeaderConsistencyTest`（那一族五页一页一格，
     * 期望值同样现读 `R.string.common_back`）。这一格只判页头两颗的**角色**：
     * §57 量到的是 `role=无`，而名字那一半已经有主了，两处各写一份就是给自己埋重复账。
     */
    @Test
    fun `the header actions declare a role`() {
        mount(listOf(understandingCase, expressionCase))
        val header = headerBand("反馈案例页·页头")
        assertEquals(
            "页头这一带应当量到两颗（返回 + 尾部那颗导出），实到：" + header.joinToString { it.describe() },
            2, header.size
        )
        val roleless = header.filter { it.role == "无" }
        assertTrue("页头这两颗没声明 role：" + roleless.joinToString { it.describe() }, roleless.isEmpty())
        header.forEach {
            assertEquals("页头这两颗都是按钮，不是选项卡：" + it.describe(), "Button", it.role)
        }
    }

    /**
     * 整卡可点 = 展开/折叠，是一颗按钮（§57：这一颗 336x68dp，热区本来够，缺的是角色）。
     * 认它用"这一屏最宽的可点节点"，不用标签——卡片合并后的第一个文本是「【类别】原因」，
     * 标签会随夹具内容变（§57.2 那条工具事实）。
     */
    @Test
    fun `the case card is a button`() {
        mount(listOf(understandingCase, expressionCase))
        val card = targets("反馈案例页·卡片").maxByOrNull { it.widthDp }
            ?: error("一个可点节点都没量到")
        assertEquals(
            "整卡可点=展开，语义要报成 Button（读屏才知道按下去有结果）：" + card.describe(),
            "Button", card.role
        )
    }

    /**
     * 360 那一档：横扫这一行，每颗芯片按它**量到的最大面积**判 ≥48dp。
     *
     * 这一格是给上面那些 600 槽的格子当**反向证人**的：换成宽槽全绿，
     * 可能只是"终于放得下了"而不是"热区修好了"。扫过一遍每颗还是 48，
     * 才说明那 48dp 是芯片自己的尺寸。裁取原理与两条哨兵抄 `ScrollScan`
     * （裁切只会让读数变小；指纹不重复就是没扫完，不许拿半截当证据）。
     */
    @Test
    fun `the narrow slot still exposes a 48dp touch box for every chip`() {
        mount(listOf(understandingCase, expressionCase), widthDp = 360f)
        val scrollBy = SemanticsMatcher("hasScrollBy") {
            it.config.contains(SemanticsActions.ScrollBy)
        }
        val scrollers = rule.onAllNodes(scrollBy).fetchSemanticsNodes()
        check(scrollers.isNotEmpty()) { "这一页一个滚动容器都没有，横扫无从谈起" }
        // 芯片那一行是树里最靠上的那颗滚动容器（案例列表在它下面）
        val chipsRow = rule.onAllNodes(scrollBy)[
            scrollers.withIndex().minBy { it.value.boundsInRoot.top }.index
        ]
        val best = LinkedHashMap<String, SemanticsProbe.Target>()
        var previous: String? = null
        var sweptToEnd = false
        for (step in 0 until 8) {
            targets("反馈案例页·360 横扫第 $step 档")
                .filter { it.role == "Tab" }
                .forEach { t ->
                    val cur = best[t.label]
                    if (cur == null || cur.widthDp * cur.heightDp < t.widthDp * t.heightDp) {
                        best[t.label] = t
                    }
                }
            val fingerprint = best.values.joinToString(";") { "${it.label}@${it.leftDp.toInt()}" }
            if (fingerprint == previous) { sweptToEnd = true; break }
            previous = fingerprint
            chipsRow.performTouchInput { swipeLeft() }
            rule.mainClock.advanceTimeBy(16L)
        }
        assertTrue("横扫 8 档还没扫到底（指纹从没重复过）——不能拿半截结果当整行的证据", sweptToEnd)
        assertEquals(
            "横扫完应当见到全部 6 颗芯片，实到 ${best.keys}：" +
                targets("反馈案例页·360 收口").joinToString { it.describe() },
            6, best.size
        )
        val small = best.values.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "360 槽里有芯片的热区不到 ${probe.floorDp.toInt()}dp：" + small.joinToString { it.describe() },
            small.isEmpty()
        )
    }

    // ── 行为：热区改成两层之后，点击还挂在那一颗上吗 ──────────────────────────

    /**
     * 把芯片放大成两层，最怕的就是"热区大了、点不动了"。这一格判的是**筛得动**：
     * 点「理解错误」之后另一类案例必须从树里消失。
     */
    /**
     * 芯片的名字**从资源现读**，不写死中文：这一页的标签刚搬进 strings.xml，
     * 而本机 JVM 测试跑在英文环境下（`common_back` 实测读回 "Back"）。
     * 写死「理解错误」的 matcher 会从"找到那颗"变成"一颗都找不到"，
     * 红在一个不相干的理由上。
     */
    private fun chip(text: String) =
        hasText(text) and androidx.compose.ui.test.hasClickAction()

    @Test
    fun `tapping a category chip narrows the list to that category`() {
        mount(listOf(understandingCase, expressionCase))
        assertEquals(1, rule.onAllNodes(hasText("角色错", substring = true)).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodes(hasText("太油", substring = true)).fetchSemanticsNodes().size)
        // 「角色错」「太油」是**案例数据**（用户点踩时选的二级原因，存在库里），
        // 不是界面文字 ⇒ 这两条按字面量找是对的，界面那几条走资源。
        tapNode(chip(context.getString(R.string.feedback_category_understanding_error)), "类别芯片")
        assertEquals(
            "筛到理解错误之后，另一类的那条必须消失",
            0, rule.onAllNodes(hasText("太油", substring = true)).fetchSemanticsNodes().size
        )
        assertEquals(
            "筛到理解错误之后，这一类的那条必须还在",
            1, rule.onAllNodes(hasText("角色错", substring = true)).fetchSemanticsNodes().size
        )
    }

    /**
     * 空结果时导出那颗**灰着还在**，而不是从树上消失：`:479` 那句"页面唯一主动作"
     * 要的是当前能不能按，读屏得听得见 disabled。
     *
     * ⚠ 这一格在改动之前就是绿的（`clickable(enabled = …)` 本来会发布 Disabled），
     * 它买的是"改这一颗的 Modifier 链时别把 gating 弄丢"，不是新债——别当战果记。
     */
    @Test
    fun `the export action stays visible but reports itself disabled when nothing matches`() {
        mount(listOf(understandingCase, expressionCase))
        tapNode(chip(context.getString(R.string.feedback_category_other)), "类别芯片（其他）")
        val export = headerBand("反馈案例页·空结果").filter { it.leftDp > 100f }
        assertEquals(
            "空结果时页头仍该有那颗导出（不能一灰就没）：" +
                targets("反馈案例页·空结果").joinToString { it.describe() },
            1, export.size
        )
        assertTrue("导出那颗在零结果时必须报 disabled：" + export.single().describe(), export.single().disabled)
    }
}
