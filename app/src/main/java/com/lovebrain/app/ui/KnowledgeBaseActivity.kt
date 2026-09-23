package com.lovebrain.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.ui.common.RowActionButton
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.KbCreationOutcome
import com.lovebrain.app.viewmodel.KbEvent
import com.lovebrain.app.viewmodel.KnowledgeBaseViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/** 知识库管理页内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object KbDimens {
    const val EMPTY_ICON_CONTAINER_DP = 72    // 空态图标容器（语义例外：大于通用 48）
    const val EMPTY_ICON_SIZE_DP = 36         // 空态图标本体（语义例外）
    const val PRIMARY_ACTION_HEIGHT_DP = 48   // 新建/导入/完成大按钮高度
    const val EDIT_ICON_SIZE_DP = 14          // 重命名小铅笔图标
    const val ONBOARDING_SPINNER_SIZE_DP = 18 // 问卷生成中按钮内转圈尺寸
    const val PROGRESS_BAR_HEIGHT_DP = 4     // 问卷答题进度条高度
}

class KnowledgeBaseActivity : ComponentActivity() {

    // 数据访问一律经 ViewModel：Activity 不 inject Repository，只负责窗口标记与 Activity Result 启动
    private val viewModel: KnowledgeBaseViewModel by viewModel()

    /** 导出目标库：CreateDocument 回调只回传 Uri，需自行配对 */
    private var pendingExportKb: String? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val kbName = pendingExportKb
        pendingExportKb = null
        if (uri == null || kbName == null) return@registerForActivityResult
        viewModel.export(kbName, uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.import(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // P3-05: FLAG_SECURE——知识库内容含关系数据，防止最近任务截图泄露
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            LoveBrainTheme {
                KbManagementScreen(
                    viewModel = viewModel,
                    onEdit = { name ->
                        startActivity(
                            Intent(this, KbEditActivity::class.java).putExtra("kb_name", name)
                        )
                    },
                    onExport = { name ->
                        pendingExportKb = name
                        exportLauncher.launch("kb_${name}.zip")
                    },
                    onImport = {
                        importLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream")
                        )
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * 建库/导入/导出页的状态承载。
 *
 * 结果反馈只有一条通道：ViewModel 的 KbEvent → 固定文案 → AlertDialog（禁 Toast 铁律）。
 */
@Composable
private fun KbManagementScreen(
    viewModel: KnowledgeBaseViewModel,
    onEdit: (String) -> Unit,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showOnboarding by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    // 未配置供应商二选一弹窗：继续 = 空模板库，取消 = 留在向导内
    var showNoProviderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is KbEvent.Creation -> when (val outcome = event.outcome) {
                    KbCreationOutcome.ProfileCreated -> {
                        showOnboarding = false
                        feedback = "知识库已创建，画像已生成"
                        viewModel.refresh()
                    }
                    KbCreationOutcome.TemplateOnlyCreated -> {
                        showOnboarding = false
                        feedback = "AI 画像生成不完整，已创建模板库，可稍后在编辑页补充"
                        viewModel.refresh()
                    }
                    KbCreationOutcome.EmptyCreated -> {
                        showOnboarding = false
                        viewModel.refresh()
                    }
                    KbCreationOutcome.EmptyCreateFailed -> {
                        showOnboarding = false
                        feedback = "创建失败：可能名称重复，请重试"
                    }
                    KbCreationOutcome.OnboardingCreateFailed -> {
                        showOnboarding = false
                        feedback = "创建知识库失败，请重试"
                    }
                    KbCreationOutcome.Cancelled -> showOnboarding = false
                    KbCreationOutcome.ProviderNotConfigured -> showNoProviderDialog = true
                }
                KbEvent.Exported -> feedback = "知识库已导出"
                KbEvent.ExportFailed -> feedback = "导出失败，请重试"
                KbEvent.Imported -> feedback = "知识库导入成功"
                KbEvent.ImportFailed -> feedback = "导入失败，请检查文件是否为有效的 LoveBrain 知识库备份"
                KbEvent.DeleteFailed -> feedback = "删除失败，请重试"
            }
        }
    }

    KbListScreen(
        kbs = state.knowledgeBases,
        activeName = state.activeName,
        onActivate = viewModel::setActive,
        onNewKb = { showOnboarding = true },
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onEdit = onEdit,
        // 导出警示 Compose 化——确认链路上提至此，弹窗由 KbListScreen pendingExport 承载
        onConfirmExport = onExport,
        onImport = onImport,
        onBack = onBack
    )

    if (showOnboarding) {
        OnboardingScreen(
            onDismiss = { showOnboarding = false },
            onSkip = { viewModel.createEmptyKb() },
            onComplete = { schema -> viewModel.createKbWithOnboarding(schema) },
            // 生成中取消：cancel 协程后关闭页面（结果以 KbEvent.Creation(Cancelled) 回传）
            onCancelGenerating = {
                viewModel.cancelOnboarding()
                showOnboarding = false
            }
        )
    }

    // 未配置供应商二选一弹窗（继续 = 空模板库，取消 = 返回向导）
    if (showNoProviderDialog) {
        AlertDialog(
            onDismissRequest = { showNoProviderDialog = false },
            title = { Text("未配置模型供应商", style = AppTypography.titleLarge) },
            text = {
                Text(
                    "未配置模型供应商，只能创建空模板库",
                    style = AppTypography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNoProviderDialog = false
                    viewModel.createEmptyKb()
                }) { Text("继续", color = Primary, style = AppTypography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { showNoProviderDialog = false }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }

    // 建库/生成/导入导出结果反馈弹窗
    feedback?.let { msg ->
        AlertDialog(
            onDismissRequest = { feedback = null },
            title = { Text("提示", style = AppTypography.titleLarge) },
            text = { Text(msg, style = AppTypography.bodyMedium, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { feedback = null }) {
                    Text("知道了", color = Primary, style = AppTypography.titleMedium)
                }
            }
        )
    }
}


@Composable
private fun KbListScreen(
    kbs: List<KnowledgeBase>,
    activeName: String?,
    onActivate: (String) -> Unit,
    onNewKb: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
    onConfirmExport: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<KnowledgeBase?>(null) }
    var pendingExport by remember { mutableStateOf<KnowledgeBase?>(null) }

    ScreenPage(title = "知识库管理", onBack = onBack) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
        if (kbs.isEmpty()) {
            // 空状态三要素（第2轮调研：图标 + 友好文案 + 明确 CTA）
            Card(
                shape = LoveBrainShape.lg,
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                // ：阴影统一收进 2/4 令牌（6→4 为任务单批准的唯一超限修正）
                modifier = Modifier.fillMaxWidth().shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(KbDimens.EMPTY_ICON_CONTAINER_DP.dp)
                            .clip(LoveBrainShape.full)
                            .background(PrimaryLight)
                            .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.full),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bubble),
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(KbDimens.EMPTY_ICON_SIZE_DP.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Text("还没有知识库", style = AppTypography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        "点下方「新建知识库」，给她建一份专属档案，军师回复会更懂她。",
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            kbs.forEach { kb ->
                KbCard(
                    kb = kb,
                    isActive = kb.name == activeName,
                    onActivate = { onActivate(kb.name) },
                    onRename = { newName -> onRename(kb.name, newName) },
                    onEdit = { onEdit(kb.name) },
                    onExport = { pendingExport = kb },
                    onDelete = { pendingDelete = kb }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Button(
                onClick = { onNewKb() },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = LoveBrainShape.md,
                modifier = Modifier.weight(1f).height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
            ) {
                Text("新建知识库", style = AppTypography.titleMedium)
            }
            OutlinedButton(
                onClick = onImport,
                shape = LoveBrainShape.md,
                modifier = Modifier.weight(1f).height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
            ) {
                Text("导入知识库", style = AppTypography.titleMedium, color = TextPrimary)
            }
        }
        }
    }

    pendingDelete?.let { kb ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除知识库「${kb.displayName}」？", style = AppTypography.titleLarge) },
            text = {
                Text("将物理删除该知识库的全部内容，不可恢复。确定删除？", style = AppTypography.bodyMedium, color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = kb.name
                    pendingDelete = null
                    onDelete(n)
                }) { Text("删除", color = Error, style = AppTypography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }

    // ：导出警示 Compose 化（/：明文 zip 含全部画像/归档，先警示再启动；文案红线逐字不动）
    pendingExport?.let { kb ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { Text("导出提醒", style = AppTypography.titleLarge) },
            text = {
                Text("导出文件是明文，包含她的全部画像、聊天归档与谈心记录。请妥善保管，不要分享给他人。", style = AppTypography.bodyMedium, color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = kb.name
                    pendingExport = null
                    onConfirmExport(n)
                }) { Text("仍要导出", color = Primary, style = AppTypography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { pendingExport = null }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }
}

@Composable
private fun KbCard(
    kb: KnowledgeBase,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRename: (String) -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(kb.displayName) }

    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        // ：阴影统一收进 2/4 令牌（6→4 为任务单批准的唯一超限修正）
        // ：点卡片主体 = 激活（非当前库时），与供应商行交互一致
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)
            .clickable(enabled = !isActive, onClick = onActivate)
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            // 第一行：名称 + 重命名笔 + 当前使用徽章 + 删除
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        renameText = kb.displayName
                        showRename = true
                    }
                ) {
                    Text(
                        kb.displayName,
                        style = AppTypography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "重命名",
                        tint = TextHint,
                        modifier = Modifier.size(KbDimens.EDIT_ICON_SIZE_DP.dp)
                    )
                }
                if (isActive) {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Box(
                        modifier = Modifier
                            .background(PrimaryLight, LoveBrainShape.sm)
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Text("当前使用", style = AppTypography.labelSmall, color = PrimaryDark, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除知识库",
                    tint = TextHint,
                    modifier = Modifier
                        .size(AppDimens.ACTION_ICON_SIZE_DP.dp)
                        .clip(LoveBrainShape.sm)
                        .clickable(onClick = onDelete)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            // 第二行：阶段/对话信息 + 编辑/导出
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "阶段：${kb.stage} ｜ 已对话 ${kb.turnCount} 轮",
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                RowActionButton("编辑") { onEdit() }
                Spacer(modifier = Modifier.width(Spacing.sm))
                RowActionButton("导出") { onExport() }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("修改显示名", style = AppTypography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("显示名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRename = false
                        onRename(renameText.trim())
                    },
                    // 空名禁止保存，避免卡片标题变空白
                    enabled = renameText.isNotBlank()
                ) { Text("保存", color = Primary, style = AppTypography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("取消", color = TextSecondary, style = AppTypography.titleMedium)
                }
            }
        )
    }
}

@Composable
private fun OnboardingScreen(
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onComplete: (com.lovebrain.app.domain.OnboardingSchema) -> Unit,
    onCancelGenerating: () -> Unit
) {
    // v4.2 向导状态：currentStep 1-5 答题，6 称呼收尾
    var currentStep by remember { mutableStateOf(1) }
    var branch by remember { mutableStateOf("") }
    // v4.2: 统一答案对象（Set<Int> + customText），废除 -1 哨兵
    val answers = remember {
        mutableStateMapOf<Int, com.lovebrain.app.domain.OnboardingAnswer>()
    }
    var myName by remember { mutableStateOf("") }
    var herName by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    // 补充说明展开状态
    var showCustomInput by remember { mutableStateOf(false) }
    // 多选上限提示
    var maxSelectionToast by remember { mutableStateOf(false) }

    val totalSteps = 5
    val singleColumn = com.lovebrain.app.ui.onboarding.shouldUseSingleColumn()

    ScreenPage(
        title = "新建知识库",
        onBack = {
            if (generating) {
                onCancelGenerating()
            } else if (currentStep > 1) {
                currentStep--
                showCustomInput = false
            } else {
                onDismiss()
            }
        },
        trailing = {
            TextButton(
                onClick = onSkip,
                enabled = !generating
            ) {
                Text(
                    "建空档案",
                    color = if (generating) TextHint else TextSecondary,
                    style = AppTypography.labelLarge
                )
            }
        }
    ) {
        // ── 进度条 ──
        val progressStep = if (currentStep <= totalSteps) currentStep else totalSteps
        Text(
            "第 $progressStep 步 · 共 $totalSteps 步",
            style = AppTypography.labelSmall,
            color = TextHint
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(KbDimens.PROGRESS_BAR_HEIGHT_DP.dp)
                .clip(LoveBrainShape.full)
                .background(SurfaceInset)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressStep.toFloat() / totalSteps)
                    .height(KbDimens.PROGRESS_BAR_HEIGHT_DP.dp)
                    .clip(LoveBrainShape.full)
                    .background(Primary)
            )
        }
        Spacer(modifier = Modifier.height(Spacing.lg))

        // ── 内容区 ──
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            if (currentStep <= totalSteps) {
                val question = if (currentStep == 1) {
                    com.lovebrain.app.domain.OnboardingBank.q1
                } else {
                    com.lovebrain.app.domain.OnboardingBank.question(currentStep, branch)
                }

                // 红线检测
                val redline = com.lovebrain.app.domain.OnboardingStateMachine
                    .isRedlineTriggered(answers, branch)
                val hiddenIndices = if (redline && currentStep == 5) {
                    com.lovebrain.app.domain.OnboardingStateMachine.hiddenOptionIndices(true)
                } else emptySet()

                // 红线提示
                if (redline && currentStep == 5) {
                    Text(
                        "军师检测到你目前的情况更适合止损和自我调整，暂时不提供挽回建议。",
                        color = Error,
                        style = AppTypography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }

                // ── 题目标题（最强层级）──
                Text(
                    question.title,
                    style = AppTypography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                // ── 规则说明（次弱层级）──
                val currentAnswer = answers[currentStep]
                    ?: com.lovebrain.app.domain.OnboardingAnswer()
                if (question.selectionMode == com.lovebrain.app.domain.SelectionMode.MULTIPLE) {
                    val max = question.maxSelections ?: 2
                    val selectedCount = currentAnswer.selectedIndices.size
                    val hintText = if (selectedCount == 0) {
                        "可多选，最多 $max 项"
                    } else {
                        "已选 $selectedCount/$max"
                    }
                    Text(
                        hintText,
                        style = AppTypography.labelMedium,
                        color = if (selectedCount >= max) PrimaryDark else TextHint
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))

                // ── 选项区 ──
                val visibleOptions = question.options.mapIndexed { idx, opt -> idx to opt }
                    .filter { (idx, _) -> idx !in hiddenIndices }

                if (singleColumn) {
                    // 大字体 / 窄屏：单列
                    visibleOptions.forEach { (idx, opt) ->
                        com.lovebrain.app.ui.onboarding.OnboardingOptionCard(
                            text = opt.text,
                            selected = idx in currentAnswer.selectedIndices,
                            selectionMode = question.selectionMode,
                            enabled = !generating,
                            onClick = {
                                maxSelectionToast = false
                                handleOptionClick(
                                    question = question,
                                    index = idx,
                                    currentStep = currentStep,
                                    answers = answers,
                                    branch = branch,
                                    onBranchChange = { branch = it },
                                    onMaxReached = { maxSelectionToast = true },
                                    generating = generating
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            useSingleColumn = true
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                    }
                } else {
                    // 标准：双列 chunked(2)
                    visibleOptions.chunked(2).forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            pair.forEach { (idx, opt) ->
                                com.lovebrain.app.ui.onboarding.OnboardingOptionCard(
                                    text = opt.text,
                                    selected = idx in currentAnswer.selectedIndices,
                                    selectionMode = question.selectionMode,
                                    enabled = !generating,
                                    onClick = {
                                        maxSelectionToast = false
                                        handleOptionClick(
                                            question = question,
                                            index = idx,
                                            currentStep = currentStep,
                                            answers = answers,
                                            branch = branch,
                                            onBranchChange = { branch = it },
                                            onMaxReached = { maxSelectionToast = true },
                                            generating = generating
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // ── 补充说明入口（仅 Q2-Q5）──
                if (currentStep in 2..totalSteps) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    // 弱一级入口：点击展开/收起
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showCustomInput = !showCustomInput }
                            )
                            .padding(vertical = Spacing.xs)
                    ) {
                        Text(
                            if (showCustomInput) "− 收起补充" else "＋ 补充其他情况",
                            style = AppTypography.bodySmall,
                            color = Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // 展开后的输入框
                    if (showCustomInput) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        val existingCustom = answers[currentStep]?.customText ?: ""
                        var customText by remember(currentStep, existingCustom) {
                            mutableStateOf(existingCustom)
                        }
                        CompactInput(
                            value = customText,
                            onValueChange = {
                                customText = it.take(100)
                                val cur = answers[currentStep]
                                    ?: com.lovebrain.app.domain.OnboardingAnswer()
                                answers[currentStep] = cur.copy(customText = customText)
                            },
                            placeholder = "简单说说你的情况，100 字以内"
                        )
                    }
                }

                // 多选上限提示
                if (maxSelectionToast) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        "最多选择 ${question.maxSelections ?: 2} 项",
                        style = AppTypography.labelSmall,
                        color = Error
                    )
                }
            } else {
                // Step6：称呼输入（选填）
                Text("选填（不填也能建，之后能改）", style = AppTypography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                CompactInput(
                    value = myName,
                    onValueChange = { myName = it },
                    placeholder = "你的称呼"
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                CompactInput(
                    value = herName,
                    onValueChange = { herName = it },
                    placeholder = "她的称呼"
                )
            }
        }

        // ── 底部按钮 ──
        if (generating) {
            Button(
                onClick = { onCancelGenerating() },
                enabled = true,
                colors = ButtonDefaults.buttonColors(containerColor = TextHint),
                shape = LoveBrainShape.md,
                modifier = Modifier.fillMaxWidth().height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
            ) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .height(KbDimens.ONBOARDING_SPINNER_SIZE_DP.dp)
                        .width(KbDimens.ONBOARDING_SPINNER_SIZE_DP.dp),
                    strokeWidth = Spacing.xs
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Text("点击取消（军师还在生成画像…）", style = AppTypography.titleMedium)
            }
        } else if (currentStep > totalSteps) {
            // Step6：完成按钮
            Button(
                onClick = {
                    generating = true
                    val schema = com.lovebrain.app.domain.OnboardingSchemaBuilder.build(
                        answers.toMap(), myName.trim(), herName.trim()
                    )
                    onComplete(schema)
                },
                enabled = true,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = LoveBrainShape.md,
                modifier = Modifier.fillMaxWidth().height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
            ) {
                Text("完成，AI 生成画像", style = AppTypography.titleMedium)
            }
        } else {
            // 答题阶段：下一步按钮（不自动跳页）
            val question = if (currentStep == 1) {
                com.lovebrain.app.domain.OnboardingBank.q1
            } else {
                com.lovebrain.app.domain.OnboardingBank.question(currentStep, branch)
            }
            val currentAnswer = answers[currentStep]
                ?: com.lovebrain.app.domain.OnboardingAnswer()
            val canProceed = currentAnswer.isAnswered(question)

            Button(
                onClick = {
                    if (currentStep < totalSteps) {
                        currentStep++
                        showCustomInput = false
                        maxSelectionToast = false
                    } else {
                        currentStep = totalSteps + 1
                        showCustomInput = false
                    }
                },
                enabled = canProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canProceed) Primary else SurfaceInset
                ),
                shape = LoveBrainShape.md,
                modifier = Modifier.fillMaxWidth().height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
            ) {
                Text(
                    "下一步",
                    style = AppTypography.titleMedium,
                    color = if (canProceed) androidx.compose.ui.graphics.Color.White else TextHint
                )
            }
        }
    }
}

/**
 * 处理选项点击：toggle 选中状态、Q1 改选清理后续、红线变化清理 Q5 隐藏项。
 */
private fun handleOptionClick(
    question: com.lovebrain.app.domain.OnboardingQuestion,
    index: Int,
    currentStep: Int,
    answers: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, com.lovebrain.app.domain.OnboardingAnswer>,
    branch: String,
    onBranchChange: (String) -> Unit,
    onMaxReached: () -> Unit,
    generating: Boolean
) {
    if (generating) return

    val currentAnswer = answers[currentStep]
        ?: com.lovebrain.app.domain.OnboardingAnswer()

        // 记录红线变化前状态
    val wasRedline = com.lovebrain.app.domain.OnboardingStateMachine
        .isRedlineTriggered(answers, branch)

    // Q1 改选 → 分支变化 → 清空后续
    if (currentStep == 1) {
        val newBranch = com.lovebrain.app.domain.OnboardingStateMachine
            .branchFromQ1(index)
        if (newBranch != branch) {
            onBranchChange(newBranch)
            com.lovebrain.app.domain.OnboardingStateMachine.clearDownstreamAnswers(answers)
        }
        // Q1 是 SINGLE，直接设为唯一选择
        answers[currentStep] = com.lovebrain.app.domain.OnboardingAnswer(
            selectedIndices = setOf(index),
            customText = currentAnswer.customText
        )
    } else {
        // Q2-Q5: 走 toggle 逻辑
        val newAnswer = com.lovebrain.app.domain.OnboardingStateMachine
            .toggleOption(question, currentAnswer, index)
        // ONB-03 修复：只有 MULTIPLE 且集合完全没变且点的是新项 → 才是因上限被拒
        if (
            question.selectionMode == com.lovebrain.app.domain.SelectionMode.MULTIPLE &&
            newAnswer.selectedIndices == currentAnswer.selectedIndices &&
            index !in currentAnswer.selectedIndices
        ) {
            onMaxReached()
        } else {
            // 清除上限提示
        }
        // 保留 customText
        answers[currentStep] = newAnswer.copy(customText = currentAnswer.customText)
    }

    // 红线变化检测
    val nowRedline = com.lovebrain.app.domain.OnboardingStateMachine
        .isRedlineTriggered(answers, branch)
    if (!wasRedline && nowRedline) {
        // 新触发红线 → 清理 Q5 中的隐藏项
        val hiddenIndices = com.lovebrain.app.domain.OnboardingStateMachine
            .hiddenOptionIndices(true)
        com.lovebrain.app.domain.OnboardingStateMachine.cleanHiddenFromQ5(
            answers, hiddenIndices
        )
    }
}
