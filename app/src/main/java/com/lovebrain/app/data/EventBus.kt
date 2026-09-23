package com.lovebrain.app.data

import android.os.SystemClock
import com.lovebrain.app.util.L
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轻量级事件总线：解耦 Service 之间的直接静态调用。
 * 由 Koin 管理为单例，所有 Service/ViewModel 通过注入使用。
 */
object EventBus {

    /** 无障碍服务捕获到的消息事件 */
    data class CapturedMessage(
        val text: String,
        /** 捕获发生时刻（SystemClock.uptimeMillis），供订阅方过滤服务重建前的旧重放 */
        val ts: Long
    )

    // replay = 1——collector 未就绪的窗口不丢事件；服务重建后的旧重放由
    // FloatingService 按 ts 做 session 过滤（早于本次启动的一律丢弃）
    private val _capturedMessages = MutableSharedFlow<CapturedMessage>(replay = 1, extraBufferCapacity = 16)
    val capturedMessages: SharedFlow<CapturedMessage> = _capturedMessages.asSharedFlow()

    /**
     * CAP-03：返回投递结果，修正无订阅者诊断。
     * - tryEmit=false：buffer busy，真正的投递失败
     * - accepted=true 但无实时订阅者：replay=1 会保留最近一条供迟订阅消费，不是"丢失"
     * 日志只记长度，不记内容。
     */
    fun emitCapturedMessage(text: String): Boolean {
        val event = CapturedMessage(
            text = text,
            ts = SystemClock.uptimeMillis()
        )

        val accepted = _capturedMessages.tryEmit(event)

        if (!accepted) {
            L.w("捕获事件投递失败：buffer busy，长度=${text.length}")
        } else if (_capturedMessages.subscriptionCount.value == 0) {
            L.w("捕获事件暂无实时订阅者，已保留最近事件供迟订阅消费，长度=${text.length}")
        }

        return accepted
    }

    /**
     * 面板打开请求：App 首页功能卡片 → FloatingService。
     * mode: 0=回复, 1=谈心；showPlan: 是否同时切到今日锦囊 Tab。
     * 用 StateFlow 实现 replay=1：服务尚未启动时发出的请求，服务订阅后仍能消费到（消费后置空）。
     */
    data class PanelRequest(val mode: Int, val showPlan: Boolean)

    private val _panelRequest = MutableStateFlow<PanelRequest?>(null)
    val panelRequest: StateFlow<PanelRequest?> = _panelRequest.asStateFlow()

    fun requestPanel(mode: Int, showPlan: Boolean = false) {
        _panelRequest.value = PanelRequest(mode, showPlan)
    }

    /** 服务消费后置空，避免下次启动服务重复触发 */
    fun consumePanelRequest(): PanelRequest? {
        val r = _panelRequest.value
        _panelRequest.value = null
        return r
    }
}
