package com.lovebrain.app.ui.feedback

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.designsystem.LbDialog
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbDialogMessageTone
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

    // 这一层 Box 只当浮层的叠放父节点：不画底色、不加边距。
    // 整屏底色与水平边距归 `LbScreenScaffold` 所有（§6.1 :478）。搬之前这一页是
    // `Box.fillMaxSize().background(SurfaceBase)` + 各区块自己 `Spacing.lg`，
    // 语义树量到的水平边距是 **12dp**，而脚手架那一档是 24dp（账本 §57.1、§58）。
    Box(modifier = Modifier.fillMaxSize()) {
        LbScreenScaffold(
            topBar = {
                // 页头归一 `LbTopBar`（§6.1 :479）。搬之前它是"第五式"：
                // `SurfaceCard` 底带 + 箭头字形当返回那颗的名字，`role=无`，
                // 英文环境下读屏念的是「←」这个字形本身。
                LbTopBar(
                    title = stringResource(R.string.feedback_cases_title),
                    level = LbTopBarLevel.Page,
                    // 「(N条)」也进资源：数出来多少条就传多少，不写死。
                    subtitle = pluralStringResource(
                        R.plurals.feedback_case_count, filtered.size, filtered.size
                    ),
                    onBack = onBack,
                    trailing = {
                        ExportAction(
                            enabled = filtered.isNotEmpty(),
                            label = if (exportFormat == "markdown") {
                                stringResource(R.string.feedback_export_markdown)
                            } else {
                                stringResource(R.string.feedback_export_json)
                            }
                        ) {
                            // 新导出自动重置 copied 状态
                            copiedExportId = null
                            viewModel.exportFeedback(filtered, exportFormat)
                        }
                    }
                )
            }
        ) {
            // 筛选芯片：水平边距由脚手架给，这一行只留垂直那一档
            // （原来是 `padding(horizontal = Spacing.lg)`，套上脚手架就成 24+12 叠两层）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilterChip(stringResource(R.string.feedback_filter_all), filterCategory == null) {
                    filterCategory = null
                }
                FeedbackCategory.entries.forEach { cat ->
                    FilterChip(
                        categoryDisplayName(cat),
                        filterCategory == cat
                    ) { filterCategory = cat }
                }
                Spacer(Modifier.width(Spacing.md))
                val markdownLabel = stringResource(R.string.feedback_format_markdown)
                val jsonLabel = stringResource(R.string.feedback_format_json)
                FilterChip(
                    if (exportFormat == "markdown") {
                        stringResource(R.string.feedback_format_selected, markdownLabel)
                    } else {
                        markdownLabel
                    },
                    exportFormat == "markdown"
                ) { exportFormat = "markdown" }
                FilterChip(
                    if (exportFormat == "json") {
                        stringResource(R.string.feedback_format_selected, jsonLabel)
                    } else {
                        jsonLabel
                    },
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
                    // 水平那一档**归零**：脚手架已经把整列往里推了一档，
                    // 这里再写 Spacing.lg 就是 24+12 叠两层（这一页搬进来最容易踩的那脚）。
                    // 只留垂直档：列表首尾要呼吸，滚动条不贴页头。
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = Spacing.sm, bottom = Spacing.xl
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
                                .clickable(role = Role.Button) {
                                    expandedCaseId = if (isExpanded) null else c.caseId
                                }
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Text(
                                    "【${categoryNamesOf(c.categories)}】 ${c.reasons.joinToString(", ")}",
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

        // 导出预览——Loading / Success / Error 三态现在共用同一族浮层形状：
        // Loading 走 LbModalSheet（不可点空白关），Success/Error 走 LbDialog。
        // 原先这里自己画了一层全屏遮罩，还挂了一个 `clickable(enabled = false){}`——
        // 那等于往语义树里塞一颗"点不动也没名字"的整屏按钮（§6.5 那两条都踩）。
        when (val state = exportState) {
            is SetupViewModel.ExportState.Loading -> {
                LbModalSheet(onDismissRequest = {}, dismissable = false) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            is SetupViewModel.ExportState.Success -> {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                // copiedFeedback 绑定 exportId
                val isCopied = copiedExportId == state.exportId
                LbDialog(
                    title = "导出预览",
                    onDismissRequest = { viewModel.resetExportState() },
                    confirm = LbDialogAction(
                        // 保存按钮——真正写入文件
                        label = "保存",
                        onClick = {
                            val fileName = "feedback_export.${exportFormat}"
                            saveLauncher.launch(fileName)
                        }
                    ),
                    secondary = listOf(
                        // 分享按钮——捕获 ActivityNotFoundException
                        LbDialogAction(
                            label = "分享",
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (exportFormat == "json") "application/json" else "text/markdown"
                                    putExtra(Intent.EXTRA_TEXT, state.text)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(shareIntent, "分享到"))
                                } catch (e: android.content.ActivityNotFoundException) {
                                    saveError = "没有可用的分享应用"
                                }
                            }
                        ),
                        // 复制按钮
                        LbDialogAction(
                            label = if (isCopied) "✓ 已复制" else "复制",
                            onClick = {
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("export", state.text))
                                copiedExportId = state.exportId
                            }
                        )
                    ),
                    dismiss = LbDialogAction("关闭", { viewModel.resetExportState() },
                        tone = LbDialogActionTone.Muted),
                    body = {
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
                    }
                )
            }
            is SetupViewModel.ExportState.Error -> {
                LbDialog(
                    title = "导出失败",
                    onDismissRequest = { viewModel.resetExportState() },
                    message = state.message,
                    messageTone = LbDialogMessageTone.Error,
                    confirm = LbDialogAction(label = "关闭", onClick = { viewModel.resetExportState() })
                )
            }
            SetupViewModel.ExportState.Idle -> { /* nothing */ }
        }

        // 保存错误 Dialog
        if (saveError != null) {
            LbDialog(
                title = "操作失败",
                onDismissRequest = { saveError = null },
                message = saveError.orEmpty(),
                messageTone = LbDialogMessageTone.Error,
                confirm = LbDialogAction(label = "关闭", onClick = { saveError = null })
            )
        }
    }
}

