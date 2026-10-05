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
 *
 * ⚠ **第10节第4条「仅看本轮」的主动发那一档走 [buildRoundScope]，不走 [build]**：开关开着时
 * [Input] 里的 `herProfile` / `recent` 本来就是从知识库读出来的旧记忆，一个都不许进请求。
 * 判据不是"这里跳过一下"，而是调用方**整条 KB 读取都不做**（见 `PromptBuilder.buildProactiveUserPrompt`）——
 * 所以 [RoundScopeInput] 的字段里没有画像/历史的位置，只有本轮草稿、本轮备注、
 * 本轮推断出的场景与本轮真实对话。这一档排除的是：画像、阶段、历史聊天/近期对话、经验、
 * 旧事项、表达偏好、持续意图；留下的是通用规则（system）、输出格式、本轮场景与当前时间。
 */
object PromptProactiveSection {

    /** 「本轮仅看模式」这一档写进请求头部的说明句——它是给模型看的边界声明，不是界面文案 */
    const val ROUND_SCOPE_HEADER =
        "（本轮仅看模式：只用当前对话与本轮草稿/备注，" +
            "不带画像、阶段、历史聊天、经验、旧事项、表达偏好与持续意图）\n\n"

    /** 调用方已读出的主动开场上下文输入；herProfile/recent 空串表示对应文件不存在/不注入 */
    data class Input(
        val draft: String,
        val herProfile: String,
        val recent: String
    )

    /**
     * 「仅看本轮」那一档的输入：四件全部来自**本轮**，没有一件来自知识库。
     *
     * @param draft 用户当前的主动发草稿（可空）
     * @param noteBlock 本轮军师备注区块（由 `IntentIdeaBlock.buildAdvisorNoteBlock` 装配好）
     * @param sceneBlock 由本轮对话推断出的场景段（`CurrentSceneInjection`）；空串=没有真实消息
     * @param dialogueBlock 本轮真实对话围栏（`ChatTranscriptBlock` 的 header + body）
     */
    data class RoundScopeInput(
        val draft: String,
        val noteBlock: String,
        val sceneBlock: String,
        val dialogueBlock: String
    )

    /** 装配主动开场段（含草稿 + 画像 + 近期对话；不含时间戳，时间戳由调用方追加） */
    fun build(input: Input): String {
        val sb = StringBuilder()
        sb.append(draftBlock(input.draft))

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

    /**
     * 装配「仅看本轮」那一档的主动开场段：本轮草稿 + 本轮备注 + 本轮场景 + 本轮真实对话。
     *
     * 与 [build] 的关系是**同一颗草稿规则的两种上下文**，不是两份草稿写法
     * （[draftBlock] 只此一处）；这一段不读画像、不读近期对话、不读经验/事项/偏好/意图，
     * 因为调用方在这一档压根不去读知识库。
     */
    fun buildRoundScope(input: RoundScopeInput): String = buildString {
        append(ROUND_SCOPE_HEADER)
        append(draftBlock(input.draft))
        append(input.noteBlock)
        append(input.sceneBlock)
        append(input.dialogueBlock)
        append("\n\n")
    }

    /** 用户草稿块——两档共用这一颗：空草稿给固定文案，非空逐字进围栏外的一节 */
    private fun draftBlock(draft: String): String {
        val trimmedDraft = draft.trim()
        return if (trimmedDraft.isNotBlank()) {
            "## 用户草稿\n$trimmedDraft\n\n"
        } else {
            "## 用户草稿\n（无草稿，请主动给出开场话题）\n\n"
        }
    }
}
