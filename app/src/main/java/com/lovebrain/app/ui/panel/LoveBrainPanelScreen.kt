package com.lovebrain.app.ui.panel

import com.lovebrain.app.core.designsystem.rememberPressScale
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
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.model.StageSuggestion
private const val KB_NOTICE_AUTO_DISMISS_MS = 3000L
private const val PANEL_WARNING_AUTO_DISMISS_MS = 3000L
private const val VECTOR_UPDATE_AUTO_DISMISS_MS = 5000L

private object PanelStrings {
    const val STAGE_SUGGESTION_TITLE = "阶段调整建议"
    const val PROACTIVE_EMPTY_HINT = "输入想说的话，点击下方「生成开场」让军师帮你找话题"
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
    const val TOUCH_TARGET_MIN_DP = 48  // 从 24dp 修正为 48dp 无障碍下限
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
    val composerMode by viewModel.composerMode.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val feedbacks by viewModel.feedbacks.collectAsStateWithLifecycle()
    val draftText by viewModel.draftText.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val ideaComposeMode by viewModel.ideaComposeMode.collectAsStateWithLifecycle()
    val composeRole = if (ideaComposeMode) ChatMessage.Role.IDEA else currentRole
    val editingIndex by viewModel.editingIndex.collectAsStateWithLifecycle()
    val review by viewModel.profileReview.collectAsStateWithLifecycle()
    val activeKb by viewModel.activeKb.collectAsStateWithLifecycle()
    val kbNotice by viewModel.kbNotice.collectAsStateWithLifecycle()
    val panelWarning by viewModel.panelWarning.collectAsStateWithLifecycle()
    val vectorUpdate by viewModel.vectorUpdate.collectAsStateWithLifecycle()
    val stageSuggestion by viewModel.stageSuggestion.collectAsStateWithLifecycle()
    val currentVector by viewModel.currentVector.collectAsStateWithLifecycle()
    val vectorDelta by viewModel.vectorDelta.collectAsStateWithLifecycle()
    val showPlanPanel by viewModel.showPlanPanel.collectAsStateWithLifecycle()

    // 花费与累计统计：九个数字一份快照、一次收集（原先这里 collect 了五把 flow）
    val usage by viewModel.usageStats.collectAsStateWithLifecycle()
    val isProviderReady by viewModel.providerReady.collectAsStateWithLifecycle()

    // proactive state collected at top level
    val isProactive by viewModel.isProactive.collectAsStateWithLifecycle()
    val proactiveOptions by viewModel.proactiveOptions.collectAsStateWithLifecycle()
    val proactiveError by viewModel.proactiveError.collectAsStateWithLifecycle()

    // 单条改写状态
    val rewriteStates by viewModel.rewriteStates.collectAsStateWithLifecycle()

    // 仅看本轮开关
    val onlyThisRound by viewModel.onlyThisRound.collectAsStateWithLifecycle()

    // 输入已变化提示
    val inputChanged by viewModel.inputChanged.collectAsStateWithLifecycle()

    // /: 点踩后展示原因面板——直接消费 VM 的 currentFeedbackCase，不再异步全库读取
    val dislikeCase by viewModel.currentFeedbackCase.collectAsStateWithLifecycle()

    // 记录实际发送——§6.4 :523：开合、保存中、草稿、绑定哪张候选都归持有者，
    // 不再摊四颗 var 在这个 composable 中间。VM 那侧仍异步，跳变来了再告诉它结果。
    val recordSent = rememberRecordSentFlow()
    val actualSentState by viewModel.actualSentState.collectAsStateWithLifecycle()

    // RECORDED 时才关闭浮层，失败时浮层留着、用户输入还在。
    // 这里必须是**穷尽的 when**，不能是 if / else if 链：VM 这一族有五种结果，
    // 原先只写了三种，`NO_KB`（未激活知识库时真会吐）与 `IDLE` 落到"什么都不做"，
    // 于是"保存中"解不开——而浮层的取消与遮罩都是 enabled = !saving，
    // 用户既关不掉也退不出。加一档新结果时让编译器来提醒，而不是靠人记得。
    LaunchedEffect(actualSentState) {
        when (actualSentState) {
            LoveBrainViewModel.ActualSentState.RECORDED -> if (recordSent.saving) {
                recordSent.recorded()
                viewModel.dismissActualSentState()
            }
            LoveBrainViewModel.ActualSentState.KB_NOT_FOUND,
            LoveBrainViewModel.ActualSentState.NO_KB,
            LoveBrainViewModel.ActualSentState.IO_ERROR -> {
                // 失败——放开那把"保存中"的锁，允许重试或取消；草稿留着
                recordSent.saveRejected()
            }
            LoveBrainViewModel.ActualSentState.IDLE -> {
                // 没有进行中的记录：也别让浮层停在一个不会来的跳变上
                recordSent.saveRejected()
            }
        }
    }

