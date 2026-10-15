package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.model.KnowledgeBase
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
 * 这一格只管四件"看界面看不出来、坏了也没人喊"的事：
 * 1. 每一颗可点节点**自己**够不够它那一档的下限：页头那一族（返回 / 收起）走 F3 装饰带那一档
 *    （短边 ≥24、整行不许被撑回旧那条 48 厚顶栏），页级控件（意图开关）走全站下限，透明度滑杆
 *    那一颗走这一族紧凑件那一档（20dp 短边 + 48dp 可拖长边，理由写在那一格的 KDoc 里）。
 *    **透明度滑杆不在那份"可交互"样本里**：样本只认 click/toggle/disabled，而滑杆交出的是
 *    进度语义 ⇒ 它按 TestTag 单独定位，量仍然用同一把尺（[SemanticsProbe.of] /
 *    `tooSmall` / `assertTargetsMeetFloor`）；同一颗的读屏名字也在这一条里判。
 * 2. 「返回」在**视口很矮**时是否仍然在屏幕上——它钉在滚动柱之外，这一条判的就是这件事；
 * 3. 挂在树上的那一个百分比读数必须是**本次**那一份值（不许抄旧值、也不许再画第二份）；
 *    屏上那一把轴是**透明度**（0–40），盘上那一把仍是不透明度（100–60）：换算只走
 *    [PanelBackdropOpacity.transparencyOf]，判据也从它现读，不在判据里抄第二个数。
 * 4. 意图那一格在新合同下的两件事：屏上**只有两态**（关 = 输入与保存整行不在树上，
 *    开 = 输入与保存在同一行），以及"切库不许把 A 的意图写给 B"。
 *    这一格量的是**整页接线之后**的形状（页面把开关、正文、写口都交下来），
 *    行本身的版式与三档宽度判据住在 `SettingsIntentRowTest`（那一格把这一颗件挂在固定宽度盒子里量，
 *    整页这一侧隔着滚动柱与组外框，量不出那三个数）。
 *
 * 旧版本里还有两条判据（超时那一行"一行四档 + 恰有一档 Selected"、捕获范围"打开后复用首页那一段
 * 选择器并把勾选投回调用方"）。它们随**整窗设置页里的那两栏**一起撤掉，依据是 2026-10-03 定下的
 * 这一页的范围：先只留调透明度这一件事，其余参数一概不摆。那一页现在这一格只交透明度与页头那几条
 * （意图与知识库那两格由下面 `mountIntent` 那一路交，各自点名），不需要的参数留默认值。
 * 这不是失去能力——超时四档住在首页「模型供应商」那一格的同一张表单里
 * （`ui/home/ProviderSection.kt`），捕获范围住在首页「消息捕获」那一格里
 * （`ui/home/CaptureAppsScreen.kt`）；**要续那两条判据应当在那两个主体上续**，
 * 而不是在这一页里留着钉一栏已经不存在的东西的断言。
 *
 * 意图那一格也换过一次判据（2026-10-11 的新合同：开关 + 同一行输入与保存）：
 * · 旧格 `the first toggle only raises the intro and writes nothing until it is acknowledged`
 *   与 `a relaunch after the acknowledged intro does not repeat it` 钉的是"首次开启先弹介绍、
 *   确认过才启用、intro-seen 落盘"。那扇浮层与那颗盘上标记已经整颗退役（开启意图就是开启），
 *   所以两句由 `turning the intent on hands over exactly one write` **替代**：
 *   拨一下只交一笔、正文原样，宿主落定之后输入行才在场。
 * · 旧格 `the intro scrim covers the page instead of taking a slot in the form` 钉的是**挂载层级**
 *   （遮罩要盖住整页）。浮层没了，这一页也就不该再有任何浮层——那条判据改由
 *   `SettingsPageStructureTest` 的"这一页不打开任何浮层"那一格看着，本文件不再重复挂一具尸体；
 * · 旧格 `an expired intent keeps its body instead of collapsing to an empty row` 与
 *   `saving or completing an intent does not restart its period` 钉的是六档与三档期限。
 *   期限与终态整族退役 ⇒ 那两句由 `the intent row when off renders no field and no chip of its own`
 *   （关着 = 输入与保存整行不在树上，且这一页一颗"期限/完成"那类的芯片都不许多出来）、
 *   `the intent row when on puts the field and the save on one row`（开着 = 同一行，芯片只多那一颗
 *   「保存」、互斥单选那一族一颗都不许多）与 `the page carries exactly two segmented rows`
 *   （整页剩下的只有图标大小与回复卡片那两排）三格替代；
 *   `a draft typed on one library is not written to another` 是**保留**的真实证人，只是按下去的
 *   那颗从期限芯片换成那颗「保存」。
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
     * 这一页是**无状态**的：透明度读数由宿主给，拖动预览与松手写盘各一条回调；意图与知识库那几格
     * 的参数都留默认值（意图那几格要用的读态由 [mountIntent] 那一颗点名交，别在这一颗里塞）。
     * 这里因此不需要一个假 VM——旧版这一格为了画供应商与超时那两栏才 `mockk` 出一份
     * `SetupViewModel`（七条流必须点名返回真流，否则 relaxed 交回的
     * 泛型 mock 一取 `.value` 就 CCE，栈顶还指向一个不存在的行号；那条坑仍写在
     * `ProviderFormSemanticsTest`，只是这一页现在没有可踩的对象了）。
     */
    private fun mount(
        matrix: UiMatrix = UiMatrix(360),
        opacityPercent: Int = 60,
        onBack: () -> Unit = {},
        onCollapse: () -> Unit = {}
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                LoveBrainSettingsContent(
                    onBack = onBack,
                    onCollapse = onCollapse,
                    opacityPercent = opacityPercent,
                    onOpacityPreview = {},
                    onOpacityCommit = {},
                    assistantOn = true,
                    onAssistantEnable = {},
                    onAssistantClose = {},
                    bubbleSizeDp = BubbleSizeTier.STANDARD_DP,
                    onBubbleSizeChange = {},
                    replyCardVertical = true,
                    onReplyCardVerticalChange = {}
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
     * 这一格判三件事：页头那一族走 **F3 装饰带档**（与本体 `PanelHeader` 同线）、透明度滑杆走
     * **这一档紧凑件自己那一把尺**、滑杆横向可拖距离走可拖下限。
     *
     * ⚠ **这一档作废了"整页可交互样本一律 ≥48"这一句原判据**（2026-10-06 定档，起因是那一页控件画得过大）：
     * 旧形制那一页拿的是 `LbTopBarLevel.Page`，返回那颗自带 48 见方热区（`LbTopBar.kt` 的
     * `BackControl` = 22dp 字形 / 48 见方），于是"整页可交互样本按全站 48 量"当时是对的——那一页
     * 唯一的可交互件就是那颗 48 的返回。这一页现在落在 F3 紧凑族：行高 30、`titleMedium`15、
     * 无分割线、返回字形降档（`LbTextAction` 图标档，28 见方热区 / 16dp 字形），与本体那一族同线。
     * 回退成用 `LbTopBarLevel.Page`（返回撑回 48、整行 48）**会被下面那两句绝对值一起判红**：
     * · 短边下限从 `PANEL_HEADER_HOTZONE`（24）起——把返回盒缩到 24 以下红；
     * · 整行/返回盒不许被撑回旧那一条 48 厚顶栏——`≤ PANEL_HEADER_ROW`（30），48 的返回当场红。
     * 这两句一头一尾，正是"不是把尺整体调松了事"的凭据（同本体 `PanelHeaderTouchTargetsTest` 的写法）。
     *
     * 滑杆为什么不是 48：界面合同把这一族的小件写成同一档——模式栏「外层 30dp / 内部字形 20dp」、
     * 供应商行内「28dp 盒 / 16dp 字形」、思考模式那颗是 MiniSwitch **36×20dp**。
     * 这一族交出的短边就是 20dp；把透明度这一颗单独撑回 48 高，这一栏就从旧版那一行变成
     * 一条巨大 Material 滑杆（同一条合同也明令不要把巨大 Switch 放回这张表单）。
     * 全站那颗下限本身一个字没动：上面那份样本里每一颗仍按 48 量，档位是**在这一格显式传进去**的。
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
        // 页头那一族（返回 / 收起）走 F3 装饰带那一档；意图开关那一颗是**页级**控件走全站 48。
        // 滑杆交出的是进度语义，进不了这份样本，见 opacitySliderTarget。
        // 所以先把页头那两颗从整份样本里挑出来按 F3 量，再单独判意图开关走 48——
        // 将来若在这页加一颗**页级**主动作，该走全站 48，不许把这一档整屏调松。
        val headerFloor = TouchTier.PANEL_HEADER_HOTZONE // 短边下限（24）：与本体齿轮同一档
        val seen = scannedTargets()
        val headerNames = setOf(backName, ctx.getString(R.string.panel_collapse))
        val headerNodes = seen.filter { it.contentDescriptions.any { d -> d in headerNames } }
        val headerOffenders = headerNodes.filter { it.tooSmall(headerFloor) }
        assertTrue(
            "有 ${headerOffenders.size}/${headerNodes.size} 颗页头可交互节点小于 F3 装饰带那一档 ${headerFloor.toInt()}dp：\n" +
                headerOffenders.joinToString("\n") { "  " + it.describe() } +
                "\n  下限要垫在带语义的那颗自己身上；外面套一层大盒子等于没改。",
            headerOffenders.isEmpty()
        )
        // 反向证人（这一半才让上面那句不是"把尺调松了事"）：整行/返回盒不许被撑回旧那一条 48 厚顶栏。
        // 回退成 `LbTopBarLevel.Page` 时返回那颗 = 48 见方 > 30 → 这里当场红。
        headerNodes.forEach { node ->
            assertTrue(
                "页头节点 ${node.describe()} 高过 F3 装饰带那一档 ${TouchTier.PANEL_HEADER_ROW.toInt()}dp——" +
                    "这就是本轮要拆掉的旧版式（原话第 17 条'控件大'的可定位来源）。",
                node.heightDp <= TouchTier.PANEL_HEADER_ROW + 0.6f
            )
        }
        // 意图开关那一颗是页级控件：MiniSwitch 自带 ≥48 见方热区，按全站下限量。
        // 「保存」那颗也走全站 48，但它只在开着的时候在场，量它的是 `SettingsIntentRowTest`。
        val intentToggle = seen.firstOrNull { it.contentDescriptions.any { d -> d.contains("意图") } }
        assertTrue(
            "设置页该有一颗意图开关；实到：${seen.joinToString { it.describe() }}",
            intentToggle != null
        )
        assertTrue(
            "意图开关走全站 48 下限，不许缩：" + intentToggle!!.describe(),
            !intentToggle.tooSmall(TouchTier.SITE_FLOOR)
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
        // 62 不是这一页的默认读数：名字里那个百分比必须是本次这一份值，抄来的旧值会当场红。
        // ⚠ 62 是**落盘轴**（不透明度）上的输入；屏上那一把是**透明度**轴，读数由那把尺镜像过去
        //   （宿主 2026-10-11 那一笔把显示轴换成了 0–40 透明度，盘上的数一寸没动）。
        //   这一格因此不能把"62%"抄进判据：期望的那一句字由 `transparencyOf` 现算，
        //   屏上与判据读的是同一颗换算口——换算被改回去（直接印不透明度）时这里当场红。
        val incoming = 62
        mount(opacityPercent = incoming)
        val seen = scannedTargets()
        val unlabeled = seen.filter { !it.labeled }
        assertTrue(
            "读屏念不出这些节点是什么（滑杆这类控件本身没有文案，名字必须由调用方拼给它）：\n" +
                unlabeled.joinToString("\n") { "  " + it.describe() },
            unlabeled.isEmpty()
        )
        val slider = opacitySliderTarget("整窗设置页")
        val readout = ctx.getString(R.string.settings_opacity_percent, PanelBackdropOpacity.transparencyOf(incoming))
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
     * · 把读数写成常量/抄一个旧值 ⇒ 翻到 85 之后 `hasText(那一档透明度读数)` 一颗都找不到，红
     *   （只看第一句是抓不住的：传 62 期待 62 的单一读数对"写死 62"也是绿的，所以第二句
     *   与那句"旧的必须消失"才是这一格的牙）；
     * · 再画第二份"预览用的数字"（两处各算一遍换算，界面上的数与盘上存的数就会分家）⇒ 2 颗，红；
     * · 换算口用反了（把不透明度直接印在标着"透明度"的滑杆上）⇒ 期望串读不到，红
     *   （期望值由 [PanelBackdropOpacity.transparencyOf] 现算，与被测那一侧同一颗函数）；
     * · 滑杆整颗被撤成一句百分比 ⇒ tag 那颗数到 0，红。
     *
     * ⚠ 旧版这一格还钉着"右侧那两个字与预览条里的数字同一个来源 ⇒ 两处"，并数过一颗
     * `SETTINGS_OPACITY_PREVIEW_TAG`。预览条已撤除（这一栏只留一个可调的滑杆与一个读数，
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
                    onCollapse = {},
                    opacityPercent = incoming.value,
                    onOpacityPreview = {},
                    onOpacityCommit = {},
                    assistantOn = true,
                    onAssistantEnable = {},
                    onAssistantClose = {},
                    bubbleSizeDp = BubbleSizeTier.STANDARD_DP,
                    onBubbleSizeChange = {},
                    replyCardVertical = true,
                    onReplyCardVerticalChange = {},
                )
            }
        }
        rule.waitForIdle()
        val first = ctx.getString(
            R.string.settings_opacity_percent,
            PanelBackdropOpacity.transparencyOf(incoming.value)
        )
        rule.onAllNodes(hasText(first)).assertCountEquals(1)
        rule.onAllNodes(hasTestTag(SETTINGS_OPACITY_SLIDER_TAG)).assertCountEquals(1)

        // 翻一次输入：屏幕上交出的那一个数必须跟着走，旧的那一个必须当场消失
        rule.runOnIdle { incoming.value = 85 }
        rule.waitForIdle()
        val second = ctx.getString(
            R.string.settings_opacity_percent,
            PanelBackdropOpacity.transparencyOf(85)
        )
        rule.onAllNodes(hasText(second)).assertCountEquals(1)
        rule.onAllNodes(hasText(first)).assertCountEquals(0)
        // 两把轴不许混：屏上那一句念的是透明度，绝不是传进去的那两个不透明度本身
        rule.onAllNodes(hasText(ctx.getString(R.string.settings_opacity_percent, 62))).assertCountEquals(0)
        rule.onAllNodes(hasText(ctx.getString(R.string.settings_opacity_percent, 85))).assertCountEquals(0)
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
    // 它的主体——整窗设置页里那一栏 `SettingsCaptureEntry`——随"这一页只留透明度"那一条范围约定整栏撤掉，
    // 参数 `captureAllowed / captureCandidates / onCaptureToggle` 也从签名里消失了，
    // 所以这一格在这一页没有可测的对象，删格；能力住在首页「消息捕获」子页（`CaptureAppsScreen`），
    // 那条"勾选只落调用方一个写入口"的判据要在**那一页**自己的守卫里续，本文件不替它挂。
    // 顺带留一句仪器教训：这一格以前在同一个用例里 `setContent` 第二次，框架报
    // "Cannot call setContent twice" ⇒ 那一段所有断言从未执行过（红的从来不是产品）。

    /**
     * 页头那一档整改（2026-10-06 定档，前半修的是"控件画得过大"）里，这一格钉的是后半：
     * 「切进设置页之后**收不起窗**」。
     *
     * 主面那颗收起长在 `PanelHeader` 里，齿轮一开整窗内容换成这一页，那颗就随整排一起消失，
     * 而这一页原本只交得出"返回"。现在这一页自己必须再给一颗收起，而且**只把宿主那一次点击投下去**
     * （状态所有者仍只有宿主那一颗 `dismissPanelToBubble`，这一页不许自己记"收起了没有"）。
     *
     * 回退成什么会红：
     * ① 那颗钮被删掉、或换成不报 `contentDescription` 的自绘 `Box` ⇒ `onNodeWithContentDescription`
     *    当场抛"没有找到/不止一个"；
     * ② 页面自己 `remember` 一份收起状态而不投回调 ⇒ 计数 `expected:<1> but was:<0>`；
     * ③ 一次点击投两遍（例如又给整行挂一颗 clickable）⇒ `expected:<1> but was:<2>`。
     * 反向证人：`onCollapse` 从来没被调用过的那一版实现（这颗回调接上来之前的形状）在这格就是 0。
     */
    @Test
    fun `the settings page hands the user one collapse and it fires exactly once`() {
        var collapses = 0
        mount(onCollapse = { collapses++ })
        val name = ctx.getString(R.string.panel_collapse)
        // 恰好一颗：`onNode*` 对"零颗或多颗"都直接失败，这一句同时钉住"没有"与"长第二颗"
        rule.onNodeWithContentDescription(name).assertIsDisplayed()
        rule.onNodeWithContentDescription(name).performClick()
        rule.waitForIdle()
        assertEquals("点收起只投宿主那一次，这一页不存第二本账", 1, collapses)
    }

    /**
     * 面板级通知要在**设置页里**看得见：这一页把主面的通知位盖住了，切库失败那一句
     * 「知识库切换没有成功，请重试」以前只画在主面那一格——用户点完知识库只会觉得"点了没反应"。
     * 现在同一条消息（`NoticeBoard.current` 那一颗）也画在设置页页首，语气与主面同一套
     * （Warning 黄 / 其余绿，同一颗 [KbNoticeBanner]）。
     *
     * 牙口：① 有通知 → 文案在树上、可关，关那一拍把**同一条的 id** 交回宿主；
     * ② 没通知 → 页上没有那一条（反向证人：-banner 永远画着的那种实现在这格红）；
     * ③ 带建议卡的那一类（Profile/Stage）**不**进设置页——确认动作归主面那一族，
     * 这一页只接普通通知，画了会出现"两处各自确认同一件事"的第二本账。
     */
    @Test
    fun `the panel notice is visible inside the settings page and dismiss hands back the id`() {
        val warningLine = "知识库切换没有成功，请重试"
        val dismissed = mutableListOf<Long>()
        // 一次 setContent：宿主那一份通知挂在 MutableState 上，挂载后翻转它来切换三种局面
        // （有普通通知 → 空 → 带建议卡）。`setContent` 每个测试只许一次（见 mount 的合同），
        // 所以"没有通知"与"建议卡那类"两个反向证人由状态翻转给出，不另起挂载。
        val noticeState = mutableStateOf<NoticeBoard.Notice?>(
            NoticeBoard.Notice(
                id = 7L,
                channel = NoticeBoard.Channel.Warning,
                message = warningLine,
                autoDismissMillis = null,
                shownAtMillis = 0L
            )
        )
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(360).RenderIn(deviceDensity) {
                LoveBrainSettingsContent(
                    onBack = {},
                    onCollapse = {},
                    opacityPercent = 60,
                    onOpacityPreview = {},
                    onOpacityCommit = {},
                    assistantOn = true,
                    onAssistantEnable = {},
                    onAssistantClose = {},
                    bubbleSizeDp = BubbleSizeTier.STANDARD_DP,
                    onBubbleSizeChange = {},
                    replyCardVertical = true,
                    onReplyCardVerticalChange = {},
                    notice = noticeState.value,
                    onDismissNotice = { id -> dismissed += id }
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(warningLine).assertIsDisplayed()
        assertEquals("还没按关闭，不该有 id 交回来", emptyList<Long>(), dismissed)
        rule.onNodeWithContentDescription(ctx.getString(R.string.a11y_close_notice)).performClick()
        rule.waitForIdle()
        assertEquals("关闭那一拍必须交回同一条的 id", listOf(7L), dismissed)

        // 反向证人①：通知清空的那一屏，页上不该长出那一条
        noticeState.value = null
        rule.waitForIdle()
        assertEquals(
            "没有通知时设置页不该有横幅",
            0,
            rule.onAllNodes(hasText(warningLine)).fetchSemanticsNodes().size
        )

        // 反向证人②：带建议卡的那一类不进设置页——确认动作归主面那一族
        noticeState.value = NoticeBoard.Notice(
            id = 9L,
            channel = NoticeBoard.Channel.Knowledge,
            message = "带建议的消息不在此页",
            autoDismissMillis = null,
            shownAtMillis = 0L,
            suggestion = NoticeBoard.SuggestionRef(NoticeBoard.SuggestionKind.Profile, "k")
        )
        rule.waitForIdle()
        assertEquals(
            "建议卡那一类不归设置页",
            0,
            rule.onAllNodes(hasText("带建议的消息不在此页")).fetchSemanticsNodes().size
        )
    }

    // ═══════════ 意图那一格：两态、同一行、切库归属 ═══════════

    /**
     * 写口那一次交出去的两件（正文／启用）——这一族只有一条写口，也只交这两件。
     *
     * 旧形状是四件（正文／启用／有效期／是否按此刻重算期限）。期限与状态那一族读数随两态合同
     * 退役之后，"交的期限对不对""有没有顺手重算"这两问已经没有对象可问；那两问的牙挪到
     * **件数与内容逐字比对**上（下面各格用 `assertEquals(那一句, 期望列表, 实到列表)`）：多交一笔、
     * 少带正文、把 enabled 拨反都仍然当场红。
     */
    private data class IntentWrite(
        val text: String,
        val enabled: Boolean
    )

    private val intentSwitchName: String get() = ctx.getString(R.string.intent_label)
    private val intentSaveName: String get() = ctx.getString(R.string.intent_save)

    /**
     * 这一页上所有芯片（`LbTags.CHIP` 是设计系统那颗芯片自己的锚点）。
     *
     * 意图那一格退役之后，这一页的芯片只有两排分段选择器（图标大小三颗 + 回复卡片两颗）
     * 与开着时那一颗「保存」。所以"这一页有几颗芯片"这一份读数就是旧六档有没有回来的形状判据：
     * 三档期限或那颗「已完成」回来 = 数字变大，输入与保存整行没撤干净 = 关着的时候也数得到颗。
     */
    private fun chipCount(): Int = rule.onAllNodes(hasTestTag(LbTags.CHIP)).fetchSemanticsNodes().size

    private fun kbLibrary(name: String) =
        KnowledgeBase(name = name, displayName = name, updatedAt = "2026-10-08T09:00:00+08:00", active = true)

    /**
     * 意图那一格的挂载：整页挂生产那颗 [LoveBrainSettingsContent]——开关、正文与写口都从页面
     * 交下来，这一格判的正是**接完线之后**屏幕上还有没有旧读数、以及那一行是不是真的在页里。
     * 期限／状态／介绍那几颗参数已经从签名上删掉了（它们随两态合同退役），所以调用里连默认值
     * 都不必再留。
     */
    private fun mountIntent(
        writes: MutableList<IntentWrite>,
        matrix: UiMatrix = UiMatrix(360, heightDp = 900),
        intentEnabled: MutableState<Boolean> = mutableStateOf(false),
        intentText: MutableState<String> = mutableStateOf(""),
        activeKb: MutableState<String?> = mutableStateOf("kbA"),
        knowledgeBases: List<KnowledgeBase> = listOf(kbLibrary("kbA"), kbLibrary("kbB")),
        switches: MutableList<String> = mutableListOf(),
        onBack: () -> Unit = {}
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                LoveBrainSettingsContent(
                    onBack = onBack,
                    onCollapse = {},
                    opacityPercent = 100,
                    onOpacityPreview = {},
                    onOpacityCommit = {},
                    assistantOn = true,
                    onAssistantEnable = {},
                    onAssistantClose = {},
                    bubbleSizeDp = BubbleSizeTier.STANDARD_DP,
                    onBubbleSizeChange = {},
                    replyCardVertical = true,
                    onReplyCardVerticalChange = {},
                    intentEnabled = intentEnabled.value,
                    // 正文由宿主那一份状态给：切库之后"这一格现在显示哪块库的那条正文"要能当场翻过来
                    // （下面 `a draft typed on one library is not written to another` 那一格钉的就是这件事）
                    intentText = intentText.value,
                    onIntentChange = { text, enabled ->
                        writes += IntentWrite(text, enabled)
                    },
                    knowledgeBases = knowledgeBases,
                    activeKbName = activeKb.value,
                    onSwitchKb = { name -> switches += name }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun nodeCount(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /**
     * 关掉的那一态：输入与保存整行不在树上，盘上那条正文一个字都没被写走。
     *
     * 芯片颗数是这一格的主要牙：这一页此刻只剩两排分段选择器（图标大小三颗 + 回复卡片两颗），
     * 意图那一格一颗都不该贡献——旧那三档期限与那颗「已完成」回来就在这里多两颗，
     * "关着但留一片禁用的表单"那种形状在这里多一颗。
     *
     * 替代关系：旧格 `an expired intent keeps its body instead of collapsing to an empty row`
     * 要求"已到期那一格仍有正文区与期限读数"；新合同里没有期限与终态，那半句判据翻成
     * "关着的时候正文区与那些读数一颗都不许在"，正文本身由 [IntentWrite] 那笔账看着
     * （关着不写盘 = 那条文本原样留着）。
     */
    @Test
    fun `the intent row when off renders no field and no chip of its own`() {
        val writes = mutableListOf<IntentWrite>()
        mountIntent(
            writes = writes,
            intentEnabled = mutableStateOf(false),
            intentText = mutableStateOf("先约她看电影")
        )

        rule.onAllNodes(hasContentDescription(intentSwitchName))[0].assertIsOff()
        assertEquals("关着的时候整页没有输入节点", 0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals("关着的时候「$intentSaveName」那颗不渲染", 0, nodeCount(intentSaveName))
        assertEquals("关着的时候这一页只剩两排分段选择器那五颗芯片", SELECTED_SEGMENT_COUNT, chipCount())
        assertTrue("关着不写盘（那条正文原样留着、不注入）：实到 $writes", writes.isEmpty())
    }

    /**
     * 开着的那一态：输入与保存在**同一行**（垂直中心一致），里面回填的就是盘上那条正文；
     * 芯片只比关着的时候多那一颗「保存」（互斥单选那一族一颗都不许多＝期限档没回来）。
     *
     * ⚠ 这一格量的是"接线之后整页里那一行成不成立"；那颗按钮在 260 / 300 / 350dp 三档下
     * 会不会被长正文挤走、两者之间只留一份留白，判据在 `SettingsIntentRowTest`——
     * 那一格把件挂在固定宽度盒子里量，整页这一侧隔着滚动柱，宽度这类数在这一页量不准。
     */
    @Test
    fun `the intent row when on puts the field and the save on one row`() {
        val writes = mutableListOf<IntentWrite>()
        mountIntent(
            writes = writes,
            intentEnabled = mutableStateOf(true),
            intentText = mutableStateOf("先约她看电影")
        )

        val fields = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals("开着的时候输入节点恰一颗，实到 ${fields.size}", 1, fields.size)
        assertEquals(
            "已保存的正文回填进那一行（EditableText 是 AnnotatedString，要 .text）",
            "先约她看电影",
            fields.single().config.getOrNull(SemanticsProperties.EditableText)?.text
        )

        // 同一行：那颗芯片与那颗输入框的垂直中心一致（旧形状是输入一行、按钮另起一行）
        val chips = probe.laid(
            rule.onAllNodes(hasTestTag(LbTags.CHIP) and hasText(intentSaveName)).fetchSemanticsNodes().map { probe.of(it) }
        )
        assertEquals("「$intentSaveName」那颗芯片恰一颗", 1, chips.size)
        val save = chips.single()
        val field = probe.of(fields.single())
        assertEquals(
            "输入与保存不在同一行：${field.describe()} / ${save.describe()}",
            field.topDp + field.heightDp / 2f,
            save.topDp + save.heightDp / 2f,
            ROW_CENTER_TOLERANCE_DP
        )
        // 页级控件那一档：那颗按钮自己到全站下限，不是把紧凑件再套一圈大方盒
        assertTrue("「$intentSaveName」不到 ${TouchTier.SITE_FLOOR.toInt()}dp：" + save.describe(), !save.tooSmall(TouchTier.SITE_FLOOR))

        // 开着 = 只多那一颗「保存」；互斥单选那一族一颗都不许多（期限三档不许回来）
        assertEquals("开着的时候整页芯片 = 两排分段 + 那一颗保存", SELECTED_SEGMENT_COUNT + 1, chipCount())
        assertEquals("互斥单选那一族仍只有两排", SELECTED_SEGMENT_COUNT, tabCount())
        assertTrue("画成一格不等于写盘：实到 $writes", writes.isEmpty())
    }

    /**
     * 拨一下开关只交**一笔**，交的是"正文原样 + enabled=true"这两件，不多交一件；
     * 宿主那份读数没跟上之前屏上不许先出现输入行（不预涂成成功）。
     *
     * 替代关系：旧格 `the first toggle only raises the intro and writes nothing until it is
     * acknowledged` 与 `a relaunch after the acknowledged intro does not repeat it` 钉的是
     * "首次开启先弹介绍、确认才启用"。介绍那一层整颗退役之后，这一格接着看同一件事的另一半：
     * 拨开关那一次到底交了几笔、交的是什么。
     */
    @Test
    fun `turning the intent on hands over exactly one write`() {
        val enabled = mutableStateOf(false)
        val writes = mutableListOf<IntentWrite>()
        mountIntent(writes = writes, intentEnabled = enabled, intentText = mutableStateOf("先约她看电影"))

        rule.onAllNodes(hasContentDescription(intentSwitchName))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "拨开只交一笔：正文原样、enabled=true，除了这两件不多交任何一件",
            listOf(IntentWrite("先约她看电影", true)), writes.toList()
        )
        assertEquals("宿主还没落定之前不许先把输入行画出来（不预涂）", 0, nodeCount(intentSaveName))

        rule.runOnIdle { enabled.value = true }
        rule.waitForIdle()
        assertEquals("落定之后那一行才在场", 1, nodeCount(intentSaveName))
        assertEquals("落定那一步不等于又写一笔", 1, writes.size)
    }

    /**
     * 「不能把 A 的意图展示或写给 B」：这一稿正文是在 A 上打的，切库落到 B 之后
     * 不许把 A 的正文写给 B；而挡下那一次之后，等新库那份数据上了屏，下一记必须还写得出去
     * （否则这一格就退化成"永远静默"，那是另一种假成功）。
     *
     * ⚠ 这是**保留**的真实证人（切库安全），只是把"按哪一颗"从旧的那三档期限芯片换成那颗「保存」：
     * 期限那一排随新合同退役，判据本身一个字没松。
     */
    @Test
    fun `a draft typed on one library is not written to another`() {
        val writes = mutableListOf<IntentWrite>()
        val activeKb = mutableStateOf<String?>("kbA")
        val bodyOfActiveKb = mutableStateOf("")
        mountIntent(
            writes = writes,
            intentEnabled = mutableStateOf(true),
            intentText = bodyOfActiveKb,
            activeKb = activeKb
        )

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("先约她")
        rule.waitForIdle()
        assertEquals("打字本身不落盘（不逐键写盘、不失焦写盘）", 0, writes.size)

        // 切库那一个窗口：屏幕上这块库已经是 kbB，意图正文那份还没跟上
        rule.runOnIdle { activeKb.value = "kbB" }
        rule.waitForIdle()

        rule.onAllNodes(hasText(intentSaveName))[0].performClick()
        rule.waitForIdle()
        assertTrue("切库之后不许把 A 那一稿写给 B：实到 $writes", writes.isEmpty())

        // B 那份数据上了屏（草稿随之交回屏幕上那一份）：接着按保存必须真的写得出去，而且写的是 B 那条
        rule.runOnIdle { bodyOfActiveKb.value = "换个节奏聊" }
        rule.waitForIdle()
        rule.onAllNodes(hasText(intentSaveName))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "挡一次不等于这一格从此静默：写的是屏幕上这一块库那一条",
            listOf(IntentWrite("换个节奏聊", true)),
            writes.toList()
        )
    }

    /**
     * 切库那一行：屏上那个名字**只跟着宿主那份真状态走**。
     *
     * 替代关系（旧断言针对的是已废弃的产品规则，所以这一格整颗重写而不是留着）：
     * 旧那一版数的是"卡片标题一颗 + 活动行一颗"两句「当前使用」，因为它把所有知识库永久逐条
     * 铺在设置页上、每行各自带一颗活动标记。现在默认只有一行（左「当前知识库」右「库名 + 箭头」），
     * 「当前使用」那一颗只存在于用户主动点开的那一列里 ⇒ 旧那两句计数在这一页没有对应主体。
     * 新语义里同一条判据读的是**名字**：
     * · 默认状态另一块库根本不在树上（不许永久占着这一页）；
     * · 点开 → 点非当前那一颗 ⇒ 只投一次切库请求，而屏上那个名字仍是旧的那一个
     *   （切库落盘之前选中态不属于这一页，先涂就是"失败也说成功"）；
     * · `runOnIdle` 把宿主那份换成 kbB 之后名字才跟着换，且整页只指着一块库。
     * 「正在用的那一颗点不动」与那一列自己的热区/禁用语义，由 `SettingsKbSwitcherEntryTest`
     * 逐颗量（这一格挂的是整页，读那一列要穿过滚动与浮层两件事）。
     */
    @Test
    fun `the current library row repaints its name only after the switch lands`() {
        val writes = mutableListOf<IntentWrite>()
        val switches = mutableListOf<String>()
        val activeKb = mutableStateOf<String?>("kbA")
        mountIntent(writes = writes, switches = switches, activeKb = activeKb)
        val label = ctx.getString(R.string.settings_kb_current_label)

        assertEquals("这一格只交出一行", 1, kbRowNodes())
        assertEquals("行标签仍说得出这一行在管什么", 1, nodeCount(label))
        assertEquals("没点开之前，另一块库不许占着设置页", 0, nodeCount("kbB"))
        assertEquals("那一行此刻念的名字", "kbA", kbShownName())

        rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG))[0].performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasText("kbB"))[0].performClick()
        rule.waitForIdle()

        assertEquals("点非当前那一颗只投一次切库请求", listOf("kbB"), switches.toList())
        assertEquals(
            "切换还没落地（宿主那份没换＝失败那一路）：名字不许先涂成成功",
            "kbA",
            kbShownName()
        )

        rule.runOnIdle { activeKb.value = "kbB" }
        rule.waitForIdle()
        assertEquals("落地之后名字才跟着换", "kbB", kbShownName())
        assertEquals("同一时刻不许两块库都被指着说当前", 0, nodeCount("kbA"))
        assertTrue("这一格不替切库写第二本账：实到 $writes", writes.isEmpty())
    }

    /**
     * 收起态那一行的颗数（按**行根** tag 定位：库名会跟着切换变，tag 不会）。
     * 行根只管版式下限，点击不挂在它身上——挂在"名字+箭头"那一段（原型那颗 `.kb` 文字按钮）。
     */
    private fun kbRowNodes(): Int =
        rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG)).fetchSemanticsNodes().size

    /**
     * 那一行此刻念出来的**库名**。读的是行根那一颗**合并之后**的文案：行根就是那颗展开触发
     * （整行点得着），所以标签、库名与箭头三串字都并进同一颗节点——这一份读数就是手指与读屏
     * 实际信的那一颗。剔掉不随切换变的那两样（行标签与箭头字形），剩下的就是库名，恰一条；
     * 剩下零条或多条都当场报错，把实到的整份读数一起交出去。
     */
    private fun kbShownName(): String {
        val node = rule.onAllNodes(hasTestTag(SETTINGS_ROW_KB_TAG)).fetchSemanticsNodes()
            .singleOrNull() ?: error("收起态那一行的展开触发不在树上")
        val label = ctx.getString(R.string.settings_kb_current_label)
        val texts = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
        // 箭头是单字符的排印符号（收起 › / 展开 ⌄，不随语言走），按"一个字"剔掉而不是照抄字形：
        // 抄字形就等于把判据绑在那两个码点上，哪天换符号就悄悄不成立了。
        return texts.filterNot { it == label || it.length == 1 }.singleOrNull()
            ?: error("那一行没念出唯一的库名，合并文案实到 $texts")
    }

    /**
     * 这一页上**互斥单选**那一族的颗数（`Role.Tab`）。
     *
     * 本仓依赖里没有 `hasRole` 这颗现成匹配器，按 `ScrollScan` 那一条先例自己写：
     * 读节点上的 Role 语义键，等于 Tab 才算一颗互斥单选档。
     */
    private fun tabCount(): Int = rule.onAllNodes(
        SemanticsMatcher("role=Tab") {
            it.config.contains(SemanticsProperties.Role) && it.config[SemanticsProperties.Role] == Role.Tab
        }
    ).fetchSemanticsNodes().size

    /**
     * 「这一页现在只有两排互斥单选芯片」那颗读数证人：图标大小三档 + 回复卡片两档。
     *
     * 替代关系：旧格 `saving or completing an intent does not restart its period` 数的是
     * "期限那一排三颗 + 图标大小那一排三颗 = 六颗 Role.Tab"，用它证明「已经完成」没有被塞回
     * 期限那一排当第四个期限（旧那一格还按标签逐排钉过两排，现在那一半由
     * `SettingsBubbleSizeEntryTest` 与 `SettingsReplyLayoutEntryTest` 各自逐颗看着）。
     * 意图那一排的三档期限与那颗「已完成」随新合同整颗退役 ⇒ 同一把尺现在量的是**剩下的两排**：
     * 图标大小 3 + 回复卡片 2 = 5 颗。有人把期限加回来（8 颗）、把某档塞进别排（6 颗）、
     * 大小那一排没画（2 颗）都在这里红；而"芯片总数 = Tab 总数"那一句额外挡掉
     * **非互斥单选**那一颗（旧「已完成」/「保存」那种 Action 芯片在关着的时候也不许在）。
     */
    @Test
    fun `the page carries exactly two segmented rows`() {
        mount()
        assertEquals("全页互斥单选芯片恰五颗＝图标大小三颗 + 回复卡片两颗", SELECTED_SEGMENT_COUNT, tabCount())
        assertEquals(
            "全页芯片颗数必须与互斥单选那一族相等（关着的时候意图那一格一颗芯片都不该贡献）",
            SELECTED_SEGMENT_COUNT, chipCount()
        )
    }

    companion object {
        /**
         * 这一族紧凑件交出的短边（模式栏内部字形 20dp、MiniSwitch 36×20 同一档）：
         * 透明度滑杆的**轨道**就量这一档，别拿它去量页面主体，也别把它当成"撤掉了下限"。
         */
        private const val COMPACT_TRACK_MIN_HEIGHT_DP = 20f

        /** 可拖控件的长边下限：横向滑不出这距离就等于这一栏没有档位可挑 */
        private const val DRAGGABLE_MIN_WIDTH_DP = 48f

        /**
         * 这一页**互斥单选**那一族的颗数：图标大小三档 + 回复卡片两档。
         * 由本文件那两格现数（`the page carries exactly two segmented rows` 判总数、
         * `the intent row when on …` 判"开着只多一颗芯片"），
         * 意图那一格退役后不该再有第三排。
         */
        private const val SELECTED_SEGMENT_COUNT = 5

        /** 整页隔着滚动柱量两颗控件的垂直中心：留一像素半的舍入余量 */
        private const val ROW_CENTER_TOLERANCE_DP = 1.5f
    }
}
