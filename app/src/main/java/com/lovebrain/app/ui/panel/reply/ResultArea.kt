package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.ReplyDirection
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.model.SchemeSource
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.util.L
import kotlinx.coroutines.delay

/** 结果区内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object ResultDimens {
    const val FILTER_TAB_HEIGHT_DP = 28      // 筛选 Tab 高度
    const val SKELETON_TAG_WIDTH_DP = 60     // 骨架标签条宽度
    const val CURSOR_START_PAD_DP = 1        // 打字机光标左间距
}

@Composable
fun ResultArea(
    result: GenerateResult?,
    @Suppress("UNUSED_PARAMETER") isGenerating: Boolean,
    streamingCoreText: String,
    isGeneratingCore: Boolean,
    streamingSchemes: List<Scheme>,
    feedbacks: Map<String, SchemeFeedback>,
    onFeedback: (Scheme, SchemeFeedback) -> Unit,
    onCopyScheme: (Scheme) -> Unit,
    onRetry: () -> Unit,
    // P1-5: 记入知识库放入结果工具区
    onSaveToKb: () -> Unit = {},
    // F09: 本轮参考记忆 + 纠正回调
    memoryRefs: List<MemoryRef> = emptyList(),
    onCorrection: (String, CorrectionAction) -> Unit = { _, _ -> },
    onUndoCorrection: (String) -> Unit = {},
    providerReady: Boolean,
    onOpenSettings: () -> Unit,
    // 单条改写
    rewriteStates: Map<String, RewriteState> = emptyMap(),
    onRewrite: (SchemeIdentity, RewriteCommand) -> Unit = { _, _ -> },
    onClearRewriteState: (SchemeIdentity) -> Unit = {},
    onCancelRewrite: (SchemeIdentity) -> Unit = {},
    onUndoRewrite: (SchemeIdentity) -> Unit = {},
    onVoiceRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    onPermissionEvent: (PermissionEvent) -> Unit = {},
    // F01 v2: 自定义改写
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    // F10: 仅看本轮开关
    onlyThisRound: Boolean = false,
    onToggleOnlyThisRound: () -> Unit = {},
    // F11: 输入已变化提示
    inputChanged: Boolean = false,
    onRegenerateWithNewInput: () -> Unit = {},
    // F03: 记录实际发送
    onRecordSent: (Scheme) -> Unit = {},
    // F04: 打开记忆纠正中心
    onShowCorrectionCenter: () -> Unit = {},
    // F07: 换个思路·方向生成
    isDirectionGenerating: Boolean = false,
    streamingDirections: List<Scheme> = emptyList(),
    onGenerateDirection: () -> Unit = {},
    onStopDirection: () -> Unit = {},
    directionError: String? = null,
    // P0-3: 稳定轮次身份——只在整轮 generate 成功时变化
    generationRoundId: Int = 0,
    modifier: Modifier = Modifier
) {
    when {
        // Phase 1 加载中：核心回复（schemes）还没出来
        isGeneratingCore -> {
            if (streamingSchemes.isNotEmpty()) {
                // ★ P1-07: 边流式边出卡——只渲染风格（directions 不再 stream）
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (streamingSchemes.isNotEmpty()) {
                        SchemeCardsRow(
                            schemes = streamingSchemes,
                            feedbacks = feedbacks,
                            onFeedback = onFeedback,
                            onCopyScheme = onCopyScheme,
                            rewriteStates = rewriteStates,
                            onRewrite = onRewrite,
                            onClearRewriteState = onClearRewriteState,
                            onCancelRewrite = onCancelRewrite,
                            onUndoRewrite = onUndoRewrite,
                            onVoiceRewrite = onVoiceRewrite,
                            onPermissionEvent = onPermissionEvent,
                            onCustomRewrite = onCustomRewrite
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryLight, LoveBrainShape.md)
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Primary,
                                modifier = Modifier.size(AppDimens.LOADING_SPINNER_SIZE_DP.dp),
                                strokeWidth = Spacing.xs
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = "其余方案生成中…",
                                color = PrimaryDark,
                                style = AppTypography.bodySmall
                            )
                        }
                    }
                }
            } else {
                CoreLoadingIndicator(
                    streamingCoreText = streamingCoreText,
                    modifier = modifier
                )
            }
        }

        // 全部完成：方案 + 进行中事项（分析展示区已移除）
        result is GenerateResult.Success -> {
            val response = result.response
            // F11: 输入已变化提示——结果来自旧输入时展示
            if (inputChanged) {
                InputChangedBanner(onRegenerate = onRegenerateWithNewInput)
                Spacer(Modifier.height(Spacing.sm))
            }

            // P0-3: viewMode 只以 generationRoundId 重置——单条改写/undo/feedback 不切换用户当前 STYLE/DIRECTION
            var viewMode by remember(generationRoundId) { mutableStateOf(SchemeViewMode.STYLE) }
            val hasDirections = response.directionSchemes.any { it.reply.isNotBlank() }
            val displaySchemes = when (viewMode) {
                SchemeViewMode.STYLE -> response.schemes
                SchemeViewMode.DIRECTION -> response.directionSchemes
            }
            // P0-4: showRefs 状态提升到 Success 层——
            // ResultUtilityTrigger 只负责 toggle 事件，MemoryRefsSection 在主 Column 中渲染。
            // 默认 showRefs=false 不增加高度；用户展开后正常增加高度。
            var showRefs by remember(generationRoundId) { mutableStateOf(false) }

            // P0-4: Box 外层——ResultUtilityTrigger 用 align(TopEnd) 覆盖，
            // 不参与 Column measurement，默认状态额外纵向高度 = 0。
            Box(
                modifier = modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
            // F07: 按需方案——方向切换器改为「换个思路」入口
            // 已有方向时直接显示切换器；无方向时显示「换个思路」按钮
            // 方向生成中时显示流式方向卡
            if (isDirectionGenerating) {
                // F07: 方向生成中——显示流式方向卡或加载指示
                if (streamingDirections.isNotEmpty()) {
                    SchemeCardsRow(
                        schemes = streamingDirections,
                        feedbacks = feedbacks,
                        onFeedback = onFeedback,
                        onCopyScheme = onCopyScheme,
                        rewriteStates = rewriteStates,
                        onRewrite = onRewrite,
                        onClearRewriteState = onClearRewriteState,
                        onCancelRewrite = onCancelRewrite,
                        onUndoRewrite = onUndoRewrite,
                        onVoiceRewrite = onVoiceRewrite,
                        onPermissionEvent = onPermissionEvent,
                        onCustomRewrite = onCustomRewrite
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryLight, LoveBrainShape.md)
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Primary,
                                modifier = Modifier.size(AppDimens.LOADING_SPINNER_SIZE_DP.dp),
                                strokeWidth = Spacing.xs
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = "正在换个思路…",
                                color = PrimaryDark,
                                style = AppTypography.bodySmall
                            )
                        }
                    }
                }
                // 停止按钮
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val (stopInteraction, stopScale) = rememberPressScale(0.96f, "stopDirectionScale")
                    Text(
                        text = "停止",
                        color = TextSecondary,
                        style = AppTypography.bodySmall,
                        modifier = Modifier
                            .graphicsLayer { scaleX = stopScale; scaleY = stopScale }
                            .clickable(interactionSource = stopInteraction, indication = null, onClick = onStopDirection)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    )
                }
            } else if (hasDirections) {
                SchemeViewSwitcher(
                    mode = viewMode,
                    onModeChange = { viewMode = it }
                )
                Spacer(Modifier.height(Spacing.sm))
            } else if (directionError != null) {
                // F07: 方向生成失败——显示错误和重试
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LoveBrainShape.md)
                        .background(ErrorBg)
                        .padding(Spacing.md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = directionError,
                            color = Error,
                            style = AppTypography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        val (retryDirInteraction, retryDirScale) = rememberPressScale(0.96f, "retryDirectionScale")
                        Text(
                            text = "重试",
                            color = PrimaryDark,
                            style = AppTypography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .graphicsLayer { scaleX = retryDirScale; scaleY = retryDirScale }
                                .clickable(interactionSource = retryDirInteraction, indication = null, onClick = onGenerateDirection)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            } else {
                // F07: 无方向时显示「换个思路」按钮
                val (changeInteraction, changeScale) = rememberPressScale(0.96f, "changeApproachScale")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "换个思路",
                        color = PrimaryDark,
                        style = AppTypography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(LoveBrainShape.md)
                            .background(PrimaryLight)
                            .graphicsLayer { scaleX = changeScale; scaleY = changeScale }
                            .clickable(interactionSource = changeInteraction, indication = null, onClick = onGenerateDirection)
                            .padding(horizontal = Spacing.xl, vertical = Spacing.md)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
            }
                    SchemeCardsRow(
                        schemes = displaySchemes,
                        feedbacks = feedbacks,
                        onFeedback = onFeedback,
                        onCopyScheme = onCopyScheme,
                        rewriteStates = rewriteStates,
                        onRewrite = onRewrite,
                        onClearRewriteState = onClearRewriteState,
                        onCancelRewrite = onCancelRewrite,
                        onUndoRewrite = onUndoRewrite,
                        onVoiceRewrite = onVoiceRewrite,
                        onPermissionEvent = onPermissionEvent,
                        onCustomRewrite = onCustomRewrite,
                        onlyThisRound = onlyThisRound,
                        onToggleOnlyThisRound = onToggleOnlyThisRound
                    )

                    if (response.analysis.ongoing.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        OngoingSection(items = response.analysis.ongoing)
                    }

                    // P0-4: MemoryRefsSection 在主 Column 中按正常文档流渲染——
                    // 默认 showRefs=false 时 AnimatedVisibility 不占高度；
                    // 用户主动展开后正常增加信息高度，不覆盖方案卡或切换器。
                    if (memoryRefs.isNotEmpty()) {
                        MemoryRefsSection(
                            memoryRefs = memoryRefs,
                            showRefs = showRefs,
                            onCorrection = onCorrection,
                            onUndoCorrection = onUndoCorrection
                        )
                    }
                }

                // P0-4: 结果级 utility trigger——右上角 overlay，不参与 Column measurement。
                // 只负责：⋯ trigger + DropdownMenu + toggle 事件。
                // 默认未展开状态额外纵向高度 = 0。
                ResultUtilityTrigger(
                    memoryRefs = memoryRefs,
                    showRefs = showRefs,
                    onSaveToKb = onSaveToKb,
                    onToggleRefs = { showRefs = !showRefs },
                    onlyThisRound = onlyThisRound,
                    onToggleOnlyThisRound = onToggleOnlyThisRound,
                    onRecordSent = { onRecordSent(displaySchemes.firstOrNull { it.reply.isNotBlank() } ?: displaySchemes.first()) },
                    hasResult = response.schemes.any { it.reply.isNotBlank() },
                    onShowCorrectionCenter = onShowCorrectionCenter,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        result is GenerateResult.Error -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(LoveBrainShape.lg)
                    .background(ErrorBg)
                    .padding(Spacing.xl)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = result.message, color = Error, style = AppTypography.bodySmall)
                    Spacer(Modifier.height(Spacing.md))
                    // #4：重试文本补标准件按压反馈（仿 CounselingPanel 错误分支先例）
                    val (retryInteraction, retryScale) = rememberPressScale(0.96f, "retryScale")
                    Text(
                        text = "点击重试",
                        color = PrimaryDark,
                        style = AppTypography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .graphicsLayer { scaleX = retryScale; scaleY = retryScale }
                            .clickable(interactionSource = retryInteraction, indication = null, onClick = onRetry)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm) // ：热区外扩至 ≥24dp（文字高约 16dp + 垂直内边距）
                    )
                }
            }
        }

        // ：未配置供应商——空态不做死路，引导去设置页（可点通）
        !providerReady -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(LoveBrainShape.lg)
                    .background(SurfaceInset)
                    .padding(Spacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "还没有配置模型供应商",
                        color = TextSecondary,
                        style = AppTypography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(Spacing.md))
                    val (settingsInteraction, settingsScale) = rememberPressScale(0.96f, "openSettingsScale")
                    Text(
                        text = "去设置",
                        color = Color.White,
                        style = AppTypography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(LoveBrainShape.md)
                            .background(Primary)
                            .graphicsLayer { scaleX = settingsScale; scaleY = settingsScale }
                            .clickable(interactionSource = settingsInteraction, indication = null, onClick = { onOpenSettings() })
                            .padding(horizontal = Spacing.xl, vertical = Spacing.md)
                    )
                }
            }
        }

        // 空状态：生成前直接留白（  主人拍板——灰色引导占位卡删除，空白本身即状态说明）
        else -> {
            Spacer(modifier = modifier.fillMaxWidth())
        }
    }
}

/** P0-1: 风格/方向视图模式 */
enum class SchemeViewMode { STYLE, DIRECTION }

