package com.lovebrain.app.ui.panel.stats

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import kotlin.math.abs
import kotlin.math.roundToInt
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
 * 顶部那一行统计条的**几何证人**。
 *
 * 这批判据一律读几何、不读"字符串等不等"。本仓库在这儿栽过：文本挂上
 * `maxLines = 1 + overflow = Ellipsis` 之后，语义树**照样把完整原串报回来**，
 * 于是"串没变"看着就像"没被裁"，判据一点牙都没有（见 `UsageExtremeValuesSemanticsTest`
 * 与 `EmptyStateOwnershipSemanticsTest` 里为同一件事被逼出来的自然宽控制组）。
 * 所以每一栏都同时挂一份**控制组**：同一颗组件、同一格字号、套在 `requiredWidth(2400dp)` 里，
 * 它必然只排一行——用它量出"一行该多高""每一格自然该多宽"，再拿这把尺量被测那一颗。
 * 这把尺**每个格子档位都要重量一次**：字号倍率是套在整棵子树上的，控制组与被测那一颗
 * 永远同档；拿第一格量到的尺去判最后一格，红的是量具不是组件。
 *
 * 形状口径（与生产那一颗同步，不是"退化成读个串"）：**一格 = 完整的「标签＋值」一段 Text**，
 * 中间那个空格是原子里唯一的缝。旧 Inline 档把一格拆成标签与数值两颗能各自换行的 Text，
 * 用户说的"第二行"就是从那条缝里长出来的；所以凡"按标签取一次、按数值取一次"的读法都不成立，
 * 现在逐格读的是那一整串（`「今日 ¥12345.678」`），标签与值两头都必须逐字在里面。
 *
 * 另一条几何事实决定了"摆在行里"怎么认：整组停在行外时，父级那层 `clipToBounds()` 会把
 * 那一组里每一格的盒子压成 `0x0dp @(0,0)`（贴边那格被压成"看得见的那半截"）。
 * 于是"哪一组正摆在行里"按**有没有画得出来的格子**认，不按盒子左右缘认——组件自己那层
 * `graphicsLayer` 平移只回灌到子节点，页盒子自己的左右缘量的还是自然摆位。
 * 这条读数反过来就是牙：半截压边 = 那一格宽度**小于**它自己的自然宽；半个分组 = 两组都还有
 * 画得出来的格子；整组没停在外面 = 行外那一组也冒出格子。
 *
 * 四件事，每件事都有能对撞的坏实现：
 * 1. **始终只占一行**：条高与"垂直方向上出现了几档文本带"都必须是 1 行；
 *    两行的实现（外面套 Column、或把格子挤到换行）当场红；叠在 4dp 刘海锚点上时同样，
 *    而且那一档里画出来的每一格高度不许被那个 4dp 框压薄；
 * 2. **没有半个数字**：静止态恰好一整组画得出来、其余整组一颗都不露，
 *    且每一格的宽与高 = 控制组里同一串的自然读数（被 `weight` 挤瘦、被省略号截掉的那种红）；
 * 3. **一行放得下就不轮播也不渐隐**（等三个间隔也不换组）；放不下才必须给翻页提示；
 * 4. **轮播与手动滑动互不抢**：到间隔换一整组；按下不动（零位移零甩速）不跳组；一记甩动恰好
 *    换一组；松手之后定时器必须重新计满一整段（差半段就自己跳 = 定时器没被按住这件事停掉，
 *    满一整段还不回来 = 手动那一甩把定时器永久停掉）。"位移很短"单独不构成"轻"——
 *    生产那条判据是"位移过阈值**或**甩速过线"，那两档的数值由纯函数那一格钉着，
 *    细节写在那一格的 KDoc 里；
 *
 * 「未知费用不许念成 0」那一栏判的是**这一颗小条把上游给的整串原样摆出来**：
 * 组件不认识 Double，所以它也拿不到"把不知道写成 0"的那条路。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UsageStatBarSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** 这一格是 mdpi ⇒ px 与 dp 同一把尺 */
    private val density: Float get() = ctx.resources.displayMetrics.density

    private fun Float.toDp(): Float = this / density

    /**
     * 时间不自己走：这一批判据要能说出"过了整整一个间隔"与"只走了几帧"的差别，
     * 自动走时时钟会被 waitForIdle 一路快进，轮播到底有没有暂停就量不出来了。
     */
    @Before
    fun freezeTheClock() {
        rule.mainClock.autoAdvance = false
    }

    /** 走 [frames] 帧：让 onSizeChanged 量到的自然宽回灌进换组方案并收敛（一帧 16ms，远不到 4s） */
    private fun settle(frames: Int = 8) {
        repeat(frames) { rule.mainClock.advanceTimeBy(16L) }
        rule.waitForIdle()
    }

    // ═══════════ 数据：与页面会传进来的形状一致（数值串由上游格式化，组件不参与） ═══════════

    private val wideFields = listOf(
        LbMetric("今日", "¥12345.678"),
        LbMetric("本次", "¥9876.543"),
        LbMetric("首字", "123.4s"),
        LbMetric("累计", "123456次"),
        LbMetric("已统计", "¥8765.432")
    )

    private val shortFields = listOf(
        LbMetric("今日", "¥0.12"),
        LbMetric("本次", "—"),
        LbMetric("首字", "1.2s"),
        LbMetric("累计", "3次"),
        LbMetric("已统计", "—")
    )

    /** 「不知道」与「不足一分」两档，逐字与 strings.xml 里那两条资源一致 */
    private val unknownFields = listOf(
        LbMetric("今日", "—"),
        LbMetric("本次", "—"),
        LbMetric("累计", "0次"),
        LbMetric("已统计", "不足 ¥0.01")
    )

    private data class TextShot(
        val text: String,
        val leftDp: Float,
        val rightDp: Float,
        val topDp: Float,
        val widthDp: Float,
        val heightDp: Float
    ) {
        /**
         * 这一格此刻**画得出来**吗。
         *
         * 整组停在行外时父级那层裁切把每一格的盒子压成 `0x0dp @(0,0)`，贴边那一格被压成
         * 看得见的那半截（宽小于自己的自然宽）。两种都不是这一格该有的尺寸。
         */
        val painted: Boolean get() = widthDp > 0f && heightDp > 0f

        fun describe() = "「$text」x[${leftDp.toInt()}..${rightDp.toInt()}] " +
            "${widthDp.toInt()}x${heightDp.toInt()}dp top=${topDp.toInt()}"
    }

    private data class PageShot(
        val index: Int,
        val leftDp: Float,
        val rightDp: Float,
        val heightDp: Float,
        val texts: List<TextShot>
    ) {
        fun describe() = "page$index [${leftDp.toInt()}..${rightDp.toInt()}] 高${heightDp.toInt()}dp " +
            texts.joinToString(" ") { it.describe() }
    }

    // ═══════════ 挂载：被测那一颗 + 一份自然宽控制组（同一棵树 ⇒ 同一把字号尺） ═══════════

    private fun mountBar(
        cell: MutableState<UiMatrix>,
        fields: List<LbMetric>,
        anchored: Boolean = false
    ) {
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                if (anchored) {
                    // 面板顶部那条 4dp 的刘海：统计条叠在上面，不另起一行
                    Box(modifier = Modifier.fillMaxWidth().height(Spacing.sm)) {
                        UsageStatBar(
                            fields = fields,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(unbounded = true)
                                .align(Alignment.Center)
                        )
                    }
                } else {
                    UsageStatBar(fields = fields, modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.testTag(CONTROL_TAG).requiredWidth(NATURAL_WIDTH)) {
                    UsageStatBar(fields = fields, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        settle()
    }

    // ═══════════ 读数 ═══════════

    private fun nodesFor(tag: String): List<SemanticsNode> =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes()

    private fun controlSubtree(): Set<Int> {
        val control = nodesFor(CONTROL_TAG)
        check(control.size == 1) { "控制组容器数到 ${control.size} 颗，要恰好 1 颗" }
        return collectIds(control.single())
    }

    private fun collectIds(node: SemanticsNode): Set<Int> {
        val mine = mutableSetOf(node.id)
        node.children.forEach { mine.addAll(collectIds(it)) }
        return mine
    }

    private fun tagOf(node: SemanticsNode): String? = node.config.getOrNull(SemanticsProperties.TestTag)

    private fun textsUnder(node: SemanticsNode): List<TextShot> {
        val found = mutableListOf<TextShot>()
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { raw ->
            val b = node.boundsInRoot
            found += TextShot(
                text = raw.toString(),
                leftDp = b.left.toDp(),
                rightDp = b.right.toDp(),
                topDp = b.top.toDp(),
                widthDp = b.width.toDp(),
                heightDp = b.height.toDp()
            )
        }
        node.children.forEach { found.addAll(textsUnder(it)) }
        return found
    }

    private fun pageNodesUnder(node: SemanticsNode): List<SemanticsNode> {
        val tag = tagOf(node)
        val mine = if (tag != null && tag.startsWith(UsageStatTags.PAGE_PREFIX)) listOf(node) else emptyList()
        return mine + node.children.flatMap { pageNodesUnder(it) }
    }

    private fun nodesUnderTagged(node: SemanticsNode, tag: String): List<SemanticsNode> {
        val mine = if (tagOf(node) == tag) listOf(node) else emptyList()
        return mine + node.children.flatMap { nodesUnderTagged(it, tag) }
    }

    private fun toPageShot(node: SemanticsNode): PageShot {
        val b = node.boundsInRoot
        return PageShot(
            index = tagOf(node)?.removePrefix(UsageStatTags.PAGE_PREFIX)?.toIntOrNull() ?: -1,
            leftDp = b.left.toDp(),
            rightDp = b.right.toDp(),
            heightDp = b.height.toDp(),
            texts = textsUnder(node)
        )
    }

    /** 被测那一颗画出来的页（控制组那一遍整棵子树剔掉，否则"恰好一组在里面"会被自己数坏） */
    private fun probedPages(): List<PageShot> {
        val control = controlSubtree()
        val strips = nodesFor(UsageStatTags.STRIP).filter { it.id !in control }
        check(strips.size == 1) { "被测这一颗数到 ${strips.size} 条 strip，要恰好 1 条" }
        val pages = pageNodesUnder(strips.single()).map(::toPageShot).sortedBy { it.index }
        check(pages.isNotEmpty()) { "被测这一颗没画出任何一组，这把尺是瞎的" }
        return pages
    }

    private fun controlPages(): List<PageShot> {
        val control = nodesFor(CONTROL_TAG).single()
        val strips = nodesUnderTagged(control, UsageStatTags.STRIP)
        check(strips.size == 1) { "控制组里数到 ${strips.size} 条 strip" }
        return pageNodesUnder(strips.single()).map(::toPageShot)
    }

    /** 被测那一颗的行宽（=视口）；条高不读，因为它套的是 4dp 刘海那类外层约束 */
    private fun barWidthDp(): Float {
        val control = controlSubtree()
        val bars = nodesFor(UsageStatTags.BAR).filter { it.id !in control }
        check(bars.size == 1) { "被测统计条数到 ${bars.size} 颗" }
        return bars.single().boundsInRoot.width.toDp()
    }

    /**
     * 控制组量到的"一行该多高"：同一颗组件、同一格字号，2400dp 下必然只排一行。
     *
     * ⚠ 必须在**当前这一格档位**下现量：字号倍率套在整棵子树上，控制组跟着被测那一颗一起变，
     * 拿挂载那一刻（标准字）的读数去判后面最大字那一格，量的就是两把尺。
     * 被裁成 0x0 的读数不参与（取最大也只有画得出来的那一格才有最大可言）。
     */
    private fun oneLineHeightDp(): Float {
        val texts = controlPages().flatMap { it.texts }.filter { it.painted }
        check(texts.isNotEmpty()) { "控制组没读到任何画得出来的文本，这把尺是空的" }
        return texts.maxOf { it.heightDp }
    }

    /**
     * 控制组里"每一格（一整段「标签＋值」）自然该有多宽、多高"的参照表，键是那一段合并串。
     *
     * 键合不上就是"被测那一颗念的串与控制组那一颗不是同一串"——`error` 说得出是哪一格，
     * 因为合并串少一段或多一段（把一格拆成两颗 Text）正是这一族最要的坏实现。
     */
    private fun naturalWidths(): Map<String, TextShot> {
        val natural = HashMap<String, TextShot>()
        controlPages().flatMap { it.texts }.filter { it.painted }.forEach { natural[it.text] = it }
        check(natural.isNotEmpty()) { "控制组没量到任何一格，这把尺是空的" }
        return natural
    }

    /** 被测那一颗**画得出来**的那些组（整组停在行外的报 0x0，认不出来） */
    private fun paintedPages(): List<PageShot> = probedPages().filter { page -> page.texts.any { it.painted } }

    /** 静止态摆在行里的那一整组 */
    private fun paintedPage(): PageShot {
        val painted = paintedPages()
        assertEquals(
            "静止态摆在行里的应当恰好一整组，实到 ${painted.size} 组：" +
                probedPages().joinToString(" | ") { it.describe() },
            1,
            painted.size
        )
        return painted.single()
    }

    /** 被测那一颗里画得出来的每一格 */
    private fun paintedCells(): List<TextShot> = paintedPages().flatMap { page -> page.texts.filter { it.painted } }

    /** 被测那一颗里画得出来的那一组的页号 */
    private fun visiblePage(): Int = paintedPage().index

    private fun probedTexts(): List<TextShot> = pageNodesUnder(probedStrip()).flatMap { textsUnder(it) }

    private fun probedStrip(): SemanticsNode {
        val control = controlSubtree()
        return nodesFor(UsageStatTags.STRIP).single { it.id !in control }
    }

    /** 手势只能打在**被测**那一条上：树里同时挂着控制组，取第一颗（被测先挂） */
    private fun probedStripNode() = rule.onAllNodesWithTag(UsageStatTags.STRIP, useUnmergedTree = true)[0]

    // ═══════════ 1. 始终只占一行 ═══════════

    @Test
    fun `the bar never grows into a second line even with absurd values`() {
        val cell = mutableStateOf(UiMatrix.FULL.first())
        mountBar(cell, wideFields)

        UiMatrix.FULL.forEach { matrix ->
            rule.runOnIdle { cell.value = matrix }
            settle()
            // 尺重量一次：这一格的字号决定控制组与被测那一颗的同一档行高
            val line = oneLineHeightDp()
            assertTrue("${matrix.id} 的控制组连一行都量不出来，这把尺不可信：$line", line > 0f)
            val pages = probedPages()
            val tallest = pages.maxOf { it.heightDp }
            assertTrue(
                "${matrix.id} 这一格统计条量到 ${tallest.toInt()}dp，而同字号的一行只有 ${line.toInt()}dp —— " +
                    "放不下应该整组换，不是另起一行：" + pages.joinToString(" | ") { it.describe() },
                tallest <= line * 1.4f
            )
            // 至少得有一格画得出来，否则下面的"竖直只出现一档"是在空转
            val shown = paintedCells()
            assertTrue("${matrix.id} 这一格一颗统计都没画出来：" + pages.joinToString(" | ") { it.describe() },
                shown.isNotEmpty())
            // 行数用几何量：画得出来的那些格在竖直方向上只许出现一档
            val spread = shown.map { it.topDp }
            if (spread.size >= 2) {
                val rows = ((spread.max() - spread.min()) / line).roundToInt() + 1
                assertEquals(
                    "${matrix.id} 画出来的那些格在竖直方向摆了 $rows 档：" +
                        shown.joinToString(" ") { it.describe() },
                    1,
                    rows
                )
            }
        }
    }

    @Test
    fun `hanging the bar on the 4dp notch still measures one line`() {
        val cell = mutableStateOf(UiMatrix(320, fontScale = 2.0f, note = "最窄 + 最大字"))
        mountBar(cell, wideFields, anchored = true)
        val line = oneLineHeightDp()
        val pages = probedPages()
        assertTrue(
            "叠在 4dp 刘海上就被挤成两行了：" + pages.joinToString(" | ") { it.describe() },
            pages.maxOf { it.heightDp } <= line * 1.4f
        )
        // 那一档 4dp 是**拖动锚点的热区**，不是文字的高度：叠上去之后画出来的每一格
        // 仍要是整行高，被那个框夹掉的实现会量出远小于一行的数（或直接被裁成 0x0）。
        val shown = paintedCells()
        assertTrue(
            "叠在 4dp 刘海上就没画出任何一格（文字被那个框裁掉了）：" +
                pages.joinToString(" | ") { it.describe() },
            shown.isNotEmpty()
        )
        val squeezed = shown.filter { it.heightDp < line - 1f }
        assertTrue(
            "这些格被外层那一档压薄了（同字号一行该 ${line.toInt()}dp）：\n" +
                squeezed.joinToString("\n") { it.describe() },
            squeezed.isEmpty()
        )
    }

    // ═══════════ 2. 没有半个数字 ═══════════

    @Test
    fun `at rest exactly one whole group sits inside the line and nothing straddles its edge`() {
        val cell = mutableStateOf(UiMatrix(320))
        mountBar(cell, wideFields)
        val viewport = barWidthDp()
        val pages = probedPages()
        assertTrue(
            "这一行本来放不下全部字段，该切出好几组：" + pages.joinToString(" | ") { it.describe() },
            pages.size > 1
        )

        val shown = paintedPage()
        // 一整组都在行里：这一组里没有任何一格被落在行外（半个分组 = 用户只看到半条信息）
        val leftBehind = shown.texts.filter { !it.painted }
        assertTrue(
            "摆在行里的那一组落下了 ${leftBehind.size} 格：" + shown.describe(),
            leftBehind.isEmpty()
        )
        // 每一格（一整段「标签＋值」）都整段在行内，不许压这一行的边
        val straddling = shown.texts.filter { it.leftDp < -0.5f || it.rightDp > viewport + 0.5f }
        assertTrue(
            "这些格压在行的边上，用户看到的就是半个数字：" +
                straddling.joinToString(" ") { it.describe() },
            straddling.isEmpty()
        )
        // 停在行外的那些组：一格都不许露进来（露半格就是"两组混在一行里"）
        val leaked = pages.filter { it.index != shown.index }.flatMap { page -> page.texts.filter { it.painted } }
        assertTrue(
            "行外那一组漏出 ${leaked.size} 格：" + leaked.joinToString(" ") { it.describe() },
            leaked.isEmpty()
        )
    }

    @Test
    fun `every cell keeps its natural width instead of being squeezed or cut short`() {
        val cell = mutableStateOf(UiMatrix(320))
        mountBar(cell, wideFields)
        val natural = naturalWidths()

        val shown = paintedCells()
        assertTrue("这一格一颗统计都没画出来，判据在空转", shown.isNotEmpty())
        val offShape = shown.filter { shot ->
            val ref = natural[shot.text] ?: error("控制组里没有「${shot.text}」这一格，参照是空的")
            shot.widthDp < ref.widthDp - 1f || shot.heightDp < ref.heightDp - 1f
        }
        assertTrue(
            "这些格子比自己那一串的自然读数还窄，用户看到的就是被挤瘦／截掉的半个数字：\n" +
                offShape.joinToString("\n") { shot ->
                    "${shot.describe()}（自然读数 ${natural.getValue(shot.text).describe()}）"
                },
            offShape.isEmpty()
        )
        // 每一格都得是"标签＋值"**一整段**：标签与值两头都在同一段里，中间那条缝不许能各自换行
        val halfCell = shown.filter { shot -> wideFields.none { shot.text == "${it.label} ${it.value}" } }
        assertTrue(
            "这些格不是「标签＋值」一整段（一格被拆回两颗 Text，或数值没在这一段里）：\n" +
                halfCell.joinToString("\n") { it.describe() },
            halfCell.isEmpty()
        )
    }

    // ═══════════ 3. 一行放得下：不轮播、不渐隐；放不下：必须给提示 ═══════════

    @Test
    fun `a line wide enough for the fields shows them all and shows no fade`() {
        val cell = mutableStateOf(UiMatrix(600))
        mountBar(cell, shortFields)
        val viewport = barWidthDp()
        val pages = probedPages()
        assertTrue(
            "放得下却被推到行外：" + pages.joinToString(" | ") { it.describe() },
            pages.all { it.leftDp >= -0.5f && it.rightDp <= viewport + 0.5f }
        )
        assertEquals(
            "一行放得下时不该画横向渐隐",
            0,
            nodesFor(UsageStatTags.FADE_LEADING).count { it.id !in controlSubtree() } +
                nodesFor(UsageStatTags.FADE_TRAILING).count { it.id !in controlSubtree() }
        )

        // 等满三个间隔也不许自己换组：所有字段一直同时看得见
        val before = probedTexts().map { it.text }.sorted()
        repeat(3) { rule.mainClock.advanceTimeBy(USAGE_STAT_GROUP_INTERVAL_MS) }
        settle(2)
        assertEquals("一行放得下却换了组", before, probedTexts().map { it.text }.sorted())
    }

    @Test
    fun `a short group that fits sits centered on the bar instead of hugging the left edge`() {
        // §7.2：测量完成后放得下的短组，整条相对实际可用视口居中——不是"只把数值字符 TextAlign.Center
        // 而整组 Row 仍靠左"，也不是左贴边。这一格读几何、量两侧留白，与被裁/被挤的读数无关。
        val cell = mutableStateOf(UiMatrix(600))
        mountBar(cell, shortFields)
        val viewport = barWidthDp()
        val painted = paintedCells()
        assertTrue("这一格一颗统计都没画出来，判据在空转：viewport=${viewport.toInt()}dp", painted.isNotEmpty())

        val leftMargin = painted.minOf { it.leftDp }
        val rightMargin = viewport - painted.maxOf { it.rightDp }
        // 反例（这栏的牙）：把 usageStatPageTranslationsPx 的 `!rotates && !oversizedPage` 分支退回
        // `return List { 0f }` ⇒ 当前页贴在左缘，leftMargin≈0、rightMargin=整条剩余留白 ⇒
        // 两者相差远大于 1.5dp ⇒ 本栏红；"仍然左贴"由此被区分开。
        // 只给数字 TextAlign.Center 而 Row 靠左的假居中，同样会让 leftMargin 明显小于 rightMargin ⇒ 红。
        assertTrue(
            "放得下的短组没居中（左留白 ${leftMargin.toInt()}dp、右留白 ${rightMargin.toInt()}dp）：" +
                painted.joinToString(" ") { it.describe() },
            abs(leftMargin - rightMargin) <= 1.5f
        )
        // 居中后整组不应还压在 0 那一线（真左贴会留白≈0）
        assertTrue("整组仍靠左贴边（左留白=${leftMargin.toInt()}dp）", leftMargin > 1f)
        // 也不许跑出这一行（半截数字的老坑）
        assertTrue(
            "居中把某一格推出了行的右缘：" + painted.joinToString(" ") { it.describe() },
            painted.maxOf { it.rightDp } <= viewport + 0.5f && painted.minOf { it.leftDp } >= -0.5f
        )
    }

    @Test
    fun `a rotating line does draw the fade hint`() {
        val cell = mutableStateOf(UiMatrix(320, fontScale = 2.0f))
        mountBar(cell, wideFields)
        val control = controlSubtree()
        val fades = nodesFor(UsageStatTags.FADE_LEADING).filter { it.id !in control } +
            nodesFor(UsageStatTags.FADE_TRAILING).filter { it.id !in control }
        assertTrue(
            "内容放不下这一行却没给任何横向提示：" + probedPages().joinToString(" | ") { it.describe() },
            fades.isNotEmpty()
        )
    }

    // ═══════════ 4. 轮播与手动滑动互不抢 ═══════════

    @Test
    fun `groups rotate on the readable interval when the line cannot hold them`() {
        val cell = mutableStateOf(UiMatrix(320))
        mountBar(cell, wideFields)
        val pageCount = probedPages().size
        val first = visiblePage()

        rule.mainClock.advanceTimeBy(USAGE_STAT_GROUP_INTERVAL_MS)
        settle(2)

        val next = visiblePage()
        assertTrue(
            "过了一个间隔（${USAGE_STAT_GROUP_INTERVAL_MS}ms）还在第 $first 组，没换组：$next",
            next != first
        )
        // 换的是一整组：换完仍在行里的只有下一组
        assertEquals((first + 1) % pageCount, next)
    }

    /**
     * 轮播与手不动的互不抢——这一格管的是**两处写同一个 page 的代码不许抢对方的那一组**。
     *
     * ⚠ 先记一条把这一格从"产品缺陷"里捞出来的分诊（旧写法红在最后一句"轻一下就把组换掉了"）：
     * 生产那条翻页判据是「位移过阈值 **或** 甩速过线」（`usageStatPageAfterDrag`：阈值取
     * `max(视口×0.34, 24px)`，甩速线 900px/s，两条的数值都另有纯函数那一格钉着，见
     * `UsageStatBarPlanTest`）。旧写法这里注入的是 `swipeLeft(startX=70, endX=54)`——位移只有
     * 16dp，但**那是一记甩动**：注入的 down/move/up 之间几乎没有时间，松手速度早过那条线。
     * 于是"换组"是合同要的行为，不是实现把轻碰当成了翻页；把这一格读成产品缺陷就会去改生产，
     * 而改坏的是那条"用户一甩就该看下一组"。
     * 这台仪器注入不出"位移短**且**慢"的那一种拖（能注入时长的接口在这一族里没被用过，
     * 为这一格新学一把不值当），所以"轻"这一半留在纯函数那一格，这一格改判**它自己量得到的**：
     * ① 按下不动（一记点击，零位移零甩速）不许换组；
     * ② 一记横向甩动恰好换**一整组**（换两组 = 定时器也从手里抢了一格）；
     * ③ 松手之后自动轮播必须**重新计满一整段**：还差 600ms 到间隔时不许自己再跳一组
     *   （把 `dragging` 从定时器那一条的 key 里摘掉，旧的排期就会在这半段里落下来 → 红）；
     * ④ 满一整段之后确实又轮播（定时器没被这一甩永久停掉）。
     * 四句都是数值判据，没有一句是"读得到就绿"；③④ 的正向对照（什么都不碰时到间隔一定换组）
     * 写在隔壁 `groups rotate on the readable interval when the line cannot hold them`。
     */
    @Test
    fun `a light touch keeps the group and the timer does not jump while the user is swiping`() {
        val cell = mutableStateOf(UiMatrix(320))
        mountBar(cell, wideFields)
        val pageCount = probedPages().size
        assertTrue(
            "这一格要有'别的组'可换才判得动，实到 $pageCount 组：" +
                probedPages().joinToString(" | ") { it.describe() },
            pageCount > 1
        )
        val start = visiblePage()

        // ① 按下不动：零位移、零甩速的那一种"轻一下"（打在这一行左半段，
        //    就是旧写法那两只手会落的地方；y 取行自己的一半高，不猜别处的数）
        val stripShot = probedStripNode().fetchSemanticsNode()
        val tapAt = Offset(60f * density, stripShot.boundsInRoot.height / 2f)
        probedStripNode().performTouchInput { click(tapAt) }
        settle(2)
        assertEquals("点一下统计条就把组换掉了（它不是翻页入口）：", start, visiblePage())

        // 把排期推到"还差 1500ms 才到间隔"，再从这一格开始甩（留的是注入本身可能吃掉的时间，
        // 别让它把间隔顶满）：定时器若不在松手时重新起表，旧排期那一记就会落在下一步那半段里
        rule.mainClock.advanceTimeBy(USAGE_STAT_GROUP_INTERVAL_MS - 1500L)
        probedStripNode().performTouchInput { swipeLeft() }
        // 换组要过那 150ms 的淡入淡出，两组的格子才会分先后（settle 的帧数按它算）
        settle(12)
        val manual = (start + 1) % pageCount
        assertEquals(
            "一记横向甩动应当恰好换一整组（$start → 应为 $manual；多跳的一格就是定时器抢的）：",
            manual, visiblePage()
        )

        // ③ 松手之后重新计满：半个间隔还差 600ms，这一段里它不该动
        rule.mainClock.advanceTimeBy(USAGE_STAT_GROUP_INTERVAL_MS - 600L)
        settle(2)
        assertEquals(
            "松手后没重新计满一整段：半个间隔（${USAGE_STAT_GROUP_INTERVAL_MS - 600L}ms）里又跳了一组",
            manual, visiblePage()
        )

        // ④ 再满一整段，轮播确实回来了（不是把手动那一甩当成了"永久停住"）
        rule.mainClock.advanceTimeBy(USAGE_STAT_GROUP_INTERVAL_MS)
        settle(12)
        assertEquals(
            "过了完整间隔还不轮播（这一格的另一头：手动看过别的组之后定时器得继续走）",
            (manual + 1) % pageCount, visiblePage()
        )
    }

    // ═══════════ 5. 未知费用不许被伪造成 0 ═══════════

    @Test
    fun `unknown and below-cent costs are shown exactly as the upstream readout says`() {
        val cell = mutableStateOf(UiMatrix(360))
        mountBar(cell, unknownFields)
        // 一格 = 一整段「标签＋值」：读合并串，不再"按标签取一次、按数值取一次"
        val shown = probedTexts().map { it.text }
        assertEquals(
            "上游给的那几档（「—」与「不足 ¥0.01」）必须逐字排在自己那一格里，顺序也不许变：",
            unknownFields.map { "${it.label} ${it.value}" },
            shown
        )

        // 把数值那一半单独剥出来判"有没有被伪造成 0 元"——这一段判的是**值**，不是存在性
        val values = shown.map { it.substringAfter(' ') }
        val fakeZero = Regex("""[¥￥]\s*0(\.0+)?$""")
        assertTrue("未知费用被念成了 0 元（数值那半段逐格看：$values）", values.none { fakeZero.containsMatchIn(it) })
        // 「0次」是计数、不是钱：它不许被替成费用读数
        assertEquals(
            "「累计」那一格的数值被换成了钱：", "0次",
            shown.first { it.startsWith("累计 ") }.substringAfter(' ')
        )
        assertTrue(
            "不知道的那两格被填成了有数（每一格的费用档都要原样）：$values",
            values.count { it == "—" } == 2 && values.any { it == "不足 ¥0.01" }
        )
    }

    @Test
    fun `the bar renders every field the caller passes, verbatim`() {
        val cell = mutableStateOf(UiMatrix(600))
        mountBar(cell, wideFields)
        val shown = probedTexts().map { it.text }
        // 一整段「标签＋值」就是一格：逐格逐字，顺序就是调用方给的顺序（组件不参与格式化）
        assertEquals(
            "每一格都该是「标签＋值」一段，且逐字是上游给的那一串：",
            wideFields.map { "${it.label} ${it.value}" },
            shown
        )
        // 这一行放得下：五格必须全都画得出来，一组都不许停在行外
        val painted = probedTexts().filter { it.painted }.map { it.text }
        assertEquals("一行放得下却有格没摆出来：", wideFields.map { "${it.label} ${it.value}" }, painted)
        // 数值那半段逐字回得上游给的串（被组件重编过就算丢口径）
        wideFields.forEach { f ->
            val value = shown.first { it.startsWith("${f.label} ") }.substringAfter(' ')
            assertEquals("「${f.label}」那格的数值被改写过：", f.value, value)
        }
    }

    companion object {
        /** 自然宽参照容器的锚点，只在本文件里用 */
        private const val CONTROL_TAG = "usage_stat_bar_control"

        /** 宽到一定放不下任何一组 ⇒ 控制组必然只排一行 */
        private val NATURAL_WIDTH = 2400.dp
    }
}
