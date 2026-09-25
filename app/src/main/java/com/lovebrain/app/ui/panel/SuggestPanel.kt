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
// AlertDialog 已替换为 PanelModalHost，避免 Service 宿主 BadTokenException
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.model.DailyBriefUsage
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.LoveBrainViewModel

/** 锦囊面板内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object SuggestDimens {
    const val PRIORITY_BADGE_HPAD_DP = 6    // 优先级/时机徽章水平内边距
    const val SECTION_GAP_DP = 6            // 卡内区块间距
    const val PROGRESS_HEIGHT_DP = 6        // 阶段进度条高度
    const val EXAMPLE_MAX_HEIGHT_DP = 96    // 话术主体展开最大高度
    const val CROSS_MARK_TOP_PAD_DP = 1     // 避坑 ✗ 顶部对齐内边距

    /** 卡片折叠入口的最小可点击边界——§6.5 的下限是 48×48dp */
    const val FOLD_MIN_HEIGHT_DP = 48
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

    // 锦囊加载文案改为「军师正在 xxx」轮换（AI 应用加载话术风格，参考"深度睡眠舱"AI 生成加载）
    val suggestPhrases = remember {
        listOf(
            "军师正在分析你们的关系…",
            "军师正在寻找今天该聊的话题…",
            "军师正在为你准备今日锦囊…"
        )
    }

    // 弹层放面板根部，不作为 LazyColumn 的某一 item，以免滚动使编辑器离开 composition
    Box(modifier = modifier.fillMaxWidth()) {
    LazyColumn(
        modifier = Modifier
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
                    // 持续意图 chip — 紧凑入口，复用标题行空档
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
                // 重新生成补按压反馈（复用标准件 0.96 scale + 120ms）
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
                            else viewModel.regenerateSuggestion()
                        })
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
            }
        }

        when {
            // 生成中：已有流式 tips 则逐条渲染，否则转圈
            isSuggesting -> {
                if (streamingTips.isNotEmpty()) {
                    items(streamingTips, key = { it.id }) { tip ->
                        SuggestTipCard(tip = tip)
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
                        // 统一 AiLoadingRow（三点跳动 + 轮换文案 + 15s 超时提示）
                        AiLoadingRow(
                            phrases = suggestPhrases,
                            timeoutHintMs = 15_000L
                        )
                    }
                }
            }

            // 锦囊错误态（无 KB 引导/弱网超时/解析失败）——显示错因 + 重试
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
                        // 空态文案
                        Text(
                            "生成今日专属做法，找话题、推进关系不卡壳",
                            color = TextHint,
                            style = AppTypography.bodySmall
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        // 生成锦囊补按压反馈（复用标准件 0.96 scale + 120ms）
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

            // 展示锦囊——轻量日常行动建议
            else -> {
                val plan = suggestion ?: return@LazyColumn
                // 顶部显示 usage 与 partial 标识
                item {
                    SuggestUsageBar(usage = plan.usage, isPartial = plan.partial)
                }
                // 阶段卡保留（阶段名 + 关系温度），但不再强制 goal
                item { SuggestStageCard(plan, vectorMean = vectorMean(currentVector)) }

                // 按 timingCategory 分组
                val groupedTips = groupTipsByCategory(plan.tips)
                for ((category, tips) in groupedTips) {
                    if (tips.isEmpty()) continue
                    item(key = "category-$category") {
                        TipCategoryHeader(category = category)
                    }
                    items(tips, key = { it.id }) { tip ->
                        SuggestTipCard(tip = tip)
                    }
                }

                // invite 保留但为可选（suggest.md 不再强制输出）
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

                // avoid 保留但为可选（suggest.md 不再默认长篇避雷）
                if (plan.avoid.isNotEmpty()) {
                    item {
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

    // 意图编辑弹窗——面板根部渲染，不在 LazyColumn 内部
    if (showIntentEditor) {
        IntentEditorDialog(
            text = intentConfig.text,
            enabled = intentConfig.enabled,
            expiry = intentConfig.expiry,
            expiryDate = intentConfig.expiryDate,
            status = intentConfig.status,
            onSave = { text, enabled, expiry, expiryDate, status ->
                viewModel.saveIntent(text, enabled, expiry, expiryDate, status)
            },
            onDismiss = { viewModel.dismissIntentEditor() }
        )
    }
    } // close Box
}

/** 五维向量均值（0-100）→ 阶段进度百分比 */
private fun vectorMean(v: Map<String, Int>): Float {
    if (v.isEmpty()) return 0f
    return v.values.average().toFloat() / 100f
}

/** 阶段卡片——阶段名 + 五维均值进度 + 本阶段目标（可选） */
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
        // stage 可能为空（suggest.md 不再强制输出 stage）
        if (plan.stage.isNotBlank()) {
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
        }
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
        // goal 为可选字段
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
            )
        }
    }
}

/** 单条日常行动建议卡片
 *
 * 展示新语义字段：action（做什么）+ timing（什么时候适合）。
 * 点击展开显示：materialNeeded（需要素材）+ example（示例配文）+ reason（理由）。
 * 不再展示旧字段 slot/topic/expected；伪确定预测已移除。
 */
