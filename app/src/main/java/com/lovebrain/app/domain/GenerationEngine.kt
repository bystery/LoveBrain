package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.prompt.MemoryRefPolicy
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.CounselingChunk
import com.lovebrain.app.model.CounselingEnded
import com.lovebrain.app.model.CounselingEvent
import com.lovebrain.app.model.CounselingFailed
import com.lovebrain.app.model.CounselingFirstToken
import com.lovebrain.app.model.CounselingResult
import com.lovebrain.app.model.CounselingStarted
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.KnowledgeBase
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
import com.lovebrain.app.model.ProviderRequestConfigView
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.hashProviderHost
import com.lovebrain.app.model.toChatMessages
import com.lovebrain.app.model.toKnowledgeBase
import com.lovebrain.app.util.IncrementalJsonObjectScanner
import com.lovebrain.app.util.IncrementalSingleObjectScanner
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

/**
 * 生成引擎（从 LoveBrainViewModel 拆出）。
 *
 * ## 接口形状
 * Engine 只暴露冷流：`fun xxxStream(...): Flow<Event>`。
 * 它**不再**接收 CoroutineScope、**不再** launch、**不再**返回 Job、**不再**回调 ViewModel。
 * 于是"谁拥有这个任务"只有一个答案——订阅这条流的协程，也就是
 * [ForegroundOperationCoordinator] 注册出来的那一个。旧写法是 VM 先 launch 一个 prepJob，
 * Engine 再在传入的 scope 上 launch 第二个 Job，两个 owner 并存。
 *
 * 所有事件都带产生时冻结的 requestId，reducer 只认同一个身份。
 */
