package com.lovebrain.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.lovebrain.app.AppConfig
import com.lovebrain.app.ui.bubble.BubbleUiState
import com.lovebrain.app.ui.bubble.FloatingBubble
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.util.L

/** 悬浮球窗口 flags：不抢焦点 + 外部触摸继续交给底层 App（球永远不 EDITING） */
internal fun bubbleWindowFlags(): Int =
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

/**
 * 吸附 / 回弹的目标位：横向贴最近的屏幕边缘（留 BUBBLE_EDGE_MARGIN 白，
 * 近乎贴边又不被系统手势区遮挡），纵向只 clamp 在安全区内、不强制吸附。
 */
internal fun bubbleDockTarget(
    x: Int,
    y: Int,
    screenW: Int,
    screenH: Int,
    mainSize: Int,
    dp: (Int) -> Int
): Pair<Int, Int> {
    val cx = x + mainSize / 2
    val targetX = if (cx < screenW / 2) dp(AppConfig.BUBBLE_EDGE_MARGIN)
    else screenW - mainSize - dp(AppConfig.BUBBLE_EDGE_MARGIN)
    return targetX to y.coerceIn(dp(48), screenH - mainSize - dp(32))
}

/** 拖拽每一帧：整颗球不许离开屏幕 */
internal fun bubbleInsideScreen(
    x: Int,
    y: Int,
    screenW: Int,
    screenH: Int,
    mainSize: Int
): Pair<Int, Int> =
    x.coerceIn(0, (screenW - mainSize).coerceAtLeast(0)) to
        y.coerceIn(0, (screenH - mainSize).coerceAtLeast(0))

/** 从临时隐藏恢复：位置超屏时校正回安全区（顶避开状态栏、底避开导航条） */
internal fun bubbleCorrectedOrigin(
    x: Int,
    y: Int,
    screenW: Int,
    screenH: Int,
    mainSize: Int,
    dp: (Int) -> Int
): Pair<Int, Int> =
    x.coerceIn(0, (screenW - mainSize).coerceAtLeast(0)) to
        y.coerceIn(dp(48), (screenH - mainSize - dp(32)).coerceAtLeast(dp(48)))

/**
 * 悬浮球那一扇 overlay 窗口。
 *
 * 变化理由只有一条：**球自己**——入场位置、单击开合、拖拽与吸边动画、
 * 闲置降透明、未读角标、半隐藏回弹。
 * 面板怎么挂、焦点抢不抢、通知写什么都不在这里。
 *
 * 这里没有协程：闲置计时的账本只在 Service 那一份里，球只报告"用户动过我"和"到点了"。
 *
 * ═══════════ 气泡（ComposeView 容器：纯主球） ═════════
 * 单击主球 → 直接展示完整悬浮窗；
 * 每次启动小球固定在页面左上方，不记忆位置/状态。
 * 手势在 Compose 内部处理（点击/拖拽判定 + 20dp 阈值），状态由 bubbleUi 持有。
 */
