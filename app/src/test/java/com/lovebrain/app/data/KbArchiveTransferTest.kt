package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 知识库归档导入/导出合同（S2-05 从 Activity 下沉的件）。
 *
 * 这些断言原先只能在真机上跑（zip 逻辑是 Activity 私有方法）；下沉到 [KbArchiveTransfer]
 * 之后可以在 JVM 上被真实调用，包括「恶意 zip 不能写到暂存区之外」「坏包不能留半截库」两条
 * 复核报告点名要求的安全边界。
 */
class KbArchiveTransferTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun kbJson(name: String) =
        """{"name":"$name","displayName":"小雅的库","stage":"dating","turnCount":3}"""

    /** 内存里造一个 zip：entryName -> content（entryName 以 / 结尾视为目录） */
    private fun zipOf(entries: List<Pair<String, String?>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, content) ->
                if (content == null) {
                    zos.putNextEntry(ZipEntry(name))
                } else {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                }
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun validKbZip(name: String = "kb_demo") = zipOf(
        listOf(
            "$name/" to null,
            "$name/kb.json" to kbJson(name),
            "$name/understand/me.md" to "我习惯先讲道理",
            "$name/moment/recent.md" to "<!-- round:abc -->\n她：周末有空吗"
        )
    )

    private fun staging() = File(tmp.root, "staging_${System.nanoTime()}")

    private fun knowledgeRoot() = File(tmp.root, "knowledge").apply { mkdirs() }

    private fun import(bytes: ByteArray, root: File = knowledgeRoot()): String =
        KbArchiveTransfer.import(ByteArrayInputStream(bytes), staging(), root)

    @Test
    fun `round trip - export then import keeps every file`() {
        val source = File(tmp.root, "src/kb_demo").apply { mkdirs() }
        File(source, "kb.json").apply { parentFile!!.mkdirs() }.writeText(kbJson("kb_demo"))
        File(source, "understand").mkdirs()
        File(source, "understand/me.md").writeText("我习惯先讲道理")
        File(source, "moment").mkdirs()
        File(source, "moment/recent.md").writeText("她：周末有空吗")

        val packed = ByteArrayOutputStream()
        KbArchiveTransfer.export(source, "kb_demo", packed)

        val target = knowledgeRoot()
        val imported = KbArchiveTransfer.import(
            ByteArrayInputStream(packed.toByteArray()), staging(), target
        )

        assertEquals("kb_demo", imported)
        assertEquals("我习惯先讲道理", File(target, "kb_demo/understand/me.md").readText())
        assertEquals("她：周末有空吗", File(target, "kb_demo/moment/recent.md").readText())
        assertEquals(kbJson("kb_demo"), File(target, "kb_demo/kb.json").readText())
    }

    @Test
    fun `export of a missing folder fails instead of writing an empty archive`() {
        val packed = ByteArrayOutputStream()
        val error = runCatching {
            KbArchiveTransfer.export(File(tmp.root, "nope"), "nope", packed)
        }.exceptionOrNull()
        assertTrue(error is KbArchiveTransfer.TransferException)
        assertEquals(0, packed.size())
    }

    @Test
    fun `import writes the tree and removes the staging dir`() {
        val stage = staging()
        val target = knowledgeRoot()
        KbArchiveTransfer.import(ByteArrayInputStream(validKbZip()), stage, target)
        assertTrue(File(target, "kb_demo/kb.json").isFile)
        assertFalse("暂存目录必须被清理", stage.exists())
    }

    @Test
    fun `directory traversal entry is rejected and writes nothing outside staging`() {
        val evil = zipOf(
            listOf(
                "kb_demo/" to null,
                "kb_demo/kb.json" to kbJson("kb_demo"),
                "../../escaped.md" to "pwned"
            )
        )
        val stage = staging()
        val error = runCatching {
            KbArchiveTransfer.import(ByteArrayInputStream(evil), stage, knowledgeRoot())
        }.exceptionOrNull()
        assertTrue("越界条目必须被拒", error is KbArchiveTransfer.TransferException)
        assertFalse(File(tmp.root.parentFile, "escaped.md").exists())
        assertFalse("失败后不得残留暂存内容", stage.exists())
    }

    @Test
    fun `metadata name mismatch is rejected`() {
        val bad = zipOf(
            listOf(
                "kb_demo/" to null,
                "kb_demo/kb.json" to kbJson("kb_someone_elses")
            )
        )
        val target = knowledgeRoot()
        val error = runCatching { import(bad, target) }.exceptionOrNull()
        assertTrue(error is KbArchiveTransfer.TransferException)
        assertFalse("校验失败不得搬入库", File(target, "kb_demo").exists())
    }

    @Test
    fun `missing kb json is rejected`() {
        val noMeta = zipOf(listOf("kb_demo/" to null, "kb_demo/me.md" to "hi"))
        val error = runCatching { import(noMeta) }.exceptionOrNull()
        assertTrue(error is KbArchiveTransfer.TransferException)
    }

    @Test
    fun `corrupt kb json is rejected`() {
        val broken = zipOf(
            listOf(
                "kb_demo/" to null,
                "kb_demo/kb.json" to "{ this is not json"
            )
        )
        val error = runCatching { import(broken) }.exceptionOrNull()
        assertTrue(error is KbArchiveTransfer.TransferException)
    }

    @Test
    fun `two top level directories are rejected`() {
        val two = zipOf(
            listOf(
                "kb_a/" to null,
                "kb_a/kb.json" to kbJson("kb_a"),
                "kb_b/" to null,
                "kb_b/kb.json" to kbJson("kb_b")
            )
        )
        val error = runCatching { import(two) }.exceptionOrNull()
        assertTrue(error is KbArchiveTransfer.TransferException)
    }

    @Test
    fun `zip without any top level directory is rejected`() {
        val flat = zipOf(listOf("kb.json" to kbJson("kb_demo")))
        val error = runCatching { import(flat) }.exceptionOrNull()
        assertTrue(error is KbArchiveTransfer.TransferException)
    }

    @Test
    fun `same name collision aborts without overwriting the existing library`() {
        val target = knowledgeRoot()
        import(validKbZip(), target)
        val existing = File(target, "kb_demo/understand/me.md")
        existing.writeText("用户手改过的内容")

        val second = zipOf(
            listOf(
                "kb_demo/" to null,
                "kb_demo/kb.json" to kbJson("kb_demo"),
                "kb_demo/understand/me.md" to "导入包里的内容"
            )
        )
        val error = runCatching { import(second, target) }.exceptionOrNull()
        assertTrue("同名库必须整体中止", error is KbArchiveTransfer.TransferException)
        assertEquals("用户手改过的内容", existing.readText())
    }

    @Test
    fun `entry count beyond the limit is rejected`() {
        val entries = mutableListOf<Pair<String, String?>>()
        entries += "kb_demo/" to null
        entries += "kb_demo/kb.json" to kbJson("kb_demo")
        repeat(KbArchiveTransfer.MAX_IMPORT_ENTRIES + 1) { i ->
            entries += "kb_demo/memory/filler_$i.md" to "x"
        }
        val error = runCatching { import(zipOf(entries)) }.exceptionOrNull()
        assertTrue(error is KbArchiveTransfer.TransferException)
    }

    @Test
    fun `staging dir is removed even when extraction itself fails`() {
        // 截断的 zip：读到一半就没数据，必须在 finally 里清干净
        val truncated = validKbZip().copyOf(40)
        val stage = staging()
        val error = runCatching {
            KbArchiveTransfer.import(ByteArrayInputStream(truncated), stage, knowledgeRoot())
        }.exceptionOrNull()
        assertTrue(error != null)
        assertFalse(stage.exists())
    }

    @Test
    fun `readback of an imported archive yields the same bytes as exported`() {
        val source = File(tmp.root, "src2/kb_x").apply { mkdirs() }
        File(source, "kb.json").writeText(kbJson("kb_x"))
        File(source, "deep").mkdirs()
        File(source, "deep/a.md").writeText("内容 A")
        val packed = ByteArrayOutputStream()
        KbArchiveTransfer.export(source, "kb_x", packed)

        val names = ZipInputStream(ByteArrayInputStream(packed.toByteArray())).let { zis ->
            val acc = mutableListOf<String>()
            var e = zis.nextEntry
            while (e != null) {
                acc += e.name
                e = zis.nextEntry
            }
            acc.sorted()
        }
        assertEquals(listOf("kb_x/deep/a.md", "kb_x/kb.json"), names)
    }
}
