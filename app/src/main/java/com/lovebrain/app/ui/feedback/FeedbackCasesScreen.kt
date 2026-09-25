package com.lovebrain.app.ui.feedback

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceBase
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.launch

/**
 * 反馈案例独立页面——根级导航目标，不在首页 Column 内插入。
 *
 * 数据通过 ViewModel/DI 提供，不在 Composable 中直接 new Repository。
 * 页面状态：Loading / Empty / Data / Error
 *
 * 修复：
 * - 使用 AlertDialog 替代伪全屏遮罩
 * - 真正的保存按钮使用 CreateDocument launcher
 * - items 使用 stable key
 * - 捕获 ActivityNotFoundException
 * - exportId 绑定导出状态
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
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    // 筛选/展开/格式状态使用 rememberSaveable，旋转/进程重建后恢复
    var filterCategory by rememberSaveable { mutableStateOf<FeedbackCategory?>(null) }
    var expandedCaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportFormat by rememberSaveable { mutableStateOf("markdown") }
    // copiedFeedback 绑定 exportId，新导出自动重置
    var copiedExportId by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadFeedbackCases()
    }

    val filtered = if (filterCategory == null) cases else cases.filter { filterCategory!! in it.categories }

    // CreateDocument launcher——真正写入用户选择的 Uri
    // openOutputStream() 返回 null 时不得假装成功。
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(if (exportFormat == "json") "application/json" else "text/markdown")
    ) { uri ->
        val state = exportState
        if (uri != null && state is SetupViewModel.ExportState.Success) {
            try {
                var writeSucceeded = false
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(state.text.toByteArray())
                    writeSucceeded = true
                }
                if (writeSucceeded) {
                    // 保存成功后关闭预览
                    viewModel.resetExportState()
                } else {
                    // openOutputStream 返回 null——不假装成功
                    saveError = "保存失败：无法打开输出流"
                }
            } catch (e: Exception) {
                saveError = "保存失败：${e.message ?: "未知错误"}"
            }
        }
    }

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
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = backScale; scaleY = backScale }
                            .clip(LoveBrainShape.md)
                            .clickable(
                                interactionSource = backInteraction,
                                indication = null,
                                onClick = onBack
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "←",
                            style = AppTypography.titleMedium,
                            color = Primary
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
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
                        .heightIn(min = 48.dp)
                        .graphicsLayer { scaleX = exportScale; scaleY = exportScale }
                        .clip(LoveBrainShape.md)
                        .background(if (filtered.isNotEmpty()) Primary else SurfaceInset, LoveBrainShape.md)
                        .clickable(
                            interactionSource = exportInteraction,
                            indication = null,
                            enabled = filtered.isNotEmpty()
                        ) {
                            // 新导出自动重置 copied 状态
                            copiedExportId = null
                            viewModel.exportFeedback(filtered, exportFormat)
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

            // 内容区：四态只判一次、版式只有一套。判定顺序与替换前逐字相同：
            // Loading > Error > Empty > Content——原来三个分支各自画一套居中文版式，
            // 于是"空的时候长什么样"每页一个答案。
            val screenState: ScreenState<List<FeedbackCase>> = when {
                isLoading -> ScreenState.Loading
                loadError != null -> ScreenState.Error(
                    message = loadError.orEmpty(),
                    retry = ScreenAction(stringResource(R.string.feedback_retry)) {
                        scope.launch { viewModel.loadFeedbackCases() }
                    }
                )
                filtered.isEmpty() -> ScreenState.Empty(stringResource(R.string.feedback_empty_hint))
                else -> ScreenState.Content(filtered)
            }
            if (screenState is ScreenState.Content) {
                val casesToShow = screenState.value
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // 使用 stable key
                    items(casesToShow, key = { it.caseId }) { c ->
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
            } else {
                LbAsyncState(screenState) { }
            }
        }

        // 导出预览——使用 AlertDialog 替代伪全屏遮罩
        when (val state = exportState) {
            is SetupViewModel.ExportState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            is SetupViewModel.ExportState.Success -> {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                // copiedFeedback 绑定 exportId
                val isCopied = copiedExportId == state.exportId
                AlertDialog(
                    onDismissRequest = { viewModel.resetExportState() },
                    title = {
                        Text(
                            "导出预览",
                            style = AppTypography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                state.text,
                                style = AppTypography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                if (isCopied) "已复制到剪贴板，可粘贴到任何位置。" else "点击「复制」复制到剪贴板，「分享」发送到其他应用，「保存」写入文件。",
                                style = AppTypography.labelSmall,
                                color = TextHint
                            )
                        }
                    },
                    confirmButton = {
                        // 保存按钮——真正写入文件
                        TextButton(onClick = {
                            val fileName = "feedback_export.${exportFormat}"
                            saveLauncher.launch(fileName)
                        }) {
                            Text("保存", color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        Row {
                            // 分享按钮——捕获 ActivityNotFoundException
                            TextButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (exportFormat == "json") "application/json" else "text/markdown"
                                    putExtra(Intent.EXTRA_TEXT, state.text)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(shareIntent, "分享到"))
                                } catch (e: android.content.ActivityNotFoundException) {
                                    saveError = "没有可用的分享应用"
                                }
                            }) {
                                Text("分享", color = Primary)
                            }
                            // 复制按钮
                            TextButton(onClick = {
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("export", state.text))
                                copiedExportId = state.exportId
                            }) {
                                Text(
                                    if (isCopied) "✓ 已复制" else "复制",
                                    color = Primary,
                                    fontWeight = if (isCopied) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            TextButton(onClick = { viewModel.resetExportState() }) {
                                Text("关闭", color = TextSecondary)
                            }
                        }
                    }
                )
            }
            is SetupViewModel.ExportState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetExportState() },
                    title = { Text("导出失败", style = AppTypography.titleMedium) },
                    text = { Text(state.message, color = Error, style = AppTypography.bodyMedium) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.resetExportState() }) {
                            Text("关闭", color = Primary)
                        }
                    }
                )
            }
            SetupViewModel.ExportState.Idle -> { /* nothing */ }
        }

        // 保存错误 Dialog
        if (saveError != null) {
            AlertDialog(
                onDismissRequest = { saveError = null },
                title = { Text("操作失败", style = AppTypography.titleMedium) },
                text = { Text(saveError.orEmpty(), color = Error, style = AppTypography.bodyMedium) },
                confirmButton = {
                    TextButton(onClick = { saveError = null }) {
                        Text("关闭", color = Primary)
                    }
                }
            )
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
