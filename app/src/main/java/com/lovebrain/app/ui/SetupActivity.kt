package com.lovebrain.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.lovebrain.app.R
import com.lovebrain.app.data.EventBus
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.ui.home.CoachStepCopy
import com.lovebrain.app.ui.home.HomeDestination
import com.lovebrain.app.ui.home.HomeCoachMarks
import com.lovebrain.app.ui.home.SetupRoot
import com.lovebrain.app.ui.panel.OnboardingFlow
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.GuideCursor
import com.lovebrain.app.viewmodel.INTRO_STEP_MAX
import com.lovebrain.app.viewmodel.SetupViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 设置页——Activity 宿主 + 系统权限桥接 + 根路由入口。
 *
 * 职责仅限于：
 * - 取 ViewModel、决定介绍页在不在场、把游标交给首页罩子
 * - 悬浮窗权限申请与 Service 启停
 * - 将回调委托给 [SetupRoot] 组合根
 *
 * 页面 UI 拆分到 `ui/home/` 包：HomeScreen、ProviderSection、HomeCoachMarks。
 *
 * ## 引导为什么不再"一翻页就没了"（用户原话第 14 条）
 *
 * 旧写法是 `val showOnboarding = viewModel.shouldShowOnboarding()` 这一颗一次性 local val，
 * 而三条出口（跳过 / 完成 / 去设置）**全走 `completeOnboarding() + recreate()`**——
 * 于是"看过一遍向导"就等于引导永久结束，翻页、旋转、去系统设置回来都会把它冲掉。
 *
 * 现在闸门是状态-driven 的两颗字段（[introOnScreen] + [guideCursor]）：
 * - 介绍页只在 `introSeen=false` 时在场；三条出口都只经 [GuideExitPaths] 走
 *   `SetupViewModel` 的公开 API，**没有任何一条再写"引导完成"**（`setContent` 也只调一次，
 *   本仓库记过第二次调它必崩的坑）；
 * - 介绍层收起来之后，引导以 [HomeCoachMarks] 的形态**覆盖在首页上**，指向游标那一格的入口，
 *   滑动、进子页、回来都只是在 ON_RESUME 重算一次游标，不再消失。
 */
class SetupActivity : ComponentActivity() {

    // by viewModel() 而非 by inject()：后者不经 ViewModelStore，配置变更后 VM 内存态会丢
    private val viewModel: SetupViewModel by viewModel()

    /** 介绍层在不在场（唯一的闸门；旧的那颗一次性 local val 就是"一翻页就没"的根） */
    private var introOnScreen by mutableStateOf(false)

    /** 指引现在停在哪一格：每格都从 `SetupViewModel.currentGuideCursor` 派生，UI 不自己判 */
    private var guideCursor by mutableStateOf(GuideCursor.NONE)

    /**
     * 介绍页第几格。原先它只住在 `OnboardingFlow` 的 `remember` 里（重建即丢），
     * 现在归宿主持有并写进 [BUNDLE_KEY_INTRO_STEP]：旋转与 Activity 重建后回到同一步。
     * ⚠ 进程被杀后重进要回到同一步还缺 `SettingsStorePort` 上的一颗键——本席不许自己加键，
     * 已落交接单 §4。
     */
    private var introStep by mutableIntStateOf(0)

    /**
     * 罩子那颗主动作点名要跳的首页子页（G1b 接线单 §3 的那条接电线，宿主这一侧）。
     * 只能由 `SetupRoot` 消费一次并交回 null：挂着不撤的话，下一次重组会再跳一次。
     */
    private var pendingGuideTarget by mutableStateOf<HomeDestination?>(null)

    /**
     * 游标 → 首页子页目的地。这张表与罩子里那本锚点表（`coachAnchorKeyFor`）各管一件事：
     * 那本说"箭头指哪一颗"，这本说"按下去跳哪一页"——合成一本就会让导航去读几何锚点。
     */
    private fun guideDestinationFor(cursor: GuideCursor): HomeDestination? = when (cursor) {
        GuideCursor.PROVIDER -> HomeDestination.Providers
        // 授权与开关同住「消息捕获」那一页（首页没有第二颗无障碍入口）
        GuideCursor.ACCESSIBILITY, GuideCursor.CAPTURE -> HomeDestination.CaptureApps
        GuideCursor.NONE, GuideCursor.DONE, GuideCursor.DEFERRED_TO_HINT -> null
    }

    private fun tempHideFloating() {
        val intent = Intent(this, FloatingService::class.java)
            .setAction("com.lovebrain.app.action.TEMP_HIDE")
        ContextCompat.startForegroundService(this, intent)
    }

