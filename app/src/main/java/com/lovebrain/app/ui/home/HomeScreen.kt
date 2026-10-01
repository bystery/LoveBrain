package com.lovebrain.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.KnowledgeBaseActivity
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.LbRowState
import com.lovebrain.app.core.designsystem.LbActionCard
import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.core.designsystem.LbMetricGrid
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.LbSection
import com.lovebrain.app.core.designsystem.LbSettingRow
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.viewmodel.SetupViewModel
import com.lovebrain.app.viewmodel.costReadout

/** 首页与「使用概览」详情页那两格用的货币符号（面板那一格走半角 `¥`，两边各有守卫钉着，本轮不并） */
const val HOME_COST_CURRENCY = "￥"

/**
 * 首页——悬浮军师状态卡、快捷功能、服务设置与使用概览。
 *
 * §6.2 把那四段固定成：**顶部**（LoveBrain + 一句价值说明 + 右侧 About）/ **军师状态主卡** /
 * **快捷功能**（知识库与反馈案例同一颗 `LbActionCard`）/ **设置与使用概览**
 * （两行走 `LbSettingRow`、统计走三等分 `LbMetricGrid`）。
 * 这一页只负责**摆哪几段、每段交哪一颗**；外框（整屏底色、水平边距）不在这儿写，
 * 交回 [LbScreenScaffold]——见下面那一段的注释。守卫是语义树那两家：
 * `HomeScreenStructureTest`（四段的顺序与数量）、`HomeScaffoldFrameSemanticsTest`（外框的尺寸与出口）。
 *
 * 不直接操作 Repository；所有数据通过 [SetupViewModel] 获取。
 *
 * §6.3 那四格在这一屏**没有对应的流**，判过所以不接 `LbAsyncState`（判据取自代码）：
 * 页面读的四条 `StateFlow`（`activeTicket` / `providerReady` / `captureEnabled` /
 * `captureAllowedPackages`）全都在 `SetupViewModel` 构造时用 `MutableStateFlow(securePrefs.…)`
 * 同步灌好，没有"第一次数据还没到"那一格；`null` / `false` / 空集在这屏各自**就是一个产品状态**
 * （没配供应商 / 没给无障碍权限 / 一个 App 都没授权），由那一行自己的文案说清楚，不是待补的占位。
 * 剩下两路是同步系统查询：`Settings.canDrawOverlays`，以及 `isCaptureServiceEnabled`
 * （读不到就 fail-closed 报"没权限"，失败已经落成一行文案，不是一条会失败的流）；
 * 统计那三格又是上面那族同步属性。首页是枢纽页——它的"内容"就是这些入口本身，永远不空，
 * 也没有一条能把整屏打失败的读。硬铺四格就是造三档产品走不到的状态。
 * 这一屏真正需要"只判一次"的那处判据是军师状态卡，它已经收在 `advisorStatus` 一处
 * （四档穷举见 `AdvisorStatusTest`），本页不再出现第二份判据。
 */
