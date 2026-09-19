package com.lovebrain.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
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
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.ui.panel.OnboardingFlow
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** 设置页内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object SetupDimens {
    const val STATUS_DOT_SIZE_DP = 6          // 状态徽章圆点
    const val CONTENT_MAX_WIDTH_DP = 600      // 首页内容最大宽度（平板防拉伸）
    const val HERO_BUTTON_HEIGHT_DP = 40      // Hero 启动按钮高
    const val HERO_ICON_SIZE_DP = 16          // Hero 播放图标
    const val FEATURE_ICON_CONTAINER_DP = 40  // 快捷卡图标容器
    const val FEATURE_ICON_SIZE_DP = 22       // 快捷卡图标本体（22dp 出网格： ④）
    const val FEATURE_ARROW_SIZE_DP = 20      // 快捷卡右箭头
    const val ROW_ACTION_HEIGHT_DP = 32       // 行内操作按钮高（去授权等；供应商行同款常量已随组件迁往 ProviderManageActivity）
}

/**
 * 设置页（ 重建）：工单式模型供应商管理。
 *
 * 分层治理：本 Activity 不再直注 SecurePrefs/DeepSeekRepository，
 * 全部经 [SetupViewModel]（ui → ViewModel → data）。
 * 首页骨架（启动悬浮窗 + 快捷功能）保留；原"配置与状态"折叠卡替换为工单管理卡。
 */
class SetupActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by inject()

    /**
     * F12/P1-G: 检测是否为已有使用痕迹的老用户。
     * 升级引入 onboarding 时，有任意已有使用痕迹的安装视为老用户。
     * 检测项：已有工单、已有 API Key、已有知识库目录、累计生成次数 > 0。
     */
    private fun isExistingUser(): Boolean {
        // 已有工单
        if (viewModel.securePrefs.getWorkerTickets().isNotEmpty()) return true
        // 已有激活工单 ID
        if (!viewModel.securePrefs.activeTicketId.isNullOrBlank()) return true
        // 累计生成次数 > 0
        if (viewModel.securePrefs.totalGenerateCount > 0) return true
        // 已有知识库目录
        val knowledgeRoot = java.io.File(filesDir, "knowledge")
        if (knowledgeRoot.exists() && (knowledgeRoot.listFiles()?.isNotEmpty() == true)) return true
        return false
    }

    /** 刷新悬浮窗服务状态——onResume 时调用 */
    private fun refreshServiceState() {
        // windowState 由 FloatingService 静态持有，onResume 时重新读取即可
    }

    /** 发送临时隐藏命令到悬浮窗服务 */
    private fun tempHideFloating() {
        val intent = Intent(this, com.lovebrain.app.service.FloatingService::class.java)
            .setAction("com.lovebrain.app.action.TEMP_HIDE")
        ContextCompat.startForegroundService(this, intent)
    }

    /** 发送恢复命令到悬浮窗服务 */
    private fun restoreFloating() {
        val intent = Intent(this, com.lovebrain.app.service.FloatingService::class.java)
            .setAction("com.lovebrain.app.action.RESTORE")
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * UX-03：用户主动点击“启动”后发现没有悬浮窗权限，跳授权页前标记。
     * 授权后回 App 时 onResume 检测：如果已授权且标记为 true，自动完成启动。
     * 避免用户授权后还需再点一次。
     */
    private var pendingOverlayStart = false

    /**
     * UX-03：记录用户点击启动时想打开的面板模式（null = 仅启动悬浮球）。
     * 授权回来后如果非 null，自动启动并请求对应面板。
     */
    private var pendingPanelMode: Int? = null
    private var pendingShowPlan: Boolean = false

    /** 启动悬浮窗服务。返回是否真正启动（未授权时跳授权页并返回 false） */
    private fun startFloatingService(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            // UX-03：标记用户主动点了启动，授权回来后自动完成
            pendingOverlayStart = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return false
        }
        // RA-01：使用 startForegroundService 启动前台服务（specialUse FGS）
        ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
        return true
    }

    /** 首页功能卡片直达：启动悬浮窗 + 通知服务打开面板对应功能 */
    private fun openPanelFromHome(mode: Int, showPlan: Boolean) {
        if (!startFloatingService()) {
            // UX-03：记录用户想打开的面板模式
            pendingPanelMode = mode
            pendingShowPlan = showPlan
            return
        }
        EventBus.requestPanel(mode, showPlan)
    }

    override fun onResume() {
        super.onResume()
        // UX-03：授权回来后自动完成刚才的启动动作
        if (pendingOverlayStart && Settings.canDrawOverlays(this)) {
            pendingOverlayStart = false
            ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
            // 如果用户当时是想打开某个面板
            pendingPanelMode?.let { mode ->
                EventBus.requestPanel(mode, pendingShowPlan)
                pendingPanelMode = null
            }
        }
        // 刷新服务真实状态——windowState 已在 service 内同步
        refreshServiceState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // F12/P1-G: 检测是否需要引导——已有使用痕迹的老用户不强制重走
        // 首次引入 onboarding 时做 migration：检测已有 KB/工单/设置/历史版本
        if (!viewModel.securePrefs.hasCompletedOnboarding) {
            if (isExistingUser()) {
                viewModel.securePrefs.hasCompletedOnboarding = true
            }
        }
        val showOnboarding = !viewModel.securePrefs.hasCompletedOnboarding

        setContent {
            // 暗色模式已删，全站固定亮色，不再检测系统暗色
            LoveBrainTheme {
                if (showOnboarding) {
                    OnboardingFlow(
                        onSkip = {
                            viewModel.securePrefs.hasCompletedOnboarding = true
                            // 重新渲染主页
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
                    SetupScreen(
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
private fun SetupScreen(
    viewModel: SetupViewModel,
    onStartService: () -> Unit,
    onOpenPanel: (Int, Boolean) -> Unit,
    onTempHide: () -> Unit,
    onRestore: () -> Unit
) {
    val context = LocalContext.current

    // 一页化：首页 + 配置（现为工单管理）同页，无独立设置页
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = SetupDimens.CONTENT_MAX_WIDTH_DP.dp)) {
            HomeTabContent(
                viewModel = viewModel,
                onStartService = onStartService,
                onOpenPanel = onOpenPanel,
                onOpenKnowledgeBase = {
                    context.startActivity(Intent(context, KnowledgeBaseActivity::class.java))
                },
                onTempHide = onTempHide,
                onRestore = onRestore
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════
// Tab 0：首页（Hero + 快捷功能 + 消息捕获开关 + 供应商状态入口卡）
// ═════════════════════════════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTabContent(
    viewModel: SetupViewModel,
    onStartService: () -> Unit,
    onOpenPanel: (Int, Boolean) -> Unit,
    onOpenKnowledgeBase: () -> Unit,
    onTempHide: () -> Unit,
    onRestore: () -> Unit
) {
    val context = LocalContext.current
    val overlayGranted = Settings.canDrawOverlays(context)
    val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle()

    //  ：无障碍授权态（从系统设置页返回时刷新；授权判定在 VM，ui 不直读系统设置）
    var accessibilityGranted by remember { mutableStateOf(viewModel.isCaptureServiceEnabled(context)) }
    val captureLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(captureLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                accessibilityGranted = viewModel.isCaptureServiceEnabled(context)
            }
        }
        captureLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { captureLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // RA-02：无障碍隐私披露 Dialog 状态
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var showFeedbackCaseDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // 首页为根页，无返回语义，不套页头骨架（保留 24dp 顶部留白）；仅留水平内边距
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
            .padding(horizontal = Spacing.xxxl)
    ) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
    ) {
        // ── Hero 主卡：启动军师悬浮窗（渐变降饱和 + 细边框）──
        Card(
            shape = LoveBrainShape.xl,
            colors = CardDefaults.cardColors(containerColor = PrimaryLight),
            modifier = Modifier
                .fillMaxWidth()
                .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.xl)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryLight, Primary.copy(alpha = 0.55f)),
                            start = Offset(0f, 0f),
                            end = Offset(1200f, 600f)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "长按自动读取，让军师帮你回",
                        style = AppTypography.titleMedium,
                        color = PrimaryDark,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    val (heroInteraction, heroScale) = rememberPressScale(0.96f, "heroScale")
                    // 阻断D修复：主按钮按状态变文案——隐藏时显示"恢复"
                    val isServiceRunning = FloatingService.instance != null
                    val currentWindowState by FloatingService.windowStateFlow
                        .collectAsStateWithLifecycle()
                    val heroButtonText = when {
                        !overlayGranted -> "授权悬浮窗（首次）"
                        isServiceRunning && currentWindowState == FloatingService.WindowState.TEMP_HIDDEN -> "恢复军师悬浮窗"
                        else -> "启动军师悬浮窗"
                    }
                    val heroButtonAction = when {
                        isServiceRunning && currentWindowState == FloatingService.WindowState.TEMP_HIDDEN -> onRestore
                        else -> onStartService
                    }
                    Button(
                        onClick = heroButtonAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = Color.White
                        ),
                        shape = LoveBrainShape.md,
                        interactionSource = heroInteraction,
                        modifier = Modifier
                            .height(SetupDimens.HERO_BUTTON_HEIGHT_DP.dp)
                            .graphicsLayer { scaleX = heroScale; scaleY = heroScale }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(SetupDimens.HERO_ICON_SIZE_DP.dp)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            heroButtonText,
                            style = AppTypography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // ── Hero 卡内临时隐藏/恢复次按钮 ──
                    // 阻断D修复：复用上方已声明的 isServiceRunning 和 currentWindowState
                    when {
                        // 未授权：不显示次按钮
                        !overlayGranted -> { }
                        // 已停止：不显示无效隐藏按钮
                        !isServiceRunning -> { }
                        // 正常可见：显示"暂时隐藏"次按钮
                        currentWindowState == FloatingService.WindowState.VISIBLE_BUBBLE ||
                        currentWindowState == FloatingService.WindowState.VISIBLE_PANEL -> {
                            Spacer(Modifier.height(Spacing.sm))
                            TextButton(
                                onClick = onTempHide,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = PrimaryDark
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(
                                    "暂时隐藏",
                                    style = AppTypography.labelMedium
                                )
                            }
                        }
                        // 已临时隐藏：主按钮已变为"恢复军师悬浮窗"，不再重复第二个恢复按钮
                        currentWindowState == FloatingService.WindowState.TEMP_HIDDEN -> {
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "军师已暂时隐藏",
                                style = AppTypography.labelSmall,
                                color = TextHint
                            )
                        }
                    }
                }
            }
        }

        // ── 快捷功能网格（2×2）──
        Text("快捷功能", style = AppTypography.titleLarge, color = TextPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_book,
                title = "知识库",
                subtitle = "她的专属记忆",
                iconTint = Primary,
                container = PrimaryLight,
                onClick = onOpenKnowledgeBase
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_bulb,
                title = "今日锦囊",
                subtitle = "每日做法建议",
                iconTint = Primary,
                container = PrimaryLight,
                available = true,
                onClick = { onOpenPanel(0, true) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_bookmarks,
                title = "谈心模式",
                subtitle = "分析关系困局",
                iconTint = Primary,
                container = PrimaryLight,
                available = true,
                onClick = { onOpenPanel(1, false) }
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_chart,
                title = "感情五维",
                subtitle = "亲密·信任·承诺",
                iconTint = Primary,
                container = PrimaryLight,
                available = true,
                onClick = { onOpenPanel(0, false) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // F02: 反馈案例入口
            FeatureCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_feature_book,
                title = "反馈案例",
                subtitle = "点踩记录与导出",
                iconTint = Primary,
                container = PrimaryLight,
                available = true,
                onClick = { showFeedbackCaseDialog = true }
            )
            Box(modifier = Modifier.weight(1f))
        }

        if (showFeedbackCaseDialog) {
            FeedbackCaseDialog(
                onDismiss = { showFeedbackCaseDialog = false }
            )
        }

        // ── 消息捕获开关（ 问题 4）──
        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "消息捕获",
                        style = AppTypography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (accessibilityGranted) "关闭后长按消息不再捕获；开启需无障碍权限"
                        else "尚未授予无障碍权限，无法捕获消息",
                        style = AppTypography.labelSmall,
                        color = TextHint
                    )
                }
                //  ：去授权入口（仅未授权显示）
                if (!accessibilityGranted) {
                    TextButton(
                        onClick = { showAccessibilityDisclosure = true },
                        modifier = Modifier.height(SetupDimens.ROW_ACTION_HEIGHT_DP.dp)
                    ) {
                        Text(
                            "去授权",
                            style = AppTypography.labelMedium,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                }
                Switch(
                    checked = accessibilityGranted && captureEnabled,
                    enabled = accessibilityGranted,
                    onCheckedChange = { viewModel.toggleCapture() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Primary,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = Neutral300.copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.White,
                        disabledCheckedTrackColor = Primary.copy(alpha = 0.4f),
                        disabledCheckedThumbColor = Color.White,
                        disabledUncheckedTrackColor = Neutral300.copy(alpha = 0.3f),
                        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f)
                    )
                )
            }
        }

        // ── 模型供应商（多模型批 /：主页直接管理，弹窗编辑，一供应商多模型）──
        ProviderSection(viewModel)

        // F12: 详细性能统计
        StatsSection(viewModel)
        }
    }

    // RA-02：无障碍隐私披露 Dialog
    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showAccessibilityDisclosure = false
                viewModel.confirmAccessibilityDisclosure()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = {
                showAccessibilityDisclosure = false
            }
        )
    }
}

