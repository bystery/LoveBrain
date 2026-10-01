package com.lovebrain.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbRowState
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.ui.common.RowActionButton
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionGlyph
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Neutral300
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.Success
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.designsystem.LbDialog
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.launch

/** 供应商管理页内部尺寸常量 */
private object ProviderDimens {
    const val STATUS_DOT_SIZE_DP = 6
    /** 添加供应商那一行的最小可点击边界——§6.5 的下限是 48×48dp */
    const val ADD_ROW_MIN_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP
    const val FEATURE_ARROW_SIZE_DP = 20
}

/**
 * 模型供应商管理页——列表、展开、添加、编辑、删除与连接测试。
 */
@Composable
fun ProviderSection(viewModel: SetupViewModel, onBack: () -> Unit) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val activeTicket by viewModel.activeTicket.collectAsStateWithLifecycle()
    val providerReady by viewModel.providerReady.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderTicket?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProviderTicket?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        if (expanded) 90f else 0f,
        label = "providerChevron"
    )

    // §6.1 :478：外框（整屏底色 + 统一水平边距）交回 `LbScreenScaffold`。
    // 改之前这一页自己拼 `Column(fillMaxSize).verticalScroll().padding(horizontal = xxxl)`，
    // 24dp 那一档与脚手架恰好同数却不是同一个所有者。insets 仍由 SetupRoot 那一层给
    // （`handlesSystemBarInsets` 留默认 false）；页头留在滚动柱子里（与首页同款"整页一起滚"）。
    LbScreenScaffold {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // §6.1 :479：同一族手拼页头，那颗返回钮的 contentDescription 实测是空串。
            LbTopBar(
                title = "模型供应商",
                level = LbTopBarLevel.Page,
                onBack = onBack
            )

            Card(
                shape = LoveBrainShape.lg,
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier
                    .fillMaxWidth()
                    // §6.5 第一行要的是"可点边界真实 ≥48dp"。这一颗以前由内容排出来：
                    // 默认字号下语义树量到 **46dp**（320/360/412 三档全 46、600 档 45），
                    // 到 1.3 倍字号才涨到 56 —— 也就是**平时就不达标**，不是窄屏才不达标。
                    // 数不抄第二份：指回全站唯一那一颗 `AppDimens.TOUCH_TARGET_MIN_DP`。
                    // 证人：`LongProviderNameSemanticsTest`（热区那一问在这一条路径上是开着的）。
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .clip(LoveBrainShape.lg)
                    .clickable { expanded = !expanded }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    ) {
                        // §6.1：点的"就绪 ↔ 颜色"这一对归 LbRowState（它的 KDoc 点名
                        // `if (providerReady) Primary else Neutral300` 就是它要替掉的旧写法）。
                        // 点的形状仍由这一处画——设计系统没有独立的 LbDot，唯一画这颗点的
                        // LbSettingRow 的行布局（点在尾、无 chevron）对不上这一行，所以只搬颜色、不搬形状。
                        Box(
                            modifier = Modifier
                                .size(ProviderDimens.STATUS_DOT_SIZE_DP.dp)
                                .clip(CircleShape)
                                .background(
                                    (if (providerReady) LbRowState.Ready else LbRowState.NotReady).color
                                )
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                activeTicket?.name ?: "未配置供应商",
                                style = AppTypography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                if (activeTicket == null) "点右侧展开添加"
                                else if (!providerReady) "配置不完整"
                                else activeTicket?.model?.ifBlank { "未选模型" } ?: "未选模型",
                                style = AppTypography.labelSmall,
                                color = TextHint,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(
                                        if (expanded) R.string.action_collapse else R.string.action_expand
                                    ),
                            tint = TextHint,
                            modifier = Modifier
                                .size(ProviderDimens.FEATURE_ARROW_SIZE_DP.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    if (expanded) {
                        HorizontalDivider(
                            thickness = AppDimens.BORDER_WIDTH_DP.dp,
                            color = Border.copy(alpha = 0.5f)
                        )
                        // §6.3：空 / 有内容两格由同一个 ScreenState 判定，由 LbAsyncState 画。
                        // 上一版是 if/else 两边各画一次（空态那格虽然已经用了统一组件，
                        // 但"哪一格"仍是这一页自己判的）；现在判定只有一处，版式也只有一处。
                        val listState: ScreenState<List<ProviderTicket>> =
                            if (tickets.isEmpty()) {
                                ScreenState.Empty(
                                    message = stringResource(R.string.provider_empty),
                                    action = ScreenAction(stringResource(R.string.provider_add)) { showAdd = true }
                                )
                            } else {
                                ScreenState.Content(tickets)
                            }
                        LbAsyncState(listState) { shownTickets ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                shownTickets.forEachIndexed { index, t ->
                                    val active = activeTicket?.id == t.id
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (active) PrimaryLight.copy(alpha = 0.5f) else Color.Transparent)
                                            .clickable { viewModel.activateTicket(t.id) }
                                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(ProviderDimens.STATUS_DOT_SIZE_DP.dp)
                                                .clip(CircleShape)
                                                .background(if (active) Primary else Color.Transparent)
                                                .border(
                                                    if (active) 0.dp else AppDimens.BORDER_WIDTH_DP.dp,
                                                    Neutral300,
                                                    CircleShape
                                                )
                                        )
                                        Spacer(Modifier.width(Spacing.md))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                t.name,
                                                style = AppTypography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                            Text(
                                                t.model.ifBlank { "未配置模型" },
                                                style = AppTypography.labelSmall,
                                                color = TextHint,
                                                maxLines = 1
                                            )
                                        }
                                        RowActionButton("编辑") { editing = t }
                                        Spacer(Modifier.width(Spacing.sm))
                                        RowActionButton("删除", tint = Error) { pendingDelete = t }
                                    }
                                    if (index < shownTickets.lastIndex) {
                                        HorizontalDivider(
                                            thickness = AppDimens.BORDER_WIDTH_DP.dp,
                                            color = Border.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                        // 空态已经有自己的添加动作了，这里不再摆第二个一模一样的入口
                        if (tickets.isNotEmpty()) Text(
                            stringResource(R.string.provider_add),
                            style = AppTypography.labelLarge,
                            color = Primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ProviderDimens.ADD_ROW_MIN_HEIGHT_DP.dp)
                                .clip(LoveBrainShape.md)
                                .clickable(role = Role.Button) { showAdd = true }
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        ProviderEditDialog(viewModel = viewModel, ticket = null, onDismiss = { showAdd = false })
    }
    editing?.let { t ->
        ProviderEditDialog(viewModel = viewModel, ticket = t, onDismiss = { editing = null })
    }

    pendingDelete?.let { t ->
        LbDialog(
            title = "删除「${t.name}」？",
            onDismissRequest = { pendingDelete = null },
            message = "删除后不可恢复，需要重新填写全部配置。确定？",
            confirm = LbDialogAction(
                label = "删除",
                tone = LbDialogActionTone.Destructive,
                onClick = { pendingDelete = null; viewModel.deleteTicket(t.id) }
            ),
            dismiss = LbDialogAction("取消", { pendingDelete = null }, tone = LbDialogActionTone.Muted)
        )
    }
}

/**
 * 「添加 / 编辑供应商」那扇浮层——§6.1 归并：形状归设计系统的 `LbDialog`，这一层只转参数。
 *
 * 改之前这里是自己画的两层壳（`Dialog` + `Card(shape = xl, containerColor = SurfaceCard)`），
 * 而标题画在表单本体里——于是「标题 + 一块卡片」在这一台仪器里有两种长法：
 * 对话框那一族走 `AlertDialog` 的 title 槽，这一颗是页面自己排的 `Text(titleLarge, SemiBold)`。
 * 现在标题进 `title=` 槽、表单进 `body=` 槽，**字符串一条都没新增也没少**
 * （只是从 Text 那一栏换到组件实参那一栏，账记在 `UiStringLiteralBudgetTest`）。
 *
 * 这一层壳**保留**、没有内联到两处调用点：`ProviderFormBody(` 的调用数被
 * `UiLayerDependencyContractTest` 的结构格钉着（除声明外只许一处），
 * 内联要么把表单挂两遍、要么再抽一层，都不比这只转参数的壳少。
 */
@Composable
private fun ProviderEditDialog(
    viewModel: SetupViewModel,
    ticket: ProviderTicket?,
    onDismiss: () -> Unit
) {
    LbDialog(
        title = if (ticket == null) "添加供应商" else "编辑供应商",
        onDismissRequest = onDismiss,
        body = {
            ProviderFormBody(viewModel = viewModel, ticket = ticket, onDismiss = onDismiss)
        }
    )
}

/**
 * 供应商表单本体——`ProviderEditDialog` 那扇浮层里、**除了外壳与标题之外**的全部内容。
 *
 * 标题（「添加供应商 / 编辑供应商」）原来画在这一格的第一行，§6.1 归并时抬进了
 * `LbDialog` 的 `title=` 槽，这里**不留第二份**——证人见 `ProviderDialogMergeTest`。
 *
 * 原来这 190 行直接写在 Dialog 里面，于是这台仪器里量不到：`Dialog` 开的是独立窗口，
 * 窗口里只要有文本框拿焦点，`waitForIdle` 就永不返回（账本 §45.1 用一次 15 行的诊断
 * 把这条边界钉死——裸 `Dialog` + 一颗 `OutlinedTextField` 同样跑满 60 秒不空闲，
 * 所以**不是**这里哪颗控件画坏了）。抽出来之后 Dialog 仍在原位、仍在原外壳里，
 * 只是测量可以绕开那扇窗口直接挂这一格。
 *
 * ⚠ 抽的是**容器**，不是"顺手重构"：状态声明、`commitModelInput`/`deleteModel`
 * 与 Column 里的每一行都逐字搬过来（只减缩进）。Dialog 关闭即销毁这些 `remember`
 * 的行为跟搬之前一样——它们仍挂在这棵树的同一个位置上。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderFormBody(
    viewModel: SetupViewModel,
    ticket: ProviderTicket?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val formError by viewModel.formError.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(ticket?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(ticket?.baseUrl.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf((ticket?.thinkingMode ?: viewModel.globalThinking) == 1) }
    // 生成超时档位：初值走白名单那道回落（老数据 / null → 默认档 120 秒），
    // 与读取侧 `ProviderConfigResolver` 同一个函数，界面上因此不可能显示出一个下游不认的档位。
    var timeoutTier by remember {
        mutableStateOf(GenerationTimeoutTier.fromSecondsOrDefault(ticket?.generateTimeoutSec))
    }
    var models by remember { mutableStateOf(ticket?.models.orEmpty()) }
    var currentModel by remember { mutableStateOf(ticket?.model.orEmpty()) }

    var addingModel by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf("") }
    var editIndex by remember { mutableStateOf(-1) }
    var testingModel by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Triple<String, Boolean, String?>?>(null) }

    fun commitModelInput(index: Int) {
        val m = modelInput.trim()
        if (m.isBlank()) return
        val newList = if (index >= 0) {
            models.toMutableList().also { it[index] = m }
        } else if (m !in models) {
            models + m
        } else models
        models = newList
        if (currentModel.isBlank()) currentModel = m
        modelInput = ""
        addingModel = false
        editIndex = -1
    }

    fun deleteModel(index: Int) {
        val removed = models[index]
        val newList = models.toMutableList().also { it.removeAt(index) }
        models = newList
        if (currentModel == removed) currentModel = newList.firstOrNull().orEmpty()
    }

    Column(
        modifier = Modifier
            .padding(Spacing.xl)
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("供应商名称", style = AppTypography.labelMedium, color = TextSecondary)
        CompactInput(value = name, onValueChange = { name = it }, placeholder = "名称")

        Text("接口地址（自动补全）", style = AppTypography.labelMedium, color = TextSecondary)
        CompactInput(value = baseUrl, onValueChange = { baseUrl = it }, placeholder = "https://api.example.com")
        if (!formError.isNullOrEmpty()) {
            Text("✗ $formError", style = AppTypography.labelSmall, color = Error)
        }

        Text("API Key", style = AppTypography.labelMedium, color = TextSecondary)
        CompactInput(
            value = key,
            onValueChange = { key = it },
            placeholder = if (ticket != null && viewModel.getKeyMask(ticket.id).isNotEmpty()) "留空保留原 Key" else "sk-…",
            passwordVisible = keyVisible,
            trailingAction = {
                // 这颗原来实量 **58x40dp**：M3 的 `TextButton` 把"至少 48dp"做成了一层
                // `minimumInteractiveContainer` 装饰，**带点击语义的那一颗自己还是 40dp**
                // ——手指与读屏信的都是后者（:531）。所以要给它本人垫高度。
                TextButton(
                    onClick = { keyVisible = !keyVisible },
                    modifier = Modifier.heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                ) {
                    Text(if (keyVisible) "隐藏" else "显示", style = AppTypography.bodySmall, color = Primary)
                }
            }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 那行字与开关的名字由 `MiniSwitchRow` 一处配对好——
            // 之前这两个东西散在调用点，"忘了给开关起名"在界面上一模一样、
            // 只有读屏的时候才看得出来。
            MiniSwitchRow(checked = thinking, onCheckedChange = { thinking = it })
        }

        // ── 高级：这一张工单愿意等多久 ──────────────────────────────────────────
        //
        // 档位挂在**这张工单**上，不是全局一个值：官方 Key 与自建慢服务各留各的等待预算。
        // 初值直接读传进来的 `ticket`（表单已有的那份工单快照），于是这台机器既不多一份状态、
        // 也不碰 `viewModel.tickets`（这颗 StateFlow 在本表单里从来没被读过）。
        //
        // ⚠ 四档是**白名单**，界面上没有输入框：用户能选的只有这四颗，
        //    非法值与老数据在 `GenerationTimeoutTier.fromSecondsOrDefault` 那一行落回默认档。
        Text(
            stringResource(R.string.provider_timeout_title),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )
        Text(
            stringResource(R.string.provider_timeout_hint),
            style = AppTypography.labelSmall,
            color = TextHint
        )
        // 互斥单选 ⇒ 设计系统那颗的 [LbChipInteraction.Single]：
        // Role.Tab + Selected 进语义树（读屏念得出"这是选项、现在在哪一档"），
        // 48dp 见方热区由 LbChipStyle.touchFloor 垫在**可点那颗自己身上**——
        // 本表单的逐颗量尺（ProviderFormSemanticsTest）量的就是这一族，自己画一条链会当场红。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            GenerationTimeoutTier.options.forEach { tier ->
                LbChip(
                    label = stringResource(R.string.provider_timeout_tier_label, tier.seconds),
                    selected = tier.seconds == timeoutTier.seconds,
                    onClick = { timeoutTier = tier },
                    interaction = LbChipInteraction.Single
                )
            }
        }

        Text("模型列表", style = AppTypography.labelMedium, color = TextSecondary)
        models.forEachIndexed { i, m ->
            if (editIndex == i) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CompactInput(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        placeholder = "模型名称",
                        modifier = Modifier.weight(1f)
                    )
                    LbTextAction(
                        icon = Icons.Filled.Check,
                        description = stringResource(R.string.a11y_action_confirm),
                        onClick = { commitModelInput(i) }
                    )
                    LbTextAction(
                        icon = Icons.Filled.Close,
                        description = stringResource(R.string.a11y_action_cancel),
                        tone = LbTextActionTone.Muted,
                        onClick = {
                            modelInput = ""; editIndex = -1
                        }
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LoveBrainShape.md)
                        .background(SurfaceInset)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                ) {
                    Text(
                        m,
                        style = AppTypography.bodyMedium,
                        color = if (m == currentModel) PrimaryDark else TextPrimary,
                        fontWeight = if (m == currentModel) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    LbTextAction(
                        icon = Icons.Filled.Star,
                        description = stringResource(R.string.a11y_set_current_model),
                        tone = if (m == currentModel) {
                            LbTextActionTone.Accent
                        } else {
                            LbTextActionTone.Muted
                        },
                        onClick = {
                            currentModel = m
                            if (ticket != null) viewModel.setTicketModel(ticket.id, m)
                        }
                    )
                    LbTextAction(
                        icon = ImageVector.vectorResource(R.drawable.ic_unplug),
                        description = stringResource(R.string.a11y_test_connection),
                        tone = LbTextActionTone.Accent,
                        onClick = {
                            testingModel = m
                            testResult = null
                            scope.launch {
                                val t = ticket ?: ProviderTicket(name = name.ifBlank { "未命名" }, baseUrl = baseUrl, model = m, models = models)
                                val result = viewModel.testConnection(t, m, key.trim())
                                testingModel = null
                                testResult = Triple(m, result.success, result.message)
                            }
                        }
                    )
                    LbTextAction(
                        icon = Icons.Filled.Edit,
                        description = stringResource(R.string.a11y_action_edit),
                        onClick = {
                            modelInput = m
                            editIndex = i
                        }
                    )
                    LbTextAction(
                        icon = Icons.Filled.Delete,
                        description = stringResource(R.string.a11y_action_delete),
                        tone = LbTextActionTone.Destructive,
                        onClick = { deleteModel(i) }
                    )
                }
            }
            if (testingModel == m) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.md)) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(AppDimens.LOADING_SPINNER_SIZE_DP.dp), strokeWidth = Spacing.xs)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("测试中…", style = AppTypography.labelSmall, color = TextHint)
                }
            }
        }
        if (addingModel) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CompactInput(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    placeholder = "模型名称",
                    modifier = Modifier.weight(1f)
                )
                LbTextAction(
                    icon = Icons.Filled.Check,
                    description = stringResource(R.string.a11y_action_confirm),
                    onClick = { commitModelInput(-1) }
                )
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = stringResource(R.string.a11y_action_cancel),
                    tone = LbTextActionTone.Muted,
                    onClick = {
                        modelInput = ""; addingModel = false
                    }
                )
            }
        } else {
            // 实量 **79x22dp、role=无**：这一行只有 22dp 高，读屏也只念得出字、念不出按钮。
            // 仍是那条"热区与视觉分两层/垫本人"的修法——`clickable` 排在 `padding` 之前，
            // 否则内边距落在热区外面，等于白垫（同形缺陷在两块面板上量到 10 处，账本 §44）。
            Text(
                "＋ 添加模型",
                style = AppTypography.labelLarge,
                color = Primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(LoveBrainShape.md)
                    .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                    .clickable(role = Role.Button) { addingModel = true }
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }
        testResult?.let { (m, ok, msg) ->
            Text(
                if (ok) "✓ 连接成功，接口已自动补全" else "✗ $m 连接失败：${msg ?: "请检查配置"}",
                style = AppTypography.labelSmall,
                color = if (ok) Success else Error
            )
        }

        Spacer(Modifier.height(Spacing.xs))
        val saving by viewModel.saving.collectAsStateWithLifecycle()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(AppDimens.INPUT_ROW_HEIGHT_DP.dp)
            ) { Text("取消", style = AppTypography.labelLarge, color = TextSecondary) }
            // §6.1 :479「页面唯一主动作」——这一屏的主动作是「保存」，它原来是一颗
            // 手写四态的 Material `Button(containerColor = Primary)`：`enabled` 里那条
            // 与号串 + `if (saving) CircularProgressIndicator` 各管一半状态。
            // 先量再搬（账本 §46.2）：**160x48dp、role=Button、有名字，全部达标**
            // ⇒ 这一处又是**归所有者，不是修缺陷**；搬的收益是"非法组合从此不可表达"
            // （原来 `saving = true` 同时字段没填完，Material 那颗会既转圈又灰着，
            // 而现在 `when` 只有条出口）。
            // ⚠ 两处**有意的改变**要认下来：
            //   1) 标签样式从 `labelLarge` 变成那颗共用的 `titleMedium + Bold`——
            //      这是"同一语义只长一个样"要的结果，但**本机没有截图证据**（:538 照旧欠着）；
            //   2) `Loading` 那一档在设计系统里的原意是"点它=停止"，表单里**没有可停的活**，
            //      所以点击必须自己吞掉：`if (saving) return@LbPrimaryButton` 那种"看着能按"
            //      不能留，故 onClick 里显式再判一次 saving。
            LbPrimaryButton(
                state = when {
                    saving -> LbButtonState.Loading
                    !(name.isNotBlank() && baseUrl.isNotBlank() && models.isNotEmpty() &&
                        (ticket != null || key.isNotBlank())) -> LbButtonState.Disabled
                    else -> LbButtonState.Idle
                },
                label = stringResource(if (ticket == null) R.string.provider_save else R.string.provider_save_changes),
                onClick = {
                    if (!saving) {
                        scope.launch {
                            val thinkingInt = if (thinking) 1 else 0
                            val success = viewModel.saveTicketWithProbe(
                                ticket?.id, name, baseUrl, models, key.trim(), thinkingInt,
                                timeoutTier.seconds
                            )
                            if (success) onDismiss()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 小型开关：48×48 的热区里画一颗 48×32 的胶囊。
 *
 * 这句注释原来写的是"触摸区扩大到 48×32，**满足** 48dp 无障碍下限"——那句是假的，
 * 而现在有实测了：直接挂载量语义树，那颗 `toggleable` 报 **48x32dp**（§6.5 :531 要
 * `bounds ≥48×48`）。同一次测量还报出第二个问题：**它没有任何可读名字**
 * （`「」`、role=无）——"思考模式"那四个字是旁边的另一个节点，眼睛看得见配对，
 * 读屏只念得出"开关"。
 *
 * 修法是这一格前面几颗已经用过两次的那条：**热区与视觉分两层**。
 * 外面这颗 ≥48 见方的盒带 `toggleable`、`Role.Switch` 与 `contentDescription`；
 * 里面那颗 48×32 只是画出来的胶囊（它不再参与点击，所以那处内联 `48.dp` 留在
 * "48 只写一次"那把闸的白名单里，仍然标着"这里是版式尺寸"）。
 */
@Composable
internal fun MiniSwitch(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 32.dp)   // 视觉胶囊，不参与点击（热区在外层那颗）
                .clip(LoveBrainShape.full)
                .background(if (checked) Primary else Neutral300.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * "思考模式"那一行：标签 + 开关，**配对由这一处保证**。
 *
 * 原来这两样散在调用点：`Text("思考模式")` 画在左边，右边那颗开关一个名字都没有
 * （语义树实量：`「」 role=无 48x32dp`）。眼睛看得见配对，读屏看不见——
 * 而"忘了给开关起名"在界面上跟配好了**一模一样**，只有读屏的时候才看得出来。
 * 所以把它收成一格，屏幕上那行字与开关的 `contentDescription` 共用同一条资源
 * （§6.5 第②栏的口径：已有说明文字的让节点去指那句现成的话，不再编一份只给读屏看的副本）。
 */
@Composable
internal fun MiniSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val label = stringResource(R.string.provider_thinking_mode)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = AppTypography.labelMedium, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        MiniSwitch(checked = checked, label = label, onCheckedChange = onCheckedChange)
    }
}

