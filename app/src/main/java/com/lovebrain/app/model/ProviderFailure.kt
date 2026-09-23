package com.lovebrain.app.model

/**
 * 供应商 / 网络错误的一次性分类结果。
 *
 * 分类只发生在供应商边界的唯一出口
 * （[com.lovebrain.app.data.DeepSeekRepository.classifyApiError]），
 * 下游拿到 [kind] 决定控制流、[message] 决定展示文案，
 * 不再从 `CONFIG_ERROR:` / `PARAM_UNSUPPORTED:` 这类字符串前缀反推错误类型。
 *
 * 英文原文与状态码只进 logcat 供排查，不作为 UI 输入。
 */
data class ProviderFailure(
    val kind: ReplyFailureKind,
    /** 已成品的用户文案；默认取 kind 的固定话术，只有需要带上下文时才覆盖 */
    val message: String = kind.userMessage,
    /** 供应商拒绝当前的 thinking wire shape：调用方可以换一种参数重试 */
    val thinkingParamRejected: Boolean = false,
    /** 被拒绝的参数名（仅诊断用，不参与控制流） */
    val rejectedParameter: String? = null
) {
    /** 配置类错误：重试无意义，降级链应立刻中断 */
    val isConfigProblem: Boolean get() = kind.isConfigProblem

    companion object {
        /**
         * 本地异常（非供应商响应）也走同一个类型，避免下游为它单独分叉一套判定。
         *
         * 未归类到已知 kind 时保留原始 message：这类异常由我们自己抛出，
         * 文案本来就是成品；已归类的（网络/超时）换成固定话术，不把英文原文甩给用户。
         */
        fun fromThrowable(e: Throwable): ProviderFailure {
            if (e is ProviderFailureException) return e.failure
            val kind = ReplyFailureKind.fromException(e)
            return if (kind is ReplyFailureKind.Unknown) {
                ProviderFailure(kind, e.message?.takeIf { it.isNotBlank() } ?: kind.userMessage)
            } else {
                ProviderFailure(kind)
            }
        }
    }
}

/**
 * 供应商侧失败。
 *
 * 边界上已经分类完成，异常只负责把 typed 结果原样带到调用方——
 * 中间层不需要再解析 message 判类型。
 */
class ProviderFailureException(val failure: ProviderFailure) : Exception(failure.message)
