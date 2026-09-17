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
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
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
    isGenerating: Boolean,
    streamingCoreText: String,
    isGeneratingCore: Boolean,
    streamingSchemes: List<Scheme>,
    streamingDirectionSchemes: List<Scheme> = emptyList(),
    feedbacks: Map<String, SchemeFeedback>,
    onFeedback: (String, SchemeFeedback) -> Unit,
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
    onRewrite: (String, String) -> Unit = { _, _ -> },
    onClearRewriteState: (String) -> Unit = {},
    onCancelRewrite: (String) -> Unit = {},
    onUndoRewrite: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when {
        // Phase 1 加载中：核心回复（schemes）还没出来
        isGeneratingCore -> {
            if (streamingSchemes.isNotEmpty() || streamingDirectionSchemes.isNotEmpty()) {
                // ★ P1-07: 边流式边出卡——风格和方向独立渲染
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
                            onUndoRewrite = onUndoRewrite
                        )
                    }
                    // UI 冻结：四方向不再独立行展示，合并入 SchemeCardsRow
                    if (streamingDirectionSchemes.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        SchemeCardsRow(
                            schemes = streamingDirectionSchemes,
                            feedbacks = emptyMap(),
                            onFeedback = { _, _ -> },
                            onCopyScheme = onCopyScheme,
                            rewriteStates = rewriteStates,
                            onRewrite = onRewrite,
                            onClearRewriteState = onClearRewriteState,
                            onCancelRewrite = onCancelRewrite,
                            onUndoRewrite = onUndoRewrite
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
                    rewriteStates = rewriteStates,
                    onRewrite = onRewrite,
                    onClearRewriteState = onClearRewriteState,
                    onCancelRewrite = onCancelRewrite,
                    onUndoRewrite = onUndoRewrite
                )

                // UI 冻结：四方向不再独立行展示，统一用 SchemeCardsRow
                val dirs = response.directionSchemes
                if (dirs.any { it.reply.isNotBlank() }) {
                    Spacer(Modifier.height(Spacing.sm))
                    SchemeCardsRow(
                        schemes = dirs,
                        feedbacks = emptyMap(),
                        onFeedback = { _, _ -> },
                        onCopyScheme = onCopyScheme,
                        rewriteStates = rewriteStates,
                        onRewrite = onRewrite,
                        onClearRewriteState = onClearRewriteState,
                        onCancelRewrite = onCancelRewrite,
                        onUndoRewrite = onUndoRewrite
                    )
                }

                if (response.analysis.ongoing.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    OngoingSection(items = response.analysis.ongoing)
                }

                // F09: 本轮参考 + 记入知识库 共享工具行（禁止独占行按钮）
                Spacer(Modifier.height(Spacing.sm))
                ResultToolRow(
                    memoryRefs = memoryRefs,
                    onSaveToKb = onSaveToKb,
                    onCorrection = onCorrection,
                    onUndoCorrection = onUndoCorrection
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

/** 方案筛选模式 */
private enum class SchemeFilter { ALL, LIKED }

/** 方案卡片行——Phase 1 完成就立刻渲染 */
@Composable
private fun SchemeCardsRow(
    schemes: List<Scheme>,
    feedbacks: Map<String, SchemeFeedback>,
    onFeedback: (String, SchemeFeedback) -> Unit,
    onCopyScheme: (Scheme) -> Unit,
    rewriteStates: Map<String, RewriteState> = emptyMap(),
    onRewrite: (String, String) -> Unit = { _, _ -> },
    onClearRewriteState: (String) -> Unit = {},
    onCancelRewrite: (String) -> Unit = {},
    onUndoRewrite: (String) -> Unit = {}
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
            // 阻断C修复：行级唯一展开状态——同一时间只展开一张卡
            var expandedRewriteTag by remember { mutableStateOf<String?>(null) }
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
                            feedback = feedbacks[scheme.tag] ?: SchemeFeedback.NONE,
                            onFeedback = onFeedback,
                            onCopy = onCopyScheme,
                            rewriteState = rewriteStates[scheme.tag],
                            onRewrite = onRewrite,
                            onClearRewriteState = onClearRewriteState,
                            onCancelRewrite = onCancelRewrite,
                            onUndoRewrite = onUndoRewrite,
                            isExpanded = expandedRewriteTag == scheme.tag,
                            onToggleRewriteExpand = { tag ->
                                // b2-6: 切卡时自动收上张；同卡点击切换展开/收起
                                // 展开前先清旧改写状态（Done/Error），使选项区干净展示
                                val currentRewriteState = rewriteStates[tag]
                                if (expandedRewriteTag != tag) {
                                    // 展开新卡：先清状态再展开
                                    if (currentRewriteState is RewriteState.Done ||
                                        currentRewriteState is RewriteState.Error) {
                                        onClearRewriteState(tag)
                                    }
                                    expandedRewriteTag = tag
                                } else {
                                    // 收起当前卡
                                    expandedRewriteTag = null
                                }
                            }
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

/** P1-07: 四方向卡片行——独立于四风格，紧凑展示。
 * P0-4: 长按卡片触发 STT 语音修改（用户说话 → 用语音指令改写该条回复） */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DirectionCardsRow(
    schemes: List<Scheme>,
    onCopyScheme: (Scheme) -> Unit,
    rewriteStates: Map<String, RewriteState> = emptyMap(),
    onVoiceRewrite: (String, String) -> Unit = { _, _ -> }
) {
    Column {
        Text(
            text = "四方向",
            style = AppTypography.labelSmall,
            color = TextHint,
            modifier = Modifier.padding(bottom = Spacing.xs)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(schemes, key = { it.tag }) { scheme ->
                DirectionCard(
                    scheme = scheme,
                    onCopy = { onCopyScheme(scheme) },
                    rewriteState = rewriteStates[scheme.tag],
                    onVoiceRewrite = onVoiceRewrite
                )
            }
        }
    }
}

/**
 * P0-4: STT 长按语音修改状态。
 * - IDLE：未开始
 * - RECORDING：正在录音/识别中
 * - PROCESSING：等待 final transcript
 * - REWRITING：已发送改写请求，等待结果
 */
private enum class VoiceRewriteState {
    IDLE, RECORDING, PROCESSING, REWRITING
}

/**
 * P0-4: 长按语音修改 Helper——封装 SpeechRecognizer 生命周期。
 *
 * 长按开始 → 启动 SpeechRecognizer
 * 松手 → 停止录音，等待 final transcript
 * transcript 非空 → 调用 onVoiceRewrite(tag, transcript)
 * transcript 为空 → 取消，不发 API
 * partial transcript 不直接发请求
 */
/** 语音改写控制器返回值：state + startListening + stopListening + cancel */
private data class VoiceRewriteController(
    val state: VoiceRewriteState,
    val startListening: () -> Unit,
    val stopListening: () -> Unit,
    val cancel: () -> Unit
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun rememberVoiceRewriteController(
    schemeTag: String,
    onVoiceRewrite: (String, String) -> Unit
): VoiceRewriteController {
    val context = androidx.compose.ui.platform.LocalContext.current
    var voiceState by remember { mutableStateOf(VoiceRewriteState.IDLE) }
    val speechRecognizer = remember { mutableStateOf<android.speech.SpeechRecognizer?>(null) }
    val accumulatedText = remember { StringBuilder() }
    val tagRef = remember { schemeTag }

    fun startListening() {
        // 检查录音权限
        if (context.checkPermission(android.Manifest.permission.RECORD_AUDIO,
                android.os.Process.myPid(), android.os.Process.myUid()) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            voiceState = VoiceRewriteState.IDLE
            L.w("VoiceRewrite: RECORD_AUDIO permission not granted")
            return
        }

        accumulatedText.clear()
        voiceState = VoiceRewriteState.RECORDING

        try {
            val sr = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer.value = sr

            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            sr.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    voiceState = VoiceRewriteState.PROCESSING
                }
                override fun onError(error: Int) {
                    L.w("VoiceRewrite: STT error=$error")
                    voiceState = VoiceRewriteState.IDLE
                    speechRecognizer.value = null
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    // P0-4: partial transcript 不直接发请求
                    val partial = partialResults
                        ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        accumulatedText.clear()
                        accumulatedText.append(partial)
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val finalText = results
                        ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?: accumulatedText.toString()

                    voiceState = VoiceRewriteState.IDLE
                    speechRecognizer.value = null

                    // P0-4: final transcript 为空不发 API
                    if (finalText.isNotBlank()) {
                        voiceState = VoiceRewriteState.REWRITING
                        // P0-4: transcript 只作为本次控制指令，不进入真实聊天历史
                        onVoiceRewrite(tagRef, finalText.trim())
                    } else {
                        L.w("VoiceRewrite: final transcript empty, not sending API")
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            sr.startListening(intent)
        } catch (e: Exception) {
            L.w("VoiceRewrite: SpeechRecognizer failed: ${e.message}")
            voiceState = VoiceRewriteState.IDLE
        }
    }

    fun stopListening() {
        voiceState = VoiceRewriteState.PROCESSING
        speechRecognizer.value?.stopListening()
    }

    fun cancel() {
        speechRecognizer.value?.cancel()
        speechRecognizer.value = null
        voiceState = VoiceRewriteState.IDLE
        accumulatedText.clear()
    }

    // 清理
    androidx.compose.runtime.DisposableEffect(schemeTag) {
        onDispose {
            speechRecognizer.value?.cancel()
            speechRecognizer.value = null
        }
    }

    return VoiceRewriteController(
        state = voiceState,
        startListening = { startListening() },
        stopListening = { stopListening() },
        cancel = { cancel() }
    )
}

/** P1-07: 单张方向卡——精简版（无赞踩），仅复制 + 长按语音修改
 * P0-4: 长按触发 STT → 用户说话 → 松手 → 用语音指令改写该条回复 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DirectionCard(
    scheme: Scheme,
    onCopy: () -> Unit,
    rewriteState: RewriteState? = null,
    onVoiceRewrite: (String, String) -> Unit = { _, _ -> }
) {
    val isEmpty = scheme.reply.isBlank()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "dirCardScale"
    )

    val tagColor = if (isEmpty) TextHint else PrimaryDark
    val tagBg = if (isEmpty) SurfaceInset else PrimaryLight
    val cardBg = if (isEmpty) SurfaceInset else SurfaceCard
    val bodyColor = if (isEmpty) TextHint else TextPrimary

    // P0-4: STT 长按语音修改控制器
    val voiceController = rememberVoiceRewriteController(scheme.tag, onVoiceRewrite)
    val voiceState = voiceController.state

    // 手势生命周期：长按开始录音 → 松手停止 → 滑出取消
    // 检测 pressed 状态变化，松手时自动停止录音
    LaunchedEffect(isPressed, voiceState) {
        if (!isPressed && voiceState == VoiceRewriteState.RECORDING) {
            // 松手 → 停止录音，等待 final transcript
            voiceController.stopListening()
        }
    }

    // 改写状态——优先展示外部 rewriteState
    val isRewriting = voiceState == VoiceRewriteState.REWRITING
        || rewriteState is RewriteState.Loading
    val rewriteError = (rewriteState as? RewriteState.Error)?.message
    val rewriteDone = rewriteState is RewriteState.Done

    // 录音中状态
    val isRecording = voiceState == VoiceRewriteState.RECORDING || voiceState == VoiceRewriteState.PROCESSING

    Box(
        modifier = Modifier
            .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
            .then(if (isRecording || isRewriting || rewriteError != null || rewriteDone)
                Modifier.wrapContentHeight() else Modifier.height(SchemeCardDimens.CARD_HEIGHT_DP.dp))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.lg)
            .clip(LoveBrainShape.lg)
            .background(cardBg)
            .border(
                if (isRecording || isRewriting) 2.dp else 1.dp,
                if (isRecording || isRewriting) Primary else if (rewriteError != null) Error else Border,
                LoveBrainShape.lg
            )
            .then(if (isEmpty) Modifier else Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCopy() },
                onLongClick = {
                    // P0-4: 长按触发 STT 录音
                    if (!isEmpty && !isRewriting && !isRecording) {
                        voiceController.startListening()
                    }
                }
            ))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            // 标签行
            Box(
                modifier = Modifier
                    .background(tagBg, LoveBrainShape.sm)
                    .padding(horizontal = SchemeCardDimens.TAG_HPAD_DP.dp, vertical = SchemeCardDimens.TAG_VPAD_DP.dp)
            ) {
                Text(
                    text = "${scheme.tag} · ${scheme.title}",
                    color = tagColor,
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(SchemeCardDimens.TAG_TO_BODY_GAP_DP.dp))

            if (isEmpty) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "本轮不适合",
                        color = TextHint,
                        style = AppTypography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (isRecording) {
                // P0-4: 录音中——显示录音状态
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = if (voiceState == VoiceRewriteState.PROCESSING) "识别中…" else "正在录音…",
                            style = AppTypography.labelSmall,
                            color = PrimaryDark
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "松手后用语音指令修改",
                            style = AppTypography.labelSmall,
                            color = TextHint
                        )
                    }
                }
            } else if (isRewriting) {
                // 改写中状态
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        "正在改写…",
                        style = AppTypography.labelSmall,
                        color = PrimaryDark
                    )
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f, fill = !rewriteDone && rewriteError == null).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = scheme.reply,
                        color = bodyColor,
                        style = AppTypography.bodyMedium,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            if (!isEmpty) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rewriteError != null) {
                        Text(
                            rewriteError,
                            style = AppTypography.labelSmall,
                            color = Error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else if (rewriteDone) {
                        Text(
                            "撤销",
                            style = AppTypography.labelSmall,
                            color = PrimaryDark,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* TODO: onUndoRewrite */ }
                            ).padding(Spacing.xs)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                    } else {
                        Text(
                            text = "长按说话修改",
                            style = AppTypography.labelSmall,
                            color = TextHint
                        )
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    CardActionIcon(
                        icon = com.lovebrain.app.R.drawable.ic_copy,
                        desc = "复制",
                        tint = TextSecondary,
                        onClick = onCopy
                    )
                }
            }
        }
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

// ═══════════ F09: 本轮参考 + 记入知识库 共享工具行 ═══════════

/**
 * F09: 结果区工具行 — "本轮参考"入口 + "记入知识库"按钮共享一行。
 * 禁止独占行：两个功能紧凑放在同一 Row 中。
 * 本轮参考展开后每条只显示记忆文本 + ⋯ 菜单。
 */
@Composable
private fun ResultToolRow(
    memoryRefs: List<MemoryRef>,
    onSaveToKb: () -> Unit,
    onCorrection: (String, CorrectionAction) -> Unit,
    onUndoCorrection: (String) -> Unit
) {
    var showRefs by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：本轮参考入口（仅在有记忆引用时显示）
            if (memoryRefs.isNotEmpty()) {
                val (refsInteraction, refsScale) = rememberPressScale(0.96f, "refsToggleScale")
                Row(
                    modifier = Modifier
                        .graphicsLayer { scaleX = refsScale; scaleY = refsScale }
                        .clip(LoveBrainShape.md)
                        .background(SurfaceInset, LoveBrainShape.md)
                        .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
                        .clickable(interactionSource = refsInteraction, indication = null) {
                            showRefs = !showRefs
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "本轮参考 ${memoryRefs.size}",
                        style = AppTypography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        if (showRefs) "▾" else "▸",
                        style = AppTypography.labelSmall,
                        color = TextHint
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            // 无引用时不加 Spacer(weight=1f)——保存按钮紧跟行首，不独占右侧

            // 右：记入知识库按钮
            val (saveInteraction, saveScale) = rememberPressScale(0.96f, "resultSaveKbScale")
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = saveScale; scaleY = saveScale }
                    .clip(LoveBrainShape.md)
                    .background(Primary, LoveBrainShape.md)
                    .clickable(interactionSource = saveInteraction, indication = null, onClick = onSaveToKb)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "记入知识库",
                    color = Color.White,
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 展开后的记忆引用列表
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
                // b2-7: 引用列表限制前5条，超出显示"更多"
                val maxInitialRefs = 5
                var showAllRefs by remember { mutableStateOf(false) }
                val displayRefs = if (showAllRefs) memoryRefs else memoryRefs.take(maxInitialRefs)
                displayRefs.forEachIndexed { index, ref ->
                    if (index > 0) Spacer(Modifier.height(Spacing.sm))
                    MemoryRefItem(
                        ref = ref,
                        onCorrection = onCorrection,
                        onUndoCorrection = onUndoCorrection
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
}

/**
 * F09: 单条记忆引用 — 只显示正文 + ⋯ 菜单。
 * ⋯ 菜单内提供：不对 / 结束 / 暂时别提 / 不是她 / 撤销。
 */
@Composable
private fun MemoryRefItem(
    ref: MemoryRef,
    onCorrection: (String, CorrectionAction) -> Unit,
    onUndoCorrection: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

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

        // 菜单展开
        AnimatedVisibility(
            visible = menuOpen,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, top = Spacing.xs)
            ) {
                CorrectionMenuItem("不对", "标记为错误内容") {
                    onCorrection(ref.id, CorrectionAction.WRONG)
                    menuOpen = false
                }
                CorrectionMenuItem("结束", "这件事已结束") {
                    onCorrection(ref.id, CorrectionAction.FINISHED)
                    menuOpen = false
                }
                CorrectionMenuItem("暂时别提", "暂停作为续聊素材") {
                    onCorrection(ref.id, CorrectionAction.MUTED)
                    menuOpen = false
                }
                CorrectionMenuItem("不是她", "归属错误，需迁移") {
                    onCorrection(ref.id, CorrectionAction.WRONG_PERSON)
                    menuOpen = false
                }
                CorrectionMenuItem("撤销纠正", "恢复可信注入") {
                    onUndoCorrection(ref.id)
                    menuOpen = false
                }
            }
        }
    }
}

/** 纠正菜单项 — 紧凑文字行 */
@Composable
private fun CorrectionMenuItem(
    label: String,
    desc: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "correctionItemScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
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
