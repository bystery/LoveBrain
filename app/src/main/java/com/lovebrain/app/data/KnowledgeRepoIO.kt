package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 原子写入工具（从 [KnowledgeRepository] 拆出）。
 *
 * 这三件是仓库唯一的落盘出口与归属判定，仍只对 [KnowledgeRepository] 暴露为 internal 扩展函数——
 * 不持锁、不拼库内路径，调用方仍是仓库里那些已持有 [KnowledgeRepository.fileMutex] 的写核。
 * 拆成扩展函数只是为缩短 [KnowledgeRepository] 本体，不变「落盘只有这一处」这条不变量。
 */
// ═══════════ 原子写入工具 ═══════════

/**
 * 唯一落盘出口（2026-09-24 的写边界要求）。
 *
 * 独立原话是：注释写着"所有写路径最终落到 writeFileUnlocked"，
 * 但十几条路径直接 `atomicWriteText` 或自己 `File(dir, …)`，于是 v4 库在 v3 App 里
 * 虽然显示 read-only，元数据和纠正文件照样能被改写。
 *
 * 所以拒绝判定放在**这里**，而且按**文件属于哪个库**判，不按"调用方有没有传 kbName"判：
 * Repository 里任何一条写链——包括以后新加的、忘记走 [KnowledgeRepository.transaction] 的——
 * 都必须先经过这道门。返回 false 表示什么都没写。
 *
 * 真正的字节操作在 [rawAtomicWriteText]，它只被本函数调用。
 */
internal fun KnowledgeRepository.atomicWriteText(file: File, content: String): Boolean {
    kbOwning(file)?.let { owner ->
        if (migrator.isReadOnly(owner)) {
            com.lovebrain.app.util.L.w(
                "write refused by the single write boundary: $owner is read-only " +
                    "(schema newer than this build) — target ${file.name}"
            )
            return false
        }
    }
    rawAtomicWriteText(file, content)
    return true
}

/**
 * 这个文件落在哪个知识库下；root 级 marker（`.kb_initialized`、`.last_backup`）
 * 与 `.backup/` 之类以点开头的目录返回 null，它们不属于任何库，也不受只读保护。
 */
internal fun KnowledgeRepository.kbOwning(file: File): String? {
    val root = runCatching { knowledgeRoot.canonicalPath }.getOrNull() ?: return null
    val abs = runCatching { file.canonicalPath }.getOrNull() ?: return null
    val prefix = root + File.separator
    if (!abs.startsWith(prefix)) return null
    val first = abs.removePrefix(prefix).substringBefore(File.separator)
    if (first.isEmpty() || first.startsWith(".")) return null
    return first
}

/**
 * 原子写入：先写临时文件 → fsync 刷盘 → rename 覆盖目标文件。
 * rename 失败时保留原件并报错，不回退到直接覆盖（直接写可能导致半写损坏）。
 *
 * 调研依据：SQLite 的原子提交机制（写 journal → flush → rename → delete journal），
 * 以及 Kotlin File.writeText() 无原子保证（Kotlin 官方文档确认）。
 * rename 在 POSIX/Android 上是原子操作（SQLite 文档确认），
 * 确保目标文件要么是旧内容要么是新内容，绝不会出现写一半的中间状态。
 *
 * 仅供 [atomicWriteText] 调用——需要写文件请走 [KnowledgeRepository.transaction] / [KnowledgeRepository.KnowledgeTx]。
 */
