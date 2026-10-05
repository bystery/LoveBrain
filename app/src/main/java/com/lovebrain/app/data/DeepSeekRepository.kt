package com.lovebrain.app.data

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.RawGenerationResult
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.util.L
import com.lovebrain.app.util.OpenAiChatEndpointResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * [AiGateway] 端口的实现与这一层的装配者。
 * - 流式请求使用 callbackFlow + enqueue，协程取消时自动中断网络请求。
 * - JSON 解析使用 kotlinx.serialization，Kotlin 默认值正确生效。
 * - 内置请求统计计数器（线程安全 AtomicInteger/AtomicLong），摘要进日志（logStatsSummary）。
 *
 * 自己只留两件事：**端口的实现**（generateStream / generateRaw / parseReplyResponse，
 * 外加协程取消与背压的接线）与**对外的门面**（工单身份、统计与计费事件、连接测试）。
 * 四个各有变化理由的协作者：
 * - [ProviderConfigResolver]：一张工单怎样冻结成一个请求的身份（含地址准入）；
 * - [ApiUsageTracker]：用量计数与计费口径；
 * - [OpenAiChatWire]：OpenAI 兼容协议的读写与错误词汇；
 * - [CancellableHttpTransport]：一次 HTTP 交换的可取消执行。
 * 本类的构造参数表与对外的方法签名逐字未变，调用点零改动。
 */
class DeepSeekRepository(securePrefs: SecurePrefs) : AiGateway {

    /** 工单身份 → 不可变请求配置快照（含地址准入），判据全在它里面 */
    private val configResolver = ProviderConfigResolver(securePrefs)

    /** 请求计数、token 用量与双条件计费；构造时即从持久化存储恢复统计 */
    private val usageTracker = ApiUsageTracker(securePrefs)

    /**
     * 非流式（经验提取 / 画像 / 连接探测）用的客户端：读超时保持全局固定
     * [AppConfig.READ_TIMEOUT_SEC]——这一档**故意不跟着工单的超时档位走**：改动面要可控。
     * 那些任务都在后台跑、没有"用户对着转圈"的
     * 体感压力，把它们一起放开只会让后台队列在坏服务上挂得更久。
     */
    private val client = CancellableHttpTransport.client(AppConfig.READ_TIMEOUT_SEC)

    // 流式客户端**不再是一颗固定的 120 秒**：每轮按这张工单冻结的档位向
    // `CancellableHttpTransport.streamClient(...)` 要（档位白名单见 `GenerationTimeoutTier`，
    // 默认档 120 秒与旧的 `AppConfig.STREAM_READ_TIMEOUT_SEC` 逐字相同）。见 generateStream 里那一行。

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // ═══════════ 工单系统门面（委托 [ProviderConfigResolver]） ═══════════

    /** 获取所有工单列表 */
    fun getAllTickets(): List<ProviderTicket> = configResolver.allTickets()

    /** 获取激活工单 */
    fun getActiveTicket(): ProviderTicket? = configResolver.activeTicket()

    /** 获取激活工单的 API Key */
    fun getActiveApiKey(): String? = configResolver.activeApiKey()

    /** 获取激活工单绑定的模型名（一工单 = 一模型） */
    fun getActiveModel(): String? = configResolver.activeModel()

    /**
     * 设置激活工单
     * @param ticketId 工单 ID
     */
    fun activateTicket(ticketId: String) {
        configResolver.activateTicket(ticketId)
    }

    /**
     * 公开快照入口——供 GenerationEngine 在整轮生成开始时冻结 Provider 身份。
     * 后续所有 retry attempt 使用同一个 ProviderRequestConfig，不因用户切工单而漂移。
     * 返回 null = 配置不完整（调用方负责处理）。
     */
    override fun snapshotProviderConfig(): ProviderRequestConfig? =
        try { configResolver.resolveActive() } catch (e: IllegalArgumentException) { null }

    /**
     * 按 GenerationInput 里冻结的 ticketId 取请求配置。
     *
     * 与 [snapshotProviderConfig] 的区别是它绝不回读 activeTicketId：
     * 用户中途换工单时这里返回的是**冻结时那张工单**的配置，
     * Engine 再用非敏感字段比对，任何一项对不上就失败，而不是悄悄换供应商发请求。
     */
    override fun configForTicket(ticketId: String): ProviderRequestConfig? =
        try { configResolver.resolveFor(ticketId) } catch (e: IllegalArgumentException) { null }

