package com.lovebrain.app.ui.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.R
import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.ProactiveOption
import kotlinx.coroutines.delay
import com.lovebrain.app.ui.panel.counseling.CounselingPanel
import com.lovebrain.app.ui.panel.host.ProfileSuggestionCard
import com.lovebrain.app.ui.panel.host.StageSuggestionCard
import com.lovebrain.app.ui.panel.host.VectorPillsRow
import com.lovebrain.app.ui.panel.reply.*
import com.lovebrain.app.ui.panel.settings.LoveBrainSettingsContent
import com.lovebrain.app.ui.panel.stats.UsageStatBar
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.costReadout
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.ResultMode

/** 面板数值串那半角货币符号（首页/详情页是全角 `￥`，两边各有守卫钉着，本轮不并——账本 第61节第4条） */
private const val PANEL_COST_CURRENCY = "¥"

private object PanelStrings {
    const val PROACTIVE_EMPTY_HINT = "输入想说的话，点击下方「生成开场」让军师帮你找话题"

    /** 三条发起路径（点击生成 / 长按生成 / 生成开场）与那颗重试共用这一句引导 */
    const val NO_PROVIDER_HINT = "还没有配置模型供应商，请先去设置"
}
private object PanelDimens {
    /**
     * 消息列表槽位：初始 160 / 最小 80 / 最大 400（用户合同表里那一行的三个数）。
     *
     * 之前初始是 80——那是"能滚就行"的读法，用户要的是旧版那一屏：两三条消息加想法区
     * 应当一眼看完而不是一进来就滚动。拖动调整的能力不变，仍然只有这三档边界。
     */
    const val MESSAGE_LIST_DEFAULT_HEIGHT_DP = 160

    /**
     * 空态那一档的列表槽位 = 图标容器 48 + 间距 8 + 那颗动作的热区下限 48
     * + `MessageList` 空态 Column 自己的 `padding(vertical = Spacing.md)` 上下各 8 = **120**。
     *
     * 这个数不是审美选的：槽位再小，`MessageList` 里那条"动作热区 ≥48dp"就会被父约束**夹掉**
     * ——本机第一发量到 8dp（104 那一档还量到 32dp：漏算了那 16dp 的内边距）。
     * 有消息时仍然用用户拖出来的 `MESSAGE_LIST_DEFAULT_HEIGHT_DP`。
     */
    const val MESSAGE_LIST_EMPTY_HEIGHT_DP = 120
    const val MESSAGE_LIST_MIN_HEIGHT_DP = 80
    const val MESSAGE_LIST_MAX_HEIGHT_DP = 400
    const val TOUCH_TARGET_MIN_DP = AppDimens.TOUCH_TARGET_MIN_DP  // 从 24dp 修正为无障碍下限；数只写在全局那颗
}

private val OnboardGuideLineHeight = 20.sp

/**
 * 这一屏"哪一面在上面"与那一层背景浓度的**视图态持有者**。
 *
 * 里面只有两件事，而且都只在画的时候用：
 * · `settingsOpen`——齿轮那一扇整窗设置页开不开（换的是这一窗的内容，不是弹窗、不是半屏 Sheet、
 *   也不跳外部 Activity）；
 * · `backdropPercent`——拖动滑杆期间的实时预览值，只喂给面板底那一层颜色的 alpha。
 *
 * 它**不认识 ViewModel、也不碰盘**：落盘那一句由面板在 `onOpacityCommit` 里调
 * `setPanelBackdropOpacityPercent`（与首页同一份落盘口，这里不开第二条）。
 * 之所以做成一颗持有者而不是两颗局部布尔：本仓对"屏幕函数中间摊一堆局部可见性状态"
 * 是有账的（`UiLayerDependencyContractTest` 那一族），设置页这一格是新增的第 4 面。
 */
private class PanelSurfaceHolder(initialBackdropPercent: Int) {
    var settingsOpen: Boolean by mutableStateOf(false)
        private set
    var backdropPercent: Int by mutableIntStateOf(PanelBackdropOpacity.snapPercent(initialBackdropPercent))
        private set

    fun openSettings() { settingsOpen = true }
    fun closeSettings() { settingsOpen = false }

    /** 预览：越界与刻度外的值先落回合法刻度，画出去的永远就是盘上可能存着的那一档 */
    fun setBackdropPreview(percent: Int) {
        backdropPercent = PanelBackdropOpacity.snapPercent(percent)
    }
}

@Composable
private fun rememberPanelSurfaceHolder(initialBackdropPercent: Int): PanelSurfaceHolder =
    remember { PanelSurfaceHolder(initialBackdropPercent) }

/**
 * 齿轮那一扇**整窗设置页**的宿主接线：正文本体住在 `ui/panel/settings/LoveBrainSettingsContent`，
 * 这一格只做两件事——把背景层当前浓度交出去、把预览与写盘两条口接回来。
 *
 * 设置页当前包含：透明度滑杆、意图入口（§10.2）、知识库切换（§10.3）。
 * 供应商/模型/超时在首页"模型供应商"那一格，捕获范围在首页"消息捕获"那一格。
 *
 * ⚠ 以前这一格会在**组合阶段同步枚举整机安装包**（`selectableCaptureTargets(context)`）并为了
 * 供应商那一行 `koinViewModel()` 依赖容器——那是"第一次点击设置卡一下"最可疑的一处（**是否真是它，
 * 要用点击前后耗时量过才算**，见 tasks\ ）。现在这一页既不碰 PackageManager，
 * 也不碰供应商状态源。
 *
 * · **透明度**拖动期间只改 `surface.backdropPercent`（背景层实时预览、不落盘），
 *   松手那一次才写盘——这条通道与窗口淡入淡出动画各管各的。
 */
@Composable
private fun PanelSettingsPage(
    viewModel: LoveBrainViewModel,
    surface: PanelSurfaceHolder,
    onBack: () -> Unit,
    /** 原话第 17 条「设置页收不起窗」：这颗只把宿主那一次点击转下去，本页不判断该不该收 */
    onCollapse: () -> Unit,
    onInputIntent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 持续意图：设置页那一格读同一份 config，一条写口 onIntentChange 拼一次 save。
    // · 拨开关 / 改正文（recomputeExpiry=false）：传**已有 expiryDate**，
    //   [IntentPolicy.effectiveExpiryDate] 保留它，不把计时重置；
    // · 换有效期档（recomputeExpiry=true）：传空串触发按新档重算到期时刻。
    val intentConfig by viewModel.intents.config.collectAsStateWithLifecycle()
    val activeKb by viewModel.activeKb.collectAsStateWithLifecycle()
    // §10.3：改用 StateFlow 收集——设置一直打开时新建/改名后也能收到最新列表
    val kbList by viewModel.knowledgeBasesState.collectAsStateWithLifecycle()
    LoveBrainSettingsContent(
        onBack = onBack,
        onCollapse = onCollapse,
        opacityPercent = surface.backdropPercent,
        onOpacityPreview = { percent -> surface.setBackdropPreview(percent) },
        onOpacityCommit = { percent ->
            surface.setBackdropPreview(percent)
            viewModel.setPanelBackdropOpacityPercent(percent)
        },
        intentEnabled = intentConfig.enabled,
        intentText = intentConfig.text,
        intentExpiry = intentConfig.expiry,
        onIntentChange = { text, enabled, expiry, recompute ->
            // §10.2: 完成/到期后重新启用或重新选有效时间时恢复 ACTIVE
            val wasTerminal = intentConfig.status == IntentStatus.COMPLETED ||
                intentConfig.status == IntentStatus.EXPIRED
            val effectiveStatus = when {
                // 重新启用（从关到开）或换有效期档且旧 status 是终态 → 恢复 ACTIVE
                (enabled && !intentConfig.enabled) || (recompute && wasTerminal) -> IntentStatus.ACTIVE
                else -> intentConfig.status
            }
            // 重新启用到期意图时必须重算期限：旧 expiryDate 已是过去时刻，
            // 不重算会立刻被 shouldAutoExpire 判过期 → "重新开启后立即失效"。
            // 换档（recompute=true）本来就传空串触发重算；
            // 从终态重新启用（wasTerminal && enabled 刚变 true）即使没换档也要传空串。
            val mustRecomputeExpiry = recompute ||
                (wasTerminal && enabled && !intentConfig.enabled)
            viewModel.intents.save(
                text, enabled, expiry,
                if (mustRecomputeExpiry) "" else intentConfig.expiryDate,
                effectiveStatus
            )
        },
        knowledgeBases = kbList,
        activeKbName = activeKb?.name,
        onSwitchKb = { name -> viewModel.switchActiveKb(name) },
        modifier = modifier,
        onInputIntent = { onInputIntent("settings") }
    )
}

