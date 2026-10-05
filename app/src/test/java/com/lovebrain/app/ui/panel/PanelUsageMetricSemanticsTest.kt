package com.lovebrain.app.ui.panel

import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.feature.profile.ProfileReview
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.ui.panel.stats.UsageStatTags
import com.lovebrain.app.ui.panel.stats.groupUsageStatFields
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.UsageStats
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 面板顶部那条使用统计：页面这一侧交出去的**内容口径**没换，挂法换成了单行统计条。
 *
 * 这一档从「五格按 leftDp 排成一条横带」改成「一条永远只占一行的统计条」之后，
 * 判据跟着换，但换的是**怎么看**，不是**看什么**：
 * 1. 页面确实挂的是那一颗统计条（`UsageStatBar` 那枚锚点在不在）——
 *    这一条专门拦"嘴上换了组件、手上还留着自画那一横条 / 或者直接挂回 `LbMetricGrid`"；
 * 2. **逐格逐字仍然逐字**：五格的标签与数值串一字不差（首字 >0 才加、费用仍走 `costReadout`）。
 *    生产那一颗把每一格缝成**一整段「标签＋值」**的 Text（旧 Inline 档那种"一格两颗 Text、
 *    各自能换行"正是"冒出第二行"的来源），所以读数按**那一整串**取，标签与数值各自剥出来比对，
 *    一段都不许多、也不许少；按内容取而不按 leftDp 排——轮播起来之后停在视口外的那一组会被
 *    父级裁成零尺寸盒子，按几何排序读就会读成"少了几格"，那是量具错了不是页面错了；
 * 3. **永远只占一行**：画得出来的那些格必须同在一行上、整条高度只有一行档，
 *    而且摆出来的那一截必须是**一整个分组**（不许出现半个分组、半个数字）；
 * 4. 标签与数值两头都非空，而且这一排不许多出点击语义（归并不顺手把只读小字变成按钮：
 *    统计条整棵子树读不到点击/切换/禁用语义，画得出来的格中心点也不落在任何可点节点里）；
 * 5. 条件槽与占位：「首字」只在有耗时时存在；没有数的那几格念「—」而不是 `¥0.000`。
 *
 * 底下三格组件档判据量的还是 `LbMetricGrid` 那一颗（一格两颗 Text 的那一档），
 * 那一份形状没被本轮撤掉，所以那三格仍按 [Cell] 读。
 *
 * ⚠ 分工：换组算法、渐隐、自然宽不被压薄这些**组件自己**的合同归
 * `ui/panel/stats/UsageStatBarSemanticsTest` 与 `UsageStatBarPlanTest`，本文件不重抄一遍；
 * 本文件只判"面板这一页把什么交给了它、以及它在页面里占了几行"。
 *
 * ⚠ 时钟不自己走：统计条的换组表是一条 `while(true) delay(4s)`，
 * 自动走时会让 `waitForIdle` 一路快进、读数停在随机某一组上。下面每格都只喂几帧。
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

    @Before
    fun freezeTheClock() {
        rule.mainClock.autoAdvance = false
    }

    /** 只喂几帧：让 `onSizeChanged` 量到的自然宽回灌进换组方案并收敛，一帧 16ms，远不到一个间隔 */
    private fun settle(frames: Int = 8) {
        repeat(frames) { rule.mainClock.advanceTimeBy(16L) }
        rule.waitForIdle()
    }

    // ═══════════ 挂载：面板整屏 ═══════════

    /**
     * 与 `PanelHostSemanticsTest` 同一套桩：泛型流一条都不留给 relaxed
     * （relaxed 交回泛型 mock，`.value` 一取就 ClassCastException）。
     */
    /** 意图那两格读数现在住在 [IntentController] 里，面板经 `vm.intents` 读它们 */
    private fun fakeIntents(): IntentController = mockk<IntentController>(relaxed = true).also {
        every { it.config } returns MutableStateFlow(IntentConfig())
        every { it.showEditor } returns MutableStateFlow(false)
    }

    private fun fakeVm(usage: UsageStats): LoveBrainViewModel =
        mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
            // VM 上那 10 条纯转发口已删，状态归 ComposerStore 自己
            val composer = mockk<ComposerStore>(relaxed = true)
            every { vm.composer } returns composer
            every { composer.panelMode } returns MutableStateFlow(0)
            // 「我的想法」那一行读的就是这一颗出口；给空串 = 这一屏没有想法
            every { composer.ideaHint() } returns ""
            every { vm.activeKb } returns MutableStateFlow(null)
            every { vm.composerMode } returns MutableStateFlow(ComposerMode.REPLY)
            every { composer.counselingDraft } returns MutableStateFlow("")
            every { vm.counselingError } returns MutableStateFlow(null)
            every { vm.counselingResult } returns MutableStateFlow<String?>(null)
            every { vm.counselingStreaming } returns MutableStateFlow("")
            // （用户 2026-10-03 原话"点踩就不要弹窗全部删除！！记入就行了"）：VM 上那颗
            // `currentFeedbackCase` 出口随原因面板一起摘除，面板这一侧一个字都不读它
            // （`ui/panel/LoveBrainPanelScreen.kt:226` 明写"这里**故意不收集**"）。旧桩替它挂的
            // 那一条空流删掉；赞/踩的状态出口仍然只有下面那一条 `vm.feedbacks`。
            every { composer.currentRole } returns MutableStateFlow(ChatMessage.Role.HER)
            // 输入对象（她/我/补充）。 之后面板在 `LoveBrainPanelScreen.kt:190` 无条件 collect
            // `composer.inputKind`，而这一条是本文件唯一漂掉的一格泛型流——relaxed 交回的是裸
            // `Object`（`StateFlow<T>.value` 擦除后的返回类型），面板一 checkcast 就 CCE，
            // 整棵组合树在挂载那一帧就断了：本文件那 6 红全是这一个异常，不是使用统计的形状。
            // 桩成生产真的那一档：`ComposerStore._inputKind` 初始就是 HER（ComposerStore.kt:192），
            // 与上面 `currentRole = HER` 同轴（`toComposerInputKind()` 推出的也是 HER）。
            every { composer.inputKind } returns MutableStateFlow(ComposerInputKind.HER)
            // 「仅看本轮」：面板 `LoveBrainPanelScreen.kt:192` 紧接着 collect 这一颗。它和上面那格
            // 是同一个病因，只是被前一个异常挡在后面——补完 inputKind 它就会成为下一发 CCE，
            // 所以一次补到位（false = 开关没拨过那一档，与生产初值同形）。
            every { vm.onlyThisRound } returns MutableStateFlow(false)
            every { vm.currentVector } returns MutableStateFlow(emptyMap())
            every { composer.draftText } returns MutableStateFlow("")
            every { composer.editingIndex } returns MutableStateFlow(-1)
            every { vm.feedbacks } returns MutableStateFlow(emptyMap())
            every { vm.generationRoundId } returns MutableStateFlow(1)
            every { composer.ideaComposeMode } returns MutableStateFlow(false)
            every { vm.inputChanged } returns MutableStateFlow(false)
            every { vm.intents } returns fakeIntents()
            every { vm.isCounseling } returns MutableStateFlow(false)
            every { vm.isGenerating } returns MutableStateFlow(false)
            every { vm.isGeneratingCore } returns MutableStateFlow(false)
            every { vm.isProactive } returns MutableStateFlow(false)
            // 通知位：面板只读这一颗（null = 通知位空着，这一屏不该有通知条）
            every { vm.currentNotice } returns MutableStateFlow(null)
            every { composer.messages } returns MutableStateFlow(emptyList())
            every { vm.proactiveError } returns MutableStateFlow(null)
            every { vm.proactiveOptions } returns MutableStateFlow(emptyList())
            every { vm.profileRegenerating } returns MutableStateFlow(false)
            every { vm.profileReview } returns MutableStateFlow(ProfileReview())
            every { vm.providerReady } returns MutableStateFlow(true)
            every { vm.result } returns MutableStateFlow(null)
            every { vm.resultMode } returns MutableStateFlow(ResultMode.REPLY)
            every { vm.rewriteStates } returns MutableStateFlow(emptyMap())
            every { vm.stageSuggestion } returns MutableStateFlow(null)
            every { vm.streamingCoreText } returns MutableStateFlow("")
            every { vm.streamingSchemes } returns MutableStateFlow(emptyList())
            every { vm.usageStats } returns MutableStateFlow(usage)
            every { vm.vectorDelta } returns MutableStateFlow(emptyMap())
            // 背景浓度：滑杆没动过那一档 = 100%，与从前逐字同形
            every { vm.panelBackdropOpacityPercent } returns 100
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
                        onOpenAppPage = { },
                        onCollapse = { }
                    )
                }
            }
        }
        settle()
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

    /**
     * 一格：自己的盒子 + 格子里全部文本（未合并树里一颗 `Text` 就是一个节点）。
     *
     * [leftDp]/[widthDp] 只在**这一格摆在视口里**时才有意义：停在视口外的那一组会被
     * 统计条自己那层 `clipToBounds()` 裁进语义盒子里，量出来是零尺寸——那是量具的边界，
     * 不是页面少画了一格。所以"少没少格"按 [announced] 判，"摆没摆出来"按可见那些判。
     */
    private data class Cell(
        val leftDp: Float,
        val topDp: Float,
        val widthDp: Float,
        val heightDp: Float,
        val texts: List<String>
    ) {
        /** 读屏在这一格里念回来的那一整串 */
        val announced: String get() = texts.joinToString("")

        val label: String get() = texts.firstOrNull()?.trim().orEmpty()

        /** 摆在视口里、看得见的一格（盒子被裁成空 = 停在左右两侧之外） */
        val onScreen: Boolean get() = widthDp > 0f && heightDp > 0f

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

    /**
     * `LbMetricGrid` 那一颗的原子项格：**一格拆成标签与数值两颗 Text** 的那一档。
     * 本文件只剩底下三格组件档判据用它；面板顶部那一排走 [statCells]（一格 = 一整段合并串）。
     *
     * 这里刻意不再 `sortedBy { leftDp }`：轮播起来之后停在视口外的那一组几何读数是零，
     * 按左缘排序会把它整个排到别处去、甚至与"少了一格"分不开。内容判据按内容读。
     */
    private fun allCells(): List<Cell> =
        rule.onAllNodesWithTag(LbTags.METRIC_CELL, useUnmergedTree = true).fetchSemanticsNodes().map { n ->
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
        }

    /**
     * 统计条那一颗画出的**格**：一格 = 一整段「标签＋值」的 Text（生产那一颗把标签与数值缝进
     * 同一段里，中间只留一个空格），所以这里读的是那**一整串**，不再"按标签取一次、按数值取一次"。
     *
     * 顺序按组合顺序（第一组在前，随后每一组），刻意不按左缘排：轮播起来后停在视口外的那一组
     * 会被统计条自己那层裁解压成 `0x0dp @(0,0)`，而**那段文本仍逐字读得回来**——按内容判少没少格，
     * 按几何判摆没摆出来（[painted] 那些）。
     */
    private data class StatCell(
        val merged: String,
        val leftDp: Float,
        val topDp: Float,
        val widthDp: Float,
        val heightDp: Float
    ) {
        val label: String get() = merged.substringBefore(' ')
        val value: String get() = merged.substringAfter(' ')
        val painted: Boolean get() = widthDp > 0f && heightDp > 0f
        fun describe(): String =
            "「$merged」 宽${widthDp.toInt()}x高${heightDp.toInt()}dp @(${leftDp.toInt()},${topDp.toInt()})"
    }

    private fun statCells(): List<StatCell> = textNodesUnder(barNode()).map { node ->
        val b = node.boundsInRoot
        StatCell(
            merged = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text },
            leftDp = b.left / density,
            topDp = b.top / density,
            widthDp = b.width / density,
            heightDp = b.height / density
        )
    }

    private fun paintedStatCells(): List<StatCell> = statCells().filter { it.painted }

    /** 把树上的每一格拼回 [LbMetric] 表（供生产那颗分组函数现算合法分组，测试不抄名单） */
    private fun statReadBack(): List<LbMetric> = statCells().map { LbMetric(it.label, it.value) }

    /** 一颗子树（含自己）：判"统计条里面有没有点击语义"要整棵都看到 */
    private fun subtreeOf(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap { subtreeOf(it) }

    /** 一格必须两头都在同一段里：标签与数值任一头为空 = 那一格少了一半 */
    private fun assertStatCellsComplete(cells: List<StatCell>, where: String) {
        cells.forEachIndexed { i, c ->
            assertTrue(
                "$where 第 ${i + 1} 格不是「标签＋值」一整段（任一头为空或被拆成两颗）：${c.describe()}",
                c.label.isNotBlank() && c.value.isNotBlank() && c.merged == "${c.label} ${c.value}"
            )
        }
    }

    /** 统计条那一枚锚点（页面挂的是这一颗，不是一排裸 `LbMetricGrid`） */
    private fun barNode(): SemanticsNode {
        val nodes = rule.onAllNodesWithTag(UsageStatTags.BAR, useUnmergedTree = true).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) {
            "读不到统计条那一枚锚点——页面顶部那一排又变回页面自己画的小字，或者直接挂 `LbMetricGrid` 了。" +
                "这一屏量到的文本：" + allCells().joinToString { it.describe() }
        }
        return nodes.single()
    }

    /** 三件事一起判：两条文本齐、每一条都念得出来（尺寸只在摆出来那一截判，见 [assertOneRow]） */
    private fun assertCellsReadable(cells: List<Cell>, where: String) {
        cells.forEachIndexed { i, c ->
            assertTrue(
                "$where 第 ${i + 1} 格没读回两条文本（标签与数值各一条），实到 ${c.texts}：" + c.describe(),
                c.texts.size == 2
            )
            assertTrue(
                "$where 第 ${i + 1} 格念不出名字（标签或数值有一条为空 = 两条文本少一条）：" + c.describe(),
                c.texts.all { it.isNotBlank() } && c.announced.isNotBlank()
            )
        }
    }

    /** 页面交出去的那五格，标签顺序（数值串在上面那格里逐字对） */
    private val fullLabels = listOf("今日", "本次", "首字", "累计", "已统计")

    // ═══════════ 判据 ═══════════

    /**
     * 挂法 + 内容：页面确实挂的是那颗单行统计条，而且五格逐字没换。
     *
     * 每一格读的是**一整段「标签＋值」**（生产那一颗的原子项形状），不是"标签一颗、数值一颗"：
     * 反例（都能把这格弄红）：把 `UsageStatBar` 换回一颗裸 `LbMetricGrid`（锚点没了）、把一格
     * 拆回两颗 Text（段数变成 10、逐段比对当场合不上）、某一格在传参时丢了标签、给"不知道"的
     * 那格填了 `¥0.000`、或者页面自己在这儿重算了一遍钱（数值串就不是 `costReadout` 那一条口径了）。
     */
    @Test
    fun `the panel hands the same five fields, word for word, to the single-row stat bar`() {
        mountPanel(fullUsage)
        barNode()
        val cells = statCells()
        assertEquals(
            "页面该把五格都交给统计条（一格 = 一整段「标签＋值」），实到 ${cells.size} 段：" +
                cells.joinToString(" | ") { it.describe() },
            5, cells.size
        )
        assertStatCellsComplete(cells, "面板·使用统计")
        assertEquals(
            "五格的标签顺序与内容变了（每格的标签都得在自己那一段里，一条都不许丢）：" +
                cells.joinToString(" | ") { it.describe() },
            fullLabels, cells.map { it.label }
        )
        // 标签＋数值整串逐字对（「首字」那格只比到标签：数值串走的是没锁 Locale 的
        // `"%.1fs".format(…)`，逗号/点号随制式变，账本里单独记着那一格）
        assertEquals(
            "四格（除「首字」的数值）念回来的整串变了：" + cells.joinToString(" | ") { it.describe() },
            listOf("今日 ¥1.230", "本次 ¥0.500", "累计 42次", "已统计 ¥8.900"),
            listOf(cells[0], cells[1], cells[3], cells[4]).map { it.merged }
        )
        assertTrue(
            "「首字」那格的数值不像一个带秒的耗时：" + cells[2].describe(),
            Regex("""^\d[.,]\ds$""").matches(cells[2].value)
        )
    }

    /**
     * **本轮核心需求**：统计永远只占一行。
     *
     * 三条一起判才咬得住：
     * · 画得出来的那些格必须**同在一行**（垂直中心一致）——换成一排会折行的布局，
     *   第二行的格顶就不在同一档上，这一条当场红；
     * · 整条统计条自己的高度只许有一行档（10sp 那一档 + 一点行距），
     *   挤成两行就是它的一倍多——这一条连"只画一行但把槽位撑成两行高"那种坏法一起拦；
     * · 摆出来的那一截必须是**一整个分组**（不许半个分组、半个数字）：合法分组由生产那颗
     *   [groupUsageStatFields] 现算，测试不抄名单。
     * 停在视口外的那一组报 `0x0dp @(0,0)`，所以它既不算"摆出来"，也不参与同一行的比较——
     * 只按内容算进 [statCells]，用来现算合法分组。
     */
    @Test
    fun `the stat bar inside the panel keeps one row and shows at most one whole group`() {
        mountPanel(fullUsage)
        val bar = barNode()
        val barHeightDp = bar.boundsInRoot.height / density
        val shown = paintedStatCells()
        assertTrue(
            "统计条里一格都没摆出来（要么整条被挤到视口外，要么这一屏根本没挂上内容）：" +
                statCells().joinToString(" | ") { it.describe() },
            shown.isNotEmpty()
        )
        assertTrue(
            "整条统计条高 ${barHeightDp.toInt()}dp，一行那一档放不下这个数——它折行或长高了",
            barHeightDp <= 24f
        )
        val centers = shown.map { it.topDp + it.heightDp / 2f }
        val spread = centers.max() - centers.min()
        assertTrue(
            "摆在视口里的格不在同一行上（垂直中心最大差 ${spread.toInt()}dp）：" +
                shown.joinToString(" | ") { it.describe() },
            spread <= 1f
        )
        // 摆在里面的那一截 = 一整个分组（或一行放得下时的全部分组）
        val allowedSets: List<Set<String>> =
            groupUsageStatFields(statReadBack()).map { group -> group.map { it.label }.toSet() }
        val shownLabels = shown.map { it.label }.toSet()
        assertTrue(
            "统计条摆出来的是「$shownLabels」，既不是完整的一组、也不是全部：" +
                statCells().joinToString(" | ") { it.describe() } + "\n允许的分组：" + allowedSets,
            allowedSets.any { it == shownLabels } || fullLabels.toSet() == shownLabels
        )
    }

    /** 没有首字耗时时**少那一格**，而不是摆一个 `0.0s` 的假数 */
    @Test
    fun `the first-token cell goes away with the latency instead of showing a fake zero`() {
        mountPanel(fullUsage.copy(lastResponseMs = 0L))
        val cells = statCells()
        assertStatCellsComplete(cells, "面板·使用统计·无耗时")
        assertEquals(
            "没有首字耗时就该少那一格（一整段「标签＋值」少一段），而不是摆一个假数；实到 " +
                cells.joinToString(" | ") { it.describe() },
            listOf("今日 ¥1.230", "本次 ¥0.500", "累计 42次", "已统计 ¥8.900"),
            cells.map { it.merged }
        )
        assertTrue(
            "「首字」那一档不许被填成 0 秒的假数：" + cells.joinToString { it.value },
            cells.none { it.value.contains("0.0s") || it.label == "首字" }
        )
    }

    /**
     * 全默认那份快照：「本次」没有数时念「—」，不是 `¥0.000`；
     * 「今日」「已统计」在**一条可计价记录都没入过账**时同样念「—」
     * ——那是"不知道"，不是"这台机器上的 AI 全免费"（`costReadout` 的 Unknown 那一档）。
     */
    @Test
    fun `an unused session still reads the dash placeholder for last cost`() {
        mountPanel(UsageStats())
        val cells = statCells()
        assertStatCellsComplete(cells, "面板·使用统计·空快照")
        assertEquals(
            "空快照该有 4 格（首字那格没有数），且三格费用都念占位符不是 0；实到 " +
                cells.joinToString(" | ") { it.describe() },
            listOf("今日 —", "本次 —", "累计 0次", "已统计 —"),
            cells.map { it.merged }
        )
        // 逐格剥出数值那一半再判"不知道有没有被念成 0 元"（「0次」是计数，不是钱）
        val money = cells.filter { it.label != "累计" }.map { it.value }
        val fakeZero = Regex("""[¥￥]\s*0(\.0+)?$""")
        assertTrue("费用那三格里有被伪造成 0 元的读数：$money", money.none { fakeZero.matches(it) })
    }

    /**
     * 归并到统计条之后，这一排仍然**不许多出点击语义**。
     *
     * 两道网：
     * · 结构上——统计条那一枚锚点的**整棵子树**里不许有任何带 `OnClick`/可切换/被禁用的节点
     *   （把某一格变成按钮、或给这一排挂个"点开详情"，这一条就红，跟摆位无关）；
     * · 几何上——画得出来的那几格中心点不许落在屏上任何可点节点里（格子被塞进某个大按钮的
     *   热区，这一条红）。
     * ⚠ 这里判的是点击/切换语义，不是"任何指针输入"——统计条在真放不下时装的是一条**横向拖拽**
     *   （换组用），那是"翻这一排"，不是把某一格变成按钮。
     */
    @Test
    fun `the usage strip stays read-only and adds no actionable node`() {
        mountPanel(fullUsage)
        val shown = paintedStatCells()
        assertTrue(
            "这一屏一格统计都没摆出来，判据在空转：" + statCells().joinToString(" | ") { it.describe() },
            shown.isNotEmpty()
        )
        val insideBar = subtreeOf(barNode()).filter { node ->
            node.config.contains(SemanticsActions.OnClick) ||
                node.config.contains(SemanticsProperties.ToggleableState) ||
                node.config.contains(SemanticsProperties.Disabled)
        }
        assertTrue(
            "使用统计那条是只读的，统计条自己那棵子树里却读到了 ${insideBar.size} 颗带点击/" +
                "切换/禁用语义的节点：" + insideBar.joinToString(" | ") { describeNode(it) },
            insideBar.isEmpty()
        )
        val actionable = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val hit = shown.mapIndexedNotNull { i, c ->
            val x = (c.leftDp + c.widthDp / 2f) * density
            val y = (c.topDp + c.heightDp / 2f) * density
            actionable.firstOrNull { n ->
                val b = n.boundsInRoot
                b.left <= x && x <= b.right && b.top <= y && y <= b.bottom
            }?.let { "第 ${i + 1} 格 ${c.describe()} 落在可点节点 ${describeNode(it)} 里" }
        }
        assertTrue(
            "使用统计那条是只读的，归并到统计条之后却多出了点击语义：\n" + hit.joinToString("\n"),
            hit.isEmpty()
        )
    }

    /**
     * 通知位上一次只许**一条**，而且顶部统计不许被它挤开第二行。
     *
     * 两条一起判的理由：这一轮把三条通道并成一条队列，最坏的坏法是"队列收住了、
     * 但页面上给通知新起了一行，顶部那条跟着折成两行"。
     * 排队里那条**根本不该出现在这一屏上**（它还没被显示，也就没有画面），
     * 所以第二条的文案必须读不到——读得到就是"一屏挤两条"回来了。
     */
    @Test
    fun `a notice on top of the strip still leaves the stats on one row`() {
        val notices = NoticeBoard()
        notices.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        notices.show(NoticeBoard.Channel.Vector, "五维关系有更新")
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = fakeVm(fullUsage).also { vm ->
                            every { vm.currentNotice } returns notices.current
                        },
                        onInputFocusChange = { _, _ -> },
                        onInputIntent = { },
                        onClearComposeFocus = { },
                        onResize = { _, _ -> },
                        onMove = { _, _ -> },
                        onCopy = { },
                        onOpenAppPage = { },
                        onCollapse = { }
                    )
                }
            }
        }
        settle()
        assertTrue(
            "通知位上正在显示的那一条没画出来，顶部那一排量到：" +
                statCells().joinToString(" | ") { it.describe() },
            rule.onAllNodes(hasText("经验提取完成", substring = true)).fetchSemanticsNodes().isNotEmpty()
        )
        assertEquals(
            "排队里的那条不该出现在这一屏上（一次只展示一条，其余等待）",
            0, rule.onAllNodes(hasText("五维关系有更新", substring = true)).fetchSemanticsNodes().size
        )
        val barBox = barNode().boundsInRoot
        assertTrue(
            "多了一条通知，顶部统计就长高了：整条高 ${(barBox.height / density).toInt()}dp",
            barBox.height / density <= 24f
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
        settle()
        val cells = allCells()
        assertCellsReadable(cells, "Inline 档")
        assertTrue(
            "Inline 档的每一格都该摆在同一行上：" + cells.joinToString { it.describe() },
            cells.map { it.topDp }.distinct().size == 1
        )
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
            settle()
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
            settle()
        }.exceptionOrNull()
        assertTrue("给了内容却抛出异常：$thrown", thrown == null)
    }
}
