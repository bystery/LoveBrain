package com.lovebrain.app.data

import com.lovebrain.app.domain.prompt.CorrectionExtractionFilter
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration

/** 一次写事务里的落盘动作。整批共享同一次 schema 只读判定。 */
fun interface MemoryTx {
    /** false = 被只读保护或路径非法挡下，一个字节都没落 */
    fun write(relativePath: String, content: String): Boolean
}

/**
 * 仓库交给记忆格用的能力。刻意只给五样，一样都不多：
 * 守门读、一次写事务、编解码纠正列表、判断库是否还在、取时间戳。
 *
 * 不给 Mutex（锁由仓库在外层套）、不给 File 构造、不给落盘实现——
 * `StorageBoundaryOwnershipTest` 那把棘轮会盯着这三件事。
 */
interface MemoryStorage {
    fun note(message: String)

    fun kbExists(kbName: String): Boolean

    fun timestamp(): String

    /** 过 canonical 守门的读：越界、非法、不存在都得到空串 */
    fun read(kbName: String, relativePath: String): String

    /** 同一个库的一次写事务；schema 过新时块内所有写一起被拒 */
    fun writeTransaction(kbName: String, block: MemoryTx.() -> Unit)

    fun decodeCorrections(text: String): List<MemoryCorrection>?

    fun encodeCorrections(corrections: List<MemoryCorrection>): String
}

/**
 * 第5节第3条 拆出的第五格：**记忆纠正与库级 revision**。
 *
 * 这格值得独立存在，是因为它守的是一条**单调性**承诺，而这条承诺以前散在仓库的两处
 * （`saveCorrection` 与 `undoCorrection` 各写一遍"递增并落盘"，两处都要记得写
 * `memory/.revision`）： 之后 revision 是**库级持久化**的，不靠剩余记录的 max 推算
 * ——撤销一条之后再新增，revision 不许倒退，否则后台防护（`getCorrectionsRevision`
 * 的冻结快照比对）会以为世界回到了从前。
 *
 * 两处各写一遍的另一个代价是"报了成功却没写"：落盘要经写事务，被只读保护挡下时
 * 返回值必须真的是 false。现在这条逻辑只有一个所有者，两个动作共用同一段代码。
 *
 * 本类不自持锁、不自己拼路径、不自己落盘：这三件事分别由仓库的 fileMutex、
 * [KnowledgeDocumentStore] 的守门、以及 `atomicWriteText` 那唯一的写链负责。
 */
internal class KnowledgeMemoryStore(private val storage: MemoryStorage) {

    /** memoryId → 纠正记录。文件缺失、JSON 坏掉、路径非法都得到空表而不是异常 */
    fun corrections(kbName: String): Map<String, MemoryCorrection> =
        decode(kbName, CORRECTIONS_FILE)

    /** 纠正记录 + 库级 revision 的一次性快照（生成准备阶段用，消除两次读之间的竞态） */
    fun correctionSnapshot(kbName: String): Pair<Map<String, MemoryCorrection>, Int> =
        corrections(kbName) to revisionOf(kbName)

    /** 库级 revision（单调递增的持久化标记）。没有标记文件时为 0。 */
    fun revisionOf(kbName: String): Int =
        storage.read(kbName, MEMORY_REVISION_FILE).trim().toIntOrNull() ?: 0

    /**
     * 保存一条纠正。返回 false = 库不在了，或被只读保护挡下（此时一个字节都没写）。
     *
     * 撤销之后再新增也必须递增，所以这里读的是**持久化标记**而不是剩余记录的 max（）。
     *
     *  S3 遗漏 B：scene.md 的**文件级**纠正生效时，把当时 scene.md 的条目原文一并
     * 持久进 [CorrectionExtractionFilter.PERSISTED_FACTS_PATH]（见 [persistedSceneFacts]）
     * ——否则话题归档、scene.md 清空后，"被纠正的原文"对撞基准消失，旧值经 raw_topic
     * 漏回提取材料。同一事务内落盘：纠正文件、依据文件、revision 三写同批。
     */
    fun save(
        kbName: String,
        memoryId: String,
        action: CorrectionAction,
        replacementText: String = "",
        targetKbId: String = "",
        muteDuration: MuteDuration = MuteDuration.UNTIL_RESTORE
    ): Boolean {
        if (!storage.kbExists(kbName)) return false
        val nextRevision = revisionOf(kbName) + 1
        val now = storage.timestamp()
        val correction = MemoryCorrection(
            memoryId = memoryId,
            action = action,
            replacementText = replacementText,
            targetKbId = targetKbId,
            revision = nextRevision,
            updatedAt = now,
            muteDuration = muteDuration,
            muteTimestamp = if (action == CorrectionAction.MUTED) now else ""
        )
        val updated = corrections(kbName).toMutableMap().apply { this[memoryId] = correction }
        return persist(kbName, updated.values.toList(), nextRevision, persistedSceneFacts(kbName, memoryId))
    }

