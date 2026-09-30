package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.PreconditionReason
import com.lovebrain.app.model.ProfileTransactionResult
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * 仓库交给画像事务格用的能力，十三样，一样都不多。
 *
 * 这一格与画像格 [KnowledgeProfileStore] 不同：它要的是"多文件一次事务 + 失败回滚"，
 * 所以比画像格（六样）多出向量读改、阶段 strict 改写、路径解析与一次写事务四组能力。
 * 锁、路径守门、只读判定、落盘与备份节流仍全在仓库那一侧——本格只经这道窄接口回到那几件，
 * 没有 Mutex、没有第二条落盘链、没有第二份 canonical 判定。
 *
 * 与其它格子一样：`read` / `writeUnlocked` / `kbExists` 与文档格/画像格同名同签，
 * 在仓库那一侧由同一个 `RepoStorage` override 满足（一处实现，多接口共用）。
 */
internal interface ProfileTxStorage {
    /** 库存在性（调用方已持锁） */
    fun kbExists(kbName: String): Boolean

    /** schema 过新即只读 */
    fun isReadOnly(kbName: String): Boolean

    /** 库级 memory revision（单调递增） */
    fun memoryRevisionOf(kbName: String): Int

    /** 过 canonical 守门的读（与公开读同源，旧布局有回退） */
    fun read(kbName: String, relativePath: String): String

    /** 唯一写链：仓库的 `writeFileUnlocked`，带着只读 schema 拒绝与备份节流 */
    fun writeUnlocked(kbName: String, relativePath: String, content: String)

    /** 画像五维状态向量（解析不到默认 50） */
    fun readVector(kbName: String): Map<String, Int>

    /** 就地更新 warmth.md 的五维状态向量 */
    fun writeVector(kbName: String, values: Map<String, Int>)

    /** 阶段白名单归一化；非白名单返回 null（留痕在画像格） */
    fun normalizeStage(raw: String, opLabel: String): String?

    /** 改写 warmth.md 里的阶段标签行（与画像格 strict 版共用这一份） */
    fun rewriteStageLine(warmth: String, stage: String): String

    /** 解析库内安全路径；越界或非法返回 null（仓库的 `safeKbFile`） */
    fun resolvePath(kbName: String, relativePath: String): File?

    /** 一次事务（锁内）：写 / 读 / 删 / 改 kb.json 都经这一个事务对象 */
    fun <T> runTx(kbName: String, block: ProfileTxOps.() -> T): T

    /** 写后节流备份——与公开写同一调度 */
    fun scheduleBackup()

    /** ISO-8601 带时区的当前时间（kb.json 的 `updatedAt` 用） */
    fun timestamp(): String
}

/**
 * 画像事务格在一次事务里能做的四类落盘动作。
 *
 * 与 [ArchiveTx] / [CatalogTx] 同形：`KnowledgeTx` 的一个收窄视图，让"格子里没有 File"这件事
 * 能被静态检查。名字刻意另起一个：`ArchiveTx.() -> Unit` 与 `ProfileTxOps.() -> T`
 * 都擦除成 `Function1`，同名就是 platform declaration clash。
 */
internal interface ProfileTxOps {
    /** 原子写。false = 被只读保护或路径非法挡下，一个字节都没落 */
    fun write(relativePath: String, content: String): Boolean

    /** 读；越界、非法或不存在都得到空串 */
    fun readTextAt(relativePath: String): String

    /** 删除。false = 没删（被挡、越界或本来就不存在） */
    fun deleteAt(relativePath: String): Boolean

    /** 读改写 kb.json；库不存在 / 只读 / JSON 坏掉返回 false 且不写 */
    fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean
}

