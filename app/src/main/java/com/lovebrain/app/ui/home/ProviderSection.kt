package com.lovebrain.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipLabelAlignment
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.LbRowState
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.ui.common.RowActionButton
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionGlyph
import com.lovebrain.app.core.designsystem.LbTextActionTone
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

/** 供应商这一屏自己的版式尺寸——都不是热区下限，下限一律指回 `AppDimens.TOUCH_TARGET_MIN_DP` */
private object ProviderDimens {
    const val STATUS_DOT_SIZE_DP = 6
    /** 「＋ 添加供应商」那一行的最小可点击边界 */
    const val ADD_ROW_MIN_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP
    const val FEATURE_ARROW_SIZE_DP = 20

    /** 思考模式那颗胶囊的**视觉**尺寸：轨道 36×20、球 16、球离轨边 2（可点那一层仍是全局下限） */
    const val SWITCH_TRACK_WIDTH_DP = 36
    const val SWITCH_TRACK_HEIGHT_DP = 20
    const val SWITCH_THUMB_SIZE_DP = 16
    const val SWITCH_THUMB_INSET_DP = 2

    /** 超时那一排四颗胶囊的**视觉**高度：四档要挤在同一行里，热区由分层外盒给 */
    const val TIER_PILL_HEIGHT_DP = 24

    /** 旧表单滚动柱写死的那一档：现在只当**封顶**，不再当作实际可用高度 */
    const val FORM_SCROLL_MAX_HEIGHT_DP = 520

    /** 浮层里除表单以外要扣掉的开销：标题、上下内边距、保存那一行与一点余量 */
    const val FORM_CHROME_HEIGHT_DP = 160

    /** 再矮也要留给表单的可见高度——低于这一档，滚动本身也救不了这个表单 */
    const val FORM_MIN_VIEWPORT_HEIGHT_DP = 240
}

/**
 * 表单那一柱最多能长多高：从窗口给的高度里扣掉浮层以外的开销，再与旧的那一档上限取小。
 *
 * 旧版是一句死档 `heightIn(max = 520.dp)`。那一档在矮窗口、小窗与键盘弹起之后**高出实际可用
 * 高度**，于是"整个表单能滚"退化成"底部被裁掉"；现在 520 只当封顶，可用高度不够就跟着降，
 * 并且不低于 [ProviderDimens.FORM_MIN_VIEWPORT_HEIGHT_DP] 那一档，免得键盘把表单压没。
 */
@Composable
private fun providerFormViewportCap(): Dp {
    val available = LocalConfiguration.current.screenHeightDp.dp
    return (available - ProviderDimens.FORM_CHROME_HEIGHT_DP.dp)
        .coerceAtLeast(ProviderDimens.FORM_MIN_VIEWPORT_HEIGHT_DP.dp)
        .coerceAtMost(ProviderDimens.FORM_SCROLL_MAX_HEIGHT_DP.dp)
}

/**
 * 模型供应商管理页——列表、展开、添加、编辑、删除与连接测试。
 *
 * 页头与状态源留在这一层；「当前供应商摘要 + 展开后的工单列表」那一格抽成
 * [ProviderManageEntry]，悬浮窗齿轮那扇整窗设置页复用的是同一颗、同一个 `SetupViewModel`
 * 状态源——两处都不写第二份工单账，也不各画一套编辑入口。
 *
 * ## 这一页的外观从哪儿来
 *
 * 与已踩案例页、捕获范围页、知识库页同一副壳：[ScreenPage] 那一族（页头 + 内容边距）+
 * 一张白卡装着两行摘要与行内紧凑动作（[RowActionButton]）。改之前这一页自己拼
 * `LbScreenScaffold` + `LbTopBar(title = "模型供应商")`，页名是内联中文（英文环境念中文）；
 * 现在页名走 `provider_page_title` 一条资源。
 * ⚠ 首页那一格入口卡片的标题现在仍写在  那一边，两边要收成同一条资源（见交接报告）。
 *
 * ## 这次删掉的是"讲给用户听的开发者话"，不是配置项
 *
 * 超时那一排只留标题与四档胶囊，**删掉那句解释读超时/连接超时原理的话**——
 * 四个档位、Key、地址、模型、思考模式、连接测试这些真配置一个都没少。
 *
 * ## 组合期不碰网络也不碰磁盘
 *
 * 这一层只渲染 `SetupViewModel` 交出来的 StateFlow；连接测试那一步在
 * `scope.launch` 里（见 [ProviderFormBody]），页面上没有任何同步 IO。
 */
