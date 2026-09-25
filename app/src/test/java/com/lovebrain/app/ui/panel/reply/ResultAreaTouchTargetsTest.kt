package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.SchemeFeedback
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 :490 与 §6.5 :531/:532 一起欠的那笔：**`ResultArea` 从来没在 JVM 语义树里挂起来过**。
 *
 * 因为挂不起来，账本 §38.7 里那 7 处自造按钮有 **两处就在这屏**（`:340`、`:1230`）
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
 * 因此第二格沿着那条横排**逐张滚过去**，每一档都要求全树过下限。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultAreaTouchTargetsTest {

    private companion object {
        /** 生产里已有的锚点（`ResultArea.kt:507`），设备侧那格用的是同一个串 */
        const val SCHEME_ROW_TAG = "scheme_cards_row"
    }

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /** 四风格 + 四方向各给两条真实长度的文案；方向里的 null = "本轮不适合"（生产就有这一态） */
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

    private fun mount(
        result: GenerateResult,
        matrix: UiMatrix,
        feedbacks: Map<String, SchemeFeedback> = emptyMap()
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
     * 不是为本格新加的夹具件——本格不改生产代码就能量到它。
     * 拿 tag 而不是拿"横向可滚"这种形状：竖排那几条 `verticalScroll` 也带 scroll 动作，
     * 按形状筛会拿错对象，拿错了照样能滚、照样绿，量的却是竖排。
     */
    private fun schemeRow() = rule.onNodeWithTag(SCHEME_ROW_TAG)

    /**
     * 量"手指在这屏点不点得中"，只看**完整落在视口里**的可交互节点。
     *
     * 为什么要筛：方案卡那条 `LazyRow` 里，只露出半张的卡片其节点拿到的是
     * **被视口裁过**的尺寸——本机实测最右那张卡露 24dp，那颗图标的 bounds 就报
     * **24x48dp**（再往外的报 0x0dp）。那不是热区不达标，那是滚动容器在裁；
     * 拿它判缺陷会把"容器裁切"读成"控件做小了"，然后去修一个没坏的东西。
     *
     * 判据是"左右都严格在视口内"（`strictBothEdges` 为真时）：**贴左边界那颗会被横排裁**。
     * 本机实测过两次才定下来——第一版只卡右边界，结果滚到第 2 张时，
     * 那张已经滚出左边的卡报来 **28x48dp @(0,196)**，又是一次容器裁切、不是控件做小了。
     * 贴边的正常控件（页头那颗 Tab 在 x=0）由**首屏那一格**判，那一格不卡左边界，
     * 所以这里收紧没有少判任何一颗。
     *
     * ⚠ "筛掉"必须是看得见的动作：排除项**连同尺寸一起打进失败信息**，
     * 并且要求留下的样本 >= 6 颗——一旦筛到只剩两三颗，这一格就退化成空转。
     * 覆盖不靠这一格赌：下面那格沿横排逐张滚过去，每张卡都会在某一档完整露出一次。
     */
    private fun assertReachableControlsMeetFloor(
        screen: String,
        context: String,
        viewportWidthDp: Int,
        strictBothEdges: Boolean = false
    ) {
        val all = probe.actionableTargets(rule, screen)
        val leftOk = if (strictBothEdges) 0.5f else -0.5f
        val (reachable, excluded) = all.partition { t ->
            t.widthDp > 0f && t.heightDp > 0f && t.leftDp > leftOk && t.topDp >= 0f &&
                t.leftDp + t.widthDp < viewportWidthDp - 0.5f
        }
        assertTrue(
            "$screen$context 视口内只量到 ${reachable.size} 个可交互节点（整树 ${all.size} 个）——" +
                "样本这么少，断言会退化成空转：先怀疑夹具没把内容渲染出来",
            reachable.size >= 6
        )
        val offenders = reachable.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "$screen$context 有 ${offenders.size}/${reachable.size} 个可交互节点小于 " +
                "${probe.floorDp.toInt()}dp：\n" +
                offenders.joinToString("\n") { "  " + it.describe() } +
                "\n  （另排除 ${excluded.size} 个被视口裁掉/未布局完的：" +
                excluded.joinToString { it.describe() } + "）",
            offenders.isEmpty()
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
        // 滚几档 = 这条横排真有几张卡，**从夹具算出来**，不写死。
        // 写死 8 的那一版当场炸在 `Can't scroll to index 4, it is out of bounds [0, 4)`——
        // 横排显示的是当前那一档（风格四张），不是"风格+方向共八张"。
        val cards = (result as GenerateResult.Success).response.schemes.size
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
                    "复制|Button", "赞|Button", "踩|Button",
                    "风格|Tab", "方向|Tab"
                )
            )
        )
    }

    /**
     * §6.5 :532 的后半句：控件要说得出"我现在是什么状态"。
     *
     * 这一屏量到的第二条不是尺寸问题：「赞/踩」被点过之后**只有图标换了颜色**，
     * 语义树里 `selected` 是 null——读屏用户能听到"赞、按钮"，
     * 但听不到"这条方案我已经表过态了"。和 §6.4 第四刀那次点踩面板 chip
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
        val targets = probe.actionableTargets(rule, "ResultArea 表态图标")
        val liked = targets.filter { it.label == "赞" }
        val disliked = targets.filter { it.label == "踩" }
        assertTrue("一屏里至少该有两颗「赞」（${liked.size} 颗）——少了就是卡没渲染出来", liked.size >= 2)
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
            disliked.isNotEmpty() && disliked.all { it.selected != null }
        )
    }
}