/**
 * §5.3 的最后一格：**画像事务性写入**（`applyProfileUpdateAtomically` 的跨文件事务 + 备份 + 回滚 + 校验）。
 *
 * 画像格 [KnowledgeProfileStore] 的注释里那行"它本体仍在仓库里等 archive 那一格"指的就是这一块：
 * 它要的是"多文件一次事务 + 失败回滚"，宽于画像格的零存储能力承诺，硬塞进画像格等于把回滚语义
 * 也搬进去。于是它单立一格——本类。
 *
 * 从仓库搬出的有五件（行为一字未改）：
 *  ① `applyProfileUpdateAtomically` 的锁内核心（前置条件 → 备份 → 写 → 失败回滚 → 回滚后校验）；
 *  ② `updateStageUnlockedStrict`（strict 版阶段改写，IO 失败必抛）；
 *  ③ `updateWarmthStageLabelUnlockedStrict`（strict 版 warmth 标签行改写）；
 *  ④ `snapshotBeforeWriteUnlocked`（写前快照：存在性与旧内容出自同一道守门）；
 *  ⑤ `readVectorUnlockedFast`（无锁向量读，仓库里再无第二处用，遂一并搬走）。
 *
 * **写链未变**：本类不自持锁、不自己拼路径、不自己落盘、不配 JSON——这四件事分别由仓库的
 * `fileMutex`、[KnowledgeDocumentStore.resolve]、`writeFileUnlocked`/`atomicWriteText` 那唯一的写链、
 * 以及仓库那一份 Json 配置负责。仓库的 `applyProfileUpdateAtomically` 只剩一次转手：
 * `withContext(Dispatchers.IO) { fileMutex.withLock { profileTx.apply(...) } }`，与 `rotateTopic`
 * 转给归档格同形。
 */
internal class KnowledgeProfileTransactionService(private val storage: ProfileTxStorage) {

