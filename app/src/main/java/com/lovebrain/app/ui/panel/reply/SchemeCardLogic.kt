package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.model.RewriteState

/**
 * 从 SchemeCard 抽离的纯机制逻辑——可测试，不依赖 Composable。
 *
 * 包含：
 * - presentation state derivation（SchemeCardPresentationState 推导）
 * - gesture state reduction（手势状态机纯函数）
 * - voice rendezvous commit decision（语音两事件 rendezvous 提交决策）
 */

/**
 * 从当前状态推导卡片展示态——纯函数，不依赖 Composable。
 *
 * @param isRecording 是否在录音/识别中
 * @param voiceState 语音状态
 * @param isRewriting 是否在改写中
 * @param rewriteError 改写错误消息
 * @param rewriteDone 改写是否完成
 * @param isExpanded 卡片是否展开
 * @return 卡片应展示的 presentation state
 */
fun deriveCardPresentationState(
    isRecording: Boolean,
    voiceState: VoiceRewriteState,
    isRewriting: Boolean,
    rewriteError: String?,
    rewriteDone: Boolean,
    isExpanded: Boolean
): SchemeCardPresentationState = when {
    isRecording && voiceState == VoiceRewriteState.PROCESSING -> SchemeCardPresentationState.Recognizing
    isRecording -> SchemeCardPresentationState.Recording
    isRewriting -> SchemeCardPresentationState.Rewriting
    rewriteError != null -> SchemeCardPresentationState.RewriteError(rewriteError)
    rewriteDone -> SchemeCardPresentationState.RewriteDone
    isExpanded -> SchemeCardPresentationState.Adjusting
    else -> SchemeCardPresentationState.Collapsed
}

/**
 * 手势状态机——纯函数 reducer。
 *
 * 输入当前手势阶段和事件，返回新阶段。
 * 用于测试手势生命周期的状态转换，不依赖 Composable。
 */
enum class GestureEvent {
    DOWN,           // 手指按下
    LONG_PRESS_REACHED,  // 达到长按阈值
    UP_IN_BOUNDS,   // 手指在边界内抬起
    UP_OUT_OF_BOUNDS, // 手指移出边界
    DRAG_CANCELLED,  // 拖动取消
    SYSTEM_CANCEL    // 系统取消
}

/**
 * 手势状态转换——纯函数。
 *
 * 规则：
 * - IDLE + DOWN → PRESSING
 * - PRESSING + LONG_PRESS_REACHED → RECORDING（需录音条件满足）
 * - PRESSING + UP_IN_BOUNDS → IDLE（普通点击）
 * - PRESSING + DRAG_CANCELLED → IDLE
 * - RECORDING + UP_IN_BOUNDS → RELEASED
 * - RECORDING + UP_OUT_OF_BOUNDS → CANCELLED
 * - RECORDING + SYSTEM_CANCEL → CANCELLED
 * - RELEASED + 任何 → IDLE
 * - CANCELLED + 任何 → IDLE
 */
fun reduceGesturePhase(
    current: GesturePhase,
    event: GestureEvent,
    canStartRecording: Boolean = true
): GesturePhase = when (current) {
    GesturePhase.IDLE -> when (event) {
        GestureEvent.DOWN -> GesturePhase.PRESSING
        else -> GesturePhase.IDLE
    }
    GesturePhase.PRESSING -> when (event) {
        GestureEvent.LONG_PRESS_REACHED -> if (canStartRecording) GesturePhase.RECORDING else GesturePhase.IDLE
        GestureEvent.UP_IN_BOUNDS -> GesturePhase.IDLE
        GestureEvent.DRAG_CANCELLED -> GesturePhase.IDLE
        GestureEvent.SYSTEM_CANCEL -> GesturePhase.IDLE
        else -> GesturePhase.PRESSING
    }
    GesturePhase.RECORDING -> when (event) {
        GestureEvent.UP_IN_BOUNDS -> GesturePhase.RELEASED
        GestureEvent.UP_OUT_OF_BOUNDS -> GesturePhase.CANCELLED
        GestureEvent.SYSTEM_CANCEL -> GesturePhase.CANCELLED
        else -> GesturePhase.RECORDING
    }
    GesturePhase.RELEASED -> GesturePhase.IDLE
    GesturePhase.CANCELLED -> GesturePhase.IDLE
}

/**
 * 语音两事件 rendezvous 提交决策——纯函数。
 *
 * 统一提交条件：
 *   physicalReleased && finalTranscript 非空 && !cancelled && !submitted
 *
 * 两个事件谁先来都行：
 * - 路径 A: onResults 先来 → finalTranscript 已缓存，等 release 到达时提交
 * - 路径 B: release 先来 → physicalReleased 已标记，等 onResults 到达时提交
 *
 * @param physicalReleased 物理松手标志
 * @param finalTranscript 缓存的 final transcript（可能为 null——尚未到达）
 * @param cancelled 手势取消标志
 * @param submitted 已提交标志（保证只提交一次）
 * @return 是否应该提交
 */
fun shouldCommitTranscript(
    physicalReleased: Boolean,
    finalTranscript: String?,
    cancelled: Boolean,
    submitted: Boolean
): Boolean = physicalReleased &&
    finalTranscript != null &&
    finalTranscript.isNotBlank() &&
    !cancelled &&
    !submitted
