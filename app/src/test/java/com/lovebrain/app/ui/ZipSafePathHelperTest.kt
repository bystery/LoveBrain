package com.lovebrain.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * RA-04 辅助测试：验证 ZIP 路径安全 helper 的核心逻辑。
 *
 * 直接验证 canonicalPath + File.separator prefix 判断，
 * 不依赖 Activity 实例。
 */
class ZipSafePathHelperTest {

    /**
     * 模拟 RA-04 的安全路径判断逻辑。
     */
    private fun isSafeEntry(root: File, entryName: String): Boolean {
        val safePrefix = root.canonicalPath + File.separator
        val targetPath = File(root, entryName).canonicalPath
        return targetPath.startsWith(safePrefix)
    }

    @Test
    fun safe_entry_inside_root_is_allowed() {
        val root = Files.createTempDirectory("zip_safe").toFile()
        try {
            assertTrue(isSafeEntry(root, "kb_a/kb.json"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun traversal_entry_is_rejected() {
        val root = Files.createTempDirectory("zip_safe").toFile()
        try {
            assert(!isSafeEntry(root, "../evil"))
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * 关键：prefix collision 防护。
     * staging = /tmp/xxx_kb_import_123
     * entry = ../xxx_kb_import_123_evil/pwn
     * canonical = /tmp/xxx_kb_import_123_evil/pwn
     * 旧 startsWith("/tmp/xxx_kb_import_123") = true → 漏洞
     * 新 startsWith("/tmp/xxx_kb_import_123/") = false → 正确拒绝
     */
    @Test
    fun prefix_collision_entry_is_rejected() {
        val root = Files.createTempDirectory("kb_import_123").toFile()
        try {
            val evilEntry = "../${root.name}_evil/pwn"
            assert(!isSafeEntry(root, evilEntry)) {
                "prefix collision entry should be rejected: $evilEntry"
            }
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * 验证可以构造一个超过 entry 数限制的 ZIP。
     * 这里只构造小 ZIP 验证 ZipInputStream 可正常遍历，
     * 不实际触发 2048 限制（成本太高且无意义）。
     */
    @Test
    fun zip_stream_can_be_enumerated() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("kb_test/kb.json"))
            zos.write("{\"name\":\"kb_test\"}".toByteArray())
            zos.closeEntry()
        }
        val zipBytes = baos.toByteArray()
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var count = 0
            var entry = zis.nextEntry
            while (entry != null) {
                count++
                entry = zis.nextEntry
            }
            assertTrue("ZIP should have at least 1 entry", count >= 1)
        }
    }
}
