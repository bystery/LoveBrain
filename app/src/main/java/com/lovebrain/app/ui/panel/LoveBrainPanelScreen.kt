package com.lovebrain.app.ui.panel

import android.view.KeyEvent
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.R
import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
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
import com.lovebrain.app.domain.IntentPolicy
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.FloatingServiceHomePort
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ReplyCardLayout
import com.lovebrain.app.viewmodel.costReadout
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.ResultMode

/** 面板花费串用半角 `¥`（首页与使用概览页用全角 `￥`）：两边的版式各由自己的语义测试钉着
 *  （`PanelUsageMetricSemanticsTest` 与 `HomeUsageCardTest`），刻意不并成一颗共用常量——
 *  并一颗就等于同时改两处显示。 */
private const val PANEL_COST_CURRENCY = "¥"

private object PanelStrings {
    const val PROACTIVE_EMPTY_HINT = "输入想说的话，点击下方「生成开场」让军师帮你找话题"

    /** 三条发起路径（点击生成 / 长按生成 / 生成开场）与那颗重试共用这一句引导 */
    const val NO_PROVIDER_HINT = "还没有配置模型供应商，请先去设置"
}
private object PanelDimens {
    /**
     * 消息列表槽位：初始 160dp，可拖区间 80..400dp（就是下面那两颗 min/max）。
     *
     * 初始值从前是 80dp，那等于按"能滚到就行"来定；这一屏要读的是两三条消息加想法区，
     * 应当一进来就一眼看完，而不是先滚动。拖动调整的能力不变，仍然只有这三档边界。
     */
    const val MESSAGE_LIST_DEFAULT_HEIGHT_DP = 160

    /**
     * 空态那一档的列表槽位 = 图标容器 `AppDimens.EMPTY_ICON_CONTAINER_DP`(48)
     * + 间距 `Spacing.md`(8) + 那颗动作的热区下限 `MessageDimens.EMPTY_ACTION_MIN_HEIGHT_DP`(48)
     * + `MessageList` 空态那一列自己的内边距上下各 8 = **120**。
     *
     * 这个数不是审美选的：槽位再小，`MessageList` 里那条"动作热区 ≥48dp"就会被父约束**夹掉**
     * ——实测第一次量到那颗动作只剩 8dp 高（只算到 104 那一档时量到 32dp，漏了那 16dp 内边距）。
     * 有消息时仍然用用户拖出来的 `MESSAGE_LIST_DEFAULT_HEIGHT_DP`。
     */
    const val MESSAGE_LIST_EMPTY_HEIGHT_DP = 120
    const val MESSAGE_LIST_MIN_HEIGHT_DP = 80
    const val MESSAGE_LIST_MAX_HEIGHT_DP = 400
    const val TOUCH_TARGET_MIN_DP = AppDimens.TOUCH_TARGET_MIN_DP  // 无障碍最小热区下限；这颗数全仓只许写在 `AppDimens` 一处，这里只做别名转发
}

private val OnboardGuideLineHeight = 20.sp

/**
 * 这三条输入路**只有"进入编辑"信号、没有失焦信号**，宿主收不到它们交回焦点。
 *
 * 名单里的字面量就是宿主 `PanelInputFocusOwner.activeInputId` 里记着的那三颗——它们各自经
 * `onInputIntent("settings")` / `("intent_editor")` / `("scheme_adjust")` 进 EDITING，
 * 但那条链上只有 `LbFieldInput` 的 `onInputIntent`；这颗组件压根没有上报焦点变化的入口，
 * 所以焦点走了也没人替它报一声（对照 "reply / counseling" 两格：那两格自带 `onFocusChange`，
 * 自己会报）。面板交回焦点时因此要把这三颗逐条点名一次：宿主的 `onInputFocusChanged(id, false)`
 * 按 `activeInputId` 认人，不是它记着的那一颗就直接 return ⇒ 多发的是空操作，
 * 不会误关别处的编辑态。
 *
 * ⚠ 这三颗名字**不许**在这里单方面改：它们由各调用点自己传进来，要改得连着那三处一起改。
 * 正解是那三条链把失焦也接上（给 `LbFieldInput` 补一个焦点回调），接上之后这一份名单就该清空。
 */
private val NON_REPORTING_INPUT_IDS = listOf("settings", "intent_editor", "scheme_adjust")

/**
 * 本屏自己那份 `OnBackPressedDispatcherOwner`，用来让返回键先退掉输入。
 *
 * 为什么必须有这一颗、而且只能住在这一屏：宿主是 Service 不是 Activity，
 * `OverlayWindowHost.newComposeView()` 只挂了 lifecycle / ViewModelStore / SavedStateRegistry
 * 三颗 view-tree owner，里没有 OnBackPressedDispatcher 那一颗，
 * `LocalOnBackPressedDispatcherOwner` 的默认值因此交回 null，而 activity-compose 1.9.0 的
 * `BackHandler` 拿到 null 走的是 `throw IllegalStateException` 那一条
 * （对着 `BackHandlerKt` 字节码核过，null 分支是 athrow，不是记一条日志）⇒
 * 只写 `BackHandler` 会让整扇面板起不来，不是"接不上"。
 *
 * 生命周期交的是 **Service 那一颗**（`onStartCommand` 末尾已经落到 RESUMED）：
 * `BackHandler` 走的 `addCallback(owner, callback)` 按生命周期决定启用与否，停在 CREATED
 * 的话那颗回调永远是禁用态，返回键同样不会响。
 *
 * 本类不判断任何按键：吞不吞那一句由 `LoveBrainPanelScreen` 里 `hasEnabledCallbacks()` 那一格判。
 */
private class PanelBackPressedOwner(
    override val lifecycle: Lifecycle,
    override val onBackPressedDispatcher: OnBackPressedDispatcher
) : OnBackPressedDispatcherOwner

/**
 * 这一屏"哪一面在上面"与那一层背景浓度的**视图态持有者**。
 *
 * 里面只有两件事，而且都只在画的时候用：
 * · `settingsOpen`——齿轮那一扇整窗设置页开不开（换的是这一窗的内容，不是弹窗、不是半屏 Sheet、
 *   也不跳外部 Activity）；
 * · `backdropPercent`——拖动滑杆期间的实时预览值，只喂给背景那几层颜色的 alpha
 *   （面板两层底色，加上供出去给消息区大面积底读的那颗浓度读数），别处不读它。
 *
 * 它**不认识 ViewModel、也不碰盘**：落盘那一句由面板在 `onOpacityCommit` 里调
 * `setPanelBackdropOpacityPercent`（与首页同一份落盘口，这里不开第二条）。
 * 之所以做成一颗持有者而不是两颗局部布尔：`UiLayerDependencyContractTest` 里
 * `panel decision surfaces are held by state holders, not ad-hoc booleans` 那一格管着
 * "屏幕函数中间摊一堆局部可见性布尔"这个形状，新开的决策面必须走 holder。
 */
