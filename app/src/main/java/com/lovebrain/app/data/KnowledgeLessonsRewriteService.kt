package com.lovebrain.app.data

import com.lovebrain.app.domain.port.LessonsRewriteResult

/**
 * 仓库交给「读—整理—条件替换」这一格用的能力，五样。
 *
 * 与其他格同一纪律（见 [RevisionCheckStorage]）：**不给 Mutex、不给 File、不给第二份落盘实现**。
 * 这一格要的恰好是"锁内那一次读"与"锁内那一次写"，别的都回仓库那一侧：
 * 路径守门仍在 [KnowledgeDocumentStore.resolve]，只读判定与原子写仍在 `atomicWriteText`，
 * 备份节流仍在 `writeFileCheckedUnlocked` 下游——**这条不是第二条写链**，
 * 它是同一条链上多出来的一个调用方，而且只在仓库已经持有 `fileMutex` 的那一段里被调到。
 */
internal interface LessonsRewriteStorage {
    /** 观测出口：拒绝/挡下的话该由仓库决定怎么说，策略类不自建日志通道 */
    fun note(message: String)

    /** 调用方必须已持有仓库那把锁；这里只回答"库还在不在" */
    fun kbExists(kbName: String): Boolean

    /** 库级 memory revision（与 [KnowledgeRevisionCheckedWriteService] 读的是同一份单调标记） */
    fun revisionOf(kbName: String): Int

    /** 守门读（与公开 `readFile` 同一道 canonical 门），锁内调用 */
    fun readGuarded(kbName: String, relativePath: String): String

    /** 唯一写链上"有没有真的落盘"那一支（`writeFileCheckedUnlocked`），锁内调用；false = 一个字节都没写 */
    fun writeChecked(kbName: String, relativePath: String, content: String): Boolean
}

/**
 * 「读—整理—条件替换」这一次事务的本体（ 第8节第2条「可重入的小范围整理」+ 第8节第3条「计数与发号在写事务里」）。
 *
 * ## 它为什么必须存在（不是"把三段代码搬一起"）
 * 整理判据 [com.lovebrain.app.domain.LessonDoc.tidy] 早就写好了，缺的是那道门：
 * 旧写法是 `readFile`（仓库锁**外**）→ domain 数批、发号 → `appendFileWithRevisionCheck`（仓库锁**内**）。
 * "读"与"写"分属两次加锁，中间那段窗口里任何一次并发追加都会：
 * - 把刚数出来的号再发一遍（`1、1、3、1、5、1` 的一半成因），或
 * - 把刚整理好的那一篇整篇覆盖掉（**丢正文**）。
 * 本类把「读正文 → 交给 domain 整理与发号 → revision 检查 → 快照 → 条件替换」收在**同一段临界区**里，
 * 一次加锁、一次判定，锁的持有者仍是仓库那一颗 `fileMutex`（见 [KnowledgeRepository.readTidyAndReplaceWithRevisionCheck]）。
 *
 * ## 三条边界各自的落点
 * 1. **同一段锁区**：本类不自持锁，它只在仓库已持锁时被调用；四步之间没有人能插进来。
 * 2. **写前先有快照**：[rewrite] 在替换**之前**把原件写到 [snapshotPathOf] 那个名字；
 *    快照没落成 ⇒ 直接收手（`SnapshotFailed`），原件一个字节都不动——"整理判据写错了"不该由用户的文件买单。
 *    替换没落成 ⇒ 交回 `WriteFailed(composed)`，原件仍在，且落盘后**再读回来核一次**，
 *    所以"写失败仍报成功"这种坏实现在这道门里过不去。
 * 3. **只在真变了才写**：`compose` 交回 null，或交回的全文与磁盘上那份**逐字相同**，都走 `NothingToWrite`，
 *    连快照都不落——于是"整理过一次的文件再整理一次"不会留下第二次痕迹（可重入）。
 *
 * 取消语义：本类没有任何挂起点（全非挂起），取消只可能从仓库那侧的 `withLock` / `withContext` 抛出，
 * 原样穿过这里，不在这里被咽掉。
 *
 * @param storage 仓库给的那份受限视图
 */