    // 记忆纠正中心：开合归持有者（§6.4 :523），记录内容仍由 VM 异步喂进来
    val correctionCenter = rememberCorrectionCenterHolder()
    // §6.4：本轮参考记忆的两颗纠正浮层（暂停时长 / 标记为错误）的状态与渲染
    // 从结果区那一行里搬到这里——遮罩因此盖得住整个面板，而不是只盖住那一行
    val memoryCorrectionFlow = rememberMemoryCorrectionFlow()
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
                    usage = usage,
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
                            "点踩可记录原因并导出，便于后续复盘和调整\n" +
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

            // Profile suggestion only for active KB（卡片两件事来自同一份快照，不会各读各的）
            val profileSuggestion = review.suggestion
            if (profileSuggestion != null && profileSuggestion.kbName == activeKb?.name) {
                val isRegenerating = viewModel.profileRegenerating.collectAsStateWithLifecycle().value
                ProfileSuggestionCard(
                    suggestion = profileSuggestion.display,
                    canConfirm = review.canConfirm,
                    isConfirming = review.isConfirming,
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
                    showRoleChips = composerMode == ComposerMode.REPLY,
                    showAddButton = composerMode == ComposerMode.REPLY,
                    placeholderOverride = if (composerMode == ComposerMode.PROACTIVE) "想说什么？留空让军师找话题" else null,
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
                    onEmptyAction = {
                        // 恢复空态入口——点击蓝字只切换到主动发模式，不发网络请求
                        viewModel.toggleProactiveMode()
                    },
                    proactiveActive = composerMode == ComposerMode.PROACTIVE
                )
                DraggableDivider(
                    onDragDelta = { dyPx ->
                        val deltaDp = (dyPx / density.density).dp
                        val newHeight = (messageListHeight + deltaDp).coerceIn(PanelDimens.MESSAGE_LIST_MIN_HEIGHT_DP.dp, PanelDimens.MESSAGE_LIST_MAX_HEIGHT_DP.dp)
                        if (newHeight != messageListHeight) messageListHeight = newHeight
                    }
                )

                // 替换 DualGenerateRow——使用 ComposerMode 驱动的 ReplyPrimaryActions
                // 4 种按钮状态完全匹配指导书要求：
                // 1. 普通回复、无结果 → 全宽"生成回复 · N 条消息"
                // 2. 普通回复、有结果 → "重试 | 记入知识库"
                // 3. 主动发模式 → 全宽"生成开场"；生成中 → "停止"
                // 4. 回复生成中 → "停止"
                ReplyPrimaryActions(
                    modifier = Modifier.fillMaxWidth(),
                    composerMode = composerMode,
                    isGenerating = isGenerating,
                    isProactive = isProactive,
                    hasReplyResult = result is GenerateResult.Success,
                    messageCount = messages.size,
                    onGenerateReply = {
                        if (isProviderReady) viewModel.generate()
                        else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置")
                    },
                    onGenerateProactive = {
                        if (isProviderReady) {
                            viewModel.generateProactive(draftText.trim())
                        } else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置")
                    },
                    onRetry = { if (isProviderReady) viewModel.generate() else viewModel.showPanelWarning("还没有配置模型供应商，请先去设置") },
                    onSaveToKb = {
                        viewModel.nextRound()
                        onCopy("")
                    },
                    onStop = {
                        if (isProactive) viewModel.stopProactive()
                        else viewModel.stopGeneration()
                    }
                )

                // Collect streaming state
                val streamingCoreText by viewModel.streamingCoreText.collectAsStateWithLifecycle()
                val isGeneratingCore by viewModel.isGeneratingCore.collectAsStateWithLifecycle()
                val streamingSchemes by viewModel.streamingSchemes.collectAsStateWithLifecycle()

                // Result area switches based on resultMode
                Box(modifier = Modifier.weight(1f)) {
                    when (resultMode) {
                        ResultMode.PROACTIVE -> {
                            ProactiveResultArea(
                                isProactive = isProactive,
                                options = proactiveOptions,
                                error = proactiveError,
                                onCopy = onCopy,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ResultMode.REPLY -> {
                            ResultArea(
                                result = result,
                                isGenerating = isGenerating,
                                streamingCoreText = streamingCoreText,
                                isGeneratingCore = isGeneratingCore,
                                streamingSchemes = streamingSchemes,
                                feedbacks = feedbacks,
        onFeedback = { scheme, fb ->
            viewModel.setFeedback(scheme.identity.key, fb)
            // /: VM 同步暴露 currentFeedbackCase，UI 直接消费——不再异步全库读取
        },
                                onCopyScheme = { scheme ->
                                    val reply = viewModel.copyScheme(scheme)
                                    onCopy(reply)
                                },
                                onRetry = { viewModel.generate() },
                                // 本轮参考记忆 + 纠正回调
                                memoryRefs = viewModel.getCurrentMemoryRefs(),
                                correctionFlow = memoryCorrectionFlow,
                                onCorrection = { memoryId, action, replacementText, muteDuration ->
                                    viewModel.applyMemoryCorrection(memoryId, action, replacementText, "", muteDuration)
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
                                // 仅看本轮开关
                                onlyThisRound = onlyThisRound,
                                onToggleOnlyThisRound = { viewModel.toggleOnlyThisRound() },
                                // 输入已变化提示
                                inputChanged = inputChanged,
                                onRegenerateWithNewInput = { viewModel.generate() },
                                // 记录实际发送——: result-level 入口不自动绑定方案卡
                                onRecordSent = { recordSent.open() },
                                // 打开记忆纠正中心
                                onShowCorrectionCenter = {
                                    viewModel.loadAllCorrections { corrections ->
                                        correctionCenterCorrections = corrections
                                        correctionCenter.open()
                                    }
                                },
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

    // /: 点踩原因面板——直接消费 VM currentFeedbackCase。
    // §6.4 :523 最后一块：之前它是这块 Box 里的一坨 Column(fillMaxWidth)（实量
    // 16 颗可点里 14 颗不到 48dp、标题贴顶 y=8dp），现在走宿主 → LbModalSheet。
    // 显隐仍由 VM 说了算，这里不再造第二颗 isOpen。
    if (dislikeCase != null) {
        com.lovebrain.app.ui.panel.reply.DislikeReasonHost(
            case = dislikeCase,
                onUpdateCase = { caseId, cats, reasons, note, better ->
                    viewModel.updateFeedbackCase(caseId, cats, reasons, note, better)
                },
                onDismiss = { viewModel.dismissFeedbackCase() }
            )
        }

        // 记录实际发送——§6.4 :523：状态在 recordSent 手里，这里是唯一渲染处。
        // 确认时**不立刻关浮层**：等 actualSentState 报 RECORDED 才关，
        // 失败则留着草稿放开"保存中"（那段 when 在文件上面）。
        RecordSentFlowHost(
            flow = recordSent,
            onConfirm = { flow ->
                recordSent.beginSaving()
                viewModel.recordActualSentMessage(flow.draft.trim(), flow.schemeKey)
            },
            onDismissed = { viewModel.dismissActualSentState() }
        )

        // §6.4 :523：记忆纠正中心——独立列出已停用／静音／隔离项，支持撤销。
        // 之前是面板顶层 Box 里一块 `Column(fillMaxWidth)` 内联展开区（无遮罩、不居中、
        // 里面每颗可点的实量 28x19dp）；现在走 CorrectionCenterHost → LbModalSheet，
        // 开合归 correctionCenter 持有者。
        CorrectionCenterHost(
            holder = correctionCenter,
            corrections = correctionCenterCorrections,
            onUndoCorrection = { memoryId ->
                viewModel.undoCorrectionFromCenter(memoryId)
                // 撤销后刷新列表
                viewModel.loadAllCorrections { corrections ->
                    correctionCenterCorrections = corrections
                }
            }
        )

        // §6.4：纠正浮层的唯一渲染处，挂在面板这一层（不是结果行里）
        MemoryCorrectionFlowHost(
            flow = memoryCorrectionFlow,
            onMute = { memoryId, duration ->
                viewModel.applyMemoryCorrection(
                    memoryId = memoryId,
                    action = com.lovebrain.app.model.CorrectionAction.MUTED,
                    muteDuration = duration
                )
            },
            onWrong = { memoryId, text ->
                viewModel.applyMemoryCorrection(
                    memoryId = memoryId,
                    action = com.lovebrain.app.model.CorrectionAction.WRONG,
                    replacementText = text,
                    muteDuration = com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
                )
            }
        )

        ResizeGrip(
            onResize = onResize,
            onResizeEnd = onResizeEnd,
            modifier = Modifier.align(Alignment.BottomEnd)
        )

    }
}

/**
 * 画像建议卡——原地重新生成，卡片位置不变。
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
        // 真正的 overlay——正文始终留在 layout 中撑高度，loading 覆盖在上层
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
            // loading 覆盖层——不替换正文，覆盖在上方
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
            // regenerating 时禁用忽略和确认
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
                    // 重新生成中原地显示 loading，不额外显示按钮
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
                    // 无效建议——显示重新生成，点击真正发起新的画像生成请求
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
    usage: com.lovebrain.app.viewmodel.UsageStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterHorizontally)
    ) {
        // 渲染口径一格没改：五个格子、首字那格的 >0 条件、"—" 占位都原样
        UsageStatCell("今日", "¥${LoveBrainViewModel.formatYuan(usage.todayCostYuan)}")
        UsageStatCell("本次", usage.lastCostYuan?.let { "¥${LoveBrainViewModel.formatYuan(it)}" } ?: "—")
        if (usage.lastResponseMs > 0) {
            UsageStatCell("首字", "%.1fs".format(usage.lastResponseMs / 1000.0))
        }
        // 累计统计
        UsageStatCell("累计", "${usage.totalGenerateCount}次")
        UsageStatCell("累计", "¥${LoveBrainViewModel.formatYuan(usage.totalCostYuan)}")
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
 * Proactive result area - now with copy support
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

// ReplyPrimaryActions 已提取为 reply/ReplyPrimaryActions.kt 中的公共可测试组件。
// 不再在 LoveBrainPanelScreen 中维护 private 副本——测试直接使用生产组件，消除双轨。
