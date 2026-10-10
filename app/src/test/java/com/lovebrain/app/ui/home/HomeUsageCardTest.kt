package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.viewmodel.HomeStatusViewModel
import com.lovebrain.app.viewmodel.HomeUsageReadout
import com.lovebrain.app.viewmodel.costReadout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页那一块累计使用**小卡**（M25 §五 取舍③：新决定**替代**旧规「不恢复使用概览/内部统计」，
 * 但新决定自己带了上限——"一块简洁的小卡，不恢复大而复杂的仪表盘"）的守卫。
 *
 * 四条判据各钉一件能被坏实现打破的事：
 * ① **每一个数都来自那条只读通道**：把 [HomeUsageReadout] 背后的四颗 `total*` 换成另一组，
 *    屏上文字跟着变；写死数字、或某颗读数没接进通道的实现必红。端口那第五颗 `totalRewriteCount`
 *    故意不在这一屏——摆一个 distinctive 的值，屏上多出一格就说明有人把小卡撑成仪表盘。
 * ② **规模是判据**：一块小卡（tag 恰 1 颗）、四格（`METRIC_CELL` 恰 4）、**只有一行**、
 *    四格全在那块卡的矩形内（别处不许再长一格统计）、体量不许超过一颗入口卡、
 *    **不可点**（不是第五颗按钮，也没并进主卡）。
 * ③ **纯映射逐颗对上读数**：换 readout 后每一格的字符串都跟着换；漏接某颗 ⇒ 那一格恒 0。
 * ④ **零成本护栏**：花费口径只指回共用判据 [costReadout]，首页没有第二处各算一遍钱。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeUsageCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /**
     * 只装这一块小卡需要的只读读数通道：把这一屏要画的那四颗 `total*` 摆出来，
     * 灯那三张派生表全走默认哑端口（这块读数与灯无关，见 `HomeStatusViewModel.usage` 的只读声明）。
     * [HomeStatusViewModel.returnedFromSubpage] 是唯一刷新这块读数的地方（一次本地读盘、不发请求）。
     */
    private fun viewModelWithUsage(store: InMemorySettingsStore): HomeStatusViewModel =
        HomeStatusViewModel(
            service = FakeHomeService(),
            provider = FakeHomeProvider(),
            knowledge = FakeHomeKnowledge(),
            probe = FakeHomeProbe(),
            store = store
        )

    private fun mount(vm: HomeStatusViewModel) {
        rule.setContent {
            UiMatrix(360, heightDp = 1000).RenderIn(LocalDensity.current.density) {
                HomeScreen(
                    homeStatus = vm,
                    onStartService = {},
                    onNavigateFeedback = {},
                    onNavigateProviders = {},
                    onNavigateCaptureApps = {},
                    onBack = {},
                    overlayGrantedOverride = true
                )
            }
        }
        rule.waitForIdle()
    }

    private fun countOfText(text: String): Int =
        rule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun tagCount(tag: String): Int =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun nodesOf(tag: String) =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    /** 带 tag 又带点击语义的节点数（统计被做成按钮那一族的探针） */
    private fun clickableWithTag(tag: String): Int =
        rule.onAllNodes(hasClickAction() and hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    // ═══════════ ① 每一个数都来自那条只读通道：换一组，屏上文字跟着变 ═══════════

    /**
     * 反例（坏实现会怎么红）：
     * - 界面把「0 次」写死（读数与屏幕脱钩）⇒ 第一组摆 7/3/2 时 `hasText("7 次")` 数不到；
     * - 某一颗读数没接进通道（比如只接了生成、复制那两颗没接）⇒ 换组之后那一句"要变成 9 次"红；
     * - 换第二组读数后屏上还留着第一组的数（通道没刷新）⇒ "旧数离场"那四句红；
     * - 花费没走 costReadout（把"没入过账"念成 0/免费）⇒ 第二组那句 `hasText("—")` 数不到；
     * - 把端口的 `totalRewriteCount` 也接成一格（撑回仪表盘那一版）⇒ 屏上多出「1 次」，那一句红。
     */
    @Test
    fun `every number on the card comes from the store readouts`() {
        val store = InMemorySettingsStore().apply {
            totalGenerateCount = 7
            totalCopyCount = 3
            totalAdoptCount = 2
            totalRewriteCount = 1      // 这一颗故意不进这一屏（摆一个看得出来的值当反向证人）
            totalCostYuan = 1.5
        }
        val vm = viewModelWithUsage(store)
        mount(vm)

        // 四格的值 = 四颗读数换算出来的串（生成/复制/采纳带单位「次」，花费两位小数）
        assertTrue("累计生成那格要念出读数 7，实到屏上没有「7 次」", countOfText("7 次") >= 1)
        assertTrue("复制那格要念出读数 3", countOfText("3 次") >= 1)
        assertTrue("采纳那格要念出读数 2", countOfText("2 次") >= 1)
        assertTrue("花费那格要念出 ￥1.50", countOfText("￥1.50") >= 1)
        assertEquals("改写那颗不在这一屏上：长出「1 次」就是又加了一格、回到大而复杂那一版",
            0, countOfText("1 次"))

        // 换一组读数：走同一条本地重读通道刷新，屏上文字必须整体跟着变
        store.totalGenerateCount = 42
        store.totalCopyCount = 9
        store.totalAdoptCount = 5
        store.totalCostYuan = 0.0   // 一笔可计价记录都没入过账 → costReadout 念「—」，不念 0
        rule.runOnIdle { vm.returnedFromSubpage(overlayGranted = true) }
        rule.waitForIdle()

        assertTrue("换组之后累计生成要变成 42 次", countOfText("42 次") >= 1)
        assertTrue("换组之后复制要变成 9 次", countOfText("9 次") >= 1)
        assertTrue("换组之后采纳要变成 5 次", countOfText("5 次") >= 1)
        assertTrue("花费没入过账时念「—」而不是 0（未知≠免费）", countOfText("—") >= 1)
        // 旧读数彻底离场：任何一处还留着上一组的数，就说明那一格没接读数通道（写死或只刷了一半）
        assertEquals("上一组的 7 次不该还在屏上", 0, countOfText("7 次"))
        assertEquals("上一组的 3 次不该还在屏上", 0, countOfText("3 次"))
        assertEquals("上一组的 2 次不该还在屏上", 0, countOfText("2 次"))
        assertEquals("上一组的 ￥1.50 不该还在屏上", 0, countOfText("￥1.50"))
    }

    // ═══════════ ② 规模判据：一块、一行、四格、不可点、体量不超过一颗入口卡 ═══════════

    /**
     * 反例（每一条都对应一种"又变回仪表盘"的坏法）：
     * - 长出第二块统计卡 / 在别处补一格 ⇒ `USAGE_CARD` 不再是 1，或那一格落在卡外（矩形那四句红）；
     * - 多一格读数（比如把改写接进来）⇒ `METRIC_CELL` 不再是 4；
     * - 排成两行/多列网格 ⇒ "只有一行"那句红（top 不再相等）；
     * - 撑成大块（加图例、加第二层说明、把格撑高）⇒ 体量那句红（超过一颗入口卡的高）；
     * - 把小卡做成第五颗按钮（加 `onClick` 或并进主卡）⇒ 可点数从 5 涨，且 tag+click 那两句红。
     */
    @Test
    fun `the usage card is one non-clickable block of exactly four cells`() {
        val store = InMemorySettingsStore()
        val vm = viewModelWithUsage(store)
        mount(vm)

        assertEquals("累计使用只许一块小卡", 1, tagCount(LbHomeTags.USAGE_CARD))
        val cells = nodesOf(LbTags.METRIC_CELL)
        assertEquals("这块小卡只许四格读数（生成/复制/采纳/花费）", 4, cells.size)

        // 整屏可点仍是 1 控件 + 4 入口：统计没被做成第五颗按钮
        val targets = probe.actionableTargets(rule, "首页·带统计小卡")
        assertEquals("统计小卡不可点：整屏仍是 5 颗可点，实到 " + targets.joinToString { it.describe() },
            5, targets.size)
        // 反向证人：不能靠"少掉一颗别的按钮"把总数抵回 5——直接问这一块自己有没有点击语义
        assertEquals("小卡自己不许带点击语义（它是读数，不是目的地）",
            0, clickableWithTag(LbHomeTags.USAGE_CARD))
        assertEquals("四格读数一颗都不许可点", 0, clickableWithTag(LbTags.METRIC_CELL))

        // 体量与形状：四格必须在**同一行**、全部落在这一块卡的矩形里
        val card = nodesOf(LbHomeTags.USAGE_CARD).first().boundsInRoot
        val firstTop = cells.first().boundsInRoot.top
        assertTrue(
            "四格只许排成一行（实到 top=${cells.map { it.boundsInRoot.top }}）：排成两行就是回到又大又复杂那一版",
            cells.all { kotlin.math.abs(it.boundsInRoot.top - firstTop) < 1f }
        )
        cells.forEachIndexed { i, cell ->
            val r = cell.boundsInRoot
            assertTrue("第 ${i + 1} 格必须住在这块小卡里（落在卡外就是别处又长了一格统计）：卡=$card 格=$r",
                r.left >= card.left - 1f && r.right <= card.right + 1f &&
                    r.top >= card.top - 1f && r.bottom <= card.bottom + 1f)
        }
        // "简洁小卡"的上限用同一棵树里那一颗入口卡当尺，不写死一个像素数：
        // 这块统计卡不许比一格入口还高，否则它已经是一块仪表盘了。
        val entry = nodesOf(LbHomeTags.ENTRY_KNOWLEDGE).first().boundsInRoot
        val cardHeightDp = (card.bottom - card.top) / density
        val entryHeightDp = (entry.bottom - entry.top) / density
        assertTrue(
            "这块统计小卡的高度不许超过一颗入口卡（小卡 ${cardHeightDp.toInt()}dp / 入口 ${entryHeightDp.toInt()}dp）" +
                "——§五 取舍③ 的上限是『一块简洁的小卡，不是大而复杂的仪表盘』",
            cardHeightDp <= entryHeightDp
        )
    }

    // ═══════════ ③ 纯映射逐颗对上读数 + 花费共用口径（JVM 直判，坏映射必红） ═══════════

    /**
     * 直接判 [homeUsageMetrics] 这颗纯映射：四格的值必须由 [HomeUsageReadout] 的四颗读数换算出来。
     * 反例：写死任一格 → 换 readout 后对应那句对不上；漏接某颗读数 → 那一格恒 0；
     * 两颗读数接错格子（把复制念成生成）→ 整串 `LbMetric` 对不上；
     * 花费没走 costReadout → "没入过账" 念不成「—」。
     */
    @Test
    fun `homeUsageMetrics maps each cell to its own store readout`() {
        val readout = HomeUsageReadout(
            totalGenerateCount = 11,
            totalCopyCount = 4,
            totalAdoptCount = 6,
            totalCostYuan = 2.34
        )
        val metrics = metricsOf(readout)
        assertEquals("只有四格", 4, metrics.size)
        assertEquals(LbMetric(app.getString(R.string.home_usage_generated), "11 次"), metrics[0])
        assertEquals(LbMetric(app.getString(R.string.home_usage_copied), "4 次"), metrics[1])
        assertEquals(LbMetric(app.getString(R.string.home_usage_adopted), "6 次"), metrics[2])
        assertEquals(LbMetric(app.getString(R.string.home_usage_cost), "￥2.34"), metrics[3])

        // 花费未知档：一笔都没入过账 → 复用 costReadout 交回「—」，绝不念成 0/免费
        assertEquals("—", metricsOf(readout.copy(totalCostYuan = 0.0))[3].value)
        // 不足一分档：与 costReadout 同一条口径（不写 0）
        assertEquals(
            app.getString(R.string.cost_below_cent, "￥"),
            metricsOf(readout.copy(totalCostYuan = 0.005))[3].value
        )
    }

    private fun metricsOf(readout: HomeUsageReadout): List<LbMetric> = homeUsageMetrics(
        readout = readout,
        generatedLabel = app.getString(R.string.home_usage_generated),
        copiedLabel = app.getString(R.string.home_usage_copied),
        adoptedLabel = app.getString(R.string.home_usage_adopted),
        costLabel = app.getString(R.string.home_usage_cost),
        countUnit = { n -> String.format(app.getString(R.string.home_usage_count), n) },
        costUnknown = app.getString(R.string.cost_unknown),
        costBelowCent = app.getString(R.string.cost_below_cent, "￥")
    )

    /**
     * 零成本护栏：这块读数的花费口径只指回 [costReadout] 那颗共用判据，
     * 首页这一格没有第二处各算一遍钱（纯映射输出的花费值 == 现算的 costReadout 值）。
     */
    @Test
    fun `the card reuses the one cost path`() {
        val yuan: (Double) -> String = { v ->
            "￥" + String.format(java.util.Locale.getDefault(), "%.2f", kotlin.math.abs(v))
        }
        val viaShared = costReadout(2.34, "—", app.getString(R.string.cost_below_cent, "￥"), yuan)
        assertEquals("花费那一格走的就是共用判据 costReadout 那一条口径", "￥2.34", viaShared)
        assertEquals(metricsOf(HomeUsageReadout(totalCostYuan = 2.34))[3].value, viaShared)
    }
}
