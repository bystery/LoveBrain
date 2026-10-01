package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/** 结果区内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
internal object ResultDimens {
    /** 结果区工具入口的可点击盒子下限（视觉字形仍 28dp） */
    const val UTILITY_HITBOX_DP = AppDimens.TOUCH_TARGET_MIN_DP
    const val FILTER_TAB_HEIGHT_DP = 28      // 筛选 Tab 高度
    const val SKELETON_TAG_WIDTH_DP = 60     // 骨架标签条宽度
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
    // 本轮参考记忆 + 纠正回调
    memoryRefs: List<MemoryRef> = emptyList(),
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit = { _, _, _, _ -> },
    onUndoCorrection: (String) -> Unit = {},
    providerReady: Boolean,
    onOpenSettings: () -> Unit,
    // 单条改写
    rewriteStates: Map<String, RewriteState> = emptyMap(),
    onRewrite: (SchemeIdentity, RewriteCommand) -> Unit = { _, _ -> },
    onClearRewriteState: (SchemeIdentity) -> Unit = {},
    onCancelRewrite: (SchemeIdentity) -> Unit = {},
    onUndoRewrite: (SchemeIdentity) -> Unit = {},
    // 自定义改写回调
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    // 仅看本轮开关
    onlyThisRound: Boolean = false,
    onToggleOnlyThisRound: () -> Unit = {},
    // 输入已变化提示
    inputChanged: Boolean = false,
    onRegenerateWithNewInput: () -> Unit = {},
    // 记录实际发送——: 不自动绑定第一张卡，调用方决定是否预填
    onRecordSent: () -> Unit = {},
    // 打开记忆纠正中心
    onShowCorrectionCenter: () -> Unit = {},
    // §6.4：纠正浮层的状态与渲染归 MemoryCorrectionFlow。
    // 面板顶层传进来时遮罩盖满面板；没传就在本地建一颗并就地渲染——
    // 少一处接线不会变成"点了没反应"。
    correctionFlow: MemoryCorrectionFlow? = null,
    // 稳定轮次身份——只在整轮 generate 成功时变化
    generationRoundId: Int = 0,
    modifier: Modifier = Modifier
) {
    when {
        // Phase 1 加载中：核心回复（schemes）还没出来
        isGeneratingCore -> {
            if (streamingSchemes.isNotEmpty()) {
                // ★ 边流式边出卡——只渲染风格（directions 不再 stream）
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
            // 输入已变化提示——结果来自旧输入时展示
            if (inputChanged) {
                InputChangedBanner(onRegenerate = onRegenerateWithNewInput)
                Spacer(Modifier.height(Spacing.sm))
            }

            // viewMode 只以 generationRoundId 重置——单条改写/undo/feedback 不切换用户当前 STYLE/DIRECTION
            var viewMode by remember(generationRoundId) { mutableStateOf(SchemeViewMode.STYLE) }
            val hasDirections = response.directionSchemes.any { it.reply.isNotBlank() }
            val displaySchemes = when (viewMode) {
                SchemeViewMode.STYLE -> response.schemes
                SchemeViewMode.DIRECTION -> response.directionSchemes
            }
            // showRefs 状态提升到 Success 层——
            // ResultUtilityTrigger 只负责 toggle 事件，MemoryRefsSection 在主 Column 中渲染。
            // 默认 showRefs=false 不增加高度；用户展开后正常增加高度。
            var showRefs by remember(generationRoundId) { mutableStateOf(false) }
            // 谁持有 flow 谁渲染宿主：调用方给了就用人家的、不再就地画
            val flow = correctionFlow ?: rememberMemoryCorrectionFlow()
            val hostLocally = correctionFlow == null

            // Box 外层——ResultUtilityTrigger 用 align(TopEnd) 覆盖，
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
            // 风格/方向切换器——有方向时显示
            if (hasDirections) {
                SchemeViewSwitcher(
                    mode = viewMode,
                    onModeChange = { viewMode = it }
                )
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
                        onCustomRewrite = onCustomRewrite,
                        onlyThisRound = onlyThisRound,
                        onToggleOnlyThisRound = onToggleOnlyThisRound
                    )

                    if (response.analysis.ongoing.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        OngoingSection(items = response.analysis.ongoing)
                    }

                    // MemoryRefsSection 在主 Column 中按正常文档流渲染——
                    // 默认 showRefs=false 时 AnimatedVisibility 不占高度；
                    // 用户主动展开后正常增加信息高度，不覆盖方案卡或切换器。
                    if (memoryRefs.isNotEmpty()) {
                        MemoryRefsSection(
                            memoryRefs = memoryRefs,
                            showRefs = showRefs,
                            onCorrection = onCorrection,
                            onUndoCorrection = onUndoCorrection,
                            correctionFlow = flow
                        )
                    }
                }

                // 结果级 utility trigger——右上角 overlay，不参与 Column measurement。
                // 只负责：⋯ trigger + DropdownMenu + toggle 事件。
                // 默认未展开状态额外纵向高度 = 0。
                if (hostLocally) {
                    MemoryCorrectionFlowHost(
                        flow = flow,
                        onMute = { id, duration ->
                            onCorrection(id, CorrectionAction.MUTED, "", duration)
                        },
                        onWrong = { id, text ->
                            onCorrection(
                                id, CorrectionAction.WRONG, text,
                                com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
                            )
                        }
                    )
                }

                ResultUtilityTrigger(
                    memoryRefs = memoryRefs,
                    showRefs = showRefs,
                    onToggleRefs = { showRefs = !showRefs },
                    onlyThisRound = onlyThisRound,
                    onToggleOnlyThisRound = onToggleOnlyThisRound,
                    onRecordSent = { onRecordSent() },
                    hasResult = response.schemes.any { it.reply.isNotBlank() },
                    onShowCorrectionCenter = onShowCorrectionCenter,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        result is GenerateResult.Error -> {
            // 这一档原来是自画的 `Box + .background(ErrorBg) + 居中 Column`，那颗重试
            // 已经归进 `LbTextAction`（本机当时实量 72x26dp、role=无）。整块版式现在也
            // 交回设计系统：`LbEmptyState` 的 Strip 容器 + Error 语气——错因、标签文案、
            // 点击回调三者逐字照旧，改的只是"这一条谁画"。契约变化见 LbAsyncState.kt。
            LbEmptyState(
                message = result.message,
                tone = LbStateTone.Error,
                container = LbStateContainer.Strip,
                action = ScreenAction(stringResource(R.string.panel_retry_tap), onRetry),
                modifier = modifier
            )
        }

        // 未配置供应商——空态不做死路，引导去设置页（可点通）
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
                    // 实量 **68x34dp、role=无**：同一族形状（自画 `Text + .background(Primary)`）。
                    // 这一档只有这一颗动作 ⇒ 它就是这一态的唯一主动作（§6.1 :479），
                    // 归 `LbPrimaryButton` 之后顺带第一次能表达禁用/进行中。
                    LbPrimaryButton(
                        state = LbButtonState.Idle,
                        label = stringResource(com.lovebrain.app.R.string.provider_open_settings),
                        onClick = { onOpenSettings() }
                    )
                }
            }
        }

        // 空状态：生成前直接留白
        else -> {
            Spacer(modifier = modifier.fillMaxWidth())
        }
    }
}

