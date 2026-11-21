package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.isIntentEffective

/**
 * 持续意图与军师备注区块（自 `domain/PromptBuilder.kt` 的「持续意图区块」「想法区块」行为块搬出）。
 *
 * 这一块是 prompt 的**表示层**：给定一份意图配置 / 一段本轮备注文本，写出 prompt 里那两段固定格式的
 * 字符串。它不读知识库、不读资产、不读时钟，也不持有任何状态，因此是纯函数。
 *
 * 语义约束：
 * - 持续意图区块的判据只有一条：**开着（enabled）且正文非空**——见 [isIntentEffective]。
 *   期限、"已完成"、"已暂停"、"已到期"都不再参与判定；老文件带着这些字段也照样按开关说话，
 *   于是"开着却被一条看不见的期限挡在门外"这一类形状从源头消失。
 * - 关着的意图正文仍留在盘上，只是这一段为空。
 * - 用户备注区块（旧称"军师备注"）仅当正文非空时出现，正文**逐字全量**注入
 *   （展示端省略过，发给模型不许裁）。主回复用 [USER_NOTE_HEADER]，影子链路沿用默认标题。
 *
 * ⚠  改名与改语义：这一段以前写着"用户想这样回……请基于这个方向润色出4种方案"，
 * 那是把**背景/限制/提醒**读成了**待发送话术**，还顺手把输出数量钉死成 4 种。
 * 现在措辞只说明"这是用户给军师的补充"，方案数量与格式仍由回复任务原有格式控制。
 * （这段措辞一旦定稿就别随手改——措辞一动，prompt 字节就变，任何按字节钉读数的格子都会红。）
 */
object IntentIdeaBlock {

    /** 持续意图区块：开着且正文非空就注入，其余情况一段都不给。 */
    fun buildIntentBlock(intentConfig: IntentConfig): String {
        if (!intentConfig.isIntentEffective) return ""
        return "【持续意图】\n${intentConfig.text.trim()}\n\n"
    }

    /** 军师备注区块的标题行（UI 不参与拼装，这一颗是 prompt 侧唯一的措辞真源） */
    const val NOTE_BLOCK_HEADER = "# 军师备注（用户补充，不是要发出去的话术）"

    /**
     * 2026-11-21 结构收敛：主回复 user prompt 的 ⑤ 改称「用户备注」。
     * 正文来源不变（仍是 `GenerationInput.replyDirective.text`），只是标题不再叫"军师备注"。
     * 默认标题仍是 [NOTE_BLOCK_HEADER]——影子链路（`ChampionPromptAssembler`）本次不动。
     */
    const val USER_NOTE_HEADER = "# 用户备注"

    /** 备注正文上限：备注是约束，不是第二段对话；超长正文在这里按行/字符边界收 */
    const val NOTE_BODY_MAX = 3000

    /** 正文被收短时追加的可读交代（只出现在真的超限时） */
    private const val NOTE_TRUNCATED_MARKER = "\n…（备注过长，后面已省略）"

    /**
     * 本轮用户备注区块（来源 `GenerationInput.replyDirective.text`）。
     *
     * 正文**逐字全量**注入是默认行为，但两个边界必须在这里兜住：
     * - 长度：超过 [NOTE_BODY_MAX] 时按行/字符边界收，留可读结尾、不切断代理对；
     *   否则备注单独就能把主回复 prompt 顶出 9000（预算层另有硬帽兜底）。
     * - 结构：备注是用户自由输入，一段以 `# ` 开头的行会在 prompt 里伪造出新的段头，
     *   所以行首标题符与保留 token 用全角字符打断；正常正文一个字节都不改。
     *
     * @param note 备注正文
     * @param header 标题行；主回复传 [USER_NOTE_HEADER]，影子链路沿用默认 [NOTE_BLOCK_HEADER]
     * @return 空正文给空串——没有备注就没有这一段，prompt 里不留空标题
     */
    fun buildAdvisorNoteBlock(note: String, header: String = NOTE_BLOCK_HEADER): String {
        val text = fenceReservedTokens(capNoteBody(note.trim()))
        if (text.isEmpty()) return ""
        return buildString {
            append(header).append("\n")
            append("「").append(text).append("」\n")
            append("这是用户给你的背景、限制、目的或提醒，只用来约束你这一轮怎么想、怎么回；")
            append("它不是要原样发给对方的消息，也不要当成用户已经说过的话来复述。\n")
            append("方案数量与输出格式按本任务既有的格式要求，不因为这段补充而改变。\n\n")
        }
    }

    /** 超长备注按行边界收；头一行就装不下时退到字符边界（不切代理对） */
    private fun capNoteBody(text: String): String {
        if (text.length <= NOTE_BODY_MAX) return text
        val room = (NOTE_BODY_MAX - NOTE_TRUNCATED_MARKER.length).coerceAtLeast(0)
        val byLines = StringBuilder()
        for (line in text.split("\n")) {
            if (byLines.length + line.length + 1 > room) break
            if (byLines.isNotEmpty()) byLines.append("\n")
            byLines.append(line)
        }
        // 行边界只是"更好读"：若几十行的头一行就超，行收法会把整份备注吞成一行，
        // 这时按字符边界保前缀，至少让用户看到自己写的那段话。
        val kept = if (byLines.length >= room / 2) byLines.toString() else takeSafe(text, room)
        return kept.trimEnd() + NOTE_TRUNCATED_MARKER
    }

    /**
     * 打断备注里能伪造 prompt 结构的保留 token：行首/任意位置的 `# 【`、单行来源行
     * `m_src[`、对话围栏 `<chat>` / `</chat>`。用全角字符替代，可读且不再命中段头判据。
     */
    private fun fenceReservedTokens(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        text.split("\n").forEachIndexed { i, line ->
            if (i > 0) out.append("\n")
            var l = line
            if (l.startsWith("#")) l = "＃" + l.substring(1)
            if (l.contains("# 【")) l = l.replace("# 【", "＃ 【")
            if (l.contains("</chat>") || l.contains("<chat>")) {
                l = l.replace("</chat>", "＜/chat＞").replace("<chat>", "＜chat＞")
            }
            if (l.contains("m_src[")) l = l.replace("m_src[", "m_src［")
            out.append(l)
        }
        return out.toString()
    }

    /** 按字符边界取前缀，不把代理对切成两半 */
    private fun takeSafe(text: String, max: Int): String {
        if (text.length <= max) return text
        var end = max.coerceAtLeast(0)
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end)
    }
}
