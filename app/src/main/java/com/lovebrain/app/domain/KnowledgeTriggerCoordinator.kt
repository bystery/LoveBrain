package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.RawGenerationResult
import com.lovebrain.app.model.ProfileParseResult
import com.lovebrain.app.model.ProfileParseStatus
import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.ProfileUpdate
import com.lovebrain.app.model.ProfileUpdateSchema
import com.lovebrain.app.model.StageSuggestion
import com.lovebrain.app.util.L
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/** 画像建议构建失败时的原文截断回退长度 */
private const val PROFILE_FALLBACK_LIMIT = 500

/** P0-2: 画像生成最大自动尝试次数（含首次） */
private const val MAX_PROFILE_ATTEMPTS = 3

/** P0-2: 重试退避基准间隔（线性递增：第 1 次 500ms，第 2 次 1000ms） */
private const val RETRY_BACKOFF_MS = 500L

/**
 * Attempt 2 使用的严格 JSON-only system prompt。
 * Schema 文案引用 [ProfileUpdateSchema.strictSchemaForPrompt]——单一真源，不另行编造。
 */
private fun buildStrictJsonReflectSystem(): String = """你是一个画像更新引擎。请直接输出一个合法的 JSON 对象，不要使用 Markdown 代码围栏，不要输出任何解释文字，不要重复输入内容。

${ProfileUpdateSchema.strictSchemaForPrompt()}

只输出 JSON 对象本身，不要输出其他任何内容。"""

/**
 * Attempt 3 使用的 compact schema repair system prompt。
 * Schema 文案引用 [ProfileUpdateSchema.compactSchemaForPrompt]——单一真源。
 * P0-4: 不再要求 AI 伪造 "最小合法 JSON"——compact schema 只描述真正合法的画像更新。
 * 达到最大重试次数仍失败时，由 Coordinator 产生 typed failure。
 */
private fun buildCompactSchemaRepairSystem(): String = """请根据以下上下文重新生成合法的画像更新 JSON。只输出 JSON 对象，不要输出其他任何内容。

${ProfileUpdateSchema.compactSchemaForPrompt()}"""

/**
 * 真实提取节头正则（A9 计数口径修正）：与 extractLessonsAsync 写入格式 `# [yyyy-MM-dd HH:mm] 第N次提取` 逐字同构。
 * 不数 schema 模板示例节与其他一级标题——修复 `split("\n# ")` 把示例节计入的多数 bug。
 */
private val LESSON_SECTION_HEADER = Regex("(?m)^# \\[\\d{4}-")

/** 统计 lessons.md 中真实提取节数（A9）：正则失配返回 0（即首次提取=第 1 次，兜底恒成立） */
internal fun countLessonSections(existing: String): Int = LESSON_SECTION_HEADER.findAll(existing).count()


/**
 * 知识库后台触发协调器（ 从 LoveBrainViewModel 拆出）。
 *
 * 职责：检查话题轮换阈值 → 触发经验提取/画像更新/向量重估三引擎。
 * 结果通过 [Callbacks] 写回 ViewModel 的 StateFlow，不直接持有 UI 状态。
 *
 * 循环依赖解决方式：Coordinator 不注入 ViewModel，而是通过 Callbacks 接口回调；
 * 调用时由 ViewModel 传入 [viewModelScope]，Coordinator 自身不持有任何 CoroutineScope。
 */
