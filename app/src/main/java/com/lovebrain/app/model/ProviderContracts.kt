package com.lovebrain.app.model

/**
 * Provider 侧的纯数据类型。
 *
 * 原先这两个类声明在 `data/DeepSeekRepository.kt` 里，后果不只是"放错文件"：
 * 任何想把网络层抽象成端口（AiGateway）的 domain 文件，都会在签名里被迫
 * `import com.lovebrain.app.data.ProviderRequestConfig` —— 也就是**端口自己**成了
 * 跨层依赖的来源，DIP 越抽象越脏。搬进 model 之后端口签名只引用 model 类型。
 *
 * 这里是原样搬迁，没有改字段、没有改默认值、没有加校验。
 */

/**
 * 非流式生成的完整结果——content + finish_reason + 错误信息。
 *
 * [finishReason] 遵循 OpenAI 兼容规范：
 * - "stop"：模型自然结束
 * - "length"：达到 max_tokens 导致截断
 * - "content_filter"：安全过滤
 * - null：未返回或请求失败
 *
 * [error] 非 null 表示网络/解析异常（此时 content 为空）。
 */
data class RawGenerationResult(
    val content: String,
    val finishReason: String?,
    val error: Exception? = null
) {
    /** 是否因达到 token 上限而截断 */
    val isTruncatedByTokenLimit: Boolean get() = "length" == finishReason

    /** 是否因安全过滤截断 */
    val isContentFiltered: Boolean get() = "content_filter" == finishReason

    /** 请求是否成功（有内容且无错误） */
    val isSuccess: Boolean get() = error == null && content.isNotBlank()
}

/**
 * 请求级不可变配置快照。
 *
 * 一次 API 请求从出生到结束的固定身份：ticket / apiKey / baseUrl / model / thinkingMode。
 * 请求开始时一次性冻结，后续 build body / build URL / Authorization / retry / usage 计费
 * 全部只使用此快照，用户之后切工单不影响已启动的请求。
 */
data class ProviderRequestConfig(
    val ticketId: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val thinkingMode: Int
)
