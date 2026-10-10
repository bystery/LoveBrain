package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
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
 * 这一格只管三件"看界面看不出来、坏了也没人喊"的事：
 * 1. 每一颗可点节点**自己**够不够它那一档的下限：整页样本走全站下限，透明度滑杆那一颗走
 *    这一族紧凑件那一档（20dp 短边 + 48dp 可拖长边，理由写在那一格的 KDoc 里）。
 *    **透明度滑杆不在那份"可交互"样本里**：样本只认 click/toggle/disabled，而滑杆交出的是
 *    进度语义 ⇒ 它按 TestTag 单独定位，量仍然用同一把尺（[SemanticsProbe.of] /
 *    `tooSmall` / `assertTargetsMeetFloor`）；同一颗的读屏名字也在这一条里判。
 * 2. 「返回」在**视口很矮**时是否仍然在屏幕上——它钉在滚动柱之外，这一条判的就是这件事；
 * 3. 挂在树上的那一个百分比读数必须是**本次**那一份值（不许抄旧值、也不许再画第二份）；
 * 4. 意图那一格的三条看不见的合同：§11.2 首次开启"先介绍、确认才启用、取消保持关闭、
 *    确认过就不再重复"，§11.2 点名的**挂载层级**（遮罩要盖住这一页，不许挤进表单布局），
 *    以及 §11.3 的「切库不许把 A 的意图写给 B」与「已到期不许塌成一片空区域」。
 *    浮层那一半只挂生产那颗 [LoveBrainSettingsContent]（浮层的主人现在在页面根部），
 *    只挂单量那一格就量不到遮罩到底盖住了谁。
 *
 * 旧版本里还有两条判据（超时那一行"一行四档 + 恰有一档 Selected"、捕获范围"打开后复用首页那一段
 * 选择器并把勾选投回调用方"）。它们随**整窗设置页里的那两栏**一起撤掉：用户 2026-10-03 原话
 * "设置里面暂时先弄一个调透明度的，别的都不要弄"。那一页现在这一格只交透明度与页头那几条
 * （意图与知识库那两格由下面 `mountIntent` 那一路交，各自点名），不需要的参数留默认值。
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
                    onReplyCardVerticalChange = {},
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
     * ⚠ **这一档作废了"整页可交互样本一律 ≥48"这一句原判据**（2026-10-06，S1b，用户原话第 17 条）：
     * 旧形制那一页拿的是 `LbTopBarLevel.Page`，返回那颗自带 48 见方热区（`LbTopBar.kt` 的
     * `BackControl` = 22dp 字形 / 48 见方），于是"整页可交互样本按全站 48 量"当时是对的——那一页
     * 唯一的可交互件就是那颗 48 的返回。本轮把齿轮设置页降到 F3 紧凑族：行高 30、`titleMedium`15、
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

    /**
     * 原话第 17 条的后半：「切进设置页之后**收不起窗**」。
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
     * 反向证人：`onCollapse` 从来没被调用过的那一版实现（今天改前的形状）在这格就是 0。
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

    // ═══════════ 意图那一格：§11.2 的首次开启与挂载层级、§11.3 的六档 ═══════════

    /** 写口那一次交出去的四件（正文／启用／有效期／是否按此刻重算期限）——这一族只有一条写口 */
    private data class IntentWrite(
        val text: String,
        val enabled: Boolean,
        val expiry: IntentExpiry,
        val recomputeExpiry: Boolean
    )

    private val introBodyName: String get() = ctx.getString(R.string.intent_intro_body)
    private val introAcknowledgeName: String get() = ctx.getString(R.string.intent_intro_acknowledge)
    private val intentSwitchName: String get() = ctx.getString(R.string.intent_label)
    private val periodRowLabel: String get() = ctx.getString(R.string.intent_expiry_label)

    /** 「已经看过介绍」那一条记录的唯一主人是盘（不是 `remember`），测前按每一格要的那一档摆好 */
    private fun putIntroRecord(seen: Boolean) {
        val editor = ctx.getSharedPreferences(IntentIntroRecord.PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (seen) editor.putBoolean(IntentIntroRecord.INTRO_SEEN_KEY, true) else editor.remove(IntentIntroRecord.INTRO_SEEN_KEY)
        editor.commit()
    }

    private fun kbLibrary(name: String) =
        KnowledgeBase(name = name, displayName = name, updatedAt = "2026-10-08T09:00:00+08:00", active = true)

    /**
     * 意图那一格的挂载：整页挂生产那颗 [LoveBrainSettingsContent]（不挂单格——介绍浮层的
     * 主人现在在页面根部，只挂那一格就量不到遮罩到底盖住了谁）。
     */
    private fun mountIntent(
        writes: MutableList<IntentWrite>,
        matrix: UiMatrix = UiMatrix(360, heightDp = 900),
        intentEnabled: Boolean = false,
        intentText: String = "",
        intentTextState: MutableState<String>? = null,
        intentExpiry: IntentExpiry = IntentExpiry.ONE_DAY,
        intentStatus: IntentStatus = IntentStatus.ACTIVE,
        intentExpiryDate: String = "",
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
                    intentEnabled = intentEnabled,
                    // 状态那份存在时读状态：切库之后"这一格现在显示哪块库的哪条意图"要能当场翻过来
                    intentText = intentTextState?.value ?: intentText,
                    intentExpiry = intentExpiry,
                    onIntentChange = { text, enabled, expiry, recompute ->
                        writes += IntentWrite(text, enabled, expiry, recompute)
                    },
                    knowledgeBases = knowledgeBases,
                    activeKbName = activeKb.value,
                    onSwitchKb = { name -> switches += name },
                    intentStatus = intentStatus,
                    intentExpiryDate = intentExpiryDate
                )
            }
        }
        rule.waitForIdle()
    }

    private fun introNodes() = rule.onAllNodes(hasText(introBodyName)).fetchSemanticsNodes().size

    /**
     * §11.2 第一次那一遍：拨开关只把介绍浮层挂起来，确认之前不启用、不落盘、不展开正文；
     * 「知道了」那一句交的仍是既有的启用那一记（正文=屏幕上那一份、enabled=true、没换档）。
     */
    @Test
    fun `the first toggle only raises the intro and writes nothing until it is acknowledged`() {
        putIntroRecord(seen = false)
        val writes = mutableListOf<IntentWrite>()
        mountIntent(writes = writes)
        assertEquals("没拨开关之前不该有介绍浮层", 0, introNodes())

        rule.onAllNodes(hasContentDescription(intentSwitchName))[0].performClick()
        rule.waitForIdle()

        assertEquals("首次拨开该起介绍浮层", 1, introNodes())
        assertTrue("确认之前一个字都不许落盘（§11.2）：实到 $writes", writes.isEmpty())
        assertEquals(
            "确认之前不展开有效期与正文那一区（§11.2「确认后才正式启用并展开内容」）",
            0, rule.onAllNodes(hasText(periodRowLabel)).fetchSemanticsNodes().size
        )

        rule.onAllNodes(hasText(introAcknowledgeName))[0].performClick()
        rule.waitForIdle()

        assertEquals(
            "「知道了」落的那一句一个字没动：当前正文、enabled=true、没换档",
            listOf(IntentWrite("", true, IntentExpiry.ONE_DAY, false)), writes.toList()
        )
        assertEquals("确认之后浮层收起", 0, introNodes())
        assertTrue("「已经确认过」必须落到盘上，不然下一次启动又重弹一遍", IntentIntroRecord.seen(ctx))
    }

    /**
     * §11.2 点名的挂载判据：遮罩得盖住这一页，浮层不许挤进表单布局。
     *
     * 牙齿在这一句：介绍浮层起来之后，用**真指针**点页头那颗「返回」。
     * · 遮罩挂在页面根部最后一层 → 这一记被遮罩接走（走既有的关闭出口），`onBack` 一次都不响；
     * · 旧形状（浮层画在那一格里面、坐在滚动柱里）→ 遮罩只剩浮层自己那一块，
     *   这一记穿到「返回」上 → `backs` 变 1、浮层还挂着 → 这一格当场红。
     * 这是这一族唯一能在 JVM 上量到"盖住没盖住"的写法：`performClick` 走语义动作、绕过命中测试，
     * 量不出遮挡，所以这里用 `performTouchInput`。
     */
    @Test
    fun `the intro scrim covers the page instead of taking a slot in the form`() {
        putIntroRecord(seen = false)
        var backs = 0
        val writes = mutableListOf<IntentWrite>()
        mountIntent(writes = writes, onBack = { backs++ })
        rule.onAllNodes(hasContentDescription(intentSwitchName))[0].performClick()
        rule.waitForIdle()
        assertEquals("先把介绍浮层起来", 1, introNodes())

        rule.onNodeWithContentDescription(backName).performTouchInput { down(center); up() }
        rule.waitForIdle()

        assertEquals("遮罩没盖住页头时这一记会穿到「返回」上——那正是要修的挂载层级", 0, backs)
        assertEquals("点遮罩就是既有的那条关闭出口", 0, introNodes())
        assertTrue("关闭那一条不写盘、不启用（§11.2「取消或返回保持关闭」）：实到 $writes", writes.isEmpty())
        assertEquals(
            "取消之后开关仍然关着（不残留半开的表单）",
            0, rule.onAllNodes(hasText(periodRowLabel)).fetchSemanticsNodes().size
        )

        // 反向证人：同一颗指针、同一个坐标，遮罩退了之后这一记就真交得出去——
        // 上面那句 `backs == 0` 不是"什么输入都没生效"读出来的假绿。
        rule.onNodeWithContentDescription(backName).performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals("浮层关掉之后页头那颗返回恢复接点", 1, backs)
    }

    /**
     * §11.2「已经确认过介绍后，再次启用无需重复介绍」：这一条读的是盘上那份记录，
     * 所以换一次挂载（等价于收起面板再进来、甚至重启应用）也不重弹。
     */
    @Test
    fun `a relaunch after the acknowledged intro does not repeat it`() {
        putIntroRecord(seen = true)
        val writes = mutableListOf<IntentWrite>()
        mountIntent(writes = writes)

        rule.onAllNodes(hasContentDescription(intentSwitchName))[0].performClick()
        rule.waitForIdle()

        assertEquals("已经确认过介绍，再次启用不重复介绍", 0, introNodes())
        assertEquals(
            "直接按既有契约启用那一句",
            listOf(IntentWrite("", true, IntentExpiry.ONE_DAY, false)), writes.toList()
        )
    }

    /**
     * §11.3「不能把 A 的意图展示或写给 B」：这一稿正文是在 A 上打的，切库落到 B 之后
     * 不许把 A 的正文写给 B；而挡下那一次之后，等新库那份数据上了屏，下一记必须还写得出去
     * （否则这一格就退化成"永远静默"，那是另一种假成功）。
     */
    @Test
    fun `a draft typed on one library is not written to another`() {
        putIntroRecord(seen = true)
        val writes = mutableListOf<IntentWrite>()
        val activeKb = mutableStateOf<String?>("kbA")
        val bodyOfActiveKb = mutableStateOf("")
        mountIntent(
            writes = writes,
            intentEnabled = true,
            intentTextState = bodyOfActiveKb,
            activeKb = activeKb
        )

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("先约她")
        rule.waitForIdle()
        assertEquals("打字本身不落盘（不逐键写盘、不逐键续期）", 0, writes.size)

        // 切库那一个窗口：屏幕上这块库已经是 kbB，意图正文那份还没跟上
        rule.runOnIdle { activeKb.value = "kbB" }
        rule.waitForIdle()

        rule.onAllNodes(hasText(ctx.getString(R.string.intent_expiry_one_hour)))[0].performClick()
        rule.waitForIdle()
        assertTrue("切库之后不许把 A 那一稿写给 B：实到 $writes", writes.isEmpty())

        // B 那份数据上了屏（草稿随之交回屏幕上这一份）：接着换档必须真的写得出去，而且写的是 B 那条
        rule.runOnIdle { bodyOfActiveKb.value = "换个节奏聊" }
        rule.waitForIdle()
        rule.onAllNodes(hasText(ctx.getString(R.string.intent_expiry_one_week)))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "挡一次不等于这一格从此静默：写的是屏幕上这一块库那一条",
            listOf(IntentWrite("换个节奏聊", true, IntentExpiry.ONE_WEEK, true)),
            writes.toList()
        )
    }

    /**
     * §11.3 第五行「已到期」：自动到期那条链把开关一起写成了关，这一格不许因此塌成一片空区域——
     * 正文与期限仍要看得见，开关读着是关（不再注入请求那一侧的真状态），拨开就是重新启用。
     */
    @Test
    fun `an expired intent keeps its body instead of collapsing to an empty row`() {
        putIntroRecord(seen = true)
        val writes = mutableListOf<IntentWrite>()
        mountIntent(
            writes = writes,
            intentEnabled = false,
            intentText = "先约她看电影",
            intentStatus = IntentStatus.EXPIRED,
            intentExpiryDate = "2026-10-08 09:00"
        )

        rule.onAllNodes(hasContentDescription(intentSwitchName))[0].assertIsOff()
        assertEquals("已到期那一格仍该有正文区（不残留禁用的大空区域 ≠ 把内容整块撤掉）",
            1, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals("期限那一行要读得出这条什么时候到的",
            1, rule.onAllNodes(hasText("2026-10-08 09:00")).fetchSemanticsNodes().size)

        // 重新启用：拨开就是启用，介绍不再重弹
        rule.onAllNodes(hasContentDescription(intentSwitchName))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "重新启用那一句交的是既有契约（正文原样、enabled=true、重算由宿主那颗判据说）",
            listOf(IntentWrite("先约她看电影", true, IntentExpiry.ONE_DAY, false)), writes.toList()
        )
    }

    /**
     * §11.3 第四行「有效」与「已经完成不是第四个期限」：三颗期限 + 下面那行操作（保存／完成），
     * 而且保存与完成都不重算期限——重算只跟着"重新启用／重新选了一段时间"那两件事。
     *
     * ⚠ 这一格量的是**芯片那颗节点**，不是"屏幕上那串字逐字等于期限名"。原因写在
     * [periodChipCount] 那里：`LbChip` 给选中那一颗的标签前缀一个对勾（`✓ 一天`），
     * 按整串字面量等值去找会在**恰好选中那一档**上读出 0——三颗明明都在屏上，那是量具瞎，
     * 不是实现没画（同一坑与同一修法的前例：`ui/home/ProviderFormSemanticsTest.kt` 的超时四档那一格）。
     * 判据一件没松，现在钉的是三件：
     * ① 三个期限名各命中**恰一颗芯片**（按设计系统那颗芯片自己的锚点 `LbTags.CHIP` 定位，
     *    页面上任何一段普通文字都冒充不了"一颗芯片"）⇒ 挪走一排、少一颗、多一颗同名档都红；
     * ② 全页**互斥单选**那一族（`Role.Tab`）恰六颗，并且按标签把两排各钉成"恰三颗"
     *    （期限那一排＋2026-10-10 新增的图标大小那一排）⇒「已经完成」被塞回期限这一排当
     *    第四个期限 = 7 颗、期限被吞掉一档 = 5 颗、大小那一排没画 = 3 颗，三种都红；
     * ③ 下面那两句写口判据（保存不重算期限、完成重算并落到 `IntentExpiry.COMPLETED`）一个字没动。
     */
    @Test
    fun `saving or completing an intent does not restart its period`() {
        putIntroRecord(seen = true)
        val writes = mutableListOf<IntentWrite>()
        mountIntent(
            writes = writes,
            intentEnabled = true,
            intentText = "先约她看电影",
            intentExpiry = IntentExpiry.ONE_DAY,
            intentExpiryDate = "2026-10-09 21:00"
        )
        // 三颗期限，不多不少（「已经完成」从这一排搬走了）
        val periods = listOf(
            R.string.intent_expiry_one_hour,
            R.string.intent_expiry_one_day,
            R.string.intent_expiry_one_week
        ).map { ctx.getString(it) }
        periods.forEach { label ->
            assertEquals(
                "期限那一排该有「$label」恰一颗芯片（选中那一档带「✓ 」前缀，所以认芯片与子串，不认整串字面量）",
                1, periodChipCount(label)
            )
        }
        sizeRowLabels().forEach { label ->
            assertEquals(
                "「悬浮图标大小」那一排该有「$label」恰一颗芯片（2026-10-10 §1 新增的那一排）",
                1, sizeChipCount(label)
            )
        }
        assertEquals(
            "全页互斥单选芯片恰六颗＝期限那一排三颗 + 图标大小那一排三颗；" +
                "期限被吞掉一档=5 颗、「已经完成」被塞回这一排=7 颗、大小那一排没画=3 颗，三种都红",
            6, rule.onAllNodes(
                // 本仓依赖里没有 `hasRole` 这颗现成匹配器，按 `ScrollScan` 那一条先例自己写：
                // 读节点上的 Role 语义键，等于 Tab 才算一颗互斥单选档；
                // 「已经完成」挂的是 Role.Button，混不进这一族（混进来就变 7 颗，当场红）。
                // 2026-10-10：这一页现在有**两排**单选芯片（期限＋图标大小），所以整页读数从 3 变 6；
                // 直接把 3 改成 6 会丢牙齿（期限少一颗、大小那排被删光，两种都还是 6）⇒
                // 上面那两组"按标签各恰一颗"把两排分别钉住，这一句只兜"全页不许多长出第三排"。
                SemanticsMatcher("role=Tab") {
                    it.config.contains(SemanticsProperties.Role) &&
                        it.config[SemanticsProperties.Role] == Role.Tab
                }
            ).fetchSemanticsNodes().size
        )

        rule.onAllNodes(hasText(ctx.getString(R.string.intent_save)))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "「保存」只交正文，不重算期限",
            listOf(IntentWrite("先约她看电影", true, IntentExpiry.ONE_DAY, false)), writes.toList()
        )

        writes.clear()
        rule.onAllNodes(hasText(ctx.getString(R.string.intent_expiry_completed)))[0].performClick()
        rule.waitForIdle()
        assertEquals(
            "「完成」是结束这条意图的动作：有效期落到 COMPLETED、正文原样",
            listOf(IntentWrite("先约她看电影", true, IntentExpiry.COMPLETED, true)), writes.toList()
        )
    }

    private fun nodeCount(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /**
     * 期限那一排那一颗芯片的颗数：**认芯片 + 认名字里的子串**，不认整串字面量。
     *
     * 为什么不能像别处那样直接按字面量等值找：`LbChip` 在**选中**那一颗的标签前面加一个对勾
     * （`core/designsystem/LbChip.kt` 里 `LB_CHIP_CHECK + label`，`LbChipStyles.filled` 的
     * `markSelectedWithCheck` 默认开着），于是"当前是哪一档"那一颗在语义树上的名字是「✓ 一天」。
     * 按整串等值去找 ⇒ 偏偏**选中那一档**读成 0（本格第一版就红在这里，`expected:<1> but was:<0>`），
     * 而三颗期限一个都没少画。这一族的坑与写法已有前例：`ui/home/ProviderFormSemanticsTest.kt`
     * 的超时四档那一格同样注明"子串匹配：选中那颗的名字带对勾前缀"。
     *
     * 定位仍然有牙：`LbTags.CHIP` 是设计系统那颗芯片自己的锚点（只挂在带语义那一层），
     * 所以这一句数的是"期限那一排的芯片"，页面上多写一段同样的文字、或把期限改成一行纯文本，
     * 都数不出 1。
     */
    /**
     * 「悬浮图标大小」那一排（2026-10-10 §1）的三颗标签。
     * 与期限那一排共用同一颗芯片锚点 `LbTags.CHIP`，所以按标签数、不按整页颗数判这一排。
     */
    private fun sizeRowLabels(): List<String> = listOf(
        R.string.settings_bubble_size_small,
        R.string.settings_bubble_size_standard,
        R.string.settings_bubble_size_large
    ).map { ctx.getString(it) }

    /**
     * 「悬浮图标大小」那一排按**整串等值**数，不用子串。
     *
     * 子串那一把在这一排上会多数一颗：期限里「一小时」那档也含"小"这个字，
     * 于是 `hasText("小", substring = true)` 在期限排与大小排各命中一次＝2 颗（本轮实测撞过）。
     * 分段档 `markSelectedWithCheck = false` ⇒ 选中那颗不带「✓ 」前缀，等值匹配不会漏认。
     */
    private fun sizeChipCount(label: String) =
        rule.onAllNodes(hasTestTag(LbTags.CHIP) and hasText(label)).fetchSemanticsNodes().size

    private fun periodChipCount(label: String) =
        rule.onAllNodes(hasTestTag(LbTags.CHIP) and hasText(label, substring = true))
            .fetchSemanticsNodes().size

    /**
     * §11.1 切库那一条：点下去只把请求交出去，**选中态只跟着宿主那份真状态走**。
     *
     * "当前使用"那几个字在这一格有两个主人位：卡片标题那一行 + 活动库那一行，所以全场恰有两颗。
     * 牙在中间那两句：
     * · 点非活动那一行 ⇒ 只投一次切库请求，而屏幕上仍然只有两颗"当前使用"（活动标记没跟着手指走）；
     *   谁要是给这一格加一颗本地乐观态（点下去就先涂自己），那里就长出第三颗 ⇒ 当场红——
     *   那正是"失败也假成功"的形状（切库落盘之前，选中态不属于这一页）。
     * · 活动那一行点不动（`enabled = !isActive`）⇒ 再点一次不许多投一句；
     * · 最后 `runOnIdle` 把宿主那份换成 kbB，"当前使用"仍然只有两颗：成功之后才换人，
     *   而不是同时指着两块库。
     */
    @Test
    fun `a library row does not repaint itself as the current one before the switch lands`() {
        val writes = mutableListOf<IntentWrite>()
        val switches = mutableListOf<String>()
        val activeKb = mutableStateOf<String?>("kbA")
        mountIntent(writes = writes, switches = switches, activeKb = activeKb)
        val currentUse = ctx.getString(R.string.kb_card_in_use)

        assertEquals("标题一颗 + 活动行一颗", 2, nodeCount(currentUse))

        rule.onAllNodes(hasText("kbB"))[0].performClick()
        rule.waitForIdle()
        assertEquals("点非活动那一行只投一次切库请求", listOf("kbB"), switches.toList())
        assertEquals("选中态不许跟着手指走：宿主还没换，标记仍只指着那块库", 2, nodeCount(currentUse))

        rule.onAllNodes(hasText("kbA"))[0].performClick()
        rule.waitForIdle()
        assertEquals("正在用的那一行点不动，不许再投一句", listOf("kbB"), switches.toList())

        rule.runOnIdle { activeKb.value = "kbB" }
        rule.waitForIdle()
        assertEquals("切成功之后标记跟着换，但同一时刻仍然只有一个当前库", 2, nodeCount(currentUse))
        assertTrue("这一格不替切库写第二本账：实到 $writes", writes.isEmpty())
    }

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
