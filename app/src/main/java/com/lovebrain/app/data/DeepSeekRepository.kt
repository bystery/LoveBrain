package com.lovebrain.app.data

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ProviderFailureException
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.util.L
import com.lovebrain.app.util.OpenAiChatEndpointResolver
import com.lovebrain.app.util.UsagePricer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * API 请求统计数据快照。
 * 用于监控 API 调用量、成功率和 token 消耗。
 */
data class ApiStats(
    val totalRequests: Int = 0,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val totalPromptTokens: Long = 0L,
    val totalCompletionTokens: Long = 0L,
    val totalCacheHitTokens: Long = 0L,
    val totalCacheMissTokens: Long = 0L
) {
    val successRate: Float get() = if (totalRequests == 0) 0f else successCount.toFloat() / totalRequests
    val totalTokens: Long get() = totalPromptTokens + totalCompletionTokens
    override fun toString(): String = "ApiStats(req=$totalRequests, ok=$successCount, fail=$failCount, " +
        "prompt=${totalPromptTokens}(hit=$totalCacheHitTokens,miss=$totalCacheMissTokens), " +
        "completion=$totalCompletionTokens, rate=${(successRate * 100).toInt()}%)"
}

/**
 * 计费事件来源标识（）。
 * FOREGROUND = 悬浮窗四流程（回复/谈心/锦囊/主动发）的流式请求
 * BACKGROUND = 后台 generateRaw（向量重估/经验提取/画像 reflect/Onboarding）
 */
enum class CostScope {
    FOREGROUND,
    BACKGROUND
}

/**
 * 计费事件：logUsage 过双条件计费后发射，VM 聚合今日累计/本次花费。
 * timestampMs 供消费侧跨天滚动判定。
 * scope 标识来源（）：VM 只将 FOREGROUND 费用写入"本次花费"。
 */
data class UsageCostEvent(
    val yuan: Double,
    val timestampMs: Long,
    val scope: CostScope
)

/**
 * 连接测试结果。
 *
 * 保存时自动探测 endpoint，成功后 [resolvedUrl] 携带完整 /chat/completions 地址。
 * 失败时 [message] 提供具体原因供 UI 展示。
 */
data class ConnectionTestResult(
    val success: Boolean,
    val resolvedUrl: String? = null,
    val message: String? = null
)

/**
 * DeepSeek API 网络层 v2。
 * - 流式请求使用 callbackFlow + enqueue，协程取消时自动中断网络请求。
 * - JSON 解析使用 kotlinx.serialization，Kotlin 默认值正确生效。
 * - 内置请求统计计数器（线程安全 AtomicInteger/AtomicLong），摘要进日志（logStatsSummary）。
 */
class DeepSeekRepository(private val securePrefs: SecurePrefs) : AiGateway {

    // ═══════════ 工单系统工具方法（ - ）════════════

    /** 获取所有工单列表 */
    fun getAllTickets(): List<ProviderTicket> {
        return securePrefs.getWorkerTickets()
    }

    /** 获取激活工单 */
    fun getActiveTicket(): ProviderTicket? {
        val ticketId = securePrefs.activeTicketId ?: return null
        return getAllTickets().find { it.id == ticketId }
    }

    /** 获取激活工单的 API Key */
    fun getActiveApiKey(): String? {
        val ticketId = securePrefs.activeTicketId ?: return null
        return securePrefs.getWorkerApiKey(ticketId)
    }

    /** 获取激活工单绑定的模型名（一工单 = 一模型，） */
    fun getActiveModel(): String? {
        return getActiveTicket()?.model
    }

    /**
     * 设置激活工单
     * @param ticketId 工单 ID
     */
    fun activateTicket(ticketId: String) {
        securePrefs.activeTicketId = ticketId
    }

