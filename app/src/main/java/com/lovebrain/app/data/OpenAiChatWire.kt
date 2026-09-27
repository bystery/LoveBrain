package com.lovebrain.app.data

import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.util.L
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容协议的读写，全仓唯一一份：
 * 请求体里该出现哪些字段（含 thinking 参数族的降级形状）、一条 SSE 帧怎么读成
 * 内容增量与 usage、一个最小探测体长什么样，以及供应商回回来的错误词汇怎么翻成
 * typed [ProviderFailure]。
 *
 * 变化理由只有这一条：对面那套协议的字段名、帧格式或错误措辞变了，才动这里。
 * 「什么时候发出去/协程取消了怎么办」在 `CancellableHttpTransport`，
 * 「一次请求的身份从哪来」在 `ProviderConfigResolver`。
 */
internal object OpenAiChatWire {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /** 一条 SSE data 帧前缀 */
    val SSE_DATA_PREFIX = "data: "

    /** 流结束标记 */
    const val SSE_DONE = "[DONE]"

    /** thinking 参数 wire shape 降级候选列表 */
    private val THINKING_WIRE_SHAPES = listOf(
        "thinking.type",      // ① thinking.type = enabled + reasoning_effort = low
        "reasoning_effort",   // ② reasoning_effort = low（无 thinking.type 包装）
        "enable_thinking",    // ③ enable_thinking = true
        "none"                // ④ 不发送任何 thinking 族参数
    )

    /**
     * 一条帧的解析结果。
     * [content] 为该帧的文本增量（没有则 null）；
     * [usageRoot] 非 null 表示这条帧带 usage，需要按整颗 chunk 入账——
     * 计费口径要求把「带 usage 的那条 chunk」整颗传给入账方，搬前就是这个形状。
     */
    class SseFrame(val content: String?, val usageRoot: JsonObject?)

    /**
     * 用冻结的 [config] 构建请求体，禁止再读取实时工单/模型/thinkingMode。
     * model 来自 config.model，thinkingMode 来自 config.thinkingMode（thinkingOverride 优先）。
     */
    fun buildRequestBody(
        config: ProviderRequestConfig,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        stream: Boolean,
        thinkingOverride: Int? = null,
        thinkingShapeIndex: Int = 0
    ): String {
        val body = buildJsonObject {
            // model 来自 config 快照，不再读 getActiveModel()
            put("model", config.model)
            put("temperature", temperature)
            if (stream) {
                put("stream", true)
                // 让流式响应末尾带 usage 字段（用于统计 token 消耗）
                putJsonObject("stream_options") { put("include_usage", true) }
            }
            // thinkingMode 来自 config 快照，thinkingOverride 优先
            val effectiveThinking = thinkingOverride ?: config.thinkingMode
            val tMode = effectiveThinking.coerceIn(0, 1)
            if (tMode != effectiveThinking) {
                L.w("⚠️ thinkingMode=$effectiveThinking 无效，已回退为 $tMode")
            }
            if (thinkingOverride != null && thinkingOverride != config.thinkingMode) {
                L.w("⚡ 超时降级：thinkingMode ${config.thinkingMode} → $tMode")
            }
            // 两态：0=直出（disabled） 1=思考（enabled + reasoning_effort low）
            when (tMode) {
                // 直出模式也走降级链——前 3 个候选才发 disabled；
                // thinkingShapeIndex == 3 时不发送任何 thinking 族参数
                0 -> if (thinkingShapeIndex < 3) {
                    putJsonObject("thinking") { put("type", "disabled") }
                }
                1 -> {
                    // thinking 两态候选 wire shape
                    when (THINKING_WIRE_SHAPES.getOrNull(thinkingShapeIndex)) {
                        "thinking.type" -> {
                            putJsonObject("thinking") {
                                put("type", "enabled")
                                put("reasoning_effort", "low")
                            }
                        }
                        "reasoning_effort" -> {
                            put("reasoning_effort", "low")
                        }
                        "enable_thinking" -> {
                            put("enable_thinking", true)
                        }
                        "none" -> { /* 不发送任何 thinking 族参数 */ }
                    }
                }
            }
            putJsonObject("response_format") { put("type", "text") }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }
        return body.toString()
    }

    /**
     * 使用冻结的 [config] 构建请求，禁止再读取实时工单。
     * URL 来自 config.baseUrl（已在配置解析阶段 normalize），Authorization 来自 config.apiKey。
     */
    fun buildRequest(config: ProviderRequestConfig, body: String): Request =
        buildBearerRequest(config.baseUrl, config.apiKey, body)

