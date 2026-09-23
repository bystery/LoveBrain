package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.ProviderRequestConfig
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.PanelState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.util.Jsons
import com.lovebrain.app.util.L
import com.lovebrain.app.model.toChatMessages
import com.lovebrain.app.model.toKnowledgeBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
/** 降级提示驻留时长（reset 前） */
private const val DEGRADE_HINT_HOLD_MS = 1000L

/** P3-04: 增量 JSON 解析阈值——rawBuffer 长度变化达到此值才重新解析 */
private const val JSON_PARSE_THRESHOLD_CHARS = 50


/**
 * 流式增量解析公共切分器：从累积的 JSON 文本中提取指定 key 数组里的所有"已完整到达"对象。
 * 不要求 JSON 整体闭合——每个 `{...}` 对象一旦匹配到结尾 `}` 就返回，
 * 实现"边流式边逐条渲染"。字符串内的括号/引号会被正确跳过。
 *  加固：先剥离 ```json / ``` 包装再匹配，兼容 AI 偶尔输出 markdown 代码块。
 *
 *  P3-04: 游标优化——IncrementalJsonScanner 保存上一次解析位置，
 *  不从 buffer 开头重新扫描整个已解析区域。
 */
object PartialJsonObjects {
    /**
     * 从累积文本中提取 key 对应数组里所有完整对象（原始字符串）。
     *
     * P3-04: 如果有游标状态，使用 [extractObjectsIncremental] 避免全量重扫。
     */
    fun extractObjects(raw: String, key: String): List<String> {
        val buffer = raw.replace("```json", "").replace("```", "")
        val start = buffer.indexOf("\"$key\"")
        if (start < 0) return emptyList()
        val arrStart = buffer.indexOf('[', start)
        if (arrStart < 0) return emptyList()

        val result = mutableListOf<String>()
        var i = arrStart + 1
        while (i < buffer.length) {
            while (i < buffer.length && (buffer[i] == ' ' || buffer[i] == '\n' || buffer[i] == '\r' || buffer[i] == ',')) i++
            if (i >= buffer.length || buffer[i] != '{') break

            val objStr = completeObjectAt(buffer, i) ?: break
            result.add(objStr)
            i = i + objStr.length
        }
        return result
    }

    /**
     * P3-04: 增量提取——使用游标避免全量重扫。
     *
     * [scanner] 保存上一次解析到的位置和已提取的对象数。
     * 只扫描新增部分，已提取的对象直接复用。
     */
    fun extractObjectsIncremental(
        raw: String,
        key: String,
        scanner: IncrementalJsonScanner
    ): List<String> {
        val buffer = raw.replace("```json", "").replace("```", "")

        // 如果 buffer 缩短了（重试清空），重置游标
        if (buffer.length < scanner.lastBufferLen) {
            scanner.reset()
        }
        scanner.lastBufferLen = buffer.length

        // 首次：定位数组起始
        if (scanner.arrayStartPos < 0) {
            val keyPos = buffer.indexOf("\"$key\"")
            if (keyPos < 0) return scanner.extractedObjects.toList()
            val arrStart = buffer.indexOf('[', keyPos)
            if (arrStart < 0) return scanner.extractedObjects.toList()
            scanner.arrayStartPos = arrStart
        }

        // 从上次位置继续扫描
        var i = scanner.scanPos.coerceAtLeast(scanner.arrayStartPos + 1)
        while (i < buffer.length) {
            while (i < buffer.length && (buffer[i] == ' ' || buffer[i] == '\n' || buffer[i] == '\r' || buffer[i] == ',')) i++
            if (i >= buffer.length || buffer[i] != '{') break

            val objStr = completeObjectAt(buffer, i) ?: break
            scanner.extractedObjects.add(objStr)
            i = i + objStr.length
            scanner.scanPos = i
        }
        if (i < buffer.length) {
            // 未到末尾（可能跳过空白后遇到 ']'），更新游标
            scanner.scanPos = i
        } else {
            scanner.scanPos = buffer.length
        }
        return scanner.extractedObjects.toList()
    }

    /** 找到从 startPos 开始的字符串的结束引号位置（跳过转义） */
    private fun findStringEnd(buffer: String, startPos: Int): Int {
        var j = startPos + 1
        while (j < buffer.length) {
            when (buffer[j]) {
                '\\' -> j += 2 // 跳过转义字符
                '"' -> return j
                else -> j++
            }
        }
        return -1 // 未闭合
    }