    // ═══════════ 统计与计费门面（委托 [ApiUsageTracker]） ═══════════

    /** 计费事件流（双条件命中后 tryEmit；VM 订阅聚合） */
    val costEvents: Flow<UsageCostEvent> = usageTracker.costEvents

    /** 从持久化存储恢复统计数据 */
    fun restoreStats() = usageTracker.restoreStats()

    /** 输出统计摘要到日志（每次生成后调用） */
    fun logStatsSummary() = usageTracker.logStatsSummary()

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
     * @param config 请求级不可变配置快照。传 null 时内部自行 resolve。
     * @param thinkingOverride 超时降级用：非 null 时覆盖 config 中的 thinkingMode。
     *        降级链：thinking(enabled) → timeout → thinking(disabled=0) → retry → fail
     * @param thinkingShapeIndex thinking 参数 wire shape 索引（降级链用）
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
            configResolver.resolveActive()
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
            val ticket = configResolver.activeTicket()
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

        usageTracker.startRequest()

        val requestBody = buildRequestBodyWithConfig(
            resolvedConfig, systemPrompt, userPrompt, AppConfig.TEMPERATURE_MAIN, stream = true,
            thinkingOverride = thinkingOverride,
            thinkingShapeIndex = thinkingShapeIndex
        )
        val request = buildRequestWithConfig(resolvedConfig, requestBody)
        val accumulated = StringBuilder()

