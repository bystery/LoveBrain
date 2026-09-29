package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
 * 输入行那三颗角色 chip 归进 `LbChip`（`Single` 一档）之后的读数。
 *
 * 这一族是**互斥单选**，所以规范位是 `Role.Tab` + `Selected`（与页头那三档模式同一写法），
 * 而 `✓` 前缀这一档**故意关掉**：三颗的字面量就是「她」「我」「想法」，
 * `ComposerAddButtonGatingTest` 那一格数角色 chip 数数的就是这三个字，
 * 给它们加上对勾会把那一格改成"找不到 chip"——那是拿归并去动别人的判据。
 *
 * 这颗原来就是"热区与视觉分两层"的形状（外面 48 见方可点、里面 28dp 胶囊），
 * 归并后由 `LbChipStyle.layeredTouch` + `pillHeight` 交出去。下面那格量的正是这件事：
 * 语义树里可点那颗报出来的必须是 48，而不是里面那颗胶囊的 28——
 * 这一族上一格踩过的坑就是"胶囊撑到 48"看着更达标，其实把输入行的高度改了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoleChipSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private var lastRole: ChatMessage.Role? = null

    private fun mount(current: MutableState<ChatMessage.Role>) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "在吗",
                    currentRole = current.value,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = { lastRole = it },
                    onAdd = {},
                    onFocusChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun tabs(what: String) =
        probe.laid(probe.actionableTargets(rule, what)).filter { it.role == "Tab" }

    /** 三颗都在、都报 Tab、都念得出自己的名字，而且**没有**被加上对勾 */
    @Test
    fun `the three role chips are tabs that name themselves without a check mark`() {
        mount(mutableStateOf(ChatMessage.Role.ME))
        val chips = tabs("输入行·角色 chip")
        assertEquals(
            "应当恰好三颗报 Tab：" + chips.joinToString { it.describe() },
            3, chips.size
        )
        assertEquals(
            "标签仍是那三个字，一字不加：" + chips.joinToString { it.describe() },
            setOf("她", "我", "想法"), chips.map { it.label }.toSet()
        )
        chips.forEach { assertTrue("${it.label} 读得出自己", it.labeled) }
    }

    /** `Selected` 两头都要量：起始那一颗 true，其余 false */
    @Test
    fun `exactly one role chip reports selected`() {
        mount(mutableStateOf(ChatMessage.Role.ME))
        val chips = tabs("输入行·选中态")
        val on = chips.filter { it.selected == true }
        assertEquals("该恰好一颗报选中：" + chips.joinToString { it.describe() }, 1, on.size)
        assertEquals("报选中的就是当前那个角色", "我", on.single().label)
        assertEquals(
            "另外两颗必须报未选中（把 selected 写死的坏实现红在这里）：" +
                chips.joinToString { it.describe() },
            2, chips.count { it.selected == false }
        )
    }

    /** 点一颗原本没选的：投递的角色对，`Selected` 也跟着挪 */
    @Test
    fun `tapping another role chip moves the selected flag onto it`() {
        val current: MutableState<ChatMessage.Role> = mutableStateOf(ChatMessage.Role.ME)
        mount(current)

        rule.onAllNodes(hasText("她"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点击要落到 onRoleChange", ChatMessage.Role.HER, lastRole)

        // 生产里这一步是 VM 把 currentRole 换成 HER；测试用同一份状态接住它
        current.value = ChatMessage.Role.HER
        rule.mainClock.advanceTimeBy(16L)
        val chips = tabs("输入行·点后")
        assertEquals(
            "报选中的应换成刚点的那一颗：" + chips.joinToString { it.describe() },
            listOf("她"), chips.filter { it.selected == true }.map { it.label }
        )
        assertEquals("而且仍然恰好一颗", 1, chips.count { it.selected == true })
    }

    /**
     * 可点那颗自己过下限，而且量的必须是**外面那一层**：里面那颗胶囊仍是 28dp。
     *
     * 这一格是给"归并顺手把胶囊撑到 48"那种改法设的闸——它会让读数更漂亮，
     * 但用户看到的输入行会变高。
     */
    @Test
    fun `each role chip's clickable box is the floor, not the pill`() {
        mount(mutableStateOf(ChatMessage.Role.IDEA))
        tabs("输入行·热区").forEach { chip ->
            assertTrue(
                "${chip.label} 的可点节点不到 ${probe.floorDp.toInt()}dp：" + chip.describe(),
                !chip.tooSmall(probe.floorDp)
            )
            assertEquals(
                "${chip.label} 的可点节点应当正好是那颗下限（胶囊在里面 28dp）：" + chip.describe(),
                48f, chip.heightDp, 0.6f
            )
        }
    }
}