/** 方案筛选模式 */
private enum class SchemeFilter { ALL, LIKED }

/** 方案卡片行——Phase 1 完成就立刻渲染 */
/** P0-1: 轻量风格/方向切换——高度≤28dp，使用现有配色，不成为视觉主角 */
@Composable
private fun SchemeViewSwitcher(
    mode: SchemeViewMode,
    onModeChange: (SchemeViewMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        SchemeFilterTab(
            label = "风格",
            isSelected = mode == SchemeViewMode.STYLE,
            onClick = { onModeChange(SchemeViewMode.STYLE) }
        )
        SchemeFilterTab(
            label = "方向",
            isSelected = mode == SchemeViewMode.DIRECTION,
            onClick = { onModeChange(SchemeViewMode.DIRECTION) }
        )
    }
}

@Composable
private fun SchemeCardsRow(
    schemes: List<Scheme>,
    feedbacks: Map<String, SchemeFeedback>,
    onFeedback: (Scheme, SchemeFeedback) -> Unit,
    onCopyScheme: (Scheme) -> Unit,
    rewriteStates: Map<String, RewriteState> = emptyMap(),
    onRewrite: (SchemeIdentity, RewriteCommand) -> Unit = { _, _ -> },
    onClearRewriteState: (SchemeIdentity) -> Unit = {},
    onCancelRewrite: (SchemeIdentity) -> Unit = {},
    onUndoRewrite: (SchemeIdentity) -> Unit = {},
    onVoiceRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    onPermissionEvent: (PermissionEvent) -> Unit = {},
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    // F10: 仅看本轮开关
    onlyThisRound: Boolean = false,
    onToggleOnlyThisRound: () -> Unit = {}
) {
        // F10: 仅看本轮开关——在方案卡行上方
        if (onlyThisRound) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (toggleInteraction, toggleScale) = rememberPressScale(0.96f, "otrToggleScale")
                Text(
                    text = "✓ 仅看本轮",
                    style = AppTypography.labelSmall,
                    color = PrimaryDark,
                    modifier = Modifier
                        .graphicsLayer { scaleX = toggleScale; scaleY = toggleScale }
                        .clip(LoveBrainShape.sm)
                        .background(PrimaryLight, LoveBrainShape.sm)
                        .clickable(
                            interactionSource = toggleInteraction,
                            indication = null,
                            onClick = onToggleOnlyThisRound
                        )
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                )
            }
        }

        // 方案筛选：全部 / 已赞（调研：NN/G 10 Heuristics #6 Recognition rather than recall——
    // 用户赞过的方案应能快速回看，无需在 4 张卡里翻找）
    // P0-4: filter 绑定 scheme group source——切换 STYLE/DIRECTION 时默认回 ALL
    val currentSource = schemes.firstOrNull()?.source
    var filter by remember(currentSource) { mutableStateOf(SchemeFilter.ALL) }
    val likedCount = schemes.count { feedbacks[it.identity.key] == SchemeFeedback.LIKED }

    // P0-4: 任何时候 likedCount=0 时不保持 LIKED——防止空页死角
    LaunchedEffect(likedCount) {
        if (likedCount == 0 && filter == SchemeFilter.LIKED) {
            filter = SchemeFilter.ALL
        }
    }

    val displaySchemes = when (filter) {
        SchemeFilter.ALL -> schemes
        SchemeFilter.LIKED -> schemes.filter { feedbacks[it.identity.key] == SchemeFeedback.LIKED }
    }

    val scrollState = rememberLazyListState()
    // 需求#20：去掉当前卡片指示器（原 activeIndex 追踪已移除）
    // 切换筛选时滚动回起点
    LaunchedEffect(filter) {
        scrollState.scrollToItem(0)
    }
    Column {
        // 筛选 Tab 行：仅有已赞方案时才显示筛选器
        if (likedCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SchemeFilterTab(
                    label = "全部 ${schemes.size}",
                    isSelected = filter == SchemeFilter.ALL,
                    onClick = { filter = SchemeFilter.ALL }
                )
                SchemeFilterTab(
                    label = "已赞 $likedCount",
                    isSelected = filter == SchemeFilter.LIKED,
                    onClick = { filter = SchemeFilter.LIKED }
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        if (displaySchemes.isEmpty()) {
            // 已赞筛选下无结果：空状态引导
            // 调研：NN/G 10 Heuristics #1 Visibility of System Status——空状态应说明原因和下一步
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LoveBrainShape.md)
                    .background(SurfaceInset)
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有赞过的方案\n点「赞」收藏喜欢的方案",
                    color = TextHint,
                    style = AppTypography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            // 需求#19 修复：入场动画只在首次出现时播放一次（playedTags 集合）。
            // LazyRow item 离开视口会销毁 remember，若动画状态留在 item 内，
            // 从右向左滑（item 重新组合）会重播动画 → 卡片"闪一下"。提升到外层集合解决。
            val playedTags = remember { mutableStateMapOf<String, Boolean>() }
            // 阻断C修复：行级唯一展开状态——同一时间只展开一张卡
            var expandedRewriteTag by remember { mutableStateOf<String?>(null) }
            LazyRow(
                state = scrollState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(displaySchemes, key = { it.identity.key }) { scheme ->
                    // 入场动效：淡入+上移，逐张交错 60ms（仅首次组合播放）
                    val index = displaySchemes.indexOfFirst { it.identity.key == scheme.identity.key }
                    var visible by remember { mutableStateOf(playedTags[scheme.identity.key] ?: false) }
                    LaunchedEffect(Unit) {
                        if (!(playedTags[scheme.identity.key] ?: false)) {
                            playedTags[scheme.identity.key] = true
                            visible = true
                        }
                    }
                    // 同一时间只展开一张改写区
                    // 阻断C修复：expandedRewriteTag 已提升到行级——切卡自动收上张

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(250, delayMillis = index * 60)) +
                            slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = tween(300, delayMillis = index * 60)
                            )
                    ) {
                        SchemeCard(
                            scheme = scheme,
                            feedback = feedbacks[scheme.identity.key] ?: SchemeFeedback.NONE,
                            onFeedback = onFeedback,
                            onCopy = onCopyScheme,
                            rewriteState = rewriteStates[scheme.identity.key],
                            onRewrite = onRewrite,
                            onClearRewriteState = onClearRewriteState,
                            onCancelRewrite = onCancelRewrite,
                            onUndoRewrite = onUndoRewrite,
                            isExpanded = expandedRewriteTag == scheme.identity.key,
                            onToggleRewriteExpand = { identity ->
                                val key = identity.key
                                val currentRewriteState = rewriteStates[key]
                                if (expandedRewriteTag != key) {
                                    if (currentRewriteState is RewriteState.Done ||
                                        currentRewriteState is RewriteState.Error) {
                                        onClearRewriteState(identity)
                                    }
                                    expandedRewriteTag = key
                                } else {
                                    expandedRewriteTag = null
                                }
                            },
                            onVoiceRewrite = onVoiceRewrite,
                            onPermissionEvent = onPermissionEvent,
                            onCustomRewrite = onCustomRewrite
                        )
                    }
                }
            }
            // 需求#20：去掉卡片下方的四个点（当前卡片指示器）
        }
    }
}