    private fun restoreFloating() {
        val intent = Intent(this, FloatingService::class.java)
            .setAction("com.lovebrain.app.action.RESTORE")
        ContextCompat.startForegroundService(this, intent)
    }

    private var pendingOverlayStart = false
    private var pendingPanelMode: Int? = null
    private var pendingShowPlan: Boolean = false

    private fun startFloatingService(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayStart = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return false
        }
        ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
        return true
    }

    private fun openPanelFromHome(mode: Int, showPlan: Boolean) {
        if (!startFloatingService()) {
            pendingPanelMode = mode
            pendingShowPlan = showPlan
            return
        }
        EventBus.requestPanel(mode, showPlan)
    }

    override fun onResume() {
        super.onResume()
        if (pendingOverlayStart && Settings.canDrawOverlays(this)) {
            pendingOverlayStart = false
            ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
            pendingPanelMode?.let { mode ->
                EventBus.requestPanel(mode, pendingShowPlan)
                pendingPanelMode = null
            }
        }
        // 从系统设置/子页回来：权限与开关都可能被别的进程改过，重读事实并重算游标。
        // 罩子因此不靠"用户点过哪颗按钮"活着——撤销授权它会自己退回未满足那一格。
        if (!introOnScreen) publishGuideCursor()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 敏感页面 FLAG_SECURE——防止 API Key / 知识库内容在最近任务截图中泄露
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // 步号的真源是偏好那一颗（`SettingsStorePort.introStep`，G1b 接线单 §4 落的新键）：
        // 进程被杀后重进也要回到同一步。Bundle 那一条留着当**第二层**（同一次会话内旋转/重建更快，
        // 且拿得到就先用它），两层不一致时以盘上那一份为准——不新开第二本账，读与写都只经 VM 那两颗口。
        introStep = savedInstanceState?.getInt(BUNDLE_KEY_INTRO_STEP)
            ?.coerceIn(0, INTRO_STEP_MAX)
            ?: viewModel.restoreIntroStep()
        // shouldShowIntro 会顺手补迁移账（旧完成旗标 → introSeen + DONE），所以只在组合之外调一次
        introOnScreen = viewModel.shouldShowIntro()
        if (!introOnScreen) publishGuideCursor()

        setContent {
            LoveBrainTheme {
                Crossfade(
                    targetState = introOnScreen,
                    animationSpec = tween(300),
                    label = "intro_home"
                ) { showIntro ->
                    if (showIntro) {
                    OnboardingFlow(
                        onSkip = { closeIntro(GuideExit.Skip) },
                        onComplete = { closeIntro(GuideExit.Complete) },
                        onOpenSettings = { closeIntro(GuideExit.OpenSettings) },
                        currentStep = introStep,
                        onStepChange = {
                            introStep = it
                            viewModel.saveIntroStep(it)
                        }
                    )
                } else {
                    // 罩子与首页同层：SetupRoot 一行没改，覆盖层是宿主的兄弟节点
                    Box(modifier = Modifier.fillMaxSize()) {
                        SetupRoot(
                            viewModel = viewModel,
                            onStartService = { startFloatingService() },
                            onOpenPanel = { mode, showPlan -> openPanelFromHome(mode, showPlan) },
                            onTempHide = { tempHideFloating() },
                            onRestore = { restoreFloating() },
                            // 直达导航的两颗：宿主点名，SetupRoot 跳完就把这颗收回 null
                            guideTarget = pendingGuideTarget,
                            onGuideTargetConsumed = { pendingGuideTarget = null }
                        )
                        HomeCoachMarks(
                            cursor = guideCursor,
                            copy = coachCopyFor(guideCursor),
                            // 主动作做两件事：① 清掉"稍后"（否则回到首页游标还是 DEFERRED，罩子永不回来）
                            // ② 点名要跳的那一格。原话第 8/14 条要的"缺项点到去设置真的到那一页"
                            // 到这里才是整条闭环（之前只做到"那颗在、点得响、罩子让路"）。
                            onOpenTarget = {
                                viewModel.resumeGuide(this@SetupActivity)
                                publishGuideCursor()
                                pendingGuideTarget = guideDestinationFor(guideCursor)
                            },
                            onDefer = {
                                viewModel.deferGuide()
                                publishGuideCursor()
                            },
                            onStopGuiding = {
                                viewModel.stopBeingGuided()
                                publishGuideCursor()
                            }
                        )
                    }
                }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(BUNDLE_KEY_INTRO_STEP, introStep)
    }

    /** 收介绍层：落盘只经 [GuideExitPaths]，游标由派生事实交回来，不 recreate */
    private fun closeIntro(exit: GuideExit) {
        guideCursor = GuideExitPaths.run(exit, viewModel, this)
        introOnScreen = false
        // "去设置"是要回来继续的那一趟：步号留着；跳过与走完都不留（写回 VM 那颗键，别只清内存——
        // 只清本地会让下一次重进又读回旧步号）
        if (exit != GuideExit.OpenSettings) {
            introStep = 0
            viewModel.saveIntroStep(0)
        }
    }

    /** 游标唯一的取处：读派生事实 + 顺带把该落盘的推进落盘（这颗写盘，所以不在组合期调） */
    private fun publishGuideCursor() {
        viewModel.refreshGuideFactsFromStore()
        guideCursor = viewModel.currentGuideCursor(this)
    }

    /**
     * 游标 → 那一格的字。**只做"字"的翻译**：哪一步没做由 [guideCursor] 说，这里不判事实。
     * 每句都从 `R.string` 取（罩子那一层因此一个中文字面量都不写）。
     */
    @Composable
    private fun coachCopyFor(cursor: GuideCursor): CoachStepCopy? = when (cursor) {
        GuideCursor.PROVIDER -> CoachStepCopy(
            plateDescription = stringResource(R.string.onboarding_title),
            title = stringResource(R.string.provider_page_title),
            body = stringResource(R.string.error_provider_missing),
            actionLabel = stringResource(R.string.provider_open_settings),
            deferLabel = stringResource(R.string.guide_defer),
            stopLabel = stringResource(R.string.guide_stop)
        )
        GuideCursor.ACCESSIBILITY -> CoachStepCopy(
            plateDescription = stringResource(R.string.onboarding_title),
            title = stringResource(R.string.guide_capture_entry),
            body = stringResource(R.string.home_capture_no_permission),
            actionLabel = stringResource(R.string.home_grant_accessibility),
            deferLabel = stringResource(R.string.guide_defer),
            stopLabel = stringResource(R.string.guide_stop)
        )
        GuideCursor.CAPTURE -> CoachStepCopy(
            plateDescription = stringResource(R.string.onboarding_title),
            title = stringResource(R.string.guide_capture_entry),
            body = stringResource(R.string.home_capture_status_off),
            actionLabel = stringResource(R.string.provider_open_settings),
            deferLabel = stringResource(R.string.guide_defer),
            stopLabel = stringResource(R.string.guide_stop)
        )
        GuideCursor.NONE, GuideCursor.DONE, GuideCursor.DEFERRED_TO_HINT -> null
    }

    companion object {
        /** 介绍页第几格的暂存键（进程重建用；跨进程死要 `SettingsStorePort` 加一颗，见交接单 §4） */
        private const val BUNDLE_KEY_INTRO_STEP = "setup_intro_step"
    }
}

/** 介绍层的三条出口——语义各不相同，见 [GuideExitPaths] */
internal enum class GuideExit {

    /** 「跳过」：不想现在弄——缺项交回首页那一行黄字，可经 `resumeGuide` 回来 */
    Skip,

    /** 「完成」：介绍层走完了——引导按派生事实停在最早未满足那一格 */
    Complete,

    /** 「去设置」：现在就去配——清掉先前那一下"稍后"，游标停在最早未满足那一格 */
    OpenSettings
}

/**
 * 介绍层三条出口各自的落盘（用户原话第 14 条 + 基线 v1.1 §6.8）。
 *
 * ⚠ 这里**没有任何一条**再写"引导完成"：`completeOnboarding()` 与 `recreate()` 两根通道都断了，
 * 完成与否一律由 `SetupViewModel.currentGuideCursor` 从真状态派生（供应商有效 / 无障碍已授权 /
 * 捕获已开且披露已同意）。点跳过因此不等于引导永久消失。
 *
 * 三出口两两可辨：
 * - [GuideExit.Skip] 把游标落到 `DEFERRED_TO_HINT`（罩子收、缺项交回黄字行）；
 * - [GuideExit.OpenSettings] 经 `resumeGuide` **清掉**已有的"稍后"，所以先前按过稍后的人
 *   说"我现在就去配"时，游标回到最早未满足那一格；
 * - [GuideExit.Complete] 只豁免介绍层并尊重既有"稍后"，事实齐时自己落 `DONE`
 *   （那颗派生 DONE 会把旧完成旗标一并补真，两本账从此只剩一本新账）。
 */
internal object GuideExitPaths {

    fun run(exit: GuideExit, viewModel: SetupViewModel, context: Context): GuideCursor {
        // 三条出口共同的第一步：只豁免介绍层（不写完成、不写 DONE）
        viewModel.markIntroSeen()
        when (exit) {
            GuideExit.Skip -> viewModel.deferGuide()
            GuideExit.OpenSettings -> viewModel.resumeGuide(context)
            GuideExit.Complete -> Unit
        }
        return viewModel.currentGuideCursor(context)
    }
}
