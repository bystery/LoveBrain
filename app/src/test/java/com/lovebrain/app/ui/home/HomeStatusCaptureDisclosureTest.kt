package com.lovebrain.app.ui.home

import android.content.Context
import android.content.Intent
import android.app.Application
import android.provider.Settings
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.service.CopyCaptureService
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 夹具摆出来的那一行 App 名：它同时是"这一批 IO 读数落位了"的可观测定点（见 [mount]） */
private const val APP_ROW_LABEL = "CHAT_ONE"

/** 等一批读数的上限：拿它当"超时就是量具坏了"的哨兵，不是拿来吸收抖动的 */
private const val WAIT_FOR_BATCH_MS = 10_000L

/**
 * 消息捕获页的**授权披露链**（合同 第7节第1条 末段"不得静默开权限"、第7节第2条 末段"辅助权限在捕获页说明"）。
 *
 * 这一族存在的原因很具体：首页重做删掉"服务设置段"时，`AccessibilityDisclosureDialog` 的**调用点**
 * 一起没了（改之前全仓 grep 只剩它自己的声明 + 异形账本那一行登记），于是
 * "未授予 → 先弹披露 → 明确同意 → 才进系统设置"这条法律链断成一句注释。
 * 断链之后最省事的两种写法都是静默开权限："点开关直接 startActivity"、"点开关直接开捕获"。
 * 所以这里判的是**动作有没有发生**（计数 + 意图读数），不是源码里有没有那个名字。
 *
 * 五格各拦一种坏写法：
 * 1. 未授予 + 这一版没明确同意过 → 只弹披露：不写同意记录、不发意图、不改开关；
 * 2. 按"同意并继续" → **先**写同意记录、**后**跳系统设置（顺序有证人），未授予时开关不亮；
 * 3. 按"取消" → 什么都不发生；
 * 4. 已授予 → 开关当场生效，不弹披露也不跳设置（"已授予不骚扰"）；
 * 5. 这一版已同意过但未授予 → 直送系统设置，同一篇长文不端第二遍。
 * 关掉捕获永远不设门槛（退出采集不需要前置告知）。
 *
 * ⚠ 授权读数不是"读不出来就 pass"：`isCaptureServiceEnabled` 与 `isAccessibilityDisclosureConfirmed`
 * 两格都由夹具**明确摆出 true / false 两边**，每一边都真跑过一次组合与点击；
 * 最后一格还单独钉了"夹具给的布尔不是恒真"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeStatusCaptureDisclosureTest {

    @get:Rule
    val rule = createComposeRule()

    // shadowOf 的重载表里没有 Context 这一档，取 Application 才命中 _ShadowApplication。
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val switchName: String get() = app.getString(R.string.capture_apps_allow)

    /**
     * 捕获页的假 VM：两格授权读数都是**摆出来的**。
     * `toggleCapture()` / `confirmAccessibilityDisclosure()` 都计数，于是"有没有发生"读得出来；
     * `toggleCapture()` 还会真的翻那颗 StateFlow，所以"当场生效"在屏幕上也看得见。
     */
    private class Harness(granted: Boolean, disclosureConfirmed: Boolean) {
        val enabled = MutableStateFlow(false)
        var toggleCount = 0
        var confirmCount = 0

        /** 同意记录写入那一刻的意图读数：顺序证人（"confirm" 必须在 "no-intent-yet" 之前） */
        val confirmOrder = mutableListOf<String>()

        val model: SetupViewModel = mockk(relaxed = true)

        init {
            every { model.captureEnabled } returns enabled
            every { model.captureAllowedPackages } returns MutableStateFlow(setOf(PKG))
            every { model.isCaptureServiceEnabled(any()) } returns granted
            every { model.isAccessibilityDisclosureConfirmed() } returns disclosureConfirmed
            every { model.selectableCaptureTargets(any()) } returns
                listOf(SetupViewModel.CaptureApp(PKG, APP_ROW_LABEL, secondRejected = false))
            every { model.toggleCapture() } answers {
                toggleCount++
                enabled.value = !enabled.value
            }
            every { model.confirmAccessibilityDisclosure() } answers {
                confirmCount++
                confirmOrder += "confirm"
                val alreadyStarted = shadowOf(
                    ApplicationProvider.getApplicationContext<Application>()
                ).peekNextStartedActivity() != null
                confirmOrder += if (alreadyStarted) "intent-first" else "no-intent-yet"
            }
        }

        companion object {
            const val PKG = "com.chat.one"
        }
    }

    private fun mount(h: Harness) {
        rule.setContent {
            UiMatrix(360).RenderIn(androidx.compose.ui.platform.LocalDensity.current.density) {
                CaptureAppsScreen(viewModel = h.model, onBack = {})
            }
        }
        rule.waitForIdle()
        // **等这一批 IO 读数落位之后再许点**，不是"睡一会儿"：
        // `CaptureAppsScreen` 把扫包 / 无障碍授权 / 这一版同意记录读成一批，并把
        // `scanning = false` 排在三个赋值的**最后一颗**，所以"列表里那一行画出来了"
        // 就是"闸门要吃的两颗布尔都已经落位"的可观测定点。
        // 不等这一格会把量具自己测红：`waitForIdle` 不跟踪 `Dispatchers.IO` 上的协程，
        // 于是"已授予不骚扰"那一格可能在 `granted` 还是初值 false 时被点下去，
        // 读出弹窗——同一条用例两次跑出两种颜色，那样的读数不能拿来定案。
        rule.waitUntil(WAIT_FOR_BATCH_MS) {
            rule.onAllNodes(hasText(APP_ROW_LABEL)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickCaptureSwitch() {
        rule.onAllNodes(hasClickAction() and hasContentDescription(switchName))[0].performClick()
        rule.waitForIdle()
    }

    private fun clickDisclosureButton(label: String) {
        rule.onAllNodesWithText(label)[0].performClick()
        rule.waitForIdle()
    }

    /**
     * 弹窗在不在：判**标题那一颗**（那句只在披露弹窗里出现过），而不是只判 testTag——
     * AlertDialog 那一层的 tag 是否落进语义树不该成为这条法律链的判据载体。
     * 上面那一格 `the disclosure states...` 另外把 tag 也读了一次，两件事都有人钉。
     */
    private fun dialogShown() = hasTextOnScreen(disclosureTitle)

    private val disclosureTitle = "开启消息捕获前，请确认"

    private fun dialogTagOnTree() =
        rule.onAllNodesWithTag(LbCaptureTags.DISCLOSURE).fetchSemanticsNodes().isNotEmpty()

    private fun hasTextOnScreen(text: String, substring: Boolean = false) =
        rule.onAllNodes(if (substring) hasText(text, substring = true) else hasText(text))
            .fetchSemanticsNodes().isNotEmpty()

    private fun peekIntent(): Intent? = shadowOf(app).peekNextStartedActivity()

    private fun takeIntent(): Intent? = shadowOf(app).nextStartedActivity

    // ═══════════ 1. 未授予 + 这一版没同意过：只弹披露 ═══════════

    /**
     * 反例（坏实现怎么把这格弄红）：
     * - `onToggle = { viewModel.toggleCapture() }`（没有闸门）→ `toggleCount` 变 1、弹窗缺席；
     * - 点开关直接 `startActivity(ACTION_ACCESSIBILITY_SETTINGS)` → 意图已经发了、披露还没弹过；
     * - 弹窗一出现就顺手 `confirmAccessibilityDisclosure()` → `confirmCount` 变 1（那是替用户同意）。
     */
    @Test
    fun `an ungranted switch opens only the disclosure`() {
        val h = Harness(granted = false, disclosureConfirmed = false)
        mount(h)

        clickCaptureSwitch()

        assertTrue("未授予时点开关必须先看见披露", dialogShown())
        assertEquals("没同意之前不许写同意记录", 0, h.confirmCount)
        assertEquals("没同意之前开关不许当场生效", 0, h.toggleCount)
        assertEquals(false, h.enabled.value)
        assertNull(
            "明确同意之前不许把用户送去系统设置（那一步只能由他点同意触发）",
            peekIntent()
        )
    }

    /**
     * 披露本体要真的把那三件事说出来：读什么、发给谁、能不能撤。
     * 只判关键词、不抄整段——抄整段等于让测试替文案说话，改一个标点就红。
     */
    @Test
    fun `the disclosure states what it reads where it goes and how to undo it`() {
        val h = Harness(granted = false, disclosureConfirmed = false)
        mount(h)
        clickCaptureSwitch()

        assertTrue("标题要说清这是开启前的确认", hasTextOnScreen(disclosureTitle))
        assertTrue("弹窗要留一个不靠文案的自动化锚点", dialogTagOnTree())
        listOf("同意并继续", "取消").forEach { label ->
            assertTrue("两颗出口都要在：缺「$label」", hasTextOnScreen(label))
        }
        assertTrue("要说清读的是界面节点文字", hasTextOnScreen("节点", substring = true))
        assertTrue("要说清内容会发给用户配置的供应商", hasTextOnScreen("供应商", substring = true))
        assertTrue("要说清可以随时撤销", hasTextOnScreen("撤销", substring = true))
    }

    // ═══════════ 2. 明确同意：先写记录，才进系统设置 ═══════════

    @Test
    fun `agreeing writes the consent record before opening system settings`() {
        val h = Harness(granted = false, disclosureConfirmed = false)
        mount(h)
        clickCaptureSwitch()
        clickDisclosureButton("同意并继续")

        assertEquals("同意要落下记录", 1, h.confirmCount)
        assertEquals(
            "同意记录必须先落地、意图后发（否则用户给完权限拿到的还是一个不工作的捕获）",
            listOf("confirm", "no-intent-yet"), h.confirmOrder
        )
        val started = takeIntent()
        assertNotNull("明确同意之后才允许进系统设置", started)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, started!!.action)
        assertEquals("未授予时按同意也不许把捕获开关点亮", 0, h.toggleCount)
        assertEquals(false, h.enabled.value)
        assertTrue("弹窗该收掉", !dialogShown())
    }

    /** 取消就是取消：不写记录、不发意图、不改开关，弹窗收掉 */
    @Test
    fun `cancelling the disclosure does nothing at all`() {
        val h = Harness(granted = false, disclosureConfirmed = false)
        mount(h)
        clickCaptureSwitch()
        clickDisclosureButton("取消")

        assertEquals(0, h.confirmCount)
        assertEquals(0, h.toggleCount)
        assertNull("取消之后不许有意图", peekIntent())
        assertTrue("取消之后弹窗要收掉", !dialogShown())
        assertEquals("取消之后捕获还是关着的", false, h.enabled.value)
    }

    // ═══════════ 3. 已授予不骚扰；同一版披露只说一次 ═══════════

    /**
     * 反例：闸门写成"没确认披露就先弹，不管授予没有" → 已授予这一格会弹窗、
     * `toggleCount` 变 0：用户明明已经在系统里给过权限，开关却按不动。
     */
    @Test
    fun `a granted capture toggles immediately without any dialog`() {
        val h = Harness(granted = true, disclosureConfirmed = true)
        mount(h)

        clickCaptureSwitch()

        assertEquals("已授予就该当场生效", 1, h.toggleCount)
        assertEquals(true, h.enabled.value)
        assertTrue("已授予不许弹披露（骚扰）", !dialogShown())
        assertNull("已授予不许把用户送去系统设置", peekIntent())
        assertEquals("不该顺手再写一次同意记录", 0, h.confirmCount)
    }

    /**
     * 这一版已经明确同意过、但系统权限还没给：直送系统设置，**不重复弹披露**。
     *
     * 反例：只判"未授予就弹" → 第二次按开关又被端一遍同一篇长文（反复问只会让用户不看内容点同意）；
     * 反例：已同意过就直接开捕获 → 开关亮着而服务读不到东西，那是一句谎。
     */
    @Test
    fun `an already agreed disclosure goes straight to system settings once`() {
        val h = Harness(granted = false, disclosureConfirmed = true)
        mount(h)

        clickCaptureSwitch()

        assertTrue("同一版披露不端第二遍", !dialogShown())
        val started = takeIntent()
        assertNotNull("已明确同意过的那一版，未授予时才允许跳系统设置", started)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, started!!.action)
        assertEquals("未授予时不许把开关点亮", 0, h.toggleCount)
        assertEquals("不该再写一次同意记录", 0, h.confirmCount)
    }

    /** 关掉捕获永远不设门槛：即使没授予、没同意过，也要关得掉 */
    @Test
    fun `turning capture off is never blocked by the gate`() {
        val h = Harness(granted = false, disclosureConfirmed = false)
        h.enabled.value = true
        mount(h)

        clickCaptureSwitch()

        assertEquals("关就是一下", 1, h.toggleCount)
        assertTrue("关掉不许弹披露", !dialogShown())
        assertNull("关掉也不许发意图", peekIntent())
        assertEquals(false, h.enabled.value)
    }

    // ═══════════ 4. 纯判据矩阵：三颗出口各被走到 ═══════════

    /**
     * 八格 = 三根输入轴的全体组合，期望值手写。
     * 反例：`when` 的分支顺序被换（先判授予再判 targetEnabled）→ "关掉"那四格红；
     * 反例：`disclosureConfirmed` 默认成 true → (未授予, 未同意) 那一格不再弹窗。
     */
    @Test
    fun `the gate table derives exactly one step per input row`() {
        data class Row(
            val name: String,
            val target: Boolean,
            val granted: Boolean,
            val consent: Boolean,
            val expected: CaptureGateStep
        )

        val rows = listOf(
            Row("开 · 已授予 · 已同意", true, true, true, CaptureGateStep.ApplySwitch),
            Row("开 · 已授予 · 未同意", true, true, false, CaptureGateStep.ApplySwitch),
            Row("开 · 未授予 · 已同意", true, false, true, CaptureGateStep.OpenAccessibilitySettings),
            Row("开 · 未授予 · 未同意", true, false, false, CaptureGateStep.ShowDisclosure),
            Row("关 · 已授予 · 已同意", false, true, true, CaptureGateStep.ApplySwitch),
            Row("关 · 已授予 · 未同意", false, true, false, CaptureGateStep.ApplySwitch),
            Row("关 · 未授予 · 已同意", false, false, true, CaptureGateStep.ApplySwitch),
            Row("关 · 未授予 · 未同意", false, false, false, CaptureGateStep.ApplySwitch)
        )
        rows.forEach { r ->
            assertEquals(
                "${r.name}：闸门只许判出一颗出口", r.expected,
                captureSwitchGateStep(r.target, r.granted, r.consent)
            )
        }
        // 哨兵：三颗出口都被矩阵走过一次，否则"逐行比对"可以是空的还报绿
        assertEquals(
            "三颗出口都要被走到",
            CaptureGateStep.values().toSet(),
            rows.map { it.expected }.toSet()
        )
        assertEquals(
            "同意之后只剩两种出口：授予了就开、没授予就去设置",
            listOf(CaptureGateStep.ApplySwitch, CaptureGateStep.OpenAccessibilitySettings),
            listOf(
                disclosureAgreedGateStep(accessibilityGranted = true),
                disclosureAgreedGateStep(accessibilityGranted = false)
            )
        )
    }

    /**
     * 夹具那两颗布尔不是恒真：上面矩阵逐格比过，而这一格钉**真读数的来源**——
     * `SetupViewModel.isAccessibilityDisclosureConfirmed()` 与 `confirmAccessibilityDisclosure()`
     * 必须是同一把尺，且这把尺与 `CopyCaptureService` 挡住正文那条是同一个比较
     * （记在偏好里的版本号 >= `CURRENT_DISCLOSURE_VERSION`）。
     *
     * 这一格是整条链的地基。反例：判据写成 `!= 0`、`>= 0` 或干脆 `true` ⇒ 从没同意过的用户
     * 被当成已同意，页面于是直接跳系统设置（= 静默开权限），而前面那五格全都不会红——它们用的是桩。
     * 反例：写记录时写死 `1` 而不是当前版本 ⇒ 披露文本以后升版就再也判不出"这一版同意过"。
     */
    @Test
    fun `the consent reading and the consent write are the same ruler`() {
        val store = InMemorySettingsStore()
        val model = SetupViewModel(
            securePrefs = store,
            deepSeekRepo = mockk(relaxed = true)
        )

        assertEquals(
            "新机器上没人同意过这一版", false, model.isAccessibilityDisclosureConfirmed()
        )

        model.confirmAccessibilityDisclosure()
        assertEquals(
            "明确同意之后要读成已同意", true, model.isAccessibilityDisclosureConfirmed()
        )
        assertEquals(
            "写下去的必须是当前那一版的号（升版要能把旧同意作废）",
            CopyCaptureService.CURRENT_DISCLOSURE_VERSION, store.accessibilityDisclosureVersion
        )

        // 旧版本用户：给过权限、但没看过这一版 ⇒ 仍然要先看披露
        store.accessibilityDisclosureVersion = CopyCaptureService.CURRENT_DISCLOSURE_VERSION - 1
        assertEquals(
            "上一版的同意不等于这一版的同意", false, model.isAccessibilityDisclosureConfirmed()
        )
    }

    /** 量具自己也要有证人：这一屏确实把那颗开关画出来了（不然是空跑，不是判据） */
    @Test
    fun `the probe actually sees the capture switch on this screen`() {
        val h = Harness(granted = false, disclosureConfirmed = false)
        mount(h)

        val switches = rule.onAllNodes(hasClickAction() and hasContentDescription(switchName))
            .fetchSemanticsNodes()
        assertEquals(
            "这一屏必须恰好有一颗捕获总开关，实到 ${switches.size} 颗", 1, switches.size
        )
    }
}
