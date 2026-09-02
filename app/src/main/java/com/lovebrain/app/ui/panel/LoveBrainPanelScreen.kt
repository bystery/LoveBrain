package com.lovebrain.app.ui.panel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.ProactiveOption
import kotlinx.coroutines.delay
import com.lovebrain.app.ui.panel.counseling.CounselingPanel
import com.lovebrain.app.ui.panel.reply.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.model.StageSuggestion
/** LoveBrainPanelScreen 横幅自动消失/驻留时长（ 魔法数字命名：值不变） */
/** 知识库横幅自动消失时长 */
private const val KB_NOTICE_AUTO_DISMISS_MS = 3000L
/** 面板警告横幅自动消失时长 */
private const val PANEL_WARNING_AUTO_DISMISS_MS = 3000L
/** 向量更新横幅自动消失时长 */
private const val VECTOR_UPDATE_AUTO_DISMISS_MS = 5000L


/** LoveBrainPanelScreen 内部使用的字符串常量（避免字面量散落） */
private object PanelStrings {
    const val STAGE_SUGGESTION_TITLE = "阶段调整建议"
    const val PROACTIVE_EMPTY_HINT = "输入想说的话（或留空），点《生成》拿 1-3 条可直接发的开场"
}
/** LoveBrainPanelScreen 内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object PanelDimens {
    const val MESSAGE_LIST_DEFAULT_HEIGHT_DP = 160 // 消息列表初始高度
    const val MESSAGE_LIST_MIN_HEIGHT_DP = 80      // 消息列表拖拽下限
    const val MESSAGE_LIST_MAX_HEIGHT_DP = 400     // 消息列表拖拽上限
    const val TRIO_HEIGHT_DP = 40                  // 生成行三件套统一高度（ -⑧）
    const val STYLE_DIVIDER_HEIGHT_DP = 16         // 生成行风格区中缝细分隔线高（主人选型 7A）
    const val PROFILE_CARD_MAX_HEIGHT_DP = 180     // 画像建议卡内容最大高度
    const val BANNER_CLOSE_ICON_SIZE_DP = 14       // 通知横幅关闭图标尺寸
    const val PILL_HEIGHT_DP = 14                  // 五维圆柱高度
    const val PILL_LABEL_GAP_DP = 3                // 圆柱与汉字标签间距
    const val TOUCH_TARGET_MIN_DP = 24             // 触控热区下限（项目自有基线， 口径；）
}

/** 引导文案行高（ 外放：值不变，仅外放命名） */
private val OnboardGuideLineHeight = 20.sp

