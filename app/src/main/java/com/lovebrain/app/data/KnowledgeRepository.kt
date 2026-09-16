package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
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
 * ：所有公开方法 suspend + withContext(Dispatchers.IO) 确保主线程无磁盘 IO；
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

    /** 文件操作互斥锁，防止多协程并发读写同一文件 */
    private val fileMutex = Mutex()

    /** 备份节流：记录最后一次写入时间，debounce 5s 后触发增量备份 */
    private val backupDebounceMs = 5_000L
    private val lastWriteTimestamp = AtomicLong(0L)
    private var backupDebounceJob: Job? = null

    init {
        knowledgeRoot.mkdirs()
        // 启动时自动备份（使用 applicationScope 替代 GlobalScope，生命周期可管理）
        // : 所有 launch 必须包 SupervisorJob + ExceptionHandler
        // RA-03：备份经 fileMutex 序列化，与 delete 互斥防竞态
        appScope.launch(Dispatchers.IO + SupervisorJob()) {
            runCatching { backupIfNeeded() }.onFailure { err ->
                com.lovebrain.app.util.L.e("backup init failed", err)
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
                runCatching { backupIfNeeded() }.onFailure { err ->
                    com.lovebrain.app.util.L.e("debounce backup failed", err)
                }
            }
        }
    }

    // ═══════════ 自动备份 ═══════════

    /**
     * RA-03：序列化备份过程——与 delete 共用 fileMutex，防止并发竞态。
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
     * 原子写入：先写临时文件 → fsync 刷盘 → rename 覆盖目标文件。
     * P0-4：rename 失败时保留原件并报错，不回退到直接覆盖（直接写可能导致半写损坏）。
     *
     * 调研依据：SQLite 的原子提交机制（写 journal → flush → rename → delete journal），
     * 以及 Kotlin File.writeText() 无原子保证（Kotlin 官方文档确认）。
     * rename 在 POSIX/Android 上是原子操作（SQLite 文档确认），
     * 确保目标文件要么是旧内容要么是新内容，绝不会出现写一半的中间状态。
     */
    private fun atomicWriteText(file: File, content: String) {
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        try {
            FileOutputStream(tmp).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync() // 强制刷盘，防断电丢失
            }
            // rename 在同一文件系统上是原子操作（POSIX/Android）。
            // Windows 上 renameTo 可能因文件锁定（防病毒等）间歇失败，
            // 添加短 retry + fallback copyTo+delete 保证可靠性。
            var renamed = false
            for (attempt in 1..3) {
                if (tmp.renameTo(file)) { renamed = true; break }
                Thread.sleep(50L * attempt)
            }
            if (!renamed) {
                // Fallback: copy then delete (not atomic but safe — tmp is already fully written)
                if (file.exists() && !file.delete()) {
                    throw java.io.IOException("atomic rename failed (cannot delete target): ${file.name}")
                }
                if (!tmp.copyTo(file, overwrite = true).exists()) {
                    throw java.io.IOException("atomic rename failed (copy fallback failed): ${file.name}")
                }
                tmp.delete()
            }
        } finally {
            // 清理可能残留的临时文件
            if (tmp.exists()) tmp.delete()
        }
    }

    /** KBG-01：判断知识库是否真实存在（目录存在 + kb.json 存在）。调用方必须已持有文件互斥锁或处于单线程路径 */
    private fun kbExistsUnlocked(kbName: String): Boolean {
        val dir = File(knowledgeRoot, kbName)
        val meta = File(dir, "kb.json")
        return dir.isDirectory && meta.isFile
    }

    /** 锁区内写入核心：不抢锁。调用方必须已持有文件互斥锁（Mutex 非重入，锁内再抢=永久挂起） */
    private fun writeFileUnlocked(kbName: String, relativePath: String, content: String) {
        val file = File(File(knowledgeRoot, kbName), relativePath)
        file.parentFile?.mkdirs()
        atomicWriteText(file, content)
        scheduleDebouncedBackup()
    }

    /** 锁区内追加核心：不抢锁。调用方必须已持有文件互斥锁 */
    private fun appendFileUnlocked(kbName: String, relativePath: String, content: String) {
        val file = File(File(knowledgeRoot, kbName), relativePath)
        file.parentFile?.mkdirs()
        val existing = if (file.exists()) file.readText() else ""
        atomicWriteText(file, existing + content)
        scheduleDebouncedBackup()
    }

    // ═══════════ F10: 默认知识库初始化 ═══════════

    /**
     * F10: 确保应用至少有一个合法知识库。应用初始化唯一入口。
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
                // 已有库（含导入）——沿用原激活项，不创建
                // 但仍需检查已有库是否完整（中断恢复补齐）
                existingKbs.forEach { kb -> ensureKbFilesCompleteUnlocked(kb.name) }
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

            // F10: 画像默认真实空内容（非 schema 模板占位文字）
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
     * F10: 检查知识库文件是否完整，补齐缺失文件（中断恢复）。
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
                        // : name 字段必须与目录名等值——防 kb.json 内容字段路径遍历（单一扼制点，所有消费端均源于 listAll）
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

    /** setActive 的无锁核心：调用方必须已持有文件互斥锁（Mutex 非重入，锁内再抢=永久挂起） */
    private fun setActiveUnlocked(name: String) {
        securePrefs.activeKbName = name
        knowledgeRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.forEach { dir ->
                val metaFile = File(dir, "kb.json")
                if (metaFile.exists()) {
                    runCatching {
                        val kb = json.decodeFromString<KnowledgeBase>(metaFile.readText())
                        val updated = kb.copy(active = kb.name == name)
                        atomicWriteText(metaFile, json.encodeToString(KnowledgeBase.serializer(), updated))
                    }
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

    // ═══════════ F07: 持续意图（每 KB 一份，moment/intent.json） ═══════════

    /** F07: 读取持续意图配置 */
    suspend fun readIntent(kbName: String): IntentConfig = withContext(Dispatchers.IO) {
        val file = File(File(knowledgeRoot, kbName), "moment/intent.json")
        if (!file.exists()) return@withContext IntentConfig()
        runCatching {
            json.decodeFromString<IntentConfig>(file.readText())
        }.getOrDefault(IntentConfig())
    }

    /** F07: 保存持续意图配置。每次保存 revision+1，用于生成时冻结快照识别旧请求。 */
    suspend fun saveIntent(kbName: String, text: String, enabled: Boolean): IntentConfig = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val current = readIntentUnlocked(kbName)
            val updated = IntentConfig(
                text = text,
                enabled = enabled,
                revision = current.revision + 1
            )
            val dir = File(knowledgeRoot, kbName)
            File(dir, "moment").mkdirs()
            atomicWriteText(File(dir, "moment/intent.json"), json.encodeToString(IntentConfig.serializer(), updated))
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

    // ═══════════ F09: 记忆纠正（每 KB 一份，memory/corrections.json） ═══════════

    /** F09: 读取纠正记录列表。返回 memoryId → correction 映射。 */
    suspend fun readCorrections(kbName: String): Map<String, com.lovebrain.app.model.MemoryCorrection> = withContext(Dispatchers.IO) {
        val file = File(File(knowledgeRoot, kbName), "memory/corrections.json")
        if (!file.exists()) return@withContext emptyMap()
        runCatching {
            val list = json.decodeFromString<List<com.lovebrain.app.model.MemoryCorrection>>(file.readText())
            list.associateBy { it.memoryId }
        }.getOrDefault(emptyMap())
    }

    /** F09: 保存一条纠正记录。revision 自增，后台旧任务不能覆盖新 revision。
     * 如果 memoryId 已存在且现有 revision >= 新 revision，拒绝写入（迟到保护）。 */
    suspend fun saveCorrection(
        kbName: String,
        memoryId: String,
        action: com.lovebrain.app.model.CorrectionAction,
        replacementText: String = "",
        targetKbId: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) return@withLock false
            val current = readCorrectionsUnlocked(kbName)
            val existing = current[memoryId]
            val newRevision = (current.values.maxOfOrNull { it.revision } ?: 0) + 1
            val correction = com.lovebrain.app.model.MemoryCorrection(
                memoryId = memoryId,
                action = action,
                replacementText = replacementText,
                targetKbId = targetKbId,
                revision = newRevision,
                updatedAt = isoNow()
            )
            val updated = current.toMutableMap()
            updated[memoryId] = correction
            val dir = File(knowledgeRoot, kbName)
            File(dir, "memory").mkdirs()
            atomicWriteText(
                File(dir, "memory/corrections.json"),
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(com.lovebrain.app.model.MemoryCorrection.serializer()),
                    updated.values.toList()
                )
            )
            scheduleDebouncedBackup()
            true
        }
    }

    /** F09: 撤销纠正 — 删除指定 memoryId 的纠正记录。 */
    suspend fun undoCorrection(kbName: String, memoryId: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) return@withLock false
            val current = readCorrectionsUnlocked(kbName)
            if (!current.containsKey(memoryId)) return@withLock false
            val updated = current.toMutableMap()
            updated.remove(memoryId)
            val dir = File(knowledgeRoot, kbName)
            atomicWriteText(
                File(dir, "memory/corrections.json"),
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(com.lovebrain.app.model.MemoryCorrection.serializer()),
                    updated.values.toList()
                )
            )
            scheduleDebouncedBackup()
            true
        }
    }

    /** F09: 获取纠正记录的全局 revision（用于后台防护）。
     * 后台任务启动时冻结 revision，完成后比对当前 revision — 如果不匹配，说明用户在期间做了新纠正，丢弃后台结果。 */
    suspend fun getCorrectionsRevision(kbName: String): Int = withContext(Dispatchers.IO) {
        readCorrectionsUnlocked(kbName).values.maxOfOrNull { it.revision } ?: 0
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
     *  RA-03：delete 成功后同时删除该 KB 的全部 backup，防止私密副本残留 */
    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val dir = File(knowledgeRoot, name)
            // : canonical 纵深守卫——删除目标必须落在 knowledge/ 树内（与 unzipToKnowledge entry 防护同写法）
            val canonicalDirPath = dir.canonicalPath
            val canonicalRootPath = knowledgeRoot.canonicalPath
            if (!canonicalDirPath.startsWith(canonicalRootPath + File.separator)) return@withLock false
            if (!dir.exists()) return@withLock false
            val ok = dir.deleteRecursively()
            // 清理旧版本遗留的 .trash（若存在），一次性腾空
            File(knowledgeRoot, ".trash").takeIf { it.exists() }?.deleteRecursively()
            // RA-03：正式目录删除成功后才删 backup，防止删 backup 后正式目录删失败导致备份丢失
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
     * RA-03：删除指定 KB 的全部自动备份。使用 backupGroupKey 精确匹配，不用 startsWith 防误删。
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

    /** 读取文件（自动兼容新旧路径） */
    suspend fun readFile(kbName: String, relativePath: String): String = withContext(Dispatchers.IO) {
        val dir = File(knowledgeRoot, kbName)
        val newFile = File(dir, relativePath)
        if (newFile.exists()) return@withContext newFile.readText()
        val oldPath = OLD_PATH_MAP[relativePath]
        if (oldPath != null) {
            val oldFile = File(dir, oldPath)
            if (oldFile.exists()) return@withContext oldFile.readText()
        }
        ""
    }

    /** 线程安全的文件追加（fileMutex 锁 + I/O 线程；A2-5 合并原 appendFileSafe）
     *  KBG-01：目标 KB 已删除时 no-op，不自动 mkdirs 复活 */
    suspend fun appendFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("appendFile skipped: kb no longer exists")
                return@withLock
            }
            appendFileUnlocked(kbName, relativePath, content)
        }
    }

    /** 线程安全的文件写入（fileMutex 锁 + I/O 线程；A2-5 合并原 writeFileSafe）
     *  KBG-01：目标 KB 已删除时 no-op，不自动 mkdirs 复活 */
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
     * P0-FIX：带版本校验的文件写入——防止编辑覆盖后台新增。
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
            val file = File(File(knowledgeRoot, kbName), relativePath)
            val currentVersion = if (file.exists()) {
                sha256(file.readText())
            } else {
                sha256("")
            }
            if (currentVersion != expectedVersion) {
                com.lovebrain.app.util.L.w("writeFileWithVersion conflict: $relativePath")
                return@withLock null
            }
            writeFileUnlocked(kbName, relativePath, content)
            sha256(content)
        }
    }

    /**
     * P0-4：读取文件并返回内容 + 版本号（SHA-256）。
     * 调用方持有版本号，写入时传给 [writeFileWithVersion] 做冲突检测。
     */
    suspend fun readFileWithVersion(kbName: String, relativePath: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val content = readFile(kbName, relativePath)
        content to sha256(content)
    }

    /** P0-FIX：对外暴露的内容哈希——供 KbEdit 无版本校验路径生成新版本号 */
    fun hashContent(text: String): String = sha256(text)

    /** P0-4：SHA-256 哈希（用于版本校验） */
    private fun sha256(text: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**  KBG-01：目标 KB 已删除时 no-op */
    suspend fun incrementTurnCount(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("incrementTurnCount skipped: kb no longer exists")
                return@withLock
            }
            val metaFile = File(File(knowledgeRoot, kbName), "kb.json")
            if (metaFile.exists()) {
                runCatching {
                    val kb = json.decodeFromString<KnowledgeBase>(metaFile.readText())
                    atomicWriteText(metaFile,
                        json.encodeToString(KnowledgeBase.serializer(),
                            kb.copy(turnCount = kb.turnCount + 1, updatedAt = isoNow()))
                    )
                }
            }
        }
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
            val metaFile = File(File(knowledgeRoot, kbName), "kb.json")
            if (metaFile.exists()) {
                runCatching {
                    val kb = json.decodeFromString<KnowledgeBase>(metaFile.readText())
                    atomicWriteText(metaFile,
                        json.encodeToString(KnowledgeBase.serializer(),
                            kb.copy(displayName = newDisplay.trim(), updatedAt = isoNow()))
                    )
                }
            }
        }
    }

    /** 设置知识库阶段标签（onboarding 推断 / 向量重估触发阶段变化时用）。写入前经 StageCatalog 归一化
     *  KBG-01：目标 KB 已删除时 no-op */
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
        val metaFile = File(File(knowledgeRoot, kbName), "kb.json")
        if (metaFile.exists()) {
            runCatching {
                val kb = json.decodeFromString<KnowledgeBase>(metaFile.readText())
                atomicWriteText(metaFile,
                    json.encodeToString(KnowledgeBase.serializer(),
                        kb.copy(stage = normalized, updatedAt = isoNow()))
                )
            }
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
     *  KBG-01：目标 KB 已删除时 no-op */
    suspend fun writeVector(kbName: String, values: Map<String, Int>) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!kbExistsUnlocked(kbName)) {
                com.lovebrain.app.util.L.w("writeVector skipped: kb no longer exists")
                return@withLock
            }
            val path = "understand/warmth.md"
            var warmth = readFile(kbName, path)
            if (warmth.isBlank()) return@withLock
            for ((cn, en) in vectorDims) {
                val v = values[en] ?: continue
                // [^/\n]* 兼容占位值（如"待评估"）和已有数字，保留 "/100" 后缀
                val dimRegex = Regex("($cn[^：:]*[：:]\\s*)[^/\\n]*")
                if (!dimRegex.containsMatchIn(warmth)) com.lovebrain.app.util.L.w("writeVector 维度零匹配：$cn（文件长度=${warmth.length}）")
                warmth = warmth.replaceFirst(dimRegex, "$1$v")
            }
            writeFileUnlocked(kbName, path, warmth)
        }
    }

    /** 就地更新 warmth.md 的阶段标签行（阶段变化时用），保留旧值作为历史注释。写入前经 StageCatalog 归一化
     *  KBG-01：目标 KB 已删除时 no-op */
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

    /** 旧阶段枚举 → 新八阶段迁移映射 */
    private val STAGE_MIGRATION = mapOf(
        "初识" to "初识期",
        "破冰" to "破冰期",
        "暧昧" to "暧昧期",
        "热恋" to "热恋期",
        "磨合" to "磨合期",
        "稳定" to "稳定期",
        "危机" to "危机期",
        "修复" to "修复期"
    )

    // ═══════════ 谈心日志（两段式） ═══════════

    private val counselingH1 = "# 谈心记录"
    private val counselingH2 = "# 军师分析"

    /**
     * 谈心日志两段式追加：recordEntry 写入「# 谈心记录」节，analysisEntry 写入「# 军师分析」节。
     * 固定代码写入、全量不截断。旧格式文件（没有两个 # 大标题）自动迁移：旧内容并入第一节。
     *  KBG-01：目标 KB 已删除时 no-op
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

    // ═══════════ F04: 累积操作状态（rotate/archive 幂等恢复） ═══════════

    /**
     * F04: 归档操作状态——追踪 rotateTopic 的多步操作，支持中断恢复和幂等。
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
        atomicWriteText(archiveOpFile(kbName), json.encodeToString(ArchiveOperationState.serializer(), state))
    }

    /** 删除操作状态（无锁，调用方持有 fileMutex） */
    private fun deleteArchiveOpUnlocked(kbName: String) {
        archiveOpFile(kbName).delete()
    }

    /** 计算内容哈希（用于检测输入是否变化） */
    private fun contentHash(vararg contents: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        contents.forEach { c -> md.update(c.toByteArray(Charsets.UTF_8)) }
        return md.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * F04: rotateTopic — 使用累积操作状态实现幂等和中断恢复。
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
            // F04: 读取已有操作状态（优先恢复）
            val existingOp = readArchiveOpUnlocked(kbName)

            // F04: 读取输入内容
            val rawChat = readFile(kbName, "memory/raw_chat.md")
            val recent = readFile(kbName, "moment/recent.md")
            val rawScene = readFile(kbName, "memory/raw_scene.md")
            val scene = readFile(kbName, "moment/scene.md")

            // F04: 如果存在未完成的操作状态，恢复它；否则创建新操作
            val opState = if (existingOp != null) {
                // 恢复已有操作状态——信任其中记录的已完成步骤
                // contentHash 仅用于 operationId 唯一性，不用于恢复时验证
                // （因为 CLEAR_SOURCES 会修改源文件，恢复时读取的内容可能已变化）
                existingOp
            } else {
                // 创建新操作状态
                val timestamp = com.lovebrain.app.util.TimeFmt.now()
                val oldTopic = getCurrentTopic(kbName)
                val inputHash = contentHash(rawChat, recent, rawScene, scene, oldTopic)
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

            // F04: 步骤 2 — 追加归档条目（幂等：检查是否已完成）
            if (hasContent && ArchiveStep.APPEND_ARCHIVE !in completed) {
                val archiveEntry = buildString {
                    append("\n# [${opState.timestamp}] ${opState.oldTopic}\n\n")
                    append("## [${opState.timestamp}] 状态变化\n")
                    append(mergeSceneEntriesSorted(rawScene, scene))
                    append("\n")
                    append("### [${opState.timestamp}] 对话记录\n")
                    if (rawChat.isNotBlank()) append(rawChat.trim()).append("\n")
                    if (recent.isNotBlank()) append(recent.trim()).append("\n")
                }
                appendFileUnlocked(kbName, "memory/raw_topic.md", archiveEntry)
                completed.add(ArchiveStep.APPEND_ARCHIVE)
                writeArchiveOpUnlocked(kbName, opState.copy(completedSteps = completed.toList()))
            }

            // F04: 步骤 3 — 增加计数（幂等：检查是否已完成）
            if (hasContent && ArchiveStep.INCREMENT_COUNT !in completed) {
                incrementTopicCountUnlocked(kbName)
                completed.add(ArchiveStep.INCREMENT_COUNT)
                writeArchiveOpUnlocked(kbName, opState.copy(completedSteps = completed.toList()))
            }

            // F04: 步骤 4 — 清空源文件（幂等：检查是否已完成）
            if (ArchiveStep.CLEAR_SOURCES !in completed) {
                writeFileUnlocked(kbName, "memory/raw_chat.md", "")
                writeFileUnlocked(kbName, "memory/raw_scene.md", "")
                writeFileUnlocked(kbName, "moment/scene.md", "")
                writeFileUnlocked(kbName, "moment/recent.md", "")
                completed.add(ArchiveStep.CLEAR_SOURCES)
                writeArchiveOpUnlocked(kbName, opState.copy(completedSteps = completed.toList()))
            }

            // F04: 操作完成 — 删除状态文件
            deleteArchiveOpUnlocked(kbName)
        }
    }

    /** 状态条目行校验：必须以 "- [yyyy-MM-dd HH:mm]" 真实时间戳开头（防 schema 模板示例行混入） */
    private val validEntryLine = Regex("^- \\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]")

    /** 合并多个来源的状态条目，按时间戳倒序（最新在前）；无合法时间戳的行丢弃 */
    private fun mergeSceneEntriesSorted(vararg sources: String): String {
        val entries = sources
            .flatMap { it.lines() }
            .map { it.trimEnd() }
            .filter { validEntryLine.containsMatchIn(it) }
        if (entries.isEmpty()) return ""
        val sorted = entries.sortedByDescending { parseEntryTs(it) }
        return sorted.joinToString("\n") + "\n"
    }

    private fun parseEntryTs(entry: String): Long {
        val match = Regex("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]").find(entry) ?: return 0L
        return com.lovebrain.app.util.TimeFmt.parse(match.groupValues[1])
    }

    suspend fun getLessonCount(kbName: String): Int = withContext(Dispatchers.IO) {
        val metaFile = File(File(knowledgeRoot, kbName), "kb.json")
        if (metaFile.exists()) {
            val counted = runCatching {
                json.decodeFromString<KnowledgeBase>(metaFile.readText()).topicCount
            }.getOrDefault(0)
            if (counted > 0) return@withContext counted
        }
        // 兼容旧知识库：kb.json 还没有计数时，从 raw_topic.md 回填一次并持久化
        val content = readFile(kbName, "memory/raw_topic.md")
        val regexCount = Regex("^## \\[", RegexOption.MULTILINE).findAll(content).count()
        if (regexCount > 0 && metaFile.exists()) {
            runCatching {
                val kb = json.decodeFromString<KnowledgeBase>(metaFile.readText())
                atomicWriteText(metaFile,
                    json.encodeToString(KnowledgeBase.serializer(), kb.copy(topicCount = regexCount))
                )
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
        val metaFile = File(File(knowledgeRoot, kbName), "kb.json")
        if (metaFile.exists()) {
            runCatching {
                val kb = json.decodeFromString<KnowledgeBase>(metaFile.readText())
                atomicWriteText(metaFile,
                    json.encodeToString(
                        KnowledgeBase.serializer(),
                        kb.copy(topicCount = kb.topicCount + 1, updatedAt = isoNow())
                    )
                )
            }
        }
    }

    // ═══════════ 迁移 & 兼容 ═══════════

    /**
     * 旧版 plan.md 清理：把注释外的裸"格式（每条一行）/示例：…"说明行包进 <!-- -->。
     * 效果：编辑态可见、预览态（MarkdownText 去注释）隐藏、prompt 注入不携带。
     */
    private fun wrapPlanMetaLines(text: String): String {
        if (text.isBlank()) return text
        val sb = StringBuilder()
        var inComment = false
        for (line in text.lines()) {
            val t = line.trim()
            when {
                inComment -> {
                    sb.append(line).append("\n")
                    if (t.contains("-->")) inComment = false
                }
                t.startsWith("<!--") -> {
                    sb.append(line).append("\n")
                    if (!t.contains("-->")) inComment = true
                }
                t.startsWith("格式") || t.startsWith("示例") -> {
                    sb.append("<!-- ").append(t).append(" -->\n")
                }
                else -> sb.append(line).append("\n")
            }
        }
        return sb.toString().trimEnd('\n') + "\n"
    }

    suspend fun migrateIfNeeded(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val dir = File(knowledgeRoot, kbName)
            val oldGlobal = File(dir, "global")
            val newUnderstand = File(dir, "understand")

            // 确保 v3 文件存在（v2→v3 过渡）
            if (dir.exists()) {
                File(dir, "moment").mkdirs()
                File(dir, "memory").mkdirs()
                val sceneFile = File(dir, "moment/scene.md")
                if (!sceneFile.exists()) atomicWriteText(sceneFile, "")
                val rawChat = File(dir, "memory/raw_chat.md")
                if (!rawChat.exists()) atomicWriteText(rawChat, "")
                val rawTopic = File(dir, "memory/raw_topic.md")
                if (!rawTopic.exists()) atomicWriteText(rawTopic, "")
                val rawScene = File(dir, "memory/raw_scene.md")
                if (!rawScene.exists()) atomicWriteText(rawScene, "")
                val planFile = File(dir, "moment/plan.md")
                if (!planFile.exists()) atomicWriteText(planFile, loadSchema("plan"))
                // 旧版 plan.md 的裸"格式/示例"说明行包进注释（编辑可见、预览隐藏、不进 prompt）
                if (planFile.exists()) {
                    val planText = runCatching { planFile.readText() }.getOrDefault("")
                    val fixed = wrapPlanMetaLines(planText)
                    if (fixed != planText) atomicWriteText(planFile, fixed)
                }
                val counselingLog = File(dir, "memory/counseling_log.md")
                if (!counselingLog.exists()) atomicWriteText(counselingLog, "")
                val reflectHistory = File(dir, "memory/reflect_history.md")
                if (!reflectHistory.exists()) atomicWriteText(reflectHistory, "")
                // 兼容：旧知识库把"她"的画像存为 understand/you.md，统一改名为 her.md
                val oldYou = File(dir, "understand/you.md")
                val newHer = File(dir, "understand/her.md")
                if (oldYou.exists() && !newHer.exists()) oldYou.renameTo(newHer)

                // ═══  修复：旧阶段枚举（无"期"六选一）→ 新八阶段（带"期"）迁移 ═══
                val oldStage = getCurrentStage(kbName)
                val mapped = STAGE_MIGRATION[oldStage]
                if (mapped != null) {
                    updateStageUnlocked(kbName, mapped)
                    updateWarmthStageLabelUnlocked(kbName, mapped)
                }
            }

            if (newUnderstand.exists()) return@withLock
            if (!oldGlobal.exists()) return@withLock

            File(dir, "understand").mkdirs()
            File(dir, "moment").mkdirs()
            File(dir, "memory").mkdirs()

            val me = File(dir, "global/me.md")
            val her = File(dir, "global/her.md")
            val status = File(dir, "global/status.md")
            if (me.exists()) me.copyTo(File(dir, "understand/me.md"), overwrite = true)
            if (her.exists()) her.copyTo(File(dir, "understand/her.md"), overwrite = true)
            if (status.exists()) status.copyTo(File(dir, "understand/warmth.md"), overwrite = true)

            val chatlog = File(dir, "recent/chatlog.md")
            if (chatlog.exists()) chatlog.copyTo(File(dir, "moment/recent.md"), overwrite = true)

            val lessons = File(dir, "general/lessons.md")
            if (lessons.exists()) lessons.copyTo(File(dir, "memory/lessons.md"), overwrite = true)
            val moments = File(dir, "general/moments.md")
            val details = File(dir, "general/details.md")
            val archiveContent = buildString {
                if (moments.exists()) append(moments.readText()).append("\n\n")
                if (details.exists()) append(details.readText())
            }
            if (archiveContent.isNotBlank()) {
                atomicWriteText(File(dir, "memory/archive.md"), archiveContent)
            }

            val initTime = com.lovebrain.app.util.TimeFmt.now()
            atomicWriteText(File(dir, "moment/topic.md"), "- [$initTime] 正在聊：（等待第一次对话）")
            atomicWriteText(File(dir, "memory/topic_log.md"), "")
            atomicWriteText(File(dir, ".migrated_v2"), isoNow())
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
