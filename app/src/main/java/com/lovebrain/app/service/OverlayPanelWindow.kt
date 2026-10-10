package com.lovebrain.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.lovebrain.app.AppConfig
import com.lovebrain.app.BubbleSizeTier
import com.lovebrain.app.R
import com.lovebrain.app.ui.theme.LoveBrainTheme

/**
 * 面板贴边定位：水平方向永远贴屏幕边缘——球在左半屏→贴左边缘，球在右半屏→贴右边缘。
 *
 * 旧逻辑小面板时把面板放在"球右侧 64dp"，视觉上是屏幕中间；大面板因超宽被挤回左边缘，
 * 造成"只有小面板才出现在屏幕中间"的 bug。面板显示时球已 GONE，无需避重叠。
 *
 * 垂直方向：与球顶部大致对齐，clamp 在安全区（顶留状态栏、底留导航条）。
 *
 * @param bubbleLeft / bubbleTop 球窗口当前位置；球还没建时传 null，回落到球的默认起点
 * @param bubbleSizeDp **当前档位直径**：球的中心是贴边判断的参照点，它必须跟着档位走，
 *        否则 48 / 64 两档下面板会按 56 的半径算中心（书 §1 要求所有读数同源）
 */
internal fun panelPositionFor(
    pw: Int,
    ph: Int,
    screenW: Int,
    screenH: Int,
    bubbleLeft: Int?,
    bubbleTop: Int?,
    bubbleSizeDp: Int,
    dp: (Int) -> Int
): Pair<Int, Int> {
    val mainSize = dp(bubbleSizeDp)
    val bLeft = bubbleLeft ?: dp(AppConfig.BUBBLE_EDGE_MARGIN)
    val bTop = bubbleTop ?: dp(48)
    val bCx = bLeft + mainSize / 2

    val px = if (bCx < screenW / 2) dp(4) else (screenW - pw - dp(4)).coerceAtLeast(dp(4))

    var py = bTop - dp(12)
    if (py + ph > screenH - dp(60)) py = screenH - ph - dp(60)
    if (py < dp(24)) py = dp(24)
    return px to py
}

/** 面板尺寸先 clamp 到屏幕可用区（左右各留 8dp，顶留状态栏 + 底留导航条共 80dp） */
internal fun panelFittedSize(
    panelW: Int,
    panelH: Int,
    screenW: Int,
    screenH: Int,
    dp: (Int) -> Int
): Pair<Int, Int> =
    panelW.coerceAtMost(screenW - dp(8)) to panelH.coerceAtMost(screenH - dp(80))

/** 用户拖出来的尺寸要落在允许的区间里（太小读不清、太大压死宿主 App） */
internal fun panelClampedSize(newWpx: Int, newHpx: Int, dp: (Int) -> Int): Pair<Int, Int> =
    newWpx.coerceIn(dp(AppConfig.PANEL_MIN_W), dp(AppConfig.PANEL_MAX_W)) to
        newHpx.coerceIn(dp(AppConfig.PANEL_MIN_H), dp(AppConfig.PANEL_MAX_H))

/** 拖拽移动后的位置：整块面板不许被推出屏幕 */
internal fun panelMovedOrigin(
    x: Int,
    y: Int,
    dxPx: Float,
    dyPx: Float,
    viewW: Int,
    viewH: Int,
    screenW: Int,
    screenH: Int
): Pair<Int, Int> =
    (x + dxPx.toInt()).coerceIn(0, (screenW - viewW).coerceAtLeast(0)) to
        (y + dyPx.toInt()).coerceIn(0, (screenH - viewH).coerceAtLeast(0))

/**
 * 军师面板那一扇 overlay 窗口。
 *
 * 变化理由只有一条：**这扇窗口本身**——什么时候建、挂什么、贴哪、多宽多高、
 * 淡出与销毁。窗口里画什么由 Service 注入的 content 决定（那是装配），
 * 抢不抢输入焦点归 [PanelInputFocusOwner]，球归 [OverlayBubbleWindow]。
 *
 * 本文件不持有协程，也不直接碰 WindowManager：两者分别经 Service 与 [OverlayWindowHost]。
 */
