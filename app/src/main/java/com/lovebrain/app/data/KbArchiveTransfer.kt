package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 知识库归档（zip）导出 / 导入的唯一实现。
 *
 * 从 Activity 下沉到这里的目的：把「文件与压缩包」这类纯数据 IO 与 UI 生命周期解耦，
 * 使其可以在 JVM 单测里被真实调用（路径穿越、zip bomb、元数据不一致、同名碰撞）。
 *
 * 导入采用「暂存区 → 校验 → 原子搬入」三段式：
 * - 不直接写 knowledge/ 树：解压中途磁盘满或进程被杀不会残留半截坏库
 * - 校验三关：顶层恰一个目录 / kb.json 可解码且 name == 顶层目录名 / 无同名碰撞
 * - 任一失败 = 整体中止并清理暂存目录，异常上抛由调用方提示
 */
object KbArchiveTransfer {

    const val MAX_IMPORT_ENTRIES = 2048
    const val MAX_IMPORT_ENTRY_BYTES = 16L * 1024 * 1024
    const val MAX_IMPORT_TOTAL_BYTES = 64L * 1024 * 1024

    /** 导入失败原因——固定文案，不拼接用户输入 */
    class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * 把 [folder] 下的全部文件写入 [output]，条目名以 [prefix] 为首段。
     * 只负责写条目，不 close [output] 的宿主生命周期由调用方 `use` 决定。
     */
    fun export(folder: File, prefix: String, output: OutputStream) {
        if (!folder.exists()) throw TransferException("知识库目录不存在")
        ZipOutputStream(output).use { zos ->
            folder.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = "$prefix/${file.relativeTo(folder).path.replace('\\', '/')}"
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    /**
     * 解压 [input] 到 [stagingRoot]，校验通过后原子搬入 [knowledgeRoot]。
     *
     * @return 导入成功的知识库名
     * @throws TransferException 任一条目越界、超限、结构非法、元数据损坏或同名碰撞
     */
    fun import(input: InputStream, stagingRoot: File, knowledgeRoot: File): String {
        if (!stagingRoot.mkdirs() && !stagingRoot.isDirectory) {
            throw TransferException("无法创建导入暂存目录")
        }
        try {
            extractToStaging(input, stagingRoot)
            val topDir = singleTopDir(stagingRoot)
            val kb = readMetadata(topDir)
            if (kb.name != topDir.name) {
                throw TransferException("知识库元数据校验失败：name 与目录名不一致")
            }
            val target = File(knowledgeRoot, topDir.name)
            if (target.exists()) {
                throw TransferException("已存在同名知识库，导入中止")
            }
            knowledgeRoot.mkdirs()
            // minSdk=26：java.nio.file 可用；同盘 rename = 原子搬入
            Files.move(topDir.toPath(), target.toPath())
            return kb.name
        } finally {
            // 任何路径都清理暂存壳：成功时剩下的是空目录，失败时是半截包
            runCatching { stagingRoot.deleteRecursively() }
        }
    }

    private fun extractToStaging(input: InputStream, stagingRoot: File) {
        val safePrefix = stagingRoot.canonicalPath + File.separator
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_IMPORT_ENTRIES) {
                    throw TransferException("ZIP 包含过多条目（上限 $MAX_IMPORT_ENTRIES）")
                }
                val outFile = File(stagingRoot, entry.name)
                // 用 canonicalPath + File.separator 严格判断，防 prefix collision（.. / 绝对路径 / 盘符）
                if (!outFile.canonicalPath.startsWith(safePrefix)) {
                    throw TransferException("ZIP 包含非法路径")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    // 逐条目统计实际解压字节，不信任 ZipEntry.size（zip bomb 的 size 可以是谎报的）
                    var entryBytes = 0L
                    val buffer = ByteArray(8 * 1024)
                    FileOutputStream(outFile).use { fos ->
                        while (true) {
                            val read = zis.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > MAX_IMPORT_ENTRY_BYTES) {
                                throw TransferException("ZIP 条目过大")
                            }
                            if (totalBytes > MAX_IMPORT_TOTAL_BYTES) {
                                throw TransferException("ZIP 解压总大小超限")
                            }
                            fos.write(buffer, 0, read)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun singleTopDir(stagingRoot: File): File {
        val topDirs = stagingRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (topDirs.size != 1) throw TransferException("知识库包结构无效：应恰有一个顶层目录")
        return topDirs.first()
    }

    private fun readMetadata(topDir: File): KnowledgeBase {
        val meta = File(topDir, "kb.json")
        if (!meta.isFile) throw TransferException("知识库元数据缺失")
        return runCatching { Json.decodeFromString<KnowledgeBase>(meta.readText()) }
            .getOrNull() ?: throw TransferException("知识库元数据损坏")
    }
}