@Composable
fun HomeScreen(
    viewModel: SetupViewModel,
    onStartService: () -> Unit,
    onOpenPanel: (Int, Boolean) -> Unit,
    onTempHide: () -> Unit,
    onRestore: () -> Unit,
    onNavigateFeedback: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateProviders: () -> Unit,
    onNavigateUsage: () -> Unit,
    onNavigateCaptureApps: () -> Unit,
    onBack: () -> Unit,
    /**
     * 两个**默认值就是原行为**的入口：首页原先直接读系统权限与 `FloatingService` 这个进程内
     * 单例（`instance != null`），那是四段结构的判据里两个看不见的前提——要证明"隐藏图标只在
     * 可隐藏时出现在右上角"，就得能把四个组合都摆出来。声明成参数之后，生产调用方一字不改，
     * 用例可以逐格喂。
     */
    overlayGrantedOverride: Boolean? = null,
    serviceRunningOverride: Boolean? = null
) {
    val context = LocalContext.current
    val overlayGranted = overlayGrantedOverride ?: Settings.canDrawOverlays(context)
    val activeTicket by viewModel.activeTicket.collectAsStateWithLifecycle()
    val providerReady by viewModel.providerReady.collectAsStateWithLifecycle()

    val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle()
    val captureAllowed by viewModel.captureAllowedPackages.collectAsStateWithLifecycle()
    val captureAllowedCount = captureAllowed.size
    var accessibilityGranted by remember { mutableStateOf(viewModel.isCaptureServiceEnabled(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityGranted = viewModel.isCaptureServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollState = rememberScrollState()
    val isServiceRunning = serviceRunningOverride ?: (FloatingService.instance != null)
    val currentWindowState by FloatingService.windowStateFlow.collectAsStateWithLifecycle()

    // 一份快照，不是五份平行判据。旧写法是 statusText / statusColor / description /
    // buttonText / buttonAction 五个 `when` 各把同样三个条件重判一遍——五份判据可以各说各话，
    // 而加状态时最容易忘的恰恰是"颜色那一份"。
    // 唯一的行为差别见 advisorStatus 的注释（服务活着但窗口从未出现 → 不再说"运行中"）。
    val advisor = advisorStatus(
        overlayGranted = overlayGranted,
        serviceRunning = isServiceRunning,
        window = currentWindowState
    )
    val buttonAction = when (advisor.intent) {
        AdvisorIntent.Start -> onStartService
        AdvisorIntent.OpenPanel -> { { onOpenPanel(0, false) } }
        AdvisorIntent.Restore -> onRestore
    }
    val canHide = isServiceRunning &&
        (currentWindowState == FloatingService.WindowState.VISIBLE_BUBBLE ||
         currentWindowState == FloatingService.WindowState.VISIBLE_PANEL)

    // ═════════════════════════════════════════════════════════════
    // §6.2 首页四段的外框：交回 `LbScreenScaffold`
    // ═════════════════════════════════════════════════════════════
    //
    // 改之前这一页自己拼外框：`Column(fillMaxSize).verticalScroll().padding(horizontal = Spacing.xxxl)`。
    // 那 24dp 与脚手架那一档**恰好同数**，却不是同一个所有者——审计点名的就是这个形状
    // （"统一水平边距"要的是只有一个地方能改它，不是四页碰巧都写 24）。
    // 现在这一页不再出现 `padding(horizontal = …)`：边距、整屏底色都只从 `LbScreenScaffold` 来。
    //
    // 两个旋钮按**这一页今天的行为**取值，不是照抄默认值：
    // - `handlesSystemBarInsets` 留默认 `false`——insets 由根导航 `SetupRoot` 那一层加过
    //   （`systemBarsPadding` 挂在它的 Box 上），这里再传 `true` 就是加两遍；
    // - `maxContentWidth` 同理归 `SetupRoot`（600dp 限宽）。这一格只换外框的主人，
    //   既不动限宽也不动 insets——那两件事的边界仍写在 `LbScreenScaffold` 的 KDoc 里。
    //
    // ⚠ 页头**留在滚动柱子里**，没交去 `topBar =` 槽：首页这一屏今天是"整页一起滚"，
    // 交出去就变成固定页头 + 内容单独滚——那是改版式，不是归所有者。
    // 边距不吃这一决定：`LbScreenScaffold` 没有页头那一档把同一档 padding 排在框上，
    // 所以视口宽度与改之前逐位相同（360dp 槽下 312dp）。
    LbScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            LbTopBar(
                title = "LoveBrain",
                subtitle = "帮你更自然地表达",
                trailing = { HomeAboutEntry(onNavigateAbout) }
            )

            AssistantStatusCard(
                status = advisor,
                onButtonClick = buttonAction,
                onHideClick = if (canHide) onTempHide else null
            )

            LbSection("快捷功能")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // 这两条走的是**同一颗** `LbActionCard`（§6.2 第 3 段那句"相同组件"）。
                // 卡片自己那一层（图标方块、箭头、按压、圆角与底色、整卡一处操作）一个字都不在这里；
                // 这一页只交图标、两个说法和出口。
                LbActionCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_feature_book,
                    title = "知识库",
                    subtitle = "她的专属记忆",
                    onClick = {
                        context.startActivity(Intent(context, KnowledgeBaseActivity::class.java))
                    }
                )
                LbActionCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_feature_feedback,
                    // 这一条进资源、旁边那两条（「知识库」「她的专属记忆」等）暂时留着，不是手抖改一半：
                    // `feedback_cases_title` 是反馈案例页页头**已经有**的那一份（账本 §58），
                    // 入口卡片与目标页页头念同一个名字，抄第二份就迟早会漂；
                    // 那两条还没有第二份，等它们自己长出资源时再一起收。
                    title = stringResource(R.string.feedback_cases_title),
                    subtitle = "点踩记录与导出",
                    onClick = onNavigateFeedback
                )
            }

            LbSection("服务设置")
            // 这一层 `Card` 不是"页面又拼了一颗卡"：设计系统里没有"一组行的卡底"那颗主人
            // （`LbSection` 的 KDoc 明写"区块的底色与圆角也不在这里，那层卡底留在调用页面上"，
            // `LbMetricGrid`/`LbActionCard` 各管各的容器），全仓同一档卡底（`lg` + `SurfaceCard`）
            // 都在页面里：`AboutScreen`、`UsageDetailScreen`、`ProviderSection`、`KbEditActivity`。
            // 给它开主人是另一格的事，那格得连着这四家一起搬，不能在本格把首页一家偷偷搬过去。
            Card(
                shape = LoveBrainShape.lg,
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    LbSettingRow(
                        title = "模型供应商",
                        subtitle = activeTicket?.let { "${it.name} · ${it.model.ifBlank { "未选模型" }}" }
                            ?: "未配置供应商",
                        // 供应商行只要一颗点：以前用 statusText = "" 表达"没有词"，
                        // 现在 dot 这一旋钮自己就能说这件事
                        dot = if (providerReady) LbRowState.Ready else LbRowState.NotReady,
                        trailingText = "管理",
                        onTrailingClick = onNavigateProviders,
                        onClick = onNavigateProviders
                    )
                    HorizontalDivider(thickness = AppDimens.BORDER_WIDTH_DP.dp, color = Border.copy(alpha = 0.5f))

                    LbSettingRow(
                        title = stringResource(R.string.capture_apps_title),
                        subtitle = when {
                            !accessibilityGranted -> stringResource(R.string.home_capture_no_permission)
                            captureAllowedCount == 0 -> stringResource(R.string.capture_apps_row_subtitle_none)
                            captureEnabled -> stringResource(R.string.home_capture_status_on)
                            else -> stringResource(R.string.home_capture_status_off)
                        },
                        dot = if (captureEnabled) LbRowState.Ready else LbRowState.NotReady,
                        // 这两个字以前**根本不会被画出来**（组件里没有画 statusText 的那一行）
                        statusText = if (accessibilityGranted) {
                            stringResource(
                                if (captureEnabled) R.string.home_on else R.string.home_off
                            )
                        } else null,
                        trailingText = if (!accessibilityGranted) {
                            stringResource(R.string.home_grant_accessibility)
                        } else {
                            stringResource(R.string.capture_apps_row_subtitle, captureAllowedCount)
                        },
                        onTrailingClick = if (!accessibilityGranted) ({ showAccessibilityDisclosure = true })
                        else ({ onNavigateCaptureApps() }),
                        onClick = if (accessibilityGranted) ({ viewModel.toggleCapture() }) else null
                    )
                }
            }

            LbSection("使用概览")
            // 这一格与「使用概览」详情页、面板顶部那条**共用同一颗判据** `costReadout`
            // （`viewmodel/UsageStats.kt`）。一笔可计价记录都没有时念「—」——
            // 以前 `< 0.01` 一律念「￥0」，对拿不到 usage / 没有价格表的自定义 Provider
            // 等于把"不知道"说成"免费"。金额仍走这一页原来的两位小数口径（账本 §61.4）。
            val costStr = costReadout(
                yuan = viewModel.totalCostYuan,
                unknownText = stringResource(R.string.cost_unknown),
                belowCentText = stringResource(R.string.cost_below_cent, HOME_COST_CURRENCY),
                amountText = { HOME_COST_CURRENCY + String.format("%.2f", it) }
            )
            val rateStr = if (viewModel.totalGenerateCount > 0) "${(viewModel.adoptRate * 100).toInt()}%" else "—"
            LbMetricGrid(
                metrics = listOf(
                    LbMetric(label = "累计生成", value = "${viewModel.totalGenerateCount}"),
                    LbMetric(label = stringResource(R.string.cost_stated_label), value = costStr),
                    LbMetric(label = "采用率", value = rateStr, highlight = true)
                ),
                modifier = Modifier.heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp),
                onClick = onNavigateUsage
            )

            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showAccessibilityDisclosure = false
                viewModel.confirmAccessibilityDisclosure()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }
}
