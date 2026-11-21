package com.lovebrain.app.domain.prompt

/**
 * 谈心的核心知识子集装配（2026-11-21 结构收敛后）。
 *
 * 目标子集只保留：
 * - 画像「我 / 她 / 我们」（warmth 保留）
 * - 其余（我的表达偏好、最近 3 块经验、进行中事项）已按用户拍板删除，
 *   本段不再渲染，`PromptBuilder` 也不再读取对应文件。
 *
 * 它不读知识库、不读资产、不读时钟、不调 selector，也不持有状态——所有读取由
 * `PromptBuilder` 在调用前完成并以 [Input] 传入，因此是纯函数。
 *
 * 语义约束：
 * - 画像段与回复知识段逐字同源：me/her/warmth 非空才拼，标题固定。
 * - 无阶段节选、无此刻、无最近对话、无经验、无进行中事项。
 */
object PromptCoreKnowledgeSection {

    /** 调用方已读出的核心知识子集输入；空串表示对应文件不存在/不注入 */
    data class Input(
        val me: String,
        val her: String,
        val warmth: String
    )

    /** 装配核心知识子集；me/her/warmth 全空时整段为空（不留空段头） */
    fun build(input: Input): String {
        val body = StringBuilder()
        if (input.me.isNotBlank()) body.append("## 我\n").append(input.me.trim()).append("\n")
        if (input.her.isNotBlank()) body.append("## 她\n").append(input.her.trim()).append("\n")
        if (input.warmth.isNotBlank()) body.append("## 我们\n").append(input.warmth.trim()).append("\n")
        if (body.isEmpty()) return ""
        return "# 【懂得】关系画像\n" + body + "\n"
    }
}