internal fun KnowledgeRepository.rawAtomicWriteText(file: File, content: String) {
    val tmp = File(file.parentFile, ".${file.name}.tmp")
    try {
        FileOutputStream(tmp).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.fd.sync() // 强制刷盘，防断电丢失
        }
        // 优先使用 NIO Files.move(REPLACE_EXISTING)——
        // 在 POSIX/Android 上是原子替换，在 Windows 上也能安全替换已存在文件。
        var renamed = false
        try {
            val targetPath = file.toPath()
            java.nio.file.Files.move(
                tmp.toPath(),
                targetPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
            renamed = true
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            // 某些文件系统不支持 ATOMIC_MOVE → 回退到 REPLACE_EXISTING（非原子但安全）
            try {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                renamed = true
            } catch (e2: Exception) {
                com.lovebrain.app.util.L.w("atomicWriteText: NIO move failed: ${e2.message}")
            }
        } catch (e: Exception) {
            com.lovebrain.app.util.L.w("atomicWriteText: NIO ATOMIC_MOVE failed: ${e.message}")
        }
        // NIO 全部失败时的回退——严格检查每步返回值，保证任何路径下至少保留 old target 或 recoverable backup。
        // 流程：target → backup（必须成功才继续）→ tmp → target → 成功删 backup / 失败用 backup 恢复。
        if (!renamed) {
            val backupTmp = File(file.parentFile, ".${file.name}.bak")
            // 清理可能存在的旧 .bak 残留——防历史 backup 干扰恢复逻辑
            if (backupTmp.exists()) {
                backupTmp.delete()
            }
            // Step 1: 如果旧文件存在，先 rename 到 .bak（保留旧文件内容）
            // 必须检查返回值——backup 失败则保持 target 原样，退出
            val backupSucceeded = if (file.exists()) {
                file.renameTo(backupTmp)
            } else {
                true // 无旧文件 → 视为 backup 成功（backupTmp 不存在）
            }
            if (!backupSucceeded) {
                // backup 失败——target 仍在，不继续 tmp → target
                com.lovebrain.app.util.L.w("atomicWriteText: backup rename failed, keeping original: ${file.name}")
                throw java.io.IOException("atomic write backup failed: ${file.name}")
            }
            // Step 2: rename tmp → 目标文件（此时目标不存在）
            if (tmp.renameTo(file)) {
                renamed = true
                // 成功后删除旧备份
                if (backupTmp.exists()) backupTmp.delete()
            } else {
                // tmp → target 失败——用 backup 恢复 target
                com.lovebrain.app.util.L.w("atomicWriteText: tmp→target rename failed, restoring backup: ${file.name}")
                if (backupTmp.exists()) {
                    val restored = backupTmp.renameTo(file)
                    if (!restored) {
                        // 恢复也失败——保留 backup 文件，绝不删除
                        com.lovebrain.app.util.L.e("atomicWriteText: CRITICAL - backup restore also failed! Backup preserved at: ${backupTmp.absolutePath}", null)
                    }
                }
                throw java.io.IOException("atomic rename failed after fallback: ${file.name}")
            }
        }
    } finally {
        // 清理可能残留的临时文件（rename 成功后 tmp 已不存在，此处只是兜底）
        if (tmp.exists()) tmp.delete()
    }
}

// ═══════════ 写核与守门（从 KnowledgeRepository 拆出的无锁原语） ═══════════
//
// 这些是仓库内部「已持有 fileMutex」路径用的无锁原语与守门判定。
// 拆成 internal 扩展函数只为缩短 KnowledgeRepository 本体；不变量未变：
// 落盘仍只经 atomicWriteText，路径仍只经 safeKbFile/safeRootFile，只读判定仍在入口。

/** 判断知识库是否真实存在（目录存在 + kb.json 存在）。调用方必须已持有文件互斥锁或处于单线程路径 */
internal fun KnowledgeRepository.kbExistsUnlocked(kbName: String): Boolean {
    val dir = File(knowledgeRoot, kbName)
    val meta = File(dir, "kb.json")
    return dir.isDirectory && meta.isFile
}

/**
 * 库 schema 比本 App 支持的还新时拒绝写入。
 *
 * 所有写路径（含 appendFileUnlocked）最终都落到这里，所以拦截只需要这一处；
 * 用旧代码往 v4 结构里写 v3 形状，会把新字段静默抹掉。
 * 与"KB 已删除即 no-op"保持同一风格：不抛异常、不复活目录，只记一条日志。
 */
internal fun KnowledgeRepository.refusedByReadOnlySchema(kbName: String, op: String, relativePath: String): Boolean {
    if (!migrator.isReadOnly(kbName)) return false
    com.lovebrain.app.util.L.w(
        "$op refused on $kbName/$relativePath — schema newer than this build, library is read-only"
    )
    return true
}

