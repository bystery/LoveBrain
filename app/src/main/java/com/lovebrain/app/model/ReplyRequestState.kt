package com.lovebrain.app.model

/**
 * 统一回复请求状态——单一事实源。
 *
 * 替代之前分散的 isPreparing / isGenerating / isGeneratingCore 三个布尔标志，
 * 所有活跃状态都携带唯一 [requestId]，确保旧请求的 finally 不会覆盖新请求。
 *
 * 状态流转：
 * Idle → Preparing → Streaming → Success / RecoverableError → Idle
 *
 * 规则：
 * - CancellationException 必须重抛，不转为 Error。
 * - finally 只清理相同 requestId 拥有的状态。
 * - 快速连点时，旧请求的 completion handler 不得把新请求改回 Idle。
 */
sealed class ReplyRequestState {

    /** 空闲——无活跃请求 */
    data object Idle : ReplyRequestState()

    /**
     * 准备中——已点击生成，正在冻结快照、读取意图/纠正、构建 Prompt、快照 Provider。
     * [requestId] 标识本次请求，用于 finally 所有权校验。
     */
    data class Preparing(val requestId: String) : ReplyRequestState()

    /**
     * 流式中——Provider 已开始返回数据。
     * [requestId] 与 Preparing 阶段一致。
     */
    data class Streaming(val requestId: String) : ReplyRequestState()

    /**
     * 成功——结果已发布。
     * 此状态后 UI 自行管理结果展示，state 应回到 Idle。
     */
    data object Success : ReplyRequestState()

    /**
     * 可恢复错误——用户可重试或修改配置后重试。
     * [message] 为用户可读的错误说明（不含裸异常）。
     * [retryable] 为 true 时错误区提供重试按钮。
     */
    data class RecoverableError(
        val message: String,
        val retryable: Boolean = true
    ) : ReplyRequestState()

    companion object {
        /** 生成新的唯一 requestId */
        fun newRequestId(): String = java.util.UUID.randomUUID().toString()
    }
}

/** 从 ReplyRequestState 派生的 UI 便利属性 */
val ReplyRequestState.isBusy: Boolean
    get() = this is ReplyRequestState.Preparing || this is ReplyRequestState.Streaming

val ReplyRequestState.isPreparing: Boolean
    get() = this is ReplyRequestState.Preparing

val ReplyRequestState.isStreaming: Boolean
    get() = this is ReplyRequestState.Streaming

val ReplyRequestState.requestId: String?
    get() = when (this) {
        is ReplyRequestState.Preparing -> this.requestId
        is ReplyRequestState.Streaming -> this.requestId
        else -> null
    }
