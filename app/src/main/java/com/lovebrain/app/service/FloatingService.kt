package com.lovebrain.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lovebrain.app.AppConfig
import com.lovebrain.app.R
import com.lovebrain.app.data.EventBus
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.bubble.BubbleUiState
import com.lovebrain.app.ui.bubble.FloatingBubble
import com.lovebrain.app.ui.SetupActivity
import com.lovebrain.app.ui.panel.LoveBrainPanelScreen
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.util.L
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import java.util.LinkedHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 军师悬浮窗服务 v5（Koin + EventBus 版）。
 * 气泡 + ComposeView 面板，面板内部全部由 Jetpack Compose 渲染。
 *
 * 改进：
 * - 依赖通过 Koin 注入，不再手动 new
 * - 服务间通信通过 EventBus（SharedFlow），不再用静态方法
 */
class FloatingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        /** 服务被杀时，无障碍服务暂存的消息（重启后消费） */
        var pendingMessage: String? = null

        /** 供外部查询服务是否存活 */
        @Volatile
        var instance: FloatingService? = null
            private set
    }

    // ═══════════ Koin 注入 ═══════════
    private val viewModel: LoveBrainViewModel by inject()
    private val securePrefs: SecurePrefs by inject()

    // ═══════════ Lifecycle / ViewModelStore / SavedState ═══════════

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    // ═══════════ 核心字段 ═══════════

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var composeView: ComposeView? = null
    private var isPanelShowing = false

    /** 悬浮球 UI 状态（Compose 只读渲染，Service 侧更新） */
    private val bubbleUi = mutableStateOf(BubbleUiState())
    /** 闲置计时器（4s 半透明 → 8s 滑出半隐藏） */
    private var idleJob: Job? = null
    /** 侧边半隐藏状态（闲置 8s 后滑出，只露 12dp 边缘） */
    private var bubbleHidden = false

    /** 系统"移除动画"（无障碍）→ 吸附动画瞬时完成（WCAG 2.3.3） */
    private val reducedMotion: Boolean
        get() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private var panelW = 0
    private var panelH = 0

    // A4 修复：去重从单值改为最近 N 条集合，避免合法重复内容被永久丢弃
    private val recentClips = object : LinkedHashSet<String>() {
        private val maxSize = 20
        override fun add(e: String): Boolean {
            // 超容量时移除最早的（LinkedHashSet 保持插入顺序）
            while (size >= maxSize) iterator().let { it.next(); it.remove() }
            return super.add(e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // v7 零保活：START_NOT_STICKY = 服务被杀后系统不再重建。
        // 无 FGS、无 STICKY、无自重启。服务完全由用户手动开关。
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 终版：不做任何保活、不干预无障碍、不自杀重启。
        // 用户实测"不锁定卡片"时系统正常回收无障碍，无需任何附加机制。
        super.onTaskRemoved(rootIntent)
        runCatching { stopSelf() }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        L.init(this)
        L.w("=== FloatingService onCreate (v5 Koin+EventBus) ===")

        // 暗色模式已删，全站固定亮色

        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // 面板尺寸持久化：恢复上次用户调整的面板大小（调研：NN/G 10 Heuristics #3 User Control and Freedom）
        val savedW = securePrefs.panelWidth
        val savedH = securePrefs.panelHeight
        panelW = if (savedW > 0) savedW else dp(AppConfig.PANEL_DEFAULT_W)
        panelH = if (savedH > 0) savedH else dp(AppConfig.PANEL_DEFAULT_H)

        // 消费无障碍服务暂存的消息（服务被杀重启后）
        pendingMessage?.let { msg ->
            viewModel.addMessage(ChatMessage.Role.HER, msg)
            pendingMessage = null
            L.w("consumed pendingMessage from accessibility restart")
        }

        // 订阅 EventBus：接收无障碍服务捕获的消息
        scope.launch {
            EventBus.capturedMessages.collect { event ->
                val stored = addClipIfNew(event.text, viewModel.currentRole.value)
                // A4 修复：只在消息真正入库时才亮红点（去重丢弃时不亮）
                if (stored && bubbleView != null) {
                    bubbleUi.value = bubbleUi.value.copy(badgeCount = bubbleUi.value.badgeCount + 1)
                    if (bubbleHidden) showBubbleFromEdge()
                }
            }
        }

        // 订阅 EventBus：App 首页功能卡片（今日锦囊/谈心模式/五维向量）直达面板对应功能
        scope.launch {
            EventBus.panelRequest.collect { req ->
                if (req != null) {
                    EventBus.consumePanelRequest()
                    L.w("panel request: mode=${req.mode} showPlan=${req.showPlan}")
                    if (!isPanelShowing) showPanel()
                    viewModel.setPanelMode(req.mode)
                    if (req.mode == 0) {
                        if (req.showPlan) viewModel.openPlanPanel() else viewModel.dismissPlanPanel()
                    }
                }
            }
        }

        // 终版：零保活、不干预无障碍。服务由用户手动开启，系统正常管理。
        showBubble()

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDestroy() {
        L.w("=== FloatingService onDestroy ===")
        instance = null
        bubbleAnim?.cancel()      // E3：防动画回调持有已销毁 Service
        panelExitAnim?.cancel()
        removeBubble()
        destroyPanel()
        viewModel.dispose()      // ：显式取消 VM 生成协程，防泄漏
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    // A4 修复：返回是否真正入库，调用方据此决定是否亮红点
    private fun addClipIfNew(text: String?, role: ChatMessage.Role = viewModel.currentRole.value): Boolean {
        if (text.isNullOrEmpty()) return false
        if (recentClips.contains(text)) return false
        recentClips.add(text)
        viewModel.addMessage(role, text)
        return true
    }

    // ═══════════ 气泡（ComposeView 容器：纯主球） ═════════
    // 单击主球 → 直接展示完整悬浮窗；
    // 每次启动小球固定在页面左上方，不记忆位置/状态。
    // 手势在 Compose 内部处理（点击/拖拽判定 + 20dp 阈值），状态由 bubbleUi 持有。

    private fun showBubble() {
        if (bubbleView != null) return
        val size = dp(AppConfig.BUBBLE_SIZE)

        // 第4轮：每次启动都固定在页面左上方（需求#11），不恢复上次位置（需求#10）
        val initX = dp(AppConfig.BUBBLE_EDGE_MARGIN)
        val initY = dp(48)   // 避开状态栏

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            // 矩形外露修复：RGBA_8888 比 TRANSLUCENT 更可靠（部分 ROM 上 TRANSLUCENT 背景不透明）
            PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initX
            y = initY
        }
        bubbleParams = params

        val cv = newOverlayComposeView().apply {
            setContent {
                LoveBrainTheme {
                    FloatingBubble(
                        state = bubbleUi.value,
                        onBubbleClick = { onBubbleTap() },
                        onDragDelta = { dx, dy -> onBubbleDrag(dx, dy) },
                        onDragEnd = { snapBubbleToEdge() }
                    )
                }
            }
        }

        bubbleView = cv
        if (!Settings.canDrawOverlays(this)) {
            L.w("overlay permission revoked, stopping service")
            stopSelf()
            return
        }
        runCatching { wm.addView(cv, params) }
            .onSuccess {
                // addView 后再次强制清背景：ComposeView attach 后可能被 theme 背景覆盖（实测矩形外露仍复现）
                cv.forceTransparentWindowBackground()
                L.w("showBubble addView OK size=${size} pos=(${params.x},${params.y})")
                resetIdleTimer()   // 启动闲置半透明计时
            }
            .onFailure {
                L.e("showBubble addView failed", it)
                stopSelf()
            }
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { wm.removeView(it) } }
        bubbleView = null
    }

    /** 矩形外露修复：强制悬浮窗 view 背景透明（addView 后调用，防 ComposeView attach 后重设主题背景） */
    private fun android.view.View.forceTransparentWindowBackground() {
        try {
            background = null
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setClipToOutline(false)
        } catch (_: Exception) {
            // 忽略：最坏情况保持原背景
        }
    }

    // ═══════════ 小球点击 → 完整悬浮窗（需求#31：点击小球 → 直接出现悬浮窗，完整展示） ═══════════

    private fun onBubbleTap() {
        L.w("bubble: onBubbleTap bubbleHidden=$bubbleHidden panel=$isPanelShowing")
        // 半隐藏态：一次点击 = 回弹 + 直接开面板（修复需求12：靠墙时需点两下才能开）
        if (bubbleHidden) {
            // 瞬时回弹（不启动动画）：showPanel() 紧接着会读取 bubbleParams 计算面板位置，
            // 如果用动画，bubbleParams 此时还在隐藏位置 → 面板位置算错（出现在屏幕中间）
            showBubbleFromEdge(animate = false)
            resetIdleTimer()
            if (isPanelShowing) hidePanel() else showPanel()
            return
        }
        resetIdleTimer()
        // 点击小球 → 直接出现悬浮窗（完整展示）
        if (isPanelShowing) hidePanel() else showPanel()
    }

    // ═══════════ 拖拽与边缘吸附 ═══════════

    private fun onBubbleDrag(dx: Float, dy: Float) {
        // 半隐藏态拖拽：先回弹，再继续拖
        if (bubbleHidden) showBubbleFromEdge()
        val cv = bubbleView ?: return
        val p = bubbleParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val mainSize = dp(AppConfig.BUBBLE_SIZE)

        p.x = (p.x + dx.toInt()).coerceIn(0, (screenW - mainSize).coerceAtLeast(0))
        p.y = (p.y + dy.toInt()).coerceIn(0, (screenH - mainSize).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(cv, p) }
        if (!bubbleUi.value.dragging) {
            bubbleUi.value = bubbleUi.value.copy(dragging = true, idleDimmed = false)
        }
        if (isPanelShowing) repositionPanel()
        resetIdleTimer()
    }

    /** 松手后吸附到最近的屏幕边缘（保留平滑滑向动画；需求#12：去掉吸附震动） */
    private fun snapBubbleToEdge() {
        bubbleUi.value = bubbleUi.value.copy(dragging = false)
        bubbleHidden = false
        if (bubbleView == null) return
        val p = bubbleParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val mainSize = dp(AppConfig.BUBBLE_SIZE)

        val cx = p.x + mainSize / 2
        val snapLeft = cx < screenW / 2
        // 同步红点偏移方向（朝屏幕中心侧）
        bubbleUi.value = bubbleUi.value.copy(snapLeft = snapLeft)
        val targetX = if (snapLeft) dp(AppConfig.BUBBLE_EDGE_MARGIN)
        else screenW - mainSize - dp(AppConfig.BUBBLE_EDGE_MARGIN)
        // Y 方向仅 clamp 在安全区内（顶部留状态栏，底部留导航条），不强制吸附
        val targetY = p.y.coerceIn(dp(48), screenH - mainSize - dp(32))
        animateBubbleTo(targetX, targetY) { _, _ ->
            // 第4轮：去掉触觉震动（需求#12）；不再持久化位置（需求#10/#11，每次启动固定左上方）
            resetIdleTimer()
        }
    }

    /** 悬浮球平滑位移动画（250ms FastOutSlowIn，驱动 wm.updateViewLayout） */
    private fun animateBubbleTo(targetX: Int, targetY: Int, onDone: ((Int, Int) -> Unit)? = null) {
        val cv = bubbleView ?: return
        val p = bubbleParams ?: return
        val startX = p.x
        val startY = p.y
        if (startX == targetX && startY == targetY) {
            onDone?.invoke(targetX, targetY)
            return
        }

        bubbleAnim?.cancel()  // E3：取消上一个位移动画，防堆积
        bubbleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (reducedMotion) 0L else AppConfig.BUBBLE_SNAP_MS.toLong()
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                p.x = startX + ((targetX - startX) * f).toInt()
                p.y = startY + ((targetY - startY) * f).toInt()
                runCatching { wm.updateViewLayout(cv, p) }
                if (isPanelShowing) repositionPanel()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    p.x = targetX
                    p.y = targetY
                    runCatching { wm.updateViewLayout(cv, p) }
                    onDone?.invoke(targetX, targetY)
                }
            })
            start()
        }
    }

    // ═══════════ 闲置计时（第2轮：4s 半透明降遮挡；第3轮：8s 滑出半隐藏） ═══════════
    // 阶段1：4s 无交互 → 半透明（AssistiveTouch）；阶段2：再 4s → 滑出侧边只露 12dp（QQ 悬挂）

    private fun resetIdleTimer() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(AppConfig.BUBBLE_IDLE_DIM_MS)
            if (!isPanelShowing) {
                bubbleUi.value = bubbleUi.value.copy(idleDimmed = true)
            }
            delay(AppConfig.BUBBLE_HIDE_IDLE_MS - AppConfig.BUBBLE_IDLE_DIM_MS)
            hideBubbleToEdge()
        }
    }

    /** 滑出半隐藏：容器滑向边缘只露 BUBBLE_HIDE_EDGE_DP，触摸露边即回弹 */
    private fun hideBubbleToEdge() {
        if (bubbleHidden) return
        if (bubbleUi.value.dragging) return
        if (isPanelShowing) return  // 悬浮窗展示中不隐藏（保证可见性）
        if (bubbleUi.value.badgeCount > 0) return  // 有未读角标时不隐藏（保证可见性）
        if (bubbleView == null) return
        val p = bubbleParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val mainSize = dp(AppConfig.BUBBLE_SIZE)
        val edge = dp(AppConfig.BUBBLE_HIDE_EDGE_DP)

        val cx = p.x + mainSize / 2
        val targetX = if (cx < screenW / 2) -(mainSize - edge) else screenW - edge
        bubbleHidden = true
        // 露边呼吸开启，让用户知道球还在（不弹窗不提示）
        bubbleUi.value = bubbleUi.value.copy(edgeBreathing = true)
        // 半隐藏是临时状态，不保存位置、不触发触觉
        animateBubbleTo(targetX, p.y)
    }

    /** 回弹：从半隐藏态滑回吸附位置
     *  @param animate true=播放滑动动画（拖拽/未读回弹等场景）；false=瞬时设置位置
     *               （onBubbleTap 场景：showPanel() 紧接着读 bubbleParams，不能等动画）
     */
    private fun showBubbleFromEdge(animate: Boolean = true) {
        if (!bubbleHidden) return
        bubbleHidden = false
        bubbleUi.value = bubbleUi.value.copy(edgeBreathing = false)
        val cv = bubbleView ?: return
        val p = bubbleParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val mainSize = dp(AppConfig.BUBBLE_SIZE)
        val cx = p.x + mainSize / 2
        val targetX = if (cx < screenW / 2) dp(AppConfig.BUBBLE_EDGE_MARGIN)
        else screenW - mainSize - dp(AppConfig.BUBBLE_EDGE_MARGIN)
        val targetY = p.y.coerceIn(dp(48), screenH - mainSize - dp(32))
        if (animate) {
            animateBubbleTo(targetX, targetY)
        } else {
            // 瞬时设置位置：球体随后被 showPanel() 设为 GONE，视觉跳变不可见
            p.x = targetX
            p.y = targetY
            runCatching { wm.updateViewLayout(cv, p) }
        }
        resetIdleTimer()
    }

    // ═══════════ 面板（ComposeView） ═══════════

    private fun showPanel() {
        ensurePanelCreated()
        val cv = composeView ?: return
        isPanelShowing = true

        // BUG 修复：打开前必须取消退出动画并重置透明度，
        // 否则上次淡出残留 alpha=0 → 面板"显示"了但完全透明看不见
        panelExitAnim?.cancel()
        panelExitAnim = null
        isPanelHiding = false
        cv.alpha = 1f

        // 打开面板 → 未读角标清零
        bubbleUi.value = bubbleUi.value.copy(badgeCount = 0)

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val pw = panelW.coerceAtMost(screenW - dp(8))
        val ph = panelH.coerceAtMost(screenH - dp(80))
        val (px, py) = calcPanelPosition(pw, ph, screenW, screenH)

        val p = cv.layoutParams as? WindowManager.LayoutParams ?: return
        p.width = pw
        p.height = ph
        p.x = px
        p.y = py
        runCatching { wm.updateViewLayout(cv, p) }

        cv.visibility = View.VISIBLE
        // ：读屏焦点转移——面板 VISIBLE 后把焦点与播报落进面板，避免读屏焦点留在已隐藏气泡上（announce 用固定文案，不拼用户内容）
        cv.post {
            runCatching { cv.requestFocus() }
            cv.announceForAccessibility("军师面板已打开")
        }
        bubbleView?.visibility = View.GONE

        scope.launch(Dispatchers.IO) { viewModel.refreshKnowledgeBases() }
        //  死锁修复：composition 跨面板隐藏/显示存活，LaunchedEffect(Unit) 只执行一次；
        // showPanel 每次开面板必经，在此强制重读工单三态（配置后 isProviderReady 即时生效）
        viewModel.refreshTicketState()
    }

    private var panelExitAnim: ValueAnimator? = null
    private var bubbleAnim: ValueAnimator? = null   // E3：持有引用，onDestroy 取消防泄漏
    private var isPanelHiding = false

    private fun hidePanel() {
        // BUG 修复：防重入——退出动画期间重复触发会堆积动画导致卡顿
        if (isPanelHiding) return
        isPanelShowing = false
        val cv = composeView ?: return
        isPanelHiding = true

        // 淡出动画：150ms alpha 1→0，结束后才真正隐藏
        panelExitAnim?.cancel()
        panelExitAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 150L
            addUpdateListener { anim ->
                cv.alpha = anim.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cv.visibility = View.GONE
                    cv.alpha = 1f   // 复位，避免下次打开残留透明
                    bubbleView?.visibility = View.VISIBLE
                    // ：读屏播报——气泡重新可见后告知面板已关闭（固定文案）
                    bubbleView?.announceForAccessibility("军师面板已关闭")
                    isPanelHiding = false
                    panelExitAnim = null
                }
                override fun onAnimationCancel(animation: Animator) {
                    isPanelHiding = false
                }
            })
            start()
        }
    }

    private fun destroyPanel() {
        isPanelShowing = false
        composeView?.let { cv ->
            cv.disposeComposition()
            runCatching { wm.removeView(cv) }
        }
        composeView = null
    }

    private fun ensurePanelCreated() {
        if (composeView != null) return

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val pw = panelW.coerceAtMost(screenW - dp(8))
        val ph = panelH.coerceAtMost(screenH - dp(80))
        val (px, py) = calcPanelPosition(pw, ph, screenW, screenH)

        // 键盘修复：面板展示期间去掉 FLAG_NOT_FOCUSABLE，否则输入框永远拿不到焦点、
        // 软键盘无法唤起（旧"获焦→清 flag"链路是死循环：不清 flag 永远获不了焦）。
        // 面板隐藏/销毁时窗口移除，不影响微信等底层 App 交互。
        // ：删外触监听标志 + 外点收起——误触即收体验差，
        // 收起改由头部收起按钮显式触发（项 2）；气泡侧 :227 外触标志属气泡既有行为，不动。
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        val params = WindowManager.LayoutParams(
            pw, ph,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            // 矩形外露修复：RGBA_8888（同气泡）
            PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = px
            y = py
            // 悬浮窗美化：面板淡入淡出动画（替代 0=生硬弹出）
            windowAnimations = R.style.PanelWindowAnimation
        }

        val cv = newOverlayComposeView().apply {
            visibility = View.GONE

            setContent {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = viewModel,
                        onFocusChange = { _ ->
                            // 旧的 flag 切换链路已删（与键盘修复冲突：失焦回加 NOT_FOCUSABLE
                            // 会导致输入框再也弹不出键盘）。面板展示期固定可聚焦，无需回调。
                        },
                        onResize = { newW, newH ->
                            handleResize(newW, newH)
                        },
                        onResizeEnd = {
                            // : 拖拽结束才写 SecurePrefs，避免每帧 onDrag 都触发磁盘写入
                            persistPanelSize()
                        },
                        onMove = { dx, dy ->
                            handleMove(dx, dy)
                        },
                        onCopy = { text ->
                            if (text.isNotEmpty()) {
                                copyToClipboard(text)
                            }
                        },
                        // ：未配置供应商引导——打开设置页（新任务栈，不干扰宿主 App）
                        onOpenSettings = {
                            startActivity(Intent(this@FloatingService, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        // ：头部收起按钮 = 现外点收起行为（复用 hidePanel 150ms 淡出 + 气泡重现）
                        onCollapse = { hidePanel() }
                    )
                }
            }
        }

        composeView = cv

        if (!Settings.canDrawOverlays(this)) {
            composeView = null
            stopSelf()
            return
        }
        runCatching { wm.addView(cv, params) }
            .onSuccess {
                // addView 后再次强制清背景（矩形外露修复）
                cv.forceTransparentWindowBackground()
            }
            .onFailure {
                L.e("ensurePanelCreated addView failed", it)
                composeView = null
                stopSelf()
            }
    }

    private fun repositionPanel() {
        val cv = composeView ?: return
        val p = cv.layoutParams as? WindowManager.LayoutParams ?: return

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val pw = cv.width.takeIf { it > 0 } ?: panelW
        val ph = cv.height.takeIf { it > 0 } ?: panelH

        val (px, py) = calcPanelPosition(pw, ph, screenW, screenH)
        p.x = px
        p.y = py
        runCatching { wm.updateViewLayout(cv, p) }
    }

    private fun calcPanelPosition(pw: Int, ph: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
        // 重写：水平方向永远贴屏幕边缘——球在左半屏→贴左边缘，球在右半屏→贴右边缘。
        // 旧逻辑小面板时把面板放在"球右侧 64dp"，视觉上是屏幕中间；大面板因超宽被挤回左边缘，
        // 造成"只有小面板才出现在屏幕中间"的 bug。面板显示时球已 GONE，无需避重叠。
        val mainSize = dp(AppConfig.BUBBLE_SIZE)
        val bLeft = bubbleParams?.x ?: dp(AppConfig.BUBBLE_EDGE_MARGIN)
        val bTop = bubbleParams?.y ?: dp(48)
        val bCx = bLeft + mainSize / 2

        val px = if (bCx < screenW / 2) dp(4) else (screenW - pw - dp(4)).coerceAtLeast(dp(4))

        // 垂直方向：与球顶部大致对齐，clamp 在安全区（顶留状态栏、底留导航条）
        var py = bTop - dp(12)
        if (py + ph > screenH - dp(60)) py = screenH - ph - dp(60)
        if (py < dp(24)) py = dp(24)
        return px to py
    }

    private fun handleResize(newWpx: Int, newHpx: Int) {
        val cv = composeView ?: return
        val p = cv.layoutParams as? WindowManager.LayoutParams ?: return

        val newW = newWpx.coerceIn(dp(AppConfig.PANEL_MIN_W), dp(AppConfig.PANEL_MAX_W))
        val newH = newHpx.coerceIn(dp(AppConfig.PANEL_MIN_H), dp(AppConfig.PANEL_MAX_H))

        p.width = newW
        p.height = newH
        panelW = newW
        panelH = newH
        // : 持久化移至 onResizeEnd（拖拽松手时），此处只更新内存与视图
        runCatching { wm.updateViewLayout(cv, p) }
    }

    /** : 拖拽松手时持久化面板尺寸到 SecurePrefs */
    private fun persistPanelSize() {
        securePrefs.panelWidth = panelW
        securePrefs.panelHeight = panelH
    }

    private fun handleMove(dxPx: Float, dyPx: Float) {
        val cv = composeView ?: return
        val p = cv.layoutParams as? WindowManager.LayoutParams ?: return

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        p.x = (p.x + dxPx.toInt()).coerceIn(0, (screenW - cv.width).coerceAtLeast(0))
        p.y = (p.y + dyPx.toInt()).coerceIn(0, (screenH - cv.height).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(cv, p) }
    }

    // ═══════════ 工具 ═══════════

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("军师回复", text))
        // ：复制成功可见反馈（固定文案，不含用户内容）
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        // A4 修复：不再写 recentClips——复制的是军师回复，不是捕获的消息，不应影响捕获去重
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * ：悬浮窗 ComposeView 宿主样板（桥接生命周期 + 透明背景防矩形外露）。
     * 集中一处，避免气泡/面板各抄一份导致矩形外露 bug 修不全。
     */
    private fun newOverlayComposeView(): ComposeView = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@FloatingService)
        setViewTreeViewModelStoreOwner(this@FloatingService)
        setViewTreeSavedStateRegistryOwner(this@FloatingService)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setBackground(null)
    }
}
