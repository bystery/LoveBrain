package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.ProviderRequestConfig
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.CounselingChunk
import com.lovebrain.app.model.CounselingEnded
import com.lovebrain.app.model.CounselingEvent
import com.lovebrain.app.model.CounselingFailed
import com.lovebrain.app.model.CounselingFirstToken
import com.lovebrain.app.model.CounselingResult
import com.lovebrain.app.model.CounselingStarted
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.ProactiveEvent
import com.lovebrain.app.model.ProactiveFailed
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ProactiveStarted
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveFirstToken
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyChunkReset
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyEvent
import com.lovebrain.app.model.ReplyFirstToken
import com.lovebrain.app.model.ReplyMemoryRefs
import com.lovebrain.app.model.ReplySchemesArrived
import com.lovebrain.app.model.ReplySchemesReset
import com.lovebrain.app.model.ReplySourceAliasMap
import com.lovebrain.app.model.ReplyStarted
import com.lovebrain.app.model.ReplyUsage
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.model.SuggestEnded
import com.lovebrain.app.model.SuggestEvent
import com.lovebrain.app.model.SuggestFailed
import com.lovebrain.app.model.SuggestFirstToken
import com.lovebrain.app.model.SuggestResult
import com.lovebrain.app.model.SuggestStarted
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.model.SuggestTips
import com.lovebrain.app.model.ProviderRequestConfigView
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.hashProviderHost
import com.lovebrain.app.model.toChatMessages
import com.lovebrain.app.model.toKnowledgeBase
import com.lovebrain.app.util.IncrementalJsonObjectScanner
import com.lovebrain.app.util.IncrementalSingleObjectScanner
import com.lovebrain.app.util.Jsons
import com.lovebrain.app.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** 降级提示驻留时长（reset 前） */
private const val DEGRADE_HINT_HOLD_MS = 1000L

/** 宽松 JSON（流式提前渲染 response 对象用） */
val jsonLenient = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/**
 * 流式增量解析公共切分器：从**完整**累积文本中提取指定 key 数组里的所有对象。
 *
 * 流式中途的增量解析已改由 [IncrementalJsonObjectScanner] / [IncrementalSingleObjectScanner]
 * 承担；这里只保留"一次性解析整段文本"的用途（终态解析与单测）。
 */
object PartialJsonObjects {

    fun extractObjects(raw: String, key: String): List<String> {
        val scanner = IncrementalJsonObjectScanner(key)
        val out = scanner.feed(raw).toMutableList()
        return out
    }

    fun extractKeyObject(raw: String, key: String): String? =
        IncrementalSingleObjectScanner(key).feed(raw)

    /** 括号匹配定位从 i 开始的完整对象；未闭合返回 null */
    private fun completeObjectAt(buffer: String, i: Int): String? {
        var depth = 0
        var j = i
        var inStr = false
        var esc = false
        while (j < buffer.length) {
            val c = buffer[j]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') inStr = false
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) break }
                }
            }
            j++
        }
        if (depth != 0) return null
        return buffer.substring(i, j + 1)
    }
}

/** 锦囊 tips 解析——产品合同（去重/校验）与 SuggestValidator 保持一致。
 * 不再持有全局可复用扫描器：扫描器状态属于单次请求，跨请求共用会串数据。 */
object PartialTipsParser {

    /** 一次性解析完整文本 */
    fun parse(buffer: String): List<SuggestTip> =
        dedupe(PartialJsonObjects.extractObjects(buffer, "tips"))

    /** 创建一个只服务于单次请求的增量解析器 */
    fun newStreamingParser(): TipsStreamingParser = TipsStreamingParser()

    private fun dedupe(objects: List<String>): List<SuggestTip> {
        val result = mutableListOf<SuggestTip>()
        val seenIds = mutableSetOf<String>()
        val seenActions = mutableSetOf<String>()
        for (objStr in objects) {
            runCatching { jsonLenient.decodeFromString<SuggestTip>(objStr) }.getOrNull()?.let { tip ->
                val action = tip.action.trim()
                if (action.isBlank()) return@let
                if (action in seenActions) return@let
                seenActions.add(action)
                val stableId = if (tip.id.isNotBlank() && tip.id !in seenIds) tip.id else "tip-${result.size + 1}"
                if (stableId in seenIds) return@let
                seenIds.add(stableId)
                result.add(tip.copy(id = stableId, action = action))
            }
        }
        return result
    }

