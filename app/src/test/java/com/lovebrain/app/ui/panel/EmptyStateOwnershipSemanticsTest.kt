package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.designsystem.LbEmptyState
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.panel.reply.CorrectionCenterHolder
import com.lovebrain.app.ui.panel.reply.CorrectionCenterHost
import kotlin.math.ceil
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 两档"页面自己画的空态"改由设计系统那一处来画之后的读数账：
 * 面板的主动发结果区（原来是一颗自画的 `Box + 居中 Text`）与记忆纠正中心
 * （原来是一行自画的说明文字）。
 *
 * 这一族此前是**零采用**：`LbEmptyState` 只在 `core/designsystem` 内部被 `LbAsyncState`
 * 的 Empty/Error 两档调用，页面想要空态只有两条路——自己画一颗，或者绕进四态渲染器。
 * 这两格买的是"自己画的那一颗从此由组件画"，判的四件事：
 *
 * 1. **所有者换了、说法没换**：读语义树，说明文字仍是那句原话，一字不差；
 * 2. **现在画它的是组件、不是页面**：证人是组件自己打的 `LbAsyncTags.MESSAGE` 锚点
 *    和它那条 `contentDescription`（页面自画的那一颗两样都没有）。把生产改回自画，
 *    这两条当场就红——这也是"`LbEmptyState` 在 core/designsystem 之外有调用方"
 *    在语义树侧的证人；
 * 3. **空态没长出入口**：主动发那一档量到 0 颗可点击节点；纠正中心那一档仍然只有
 *    浮层那颗「关闭」，并且它名字、`Button` 角色、两轴热区三样都得在（下限不自己抄数，
 *    指回 [AppDimens.TOUCH_TARGET_MIN_DP]）。给空态传 `action` 就会红——
 *    同一个决定不许修第二条路；
 * 4. **换所有者不许把话换丢**：12 格宽度 × 字体矩阵里说明文字必须整段排开。判几何用
 *    **控制组**（同一颗组件、同一字号、`requiredWidth(1200dp)` 量自然宽），不比对公告文字——
 *    语义树在被裁掉行时**照样报完整字符串**（坑表记过这一条），"串没变"不是"没被裁"。
 *    搬进组件之后说明文字从 `bodySmall`/`labelSmall` 抬到了 `bodyMedium`，会多占行，
 *    这一格就是盯着这件事的。
 *
 * ⚠ 矩阵换格靠**改 hoisted 值**，一个用例只 `setContent` 一次（坑表 3）；
 * 每格读不到"恰好一颗被测 + 一颗控制组"就抛——"读不到数"不许当"没问题"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyStateOwnershipSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density

    /** 下限不自己抄一个数：指回全站唯一那一颗 */
    private val probe by lazy { SemanticsProbe(density, AppDimens.TOUCH_TARGET_MIN_DP.toFloat()) }

    /** 两句话就是搬之前页面上念的那两句，逐字钉在这里——要改文案的人必须同时改这一行 */
    private val proactiveHint = "输入想说的话，点击下方「生成开场」让军师帮你找话题"
    private val correctionEmpty = "暂无纠正记录。在「本轮参考」中可对记忆发起纠正。"

    /** 控制组容器的锚点，只在本文件里用 */
    private val CONTROL_TAG = "lb_empty_state_control"

    // ═══════════ 挂载 ═══════════

    private fun mountProactive(
        cell: MutableState<UiMatrix>,
        isProactive: Boolean = false,
        withControl: Boolean = false
    ) {
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                ProactiveResultArea(
                    isProactive = isProactive,
                    options = emptyList(),
                    error = null,
                    onCopy = {},
                    modifier = Modifier.fillMaxWidth()
                )
                if (withControl) {
                    Box(Modifier.testTag(CONTROL_TAG).requiredWidth(1200.dp)) {
                        LbEmptyState(message = proactiveHint)
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun mountCorrectionCenter(
        cell: MutableState<UiMatrix>,
        withControl: Boolean = false
    ) {
        val holder = CorrectionCenterHolder()
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                CorrectionCenterHost(
                    holder = holder,
                    corrections = emptyMap(),
                    onUndoCorrection = {}
                )
                if (withControl) {
                    Box(Modifier.testTag(CONTROL_TAG).requiredWidth(1200.dp)) {
                        LbEmptyState(message = correctionEmpty)
                    }
                }
            }
        }
        holder.open()
        rule.mainClock.advanceTimeBy(16L)
        rule.waitForIdle()
    }

    // ═══════════ 读数 ═══════════

    /** 语义树里组件那处说明文字的一条：原话 + 它自己的尺寸（dp） */
    private data class Shot(val text: String, val widthDp: Float, val heightDp: Float) {
        fun describe() = "「$text」${widthDp.toInt()}x${heightDp.toInt()}dp"
    }

    private fun messageNodes(): List<SemanticsNode> =
        rule.onAllNodesWithTag(LbAsyncTags.MESSAGE, useUnmergedTree = true).fetchSemanticsNodes()

    private fun shotOf(node: SemanticsNode): Shot {
        val b = node.boundsInRoot
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .joinToString("") { it.text }
        check(text.isNotBlank()) { "空态的说明节点在树里没有可读文本：$b" }
        return Shot(text, b.width / density, b.height / density)
    }

    /** 组件那颗说明节点声明的 contentDescription（页面自画的那一颗没有这一条） */
    private fun announcedOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private fun collectTagged(node: SemanticsNode, tag: String): List<SemanticsNode> {
        val mine = if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) listOf(node) else emptyList()
        return mine + node.children.flatMap { collectTagged(it, tag) }
    }

    /** 树里全部可读文本——只用来证明"挂的是哪一档"，不拿来判尺寸 */
    private fun allTexts(): List<String> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes().flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            }

    /**
     * 被测那一颗与控制组那一颗，各恰好一颗，否则抛。
     *
     * 分桶按 testTag 的子树归属，不按坐标猜（控制组是故意比被测宽的那一颗）。
     */
    private fun splitControl(): Pair<Shot, Shot> {
        val containers = rule.onAllNodesWithTag(CONTROL_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        check(containers.size == 1) { "控制组容器数到 ${containers.size} 颗，要恰好 1 颗" }
        val underControl = collectTagged(containers.single(), LbAsyncTags.MESSAGE).map { it.id }.toSet()
        val all = messageNodes()
        check(all.size == 2) {
            "被测 + 控制组该有 2 颗说明节点，实到 ${all.size} 颗：" +
                all.joinToString { shotOf(it).describe() }
        }
        val control = all.filter { it.id in underControl }
        val probed = all.filter { it.id !in underControl }
        check(control.size == 1 && probed.size == 1) {
            "分桶分坏了：被测 ${probed.size} 颗、控制组 ${control.size} 颗"
        }
        val c = shotOf(control.single())
        val p = shotOf(probed.single())
        // 控制组只许"参照不出行"，不许比被测还窄：它要是被窗口裁掉了，参照只会**变松**
        // （算出来的行数偏小），不会冤枉人；但窄过被测就是量具接错了，必须当场抛。
        check(c.widthDp + 2f >= p.widthDp) {
            "控制组比被测还窄，自然宽参照是假的：控制「${c.describe()}」vs 被测「${p.describe()}」"
        }
        return p to c
    }

    /**
     * 12 格跑一遍：每格这句话都必须整段排开。
     *
     * 判据同 `UsageExtremeValuesSemanticsTest`：控制组量到这条文本排一行要多宽（自然宽），
     * 被测那一颗的槽宽放不下就必须给出对应的行数；行数不够 = 有话看不见 = 红。
     * 0.8 是行高余量（行距不是整数倍），它只会让判据更宽，不会让它看不见。
     */
    private fun sweepLaidOut(where: String, cell: MutableState<UiMatrix>): Map<String, Float> {
        val heights = LinkedHashMap<String, Float>()
        val clipped = mutableListOf<String>()
        val readings = mutableListOf<String>()
        for (matrix in UiMatrix.FULL) {
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            val (probed, control) = splitControl()
            heights[matrix.id] = probed.heightDp
            readings += "${matrix.id}：${probed.describe()}"
            val slot = max(probed.widthDp, 1f)
            val lines = if (control.widthDp <= slot + 2f) 1f
            else ceil((control.widthDp - 0.5) / slot).toFloat()
            val required = 0.8f * lines * control.heightDp
            if (probed.heightDp < required) {
                clipped += "$where 在 ${matrix.id}：这句话一行要 ${control.widthDp.toInt()}dp 宽、" +
                    "一行高 ${control.heightDp.toInt()}dp ⇒ 槽宽 ${slot.toInt()}dp 需要 ${lines.toInt()} 行" +
                    "（≥${required.toInt()}dp），实到 ${probed.heightDp.toInt()}dp"
            }
        }
        assertTrue(
            "$where 有空态说明被裁掉了行（用户就看不见后半句）：\n" +
                clipped.joinToString("\n") + "\n  12 格读数：" + readings.joinToString(" | "),
            clipped.isEmpty()
        )
        // 矩阵本身要有牙：同一宽度下 2.0 倍字必须比 1.0 倍字占更多高，
        // 否则换配置没真的进组合，上面那 12 格其实是同一格。
        val small = checkNotNull(heights["320dp-font100"]) { "$where 没读到 320dp-font100 那一格：$heights" }
        val large = checkNotNull(heights["320dp-font200"]) { "$where 没读到 320dp-font200 那一格：$heights" }
        assertTrue(
            "$where 同一宽度下 2.0 倍字没比 1.0 倍字占更多高（$small → $large），这 12 格是同一格",
            large > small
        )
        return heights
    }

    // ═══════════ 面板·主动发结果区 ═══════════

    /**
     * ① + ②：说法一字没改，而画它的是组件。
     *
     * 这一档过去的形状是一颗自画的 `Box(contentAlignment = Center) + Text`——
     * 文字、颜色、字号都在页面里各写一遍，所以"空的时候长什么样"这一页有自己的答案。
     */
    @Test
    fun `the proactive empty state says the same words and is drawn by the design system`() {
        mountProactive(mutableStateOf(UiMatrix(360, 1000)))
        val nodes = messageNodes()
        assertEquals(
            "空态该恰好一颗说明节点，多一颗就是有人在同一档里画了两行字：" +
                nodes.joinToString { shotOf(it).describe() },
            1, nodes.size
        )
        rule.onNodeWithText(proactiveHint).assertExists()
        assertEquals("说明节点念的还是原来那句", proactiveHint, shotOf(nodes.single()).text)
        assertEquals(
            "组件那颗说明文字自己打 contentDescription 锚点（页面自画的那一颗没有这一条）",
            listOf(proactiveHint), announcedOf(nodes.single())
        )
        // "量的确实是空态那一档"的证人（这一课是 `PanelErrorStatesSemanticsTest` 记的）：
        // 同一块区域进行中那一档画的是加载行，它那几句话出现在这里就说明挂错档了。
        // 证人写成"不在加载档"而不是真去挂加载档——那一档带无限动画，挂上来就再也空闲不了。
        val texts = allTexts()
        assertTrue(
            "这一格应当量在空态那一档，树里却读到了加载档的说法：$texts",
            texts.none { "军师正在" in it }
        )
    }

    /** ③：这一档没有动作，就不该有第二颗点得到的东西 */
    @Test
    fun `the proactive empty state does not grow a tappable entry`() {
        mountProactive(mutableStateOf(UiMatrix(360, 1000)))
        val clickable = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(
            "主动发空态里量到 ${clickable.size} 颗可点击节点，这一档该是 0 颗" +
                "（要给出口就让持有者传 action，不许在空态里再画一颗）",
            clickable.isEmpty()
        )
    }

    /** ④：字号抬了一档之后，这句话在 12 格里都得排开 */
    @Test
    fun `the proactive empty state stays fully laid out across the whole matrix`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountProactive(cell, withControl = true)
        sweepLaidOut("面板·主动发空态", cell)
    }

    // ═══════════ 记忆纠正中心 ═══════════

    /** ① + ②：同上，换的是浮层里那一行自画文字 */
    @Test
    fun `the correction center empty state says the same words and is drawn by the design system`() {
        mountCorrectionCenter(mutableStateOf(UiMatrix(360, 1000)))
        val nodes = messageNodes()
        assertEquals(
            "空纠正中心该恰好一颗说明节点：" + nodes.joinToString { shotOf(it).describe() },
            1, nodes.size
        )
        rule.onNodeWithText(correctionEmpty).assertExists()
        assertEquals("说明节点念的还是原来那句", correctionEmpty, shotOf(nodes.single()).text)
        assertEquals(
            "组件那颗说明文字自己打 contentDescription 锚点（页面自画的那一颗没有这一条）",
            listOf(correctionEmpty), announcedOf(nodes.single())
        )
    }

    /**
     * ③：空态自己不长出口——这一档唯一的可点东西仍是浮层那颗「关闭」，
     * 而且名字、角色、热区三样都得在：空态换了主人，不许把退出入口一起换没。
     */
    @Test
    fun `the empty correction center keeps exactly one named way out`() {
        mountCorrectionCenter(mutableStateOf(UiMatrix(360, 1000)))
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "空纠正中心")
        assertEquals(
            "空态里唯一那颗该是退出入口：" + targets.joinToString { it.describe() },
            listOf("关闭"), targets.map { it.label }
        )
        val way = targets.single()
        assertEquals(
            "退出入口要报 Button，否则读屏念得出字、说不出它是按钮：" + way.describe(),
            "Button", way.role
        )
        assertTrue(
            "热区两轴都要 ≥${AppDimens.TOUCH_TARGET_MIN_DP}dp：" + way.describe(),
            !way.tooSmall(probe.floorDp)
        )
    }

    /** ④：说明文字从 labelSmall 抬到 bodyMedium 之后，这句话在 12 格里都得排开 */
    @Test
    fun `the correction center empty state stays fully laid out across the whole matrix`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountCorrectionCenter(cell, withControl = true)
        sweepLaidOut("纠正中心空态", cell)
    }
}
