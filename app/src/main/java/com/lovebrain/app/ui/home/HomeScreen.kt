package com.lovebrain.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbActionCard
import com.lovebrain.app.core.designsystem.LbActionCardIconBlock
import com.lovebrain.app.core.designsystem.LbMetricGrid
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.LB_SCREEN_HORIZONTAL_MARGIN
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.ui.KnowledgeBaseActivity
import com.lovebrain.app.viewmodel.HomeStatusViewModel

/**
 * 首页——精简但有完成度的一屏：一行应用标识、一条浮起来的 hero 状态卡、仅黄灯时那一行**带出口**的缺项、2×2 四入口。
 *
 * 依据指导书 §5.1（页面从上到下**只有**这四格：应用标识与必要标题 → 状态主卡和开始/停止 →
 * 有缺项时的紧凑提示 → 四入口）+ §3 表"应用首页"那一行（保留结构 = 状态主卡、真实缺项提示、
 * 知识库/已踩案例/消息捕获/模型供应商四入口；不塞 = 调试参数、大段介绍、重复统计、旧锦囊入口）。
 * 从上到下：
 * 0. [LbTopBar] 的 `Identity` 档——**只有应用名**（`R.string.app_name`，既有资源）：
 *    §5.1 那一格写的是"应用标识与必要标题"，而旧版那句价值说明（"帮你更自然地表达"）已被合同删掉，
 *    标题这一档因此只剩标识本身。页头**不自己画**：字号档、行高、热区全在设计系统那一颗里，
 *    `Identity` 这一档在 [com.lovebrain.app.core.designsystem.LbTopBarLevel] 的注释里点名的就是首页；
 *    不带返回钮（这一屏的返回就是退出 Activity，走 [BackHandler]）、不带分割线（那是 `ScreenPage` 那一族的规格）；
 * 1. [AssistantStatusCard]：左边红/黄/绿小灯 + **写在屏幕上的状态名**，右边朝右三角（开始）或方块（停止）；
 *    `Xl` 24 圆角 + 四边等距 16 + `PrimaryLight` 浅蓝底 + `PrimarySubtle` 细描边 + **零阴影**——
 *    M09 §5 第 1 条要把 1.3.1 那一级浅蓝层次买回来，用的是旧版就在盘上的那三颗令牌，不新增颜色；
 *    第 1 条同时明令"不要同时堆满阴影、描边和渐变"，所以这一格**描边与阴影二选一**（选了描边）；
 * 2. 只有黄灯才存在的 [HomeSetupHint]：一行 `bodySmall` 12 的黄字（浅黄底容器，不是裸奔的一行 log），
 *    末尾配一颗设计系统现有的行内胶囊动作——回应"不能只给一句黄警告让用户自己摸索"（§2.2 第 5 条）；
 * 3. 四入口 = 设计系统 `LbActionCard` 的 `GridCell` 紧凑档：`Lg` 16 圆角 + 内 12 + **无阴影**，
 *    名字 + 一句 5–7 字的**辅助描述**（M09 §5 第 4 条：旧版每格都带 `subtitle`，上一批收掉空槽之后
 *    四格只剩名字；文案真源在 `AdvisorStatus.kt` 的 `internal const` 那一族，不在调用里内联中文）。
 *
 * 层级不是靠新文案、也不是靠第五种状态买来的：hero 与入口卡是**两档**（圆角 24 对 16、
 * 内距 16 对 12、`PrimaryLight` 浅蓝对 `SurfaceCard` 白、状态名 18 对标题 15——
 * "主卡是视觉焦点、入口不许与主卡同样抢眼"（§5.1）这一条之前只剩阴影在扛，
 * 现在由浅蓝底 + 字阶两处一起扛），段间距 16、卡与缺项 8、入口两排之间 12、列间 12——
 * 旧版那"三处一律 12dp"读不出层级，四格 + 一卡于是就是五个等大方块。
 *
 * 页面不判任何条件：灯色、形状、黄字全部来自 [AdvisorStatus.render]，那一行末尾**有没有**去处、
 * 是**哪一条**去处来自 [AdvisorStatus.primaryAction]，而那一行的状态来自
 * [HomeStatusViewModel]——它才是唯一会看权限、配置、知识库与探针结果的地方（§4.1 第 3 条）。
 *
 * ## 那一行末尾的那一颗去处（§5.2 末段 + §2.2 第 5 条）
 *
 * **至多一颗**，跟着清单里最有用的那一项走，一条一个目的地，不堆同级大按钮、也不新增一排：
 * 悬浮权限缺 → 「去授权」（宿主那条平台链，它才认得系统授权页与回来续跑那半截）；
 * 模型配置缺 → 「去设置」（四入口里那颗「模型供应商」）；
 * 当前对象没有知识库 → 去管理（与首页那颗入口卡同一个目的地，同一个开法）；
 * 资料没读到 → 「重试」= [HomeStatusViewModel.returnedFromSubpage]，**只重读本地事实，一个请求都不发**；
 * 本次连接请求失败 → 「重试」= [HomeStatusViewModel.playClicked]，在**那颗请求的位置**再发那一次。
 * "服务没起来""连接还没检查过"那两条**没有**去处：此刻首页给不出目的地，硬造一颗就是假按钮。
 *
 * ## 被删掉的那些去哪了
 *
 * 「帮你更自然地表达」那一句、大军师介绍卡、当前供应商详情行、服务设置段（捕获开关与
 * 无障碍授权）、使用概览那一行、反馈/关于那两行、页尾那颗 About——合同点名要删。
 * 它们的**能力**没有一起删掉：捕获的授权与开关在"消息捕获"子页（`CaptureAppsScreen`），
 * 供应商的增删改与测试连接在"模型供应商"子页（`ProviderSection`），
 * 累计统计的实现仍在 `data/ApiUsageTracker` 与面板顶部那条读数里，只是首页不再念它。
 *
 * ⚠ 授权告知没有因为这一屏删了那段而断掉：`AccessibilityDisclosureDialog` 现在住在
 * "消息捕获"子页（`CaptureAppsScreen`）——没授予无障碍权限时点那颗开关只弹披露，
 * 用户明确"同意并继续"才写同意记录并跳系统设置，取消就什么都不做。
 * 不许退化成"静默开无障碍权限"（合同 第7节第1条 末段）。
 *
 * ## 这一屏没有"第一次数据还没到"那一格
 *
 * 页面上唯一的读数是那盏灯，而灯只有四档；缺哪一步由黄字那一行念出来。
 * 知识库那一格有三种实话：读到带真名的活动库＝`Present`（**0 轮的空库也算已建立**，
 * 内容完整度不参与存在性，判据的主人只有 `HomeStatusViewModel` 那一处）、没有活动库＝"请为当前
 * 对象建立知识库"、本机读不动＝"还没读到当前对象的知识库"，后两格的话由 `AdvisorMissing` 各自念。
 *
 * 首页也**不在组合期发请求**：`LaunchedEffect(Unit)` 与 ON_RESUME 各触发一次
 * [HomeStatusViewModel.returnedFromSubpage]，而那一颗**只做本地重读**——探针唯一的入口是
 * 上面那条 `onPlay`（用户按 ▶）与"本次请求失败"那一格用户点名按下的那颗「重试」。
 * 身份四元组里任何一位变了（供应商 / 生效模型 / 地址 / Key；**切知识库不改身份**，
 * 算式的主人只有 `HomeStatusViewModel.identityOf`）时，它作废那一组身份的旧结论
 * （灯落回黄 + 念"连接还没检查过"这一条实话：从没为这组身份按过 ▶，不等于检查失败），
 * 但不替用户补发请求。去处那五颗里只有失败那一格的「重试」会花钱，其余四颗一次请求都不该多发。
 */
