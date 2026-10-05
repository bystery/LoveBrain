package com.lovebrain.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lovebrain.app.util.L

/**
 * 悬浮窗挂载结果。
 *
 * 宿主类不知道 Service 的存在（它只借到一只 Context），所以"权限被回收 / WindowManager 拒挂"
 * 只能作为结果值交回调用方，由 Service 决定 stopSelf —— 停服这句永远只有 Service 会说。
 */
internal enum class OverlayAttach {
    ATTACHED,             // 这一次真的挂上了
    ALREADY_ATTACHED,     // 窗口早就在，本次什么都没做
    PERMISSION_REVOKED,   // 悬浮窗权限运行中被回收
    FAILED                // WindowManager 拒绝挂载
}

/**
 * 气泡与面板两扇悬浮窗**共用**的宿主样板。
 *
 * 唯一的理由：Android 窗口层自身。
 * - 悬浮窗权限可能在运行中被回收；
 * - addView / updateViewLayout 会抛（view 已被移除、尺寸非法）；
 * - ComposeView 必须挂上 Lifecycle / ViewModelStore / SavedState 三个宿主，否则 composition 起不来；
 * - 部分 ROM 上窗口背景不透 → 圆角球外面套一圈矩形。这个历史 bug 的修法（强制透明背景）
 *   和窗口类型 / 像素格式一样只留这一处，两扇窗各处抄一份就修不全。
 *
 * 「球画什么」「面板画什么」「谁该抢焦点」都不在这里，各有各的文件。
 * 这里也**不持有任何协程**：服务生命周期内唯一的协程账本在 FloatingService。
 */
internal class OverlayWindowHost(
    private val context: Context,
    private val treeOwner: FloatingService
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** dp → px（原来 Service 自己那份 dp() 搬到这里，两扇窗与装配方共用一把尺） */
    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    /** 系统"移除动画"（无障碍）→ 位移动画瞬时完成（WCAG 2.3.3） */
    fun animatorDisabled(): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f

    /**
     * 悬浮窗 LayoutParams 的唯一出处。
     *
     * 窗口类型固定 TYPE_APPLICATION_OVERLAY；
     * 矩形外露修复：RGBA_8888 比 TRANSLUCENT 更可靠（部分 ROM 上 TRANSLUCENT 背景不透明）。
     */
    fun overlayParams(
        width: Int,
        height: Int,
        flags: Int
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.RGBA_8888
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    /**
     * 悬浮窗 ComposeView 宿主样板（桥接生命周期 + 透明背景防矩形外露）。
     * 集中一处，避免气泡/面板各抄一份导致矩形外露 bug 修不全。
     */
    fun newComposeView(): ComposeView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(treeOwner)
        setViewTreeViewModelStoreOwner(treeOwner)
        setViewTreeSavedStateRegistryOwner(treeOwner)
        setBackgroundColor(Color.TRANSPARENT)
        setBackground(null)
    }

    /**
     * 挂载一扇悬浮窗：先查权限，再 addView，成功后立刻强制清背景
     * （addView 后 ComposeView attach 可能被 theme 背景覆盖，实测矩形外露仍复现）。
     */
    fun attach(
        view: View,
        params: WindowManager.LayoutParams,
        tag: String
    ): OverlayAttach {
        if (!Settings.canDrawOverlays(context)) {
            L.w("overlay permission revoked, stopping service")
            return OverlayAttach.PERMISSION_REVOKED
        }
        return runCatching { windowManager.addView(view, params) }.fold(
            onSuccess = {
                view.forceTransparentWindowBackground()
                L.w("$tag addView OK size=${params.width}x${params.height} pos=(${params.x},${params.y})")
                OverlayAttach.ATTACHED
            },
            onFailure = {
                L.e("$tag addView failed", it)
                OverlayAttach.FAILED
            }
        )
    }

    /** 移除一扇悬浮窗：Service 销毁时窗口可能已经被系统收走，故 runCatching */
    fun detach(view: View) {
        runCatching { windowManager.removeView(view) }
    }

    /**
     * 更新窗口布局（拖拽、吸边、缩放都走这里）。
     * @return true = 已生效；false = WindowManager 抛错，调用方需要回滚自己记的状态
     */
    fun updateLayout(view: View, params: WindowManager.LayoutParams): Boolean =
        runCatching { windowManager.updateViewLayout(view, params) }.isSuccess

    /** 收起输入法：只清焦点，不关窗口 */
    fun hideImeFor(view: View) {
        val imm =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /** 矩形外露修复：强制悬浮窗 view 背景透明（addView 后调用，防 ComposeView attach 后重设主题背景） */
    private fun View.forceTransparentWindowBackground() {
        try {
            background = null
            setBackgroundColor(Color.TRANSPARENT)
            setClipToOutline(false)
        } catch (_: Exception) {
            // 忽略：最坏情况保持原背景
        }
    }
}
