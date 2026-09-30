package com.lovebrain.app.ui.panel.suggest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.model.DailyBriefUsage
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.ui.panel.TriangleArrow
import com.lovebrain.app.ui.theme.*

/**
 * 今日锦囊**结果态的内容块**——一次生成成功之后那一屏要摆的七样东西：
 * usage/不完整条、阶段卡（含关系温度那条进度）、按时机分好组的建议卡、分组表头、
 * 邀约窗、避坑标题与避坑清单，外加它们背后那两处纯算法（[vectorMean]、[groupTipsByCategory]）。
 *
 * 这一块为什么是**一块**（不是照着行数切的一段）：
 * - **做什么**：把一条 `DailySuggestion` 画成结果态的那几格内容，以及「按 timingCategory
 *   排成 现在可用 → 今天可准备 → 有机会再做 → 更多建议」这一条顺序。
 * - **谁触发**：`ui/panel/SuggestPanel.kt` 的 `when` 走到「有建议」那一档之后逐格调用；
 *   面板自己只留下取流（VM 那几条 StateFlow）、状态分档、标题行与那颗重新生成。
 * - **状态归谁**：跨出去的只有数据（plan / tips / usage / avoid / vector）；唯一的本地状态
 *   是每张建议卡自己的展开开关（`rememberSaveable`），它本来就没出过那张卡。
 *   面板与 `LoveBrainViewModel` 都不持有这一块的任何一格。
 * - **今天有格子吗**：只有建议卡有（`ui/panel/SuggestTipCardSemanticsTest`）。
 *   usage 条、阶段卡、分组表头、邀约窗、避坑清单、以及那条分组顺序**一颗格子都没有**，
 *   本包补的 `SuggestResultContentSemanticsTest` 与 `SuggestResultLogicTest` 就是补这一段。
 *
 * ⚠ 搬家不是重写：函数体逐字照抄，只把被面板直接调用的那几颗从 `private` 抬到 `internal`
 * （同一条先例：`SuggestTipCard` 当初为了能被量到就是这么抬的）。避坑那一块原来是写在面板
 * `LazyColumn` 里的**两格 item**，搬完仍是两格——合成一格会少算一档 `spacedBy(Spacing.md)`，
 * 那是改版式不是搬代码。拆法与 KDoc 口径照抄 `ui/panel/reply/ResultArea.kt`。
 */
/** 锦囊结果内容那一族的内部尺寸常量（令牌化：数值不变，仅外放命名）——跟着那块内容从 `ui/panel/SuggestPanel.kt` 一起搬进来，值一个没动 */
private object SuggestDimens {
    const val PRIORITY_BADGE_HPAD_DP = 6    // 优先级/时机徽章水平内边距
    const val SECTION_GAP_DP = 6            // 卡内区块间距
    const val PROGRESS_HEIGHT_DP = 6        // 阶段进度条高度
    const val EXAMPLE_MAX_HEIGHT_DP = 96    // 话术主体展开最大高度
    const val CROSS_MARK_TOP_PAD_DP = 1     // 避坑 ✗ 顶部对齐内边距

    /** 卡片折叠入口的最小可点击边界——数取自全局那颗下限，这里只留"这是折叠入口"这个名字 */
    const val FOLD_MIN_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP
}

/** 五维向量均值（0-100）→ 阶段进度百分比 */
internal fun vectorMean(v: Map<String, Int>): Float {
    if (v.isEmpty()) return 0f
    return v.values.average().toFloat() / 100f
}

/** 阶段卡片——阶段名 + 五维均值进度 + 本阶段目标（可选） */
@Composable
internal fun SuggestStageCard(plan: DailySuggestion, vectorMean: Float) {
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
internal fun groupTipsByCategory(tips: List<SuggestTip>): List<Pair<String, List<SuggestTip>>> {
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
internal fun TipCategoryHeader(category: String) {
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
internal fun SuggestUsageBar(usage: DailyBriefUsage?, isPartial: Boolean) {
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
internal fun InviteSuggestionCard(signal: String, suggestion: String) {
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

/**
 * 「该阶段避坑」那一行标题——原先是面板 `LazyColumn` 里独立的一格 item，搬完仍是独立的一格。
 */
@Composable
internal fun SuggestAvoidHeader() {
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

/** 避坑清单本体：✗ + 一句话，逐条摆；同样占独立的一格 item。 */
@Composable
internal fun SuggestAvoidList(avoid: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(ErrorBg)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Error, LoveBrainShape.md)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        avoid.forEach { avoidText ->
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