    /** 简易 JSON 字符串反转义 */
    private fun unescapeJsonString(s: String): String {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
    }

    /** 提取 "key": {...} 中已完整闭合的对象字符串；未闭合返回 null（流式提前渲染用） */
    fun extractKeyObject(raw: String, key: String): String? {
        val buffer = raw.replace("```json", "").replace("```", "")
        val start = buffer.indexOf("\"$key\"")
        if (start < 0) return null
        val brace = buffer.indexOf('{', start)
        if (brace < 0) return null
        return completeObjectAt(buffer, brace)
    }

    /**
     * P3-04: 增量 JSON 扫描器——保存游标位置和已提取对象，避免全量重扫。
     *
     * 用法：
     * ```
     * val scanner = IncrementalJsonScanner()
     * // 每次收到新 chunk 后调用
     * val objects = PartialJsonObjects.extractObjectsIncremental(rawBuffer, "tips", scanner)
     * ```
     */
    class IncrementalJsonScanner {
        internal var arrayStartPos: Int = -1
        internal var scanPos: Int = -1
        internal var lastBufferLen: Int = 0
        internal val extractedObjects: MutableList<String> = mutableListOf()

        fun reset() {
            arrayStartPos = -1
            scanPos = -1
            lastBufferLen = 0
            extractedObjects.clear()
        }
    }

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

/** 锦囊 tips 流式解析（逐条渲染）。
 * R1-28: 流式和最终解析共用 SuggestValidator 的去重/校验规则，保证一致性。
 * P3-04: 使用 IncrementalJsonScanner 避免全量重扫。 */
object PartialTipsParser {
    /** P3-04: 增量解析——保存游标，只扫描新增部分 */
    private val tipsScanner = PartialJsonObjects.IncrementalJsonScanner()

    /** P3-04: 重置游标（重试/新请求时调用） */
    fun resetScanner() {
        tipsScanner.reset()
    }

