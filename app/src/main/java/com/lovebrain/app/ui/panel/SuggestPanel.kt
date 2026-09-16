package com.lovebrain.app.ui.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.LoveBrainViewModel

/** 锦囊面板内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object SuggestDimens {
    const val PRIORITY_BADGE_HPAD_DP = 6    // 优先级/时机徽章水平内边距
    const val TITLE_ROW_GAP_DP = 6          // 标题行元素间距
    const val SECTION_GAP_DP = 6            // 卡内区块间距
    const val PROGRESS_HEIGHT_DP = 6        // 阶段进度条高度
    const val EXAMPLE_MAX_HEIGHT_DP = 96    // 话术主体展开最大高度
    const val CROSS_MARK_TOP_PAD_DP = 1     // 避坑 ✗ 顶部对齐内边距
}

/**
 * 今日锦囊面板（v2）。
 *
 * 定位：参考性做法建议（类似日报/锦囊），不写知识库、不影响知识库。
 * 交互：空页面 → 点击"生成锦囊" → AI 生成 → 不满意可"重新生成"。
 * 已删除：刷新按钮、右上角 ×、"当前推荐"卡片、"进度追踪"卡片、时间戳、
 *         本阶段目标独立卡、做法卡全部按钮（/9）。
 * 阶段进度改用五维向量均值（方案 B）。
 */
