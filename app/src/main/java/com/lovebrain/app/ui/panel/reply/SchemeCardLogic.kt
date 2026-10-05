package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.model.RewriteState

/**
 * 从 SchemeCard 抽离的纯机制逻辑——可测试，不依赖 Composable。
 *
 * 这里原本还住着长按录音的手势 reducer 与语音两事件 rendezvous 提交决策，
 * 随语音模式一起删除；卡片现在只剩普通点击展开。
 *
 * 包含：
 * - presentation state derivation（SchemeCardPresentationState 推导）
 */

/**
 * 从当前状态推导卡片展示态——纯函数，不依赖 Composable。
 *
 * @param isRewriting 是否在改写中
 * @param rewriteError 改写错误消息
 * @param rewriteDone 改写是否完成
 * @param isExpanded 卡片是否展开
 * @return 卡片应展示的 presentation state
 */
fun deriveCardPresentationState(
    isRewriting: Boolean,
    rewriteError: String?,
    rewriteDone: Boolean,
    isExpanded: Boolean
): SchemeCardPresentationState = when {
    isRewriting -> SchemeCardPresentationState.Rewriting
    rewriteError != null -> SchemeCardPresentationState.RewriteError(rewriteError)
    rewriteDone -> SchemeCardPresentationState.RewriteDone
    isExpanded -> SchemeCardPresentationState.Adjusting
    else -> SchemeCardPresentationState.Collapsed
}
