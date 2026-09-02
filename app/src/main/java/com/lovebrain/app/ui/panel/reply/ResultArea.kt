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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*
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
    isGenerating: Boolean,
    streamingCoreText: String,
    isGeneratingCore: Boolean,
    streamingSchemes: List<Scheme>,
    feedbacks: Map<String, SchemeFeedback>,
    onFeedback: (String, SchemeFeedback) -> Unit,
    onCopyScheme: (Scheme) -> Unit,
    onRetry: () -> Unit,
    providerReady: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        // Phase 1 加载中：核心回复（schemes）还没出来
        isGeneratingCore -> {
            if (streamingSchemes.isNotEmpty()) {
                // ★ 边流式边出卡：已解析到的方案立即渲染，其余等待
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    SchemeCardsRow(
                        schemes = streamingSchemes,
                        feedbacks = feedbacks,
                        onFeedback = onFeedback,
                        onCopyScheme = onCopyScheme,

                    )
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
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SchemeCardsRow(
                    schemes = response.schemes,
                    feedbacks = feedbacks,
                    onFeedback = onFeedback,
                    onCopyScheme = onCopyScheme,
                )

                if (response.analysis.ongoing.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    OngoingSection(items = response.analysis.ongoing)
                }
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

/** 方案筛选模式 */
private enum class SchemeFilter { ALL, LIKED }

/** 方案卡片行——Phase 1 完成就立刻渲染 */
@Composable
private fun SchemeCardsRow(
    schemes: List<Scheme>,
    feedbacks: Map<String, SchemeFeedback>,
    onFeedback: (String, SchemeFeedback) -> Unit,
    onCopyScheme: (Scheme) -> Unit
) {
    // 方案筛选：全部 / 已赞（调研：NN/G 10 Heuristics #6 Recognition rather than recall——
    // 用户赞过的方案应能快速回看，无需在 4 张卡里翻找）
    var filter by rememberSaveable { mutableStateOf(SchemeFilter.ALL) }
    val likedCount = schemes.count { feedbacks[it.tag] == SchemeFeedback.LIKED }
    val displaySchemes = when (filter) {
        SchemeFilter.ALL -> schemes
        SchemeFilter.LIKED -> schemes.filter { feedbacks[it.tag] == SchemeFeedback.LIKED }
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
            LazyRow(
                state = scrollState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(displaySchemes, key = { it.tag }) { scheme ->
                    // 入场动效：淡入+上移，逐张交错 60ms（仅首次组合播放）
                    val index = displaySchemes.indexOfFirst { it.tag == scheme.tag }
                    var visible by remember { mutableStateOf(playedTags[scheme.tag] ?: false) }
                    LaunchedEffect(Unit) {
                        if (!(playedTags[scheme.tag] ?: false)) {
                            playedTags[scheme.tag] = true
                            visible = true
                        }
                    }
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
                            feedback = feedbacks[scheme.tag] ?: SchemeFeedback.NONE,
                            onFeedback = onFeedback,
                            onCopy = onCopyScheme
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