    fun parseCompleted(buffer: String): List<com.lovebrain.app.model.SuggestTip> {
        val result = mutableListOf<com.lovebrain.app.model.SuggestTip>()
        val seenIds = mutableSetOf<String>()
        val seenActions = mutableSetOf<String>()
        // P3-04: 使用增量扫描器避免全量重扫
        for (objStr in PartialJsonObjects.extractObjectsIncremental(buffer, "tips", tipsScanner)) {
            runCatching { jsonLenient.decodeFromString<com.lovebrain.app.model.SuggestTip>(objStr) }.getOrNull()?.let { tip ->
                val action = tip.action.trim()
                if (action.isBlank()) return@let
                if (action in seenActions) return@let
                seenActions.add(action)
                val stableId = if (tip.id.isNotBlank() && tip.id !in seenIds) {
                    tip.id
                } else {
                    "tip-${result.size + 1}"
                }
                if (stableId in seenIds) return@let
                seenIds.add(stableId)
                result.add(tip.copy(id = stableId, action = action))
            }
        }
        return result
    }
}

/** 宽松 JSON（流式提前渲染 response 对象用） */
val jsonLenient = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/**
 * 生成引擎（ 从 LoveBrainViewModel 拆出）。
 *
 * 职责：回复/谈心/锦囊/主动发的提示词组装、流式收集、解析、重试与超时降级。
 * 通过 [Callbacks] 实时写回 ViewModel 的 StateFlow（流式过程中的每 token 都走回调）。
 *
 * 与  的 KnowledgeTriggerCoordinator 同理：Engine 不注入 ViewModel，
 * 调用时由 VM 传入 [viewModelScope]。
 */
class GenerationEngine(
    private val deepSeekRepo: DeepSeekRepository,
    private val promptBuilder: PromptBuilder
) {

    /** 回调接口——VM 实现此接口，Engine 通过它实时写回 UI 状态 */
    interface Callbacks {
        // ═══ 回复生成 ═══
        fun onReplyStart()
        fun onReplyStreamingCoreText(chunk: String)
        fun onReplyStreamingSchemes(schemes: List<Scheme>)
        fun onReplyStreamingSchemesReset()
        fun onReplyResult(result: GenerateResult)
        fun onReplyPanelState(state: PanelState)
        fun onReplyGenerating(isGenerating: Boolean, isGeneratingCore: Boolean)
        fun onReplyStreamingCoreTextReset()

        // ═══ 谈心 ═══
        fun onCounselingStart()
        fun onCounselingStreaming(chunk: String)
        fun onCounselingResult(text: String)
        fun onCounselingError(error: String)
        fun onCounselingEnd()
        fun onCounselingSaveLog(kbName: String?, userMessage: String, replyText: String, analysisText: String)

        // ═══ 锦囊 ═══
        fun onSuggestStart()
        fun onSuggestStreamingTips(tips: List<com.lovebrain.app.model.SuggestTip>)
        fun onSuggestResult(suggestion: com.lovebrain.app.model.DailySuggestion?)
        fun onSuggestEnd()
        fun onSuggestLog(msg: String)
        fun onSuggestError(msg: String)

        // ═══ 主动发起 ═══
        fun onProactiveStart()
        fun onProactiveStreamingOptions(options: List<com.lovebrain.app.model.ProactiveOption>)
        fun onProactiveError(error: String)
        fun onProactiveEnd()

        // ═══ 共用 ═══
        /** ：首字耗时上报（四流程首个非空 chunk 到达；重试链只报第一次） */
        fun onFirstToken(elapsedMs: Long)
        fun getActiveKb(): KnowledgeBase?
        fun getMessages(): List<ChatMessage>
        fun getUserHint(): String
        fun isGenerating(): Boolean
        fun isCounseling(): Boolean
        fun isSuggesting(): Boolean
        fun isProactive(): Boolean
        fun getOutputMode(): Int

        // 回报本轮注入的 MemoryRef 清单（供 UI 展示纠正入口）
        fun onReplyMemoryRefs(refs: List<com.lovebrain.app.model.MemoryRef>) {}
        // 回报来源别名→实际消息ID映射（供 TopicRecorder 来源校验用）
        fun onReplySourceAliasMap(aliasMap: Map<String, String>) {}
    }

    // ═══════════ 回复生成（主生成） ═══════════

    /**
     * S2-01: 不可变生成输入入口——替代散参的 [generate] 方法。
     *
     * 所有输入通过 [GenerationInput] 冻结：requestId、dialogue（只含 PARTNER/USER）、
     * replyDirective（hint + aggressive）、kbContext、intentConfig、corrections、
     * onlyThisRound、providerIdentity。
     *
     * 进入 domain 后不再出现 ChatMessage.Role.IDEA——dialogue 只能是 PARTNER/USER。
     * ReplyDirective 独立，不能作为事实、topic、scene、ongoing 或 recent 证据。
     *
     * S2-03: 事件携带 input.requestId，reducer 只接受 owner 匹配的事件。
     */
    fun generateReply(
        input: GenerationInput,
        scope: CoroutineScope,
        callbacks: Callbacks
    ): Job? {
        if (input.dialogue.isEmpty() || callbacks.isGenerating()) return null

        // S2-01 审计修复: 从 GenerationInput 提取冻结参数——不再转回旧散参
        // toChatMessages() 是 GenerationInput 的适配器方法，用于 PromptBuilder 的历史接口。
        // 审计要求：GenerationInput 直接进入 Prompt/Provider 层。
        // 当前限制：PromptBuilder 仍接受 ChatMessage/KnowledgeBase，适配器在 GenerationInput 上
        // 已实现。完全迁移需要 PromptBuilder 接口改为接受 DialogueMessage/KbContext。
        val requestId = input.requestId
        val messages = input.toChatMessages()
        val userHint = input.replyDirective.text
        val knowledgeBase = input.kbContext?.toKnowledgeBase()
        val aggressive = input.replyDirective.aggressive

        callbacks.onReplyStart()
        callbacks.onReplyPanelState(PanelState.AI_LOADING)
        callbacks.onReplyGenerating(true, true)
        callbacks.onReplyStreamingCoreTextReset()
        callbacks.onReplyStreamingSchemesReset()

        val t0 = System.currentTimeMillis()
        L.w("PERF t0 click generate (requestId=${requestId.take(8)})")

        return scope.launch {
            // F02: 准备阶段（system prompt 构建、知识背景、来源引用、Provider 快照）
            // 包裹在 try/catch 中——准备阶段的读盘、迁移、状态写入异常不在原有网络请求的 try/catch 保护范围内。
            // 该路径异常可能逃出前台 launch 导致 App 崩溃。
            val system: String
            val buildResult: PromptBuilder.PromptBuildResult
            var providerConfig: ProviderRequestConfig? = null
            try {
                system = withContext(Dispatchers.IO) { promptBuilder.buildSystemPrompt() }
                // F09: 使用 buildReplyUserPromptWithRefs 收集 MemoryRef 清单并应用纠正过滤
                // F10: onlyThisRound=true 时只携带通用规则、本轮真实消息和想法
                // S2-01: 参数全部来自冻结的 GenerationInput，不读 callbacks 实时状态
                buildResult = withContext(Dispatchers.IO) {
                    if (input.onlyThisRound) {
                        promptBuilder.buildReplyUserPromptOnlyThisRound(messages, userHint)
                    } else {
                        promptBuilder.buildReplyUserPromptWithRefs(knowledgeBase, messages, userHint, aggressive, input.intentConfig, input.corrections)
                    }
                }
                // PROV-01：整轮生成开始时冻结 Provider 身份
                // S2-01 审计修复: providerIdentity 已在 GenerationInput 中冻结（host hash + model）。
                // 此处仍需 snapshotProviderConfig() 获取完整请求配置（含 API Key）——
                // API Key 不冻结在 input 中是正确的（安全考虑）。
                // 但 provider identity（host/model）应从 input 验证一致性。
                providerConfig = deepSeekRepo.snapshotProviderConfig()
                if (providerConfig != null && input.providerIdentity != null) {
                    // 验证冻结时的 provider 与当前配置一致——不一致时仍使用当前快照
                    val frozenHash = input.providerIdentity!!.hostHash
                    val currentHash = providerConfig.baseUrl?.let { it.hashCode().toString(16) }
                    if (frozenHash != currentHash) {
                        L.w("S2-01: provider changed since freeze (frozen=$frozenHash, current=$currentHash)")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e  // 取消单独处理并继续传播
            } catch (e: Exception) {
                // S1-02: 准备阶段异常——使用 ReplyFailureKind 映射，不泄露路径/Provider/内部细节
                L.e("prepare phase exception", e)
                val failure = com.lovebrain.app.model.ReplyFailureKind.fromException(e)
                callbacks.onReplyResult(GenerateResult.Error(failure.userMessage))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyStreamingCoreTextReset()
                callbacks.onReplyPanelState(PanelState.AI_RESULT)
                return@launch
            }
            val user = buildResult.prompt
            // 回报 MemoryRef 清单
            callbacks.onReplyMemoryRefs(buildResult.memoryRefs)
            // 回报来源别名映射
            callbacks.onReplySourceAliasMap(buildResult.sourceAliasMap)
            L.w("PERF t1 prompt built (+${System.currentTimeMillis() - t0}ms), user=${user.length} chars")

            if (providerConfig == null) {
                callbacks.onReplyResult(GenerateResult.Error("请先配置一个可用的模型供应商"))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyStreamingCoreTextReset()
                callbacks.onReplyPanelState(PanelState.AI_RESULT)
                return@launch
            }

            var fullText = ""
            var errorMsg: String? = null
            var timedOut = false
            // thinking 降级与重试共用 GENERATE_MAX_ATTEMPTS 总预算
            var thinkingShapeIndex = 0
            var attemptsUsed = 0
            // ：首字耗时只报第一次（重试链不重复上报）
            var firstTokenReported = false
            // P3-04: 增量 JSON 解析阈值——达到多少字符变化才重新解析
            var lastParseRawLen = 0

            while (attemptsUsed < AppConfig.GENERATE_MAX_ATTEMPTS) {
                attemptsUsed++
                // GEN-04：每个 attempt 拥有独立 rawBuffer，防上一次失败 attempt 的 partial JSON 污染下一次 retry
                val rawBuffer = StringBuilder()
                if (attemptsUsed > 1) {
                    // 区分参数降级 / 网络超时 / 网络重试的文案口径
                    val degradeMsg = when {
                        errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true -> "参数不支持，正在降级…"
                        timedOut -> "网络波动，正在重试…"
                        else -> "网络波动，第 ${attemptsUsed - 1} 次重试中…"
                    }
                    callbacks.onReplyStreamingCoreText(degradeMsg)
                    delay(DEGRADE_HINT_HOLD_MS)
                    callbacks.onReplyStreamingCoreTextReset()
                    // GEN-04：retry 前清理上一次 attempt 的流式方案卡
                    callbacks.onReplyStreamingSchemesReset()
                }
                try {
                    val thinkingOverride = if (timedOut) 0 else null
                    errorMsg = null  // 每次重试重置错误
                    fullText = collectStream(
                        // PROV-01：所有 retry attempt 使用同一个 providerConfig 快照
                        deepSeekRepo.generateStream(system, user, thinkingOverride, thinkingShapeIndex, config = providerConfig),
                        AppConfig.GENERATE_TIMEOUT_MS,
                        onChunk = { chunk ->
                            callbacks.onReplyStreamingCoreText(chunk)
                            rawBuffer.append(chunk)
                            // P3-04 审计修复: 增量 JSON 解析——不每次全量 toString() + 重新解析
                            // 只在 rawBuffer 长度变化达到一定阈值时才解析
                            // 这减少了每 token 的 O(n) 解析开销
                            val rawLen = rawBuffer.length
                            val lastParseLen = lastParseRawLen
                            if (rawLen - lastParseLen >= JSON_PARSE_THRESHOLD_CHARS) {
                                lastParseRawLen = rawLen
                                val respObj = PartialJsonObjects.extractKeyObject(rawBuffer.toString(), "response")
                                if (respObj != null) {
                                    val schemes = runCatching {
                                        jsonLenient.decodeFromString<com.lovebrain.app.model.ReplySchemes>(respObj).toSchemes()
                                    }.getOrDefault(emptyList())
                                    if (schemes.isNotEmpty()) {
                                        callbacks.onReplyStreamingSchemes(schemes)
                                    }
                                    // P1-07: 不再在生成中 stream directions——
                                    // directions 只在最终 response 完成后提供切换，避免半成品状态链
                                }
                            }
                        },
                        onError = { errorMsg = it },
                        onFirstChunk = {
                            if (!firstTokenReported) {
                                firstTokenReported = true
                                callbacks.onFirstToken(System.currentTimeMillis() - t0)
                            }
                        }
                    ).text
                    // 收到 PARAM_UNSUPPORTED 错误 → 换下一候选 wire shape
                    if (fullText.isBlank() && errorMsg != null && errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true && thinkingShapeIndex < 3) {
                        thinkingShapeIndex++
                        L.w("thinking 参数不支持，降级到候选 $thinkingShapeIndex")
                        continue
                    }
                    // CONFIG_ERROR 类配置错误不重试，立即退出；展示前去前缀
                    if (fullText.isBlank() && errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true) break
                    L.w("PERF t2 stream complete (+${System.currentTimeMillis() - t0}ms, ${fullText.length} chars)")
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    errorMsg = "请求超时，请重试"
                    timedOut = true
                    // 超时也触发降级
                    if (timedOut && thinkingShapeIndex < 3) {
                        thinkingShapeIndex++
                        L.w("网络超时，降级到候选 $thinkingShapeIndex")
                        continue
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorMsg = e.message ?: "请求异常"
                    // 异常携带配置类错误同样不重试，立即退出
                    if (fullText.isBlank() && errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true) break
                }
                if (fullText.isNotBlank()) break
            }

            if (fullText.isBlank()) {
                // S1-02: 使用 ReplyFailureKind 统一错误映射
                val failure = com.lovebrain.app.model.ReplyFailureKind.fromErrorMessage(errorMsg)
                callbacks.onReplyResult(GenerateResult.Error(failure.userMessage))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyStreamingCoreTextReset()
                callbacks.onReplyPanelState(PanelState.AI_RESULT)
                return@launch
            }

            val parsed = runCatching { deepSeekRepo.parseReplyResponse(fullText) }
            if (parsed.isFailure) {
                // S1-02: 解析失败使用 ReplyFailureKind
                val cause = parsed.exceptionOrNull()
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                callbacks.onReplyResult(GenerateResult.Error(com.lovebrain.app.model.ReplyFailureKind.Parse.userMessage))
                L.w("PERF parse failed: ${parsed.exceptionOrNull()?.message} | rawLen=${fullText.length}")
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyStreamingCoreTextReset()
                callbacks.onReplyPanelState(PanelState.AI_RESULT)
                return@launch
            }

            callbacks.onReplyGenerating(false, true) // isGenerating=false, isGeneratingCore→will be set false next
            callbacks.onReplyStreamingCoreTextReset()
            callbacks.onReplyResult(GenerateResult.Success(parsed.getOrThrow()))
            L.w("PERF t3 ★ result rendered (+${System.currentTimeMillis() - t0}ms)")

            callbacks.onReplyGenerating(false, false)
            callbacks.onReplyPanelState(PanelState.AI_RESULT)
        }
    }

    // ═══════════ 谈心模式 ═══════════

    /**
     * GEN-01：返回 Job? — reject 时返回 null，不创建假 Job 覆盖调用方引用。
     * COUN-01：knowledgeBase 由 ViewModel 传入冻结快照，谈心期间切 KB 不影响 prompt 与日志。
     */
    fun generateCounseling(
        userMessage: String,
        knowledgeBase: KnowledgeBase?,
        scope: CoroutineScope,
        callbacks: Callbacks
    ): Job? {
        if (userMessage.isBlank() || callbacks.isCounseling()) return null

        callbacks.onCounselingStart()

        return scope.launch {
            // PROV-01：冻结 Provider 身份
            val providerConfig = deepSeekRepo.snapshotProviderConfig()
            if (providerConfig == null) {
                callbacks.onCounselingError("请先配置一个可用的模型供应商")
                callbacks.onCounselingEnd()
                return@launch
            }
            // ：谈心首字耗时计时起点（复用回复流程 t0 口径）
            val t0 = System.currentTimeMillis()
            val suffix = "\n\n## 用户倾诉\n" + userMessage.trim() +
                "\n\n## 任务\n请以公正法官的身份，按谈心引擎的回应结构（六步法）回复，末尾按契约附上 ===分析=== 块。"
            // COUN-01：使用冻结的 knowledgeBase 快照，不读 callbacks.getActiveKb()
            val (system, user) = withContext(Dispatchers.IO) {
                promptBuilder.buildCounselingSystemPrompt() to
                    promptBuilder.buildCounselingUserPrompt(knowledgeBase, suffix)
            }

            var fullText = ""
            var errorMsg: String? = null
            try {
fullText = collectStream(
// PROV-01：使用冻结的 providerConfig
deepSeekRepo.generateStream(system, user, config = providerConfig),
AppConfig.GENERATE_TIMEOUT_MS,
onChunk = { callbacks.onCounselingStreaming(it) },
onError = { errorMsg = it },
onFirstChunk = { callbacks.onFirstToken(System.currentTimeMillis() - t0) }
).text
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                errorMsg = "请求超时，请重试"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMsg = e.message ?: "请求失败，请重试"
            }

            if (fullText.isNotBlank()) {
                val (replyText, analysisText) = splitCounselingAnalysis(fullText)
                callbacks.onCounselingResult(replyText)
                callbacks.onCounselingSaveLog(knowledgeBase?.name, userMessage, replyText, analysisText)
            } else {
                // PARAM_UNSUPPORTED 是内部标记，不直接展示给用户；CONFIG_ERROR 去前缀透传
                val userFriendlyMsg = when {
                    errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true ->
                        "模型不支持当前思考模式，请更换模型或关闭思考后再试"
                    errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true ->
                        DeepSeekRepository.stripConfigPrefix(errorMsg.orEmpty())
                    else ->
                        errorMsg ?: "未知错误，请重试"
                }
                callbacks.onCounselingError(userFriendlyMsg)
            }
            callbacks.onCounselingEnd()
        }
    }

    /** 切分谈心输出：===分析=== 之前 = 回复正文，之后 = 分析块 */
    private fun splitCounselingAnalysis(fullText: String): Pair<String, String> {
        val marker = "===分析==="
        val idx = fullText.indexOf(marker)
        if (idx < 0) return fullText.trim() to ""
        return fullText.substring(0, idx).trim() to fullText.substring(idx + marker.length).trim()
    }

    // ═══════════ 今日锦囊 ═══════════

    /** GEN-01：返回 Job? — reject 时返回 null，不创建假 Job 覆盖调用方引用。 */
    fun generateSuggest(scope: CoroutineScope, callbacks: Callbacks): Job? {
        val kb = callbacks.getActiveKb()
        if (kb == null) {
            // ：无 KB 不做死路——锦囊区给引导提示
            callbacks.onSuggestError("还没有知识库，请先到设置页创建")
            return null
        }
        if (callbacks.isSuggesting()) return null

        callbacks.onSuggestStart()
        // P3-04: 重置增量 JSON 扫描器（新请求/重试时）
        PartialTipsParser.resetScanner()

        return scope.launch(Dispatchers.Main) {
            // R1-32: 整条 suggest transaction 使用 try/finally，确保 onSuggestEnd 始终被调用
            try {
            val system = withContext(Dispatchers.IO) { promptBuilder.buildSuggestSystemPrompt() }
            val user = withContext(Dispatchers.IO) {
                promptBuilder.buildSuggestUserPrompt(kb)
            }

            // PROV-01：冻结 Provider 身份
            val providerConfig = deepSeekRepo.snapshotProviderConfig()
            if (providerConfig == null) {
                callbacks.onSuggestError("请先配置一个可用的模型供应商")
                return@launch
            }

            val buffer = StringBuilder()
            var fullText = ""
            val t0 = System.currentTimeMillis()
            var firstChunkAt = -1L
            // ：弱网超时/异常/解析失败——记录错因，拿不到结果时告知 UI
            var failMsg: String? = null
            var streamResult: StreamResult? = null
            try {
                L.w("SUGGEST t0 request enqueued, user=${user.length} chars")
val sr = collectStream(
// PROV-01：使用冻结的 providerConfig
deepSeekRepo.generateStream(system, user, config = providerConfig),
AppConfig.SUGGEST_TIMEOUT_MS,
onChunk = { chunk ->
if (firstChunkAt < 0) {
firstChunkAt = System.currentTimeMillis()
callbacks.onFirstToken(firstChunkAt - t0) // ：锦囊复用既有首 chunk 时点上报（单次流无重试，天然幂等）
L.w("SUGGEST t1 first chunk (+${firstChunkAt - t0}ms)")
}
buffer.append(chunk)
val parsed = PartialTipsParser.parseCompleted(buffer.toString())
callbacks.onSuggestStreamingTips(parsed)
}
)
streamResult = sr
fullText = sr.text
                L.w("SUGGEST t2 complete (+${System.currentTimeMillis() - t0}ms, ${buffer.length} chars)")
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                callbacks.onSuggestLog("SUGGEST timeout after ${AppConfig.SUGGEST_TIMEOUT_MS}ms, partial=${buffer.length} chars, firstChunkAt=${if (firstChunkAt < 0) "NONE" else (firstChunkAt - t0)}ms")
                failMsg = "生成超时，请检查网络后重试"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                callbacks.onSuggestLog("generateSuggest failed: ${e.javaClass.simpleName}")
                failMsg = "生成失败，请点重新生成"
            }

            val suggestion = runCatching {
                parseSuggestJson(fullText.ifBlank { buffer.toString() })
            }.getOrNull()

            // R1-24: 使用 Provider 返回的真实 usage，不再硬编码 null
            // R1-32: finally 确保无论异常/解析失败都调用 onSuggestEnd
            val suggestUsage = streamResult?.usage
            val finalSuggestion = suggestion?.let { s ->
                val tipsCount = s.tips.size
                val isPartial = tipsCount < 6 || failMsg != null
                s.copy(
                    partial = isPartial,
                    usage = com.lovebrain.app.model.DailyBriefUsage(
                        promptTokens = suggestUsage?.promptTokens,
                        completionTokens = suggestUsage?.completionTokens,
                        costYuan = suggestUsage?.costYuan,
                        elapsedMs = System.currentTimeMillis() - t0,
                        generatedAt = com.lovebrain.app.util.TimeFmt.now()
                    )
                )
            }

            if (finalSuggestion == null) {
                // 超时但部分内容解析成功时不报错（成功优先）；彻底无结果才提示
                callbacks.onSuggestError(failMsg ?: "本次没生成出来，请点重新生成")
            }
            callbacks.onSuggestResult(finalSuggestion)
            } finally {
                // R1-32: finally 确保无论异常/解析失败都调用 onSuggestEnd
                callbacks.onSuggestEnd()
            }
        }
    }

    // ═══════════ 主动发起/润色 ═══════════

    /** GEN-01：返回 Job? — reject 时返回 null，不创建假 Job 覆盖调用方引用。 */
    fun generateProactive(draft: String, scene: String, scope: CoroutineScope, callbacks: Callbacks): Job? {
        if (callbacks.isProactive()) return null

        callbacks.onProactiveStart()

        return scope.launch(Dispatchers.Main) {
            // PROV-01：冻结 Provider 身份
            val providerConfig = deepSeekRepo.snapshotProviderConfig()
            if (providerConfig == null) {
                callbacks.onProactiveError("请先配置一个可用的模型供应商")
                callbacks.onProactiveEnd()
                return@launch
            }
            // ：主动发首字耗时计时起点（复用回复流程 t0 口径）
            val t0 = System.currentTimeMillis()
            // F17: 使用新的主动开场 prompt（含画像和近期对话），替代旧润色 prompt
            val activeKb = callbacks.getActiveKb()
            val user = withContext(Dispatchers.IO) {
                promptBuilder.buildProactiveUserPrompt(draft, activeKb, callbacks.getMessages())
            }
            val system = withContext(Dispatchers.IO) { promptBuilder.buildProactiveSystemPrompt() }

            val buffer = StringBuilder()
            var fullText = ""
            try {
fullText = collectStream(
// PROV-01：使用冻结的 providerConfig
deepSeekRepo.generateStream(system, user, config = providerConfig),
AppConfig.SUGGEST_TIMEOUT_MS,
onChunk = { chunk ->
buffer.append(chunk)
val parsed = parseProactiveOptions(buffer.toString())
callbacks.onProactiveStreamingOptions(parsed)
},
onFirstChunk = { callbacks.onFirstToken(System.currentTimeMillis() - t0) }
).text
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                callbacks.onProactiveError("生成超时，已保留部分内容")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 异常文案可能携带 CONFIG_ERROR 前缀，展示前去前缀
                callbacks.onProactiveError("生成失败：${DeepSeekRepository.stripConfigPrefix(e.message ?: "未知错误")}")
            }

            val finalOptions = parseProactiveOptions(fullText.ifBlank { buffer.toString() })
            if (finalOptions.isNotEmpty()) callbacks.onProactiveStreamingOptions(finalOptions)
            callbacks.onProactiveEnd()
        }
    }

    // ═══════════ 内部工具 ═══════════

    /**
     * 统一流式收集（withTimeout + 取消重抛 + 累积兜底）。
     * onChunk 用于各引擎的"提前渲染"；超时抛 TimeoutCancellationException 由调用方决定降级。
     */
    private suspend fun collectStream(
        flow: kotlinx.coroutines.flow.Flow<StreamEvent>,
        timeoutMs: Long = AppConfig.GENERATE_TIMEOUT_MS,
        onChunk: (String) -> Unit = {},
        onError: (String) -> Unit = {},
            // 首个非空 chunk 回调（首字耗时上报；只触发一次）
        onFirstChunk: (() -> Unit)? = null,
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
                            onFirstChunk?.invoke()
                        }
                        onChunk(e.text)
                    }
                    is StreamEvent.Complete -> {
                        full = e.fullText
                        usage = e.usage
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

    /** 流式提取 options 数组里已完整闭合的对象 */
    private fun parseProactiveOptions(raw: String): List<com.lovebrain.app.model.ProactiveOption> {
        val result = mutableListOf<com.lovebrain.app.model.ProactiveOption>()
        for (objStr in PartialJsonObjects.extractObjects(raw, "options")) {
            runCatching { jsonLenient.decodeFromString<com.lovebrain.app.model.ProactiveOption>(objStr) }
                .getOrNull()?.let { if (it.text.isNotBlank() && result.none { o -> o.text == it.text }) result.add(it) }
        }
        return result
    }

    /** 解析锦囊 JSON（容错：提取首个 { } 块）并执行产品合同校验 */
    private fun parseSuggestJson(raw: String): com.lovebrain.app.model.DailySuggestion {
        val jsonStr = Jsons.extractJsonBlock(raw)
            ?: throw IllegalStateException("锦囊返回格式异常")
        val parsed = jsonLenient.decodeFromString<com.lovebrain.app.model.DailySuggestion>(jsonStr)
        return SuggestValidator.validate(parsed)
    }

}