@Composable
fun SuggestPanel(
    viewModel: LoveBrainViewModel,
    modifier: Modifier = Modifier
) {
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val isSuggesting by viewModel.isSuggesting.collectAsStateWithLifecycle()
    val currentVector by viewModel.currentVector.collectAsStateWithLifecycle()
    val streamingTips by viewModel.streamingTips.collectAsStateWithLifecycle()
    val suggestError by viewModel.suggestError.collectAsStateWithLifecycle()
    val intentConfig by viewModel.intentConfig.collectAsStateWithLifecycle()
    val showIntentEditor by viewModel.showIntentEditor.collectAsStateWithLifecycle()
    val activeKb by viewModel.activeKb.collectAsStateWithLifecycle()

    // 需求#23：锦囊加载文案改为「军师正在 xxx」轮换（AI 应用加载话术风格，参考"深度睡眠舱"AI 生成加载）
    val suggestPhrases = remember {
        listOf(
            "军师正在分析你们的关系…",
            "军师正在寻找今天该聊的话题…",
            "军师正在为你准备今日锦囊…"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.lg)
            .background(SurfaceBase)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 标题栏：标题 + 持续意图 chip + 重新生成（三者共占一行）
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日锦囊",
                        style = AppTypography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    // F07: 持续意图 chip — 紧凑入口，复用标题行空档
                    if (activeKb != null) {
                        Spacer(Modifier.width(Spacing.sm))
                        IntentChip(
                            enabled = intentConfig.enabled,
                            text = intentConfig.text,
                            onClick = { viewModel.openIntentEditor() }
                        )
                    }
                }
                // #13 修复：重新生成按钮放大——从 labelMedium 文字 chip 升级为 labelLarge 按钮
                // 结果态升级为 Primary 实心底（与空态 CTA 同级，换一批是结果态唯一主动作）
                // 生成中点击 = 强行停止（替代原"禁用无反馈"）
                // ：重新生成补按压反馈（复用标准件 0.96 scale + 120ms）
                val (regenInteraction, regenScale) = rememberPressScale(0.96f, "regenScale")
                Text(
                    if (isSuggesting) "生成中·点击停止" else "重新生成",
                    style = AppTypography.labelLarge,
                    color = if (isSuggesting) TextSecondary else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(LoveBrainShape.md)
                        .background(if (isSuggesting) SurfaceInset else Primary)
                        .border(
                            AppDimens.BORDER_WIDTH_DP.dp,
                            if (isSuggesting) Border else Primary,
                            LoveBrainShape.md
                        )
                        .graphicsLayer { scaleX = regenScale; scaleY = regenScale }
                        .clickable(interactionSource = regenInteraction, indication = null, onClick = {
                            if (isSuggesting) viewModel.stopSuggest()
                            else viewModel.generateSuggest()
                        })
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
            }
        }

        // F07: 持续意图编辑弹窗（锦囊面板内编辑，不占回复主页）
        if (showIntentEditor) {
            item {
                IntentEditorDialog(
                    text = intentConfig.text,
                    enabled = intentConfig.enabled,
                    onSave = { text, enabled -> viewModel.saveIntent(text, enabled) },
                    onDismiss = { viewModel.dismissIntentEditor() }
                )
            }
        }

        when {
            // 生成中：已有流式 tips 则逐条渲染，否则转圈
            isSuggesting -> {
                if (streamingTips.isNotEmpty()) {
                    items(streamingTips, key = { streamingTips.indexOf(it) }) { tip ->
                        val priority = when (streamingTips.indexOf(tip)) {
                            0 -> TipPriority.HIGH
                            1 -> TipPriority.MEDIUM
                            else -> TipPriority.LOW
                        }
                        SuggestTipCard(tip = tip, priority = priority)
                    }
                    item {
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
                                Text("军师正在整理其余做法…", color = PrimaryDark, style = AppTypography.bodySmall)
                            }
                        }
                    }
                } else {
                    item {
                        // ：统一 AiLoadingRow（三点跳动 + 轮换文案 + 15s 超时提示）
                        AiLoadingRow(
                            phrases = suggestPhrases,
                            timeoutHintMs = 15_000L
                        )
                    }
                }
            }

            // ：锦囊错误态（无 KB 引导/弱网超时/解析失败）——显示错因 + 重试
            suggestError != null -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(LoveBrainShape.lg)
                            .background(ErrorBg)
                            .padding(Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(suggestError.orEmpty(), color = Error, style = AppTypography.bodySmall)
                            Spacer(Modifier.height(Spacing.md))
                            val (errRetryInteraction, errRetryScale) = rememberPressScale(0.96f, "suggestErrorRetryScale")
                            Text(
                                "点击重试",
                                color = Color.White,
                                style = AppTypography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(LoveBrainShape.md)
                                    .background(Primary)
                                    .graphicsLayer { scaleX = errRetryScale; scaleY = errRetryScale }
                                    .clickable(interactionSource = errRetryInteraction, indication = null, onClick = { viewModel.generateSuggest() })
                                    .padding(horizontal = Spacing.xl, vertical = Spacing.md)
                            )
                        }
                    }
                }
            }

            // 空页面：引导点击生成
            suggestion == null -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 空态图标：锦囊版（素材已预处理：贴边白底转透明+裁边，直接呈现不再裁圆）；
                        // tint=Unspecified 必须：M3 Icon 默认按内容色染色，位图彩图会被整体染黑（全黑根因）
                        Icon(
                            painter = painterResource(R.drawable.ic_empty_jinnang),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(AppDimens.EMPTY_ICON_CONTAINER_DP.dp)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        //  文案（/）：主句删除；副句去"为你"（主人原话）
                        Text(
                            "生成今日专属做法，找话题、推进关系不卡壳",
                            color = TextHint,
                            style = AppTypography.bodySmall
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        // ：生成锦囊补按压反馈（复用标准件 0.96 scale + 120ms）
                        val (genInteraction, genScale) = rememberPressScale(0.96f, "genScale")
                        Text(
                            "生成锦囊",
                            color = SurfaceCard,
                            style = AppTypography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(LoveBrainShape.md)   // 按钮族圆角统一 md
                                .background(Primary)
                                .graphicsLayer { scaleX = genScale; scaleY = genScale }
                                .clickable(interactionSource = genInteraction, indication = null, onClick = { viewModel.generateSuggest() })
                                .padding(horizontal = Spacing.xl, vertical = Spacing.md)
                        )
                    }
                }
            }

            // 展示锦囊
            else -> {
                val plan = suggestion ?: return@LazyColumn
                // ：去掉生成时间戳与《本阶段目标》独立卡；goal 已并入阶段卡（关系温度下方）
                item { SuggestStageCard(plan, vectorMean = vectorMean(currentVector)) }

                items(plan.tips, key = { plan.tips.indexOf(it) }) { tip ->
                    val priority = when (plan.tips.indexOf(tip)) {
                        0 -> TipPriority.HIGH
                        1 -> TipPriority.MEDIUM
                        else -> TipPriority.LOW
                    }
                    SuggestTipCard(tip = tip, priority = priority)
                }

                plan.invite?.let { invite ->
                    if (invite.suggestion.isNotBlank()) {
                        item {
                            InviteSuggestionCard(
                                signal = invite.signal,
                                suggestion = invite.suggestion
                            )
                        }
                    }
                }

                if (plan.avoid.isNotEmpty()) {
                    item {
                        // 避坑区视觉增强（调研：NN/G Error Message Guidelines——使用醒目的冗余指示器；
                        // NN/G Proximity——相关元素分组聚合，用留白与上方 tips 区分）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "该阶段避坑",
                                style = AppTypography.labelLarge,
                                color = Error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(LoveBrainShape.md)
                                .background(ErrorBg)
                                .border(AppDimens.BORDER_WIDTH_DP.dp, Error, LoveBrainShape.md)
                                .padding(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            plan.avoid.forEach { avoidText ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        "✗",
                                        style = AppTypography.labelMedium,
                                        color = Error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = Spacing.sm, top = SuggestDimens.CROSS_MARK_TOP_PAD_DP.dp)
                                    )
                                    Text(
                                        avoidText,
                                        color = TextSecondary,
                                        style = AppTypography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 五维向量均值（0-100）→ 阶段进度百分比 */
private fun vectorMean(v: Map<String, Int>): Float {
    if (v.isEmpty()) return 0f
    return v.values.average().toFloat() / 100f
}

/** 阶段卡片：阶段名 + 五维均值进度 + 本阶段目标（：goal 由独立卡移入此处，关系温度下方） */
@Composable
private fun SuggestStageCard(plan: DailySuggestion, vectorMean: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.lg)
            .background(PrimaryLight)
            .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.lg)
            .padding(Spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("当前阶段", style = AppTypography.labelSmall, color = TextSecondary)
            Spacer(Modifier.width(Spacing.md))
            Text(
                plan.stage,
                style = AppTypography.labelLarge,
                color = PrimaryDark,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(Spacing.md))
        // 阶段进度 = 五维向量均值（方案 B）
        LinearProgressIndicator(
            progress = { vectorMean.coerceIn(0f, 1f) },
            color = Primary,
            trackColor = PrimarySubtle,
            modifier = Modifier
                .fillMaxWidth()
                .height(SuggestDimens.PROGRESS_HEIGHT_DP.dp)
                .clip(LoveBrainShape.full)
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "关系温度 ${(vectorMean * 100).toInt()}%",
            style = AppTypography.labelSmall,
            color = TextHint
        )
        // ：本阶段目标并入此卡（关系温度下方），替代原独立灰色卡
        if (plan.goal.isNotBlank()) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                "本阶段目标",
                style = AppTypography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                plan.goal,
                style = AppTypography.bodySmall,
                color = TextPrimary
                // ：lineHeight = 18.sp 与 bodySmall 固有行高（Type.kt:39）重复，已删
            )
        }
    }
}