    /**
     * 画像更新事务性写入——调用方必须已持有仓库的 `fileMutex` 并在 IO 线程上。
     *
     * 返回 typed [ProfileTransactionResult]，替代模糊 Boolean。
     *
     * - 所有文件写入、向量写入、阶段更新、warmth 标签更新在同一锁内完成
     * - **回滚与校验也走同一条写边界**：快照的存在性与旧内容出自同一道守门，恢复用
     *   `ProfileTxOps.write`、删除用 `ProfileTxOps.deleteAt`，不再有第二条 `atomicWriteText`
     *   （以前那三段自己拼 `File(File(knowledgeRoot, kb), path)`，库名带 `..` 时会把
     *   知识库根外面的文件写空——见 `ProfileTransactionRollbackBoundaryTest`）
     * - backup 覆盖所有实际会被修改的文件（包括 warmth.md——即使 payload.warmth 为 null，
     *   stage_changed=true 时 updateWarmthStageLabel 仍会修改 warmth.md）
     * - IO 失败必须抛出（使用 strict 版本），不吞错误
     * - 任一步失败自动 rollback 到 backup
     * - rollback 成功 → [ProfileTransactionResult.RolledBack]
     * - rollback 自身失败 → [ProfileTransactionResult.RollbackFailed]（携带失败路径列表）
     *
     * @return typed result——调用方据此给出精确的 UI 反馈
     */
    fun apply(
        kbName: String,
        me: String?,
        her: String?,
        warmth: String?,
        stageChanged: Boolean,
        newStage: String?,
        expectedRevision: Int
    ): ProfileTransactionResult {
        if (!storage.kbExists(kbName)) {
            com.lovebrain.app.util.L.w("applyProfileUpdateAtomically: kb no longer exists")
            return ProfileTransactionResult.PreconditionFailed(
                PreconditionReason.KB_NOT_FOUND
            )
        }
        val currentRevision = storage.memoryRevisionOf(kbName)
        if (storage.isReadOnly(kbName)) {
            // 只读判定本来藏在每一次写里面（writeFileUnlocked 静默跳过），于是这一段
            // 所有写都不落、也没有任何一步抛，函数一路走到 Success：磁盘没变、嘴里说成功。
            // 与公开的 transaction() 同一把尺——只读是**前置条件**，在入口就报出来。
            com.lovebrain.app.util.L.w("applyProfileUpdateAtomically refused: kb is read-only (schema newer)")
            return ProfileTransactionResult.PreconditionFailed(
                PreconditionReason.LIBRARY_READ_ONLY
            )
        }
        if (currentRevision != expectedRevision) {
            com.lovebrain.app.util.L.w("applyProfileUpdateAtomically: revision changed (expected=$expectedRevision, current=$currentRevision)")
            return ProfileTransactionResult.PreconditionFailed(
                PreconditionReason.REVISION_CONFLICT
            )
        }

        // 确定实际会被修改的文件列表——stage_changed=true 时 warmth.md 也会被修改
        val willChangeStage = stageChanged && !newStage.isNullOrBlank()

        // 收集写入目标和旧内容（backup）
        val writeTargets = mutableListOf<Pair<String, String>>()
        me?.let { writeTargets.add("understand/me.md" to it) }
        her?.let { writeTargets.add("understand/her.md" to it) }
        warmth?.let { writeTargets.add("understand/warmth.md" to it) }

        // backup 所有可能被修改的文件
        // 记录文件原先是否存在——rollback 时原不存在的文件应删除而非创建空文件
        val backups = mutableMapOf<String, Pair<Boolean, String>>() // path -> (existed, oldContent)
        for ((path, _) in writeTargets) backups[path] = snapshotBeforeWrite(kbName, path)
        // warmth.md 即使不在 writeTargets 中，stage 变化时也会被 updateWarmthStageLabel 修改
        if (willChangeStage && "understand/warmth.md" !in backups) {
            backups["understand/warmth.md"] =
                snapshotBeforeWrite(kbName, "understand/warmth.md")
        }
        // kb.json backup（stage 变化时 updateStageStrict 会修改它）
        if (willChangeStage) {
            backups["kb.json"] = snapshotBeforeWrite(kbName, "kb.json")
        }
        // 向量 backup（warmth 变化时向量同步会修改 warmth.md 中的数值）
        val oldVector = if (warmth != null) {
            storage.readVector(kbName)
        } else null

        try {
            // 逐个写入画像文件
            for ((path, content) in writeTargets) {
                storage.writeUnlocked(kbName, path, content)
            }

            // warmth 向量同步——保持 warmth.md 中的数值与文件内容一致
            // 注意：writeVector 会修改 warmth.md，如果 warmth 内容已写入
            if (warmth != null && oldVector != null && oldVector.isNotEmpty()) {
                storage.writeVector(kbName, oldVector)
            }

            // 阶段更新——使用 strict 版本，IO 失败必须抛出
            if (willChangeStage) {
                updateStageStrict(kbName, newStage!!)
                updateWarmthStageLabelStrict(kbName, newStage)
            }
        } catch (e: Exception) {
            // Rollback——恢复所有 backup，跟踪失败路径
            // 原先存在的文件恢复内容；原先不存在的文件删除（不创建空文件）
            // 必须检查 file.delete() 返回值——delete 失败不抛异常但返回 false
            com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: write failed, rolling back", e)
            val rollbackFailures = mutableListOf<String>()
            for ((path, existedAndContent) in backups) {
                try {
                    val (existed, oldContent) = existedAndContent
                    if (existed) {
                        // 恢复走唯一写链（只读拒绝、建父目录、原子 rename 都在里面），
                        // 不再自己 atomicWriteText——那等于给回滚开第二条写边界
                        val restored = storage.runTx(kbName) { write(path, oldContent) }
                        val now = storage.runTx(kbName) { readTextAt(path) }
                        if (!restored || now != oldContent) {
                            com.lovebrain.app.util.L.e(
                                "applyProfileUpdateAtomically: CRITICAL rollback verification " +
                                    "failed for $path (written=$restored, content ${if (now == oldContent) "matches" else "mismatch"})"
                            )
                            rollbackFailures.add(path)
                        }
                    } else {
                        // 原先不存在的文件——rollback 应删除，必须检查返回值。
                        // 存在性与删除都过守门：库外的路径根本解析不出来，于是"不存在的"保持不存在，
                        // 也不会拿 delete() 去碰不属于本库的文件。
                        val present = storage.resolvePath(kbName, path)?.exists() == true
                        if (present) {
                            val deleted = storage.runTx(kbName) { deleteAt(path) }
                            if (!deleted) {
                                com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL rollback delete failed for $path (delete returned false)")
                                rollbackFailures.add(path)
                            }
                        }
                    }
                } catch (rollbackErr: Exception) { // cancel-safe: 这里只有写与删（都走守门后的同步 I/O），协程取消不会从这里抛出
                    com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL rollback failed for $path", rollbackErr)
                    rollbackFailures.add(path)
                }
            }
            // 最终 snapshot verification——原存在的文件必须存在且内容正确；原不存在的文件必须不存在
            // verification 自身的 I/O 异常（readText 抛异常、exists 抛异常等）
            // 必须加入 rollbackFailures，不得从 apply 直接 throw 绕过 typed result
            for ((path, existedAndContent) in backups) {
                try {
                    // 校验也必须过守门：这条 `File(File(knowledgeRoot, kb), path)` 之前
                    // 是全仓最后一条"自己拼库内路径"，现在解析不出来就是 null
                    val file = storage.resolvePath(kbName, path)
                    val (existed, oldContent) = existedAndContent
                    if (existed) {
                        // 快照说它在 → 这一条路径当时是解析得出来的；现在解析不出就是无法确认，按失败报
                        val existsNow = file?.isFile == true
                        val contentMatches = if (file != null && existsNow) {
                            try {
                                file.readText(Charsets.UTF_8) == oldContent
                            } catch (verifyErr: Exception) {
                                // readText 自身抛 I/O 异常 → 视为 verification 失败
                                com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL verification read failed for $path", verifyErr)
                                false
                            }
                        } else false
                        if (file == null || !existsNow || !contentMatches) {
                            if (path !in rollbackFailures) {
                                com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL post-rollback verification failed for $path")
                                rollbackFailures.add(path)
                            }
                        }
                    } else {
                        val stillExists = try {
                            file?.exists() == true
                        } catch (verifyErr: Exception) {
                            com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL verification exists check failed for $path", verifyErr)
                            true // 无法确认 → 视为失败
                        }
                        if (stillExists && path !in rollbackFailures) {
                            com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL post-rollback verification failed for $path (file should not exist)")
                            rollbackFailures.add(path)
                        }
                    }
                } catch (verifyErr: Exception) {
                    // verification 本身的任何异常都加入 rollbackFailures
                    com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL verification exception for $path", verifyErr)
                    if (path !in rollbackFailures) {
                        rollbackFailures.add(path)
                    }
                }
            }
            // 取消不是业务失败：回滚照做，但做完原样上抛，不得伪装成 RolledBack 结果
            if (e is CancellationException) throw e
            // 区分 rollback 成功与失败——不再吞错误也不模糊 throw
            return if (rollbackFailures.isEmpty()) {
                ProfileTransactionResult.RolledBack(e)
            } else {
                ProfileTransactionResult.RollbackFailed(e, rollbackFailures)
            }
        }

        storage.scheduleBackup()
        return ProfileTransactionResult.Success
    }

