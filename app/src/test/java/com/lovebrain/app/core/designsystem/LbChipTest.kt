package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `LbChip` 的语义合同——这一颗组件是芯片族的新所有者，所以它自己先要能被量出来。
 *
 * 为什么不能只靠各页面的守卫：归并之前那七处各自画链，角色有四样
 * （`Tab`、`Checkbox`、`Button`、干脆没声明），"选中"有三样（`Selected`、
 * `ToggleableState`、只有底色加一个 `✓`）。页面那一侧的格子判的是"这一屏那颗
 * 现在对不对"，判不了"这一族的规范是哪一样"。这一格把规范钉在设计系统里：
 *
 * | 档位 | 角色 | 选中怎么说 |
 * |---|---|---|
 * | `Single` | Tab | `Selected`，两头都量（真的那颗 true、其余 false） |
 * | `Multi` | Checkbox | `ToggleableState` On/Off |
 * | `Action` | Button | **不播报**（`selected == null`：它没有"在哪一格"这件事） |
 * | `enabled = false` | 原样 | 仍在原位、仍有名字、多报一个 Disabled |
 *
 * 下限也按语义树量（`boundsInRoot`），不读源码：这一族的教训恰恰是
 * "外层盒子变大了、动作仍挂在子里面"那种写法看着像修过了。
 *
 * ⚠ 标签一律用 ASCII 哨兵（与 `LbDialogTest` 同一理由）：这一格判的是形状与语义，
 * 拿中文当锚点会把"资源改名"这种无关变更算成组件坏了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbChipTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private fun targets(what: String) = probe.actionableTargets(rule, what)

    private fun mountSingleGroup(current: String, pick: (String) -> Unit) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                // 两颗要各占一格：`RenderIn` 给的是 Box 槽位，不平铺子节点
                Column {
                    listOf("CHIP_A", "CHIP_B").forEach { name ->
                        LbChip(
                            label = name,
                            selected = name == current,
                            onClick = { pick(name) },
                            interaction = LbChipInteraction.Single,
                            style = LbChipStyles.filled
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** `Single` = Tab + `Selected`，而且两头都要报：写死 true 与写死 false 都得红 */
    @Test
    fun `a single-select chip is a tab that reports which one is selected`() {
        var picked: String? = null
        mountSingleGroup(current = "CHIP_B") { picked = it }

        val chips = targets("单选芯片")
        assertEquals("两颗都该在树里：" + chips.joinToString { it.describe() }, 2, chips.size)
        chips.forEach {
            assertEquals("${it.label} 的角色：" + it.describe(), "Tab", it.role)
            assertTrue("${it.label} 读得出名字", it.labeled)
        }
        val on = chips.filter { it.selected == true }
        assertEquals("该恰好一颗报选中：" + chips.joinToString { it.describe() }, 1, on.size)
        assertEquals("报选中的就是 current 那一颗，对勾也一起画", "✓ CHIP_B", on.single().label)
        val off = chips.filter { it.selected == false }
        assertEquals("另一颗必须报未选中（把 selected 写死的实现红在这里）", 1, off.size)
        assertEquals("报未选中的那颗应是 CHIP_A", "CHIP_A", off.single().label)

        rule.onAllNodes(hasText("CHIP_A"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点击要落到这一颗自己的 onClick", "CHIP_A", picked)
    }

    /** `Multi` = Checkbox + `ToggleableState`；对勾留给眼睛，规范位在语义树里。
     *  直接挂两颗 `LbChip(Multi)`：`LbChipGroup` 那颗换行容器生产零引用已删（旧账 Q05），
     *  但多选语义的规范证人不能陪葬——这里就是它的直接形状。 */
    @Test
    fun `a multi-select chip is a checkbox whose toggle state follows the value`() {
        val selected: MutableState<Set<String>> = mutableStateOf(setOf("CHIP_A"))
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column {
                    listOf("CHIP_A", "CHIP_B").forEach { name ->
                        LbChip(
                            label = name,
                            selected = name in selected.value,
                            onClick = {
                                selected.value = if (name in selected.value) selected.value - name
                                else selected.value + name
                            },
                            interaction = LbChipInteraction.Multi
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val chips = targets("多选芯片")
        assertEquals("换行容器里两颗都该在树里：" + chips.joinToString { it.describe() }, 2, chips.size)
        chips.forEach {
            assertEquals("${it.label} 的角色：" + it.describe(), "Checkbox", it.role)
            assertTrue("${it.label} 该是 toggle 语义：" + it.describe(), it.isToggle)
        }
        assertEquals(
            "起始只有 CHIP_A 报 On：" + chips.joinToString { it.describe() },
            listOf("✓ CHIP_A"), chips.filter { it.toggleOn }.map { it.label }
        )

        rule.onAllNodes(hasText("CHIP_B"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        val after = targets("多选芯片·点后")
        assertEquals(
            "点过之后两颗都该报 On（Off → On 这一跳必须看得见）：" +
                after.joinToString { it.describe() },
            2, after.count { it.toggleOn }
        )
    }

    /** `Action` 只报按钮：它没有"在哪一格"，多一槽 `Selected` 就是给读屏加噪声 */
    @Test
    fun `an action chip is a button that invents no selection state`() {
        var clicks = 0
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbChip(
                    label = "CHIP_ACTION",
                    onClick = { clicks++ },
                    interaction = LbChipInteraction.Action,
                    style = LbChipStyles.neutral
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val chip = targets("动作芯片").single()
        assertEquals("动作芯片报 Button：" + chip.describe(), "Button", chip.role)
        assertEquals("不该替这颗造一个选中态：" + chip.describe(), null, chip.selected)
        assertTrue("也不该有 toggle 槽：" + chip.describe(), !chip.isToggle)
        rule.onAllNodes(hasText("CHIP_ACTION"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点下去得真的投递一次", 1, clicks)
    }

    /** 禁用的那颗**还在原位**：灰着、说得出自己、多报一个 Disabled，而不是从树上消失 */
    @Test
    fun `a disabled chip stays present and announces itself disabled`() {
        var clicks = 0
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbChip(
                    label = "CHIP_OFF",
                    enabled = false,
                    onClick = { clicks++ },
                    interaction = LbChipInteraction.Single,
                    style = LbChipStyles.filled
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val chip = targets("禁用芯片").single()
        assertTrue("禁用态必须报出来：" + chip.describe(), chip.disabled)
        assertTrue("禁用态仍然要有名字：" + chip.describe(), chip.labeled)
        assertEquals("角色不因为禁用就消失：" + chip.describe(), "Tab", chip.role)
        rule.onAllNodes(hasText("CHIP_OFF"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("禁用态点下去不该投递", 0, clicks)
    }

    /**
     * 说过要垫下限的那几种形状，可点那颗**自己**就得过下限。
     *
     * 三种形状都要量：单层实心（`filled`）、热区分两层（`layeredTouch`）、
     * 以及换行容器里那一颗（上一格已经量过它的角色，这里量它的盒子）。
     * `touchFloor = false` 那一档**故意不在这里量**：它是两处既有欠账
     * （持续意图那颗胶囊、有效期那一排）的形状，欠着什么由 `SuggestIntentChipTest`
     * 那一格说明，别让它躲在这一格里被"全达标"读过去。
     */
    @Test
    fun `every shape that claims the floor actually fills it`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column {
                    LbChip(
                        label = "FLAT_A",
                        selected = true,
                        onClick = {},
                        interaction = LbChipInteraction.Multi,
                        style = LbChipStyles.filled
                    )
                    LbChip(
                        label = "LAYERED_A",
                        selected = true,
                        onClick = {},
                        interaction = LbChipInteraction.Single,
                        style = LbChipStyles.filled.copy(
                            layeredTouch = true,
                            pillHeight = 28.dp
                        )
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        val chips = targets("下限")
        assertEquals("两颗都该在树里：" + chips.joinToString { it.describe() }, 2, chips.size)
        chips.forEach {
            assertTrue(
                "${it.label} 的热区不到 ${probe.floorDp.toInt()}dp：" + it.describe(),
                !it.tooSmall(probe.floorDp)
            )
        }
        // 两层那一颗：胶囊自己 28dp，可点那颗仍是下限那颗数——量到的必须是后者
        val layered = chips.first { it.label == "✓ LAYERED_A" }
        assertEquals("两层形状里可点那颗仍垫到下限", 48f, layered.heightDp, 0.6f)
        val flat = chips.first { it.label == "✓ FLAT_A" }
        assertEquals("单层那一颗的胶囊自己就铺到下限", 48f, flat.heightDp, 0.6f)
    }

    private val SemanticsProbe.Target.toggleOn: Boolean
        get() = isToggle && toggleState?.contains("On", ignoreCase = true) == true &&
            toggleState != "Indeterminate"
}
