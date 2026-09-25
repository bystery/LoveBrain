package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.MuteDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.4：本轮参考记忆的纠正浮层归**独立 state holder + 单一宿主**之后，形状必须成立。
 *
 * 搬之前本机量到（同一个仪器、360x400dp 挂载槽，`MemoryRefItem` 自己在行里画浮层）：
 * 「标记为错误」那颗标题落在 **y = 139–161dp**，也就是浮层只铺满那一行的高度，
 * 行以外那一片还在遮罩下面露着——`LbModalSheet` 的 `fillMaxSize()` 铺的是**它的父容器**。
 * 挂载槽这里刻意用 900dp 高：把"盖住一行"和"盖住整块面板"差成几百 dp，
 * 不靠猜阈值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryCorrectionFlowTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }
    private val density: Float get() = app.resources.displayMetrics.density

    private val ref = MemoryRef(
        id = "mem-1", kbId = "kb", kind = MemoryKind.SCENE,
        text = "她这周主要在赶毕业设计", sourcePath = "scene/x.md"
    )

    private var muteCalls: Pair<String, MuteDuration>? = null
    private var wrongCalls: Pair<String, String>? = null

    /** 面板怎么拼，这里就怎么拼：行 + 顶层宿主（360x900dp 槽位） */
    private fun mount(withHost: Boolean = true) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                val flow = rememberMemoryCorrectionFlow()
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    Column {
                        MemoryRefItem(
                            ref = ref,
                            onCorrection = { _, _, _, _ -> },
                            onUndoCorrection = {},
                            correctionFlow = flow
                        )
                    }
                    if (withHost) {
                        MemoryCorrectionFlowHost(
                            flow = flow,
                            onMute = { id, d -> muteCalls = id to d },
                            onWrong = { id, text -> wrongCalls = id to text }
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun openMenuAndPick(item: String) {
        rule.onNodeWithText("⋯").performClick()
        rule.mainClock.advanceTimeBy(16L)
        val nodes = rule.onAllNodes(hasText(item)).fetchSemanticsNodes()
        assertEquals("菜单里「$item」应当恰好一个，实到 ${nodes.size}", 1, nodes.size)
        rule.onAllNodes(hasText(item))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun titleTop(label: String): Float? {
        val nodes = rule.onAllNodes(hasText(label)).fetchSemanticsNodes()
        return nodes.firstOrNull()?.boundsInRoot?.let { it.top / density }
    }

    /** 浮层不再由那一行渲染：宿主缺席时，点了菜单也不该在树里冒出一颗遮罩 */
    @Test
    fun `the row no longer draws its own sheet`() {
        mount(withHost = false)
        openMenuAndPick("不对")
        assertNull(
            "只挂行、不挂宿主时不该出现「标记为错误」——" +
                "行里还留着浮层的话，这条会红",
            titleTop("标记为错误")
        )
    }

    /** 标题应当落在整块槽位的中部（改之前实测 y=139–161，只盖住那一行） */
    @Test
    fun `the sheet is centered on the whole slot, not on the row`() {
        mount()
        openMenuAndPick("不对")
        val top = titleTop("标记为错误")
        assertNotNull("宿主挂上之后就该看得到浮层", top)
        assertTrue(
            "遮罩要盖得住整块面板：标题的 y 应当在槽位中部（900dp 高 ⇒ 300–600dp 之间），实到 ${top}dp。" +
                "改之前同一台仪器量到的是 139dp（只画在一行里）",
            top!! in 300f..600f
        )
    }

    /**
     * 一次只允许一颗——**这条只能对着持有者量**。
     *
     * 第一版只写了界面路径（先"暂时别提"、取消、再"不对"），于是 V1 探针
     * （`requestWrong` 不再顶掉前一颗）照样绿：那格从头到尾没让两颗**同时**存在过。
     * 而界面上也确实走不到那一步——第一颗的遮罩已经把槽位吞掉，行的 ⋯ 入口点不到。
     * ⇒ 持有者的不变量就对着持有者测，别假装界面能构造它。
     */
    @Test
    fun `the holder keeps at most one request live`() {
        val flow = MemoryCorrectionFlow()
        flow.requestMute("mem-A")
        assertEquals("先要暂停时长", "mem-A", flow.muteTargetId)
        assertNull("不该有第二颗", flow.wrongTargetId)

        flow.requestWrong("mem-B")
        assertNull("第二颗进来要把前一颗顶掉，不是叠两层遮罩", flow.muteTargetId)
        assertEquals("mem-B", flow.wrongTargetId)
        assertEquals("换目标时草稿要清空", "", flow.wrongDraft)

        flow.editWrongDraft("  半截话  ")
        flow.dismiss()
        assertNull("dismiss 之后哪颗都不该在", flow.wrongTargetId)
        assertEquals("dismiss 也要清掉草稿", "", flow.wrongDraft)
    }

    /** 一次只允许一颗：第二颗请求进来时顶掉前一颗，而不是叠两层遮罩 */
    @Test
    fun `a second request replaces the first sheet instead of stacking two`() {
        mount()
        openMenuAndPick("暂时别提")
        assertNotNull("先看到暂停时长", titleTop("暂停时长"))
        val stacked = rule.onAllNodes(
            SemanticsMatcher("文案含「标记为错误」") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { "标记为错误" in it.text } ?: false
            }
        ).fetchSemanticsNodes().size
        assertEquals("点了「暂时别提」之后不该同时把「标记为错误」也画出来，实到 $stacked 颗", 0, stacked)

        // 关掉之后再点另一项——两档同时存在是不允许的
        rule.onNodeWithText("取消").performClick()
        rule.mainClock.advanceTimeBy(16L)
        openMenuAndPick("不对")
        assertNotNull("该看到标记为错误", titleTop("标记为错误"))
        assertNull("同时不该还留着暂停时长", titleTop("暂停时长"))
    }

    /** 确认把 id 与草稿交出去；交完浮层必须关闭（不留下"看起来还开着"的状态） */
    @Test
    fun `confirming hands over the id and the draft then closes`() {
        mount()
        openMenuAndPick("不对")
        rule.onAllNodes(hasSetTextAction())[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        rule.onAllNodes(hasSetTextAction())[0]
            .performTextInput("她说明天再答，不是答应约会")
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithText("确认").performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("应交出这条记忆 id 与去掉首尾空白的正文",
            "mem-1" to "她说明天再答，不是答应约会", wrongCalls)
        assertNull("交完之后浮层要关上", titleTop("标记为错误"))
    }

    /**
     * 暂停时长那颗：**三档都点得到且各交各的时长**。
     *
     * 第一版写成"每一档重新 mount 一次"，直接被仪器拦下来
     * （`Cannot call setContent twice per test!`——本仓库那台仪器的硬约束）。
     * 现在同一个组合里循环：开菜单 → 选档 → 交完浮层自己关 → 再开下一轮。
     */
    @Test
    fun `the mute sheet offers every duration and reports the chosen one`() {
        assertEquals("枚举三档，界面就该三档", 3, MuteDuration.entries.size)
        mount()
        MuteDuration.entries.forEach { duration ->
            muteCalls = null
            openMenuAndPick("暂时别提")
            val label = when (duration) {
                MuteDuration.THIS_ROUND -> "仅本轮"
                MuteDuration.TODAY -> "今天剩余"
                MuteDuration.UNTIL_RESTORE -> "直到手动恢复"
            }
            rule.onNodeWithText(label).performClick()
            rule.mainClock.advanceTimeBy(16L)
            assertEquals("点「$label」应交回对应时长", "mem-1" to duration, muteCalls)
            assertNull("交完要关闭", titleTop("暂停时长"))
        }
    }

    /** 反空跑之一：浮层里每个可交互节点都**有名字**（§6.5 第②栏） */
    @Test
    fun `every actionable node in the flow is labeled`() {
        mount()
        openMenuAndPick("不对")
        val targets = probe.assertAllActionableLabeled(rule, "记忆纠正浮层")
        assertTrue("至少该量到取消与确认：" + targets.joinToString { it.describe() },
            targets.size >= 2)
    }

    /**
     * 反空跑之二：浮层里每个可交互节点都**点得到**（§6.5 :531 的 48dp）。
     *
     * 与上一格分开写，是因为 V3 探针（把行的 ⋯ 入口退回 28dp 热区）红的是**尺寸**；
     * 两件事挤在一格里，报错格名会说出一个错的理由。
     * 这一格也是本轮顺手修掉的那条缺陷的守卫：行的 ⋯ 入口以前是
     * `Box(size = 28.dp).clickable{}`，实量 **28x28dp**。
     */
    @Test
    fun `every actionable node in the flow meets the touch floor`() {
        mount()
        openMenuAndPick("不对")
        probe.assertAllActionableMeetTouchFloor(rule, "记忆纠正浮层")
        // 行的 ⋯ 入口单独点名：它就是本轮从 28dp 抬到 48dp 的那一颗
        val trigger = probe.actionableTargets(rule, "记忆纠正浮层")
            .first { it.label == "⋯" }
        assertEquals("行内 ⋯ 入口的热区下限", 48f, trigger.heightDp, 0.6f)
    }

    /**
     * 上一格只量了「不对」那颗。这一格回扫**同一菜单的另一条分支**：
     * 「暂停时长」那颗浮层里的三档，走的是 `CorrectionSubmenuItem`——
     * 一个和取消/确认完全不同的实现，尺寸自然也可能是另一个数。
     *
     * 局部收紧时必须回扫同一个资源的其它出口，否则"修好了 48dp"只对了一半的浮层成立。
     */
    @Test
    fun `the duration menu items meet the touch floor too`() {
        mount()
        openMenuAndPick("暂时别提")
        val targets = probe.actionableTargets(rule, "暂停时长浮层")
            .filter { it.label in MuteDuration.entries.map { d -> durationLabel(d) } }
        assertEquals("三档都该在树里：" + targets.joinToString { it.describe() }, 3, targets.size)
        targets.forEach { t ->
            assertTrue(
                "「${t.label}」这一档的热区应当 ≥48dp，实到 " + t.describe() +
                    "（上一格只量了「不对」那颗，没量这条分支）",
                !t.tooSmall(48f)
            )
        }
    }
}