internal class OverlayPanelWindow(
    private val host: OverlayWindowHost,
    /** Panel 贴边定位跟着球走，所以要知道球现在在哪（球没建时返回 null） */
    private val bubbleOrigin: () -> Pair<Int, Int>?,
    initialWidth: Int,
    initialHeight: Int,
    /** 拖拽松手才写 SecurePrefs：每帧都落盘是旧 bug 的修法 */
    private val persistSize: (Int, Int) -> Unit,
    private val content: @Composable () -> Unit,
    /**
     * **档位读数口子**：面板贴边定位跟着球的中心走，中心由半径算，所以这里必须拿到同一颗档位。
     * 默认参数就是标准档 ⇒ 接线之前与从前逐字同形。
     */
    private val bubbleSizeDp: () -> Int = { BubbleSizeTier.DEFAULT_DP }
) : PanelFocusWindow {

    private var composeView: ComposeView? = null
    private var panelW = initialWidth
    private var panelH = initialHeight
    private var panelExitAnim: ValueAnimator? = null
    private var isPanelHiding = false

    /** 焦点与输入框所有权的唯一持有者；本类只当它的那扇真窗口 */
    private val focus = PanelInputFocusOwner(this)

    /** 面板是否处于展示中（窗口状态机由 Service 驱动，这里只记事实） */
    var isShowing = false
        private set

    // ═══════════ PanelFocusWindow：焦点状态机对窗口的全部需求 ═══════════

    override fun isPresent(): Boolean = composeView != null

    override fun applyFlags(flags: Int): Boolean {
        val cv = composeView ?: return false
        val params = cv.layoutParams as? WindowManager.LayoutParams ?: return false
        params.flags = flags
        return host.updateLayout(cv, params)
    }

    override fun hideIme() {
        composeView?.let { host.hideImeFor(it) }
    }

    // ═══════════ 建窗 / 挂载 ═══════════

    /**
     * 幂等建窗：已存在直接返回。
     * 权限被回收或挂载失败时把 view 引用清掉并交出结果，由 Service 停服。
     */
    fun ensureCreated(): OverlayAttach {
        if (composeView != null) return OverlayAttach.ALREADY_ATTACHED

        val (pw, ph) = fittedSize()
        val (px, py) = positionFor(pw, ph)

        // 默认 PASSIVE：Panel 可见但不抢焦点，不阻止底层 App 交互。
        // FLAG_NOT_FOCUSABLE: Panel 默认不抢宿主 App 输入焦点
        // FLAG_NOT_TOUCH_MODAL: Panel 外部触摸继续交给底层 App
        // FLAG_WATCH_OUTSIDE_TOUCH: 用户点 Panel 外部时收到 ACTION_OUTSIDE → 收起
        val params = host.overlayParams(pw, ph, panelFlagsFor(PanelFocusMode.PASSIVE)).apply {
            x = px
            y = py
            // 悬浮窗美化：面板淡入淡出动画（替代 0=生硬弹出）
            windowAnimations = R.style.PanelWindowAnimation
        }

        val cv = host.newComposeView().apply {
            visibility = View.GONE

            // ACTION_OUTSIDE 外点监听：用户点击 Panel 窗口外 →
            // 不关 Panel（用户需边看回复边操作宿主 App），只退出编辑态：
            // 清 Compose 焦点 → 隐藏 IME → Window 恢复 NOT_FOCUSABLE
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (focus.mode == PanelFocusMode.EDITING) {
                        releaseInput("outside_touch")
                    }
                    true
                } else {
                    false
                }
            }

            setContent {
                LoveBrainTheme {
                    content()
                }
            }
        }

        composeView = cv
        val result = host.attach(cv, params, "panel")
        if (result != OverlayAttach.ATTACHED) {
            composeView = null
        }
        return result
    }

    // ═══════════ 展示 / 收起 ═══════════

    /**
     * 打开这一扇窗：取消退出动画并把**动画**那一条 alpha 复位 → 贴边定位 → VISIBLE → 强制 PASSIVE → 读屏播报。
     *
     * BUG 修复：打开前必须取消退出动画并复位动画透明度，
     * 否则上次淡出残留 alpha=0 → 面板"显示"了但完全透明看不见。
     *
     * 这里复位的只有动画通道（见 [WINDOW_SHOWN_ALPHA]）：用户设的是**背景层**的不透明度，
     * 长在底色自己的 alpha 里，跟这一行无关，也就不会被这一行抹掉。
     *
     * 不主动 requestFocus——打开 Panel ≠ 开始输入。
     */
    fun present(): Boolean {
        val cv = composeView ?: return false
        isShowing = true

        panelExitAnim?.cancel()
        panelExitAnim = null
        isPanelHiding = false
        restoreFadeBaseline(cv)

        val (pw, ph) = fittedSize()
        val (px, py) = positionFor(pw, ph)

        val p = cv.layoutParams as? WindowManager.LayoutParams ?: return false
        p.width = pw
        p.height = ph
        p.x = px
        p.y = py
        host.updateLayout(cv, p)

        cv.visibility = View.VISIBLE
        // 强制 PASSIVE：确保不存在上次 EDITING 状态泄漏（打开即不抢焦点）
        focus.setMode(PanelFocusMode.PASSIVE, "show_panel")
        // 读屏播报（固定文案，不拼用户内容）
        cv.post {
            cv.announceForAccessibility("军师面板已打开")
        }
        return true
    }

    /**
     * 淡出收起：150ms 从"完全显示"降到 0，结束后才真正隐藏。
     * 焦点释放在动画开始之前已完成。
     *
     * 这条动画走的只有 [WINDOW_SHOWN_ALPHA] 那一格窗口强度：面板底色的透明程度是用户设的、
     * 存在颜色自己的 alpha 通道里，动画把整扇窗淡下去再复位，改不到那一位。
     *
     * @param revealBubble 动画结束回调——球是否重新可见由 Service 的窗口状态决定，
     *                     这里不猜（TEMP_HIDDEN 时绝不能把球亮回去）。
     * @return false = 正在收起（重入）或窗口不存在
     */
    fun fadeOut(revealBubble: () -> Unit): Boolean {
        // BUG 修复：防重入——退出动画期间重复触发会堆积动画导致卡顿
        if (isPanelHiding) return false
        isShowing = false
        val cv = composeView ?: return false
        isPanelHiding = true

        // 兜底焦点清理：确保收起结束后 panelFocusMode == PASSIVE（第二道保险）
        focus.releaseInput("hide_panel")

        // 淡出动画：150ms 完全显示→0，结束后才真正隐藏。动的是整扇窗的强度，不是底色。
        panelExitAnim?.cancel()
        panelExitAnim = ValueAnimator.ofFloat(WINDOW_SHOWN_ALPHA, 0f).apply {
            duration = 150L
            addUpdateListener { anim ->
                cv.alpha = anim.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cv.visibility = View.GONE
                    restoreFadeBaseline(cv)   // 复位动画通道，避免下次打开残留透明（与用户设的底色透明度无关）
                    // 旧动画回调必须守卫全部窗口操作，不能只守卫状态变量。
                    // TEMP_HIDDEN 时气泡不应重新可见——否则状态显示隐藏、实际球可见。
                    revealBubble()
                    isPanelHiding = false
                    panelExitAnim = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    isPanelHiding = false
                }
            })
            start()
        }
        return true
    }

    /**
     * 立即隐藏（临时隐藏用）：只改可见性，不留拦截层、不跑淡出。
     * 同样只复位动画那一格；底色透明度不归这一格管。
     */
    fun hideInstantly() {
        if (!isShowing) return
        isShowing = false
        composeView?.let { cv ->
            panelExitAnim?.cancel()
            cv.visibility = View.GONE
            restoreFadeBaseline(cv)
        }
        isPanelHiding = false
    }

    /** Service 销毁：取消退出动画（：防动画回调持有已销毁 Service） */
    fun cancelExitAnimation() {
        panelExitAnim?.cancel()
    }

    /**
     * 拆窗：先释放输入（还能拿到 windowToken），再把模式钉回 PASSIVE，
     * 最后 dispose composition 并从 WindowManager 摘掉。
     */
    fun destroy() {
        isShowing = false
        focus.releaseInput("service_destroy")
        focus.resetForDestroy()
        composeView?.let { cv ->
            cv.disposeComposition()
            host.detach(cv)
        }
        composeView = null
    }

    // ═══════════ 几何：重算位置 / 缩放 / 移动 ═══════════

    /** 球在动（拖拽或吸边动画每一帧）时面板跟着重贴边 */
    fun reposition() {
        val cv = composeView ?: return
        val p = cv.layoutParams as? WindowManager.LayoutParams ?: return

        val pw = cv.width.takeIf { it > 0 } ?: panelW
        val ph = cv.height.takeIf { it > 0 } ?: panelH

        val (px, py) = positionFor(pw, ph)
        p.x = px
        p.y = py
        host.updateLayout(cv, p)
    }

    /** 拖拽每一帧：只更新内存与视图 */
    fun resize(newWpx: Int, newHpx: Int) {
        val cv = composeView ?: return
        val p = cv.layoutParams as? WindowManager.LayoutParams ?: return

        val (newW, newH) = panelClampedSize(newWpx, newHpx, host::dp)

        p.width = newW
        p.height = newH
        panelW = newW
        panelH = newH
        //  持久化移至 onResizeEnd（拖拽松手时），此处只更新内存与视图
        host.updateLayout(cv, p)
    }

    /** 拖拽松手时把当前尺寸交回 Service 落盘 */
    fun commitSize() {
        persistSize(panelW, panelH)
    }

    fun move(dxPx: Float, dyPx: Float) {
        val cv = composeView ?: return
        val p = cv.layoutParams as? WindowManager.LayoutParams ?: return

        val (x, y) = panelMovedOrigin(
            p.x, p.y, dxPx, dyPx, cv.width, cv.height, host.screenWidth(), host.screenHeight()
        )
        p.x = x
        p.y = y
        host.updateLayout(cv, p)
    }

    // ═══════════ 焦点与输入的所有对外入口（转交 PanelInputFocusOwner） ═══════════

    fun onInputIntent(inputId: String) = focus.onInputIntent(inputId)

    fun onInputFocusChanged(inputId: String, focused: Boolean) =
        focus.onInputFocusChanged(inputId, focused)

    /** 注册 Compose 焦点清理回调，releaseInput 时调用 */
    fun registerClearFocusCallback(callback: () -> Unit) {
        focus.clearComposeFocusCallback = callback
    }

    fun releaseInput(reason: String) = focus.releaseInput(reason)

    // ═══════════ 内部 ═══════════

    private companion object {
        /**
         * 淡入淡出动画的"完全显示"基准。`ComposeView.alpha` 这一条通道**只归动画所有**。
         *
         * 为什么单独把它挑出来写成一枚常量：面板背景的透明程度是用户可设的持久化项
         * （见 [com.lovebrain.app.PanelBackdropOpacity]），
         * 它必须作用在**背景层的颜色 alpha 通道**上，绝不能借用 `View.alpha`。
         * 借了会坏两件事：
         * - 整棵 ComposeView 一起降透明度 = 正文连着一起被洗淡，先读不清的就是用户最要看的那段字；
         * - 这一格在淡出结束、再次打开、立即隐藏时都要被复位回"完全显示"（见下面
         *   [restoreFadeBaseline] 那三处调用），任何存在它上面的用户设置活不过第一次收起面板。
         * 两条通道各管各的：底色自身的 alpha 恒定地跟着用户设定，动画那一层是乘在外面的整窗强度，
         * 复位它抹不掉颜色里那一位。
         */
        const val WINDOW_SHOWN_ALPHA = 1f
    }

    /**
     * 把**动画**那一条 alpha 复位回完全显示。
     *
     * 三个调用点（[present] / [fadeOut] 收尾 / [hideInstantly]）都必须走这里而不是各写一遍字面量：
     * 上一次淡出残留 alpha=0 会让下回"打开"了却完全透明；而这条通道与用户设的背景透明度无关，
     * 复位它只把窗口拉回"完全显示"，抹不到用户设的那一位上。
     */
    private fun restoreFadeBaseline(cv: View) {
        cv.alpha = WINDOW_SHOWN_ALPHA
    }

    private fun fittedSize(): Pair<Int, Int> =
        panelFittedSize(panelW, panelH, host.screenWidth(), host.screenHeight(), host::dp)

    private fun positionFor(pw: Int, ph: Int): Pair<Int, Int> {
        val origin = bubbleOrigin()
        return panelPositionFor(
            pw = pw,
            ph = ph,
            screenW = host.screenWidth(),
            screenH = host.screenHeight(),
            bubbleLeft = origin?.first,
            bubbleTop = origin?.second,
            bubbleSizeDp = bubbleSizeDp(),
            dp = host::dp
        )
    }
}
