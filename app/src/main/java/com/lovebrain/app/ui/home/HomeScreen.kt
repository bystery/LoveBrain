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
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.LbRowState
import com.lovebrain.app.core.designsystem.LbActionCard
import com.lovebrain.app.core.designsystem.LbMetricGrid
import com.lovebrain.app.core.designsystem.LbSection
import com.lovebrain.app.core.designsystem.LbSettingRow
import com.lovebrain.app.core.designsystem.LbTopBar
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
    onBack: () -> Unit,
    /**
     * 两个**默认值就是原行为**的入口：首页原先直接读系统权限与 `FloatingService` 这个进程内
     * 单例（`instance != null`），那是四段结构的判据里两个看不见的前提——要证明"隐藏图标只在
     * 可隐藏时出现在右上角"，就得能把四个组合都摆出来。声明成参数之后，生产调用方一字不改，
     * 用例可以逐格喂。
     */
    overlayGrantedOverride: Boolean? = null,
    serviceRunningOverride: Boolean? = null
) {
    val context = LocalContext.current
    val overlayGranted = overlayGrantedOverride ?: Settings.canDrawOverlays(context)
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
    val isServiceRunning = serviceRunningOverride ?: (FloatingService.instance != null)
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
        LbTopBar(
            title = "LoveBrain",
            subtitle = "帮你更自然地表达",
            trailing = { HomeAboutEntry(onNavigateAbout) }
        )

        AssistantStatusCard(
            status = advisor,
            onButtonClick = buttonAction,
            onHideClick = if (canHide) onTempHide else null
        )

        LbSection("快捷功能")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            LbActionCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_book,
                title = "知识库",
                subtitle = "她的专属记忆",
                onClick = {
                    context.startActivity(Intent(context, KnowledgeBaseActivity::class.java))
                }
            )
            LbActionCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_feedback,
                // 这一条进资源、旁边那两条（「知识库」「她的专属记忆」等）暂时留着，不是手抖改一半：
                // `feedback_cases_title` 是反馈案例页页头**已经有**的那一份（账本 §58），
                // 入口卡片与目标页页头念同一个名字，抄第二份就迟早会漂；
                // 那两条还没有第二份，等它们自己长出资源时再一起收。
                title = stringResource(R.string.feedback_cases_title),
                subtitle = "点踩记录与导出",
                onClick = onNavigateFeedback
            )
        }

        LbSection("服务设置")
        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                LbSettingRow(
                    title = "模型供应商",
                    subtitle = activeTicket?.let { "${it.name} · ${it.model.ifBlank { "未选模型" }}" }
                        ?: "未配置供应商",
                    // 供应商行只要一颗点：以前用 statusText = "" 表达"没有词"，
                    // 现在 dot 这一旋钮自己就能说这件事
                    dot = if (providerReady) LbRowState.Ready else LbRowState.NotReady,
                    trailingText = "管理",
                    onTrailingClick = onNavigateProviders,
                    onClick = onNavigateProviders
                )
                HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))

                LbSettingRow(
                    title = stringResource(R.string.capture_apps_title),
                    subtitle = when {
                        !accessibilityGranted -> stringResource(R.string.home_capture_no_permission)
                        captureAllowedCount == 0 -> stringResource(R.string.capture_apps_row_subtitle_none)
                        captureEnabled -> stringResource(R.string.home_capture_status_on)
                        else -> stringResource(R.string.home_capture_status_off)
                    },
                    dot = if (captureEnabled) LbRowState.Ready else LbRowState.NotReady,
                    // 这两个字以前**根本不会被画出来**（组件里没有画 statusText 的那一行）
                    statusText = if (accessibilityGranted) {
                        stringResource(
                            if (captureEnabled) R.string.home_on else R.string.home_off
                        )
                    } else null,
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

        LbSection("使用概览")
        val costStr = if (viewModel.totalCostYuan < 0.01) "￥0" else "￥${String.format("%.2f", viewModel.totalCostYuan)}"
        val rateStr = if (viewModel.totalGenerateCount > 0) "${(viewModel.adoptRate * 100).toInt()}%" else "—"
        LbMetricGrid(
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
