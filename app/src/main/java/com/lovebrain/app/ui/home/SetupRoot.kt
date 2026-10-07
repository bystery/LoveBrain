package com.lovebrain.app.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.SurfaceBase
import com.lovebrain.app.ui.feedback.FeedbackCasesScreen
import com.lovebrain.app.viewmodel.HomeStatusViewModel
import com.lovebrain.app.viewmodel.SetupViewModel
import org.koin.androidx.compose.koinViewModel

/** 根导航内容最大宽度 */
private const val CONTENT_MAX_WIDTH_DP = 600

/**
 * 首页目的地——根级导航。
 *
 * 合同点名的四入口各自落到哪一格都写在这里：知识库是**另一个 Activity**（不在这棵树里），
 * 已踩案例 / 消息捕获 / 模型供应商是这三格。`About` 与 `Usage` 两格已随首页那几段一起删：
 * 全仓唯一的入口就是首页那两行，入口没了这两页就没有任何动态入口（Manifest 只有三个
 * Activity、DI 里没有页面注册、导航表也只有这一颗 `when`、资源里那两条 `usage_*` /
 * `about_*` 文案不再被任何生产代码引用），所以是"完全不可达"，按清理流程物理删除。
 */
sealed class HomeDestination {
    data object Home : HomeDestination()
    data object FeedbackCases : HomeDestination()
    data object Providers : HomeDestination()
    data object CaptureApps : HomeDestination()

    companion object {
        /** Saver for rememberSaveable */
        val Saver = androidx.compose.runtime.saveable.Saver<HomeDestination, String>(
            save = { it::class.simpleName ?: "Home" },
            restore = { name ->
                when (name) {
                    "FeedbackCases" -> FeedbackCases
                    "Providers" -> Providers
                    "CaptureApps" -> CaptureApps
                    else -> Home
                }
            }
        )
    }
}

/** 根导航的前进/后退判据：Home 排最前，三格子页按上面声明的顺序排（§9.2 统一过渡读这一颗） */
internal val HomeDestination.rank: Int
    get() = when (this) {
        HomeDestination.Home -> 0
        HomeDestination.FeedbackCases -> 1
        HomeDestination.Providers -> 2
        HomeDestination.CaptureApps -> 3
    }

/**
 * 设置页根导航——按 destination 切 Home / FeedbackCases / Providers / CaptureApps。
 *
 * 用 [HomeDestination.Saver] + [rememberSaveable]，旋转/进程重建后恢复 destination。
 * 旧版这里 `when` 里同一个 `CaptureApps` 分支写了两遍（重复的那一份永远不会被执行），
 * 现在只剩一份；`About` / `Usage` 两格随首页那几段一起撤。
 *
 * ## 只读出口：destination 现在住在哪一格（§9.1）
 *
 * `destination` 是这一层自己的导航账，**不上提**（上提会让首页/子页两套代码各拿一半导航状态）。
 * [onDestinationChanged] 是这一层唯一的只读出口：**只说"现在站在哪一格"，不回写任何导航**。
 * 宿主 `SetupActivity` 用它做两件 §9.1 点名的动作：
 * 1. 罩子整块只在 Home 那一格在场（`HomeCoachMarks(onHome = …)`），进子页即移除；
 * 2. 回到 Home 时重算一次游标——同一 Activity 内的子页来回**不触发 `onResume`**，
 *    少了这一个刷新点，回首页的罩子会停在出发前那一格（表行判的"子页返回未接上游标刷新"）。
 *
 * ⚠ `onTempHide` / `onRestore` 这两颗回调本轮**不再被使用**：
 * 首页重做后没有"临时隐藏"那处出口了，而签名由宿主 `SetupActivity` 持有、
 * 那一页不归。签名留着不动，收口写进"需"。
 */