        // 读超时按**这张工单**冻结的档位取（档位只走白名单，见 GenerationTimeoutTier）。
        // 连接/写超时不在这里——那两颗仍是 AppConfig 的固定值，见 CancellableHttpTransport.client。
        val call = CancellableHttpTransport
            .streamClient(resolvedConfig.streamReadTimeoutSec)
            .newCall(request)
        val t2 = System.currentTimeMillis()
        L.w("PERF t2 request enqueued")

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return // 主动取消，不报错
                usageTracker.recordFailure()
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
                            usageTracker.recordFailure()
                            trySend(StreamEvent.Error(ProviderFailure(ReplyFailureKind.Auth), ""))
                            close()
                            return
                        }
                        resp.code == 429 -> {
                            usageTracker.recordFailure()
                            trySend(StreamEvent.Error(ProviderFailure(ReplyFailureKind.RateLimited), ""))
                            close()
                            return
                        }
                        !resp.isSuccessful -> {
                            usageTracker.recordFailure()
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

                            val payload = OpenAiChatWire.sseDataPayload(line) ?: continue
                            if (payload == OpenAiChatWire.SSE_DONE) break

                            val chunkContent = runCatching {
                                val frame = OpenAiChatWire.parseChunk(payload)
                                // 流式末尾带 usage 的 chunk（include_usage=true 时最后一条）
                                // logUsage 使用请求开始时冻结的 config + FOREGROUND scope
                                val usageRoot = frame.usageRoot
                                if (usageRoot != null) streamUsage = usageTracker.logUsage(
                                    usageRoot, resolvedConfig, CostScope.FOREGROUND
                                )
                                frame.content
                            }.getOrNull()

                            if (!chunkContent.isNullOrEmpty()) {
                                if (!firstChunkLogged) {
                                    firstChunkLogged = true
                                    L.w("PERF t3 first chunk (+${System.currentTimeMillis() - t2}ms)")
                                }
                                accumulated.append(chunkContent)
                                // 高频逐 token 发射，trySend 在 channel 满时静默丢弃导致丢字；
                                // trySendBlocking 在 IO 线程阻塞等待消费者，背压正确传导
                                trySendBlocking(StreamEvent.Chunk(chunkContent))
                            }
                        }

                        if (!call.isCanceled()) {
                            usageTracker.recordSuccess()
                            L.w("PERF t4 stream complete (+${System.currentTimeMillis() - t2}ms, ${accumulated.length} chars)")
                            L.w("API stats: ${usageTracker.stats}")
                            logStatsSummary()
                            // 流结束发射完整文本和 usage，同样用 trySendBlocking 防背压丢字
                            trySendBlocking(StreamEvent.Complete(accumulated.toString(), streamUsage))
                        }
                    } catch (e: Exception) {
                        if (!call.isCanceled()) {
                            usageTracker.recordFailure()
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
     * : 至少一个真实非空风格才可作为回复成功；全空 response 被拒绝。
     * b3-9: 解析降级——当 response 全空但 directions 有非空项时，使用 directions 作为回复。 */
    override fun parseReplyResponse(content: String): LoveBrainResponse {
        val jsonStr = com.lovebrain.app.util.Jsons.extractJsonBlock(content)
            ?: throw IllegalStateException("模型未返回有效 JSON，请重试")

        val resp = runCatching { json.decodeFromString<LoveBrainResponse>(jsonStr) }
            .getOrElse { throw IllegalStateException("解析回复失败，请重试") }

        // : 不再用 resp.schemes.isEmpty() 验证——toSchemes 永远返回 4 条（空 reply = 本轮不适合）。
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
    val config = try { configResolver.resolveActive() } catch (e: IllegalArgumentException) { return "" }
    ?: return ""
    return generateRaw(config, systemPrompt, userPrompt)
}

/**
 * 带元数据的非流式生成便捷重载（内部自行 resolve 配置快照）。
 * 供 KnowledgeTriggerCoordinator 等不持有 config 快照的调用方使用。
 */
    override suspend fun generateRawWithMetadata(systemPrompt: String, userPrompt: String): RawGenerationResult {
    val config = try { configResolver.resolveActive() } catch (e: IllegalArgumentException) {
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
        usageTracker.countRequest()
        val requestBody = buildRequestBodyWithConfig(
            config, systemPrompt, userPrompt, AppConfig.TEMPERATURE_RAW, stream = false
        )
        val request = buildRequestWithConfig(config, requestBody)

        // CancellationException 必须重新抛出，不计入 failCount
        return try {
            val respBody = CancellableHttpTransport.execute(client, request)
            val root = json.parseToJsonElement(respBody).jsonObject
            // 统计 token 用量 + 计费使用请求开始时冻结的 config + BACKGROUND scope
            runCatching { usageTracker.logUsage(root, config, CostScope.BACKGROUND) }
            usageTracker.recordSuccess()
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
            usageTracker.recordFailure()
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
            configResolver.normalizeBaseUrl(baseUrl)
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
        val url = configResolver.normalizeBaseUrl(baseUrl)

        // 降级链：最多 2 次尝试（初始带 thinking.type=disabled + 参数被拒后去掉 thinking）
        var thinkingShapeIndex = 0
        var attemptsUsed = 0

        while (attemptsUsed < 2) {
            attemptsUsed++
            val body = OpenAiChatWire.minimalProbeBody(
                testModel, thinkingDisabled = thinkingShapeIndex == 0
            )
            val request = OpenAiChatWire.buildBearerRequest(url, apiKey, body)
            try {
                val respBody = CancellableHttpTransport.execute(client, request)
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
        val body = OpenAiChatWire.minimalProbeBody(model, thinkingDisabled = false)
        val request = OpenAiChatWire.buildBearerRequest(url, apiKey, body)

        return try {
            val respBody = CancellableHttpTransport.executeWithCode(client, request)
            val root = json.parseToJsonElement(respBody.body).jsonObject
            if (root["choices"] != null) {
                ProbeResult.Success(url)
            } else {
                ProbeResult.Fatal("接口返回异常，请检查模型名称是否正确")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CancellableHttpTransport.HttpCodeException) {
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

    // ═══════════ 协议构造门面（委托 [OpenAiChatWire]，测试与调用点用的签名不变） ═══════════

    /**
     * 使用冻结的 [config] 构建请求体，禁止再读取实时工单/模型/thinkingMode。
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
    ): String = OpenAiChatWire.buildRequestBody(
        config, systemPrompt, userPrompt, temperature, stream, thinkingOverride, thinkingShapeIndex
    )

    /**
     * 使用冻结的 [config] 构建请求，禁止再读取实时工单。
     * URL 来自 config.baseUrl（已在 resolve 阶段 normalize），Authorization 来自 config.apiKey。
     */
    internal fun buildRequestWithConfig(config: ProviderRequestConfig, body: String): Request =
        OpenAiChatWire.buildRequest(config, body)

    companion object {

        /**
         * 全仓唯一的错误分类出口（实现委托 [OpenAiChatWire.classifyApiError]，判据只有一份）：
         * 把供应商/网络的原始响应读成一个 typed [ProviderFailure]。
         *
         * 供应商的英文词汇只在那里被解释一次；下游按 kind 决定控制流、按 message 展示，
         * 不从字符串前缀或异常 message 反推类型。
         * 英文原文保留在 logcat（L.w）供排查，不作为 UI 输入。
         */
        internal fun classifyApiError(raw: String, code: Int = 0): ProviderFailure =
            OpenAiChatWire.classifyApiError(raw, code)
    }
}

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
