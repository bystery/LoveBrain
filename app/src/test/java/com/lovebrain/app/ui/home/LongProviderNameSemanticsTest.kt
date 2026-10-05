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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbRowState
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 第6节第5条 第⑦栏「中英文 + 超长名」：**钉住生产今天的实情——长名字被静默裁在一行里，不给省略号，也没有第二处能看全名。**
 *
 * 两处落点（首页那条"当前供应商 · 当前模型"的行随首页重做删掉了，长名现在只在供应商页里念；
 * 两处都只写 `maxLines = 1` 而**不写 `overflow`** ⇒ Material3 默认档 `TextClip`：
 * 多出来那一截直接切掉，屏幕上既没有"…"）：
 * - 卡片标题 `ui/home/ProviderSection.kt:160-166`（`activeTicket?.name`，`maxLines` 在 **:165**）；
 * - 展开后的列表行 `ui/home/ProviderSection.kt:230-236`（`t.name`，`maxLines` 在 **:235**）；
 * 会说全名的只剩删除确认标题 `ProviderSection.kt:284` 与表单那颗名称输入框 `:394`，两者都不是"浏览"面；面板头部不画供应商名。
 *
 * 这条路是真路：`viewmodel/SetupViewModel.kt:216` 保存名字只判 `isBlank()`，**没有任何长度上限**。
 *
 * **判据只认几何，不认文本相等**（本仓库记过坑：语义树在被裁掉的那一截上仍报**完整字符串**，
 * 所以"念出来的串没变""串里有没有 …"都看不见裁切）。控制组（同 `LbMetricGrid` 那族用的自然宽参照）：
 * 同一棵页面/同一颗组件挂在 `requiredWidth(2400dp)` 的盒子里当第二遍，两遍在同一个 `RenderIn` 内 ⇒ **同一把字号**，
 * 第二遍量到的就是"这条名字排一行要多宽"；被测槽位比它窄、而高度只有一行 ⇒ 被裁了。
 *
 * ⚠ 列表行那处的参照**挂不出来**：那一行只在卡片内部 `remember` 的 `expanded` 为真时才存在，而带点击的卡片在
 * 2400dp 那一遍里几何中心落在窗口外（白名单只有 `performClick()`，坐标手势被明令禁止）⇒ 换成同字号四档宽度的
 * **单调上涨**论证：`宽(w) = min(自然宽, 槽(w))` 且 `槽(w) = w − 常数`，所以宽度随格子涨的那些格子里，比最宽那格窄的每一格
 * 都**必然**没排完（推出来的，不是估的）；"只给一行高"的尺用**同一行换短名字在同一格量到的高度**，不抄版式常数。
 *
 * ⚠ 判不到、如实交出去的两件事（不靠加判据"看起来已修"）：① `TextClip` 换成 `Ellipsis` 时语义串与几何**都不变**，
 * 只有像素能分别 ⇒ 那时本文件仍会绿，要红得靠截图基线那一格；② 列表行最宽那一格（600dp）排没排完，白名单量不到。
 *
 * 换格靠改 hoisted 值，一个用例只 `setContent` 一次（坑表 3）；每格都必须数到名字节点、参照与右邻，数不到就抛——
 * "读不到数"不许当"没问题"（`named()` 数不到 = 生产把串改短了或压根没展开，当场红）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LongProviderNameSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 那一栏要"中英文 + 超长名"：一符一宽与一符半宽走的是两条排字路径 */
    private val longZh = "深圳市南山区海岸城那个把心动写成诗的长期合作伙伴服务商"
    private val longEn = "Shenzhen Nanshan Coastal City LoveBrain Gateway Partner"
    private val extraLong = longZh + "第二批接入的海外网关与本地部署实例"
    private val shortName = "DeepSeek"
    private val otherName = "备用供应商"
    private val model = "deepseek-chat"

    private val activeFlow = MutableStateFlow<ProviderTicket?>(null)
    private val ticketsFlow = MutableStateFlow<List<ProviderTicket>>(emptyList())

    @After
    fun tearDown() {
        FloatingService.setWindowState(FloatingService.WindowState.STOPPED)
    }

    // ═══════════ 挂载 ═══════════

    private data class Spec(val matrix: UiMatrix, val name: String)

    private fun stubViewModel(): SetupViewModel =
        mockk<SetupViewModel>(relaxed = true).also {
            every { it.tickets } returns ticketsFlow
            every { it.activeTicket } returns activeFlow
            every { it.providerReady } returns MutableStateFlow(true)
            every { it.captureEnabled } returns MutableStateFlow(true)
            every { it.captureAllowedPackages } returns MutableStateFlow(setOf("com.a"))
            every { it.isCaptureServiceEnabled(any()) } returns true
        }

    /**
     * 被测那一遍在矩阵槽位里；`withControl` 那一遍同一棵页面套进 `requiredWidth(2400.dp)` 当自然宽参照。
     * 两遍在同一个 `RenderIn` 内 ⇒ 同一把字号，否则量的不是同一种字。
     * 列表行那一格关掉参照：控制组那一遍没法展开（见文件头那句 ⚠），挂着只会让每格多走一遍树。
     */
    private fun mountSection(spec: MutableState<Spec>, viewModel: SetupViewModel, withControl: Boolean = true) {
        rule.setContent {
            spec.value.matrix.RenderIn(LocalDensity.current.density) {
                Box(Modifier.testTag(PROBED_TAG)) { ProviderSection(viewModel = viewModel, onBack = {}) }
                if (withControl) {
                    Box(Modifier.testTag(CONTROL_TAG).requiredWidth(CONTROL_WIDTH)) {
                        ProviderSection(viewModel = viewModel, onBack = {})
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun ticket(name: String, id: String) = ProviderTicket(
        id = id, name = name, baseUrl = "https://example.test/v1", model = model, models = listOf(model)
    )

    /** 换格：矩阵档位与名字都走 hoisted 值。两个桶的名字必须同时换，否则参照与判据量的不是同一条串 */
    private fun setCell(spec: MutableState<Spec>, matrix: UiMatrix, titleName: String, rowName: String? = null) {
        rule.runOnIdle {
            activeFlow.value = ticket(titleName, "active")
            ticketsFlow.value = listOfNotNull(rowName?.let { ticket(it, "t1") })
            spec.value = Spec(matrix, titleName)
        }
        rule.waitForIdle()
    }

    /**
     * 点开卡片（`expanded` 是页面内部的 `remember`，没有旋钮，只能真点）。
     * 别按 index 点：这一页第一个可点击节点是页头返回钮，点了它整页什么都不发生（`ProviderSectionSemanticsTest` 踩过）。
     */
    private fun expandCard() {
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "供应商区一个可点击节点都没有，展开这一格是空的" }
        val widest = nodes.withIndex().maxByOrNull { (_, n) -> n.boundsInRoot.width }!!.index
        rule.onAllNodes(hasClickAction())[widest].performClick()
        rule.waitForIdle()
    }

    // ═══════════ 读数（从 testTag 桶往下走，不用 onRoot） ═══════════

    private class GNode(
        val id: Int, val tag: String?, val left: Float, val top: Float,
        val right: Float, val bottom: Float, val text: String, val clickable: Boolean,
        /** 这一颗自己或它的某一颗祖先挂着 [LbTags.SETTING_ROW]（= 它是设计系统那颗行画的） */
        val inSettingRow: Boolean
    ) {
        val widthDp: Float get() = right - left
        val heightDp: Float get() = bottom - top
        fun laid() = widthDp > 0f && heightDp > 0f
        fun sameLine(o: GNode): Boolean {
            val overlap = minOf(bottom, o.bottom) - maxOf(top, o.top)
            return overlap > 0.5f && overlap >= 0.5f * minOf(heightDp, o.heightDp)
        }

        fun describe() = "「${text.take(24)}」 ${widthDp.toInt()}x${heightDp.toInt()}dp @(${left.toInt()},${top.toInt()})"
    }

    /** 从这个 testTag 桶往下走整棵（未合并）子树——`onRoot` 不在白名单里，桶自己就是根 */
    private fun bucket(tag: String): List<GNode> {
        val roots = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        check(roots.size == 1) { "$tag 这个桶的容器数到 ${roots.size} 颗，要恰好 1 颗" }
        val clickable = rule.onAllNodes(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.id }.toSet()
        val out = ArrayList<GNode>()
        // 第二枚标记：走到这一颗时，父链上有没有出现过那颗设置行的 tag（tag 不往子节点传，
        // 所以要自己沿树带下去——"这条文本住在哪颗组件里"只能靠父链说）。
        val queue = ArrayDeque<Pair<SemanticsNode, Boolean>>()
        queue.addLast(roots.single() to false)
        while (queue.isNotEmpty()) {
            val (node, ancestorHasRowTag) = queue.removeFirst()
            val nodeTag = node.config.getOrNull(SemanticsProperties.TestTag)
            val inRow = ancestorHasRowTag || nodeTag == LbTags.SETTING_ROW
            val b = node.boundsInRoot
            out += GNode(
                id = node.id, tag = nodeTag,
                left = b.left / density, top = b.top / density, right = b.right / density,
                bottom = b.bottom / density,
                text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: "",
                clickable = node.id in clickable,
                inSettingRow = inRow
            )
            node.children.forEach { queue.addLast(it to inRow) }
        }
        return out
    }

    /**
     * 桶里文本**恰好**等于 [text] 的那一颗。读不到就是红：生产把名字 `take(8)` 掉、或者分桶读坏了、
     * 或者那一行压根没展开，三种都不许悄悄过去。0x0（被滚动压掉，坑表 90）与"没这条"要分开说。
     * 「语义树仍报整条名字」这一半的证人就是这一句：按整条串能找到这颗节点 = 被裁的那一截仍在串里。
     */
    private fun List<GNode>.named(text: String, where: String): GNode {
        val all = filter { it.text == text }
        val hits = all.filter { it.laid() }
        check(hits.size == 1) {
            "$where 里「${text.take(24)}」数到 ${all.size} 颗，摆出来的 ${hits.size} 颗，要恰好 1 颗" +
                "（0x0 = 被压掉了，不是没这条）。整桶读数：" +
                joinToString(" | ") { it.describe() }.take(1200)
        }
        return hits.single()
    }

    // ═══════════ 判据 ═══════════

    /**
     * 一格两句话（被测槽 vs 控制组自然宽，同一把字号）：
     * 槽比自然窄 ⇒ **今天被裁在一行里**（两行高就说明有人改成换行了，这条记录要重立，本轮必须跟着改）；
     * 槽够 ⇒ 完整排出，且两遍必须量到同一个高度——"参照与被测是同一种字"这件事只有这一个证人。
     */
    private fun judgeAgainstControl(
        where: String, matrix: UiMatrix, probed: GNode, natural: GNode, silentClip: MutableList<String>
    ) {
        val need = natural.widthDp
        val got = probed.widthDp
        if (need > got + 2f) {
            assertTrue(
                "$where 在 ${matrix.id}：这条名字排一行要 ${need.toInt()}dp（控制组 ${natural.describe()}），" +
                    "被测槽位只给它 ${got.toInt()}dp，而实到高 ${probed.heightDp.toInt()}dp —— 不再是一行高" +
                    "（一行 = ${natural.heightDp.toInt()}dp）⇒ 本文件钉的「生产今天把它裁在一行里」失效，判据要跟着重立",
                probed.heightDp < 1.8f * natural.heightDp
            )
            silentClip += "${matrix.id}（槽 ${got.toInt()}dp / 一行要 ${need.toInt()}dp，语义仍报整条串）"
        } else {
            assertTrue(
                "$where 在 ${matrix.id}：名字排到 ${got.toInt()}dp，比它一行需要的 ${need.toInt()}dp 还宽，" +
                    "多出来的地方不知道从哪来（控制组 ${natural.describe()}）",
                got <= need + 2f
            )
            val gap = maxOf(probed.heightDp, natural.heightDp) - minOf(probed.heightDp, natural.heightDp)
            assertTrue(
                "$where 在 ${matrix.id}：这一格名字排得下（${got.toInt()}dp ≥ 需要的 ${need.toInt()}dp），" +
                    "但被测那一遍高 ${probed.heightDp.toInt()}dp、控制组那一遍高 ${natural.heightDp.toInt()}dp —— " +
                    "两遍量的不是同一种字，自然宽参照作废",
                gap <= 1f
            )
        }
    }

    /**
     * 长名字不许压到同一行的邻居（那句"不叠在一起"），并且这一行的可点热区——包住名字的宿主
     * 与名字右侧那些动作——仍够 [SemanticsProbe.floorDp]。判的是带点击的那一颗自己，不是外层框。
     */
    private fun judgeNeighbours(
        where: String, matrix: UiMatrix, nodes: List<GNode>, name: GNode, expectRowActions: Boolean
    ) {
        val right = nodes.filter { it.id != name.id && it.laid() && it.left > name.left + 1f && it.sameLine(name) }
        val crossed = right.filter { minOf(name.right, it.right) - maxOf(name.left, it.left) > 0.5f }
        assertTrue(
            "$where 在 ${matrix.id}：长名字与同一行的邻居叠上了 →\n" +
                crossed.joinToString("\n") { "  " + it.describe() } + "\n  名字：${name.describe()}",
            crossed.isEmpty()
        )
        val cx = (name.left + name.right) / 2f
        val cy = (name.top + name.bottom) / 2f
        val holder = nodes.filter {
            it.clickable && it.laid() && it.left <= cx && cx <= it.right && it.top <= cy && cy <= it.bottom
        }
        check(holder.isNotEmpty()) {
            "$where 在 ${matrix.id}：名字那一行读不到任何带点击的宿主，热区判据在这一格是空的：" +
                nodes.joinToString(" | ") { it.describe() }
        }
        val actions = right.filter { it.clickable }
        if (expectRowActions) {
            check(actions.isNotEmpty()) {
                "$where 在 ${matrix.id}：名字右侧读不到任何可点邻居（行内动作被长名字挤没了？）：" +
                    nodes.joinToString(" | ") { it.describe() }
            }
        }
        val small = (holder + actions).filter {
            it.widthDp + 0.5f < probe.floorDp || it.heightDp + 0.5f < probe.floorDp
        }
        assertTrue(
            "$where 在 ${matrix.id}：长名字把这一行的可点热区挤到 ${probe.floorDp.toInt()}dp 以下（SemanticsProbe 那把尺）→\n" +
                small.joinToString("\n") { "  " + it.describe() } + "\n  名字：${name.describe()}",
            small.isEmpty()
        )
    }

    /**
     * 名字必须一格不缺地拿回"格子多给出来的地方"，一直拿到它排完为止——拿不回来就是被无谓地又切了一刀
     * （钉死 `widthIn(max = …)` 那种写法在这一句红，而不是在"它被裁了"那句红：被裁是正常的，白拿才是缺陷）。
     * 容差用**从读数里推出来的字符宽**（自然宽 ÷ 字符数），不抄版式常数：裁到一行的那颗 `Text`
     * 少排掉的那半截最宽就是一个字符，留两个字符的余量只会让判据更松，不会让它看不见。
     */
    private fun assertPaysForEveryDp(
        where: String, matrix: UiMatrix, got: Float, need: Float, chars: Int,
        paid: MutableMap<Float, Pair<Float, Float>>
    ) {
        val cell = matrix.widthDp.toFloat()
        val previous = paid[matrix.fontScale]
        paid[matrix.fontScale] = cell to got
        if (previous == null) return
        val grew = cell - previous.first
        val owed = minOf(grew - 1f, need - previous.second) - 2f * (need / chars) - 1f
        assertTrue(
            "$where 在 ${matrix.id}：格子比上一档（${previous.first.toInt()}dp）宽了 ${grew.toInt()}dp，" +
                "名字却只从 ${previous.second.toInt()}dp 排到 ${got.toInt()}dp，而它一行要 ${need.toInt()}dp——" +
                "多出来的地方没给名字（字符宽 ${(need / chars).toInt()}dp，容差两个字符）",
            got - previous.second >= maxOf(0f, owed)
        )
    }

    // ═══════════ 断言 ═══════════

    /**
     * 落点①（`ProviderSection.kt:165`）：中英两条长名各跑满 12 格，每格都拿控制组当尺。
     * 变异反证：`name.take(8)` ⇒ `named()` 当场抛；标题加 `widthIn(max = 60.dp)` ⇒ [assertPaysForEveryDp]
     * 在 360dp-font100 那一格红；`maxLines` 改成 2 ⇒ "一行高"那句红（记录失效，要重立）。
     */
    @Test
    fun `the provider card title is clipped to one line in silence`() {
        val spec = mutableStateOf(Spec(UiMatrix.FULL.first(), longZh))
        mountSection(spec, stubViewModel())
        listOf(longZh, longEn).forEach { name ->
            val silent = mutableListOf<String>()
            val paid = HashMap<Float, Pair<Float, Float>>()
            UiMatrix.FULL.forEach { matrix ->
                setCell(spec, matrix, titleName = name)
                val probed = bucket(PROBED_TAG)
                val title = probed.named(name, "卡片标题·被测")
                val natural = bucket(CONTROL_TAG).named(name, "卡片标题·控制组")
                judgeAgainstControl("卡片标题", matrix, title, natural, silent)
                assertPaysForEveryDp("卡片标题", matrix, title.widthDp, natural.widthDp, name.length, paid)
                judgeNeighbours("卡片标题", matrix, probed, title, expectRowActions = false)
            }
            assertTrue(
                "「${name.take(12)}」在 12 格里一格都没被裁——是名字短了还是生产改过了？这一格的记录要重立。$silent",
                silent.isNotEmpty()
            )
        }
    }

    /**
     * 落点②（`ProviderSection.kt:235`）：展开后的列表行，槽位最紧（右边并着「编辑」「删除」）。
     * 参照挂不出来 ⇒ 用同字号四档宽度的上涨来证：`宽(w) = min(自然宽, 槽(w))`，宽度随格子涨 ⇒ 比最宽那格窄的每一格必然没排完。
     * 变异反证：行内两颗动作改成 `weight(1f)` ⇒ 「编辑」被挤掉 ⇒ `judgeNeighbours` 红；
     * 名字加 `widthIn(max = 60.dp)` ⇒ 四档宽度不再上涨 ⇒ `check(涨过 2dp)` 红；
     * 名字的 `Column` 去掉权重 ⇒ 名字压上「编辑」⇒ 叠那句红；`maxLines` 改成 2 ⇒ 对短名字那一行高的 1.3 倍那句红。
     */
    @Test
    fun `the expanded provider row keeps a long name on one line without a plateau`() {
        val spec = mutableStateOf(Spec(UiMatrix.FULL.first(), extraLong))
        mountSection(spec, stubViewModel(), withControl = false)
        setCell(spec, UiMatrix(600), titleName = otherName, rowName = shortName)
        expandCard()
        // 一行多高**不抄版式常数、也不跨宽度借尺**：参照必须是"同一格、同一字号、只把名字换短"那一次读数。
        // （第一版拿 600dp 那格的短名字当尺，量到 15dp，而被测那格在 320dp 是 20dp——
        //   差的是**格子**不是**行数**：这一句于是变成假指控。见 第0节第65条第7条 第二条。）
        val width = LinkedHashMap<String, Float>()
        val height = LinkedHashMap<String, Float>()
        UiMatrix.FULL.forEach { matrix ->
            setCell(spec, matrix, titleName = otherName, rowName = extraLong)
            val nodes = bucket(PROBED_TAG)
            val row = nodes.named(extraLong, "列表行·被测")
            listOf("编辑", "删除").forEach { a ->
                check(nodes.any { it.text == a }) { "${matrix.id} 读不到行内动作「$a」，这一格根本没展开" }
            }
            judgeNeighbours("列表行", matrix, nodes, row, expectRowActions = true)
            width[matrix.id] = row.widthDp
            height[matrix.id] = row.heightDp
            // 同一格、只把名字换短 ⇒ 这一档"一行"到底是多高，由这一次读数说，不由别格借
        }
        val provenClip = mutableListOf<String>()
        UiMatrix.FONT_SCALES.forEach { f ->
            val cells = UiMatrix.WIDTHS_DP.map { UiMatrix(it, fontScale = f) }
            val heightsInRow = cells.map { height.getValue(it.id) }
            val ws = cells.map { width.getValue(it.id) }
            check(ws.last() > ws.first() + 2f) {
                "列表行在 font${(f * 100).toInt()} 这一档：格子从 ${cells.first().widthDp}dp 放宽到 " +
                    "${cells.last().widthDp}dp，名字却一动不动（${ws.map { it.toInt() }}）——" +
                    "要么它被无谓地钉死了宽度，要么这一档压根没在涨，参照是假的"
            }
            for (i in 1 until ws.size) {
                assertTrue("列表行在 font${(f * 100).toInt()}：格子从 ${cells[i - 1].widthDp}dp 放宽到 ${cells[i].widthDp}dp，" +
                    "名字反而退回 ${ws[i].toInt()}dp（上一格 ${ws[i - 1].toInt()}dp）——这一档的读数自相矛盾",
                    ws[i] >= ws[i - 1] - 0.5f)
                if (ws[i] > ws[i - 1] + 2f) provenClip += cells[i - 1].id
            }
            cells.forEach { c ->
                assertTrue(
                    "列表行的名字在 font" + (f * 100).toInt() + " 这一档里高度不一致：" +
                        cells.map { "${it.id}=${height.getValue(it.id).toInt()}dp" } +
                        " —— 同一颗组件、同一把字号，只有换行才会让某几档比别人高。" +
                        "宽度换了高度就换，说明它不再是一行，本文件钉的记录要重立",
                    heightsInRow.maxOf { it } - heightsInRow.minOf { it } <= 0.5f
                )
            }
        }
        assertTrue(
            "12 格里没有一格能被证明「名字没排完」（同一档字号里名字宽度不随格子涨）：实到宽度 $width",
            provenClip.isNotEmpty()
        )
    }

    private companion object {
        /** 被测那一遍 / 自然宽参照那一遍（只在本文件里用；两遍同字号才量得是同一种字） */
        const val PROBED_TAG = "lb_longname_probed"
        const val CONTROL_TAG = "lb_longname_control"

        /**
         * 控制组的宽度：够两条长名在 2.0 倍字下仍排成一行。
         * 开小了会被 `named()`/`judgeAgainstControl` 抓住（参照那一遍自己排不进一行就是假参照），不是悄悄放宽。
         */
        val CONTROL_WIDTH = 2400.dp
    }
}
