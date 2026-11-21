package com.lovebrain.app.domain.prompt

/**
 * 主动开场 user 侧段装配（2026-11-21 结构收敛后）。
 *
 * 普通与「仅看本轮」两档现在**只保留用户草稿**：
 * - 军师备注 / 当前场景 / 本轮真实对话 / 持续意图 / 对方画像 / 近期对话 / 表达偏好
 *   已按用户拍板全部删除；
 * - 调用方（`PromptBuilder.buildProactiveUserPrompt`）在这两档都不再读取知识库，
 *   也不从本轮消息推断场景——请求正文只剩草稿 + 当前时间（时间由调用方追加）。
 *
 * 它不读知识库、不读资产、不读时钟，也不持有状态，因此是纯函数。
 * 草稿空时给固定文案「（无草稿，请主动给出开场话题）」。
 */
object PromptProactiveSection {

    /** 装配主动开场段（仅草稿；时间戳由调用方拼到尾部） */
    fun build(draft: String): String = draftBlock(draft)

    /** 用户草稿块：空草稿给固定文案，非空逐字进一节 */
    private fun draftBlock(draft: String): String {
        val trimmedDraft = draft.trim()
        return if (trimmedDraft.isNotBlank()) {
            "## 用户草稿\n$trimmedDraft\n\n"
        } else {
            "## 用户草稿\n（无草稿，请主动给出开场话题）\n\n"
        }
    }
}
