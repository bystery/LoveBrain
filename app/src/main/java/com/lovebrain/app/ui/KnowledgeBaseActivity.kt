package com.lovebrain.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.designsystem.LbDialog
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.ui.common.RowActionButton
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.ui.kb.KbDimens
import com.lovebrain.app.ui.kb.OnboardingScreen
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.KbCreationOutcome
import com.lovebrain.app.viewmodel.KbEvent
import com.lovebrain.app.viewmodel.KbListState
import com.lovebrain.app.viewmodel.KnowledgeBaseViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

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
        // FLAG_SECURE——知识库内容含关系数据，防止最近任务截图泄露
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
                        feedback = "知识库已创建"
                        viewModel.refresh()
                    }
                    KbCreationOutcome.TemplateOnlyCreated -> {
                        showOnboarding = false
                        feedback = "先建了空档案，可稍后在编辑页补充"
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
                KbEvent.Exported -> feedback = "已导出"
                KbEvent.ExportFailed -> feedback = "导出失败，请重试"
                KbEvent.Imported -> feedback = "已导入"
                KbEvent.ImportFailed -> feedback = "导入失败：不是有效的知识库备份文件"
                KbEvent.DeleteFailed -> feedback = "删除失败，请重试"
            }
        }
    }

    KbListScreen(
        screenState = kbScreenState(
            state = state,
            emptyMessage = stringResource(R.string.kb_empty_message),
            errorMessage = stringResource(R.string.kb_load_failed),
            // 空态那颗动作直接开向导：空态里的动作是一处操作，不是一行指路文字——
            // 替换前这里只是一句"点下方「新建知识库」"。
            newKb = ScreenAction(stringResource(R.string.kb_empty_action)) { showOnboarding = true },
            retry = ScreenAction(stringResource(R.string.action_retry)) { viewModel.refresh() }
        ),
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

    // 未配置供应商：一句话说清能做什么、不能做什么（旧文案里"模板库"是内部词）
    if (showNoProviderDialog) {
        LbDialog(
            title = "还没配置模型",
            onDismissRequest = { showNoProviderDialog = false },
            message = "没配置模型，只能先建一份空档案",
            confirm = LbDialogAction(
                label = "继续",
                onClick = {
                    showNoProviderDialog = false
                    viewModel.createEmptyKb()
                }
            ),
            dismiss = LbDialogAction("取消", { showNoProviderDialog = false },
                tone = LbDialogActionTone.Muted)
        )
    }

    // 建库/生成/导入导出结果反馈弹窗
    feedback?.let { msg ->
        LbDialog(
            title = "提示",
            onDismissRequest = { feedback = null },
            message = msg,
            confirm = LbDialogAction(label = "知道了", onClick = { feedback = null })
        )
    }
}

/**
 * 这一页"现在是哪一格"只在这里判一次，[LbAsyncState] 只负责把已经定好的那格画出来。
 *
 * 优先顺序 Loading > Error > Empty > Content 与反馈案例页、供应商区**同一副判据**——
 * 三个目的地共用一套，而不是各页各定一副（替换前这一页根本没有 Error 这一格，
 * 读取抛异常时它会一直转圈）。
 *
 * `message` / 动作标签由调用方把**已解析的资源字符串**传进来：`stringResource` 不能在语义
 * 判定里现调，而且这样这个函数是纯的——四格可以用 [KbScreenStateMappingTest] 穷举。
 */
internal fun kbScreenState(
    state: KbListState,
    emptyMessage: String,
    errorMessage: String,
    newKb: ScreenAction,
    retry: ScreenAction
): ScreenState<KbListState> = when {
    !state.loaded -> ScreenState.Loading
    state.loadFailed -> ScreenState.Error(errorMessage, retry)
    state.knowledgeBases.isEmpty() -> ScreenState.Empty(emptyMessage, newKb)
    else -> ScreenState.Content(state)
}

