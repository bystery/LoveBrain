package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
import com.lovebrain.app.domain.port.KnowledgePort
import com.lovebrain.app.model.PreconditionReason
import com.lovebrain.app.model.ProfileTransactionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 知识库仓储 v3：基于「懂得/此刻/记忆」三层架构。
 *
 * 所有公开方法 suspend + withContext(Dispatchers.IO) 确保主线程无磁盘 IO；
 * read-modify-write 路径经 fileMutex.withLock 保护，防止并发读写冲突。
 */
class KnowledgeRepository(
    private val knowledgeRoot: File,
    private val securePrefs: SecurePrefs,
    private val context: Context,
    private val appScope: CoroutineScope
) : KnowledgePort {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    /** 文件操作互斥锁，防止多协程并发读写同一文件——全仓库唯一的一把 */
    private val fileMutex = Mutex()

    /**
     * schema 探测与旧库迁移。
     *
     * 它不持锁也不碰路径拼接，所有文件动作都经 [RepoStorage] 回到本类，
     * 所以"同一时刻只有一个写者"这条不变量不会因为拆类而散成两把锁。
     */
    private val migrator = KnowledgeMigrator(RepoStorage())

    /** 交给迁移器用的受限视图：只暴露无锁原语，公开 API 仍然只在本类上 */
    private inner class RepoStorage : KbStorageAccess, BackupStorage, CatalogStorage, DocumentStorage,
        MemoryStorage, ProfileStorage, ArchiveStorage {
        override val root: File get() = knowledgeRoot
        override val catalogRoot: File get() = knowledgeRoot

        /** 枚举用的解析走本类那一份 Json 配置——不给第二个类另配一把尺 */
        override fun decodeMeta(text: String): KnowledgeBase? =
            runCatching { json.decodeFromString<KnowledgeBase>(text) }.getOrNull()

        /** 目录被挡下的原因只有仓库知道该不该说、怎么说；观测留在这里，不在策略类里 */
        override fun onMetaRejected(dirName: String, reason: String) {
            com.lovebrain.app.util.L.w("知识库元数据异常已忽略：dir=$dirName reason=$reason")
        }

        /** 文档格的观测出口：同一个日志器，不给第二个类开一条自己的日志通道 */
        override fun note(message: String) {
            com.lovebrain.app.util.L.w(message)
        }

        /** 文档格判断"库还在不在"用无锁那一份——锁由调用方在外面套 */
        override fun kbExists(kbName: String): Boolean = kbExistsUnlocked(kbName)

        /** 记忆格的读也走文档格那道守门：两个格子共用同一个路径边界，不各写一套 */
        override fun read(kbName: String, relativePath: String): String =
            documents.read(kbName, relativePath)

        /** 记忆格只能经这一次写事务落盘：只读判定与"一批要么都写要么都不写"都在这 */
        override fun writeTransaction(kbName: String, block: MemoryTx.() -> Unit) {
            transactionUnlocked(kbName) {
                MemoryTx { relativePath, content -> write(relativePath, content) }.block()
            }
        }

        /**
         * 画像格只能经这一次**元数据**写事务改 kb.json。
         *
         * 与上面那条同一段事务语义（同一次 schema 判定），只是交出去的能力不同：
         * 画像格拿到的是"读改写 kb.json"，不是"想写哪个文件就写哪个文件"。
         * 名字与上面那条不同不是 stylistic：`MemoryTx.() -> Unit` 与 `ProfileTx.() -> Unit`
         * 都擦除成 `Function1`，同名 overload 在 JVM 上是 platform declaration clash——
         * 编译器报这一条要等编译，所以先把名字分开写清，别留给下一个人踩。
         */
        override fun writeMetaTransaction(kbName: String, block: ProfileTx.() -> Unit) {
            transactionUnlocked(kbName) {
                block(ProfileTx { transform -> updateMeta(transform) })
            }
        }

        /** 画像格读 kb.json 用的就是本类那一份解码口径（坏 JSON 当没有），不开第二把尺 */
        override fun metaOf(kbName: String): KnowledgeBase? = readMetaUnlocked(kbName)

        /**
         * 归档格的落盘：四类动作都从同一个事务对象取。
         * 只读判定、锁、路径守门仍在本类那一侧（`transactionUnlocked` + [KnowledgeTx]）。
         *
         * 名字刻意不叫 `writeTransaction`：`MemoryTx.() -> Unit` 与 `ArchiveTx.() -> Unit`
         * 都擦除成 `Function1`，同名就是 platform declaration clash（同一条坑这轮踩到第二次）。
         */
        override fun runTransaction(kbName: String, block: ArchiveTx.() -> Unit) {
            transactionUnlocked(kbName) { ArchiveTxView(this).block() }
        }

        /**
         * 归档格的两把时钟。
         *
         * 分成两个名字不是设计癖好，是既存事实：归档条目标题与 operationId 用 `TimeFmt.now()`
         * （`2026-09-25 10:00`），而 kb.json 的 `updatedAt` 一直是 ISO-8601 带时区。
         * 合成一把就会悄悄换掉某一份落盘格式。
         */
        override fun stamp(): String = com.lovebrain.app.util.TimeFmt.now()
        override fun metaTimestamp(): String = isoNow()

        /** 归档状态的编解码仍用本类那一份 Json 配置 */
        override fun encodeState(state: ArchiveOperationState): String =
            json.encodeToString(ArchiveOperationState.serializer(), state)

        override fun decodeState(text: String): ArchiveOperationState? = runCatching {
            json.decodeFromString<ArchiveOperationState>(text)
        }.getOrNull()

        /** 编解码共用本类那一份 Json 配置：给第二个类另配一把尺就等于换了把判据 */
        override fun decodeCorrections(
            text: String
        ): List<com.lovebrain.app.model.MemoryCorrection>? = runCatching {
            json.decodeFromString<List<com.lovebrain.app.model.MemoryCorrection>>(text)
        }.getOrNull()

        override fun encodeCorrections(
            corrections: List<com.lovebrain.app.model.MemoryCorrection>
        ): String = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                com.lovebrain.app.model.MemoryCorrection.serializer()
            ),
            corrections
        )

        /**
         * 文档格的唯一写出口：回到本类的 [writeFileUnlocked]。
         * 那里带着只读 schema 拒绝与备份节流，所以新版本写也不可能绕开它们。
         */
        override fun writeUnlocked(kbName: String, relativePath: String, content: String) =
            writeFileUnlocked(kbName, relativePath, content)

        override fun atomicWrite(target: File, content: String) {
            // 迁移器只会写"不超纲"的库（判定在 KnowledgeMigrator 里提前 return），
            // 所以这里返回 false 一定是异常状况，必须留下痕迹而不是静默跳过。
            if (!atomicWriteText(target, content)) {
                com.lovebrain.app.util.L.e(
                    "migration write was refused by the schema guard: ${target.name}", null
                )
            }
        }
        /** 备份写 `.last_backup` 走同一道门；"有没有真写进去"原样报回去 */
        override fun guardedWrite(target: File, content: String): Boolean = atomicWriteText(target, content)
        override fun schema(name: String): String = loadSchema(name)
        override suspend fun currentStage(kbName: String): String = getCurrentStage(kbName)
        override suspend fun setStage(kbName: String, stage: String): Unit =
            updateStageUnlocked(kbName, stage)
        override suspend fun setWarmthStageLabel(kbName: String, stage: String): Unit =
            updateWarmthStageLabelUnlocked(kbName, stage)
        override fun timestamp(): String = isoNow()
    }

    /**
     * 自动备份的策略（§5.3 拆出的第一格）：该复制什么、留几份、删哪些全在它里，
     * 写盘仍经 [RepoStorage] 回到本类唯一的 [atomicWriteText]。
     *
     * "什么时候要备份"的节流调度**故意留在本类**：外部 CoroutineScope 只能由协调器持有
     * （SingleOwnerContractTest 那条闸），仓库是这里唯一的启动者，把 launch 一起搬出去就违规。
     */
    private val backup = KnowledgeBackupService(RepoStorage())

    /**
     * 目录枚举（§5.3 catalog 第一刀）：读哪些目录、什么算一个库、按什么排，全在它里。
     *
     * 这格拆出来不是因为仓库大，而是因为这件事**以前在本类里写了两遍**——
     * 公开的 `listAll()` 与无锁的 `listAllUnlocked()` 各一份，且只有一份会说话（记日志）。
     * 现在两个入口共用一个实现。
     */
    private val catalog = KnowledgeCatalogStore(RepoStorage())

    /**
     * 文档的安全路径 + 版本化读写（§5.3 第四格）。
     *
     * 搬出来的理由是一条真实的宽严不一：公开读路径过 canonical 守门，
     * 而无锁快速读 `readFileUnlockedFast` 直接 `File(File(root, kb), path)`——
     * 同一份内容，走哪个入口决定"边界"存不存在（独立复核报告里 P0 那条读路径的末段点名的就是它）。
     * 现在两个入口共用 [KnowledgeDocumentStore.resolve]，守门只有一处。
     *
     * 写仍然只有本类那一条链：文档格想落盘必须经 [RepoStorage.writeUnlocked] 回来，
     * 于是只读 schema 拒绝与备份节流都不会被新版本写绕过。
     */
    private val documents = KnowledgeDocumentStore(RepoStorage())

    /**
     * 记忆纠正与库级 revision（§5.3 第五格）。
     *
     * 拆它的理由是一条**单调性**承诺以前散在两处：saveCorrection 与 undoCorrection
     * 各写一遍"递增并把 revision 落盘"，两处都得自己记得"纠正文件写成了才写 revision"。
     * 现在这条顺序只有一个所有者（KnowledgeMemoryStore.persist）。
     * 锁、路径守门、落盘仍然在本类：记忆格只拿到 read / writeTransaction / 编解码 /
     * kbExists / timestamp 五样能力。
     */
    private val memory = KnowledgeMemoryStore(RepoStorage())

    /**
     * 画像正文、内容修订、阶段、状态向量与温度文件里的阶段标签（§5.3 第六格）。
     *
     * 拆它的理由有两条，都不是"仓库太大"：
     *  ① 同一份 warmth 内容以前有三种宽严——`readVector` 看得见旧布局回退、
     *     `writeVectorUnlocked` 看不见于是静默不写、阶段标签那条又走公开读；
     *  ② 九阶段白名单"拒绝时说不说、怎么说"在四处各写一遍，标签行改写两处各抄一份。
     * 现在向量维度表、阶段行正则、修订号输入清单都只有一个所有者，
     * `applyProfileUpdateAtomically` 的 strict 版也回来共用同一份改写规则。
     * 锁、路径守门、落盘、kb.json 的解码仍然在本类。
     */
    private val profile = KnowledgeProfileStore(RepoStorage())

    /**
     * 话题归档的四步状态机（§5.3 第七格）。
     *
     * `rotateTopic` 原先是仓库里一段 80 行的方法体：步骤判定、归档条目格式、状态文件读写删、
     * kb.json 计数全混在一起，三件事没有名字。现在这四条规则在 [KnowledgeArchiveService]，
     * 锁与事务仍在本类——归档格拿到的是七样能力（note / 两把时钟 / 守门读 / 一次写事务 /
     * 状态编解码），没有 Mutex、没有 File、没有第二条落盘链。
     * 它比画像格宽一样是因为它确实管四步与一个状态文件；再宽就要开始拆"初始化"了。
     */
    private val archive = KnowledgeArchiveService(RepoStorage())

    /** 备份节流：记录最后一次写入时间，debounce 5s 后触发增量备份 */
    private val backupDebounceMs = 5_000L
    private val lastWriteTimestamp = AtomicLong(0L)
    private var backupDebounceJob: Job? = null

    init {
        knowledgeRoot.mkdirs()
        // 启动时自动备份（使用 applicationScope 替代 GlobalScope，生命周期可管理）
        //  所有 launch 必须包 SupervisorJob + ExceptionHandler
        // 备份经 fileMutex 序列化，与 delete 互斥防竞态
        appScope.launch(Dispatchers.IO + SupervisorJob()) {
            try {
                backupIfNeeded()
            } catch (e: CancellationException) {
                // 作用域被取消不是"备份失败"，记成失败会丢真相
                throw e
            } catch (e: Exception) {
                com.lovebrain.app.util.L.e("backup init failed", e)
            }
        }
    }

    /**
     * 写入后触发节流备份：每次写入操作调用此方法，5s 内无新写入则触发一次增量备份。
     * 调研依据：kotlinx.coroutines debounce 模式 + Android 文件 I/O 最佳实践。
     */
    /**
     * 写入后触发节流备份：5s 内没有新写入才真的备份一次。
     *
     * 任务体里再核一次时间戳——`delay` 期间可能又来了新写入，那一班就该让给下一班。
     */
    private fun scheduleDebouncedBackup() {
        lastWriteTimestamp.set(System.currentTimeMillis())
        backupDebounceJob?.cancel()
        backupDebounceJob = appScope.launch(Dispatchers.IO + SupervisorJob()) {
            delay(backupDebounceMs)
            // 再次确认：delay 期间没有新的写入（时间戳没变）
            if (System.currentTimeMillis() - lastWriteTimestamp.get() >= backupDebounceMs - 100L) {
                try {
                    backupIfNeeded()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.lovebrain.app.util.L.e("debounce backup failed", e)
                }
            }
        }
    }

    // ═══════════ 自动备份：调度在本类，策略在 KnowledgeBackupService ═══════════

    /**
     * 序列化备份过程——与 delete 共用 fileMutex，防止并发竞态。
     * 备份最多 12 小时一次、知识库主要是文本，短暂锁住文件更新的成本可接受。
     */
    private suspend fun backupIfNeeded() {
        fileMutex.withLock {
            backup.backupIfNeededUnlocked()
        }
    }

    // ═══════════ 原子写入工具 ═══════════

    /**
     * 唯一落盘出口（2026-09-24 独立复核的写边界要求）。
     *
     * 独立复核报告 §3.1 的原话是：注释写着"所有写路径最终落到 writeFileUnlocked"，
     * 但十几条路径直接 `atomicWriteText` 或自己 `File(dir, …)`，于是 v4 库在 v3 App 里
     * 虽然显示 read-only，元数据和纠正文件照样能被改写。
     *
     * 所以拒绝判定放在**这里**，而且按**文件属于哪个库**判，不按"调用方有没有传 kbName"判：
     * Repository 里任何一条写链——包括以后新加的、忘记走 [transaction] 的——
     * 都必须先经过这道门。返回 false 表示什么都没写。
     *
     * 真正的字节操作在 [rawAtomicWriteText]，它只被本函数调用。
     */
    private fun atomicWriteText(file: File, content: String): Boolean {
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
    private fun kbOwning(file: File): String? {
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
     * 仅供 [atomicWriteText] 调用——需要写文件请走 [transaction] / [KnowledgeTx]。
     */
    private fun rawAtomicWriteText(file: File, content: String) {
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

    /** 判断知识库是否真实存在（目录存在 + kb.json 存在）。调用方必须已持有文件互斥锁或处于单线程路径 */
    private fun kbExistsUnlocked(kbName: String): Boolean {
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
    private fun refusedByReadOnlySchema(kbName: String, op: String, relativePath: String): Boolean {
        if (!migrator.isReadOnly(kbName)) return false
        com.lovebrain.app.util.L.w(
            "$op refused on $kbName/$relativePath — schema newer than this build, library is read-only"
        )
        return true
    }

    /** 该库当前是否可写（schema 过新时为 false） */
    fun isWritable(kbName: String): Boolean = !migrator.isReadOnly(kbName)

    /** 锁区内写入核心：不抢锁。调用方必须已持有文件互斥锁（Mutex 非重入，锁内再抢=永久挂起） */
    private fun writeFileUnlocked(kbName: String, relativePath: String, content: String) {
        if (refusedByReadOnlySchema(kbName, "writeFile", relativePath)) return
        val file = safeKbFile(kbName, relativePath) ?: return
        file.parentFile?.mkdirs()
        atomicWriteText(file, content)
        scheduleDebouncedBackup()
    }

    /** 锁区内追加核心：不抢锁。调用方必须已持有文件互斥锁 */
    private fun appendFileUnlocked(kbName: String, relativePath: String, content: String) {
        if (refusedByReadOnlySchema(kbName, "appendFile", relativePath)) return
        val file = safeKbFile(kbName, relativePath) ?: return
        file.parentFile?.mkdirs()
        val existing = if (file.exists()) file.readText() else ""
        atomicWriteText(file, existing + content)
        scheduleDebouncedBackup()
    }

    // ═══════════ 唯一写边界（独立复核 2026-09-24）═══════════

    /** 一次 [transaction] 的结果。三个分支各自对应"有没有写过字节"。 */
    sealed interface WriteResult<out T> {
        /** 事务里的写都真的落盘了 */
        data class Written<out T>(val value: T) : WriteResult<T>

        /** 库的 schema 比本 App 还新：整段事务一个字节都没写 */
        data object RefusedNewerSchema : WriteResult<Nothing>

        /** 库不存在或已被删除：同样什么都没写 */
        data object MissingLibrary : WriteResult<Nothing>
    }

    /**
     * 一个知识库的写事务句柄——Repository 里唯一被允许"拿到路径 + 写文件"的入口。
     *
     * 为什么要有它（2026-09-24 独立复核）：以前每个新方法都可能顺手
     * `atomicWriteText(File(dir, "kb.json"), …)`，于是"schema 过新即只读"要靠
     * 十几处 if 各自记得写。现在 mutation 只能从这个对象取得安全路径与原子写能力，
     * 判定集中在 [transaction] 入口 + [atomicWriteText] 出口两处。
     *
     * 构造点是 internal：外部拿不到一个"没经过只读判定"的 Tx。
     */
    inner class KnowledgeTx internal constructor(val kbName: String) {

        // 方法名刻意带 At 后缀：取消审计按**名字**判断块体里有没有挂起调用，
        // 而仓库里已经有 `suspend fun read` / 大量 delete 语义，叫裸名会被虚报成
        // "catch 吞掉挂起调用"。名字撞车会让那条门禁变成噪声，噪声一旦被接受就等于没有门禁。

        /** 相对路径 → 安全绝对路径；越界或非法输入返回 null（绝不落到库目录之外） */
        fun pathOf(relativePath: String): File? = safeKbFile(kbName, relativePath)

        /** 读；越界、非法或不存在都得到空串 */
        fun readTextAt(relativePath: String): String {
            val f = pathOf(relativePath) ?: return ""
            return if (f.exists()) runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("") else ""
        }

        /** 原子写。false = 被只读保护或路径非法挡下，一个字节都没落。 */
        fun write(relativePath: String, content: String): Boolean =
            writeFileCheckedUnlocked(kbName, relativePath, content)

        /** 原子追加，语义同 [write] */
        fun append(relativePath: String, content: String): Boolean =
            appendFileCheckedUnlocked(kbName, relativePath, content)

        /** 删除。false = 没删（被挡、越界或本来就不存在）。 */
        fun deleteAt(relativePath: String): Boolean {
            if (migrator.isReadOnly(kbName)) return false
            val f = pathOf(relativePath) ?: return false
            return f.exists() && f.delete()
        }

        /** 读改写 kb.json；库不存在 / 只读 / JSON 坏掉都返回 false 且不写 */
        fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean {
            if (migrator.isReadOnly(kbName)) return false
            val raw = readTextAt("kb.json")
            if (raw.isBlank()) return false
            val kb = runCatching { json.decodeFromString<KnowledgeBase>(raw) }.getOrNull() ?: return false
            return write("kb.json", json.encodeToString(KnowledgeBase.serializer(), transform(kb)))
        }
    }

    /**
     * 公开写入口：一次事务、一个库、一把锁。
     *
     * 只读判定在**入口**做一次，所以 block 里不需要再写任何 schema if；
     * block 返回即事务结束。失败原因用类型给出，不靠调用方猜 null。
     */
    suspend fun <T> transaction(
        kb: com.lovebrain.app.model.KbName,
        block: KnowledgeTx.() -> T
    ): WriteResult<T> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kb.value)) return@withLock WriteResult.MissingLibrary
            if (migrator.isReadOnly(kb.value)) return@withLock WriteResult.RefusedNewerSchema
            WriteResult.Written(KnowledgeTx(kb.value).block())
        }
    }

    /**
     * 把 [KnowledgeTx] 收窄成归档格能看见的四件事。
     *
     * 直接把 `KnowledgeTx` 交出去就等于把 `pathOf`（能拿 File）也交出去了——那正是复核报告
     * 说的"第二个所有者"的起点。适配层薄，但它让"格子里没有 File"这件事能被静态检查。
     */
    private class ArchiveTxView(private val tx: KnowledgeTx) : ArchiveTx {
        override fun write(relativePath: String, content: String): Boolean = tx.write(relativePath, content)
        override fun append(relativePath: String, content: String): Boolean = tx.append(relativePath, content)
        override fun delete(relativePath: String): Boolean = tx.deleteAt(relativePath)
        override fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean =
            tx.updateMeta(transform)
    }

    /**
     * 给"已经持有 [fileMutex]"的内部路径用。
     *
     * 判定与 [transaction] 完全同一把尺；Mutex 非重入，锁内不能再调 [transaction]。
     */
    private fun <T> transactionUnlocked(kbName: String, block: KnowledgeTx.() -> T): T =
        KnowledgeTx(kbName).block()

    /** [writeFileUnlocked] 的"有没有真的写"版本：只读时返回 false 而不是静默跳过 */
    private fun writeFileCheckedUnlocked(kbName: String, relativePath: String, content: String): Boolean {
        if (refusedByReadOnlySchema(kbName, "writeFile", relativePath)) return false
        val file = safeKbFile(kbName, relativePath) ?: return false
        file.parentFile?.mkdirs()
        if (!atomicWriteText(file, content)) return false
        scheduleDebouncedBackup()
        return true
    }

    /** [appendFileUnlocked] 的"有没有真的写"版本 */
    private fun appendFileCheckedUnlocked(kbName: String, relativePath: String, content: String): Boolean {
        if (refusedByReadOnlySchema(kbName, "appendFile", relativePath)) return false
        val file = safeKbFile(kbName, relativePath) ?: return false
        file.parentFile?.mkdirs()
        val existing = if (file.exists()) file.readText() else ""
        if (!atomicWriteText(file, existing + content)) return false
        scheduleDebouncedBackup()
        return true
    }

    // ═══════════ schema 版本与迁移（实现见 KnowledgeMigrator）═══════════

    /** 该库是否因 schema 过新而进入只读保护 */
    fun isSchemaReadOnly(kbName: String): Boolean = migrator.isReadOnly(kbName)

    /**
     * 库当前的 schema 版本（对外只读，供升级断言与诊断使用）。
     * 读不到 .schema_version 时按 legacy marker 推断，与迁移判定同一把尺。
     */
    suspend fun schemaVersion(kbName: String): Int = withContext(Dispatchers.IO) {
        migrator.detectVersion(kbName)
    }

    /** 进入知识库前补齐/迁移结构；整段在文件互斥锁内执行 */
    suspend fun migrateIfNeeded(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock { migrator.migrateUnlocked(kbName) }
    }

    // ═══════════ 默认知识库初始化 ═══════════

    /**
     * 确保应用至少有一个合法知识库。应用初始化唯一入口。
     *
     * 规则：
     * 1. 有库沿用原激活项；不创建新库
     * 2. 首次无库创建恰好一个"默认知识库"，阶段"待确定"，画像空
     * 3. 用户主动删除最后一个库后不重复创建（通过 .kb_initialized 标记区分）
     * 4. 导入优先：有导入的库存在时不创建默认库
     * 5. 中断恢复优先补齐缺失文件，不 deleteRecursively 后重建
     * 6. 全部写入成功才标完成
     * 7. 无网络、无模型配置也成功
     */
    suspend fun ensureInitialKnowledgeBase() = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val initMarker = File(knowledgeRoot, ".kb_initialized")
            val existingKbs = listAllUnlocked()

            if (existingKbs.isNotEmpty()) {
                // R11: 已有库——先完成旧格式迁移，再补缺失文件。
                // 旧顺序：先 ensureKbFilesComplete 创建空文件，再 migrateIfNeeded——
                // 空文件遮住迁移（migrateIfNeeded 以 understand 已存在为提前返回条件）。
                existingKbs.forEach { kb -> 
                    migrator.migrateUnlocked(kb.name)
                    ensureKbFilesCompleteUnlocked(kb.name)
                }
                initMarker.writeText("done")
                return@withLock
            }

            // 无库——区分"首次启动"和"用户删除最后一个库后"
            if (initMarker.exists()) {
                // 用户已初始化过，之后删除了所有库——不重复创建
                return@withLock
            }

            // 首次启动——创建默认知识库
            val defaultName = "default"
            val defaultDir = File(knowledgeRoot, defaultName)
            defaultDir.mkdirs()
            File(defaultDir, "understand").mkdirs()
            File(defaultDir, "moment").mkdirs()
            File(defaultDir, "memory").mkdirs()

            val now = isoNow()
            val kb = KnowledgeBase(
                name = defaultName,
                displayName = "默认知识库",
                updatedAt = now,
                stage = "待确定",
                turnCount = 0,
                topicCount = 0,
                active = true
            )
            atomicWriteText(File(defaultDir, "kb.json"), json.encodeToString(KnowledgeBase.serializer(), kb))

            // 画像默认真实空内容（非 schema 模板占位文字）
            atomicWriteText(File(defaultDir, "understand/me.md"), "")
            atomicWriteText(File(defaultDir, "understand/her.md"), "")
            atomicWriteText(File(defaultDir, "understand/warmth.md"), "")
            // 此刻层：topic 有初始行，其余空
            atomicWriteText(
                File(defaultDir, "moment/topic.md"),
                KbTextOps.topicLine(com.lovebrain.app.util.TimeFmt.now(), KbTextOps.TOPIC_INITIAL_LABEL)
            )
            atomicWriteText(File(defaultDir, "moment/recent.md"), "")
            atomicWriteText(File(defaultDir, "moment/scene.md"), "")
            atomicWriteText(File(defaultDir, "moment/plan.md"), loadSchema("plan"))
            // 记忆层：全部空
            atomicWriteText(File(defaultDir, "memory/lessons.md"), "")
            atomicWriteText(File(defaultDir, "memory/raw_chat.md"), "")
            atomicWriteText(File(defaultDir, "memory/raw_topic.md"), "")
            atomicWriteText(File(defaultDir, "memory/raw_scene.md"), "")
            atomicWriteText(File(defaultDir, "memory/counseling_log.md"), "")

            // 全部写入成功才标完成
            securePrefs.activeKbName = defaultName
            initMarker.writeText("done")
            scheduleDebouncedBackup()
        }
    }

    /**
     * 无锁版 listAll（调用方持有 fileMutex）。
     *
     * 以前这里是**第二份**"扫目录 + 校验 name + 排序"的实现，和公开的 [listAll] 各写一遍，
     * 差别只在这一份被挡下时什么都不说。现在两条路径共用 [KnowledgeCatalogStore]。
     */
    private fun listAllUnlocked(): List<KnowledgeBase> = catalog.list()

    /**
     * 检查知识库文件是否完整，补齐缺失文件（中断恢复）。
     * 不 deleteRecursively，只补缺失。调用方持有 fileMutex。
     */
    private fun ensureKbFilesCompleteUnlocked(kbName: String) {
        val dir = File(knowledgeRoot, kbName)
        if (!dir.isDirectory) return

        File(dir, "understand").mkdirs()
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()

        // 补齐缺失的必需文件（不覆盖已有内容）
        val requiredFiles = listOf(
            "understand/me.md" to "",
            "understand/her.md" to "",
            "understand/warmth.md" to "",
            "moment/recent.md" to "",
            "moment/scene.md" to "",
            "moment/plan.md" to loadSchema("plan"),
            "memory/lessons.md" to "",
            "memory/raw_chat.md" to "",
            "memory/raw_topic.md" to "",
            "memory/raw_scene.md" to "",
            "memory/counseling_log.md" to ""
        )

        requiredFiles.forEach { (path, defaultContent) ->
            val file = File(dir, path)
            if (!file.exists()) {
                atomicWriteText(file, defaultContent)
            }
        }

        // topic.md 特殊处理：不存在时写入初始行
        val topicFile = File(dir, "moment/topic.md")
        if (!topicFile.exists()) {
            atomicWriteText(
                topicFile,
                KbTextOps.topicLine(com.lovebrain.app.util.TimeFmt.now(), KbTextOps.TOPIC_INITIAL_LABEL)
            )
        }
    }

    // ═══════════ 公开 API ═══════════

    /**
     * 列出所有知识库。判定（隐藏目录、name 与目录名等值、坏元数据丢弃、按 updatedAt 倒序）
     * 在 [KnowledgeCatalogStore]，与无锁路径同一条尺。
     */
    suspend fun listAll(): List<KnowledgeBase> = withContext(Dispatchers.IO) {
        catalog.list()
    }

    override suspend fun getActive(): KnowledgeBase? = withContext(Dispatchers.IO) {
        val all = listAll()
        val activeName = securePrefs.activeKbName
        all.firstOrNull { it.name == activeName && it.active }
            ?: all.firstOrNull { it.name == activeName }
            ?: all.firstOrNull()
    }

    suspend fun setActive(name: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            setActiveUnlocked(name)
        }
    }

    /**
     * setActive 的无锁核心：调用方必须已持有文件互斥锁（Mutex 非重入，锁内再抢=永久挂起）
     *
     * 写边界：切库要重写**每个**库的 kb.json。旧实现直接 `atomicWriteText(metaFile, …)`，
     * 于是 schema 过新的只读库也会在这里被改掉 active 字段。现在逐库走 [KnowledgeTx]。
     */
    private fun setActiveUnlocked(name: String) {
        securePrefs.activeKbName = name
        knowledgeRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.forEach { dir ->
                transactionUnlocked(dir.name) {
                    updateMeta { kb -> kb.copy(active = kb.name == name) }
                }
            }
    }

    suspend fun create(name: String, displayName: String): KnowledgeBase = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val safeName = name.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\\u4e00-\\u9fa5_-]"), "")
            require(safeName.isNotEmpty()) { "知识库名不能为空" }
            val dir = File(knowledgeRoot, safeName)
            require(!dir.exists()) { "知识库 '$safeName' 已存在" }

            File(dir, "understand").mkdirs()
            File(dir, "moment").mkdirs()
            File(dir, "memory").mkdirs()

            val now = isoNow()
            val kb = KnowledgeBase(
                name = safeName,
                displayName = displayName.ifBlank { safeName },
                updatedAt = now,
                stage = "待确定",
                turnCount = 0,
                active = listAll().isEmpty()
            )
            atomicWriteText(File(dir, "kb.json"), json.encodeToString(KnowledgeBase.serializer(), kb))

            // 全部文件从 assets/schema/ 加载（schema 是知识库结构的唯一来源）
            // 懂得层（慢变量画像）
            atomicWriteText(File(dir, "understand/me.md"), loadSchema("me"))
            atomicWriteText(File(dir, "understand/her.md"), loadSchema("her"))
            atomicWriteText(File(dir, "understand/warmth.md"), loadSchema("warmth"))
            // 此刻层（快变量上下文）
            atomicWriteText(File(dir, "moment/topic.md"), loadSchema("topic"))
            atomicWriteText(File(dir, "moment/recent.md"), loadSchema("recent"))
            atomicWriteText(File(dir, "moment/scene.md"), loadSchema("scene"))
            atomicWriteText(File(dir, "moment/plan.md"), loadSchema("plan"))
            // 记忆层（长期归档）
            atomicWriteText(File(dir, "memory/lessons.md"), loadSchema("lessons"))
            atomicWriteText(File(dir, "memory/raw_chat.md"), loadSchema("raw_chat"))
            atomicWriteText(File(dir, "memory/raw_topic.md"), loadSchema("raw_topic"))
            atomicWriteText(File(dir, "memory/raw_scene.md"), loadSchema("raw_scene"))
            atomicWriteText(File(dir, "memory/counseling_log.md"), loadSchema("counseling_log"))

            if (kb.active) securePrefs.activeKbName = safeName
            scheduleDebouncedBackup()
            kb
        }
    }

    // ═══════════ 持续意图（每 KB 一份，moment/intent.json） ═══════════

    /**
     * 读取持续意图配置。
     *
     * §5.3 画像格量读路径时发现的第三处裸路径：以前这里 `File(File(knowledgeRoot, kbName),
     * "moment/intent.json")` 自己拼，库名带 `..` 就能把库外那份意图读回调用方手里。
     * 现在与公开读共用 [KnowledgeDocumentStore.read] 那一道门。
     */
    suspend fun readIntent(kbName: String): IntentConfig = withContext(Dispatchers.IO) {
        decodeIntent(documents.read(kbName, "moment/intent.json"))
    }

    /** 保存持续意图配置。每次保存 revision+1，用于生成时冻结快照识别旧请求。
     *  支持有效期和完成状态。 */
    suspend fun saveIntent(
        kbName: String,
        text: String,
        enabled: Boolean,
        expiry: com.lovebrain.app.model.IntentExpiry = com.lovebrain.app.model.IntentExpiry.UNTIL_DONE,
        expiryDate: String = "",
        status: com.lovebrain.app.model.IntentStatus = com.lovebrain.app.model.IntentStatus.ACTIVE
    ): IntentConfig = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val current = readIntentUnlocked(kbName)
            val updated = IntentConfig(
                text = text,
                enabled = enabled,
                revision = current.revision + 1,
                expiry = expiry,
                expiryDate = expiryDate,
                status = status
            )
            var persisted = false
            transactionUnlocked(kbName) {
                persisted = write("moment/intent.json", json.encodeToString(IntentConfig.serializer(), updated))
            }
            if (!persisted) {
                com.lovebrain.app.util.L.w("saveIntent 未落盘：$kbName 只读（schema 过新）或目录不可用")
            }
            updated
        }
    }

    /**
     * 无锁版读取（调用方持有 fileMutex）。
     *
     * 与 [readIntent] 同一个所有者、同一道门：以前这两处各写一遍裸路径，
     * 而 `saveIntent` 恰好走的是这一条——它把外面读到的 revision 加一再**返回**给调用方，
     * 于是"库外的数"会带着本库的身份进入生成快照比对。
     */
    private fun readIntentUnlocked(kbName: String): IntentConfig =
        decodeIntent(documents.read(kbName, "moment/intent.json"))

    /** 意图文件的解码口径：缺失、空、JSON 坏掉都回到"当前没有意图" */
    private fun decodeIntent(text: String): IntentConfig {
        if (text.isBlank()) return IntentConfig()
        return runCatching {
            json.decodeFromString<IntentConfig>(text)
        }.getOrDefault(IntentConfig())
    }

    // ═══════════ 记忆纠正（每 KB 一份，memory/corrections.json） ═══════════

    /** 读取纠正记录列表。返回 memoryId → correction 映射。 */
    suspend fun readCorrections(kbName: String): Map<String, com.lovebrain.app.model.MemoryCorrection> =
        withContext(Dispatchers.IO) { memory.corrections(kbName) }

    /** 保存一条纠正记录。revision 单调递增（库级），不会因撤销倒退。
     * R07: 不再使用剩余记录的 max 推算 revision（撤销删除后可能倒退）。
     * 改为读取库级持久化 revision 标记，每次纠正/撤销均递增。 */
    /**
     * 保存一条纠正（§5.3 第五格之后只剩锁与转发）。
     *
     * revision 单调、"纠正文件写成了才允许写 revision"这条顺序都在 [KnowledgeMemoryStore.save]；
     * 被只读保护挡下时如实返回 false，不报"成功却没落盘"。
     */
    suspend fun saveCorrection(
        kbName: String,
        memoryId: String,
        action: com.lovebrain.app.model.CorrectionAction,
        replacementText: String = "",
        targetKbId: String = "",
        muteDuration: com.lovebrain.app.model.MuteDuration = com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            memory.save(kbName, memoryId, action, replacementText, targetKbId, muteDuration)
        }
    }

    /** 撤销纠正 — 删除指定 memoryId 的纠正记录。
     * R07: 撤销也递增库级 revision，保证单调性。 */
    /**
     * 撤销一条纠正。撤销同样递增 revision（0→1→0 是最容易被破的单调性），
     * 规则住在 [KnowledgeMemoryStore.undo] 里，这里只保留锁与转发。
     */
    suspend fun undoCorrection(kbName: String, memoryId: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock { memory.undo(kbName, memoryId) }
    }

    /** 获取纠正记录的全局 revision（用于后台防护）。
     * R07: 读取库级持久化 revision（单调递增），不依赖剩余记录 max。
     * b3-8: 加锁读取，保证一致性（原先无锁读可能读到半写状态） */
    override suspend fun getCorrectionsRevision(kbName: String): Int = withContext(Dispatchers.IO) {
        fileMutex.withLock { memory.revisionOf(kbName) }
    }

    /** R07: 原子读取纠正记录和 revision——用于生成准备阶段一次性快照。
     * 消除读取纠正和读取 revision 之间的竞态窗口。 */
    suspend fun readCorrectionsAndRevision(kbName: String): Pair<Map<String, com.lovebrain.app.model.MemoryCorrection>, Int> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            memory.correctionSnapshot(kbName)
        }
    }

    /** b3-8: 带修订版本条件校验的原子追加——在锁内一次性完成 revision 检查和文件写入，
     * 消除 KnowledgeTriggerCoordinator 中先检查后写入的竞态窗口。
     * @return true = 写入成功，false = revision 已变或 KB 不存在 */
    override suspend fun appendFileWithRevisionCheck(
        kbName: String,
        relativePath: String,
        content: String,
        expectedRevision: Int
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("appendFileWithRevisionCheck skipped: kb no longer exists")
                return@withLock false
            }
            val currentRevision = readMemoryRevisionUnlocked(kbName)
            if (currentRevision != expectedRevision) {
                com.lovebrain.app.util.L.w("appendFileWithRevisionCheck skipped: revision changed (expected=$expectedRevision, current=$currentRevision)")
                return@withLock false
            }
            appendFileUnlocked(kbName, relativePath, content)
            true
        }
    }

    /** b3-8: 带修订版本条件校验的原子写入——在锁内一次性完成 revision 检查和文件写入。
     * @return true = 写入成功，false = revision 已变或 KB 不存在 */
    suspend fun writeFileWithRevisionCheck(
        kbName: String,
        relativePath: String,
        content: String,
        expectedRevision: Int
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("writeFileWithRevisionCheck skipped: kb no longer exists")
                return@withLock false
            }
            val currentRevision = readMemoryRevisionUnlocked(kbName)
            if (currentRevision != expectedRevision) {
                com.lovebrain.app.util.L.w("writeFileWithRevisionCheck skipped: revision changed (expected=$expectedRevision, current=$currentRevision)")
                return@withLock false
            }
            writeFileUnlocked(kbName, relativePath, content)
            true
        }
    }

    /** b3-8: 带修订版本条件校验的向量写入——在锁内一次性完成 revision 检查和向量写入。
     * @return true = 写入成功，false = revision 已变或 KB 不存在 */
    override suspend fun writeVectorWithRevisionCheck(
        kbName: String,
        values: Map<String, Int>,
        expectedRevision: Int
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("writeVectorWithRevisionCheck skipped: kb no longer exists")
                return@withLock false
            }
            val currentRevision = readMemoryRevisionUnlocked(kbName)
            if (currentRevision != expectedRevision) {
                com.lovebrain.app.util.L.w("writeVectorWithRevisionCheck skipped: revision changed (expected=$expectedRevision, current=$currentRevision)")
                return@withLock false
            }
            writeVectorUnlocked(kbName, values)
            true
        }
    }

    /** R07: 读取库级 memory revision（无锁，调用方持有 fileMutex）
     *
     * 读走 [KnowledgeDocumentStore.read]：以前这里 `File(File(knowledgeRoot, kbName), MEMORY_REVISION_FILE)`
     * 自己拼路径，等于绕开 canonical 守门。文件不存在时读得到空串 → revision 记 0，语义不变。
     */
    private fun readMemoryRevisionUnlocked(kbName: String): Int = memory.revisionOf(kbName)

    /** 删除知识库（/：物理删除——UI 已有确认步骤，不再进 .trash 永久残留隐私数据）
     *  delete 成功后同时删除该 KB 的全部 backup，防止私密副本残留 */
    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val dir = File(knowledgeRoot, name)
            //  canonical 纵深守卫——删除目标必须落在 knowledge/ 树内（与 unzipToKnowledge entry 防护同写法）
            val canonicalDirPath = dir.canonicalPath
            val canonicalRootPath = knowledgeRoot.canonicalPath
            if (!canonicalDirPath.startsWith(canonicalRootPath + File.separator)) return@withLock false
            if (!dir.exists()) return@withLock false
            val ok = dir.deleteRecursively()
            // 清理旧版本遗留的 .trash（若存在），一次性腾空
            File(knowledgeRoot, ".trash").takeIf { it.exists() }?.deleteRecursively()
            // 正式目录删除成功后才删 backup，防止删 backup 后正式目录删失败导致备份丢失
            if (ok) {
                backup.deleteBackupsFor(name)
            }
            if (ok && securePrefs.activeKbName == name) {
                val next = knowledgeRoot.listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.maxByOrNull { it.lastModified() }
                if (next != null) {
                    setActiveUnlocked(next.name)
                } else {
                    securePrefs.activeKbName = ""
                }
            }
            ok
        }
    }

    /**
     * 读取文件（自动兼容新旧路径）。
     *
     * 实现已归 [KnowledgeDocumentStore]；这里只剩转发，公开 API 不变。
     */
    override suspend fun readFile(kbName: String, relativePath: String): String =
        withContext(Dispatchers.IO) { documents.read(kbName, relativePath) }

    // ═══════════ canonical 路径边界 ═══════════

    /** 把裸字符串库名校验成 [KbName]（判据见 [KnowledgeDocumentStore.toKbName]） */
    fun toKbName(raw: String): com.lovebrain.app.model.KbName? = documents.toKbName(raw)

    /** 把裸字符串相对路径校验成 [KbRelativePath] */
    fun toKbPath(raw: String): com.lovebrain.app.model.KbRelativePath? = documents.toKbPath(raw)

    /**
     * String 入口的统一守门：返回解析后的绝对 File，非法输入返回 null。
     *
     * 判据住在 [KnowledgeDocumentStore.resolve]——公开读、无锁快速读、写、删、版本写
     * 全部经这一个函数，不再有第二条 `File(dir, path)` 捷径。
     */
    private fun safeKbFile(kbName: String, relativePath: String): File? =
        documents.resolve(kbName, relativePath)

    /** 线程安全的文件追加（fileMutex 锁 + I/O 线程；A2-5 合并原 appendFileSafe）
     *  目标 KB 已删除时 no-op，不自动 mkdirs 复活 */
    override suspend fun appendFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("appendFile skipped: kb no longer exists")
                return@withLock
            }
            appendFileUnlocked(kbName, relativePath, content)
        }
    }

    /** 线程安全的文件删除（fileMutex 锁 + I/O 线程） */
    override suspend fun deleteFile(kbName: String, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) return@withLock false
            if (refusedByReadOnlySchema(kbName, "deleteFile", relativePath)) return@withLock false
            val file = safeKbFile(kbName, relativePath) ?: return@withLock false
            if (file.exists()) file.delete() else false
        }
    }

    /** 线程安全的文件写入（fileMutex 锁 + I/O 线程；A2-5 合并原 writeFileSafe）
     *  目标 KB 已删除时 no-op，不自动 mkdirs 复活 */
    override suspend fun writeFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("writeFile skipped: kb no longer exists")
                return@withLock
            }
            writeFileUnlocked(kbName, relativePath, content)
        }
    }

    /**
     * 带版本校验的文件写入——防止编辑覆盖后台新增。
     * 调用方在读取文件时获得 [expectedVersion]（文件内容的 SHA-256），
     * 写入时校验磁盘上的文件是否仍为该版本。
     * 如果文件已被修改（后台追加等），拒绝写入并返回 null，调用方保留草稿。
     * 成功写入后返回新内容的 SHA-256 作为新版本号，调用方应更新本地版本快照。
     *
     * @return 新版本号（SHA-256）=写入成功，null=版本冲突或 KB 不存在
     */
    suspend fun writeFileWithVersion(
        kbName: String, relativePath: String, content: String, expectedVersion: String
    ): String? = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            documents.writeWithVersion(kbName, relativePath, content, expectedVersion)
        }
    }

    /**
     * 读取文件并返回内容 + 版本号（SHA-256）。
     * 调用方持有版本号，写入时传给 [writeFileWithVersion] 做冲突检测。
     */
    suspend fun readFileWithVersion(kbName: String, relativePath: String): Pair<String, String> =
        withContext(Dispatchers.IO) { documents.readWithVersion(kbName, relativePath) }

    /** 对外暴露的内容哈希——供 KbEdit 无版本校验路径生成新版本号 */
    fun hashContent(text: String): String = documents.hashContent(text)

    /**  目标 KB 已删除时 no-op */
    suspend fun incrementTurnCount(kbName: String) = incrementTurnCountBy(kbName, 1)

    /**
     * 按 WAL 事件中记录的增量推进轮次计数。
     *
     * 恢复路径必须读事件里的 `turnCountIncrement` 值，
     * 而不是硬编码 +1——否则一次记录 2 轮的事务恢复后只补 1。
     */
    override suspend fun incrementTurnCountBy(kbName: String, delta: Int): Unit = withContext(Dispatchers.IO) {
        if (delta <= 0) return@withContext
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("incrementTurnCount skipped: kb no longer exists")
                return@withLock
            }
            transactionUnlocked(kbName) {
                updateMeta { kb -> kb.copy(turnCount = kb.turnCount + delta, updatedAt = isoNow()) }
            }
        }
    }

    /**
     * 读 kb.json（过 canonical 守门）。库不在、路径非法、JSON 坏掉都给 null。
     *
     * 这一个是 [getTurnCount] 与 [getCurrentStage] 共用的读入口。以前两处各自
     * `File(File(knowledgeRoot, kbName), "kb.json")` + `readText()`，
     * 而同一个文件的**写**侧走的是 `KnowledgeTx.updateMeta`（守门）——
     * 一宽一严正是复核报告里 P0 那条读路径点名的形状。
     */
    private fun readMetaUnlocked(kbName: String): KnowledgeBase? {
        val raw = documents.read(kbName, "kb.json")
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<KnowledgeBase>(raw) }.getOrNull()
    }

    /** 读取当前轮次数（kb.json 的 turnCount 字段），供 OngoingContextSelector 冷却逻辑使用 */
    override suspend fun getTurnCount(kbName: String): Int = withContext(Dispatchers.IO) {
        readMetaUnlocked(kbName)?.turnCount ?: 0
    }

    /**
     * 关系画像正文。
     *
     * GenerationInput 的 `kbContext.profile` 历史上被写死成空串，
     * 于是"冻结输入"里根本没有画像，画像变化也就无从参与身份比对。
     * 拼哪四份、按什么顺序，归 [KnowledgeProfileStore]。
     */
    suspend fun readProfile(kbName: String): String = withContext(Dispatchers.IO) {
        profile.profileText(kbName)
    }

    /** 知识内容修订号（判据与输入文件清单见 [KnowledgeProfileStore.contentRevision]） */
    suspend fun contentRevision(kbName: String): String = withContext(Dispatchers.IO) {
        profile.contentRevision(kbName)
    }

    /** 读取当前阶段（kb.json）；读不到或库在根外时给空串 */
    override suspend fun getCurrentStage(kbName: String): String = withContext(Dispatchers.IO) {
        profile.stageOf(kbName)
    }

    /** 修改知识库显示名（在知识库管理页点击显示名编辑） */
    suspend fun updateDisplayName(kbName: String, newDisplay: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (newDisplay.isBlank()) return@withLock
            transactionUnlocked(kbName) {
                updateMeta { kb -> kb.copy(displayName = newDisplay.trim(), updatedAt = isoNow()) }
            }
        }
    }

    /** 设置知识库阶段标签（onboarding 推断 / 向量重估触发阶段变化时用）。写入前经 StageCatalog 归一化
     *  目标 KB 已删除时 no-op */
    suspend fun updateStage(kbName: String, stage: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("updateStage skipped: kb no longer exists")
                return@withLock
            }
            updateStageUnlocked(kbName, stage)
        }
    }

    /**
     * updateStage 的无锁核心：调用方必须已持有文件互斥锁。
     *
     * 判"能不能当阶段用"与"落不落盘"分家之后，这里只剩一次转发；
     * 白名单、拒绝时的措辞、以及 `updatedAt` 都归 [KnowledgeProfileStore.setStage]。
     */
    private fun updateStageUnlocked(kbName: String, stage: String) {
        profile.setStage(kbName, stage)
    }

    /** 读取 warmth.md 的五维状态向量（解析不到默认 50） */
    override suspend fun readVector(kbName: String): Map<String, Int> = withContext(Dispatchers.IO) {
        profile.vectorOf(kbName)
    }

    /** 就地更新 warmth.md 的五维状态向量数值
     *  目标 KB 已删除时 no-op */
    suspend fun writeVector(kbName: String, values: Map<String, Int>) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("writeVector skipped: kb no longer exists")
                return@withLock
            }
            writeVectorUnlocked(kbName, values)
        }
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
    private fun writeVectorUnlocked(kbName: String, values: Map<String, Int>) {
        profile.setVector(kbName, values)
    }

    /** 就地更新 warmth.md 的阶段标签行（阶段变化时用），保留旧值作为历史注释。写入前经 StageCatalog 归一化
     *  目标 KB 已删除时 no-op */
    suspend fun updateWarmthStageLabel(kbName: String, newStage: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("updateWarmthStageLabel skipped: kb no longer exists")
                return@withLock
            }
            updateWarmthStageLabelUnlocked(kbName, newStage)
        }
    }

    /**
     * updateWarmthStageLabel 的无锁核心：调用方必须已持有文件互斥锁。
     *
     * 阶段行怎么写（变体认法、「当前状态」节插入、旧值留成"过去曾经是…"）
     * 与 strict 版共用 [KnowledgeProfileStore.rewriteStageLine] 那一份——
     * 拆之前这两处各抄了 30 行，改一处就会漏另一处。
     */
    private fun updateWarmthStageLabelUnlocked(kbName: String, newStage: String) {
        profile.setWarmthStageLabel(kbName, newStage)
    }

    // ═══════════ 画像事务性写入 ═══════════

    /**
     * updateStageUnlocked 的 strict 版本——IO 失败时抛出异常，不吞错误。
     * 供事务性 API 使用；非事务场景仍用 [updateStageUnlocked]（容错）。
     *
     * 白名单判定与拒绝对那半句日志已归画像格；这里只多一样：**拒的原因要能分得开**
     * （非白名单 vs 写不进），所以两种失败各抛各的。
     */
    private fun updateStageUnlockedStrict(kbName: String, stage: String) {
        if (stage.isBlank()) return
        val normalized = profile.normalizeStage(stage, "updateStageStrict")
            ?: throw java.io.IOException("非法阶段：$stage")
        val written = transactionUnlocked(kbName) {
            updateMeta { kb -> kb.copy(stage = normalized, updatedAt = isoNow()) }
        }
        if (!written) {
            throw java.io.IOException("updateStageStrict 无法写 $kbName/kb.json（库缺失、schema 过新或 JSON 不可解析）")
        }
    }

    /**
     * updateWarmthStageLabelUnlocked 的 strict 版本——IO 失败时抛出异常。
     * 供事务性 API 使用。
     */
    private fun updateWarmthStageLabelUnlockedStrict(kbName: String, newStage: String) {
        if (newStage.isBlank()) return
        val stage = profile.normalizeStage(newStage, "updateWarmthStageLabelStrict")
            ?: throw java.io.IOException("非法阶段：$newStage")
        val path = KnowledgeProfileStore.WARMTH_FILE
        val warmth = readFileUnlockedFast(kbName, path)
        if (warmth.isBlank()) return
        val updated = profile.rewriteStageLine(warmth, stage)
        if (updated != warmth) writeFileUnlocked(kbName, path, updated)
    }

    /**
     * 无锁快速读取（调用方持有 mutex）。
     *
     * ⚠ 这一行改动是**行为收紧**，不是搬家：以前它直接 `File(File(root, kbName), path)`，
     * 完全不过 canonical 守门，所以"`..`"或绝对路径能从这条入口把文件指针指到库目录外面，
     * 而同一个内容的公开读却会被拒——同一件事两个答案。现在它走 [KnowledgeDocumentStore.read]，
     * 与公开读共用同一道门，宽严不再有第二套。
     */
    private fun readFileUnlockedFast(kbName: String, relativePath: String): String =
        documents.read(kbName, relativePath)

    /**
     * 画像更新事务性写入——在单次 fileMutex.withLock 中执行全部操作。
     *
     * 返回 typed [ProfileTransactionResult]，替代模糊 Boolean。
     *
     * - 所有文件写入、向量写入、阶段更新、warmth 标签更新在同一锁内完成
     * - **回滚与校验也走同一条写边界**：快照的存在性与旧内容出自同一道守门，恢复用
     *   `KnowledgeTx.write`、删除用 `KnowledgeTx.deleteAt`，不再有第二条 `atomicWriteText`
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
    suspend fun applyProfileUpdateAtomically(
        kbName: String,
        me: String?,
        her: String?,
        warmth: String?,
        stageChanged: Boolean,
        newStage: String?,
        expectedRevision: Int
    ): ProfileTransactionResult = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("applyProfileUpdateAtomically: kb no longer exists")
                return@withLock ProfileTransactionResult.PreconditionFailed(
                    PreconditionReason.KB_NOT_FOUND
                )
            }
            val currentRevision = readMemoryRevisionUnlocked(kbName)
            if (migrator.isReadOnly(kbName)) {
                // 只读判定本来藏在每一次写里面（writeFileUnlocked 静默跳过），于是这一段
                // 所有写都不落、也没有任何一步抛，函数一路走到 Success：磁盘没变、嘴里说成功。
                // 与公开的 transaction() 同一把尺——只读是**前置条件**，在入口就报出来。
                com.lovebrain.app.util.L.w("applyProfileUpdateAtomically refused: kb is read-only (schema newer)")
                return@withLock ProfileTransactionResult.PreconditionFailed(
                    PreconditionReason.LIBRARY_READ_ONLY
                )
            }
            if (currentRevision != expectedRevision) {
                com.lovebrain.app.util.L.w("applyProfileUpdateAtomically: revision changed (expected=$expectedRevision, current=$currentRevision)")
                return@withLock ProfileTransactionResult.PreconditionFailed(
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
            for ((path, _) in writeTargets) backups[path] = snapshotBeforeWriteUnlocked(kbName, path)
            // warmth.md 即使不在 writeTargets 中，stage 变化时也会被 updateWarmthStageLabel 修改
            if (willChangeStage && "understand/warmth.md" !in backups) {
                backups["understand/warmth.md"] =
                    snapshotBeforeWriteUnlocked(kbName, "understand/warmth.md")
            }
            // kb.json backup（stage 变化时 updateStageUnlockedStrict 会修改它）
            if (willChangeStage) {
                backups["kb.json"] = snapshotBeforeWriteUnlocked(kbName, "kb.json")
            }
            // 向量 backup（warmth 变化时向量同步会修改 warmth.md 中的数值）
            val oldVector = if (warmth != null) {
                readVectorUnlockedFast(kbName)
            } else null

            try {
                // 逐个写入画像文件
                for ((path, content) in writeTargets) {
                    writeFileUnlocked(kbName, path, content)
                }

                // warmth 向量同步——保持 warmth.md 中的数值与文件内容一致
                // 注意：writeVectorUnlocked 会修改 warmth.md，如果 warmth 内容已写入
                if (warmth != null && oldVector != null && oldVector.isNotEmpty()) {
                    writeVectorUnlocked(kbName, oldVector)
                }

                // 阶段更新——使用 strict 版本，IO 失败必须抛出
                if (willChangeStage) {
                    updateStageUnlockedStrict(kbName, newStage!!)
                    updateWarmthStageLabelUnlockedStrict(kbName, newStage)
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
                            val restored = transactionUnlocked(kbName) { write(path, oldContent) }
                            val now = transactionUnlocked(kbName) { readTextAt(path) }
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
                            val present = safeKbFile(kbName, path)?.exists() == true
                            if (present) {
                                val deleted = transactionUnlocked(kbName) { deleteAt(path) }
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
                // 必须加入 rollbackFailures，不得从 applyProfileUpdateAtomically 直接 throw 绕过 typed result
                for ((path, existedAndContent) in backups) {
                    try {
                        // 校验也必须过守门：这条 `File(File(knowledgeRoot, kb), path)` 之前
                        // 是全仓最后一条"自己拼库内路径"，现在解析不出来就是 null
                        val file = safeKbFile(kbName, path)
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
                return@withLock if (rollbackFailures.isEmpty()) {
                    ProfileTransactionResult.RolledBack(e)
                } else {
                    ProfileTransactionResult.RollbackFailed(e, rollbackFailures)
                }
            }

            scheduleDebouncedBackup()
            ProfileTransactionResult.Success
        }
    }

    /**
     * 写前快照：目标文件的**存在性与旧内容必须出自同一道门**。
     *
     * 之前是裸 `File(File(knowledgeRoot, kb), path).exists()` 配守门版 `readFileUnlockedFast`：
     * 库名带 `..` 时前者说"在"、后者给空串，回滚因此把库外那个文件当"本库的旧文件"写成空。
     * 这里刻意读**新路径那一份**而不是带旧布局回退的公开读——快照要描述"这次写会覆盖谁"，
     * 不是"读侧会看到什么"。读不出内容时**让异常穿出去**：这一段在所有写之前，抛出来说明
     * 一个字节都没动过；把它吞成空串反而会害命——回滚会照着"旧内容是空"把真文件写空。
     */
    private fun snapshotBeforeWriteUnlocked(kbName: String, relativePath: String): Pair<Boolean, String> {
        val file = safeKbFile(kbName, relativePath) ?: return false to ""
        if (!file.isFile) return false to ""
        return true to file.readText(Charsets.UTF_8)
    }

    /**
     * 无锁快速读取向量（不加 mutex，调用方持有锁）。
     *
     * 拆画像格之前这是第三份"自己解析 warmth 里的五维"：与 `readVector` 少一条零匹配日志，
     * 维度表还各自引用一份。现在解析与维度表都只有 [KnowledgeProfileStore.vectorOf] 一处。
     */
    private fun readVectorUnlockedFast(kbName: String): Map<String, Int> = profile.vectorOf(kbName)


    // ═══════════ 谈心日志（两段式） ═══════════

    private val counselingH1 = "# 谈心记录"
    private val counselingH2 = "# 军师分析"

    /**
     * 谈心日志两段式追加：recordEntry 写入「# 谈心记录」节，analysisEntry 写入「# 军师分析」节。
     * 固定代码写入、全量不截断。旧格式文件（没有两个 # 大标题）自动迁移：旧内容并入第一节。
     *  目标 KB 已删除时 no-op
     */
    suspend fun appendCounselingEntries(kbName: String, recordEntry: String, analysisEntry: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("appendCounselingEntries skipped: kb no longer exists")
                return@withLock
            }
            val path = "memory/counseling_log.md"
            val lines = readFile(kbName, path).lines()
            val idx1 = lines.indexOfFirst { it.trim() == counselingH1 }
            val idx2 = lines.indexOfFirst { it.trim() == counselingH2 }

            val newContent = if (idx1 >= 0 && idx2 > idx1) {
                val section1 = lines.subList(0, idx2).joinToString("\n")
                val section2 = lines.subList(idx2, lines.size).joinToString("\n")
                buildString {
                    append(section1.trimEnd())
                    if (recordEntry.isNotBlank()) append("\n\n").append(recordEntry.trim())
                    append("\n\n")
                    append(section2.trimEnd())
                    if (analysisEntry.isNotBlank()) append("\n\n").append(analysisEntry.trim())
                    append("\n")
                }
            } else {
                // 旧格式/无标题：重建两段结构，旧内容整体并入第一节
                val old = lines.joinToString("\n").trim()
                buildString {
                    append(counselingH1).append("\n")
                    if (old.isNotBlank()) append("\n").append(old).append("\n")
                    if (recordEntry.isNotBlank()) append("\n").append(recordEntry.trim()).append("\n")
                    append("\n").append(counselingH2).append("\n")
                    if (analysisEntry.isNotBlank()) append("\n").append(analysisEntry.trim()).append("\n")
                }
            }
            writeFileUnlocked(kbName, path, newContent)
        }
    }

    /**
     * 原子追加"实际发送"记录——在单次 fileMutex.withLock 中完成：
     * 1. 校验 KB 仍存在
     * 2. 读取 recent.md
     * 3. 追加发送记录
     * 4. 原子写入
     * 5. 返回 true（成功）或 false（KB 不存在）
     *
     * 消除 ViewModel 中 listAll → readFile → writeFile 的 TOCTOU 竞态。
     */
    suspend fun appendActualSentRecord(kbName: String, entry: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("appendActualSentRecord skipped: kb no longer exists")
                return@withLock false
            }
            val recentPath = "moment/recent.md"
            val existing = readFileUnlockedFast(kbName, recentPath)
            writeFileUnlocked(kbName, recentPath, existing + entry)
            true
        }
    }

    /** 替换同一 generationVersionId 的旧 actual sent 记录（upsert）。
     * 在单次 fileMutex.withLock 中完成：检查 KB → 读取 → 替换 → 写入 → 返回 Boolean。
     * 如果 oldEntry 在 recent.md 中不存在，返回 false（不执行无效写入）。 */
    suspend fun replaceActualSentRecord(kbName: String, oldEntry: String, newEntry: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("replaceActualSentRecord skipped: kb no longer exists")
                return@withLock false
            }
            val recentPath = "moment/recent.md"
            val existing = readFileUnlockedFast(kbName, recentPath)
            // oldEntry 不存在时返回 false，不执行无效写入
            if (!existing.contains(oldEntry)) {
                com.lovebrain.app.util.L.w("replaceActualSentRecord: oldEntry not found in recent.md")
                return@withLock false
            }
            val updated = existing.replace(oldEntry, newEntry)
            writeFileUnlocked(kbName, recentPath, updated)
            true
        }
    }

    /** 读取谈心日志「# 军师分析」节的最近 count 个 ## 块（供画像更新引擎） */
    override suspend fun readCounselingAnalysisBlocks(kbName: String, count: Int): String = withContext(Dispatchers.IO) {
        val content = readFile(kbName, "memory/counseling_log.md")
        val idx = content.indexOf(counselingH2)
        if (idx < 0) return@withContext ""
        val section = content.substring(idx + counselingH2.length)
        val blocks = section.split(Regex("(?m)^(?=## )"))
            .map { it.trim() }
            .filter { it.startsWith("## ") }
        blocks.takeLast(count).joinToString("\n\n")
    }

    // ═══════════ 话题管理 API ═══════════

    /** 话题行的读法在 [KbTextOps.topicLabel]，与四处写侧同一个所有者 */
    override suspend fun getCurrentTopic(kbName: String): String = withContext(Dispatchers.IO) {
        KbTextOps.topicLabel(readFile(kbName, "moment/topic.md"))
    }

    override suspend fun setCurrentTopic(kbName: String, topicLabel: String) = withContext(Dispatchers.IO) {
        val time = com.lovebrain.app.util.TimeFmt.now()
        writeFile(kbName, "moment/topic.md", KbTextOps.topicLine(time, topicLabel))
    }

    /** 读取 plan.md「## 进行中」分区的事项行（注入 prompt；已结束不注入） */
    override suspend fun readPlanActive(kbName: String): String = withContext(Dispatchers.IO) {
        val content = readFile(kbName, "moment/plan.md")
        val sb = StringBuilder()
        var inActive = false
        var inComment = false
        for (line in content.lines()) {
            val t = line.trim()
            // 跨行注释块跟踪（注释里的格式/示例绝不注入）
            if (inComment) {
                if (t.contains("-->")) inComment = false
                continue
            }
            when {
                t.startsWith("<!--") -> if (!t.contains("-->")) inComment = true
                t.startsWith("## 进行中") -> inActive = true
                t.startsWith("##") -> inActive = false
                // 旧数据防御：裸的"格式/示例"说明行不当事项注入
                t.startsWith("格式") || t.startsWith("示例") -> Unit
                inActive && t.contains("|") -> sb.append(t).append("\n")
            }
        }
        sb.toString().trim()
    }

    /** 获取当前话题的年龄（小时），用于时间衰减判断 */
    override suspend fun getTopicAgeHours(kbName: String): Int = withContext(Dispatchers.IO) {
        val content = readFile(kbName, "moment/topic.md")
        val match = Regex("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]").find(content) ?: return@withContext 99
        val updated = com.lovebrain.app.util.TimeFmt.parse(match.groupValues[1])
        if (updated <= 0) return@withContext 99
        ((System.currentTimeMillis() - updated) / 3600_000).toInt()
    }

    // ═══════════ 话题归档（§5.3 第七格：实现见 KnowledgeArchiveService）═══════════

    /**
     * rotateTopic — 使用累积操作状态实现幂等和中断恢复。
     *
     * 四步（读输入 → 追加归档条目 → 计数 +1 → 清空四个源文件）与"信任状态文件里记录的
     * 已完成步骤"这套恢复判定，都在 [KnowledgeArchiveService.rotate]；这里只剩"一次锁 + 一次 IO 线程"。
     *
     * 搬走的原因是三件事原先没有名字：哪些步骤算完成、归档条目长什么样、计数从哪回填——
     * 它们与 kb.json 的读写挤在同一段 80 行的方法体里，只能连着临时目录和互斥锁间接测。
     */
    override suspend fun rotateTopic(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock { archive.rotate(kbName) }
    }

    /**
     * 归档计数：kb.json 的 `topicCount`；旧库还没记数时从 `memory/raw_topic.md` 回填一次并持久化。
     *
     * 两处改动是本轮显式决定的：
     *  ① 原先"读 kb.json"与"回填"各自加一次锁，两次之间别人可以写完一份，回填就会拿旧口径
     *     覆盖一次计数——现在整段在一次锁内完成（收紧，不是搬家）；
     *  ② "怎么数一条归档"这条格式规则搬去 [KnowledgeArchiveService.countArchiveEntries]，
     *     与 rotate 那一步的 `topicCount + 1` 同出一门。**事务编排仍在这里**：
     *     归档格的能力接口已经七样，再为一次回填加"读 meta"就是第八样，那是拿拆类名义放宽端口。
     */
    override suspend fun getLessonCount(kbName: String): Int = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val counted = readMetaUnlocked(kbName)?.topicCount ?: 0
            if (counted > 0) return@withLock counted
            val content = documents.read(kbName, KnowledgeArchiveService.ARCHIVE_FILE)
            val regexCount = archive.countArchiveEntries(content)
            if (regexCount > 0) {
                transactionUnlocked(kbName) { updateMeta { kb -> kb.copy(topicCount = regexCount) } }
            }
            regexCount
        }
    }


    private fun readAsset(path: String): String {
        return runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date())

    companion object {
        // 内容修订号的输入清单已随画像正文归 KnowledgeProfileStore，这里不再有第二份

        // 旧→新路径映射已随读路径一起归 KnowledgeDocumentStore，这里不再有第二份
        /**
         * 记忆格的两个文件相对路径。
         *
         * 只登记**名字**，不登记怎么拼：拼路径是文档格的守门职责（[KnowledgeDocumentStore.resolve]），
         * 读写两侧都用这两个常量，免得又出现"读不过门、写走门"那种宽严不一。
         */
        const val MEMORY_REVISION_FILE = "memory/.revision"
        const val CORRECTIONS_FILE = "memory/corrections.json"
        // rotateTopic 的状态文件名跟着归档格走（KnowledgeArchiveService.STATE_FILE），这里不留第二份
    }

    /** 从 assets/schema/ 加载知识库初始化模板（标题骨架 = 单一数据源） */
    private fun loadSchema(name: String): String {
        return readAsset(com.lovebrain.app.domain.AssetRegistry.schema(name))
    }
}