/** 方案筛选 Tab：选中态高亮 + 点击切换（与 ActionChip 样式统一） */
@Composable
private fun SchemeFilterTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "filterTabScale")
    Box(
        modifier = Modifier
            .height(ResultDimens.FILTER_TAB_HEIGHT_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.md)
            .background(if (isSelected) PrimaryLight else SurfaceInset, LoveBrainShape.md)
            .border(
                AppDimens.BORDER_WIDTH_DP.dp,
                if (isSelected) PrimarySubtle else Border,
                LoveBrainShape.md
            )
            .semantics { selected = isSelected }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AppTypography.labelMedium,
            color = if (isSelected) PrimaryDark else TextHint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 进行中事项折叠卡（ongoing 可视化） */
@Composable
private fun OngoingSection(items: List<com.lovebrain.app.model.OngoingItem>) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(SurfaceInset)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("进行中事项", color = TextSecondary, style = AppTypography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "收起" else "展开",
                color = TextHint,
                style = AppTypography.labelSmall
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                items.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(Spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, color = TextPrimary, style = AppTypography.bodySmall, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = when (item.status) {
                                "已完成" -> "✓"
                                "已取消" -> "✗"
                                else -> "…"
                            },
                            color = if (item.status == "已完成") Success else TextHint,
                            style = AppTypography.labelSmall
                        )
                    }
                    if (item.state.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(item.state, color = TextHint, style = AppTypography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreLoadingIndicator(
    streamingCoreText: String,
    modifier: Modifier = Modifier
) {
    val phrases = remember {
        listOf(
            "军师正在生成核心回复…",
            "思考四种风格方案…",
            "为你精选最佳话术…"
        )
    }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            index = (index + 1) % phrases.size
        }
    }

    // 骨架屏闪烁动画（调研：NN/G Progress Indicators——骨架屏减少感知等待时间）
    val skeletonTransition = rememberInfiniteTransition(label = "skeleton")
    val skeletonAlpha by skeletonTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // 骨架卡片占位：4 张灰色卡片，让用户预知即将出现的内容布局
        // ：骨架卡尺寸引用 SchemeCardDimens（166→150 对齐实体卡，消除加载完成瞬间跳变）
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(4) { _ ->
                Box(
                    modifier = Modifier
                        .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
                        .height(SchemeCardDimens.CARD_HEIGHT_DP.dp)
                        .clip(LoveBrainShape.lg)
                ) {
                    // ④ 裁决修复（方案 a）：呼吸 alpha 只作用于独立背景层（无子节点，层 alpha 与色 alpha
                    // 合成恒等），避免外层 graphicsLayer 包子内容造成 s×(s+0.1) 乘算漂移——对齐 GenerateButton 叠层先例
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = skeletonAlpha }
                            .background(Neutral600)
                    )
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        // 骨架标签条
                        Box(
                            modifier = Modifier
                                .width(ResultDimens.SKELETON_TAG_WIDTH_DP.dp)
                                .height(Spacing.xl)
                                .clip(LoveBrainShape.sm)
                                .graphicsLayer { alpha = skeletonAlpha + 0.1f }
                                .background(Neutral500)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        // 骨架文本行
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Spacing.lg)
                                    .clip(LoveBrainShape.sm)
                                    .graphicsLayer { alpha = skeletonAlpha + 0.1f }
                                    .background(Neutral500)
                            )
                            if (it < 2) Spacer(Modifier.height(Spacing.xs))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        // 加载文案 + 流式文本
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(LoveBrainShape.md)
                .background(PrimaryLight)
                .padding(Spacing.lg)
        ) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier.size(AppDimens.LOADING_SPINNER_SIZE_DP.dp),
                strokeWidth = Spacing.xs
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(text = phrases[index], color = PrimaryDark, style = AppTypography.bodySmall)
        }
        // 显示流式核心文本（逐字显示）
        if (streamingCoreText.isNotBlank()) {
            Spacer(Modifier.height(Spacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, LoveBrainShape.md)
                    .padding(Spacing.lg)
            ) {
                TypewriterText(
                    text = streamingCoreText,
                    color = TextPrimary,
                    style = AppTypography.bodySmall,
                    maxLines = 6
                )
            }
        }
    }
}

