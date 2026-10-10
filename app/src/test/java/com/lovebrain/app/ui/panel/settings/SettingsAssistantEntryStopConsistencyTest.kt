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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.service.FloatingService.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **停止入口一致**那一族的界面半边（M05/M06）：设置页那颗「悬浮助手」开关与「关闭悬浮助手」
 * 动作，在语义树上必须表现为**同一颗真实停止入口的两个把手**，而不是页面自己的一份状态。
 *
 * 与 `FloatingServiceStopEntryConsistencyTest`（形状半边）的分工：
 * 那一族钉"三颗入口的最终动作只有一颗、清理路径没被绕过、没有新键"；这一族钉
 * "屏幕画出来的那颗开关读的是真实 `WindowState`、拨下去只投一次回调、不乐观翻状态"。
 * 两族合起来才是"入口一致"这件事——单独任何一族都能被绕过（只看形状的话，页面可以把开关
 * 涂成永远亮着；只看语义树的话，宿主可以把关闭接到 `tempHide` 上而屏幕上看不出来）。
 *
 * 读数全部来自 `stringResource`/`getString`：这台机器环境解析出来是英文那一套时写死中文会假红
 * （同 `SettingsPageSemanticsTest` 文件头那条仪器教训）。
 *
 * ⚠ 这一族**没有**任何"关闭之后屏幕上是不是真的没了"的断言：语义树里没有第二扇 overlay 窗口可查，
 * 面板/球/通知的同时消失属**需真机**那一半（登记在 `FloatingServiceStopEntryConsistencyTest` 最后一格）。
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
    private val closeName: String get() = ctx.getString(R.string.settings_assistant_close)
    private val groupName: String get() = ctx.getString(R.string.settings_group_assistant)

    /** 一次挂载里翻窗口状态（同一用例里第二次 `setContent` 会被框架当场拒掉——那条坑写在原处） */
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
                    // 这一格不给默认值、不留第二本账（判据在 `no new persisted flag...` 那一格）。
                    assistantOn = FloatingService.isAssistantOn(window.value),
                    onRequestEnable = { enables += Unit },
                    onRequestClose = { closes += Unit }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun switchNode() = rule.onAllNodes(hasContentDescription(switchName))

    private fun closeNode() = rule.onAllNodes(hasText(closeName))

    /**
     * 原话第 2 条：开关的亮与灭**逐档**跟着真实 `WindowState` 走。
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
    }

    /**
     * 原话第 1 条（关闭那一半）：拨到关**只投一次**宿主那一条停止入口，并且这一格不自己翻状态。
     *
     * "投完之后屏幕上仍是亮着的开关"是这一格的牙：那一句判的是**没有乐观更新**——
     * 只有真实 `WindowState` 落回 `STOPPED`（宿主那一条 `stopSelf()` 走通、`onDestroy` 清完）
     * 开关才落灰。回退成什么会红：
     * · 页面 `onCheckedChange` 里先把局部状态翻成 false → 第二句 `assertIsOn` 红；
     * · 拨关同时又投了开启回调（两颗混成一颗 `(Boolean) -> Unit` 让页面自己判）→ 计数红；
     * · 宿主把这一记接到"隐藏"上：这一格判不了宿主接的是哪颗，那一半在
     *   `FloatingServiceStopEntryConsistencyTest` 的 `the settings switch hands the host one close...`
     *   与 `temp hidden stays a hide...` 两格里按形状判（本页不出现 tempHide 的调用面）。
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

        // 真实状态落回来之后才灰，并且那颗「关闭悬浮助手」一起退场（不留一颗点了没反应的死按钮）
        rule.runOnIdle { window.value = WindowState.STOPPED }
        rule.waitForIdle()
        switchNode()[0].assertIsOff()
        assertEquals("关掉之后这一格不该还剩一颗关闭动作", 0, closeNode().fetchSemanticsNodes().size)
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
     * 原话「在悬浮面板内安排一个明确的『关闭悬浮助手』操作入口」：那一颗必须**恰有一颗**，
     * 点下去只投那一条停止入口、投一次。
     *
     * 回成什么样子会红：
     * · 那颗被删掉/换成不报名字的自绘盒 → `assertIsDisplayed` 或"恰一颗"红；
     * · 一次点击投两遍（整行 clickable 与那颗胶囊各挂一次）→ 计数 `expected:<1> but was:<2>`；
     * · 又在顶部塞一排新图标画第二颗 → "恰一颗"红（`SettingsPageSemanticsTest` 那条"页面上
     *   只有一颗『收起』"的同一族形状，这里钉的是关闭那颗）。
     */
    @Test
    fun `there is exactly one close assistant action and it fires once`() {
        val window = mutableStateOf(WindowState.VISIBLE_PANEL)
        val enables = mutableListOf<Unit>()
        val closes = mutableListOf<Unit>()
        mount(window, enables, closes)

        assertEquals("「关闭悬浮助手」必须恰一颗", 1, closeNode().fetchSemanticsNodes().size)
        closeNode()[0].assertIsDisplayed()
        closeNode()[0].performClickSafe()
        rule.waitForIdle()

        assertEquals(1, closes.size)
        assertTrue("那颗不该顺带把服务再开一次", enables.isEmpty())
        // 面板顶部那一排不许多出第二颗收起（原话：收起按钮保持原行为，不跟着加一排）
        assertEquals(
            "这一格不许画第二颗「收起」",
            0,
            rule.onAllNodes(hasContentDescription(ctx.getString(R.string.panel_collapse))).fetchSemanticsNodes().size
        )
    }

    /**
     * 第6节第5条 + 第②栏：这一格每一颗可点节点两轴达全站下限、并且读屏念得出它是什么。
     *
     * 这一格量的是**页级**控件那一档（全站 48），与 `SettingsPageSemanticsTest` 那一条同一把尺；
     * 回退成什么会红：把那颗关闭动作换成 28 见方的卡内档、或把开关缩成没有热区的小胶囊
     * （`MiniSwitch` 外面再套一层大盒子等于没改——失败信息里那句"要垫在带语义的那颗自己身上"）。
     */
    @Test
    fun `every actionable node in the assistant entry is big enough and named`() {
        val window = mutableStateOf(WindowState.VISIBLE_BUBBLE)
        mount(window, mutableListOf(), mutableListOf())

        val targets = probe.assertAllActionableMeetTouchFloor(rule, "悬浮助手那一格")
        // 两颗：那颗开关 + 那颗关闭动作（整行不接点击，所以不会长出第三个可点所有者）
        assertEquals("开着那一格恰有两颗可点节点，实到：${targets.joinToString { it.describe() }}", 2, targets.size)
        assertTrue(
            "每颗都得念出自己是什么：${targets.joinToString { it.describe() }}",
            targets.none { !it.labeled }
        )
        // 组名这一行只是标题，不是可点节点；它在场才说明这一格挂在了对的族里
        assertEquals(1, rule.onAllNodes(hasText(groupName)).fetchSemanticsNodes().size)
    }

    /**
     * 关掉之后那颗关闭动作退场（`assistantOn = false` 那一档）：
     * 只留一颗开关、且开关是灰的——屏幕上没有"看着还能关一次"的死入口。
     *
     * 回退成什么会红：`if (assistantOn)` 那一档被摘掉、动作常驻 → "恰一颗可点节点" 数到 2。
     */
    @Test
    fun `a stopped assistant leaves one toggle and no dead close button`() {
        val window = mutableStateOf(WindowState.STOPPED)
        val enables = mutableListOf<Unit>()
        val closes = mutableListOf<Unit>()
        mount(window, enables, closes)

        assertEquals(0, closeNode().fetchSemanticsNodes().size)
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
     * `performClick` 走语义动作、绕过命中测试；这里仍按语义动作点，
     * 但把"那颗真的在屏上"另判一次，免得量具换成"点得到但看不见"那种半绿。
     */
    private fun SemanticsNodeInteraction.performClickSafe() {
        assertIsDisplayed()
        performClick()
    }
}