@Composable
fun LoveBrainPanelScreen(
    viewModel: LoveBrainViewModel,
    onInputFocusChange: (String, Boolean) -> Unit,
    onInputIntent: (String) -> Unit,
    onClearComposeFocus: ((() -> Unit) -> Unit),
    onResize: (Int, Int) -> Unit,
    onResizeEnd: () -> Unit = {},
    onMove: (Float, Float) -> Unit,
    onCopy: (String) -> Unit,
    /**
     * 「去设置」那颗（原话第 8 条）：缺模型供应商时点它要进 **App 主页面**，
     * 不是悬浮窗里那页调透明度的设置。齿轮那颗仍走 `surface.openSettings()`——
     * **两条路分开**（指导书 §153 那一行"去设置和齿轮被约定同一个入口"是被推翻的旧约定）。
     *
     * 这颗**故意没有默认值**：宿主没接线就得编译不过，不会留下一颗点了没反应的假入口
     * （本仓库的旧规矩是"没接线就不画"，而这一颗是非画不可的失败出口）。
     */
    onOpenAppPage: () -> Unit,
    onCollapse: () -> Unit
) {
    val panelMode by viewModel.composer.panelMode.collectAsStateWithLifecycle()
    val resultMode by viewModel.resultMode.collectAsStateWithLifecycle()
    val composerMode by viewModel.composerMode.collectAsStateWithLifecycle()
    val messages by viewModel.composer.messages.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val feedbacks by viewModel.feedbacks.collectAsStateWithLifecycle()
    val draftText by viewModel.composer.draftText.collectAsStateWithLifecycle()
    val currentRole by viewModel.composer.currentRole.collectAsStateWithLifecycle()
    val ideaComposeMode by viewModel.composer.ideaComposeMode.collectAsStateWithLifecycle()
    //  三轴之一：**输入对象**（她/我/补充）。它与上面那颗 `currentRole`（自动捕获角色）是两件事，
    // 必须各读各的——"选补充把捕获角色也改掉"就是这一屏原来那条错标路径。
    val inputKind by viewModel.composer.inputKind.collectAsStateWithLifecycle()
    // ：仅看本轮。状态源在 RoundStateStore，面板只读；写口只有 toggle 那一条（长按那条隐蔽入口已废）。
    val onlyThisRound by viewModel.onlyThisRound.collectAsStateWithLifecycle()
    val composeRole = if (ideaComposeMode) ChatMessage.Role.IDEA else currentRole
    val editingIndex by viewModel.composer.editingIndex.collectAsStateWithLifecycle()
    val review by viewModel.profileReview.collectAsStateWithLifecycle()
    val activeKb by viewModel.activeKb.collectAsStateWithLifecycle()
    // 通知位上**正在显示的那一条**：三条通道的排队、"等待期间不倒计时"这两条判据都在
    // `feature/notice/NoticeBoard`（VM 只投递），面板这一侧只读这一颗、只起一张表。
    // 以前是三条流各读各的 + 三个 `LaunchedEffect` 各计各的，于一屏能挤两条、后到的盖掉正在读的。
    val notice by viewModel.currentNotice.collectAsStateWithLifecycle()
    val stageSuggestion by viewModel.stageSuggestion.collectAsStateWithLifecycle()
    val currentVector by viewModel.currentVector.collectAsStateWithLifecycle()
    val vectorDelta by viewModel.vectorDelta.collectAsStateWithLifecycle()
    // 持续意图：入口那颗 chip 与编辑器浮层各读一位
    val intentConfig by viewModel.intents.config.collectAsStateWithLifecycle()
    val showIntentEditor by viewModel.intents.showEditor.collectAsStateWithLifecycle()

    // 花费与累计统计：九个数字一份快照、一次收集
    val usage by viewModel.usageStats.collectAsStateWithLifecycle()
    val isProviderReady by viewModel.providerReady.collectAsStateWithLifecycle()

    // proactive state collected at top level
    val isProactive by viewModel.isProactive.collectAsStateWithLifecycle()
    val proactiveOptions by viewModel.proactiveOptions.collectAsStateWithLifecycle()
    val proactiveError by viewModel.proactiveError.collectAsStateWithLifecycle()

    // 单条改写状态
    val rewriteStates by viewModel.rewriteStates.collectAsStateWithLifecycle()

    // 输入已变化——**不再在方案周围插一条横幅**：这句黄色提示走通知队列的 Warning 通道，
    // 与另外两条同位置、同顺序（下面那格 `LaunchedEffect(inputChanged)` 就是这一句的上车点）。
    val inputChanged by viewModel.inputChanged.collectAsStateWithLifecycle()

    // （用户 2026-10-03："点击踩之后出来的界面太难看了，而且成本太高了，就点踩就不要弹窗全部删除！！记入就行了"）：
    // 点踩不再挂任何面板/弹窗；这里**故意不收集** `currentFeedbackCase`——一收集就把 UI 状态拉回面板这一侧。
    // 当前真实的保存通路（2026-10-03 接线完成，面板这一侧一个字不用改）：
    //   `LoveBrainViewModel.setFeedback` → `FeedbackCaseController.toggle`（点击当刻同步建案例）
    //   → `feature/feedback/recordDislikeCase`（先按「方案 identity + 本轮生成版本身份」查重，再落盘）
    //   → `FeedbackCaseController.persistCase`（**把成败返回给调用方**，不再吞成成功）
    //   → 回执那一格 `LoveBrainViewModel.reportDislikeCaseSave`：`Recorded` / `AlreadyRecorded` 走绿色成功格
    //     （`showSuccessNotice` → `NoticeBoard.Channel.Knowledge` → `LbStateTone.Success`，3 秒自动消失）；
    //     `Failed` 走既有警告格（`showPanelWarning` → `Channel.Warning` → `LbStateTone.Warning`），
    //     并且留着当前候选、回复与用户刚点的那一下踩。两句文案就是 `R.string.notice_recorded` /
    //     `notice_record_failed` 本身（中英两份都在盘上，串里没有 ✅——这一格成功从不画成黄色警告）。

    // 「记录实际发送」这一条用户可见通路整体退场（弹层、专属状态持有者、与它的成败回执）。
    // VM 那侧 `recordActualSentMessage` / 点赞 / 记入知识库的语义一个字没动，既有数据不清理、
    // 不迁移；「复制」也**不**被标成"已发送"——那件事从来不是这个入口的含义。
    // 本体留在 ，专属用例随件一起归档。

    // 记忆纠正中心：开合归持有者（第6节第4条 :523），记录内容仍由 VM 异步喂进来
    val correctionCenter = rememberCorrectionCenterHolder()
    // 第6节第4条：本轮参考记忆的两颗纠正浮层（暂停时长 / 标记为错误）的状态与渲染
    // 从结果区那一行里搬到这里——遮罩因此盖得住整个面板，而不是只盖住那一行
    val memoryCorrectionFlow = rememberMemoryCorrectionFlow()
    var correctionCenterCorrections by remember { mutableStateOf<Map<String, com.lovebrain.app.model.MemoryCorrection>>(emptyMap()) }

    // 齿轮那扇整窗设置页的开合 + 面板背景浓度的实时预览：这一屏"哪一面在上面"只认这一颗持有者。
    // 背景浓度**不**走 `ComposeView.alpha`（那条通道归窗口淡入淡出动画所有，服务侧会把它复位），
    // 预览值只影响下面那层底色，正文一个字都不乘它。
    val surface = rememberPanelSurfaceHolder(viewModel.panelBackdropOpacityPercent)

    // 稳定轮次身份：整轮 generate 成功时才变。它同时是「本轮参考」展开态的键——
    // 换一整轮就归零，参考信息从来不是跨轮共享的那一份。
    val generationRoundId by viewModel.generationRoundId.collectAsStateWithLifecycle()
    // 「本轮参考」那份清单的展开态：入口在每张卡下面、清单画在结果区里，状态得住在两者之上才翻得动。
    // 默认收起；这一格从结果区里那行搬上来，是为了让"切去设置页再切回来"不至于把用户翻开的清单丢掉。
    var memoryRefsExpanded by rememberSaveable(generationRoundId) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshTicketState()
    }

    // 通知位上**只有一张表**，而且它跟着"正在显示的那一条"起：
    // `NoticeBoard` 只在条目顶到通知位那一刻才盖时间戳，排队期间条目身上没有任何时间，
    // 所以"还在等就被判过期"这个形状在这条链路上写不出来。
    // key 是 `notice?.id`：换下一条、或同通道被刷新都会换号 ⇒ 表重起；
    // 通知位空着（null）就没有表。`remainingMillis` 交 null 的是**需要用户确认**那类——
    // 那种不自动过期，这里就不起表，等确认/忽略之后由那一格自己的回调回报 `endCurrentNotice()`。
    LaunchedEffect(notice?.id) {
        val shown = notice ?: return@LaunchedEffect
        val remaining = shown.remainingMillis(System.currentTimeMillis()) ?: return@LaunchedEffect
        delay(remaining)
        viewModel.endCurrentNotice()
    }

    // 「输入已变化」那句黄色提示不再插在方案周围：它上通知队列的 Warning 通道，
    // 与另外两条同位置、同顺序播放。只在**跳变**那一次投递（判据在 VM 的 stale 检测里），
    // 出口仍是那颗「重试」，这里不给第二个按钮、也不给"以后不再提示"。
    // 判据回到"没变"时把**这一句**摘掉（按文案摘、不按通道整条关），否则它会挂在队列里
    // 在一个已经不成立的条件上继续播一次。
    val inputChangedHint = stringResource(R.string.panel_input_changed)
    LaunchedEffect(inputChanged) {
        if (inputChanged) viewModel.showPanelWarning(inputChangedHint)
        else viewModel.dismissPanelNotice(inputChangedHint)
    }

    // 待确认的建议卡进队：Store 里出现一张就按它的稳定身份投一次，Store 里没了就把它摘掉。
    // 只在**身份跳变**那一次调——放在组合体里直接调会每次 recompose 都投一次，
    // 队列会被同一张卡淹掉。身份用 Store 给的那颗 id（画像用 suggestionId，
    // 阶段用"库名|新阶段"），确认/忽略的业务仍在各 Store 里，这里只管"轮到谁"。
    val profileNoticeKey = review.suggestion
        ?.takeIf { it.kbName == activeKb?.name }
        ?.suggestionId
    LaunchedEffect(profileNoticeKey) {
        val key = profileNoticeKey
        if (key != null) viewModel.showProfileSuggestion(key) else viewModel.dismissProfileSuggestion()
    }

    val stageNoticeKey = stageSuggestion
        ?.takeIf { it.kbName == activeKb?.name }
        ?.let { "${it.kbName}|${it.newStage}" }
    LaunchedEffect(stageNoticeKey) {
        val key = stageNoticeKey
        if (key != null) viewModel.showStageSuggestion(key) else viewModel.dismissStageSuggestion()
    }

    val focusManager = LocalFocusManager.current
    DisposableEffect(onClearComposeFocus) {
        onClearComposeFocus { focusManager.clearFocus() }
        onDispose { }
    }

    // 第12节第2条「输入焦点和 IME 状态在切页时明确移交」。两页都留在组合里（切页要保留各自草稿），
    // 于是同一棵树里**同时**挂着回复那格的 `PanelTextInput` 与谈心那格的 `BasicTextField`——
    // 不交接的话，用户已经滑到谈心那一面，键盘与 `PanelInputFocusOwner.activeInputId`
    // 还停在 "reply" 那根光标上（窗口 flags 也还挂在 EDITING，服务侧据此决定软键盘）。
    // 这一句只做**交出**：焦点一交回，两格各自的 `onFocusChanged(false)` 会把 activeInputId
    // 清空、窗口落回 PASSIVE、IME 收起。**不**替下一页抢焦点——抢了就是"滑一下键盘自己弹出来"，
    // 那是用户没要求的第二件事；他要在那一面打字就点那一面的输入框（原有的直接获焦路径）。
    // 首次组合也会走这一句，那时没有任何焦点，`clearFocus` 是空操作。
    LaunchedEffect(panelMode) {
        focusManager.clearFocus(force = true)
    }

    // ── 宿主失焦交接（§12.2 补完）─────────────────────────────────────────
    // 设置页盖层打开/关闭、意图编辑器关闭这三条路径同样需要交出 Compose 焦点。
    // 旧版只接了 `panelMode` 切页，设置页从主面盖上来（或收回去）时，正在获焦的那根
    // BasicTextField 不会自动失焦——`onFocusChanged(false)` 不触发，
    // `PanelInputFocusOwner.activeInputId` 不清、窗口仍挂 EDITING、IME 不收。
    // 用户从设置页回到主面时光标还停在刚才那格上，长按选区也跟着错位。
    //
    // `surface.settingsOpen` 一翻就清一次：打开时主面那几颗输入框被盖在下层、
    // 不该继续持焦；关闭时设置页那一排输入框跟着退场、也不该留着。
    // `force = true` 是因为 Compose 默认 `clearFocus` 只清"非主动"焦点，
    // 用户正在打字那一颗是主动焦点，不清掉它 IME 就不会收。
    LaunchedEffect(surface.settingsOpen) {
        focusManager.clearFocus(force = true)
    }

    // 意图编辑器关闭时同样交出焦点——LbModalSheet 的退场动画跑完之后，
    // 编辑器里那颗 LbFieldInput 仍可能持焦，不清就会留一个看不见的输入框
    // 挂着 EDITING。`showIntentEditor` 从 true→false 时清一次。
    LaunchedEffect(showIntentEditor) {
        if (!showIntentEditor) {
            focusManager.clearFocus(force = true)
        }
    }

    // 面板背景浓度送进子树的**唯一**一处接线：只包这一层 provider，里面每一行原样不动。
    // 这棵子树里的大面积卡片经 `panelBackdropCardColor()` 读浓度（换算口仍是
    // `PanelBackdropOpacity`，这里不提供数字）；面板底那一层照旧直接读 `surface.backdropPercent`。
    CompositionLocalProvider(LocalPanelBackdropDensity provides surface.backdropPercent) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // shadow fix
                .clip(LoveBrainShape.xl)
                // 面板背景浓度**唯一**的作用点就是这一层颜色的 alpha：`PanelBackdropOpacity.alphaOf`
                // 把盘上那个整数（40..100，默认 100 = 与从前逐字同形）换成 alpha。
                // 不借 `ComposeView.alpha` ——那条通道归窗口淡入淡出动画所有（服务侧在 present/hide
                // 路径上会把它复位成 1），借它画浓度第一次收起面板就会把设置抹掉；
                // 也不压正文：下面所有文字、图标、卡片都保持满不透明度。
                .background(SurfaceBase.copy(alpha = PanelBackdropOpacity.alphaOf(surface.backdropPercent)))
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border.copy(alpha = 0.5f), LoveBrainShape.xl)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
        ) {
            // H2（2026-10-06）：原来这里是 `if (settingsOpen) { 设置页 } else { 主面 }`——整棵主面树被换掉。
            // 现在主面永远在树上，设置页改为盖一层（见 Column 之外那扇 overlay）。
            // 会话、输入、卡片展开态本就住在 ViewModel 与 holder 上，主面不卸树 ≠ 多保留什么，
            // 只是主面那些 remember 与收集流不再因切设置页被丢回重建。
                Box(modifier = Modifier.fillMaxWidth().height(Spacing.sm)) {
                    DragHandle(onMove = onMove)
                    // 顶部使用统计：**永远只占一行**。每一格是"标签＋值"合并成的**一段** Text
                    // （`maxLines=1`、`softWrap=false`）——旧 Inline 档把一格拆成标签与数值两颗
                    // 可各自换行的 Text，那正是"冒出第二行"的来源；分组、轮播与渐隐都归 `stats` 那一族。
                    // 但**换组与横向查看**归 `UsageStatBar`：一行放得下就把五格平铺、不轮播也不渐隐；
                    // 放不下就按现有字段切出的完整分组整组换，两侧用淡渐隐表示"那边还有内容"。
                    // 渲染口径一格没改：五格、首字那格的 >0 条件、「—」占位都原样，
                    // 标签与带单位的数值串也逐字照搬；「今日」「累计」两格的费用仍走**与首页/使用概览页
                    // 同一颗判据** `costReadout`（`viewmodel/UsageStats.kt`）：一笔可计价记录都没入过账时
                    // 念「—」，绝不念成 `¥0.000`（未知 ≠ 免费）。页面这一侧不重算任何钱。
                    // 「本次」那格本来就是 `Double?`：没有数就念「—」，占位串与另两格同一份资源。
                    // 槽位仍是页头上面那 4dp 那一档：组件按自己一行的高度居中溢出绘制（`unbounded = true`），
                    // 所以它既挤不出第二行，也不会被那一档夹掉。
                    val costUnknown = stringResource(R.string.cost_unknown)
                    val costBelowCent = stringResource(R.string.cost_below_cent, PANEL_COST_CURRENCY)
                    val panelYuanText: (Double) -> String = { PANEL_COST_CURRENCY + LoveBrainViewModel.formatYuan(it) }
                    UsageStatBar(
                        fields = buildList {
                            add(LbMetric("今日", costReadout(usage.todayCostYuan, costUnknown, costBelowCent, panelYuanText)))
                            add(LbMetric("本次", usage.lastCostYuan?.let(panelYuanText) ?: costUnknown))
                            if (usage.lastResponseMs > 0) {
                                add(LbMetric("首字", "%.1fs".format(usage.lastResponseMs / 1000.0)))
                            }
                            add(LbMetric("累计", "${usage.totalGenerateCount}次"))
                            add(LbMetric("已统计", costReadout(usage.totalCostYuan, costUnknown, costBelowCent, panelYuanText)))
                        },
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).align(Alignment.Center)
                    )
                }

                Spacer(Modifier.height(Spacing.sm))

                PanelHeader(
                    panelMode = panelMode,
                    onModeChange = { viewModel.setPanelMode(it) },
                    // 顶部只剩两段（回复/谈心），panelMode 是唯一那一颗"哪一页在上面"的账。
                    // collapse button
                    onCollapse = onCollapse,

                    // drag fix
                    // outputMode in VM
                    onHeaderDrag = onMove,
                    // 左上角齿轮：整窗切设置页（弹窗、半屏 Sheet、外部 Activity 都不是这一格的答案）
                    onOpenSettings = { surface.openSettings() }
                )

                Spacer(Modifier.height(Spacing.xs))

                // Onboarding card
                val onboardContext = androidx.compose.ui.platform.LocalContext.current
                val onboardPrefs = remember { onboardContext.getSharedPreferences("lovebrain_onboarding", android.content.Context.MODE_PRIVATE) }
                var showOnboard by remember { mutableStateOf(!onboardPrefs.getBoolean("done", false)) }
                if (showOnboard) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm)
                            .clip(LoveBrainShape.md)
                            .background(PrimaryLight, LoveBrainShape.md)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("使用提示", style = AppTypography.labelLarge, color = PrimaryDark, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(PanelDimens.TOUCH_TARGET_MIN_DP.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = {
                                            onboardPrefs.edit().putBoolean("done", true).apply()
                                            showOnboard = false
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    // 原来这里是内联中文 `"关闭使用提示"`，而 `a11y_close_onboarding`
                                    // 中英两份资源都在、从没被引用 ⇒ 英文环境念中文（面板整屏量到的）
                                    contentDescription = stringResource(R.string.a11y_close_onboarding),
                                    tint = TextHint,
                                    modifier = Modifier.size(Spacing.xl)
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "添加对话 -> 生成回复 -> 查看回复方案\n" +
                                "点击方案卡可调整单条措辞\n" +
                                "点踩可记录原因并导出，便于后续复盘和调整\n" +
                                "顶部显示今日/本次/累计花费与生成次数\n" +
                                "困惑时可切「谈心」模式，军师用公正视角帮你分析",
                            style = AppTypography.labelMedium,
                            color = TextSecondary,
                            lineHeight = OnboardGuideLineHeight
                        )
                    }
                }

                // 通知位排在五维**上面**（字符图那一列的顺序：通知 → 已有内容 → 输入行）：
                // 通知是要被看见的一次性消息，压在五维下面等于第一条就被内容顶出视线。
                // 通知位上只有**一格**：轮到哪一条就画哪一条，其余在队列里等。
                // 文字回执与待确认的建议卡**共用这一格**——两张卡各自单独占位的话，
                // "同一时间只显示一条"就只剩名义：一屏会同时出现一条回执加一张待确认卡。
                // 卡的内容与"现在能不能确认"仍从各自 Store 的那份快照读，队列只管"现在轮到谁"；
                // 所以下面分支的条件是队列给的 kind，正文条件仍是原有的 active-KB 过滤。
                // 关闭一律点名到 id：晚到的旧回调不能把刚顶上来那条一起误杀。
                notice?.let { current ->
                    when (current.suggestion?.kind) {
                        NoticeBoard.SuggestionKind.Profile -> {
                            val profileSuggestion = review.suggestion
                            if (profileSuggestion != null && profileSuggestion.kbName == activeKb?.name) {
                                val isRegenerating = viewModel.profileRegenerating.collectAsStateWithLifecycle().value
                                ProfileSuggestionCard(
                                    suggestion = profileSuggestion.display,
                                    canConfirm = review.canConfirm,
                                    isConfirming = review.isConfirming,
                                    isRegenerating = isRegenerating,
                                    onConfirm = { viewModel.confirmProfileUpdate() },
                                    onDismiss = { viewModel.dismissProfileUpdate() },
                                    onRegenerate = { viewModel.regenerateProfileUpdate() }
                                )
                                Spacer(Modifier.height(Spacing.md))
                            }
                        }

                        NoticeBoard.SuggestionKind.Stage -> {
                            val pendingStage = stageSuggestion
                            if (pendingStage != null && pendingStage.kbName == activeKb?.name) {
                                StageSuggestionCard(
                                    suggestion = pendingStage,
                                    onConfirm = { viewModel.confirmStageChange() },
                                    onDismiss = { viewModel.dismissStageChange() }
                                )
                                Spacer(Modifier.height(Spacing.md))
                            }
                        }

                        null -> KbNoticeBanner(
                            text = current.message,
                            tone = if (current.channel == NoticeBoard.Channel.Warning) {
                                LbStateTone.Warning
                            } else {
                                LbStateTone.Success
                            },
                            onDismiss = { viewModel.endCurrentNotice(current.id) }
                        )
                    }
                }

                // 关系五维那一排挪到通知位**下面**：它属于"已有内容"那一带，
                // 不该把一次性消息挤到自己的位置之上（本轮之前正是这个顺序反了）。
                VectorPillsRow(vector = currentVector, delta = vectorDelta)
                Spacer(Modifier.height(Spacing.xs))

                // ── 回复(0) ↔ 谈心(1)：两页左右滑互切（第12节第2条） ──────────────────
                // 页索引只有**一颗真 owner**：`viewModel.composer.panelMode`（上面那行 collect）。
                // · 点 tab：`onModeChange` → `setPanelMode` → 这一颗变 → 画哪一页跟着变；
                // · 左右滑：手势结束时也只投一次 `setPanelMode`，宿主里没有第二颗"现在在哪一页"；
                // · `panelMode ↔ 页索引` 的换算只有一对函数（`panelModeToPage`/`panelPageToMode`，
                //   住在 `PanelPagePager.kt`），页头那两段读的是同一对——所以"tab 指着谈心、
                //   身体还画着回复"这种两本账对不上的形状在这棵树上写不出来。
                // 挂上就不再卸（见 `PanelPagePager` 文件头）：谈心那页的本地草稿与历史
                // （`counseling/CounselingPanel.kt:237/240` 的 `remember`）、回复这页正在收的
                // 流式结果与列表展开态都不随切页丢，这一格也不发起、不取消任何东西。
                PanelPagePager(
                    currentPage = panelModeToPage(panelMode),
                    onPageChange = { page -> viewModel.setPanelMode(panelPageToMode(page)) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { page ->
                if (page == 0) {
                // 回复侧只剩这一格。
                // 原来这一支挂在页外层 Column 上，现在整支搬进页槽位，正文一行没改；
                // 下面这些行的缩进**故意**留在原来的档位（跟着 `if` 一起再缩一层会把 240 行
                // 正文全拖进纯缩进 diff，复核时看不见真改动）。
                Column(modifier = Modifier.fillMaxSize()) {
                val inputFocusRequester = remember { FocusRequester() }
                // ── 次级控制的两处挂载点，共用同一份状态（原话第 10 条 + 基线 v1 §3.11）──
                // 口径只算一次：`hasRealDialogueRows(messages)` 就是 `MessageList` 内部那一口，
                // 有真实消息 → 挂输入区行 2；没有 → 收进消息卡空态分支。两处二选一，绝不在
                // 两处各存一份开关（那才是"意图与仅看本轮各长两个所有者"的第二本账）。
                val hasRealRows = hasRealDialogueRows(messages)
                ReplyInput(
                    draftText = draftText,
                    currentRole = composeRole,
                    editingIndex = editingIndex,
                    showRoleChips = composerMode == ComposerMode.REPLY,
                    showAddButton = composerMode == ComposerMode.REPLY,
                    placeholderOverride = if (composerMode == ComposerMode.PROACTIVE) "想说什么？留空让军师找话题" else null,
                    onDraftChange = { viewModel.setDraft(it) },
                    onRoleChange = { viewModel.setCurrentRole(it) },
                    // ：输入对象与自动捕获角色分轴。投 SetInputKind 只改"这一格在写谁"，
                    // 不动 captureRole —— 这正是"选《补充》后新增消息被标成上一个角色"那条原话的修法。
                    inputKind = inputKind,
                    onInputKindChange = { viewModel.composer.accept(ComposerStore.Intent.SetInputKind(it)) },
                    // ：仅看本轮常驻输入行 1（在 ＋ 与输入框之间），给了回调才画。
                    onlyThisRound = onlyThisRound,
                    onOnlyThisRoundChange = { viewModel.toggleOnlyThisRound() },
                    // hasRealMessages 与 MessageList 共用同一口径（hasRealRows）；仅看本轮不再随它二选一挂载。
                    hasRealMessages = hasRealRows,
                    onAdd = {
                        val text = draftText.trim()
                        if (text.isNotEmpty()) {
                            if (editingIndex >= 0) {
                                viewModel.updateMessage(editingIndex, composeRole, text)
                                viewModel.setEditingIndex(-1)
                            } else if (inputKind == ComposerInputKind.SUPPLEMENT) {
                                // 等：这一格写的是"给军师的话"，不是第 4 条聊天消息。
                                // 走 SubmitNote，绝不进 messages（进了就会被记成"我说过这句话"）。
                                viewModel.composer.accept(ComposerStore.Intent.SubmitNote(text))
                            } else {
                                viewModel.addMessage(composeRole, text)
                            }
                            viewModel.setDraft("")
                        }
                    },
                    inputId = "reply",
                    onFocusChange = { focused -> onInputFocusChange("reply", focused) },
                    onInputIntent = { onInputIntent("reply") },
                    focusRequester = inputFocusRequester
                )

                // ── 持续意图入口：宿主仍是这一页，挂载点已从面板输入行撤走（搬到设置页）──
                // 回复链每次生成都读 `intents.config` 拼进 prompt，PRODUCT_SPEC 第2节 把持续意图列在"保留、不许
                // 借简化删"那一栏。这一颗一度落在这里"另画一排"——
                // 于是屏上有两排次级控件（这一排 + 输入区那颗「仅看本轮」那一排），正是原话第 10 条
                // 要收掉的形状。意图入口现在走设置页（另一路负责接线），本体仍由上面 `intentSlot`
                // 提供、没有活动知识库依旧不画（意图按库隔离，没有"这一块库"就无处可存）。

                val density = androidx.compose.ui.platform.LocalDensity.current
                var messageListHeight by remember { mutableStateOf(PanelDimens.MESSAGE_LIST_DEFAULT_HEIGHT_DP.dp) }
                // ⚠ 空态那一档**不能**沿用用户拖出来的列表高度：空态自己需要
                //   图标 48 + 间距 8 + 动作热区 48 = 104dp（`MessageList` 里那条
                //   `heightIn(min = EMPTY_ACTION_MIN_HEIGHT_DP)` 是 48），
                //   而默认槽位只有 80dp ⇒ 父约束把 min 夹到 max 以下，那颗动作被压成
                //   **8dp 高**（面板整屏第一次量到，账本 第53节）。拖拽那一档只管"有消息"的时候。
                MessageList(
                    messages = messages,
                    editingIndex = editingIndex,
                    onReorder = { from, to -> viewModel.reorderMessages(from, to) },
                    onEdit = { index ->
                        messages.getOrNull(index)?.let { msg ->
                            viewModel.setEditingIndex(index)
                            viewModel.setDraft(msg.content)
                            viewModel.setCurrentRole(msg.role)
                        }
                    },
                    onDelete = { id ->
                        viewModel.removeMessageById(id)
                    },
                    modifier = Modifier.height(
                        if (messages.isEmpty()) PanelDimens.MESSAGE_LIST_EMPTY_HEIGHT_DP.dp
                        else messageListHeight
                    ),
                    onEmptyAction = {
                        // 恢复空态入口——点击蓝字只切换到主动发模式，不发网络请求
                        viewModel.toggleProactiveMode()
                    },
                    proactiveActive = composerMode == ComposerMode.PROACTIVE,
                    // 军师备注（等）：读 ComposerStore 那唯一一颗出口（含未提交草稿），
                    // 页面不另存第二本账；渲染点在消息卡内、最后一个真实气泡下面那一行灰字，
                    // 它不是聊天消息，也不进真实对话列表。点它进同一个编辑器（BeginNoteEdit）。
                    noteText = viewModel.composer.ideaHint().takeIf { it.isNotBlank() },
                    onEditNote = { viewModel.composer.accept(ComposerStore.Intent.BeginNoteEdit) },
                    // 侧滑清除备注：投的是 `ClearNote`，与被删对象分开记账——
                    // 绝不让那一下落到 `onDelete` 那条消息删除链上（备注不是聊天消息）。
                    onClearNote = { viewModel.composer.accept(ComposerStore.Intent.ClearNote) }
                )
                DraggableDivider(
                    onDragDelta = { dyPx ->
                        val deltaDp = (dyPx / density.density).dp
                        val newHeight = (messageListHeight + deltaDp).coerceIn(PanelDimens.MESSAGE_LIST_MIN_HEIGHT_DP.dp, PanelDimens.MESSAGE_LIST_MAX_HEIGHT_DP.dp)
                        if (newHeight != messageListHeight) messageListHeight = newHeight
                    }
                )

                // 替换 DualGenerateRow——使用 ComposerMode 驱动的 ReplyPrimaryActions
                // 4 种按钮状态完全匹配要求：
                // 1. 普通回复、无结果 → 全宽"生成回复 · N 条消息"
                // 2. 普通回复、有结果 → "重试 | 记入知识库"
                // 3. 主动发模式 → 全宽"生成开场"；生成中 → "停止"
                // 4. 回复生成中 → "停止"
                ReplyPrimaryActions(
                    modifier = Modifier.fillMaxWidth(),
                    composerMode = composerMode,
                    isGenerating = isGenerating,
                    isProactive = isProactive,
                    hasReplyResult = result is GenerateResult.Success,
                    messageCount = messages.size,
                    onGenerateReply = {
                        if (isProviderReady) viewModel.generate()
                        else viewModel.showPanelWarning(PanelStrings.NO_PROVIDER_HINT)
                    },
                    onGenerateProactive = {
                        if (isProviderReady) {
                            viewModel.generateProactive(draftText.trim())
                        } else viewModel.showPanelWarning(PanelStrings.NO_PROVIDER_HINT)
                    },
                    onRetry = { if (isProviderReady) viewModel.retryCurrentReply() else viewModel.showPanelWarning(PanelStrings.NO_PROVIDER_HINT) },
                    onSaveToKb = {
                        viewModel.nextRound()
                        onCopy("")
                    },
                    onStop = {
                        if (isProactive) viewModel.stopProactive()
                        else viewModel.stopGeneration()
                    }
                )

                // Collect streaming state
                val streamingCoreText by viewModel.streamingCoreText.collectAsStateWithLifecycle()
                val isGeneratingCore by viewModel.isGeneratingCore.collectAsStateWithLifecycle()
                val streamingSchemes by viewModel.streamingSchemes.collectAsStateWithLifecycle()

                // Result area switches based on resultMode
                Box(modifier = Modifier.weight(1f)) {
                    when (resultMode) {
                        ResultMode.PROACTIVE -> {
                            ProactiveResultArea(
                                isProactive = isProactive,
                                options = proactiveOptions,
                                error = proactiveError,
                                onCopy = onCopy,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ResultMode.REPLY -> {
                            ResultArea(
                                result = result,
                                isGenerating = isGenerating,
                                streamingCoreText = streamingCoreText,
                                isGeneratingCore = isGeneratingCore,
                                streamingSchemes = streamingSchemes,
                                feedbacks = feedbacks,
            onFeedback = { scheme, fb ->
            viewModel.setFeedback(scheme.identity.key, fb)
            // ：点踩即建案例并落盘，这里不再展示原因面板、也不再消费"当前案例"那颗展示状态
            },
                                onCopyScheme = { scheme ->
                                    val reply = viewModel.copyScheme(scheme)
                                    onCopy(reply)
                                },
                                onRetry = { viewModel.retryCurrentReply() },
                                // 本轮参考记忆 + 纠正回调
                                memoryRefs = viewModel.getCurrentMemoryRefs(),
                                // （）：显示侧保险传**生成时冻结**的那份「仅看本轮」，
                                // 不传 :556 那颗实时开关——开关是这一轮之后才拨的。
                                frozenOnlyThisRound = viewModel.replyResultOnlyThisRound,
                                correctionFlow = memoryCorrectionFlow,
                                onCorrection = { memoryId, action, replacementText, muteDuration ->
                                    viewModel.applyMemoryCorrection(memoryId, action, replacementText, "", muteDuration)
                                },
                                onUndoCorrection = { memoryId ->
                                    viewModel.undoMemoryCorrection(memoryId)
                                },
                                providerReady = isProviderReady,
                                // 原话第 8 条：缺模型那颗「去设置」进的是 **App 主页面**（这颗由
                                // `FloatingService` 起 `SetupActivity`），不是悬浮窗里调透明度那一页；
                                // 左上角那颗齿轮才走 `surface.openSettings()`。**两条路分开**——
                                // 这里旧注释写的"与齿轮必须是同一扇门"是被用户推翻的旧约定（指导书 §153）。
                                onOpenSettings = onOpenAppPage,
                                // 单条改写
                                rewriteStates = rewriteStates,
                                onRewrite = { identity, command -> viewModel.rewriteScheme(
                                    identity.source,
                                    identity.tag,
                                    command.label
                                ) },
                                onClearRewriteState = { identity -> viewModel.clearRewriteState(identity.key) },
                                onCancelRewrite = { identity -> viewModel.cancelRewrite(identity.key) },
                                onUndoRewrite = { identity -> viewModel.undoRewrite(identity.key) },
                                onCustomRewrite = { identity, customText ->
                                    viewModel.rewriteSchemeCustom(
                                        identity.source,
                                        identity.tag,
                                        customText
                                    )
                                },
                                // 「本轮参考」那份清单的展开态：入口在卡片下方、清单在结果区里，
                                // 所以这一格住在两者之上（换一整轮归零，见上面那颗 key 的说明）
                                memoryRefsExpanded = memoryRefsExpanded,
                                onToggleMemoryRefs = { memoryRefsExpanded = !memoryRefsExpanded },
                                // 打开记忆纠正中心
                                onShowCorrectionCenter = {
                                    viewModel.loadAllCorrections { corrections ->
                                        correctionCenterCorrections = corrections
                                        correctionCenter.open()
                                    }
                                },
                                generationRoundId = generationRoundId,
                                onInputIntent = { onInputIntent("scheme_adjust") },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                }
                } else {
                // 页 1 = 谈心：正文一个字没改，只是从"切档即卸载另一页"的那支 else
                // 换成页槽位里的一页。`weight(1f)` 那一档高度现在由滑页宿主给（上面那行
                // `modifier = Modifier.fillMaxWidth().weight(1f)`），这一格照旧铺满页。
                Box(modifier = Modifier.fillMaxSize()) {
                    CounselingPanel(
                        viewModel = viewModel,
                        inputId = "counseling_main",
                        onFocusChange = { focused -> onInputFocusChange("counseling_main", focused) },
                        onInputIntent = { onInputIntent("counseling_main") },
                        onFollowUpFocusChange = { focused -> onInputFocusChange("counseling_followup", focused) },
                        onFollowUpInputIntent = { onInputIntent("counseling_followup") },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                }
                }
        }

    // H2（2026-10-06）：设置页改为盖一层（overlay），不再与主面互斥换树。
    // 主面那些 remember 与收集流不再因切设置页被丢回重建——会话、输入、卡片展开态
    // 本就住在 ViewModel 与 holder 上，盖一层只是让它们在树里不被拆掉。
    if (surface.settingsOpen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(LoveBrainShape.xl)
                .background(SurfaceBase.copy(alpha = PanelBackdropOpacity.alphaOf(surface.backdropPercent)))
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border.copy(alpha = 0.5f), LoveBrainShape.xl)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(Spacing.sm)) {
                DragHandle(onMove = onMove)
            }
            PanelSettingsPage(
                viewModel = viewModel,
                surface = surface,
                onBack = { surface.closeSettings() },
                onCollapse = onCollapse,
                onInputIntent = onInputIntent,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }

    // ：点踩原因面板整块摘除（宿主 `DislikeReasonHost`/`DislikeReasonPanel` 已删，原件在 git `67dca22`）。
    // 保存链保留：`viewModel.setFeedback(...)` 那一句仍会建案例并落盘，空原因合法，
    // 落盘成功/失败由统一通知那一格报（见 `showSuccessNotice` 与 `FeedbackCaseController`）。

        // 持续意图编辑浮层——**面板根部**渲染，不挂在输入行那一列里面（遮罩要盖得住整屏，
        // 同 `CorrectionCenterHost` / `MemoryCorrectionFlowHost` 那两扇的处理）。
        // 显隐只有 `IntentController.showEditor` 一本账：开在哪块库由它自己冻结（openEditor 先绑库
        // 再翻可见性），保存回 `intents.save(...)`，关闭点名到 `dismissEditor()`。
        // 这一扇编辑浮层的宿主是这一页。能力一个字没减。
        // visible 直接传给 LbModalSheet：它自己的 AnimatedVisibility 负责入退场动画
        // （200ms 淡入+缩放 / 200ms 淡出+缩放），不再外裹一层 AnimatedVisibility——
        // 外裹那一层会在退场时把整棵树摘掉，LbModalSheet 的 exit 动画永远跑不到。
            IntentEditorDialog(
                text = intentConfig.text,
                enabled = intentConfig.enabled,
                expiry = intentConfig.expiry,
                expiryDate = intentConfig.expiryDate,
                status = intentConfig.status,
                visible = showIntentEditor,
                onSave = { text, enabled, expiry, expiryDate, status ->
                    viewModel.intents.save(text, enabled, expiry, expiryDate, status)
                },
                onDismiss = { viewModel.intents.dismissEditor() },
                onInputIntent = { onInputIntent("intent_editor") }
            )

        // 「记录实际发送」那扇浮层连同它的状态接线一起退场（本体与专属用例进 _archive）。
        // 点赞 / 采用 / 记入知识库这三条机制原样保留；VM 的 `recordActualSentMessage` 也原样留着，
        // 只是界面不再替用户手写"我实际发了什么"——那件事从来不是应用检测到的发送。

        // 第6节第4条 :523：记忆纠正中心——独立列出已停用／静音／隔离项，支持撤销。
        // 之前是面板顶层 Box 里一块 `Column(fillMaxWidth)` 内联展开区（无遮罩、不居中、
        // 里面每颗可点的实量 28x19dp）；现在走 CorrectionCenterHost → LbModalSheet，
        // 开合归 correctionCenter 持有者。
        CorrectionCenterHost(
            holder = correctionCenter,
            corrections = correctionCenterCorrections,
            onUndoCorrection = { memoryId ->
                viewModel.undoCorrectionFromCenter(memoryId)
                // 撤销后刷新列表
                viewModel.loadAllCorrections { corrections ->
                    correctionCenterCorrections = corrections
                }
            }
        )

        // 第6节第4条：纠正浮层的唯一渲染处，挂在面板这一层（不是结果行里）
        MemoryCorrectionFlowHost(
            flow = memoryCorrectionFlow,
            onMute = { memoryId, duration ->
                viewModel.applyMemoryCorrection(
                    memoryId = memoryId,
                    action = com.lovebrain.app.model.CorrectionAction.MUTED,
                    muteDuration = duration
                )
            },
            onWrong = { memoryId, text ->
                viewModel.applyMemoryCorrection(
                    memoryId = memoryId,
                    action = com.lovebrain.app.model.CorrectionAction.WRONG,
                    replacementText = text,
                    muteDuration = com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
                )
            }
        )

        ResizeGrip(
            onResize = onResize,
            onResizeEnd = onResizeEnd,
            modifier = Modifier.align(Alignment.BottomEnd)
        )

    }
    }
}

/**
 * 面板顶部那一张通知条（知识库回执 / 面板级警告 / 五维重估摘要，**同一格一次只画一条**）。
 *
 * 原来它是异形账本里 `LoveBrainPanelScreen.kt#KbNoticeBanner` 那一颗，而且带着两个
 * **颜色旋钮**：`container: Color = SuccessBg`、`textColor: Color = Success`，
 * 第二处调用再现场填一对 `WarningBg`/`Warning`——"这条通知是什么语气"由调用方
 * 自选颜色，下一对颜色没有任何地方拦得住（第6节第1条 :490 末句要挡的正是这个形状）。
 * 现在语气走 [LbStateTone] 那张词表（字色与浅底成对，都只在 `LbAsyncState.kt` 里写一次），
 * 容器走 [LbStateContainer.Notice]（原话第 16 条"通知太大"那一格的落点：设计基线 v1.1 §6 把面板
 * 轻通知收成**单档**——`labelMedium`11/16、圆角 `md`10、内边距横 12 竖 4、关闭那颗 14dp 字形坐
 * 24dp 盒；原先的 `Strip` 一支整支留着，别处照旧），那颗关闭转成设计系统里唯一的文字动作。
 *
 * 随归并变掉/补齐的三件事，如实记在这儿：
 * - 关闭那颗原来是裸 `Box.clickable`：**没有声明 `role`**，盒子已经 48dp 见方，
 *   但读屏只念图标名、说不出它是按钮；现在角色与两轴热区都由那一处文字动作保证，
 *   名字改用 `R.string.a11y_close_notice`——那串中文原先是**内联字面量**，
 *   英文环境下读屏照念中文（DESC 那把尺数到的就是它，中英两份资源其实一直都在）。
 * - 说明文字从 `labelSmall` 抬到组件那一档 `bodyMedium`（同 `RowActionButton` 归进
 *   `LbTextAction` 那次：不换所有者就自己定字号，要留住字号就得给组件开旋钮）。
 *   **2026-10-06 再改一轮**：面板轻通知按基线 v1.1 §6 收成单档，字阶落 `labelMedium`11/16——
 *   这一档由 `LbStateContainer.Notice` 那一支自己持有（`StateMessage` 的 `style` 旋钮默认仍是
 *   `bodyMedium`，`Strip`/`Block` 两支一字未动），页面这一侧不填字号、也不开新旋钮。
 * - 原先那句 `maxLines = 1 + Ellipsis` 不再由组件提供：这三条话都有下半句
 *   （例如「已暂停本轮提及，下次生成将过滤此条记忆」），裁掉的正好是要看的那半句。
 *
 * 自动收起那三档时间现在由 `NoticeBoard` 那条队列自己带着（每条通知随身一个时限），
 * 这一颗只管画**正在显示的那一条**；三条文案的来源一个字没动。
 *
 * `internal` 不是给页面用的：这一条通知要由语义树测试**直接挂生产这一颗**，
 * 而不是在测试里再抄一份私有副本——同一件事在本文件的 [ProactiveResultArea] 与
 * `ProviderFormBody` 上都记过账（双轨的那一轨不会跟着改）。
 * 面板本体挂不动这一档还有第二个理由：通知位上的那一条挂着自动收起的那张表，
 * 测试一 `waitForIdle` 就把时间喂完、条子自己消失了，量到的那一屏根本没有它。
 */
@Composable
internal fun KbNoticeBanner(
    text: String,
    tone: LbStateTone,
    onDismiss: () -> Unit
) {
    LbEmptyState(
        message = text,
        tone = tone,
        container = LbStateContainer.Notice,
        action = ScreenAction(stringResource(R.string.a11y_close_notice), onDismiss)
    )
}

/**
 * Proactive result area - now with copy support
 *
 * `internal` 不是给页面用的：空态那一档要由语义树测试**直接挂生产这一颗**，
 * 而不是在测试里再抄一份私有副本（同一件事在 `ReplyPrimaryActions` 与 `ProviderFormBody`
 * 上都记过账——双轨的那一轨不会跟着改）。
 */
@Composable
internal fun ProactiveResultArea(
    isProactive: Boolean,
    options: List<ProactiveOption>,
    error: String?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        when {
            isProactive && options.isEmpty() -> {
                AiLoadingRow(
                    phrases = listOf(
                        "军师正在看你们的近况。",
                        "军师正在找合适的切入点。",
                        "军师正在为你准备开场白。"
                    )
                )
            }
            error != null && options.isEmpty() -> {
                // 这一档原来是一颗自画的 `Box + .background(ErrorBg) + Text`，本页对"出事
                // 了长什么样"因此有自己的第四种答案。说法一个字没改，容器交回 `LbEmptyState`。
                // 这里**不给动作**：主动发失败时这一屏唯一的主操作是下面那颗「生成开场」，
                // 在这一格再塞一颗重试就会出现两颗出口（第6节第3条 要的是就地有出口，不是要有两颗）。
                // 下面那格 `options.isEmpty() ->` 走的也是同一颗组件，只是换成空态那一档。
                LbEmptyState(
                    message = error,
                    tone = LbStateTone.Error,
                    container = LbStateContainer.Strip
                )
            }
            // 还没开场子 = 空态：版式交回设计系统里那唯一一处（这一页原来自己画一颗 Box + 居中
            // Text，于是"空的时候长什么样"每页一个答案）。说法、槽位与内边距一个字没改，
            // 改的只是所有者。
            options.isEmpty() -> LbEmptyState(
                message = PanelStrings.PROACTIVE_EMPTY_HINT,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl)
            )
            else -> {
                // §9（原话第 18 条）：主动发候选补回策略载荷后，正文仍是**唯一可发送内容**
                // （点击复制只复制 `opt.text`，见下面那颗 `clickable`），时机/先别发/需要准备
                // 三段收成"按需展开的次要行"。三段全空的旧候选看起来与改前一致——不多空行、
                // 也不多箭头。
                val strategyOpen = remember { mutableStateMapOf<Int, Boolean>() }
                options.forEachIndexed { index, opt ->
                    val strategy = listOf(opt.timing, opt.holdBack, opt.prepare).filter { it.isNotBlank() }
                    val open = strategyOpen[index] == true
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(LoveBrainShape.md)
                            .background(SurfaceCard, LoveBrainShape.md)
                            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
                            .padding(Spacing.lg)
                            .clickable { onCopy(opt.text) }
                    ) {
                        Text(opt.text, style = AppTypography.bodyMedium, color = TextPrimary)
                        if (opt.angle.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text("角度：${opt.angle}", style = AppTypography.labelSmall, color = TextHint)
                        }
                        if (strategy.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xs)
                                    .clickable(
                                        onClickLabel = stringResource(
                                            if (open) R.string.action_collapse else R.string.action_expand
                                        )
                                    ) { strategyOpen[index] = !open }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_down),
                                    contentDescription = null,
                                    tint = TextHint,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .graphicsLayer { rotationZ = if (open) 180f else 0f }
                                )
                            }
                            if (open) {
                                strategy.forEach { line ->
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(line, style = AppTypography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    Text(error, style = AppTypography.labelSmall, color = Error)
                }
            }
        }
    }
}

// ReplyPrimaryActions 已提取为 reply/ReplyPrimaryActions.kt 中的公共可测试组件。
// 不再在 LoveBrainPanelScreen 中维护 private 副本——测试直接使用生产组件，消除双轨。
