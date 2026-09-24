package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.KnowledgeSchemaVersion
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

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
) {
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
    private inner class RepoStorage : KbStorageAccess {
        override val root: File get() = knowledgeRoot
        override fun atomicWrite(target: File, content: String) {
            // 迁移器只会写"不超纲"的库（判定在 KnowledgeMigrator 里提前 return），
            // 所以这里返回 false 一定是异常状况，必须留下痕迹而不是静默跳过。
            if (!atomicWriteText(target, content)) {
                com.lovebrain.app.util.L.e(
                    "migration write was refused by the schema guard: ${target.name}", null
                )
            }
        }
        override fun schema(name: String): String = loadSchema(name)
        override suspend fun currentStage(kbName: String): String = getCurrentStage(kbName)
        override suspend fun setStage(kbName: String, stage: String): Unit =
            updateStageUnlocked(kbName, stage)
        override suspend fun setWarmthStageLabel(kbName: String, stage: String): Unit =
            updateWarmthStageLabelUnlocked(kbName, stage)
        override fun timestamp(): String = isoNow()
    }

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

    // ═══════════ 自动备份 ═══════════

    /**
     * 序列化备份过程——与 delete 共用 fileMutex，防止并发竞态。
     * 备份最多 12 小时一次、知识库主要是文本，短暂锁住文件更新的成本可接受。
     */
    private suspend fun backupIfNeeded() {
        fileMutex.withLock {
            backupIfNeededUnlocked()
        }
    }

    /**
     * 自动备份核心（无锁）：如果距上次备份超过 12 小时，复制所有知识库到 .backup/目录。
     * 调用方必须已持有 fileMutex。
     */
    private fun backupIfNeededUnlocked() {
        val marker = File(knowledgeRoot, ".last_backup")
        val now = System.currentTimeMillis()
        val lastBackup = if (marker.exists()) runCatching { marker.readText().toLong() }.getOrDefault(0L) else 0L
        if (now - lastBackup < BACKUP_INTERVAL_MS) return

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val backupRoot = File(knowledgeRoot, ".backup")
        backupRoot.mkdirs()

        // 备份每个知识库
        knowledgeRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.forEach { kbDir ->
                runCatching {
                    val backupDir = File(backupRoot, "${kbDir.name}_$timestamp")
                    if (backupDir.exists()) return@runCatching
                    backupDir.mkdirs()
                    kbDir.walkTopDown().forEach { src ->
                        val rel = src.relativeTo(kbDir).path
                        if (rel == ".") return@forEach
                        val dst = File(backupDir, rel)
                        if (src.isDirectory) dst.mkdirs() else src.copyTo(dst, overwrite = false)
                    }
                }
            }

        // 修剪旧备份（每个 KB 只保留最近 N 份）
        pruneBackups()

        // 更新备份时间标记
        atomicWriteText(marker, now.toString())
    }

    /** 修剪旧备份：每个知识库只保留最近 BACKUP_MAX_COUNT 份 */
    private fun pruneBackups() {
        val backupRoot = File(knowledgeRoot, ".backup")
        if (!backupRoot.exists()) return
        // 按知识库名分组，每组只保留最近 N 份
        val groups = backupRoot.listFiles()?.filter { it.isDirectory }
            ?.groupBy { backupGroupKey(it.name) } ?: return
        groups.forEach { (_, backups) ->
            if (backups.size > BACKUP_MAX_COUNT) {
                backups.sortedByDescending { it.name }
                    .drop(BACKUP_MAX_COUNT)
                    .forEach { old -> runCatching { old.deleteRecursively() } }
            }
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
            atomicWriteText(File(defaultDir, "moment/topic.md"), "- [${com.lovebrain.app.util.TimeFmt.now()}] 正在聊：（等待第一次对话）")
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

    /** 无锁版 listAll（调用方持有 fileMutex） */
    private fun listAllUnlocked(): List<KnowledgeBase> {
        return knowledgeRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { dir ->
                runCatching {
                    val metaFile = File(dir, "kb.json")
                    if (metaFile.exists()) {
                        val kb = json.decodeFromString<KnowledgeBase>(metaFile.readText())
                        if (kb.name != dir.name) null else kb
                    } else null
                }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

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
            atomicWriteText(topicFile, "- [${com.lovebrain.app.util.TimeFmt.now()}] 正在聊：（等待第一次对话）")
        }
    }

    // ═══════════ 公开 API ═══════════

    suspend fun listAll(): List<KnowledgeBase> = withContext(Dispatchers.IO) {
        knowledgeRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { dir ->
                runCatching {
                    val metaFile = File(dir, "kb.json")
                    if (metaFile.exists()) {
                        val kb = json.decodeFromString<KnowledgeBase>(metaFile.readText())
                        //  name 字段必须与目录名等值——防 kb.json 内容字段路径遍历（单一扼制点，所有消费端均源于 listAll）
                        if (kb.name != dir.name) {
                            com.lovebrain.app.util.L.w("知识库元数据异常已忽略：dir=${dir.name}")
                            null
                        } else kb
                    } else null
                }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    suspend fun getActive(): KnowledgeBase? = withContext(Dispatchers.IO) {
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

    /** 读取持续意图配置 */
    suspend fun readIntent(kbName: String): IntentConfig = withContext(Dispatchers.IO) {
        val file = File(File(knowledgeRoot, kbName), "moment/intent.json")
        if (!file.exists()) return@withContext IntentConfig()
        runCatching {
            json.decodeFromString<IntentConfig>(file.readText())
        }.getOrDefault(IntentConfig())
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

    /** 无锁版读取（调用方持有 fileMutex） */
    private fun readIntentUnlocked(kbName: String): IntentConfig {
        val file = File(File(knowledgeRoot, kbName), "moment/intent.json")
        if (!file.exists()) return IntentConfig()
        return runCatching {
            json.decodeFromString<IntentConfig>(file.readText())
        }.getOrDefault(IntentConfig())
    }

    // ═══════════ 记忆纠正（每 KB 一份，memory/corrections.json） ═══════════

    /** 读取纠正记录列表。返回 memoryId → correction 映射。 */
    suspend fun readCorrections(kbName: String): Map<String, com.lovebrain.app.model.MemoryCorrection> = withContext(Dispatchers.IO) {
        val file = File(File(knowledgeRoot, kbName), "memory/corrections.json")
        if (!file.exists()) return@withContext emptyMap()
        runCatching {
            val list = json.decodeFromString<List<com.lovebrain.app.model.MemoryCorrection>>(file.readText())
            list.associateBy { it.memoryId }
        }.getOrDefault(emptyMap())
    }

    /** 保存一条纠正记录。revision 单调递增（库级），不会因撤销倒退。
     * R07: 不再使用剩余记录的 max 推算 revision（撤销删除后可能倒退）。
     * 改为读取库级持久化 revision 标记，每次纠正/撤销均递增。 */
    suspend fun saveCorrection(
        kbName: String,
        memoryId: String,
        action: com.lovebrain.app.model.CorrectionAction,
        replacementText: String = "",
        targetKbId: String = "",
        muteDuration: com.lovebrain.app.model.MuteDuration = com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) return@withLock false
            val current = readCorrectionsUnlocked(kbName)
            // R07: 读取库级持久化 revision（单调递增，不会因撤销倒退）
            val newRevision = readMemoryRevisionUnlocked(kbName) + 1
            val now = isoNow()
            val correction = com.lovebrain.app.model.MemoryCorrection(
                memoryId = memoryId,
                action = action,
                replacementText = replacementText,
                targetKbId = targetKbId,
                revision = newRevision,
                updatedAt = now,
                muteDuration = muteDuration,
                muteTimestamp = if (action == com.lovebrain.app.model.CorrectionAction.MUTED) now else ""
            )
            val updated = current.toMutableMap()
            updated[memoryId] = correction
            // 写边界：两条落盘都从写事务句柄取，schema 过新的库返回 false 而不是"报了成功却没写"。
            var written = false
            transactionUnlocked(kbName) {
                written = write(
                    "memory/corrections.json",
                    json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.lovebrain.app.model.MemoryCorrection.serializer()
                        ),
                        updated.values.toList()
                    )
                )
                // R07: 持久化库级 revision（单调递增）
                if (written) write("memory/.revision", newRevision.toString())
            }
            written
        }
    }

    /** 撤销纠正 — 删除指定 memoryId 的纠正记录。
     * R07: 撤销也递增库级 revision，保证单调性。 */
    suspend fun undoCorrection(kbName: String, memoryId: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) return@withLock false
            val current = readCorrectionsUnlocked(kbName)
            if (!current.containsKey(memoryId)) return@withLock false
            val updated = current.toMutableMap()
            updated.remove(memoryId)
            var written = false
            transactionUnlocked(kbName) {
                written = write(
                    "memory/corrections.json",
                    json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.lovebrain.app.model.MemoryCorrection.serializer()
                        ),
                        updated.values.toList()
                    )
                )
                // R07: 撤销也递增 revision（防 0→1→0 倒退）
                if (written) {
                    write("memory/.revision", (readMemoryRevisionUnlocked(kbName) + 1).toString())
                }
            }
            written
        }
    }

    /** 获取纠正记录的全局 revision（用于后台防护）。
     * R07: 读取库级持久化 revision（单调递增），不依赖剩余记录 max。
     * b3-8: 加锁读取，保证一致性（原先无锁读可能读到半写状态） */
    suspend fun getCorrectionsRevision(kbName: String): Int = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            readMemoryRevisionUnlocked(kbName)
        }
    }

    /** R07: 原子读取纠正记录和 revision——用于生成准备阶段一次性快照。
     * 消除读取纠正和读取 revision 之间的竞态窗口。 */
    suspend fun readCorrectionsAndRevision(kbName: String): Pair<Map<String, com.lovebrain.app.model.MemoryCorrection>, Int> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            readCorrectionsUnlocked(kbName) to readMemoryRevisionUnlocked(kbName)
        }
    }

    /** b3-8: 带修订版本条件校验的原子追加——在锁内一次性完成 revision 检查和文件写入，
     * 消除 KnowledgeTriggerCoordinator 中先检查后写入的竞态窗口。
     * @return true = 写入成功，false = revision 已变或 KB 不存在 */
    suspend fun appendFileWithRevisionCheck(
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
    suspend fun writeVectorWithRevisionCheck(
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

    /** R07: 库级 memory revision 标记文件 */
    private fun memoryRevisionFile(kbName: String): File =
        File(File(knowledgeRoot, kbName), "memory/.revision")

    /** R07: 读取库级 memory revision（无锁，调用方持有 fileMutex） */
    private fun readMemoryRevisionUnlocked(kbName: String): Int {
        val file = memoryRevisionFile(kbName)
        if (!file.exists()) return 0
        return runCatching { file.readText().trim().toIntOrNull() ?: 0 }.getOrDefault(0)
    }

    /** R07: 写入库级 memory revision（无锁，调用方持有 fileMutex） */
    private fun writeMemoryRevisionUnlocked(kbName: String, revision: Int) {
        transactionUnlocked(kbName) { write("memory/.revision", revision.toString()) }
    }

    /** 无锁版读取（调用方持有 fileMutex） */
    private fun readCorrectionsUnlocked(kbName: String): Map<String, com.lovebrain.app.model.MemoryCorrection> {
        val file = File(File(knowledgeRoot, kbName), "memory/corrections.json")
        if (!file.exists()) return emptyMap()
        return runCatching {
            val list = json.decodeFromString<List<com.lovebrain.app.model.MemoryCorrection>>(file.readText())
            list.associateBy { it.memoryId }
        }.getOrDefault(emptyMap())
    }

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
                deleteBackupsForKbUnlocked(name)
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
     * 删除指定 KB 的全部自动备份。使用 backupGroupKey 精确匹配，不用 startsWith 防误删。
     * 调用方必须已持有 fileMutex。
     */
    private fun deleteBackupsForKbUnlocked(kbName: String) {
        val backupRoot = File(knowledgeRoot, ".backup")
        backupRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.filter { backupGroupKey(it.name) == kbName }
            ?.forEach {
                runCatching { it.deleteRecursively() }
            }
    }

    /**
     * 读取文件（自动兼容新旧路径）。
     *
     * 写边界的另一半：读也要过 [safeKbFile]。旧实现直接 `File(File(knowledgeRoot, kbName), relativePath)`，
     * 于是"canonical 边界覆盖了所有 String 入口"这句话对读路径并不成立——
     * `../` 或绝对路径照样能把打开的文件指到知识库目录外面。
     */
    suspend fun readFile(kbName: String, relativePath: String): String = withContext(Dispatchers.IO) {
        val newFile = safeKbFile(kbName, relativePath) ?: return@withContext ""
        if (newFile.exists()) return@withContext newFile.readText()
        val oldPath = OLD_PATH_MAP[relativePath]
        if (oldPath != null) {
            val oldFile = safeKbFile(kbName, oldPath) ?: return@withContext ""
            if (oldFile.exists()) return@withContext oldFile.readText()
        }
        ""
    }

    // ═══════════ canonical 路径边界 ═══════════

    /**
     * 把裸字符串库名校验成 [KbName]。
     *
     *原话是"Repository 的公共方法仍接收裸 String kbName/path，
     * canonical boundary 没建立"。光加一个没人用的 value class 不算建立边界，
     * 所以这里让**所有** String 入口先过同一套校验：
     * 空名、带路径分隔符、`..`、超长一律拒绝，非法输入不再有机会变成 File 路径。
     */
    fun toKbName(raw: String): com.lovebrain.app.model.KbName? =
        runCatching { com.lovebrain.app.model.KbName(raw) }
            .onFailure { com.lovebrain.app.util.L.w("rejected kb name: ${it.message}") }
            .getOrNull()

    /** 把裸字符串相对路径校验成 [KbRelativePath] */
    fun toKbPath(raw: String): com.lovebrain.app.model.KbRelativePath? =
        runCatching { com.lovebrain.app.model.KbRelativePath(raw) }
            .onFailure { com.lovebrain.app.util.L.w("rejected kb path: ${it.message}") }
            .getOrNull()

    /**
     * String 入口的统一守门：返回解析后的绝对 File，非法输入返回 null 并记日志。
     *
     * 之前只检查 `..`，漏了绝对路径与 Windows 反斜杠分隔符，
     * 也允许 `/etc/passwd` 这类以 `/` 开头的值走到 File(parent, child) 里。
     */
    private fun safeKbFile(kbName: String, relativePath: String): File? {
        val name = toKbName(kbName) ?: return null
        val path = toKbPath(relativePath) ?: return null
        val dir = File(knowledgeRoot, name.value)
        val file = File(dir, path.value)
        // canonicalPath 在某些畸形输入上会直接抛 IOException（Windows 上混用分隔符时实测会抛），
        // 边界函数不能让异常穿到调用方——抛不出去就当拒绝。
        val escaped = runCatching {
            !file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
        }.getOrElse {
            com.lovebrain.app.util.L.w("path could not be canonicalised, refused: ${it.message}")
            true
        }
        if (escaped) {
            com.lovebrain.app.util.L.w("path escapes knowledge dir, refused")
            return null
        }
        return file
    }

    /** 线程安全的文件追加（fileMutex 锁 + I/O 线程；A2-5 合并原 appendFileSafe）
     *  目标 KB 已删除时 no-op，不自动 mkdirs 复活 */
    suspend fun appendFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("appendFile skipped: kb no longer exists")
                return@withLock
            }
            appendFileUnlocked(kbName, relativePath, content)
        }
    }

    /** 线程安全的文件删除（fileMutex 锁 + I/O 线程） */
    suspend fun deleteFile(kbName: String, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) return@withLock false
            if (refusedByReadOnlySchema(kbName, "deleteFile", relativePath)) return@withLock false
            val file = safeKbFile(kbName, relativePath) ?: return@withLock false
            if (file.exists()) file.delete() else false
        }
    }

    /** 线程安全的文件写入（fileMutex 锁 + I/O 线程；A2-5 合并原 writeFileSafe）
     *  目标 KB 已删除时 no-op，不自动 mkdirs 复活 */
    suspend fun writeFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
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
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("writeFileWithVersion skipped: kb no longer exists")
                return@withLock null
            }
            val file = safeKbFile(kbName, relativePath) ?: return@withLock null
            val currentVersion = if (file.exists()) {
                KbTextOps.sha256(file.readText())
            } else {
                KbTextOps.sha256("")
            }
            if (currentVersion != expectedVersion) {
                com.lovebrain.app.util.L.w("writeFileWithVersion conflict: $relativePath")
                return@withLock null
            }
            writeFileUnlocked(kbName, relativePath, content)
            KbTextOps.sha256(content)
        }
    }

    /**
     * 读取文件并返回内容 + 版本号（SHA-256）。
     * 调用方持有版本号，写入时传给 [writeFileWithVersion] 做冲突检测。
     */
    suspend fun readFileWithVersion(kbName: String, relativePath: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val content = readFile(kbName, relativePath)
        content to KbTextOps.sha256(content)
    }

    /** 对外暴露的内容哈希——供 KbEdit 无版本校验路径生成新版本号 */
    fun hashContent(text: String): String = KbTextOps.sha256(text)

    /**  目标 KB 已删除时 no-op */
    suspend fun incrementTurnCount(kbName: String) = incrementTurnCountBy(kbName, 1)

    /**
     * 按 WAL 事件中记录的增量推进轮次计数。
     *
     * 恢复路径必须读事件里的 `turnCountIncrement` 值，
     * 而不是硬编码 +1——否则一次记录 2 轮的事务恢复后只补 1。
     */
    suspend fun incrementTurnCountBy(kbName: String, delta: Int) = withContext(Dispatchers.IO) {
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

    /** 读取当前轮次数（kb.json 的 turnCount 字段），供 OngoingContextSelector 冷却逻辑使用 */
    suspend fun getTurnCount(kbName: String): Int = withContext(Dispatchers.IO) {
        val metaFile = File(File(knowledgeRoot, kbName), "kb.json")
        if (!metaFile.exists()) return@withContext 0
        runCatching {
            json.decodeFromString<KnowledgeBase>(metaFile.readText()).turnCount
        }.getOrDefault(0)
    }

    /**
     * 关系画像正文。
     *
     * GenerationInput 的 `kbContext.profile` 历史上被写死成空串，
     * 于是"冻结输入"里根本没有画像，画像变化也就无从参与身份比对。
     */
    suspend fun readProfile(kbName: String): String = withContext(Dispatchers.IO) {
        buildString {
            for (name in listOf("me", "her", "warmth", "style")) {
                val text = readFile(kbName, "understand/$name.md").trim()
                if (text.isNotBlank()) append(text).append('\n')
            }
        }
    }

    /**
     * 知识内容修订号——对回复链路真正会读到的知识文件取内容指纹。
     *
     * 不能用 turnCount 近似：turnCount 只统计"提交过几轮"，
     * 同一 turnCount 可以对应完全不同的画像/场景/事项内容，
     * 手工编辑画像也不会改 turnCount。
     */
    suspend fun contentRevision(kbName: String): String = withContext(Dispatchers.IO) {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (path in REVISION_INPUT_PATHS) {
            val text = readFile(kbName, path)
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(text.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        digest.digest().joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    /** 读取当前阶段（kb.json） */
    suspend fun getCurrentStage(kbName: String): String = withContext(Dispatchers.IO) {
        val metaFile = File(File(knowledgeRoot, kbName), "kb.json")
        runCatching {
            json.decodeFromString<KnowledgeBase>(metaFile.readText()).stage
        }.getOrDefault("")
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

    /** updateStage 的无锁核心：调用方必须已持有文件互斥锁 */
    private suspend fun updateStageUnlocked(kbName: String, stage: String) {
        if (stage.isBlank()) return
        val normalized = com.lovebrain.app.domain.StageCatalog.normalize(stage)
        if (normalized == null) {
            com.lovebrain.app.util.L.w("updateStage 拒绝非白名单阶段：'$stage'（九阶段见 StageCatalog）")
            return
        }
        transactionUnlocked(kbName) {
            updateMeta { kb -> kb.copy(stage = normalized, updatedAt = isoNow()) }
        }
    }

    private val vectorDims = listOf(
        "亲密度" to "intimacy", "信任度" to "trust", "承诺度" to "commitment",
        "激情" to "passion", "安全感" to "security"
    )

    /** 读取 warmth.md 的五维状态向量（解析不到默认 50） */
    suspend fun readVector(kbName: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val warmth = readFile(kbName, "understand/warmth.md")
        val result = mutableMapOf<String, Int>()
        for ((cn, en) in vectorDims) {
            val v = Regex("$cn[^：:]*[：:]\\s*(\\d+)").find(warmth)?.groupValues?.get(1)?.toIntOrNull()
            if (v == null) com.lovebrain.app.util.L.w("readVector 维度零匹配：$cn（文件长度=${warmth.length}）")
            result[en] = v ?: 50
        }
        result
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

    /** b3-8: writeVector 的无锁核心——调用方必须已持有 fileMutex */
    private fun writeVectorUnlocked(kbName: String, values: Map<String, Int>) {
        val path = "understand/warmth.md"
        val file = File(File(knowledgeRoot, kbName), path)
        var warmth = file.takeIf { it.exists() }?.readText() ?: return
        if (warmth.isBlank()) return
        for ((cn, en) in vectorDims) {
            val v = values[en] ?: continue
            // [^/\n]* 兼容占位值（如"待评估"）和已有数字，保留 "/100" 后缀
            val dimRegex = Regex("($cn[^：:]*[：:]\\s*)[^/\\n]*")
            if (!dimRegex.containsMatchIn(warmth)) com.lovebrain.app.util.L.w("writeVector 维度零匹配：$cn（文件长度=${warmth.length}）")
            warmth = warmth.replaceFirst(dimRegex, "$1$v")
        }
        writeFileUnlocked(kbName, path, warmth)
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

    /** updateWarmthStageLabel 的无锁核心：调用方必须已持有文件互斥锁 */
    private suspend fun updateWarmthStageLabelUnlocked(kbName: String, newStage: String) {
        if (newStage.isBlank()) return
        val stage = com.lovebrain.app.domain.StageCatalog.normalize(newStage) ?: run {
            com.lovebrain.app.util.L.w("updateWarmthStageLabel 拒绝非白名单阶段：'$newStage'")
            return
        }
        val path = "understand/warmth.md"
        val warmth = readFile(kbName, path)
        if (warmth.isBlank()) return
        //  加固：兼容 "- 阶段标签：" / "- 阶段：" / "阶段标签:" 等变体；找不到则在「当前状态」节首行插入
        val regex = Regex("(-\\s*阶段(?:标签)?[：:])([^\n]*)")
        val match = regex.find(warmth)
        if (match == null) {
            val header = "## 当前状态"
            val idx = warmth.indexOf(header)
            val updated = if (idx >= 0) {
                warmth.substring(0, idx + header.length) + "\n- 阶段标签：$stage" + warmth.substring(idx + header.length)
            } else {
                "- 阶段标签：$stage\n" + warmth
            }
            if (updated != warmth) writeFileUnlocked(kbName, path, updated)
            return
        }
        val oldValue = match.groupValues[2].trim()
        // 提取旧阶段名（去掉已有的历史注释部分）
        val oldStage = oldValue.split("；").firstOrNull()?.trim() ?: oldValue
        val newValue = if (oldStage.isNotBlank() && oldStage != stage) {
            "$stage；过去曾经是$oldStage"
        } else {
            stage
        }
        val updated = warmth.replaceFirst(regex, "${match.groupValues[1]}$newValue")
        if (updated != warmth) writeFileUnlocked(kbName, path, updated)
    }

    // ═══════════ 画像事务性写入 ═══════════

    /**
     * updateStageUnlocked 的 strict 版本——IO 失败时抛出异常，不吞错误。
     * 供事务性 API 使用；非事务场景仍用 [updateStageUnlocked]（容错）。
     */
    private suspend fun updateStageUnlockedStrict(kbName: String, stage: String) {
        if (stage.isBlank()) return
        val normalized = com.lovebrain.app.domain.StageCatalog.normalize(stage)
        if (normalized == null) {
            com.lovebrain.app.util.L.w("updateStageStrict 拒绝非白名单阶段：'$stage'")
            throw java.io.IOException("非法阶段：$stage")
        }
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
    private suspend fun updateWarmthStageLabelUnlockedStrict(kbName: String, newStage: String) {
        if (newStage.isBlank()) return
        val stage = com.lovebrain.app.domain.StageCatalog.normalize(newStage) ?: run {
            com.lovebrain.app.util.L.w("updateWarmthStageLabelStrict 拒绝非白名单阶段：'$newStage'")
            throw java.io.IOException("非法阶段：$newStage")
        }
        val path = "understand/warmth.md"
        val warmth = readFileUnlockedFast(kbName, path)
        if (warmth.isBlank()) return
        val regex = Regex("(-\\s*阶段(?:标签)?[：:])([^\n]*)")
        val match = regex.find(warmth)
        if (match == null) {
            val header = "## 当前状态"
            val idx = warmth.indexOf(header)
            val updated = if (idx >= 0) {
                warmth.substring(0, idx + header.length) + "\n- 阶段标签：$stage" + warmth.substring(idx + header.length)
            } else {
                "- 阶段标签：$stage\n" + warmth
            }
            if (updated != warmth) writeFileUnlocked(kbName, path, updated)
            return
        }
        val oldValue = match.groupValues[2].trim()
        val oldStage = oldValue.split("；").firstOrNull()?.trim() ?: oldValue
        val newValue = if (oldStage.isNotBlank() && oldStage != stage) {
            "$stage；过去曾经是$oldStage"
        } else {
            stage
        }
        val updated = warmth.replaceFirst(regex, "${match.groupValues[1]}$newValue")
        if (updated != warmth) writeFileUnlocked(kbName, path, updated)
    }

    /** 无锁快速读取文件内容（不加 mutex，调用方持有锁） */
    private fun readFileUnlockedFast(kbName: String, relativePath: String): String {
        val dir = File(knowledgeRoot, kbName)
        val file = File(dir, relativePath)
        if (file.exists()) return file.readText()
        val oldPath = OLD_PATH_MAP[relativePath]
        if (oldPath != null) {
            val oldFile = File(dir, oldPath)
            if (oldFile.exists()) return oldFile.readText()
        }
        return ""
    }

    /**
     * 画像更新事务性写入——在单次 fileMutex.withLock 中执行全部操作。
     *
     * 返回 typed [ProfileTransactionResult]，替代模糊 Boolean。
     *
     * - 所有文件写入、向量写入、阶段更新、warmth 标签更新在同一锁内完成
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
            for ((path, _) in writeTargets) {
                val file = File(File(knowledgeRoot, kbName), path)
                backups[path] = (file.exists() to readFileUnlockedFast(kbName, path))
            }
            // warmth.md 即使不在 writeTargets 中，stage 变化时也会被 updateWarmthStageLabel 修改
            if (willChangeStage && "understand/warmth.md" !in backups) {
                val file = File(File(knowledgeRoot, kbName), "understand/warmth.md")
                backups["understand/warmth.md"] = (file.exists() to readFileUnlockedFast(kbName, "understand/warmth.md"))
            }
            // kb.json backup（stage 变化时 updateStageUnlockedStrict 会修改它）
            if (willChangeStage) {
                val metaFile = File(File(knowledgeRoot, kbName), "kb.json")
                backups["kb.json"] = (metaFile.exists() to (if (metaFile.exists()) metaFile.readText() else ""))
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
                        val dir = File(knowledgeRoot, kbName)
                        val file = File(dir, path)
                        file.parentFile?.mkdirs()
                        val (existed, oldContent) = existedAndContent
                        if (existed) {
                            atomicWriteText(file, oldContent)
                            // snapshot verification——恢复后内容必须等于 backup
                            if (file.readText() != oldContent) {
                                com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL rollback verification failed for $path (content mismatch)")
                                rollbackFailures.add(path)
                            }
                        } else {
                            // 原先不存在的文件——rollback 应删除，必须检查返回值
                            if (file.exists()) {
                                val deleted = file.delete()
                                if (!deleted) {
                                    com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL rollback delete failed for $path (delete returned false)")
                                    rollbackFailures.add(path)
                                }
                            }
                        }
                    } catch (rollbackErr: Exception) { // cancel-safe: 这里只有 java.io.File 读写（delete()/writeText()），协程取消不会从这里抛出
                        com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL rollback failed for $path", rollbackErr)
                        rollbackFailures.add(path)
                    }
                }
                // 最终 snapshot verification——原存在的文件必须存在且内容正确；原不存在的文件必须不存在
                // verification 自身的 I/O 异常（readText 抛异常、exists 抛异常等）
                // 必须加入 rollbackFailures，不得从 applyProfileUpdateAtomically 直接 throw 绕过 typed result
                for ((path, existedAndContent) in backups) {
                    try {
                        val file = File(File(knowledgeRoot, kbName), path)
                        val (existed, oldContent) = existedAndContent
                        if (existed) {
                            val existsNow = file.exists()
                            val contentMatches = if (existsNow) {
                                try {
                                    file.readText() == oldContent
                                } catch (verifyErr: Exception) {
                                    // readText 自身抛 I/O 异常 → 视为 verification 失败
                                    com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL verification read failed for $path", verifyErr)
                                    false
                                }
                            } else false
                            if (!existsNow || !contentMatches) {
                                if (path !in rollbackFailures) {
                                    com.lovebrain.app.util.L.e("applyProfileUpdateAtomically: CRITICAL post-rollback verification failed for $path")
                                    rollbackFailures.add(path)
                                }
                            }
                        } else {
                            val stillExists = try {
                                file.exists()
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

    /** 无锁快速读取向量（不加 mutex，调用方持有锁） */
    private fun readVectorUnlockedFast(kbName: String): Map<String, Int> {
        val warmth = readFileUnlockedFast(kbName, "understand/warmth.md")
        val result = mutableMapOf<String, Int>()
        for ((cn, en) in vectorDims) {
            val v = Regex("$cn[^：:]*[：:]\\s*(\\d+)").find(warmth)?.groupValues?.get(1)?.toIntOrNull()
            result[en] = v ?: 50
        }
        return result
    }


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
    suspend fun readCounselingAnalysisBlocks(kbName: String, count: Int): String = withContext(Dispatchers.IO) {
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

    suspend fun getCurrentTopic(kbName: String): String = withContext(Dispatchers.IO) {
        val content = readFile(kbName, "moment/topic.md")
        val raw = content.lines().firstOrNull()?.trim()?.substringAfter("正在聊：") ?: ""
        raw.substringBefore(" | key：").trim()
    }

    suspend fun setCurrentTopic(kbName: String, topicLabel: String) = withContext(Dispatchers.IO) {
        val time = com.lovebrain.app.util.TimeFmt.now()
        writeFile(kbName, "moment/topic.md", "- [$time] 正在聊：$topicLabel")
    }

    /** 读取 plan.md「## 进行中」分区的事项行（注入 prompt；已结束不注入） */
    suspend fun readPlanActive(kbName: String): String = withContext(Dispatchers.IO) {
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
    suspend fun getTopicAgeHours(kbName: String): Int = withContext(Dispatchers.IO) {
        val content = readFile(kbName, "moment/topic.md")
        val match = Regex("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]").find(content) ?: return@withContext 99
        val updated = com.lovebrain.app.util.TimeFmt.parse(match.groupValues[1])
        if (updated <= 0) return@withContext 99
        ((System.currentTimeMillis() - updated) / 3600_000).toInt()
    }

    // ═══════════ 累积操作状态（rotate/archive 幂等恢复） ═══════════

    /**
     * 归档操作状态——追踪 rotateTopic 的多步操作，支持中断恢复和幂等。
     *
     * 每一步完成后在 completedSteps 中追加，而不是每次从初始集合重新生成。
     * 异常中断后重启读取此状态，跳过已完成步骤，只执行剩余部分。
     */
    @Serializable
    private data class ArchiveOperationState(
        val operationId: String,       // 唯一操作 ID（基于内容哈希）
        val kbName: String,            // 目标知识库
        val timestamp: String,         // 操作时间戳
        val oldTopic: String,          // 旧话题名
        val contentHash: String,       // 输入内容哈希（防重复归档不同内容）
        val completedSteps: List<String> = emptyList()  // 已完成步骤（累积追加）
    )

    /** 归档操作步骤名称 */
    private object ArchiveStep {
        const val READ_INPUT = "read_input"           // 读取输入文件
        const val APPEND_ARCHIVE = "append_archive"     // 追加到 raw_topic.md
        const val INCREMENT_COUNT = "increment_count"   // topicCount + 1
        const val CLEAR_SOURCES = "clear_sources"       // 清空四个源文件
    }

    /** 操作状态文件路径 */
    private fun archiveOpFile(kbName: String): File =
        File(File(knowledgeRoot, kbName), "moment/.archive_op.json")

    /** 读取当前操作状态（无锁，调用方持有 fileMutex） */
    private fun readArchiveOpUnlocked(kbName: String): ArchiveOperationState? {
        val file = archiveOpFile(kbName)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<ArchiveOperationState>(file.readText())
        }.getOrNull()
    }

    /** 写入操作状态（无锁，调用方持有 fileMutex） */
    private fun writeArchiveOpUnlocked(kbName: String, state: ArchiveOperationState) {
        transactionUnlocked(kbName) {
            write("moment/.archive_op.json", json.encodeToString(ArchiveOperationState.serializer(), state))
        }
    }

    /** 删除操作状态（无锁，调用方持有 fileMutex） */
    private fun deleteArchiveOpUnlocked(kbName: String) {
        transactionUnlocked(kbName) { deleteAt("moment/.archive_op.json") }
    }

    /**
     * rotateTopic — 使用累积操作状态实现幂等和中断恢复。
     *
     * 步骤顺序：
     * 1. READ_INPUT: 读取 raw_chat/recent/raw_scene/scene 内容
     * 2. APPEND_ARCHIVE: 追加归档条目到 raw_topic.md
     * 3. INCREMENT_COUNT: topicCount + 1
     * 4. CLEAR_SOURCES: 清空四个源文件
     *
     * 恢复逻辑：
     * - 如果存在操作状态（无论是否完成），信任其中记录的已完成步骤，跳过它们
     * - CLEAR_SOURCES 会修改源文件，因此恢复时不能用内容 hash 验证
     * - 操作状态在创建时记录 contentHash，仅用于 operationId 唯一性
     * - 所有步骤完成后删除状态文件
     */
    suspend fun rotateTopic(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            // 读取已有操作状态（优先恢复）
            val existingOp = readArchiveOpUnlocked(kbName)

            // 读取输入内容
            val rawChat = readFile(kbName, "memory/raw_chat.md")
            val recent = readFile(kbName, "moment/recent.md")
            val rawScene = readFile(kbName, "memory/raw_scene.md")
            val scene = readFile(kbName, "moment/scene.md")

            // 如果存在未完成的操作状态，恢复它；否则创建新操作
            val opState = if (existingOp != null) {
                // 恢复已有操作状态——信任其中记录的已完成步骤
                // contentHash 仅用于 operationId 唯一性，不用于恢复时验证
                // （因为 CLEAR_SOURCES 会修改源文件，恢复时读取的内容可能已变化）
                existingOp
            } else {
                // 创建新操作状态
                val timestamp = com.lovebrain.app.util.TimeFmt.now()
                val oldTopic = getCurrentTopic(kbName)
                val inputHash = KbTextOps.contentHash(rawChat, recent, rawScene, scene, oldTopic)
                val newState = ArchiveOperationState(
                    operationId = "$timestamp-$inputHash",
                    kbName = kbName,
                    timestamp = timestamp,
                    oldTopic = oldTopic,
                    contentHash = inputHash
                )
                writeArchiveOpUnlocked(kbName, newState)
                newState
            }

            val completed = opState.completedSteps.toMutableList()
            val hasContent = rawChat.isNotBlank() || recent.isNotBlank() || rawScene.isNotBlank() || scene.isNotBlank()

            // R02/R03: 步骤 2 — 追加归档条目（幂等：检查是否已完成）
            // R03: 保留无法识别的原内容，标为 legacy，不因格式校验丢弃用户数据
            if (hasContent && ArchiveStep.APPEND_ARCHIVE !in completed) {
                val archiveEntry = buildString {
                    append("\n# [${opState.timestamp}] ${opState.oldTopic}\n\n")
                    append("## [${opState.timestamp}] 状态变化\n")
                    // 合并策略见 KbTextOps.mergeSceneEntries：无合法时间戳的行不丢弃，标为 legacy 留在尾部
                    append(KbTextOps.mergeSceneEntries(rawScene, scene))
                    append("\n")
                    append("### [${opState.timestamp}] 对话记录\n")
                    if (rawChat.isNotBlank()) append(rawChat.trim()).append("\n")
                    if (recent.isNotBlank()) append(recent.trim()).append("\n")
                }
                appendFileUnlocked(kbName, "memory/raw_topic.md", archiveEntry)
                completed.add(ArchiveStep.APPEND_ARCHIVE)
                writeArchiveOpUnlocked(kbName, opState.copy(completedSteps = completed.toList()))
            }

            // 步骤 3 — 增加计数（幂等：检查是否已完成）
            if (hasContent && ArchiveStep.INCREMENT_COUNT !in completed) {
                incrementTopicCountUnlocked(kbName)
                completed.add(ArchiveStep.INCREMENT_COUNT)
                writeArchiveOpUnlocked(kbName, opState.copy(completedSteps = completed.toList()))
            }

            // 步骤 4 — 清空源文件（幂等：检查是否已完成）
            if (ArchiveStep.CLEAR_SOURCES !in completed) {
                writeFileUnlocked(kbName, "memory/raw_chat.md", "")
                writeFileUnlocked(kbName, "memory/raw_scene.md", "")
                writeFileUnlocked(kbName, "moment/scene.md", "")
                writeFileUnlocked(kbName, "moment/recent.md", "")
                completed.add(ArchiveStep.CLEAR_SOURCES)
                writeArchiveOpUnlocked(kbName, opState.copy(completedSteps = completed.toList()))
            }

            // 操作完成 — 删除状态文件
            deleteArchiveOpUnlocked(kbName)
        }
    }

    suspend fun getLessonCount(kbName: String): Int = withContext(Dispatchers.IO) {
        val counted = fileMutex.withLock {
            transactionUnlocked(kbName) {
                runCatching {
                    json.decodeFromString<KnowledgeBase>(readTextAt("kb.json")).topicCount
                }.getOrDefault(0)
            }
        }
        if (counted > 0) return@withContext counted
        // 兼容旧知识库：kb.json 还没有计数时，从 raw_topic.md 回填一次并持久化
        val content = readFile(kbName, "memory/raw_topic.md")
        val regexCount = Regex("^## \\[", RegexOption.MULTILINE).findAll(content).count()
        if (regexCount > 0) {
            fileMutex.withLock {
                transactionUnlocked(kbName) { updateMeta { kb -> kb.copy(topicCount = regexCount) } }
            }
        }
        regexCount
    }

    /** 话题归档计数 +1（写入 kb.json 的 topicCount 字段） */
    private suspend fun incrementTopicCount(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            incrementTopicCountUnlocked(kbName)
        }
    }

    /** incrementTopicCount 的无锁核心：调用方必须已持有文件互斥锁 */
    private suspend fun incrementTopicCountUnlocked(kbName: String) {
        transactionUnlocked(kbName) {
            updateMeta { kb -> kb.copy(topicCount = kb.topicCount + 1, updatedAt = isoNow()) }
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
        /** 回复链路实际读取的知识文件——内容变化即构成一次新的可冻结修订（） */
        private val REVISION_INPUT_PATHS = listOf(
            "understand/me.md", "understand/her.md", "understand/warmth.md", "understand/style.md",
            "moment/topic.md", "moment/scene.md", "moment/recent.md", "moment/plan.md",
            "memory/lessons.md", "memory/raw_topic.md", "memory/raw_scene.md", "memory/raw_chat.md"
        )

        private const val BACKUP_MAX_COUNT = 7      // 每个知识库保留最近 7 份备份
        private const val BACKUP_INTERVAL_MS = 12 * 3600_000L  // 两次备份间隔 ≥ 12 小时
        // 备份目录名 = <库名>_<yyyyMMdd>_<HHmm>：锚定实际命名去时间戳还原库名作分组键；
        // 不匹配命名 = 整名为键（自成一组永不修剪，保守保留）
        private val BACKUP_TS_SUFFIX = Regex("_\\d{8}_\\d{4}$")
        internal fun backupGroupKey(name: String): String = BACKUP_TS_SUFFIX.replace(name, "")

        // 旧→新路径映射：readFile 与 KbEditActivity fallback 共用（ 去重，改这里一处即可）
        val OLD_PATH_MAP = mapOf(
            "understand/me.md" to "global/me.md",
            "understand/her.md" to "global/her.md",
            "understand/warmth.md" to "global/status.md",
            "moment/recent.md" to "recent/chatlog.md",
            "memory/lessons.md" to "general/lessons.md"
        )
    }

    /** 从 assets/schema/ 加载知识库初始化模板（标题骨架 = 单一数据源） */
    private fun loadSchema(name: String): String {
        return readAsset(com.lovebrain.app.domain.AssetRegistry.schema(name))
    }
}
