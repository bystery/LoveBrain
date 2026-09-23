package com.lovebrain.app.model

/**
 * S2-03: 领域事件——typed event/outcome，替代大回调接口。
 *
 * 每个 use case 暴露 Flow<DomainEvent>，ViewModel 的 reducer 是唯一状态写入口。
 * 事件只包含 domain 数据：Started/Chunk/Schemes/Usage/Completed/Failed。
 * 不回调 PanelState——UI state 由 reducer 派生。
 *
 * 所有事件携带 requestId，reducer 只接受 owner 匹配的事件。
 */
sealed class DomainEvent {
    abstract val requestId: String

    /** 生成开始 */
    data class Started(override val requestId: String) : DomainEvent()

    /** 流式文本块 */
    data class Chunk(
        override val requestId: String,
        val text: String
    ) : DomainEvent()

    /** 流式方案卡 */
    data class Schemes(
        override val requestId: String,
        val schemes: List<Scheme>
    ) : DomainEvent()

    /** Provider usage 回报 */
    data class Usage(
        override val requestId: String,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val costYuan: Double?
    ) : DomainEvent()

    /** 首字耗时回报 */
    data class FirstToken(
        override val requestId: String,
        val elapsedMs: Long
    ) : DomainEvent()

    /** 生成完成 */
    data class Completed(
        override val requestId: String,
        val result: GenerateResult
    ) : DomainEvent()

    /** 生成失败 */
    data class Failed(
        override val requestId: String,
        val failure: ReplyFailureKind
    ) : DomainEvent()

    /** 流式方案卡重置（retry 前） */
    data class SchemesReset(override val requestId: String) : DomainEvent()

    /** 流式文本重置 */
    data class CoreTextReset(override val requestId: String) : DomainEvent()
}

/**
 * S2-03: 回复 reducer——唯一状态写入口。
 * 只接受 owner 匹配的事件（requestId 隔离）。
 */
object ReplyReducer {
    /**
     * 将事件应用到当前状态，返回新状态。
     * 旧请求的迟到事件被丢弃（requestId 不匹配）。
     */
    fun reduce(
        current: ReplyRequestState,
        event: DomainEvent
    ): ReplyRequestState {
        // S2-03: requestId 隔离——只接受 owner 匹配的事件
        val currentRequestId = current.requestId
        if (currentRequestId != null && currentRequestId != event.requestId) {
            // 迟到旧回调，丢弃
            return current
        }

        return when (event) {
            is DomainEvent.Started -> {
                // Preparing → Streaming（保持同一 requestId）
                ReplyRequestState.Streaming(event.requestId)
            }
            is DomainEvent.Completed -> {
                when (event.result) {
                    is GenerateResult.Success -> ReplyRequestState.Idle
                    is GenerateResult.Error -> ReplyRequestState.RecoverableError(
                        message = event.result.message,
                        retryable = true
                    )
                }
            }
            is DomainEvent.Failed -> {
                ReplyRequestState.RecoverableError(
                    message = event.failure.userMessage,
                    retryable = event.failure.retryable
                )
            }
            // Chunk/Schemes/Usage/FirstToken/SchemesReset/CoreTextReset 不改变请求状态
            // 这些事件由 ViewModel 在 side-effect 中处理（写流式临时态）
            else -> current
        }
    }
}