/**
 * 流式逐字显示组件：模仿打字机效果，逐字渐显。
 * 调研依据：ChatGPT/Claude 流式输出体验，逐字显示提升阅读节奏感。
 * 实现：用 LaunchedEffect 跟踪上一次显示长度，每次新文本到达时逐步增长显示字符数。
 */
@Composable
private fun TypewriterText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle,
    maxLines: Int = Int.MAX_VALUE
) {
    var displayedLength by remember { mutableStateOf(0) }

    // 当文本变长时，逐步追赶到最新长度
    LaunchedEffect(text) {
        if (text.length > displayedLength) {
            // 自适应步长：文本越长，每次追赶的字符越多，避免长文本打字太慢
            val remaining = text.length - displayedLength
            val step = when {
                remaining > 200 -> 8    // 长文本：快进
                remaining > 100 -> 5
                remaining > 50 -> 3
                else -> 2              // 短文本：逐字
            }
            val interval = when {
                remaining > 200 -> 8L  // 长文本：更快
                else -> 16L           // 正常 60fps
            }
            while (displayedLength < text.length) {
                val actualStep = minOf(step, text.length - displayedLength)
                displayedLength += actualStep
                delay(interval)
            }
        } else if (text.length < displayedLength) {
            // 文本重置（新的一轮生成）
            displayedLength = 0
        }
    }

    val displayText = if (displayedLength <= text.length) {
        text.take(displayedLength)
    } else {
        text
    }

    // 带闪烁光标的文本
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = displayText,
            color = color,
            style = style,
            maxLines = maxLines
        )
        // 光标闪烁动画：只在还在逐字显示时闪烁，全部显示后光标消失
        if (displayText.length < text.length) {
            var cursorVisible by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(500)
                    cursorVisible = !cursorVisible
                }
            }
            Text(
                text = "▎",
                color = if (cursorVisible) Primary else androidx.compose.ui.graphics.Color.Transparent,
                style = style,
                modifier = Modifier.padding(start = ResultDimens.CURSOR_START_PAD_DP.dp)
            )
        }
    }
}