private class PanelSurfaceHolder(
    initialBackdropPercent: Int,
    initialCardLayout: ReplyCardLayout
) {
    var settingsOpen: Boolean by mutableStateOf(false)
        private set
    var backdropPercent: Int by mutableIntStateOf(PanelBackdropOpacity.snapPercent(initialBackdropPercent))
        private set
    /**
     * 回复卡片方向的**组合期内读数**。
     *
     * 真源仍是盘上那一格（`ReplyCardLayout.PREF_KEY`，读口写在 `LoveBrainViewModel`）；这一颗只
     * 负责"拨一下开关当场就换摆法"，不必等面板重开一次。初值由宿主从 VM 的读盘口交进来，
     * 所以服务重启、窗口重建后仍然跟着盘走，这里不写盘、也不另存一份。
     */
    var cardLayout: ReplyCardLayout by mutableStateOf(initialCardLayout)
        private set

    fun openSettings() { settingsOpen = true }
    fun closeSettings() { settingsOpen = false }

    /** 预览：越界与刻度外的值先落回合法刻度，画出去的永远就是盘上可能存着的那一档 */
    fun setBackdropPreview(percent: Int) {
        backdropPercent = PanelBackdropOpacity.snapPercent(percent)
    }

    /**
     * 换方向：只换画侧读数；结果集、筛选与阅读位置的主人不在这里——换摆法不重新请求、
     * 不重新付费，也不清空当前已经拿到的回复。
     *
     * 名字不叫 `setCardLayout`：那颗属性是 `private set`，Kotlin 已经占用了 `setCardLayout` 这颗
     * JVM setter，同名函数会撞成 platform declaration clash（同一格 JVM 签名）。
     */
    fun applyCardLayout(layout: ReplyCardLayout) { cardLayout = layout }
}

@Composable
private fun rememberPanelSurfaceHolder(
    initialBackdropPercent: Int,
    initialCardLayout: ReplyCardLayout
): PanelSurfaceHolder = remember { PanelSurfaceHolder(initialBackdropPercent, initialCardLayout) }

