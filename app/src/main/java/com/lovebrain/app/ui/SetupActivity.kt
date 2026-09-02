package com.lovebrain.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

    /** 启动悬浮窗服务。返回是否真正启动（未授权时跳授权页并返回 false） */
    private fun startFloatingService(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return false
        }
        // v7 零保活：普通后台服务 startService（无 FGS）
        startService(Intent(this, FloatingService::class.java))
        return true
    }

    /** 首页功能卡片直达：启动悬浮窗 + 通知服务打开面板对应功能 */
    private fun openPanelFromHome(mode: Int, showPlan: Boolean) {
        if (!startFloatingService()) return  // 未授权 → 已跳授权页，授权后回首页再点一次即可
        EventBus.requestPanel(mode, showPlan)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // 暗色模式已删，全站固定亮色，不再检测系统暗色
            LoveBrainTheme {
                SetupScreen(
                    viewModel = viewModel,
                    onStartService = { startFloatingService() },
                    onOpenPanel = { mode, showPlan -> openPanelFromHome(mode, showPlan) }
                )
            }
        }
    }
}

@Composable
private fun SetupScreen(
    viewModel: SetupViewModel,
    onStartService: () -> Unit,
    onOpenPanel: (Int, Boolean) -> Unit
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
                }
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
    onOpenKnowledgeBase: () -> Unit
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
                    Button(
                        onClick = onStartService,
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
                            if (overlayGranted) "启动军师悬浮窗"
                            else "授权悬浮窗（首次）",
                            style = AppTypography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
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
                //  ：去授权入口（仅未授权显示，跳系统无障碍设置页）
                if (!accessibilityGranted) {
                    TextButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
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
                    checked = captureEnabled,
                    onCheckedChange = { viewModel.toggleCapture() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Primary,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = Neutral300.copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.White
                    )
                )
            }
        }

        // ── 模型供应商（多模型批 /：主页直接管理，弹窗编辑，一供应商多模型）──
        ProviderSection(viewModel)
        }
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
                    // 状态点：有激活=蓝实心；未配置=灰
                    Box(
                        modifier = Modifier
                            .size(SetupDimens.STATUS_DOT_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeTicket != null) Primary else Neutral300
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
                            activeTicket?.model?.ifBlank { "未选模型" } ?: "点右侧展开添加",
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
    var keyVisible by remember { mutableStateOf(true) }
    var thinking by remember { mutableStateOf((ticket?.thinkingMode ?: viewModel.globalThinking) == 1) }
    var models by remember { mutableStateOf(ticket?.models.orEmpty()) }
    var currentModel by remember { mutableStateOf(ticket?.model.orEmpty()) }

    var addingModel by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf("") }
    var editIndex by remember { mutableStateOf(-1) }
    var testingModel by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

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

                Text("接口地址", style = AppTypography.labelMedium, color = TextSecondary)
                CompactInput(value = baseUrl, onValueChange = { baseUrl = it }, placeholder = "支持 OpenAI 协议")
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
                                    val ok = viewModel.testConnection(t, m, key.trim())
                                    testingModel = null
                                    testResult = m to ok
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
                testResult?.let { (m, ok) ->
                    Text(
                        if (ok) "✓ $m 连接成功" else "✗ $m 连接失败，请检查配置",
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
                    Button(
                        onClick = {
                            if (ticket == null) {
                                viewModel.addTicket(name, baseUrl, models, key)
                            } else {
                                viewModel.updateTicket(ticket.id, name, baseUrl, models, key)
                            }
                            // ：违规时 formError 非空 → 弹窗不关、就地提示
                            if (viewModel.formError.value == null && name.isNotBlank() && baseUrl.isNotBlank()) {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                        shape = LoveBrainShape.md,
                        enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                        modifier = Modifier.weight(1f).height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
                    ) { Text(if (ticket == null) "保存" else "保存修改", style = AppTypography.labelLarge) }
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

