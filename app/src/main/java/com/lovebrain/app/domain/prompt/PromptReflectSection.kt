package com.lovebrain.app.domain.prompt

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.PromptBudget

/**
 * 画像反思 user 侧段装配（自 `domain/PromptBuilder.kt` 的 `buildReflectUserPrompt` 行为块搬出）。
 *
 * 这一块把**已经读出来的**画像 / 经验 / 话题档案 / 谈心分析文本拼成 reflect user 侧段。
 * 它不读知识库、不读资产、不读时钟，也不持有状态——所有读取由 `PromptBuilder` 在调用前
 * 完成并以 [Input] 传入，因此是纯函数。搬运前后对同一批输入必须给出逐字相同的字符串
 * （字节证据见 `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 画像三件（me/her/warmth）逐字 trim 后分 `### 文件名` 小节拼接。
 * - 经验 `lastH1Blocks(2)`；话题档案 `lastH1Blocks(REFLECT_CONTEXT_TOPICS)`；谈心分析原样追加。
 * - 末尾固定任务句逐字保留。
 */
object PromptReflectSection {

    /** 调用方已读出的反思上下文输入；空串表示对应文件不存在/不注入 */
    data class Input(
        val me: String,
        val her: String,
        val warmth: String,
        val lessons: String,
        val rawTopic: String,
        val counselingAnalysis: String
    )

    /** 装配 reflect user 侧段 */
    fun build(input: Input): String = buildString {
        append("## 当前画像\n\n")
        append("### me.md\n").append(input.me.trim()).append("\n\n")
        append("### her.md\n").append(input.her.trim()).append("\n\n")
        append("### warmth.md\n").append(input.warmth.trim()).append("\n\n")
        if (input.lessons.isNotBlank()) {
            append("## 最近经验（最近2次提取）\n\n")
                .append(PromptBudget.lastH1Blocks(input.lessons, 2)).append("\n\n")
        }
        if (input.rawTopic.isNotBlank()) {
            append("## 最近话题档案（最近${AppConfig.REFLECT_CONTEXT_TOPICS}个话题）\n\n")
                .append(PromptBudget.lastH1Blocks(input.rawTopic, AppConfig.REFLECT_CONTEXT_TOPICS)).append("\n\n")
        }
        if (input.counselingAnalysis.isNotBlank()) {
            append("## 谈心分析（最近2次）\n\n")
                .append(input.counselingAnalysis as CharSequence).append("\n\n")
        }
        append("## 任务\n请根据以上经验和话题档案，按画像更新引擎的格式，输出 JSON 格式的完整覆写版本。")
    }
}