/** 风格/方向视图模式 */
enum class SchemeViewMode { STYLE, DIRECTION }

/** 方案筛选模式 */
private enum class SchemeFilter { ALL, LIKED }

/** 方案卡片行——Phase 1 完成就立刻渲染 */
/** 轻量风格/方向切换——高度≤28dp，使用现有配色，不成为视觉主角 */
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
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    // 仅看本轮开关
    onlyThisRound: Boolean = false,
    onToggleOnlyThisRound: () -> Unit = {}
) {
        // 仅看本轮开关——在方案卡行上方
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
    // filter 绑定 scheme group source——切换 STYLE/DIRECTION 时默认回 ALL
    val currentSource = schemes.firstOrNull()?.source
    var filter by remember(currentSource) { mutableStateOf(SchemeFilter.ALL) }
    val likedCount = schemes.count { feedbacks[it.identity.key] == SchemeFeedback.LIKED }

    // 任何时候 likedCount=0 时不保持 LIKED——防止空页死角
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
    // 去掉当前卡片指示器（原 activeIndex 追踪已移除）
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
            //修复：入场动画只在首次出现时播放一次（playedTags 集合）。
            // LazyRow item 离开视口会销毁 remember，若动画状态留在 item 内，
            // 从右向左滑（item 重新组合）会重播动画 → 卡片"闪一下"。提升到外层集合解决。
            val playedTags = remember { mutableStateMapOf<String, Boolean>() }
            // 行级唯一展开状态——同一时间只展开一张卡
            var expandedRewriteTag by remember { mutableStateOf<String?>(null) }
            LazyRow(
                state = scrollState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth()
                    .testTag("scheme_cards_row")
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
                    // expandedRewriteTag 已提升到行级——切卡自动收上张

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
                            onCustomRewrite = onCustomRewrite
                        )
                    }
                }
            }
            // 去掉卡片下方的四个点（当前卡片指示器）
        }
    }
}

