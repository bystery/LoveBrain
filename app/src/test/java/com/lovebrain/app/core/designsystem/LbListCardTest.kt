package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `LbListCard` 的五槽合同（设计基线 v1.1 §3.6 / D1 §③-9，母版 = `KnowledgeBaseActivity.kt:392-490`）。
 *
 * ## 每一格都在判"真复用母版"，不是"换了个壳"
 *
 * 文本判据在这台仪器上是**没有牙的**（本仓库记过坑：写了 `maxLines + Ellipsis` 之后语义树仍报
 * **完整原文**，所以"标题有没有被裁成一行""摘要是不是只有两行"这类判据若写成字符串相等，
 * 永远看不见裁切）。凡判行数，都用**控制组**那一法：同一棵组件在同一个 `RenderIn`（= 同一把字号）里
 * 挂两遍，第二遍套进 `requiredWidth(2400dp)` 量"这句话排一行到底要多宽、多高"，被测那一遍与它比几何。
 * 体例照 `LongProviderNameSemanticsTest:124` 与 `UsageStatBarSemanticsTest:196`。
 *
 * ## 反向那半边（每条判据都说得出"回退成什么会红"）
 *
 * | 判据 | 回退成什么就红 |
 * | --- | --- |
 * | 五槽各有锚点 | 任一槽被删、或被页面又自己拼回去（锚点读不到） |
 * | 标题单行 | 去掉 `maxLines = 1` ⇒ 被测那一遍排成两行，`< 1.8 × 一行高` 那句红 |
 * | 摘要 ≤2 行 | 去掉 `maxLines = 2` ⇒ 高度涨到三行 ⇒ `<= 2.6 × 一行高` 红 |
 * | 状态点与字同屏 | 点没画 / 点与字分行 / 只有字没有点 ⇒ 数不到那颗 6dp 的 laid 节点，或垂直重叠不到一半 |
 * | 元信息合成一行 | 每段各画一个 `Text` ⇒ META 锚点数到 2 颗；换分隔符 ⇒ 串比对红 |
 * | 动作 ≤3 颗 | 去掉那颗上限 ⇒ 数到 4 颗 |
 * | 卡高由内容给 | 给卡体写 `heightIn(min = …)` ⇒ "空白卡只比标题行多出两档卡内边距"那句红 |
 * | 整卡一处操作 | `onClick = null` 仍挂 clickable ⇒ 不可点的卡被念成按钮 |
 *
 * ⚠ 这一族测不到的两件事，如实交出去，不靠加判据"看起来已修"：
 * **描边 1dp / 无阴影**（语义树里没有宽度也没有 elevation）由 `LbListCardContractTest` 按源码形状判，
 * 真实观感仍只能等截图；**字阶与颜色**（15/13/12/10 那几档）同理——树上没有字号那一栏。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbListCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /** 两遍同一棵树：被测那一遍在矩阵格子内，控制组那一遍套 `requiredWidth(2400dp)`（同一把字号） */
    private fun mountProbedAndControl(widthDp: Int = 320, card: @Composable () -> Unit) {
        rule.setContent {
            UiMatrix(widthDp).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.testTag(PROBED_TAG)) { card() }
                Box(modifier = Modifier.testTag(CONTROL_TAG).requiredWidth(NATURAL_WIDTH)) { card() }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 只挂被测那一遍（判尺寸、数颗数量热区时用，省一趟遍历） */
    private fun mountProbed(widthDp: Int = 320, card: @Composable () -> Unit) {
        rule.setContent {
            UiMatrix(widthDp).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.testTag(PROBED_TAG)) { card() }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    // ── 读数：从 testTag 桶往下走整棵**未合并**子树 ──────────────────────────

    private class GNode(
        val id: Int,
        val tag: String?,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val text: String,
        val clickable: Boolean
    ) {
        val widthDp: Float get() = right - left
        val heightDp: Float get() = bottom - top
        fun laid() = widthDp > 0f && heightDp > 0f
        fun sameLine(o: GNode): Boolean {
            val overlap = minOf(bottom, o.bottom) - maxOf(top, o.top)
            return overlap > 0.5f && overlap >= 0.5f * minOf(heightDp, o.heightDp)
        }

        fun inside(o: GNode) = top >= o.top - 0.5f && bottom <= o.bottom + 0.5f

        fun describe() = "「${text.take(28)}」 ${widthDp.toInt()}x${heightDp.toInt()}dp @(${left.toInt()},${top.toInt()})"
    }

    private fun bucket(tag: String): List<GNode> {
        val roots = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        check(roots.size == 1) { "$tag 这个桶数到 ${roots.size} 颗容器，要恰好 1 颗" }
        val clickable = rule.onAllNodes(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.id }.toSet()
        val out = ArrayList<GNode>()
        val queue = ArrayDeque<SemanticsNode>()
        queue.addLast(roots.single())
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val b = node.boundsInRoot
            out += GNode(
                id = node.id,
                tag = node.config.getOrNull(SemanticsProperties.TestTag),
                left = b.left / density, top = b.top / density,
                right = b.right / density, bottom = b.bottom / density,
                text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: "",
                clickable = node.id in clickable
            )
            node.children.forEach { queue.addLast(it) }
        }
        return out
    }

    /**
     * 桶里挂着某个 tag 且**摆出来了**的那些颗。数不到就红——
     * "读不到数"不许当"没问题"（0x0 是被压掉，与压根没画要分开说，见 `SemanticsProbe.unlaid` 那一条）。
     */
    private fun List<GNode>.laidWith(tag: String, where: String, expected: Int = 1): List<GNode> {
        val hits = filter { it.tag == tag && it.laid() }
        assertEquals(
            "$where：锚点 $tag 应有 $expected 颗已摆出来的节点，实到 ${hits.size} 颗：" +
                joinToString(" | ") { it.describe() }.take(900),
            expected, hits.size
        )
        return hits
    }

    // ── 判据 ────────────────────────────────────────────────────────────────

    /** 五槽齐挂：每槽都要有自己的锚点、都要摆出来（少一槽 = 有人把那一槽又搬回页面里画） */
    @Test
    fun `every slot has its own anchor when the caller fills all five`() {
        mountProbed(
            card = {
                LbListCard(
                    title = TITLE_TEXT,
                    status = LbListCardStatus("就绪", LbRowState.Ready),
                    summary = SUMMARY_TEXT,
                    meta = listOf("甲段", "乙段"),
                    actions = listOf(
                        LbListCardAction.secondary("编辑") {},
                        LbListCardAction.secondary("导出") {},
                        LbListCardAction.destructive("删除") {}
                    ),
                    onClick = {},
                    detail = { Text("展开层", style = AppTypography.labelSmall, color = TextSecondary) }
                )
            }
        )
        val nodes = bucket(PROBED_TAG)
        nodes.laidWith(LbListCardTags.TITLE, "槽 1 标题")
        nodes.laidWith(LbListCardTags.STATUS_DOT, "槽 2 状态点")
        nodes.laidWith(LbListCardTags.STATUS_TEXT, "槽 2 状态字")
        nodes.laidWith(LbListCardTags.SUMMARY, "槽 3 摘要")
        nodes.laidWith(LbListCardTags.META, "槽 4 元信息")
        nodes.laidWith(LbListCardTags.ACTION_ROW, "槽 5 动作行")
        nodes.laidWith(LbListCardTags.CARD, "卡底")
        assertEquals(
            "展开层也要画得出（否则案例页那一层会又变成卡外第二层底）",
            1, nodes.count { it.text == "展开层" && it.laid() }
        )
    }

    /**
     * 状态槽的两件事：点是 6dp 那一档，点与字**同一行**。
     *
     * 母版那一行没有状态点，所以"点与字同屏"只能按基线 §3.6 的字面判：点单独画、字单独画，
     * 分成两行就不算这一槽。回退成"点画到标题前""只有字没有点"都在这一格红。
     */
    @Test
    fun `the status dot sits on the same line as the status word`() {
        mountProbed(
            card = {
                LbListCard(title = TITLE_TEXT, status = LbListCardStatus("就绪", LbRowState.Ready))
            }
        )
        val nodes = bucket(PROBED_TAG)
        val dot = nodes.laidWith(LbListCardTags.STATUS_DOT, "状态点").single()
        val word = nodes.laidWith(LbListCardTags.STATUS_TEXT, "状态字").single()
        assertEquals("状态点应当是 6dp 那一档（宽）：" + dot.describe(), 6, dot.widthDp.toInt())
        assertEquals("状态点应当是 6dp 那一档（高）：" + dot.describe(), 6, dot.heightDp.toInt())
        assertTrue("点与字必须同一行（垂直重叠过半）→ 点 ${dot.describe()} / 字 ${word.describe()}",
            dot.sameLine(word))
        assertTrue("点在字的左边 → ${dot.describe()} / ${word.describe()}", dot.left < word.left)
    }

    /**
     * 标题槽单行（控制组量自然宽）：被测那一遍拿到的格子比"排一行需要的宽"窄，
     * 而高度仍只有**一行** ⇒ 真的被裁在一行里。
     *
     * 回退成什么会红：去掉 `maxLines = 1` ⇒ 被测排成两行，那句 `< 1.8 × 一行高` 红；
     * 有人改成"提前把句子截短"（`take(20)`）⇒ 两遍的串不再相等，`assertEquals(probed.text, natural.text)` 红。
     */
    @Test
    fun `the title stays on one line even when it cannot fit`() {
        mountProbedAndControl(
            card = { LbListCard(title = LONG_TITLE, status = LbListCardStatus("就绪", LbRowState.Ready)) }
        )
        val probed = bucket(PROBED_TAG).laidWith(LbListCardTags.TITLE, "标题·被测").single()
        val natural = bucket(CONTROL_TAG).laidWith(LbListCardTags.TITLE, "标题·控制组").single()
        assertEquals("两遍量的必须是同一句话：" + probed.text + " / " + natural.text, probed.text, natural.text)
        assertTrue(
            "控制组没把这一句排开（被测 ${probed.widthDp.toInt()}dp / 参照 ${natural.widthDp.toInt()}dp）" +
                "——参照是假的，这一格作废",
            natural.widthDp > probed.widthDp + 2f
        )
        assertTrue(
            "标题排一行要 ${natural.widthDp.toInt()}dp、被测槽位只给 ${probed.widthDp.toInt()}dp，" +
                "而被测实到高 ${probed.heightDp.toInt()}dp、一行高 ${natural.heightDp.toInt()}dp" +
                " ⇒ 不再是单行（基线 §3.6 槽 1 要的是单行 + ellipsis）",
            probed.heightDp < 1.8f * natural.heightDp
        )
    }

    /**
     * 摘要槽最多两行：长句在 320dp 里两行也不够用（自然宽 > 被测槽宽 × 2），
     * 于是被测那一遍的高度必须停在"两行"这一档，第三行不许出现。
     *
     * 回退成什么会红：`maxLines = 2` 改成 3、或整个去掉 ⇒ 高度涨到三行以上。
     */
    @Test
    fun `the summary takes at most two lines`() {
        mountProbedAndControl(
            card = { LbListCard(title = TITLE_TEXT, summary = LONG_SUMMARY) }
        )
        val probed = bucket(PROBED_TAG).laidWith(LbListCardTags.SUMMARY, "摘要·被测").single()
        val natural = bucket(CONTROL_TAG).laidWith(LbListCardTags.SUMMARY, "摘要·控制组").single()
        assertEquals("两遍量的必须是同一句话", probed.text, natural.text)
        val oneLine = natural.heightDp
        assertTrue("控制组里这一句就不是一行（高 ${oneLine.toInt()}dp）——参照作废", oneLine > 0f)
        assertTrue(
            "这一句在 320dp 里一行放不下（一行要 ${natural.widthDp.toInt()}dp、槽位 ${probed.widthDp.toInt()}dp），" +
                "所以必须用到第二行；实到高 ${probed.heightDp.toInt()}dp",
            natural.widthDp > 2f * probed.widthDp
        )
        assertTrue(
            "摘要超出两行：一行高 ${oneLine.toInt()}dp、被测实到高 ${probed.heightDp.toInt()}dp" +
                "（基线 §3.6 槽 3 的上限是两行）",
            probed.heightDp <= 2.6f * oneLine
        )
    }

    /**
     * 元信息槽：多段合成**一个** `Text`、用母版那一个分隔符（`lbMetaLine` 是唯一主人）。
     *
     * 回退成什么会红：每段各画一行 ⇒ META 锚点数到 2 颗；分隔符换掉或页面自己 `joinToString` 一份
     * ⇒ 这一句的串比对红；空白段留下（"暂无XX"那一族）⇒ 串里多出分隔符。
     */
    @Test
    fun `the meta parts collapse into one merged line`() {
        mountProbed(
            card = { LbListCard(title = TITLE_TEXT, meta = listOf("前段", "", "后段")) }
        )
        val meta = bucket(PROBED_TAG).laidWith(LbListCardTags.META, "元信息").single()
        assertEquals("两段元信息要合成一行、空白段丢掉：" + meta.text, "前段 ｜ 后段", meta.text)
    }

    /** 分隔符那颗主人的单元测试：顺序、空白段、散参入口都只在这里判一次 */
    @Test
    fun `the meta joiner keeps order and drops blanks`() {
        assertEquals("甲 ｜ 乙 ｜ 丙", lbMetaLine(listOf("甲", "乙", "丙")))
        assertEquals("甲", lbMetaLine(listOf("", "  ", "甲", "")))
        assertEquals("", lbMetaLine(emptyList()))
        assertEquals("甲 ｜ 乙", lbMetaLine("甲", "乙"))
    }

    /**
     * 动作行最多三颗（基线 §3.6）。第四颗今天没有主人（`⋯` 溢出档还没进设计系统，已按缺口登记），
     * 所以多出来的那一颗不许出现在屏上。
     *
     * 这一格同时是那条缺口的证人：谁补上 `⋯`，读数会从"3 颗"变成"3 颗 + 1 颗溢出钮"，
     * 那时要回来把这一格改成"三颗 + 溢出档"，**不许**把 3 直接改成 4。
     * 回退成什么会红：去掉那颗上限 ⇒ 数到 4 颗。
     */
    @Test
    fun `no more than three capsules are drawn`() {
        mountProbed(
            card = {
                LbListCard(
                    title = TITLE_TEXT,
                    actions = listOf(
                        LbListCardAction.secondary("一") {},
                        LbListCardAction.secondary("二") {},
                        LbListCardAction.destructive("三") {},
                        LbListCardAction.primary("四") {}
                    )
                )
            }
        )
        val capsules = bucket(PROBED_TAG).filter { it.clickable && it.laid() }
        assertEquals(
            "动作行只该排三颗（第四颗没有档可去）：" + capsules.joinToString(" | ") { it.describe() },
            3, capsules.size
        )
        // 三颗都拿到全站那颗热区下限（可见 32 的胶囊由外层透明盒垫到 48 两轴）
        val small = probe.laid(probe.actionableTargets(rule, "LbListCard 动作行"))
            .filter { it.widthDp + 0.5f < probe.floorDp || it.heightDp + 0.5f < probe.floorDp }
        assertTrue(
            "有动作颗的热区不到 ${probe.floorDp.toInt()}dp（两轴都要垫，只垫高度不算）：" +
                small.joinToString("\n") { "  " + it.describe() },
            small.isEmpty()
        )
    }

    /**
     * 卡高由内容给：只交一颗标题的卡，整卡高 = 标题那一行 + **两档卡内边距**，
     * 卡体不许有自己的地板。
     *
     * 判据不抄像素常数：卡内那一档由 `Spacing.lg` 现读，行高由被测那一遍自己量出来。
     * 回退成什么会红：给卡体写 `heightIn(min = 96.dp)` ⇒ 第一句红（96 − 一行高 远大于 2×12 + 容差）；
     * 有人把槽位又压成固定高 ⇒ 第二句红（满槽卡不比空白卡高）。
     */
    @Test
    fun `the card height comes from its content not from a floor`() {
        val cardPaddingBothSides = Spacing.lg.value * 2f
        mountProbed(
            widthDp = 600,
            card = {
                Column {
                    Box(modifier = Modifier.testTag(MINIMAL_CARD)) { LbListCard(title = TITLE_TEXT) }
                    Box(modifier = Modifier.testTag(FULL_CARD)) {
                        LbListCard(
                            title = LONG_TITLE,
                            status = LbListCardStatus("就绪", LbRowState.Ready),
                            summary = LONG_SUMMARY,
                            meta = listOf("前段", "后段"),
                            actions = listOf(LbListCardAction.secondary("编辑") {})
                        )
                    }
                }
            }
        )
        val all = bucket(PROBED_TAG)
        val minimal = all.laidWith(MINIMAL_CARD, "空白卡").single()
        val full = all.laidWith(FULL_CARD, "满槽卡").single()
        val titles = all.laidWith(LbListCardTags.TITLE, "标题行", expected = 2)
        val minimalTitle = titles.single { it.inside(minimal) }
        assertTrue(
            "只交标题的卡高 ${minimal.heightDp.toInt()}dp，而标题那一行只有 ${minimalTitle.heightDp.toInt()}dp，" +
                "卡内两档边距合计 ${cardPaddingBothSides.toInt()}dp ⇒ 多出来的是卡体自己垫的地板（heightIn?）",
            minimal.heightDp <= minimalTitle.heightDp + cardPaddingBothSides + 2f
        )
        assertTrue(
            "满槽卡（${full.heightDp.toInt()}dp）不比空白卡（${minimal.heightDp.toInt()}dp）高——" +
                "槽位加上去却动不了卡高，说明有一处固定高度回来了",
            full.heightDp > minimal.heightDp + 24f
        )
    }

    /**
     * 整卡一处操作：交 `onClick` 时报 Button 且只有这一颗可点，
     * 并且点击语义挂在**卡底自己**那一颗节点上（挂在子节点上读屏念的就是另一个所有者）。
     */
    @Test
    fun `a card is one actionable thing when the caller says it is`() {
        var fired = 0
        mountProbed(
            card = { LbListCard(title = TITLE_TEXT, summary = LONG_SUMMARY, onClick = { fired++ }) }
        )
        val clickable = probe.laid(probe.actionableTargets(rule, "LbListCard 整卡"))
        assertEquals(
            "没有动作行的卡应当只有整卡这一处操作：" + clickable.joinToString { it.describe() },
            1, clickable.size
        )
        assertEquals("整卡要报成 Button：" + clickable.single().describe(), "Button", clickable.single().role)
        assertTrue(
            "点击语义要挂在带 testTag 的那一颗卡上（父链上不能只有子节点可点）：" +
                bucket(PROBED_TAG).laidWith(LbListCardTags.CARD, "卡底").single().describe(),
            bucket(PROBED_TAG).laidWith(LbListCardTags.CARD, "卡底").single().clickable
        )
        rule.onAllNodes(hasClickAction())[0].performClick()
        assertEquals("点整卡必须落到调用方那一次回调", 1, fired)
    }

    /** `onClick = null` 那一档：卡还画得出来，但树上一个可点节点都没有（不许念成死按钮） */
    @Test
    fun `a card without onClick draws no dead button`() {
        mountProbed(card = { LbListCard(title = TITLE_TEXT, summary = LONG_SUMMARY) })
        bucket(PROBED_TAG).laidWith(LbListCardTags.CARD, "卡底")
        val clickables = rule.onAllNodes(SemanticsMatcher("带点击") {
            it.config.contains(SemanticsActions.OnClick)
        }).fetchSemanticsNodes()
        assertEquals(
            "不可点的卡一个可点节点都不该有：" + clickables.joinToString { probe.of(it).describe() },
            0, clickables.size
        )
    }

    /**
     * 空白标题/摘要/元信息不画幽灵节点（D1 §③-9：无业务真源时不许编标题）。
     * 回退成什么会红：无条件画那一行 ⇒ 锚点数到 1 颗而串是空的，或行里留着一段空白。
     */
    @Test
    fun `blank slots leave no ghost lines`() {
        mountProbed(card = { LbListCard(title = "", summary = "  ", meta = listOf("", "  ")) })
        val nodes = bucket(PROBED_TAG).filter { it.laid() }
        val where = nodes.joinToString(" | ") { it.describe() }
        assertEquals("空白标题不该画出节点：$where", 0, nodes.count { it.tag == LbListCardTags.TITLE })
        assertEquals("空白摘要不该画出节点：$where", 0, nodes.count { it.tag == LbListCardTags.SUMMARY })
        assertEquals("全空白的元信息不该画出节点：$where", 0, nodes.count { it.tag == LbListCardTags.META })
    }

    /**
     * 语气档不许换热区、也不许让那一颗换成别的形状：三颗具名档都走同一颗 `RowCapsule`。
     *
     * 语义树里没有颜色，所以"删除那颗是红的"这一句在树上量不到——本仓库的既有办法是
     * 把语气收成**只能从具名档挑**的一颗构造函数（`RowAction.kt` 的 `rowActionTone` 同一法）。
     * 这一格用反射判那扇门还关着：回退成什么会红 = 有人给 `LbListCardAction` 开出公开构造函数
     * 或 `tone:` 旋钮（那就等于"删除是不是红的"又交回页面决定）。
     */
    @Test
    fun `the tone cannot be chosen by the caller`() {
        val ctors = LbListCardAction::class.java.declaredConstructors
        assertTrue("动作档应当读不到构造函数（实到 ${ctors.size} 颗）", ctors.isNotEmpty())
        // Kotlin 给 companion 里那句 `LbListCardAction(label, tone, onClick)` 生成一根**合成桥**：
        // 参数表与私有那颗一字不差、末尾多一颗 `kotlin.jvm.internal.DefaultConstructorMarker`，
        // 在字节码里是 public。这一格要关的是"调用方能自己挑 tone"那扇门，不是编译器那根桥——
        // 所以带 marker 的合成颗放行，其余**每一颗**都必须 private。
        // 回退成什么会红：把 `private constructor` 改回公开（或写成 `data class`）⇒ 不带 marker
        // 的那颗不再是 private，这一句当场红。
        fun isSyntheticBridge(ctor: java.lang.reflect.Constructor<*>) =
            ctor.parameterTypes.lastOrNull()?.name
                ?.startsWith("kotlin.jvm.internal.DefaultConstructorMarker") == true
        ctors.forEach {
            assertTrue(
                "动作的构造函数必须是私有的（编译器合成桥除外）：$it",
                isSyntheticBridge(it) || java.lang.reflect.Modifier.isPrivate(it.modifiers)
            )
        }
        assertEquals("不带 marker 的构造函数只许有那一颗私有的本体", 1, ctors.count { !isSyntheticBridge(it) })
        // `copy()` 是把私有构造函数从后门发回去的那颗：`data class` 一恢复它就自己长出来
        assertEquals(
            "动作档不许有 copy()（有就等于页面能换任意字段，包括 tone）",
            0, LbListCardAction::class.java.declaredMethods.count { it.name == "copy" }
        )
        assertEquals("三颗具名档各说一件事：secondary", LbTextActionTone.RowSecondary,
            LbListCardAction.secondary("x") {}.tone)
        assertEquals("三颗具名档各说一件事：primary", LbTextActionTone.Accent,
            LbListCardAction.primary("x") {}.tone)
        assertEquals("删除那颗恒 Destructive", LbTextActionTone.Destructive,
            LbListCardAction.destructive("x") {}.tone)
    }

    companion object {
        private const val PROBED_TAG = "lb_list_card_probed"
        private const val CONTROL_TAG = "lb_list_card_control"
        private const val MINIMAL_CARD = "lb_list_card_minimal"
        private const val FULL_CARD = "lb_list_card_full"

        /** 参照那一遍的宽度：够长句在 2.0 倍字下仍排成一行；排不进就是假参照，上面几格当场红 */
        private val NATURAL_WIDTH = 2400.dp

        private const val TITLE_TEXT = "标题那一句"
        private const val SUMMARY_TEXT = "摘要那一句"
        private const val LONG_TITLE = "这一句是回复的首句，它长到在三百二十宽的格子里一行放不下，" +
            "所以必须被裁掉后面那一截，而不是把整张卡顶高"
        private const val LONG_SUMMARY = "余文这一段的长度是刻意放的：它要能在 320dp 里排到第三行去，" +
            "这样两行上限才真的被量到，而不是因为文案短而恒绿。" +
            "再补一句，让第三行确实存在，而不是从版式推断出来的。"
    }
}
