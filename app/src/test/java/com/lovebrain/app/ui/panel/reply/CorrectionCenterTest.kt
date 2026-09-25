package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
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
 * §6.4 :523 第三刀——记忆纠正中心。
 *
 * 搬之前它是面板顶层 `Box` 里一块 `Column(fillMaxWidth)` 内联展开区，
 * 这台仪器在同一块 360x900dp 挂载槽里量到（账本 §33 记了原文）：
 * 里面**每一颗**可点的东西——「关闭」和两条「撤销」——都是同一个尺寸，
 * 够不到 §6.5 :531 的 48dp 下限；而它没有遮罩，也就谈不上"盖住面板"。
 *
 * 挂载槽用 900dp 高而不是 400dp，是为了让"居中"和"贴着顶"差出几百 dp，
 * 不靠猜阈值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CorrectionCenterTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }
    private val density: Float get() = app.resources.displayMetrics.density

    private val records = mapOf(
        "mem-1" to MemoryCorrection(
            memoryId = "mem-1",
            action = CorrectionAction.MUTED,
            muteDuration = MuteDuration.TODAY,
        ),
        "mem-2" to MemoryCorrection(
            memoryId = "mem-2",
            action = CorrectionAction.WRONG,
            replacementText = "她把答辩改到下周了",
        ),
    )

    private val undone = mutableListOf<String>()

    private fun mount(corrections: Map<String, MemoryCorrection> = records): CorrectionCenterHolder {
        val holder = CorrectionCenterHolder()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    CorrectionCenterHost(
                        holder = holder,
                        corrections = corrections,
                        onUndoCorrection = { undone += it }
                    )
                }
            }
        }
        holder.open()
        rule.mainClock.advanceTimeBy(16L)
        return holder
    }

    private fun titleTop(label: String): Float? =
        rule.onAllNodes(hasText(label)).fetchSemanticsNodes().firstOrNull()
            ?.boundsInRoot?.let { it.top / density }

    /** §6.5 第①栏：纠正中心里每一颗可点的东西，热区都得够 48dp */
    @Test
    fun `every control in the correction center meets the touch floor`() {
        mount()
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "纠正中心")
        // 反空跑：这把尺得真看得见东西（一条记录一颗撤销 + 一颗关闭）
        assertEquals(
            "该量到 2 颗撤销 + 1 颗关闭：" + targets.joinToString { it.describe() },
            listOf("关闭", "撤销", "撤销"), targets.map { it.label }.sorted()
        )
    }

    /** §6.5 第②栏：可点的东西要有读屏名字 */
    @Test
    fun `every control in the correction center is announced`() {
        mount()
        probe.assertAllActionableLabeled(rule, "纠正中心")
    }

    /**
     * 它得是**浮层**，不是往页面里插一块：
     * 标题要落在整块槽位的中部（900dp ⇒ 300–600dp 之间）。
     */
    @Test
    fun `the center opens as a sheet centered on the panel, not as an inline block`() {
        mount()
        val top = titleTop("记忆纠正中心")
        assertNotNull("开起来就该看得到", top)
        assertTrue(
            "标题的 y 应当在槽位中部，实到 ${top}dp。" +
                "内联展开区会贴着顶（≈8dp），这条就是抓它退回原形的",
            top!! in 300f..600f
        )
    }

    /** 撤销交回的是**这一条**的 id，不是永远第一条 */
    @Test
    fun `undo hands back the memory id of the row that was pressed`() {
        mount()
        // 这一版 ui-test 的集合上没有 onFirst()，用索引取（`[0]` 才是本仓库跑通过的写法）
        val nodes = rule.onAllNodes(hasText("撤销")).fetchSemanticsNodes()
        assertEquals("两条记录该有两颗撤销，实到 ${nodes.size}", 2, nodes.size)
        rule.onAllNodes(hasText("撤销"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("按下第一条该交第一条", listOf("mem-1"), undone.toList())
        rule.onAllNodes(hasText("撤销"))[1].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "第二条必须交它自己的 id——写死成第一个 id 的坏实现会红在这里",
            listOf("mem-1", "mem-2"), undone.toList()
        )
    }

    /** 空态要自己说话，且退出入口仍在 */
    @Test
    fun `with no records the center still says so and still lets you leave`() {
        mount(emptyMap())
        val texts = rule.onAllNodes(hasText("暂无纠正记录。在「本轮参考」中可对记忆发起纠正。"))
            .fetchSemanticsNodes().size
        assertEquals("空态文案应当恰好一句，实到 $texts", 1, texts)
        val targets = probe.actionableTargets(rule, "空纠正中心")
        assertEquals(
            "空态里唯一那颗该是退出入口：" + targets.joinToString { it.describe() },
            listOf("关闭"), targets.map { it.label }
        )
    }

    /** 持有者：关了就真的没了（不是"还在树里只是看不见"） */
    @Test
    fun `the holder is the only thing deciding whether the sheet exists`() {
        val holder = CorrectionCenterHolder()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    CorrectionCenterHost(holder, records, onUndoCorrection = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("没 open 之前树里不该有标题", 0, titleNodes())
        holder.open()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("open 之后恰好一颗", 1, titleNodes())
        holder.close()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("close 之后要回 0，留着就是画而不见", 0, titleNodes())
    }

    private fun titleNodes(): Int =
        rule.onAllNodes(
            SemanticsMatcher("文案含「记忆纠正中心」") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { "记忆纠正中心" in it.text } ?: false
            }
        ).fetchSemanticsNodes().size
}