@Composable
internal fun KbListScreen(
    screenState: ScreenState<KbListState>,
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
        // 四态只在这里出现一次。替换前这里是一张自造的 40 行空态卡（图标 + 标题 + 一句
        // "点下方「新建知识库」"的指路文字），版式与反馈案例页、供应商区各不相同。
        LbAsyncState(screenState) { shown ->
            shown.knowledgeBases.forEach { kb ->
                KbCard(
                    kb = kb,
                    isActive = kb.name == shown.activeName,
                    onActivate = { onActivate(kb.name) },
                    onRename = { newName -> onRename(kb.name, newName) },
                    onEdit = { onEdit(kb.name) },
                    onExport = { pendingExport = kb },
                    onDelete = { pendingDelete = kb }
                )
            }
        }

        // 底部这条大按钮是页面常驻工具条（导入只在这里），四态都保留：
        // 空态那颗动作开的是向导，这条右边的按钮开的是文件选择器，两件事。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 这一页的唯一主动作走设计系统那一颗组件。搬之前先量过：原来那颗 Material
            // `Button(containerColor = Primary)` 热区、角色、名字三项都达标，所以这一笔是
            // **归所有者，不是修缺陷**；可见高度仍是旧版定稿那一档 48dp（下面那颗「导入」
            // 与它并排，走同一档，两颗粒子才一般高）。
            LbPrimaryButton(
                state = LbButtonState.Idle,
                label = stringResource(R.string.kb_new_kb),
                onClick = onNewKb,
                modifier = Modifier.weight(1f)
            )
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
        LbDialog(
            title = "删除知识库「${kb.displayName}」？",
            onDismissRequest = { pendingDelete = null },
            // 旧文案里"物理删除"是给我们读代码的人用的词，用户只需要知道回不回来得了
            message = "删了就找不回来了。",
            confirm = LbDialogAction(
                label = "删除",
                tone = LbDialogActionTone.Destructive,
                onClick = {
                    val n = kb.name
                    pendingDelete = null
                    onDelete(n)
                }
            ),
            dismiss = LbDialogAction("取消", { pendingDelete = null },
                tone = LbDialogActionTone.Muted)
        )
    }

    // 导出警示 Compose 化：确认链路上提至此，弹窗由 KbListScreen pendingExport 承载。
    // 只留"要防的那件事"一句——旧文案把档案里装了哪几格（画像/归档/谈心记录）报了一遍，
    // 那是给我们的结构说明，不是给用户的选择信息。
    pendingExport?.let { kb ->
        LbDialog(
            title = "导出提醒",
            onDismissRequest = { pendingExport = null },
            message = "导出文件是明文，别发给别人。",
            confirm = LbDialogAction(
                label = "仍要导出",
                onClick = {
                    val n = kb.name
                    pendingExport = null
                    onConfirmExport(n)
                }
            ),
            dismiss = LbDialogAction("取消", { pendingExport = null },
                tone = LbDialogActionTone.Muted)
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
        // 阴影统一收进 2/4 令牌（6→4 为唯一超限修正）
        // 点卡片主体 = 激活（非当前库时），与供应商行交互一致
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)
            .clickable(enabled = !isActive, onClick = onActivate)
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            // 第一行：名称 + 重命名笔 + 当前使用徽章 + 删除
            //
            // `height(IntrinsicSize.Min)` 是这一行的**版式**地板：行高只由名字那一行字给
            // （v1.3.1 量到的就是这一档），而这一行里的两颗小动作各自 `fillMaxHeight()`
            // 把整行高吃成自己的热区。热区买的是"已经存在的那 22dp"，不是新要的一分钟高度。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // 改名那颗：可见的仍然只有那一行字 + 一颗 14dp 铅笔。
                    // 把两条边都垫到 48，于是整张卡片的第一行凭空高了一截——
                    // 用户点的正是"卡片变大了"这一条。现在热区只补**横向余量**与**行高**，
                    // 两者都是透明的：没有底色、没有边框，屏幕上看不见任何新东西。
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                        .clickable(role = Role.Button) {
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
                // 删除那颗：**画**是 18dp 图标，**点**是这一整颗透明盒（行高 + 全局下限那一档的宽，
                // 即 `AppDimens.TOUCH_TARGET_MIN_DP`，与上面改名那颗同一把尺）。
                // `indication = null` 且没有底色，所以这颗盒在屏幕上不存在，只是手指够得着。
                // role 仍挂在带 clickable 的这一层自己身上（旧写法把 clickable 直接挂在 Icon 上，
                // 读屏念的是"删除知识库，图像"而不是一个动作）。
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onDelete
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除知识库",
                        tint = TextHint,
                        modifier = Modifier.size(AppDimens.ACTION_ICON_SIZE_DP.dp)
                    )
                }
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
        LbDialog(
            title = "修改显示名",
            onDismissRequest = { showRename = false },
            body = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("显示名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirm = LbDialogAction(
                label = "保存",
                // 空名禁止保存，避免卡片标题变空白
                enabled = renameText.isNotBlank(),
                onClick = {
                    showRename = false
                    onRename(renameText.trim())
                }
            ),
            dismiss = LbDialogAction("取消", { showRename = false },
                tone = LbDialogActionTone.Muted)
        )
    }
}