internal class OverlayBubbleWindow(
    private val host: OverlayWindowHost,
    /** 单击主球 = 开 / 收面板；球不知道也不该知道 Panel 现在的状态，交给 Service 判 */
    private val onTogglePanel: () -> Unit,
    private val isPanelShowing: () -> Boolean,
    /** 球每动一次，Panel 的贴边定位都要重算 */
    private val onBubbleMoved: () -> Unit,
    /** 任何用户交互都要重启闲置计时（延迟与 Job 都在 Service） */
    private val onUserInteraction: () -> Unit
) {

    /** 悬浮球 UI 状态（Compose 只读渲染，Service 侧更新） */
    private val bubbleUi = mutableStateOf(BubbleUiState())

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var bubbleAnim: ValueAnimator? = null   // ：持有引用，onDestroy 取消防泄漏

    /** 侧边半隐藏状态（ 已禁用半隐藏阶段，此字段恒为 false，相关分支不可达，保留待后续清理） */
    private var bubbleHidden = false

    val isPresent: Boolean get() = bubbleView != null

    /** 面板贴边定位要读球的位置；球还没建时交出 null，由 panelPositionFor 回落默认起点 */
    fun origin(): Pair<Int, Int>? = bubbleParams?.let { it.x to it.y }

    // ═══════════ 建窗 / 拆窗 ═══════════

    /**
     * 挂上悬浮球。每次启动都固定在页面左上方，不恢复上次位置。
     * @return 挂载结果；ALREADY_ATTACHED = 球已在，本次什么都没做
     */
    fun attach(): OverlayAttach {
        if (bubbleView != null) return OverlayAttach.ALREADY_ATTACHED
        val size = host.dp(AppConfig.BUBBLE_SIZE)

        val initX = host.dp(AppConfig.BUBBLE_EDGE_MARGIN)
        val initY = host.dp(48)   // 避开状态栏

        val params = host.overlayParams(size, size, bubbleWindowFlags()).apply {
            x = initX
            y = initY
        }
        bubbleParams = params

        val cv = host.newComposeView().apply {
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
        return host.attach(cv, params, "bubble")
    }

    fun remove() {
        bubbleView?.let { host.detach(it) }
        bubbleView = null
    }

    /** LB-LIFE-02：统一取消吸边动画——先清引用再 cancel，防 listener 操作已失效引用 */
    fun cancelAnimation() {
        val anim = bubbleAnim ?: return
        bubbleAnim = null
        anim.cancel()
    }

    // ═══════════ 状态对外：角标与可见性（Service 的窗口状态机要用） ═══════════

    /** 捕获消息已入库：亮红点；半隐藏时先回弹，别让用户看不见 */
    fun markCaptured() {
        //  修复：只在消息真正入库时才亮红点（去重丢弃时不亮）——判"要不要亮"归调用方
        bubbleUi.value = bubbleUi.value.copy(badgeCount = bubbleUi.value.badgeCount + 1)
        if (bubbleHidden) showBubbleFromEdge()
    }

    /** 打开面板 → 未读角标清零 */
    fun clearBadge() {
        bubbleUi.value = bubbleUi.value.copy(badgeCount = 0)
    }

    /** 面板显示时球让位（GONE，不 remove：恢复时还要用同一个 view 与位置） */
    fun hideForPanel() {
        bubbleView?.visibility = View.GONE
    }

    /** 面板收起后球重新可见（：读屏播报由调用方决定时机，这里只负责亮出来） */
    fun makeVisible() {
        bubbleView?.visibility = View.VISIBLE
    }

    /** 读屏播报——气泡重新可见后告知面板已关闭（固定文案） */
    fun announcePanelClosed() {
        bubbleView?.announceForAccessibility("军师面板已关闭")
    }

    /** 临时隐藏：只改可见性，保留视图与位置 */
    fun hideForTempHidden() {
        bubbleView?.let { it.visibility = View.GONE }
    }

    /** 从临时隐藏恢复到球：校正超屏位置并亮出来 */
    fun restoreAfterTempHidden() {
        val bv = bubbleView
        val p = bubbleParams
        if (bv != null && p != null) {
            val (x, y) = bubbleCorrectedOrigin(
                p.x, p.y, host.screenWidth(), host.screenHeight(), host.dp(AppConfig.BUBBLE_SIZE), host::dp
            )
            p.x = x
            p.y = y
            host.updateLayout(bv, p)
        }
        bubbleView?.visibility = View.VISIBLE
    }

    /** 闲置到点：降透明度减遮挡（悬浮窗展示中不降，保证用户看得见自己在用什么） */
    fun onIdleElapsed() {
        if (!isPanelShowing()) {
            bubbleUi.value = bubbleUi.value.copy(idleDimmed = true)
        }
    }

    // ═══════════ 小球点击 → 完整悬浮窗：点击小球直接出现悬浮窗、完整展示 ═══════════

    private fun onBubbleTap() {
        L.w("bubble: onBubbleTap bubbleHidden=$bubbleHidden panel=${isPanelShowing()}")
        // 半隐藏态：一次点击 = 回弹 + 直接开面板（修复需求12：靠墙时需点两下才能开）
        if (bubbleHidden) {
            // 瞬时回弹（不启动动画）：Panel 的定位紧接着要读 bubbleParams，
            // 如果用动画，bubbleParams 此时还在隐藏位置 → 面板位置算错（出现在屏幕中间）
            showBubbleFromEdge(animate = false)
            //  修复：点击唤醒必须复位 idleDimmed，否则松手后球立刻又暗回去
            bubbleUi.value = bubbleUi.value.copy(idleDimmed = false)
            onUserInteraction()
            onTogglePanel()
            return
        }
        //  修复：正常态点击也复位 idleDimmed
        bubbleUi.value = bubbleUi.value.copy(idleDimmed = false)
        onUserInteraction()
        // 点击小球 → 直接出现悬浮窗（完整展示）
        onTogglePanel()
    }

    // ═══════════ 拖拽与边缘吸附 ═══════════

    private fun onBubbleDrag(dx: Float, dy: Float) {
        // 半隐藏态拖拽：先回弹，再继续拖
        if (bubbleHidden) showBubbleFromEdge()
        // LB-LIFE-02：用户开始新拖动时立即取消旧吸边动画，防旧 target 被提交
        cancelAnimation()
        val cv = bubbleView ?: return
        val p = bubbleParams ?: return
        val screenW = host.screenWidth()
        val screenH = host.screenHeight()
        val mainSize = host.dp(AppConfig.BUBBLE_SIZE)

        val (x, y) = bubbleInsideScreen(p.x + dx.toInt(), p.y + dy.toInt(), screenW, screenH, mainSize)
        p.x = x
        p.y = y
        host.updateLayout(cv, p)
        if (!bubbleUi.value.dragging) {
            bubbleUi.value = bubbleUi.value.copy(dragging = true, idleDimmed = false)
        }
        if (isPanelShowing()) onBubbleMoved()
        onUserInteraction()
    }

    /** 松手后吸附到最近的屏幕边缘（保留平滑滑向动画；：去掉吸附震动） */
    private fun snapBubbleToEdge() {
        bubbleUi.value = bubbleUi.value.copy(dragging = false)
        bubbleHidden = false
        if (bubbleView == null) return
        val p = bubbleParams ?: return
        val mainSize = host.dp(AppConfig.BUBBLE_SIZE)

        // 同步红点偏移方向（朝屏幕中心侧）
        bubbleUi.value = bubbleUi.value.copy(snapLeft = p.x + mainSize / 2 < host.screenWidth() / 2)
        val (targetX, targetY) = bubbleDockTarget(
            p.x, p.y, host.screenWidth(), host.screenHeight(), mainSize, host::dp
        )
        animateBubbleTo(targetX, targetY) { _, _ ->
            // 去掉触觉震动；不再持久化位置，每次启动固定左上方
            onUserInteraction()
        }
    }

    /** 悬浮球平滑位移动画（250ms FastOutSlowIn，驱动 wm.updateViewLayout） */
    private fun animateBubbleTo(targetX: Int, targetY: Int, onDone: ((Int, Int) -> Unit)? = null) {
        val cv = bubbleView ?: return
        val p = bubbleParams ?: return

        // LB-LIFE-02：先取消旧动画，再读取当前位置作为新动画起点
        cancelAnimation()

        val startX = p.x
        val startY = p.y
        if (startX == targetX && startY == targetY) {
            onDone?.invoke(targetX, targetY)
            return
        }

        val animator = ValueAnimator.ofFloat(0f, 1f)
        var cancelled = false
        animator.duration = if (host.animatorDisabled()) 0L else AppConfig.BUBBLE_SNAP_MS.toLong()
        animator.interpolator = FastOutSlowInInterpolator()
        animator.addUpdateListener {
            val f = it.animatedValue as Float
            p.x = startX + ((targetX - startX) * f).toInt()
            p.y = startY + ((targetY - startY) * f).toInt()
            host.updateLayout(cv, p)
            if (isPanelShowing()) onBubbleMoved()
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
                host.updateLayout(cv, p)
                onDone?.invoke(targetX, targetY)
            }
        })
        bubbleAnim = animator
        animator.start()
    }

    // ═══════════ 闲置计时：4s 半透明降遮挡；8s 滑出半隐藏 ═══════════
    // 阶段1：4s 无交互 → 半透明（AssistiveTouch）；阶段2：再 4s → 滑出侧边只露 12dp（QQ 悬挂）
    //  修复：删掉半隐藏阶段（8s 滑出侧边只露 12dp）。
    // 半隐藏后可点区域只剩 12dp → 经常点空"不灵"，且与捕获 bug 无因果关系。
    // 保留 4s 半透明降遮挡即可；因此 hideBubbleToEdge 已无调用方（回弹路径仍被 markCaptured
    // 与 onBubbleTap 经由，只是 bubbleHidden 恒为 false 让它们进不去），一并留着待清理。

    /** 滑出半隐藏：容器滑向边缘只露 BUBBLE_HIDE_EDGE_DP，触摸露边即回弹 */
    private fun hideBubbleToEdge() {
        if (bubbleHidden) return
        if (bubbleUi.value.dragging) return
        if (isPanelShowing()) return  // 悬浮窗展示中不隐藏（保证可见性）
        if (bubbleUi.value.badgeCount > 0) return  // 有未读角标时不隐藏（保证可见性）
        if (bubbleView == null) return
        val p = bubbleParams ?: return
        val screenW = host.screenWidth()
        val mainSize = host.dp(AppConfig.BUBBLE_SIZE)
        val edge = host.dp(AppConfig.BUBBLE_HIDE_EDGE_DP)

        val cx = p.x + mainSize / 2
        val targetX = if (cx < screenW / 2) -(mainSize - edge) else screenW - edge
        bubbleHidden = true
        // 露边呼吸开启，让用户知道球还在（不弹窗不提示）
        // 半隐藏是临时状态，不保存位置、不触发触觉
        animateBubbleTo(targetX, p.y)
    }

    /** 回弹：从半隐藏态滑回吸附位置
     *  @param animate true=播放滑动动画（拖拽/未读回弹等场景）；false=瞬时设置位置
     *               （onBubbleTap 场景：Panel 紧接着要读 bubbleParams，不能等动画）
     */
    private fun showBubbleFromEdge(animate: Boolean = true) {
        if (!bubbleHidden) return
        bubbleHidden = false
        bubbleUi.value = bubbleUi.value.copy(edgeBreathing = false)
        val cv = bubbleView ?: return
        val p = bubbleParams ?: return
        val (targetX, targetY) = bubbleDockTarget(
            p.x, p.y, host.screenWidth(), host.screenHeight(),
            host.dp(AppConfig.BUBBLE_SIZE), host::dp
        )
        if (animate) {
            animateBubbleTo(targetX, targetY)
        } else {
            // 瞬时设置位置：球体随后被 Panel 设为 GONE，视觉跳变不可见
            p.x = targetX
            p.y = targetY
            host.updateLayout(cv, p)
        }
        onUserInteraction()
    }
}