@Composable
internal fun SuggestTipCard(tip: SuggestTip) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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
            .padding(Spacing.md)
    ) {
        // 标题行：优先级由分组 header 体现。
        // 改之前语义树实测这颗折叠入口是 344x17dp（最窄 + 2.0 倍字时 304x33dp）——
        // 也就是它只有标题文字那么高，手指要正中那 17dp 才算点得到，故垫到 ≥48dp。
        // stateDescription 是读屏唯一听得见"现在收起/展开"的地方：原来写成内联中文，
        // 英文环境下照样念中文，而且文案预算那把尺当时根本看不见 stateDescription。
        // 公告文案必须在 semantics 之外解析——那个 lambda 不是 composable 上下文。
        val foldAnnouncement = stringResource(
            if (expanded) R.string.state_expanded else R.string.state_collapsed
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SuggestDimens.FOLD_MIN_HEIGHT_DP.dp)
                .semantics { stateDescription = foldAnnouncement }
                .clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // UI 只消费新模型字段
            Text(
                text = tip.action,
                style = AppTypography.labelMedium,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.xs))
            TriangleArrow(color = TextHint, rotation = tipArrowRotation)
        }

        // timing（什么时候适合）——折叠态也显示，帮助用户判断
        if (tip.timing.isNotBlank()) {
            Spacer(Modifier.height(SuggestDimens.SECTION_GAP_DP.dp))
            Text(
                text = "适合：${tip.timing}",
                style = AppTypography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 可折叠详情区：materialNeeded + example + reason
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                // materialNeeded（需要的素材或前提）
                if (tip.materialNeeded.isNotBlank()) {
                    Spacer(Modifier.height(SuggestDimens.SECTION_GAP_DP.dp))
                    Text(
                        text = "需要：${tip.materialNeeded}",
                        style = AppTypography.labelSmall,
                        color = Warning,
                        fontWeight = FontWeight.Medium
                    )
                }
                // example（示例配文）
                if (tip.example.isNotBlank()) {
                    Spacer(Modifier.height(SuggestDimens.SECTION_GAP_DP.dp))
                    Text(
                        text = "\u201C${tip.example}\u201D",
                        style = AppTypography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = SuggestDimens.EXAMPLE_MAX_HEIGHT_DP.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                // reason（为什么建议这个）
                if (tip.reason.isNotBlank()) {
                    Spacer(Modifier.height(SuggestDimens.SECTION_GAP_DP.dp))
                    Text(
                        text = tip.reason,
                        style = AppTypography.labelSmall,
                        color = TextHint
                    )
                }
            }
        }
    }
}

/** 按 timingCategory 分组 tips。
 * 顺序："现在可用" → "今天可准备" → "有机会再做" → 其他（无分类的排末尾）。 */
private fun groupTipsByCategory(tips: List<SuggestTip>): List<Pair<String, List<SuggestTip>>> {
    val order = listOf("现在可用", "今天可准备", "有机会再做")
    val grouped = tips.groupBy { it.timingCategory }
    val result = mutableListOf<Pair<String, List<SuggestTip>>>()
    for (cat in order) {
        (grouped[cat] ?: emptyList()).takeIf { it.isNotEmpty() }?.let {
            result.add(cat to it)
        }
    }
    // 未分类的 tips
    val uncategorized = tips.filter { it.timingCategory.isBlank() || it.timingCategory !in order }
    if (uncategorized.isNotEmpty()) {
        result.add("更多建议" to uncategorized)
    }
    return result
}

/** 分类标题 */
@Composable
private fun TipCategoryHeader(category: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category,
            style = AppTypography.labelLarge,
            color = PrimaryDark,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 锦囊 usage 与 partial 状态展示。
 *  Provider 返回 usage 时显示 token/费用/耗时；无值显示"未知"。
 *  partial=true 时显示不完整标识。 */
@Composable
private fun SuggestUsageBar(usage: DailyBriefUsage?, isPartial: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(if (isPartial) WarningBg else SurfaceInset, LoveBrainShape.md)
            .border(
                AppDimens.BORDER_WIDTH_DP.dp,
                if (isPartial) Warning else Border,
                LoveBrainShape.md
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：token 信息
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isPartial) {
                Text("不完整", style = AppTypography.labelSmall, color = Warning, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(Spacing.sm))
            }
            val tokens = usage?.let {
                listOfNotNull(
                    it.promptTokens?.let { p -> "↑$p" },
                    it.completionTokens?.let { c -> "↓$c" }
                ).joinToString("  ")
            } ?: ""
            if (tokens.isNotBlank()) {
                Text(tokens, style = AppTypography.labelSmall, color = TextSecondary)
            } else {
                Text("token 未知", style = AppTypography.labelSmall, color = TextHint)
            }
        }
        // 右侧：费用 + 耗时
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cost = usage?.costYuan
            if (cost != null) {
                Text("≈${"%.4f".format(cost)}元", style = AppTypography.labelSmall, color = TextSecondary)
            } else {
                Text("费用未知", style = AppTypography.labelSmall, color = TextHint)
            }
            Spacer(Modifier.width(Spacing.md))
            usage?.elapsedMs?.let { ms ->
                Text("${ms / 1000}s", style = AppTypography.labelSmall, color = TextHint)
            }
        }
    }
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

