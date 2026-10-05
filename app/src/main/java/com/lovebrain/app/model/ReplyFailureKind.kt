package com.lovebrain.app.model

/**
 * 统一回复失败类型——sealed error code。
 *
 * UI 只消费 [userMessage] 和 [retryable]，不接裸异常；
 * 控制流（是否换参数重试、是否中断降级链）只看 kind。
 *
 * 这里刻意**不提供** `fromErrorMessage(String)`：
 * 错误类型一旦能从字符串前缀/异常 message 反推，分类就会散落到每个消费点。
 * 分类只发生在 [com.lovebrain.app.data.DeepSeekRepository.classifyApiError] 这一个供应商边界出口，
 * 产出 typed 的 [ProviderFailure]。
 */
sealed class ReplyFailureKind {
    /** Provider 未配置 */
    data object ProviderMissing : ReplyFailureKind()
    /** 接口地址不合法（缺协议、被 HTTPS 信任闸门拦下、Key 误填进地址栏） */
    data object InvalidAddress : ReplyFailureKind()
    /** API Key 无效或过期 */
    data object Auth : ReplyFailureKind()
    /** 网络连接失败 */
    data object Network : ReplyFailureKind()
    /** 请求超时 */
    data object Timeout : ReplyFailureKind()
    /** Provider 返回 429 限流 */
    data object RateLimited : ReplyFailureKind()
    /** 账户余额不足 */
    data object InsufficientBalance : ReplyFailureKind()
    /** 内容被安全过滤拦截 */
    data object ContentFiltered : ReplyFailureKind()
    /** 上下文超出模型长度上限 */
    data object ContextTooLong : ReplyFailureKind()
    /** 供应商侧过载/5xx */
    data object ServerBusy : ReplyFailureKind()
    /** 读取 Prompt 资产失败 */
    data object PromptRead : ReplyFailureKind()
    /** 解析 Provider 响应失败 */
    data object Parse : ReplyFailureKind()
    /** 本地存储写入失败 */
    data object Storage : ReplyFailureKind()
    /** Provider 参数不支持 */
    data object ParamUnsupported : ReplyFailureKind()
    /**
     * Provider 配置在本轮冻结之后被改掉了。
     *
     * 旧实现遇到这种情况只写一条日志、然后改用新配置把请求发出去——
     * 于是"冻结 Provider 身份"是假的。现在直接失败，让用户用新配置重试。
     */
    data object ProviderChanged : ReplyFailureKind()
    /** 未知错误 */
    data class Unknown(val rawMessage: String?) : ReplyFailureKind()

    /** 用户可读的文案（不含路径、Provider ID 或内部细节） */
    val userMessage: String get() = when (this) {
        ProviderMissing -> "请先配置一个可用的模型供应商"
        InvalidAddress -> "接口地址不可用，请检查设置里的地址"
        Auth -> "API Key 无效或已过期，请检查设置"
        Network -> "网络连接失败，请检查网络后重试"
        Timeout -> "请求超时，请重试"
        RateLimited -> "请求太频繁了，稍等几秒再试"
        InsufficientBalance -> "API 余额不足，去供应商官网充值后就能继续用了"
        ContentFiltered -> "内容被安全过滤拦截了，换个说法再试"
        ContextTooLong -> "内容太长超过模型上限，删减几条对话再试"
        ServerBusy -> "服务繁忙，稍后再试"
        PromptRead -> "读取配置时出错，请重试"
        Parse -> "解析回复失败，请重试"
        Storage -> "保存数据时出错，请重试"
        ParamUnsupported -> "模型不支持当前思考模式，已尝试所有降级方案"
        ProviderChanged -> "模型供应商已切换，请重新生成"
        is Unknown -> "生成失败，请重试"
    }

    /** 是否可重试 */
    val retryable: Boolean get() = when (this) {
        ProviderMissing, Auth, InvalidAddress -> false
        Network, Timeout, RateLimited, PromptRead, Parse, Storage, ParamUnsupported,
        ProviderChanged, InsufficientBalance, ContentFiltered, ContextTooLong, ServerBusy -> true
        is Unknown -> true
    }

    /**
     * 是否属于"配置类"错误：换参数、重试都无意义，降级链必须立刻中断，
     * 用户得先去设置页。取代此前散落的 isConfigError(字符串) 判定。
     */
    val isConfigProblem: Boolean get() =
        this == ProviderMissing || this == Auth || this == InvalidAddress

    companion object {
        /** 从异常映射到 ReplyFailureKind——按异常类型判定，不解析 message 文本 */
        fun fromException(e: Throwable): ReplyFailureKind = when {
            // SocketTimeoutException extends IOException, so check it first
            e is java.net.SocketTimeoutException -> Timeout
            e is kotlinx.coroutines.TimeoutCancellationException -> Timeout
            e is java.net.UnknownHostException || e is java.net.SocketException ||
                e is java.net.ConnectException -> Network
            e is java.io.IOException -> Network
            else -> Unknown(e.message)
        }
    }
}
