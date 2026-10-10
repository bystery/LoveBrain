package com.lovebrain.app.data

import com.lovebrain.app.domain.port.KnowledgePresencePort
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [FileKnowledgePresence]（[KnowledgePresencePort] 的文件系统实现）的契约单测。
 *
 * SetupViewModel 的"本机有没有知识库"那一格端口化后，磁盘判据从 viewmodel 挪进了这颗实现——
 * 这格把旧判据（目录存在 && `listFiles()` 非空）的三条路各钉一行：
 * 目录不在 → false；目录在但空 → false（空目录不许把人当老用户）；有任何条目 → true。
 * 刻意不验 kb.json：这是「老用户」判据不是「合法库」判据，残留坏目录也不该把人拽回引导
 * （那格因此喂的是杂牌文件名，不是一本合规库）。
 */
class FileKnowledgePresenceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun presenceWith(root: File): KnowledgePresencePort = FileKnowledgePresence(root)

    @Test
    fun `missing knowledge directory is not an existing user`() {
        assertFalse(presenceWith(File(tmp.root, "knowledge")).hasAnyKnowledgeBase())
    }

    @Test
    fun `empty knowledge directory does not fake an existing user`() {
        val root = File(tmp.root, "knowledge").apply { mkdirs() }

        assertFalse(presenceWith(root).hasAnyKnowledgeBase())
    }

    @Test
    fun `any entry in the knowledge directory counts`() {
        val root = File(tmp.root, "knowledge").apply { mkdirs() }
        // 杂牌条目也算：判据是"这台机器上留过东西"，不是"存着一本合法库"
        File(root, "kb_demo").apply { mkdirs() }
        File(root, "loose_leftover.txt").writeText("残留物")

        assertTrue(presenceWith(root).hasAnyKnowledgeBase())
    }
}