/**
 * 页头尾部那颗导出。形状照搬之前那一份（实心品牌底，零结果时灰），只补两件事：
 * `role = Role.Button`（§6.5 :532——本机量到 58x48dp、热区够、`role=无`），
 * 以及"禁用仍留在树上、并报得出 disabled"这条合同（由 `FeedbackCasesSemanticsTest` 钉）。
 *
 * ⚠ 它**没有**换成 `LbPrimaryButton`。那是 :479"页面唯一主动作"的判断，换过去要连带动作
 * 形状与字号；这一格的验收线是外框/页头归一 + 热区与角色补齐，不顺手改形状
 * （§51 那次"顺手改形状"留下的教训：视觉变化没有截图基线可核，只能留给人工）。
 */
@Composable
private fun ExportAction(enabled: Boolean, label: String, onClick: () -> Unit) {
    val (interaction, scale) = rememberPressScale(0.96f, "exportBtn")
    Box(
        modifier = Modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.md)
            .background(if (enabled) Primary else SurfaceInset, LoveBrainShape.md)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = AppTypography.labelMedium,
            color = if (enabled) Color.White else TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 芯片。两层：**点击挂在 48 见方的外盒上，视觉仍是那颗小胶囊**。
 *
 * §57 本机量到这族六颗是 15–19dp 高（「JSON」34x15、「全部」28x19…），而 §6.5 :531
 * 要的是手指能点中的那一颗 ≥48dp。只在原来那条链上加 `heightIn` 是不够的——
 * 那台仪器反复量到的一条：外层容器变大了、点击仍挂在子里面，等于没改
 * （所以 `clickable` 与 `semantics` 都排在外盒上，版式与按压缩放留在内盒）。
 *
 * 互斥单选 ⇒ `Role.Tab` + `selected`（与 `ReplyInput.RoleChip`、面板那三档模式同一写法）。
 * 「✓ Markdown」那个对勾留着：它不是颜色之外的第二种选中提示，去掉会让低视力用户只剩底色可辨；
 * 规范位是 `selected`，字形是给眼睛看的。
 */
@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val (interaction, scale) = rememberPressScale(0.94f, "filterChip_$label")
    Box(
        modifier = Modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(LoveBrainShape.sm)
                .background(if (isSelected) Primary else SurfaceInset, LoveBrainShape.sm)
                .border(1.dp, if (isSelected) Primary else Border, LoveBrainShape.sm)
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
}

/**
 * 类别名。之前是非 composables 的 `when` 直接返回内联中文，于是英文环境下
 * 页头芯片与卡片首行一起念中文；现在两边都读这一处（同一条判据不许有两份实现）。
 */
@Composable
private fun categoryDisplayName(cat: FeedbackCategory): String = when (cat) {
    FeedbackCategory.UNDERSTANDING_ERROR -> stringResource(R.string.feedback_category_understanding_error)
    FeedbackCategory.EXPRESSION_DISLIKE -> stringResource(R.string.feedback_category_expression_dislike)
    FeedbackCategory.OTHER -> stringResource(R.string.feedback_category_other)
}

/**
 * 卡片首行那句「【类别、类别】 原因、原因」。
 *
 * 为什么不是 `cats.joinToString { categoryDisplayName(it) }`：`joinToString` 的转换 lambda
 * 是普通 inline lambda，**里面调 @Composable 编译不过**。这一层用普通 for 循环把名字取出来，
 * 判据仍只有一份（[categoryDisplayName]）。
 */
@Composable
private fun categoryNamesOf(cats: List<FeedbackCategory>): String {
    val names = ArrayList<String>(cats.size)
    for (cat in cats) names.add(categoryDisplayName(cat))
    return names.joinToString(", ")
}

private fun statusDisplayName(status: com.lovebrain.app.model.CaseStatus): String = when (status) {
    com.lovebrain.app.model.CaseStatus.PENDING -> "待分析"
    com.lovebrain.app.model.CaseStatus.CONCLUDED -> "已有结论"
    com.lovebrain.app.model.CaseStatus.TO_VERIFY -> "待验证"
    com.lovebrain.app.model.CaseStatus.VERIFIED -> "已验证"
}
