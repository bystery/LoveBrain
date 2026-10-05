package com.lovebrain.app.ui.panel.reply

import android.content.Context
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.ChatOutgoingBg
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.testing.PixelContrastMeter
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 真实对话的两列气泡（）+ 尾部那一行军师备注（）在**渲染与语义树**上的读数。
 *
 * 四把尺各管一件事，别把它们合成一把：
 * · 档位表 [bubbleInkOf] 是纯函数——它钉"谁该拿哪一档"，八种组合穷举，第四档根本不存在；
 *   但它**证明不了** MessageRow 真按这张表涂色，所以另有两格从**像素**读实际画出来的底色
 *   （取色通道与 `RenderedPixelContrastTest` 同一条：`androidx.core.view.drawToBitmap`，
 *    它的"通电对照"与"像素尺=token 尺"两格已经在那把尺上钉过；这里再加一条
 *    "两列读数必须彼此不同"的哨兵，仪器坏成整屏一个色时这一格先红）。
 * · 左右贴边、八成宽、内边距、条目间距这些量语义树里的**布局边界**，不读源码也不读文案：本项目已知
 *   `maxLines + Ellipsis` 裁切时语义树永远报完整原串，拿文案判尺寸会得到一颗永远绿的假闸。
 * · 旧 `Role.IDEA` 的去向只能在树上判（它该折进那一行灰字，不该长出第三颗气泡），
 *   而"备注在两个分支都画"这一条里，空态分支必须由**渲染出来**的节点证明，源码扫不算。
 *
 * ⚠⚠ **两棵树要分清楚**（2026-10-04 复核 10 条红的唯一成因，先读这一段再动判据）：
 *   `MessageRow` 那一层写的是 `semantics(mergeDescendants = true)`——第5节第1条 "角色仍存在读屏标签里"
 *   要的就是它（读屏聚焦一行，念出"她的消息 + 正文"，删除自定义动作也挂在这一颗上）。
 *   代价是**合并树把被吞的那半棵子树整个摘出去**：本机 13:05 那轮 [ChatBubbleAppearanceTest] 的
 *   实读是 `onAllNodes(hasTestTag("message_bubble")).fetchSemanticsNodes()` 在合并树上返回 **0 颗**
 *   （失败信息自己打出来的：`聊天行只该有 HER/ME 那两颗气泡：0`，`:433`），
 *   于是拿行宽当分母那格、取气泡底色的三格、量"展开"那一格全部红在**读不到对象**上，
 *   而不是红在版式上。所以本文件的规矩是：
 *     —— 行级身份（读屏名、点击落点、可交互计数）走**合并树**（那正是读屏看到的形状）；
 *     —— 气泡级几何与底色走**未合并树**（`useUnmergedTree = true`，那才是画出来的那一颗）。
 *   两棵树各管一件事，谁也不许替谁：把几何判据搬回合并树 = 判据读不到对象；
 *   把"底色挂回整行"这种坏实现指望几何格抓住 = 抓不住，那是
 *   [the row itself stays unpainted so only the bubble carries ink] 那一格的活。
 *   这条路不是本轮新开的，是**仓库已经付过学费的那一条**：`LbPrimaryButtonStateTest` 里
 *   `the stop anchor is reachable in the merged tree the device queries` 记着同一个形状——
 *   `onAllNodes*` 默认查合并树，而 `clickable` 那一层把后代并进自己，tag 挂在被并掉的子节点上
 *   就"节点不存在"；那一族当时是**设备侧三格红、本机绿**才发现的。这里同一课不再交第二遍学费。
 *
 * ⚠ 底色读数容差取"最大单通道差 ≤ 2"（round/truncate 之差），比的是**画出来那颗色**；
 *   两列之间的实测差在几十个通道单位上（卡白 vs 微信绿），容差不是把判据调松。
 *
 * ⚠ "八成"那条线只能这样量（见 [a long message is capped at eight tenths of the available row width]）：
 *   `widthIn(max=…)` 是**夹**不是**铺**——最长那一行按整字进宽，实测必然落在
 *   `[cap - 一个字宽, cap]` 里。所以分母取**行内可用宽**（行自己左右各留 4dp），
 *   字宽从同一屏那颗粒短气泡**现量**，不留魔法数字。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatBubbleAppearanceTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    /** Compose 的绘制根：由 [mount] 在 composition 里从 `LocalView.current` 记下来（取像素的唯一通道） */
    private var drawRoot: View? = null

    /** 短句：气泡该贴住内容；长句：该被夹在**行内可用宽**八成那条线里 */
    private val shortHer = ChatMessage(role = ChatMessage.Role.HER, content = "在吗")
    private val shortMe = ChatMessage(role = ChatMessage.Role.ME, content = "在的")

    /**
     * 一定撞出宽度上限的长文，**不带标点**：行尾的标点在换行时会被整颗推到下一行（中文禁则），
     * 最长那一行就可能少一个字宽，于是"两颗长文夹在同一条线上"会被判成假红。
     * 纯汉字让换行量化只剩"整字进宽"这一件事，判据才写得死。
     */
    private val longMe = ChatMessage(
        role = ChatMessage.Role.ME,
        content = "这句话故意写得很长很长为的就是让它一定撞到气泡宽度上限然后自己换行而不是贴着屏幕两边铺成一张整行卡片"
    )
    /** 与 [longMe] 另一条内容、同一档长度的长文：两颗必须被**同一条线**夹成同一个宽度 */
    private val longHer = ChatMessage(
        role = ChatMessage.Role.HER,
        content = "这一句同样故意写得很长很长为的是让它撞到与上面那颗同一条宽度上限线然后自己换行而不是各夹各的宽度也不是整行卡片"
    )
    private val idea = ChatMessage(role = ChatMessage.Role.IDEA, content = "先哄两句")

    /**
     * 超过 `MessageBody` 那 80 字阈值的长消息——折叠这一档只认字数，不认"看起来长"。
     * 反例：拿 [longMe]（50 字）去判"展开"出口 ⇒ 折叠根本不触发，那一格会红在夹具上而不是产品上。
     */
    private val foldedMe = ChatMessage(
        role = ChatMessage.Role.ME,
        content = "这一条故意写得超过八十个字，为的是让它走长消息折叠那一档：默认只给两行加一处展开出口，" +
            "点一下才把剩下的部分摊开给眼睛看。这段话的字数是刻意数过的，它必须稳稳越过那条阈值，" +
            "否则那一格量的就不是折叠而是普通换行。"
    )

    /** 一定撞出宽度上限的备注（判"只画一行"这一档用） */
    private val longNote = "这句话故意写得很长很长，为的就是让它一定撞到那一行灰字的宽度上限" +
        "然后被省略掉，而不是把上面的聊天气泡挤成两行三行"

    /** 折叠那一对的锚点文案：源在 `MessageList.MessageBody` 那一处（本轮没进资源，改产品文案要回来同步） */
    private val expandWords = "展开"
    private val collapseWords = "收起"

    private val edited = mutableListOf<Int>()
    private val noteClicks = mutableListOf<Int>()

    private fun mount(
        messages: List<ChatMessage>,
        noteText: String? = null,
        editingIndex: Int = -1,
        matrix: UiMatrix = UiMatrix(360)
    ) {
        edited.clear()
        noteClicks.clear()
        rule.setContent {
            drawRoot = LocalView.current
            matrix.RenderIn(LocalDensity.current.density) {
                MessageList(
                    messages = messages,
                    editingIndex = editingIndex,
                    onReorder = { _, _ -> },
                    onEdit = { edited.add(it) },
                    onDelete = { },
                    noteText = noteText,
                    onEditNote = { noteClicks.add(1) }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /**
     * 按展示顺序读出聊天行与气泡的边界（0x0 的那些被 laid 筛掉，且筛了看得见）。
     *
     * ⚠ 两颗粒都从**未合并树**取：气泡在合并树里根本不存在（类 KDoc 那条），而"行"与"气泡"
     *   必须在同一棵树里量，否则分子分母读的不是同一个坐标系。
     */
    private fun rowsAndBubbles(): Pair<List<SemanticsProbe.Target>, List<SemanticsProbe.Target>> =
        laidTagged(MESSAGE_ROW_TEST_TAG) to laidTagged(MESSAGE_BUBBLE_TEST_TAG)

    /** 未合并树里按 testTag 取全部边界，按 topDp 排序（= LazyColumn 条目顺序，同一台仪器在滑动删除那族验过） */
    private fun laidTagged(tag: String): List<SemanticsProbe.Target> =
        probe.laid(
            rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes().map { probe.of(it) }
        ).sortedBy { it.topDp }

    /** 未合并树里第 [index] 颗的完整读数：取不到就抛，不许静默给一个 0x0 让判据空过 */
    private fun nodeOf(tag: String, index: Int): SemanticsProbe.Target =
        probe.of(rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[index].fetchSemanticsNode())

    /**
     * 正文那一颗（未合并树里 `Text` 自己的盒子）。
     * 内边距只能从**两颗的边界差**量：语义树里没有 padding 这个属性，源码里读那个数又证明不了它被用上。
     */
    private fun bodyNode(content: String): SemanticsProbe.Target =
        probe.of(rule.onAllNodes(hasText(content), useUnmergedTree = true)[0].fetchSemanticsNode())

    /** 最大单通道差：把"屏幕上这一色"与"token 该是那一色"接起来（±2 = round 之差） */
    private fun channelDelta(a: Int, b: Int): Int =
        listOf(16, 8, 0).maxOf { shift -> kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) }

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /**
     * 取这一颗节点**真的画出来**的底色（不透明像素的众数）。
     *
     * ⚠ 一律走未合并树：合并树里气泡不存在，`capture` 会在 `fetchSemanticsNode` 那一步抛
     *   AssertionError（本机 13:05 那三格红在 [inkOf] 里就是这个形状，不是红在产品配色上）。
     * 顺序按 `onAllNodes` 的树遍历走——与 `MessageRowSwipeDeleteTest` 里"滑第 [1] 行就删第 [1] 行"
     * 同一台仪器上验过的同一条口径（LazyColumn 的子节点顺序就是条目顺序）。
     */
    private fun inkOf(tag: String, index: Int): Int {
        val root = checkNotNull(drawRoot) {
            "$tag 这一格没记下绘制根——LocalView 没在 composition 里交出来，这台仪器没接上渲染"
        }
        // capture 自己在"节点没有面积 / 落在绘制根之外"时抛，backgroundOf 在"读不到不透明像素、
        // 或众数色只占不到四分之一"时抛——这台仪器接不上电的时候不许给一个看起来对的数
        return PixelContrastMeter.backgroundOf(
            PixelContrastMeter.capture(
                rule, root,
                rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[index]
            )
        )
    }

    /** 备注那一行节点身上的整段文本（前缀 + 正文；省略只发生在绘制阶段，语义树永远报完整原串） */
    private fun noteAnnouncedText(): String {
        val nodes = rule.onAllNodesWithText(ADVISOR_NOTE_PREFIX, substring = true).fetchSemanticsNodes()
        assertEquals("备注那一行该恰好一个文本节点：" + nodes.size, 1, nodes.size)
        return nodes.single().config.getOrNull(SemanticsProperties.Text)?.joinToString("") ?: ""
    }

    /**
     * 行内左右留白（`MessageDimens.ROW_HPAD_DP`）：**只在这一族里由"左右贴边"那格钉死成 4dp**，
     * "八成"那格拿它算分母。两处一起红是刻意的耦合——行内留白被人动了，分母那条线也跟着动。
     */
    private val rowHpadDp = 4f

    /** 行内可用宽 = 行外包络宽 - 左右两笔行内留白（第5节第1条 说的"可用宽度"是这一个，不是行宽） */
    private fun availableDp(row: SemanticsProbe.Target): Float = row.widthDp - 2f * rowHpadDp

    // ═══════════  版式：底色只在气泡身上，宽度按内容并夹在八成 ═══════════

    /**
     * 底色不在整行上：行仍然铺满可用宽，气泡只吃内容宽。
     * 反例：给气泡 `fillMaxWidth()` ⇒ 气泡宽等于行内可用宽，红；
     * 反例：把气泡钉成某个定值（旧那 236dp）⇒ 短句那一颗不等于内容宽，同样红。
     * ⚠ "把底色挪回 Row 那一层"这一笔**这一格看不住**（气泡盒子自己仍按内容收，几何一个字没变）——
     *   那是 [the row itself stays unpainted so only the bubble carries ink] 的活，两格合起来才封住。
     */
    @Test
    fun `a short message hugs its own content instead of filling the row`() {
        mount(listOf(shortHer, shortMe))
        val (rows, bubbles) = rowsAndBubbles()
        assertEquals("两行都该有气泡锚点：" + bubbles.joinToString { it.describe() }, 2, bubbles.size)
        rows.forEach { row ->
            assertTrue("行自己仍该铺满可用宽（它是手势落点）：" + row.describe(), row.widthDp > 300f)
        }
        bubbles.forEachIndexed { i, bubble ->
            val row = rows[i]
            assertTrue(
                "短句气泡该按内容宽收，而不是占满整行（第 $i 行：气泡 " + bubble.describe() +
                    " / 行 " + row.describe() + "）",
                bubble.widthDp < row.widthDp * 0.5f
            )
        }
    }

    /**
     * 长文的上限 = **行内可用宽**的八成（合同 第5节第1条"可用宽度约 80%"），越线的部分自己换行。
     *
     * 为什么这条判据只能这么算（这一格 2026-10-04 之前红在两处，都不在产品身上）：
     * 1. **分母**：第5节第1条 那句是"可用宽度"，而 `MessageRow` 的写法是 `BoxWithConstraints` 里
     *    `widthIn(max = maxWidth * 0.8f)`——`maxWidth` 是行**扣掉左右各 4dp 留白之后**交给内容的那一段。
     *    旧判据拿"外包络行宽"当分母，凭空多算 2×4×0.8 = 6.4dp，容差只有 2dp ⇒ 必红。
     *    现在分母 = 行宽 - 8dp（那 8dp 由"左右贴边"那一格实测钉死），两格读同一条线。
     * 2. **上限是"夹"不是"铺"**：CJK 换行按整字进宽，最长那一行 = `floor((cap-横2笔内边距)/字宽)×字宽`，
     *    于是实测气泡宽必然落在 `[cap - 一个字宽, cap]`。**钉成"正好 cap ±2dp"是把文字量子的存在当成画错**。
     *    字宽不许抄常数：本轮拿**同一屏那颗粒短气泡**现量（正文盒宽 ÷ 字数 = 一个字的进宽），
     *    所以换字体、换字号档都跟着走，不留一个悬空的魔法数字。
     * 3. 两颗不同内容的长文（[longMe] 与 [longHer]）必须夹成**同一个宽度**（差 ≤ 一个字宽）：
     *    这条说的是"夹它们的是同一条公共线"，而不是各自贴内容。
     * 反例：`fillMaxWidth()` ⇒ 越过 cap，第一句红；
     * 反例：定宽 236dp ⇒ 低于 `cap - 字宽`，第二句红；
     * 反例：分母改成 0.75 或 0.9 ⇒ 一颗红在天花板、另一颗红在地板（两头的哨兵各守一边）。
     */
    @Test
    fun `a long message is capped at eight tenths of the available row width`() {
        mount(listOf(shortHer, longMe, longHer))
        val (rows, bubbles) = rowsAndBubbles()
        assertEquals("一短两长三颗气泡都要读得到：" + bubbles.joinToString { it.describe() }, 3, bubbles.size)
        val cap = availableDp(rows[1]) * 0.8f

        // 字宽现量：短气泡那颗正文盒里就是"在吗"两个等宽汉字
        val glyph = bodyNode(shortHer.content).widthDp / shortHer.content.length
        assertTrue("现量出来的字宽不像话（$glyph dp），这一格的地板与天花板都失去意义", glyph in 4f..40f)

        val lower = cap - glyph - 1f
        bubbles.drop(1).forEachIndexed { i, bubble ->
            assertTrue(
                "长文不许越过八成那条线（cap=$cap dp，实测 " + bubble.describe() + " / 行 " +
                    rows[i + 1].describe() + "）——越线就是整行卡",
                bubble.widthDp <= cap + 1f
            )
            assertTrue(
                "长文必须被那条线夹住，不是被某个更小的定值夹住（地板=$lower dp，实测 " +
                    bubble.describe() + "，字宽 $glyph dp）",
                bubble.widthDp >= lower
            )
            // 撞了宽度线才叫"长文换行"：高度必须高出单行那一档（一行 ≈ 20sp 行高 + 竖 6dp×2）
            assertTrue(
                "长文该在八成宽里换成多行，而不是把一行拉到底：" + bubble.describe(),
                bubble.heightDp > 40f
            )
        }
        assertTrue(
            "两颗互不相干的长文必须夹成同一个宽度（差 ≤ 一个字宽）：现在是 " +
                bubbles[1].describe() + " / " + bubbles[2].describe(),
            kotlin.math.abs(bubbles[1].widthDp - bubbles[2].widthDp) <= glyph + 1f
        )
        assertTrue(
            "短的那一颗必须在同一条线**以内**按内容收（否则'八成'就是把气泡钉死了）：" +
                bubbles[0].describe() + " vs cap=" + cap,
            bubbles[0].widthDp < lower
        )
    }

    /**
     * 上限跟着**可用宽**走，不是钉死的定值：同一颗长文搬到 600dp 那一档，
     * 天花板与地板都按 600 那一档的行内可用宽重算，两头的哨兵同上一条格一对。
     * 反例：把气泡钉成任何定值（236dp 那一族）⇒ 这一档下读不到 `cap - 字宽` 那么宽，红；
     * 反例：压根没有上限（fillMaxWidth）⇒ 越过 600 那一档的天花板，红。
     */
    @Test
    fun `the eight tenths line follows the available width instead of a fixed box`() {
        mount(listOf(shortHer, longMe), matrix = UiMatrix(600))
        val (rows, bubbles) = rowsAndBubbles()
        val row = rows[1]
        val bubble = bubbles[1]
        val cap = availableDp(row) * 0.8f
        val glyph = bodyNode(shortHer.content).widthDp / shortHer.content.length
        assertTrue(
            "600 这一档也必须停在八成线内（cap=$cap dp，实测 " + bubble.describe() + " / 行 " +
                row.describe() + "）",
            bubble.widthDp <= cap + 1f
        )
        assertTrue(
            "600 这一档的实测必须贴住它自己那条线（地板=${cap - glyph - 1f} dp，实测 " +
                bubble.describe() + "）——宽一档线不动就是定宽盒子",
            bubble.widthDp >= cap - glyph - 1f
        )
        assertTrue(
            "600 那一档的实测必须宽过 360 那一档可能有的天花板（约 270dp），实测 " + bubble.describe(),
            bubble.widthDp > 300f
        )
    }

    /**
     * 左右两列真的分开了：HER 贴左、ME 贴右。
     * 反例：两列都用同一种对齐（Arrangement.Start）⇒ ME 那颗贴不到行右侧，红；
     * 反例：把行内横向留白写歪（一侧没留）⇒ 差值对不上，红（这一格就是那 4dp 的出处）。
     */
    @Test
    fun `her bubble sits on the left and mine sits on the right`() {
        mount(listOf(shortHer, shortMe))
        val (rows, bubbles) = rowsAndBubbles()
        val her = bubbles[0]
        val me = bubbles[1]
        assertEquals("行与气泡该一一对上：" + bubbles.joinToString { it.describe() }, 2, rows.size)
        assertEquals(
            "「她」的气泡左边界该贴住行的左边距（行 " + rows[0].describe() + " / 气泡 " + her.describe() + "）",
            rows[0].leftDp + rowHpadDp, her.leftDp, 1f
        )
        val meRight = me.leftDp + me.widthDp
        val rowRight = rows[1].leftDp + rows[1].widthDp
        assertEquals(
            "「我」的气泡右边界该贴住行的右边距（行 " + rows[1].describe() + " / 气泡 " + me.describe() + "）",
            rowRight - rowHpadDp, meRight, 1f
        )
        assertTrue(
            "两列的左边界必须真的差得开（同对齐的写法会假绿）：" + her.describe() + " / " + me.describe(),
            me.leftDp - her.leftDp > 150f
        )
    }

    /**
     * 第5节第1条 给的气泡内边距起点：**横 12 / 竖 6**。
     * 语义树里没有 padding 这个属性，所以只能量**两颗节点的边界差**（正文盒 vs 气泡盒）；
     * 源码里读那个数又证明不了它真被用上——两头都不算，只有边界差算。
     * 反例：12 改 16 ⇒ 左差与宽差一起红；6 改 8 ⇒ 上差与高差一起红；
     * 反例：把内边距从气泡挪到行上（气泡自己贴字、行去留白）⇒ 四个差值一起对不上，红；
     * 反例：只留横不留竖 ⇒ 高差回落到 0，红。
     */
    @Test
    fun `the bubble keeps twelve dp across and six dp down of clear margin`() {
        mount(listOf(shortHer))
        val bubble = nodeOf(MESSAGE_BUBBLE_TEST_TAG, 0)
        val body = bodyNode(shortHer.content)
        val leftGap = body.leftDp - bubble.leftDp
        val topGap = body.topDp - bubble.topDp
        assertTrue("横内边距（左）该是 12dp，实测 $leftGap（气泡 " + bubble.describe() + " / 正文 " + body.describe() + "）",
            kotlin.math.abs(leftGap - 12f) <= 1f)
        assertTrue("竖内边距（上）该是 6dp，实测 $topGap", kotlin.math.abs(topGap - 6f) <= 1f)
        assertTrue("左右两笔横内边距都得在（合计 24dp），实测 ${bubble.widthDp - body.widthDp}",
            kotlin.math.abs((bubble.widthDp - body.widthDp) - 24f) <= 2f)
        assertTrue("上下两笔竖内边距都得在（合计 12dp），实测 ${bubble.heightDp - body.heightDp}",
            kotlin.math.abs((bubble.heightDp - body.heightDp) - 12f) <= 2f)
    }

    /**
     * 第5节第1条 的垂直间距：**条目之间 4dp**。量的是相邻两行的边界差，不是源码里那个数。
     * 反例：`spacedBy(4)` 改成 8 或 0 ⇒ 差值对不上，红；
     * 反例：给行自己再垫竖向 padding（旧的"整行垫到 48dp 高"那一族）⇒ 行高把间距吃掉，同样红。
     */
    @Test
    fun `two consecutive rows sit four dp apart`() {
        mount(listOf(shortHer, shortMe))
        val rows = laidTagged(MESSAGE_ROW_TEST_TAG)
        assertEquals("两行都要有读数：" + rows.joinToString { it.describe() }, 2, rows.size)
        val gap = rows[1].topDp - (rows[0].topDp + rows[0].heightDp)
        assertTrue(
            "条目垂直间距该是 4dp，实测 $gap dp（" + rows[0].describe() + " / " + rows[1].describe() + "）",
            kotlin.math.abs(gap - 4f) <= 1f
        )
    }

    /**
     * 底色不在整行身上：第5节第1条 原话"不是整行白底卡"。
     *
     * 这一格补的是几何格看不住的那一笔——把 `.background(...)` 从气泡挪回 Row 那一层，
     * 气泡盒子的宽、高、贴边**一个字都不会变**（几何格因此照旧绿），变的只是"整行都被涂满"。
     * 所以这里量整行的像素众数：行左右那两段留白（各 4dp）+ 气泡之外的大片空地必须仍是消息卡
     * 容器底 [SurfaceInset]；而同一屏上气泡自己的读数必须与它不同（不同才是"色只在气泡身上"）。
     * 反例：底色挂回整行（旧形状）⇒ 整行众数读成卡白/微信绿，与 SurfaceInset 差十几个通道单位，红；
     * 反例：气泡没涂色（撤掉左右分色）⇒ 后半句"两处读数必须不同"红。
     * ⚠ 挑短句夹具：长文那颗气泡会盖掉行的大部分面积，众数就换成气泡色了（量错对象）。
     */
    @Test
    fun `the row itself stays unpainted so only the bubble carries ink`() {
        mount(listOf(shortHer, shortMe))
        for (index in 0..1) {
            val rowInk = inkOf(MESSAGE_ROW_TEST_TAG, index)
            assertTrue(
                "第 $index 行整行的底色该仍是消息卡容器底 ${hex(SurfaceInset.toArgb())}，实到 ${hex(rowInk)} " +
                    "——底色回到整行就是  明令撤掉的那张'整行白底卡'",
                channelDelta(rowInk, SurfaceInset.toArgb()) <= 2
            )
            val bubbleInk = inkOf(MESSAGE_BUBBLE_TEST_TAG, index)
            assertTrue(
                "第 $index 行气泡自己的底色与整行读数一模一样（${hex(bubbleInk)}）：" +
                    "要么气泡根本没涂色，要么整行都被涂成同一色——两样都不是左白右绿",
                channelDelta(rowInk, bubbleInk) > 2
            )
        }
    }

    // ═══════════  底色：档位表 + 真的涂出来那颗色 ═══════════

    /**
     * 底色档位表本身（纯函数）：`isMine` 真假分流、状态盖过角色、**没有第四档**。
     * 反例：`isMine` 两支写反 ⇒ 前两句红；
     * 反例：把状态那一档挪到角色之后（`isMine -> Outgoing` 先命中）⇒ 编辑中的那条不再换色，红；
     * 反例：给旧 `Role.IDEA` 开一颗第三种色 ⇒ 档位集多出第四档，最后那句红（这一句就是"备注不许
     *        冒充对话"在档位上的钉子）；
     * 反例：两列并成同一档（比如都写 Incoming）⇒ 那句"两列必须真的分给两种档"红。
     */
    @Test
    fun `the ink table splits on isMine and state wins over role`() {
        assertEquals(BubbleInk.Outgoing, bubbleInkOf(isMine = true, isEditing = false, isDragged = false))
        assertEquals(BubbleInk.Incoming, bubbleInkOf(isMine = false, isEditing = false, isDragged = false))
        assertTrue(
            "两列必须真的分给两种档，否则位置之外再没有东西说明谁在说话",
            BubbleInk.Outgoing != BubbleInk.Incoming
        )
        listOf(true, false).forEach { mine ->
            assertEquals("编辑中该走状态档（mine=$mine）", BubbleInk.InState,
                bubbleInkOf(mine, isEditing = true, isDragged = false))
            assertEquals("被拖着也该走状态档（mine=$mine）", BubbleInk.InState,
                bubbleInkOf(mine, isEditing = false, isDragged = true))
        }
        val seen = mutableSetOf<BubbleInk>()
        for (mine in listOf(true, false)) {
            for (editing in listOf(true, false)) {
                for (dragged in listOf(true, false)) {
                    seen += bubbleInkOf(mine, editing, dragged)
                }
            }
        }
        assertEquals("八种组合跑完只许出现这三档（第四档就是给旧想法留的第三种气泡）：$seen",
            setOf(BubbleInk.Incoming, BubbleInk.Outgoing, BubbleInk.InState), seen)
    }

    /**
     * 涂在屏幕上的确实是那两色，而且**只在气泡身上**：ME = 合同那颗微信绿 #95EC69、HER = 卡白。
     * 这一格补的是纯函数那一半看不见的那件事——档位表对了，但 `MessageRow` 没照它涂。
     * 反例：绿换成别的品牌色/浅蓝 ⇒ 与 #95EC69 的读数对不上，红；
     * 反例：把两列涂成同一色（撤掉左右分色）⇒ "两列读成同一个颜色"那句先红；
     * 反例：这台仪器没接上渲染 ⇒ [inkOf] 直接抛（不静默给一个"看起来对"的数）。
     * ⚠ 底色挂回整行那一笔由 [the row itself stays unpainted so only the bubble carries ink] 守，
     *   这里守的是"哪一格该是哪一色"。
     */
    @Test
    fun `mine paints the wechat green while hers paints the card white`() {
        mount(listOf(shortHer, shortMe))
        val hers = inkOf(MESSAGE_BUBBLE_TEST_TAG, 0)
        val mine = inkOf(MESSAGE_BUBBLE_TEST_TAG, 1)
        assertTrue(
            "两列读成了同一个底色（底色分流没了）：她=${hex(hers)} 我=${hex(mine)}",
            channelDelta(hers, mine) > 8
        )
        assertTrue(
            "「我」这一列该是合同那颗微信绿 ${hex(ChatOutgoingBg.toArgb())}，实测 ${hex(mine)}",
            channelDelta(mine, ChatOutgoingBg.toArgb()) <= 2
        )
        assertTrue(
            "「她」这一列该是卡白 ${hex(SurfaceCard.toArgb())}，实测 ${hex(hers)}",
            channelDelta(hers, SurfaceCard.toArgb()) <= 2
        )
    }

    /**
     * 正在编辑的那一条走品牌浅蓝——状态那一档**盖过**角色（不是第三种人色）。
     * 挑的是 ME 那一行：它平时必须是绿，编辑态要读成浅蓝；两个读数互不相干才算换过。
     * 反例：编辑态没接上（继续走角色色）⇒ 读到微信绿，红；
     * 反例：编辑态画成第三种"角色色"（比如灰底 + 描边冒充卡片）⇒ 与 PrimaryLight 对不上，红。
     */
    @Test
    fun `the row being edited wears the state ink instead of its role ink`() {
        mount(listOf(shortHer, shortMe), editingIndex = 1)
        val editing = inkOf(MESSAGE_BUBBLE_TEST_TAG, 1)
        val untouched = inkOf(MESSAGE_BUBBLE_TEST_TAG, 0)
        assertTrue(
            "编辑中这一颗该是品牌浅蓝 ${hex(PrimaryLight.toArgb())}，实测 ${hex(editing)}",
            channelDelta(editing, PrimaryLight.toArgb()) <= 2
        )
        assertTrue(
            "没被编辑的那一条不许跟着换色（状态串到别的行）：${hex(untouched)} vs ${hex(editing)}",
            channelDelta(untouched, editing) > 8
        )
    }

    // ═══════════  尾部那一行灰字 ═══════════

    /**
     * 备注就是**一行灰字铺在卡底上**，背后没有气泡、也没有卡中卡。
     * 反例：给备注重新套一颗气泡/浅色卡（旧 IdeaSection 那个形状）⇒ 众数色读成气泡底而非卡底，红；
     * 反例：备注又画成"第三种颜色的聊天气泡"⇒ 同一条红。
     */
    @Test
    fun `the note line sits on the card with no bubble behind it`() {
        mount(listOf(shortHer, idea), noteText = "先哄两句")
        val noteInk = inkOf(ADVISOR_NOTE_TEST_TAG, 0)
        val bubbleInk = inkOf(MESSAGE_BUBBLE_TEST_TAG, 0)
        assertTrue(
            "备注那一行背后不该再涂一层：读到的底 ${hex(noteInk)} 该就是消息卡的容器底 " +
                hex(SurfaceInset.toArgb()),
            channelDelta(noteInk, SurfaceInset.toArgb()) <= 2
        )
        assertTrue(
            "备注读到的色与气泡色一模一样 = 它被画成了第三颗气泡：${hex(bubbleInk)}",
            channelDelta(noteInk, bubbleInk) > 2
        )
    }

    /**
     * 备注钉在**最后一个聊天气泡下面**（ 原话），而且它自己是 48dp 热区的一颗入口。
     * 反例：把备注挂到列表上方或塞进 LazyColumn 尾部跟着滚 ⇒ 第一句红；
     * 反例：只留一行裸文字（旧空态那颗被投诉的 28dp 形状）⇒ 热区那句红。
     */
    @Test
    fun `the note sits under the last bubble and keeps a full touch row`() {
        mount(listOf(shortHer, shortMe, idea), noteText = "先哄两句")
        val bubbles = laidTagged(MESSAGE_BUBBLE_TEST_TAG)
        val note = nodeOf(ADVISOR_NOTE_TEST_TAG, 0)
        val lastBubble = bubbles.last()
        assertTrue(
            "备注该在最后一条气泡下面（气泡 " + lastBubble.describe() + " / 备注 " + note.describe() + "）",
            note.topDp >= lastBubble.topDp + lastBubble.heightDp
        )
        assertTrue("备注该占满自己那一行的宽（它就是热区）：" + note.describe(), note.widthDp > 300f)
        probe.assertTargetsMeetFloor(listOf(note), 48f, "军师备注那一行")
    }

    /**
     * 「一行」是这条合同的原话，所以这一格量的是**可见尺寸**而不是文案：
     * 大字号（2.0 档）+ 一定撞出宽度上限的长备注，仍只许占一行的高度。
     *
     * ⚠ 本项目已知：`maxLines + Ellipsis` 裁切时语义树永远报完整原串——所以这里**不写**
     *   "读不到省略号"那种没牙的文本断言，改用同组件的自然高对照：
     *   反例：把 `maxLines` 放宽到 2 ⇒ 2.0 档那一行灰字量到两行高（≈88dp），红；
     *   反例：把它垫成两行高的卡（旧 IdeaSection 那一档）⇒ 同一条红；
     *   反例：把字号档位写死（不受 LocalDensity.fontScale 走）⇒ 短/长两格读回同一个"永远 48"，
     *         此时下面那句"短备注也只有这一档高"与"长备注不许长高"一起说明量到的是盒子而不是裁切。
     */
    @Test
    fun `a long note stays one display line even at the largest font step`() {
        mount(listOf(shortHer), noteText = longNote, matrix = UiMatrix(360, fontScale = 2.0f))
        val note = nodeOf(ADVISOR_NOTE_TEST_TAG, 0)
        assertTrue(
            "长备注在 2.0 字档只许占一行（48dp 热区那一档），实测 " + note.describe(),
            note.heightDp in 44f..53f
        )
    }

    /** 同一颗组件的自然高对照：短备注也只有这一档高（长的那格红时，这一格说清盒子本身没变） */
    @Test
    fun `a short note takes exactly the same box`() {
        mount(listOf(shortHer), noteText = "先听我说完", matrix = UiMatrix(360, fontScale = 2.0f))
        val note = nodeOf(ADVISOR_NOTE_TEST_TAG, 0)
        assertTrue("短备注也该是那一颗 48dp 热区行，实测 " + note.describe(), note.heightDp in 44f..53f)
    }

    /**
     * 折叠口径（纯函数）：宿主那份在前、旧想法行补在后面、按行去重、空白全丢、什么都没剩时给 null。
     * 反例：不去重 ⇒ "同一句出现两遍"那两句红（旧版就是这么把一句补充画出两条的）；
     * 反例：只读宿主那份 ⇒ 旧想法行整个不见（用户看不到自己写过的东西），红；
     * 反例：空备注返回 "" 而不是 null ⇒ 最后两句红（`MessageList` 会画出一行只有前缀的空灰字）。
     */
    @Test
    fun `folding the note keeps one copy of each line and gives null when nothing is left`() {
        assertEquals("先哄两句", foldAdvisorNote(listOf("先哄两句"), null))
        assertEquals("宿主那份排前面", "别太刻意\n先哄两句", foldAdvisorNote(listOf("先哄两句"), "别太刻意"))
        assertEquals("同一句只出现一次", "先哄两句", foldAdvisorNote(listOf("先哄两句"), "先哄两句"))
        assertEquals("多行里重复的那一行也去重", "甲\n乙", foldAdvisorNote(listOf("乙"), "甲\n乙"))
        assertEquals("空白逐行丢掉", "甲\n乙", foldAdvisorNote(listOf("  甲 ", "", "\n乙\n"), null))
        assertNull("宿主没接线、也没有旧想法 ⇒ 这一行根本不画", foldAdvisorNote(emptyList(), null))
        assertNull("全是空白也只交回 null，不是一串空格", foldAdvisorNote(listOf("  ", ""), "   \n "))
    }

    /**
     * 旧 `Role.IDEA` 数据不进聊天顺序、也不画成第三颗气泡：它折进尾部那一行灰字。
     * 反例：把 IDEA 塞回 LazyColumn 当一行气泡 ⇒ 气泡数从 2 变 3，第一句红；
     * 反例：只画宿主那份、旧想法行被丢掉 ⇒ 备注文本里读不到那句，红；
     * 反例：已提交的那句又被草稿重画一遍 ⇒ 备注文本读出"先哄两句 先哄两句"，那句逐字相等红。
     */
    @Test
    fun `legacy idea data folds into the note line instead of a third bubble`() {
        mount(listOf(shortHer, idea, shortMe), noteText = "先哄两句")
        val bubbles = rule.onAllNodes(hasTestTag(MESSAGE_BUBBLE_TEST_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("聊天行只该有 HER/ME 那两颗气泡：" + bubbles.size, 2, bubbles.size)
        val notes = rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG)).fetchSemanticsNodes()
        assertEquals("尾部该有一行灰字备注：" + notes.size, 1, notes.size)
        assertEquals(
            "同一句备注只许出现一遍（前缀 + 正文逐字，多一遍就是草稿又重画了一次）：",
            ADVISOR_NOTE_PREFIX + "先哄两句",
            noteAnnouncedText()
        )
    }

    /**
     * 宿主**没接线**（noteText=null，旧面板形状）时旧想法照样有出口，而那一块《我的想法》标题不再出现。
     * 反例：只在宿主接线时才折备注 ⇒ 未接线的宿主里那条旧想法整个消失，红；
     * 反例：`IdeaSection` 的标题/容器没换干净 ⇒ 后两句红（标题又长出一块，或画出了两行灰字）。
     */
    @Test
    fun `an unwired host still folds the idea into one note line and draws no section title`() {
        mount(listOf(shortHer, idea))
        val bubbles = rule.onAllNodes(hasTestTag(MESSAGE_BUBBLE_TEST_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("聊天气泡只该有 HER 那一颗：" + bubbles.size, 1, bubbles.size)
        val notes = rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG)).fetchSemanticsNodes()
        assertEquals("旧想法要在尾部那行灰字里：" + notes.size, 1, notes.size)
        assertEquals("备注正文：" + noteAnnouncedText(), ADVISOR_NOTE_PREFIX + "先哄两句", noteAnnouncedText())
        val sectionTitle = app.getString(R.string.idea_section_title)
        assertTrue(
            "旧的《$sectionTitle》那一块标题必须整块换掉：" +
                rule.onAllNodes(hasText(sectionTitle)).fetchSemanticsNodes(),
            rule.onAllNodes(hasText(sectionTitle)).fetchSemanticsNodes().isEmpty()
        )
    }

    /**
     * 只有备注、还没有真实聊天的那一轮也必须画这行灰字（ 原话要的就是这一格），
     * 而空态那颗"主动发一句"入口不能因为多了备注就不见了。
     * 反例：备注只挂在有聊天的那个分支 ⇒ 第一句红（用户首轮写了备注却看不见）；
     * 反例：换备注件时把空态那颗入口一起删了 ⇒ 第二、三句红。
     */
    @Test
    fun `the note line still shows when there is no chat at all`() {
        mount(emptyList(), noteText = "先听我说完")
        val notes = rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG)).fetchSemanticsNodes()
        assertEquals("空态也该看到那行灰字：" + notes.size, 1, notes.size)
        val targets = probe.actionableTargets(rule, "消息列表空态（带备注）")
        assertEquals("空态该只剩两处入口（主动发 + 编辑备注）：" + targets.joinToString { it.describe() },
            2, targets.size)
        assertTrue(
            "空态那颗「主动发」入口必须还在：" + targets.joinToString { it.announced },
            targets.any { it.label == app.getString(R.string.proactive_empty_send_one) }
        )
        val note = probe.of(notes.first())
        val entry = targets.first { it.label == app.getString(R.string.proactive_empty_send_one) }
        assertTrue("备注仍在最后一条内容下面：" + note.describe(), note.topDp > entry.topDp)
    }

    /**
     * 备注这一行的唯一出口 = 去编辑完整备注，而且读屏念得出它是干什么的。
     * 反例：点击错接成"编辑第 N 条聊天"⇒ onEdit 那条红；
     * 反例：把灰字画成一节标题 + 多条条目（旧 IdeaSection 形状）⇒ 可点节点数涨过 3，红。
     */
    @Test
    fun `tapping the note line opens the note editor and nothing else`() {
        mount(listOf(shortHer, shortMe, idea), noteText = "先哄两句")
        val targets = probe.actionableTargets(rule, "回复消息卡（带备注）")
        assertEquals("两行聊天 + 一行备注，只该三处入口：" + targets.joinToString { it.describe() },
            3, targets.size)
        rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点备注只投一次编辑：" + noteClicks, listOf(1), noteClicks)
        assertTrue("点备注不该把某条聊天选进编辑位：" + edited, edited.isEmpty())
    }

    // ═══════════ 撤掉可见标签之后的身份与热区 ═══════════

    /**
     * 可见的「她/我」标签撤掉了，但角色不能因此从读屏里消失：
     * 行自己那颗语义节点必须报出"她的消息 / 我的消息"（文案从资源现读，测试侧不留第二份），
     * 而且这一句挂在**带点击落点的那一颗**节点上——挂在气泡里就要赌合并行为。
     * 反例：删标签时把 contentDescription 一起删了 ⇒ 读不到，红；
     * 反例：把角色描述挪进气泡子节点（点击落点留在行上）⇒ 最后那句"必须落在同一颗粒上"红，
     *        TalkBack 聚焦到行时念得出的是文案而不是身份；
     * 反例：两列写成同一句读屏名 ⇒ 那句"不许是同一句"红。
     * ⚠ 这一族读的是**合并树**：判的正是"读屏聚焦一行时念得出什么"，换树就换成了另一件事。
     */
    @Test
    fun `the visible role marks are gone but the screen reader still names the role`() {
        mount(listOf(shortHer, shortMe))
        // 标签文案按**数据里那两个字**找（enum 的 label 就是过去画在 chip 上的那一句），
        // 测试侧不留第二份字面量，也不会因为资源改名而假绿。
        val herMark = ChatMessage.Role.HER.label
        val meMark = ChatMessage.Role.ME.label
        assertTrue(
            "屏上不该再看得见「$herMark」这个角色标签：" +
                rule.onAllNodes(hasText(herMark, substring = true)).fetchSemanticsNodes().joinToString(),
            rule.onAllNodes(hasText(herMark, substring = true)).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "屏上不该再看得见「$meMark」这个角色标签",
            rule.onAllNodes(hasText(meMark, substring = true)).fetchSemanticsNodes().isEmpty()
        )
        val nodes = rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG)).fetchSemanticsNodes()
        val rows = probe.laid(nodes.map { probe.of(it) }).sortedBy { it.topDp }
        val herWords = app.getString(R.string.a11y_message_her)
        val meWords = app.getString(R.string.a11y_message_me)
        assertTrue("第一行（她）的读屏名里要点名这一句：" + rows[0].describe(),
            rows[0].contentDescriptions.contains(herWords))
        assertTrue("第二行（我）的读屏名里要点名这一句：" + rows[1].describe(),
            rows[1].contentDescriptions.contains(meWords))
        assertTrue("她/我的读屏名不许是同一句：$herWords / $meWords", herWords != meWords)
        // 身份与点击落点必须在同一颗粒上（撤掉可见标签之后这是唯一的身份出口）
        val herRowNode = nodes.first { probe.of(it).contentDescriptions.contains(herWords) }
        assertTrue(
            "读屏名所在那颗节点必须同时是点击落点（否则 TalkBack 聚焦的不是带身份的那一层）：" +
                herRowNode.config,
            herRowNode.config.contains(SemanticsActions.OnClick)
        )
    }

    /**
     * 撤掉叉号之后，一行只剩"点这一行=编辑"这一颗可交互节点。
     * 反例：把叉号或第二个入口画回行里 ⇒ 可交互数从 2 涨到 4，红。
     */
    @Test
    fun `each row stays exactly one actionable node`() {
        mount(listOf(shortHer, shortMe))
        val targets = probe.actionableTargets(rule, "消息列表")
        assertEquals(
            "两行只该有两次点击落点：" + targets.joinToString { it.describe() },
            2, targets.size
        )
        targets.forEach { assertTrue("落点该有名字（撤掉标签后尤其要）：" + it.describe(), it.labeled) }
    }

    /**
     * 编辑落点按**原列表下标**算：中间夹着一条不进聊天顺序的旧想法时，
     * 点第二条聊天行报的仍是它在持有者列表里的那一位（2），不是展示位（1）。
     * 反例：把 onEdit 的口径换成展示下标 ⇒ 这里读到 1，红；用户拿到的是"改第 2 条实际改到第 3 条"。
     */
    @Test
    fun `tapping a row reports the index in the holder list, not the display slot`() {
        mount(listOf(shortHer, idea, shortMe), noteText = "先哄两句")
        val rows = rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG)).fetchSemanticsNodes()
        assertEquals("聊天行只该有 HER/ME 那两条：" + rows.size, 2, rows.size)
        rule.onAllNodes(hasTestTag(MESSAGE_ROW_TEST_TAG))[1].performClick()
        assertEquals("点第二条聊天行该交回原列表下标 2：" + edited, listOf(2), edited)
    }

    /**
     * 长文折叠这条既有能力没被"缩小条目"顺手删掉：超过阈值的消息默认只给两行 + 一处**紧凑**出口。
     *
     * ⚠ 这里判的是"出口存在、点它真的换行、再点回去"，全部从**未合并树**读：
     *   合并树里"展开"那一颗被吸进行节点，`onAllNodes(hasText("展开"))` 命中的是**整行**，
     *   performClick 打出去的是 `onEdit`（本机 13:05 那轮 `:596` 绿而 `:598` 红就是这个形状——
     *   存在句因为文案被合并上来而假绿，量高度那句却读不到气泡）。
     *   所以这一格加两条有牙的正向对照：点完"展开"**不许**把这条选进编辑位（[edited] 必须空），
     *   否则说明落点又回到整行；"收起"那一颗必须**只有一颗，而且仍待在气泡的内边距以内**
     *   ——它是一处紧凑动作，不是一整行按钮。
     * 反例：撤掉折叠那一段 ⇒ 找不到"展开"，第一句红；
     * 反例：把折叠换成只裁不展（点一下没换文本）⇒ 高度不变，第二句红；
     * 反例：把出口做成一整行大按钮 ⇒ 它跌出气泡边界，那句"必须还在气泡以内"红；
     * 反例：把出口的点击错接到整行 ⇒ "不许投编辑"那句当场红。
     */
    @Test
    fun `a long message still offers the expand exit`() {
        mount(listOf(foldedMe))
        val foldNodes = rule.onAllNodes(hasText(expandWords), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("长文必须还有展开这一处出口：" + foldNodes.size, foldNodes.isNotEmpty())
        val before = nodeOf(MESSAGE_BUBBLE_TEST_TAG, 0)
        rule.onAllNodes(hasText(expandWords), useUnmergedTree = true)[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        val expanded = nodeOf(MESSAGE_BUBBLE_TEST_TAG, 0)
        assertTrue(
            "展开之后这一颗该真的长高（折叠只是个样子而没换文本）：before=$before after=$expanded",
            expanded.heightDp > before.heightDp
        )
        assertTrue(
            "点「$expandWords」的落点必须是折叠动作自己，不许顺手把这条消息选进编辑位：" + edited,
            edited.isEmpty()
        )
        // 紧凑那一档：出口只有一颗，而且它不是一整行（它仍在这颗气泡里面）
        val collapseNodes = rule.onAllNodes(hasText(collapseWords), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("展开之后只许留下一颗「$collapseWords」出口：" + collapseNodes.size, 1, collapseNodes.size)
        val exit = probe.of(collapseNodes.first())
        val bubble = nodeOf(MESSAGE_BUBBLE_TEST_TAG, 0)
        assertTrue(
            "「$collapseWords」那颗出口必须还在气泡内边距以内（不是长成一整行）：" +
                exit.describe() + " / 气泡 " + bubble.describe(),
            exit.leftDp >= bubble.leftDp && exit.leftDp + exit.widthDp <= bubble.leftDp + bubble.widthDp &&
                exit.heightDp < bubble.heightDp
        )
        rule.onAllNodes(hasText(collapseWords), useUnmergedTree = true)[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        val collapsed = nodeOf(MESSAGE_BUBBLE_TEST_TAG, 0)
        assertTrue(
            "「$collapseWords」必须真的收回去（两档来回是同一条能力）：collapsed=$collapsed before=$before",
            kotlin.math.abs(collapsed.heightDp - before.heightDp) <= 1f
        )
    }

    /** 没有备注时**不许**留下一个空壳（原话骂的"没有备注也占一大块"就是这个形状） */
    @Test
    fun `an empty note leaves no line and no dead entry`() {
        mount(listOf(shortHer, shortMe), noteText = "   ")
        assertTrue(
            "空备注不该画出任何一行：" +
                rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG)).fetchSemanticsNodes(),
            rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG)).fetchSemanticsNodes().isEmpty()
        )
        val targets = probe.actionableTargets(rule, "消息列表（空备注）")
        assertEquals("只剩两行聊天的落点：" + targets.joinToString { it.describe() }, 2, targets.size)
        assertTrue(
            "空备注也不许留一颗点了没反应的入口",
            rule.onAllNodes(hasTestTag(ADVISOR_NOTE_TEST_TAG) and hasClickAction()).fetchSemanticsNodes()
                .isEmpty()
        )
    }
}