// ═══════════ 持续意图 UI 组件（锦囊面板内紧凑入口） ═══════════

/** 持续意图 chip — 紧凑入口，复用标题行空档。
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

/** 持续意图编辑弹窗 — 输入意图文本 + 开关 + 保存/取消。
 *  长度限制可见校验（超过 200 字提示截断），不静默截断。 */
private const val INTENT_MAX_LENGTH = 200

@Composable
private fun IntentEditorDialog(
    text: String,
    enabled: Boolean,
    expiry: com.lovebrain.app.model.IntentExpiry = com.lovebrain.app.model.IntentExpiry.UNTIL_DONE,
    expiryDate: String = "",
    status: com.lovebrain.app.model.IntentStatus = com.lovebrain.app.model.IntentStatus.ACTIVE,
    onSave: (String, Boolean, com.lovebrain.app.model.IntentExpiry, String, com.lovebrain.app.model.IntentStatus) -> Unit,
    onDismiss: () -> Unit
) {
    var editText by remember { mutableStateOf(text) }
    var editEnabled by remember { mutableStateOf(enabled) }
    var editExpiry by remember { mutableStateOf(expiry) }
    var editExpiryDate by remember { mutableStateOf(expiryDate) }
    var editStatus by remember { mutableStateOf(status) }
    val overLimit = editText.length > INTENT_MAX_LENGTH

    // 使用 PanelModalHost 替代 AlertDialog——Service 宿主中安全
    PanelModalHost(
        onDismiss = onDismiss
    ) {
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
                // 有效期选择
                Text("有效期", style = AppTypography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    IntentExpiryChip("直到完成", editExpiry == com.lovebrain.app.model.IntentExpiry.UNTIL_DONE) {
                        editExpiry = com.lovebrain.app.model.IntentExpiry.UNTIL_DONE
                    }
                    IntentExpiryChip("仅今天", editExpiry == com.lovebrain.app.model.IntentExpiry.TODAY) {
                        editExpiry = com.lovebrain.app.model.IntentExpiry.TODAY
                    }
                    IntentExpiryChip("指定日期", editExpiry == com.lovebrain.app.model.IntentExpiry.DATE) {
                        editExpiry = com.lovebrain.app.model.IntentExpiry.DATE
                    }
                }
                // 指定日期时显示日期输入框
                if (editExpiry == com.lovebrain.app.model.IntentExpiry.DATE) {
                    Spacer(Modifier.height(Spacing.xs))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, LoveBrainShape.md)
                            .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.md)
                            .padding(Spacing.md)
                    ) {
                        if (editExpiryDate.isEmpty()) {
                            Text("输入日期（如 2026-12-31）", color = TextHint, style = AppTypography.bodyMedium)
                        }
                        BasicTextField(
                            value = editExpiryDate,
                            onValueChange = { editExpiryDate = it },
                            textStyle = AppTypography.bodyMedium.copy(color = TextPrimary),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // 状态操作——已完成时可标记完成
                if (editEnabled && editStatus == com.lovebrain.app.model.IntentStatus.ACTIVE) {
                    Spacer(Modifier.height(Spacing.sm))
                    val (completeInteraction, completeScale) = rememberPressScale(0.96f, "intentCompleteScale")
                    Text(
                        "标记为已完成",
                        style = AppTypography.labelSmall,
                        color = TextHint,
                        modifier = Modifier
                            .graphicsLayer { scaleX = completeScale; scaleY = completeScale }
                            .clickable(interactionSource = completeInteraction, indication = null) {
                                editStatus = com.lovebrain.app.model.IntentStatus.COMPLETED
                                editEnabled = false
                            }
                            .padding(vertical = Spacing.xs)
                    )
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
            // 统一操作行——PanelModalActions
            Spacer(Modifier.height(Spacing.md))
            PanelModalActions(
                confirmLabel = "保存",
                confirmEnabled = !overLimit,
                onConfirm = {
                    if (!overLimit) {
                        onSave(editText.trim().take(INTENT_MAX_LENGTH), editEnabled, editExpiry, editExpiryDate.trim(), editStatus)
                    }
                },
                onDismiss = onDismiss
            )
        }
    }

/**
 * 有效期选择 chip。
 */
@Composable
private fun IntentExpiryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.94f, "expiryChip_$label")
    Text(
        text = if (isSelected) "✓ $label" else label,
        style = AppTypography.labelSmall,
        color = if (isSelected) Color.White else TextSecondary,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .background(if (isSelected) Primary else SurfaceCard, LoveBrainShape.sm)
            .border(AppDimens.BORDER_WIDTH_DP.dp, if (isSelected) Primary else Border, LoveBrainShape.sm)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    )
}