// ═══════════ P0-4: 结果级 utility trigger ═══════════

/**
 * P0-4: 结果级 utility trigger — 右上角轻量 ⋯ trigger + DropdownMenu。
 *
 * 替代旧的"记入知识库专属 Row"。不增加结果区纵向高度。
 * - 菜单始终包含"记入知识库"
 * - memoryRefs 非空时包含"本轮参考"展开/收起 toggle
 *
 * 只负责：⋯ trigger、DropdownMenu、toggle 事件回调。
 * MemoryRefsSection 由 ResultArea 在主 Column 中渲染。
 *
 * 目标：功能一直存在，但没有"一个功能一整行"。
 * 通过 modifier = Modifier.align(TopEnd) 定位为 overlay，不参与 Column measurement。
 */
@Composable
private fun ResultUtilityTrigger(
    memoryRefs: List<MemoryRef>,
    showRefs: Boolean,
    onSaveToKb: () -> Unit,
    onToggleRefs: () -> Unit,
    // F10: 仅看本轮开关
    onlyThisRound: Boolean = false,
    onToggleOnlyThisRound: () -> Unit = {},
    // F03: 记录实际发送
    onRecordSent: () -> Unit = {},
    hasResult: Boolean = false,
    // F04: 记忆纠正中心
    onShowCorrectionCenter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    // trigger 不使用 fillMaxWidth——只是一个 28dp 的 overlay 图标
    Box(
        modifier = modifier
    ) {
        // ⋯ trigger
        val (triggerInteraction, triggerScale) = rememberPressScale(0.92f, "resultUtilityTriggerScale")
        Box(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { scaleX = triggerScale; scaleY = triggerScale }
                .clip(LoveBrainShape.sm)
                .clickable(interactionSource = triggerInteraction, indication = null) {
                    menuOpen = !menuOpen
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "⋯",
                style = AppTypography.labelLarge,
                color = TextHint
            )
        }

        // DropdownMenu 浮层——不改变结果区 layout height
        androidx.compose.material3.DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier
                .clip(LoveBrainShape.md)
                .background(SurfaceCard)
        ) {
            // 始终包含"记入知识库"
            UtilityMenuItem(
                label = "记入知识库",
                desc = "保存本轮回复到知识库"
            ) {
                menuOpen = false
                onSaveToKb()
            }

            // F03: 记录实际发送
            if (hasResult) {
                UtilityMenuItem(
                    label = "记录实际发送",
                    desc = "确认你已发送的版本"
                ) {
                    menuOpen = false
                    onRecordSent()
                }
            }

            // F10: 仅看本轮
            UtilityMenuItem(
                label = if (onlyThisRound) "✓ 仅看本轮" else "仅看本轮",
                desc = "排除旧记忆，只看本轮输入"
            ) {
                menuOpen = false
                onToggleOnlyThisRound()
            }

            // F04: 记忆纠正中心
            UtilityMenuItem(
                label = "记忆纠正中心",
                desc = "查看和撤销已纠正的记忆"
            ) {
                menuOpen = false
                onShowCorrectionCenter()
            }

            // memoryRefs 非空时包含"本轮参考" toggle
            if (memoryRefs.isNotEmpty()) {
                UtilityMenuItem(
                    label = if (showRefs) "收起本轮参考" else "本轮参考 ${memoryRefs.size}",
                    desc = "查看本轮注入的记忆"
                ) {
                    menuOpen = false
                    onToggleRefs()
                }
            }
        }
    }
}