@Composable
fun LoveBrainPanelScreen(
    viewModel: LoveBrainViewModel,
    onFocusChange: (Boolean) -> Unit,
    onResize: (Int, Int) -> Unit,
    onResizeEnd: () -> Unit = {},
    onMove: (Float, Float) -> Unit,
    onCopy: (String) -> Unit,
    onOpenSettings: () -> Unit,
    // ：头部收起按钮回调（FloatingService 传 hidePanel）
    onCollapse: () -> Unit
) {
    val panelMode by viewModel.panelMode.collectAsStateWithLifecycle()
    val outputMode by viewModel.outputMode.collectAsStateWithLifecycle()
    // 主动发态（主人重构：空态蓝字召唤/关闭）；提前声明供 PanelHeader 齿轮禁用判断
    var inputMode by remember { mutableStateOf(0) } // 0=回复 1=主动发
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val feedbacks by viewModel.feedbacks.collectAsStateWithLifecycle()
    val draftText by viewModel.draftText.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    // ：《想法》chip 态——输入去向 = composeRole（想法态 → Role.IDEA）；捕获链不受影响（VM setCurrentRole 收口）
    val ideaComposeMode by viewModel.ideaComposeMode.collectAsStateWithLifecycle()
    val composeRole = if (ideaComposeMode) ChatMessage.Role.IDEA else currentRole
    val editingIndex by viewModel.editingIndex.collectAsStateWithLifecycle()
    val profileSuggestion by viewModel.profileSuggestion.collectAsStateWithLifecycle()
    val kbNotice by viewModel.kbNotice.collectAsStateWithLifecycle()
    val panelWarning by viewModel.panelWarning.collectAsStateWithLifecycle()
    val vectorUpdate by viewModel.vectorUpdate.collectAsStateWithLifecycle()
    val stageSuggestion by viewModel.stageSuggestion.collectAsStateWithLifecycle()
    val currentVector by viewModel.currentVector.collectAsStateWithLifecycle()
    val vectorDelta by viewModel.vectorDelta.collectAsStateWithLifecycle()
    val showPlanPanel by viewModel.showPlanPanel.collectAsStateWithLifecycle()

    // ：花费/耗时展示状态（VM 聚合；本次花费 null = 未计费 → 占位"—"）
    val todayCostYuan by viewModel.todayCostYuan.collectAsStateWithLifecycle()
    val lastCostYuan by viewModel.lastCostYuan.collectAsStateWithLifecycle()
    val lastResponseMs by viewModel.lastResponseMs.collectAsStateWithLifecycle()
    
    //  ：供应商就绪态订阅 VM（原面板本地 remember 计算已删，三条件含 Key 非空）
    val isProviderReady by viewModel.providerReady.collectAsStateWithLifecycle()

    // + 修复：面板每次进入组合时刷新工单状态（Service 长生命周期下配置后不刷新）
    LaunchedEffect(Unit) {
        viewModel.refreshTicketState()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 矩形阴影修复：去掉面板外层 shadow（延伸到矩形 bounds 外看起来像矩形阴影）
                .clip(LoveBrainShape.xl)
                .background(SurfaceBase)
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border.copy(alpha = 0.5f), LoveBrainShape.xl)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
        ) {
            // 主人纠正（2026-08-30 二次）：展示条叠加在拖拽条上——主人原话（Q6）"总刘海高度不要变"，
            // 上一版误作为独立新行插入致刘海整体增高约 20dp，现回归需求#2 原话"字体覆盖在透明拖拽条上"：
            // 外层盒高钉死 4dp（拖拽条原高），小字居中溢出绘制，刘海总高不变（不裁剪，面板顶部留白容纳）
            Box(modifier = Modifier.fillMaxWidth().height(Spacing.sm)) {
                DragHandle(onMove = onMove)
                // 回归修复（d40ff53 后小字被 4dp 约束截断）：wrapContentHeight(unbounded=true) 让文字
                // 按自身高度测量、居中溢出绘制，外层盒高仍钉死 4dp 刘海不变（截断根因：钉高盒会把 4dp 约束传给子级）
                UsageStatsRow(
                    todayCostYuan = todayCostYuan,
                    lastCostYuan = lastCostYuan,
                    lastResponseMs = lastResponseMs,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            PanelHeader(
                panelMode = panelMode,
                onModeChange = { viewModel.setPanelMode(it) },
                // 需求#25：顶部三段切换（回复/锦囊/谈心），锦囊=回复模式下锦囊面板
                showPlanPanel = showPlanPanel,
                onPlanVisibility = { show ->
                    if (show) viewModel.openPlanPanel() else viewModel.dismissPlanPanel()
                },
                // ：头部收起按钮（直出/思考按钮与胶囊已随/4 迁出）
                onCollapse = onCollapse,

                // 修复 2.2：顶部整行（含切换器左侧空白）可拖动悬浮窗
                // 主人 2026-08-31：进攻开关随齿轮一并移除（逻辑留 VM：outputMode/setOutputMode）
                onHeaderDrag = onMove
            )

            Spacer(Modifier.height(Spacing.xs))

            // ═══ 首次使用引导（轻量化内联卡片，替代原全屏遮罩引导） ═══
            val onboardContext = androidx.compose.ui.platform.LocalContext.current
            val onboardPrefs = remember { onboardContext.getSharedPreferences("lovebrain_onboarding", android.content.Context.MODE_PRIVATE) }
            var showOnboard by remember { mutableStateOf(!onboardPrefs.getBoolean("done", false)) }
            if (showOnboard) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm)
                        .clip(LoveBrainShape.md)
                        .background(PrimaryLight, LoveBrainShape.md)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("使用提示", style = AppTypography.labelLarge, color = PrimaryDark, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        // ：热区扩至 24dp（外包盒，图标视觉尺寸不变）
                        Box(
                            modifier = Modifier
                                .size(PanelDimens.TOUCH_TARGET_MIN_DP.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        onboardPrefs.edit().putBoolean("done", true).apply()
                                        showOnboard = false
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = "关闭使用提示",
                                tint = TextHint,
                                modifier = Modifier.size(Spacing.xl)
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "① 添加对话 → ② 生成回复 → ③ 查看回复方案，长按预览话术\n困惑时可切「谈心」模式，军师用公正视角帮你分析",
                        style = AppTypography.labelMedium,
                        color = TextSecondary,
                        lineHeight = OnboardGuideLineHeight
                    )
                }
            }

            // 需求#24：《回复/今日锦囊》这一行去掉（切换入口并入顶部模式切换器：回复/锦囊/谈心，需求#25）

            // 五维向量：常驻展示五个小圆柱（胶囊标签），去掉下拉框（需求#1）；字体与圆柱整体缩小
            VectorPillsRow(vector = currentVector, delta = vectorDelta)
            Spacer(Modifier.height(Spacing.xs))

            LaunchedEffect(kbNotice) {
                if (kbNotice != null) {
                    delay(KB_NOTICE_AUTO_DISMISS_MS)
                    viewModel.dismissKbNotice()
                }
            }
            LaunchedEffect(panelWarning) {
                if (panelWarning != null) {
                    delay(PANEL_WARNING_AUTO_DISMISS_MS)
                    viewModel.dismissPanelWarning()
                }
            }
            LaunchedEffect(vectorUpdate) {
                if (vectorUpdate != null) {
                    delay(VECTOR_UPDATE_AUTO_DISMISS_MS)
                    viewModel.dismissVectorUpdate()
                }
            }
            // Banner 合并显示：同一时间只显示一个通知，避免多个 Banner 同时出现拥挤
            // 优先级：kbNotice > panelWarning > vectorUpdate（：知识库通知最前，面板警告次之）
            when {
                kbNotice != null -> {
                    KbNoticeBanner(text = "✓ ${kbNotice.orEmpty()}", onDismiss = { viewModel.dismissKbNotice() })
                }
                panelWarning != null -> {
                    KbNoticeBanner(text = "⚠ ${panelWarning.orEmpty()}", onDismiss = { viewModel.dismissPanelWarning() }, container = WarningBg, textColor = Warning)
                }
                vectorUpdate != null -> {
                    KbNoticeBanner(text = "◆ ${vectorUpdate.orEmpty()}", onDismiss = { viewModel.dismissVectorUpdate() })
                }
            }

            if (profileSuggestion != null) {
                ProfileSuggestionCard(
                    suggestion = profileSuggestion.orEmpty(),
                    onConfirm = { viewModel.confirmProfileUpdate() },
                    onDismiss = { viewModel.dismissProfileUpdate() }
                )
                Spacer(Modifier.height(Spacing.md))
            }

            stageSuggestion?.let { suggestion ->
                StageSuggestionCard(
                    suggestion = suggestion,
                    onConfirm = { viewModel.confirmStageChange() },
                    onDismiss = { viewModel.dismissStageChange() }
                )
                Spacer(Modifier.height(Spacing.md))
            }

            if (panelMode == 0) {
                if (showPlanPanel) {
                    // 崩溃修复：weight 放在固定 Box 上，滚动组件拿到确定约束（不出现 Infinity）
                    Box(modifier = Modifier.weight(1f)) {
                        SuggestPanel(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                val inputFocusRequester = remember { FocusRequester() }
                // ═══ 方案 A：说话方向切换上提至常驻控制条，此处只保留状态消费 ═══
                val isProactive by viewModel.isProactive.collectAsStateWithLifecycle()
                val proactiveOptions by viewModel.proactiveOptions.collectAsStateWithLifecycle()
                val proactiveError by viewModel.proactiveError.collectAsStateWithLifecycle()

                // ：主动发态复用同一输入行——chips 隐藏/添加钮隐藏/placeholder 换（-⑥ 默认）；
                // 草稿复用 draftText 通道（切态不清空，生成开场后清空），原独立主动发输入行删除
                ReplyInput(
                    draftText = draftText,
                    currentRole = composeRole,
                    editingIndex = editingIndex,
                    showRoleChips = inputMode == 0,
                    showAddButton = inputMode == 0,
                    placeholderOverride = if (inputMode == 1) "想说什么？留空让军师找话题" else null,
                    onDraftChange = { viewModel.setDraft(it) },
                    onRoleChange = { viewModel.setCurrentRole(it) },
                    onAdd = {
                        val text = draftText.trim()
                        if (text.isNotEmpty()) {
                            if (editingIndex >= 0) {
                                viewModel.updateMessage(editingIndex, composeRole, text)
                                viewModel.setEditingIndex(-1)
                            } else {
                                viewModel.addMessage(composeRole, text)
                            }
                            viewModel.setDraft("")
                        }
                    },
                    onFocusChange = onFocusChange,
                    focusRequester = inputFocusRequester
                )

                // 第 6 轮 BUG 修复：消息列表与《想法》之间的拖拽块实际未插入，加回 8dp 透明 DraggableDivider
                // 拖拽即可实时调整消息列表高度（向下拖=增高、向上拖=减小），区间 80dp-400dp
                val density = androidx.compose.ui.platform.LocalDensity.current
                var messageListHeight by remember { mutableStateOf(PanelDimens.MESSAGE_LIST_DEFAULT_HEIGHT_DP.dp) }
                // ：原"有消息自动退回主动发"补丁删除——主动发开关随生成行常驻，随时可切
                MessageList(
                    messages = messages,
                    editingIndex = editingIndex,
                    onReorder = { from, to -> viewModel.reorderMessages(from, to) },
                    onEdit = { index ->
                        //  防越界：删除/重排竞态下 index 可能失效
                        messages.getOrNull(index)?.let { msg ->
                            viewModel.setEditingIndex(index)
                            viewModel.setDraft(msg.content)
                            viewModel.setCurrentRole(msg.role)
                        }
                    },
                    onDelete = { id ->
                        // ：editingIndex 修正已下沉 VM（持数据真源，同帧连删无旧快照竞态）
                        viewModel.removeMessageById(id)
                    },
                    // 回归修复（d40ff53 后空态拖拽失效）：空态恢复吃拖拽可调高——主人要"拖动控制高度"，
                    // 空态包高会让 messageListHeight 拖动落空；内容已垂直居中（空白对称分布，嫌高直接拖小）
                    modifier = Modifier.height(messageListHeight),
                    // 主人重构（本轮）：空态蓝字 = 主动发入口（点击召唤/再点关闭）
                    onEmptyAction = { inputMode = if (inputMode == 1) 0 else 1 },
                    proactiveActive = inputMode == 1
                )
                DraggableDivider(
                    onDragDelta = { dyPx ->
                        // 像素转 dp：向下拖（dyPx>0）→ 列表增高；向上拖 → 减小
                        val deltaDp = (dyPx / density.density).dp
                        val newHeight = (messageListHeight + deltaDp).coerceIn(PanelDimens.MESSAGE_LIST_MIN_HEIGHT_DP.dp, PanelDimens.MESSAGE_LIST_MAX_HEIGHT_DP.dp)
                        if (newHeight != messageListHeight) messageListHeight = newHeight
                    }
                )

                // ：原"想法"toggle 行与独立 userHint 输入框整块删除——想法成为输入行第三枚 chip（Role.IDEA），
                // 生成时由 VM 从消息列表收集（getUserHint 换源 collectIdeaHint），userHint 状态链路废除

                // 主人重构（本轮）：生成行只剩生成按钮独占——主动发入口挪到空态蓝字（点击召唤/关闭），
                // 进攻收进页头小齿轮弹窗（HeaderDimens 齿轮）；行面干净，无并排开关
                GenerateButton(
                    modifier = Modifier.fillMaxWidth(),
                    proactiveMode = inputMode == 1,
                    isProactive = isProactive,
                    count = messages.size,
                    isGenerating = isGenerating,
                    hasResult = result is GenerateResult.Success,
                    onGenerate = {
                        if (inputMode == 1) {
                            // ：无供应商引导在两条生成路径均保留
                            if (isProviderReady) {
                                viewModel.generateProactive(draftText.trim())
                                viewModel.setDraft("")
                            } else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置")
                        } else {
                            if (isProviderReady) viewModel.generate() else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置")
                        }
                    },
                    onRetry = { if (isProviderReady) viewModel.generate() else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置") },
                    onNextRound = {
                        viewModel.nextRound()
                        onCopy("")
                    },
                    onStop = { if (inputMode == 1) viewModel.stopProactive() else viewModel.stopGeneration() }
                )

                // 收集新增的状态流
                val streamingCoreText by viewModel.streamingCoreText.collectAsStateWithLifecycle()
                val isGeneratingCore by viewModel.isGeneratingCore.collectAsStateWithLifecycle()
                val streamingSchemes by viewModel.streamingSchemes.collectAsStateWithLifecycle()

                // 崩溃修复：weight 放在固定 Box 上，ResultArea 内部 verticalScroll 拿到确定约束
                // 方案 A：主动发模式下结果区切换为开场方案列表
                Box(modifier = Modifier.weight(1f)) {
                    if (inputMode == 1) {
                        ProactiveResultArea(
                            isProactive = isProactive,
                            options = proactiveOptions,
                            error = proactiveError,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ResultArea(
                            result = result,
                            isGenerating = isGenerating,
                            streamingCoreText = streamingCoreText,
                            isGeneratingCore = isGeneratingCore,
                            streamingSchemes = streamingSchemes,
                            feedbacks = feedbacks,
                            onFeedback = { tag, fb -> viewModel.setFeedback(tag, fb) },
                            onCopyScheme = { scheme ->
                                val reply = viewModel.copyScheme(scheme)
                                onCopy(reply)
                            },
                            onRetry = { viewModel.generate() },
                            providerReady = isProviderReady,
                            onOpenSettings = onOpenSettings,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    CounselingPanel(
                        viewModel = viewModel,
                        onFocusChange = onFocusChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        ResizeGrip(
            onResize = onResize,
            onResizeEnd = onResizeEnd,
            modifier = Modifier.align(Alignment.BottomEnd)
        )

    }
}

@Composable
private fun ProfileSuggestionCard(
    suggestion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
        .background(PrimaryLight, LoveBrainShape.lg)
        .padding(Spacing.lg)
    ) {
        Text("AI 画像更新建议", style = AppTypography.labelLarge, color = PrimaryDark)
        Spacer(Modifier.height(Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = PanelDimens.PROFILE_CARD_MAX_HEIGHT_DP.dp)
                .background(SurfaceCard, LoveBrainShape.md)
                .padding(Spacing.md)
        ) {
            Text(
                text = suggestion,
                style = AppTypography.labelMedium,
                color = TextPrimary,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            // ：忽略/确认更新补按压反馈（复用标准件 0.96 scale + 120ms）
            val (dismissInteraction, dismissScale) = rememberPressScale(0.96f, "profileDismissScale")
            Text(
                "忽略",
                style = AppTypography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .graphicsLayer { scaleX = dismissScale; scaleY = dismissScale }
                    .clickable(interactionSource = dismissInteraction, indication = null, onClick = {
                        onDismiss()
                    })
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
            val (confirmInteraction, confirmScale) = rememberPressScale(0.96f, "profileConfirmScale")
            Text(
                "确认更新",
                style = AppTypography.labelLarge,
                color = PrimaryDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .graphicsLayer { scaleX = confirmScale; scaleY = confirmScale }
                    .clickable(interactionSource = confirmInteraction, indication = null, onClick = {
                        onConfirm()
                    })
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
        }
    }
}

@Composable
private fun KbNoticeBanner(
    text: String,
    onDismiss: () -> Unit,
    container: Color = SuccessBg,
    textColor: Color = Success
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(container, LoveBrainShape.sm)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(
            text,
            style = AppTypography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // ：热区扩至 24dp（外包盒，图标视觉仍 14dp； 在册清偿）
        Box(
            modifier = Modifier
                .padding(start = Spacing.sm)
                .size(PanelDimens.TOUCH_TARGET_MIN_DP.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "关闭通知",
                tint = TextHint,
                modifier = Modifier.size(PanelDimens.BANNER_CLOSE_ICON_SIZE_DP.dp)
            )
        }
    }
}

/** 五维小圆柱胶囊行（需求#1：去掉下拉框，常驻直接展示；字体与圆柱整体缩小） */
@Composable
private fun VectorPillsRow(vector: Map<String, Int>, delta: Map<String, Int>) {
    // 暗色已删，固定亮色低饱和深色文字（对比度：8-9sp 小字≥4.5:1）
    // ：五维色收编进 Color.kt 令牌（HSL 值逐位不变）
    val dims = listOf(
        Triple("intimacy", "亲密", VectorIntimacy),
        Triple("trust", "信任", VectorTrust),
        Triple("commitment", "承诺", VectorCommitment),
        Triple("passion", "激情", VectorPassion),
        Triple("security", "安全", VectorSecurity)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), // 恢复：用户要求改回 4dp
        verticalAlignment = Alignment.CenterVertically
    ) {
        dims.forEach { (key, label, color) ->
            VectorPill(
                label = label,
                value = (vector[key] ?: 50).coerceIn(0, 100),
                delta = delta[key] ?: 0,
                color = color,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 单个小圆柱胶囊（需求#34）：汉字标签放圆柱体左边；数字放圆柱体内部（类似电量显示）；圆柱尺寸缩小 */
@Composable
private fun VectorPill(
    label: String,
    value: Int,
    delta: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = nonlinearFraction(value),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "vectorPill_$label"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(label).append(' ').append(value).append("分")
                    if (delta > 0) append("，上升 ").append(delta).append(" 分")
                    else if (delta < 0) append("，下降 ").append(-delta).append(" 分")
                }
            }
    ) {
        // 汉字标签放圆柱体左边（9sp，与圆柱垂直居中）
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextHint,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
            )
        )
        Spacer(Modifier.width(PanelDimens.PILL_LABEL_GAP_DP.dp))
        // 小圆柱（胶囊）：尺寸缩小，彩色填充 + 内部数字（类似电量显示）——恢复 14dp（用户要求改回）
        Box(
            modifier = Modifier
                .weight(1f)
                .height(PanelDimens.PILL_HEIGHT_DP.dp)
                .clip(LoveBrainShape.full)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(animatedFraction)
                    .height(PanelDimens.PILL_HEIGHT_DP.dp)
                    .background(color, LoveBrainShape.full)
            )
            // 数字放圆柱体内部：填充 >40% 时白字（落在彩色底），否则用主题色
            Text(
                text = "$value",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (animatedFraction > 0.4f) Color.White else color,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
        if (delta != 0) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = if (delta > 0) "↑$delta" else "↓${-delta}",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (delta > 0) Success else Error,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

/** 非线性映射：0-40→0~20%, 40-80→20~90%, 80-100→90~100% */
private fun nonlinearFraction(value: Int): Float {
    return when {
        value <= 40 -> (value / 40f) * 0.20f
        value <= 80 -> 0.20f + ((value - 40) / 40f) * 0.70f
        else -> 0.90f + ((value - 80) / 20f) * 0.10f
    }
}

@Composable
private fun StageSuggestionCard(
    suggestion: StageSuggestion,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
        .background(PrimaryLight, LoveBrainShape.lg)
        .padding(Spacing.lg)
    ) {
        Text(PanelStrings.STAGE_SUGGESTION_TITLE, style = AppTypography.labelLarge, color = PrimaryDark)
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "建议调整为「${suggestion.newStage}」\n依据：${suggestion.reason}",
            style = AppTypography.labelMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            // ：忽略/确认调整补按压反馈（复用标准件 0.96 scale + 120ms）
            val (dismissInteraction, dismissScale) = rememberPressScale(0.96f, "stageDismissScale")
            Text(
                "忽略",
                style = AppTypography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .graphicsLayer { scaleX = dismissScale; scaleY = dismissScale }
                    .clickable(interactionSource = dismissInteraction, indication = null, onClick = { onDismiss() })
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
            val (confirmInteraction, confirmScale) = rememberPressScale(0.96f, "stageConfirmScale")
            Text(
                "确认调整",
                style = AppTypography.labelLarge,
                color = PrimaryDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .graphicsLayer { scaleX = confirmScale; scaleY = confirmScale }
                    .clickable(interactionSource = confirmInteraction, indication = null, onClick = { onConfirm() })
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
        }
    }
}

/**
 * 花费/耗时展示条：今日花费 / 本次花费 / 首字耗时。
 * labelSmall 固定字号；本次未计费（null）显示"—"；首字耗时 ≤0 不显示。
 */
@Composable
private fun UsageStatsRow(
    todayCostYuan: Double,
    lastCostYuan: Double?,
    lastResponseMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterHorizontally)
    ) {
        UsageStatCell("今日", "¥${LoveBrainViewModel.formatYuan(todayCostYuan)}")
        UsageStatCell("本次", lastCostYuan?.let { "¥${LoveBrainViewModel.formatYuan(it)}" } ?: "—")
        if (lastResponseMs > 0) {
            UsageStatCell("首字", "%.1fs".format(lastResponseMs / 1000.0))
        }
    }
}

/** 展示条单格：标签 TextHint + 数值 Primary（ 字号钉死 labelSmall） */
@Composable
private fun UsageStatCell(label: String, value: String) {
    Row {
        Text("$label ", style = AppTypography.labelSmall, color = TextHint)
        Text(value, style = AppTypography.labelSmall, color = Primary)
    }
}

/**
 * 主动发起结果区（方案 A）：加载/错误/开场方案列表。
 * 与 ReplyInput 的"主动发"模式配套，替换旧 ProactiveSection 折叠块。
 */
@Composable
private fun ProactiveResultArea(
    isProactive: Boolean,
    options: List<ProactiveOption>,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        when {
            isProactive && options.isEmpty() -> {
                AiLoadingRow(
                    phrases = listOf(
                        "军师正在看你们的近况…",
                        "军师正在找合适的切入点…",
                        "军师正在为你准备开场白…"
                    )
                )
            }
            error != null && options.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LoveBrainShape.md)
                        .background(ErrorBg)
                        .padding(Spacing.lg)
                ) {
                    Text(error, color = Error, style = AppTypography.bodySmall)
                }
            }
            options.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        PanelStrings.PROACTIVE_EMPTY_HINT,
                        color = TextHint,
                        style = AppTypography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                options.forEach { opt ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(LoveBrainShape.md)
                            .background(SurfaceCard, LoveBrainShape.md)
                            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
                            .padding(Spacing.lg)
                    ) {
                        Text(opt.text, style = AppTypography.bodyMedium, color = TextPrimary)
                        if (opt.angle.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text("角度：${opt.angle}", style = AppTypography.labelSmall, color = TextHint)
                        }
                    }
                }
                if (error != null) {
                    Text(error, style = AppTypography.labelSmall, color = Error)
                }
            }
        }
    }
}
