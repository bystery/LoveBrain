package com.lovebrain.app.ui.panel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalFocusManager
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
import com.lovebrain.app.model.SchemeFeedback
import kotlinx.coroutines.delay
import com.lovebrain.app.ui.panel.counseling.CounselingPanel
import com.lovebrain.app.ui.panel.reply.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.model.StageSuggestion
private const val KB_NOTICE_AUTO_DISMISS_MS = 3000L
private const val PANEL_WARNING_AUTO_DISMISS_MS = 3000L
private const val VECTOR_UPDATE_AUTO_DISMISS_MS = 5000L

private object PanelStrings {
    const val STAGE_SUGGESTION_TITLE = "阶段调整建议"
    const val PROACTIVE_EMPTY_HINT = "输入想说的话后，点击「主动发」润色"
}
private object PanelDimens {
    const val MESSAGE_LIST_DEFAULT_HEIGHT_DP = 80
    const val MESSAGE_LIST_MIN_HEIGHT_DP = 64
    const val MESSAGE_LIST_MAX_HEIGHT_DP = 400
    const val TRIO_HEIGHT_DP = 40
    const val STYLE_DIVIDER_HEIGHT_DP = 16
    const val PROFILE_CARD_MAX_HEIGHT_DP = 180
    const val BANNER_CLOSE_ICON_SIZE_DP = 14
    const val PILL_HEIGHT_DP = 14
    const val PILL_LABEL_GAP_DP = 3
    const val TOUCH_TARGET_MIN_DP = 24
    const val GENERATE_BUTTON_GAP_DP = 8
}

private val OnboardGuideLineHeight = 20.sp

