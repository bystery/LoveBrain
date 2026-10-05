package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.PromptBudget

/**
 * 谈心的核心知识子集装配（自 `domain/PromptBuilder.kt` 的 `buildCoreKnowledgeSubset`
 * 及其三个 `appendXxxSection` 辅助函数搬出）。
 *
 * 这一块把**已经读出来的**画像 / 经验 / 进行中事项文本拼成核心知识子集。它不读知识库、
 * 不读资产、不读时钟、不调 selector，也不持有状态——所有读取由 `PromptBuilder` 在调用前
 * 完成并以 [Input] 传入，因此是纯函数。搬运前后对同一批输入必须给出逐字相同的字符串
 * （字节证据见 `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 画像段与回复知识段逐字同源：me/her/warmth/style 非空才拼，标题固定。
 * - 经验段 `lastH1Blocks(3)`。
 * - 进行中事项段：`ongoingPlan` 非空才拼，固定段标题与括注。
 * - 无阶段节选、无此刻、无最近对话（与回复知识段的差异点）。
 */
object PromptCoreKnowledgeSection {

    /** 调用方已读出的核心知识子集输入；空串表示对应文件不存在/不注入 */
    data class Input(
        val me: String,
        val her: String,
        val warmth: String,
        val style: String,
        val lessons: String,
        val ongoingPlan: String
    )

    /** 装配核心知识子集 */
    fun build(input: Input): String {
        val sb = StringBuilder()
        // # 【懂得】关系画像
        sb.append("# 【懂得】关系画像\n")
        if (input.me.isNotBlank()) sb.append("## 我\n").append(input.me.trim()).append("\n")
        if (input.her.isNotBlank()) sb.append("## 她\n").append(input.her.trim()).append("\n")
        if (input.warmth.isNotBlank()) sb.append("## 我们\n").append(input.warmth.trim()).append("\n")
        if (input.style.isNotBlank()) sb.append("## 我的表达偏好\n").append(input.style.trim()).append("\n")
        sb.append("\n")

        // # 【记忆】经验教训（最近3块）
        if (input.lessons.isNotBlank()) {
            sb.append("# 【记忆】经验教训（仅供参考）\n")
            sb.append(PromptBudget.lastH1Blocks(input.lessons, 3)).append("\n\n")
        }

        // # 【进行中事项】
        if (input.ongoingPlan.isNotBlank()) {
            sb.append("# 【进行中事项】（长期追踪，仅在与当前对话相关时提及，不必每条都提）\n")
            sb.append(input.ongoingPlan).append("\n")
        }

        return sb.toString()
    }
}