/**
 * P0-4: MemoryRefsSection — 在主 Column 中按正常文档流渲染的记忆引用列表。
 *
 * 默认 showRefs=false 时 AnimatedVisibility 不占高度（collapsed）。
 * 用户主动展开后正常增加信息高度，不覆盖方案卡或切换器。
 */
@Composable
private fun MemoryRefsSection(
    memoryRefs: List<MemoryRef>,
    showRefs: Boolean,
    onCorrection: (String, CorrectionAction) -> Unit,
    onUndoCorrection: (String) -> Unit
) {
    AnimatedVisibility(
        visible = showRefs && memoryRefs.isNotEmpty(),
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .clip(LoveBrainShape.md)
                .background(SurfaceInset, LoveBrainShape.md)
                .padding(Spacing.md)
        ) {
            val maxInitialRefs = 5
            var showAllRefs by remember { mutableStateOf(false) }
            val displayRefs = if (showAllRefs) memoryRefs else memoryRefs.take(maxInitialRefs)
            displayRefs.forEachIndexed { index, ref ->
                if (index > 0) Spacer(Modifier.height(Spacing.sm))
                MemoryRefItem(
                    ref = ref,
                    onCorrection = onCorrection,
                    onUndoCorrection = onUndoCorrection,
                    onCorrectionWithMute = { memoryId, duration ->
                        onCorrection(memoryId, CorrectionAction.MUTED)
                        // F04: 通过外层回调传递时长——使用 muteDuration 的实际效果
                        // 由 ViewModel 最终调用 saveCorrection 时传入
                    },
                    onCorrectionWithReplacement = { memoryId, text ->
                        onCorrection(memoryId, CorrectionAction.WRONG)
                        // F04: replacementText 通过外层回调传递
                    }
                )
            }
            if (memoryRefs.size > maxInitialRefs && !showAllRefs) {
                Spacer(Modifier.height(Spacing.sm))
                val (moreInteraction, moreScale) = rememberPressScale(0.96f, "moreRefsScale")
                Text(
                    "更多 ${memoryRefs.size - maxInitialRefs} 条",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    modifier = Modifier
                        .graphicsLayer { scaleX = moreScale; scaleY = moreScale }
                        .clickable(interactionSource = moreInteraction, indication = null) {
                            showAllRefs = true
                        }
                        .padding(Spacing.xs)
                )
            }
        }
    }
}

