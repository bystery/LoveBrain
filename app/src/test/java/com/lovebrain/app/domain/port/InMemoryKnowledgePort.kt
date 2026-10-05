package com.lovebrain.app.domain.port

import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.CorrectionAction

/**
 * 内存版知识端口——**只为合同测试与 UI 测试存在**，不在生产图里注册。
 *
 * 它必须与生产适配器遵守同一份 [KnowledgePortContract]（含"schema 过新即只读"和
 * "越界路径读不到"这两条安全语义）。fake 与生产走不同语义，是复核 第4节 的 LSP 那行
 * 判 FAIL 的确切含义：测试全绿但生产不是那台机器。
 */
class InMemoryKnowledgePort : KnowledgePort {

    private val files = LinkedHashMap<String, String>()          // "kb/rel/path" -> content
    private val meta = LinkedHashMap<String, KnowledgeBase>()    // kb -> 元数据
    /** 这些库被标成"来自未来"，写一律拒绝（与 KnowledgeRepository 的 schema 门禁同语义） */
    val readOnlyLibraries = mutableSetOf<String>()

    private fun key(kb: String, path: String) = "$kb/$path"
    private fun safe(kb: String, path: String): Boolean =
        !path.contains("..") && !path.startsWith("/") && !path.startsWith("\\")

    fun seed(kb: String, path: String, content: String) {
        files[key(kb, path)] = content
    }

    fun seedLibrary(kb: KnowledgeBase) {
        meta[kb.name] = kb
    }

    fun dump(): Map<String, String> = LinkedHashMap(files)

    override suspend fun readFile(kbName: String, relativePath: String): String =
        if (safe(kbName, relativePath)) files[key(kbName, relativePath)].orEmpty() else ""

    override suspend fun getActive(): KnowledgeBase? = meta.values.firstOrNull { it.active }

    override suspend fun getCurrentStage(kbName: String): String = meta[kbName]?.stage.orEmpty()

    override suspend fun getCurrentTopic(kbName: String): String =
        files[key(kbName, "moment/topic.md")].orEmpty().substringAfterLast(' ').ifBlank { "" }

    override suspend fun getTurnCount(kbName: String): Int = meta[kbName]?.turnCount ?: 0

    override suspend fun getTopicAgeHours(kbName: String): Int = 0

    override suspend fun readPlanActive(kbName: String): String =
        files[key(kbName, "moment/plan.md")].orEmpty()

    override suspend fun readVector(kbName: String): Map<String, Int> =
        listOf("intimacy", "trust", "commitment", "passion", "security")
            .associateWith { files[key(kbName, "understand/warmth.md")].orEmpty().length }

    override suspend fun readCounselingAnalysisBlocks(kbName: String, count: Int): String =
        files[key(kbName, "memory/counseling_log.md")].orEmpty()

    override suspend fun getCorrectionsRevision(kbName: String): Int =
        files[key(kbName, "memory/.revision")]?.toIntOrNull() ?: 0

    override suspend fun getLessonCount(kbName: String): Int =
        files[key(kbName, "memory/raw_topic.md")].orEmpty()
            .let { Regex("^## \\[", RegexOption.MULTILINE).findAll(it).count() }

    private fun refused(kbName: String): Boolean =
        kbName in readOnlyLibraries || meta[kbName] == null || !safe(kbName, "")

    override suspend fun writeFile(kbName: String, relativePath: String, content: String) {
        if (refused(kbName) || !safe(kbName, relativePath)) return
        files[key(kbName, relativePath)] = content
    }

    override suspend fun appendFile(kbName: String, relativePath: String, content: String) {
        if (refused(kbName) || !safe(kbName, relativePath)) return
        files[key(kbName, relativePath)] = files[key(kbName, relativePath)].orEmpty() + content
    }

    override suspend fun deleteFile(kbName: String, relativePath: String): Boolean {
        if (refused(kbName) || !safe(kbName, relativePath)) return false
        return files.remove(key(kbName, relativePath)) != null
    }

    override suspend fun setCurrentTopic(kbName: String, topicLabel: String) {
        writeFile(kbName, "moment/topic.md", "- [2026-09-24 09:00] 正在聊：$topicLabel")
    }

