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
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.LB_SCREEN_HORIZONTAL_MARGIN
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.ui.KnowledgeBaseActivity
import com.lovebrain.app.viewmodel.HomeStatusViewModel

/**
 * 首页——精简但有完成度的一屏：一条浮起来的 hero 状态卡、仅黄灯时那一行**带出口**的缺项、2×2 四入口。
 *
 * 依据基线 v1 §1（F2 首页族那一张表）+ §3.3/§3.4/§3.9/§3.10 与 §4 的首页裁决（采 candidate-a 方向）。
 * 从上到下：
 * 1. [AssistantStatusCard]：左边红/黄/绿小灯 + **写在屏幕上的状态名**，右边朝右三角（开始）或方块（停止）；
 *    `Xl` 24 圆角 + 四边等距 16 + `ELEVATION_DEFAULT` 2——这一格是这一屏唯一浮起来的东西；
 * 2. 只有黄灯才存在的 [HomeSetupHint]：一行 `bodySmall` 12 的黄字（浅黄底容器，不是裸奔的一行 log），
 *    末尾配一颗设计系统现有的行内胶囊动作「去设置」——回应"不能只给一句黄警告让用户自己摸索"；
 * 3. 四入口 = 设计系统 `LbActionCard` 的 `GridCell` 紧凑档：`Lg` 16 圆角 + 内 12 + **无阴影**，
 *    只交名字与小图标、**不写副标题**（空的副标题槽默认不画，见那颗组件自己的注释）。
 *
 * 层级不是靠新文案、也不是靠第五种状态买来的：hero 与入口卡是**两档**（圆角 24 对 16、
 * 内距 16 对 12、阴影 2 对无），段间距 24、卡与缺项 8、入口两排之间 12、列间 12——
 * 旧版那"三处一律 12dp"读不出层级，四格 + 一卡于是就是五个等大方块。
 *
 * 页面不判任何条件：灯色、形状、黄字全部来自 [AdvisorStatus.render]，而那一行的状态来自
 * [HomeStatusViewModel]——它才是唯一会看权限、配置、知识库与探针结果的地方。
 * 页面**只多做一件事**：从 `status.missing` 里认"这一条缺项有没有能带用户去的地方"
 * （只有未配置模型供应商那一格有：就是四入口里那颗「模型供应商」），这不改灯、不改形状、
 * 不改黄字，那三样仍然只在 `render()` 一处派生。
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
 * 上面那条 `onPlay`（用户按 ▶）。切了供应商或换了当前对象时，它作废那一组身份的旧结论
 * （灯落回黄 + 念"连接还没检查过"这一条实话：从没为这组身份按过 ▶，不等于检查失败），
 * 但不替用户补发请求。黄字末尾那颗「去设置」也**只是导航**：它不碰 `onPlay`，
 * 一次请求都不该多发。
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
    // 「去设置」那两个字走既有资源（`provider_open_settings`，zh + en 两份都在盘上）：
    // 这一席没有 res/values 的写入权，而同一句短词再抄一份 Kotlin 字面量正是文案预算那把尺管的事。
    val setupGoLabel = stringResource(R.string.provider_open_settings)
    // 缺项里只有"没配供应商"这一格在首页就有明确的去处（四入口里那颗「模型供应商」）。
    // 其余缺项的出口另有宿主，接线见 handoffs/2026-10-06-H1b-DragHandle接线单.md 的第二节：
    // 悬浮窗授权在平台那条链上（去系统设置页）、知识库那一格在这屏的入口卡上、
    // "服务没起来/连接还没检查过"这两条根本没有"去设置"可给——不硬造一颗假按钮。
    val setupActionLabel = if (AdvisorMissing.NoProvider in status.missing) setupGoLabel else null

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
        // 段与段之间 24（`xxxl`）：这一档差值是"hero 那一块"与"入口那一块"之间唯一的视觉断点，
        // 旧版三处一律 12 的时候五格读成一张网格。水平边距仍只有 `LbScreenScaffold` 那一个主人。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // 这一页没有页头（`LbScreenScaffold` 的 `topBar == null` 那一支只给水平边距），
                // 所以顶距必须由页面自己补上：不补的话第一张卡直接坐在状态栏下沿上
                // （2026-10-06 视觉基线重录前一眼看到的：实到截图里卡片顶到 y=0，
                //  宿主 `SetupRoot.kt:114-120` 只有 `systemBarsPadding()`，那一格是 0 而不是留白）。
                // 数不新写一颗：与左右同用 `LB_SCREEN_HORIZONTAL_MARGIN` 那一个主人（24dp）。
                .padding(top = LB_SCREEN_HORIZONTAL_MARGIN)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxxl)
        ) {
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
                        // 顺序是承重的：先清"稍后"（否则回到首页游标还是 DEFERRED，罩子永不回来），
                        // 再导航到供应商那一格。两颗都做，一颗都不落第二本账。
                        onAction = {
                            onResumeGuide()
                            onNavigateProviders()
                        }
                    )
                }
            }

            // ── 2×2 四入口：同一颗 LbActionCard 紧凑档，两排之间 12、列间 12 ──
            // 四格那五个数（方块 40 / 图标 22 / 尾部箭头 20 / 卡圆角 16 / 列间 12）与 1.3.1 逐字同值；
            // 本轮只动了两件事：卡内留白按 §3.3 收到 12、空的副标题槽不再占一格。
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
                        iconBlock = LbActionCardIconBlock.GridCell,
                        onClick = { context.startActivity(Intent(context, KnowledgeBaseActivity::class.java)) }
                    )
                    LbActionCard(
                        modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_FEEDBACK)
                            .coachAnchor(LbHomeTags.ENTRY_FEEDBACK),
                        iconRes = R.drawable.ic_feature_feedback,
                        title = HOME_ENTRY_FEEDBACK,
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
                        iconBlock = LbActionCardIconBlock.GridCell,
                        onClick = onNavigateCaptureApps
                    )
                    LbActionCard(
                        modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_PROVIDER)
                            .coachAnchor(LbHomeTags.ENTRY_PROVIDER),
                        iconRes = R.drawable.ic_unplug,
                        title = HOME_ENTRY_PROVIDER,
                        iconBlock = LbActionCardIconBlock.GridCell,
                        onClick = onNavigateProviders
                    )
                }
            }
        }
    }
}
