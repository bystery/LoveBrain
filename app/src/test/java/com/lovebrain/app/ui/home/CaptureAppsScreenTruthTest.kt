package com.lovebrain.app.ui.home

import android.app.Application
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * CAP1（2026-10-06）：捕获页状态行**在屏幕上**只说真话。
 *
 * 判据本体在 [captureTruthOf]（纯表由 `CaptureTruthStateTest` 穷举）；这一族钉的是
 * "那一行到底画出来的是哪一句"——用户报的形态是**显示已开启但抓不到**
 * （source-10 候选①③），所以每一格都从屏幕的语义树里读数，不读内部变量：
 *
 * 1. 范围空 + 其余全真 → 屏幕念"未选择 App · 不会捕获任何内容"，**"已开启"那句不在屏上**；
 * 2. 悬浮窗不在 + 四件全真 → 念悬浮窗那一格，不念"已开启"（候选③的可见出口）；
 * 3. 披露缺 → 行本身是能点的入口：点开的是同一扇披露窗，"同意并继续"**只补写记录**，
 *    不许拨开关（捕获明明开着，拨一下反而当场关掉它）、不许跳系统设置（权限已给，那是骚扰）；
 * 4. 五读全真 → 才出现"已开启 · 长按消息自动捕获"（候选②的处置：先把"只有长按才抓"说清，
 *    事件面一个字没放宽）。
 *
 * CAP3（同日，用户第二次追加的原话「第一次进入消息捕获页面，开关是打开的」）在三格后面又接三格，
 * 判的是**那颗胶囊画的是哪一档**（读语义树里的 `ToggleableState`，不读内部变量）：
 * 5. 首屏（偏好位默认 true、权限没给、没同意、没勾范围）→ 开关**画成关**（D1）；
 * 6. 四件全真而悬浮窗不在 → 开关仍画开，缺的那件事由那一行的句子说（第五次读数不改开关脸）；
 * 7. 开关画成关、意图旗标却开着（只差没勾范围）时拨一下 → 那一笔**不落**、意图原样留着
 *    （显示与意图两根轴，不许并成一根）。
 *
 * ⚠ 不动 `HomeStatusCaptureDisclosureTest` 钉的那条法律链：本族全部走
 * "开关已经开着、权限已经给了"之后的状态读账，披露弹窗只多了一个入口，链与顺序没换主人。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureAppsScreenTruthTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun str(id: Int) = app.getString(id)

    private class Harness(
        enabled: Boolean,
        allowed: Set<String>,
        granted: Boolean,
        disclosure: Boolean
    ) {
        val enabledFlow = MutableStateFlow(enabled)
        val allowedFlow = MutableStateFlow(allowed)
        var disclosureConfirmed = disclosure
        var confirmCount = 0
        var toggleCount = 0

        val model: SetupViewModel = mockk(relaxed = true)

        init {
            every { model.captureEnabled } returns enabledFlow
            every { model.captureAllowedPackages } returns allowedFlow
            every { model.isCaptureServiceEnabled(any()) } returns granted
            every { model.isAccessibilityDisclosureConfirmed() } answers { disclosureConfirmed }
            every { model.selectableCaptureTargets(any()) } returns
                listOf(SetupViewModel.CaptureApp(PKG, APP_ROW_LABEL, secondRejected = false))
            every { model.toggleCapture() } answers {
                toggleCount++
                enabledFlow.value = !enabledFlow.value
            }
            every { model.confirmAccessibilityDisclosure() } answers {
                confirmCount++
                disclosureConfirmed = true
            }
        }

        companion object {
            const val PKG = "com.chat.one"
            const val APP_ROW_LABEL = "CHAT_ONE"
        }
    }

    /** 挂载并等那一批 IO 读数落位（"那一行在"= 四颗真读数都已落位，同披露族的可观测定点） */
    private fun mount(h: Harness, floating: Boolean) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                CaptureAppsScreen(
                    viewModel = h.model,
                    onBack = {},
                    floatingRunning = { floating }
                )
            }
        }
        rule.waitForIdle()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText(Harness.APP_ROW_LABEL)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun textOnScreen(text: String) =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /**
     * 那颗开关在语义树里**画的是哪一档**（`ToggleableState`）——CAP3 的显示轴读数口。
     *
     * 读语义、不读内部变量：显示与意图分成两根轴之后，"屏幕上那颗胶囊"就是用户唯一看得见的那件事，
     * 拿 `enabledFlow.value` 当判据会把两根轴又并回一根。
     * 里面先钉"恰好一颗 + 确实挂着 toggle 语义"，不然是空跑（读不到东西也算"没画开"）。
     */
    private fun switchShowsOn(): Boolean {
        val nodes = rule.onAllNodes(
            hasClickAction() and hasContentDescription(str(R.string.capture_apps_allow))
        ).fetchSemanticsNodes()
        assertEquals("这一屏必须恰好一颗捕获总开关（不然是空跑）", 1, nodes.size)
        val state = nodes.single().config.getOrNull(SemanticsProperties.ToggleableState)
        assertNotNull("那颗开关必须挂 toggle 语义，否则显示这一档根本没读数", state)
        return state == ToggleableState.On
    }

    private fun clickCaptureSwitch() {
        rule.onAllNodes(hasClickAction() and hasContentDescription(str(R.string.capture_apps_allow)))[0]
            .performClick()
        rule.waitForIdle()
    }

    // ═══════════ 1. 范围空：念"没选 App"，不念"已开启" ═══════════

    @Test
    fun `empty scope states nothing-will-be-captured instead of running`() {
        mount(
            Harness(
                enabled = true, allowed = emptySet(),
                granted = true, disclosure = true
            ),
            floating = true
        )
        assertTrue(
            "范围空必须念得出'不会捕获任何内容'",
            textOnScreen(str(R.string.capture_apps_row_subtitle_none))
        )
        assertFalse(
            "回退成旧账（只看开关+披露就念已开启）时这一格红",
            textOnScreen(str(R.string.home_capture_status_on))
        )
    }

    // ═══════════ 2. 悬浮窗不在：念投递缺的那格，不念"已开启" ═══════════

    @Test
    fun `capture is not announced running while the floating window is down`() {
        mount(
            Harness(
                enabled = true, allowed = setOf(Harness.PKG),
                granted = true, disclosure = true
            ),
            floating = false
        )
        assertFalse(
            "悬浮窗不在时长按确认到的内容当场不记（候选③），界面不许念已开启",
            textOnScreen(str(R.string.home_capture_status_on))
        )
        assertTrue(
            "要给用户看得懂的出口：那一行指向悬浮窗本身（点悬浮窗/开始之后才恢复抓取）",
            textOnScreen(str(R.string.home_desc_not_started))
        )
    }

    // ═══════════ 3. 披露缺：行是可点的入口，同意只补记录 ═══════════

    @Test
    fun `consent-pending row opens the disclosure and agreeing only records consent`() {
        val h = Harness(
            enabled = true, allowed = setOf(Harness.PKG),
            granted = true, disclosure = false
        )
        mount(h, floating = true)

        assertTrue(
            "披露缺的那格要念得出'开启前请确认'",
            textOnScreen(str(R.string.capture_disclosure_title))
        )
        assertFalse(textOnScreen(str(R.string.home_capture_status_on)))

        // 那一行本身是能点的入口（不是第四种状态件：还是那一行、那颗点）。
        // performClick 落在句子节点上，语义手势会往上传：行没有整行点击位时会当场抛错，
        // "入口在不在"由动作本身作证，不靠数语义节点。
        rule.onAllNodes(hasText(str(R.string.capture_disclosure_title)))[0].performClick()
        rule.waitForIdle()

        // 点开的是同一扇披露窗（同一篇长文，不另造一屏）
        rule.onAllNodesWithText(str(R.string.capture_disclosure_agree))[0].performClick()
        rule.waitForIdle()

        assertEquals("明确同意才写记录，且只写一次", 1, h.confirmCount)
        assertEquals(
            "这一路权限已给、开关已开：同意**不许**顺手拨开关（拨一下=当场关掉用户开着的捕获）",
            0, h.toggleCount
        )
        assertNull(
            "这一路也不许把用户送去系统设置（权限已经给了，那一跳是骚扰；" +
                "跳设置只属于'未授予'的开关路径，那条链由 HomeStatusCaptureDisclosureTest 钉）",
            shadowOf(app).peekNextStartedActivity()
        )
        // 同意之后当场落回运行中那句（floating=true、范围非空、其余全真）
        assertTrue(textOnScreen(str(R.string.home_capture_status_on)))
    }

    // ═══════════ 4. 五读全真：才许念"已开启 · 长按…" ═══════════

    @Test
    fun `only the all-true row gets the running sentence`() {
        mount(
            Harness(
                enabled = true, allowed = setOf(Harness.PKG),
                granted = true, disclosure = true
            ),
            floating = true
        )
        val running = str(R.string.home_capture_status_on)
        assertTrue("五读全真必须出现运行中那句：$running", textOnScreen(running))
        // 候选②处置钉在内容里：那句自带"长按"，用户从此看得见"要先长按才抓"
        assertTrue(
            "运行中那句必须含'长按'——换成不含它的句子就是这一格红（事件面没放宽，话要说全）",
            running.contains("长按") || running.contains("long-press", ignoreCase = true) ||
                running.contains("press and hold", ignoreCase = true)
        )
    }

    // ═══════════ 5. CAP3（D1）：首屏那颗开关不许画成"开" ═══════════

    /**
     * 用户原话：「第一次进入消息捕获页面的时候，会发现开关是打开的……我关闭了重新拨开才触发了长文」。
     * 夹具摆的就是那一副现场：意图旗标是 true（这颗偏好在旧版本里**默认就是 true**；今天由夹具
     * 显式摆出，判据不依赖那颗默认值归谁），而权限没给、这一版没同意过、范围没勾
     * ⇒ 开关**必须画成关**，用户第一下拨的就是真"拨开"。
     *
     * 反例（改坏哪一行）：`CaptureStatusCard(checked = captureEnabled …)`（＝改前形状）→ 这一格红。
     * 主判据是那颗胶囊的 `ToggleableState`，不是屏上那一句——句子中不中奖跟画哪一档是两件事。
     */
    @Test
    fun `the very first screen never draws the capture switch as on`() {
        mount(
            Harness(
                enabled = true, allowed = emptySet(),
                granted = false, disclosure = false
            ),
            floating = false
        )
        assertFalse(
            "偏好位默认 true ≠ 捕获已经开着：四件里缺任何一件，那颗胶囊都不许画成拨开的",
            switchShowsOn()
        )
        assertFalse(textOnScreen(str(R.string.home_capture_status_on)))
    }

    // ═══════════ 6. CAP3：第五次读数只改那一行的句子，不改开关的脸 ═══════════

    /**
     * 四件全真、只有悬浮窗不在（候选③）：那一行念"悬浮窗没起"（第 2 格已经钉过句子），
     * 而那颗开关**仍然画开**——用户自己那一档意图是真的。
     *
     * 反例：把 `captureSwitchShowsOn` 写成"只有 Running 才画开" → 这一格红；
     * 而那正是下一格（拨一下反而把意图写成关）的新病灶来源。
     */
    @Test
    fun `a down floating window keeps the switch face on while the row names the gap`() {
        mount(
            Harness(
                enabled = true, allowed = setOf(Harness.PKG),
                granted = true, disclosure = true
            ),
            floating = false
        )
        assertTrue("四件全真时开关必须画开，与悬浮窗在不在无关", switchShowsOn())
        assertTrue(
            "但那一行不许借'已开启'的句子（投递闸缺着）",
            textOnScreen(str(R.string.home_desc_not_started))
        )
    }

    // ═══════════ 7. CAP3：显示为关 ≠ 用户那一档是关（不许把意图写反） ═══════════

    /**
     * 现场：权限给了、同意了、意图旗标开着，只差没勾 App ⇒ 开关**画成关**。
     * 用户照着直觉拨一下"开" ⇒ 闸门判 `ApplySwitch`，而写口只有"翻"没有"设"
     * （`SetupViewModel` 只交得出 `toggleCapture()`）：这一笔**不许落**——
     * 落了就把他的意图从"开"写成"关"，也就是"拨一下自己弹回去"那一族新毛病。
     *
     * 反例：`CaptureGateStep.ApplySwitch -> viewModel.toggleCapture()`（无条件翻）→
     * `toggleCount` 变 1、`enabledFlow` 掉成 false，这一格红；
     * 反例：拿显示当意图回写（第二本账）→ 同一格红。
     */
    @Test
    fun `clicking an effectively-off switch does not flip the recorded intent off`() {
        val h = Harness(
            enabled = true, allowed = emptySet(),
            granted = true, disclosure = true
        )
        mount(h, floating = true)
        assertFalse("范围没勾时开关画关", switchShowsOn())

        clickCaptureSwitch()

        assertEquals("目标档与记着的那一档相同 ⇒ 那一笔不落", 0, h.toggleCount)
        assertTrue("用户'要开'这一记意图必须原样留着", h.enabledFlow.value)
        assertNull("这一格也不许被送去系统设置（权限已经给了）", shadowOf(app).peekNextStartedActivity())
    }
}