    override suspend fun rotateTopic(kbName: String) {
        if (refused(kbName)) return
        val topic = files[key(kbName, "moment/topic.md")].orEmpty()
        if (topic.isBlank()) return
        val archive = files[key(kbName, "memory/raw_topic.md")].orEmpty()
        files[key(kbName, "memory/raw_topic.md")] = archive + "\n# [2026-09-24]\n" + topic + "\n"
        files[key(kbName, "moment/topic.md")] = ""
    }

    override suspend fun incrementTurnCountBy(kbName: String, delta: Int) {
        if (delta <= 0 || refused(kbName)) return
        val kb = meta[kbName] ?: return
        meta[kbName] = kb.copy(turnCount = kb.turnCount + delta)
    }

    override suspend fun appendFileWithRevisionCheck(
        kbName: String, relativePath: String, content: String, expectedRevision: Int
    ): Boolean {
        if (getCorrectionsRevision(kbName) != expectedRevision) return false
        appendFile(kbName, relativePath, content)
        return true
    }

    override suspend fun writeVectorWithRevisionCheck(
        kbName: String, values: Map<String, Int>, expectedRevision: Int
    ): Boolean {
        if (getCorrectionsRevision(kbName) != expectedRevision) return false
        val body = values.entries.joinToString("\n") { "${it.key}：${it.value}" }
        writeFile(kbName, "understand/warmth.md", body)
        return true
    }

    /**
     * 「读—整理—条件替换」那一次事务的内存版，与生产适配器**同一份合同**
     * （[KnowledgePortContract] 里那三格把它钉在两个实现上：只读保护、快照先行、逐字相同就不写）。
     *
     * 三条边界的落点与 [com.lovebrain.app.data.KnowledgeLessonsRewriteService] 逐条对齐：
     * 这里没有并发（单线程 runBlocking 的测试夹具），所以"同一段临界区"退化成"一次读完再写、中间不重读"；
     * 快照与"只在真变了才写"这两条是**行为**而不是锁，fake 一样得守住，否则合同测试就是假绿。
     *
     * 快照的名字不由本 fake 决定：命名规则只有一个主人
     * （[com.lovebrain.app.data.KnowledgeLessonsRewriteService.snapshotPathOf]，internal 在同模块测试里可见），
     * 抄一份就是第二个所有者，那份规则改了这里不会红。
     */
    override suspend fun readTidyAndReplaceWithRevisionCheck(
        kbName: String,
        relativePath: String,
        expectedRevision: Int,
        compose: (existing: String) -> String?
    ): LessonsRewriteResult {
        if (meta[kbName] == null) return LessonsRewriteResult.MissingLibrary("库目录或 kb.json 不在了")
        if (!safe(kbName, relativePath)) return LessonsRewriteResult.MissingLibrary("库名或相对路径被守门挡下")
        if (kbName in readOnlyLibraries) return LessonsRewriteResult.RefusedNewerSchema
        if (getCorrectionsRevision(kbName) != expectedRevision) return LessonsRewriteResult.RevisionChanged

        val existing = files[key(kbName, relativePath)].orEmpty()
        val composed = compose(existing) ?: return LessonsRewriteResult.NothingToWrite
        if (composed == existing) return LessonsRewriteResult.NothingToWrite

        // 写前先有快照（空文件没有原件可保，与生产同一条口径）
        val snapshotPath = if (existing.isEmpty()) null
        else com.lovebrain.app.data.KnowledgeLessonsRewriteService.snapshotPathOf(relativePath)
        snapshotPath?.let { files[key(kbName, it)] = existing }
        files[key(kbName, relativePath)] = composed
        return LessonsRewriteResult.Rewritten(snapshotPath)
    }

    /** 测试夹具：让纠正 revision 可比 */
    fun seedRevision(kbName: String, revision: Int) {
        files[key(kbName, "memory/.revision")] = revision.toString()
    }

    fun seedCorrectionStore(kbName: String, vararg entries: MemoryCorrection) {
        files[key(kbName, "memory/corrections.json")] = entries.joinToString(",", "[", "]") {
            """{"memoryId":"${it.memoryId}","action":"${it.action}","revision":${it.revision}}"""
        }
    }

    @Suppress("unused")
    private fun actionName(action: CorrectionAction) = action.name
}
