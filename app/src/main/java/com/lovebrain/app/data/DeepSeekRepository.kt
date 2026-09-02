package com.lovebrain.app.data

import com.lovebrain.app.AppConfig
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.util.L
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
 * 计费事件：logUsage 过双条件计费后发射，VM 聚合今日累计/本次花费。
 * timestampMs 供消费侧跨天滚动判定。
 */
data class UsageCostEvent(val yuan: Double, val timestampMs: Long)

/**
 * DeepSeek API 网络层 v2。
 * - 流式请求使用 callbackFlow + enqueue，协程取消时自动中断网络请求。
 * - JSON 解析使用 kotlinx.serialization，Kotlin 默认值正确生效。
 * - 内置请求统计计数器（线程安全 AtomicInteger/AtomicLong），摘要进日志（logStatsSummary）。
 */
class DeepSeekRepository(private val securePrefs: SecurePrefs) {

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
        // ：http:// 仅放行 loopback 主机，对外强制 https（扼制点：生成/测试连接两出口唯一必经）
        HttpsTrustGuard.enforce(trimmed)
        return trimmed
    }

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

    /** ：计费事件流（logUsage 双条件命中后 tryEmit；VM 订阅聚合） */
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
     * @param thinkingOverride 超时降级用：非 null 时覆盖 securePrefs 中的 thinkingMode。
     *        降级链：thinking(enabled) → timeout → thinking(disabled=0) → retry → fail
     * @param thinkingShapeIndex thinking 参数 wire shape 索引（ 降级链用）
     */
    fun generateStream(
        systemPrompt: String,
        userPrompt: String,
        thinkingOverride: Int? = null,
        thinkingShapeIndex: Int = 0
    ): Flow<StreamEvent> = callbackFlow {
        //  Step 3+5+6A+6B: 三校验 + 脏数据拦截 (ticket/baseUrl/apiKey)
        val ticket = getActiveTicket()
            ?: run { trySend(StreamEvent.Error("CONFIG_ERROR:请先配置一个模型供应商", "")).getOrThrow(); close(); return@callbackFlow }
        
        if (ticket.id.isNullOrBlank()) {
            run { trySend(StreamEvent.Error("CONFIG_ERROR:工单 ID 无效，请重新激活", "")).getOrThrow(); close(); return@callbackFlow }
        }
        
        if (ticket.baseUrl.isNullOrBlank()) {
            run { trySend(StreamEvent.Error("CONFIG_ERROR:接口地址未填写，请在设置中补充", "")).getOrThrow(); close(); return@callbackFlow }
        }
        
        if (ticket.model.isNullOrBlank()) {
            run { trySend(StreamEvent.Error("CONFIG_ERROR:模型名称未配置，请在设置中补充", "")).getOrThrow(); close(); return@callbackFlow }
        }
        
        val apiKey = getActiveApiKey()
            ?: run { trySend(StreamEvent.Error("CONFIG_ERROR:API Key 缺失，请检查工单配置", "")).getOrThrow(); close(); return@callbackFlow }
        
        _totalRequests.incrementAndGet()
        refreshStats()

        val requestBody = buildRequestBody(
            systemPrompt, userPrompt, AppConfig.TEMPERATURE_MAIN, stream = true,
            thinkingOverride = thinkingOverride,
            thinkingShapeIndex = thinkingShapeIndex
        )
        val request = buildRequest(apiKey, requestBody)
        val accumulated = StringBuilder()

        val call = streamClient.newCall(request)
        val t2 = System.currentTimeMillis()
        L.w("PERF t2 request enqueued")

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return // 主动取消，不报错
                _failCount.incrementAndGet()
                refreshStats()
                // ：日志脱敏，只记 host 和消息长度
                L.w("API onFailure host=${request.url.host} msgLen=${e.message?.length}")
                trySend(StreamEvent.Error(
                    mapApiError(e.message ?: ""),
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
                            trySend(StreamEvent.Error("CONFIG_ERROR:API Key 无效，请检查设置", ""))
                            close()
                            return
                        }
                        resp.code == 429 -> {
                            _failCount.incrementAndGet()
                            refreshStats()
                            trySend(StreamEvent.Error("请求过于频繁，请稍后再试", ""))
                            close()
                            return
                        }
                        !resp.isSuccessful -> {
                            _failCount.incrementAndGet()
                            refreshStats()
                            val errBody = resp.body?.string()?.take(500) ?: ""
                            // ：日志脱敏，响应体只记长度
                            L.w("API error code=${resp.code} bodyLen=${errBody.length}")
                            trySend(StreamEvent.Error(mapApiError(errBody, resp.code), ""))
                            close()
                            return
                        }
                    }

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
                                if (chunk["usage"] != null) logUsage(chunk)
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
                                // : 高频逐 token 发射，trySend 在 channel 满时静默丢弃导致丢字；
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
                            // : 流结束发射完整文本，同样用 trySendBlocking 防背压丢字
                            trySendBlocking(StreamEvent.Complete(accumulated.toString()))
                        }
                    } catch (e: Exception) {
                        if (!call.isCanceled()) {
                            _failCount.incrementAndGet()
                            refreshStats()
                            // ：日志脱敏，只记消息长度
                            L.w("API stream error msgLen=${e.message?.length}")
                            trySend(StreamEvent.Error(
                                mapApiError(e.message ?: ""),
                                accumulated.toString()
                            ))
                        }
                    }
                    close()
                }
            }
        })

        // 必须放在 callbackFlow 最后一行：挂起直到 flow 被取消，取消时中断网络请求
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /** 将累积文本解析为 LoveBrainResponse（单次调用：response + analysis） */
    fun parseReplyResponse(content: String): LoveBrainResponse {
        val jsonStr = com.lovebrain.app.util.Jsons.extractJsonBlock(content)
            ?: throw IllegalStateException("模型未返回有效 JSON，请重试")

        val resp = runCatching { json.decodeFromString<LoveBrainResponse>(jsonStr) }
            .getOrElse { throw IllegalStateException("解析回复失败，请重试") }

        if (resp.schemes.isEmpty()) {
            throw IllegalStateException("返回格式不完整：缺少回复方案，请重试")
        }
        return resp
    }

    // ═══════════ 非流式生成（辅助任务用） ═══════════

    /**
     * 纯文本生成（不解析为 LoveBrainResponse），用于经验提取等辅助任务。
     * 使用 suspendCancellableCoroutine + enqueue，支持协程取消。
     */
    suspend fun generateRaw(systemPrompt: String, userPrompt: String): String {
        //  改动点⑧：Key 源切换至工单
        val apiKey = getActiveApiKey() ?: return ""
        if (apiKey.isBlank()) return ""

        _totalRequests.incrementAndGet()
        val requestBody = buildRequestBody(
            systemPrompt, userPrompt, AppConfig.TEMPERATURE_RAW, stream = false
        )
        val request = buildRequest(apiKey, requestBody)

        return runCatching {
            val respBody = executeRequest(client, request)
            val root = json.parseToJsonElement(respBody).jsonObject
            // 统计 token 用量（非流式响应也带 usage）
            runCatching { logUsage(root) }
            _successCount.incrementAndGet()
            refreshStats()
            root["choices"]?.jsonArray
                ?.get(0)?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content ?: ""
        }.getOrElse {
            _failCount.incrementAndGet()
            refreshStats()
            ""
        }
    }

    // ═══════════ API 连接测试 ═══════════

    /**
     * 测试 API 连接是否有效：发送一个最小请求验证 apiKey/model/baseUrl 是否正确。
     * 不使用 securePrefs 中的配置，而是用传入的参数测试（允许用户先测试再保存）。
     *
     *  修复：请求体接上  降级链——初始带 thinking.type=disabled（直出），
     * 命中 PARAM_UNSUPPORTED 就去掉 thinking 参数重试一次（最多 2 次尝试）。
     */
    suspend fun testConnection(apiKey: String, model: String, baseUrl: String): Boolean {
        if (apiKey.isBlank() || baseUrl.isBlank()) return false
        val testModel = model.ifBlank { AppConfig.DEFAULT_MODEL }
        //  改动点⑤：使用 normalizeBaseUrl 统一处理（含  脏数据拦截）
        val url = normalizeBaseUrl(baseUrl)

        // 降级链：最多 2 次尝试（初始带 thinking.type=disabled + 命中 PARAM_UNSUPPORTED 后去掉 thinking）
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
                // ：取消信号必须重抛，禁止吞掉误报"测试失败"（复用 GenerationEngine :397 先例）
                if (e is CancellationException) throw e
                // 命中 PARAM_UNSUPPORTED → 去掉 thinking 参数重试一次
                val errorMsg = mapApiError(e.message ?: "")
                if (errorMsg.startsWith("PARAM_UNSUPPORTED:") && thinkingShapeIndex < 1) {
                    thinkingShapeIndex++
                    L.w("testConnection: thinking 参数不支持，降级到不带 thinking 参数重试")
                    continue
                }
                return false
            }
        }
        return false
    }

    // ═══════════ 内部工具 ═══════════

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
                                    IllegalStateException("CONFIG_ERROR:API Key 无效，请检查设置"))
                            resp.code == 429 ->
                                if (cont.isActive) cont.resumeWithException(
                                    IllegalStateException("请求过于频繁，请稍后再试"))
                            !resp.isSuccessful ->
                                if (cont.isActive) cont.resumeWithException(
                                    IllegalStateException("请求失败 (${resp.code})：${body.take(200)}"))
                            else ->
                                if (cont.isActive) cont.resume(body)
                        }
                    }
                }
            })
        }

    private fun buildRequestBody(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        stream: Boolean,
        thinkingOverride: Int? = null,
        thinkingShapeIndex: Int = 0
    ): String {
        val body = buildJsonObject {
            //  改动点③：model 源 = 激活工单绑定的模型（一工单 = 一模型）
            val model = getActiveModel() ?: throw IllegalStateException("激活工单未配置模型，请到设置里补充")
            put("model", model)
            put("temperature", temperature)
            if (stream) {
                put("stream", true)
                // 让流式响应末尾带 usage 字段（用于统计 token 消耗）
                putJsonObject("stream_options") { put("include_usage", true) }
            }
            // 思考模式两态（悬浮窗可切换）：0=直出 1=思考（toggle 切换，两态化后无写 2 的可达路径）
            // deepseek-v4-flash 默认 thinking=enabled+high，输出思维链 → completion 虚高、偶发 content 空。
            // 话术生成任务是确定性输出，默认直出，更快更省更稳。
            // 超时降级：thinkingOverride 非 null 时覆盖配置值（用于超时后自动降级到直出模式）
            // 配置校验：无效值兜底为 0（直出），防止 API 报错
            //  消费兜底链：本次调用覆盖 > 工单开关 > 全局设置；
            // 老工单无字段（null）回退全局 = 继承当前全局直出/思考设置；工单开关经
            // SetupViewModel.toggleTicketThinking 写入（SecurePrefs.thinkingMode 键保留作兜底读源）
            val effectiveThinking = thinkingOverride ?: (getActiveTicket()?.thinkingMode ?: securePrefs.thinkingMode)
            val tMode = effectiveThinking.coerceIn(0, 1)
            if (tMode != effectiveThinking) {
                L.w("⚠️ thinkingMode=$effectiveThinking 无效，已回退为 $tMode")
            }
            if (thinkingOverride != null && thinkingOverride != securePrefs.thinkingMode) {
                L.w("⚡ 超时降级：thinkingMode ${securePrefs.thinkingMode} → $tMode")
            }
            // 两态：0=直出（disabled） 1=思考（enabled + reasoning_effort low）
            when (tMode) {
                //  修复②：直出模式也走降级链——前 3 个候选（index 0-2）才发 disabled；
                // thinkingShapeIndex == 3（④ none）时不发送任何 thinking 族参数
                0 -> if (thinkingShapeIndex < 3) {
                    putJsonObject("thinking") { put("type", "disabled") }
                }
                1 -> {
                    //  改动点④：thinking 两态候选 wire shape
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

    private fun buildRequest(apiKey: String, body: String): Request {
        //  改动点②：URL 源切换至激活工单；
        // ：异常文案经 GenerationEngine :260 原样透传展示给用户，黑话换成固定表人话（G4③）
        val ticket = getActiveTicket()
            ?: throw IllegalStateException("还没有可用的模型配置，请到设置里检查")
        val url = normalizeBaseUrl(ticket.baseUrl)
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun logUsage(root: JsonObject) {
        runCatching {
            val usage = root["usage"]?.jsonObject ?: return
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
            // ：计费双条件（主人拍板）——工单地址含 deepseek.com 且 usage 带缓存命中/未命中字段；
            // 不满足 → 不计费（展示侧"本次"位占位"—"）。日志只记金额（规则 8 口径）。
            val hasCacheFields = usage.containsKey("prompt_cache_hit_tokens") || usage.containsKey("prompt_cache_miss_tokens")
            val ticket = getActiveTicket()
            if (ticket != null && UsagePricer.shouldBill(ticket.baseUrl, hasCacheFields)) {
                val tier = UsagePricer.priceTier(getActiveModel().orEmpty())
                val peak = UsagePricer.isPeakHourBeijing(Instant.now())
                val costYuan = UsagePricer.costYuan(hit.toLong(), miss.toLong(), completion.toLong(), tier, peak)
                if (costYuan > 0.0) {
                    _costEvents.tryEmit(UsageCostEvent(costYuan, System.currentTimeMillis()))
                    L.w("Cost billed: ${"%.4f".format(costYuan)} yuan (tier=$tier, peak=$peak)")
                }
            }
        }
    }

    companion object {

        /**
         * 配置类错误内部前缀（，仿 PARAM_UNSUPPORTED: 先例）：
         * 携带此标记的错误不重试、不误报"网络波动"，展示前去前缀透传原文案。
         */
        internal const val CONFIG_ERROR_PREFIX = "CONFIG_ERROR:"

        /** 配置类错误固定文案全集（无新机制：全部来自既有固定字符串，单测锚定） */
        private val CONFIG_ERROR_MESSAGES = setOf(
            // normalizeBaseUrl 两条（ 脏数据拦截）
            "地址必须以 http:// 或 https:// 开头",
            "检测到 API Key 误填入地址栏，请检查",
            // HttpsTrustGuard 两条
            "地址格式不正确，请检查后重试",
            "http:// 地址仅限本机（127.0.0.1/::1/localhost）；对外地址请使用 https://，避免 API Key 明文传输",
            // mapApiError 401 人话
            "API Key 无效，请检查设置里填的密钥",
            // buildRequest 无工单
            "还没有可用的模型配置，请到设置里检查"
        )

        /**
         * 配置类错误判定：前缀命中 或 属固定文案全集。
         * internal 放开供单测锚定（先例 = mapApiError/HttpsTrustGuard）。
         */
        internal fun isConfigError(msg: String): Boolean =
            msg.startsWith(CONFIG_ERROR_PREFIX) || msg in CONFIG_ERROR_MESSAGES

        /** 展示前去前缀透传原文案（无标记时原样返回） */
        internal fun stripConfigPrefix(msg: String): String =
            if (msg.startsWith(CONFIG_ERROR_PREFIX)) msg.removePrefix(CONFIG_ERROR_PREFIX) else msg

        /** thinking 参数 wire shape 降级候选列表 */
        private val THINKING_WIRE_SHAPES = listOf(
            "thinking.type",      // ① thinking.type = enabled + reasoning_effort = low
            "reasoning_effort",   // ② reasoning_effort = low（无 thinking.type 包装）
            "enable_thinking",    // ③ enable_thinking = true
            "none"                // ④ 不发送任何 thinking 族参数
        )

        /**
         * 错误文案映射：DeepSeek/网络的英文原文不直接甩给用户。
         * 命中已知错误→人话；未命中→通用提示；英文原文保留在 logcat（L.w）供排查。
         *  改动点⑥ + ：新增 PARAM_UNSUPPORTED 标记供降级链判定；响应体只记长度。
         *  修复：PARAM_UNSUPPORTED 仅限内部使用（GenerationEngine 判定降级），
         * 降级用尽后走下方人话兜底文案，不直接展示裸标记。
         * ：internal 放开（仅单测可见，先例 = HttpsTrustGuard），供回归锚定 400+thinking 文案。
         */
        internal fun mapApiError(raw: String, code: Int = 0): String {
            val s = raw.lowercase()
            // 参数不支持族 → 内部标记（仅 GenerationEngine 降级判定用，不直接展示给用户）
            if (s.contains("unknown parameter") ||
                s.contains("unsupported") ||
                s.contains("invalid field")) {
                val field = when {
                    s.contains("thinking") -> "thinking"
                    s.contains("reasoning_effort") -> "reasoning_effort"
                    s.contains("enable_thinking") -> "enable_thinking"
                    else -> ""
                }
                return "PARAM_UNSUPPORTED:$field"
            }
            // 人话兜底：400 且 body 含 thinking 族关键词（降级链未接或已用尽时展示）
            // ：原“已自动降级”描述失真（降级已用尽才走到这），换成如实指引（固定表文案）
            if (code == 400 &&
                (s.contains("thinking") || s.contains("reasoning_effort") || s.contains("enable_thinking"))) {
                return "模型不支持思考模式参数，请切换直出模式后重试"
            }
            return when {
                s.contains("insufficient balance") || s.contains("insufficient_balance") || code == 402 ->
                    "API 余额不足，去供应商官网充值后就能继续用了"
                s.contains("invalid api key") || s.contains("authentication credentials") || code == 401 ->
                    "API Key 无效，请检查设置里填的密钥"
                s.contains("rate limit") || s.contains("too many requests") || s.contains("max concurrency") || code == 429 ->
                    "请求太频繁了，稍等几秒再试"
                s.contains("content_filter") || s.contains("content filter") || s.contains("sensitive") ->
                    "内容被安全过滤拦截了，换个说法再试"
                s.contains("context_length") || s.contains("context length") || s.contains("too long") || s.contains("max_tokens") ->
                    "内容太长超过模型上限，删减几条对话再试"
                s.contains("server is busy") || s.contains("overloaded") || s.contains("server_error") || code == 503 ->
                    "服务繁忙，稍后再试"
                s.contains("timeout") || s.contains("timed out") ->
                    "请求超时，检查网络后重试"
                s.contains("unable to resolve host") || s.contains("failed to connect") || s.contains("network is unreachable") ->
                    "网络不通，检查手机联网"
                code == 502 -> "网关错误，稍后再试"
                code in 500..599 -> "服务暂时开小差，稍后再试"
                else -> "生成失败，请重试"
            }
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
