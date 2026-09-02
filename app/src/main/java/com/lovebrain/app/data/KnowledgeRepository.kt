package com.lovebrain.app.data

import android.content.Context
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
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
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
        appScope.launch(Dispatchers.IO + SupervisorJob()) {
            runCatching { backupIfNeededAsync() }.onFailure { err ->
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
                runCatching { backupIfNeededAsync() }.onFailure { err ->
                    com.lovebrain.app.util.L.e("debounce backup failed", err)
                }
            }
        }
    }

    // ═══════════ 自动备份 ═══════════

    /**
     * 自动备份：如果距上次备份超过 12 小时，复制所有知识库到 .backup/目录。
     * 供 init 块调用（非 suspend），在 IO 线程异步执行。
     */
    private fun backupIfNeededAsync() {
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
            // rename 在同一文件系统上是原子操作
            if (!tmp.renameTo(file)) {
                // 某些 Android 设备 rename 可能失败（跨挂载点等），fallback 到直接写
                file.writeText(content)
            }
        } finally {
            // 清理可能残留的临时文件
            if (tmp.exists()) tmp.delete()
        }
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
            securePrefs.activeKbName = name
            listAll().forEach { kb ->
                val metaFile = File(File(knowledgeRoot, kb.name), "kb.json")
                if (metaFile.exists()) {
                    runCatching {
                        val updated = kb.copy(active = kb.name == name)
                        atomicWriteText(metaFile, json.encodeToString(KnowledgeBase.serializer(), updated))
                    }
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

    /** 删除知识库（/：物理删除——UI 已有确认步骤，不再进 .trash 永久残留隐私数据） */
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
            if (ok && securePrefs.activeKbName == name) {
                securePrefs.activeKbName = listAll().firstOrNull()?.name ?: ""
            }
            ok
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

    /** 线程安全的文件追加（fileMutex 锁 + I/O 线程；A2-5 合并原 appendFileSafe） */
    suspend fun appendFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            appendFileUnlocked(kbName, relativePath, content)
        }
    }

    /** 线程安全的文件写入（fileMutex 锁 + I/O 线程；A2-5 合并原 writeFileSafe） */
    suspend fun writeFile(kbName: String, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            writeFileUnlocked(kbName, relativePath, content)
        }
    }

    suspend fun incrementTurnCount(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
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

    /** 设置知识库阶段标签（onboarding 推断 / 向量重估触发阶段变化时用）。写入前经 StageCatalog 归一化 */
    suspend fun updateStage(kbName: String, stage: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
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

    /** 就地更新 warmth.md 的五维状态向量数值 */
    suspend fun writeVector(kbName: String, values: Map<String, Int>) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
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

    /** 就地更新 warmth.md 的阶段标签行（阶段变化时用），保留旧值作为历史注释。写入前经 StageCatalog 归一化 */
    suspend fun updateWarmthStageLabel(kbName: String, newStage: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
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
     */
    suspend fun appendCounselingEntries(kbName: String, recordEntry: String, analysisEntry: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
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

    suspend fun rotateTopic(kbName: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val timestamp = com.lovebrain.app.util.TimeFmt.now()

            val oldTopic = getCurrentTopic(kbName)
            // B = 对话暂存 + 最近两句
            val rawChat = readFile(kbName, "memory/raw_chat.md")
            val recent = readFile(kbName, "moment/recent.md")
            // A = 状态暂存 + 此刻状态
            val rawScene = readFile(kbName, "memory/raw_scene.md")
            val scene = readFile(kbName, "moment/scene.md")

            val hasContent = rawChat.isNotBlank() || recent.isNotBlank() || rawScene.isNotBlank() || scene.isNotBlank()
            if (hasContent) {
                val archiveEntry = buildString {
                    append("\n# [$timestamp] $oldTopic\n\n")
                    // 状态变化（H2）：合并两个来源，按时间倒序（最新在前），过滤无效行
                    append("## [$timestamp] 状态变化\n")
                    append(mergeSceneEntriesSorted(rawScene, scene))
                    append("\n")
                    // 对话记录（H3）：保持时间正序（暂存在前、最近在尾，天然顺序）
                    append("### [$timestamp] 对话记录\n")
                    if (rawChat.isNotBlank()) append(rawChat.trim()).append("\n")
                    if (recent.isNotBlank()) append(recent.trim()).append("\n")
                }
                appendFileUnlocked(kbName, "memory/raw_topic.md", archiveEntry)
                incrementTopicCountUnlocked(kbName)
            }

            // 清空四个源文件，为新话题腾空间（话题与 key 由调用方写入）
            writeFileUnlocked(kbName, "memory/raw_chat.md", "")
            writeFileUnlocked(kbName, "memory/raw_scene.md", "")
            writeFileUnlocked(kbName, "moment/scene.md", "")
            writeFileUnlocked(kbName, "moment/recent.md", "")
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
