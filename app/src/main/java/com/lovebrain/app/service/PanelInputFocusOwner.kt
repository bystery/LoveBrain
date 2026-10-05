package com.lovebrain.app.service

import android.view.WindowManager
import com.lovebrain.app.util.L

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
internal enum class PanelFocusMode {
    PASSIVE,
    EDITING
}

/**
 * 统一 Panel flags 计算函数：整个项目唯一允许计算 Panel Window flags 的入口。
 *
 * PASSIVE: NOT_FOCUSABLE | NOT_TOUCH_MODAL | WATCH_OUTSIDE_TOUCH
 * EDITING: NOT_TOUCH_MODAL | WATCH_OUTSIDE_TOUCH（去掉 NOT_FOCUSABLE）
 *
 * 纯函数（只有 WindowManager 的编译期常量），所以这条契约能被单元测试直接证伪。
 */
internal fun panelFlagsFor(mode: PanelFocusMode): Int {
    var result =
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    if (mode == PanelFocusMode.PASSIVE) {
        result = result or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }
    return result
}

/**
 * 焦点所有者对"那一扇窗"的全部需求。
 *
 * 抽成三件事是因为焦点状态机本身必须可被测：
 * 真窗口（OverlayPanelWindow）负责 flags 落与 IME，本文件只负责"该不该抢焦点"这个答案。
 */
internal interface PanelFocusWindow {
    /** Panel 窗口是否已存在（没建起来时任何焦点切换都是空操作） */
    fun isPresent(): Boolean

    /**
     * 把 flags 写进窗口并 updateViewLayout。
     * @return true = 已生效；false = 窗口没了 / WindowManager 抛错，调用方必须回滚记录的模式
     */
    fun applyFlags(flags: Int): Boolean

    /** 收起输入法（通过清焦点自动隐藏，这里是兜底） */
    fun hideIme()
}

/**
 * Panel 输入焦点与输入框所有权的唯一持有者。
 *
 * 变化理由只有一条：**Panel 与宿主 App 抢不抢输入焦点**。
 * 什么时候进 EDITING、什么时候必须退回 PASSIVE、失焦的是不是当前那个输入框——
 * 全部收在这里，窗口类与 Service 都只能经它问一句、改一次。
 */
internal class PanelInputFocusOwner(private val window: PanelFocusWindow) {

    /** 唯一 Panel 焦点状态源 */
    var mode: PanelFocusMode = PanelFocusMode.PASSIVE
        private set

    /** 当前持有输入焦点的输入框 ID（null = 无输入框获焦） */
    var activeInputId: String? = null
        private set

    /** Compose 输入焦点清理回调（由 Panel 层注册，releaseInput 时调用） */
    var clearComposeFocusCallback: (() -> Unit)? = null

    /** 进入编辑态：用户明确按下 TextField 后调用 */
    fun enterEditing(reason: String) {
        setMode(PanelFocusMode.EDITING, reason)
    }

    /** 退出编辑态：输入框失焦或 Panel 即将隐藏时调用 */
    fun exitEditing(reason: String) {
        setMode(PanelFocusMode.PASSIVE, reason)
    }

    /**
     * 统一 Panel 焦点模式切换入口：整个项目唯一允许修改 Panel FLAG_NOT_FOCUSABLE 的方法。
     * 其他文件只能通过 [enterEditing] / [exitEditing] / [releaseInput] 间接调用。
     *
     * 写失败要回滚，否则"记着的模式"与 Window 实际 flags 不一致——
     * 那正是这套状态机当初要消灭的东西，所以宁可退回原值也别留一个假当前态。
     */
    fun setMode(newMode: PanelFocusMode, reason: String) {
        if (!window.isPresent()) return
        if (mode == newMode) return

        val oldMode = mode
        mode = newMode

        if (!window.applyFlags(panelFlagsFor(newMode))) {
            L.e("panel focus update failed: $oldMode -> $newMode reason=$reason")
            mode = oldMode
        } else {
            L.w("panel focus: $oldMode -> $newMode reason=$reason")
        }
    }

    /**
     * 释放 Panel 输入状态：清理 Compose 焦点 + 隐藏 IME + Window 恢复 NOT_FOCUSABLE。
     * 关闭顺序：1.清 TextField Focus → 2.Hide IME → 3.Window → PASSIVE
     */
    fun releaseInput(reason: String) {
        // 0. 清空输入所有者
        activeInputId = null
        // 1. 清理 Compose 输入焦点
        clearComposeFocusCallback?.invoke()
        // 2. 隐藏 IME（通过清除焦点自动隐藏，此处兜底）
        if (window.isPresent()) window.hideIme()
        // 3. Window 恢复 NOT_FOCUSABLE
        exitEditing(reason)
    }

    /** 输入意图：用户触碰了某个输入框 → 立即接管 activeInputId 并进入编辑态 */
    fun onInputIntent(inputId: String) {
        activeInputId = inputId
        enterEditing("input_intent:$inputId")
    }

    /**
     * 焦点变化：只有当前 activeInputId 的失焦才允许退出 EDITING。
     * 如果 activeInputId 已经被新输入框接管，则忽略旧输入框的 blur。
     */
    fun onInputFocusChanged(inputId: String, focused: Boolean) {
        if (focused) {
            activeInputId = inputId
            return
        }
        if (activeInputId == inputId) {
            activeInputId = null
            exitEditing("input_blur:$inputId")
        }
    }

    /**
     * 窗口即将销毁：直接落到 PASSIVE 并交回 Compose 焦点回调。
     * 不动窗口（它下一刻就不在了），只保证不会有人拿着已死的回调往下调。
     */
    fun resetForDestroy() {
        mode = PanelFocusMode.PASSIVE
        clearComposeFocusCallback = null
    }
}
