package com.lovebrain.app.data

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
        val text: String
    )

    //  ：replay = 1——面板订阅前发生的捕获仍可收到最近一条（重放去重由
    // FloatingService.addClipIfNew 兜底）；完整补录见 backlog 挂起项
    private val _capturedMessages = MutableSharedFlow<CapturedMessage>(replay = 1, extraBufferCapacity = 16)
    val capturedMessages: SharedFlow<CapturedMessage> = _capturedMessages.asSharedFlow()

    fun emitCapturedMessage(text: String) {
        // ：无订阅者时记丢失日志（：只记长度，不记内容）
        if (_capturedMessages.subscriptionCount.value == 0) {
            L.w("捕获事件时无订阅者（丢失，长度=${text.length}）")
        }
        _capturedMessages.tryEmit(CapturedMessage(text))
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
