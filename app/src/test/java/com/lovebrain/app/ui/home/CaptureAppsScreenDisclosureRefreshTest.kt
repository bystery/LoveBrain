package com.lovebrain.app.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.service.CopyCaptureService
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「同意记录」这颗读数必须跟着会作废它的那件事一起刷新——本页有两条重读通路，各钉一格：
 *
 * 1. **采集范围一变**：`SetupViewModel.setCaptureAllowed` 在范围真的变了时把披露版本号清零
 *    （旧同意覆盖不了新范围，服务侧同一条判据当场拒到 consent_pending）。页面上那颗
 *    `disclosureConfirmed` 以前只在装配/resume 那一批 IO 里读一次，于是同页勾完 App，
 *    状态行仍念"已开启"，要退出重进才看得见"开启前请确认"。
 *    反例（回退成组合期快照）→ 第一格红：点完那一行，屏上还是运行中那句。
 * 2. **从别处回来**（ON_RESUME）：离开这一页期间同意记录失效（版本升级、范围在别处被改），
 *    回来只触发 resume、不重新装配。反例（去掉那颗 `LifecycleEventObserver`）→ 第二格红。
 *
 * 走真机那条路：点的是列表里那一行（`setCaptureAllowed` 走真 `SetupViewModel`，作废规则不许替身），
 * 读的是屏幕上那一句话，不读内部变量。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureAppsScreenDisclosureRefreshTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun str(id: Int) = app.getString(id)

    /**
     * 两台可授权 App：勾第二台时范围**非空**，状态行才可能落在"待披露"那一格
     * （`captureTruthOf` 里范围空是另一格，见 `CaptureTruthStateTest`）。
     */
    private class Harness(disclosure: Boolean) {
        /** 真 store：`setCaptureAllowed` 的落账与同意版本号读写都走它，测试不养第二本账 */
        val store = InMemorySettingsStore().apply {
            captureEnabled = true
            captureAllowedPackages = setOf(FIRST)
            if (disclosure) {
                accessibilityDisclosureVersion = CopyCaptureService.CURRENT_DISCLOSURE_VERSION
            }
        }

        /** 真 VM：范围真的变了 ⇒ 同意记录作废这条生产规则必须走真实现，不许夹具替它落账 */
        val model: SetupViewModel = spyk(
            SetupViewModel(store, mockk<DeepSeekRepository>(relaxed = true))
        )

        init {
            // spyk 只替两颗**环境读数**（Robolectric 下扫包没有可装的启动器、无障碍授权读系统设置）；
            // `setCaptureAllowed` / `isAccessibilityDisclosureConfirmed` / 两颗 flow 全是真实现。
            every { model.isCaptureServiceEnabled(any()) } returns true
            every { model.selectableCaptureTargets(any()) } returns listOf(
                SetupViewModel.CaptureApp(FIRST, FIRST_LABEL, secondRejected = false),
                SetupViewModel.CaptureApp(SECOND, SECOND_LABEL, secondRejected = false)
            )
        }

        /** 离开这一页期间同意记录失效（版本升级/别的界面动这颗键），写的就是生产同一颗键 */
        fun invalidateConsentRecord() {
            store.accessibilityDisclosureVersion = 0
        }

        companion object {
            const val FIRST = "com.chat.one"
            const val SECOND = "com.chat.two"
            const val FIRST_LABEL = "CHAT_ONE"
            const val SECOND_LABEL = "CHAT_TWO"
        }
    }

    /** 挂载并等整批 IO 读数落位（两行都在 = 扫包那一批已回来） */
    private fun mount(h: Harness) {
        rule.setContent {
            CaptureAppsScreen(
                viewModel = h.model,
                onBack = {},
                floatingRunning = { true }
            )
        }
        rule.waitForIdle()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText(Harness.SECOND_LABEL)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun textOnScreen(text: String) =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** 运行中那句 / 待披露那句 —— 都用已有资源，不抄中文 */
    private val runningSentence get() = str(R.string.home_capture_status_on)
    private val pendingSentence get() = str(R.string.capture_disclosure_title)

    @Test
    fun `changing the capture scope on this page flips the row to consent-pending at once`() {
        val h = Harness(disclosure = true)
        mount(h)

        assertTrue("前置：四件全真必须念运行中那句", textOnScreen(runningSentence))
        assertFalse(textOnScreen(pendingSentence))

        // 页内勾上第二台 App：范围变了，这一版同意记录在 ViewModel 里当场作废
        rule.onAllNodes(hasText(Harness.SECOND_LABEL))[0].performClick()
        rule.waitForIdle()
        rule.waitUntil(5_000) { textOnScreen(pendingSentence) }

        assertTrue(
            "范围一变就必须同页改口：旧快照会把'开启前请确认'藏到退出重进之后",
            textOnScreen(pendingSentence)
        )
        assertFalse(
            "同意记录已作废，运行中那句不许再留在屏上（服务侧此刻每条事件都拒）",
            textOnScreen(runningSentence)
        )
    }

    @Test
    fun `returning to this page re-reads the consent record on resume`() {
        val h = Harness(disclosure = true)
        mount(h)
        assertTrue("前置：四件全真必须念运行中那句", textOnScreen(runningSentence))

        // 用户离开这一页期间，这一版同意记录失效（版本升级/范围在别处被改）：
        // 组合不重建，只有 Activity 的 ON_RESUME
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        h.invalidateConsentRecord()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        rule.waitUntil(5_000) { textOnScreen(pendingSentence) }

        assertTrue("回来必须重读同意记录，而不是停在出发前那一档", textOnScreen(pendingSentence))
        assertFalse(textOnScreen(runningSentence))
    }
}
