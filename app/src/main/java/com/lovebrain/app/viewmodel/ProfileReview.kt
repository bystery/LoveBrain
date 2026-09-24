package com.lovebrain.app.viewmodel

import com.lovebrain.app.model.ProfileSuggestion

/**
 * 画像建议卡片的一份状态：建议内容 + "正在确认"两件事，一份快照。
 *
 * 为什么要合（指导书 §2.2 那行"各自直接写多个 MutableStateFlow，没有统一 Reducer/UiState"）：
 * 原先是两个 flow（`profileSuggestion` 与 `isProfileConfirming`），VM 里 14 处各改各的。
 * 它们必须同帧：卡片显示与否看建议、按钮与转圈看"在不在确认"，两张脸读两个源就会拼出
 * 没人设计过的中间态——例如确认成功时"建议已清空、confirming 还是 true"，
 * 或者两个请求同时通过 `if (isConfirming) return` 这道门。
 *
 * 判定也收在这里：能不能发起确认（[canAttemptConfirm]）、建议本身可不可确认（[canConfirm]）、
 * 重复点确认要忽略（[Event.ConfirmStarted] 自己判）。UI 只读一份快照。
 *
 * ⚠ 这一族里 `vectorUpdate`（向量摘要横幅）与 `stageSuggestion`（阶段建议卡）**没有**跟着并进来：
 * 它们与这张卡片不需要同帧（一个在顶部横幅、一个是独立卡片），并成一个对象反而让
 * "关掉横幅"与"确认画像"互为无关的两件事变成一次大快照复制。这是判断，不是没做完。
 */
data class ProfileReview(
    val suggestion: ProfileSuggestion? = null,
    val isConfirming: Boolean = false
) {
    /** 卡片上"确认"按钮能不能点：payload 校验过才行 */
    val canConfirm: Boolean get() = suggestion?.canConfirm == true

    /** 能不能发起一次确认：有建议且没在确认中（重复点击与空卡片都拒） */
    val canAttemptConfirm: Boolean get() = suggestion != null && !isConfirming

    sealed interface Event {
        /** 新建议到达（首次生成，或重新生成回来的那份） */
        data class Arrived(val suggestion: ProfileSuggestion) : Event

        /** 用户关掉卡片 / 建议被判定作废 */
        data object Dismissed : Event

        /** 开始写入。重复发起时原样返回——"一次只允许一个确认"这条规则住在这里 */
        data object ConfirmStarted : Event

        /** 写入结束（成功、失败、抛异常都算结束） */
        data object ConfirmFinished : Event

        /**
         * 只有当前还是这一份建议时才清空。
         *
         * 确认期间可能又有新建议到达（后台 reflect 又推了一张），
         * 无条件清就会把用户没看过的新卡片抹掉。
         */
        data class ClearedIfCurrent(val suggestionId: String) : Event
    }

    fun reduce(event: Event): ProfileReview = when (event) {
        is Event.Arrived -> copy(suggestion = event.suggestion)
        Event.Dismissed -> copy(suggestion = null)
        Event.ConfirmStarted -> if (canAttemptConfirm) copy(isConfirming = true) else this
        Event.ConfirmFinished -> copy(isConfirming = false)
        is Event.ClearedIfCurrent ->
            if (suggestion?.suggestionId == event.suggestionId) copy(suggestion = null) else this
    }
}