class KnowledgeTriggerCoordinator(
    private val knowledgeRepo: KnowledgeRepository,
    private val deepSeekRepo: DeepSeekRepository,
    private val promptBuilder: PromptBuilder,
    private val topicRecorder: TopicRecorder
) {

    /** 回调接口——VM 实现此接口，Coordinator 通过它写回 UI 状态
     *  KBG-02/KBG-03：所有后台结果回调携带 originating kbName，防串库 */
    interface Callbacks {
        fun onVectorUpdated(kbName: String, newVector: Map<String, Int>, delta: Map<String, Int>)
        fun onVectorUpdateNotice(kbName: String, summary: String)
        fun onStageSuggestion(suggestion: StageSuggestion)
        fun onKbNotice(notice: String)
        fun onProfileSuggestion(suggestion: ProfileSuggestion)
        fun onCurrentVector(kbName: String, vector: Map<String, Int>)
    }

    /**
     * 检查是否达到经验提取（5 话题）/画像更新（5 话题）/向量重估（3 话题）阈值。
     * A12：在 [scope] 中启动后台协程，串行执行三引擎——向量重估 → 经验提取 → 画像 reflect。
     * 
     *  第 3 步：画像更新频率从"every turn"→"every 50 topics"
     * 使用 AppConfig.REFLECT_TRIGGER_INTERVAL（值=5）控制触发间隔
     */
    fun checkTriggers(kbName: String, scope: CoroutineScope, callbacks: Callbacks) {
        scope.launch {
            runCatching {
                val topicCount = knowledgeRepo.getLessonCount(kbName)
                if (topicCount <= 0) return@runCatching

                // F09: 后台任务启动时冻结 corrections revision，完成后比对防迟到覆盖
                val frozenCorrectionsRev = knowledgeRepo.getCorrectionsRevision(kbName)

                // A12 三引擎串行：向量重估 → 经验提取 → 画像 reflect（前一引擎完成才开始下一引擎；
                // 各自既有 runCatching 兜底不变，单引擎失败不阻断后续；reflect 最后，天然拿最新向量值）
                if (topicCount % AppConfig.VECTOR_REESTIMATE_INTERVAL == 0) {
                    reestimateVector(kbName, scope, callbacks, frozenCorrectionsRev).join()
                }

                if (topicCount % AppConfig.LESSON_TRIGGER_INTERVAL == 0) {
                    val context = topicRecorder.getTopicFullContext(kbName, AppConfig.LESSON_CONTEXT_TOPICS)
                    extractLessonsAsync(kbName, context, scope, callbacks, frozenCorrectionsRev).join()
                }

                if (topicCount % AppConfig.REFLECT_TRIGGER_INTERVAL == 0) {
                    generateReflectSuggestion(kbName, scope, callbacks, frozenCorrectionsRev).join()
                }
            }.onFailure { L.e("checkKnowledgeTriggers failed", it) }
        }
    }

    /** R07: 重估五维状态向量：读当前向量 + 最近上下文 → AI 重估 → 写回前校验 revision → 写回 warmth → 触发阶段建议
     * 后台任务在写入前（而非启动时）校验 revision，防迟到覆盖 */
    private fun reestimateVector(kbName: String, scope: CoroutineScope, callbacks: Callbacks, frozenCorrectionsRev: Int): Job {
        return scope.launch {
            runCatching {
                val oldVector = withContext(Dispatchers.IO) { knowledgeRepo.readVector(kbName) }
                val currentStage = withContext(Dispatchers.IO) { knowledgeRepo.getCurrentStage(kbName) }
                val context = withContext(Dispatchers.IO) { topicRecorder.getVectorContext(kbName) }

                val system = promptBuilder.buildVectorSystemPrompt()
                val user = promptBuilder.buildVectorUserPrompt(oldVector, currentStage, context)
                val raw = runCatching {
                    withContext(Dispatchers.IO) { deepSeekRepo.generateRaw(system, user) }
                }.getOrDefault("")
                if (raw.isBlank()) {
                    //  ：后台引擎失败轻提示（固定文案；extractLessons 不加，）
                    callbacks.onKbNotice("向量重估本次失败，可稍后重试")
                    return@launch
                }

                // 解析新向量
                val newVector = oldVector.toMutableMap()
                val dimNames = mapOf(
                    "亲密度" to "intimacy", "信任度" to "trust", "承诺度" to "commitment",
                    "激情" to "passion", "安全感" to "security"
                )
                for ((cn, en) in dimNames) {
                    val v = Regex("$cn[：:]\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
                    if (v != null) {
                        val clamped = v.coerceIn(0, 100)
                        val old = oldVector[en] ?: 50
                        val delta = clamped - old
                        if (kotlin.math.abs(delta) > 20) {
                            val corrected = old + delta.coerceIn(-20, 20)
                            L.w("VECTOR clamp: $en AI=$clamped old=$old delta=$delta → corrected=$corrected")
                            newVector[en] = corrected
                        } else {
                            newVector[en] = clamped
                        }
                    }
                }
                val reason = raw.substringAfter("===REASON===", "").substringBefore("===STAGE===").trim()
                val suggestedStage = raw.substringAfter("===STAGE===", "").trim()
                    .lines().firstOrNull()?.trim().orEmpty()

                // b3-8: 锁内原子 revision 检查 + 向量写入 + 历史记录，消除竞态窗口
                val ts = TimeFmt.now()
                val labelMap2 = mapOf(
                    "intimacy" to "亲密", "trust" to "信任", "commitment" to "承诺",
                    "passion" to "激情", "security" to "安全"
                )
                val histEntry = buildString {
                    append("## [$ts] 向量重估\n")
                    labelMap2.forEach { (en, label) ->
                        val o = oldVector[en] ?: 50
                        val n = newVector[en] ?: o
                        append("- $label：$o→$n (${if (n > o) "+" else ""}${n - o})\n")
                    }
                    if (reason.isNotBlank()) append("- 依据：$reason\n")
                }
                val vectorWritten = withContext(Dispatchers.IO) {
                    knowledgeRepo.writeVectorWithRevisionCheck(kbName, newVector, frozenCorrectionsRev)
                }
                if (!vectorWritten) {
                    L.w("reestimateVector skipped at write time: corrections changed (frozen=$frozenCorrectionsRev)")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    knowledgeRepo.appendFileWithRevisionCheck(kbName, "memory/vector_history.md", histEntry + "\n", frozenCorrectionsRev)
                }
                callbacks.onCurrentVector(kbName, newVector)
                callbacks.onVectorUpdated(kbName, newVector, newVector.mapValues { (k, v) -> v - (oldVector[k] ?: v) })

                // 生成变化摘要（只列变化的维度）
                val labelMap = mapOf(
                    "intimacy" to "亲密", "trust" to "信任", "commitment" to "承诺",
                    "passion" to "激情", "security" to "安全"
                )
                val changes = labelMap.mapNotNull { (en, label) ->
                    val o = oldVector[en] ?: 50
                    val n = newVector[en] ?: o
                    if (n != o) {
                        val arrow = if (n > o) "↑" else "↓"
                        "$label $o→$n$arrow"
                    } else null
                }
                if (changes.isNotEmpty()) {
                    val summary = "五维更新：" + changes.joinToString("｜") +
                        (if (reason.isNotBlank()) "\n依据：$reason" else "")
                    callbacks.onVectorUpdateNotice(kbName, summary)
                    val time = TimeFmt.now()
                    // b3-8: 使用锁内原子 revision 检查
                    withContext(Dispatchers.IO) {
                        knowledgeRepo.appendFileWithRevisionCheck(kbName, "memory/reflect_history.md",
                            "\n\n### [$time] 五维向量变化\n$summary", frozenCorrectionsRev)
                    }
                }

                // 阶段建议（AI 建议的阶段与当前不同且非"维持"）——经 StageCatalog 归一化（九阶段全带"期"）
                val suggested = StageCatalog.normalize(suggestedStage)
                if (suggested != null && suggested != currentStage) {
                    callbacks.onStageSuggestion(StageSuggestion(
                        kbName = kbName,
                        newStage = suggested,
                        reason = reason.ifBlank { "五维向量变化触发" }
                    ))
                }
            }.onFailure { L.e("reestimateVector failed", it) }
        }
    }

    private fun extractLessonsAsync(kbName: String, topicContext: String, scope: CoroutineScope, callbacks: Callbacks, frozenCorrectionsRev: Int): Job {
        return scope.launch {
            runCatching {
                if (topicContext.isBlank()) return@launch
                val system = promptBuilder.buildLessonsSystemPrompt()
                val user = promptBuilder.buildLessonsUserPrompt(topicContext)
                val lessons = runCatching { deepSeekRepo.generateRaw(system, user) }.getOrDefault("")
                if (lessons.isNotBlank() && lessons != "无新经验") {
                    // b3-8: 锁内原子 revision 检查 + 追加经验，消除竞态窗口
                    val existing = withContext(Dispatchers.IO) { knowledgeRepo.readFile(kbName, "memory/lessons.md") }
                    val extractCount = countLessonSections(existing) + 1
                    val time = TimeFmt.now()
                    val entry = "\n\n# [$time] 第${extractCount}次提取\n\n$lessons"
                    val written = withContext(Dispatchers.IO) {
                        knowledgeRepo.appendFileWithRevisionCheck(kbName, "memory/lessons.md", entry, frozenCorrectionsRev)
                    }
                    if (!written) {
                        L.w("extractLessons skipped at write time: corrections changed (frozen=$frozenCorrectionsRev)")
                        return@launch
                    }
                    callbacks.onKbNotice("已自动提取新经验，记入知识库「经验」")
                } else {
                    L.w("extractLessons: AI returned empty or no new lessons, skipping")
                }
            }.onFailure { L.e("extractLessonsAsync failed", it) }
        }
    }

    /**
     * P1-03: 用户手动触发画像重新生成（不依赖话题计数阈值）。
     * P0-2: 改为 suspend operation——禁止 fire-and-forget 嵌套 launch。
     * 调用方在自身协程中 await，真正持有生成任务的生命周期。
     */
    suspend fun regenerateProfile(kbName: String, scope: CoroutineScope, callbacks: Callbacks) {
        val frozenCorrectionsRev = knowledgeRepo.getCorrectionsRevision(kbName)
        generateReflectSuggestion(kbName, scope, callbacks, frozenCorrectionsRev).join()
    }

    private fun generateReflectSuggestion(kbName: String, scope: CoroutineScope, callbacks: Callbacks, frozenCorrectionsRev: Int): Job {
        return scope.launch {
            runCatching {
                // 所有 attempt 共享同一份冻结上下文——Attempt 3 也不丢弃原始事实
                val system = promptBuilder.buildReflectSystemPrompt()
                val user = promptBuilder.buildReflectUserPrompt(kbName)

                // 分级自愈：
                // Attempt 1: 正常 reflect prompt
                // Attempt 2: 严格 JSON-only prompt（TRUNCATED/EMPTY/INVALID_JSON 时）
                // Attempt 3: Compact schema prompt + 冻结上下文重新生成（不靠残缺 JSON 猜内容）
                var parseResult: ProfileParseResult? = null
                for (attempt in 1..MAX_PROFILE_ATTEMPTS) {
                    val effectiveSystem = when (attempt) {
                        1 -> system
                        2 -> buildStrictJsonReflectSystem()
                        else -> buildCompactSchemaRepairSystem()
                    }

                    // Attempt 3: 携带冻结上下文重新生成，不只用残缺 JSON 猜内容
                    val effectiveUser = if (attempt == 3) {
                        // 区分两种情况：
                        // A. 上次输出语义完整、只是 JSON syntax 小错误 → 可附带残缺输出供修复
                        // B. TRUNCATED（语义内容本身缺失）→ 必须使用冻结上下文重新生成
                        val lastStatus = parseResult?.status
                        val isTruncated = lastStatus == ProfileParseStatus.TRUNCATED
                        if (isTruncated) {
                            // TRUNCATED: 残缺前缀无参考价值，使用原始冻结上下文重新生成
                            user
                        } else {
                            // INVALID_JSON / EMPTY / PROVIDER_ERROR: 语义可能完整，附带残缺输出 + 冻结上下文
                            val lastRaw = parseResult?.rawContent?.take(PROFILE_FALLBACK_LIMIT) ?: ""
                            """以下是原始上下文，请据此重新生成合法的画像更新 JSON。

$user

上次输出存在格式问题，请参考并修正（不要照抄，基于上下文重新生成）：
$lastRaw"""
                        }
                    } else {
                        user
                    }

                    val rawResult = runCatching {
                        withContext(Dispatchers.IO) { deepSeekRepo.generateRawWithMetadata(effectiveSystem, effectiveUser) }
                    }.getOrDefault(RawGenerationResult(content = "", finishReason = null))

                    if (rawResult.content.isBlank() && rawResult.error == null) {
                        // 供应商返回空——可能是偶发，允许重试
                        parseResult = ProfileParseResult(
                            status = ProfileParseStatus.EMPTY,
                            profileUpdate = null,
                            rawContent = "",
                            finishReason = rawResult.finishReason
                        )
                        L.w("generateReflect: attempt $attempt got empty response")
                        continue
                    }
                    if (rawResult.error != null) {
                        // 供应商请求失败——允许重试
                        parseResult = ProfileParseResult(
                            status = ProfileParseStatus.PROVIDER_ERROR,
                            profileUpdate = null,
                            rawContent = rawResult.content,
                            finishReason = rawResult.finishReason
                        )
                        L.w("generateReflect: attempt $attempt provider error: ${rawResult.error.message}")
                        continue
                    }

                    parseResult = ProfileUpdate.parseWithStatus(rawResult.content, rawResult.finishReason)

                    if (parseResult.status == ProfileParseStatus.SUCCESS) {
                        // 成功——跳出重试循环
                        break
                    }

                    if (!parseResult.shouldRetry) {
                        // INVALID_SCHEMA——重试不会改善，直接终止
                        L.w("generateReflect: attempt $attempt status=${parseResult.status}, not retryable")
                        break
                    }

                    L.w("generateReflect: attempt $attempt status=${parseResult.status}, will retry with ${if (attempt == 1) "strict JSON prompt" else "compact schema repair prompt"}")
                    // 短暂退避后重试
                    if (attempt < MAX_PROFILE_ATTEMPTS) {
                        kotlinx.coroutines.delay(RETRY_BACKOFF_MS * attempt)
                    }
                }

                // 处理最终结果
                val finalResult = parseResult
                if (finalResult == null || finalResult.status == ProfileParseStatus.EMPTY) {
                    callbacks.onKbNotice("画像更新建议本次生成失败")
                    return@launch
                }

                if (finalResult.status == ProfileParseStatus.SUCCESS && finalResult.profileUpdate != null) {
                    // 成功——正常展示画像更新建议
                    val profileUpdate = finalResult.profileUpdate
                    val display = profileUpdate.displaySummary

                    callbacks.onProfileSuggestion(
                        ProfileSuggestion(
                            kbName = kbName,
                            display = display,
                            rawJson = finalResult.rawContent,
                            profileUpdate = profileUpdate,
                            correctionsRevision = frozenCorrectionsRev
                        )
                    )

                    // b3-8: 锁内原子 revision 检查 + 追加画像更新历史，消除竞态窗口
                    val time = TimeFmt.now()
                    val written = withContext(Dispatchers.IO) {
                        knowledgeRepo.appendFileWithRevisionCheck(kbName, "memory/reflect_history.md",
                            "\n\n## [$time] 画像更新建议\n$display", frozenCorrectionsRev)
                    }
                    if (!written) {
                        L.w("generateReflect skipped at write time: corrections changed (frozen=$frozenCorrectionsRev)")
                    }
                } else {
                    // P0-2: 截断/格式错误——展示失败建议（带"重新生成"按钮）
                    val errorMsg = when (finalResult.status) {
                        ProfileParseStatus.TRUNCATED -> "画像更新输出不完整，本次未写入任何数据。"
                        ProfileParseStatus.INVALID_JSON -> "画像更新 JSON 解析失败，本次未写入任何数据。"
                        ProfileParseStatus.INVALID_SCHEMA -> "画像更新格式校验失败：${finalResult.profileUpdate?.error ?: "未知错误"}"
                        ProfileParseStatus.PROVIDER_ERROR -> "画像更新请求失败，请稍后重试。"
                        else -> "画像更新失败"
                    }
                    L.w("generateReflect: final status=${finalResult.status}, msg=$errorMsg")

                    callbacks.onProfileSuggestion(
                        ProfileSuggestion(
                            kbName = kbName,
                            display = errorMsg,
                            rawJson = finalResult.rawContent,
                            profileUpdate = finalResult.profileUpdate,
                            correctionsRevision = frozenCorrectionsRev
                        )
                    )
                }
            }.onFailure { L.e("generateReflectSuggestion failed", it) }
        }
    }
}
