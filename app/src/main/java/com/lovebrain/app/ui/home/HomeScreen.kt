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
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.KnowledgeBaseActivity
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.Neutral300
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
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
    onNavigateCaptureApps: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val overlayGranted = Settings.canDrawOverlays(context)
    val activeTicket by viewModel.activeTicket.collectAsStateWithLifecycle()
    val providerReady by viewModel.providerReady.collectAsStateWithLifecycle()

    val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle()
    val captureAllowed by viewModel.captureAllowedPackages.collectAsStateWithLifecycle()
    val captureAllowedCount = captureAllowed.size
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

    // 一份快照，不是五份平行判据。旧写法是 statusText / statusColor / description /
    // buttonText / buttonAction 五个 `when` 各把同样三个条件重判一遍——五份判据可以各说各话，
    // 而加状态时最容易忘的恰恰是"颜色那一份"。
    // 唯一的行为差别见 advisorStatus 的注释（服务活着但窗口从未出现 → 不再说"运行中"）。
    val advisor = advisorStatus(
        overlayGranted = overlayGranted,
        serviceRunning = isServiceRunning,
        window = currentWindowState
    )
    val buttonAction = when (advisor.intent) {
        AdvisorIntent.Start -> onStartService
        AdvisorIntent.OpenPanel -> { { onOpenPanel(0, false) } }
        AdvisorIntent.Restore -> onRestore
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
            status = advisor,
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
                    title = stringResource(R.string.capture_apps_title),
                    subtitle = when {
                        !accessibilityGranted -> stringResource(R.string.home_capture_no_permission)
                        captureAllowedCount == 0 -> stringResource(R.string.capture_apps_row_subtitle_none)
                        captureEnabled -> stringResource(R.string.home_capture_status_on)
                        else -> stringResource(R.string.home_capture_status_off)
                    },
                    statusText = if (accessibilityGranted) {
                        stringResource(
                            if (captureEnabled) R.string.home_on else R.string.home_off
                        )
                    } else null,
                    statusColor = if (captureEnabled) Primary else Neutral300,
                    trailingText = if (!accessibilityGranted) {
                        stringResource(R.string.home_grant_accessibility)
                    } else {
                        stringResource(R.string.capture_apps_row_subtitle, captureAllowedCount)
                    },
                    onTrailingClick = if (!accessibilityGranted) ({ showAccessibilityDisclosure = true })
                    else ({ onNavigateCaptureApps() }),
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