/**
 * 齿轮那一扇**整窗设置页**的宿主接线：正文本体住在 `ui/panel/settings/LoveBrainSettingsContent`，
 * 这一格只做两件事——把背景层当前浓度交出去、把预览与写盘两条口接回来。
 *
 * 设置页当前分三组（分组与各行的主人是 `LoveBrainSettingsContent`，这里只喂读数与回调）：
 * 「显示」透明度 → 悬浮图标大小 → 回复显示（卡片纵向排列）；「内容」当前知识库 → 持续意图
 * （知识库排在意图前面，因为意图是按库隔离的那一份数据）；「悬浮助手」开关＋关闭动作。
 * 供应商/模型/超时在首页"模型供应商"那一格，捕获范围在首页"消息捕获"那一格，都不在这页。
 *
 * ⚠ 以前这一格会在**组合阶段同步枚举整机安装包**（`selectableCaptureTargets(context)`）并为了
 * 供应商那一行 `koinViewModel()` 依赖容器——那是"第一次点击设置卡一下"最可疑的一处
 * （**是否真是它，要用点击前后耗时量过才算**，目前没有量过）。现在这一页既不碰 PackageManager，
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
    /** 齿轮一开整窗换成这一页，主面那颗收起跟着没了出口，所以这页要有一颗自己的收起；
     *  这颗只把宿主那一次点击转下去，本页不判断该不该收 */
    onCollapse: () -> Unit,
    onInputIntent: (String) -> Unit,
    /** 「回到 App 里启动」那颗：拨开时交回既有启动链（悬浮窗起不了授权页那一跳，只有 Activity 认得） */
    onOpenAppPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 悬浮助手此刻开着没有：**唯一判据是那颗真实窗口状态**，页面不自己记一份布尔。
    // `TEMP_HIDDEN` 读出来仍是"开着"——窗暂时藏了、服务与前台通知都还在跑，
    // 把它当"已关闭"就是让用户以为窗没了、屏上却还留着一颗会亮回来的球。
    val windowState by FloatingService.windowStateFlow.collectAsStateWithLifecycle()
    // 档位读数走 VM 那对函数口子：盘上唯一那一格，刻度尺长在 `AppConfig.BubbleSizeTier`。
    val bubbleSizeDp = viewModel.bubbleSizeDp
    // 持续意图：设置页那一格读同一份 config，一条写口 onIntentChange 拼一次 save。
    // 这一格只交"正文 + 开没开"两件事，不再传期限、也不再传"要不要重算到期时刻"——
    // 落盘形状由写侧一处统一（`IntentController.save`），界面这边不留第二套判据。
    // 为什么两条入口（设置页与编辑浮层）必须共用同一条写口：它们改的是同一格数据，
    // 各写各的就会出现"从设置页开一次、从浮层开一次，落盘形状不一样"；判据只留一份
    // 也才可在 JVM 上直接证伪，界面这一侧不留第二套规则。
    val intentConfig by viewModel.intents.config.collectAsStateWithLifecycle()
    // 那一笔写盘还在飞吗 + 「已保存」的成功票根：两个都直接读写侧那一颗真值。
    // 设置页那一格不自己推演"我按了一下所以应该在飞"，也不留第二本账——
    // 上一版那里是一颗 1.5 秒的时间窗，失败那一路靠到点放行；真值本来就在写侧，读它就行。
    val intentSaving by viewModel.intents.isSaving.collectAsStateWithLifecycle()
    val intentSaveReceipts by viewModel.intents.savedReceipts.collectAsStateWithLifecycle()
    val activeKb by viewModel.activeKb.collectAsStateWithLifecycle()
    // 知识库列表改用 StateFlow 收集（不是一次性快照）——设置一直打开时新建/改名后也能收到最新列表
    val kbList by viewModel.knowledgeBasesState.collectAsStateWithLifecycle()
    // 面板级通知（同一条 [NoticeBoard.current]）：设置页盖住主面的通知位，失败/回执那几句
    // 以前画在主面那一格——用户在设置页里根本看不见。现在同一条也画在设置页页首。
    val currentNotice by viewModel.currentNotice.collectAsStateWithLifecycle()
    // 打开设置页这一拍重读一次真目录：这颗 VM 是服务那一侧 `by inject()` 懒解析的，
    // 建库/改名/删库都发生在 App 主页面（另一个实例），不重读这一拍，设置页就一直背一份
    // 服务起来那一刻的旧名单——用户盘上明明有两块库，这一页却只有一块、行根点不开，
    // 真机上读到的就是"当前知识库无法切换"（10-10、10-12 两轮报的是同一件事）。
    // 复用 [LoveBrainViewModel.refreshKnowledgeBases] 那条已有的重读链，不另写第二条。
    LaunchedEffect(Unit) {
        viewModel.refreshKnowledgeBases()
    }
    LoveBrainSettingsContent(
        onBack = onBack,
        onCollapse = onCollapse,
        assistantOn = FloatingService.isAssistantOn(windowState),
        // 拨开：这一格起不了授权页那一跳（`startActivity` 在 settings 那一族是被门禁禁掉的，
        // 见 `SettingsPageStructureTest`），交回「去 App 主页面」那颗既有出口——
        // 启动与权限检查仍走首页那一条真实链，本页不复制第二份启动逻辑。
        onAssistantEnable = onOpenAppPage,
        // 拨关：与首页那颗停止（■）**同一个端口、同一个方法**（`FloatingServiceHomePort.stop()`），
        // 最终 `stopSelf()` 经 `onDestroy` 清理；面板/球/前台通知一起退，不留一颗还在跑的透明窗口。
        onAssistantClose = { FloatingServiceHomePort.stop() },
        opacityPercent = surface.backdropPercent,
        onOpacityPreview = { percent -> surface.setBackdropPreview(percent) },
        onOpacityCommit = { percent ->
            surface.setBackdropPreview(percent)
            viewModel.setPanelBackdropOpacityPercent(percent)
            // 悬浮球与面板背景共用同一个百分比：松手落盘后让球重读一次，
            // 否则换了浓度要重启服务才落到球上——少了这一行，"同一个百分比同时作用于两者"
            // 这个行为就不成立。
            FloatingService.instance?.refreshOverlayDisplaySettings()
        },
        bubbleSizeDp = bubbleSizeDp,
        onBubbleSizeChange = { dp ->
            viewModel.setBubbleSizeDp(dp)
            // 立即重读：窗口宽高、球径、角标、边界与面板贴边都从这一档重算，
            // 换完大小当下就能点击、拖动、吸边，不留给下一次服务启动。
            FloatingService.instance?.refreshOverlayDisplaySettings()
        },
        replyCardVertical = surface.cardLayout == ReplyCardLayout.VERTICAL,
        // 写盘走 VM 那对口子（盘上唯一那一格），换画侧读数走 surface 那颗组合期内的读数 ⇒ 当场换摆法。
        // 两处都不碰结果集、筛选与阅读位置：换布局不重新请求 AI、不重新付费、也不清空当前回复。
        onReplyCardVerticalChange = { vertical ->
            val next = if (vertical) ReplyCardLayout.VERTICAL else ReplyCardLayout.HORIZONTAL
            viewModel.setReplyCardLayout(next)
            surface.applyCardLayout(next)
        },
        intentEnabled = intentConfig.enabled,
        intentText = intentConfig.text,
        intentSaving = intentSaving,
        intentSaveReceipts = intentSaveReceipts,
        // 设置页与编辑浮层走同一份合同：界面只交"正文 + 开没开"两件事，
        // 落盘形状由写侧统一，这里不再传期限决议。
        onIntentChange = { text, enabled ->
            viewModel.intents.save(text, enabled)
        },
        knowledgeBases = kbList,
        activeKbName = activeKb?.name,
        onSwitchKb = { name -> viewModel.switchActiveKb(name) },
        notice = currentNotice,
        onDismissNotice = { id -> viewModel.endCurrentNotice(id) },
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
     * 「去设置」那颗：缺模型供应商时点它要进 **App 主页面**，
     * 不是悬浮窗里那页调透明度的设置——供应商/模型只在主页面配，进错那一页用户就找不到出口。
     * 齿轮那颗仍走 `surface.openSettings()`：**两条路刻意分开**，
     * 早先"去设置与齿轮必须是同一扇门"的约定已被推翻，别再合回去。
     *
     * 这颗**故意没有默认值**：宿主没接线就得编译不过，不会留下一颗点了没反应的假入口
     * （这一带惯常的写法是"没接线就不画"，而这一颗是非画不可的失败出口）。
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
    // 「仅看本轮」：状态源在 RoundStateStore，面板只读；写口只有 toggle 那一条（长按那条隐蔽入口已废）。
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
    // 那一笔写盘还在飞吗：编辑浮层那颗保存键读它把自己禁用（重复点不再开第二条协程）
    val intentSaving by viewModel.intents.isSaving.collectAsStateWithLifecycle()

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

    // 点踩不弹原因面板（2026-10-03 定下：那扇弹层挡视线、又多一次操作，把案例记下来就够了）：
    // 这里**故意不收集**任何"当前案例"的展示状态——一收集就把 UI 状态拉回面板这一侧。
    // 真实的保存通路（面板这一侧不参与）：
    //   `LoveBrainViewModel.setFeedback` → `FeedbackCaseController.toggle`（点击当刻同步建案例）
    //   → `feature/feedback/recordDislikeCase`（先按「方案 identity + 本轮生成版本身份」查重，再落盘）
    //   → `FeedbackCaseController.persistCase`（**把成败返回给调用方**，不许吞成成功：
    //     写盘失败却说"已记录"，用户就再也不会去补那一条）
    //   → 回执那一格 `LoveBrainViewModel.reportDislikeCaseSave`：`Recorded` / `AlreadyRecorded` 走绿色成功格
    //     （`showSuccessNotice` → `NoticeBoard.Channel.Knowledge` → `LbStateTone.Success`，3 秒自动消失）；
    //     `Failed` 走既有警告格（`showPanelWarning` → `Channel.Warning` → `LbStateTone.Warning`），
    //     并且留着当前候选、回复与用户刚点的那一下踩。两句文案就是 `R.string.notice_recorded` /
    //     `notice_record_failed` 本身，成功那一格从不画成黄色警告。

    // 「记录实际发送」这一条用户可见通路整体退场（弹层、专属状态持有者、与它的成败回执一起摘掉）。
    // VM 那侧 `recordActualSentMessage` / 点赞 / 记入知识库的语义一个字没动，既有数据不清理、
    // 不迁移；「复制」也**不**被标成"已发送"——那件事从来不是这个入口的含义。
    // 摘掉的是界面与其状态持有者，被摘那几颗没有留第二份副本，界面上也就不存在"半条通路"。

    // 记忆纠正中心：开合归 holder（不在屏幕函数里摊 `var showXxx` 布尔），记录内容仍由 VM 异步喂进来
    val correctionCenter = rememberCorrectionCenterHolder()
    // 「本轮参考」那两条记忆的两颗纠正浮层（暂停时长 / 标记为错误）：状态与渲染的主人
    // 从结果区那一行里搬到这里——浮层要挂在整个面板这一层，遮罩才盖得住全屏而不是只盖住那一行
    val memoryCorrectionFlow = rememberMemoryCorrectionFlow()
    var correctionCenterCorrections by remember { mutableStateOf<Map<String, com.lovebrain.app.model.MemoryCorrection>>(emptyMap()) }

    // 齿轮那扇整窗设置页的开合 + 面板背景浓度的实时预览：这一屏"哪一面在上面"只认这一颗持有者。
    // 背景浓度**不**走 `ComposeView.alpha`（那条通道归窗口淡入淡出动画所有，服务侧会把它复位），
    // 预览值只影响下面那层底色，正文一个字都不乘它。
    val surface = rememberPanelSurfaceHolder(
        viewModel.panelBackdropOpacityPercent,
        viewModel.replyCardLayout
    )

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
    val keyboard = LocalSoftwareKeyboardController.current
    // ── 宿主失焦交接的**唯一出口**：输入焦点与输入法（IME）状态必须成对交回 ──────────
    // 一次交接同时做三件事，顺序不许倒，也不许在别处再拼第二份：
    // 1. `keyboard?.hide()`——收输入法。悬浮窗在 EDITING 那一档时输入法挂在**窗口 token** 上，
    //    只把 Compose 焦点交回去不保证键盘自己收（此前这三处就是只"交出"、从不"收键盘"）。
    // 2. `focusManager.clearFocus(force = true)`——交回 Compose 焦点。`force` 才动得了用户
    //    正在打字那一颗**主动**焦点；不带 force 的 clearFocus 只清非主动焦点，等于什么都没交。
    // 3. 把那三条**只有"进入编辑"信号、没有失焦信号**的路逐条点名给宿主
    //    （[NON_REPORTING_INPUT_IDS]）：`PanelInputFocusOwner.onInputFocusChanged` 按
    //    `activeInputId` 认人——不是它记着的那一格就直接 return，所以多发的那几句是空操作，
    //    不会误关别处的编辑态；而真的由这三条路之一进的那一次编辑，这一刻就落回 PASSIVE。
    //    「reply / counseling」两格不需要这一句：它们自己 `onFocusChanged(false)` 就会报（见上面）。
    // `rememberUpdatedState` 交出去的是**当下这一份**接线：切页/关层那些 `LaunchedEffect`
    // 的 key 变了也不会拿第一次组合的旧 lambda 去调新的宿主回调。
    val handOffInput: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        NON_REPORTING_INPUT_IDS.forEach { onInputFocusChange(it, false) }
    }
    val currentHandOffInput by rememberUpdatedState(handOffInput)
    DisposableEffect(onClearComposeFocus) {
        // 宿主那一条 releaseInput 链（外点 / 收起 / 销毁）反过来要面板交焦点：
        // 同一个出口，不再在这里单独写一次不带 force 的 clearFocus。
        onClearComposeFocus { currentHandOffInput() }
        onDispose { }
    }

    // 切页时输入焦点与 IME 状态要明确移交。两页都留在组合里（切页要保留各自草稿），
    // 于是同一棵树里**同时**挂着回复那格的 `PanelTextInput` 与谈心那格的 `BasicTextField`——
    // 不交接的话，用户已经滑到谈心那一面，键盘与 `PanelInputFocusOwner.activeInputId`
    // 还停在 "reply" 那根光标上（窗口 flags 也还挂在 EDITING，服务侧据此决定软键盘）。
    // 这一句只做**交出**：不替下一页抢焦点——抢了就是"滑一下键盘自己弹出来"，
    // 那是用户没要求的第二件事；他要在那一面打字就点那一面的输入框（原有的直接获焦路径）。
    // 首次组合也会走这一句，那时没有任何焦点，`clearFocus` 与 `hide` 都是空操作。
    LaunchedEffect(panelMode) {
        currentHandOffInput()
    }

    // ── 另外三条失焦路径也走同一个交接出口 ─────────────────────────────────
    // 设置页盖层打开/关闭、意图编辑器关闭这三条路径同样需要交出 Compose 焦点。
    // 旧版只接了 `panelMode` 切页，设置页从主面盖上来（或收回去）时，正在获焦的那根
    // BasicTextField 不会自动失焦——`onFocusChanged(false)` 不触发，
    // `PanelInputFocusOwner.activeInputId` 不清、窗口仍挂 EDITING、IME 不收。
    // 用户从设置页回到主面时光标还停在刚才那格上，长按选区也跟着错位。
    //
    // `surface.settingsOpen` 一翻就清一次：打开时主面那几颗输入框被盖在下层、
    // 不该继续持焦；关闭时设置页那一排输入框跟着退场、也不该留着。
    LaunchedEffect(surface.settingsOpen) {
        currentHandOffInput()
    }

    // 意图编辑器关闭时同样交出焦点——LbModalSheet 的退场动画跑完之后，
    // 编辑器里那颗 LbFieldInput 仍可能持焦，不清就会留一个看不见的输入框挂着 EDITING。
    // `showIntentEditor` 从 true→false 时清一次。
    LaunchedEffect(showIntentEditor) {
        if (!showIntentEditor) {
            currentHandOffInput()
        }
    }

    // **返回键 = 先退出输入**。这一环在悬浮窗里不会自动成立（`BackHandler` 在本屏必须自己造
    // owner 才活得成，见上面那颗 `PanelBackPressedOwner`）。收到这一下之后做三件事：收键盘、
    // 交回 Compose 焦点、点名宿主落回 PASSIVE（全走上面那颗唯一出口 `handOffInput`，
    // 不在这里再拼一份），然后只把**意图编辑那一扇**
    // 关掉（与它 X / 遮罩那两条出口同一句 `dismissEditor`）。
    // 不关整扇面板、不切页、不碰设置页：那些各有自己的所有者（`onCollapse` / 齿轮那扇门）。
    //
    // ⚠ 为什么这一格要自己提供一份 `OnBackPressedDispatcherOwner`，而不是像 Activity 那几屏
    //   直接写 `BackHandler { }`：这一屏的宿主是 Service 不是 Activity，而
    //   `OverlayWindowHost.newComposeView()` 只挂了 Lifecycle / ViewModelStore / SavedState
    //   三颗 owner——**没有这一颗**。`LocalOnBackPressedDispatcherOwner` 的默认值沿 ViewTree
    //   找不到它就交回 null，activity-compose 1.9.0 的 `BackHandler` 走到的是
    //   `throw IllegalStateException("No OnBackPressedDispatcherOwner was provided …")`
    //   （对着 `BackHandlerKt` 的字节码核过：null 分支是 athrow，不是记日志）。
    //   ⇒ 光写 `BackHandler` 不是"接不上"，是**整扇面板起不来**。这一颗 owner 与那颗泵按键的
    //   监听都必须住在这一屏里：窗口的 flags、协程作用域、其余三颗 owner 归窗口宿主管，
    //   这一屏不越过去改它们。
    // ⚠ 泵这一颗的是 `View.setOnKeyListener`：输入法还开着时，按键先走
    //   `View.dispatchKeyEventPreIme` 那一站，而 AOSP 在那一站只认宿主 view 上挂着的
    //   OnKeyListener（且只认 KEYCODE_BACK）；本屏根 view 就是这扇窗的 `mView` ⇒ 这一站收得到。
    //   只有窗口在 EDITING 才收得到按键（PASSIVE 挂着 NOT_FOCUSABLE），所以"收到返回键"
    //   那一刻必然正在输入 ⇒ 吞下这一对动作就是把"退输入"做完，不替宿主 App 抢别的返回语义。
    //   `hasEnabledCallbacks()` 为假（回调被生命周期关掉那一刻）就不吞，原样交回系统。
    val panelRootView = LocalView.current
    val backDispatcher = remember { OnBackPressedDispatcher() }
    val panelLifecycleOwner = LocalLifecycleOwner.current
    val backOwner = remember(backDispatcher, panelLifecycleOwner) {
        PanelBackPressedOwner(panelLifecycleOwner.lifecycle, backDispatcher)
    }
    DisposableEffect(panelRootView, backDispatcher) {
        panelRootView.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK || !backDispatcher.hasEnabledCallbacks()) {
                return@setOnKeyListener false
            }
            // DOWN 与 UP 一起吞，只在 UP 那一下真的退输入：只吞一半会让这一对动作残缺
            if (event.action == KeyEvent.ACTION_UP) backDispatcher.onBackPressed()
            true
        }
        onDispose { panelRootView.setOnKeyListener(null) }
    }

    // 面板背景浓度在这一层有**三个**去处，全部只经 `PanelBackdropOpacity.effectiveAlpha` 这一颗尺：
    // ① 下面那两层底色（根与设置页那一层）各自直接取一次；
    // ② 这一颗 [LocalPanelBackdropAlpha] —— 消息区那两块大面积底读它（见 `reply/MessageList.kt`）。
    // 供出去的是**算完的 alpha**，不是百分比，也不是第二把尺：读它的那两格不许再换算、再相乘。
    // 以前这里也 provides 过一颗浓度读数，但全仓**没有任何读取方**，那时清掉是对的；
    // 这一笔它有了读它的地方，所以才回来——尺还是那一颗，作用点从两层变成三层。
    // 面板这一侧永远不带可读下限（`idleDimmed = false`、下限那颗不传）：面板淡到 20% 用户看得见、
    // 也滑得回来，只有悬浮球因为是全屏唯一手势入口才需要那颗下限。
    // `LocalOnBackPressedDispatcherOwner` 必须留在这一层：`BackHandler` 读的是
    // 它**所在位置**的那份值，写在下面这层 provider 之外就还是那颗会抛的 null。
    val backdropAlpha = PanelBackdropOpacity.effectiveAlpha(surface.backdropPercent, idleDimmed = false)
    CompositionLocalProvider(
        LocalOnBackPressedDispatcherOwner provides backOwner,
        LocalPanelBackdropAlpha provides backdropAlpha
    ) {
    // 返回键那一句的正文住在这里（见上面那段：出了这层 provider 就拿不到本屏那份 owner）
    BackHandler {
        currentHandOffInput()
        if (showIntentEditor) viewModel.intents.dismissEditor()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 设置页开着的时候主面**整层不画**（alpha 0，树还在——会话、焦点、展开态
                // 都不因此重建，见下面 overlay 那段的说明）：以前只靠设置页那层半透明底压着，
                // 用户把透明度调高之后主面的行、字、两颗收起全都从底下透出来——
                // 右上角两颗收起叠成一团重影（10-12 报的"重叠了"），主面的行混进设置页的行距里
                // 读成"每行的间隔都不一样"。alpha 只管画，节点还在树上，语义与状态一寸不动。
                .alpha(if (surface.settingsOpen) 0f else 1f)
                // 先 clip 再画底色：不裁剪时背景与描边按矩形外沿铺出去，
                // 悬浮窗本身是透明背景，那圈直角外露就是一块难看的矩形板
                .clip(LoveBrainShape.xl)
                // 面板背景浓度在这一层的作用点就是这一层颜色的 alpha：`PanelBackdropOpacity.effectiveAlpha`
                // 把盘上那个整数（20..100，默认 100 时外观与引入这一项之前完全一致）换成 alpha。
                // 它与悬浮球读的是**同一颗函数**（球那一侧多一颗"闲置降档"与一处可读下限，
                // 这里两个都不带：面板只有用户正在用时才在屏上，不存在闲置态，淡到什么程度也看得见），
                // 所以"同一个百分比同时作用于面板背景与悬浮图标"这句话在代码里只有一条实现路径。
                // 不借 `ComposeView.alpha` ——那条通道归窗口淡入淡出动画所有（服务侧在 present/hide
                // 路径上会把它复位成 1），借它画浓度第一次收起面板就会把设置抹掉；
                // 也不压正文：下面所有文字、图标、卡片都保持满不透明度。
                .background(SurfaceBase.copy(alpha = PanelBackdropOpacity.effectiveAlpha(surface.backdropPercent, idleDimmed = false)))
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border.copy(alpha = 0.5f), LoveBrainShape.xl)
                // 顶部那一段内边距（`Spacing.lg`）现在由命中带那只容器**自己占进布局**
                // （见下面 `DragHandle`：12 + 4 + 4 = 20dp），这里只留左右与底部，
                // 否则那条带子要么虚高、要么把页头整片顶下去 12dp。页头/滑页/输入区位置一寸没动。
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg)
        ) {
            // 2026-10-06 改的结构：原来这里是 `if (settingsOpen) { 设置页 } else { 主面 }`——整棵主面树被换掉。
            // 现在主面永远在树上，设置页改为盖一层（见 Column 之外那扇 overlay）。
            // 会话、输入、卡片展开态本就住在 ViewModel 与 holder 上，主面不卸树 ≠ 多保留什么，
            // 只是主面那些 remember 与收集流不再因切设置页被丢回重建。
                // 顶部这一格：**容器自己**就是那条命中带（`DragBand.hitHeight` = 20dp），
                // 不再靠子节点向上下溢出把带子画到父盒子之外（旧写法与统计条同叠在一只 4dp 盒子里，
                // 兄弟命中按 z 序倒着走、第一个有命中的那一支走到底 ⇒ 统计那一行之内根本进不到带子）。
                // 现在统计条挂进带子里面：子层只认领横向（换组），父层认领拖动 ⇒
                // 同一块矩形里竖向拖动照样移动窗口。⚠ 认领范围按实测说（`DragHandleHitBandTest`）：
                // 统计那一格装了换组手势时，整条带子的横向都先归它（Compose 的命中链把子树的
                // pointer-input 节点连同父层一起收），带子那一段的横向只在它不装手势时才归窗口。
                // 这一格把根 Column 从前那段顶部内边距（Spacing.lg）与页头之前那颗 Spacer
                // （Spacing.sm）一起占进布局，页头、滑页、输入区的绝对位置一寸没动。
                DragHandle(
                    onMove = onMove,
                    content = {
                        // 可见那一格仍是 4dp 那条细槽，摆在带子里第 12dp 那一档 ⇒ 统计那一行的
                        // 像素位置与改版前完全一致（细可见标记不因为好拖而画粗）。
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = DragBand.overhangAbove)
                                .fillMaxWidth()
                                .height(DragBand.slot)
                                .testTag(DRAG_HANDLE_SLOT_TAG),
                            contentAlignment = Alignment.Center
                        ) {
                            // 顶部使用统计：**永远只占一行**。每一格是"标签＋值"合并成的**一段** Text
                            // （`maxLines=1`、`softWrap=false`）——`LbMetricDensity.Inline` 那一档把一格拆成
                            // 标签与数值两颗可各自换行的 Text，那正是"冒出第二行"的来源；
                            // 分组、轮播与渐隐都归 `stats` 那一族。
                            // 但**换组与横向查看**归 `UsageStatBar`：一行放得下就把五格平铺、不轮播也不渐隐；
                            // 放不下就按现有字段切出的完整分组整组换，两侧用淡渐隐表示"那边还有内容"。
                            // 渲染口径一格没改：五格、首字那格的 >0 条件、「—」占位都原样，
                            // 标签与带单位的数值串也原样照搬；「今日」「累计」两格的费用仍走**与首页/使用概览页
                            // 同一颗判据** `costReadout`（`viewmodel/UsageStats.kt`）：一笔可计价记录都没记过的时候
                            // 念「—」，绝不念成 `¥0.000`（未知 ≠ 免费）。页面这一侧不重算任何钱。
                            // 「本次」那格本来就是 `Double?`：没有数就念「—」，占位串与另两格同一份资源。
                            // 槽位仍是页头上面那 4dp 那一档（现在它住在带子里）：组件按自己一行的高度居中
                            // 溢出绘制（`unbounded = true`），所以它既挤不出第二行，也不会被那一档夹掉。
                            // ⚠ 它的横向拖拽长在 `UsageStatBar` 自己那颗节点上（子层），**不许**搬到外面那条
                            // 拖动带上：两颗 owner 一旦并成一颗，带子左右那几 dp 的横滑就不再移动窗口。
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
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true)
                            )
                        }
                    }
                )

                PanelHeader(
                    panelMode = panelMode,
                    onModeChange = { viewModel.setPanelMode(it) },
                    // 顶部只剩两段（回复/谈心），panelMode 是"哪一页在上面"的唯一真源。
                    // 收起：由宿主把面板收成悬浮球（不是停服务），这一格只转那一次点击
                    onCollapse = onCollapse,

                    // 页头整行也是拖动窗口的手柄：不接这颗，只能靠顶部那条细槽拖动
                    // 输出模式（回复/主动发）的状态在 VM 里，页头不持有它
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
                                    // 原来这里是内联中文 `"关闭使用提示"`，而 `R.string.a11y_close_onboarding`
                                    // 早就在资源里、从没被引用 ⇒ 读屏念的是一颗硬编码中文（整屏量语义时发现的），
                                    // 也绕开了 `UiStringLiteralBudgetTest` 那条"用户可见文案走资源"的预算
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
                // 不该把一次性消息挤到自己的位置之上（改结构之前正是这个顺序反了）。
                VectorPillsRow(vector = currentVector, delta = vectorDelta)
                Spacer(Modifier.height(Spacing.xs))

                // ── 回复(0) ↔ 谈心(1)：两页左右滑互切，切页与滑动手势共用同一颗页索引 ──────
                // 页索引只有**一颗真 owner**：`viewModel.composer.panelMode`（上面那行 collect）。
                // · 点 tab：`onModeChange` → `setPanelMode` → 这一颗变 → 画哪一页跟着变；
                // · 左右滑：手势结束时也只投一次 `setPanelMode`，宿主里没有第二颗"现在在哪一页"；
                // · `panelMode ↔ 页索引` 的换算只有一对函数（`panelModeToPage`/`panelPageToMode`，
                //   住在 `PanelPagePager.kt`），页头那两段读的是同一对——所以"tab 指着谈心、
                //   身体还画着回复"这种两处状态对不上的形状在这棵树上写不出来。
                // 挂上就不再卸（见 `PanelPagePager` 文件头）：谈心那页的本地草稿与历史
                // （`CounselingPanel` 里 `followUpText` / `counselingHistory` 那两颗 `remember`）、
                // 回复这页正在收的流式结果与列表展开态都不随切页丢，这一格也不发起、不取消任何东西。
                PanelPagePager(
                    currentPage = panelModeToPage(panelMode),
                    onPageChange = { page -> viewModel.setPanelMode(panelPageToMode(page)) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { page ->
                if (page == 0) {
                // 回复侧只剩这一格。
                // 原来这一支挂在页外层 Column 上，现在整支搬进页槽位，正文一行没改；
                // 下面这些行的缩进**故意**留在原来的档位（跟着 `if` 一起再缩一层会把 240 行
                // 正文全拖进纯缩进 diff，真改动就被 240 行缩进淹掉）。
                Column(modifier = Modifier.fillMaxSize()) {
                val inputFocusRequester = remember { FocusRequester() }
                // ── 次级控制的两处挂载点，共用同一份状态 ──────────────────────────
                // 口径只算一次：`hasRealDialogueRows(messages)` 就是 `MessageList` 内部那一口，
                // 有真实消息 → 挂输入区行 2；没有 → 收进消息卡空态分支。两处二选一，绝不在
                // 两处各存一份开关（那才是"意图与仅看本轮各长两个所有者"的第二份状态）。
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
                    // 输入对象与"自动捕获角色"（`currentRole`）是两条轴。投 SetInputKind 只改"这一格在写谁"，
                    // 不动捕获角色——"选《补充》后新增消息被标成上一个角色"就是这么错标上去的，
                    // 两条轴各改各的才是修法。
                    inputKind = inputKind,
                    onInputKindChange = { viewModel.composer.accept(ComposerStore.Intent.SetInputKind(it)) },
                    // 「仅看本轮」常驻输入行 1（在 ＋ 与输入框之间），给了回调才画。
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
                                // 选《补充》时这一格写的是"给军师的话"，不是聊天流里的一条消息。
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

                // ── 持续意图入口：这一格现在什么都不画，入口在设置页那一组里 ──
                // 为什么这一条功能不能跟着"精简掉一排控件"一起删：回复链每次生成都读
                // `intents.config` 拼进 prompt，它是常驻能力，不是这一屏的可选装饰。
                // 它一度落在这里"另画一排"——于是屏上有两排次级控件（这一排 + 输入区那颗
                // 「仅看本轮」那一排），同一屏两套次级开关才是这里要收掉的形状。
                // 现在入口走设置页 `SettingsIntentEntry`；意图数据按知识库隔离，
                // 没有活动知识库时写不进去（`IntentController.save` 拿不到库名就直接返回）。

                val density = androidx.compose.ui.platform.LocalDensity.current
                var messageListHeight by remember { mutableStateOf(PanelDimens.MESSAGE_LIST_DEFAULT_HEIGHT_DP.dp) }
                // ⚠ 空态那一档**不能**沿用用户拖出来的列表高度：空态自己需要
                //   图标 48 + 间距 8 + 动作热区 48 = 104dp（`MessageList` 里那条
                //   `heightIn(min = EMPTY_ACTION_MIN_HEIGHT_DP)` 是 48），再加上空态那一列
                //   上下各 8 的内边距 = 120dp，也就是 `MESSAGE_LIST_EMPTY_HEIGHT_DP`。
                //   可拖区间下限只有 80dp ⇒ 父约束把 min 夹到 max 以下，那颗动作被压成
                //   **8dp 高**（面板整屏第一次量到这个数）。拖拽那一档只管"有消息"的时候。
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
                    // 横滑方向映射的另一半：横滑**向内** = 把这一条换成对方（只交 id，
                    // 新角色由持有列表的一方算，见 ComposerStore.Intent.SwitchMessageRole）。
                    // 这里不碰 composeRole / inputKind / 捕获角色那三颗 —— 换一条**已有消息**的说话人，
                    // 不等于改用户此刻在输入行选的那一颗（同一屏两处读数被一次手势连坐改掉，正是这里要拦的）。
                    onSwitchRole = { id ->
                        viewModel.composer.accept(ComposerStore.Intent.SwitchMessageRole(id))
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
                    // 军师备注（《补充》那一格写的话）：读 ComposerStore 那唯一一颗出口（含未提交草稿），
                    // 页面不另存第二份状态；渲染点在消息卡内、最后一个真实气泡下面那一行灰字，
                    // 它不是聊天消息，也不进真实对话列表。点它进同一个编辑器（BeginNoteEdit）。
                    noteText = viewModel.composer.ideaHint().takeIf { it.isNotBlank() },
                    onEditNote = { viewModel.composer.accept(ComposerStore.Intent.BeginNoteEdit) },
                    // 侧滑清除备注：投的是 `ClearNote`，与删一条聊天消息是两条不同的意图——
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

                // 主操作按钮的形态由 composerMode / isGenerating / 有没有结果这三读数量决定，一共四档
                // （实现与逐档语义在 `reply/ReplyPrimaryActions.kt`）：
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
                                // 回复卡摆法：默认纵向，设置页那颗开关换的就是这一颗读数。
                                // 它**不参与**请求、也不进本轮身份——换方向不重新生成、不重新付费、不清空回复。
                                cardLayout = surface.cardLayout,
                                streamingCoreText = streamingCoreText,
                                isGeneratingCore = isGeneratingCore,
                                streamingSchemes = streamingSchemes,
                                feedbacks = feedbacks,
            onFeedback = { scheme, fb ->
            viewModel.setFeedback(scheme.identity.key, fb)
            // 点踩即建案例并落盘，这里不再展示原因面板、也不再消费任何"当前案例"的展示状态
            },
                                onCopyScheme = { scheme ->
                                    val reply = viewModel.copyScheme(scheme)
                                    onCopy(reply)
                                },
                                onRetry = { viewModel.retryCurrentReply() },
                                // 本轮参考记忆 + 纠正回调
                                memoryRefs = viewModel.getCurrentMemoryRefs(),
                                // 显示侧保险：传**生成当时冻结**的那份「仅看本轮」，
                                // 不传上面那颗实时 `onlyThisRound` 读数——开关可能是这一轮之后才拨的，
                                // 拿实时值接显示侧会让这一轮真用过的引用凭空消失。
                                frozenOnlyThisRound = viewModel.replyResultOnlyThisRound,
                                correctionFlow = memoryCorrectionFlow,
                                onCorrection = { memoryId, action, replacementText, muteDuration ->
                                    viewModel.applyMemoryCorrection(memoryId, action, replacementText, "", muteDuration)
                                },
                                onUndoCorrection = { memoryId ->
                                    viewModel.undoMemoryCorrection(memoryId)
                                },
                                providerReady = isProviderReady,
                                // 缺模型那颗「去设置」进的是 **App 主页面**（这颗由 `FloatingService`
                                // 的 `openAppMainPage()` 起 `SetupActivity`），不是悬浮窗里调透明度那一页——
                                // 供应商/模型在那一页配，悬浮窗这页里根本没有它，进错了就没有出口。
                                // 左上角那颗齿轮才走 `surface.openSettings()`。**两条路刻意分开**：
                                // "去设置与齿轮必须是同一扇门"这个旧约定已被推翻，别照旧注释合回去。
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

    // 2026-10-06：设置页改为盖一层（overlay），不再与主面互斥换树。
    // 主面那些 remember 与收集流不再因切设置页被丢回重建——会话、输入、卡片展开态
    // 本就住在 ViewModel 与 holder 上，盖一层只是让它们在树里不被拆掉。
    if (surface.settingsOpen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 主面虽然不画了（上面那颗 alpha），节点还在树上、还摸得着。设置页盖在它的
                // 上面，但只有落在设置页**控件**上的那一拍被设置页接走；落在空白处的拍子
                // 会穿过这层继续落到主面那批看不见的输入框、按钮上——暗着改字、暗着切页。
                // 这一圈只拦「拍」：detectTapGestures 的空实现把 down/up 消费掉（且都发生在
                // 子节点看过之后——Main pass 里叶先根后），主面那批可点件的按压链起不来。
                // ⚠ **不许碰 move**：旧写法（v17）在 awaitEachGesture 里把每颗没人消费的移动
                //   也顺手吃掉，真手指的拖动恰恰是一长串小步移动——透明度滑条的拖动链
                //   就这么被掐死（2026-10-15 真机报"滑不动"；`SettingsOverlayPointerGateTest`
                //   的 24 小步见证格是它的复现：8 大步活、24 小步死、无门活）。
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
                .clip(LoveBrainShape.xl)
                .background(SurfaceBase.copy(alpha = PanelBackdropOpacity.effectiveAlpha(surface.backdropPercent, idleDimmed = false)))
                .border(AppDimens.BORDER_WIDTH_DP.dp, Border.copy(alpha = 0.5f), LoveBrainShape.xl)
                // 同主面那一格：顶部内边距交回命中带自己占（这里页头之前没有 Spacer，
                // 所以带子只占 12 + 4 = 16dp，设置页正文的绝对位置一寸没动）
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg)
        ) {
            DragHandle(
                onMove = onMove,
                hitHeight = DragBand.settingsHitHeight
            )
            PanelSettingsPage(
                viewModel = viewModel,
                surface = surface,
                onBack = { surface.closeSettings() },
                onCollapse = onCollapse,
                onInputIntent = onInputIntent,
                onOpenAppPage = onOpenAppPage,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }

    // 点踩原因面板整块摘除：`DislikeReasonHost` / `DislikeReasonPanel` 这两颗已经不在源码树里，
    // 这里也不再为它们留任何状态或接线。
    // 保存链保留：`viewModel.setFeedback(...)` 那一句仍会建案例并落盘，空原因合法，
    // 落盘成功/失败由统一通知那一格报（见 `showSuccessNotice` 与 `FeedbackCaseController`）。

        // 持续意图编辑浮层——**面板根部**渲染，不挂在输入行那一列里面（遮罩要盖得住整屏，
        // 同 `CorrectionCenterHost` / `MemoryCorrectionFlowHost` 那两扇的处理）。
        // 显隐只有 `IntentController.showEditor` 这一处状态：开在哪块库由它自己冻结（openEditor 先绑库
        // 再翻可见性，dismissEditor 把两者一起收），保存回 `intents.save(...)`，关闭点名到 `dismissEditor()`。
        // 这一扇的宿主就是这一页，摘掉外层容器不会少掉任何能力。
        // visible 直接传给 LbModalSheet：它自己的 AnimatedVisibility 负责入退场动画
        // （200ms 淡入+缩放 / 200ms 淡出+缩放），不再外裹一层 AnimatedVisibility——
        // 外裹那一层会在退场时把整棵树摘掉，LbModalSheet 的 exit 动画永远跑不到。
            IntentEditorDialog(
                text = intentConfig.text,
                enabled = intentConfig.enabled,
                visible = showIntentEditor,
                // 在飞那一笔跟写侧同一颗读数：禁用而不是再开一条协程写同一份草稿
                saving = intentSaving,
                onSave = { text, enabled ->
                    // 与设置页同一份合同：界面只交"正文 + 开没开"两件事。
                    // 期限那一族的判据（重新启用要不要重算到期时刻、只改正文要不要保留旧时刻）
                    // 收在写侧一处，两条入口各判一份就会漂成两种落盘形状。
                    viewModel.intents.save(text, enabled)
                },
                onDismiss = { viewModel.intents.dismissEditor() },
                onInputIntent = { onInputIntent("intent_editor") }
            )

        // 「记录实际发送」那扇浮层连同它的状态接线一起摘掉，这里不留残件。
        // 点赞 / 采用 / 记入知识库这三条机制原样保留；VM 的 `recordActualSentMessage` 也原样留着，
        // 只是界面不再替用户手写"我实际发了什么"——那件事从来不是应用检测到的发送。

        // 记忆纠正中心——独立列出已停用／静音／隔离项，支持撤销。
        // 之前是面板顶层 Box 里一块 `Column(fillMaxWidth)` 内联展开区（无遮罩、不居中、
        // 里面每颗可点的实测只有 28x19dp，够不到热区下限）；现在走 CorrectionCenterHost → LbModalSheet，
        // 开合归 correctionCenter 那颗 holder。
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

        // 纠正浮层的唯一渲染处，挂在面板这一层（不是结果行里）——遮罩与热区才按整屏算
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
 * 它以前是本文件里自画的一颗异形（登记在 `OddShapeOwnershipTest` 的异形清单里），而且带着两个
 * **颜色旋钮**：`container: Color = SuccessBg`、`textColor: Color = Success`，
 * 第二处调用再现场填一对 `WarningBg`/`Warning`——"这条通知是什么语气"由调用方
 * 自选颜色，下一对颜色没有任何地方拦得住（`OddShapeOwnershipTest` 要挡的正是这个形状：
 * 页面自己拿颜色决定语气，颜色就会一页一个样）。
 * 现在语气走 [LbStateTone] 那张词表（字色与浅底成对，都只在 `LbAsyncState.kt` 里写一次），
 * 容器走 [LbStateContainer.Notice]（面板通知原先太大，这一档就是它的落点：轻通知收成**单档**——
 * `labelMedium` 11/16、圆角 `md`10、内边距横 12 竖 4、关闭那颗 14dp 字形坐
 * 24dp 盒；不直接把 `Strip` 压小是因为它还管着全站那些 empty/error 横条，另立一档才不动别处），
 * 那颗关闭转成设计系统里唯一的文字动作。
 *
 * 与那一颗自画的旧写法相比，变掉/补齐的有三件事：
 * - 关闭那颗原来是裸 `Box.clickable`：**没有声明 `role`**，盒子已经 48dp 见方，
 *   但读屏只念图标名、说不出它是按钮；现在角色与两轴热区都由那一处文字动作保证，
 *   名字改用 `R.string.a11y_close_notice`——那串中文原先是**内联字面量**，读屏念的就是硬编码那句，
 *   `UiStringLiteralBudgetTest` 数 `contentDescription` 那把尺也数不到它（资源里那条一直都在，只是没被引用）。
 * - 说明文字从 `labelSmall` 抬到组件那一档 `bodyMedium`（同 `RowActionButton` 归进
 *   `LbTextAction` 那次：不换所有者就自己定字号，要留住字号就得给组件开旋钮）。
 *   **2026-10-06 又收了一次**：面板轻通知收成单档，字阶落 `labelMedium` 11/16——
 *   这一档由 `LbStateContainer.Notice` 那一支自己持有（`StateMessage` 的 `style` 旋钮默认仍是
 *   `bodyMedium`，`Strip`/`Block` 两支一字未动），页面这一侧不填字号、也不开新旋钮。
 * - 原先那句 `maxLines = 1 + Ellipsis` 不再由组件提供：这三条话都有下半句
 *   （例如「已暂停本轮提及，下次生成将过滤此条记忆」），裁掉的正好是要看的那半句。
 *
 * 自动收起那三档时间现在由 `NoticeBoard` 那条队列自己带着（每条通知随身一个时限），
 * 这一颗只管画**正在显示的那一条**；三条文案的来源一个字没动。
 *
 * `internal` 不是给页面用的：这一条通知要由语义树测试**直接挂生产这一颗**，
 * 而不是在测试里再抄一份私有副本——副本不跟着生产代码改，测过了也不算数
 * （同一件事在本文件的 [ProactiveResultArea] 与 `ProviderFormBody` 上都是这么处理的）。
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
 * 主动发结果区：候选列表 + 加载/失败/空态，每张卡点一下就把正文送进剪贴板。
 *
 * `internal` 不是给页面用的：空态那一档要由语义树测试**直接挂生产这一颗**，
 * 而不是在测试里再抄一份私有副本——副本不跟着生产代码改，测过了也不算数
 * （同一件事在 `ReplyPrimaryActions` 与 `ProviderFormBody` 上也是这么处理的）。
 */
@Composable
internal fun ProactiveResultArea(
    isProactive: Boolean,
    options: List<ProactiveOption>,
    error: String?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 每张卡"策略行展开没有"的状态表，以**一批候选**为单位管（见下一格）：
    // 流式追加（同批内 options 变长）不动它——用户翻开的那张不许自己合上。
    val strategyOpen = remember { mutableStateMapOf<Int, Boolean>() }
    // 「默认收起」这个行为以**一批**为单位落实：新一轮开始（store 收到 ProactiveStarted 先清空）
    // 或用户切走清残留时，上一批翻开的展开态一起收掉——以前只按 index 记展开态、从不重置，
    // 再生成之后新的第 N 张会带着上一批第 N 张的展开姿态进场。
    // 展开态只认本批；"清空"这个信号是展示侧唯一拿得到的批次边界（requestId 不出 store）。
    LaunchedEffect(options.isEmpty()) {
        if (options.isEmpty()) strategyOpen.clear()
    }
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
                // 在这一格再塞一颗重试就会出现两颗出口——要的是失败就地看得见出口，不是要有两颗。
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
                // 主动发候选补回策略载荷（时机/先别发/需要准备）之后，正文仍是**唯一可发送内容**
                // （点击复制只复制 `opt.text`，见 [ProactiveOptionCard] 那颗整卡点击）。
                // 那三段收成"按需展开的次要行"，展开区里每行带自己的类别标签
                // （标签与行格式全走资源，页面不内联中文——常驻的角度那一行也是同一口径）。
                // 三段全空的旧候选与改前一致：不多空行、也不多箭头。
                val labels = ProactiveStrategyLabels(
                    angle = stringResource(R.string.proactive_label_angle),
                    timing = stringResource(R.string.proactive_label_timing),
                    holdBack = stringResource(R.string.proactive_label_hold_back),
                    prepare = stringResource(R.string.proactive_label_prepare),
                    line = stringResource(R.string.proactive_strategy_line),
                    copyOpener = stringResource(R.string.a11y_copy_opener)
                )
                val expandLabel = stringResource(R.string.action_expand)
                val collapseLabel = stringResource(R.string.action_collapse)
                options.forEachIndexed { index, opt ->
                    ProactiveOptionCard(
                        option = opt,
                        labels = labels,
                        expanded = strategyOpen[index] == true,
                        expandLabel = expandLabel,
                        collapseLabel = collapseLabel,
                        onToggleExpanded = { strategyOpen[index] = !(strategyOpen[index] == true) },
                        onCopy = onCopy
                    )
                }
                if (error != null) {
                    Text(error, style = AppTypography.labelSmall, color = Error)
                }
            }
        }
    }
}

/**
 * 主动发候选卡上那几行策略说明的标签集（策略说明要与可发送正文视觉分离）。
 *
 * 全部来自 strings 资源、由调用方一次性取好传进来——卡内不出现任何内联中文/标点
 * （这是 `UiStringLiteralBudgetTest` 那道预算闸的形状），将来换语言时卡里不用动代码。
 * [line] 是"标签：内容"的行格式（冒号住在资源里）。
 */
private data class ProactiveStrategyLabels(
    val angle: String,
    val timing: String,
    val holdBack: String,
    val prepare: String,
    val line: String,
    val copyOpener: String
)

/**
 * 一张主动发候选卡（可复制正文 + 常驻角度 + 按需展开的策略三段，从 `ProactiveResultArea` 里拆出来单独成件）：
 *
 * - **可复制正文在前**：`option.text` 独占第一行、正文字阶；角度是它下面那行常驻小字。
 * - **策略说明为辅、按需展开**：时机/先别发/需要准备三类默认收在一行"类别名"后面
 *   （收起时也讲得出里面是什么，不是一颗裸箭头），展开后每行带类别标签——
 *   三类句子长得像，没有标签就分不清哪句是"先别发"的等待依据（这里要拦的正是这个混淆）。
 * - **复制只带走正文**：整卡点击 = `onCopy(option.text)`；角度与三段策略行一个字不进剪贴板。
 *   点击动作带资源名与 Button 角色，读屏念得出"点了会干什么"。
 * - 三段全空的候选（旧输出）不画展开行，形状与没有策略载荷的旧版一致。
 */
@Composable
private fun ProactiveOptionCard(
    option: ProactiveOption,
    labels: ProactiveStrategyLabels,
    expanded: Boolean,
    expandLabel: String,
    collapseLabel: String,
    onToggleExpanded: () -> Unit,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(SurfaceCard, LoveBrainShape.md)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.md)
            .padding(Spacing.lg)
            .clickable(onClickLabel = labels.copyOpener, role = Role.Button) { onCopy(option.text) }
    ) {
        // 可发送正文在前：它是这张卡唯一的主角，其余都是辅助说明
        Text(option.text, style = AppTypography.bodyMedium, color = TextPrimary)
        if (option.angle.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                labels.line.format(labels.angle, option.angle),
                style = AppTypography.labelSmall,
                color = TextHint
            )
        }
        val strategy = listOfNotNull(
            if (option.timing.isBlank()) null else labels.timing to option.timing,
            if (option.holdBack.isBlank()) null else labels.holdBack to option.holdBack,
            if (option.prepare.isBlank()) null else labels.prepare to option.prepare
        )
        if (strategy.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs)
                    .clickable(onClickLabel = if (expanded) collapseLabel else expandLabel) {
                        onToggleExpanded()
                    }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = TextHint,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f }
                )
                Spacer(Modifier.width(Spacing.xs))
                // 收起态也要说得出展开区里装的是哪几类（"按需展开"的入口得有自己的名字，不能是一颗裸箭头）
                Text(
                    strategy.joinToString(" · ") { it.first },
                    style = AppTypography.labelSmall,
                    color = TextHint
                )
            }
            if (expanded) {
                strategy.forEach { (label, body) ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        labels.line.format(label, body),
                        style = AppTypography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// ReplyPrimaryActions 住在 reply/ReplyPrimaryActions.kt，是本屏唯一那颗主动作按钮的实现。
// 这里不再维护 private 副本：测试直接挂生产这一颗，副本不会跟着生产代码改，测过了也不算数。