internal class KnowledgeLessonsRewriteService(private val storage: LessonsRewriteStorage) {

    /**
     * 一次事务的全部五步。返回值即"字节到底落没落"，调用方不需要再猜 null。
     *
     * @param compose domain 侧的整理与发号：拿到**锁内读到的**正文，交回要整篇替换的全文；
     *                null = "这批不用写"。它在本类里被调用，因而天然落在仓库那把锁内。
     */
    fun rewrite(
        kbName: String,
        relativePath: String,
        expectedRevision: Int,
        compose: (existing: String) -> String?
    ): LessonsRewriteResult {
        // ① revision 检查在读正文之前：号都没发、正文都没读，谈不上一寸写入
        if (!storage.kbExists(kbName)) {
            storage.note("lessons rewrite skipped: kb no longer exists")
            return LessonsRewriteResult.MissingLibrary("库目录或 kb.json 不在了")
        }
        val currentRevision = storage.revisionOf(kbName)
        if (currentRevision != expectedRevision) {
            storage.note(
                "lessons rewrite skipped: revision changed (expected=$expectedRevision, current=$currentRevision)"
            )
            return LessonsRewriteResult.RevisionChanged
        }

        // ② 锁内读到的那一份就是发号与整理的唯一输入（旧写法读的是锁外那份）
        val existing = storage.readGuarded(kbName, relativePath)
        val composed = compose(existing)
            ?: return LessonsRewriteResult.NothingToWrite
        if (composed == existing) {
            // ③ 逐字相同 = 已经很干净 = 什么都不写（不重写、不快照、不动时间戳）
            storage.note("lessons rewrite no-op: composed text is byte-identical to the file")
            return LessonsRewriteResult.NothingToWrite
        }

        // ② 写前先有快照：拿原件赌判据没写错，这件事在这道门里不被允许
        if (existing.isNotEmpty() &&
            !storage.writeChecked(kbName, snapshotPathOf(relativePath), existing)
        ) {
            storage.note("lessons rewrite aborted: pre-write snapshot did not land, original untouched")
            return LessonsRewriteResult.SnapshotFailed
        }

        if (!storage.writeChecked(kbName, relativePath, composed)) {
            storage.note("lessons rewrite failed: replacement did not land; composed text handed back, not reported as success")
            return LessonsRewriteResult.WriteFailed(composed)
        }
        // 落盘后回读核一次：写链哪天出一次"返回 true 但字节没落"的谎，这里就是它的证人
        if (storage.readGuarded(kbName, relativePath) != composed) {
            storage.note("lessons rewrite failed: read-back does not match the composed text")
            return LessonsRewriteResult.WriteFailed(composed)
        }
        // 空文件没有原件可保，所以那一路不交快照路径——"没得保"与"没保住"是两件事，不许混报
        return LessonsRewriteResult.Rewritten(
            snapshotPath = if (existing.isEmpty()) null else snapshotPathOf(relativePath)
        )
    }

    companion object {
        /**
         * 整理前原件快照的落点：与原件同目录、点名 `.pre-tidy`，一个库只留**最近一份**。
         *
         * 为什么是库内的隐藏文件而不是 `.backup/` 里的一份：
         * - `.backup/` 那族是"≥12 小时整库节流"（[KnowledgeBackupService]），不保证整理前真有原件；
         * - `atomicWriteText` 的 `.bak` 只是**同一次写**的回滚件，写完就删，留不住"整理前"那一刻；
         * - 落在库内才过同一道 canonical 守门、同一条写链，不需要第二个路径所有者。
         * 名字以 `.` 开头与 `memory/.revision` 同族：它不是用户内容，不进预览也不进 prompt（读侧按名字取文件）。
         */
        internal fun snapshotPathOf(relativePath: String): String {
            val dir = relativePath.substringBeforeLast('/', "")
            val base = relativePath.substringAfterLast('/')
            val stem = base.removeSuffix(".md")
            val name = if (dir.isEmpty()) ".$stem.pre-tidy.md" else "$dir/.$stem.pre-tidy.md"
            return name
        }
    }
}
