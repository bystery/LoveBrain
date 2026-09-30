package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.PromptBudget
import com.lovebrain.app.model.KnowledgeBase

/**
 * 锦囊 user 侧知识段装配（自 `domain/PromptBuilder.kt` 的 `buildSuggestUserPrompt` 行为块搬出）。
 *
 * 这一块把**已经读出来的**阶段 / 温度 / 表达偏好 / 经验 / 进行中事项文本拼成锦囊 user 侧的
 * 知识子集，再过 `PromptBudget.trimSuggestToBudget`。它不读知识库、不读资产、不读时钟，
 * 也不持有状态——所有读取由 `PromptBuilder` 在调用前完成并以 [Input] 传入，因此是纯函数。
 * 搬运前后对同一批输入必须给出逐字相同的字符串（字节证据见
 * `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 阶段为「待确定」/「阶段未确定」/空时不注入关系阶段段。
 * - 温度摘要 `take(300)`；表达偏好 `take(300)`；事项 `take(800)`；经验 `lastH1Blocks(1)` 后 `take(400)`。
 * - 裁剪优先级与 section 顺序由 `PromptBudget.trimSuggestToBudget` 单一真源决定。
 */
object PromptSuggestSection {

    /** 调用方已读出的锦囊上下文输入；空串表示对应文件不存在/不注入 */
    data class Input(
        val stage: String,
        val warmth: String,
        val ongoingPlan: String,
        val style: String,
        val lessons: String
    )

    /** 装配锦囊知识子集（未含时间戳；时间戳由 PromptBuilder 拼到尾部） */
    fun build(input: Input): String = buildString {
        val stage = input.stage.trim()
        if (stage.isNotBlank() && stage != "待确定" && stage != "阶段未确定") {
            append("## 关系阶段\n").append(stage).append("\n\n")
        }
        if (input.warmth.isNotBlank()) {
            append("## 温度摘要\n").append(input.warmth.trim().take(300)).append("\n\n")
        }
        if (input.ongoingPlan.isNotBlank()) {
            append("## 与今天相关的事项\n")
            append(input.ongoingPlan.take(800)).append("\n\n")
        }
        if (input.style.isNotBlank()) {
            append("## 表达偏好\n").append(input.style.trim().take(300)).append("\n\n")
        }
        if (input.lessons.isNotBlank()) {
            val recentLessons = PromptBudget.lastH1Blocks(input.lessons, 1)
            if (recentLessons.isNotBlank()) {
                append("## 需要避开的经验\n")
                append(recentLessons.take(400)).append("\n\n")
            }
        }
    }

    /** 锦囊 user 侧知识子集是否对应 `kb == null`（无库占位） */
    fun isKbNull(kb: KnowledgeBase?): Boolean = kb == null
}
