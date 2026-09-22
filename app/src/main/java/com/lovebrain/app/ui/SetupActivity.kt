package com.lovebrain.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.data.EventBus
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.ui.common.RowActionButton
import com.lovebrain.app.ui.feedback.FeedbackCasesScreen
import com.lovebrain.app.ui.home.AssistantStatusCard
import com.lovebrain.app.ui.home.HomeActionCard
import com.lovebrain.app.ui.home.HomeDestination
import com.lovebrain.app.ui.home.HomeSectionHeader
import com.lovebrain.app.ui.home.HomeSettingRow
import com.lovebrain.app.ui.home.HomeTopBar
import com.lovebrain.app.ui.home.UsageSummary
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.ui.panel.OnboardingFlow
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** 设置页内部尺寸常量 */
private object SetupDimens {
    const val STATUS_DOT_SIZE_DP = 6
    const val CONTENT_MAX_WIDTH_DP = 600
    const val HERO_BUTTON_HEIGHT_DP = 48
    const val HERO_ICON_SIZE_DP = 16
    const val FEATURE_ICON_CONTAINER_DP = 48
    const val FEATURE_ICON_SIZE_DP = 22
    const val FEATURE_ARROW_SIZE_DP = 20
    const val ROW_ACTION_HEIGHT_DP = 48
}

/**
 * 设置页——Activity 宿主 + 根路由 + 系统权限桥接。
 *
 * 分层治理：UI → ViewModel → data。
 * 首页、供应商管理、反馈案例、关于页通过 sealed destination 根级导航切换。
 */
class SetupActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by inject()

    private fun isExistingUser(): Boolean {
        val knowledgeRoot = java.io.File(filesDir, "knowledge")
        val hasKb = knowledgeRoot.exists() && (knowledgeRoot.listFiles()?.isNotEmpty() == true)
        return com.lovebrain.app.domain.OnboardingDecision.isExistingUser(
            hasWorkerTickets = viewModel.securePrefs.getWorkerTickets().isNotEmpty(),
            hasActiveTicketId = !viewModel.securePrefs.activeTicketId.isNullOrBlank(),
            totalGenerateCount = viewModel.securePrefs.totalGenerateCount,
            hasKnowledgeBase = hasKb
        )
    }

    private fun refreshServiceState() {
        // windowState 由 FloatingService 静态持有，onResume 时重新读取即可
    }

    private fun tempHideFloating() {
        val intent = Intent(this, com.lovebrain.app.service.FloatingService::class.java)
            .setAction("com.lovebrain.app.action.TEMP_HIDE")
        ContextCompat.startForegroundService(this, intent)
    }

    private fun restoreFloating() {
        val intent = Intent(this, com.lovebrain.app.service.FloatingService::class.java)
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
        refreshServiceState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!viewModel.securePrefs.hasCompletedOnboarding) {
            if (isExistingUser()) {
                viewModel.securePrefs.hasCompletedOnboarding = true
            }
        }
        val showOnboarding = !viewModel.securePrefs.hasCompletedOnboarding

        setContent {
            LoveBrainTheme {
                if (showOnboarding) {
                    OnboardingFlow(
                        onSkip = {
                            viewModel.securePrefs.hasCompletedOnboarding = true
                            recreate()
                        },
                        onComplete = {
                            viewModel.securePrefs.hasCompletedOnboarding = true
                            recreate()
                        },
                        onOpenSettings = {
                            viewModel.securePrefs.hasCompletedOnboarding = true
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

@Composable
private fun SetupRoot(
    viewModel: SetupViewModel,
    onStartService: () -> Unit,
    onOpenPanel: (Int, Boolean) -> Unit,
    onTempHide: () -> Unit,
    onRestore: () -> Unit
) {
    var destination by remember { mutableStateOf<HomeDestination>(HomeDestination.Home) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = SetupDimens.CONTENT_MAX_WIDTH_DP.dp)) {
            when (destination) {
                HomeDestination.Home -> HomeScreen(
                    viewModel = viewModel,
                    onStartService = onStartService,
                    onOpenPanel = onOpenPanel,
                    onTempHide = onTempHide,
                    onRestore = onRestore,
                    onNavigateFeedback = { destination = HomeDestination.FeedbackCases },
                    onNavigateAbout = { destination = HomeDestination.About },
                    onNavigateProviders = { destination = HomeDestination.Providers },
                    onNavigateUsage = { destination = HomeDestination.Usage },
                    onBack = { (context as? android.app.Activity)?.finish() }
                )
                HomeDestination.FeedbackCases -> {
                    BackHandler { destination = HomeDestination.Home }
                    FeedbackCasesScreen(
                    viewModel = viewModel,
                    onBack = { destination = HomeDestination.Home }
                )
                }
                HomeDestination.About -> {
                    BackHandler { destination = HomeDestination.Home }
                    AboutScreen(
                    onBack = { destination = HomeDestination.Home }
                )
                }
                HomeDestination.Providers -> {
                    BackHandler { destination = HomeDestination.Home }
                    ProviderSection(viewModel = viewModel, onBack = { destination = HomeDestination.Home })
                }
                HomeDestination.Usage -> {
                    BackHandler { destination = HomeDestination.Home }
                    UsageDetailScreen(viewModel = viewModel, onBack = { destination = HomeDestination.Home })
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// 首页
// ═════════════════════════════════════════════════════════════

@Composable
private fun HomeScreen(
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
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

        // 模型供应商管理移至根页面

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

// ═════════════════════════════════════════════════════════════
// 模型供应商管理
// ═════════════════════════════════════════════════════════════

@Composable
private fun ProviderSection(viewModel: SetupViewModel, onBack: () -> Unit) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val activeTicket by viewModel.activeTicket.collectAsStateWithLifecycle()
    val providerReady by viewModel.providerReady.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderTicket?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProviderTicket?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        if (expanded) 90f else 0f,
        label = "providerChevron"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xxxl)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (backInteraction, backScale) = rememberPressScale(0.94f, "providerBack")
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = backScale; scaleY = backScale }
                    .clip(LoveBrainShape.md)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "←",
                    style = AppTypography.titleMedium,
                    color = Primary
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "模型供应商",
                style = AppTypography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .clip(LoveBrainShape.lg)
                .clickable { expanded = !expanded }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(SetupDimens.STATUS_DOT_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(
                                if (providerReady) Primary else Neutral300
                            )
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            activeTicket?.name ?: "未配置供应商",
                            style = AppTypography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            if (activeTicket == null) "点右侧展开添加"
                            else if (!providerReady) "配置不完整"
                            else activeTicket?.model?.ifBlank { "未选模型" } ?: "未选模型",
                            style = AppTypography.labelSmall,
                            color = TextHint,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = TextHint,
                        modifier = Modifier
                            .size(SetupDimens.FEATURE_ARROW_SIZE_DP.dp)
                            .rotate(chevronRotation)
                    )
                }

                if (expanded) {
                    HorizontalDivider(
                        thickness = AppDimens.BORDER_WIDTH_DP.dp,
                        color = Border.copy(alpha = 0.5f)
                    )
                    if (tickets.isEmpty()) {
                        Text(
                            "还没有供应商",
                            style = AppTypography.bodySmall,
                            color = TextHint,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            tickets.forEachIndexed { index, t ->
                                val active = activeTicket?.id == t.id
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (active) PrimaryLight.copy(alpha = 0.5f) else Color.Transparent)
                                        .clickable { viewModel.activateTicket(t.id) }
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(SetupDimens.STATUS_DOT_SIZE_DP.dp)
                                            .clip(CircleShape)
                                            .background(if (active) Primary else Color.Transparent)
                                            .border(
                                                if (active) 0.dp else AppDimens.BORDER_WIDTH_DP.dp,
                                                Neutral300,
                                                CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(Spacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            t.name,
                                            style = AppTypography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            t.model.ifBlank { "未配置模型" },
                                            style = AppTypography.labelSmall,
                                            color = TextHint,
                                            maxLines = 1
                                        )
                                    }
                                    RowActionButton("编辑") { editing = t }
                                    Spacer(Modifier.width(Spacing.sm))
                                    RowActionButton("删除", tint = Error) { pendingDelete = t }
                                }
                                if (index < tickets.lastIndex) {
                                    HorizontalDivider(
                                        thickness = AppDimens.BORDER_WIDTH_DP.dp,
                                        color = Border.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "＋ 添加供应商",
                        style = AppTypography.labelLarge,
                        color = Primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(LoveBrainShape.md)
                            .clickable { showAdd = true }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    )
                }
            }
        }
    }

    if (showAdd) {
        ProviderEditDialog(viewModel = viewModel, ticket = null, onDismiss = { showAdd = false })
    }
    editing?.let { t ->
        ProviderEditDialog(viewModel = viewModel, ticket = t, onDismiss = { editing = null })
    }

    pendingDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${t.name}」？", style = AppTypography.titleLarge) },
            text = { Text("删除后不可恢复，需要重新填写全部配置。确定？", style = AppTypography.bodyMedium, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; viewModel.deleteTicket(t.id) }) {
                    Text("删除", color = Error, style = AppTypography.titleMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }
}

@Composable
private fun ProviderEditDialog(
    viewModel: SetupViewModel,
    ticket: ProviderTicket?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val formError by viewModel.formError.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(ticket?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(ticket?.baseUrl.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf((ticket?.thinkingMode ?: viewModel.globalThinking) == 1) }
    var models by remember { mutableStateOf(ticket?.models.orEmpty()) }
    var currentModel by remember { mutableStateOf(ticket?.model.orEmpty()) }

    var addingModel by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf("") }
    var editIndex by remember { mutableStateOf(-1) }
    var testingModel by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Triple<String, Boolean, String?>?>(null) }

    fun commitModelInput(index: Int) {
        val m = modelInput.trim()
        if (m.isBlank()) return
        val newList = if (index >= 0) {
            models.toMutableList().also { it[index] = m }
        } else if (m !in models) {
            models + m
        } else models
        models = newList
        if (currentModel.isBlank()) currentModel = m
        modelInput = ""
        addingModel = false
        editIndex = -1
    }

    fun deleteModel(index: Int) {
        val removed = models[index]
        val newList = models.toMutableList().also { it.removeAt(index) }
        models = newList
        if (currentModel == removed) currentModel = newList.firstOrNull().orEmpty()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = LoveBrainShape.xl,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xl)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    if (ticket == null) "添加供应商" else "编辑供应商",
                    style = AppTypography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text("供应商名称", style = AppTypography.labelMedium, color = TextSecondary)
                CompactInput(value = name, onValueChange = { name = it }, placeholder = "名称")

                Text("接口地址（自动补全）", style = AppTypography.labelMedium, color = TextSecondary)
                CompactInput(value = baseUrl, onValueChange = { baseUrl = it }, placeholder = "https://api.example.com")
                if (!formError.isNullOrEmpty()) {
                    Text("✗ $formError", style = AppTypography.labelSmall, color = Error)
                }

                Text("API Key", style = AppTypography.labelMedium, color = TextSecondary)
                CompactInput(
                    value = key,
                    onValueChange = { key = it },
 placeholder = if (ticket != null && viewModel.getKeyMask(ticket.id).isNotEmpty()) "留空保留原 Key" else "sk-…",
                    passwordVisible = keyVisible,
                    trailingAction = {
                        TextButton(onClick = { keyVisible = !keyVisible }) {
                            Text(if (keyVisible) "隐藏" else "显示", style = AppTypography.bodySmall, color = Primary)
                        }
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("思考模式", style = AppTypography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    MiniSwitch(checked = thinking, onCheckedChange = { thinking = it })
                }

                Text("模型列表", style = AppTypography.labelMedium, color = TextSecondary)
                models.forEachIndexed { i, m ->
                    if (editIndex == i) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            CompactInput(
                                value = modelInput,
                                onValueChange = { modelInput = it },
                                placeholder = "模型名称",
                                modifier = Modifier.weight(1f)
                            )
                            IconAction(Icons.Filled.Check, "确认") { commitModelInput(i) }
                            IconAction(Icons.Filled.Close, "取消", tint = TextHint) {
                                modelInput = ""; editIndex = -1
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(LoveBrainShape.md)
                                .background(SurfaceInset)
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        ) {
                            Text(
                                m,
                                style = AppTypography.bodyMedium,
                                color = if (m == currentModel) PrimaryDark else TextPrimary,
                                fontWeight = if (m == currentModel) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            IconAction(
                                Icons.Filled.Star,
                                "设为当前",
                                tint = if (m == currentModel) Primary else TextHint
                            ) {
                                currentModel = m
                                if (ticket != null) viewModel.setTicketModel(ticket.id, m)
                            }
                            IconAction(ImageVector.vectorResource(R.drawable.ic_unplug), "测试连接", tint = Primary) {
                                testingModel = m
                                testResult = null
                                scope.launch {
                                    val t = ticket ?: ProviderTicket(name = name.ifBlank { "未命名" }, baseUrl = baseUrl, model = m, models = models)
                                    val result = viewModel.testConnection(t, m, key.trim())
                                    testingModel = null
                                    testResult = Triple(m, result.success, result.message)
                                }
                            }
                            IconAction(Icons.Filled.Edit, "编辑", tint = TextSecondary) {
                                modelInput = m
                                editIndex = i
                            }
                            IconAction(Icons.Filled.Delete, "删除", tint = Error) { deleteModel(i) }
                        }
                    }
                    if (testingModel == m) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.md)) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(AppDimens.LOADING_SPINNER_SIZE_DP.dp), strokeWidth = Spacing.xs)
                            Spacer(Modifier.width(Spacing.sm))
                            Text("测试中…", style = AppTypography.labelSmall, color = TextHint)
                        }
                    }
                }
                if (addingModel) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        CompactInput(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            placeholder = "模型名称",
                            modifier = Modifier.weight(1f)
                        )
                        IconAction(Icons.Filled.Check, "确认") { commitModelInput(-1) }
                        IconAction(Icons.Filled.Close, "取消", tint = TextHint) {
                            modelInput = ""; addingModel = false
                        }
                    }
                } else {
                    Text(
                        "＋ 添加模型",
                        style = AppTypography.labelLarge,
                        color = Primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(LoveBrainShape.md)
                            .clickable { addingModel = true }
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    )
                }
                testResult?.let { (m, ok, msg) ->
                    Text(
                        if (ok) "✓ 连接成功，接口已自动补全" else "✗ $m 连接失败：${msg ?: "请检查配置"}",
                        style = AppTypography.labelSmall,
                        color = if (ok) Success else Error
                    )
                }

                Spacer(Modifier.height(Spacing.xs))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                    ) { Text("取消", style = AppTypography.labelLarge, color = TextSecondary) }
                    val saving by viewModel.saving.collectAsStateWithLifecycle()
                    Button(
                        onClick = {
                            scope.launch {
                                val thinkingInt = if (thinking) 1 else 0
                                val success = viewModel.saveTicketWithProbe(
                                    ticket?.id, name, baseUrl, models, key.trim(), thinkingInt
                                )
                                if (success) onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                        shape = LoveBrainShape.md,
                        enabled = !saving &&
                            name.isNotBlank() &&
                            baseUrl.isNotBlank() &&
                            models.isNotEmpty() &&
                            (ticket != null || key.isNotBlank()),
                        modifier = Modifier.weight(1f).height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (ticket == null) "保存" else "保存修改", style = AppTypography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** 小型开关（36×20 胶囊 + 16dp 圆球） */
@Composable
private fun MiniSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(LoveBrainShape.full)
            .background(if (checked) Primary else Neutral300.copy(alpha = 0.5f))
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange
            )
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(2.dp)
                .size(16.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** 弹窗内行尾图标操作钮——48dp 触摸区满足无障碍下限 */
@Composable
private fun IconAction(
    icon: ImageVector,
    contentDesc: String,
    tint: Color = TextSecondary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(LoveBrainShape.full)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ═════════════════════════════════════════════════════════════
// 无障碍隐私披露 Dialog
// ═════════════════════════════════════════════════════════════

@Composable
private fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启消息捕获前，请确认", style = AppTypography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("LoveBrain 会接收长按与窗口变化事件，并读取相关界面节点文字，用于判断你主动长按的消息以及\u201c复制\u201d菜单。", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("用于把你主动选择的聊天内容�容加入悬浮窗，从而生成回复建议。", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("当你请求 AI 回复时，相关聊天文字和所需知识上下文会发送给你在 LoveBrain 中配置的 AI 模型供应商。", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("捕获内容可在后续操作中写入 LoveBrain 本地知识库，例如聊天归档、谈心记录和画像更新所需的数据。", style = AppTypography.bodyMedium, color = TextSecondary)
                HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))
                Text("LoveBrain 不会通过无障碍服务自动点击、自动发送消息。你可以随时关闭\u201c消息捕获\u201d，或在系统设置中撤销无障碍权限。", style = AppTypography.labelMedium, color = TextHint)
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text("同意并继续", color = Primary, style = AppTypography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
            }
        }
    )
}