    /** 单请求增量解析器：每次喂新 chunk，返回当前已完整到达且通过去重的全部 tips */
    class TipsStreamingParser internal constructor() {
        private val scanner = IncrementalJsonObjectScanner("tips")
        private val accepted = mutableListOf<SuggestTip>()

        fun feed(chunk: String): List<SuggestTip> {
            val fresh = scanner.feed(chunk)
            if (fresh.isEmpty()) return accepted
            accepted.addAll(dedupeAgainst(fresh))
            return accepted
        }

        private fun dedupeAgainst(objects: List<String>): List<SuggestTip> {
            val seenActions = accepted.mapTo(mutableSetOf()) { it.action }
            val seenIds = accepted.mapTo(mutableSetOf()) { it.id }
            val out = mutableListOf<SuggestTip>()
            for (objStr in objects) {
                val tip = runCatching {
                    jsonLenient.decodeFromString<SuggestTip>(objStr)
                }.getOrNull() ?: continue
                val action = tip.action.trim()
                if (action.isBlank() || action in seenActions) continue
                seenActions.add(action)
                val stableId =
                    if (tip.id.isNotBlank() && tip.id !in seenIds) tip.id else "tip-${accepted.size + out.size + 1}"
                if (stableId in seenIds) continue
                seenIds.add(stableId)
                out.add(tip.copy(id = stableId, action = action))
            }
            return out
        }
    }
}

/**
 * 生成引擎（从 LoveBrainViewModel 拆出）。
 *
 * ## S2-02 / S2-03 的接口形状
 * Engine 只暴露冷流：`fun xxxStream(...): Flow<Event>`。
 * 它**不再**接收 CoroutineScope、**不再** launch、**不再**返回 Job、**不再**回调 ViewModel。
 * 于是"谁拥有这个任务"只有一个答案——订阅这条流的协程，也就是
 * [ForegroundOperationCoordinator] 注册出来的那一个。旧写法是 VM 先 launch 一个 prepJob，
 * Engine 再在传入的 scope 上 launch 第二个 Job，两个 owner 并存。
 *
 * 所有事件都带产生时冻结的 requestId，reducer 只认同一个身份。
 */
