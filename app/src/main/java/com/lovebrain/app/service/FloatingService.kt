package com.lovebrain.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PersistableBundle
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.lovebrain.app.AppConfig
import com.lovebrain.app.R
import com.lovebrain.app.data.EventBus
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.SetupActivity
import com.lovebrain.app.ui.common.OverlayTextToolbarHost
import com.lovebrain.app.ui.common.rememberOverlayTextToolbar
import com.lovebrain.app.ui.panel.LoveBrainPanelScreen
import com.lovebrain.app.util.L
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 军师悬浮窗服务 v5（Koin + EventBus 版）。
 * 气泡 + ComposeView 面板，面板内部全部由 Jetpack Compose 渲染。
 *
 * 改进：
 * - 依赖通过 Koin 注入，不再手动 new
 * - 服务间通信通过 EventBus（SharedFlow），不再用静态方法
 *
 * ## 这一屏的所有权（一个变化理由一个所有者）
 * - [OverlayWindowHost]：窗口层样板（权限、挂载、透明背景、dp）
 * - [OverlayBubbleWindow]：悬浮球自己（手势、吸边、闲置、角标）
 * - [OverlayPanelWindow]：面板这一扇窗（建窗、贴边定位、缩放、淡出、拆窗）
 * - [PanelInputFocusOwner]：Panel 抢不抢输入焦点、哪个输入框持有焦点
 *
 * 留在这里的只有三样：**Android 组件生命周期**（含前台通知与防泄漏 teardown）、
 * **窗口状态机**（含临时隐藏/恢复）、**服务生命周期内唯一的协程账本**（[scope]）。
 * 四个协作者一律不许持有 CoroutineScope / Job：所有 delay 与 collect 都从 [scope] 出，
 * 取消因此只有一本账——`onDestroy` 里 [scope] 一 cancel，挂出去的挂起点全部一起结束。
 * 前台 AI 生成任务的账本另在 coordinator 那份，Service 不另开。
 */
class FloatingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    /** 窗口实际状态：供 UI 订阅，不独立 remember 布尔值 */
    enum class WindowState {
        STOPPED,          // 服务未运行
        VISIBLE_BUBBLE,   // 悬浮球可见
        VISIBLE_PANEL,    // 面板可见
        TEMP_HIDDEN       // 临时隐藏（面板和球都不可见）
    }

    companion object {
        private const val OVERLAY_CHANNEL_ID = "lovebrain_overlay"
        private const val OVERLAY_NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.lovebrain.app.action.STOP_FLOATING"
        private const val ACTION_TEMP_HIDE = "com.lovebrain.app.action.TEMP_HIDE"
        private const val ACTION_RESTORE = "com.lovebrain.app.action.RESTORE"

        /** 供外部查询服务是否存活 */
        @Volatile
        var instance: FloatingService? = null
            private set

        /** 当前窗口状态（UI 通过此值判断显示隐藏/恢复按钮） */
        @Volatile
        var windowState: WindowState = WindowState.STOPPED
            private set

        /** 阻断D修复：Compose 可观察的窗口状态 StateFlow */
        private val _windowStateFlow = MutableStateFlow(WindowState.STOPPED)
        val windowStateFlow: StateFlow<WindowState> = _windowStateFlow.asStateFlow()

        /** 临时隐藏前的展示形态，恢复时还原 */
        @Volatile
        var preHiddenState: WindowState = WindowState.VISIBLE_BUBBLE
            private set

        /** 统一更新 windowState，同步 volatile 和 StateFlow */
        fun setWindowState(state: WindowState) {
            windowState = state
            _windowStateFlow.value = state
        }
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

    /**
     * 服务生命周期内唯一的协程账本。
     * 只有这里可以 launch；拆出去的窗口与焦点类一律不持有 scope / Job。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var host: OverlayWindowHost
    private lateinit var bubble: OverlayBubbleWindow
    private lateinit var panel: OverlayPanelWindow

    /** 闲置计时器（4s 半透明 → 8s 滑出半隐藏）；随 scope 一起取消，不留第二本账 */
    private var idleJob: Job? = null

    /** 洪峰去重：仅拦 ≤1.5s 内同文本的重复事件（同一次手势的系统连发），不拦用户主动重捕 */
    private var lastClipText: String? = null
    private var lastClipTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 停止 Action —— 用户从通知点「停止」时直接 stopSelf
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 临时隐藏：面板和球都隐藏，不留触摸拦截层
        if (intent?.action == ACTION_TEMP_HIDE) {
            tempHide()
            return START_NOT_STICKY
        }
        // 恢复：从临时隐藏恢复到隐藏前形态
        if (intent?.action == ACTION_RESTORE) {
            restoreFromTempHidden()
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

        // 尽快进入前台服务状态，不等 IO / AI 初始化
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

        host = OverlayWindowHost(this, this)

        // 面板尺寸持久化：恢复上次用户调整的面板大小（调研：NN/G 10 Heuristics #3 User Control and Freedom）
        val savedW = securePrefs.panelWidth
        val savedH = securePrefs.panelHeight
        panel = OverlayPanelWindow(
            host = host,
            bubbleOrigin = { bubble.origin() },
            initialWidth = if (savedW > 0) savedW else host.dp(AppConfig.PANEL_DEFAULT_W),
            initialHeight = if (savedH > 0) savedH else host.dp(AppConfig.PANEL_DEFAULT_H),
            persistSize = { w, h ->
                //  拖拽结束才写 SecurePrefs，避免每帧 onDrag 都触发磁盘写入
                securePrefs.panelWidth = w
                securePrefs.panelHeight = h
            },
            content = { PanelContent() }
        )
        bubble = OverlayBubbleWindow(
            host = host,
            // 单击主球 → 直接出现悬浮窗（完整展示）；已开着就收起
            onTogglePanel = {
                if (panel.isShowing) dismissPanelToBubble("bubble_tap") else showPanel()
            },
            isPanelShowing = { panel.isShowing },
            onBubbleMoved = { panel.reposition() },
            // 球每次被摸一下都要重启闲置计时；计时器本身留在这里（唯一那本账）
            onUserInteraction = { resetIdleTimer() }
        )

        // 订阅 EventBus：接收无障碍服务捕获的消息
        scope.launch {
            EventBus.capturedMessages.collect { event ->
                // CAP-01：严格 session 边界——早于 sessionStart 的一律是上一 session 的 replay，丢弃
                if (event.ts < sessionStart) return@collect
                val stored = addClipIfNew(event.text, viewModel.currentRole.value)
                // A4 修复：只在消息真正入库时才亮红点（去重丢弃时不亮）
                if (stored && bubble.isPresent) {
                    bubble.markCaptured()
                }
            }
        }

        // 订阅 EventBus：App 首页功能卡片（今日锦囊/谈心模式/五维向量）直达面板对应功能
        scope.launch {
            EventBus.panelRequest.collect { req ->
                if (req != null) {
                    EventBus.consumePanelRequest()
                    L.w("panel request: mode=${req.mode} showPlan=${req.showPlan}")
                    if (!panel.isShowing) showPanel()
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
        setWindowState(WindowState.STOPPED)
        bubble.cancelAnimation()   // E3：防动画回调持有已销毁 Service
        panel.cancelExitAnimation()
        bubble.remove()
        panel.destroy()
        viewModel.dispose()      // 显式取消 VM 生成协程，防泄漏
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        // 销毁时确保前台状态结束
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ═══════════ 悬浮球：装配层只剩"挂上 / 摘掉"两个动作 ═══════════

    private fun showBubble() {
        when (bubble.attach()) {
            OverlayAttach.ATTACHED -> {
                resetIdleTimer()   // 启动闲置半透明计时
                setWindowState(WindowState.VISIBLE_BUBBLE)
            }
            // 权限被回收 / WindowManager 拒挂：停服。view 引用留着，由 onDestroy 统一摘掉。
            OverlayAttach.PERMISSION_REVOKED, OverlayAttach.FAILED -> stopSelf()
            OverlayAttach.ALREADY_ATTACHED -> Unit
        }
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

    // ═══════════ 临时隐藏/恢复（面板和球都隐藏，保留全部状态） ═══════════

    /**
     * 临时隐藏：同时隐藏面板和悬浮球，不留触摸拦截层。
     * 当前草稿、消息、回复、点赞、编辑状态和本轮上下文保留。
     * 与"收起成球"（dismissPanelToBubble）和"停止服务"（stopSelf）不同。
     *
     * 隐藏先释放输入焦点、关闭键盘，再隐藏/移除两个窗口。
     * 不调用 stopSelf，不清 ViewModel，不取消正在运行的正常请求。
     */
    private fun tempHide() {
        // 阻断D修复：幂等 guard——已经临时隐藏时不重复覆盖 preHiddenState
        if (windowState == WindowState.TEMP_HIDDEN) return

        // 记录隐藏前展示形态
        preHiddenState = if (panel.isShowing) WindowState.VISIBLE_PANEL else WindowState.VISIBLE_BUBBLE

        // 释放输入焦点、关闭键盘
        panel.releaseInput("temp_hide")

        // 先设置 TEMP_HIDDEN 状态，再取消动画——
        // 这样即使 cancel() 同步触发 onAnimationEnd，guard 也已看到 TEMP_HIDDEN，
        // 不会把 bubbleView.visibility 设回 VISIBLE。
        setWindowState(WindowState.TEMP_HIDDEN)

        // 隐藏面板
        panel.hideInstantly()

        // 隐藏悬浮球（不移除视图，只改 visibility，恢复时还原位置）
        bubble.hideForTempHidden()

        // 取消闲置计时器（隐藏后不需要呼吸/半隐藏动画）
        idleJob?.cancel()

        updateNotification()
        L.w("FloatingService: temp hide applied")
    }

    /**
     * 从临时隐藏恢复到隐藏前形态。
     * 不自动抢宿主输入焦点。
     * 位置超屏时校正。
     */
    private fun restoreFromTempHidden() {
        if (windowState != WindowState.TEMP_HIDDEN) return

        when (preHiddenState) {
            WindowState.VISIBLE_PANEL -> {
                // 恢复面板
                showPanel()
                setWindowState(WindowState.VISIBLE_PANEL)
            }
            WindowState.VISIBLE_BUBBLE -> {
                // 恢复悬浮球，校正位置
                bubble.restoreAfterTempHidden()
                resetIdleTimer()
                setWindowState(WindowState.VISIBLE_BUBBLE)
            }
            else -> {
                // 默认恢复到球
                bubble.makeVisible()
                resetIdleTimer()
                setWindowState(WindowState.VISIBLE_BUBBLE)
            }
        }
        updateNotification()
        L.w("FloatingService: restored from temp hidden to $windowState")
    }

    // ═══════════ 面板：窗口动作交给 OverlayPanelWindow，这里只编排状态与副作用 ═══════════

    private fun showPanel() {
        val attached = panel.ensureCreated()
        if (attached == OverlayAttach.PERMISSION_REVOKED || attached == OverlayAttach.FAILED) {
            stopSelf()
            return
        }
        // 打开面板 → 未读角标清零
        bubble.clearBadge()
        setWindowState(WindowState.VISIBLE_PANEL)
        // 贴边定位 / VISIBLE / 强制 PASSIVE / 读屏播报都在窗口里做
        if (!panel.present()) return
        bubble.hideForPanel()

        scope.launch(Dispatchers.IO) { viewModel.refreshKnowledgeBases() }
        //  死锁修复：composition 跨面板隐藏/显示存活，LaunchedEffect(Unit) 只执行一次；
        // showPanel 每次开面板必经，在此强制重读工单三态（配置后 isProviderReady 即时生效）
        viewModel.refreshTicketState()
    }

    /**
     * 淡出收起面板。
     * 球要不要重新可见由窗口状态决定——TEMP_HIDDEN 时亮回球就是"状态说隐藏、屏幕上有球"。
     */
    private fun hidePanel() {
        panel.fadeOut(revealBubble = {
            if (windowState != WindowState.TEMP_HIDDEN) {
                bubble.makeVisible()
                setWindowState(WindowState.VISIBLE_BUBBLE)
                // 读屏播报——气泡重新可见后告知面板已关闭（固定文案）
                bubble.announcePanelClosed()
            }
        })
    }

    /**
     * 统一 Panel 收起入口：收起按钮 / 模式切换关闭 / 点球都走此方法。
     * 注意：ACTION_OUTSIDE 不走此方法（只释放输入不关 Panel）。
     * 顺序：释放输入 → hidePanel（淡出动画）→ Bubble 恢复
     */
    private fun dismissPanelToBubble(reason: String) {
        L.w("panel dismiss reason=$reason")
        panel.releaseInput("dismiss_$reason")
        hidePanel()
    }

    /**
     * 面板内容装配：真实生产 Panel + overlay 文字工具栏宿主 + 输入焦点/几何回调。
     * 浮层一律走统一 host（OverlayTextToolbarHost 那一套），不往页面下方直插。
     */
    @Composable
    private fun PanelContent() {
        val overlayToolbar = rememberOverlayTextToolbar()
        OverlayTextToolbarHost(toolbar = overlayToolbar) {
            LoveBrainPanelScreen(
                viewModel = viewModel,
                onInputFocusChange = { inputId, focused ->
                    panel.onInputFocusChanged(inputId, focused)
                },
                onInputIntent = { inputId ->
                    panel.onInputIntent(inputId)
                },
                onClearComposeFocus = { callback ->
                    // 注册 Compose 焦点清理回调，releaseInput 时调用
                    panel.registerClearFocusCallback(callback)
                },
                onResize = { newW, newH ->
                    panel.resize(newW, newH)
                },
                onResizeEnd = {
                    //  拖拽松手才写 SecurePrefs，避免每帧 onDrag 都触发磁盘写入
                    panel.commitSize()
                },
                onMove = { dx, dy ->
                    panel.move(dx, dy)
                },
                onCopy = { text ->
                    if (text.isNotEmpty()) {
                        copyToClipboard(text)
                    }
                },
                // 未配置供应商引导——打开设置页（新任务栈，不干扰宿主 App）
                onOpenSettings = {
                    startActivity(Intent(this@FloatingService, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                // 头部收起按钮 → 统一走 dismissPanelToBubble
                onCollapse = { dismissPanelToBubble("collapse_button") }
            )
        }
    }

    // ═══════════ 闲置计时：4s 半透明降遮挡；8s 滑出半隐藏 ═══════════
    // 阶段1：4s 无交互 → 半透明（AssistiveTouch）；阶段2：再 4s → 滑出侧边只露 12dp（QQ 悬挂）

    /**
     * 重启闲置计时。
     *
     * 这是本文件之外所有协作者都不能自己 launch 的原因：service 包里只有这一处 `delay`，
     * 计时器就只有一本账——`tempHide` cancel 它、`scope.cancel()` 兜住它，
     * 不存在"服务已销毁但计时器还在把球调暗"的那条路。
     */
    private fun resetIdleTimer() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(AppConfig.BUBBLE_IDLE_DIM_MS)
            bubble.onIdleElapsed()
            // B2 修复：删掉半隐藏阶段（8s 滑出侧边只露 12dp）。
            // 半隐藏后可点区域只剩 12dp → 经常点空"不灵"，且与捕获 bug 无因果关系。
            // 保留 4s 半透明降遮挡即可。
        }
    }

    // ═══════════ 工具 ═══════════

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("军师回复", text)
        // RB-04：标记为敏感内容，隐藏 Android 13+ 系统剪贴板浮层预览
        val extras = PersistableBundle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clip.description.extras = extras
        cm.setPrimaryClip(clip)
        // 复制成功可见反馈（固定文案，不含用户内容）
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        // A4 修复：不再写 recentClips——复制的是军师回复，不是捕获的消息，不应影响捕获去重
    }

    // ═══════════ 前台服务通知 ═══════════

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

    private fun buildOverlayNotification(statusText: String? = null): android.app.Notification {
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
        // 临时隐藏时增加"恢复"动作
        val builder = NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("LoveBrain 悬浮助手正在运行")
            .setContentText(statusText ?: "悬浮球已开启，点此返回设置")
            .setContentIntent(contentIntent)
            .addAction(0, "停止", stopIntent)
            .setOngoing(true)
            .setSilent(true)
        if (windowState == WindowState.TEMP_HIDDEN) {
            val restoreIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, FloatingService::class.java).setAction(ACTION_RESTORE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "恢复", restoreIntent)
        }
        return builder.build()
    }

    /** 更新通知文案以同步真实状态 */
    private fun updateNotification() {
        val text = when (windowState) {
            // 「已暂时隐藏」这句话首页状态卡也在说——旧代码两处各写一遍且用词不同
            // （"点击恢复" / "点此恢复"），现在共用 status_hidden_desc 一条资源。
            WindowState.TEMP_HIDDEN -> getString(R.string.status_hidden_desc)
            WindowState.VISIBLE_BUBBLE -> "悬浮球已开启，点此返回设置"
            WindowState.VISIBLE_PANEL -> "军师面板已打开，点此返回设置"
            WindowState.STOPPED -> "军师已停止"
        }
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(OVERLAY_NOTIFICATION_ID, buildOverlayNotification(text))
    }
}