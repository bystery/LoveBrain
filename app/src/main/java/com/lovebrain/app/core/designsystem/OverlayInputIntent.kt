package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 悬浮窗输入面的编辑意图上报：每次手势只在按下那一跳报一次（边沿触发、不消费事件）。
 *
 * WHY 边沿：逐 pressed 事件上报会让窗口 flags（NOT_FOCUSABLE↔可聚焦）随
 * updateViewLayout 反复抖动，长按手势正压在窗口属性更新的同一帧上会被系统打断。
 * null（默认场景：普通 Activity）→ 原样返回，不挂任何手势。
 */
fun Modifier.overlayInputIntent(onInputIntent: (() -> Unit)?): Modifier =
    if (onInputIntent == null) this
    else pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onInputIntent()
        }
    }