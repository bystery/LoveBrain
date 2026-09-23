package com.lovebrain.app.model

/**
 * S1-02: 统一回复失败类型——sealed error code。
 *
 * UI 只消费 [userMessage] 和可选 [actionLabel]，不接裸异常。
 * 替代之前用字符串前缀识别 CONFIG_ERROR / PARAM_UNSUPPORTED 的做法。
 */
sealed class ReplyFailureKind {
    /** Provider 未配置 */
    data object ProviderMissing : ReplyFailureKind()
    /** API Key 无效或过期 */
    data object Auth : ReplyFailureKind()
    /** 网络连接失败 */
    data object Network : ReplyFailureKind()
    /** 请求超时 */
    data object Timeout : ReplyFailureKind()
    /** Provider 返回 429 限流 */
    data object RateLimited : ReplyFailureKind()
    /** 读取 Prompt 资产失败 */
    data object PromptRead : ReplyFailureKind()
    /** 解析 Provider 响应失败 */
    data object Parse : ReplyFailureKind()
    /** 本地存储写入失败 */
    data object Storage : ReplyFailureKind()
    /** Provider 参数不支持 */
    data object ParamUnsupported : ReplyFailureKind()
    /** 未知错误 */
    data class Unknown(val rawMessage: String?) : ReplyFailureKind()

    /** 用户可读的文案（不含路径、Provider ID 或内部细节） */
    val userMessage: String get() = when (this) {
        ProviderMissing -> "请先配置一个可用的模型供应商"
        Auth -> "API Key 无效或已过期，请检查设置"
        Network -> "网络连接失败，请检查网络后重试"
        Timeout -> "请求超时，请重试"
        RateLimited -> "请求过于频繁，请稍后再试"
        PromptRead -> "读取配置时出错，请重试"
        Parse -> "解析回复失败，请重试"
        Storage -> "保存数据时出错，请重试"
        ParamUnsupported -> "模型不支持当前思考模式，已尝试所有降级方案"
        is Unknown -> "生成失败，请重试"
    }

    /** 是否可重试 */
    val retryable: Boolean get() = when (this) {
        ProviderMissing, Auth -> false
        Network, Timeout, RateLimited, PromptRead, Parse, Storage, ParamUnsupported -> true
        is Unknown -> true
    }

    companion object {
        /** 从异常映射到 ReplyFailureKind */
        fun fromException(e: Throwable): ReplyFailureKind = when {
            // SocketTimeoutException extends IOException, so check it first
            e is java.net.SocketTimeoutException -> Timeout
            e is java.net.UnknownHostException || e is java.net.SocketException ||
                e is java.net.ConnectException -> Network
            e is java.io.IOException -> Network
            else -> Unknown(e.message)
        }

        /** 从错误字符串映射到 ReplyFailureKind */
        fun fromErrorMessage(msg: String?): ReplyFailureKind {
            if (msg == null) return Unknown(null)
            return when {
                msg.startsWith("CONFIG_ERROR:") -> {
                    val stripped = msg.removePrefix("CONFIG_ERROR:").trim()
                    when {
                        stripped.contains("Key", ignoreCase = true) ||
                            stripped.contains("密钥", ignoreCase = true) -> Auth
                        stripped.contains("地址", ignoreCase = true) ||
                            stripped.contains("URL", ignoreCase = true) -> ProviderMissing
                        else -> ProviderMissing
                    }
                }
                msg.startsWith("PARAM_UNSUPPORTED:") -> ParamUnsupported
                msg.contains("429") -> RateLimited
                msg.contains("401") || msg.contains("403") -> Auth
                msg.contains("超时") || msg.contains("timeout", ignoreCase = true) -> Timeout
                msg.contains("解析") -> Parse
                else -> Unknown(msg)
            }
        }
    }
}
