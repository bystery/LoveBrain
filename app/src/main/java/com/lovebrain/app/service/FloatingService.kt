package com.lovebrain.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.view.Gravity
import android.view.MotionEvent
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
import com.lovebrain.app.ui.common.OverlayTextToolbarHost
import com.lovebrain.app.ui.common.rememberOverlayTextToolbar
import com.lovebrain.app.ui.SetupActivity
import com.lovebrain.app.ui.panel.LoveBrainPanelScreen
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.util.L
import com.lovebrain.app.viewmodel.LoveBrainViewModel
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
        private const val OVERLAY_CHANNEL_ID = "lovebrain_overlay"
        private const val OVERLAY_NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.lovebrain.app.action.STOP_FLOATING"

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

    /*
     * IMPORTANT OVERLAY FOCUS CONTRACT
     *
     * LoveBrain runs above arbitrary host apps.
     *
     * PANEL_PASSIVE:
     *   FLAG_NOT_FOCUSABLE must be enabled.
     *
     * PANEL_EDITING:
     *   FLAG_NOT_FOCUSABLE may be removed temporarily.
     *
     * The panel must never remain focusable merely because it is visible.
     *
     * Outside touch releases Panel editing/window focus,
     * but keeps the Panel visible.
     *
     * Never special-case host application package names.
     */

    /** Panel 焦点状态机：PASSIVE = 不抢焦点；EDITING = 临时可聚焦供输入 */
    private enum class PanelFocusMode {
        PASSIVE,
        EDITING
    }

    /** 唯一 Panel 焦点状态源 */
    private var panelFocusMode = PanelFocusMode.PASSIVE

    /** Compose 输入焦点清理回调（由 Panel 层注册，releasePanelInput 时调用） */
    private var clearComposeFocusCallback: (() -> Unit)? = null

    /** 当前持有输入焦点的输入框 ID（null = 无输入框获焦） */
    private var activeInputId: String? = null

    /**
     * 统一 Panel flags 计算函数：整个项目唯一允许计算 Panel Window flags 的入口。
     *
     * PASSIVE: NOT_FOCUSABLE | NOT_TOUCH_MODAL | WATCH_OUTSIDE_TOUCH
     * EDITING: NOT_TOUCH_MODAL | WATCH_OUTSIDE_TOUCH（去掉 NOT_FOCUSABLE）
     */
    private fun panelFlags(mode: PanelFocusMode): Int {
        var result =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (mode == PanelFocusMode.PASSIVE) {
            result = result or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return result
    }

    /**
     * 统一 Panel 焦点模式切换入口：整个项目唯一允许修改 Panel FLAG_NOT_FOCUSABLE 的方法。
     * 其他文件只能通过 [enterPanelEditing] / [exitPanelEditing] 间接调用。
     */
    private fun setPanelFocusMode(newMode: PanelFocusMode, reason: String) {
        val cv = composeView ?: return
        val params = cv.layoutParams as? WindowManager.LayoutParams ?: return

        if (panelFocusMode == newMode) return

        val oldMode = panelFocusMode
        panelFocusMode = newMode

        params.flags = panelFlags(newMode)

        runCatching {
            wm.updateViewLayout(cv, params)
        }.onSuccess {
            L.w("panel focus: $oldMode -> $newMode reason=$reason")
        }.onFailure {
            L.e("panel focus update failed: $oldMode -> $newMode reason=$reason", it)
            // 回滚状态，避免状态与实际 Window flags 不一致
            panelFocusMode = oldMode
        }
    }

    /** 进入编辑态：用户明确按下 TextField 后调用 */
    private fun enterPanelEditing(reason: String) {
        setPanelFocusMode(PanelFocusMode.EDITING, reason)
    }

    /** 退出编辑态：输入框失焦或 Panel 即将隐藏时调用 */
    private fun exitPanelEditing(reason: String) {
        setPanelFocusMode(PanelFocusMode.PASSIVE, reason)
    }

    /**
     * 释放 Panel 输入状态：清理 Compose 焦点 + 隐藏 IME + Window 恢复 NOT_FOCUSABLE。
     * 关闭顺序：1.清 TextField Focus → 2.Hide IME → 3.Window → PASSIVE
     */
    private fun releasePanelInput(reason: String) {
        // 0. 清空输入所有者
        activeInputId = null
        // 1. 清理 Compose 输入焦点
        clearComposeFocusCallback?.invoke()
        // 2. 隐藏 IME（通过清除焦点自动隐藏，此处兜底）
        composeView?.let { cv ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(cv.windowToken, 0)
        }
        // 3. Window 恢复 NOT_FOCUSABLE
        exitPanelEditing(reason)
    }

    /**
     * 统一 Panel 收起入口：收起按钮 / 模式切换关闭走此方法。
     * 注意：ACTION_OUTSIDE 不走此方法（只释放输入不关 Panel）。
     * 顺序：释放输入 → hidePanel（淡出动画）→ Bubble 恢复
     */
    private fun dismissPanelToBubble(reason: String) {
        L.w("panel dismiss reason=$reason")
        releasePanelInput("dismiss_$reason")
        hidePanel()
    }

    /** 悬浮球 UI 状态（Compose 只读渲染，Service 侧更新） */
    private val bubbleUi = mutableStateOf(BubbleUiState())
    /** 闲置计时器（4s 半透明 → 8s 滑出半隐藏） */
    private var idleJob: Job? = null
    /** 侧边半隐藏状态（B2 已禁用半隐藏阶段，此字段恒为 false，相关分支不可达，保留待后续清理） */
    private var bubbleHidden = false

    /** 系统"移除动画"（无障碍）→ 吸附动画瞬时完成（WCAG 2.3.3） */
    private val reducedMotion: Boolean
        get() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private var panelW = 0
    private var panelH = 0

    /** 洪峰去重：仅拦 ≤1.5s 内同文本的重复事件（同一次手势的系统连发），不拦用户主动重捕 */
    private var lastClipText: String? = null
    private var lastClipTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // RA-01：停止 Action —— 用户从通知点「停止」时直接 stopSelf
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 仍然 START_NOT_STICKY = 服务被杀后系统不再重建。无保活、无自重启。
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

        // CAP-01：sessionStart 必须在 instance = this 之前建立。
        // 只有 instance != null 后 CopyCaptureService 才允许 emit 有效捕获。
        // 因此 session 边界必须覆盖 instance 发布之后的全部时间，不能有宽限。
        val sessionStart = SystemClock.uptimeMillis()

        instance = this
        L.init(this)
        L.w("=== FloatingService onCreate (v5 Koin+EventBus) ===")

        // RA-01：尽快进入前台服务状态，不等 IO / AI 初始化
        ensureNotificationChannel()
        ServiceCompat.startForeground(
            this,
            OVERLAY_NOTIFICATION_ID,
            buildOverlayNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        // 暗色模式已删，全站固定亮色

        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // 面板尺寸持久化：恢复上次用户调整的面板大小（调研：NN/G 10 Heuristics #3 User Control and Freedom）
        val savedW = securePrefs.panelWidth
        val savedH = securePrefs.panelHeight
        panelW = if (savedW > 0) savedW else dp(AppConfig.PANEL_DEFAULT_W)
        panelH = if (savedH > 0) savedH else dp(AppConfig.PANEL_DEFAULT_H)

        // 订阅 EventBus：接收无障碍服务捕获的消息
        scope.launch {
            EventBus.capturedMessages.collect { event ->
                // CAP-01：严格 session 边界——早于 sessionStart 的一律是上一 session 的 replay，丢弃
                if (event.ts < sessionStart) return@collect
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
        // RA-01：销毁时确保前台状态结束
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** 返回是否真正入库，调用方据此决定是否亮红点；只拦 1.5s 内的同文本洪峰，间隔更久的重捕一律入库 */
    private fun addClipIfNew(text: String?, role: ChatMessage.Role = viewModel.currentRole.value): Boolean {
        if (text.isNullOrEmpty()) return false
        val now = SystemClock.uptimeMillis()
        if (text == lastClipText && now - lastClipTime <= AppConfig.BURST_DEDUP_WINDOW_MS) return false
        lastClipText = text
        lastClipTime = now
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
            // B1 修复：点击唤醒必须复位 idleDimmed，否则松手后球立刻又暗回去
            bubbleUi.value = bubbleUi.value.copy(idleDimmed = false)
            resetIdleTimer()
            if (isPanelShowing) dismissPanelToBubble("bubble_tap") else showPanel()
            return
        }
        // B1 修复：正常态点击也复位 idleDimmed
        bubbleUi.value = bubbleUi.value.copy(idleDimmed = false)
        resetIdleTimer()
        // 点击小球 → 直接出现悬浮窗（完整展示）
        if (isPanelShowing) dismissPanelToBubble("bubble_tap") else showPanel()
    }

    // ═══════════ 拖拽与边缘吸附 ═══════════

    private fun onBubbleDrag(dx: Float, dy: Float) {
        // 半隐藏态拖拽：先回弹，再继续拖
        if (bubbleHidden) showBubbleFromEdge()
        // LB-LIFE-02：用户开始新拖动时立即取消旧吸边动画，防旧 target 被提交
        cancelBubbleAnimation()
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

        // LB-LIFE-02：先取消旧动画，再读取当前位置作为新动画起点
        cancelBubbleAnimation()

        val startX = p.x
        val startY = p.y
        if (startX == targetX && startY == targetY) {
            onDone?.invoke(targetX, targetY)
            return
        }

        val animator = ValueAnimator.ofFloat(0f, 1f)
        var cancelled = false
        animator.duration = if (reducedMotion) 0L else AppConfig.BUBBLE_SNAP_MS.toLong()
        animator.interpolator = FastOutSlowInInterpolator()
        animator.addUpdateListener {
            val f = it.animatedValue as Float
            p.x = startX + ((targetX - startX) * f).toInt()
            p.y = startY + ((targetY - startY) * f).toInt()
            runCatching { wm.updateViewLayout(cv, p) }
            if (isPanelShowing) repositionPanel()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }
            override fun onAnimationEnd(animation: Animator) {
                // LB-LIFE-02：取消导致的 onAnimationEnd 不允许提交旧 target
                if (cancelled) return
                // 实例身份保护：避免旧 animation callback 清掉后来创建的新 animation 引用
                if (bubbleAnim === animation) {
                    bubbleAnim = null
                }
                p.x = targetX
                p.y = targetY
                runCatching { wm.updateViewLayout(cv, p) }
                onDone?.invoke(targetX, targetY)
            }
        })
        bubbleAnim = animator
        animator.start()
    }

    /** LB-LIFE-02：统一取消吸边动画——先清引用再 cancel，防 listener 操作已失效引用 */
    private fun cancelBubbleAnimation() {
        val anim = bubbleAnim ?: return
        bubbleAnim = null
        anim.cancel()
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
            // B2 修复：删掉半隐藏阶段（8s 滑出侧边只露 12dp）。
            // 半隐藏后可点区域只剩 12dp → 经常点空"不灵"，且与捕获 bug 无因果关系。
            // 保留 4s 半透明降遮挡即可。
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
        // 强制 PASSIVE：确保不存在上次 EDITING 状态泄漏（打开即不抢焦点）
        setPanelFocusMode(PanelFocusMode.PASSIVE, reason = "show_panel")
        // 读屏播报（固定文案，不拼用户内容）；不主动 requestFocus——打开 Panel ≠ 开始输入
        cv.post {
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

    // ═══════════ LB-LIFE-01: 输入框焦点所有权 ═══════════

    /** 输入意图：用户触碰了某个输入框 → 立即接管 activeInputId 并进入编辑态 */
    private fun onPanelInputIntent(inputId: String) {
        activeInputId = inputId
        enterPanelEditing("input_intent:$inputId")
    }

    /**
     * 焦点变化：只有当前 activeInputId 的失焦才允许退出 EDITING。
     * 如果 activeInputId 已经被新输入框接管，则忽略旧输入框的 blur。
     */
    private fun onPanelInputFocusChanged(inputId: String, focused: Boolean) {
        if (focused) {
            activeInputId = inputId
            return
        }
        if (activeInputId == inputId) {
            activeInputId = null
            exitPanelEditing("input_blur:$inputId")
        }
    }

    private fun hidePanel() {
        // BUG 修复：防重入——退出动画期间重复触发会堆积动画导致卡顿
        if (isPanelHiding) return
        isPanelShowing = false
        val cv = composeView ?: return
        isPanelHiding = true

        // 兜底焦点清理：确保 hidePanel 结束后 panelFocusMode == PASSIVE（第二道保险）
        releasePanelInput("hide_panel")

        // 淡出动画：150ms alpha 1→0，结束后才真正隐藏
        // 焦点释放在动画开始之前已完成（上方 releasePanelInput）
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
        releasePanelInput("service_destroy")
        panelFocusMode = PanelFocusMode.PASSIVE
        clearComposeFocusCallback = null
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

        // 默认 PASSIVE：Panel 可见但不抢焦点，不阻止底层 App 交互。
        // FLAG_NOT_FOCUSABLE: Panel 默认不抢宿主 App 输入焦点
        // FLAG_NOT_TOUCH_MODAL: Panel 外部触摸继续交给底层 App
        // FLAG_WATCH_OUTSIDE_TOUCH: 用户点 Panel 外部时收到 ACTION_OUTSIDE → 收起
        val flags = panelFlags(PanelFocusMode.PASSIVE)

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

            // ACTION_OUTSIDE 外点监听：用户点击 Panel 窗口外 →
            // 不关 Panel（用户需边看回复边操作宿主 App），只退出编辑态：
            // 清 Compose 焦点 → 隐藏 IME → Window 恢复 NOT_FOCUSABLE
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (panelFocusMode == PanelFocusMode.EDITING) {
                        releasePanelInput("outside_touch")
                    }
                    true
                } else {
                    false
                }
            }

            setContent {
                LoveBrainTheme {
                    val overlayToolbar = rememberOverlayTextToolbar()
                    OverlayTextToolbarHost(toolbar = overlayToolbar) {
                        LoveBrainPanelScreen(
                            viewModel = viewModel,
                            onInputFocusChange = { inputId, focused ->
                                onPanelInputFocusChanged(inputId, focused)
                            },
                            onInputIntent = { inputId ->
                                onPanelInputIntent(inputId)
                            },
                            onClearComposeFocus = { callback ->
                                // 注册 Compose 焦点清理回调，releasePanelInput 时调用
                                clearComposeFocusCallback = callback
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
                            // 头部收起按钮 → 统一走 dismissPanelToBubble
                            onCollapse = { dismissPanelToBubble("collapse_button") }
                        )
                    }
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

    // ═══════════ RA-01: 前台服务通知 ═══════════

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            "LoveBrain 悬浮助手",
            NotificationManager.IMPORTANCE_LOW  // 生命周期通知，不响铃、不震动、不 badge
        ).apply {
            description = "悬浮助手运行状态通知"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildOverlayNotification(): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("LoveBrain 悬浮助手正在运行")
            .setContentText("悬浮球已开启，点此返回设置")
            .setContentIntent(contentIntent)
            .addAction(0, "停止", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
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