// ═════════════════════════════════════════════════════════════
// 关于页
// ═════════════════════════════════════════════════════════════

@Composable
private fun AboutScreen(
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (backInteraction, backScale) = rememberPressScale(0.94f, "aboutBack")
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = backScale; scaleY = backScale }
                    .clip(LoveBrainShape.md)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "←",
                    style = AppTypography.titleMedium,
                    color = Primary
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text("关于", style = AppTypography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }

        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("LoveBrain", style = AppTypography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "版本 v${com.lovebrain.app.BuildConfig.VERSION_NAME}",
                    style = AppTypography.bodyMedium,
                    color = TextSecondary
                )
                Text("帮你更自然地表达", style = AppTypography.bodySmall, color = TextHint)

                HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))

                Text("隐私说明", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "LoveBrain 在本地运行，聊天内容仅发送给你配置的 AI 模型供应商。\n知识库数据存储在本地设备，不上传到任何第三方服务器。",
                    style = AppTypography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // 诊断信息（可折叠）
        var showDiagnostics by remember { mutableStateOf(false) }
        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDiagnostics = !showDiagnostics }
        ) {
            Column(modifier = Modifier.padding(Spacing.xl)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("诊断信息", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(if (showDiagnostics) "▾" else "▸", color = TextHint)
                }
                if (showDiagnostics) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text("SHA: ${com.lovebrain.app.BuildConfig.GIT_SHA}", style = AppTypography.labelSmall, color = TextHint)
                    Text("Build: ${com.lovebrain.app.BuildConfig.BUILD_TYPE}", style = AppTypography.labelSmall, color = TextHint)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// 使用概览详情页
// ═════════════════════════════════════════════════════════════

@Composable
private fun UsageDetailScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (backInteraction, backScale) = rememberPressScale(0.94f, "usageBack")
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = backScale; scaleY = backScale }
                    .clip(LoveBrainShape.md)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "←",
                    style = AppTypography.titleMedium,
                    color = Primary
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text("使用概览", style = AppTypography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }

        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("累计统计", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("生成次数：${viewModel.totalGenerateCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("复制次数：${viewModel.totalCopyCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("采用次数：${viewModel.totalAdoptCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                Text("改写次数：${viewModel.totalRewriteCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                val costStr = if (viewModel.totalCostYuan < 0.01) "￥0" else "￥${String.format("%.2f", viewModel.totalCostYuan)}"
                Text("累计花费：$costStr", style = AppTypography.bodyMedium, color = TextSecondary)
                val rateStr = if (viewModel.totalGenerateCount > 0) "${(viewModel.adoptRate * 100).toInt()}%" else "—"
                Text("采用率：$rateStr", style = AppTypography.bodyMedium, color = TextSecondary)
            }
        }
    }
}
