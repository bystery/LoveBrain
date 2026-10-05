package com.lovebrain.app.viewmodel

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.service.CopyCaptureService
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.lovebrain.app.data.DeepSeekRepository

/**
 * CAP1（2026-10-06）：`SetupViewModel.guideFacts` 的 `captureOn` 那一格判据。
 *
 * 取证账（source-10 候选①）：旧判据 `captureOn = 开关 ∧ 披露同意`，**不看范围**；
 * 服务侧 `CapturePolicy` 空 allowlist 却 fail-closed 全拒——引导层因此能对着一台
 * 什么都抓不到的机器念"捕获已开启"（用户原话的形态）。
 * 本族只钉这一格判据；探针/空库两族归 A2 席，`resolveGuideCursor` 归 `GuideCursorResolveTest`。
 *
 * CAP3/CAP4（2026-10-06，同一族）：又补了第四件——**无障碍没授予**。
 * 那一格里 `onServiceConnected` 根本进不来，服务一条正文都收不到，
 * 而引导可以拿着"旗标 + 同意 + 范围"三件真判 DONE ⇒ 首页罩子念"这一步过了"、
 * 捕获页念"还没授予"，两张嘴对同一台机器念相反的话（用户 01:4x 那句"我已经开启了啊"就是这一格）。
 * 加完之后 `guideFacts.captureOn` 是页面 `captureTruthOf` 五颗读数的**严格子集**：
 * 唯一不参与的那一颗是悬浮窗（投递闸，不是同意闸——用户把该给的四件都给完了，
 * 引导不该因为服务还没起来把他卡在 CAPTURE 那一格）。
 *
 * 回退成什么会红：
 * - 把 `&& _captureAllowedPackages.value.isNotEmpty()` 删回去 → 第一格红；
 * - 反过来把范围当**唯一**判据（开关/披露不再要求）→ 第三格红；
 * - 把 `isCaptureServiceEnabled(context)` 那一颗删回去 → 第四格红（"三件齐、权限没给"当场翻成 true）；
 * - 把悬浮窗也拉进这一格 → 第二格红（那一档四件齐、服务没起，引导必须算过）。
 */
@RunWith(RobolectricTestRunner::class)
// VM 由构造参数直接喂 fake 仓库，容器一眼都用不上；Robolectric 只要一颗不 startKoin 的空壳 App。
// 默认清单那颗 App 在 onCreate 无条件 startKoin，而 GlobalContext 是 JVM 静态的——第二个用例再建
// Application 就抛 KoinAppAlreadyStartedException，抛在装配阶段（早于任何 @Before，用例里拦不住）。
// 真值表与 `captureOn` 的合取判据取面一个字没动：换的只是 Application。
@Config(application = UiProbeApplication::class)
class GuideFactsCaptureScopeTest {

    private fun storeWith(
        enabled: Boolean,
        consent: Boolean,
        allowlist: Set<String>
    ): InMemorySettingsStore = InMemorySettingsStore().apply {
        captureEnabled = enabled
        accessibilityDisclosureVersion = if (consent) {
            CopyCaptureService.CURRENT_DISCLOSURE_VERSION
        } else {
            CopyCaptureService.CURRENT_DISCLOSURE_VERSION - 1
        }
        captureAllowedPackages = allowlist
    }

    private fun factsFor(
        store: InMemorySettingsStore,
        accessibilityGranted: Boolean = true
    ): SetupViewModel.GuideFacts {
        // VM 在构造时抓 store 的快照（StateFlow 初值），所以摆好偏好位再 new。
        val app = ApplicationProvider.getApplicationContext<Application>()
        // 权限这一件不读偏好、读系统设置，所以它必须由本格自己摆出来（不摆=Robolectric 给 null=false）。
        // 组件全名与生产那一颗 `isCaptureServiceEnabled` 逐字同一把尺：换写法当场读不到，格就红在摆不上。
        Settings.Secure.putString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            if (accessibilityGranted) "${app.packageName}/com.lovebrain.app.service.CopyCaptureService" else null
        )
        val model = SetupViewModel(
            securePrefs = store,
            deepSeekRepo = mockk<DeepSeekRepository>(relaxed = true)
        )
        return model.guideFacts(app)
    }

    @Test
    fun `an empty allowlist is never captureOn however the switch reads`() {
        val f = factsFor(
            storeWith(enabled = true, consent = true, allowlist = emptySet())
        )
        assertFalse(
            "开关开着、披露同意过、范围没选 ⇒ 服务侧每条事件 allowlist_empty 全拒，" +
                "引导层不许念'捕获已开启'",
            f.captureOn
        )
    }

    @Test
    fun `all four inputs true makes captureOn true`() {
        val f = factsFor(
            storeWith(enabled = true, consent = true, allowlist = setOf("com.tencent.mm")),
            accessibilityGranted = true
        )
        assertTrue(
            "权限给了、开关拨着、这一版同意过、范围选了 ⇒ 引导算这一格过了。" +
                "注意这一档**不要求悬浮窗在跑**：那是投递闸，不是同意闸",
            f.captureOn
        )
    }

    /**
     * 第四件（无障碍授权）单独钉一格：CAP3/CAP4 之前这一格根本不存在——
     * 三件齐、权限没给时旧判据直接判 `captureOn = true`，于是首页罩子说"这步过了"、
     * 捕获页说"还没授予"（用户 01:4x 原话「我已经开启了啊」那一串的分叉源头之一）。
     * 把 `isCaptureServiceEnabled(context)` 从合取里删回去，这一格当场红。
     */
    @Test
    fun `permission not granted keeps captureOn false however the other three read`() {
        assertFalse(
            "开关开着、这一版同意过、范围也选了，但无障碍没授予 ⇒ 服务连 onServiceConnected 都进不来，" +
                "引导不许念'捕获已开启'",
            factsFor(
                storeWith(enabled = true, consent = true, allowlist = setOf("com.tencent.mm")),
                accessibilityGranted = false
            ).captureOn
        )
    }

    @Test
    fun `switch off or consent missing still keeps captureOn false with a chosen scope`() {
        assertFalse(
            factsFor(storeWith(enabled = false, consent = true, allowlist = setOf("com.tencent.mm")))
                .captureOn
        )
        assertFalse(
            factsFor(storeWith(enabled = true, consent = false, allowlist = setOf("com.tencent.mm")))
                .captureOn
        )
    }
}
