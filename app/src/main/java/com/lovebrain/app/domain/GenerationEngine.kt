package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.PanelState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.util.Jsons
import com.lovebrain.app.util.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
/** 降级提示驻留时长（reset 前） */
private const val DEGRADE_HINT_HOLD_MS = 1000L


/**
 * 流式增量解析公共切分器：从累积的 JSON 文本中提取指定 key 数组里的所有"已完整到达"对象。
 * 不要求 JSON 整体闭合——每个 `{...}` 对象一旦匹配到结尾 `}` 就返回，
 * 实现"边流式边逐条渲染"。字符串内的括号/引号会被正确跳过。
 *  加固：先剥离 ```json / ``` 包装再匹配，兼容 AI 偶尔输出 markdown 代码块。
 */
object PartialJsonObjects {
    /** 从累积文本中提取 key 对应数组里所有完整对象（原始字符串） */
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

    /** 提取 "key": {...} 中已完整闭合的对象字符串；未闭合返回 null（流式提前渲染用） */
    fun extractKeyObject(raw: String, key: String): String? {
        val buffer = raw.replace("```json", "").replace("```", "")
        val start = buffer.indexOf("\"$key\"")
        if (start < 0) return null
        val brace = buffer.indexOf('{', start)
        if (brace < 0) return null
        return completeObjectAt(buffer, brace)
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

/** 锦囊 tips 流式解析（逐条渲染） */
object PartialTipsParser {
    fun parseCompleted(buffer: String): List<com.lovebrain.app.model.SuggestTip> {
        val result = mutableListOf<com.lovebrain.app.model.SuggestTip>()
        for (objStr in PartialJsonObjects.extractObjects(buffer, "tips")) {
            runCatching { jsonLenient.decodeFromString<com.lovebrain.app.model.SuggestTip>(objStr) }.getOrNull()?.let { tip ->
                if (tip.example.isNotBlank() && result.none { it.example == tip.example }) {
                    result.add(tip)
                }
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
        fun onCounselingSaveLog(userMessage: String, replyText: String, analysisText: String)

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
    }

    // ═══════════ 回复生成（主生成） ═══════════

    fun generate(scope: CoroutineScope, callbacks: Callbacks): Job {
        val msgs = callbacks.getMessages()
        if (msgs.isEmpty() || callbacks.isGenerating()) return scope.launch { }

        callbacks.onReplyStart()
        callbacks.onReplyPanelState(PanelState.AI_LOADING)
        callbacks.onReplyGenerating(true, true)
        callbacks.onReplyStreamingCoreTextReset()
        callbacks.onReplyStreamingSchemes(emptyList())

        val t0 = System.currentTimeMillis()
        L.w("PERF t0 click generate")

        return scope.launch {
            val aggressive = callbacks.getOutputMode() == 1
            val system = withContext(Dispatchers.IO) { promptBuilder.buildSystemPrompt() }
            val user = withContext(Dispatchers.IO) {
                promptBuilder.buildReplyUserPrompt(callbacks.getActiveKb(), msgs, callbacks.getUserHint(), aggressive)
            }
            L.w("PERF t1 prompt built (+${System.currentTimeMillis() - t0}ms), user=${user.length} chars")

            var fullText = ""
            var errorMsg: String? = null
            val rawBuffer = StringBuilder()
            var timedOut = false
            // /：thinking 降级与重试共用 GENERATE_MAX_ATTEMPTS=4 总预算（3→4 使候选 ④ none 可达）
            var thinkingShapeIndex = 0
            var attemptsUsed = 0
            // ：首字耗时只报第一次（重试链不重复上报）
            var firstTokenReported = false

            while (attemptsUsed < AppConfig.GENERATE_MAX_ATTEMPTS) {
                attemptsUsed++
                if (attemptsUsed > 1) {
                    //  修复：区分参数降级 / 网络超时 / 网络重试的文案口径，不再一律误报"网络波动"
                    val degradeMsg = when {
                        errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true -> "参数不支持，正在降级…"
                        timedOut -> "网络波动，正在重试…"
                        else -> "网络波动，第 ${attemptsUsed - 1} 次重试中…"
                    }
                    callbacks.onReplyStreamingCoreText(degradeMsg)
                    delay(DEGRADE_HINT_HOLD_MS)
                    callbacks.onReplyStreamingCoreTextReset()
                }
                try {
                    val thinkingOverride = if (timedOut) 0 else null
                    errorMsg = null  // 每次重试重置错误
                    fullText = collectStream(
                        deepSeekRepo.generateStream(system, user, thinkingOverride, thinkingShapeIndex),
                        AppConfig.GENERATE_TIMEOUT_MS,
                        onChunk = { chunk ->
                            callbacks.onReplyStreamingCoreText(chunk)
                            rawBuffer.append(chunk)
                            val respObj = PartialJsonObjects.extractKeyObject(rawBuffer.toString(), "response")
                            if (respObj != null) {
                                val schemes = runCatching {
                                    jsonLenient.decodeFromString<com.lovebrain.app.model.ReplySchemes>(respObj).toSchemes()
                                }.getOrDefault(emptyList())
                                callbacks.onReplyStreamingSchemes(schemes)
                            }
                        },
                        onError = { errorMsg = it },
                        onFirstChunk = {
                            if (!firstTokenReported) {
                                firstTokenReported = true
                                callbacks.onFirstToken(System.currentTimeMillis() - t0)
                            }
                        }
                    )
                    //  段 B：收到 PARAM_UNSUPPORTED 错误 → 换下一候选 wire shape
                    if (fullText.isBlank() && errorMsg != null && errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true && thinkingShapeIndex < 3) {
                        thinkingShapeIndex++
                        L.w("thinking 参数不支持，降级到候选 $thinkingShapeIndex")
                        continue
                    }
                    // ：CONFIG_ERROR 类配置错误（无工单/缺 Key/无效 Key/非法地址）不重试，立即退出；
                    // 展示前经 stripConfigPrefix 去前缀（见下方兜底分支）
                    if (fullText.isBlank() && errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true) break
                    L.w("PERF t2 stream complete (+${System.currentTimeMillis() - t0}ms, ${fullText.length} chars)")
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    errorMsg = "请求超时，请重试"
                    timedOut = true
                    // ：超时也触发降级（换下一 wire shape 或关闭 thinking）
                    //  修复③：条件显式带上 timedOut，日志区分"网络超时"口径
                    if (timedOut && thinkingShapeIndex < 3) {
                        thinkingShapeIndex++
                        L.w("网络超时，降级到候选 $thinkingShapeIndex")
                        continue
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorMsg = e.message ?: "请求异常"
                    // ：异常携带的配置类错误（normalizeBaseUrl/HttpsTrustGuard 固定文案）同样不重试，立即退出；
                    // 展示前经下方兜底分支 stripConfigPrefix 去前缀（?.let 形态与 :239 同口径，防闭包捕获 smart cast 坑）
                    if (fullText.isBlank() && errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true) break
                }
                if (fullText.isNotBlank()) break
            }

            if (fullText.isBlank()) {
                //  修复：预算耗尽后，PARAM_UNSUPPORTED 内部标记转人话兜底文案，不直接展示裸标记；
                // ：CONFIG_ERROR 类配置错误去前缀透传原文案（本就是固定人话）
                val userFriendlyMsg = when {
                    errorMsg?.startsWith("PARAM_UNSUPPORTED:") == true ->
                        "模型不支持当前思考模式，已尝试所有降级方案"
                    errorMsg?.let { DeepSeekRepository.isConfigError(it) } == true ->
                        DeepSeekRepository.stripConfigPrefix(errorMsg.orEmpty())
                    else -> errorMsg ?: "生成失败，请重试"
                }
                callbacks.onReplyResult(GenerateResult.Error(userFriendlyMsg))
                callbacks.onReplyGenerating(false, false)
                callbacks.onReplyStreamingCoreTextReset()
                callbacks.onReplyPanelState(PanelState.AI_RESULT)
                return@launch
            }

            val parsed = runCatching { deepSeekRepo.parseReplyResponse(fullText) }
            if (parsed.isFailure) {
                callbacks.onReplyResult(GenerateResult.Error(parsed.exceptionOrNull()?.message ?: "解析失败，请重试"))
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

    fun generateCounseling(userMessage: String, scope: CoroutineScope, callbacks: Callbacks): Job {
        if (userMessage.isBlank() || callbacks.isCounseling()) return scope.launch { }

        callbacks.onCounselingStart()

        return scope.launch {
            // ：谈心首字耗时计时起点（复用回复流程 t0 口径）
            val t0 = System.currentTimeMillis()
            val suffix = "\n\n## 用户倾诉\n" + userMessage.trim() +
                "\n\n## 任务\n请以公正法官的身份，按谈心引擎的回应结构（六步法）回复，末尾按契约附上 ===分析=== 块。"
            val (system, user) = withContext(Dispatchers.IO) {
                promptBuilder.buildCounselingSystemPrompt() to
                    promptBuilder.buildCounselingUserPrompt(callbacks.getActiveKb(), suffix)
            }

            var fullText = ""
            var errorMsg: String? = null
            try {
                fullText = collectStream(
                    deepSeekRepo.generateStream(system, user),
                    AppConfig.GENERATE_TIMEOUT_MS,
                    onChunk = { callbacks.onCounselingStreaming(it) },
                    onError = { errorMsg = it },
                    onFirstChunk = { callbacks.onFirstToken(System.currentTimeMillis() - t0) }
                )
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                errorMsg = "请求超时，请重试"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMsg = e.message ?: "请求失败，请重试"
            }

            if (fullText.isNotBlank()) {
                val (replyText, analysisText) = splitCounselingAnalysis(fullText)
                callbacks.onCounselingResult(replyText)
                callbacks.onCounselingSaveLog(userMessage, replyText, analysisText)
            } else {
                //  修复：PARAM_UNSUPPORTED 是内部标记，不直接展示给用户（谈心无降级链，转通用人话）；
                // ：CONFIG_ERROR 类配置错误去前缀透传（谈心单次调用，无重试面）
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

    fun generateSuggest(scope: CoroutineScope, callbacks: Callbacks): Job {
        val kb = callbacks.getActiveKb()
        if (kb == null) {
            // ：无 KB 不做死路——锦囊区给引导提示
            callbacks.onSuggestError("还没有知识库，请先到设置页创建")
            return scope.launch { }
        }
        if (callbacks.isSuggesting()) return scope.launch { }

        callbacks.onSuggestStart()

        return scope.launch(Dispatchers.Main) {
            val system = withContext(Dispatchers.IO) { promptBuilder.buildSuggestSystemPrompt() }
            val user = withContext(Dispatchers.IO) {
                promptBuilder.buildSuggestUserPrompt(kb)
            }

            val buffer = StringBuilder()
            var fullText = ""
            val t0 = System.currentTimeMillis()
            var firstChunkAt = -1L
            // ：弱网超时/异常/解析失败——记录错因，拿不到结果时告知 UI
            var failMsg: String? = null
            try {
                L.w("SUGGEST t0 request enqueued, user=${user.length} chars")
                fullText = collectStream(
                    deepSeekRepo.generateStream(system, user),
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
            if (suggestion == null) {
                // ：超时但部分内容解析成功时不报错（成功优先）；彻底无结果才提示
                callbacks.onSuggestError(failMsg ?: "本次没生成出来，请点重新生成")
            }
            callbacks.onSuggestResult(suggestion)
            callbacks.onSuggestEnd()
        }
    }

    // ═══════════ 主动发起/润色 ═══════════

    fun generateProactive(draft: String, scene: String, scope: CoroutineScope, callbacks: Callbacks): Job {
        if (callbacks.isProactive()) return scope.launch { }

        callbacks.onProactiveStart()

        return scope.launch(Dispatchers.Main) {
            // ：主动发首字耗时计时起点（复用回复流程 t0 口径）
            val t0 = System.currentTimeMillis()
            //  规格：scene 参数保留但不再注入；user 仅草稿（无时间戳/无知识/无场景）
            val user = withContext(Dispatchers.IO) { promptBuilder.buildPolishUserPrompt(draft) }
            val system = withContext(Dispatchers.IO) { promptBuilder.buildPolishSystemPrompt() }

            val buffer = StringBuilder()
            var fullText = ""
            try {
                fullText = collectStream(
                    deepSeekRepo.generateStream(system, user),
                    AppConfig.SUGGEST_TIMEOUT_MS,
                    onChunk = { chunk ->
                        buffer.append(chunk)
                        val parsed = parseProactiveOptions(buffer.toString())
                        callbacks.onProactiveStreamingOptions(parsed)
                    },
                    onFirstChunk = { callbacks.onFirstToken(System.currentTimeMillis() - t0) }
                )
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                callbacks.onProactiveError("生成超时，已保留部分内容")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // ：异常文案可能携带 CONFIG_ERROR 前缀（建链前校验抛出），展示前去前缀防裸标记外露
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
        // ：首个非空 chunk 回调（首字耗时上报；只触发一次，不影响降级/重试链）
        onFirstChunk: (() -> Unit)? = null,
    ): String {
        val buf = StringBuilder()
        var full = ""
        var firstChunkSeen = false
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
                    is StreamEvent.Complete -> full = e.fullText
                    is StreamEvent.Error -> {
                        onError(e.message)
                        if (e.partialText.isNotBlank()) full = e.partialText
                    }
                }
            }
        }
        return full.ifBlank { buf.toString() }
    }

    /** 流式提取 options 数组里已完整闭合的对象 */
    private fun parseProactiveOptions(raw: String): List<com.lovebrain.app.model.ProactiveOption> {
        val result = mutableListOf<com.lovebrain.app.model.ProactiveOption>()
        for (objStr in PartialJsonObjects.extractObjects(raw, "options")) {
            runCatching { jsonLenient.decodeFromString<com.lovebrain.app.model.ProactiveOption>(objStr) }
                .getOrNull()?.let { if (it.text.isNotBlank() && result.none { o -> o.text == it.text }) result.add(it) }
        }
        return result
    }

    /** 解析锦囊 JSON（容错：提取首个 { } 块） */
    private fun parseSuggestJson(raw: String): com.lovebrain.app.model.DailySuggestion {
        val jsonStr = Jsons.extractJsonBlock(raw)
            ?: throw IllegalStateException("锦囊返回格式异常")
        return jsonLenient.decodeFromString<com.lovebrain.app.model.DailySuggestion>(jsonStr)
    }
}
