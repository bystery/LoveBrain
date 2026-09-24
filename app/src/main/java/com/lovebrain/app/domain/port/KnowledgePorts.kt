package com.lovebrain.app.domain.port

import com.lovebrain.app.model.KnowledgeBase

/**
 * 知识库读端口（复核 §4 DIP 行、§5「domain 只依赖 AiGateway / KnowledgeReadPort /
 * KnowledgeWritePort / Clock」）。
 *
 * 成员不是"把 KnowledgeRepository 的 public API 抄一遍"，而是**domain 这六个月真正调用过的**
 * 那些：RoundCommitJournal、TopicRecorder、OngoingContextSelector、PromptBuilder、
 * KnowledgeTriggerCoordinator 各自用到的读操作（脚本实测枚举，不是凭印象）。
 * 少一层"接口=实现影子"，接口隔离才有意义：
 * 读端口里没有 deleteFile，所以只读的调用方拿不到写能力。
 */
interface KnowledgeReadPort {
    suspend fun readFile(kbName: String, relativePath: String): String
    suspend fun getActive(): KnowledgeBase?
    suspend fun getCurrentStage(kbName: String): String
    suspend fun getCurrentTopic(kbName: String): String
    suspend fun getTurnCount(kbName: String): Int
    suspend fun getTopicAgeHours(kbName: String): Int
    suspend fun readPlanActive(kbName: String): String
    suspend fun readVector(kbName: String): Map<String, Int>
    suspend fun readCounselingAnalysisBlocks(kbName: String, count: Int): String
    suspend fun getCorrectionsRevision(kbName: String): Int
    suspend fun getLessonCount(kbName: String): Int
}

/**
 * 知识库写端口。
 *
 * 注意：**这里没有** "绕过 schema 只读判定" 的口子。落盘边界仍然只有一个
 * （KnowledgeRepository 里的 atomicWriteText / transaction），端口只是把"能写哪些东西"
 * 收窄到 domain 真正需要的操作，不是第二套写路径。
 */
interface KnowledgeWritePort {
    suspend fun writeFile(kbName: String, relativePath: String, content: String)
    suspend fun appendFile(kbName: String, relativePath: String, content: String)
    suspend fun deleteFile(kbName: String, relativePath: String): Boolean
    suspend fun setCurrentTopic(kbName: String, topicLabel: String)
    suspend fun rotateTopic(kbName: String)
    suspend fun incrementTurnCountBy(kbName: String, delta: Int)
    suspend fun appendFileWithRevisionCheck(
        kbName: String, relativePath: String, content: String, expectedRevision: Int
    ): Boolean

    suspend fun writeVectorWithRevisionCheck(
        kbName: String, values: Map<String, Int>, expectedRevision: Int
    ): Boolean
}

/**
 * 读写一起要用的调用方（WAL、TopicRecorder）拿这个。
 *
 * 分成两个接口不是为了好看：PromptBuilder 只读，就不该在它的构造参数里
 * 出现 deleteFile —— 复核 §4 的 ISP 验收标准是"Home 无法调用知识库内部写"。
 */
interface KnowledgePort : KnowledgeReadPort, KnowledgeWritePort