/** 单条推荐做法卡片（紧凑化：解决『tip.topic 超长撑高 Card 留白 90%』实测问题）
 *  关键修复：右侧 tip.topic 强制 maxLines=1+Ellipsis，避免 AI 把长话术塞进 topic 字段导致整张 Card 高度暴涨。
 */
@Composable
private fun SuggestTipCard(tip: SuggestTip, priority: TipPriority = TipPriority.MEDIUM) {
    // 可折叠：高优先级默认展开，中/低优先级默认折叠
    var expanded by rememberSaveable { mutableStateOf(priority == TipPriority.HIGH) }
    val tipArrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "tipArrow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(SurfaceCard)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
            .padding(Spacing.md) // 紧凑 12→8dp，减小上下空白
    ) {
        // 标题行：左 [优先级] [slot，1 行截断] ｜ 右 [topic，1 行截断 + 折叠箭头]
        Row(
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "已展开" else "已收起" }.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：[优先级标签][slot]
            Box(
                modifier = Modifier
                    .clip(LoveBrainShape.sm)
                    .background(priority.bgColor)
                    .padding(horizontal = SuggestDimens.PRIORITY_BADGE_HPAD_DP.dp, vertical = Spacing.xs)
            ) {
                Text(
                    text = priority.label,
                    style = AppTypography.labelSmall,
                    color = priority.textColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(SuggestDimens.TITLE_ROW_GAP_DP.dp))
            // slot 强制单行截断（防 AI 把长话术塞 slot 导致标题行换行撑高）
            Text(
                text = tip.slot,
                style = AppTypography.labelMedium,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(SuggestDimens.TITLE_ROW_GAP_DP.dp))
            // topic 也强制单行截断（核心修复：防止 250dp 大空白根因）
            Text(
                text = tip.topic,
                style = AppTypography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.xs))
            // A2-2：共享三角箭头（原 Canvas Path 块与谈心处逐字相同）
            TriangleArrow(color = TextHint, rotation = tipArrowRotation)
        }
        Spacer(Modifier.height(SuggestDimens.SECTION_GAP_DP.dp))
        // 话术主体：折叠时 1 行截断、展开时完整显示；过长时卡内可上下滑动（实测问题：长话术被面板裁掉）
        Text(
            text = "\u201C${tip.example}\u201D",
            style = AppTypography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
            modifier = if (expanded) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = SuggestDimens.EXAMPLE_MAX_HEIGHT_DP.dp)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }
        )

        // 可折叠详情区：仅"她可能：预期"；展开时才渲染
        AnimatedVisibility(
            visible = expanded && tip.expected.isNotBlank(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(SuggestDimens.SECTION_GAP_DP.dp))
                Text(
                    text = "她可能：${tip.expected}",
                    style = AppTypography.labelSmall,
                    color = Success
                )
            }
        }
    }
}