/** 锁区内写入核心：不抢锁。调用方必须已持有文件互斥锁（Mutex 非重入，锁内再抢=永久挂起） */
internal fun KnowledgeRepository.writeFileUnlocked(kbName: String, relativePath: String, content: String) {
    if (refusedByReadOnlySchema(kbName, "writeFile", relativePath)) return
    val file = safeKbFile(kbName, relativePath) ?: return
    file.parentFile?.mkdirs()
    atomicWriteText(file, content)
    scheduleDebouncedBackup()
}

/** 锁区内追加核心：不抢锁。调用方必须已持有文件互斥锁 */
internal fun KnowledgeRepository.appendFileUnlocked(kbName: String, relativePath: String, content: String) {
    if (refusedByReadOnlySchema(kbName, "appendFile", relativePath)) return
    val file = safeKbFile(kbName, relativePath) ?: return
    file.parentFile?.mkdirs()
    val existing = if (file.exists()) file.readText() else ""
    atomicWriteText(file, existing + content)
    scheduleDebouncedBackup()
}

/**
 * 根下"看着像一个库目录"的那些：是目录、名字不以 `.` 开头。
 *
 * 比枚举格 [KnowledgeCatalogStore.list] 宽，而且必须宽：切库要把 `active` 写回**每一个**
 * 目录里的 kb.json（含元数据坏掉、名字对不上而列不进清单的那些），否则磁盘上会出现
 * 两个 active=true。枚举判据归枚举格，这里只回答"根下有哪些目录"。
 */
internal fun KnowledgeRepository.visibleKbDirs(): List<File> =
    knowledgeRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()

/**
 * 给"已经持有 [fileMutex]"的内部路径用。
 *
 * 判定与 [transaction] 完全同一把尺；Mutex 非重入，锁内不能再调 [transaction]。
 */
internal fun <T> KnowledgeRepository.transactionUnlocked(kbName: String, block: KnowledgeTx.() -> T): T =
    KnowledgeTx(this, kbName).block()

/** [writeFileUnlocked] 的"有没有真的写"版本：只读时返回 false 而不是静默跳过 */
internal fun KnowledgeRepository.writeFileCheckedUnlocked(kbName: String, relativePath: String, content: String): Boolean {
    if (refusedByReadOnlySchema(kbName, "writeFile", relativePath)) return false
    val file = safeKbFile(kbName, relativePath) ?: return false
    if (!writeFileCheckedUnlocked(file, content)) return false
    scheduleDebouncedBackup()
    return true
}

/**
 * 同一个登记在册的写核，另一支入口：目标文件**已由某道守门解析好**时用这一支
 * （库内路径来自 `safeKbFile`，根级 marker 来自 [safeRootFile]）。
 *
 * 这不是第三条链：落盘只有 [atomicWriteText] 那一处，只读判定也在它里面按归属判。
 * 只读**入口**判定与备份节流留在库内那一支——根级文件不属于任何库，硬给它套一个 kbName
 * 就得编一个不存在的库；而 marker 自己就是备份的产物，写完再排一次备份只会空转（那是改行为）。
 */
internal fun KnowledgeRepository.writeFileCheckedUnlocked(file: File, content: String): Boolean {
    file.parentFile?.mkdirs()
    return atomicWriteText(file, content)
}

/** 根级文件的统一守门：与 [safeKbFile] 同一位所有者、同宽严，判据住在 [KnowledgeDocumentStore.resolveRoot] */
internal fun KnowledgeRepository.safeRootFile(fileName: String): File? = documents.resolveRoot(fileName)

/**
 * 根级 marker 的唯一写路径：根级守门给路径 → 唯一写核落盘。
 * 拒绝必须留痕——一次不出声的 false 与静默跳过没法区分（与 [RepoStorage.atomicWriteAt] 同一口径）。
 */
internal fun KnowledgeRepository.writeRootFileGuarded(fileName: String, content: String): Boolean {
    val file = safeRootFile(fileName)
    if (file == null) {
        com.lovebrain.app.util.L.e("root marker write refused by the guard: $fileName", null)
        return false
    }
    return writeFileCheckedUnlocked(file, content)
}