@Composable
fun HomeScreen(
    homeStatus: HomeStatusViewModel,
    onStartService: () -> Unit,
    onNavigateFeedback: () -> Unit,
    onNavigateProviders: () -> Unit,
    onNavigateCaptureApps: () -> Unit,
    /**
     * 黄字行那颗「去设置」的**第二件事**（G1b 接线单 §5）：按过引导"稍后"的人，
     * 盘上游标是 `DEFERRED_TO_HINT`，罩子不再回来。宿主把 `SetupViewModel.resumeGuide` 接在这里，
     * 于是"缺项提示点到去设置"与"引导不会一翻页就没"是同一条闭环。
     * 默认值 = 不动游标（这一屏的判据里"点了黄字行"本来就只判导航那一半）。
     */
    onResumeGuide: () -> Unit = {},
    onBack: () -> Unit,
    /**
     * 悬浮权限这一颗**默认值就是原行为**：生产直读 `Settings.canDrawOverlays`。
     * 留成参数不是为了改行为，而是这一屏的判据里"有没有权限"决定黄字念哪一句，
     * JVM 用例必须能把这一件事实摆出来（读系统设置在 Robolectric 里给不出真值）。
     */
    overlayGrantedOverride: Boolean? = null
) {
    val context = LocalContext.current
    val overlayGranted = overlayGrantedOverride ?: Settings.canDrawOverlays(context)
    val status by homeStatus.status.collectAsStateWithLifecycle()
    val render = status.render()
    // 知识库管理页这一格**只有一个开法**：入口卡与缺项那颗去处共用下面这一条链。
    // 抄第二遍 `Intent(context, KnowledgeBaseActivity)` 就是第二本账——那一页哪天换宿主会漏改一处。
    val openKnowledgeManagement: () -> Unit = {
        context.startActivity(Intent(context, KnowledgeBaseActivity::class.java))
    }
    // 那一行末尾**至多一颗**去处，它跟着清单里最有用的那一项走（判据在 `AdvisorStatus.primaryAction`，
    // 页面只回答"这一条怎么跳"）。三句动作词全部走 `res/values/strings.xml` 里**已有**的键：
    // 这一席没有 res 的写入权，而在页面里内联一条中文正是文案预算那把尺会当场顶红的事
    // （要新词就回报，见本席交付）。
    val primaryAction = status.primaryAction
    val setupActionLabel = primaryAction?.let {
        stringResource(
            when (it) {
                // 「去设置」：既有键，指回四入口里那颗「模型供应商」
                HomeMissingAction.OpenProviders -> R.string.provider_open_settings
                // 「去授权」：键名带着它出生时那一格（无障碍），值本身是通用的授权动作词。
                // 借的是这一句短词，不新抄一份——与上面那颗「去设置」同一条纪律。
                HomeMissingAction.GrantOverlay -> R.string.home_grant_accessibility
                // 「新建知识库」：当前对象没有库时唯一要说的事（目的地是知识库管理页，
                // 那一页的空态主动作就叫这个名字）。§5.2 想要的是「去管理」那一档更中性，
                // 而 `res` 里没有那颗键：已回报要一颗，键落地前先用这一颗真在盘上的词，不内联。
                HomeMissingAction.OpenKnowledgeBase -> R.string.home_go_manage
                // 「重试」：读到没读到那一格 = 重读本地事实；本次请求失败那一格 = 再发那一次
                HomeMissingAction.RetryKnowledgeRead,
                HomeMissingAction.RetryConnection -> R.string.action_retry
            }
        )
    }
    // 五条跳转各自先落成一颗具名 lambda，再交给下面那张表：`when` 分支里直接写 `{ … }` 会被读成
    // 分支体（值成了 Unit），那一维的编译器帮忙是站不住的，具名反而好核。
    // 顺序是承重的：先清"稍后"（否则回到首页游标还是 DEFERRED，罩子永不回来），再导航到供应商那一格。
    val goProviders: () -> Unit = {
        onResumeGuide()
        onNavigateProviders()
    }
    // 本地重读：VM 那一侧唯一"只重读事实、一个请求都不发"的重读口
    val retryKnowledgeRead: () -> Unit = { homeStatus.returnedFromSubpage(overlayGranted) }
    // 本次失败的那一次：探针唯一的入口就是这颗，用户点名才走（不自动补发）
    val retryConnection: () -> Unit = { homeStatus.playClicked(overlayGranted) }
    val setupAction: (() -> Unit)? = when (primaryAction) {
        null -> null
        // 授权这一颗**只走宿主那条平台链**：`SetupActivity.startFloatingService` 才认得
        // "没权限就去系统授权页、回来后接着启动"那半截，页面自己再发一遍 Intent 就是第二套行为
        // （§4.1 第 2 条：复用同时包含外观、行为和宿主）。它不碰 `onPlay`，一次请求都不发。
        HomeMissingAction.GrantOverlay -> onStartService
        HomeMissingAction.OpenProviders -> goProviders
        HomeMissingAction.OpenKnowledgeBase -> openKnowledgeManagement
        HomeMissingAction.RetryKnowledgeRead -> retryKnowledgeRead
        HomeMissingAction.RetryConnection -> retryConnection
    }

    BackHandler { onBack() }

    // 从系统授权页回来：Activity resume，而这一屏没有重新装配 → 重读一次本地事实
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) homeStatus.returnedFromSubpage(overlayGranted)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 从站在这棵树里的子页（供应商 / 捕获范围 / 已踩案例）返回首页：这一屏重新进入组合
    LaunchedEffect(Unit) { homeStatus.returnedFromSubpage(overlayGranted) }

    LbScreenScaffold {
        // 段与段之间 16（`Spacing.xl`）——M09 §5 第 3 条"收紧主页大区块间距"落在这一格：
        // 上一版是 `Spacing.xxxl`(24)，与 1.3.1 那条 `Arrangement.spacedBy(Spacing.xl)`＝**16** 对不上，
        // 主页因此比旧版松一圈。这里换的只是**这一处用哪一档既有 token**，
        // `Spacing.xxxl` 那颗数值、`Spacing` 整张表、水平边距那颗 24（`LB_SCREEN_HORIZONTAL_MARGIN`）
        // 一个字都没动，也不靠缩字买密度。
        // 层次差仍在：段间 16 > 入口两排之间 12 > 卡与缺项 8，标识那一行仍然自成一段。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // 顶距与底距同用 `LB_SCREEN_HORIZONTAL_MARGIN` 那一个主人（24dp），不新写数：
                // 顶部那一格原本由页面自己补（`LbScreenScaffold` 的 `topBar == null` 那一支只给水平
                // 边距），底部那一格本轮补上——这一列是能滚的（320dp + 2 倍字那一格真的会滚），
                // 没有底距的话最后一排入口会直接坐在导航栏下沿上。
                .padding(top = LB_SCREEN_HORIZONTAL_MARGIN, bottom = LB_SCREEN_HORIZONTAL_MARGIN)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            // ── 应用标识（§5.1 第一格）：只有名字，没有副标题、没有返回钮、没有分割线。
            //    页头这一格交回设计系统那一颗（`Identity` 档点名的就是首页），页面不自画第二套行高。──
            LbTopBar(
                title = stringResource(R.string.app_name),
                level = LbTopBarLevel.Identity,
                modifier = Modifier.testTag(LbHomeTags.IDENTITY)
            )

            // ── 状态那一块：卡 + 属于它的那一行缺项，8dp（缺项是这一档状态的说明，不是第四段）──
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AssistantStatusCard(
                    render = render,
                    onPlay = {
                        // 先走平台那条链（查悬浮权限 → 需要时去授权 → 启动服务），再让灯进"正在检查"
                        onStartService()
                        homeStatus.playClicked(overlayGranted)
                    },
                    onStop = { homeStatus.stopClicked() }
                )

                if (render.hint != null) {
                    HomeSetupHint(
                        text = render.hint,
                        actionLabel = setupActionLabel,
                        onAction = setupAction
                    )
                }
            }

            // ── 2×2 四入口：同一颗 LbActionCard 紧凑档，两排之间 12、列间 12 ──
            // 四格那五个数（方块 40 / 图标 22 / 尾部箭头 20 / 卡圆角 16 / 列间 12）与 1.3.1 逐字同值。
            // M09 §5 第 4 条这一轮补回的是**副标题那一行**：旧版每格都带 4–7 字的辅助描述
            // （`FeatureCard` 的 `subtitle` 槽），上一批把空槽收掉之后四格只剩名字，
            // 于是"已踩案例""消息捕获"这种三四个字的名字读不出点进去是什么。
            // 文案真源仍走 `AdvisorStatus.kt` 那一族 `internal const`（与四颗名字同一形状），
            // 不在调用里内联中文；四句各指那一页的真功能，旧版那三格已替换掉的功能一句话都不搬。
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    LbActionCard(
                        modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_KNOWLEDGE)
                            .coachAnchor(LbHomeTags.ENTRY_KNOWLEDGE),
                        iconRes = R.drawable.ic_feature_book,
                        title = HOME_ENTRY_KNOWLEDGE,
                        subtitle = HOME_ENTRY_KNOWLEDGE_SUB,
                        iconBlock = LbActionCardIconBlock.GridCell,
                        onClick = openKnowledgeManagement
                    )
                    LbActionCard(
                        modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_FEEDBACK)
                            .coachAnchor(LbHomeTags.ENTRY_FEEDBACK),
                        iconRes = R.drawable.ic_feature_feedback,
                        title = HOME_ENTRY_FEEDBACK,
                        subtitle = HOME_ENTRY_FEEDBACK_SUB,
                        iconBlock = LbActionCardIconBlock.GridCell,
                        onClick = onNavigateFeedback
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    LbActionCard(
                        modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_CAPTURE)
                            .coachAnchor(LbHomeTags.ENTRY_CAPTURE),
                        iconRes = R.drawable.ic_copy,
                        title = HOME_ENTRY_CAPTURE,
                        subtitle = HOME_ENTRY_CAPTURE_SUB,
                        iconBlock = LbActionCardIconBlock.GridCell,
                        onClick = onNavigateCaptureApps
                    )
                    LbActionCard(
                        modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_PROVIDER)
                            .coachAnchor(LbHomeTags.ENTRY_PROVIDER),
                        iconRes = R.drawable.ic_unplug,
                        title = HOME_ENTRY_PROVIDER,
                        subtitle = HOME_ENTRY_PROVIDER_SUB,
                        iconBlock = LbActionCardIconBlock.GridCell,
                        onClick = onNavigateProviders
                    )
                }
            }

            // ── 累计使用那一块只读小卡（M25 §五 取舍③，新决定替代旧规「不恢复使用概览」）──
            // 放在**四入口之后**，是这一屏既有节奏里的最后一格：一个读数，不是第五个目的地，
            // 也不并进主卡（主卡仍只有一颗可点，由 HomeHeroActionTest 钉）。它自己**不可点**：
            // 此刻首页没有"使用概览"这一页可去，硬造一颗就是假按钮（与那两条没去处的缺项同一纪律）。
            // 数字一律来自那一条只读通道（`HomeUsageReadout` 的四颗 `total*`，见 HomeUsageReadout.kt 的
            // "数字来路只有一条"），界面里没有一颗写死的数；
            // 四格（生成 / 复制 / 采纳 / 花费）刻意收得比面板那条更窄——只是"一块简洁小卡"，
            // 不恢复上一轮删掉的版本区/内部指标格（新指导书 §五 取舍③第④条）。
            // 标签与单位串一律现读资源（`stringResource` 只能在组合期调，故先解成实参再交给
            // 纯映射 `homeUsageMetrics`——那颗不是 @Composable，不能在它里面摸资源）。
            val usage by homeStatus.usage.collectAsStateWithLifecycle()
            val countFormat = stringResource(R.string.home_usage_count)
            LbMetricGrid(
                modifier = Modifier.testTag(LbHomeTags.USAGE_CARD),
                metrics = homeUsageMetrics(
                    readout = usage,
                    generatedLabel = stringResource(R.string.home_usage_generated),
                    copiedLabel = stringResource(R.string.home_usage_copied),
                    adoptedLabel = stringResource(R.string.home_usage_adopted),
                    costLabel = stringResource(R.string.home_usage_cost),
                    countUnit = { n -> String.format(countFormat, n) },
                    costUnknown = stringResource(R.string.cost_unknown),
                    costBelowCent = stringResource(R.string.cost_below_cent, HOME_COST_CURRENCY)
                )
            )
        }
    }
}
