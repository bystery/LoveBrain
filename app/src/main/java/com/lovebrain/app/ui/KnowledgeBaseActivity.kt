package com.lovebrain.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.designsystem.LbDialog
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.model.KnowledgeBase
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
                KbEvent.RenameFailed -> feedback = "重命名失败，请重试"
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
    // 改名的待确认库与临时输入：与 pendingDelete/pendingExport 同一副"pending-X"格局
    // 收在页面层——卡本体因此只剩"把内容交给公共件"，体里不再自画任何形状（异形账本口径）。
    var pendingRename by remember { mutableStateOf<KnowledgeBase?>(null) }
    var renameText by remember { mutableStateOf("") }

    ScreenPage(title = "知识库管理", onBack = onBack) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            // 基线 v1.1 §3.3：F1 族列表间距 16→12，四页不许再各写各的（旧档 `spacedBy(Spacing.xl)` 作废）。
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
        // 四态只在这里出现一次。替换前这里是一张自造的 40 行空态卡（图标 + 标题 + 一句
        // "点下方「新建知识库」"的指路文字），版式与反馈案例页、供应商区各不相同。
        LbAsyncState(screenState) { shown ->
            // LbAsyncState.Content 的 slot 是 Box——多张卡必须在此处给纵向布局，
            // 否则 forEach 画出的卡会叠在 Box 同一位置（指导书§6 P0 叠放回归根因）。
            // ProviderSection.kt 同一族也是自己在这里包 Column。
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                shown.knowledgeBases.forEach { kb ->
                    KbCard(
                        kb = kb,
                        isActive = kb.name == shown.activeName,
                        onActivate = { onActivate(kb.name) },
                        onRename = {
                            renameText = kb.displayName
                            pendingRename = kb
                        },
                        onEdit = { onEdit(kb.name) },
                        onExport = { pendingExport = kb },
                        onDelete = { pendingDelete = kb }
                    )
                }
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
    // 改名确认：文案与判据一字未动（原挂在卡本体内），只是随"卡本体收成委托壳"
    // 上提到页面层，与删除/导出两扇确认走同一副格局。
    pendingRename?.let { kb ->
        LbDialog(
            title = "修改显示名",
            onDismissRequest = { pendingRename = null },
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
                    val n = kb.name
                    val newName = renameText.trim()
                    pendingRename = null
                    onRename(n, newName)
                }
            ),
            dismiss = LbDialogAction("取消", { pendingRename = null },
                tone = LbDialogActionTone.Muted)
        )
    }
}

/**
 * 知识库这一条的卡 = [LbListCard] 本身（基线 v1.1 §3.6；母版页从今天起也**只交内容**，
 * 卡底形状、字阶、行数上限、动作写法全部归公共件——"母版"不再是"那个还在自己画卡的页面"）。
 *
 * 五个槽每一颗都有真源，没有一颗是编出来的：
 * - `title`：库的显示名（单行 + ellipsis 由公共件钉，页面传不进 `maxLines`）；
 * - `status`：只有当前在用的库有这一槽（旧档那颗"当前使用"是 `background(PrimaryLight)` 自画徽章，
 *   §3.4 之后静息结构归描边、状态归状态槽：6dp 点 + `bodySmall`12 字同一行）；
 * - `meta`：「阶段：X」与「已对话 N 轮」交**两段**，`｜` 分隔符的主人是 `lbMetaLine`（在公共件里），
 *   页面不再拼整句、也不再并排两颗 `Text`；
 * - `actions`：重命名 / 编辑 / 导出恰好三颗次级胶囊（§3.6 动作行上限）。「编辑」「导出」「重命名」
 *   三个词接上仓里**已有**的 `a11y_action_edit` / `a11y_action_export` / `a11y_action_rename`
 *   （中英两份都在盘上，此前只有读屏在用），不是新文案；
 * - `trailingIcon`：删除从动作行挪到顶右角的垃圾桶图标。语气仍恒走 [LbTextActionTone.Destructive]，
 *   热区与角色归 [LbTextAction] 那唯一一处主人（公共件这一槽只给落点，不画 clickable），
 *   名字接仓里**已有**的 `a11y_action_delete`；点了走页面层那扇既有删除确认弹窗（`pendingDelete`）。
 * - `onClick`：点整卡 = 激活（与旧交互一致）；当前在用的库没有"再激活一次"这件事，交 `null`，
 *   公共件此时不挂 `Role.Button`（旧档靠 `clickable(enabled = !isActive)` 压死一颗活按钮）。
 *
 * ⚠ 改之前「重命名」是动作行装不下的第四颗，挂在 `detail` 槽（理由见 2026-10-06-L1b 接线单）。
 * 这一轮把删除挪去顶右角、动作行让位给 rename/edit/export 三颗次级胶囊：`detail` 槽退场，
 * 「⋯ 溢出档」那条债随这一改一并还清——四件事都在屏上、且都走动作行/尾图标这两条有主人的路。
 */
@Composable
private fun KbCard(
    kb: KnowledgeBase,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    LbListCard(
        title = kb.displayName,
        status = if (isActive) LbListCardStatus(
            // 三颗读数都走资源：英文环境里这一行以前念的是中文（字面量预算那一栏的债）。
            stringResource(R.string.kb_card_in_use), LbRowState.Ready
        ) else null,
        meta = listOf(
            stringResource(R.string.kb_card_stage, kb.stage),
            stringResource(R.string.kb_card_turns, kb.turnCount)
        ),
        actions = listOf(
            LbListCardAction.secondary(stringResource(R.string.a11y_action_rename), onRename),
            LbListCardAction.secondary(stringResource(R.string.a11y_action_edit), onEdit),
            LbListCardAction.secondary(stringResource(R.string.a11y_action_export), onExport)
        ),
        onClick = if (isActive) null else onActivate,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            // 删除走顶右角的垃圾桶图标（动作行让位给 rename/edit/export 三颗次级胶囊）：
            // 语气仍恒 destructive（红），热区与角色归 LbTextAction 那唯一一处主人，
            // 名字接 a11y_action_delete；点了走页面层那扇既有删除确认弹窗。
            LbTextAction(
                icon = Icons.Filled.Delete,
                description = stringResource(R.string.a11y_action_delete),
                onClick = onDelete,
                tone = LbTextActionTone.Destructive
            )
        }
    )
}
