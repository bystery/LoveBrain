package com.lovebrain.app.data

import com.lovebrain.app.model.KbName
import com.lovebrain.app.model.KbRelativePath
import java.io.File

/**
 * 仓库交给文档格用的能力，故意只给四样：根目录、一条日志出口、"这个库还在不在"、
 * 以及**那一条**无锁写原语。
 *
 * 不给 Mutex，也不给第二份落盘实现——独立复核报告里 P0 那条要的是"唯一写边界"，
 * `KnowledgeRepository.rawAtomicWriteText` 全仓只许有一个调用方；
 * 文档格想写文件必须回到仓库那道门（那里还捎带着只读 schema 拒绝与备份节流）。
 */
interface DocumentStorage {
    val root: File

    /** 观测出口：拒绝/越界这类话该由仓库决定说不说、怎么说，策略类不自己抓日志 */
    fun note(message: String)

    fun kbExists(kbName: String): Boolean

    /** 仓库的 `writeFileUnlocked` 转发：只读库拒绝、建父目录、备份节流都仍在那一处 */
    fun writeUnlocked(kbName: String, relativePath: String, content: String)
}

/**
 * §5.3 拆出的第四格：**文档的安全路径 + 版本化读写**。
 *
 * 拆它的理由不是"仓库太大"，而是这两件事此前**各写了两遍且宽严不一**：
 * 公开读路径 `readFile` 过 `safeKbFile`（拒绝 `..`、绝对路径、盘符、UNC、反斜杠），
 * 而无锁快速读 `readFileUnlockedFast` 直接 `File(File(root, kbName), relativePath)`——
 * 同一份内容，走哪个入口决定 canonical 边界存不存在。指导书里 P0 那条读路径最后一段点名的
 * 就是这件事："读取接口也必须走 safeKbFile，canonical boundary 并未覆盖所有 String 入口"。
 * 现在两个入口共用同一道门，只有一个所有者。
 *
 * 版本化读写（`readWithVersion` / `writeWithVersion`，SHA-256 做乐观并发）也归这里，
 * 因为它整个语义就是"读一个文档 + 写回同一个文档"，跟知识库目录、画像、备份不是一回事。
 *
 * 本类不自持锁、不启动协程：调用方（仓库）负责 `fileMutex.withLock`。
 */
internal class KnowledgeDocumentStore(private val storage: DocumentStorage) {

    /**
     * 把裸字符串库名校验成 [KbName]。
     *
     * 光加一个没人用的 value class 不算建立边界，所以**所有** String 入口都先过这里：
     * 空名、带路径分隔符、`..`、超长一律拒绝。
     */
    fun toKbName(raw: String): KbName? =
        runCatching { KbName(raw) }
            .onFailure { storage.note("rejected kb name: ${it.message}") }
            .getOrNull()

    /** 把裸字符串相对路径校验成 [KbRelativePath] */
    fun toKbPath(raw: String): KbRelativePath? =
        runCatching { KbRelativePath(raw) }
            .onFailure { storage.note("rejected kb path: ${it.message}") }
            .getOrNull()

    /**
     * String 入口的统一守门：返回解析后的绝对 File，非法输入返回 null。
     *
     * 检查的不只是 `..`：绝对路径、Windows 盘符、UNC、反斜杠分隔符都会让
     * `File(dir, child)` 跳出库目录；`canonicalPath` 在畸形输入上还会直接抛
     * IOException（Windows 混用分隔符时实测），边界函数不能让异常穿出去——抛不出去就当拒绝。
     */
    fun resolve(kbName: String, relativePath: String): File? {
        val name = toKbName(kbName) ?: return null
        val path = toKbPath(relativePath) ?: return null
        val dir = File(storage.root, name.value)
        val file = File(dir, path.value)
        val escaped = runCatching {
            !file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
        }.getOrElse {
            storage.note("path could not be canonicalised, refused: ${it.message}")
            true
        }
        if (escaped) {
            storage.note("path escapes knowledge dir, refused")
            return null
        }
        return file
    }

    /** 读一个文档（自动兼容旧路径）。非法路径与不存在的文件都给空串，不给异常 */
    fun read(kbName: String, relativePath: String): String {
        val file = resolve(kbName, relativePath) ?: return ""
        if (file.exists()) return file.readText()
        return readLegacy(kbName, relativePath)
    }

    /**
     * 旧版布局回退：新版路径没有时再看老位置有没有。
     *
     * 回退目标同样要过 [resolve]——否则"`../global/me.md`"这种写在映射表里的相对路径
     * 就成了绕过边界的第二条路。
     */
    private fun readLegacy(kbName: String, relativePath: String): String {
        val oldPath = OLD_PATH_MAP[relativePath] ?: return ""
        val oldFile = resolve(kbName, oldPath) ?: return ""
        return if (oldFile.exists()) oldFile.readText() else ""
    }

    /** 读取内容 + 版本号（SHA-256）；调用方持有版本号，写回时用于冲突检测 */
    fun readWithVersion(kbName: String, relativePath: String): Pair<String, String> {
        val content = read(kbName, relativePath)
        return content to KbTextOps.sha256(content)
    }

    /** 供无版本校验路径生成新版本号 */
    fun hashContent(text: String): String = KbTextOps.sha256(text)

    /**
     * 乐观并发写：磁盘上当前内容必须仍等于调用方读到的那个版本，否则拒写。
     *
     * 返回新版本号 = 写成功；返回 null = 库不在了、路径非法，或版本冲突（调用方保留草稿）。
     * 注意这里**不自己落盘**：写仍回到 [DocumentStorage.writeUnlocked]，
     * 只读库拒绝与备份节流因此不会被这条路绕开。
     */
    fun writeWithVersion(
        kbName: String,
        relativePath: String,
        content: String,
        expectedVersion: String
    ): String? {
        if (!storage.kbExists(kbName)) {
            storage.note("writeFileWithVersion skipped: kb no longer exists")
            return null
        }
        val file = resolve(kbName, relativePath) ?: return null
        val currentVersion = KbTextOps.sha256(if (file.exists()) file.readText() else "")
        if (currentVersion != expectedVersion) {
            storage.note("writeFileWithVersion conflict: $relativePath")
            return null
        }
        storage.writeUnlocked(kbName, relativePath, content)
        return KbTextOps.sha256(content)
    }

    companion object {
        /**
         * 旧→新路径映射：只有这一份。
         *
         * 以前 `readFile` 与无锁快速读各自认一张表，改一处漏一处。
         */
        val OLD_PATH_MAP = mapOf(
            "understand/me.md" to "global/me.md",
            "understand/her.md" to "global/her.md",
            "understand/warmth.md" to "global/status.md",
            "moment/recent.md" to "recent/chatlog.md",
            "memory/lessons.md" to "general/lessons.md"
        )
    }
}
