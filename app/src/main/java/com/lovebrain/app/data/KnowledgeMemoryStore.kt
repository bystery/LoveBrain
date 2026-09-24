package com.lovebrain.app.data

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
 * §5.3 拆出的第五格：**记忆纠正与库级 revision**。
 *
 * 这格值得独立存在，是因为它守的是一条**单调性**承诺，而这条承诺以前散在仓库的两处
 * （`saveCorrection` 与 `undoCorrection` 各写一遍"递增并落盘"，两处都要记得写
 * `memory/.revision`）：R07 之后 revision 是**库级持久化**的，不靠剩余记录的 max 推算
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
     * 撤销之后再新增也必须递增，所以这里读的是**持久化标记**而不是剩余记录的 max（R07）。
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
        return persist(kbName, updated.values.toList(), nextRevision)
    }

    /**
     * 撤销一条纠正。返回 false = 库不在了、记录本来就没有，或被只读保护挡下。
     *
     * ⚠ 撤销**也递增** revision：删记录会让"剩余 max"倒退，后台防护就漏了
     * （0→1→0 是这条承诺最容易被破的写法）。
     */
    fun undo(kbName: String, memoryId: String): Boolean {
        if (!storage.kbExists(kbName)) return false
        val current = corrections(kbName)
        if (!current.containsKey(memoryId)) return false
        val nextRevision = revisionOf(kbName) + 1
        val updated = current.toMutableMap().apply { remove(memoryId) }
        return persist(kbName, updated.values.toList(), nextRevision)
    }

    /**
     * 两个动作共用的一段：把纠正表与新的 revision 写进**同一个**写事务。
     *
     * 顺序也在这段里：纠正文件写失败时绝不再写 revision——否则记录没变而版本跳了，
     * 后台防护会误判成"这一版已经处理过"。
     */
    private fun persist(kbName: String, records: List<MemoryCorrection>, revision: Int): Boolean {
        var written = false
        storage.writeTransaction(kbName) {
            written = write(CORRECTIONS_FILE, storage.encodeCorrections(records))
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
    }
}