/**
 * 方案筛选 Tab：选中态高亮 + 点击切换（与 ActionChip 样式统一）
 *
 * 热区与视觉**分两层**：外面那颗 ≥48dp 见方的盒负责"点得中"和语义（role/selected），
 * 里面那颗 28dp 胶囊负责"长什么样"。
 * 以前只有里面那颗，它既画外观又当点击点，本机语义树实量 **46x28dp**
 * （§6.5 :531 要的是 clickable 边界 ≥48×48）——这一屏第一次被挂进仪器就量到了。
 * 胶囊高度保持不动，所以外观没变，变的是"要点多准才算点到"。
 */
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
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            // clickable 排在 padding 之前：排后面等于自己把热区削掉一圈
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { selected = isSelected }
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
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
        // 骨架卡尺寸引用 SchemeCardDimens（166→150 对齐实体卡，消除加载完成瞬间跳变）
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
 * 输入已变化提示——结果来自修改前内容时展示，主操作是"按新输入生成"。
 *
 * 形状原来是自己画的：一条 `Row` + `WarningBg` 底 + 1dp `Warning` 描边，尾部那颗实心
 * 胶囊是裸 `Text.clickable`——本机语义树读得到的是 **role=无**、两轴都没垫到下限
 * （它就是异形账本里 `ResultArea.kt#InputChangedBanner` 那一颗）。
 * 现在转进 `LbEmptyState` 的 Strip 容器 + Warning 语气：说法、回调、语气三样照旧，
 * 补齐的是热区与按钮角色，随归并一起消失的是那条描边与那颗胶囊底——留住它们就得给
 * 组件开一个 `borderColor`/`containerColor` 旋钮，代价写在 `LbAsyncState.kt` 的契约段
 * （同一处置在 `LbTextAction` 收编 `RowActionButton` 时记过一次：归并换的是所有者）。
 * 文案两条都进了 `res/values` + `values-en`，英文环境下不再念中文。
 */
@Composable
private fun InputChangedBanner(
    onRegenerate: () -> Unit
) {
    LbEmptyState(
        message = stringResource(R.string.panel_input_changed),
        tone = LbStateTone.Warning,
        container = LbStateContainer.Strip,
        action = ScreenAction(stringResource(R.string.panel_regenerate_with_new_input), onRegenerate)
    )
}