    /**
     * 一个 Bearer 鉴权的 POST JSON 请求。
     * 连接探测走这条（那次还没有 config 快照），正式生成走 [buildRequest]。
     */
    fun buildBearerRequest(url: String, apiKey: String, body: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

    /**
     * 连通性探测用的极小请求体：一句 "hi"、8 个 token 上限。
     * [thinkingDisabled] 决定是否带上 thinking.type=disabled——
     * 供应商不支持这个参数时要能去掉它再试一次。
     */
    fun minimalProbeBody(model: String, thinkingDisabled: Boolean): String = buildJsonObject {
        put("model", model)
        put("temperature", 0.0)
        put("max_tokens", 8)
        if (thinkingDisabled) {
            putJsonObject("thinking") { put("type", "disabled") }
        }
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", "hi")
            })
        })
    }.toString()

    /**
     * SSE 的一行里取出 data 载荷：空行、非 `data: ` 行都返回 null（调用方跳过本行）。
     * 返回 "[DONE]" 表示流结束，由调用方负责收尾。
     */
    fun sseDataPayload(line: String): String? {
        if (line.isBlank()) return null
        if (!line.startsWith(SSE_DATA_PREFIX)) return null
        return line.removePrefix(SSE_DATA_PREFIX).trim()
    }

    /**
     * 解析一条 data 载荷：文本增量 + 是否带 usage。
     * 载荷不是合法 JSON 时按异常抛出，由流式循环逐帧吞掉（坏帧不终止整条流）。
     *
     * 顺序要紧：带 usage 的那一帧 `choices` 是空数组，取增量必然抛——
     * usage 必须先把整颗 chunk 交出去，账单不能被那一抛吞掉（搬前就是这个次序，逐字保持）。
     */
    fun parseChunk(payload: String): SseFrame {
        val chunk = json.parseToJsonElement(payload).jsonObject
        val usageRoot = if (chunk["usage"] != null) chunk else null
        val content = runCatching {
            val contentElement = chunk["choices"]?.jsonArray
                ?.get(0)?.jsonObject
                ?.get("delta")?.jsonObject
                ?.get("content")
            // JsonNull.content 返回字符串 "null"，必须排除
            if (contentElement == null || contentElement is kotlinx.serialization.json.JsonNull)
                null else contentElement.jsonPrimitive.content
        }.getOrNull()
        return SseFrame(content = content, usageRoot = usageRoot)
    }

    /**
     * 全仓唯一的错误分类出口：把供应商/网络的原始响应读成一个 typed [ProviderFailure]。
     *
     * 供应商的英文词汇只在这里被解释一次；下游按 kind 决定控制流、按 message 展示，
     * 供应商的原始词汇只在这里解释一次，下游不再从字符串前缀或异常 message 反推类型。
     * 英文原文保留在 logcat（L.w）供排查，不作为 UI 输入。
     *
     * `DeepSeekRepository.classifyApiError` 是它的对口委托，不另写第二份判据。
     */
    internal fun classifyApiError(raw: String, code: Int = 0): ProviderFailure {
        val s = raw.lowercase()
        L.w("provider error classified: code=$code rawLen=${raw.length}")
        // 参数不支持族 → 允许换一种 thinking wire shape 再试（是否真的可换由调用方决定）
        if (s.contains("unknown parameter") ||
            s.contains("unsupported") ||
            s.contains("invalid field")) {
            val field = when {
                s.contains("thinking") -> "thinking"
                s.contains("reasoning_effort") -> "reasoning_effort"
                s.contains("enable_thinking") -> "enable_thinking"
                else -> null
            }
            return ProviderFailure(
                kind = ReplyFailureKind.ParamUnsupported,
                thinkingParamRejected = true,
                rejectedParameter = field
            )
        }
        // 400 且 body 提到 thinking 族：同为参数不支持，但换参数已无意义
        if (code == 400 &&
            (s.contains("thinking") || s.contains("reasoning_effort") || s.contains("enable_thinking"))) {
            return ProviderFailure(
                ReplyFailureKind.ParamUnsupported,
                "模型不支持思考模式参数，请切换直出模式后重试"
            )
        }
        val kind = when {
            s.contains("insufficient balance") || s.contains("insufficient_balance") || code == 402 ->
                ReplyFailureKind.InsufficientBalance
            s.contains("invalid api key") || s.contains("authentication credentials") ||
                code == 401 || code == 403 -> ReplyFailureKind.Auth
            s.contains("rate limit") || s.contains("too many requests") ||
                s.contains("max concurrency") || code == 429 -> ReplyFailureKind.RateLimited
            s.contains("content_filter") || s.contains("content filter") || s.contains("sensitive") ->
                ReplyFailureKind.ContentFiltered
            s.contains("context_length") || s.contains("context length") ||
                s.contains("too long") || s.contains("max_tokens") -> ReplyFailureKind.ContextTooLong
            s.contains("server is busy") || s.contains("overloaded") ||
                s.contains("server_error") || code == 503 -> ReplyFailureKind.ServerBusy
            s.contains("timeout") || s.contains("timed out") -> ReplyFailureKind.Timeout
            s.contains("unable to resolve host") || s.contains("failed to connect") ||
                s.contains("network is unreachable") -> ReplyFailureKind.Network
            code == 502 -> ReplyFailureKind.ServerBusy
            code in 500..599 -> ReplyFailureKind.ServerBusy
            else -> ReplyFailureKind.Unknown(raw.take(200))
        }
        // 502 / 其它 5xx 保留原有更具体的话术，其余用 kind 的固定文案
        val message = when {
            code == 502 && kind == ReplyFailureKind.ServerBusy -> "网关错误，稍后再试"
            code in 500..599 && kind == ReplyFailureKind.ServerBusy &&
                !s.contains("server is busy") && !s.contains("overloaded") &&
                !s.contains("server_error") && code != 503 -> "服务暂时开小差，稍后再试"
            else -> kind.userMessage
        }
        return ProviderFailure(kind, message)
    }
}