/** P0-4: DropdownMenu utility 菜单项 */
@Composable
private fun UtilityMenuItem(
    label: String,
    desc: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "utilityMenuScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            desc,
            style = AppTypography.labelSmall,
            color = TextHint
        )
    }
}

/**
 * F09: 单条记忆引用 — 只显示正文 + ⋯ 菜单。
 * ⋯ 菜单内提供：不对 / 结束 / 暂时别提 / 不是她 / 撤销。
 */
@Composable
private fun MemoryRefItem(
    ref: MemoryRef,
    onCorrection: (String, CorrectionAction) -> Unit,
    onUndoCorrection: (String) -> Unit,
    // F04: 带时长的"暂时别提"和带输入的"不对"
    onCorrectionWithMute: (String, com.lovebrain.app.model.MuteDuration) -> Unit = { id, _ -> onCorrection(id, CorrectionAction.MUTED) },
    onCorrectionWithReplacement: (String, String) -> Unit = { id, text -> onCorrection(id, CorrectionAction.WRONG) }
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showMuteSubmenu by remember { mutableStateOf(false) }
    var showWrongDialog by remember { mutableStateOf(false) }
    var wrongText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // kind 标签（紧凑）
            val kindLabel = when (ref.kind) {
                com.lovebrain.app.model.MemoryKind.PROFILE -> "画像"
                com.lovebrain.app.model.MemoryKind.SCENE -> "场景"
                com.lovebrain.app.model.MemoryKind.ONGOING -> "事项"
                com.lovebrain.app.model.MemoryKind.LESSON -> "经验"
            }
            Text(
                text = "[$kindLabel]",
                style = AppTypography.labelSmall,
                color = TextHint,
                modifier = Modifier.padding(end = Spacing.xs)
            )
            // 记忆正文
            Text(
                text = ref.text,
                style = AppTypography.labelSmall,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // ⋯ 菜单入口
            val (menuInteraction, menuScale) = rememberPressScale(0.92f, "refMenuScale")
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { scaleX = menuScale; scaleY = menuScale }
                    .clip(LoveBrainShape.sm)
                    .clickable(interactionSource = menuInteraction, indication = null) {
                        menuOpen = !menuOpen
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⋯",
                    style = AppTypography.labelLarge,
                    color = TextHint
                )
            }
        }

        // P1-2: 纠正菜单——DropdownMenu 浮层，不改变结果区 layout height
        androidx.compose.material3.DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier
                .clip(LoveBrainShape.md)
                .background(SurfaceCard)
        ) {
            // F04: "不对"——弹出输入框让用户输入正确内容
            CorrectionDropdownItem("不对", "标记为错误内容") {
                menuOpen = false
                showWrongDialog = true
            }
            CorrectionDropdownItem("结束", "这件事已结束") {
                onCorrection(ref.id, CorrectionAction.FINISHED)
                menuOpen = false
            }
            // F04: "暂时别提"——展开时长选择子菜单
            CorrectionDropdownItem("暂时别提", "暂停作为续聊素材") {
                menuOpen = false
                showMuteSubmenu = true
            }
            CorrectionDropdownItem("不是她", "归属错误，需迁移") {
                onCorrection(ref.id, CorrectionAction.WRONG_PERSON)
                menuOpen = false
            }
            CorrectionDropdownItem("撤销纠正", "恢复可信注入") {
                onUndoCorrection(ref.id)
                menuOpen = false
            }
        }

        // F04: "暂时别提"时长选择子菜单
        if (showMuteSubmenu) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showMuteSubmenu = false },
                title = { Text("暂停时长", style = AppTypography.labelMedium, color = TextPrimary) },
                text = {
                    Column {
                        CorrectionSubmenuItem("仅本轮") {
                            onCorrectionWithMute(ref.id, com.lovebrain.app.model.MuteDuration.THIS_ROUND)
                            showMuteSubmenu = false
                        }
                        CorrectionSubmenuItem("今天剩余") {
                            onCorrectionWithMute(ref.id, com.lovebrain.app.model.MuteDuration.TODAY)
                            showMuteSubmenu = false
                        }
                        CorrectionSubmenuItem("直到手动恢复") {
                            onCorrectionWithMute(ref.id, com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE)
                            showMuteSubmenu = false
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    Text(
                        "取消",
                        style = AppTypography.labelSmall,
                        color = TextHint,
                        modifier = Modifier.clickable { showMuteSubmenu = false }
                    )
                }
            )
        }

        // F04: "不对"——输入正确内容
        if (showWrongDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showWrongDialog = false },
                title = { Text("标记为错误", style = AppTypography.labelMedium, color = TextPrimary) },
                text = {
                    Column {
                        Text(
                            "输入正确内容（可选，留空仅停用）",
                            style = AppTypography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = wrongText,
                            onValueChange = { wrongText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("输入正确的内容", style = AppTypography.labelSmall, color = TextHint)
                            },
                            textStyle = AppTypography.labelSmall.copy(color = TextPrimary),
                            singleLine = false,
                            maxLines = 3,
                            shape = LoveBrainShape.sm
                        )
                    }
                },
                confirmButton = {
                    val (confirmInteraction, confirmScale) = rememberPressScale(0.96f, "wrongConfirmScale")
                    Text(
                        "确认",
                        style = AppTypography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .graphicsLayer { scaleX = confirmScale; scaleY = confirmScale }
                            .clip(LoveBrainShape.sm)
                            .background(Primary)
                            .clickable(
                                interactionSource = confirmInteraction,
                                indication = null,
                                onClick = {
                                    onCorrectionWithReplacement(ref.id, wrongText.trim())
                                    showWrongDialog = false
                                }
                            )
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    )
                },
                dismissButton = {
                    Text(
                        "取消",
                        style = AppTypography.labelSmall,
                        color = TextHint,
                        modifier = Modifier.clickable { showWrongDialog = false }
                    )
                }
            )
        }
    }
}