@Composable
fun ProviderSection(viewModel: SetupViewModel, onBack: () -> Unit) {
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val activeTicket by viewModel.activeTicket.collectAsStateWithLifecycle()
    val providerReady by viewModel.providerReady.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderTicket?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProviderTicket?>(null) }

    // 页头、整屏底色与水平边距都交回 `ScreenPage` 那一族（与知识库两页、捕获范围页、
    // 已踩案例页同一副）；页名走资源，不再在这一页里内联一份中文。
    ScreenPage(title = stringResource(R.string.provider_page_title), onBack = onBack) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ProviderManageEntry(
                tickets = tickets,
                activeTicket = activeTicket,
                providerReady = providerReady,
                onActivate = { viewModel.activateTicket(it) },
                onEdit = { editing = it },
                onDelete = { pendingDelete = it },
                onAdd = { showAdd = true }
            )
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
 * 「当前供应商摘要 + 展开后的工单列表」那一格——**形状的主人只有这一处**。
 *
 * 抽出来的动机是复用，不是拆碎：悬浮窗齿轮那扇整窗设置页要放的就是同一段外观
 * （1.3.1 的那张摘要卡与那一列工单行），而它读写的仍然是调用方交出来的同一份工单快照，
 * 保存/删除/切换启用一律走首页那三个既有的出口——这一格自己**不做任何业务判断**。
 *
 * ⚠ 抽取是**逐字搬容器**：Card 的那条 Modifier 链、状态点、chevron、`LbAsyncState` 判据、
 * 行底色与那颗「＋ 添加供应商」全部原样，只把三处 `showAdd = true` / `editing = t` /
 * `pendingDelete = t` 换成同义的回调。语义树里每一颗节点、每一个 48dp 热区与搬家前相同
 * （证人：`LongProviderNameSemanticsTest`、`ProviderSectionSemanticsTest`）。
 * `expanded` 只有这一格在读，所以它与 chevron 那段动画跟着一起搬进来——首页那层
 * 因此少一颗杂散布尔，而展开/收起这件事仍然只有这一格知道。
 */
@Composable
internal fun ProviderManageEntry(
    tickets: List<ProviderTicket>,
    activeTicket: ProviderTicket?,
    providerReady: Boolean,
    onActivate: (String) -> Unit,
    onEdit: (ProviderTicket) -> Unit,
    onDelete: (ProviderTicket) -> Unit,
    onAdd: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        if (expanded) 90f else 0f,
        label = "providerChevron"
    )

    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            // 第6节第5条 第一行要的是"可点边界真实 ≥48dp"。这一颗以前由内容排出来：
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
                // 第6节第1条：点的"就绪 ↔ 颜色"这一对归 LbRowState（它的 KDoc 点名
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
                        // 长模型名在这一行只省略，不撑卡：整列已经被 weight(1f) 约束住，
                        // 想看清全名去编辑那一栏（那里逐字给全，见 ProviderFormBody 的模型行）
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                // 第6节第3条：空 / 有内容两格由同一个 ScreenState 判定，由 LbAsyncState 画。
                // 上一版是 if/else 两边各画一次（空态那格虽然已经用了统一组件，
                // 但"哪一格"仍是这一页自己判的）；现在判定只有一处，版式也只有一处。
                val listState: ScreenState<List<ProviderTicket>> =
                    if (tickets.isEmpty()) {
                        ScreenState.Empty(
                            message = stringResource(R.string.provider_empty),
                            action = ScreenAction(stringResource(R.string.provider_add)) { onAdd() }
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
                                    .clickable { onActivate(t.id) }
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        t.model.ifBlank { "未配置模型" },
                                        style = AppTypography.labelSmall,
                                        color = TextHint,
                                        // 同上：列表行里长模型名只省略，卡不被它撑开
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                RowActionButton("编辑") { onEdit(t) }
                                Spacer(Modifier.width(Spacing.sm))
                                RowActionButton("删除", tint = Error) { onDelete(t) }
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
                        .clickable(role = Role.Button) { onAdd() }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                )
            }
        }
    }
}

