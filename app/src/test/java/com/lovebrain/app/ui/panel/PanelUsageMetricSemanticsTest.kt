package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.core.designsystem.LbMetricDensity
import com.lovebrain.app.core.designsystem.LbMetricGrid
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.profile.ProfileReview
import com.lovebrain.app.feature.roundcommit.ActualSentState
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.UsageStats
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
 * §6.1 表第 7 行归并的证人：面板顶部那条使用统计现在由 `LbMetricGrid`（Inline 档）来排。
 *
 * 为什么这一格必须**挂整屏**读，不许只挂组件：
 * 归并的失败模式不是「组件画不出来」（那种红在 designsystem 那一侧），而是
 * 「页面嘴上换了组件、手上还留着自画那一横条」，或者「换过去了但某一格的标签在传参时丢了」。
 * 两种都只有从面板这一侧读语义树才看得见——所以判据全落在**页面画出来的那五格**上。
 *
 * 判的四件事（每件事都有下面的反例能对撞）：
 * 1. **每格两条都读得回来**：每格同时读得到「标签」与「带单位的数值」两条文本，一条都不许少；
 * 2. **逐格逐字**：五格的标签顺序与数值串一字不差地对上（今日 / 本次 / 首字 / 累计 / 累计）；
 * 3. **热区与可访问名不为空**：每格盒子宽高都 >0，格内两条文本都非空白；
 *    并且这条小条**没有**多出点击语义（归并不顺手把一排只读的小字变成按钮）；
 * 4. **条件槽与占位**：「首字」只在有耗时时存在——缺的是那一格，不是一个 `0.0s` 的假数；
 *    「本次」没有数时仍念「—」，不是 `¥0.000`。
 *
 * ⚠ 一条读数的边界，别把下面的判据读成「面板排版没问题」：这条小字挂在 `DragHandle`
 * 那 4dp 高的锚点上（`wrapContentHeight(unbounded = true)`），**归并前后都是同一个锚点、
 * 同一批字号与间距**。本文件判的是「读回来什么」，不判它压没压住页头。
 * 「首字」那一格的数值串也不做逐字断言：生产走的是没锁 Locale 的 `"%.1fs".format(…)`
 * （锁 Locale 的只有 `formatYuan`），账本 §61.4 把那个逗号制洞单独记着，不在本拍修。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelUsageMetricSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density

    // ═══════════ 挂载：面板整屏 ═══════════

    /**
     * 与 `PanelHostSemanticsTest` 同一套桩：泛型流一条都不留给 relaxed
     * （relaxed 交回泛型 mock，`.value` 一取就 ClassCastException）。
     */
    private fun fakeVm(usage: UsageStats): LoveBrainViewModel =
        mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
            every { vm.panelMode } returns MutableStateFlow(0)
            every { vm.showPlanPanel } returns MutableStateFlow(false)
            every { vm.activeKb } returns MutableStateFlow(null)
            every { vm.actualSentState } returns MutableStateFlow(ActualSentState.IDLE)
            every { vm.composerMode } returns MutableStateFlow(ComposerMode.REPLY)
            every { vm.counselingDraft } returns MutableStateFlow("")
            every { vm.counselingError } returns MutableStateFlow(null)
            every { vm.counselingResult } returns MutableStateFlow<String?>(null)
            every { vm.counselingStreaming } returns MutableStateFlow("")
            every { vm.currentFeedbackCase } returns MutableStateFlow(null)
            every { vm.currentRole } returns MutableStateFlow(ChatMessage.Role.HER)
            every { vm.currentVector } returns MutableStateFlow(emptyMap())
            every { vm.draftText } returns MutableStateFlow("")
            every { vm.editingIndex } returns MutableStateFlow(-1)
            every { vm.feedbacks } returns MutableStateFlow(emptyMap())
            every { vm.generationRoundId } returns MutableStateFlow(1)
            every { vm.ideaComposeMode } returns MutableStateFlow(false)
            every { vm.inputChanged } returns MutableStateFlow(false)
            every { vm.intentConfig } returns MutableStateFlow(IntentConfig())
            every { vm.isCounseling } returns MutableStateFlow(false)
            every { vm.isGenerating } returns MutableStateFlow(false)
            every { vm.isGeneratingCore } returns MutableStateFlow(false)
            every { vm.isProactive } returns MutableStateFlow(false)
            every { vm.isSuggesting } returns MutableStateFlow(false)
            every { vm.kbNotice } returns MutableStateFlow(null)
            every { vm.messages } returns MutableStateFlow(emptyList())
            every { vm.onlyThisRound } returns MutableStateFlow(false)
            every { vm.panelWarning } returns MutableStateFlow(null)
            every { vm.proactiveError } returns MutableStateFlow(null)
            every { vm.proactiveOptions } returns MutableStateFlow(emptyList())
            every { vm.profileRegenerating } returns MutableStateFlow(false)
            every { vm.profileReview } returns MutableStateFlow(ProfileReview())
            every { vm.providerReady } returns MutableStateFlow(true)
            every { vm.result } returns MutableStateFlow(null)
            every { vm.resultMode } returns MutableStateFlow(ResultMode.REPLY)
            every { vm.rewriteStates } returns MutableStateFlow(emptyMap())
            every { vm.showIntentEditor } returns MutableStateFlow(false)
            every { vm.stageSuggestion } returns MutableStateFlow(null)
            every { vm.streamingCoreText } returns MutableStateFlow("")
            every { vm.streamingSchemes } returns MutableStateFlow(emptyList())
            every { vm.streamingTips } returns MutableStateFlow(emptyList())
            every { vm.suggestError } returns MutableStateFlow(null)
            every { vm.suggestion } returns MutableStateFlow<DailySuggestion?>(null)
            every { vm.usageStats } returns MutableStateFlow(usage)
            every { vm.vectorDelta } returns MutableStateFlow(emptyMap())
            every { vm.vectorUpdate } returns MutableStateFlow(null)
        }

    private fun mountPanel(usage: UsageStats) {
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = fakeVm(usage),
                        onInputFocusChange = { _, _ -> },
                        onInputIntent = { },
                        onClearComposeFocus = { },
                        onResize = { _, _ -> },
                        onMove = { _, _ -> },
                        onCopy = { },
                        onOpenSettings = { },
                        onCollapse = { }
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** 每格都有数、也有首字耗时：五格齐 */
    private val fullUsage = UsageStats(
        todayDate = "2026-09-27",
        todayCostYuan = 1.23,
        lastCostYuan = 0.5,
        lastResponseMs = 1234L,
        totalGenerateCount = 42,
        totalCostYuan = 8.9
    )

    // ═══════════ 读数 ═══════════

    /** 一格：自己的盒子 + 格子里全部文本（未合并树里一颗 `Text` 就是一个节点） */
    private data class Cell(
        val leftDp: Float,
        val topDp: Float,
        val widthDp: Float,
        val heightDp: Float,
        val texts: List<String>
    ) {
        /** 读屏在这一格里念回来的那一整串 */
        val announced: String get() = texts.joinToString("")

        fun describe(): String =
            "宽${widthDp.toInt()}x高${heightDp.toInt()}dp @(${leftDp.toInt()},${topDp.toInt()}) 「$announced」"
    }

    private fun textNodesUnder(node: SemanticsNode): List<SemanticsNode> {
        val mine = if (node.config.getOrNull(SemanticsProperties.Text)
                ?.any { it.text.isNotBlank() } == true
        ) listOf(node) else emptyList()
        return mine + node.children.flatMap { textNodesUnder(it) }
    }

    /** 一颗节点的读数（失败信息要凭这一行说得出是哪一颗、多大、念什么） */
    private fun describeNode(node: SemanticsNode): String {
        val b = node.boundsInRoot
        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("/") { it.text }
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("+")
        return "「$texts$desc」 宽${(b.width / density).toInt()}x高${(b.height / density).toInt()}dp" +
            "@(${(b.left / density).toInt()},${(b.top / density).toInt()})"
    }

    private fun cellsOf(anchor: String): List<Cell> =
        rule.onAllNodesWithTag(anchor, useUnmergedTree = true).fetchSemanticsNodes().map { n ->
            val b = n.boundsInRoot
            Cell(
                leftDp = b.left / density,
                topDp = b.top / density,
                widthDp = b.width / density,
                heightDp = b.height / density,
                texts = textNodesUnder(n).flatMap {
                    it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { a -> a.text }
                }
            )
        }.sortedBy { it.leftDp }

    /**
     * 面板顶部那条使用统计。
     *
     * 一颗都读不到就是归并没生效（页面自己画的那一横条从来没有 `LbTags.METRIC_CELL` 这个锚点），
     * 所以这里直接抛，不返回空集合让后面的断言空过。
     */
    private fun usageCells(): List<Cell> {
        val cells = cellsOf(LbTags.METRIC_CELL)
        check(cells.isNotEmpty()) {
            val onScreen = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                .fetchSemanticsNodes()
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { a -> a.text } }
            "面板上一颗 LbTags.METRIC_CELL 都没读到——那一横条还是页面自己画的。" +
                "这一屏量到的文本：$onScreen"
        }
        return cells
    }

    /** 三件事一起判：两条文本齐、盒子非空、每一条都念得出来 */
    private fun assertCellsReadable(cells: List<Cell>, where: String) {
        cells.forEachIndexed { i, c ->
            assertTrue(
                "$where 第 ${i + 1} 格没读回两条文本（标签与数值各一条），实到 ${c.texts}：" + c.describe(),
                c.texts.size == 2
            )
            assertTrue(
                "$where 第 ${i + 1} 格的盒子是空读数（连热区都没了）：" + c.describe(),
                c.widthDp > 0f && c.heightDp > 0f
            )
            assertTrue(
                "$where 第 ${i + 1} 格念不出名字（标签或数值有一条为空 = 两条文本少一条）：" + c.describe(),
                c.texts.all { it.isNotBlank() } && c.announced.isNotBlank()
            )
        }
    }

    // ═══════════ 判据 ═══════════

    @Test
    fun `the merged usage strip still reads label, value and unit cell by cell`() {
        mountPanel(fullUsage)
        val cells = usageCells()
        assertEquals(
            "面板顶部该有 5 格使用统计，实到 ${cells.size} 格：" + cells.joinToString { it.describe() },
            5, cells.size
        )
        assertCellsReadable(cells, "面板·使用统计")
        assertEquals(
            "五格的标签顺序与内容变了（每格的标签都得在自己那一格里，一条都不许丢）：" +
                cells.map { it.texts }.joinToString(" | ") { it.joinToString("/") },
            listOf("今日", "本次", "首字", "累计", "累计"), cells.map { it.texts.first().trim() }
        )
        // 整串逐字对：标签与数值之间那颗空格是组件自己放的分隔符（归并前后同一颗）
        assertEquals(
            "四格（除「首字」）念回来的整串变了：" + cells.joinToString { it.describe() },
            listOf("今日 ¥1.230", "本次 ¥0.500", "累计 42次", "累计 ¥8.900"),
            listOf(cells[0], cells[1], cells[3], cells[4]).map { it.announced }
        )
        // 数值那一半单独钉：单位跟着数值整串走，谁把它拆走这里就红
        assertEquals("¥1.230", cells[0].texts[1])
        assertEquals("¥0.500", cells[1].texts[1])
        assertTrue(
            "「首字」那格的数值不像一个带秒的耗时：" + cells[2].describe(),
            Regex("""\d[.,]\ds""").matches(cells[2].texts[1])
        )
        assertEquals("42次", cells[3].texts[1])
        assertEquals("¥8.900", cells[4].texts[1])
    }

    @Test
    fun `the first-token cell goes away with the latency instead of showing a fake zero`() {
        mountPanel(fullUsage.copy(lastResponseMs = 0L))
        val cells = usageCells()
        assertCellsReadable(cells, "面板·使用统计·无耗时")
        assertEquals(
            "没有首字耗时就该少那一格，而不是摆一个 0.0s 的假数；实到 " +
                cells.joinToString { it.describe() },
            listOf("今日 ¥1.230", "本次 ¥0.500", "累计 42次", "累计 ¥8.900"),
            cells.map { it.announced }
        )
    }

    /**
     * 全默认那份快照（`PanelHostSemanticsTest` 挂的就是它）：
     * 「本次」没有数时念「—」，不是 `¥0.000`；今日与累计那三格是真的零。
     */
    @Test
    fun `an unused session still reads the dash placeholder for last cost`() {
        mountPanel(UsageStats())
        val cells = usageCells()
        assertCellsReadable(cells, "面板·使用统计·空快照")
        assertEquals(
            "空快照该有 4 格（首字那格没有数），实到 " + cells.joinToString { it.describe() },
            listOf("今日 ¥0.000", "本次 —", "累计 0次", "累计 ¥0.000"),
            cells.map { it.announced }
        )
    }

    /**
     * 归并不顺手把一排只读的小字变成按钮：这一排上下都不许多出点击语义。
     *
     * 为什么判「没有」而不是判「够 48dp」：Inline 档只有 10sp 高，真要让它可点，
     * 得先把热区自己垫到下限（`LbMetricGrid` 的 KDoc 里写着这条）。谁把 `onClick` 传进来，
     * 这一格当场红——而不是等整屏那颗热区守卫去报一串谁也没见过的节点。
     */
    @Test
    fun `the usage strip stays read-only and adds no actionable node`() {
        mountPanel(fullUsage)
        val cells = usageCells()
        val actionable = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val hit = cells.mapIndexedNotNull { i, c ->
            val x = (c.leftDp + c.widthDp / 2f) * density
            val y = (c.topDp + c.heightDp / 2f) * density
            actionable.firstOrNull { n ->
                val b = n.boundsInRoot
                b.left <= x && x <= b.right && b.top <= y && y <= b.bottom
            }?.let { "第 ${i + 1} 格 ${c.describe()} 落在可点节点 ${describeNode(it)} 里" }
        }
        assertTrue(
            "使用统计那条是只读的，归并之后却多出了点击语义：\n" + hit.joinToString("\n"),
            hit.isEmpty()
        )
    }

    // ═══════════ 组件档：新槽自己的两条合同（不挂整屏）═══════════

    @Test
    fun `the inline strip reads the caller's own labels in the given order`() {
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                LbMetricGrid(
                    metrics = listOf(
                        LbMetric(label = "今日", value = "¥1.000"),
                        LbMetric(label = "累计", value = "42次")
                    ),
                    density = LbMetricDensity.Inline
                )
            }
        }
        rule.waitForIdle()
        val cells = cellsOf(LbTags.METRIC_CELL)
        assertCellsReadable(cells, "Inline 档")
        assertEquals(
            "标签由调用方给：两颗就该读出两颗、原样顺序：" + cells.joinToString { it.describe() },
            listOf("今日 ¥1.000", "累计 42次"), cells.map { it.announced }
        )
    }

    /**
     * 内容来源只许有一个，而且**给了就必须画**。
     *
     * 归并这一格时组件上曾有第二条内容入口（三件套 `totalGenerate/totalCost/adoptRate`，
     * 标签写死在设计系统里），它已经整条删掉——所以现在"混着给"在类型上就写不出来了。
     * 这一格保留的是同一件事的另一半：**空内容不许静默画一张空卡**
     * （`ifEmpty { }` 那种"看着像加载失败其实是什么都没给"的静默偏向，只有当场红才修得回来）。
     */
    @Test
    fun `the inline density refuses an empty content source`() {
        val thrown = runCatching {
            rule.setContent {
                UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                    LbMetricGrid(
                        metrics = emptyList(),
                        density = LbMetricDensity.Inline
                    )
                }
            }
            rule.waitForIdle()
        }.exceptionOrNull()
        assertTrue(
            "metrics 为空应当当场抛（静默画一张空卡就是第二份口径），实到异常：$thrown",
            thrown is IllegalArgumentException && thrown.message?.contains("metrics") == true
        )
    }

    /**
     * 反向证人：上一段那句"混着给抛"能不能真的咬？
     * 拿**同一颗组件**给一份非空 metrics，必须不抛、而且按给的格数画出来——
     * 没有这一格，`require(metrics.isEmpty())` 写反（把正常输入也抛掉）没人看得出来。
     */
    @Test
    fun `the same guard does not fire on a real content source`() {
        val thrown = runCatching {
            rule.setContent {
                UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                    LbMetricGrid(
                        metrics = listOf(LbMetric(label = "今日", value = "¥1.000")),
                        density = LbMetricDensity.Inline
                    )
                }
            }
            rule.waitForIdle()
        }.exceptionOrNull()
        assertTrue("给了内容却抛出异常：$thrown", thrown == null)
    }
}
