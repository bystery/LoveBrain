package com.lovebrain.app.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

/**
 * 悬浮权限那一格必须在 **ON_RESUME 现读**（审计项 6）。
 *
 * 旧写法在组合期取一次 `Settings.canDrawOverlays(context)`，然后把它闭进
 * `DisposableEffect(lifecycleOwner)` 的观察器里——从系统授权页回来只触发 Activity 的 ON_RESUME，
 * 不触发重组，于是观察器拿的还是"离开前"那个 false，灯一直按缺权限那一档算。
 *
 * 这里走生产那条现读通道（不传 `overlayGrantedOverride`），用 `ShadowSettings` 演
 * "离开时没授权、回来时已授权"，再经 `moveToState` 走一遍真实的 ON_PAUSE→ON_RESUME。
 * 回退成组合期闭包 → 第二段断言红（missing 里仍带 OverlayPermission）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeOverlayResumeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the lamp re-reads the overlay permission when the activity resumes`() {
        ShadowSettings.setCanDrawOverlays(false)
        // 服务在跑 ⇒ 状态不落在"未启动"那一档，缺项清单才看得见悬浮权限这一条
        val harness = HomeStatusHarness(service = FakeHomeService(initialRunning = true))

        rule.setContent {
            HomeScreen(
                homeStatus = harness.vm,
                onStartService = {},
                onNavigateFeedback = {},
                onNavigateProviders = {},
                onNavigateCaptureApps = {},
                onBack = {}
                // 不传 overlayGrantedOverride：走生产那条 Settings.canDrawOverlays 现读
            )
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            harness.vm.status.value.missing.contains(AdvisorMissing.OverlayPermission)
        }
        assertTrue(
            "授权前：缺项里必须有悬浮权限",
            harness.vm.status.value.missing.contains(AdvisorMissing.OverlayPermission)
        )

        // 用户去系统授权页给了权限再回来：组合不重建，只有 Activity 的 ON_RESUME
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ShadowSettings.setCanDrawOverlays(true)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            !harness.vm.status.value.missing.contains(AdvisorMissing.OverlayPermission)
        }

        assertFalse(
            "授权回来：悬浮权限那条必须消失（旧闭包会把它留成 false）",
            harness.vm.status.value.missing.contains(AdvisorMissing.OverlayPermission)
        )
        assertTrue(
            "权限补齐后只剩'连接还没检查过'这一条（服务在跑）",
            harness.vm.status.value.missing.contains(AdvisorMissing.ConnectionUnchecked)
        )
    }
}
