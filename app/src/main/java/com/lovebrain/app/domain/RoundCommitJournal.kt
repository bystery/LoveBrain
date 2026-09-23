package com.lovebrain.app.domain

import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S2-04: Round Commit Journal——WAL 机制实现可恢复事务。
 *
 * 替代 TopicRecorder 中的顺序写入（read marker → rotate topic → write recent → write scene → write plan → increment count）。
 *
 * WAL 流程：
 * 1. PREPARED：将完整 RoundCommitEvent（含 before/after/revision）原子写入 journal 文件
 * 2. 写 targets：按顺序写入 recent.md / topic / scene.md / plan.md / turn count
 * 3. COMMITTED：在 journal 中标记 committed
 *
 * 崩溃恢复时：
 * - 如果 journal 中有 PREPARED 但无 COMMITTED → roll-forward（重放未完成的写入，幂等）
 * - 如果 journal 中有 COMMITTED → 已完成，清理 journal
 * - 如果无 journal → 无事务在途，正常启动
 *
 * 禁止继续把 recent.md 内的 HTML marker 当跨文件 commit marker。
 */
class RoundCommitJournal(
    private val knowledgeRepo: KnowledgeRepository
) {
    /** WAL journal 文件路径 */
    private val journalPath = "moment/.round_commit_journal.json"

    /** 单次轮次提交的完整事件 */
    data class RoundCommitEvent(
        val roundId: String,
        val kbName: String,
        val inputRevision: Int,
        val timestamp: String,
        val topicStatus: String,
        val topicLabel: String,
        val sceneFacts: List<String>,
        val ongoing: List<String>,
        val recentEntry: String,
        val turnCountIncrement: Int = 1,
        var stage: CommitStage = CommitStage.PREPARED
    )

    /** WAL 提交阶段 */
    enum class CommitStage {
        PREPARED,    // journal 已写入，targets 未开始
        WRITING,     // 正在写 targets
        COMMITTED    // 所有 targets 已写入，可清理 journal
    }

    /**
     * S2-04: 开始一轮提交——先写 PREPARED 到 journal。
     * 返回 roundId，后续用此 ID 完成或回滚。
     */
    suspend fun beginCommit(event: RoundCommitEvent): String = withContext(Dispatchers.IO) {
        val roundId = event.roundId
        val journalEntry = serializeEvent(event)
        knowledgeRepo.writeFile(event.kbName, journalPath, journalEntry)
        L.w("S2-04: WAL PREPARED roundId=$roundId")
        roundId
    }

    /**
     * S2-04: 标记提交完成——写 COMMITTED 到 journal，然后清理。
     */
    suspend fun markCommitted(kbName: String, roundId: String) = withContext(Dispatchers.IO) {
        val journalContent = knowledgeRepo.readFile(kbName, journalPath)
        if (journalContent.isNotBlank()) {
            val event = deserializeEvent(journalContent)
            if (event?.roundId == roundId) {
                // 标记 committed
                val committedEvent = event.copy(stage = CommitStage.COMMITTED)
                knowledgeRepo.writeFile(kbName, journalPath, serializeEvent(committedEvent))
                // 清理 journal
                knowledgeRepo.deleteFile(kbName, journalPath)
                L.w("S2-04: WAL COMMITTED and cleaned roundId=$roundId")
            }
        }
    }

    /**
     * S2-04: 崩溃恢复——检查是否有未完成的 WAL 事务。
     * 返回需要 roll-forward 的事件，或 null 如果无在途事务。
     */
    suspend fun recoverPending(kbName: String): RoundCommitEvent? = withContext(Dispatchers.IO) {
        val journalContent = knowledgeRepo.readFile(kbName, journalPath)
        if (journalContent.isBlank()) return@withContext null

        val event = deserializeEvent(journalContent) ?: run {
            // journal 损坏，清理
            knowledgeRepo.deleteFile(kbName, journalPath)
            return@withContext null
        }

        when (event.stage) {
            CommitStage.COMMITTED -> {
                // 已完成但未清理——清理 journal
                knowledgeRepo.deleteFile(kbName, journalPath)
                L.w("S2-04: WAL recovery - found COMMITTED, cleaning roundId=${event.roundId}")
                null
            }
            CommitStage.PREPARED, CommitStage.WRITING -> {
                // 需要 roll-forward
                L.w("S2-04: WAL recovery - found ${event.stage}, will roll-forward roundId=${event.roundId}")
                event
            }
        }
    }

    /**
     * S2-04: 回滚——删除 journal 文件，targets 中的写入需由调用方幂等处理。
     */
    suspend fun rollback(kbName: String, roundId: String) = withContext(Dispatchers.IO) {
        val journalContent = knowledgeRepo.readFile(kbName, journalPath)
        if (journalContent.isNotBlank()) {
            val event = deserializeEvent(journalContent)
            if (event?.roundId == roundId) {
                knowledgeRepo.deleteFile(kbName, journalPath)
                L.w("S2-04: WAL rollback roundId=$roundId")
            }
        }
    }

    /** 序列化事件到 JSON 字符串 */
    private fun serializeEvent(event: RoundCommitEvent): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"roundId\":\"").append(escape(event.roundId)).append("\",")
        sb.append("\"kbName\":\"").append(escape(event.kbName)).append("\",")
        sb.append("\"inputRevision\":").append(event.inputRevision).append(",")
        sb.append("\"timestamp\":\"").append(escape(event.timestamp)).append("\",")
        sb.append("\"topicStatus\":\"").append(escape(event.topicStatus)).append("\",")
        sb.append("\"topicLabel\":\"").append(escape(event.topicLabel)).append("\",")
        sb.append("\"stage\":\"").append(event.stage.name).append("\",")
        sb.append("\"turnCountIncrement\":").append(event.turnCountIncrement).append(",")
        sb.append("\"sceneFacts\":").append(serializeStringList(event.sceneFacts)).append(",")
        sb.append("\"ongoing\":").append(serializeStringList(event.ongoing)).append(",")
        sb.append("\"recentEntry\":\"").append(escape(event.recentEntry)).append("\"")
        sb.append("}")
        return sb.toString()
    }

    /** 反序列化 JSON 字符串到事件 */
    private fun deserializeEvent(json: String): RoundCommitEvent? {
        return try {
            val roundId = extractStringField(json, "roundId") ?: return null
            val kbName = extractStringField(json, "kbName") ?: return null
            val inputRevision = extractNumericField(json, "inputRevision")?.toIntOrNull() ?: 0
            val timestamp = extractStringField(json, "timestamp") ?: ""
            val topicStatus = extractStringField(json, "topicStatus") ?: ""
            val topicLabel = extractStringField(json, "topicLabel") ?: ""
            val stageStr = extractStringField(json, "stage") ?: "PREPARED"
            val stage = CommitStage.valueOf(stageStr)
            val turnCountIncrement = extractNumericField(json, "turnCountIncrement")?.toIntOrNull() ?: 1
            val recentEntry = extractStringField(json, "recentEntry") ?: ""
            val sceneFacts = extractStringListField(json, "sceneFacts")
            val ongoing = extractStringListField(json, "ongoing")
            RoundCommitEvent(
                roundId = roundId,
                kbName = kbName,
                inputRevision = inputRevision,
                timestamp = timestamp,
                topicStatus = topicStatus,
                topicLabel = topicLabel,
                sceneFacts = sceneFacts,
                ongoing = ongoing,
                recentEntry = recentEntry,
                turnCountIncrement = turnCountIncrement,
                stage = stage
            )
        } catch (e: Exception) {
            L.w("S2-04: journal deserialize failed: ${e.message}")
            null
        }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescape(s: String): String =
        s.replace("\\r", "\r").replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

    private fun extractStringField(json: String, field: String): String? {
        val key = "\"$field\":\""
        val start = json.indexOf(key)
        if (start < 0) return null
        var i = start + key.length
        val sb = StringBuilder()
        while (i < json.length) {
            when {
                json[i] == '\\' && i + 1 < json.length -> {
                    sb.append(json[i + 1])
                    i += 2
                }
                json[i] == '"' -> return unescape(sb.toString())
                else -> sb.append(json[i++])
            }
        }
        return null
    }

    /** 从 JSON 中提取数值字段（无引号的数字） */
    private fun extractNumericField(json: String, field: String): String? {
        val key = "\"$field\":"
        val start = json.indexOf(key)
        if (start < 0) return null
        var i = start + key.length
        while (i < json.length && json[i].isWhitespace()) i++
        val sb = StringBuilder()
        while (i < json.length && (json[i].isDigit() || json[i] == '-')) {
            sb.append(json[i++])
        }
        return sb.toString().ifBlank { null }
    }

    private fun serializeStringList(list: List<String>): String {
        return list.joinToString(prefix = "[", postfix = "]") { "\"${escape(it)}\"" }
    }

    /** 从 JSON 中提取字符串数组字段 */
    private fun extractStringListField(json: String, field: String): List<String> {
        val key = "\"$field\":"
        val start = json.indexOf(key)
        if (start < 0) return emptyList()
        var i = start + key.length
        // 跳过空格
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '[') return emptyList()
        i++ // 跳过 '['
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        while (i < json.length) {
            when {
                json[i] == ']' -> return result
                json[i] == '"' -> {
                    sb.clear()
                    i++
                    while (i < json.length && json[i] != '"') {
                        if (json[i] == '\\' && i + 1 < json.length) {
                            sb.append(json[i + 1])
                            i += 2
                        } else {
                            sb.append(json[i++])
                        }
                    }
                    result.add(unescape(sb.toString()))
                    i++ // 跳过闭合引号
                }
                else -> i++
            }
        }
        return result
    }
}