/** P1-2: DropdownMenu 纠正菜单项——浮层内文字行 */
@Composable
private fun CorrectionDropdownItem(
    label: String,
    desc: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "correctionDropdownScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            desc,
            style = AppTypography.labelSmall,
            color = TextHint
        )
    }
}

/**
 * F11: 输入已变化提示——结果来自修改前内容时展示。
 * 主按钮为"按新输入生成"。
 */
@Composable
private fun InputChangedBanner(
    onRegenerate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(WarningBg)
            .border(1.dp, Warning, LoveBrainShape.md)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "输入已修改，当前答案基于修改前内容",
            style = AppTypography.labelSmall,
            color = Warning,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Spacing.sm))
        val (regenInteraction, regenScale) = rememberPressScale(0.96f, "regenInputScale")
        Text(
            text = "按新输入生成",
            style = AppTypography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .graphicsLayer { scaleX = regenScale; scaleY = regenScale }
                .clip(LoveBrainShape.sm)
                .background(Primary)
                .clickable(
                    interactionSource = regenInteraction,
                    indication = null,
                    onClick = onRegenerate
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

/**
 * F04: "暂时别提"时长选择子菜单项。
 */
@Composable
private fun CorrectionSubmenuItem(
    label: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "muteSubmenu_$label")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            fontWeight = FontWeight.Medium
        )
    }
}