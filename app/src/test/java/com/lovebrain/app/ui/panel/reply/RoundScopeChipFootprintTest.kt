package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 指导书 §10.1 范围符号那一颗（「仅看本轮」，在 ＋ 之后）的**占用空间**判据：
 *
 * > 角色、＋、范围是紧凑固定控件……不要继续用 22dp 图标外包巨大布局盒造成「看着小，仍占48dp」。
 *
 * 这一格量的是**语义树上的实测边界**（`boundsInRoot`），不是读源码里的某一条 modifier：
 * 这一族的旧形状正是「外层透明盒 heightIn/widthIn(min = 48) + 内层 22dp 胶囊」，
 * 只看里面那颗胶囊会读成「已经小了」，而它占的仍是 48dp 见方。
 * 判据按约束链算：`LbChip` 里那颗下限盒由 `style.touchFloor` 决定，
 * 单层与分层两条分支都读同一个 `floorModifier`，所以关掉下限必须两条分支都小。
 *
 * ⚠ 反向证人一起量：同一棵树里声明要下限的那颗粒必须量到 ≥48dp——
 * 少了它，「小于 48」这句就成了恒真（控件被删掉也能绿）。
 * 同时这颗仍然点得着、仍然报得出开/关，缩小占用不等于取消热区。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoundScopeChipFootprintTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(ctx.resources.displayMetrics.density) }

    /** 全站那颗下限（48dp），失败信息里要说清用的是哪一把尺 */
    private val floor: Float get() = probe.floorDp

    private fun boxOf(tag: String): SemanticsProbe.Target {
        val nodes = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("$tag 应当只有一颗", 1, nodes.size)
        return probe.of(nodes.single())
    }

    /**
     * 真实输入行上的那颗范围按钮：可见的就是那颗 22dp 胶囊，外层不再有 48dp 大盒。
     *
     * 两轴都要判（§10 那句「看着小，仍占48dp」里 48 既指高也指宽）：
     * 只收高度、把 `widthIn(min = 48)` 留在外面那颗盒子上，占的还是一个 48dp 见方。
     */
    @Test
    fun `the round scope symbol occupies less than the 48dp box`() {
        var clicks = 0
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column {
                    ReplyInput(
                        draftText = "",
                        currentRole = ChatMessage.Role.HER,
                        editingIndex = -1,
                        onDraftChange = {},
                        onRoleChange = {},
                        onAdd = {},
                        onFocusChange = {},
                        onlyThisRound = true,
                        onOnlyThisRoundChange = { clicks++ }
                    )
                    // 反向证人 A：同一棵树里声明要下限的那颗粒，必须量到下限
                    LbChip(
                        label = "FLOOR_CTRL",
                        selected = false,
                        onClick = {},
                        interaction = LbChipInteraction.Single,
                        style = LbChipStyles.filled.copy(layeredTouch = true, pillHeight = 22.dp),
                        modifier = Modifier.testTag("floor_ctrl")
                    )
                    // 反向证人 B：同一个 pill 档把下限翻回来，也必须长回 48dp 见方
                    LbChip(
                        label = "PILL_FLOOR",
                        selected = false,
                        onClick = {},
                        interaction = LbChipInteraction.Single,
                        style = LbChipStyles.pill.copy(pillHeight = 22.dp, touchFloor = true),
                        modifier = Modifier.testTag("pill_floor")
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val scope = boxOf(PANEL_ROUND_SCOPE_TEST_TAG)
        assertTrue(
            "范围按钮外层仍占高度（§10.1「看着小，仍占48dp」）：${scope.describe()}",
            scope.heightDp + 0.5f < floor
        )
        assertTrue(
            "范围按钮外层仍占宽度（widthIn 那颗大盒没关掉）：${scope.describe()}",
            scope.widthDp + 0.5f < floor
        )
        // 缩小 ≠ 删掉：这颗仍在位、仍是那颗 22dp 胶囊的可见尺寸
        assertTrue("小过头就是没画出来：${scope.describe()}", scope.heightDp >= 16f)
        assertTrue("小过头就是没画出来：${scope.describe()}", scope.widthDp >= 12f)

        // 反向证人：下限档在两轴上仍到 48dp，上面那两句不是恒真
        val floorCtrl = boxOf("floor_ctrl")
        assertTrue("分层档的下限盒没垫到位：${floorCtrl.describe()}", !floorCtrl.tooSmall(floor))
        val pillFloor = boxOf("pill_floor")
        assertTrue("单层档的下限盒没垫到位：${pillFloor.describe()}", !pillFloor.tooSmall(floor))

        // 占用小了，点击与语义没被一起缩掉
        val scopeNodes = rule.onAllNodesWithTag(PANEL_ROUND_SCOPE_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("范围按钮仍在语义树上", 1, scopeNodes.size)
        assertEquals(
            "开关状态仍在语义树上",
            ToggleableState.On,
            scopeNodes.single().config.getOrNull(SemanticsProperties.ToggleableState)
        )
        rule.onNodeWithTag(PANEL_ROUND_SCOPE_TEST_TAG).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点击仍只投一次回调", 1, clicks)
    }

    /**
     * 设计系统那一层的判据：`touchFloor = false` 时，**单层与分层两条分支**都不长 48dp 大盒。
     *
     * 为什么要两档一起量：这一族的透明大盒由 `LbChip` 的 `floorModifier` 提供，
     * 分层那条分支（外面那颗只负责点击与语义的盒）是最容易「看着小、其实仍占 48」的形状。
     */
    @Test
    fun `a chip that declines the floor stays compact on both branches`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column {
                    LbChip(
                        label = "COMPACT_FLAT",
                        selected = false,
                        onClick = {},
                        interaction = LbChipInteraction.Multi,
                        style = LbChipStyles.pill.copy(
                            pillHeight = 22.dp,
                            markSelectedWithCheck = true,
                            touchFloor = false,
                            layeredTouch = false
                        ),
                        modifier = Modifier.testTag("compact_flat")
                    )
                    LbChip(
                        label = "COMPACT_LAYERED",
                        selected = false,
                        onClick = {},
                        interaction = LbChipInteraction.Multi,
                        style = LbChipStyles.pill.copy(
                            pillHeight = 22.dp,
                            markSelectedWithCheck = true,
                            touchFloor = false,
                            layeredTouch = true
                        ),
                        modifier = Modifier.testTag("compact_layered")
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        listOf(boxOf("compact_flat"), boxOf("compact_layered")).forEach { chip ->
            assertTrue("关掉下限后仍占 48dp 高：${chip.describe()}", chip.heightDp + 0.5f < floor)
            assertTrue("胶囊自己也得在位：${chip.describe()}", chip.heightDp in 16f..30f)
            // 这一格不判宽度：两颗证人是文字标签芯片，宽度=文案自然宽（"COMPACT_LAYERED"
            // 这句就有 85dp），与 48 下限无关。§10 点名的"巨大布局盒"是 min **高度**那一轴，
            // 图标芯片的两轴读数以真正那颗「仅看本轮」（21x22）与反证 ≥48 的档位为准。
        }
    }
}
