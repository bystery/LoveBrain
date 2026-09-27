package com.lovebrain.app.data

import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.StreamUsage
import com.lovebrain.app.util.L
import com.lovebrain.app.util.UsagePricer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
 * 计费事件来源标识。
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
 * scope 标识来源：VM 只将 FOREGROUND 费用写入"本次花费"。
 */
data class UsageCostEvent(
    val yuan: Double,
    val timestampMs: Long,
    val scope: CostScope
)

/**
 * 用量与计费的唯一所有者：请求计数、统计快照的持久化、token 用量入账、
 * 双条件计费判定与成本事件发射。
 *
 * 变化理由只有这一条——口径（数什么、什么时候算钱、事件给谁）变了才动这里；
 * 请求怎么构造、流怎么读都不在这里。
 *
 * 计数器用线程安全的 AtomicInteger/AtomicLong：OkHttp 回调跑在它自己的线程上，
 * 而发起方是协程，两条线程会同时碰同一组计数。
 */
internal class ApiUsageTracker(private val securePrefs: SecurePrefs) {

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

    /** 当前统计快照：日志摘要用，口径与内部 _stats.value 逐字一致 */
    val stats: ApiStats get() = _stats.value

    init {
        restoreStats()
    }

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

    /**
     * 一次请求开始（流式）：请求数 +1 并刷新快照。
     * 与搬前 `_totalRequests.incrementAndGet(); refreshStats()` 逐字同序。
     */
    fun startRequest() {
        _totalRequests.incrementAndGet()
        refreshStats()
    }

    /**
     * 一次请求开始（非流式）：只 +1 计数，不在此处刷新快照——
     * 搬前的 `generateRawWithMetadata` 就是这个口径（快照由随后的 success/failure 刷新落盘）。
     */
    fun countRequest() {
        _totalRequests.incrementAndGet()
    }

    /** 一次请求成功 */
    fun recordSuccess() {
        _successCount.incrementAndGet()
        refreshStats()
    }

    /** 一次请求失败（调用方已确认那不是用户取消） */
    fun recordFailure() {
        _failCount.incrementAndGet()
        refreshStats()
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

    /**
     * 计费必须绑定实际请求 config，禁止读取响应时的 active ticket/model。
     * @param config 请求开始时冻结的 ProviderRequestConfig 快照
     * @param scope  计费来源标识（FOREGROUND=悬浮窗流式 / BACKGROUND=后台 generateRaw）
     */
    fun logUsage(
        root: JsonObject,
        config: ProviderRequestConfig,
        scope: CostScope
    ): StreamUsage? {
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
            StreamUsage(
                promptTokens = prompt.takeIf { it > 0 },
                completionTokens = completion.takeIf { it > 0 },
                costYuan = costYuan
            )
        }.getOrNull()
    }
}
