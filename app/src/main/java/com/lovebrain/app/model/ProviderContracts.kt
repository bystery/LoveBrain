package com.lovebrain.app.model

import com.lovebrain.app.AppConfig

/**
 * Provider 侧的纯数据类型。
 *
 * 原先这两个类声明在 `data/DeepSeekRepository.kt` 里，后果不只是"放错文件"：
 * 任何想把网络层抽象成端口（AiGateway）的 domain 文件，都会在签名里被迫
 * `import com.lovebrain.app.data.ProviderRequestConfig` —— 也就是**端口自己**成了
 * 跨层依赖的来源，DIP 越抽象越脏。搬进 model 之后端口签名只引用 model 类型。
 *
 * 搬迁那一次是原样搬（字段、默认值、校验都没动）。之后超时档位那一笔给
 * `ProviderRequestConfig` 加了 [ProviderRequestConfig.generateTimeoutMs] 一项
 * ——带默认值，所以既有构造点与身份比对一个都没受影响。
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
 * 一次 API 请求从出生到结束的固定身份：ticket / apiKey / baseUrl / model / thinkingMode，
 * 再加上这一张工单的**等待预算** [generateTimeoutMs]。
 * 请求开始时一次性冻结，后续 build body / build URL / Authorization / retry / usage 计费
 * 全部只使用此快照，用户之后切工单不影响已启动的请求。
 *
 * @property generateTimeoutMs 主生成总超时（毫秒），由 `ProviderConfigResolver` 从工单档位
 *             （[com.lovebrain.app.GenerationTimeoutTier]，有界白名单）换算而来。
 *             默认值就是档位放开之前那个全局固定值 [AppConfig.GENERATE_TIMEOUT_MS]，
 *             所以老调用点（不点名这一项的构造）行为逐字不变。
 */
data class ProviderRequestConfig(
    val ticketId: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val thinkingMode: Int,
    val generateTimeoutMs: Long = AppConfig.GENERATE_TIMEOUT_MS
) {
    /**
     * 这一档对应的流式（SSE）读超时，单位秒——`CancellableHttpTransport.streamClient` 吃它。
     *
     * 为什么总超时放开了、读超时也必须跟着走：OkHttp 的读超时是"**两个 token 之间**最多等多久"，
     * 停在 120 秒的话，档位给到 180/300 也只是纸面值——慢服务只要有一次超过 120 秒没吐字就被掐断。
     *
     * 取 `maxOf` 而不是直接等于档位：**读超时不因为选了小档位而比历史值更短**。
     * 档位管的是"这一轮总共愿意等多久"（总超时），不该反过来把一次正常停顿判成故障。
     */
    val streamReadTimeoutSec: Long
        get() = maxOf(AppConfig.STREAM_READ_TIMEOUT_SEC, generateTimeoutMs / 1000)
}