class GenerationEngine(
    private val deepSeekRepo: AiGateway,
    private val promptBuilder: PromptBuilder
) {

    // ═══════════ 回复生成（主生成） ═══════════

    /**
     * 不可变生成输入入口。
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
            // 按冻结的 ticketId 解析，不回读 activeTicketId
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
            L.w("provider config drifted from the frozen identity, refusing to send")
            emitFailed(requestId, ReplyFailureKind.ProviderChanged)
            return@flow
        }
        // 资产 hash 只做**诊断**，不是防重复使用：本轮的 system / user 文本都是在上面
        // 现读资产拼出来的，所以 hash 不一致的真实含义是"冻结之后资产又被换过一次，
        // 这一轮用的是换过之后的文本"。以前这行注释写的是"仍用冻结时的 prompt 文本"，
        // 那是没有实现的说法。要真正做到冻结构造好的文本，得把 PreparedPrompt
        // 整体装进 GenerationInput（复核 P2 给的两个选项里的另一个）。
        val liveAssetHash = promptBuilder.replyPromptAssetHash()
        if (input.promptAssetHash.isNotBlank() && liveAssetHash != input.promptAssetHash) {
            L.w("prompt assets changed after freeze (frozen=${input.promptAssetHash.take(8)})")
        }

        val user = buildResult.prompt
        // 「仅看本轮」那一档不存在旧记忆引用：清单在这道边界上过一次唯一规则源，
        // 于是界面侧无论拿到什么，开着开关的那一轮都拿不到"看起来被引用过"的条目（第10节第3条）。
        emit(
            ReplyMemoryRefs(
                requestId,
                MemoryRefPolicy.refsForRoundScope(buildResult.memoryRefs, input.onlyThisRound)
            )
        )
        emit(ReplySourceAliasMap(requestId, buildResult.sourceAliasMap))
        L.w("PERF t1 prompt built (+${System.currentTimeMillis() - t0}ms), user=${user.length} chars")

        var fullText = ""
        var failure: com.lovebrain.app.model.ProviderFailure? = null
        var timedOut = false
        var thinkingShapeIndex = 0
        var attemptsUsed = 0
        var firstTokenReported = false

        while (attemptsUsed < AppConfig.GENERATE_MAX_ATTEMPTS) {
            attemptsUsed++
            // 每个 attempt 一套独立的增量解析状态，防上一次失败 attempt 的半成品污染重试
            val responseScanner = IncrementalSingleObjectScanner("response")

            if (attemptsUsed > 1) {
                val degradeMsg = when {
                    failure?.thinkingParamRejected == true -> "参数不支持，正在降级…"
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
                failure = null
                fullText = collectStream(
                    deepSeekRepo.generateStream(
                        system, user, thinkingOverride, thinkingShapeIndex, config = providerConfig
                    ),
                    // 主生成总超时按**这张工单**的档位走（慢速兼容服务会被固定 120 秒盖住）。
                    // 档位是有界白名单，最大 300 秒——不是无限等待。
                    providerConfig.generateTimeoutMs,
                    onChunk = { chunk ->
                        emit(ReplyChunk(requestId, chunk))
                        // 只把新增文本交给扫描器，不再 toString() 全量重扫
                        val objStr = responseScanner.feed(chunk)
                        if (objStr != null) {
                            val schemes = runCatching {
                                jsonLenient.decodeFromString<com.lovebrain.app.model.ReplySchemes>(objStr).toSchemes()
                            }.getOrDefault(emptyList())
                            if (schemes.isNotEmpty()) emit(ReplySchemesArrived(requestId, schemes))
                        }
                    },
                    onError = { failure = it },
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
                if (fullText.isBlank() && failure?.thinkingParamRejected == true && thinkingShapeIndex < 3) {
                    thinkingShapeIndex++
                    L.w("thinking 参数不支持，降级到候选 $thinkingShapeIndex")
                    continue
                }
                if (fullText.isBlank() && failure?.isConfigProblem == true) break
                L.w("PERF t2 stream complete (+${System.currentTimeMillis() - t0}ms, ${fullText.length} chars)")
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                failure = com.lovebrain.app.model.ProviderFailure(
                    com.lovebrain.app.model.ReplyFailureKind.Timeout
                )
                timedOut = true
                if (thinkingShapeIndex < 3) {
                    thinkingShapeIndex++
                    L.w("网络超时，降级到候选 $thinkingShapeIndex")
                    continue
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val caught = com.lovebrain.app.model.ProviderFailure.fromThrowable(e)
                failure = caught
                if (fullText.isBlank() && caught.isConfigProblem) break
            }
            if (fullText.isNotBlank()) break
        }

        if (fullText.isBlank()) {
            emitFailed(requestId, failure?.kind ?: ReplyFailureKind.Unknown(null))
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
     * knowledgeBase 由 ViewModel 传入冻结快照，谈心期间切库不影响 prompt 与日志。
     */
    fun counselingStream(
        requestId: String,
        userMessage: String,
        knowledgeBase: KnowledgeBase?
    ): Flow<CounselingEvent> = flow {
        emit(CounselingStarted(requestId))
        // prompt 与日志都用调用方传入的冻结快照；Provider 配置按当次活跃工单取
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
        var failure: com.lovebrain.app.model.ProviderFailure? = null
        try {
            fullText = collectStream(
                deepSeekRepo.generateStream(system, user, config = providerConfig),
                // 谈心与主生成同一个等待预算：它走的也是这一条长回复链路，
                // 工单调到 300 秒时不该只有主生成放开、谈心还在 120 秒上截。
                providerConfig.generateTimeoutMs,
                onChunk = { emit(CounselingChunk(requestId, it)) },
                onError = { failure = it },
                onFirstChunk = { emit(CounselingFirstToken(requestId, System.currentTimeMillis() - t0)) }
            ).text
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            failure = com.lovebrain.app.model.ProviderFailure(
                com.lovebrain.app.model.ReplyFailureKind.Timeout
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failure = com.lovebrain.app.model.ProviderFailure.fromThrowable(e)
        }

        if (fullText.isNotBlank()) {
            val (replyText, analysisText) = splitCounselingAnalysis(fullText)
            emit(CounselingResult(requestId, replyText, analysisText, knowledgeBase?.name, userMessage))
        } else {
            emit(CounselingFailed(requestId, friendlyProviderError(failure)))
        }
        emit(CounselingEnded(requestId))
    }

    /**
     * 展示文案：分类已在 Provider 边界做完，这里只按 typed 结果选话术，
     * 不再从前缀或异常 message 反推错误类型。
     */
    private fun friendlyProviderError(failure: com.lovebrain.app.model.ProviderFailure?): String = when {
        failure == null -> "未知错误，请重试"
        failure.thinkingParamRejected ->
            "模型不支持当前思考模式，请更换模型或关闭思考后再试"
        else -> failure.message
    }

    /** 切分谈心输出：===分析=== 之前 = 回复正文，之后 = 分析块 */
    private fun splitCounselingAnalysis(fullText: String): Pair<String, String> {
        val marker = "===分析==="
        val idx = fullText.indexOf(marker)
        if (idx < 0) return fullText.trim() to ""
        return fullText.substring(0, idx).trim() to fullText.substring(idx + marker.length).trim()
    }

    // ═══════════ 主动发（含并入的话题切入点能力） ═══════════

    /**
     * 主动发一句：一次点击 = **一条**生成请求。
     *
     * 锦囊（第12节第1条 已删）那条独立请求流不在了，它"根据已有近况找合适话题/切入点、
     * 结合有效持续意图输出一句能发的话"这一半能力是**并进这条流的 prompt**里长出来的
     * （`engine/proactive.md` + `PromptProactiveSection`），不是在这里串两次模型调用：
     * 整条流只碰一次 [AiGateway.generateStream]，事件按 ProactiveStarted → 增量
     * ProactiveOptions → ProactiveEnded 的顺序在同一条流上出完。
     *
     * 结束事件排在最后一批 options **之后**，而 [com.lovebrain.app.feature.proactive.ProactiveStore]
     * 收到它时一个字都不写——所以生成完那几条开场一直留在屏幕上可复制（）。
     *
     * ⚠ **[onlyThisRound]：「仅看本轮」这条开关判据（此前主动发整条链路读不到它）**
     * 主动发过去**无条件**吃 KB 画像与近期历史，于是"仅看本轮"总开关画在公共输入区、
     * 主动发却悄悄继续用旧记忆——那是产品级假开关。现在的判据与回复分支同语义、
     * 同一个真源（这颗布尔由调用方按 `RoundStateStore` 的开关 ∨ 这一次点名只读本轮算出来，
     * 沿回复那条 [GenerationInput.onlyThisRound] 的算法，不在这里二次现读开关）：
     * - `true` → 请求正文只有**当前对话 + 当前草稿/备注**（加通用规则、输出格式、
     *   由本轮推断的场景与当前时间）；画像、阶段、历史聊天/近期对话、经验、旧事项、
     *   表达偏好、持续意图**一条都不进**，因为 [PromptBuilder.buildProactiveUserPrompt]
     *   在这一档**一次知识库读取都不做**。
     * - `false` → 普通分支带全部相关记忆（画像 + 近期对话 + 表达偏好），并且**先拼本轮真实输入**
     *   （备注 / 本轮场景 / 本轮真实对话），再拼相关记忆。
     *
     * [advisorNote] 是本轮军师备注（`ReplyDirective` 那一份）。原始第 18 条之前，普通分支只读画像/近期
     * 对话、从不引用 [messages] 与 [advisorNote]——用户补了背景但主动发看不见；现在两档都把它们拼进请求正文，
     * 表达偏好也从 `understand/style.md` 真接了来源（不再是资产里写了却没接入的假声明）。
     */
    fun proactiveStream(
        requestId: String,
        draft: String,
        knowledgeBase: KnowledgeBase?,
        messages: List<ChatMessage>,
        onlyThisRound: Boolean = false,
        advisorNote: String = ""
    ): Flow<ProactiveEvent> = flow {
        emit(ProactiveStarted(requestId))
        val providerConfig = deepSeekRepo.snapshotProviderConfig()
        if (providerConfig == null) {
            emit(ProactiveFailed(requestId, ReplyFailureKind.ProviderMissing.userMessage))
            emit(ProactiveEnded(requestId))
            return@flow
        }
        val t0 = System.currentTimeMillis()
        // 使用主动开场 prompt：开关关闭时含画像和近期对话，开启时只用本轮草稿/备注与当前对话
        val user = withContext(Dispatchers.IO) {
            promptBuilder.buildProactiveUserPrompt(
                draft, knowledgeBase, messages, onlyThisRound, advisorNote
            )
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
                // 主动开场这一道吃的是**这张工单冻结出来的等待预算**（原来钉在固定 45 秒上，
                // 和主回复不是同一个承诺）。副作用要认：慢服务上这一屏可能转到满档为止，
                // 但 options 是边流边出的，第几条出来就先显示第几条，不是干等。
                // 这里仍**只放开读的那一侧**：连接与写超时保持全局固定值，不跟档。
                providerConfig.generateTimeoutMs,
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
            L.w("PROACTIVE timeout after ${providerConfig.generateTimeoutMs}ms, options=${seen.size}")
            emit(ProactiveFailed(requestId, "生成超时，已保留部分内容"))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(
                ProactiveFailed(
                    requestId,
                    "生成失败：${com.lovebrain.app.model.ProviderFailure.fromThrowable(e).message}"
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
     *
     * ⚠ [timeoutMs] 的默认值只是"忘了传"时的保险丝（= 默认档 120 秒）。
     * 三条生成链路的调用点现在都**显式交**这张工单冻结出来的预算
     * （`ProviderRequestConfig.generateTimeoutMs`，见 `GenerationTimeoutTier`），
     * 主生成/谈心/主动开场靠的都是那条显式实参，不是这里，也不再各自留固定秒数。
     */
    private suspend fun collectStream(
        flow: Flow<StreamEvent>,
        timeoutMs: Long = AppConfig.GENERATE_TIMEOUT_MS,
        onChunk: suspend (String) -> Unit = {},
        onError: suspend (com.lovebrain.app.model.ProviderFailure) -> Unit = {},
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
                        onError(e.failure)
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
}

/** 把 Provider 配置压成"非敏感视图"用于身份比对——绝不带 API Key */
fun ProviderRequestConfig.toView(): ProviderRequestConfigView = ProviderRequestConfigView(
    ticketId = ticketId,
    hostHash = hashProviderHost(baseUrl) ?: "",
    model = model,
    thinkingMode = thinkingMode
)

/**
 * 把 Provider 配置冻结成 GenerationInput 里的身份。
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