/** [appendFileUnlocked] 的"有没有真的写"版本 */
internal fun KnowledgeRepository.appendFileCheckedUnlocked(kbName: String, relativePath: String, content: String): Boolean {
    if (refusedByReadOnlySchema(kbName, "appendFile", relativePath)) return false
    val file = safeKbFile(kbName, relativePath) ?: return false
    file.parentFile?.mkdirs()
    val existing = if (file.exists()) file.readText() else ""
    if (!atomicWriteText(file, existing + content)) return false
    scheduleDebouncedBackup()
    return true
}

/**
 * String 入口的统一守门：返回解析后的绝对 File，非法输入返回 null。
 *
 * 判据住在 [KnowledgeDocumentStore.resolve]——公开读、无锁快速读、写、删、版本写
 * 全部经这一个函数，不再有第二条 `File(dir, path)` 捷径。
 */
internal fun KnowledgeRepository.safeKbFile(kbName: String, relativePath: String): File? =
    documents.resolve(kbName, relativePath)

/**
 * 读 kb.json（过 canonical 守门）。库不在、路径非法、JSON 坏掉都给 null。
 *
 * 这一个是 [getTurnCount] 与 [getCurrentStage] 共用的读入口。以前两处各自
 * `File(File(knowledgeRoot, kbName), "kb.json")` + `readText()`，
 * 而同一个文件的**写**侧走的是 `KnowledgeTx.updateMeta`（守门）——
 * 一宽一严正是里 P0 那条读路径点名的形状。
 */
internal fun KnowledgeRepository.readMetaUnlocked(kbName: String): KnowledgeBase? {
    val raw = documents.read(kbName, "kb.json")
    if (raw.isBlank()) return null
    return runCatching { json.decodeFromString<KnowledgeBase>(raw) }.getOrNull()
}

/**
 * updateStage 的无锁核心：调用方必须已持有文件互斥锁。
 *
 * 判"能不能当阶段用"与"落不落盘"分家之后，这里只剩一次转发；
 * 白名单、拒绝时的措辞、以及 `updatedAt` 都归 [KnowledgeProfileStore.setStage]。
 */
internal fun KnowledgeRepository.updateStageUnlocked(kbName: String, stage: String) {
    profile.setStage(kbName, stage)
}

/**
 * b3-8: writeVector 的无锁核心——调用方必须已持有 fileMutex
 *
 * 读侧原来是这个函数体里最后一条裸路径：`File(File(knowledgeRoot, kbName), path).readText()`
 * 之后才 `writeFileUnlocked` 落盘——**读不过守门、写守门**，同一个文件两种宽严。
 * 这条改动带出一个真的行为差别：旧布局（只有 `global/status.md`）的库，
 * `readVector` 一直读得到内容（公开读有回退），而这里读不到于是**静默不写**；
 * 现在两边同一把尺，见 `writingVectorForALegacyLibraryActuallyLands`。
 * 至于"库名带 .. "那一面，这里黑盒量不到（读到的内容不外露，写那一侧本来就拒），
 * 所以 `ProfileReadBoundaryTest` 里那一格在修之前就是绿的，它是防回归而不是证据；
 * 真正的证据是 `StorageBoundaryOwnershipTest` 的计数棘轮：仓库里这种写法还剩几条。
 */
internal fun KnowledgeRepository.writeVectorUnlocked(kbName: String, values: Map<String, Int>) {
    profile.setVector(kbName, values)
}

/**
 * updateWarmthStageLabel 的无锁核心：调用方必须已持有文件互斥锁。
 *
 * 阶段行怎么写（变体认法、「当前状态」节插入、旧值留成"过去曾经是…"）
 * 与 strict 版共用 [KnowledgeProfileStore.rewriteStageLine] 那一份——
 * 拆之前这两处各抄了 30 行，改一处就会漏另一处。
 */
internal fun KnowledgeRepository.updateWarmthStageLabelUnlocked(kbName: String, newStage: String) {
    profile.setWarmthStageLabel(kbName, newStage)
}

internal fun KnowledgeRepository.isoNow(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date())
