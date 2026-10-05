package com.lovebrain.app.data

/**
 * 仓库交给谈心日志格用的能力，四样。
 *
 * 与记忆格/归档格同一纪律：不给 Mutex（锁由仓库在外层套）、不给 File 构造、
 * 不给落盘实现——`StorageBoundaryOwnershipTest` 那把棘轮会盯着这三件事。
 *
 * `read` 是过 canonical 守门的读（与公开读同源），`writeUnlocked` 是回到仓库
 * 唯一写链的写出口（只读 schema 拒绝与备份节流都在它下游）。
 */
internal interface CounselingStorage {
    fun note(message: String)

    fun kbExists(kbName: String): Boolean

    /** 过 canonical 守门的读：越界、非法、不存在都得到空串 */
    fun read(kbName: String, relativePath: String): String

    /** 锁区内写入核心（调用方已持有 fileMutex）；只读 schema 拒绝在仓库那一侧 */
    fun writeUnlocked(kbName: String, relativePath: String, content: String)
}

/**
 * 第5节第3条 后续拆出的谈心日志格：两段式追加 + 实际发送记录 upsert + 分析节读取。
 *
 * 搬出来的理由是这三件事以前混在仓库 1400-1460 那一段里，与锁、kbExists 判定、
 * 路径守门纠缠在一起，只能连临时目录一起测。现在两段式格式（`# 谈心记录` / `# 军师分析`）
 * 与 recent.md 的 upsert 语义各有一个所有者，锁与落盘仍在本类外（仓库的 fileMutex + atomicWriteText）。
 *
 * 本类不自持锁、不拼路径、不落盘：这三件事分别由仓库的 fileMutex、
 * [KnowledgeDocumentStore.resolve]、以及 `atomicWriteText` 那唯一的写链负责。
 */
internal class KnowledgeCounselingService(private val storage: CounselingStorage) {

    private val h1 = "# 谈心记录"
    private val h2 = "# 军师分析"

    /**
     * 谈心日志两段式追加：recordEntry 写入「# 谈心记录」节，analysisEntry 写入「# 军师分析」节。
     * 固定代码写入、全量不截断。旧格式文件（没有两个 # 大标题）自动迁移：旧内容并入第一节。
     * 调用方必须已持有 fileMutex；目标 KB 已删除时 no-op。
     */
    fun appendCounselingEntries(kbName: String, recordEntry: String, analysisEntry: String) {
        if (!storage.kbExists(kbName)) {
            storage.note("appendCounselingEntries skipped: kb no longer exists")
            return
        }
        val path = "memory/counseling_log.md"
        val lines = storage.read(kbName, path).lines()
        val idx1 = lines.indexOfFirst { it.trim() == h1 }
        val idx2 = lines.indexOfFirst { it.trim() == h2 }

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
                append(h1).append("\n")
                if (old.isNotBlank()) append("\n").append(old).append("\n")
                if (recordEntry.isNotBlank()) append("\n").append(recordEntry.trim()).append("\n")
                append("\n").append(h2).append("\n")
                if (analysisEntry.isNotBlank()) append("\n").append(analysisEntry.trim()).append("\n")
            }
        }
        storage.writeUnlocked(kbName, path, newContent)
    }

    /**
     * 原子追加"实际发送"记录——调用方已持有 fileMutex。
     * 返回 false = KB 不存在（不执行写入）。
     */
    fun appendActualSentRecord(kbName: String, entry: String): Boolean {
        if (!storage.kbExists(kbName)) {
            storage.note("appendActualSentRecord skipped: kb no longer exists")
            return false
        }
        val recentPath = "moment/recent.md"
        val existing = storage.read(kbName, recentPath)
        storage.writeUnlocked(kbName, recentPath, existing + entry)
        return true
    }

    /**
     * 替换同一 generationVersionId 的旧 actual sent 记录（upsert）。
     * 如果 oldEntry 在 recent.md 中不存在，返回 false（不执行无效写入）。
     */
    fun replaceActualSentRecord(kbName: String, oldEntry: String, newEntry: String): Boolean {
        if (!storage.kbExists(kbName)) {
            storage.note("replaceActualSentRecord skipped: kb no longer exists")
            return false
        }
        val recentPath = "moment/recent.md"
        val existing = storage.read(kbName, recentPath)
        if (!existing.contains(oldEntry)) {
            storage.note("replaceActualSentRecord: oldEntry not found in recent.md")
            return false
        }
        val updated = existing.replace(oldEntry, newEntry)
        storage.writeUnlocked(kbName, recentPath, updated)
        return true
    }

    /** 读取谈心日志「# 军师分析」节的最近 count 个 ## 块（供画像更新引擎） */
    fun readAnalysisBlocks(kbName: String, count: Int): String {
        val content = storage.read(kbName, "memory/counseling_log.md")
        val idx = content.indexOf(h2)
        if (idx < 0) return ""
        val section = content.substring(idx + h2.length)
        val blocks = section.split(Regex("(?m)^(?=## )"))
            .map { it.trim() }
            .filter { it.startsWith("## ") }
        return blocks.takeLast(count).joinToString("\n\n")
    }
}
