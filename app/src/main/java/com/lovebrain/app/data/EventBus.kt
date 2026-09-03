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

    //  ：replay = 1——collector 未就绪的窗口不丢事件；服务重建后的旧重放由
    // FloatingService 按 ts 做 session 过滤（早于本次启动的一律丢弃）
    private val _capturedMessages = MutableSharedFlow<CapturedMessage>(replay = 1, extraBufferCapacity = 16)
    val capturedMessages: SharedFlow<CapturedMessage> = _capturedMessages.asSharedFlow()

    fun emitCapturedMessage(text: String) {
        // ：无订阅者时记丢失日志（：只记长度，不记内容）
        if (_capturedMessages.subscriptionCount.value == 0) {
            L.w("捕获事件时无订阅者（丢失，长度=${text.length}）")
        }
        _capturedMessages.tryEmit(CapturedMessage(text, SystemClock.uptimeMillis()))
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