/**
 * 「添加 / 编辑供应商」那扇浮层——第6节第1条 归并：形状归设计系统的 `LbDialog`，这一层只转参数。
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
internal fun ProviderEditDialog(
    viewModel: SetupViewModel,
    ticket: ProviderTicket?,
    onDismiss: () -> Unit
) {
    LbDialog(
        title = if (ticket == null) "添加供应商" else "编辑供应商",
        onDismissRequest = onDismiss,
        body = {
            // 这一扇是系统对话框窗口：键盘弹起来只动这一扇自己的可见高度，
            // 所以表单柱要自己让位（悬浮窗里那一格由宿主的滚动柱统一让位，见 [ProviderFormBody] 上那颗参数）。
            ProviderFormBody(
                viewModel = viewModel,
                ticket = ticket,
                onDismiss = onDismiss,
                keyboardAwareViewport = true
            )
        }
    )
}

/**
 * 供应商表单本体——`ProviderEditDialog` 那扇浮层里、**除了外壳与标题之外**的全部内容。
 *
 * 标题（「添加供应商 / 编辑供应商」）原来画在这一格的第一行，第6节第1条 归并时抬进了
 * `LbDialog` 的 `title=` 槽，这里**不留第二份**——证人见 `ProviderDialogMergeTest`。
 *
 * 原来这 190 行直接写在 Dialog 里面，于是这台仪器里量不到：`Dialog` 开的是独立窗口，
 * 窗口里只要有文本框拿焦点，`waitForIdle` 就永不返回（账本 第45节第1条 用一次 15 行的诊断
 * 把这条边界钉死——裸 `Dialog` + 一颗 `OutlinedTextField` 同样跑满 60 秒不空闲，
 * 所以**不是**这里哪颗控件画坏了）。抽出来之后 Dialog 仍在原位、仍在原外壳里，
 * 只是测量可以绕开那扇窗口直接挂这一格。
 *
 * ⚠ 抽的是**容器**，不是"顺手重构"：状态声明、`commitModelInput`/`deleteModel`
 * 与 Column 里的每一行都逐字搬过来（只减缩进）。Dialog 关闭即销毁这些 `remember`
 * 的行为跟搬之前一样——它们仍挂在这棵树的同一个位置上。
 */
