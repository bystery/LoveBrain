package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 纠正中心那一行的标题：**内部 id 只做 key，念出来的是用户认得出的东西**。
 *
 * 旧写法 `memoryId.takeLast(30)` 直接端出 `PROFILE:understand/me.md` 这类内部路径：
 * 既暴露了库里的文件名，也答不上"我到底纠正了哪一条"。
 *
 * 三条来路各钉一格：事项条目用条目名（用户自己写的那句话）、文件级用分区名（知识库编辑页
 * 那张唯一的名单）、认不出的才退回旧兜底——不许为了好看编一个名字。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CorrectionCenterMemoryTitleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a file level id reads as the section name instead of the internal path`() {
        assertEquals("我是谁", readableMemoryTitle("PROFILE:understand/me.md"))
        assertEquals("此刻状态", readableMemoryTitle("SCENE:moment/scene.md"))
    }

    @Test
    fun `an ongoing entry id reads as the item name the user typed`() {
        assertEquals("买花", readableMemoryTitle("ONGOING:moment/plan.md:买花"))
    }

    @Test
    fun `legacy hash tails and unknown paths never get dressed up as a name`() {
        // 旧版本把内容 hash 当日志尾巴：它是内部形状，不许被当成条目名念出来
        assertEquals("我是谁", readableMemoryTitle("PROFILE:understand/me.md:ab12cd34"))
        // 名单里没有的路径：退回旧的 takeLast(30) 兜底，不编（这颗 id 不足 30 字，原样回来）
        assertEquals("SCENE:moment/unknown.md", readableMemoryTitle("SCENE:moment/unknown.md"))
        assertEquals("mem-legacy", readableMemoryTitle("mem-legacy"))
    }

    @Test
    fun `the center draws the readable title and never the raw path`() {
        val holder = CorrectionCenterHolder()
        val corrections = linkedMapOf(
            "PROFILE:understand/me.md" to MemoryCorrection(
                memoryId = "PROFILE:understand/me.md",
                action = CorrectionAction.MUTED,
            ),
            "ONGOING:moment/plan.md:买花" to MemoryCorrection(
                memoryId = "ONGOING:moment/plan.md:买花",
                action = CorrectionAction.FINISHED,
            )
        )
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    CorrectionCenterHost(
                        holder = holder,
                        corrections = corrections,
                        onUndoCorrection = {}
                    )
                }
            }
        }
        holder.open()
        rule.mainClock.advanceTimeBy(16L)

        rule.onNodeWithText("我是谁").assertExists()
        rule.onNodeWithText("买花").assertExists()

        val spoken = rule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("节点带文字") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
            }
        ).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
        assertTrue(
            "纠正中心必须念得出人话（读到的全是：$spoken）",
            spoken.any { it.contains("我是谁") } && spoken.any { it.contains("买花") }
        )
        assertFalse(
            "内部路径不许出现在用户读得到的文字里：$spoken",
            spoken.any { it.contains("understand/me.md") || it.contains("moment/plan.md") }
        )
        // 反向证人：这一行仍然是"一条记录一颗撤销"，不是被标题改写整行吞掉
        assertEquals(2, rule.onAllNodes(hasText("撤销")).fetchSemanticsNodes().size)
    }
}
