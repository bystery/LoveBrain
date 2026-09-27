package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.util.L

/**
 * `moment/recent.md` 的所有者——"一轮对话在这份文件里长什么样、留几轮"。
 *
 * 变化理由：轮次记录的正文（块头、来源 marker、想法行、最终回复行、点赞块）
 * 加上"最近 N 轮、溢出转对话暂存"这条窗口规则。这些都跟 WAL 事务无关，
 * 也跟 scene/plan 的落盘格式无关，所以它们不该和那些规则住在一起。
 *
 * 从 [TopicRecorder] 拆出，判据与文案逐字保留：
 * - 块头 `- [yyyy-MM-dd HH:mm]` 用的时间串由调用方传入（TopicRecorder 从 Clock 端口取），
 *   本类不自己读钟——落盘正文里那个时间必须就是注入的那个（ClockWiringTest 钉着）。
 * - [buildRoundEntry] 是纯函数，必须在 WAL PREPARED 之前跑完：它的返回值还要参与
 *   稳定 roundId 的派生（[RoundCommitJournal.stableRoundId] 的 fallbackContent）。
 * - marker 只作本文件内的去重，不承担跨文件提交标记（跨文件幂等归 [RoundCommitJournal]）。
 */
class RecentRoundStore(
    private val knowledgeRepo: KnowledgePort
) {

    private val maxTopicTurns = AppConfig.MAX_TOPIC_TURNS

    /** 轮次块首行校验："- [yyyy-MM-dd HH:mm]" 真实时间戳（排除模板示例 "- [yyyy-MM-dd HH:mm]"） */
    private val roundTsRegex = Regex("^- \\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]")

    /**
     * 构建一轮的 recent.md 记录（纯函数）。
     *
     * 候选回复不再自动当作实际发送消息——scheme=null 表示本轮没有确认发送任何候选，
     * likedSchemes 记录用户偏好（点赞），但不写入"实际对话"段。
     * IDEA（想法）不写入 recent.md——IDEA 是本轮控制信息，不是真实聊天。
     */
    internal fun buildRoundEntry(
        time: String,
        roundMsgIds: String,
        conversationalMessages: List<ChatMessage>,
        userHint: String,
        scheme: Scheme?,
        likedSchemes: List<Scheme>
    ): String = buildString {
        append("- [").append(time).append("]\n")
        append(RoundCommitJournal.WRITER_MARKER_PREFIX).append(roundMsgIds).append(" -->\n")
        conversationalMessages.forEach { msg ->
            append(msg.role.label).append("：").append(msg.content).append("\n")
        }
        if (userHint.isNotBlank()) {
            append("我的想法：").append(userHint.trim()).append("\n")
        }
        if (scheme != null) {
            val schemeLabel = if (scheme.tag.contains("+")) {
                scheme.title
            } else {
                "方案${scheme.tag}-${scheme.title}"
            }
            append("我（最终回复：").append(schemeLabel).append("）：").append(scheme.reply).append("\n")
        }
        if (likedSchemes.isNotEmpty() && scheme == null) {
            // 点赞学习资料必须保存完整候选正文+风格标识，
            // 不能只存 tag 标签——否则下一轮无法知道用户喜欢了什么表达。
            append("（用户偏好，未确认发送：\n")
            likedSchemes.forEach { s ->
                val label = if (s.tag.contains("+")) s.title else "方案${s.tag}-${s.title}"
                append("  $label：${s.reply}\n")
            }
            append("）\n")
        }
    }

    /** 职责2（自 record 拆出）：写入 moment/recent.md，保留最近 N 轮，溢出→对话暂存
     * 未识别内容（不符合时间戳格式的块）原样保留，不当垃圾丢弃。
     * marker 只作本文件内的去重，不承担跨文件提交标记。 */
    internal suspend fun writeRecent(kbName: String, entry: String, roundMsgIds: String = "") {
        val recentPath = "moment/recent.md"
        if (roundMsgIds.isNotBlank()) {
            val marker = "${RoundCommitJournal.WRITER_MARKER_PREFIX}$roundMsgIds -->"
            if (knowledgeRepo.readFile(kbName, recentPath).contains(marker)) {
                L.w("recent.md already holds this round locally, skipping append")
                return
            }
        }
        val existing = knowledgeRepo.readFile(kbName, recentPath)
        if (existing.isNotBlank()) {
            val allParts = existing.split(Regex("(?=^- \\[)", RegexOption.MULTILINE)).map { it.trim() }
            val validBlocks = allParts.filter { validRoundBlock(it) }
            // 未识别内容原样保留——拼回文件头部，不被当作轮次计数或溢出处理
            val unrecognized = allParts.filter { !validRoundBlock(it) && it.isNotBlank() }

            val kept = validBlocks.takeLast(maxTopicTurns - 1)
            val overflow = validBlocks.dropLast(maxTopicTurns - 1)
            if (overflow.isNotEmpty()) {
                knowledgeRepo.appendFile(kbName, "memory/raw_chat.md", "\n" + overflow.joinToString("\n\n") + "\n")
            }
            val newRecent = buildString {
                // 未识别内容拼回头部
                if (unrecognized.isNotEmpty()) {
                    unrecognized.forEach { append(it).append("\n\n") }
                }
                kept.forEach { append(it).append("\n\n") }
                append(entry).append("\n")
            }
            knowledgeRepo.writeFile(kbName, recentPath, newRecent)
        } else {
            knowledgeRepo.writeFile(kbName, recentPath, entry + "\n")
        }
    }

    /** 轮次块校验：首行必须是真实时间戳行（防模板示例行被当作轮次流入暂存/档案） */
    private fun validRoundBlock(block: String): Boolean {
        val firstLine = block.lineSequence().firstOrNull()?.trim() ?: return false
        return roundTsRegex.matches(firstLine)
    }
}