@Composable
internal fun ProviderFormBody(
    viewModel: SetupViewModel,
    ticket: ProviderTicket?,
    onDismiss: () -> Unit,
    /**
     * 键盘让位这一档由**宿主**决定，不是外观旋钮：
     * 系统对话框（[ProviderEditDialog]）里表单柱自己就是那扇窗口的正文，键盘弹起时要自己缩；
     * 悬浮窗里那一格（`SettingsProviderEntry`）画在宿主自己的滚动柱里，宿主已经统一接过键盘让位，
     * 这里再让一次就是同一段空白扣两遍。默认关，保持悬浮窗那一份逐像素不动。
     */
    keyboardAwareViewport: Boolean = false
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
            .heightIn(max = providerFormViewportCap())
            .verticalScroll(rememberScrollState())
            // 键盘让位只在"这一扇自己就是窗口正文"的那条路径上做（弹窗宿主传 true，见
            // [ProviderEditDialog]）。悬浮窗里那一格由宿主的滚动柱统一让位，
            // 这里再让一次就是把同一段空白扣两遍。
            .then(if (keyboardAwareViewport) Modifier.imePadding() else Modifier),
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
                // 这颗写着 heightIn(min = TOUCH_TARGET_MIN_DP)，但**它赢不了父约束**：它坐在
                // CompactInput 中层那颗 36dp 的可见胶囊里，maxHeight=36 把它压回 36（语义树实量
                // 58x36）。那句 min(48) 是一条永远不成立的死码，留着会让下一个人以为这颗已达标。
                // 现在如实按字段档走：这颗自己 36 高，"点得到"那一半由外层 48 热区整行承担
                // （点框内空档即获焦）。要让这颗自己回到 48，得把尾部槽从中层挪到外层那一级——
                // 那是全仓共用组件的改动（还要处理它与外层"点空档获焦"手势抢事件），不在这里偷偷做。
                TextButton(
                    onClick = { keyVisible = !keyVisible },
                    modifier = Modifier.heightIn(min = AppDimens.INPUT_ROW_HEIGHT_DP.dp)
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
        //
        // 这一族原来还有一行"只放宽读回复的时间，连接超时不变"的说明——那是参数原理，
        // 用户在这四颗里选一个决定不了任何下游行为，所以它属于要删的废话；
        // 真配置项（标题 + 四档）一个没动。
        Text(
            stringResource(R.string.provider_timeout_title),
            style = AppTypography.labelMedium,
            color = TextSecondary
        )
        // 互斥单选 ⇒ 设计系统那颗的 [LbChipInteraction.Single]：
        // Role.Tab + Selected 进语义树（读屏念得出"这是选项、现在在哪一档"）。
        // 四档挤同一行、每档 `weight(1f)` 分宽，与悬浮窗设置页那一排同一份档位源
        // （`GenerationTimeoutTier`，界面上没有自由输入）：这一族不再排成四块 48dp 见方的卡块。
        // 矮的是**视觉**那一层（[ProviderDimens.TIER_PILL_HEIGHT_DP]），可点那一层仍由
        // `layeredTouch` 垫到全局下限——本表单逐颗量尺（ProviderFormSemanticsTest）量的就是这一族。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxWidth()
        ) {
            GenerationTimeoutTier.options.forEach { tier ->
                LbChip(
                    label = stringResource(R.string.provider_timeout_tier_label, tier.seconds),
                    selected = tier.seconds == timeoutTier.seconds,
                    onClick = { timeoutTier = tier },
                    interaction = LbChipInteraction.Single,
                    style = timeoutTierChipStyle,
                    modifier = Modifier.weight(1f)
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
                        glyph = LbTextActionGlyph.Compact,
                        onClick = { commitModelInput(i) }
                    )
                    LbTextAction(
                        icon = Icons.Filled.Close,
                        description = stringResource(R.string.a11y_action_cancel),
                        tone = LbTextActionTone.Muted,
                        glyph = LbTextActionGlyph.Compact,
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
                        // 编辑这一栏**不截断**：列表那一行只省略，而"这条模型名到底是什么"
                        // 就要在这里看得见，所以让它自己换行排全（外层 weight(1f) 已经把宽度钉住）
                        modifier = Modifier.weight(1f)
                    )
                    // 行尾那四颗走设计系统的**紧凑档**（`LbTextActionGlyph.Compact` = 28dp 见方盒）：
                    // 行尾若是四颗 48dp 见方盒，一行先被它们占掉 192dp，模型名就没地方长了。
                    // 角色、`contentDescription` 与语气词表都还是那一颗组件给的，一寸没裁。
                    LbTextAction(
                        icon = Icons.Filled.Star,
                        description = stringResource(R.string.a11y_set_current_model),
                        tone = if (m == currentModel) {
                            LbTextActionTone.Accent
                        } else {
                            LbTextActionTone.Muted
                        },
                        glyph = LbTextActionGlyph.Compact,
                        onClick = {
                            currentModel = m
                            if (ticket != null) viewModel.setTicketModel(ticket.id, m)
                        }
                    )
                    LbTextAction(
                        icon = ImageVector.vectorResource(R.drawable.ic_unplug),
                        description = stringResource(R.string.a11y_test_connection),
                        tone = LbTextActionTone.Accent,
                        glyph = LbTextActionGlyph.Compact,
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
                        glyph = LbTextActionGlyph.Compact,
                        onClick = {
                            modelInput = m
                            editIndex = i
                        }
                    )
                    LbTextAction(
                        icon = Icons.Filled.Delete,
                        description = stringResource(R.string.a11y_action_delete),
                        tone = LbTextActionTone.Destructive,
                        glyph = LbTextActionGlyph.Compact,
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
                    glyph = LbTextActionGlyph.Compact,
                    onClick = { commitModelInput(-1) }
                )
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = stringResource(R.string.a11y_action_cancel),
                    tone = LbTextActionTone.Muted,
                    glyph = LbTextActionGlyph.Compact,
                    onClick = {
                        modelInput = ""; addingModel = false
                    }
                )
            }
        } else {
            // 实量 **79x22dp、role=无**：这一行只有 22dp 高，读屏也只念得出字、念不出按钮。
            // 仍是那条"热区与视觉分两层/垫本人"的修法——`clickable` 排在 `padding` 之前，
            // 否则内边距落在热区外面，等于白垫（同形缺陷在两块面板上量到 10 处，账本 第44节）。
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
            val noDetail = stringResource(R.string.provider_test_no_detail)
            Text(
                text = if (ok) {
                    stringResource(R.string.provider_test_success)
                } else {
                    stringResource(
                        R.string.provider_test_failed,
                        m,
                        redactSecrets(msg.orEmpty(), stringResource(R.string.secret_redacted))
                            .ifBlank { noDetail }
                    )
                },
                style = AppTypography.labelSmall,
                color = if (ok) Success else Error
            )
        }

        Spacer(Modifier.height(Spacing.xs))
        val saving by viewModel.saving.collectAsStateWithLifecycle()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextButton(
                onClick = onDismiss,
                // 这颗跟着表单密度收进 36 的时候，把**带 clickable 的自己**缩到了一颗输入行高度
                // （用的还是"输入行"那颗常量，档位也拿错了）——热区实测 160x36，比旁边那颗
                // 保存的 48 见方低一档，等于把这屏的退路做成最难点的那一颗。
                // 合同要的是"可见件别被撑大、可点区域仍要合理"，所以这里垫回热区下限，
                // 而不是把尺子改成 36 来认这个退化。这颗在表单底部的 Row 里，父约束没有压它，
                // 所以 min(48) 是真的生效（与字段框里那颗尾部按钮不同，见 CompactInput 那一处）。
                modifier = Modifier.weight(1f).heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            ) { Text("取消", style = AppTypography.labelLarge, color = TextSecondary) }
            // 第6节第1条 :479「页面唯一主动作」——这一屏的主动作是「保存」，它原来是一颗
            // 手写四态的 Material `Button(containerColor = Primary)`：`enabled` 里那条
            // 与号串 + `if (saving) CircularProgressIndicator` 各管一半状态。
            // 先量再搬（账本 第46节第2条）：**160x48dp、role=Button、有名字，全部达标**
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
 * 连接失败那一句里，异常文本上屏之前的最后一道 Key 遮蔽。
 *
 * 为什么要在这里兜一句：`testConnectionWithProbe` 把底层异常的 `message` 原样交回来
 * （数据层这一轮不动它），而"Key 不出现在异常提示/截图里"是这一屏的合同。
 * 判据只有一条——**长得像 Key 的那一串换成遮蔽词**，其余原样保留，
 * 因为用户在这里要看见的是"连不上还是地址写错了"，把整句抹成"出错了"等于少说一件事。
 *
 * 纯函数、不碰组合：正因为这一屏的语义树量不到颜色与文字内容，
 * 这条遮蔽只能这样留下来被直接判（用例见 `ProviderKeyRedactionTest`）。
 */
