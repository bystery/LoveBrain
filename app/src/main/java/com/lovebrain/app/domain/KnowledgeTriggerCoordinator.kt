package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.StageSuggestion
import com.lovebrain.app.util.L
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
/** 画像建议构建失败时的原文截断回退长度 */
private const val PROFILE_FALLBACK_LIMIT = 500

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

    /** 回调接口——VM 实现此接口，Coordinator 通过它写回 UI 状态 */
    interface Callbacks {
        fun onVectorUpdated(newVector: Map<String, Int>, delta: Map<String, Int>)
        fun onVectorUpdateNotice(summary: String)
        fun onStageSuggestion(suggestion: StageSuggestion)
        fun onKbNotice(notice: String)
        fun onProfileSuggestion(display: String, rawJson: String)
        fun onCurrentVector(vector: Map<String, Int>)
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

                // A12 三引擎串行：向量重估 → 经验提取 → 画像 reflect（前一引擎完成才开始下一引擎；
                // 各自既有 runCatching 兜底不变，单引擎失败不阻断后续；reflect 最后，天然拿最新向量值）
                if (topicCount % AppConfig.VECTOR_REESTIMATE_INTERVAL == 0) {
                    reestimateVector(kbName, scope, callbacks).join()
                }

                if (topicCount % AppConfig.LESSON_TRIGGER_INTERVAL == 0) {
                    val context = topicRecorder.getTopicFullContext(kbName, AppConfig.LESSON_CONTEXT_TOPICS)
                    extractLessonsAsync(kbName, context, scope, callbacks).join()
                }

                if (topicCount % AppConfig.REFLECT_TRIGGER_INTERVAL == 0) {
                    generateReflectSuggestion(kbName, scope, callbacks).join()
                }
            }.onFailure { L.e("checkKnowledgeTriggers failed", it) }
        }
    }

    /** 重估五维状态向量：读当前向量 + 最近上下文 → AI 重估 → 写回 warmth → 触发阶段建议 */
    private fun reestimateVector(kbName: String, scope: CoroutineScope, callbacks: Callbacks): Job {
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

                // 写回 warmth.md
                withContext(Dispatchers.IO) {
                    knowledgeRepo.writeVector(kbName, newVector)
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
                    knowledgeRepo.appendFile(kbName, "memory/vector_history.md", histEntry + "\n")
                }
                callbacks.onCurrentVector(newVector)
                callbacks.onVectorUpdated(newVector, newVector.mapValues { (k, v) -> v - (oldVector[k] ?: v) })

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
                    callbacks.onVectorUpdateNotice(summary)
                    val time = TimeFmt.now()
                    knowledgeRepo.appendFile(kbName, "memory/reflect_history.md",
                        "\n\n### [$time] 五维向量变化\n$summary")
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

    private fun extractLessonsAsync(kbName: String, topicContext: String, scope: CoroutineScope, callbacks: Callbacks): Job {
        return scope.launch {
            runCatching {
                if (topicContext.isBlank()) return@launch
                val system = promptBuilder.buildLessonsSystemPrompt()
                val user = promptBuilder.buildLessonsUserPrompt(topicContext)
                val lessons = runCatching { deepSeekRepo.generateRaw(system, user) }.getOrDefault("")
                if (lessons.isNotBlank() && lessons != "无新经验") {
                    val existing = withContext(Dispatchers.IO) { knowledgeRepo.readFile(kbName, "memory/lessons.md") }
                    val extractCount = countLessonSections(existing) + 1
                    val time = TimeFmt.now()
                    val entry = "\n\n# [$time] 第${extractCount}次提取\n\n$lessons"
                    knowledgeRepo.appendFile(kbName, "memory/lessons.md", entry)
                    callbacks.onKbNotice("已自动提取新经验，记入知识库「经验」")
                } else {
                    L.w("extractLessons: AI returned empty or no new lessons, skipping")
                }
            }.onFailure { L.e("extractLessonsAsync failed", it) }
        }
    }

    private fun generateReflectSuggestion(kbName: String, scope: CoroutineScope, callbacks: Callbacks): Job {
        return scope.launch {
            runCatching {
                val system = promptBuilder.buildReflectSystemPrompt()
                val user = promptBuilder.buildReflectUserPrompt(kbName)
                val raw = runCatching { deepSeekRepo.generateRaw(system, user) }.getOrDefault("")
                if (raw.isBlank()) {
                    //  ：后台引擎失败轻提示（固定文案；extractLessons 不加，）
                    callbacks.onKbNotice("画像更新建议本次生成失败")
                    return@launch
                }

                // 从 JSON 中提取展示给用户的摘要
                val display = runCatching {
                    val json = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
                    val msg = json["message_to_user"]?.jsonPrimitive?.content ?: ""
                    val obs = json["observations"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                    val stageChanged = json["stage_changed"]?.jsonPrimitive?.boolean ?: false
                    val newStage = json["new_stage"]?.jsonPrimitive?.content ?: ""
                    buildString {
                        if (msg.isNotBlank()) append(msg).append("\n\n")
                        if (stageChanged && newStage.isNotBlank()) {
                            append("【阶段调整建议】→ $newStage\n\n")
                        }
                        if (obs.isNotEmpty()) {
                            append("待验证观察：\n")
                            obs.forEach { append("· $it\n") }
                        }
                    }.trim()
                }.getOrDefault(raw.take(PROFILE_FALLBACK_LIMIT))

                callbacks.onProfileSuggestion(
                    display.ifBlank { "画像更新建议已生成，点击确认写入。" },
                    raw
                )

                val time = TimeFmt.now()
                knowledgeRepo.appendFile(kbName, "memory/reflect_history.md",
                    "\n\n## [$time] 画像更新建议\n$display")
            }.onFailure { L.e("generateReflectSuggestion failed", it) }
        }
    }
}