/** 优先级枚举：中性化——只留"高优先"黄色警示，中/低统一中性灰，
 *  让避坑（红）和邀约（绿）成为页面上唯二的彩色信号 */
private enum class TipPriority(
    val label: String,
    val textColor: androidx.compose.ui.graphics.Color,
    val bgColor: androidx.compose.ui.graphics.Color
) {
    HIGH("高优先", Warning, WarningBg),
    MEDIUM("中优先", TextSecondary, SurfaceInset),
    LOW("低优先", TextSecondary, SurfaceInset)
}

/** 邀约窗口卡片（：简约化；：去复制按钮，纯展示） */
@Composable
private fun InviteSuggestionCard(signal: String, suggestion: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.lg)
            .background(SurfaceCard)
            .border(AppDimens.BORDER_WIDTH_DP.dp, SuccessBorder, LoveBrainShape.lg)
            .padding(Spacing.lg)
    ) {
        // 标题行 + 时机成熟徽章
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("邀约窗口", style = AppTypography.labelLarge, color = Success, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .clip(LoveBrainShape.sm)
                    .background(Success, LoveBrainShape.sm)
                    .padding(horizontal = SuggestDimens.PRIORITY_BADGE_HPAD_DP.dp, vertical = Spacing.xs)
            ) {
                Text("✓ 时机成熟", style = AppTypography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        // 信号依据（删除硬编码假数据"信号检测 5/5"，只展示真实依据）
        if (signal.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "依据：$signal",
                style = AppTypography.labelSmall,
                color = TextHint
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(suggestion, style = AppTypography.bodySmall, color = TextPrimary)
    }
}

// ═══════════ F07: 持续意图 UI 组件（锦囊面板内紧凑入口） ═══════════

/** F07: 持续意图 chip — 紧凑入口，复用标题行空档。
 *  enabled=true 时高亮显示，点击打开编辑弹窗。 */
@Composable
private fun IntentChip(
    enabled: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.92f, "intentChipScale")
    val label = if (enabled) {
        if (text.isNotBlank()) "意图·${text.take(8)}${if (text.length > 8) "…" else ""}"
        else "意图·未设"
    } else {
        "意图·关"
    }
    Box(
        modifier = Modifier
            .height(22.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.full)
            .background(if (enabled) PrimaryLight else SurfaceInset, LoveBrainShape.full)
            .border(
                AppDimens.BORDER_WIDTH_DP.dp,
                if (enabled) PrimarySubtle else Border,
                LoveBrainShape.full
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AppTypography.labelSmall,
            color = if (enabled) PrimaryDark else TextHint,
            maxLines = 1
        )
    }
}

/** F07: 持续意图编辑弹窗 — 输入意图文本 + 开关 + 保存/取消。
 *  长度限制可见校验（超过 200 字提示截断），不静默截断。 */
private const val INTENT_MAX_LENGTH = 200

@Composable
private fun IntentEditorDialog(
    text: String,
    enabled: Boolean,
    onSave: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var editText by remember { mutableStateOf(text) }
    var editEnabled by remember { mutableStateOf(enabled) }
    val overLimit = editText.length > INTENT_MAX_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        title = {
            Text("持续意图", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "设置一个持续的对话目标（如\"约她周末看电影\"），军师每轮生成时都会参考。",
                    style = AppTypography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Spacing.md))
                // 开关行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用", style = AppTypography.labelLarge, color = TextPrimary)
                    val (toggleInteraction, toggleScale) = rememberPressScale(0.92f, "intentToggleScale")
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(24.dp)
                            .graphicsLayer { scaleX = toggleScale; scaleY = toggleScale }
                            .clip(LoveBrainShape.full)
                            .background(if (editEnabled) Primary else SurfaceInset, LoveBrainShape.full)
                            .border(AppDimens.BORDER_WIDTH_DP.dp, if (editEnabled) Primary else Border, LoveBrainShape.full)
                            .clickable(interactionSource = toggleInteraction, indication = null) { editEnabled = !editEnabled },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            Modifier
                                .offset(x = if (editEnabled) 20.dp else 2.dp)
                                .size(20.dp)
                                .clip(LoveBrainShape.full)
                                .background(Color.White)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                // 文本输入框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .background(SurfaceCard, LoveBrainShape.md)
                        .border(
                            AppDimens.BORDER_WIDTH_DP.dp,
                            if (overLimit) Error else PrimarySubtle,
                            LoveBrainShape.md
                        )
                        .padding(Spacing.md)
                ) {
                    if (editText.isEmpty()) {
                        Text(
                            "输入你的持续意图…",
                            color = TextHint,
                            style = AppTypography.bodyMedium
                        )
                    }
                    BasicTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 字数 + 超限提示
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${editText.length}/$INTENT_MAX_LENGTH",
                        style = AppTypography.labelSmall,
                        color = if (overLimit) Error else TextHint
                    )
                }
                if (overLimit) {
                    Text(
                        "意图过长，请精简到 $INTENT_MAX_LENGTH 字以内",
                        style = AppTypography.labelSmall,
                        color = Error,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
        },
        confirmButton = {
            val (saveInteraction, saveScale) = rememberPressScale(0.96f, "intentSaveScale")
            Text(
                "保存",
                style = AppTypography.labelLarge,
                color = if (overLimit) TextHint else Primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .graphicsLayer { scaleX = saveScale; scaleY = saveScale }
                    .clickable(interactionSource = saveInteraction, indication = null) {
                        if (!overLimit) {
                            // R08修复：保存后由 ViewModel 在成功时关闭编辑器，
                            // 失败时保留编辑状态供重试，不立即 onDismiss
                            onSave(editText.trim().take(INTENT_MAX_LENGTH), editEnabled)
                        }
                    }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )
        },
        dismissButton = {
            val (cancelInteraction, cancelScale) = rememberPressScale(0.96f, "intentCancelScale")
            Text(
                "取消",
                style = AppTypography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .graphicsLayer { scaleX = cancelScale; scaleY = cancelScale }
                    .clickable(interactionSource = cancelInteraction, indication = null, onClick = onDismiss)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )
        }
    )
}
