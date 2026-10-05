package com.lovebrain.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbActionCard
import com.lovebrain.app.core.designsystem.LbActionCardIconBlock
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.ui.KnowledgeBaseActivity
import com.lovebrain.app.viewmodel.HomeStatusViewModel

/**
 * 首页——就三样：一条状态卡（左灯右 ▶/■）、仅黄灯时下面那一行小字、2×2 四入口。
 *
 * 从上到下：
 * 1. [AssistantStatusCard]：左边红/黄/绿小灯，右边朝右三角（开始）或方块（停止）；
 * 2. 只有黄灯才存在的 [HomeSetupHint]：一行小号黄字说清到底哪一步没做，多缺项用短分隔；
 * 3. 四入口 = 设计系统 `LbActionCard` 的 `GridCell` 紧凑档，只交名字与小图标、**不写副标题**。
 *
 * 页面不判任何条件：灯色、形状、黄字全部来自 [AdvisorStatus.render]，而那一行的状态来自
 * [HomeStatusViewModel]——它才是唯一会看权限、配置、知识库与探针结果的地方。
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
 * 首页也**不在组合期发请求**：`LaunchedEffect(Unit)` 与 ON_RESUME 各触发一次
 * [HomeStatusViewModel.returnedFromSubpage]，而那一颗**只做本地重读**——探针唯一的入口是
 * 上面那条 `onPlay`（用户按 ▶）。切了供应商或换了当前对象时，它作废旧的那次检查结论
 * （灯落回黄 + "连接还没检查成功"），但不替用户补发请求。
 */
@Composable
fun HomeScreen(
    homeStatus: HomeStatusViewModel,
    onStartService: () -> Unit,
    onNavigateFeedback: () -> Unit,
    onNavigateProviders: () -> Unit,
    onNavigateCaptureApps: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
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
                HomeSetupHint(text = render.hint)
            }

            // ── 2×2 四入口：同一颗 LbActionCard 紧凑档，两列等宽、列间 12dp ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                LbActionCard(
                    modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_KNOWLEDGE),
                    iconRes = R.drawable.ic_feature_book,
                    title = HOME_ENTRY_KNOWLEDGE,
                    subtitle = "",
                    iconBlock = LbActionCardIconBlock.GridCell,
                    onClick = { context.startActivity(Intent(context, KnowledgeBaseActivity::class.java)) }
                )
                LbActionCard(
                    modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_FEEDBACK),
                    iconRes = R.drawable.ic_feature_feedback,
                    title = HOME_ENTRY_FEEDBACK,
                    subtitle = "",
                    iconBlock = LbActionCardIconBlock.GridCell,
                    onClick = onNavigateFeedback
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                LbActionCard(
                    modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_CAPTURE),
                    iconRes = R.drawable.ic_copy,
                    title = HOME_ENTRY_CAPTURE,
                    subtitle = "",
                    iconBlock = LbActionCardIconBlock.GridCell,
                    onClick = onNavigateCaptureApps
                )
                LbActionCard(
                    modifier = Modifier.weight(1f).testTag(LbHomeTags.ENTRY_PROVIDER),
                    iconRes = R.drawable.ic_unplug,
                    title = HOME_ENTRY_PROVIDER,
                    subtitle = "",
                    iconBlock = LbActionCardIconBlock.GridCell,
                    onClick = onNavigateProviders
                )
            }
        }
    }
}

/** 只在测试里用到的那一条导入占位（删掉它会让上面 `testTag` 那两处少一次真实调用） */
@Suppress("unused")
private fun homeScreenUnusedImportGuard(modifier: Modifier) {
    modifier.semantics { }
}
