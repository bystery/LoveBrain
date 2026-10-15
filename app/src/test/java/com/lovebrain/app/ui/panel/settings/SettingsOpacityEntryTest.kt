package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页那颗「透明度」滑杆：显示轴读数 + 原型那一族的形状 + 生命周期。
 *
 * 这一族管四件事，各挡一种坏形状：
 * · **读数住在显示轴**（0% = 完全不透明 / 40% = 正中 / 80% = 这一族允许的最透），三档各钉一次，
 *   钉的是屏上那一句、滑杆报给语义树的进度读数、读屏念的那一句——三处必须同一个来源；
 * · **形状照原型**（`input[type=range]`：轨 4dp、球 15dp、整根最宽 125dp、无刻度点）——
 *   这一格量的是语义树读得到的那一半：整根宽度上限 125dp、它不占满整行，
 *   外加行根那颗 `settings-row-opacity` 仍走族里的行骨架（≥48）。
 *   轨厚与球径是**画出来的**，语义树里没有对应读数 ⇒ 那一半归截图基线
 *   `ui/visual/SettingsPageVisualBaselineTest`（席 F）；这里不拿"读不到就当过"糊弄过去；
 * · **刻度吸附**：拖动连续、松手吸附到 [PanelBackdropOpacity.steps] 那一档，
 *   屏上读数与那一笔 commit 同一拍（`releasing between two ticks snaps and the readout matches the commit`）；
 * · **非法/越界回落**：宿主交来 180 / 12 / -40 / 62 这样的脏数，屏上不许出现越界或不在格上的读数。
 *
 * ⚠ 落盘那把轴一寸没动：两条回调交回宿主的仍是 20..100 那一串**不透明度**刻度（每一个数都还落在
 * 那把尺的合法刻度里），所以 Service 侧、面板那两层底色、供出去给消息区大面积底的读数与悬浮球
 * 读的还是同一颗数。
 * 换算只走那一对镜像口，旧形状（把宿主交来的不透明度直接标成"透明度"）会红在
 * "0% 那一颗找不到 / 100% 还在屏上"这两句上。
 *
 * 文案锚点一律 `getString` 现取（这台机器的环境解析出来是英文，写死中文会假红）。
 * 每个用例只 `setContent` 一次（同一个用例里第二次挂机会被框架当场拒掉，那一段所有断言
 * 从未执行过——要换输入就在同一次挂载里 `runOnIdle` 翻那颗状态）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsOpacityEntryTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** 手势坐标是设备像素，而让开的那一截按 dp 写：换算只走这一颗（与 `RenderIn` 交给组合的那一把同一来源） */
    private val density: Float get() = ctx.resources.displayMetrics.density

    private val rowLabel: String get() = ctx.getString(R.string.settings_opacity_label)

    private fun percentText(percent: Int): String =
        ctx.getString(R.string.settings_opacity_percent, percent)

    /** 显示轴的两端从那一把尺现算，不在判据里抄 0 / 80 这两个数 */
    private val opaqueEnd: Int get() = PanelBackdropOpacity.transparencyOf(PanelBackdropOpacity.MAX_PERCENT)
    private val transparentEnd: Int get() = PanelBackdropOpacity.transparencyOf(PanelBackdropOpacity.MIN_PERCENT)

    /** 显示轴的中点（= 尺上居中的那一格）：球此刻正坐在节点中心，小步拖拽的起手就按在这里 */
    private val midTransparency: Int get() = (opaqueEnd + transparentEnd) / 2

    /** 原型那一根的宽度上限（生产的 `SettingsSliderDimens` 里有同名一颗；判据按原型那个数写，不借被测那一侧的表） */
    private val sliderMaxWidthDp = 125f

    /** 行骨架那一条版式下限：与 `AppDimens.TOUCH_TARGET_MIN_DP` 同数（同理，判据写合同那个数） */
    private val rowMinHeightDp = 48f

    /**
     * 上一次 [mount] 里"最后一帧 preview 到来时已经收到几笔 commit"。
     * 由 preview 那一句当场记下——注入一结束指针对测试线程就不可见了，读树读不到"拖动中途"，
     * 这一颗才是"拖动期间一笔 commit 都不来"的凭据。
     */
    private var commitsSeenAtLastPreview = 0

    /**
     * 挂一扇宿主。两种接法都在这格有用，区别就是"吸附到底该不该由这一行自己做"：
     * · `echoPreview = true`——拖动每帧把值写回 `host.value`，与真实宿主一致
     *   （`LoveBrainPanelScreen` 把 `opacityPercent` 接到 `surface.backdropPercent`，
     *   而 preview 那一句写的正是同一颗）。这一档量方向感与"松手那一拍读数不弹回"。
     * · `echoPreview = false`——只有 commit 写回（真实宿主在 commit 那一句里也是同一颗）。
     *   这一档把"屏上吸附后的那一格"**全部**压给这一行自己：拖动期间参数一动不动，
     *   松手后屏上若还念着没吸附的原始读数，就是这一行漏了吸附，不会由 echo 替它圆过去。
     * · `raw = true`——宿主不预先钳位，把交来的数原样塞进参数，判的就是这一行自己那一截回落。
     */
    private fun mount(
        startPercent: Int,
        previews: MutableList<Int> = mutableListOf(),
        commits: MutableList<Int> = mutableListOf(),
        echoPreview: Boolean = true,
        raw: Boolean = false
    ): MutableState<Int> {
        val host = mutableStateOf(if (raw) startPercent else PanelBackdropOpacity.snapPercent(startPercent))
        commitsSeenAtLastPreview = 0
        rule.setContent {
            UiMatrix(ROW_WIDTH_DP).RenderIn(LocalDensity.current.density) {
                SettingsOpacityEntry(
                    opacityPercent = host.value,
                    onOpacityPreview = { percent ->
                        previews += percent
                        commitsSeenAtLastPreview = commits.size
                        if (echoPreview) host.value = percent
                    },
                    onOpacityCommit = { percent ->
                        commits += percent
                        host.value = percent
                    }
                )
            }
        }
        rule.waitForIdle()
        return host
    }

    /**
     * 滑杆报给语义树的那一份进度读数。
     *
     * ⚠ 这一格不走"整屏可交互样本"那份尺：那份样本只认 click / toggle / disabled，
     * 而滑杆交出的是进度语义，三种一个都不带 ⇒ 它永远进不了那份样本。这里直接按进度语义定位，
     * 并且要求**恰有一颗**：读到 0 颗就是滑杆被撤成一句文案（或量具瞎），两种都不许绿。
     * 自定义 thumb/track 只换画法、不换语义 ⇒ 这三处读数仍然该由 Material 那颗交出。
     */
    private fun sliderProgress(): ProgressBarRangeInfo {
        val nodes = rule.onAllNodes(
            SemanticsMatcher("progressBarRangeInfo") {
                it.config.contains(SemanticsProperties.ProgressBarRangeInfo)
            }
        ).fetchSemanticsNodes()
        assertEquals(
            "屏上必须恰有一颗带进度语义的节点（就是那颗滑杆），实到 ${nodes.size} 颗",
            1, nodes.size
        )
        return nodes.single().config[SemanticsProperties.ProgressBarRangeInfo]
    }

    /**
     * 屏上此刻那颗「NN%」读数（恰有一处是合同：多一处就是有人又画了第二份数字）。
     *
     * 定位走 [SemanticsMatcher] 而不是 `hasText(Regex)`：本文件里唯一被验过的按属性定位的写法就是
     * 前者（`sliderProgress` 同一颗），而这一格要的是"把所有带文字的节点捞出来再按形状筛"，
     * 不需要匹配器认正则。
     */
    private fun onScreenPercents(): List<Int> = rule.onAllNodes(
        SemanticsMatcher("hasText") { it.config.contains(SemanticsProperties.Text) }
    )
        .fetchSemanticsNodes()
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        .mapNotNull { PERCENT_TEXT.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        .also { assertEquals("屏上那颗百分比读数必须恰有一处，实到 $it", 1, it.size) }

    private fun dp(px: Float): Float = px / density

    /** 三档共用的一把量法：屏上那个数、滑杆画在哪一格、读屏念的那一句，三处必须同一个来源 */
    private fun assertShownTransparency(storedOpacityPercent: Int, wantTransparency: Int) {
        mount(storedOpacityPercent)
        // 显示轴上那一个数在屏上
        rule.onAllNodes(hasText(percentText(wantTransparency))).assertCountEquals(1)
        // 把落盘轴直接当显示轴印出来的那种写法（100 / 80 / 60 上屏）不许还挂在屏上
        rule.onAllNodes(hasText(percentText(storedOpacityPercent))).assertCountEquals(0)
        val info = sliderProgress()
        assertEquals(
            "滑杆画出来的那一格必须是透明度 $wantTransparency%，实到 ${info.current}",
            wantTransparency.toFloat(), info.current, 0.01f
        )
        assertEquals(
            "滑杆左端必须是完全不透明那一头（透明度 $opaqueEnd%）",
            opaqueEnd.toFloat(), info.range.start, 0.01f
        )
        assertEquals(
            "滑杆右端必须是这一族允许的最透那一头（透明度 $transparentEnd%）",
            transparentEnd.toFloat(), info.range.endInclusive, 0.01f
        )
        // 读屏那颗念的也是显示轴上的数
        val slider = rule.onNode(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG)).fetchSemanticsNode()
        val descriptions = slider.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue(
            "滑杆要自己念出「$rowLabel ${percentText(wantTransparency)}」，实到 $descriptions",
            descriptions.any { it.startsWith(rowLabel) && it.contains(percentText(wantTransparency)) }
        )
    }

    @Test
    fun `a fully opaque backdrop is shown as zero percent transparency`() {
        assertShownTransparency(storedOpacityPercent = 100, wantTransparency = opaqueEnd)
    }

    @Test
    fun `the middle of the scale is shown as forty percent transparency`() {
        assertShownTransparency(storedOpacityPercent = 60, wantTransparency = midTransparency)
    }

    @Test
    fun `the most transparent tier is shown as eighty percent transparency`() {
        assertShownTransparency(storedOpacityPercent = PanelBackdropOpacity.MIN_PERCENT,
            wantTransparency = transparentEnd)
    }

    /**
     * 形状那一半：整根最宽 125dp、不占满整行，行根仍是族里那一颗 ≥48 的行骨架。
     *
     * 10-11 那一版"根本不一样"的两条来源之一就是这个：滑杆吃满 weight 分到的那一整条，
     * 而原型那一根写着 `max-width:125px`。三句都在，各挡一种坏法：
     * · 上限没生效 ⇒ 第二句红；
     * · 拿"整行也压窄"糊过上限 ⇒ 第一句红（行骨架是 `fillMaxWidth`，那一行必须还是满宽）；
     * · 行根自己不长高（把版式下限撤掉）⇒ 第三句红。
     */
    @Test
    fun `the slider keeps the prototype width cap and the row keeps the family shell`() {
        mount(80)
        val rows = rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_ROW_TAG)).fetchSemanticsNodes()
        assertEquals("行根那颗 tag 只许挂在行本身（锚点合同），实到 ${rows.size} 颗", 1, rows.size)
        val sliders = rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG)).fetchSemanticsNodes()
        assertEquals("滑杆必须恰有一颗，实到 ${sliders.size} 颗", 1, sliders.size)
        val row = rows.single().boundsInRoot
        val slider = sliders.single().boundsInRoot
        val rowWidthDp = dp(row.width)
        val sliderWidthDp = dp(slider.width)
        assertTrue(
            "整行没铺满宽度（行骨架该是 fillMaxWidth + 版式下限）：实到 ${rowWidthDp}dp，" +
                "这一档挂的窗口宽 ${ROW_WIDTH_DP}dp",
            rowWidthDp >= ROW_WIDTH_DP - 1f
        )
        assertTrue(
            "滑杆比原型那 ${sliderMaxWidthDp}dp 宽（占满整行就是10-11 那一版那张脸）：实到 ${sliderWidthDp}dp",
            sliderWidthDp <= sliderMaxWidthDp + 1f
        )
        assertTrue(
            "滑杆还吃着整行的宽度：${sliderWidthDp}dp 对整行 ${rowWidthDp}dp——宽度上限那颗没生效",
            sliderWidthDp < rowWidthDp - 1f
        )
        assertTrue(
            "行根短于族里那一条版式下限 ${rowMinHeightDp}dp：实到 ${dp(row.height)}dp",
            dp(row.height) + 0.6f >= rowMinHeightDp
        )
    }

    /**
     * 从滑杆自己那一格里横着走一趟：起手与落点各从边上让开半颗球那一截。
     *
     * 形状说明（为什么不是 `swipe(from = …, to = …)` 一句带过）：本项目锁定的那版 Compose 测试库里
     * `TouchInjectionScope` 只交出 `down` / `moveTo` / `up` 三颗原语，
     * `swipe(from, to)` 那对形参与 `size` 那颗属性都还不在这个接收者上
     * （本仓同族的注入用例走的就是这一形状：`PanelPageSwipePagerTest` 的 `dragOnFiller`、
     * `MessageRowSwipeDeleteTest` 里"1.6.8 只有 down/moveTo/up"那一句）。
     * 尺寸因此**不在注入过程中读树**，而是先把那颗节点的 `boundsInRoot` 量好当局部坐标用
     * （`DragHandleHitBandTest.dragInside` 同一把尺）。
     */
    private fun dragAcrossTheTrack(toRight: Boolean) {
        val nodes = rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG)).fetchSemanticsNodes()
        assertEquals(
            "拖动之前那颗滑杆必须恰有一颗，实到 ${nodes.size}",
            1, nodes.size
        )
        val track = nodes.single().boundsInRoot
        val edge = GRIP_DP * density
        val y = track.height / 2f
        val fromX = if (toRight) edge else track.width - edge
        val toX = if (toRight) track.width - edge else edge
        val steps = 6
        rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG))[0].performTouchInput {
            down(Offset(fromX, y))
            for (i in 1..steps) moveTo(Offset(fromX + (toX - fromX) * i / steps, y))
            up()
        }
    }

    /**
     * 按住球再往右挪一小截（中途不放手、不横跨整根）：落点**故意**不在格上。
     *
     * 起手用 `midTransparency` 那一档 ⇒ 球正坐在显示轴中点，按节点中心就是按在球上；
     * 125dp 这一档里一格（5% 透明度）约 7.8dp，挪 [SMALL_DRAG_DP] dp ⇒ 约十二三格透明度，
     * 原始读数落在 mid+13 附近，离两侧的格都差着两格上下（格子之间相隔 5，量具余量宽），
     * 正是"松手不吸附"会留在屏上的那一种数。
     * 这一段行程也早就越过 touch slop，所以走的是拖动那一条链而不是轻点跳转——两条链落点同一处，
     * 这一格判的是"松手那一拍屏上念什么"，两条都念得对。
     */
    private fun dragTheThumbBy(dragDp: Float) {
        val node = rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG)).fetchSemanticsNodes().single()
        val track = node.boundsInRoot
        val startX = track.width / 2f
        val endX = (startX + dragDp * density)
            .coerceIn(GRIP_DP * density, (track.width - GRIP_DP * density).coerceAtLeast(GRIP_DP * density))
        val y = track.height / 2f
        rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG))[0].performTouchInput {
            down(Offset(startX, y))
            moveTo(Offset((startX + endX) / 2f, y))
            moveTo(Offset(endX, y))
            up()
        }
    }

    /**
     * 拖动手感与显示同向：往右 = 更透明 = **交回宿主的落盘值往下走**，往左反过来。
     * 两头各拖一次，中间钉五件事：方向、落盘那把轴的合法性、**拖动期间一笔 commit 都不来**、
     * 松手只交一笔账、屏上读数与那一笔账互为镜像。
     */
    @Test
    fun `dragging toward the right makes the panel more transparent and still hands over a stored-scale value`() {
        val previews = mutableListOf<Int>()
        val commits = mutableListOf<Int>()
        mount(PanelBackdropOpacity.MAX_PERCENT, previews, commits)

        dragAcrossTheTrack(toRight = true)
        rule.waitForIdle()
        assertTrue("往右拖一个回调都没收到 ⇒ 量具瞎，不是产品没画：$previews", previews.isNotEmpty())
        val rightEnd = previews.last()
        assertTrue(
            "往右必须更透明：交回的不透明度必须从 ${PanelBackdropOpacity.MAX_PERCENT} 往下走，实到 $previews",
            rightEnd < PanelBackdropOpacity.MAX_PERCENT
        )
        // 右端那一拖只要求**推过四分之三**，不要求推到最末一档：区间从 0..40 放开到 0..80 之后，
        // 合成拖动最后那一截落在球自己的半径里（球 15dp、整根 125dp），量具到不了而产品到得了。
        // ⚠ 区间端点本身不在这里钉——它由"挂上最淡那一档屏上就念 80%"那一格与换算口自己的
        //   端点格钉死（`PanelBackdropOpacityTest`）；这一句钉的是**手感方向与行程**：
        //   一颗死的、反的、或只走一小截就停的滑杆，在这里当场红。
        val threeQuarters = PanelBackdropOpacity.MAX_PERCENT -
            (PanelBackdropOpacity.MAX_PERCENT - PanelBackdropOpacity.MIN_PERCENT) * 3 / 4
        assertTrue(
            "往右拖的行程不够：从 ${PanelBackdropOpacity.MAX_PERCENT} 出发至少该推到 $threeQuarters 以下，实到 $rightEnd",
            rightEnd <= threeQuarters
        )
        assertTrue(
            "交回的每一个数都还得是那一把尺上的合法刻度（落盘那把轴一个字没动）：$previews",
            previews.all { it in PanelBackdropOpacity.steps }
        )
        assertEquals("松手只交一笔账", 1, commits.size)
        assertEquals("写盘那一笔就是最后一次预览那一档", rightEnd, commits.single())
        assertEquals(
            "拖动期间一次 commit 都不许来（最后一帧 preview 到来时实到 $commitsSeenAtLastPreview 笔）",
            0, commitsSeenAtLastPreview
        )
        rule.onAllNodes(hasText(percentText(PanelBackdropOpacity.transparencyOf(rightEnd))))
            .assertCountEquals(1)

        // 反向：往左拖回来 = 更不透明 = 落盘值往上走
        previews.clear()
        commits.clear()
        dragAcrossTheTrack(toRight = false)
        rule.waitForIdle()
        assertTrue("往左必须更不透明（往右的反向）：从 $rightEnd 出发，实到 $previews", previews.last() > rightEnd)
        // 左端同一把尺（合成拖动够不到球自己半径里那最后一截，两端对称）：
        // 往回至少要走完这四分之一程，否则"拖回来了"这句话就是假的。
        assertTrue(
            "拖回左端的行程不够：至少该回到 " +
                "${PanelBackdropOpacity.MAX_PERCENT - (PanelBackdropOpacity.MAX_PERCENT - PanelBackdropOpacity.MIN_PERCENT) / 4} 以上，" +
                "实到 ${previews.last()}",
            previews.last() >= PanelBackdropOpacity.MAX_PERCENT -
                (PanelBackdropOpacity.MAX_PERCENT - PanelBackdropOpacity.MIN_PERCENT) / 4
        )
        assertTrue("落盘刻度仍然合法：$previews", previews.all { it in PanelBackdropOpacity.steps })
        assertEquals(1, commits.size)
    }

    /**
     * **拖动连续、松手吸附**，而且屏上那一拍念的就是吸附后的那一档。
     *
     * 这一格用 `echoPreview = false` 那一档宿主：拖动期间参数一动不动，所以"松手后屏上是什么数"
     * 全部由这一行自己交代——没有 echo 替它把没吸附的原始读数圆成格上的数。反例逐条对得上：
     * · 松手不吸附、把拖动期间的原始读数留在屏上 ⇒ 屏上念的是约 mid+8 那种**不在格上**的数，
     *   下面 `% STEP_PERCENT == 0` 那句当场红，滑杆自己的进度读数那句也红；
     * · 吸附吸附错了轴（往不透明度那把轴吸）⇒ 交回的数不在 [PanelBackdropOpacity.steps] 里，红；
     * · 吸附写成"再多交一笔 commit" ⇒ 笔数不是 1，红；
     * · 交回的是显示轴那个数（100 那一侧没换算）⇒ 不在 20..100 的刻度里，红。
     *
     * ⚠ 一条如实写着的边界：把 `steps` 交回 Material（旧形状，中途就量化）在**这两把轴的回调上
     * 读不出差别**（preview 交出去之前已经过换算口那一格量化），所以这一格挡不住"中途量化"的回退。
     * "拖动过程连续"真正的凭据是轨道上没有刻度点、球在松手前停在两格之间——那是画法，语义树里没有
     * 对应读数 ⇒ 归截图基线（席 F）。这一格不假装量到了自己量不到的东西。
     */
    @Test
    fun `releasing between two ticks snaps and the readout matches the commit`() {
        val previews = mutableListOf<Int>()
        val commits = mutableListOf<Int>()
        mount(PanelBackdropOpacity.fromTransparencyPercent(midTransparency), previews, commits,
            echoPreview = false)
        assertEquals("起手屏上就是中点那一格、球坐在节点中心", listOf(midTransparency), onScreenPercents())

        dragTheThumbBy(SMALL_DRAG_DP)
        rule.waitForIdle()

        assertEquals("松手交且只交一笔账", 1, commits.size)
        val committed = commits.single()
        assertTrue(
            "交回的必须是不透明度那把轴上的一格（${PanelBackdropOpacity.MIN_PERCENT}.." +
                "${PanelBackdropOpacity.MAX_PERCENT}、步长 ${PanelBackdropOpacity.STEP_PERCENT}），" +
                "实到 $committed；整段 preview：$previews",
            committed in PanelBackdropOpacity.steps
        )
        val shown = onScreenPercents().single()
        assertEquals(
            "松手那一拍屏上念的必须是吸附后那一档（$committed 镜像回显示轴），实到 $shown%",
            PanelBackdropOpacity.transparencyOf(committed), shown
        )
        assertEquals(
            "屏上那个数必须落在尺的格上（步长 ${PanelBackdropOpacity.STEP_PERCENT}），实到 $shown%——" +
                "不在格上就是把拖动途中的原始读数留在了屏上",
            0, shown % PanelBackdropOpacity.STEP_PERCENT
        )
        assertEquals(
            "滑杆自己也得停在吸附后的那一格（球与数字同一拍，不是只改了文案）",
            shown.toFloat(), sliderProgress().current, 0.5f
        )
        assertTrue(
            "拖动期间交出的每一颗 preview 也仍得是那把轴上的合法刻度：$previews",
            previews.isNotEmpty() && previews.all { it in PanelBackdropOpacity.steps }
        )
    }

    /**
     * 「拖动途中就一格一格吸」（原型 `input[type=range]{step:5}`）这一条，语义树量不到：
     * preview 在交出去之前已经过换算口那一格量化，"中途量化"与"松手才量化"在两把轴的回调读数上
     * **完全同形**。所以这一格钉的是源码形状——显示那一侧的临时读数必须来自"过了一遍换算口"
     * 的那一颗，而不是拖动的原始坐标。
     *
     * 反例就在下面：拿真生产源码改一处（把显示读数换回直接吃原始坐标），跑同一把尺必须报。
     * 旧那一版（球与屏上那个数停在两格之间、松手才跳回格上）在这句当场红。
     */
    @Test
    fun `the thumb is quantised during the drag, not only on release`() {
        val body = maskedEntrySource()
            .substringAfter("onValueChange = { next ->")
            .substringBefore("onValueChangeFinished")
        assertTrue("拖动那一段读不到显示读数的赋值，这把尺扫空了：$body", body.contains("draggingTransparency ="))
        assertTrue(
            "拖动途中屏上那颗读数没走换算口（等于把原始坐标留在屏上：球与数字停在两格之间）",
            DRAG_QUANTISED.containsMatchIn(body)
        )
        assertFalse(
            "拖动那一段把原始坐标直接交给显示读数了",
            DRAG_RAW_TO_DISPLAY.containsMatchIn(body)
        )

        // 反例证人：把生产那一行退回旧形状，同一把尺必须报（先证明这颗反例真的改了东西）
        val regressed = body.replace(
            "draggingTransparency = PanelBackdropOpacity.transparencyOf(stepped)",
            "draggingTransparency = next.roundToInt()"
        )
        assertNotEquals("反例没生效：退回旧形状后这一段与生产一模一样", body, regressed)
        assertFalse("反例没被打破：退回「原始坐标直接上屏」之后这把尺还放行", DRAG_QUANTISED.containsMatchIn(regressed))
        assertTrue("反例没被打破：退回旧形状后那句「吃原始坐标」没被认出", DRAG_RAW_TO_DISPLAY.containsMatchIn(regressed))
    }

    /** 生产那颗滑杆自己那一颗文件的掩码源码（剥注释，避免数到 KDoc 里的举例） */
    private fun maskedEntrySource(): String {
        val file = File("src/main/java/com/lovebrain/app/ui/panel/settings/SettingsOpacityEntry.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/settings/SettingsOpacityEntry.kt")
        assertTrue("读不到 $file——这一格会恒绿", file.isFile)
        return SourceScan.maskComments(file.readText(Charsets.UTF_8))
    }

    /**
     * 非法与越界的输入：屏上不许出现第二把轴上的数，也不许出现不在格上或出界的读数。
     *
     * `raw = true` 让宿主**不做**任何预先钳位（脏数原样进参数），判的就是这一行自己那一截回落：
     * 180 落回完全不透明那一头、-40 / 12 落回这一族允许的最透那一头；区间内那两个非格值
     * （22、62）就近落格——22 挨着下限所以落到最透那一格，62 落回 60 那一格（透明 40%），
     * 这一条同时钉住"放开区间之后中段仍然按 5% 对齐"。
     * 第一句是**对照**（合法那颗也必须念 0%）——其余几句要是被"屏上恒写 0%"那种坏法蒙过，对照先红。
     * `null` 那一档不在这一格的射程里：这一行的签名是 `Int`，读盘那一步的 null 回落由
     * `PanelBackdropOpacityTest` 钉。
     */
    @Test
    fun `out-of-range and off-grid stored values fall back to the legal ends`() {
        val host = mount(PanelBackdropOpacity.MAX_PERCENT, raw = true)
        listOf(
            PanelBackdropOpacity.MAX_PERCENT to opaqueEnd,   // 对照：合法那颗
            180 to opaqueEnd,                               // 越界的高那头
            -40 to transparentEnd,                          // 越界的低那头
            12 to transparentEnd,                           // 区间外、贴近下限
            22 to transparentEnd,                           // 区间内的非格值、贴着下限 ⇒ 就近落格
            62 to PanelBackdropOpacity.transparencyOf(60)   // 区间内的非格值、中段 ⇒ 就近落格
        ).forEach { (incoming, wantTransparency) ->
            rule.runOnIdle { host.value = incoming }
            rule.waitForIdle()
            val shown = onScreenPercents().single()
            assertEquals("宿主交来 $incoming 时屏上念的数", wantTransparency, shown)
            val info = sliderProgress()
            assertEquals(
                "宿主交来 $incoming 时滑杆画的那一格",
                wantTransparency.toFloat(), info.current, 0.01f
            )
            assertTrue(
                "宿主交来 $incoming 时读数出了显示轴那把尺的范围（读数 ${info.current}，范围 ${info.range}）",
                info.current >= info.range.start - 0.01f && info.current <= info.range.endInclusive + 0.01f
            )
        }
    }

    companion object {
        /** 悬浮窗设置页那一族的常用档：轴的两端在这一档都要还拖得起来 */
        private const val ROW_WIDTH_DP = 300

        /** 起手与落点各从边上让开这一截（轨道两端各让开半个球，贴着边按下会被夹到端点） */
        private const val GRIP_DP = 12f

        /** 只挪这一截：约七八格透明度，既要越过 touch slop，落点又要故意不在格上（见上面那一格的说明） */
        private const val SMALL_DRAG_DP = 20f

        /** 「NN%」那一句的形状：屏上那颗读数唯一一次被按内容捞出来 */
        private val PERCENT_TEXT = Regex("""(\d+)%""")

        /** 拖动途中那颗显示读数**过了换算口**（原型 `step:5`：中途就吸到格上） */
        val DRAG_QUANTISED = Regex("""draggingTransparency\s*=\s*PanelBackdropOpacity\.transparencyOf\(""")

        /** 显示读数直接吃拖动的原始坐标（旧那一版：球与数字停在两格之间，松手才跳回来） */
        val DRAG_RAW_TO_DISPLAY = Regex("""draggingTransparency\s*=\s*next\.""")
    }
}