/** 功能入口卡片：可用 / 即将推出（灰色占位）双态；按压缩放反馈（M3 press） */
@Composable
private fun FeatureCard(
    modifier: Modifier,
    @androidx.annotation.DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    iconTint: Color,
    container: Color,
    available: Boolean = true,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "featureCardScale")

    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = if (available) SurfaceCard else SurfaceInset),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.lg)
            .border(AppDimens.BORDER_WIDTH_DP.dp, if (available) Border else BorderLight, LoveBrainShape.lg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = available,
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(SetupDimens.FEATURE_ICON_CONTAINER_DP.dp)
                        .clip(LoveBrainShape.md)
                        .background(container),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(SetupDimens.FEATURE_ICON_SIZE_DP.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (available) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextHint,
                        modifier = Modifier.size(SetupDimens.FEATURE_ARROW_SIZE_DP.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                title,
                style = AppTypography.titleMedium,
                color = if (available) TextPrimary else TextHint,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                subtitle,
                style = AppTypography.labelSmall,
                color = TextHint,
                maxLines = 1
            )
            if (!available) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "即将推出",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    modifier = Modifier
                        .clip(LoveBrainShape.full)
                        .background(Neutral300.copy(alpha = 0.4f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
        }
    }
}


// ═════════════════════════════════════════════════════════════
// 模型供应商（/：主页直管 + 弹窗编辑 + 一供应商多模型）
// ═════════════════════════════════════════════════════════════

@Composable
private fun ProviderSection(viewModel: SetupViewModel) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val activeTicket by viewModel.activeTicket.collectAsStateWithLifecycle()
    val providerReady by viewModel.providerReady.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderTicket?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProviderTicket?>(null) }
    // 主人 2026-08-31：默认收起；列表与"＋添加供应商"藏起来，点 > 展开
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        if (expanded) 90f else 0f,
        label = "providerChevron"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 分区标题（小字档）——添加入口移入展开区
        Text(
            "模型供应商",
            style = AppTypography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Spacing.sm)
        )

        // 折叠卡：头部常驻（状态点 + 供应商名 + 当前模型小字 + 展开钮）；展开态出列表与添加入口
        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .clip(LoveBrainShape.lg)
                .clickable { expanded = !expanded }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── 卡片头 ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) {
                    // UX-02：状态点真正 Ready=蓝；不完整=灰
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
                    // 展开箭头（> → ∨）
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = TextHint,
                        modifier = Modifier
                            .size(SetupDimens.FEATURE_ARROW_SIZE_DP.dp)
                            .rotate(chevronRotation)
                    )
                }

                // ── 展开态：列表 + 添加入口 ──
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
                                    // 行状态点：●激活 ○未激活（空心描边）
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
                    // ＋ 添加供应商（蓝字文字钮，与全 App 同款）
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

