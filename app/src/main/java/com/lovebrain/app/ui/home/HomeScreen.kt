package com.lovebrain.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.lovebrain.app.R
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.KnowledgeBaseActivity
import com.lovebrain.app.ui.theme.AppDimens
import com.lovebrain.app.ui.theme.Border
import com.lovebrain.app.ui.theme.Neutral300
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.SurfaceCard
import com.lovebrain.app.viewmodel.SetupViewModel

/**
 * 首页——悬浮军师状态卡、快捷功能、服务设置与使用概览。
 *
 * 不直接操作 Repository；所有数据通过 [SetupViewModel] 获取。
 */
@Composable
fun HomeScreen(
    viewModel: SetupViewModel,
    onStartService: () -> Unit,
    onOpenPanel: (Int, Boolean) -> Unit,
    onTempHide: () -> Unit,
    onRestore: () -> Unit,
    onNavigateFeedback: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateProviders: () -> Unit,
    onNavigateUsage: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val overlayGranted = Settings.canDrawOverlays(context)
    val activeTicket by viewModel.activeTicket.collectAsStateWithLifecycle()
    val providerReady by viewModel.providerReady.collectAsStateWithLifecycle()

    val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle()
    var accessibilityGranted by remember { mutableStateOf(viewModel.isCaptureServiceEnabled(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityGranted = viewModel.isCaptureServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollState = rememberScrollState()
    val isServiceRunning = FloatingService.instance != null
    val currentWindowState by FloatingService.windowStateFlow.collectAsStateWithLifecycle()

    val statusText = when {
        !overlayGranted -> "未授权"
        !isServiceRunning -> "未启动"
        currentWindowState == FloatingService.WindowState.TEMP_HIDDEN -> "已隐藏"
        else -> "运行中"
    }
    val statusColor = when {
        !overlayGranted || !isServiceRunning -> Neutral300
        currentWindowState == FloatingService.WindowState.TEMP_HIDDEN -> Neutral300
        else -> Primary
    }
    val description = when {
        !overlayGranted -> "需要悬浮窗权限才能显示军师浮窗"
        !isServiceRunning -> "点击启动军师悬浮窗"
        currentWindowState == FloatingService.WindowState.TEMP_HIDDEN -> "军师已暂时隐藏，点击恢复"
        else -> "军师正在运行，长按消息即可捕获"
    }
    val buttonText = when {
        !overlayGranted -> "授权悬浮窗"
        currentWindowState == FloatingService.WindowState.TEMP_HIDDEN -> "恢复军师"
        isServiceRunning -> "打开军师"
        else -> "启动军师悬浮窗"
    }
    val buttonAction = when {
        !overlayGranted -> onStartService
        currentWindowState == FloatingService.WindowState.TEMP_HIDDEN -> onRestore
        isServiceRunning -> { { onOpenPanel(0, false) } }
        else -> onStartService
    }
    val canHide = isServiceRunning &&
        (currentWindowState == FloatingService.WindowState.VISIBLE_BUBBLE ||
         currentWindowState == FloatingService.WindowState.VISIBLE_PANEL)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
    ) {
        HomeTopBar(onNavigateAbout = onNavigateAbout)

        AssistantStatusCard(
            statusText = statusText,
            statusColor = statusColor,
            description = description,
            buttonText = buttonText,
            onButtonClick = buttonAction,
            onHideClick = if (canHide) onTempHide else null
        )

        HomeSectionHeader("快捷功能")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            HomeActionCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_book,
                title = "知识库",
                subtitle = "她的专属记忆",
                onClick = {
                    context.startActivity(Intent(context, KnowledgeBaseActivity::class.java))
                }
            )
            HomeActionCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_feedback,
                title = "反馈案例",
                subtitle = "点踩记录与导出",
                onClick = onNavigateFeedback
            )
        }

        HomeSectionHeader("服务设置")
        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HomeSettingRow(
                    title = "模型供应商",
                    subtitle = activeTicket?.let { "${it.name} · ${it.model.ifBlank { "未选模型" }}" }
                        ?: "未配置供应商",
                    statusText = "",
                    statusColor = if (providerReady) Primary else Neutral300,
                    trailingText = "管理",
                    onTrailingClick = onNavigateProviders,
                    onClick = onNavigateProviders
                )
                HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))

                HomeSettingRow(
                    title = "消息捕获",
                    subtitle = when {
                        !accessibilityGranted -> "尚未授予无障碍权限"
                        captureEnabled -> "已开启·长按消息自动捕获"
                        else -> "已关闭·点击开启"
                    },
                    statusText = if (accessibilityGranted) (if (captureEnabled) "开" else "关") else null,
                    statusColor = if (captureEnabled) Primary else Neutral300,
                    trailingText = if (!accessibilityGranted) "去授权" else null,
                    onTrailingClick = if (!accessibilityGranted) ({ showAccessibilityDisclosure = true }) else null,
                    onClick = if (accessibilityGranted) ({ viewModel.toggleCapture() }) else null
                )
            }
        }

        HomeSectionHeader("使用概览")
        val costStr = if (viewModel.totalCostYuan < 0.01) "￥0" else "￥${String.format("%.2f", viewModel.totalCostYuan)}"
        val rateStr = if (viewModel.totalGenerateCount > 0) "${(viewModel.adoptRate * 100).toInt()}%" else "—"
        UsageSummary(
            totalGenerate = "${viewModel.totalGenerateCount}",
            totalCost = costStr,
            adoptRate = rateStr,
            onClick = onNavigateUsage
        )

        Spacer(Modifier.height(Spacing.xl))
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showAccessibilityDisclosure = false
                viewModel.confirmAccessibilityDisclosure()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }
}
