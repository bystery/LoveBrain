package com.lovebrain.app.ui.matrix

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import com.lovebrain.app.core.designsystem.LbSettingRow
import com.lovebrain.app.core.designsystem.LbStatus
import com.lovebrain.app.core.designsystem.LbStatusBadge
import com.lovebrain.app.core.designsystem.LbStatusTags
import com.lovebrain.app.core.designsystem.LbTags
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
 * §6.5 那 4 宽 × 3 字 = 12 格的**跑满**：把四颗已经有语义树用例的公共件，从固定在 360 一档
 * 扩到全矩阵，每格照旧量热区、名字、角色、选中态，并且加两句哨兵证明
 * 「字体档与宽度档真的换了」——没有哨兵的矩阵循环，覆盖面是假的（把 `RenderIn` 里的
 * `fontScale` 换成常数，十二格会量出十二个一模一样的读数还全绿）。
 *
 * 挑的是这四颗，各自都已经有语义树用例钉在单格里（本文件只**扩矩阵**，不改那些用例的判据口径）：
 *
 * | 组件 | 已有用例（固定在 360 一档） | 本文件扩到 |
 * |---|---|---|
 * | `LbChip` | `LbChipTest` | 12 格 × 两颗（选/未选），角色 + selected + 热区 + 名字 + 不越槽 |
 * | `LbStatusBadge` | `LbStatusBadgeTest` | 12 格，contentDescription + liveRegion + 不越槽 + 非零尺寸 |
 * | `LbSettingRow` | `LbSettingRowStateTest` | 12 格，整行 + 尾部动作的热区/名字 + 行宽随格子 |
 * | `LbPrimaryButton` | `LbPrimaryButtonStateTest` | 12 格 × Idle/Disabled，热区 + 角色 + 禁用态仍报出来 |
 *
 * 与 `LbAsyncStateTest` 那条同类前例的关系：那一格已经证明 12 格循环在 JVM 上跑得起来，
 * 但它没有哨兵——所以它量到的「十二格全过」并不能排除「十二格其实是同一格」。
 * 本文件的两条哨兵（[the_font_scale_axis_really_changes_the_measured_box] /
 * [the_width_axis_really_changes_the_measured_box]）补的就是这一句。
 *
 * ⚠ `@Config` 的窗口必须是 **600dp 宽**那一串（照抄 `LbChipTest` / `LbSettingRowStateTest`）：
 * `UiMatrix.RenderIn` 给子树的是「被要求在 widthDp 内摆好」的约束，窗口只有 360dp 时
 * 600dp 那一格会被窗口夹住，宽度档就白换了——这条正是 [the_width_axis_really_changes_the_measured_box]
 * 会当场红的那种坏法（哨兵红在仪器上，比绿在假象上有价值）。
 *
 * ⚠ 裁切类判据只判几何：`maxLines + Ellipsis` 时语义树仍报完整原串（坑表里那条），
 * 所以这一族只用 `boundsInRoot` 量出来的几何，不拿文本反推「有没有被裁」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiMatrixFullSweepTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val deviceDensity: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(deviceDensity) }

    /** 换格子靠 hoisted 状态：本仓库的仪器要求一个用例只 `setContent` 一次 */
    private val cell: MutableState<UiMatrix> = mutableStateOf(UiMatrix.FULL.first())

    /**
     * 一次 setContent，十二格轮流挂上去。
     *
     * 每格都真的等空闲（`runOnIdle` 改状态 + `waitForIdle`），否则读到的是上一格的树。
     */
    private fun sweep(mount: @Composable () -> Unit, body: (UiMatrix) -> Unit) {
        rule.setContent {
            cell.value.RenderIn(deviceDensity) { mount() }
        }
        UiMatrix.FULL.forEach { matrix ->
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            body(matrix)
        }
    }

    /** 每一格都必须待在 [UiMatrix] 给的那条槽里：越界就是"这格没被要求摆好" */
    private fun assertInsideTheSlot(where: String, matrix: UiMatrix, target: SemanticsProbe.Target) {
        assertTrue(
            "$where 在 ${matrix.id} 这一格超出了 ${matrix.widthDp}dp 的槽：" +
                "左 ${target.leftDp.toInt()}dp + 宽 ${target.widthDp.toInt()}dp = " +
                "${(target.leftDp + target.widthDp).toInt()}dp（${target.describe()}）。" +
                "§6.5 要的正是『这一档宽度里摆得下』，越界等于没测",
            target.leftDp + target.widthDp <= matrix.widthDp + 0.5f
        )
    }

    /** 十二格必须各量到一次：少一格就是循环被谁悄悄短路了 */
    private fun assertTwelveCells(ids: List<String>) {
        assertEquals(
            "矩阵只量到 ${ids.size} 格（应为 ${UiMatrix.FULL.size}）：$ids —— 覆盖面不能靠循环写法自称",
            UiMatrix.FULL.map { it.id }, ids
        )
    }

    // ═══════════════════ 四颗组件：每格照旧口径 ═══════════════════

    /**
     * `LbChip`：单选两颗（一选一无）在 12 格里都必须
     * ① 是 Tab、② 只有选中的那颗报 selected=true、③ 两轴都到下限、④ 说得出名字、⑤ 不越槽。
     *
     * 这把尺有牙的证据（本文件跑过的注入，原话抄在交付的反证表里）：
     * 把夹具那颗芯片换成 `touchFloor = false`（就是 `LbChipStyle` 里那条"下限不垫"的档位），
     * 最窄 + 标准字那一格当场红在"有 N/M 个可交互节点小于 48dp"。
     */
    @Test
    fun the_chip_family_keeps_its_contract_in_all_twelve_cells() {
        val widths = LinkedHashMap<String, Float>()
        sweep(
            mount = {
                Column {
                    ChipFor(label = "CHIP_A", selected = false)
                    ChipFor(label = "CHIP_B", selected = true)
                }
            }
        ) { matrix ->
            val targets = probe.assertAllActionableMeetTouchFloor(rule, "芯片", "（${matrix.id}）")
            probe.assertAllActionableLabeled(rule, "芯片", "（${matrix.id}）")
            assertEquals("两颗芯片都该在树里（${matrix.id}）：" + targets.joinToString { it.describe() }, 2, targets.size)
            targets.forEach {
                assertEquals("${it.label} 的角色在 ${matrix.id} 变了：" + it.describe(), "Tab", it.role)
                assertInsideTheSlot("芯片", matrix, it)
            }
            val on = targets.filter { it.selected == true }
            assertEquals("${matrix.id} 该恰好一颗报选中：" + targets.joinToString { it.describe() }, 1, on.size)
            assertEquals("报选中的必须是 CHIP_B：" + on.single().describe(), "CHIP_B", on.single().label)
            assertEquals(
                "另一颗必须报未选中（把 selected 写死在这一格会红）：" +
                    targets.joinToString { it.describe() },
                1, targets.count { it.selected == false }
            )
            widths[matrix.id] = on.single().widthDp
        }
        assertTwelveCells(widths.keys.toList())
    }

    /** 芯片夹具：对勾关掉，免得选中那颗的名字变成「✓ CHIP_B」把这一格的锚点带跑（底色与字色一档没动） */
    @Composable
    private fun ChipFor(label: String, selected: Boolean) {
        LbChip(
            label = label,
            selected = selected,
            onClick = {},
            interaction = LbChipInteraction.Single,
            style = LbChipStyles.filled.copy(markSelectedWithCheck = false)
        )
    }

    /**
     * `LbStatusBadge`：§6.5 第②栏那三件事（说得出、变了会补播、不越槽）在 12 格里都得成立。
     *
     * 这一颗同时是**底色配比**那把像素尺的对象（见 `RenderedPixelContrastTest`）：
     * 语义树读得出它念什么，读不出它是什么底——所以这里只钉语义与几何那一半。
     */
    @Test
    fun the_status_badge_announces_itself_in_all_twelve_cells() {
        val expected = app.getString(LbStatus.Running.labelRes)
        val widths = LinkedHashMap<String, Float>()
        sweep(
            mount = { LbStatusBadge(status = LbStatus.Running) }
        ) { matrix ->
            val node = rule.onNodeWithTag(LbStatusTags.BADGE).fetchSemanticsNode(
                "状态胶囊不在语义树上（tag=${LbStatusTags.BADGE}，格子=${matrix.id}）"
            )
            val target = probe.of(node)
            assertEquals(
                "${matrix.id} 这一格胶囊没把状态词带进 contentDescription，读屏只会念一段未知文本",
                listOf(expected),
                node.config.getOrNull(SemanticsProperties.ContentDescription)
            )
            assertEquals(
                "${matrix.id} 这一格状态没有 liveRegion=Polite：从「运行中」变成「已隐藏」时 TalkBack 不补播",
                LiveRegionMode.Polite,
                node.config.getOrNull(SemanticsProperties.LiveRegion)
            )
            assertInsideTheSlot("状态胶囊", matrix, target)
            assertTrue(
                "${matrix.id} 胶囊量出 0 尺寸（${target.describe()}）——这格根本没画出来",
                target.widthDp > 0f && target.heightDp > 0f
            )
            widths[matrix.id] = target.widthDp
        }
        assertTwelveCells(widths.keys.toList())
    }

    /**
     * `LbSettingRow`：整行 + 尾部那颗动作，12 格里两轴都要到下限、都要说得出自己、都不越槽。
     *
     * 尾部那颗是这一族的历史欠账（旧值 `heightIn(min = 32dp)`，由 `LbSettingRowStateTest` 量出来过），
     * 那一格钉的是 360 一档；这里钉的是「换了字号、换了宽度也还是 48」。
     */
    @Test
    fun the_settings_row_and_its_trailing_action_survive_the_whole_matrix() {
        val rowWidth = LinkedHashMap<String, Float>()
        sweep(
            mount = {
                LbSettingRow(
                    title = "ROW_TITLE",
                    subtitle = "ROW_SUB",
                    trailingText = "GO",
                    onTrailingClick = {},
                    onClick = {}
                )
            }
        ) { matrix ->
            val targets = probe.assertAllActionableMeetTouchFloor(rule, "设置行", "（${matrix.id}）")
            probe.assertAllActionableLabeled(rule, "设置行", "（${matrix.id}）")
            assertTrue(
                "${matrix.id} 整行 + 尾部动作都该量到：" + targets.joinToString { it.describe() },
                targets.size >= 2
            )
            targets.forEach { assertInsideTheSlot("设置行", matrix, it) }
            val row = probe.of(rule.onNodeWithTag(LbTags.SETTING_ROW).fetchSemanticsNode())
            rowWidth[matrix.id] = row.widthDp
            assertInsideTheSlot("设置行", matrix, row)
        }
        assertTwelveCells(rowWidth.keys.toList())
    }

    /**
     * `LbPrimaryButton`：Idle 与 Disabled 两态 × 12 格。
     *
     * 判的是 §6.5 里最容易被大字号挤坏的那一颗：唯一主动作。禁用态必须**仍在原位**并报出
     * Disabled（`SemanticsProbe` 的口径：`clickable(enabled = false)` 仍是操作入口），
     * 「禁用就等于不画」在这一族是红线。
     * 只测 Idle/Disabled 两态：Loading 带无限脉冲动画，`waitForIdle` 等不到空闲，
     * 那一态的形状由 `LbPrimaryButtonStateTest` 在 360 一档钉着，矩阵这一格不重开那条通道。
     */
    @Test
    fun the_primary_action_keeps_its_floor_and_its_disabled_voice_in_all_twelve_cells() {
        val state = mutableStateOf(LbButtonState.Idle)
        val widths = LinkedHashMap<String, Float>()
        rule.setContent {
            cell.value.RenderIn(deviceDensity) {
                LbPrimaryButton(
                    state = state.value,
                    label = "GO_" + state.value.name,
                    onClick = {}
                )
            }
        }
        UiMatrix.FULL.forEach { matrix ->
            listOf(LbButtonState.Idle, LbButtonState.Disabled).forEach { next ->
                rule.runOnIdle {
                    cell.value = matrix
                    state.value = next
                }
                rule.waitForIdle()
                val targets = probe.assertAllActionableMeetTouchFloor(rule, "主动作", "（${matrix.id}·${next.name}）")
                probe.assertAllActionableLabeled(rule, "主动作", "（${matrix.id}·${next.name}）")
                assertEquals(
                    "${matrix.id}·${next.name} 只该有一颗可点的主动作：" +
                        targets.joinToString { it.describe() },
                    1, targets.size
                )
                val button = targets.single()
                assertEquals("角色在 ${matrix.id}·${next.name} 丢了：" + button.describe(), "Button", button.role)
                assertEquals("禁用态不许把按钮从树上消失", next == LbButtonState.Disabled, button.disabled)
                assertInsideTheSlot("主动作", matrix, button)
                if (next == LbButtonState.Idle) widths[matrix.id] = button.widthDp
            }
        }
        assertTwelveCells(widths.keys.toList())
    }

    // ═══════════════════ 哨兵①：字号这一档必须真的动了 ═══════════════════

    /**
     * 同一颗组件、同一个宽度档：**2.0 字下的宽度必须大于 1.0 字下**（四档宽度各验一遍）。
     *
     * 这一格是整个矩阵的电源指示器。它红的样子有两种，都该红：
     * - `UiMatrix.RenderIn` 把 fontScale 写成常数（`Density(density, 1f)`）⇒ 四颗全平 ⇒ 红；
     * - 组件被谁钉死了宽度（`widthIn(max = …)`）⇒ 那颗红 ⇒ 红。
     * 没有这一格，「12 格全跑」这句话只证明了循环写了 12 次。
     *
     * 行（`LbSettingRow`）是 fillMaxWidth 的，宽度天生不随字号动，所以它的字号档用**高度**验。
     */
    @Test
    fun the_font_scale_axis_really_changes_the_measured_box() {
        val readings = sweepFourAnchors()
        assertPinnedControlStays(readings, "字号档")
        val rows = LinkedHashMap<String, String>()
        UiMatrix.WIDTHS_DP.forEach { width ->
            val one = UiMatrix(widthDp = width, fontScale = 1.0f).id
            val two = UiMatrix(widthDp = width, fontScale = 2.0f).id
            listOf(
                "芯片" to (readings.getValue(one).chip.widthDp to readings.getValue(two).chip.widthDp),
                "徽标" to (readings.getValue(one).badge.widthDp to readings.getValue(two).badge.widthDp),
                "主动作" to (readings.getValue(one).button.widthDp to readings.getValue(two).button.widthDp),
                "设置行(高度)" to (readings.getValue(one).row.heightDp to readings.getValue(two).row.heightDp)
            ).forEach { (name, pair) ->
                rows["$width/$name"] = "%.1f → %.1f".format(pair.first, pair.second)
                assertTrue(
                    "$name 在 ${width}dp 这一档宽度里：font 1.0 量到 ${pair.first.toInt()}dp，" +
                        "font 2.0 只量到 ${pair.second.toInt()}dp——字号换了而盒子没换，" +
                        "本文件其余十二格的『全矩阵』是假的。全部读数：$rows",
                    pair.second - pair.first >= 8f
                )
            }
        }
    }

    // ═══════════════════ 哨兵②：宽度这一档必须真的动了 ═══════════════════

    /**
     * 同一把字号（1.0）下，`LbSettingRow` 的宽度必须跟着格子从 320dp 涨到 600dp。
     *
     * 存在的理由：窗口比 600dp 窄、或 `RenderIn` 把 widthDp 写成常数时，四档宽度会量出
     * 同一个数——那时「4 宽」这一栏一个字都没测过，而循环照样绿。
     * 顺带把「每一格都不越槽、且 fillMaxWidth 正好铺满那一档的槽」再说一遍。
     */
    @Test
    fun the_width_axis_really_changes_the_measured_box() {
        val readings = sweepFourAnchors()
        assertPinnedControlStays(readings, "宽度档")
        val narrowPinned = readings.getValue(UiMatrix(widthDp = 320).id).pinned
        val widePinned = readings.getValue(UiMatrix(widthDp = 600).id).pinned
        assertTrue(
            "宽度档的零位不对：钉死尺寸的控制组从 320dp 档到 600dp 档宽度变成了 " +
                "${narrowPinned.widthDp} → ${widePinned.widthDp}。它本该一动不动——" +
                "它跟着格子动，说明这把尺量的不是组件而是噪声",
            kotlin.math.abs(widePinned.widthDp - narrowPinned.widthDp) < 1f
        )
        val narrow = readings.getValue(UiMatrix(widthDp = 320).id).row
        val wide = readings.getValue(UiMatrix(widthDp = 600).id).row
        assertTrue(
            "同一把字号（1.0）下，设置行从 320dp 档到 600dp 档只涨了 " +
                "${(wide.widthDp - narrow.widthDp).toInt()}dp（${narrow.widthDp.toInt()} → " +
                "${wide.widthDp.toInt()}）——宽度这一档没换：要么窗口夹住了槽，" +
                "要么 RenderIn 把 widthDp 写成了常数。整行本应正好铺满那一档的槽。",
            wide.widthDp - narrow.widthDp >= 200f
        )
        UiMatrix.FULL.forEach { matrix ->
            val row = readings.getValue(matrix.id).row
            assertTrue(
                "${matrix.id}：设置行是 fillMaxWidth 的，宽度该正好等于槽宽 ${matrix.widthDp}dp，" +
                    "实到 ${row.widthDp.toInt()}dp（${row.describe()}）",
                kotlin.math.abs(row.widthDp - matrix.widthDp.toFloat()) <= 1f
            )
        }
    }

    /** 一次 12 格，把四颗锚点 + 一颗**钉死的控制组**各自的读数都抄下来（两颗哨兵共用，避免各写一遍循环而口径漂） */
    private data class Anchors(
        val chip: SemanticsProbe.Target,
        val badge: SemanticsProbe.Target,
        val row: SemanticsProbe.Target,
        val button: SemanticsProbe.Target,
        val pinned: SemanticsProbe.Target
    )

    /**
     * 四颗锚点挂在同一棵树上（一个用例只许 setContent 一次），跑满 12 格。
     *
     * 外面那层带 tag 的 Box 只是锚点：它不带语义、按内容排，边界与被测节点重合。
     * 徽标/芯片用各自组件自己的 tag 会撞名（同一种 tag 一颗以上），所以只有它们套壳。
     *
     * 第六颗 [ANCHOR_PINNED] 是**哨兵的哨兵**：一颗尺寸写死的盒子，字号与槽宽怎么换它都不该动。
     * 两条哨兵用它把"零位"钉住——如果连它都在两档之间"变了"，那句 `≥8dp` 就是噪声而不是判据；
     * 反过来，某颗真组件被钉死宽度时，它给出的读数正好落在这颗控制组的位置上，哨兵当场红。
     */
    private fun sweepFourAnchors(): LinkedHashMap<String, Anchors> {
        val out = LinkedHashMap<String, Anchors>()
        rule.setContent {
            cell.value.RenderIn(deviceDensity) {
                Column {
                    Box(Modifier.testTag(ANCHOR_CHIP)) { ChipFor(label = "CHIP_A", selected = true) }
                    Box(Modifier.testTag(ANCHOR_BADGE)) { LbStatusBadge(status = LbStatus.Running) }
                    LbSettingRow(
                        title = "ROW_TITLE",
                        subtitle = "ROW_SUB",
                        trailingText = "GO",
                        onTrailingClick = {},
                        onClick = {}
                    )
                    Box(Modifier.testTag(ANCHOR_BUTTON)) {
                        LbPrimaryButton(state = LbButtonState.Idle, label = "GO", onClick = {})
                    }
                    Box(Modifier.testTag(ANCHOR_PINNED).size(120.dp, 40.dp))
                }
            }
        }
        UiMatrix.FULL.forEach { matrix ->
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            out[matrix.id] = Anchors(
                chip = probe.of(rule.onNodeWithTag(ANCHOR_CHIP).fetchSemanticsNode()),
                badge = probe.of(rule.onNodeWithTag(ANCHOR_BADGE).fetchSemanticsNode()),
                row = probe.of(rule.onNodeWithTag(LbTags.SETTING_ROW).fetchSemanticsNode()),
                button = probe.of(rule.onNodeWithTag(ANCHOR_BUTTON).fetchSemanticsNode()),
                pinned = probe.of(rule.onNodeWithTag(ANCHOR_PINNED).fetchSemanticsNode())
            )
        }
        assertTwelveCells(out.keys.toList())
        UiMatrix.FULL.forEach { matrix ->
            val read = out.getValue(matrix.id)
            println(
                "G6-MATRIX|${matrix.id}|芯片宽=${"%.1f".format(read.chip.widthDp)} " +
                    "徽标宽=${"%.1f".format(read.badge.widthDp)} 主动作宽=${"%.1f".format(read.button.widthDp)} " +
                    "行宽=${"%.1f".format(read.row.widthDp)} 行高=${"%.1f".format(read.row.heightDp)} " +
                    "钉死控制组=${"%.1f".format(read.pinned.widthDp)}x${"%.1f".format(read.pinned.heightDp)}"
            )
        }
        return out
    }

    /** 那颗钉死的控制组在两档字号之间**不该**动；动了就说明上面那句 `≥8dp` 是噪声 */
    private fun assertPinnedControlStays(readings: LinkedHashMap<String, Anchors>, where: String) {
        UiMatrix.WIDTHS_DP.forEach { width ->
            val one = readings.getValue(UiMatrix(widthDp = width, fontScale = 1.0f).id).pinned
            val two = readings.getValue(UiMatrix(widthDp = width, fontScale = 2.0f).id).pinned
            listOf(
                "宽" to (one.widthDp to two.widthDp),
                "高" to (one.heightDp to two.heightDp)
            ).forEach { (axis, pair) ->
                assertTrue(
                    "$where 的零位不对：钉死尺寸的控制组在 ${width}dp 这一档里，" +
                        "font 1.0 量到 ${pair.first}、font 2.0 量到 ${pair.second}（$axis 动了 " +
                        "${kotlin.math.abs(pair.second - pair.first)}dp）。" +
                        "它本该一个字都不动——它动了，说明这把尺把噪声当成了字号档的变化，" +
                        "上面那句『组件必须涨 ≥8dp』就不是判据",
                    kotlin.math.abs(pair.second - pair.first) < 1f
                )
            }
        }
    }

    private companion object {
        const val ANCHOR_CHIP = "matrix_anchor_chip"
        const val ANCHOR_BADGE = "matrix_anchor_badge"
        const val ANCHOR_BUTTON = "matrix_anchor_button"
        const val ANCHOR_PINNED = "matrix_anchor_pinned_control"
    }
}
