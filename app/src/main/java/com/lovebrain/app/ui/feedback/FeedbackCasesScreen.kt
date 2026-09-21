package com.lovebrain.app.ui.feedback

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.AppTypography
import com.lovebrain.app.ui.theme.Border
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Primary
import com.lovebrain.app.ui.theme.PrimaryDark
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.SurfaceBase
import com.lovebrain.app.ui.theme.SurfaceCard
import com.lovebrain.app.ui.theme.SurfaceInset
import com.lovebrain.app.ui.theme.TextHint
import com.lovebrain.app.ui.theme.TextPrimary
import com.lovebrain.app.ui.theme.TextSecondary
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.launch

/**
 * 反馈案例独立页面——根级导航目标，不在首页 Column 内插入。
 *
 * 数据通过 ViewModel/DI 提供，不在 Composable 中直接 new Repository。
 * 页面状态：Loading / Empty / Data / Error
 */
@Composable
fun FeedbackCasesScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val cases by viewModel.feedbackCases.collectAsStateWithLifecycle()
    val isLoading by viewModel.feedbackLoading.collectAsStateWithLifecycle()
    val loadError by viewModel.feedbackError.collectAsStateWithLifecycle()

    var filterCategory by remember { mutableStateOf<FeedbackCategory?>(null) }
    var expandedCaseId by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf("") }
    var showExport by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("markdown") }

    LaunchedEffect(Unit) {
        viewModel.loadFeedbackCases()
    }

    val filtered = if (filterCategory == null) cases else cases.filter { filterCategory!! in it.categories }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (backInteraction, backScale) = rememberPressScale(0.94f, "backBtn")
                    Text(
                        "←",
                        style = AppTypography.titleMedium,
                        color = Primary,
                        modifier = Modifier
                            .graphicsLayer { scaleX = backScale; scaleY = backScale }
                            .clip(LoveBrainShape.md)
                            .clickable(
                                interactionSource = backInteraction,
                                indication = null,
                                onClick = onBack
                            )
                            .padding(end = Spacing.md)
                    )
                    Text(
                        "反馈案例",
                        style = AppTypography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "(${filtered.size}条)",
                        style = AppTypography.labelMedium,
                        color = TextHint
                    )
                }
                // 导出按钮
                val (exportInteraction, exportScale) = rememberPressScale(0.96f, "exportBtn")
                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = exportScale; scaleY = exportScale }
                        .clip(LoveBrainShape.md)
                        .background(if (filtered.isNotEmpty()) Primary else SurfaceInset, LoveBrainShape.md)
                        .clickable(
                            interactionSource = exportInteraction,
                            indication = null,
                            enabled = filtered.isNotEmpty()
                        ) {
                            scope.launch {
                                exportText = if (exportFormat == "markdown") {
                                    viewModel.exportFeedbackMarkdown(filtered)
                                } else {
                                    viewModel.exportFeedbackJson(filtered)
                                }
                                showExport = true
                            }
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (exportFormat == "markdown") "导出 MD" else "导出 JSON",
                        style = AppTypography.labelMedium,
                        color = if (filtered.isNotEmpty()) Color.White else TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 筛选芯片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilterChip("全部", filterCategory == null) { filterCategory = null }
                FeedbackCategory.entries.forEach { cat ->
                    FilterChip(
                        categoryDisplayName(cat),
                        filterCategory == cat
                    ) { filterCategory = cat }
                }
                Spacer(Modifier.width(Spacing.md))
                FilterChip(
                    if (exportFormat == "markdown") "✓ Markdown" else "Markdown",
                    exportFormat == "markdown"
                ) { exportFormat = "markdown" }
                FilterChip(
                    if (exportFormat == "json") "✓ JSON" else "JSON",
                    exportFormat == "json"
                ) { exportFormat = "json" }
            }

            // 内容区
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                loadError != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                loadError!!,
                                style = AppTypography.bodyMedium,
                                color = TextHint
                            )
                            Spacer(Modifier.padding(top = Spacing.md))
                            val (retryInteraction, retryScale) = rememberPressScale(0.96f, "retryLoad")
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { scaleX = retryScale; scaleY = retryScale }
                                    .clip(LoveBrainShape.md)
                                    .background(Primary, LoveBrainShape.md)
                                    .clickable(
                                        interactionSource = retryInteraction,
                                        indication = null,
                                        onClick = {
                                            scope.launch { viewModel.loadFeedbackCases() }
                                        }
                                    )
                                    .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("重试", color = Color.White, style = AppTypography.labelLarge)
                            }
                        }
                    }
                }
                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无反馈案例。点踩后会自动记录。",
                            style = AppTypography.bodyMedium,
                            color = TextHint
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(filtered) { c ->
                            val isExpanded = expandedCaseId == c.caseId
                            Card(
                                shape = LoveBrainShape.md,
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCaseId = if (isExpanded) null else c.caseId
                                    }
                            ) {
                                Column(modifier = Modifier.padding(Spacing.md)) {
                                    Text(
                                        "【${c.categories.joinToString(", ") { categoryDisplayName(it) }}】 ${c.reasons.joinToString(", ")}",
                                        style = AppTypography.labelMedium,
                                        color = PrimaryDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(
                                        if (isExpanded) c.candidateReply else "${c.candidateReply.take(80)}${if (c.candidateReply.length > 80) "..." else ""}",
                                        style = AppTypography.bodySmall,
                                        color = TextSecondary
                                    )
                                    if (c.userNote.isNotBlank()) {
                                        Text("补充：${c.userNote}", style = AppTypography.labelSmall, color = TextHint)
                                    }
                                    Text(
                                        "${c.timestamp} · ${c.modelId.ifBlank { "未知模型" }} · ${statusDisplayName(c.status)}",
                                        style = AppTypography.labelSmall,
                                        color = TextHint
                                    )
                                    if (isExpanded) {
                                        Spacer(Modifier.height(Spacing.sm))
                                        if (c.betterVersion.isNotBlank()) {
                                            Text("期望版本：${c.betterVersion}", style = AppTypography.labelSmall, color = Primary)
                                        }
                                        if (c.ideaHint.isNotBlank()) {
                                            Text("本轮想法：${c.ideaHint}", style = AppTypography.labelSmall, color = TextSecondary)
                                        }
                                        if (c.intentText.isNotBlank()) {
                                            Text("意图：${c.intentText}", style = AppTypography.labelSmall, color = TextSecondary)
                                        }
                                        if (c.contextMode.isNotBlank()) {
                                            Text("上下文模式：${c.contextMode}", style = AppTypography.labelSmall, color = TextSecondary)
                                        }
                                        if (c.dialogueSnapshot.isNotEmpty()) {
                                            Text("真实对话：", style = AppTypography.labelSmall, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                                            c.dialogueSnapshot.forEach { msg ->
                                                Text(
                                                    "  [${if (msg.speaker == "PARTNER") "对方" else "我"}] ${msg.text}",
                                                    style = AppTypography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                        if (c.memoryRefs.isNotEmpty()) {
                                            Text("记忆引用：${c.memoryRefs.joinToString(", ")}", style = AppTypography.labelSmall, color = TextSecondary)
                                        }
                                        if (c.appVersion.isNotBlank()) {
                                            Text("版本：${c.appVersion} (${c.buildType})", style = AppTypography.labelSmall, color = TextHint)
                                        }
                                        if (c.promptTokens > 0 || c.completionTokens > 0) {
                                            Text("Token：prompt=${c.promptTokens}, completion=${c.completionTokens}", style = AppTypography.labelSmall, color = TextHint)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 导出预览面板
        if (showExport) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showExport = false }
            ) {
                Card(
                    shape = LoveBrainShape.lg,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg)
                        .align(Alignment.Center)
                        .clickable { }
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "导出预览",
                                style = AppTypography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            Text(
                                "复制",
                                style = AppTypography.labelMedium,
                                color = Primary,
                                modifier = Modifier
                                    .clip(LoveBrainShape.md)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("export", exportText))
                                        }
                                    )
                                    .padding(Spacing.sm)
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            exportText,
                            style = AppTypography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "已复制到剪贴板，可粘贴到任何位置。默认已去除身份信息和连接信息。",
                            style = AppTypography.labelSmall,
                            color = TextHint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val (interaction, scale) = rememberPressScale(0.94f, "filterChip_$label")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.sm)
            .background(if (isSelected) Primary else SurfaceInset, LoveBrainShape.sm)
            .border(1.dp, if (isSelected) Primary else Border, LoveBrainShape.sm)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = AppTypography.labelSmall,
            color = if (isSelected) Color.White else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun categoryDisplayName(cat: com.lovebrain.app.model.FeedbackCategory): String = when (cat) {
    com.lovebrain.app.model.FeedbackCategory.UNDERSTANDING_ERROR -> "理解错误"
    com.lovebrain.app.model.FeedbackCategory.EXPRESSION_DISLIKE -> "表达不喜欢"
    com.lovebrain.app.model.FeedbackCategory.OTHER -> "其他"
}

private fun statusDisplayName(status: com.lovebrain.app.model.CaseStatus): String = when (status) {
    com.lovebrain.app.model.CaseStatus.PENDING -> "待分析"
    com.lovebrain.app.model.CaseStatus.CONCLUDED -> "已有结论"
    com.lovebrain.app.model.CaseStatus.TO_VERIFY -> "待验证"
    com.lovebrain.app.model.CaseStatus.VERIFIED -> "已验证"
}
