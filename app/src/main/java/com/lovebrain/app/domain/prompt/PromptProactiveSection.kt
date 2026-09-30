package com.lovebrain.app.domain.prompt

/**
 * 主动开场 user 侧段装配（自 `domain/PromptBuilder.kt` 的 `buildProactiveUserPrompt` 行为块搬出）。
 *
 * 这一块把**已经读出来的**对方画像 / 近期对话文本与用户草稿拼成主动开场 user 侧段。
 * 它不读知识库、不读资产、不读时钟，也不持有状态——所有读取由 `PromptBuilder` 在调用前
 * 完成并以 [Input] 传入，因此是纯函数。搬运前后对同一批输入必须给出逐字相同的字符串
 * （字节证据见 `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 草稿空时固定文案「（无草稿，请主动给出开场话题）」；非空时按 `## 用户草稿\n{draft}\n\n` 注入。
 * - 对方画像 `take(500)`，超长追加「…（略）」。
 * - 近期对话只取非空行的最后 20 行。
 * - 时间戳由调用方拼到尾部（本段不含时间戳生成）。
 */
object PromptProactiveSection {

    /** 调用方已读出的主动开场上下文输入；herProfile/recent 空串表示对应文件不存在/不注入 */
    data class Input(
        val draft: String,
        val herProfile: String,
        val recent: String
    )

    /** 装配主动开场段（含草稿 + 画像 + 近期对话；不含时间戳，时间戳由调用方追加） */
    fun build(input: Input): String {
        val sb = StringBuilder()

        val trimmedDraft = input.draft.trim()
        if (trimmedDraft.isNotBlank()) {
            sb.append("## 用户草稿\n").append(trimmedDraft).append("\n\n")
        } else {
            sb.append("## 用户草稿\n（无草稿，请主动给出开场话题）\n\n")
        }

        if (input.herProfile.isNotBlank()) {
            sb.append("## 对方画像\n").append(input.herProfile.take(500))
            if (input.herProfile.length > 500) sb.append("…（略）")
            sb.append("\n\n")
        }

        if (input.recent.isNotBlank()) {
            val lines = input.recent.lines().filter { it.isNotBlank() }
            val recentLines = lines.takeLast(20)
            sb.append("## 近期对话\n").append(recentLines.joinToString("\n")).append("\n\n")
        }

        return sb.toString()
    }
}
