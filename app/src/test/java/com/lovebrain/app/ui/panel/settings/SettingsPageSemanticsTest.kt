package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.ScrollScan
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
 * 悬浮窗齿轮那扇整窗设置页的语义树合同。
 *
 * 这一格只管三件"看界面看不出来、坏了也没人喊"的事：
 * 1. 每一颗可点节点**自己**够不够它那一档的下限：整页样本走全站下限，透明度滑杆那一颗走
 *    这一族紧凑件那一档（20dp 短边 + 48dp 可拖长边，理由写在那一格的 KDoc 里）。
 *    **透明度滑杆不在那份"可交互"样本里**：样本只认 click/toggle/disabled，而滑杆交出的是
 *    进度语义 ⇒ 它按 TestTag 单独定位，量仍然用同一把尺（[SemanticsProbe.of] /
 *    `tooSmall` / `assertTargetsMeetFloor`）；同一颗的读屏名字也在这一条里判。
 * 2. 「返回」在**视口很矮**时是否仍然在屏幕上——它钉在滚动柱之外，这一条判的就是这件事；
 * 3. 挂在树上的那一个百分比读数必须是**本次**那一份值（不许抄旧值、也不许再画第二份）。
 *
 * 旧版本里还有两条判据（超时那一行"一行四档 + 恰有一档 Selected"、捕获范围"打开后复用首页那一段
 * 选择器并把勾选投回调用方"）。它们随**整窗设置页里的那两栏**一起撤掉：用户 2026-10-03 原话
 * "设置里面暂时先弄一个调透明度的，别的都不要弄"，`LoveBrainSettingsContent` 现在只接
 * `onBack / opacityPercent / onOpacityPreview / onOpacityCommit / modifier` 五个参数。
 * 这不是失去能力——超时四档住在首页「模型供应商」那一格的同一张表单里
 * （`ui/home/ProviderSection.kt`），捕获范围住在首页「消息捕获」那一格里
 * （`ui/home/CaptureAppsScreen.kt`）；**要续那两条判据应当在那两个主体上续**，
 * 而不是在这一页里留着钉一栏已经不存在的东西的断言。
 *
 * 文案锚点一律 `getString` 取：这台机器的环境解析出来是英文（前例写在
 * `ProviderFormSemanticsTest` 的文件头），写死中文会让判据在谁都没改代码的日子里假红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPageSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }
    private val scan by lazy { ScrollScan(rule, probe) }

    private val backName: String get() = ctx.getString(R.string.common_back)
    private val opacityName: String get() = ctx.getString(R.string.settings_opacity_label)

    /**
     * 这一页现在是**无状态**的：透明度读数由宿主给，拖动预览与松手写盘各一条回调。
     * 参数就那五个（`onBack / opacityPercent / onOpacityPreview / onOpacityCommit / modifier`），
     * 所以这里也不需要一个假 VM——旧版这一格为了画供应商与超时那两栏才 `mockk` 出一份
     * `SetupViewModel`（七条流必须点名返回真流，否则 relaxed 交回的
     * 泛型 mock 一取 `.value` 就 CCE，栈顶还指向一个不存在的行号；那条坑仍写在
     * `ProviderFormSemanticsTest`，只是这一页现在没有可踩的对象了）。
     */
    private fun mount(
        matrix: UiMatrix = UiMatrix(360),
        opacityPercent: Int = 60,
        onBack: () -> Unit = {}
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                LoveBrainSettingsContent(
                    onBack = onBack,
                    opacityPercent = opacityPercent,
                    onOpacityPreview = {},
                    onOpacityCommit = {}
                )
            }
        }
        rule.waitForIdle()
    }

    /**
     * 整页滚一遍并取**最大面积**，不是只扫首屏：滚动容器会把贴着视口底的那颗裁小、
     * 把滚出去的那颗压成 0x0（同一件事写在 `ScrollScan` 的文件头）。
     */
    private fun scannedTargets(): List<SemanticsProbe.Target> =
        probe.laid(scan.toBottom("整窗设置页").values.toList())

    /**
     * 按 TestTag 定位透明度滑杆，取**它自己**那颗语义节点的读数。
     *
     * 为什么不借 [SemanticsProbe] 那份"可交互"样本：那份样本只认 click / toggle / disabled
     * 三种语义，而 Material 滑杆交出的是进度语义，三种一个都不带 ⇒ 它**永远进不了样本**。
     * 那是量具瞎，不是产品没画（生产那颗滑杆一直在这一栏里）；而那份样本定义是全站共用的尺，
     * 为这一颗去改它会让别的套件"可交互颗数"的判据集体变义。
     * 所以这里只换"找到它"的方式，不换"怎么量"：数值与标签仍走同一颗 [SemanticsProbe.of]、
     * 同一颗 [SemanticsProbe.Target.tooSmall]、同一颗 [SemanticsProbe.laid] 的哨兵。
     */
    private fun opacitySliderTarget(screen: String): SemanticsProbe.Target {
        val nodes = rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG)).fetchSemanticsNodes()
        assertEquals(
            "$screen 里 tag=$SETTINGS_OPACITY_SLIDER_TAG 的滑杆必须恰有一颗" +
                "（撤成 0 颗、或再画第二颗都不算这一栏在位），实到 ${nodes.size}",
            1,
            nodes.size
        )
        // 量不到尺寸的那一份读数不能当证据：laid 拿不到颗就抛，不许把"看不见"读成"没东西要查"
        return probe.laid(listOf(probe.of(nodes.single()))).single()
    }

    /**
     * 这一格判两件事：整页可交互样本仍按**全站下限**量；透明度滑杆按**这一档紧凑件自己那一把尺**量。
     *
     * 滑杆为什么不是 48：界面合同把这一族的小件写成同一档——模式栏「外层 30dp / 内部字形 20dp」、
     * 供应商行内「28dp 盒 / 16dp 字形」、思考模式那颗是 MiniSwitch **36×20dp**。
     * 这一族交出的短边就是 20dp；把透明度这一颗单独撑回 48 高，这一栏就从旧版那一行变成
     * 一条巨大 Material 滑杆（同一条合同也明令不要把巨大 Switch 放回这张表单）。
     * 全站那颗下限一个字没动：上面那份样本里每一颗仍按 48 量，档位是**在这一格显式传进去**的。
     *
     * ⚠ 它不是存在性判据，两轴分开钉：
     * · **短边（高）** ≥ 这一族的 20dp。反例：轨道被挤到 12dp、或整条滑杆撤掉只留一句百分比
     *   读数（那颗节点读不到时 `opacitySliderTarget` 里的 laid 哨兵直接抛，不会静默绿）→ 红；
     * · **长边（宽）** ≥ 48dp：横向可拖距离低于这颗就滑不出档位（本机 286dp）。
     *
     * 仪器边界（如实记着，别把它读成"竖直热区已经量过了"）：语义树交回来的是那颗**轨道**的边界，
     * 生产写在滑杆自己那条链上的 `heightIn(min = TOUCH_TARGET_MIN_DP)`（`SettingsOpacityEntry`
     * 里那一笔；这一栏从 `SettingsAdvancedEntry` 搬过来之后那一笔一个字没动）在这份读数里看不见
     * ——本机两次实跑报的都是 20dp 高。于是"按住滑杆那一条的
     * 竖直热区到底多高"这一半，JVM 侧现在量不到：要么上设备量，要么像输入行那样把外层透明盒
     * 也挂成一颗有 tag 的语义节点，再把它加进这一格。那一层没落地之前，这一格不许松成
     * "读得到就绿"。
     */
    @Test
    fun `every actionable node on the settings page meets the touch floor`() {
        mount()
        val seen = scannedTargets()
        val offenders = seen.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "有 ${offenders.size}/${seen.size} 颗可交互节点小于 ${probe.floorDp.toInt()}dp：\n" +
                offenders.joinToString("\n") { "  " + it.describe() } +
                "\n  下限要垫在带语义的那颗自己身上；外面套一层大盒子等于没改。",
            offenders.isEmpty()
        )
        // 滑杆那一颗单独按 tag 判（上面那份样本永远不会有它，见 opacitySliderTarget）
        val slider = opacitySliderTarget("整窗设置页")
        probe.assertTargetsMeetFloor(
            listOf(slider),
            COMPACT_TRACK_MIN_HEIGHT_DP,
            "整窗设置页那颗透明度滑杆",
            "（短边按这一族紧凑件那一档 ${COMPACT_TRACK_MIN_HEIGHT_DP.toInt()}dp 量；" +
                "本机实量 ${slider.widthDp.toInt()}x${slider.heightDp.toInt()}dp " +
                "@(${slider.leftDp.toInt()},${slider.topDp.toInt()})）"
        )
        assertTrue(
            "滑杆横向可拖的距离不到 ${DRAGGABLE_MIN_WIDTH_DP.toInt()}dp（轨道被收成一条窄带就滑不出档位）：" +
                slider.describe(),
            slider.widthDp + 0.5f >= DRAGGABLE_MIN_WIDTH_DP
        )
    }

    @Test
    fun `every actionable node says what it is`() {
        // 62 不是这一页的默认读数：名字里那个百分比必须是本次这一份值，抄来的旧值会当场红
        mount(opacityPercent = 62)
        val seen = scannedTargets()
        val unlabeled = seen.filter { !it.labeled }
        assertTrue(
            "读屏念不出这些节点是什么（滑杆这类控件本身没有文案，名字必须由调用方拼给它）：\n" +
                unlabeled.joinToString("\n") { "  " + it.describe() },
            unlabeled.isEmpty()
        )
        val slider = opacitySliderTarget("整窗设置页")
        val readout = ctx.getString(R.string.settings_opacity_percent, 62)
        assertTrue(
            "滑杆那颗得自己念出「$opacityName + 当前百分比」，实到 ${slider.describe()}",
            slider.contentDescriptions.any { it.startsWith(opacityName) && it.contains(readout) }
        )
    }

    /**
     * 挂在树上的那一个百分比必须就是**本次**传进来的那一份值，而且**只有一处**。
     *
     * 读数由这一格自己举起的一颗状态给：先 62，再在**同一次挂载**里翻成 85。
     * 只挂一次是硬要求（同一个用例里第二次 `setContent` 会被框架当场拒掉，那一段所有断言
     * 从未执行过——这条坑就写在被删掉的那一格的位置上）。
     *
     * 反例：
     * · 把读数写成常量/抄一个旧值 ⇒ 翻到 85 之后 `hasText(getString(...,85))` 一颗都找不到，红
     *   （只看第一句是抓不住的：传 62 期待 62 的单一读数对"写死 62"也是绿的，所以第二句
     *   与那句"旧的必须消失"才是这一格的牙）；
     * · 再画第二份"预览用的数字"（两处各算一遍 `/100`，界面上的数与盘上存的数就会分家）⇒ 2 颗，红；
     * · 滑杆整颗被撤成一句百分比 ⇒ tag 那颗数到 0，红。
     *
     * ⚠ 旧版这一格还钉着"右侧那两个字与预览条里的数字同一个来源 ⇒ 两处"，并数过一颗
     * `SETTINGS_OPACITY_PREVIEW_TAG`。预览条随  撤除（用户原话"调个透明度还写一堆"，
     * `SettingsOpacityEntry` 里现在只有 label + 滑杆 + 一个百分比读数，那颗 tag 常量也没留），
     * 判据"两处同一个来源"在这一页**没有对应主体**：没有第二处可数，也就没有第二处可比。
     * 浓度换算本身那一半（画出去的 alpha 与交出的数同一来源）没有跟着预览条走——
     * 它挂在 `SettingsPageStructureTest` 的换算格上，测的是 `PanelBackdropOpacity.alphaOf` 本体。
     */
    @Test
    fun `the opacity readout comes from the incoming value`() {
        val incoming = mutableStateOf(62)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(360).RenderIn(deviceDensity) {
                LoveBrainSettingsContent(
                    onBack = {},
                    opacityPercent = incoming.value,
                    onOpacityPreview = {},
                    onOpacityCommit = {}
                )
            }
        }
        rule.waitForIdle()
        val first = ctx.getString(R.string.settings_opacity_percent, 62)
        rule.onAllNodes(hasText(first)).assertCountEquals(1)
        rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG)).assertCountEquals(1)

        // 翻一次输入：屏幕上交出的那一个数必须跟着走，旧的那一个必须当场消失
        rule.runOnIdle { incoming.value = 85 }
        rule.waitForIdle()
        val second = ctx.getString(R.string.settings_opacity_percent, 85)
        rule.onAllNodes(hasText(second)).assertCountEquals(1)
        rule.onAllNodes(hasText(first)).assertCountEquals(0)
    }

    @Test
    fun `the back control is still on screen when the viewport is too short for the form`() {
        // 视口压到 260dp：正文连一栏都摆不下，但页头钉在滚动柱之外，"返回"必须仍在
        var back = 0
        mount(matrix = UiMatrix(320, heightDp = 260), onBack = { back++ })
        rule.onNodeWithContentDescription(backName).assertIsDisplayed()
        rule.onNodeWithContentDescription(backName).performClick()
        rule.waitForIdle()
        assertEquals("页头那颗返回必须真的把 onBack 交出去", 1, back)
    }

    // 旧版这一格还有一条「捕获范围收起/打开后复用首页那一段选择器，勾选投回调用方」
    // （`capture scope starts collapsed and reuses the shared picker once opened`）。
    // 它的主体——整窗设置页里那一栏 `SettingsCaptureEntry`——随用户原话"别的都不要弄"整栏撤掉，
    // 参数 `captureAllowed / captureCandidates / onCaptureToggle` 也从签名里消失了，
    // 所以这一格在这一页没有可测的对象，删格；能力住在首页「消息捕获」子页（`CaptureAppsScreen`），
    // 那条"勾选只落调用方一个写入口"的判据要在**那一页**续（本轮不由我改那两个文件）。
    // 顺带留一句仪器教训：这一格以前在同一个用例里 `setContent` 第二次，框架报
    // "Cannot call setContent twice" ⇒ 那一段所有断言从未执行过（红的从来不是产品）。

    companion object {
        /**
         * 这一族紧凑件交出的短边（模式栏内部字形 20dp、MiniSwitch 36×20 同一档）：
         * 透明度滑杆的**轨道**就量这一档，别拿它去量页面主体，也别把它当成"撤掉了下限"。
         */
        private const val COMPACT_TRACK_MIN_HEIGHT_DP = 20f

        /** 可拖控件的长边下限：横向滑不出这距离就等于这一栏没有档位可挑 */
        private const val DRAGGABLE_MIN_WIDTH_DP = 48f
    }
}
