package com.lovebrain.app.domain.port

import com.lovebrain.app.model.KnowledgeBase

/**
 * 知识库读端口（复核 第4节 DIP 行、第5节「domain 只依赖 AiGateway / KnowledgeReadPort /
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
 * [KnowledgeWritePort.readTidyAndReplaceWithRevisionCheck] 这一次事务的结果。
 *
 * 分成这几个分支只为一件事：**「谁在撒谎」必须能从类型里读出来**。
 * 旧写法返回 `Boolean`，于是"整理过一遍但一个字节都没落"和"落盘成功"在调用方手里是同一个 true，
 *  第8节第3条 那句"写入失败不报告成功"就没有可钉的形状。
 * 每个分支各自对应"字节到底落没落"，且**除 [Rewritten] 之外每一支都保证原件一个字节都没动**。
 */
sealed interface LessonsRewriteResult {

    /**
     * 整理结果已落盘。
     *
     * @param snapshotPath 写前原件快照落在库内的相对路径；null = 原文件本来就是空的、没有原件可保
     */
    data class Rewritten(val snapshotPath: String?) : LessonsRewriteResult

    /** 没要写：[compose] 交回 null，或交回的全文与磁盘上那份**逐字相同**（可重入的第二、第三次就是这一支） */
    data object NothingToWrite : LessonsRewriteResult

    /** 纠正号已经不是冻结的那一个：整段事务在**读正文之前**就收手，一个字节都没写 */
    data object RevisionChanged : LessonsRewriteResult

    /**
     * 写前快照没落成 ⇒ **拒绝动原件**（ 第8节第2条"备份原文件"那道门）。
     *
     * 这一支存在的理由：整理是一次"整篇替换"，没有原件快照就等于拿用户的文件赌判据没写错。
     */
    data object SnapshotFailed : LessonsRewriteResult

    /**
     * 目标替换没落成：原件与快照都还在原处，[composed] 原样交回调用侧留痕——
     * **不许分配完编号再把正文咽掉**，也不许报成功。
     */
    data class WriteFailed(val composed: String) : LessonsRewriteResult

    /**
     * 这个目标写不了：库目录/`kb.json` 不在了，或库名、相对路径被 canonical 守门挡下。
     *
     * @param reason 到底是哪一种——三者行为相同（没写），但诊断时不能混成一句话
     */
    data class MissingLibrary(val reason: String) : LessonsRewriteResult

    /** 该库的 schema 比本 App 还新：整段事务一个字节都没写（与 [Rewritten] 相对，这是只读保护） */
    data object RefusedNewerSchema : LessonsRewriteResult
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

    /**
     * 一次「**锁内读正文 → 整理/发号 → 快照 → 条件整篇替换**」事务（ 第8节第2条、第8节第3条 要的那道门）。
     *
     * 存在的理由是一条真实的窗口：`appendFileWithRevisionCheck` 只把"检查 revision + 追加"锁在一起，
     * 而**编号是从正文里数出来的**，数号那一步在 `readFile` 里、在这把锁**之外**
     * （`KnowledgeTriggerCoordinator.lessonWriteMutex` 只是进程内补丁，它管不住"读"与"写"分属两次加锁）。
     * 于是并发追加可以把刚数出来的号发第二遍、把刚整理好的那一篇覆盖掉。
     * 这一颗口把四步收进同一个 `fileMutex` 临界区：计数、发号、revision 检查、替换全在同一段里。
     *
     * @param compose 拿到**锁内读到的**正文，交回要整体替换回去的全文；
     *                返回 null 或返回与原文逐字相同 ⇒ 什么都不写。
     *                整理判据（`LessonDoc.tidy`）住在 domain，这一侧不落盘也不认得它——
     *                写事务与判据各自的主人不变，这里只是把判据请进锁里跑一次。
     *
     * ⚠ 三条安全边界由实现（`KnowledgeRepository.readTidyAndReplaceWithRevisionCheck`）保证，
     * 逐条钉在 `KnowledgePortContract` 与 `KnowledgeRepositoryLessonsRewriteTest`：
     * ① 读、发号、revision 检查、快照、替换全在同一把 `fileMutex` 里；
     * ② 替换之前先有原件快照，快照没落成就**不动原件**；替换没落成就把全文交回调用侧、不报成功；
     * ③ 只有内容真的变了才写盘（可重入：第二次进来一个字节都不写）。
     * 落盘仍走仓库唯一那条原子写链，本端口**不是**第二个写入口。
     */
    suspend fun readTidyAndReplaceWithRevisionCheck(
        kbName: String,
        relativePath: String,
        expectedRevision: Int,
        compose: (existing: String) -> String?
    ): LessonsRewriteResult
}

/**
 * 读写一起要用的调用方（WAL、TopicRecorder）拿这个。
 *
 * 分成两个接口不是为了好看：PromptBuilder 只读，就不该在它的构造参数里
 * 出现 deleteFile —— 复核 第4节 的 ISP 验收标准是"Home 无法调用知识库内部写"。
 */
interface KnowledgePort : KnowledgeReadPort, KnowledgeWritePort