/** 供应商编辑弹窗（///）：名称/地址/Key + 思考模式小开关 + 模型列表（设为当前/测试/编辑/删除） */
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
                // 补标题（2A 同款字号语言）：新建/编辑双态
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

                // 思考模式：小开关（修复 M3 Switch 过大/点击不灵问题）
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

/** 小型开关（36×20 胶囊 + 16dp 圆球）：替代 M3 Switch（尺寸过大、嵌套点击不灵） */
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

/** 弹窗内行尾图标操作钮（28dp 热区 + 16dp 字形） */
@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    tint: Color = TextSecondary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(LoveBrainShape.full)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** 紧凑圆角单行输入框已上提至 ui/common/CompactInput.kt（问卷页与供应商弹窗共用） */

// ═════════════════════════════════════════════════════════════
// RA-02：无障碍隐私披露 Dialog
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
                Text("用于把你主动选择的聊天内容加入悬浮窗，从而生成回复建议。", style = AppTypography.bodyMedium, color = TextSecondary)
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
// F12: 性能统计卡片
// ═════════════════════════════════════════════════════════════

@Composable
private fun StatsSection(viewModel: SetupViewModel) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg)
    ) {
        Text(
            "使用统计",
            style = AppTypography.titleLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(Spacing.sm))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(Spacing.xl)) {
                // 累计生成
                StatRow(label = "累计生成", value = "${viewModel.totalGenerateCount} 次")
                Spacer(Modifier.height(Spacing.md))

                // 累计花费
                val costStr = if (viewModel.totalCostYuan < 0.01) "￥0" else "￥${String.format("%.2f", viewModel.totalCostYuan)}"
                StatRow(label = "累计花费", value = costStr)
                Spacer(Modifier.height(Spacing.md))

                // 复制次数
                StatRow(label = "复制次数", value = "${viewModel.totalCopyCount} 次")
                Spacer(Modifier.height(Spacing.md))

                // 采用次数
                StatRow(label = "采用（已发送）", value = "${viewModel.totalAdoptCount} 次")
                Spacer(Modifier.height(Spacing.md))

                // 改写次数
                StatRow(label = "改写次数", value = "${viewModel.totalRewriteCount} 次")
                Spacer(Modifier.height(Spacing.md))

                // 采用率
                val rateStr = if (viewModel.totalGenerateCount > 0)
                    "${(viewModel.adoptRate * 100).toInt()}%"
                else "—"
                StatRow(label = "采用率", value = rateStr, highlight = true)

                Spacer(Modifier.height(Spacing.md))
                HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "采用率 = 记录已发送 / 累计生成。复制和采用独立统计。",
                    style = AppTypography.labelSmall,
                    color = TextHint
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppTypography.bodyMedium,
            color = TextSecondary
        )
        Text(
            value,
            style = AppTypography.titleMedium,
            color = if (highlight) Primary else TextPrimary,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}