    /**
     * updateStage 的 strict 版本——IO 失败时抛出异常，不吞错误。
     * 供事务性 API 使用；非事务场景仓库仍用画像格的容错版（`updateStageUnlocked`）。
     *
     * 白名单判定与拒绝对那半句日志已归画像格；这里只多一样：**拒的原因要能分得开**
     * （非白名单 vs 写不进），所以两种失败各抛各的。
     */
    private fun updateStageStrict(kbName: String, stage: String) {
        if (stage.isBlank()) return
        val normalized = storage.normalizeStage(stage, "updateStageStrict")
            ?: throw java.io.IOException("非法阶段：$stage")
        val written = storage.runTx(kbName) {
            updateMeta { kb -> kb.copy(stage = normalized, updatedAt = storage.timestamp()) }
        }
        if (!written) {
            throw java.io.IOException("updateStageStrict 无法写 $kbName/kb.json（库缺失、schema 过新或 JSON 不可解析）")
        }
    }

    /**
     * updateWarmthStageLabel 的 strict 版本——IO 失败时抛出异常。
     * 供事务性 API 使用。
     */
    private fun updateWarmthStageLabelStrict(kbName: String, newStage: String) {
        if (newStage.isBlank()) return
        val stage = storage.normalizeStage(newStage, "updateWarmthStageLabelStrict")
            ?: throw java.io.IOException("非法阶段：$newStage")
        val path = KnowledgeProfileStore.WARMTH_FILE
        val warmth = storage.read(kbName, path)
        if (warmth.isBlank()) return
        val updated = storage.rewriteStageLine(warmth, stage)
        if (updated != warmth) storage.writeUnlocked(kbName, path, updated)
    }

    /**
     * 写前快照：目标文件的**存在性与旧内容必须出自同一道门**。
     *
     * 之前是裸 `File(File(knowledgeRoot, kb), path).exists()` 配守门版读：
     * 库名带 `..` 时前者说"在"、后者给空串，回滚因此把库外那个文件当"本库的旧文件"写成空。
     * 这里刻意读**新路径那一份**而不是带旧布局回退的公开读——快照要描述"这次写会覆盖谁"，
     * 不是"读侧会看到什么"。读不出内容时**让异常穿出去**：这一段在所有写之前，抛出来说明
     * 一个字节都没动过；把它吞成空串反而会害命——回滚会照着"旧内容是空"把真文件写空。
     */
    private fun snapshotBeforeWrite(kbName: String, relativePath: String): Pair<Boolean, String> {
        val file = storage.resolvePath(kbName, relativePath) ?: return false to ""
        if (!file.isFile) return false to ""
        return true to file.readText(Charsets.UTF_8)
    }
}
