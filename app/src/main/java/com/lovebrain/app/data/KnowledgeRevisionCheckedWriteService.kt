package com.lovebrain.app.data

/**
 * 仓库交给 revision-check 写入格用的能力，六样。
 *
 * 与其他格同一纪律：不给 Mutex、不给 File、不给落盘实现。
 * `appendUnlocked` / `writeUnlocked` / `writeVectorUnlocked` 都是回到仓库唯一写链的写出口，
 * 只读 schema 拒绝与备份节流都在它们下游。
 */
internal interface RevisionCheckStorage {
    fun note(message: String)

    fun kbExists(kbName: String): Boolean

    /** 库级 memory revision（单调递增的持久化标记） */
    fun revisionOf(kbName: String): Int

    /** 锁区内追加核心（调用方已持有 fileMutex） */
    fun appendUnlocked(kbName: String, relativePath: String, content: String)

    /** 锁区内写入核心（调用方已持有 fileMutex） */
    fun writeUnlocked(kbName: String, relativePath: String, content: String)

    /** 锁区内向量写入核心（调用方已持有 fileMutex） */
    fun writeVectorUnlocked(kbName: String, values: Map<String, Int>)
}

/**
 * 第5节第3条 后续拆出的 revision-check 写入格：带修订版这条件校验的原子写入。
 *
 * 搬出来的理由是这三个方法（appendFile / writeFile / writeVector with revision check）
 * 以前混在仓库里，共享同一段"检查 KB 存在 → 读 revision → 比对 expected → 条件写入"逻辑，
 * 但三段各写一遍。现在这段逻辑有一个所有者；锁、路径守门、落盘仍在本类外
 * （仓库的 fileMutex + `atomicWriteText`）。
 *
 * b3-8: 在锁内一次性完成 revision 检查和文件写入，消除先检查后写入的竞态窗口。
 * @return true = 写入成功，false = revision 已变或 KB 不存在
 */
internal class KnowledgeRevisionCheckedWriteService(private val storage: RevisionCheckStorage) {

    /** 带修订版这条件校验的原子追加——在锁内一次性完成 revision 检查和文件写入。 */
    fun appendFileWithRevisionCheck(
        kbName: String,
        relativePath: String,
        content: String,
        expectedRevision: Int
    ): Boolean {
        if (!storage.kbExists(kbName)) {
            storage.note("appendFileWithRevisionCheck skipped: kb no longer exists")
            return false
        }
        val currentRevision = storage.revisionOf(kbName)
        if (currentRevision != expectedRevision) {
            storage.note("appendFileWithRevisionCheck skipped: revision changed (expected=$expectedRevision, current=$currentRevision)")
            return false
        }
        storage.appendUnlocked(kbName, relativePath, content)
        return true
    }

    /** 带修订版这条件校验的原子写入——在锁内一次性完成 revision 检查和文件写入。 */
    fun writeFileWithRevisionCheck(
        kbName: String,
        relativePath: String,
        content: String,
        expectedRevision: Int
    ): Boolean {
        if (!storage.kbExists(kbName)) {
            storage.note("writeFileWithRevisionCheck skipped: kb no longer exists")
            return false
        }
        val currentRevision = storage.revisionOf(kbName)
        if (currentRevision != expectedRevision) {
            storage.note("writeFileWithRevisionCheck skipped: revision changed (expected=$expectedRevision, current=$currentRevision)")
            return false
        }
        storage.writeUnlocked(kbName, relativePath, content)
        return true
    }

    /** 带修订版这条件校验的向量写入——在锁内一次性完成 revision 检查和向量写入。 */
    fun writeVectorWithRevisionCheck(
        kbName: String,
        values: Map<String, Int>,
        expectedRevision: Int
    ): Boolean {
        if (!storage.kbExists(kbName)) {
            storage.note("writeVectorWithRevisionCheck skipped: kb no longer exists")
            return false
        }
        val currentRevision = storage.revisionOf(kbName)
        if (currentRevision != expectedRevision) {
            storage.note("writeVectorWithRevisionCheck skipped: revision changed (expected=$expectedRevision, current=$currentRevision)")
            return false
        }
        storage.writeVectorUnlocked(kbName, values)
        return true
    }
}
