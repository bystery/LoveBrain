package com.lovebrain.app.domain.prompt

/**
 * 向量重估 user 侧段装配（自 `domain/PromptBuilder.kt` 的 `buildVectorUserPrompt` 行为块搬出）。
 *
 * 这一块是纯表示层：给定五维向量与当前阶段、最近对话场景文本，写出 reflect 用的固定格式段。
 * 它不读知识库、不读资产、不读时钟，也不持有状态，因此是纯函数。搬运前后对同一批输入
 * 必须给出逐字相同的字符串（字节证据见 `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 五维默认值 50；阶段空时显示「待确定」。
 * - 末尾固定任务句逐字保留。
 */
object PromptVectorSection {

    /** 装配向量重估 user 侧段 */
    fun build(currentVector: Map<String, Int>, currentStage: String, context: String): String = buildString {
        append("## 当前五维向量\n")
        append("- 亲密度：").append(currentVector["intimacy"] ?: 50).append("\n")
        append("- 信任度：").append(currentVector["trust"] ?: 50).append("\n")
        append("- 承诺度：").append(currentVector["commitment"] ?: 50).append("\n")
        append("- 激情：").append(currentVector["passion"] ?: 50).append("\n")
        append("- 安全感：").append(currentVector["security"] ?: 50).append("\n\n")
        append("## 当前阶段：").append(currentStage.ifBlank { "待确定" }).append("\n\n")
        append("## 最近的对话与场景\n").append(context.trim()).append("\n\n")
        append("请按输出格式重估五维向量并给出阶段建议。")
    }
}
