package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.service.FloatingService.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * **停止入口一致**那一族的界面半边：设置页里那颗「悬浮助手」开关，在语义树上必须表现为
 * **唯一一颗能停服务的界面入口**，并且读的是真实窗口状态，而不是页面自己的一份状态。
 *
 * 旧形状里这里曾有**两个**同义入口（开关 + 尾部那颗「关闭悬浮助手」按钮，都投同一条关闭回调）。
 * 那是已废弃的产品规则：同一件事两个入口，用户反而看不清哪颗真能停服务。现在的判据反过来钉——
 * "能停服务的入口恰好一个"：整格里只有一颗可点节点（那颗开关），关闭动作的文案在屏上一颗都不许出现。
 *
 * 2026-10-12 原型形状那一半也钉在这里：这一行**不再借 `LbSettingRow`**（那颗的
 * `titleMedium` 粗体让"悬浮助手"在同一页里比别的行大一档——10-11 那一版"根本不一样"的成因之一），
 * 行壳走族里的 `settingsEntryRow()`、行标签走整页唯一那颗 `LbRowLabel()`，
 * 状态字按原型 `.assist-state` 取 `labelSmall` + `TextHint`；行根挂跨席锚点
 * `settings-row-assistant`，整行不接点击。
 *
 * 与 `FloatingServiceStopEntryConsistencyTest`（形状半边）的分工：
 * 那一族钉"三颗入口（主页停止动作、通知栏停止、设置页开关）的最终动作只有一颗、清理路径没被绕过、
 * 没有新键"；这一族钉"屏幕画出来的那颗开关读的是真实 `WindowState`、拨下去只投一次回调、
 * 不乐观翻状态、且这一格不再长出第二颗关闭入口"。两族合起来才是"入口一致"这件事——
 * 单独任何一族都能被绕过（只看形状的话，页面可以把开关涂成永远亮着；只看语义树的话，
 * 宿主可以把关闭接到隐藏窗口上而屏幕上看不出来）。
 *
 * 读数全部来自 `stringResource`/`getString`：这台机器环境解析出来是英文那一套时写死中文会假红
 * （同 `SettingsPageSemanticsTest` 文件头那条仪器教训）。
 *
 * ⚠ 这一族**没有**任何"关闭之后屏幕上是不是真的没了"的断言：语义树里没有第二扇 overlay 窗口可查，
 * 面板/球/通知的同时消失、重开不出现两颗球，属**需真机**那一半（登记在
 * `FloatingServiceStopEntryConsistencyTest` 最后一格）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAssistantEntryStopConsistencyTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private val switchName: String get() = ctx.getString(R.string.settings_assistant_label)
    private val retiredCloseName: String get() = ctx.getString(R.string.settings_assistant_close)
    private val groupName: String get() = ctx.getString(R.string.settings_group_assistant)
    private val runningState: String get() = ctx.getString(R.string.assistant_state_running)
    private val stoppedState: String get() = ctx.getString(R.string.assistant_state_stopped)

    /** 挂载就把窗口状态交出去（翻档由调用点在同一棵已挂载的树上做；第二次 `setContent` 会被框架当场拒掉） */
    private fun mount(
        window: MutableState<WindowState>,
        enables: MutableList<Unit>,
        closes: MutableList<Unit>,
        matrix: UiMatrix = UiMatrix(360, heightDp = 900)
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                SettingsAssistantEntry(
                    // 与主页那颗灯同一个来源：宿主交下来的是 `FloatingService.isAssistantOn(真实状态)`，
                    // 这一格不给默认值、不留第二本账（判据在形状半边的 `no new persisted flag...` 那一格）。
                    assistantOn = FloatingService.isAssistantOn(window.value),
                    onRequestEnable = { enables += Unit },
                    onRequestClose = { closes += Unit }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun switchNode() = rule.onAllNodes(hasContentDescription(switchName))

    /** 已拆掉的重复关闭入口：它的文案在屏上任何一档状态都不许出现，出现就是旧形状长回来 */
    private fun retiredCloseCount() =
        rule.onAllNodes(hasText(retiredCloseName)).fetchSemanticsNodes().size

    private fun textCount(text: String) =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    /**
     * 开关的亮与灭**逐档**跟着真实 `WindowState` 走。
     *
     * 回成什么样子会红：
     * · 页面自己 `remember` 一颗布尔（或给 `assistantOn` 一个默认值）→ 翻到 STOPPED 之后屏幕仍亮着，
     *   那一句 `assertIsOff` 当场红；
     * · 把 `TEMP_HIDDEN` 当成"已关闭"（换算写成只认球与面板两档）→ TEMP_HIDDEN 那一步红；
     * · 再画第二颗开关（比如顶部塞一排新图标里也有一颗）→ 那句「只许一颗开关」红。
     */
    @Test
    fun `the switch follows the real window state through every state`() {
        val window = mutableStateOf(WindowState.VISIBLE_BUBBLE)
        mount(window, mutableListOf(), mutableListOf())
        assertEquals("这一格只许一颗开关", 1, switchNode().fetchSemanticsNodes().size)
        switchNode()[0].assertIsOn()

        rule.runOnIdle { window.value = WindowState.VISIBLE_PANEL }
        rule.waitForIdle()
        switchNode()[0].assertIsOn()

        // 暂时隐藏：服务仍在跑 ⇒ 开关仍是开着（不是"已关闭"）
        rule.runOnIdle { window.value = WindowState.TEMP_HIDDEN }
        rule.waitForIdle()
        switchNode()[0].assertIsOn()

        rule.runOnIdle { window.value = WindowState.STOPPED }
        rule.waitForIdle()
        switchNode()[0].assertIsOff()

        rule.runOnIdle { window.value = WindowState.VISIBLE_BUBBLE }
        rule.waitForIdle()
        switchNode()[0].assertIsOn()

        // 开关是全状态常驻的唯一入口：停了它也在场（灰着、还能拨开），不许有哪一档把它撤走再留颗死按钮
        assertEquals("这一格从头到尾只许一颗开关", 1, switchNode().fetchSemanticsNodes().size)
    }

    /**
     * 标签下那行极短状态读数是**同一份窗口状态**的复述：开着 = 运行中，停了 = 已停止，
     * 隐藏仍是运行中。它不是第二本账——每一档整屏上各只许出现一次。
     *
     * 回成什么样子会红：读数写成另一颗来源（盘上布尔、或压根不跟着状态翻）→ 翻档那两句红；
     * 读数被做成一张说明卡（多写几行）→ 计数红。
     */
    @Test
    fun `the status line restates the same window reading and nothing else`() {
        val window = mutableStateOf(WindowState.VISIBLE_PANEL)
        mount(window, mutableListOf(), mutableListOf())
        assertEquals("开着：运行中恰一行", 1, textCount(runningState))
        assertEquals("开着：不许同时念出已停止", 0, textCount(stoppedState))

        rule.runOnIdle { window.value = WindowState.TEMP_HIDDEN }
        rule.waitForIdle()
        assertEquals("隐藏仍是运行中", 1, textCount(runningState))

        rule.runOnIdle { window.value = WindowState.STOPPED }
        rule.waitForIdle()
        assertEquals("停了：已停止恰一行", 1, textCount(stoppedState))
        assertEquals("停了：运行中退场", 0, textCount(runningState))
    }

    /**
     * 拨到关**只投一次**宿主那一条停止入口，并且这一格不自己翻状态。
     *
     * "投完之后屏幕上仍是亮着的开关"是这一格的牙：那一句判的是**没有乐观更新**——
     * 只有真实 `WindowState` 落回 `STOPPED`（宿主那条真实停止走完、清理路径走完）
     * 开关才落灰。回退成什么会红：
     * · 页面 `onCheckedChange` 里先把局部状态翻成 false → 第二句 `assertIsOn` 红；
     * · 拨关同时又投了开启回调（两颗混成一颗 `(Boolean) -> Unit` 让页面自己判）→ 计数红；
     * · 宿主把这一记接到"隐藏"上：这一格判不了宿主接的是哪颗，那一半在
     *   `FloatingServiceStopEntryConsistencyTest` 的 `the settings switch hands the host one close...`
     *   与 `temp hidden stays a hide...` 两格里按形状判（本页不出现隐藏的调用面）。
     */
    @Test
    fun `toggling off hands the host exactly one close and flips nothing locally`() {
        val window = mutableStateOf(WindowState.VISIBLE_BUBBLE)
        val enables = mutableListOf<Unit>()
        val closes = mutableListOf<Unit>()
        mount(window, enables, closes)

        switchNode()[0].performClickSafe()
        rule.waitForIdle()

        assertEquals("拨关只投一次关闭入口", 1, closes.size)
        assertTrue("开启那一条没被牵到", enables.isEmpty())
        switchNode()[0].assertIsOn()   // 宿主还没把状态落成 STOPPED：页面不许自己翻灰

        // 真实状态落回来之后才灰；这一格任何一档都不画那颗已废弃的重复关闭动作
        rule.runOnIdle { window.value = WindowState.STOPPED }
        rule.waitForIdle()
        switchNode()[0].assertIsOff()
        assertEquals("重复的「关闭悬浮助手」入口不许存在", 0, retiredCloseCount())
    }

    /** 拨开只投"走既有启动路径"那一次（权限检查与服务启动都归宿主，不在本页） */
    @Test
    fun `toggling on hands the host exactly one enable and no close`() {
        val window = mutableStateOf(WindowState.STOPPED)
        val enables = mutableListOf<Unit>()
        val closes = mutableListOf<Unit>()
        mount(window, enables, closes)

        switchNode()[0].performClickSafe()
        rule.waitForIdle()

        assertEquals(1, enables.size)
        assertTrue("关闭那一条没被牵到", closes.isEmpty())
        switchNode()[0].assertIsOff()
    }

    /**
     * 设置页这一格里**能停服务的入口恰好一个**：开着那一档整格只有一颗可点节点（那颗开关），
     * 点它 = 投那一条停止入口、投一次；那颗已废弃的「关闭悬浮助手」按钮的文案在屏上零颗。
     *
     * 回退成什么样子会红：
     * · 尾部那颗重复关闭按钮长回来 → 可点节点数到 2、或关闭文案数到 1，两句都红；
     * · 整行也接了点击（行 + 开关各挂一次）→ 可点节点数到 2；
     * · 一次点击投两遍关闭 → 计数 `expected:<1> but was:<2>`；
     * · 又在顶部塞一颗"收起"冒充关闭入口 → 最后那句红（这里的口径是：收起保持原行为且归页头，
     *   这一格不许画它，也不许把它的语义写成停止）。
     */
    @Test
    fun `the assistant cell exposes exactly one entry that can stop the service`() {
        val window = mutableStateOf(WindowState.VISIBLE_PANEL)
        val enables = mutableListOf<Unit>()
        val closes = mutableListOf<Unit>()
        mount(window, enables, closes)

        assertEquals("重复关闭入口的文案零颗", 0, retiredCloseCount())
        val targets = probe.actionableTargets(rule, "开着那一档的悬浮助手格")
        assertEquals(
            "能停服务的入口恰好一颗（那颗开关）：${targets.joinToString { it.describe() }}",
            1,
            targets.size
        )
        targets.single().let {
            assertTrue("那颗必须报名字：${it.describe()}", it.labeled)
            assertEquals("那颗就是开关自己", "Switch", it.role)
        }

        switchNode()[0].performClickSafe()
        rule.waitForIdle()
        assertEquals(1, closes.size)
        assertTrue("那颗不该顺带把服务再开一次", enables.isEmpty())

        // 这一格不画第二颗「收起」：收起归页头，语义也不是停止
        assertEquals(
            "这一格不许画第二颗「收起」",
            0,
            rule.onAllNodes(hasContentDescription(ctx.getString(R.string.panel_collapse))).fetchSemanticsNodes().size
        )
    }

    /**
     * 这一格每一颗可点节点两轴达全站下限、并且读屏念得出它是什么。
     *
     * 这一格量的是**页级**控件那一档（全站 48），与 `SettingsPageSemanticsTest` 那一条同一把尺；
     * 回退成什么会红：把开关缩成没有热区的小胶囊、或给紧凑件外面再套一层 48 见方空盒
     * （下限要垫在带语义的那颗自己身上——失败信息里那句"外面套一层大盒子等于没改"）、
     * 整行也接了点击（行 + 开关各挂一次 ⇒ 可点节点数到 2）。
     * 组外框归页面：这一格里不许长出第二串「悬浮助手」——行标签与组名**同字**是原型那一条
     * （组就叫悬浮助手、那一行也叫悬浮助手），单挂这一格恰一颗（行标签）；两颗就是这一格
     * 把组名也抄进了框内（卡套卡的旧分工），零颗就是行标签没了。
     */
    @Test
    fun `every actionable node in the assistant cell is big enough and named`() {
        val window = mutableStateOf(WindowState.VISIBLE_BUBBLE)
        mount(window, mutableListOf(), mutableListOf())

        val targets = probe.assertAllActionableMeetTouchFloor(rule, "悬浮助手那一格")
        // 一颗：那颗开关（整行不接点击，所以不会长出第二个可点所有者）
        assertEquals("开着那一格恰有一颗可点节点，实到：${targets.joinToString { it.describe() }}", 1, targets.size)
        assertTrue(
            "每颗都得念出自己是什么：${targets.joinToString { it.describe() }}",
            targets.none { !it.labeled }
        )
        assertEquals("「悬浮助手」那一串字在这一格里恰一颗（行标签本身），多一颗就是这一格自己画了组名",
            1, textCount(groupName))
    }

    /**
     * 停了之后这一格仍是那颗唯一的开关（灰着、还能拨开）：屏幕上没有"看着还能关一次"的死入口。
     *
     * 回退成什么会红：停了撤掉开关、另画一颗"开启"动作 → 可点节点数或开关在场那句红；
     * 那颗重复关闭按钮常驻 → "恰一颗可点节点"数到 2。
     */
    @Test
    fun `a stopped assistant leaves one toggle and no dead close button`() {
        val window = mutableStateOf(WindowState.STOPPED)
        val enables = mutableListOf<Unit>()
        val closes = mutableListOf<Unit>()
        mount(window, enables, closes)

        assertEquals(0, retiredCloseCount())
        val targets = probe.actionableTargets(rule, "关掉那一格的悬浮助手")
        assertEquals("停了就只剩那颗开关：${targets.joinToString { it.describe() }}", 1, targets.size)
        probe.assertTargetsMeetFloor(targets, TouchTier.SITE_FLOOR, "关掉那一格的悬浮助手")
        switchNode()[0].assertIsOff()
        // 停了之后那颗开关还是能拨（拨开 = 走既有启动路径），不是整格被撤掉
        switchNode()[0].performClickSafe()
        rule.waitForIdle()
        assertEquals(1, enables.size)
        assertTrue(closes.isEmpty())
    }

    /**
     * 2026-10-12 原型形状那一半：这一行是**族里的行根**，不是借来的公共件。
     *
     * 语义树侧：行根 `settings-row-assistant` 恰一颗、到全站下限又不超一行版式（原型
     * `.assistant` 的 min-height 48）；整行不接点击 ⇒ 可点样本恰一颗且角色就是那颗开关。
     * 源码侧（颜色/字阶读不出，按引用关系钉）：行壳 `settingsEntryRow()` 一次、行标签
     * `LbRowLabel()` 一次、**`LbSettingRow` 零次**（10-11 那一版"悬浮助手比别行大一档"就是
     * 从借它那档 `titleMedium` 粗体来的）；状态字 `labelSmall` + `TextHint`（原型
     * `.assist-state` 10px/--light）；开关仍是现成的 [MiniSwitch]，外面不套新容器（套了等于没改）。
     *
     * 反例逐条有牙：换回 `LbSettingRow` → 源码两句红；行也接点击 → 可点样本数到 2；
     * 状态字升档（labelLarge/别档）或换墨色 → 那两句引用红；行标签再叫「悬浮助手开启」
     * 之外的别串 → 与资源同字的计数句红（资源键由资源文件的主人统一改，判据跟着资源走）。
     */
    @Test
    fun `the assistant row is the family row root and never borrows LbSettingRow`() {
        val window = mutableStateOf(WindowState.VISIBLE_BUBBLE)
        mount(window, mutableListOf(), mutableListOf())

        val rows = rule.onAllNodes(hasTestTag(SETTINGS_ASSISTANT_ROW_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("行根该恰一颗（tag=$SETTINGS_ASSISTANT_ROW_TAG，实到 ${rows.size}）", 1, rows.size)
        val row = probe.of(rows.single())
        assertFalse("行骨架不到全站下限 ${TouchTier.SITE_FLOOR.toInt()}dp：" + row.describe(),
            row.tooSmall(TouchTier.SITE_FLOOR))
        assertTrue("这一行高过一行版式（实到 ${row.heightDp.toInt()}dp）——紧凑靠少一层容器，不是靠把行撑高：" +
            row.describe(), row.heightDp <= TouchTier.SITE_FLOOR + 8f)

        val targets = probe.actionableTargets(rule, "形状那格的悬浮助手行")
        assertEquals("整行不接点击：可点节点恰一颗（那颗开关），实到：" +
            targets.joinToString { it.describe() }, 1, targets.size)
        assertEquals("那颗就是开关自己", "Switch", targets.single().role)

        val code = maskedEntrySource()
        assertEquals("行骨架 settingsEntryRow() 恰一次（挂在行根）", 1, occurrences(code, "settingsEntryRow()"))
        assertEquals("行标签 LbRowLabel() 恰一次（整页唯一那一颗）", 1, occurrences(code, "LbRowLabel("))
        assertFalse("不许再借 LbSettingRow（首页与捕获页的公共件，设置页这一族自己解决字阶）",
            code.contains("LbSettingRow"))
        assertTrue("状态字按原型 `.assist-state` 取 labelSmall 那一档", code.contains("AppTypography.labelSmall"))
        assertTrue("状态字墨色取 TextHint（换算表 --light 同值那颗）", code.contains("TextHint"))
        assertEquals("行根锚点与行骨架挂在同一颗上（锚点合同：量具只认行根）", 1,
            occurrences(code, "settingsEntryRow().testTag(SETTINGS_ASSISTANT_ROW_TAG)"))
        assertEquals("开关还是现成的那颗 MiniSwitch，不多不少", 1, occurrences(code, "MiniSwitch("))
        assertFalse("开关外面不许再套一层容器（Box 一颗都不该出现，套了等于没改）", code.contains("Box("))
    }

    /** 生产那棵件的源码（剥注释后）：形状那一族按引用关系钉的那把尺的对象 */
    private fun maskedEntrySource(): String {
        val file = File("src/main/java/com/lovebrain/app/ui/panel/settings/SettingsAssistantEntry.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/settings/SettingsAssistantEntry.kt")
        assertTrue("找不到 $file——这把尺会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
        assertTrue("掩码源码为空（扫了个寂寞的恒绿形状）", code.isNotBlank())
        return code
    }

    /** 数一个符号片段在剥过注释的源码里出现几次（"出现即可"顶不住"多一颗/少一颗"那一族坏法） */
    private fun occurrences(haystack: String, needle: String): Int =
        Regex(Regex.escape(needle)).findAll(haystack).count()

    /**
     * `performClick` 走语义动作、绕过命中测试；这里仍按语义动作点，
     * 但把"那颗真的在屏上"另判一次，免得量具换成"点得到但看不见"那种半绿。
     */
    private fun SemanticsNodeInteraction.performClickSafe() {
        assertIsDisplayed()
        performClick()
    }
}