@Composable
fun LoveBrainPanelScreen(
    viewModel: LoveBrainViewModel,
    onInputFocusChange: (String, Boolean) -> Unit,
    onInputIntent: (String) -> Unit,
    onClearComposeFocus: ((() -> Unit) -> Unit),
    onResize: (Int, Int) -> Unit,
    onResizeEnd: () -> Unit = {},
    onMove: (Float, Float) -> Unit,
    onCopy: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCollapse: () -> Unit
) {
    val panelMode by viewModel.panelMode.collectAsStateWithLifecycle()
    val resultMode by viewModel.resultMode.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val feedbacks by viewModel.feedbacks.collectAsStateWithLifecycle()
    val draftText by viewModel.draftText.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val ideaComposeMode by viewModel.ideaComposeMode.collectAsStateWithLifecycle()
    val composeRole = if (ideaComposeMode) ChatMessage.Role.IDEA else currentRole
    val editingIndex by viewModel.editingIndex.collectAsStateWithLifecycle()
    val profileSuggestion by viewModel.profileSuggestion.collectAsStateWithLifecycle()
    val activeKb by viewModel.activeKb.collectAsStateWithLifecycle()
    val kbNotice by viewModel.kbNotice.collectAsStateWithLifecycle()
    val panelWarning by viewModel.panelWarning.collectAsStateWithLifecycle()
    val vectorUpdate by viewModel.vectorUpdate.collectAsStateWithLifecycle()
    val stageSuggestion by viewModel.stageSuggestion.collectAsStateWithLifecycle()
    val currentVector by viewModel.currentVector.collectAsStateWithLifecycle()
    val vectorDelta by viewModel.vectorDelta.collectAsStateWithLifecycle()
    val showPlanPanel by viewModel.showPlanPanel.collectAsStateWithLifecycle()

    val todayCostYuan by viewModel.todayCostYuan.collectAsStateWithLifecycle()
    val lastCostYuan by viewModel.lastCostYuan.collectAsStateWithLifecycle()
    val lastResponseMs by viewModel.lastResponseMs.collectAsStateWithLifecycle()
    // F12: 累计统计
    val totalGenerateCount by viewModel.totalGenerateCount.collectAsStateWithLifecycle()
    val totalCostYuan by viewModel.totalCostYuan.collectAsStateWithLifecycle()
    val isProviderReady by viewModel.providerReady.collectAsStateWithLifecycle()

    // P1-2: proactive state collected at top level
    val isProactive by viewModel.isProactive.collectAsStateWithLifecycle()
    val proactiveOptions by viewModel.proactiveOptions.collectAsStateWithLifecycle()
    val proactiveError by viewModel.proactiveError.collectAsStateWithLifecycle()

    // 单条改写状态
    val rewriteStates by viewModel.rewriteStates.collectAsStateWithLifecycle()

    // F10: 仅看本轮开关
    val onlyThisRound by viewModel.onlyThisRound.collectAsStateWithLifecycle()

    // F11: 输入已变化提示
    val inputChanged by viewModel.inputChanged.collectAsStateWithLifecycle()

    // F02: 点踩后展示原因面板——记录最近一次点踩的 case
    var dislikeCase by remember { mutableStateOf<com.lovebrain.app.model.FeedbackCase?>(null) }

    // F03: 记录实际发送——编辑框
    var showSentDialog by remember { mutableStateOf(false) }
    var sentDialogSchemeKey by remember { mutableStateOf<String?>(null) }
    var sentDialogPrefill by remember { mutableStateOf("") }

    // F04: 记忆纠正中心
    var showCorrectionCenter by remember { mutableStateOf(false) }
    var correctionCenterCorrections by remember { mutableStateOf<Map<String, com.lovebrain.app.model.MemoryCorrection>>(emptyMap()) }

    LaunchedEffect(Unit) {
        viewModel.refreshTicketState()
    }

    val focusManager = LocalFocusManager.current
    DisposableEffect(onClearComposeFocus) {
        onClearComposeFocus { focusManager.clearFocus() }
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // shadow fix
                .clip(LoveBrainShape.xl)
                .background(SurfaceBase)
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border.copy(alpha = 0.5f), LoveBrainShape.xl)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(Spacing.sm)) {
                DragHandle(onMove = onMove)
                UsageStatsRow(
                    todayCostYuan = todayCostYuan,
                    lastCostYuan = lastCostYuan,
                    lastResponseMs = lastResponseMs,
                    totalGenerateCount = totalGenerateCount,
                    totalCostYuan = totalCostYuan,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            PanelHeader(
                panelMode = panelMode,
                onModeChange = { viewModel.setPanelMode(it) },
                // Tab switch
                showPlanPanel = showPlanPanel,
                onPlanVisibility = { show ->
                    if (show) viewModel.openPlanPanel() else viewModel.dismissPlanPanel()
                },
                // collapse button
                onCollapse = onCollapse,

                // drag fix
                // outputMode in VM
                onHeaderDrag = onMove
            )

            Spacer(Modifier.height(Spacing.xs))

            // Onboarding card
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
                        "添加对话 -> 生成回复 -> 查看回复方案\n" +
                            "点击方案卡可调整单条措辞，长按可语音修改\n" +
                            "点踩可记录原因并导出，帮助军师学习\n" +
                            "顶部显示今日/本次/累计花费与生成次数\n" +
                            "困惑时可切「谈心」模式，军师用公正视角帮你分析",
                        style = AppTypography.labelMedium,
                        color = TextSecondary,
                        lineHeight = OnboardGuideLineHeight
                    )
                }
            }

            // Vector pills row
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
            when {
                kbNotice != null -> {
                    KbNoticeBanner(text = kbNotice.orEmpty(), onDismiss = { viewModel.dismissKbNotice() })
                }
                panelWarning != null -> {
                    KbNoticeBanner(text = panelWarning.orEmpty(), onDismiss = { viewModel.dismissPanelWarning() }, container = WarningBg, textColor = Warning)
                }
                vectorUpdate != null -> {
                    KbNoticeBanner(text = vectorUpdate.orEmpty(), onDismiss = { viewModel.dismissVectorUpdate() })
                }
            }

            // Profile suggestion only for active KB
            val profileSuggestionValid = profileSuggestion?.canConfirm == true
            val isProfileConfirming = viewModel.isProfileConfirming.collectAsStateWithLifecycle().value
            if (profileSuggestion != null && profileSuggestion?.kbName == activeKb?.name) {
                val isRegenerating = viewModel.profileRegenerating.collectAsStateWithLifecycle().value
                ProfileSuggestionCard(
                    suggestion = profileSuggestion?.display.orEmpty(),
                    canConfirm = profileSuggestionValid,
                    isConfirming = isProfileConfirming,
                    isRegenerating = isRegenerating,
                    onConfirm = { viewModel.confirmProfileUpdate() },
                    onDismiss = { viewModel.dismissProfileUpdate() },
                    onRegenerate = { viewModel.regenerateProfileUpdate() }
                )
                Spacer(Modifier.height(Spacing.md))
            }

            // Stage suggestion
            stageSuggestion?.let { suggestion ->
                if (suggestion.kbName == activeKb?.name) {
                    StageSuggestionCard(
                        suggestion = suggestion,
                        onConfirm = { viewModel.confirmStageChange() },
                        onDismiss = { viewModel.dismissStageChange() }
                    )
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            if (panelMode == 0) {
                if (showPlanPanel) {
                    // Crash fix: weight on fixed Box for finite constraint
                    Box(modifier = Modifier.weight(1f)) {
                        SuggestPanel(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                val inputFocusRequester = remember { FocusRequester() }
                ReplyInput(
                    draftText = draftText,
                    currentRole = composeRole,
                    editingIndex = editingIndex,
                    showRoleChips = true,
                    showAddButton = true,
                    placeholderOverride = null,
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
                    inputId = "reply",
                    onFocusChange = { focused -> onInputFocusChange("reply", focused) },
                    onInputIntent = { onInputIntent("reply") },
                    focusRequester = inputFocusRequester
                )

                val density = androidx.compose.ui.platform.LocalDensity.current
                var messageListHeight by remember { mutableStateOf(PanelDimens.MESSAGE_LIST_DEFAULT_HEIGHT_DP.dp) }
                MessageList(
                    messages = messages,
                    editingIndex = editingIndex,
                    onReorder = { from, to -> viewModel.reorderMessages(from, to) },
                    onEdit = { index ->
                        messages.getOrNull(index)?.let { msg ->
                            viewModel.setEditingIndex(index)
                            viewModel.setDraft(msg.content)
                            viewModel.setCurrentRole(msg.role)
                        }
                    },
                    onDelete = { id ->
                        viewModel.removeMessageById(id)
                    },
                    modifier = Modifier.height(messageListHeight),
                    onEmptyAction = null,
                    proactiveActive = false
                )
                DraggableDivider(
                    onDragDelta = { dyPx ->
                        val deltaDp = (dyPx / density.density).dp
                        val newHeight = (messageListHeight + deltaDp).coerceIn(PanelDimens.MESSAGE_LIST_MIN_HEIGHT_DP.dp, PanelDimens.MESSAGE_LIST_MAX_HEIGHT_DP.dp)
                        if (newHeight != messageListHeight) messageListHeight = newHeight
                    }
                )

                // P1-5: Dual button row - "生成回复" (left) | "主动发" (right)
                // 非生成状态下始终保留双入口；"记入知识库"移入结果工具区不再挤掉主入口
                DualGenerateRow(
                    modifier = Modifier.fillMaxWidth(),
                    isGenerating = isGenerating,
                    isProactive = isProactive,
                    hasReplyResult = result is GenerateResult.Success,
                    resultMode = resultMode,
                    messageCount = messages.size,
                    draftText = draftText,
                    onGenerateReply = {
                        if (isProviderReady) viewModel.generate()
                        else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置")
                    },
                    onGenerateProactive = {
                        val draft = draftText.trim()
                        if (draft.isEmpty()) {
                            viewModel.showPanelWarning("先输入想说的话，再点主动发")
                            return@DualGenerateRow
                        }
                        if (isProviderReady) {
                            viewModel.generateProactive(draft)
                        } else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置")
                    },
                    onRetry = { if (isProviderReady) viewModel.generate() else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置") },
                    onStop = {
                        if (isProactive) viewModel.stopProactive()
                        else viewModel.stopGeneration()
                    }
                )

                // Collect streaming state
                val streamingCoreText by viewModel.streamingCoreText.collectAsStateWithLifecycle()
                val isGeneratingCore by viewModel.isGeneratingCore.collectAsStateWithLifecycle()
                val streamingSchemes by viewModel.streamingSchemes.collectAsStateWithLifecycle()

                // P1-2: Result area switches based on resultMode
                Box(modifier = Modifier.weight(1f)) {
                    when (resultMode) {
                        LoveBrainViewModel.ResultMode.PROACTIVE -> {
                            ProactiveResultArea(
                                isProactive = isProactive,
                                options = proactiveOptions,
                                error = proactiveError,
                                onCopy = onCopy,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        LoveBrainViewModel.ResultMode.REPLY -> {
                            ResultArea(
                                result = result,
                                isGenerating = isGenerating,
                                streamingCoreText = streamingCoreText,
                                isGeneratingCore = isGeneratingCore,
                                streamingSchemes = streamingSchemes,
                                feedbacks = feedbacks,
                                onFeedback = { scheme, fb ->
                                    viewModel.setFeedback(scheme.identity.key, fb)
                                    // F02: 点踩时展开原因面板
                                    if (fb == SchemeFeedback.DISLIKED) {
                                        viewModel.loadFeedbackCases { cases ->
                                            dislikeCase = cases.lastOrNull { it.schemeIdentityKey == scheme.identity.key }
                                        }
                                    } else {
                                        dislikeCase = null
                                    }
                                },
                                onCopyScheme = { scheme ->
                                    val reply = viewModel.copyScheme(scheme)
                                    onCopy(reply)
                                },
                                onRetry = { viewModel.generate() },
                                // P1-5: 记入知识库放入结果工具区，不挤掉主入口
                                onSaveToKb = {
                                    viewModel.nextRound()
                                    onCopy("")
                                },
                                // F09: 本轮参考记忆 + 纠正回调
                                memoryRefs = viewModel.getCurrentMemoryRefs(),
                                onCorrection = { memoryId, action ->
                                    viewModel.applyMemoryCorrection(memoryId, action)
                                },
                                onUndoCorrection = { memoryId ->
                                    viewModel.undoMemoryCorrection(memoryId)
                                },
                                providerReady = isProviderReady,
                                onOpenSettings = onOpenSettings,
                                // 单条改写
                                rewriteStates = rewriteStates,
                                onRewrite = { identity, command -> viewModel.rewriteScheme(
                                    identity.source,
                                    identity.tag,
                                    command.label
                                ) },
                                onClearRewriteState = { identity -> viewModel.clearRewriteState(identity.key) },
                                onCancelRewrite = { identity -> viewModel.cancelRewrite(identity.key) },
                                onUndoRewrite = { identity -> viewModel.undoRewrite(identity.key) },
                                onCustomRewrite = { identity, customText ->
                                    viewModel.rewriteSchemeCustom(
                                        identity.source,
                                        identity.tag,
                                        customText
                                    )
                                },
                                // F10: 仅看本轮开关
                                onlyThisRound = onlyThisRound,
                                onToggleOnlyThisRound = { viewModel.toggleOnlyThisRound() },
                                // F11: 输入已变化提示
                                inputChanged = inputChanged,
                                onRegenerateWithNewInput = { viewModel.generate() },
                                // F03: 记录实际发送
                                onRecordSent = { scheme ->
                                    sentDialogSchemeKey = scheme.identity.key
                                    sentDialogPrefill = scheme.reply
                                    showSentDialog = true
                                },
                                // F04: 打开记忆纠正中心
                                onShowCorrectionCenter = {
                                    viewModel.loadAllCorrections { corrections ->
                                        correctionCenterCorrections = corrections
                                        showCorrectionCenter = true
                                    }
                                },
                                // F07: 换个思路·方向生成
                                isDirectionGenerating = viewModel.isDirectionGenerating.collectAsStateWithLifecycle().value,
                                streamingDirections = viewModel.streamingDirections.collectAsStateWithLifecycle().value,
                                onGenerateDirection = { viewModel.generateDirection() },
                                onStopDirection = { viewModel.stopDirection() },
                                directionError = viewModel.directionError.collectAsStateWithLifecycle().value,
                                onVoiceRewrite = { identity, transcript ->
                                    viewModel.rewriteScheme(
                                        identity.source,
                                        identity.tag,
                                        transcript
                                    )
                                },
                                onPermissionEvent = { event ->
                                    when (event) {
                                        is PermissionEvent.Granted -> viewModel.showPanelWarning("麦克风权限已开启，请再次长按说话")
                                        is PermissionEvent.Denied -> viewModel.showPanelWarning(
                                            if (event.permanently) "需要麦克风权限才能语音修改，请到设置中开启。仍可点击卡片使用文字调整。"
                                            else "需要麦克风权限才能语音修改，仍可点击卡片使用文字调整。"
                                        )
                                    }
                                },
                                generationRoundId = viewModel.generationRoundId.collectAsStateWithLifecycle().value,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    CounselingPanel(
                        viewModel = viewModel,
                        inputId = "counseling_main",
                        onFocusChange = { focused -> onInputFocusChange("counseling_main", focused) },
                        onInputIntent = { onInputIntent("counseling_main") },
                        onFollowUpFocusChange = { focused -> onInputFocusChange("counseling_followup", focused) },
                        onFollowUpInputIntent = { onInputIntent("counseling_followup") },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // F02: 点踩原因面板——点踩后在结果区下方展开
        if (dislikeCase != null) {
            com.lovebrain.app.ui.panel.reply.DislikeReasonPanel(
                case = dislikeCase,
                onUpdateCase = { caseId, cats, reasons, note, better ->
                    viewModel.updateFeedbackCase(caseId, cats, reasons, note, better)
                },
                onDismiss = { dislikeCase = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }

        // F03: 记录实际发送——编辑确认框
        if (showSentDialog) {
            com.lovebrain.app.ui.panel.reply.RecordSentDialog(
                prefill = sentDialogPrefill,
                onConfirm = { text ->
                    viewModel.recordActualSentMessage(text, sentDialogSchemeKey)
                    showSentDialog = false
                },
                onDismiss = { showSentDialog = false }
            )
        }

        // F04: 记忆纠正中心——独立列出已停用／静音／隔离项，支持撤销
        if (showCorrectionCenter) {
            com.lovebrain.app.ui.panel.reply.CorrectionCenter(
                corrections = correctionCenterCorrections,
                onUndoCorrection = { memoryId ->
                    viewModel.undoCorrectionFromCenter(memoryId)
                    // 撤销后刷新列表
                    viewModel.loadAllCorrections { corrections ->
                        correctionCenterCorrections = corrections
                    }
                },
                onDismiss = { showCorrectionCenter = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }

        ResizeGrip(
            onResize = onResize,
            onResizeEnd = onResizeEnd,
            modifier = Modifier.align(Alignment.BottomEnd)
        )

    }
}

/**
 * P0-10: 画像建议卡——原地重新生成，卡片位置不变。
 *
 * 状态：
 * - Ready: 正常展示建议，可确认/忽略/重新生成
 * - Regenerating: 原地显示"正在重新生成…"，禁用确认
 * - Confirming: 写入中，禁用所有操作
 * - Error: 建议格式无效，显示重新生成
 *
 * 禁止整张卡消失再重新出现导致页面跳动。
 */
@Composable
private fun ProfileSuggestionCard(
    suggestion: String,
    canConfirm: Boolean = true,
    isConfirming: Boolean = false,
    isRegenerating: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
        .background(PrimaryLight, LoveBrainShape.lg)
        .padding(Spacing.lg)
    ) {
        Text("AI 画像更新建议", style = AppTypography.labelLarge, color = PrimaryDark)
        Spacer(Modifier.height(Spacing.md))
        // P1-1: 真正的 overlay——正文始终留在 layout 中撑高度，loading 覆盖在上层
        // 不用 if/else 替换正文，避免高度跳变
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = PanelDimens.PROFILE_CARD_MAX_HEIGHT_DP.dp)
                .background(SurfaceCard, LoveBrainShape.md)
        ) {
            // 正文始终存在于 layout 中——负责撑高
            Text(
                text = suggestion,
                style = AppTypography.labelMedium,
                color = if (isRegenerating) TextHint else TextPrimary,
                modifier = Modifier
                    .padding(Spacing.md)
                    .verticalScroll(rememberScrollState())
            )
            // P1-1: loading 覆盖层——不替换正文，覆盖在上方
            if (isRegenerating) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(SurfaceCard.copy(alpha = 0.85f))
                        .padding(Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "正在重新生成…",
                            style = AppTypography.labelMedium,
                            color = PrimaryDark
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            // P0-10: regenerating 时禁用忽略和确认
            val actionsEnabled = !isConfirming && !isRegenerating
            // press scale feedback
            val (dismissInteraction, dismissScale) = rememberPressScale(0.96f, "profileDismissScale")
            Text(
                "忽略",
                style = AppTypography.labelLarge,
                color = if (actionsEnabled) TextSecondary else TextHint,
                modifier = Modifier
                    .graphicsLayer { scaleX = dismissScale; scaleY = dismissScale }
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        enabled = actionsEnabled,
                        onClick = { onDismiss() }
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
            when {
                isRegenerating -> {
                    // P0-10: 重新生成中原地显示 loading，不额外显示按钮
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "重新生成中…",
                            style = AppTypography.labelLarge,
                            color = TextHint,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
                isConfirming -> {
                    // 提交中：禁用并显示进度
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "写入中…",
                            style = AppTypography.labelLarge,
                            color = PrimaryDark,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
                canConfirm -> {
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
                else -> {
                    // P1-03: 无效建议——显示重新生成，点击真正发起新的画像生成请求
                    val (regenInteraction, regenScale) = rememberPressScale(0.96f, "profileRegenScale")
                    Text(
                        "建议格式无效，重新生成",
                        style = AppTypography.labelLarge,
                        color = Error,
                        modifier = Modifier
                            .graphicsLayer { scaleX = regenScale; scaleY = regenScale }
                            .clickable(interactionSource = regenInteraction, indication = null, onClick = {
                                onRegenerate()
                            })
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    )
                }
            }
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
        // touch target
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

/** Vector pills row */
@Composable
private fun VectorPillsRow(vector: Map<String, Int>, delta: Map<String, Int>) {
    // light theme
    // colors from Color.kt
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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

/** Single vector pill */
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
        // label left
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
        // pill body
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
            // value inside
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
                text = if (delta > 0) "+$delta" else "$delta",
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

/** nonlinear mapping */
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
            // press scale
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
 * Usage stats row
 * 
 */
@Composable
private fun UsageStatsRow(
    todayCostYuan: Double,
    lastCostYuan: Double?,
    lastResponseMs: Long,
    totalGenerateCount: Int,
    totalCostYuan: Double,
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
        // F12: 累计统计
        UsageStatCell("累计", "${totalGenerateCount}次")
        UsageStatCell("累计", "¥${LoveBrainViewModel.formatYuan(totalCostYuan)}")
    }
}

/** Single stat cell */
@Composable
private fun UsageStatCell(label: String, value: String) {
    Row {
        Text("$label ", style = AppTypography.labelSmall, color = TextHint)
        Text(value, style = AppTypography.labelSmall, color = Primary)
    }
}

/**
 * Proactive result area - P1-2: now with copy support
 */
@Composable
private fun ProactiveResultArea(
    isProactive: Boolean,
    options: List<ProactiveOption>,
    error: String?,
    onCopy: (String) -> Unit,
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
                        "军师正在看你们的近况。",
                        "军师正在找合适的切入点。",
                        "军师正在为你准备开场白。"
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
                            .clickable { onCopy(opt.text) }
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

/**
 * P1-5: Dual button row - "生成回复" (left) | "主动发" (right).
 * 非生成状态下始终保留双入口——"记入知识库"已移入结果工具区。
 * 有回复结果时左按钮变为"重试"，右按钮"主动发"保持可达。
 * 按钮状态同时参考 resultMode，避免主动发页面出现旧回复的保存操作。
 */
@Composable
private fun DualGenerateRow(
    modifier: Modifier = Modifier,
    isGenerating: Boolean,
    isProactive: Boolean,
    hasReplyResult: Boolean,
    resultMode: LoveBrainViewModel.ResultMode,
    messageCount: Int,
    draftText: String,
    onGenerateReply: () -> Unit,
    onGenerateProactive: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    if (isGenerating) {
        // Reply generating: stop button full width
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
        val overlayAlpha by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0.22f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(800),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "overlayAlpha"
        )
        var elapsedSec by remember { mutableStateOf(0) }
        androidx.compose.runtime.LaunchedEffect(isGenerating) {
            elapsedSec = 0
            while (true) {
                kotlinx.coroutines.delay(1000)
                elapsedSec++
            }
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs)
                .height(PanelDimens.TRIO_HEIGHT_DP.dp)
                .clip(LoveBrainShape.md)
                .background(Primary, LoveBrainShape.md)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.matchParentSize().graphicsLayer { alpha = overlayAlpha }
                    .background(PrimaryDark, LoveBrainShape.md)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(Spacing.xl),
                    strokeWidth = Spacing.xs
                )
                Spacer(Modifier.width(Spacing.md))
                val phase = when {
                    elapsedSec < 5 -> "分析对话"
                    elapsedSec < 15 -> "生成方案"
                    else -> "深度分析"
                }
                Text(
                    text = "$phase · ${elapsedSec}s  点击停止",
                    color = Color.White,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else if (isProactive) {
        // Proactive running: stop button
        val (stopInteraction, stopScale) = rememberPressScale(0.96f, "proactiveStopScale")
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs)
                .height(PanelDimens.TRIO_HEIGHT_DP.dp)
                .graphicsLayer { scaleX = stopScale; scaleY = stopScale }
                .clip(LoveBrainShape.md)
                .background(Neutral200, LoveBrainShape.md)
                .clickable(interactionSource = stopInteraction, indication = null, onClick = onStop),
            contentAlignment = Alignment.Center
        ) {
            Text("停止", color = Color.White, style = AppTypography.titleMedium, fontWeight = FontWeight.Bold)
        }
    } else {
        // P1-5: 非生成状态始终显示双入口——"记入知识库"已移入结果工具区
        // P0-FIX: hasReplyResult 只在 REPLY 模式下才影响左按钮——避免主动发结果展示时左按钮变成"重试"
        val replyResultVisible = resultMode == LoveBrainViewModel.ResultMode.REPLY && hasReplyResult
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(PanelDimens.GENERATE_BUTTON_GAP_DP.dp)
        ) {
            // Left: 生成回复 / 重试（有回复结果时变为重试）
            val replyEnabled = messageCount > 0 || replyResultVisible
            val (replyInteraction, replyScale) = rememberPressScale(0.96f, "genReplyScale")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(PanelDimens.TRIO_HEIGHT_DP.dp)
                    .then(if (replyEnabled) Modifier.shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md) else Modifier)
                    .clip(LoveBrainShape.md)
                    .background(if (replyEnabled) Primary else SurfaceInset, LoveBrainShape.md)
                    .graphicsLayer { scaleX = replyScale; scaleY = replyScale }
                    .then(if (replyEnabled) Modifier.clickable(
                        interactionSource = replyInteraction,
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            if (replyResultVisible) onRetry() else onGenerateReply()
                        }
                    ) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (replyResultVisible) "重试" else "生成回复",
                    color = if (replyEnabled) Color.White else TextSecondary,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Right: 主动发——始终可达（非生成状态）
            val proactiveEnabled = draftText.trim().isNotEmpty()
            val (proactiveInteraction, proactiveScale) = rememberPressScale(0.96f, "genProactiveScale")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(PanelDimens.TRIO_HEIGHT_DP.dp)
                    .then(if (proactiveEnabled) Modifier.shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md) else Modifier)
                    .clip(LoveBrainShape.md)
                    .background(if (proactiveEnabled) PrimaryDark else SurfaceInset, LoveBrainShape.md)
                    .graphicsLayer { scaleX = proactiveScale; scaleY = proactiveScale }
                    .then(if (proactiveEnabled) Modifier.clickable(
                        interactionSource = proactiveInteraction,
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onGenerateProactive()
                        }
                    ) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "主动发",
                    color = if (proactiveEnabled) Color.White else TextSecondary,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
