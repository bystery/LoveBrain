package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
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
 * 第6节第1条 表第 3 行 `LbSection` 的两档：常驻档与可折叠档**共用同一档标题外观**，
 * 折叠档额外要把折叠入口的三件事一次给全（热区下限、`Role.Button`、状态公告）。
 *
 * 为什么这颗组件需要新格子，而不是让 `HomeScreenStructureTest` 那三处标题继续代判：
 * 那几格量的是首页文档流里的常驻档，折叠档是新长的一档，而它要防的正是
 * "第二天有人在别的页面又把折叠行手抄一遍"。本机把结果区那颗手抄件挂进语义树实量过：
 * **360x41dp、role=无、stateDescription 没设过**（读数写在 `LbSection` 的 KDoc 与那一页的
 * `OngoingSectionSemanticsTest` 里）。
 *
 * 判据全部读语义树，不读源码里的 `style =` / `role =`：声明了不等于树里读得到，
 * 而源码里留着"这里用的是共用组件"那句话也挡不住字号被调小。
 *
 * ⚠ 比高度一律用**同一句话在两档各挂一次**，不用两句不同的话：
 * 第一版这里英文串对中文串，量出 18dp 对 22dp，红的不是"样式有两份"而是
 * "中文回退字体的行盒更高"（Robolectric NATIVE 下就是这么不一样）。按字符串形状比的断言
 * 必须先证明比的是同一串，否则它咬的是字形。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val density: Float get() = app.resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val expanded: MutableState<Boolean> = mutableStateOf(false)

    private companion object {
        const val PLAIN_TITLE = "SECTION_PLAIN_TITLE"
        const val FOLD_TITLE = "SECTION_FOLD_TITLE"

        /**
         * 两档共用的那一句要**短到在折叠档里不换行**。
         *
         * 第一版这里写的是 `SECTION_SAME_TITLE_BOTH_TIERS`，本机量出常驻档 18dp、折叠档 **40dp**——
         * 差的不是字号，是折叠档那一行的标题带 `weight(1f)`（让位给尾部那颗状态词），
         * 长句子于是换成两行。拿这个形状去比"两档是不是同一档字"，咬到的是换行。
         */
        const val SAME_TITLE = "SECTION_TITLE"
        const val FOLD_LABEL = "SECTION_FOLD_LABEL"
        const val BODY = "SECTION_BODY_SENTINEL"
    }

    /** 两档各挂一句自己的话：给折叠入口那几格当唯一被测对象用 */
    private fun mountBoth(matrix: UiMatrix = UiMatrix(360)) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                Column {
                    LbSection(PLAIN_TITLE)
                    FoldedSection(FOLD_TITLE)
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 同一句话在两档各挂一次：给"标题只有一份样式"那格用 */
    private fun mountSameTitleBothTiers() {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(360).RenderIn(deviceDensity) {
                Column {
                    LbSection(SAME_TITLE)
                    FoldedSection(SAME_TITLE)
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Composable
    private fun FoldedSection(title: String) {
        LbSection(
            title = title,
            fold = LbSectionFold(
                expanded = expanded.value,
                onToggle = { expanded.value = !expanded.value },
                label = { Text(FOLD_LABEL) }
            ),
            content = { Text(BODY) }
        )
    }

    /** 未合并树里**带 [LbTags.SECTION] 锚点**、文案就是 `text` 的标题节点（可点父节点会吸走子文案） */
    private fun titleNodes(text: String): List<SemanticsNode> =
        rule.onAllNodesWithTag(LbTags.SECTION, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }

    private fun heightDp(node: SemanticsNode): Float = node.boundsInRoot.height / density

    /** 折叠入口：折叠档只该有这一处操作 */
    private fun foldEntry(): SemanticsProbe.Target {
        val targets = probe.actionableTargets(rule, "LbSection 折叠档")
        assertEquals(
            "折叠入口应当恰好一颗：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        return targets.single()
    }

    /** ① 两档共用同一档标题外观——标题样式没有第二份 */
    @Test
    fun `both tiers draw the title at the same size`() {
        mountSameTitleBothTiers()
        val heights = titleNodes(SAME_TITLE).map { heightDp(it) }
        assertEquals(
            "同一句话应当在两档各挂出一颗标题节点，实到 ${heights.size} 颗（高度 $heights）",
            2, heights.size
        )
        assertEquals(
            "常驻档与折叠档的标题必须等高（${heights.first()} dp 对 ${heights.last()} dp）",
            heights.first(), heights.last(), 0.5f
        )
    }

    /** ② 折叠状态要说得出来，而且换状态时那句公告跟着换——走的是全站那两条资源 */
    @Test
    fun `the fold entry announces collapsed then expanded`() {
        mountBoth()
        val before = foldEntry()
        assertNotNull(
            "折叠入口没有 stateDescription，读屏只会念标题、不会说它是收起的：" + before.describe(),
            before.stateDescription
        )
        assertEquals(
            "收起态念资源那一条，而不是这一页自己的两个中文字",
            app.getString(R.string.state_collapsed), before.stateDescription
        )
        assertEquals("折叠入口得报成按钮", "Button", before.role)

        rule.onAllNodes(hasClickAction())[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "点开之后公告跟着换",
            app.getString(R.string.state_expanded), foldEntry().stateDescription
        )
    }

    /** ③ 第6节第5条 :531：折叠入口的热区 ≥48dp（归并前那一页实量 41dp） */
    @Test
    fun `the fold entry meets the touch floor`() {
        mountBoth()
        val entry = foldEntry()
        assertTrue(
            "折叠入口低于 ${probe.floorDp.toInt()}dp 下限：" + entry.describe(),
            !entry.tooSmall(probe.floorDp)
        )
    }

    /** ④ 最坏那一格：最窄 + 2.0 倍字，下限不缩、公告还在 */
    @Test
    fun `the fold entry keeps floor and announcement at 320dp with 2x font`() {
        mountBoth(UiMatrix(320, fontScale = 2.0f))
        probe.assertAllActionableMeetTouchFloor(rule, "LbSection 折叠档", "（320dp-font200）")
        assertEquals(app.getString(R.string.state_collapsed), foldEntry().stateDescription)
    }

    /** ⑤ 内容真的跟着状态走：收起态不许把内容摆进树里 */
    @Test
    fun `content is gated by the fold state`() {
        mountBoth()
        assertEquals("收起态不该有内容节点", 0, textCount(BODY))
        rule.onAllNodes(hasClickAction())[0].performClick()
        rule.waitForIdle()
        assertEquals("点开之后内容要在", 1, textCount(BODY))
    }

    /** ⑥ 常驻档仍是"标题"而不是按钮——有了折叠档之后，这一格钉的是"别把两档做成一样可点" */
    @Test
    fun `the plain tier is not actionable`() {
        mountBoth()
        val targets = probe.actionableTargets(rule, "两档并排")
        assertEquals(
            "两档并排的树上只该有折叠入口这一处可点：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        assertTrue(
            "那一处可点的必须是折叠档的标题行：" + targets.single().describe(),
            targets.single().label.contains(FOLD_TITLE)
        )
        // 常驻档那颗标题照样读得到（没被折叠档顶掉、也没被合并掉名字）
        val plain = titleNodes(PLAIN_TITLE)
        assertEquals(1, plain.size)
        assertEquals(
            PLAIN_TITLE,
            plain.single().config.getOrNull(SemanticsProperties.Text).orEmpty().single().text
        )
    }

    private fun textCount(text: String): Int = rule.onAllNodes(
        SemanticsMatcher("节点文案含 $text") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }
    ).fetchSemanticsNodes().size
}
