package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbSection
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.OngoingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 表第 3 行落到结果区那块折叠卡上的三件事：**标题样式只有一个所有者**、
 * **折叠入口点得中**、**折叠状态说得出**。
 *
 * ## 为什么标题那一格量两颗节点、不读源码里的 `style =`
 *
 * 归并之前这一页写的是 `AppTypography.labelMedium` + `TextSecondary`，而设计系统那颗是
 * `titleMedium` + `TextPrimary` + SemiBold。读源码判不出漂移（「这里用的是共用组件」这句
 * 好话谁都会在改小字号之后继续留着），所以同一棵树上并排挂两颗分区标题：
 * 常驻档那颗（就是表里那一档）与这一页那颗，比**它们渲染出来的行盒高度**，
 * 并要求这一页那颗就是 `LbTags.SECTION` 锚点下的那颗节点——
 * 「归并进 LbSection」在语义树里的形状就是这个：标题不是页面新画的一行字，
 * 而是设计系统那一颗标题。
 *
 * ⚠ 比的是高度而不是字体名：语义树不报 `TextStyle`。同一档字在同 density / 同 fontScale 下
 * 行盒等高，换一档就差 5dp（反证那一注把折叠档标题换成这一页原来用的 `labelMedium`（11sp），
 * 本机实量中文串 22.0dp 对 17.0dp、英文串 18.0dp 对 13.0dp；共用档那颗是 `titleMedium` 15sp）。
 * 两句必须**是同一句话**：第一版这里英文串对中文串，红的是"中文回退字体行盒更高"，
 * 不是"样式有两份"——那种判据咬错东西。
 *
 * ## 这几格在归并之前就是红的（本机原始读数，跑在还没动过的那份实现上）
 *
 * - `the fold entry meets the touch floor`：`进行中事项卡 有 1/1 个可交互节点小于 48dp
 *   （density=1.0）：「进行中事项」 role=无 selected=null state=null 尺寸 360x41dp @(0,22)`
 *   ——折叠入口当时靠 `padding(lg)` 垫出来、没写下限，也没有角色；
 * - `the fold entry announces collapsed then expanded`：同一行 `state=null`，
 *   读屏只念得出「进行中事项 展开」那两个**内联中文**，英文环境下照样念中文；
 * - `the ongoing title is the shared section heading node`：`带 lb_section 锚点、
 *   文案为「进行中事项」的标题节点应当恰好一颗，实到 0`——那一行是页面自己画的 `Text`，
 *   连锚点都不存在。
 *
 * 别把这里的断言放宽回"量不到就算了"：上面三条就是"量不到"长什么样。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OngoingSectionSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val density: Float get() = app.resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val items = listOf(
        OngoingItem(name = "ITEM_ONE_SENTINEL", status = "进行中", state = "STATE_ONE_SENTINEL"),
        OngoingItem(name = "ITEM_TWO_SENTINEL", status = "已完成", state = "")
    )

    /**
     * 比较基准用的那一句 = 这一页自己的标题文案。
     *
     * ⚠ 为什么不用 ASCII 哨兵串：上一版这里挂了两句**不同的**话（常驻档一句英文、
     * 这一页一句中文），结果量到 18dp 对 22dp——差的不是字号，是**中文与英文的字形盒**
     * （Robolectric NATIVE 下中文回退字体的行盒更高）。按不同字符串比高度，
     * 判的就不是"同一档字"而是"同一句话"，那种判据会自己红给人看。
     * 现在同一句话在两档各挂一次，比的才是样式。
     */
    private val ongoingTitle = "进行中事项"

    private fun mount(matrix: UiMatrix = UiMatrix(360)) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                Column {
                    LbSection(ongoingTitle)
                    OngoingSection(items = items)
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 折叠入口：这块卡上唯一带点击动作的节点。多一颗就说明别处又长了一份 */
    private fun foldEntry(): SemanticsProbe.Target {
        val targets = probe.actionableTargets(rule, "进行中事项卡")
        assertEquals(
            "折叠入口应当恰好一颗：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        return targets.single()
    }

    private fun dp(v: Float) = (v / density).toInt()

    /**
     * **带 SECTION 锚点**、文案就是 `text` 的那几颗标题节点。
     *
     * 未合并树：可点的父节点会把子文案吸进自己那份 config，按文案在合并树里找会同时找到整行，
     * 于是量到的是行高而不是标题行盒。空集合 = 这一页的标题不是设计系统那颗。
     */
    private fun sectionTitles(text: String): List<androidx.compose.ui.semantics.SemanticsNode> {
        val tagged = rule.onAllNodesWithTag(LbTags.SECTION, useUnmergedTree = true)
            .fetchSemanticsNodes()
        return tagged.filter { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                .orEmpty().any { it.text == text }
        }.also { found ->
            assertTrue(
                "带 lb_section 锚点、文案为「$text」的标题节点一颗都没量到，说明这一页的标题不是" +
                    "设计系统那一颗。这一屏全部锚点节点：" + tagged.joinToString {
                        "(宽${dp(it.boundsInRoot.width)}x高${dp(it.boundsInRoot.height)})"
                    },
                found.isNotEmpty()
            )
        }
    }

    private fun heightDp(node: androidx.compose.ui.semantics.SemanticsNode): Float =
        node.boundsInRoot.height / density

    /**
     * ① 这一页的分区标题**不是**页面另造的那一档，两句话合起来才判得住：
     * - 「进行中事项」得是 `LbTags.SECTION` 锚点下的那颗节点（归并之前这里实量 **0 颗**——
     *   那一行是页面自己画的 `Text`，压根没有锚点）；
     * - 常驻档挂的是**同一句话**，两档量出来的行盒必须等高。反证那一注把折叠档的标题换成
     *   `labelMedium`（这一页归并之前用的就是它），本机实量：中文串 22.0dp 对 17.0dp、
     *   英文串 18.0dp 对 13.0dp——**差 5dp**，所以 tolerance 只给 0.5dp：
     *   留的是 Robolectric 取整，不是"大概一样"。
     */
    @Test
    fun `the ongoing title is the shared section heading node`() {
        mount()
        val heights = sectionTitles(ongoingTitle).map { heightDp(it) }
        assertTrue(
            "同一句话至少要有常驻档与折叠档各一颗，实到 ${heights.size} 颗（高度 $heights）",
            heights.size >= 2
        )
        assertEquals(
            "「$ongoingTitle」这一页量到 ${heights.last()} dp、共用档 ${heights.first()} dp：" +
                "标题样式不许有第二份",
            heights.first(), heights.last(), 0.5f
        )
    }

    /** ② §6.5 :531：折叠入口热区 ≥48dp */
    @Test
    fun `the fold entry meets the touch floor`() {
        mount()
        probe.assertAllActionableMeetTouchFloor(rule, "进行中事项卡")
    }

    /** ③ 最坏那一格：最窄 + 2.0 倍字，热区不缩 */
    @Test
    fun `the fold entry keeps its floor at 320dp with 2x font`() {
        mount(UiMatrix(320, fontScale = 2.0f))
        probe.assertAllActionableMeetTouchFloor(rule, "进行中事项卡", "（320dp-font200）")
    }

    /** ④ §6.5 :532：可访问名要说得出"现在收起 / 展开"，换状态时那句公告跟着换 */
    @Test
    fun `the fold entry announces collapsed then expanded`() {
        mount()
        val collapsed = foldEntry()
        assertNotNull(
            "折叠入口没有 stateDescription，读屏只会念标题、不会说它是收起的：" + collapsed.describe(),
            collapsed.stateDescription
        )
        assertEquals(
            "收起态的公告必须走全站那两条资源，而不是这一页的两个中文字",
            app.getString(R.string.state_collapsed), collapsed.stateDescription
        )

        rule.onAllNodes(hasClickAction())[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "点开之后公告得跟着变成展开",
            app.getString(R.string.state_expanded), foldEntry().stateDescription
        )
    }

    /** ⑤ 内容真的跟着状态走：收起态不许把事项行摆进树里（不然④可以只是一句空转的公告） */
    @Test
    fun `content is gated by the fold state`() {
        mount()
        assertEquals(
            "默认收起态不许把事项行摆进树里",
            0, rule.onAllNodes(hasText("ITEM_ONE_SENTINEL")).fetchSemanticsNodes().size
        )

        rule.onAllNodes(hasClickAction())[0].performClick()
        rule.waitForIdle()
        assertEquals("第一条事项", 1, rule.onAllNodes(hasText("ITEM_ONE_SENTINEL")).fetchSemanticsNodes().size)
        assertEquals("第二条事项", 1, rule.onAllNodes(hasText("ITEM_TWO_SENTINEL")).fetchSemanticsNodes().size)
        assertEquals(
            "state 那行补充说明也该在",
            1, rule.onAllNodes(hasText("STATE_ONE_SENTINEL")).fetchSemanticsNodes().size
        )
    }
}
