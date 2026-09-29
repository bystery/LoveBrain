package com.lovebrain.app.domain.port

import com.lovebrain.app.data.FileKbArchiveTransfer
import com.lovebrain.app.model.KnowledgeBase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * [KbArchivePort] 的合同：谁实现归档导出/导入，谁就必须给出同样的可观察行为。
 *
 * 复用仓库既有的合同打法（[AiGatewayContract] / [KnowledgePortContract]）——同一份格子
 * 对**生产侧**（[FileBackedKbArchivePortContractTest]：真 `FileKbArchiveTransfer` + 真临时目录
 * + 真 zip）与 **fake 侧**（[InMemoryKbArchivePortContractTest]：内存虚拟库 + 真 zip 流）各跑一遍。
 * fake 想蒙混只有一条路：把"单顶层目录 / 元数据与目录名一致 / 同名不覆盖"这三道判据也做对。
 */
abstract class KbArchivePortContract {

    /** 每次给一个全新、空的端口（生产侧＝空目录，fake 侧＝空 map） */
    protected abstract fun newPort(): KbArchivePort

    @Test
    fun `export of a library that is not there fails instead of emitting an empty zip`() {
        val port = newPort()
        val threw = runCatching { port.exportTo("ghost", ByteArrayOutputStream()) }.isFailure
        assertTrue("导出不存在的库必须抛，不能让调用方以为导了个空包", threw)
    }

    @Test
    fun `import a valid archive then export reproduces exactly its entries`() {
        val port = newPort()
        val name = runCatching { port.importFrom(ByteArrayInputStream(zipWithMeta("kb_demo"))) }.getOrNull()
        assertEquals("导入成功要回吐库名", "kb_demo", name)

        val out = ByteArrayOutputStream()
        port.exportTo("kb_demo", out)
        assertEquals(
            "导出条目名必须与当初导入的字节层结构逐条一致（fake 与生产不许各拼一套前缀）",
            listOf("kb_demo/kb.json", "kb_demo/understand/me.md"),
            entriesOf(out.toByteArray()).sorted()
        )
    }

    @Test
    fun `import refuses two top-level directories and writes nothing`() {
        val port = newPort()
        val bogus = zipOf(
            "a/" to null,
            "a/kb.json" to """{"name":"a","displayName":"A"}""",
            "b/" to null,
            "b/kb.json" to """{"name":"b","displayName":"B"}"""
        )
        assertTrue("两个顶层目录必须拒绝", runCatching { port.importFrom(ByteArrayInputStream(bogus)) }.isFailure)
        assertTrue(
            "被拒的包不得留下任何一本可读回的库",
            runCatching { port.exportTo("a", ByteArrayOutputStream()) }.isFailure
        )
    }

    @Test
    fun `import refuses metadata whose name disagrees with the folder`() {
        val port = newPort()
        val bogus = zipOf(
            "kb_demo/" to null,
            "kb_demo/kb.json" to """{"name":"别的名字","displayName":"小雅"}"""
        )
        assertTrue("元数据 name 与顶层目录名不一致必须拒绝", runCatching { port.importFrom(ByteArrayInputStream(bogus)) }.isFailure)
    }

    @Test
    fun `import refuses to overwrite a same-named library`() {
        val port = newPort()
        val bytes = zipWithMeta("kb_demo")
        assertEquals("kb_demo", runCatching { port.importFrom(ByteArrayInputStream(bytes)) }.getOrNull())
        assertTrue(
            "同名二次导入必须中止，不许悄悄覆盖用户已有的库",
            runCatching { port.importFrom(ByteArrayInputStream(bytes)) }.isFailure
        )
    }

    // ═══════════ 与实现无关的 zip 夹具 ═══════════

    private fun zipOf(vararg entries: Pair<String, String?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                if (content != null) zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipWithMeta(name: String): ByteArray = zipOf(
        "$name/" to null,
        "$name/kb.json" to """{"name":"$name","displayName":"小雅的库"}""",
        "$name/understand/me.md" to "我习惯先讲道理"
    )

    private fun entriesOf(bytes: ByteArray): List<String> {
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        val acc = mutableListOf<String>()
        generateSequence { zis.nextEntry }.forEach { acc += it.name }
        return acc
    }
}

/** 生产侧：真 FileKbArchiveTransfer + 真临时目录（走真 zip / 真搬移） */
class FileBackedKbArchivePortContractTest : KbArchivePortContract() {
    private var root: File = Files.createTempDirectory("kb_archive_port").toFile()

    override fun newPort(): KbArchivePort {
        root.deleteRecursively()
        root = Files.createTempDirectory("kb_archive_port").toFile()
        return FileKbArchiveTransfer(
            knowledgeRoot = File(root, "knowledge"),
            stagingBase = File(root, "cache")
        )
    }
}

/**
 * fake 侧：同一份合同，内存虚拟库。
 * 只用真实 zip 流编解码，不碰磁盘——这样"导入落没落库"完全由 map 决定，
 * 判据与生产逐条对齐，才测得出 fake 有没有偷工。
 */
class InMemoryKbArchivePort : KbArchivePort {
    /** 库名 → (相对路径 → 内容) */
    private val libraries = LinkedHashMap<String, MutableMap<String, String>>()

    override fun exportTo(kbName: String, output: OutputStream) {
        val files = libraries[kbName] ?: throw IllegalArgumentException("知识库目录不存在")
        ZipOutputStream(output).use { zos ->
            files.forEach { (rel, content) ->
                zos.putNextEntry(ZipEntry("$kbName/$rel"))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    override fun importFrom(input: InputStream): String {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(input).use { zis ->
            generateSequence { zis.nextEntry }.forEach { e ->
                if (!e.isDirectory) {
                    entries[e.name] = zis.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        val topDirs = entries.keys.map { it.substringBefore('/', "") }.toSet()
        if (topDirs.size != 1) throw IllegalArgumentException("知识库包结构无效：应恰有一个顶层目录")
        val top = topDirs.first()
        val meta = entries["$top/kb.json"] ?: throw IllegalArgumentException("知识库元数据缺失")
        val kb = runCatching { Json.decodeFromString<KnowledgeBase>(meta) }
            .getOrNull() ?: throw IllegalArgumentException("知识库元数据损坏")
        if (kb.name != top) throw IllegalArgumentException("知识库元数据校验失败：name 与目录名不一致")
        if (libraries.containsKey(kb.name)) throw IllegalArgumentException("已存在同名知识库，导入中止")
        libraries[kb.name] = entries.entries
            .filter { it.key.startsWith("$top/") }
            .associate { it.key.removePrefix("$top/") to it.value }
            .toMutableMap()
        return kb.name
    }
}

/** fake 必须通过同一套合同 */
class InMemoryKbArchivePortContractTest : KbArchivePortContract() {
    override fun newPort(): KbArchivePort = InMemoryKbArchivePort()
}
