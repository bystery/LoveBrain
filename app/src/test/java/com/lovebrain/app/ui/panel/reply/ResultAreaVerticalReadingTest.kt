package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.ReplyCardLayout
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §3「回复卡片纵向排列」的**行为**那一半（默认纵向）：把 [ResultArea] 真挂进语义树量，
 * 与 `ReplyCardLayoutTest`（纯函数 + 源码结构那一半）分家。
 *
 * 这一族买四件事，每件都对着一条坏实现：
 * 1. **默认档就是纵向**：不传 `cardLayout` 挂载，八条正文必须一次全在树里、而且排成一列。
 *    坏实现——①默认仍是横向：那条 `LazyRow` 会回收滚出视口的 item，后面的正文根本不组合；
 *    ②纵向档嵌 `LazyColumn`：同一发；③给"最多八条"新造截断：同一发。
 *    这条仪器口径就是坑表 ⑮/⑯ 那一族（"量到一半也像没缺陷"）的解药。
 * 2. **切换方向不重新请求、不重新付费、不清空当前回复**：`onRetry` 是这一屏唯一的"再生成"出口，
 *    来回换方向它必须一次都没被叫过，八条正文换完方向逐条还在。
 * 3. **两档共用同一套卡逻辑**：筛选（全部/已赞/已踩）在两档下都对得上原卡，
 *    赞/复制的回调仍落在**原来那颗 identity** 上（纵向档若另起一条卡片链、按位置或按展示字母
 *    绑身份，这两格分别红）。
 * 4. **展开态与自定义草稿跨方向活着**：那两样住在 `rowState`（按 identity 记、按轮次作废），
 *    换方向就重建 rowState 的实现等于把用户正在写的东西清掉。
 *
 * ⚠ 热区下限那一半**不在这里重开一把尺**：`ResultAreaTouchTargetsTest` 沿横向档那条 `LazyRow`
 * 逐张滚过去量，纵向档用的是同一批 28dp 紧凑盒（卡片内容层没换实现）。
 * ⚠ 只能实屏判的两件（输入法弹起后遮不遮卡、八条连读的观感）记在交付说明里，不在本机宣称通过。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultAreaVerticalReadingTest {

    private companion object {
        /** 两档共用的那一颗锚点（与横向档同一个串，不新增第二个） */
        const val SCHEME_ROW_TAG = "scheme_cards_row"

        /** 八条互不相同的正文，按八项宇宙的顺序：四风格在前、四方向在后 */
        val BODIES = listOf(
            "第一条正文甲", "第二条正文乙", "第三条正文丙", "第四条正文丁",
            "第五条正文戊", "第六条正文己", "第七条正文庚", "第八条正文辛"
        )

        /** [BODIES] 对应的内部身份（展示字母 A–H 是另一层，回调必须认这一颗） */
        val KEYS = listOf(
            "STYLE:A", "STYLE:B", "STYLE:C", "STYLE:D",
            "DIRECTION:F", "DIRECTION:E", "DIRECTION:X", "DIRECTION:S"
        )
    }

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val copyLabel: String get() = ctx.getString(R.string.panel_copy)
    private val likeLabel: String get() = ctx.getString(R.string.a11y_scheme_like)

    /** 一轮完成的八条结果：挂在字段上（每次重组换新对象会让 `LaunchedEffect(schemes)` 白重跑一次） */
    private val result: GenerateResult.Success by lazy {
        GenerateResult.Success(
            LoveBrainResponse(
                response = ReplySchemes(
                    recommended = BODIES[0],
                    badBoy = BODIES[1],
                    playful = BODIES[2],
                    warm = BODIES[3]
                ),
                directions = listOf(BODIES[4], BODIES[5], BODIES[6], BODIES[7]),
                analysis = ReplyAnalysis()
            )
        )
    }

    /**
     * 挂一屏完成档的结果区。
     *
     * [layout] 传 null 就是**不传 `cardLayout` 实参**——那一格量的正是"宿主还没接线时默认哪一档"，
     * 所以那一格不许改成"传个 VERTICAL 就行"（那样把默认值翻成横向也不会红）。
     */
    private fun mount(
        layout: MutableState<ReplyCardLayout>?,
        feedbacks: Map<String, SchemeFeedback> = emptyMap(),
        onRetry: () -> Unit = {},
        onFeedback: (Scheme, SchemeFeedback) -> Unit = { _, _ -> },
        onCopyScheme: (Scheme) -> Unit = {}
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(widthDp = 600, heightDp = 1200).RenderIn(deviceDensity) {
                if (layout == null) {
                    // 这一支**不写 cardLayout 实参**：默认档那一格量的是"宿主还没接线时画哪一档"，
                    // 在这里补一句 `cardLayout = VERTICAL` 就等于把被测的那颗默认值换掉了
                    ResultArea(
                        result = result,
                        isGenerating = false,
                        streamingCoreText = "",
                        isGeneratingCore = false,
                        streamingSchemes = emptyList(),
                        feedbacks = feedbacks,
                        onFeedback = onFeedback,
                        onCopyScheme = onCopyScheme,
                        onRetry = onRetry,
                        providerReady = true,
                        onOpenSettings = {},
                        generationRoundId = 7
                    )
                } else {
                    ResultArea(
                        result = result,
                        isGenerating = false,
                        streamingCoreText = "",
                        isGeneratingCore = false,
                        streamingSchemes = emptyList(),
                        feedbacks = feedbacks,
                        onFeedback = onFeedback,
                        onCopyScheme = onCopyScheme,
                        onRetry = onRetry,
                        providerReady = true,
                        onOpenSettings = {},
                        generationRoundId = 7,
                        cardLayout = layout.value
                    )
                }
            }
        }
        settle()
    }

    /** 入场动画逐张交错 60ms（第八张延迟 420ms），给足帧再数树——否则"少了两张"会是动画造成的假红 */
    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600L)
        rule.waitForIdle()
    }

    private fun hasNode(text: String) =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** 现在还在树里的正文，按八项宇宙的顺序返回（只留存在的） */
    private fun bodiesInTree(): List<String> = BODIES.filter { hasNode(it) }

    // ═══ 1. 默认档＝纵向：八条一次数完，而且真的排成一列 ═══

    @Test
    fun `the default tier lays all eight cards out as one column in round order`() {
        // 传 null ⇒ mount 走"不写 cardLayout 实参"那一支，量的就是 ResultArea 那颗默认值：
        // 默认值被人翻成横向，这一格就该红（第一句就是横向档量不齐的那一发）
        mount(layout = null)
        BODIES.forEach { body ->
            assertTrue("默认纵向档该一次把「$body」这张卡也组合出来", hasNode(body))
        }
        // 纵向顺序：八条正文的 top 坐标按八项宇宙的顺序严格递增（风格在前、方向在后）
        val tops = BODIES.map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        tops.windowed(2).forEachIndexed { i, pair ->
            assertTrue(
                "纵向档第 ${i + 1} 张（${BODIES[i]}）必须排在第 ${i + 2} 张上面，" +
                    "实到 top ${pair[0]} vs ${pair[1]}（横向档那一排是并排的，top 一样 ⇒ 这里红）",
                pair[0] < pair[1]
            )
        }
        // 一卡一行 ⇒ 每行左缘同一条线（横向档并排滑，左缘一颗比一颗靠右）
        val lefts = BODIES.map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(
            "纵向档每行左缘应当同一条线（一卡一行），实到 $lefts",
            1, lefts.distinct().size
        )
    }

    // ═══ 2. 切换方向：不重新请求、不重新付费、不清空当前回复 ═══

    @Test
    fun `flipping the reading direction keeps every card and asks the model for nothing new`() {
        var retryCalls = 0
        val layout = mutableStateOf(ReplyCardLayout.VERTICAL)
        mount(layout, onRetry = { retryCalls++ })
        val before = bodiesInTree()
        assertEquals("起点：纵向档八条都在", 8, before.size)

        rule.runOnIdle { layout.value = ReplyCardLayout.HORIZONTAL }
        settle()
        rule.onNodeWithTag(SCHEME_ROW_TAG).assertExists()
        assertEquals("切到横向档不许发一次重新生成（§3：不重新请求 AI、不重新付费）", 0, retryCalls)

        rule.runOnIdle { layout.value = ReplyCardLayout.VERTICAL }
        settle()
        assertEquals("切回纵向档同样不许重新生成", 0, retryCalls)
        assertEquals("来回换方向后八条正文必须逐条还在（当前回复没被布局切换清空）", before, bodiesInTree())
    }

    // ═══ 3. 筛选在两档下都对得上原卡，且换方向不许把用户的选择洗掉 ═══

    @Test
    fun `the liked and disliked filters pick out the same cards in both tiers`() {
        val feedbacks = mapOf(
            "STYLE:B" to SchemeFeedback.LIKED,
            "STYLE:C" to SchemeFeedback.LIKED,
            "DIRECTION:X" to SchemeFeedback.DISLIKED
        )
        val layout = mutableStateOf(ReplyCardLayout.VERTICAL)
        mount(layout, feedbacks = feedbacks)
        assertEquals("起点：全部那一档八条都在", 8, bodiesInTree().size)

        // 纵向档筛已赞：只剩 B、C 两张
        rule.onNodeWithText("已赞 2").performClick()
        settle()
        assertEquals("纵向档筛已赞：只剩那两张", listOf(BODIES[1], BODIES[2]), bodiesInTree())

        // 换到横向档：筛选住在卡片行本地，换方向不许把它洗回「全部」
        rule.runOnIdle { layout.value = ReplyCardLayout.HORIZONTAL }
        settle()
        assertEquals(
            "横向档仍该停在已赞那一档、还是那两张（换方向洗掉筛选＝替用户改了选择）",
            listOf(BODIES[1], BODIES[2]), bodiesInTree()
        )

        // 换回纵向档再筛已踩：只有被踩过的那一张（DIRECTION:X）
        rule.runOnIdle { layout.value = ReplyCardLayout.VERTICAL }
        settle()
        rule.onNodeWithText(ctx.getString(R.string.scheme_filter_disliked, 1)).performClick()
        settle()
        assertEquals("已踩那一档只对得上真正被踩过的那张卡", listOf(BODIES[6]), bodiesInTree())
    }

    // ═══ 4. 回调仍落在原 identity 上（纵向档不是第二套卡片链）═══

    @Test
    fun `like and copy callbacks still name the original scheme in the vertical tier`() {
        val feedbackCalls = mutableListOf<Pair<String, SchemeFeedback>>()
        val copyCalls = mutableListOf<String>()
        val layout = mutableStateOf(ReplyCardLayout.VERTICAL)
        mount(
            layout,
            onFeedback = { scheme, fb -> feedbackCalls += scheme.identity.key to fb },
            onCopyScheme = { scheme -> copyCalls += scheme.identity.key }
        )

        // 2026-10-10 纵向档的取样口径：八张卡摊成一列，一屏放不下两张的目标节点，
        // 而 `boundsInRoot` 会把滚动祖先裁掉的那颗报成 `0x0 @(0,0)`（同族坑：横扫取最大面积那条）。
        // ⇒ **每一拍先把要看的那张卡滚进视口，再取它下面的那颗按钮**，并把量不到尺寸的节点排除；
        //    两次取样各钉一件事（第一张的复制、第二张的赞），判据一件没松。
        //    反过来说，"整列一次量完八颗再按 top 排序"这种写法在纵向档必然挑错张（本轮实测撞过两次：
        //    先实到 STYLE:C，把第二张滚进来之后第一张又被顶出视口、复制那拍实到 STYLE:B）。
        rule.runOnIdle { rule.onNodeWithText(BODIES[0]).performScrollTo() }
        val firstBodyTop = rule.onNodeWithText(BODIES[0]).fetchSemanticsNode().boundsInRoot.top
        val copies = rule.onAllNodesWithContentDescription(copyLabel).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0 }
            .sortedBy { it.boundsInRoot.top }
        val copyOfFirstCard = copies.indexOfFirst { it.boundsInRoot.top > firstBodyTop }
        assertTrue("该在第一张卡下面量到一颗「复制」，实到 ${copies.size} 颗", copyOfFirstCard >= 0)
        rule.onAllNodesWithContentDescription(copyLabel)[copyOfFirstCard].performClick()
        settle()
        assertEquals("复制回调也必须落在第一张卡自己的身份上", listOf(KEYS[0]), copyCalls)

        // 第二张卡的那颗「赞」：按落点找（第一颗落在这条正文之下的），不按"第几颗"猜。
        rule.runOnIdle { rule.onNodeWithText(BODIES[1]).performScrollTo() }
        val bodyTop = rule.onNodeWithText(BODIES[1]).fetchSemanticsNode().boundsInRoot.top
        val likes = rule.onAllNodesWithContentDescription(likeLabel).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0 }
            .sortedBy { it.boundsInRoot.top }
        val likeOfSecondCard = likes.indexOfFirst { it.boundsInRoot.top > bodyTop }
        assertTrue("该在第二张卡下面量到一颗「赞」，实到 ${likes.size} 颗", likeOfSecondCard >= 0)
        rule.onAllNodesWithContentDescription(likeLabel)[likeOfSecondCard].performClick()
        settle()
        assertEquals("点赞回调只该发一次", 1, feedbackCalls.size)
        assertEquals("回调必须落在第二张卡自己的身份上（展示字母与内部 tag 是两层）", KEYS[1],
            feedbackCalls.first().first)
        assertEquals(SchemeFeedback.LIKED, feedbackCalls.first().second)
    }

    // ═══ 5. 展开态与自定义草稿跨方向活着 ═══

    @Test
    fun `an open card with a draft stays open with that draft across the direction flip`() {
        val layout = mutableStateOf(ReplyCardLayout.VERTICAL)
        mount(layout)
        val firstPill = RewriteCommand.UI_OPTIONS.first().displayLabel
        val draft = "把第二句改软一点"

        // 点第一张卡进调整态（纵向档卡内不嵌滚动，点在正文上就是点在卡上）
        rule.onNodeWithText(BODIES[0]).performClick()
        settle()
        assertTrue("纵向档点开卡片该进调整态（量到「$firstPill」）", hasNode(firstPill))
        // 打开自定义那一格并写一句草稿
        rule.onNodeWithText(RewriteCommand.CUSTOM.displayLabel).performClick()
        settle()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(draft)
        settle()
        assertEquals("草稿该落进那格输入里", draft, editableValue())

        rule.runOnIdle { layout.value = ReplyCardLayout.HORIZONTAL }
        settle()
        assertTrue("横向档该还停在同一张卡的调整态（换方向洗掉展开态就红这里）", hasNode(firstPill))

        rule.runOnIdle { layout.value = ReplyCardLayout.VERTICAL }
        settle()
        assertTrue("切回纵向档仍在调整态", hasNode(firstPill))
        assertEquals(
            "切回纵向档草稿还在——它住在 rowState（按 identity 记、按轮次作废），不在卡片 item 里；" +
                "换方向重建 rowState 的实现等于清了用户正在写的东西",
            draft, editableValue()
        )
    }

    /**
     * 编辑器那一柱的真值。写法照本仓两处既有先例（`ProviderFormSemanticsTest:753`、
     * `KbEditScreenStatesTest:146`）：**先取节点、再走非安全的 `.config.getOrNull(...)`**——
     * 那颗成员带类型参数，挂在全安全调用链上这一版 Kotlin 解析不到（编译期撞过一次）。
     * `EditableText` 是 AnnotatedString，取的是它的 `.text`；`Text` 那一栏不是输入值。
     */
    private fun editableValue(): String? {
        val node = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().firstOrNull() ?: return null
        return node.config.getOrNull(SemanticsProperties.EditableText)?.text
    }

    // ═══ 6. 纵向档按内容换高（用户报"划拉半天都是空白"那一族：卡高由内容决定，不留固定下限）═══
    //
    // ⚠ 这一族量的是**语义树里那张卡自己那一格**的高度，不是源码里有没有 `heightIn`：
    //   只把源码 grep 改绿不等于交互验收——用户抱怨的是"划拉半天都是空白"，那是屏幕上的高度。
    // ⚠ 纵向档把卡摊成一列，视口外的节点会被 `boundsInRoot` 裁成 `0x0`（本仓同族坑：先滚进视口再量、
    //   并排掉零尺寸节点），所以下面每一颗都先 `performScrollTo`。
    // ⚠ 判据只引生产里那两颗数（`SchemeCardDimens.CARD_HEIGHT_DP` 与 `SchemeTextDimens.BODY_LINE_HEIGHT`），
    //   测试不抄第三份字面量。

    /** px→dp 与 Compose 内部用的同一把尺（@Config 那一格的 density） */
    private val dpPerPx: Float get() = ctx.resources.displayMetrics.density

    /** 横向档那颗固定卡高＝纵向档旧的**下限**：本轮要证明的是纵向短卡不再占满这一档 */
    private val horizontalCardHeightDp: Float get() = SchemeCardDimens.CARD_HEIGHT_DP.toFloat()

    /** 正文一行的版式高（"长回复自然增长"那一格按行数判就靠它） */
    private val bodyLineHeightDp: Float get() = SchemeTextDimens.BODY_LINE_HEIGHT.value

    /** 按指定正文造一轮完成档结果：`null` 方向位＝协议允许的"本轮不适合"，空串＝"未生成" */
    private fun successWith(bodies: List<String?>): GenerateResult.Success = GenerateResult.Success(
        LoveBrainResponse(
            response = ReplySchemes(
                recommended = bodies.getOrNull(0) ?: "",
                badBoy = bodies.getOrNull(1) ?: "",
                playful = bodies.getOrNull(2) ?: "",
                warm = bodies.getOrNull(3) ?: ""
            ),
            directions = listOf(bodies.getOrNull(4), bodies.getOrNull(5), bodies.getOrNull(6), bodies.getOrNull(7)),
            analysis = ReplyAnalysis()
        )
    )

    private fun mountBodies(
        bodies: List<String?>,
        layout: ReplyCardLayout = ReplyCardLayout.VERTICAL
    ) {
        val round = successWith(bodies)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(widthDp = 600, heightDp = 1200).RenderIn(deviceDensity) {
                ResultArea(
                    result = round,
                    isGenerating = false,
                    streamingCoreText = "",
                    isGeneratingCore = false,
                    streamingSchemes = emptyList(),
                    feedbacks = emptyMap(),
                    onFeedback = { _, _ -> },
                    onCopyScheme = {},
                    onRetry = {},
                    providerReady = true,
                    onOpenSettings = {},
                    generationRoundId = 7,
                    cardLayout = layout
                )
            }
        }
        settle()
    }

    /**
     * 一张卡在语义树里自己那一格的落点：`clickable` 会把子节点文字并进自己那一份语义，
     * 所以"带这条正文、且可点"的那一个节点就是卡片本体（不是里面那行文字）。
     * 先把它滚进视口再取，否则视口外那一张会被裁成 `0x0`，看着像"卡塌了"。
     */
    private fun cardNode(body: String): androidx.compose.ui.semantics.SemanticsNode {
        rule.runOnIdle { rule.onNode(hasText(body)).performScrollTo() }
        rule.waitForIdle()
        val nodes = rule.onAllNodes(hasText(body) and hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f }
        assertEquals(
            "「$body」这张卡该在树里量到恰好一个本体（实到 ${nodes.size} 颗；0 颗=滚不进视口或不可点，>1 颗=文字没被并进同一个可点节点）",
            1, nodes.size
        )
        return nodes.first()
    }

    private fun cardHeightDp(body: String): Float = cardNode(body).boundsInRoot.height / dpPerPx

    /**
     * 中间那一格（正文为空、因此**不可点**、也就没有自己的语义节点）的高度：
     * 由它上下两颗可点卡之间的净距离减掉两处 `spacedBy(Spacing.md)` 算出来。
     */
    private fun cardHeightBetween(aboveBody: String, belowBody: String): Float {
        val above = cardNode(aboveBody)
        val below = cardNode(belowBody)
        val gaps = 2 * Spacing.md.value
        return (below.boundsInRoot.top - above.boundsInRoot.bottom) / dpPerPx - gaps
    }

    @Test
    fun `eight one line replies do not stack eight blanks`() {
        mount(mutableStateOf(ReplyCardLayout.VERTICAL))
        val floor = horizontalCardHeightDp
        val heights = BODIES.map { cardHeightDp(it) }
        heights.forEachIndexed { i, h ->
            assertTrue(
                "第 ${i + 1} 张一行回复的卡实到 ${h}dp：纵向档要按内容换高，不许再占满横向档那 ${floor}dp",
                h < floor
            )
            assertTrue("第 ${i + 1} 张卡不许塌成零高（背景/边框错位）：实到 ${h}dp", h > 40f)
        }
        val total = heights.sum()
        assertTrue(
            "八张一行回复合计 ${total}dp，仍按每张 ${floor}dp 起占的话就是 ${8 * floor}dp——" +
                "那正是用户那句「划拉半天都是空白」的来源",
            total < 8 * floor
        )
        // 卡矮了但内容一件没少：三颗动作仍逐张在架（体验场景之一：各卡仍有复制/赞/踩）
        assertEquals("八张卡各一颗复制", 8, rule.onAllNodesWithContentDescription(copyLabel).fetchSemanticsNodes().size)
        assertEquals("八张卡各一颗赞", 8, rule.onAllNodesWithContentDescription(likeLabel).fetchSemanticsNodes().size)
    }

    @Test
    fun `a long reply grows the card instead of being clipped to two lines`() {
        val short = "这条很短"
        val long = (1..12).joinToString("；") { "第 $it 段：这一条故意写得很长，用来证明纵向档的卡是跟着正文行数额外汇成的" } +
            "。不是塞进一格固定高度里再让用户在卡里面划：外面那条滚动才是唯一的滚动主人，卡内再嵌一条滚动是本轮明令禁止的坏法。"
        mountBodies(listOf(long, short, short + "丙", short + "丁", short + "戊", short + "己", short + "庚", short + "辛"))
        val longHeight = cardHeightDp(long)
        val shortHeight = cardHeightDp(short)
        assertTrue(
            "长回复实到 ${longHeight}dp、短回复实到 ${shortHeight}dp：正文这三十来行里哪怕只落三行以上，" +
                "两张卡的高度差也该拉开三行以上（差 ${longHeight - shortHeight}dp）。" +
                "被裁成固定两行、或卡内再嵌一条滚动，都只会差一行以内",
            longHeight - shortHeight >= 3 * bodyLineHeightDp
        )
        // 反向证人：这一格量的确实是"长的那张"，短的那张没被顺手拉长
        assertTrue("短回复那张仍该矮于横向档卡高：实到 ${shortHeight}dp", shortHeight < horizontalCardHeightDp)
    }

    @Test
    fun `a null direction says not suitable without holding a blank card`() {
        val s5 = "第五条正文戊"
        val s7 = "第七条正文庚"
        mountBodies(listOf("第一条正文甲", "第二条正文乙", "第三条正文丙", "第四条正文丁", s5, null, s7, "第八条正文辛"))
        assertTrue(
            "合法 null 的方向该念「本轮不适合」（这句的判据归空卡自己，本轮不动它）",
            rule.onAllNodes(hasText(ctx.getString(R.string.scheme_not_suitable))).fetchSemanticsNodes().isNotEmpty()
        )
        val emptyHeight = cardHeightBetween(s5, s7)
        assertTrue(
            "「本轮不适合」那一格实到 ${emptyHeight}dp：只有一行说明的卡不该占满横向档那 ${horizontalCardHeightDp}dp",
            emptyHeight < horizontalCardHeightDp && emptyHeight > 20f
        )
    }

    @Test
    fun `the horizontal tier still holds its fixed 158 by 150 box`() {
        // 纵向换高的另一半判据：横向档一寸不许动（固定 158×150 与卡内滚动仍是原形状）
        mount(mutableStateOf(ReplyCardLayout.HORIZONTAL))
        val first = cardNode(BODIES[0]).boundsInRoot
        val expectedHeight = SchemeCardDimens.CARD_HEIGHT_DP.toFloat()
        val expectedWidth = SchemeCardDimens.CARD_WIDTH_DP.toFloat()
        assertEquals(
            "横向档卡高仍是那颗固定数：实到 ${first.height / dpPerPx}dp，应为 ${expectedHeight}dp",
            expectedHeight, first.height / dpPerPx, 1f
        )
        assertEquals(
            "横向档卡宽仍是那颗固定数：实到 ${first.width / dpPerPx}dp，应为 ${expectedWidth}dp",
            expectedWidth, first.width / dpPerPx, 1f
        )
    }
}