/**
 * F02: 反馈案例管理弹层——查看/筛选/导出。
 *
 * 显示条数，按时间、原因、档案、模型筛选。
 * 支持导出 Markdown + JSON，不记录凭证。
 */
@Composable
private fun FeedbackCaseDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cases by remember { mutableStateOf<List<com.lovebrain.app.model.FeedbackCase>>(emptyList()) }
    var filterCategory by remember { mutableStateOf<com.lovebrain.app.model.FeedbackCategory?>(null) }
    var exportText by remember { mutableStateOf("") }
    var showExport by remember { mutableStateOf(false) }

    // 加载案例
    LaunchedEffect(Unit) {
        val dir = java.io.File(context.filesDir, "feedback")
        val file = java.io.File(dir, "cases.json")
        if (file.exists() && file.length() > 0) {
            runCatching {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val text = file.readText()
                if (text.isNotBlank()) {
                    cases = json.decodeFromString(text)
                }
            }
        }
    }

    val filtered = if (filterCategory == null) cases else cases.filter { filterCategory!! in it.categories }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = LoveBrainShape.lg,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth().padding(Spacing.md)
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                // 标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "反馈案例（${filtered.size}条）",
                        style = AppTypography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "关闭",
                        style = AppTypography.labelMedium,
                        color = TextHint,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ).padding(Spacing.sm)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))

                // 筛选行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip("全部", filterCategory == null) { filterCategory = null }
                    FilterChip("理解错误", filterCategory == com.lovebrain.app.model.FeedbackCategory.UNDERSTANDING_ERROR) { filterCategory = com.lovebrain.app.model.FeedbackCategory.UNDERSTANDING_ERROR }
                    FilterChip("表达不喜欢", filterCategory == com.lovebrain.app.model.FeedbackCategory.EXPRESSION_DISLIKE) { filterCategory = com.lovebrain.app.model.FeedbackCategory.EXPRESSION_DISLIKE }
                    FilterChip("其他", filterCategory == com.lovebrain.app.model.FeedbackCategory.OTHER) { filterCategory = com.lovebrain.app.model.FeedbackCategory.OTHER }
                }
                Spacer(Modifier.height(Spacing.sm))

                // 案例列表
                if (filtered.isEmpty()) {
                    Text(
                        "暂无反馈案例。点踩后会自动记录。",
                        style = AppTypography.bodyMedium,
                        color = TextHint,
                        modifier = Modifier.padding(vertical = Spacing.xl)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        filtered.forEach { c ->
                            Card(
                                shape = LoveBrainShape.md,
                                colors = CardDefaults.cardColors(containerColor = SurfaceInset),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(Spacing.md)) {
                                    Text(
                                        "【${c.categories.joinToString(", ") { it.name }}】 ${c.reasons.joinToString(", ")}",
                                        style = AppTypography.labelSmall,
                                        color = PrimaryDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(
                                        "候选：${c.candidateReply.take(60)}${if (c.candidateReply.length > 60) "..." else ""}",
                                        style = AppTypography.labelSmall,
                                        color = TextSecondary
                                    )
                                    if (c.userNote.isNotBlank()) {
                                        Text("补充：${c.userNote}", style = AppTypography.labelSmall, color = TextHint)
                                    }
                                    if (c.betterVersion.isNotBlank()) {
                                        Text("期望：${c.betterVersion}", style = AppTypography.labelSmall, color = Primary)
                                    }
                                    Text(
                                        "${c.timestamp} · ${c.modelId} · ${c.status}",
                                        style = AppTypography.labelSmall,
                                        color = TextHint
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                // 导出按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // 导出 Markdown
                    val (mdInteraction, mdScale) = rememberPressScale(0.96f, "exportMd")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { scaleX = mdScale; scaleY = mdScale }
                            .clip(LoveBrainShape.md)
                            .background(Primary)
                            .clickable(
                                interactionSource = mdInteraction,
                                indication = null,
                                enabled = filtered.isNotEmpty()
                            ) {
                                scope.launch {
                                    exportText = buildMarkdownReport(filtered)
                                    showExport = true
                                }
                            }
                            .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("导出 Markdown", style = AppTypography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    // 导出 JSON
                    val (jsonInteraction, jsonScale) = rememberPressScale(0.96f, "exportJson")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { scaleX = jsonScale; scaleY = jsonScale }
                            .clip(LoveBrainShape.md)
                            .background(SurfaceInset)
                            .border(1.dp, Border, LoveBrainShape.md)
                            .clickable(
                                interactionSource = jsonInteraction,
                                indication = null,
                                enabled = filtered.isNotEmpty()
                            ) {
                                scope.launch {
                                    val json = kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true }
                                    exportText = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.lovebrain.app.model.FeedbackCase.serializer()), filtered)
                                    showExport = true
                                }
                            }
                            .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("导出 JSON", style = AppTypography.labelMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // 导出预览弹层
    if (showExport) {
        Dialog(onDismissRequest = { showExport = false }) {
            Card(
                shape = LoveBrainShape.lg,
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth().padding(Spacing.md)
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("导出预览", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("复制", style = AppTypography.labelMedium, color = Primary, modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("export", exportText))
                            }
                        ).padding(Spacing.sm))
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        exportText,
                        style = AppTypography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "已复制到剪贴板，可粘贴到任何位置。默认已去除身份信息和连接信息。",
                        style = AppTypography.labelSmall,
                        color = TextHint
                    )
                }
            }
        }
    }
}

