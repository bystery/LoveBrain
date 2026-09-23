package com.lovebrain.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.lovebrain.app.data.EventBus
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.home.SetupRoot
import com.lovebrain.app.ui.panel.OnboardingFlow
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.SetupViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 设置页——Activity 宿主 + 系统权限桥接 + 根路由入口。
 *
 * 职责仅限于：
 * - 取 ViewModel、决定是否显示引导
 * - 悬浮窗权限申请与 Service 启停
 * - 将回调委托给 [SetupRoot] 组合根
 *
 * 页面 UI 拆分到 `ui/home/` 包：HomeScreen、ProviderSection、AboutScreen、UsageDetailScreen。
 */
class SetupActivity : ComponentActivity() {

    // by viewModel() 而非 by inject()：后者不经 ViewModelStore，配置变更后 VM 内存态会丢
    private val viewModel: SetupViewModel by viewModel()

    private fun tempHideFloating() {
        val intent = Intent(this, FloatingService::class.java)
            .setAction("com.lovebrain.app.action.TEMP_HIDE")
        ContextCompat.startForegroundService(this, intent)
    }

    private fun restoreFloating() {
        val intent = Intent(this, FloatingService::class.java)
            .setAction("com.lovebrain.app.action.RESTORE")
        ContextCompat.startForegroundService(this, intent)
    }

    private var pendingOverlayStart = false
    private var pendingPanelMode: Int? = null
    private var pendingShowPlan: Boolean = false

    private fun startFloatingService(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayStart = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return false
        }
        ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
        return true
    }

    private fun openPanelFromHome(mode: Int, showPlan: Boolean) {
        if (!startFloatingService()) {
            pendingPanelMode = mode
            pendingShowPlan = showPlan
            return
        }
        EventBus.requestPanel(mode, showPlan)
    }

    override fun onResume() {
        super.onResume()
        if (pendingOverlayStart && Settings.canDrawOverlays(this)) {
            pendingOverlayStart = false
            ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
            pendingPanelMode?.let { mode ->
                EventBus.requestPanel(mode, pendingShowPlan)
                pendingPanelMode = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 敏感页面 FLAG_SECURE——防止 API Key / 知识库内容在最近任务截图中泄露
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val showOnboarding = viewModel.shouldShowOnboarding()

        setContent {
            LoveBrainTheme {
                if (showOnboarding) {
                    OnboardingFlow(
                        onSkip = {
                            viewModel.completeOnboarding()
                            recreate()
                        },
                        onComplete = {
                            viewModel.completeOnboarding()
                            recreate()
                        },
                        onOpenSettings = {
                            viewModel.completeOnboarding()
                            recreate()
                        }
                    )
                } else {
                    SetupRoot(
                        viewModel = viewModel,
                        onStartService = { startFloatingService() },
                        onOpenPanel = { mode, showPlan -> openPanelFromHome(mode, showPlan) },
                        onTempHide = { tempHideFloating() },
                        onRestore = { restoreFloating() }
                    )
                }
            }
        }
    }
}