    /**
     * 撤销一条纠正。返回 false = 库不在了、记录本来就没有，或被只读保护挡下。
     *
     * ⚠ 撤销**也递增** revision：删记录会让"剩余 max"倒退，后台防护就漏了
     * （0→1→0 是这条承诺最容易被破的写法）。
     *
     *  S3 遗漏 B：撤销 scene 文件级纠正 = 恢复可信注入，持久依据随之清空
     * （写空文件；读取侧解出空集，放行恢复）。
     */
    fun undo(kbName: String, memoryId: String): Boolean {
        if (!storage.kbExists(kbName)) return false
        val current = corrections(kbName)
        if (!current.containsKey(memoryId)) return false
        val nextRevision = revisionOf(kbName) + 1
        val updated = current.toMutableMap().apply { remove(memoryId) }
        return persist(
            kbName, updated.values.toList(), nextRevision,
            if (CorrectionExtractionFilter.isSceneFileCorrection(memoryId)) "" else null
        )
    }

    /**
     * scene 文件级纠正的持久依据内容（ S3 遗漏 B）：当时 scene.md 的条目原文，
     * 与已有依据**取并**——话题切换归档后用户再次纠正同一文件时，旧话题里被纠正
     * 的原文不因覆盖而漏回。非 scene 纠正、或并集为空（scene.md 空、无旧依据）→ null，
     * 不写这个文件。读库走 [MemoryStorage.read] 的同一守门，这里不拼路径。
     */
    private fun persistedSceneFacts(kbName: String, memoryId: String): String? {
        if (!CorrectionExtractionFilter.isSceneFileCorrection(memoryId)) return null
        val existing = CorrectionExtractionFilter.decodePersistedFacts(
            storage.read(kbName, CorrectionExtractionFilter.PERSISTED_FACTS_PATH)
        )
        val merged = existing +
            CorrectionExtractionFilter.sceneEntryFactTexts(
                storage.read(kbName, CorrectionExtractionFilter.SCENE_SOURCE_PATH)
            )
        if (merged.isEmpty()) return null
        return CorrectionExtractionFilter.encodePersistedFacts(merged)
    }

    /**
     * 两个动作共用的一段：把纠正表与新的 revision 写进**同一个**写事务。
     *
     * 顺序也在这段里：纠正文件写失败时绝不再写 revision——否则记录没变而版本跳了，
     * 后台防护会误判成"这一版已经处理过"。
     *
     *  S3 遗漏 B：[persistedFacts] 非 null（scene 纠正的保存/撤销）时，持久依据文件
     * 排在纠正记录之后、revision 之前，同一事务——依据写失败同样不推进 revision。
     */
    private fun persist(kbName: String, records: List<MemoryCorrection>, revision: Int, persistedFacts: String? = null): Boolean {
        var written = false
        storage.writeTransaction(kbName) {
            written = write(CORRECTIONS_FILE, storage.encodeCorrections(records))
            if (written && persistedFacts != null) {
                written = write(PERSISTED_FACTS_FILE, persistedFacts)
            }
            if (written) write(MEMORY_REVISION_FILE, revision.toString())
        }
        if (!written) {
            storage.note("memory write refused or failed: kb=$kbName revision=$revision")
        }
        return written
    }

    private fun decode(kbName: String, relativePath: String): Map<String, MemoryCorrection> {
        val raw = storage.read(kbName, relativePath)
        if (raw.isBlank()) return emptyMap()
        return storage.decodeCorrections(raw)?.associateBy { it.memoryId } ?: emptyMap()
    }

    companion object {
        /** 路径名与仓库共用同一份定义，读写两侧不许各写一遍字面量 */
        const val CORRECTIONS_FILE = KnowledgeRepository.CORRECTIONS_FILE
        const val MEMORY_REVISION_FILE = KnowledgeRepository.MEMORY_REVISION_FILE

        /**  S3 遗漏 B：被纠正条目原文的持久依据落点，单一真源在过滤器那颗 object 上 */
        const val PERSISTED_FACTS_FILE = CorrectionExtractionFilter.PERSISTED_FACTS_PATH
    }
}