class GenerationEngine(
    private val deepSeekRepo: DeepSeekRepository,
    private val promptBuilder: PromptBuilder
) {

    // ═══════════ 回复生成（主生成） ═══════════

    /**
     * S2-01: 不可变生成输入入口。
     *
     * 全部输入取自 [GenerationInput]：requestId、dialogue（只含 PARTNER/USER）、
     * replyDirective、冻结的 KB 内容修订、intent、corrections、onlyThisRound、
     * Provider 完整非敏感身份、prompt 资产 hash。
     *
     * Provider 身份对不上时**直接失败**，不再"记一条日志然后改用新配置"。
     */
    fun replyStream(input: GenerationInput): Flow<ReplyEvent> = flow {
        val requestId = input.requestId

        // ── 准备阶段：读资产、组 prompt、按冻结 ticketId 取请求配置 ──
        val system: String
        val buildResult: PromptBuilder.PromptBuildResult
        val providerConfig: ProviderRequestConfig?
        try {
            system = withContext(Dispatchers.IO) { promptBuilder.buildSystemPrompt() }
            val messages = input.toChatMessages()
            val userHint = input.replyDirective.text
            val knowledgeBase = input.kbContext?.toKnowledgeBase()
            buildResult = withContext(Dispatchers.IO) {
                if (input.onlyThisRound) {
                    promptBuilder.buildReplyUserPromptOnlyThisRound(messages, userHint)
                } else {
                    promptBuilder.buildReplyUserPromptWithRefs(
                        knowledgeBase, messages, userHint,
                        input.replyDirective.aggressive, input.intentConfig, input.corrections
                    )
                }
            }
            // S2-01：按冻结的 ticketId 解析，不回读 activeTicketId
            providerConfig = input.providerIdentity?.let {
                deepSeekRepo.configForTicket(it.ticketId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e("prepare phase exception", e)
            emitFailed(requestId, ReplyFailureKind.fromException(e))
            return@flow
        }

        emit(ReplyStarted(requestId))
        val t0 = System.currentTimeMillis()
        L.w("PERF t0 stream opened (requestId=${requestId.take(8)})")

        // ── Provider 身份校验：不一致就不发请求 ──
        val identity = input.providerIdentity
        if (providerConfig == null) {
            emitFailed(requestId, ReplyFailureKind.ProviderMissing)
            return@flow
        }
        if (identity != null && !identity.matches(providerConfig.toView())) {
            L.w("S2-01: provider config drifted from the frozen identity, refusing to send")
            emitFailed(requestId, ReplyFailureKind.ProviderChanged)
            return@flow
        }
        // 资产 hash 只做诊断：不一致说明 prompt 在冻结后被换掉，本轮仍用冻结时的 prompt 文本
        val liveAssetHash = promptBuilder.replyPromptAssetHash()
        if (input.promptAssetHash.isNotBlank() && liveAssetHash != input.promptAssetHash) {
            L.w("S2-01: prompt assets changed after freeze (frozen=${input.promptAssetHash.take(8)})")
        }

        val user = buildResult.prompt
        emit(ReplyMemoryRefs(requestId, buildResult.memoryRefs))
        emit(ReplySourceAliasMap(requestId, buildResult.sourceAliasMap))
        L.w("PERF t1 prompt built (+${System.currentTimeMillis() - t0}ms), user=${user.length} chars")

        var fullText = ""
        var errorMsg: String? = null
        var timedOut = false
        var thinkingShapeIndex = 0
        var attemptsUsed = 0
        var firstTokenReported = false

        while (attemptsUsed < AppConfig.GENERATE_MAX_ATTEMPTS) {
            attemptsUsed++
            // GEN-04：每个 attempt 一套独立的增量解析状态，防上一次失败 attempt 的半成品污染重试
            val responseScanner = IncrementalSingleObjectScanner("response")

            if (attemptsUsed > 1) {
                val degradeMsg = when {
                    errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true -> "参数不支持，正在降级…"
                    timedOut -> "网络波动，正在重试…"
                    else -> "网络波动，第 ${attemptsUsed - 1} 次重试中…"
                }
                emit(ReplyChunk(requestId, degradeMsg))
                delay(DEGRADE_HINT_HOLD_MS)
                emit(ReplyChunkReset(requestId))
                emit(ReplySchemesReset(requestId))
            }
            try {
                val thinkingOverride = if (timedOut) 0 else null
                errorMsg = null
                fullText = collectStream(
                    deepSeekRepo.generateStream(
                        system, user, thinkingOverride, thinkingShapeIndex, config = providerConfig
                    ),
                    AppConfig.GENERATE_TIMEOUT_MS,
                    onChunk = { chunk ->
                        emit(ReplyChunk(requestId, chunk))
                        // P3-04：只把新增文本交给扫描器，不再 toString() 全量重扫
                        val objStr = responseScanner.feed(chunk)
                        if (objStr != null) {
                            val schemes = runCatching {
                                jsonLenient.decodeFromString<com.lovebrain.app.model.ReplySchemes>(objStr).toSchemes()
                            }.getOrDefault(emptyList())
                            if (schemes.isNotEmpty()) emit(ReplySchemesArrived(requestId, schemes))
                        }
                    },
                    onError = { errorMsg = it },
                    onFirstChunk = {
                        if (!firstTokenReported) {
                            firstTokenReported = true
                            emit(ReplyFirstToken(requestId, System.currentTimeMillis() - t0))
                        }
                    },
                    onComplete = { usage ->
                        if (usage != null) {
                            emit(
                                ReplyUsage(
                                    requestId,
                                    usage.promptTokens,
                                    usage.completionTokens,
                                    usage.costYuan
                                )
                            )
                        }
                    }
                ).text
                if (fullText.isBlank() && errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true && thinkingShapeIndex < 3) {
                    thinkingShapeIndex++
                    L.w("thinking 参数不支持，降级到候选 $thinkingShapeIndex")
                    continue
                }
                if (fullText.isBlank() && errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true) break
                L.w("PERF t2 stream complete (+${System.currentTimeMillis() - t0}ms, ${fullText.length} chars)")
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                errorMsg = "请求超时，请重试"
                timedOut = true
                if (thinkingShapeIndex < 3) {
                    thinkingShapeIndex++
                    L.w("网络超时，降级到候选 $thinkingShapeIndex")
                    continue
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMsg = e.message ?: "请求异常"
                if (fullText.isBlank() && errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true) break
            }
            if (fullText.isNotBlank()) break
        }

        if (fullText.isBlank()) {
            emitFailed(requestId, ReplyFailureKind.fromErrorMessage(errorMsg))
            return@flow
        }

        val parsed = runCatching { deepSeekRepo.parseReplyResponse(fullText) }
        if (parsed.isFailure) {
            val cause = parsed.exceptionOrNull()
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            L.w("PERF parse failed: ${parsed.exceptionOrNull()?.message} | rawLen=${fullText.length}")
            emitFailed(requestId, ReplyFailureKind.Parse)
            return@flow
        }
        emit(
            ReplyCompleted(
                requestId,
                GenerateResult.Success(parsed.getOrThrow())
            )
        )
        L.w("PERF t3 ★ result rendered (+${System.currentTimeMillis() - t0}ms)")
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ReplyEvent>.emitFailed(
        requestId: String,
        failure: ReplyFailureKind
    ) {
        emit(ReplyCompleted(requestId, GenerateResult.Error(failure.userMessage)))
    }

    // ═══════════ 谈心模式 ═══════════

    /**
     * COUN-01：knowledgeBase 由 ViewModel 传入冻结快照，谈心期间切库不影响 prompt 与日志。
     */
    fun counselingStream(
        requestId: String,
        userMessage: String,
        knowledgeBase: KnowledgeBase?
    ): Flow<CounselingEvent> = flow {
        emit(CounselingStarted(requestId))
        // COUN-01：prompt 与日志都用调用方传入的冻结快照；Provider 配置按当次活跃工单取
        val providerConfig = deepSeekRepo.snapshotProviderConfig()
        if (providerConfig == null) {
            emit(CounselingFailed(requestId, ReplyFailureKind.ProviderMissing.userMessage))
            emit(CounselingEnded(requestId))
            return@flow
        }
        val t0 = System.currentTimeMillis()
        val suffix = "\n\n## 用户倾诉\n" + userMessage.trim() +
            "\n\n## 任务\n请以公正法官的身份，按谈心引擎的回应结构（六步法）回复，末尾按契约附上 ===分析=== 块。"
        val (system, user) = withContext(Dispatchers.IO) {
            promptBuilder.buildCounselingSystemPrompt() to
                promptBuilder.buildCounselingUserPrompt(knowledgeBase, suffix)
        }

        var fullText = ""
        var errorMsg: String? = null
        try {
            fullText = collectStream(
                deepSeekRepo.generateStream(system, user, config = providerConfig),
                AppConfig.GENERATE_TIMEOUT_MS,
                onChunk = { emit(CounselingChunk(requestId, it)) },
                onError = { errorMsg = it },
                onFirstChunk = { emit(CounselingFirstToken(requestId, System.currentTimeMillis() - t0)) }
            ).text
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            errorMsg = "请求超时，请重试"
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            errorMsg = e.message ?: "请求失败，请重试"
        }

        if (fullText.isNotBlank()) {
            val (replyText, analysisText) = splitCounselingAnalysis(fullText)
            emit(CounselingResult(requestId, replyText, analysisText, knowledgeBase?.name, userMessage))
        } else {
            emit(CounselingFailed(requestId, friendlyProviderError(errorMsg)))
        }
        emit(CounselingEnded(requestId))
    }

    private fun friendlyProviderError(errorMsg: String?): String = when {
        errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true ->
            "模型不支持当前思考模式，请更换模型或关闭思考后再试"
        errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true ->
            DeepSeekRepository.stripConfigPrefix(errorMsg.orEmpty())
        else -> errorMsg ?: "未知错误，请重试"
    }

    /** 切分谈心输出：===分析=== 之前 = 回复正文，之后 = 分析块 */
    private fun splitCounselingAnalysis(fullText: String): Pair<String, String> {
        val marker = "===分析==="
        val idx = fullText.indexOf(marker)
        if (idx < 0) return fullText.trim() to ""
        return fullText.substring(0, idx).trim() to fullText.substring(idx + marker.length).trim()
    }

    // ═══════════ 今日锦囊 ═══════════

    /**
     * S1-04：整条流的解析状态属于本次请求——扫描器随流创建，不再放全局单例。
     */
    fun suggestStream(requestId: String, knowledgeBase: KnowledgeBase): Flow<SuggestEvent> = flow {
        emit(SuggestStarted(requestId))
        val parser = PartialTipsParser.newStreamingParser()

        val system = withContext(Dispatchers.IO) { promptBuilder.buildSuggestSystemPrompt() }
        val user = withContext(Dispatchers.IO) { promptBuilder.buildSuggestUserPrompt(knowledgeBase) }
        val providerConfig = deepSeekRepo.snapshotProviderConfig()
        if (providerConfig == null) {
            emit(SuggestFailed(requestId, ReplyFailureKind.ProviderMissing.userMessage))
            emit(SuggestEnded(requestId))
            return@flow
        }

        val buffer = StringBuilder()
        var fullText = ""
        val t0 = System.currentTimeMillis()
        var firstChunkAt = -1L
        var failMsg: String? = null
        var usage: com.lovebrain.app.model.StreamUsage? = null
        try {
            L.w("SUGGEST t0 request enqueued, user=${user.length} chars")
            val sr = collectStream(
                deepSeekRepo.generateStream(system, user, config = providerConfig),
                AppConfig.SUGGEST_TIMEOUT_MS,
                onChunk = { chunk ->
                    if (firstChunkAt < 0) {
                        firstChunkAt = System.currentTimeMillis()
                        emit(SuggestFirstToken(requestId, firstChunkAt - t0))
                        L.w("SUGGEST t1 first chunk (+${firstChunkAt - t0}ms)")
                    }
                    buffer.append(chunk)
                    emit(SuggestTips(requestId, parser.feed(chunk)))
                }
            )
            usage = sr.usage
            fullText = sr.text
            L.w("SUGGEST t2 complete (+${System.currentTimeMillis() - t0}ms, ${buffer.length} chars)")
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            L.w("SUGGEST timeout after ${AppConfig.SUGGEST_TIMEOUT_MS}ms, partial=${buffer.length} chars, firstChunkAt=${if (firstChunkAt < 0) "NONE" else (firstChunkAt - t0)}ms")
            failMsg = "生成超时，请检查网络后重试"
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            L.w("generateSuggest failed: ${e.javaClass.simpleName}")
            failMsg = "生成失败，请点重新生成"
        }

        val suggestion = runCatching { parseSuggestJson(fullText.ifBlank { buffer.toString() }) }.getOrNull()

        // R1-24: 使用 Provider 返回的真实 usage，不硬编码 null
        val finalSuggestion = suggestion?.let { s ->
            s.copy(
                partial = s.tips.size < 6 || failMsg != null,
                usage = com.lovebrain.app.model.DailyBriefUsage(
                    promptTokens = usage?.promptTokens,
                    completionTokens = usage?.completionTokens,
                    costYuan = usage?.costYuan,
                    elapsedMs = System.currentTimeMillis() - t0,
                    generatedAt = com.lovebrain.app.util.TimeFmt.now()
                )
            )
        }
        if (finalSuggestion == null) {
            emit(SuggestFailed(requestId, failMsg ?: "本次没生成出来，请点重新生成"))
        }
        emit(SuggestResult(requestId, finalSuggestion))
        emit(SuggestEnded(requestId))
    }

    // ═══════════ 主动发起/润色 ═══════════

    fun proactiveStream(
        requestId: String,
        draft: String,
        knowledgeBase: KnowledgeBase?,
        messages: List<ChatMessage>
    ): Flow<ProactiveEvent> = flow {
        emit(ProactiveStarted(requestId))
        val providerConfig = deepSeekRepo.snapshotProviderConfig()
        if (providerConfig == null) {
            emit(ProactiveFailed(requestId, ReplyFailureKind.ProviderMissing.userMessage))
            emit(ProactiveEnded(requestId))
            return@flow
        }
        val t0 = System.currentTimeMillis()
        // F17: 使用主动开场 prompt（含画像和近期对话），替代旧润色 prompt
        val user = withContext(Dispatchers.IO) {
            promptBuilder.buildProactiveUserPrompt(draft, knowledgeBase, messages)
        }
        val system = withContext(Dispatchers.IO) { promptBuilder.buildProactiveSystemPrompt() }

        val scanner = IncrementalJsonObjectScanner("options")
        val buffer = StringBuilder()
        val seen = mutableListOf<ProactiveOption>()
        var fullText = ""
        var firstToken = false
        try {
            fullText = collectStream(
                deepSeekRepo.generateStream(system, user, config = providerConfig),
                AppConfig.SUGGEST_TIMEOUT_MS,
                onChunk = { chunk ->
                    if (!firstToken && chunk.isNotBlank()) {
                        firstToken = true
                        emit(ProactiveFirstToken(requestId, System.currentTimeMillis() - t0))
                    }
                    buffer.append(chunk)
                    for (objStr in scanner.feed(chunk)) {
                        val opt = runCatching {
                            jsonLenient.decodeFromString<ProactiveOption>(objStr)
                        }.getOrNull()
                        if (opt != null && opt.text.isNotBlank() && seen.none { it.text == opt.text }) {
                            seen.add(opt)
                            emit(ProactiveOptions(requestId, seen.toList()))
                        }
                    }
                },
                onError = { emit(ProactiveFailed(requestId, friendlyProviderError(it))) }
            ).text
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            emit(ProactiveFailed(requestId, "生成超时，已保留部分内容"))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(
                ProactiveFailed(
                    requestId,
                    "生成失败：${DeepSeekRepository.stripConfigPrefix(e.message ?: "未知错误")}"
                )
            )
        }

        if (seen.isEmpty()) {
            val finalOptions = parseProactiveOptions(fullText.ifBlank { buffer.toString() })
            if (finalOptions.isNotEmpty()) emit(ProactiveOptions(requestId, finalOptions))
        }
        emit(ProactiveEnded(requestId))
    }

    // ═══════════ 内部工具 ═══════════

    /**
     * 统一流式收集（withTimeout + 取消重抛 + 累积兜底）。
     *
     * 回调全部是 suspend：它们直接往同一条事件流上 emit，
     * 不再有"从后台线程反向写 ViewModel"的通道。
     */
    private suspend fun collectStream(
        flow: Flow<StreamEvent>,
        timeoutMs: Long = AppConfig.GENERATE_TIMEOUT_MS,
        onChunk: suspend (String) -> Unit = {},
        onError: suspend (String) -> Unit = {},
        onFirstChunk: suspend () -> Unit = {},
        onComplete: suspend (com.lovebrain.app.model.StreamUsage?) -> Unit = {}
    ): StreamResult {
        val buf = StringBuilder()
        var full = ""
        var firstChunkSeen = false
        var usage: com.lovebrain.app.model.StreamUsage? = null
        withTimeout(timeoutMs) {
            flow.collect { e ->
                when (e) {
                    is StreamEvent.Chunk -> {
                        buf.append(e.text)
                        if (!firstChunkSeen && e.text.isNotBlank()) {
                            firstChunkSeen = true
                            onFirstChunk()
                        }
                        onChunk(e.text)
                    }
                    is StreamEvent.Complete -> {
                        full = e.fullText
                        usage = e.usage
                        onComplete(e.usage)
                    }
                    is StreamEvent.Error -> {
                        onError(e.message)
                        if (e.partialText.isNotBlank()) full = e.partialText
                    }
                }
            }
        }
        return StreamResult(full.ifBlank { buf.toString() }, usage)
    }

    /** collectStream 返回值：完整文本 + Provider usage */
    private data class StreamResult(val text: String, val usage: com.lovebrain.app.model.StreamUsage?)

    /** 流式提取 options 数组里已完整闭合的对象（终态一次性解析用） */
    private fun parseProactiveOptions(raw: String): List<ProactiveOption> {
        val result = mutableListOf<ProactiveOption>()
        for (objStr in PartialJsonObjects.extractObjects(raw, "options")) {
            runCatching { jsonLenient.decodeFromString<ProactiveOption>(objStr) }
                .getOrNull()?.let { if (it.text.isNotBlank() && result.none { o -> o.text == it.text }) result.add(it) }
        }
        return result
    }

    /** 解析锦囊 JSON（容错：提取首个 { } 块）并执行产品合同校验 */
    private fun parseSuggestJson(raw: String): DailySuggestion {
        val jsonStr = Jsons.extractJsonBlock(raw) ?: throw IllegalStateException("锦囊返回格式异常")
        val parsed = jsonLenient.decodeFromString<DailySuggestion>(jsonStr)
        return SuggestValidator.validate(parsed)
    }
}

/** 把 Provider 配置压成"非敏感视图"用于身份比对——绝不带 API Key */
fun ProviderRequestConfig.toView(): ProviderRequestConfigView = ProviderRequestConfigView(
    ticketId = ticketId,
    hostHash = hashProviderHost(baseUrl) ?: "",
    model = model,
    thinkingMode = thinkingMode
)

/**
 * S2-01: 把 Provider 配置冻结成 GenerationInput 里的身份。
 *
 * 覆盖 ticketId/host/model/thinkingMode 四项非敏感配置——
 * 以前只冻 host hash 和 model，用户换了工单（同 host 同模型）本轮识别不出来。
 * API Key 有意不冻结、也不进事件。
 */
fun ProviderRequestConfig.toIdentity(): com.lovebrain.app.model.ProviderIdentity =
    com.lovebrain.app.model.ProviderIdentity(
        ticketId = ticketId,
        hostHash = hashProviderHost(baseUrl) ?: "",
        model = model,
        thinkingMode = thinkingMode
    )
