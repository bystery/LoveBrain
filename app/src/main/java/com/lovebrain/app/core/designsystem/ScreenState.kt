package com.lovebrain.app.core.designsystem

/**
 * 一个目的地的四类顶层状态——指导书 §6.3 要求"每个目的地只允许这四类"。
 *
 * 为什么要有它：此前每页各自发明空态/错误态（反馈案例用一列居中 Text、
 * Provider 用内联文字、知识库用另一套），于是"空的时候长什么样"没有一致答案，
 * 也没法一处修好、处处生效。
 *
 * 判定的**优先顺序**由持有状态的那一侧决定（本页现状：Loading > Error > Empty > Content），
 * 组件只负责把已经定好的那一格画出来——不在 UI 里再判一次，
 * 否则" isLoading 与 error 同时为真"这类情况会在两个地方各解一遍。
 *
 * `message` 用 String 而不是资源 id：调用方用 `stringResource(...)` 解析后传进来。
 * 这里不另造一套 UiText（KISS/YAGNI），但**要求**调用方交出的字符串来自资源——
 * 用户可见字面量由 UiStringLiteralBudgetTest 那把尺管着。
 */
sealed interface ScreenState<out T> {

    /** 第一次数据还没到 */
    data object Loading : ScreenState<Nothing>

    /** 有内容 */
    data class Content<T>(val value: T) : ScreenState<T>

    /** 请求成功但结果为空——可以带一个文字动作（例如"点这里主动发一条"那种入口） */
    data class Empty(val message: String, val action: ScreenAction? = null) : ScreenState<Nothing>

    /** 请求失败——必须带重试入口；没有重试的错误态等于把用户关死 */
    data class Error(val message: String, val retry: ScreenAction? = null) : ScreenState<Nothing>
}

/** 状态里携带的一个动作。label 必须来自资源，读屏念的就是它 */
data class ScreenAction(val label: String, val run: () -> Unit)