@Composable
fun SetupRoot(
    viewModel: SetupViewModel,
    onStartService: () -> Unit,
    onOpenPanel: (Int) -> Unit,
    onTempHide: () -> Unit,
    onRestore: () -> Unit,
    /**
     * 引导罩子里那颗主动作要的**直达目标**（G1b 接线单 §3）。
     *
     * 为什么是"宿主给一颗目标、这一层消费掉"而不是把 `destination` 整体上提：
     * `destination` 是这一层自己的导航账（`rememberSaveable`），上提会让首页/子页两套代码
     * 各拿一半导航状态（那是第二本账）。这颗参数只做一件事：**有人点名要跳哪一格**，
     * 这一层跳完就交回"已消费"，宿主那颗状态于是不会长期挂着、也不会重复导航。
     * null = 没有要跳的（生产默认；罩子没点主动作时一直是 null）。
     */
    guideTarget: HomeDestination? = null,
    onGuideTargetConsumed: () -> Unit = {},
    /**
     * 只读出口（§9.1）：每次导航真的发生那一刻同步一次，**包括回到 Home**。
     *
     * 为什么挂在写点上而不是 `LaunchedEffect(destination)`：后者要等下一帧才通知，
     * 那 250ms 滑入动画里会有一帧让旧提示板浮在正在进场子的页面上——正是这一条要收的口。
     */
    onDestinationChanged: (HomeDestination) -> Unit = {}
) {
    val destinationState = rememberSaveable(stateSaver = HomeDestination.Saver) {
        mutableStateOf(HomeDestination.Home)
    }
    val destination = destinationState.value
    // 导航唯一的写点：改这一层的账 + 同步告诉宿主。页内不许再直接写 `destinationState.value`
    // （绕开这里 = 只读出口漏一次通知 = 罩子留在子页上）。
    val goTo: (HomeDestination) -> Unit = { dest ->
        destinationState.value = dest
        onDestinationChanged(dest)
    }
    val context = LocalContext.current
    // 重建/旋转：destination 由 Saver 恢复成子页，而宿主那一颗每次都是新的、默认 Home。
    // 初次组合补一次同步，否则"restore 到子页"这一格里罩子会错判成"还在首页"。
    LaunchedEffect(Unit) { onDestinationChanged(destination) }
    // 直达导航：目标来了就跳，跳完立刻请宿主收回（不收回的话，下一次重组会再跳一次）。
    LaunchedEffect(guideTarget) {
        if (guideTarget != null) {
            goTo(guideTarget)
            onGuideTargetConsumed()
        }
    }
    // 首页那盏灯的状态机：**容器注册**（di/AppModule.kt 那颗 viewModel {}），挂 Activity 那一棵
    // ViewModelStore 上——所以子页来回、旋转都拿到同一颗，不会因为换 owner 而重建。
    // 重建本身也不会发请求（探针只在按 ▶ 时走），但共用一颗才让"上一次检查属于哪一组身份"
    // 这件事在整趟导航里连续有效。
    val storeOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "SetupRoot 必须住在有 ViewModelStore 的宿主里（生产就是 SetupActivity 那一棵）"
    }
    val homeStatus: HomeStatusViewModel = koinViewModel<HomeStatusViewModel>(
        viewModelStoreOwner = storeOwner
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = CONTENT_MAX_WIDTH_DP.dp)) {
            // §9.2：根导航**一整族**统一前进/返回过渡——子页从右侧滑入、返回时向右滑出，
            // 与知识库 Activity 的默认方向一致；不每页各补一份负 padding 或一次性淡入。
            // 子页↔子页（原先是纯淡入淡出那一支）现在同一把尺：按 [HomeDestination.rank] 判前进/后退。
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    if (targetState == HomeDestination.Home) {
                        // 返回首页：首页淡入，当前页向右滑出 + 淡出
                        fadeIn(tween(200)) togetherWith
                            (slideOutHorizontally(tween(250)) { it } + fadeOut(tween(200)))
                    } else if (initialState == HomeDestination.Home ||
                        targetState.rank > initialState.rank
                    ) {
                        // 前进子页：新页从右侧滑入 + 淡入，旧页向左滑出 + 淡出
                        (slideInHorizontally(tween(250)) { it } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(250)) { -it } + fadeOut(tween(200)))
                    } else {
                        // 子页退回更靠前的子页：方向反过来，仍是同一族滑入滑出，不留一次性淡入
                        (slideInHorizontally(tween(250)) { -it } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(250)) { it } + fadeOut(tween(200)))
                    }
                },
                label = "rootNav"
            ) { dest ->
                when (dest) {
                HomeDestination.Home -> HomeScreen(
                    homeStatus = homeStatus,
                    onStartService = onStartService,
                    onNavigateFeedback = { goTo(HomeDestination.FeedbackCases) },
                    onNavigateProviders = { goTo(HomeDestination.Providers) },
                    onNavigateCaptureApps = { goTo(HomeDestination.CaptureApps) },
                    // 按过"稍后"的人唯一的回程（G1b 接线单 §5）：先清掉盘上的 DEFERRED_TO_HINT，
                    // 再走导航——顺序反过来的话，回到首页时游标还是"稍后"，罩子从此不再回来。
                    onResumeGuide = { viewModel.resumeGuide(context) },
                    onBack = { (context as? Activity)?.finish() }
                )
                HomeDestination.FeedbackCases -> {
                    BackHandler { goTo(HomeDestination.Home) }
                    FeedbackCasesScreen(
                        viewModel = viewModel,
                        onBack = { goTo(HomeDestination.Home) }
                    )
                }
                HomeDestination.Providers -> {
                    BackHandler { goTo(HomeDestination.Home) }
                    ProviderSection(viewModel = viewModel, onBack = { goTo(HomeDestination.Home) })
                }
                HomeDestination.CaptureApps -> {
                    BackHandler { goTo(HomeDestination.Home) }
                    CaptureAppsScreen(viewModel = viewModel, onBack = { goTo(HomeDestination.Home) })
                }
            }
            }
        }
    }
}
