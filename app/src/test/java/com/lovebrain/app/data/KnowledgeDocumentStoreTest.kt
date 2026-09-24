package com.lovebrain.app.data

import com.lovebrain.app.model.KbName
import com.lovebrain.app.model.KbRelativePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 文档格（§5.3 第四格）的两件事：安全路径 + 版本化读写。
 *
 * 为什么这一格值得单独存在，历史证据比行数清楚：仓库里有**两条**读路径，
 * 公开的 `readFile` 过 canonical 守门，而无锁快速读 `readFileUnlockedFast`
 * 直接 `File(File(root, kbName), relativePath)` 拼路径。同一份内容，
 * 走哪个入口决定"边界"这件事存不存在——独立复核 P0-03 最后一句点名的正是它。
 * 现在两个入口共用 [KnowledgeDocumentStore.read]，所以下面这些拒绝断言
 * **一次覆盖两条路**：谁再开一条捷径，本类的注入式夹具测不到它，
 * 但 `StorageBoundaryOwnershipTest` 那把所有权棘轮会红。
 *
 * 夹具是临时目录 + 手写 fake，不用 mockk：这个格子只依赖 4 个能力，
 * 用真文件系统反而能断言"到底有没有落盘"这种最容易骗人的事。
 */
class KnowledgeDocumentStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val notes = mutableListOf<String>()
    private val writes = mutableListOf<Triple<String, String, String>>()

    /** 文档格自己不碰锁也不碰磁盘，所以 fake 只需回答：库还在吗、写这一笔 */
    private fun store(
        root: File = folder.root,
        existing: Set<String> = setOf("她")
    ) = KnowledgeDocumentStore(
        object : DocumentStorage {
            override val root: File get() = root
            override fun note(message: String) { notes += message }
            override fun kbExists(kbName: String): Boolean = kbName in existing
            override fun writeUnlocked(kbName: String, relativePath: String, content: String) {
                writes += Triple(kbName, relativePath, content)
                File(root, "$kbName/$relativePath").let { f ->
                    f.parentFile?.mkdirs()
                    f.writeText(content, Charsets.UTF_8)
                }
            }
        }
    )

    private fun lib(root: File, kb: String, path: String, content: String) {
        val f = File(File(root, kb), path)
        f.parentFile.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }

    // ═══ 安全路径 ═══

    @Test
    fun aLegitimateNestedPathResolvesInsideTheRoot() {
        val resolved = store().resolve("她", "understand/me.md")
        assertNotNull(resolved)
        assertTrue(
            "解析结果跑到了知识库根目录外面：" + resolved!!.canonicalPath,
            resolved.canonicalPath.startsWith(folder.root.canonicalPath + File.separator)
        )
    }

    @Test
    fun everyMalformedLibraryNameIsRefusedAndSaidOutLoud() {
        val s = store()
        for (bad in listOf("", "..", "a/b", "a\\b", "x".repeat(101))) {
            assertNull("库名「$bad」竟然被接受", s.resolve(bad, "understand/me.md"))
        }
        assertEquals("每一次拒绝都要留下一句原因", 5, notes.size)
    }

    @Test
    fun everyShapeOfPathEscapeIsRefused() {
        // 外面那层放一个真文件，"返回 null"与"读到了外面的东西"才分得开
        val outsideDir = File(folder.root.parentFile, "lovebrain-outside-" + System.nanoTime())
        outsideDir.mkdirs()
        val outside = File(outsideDir, "me.md").apply { writeText("库外不该被读到的东西", Charsets.UTF_8) }
        try {
            val s = store()
            val escapes = listOf(
                "../${outsideDir.name}/me.md",     // 相对上跳
                "${outside.absolutePath}",          // POSIX 绝对路径
                "C:\\Windows\\win.ini",             // Windows 盘符
                "\\\\server\\share\\x.md",          // UNC
                "understand\\me.md"                 // 反斜杠在 Windows 上就是分隔符
            )
            for (bad in escapes) {
                assertNull("路径「$bad」竟然被接受", s.resolve("她", bad))
                assertEquals("路径「$bad」不许读出任何内容", "", s.read("她", bad))
            }
        } finally {
            outside.delete(); outsideDir.delete()
        }
    }

    /** 旧路径回退本身也不能成为第二条绕过边界的路 */
    @Test
    fun theLegacyFallbackTargetIsGuardedToo() {
        val root = File(folder.root, "她")
        File(root, "global").mkdirs()
        File(root, "global/me.md").writeText("旧位置的内容", Charsets.UTF_8)
        assertEquals("旧位置的内容", store().read("她", "understand/me.md"))

        // 映射表里若被人塞进一个跳出去的目标，resolve 必须把它拒掉
        val s = store()
        assertNull(s.resolve("她", "../global/me.md"))
    }

    // ═══ 版本化读写 ═══

    @Test
    fun versionedReadHandsBackContentAndItsHash() {
        lib(folder.root, "她", "understand/me.md", "第一版")
        val (content, version) = store().readWithVersion("她", "understand/me.md")
        assertEquals("第一版", content)
        assertEquals("版本号就是内容的 SHA-256", KbTextOps.sha256("第一版"), version)
    }

    @Test
    fun aStaleVersionRefusesTheWriteAndTouchesNothing() {
        lib(folder.root, "她", "understand/me.md", "磁盘上的现在")
        val s = store()

        val outcome = s.writeWithVersion("她", "understand/me.md", "我想覆盖", "不是它")

        assertNull("版本不匹配必须拒写", outcome)
        assertTrue("拒写不得经过写链", writes.isEmpty())
        assertEquals("磁盘内容必须原样", "磁盘上的现在",
            File(File(folder.root, "她"), "understand/me.md").readText())
        assertTrue("要说出为什么拒", notes.any { it.contains("conflict") })
    }

    @Test
    fun aMatchingVersionWritesOnceAndReturnsTheNewHash() {
        lib(folder.root, "她", "understand/me.md", "磁盘上的现在")
        val s = store()

        val newVersion = s.writeWithVersion("她", "understand/me.md", "改过的内容", KbTextOps.sha256("磁盘上的现在"))

        assertEquals("成功要返回新内容的哈希", KbTextOps.sha256("改过的内容"), newVersion)
        assertEquals("写必须恰好一次", 1, writes.size)
        assertEquals("写的是这一格", Triple("她", "understand/me.md", "改过的内容"), writes[0])
    }

    @Test
    fun writingIntoAKbThatIsGoneIsRefusedWithoutCreatingIt() {
        val s = store(existing = emptySet())

        assertNull(s.writeWithVersion("没了的库", "understand/me.md", "内容", KbTextOps.sha256("")))

        assertTrue("不该经过写链", writes.isEmpty())
        assertTrue("要说清楚是库没了", notes.any { it.contains("no longer exists") })
    }

    /** 本类不许自带第二份落盘实现：写只有一条路，就是从注入的写链回去 */
    @Test
    fun readsAndHashingNeverReachTheWriteChain() {
        lib(folder.root, "她", "understand/me.md", "在里面")
        val s = store()
        s.read("她", "understand/me.md")
        s.readWithVersion("她", "understand/me.md")
        s.resolve("她", "understand/me.md")
        s.hashContent("随便")
        s.toKbName("她"); s.toKbPath("understand/me.md")
        assertTrue("读、解析、哈希都不得产生写入", writes.isEmpty())
    }

    // ═══ 值对象判据仍在（搬家不能搬丢）═══

    @Test
    fun theValueObjectsStillRefuseWhatTheyAlwaysRefused() {
        assertNotNull(store().toKbName("她"))
        assertNull(store().toKbName("a/b"))
        assertNotNull(store().toKbPath("understand/me.md"))
        assertNull(store().toKbPath("/etc/passwd"))
        // 直接用值对象也测一遍，防止有人把校验从值对象里摘掉只留在 store
        runCatching { KbName("x".repeat(101)) }.onSuccess { throw AssertionError("超长库名被接受") }
        runCatching { KbRelativePath("..") }.onSuccess { throw AssertionError("含 .. 的路径被接受") }
    }
}
