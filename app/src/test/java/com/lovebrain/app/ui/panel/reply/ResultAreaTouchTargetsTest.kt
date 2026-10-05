package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.RewriteCommand
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
 * 第6节第1条 :490 与 第6节第5条 :531/:532 一起欠的那笔：**`ResultArea` 从来没在 JVM 语义树里挂起来过**。
 *
 * 因为挂不起来，账本 第38节第7条 里那 7 处自造按钮有 **两处就在这屏**（`:340`、`:1230`）
 * 却一处没判——当时的原话是"不搬不是因为它们没问题，是因为我证不了"。
 *
 * ⚠ 顺手纠正一条我抄进记忆与文档的**假事实**：我一直记着"这屏要 VM 所以挂不起来"。
 * 实际 `ResultArea` 的入参全是数据 + 回调（没有一个 ViewModel 参数），
 * 挂不起来的真原因是**我没去造夹具**——而 `Scheme` / `ReplySchemes` / `LoveBrainResponse`
 * 每个字段都有默认值，造一副只要十行。
 * "要 VM"那条是读调用点想出来的，不是量出来的（同族：写错的行数、过期的行号、
 * `TYPE_ACCESSIBILITY_OVERLAY`）。
 *
 * 这一格只买一样东西：**把这屏量到手**，并且明确量到的范围——
 * 方案卡是一条 `LazyRow`，**离开视口的 item 根本不组合**，所以"整屏扫一遍"
 * 会静默地只看到露出来的那一两张（坑表 ⑮/⑯ 那一族：量到 0 或量到一半都像"没有缺陷"）。
 * 因此第二格沿着那条横排**逐张滚过去**，每一档都按**自己那一档**过下限：
 * 卡内右下角那一排三颗走本轮明写的紧凑档（并由 `assertSchemeActionRowsFitCardContentWidth`
 * 说出"为什么这一排允许低于全站下限"——三颗紧凑档放得进卡内容宽、三颗下限放不下），
 * 其余每一颗仍是全站下限，认不出档的新控件也按全站下限判。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultAreaTouchTargetsTest {

    private companion object {
        /** 生产里已有的锚点（方案卡那条横排），设备侧那格用的是同一个串 */
        const val SCHEME_ROW_TAG = "scheme_cards_row"

        /**
         * 结果区下方那条文字入口的名字。它现在是内联文案（还没进 `res/values`），
         * 所以这里只能写死同一个串；哪天它换成资源，这一格跟着改成 `ctx.getString(...)`——
         * 别把它悄悄放宽成"有一个按钮就行"，那判的就不是"这一屏说得出这句话"了。
         */
        const val CORRECTION_ENTRY_LABEL = "纠正记忆"

        /**
         * 判断"这两颗是排在同一排里紧挨着的"所用的容差（dp）。
         *
         * 卡内那一排三颗之间没有间距（贴边排），而卡片与卡片之间隔着卡距 + 两份卡内边距，
         * 差着一个数量级，所以 1dp 的容差就够分开"同一排的下一颗"与"下一张卡的第一颗"。
         */
        const val ROW_ADJACENCY_TOLERANCE_DP = 1f

        /** 样本至少要有的颗数：筛到只剩两三颗时这一格就退化成空转，这里当场说出来 */
        const val MIN_SAMPLE_SIZE = 6
    }

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /**
     * 卡片右下角那三颗的读屏名字**走资源**（图标档那一颗不给内联字面量留位置），
     * 所以这里的锚点也得从资源取——这台机器的环境解析出来是英文，
     * 写死中文那四个字只会命中 0 颗（同 `ProviderFormSemanticsTest` 里 `saveChangesLabel`
     * 那条教训）。判据没变：还是"这屏说得出名字、报得出按钮角色、两轴够下限"。
     */
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val copyLabel: String get() = ctx.getString(R.string.panel_copy)
    private val likeLabel: String get() = ctx.getString(R.string.a11y_scheme_like)
    private val dislikeLabel: String get() = ctx.getString(R.string.a11y_scheme_dislike)

    /**
     * 四风格齐全 + 四条方向里只有两条有内容（两条 null = 本轮不存在那一条）。
     * 合成一条列表之后这一排应当是 **6 张卡**——不是"风格那一档的 4 张"，
     * 也不是"两组各四条、点着切"。
     */
    private fun fixture(): GenerateResult.Success = GenerateResult.Success(
        LoveBrainResponse(
            response = ReplySchemes(
                recommended = "我理解你的意思，不过今天先把话说清楚再决定。",
                badBoy = "你要是还想拖，我就直说了：这事拖不下去。",
                playful = "哟，又来，这次我站你这边五秒钟。",
                warm = "我知道你不容易，我们慢慢来。"
            ),
            directions = listOf("先问清楚她想要什么", null, "把你的底线说一次", null),
            analysis = ReplyAnalysis()
        )
    )

    /** 一条真实的本轮参考：MemoryRefsFeed 会把它念成 `[画像] + 正文` */
    private fun refFixture(text: String) = MemoryRef(
        id = "mem-ref-1",
        kbId = "kb-1",
        kind = MemoryKind.PROFILE,
        text = text,
        sourcePath = "understand/me.md"
    )

    private fun mount(
        result: GenerateResult,
        matrix: UiMatrix,
        feedbacks: Map<String, SchemeFeedback> = emptyMap(),
        memoryRefs: List<MemoryRef> = emptyList(),
        memoryRefsExpanded: Boolean = false
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                ResultArea(
                    result = result,
                    isGenerating = false,
                    streamingCoreText = "先把这轮的重点说出来，再决定要不要发。",
                    isGeneratingCore = false,
                    streamingSchemes = emptyList(),
                    feedbacks = feedbacks,
                    onFeedback = { _, _ -> },
                    onCopyScheme = {},
                    onRetry = {},
                    memoryRefs = memoryRefs,
                    memoryRefsExpanded = memoryRefsExpanded,
                    providerReady = true,
                    onOpenSettings = {}
                )
            }
        }
    }

    /**
     * 那条方案卡横排。
     *
     * 锚点用**生产里已经存在**的那个 testTag（`scheme_cards_row`，
     * 设备侧 `androidTest/.../ResultAreaVisualRegressionTest` 就是按它滚卡的），
     * 不是为本轮新加的夹具件——本轮不改生产代码就能量到它。
     * 拿 tag 而不是拿"横向可滚"这种形状：竖排那几条 `verticalScroll` 也带 scroll 动作，
     * 按形状筛会拿错对象，拿错了照样能滚、照样绿，量的却是竖排。
     */
    private fun schemeRow() = rule.onNodeWithTag(SCHEME_ROW_TAG)

    /**
     * 卡内右下角那一排三颗的名字（走资源）。这一族是本轮明写的**卡内**热区例外：
     * 158dp 的卡塞不进 3×48dp 的操作行，所以它们走紧凑档，其余每一颗仍过全站下限。
     */
    private val cardActionLabels: Set<String> get() = setOf(copyLabel, likeLabel, dislikeLabel)

    /** 调整区那四项胶囊：卡片展开时才进这一屏，与卡内三颗同一把尺（胶囊档） */
    private val adjustPillLabels: Set<String> get() = RewriteCommand.UI_OPTION_LABELS.toSet()

    /**
     * 逐颗点名它走哪一档，而不是整屏一把尺。
     *
     * ⚠ 认不出档的节点**一律按全站下限判**：这样"这一屏新长出一颗没登记档位的控件"
     * 不会跟着卡内那一族一起静默松掉。把整屏的尺调到 28 才是本轮明令禁止的那条。
     */
    private fun tierOf(target: SemanticsProbe.Target): Float = when {
        target.label in cardActionLabels -> TouchTier.CARD_ACTION
        target.label in adjustPillLabels -> TouchTier.COMPACT_CHIP
        else -> TouchTier.SITE_FLOOR
    }

    /**
     * 量"手指在这屏点不点得中"，只看**完整落在视口里、且真的被摆出来了**的可交互节点。
     *
     * 样本切两刀，两刀都是**尺寸判据**，不是把这一格改成"只数颗数"：
     *
     * 1. **尺寸 > 0**：横排里滚出视口的那一颗在语义树里报的是 **0x0dp @(0,0)**
     *    （本机这一屏实量到的读数里就有一条「Dislike」0x0dp @(0,0)）。
     *    它既过不了任何一档的尺寸判据（0 < 28），又会把"取最左/最上那颗"的锚点顶到 (0,0)，
     *    所以必须**在样本阶段**按尺寸排除，而不是靠改判据迁就它。
     * 2. **完整落在视口里**：只露半张的卡片，其节点拿到的是**被视口裁过**的尺寸
     *    （本机实量到 8x28dp @(592,114) 那种"看得见的那半截"）。那不是热区不达标，是容器在裁；
     *    拿它判缺陷会把"容器裁切"读成"控件做小了"，然后去修一个没坏的东西。
     *    判据是"左右都严格在视口内"（`strictBothEdges` 为真时）：**贴左边界那颗会被横排裁**——
     *    第一版只卡右边界，结果滚到第 2 张时那张已滚出左边的卡报来 @(0,·) 的半截，又是一次裁切。
     *    贴边的正常控件（页头那颗在 x=0）由**首屏那一格**判，那一格不卡左边界，所以这里收紧没少判任何一颗。
     *
     * ⚠ "筛掉"必须是看得见的动作：两刀排除掉的颗**连同尺寸一起打进失败信息**，
     *   并且要求留下的样本 ≥ [MIN_SAMPLE_SIZE] 颗。
     *   覆盖不靠这一格赌：下面那格沿横排逐张滚过去，每张卡都会在某一档完整露出一次。
     *
     * 尺寸判据本身：逐颗用 [tierOf] 取自己那一档，**数值**比较（两轴），不是"存在就行"。
     */
    private fun assertReachableControlsMeetFloor(
        screen: String,
        context: String,
        viewportWidthDp: Int,
        strictBothEdges: Boolean = false
    ) {
        val all = probe.actionableTargets(rule, screen)
        val (laidOut, neverLaid) = all.partition { it.widthDp > 0f && it.heightDp > 0f }
        val leftOk = if (strictBothEdges) 0.5f else -0.5f
        val (reachable, clipped) = laidOut.partition { t ->
            t.leftDp > leftOk && t.topDp >= 0f && t.leftDp + t.widthDp < viewportWidthDp - 0.5f
        }
        assertTrue(
            "$screen$context 视口内只量到 ${reachable.size} 个可交互节点（整树 ${all.size} 个）——" +
                "样本这么少，断言会退化成空转：先怀疑夹具没把内容渲染出来",
            reachable.size >= MIN_SAMPLE_SIZE
        )
        val offenders = reachable.filter { it.tooSmall(tierOf(it)) }
        assertTrue(
            "$screen$context 有 ${offenders.size}/${reachable.size} 个可交互节点不到**自己那一档**的下限" +
                "（卡内那一排与调整区胶囊 ${TouchTier.CARD_ACTION.toInt()}dp，其余仍是全站 " +
                "${TouchTier.SITE_FLOOR.toInt()}dp）：\n" +
                offenders.joinToString("\n") { "  这一颗该走 ${tierOf(it).toInt()}dp → " + it.describe() } +
                "\n  （另排除 ${neverLaid.size} 个尺寸 0x0、根本没摆出来的：" +
                neverLaid.joinToString { it.describe() } +
                "；${clipped.size} 个被视口裁掉的：" + clipped.joinToString { it.describe() } + "）",
            offenders.isEmpty()
        )
        assertSchemeActionRowsFitCardContentWidth(screen, context, reachable)
    }

    /**
     * 卡内那一排为什么**允许**低于全站下限——这条几何证人就是那句理由；
     * 只把上面那一格从 48 改成 28 而没有这一条，等于无理由放宽。
     *
     * 卡片是合同钉死的 158x150，内容宽 = 卡宽 − 左右各一份 `Spacing.md` = **142dp**
     * （两个数都从生产常量读，不在这里抄字面量）。右下角那一排三颗的合计宽必须放得进它：
     * - 三颗紧凑档：3 × 28 = **84dp** ✓ 放得下；
     * - 三颗全站下限：3 × 48 = **144dp** ✗ 放不下（正文就被挤没了——那正是本轮撤掉的形状）。
     *
     * 所以两侧都有牙：涨回 48 红在"这一排超出内容宽"，缩成更小的数红在上面那一格的档位下限。
     * 语义也不因为换档而变薄：这一排每颗仍要报 `Role.Button`、说得出自己的名字，
     * 赞/踩还要报得出表态状态。
     *
     * 分组的判据是**紧挨着排**（前一颗的右缘到后一颗的左缘 ≤ [ROW_ADJACENCY_TOLERANCE_DP]dp）：
     * 卡片之间隔着卡距与两份卡内边距，不会与同一张卡里那三颗粘成一组。
     * 只有"三颗名字各一颗"的那一组才进合计——贴视口边被裁过的卡只剩 1–2 颗，
     * 它们照样过档位那一判，但不进合计，免得把容器裁切读成"这一排做宽了"。
     * 样本里 0x0 那颗已经在上面按尺寸排除，这里再明写一次同样的判据，
     * 因为 @(0,0) 会假装自己是"最左那一颗"、把分组顺序整个带偏。
     */
    private fun assertSchemeActionRowsFitCardContentWidth(
        screen: String,
        context: String,
        sample: List<SemanticsProbe.Target>
    ) {
        val contentWidthDp = SchemeCardDimens.CARD_WIDTH_DP - 2f * Spacing.md.value
        val icons = sample.filter {
            it.label in cardActionLabels && it.widthDp > 0f && it.heightDp > 0f
        }.sortedBy { it.leftDp }
        val rows = mutableListOf<MutableList<SemanticsProbe.Target>>()
        icons.forEach { icon ->
            val last = rows.lastOrNull()
            if (last != null && icon.leftDp - (last.last().leftDp + last.last().widthDp) <= ROW_ADJACENCY_TOLERANCE_DP) {
                last.add(icon)
            } else {
                rows.add(mutableListOf(icon))
            }
        }
        rows.forEach { row ->
            assertTrue(
                "卡内右下角那一排最多三颗（复制/赞/踩），这一组量到 ${row.size} 颗——" +
                    "多一颗就是往卡内那一排又塞了一颗动作：" + row.joinToString { it.describe() },
                row.size <= 3
            )
        }
        val fullRows = rows.filter { it.size == 3 && it.map { t -> t.label }.toSet() == cardActionLabels }
        assertTrue(
            "$screen$context 量不到一整排卡内操作（三颗名字各一颗、都没被裁）——这条几何证人就成了空转，" +
                "实到分组：" + rows.joinToString(" | ") { g -> g.joinToString { it.describe() } },
            fullRows.isNotEmpty()
        )
        val trioBudgetDp = 3 * TouchTier.CARD_ACTION
        val siteFloorTrioDp = 3 * AppDimens.TOUCH_TARGET_MIN_DP.toFloat()
        fullRows.forEach { trio ->
            val totalWidthDp = trio.fold(0f) { acc, t -> acc + t.widthDp }
            assertTrue(
                "卡内一排三颗合计 ${totalWidthDp.toInt()}dp，放不进卡片 ${contentWidthDp.toInt()}dp 的内容宽。" +
                    "这一条就是" +
                    "「为什么这一排允许低于全站下限」的理由本身（紧凑档 3×${TouchTier.CARD_ACTION.toInt()}=" +
                    "${trioBudgetDp.toInt()}dp 放得下，全站下限 3×${AppDimens.TOUCH_TARGET_MIN_DP}=" +
                    "${siteFloorTrioDp.toInt()}dp 放不下）：\n" +
                    trio.joinToString("\n") { "  " + it.describe() },
                totalWidthDp <= contentWidthDp + 0.5f
            )
            assertTrue(
                "这一排合计只有 ${totalWidthDp.toInt()}dp，比三颗 ${TouchTier.CARD_ACTION.toInt()}dp 紧凑盒" +
                    "（${trioBudgetDp.toInt()}dp）还小 ⇒ 热区被缩成了粒子：" +
                    trio.joinToString { it.describe() },
                totalWidthDp >= trioBudgetDp - 0.5f
            )
            trio.forEach { icon ->
                assertEquals(
                    "换到紧凑档不许把角色弄丢，这一颗必须报 Button：" + icon.describe(),
                    "Button", icon.role
                )
                assertTrue(
                    "换到紧凑档不许把名字弄丢，读屏得说得出这一颗：" + icon.describe(),
                    icon.contentDescriptions.isNotEmpty()
                )
                if (icon.label == likeLabel || icon.label == dislikeLabel) {
                    assertTrue(
                        "赞/踩换了热区档也要报得出表过态没有（selected 不能是 null）：" + icon.describe(),
                        icon.selected != null
                    )
                }
            }
        }
        assertTrue(
            "这一排走紧凑档的理由已经不成立：三颗全站下限（${siteFloorTrioDp.toInt()}dp）" +
                "现在放得进卡内容宽 ${contentWidthDp.toInt()}dp——那这一排就该回到全站下限判，" +
                "而不是继续放过 ${TouchTier.CARD_ACTION.toInt()}dp。请把上面两格的档位一起重钉。",
            siteFloorTrioDp > contentWidthDp + 0.5f
        )
    }

    @Test
    fun `the controls visible on the first screen of the result area all meet the floor`() {
        mount(fixture(), UiMatrix(600))
        assertReachableControlsMeetFloor("ResultArea 首屏", "", 600)
        probe.assertAllActionableLabeled(rule, "ResultArea 首屏")
    }

    @Test
    fun `every scheme card in the row meets the floor`() {
        val result = fixture()
        // 滚几档 = 这一排真有几张卡，**从夹具算出来**，不写死。
        // 判的是合并后的那一条列表（四风格 + 有内容的两条方向 = 6 张）：
        // 还只渲染风格那一档的实现会在这里当场红——`performScrollToIndex(4)`
        // 会报 "out of bounds [0, 4)"，而不是安静地少滚两档。
        val cards = mergedSchemesInRoundOrder((result as GenerateResult.Success).response).size
        assertEquals("夹具这一轮该有的卡数", 6, cards)
        mount(result, UiMatrix(600))
        val row = schemeRow()
        // 逐张滚过去，每一档都重扫整棵树。要求的是**滚完之后累计**至少见过
        // N 种卡内控件——否则 LazyRow 会让我们以为这屏很干净（首屏只露得出三张半）。
        val seen = mutableSetOf<String>()
        for (index in 0 until cards) {
            row.performScrollToIndex(index)
            rule.waitForIdle()
            // 累计键只用「名字 + 角色」，**不能带尺寸**：带尺寸的话同一颗控件在各滚动档
            // 会算成不同节点，这条证人就永远是绿的（恒真证人）。
            probe.actionableTargets(rule, "ResultArea 方案卡").forEach { seen.add("${it.label}|${it.role}") }
            assertReachableControlsMeetFloor("ResultArea 方案卡", "（滚到第 $index 张）", 600, strictBothEdges = true)
        }
        assertTrue(
            "滚过 $cards 档累计到的节点集合是 " + seen.sorted() +
                "——下面这些是该在这屏说得出名字与角色的控件，少一个就是滚动或筛对象没生效，" +
                "这一格就只是在首屏转圈",
            seen.containsAll(
                listOf(
                    "$copyLabel|Button", "$likeLabel|Button", "$dislikeLabel|Button",
                    // 结果区下方那条文字入口：它现在是这一屏唯一的工具类动作
                    "$CORRECTION_ENTRY_LABEL|Button"
                )
            )
        )
        // 反向：已经退场的那两颗分组按钮不许换个名字又出现在这一排上方
        assertTrue(
            "风格/方向那两颗切换按钮已删除，语义树里还能量到就是又长回来了：" + seen.sorted(),
            seen.none { it == "风格|Tab" || it == "方向|Tab" }
        )
    }

    /**
     * 「本轮参考」那份清单：**默认收起**，展开态由调用方给。
     *
     * 判的是两件事，缺一件都不算：
     * 1. 没让展开时，那条记忆的正文**读不到**（默认收起，不占这一屏的纵向高度）；
     * 2. 展开态传进来时，同一条正文**读得到**（证明第 1 条不是"清单整块被删了"造成的假绿）。
     */
    @Test
    fun `the round reference list stays collapsed until the caller opens it`() {
        val refText = "她把猫送到医院那天的原话"
        // 一颗挂在测试这边的展开态：判的就是"清单可不可见"由**外面**说了算，
        // 而不是这一屏自己揣着一个本地布尔（那种实现下面那半条会红）。
        val expanded = mutableStateOf(false)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(600).RenderIn(deviceDensity) {
                ResultArea(
                    result = fixture(),
                    isGenerating = false,
                    streamingCoreText = "",
                    isGeneratingCore = false,
                    streamingSchemes = emptyList(),
                    feedbacks = emptyMap(),
                    onFeedback = { _, _ -> },
                    onCopyScheme = {},
                    onRetry = {},
                    memoryRefs = listOf(refFixture(refText)),
                    memoryRefsExpanded = expanded.value,
                    providerReady = true,
                    onOpenSettings = {}
                )
            }
        }
        // 默认收起：那条正文读不到，清单不占这一屏的纵向高度
        rule.onNode(hasText(refText, substring = true)).assertDoesNotExist()
        // 反向证人：展开态传成 true 时同一条正文读得到。
        // 少了这半条，"清单整块被删空"也能让上面那条永远绿。
        rule.runOnIdle { expanded.value = true }
        rule.waitForIdle()
        rule.onNode(hasText(refText, substring = true)).assertExists()
    }

    /**
     * 结果区下方那条「纠正记忆」是**一颗报得出角色的按钮、一个够下限的热区**，
     * 而不是又一颗"点得中但读屏说不出干什么"的自画文字。
     *
     * 同时钉住那颗 `⋯` 总工具入口整块退场：语义树里再没有拿那条资源当名字的节点。
     */
    @Test
    fun `the correction entry is a labelled button and the utility popup is gone`() {
        mount(fixture(), UiMatrix(600))
        val all = probe.actionableTargets(rule, "ResultArea 纠正入口")
        // 找那颗入口时只认**尺寸 > 0** 的那些颗：滚动容器会把滚出视口的那份压成 0x0 @(0,0)，
        // 而这里用的是 `firstOrNull{名字}`——留着它就会选中那颗 0x0，红在"0 小于下限"
        // 这种没发生过的话上，而不是红在真缺陷上。排除判据是尺寸，不是把这一格改成"只数数量"。
        val targets = probe.laid(all)
        val entry = targets.firstOrNull { it.label == CORRECTION_ENTRY_LABEL }
        assertTrue(
            "结果区下方该有一条「纠正记忆」文字入口，实到名字集合：" +
                targets.map { it.label }.distinct(),
            entry != null
        )
        val hit = entry!!
        assertEquals(
            "那条入口要报 Button（它就是一个动作）：" + hit.describe(),
            "Button", hit.role
        )
        assertTrue(
            "热区两轴都要垫到下限（字形大小另算，那是外观不是可点范围）。" +
                "这一颗不住在卡内那一排里，所以走的仍是全站下限，不跟着紧凑档一起松：" + hit.describe(),
            !hit.tooSmall(probe.floorDp)
        )
        // ⋯ 那颗的锚点是它自己的 contentDescription（资源），不按位置猜。
        // ⚠ "已经不在了"那一判读的是**整棵树**（含 0x0 的那些颗）：排除只服务于尺寸判据，
        //   拿筛过的样本去证明"没有这颗"会给"把它滚出去"留一条假绿。
        val menuDescription = ctx.getString(R.string.panel_result_menu)
        assertTrue(
            "结果区右上角那颗 ⋯ 工具入口已整块删除，还能量到就是没删干净：" +
                all.joinToString { it.describe() },
            all.none { t -> t.contentDescriptions.any { it.contains(menuDescription, ignoreCase = true) } }
        )
    }

    /**
     * 第6节第5条 :532 的后半句：控件要说得出"我现在是什么状态"。
     *
     * 这一屏量到的第二条不是尺寸问题：「赞/踩」被点过之后**只有图标换了颜色**，
     * 语义树里 `selected` 是 null——读屏用户能听到"赞、按钮"，
     * 但听不到"这条方案我已经表过态了"。和 第6节第4条 第四刀那次点踩面板 chip
     * （"选中"只写成 `"✓ " + label` 那个字符串）是同一族缺陷，只是这次是纯变色。
     *
     * 断言不靠"第几颗"：同一屏有多张卡、每颗都有「赞」，所以判的是
     * **表过态那张卡里为 true、没表态的卡里为 false** 两件事都成立。
     */
    @Test
    fun `the feedback icons announce whether that scheme is already marked`() {
        val result = fixture()
        val firstScheme = (result as GenerateResult.Success).response.schemes.first()
        mount(
            result,
            UiMatrix(600),
            feedbacks = mapOf(firstScheme.identity.key to SchemeFeedback.LIKED)
        )
        val targets = probe.laid(probe.actionableTargets(rule, "ResultArea 表态图标"))
        // 样本按**尺寸 > 0** 切：横排里滚出视口的那颗报 0x0 @(0,0)，它没有真状态、也没有真尺寸，
        // 留在样本里既会把"最左/最上"锚点顶到 (0,0)，也会让下面的计数像是渲染出来了。
        val liked = targets.filter { it.label == likeLabel }
        val disliked = targets.filter { it.label == dislikeLabel }
        assertTrue("一屏里至少该有两颗「赞」（${liked.size} 颗）——少了就是卡没渲染出来", liked.size >= 2)
        assertTrue("一屏里至少该有一颗「踩」，实到 ${disliked.size} 颗", disliked.isNotEmpty())
        (liked + disliked).forEach { icon ->
            // 这一族换的是**热区档**（卡内紧凑档，见上面那两格），语义一条都不许跟着换
            assertEquals("表态图标得报 Button：" + icon.describe(), "Button", icon.role)
            assertTrue("表态图标得说得出自己是什么：" + icon.describe(), icon.contentDescriptions.isNotEmpty())
        }
        assertTrue(
            "表过态那颗的「赞」必须把 selected 播报出来，实到 " + liked.joinToString { it.describe() },
            liked.any { it.selected == true }
        )
        assertTrue(
            "没表态那颗的「赞」要报 false，不能报 null——null 等于读屏听不出差别，" +
                "实到 " + liked.joinToString { it.selected.toString() },
            liked.any { it.selected == false }
        )
        assertTrue(
            "「踩」同理要能报出自己的状态：" + disliked.joinToString { it.describe() },
            disliked.all { it.selected != null }
        )
    }
}