    /** 标准化 Base URL（ 脏数据拦截；多模型批主人原话：不补全任何路径——填什么用什么） */
    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        //  脏数据拦截
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))
            throw IllegalArgumentException("地址必须以 http:// 或 https:// 开头")
        if (trimmed.contains("sk-") || trimmed.contains(" "))
            throw IllegalArgumentException("检测到 API Key 误填入地址栏，请检查")
        // http:// 仅放行 loopback 主机，对外强制 https（扼制点：生成/测试连接两出口唯一必经）
        HttpsTrustGuard.enforce(trimmed)
        return trimmed
    }

    /**
     * 一次性解析当前激活工单为不可变请求配置快照。
     * 返回 null = 配置不完整（调用方负责发 ProviderMissing）。
     * 返回后整次请求只使用此快照，不再调 getActiveTicket/getActiveApiKey/getActiveModel。
     * normalizeBaseUrl（含 HttpsTrustGuard）在此完成，地址不合法时抛 IllegalArgumentException。
     *
     * 关键：开头一次性读取 securePrefs.activeTicketId，后续全部围绕这个固定 ID 取值。
     * ticket 和 apiKey 必须来自同一个 ticketId，禁止分两次读取 activeTicketId。
     */
    private fun resolveRequestConfig(): ProviderRequestConfig? {
        val ticketId = securePrefs.activeTicketId ?: return null
        return resolveRequestConfigFor(ticketId)
    }

    /**
     * 按指定 ticketId 解析请求配置。
     *
     * 生成请求在 GenerationInput 里冻结了 ticketId，Engine 必须按那个 ticketId 取配置，
     * 不能在准备阶段之后再回读 activeTicketId——否则用户中途切工单，
     * 本轮就会用一套从未被冻结、也从未被校验过的配置发出去。
     */
    private fun resolveRequestConfigFor(ticketId: String): ProviderRequestConfig? {
        if (ticketId.isBlank()) return null

        val ticket = getAllTickets().firstOrNull { it.id == ticketId } ?: return null
        if (ticket.id.isBlank() || ticket.baseUrl.isBlank() || ticket.model.isBlank()) return null

        val apiKey = securePrefs.getWorkerApiKey(ticketId)?.takeIf { it.isNotBlank() } ?: return null

        // normalizeBaseUrl 包含脏数据拦截 + HttpsTrustGuard.enforce
        val baseUrl = normalizeBaseUrl(ticket.baseUrl)
        val thinkingMode = ticket.thinkingMode ?: securePrefs.thinkingMode
        return ProviderRequestConfig(
            ticketId = ticketId,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = ticket.model,
            thinkingMode = thinkingMode
        )
    }

    /**
     * 公开快照入口——供 GenerationEngine 在整轮生成开始时冻结 Provider 身份。
     * 后续所有 retry attempt 使用同一个 ProviderRequestConfig，不因用户切工单而漂移。
     * 返回 null = 配置不完整（调用方负责处理）。
     */
    override fun snapshotProviderConfig(): ProviderRequestConfig? =
        try { resolveRequestConfig() } catch (e: IllegalArgumentException) { null }

    /**
     * 按 GenerationInput 里冻结的 ticketId 取请求配置。
     *
     * 与 [snapshotProviderConfig] 的区别是它绝不回读 activeTicketId：
     * 用户中途换工单时这里返回的是**冻结时那张工单**的配置，
     * Engine 再用非敏感字段比对，任何一项对不上就失败，而不是悄悄换供应商发请求。
     */
    override fun configForTicket(ticketId: String): ProviderRequestConfig? =
        try { resolveRequestConfigFor(ticketId) } catch (e: IllegalArgumentException) { null }

    // ═══════════ API 统计计数器（线程安全） ═══════════

    /** API 统计计数器（线程安全） */
    private val _totalRequests = AtomicInteger(0)
    private val _successCount = AtomicInteger(0)
    private val _failCount = AtomicInteger(0)
    private val _totalPromptTokens = AtomicLong(0L)
    private val _totalCompletionTokens = AtomicLong(0L)
    private val _totalCacheHitTokens = AtomicLong(0L)
    private val _totalCacheMissTokens = AtomicLong(0L)

    private val _stats = MutableStateFlow(ApiStats())

    /** 计费事件流（logUsage 双条件命中后 tryEmit；VM 订阅聚合） */
    private val _costEvents = MutableSharedFlow<UsageCostEvent>(replay = 0, extraBufferCapacity = 8)
    val costEvents: Flow<UsageCostEvent> = _costEvents.asSharedFlow()

    /** 从持久化存储恢复统计数据 */
    fun restoreStats() {
        securePrefs.loadApiStats()?.let { json ->
            runCatching {
                val obj = kotlinx.serialization.json.Json.decodeFromString<
                    kotlinx.serialization.json.JsonObject>(json)
                _totalRequests.set(obj["totalRequests"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                _successCount.set(obj["successCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                _failCount.set(obj["failCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                _totalPromptTokens.set(obj["totalPromptTokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L)
                _totalCompletionTokens.set(obj["totalCompletionTokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L)
                _totalCacheHitTokens.set(obj["totalCacheHitTokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L)
                _totalCacheMissTokens.set(obj["totalCacheMissTokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L)
                refreshStats()
                L.w("API stats restored: ${_stats.value}")
            }.onFailure { L.w("恢复 API 统计失败：${it.javaClass.simpleName}") }
        }
    }

    /** 持久化统计数据 */
    private fun persistStats() {
        val s = _stats.value
        val json = """{"totalRequests":${s.totalRequests},"successCount":${s.successCount},"failCount":${s.failCount},"totalPromptTokens":${s.totalPromptTokens},"totalCompletionTokens":${s.totalCompletionTokens},"totalCacheHitTokens":${s.totalCacheHitTokens},"totalCacheMissTokens":${s.totalCacheMissTokens}}"""
        securePrefs.saveApiStats(json)
    }

    /** 刷新统计快照 */
    private fun refreshStats() {
        _stats.value = ApiStats(
            totalRequests = _totalRequests.get(),
            successCount = _successCount.get(),
            failCount = _failCount.get(),
            totalPromptTokens = _totalPromptTokens.get(),
            totalCompletionTokens = _totalCompletionTokens.get(),
            totalCacheHitTokens = _totalCacheHitTokens.get(),
            totalCacheMissTokens = _totalCacheMissTokens.get()
        )
        persistStats()
    }

    init {
        restoreStats()
    }

    /** 输出统计摘要到日志（每次生成后调用） */
    fun logStatsSummary() {
        val s = _stats.value
        L.w("=== API Stats Summary ===")
        L.w("  Requests: ${s.totalRequests} (ok=${s.successCount}, fail=${s.failCount}, rate=${(s.successRate * 100).toInt()}%)")
        L.w("  Tokens: prompt=${s.totalPromptTokens}(hit=${s.totalCacheHitTokens},miss=${s.totalCacheMissTokens}) completion=${s.totalCompletionTokens} total=${s.totalTokens}")
        val cacheRate = if (s.totalPromptTokens > 0) (s.totalCacheHitTokens.toFloat() / s.totalPromptTokens * 100).toInt() else 0
        L.w("  Cache hit rate: $cacheRate%")
        L.w("=========================")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(AppConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(AppConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(AppConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    /** 流式专用 client：更长读超时（SSE 间隔可能较大） */
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(AppConfig.STREAM_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(AppConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // ═══════════ 流式生成（主生成用） ═══════════

    /**
     * 流式 chat completion。使用 callbackFlow + enqueue 实现：
     * - 协程取消时自动 call.cancel()，中断网络请求
     * - 逐块发射 StreamEvent.Chunk
     * - 流结束后发射 StreamEvent.Complete（含完整累积文本）
     *
     * 接受外部冻结的 [config] 快照（由 GenerationEngine 在整轮生成开始时 snapshot）。
     * 如果调用方未传 config，则每次调用自行 resolve 一次（向后兼容）。
     *
     * @param config 请求级不可变配置快照（）。传 null 时内部自行 resolve。
     * @param thinkingOverride 超时降级用：非 null 时覆盖 config 中的 thinkingMode。
     *        降级链：thinking(enabled) → timeout → thinking(disabled=0) → retry → fail
     * @param thinkingShapeIndex thinking 参数 wire shape 索引（ 降级链用）
     */
    override fun generateStream(
        systemPrompt: String,
        userPrompt: String,
        thinkingOverride: Int?,
        thinkingShapeIndex: Int,
        config: ProviderRequestConfig?
    ): Flow<StreamEvent> = callbackFlow {
        // 一次性冻结请求配置快照——后续 build body / build URL / Authorization / logUsage 全用此快照
        val resolvedConfig = config ?: try {
            resolveRequestConfig()
        } catch (e: IllegalArgumentException) {
            // normalizeBaseUrl / HttpsTrustGuard 脏数据拦截
            trySend(
                StreamEvent.Error(
                    ProviderFailure(
                        kind = ReplyFailureKind.InvalidAddress,
                        message = e.message?.takeIf { it.isNotBlank() }
                            ?: ReplyFailureKind.InvalidAddress.userMessage
                    ),
                    ""
                )
            ).getOrThrow()
            close()
            return@callbackFlow
        }
        if (resolvedConfig == null) {
            // 区分具体缺失项给精确提示
            val ticket = getActiveTicket()
            val failure = when {
                ticket == null -> ProviderFailure(
                    ReplyFailureKind.ProviderMissing, "请先配置一个模型供应商")
                ticket.id.isNullOrBlank() -> ProviderFailure(
                    ReplyFailureKind.ProviderMissing, "工单 ID 无效，请重新激活")
                ticket.baseUrl.isNullOrBlank() -> ProviderFailure(
                    ReplyFailureKind.ProviderMissing, "接口地址未填写，请在设置中补充")
                ticket.model.isNullOrBlank() -> ProviderFailure(
                    ReplyFailureKind.ProviderMissing, "模型名称未配置，请在设置中补充")
                else -> ProviderFailure(
                    ReplyFailureKind.Auth, "API Key 缺失，请检查工单配置")
            }
            trySend(StreamEvent.Error(failure, "")).getOrThrow()
            close()
            return@callbackFlow
        }

        _totalRequests.incrementAndGet()
        refreshStats()

        val requestBody = buildRequestBodyWithConfig(
            resolvedConfig, systemPrompt, userPrompt, AppConfig.TEMPERATURE_MAIN, stream = true,
            thinkingOverride = thinkingOverride,
            thinkingShapeIndex = thinkingShapeIndex
        )
        val request = buildRequestWithConfig(resolvedConfig, requestBody)
        val accumulated = StringBuilder()

        val call = streamClient.newCall(request)
        val t2 = System.currentTimeMillis()
        L.w("PERF t2 request enqueued")

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return // 主动取消，不报错
                _failCount.incrementAndGet()
                refreshStats()
                // 日志脱敏，只记 host 和消息长度
                L.w("API onFailure host=${request.url.host} msgLen=${e.message?.length}")
                trySend(StreamEvent.Error(
                    classifyApiError(e.message ?: ""),
                    accumulated.toString()
                ))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    when {
                        resp.code == 401 -> {
                            _failCount.incrementAndGet()
                            refreshStats()
                            trySend(StreamEvent.Error(ProviderFailure(ReplyFailureKind.Auth), ""))
                            close()
                            return
                        }
                        resp.code == 429 -> {
                            _failCount.incrementAndGet()
                            refreshStats()
                            trySend(StreamEvent.Error(ProviderFailure(ReplyFailureKind.RateLimited), ""))
                            close()
                            return
                        }
                        !resp.isSuccessful -> {
                            _failCount.incrementAndGet()
                            refreshStats()
                            val errBody = resp.body?.string()?.take(500) ?: ""
                            // 日志脱敏，响应体只记长度
                            L.w("API error code=${resp.code} bodyLen=${errBody.length}")
                            trySend(StreamEvent.Error(classifyApiError(errBody, resp.code), ""))
                            close()
                            return
                        }
                    }

                    var streamUsage: com.lovebrain.app.model.StreamUsage? = null
                    try {
                        val source = resp.body?.source()
                            ?: throw IllegalStateException("响应体为空")
                        var firstChunkLogged = false
                        while (!call.isCanceled()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            if (!line.startsWith("data: ")) continue

                            val payload = line.removePrefix("data: ").trim()
                            if (payload == "[DONE]") break

                            val chunkContent = runCatching {
                                val chunk = json.parseToJsonElement(payload).jsonObject
                                // 流式末尾带 usage 的 chunk（include_usage=true 时最后一条）
                                // logUsage 使用请求开始时冻结的 config + FOREGROUND scope
                                if (chunk["usage"] != null) streamUsage = logUsage(chunk, resolvedConfig, CostScope.FOREGROUND)
                                val contentElement = chunk["choices"]?.jsonArray
                                    ?.get(0)?.jsonObject
                                    ?.get("delta")?.jsonObject
                                    ?.get("content")
                                // JsonNull.content 返回字符串 "null"，必须排除
                                if (contentElement == null ||
                                    contentElement is kotlinx.serialization.json.JsonNull) null
                                else contentElement.jsonPrimitive.content
                            }.getOrNull()

                            if (!chunkContent.isNullOrEmpty()) {
                                if (!firstChunkLogged) {
                                    firstChunkLogged = true
                                    L.w("PERF t3 first chunk (+${System.currentTimeMillis() - t2}ms)")
                                }
                                accumulated.append(chunkContent)
                                //  高频逐 token 发射，trySend 在 channel 满时静默丢弃导致丢字；
                                // trySendBlocking 在 IO 线程阻塞等待消费者，背压正确传导
                                trySendBlocking(StreamEvent.Chunk(chunkContent))
                            }
                        }

                        if (!call.isCanceled()) {
                            _successCount.incrementAndGet()
                            refreshStats()
                            L.w("PERF t4 stream complete (+${System.currentTimeMillis() - t2}ms, ${accumulated.length} chars)")
                            L.w("API stats: ${_stats.value}")
                            logStatsSummary()
                            // 流结束发射完整文本和 usage，同样用 trySendBlocking 防背压丢字
                            trySendBlocking(StreamEvent.Complete(accumulated.toString(), streamUsage))
                        }
                    } catch (e: Exception) {
                        if (!call.isCanceled()) {
                            _failCount.incrementAndGet()
                            refreshStats()
                            // 日志脱敏，只记消息长度
                            L.w("API stream error msgLen=${e.message?.length}")
                            trySend(StreamEvent.Error(
                                classifyApiError(e.message ?: ""),
                                accumulated.toString()
                            ))
                        }
                        streamUsage = null
                    }
                    close()
                }
            }
        })

        // 必须放在 callbackFlow 最后一行：挂起直到 flow 被取消，取消时中断网络请求
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /** 将累积文本解析为 LoveBrainResponse（单次调用：response + analysis）
     * R10: 至少一个真实非空风格才可作为回复成功；全空 response 被拒绝。
     * b3-9: 解析降级——当 response 全空但 directions 有非空项时，使用 directions 作为回复。 */
    override fun parseReplyResponse(content: String): LoveBrainResponse {
        val jsonStr = com.lovebrain.app.util.Jsons.extractJsonBlock(content)
            ?: throw IllegalStateException("模型未返回有效 JSON，请重试")

        val resp = runCatching { json.decodeFromString<LoveBrainResponse>(jsonStr) }
            .getOrElse { throw IllegalStateException("解析回复失败，请重试") }

        // R10: 不再用 resp.schemes.isEmpty() 验证——toSchemes 永远返回 4 条（空 reply = 本轮不适合）。
        // 改为检查至少一个非空 reply 才算成功。
        // b3-9: schemes 访问器已内置 directions 降级逻辑
        val hasNonEmpty = resp.schemes.any { it.reply.isNotBlank() }
        if (!hasNonEmpty) {
            throw IllegalStateException("返回格式不完整：所有回复方案均为空，请重试")
        }
        return resp
    }

    // ═══════════ 非流式生成（辅助任务用） ═══════════

    /**
     * 纯文本生成（不解析为 LoveBrainResponse），用于经验提取等辅助任务。
     * 使用 suspendCancellableCoroutine + enqueue，支持协程取消。
     *
     * 内部自行 resolve 配置快照，请求身份冻结后不再读实时 activeTicket/Model。
     * CancellationException 重新抛出，不计入 failCount。
     */
    override suspend fun generateRaw(systemPrompt: String, userPrompt: String): String {
    val config = try { resolveRequestConfig() } catch (e: IllegalArgumentException) { return "" }
    ?: return ""
    return generateRaw(config, systemPrompt, userPrompt)
}

/**
 * 带元数据的非流式生成便捷重载（内部自行 resolve 配置快照）。
 * 供 KnowledgeTriggerCoordinator 等不持有 config 快照的调用方使用。
 */
    override suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String): RawGenerationResult {
    val config = try { resolveRequestConfig() } catch (e: IllegalArgumentException) {
        return RawGenerationResult(content = "", finishReason = null, error = e)
    } ?: return RawGenerationResult(content = "", finishReason = null)
    return generateRawWithMetadata(config, systemPrompt, userPrompt)
}

    /**
     * 接受外部冻结 config 的 generateRaw overload。
     * 供调用方在已持有 config 快照时复用，确保请求身份一致。
     */
    suspend fun generateRaw(
        config: ProviderRequestConfig,
        systemPrompt: String,
        userPrompt: String
    ): String = generateRawWithMetadata(config, systemPrompt, userPrompt).content

    /**
     * 带元数据的非流式生成——返回 content + finish_reason。
     *
     * finish_reason 可能值（OpenAI 兼容规范）：
     * - "stop"：正常结束
     * - "length"：达到 max_tokens 导致截断
     * - "content_filter"：安全过滤
     * - null / 其他：未知
     *
     * 供画像 reflect 等需要判断是否截断的调用方使用。
     * 传统 generateRaw 委托此方法，只取 content。
     */
    suspend fun generateRawWithMetadata(
        config: ProviderRequestConfig,
        systemPrompt: String,
        userPrompt: String
    ): RawGenerationResult {
        _totalRequests.incrementAndGet()
        val requestBody = buildRequestBodyWithConfig(
            config, systemPrompt, userPrompt, AppConfig.TEMPERATURE_RAW, stream = false
        )
        val request = buildRequestWithConfig(config, requestBody)

        // CancellationException 必须重新抛出，不计入 failCount
        return try {
            val respBody = executeRequest(client, request)
            val root = json.parseToJsonElement(respBody).jsonObject
            // 统计 token 用量 + 计费使用请求开始时冻结的 config + BACKGROUND scope
            runCatching { logUsage(root, config, CostScope.BACKGROUND) }
            _successCount.incrementAndGet()
            refreshStats()
            val choice = root["choices"]?.jsonArray?.get(0)?.jsonObject
            val content = choice
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content ?: ""
            val finishReason = choice?.get("finish_reason")?.let {
                if (it is kotlinx.serialization.json.JsonNull) null
                else it.jsonPrimitive.content
            }
            RawGenerationResult(content = content, finishReason = finishReason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _failCount.incrementAndGet()
            refreshStats()
            RawGenerationResult(content = "", finishReason = null, error = e)
        }
    }

    // ═══════════ API 连接测试 ═══════════

    /**
     * 带 endpoint 自动探测的连接测试（保存供应商 + 测试连接按钮共用）。
     *
     * 用 [OpenAiChatEndpointResolver] 生成候选 URL 列表，逐个探测：
     * - 404 / 405 / 400 → 路径不对，继续下一个候选
     * - 401 / 403 → 可能是路径不对导致网关拦截，继续下一个候选；若所有候选都返回 auth 错误，则返回 Key/权限原因
     * - 429 → 限流，立即停止
     * - 5xx → 供应商服务异常，立即停止
     * - DNS / 超时 → 网络问题，立即停止
     *
     * 成功时 [ConnectionTestResult.resolvedUrl] 携带完整 /chat/completions 地址，
     * 调用方（SetupViewModel）将其存入 ProviderTicket.baseUrl。
     * 正式生成阶段不再进行 URL 猜测，ProviderRequestConfig snapshot 逻辑完全不变。
     */
    suspend fun testConnectionWithProbe(
        apiKey: String,
        model: String,
        baseUrl: String
    ): ConnectionTestResult {
        if (apiKey.isBlank() || baseUrl.isBlank()) {
            return ConnectionTestResult(success = false, message = "API Key 和接口地址不能为空")
        }
        val testModel = model.ifBlank { AppConfig.DEFAULT_MODEL }
        // normalizeBaseUrl 含脏数据拦截 + HttpsTrustGuard.enforce
        val normalizedBase = try {
            normalizeBaseUrl(baseUrl)
        } catch (e: IllegalArgumentException) {
            return ConnectionTestResult(success = false, message = e.message)
        }

        val candidates = OpenAiChatEndpointResolver.candidates(normalizedBase)
        L.w("testConnectionWithProbe: candidates=${candidates.size} base=$normalizedBase")

        var lastAuthError: String? = null
        for (url in candidates) {
            L.w("testConnectionWithProbe: trying $url")
            val result = probeEndpoint(apiKey, testModel, url)
            when (result) {
                is ProbeResult.Success -> {
                    L.w("testConnectionWithProbe: resolved=$url")
                    return ConnectionTestResult(success = true, resolvedUrl = url)
                }
                is ProbeResult.NotFound -> {
                    // 404 / 405 / 400 → 路径不对，继续下一个候选
                    L.w("testConnectionWithProbe: ${result.code} on $url, trying next candidate")
                    continue
                }
                is ProbeResult.AuthError -> {
                    // 401/403 → 可能是路径不对导致网关拦截，继续下一个候选，但记录原因
                    L.w("testConnectionWithProbe: auth error on $url: ${result.message}, trying next candidate")
                    lastAuthError = result.message
                    continue
                }
                is ProbeResult.Fatal -> {
                    // 429/5xx/DNS/超时 → 立即停止
                    return ConnectionTestResult(success = false, message = result.message)
                }
            }
        }
        // 所有候选都尝试完毕：如果有 auth 错误，优先返回 auth 原因（更可能是 Key 问题）
        return ConnectionTestResult(
            success = false,
            message = lastAuthError ?: "无法连接到接口地址，请检查 URL 是否正确"
        )
    }

    /**
     * 测试 API 连接是否有效：发送一个最小请求验证 apiKey/model/baseUrl 是否正确。
     * 不使用 securePrefs 中的配置，而是用传入的参数测试（允许用户先测试再保存）。
     *
     * 请求体接上降级链——初始带 thinking.type=disabled（直出），
     * 参数被供应商拒掉就去掉 thinking 参数重试一次（最多 2 次尝试）。
     */
    suspend fun testConnection(apiKey: String, model: String, baseUrl: String): Boolean {
        if (apiKey.isBlank() || baseUrl.isBlank()) return false
        val testModel = model.ifBlank { AppConfig.DEFAULT_MODEL }
        // 使用 normalizeBaseUrl 统一处理
        val url = normalizeBaseUrl(baseUrl)

        // 降级链：最多 2 次尝试（初始带 thinking.type=disabled + 参数被拒后去掉 thinking）
        var thinkingShapeIndex = 0
        var attemptsUsed = 0

        while (attemptsUsed < 2) {
            attemptsUsed++
            val body = buildJsonObject {
                put("model", testModel)
                put("temperature", 0.0)
                put("max_tokens", 8)
                if (thinkingShapeIndex == 0) {
                    putJsonObject("thinking") { put("type", "disabled") }
                }
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", "hi")
                    })
                })
            }.toString()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            try {
                val respBody = executeRequest(client, request)
                val root = json.parseToJsonElement(respBody).jsonObject
                return root["choices"] != null
            } catch (e: Exception) {
                // 取消信号必须重抛
                if (e is CancellationException) throw e
                // 参数被供应商拒掉 → 去掉 thinking 参数重试一次
                val failure = classifyApiError(e.message ?: "")
                if (failure.thinkingParamRejected && thinkingShapeIndex < 1) {
                    thinkingShapeIndex++
                    L.w("testConnection: thinking 参数不支持，降级到不带 thinking 参数重试")
                    continue
                }
                return false
            }
        }
        return false
    }

    /**
     * endpoint 探测结果密封类。
     * Success = 连接成功；
     * NotFound = 404/405/400 路径不对，继续下一个候选；
     * AuthError = 401/403 可能是路径不对导致的网关拦截，继续下一个候选，但记录原因以防所有候选都失败；
     * Fatal = 429/5xx/DNS/超时 等不可恢复错误，立即停止。
     */
    private sealed class ProbeResult {
        data class Success(val url: String) : ProbeResult()
        data class NotFound(val code: Int) : ProbeResult()
        data class AuthError(val message: String) : ProbeResult()
        data class Fatal(val message: String) : ProbeResult()
    }

    /**
     * 对单个 URL 发送极小请求探测。
     * 404/405/400 → NotFound（路径不对，继续下一个候选）
     * 401/403 → AuthError（可能是路径不对导致网关拦截，继续下一个候选，但记录原因）
     * 429 → Fatal（限流）
     * 5xx → Fatal（供应商服务异常）
     * DNS/超时 → Fatal（网络问题）
     * 成功 → Success
     */
    private suspend fun probeEndpoint(apiKey: String, model: String, url: String): ProbeResult {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.0)
            put("max_tokens", 8)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "hi")
                })
            })
        }.toString()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            val respBody = executeRequestWithCode(client, request)
            val root = json.parseToJsonElement(respBody.body).jsonObject
            if (root["choices"] != null) {
                ProbeResult.Success(url)
            } else {
                ProbeResult.Fatal("接口返回异常，请检查模型名称是否正确")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpCodeException) {
            when (e.code) {
                404, 405, 400 -> ProbeResult.NotFound(e.code)
                401 -> ProbeResult.AuthError("API Key 无效，请检查密钥")
                403 -> ProbeResult.AuthError("没有权限访问该接口，请检查 API Key 权限")
                429 -> ProbeResult.Fatal("请求过于频繁，请稍后再试")
                in 500..599 -> ProbeResult.Fatal("供应商服务异常（${e.code}），请稍后再试")
                else -> ProbeResult.Fatal("连接失败（${e.code}）")
            }
        } catch (e: Exception) {
            ProbeResult.Fatal(classifyApiError(e.message ?: "").message)
        }
    }

    // ═══════════ 内部工具 ═══════════

    /**
     * 携带 HTTP 状态码的异常，供 [probeEndpoint] 区分 404/405 与其他错误。
     */
    private class HttpCodeException(val code: Int, message: String) : Exception(message)

    /**
     * 返回 HTTP 状态码 + body 的可取消请求执行。
     * 成功（2xx）返回 [CodeBody]；非 2xx 抛 [HttpCodeException]（携带状态码 + body 摘要）。
     * 网络失败抛原始 [IOException]。
     */
    private data class CodeBody(val code: Int, val body: String)

    private suspend fun executeRequestWithCode(client: OkHttpClient, request: Request): CodeBody =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful) {
                            if (cont.isActive) cont.resume(CodeBody(resp.code, body))
                        } else {
                            if (cont.isActive) cont.resumeWithException(
                                HttpCodeException(resp.code, body.take(200))
                            )
                        }
                    }
                }
            })
        }

    /**
     * 可取消的异步请求执行。协程取消时自动 call.cancel()。
     */
    private suspend fun executeRequest(client: OkHttpClient, request: Request): String =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = resp.body?.string() ?: ""
                        when {
                            resp.code == 401 ->
                                if (cont.isActive) cont.resumeWithException(
                                    ProviderFailureException(
                                        ProviderFailure(
                                            ReplyFailureKind.Auth,
                                            "API Key 无效，请检查设置"
                                        )
                                    ))
                            resp.code == 429 ->
                                if (cont.isActive) cont.resumeWithException(
                                    ProviderFailureException(ProviderFailure(ReplyFailureKind.RateLimited)))
                            !resp.isSuccessful ->
                                if (cont.isActive) cont.resumeWithException(
                                    ProviderFailureException(classifyApiError(body.take(200), resp.code)))
                            else ->
                                if (cont.isActive) cont.resume(body)
                        }
                    }
                }
            })
        }

    /**
     * 使用冻结的 [config] 构建请求体，禁止再读取 getActiveModel()/getActiveTicket()/securePrefs.thinkingMode。
     * model 来自 config.model，thinkingMode 来自 config.thinkingMode（thinkingOverride 优先）。
     */
    internal fun buildRequestBodyWithConfig(
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
     * 使用冻结的 [config] 构建请求，禁止再读取 getActiveTicket()。
     * URL 来自 config.baseUrl（已在 resolveRequestConfig 中 normalize），Authorization 来自 config.apiKey。
     */
    internal fun buildRequestWithConfig(config: ProviderRequestConfig, body: String): Request {
        // URL + Key 全部来自 config 快照，不再读 getActiveTicket()/getActiveApiKey()
        return Request.Builder()
            .url(config.baseUrl)  // resolveRequestConfig 中已 normalize
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    /**
     * 计费必须绑定实际请求 config，禁止读取响应时的 active ticket/model。
     * @param config 请求开始时冻结的 ProviderRequestConfig 快照
     * @param scope  计费来源标识（FOREGROUND=悬浮窗流式 / BACKGROUND=后台 generateRaw）
     */
    private fun logUsage(root: JsonObject, config: ProviderRequestConfig, scope: CostScope): com.lovebrain.app.model.StreamUsage? {
        return runCatching {
            val usage = root["usage"]?.jsonObject ?: return@runCatching null
            val hit = usage["prompt_cache_hit_tokens"]?.jsonPrimitive?.int ?: 0
            val miss = usage["prompt_cache_miss_tokens"]?.jsonPrimitive?.int ?: 0
            val prompt = usage["prompt_tokens"]?.jsonPrimitive?.int ?: 0
            val completion = usage["completion_tokens"]?.jsonPrimitive?.int ?: 0
            // 更新统计计数器
            _totalPromptTokens.addAndGet(prompt.toLong())
            _totalCompletionTokens.addAndGet(completion.toLong())
            _totalCacheHitTokens.addAndGet(hit.toLong())
            _totalCacheMissTokens.addAndGet(miss.toLong())
            refreshStats()
            L.w("API usage: prompt=$prompt(hit=$hit,miss=$miss) completion=$completion")
            // 计费双条件使用 config.baseUrl / config.model，不再读 getActiveTicket()/getActiveModel()
            val hasCacheFields = usage.containsKey("prompt_cache_hit_tokens") || usage.containsKey("prompt_cache_miss_tokens")
            var costYuan: Double? = null
            if (UsagePricer.shouldBill(config.baseUrl, hasCacheFields)) {
                val tier = UsagePricer.priceTier(config.model)
                val peak = UsagePricer.isPeakHourBeijing(Instant.now())
                val cost = UsagePricer.costYuan(hit.toLong(), miss.toLong(), completion.toLong(), tier, peak)
                if (cost > 0.0) {
                    costYuan = cost
                    _costEvents.tryEmit(UsageCostEvent(cost, System.currentTimeMillis(), scope))
                    L.w("Cost billed: ${"%.4f".format(cost)} yuan (tier=$tier, peak=$peak, scope=$scope)")
                }
            }
            com.lovebrain.app.model.StreamUsage(
                promptTokens = prompt.takeIf { it > 0 },
                completionTokens = completion.takeIf { it > 0 },
                costYuan = costYuan
            )
        }.getOrNull()
    }

    companion object {

        /** thinking 参数 wire shape 降级候选列表 */
        private val THINKING_WIRE_SHAPES = listOf(
            "thinking.type",      // ① thinking.type = enabled + reasoning_effort = low
            "reasoning_effort",   // ② reasoning_effort = low（无 thinking.type 包装）
            "enable_thinking",    // ③ enable_thinking = true
            "none"                // ④ 不发送任何 thinking 族参数
        )

        /**
         * 全仓唯一的错误分类出口：把供应商/网络的原始响应读成一个 typed [ProviderFailure]。
         *
         * 供应商的英文词汇只在这里被解释一次；下游按 kind 决定控制流、按 message 展示，
         * 供应商的原始词汇只在这里解释一次，下游不再从字符串前缀或异常 message 反推类型。
         * 英文原文保留在 logcat（L.w）供排查，不作为 UI 输入。
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
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * 网络信任闸门：http:// 仅放行 loopback 主机，其余主机强制 https://，
 * 避免 Bearer Key 与聊天上下文被明文嗅探。
 * 纯判定、不依赖 Android 运行时，由 HttpsTrustGuardTest 直测。
 *
 * 只处理以 http:// 开头的地址；其余形态不在本闸门职责
 * （由 normalizeBaseUrl 既有脏数据拦截负责）。
 */
internal object HttpsTrustGuard {

    /** loopback 主机白名单（小写化比对）；保本地 LLM（Ollama/LM Studio 默认 http://127.0.0.1）正当场景 */
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "::1", "localhost")

    /**
     * 违规抛 IllegalArgumentException（固定文案，不拼用户输入）；合规则直接返回。
     * host 用 java.net.URI 解析（IPv6 字面量的方括号显式剥离后比对）；解析失败 = 按违规拦截（宁可拒发不可放行）。
     */
    fun enforce(rawBaseUrl: String) {
        val trimmed = rawBaseUrl.trim().trimEnd('/')
        if (!trimmed.startsWith("http://")) return
        var host = runCatching { java.net.URI(trimmed).host }.getOrNull()?.lowercase()
            ?: throw IllegalArgumentException("地址格式不正确，请检查后重试")
        // JDK URI.getHost() 对 IPv6 字面量保留方括号（[::1]），显式剥离后比对（::1 在豁免名单）
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length - 1)
        }
        if (host !in LOOPBACK_HOSTS) {
            throw IllegalArgumentException(
                "http:// 地址仅限本机（127.0.0.1/::1/localhost）；对外地址请使用 https://，避免 API Key 明文传输"
            )
        }
    }
}
