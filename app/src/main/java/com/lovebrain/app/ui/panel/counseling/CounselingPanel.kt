package com.lovebrain.app.ui.panel.counseling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.AppConfig
import com.lovebrain.app.R
import com.lovebrain.app.ui.panel.AiLoadingRow
import com.lovebrain.app.ui.panel.DraggableDivider
import com.lovebrain.app.ui.panel.MarkdownText
import com.lovebrain.app.ui.panel.TriangleArrow
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.LoveBrainViewModel

/** 谈心面板内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object CounselingDimens {
    const val CTA_HEIGHT_DP = 40            // 脉冲条/开始谈心按钮高度（2 文件各自私有）
    const val FADE_MASK_WIDTH_DP = 24       // 模板 chip 尾部渐隐遮罩宽
    const val FADE_MASK_HEIGHT_DP = 28      // 模板 chip 尾部渐隐遮罩高
    const val PLACEHOLDER_TOP_PAD_DP = 1    // 输入框占位文字顶部对齐内边距
}

@Composable
fun CounselingPanel(
    viewModel: LoveBrainViewModel,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft by viewModel.counselingDraft.collectAsStateWithLifecycle()
    val result by viewModel.counselingResult.collectAsStateWithLifecycle()
    val error by viewModel.counselingError.collectAsStateWithLifecycle()
    val isCounseling by viewModel.isCounseling.collectAsStateWithLifecycle()
    val streaming by viewModel.counselingStreaming.collectAsStateWithLifecycle()

    var inputHeight by remember { mutableFloatStateOf(100f) }
    val density = LocalDensity.current.density

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(inputHeight.dp)
                .clip(LoveBrainShape.lg)
                .background(SurfaceCard)
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
                .padding(Spacing.lg)
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { viewModel.setCounselingDraft(it) },
                textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .onFocusChanged { state ->
                        onFocusChange(state.isFocused)
                    }
            )

            if (draft.isEmpty()) {
                Text(
                    text = "说说你的困惑，军师帮你分析…",
                    color = TextHint,
                    style = AppTypography.bodyMedium,
                    modifier = Modifier.padding(top = CounselingDimens.PLACEHOLDER_TOP_PAD_DP.dp)
                )
            }
        }

        DraggableDivider(
            onDragDelta = { delta ->
                inputHeight = (inputHeight + delta / density).coerceIn(60f, 200f)
            }
        )

        // 谈心快速模板：未在谈心中、无结果时，显示常见困惑模板 chip（全部 6 个，一行横向滚动）
        // 第 7 轮修复（用户实测"示例没了"）：不再要求 draft.isEmpty()，也不折叠成 2 个——
        // 只要不在谈心中且无结果就全部展示；点击模板直接填入输入框。
        if (!isCounseling && result == null && error == null) {
            Spacer(Modifier.height(Spacing.xs))
            val templates = listOf(
                "她突然冷淡了怎么办",
                "我们吵架了该谁先低头",
                "她说了这句话什么意思",
                "怎么判断她喜不喜欢我",
                "暧昧期怎么推进关系",
                "她嫌我不够浪漫"
            )
            // 尾部渐隐遮罩，提示后面还有可滑动的 chip
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    templates.forEach { template ->
                        TemplateChip(text = template) { viewModel.setCounselingDraft(template) }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(CounselingDimens.FADE_MASK_WIDTH_DP.dp)
                        .height(CounselingDimens.FADE_MASK_HEIGHT_DP.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, SurfaceBase)
                            )
                        )
                )
            }
        }

        // 字数提示：超过 100 字时显示，超过 500 字变橙色提醒
        // 调研依据：NN/G 10 Heuristics #5 Error Prevention——提前提示而非事后报错
        if (draft.length > 100) {
            Text(
                text = "${draft.length} 字",
                color = if (draft.length > 500) Warning else TextHint,
                style = AppTypography.labelSmall,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = Spacing.xs)
            )
        }

        val canStart = draft.isNotBlank() && !isCounseling
        // 谈心中脉冲动画（与 GenerateButton 一致的视觉反馈）——
        // 仅在 isCounseling 时创建 rememberInfiniteTransition，非谈心状态不运行动画（避免无谓重组开销）
        if (isCounseling) {
            // ：实底 Primary + PrimaryDark 叠层呼吸（对比度优于整条 alpha 脉冲）
            val pulseTransition = rememberInfiniteTransition(label = "counselingPulse")
            val overlayAlpha by pulseTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0.22f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "counselingPulseOverlay"
            )
            // 整个加载条可点击 = 强行停止谈心
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CounselingDimens.CTA_HEIGHT_DP.dp)
                    .clip(LoveBrainShape.md)
                    .background(Primary, LoveBrainShape.md)
                    .clickable { viewModel.stopCounseling() },
                contentAlignment = Alignment.Center
            ) {
                // ：PrimaryDark 叠层呼吸（不透明度 0~0.22 循环），实底之上做明暗脉动
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(PrimaryDark, LoveBrainShape.md)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(Spacing.xl),
                        strokeWidth = Spacing.xs
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = "军师聆听中…",
                        color = Color.White,
                        style = AppTypography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = "点击停止",
                        color = Color.White,
                        style = AppTypography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        } else {
            // ：开始谈心 CTA 补按压反馈（复用标准件 0.96 scale + 120ms；条件 clickable 结构保留）
            val (ctaInteraction, ctaScale) = rememberPressScale(0.96f, "ctaScale")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CounselingDimens.CTA_HEIGHT_DP.dp)
                    .graphicsLayer { scaleX = ctaScale; scaleY = ctaScale }
                    .then(if (canStart) Modifier.shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.md) else Modifier)
                    // ：禁用态对齐 GenerateButton 先例（SurfaceInset 底 + TextSecondary 文字，WCAG 对比度）
                    .background(if (canStart) Primary else SurfaceInset, LoveBrainShape.md)
                    .then(if (canStart) Modifier.clickable(interactionSource = ctaInteraction, indication = null, onClick = {
                        viewModel.generateCounseling(draft.trim())
                    }) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "开始谈心",
                    color = if (canStart) Color.White else TextSecondary,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        when {
            isCounseling -> {
                if (streaming.isBlank()) {
                    CounselingLoading(Modifier.fillMaxWidth())
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(LoveBrainShape.lg)
                            .background(SurfaceCard)
                            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
                            .padding(Spacing.xl)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownText(
                            text = streaming,
                            color = TextPrimary,
                            fontSize = MarkdownBodyFontSize,
                            lineHeight = MarkdownBodyLineHeight
                        )
                    }
                }
            }

            error != null -> {
                val err = error ?: return
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LoveBrainShape.md)
                        .background(ErrorBg)
                        .padding(Spacing.lg)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = err, color = Error, style = AppTypography.bodySmall)
                        Spacer(Modifier.height(Spacing.sm))
                        // ：点击重试补按压反馈（复用标准件 0.96 scale + 120ms）
                        val (retryInteraction, retryScale) = rememberPressScale(0.96f, "counselRetryScale")
                        Text(
                            text = "点击重试",
                            color = PrimaryDark,
                            style = AppTypography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .graphicsLayer { scaleX = retryScale; scaleY = retryScale }
                                .clickable(interactionSource = retryInteraction, indication = null, onClick = {
                                    viewModel.generateCounseling(draft.trim())
                                })
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm) // ：热区外扩至 ≥24dp（文字高约 16dp + 垂直内边距）
                        )
                    }
                }
            }

            result != null -> {
                val res = result ?: return
                // 追问历史：保存之前的问答对，让用户看到完整对话脉络
                // 调研依据：NN/G 10 Heuristics #1 Visibility of System Status——用户应能看到之前的对话上下文
                var followUpText by remember { mutableStateOf("") }
                // 谈心多轮历史：从磁盘恢复，实现重启不丢失
                // 调研依据：NN/G 10 Heuristics #1 Visibility of System Status——用户应能看到之前的对话上下文
                var counselingHistory by remember {
                    mutableStateOf(viewModel.loadCounselingHistory())
                }
                // 操作行（继续追问/重新开始）移到卡片顶部文字前方
                var showFollowUp by remember { mutableStateOf(false) }
                // V6: Canvas 箭头替代 Unicode ▾/▴
                val followUpArrowRotation by animateFloatAsState(
                    targetValue = if (showFollowUp) 0f else -90f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "followUpArrow"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(LoveBrainShape.lg)
                        .background(SurfaceCard)
                        .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
                        .padding(Spacing.xl)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ═══ 操作行置顶：继续追问 toggle + 重新开始（移到文字前方）═══
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ：继续追问胶囊补按压反馈（胶囊类 0.92，对齐 RoleChip 先例）
                        val (followUpInteraction, followUpScale) = rememberPressScale(0.92f, "followUpScale")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(LoveBrainShape.sm)
                                .background(PrimaryLight)
                                .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.sm)
                                .graphicsLayer { scaleX = followUpScale; scaleY = followUpScale }
                                .semantics { stateDescription = if (showFollowUp) "已展开" else "已收起" }
                                .clickable(interactionSource = followUpInteraction, indication = null, onClick = { showFollowUp = !showFollowUp })
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        ) {
                            Text(
                                text = "继续追问",
                                style = AppTypography.labelMedium,
                                color = PrimaryDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            // A2-2：共享三角箭头（原 Canvas Path 块与锦囊处逐字相同）
                            TriangleArrow(color = Primary, rotation = followUpArrowRotation)
                        }
                        // ：清空重聊胶囊补按压反馈（胶囊类 0.92，对齐 RoleChip 先例）
                        val (clearInteraction, clearScale) = rememberPressScale(0.92f, "clearScale")
                        Text(
                            text = "清空重聊",
                            color = TextHint,
                            style = AppTypography.labelMedium,
                            modifier = Modifier
                                .clip(LoveBrainShape.sm)
                                .background(SurfaceInset)
                                .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.sm)
                                .graphicsLayer { scaleX = clearScale; scaleY = clearScale }
                                .clickable(interactionSource = clearInteraction, indication = null, onClick = {
                                    counselingHistory = emptyList()
                                    showFollowUp = false
                                    viewModel.clearCounselingAll()
                                })
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        )
                    }
                    // 追问输入区（展开时显示，跟随操作行置顶）
                    AnimatedVisibility(
                        visible = showFollowUp,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = Spacing.md)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(LoveBrainShape.md)
                                        .background(SurfaceInset)
                                        .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                                ) {
                                    if (followUpText.isEmpty()) {
                                        Text(
                                            text = "想继续追问…",
                                            color = TextHint,
                                            style = AppTypography.bodySmall
                                        )
                                    }
                                    BasicTextField(
                                        value = followUpText,
                                        onValueChange = { followUpText = it },
                                        singleLine = false,
                                        maxLines = 3,
                                        textStyle = AppTypography.bodySmall.copy(color = TextPrimary),
                                        cursorBrush = SolidColor(Primary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Spacer(Modifier.width(Spacing.sm))
                                // ：追问动作按钮补按压反馈（复用标准件 0.96 scale + 120ms）
                                val (askInteraction, askScale) = rememberPressScale(0.96f, "askScale")
                                Box(
                                    modifier = Modifier
                                        .clip(LoveBrainShape.md)
                                        .background(
                                            if (followUpText.isNotBlank()) Primary else SurfaceInset,
                                            LoveBrainShape.md
                                        )
                                        .graphicsLayer { scaleX = askScale; scaleY = askScale }
                                        // ：禁用态三件套——空输入时不可点（对齐  先例）
                                        .then(if (followUpText.isNotBlank()) Modifier.clickable(interactionSource = askInteraction, indication = null, onClick = {
                                            if (followUpText.isNotBlank()) {
                                                // 保存当前问答对到历史
                                                counselingHistory = counselingHistory + (draft to res)
                                                // 持久化历史到磁盘（重启不丢失）
                                                viewModel.saveCounselingHistory(counselingHistory)
                                                // 组装带上下文的追问消息
                                                // : 只保留最近 N 轮问答，防 context length 超限
                                                val recentHistory = counselingHistory.takeLast(AppConfig.COUNSELING_MAX_HISTORY_ROUNDS)
                                                val contextMsg = buildString {
                                                    append("【前情提要】\n")
                                                    recentHistory.forEach { (q, a) ->
                                                        append("我问：").append(q.trim()).append("\n")
                                                        append("军师回复：").append(a.trim()).append("\n\n")
                                                    }
                                                    append("我问：").append(draft.trim()).append("\n")
                                                    append("军师回复：").append(res.trim()).append("\n\n")
                                                    append("【追问】").append(followUpText.trim())
                                                }
                                                viewModel.setCounselingDraft(followUpText.trim())
                                                followUpText = ""
                                                showFollowUp = false
                                                viewModel.generateCounseling(contextMsg)
                                            }
                                        }) else Modifier)
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "追问",
                                        color = if (followUpText.isNotBlank()) Color.White else TextSecondary,
                                        style = AppTypography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))
                    HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border)
                    Spacer(Modifier.height(Spacing.md))

                    // 问答历史展示块已删（截断摘要无阅读价值；历史仍作为追问上下文发送给模型）

                    // 当前军师回复
                    MarkdownText(
                        text = res,
                        color = TextPrimary,
                        fontSize = MarkdownBodyFontSize,
                        lineHeight = MarkdownBodyLineHeight
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            // 空状态引导：初始进入谈心模式时，输入框下方为空白，用户不知道会发生什么。
            // 调研依据：NN/G 10 Heuristics「Visibility of System Status」——系统应在合理时间内给用户恰当反馈。
            // 空状态用占位+引导文案告诉用户下一步该做什么，避免「空白焦虑」。
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(LoveBrainShape.lg)
                        .background(SurfaceInset)
                        .padding(Spacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // ✦ 符号 → App 图标（去符号化装饰，统一图标语言）
                        Box(
                            modifier = Modifier
                                .size(AppDimens.EMPTY_ICON_CONTAINER_DP.dp)
                                .clip(LoveBrainShape.full)
                                .background(PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bubble_simple),
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(Spacing.xxxl)
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = "说说你的困惑，军师帮你分析",
                            color = TextSecondary,
                            style = AppTypography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "感情里遇到难题，军师用公正法官的视角帮你理清",
                            color = TextHint,
                            style = AppTypography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/** 谈心模板 chip（需求11：折叠后仍保持统一 chip 样式） */
@Composable
private fun TemplateChip(text: String, onClick: () -> Unit) {
    // #1：模板 chip 补按压反馈（标准件 0.92 scale + 120ms）
    val (interaction, templateChipScale) = rememberPressScale(0.92f, "templateChipScale")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = templateChipScale; scaleY = templateChipScale }
            .clip(LoveBrainShape.sm)
            .background(SurfaceInset, LoveBrainShape.sm)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.sm)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm) // ：垂直内边距 xs→sm，热区 ≈20→24dp
    ) {
        Text(text = text, style = AppTypography.labelSmall, color = TextSecondary)
    }
}

/** 谈心加载动画（需求19）：统一 AiLoadingRow——三点跳动 + 轮换文案（首 token 前展示） */
@Composable
private fun CounselingLoading(modifier: Modifier = Modifier) {
    val phrases = remember {
        listOf(
            "军师正在倾听…",
            "军师正在梳理你的情绪…",
            "军师正在还原事情的全貌…",
            "军师正在权衡公正的裁决…",
            "军师正在为你斟酌词句…"
        )
    }
    AiLoadingRow(
        phrases = phrases,
        modifier = modifier,
        background = SurfaceInset
    )
}