internal fun redactSecrets(text: String, hidden: String): String =
    SECRET_IN_TEXT.replace(text, hidden)

/** `sk-…` / `Bearer …` / `api_key=…` 这三族写法都收进同一条正则，只在这里出现一次 */
private val SECRET_IN_TEXT = Regex(
    """(?i)(sk-[A-Za-z0-9_\-.*]{6,}|Bearer[\s_][A-Za-z0-9_.\-]{6,}|api[_-]?key\s*[=:]\s*[^\s,;]+)"""
)

/**
 * 小型开关：≥48 见方的热区里画一颗 **36×20** 的胶囊，球 16dp、离轨边 2dp。
 *
 * 两条轴各归各的主人，别拧成一条（与 `CompactInput` 同一个写法）：
 * - **视觉**那一层是旧版那颗小胶囊：36×20 的轨道、16dp 的球、2dp 的边距。
 *   这一屏退回过一颗巨大的 Material `Switch`，也画过 48×32 的自画胶囊——
 *   前者比表单里任何一行都高，后者是把"热区不够"记在版式头上，两种都不作复刻对象。
 * - **热区**那一层是外面这颗带 `toggleable`、`Role.Switch` 与 `contentDescription` 的盒，
 *   两轴都垫到全局那颗下限（[AppDimens.TOUCH_TARGET_MIN_DP]）：胶囊画小了，
 *   手指点得到的范围不跟着缩。里面那颗胶囊自己不参与点击。
 *
 * 名字与"思考模式"那行字的配对由 [MiniSwitchRow] 那一处保证，这里只接现成的那句。
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
                .size(
                    width = ProviderDimens.SWITCH_TRACK_WIDTH_DP.dp,
                    height = ProviderDimens.SWITCH_TRACK_HEIGHT_DP.dp
                )   // 视觉胶囊，不参与点击（热区在外层那颗）
                .clip(LoveBrainShape.full)
                .background(if (checked) Primary else Neutral300.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(ProviderDimens.SWITCH_THUMB_INSET_DP.dp)
                    .size(ProviderDimens.SWITCH_THUMB_SIZE_DP.dp)
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
 * （第6节第5条 第②栏的口径：已有说明文字的让节点去指那句现成的话，不再编一份只给读屏看的副本）。
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

/**
 * 超时那一排四颗胶囊的形状档：**设计系统已有档位的参数微调**，不是在页面里再画一条链
 * （`LbChipStyles.soft` + 分层热区 + 钉死胶囊自己的高度），与悬浮窗设置页那一排同一形状。
 *
 * ⚠ 这一档与 `ui/panel/settings/SettingsAdvancedEntry.kt` 里那颗私有的
 * `timeoutTierChipStyle` 现在是**两份同数**：两处都不肯把形状交给对方（一处是弹窗、一处是正文），
 * 而设计系统又没有"超时档位"这一档。要收成一个主人，请在 `LbChipStyles` 里补一档，
 * 两边各自删掉自己那一份 copy——不是由这一屏替那一屏定。
 */
private val timeoutTierChipStyle = LbChipStyles.soft.copy(
    radius = LoveBrainShape.full,
    paddingHorizontal = Spacing.xs,
    paddingVertical = Spacing.xs,
    markSelectedWithCheck = false,
    layeredTouch = true,
    pillHeight = ProviderDimens.TIER_PILL_HEIGHT_DP.dp,
    labelAlignment = LbChipLabelAlignment.Center
)