/** 筛选 chip */
@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val (interaction, scale) = rememberPressScale(0.94f, "filterChip_$label")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .background(if (isSelected) Primary else SurfaceInset, LoveBrainShape.sm)
            .border(1.dp, if (isSelected) Primary else Border, LoveBrainShape.sm)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = AppTypography.labelSmall,
            color = if (isSelected) Color.White else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 构建 Markdown 报告 */
private fun buildMarkdownReport(cases: List<com.lovebrain.app.model.FeedbackCase>): String = buildString {
    appendLine("# LoveBrain 反馈案例报告")
    appendLine()
    appendLine("生成时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
    appendLine()
    appendLine("## 统计")
    appendLine("- 总案例数：${cases.size}")
    appendLine("- 理解错误：${cases.count { com.lovebrain.app.model.FeedbackCategory.UNDERSTANDING_ERROR in it.categories }}")
    appendLine("- 表达不喜欢：${cases.count { com.lovebrain.app.model.FeedbackCategory.EXPRESSION_DISLIKE in it.categories }}")
    appendLine("- 其他：${cases.count { com.lovebrain.app.model.FeedbackCategory.OTHER in it.categories }}")
    appendLine()
    appendLine("## 案例列表")
    appendLine()
    cases.forEachIndexed { idx, c ->
        appendLine("### 案例 ${idx + 1}")
        appendLine("- **时间**：${c.timestamp}")
        appendLine("- **分类**：${c.categories.joinToString(", ")}")
        appendLine("- **原因**：${c.reasons.joinToString(", ")}")
        if (c.kbName.isNotBlank()) appendLine("- **档案**：${c.kbName}")
        if (c.modelId.isNotBlank()) appendLine("- **模型**：${c.modelId}")
        appendLine("- **候选原文**：${c.candidateReply}")
        if (c.userNote.isNotBlank()) appendLine("- **用户补充**：${c.userNote}")
        if (c.betterVersion.isNotBlank()) appendLine("- **期望版本**：${c.betterVersion}")
        appendLine("- **状态**：${c.status}")
        appendLine()
    }
}
