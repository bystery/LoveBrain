package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbMetricGrid
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.math.max

/**
 * §6.5 第 6 条的极端值矩阵（指导书 527 行）：**"长 Provider 名、超长模型名、￥9999.999、100000 次生成"**。
 *
 * 为什么要有这一格（2026-09-26 实测）：这一条**全仓一个字符都没有**——
 * `grep -rl "9999\|100000" app/src/test app/src/androidTest` 空；
 * 只有 `ProviderSectionSemanticsTest` 用了长供应商名，钱与次数那两档从来没人喂过。
 * 而这两个值恰好踩在两条不同的格式化链上：
 * `HomeScreen:215` 与 `UsageDetailScreen:60` 写 `"￥" + String.format("%.2f", …)`（**没带 Locale**），
 * 面板 `LoveBrainPanelScreen:1025` 走 `LoveBrainViewModel.formatYuan` = `String.format(Locale.US, "%.3f", …)`。
 *
 * 判的三句话（都是能被反例打破的，不是"画得出来"）：
 * 1. **值没被裁**——两半：每格读回来的那组文本在最坏一格（320dp + 2.0 倍字）与最宽一格**逐字相同**、
 *    且不含省略号字符；**加上**一条几何判据：同一颗组件在不受宽度限制的控制组里量到"这条文本一行要多宽"，
 *    被测槽位放不下却不给出对应行数就是被裁。为什么非要控制组：注入 `maxLines = 1 + Ellipsis` 那一发
 *    回来是 **NO-TEETH**——**语义树在被裁成省略号时仍报完整字符串**，"串没变"根本不是"没被裁"。
 * 2. **三颗格子互不重叠、宽度仍是三等分**（判的是**格子自己**的宽度——第一版误拿了文本的自然宽去判，
 *    三颗值的字数本来就不同，那条判据当时就红在没发生过的话上）；
 * 3. **热区没被挤小**：承载这三颗值的那张可点卡片，12 格里都 ≥48dp。
 *
 * ⚠ 这一格还顺带量到一件事但**按用户口径没修**：逗号制语言下首页那颗花费会念成「￥10000,00」，
 * 而面板那条锁 `Locale.US` ⇒ 两台页面对同一笔钱念两个数。判据与两处生产改动都已撤掉，
 * 只把读数记进账本 §61.4，**不当已修**。
 *
 * ⚠ 矩阵换格靠**改 hoisted 值**，一个用例只 `setContent` 一次（坑表 3）；
 * 每格必须数到 3 颗格子、每格至少两条文本，数不到就抛——"读不到数"不许当"没问题"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UsageExtremeValuesSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 指导书点名的两个极端值 */
    private val extremeGenerations = 100000
    private val extremeCostYuan = 9999.999

    /** 控制组容器（同一颗组件、宽度不受限）的锚点，只在这一个文件里用 */
    private val CONTROL_TAG = "lb_extreme_control"

    @After
    fun tearDown() {
        FloatingService.setWindowState(FloatingService.WindowState.STOPPED)
    }

    // ═══════════ 挂载 ═══════════

    private fun mountGrid(cell: MutableState<UiMatrix>) {
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                LbMetricGrid(
                    totalGenerate = "$extremeGenerations",
                    totalCost = "￥10000.00",
                    adoptRate = "100%",
                    onClick = {}
                )
            }
        }
        rule.waitForIdle()
    }

    /**
     * 被测那一颗 + 一份**自然宽控制组**（同一颗组件、同一字号、宽度不受限）。
     *
     * 为什么必须有控制组：P1 那一发（给值加 `maxLines = 1 + overflow = Ellipsis`）回来的是
     * **NO-TEETH**——Compose 的语义树在被裁成省略号时**仍报完整字符串**，
     * 所以"跨格文本恒等 + 找省略号字符"这种判据看不见这种裁法。
     * 能看见的是几何：控制组量到"这条文本一行要多宽"，被测格子的槽宽放不下就必须给出对应行数；
     * 只给一行高 = 被裁了。
     */
    private fun mountGridWithControl(cell: MutableState<UiMatrix>) {
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                LbMetricGrid(
                    totalGenerate = "$extremeGenerations",
                    totalCost = "￥10000.00",
                    adoptRate = "100%",
                    onClick = {}
                )
                Box(Modifier.testTag(CONTROL_TAG).requiredWidth(1200.dp)) {
                    LbMetricGrid(
                        totalGenerate = "$extremeGenerations",
                        totalCost = "￥10000.00",
                        adoptRate = "100%",
                        onClick = null
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun stubViewModel(): SetupViewModel =
        mockk<SetupViewModel>(relaxed = true).also {
            every { it.activeTicket } returns MutableStateFlow(
                ProviderTicket(
                    name = "TICKET_NAME_SENTINEL",
                    baseUrl = "https://example.test/v1",
                    model = "MODEL_SENTINEL"
                )
            )
            every { it.providerReady } returns MutableStateFlow(true)
            every { it.captureEnabled } returns MutableStateFlow(false)
            every { it.captureAllowedPackages } returns MutableStateFlow(emptySet())
            every { it.isCaptureServiceEnabled(any()) } returns false
            every { it.totalGenerateCount } returns extremeGenerations
            every { it.totalCopyCount } returns extremeGenerations
            every { it.totalAdoptCount } returns extremeGenerations
            every { it.totalRewriteCount } returns extremeGenerations
            every { it.totalCostYuan } returns extremeCostYuan
            every { it.adoptRate } returns 1.0f
        }

    private fun mountHome(cell: MutableState<UiMatrix>, viewModel: SetupViewModel) {
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                HomeScreen(
                    viewModel = viewModel,
                    onStartService = {}, onOpenPanel = { _, _ -> }, onTempHide = {},
                    onRestore = {}, onNavigateFeedback = {}, onNavigateAbout = {},
                    onNavigateProviders = {}, onNavigateUsage = {}, onNavigateCaptureApps = {},
                    onBack = {},
                    overlayGrantedOverride = true,
                    serviceRunningOverride = false
                )
            }
        }
        rule.waitForIdle()
    }

    private fun mountUsageDetail(cell: MutableState<UiMatrix>, viewModel: SetupViewModel) {
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                UsageDetailScreen(viewModel = viewModel, onBack = {})
            }
        }
        rule.waitForIdle()
    }

    // ═══════════ 读数 ═══════════

    /** 一颗格子里的一条文本：自然几何读数 */
    private data class TextBox(val text: String, val widthDp: Float, val heightDp: Float)

    /** 一颗格子：格子自己的左边界与宽度（dp）+ 它子树里全部文本 */
    private data class Cell(val leftDp: Float, val widthDp: Float, val boxes: List<TextBox>) {
        val texts: List<String> get() = boxes.map { it.text }
        fun describe() = "宽${widthDp.toInt()}dp@${leftDp.toInt()} " + boxes.joinToString("/", "「", "」") {
            "${it.text}(${it.widthDp.toInt()}x${it.heightDp.toInt()})"
        }
    }

    /** 一颗可点节点的尺寸（dp） */
    private data class Box(val widthDp: Float, val heightDp: Float, val text: String)

    private fun boundsOf(node: SemanticsNode): Box {
        val b = node.boundsInRoot
        return Box(
            b.width / density, b.height / density,
            node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text ?: ""
        )
    }

    /** 这棵子树里所有**带文本的节点**（未合并树里一颗 `Text` 就是一个节点） */
    private fun textNodesUnder(node: SemanticsNode): List<SemanticsNode> {
        val mine = if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.isNotBlank() } == true) {
            listOf(node)
        } else {
            emptyList()
        }
        return mine + node.children.flatMap { textNodesUnder(it) }
    }

    private fun cellOf(node: SemanticsNode): Cell {
        val b = node.boundsInRoot
        return Cell(
            leftDp = b.left / density,
            widthDp = b.width / density,
            boxes = textNodesUnder(node).map { n ->
                val nb = n.boundsInRoot
                TextBox(
                    n.config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text },
                    nb.width / density, nb.height / density
                )
            }
        )
    }

    private fun taggedNodes(anchor: String) =
        rule.onAllNodesWithTag(anchor, useUnmergedTree = true).fetchSemanticsNodes()

    /**
     * 读那三颗格子。
     *
     * 第一版这里想"取每格里最靠上那条文本当值"，320dp + 2.0 倍字那一格立刻选错
     * （报回来的是「累计生成」这种标签）。判"值有没有被裁"**不该靠猜哪条是值**：
     * 现在把每格的全部文本连同各自的宽高原样读回来。
     * 数不到 3 格、某格文本不足两条，一律抛——静默少读就是假绿。
     */
    private fun readCells(anchor: String): List<Cell> {
        val nodes = taggedNodes(anchor)
        check(nodes.size == 3) { "$anchor 数到 ${nodes.size} 颗格子，不是 3 颗——这把尺在这一格里是瞎的" }
        return nodes.map(::cellOf).sortedBy { it.leftDp }.also { read ->
            read.forEach { check(it.boxes.size >= 2) { "$anchor 的一格里文本不足两条：${it.describe()}" } }
        }
    }

    /**
     * 把被测那三颗与**控制组**那三颗分开。
     *
     * 控制组就是同一颗 `LbMetricGrid`，只是套在 `requiredWidth(1200dp)` 里 ⇒
     * 同一字号下每条文本都只排一行，量到的宽度就是它的**自然宽**。
     * 分桶靠 testTag 的子树归属，不靠坐标猜；两桶各 3 颗，不是就抛。
     */
    private fun splitControlled(anchor: String): Pair<List<Cell>, List<Cell>> {
        val controls = taggedNodes(CONTROL_TAG)
        check(controls.size == 1) { "控制组容器数到 ${controls.size} 颗，要恰好 1 颗" }
        val underControl = collectTagged(controls.single(), anchor).map { it.id }.toSet()
        val all = taggedNodes(anchor)
        check(all.size == 6) { "被测 + 控制组一共数到 ${all.size} 颗格子，要 6 颗" }
        val ctl = all.filter { it.id in underControl }.map(::cellOf).sortedBy { it.leftDp }
        val probed = all.filter { it.id !in underControl }.map(::cellOf).sortedBy { it.leftDp }
        check(ctl.size == 3 && probed.size == 3) { "分桶分坏了：被测 ${probed.size}、控制 ${ctl.size}" }
        check(ctl.minOf { it.widthDp } > probed.maxOf { it.widthDp }) {
            "控制组的格子不比被测的宽，自然宽参照就是假的：ctl=${ctl.map { it.widthDp.toInt() }} " +
                "probed=${probed.map { it.widthDp.toInt() }}"
        }
        return probed to ctl
    }

    private fun collectTagged(node: SemanticsNode, tag: String): List<SemanticsNode> {
        val mine = if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) listOf(node) else emptyList()
        return mine + node.children.flatMap { collectTagged(it, tag) }
    }

    /** 包住这三颗格子的可点卡片（取"装得下三颗格子的那一颗里最小的"） */
    private fun actionableAround(anchor: String): Box {
        val cells = rule.onAllNodesWithTag(anchor, useUnmergedTree = true).fetchSemanticsNodes()
        check(cells.isNotEmpty()) { "$anchor 找不到格子，热区那一条就没有证人" }
        val centers = cells.map { it.boundsInRoot }.map { (it.left + it.right) / 2f to (it.top + it.bottom) / 2f }
        val candidates = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val b = node.boundsInRoot
            centers.all { (x, y) -> b.left <= x && x <= b.right && b.top <= y && y <= b.bottom }
        }
        check(candidates.isNotEmpty()) { "$anchor 周围找不到任何可点节点——热区判据在这一格是空的" }
        return boundsOf(candidates.minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }!!)
    }

    // ═══════════ 判据 ═══════════

    private fun allTexts(cells: List<Cell>) = cells.flatMap { it.texts }

    private fun assertCellsIntact(cells: List<Cell>, where: String, matrix: UiMatrix) {
        val overlaps = mutableListOf<String>()
        for (i in cells.indices) for (j in cells.indices) if (i < j) {
            val a = cells[i]; val b = cells[j]
            val shared = minOf(a.leftDp + a.widthDp, b.leftDp + b.widthDp) - maxOf(a.leftDp, b.leftDp)
            if (shared > 0.5f) overlaps += "${a.describe()} × ${b.describe()}"
        }
        assertTrue("$where 在 ${matrix.id} 有三颗格子互相压上了（长串把 weight(1f) 挤垮）：\n" +
            overlaps.joinToString("\n"), overlaps.isEmpty())

        val widths = cells.map { it.widthDp }
        assertTrue(
            "$where 在 ${matrix.id} 不是三等分了，格子宽度实到 ${widths.map { it.toInt() }}",
            widths.max()!! - widths.min()!! <= 1f
        )

        val cut = allTexts(cells).filter { it.contains("…") || it.endsWith("...") }
        assertTrue("$where 在 ${matrix.id} 出现了被截断的文本：\n" + cut.joinToString("\n"), cut.isEmpty())
    }

    /** 12 格全跑一遍三句话（完整、不重叠+三等分、热区），并把每格读数带上 */
    private fun sweep(cells: List<Cell>, where: String, matrix: UiMatrix, readings: LinkedHashMap<String, List<String>>) {
        readings[matrix.id] = allTexts(cells)
        assertCellsIntact(cells, where, matrix)
    }

    private fun assertIdentity(readings: LinkedHashMap<String, List<String>>, where: String) {
        val reference = readings.getValue(UiMatrix.FULL.first().id)
        readings.forEach { (id, read) ->
            assertEquals(
                "$where 在 $id 读到的文本与最宽一格不一样（12 格全读数：$readings）",
                reference, read
            )
        }
    }

    // ═══════════ 断言 ═══════════

    @Test
    fun `the metric grid keeps three extreme values intact across the whole matrix`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountGrid(cell)
        val readings = LinkedHashMap<String, List<String>>()
        for (matrix in UiMatrix.FULL) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            sweep(readCells(LbTags.METRIC_CELL), "LbMetricGrid", matrix, readings)
        }
        assertIdentity(readings, "LbMetricGrid")
        val reference = readings.getValue(UiMatrix.FULL.first().id)
        assertTrue("组件层没读到指导书那两个极端串：$reference",
            "$extremeGenerations" in reference && "￥10000.00" in reference)
    }

    /**
     * **这一格是 P1 那一发逼出来的**：给三颗值加上 `maxLines = 1 + overflow = Ellipsis` 之后，
     * 前面那些判据（跨格文本恒等、找省略号字符、不重叠、三等分）**一格都没红**——
     * Compose 的语义树在被裁成省略号时仍然把完整字符串报给你，"串没变"根本不是"没被裁"。
     *
     * 能看见裁切的只有几何：控制组（同一颗组件、同一字号、`requiredWidth(1200dp)`）量到
     * "这条文本排一行要多宽"；被测槽位放不下时（自然宽 > 槽宽）文本就必须给出对应的行数。
     * 行数不够 ⇒ 有数字看不见 ⇒ 红。0.8 这个系数是给行高留的余量（字体行距不是整数倍），
     * 它只会让判据**更宽**，不会让它看不见。
     */
    @Test
    fun `no extreme value gets traded for fewer lines at any matrix cell`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountGridWithControl(cell)
        for (matrix in UiMatrix.FULL) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            val (probed, control) = splitControlled(LbTags.METRIC_CELL)
            val natural = HashMap<String, TextBox>()
            control.flatMap { it.boxes }.forEach { box ->
                val prev = natural.put(box.text, box)
                check(prev == null || (prev.widthDp - box.widthDp).toInt() == 0) {
                    "控制组里同一条文本量到两个自然宽，参照不可信：$prev vs $box"
                }
            }
            val clipped = mutableListOf<String>()
            probed.forEach { c ->
                c.boxes.forEach { b ->
                    val ref = natural[b.text] ?: error("控制组里没有「${b.text}」这一条，参照是空的")
                    val slotWidth = max(c.widthDp, 1f)
                    // "要不要多行"留 2dp 容差：这不是拍脑袋放宽——第一版没容差时它红在
                    // 360dp+1.3 倍字那一格，自然宽 110dp、槽宽 109dp，一行确实排得下（实测读数）。
                    val lines = if (ref.widthDp <= slotWidth + 2f) 1f
                    else ceil((ref.widthDp - 0.5) / slotWidth).toFloat()
                    val required = 0.8f * lines * ref.heightDp
                    if (b.heightDp < required) {
                        clipped += "${matrix.id} 「${b.text}」槽宽 ${c.widthDp.toInt()}dp，" +
                            "一行要 ${ref.widthDp.toInt()}dp 宽 ⇒ 需要 ${lines.toInt()} 行，" +
                            "实到高 ${b.heightDp.toInt()}dp（一行高 ${ref.heightDp.toInt()}dp）"
                    }
                }
            }
            assertTrue(
                "极端值在这个格子里被裁掉了行（用户就看不见那几位数字）：\n" +
                    clipped.joinToString("\n") + "\n  格子读数：" + probed.joinToString(" | ") { it.describe() },
                clipped.isEmpty()
            )
        }
    }

    @Test
    fun `the metric card keeps a 48dp touch floor across the whole matrix`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountGrid(cell)
        for (matrix in UiMatrix.FULL) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            val card = actionableAround(LbTags.METRIC_CELL)
            assertTrue(
                "承载三颗值的那张可点卡片在 ${matrix.id} 只有 ${card.widthDp.toInt()}x${card.heightDp.toInt()}dp，" +
                    "低于 ${probe.floorDp.toInt()}dp（值变长把热区挤小了）",
                card.widthDp + 0.5f >= probe.floorDp && card.heightDp + 0.5f >= probe.floorDp
            )
        }
    }

    @Test
    fun `home shows the extreme usage numbers in full across the whole matrix`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountHome(cell, stubViewModel())
        val readings = LinkedHashMap<String, List<String>>()
        for (matrix in UiMatrix.FULL) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            sweep(readCells(LbTags.METRIC_CELL), "首页·使用概览", matrix, readings)
        }
        assertIdentity(readings, "首页·使用概览")
        val reference = readings.getValue(UiMatrix.FULL.first().id)
        assertTrue("首页没读到 100000 次那一颗：$reference", "$extremeGenerations" in reference)
        assertTrue("首页花费串不像钱：$reference", reference.any { it.startsWith("￥") || it.startsWith("¥") })
    }

    @Test
    fun `the home usage card keeps a 48dp touch floor across the whole matrix`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountHome(cell, stubViewModel())
        for (matrix in UiMatrix.FULL) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            val card = actionableAround(LbTags.METRIC_CELL)
            assertTrue(
                "首页「使用概览」那张卡在 ${matrix.id} 的热区只有 " +
                    "${card.widthDp.toInt()}x${card.heightDp.toInt()}dp，低于 ${probe.floorDp.toInt()}dp",
                card.widthDp + 0.5f >= probe.floorDp && card.heightDp + 0.5f >= probe.floorDp
            )
        }
    }

    @Test
    fun `usage detail keeps every stat row when the numbers are extreme`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountUsageDetail(cell, stubViewModel())
        for (matrix in UiMatrix.FULL) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                .fetchSemanticsNodes().flatMap {
                    it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { a -> a.text }
                }
            // 六行一行都不许少：最后一行「采用率」离视口顶端最远，最先被顶出去
            listOf("生成次数", "复制次数", "采用次数", "改写次数", "累计花费", "采用率").forEach { row ->
                assertTrue("详情页在 ${matrix.id} 少了一行「$row」，实到文本 $texts", texts.any { it.contains(row) })
            }
            assertTrue("详情页在 ${matrix.id} 没把 100000 次念全：$texts", texts.any { it.contains("$extremeGenerations") })
        }
    }

    /*
     * 撤掉的一格（读数留档，不留在代码里当守卫）：
     * 同一笔钱在两台页面上本来应该念同一个数——首页与详情页写 `String.format("%.2f", …)`（没带 Locale），
     * 面板那条锁 `Locale.US`（`LoveBrainViewModel.formatYuan`）。本机实测：默认语言换成逗号制时
     * 首页那颗变成「￥10000,00」。2026-09-26 用户判"这条不在开工包范围内，先别动生产码"
     * ⇒ 判据与两处 `Locale.US` 一并撤掉，只把读数记进账本 §61.4，**不当已修**。
     */
}
