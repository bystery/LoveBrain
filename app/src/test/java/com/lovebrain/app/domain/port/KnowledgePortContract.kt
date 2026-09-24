package com.lovebrain.app.domain.port

import com.lovebrain.app.model.KnowledgeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识端口的**合同**：谁实现 [KnowledgePort]，谁就必须给出同样的可观察行为。
 *
 * 复核 §4 的 LSP 验收标准写的是"同一 contract test suite 对 production adapter 和 fake
 * 都通过"。这条不是形式主义：本轮之前，fake 只存在于各个测试自己现编的 mockk 桩里，
 * 生产语义（只读保护、越界路径、revision 校验）没有任何一个 fake 需要遵守，
 * 于是"UI 测试全绿"和"设备上的行为"是两件事。
 *
 * 两个实现各自跑一遍下面全部格子：
 *  - [FileBackedKnowledgePortContractTest]：真实 KnowledgeRepository + 临时目录
 *  - [InMemoryKnowledgePortContractTest] ：内存 fake
 */
abstract class KnowledgePortContract {

    protected val kb = "kb"

    /** 实现必须准备好一个叫 `kb` 的、schema 当前、可读写的库 */
    protected abstract fun newPort(): KnowledgePort

    /** 该库是否因"来自未来的 schema"而只读（生产靠迁移器判定，fake 靠显式名单） */
    protected abstract fun markFutureSchema(port: KnowledgePort)

    /** 落在库目录之外、可被断言"没被写过"的探针文件；fake 返回 null 表示不适用 */
    protected open fun outsideProbeFile(): java.io.File? = null

    @Test
    fun `write then read round-trips exactly`() {
        val port = newPort()
        runSuspend { port.writeFile(kb, "moment/recent.md", "她：下周考雅思\n我：要不要陪你练") }
        assertEquals(
            "写进去的字节必须原样读回来（含换行、含中文）",
            "她：下周考雅思\n我：要不要陪你练",
            runSuspend { port.readFile(kb, "moment/recent.md") }
        )
    }

    @Test
    fun `reading a file that does not exist yields empty, not an error`() {
        val port = newPort()
        assertEquals("", runSuspend { port.readFile(kb, "moment/nope.md") })
        // getActive() 故意不进合同：只有一个库时，生产会把它当 active 返回
        // （KnowledgeRepository.getActive 的兜底），而 fake 没有义务复刻这个产品便利。
        // 把 incidental 行为写进合同，等于强迫 fake 长得和生产一样啰嗦。
    }

    @Test
    fun `append accumulates in order`() {
        val port = newPort()
        runSuspend { port.appendFile(kb, "memory/raw_chat.md", "第一段\n") }
        runSuspend { port.appendFile(kb, "memory/raw_chat.md", "第二段\n") }
        assertEquals(
            "第一段\n第二段\n",
            runSuspend { port.readFile(kb, "memory/raw_chat.md") }
        )
    }

    @Test
    fun `delete reports what actually happened`() {
        val port = newPort()
        // 用一个两边都肯定不存在的路径：生产侧 migrateIfNeeded 会把 moment/scene.md
        // 这类 v3 标准文件补齐，拿它当"不存在的文件"是个假前提（第一版合同就踩了）。
        val ghost = "moment/definitely-not-there.md"
        assertFalse("删不存在的文件要返回 false", runSuspend { port.deleteFile(kb, ghost) })
        runSuspend { port.writeFile(kb, "moment/scene.md", "事实 A") }
        assertTrue(runSuspend { port.deleteFile(kb, "moment/scene.md") })
        assertEquals("", runSuspend { port.readFile(kb, "moment/scene.md") })
    }

    /** 越界读写：端口不能成为第二条绕过 canonical 边界的路 */
    @Test
    fun `paths that try to leave the library are refused on both sides`() {
        val outside = outsideProbeFile()
        val port = newPort()
        assertEquals("", runSuspend { port.readFile(kb, "../outside-secret.md") })
        runSuspend { port.writeFile(kb, "../outside-secret.md", "越界写入") }
        runSuspend { port.appendFile(kb, "moment/../../outside-secret.md", "越界追加") }
        assertEquals("", runSuspend { port.readFile(kb, "moment/../../outside-secret.md") })
        if (outside != null) {
            assertFalse("生产实现不得在库目录外留下文件", outside.exists())
        }
    }

    @Test
    fun `turn count only moves by a positive delta`() {
        val port = newPort()
        val before = runSuspend { port.getTurnCount(kb) }
        runSuspend { port.incrementTurnCountBy(kb, 3) }
        assertEquals(before + 3, runSuspend { port.getTurnCount(kb) })
        runSuspend { port.incrementTurnCountBy(kb, 0) }
        runSuspend { port.incrementTurnCountBy(kb, -5) }
        assertEquals("delta<=0 必须完全不动计数", before + 3, runSuspend { port.getTurnCount(kb) })
    }

    @Test
    fun `revision checked writes refuse a stale caller and accept the current one`() {
        val port = newPort()
        seedRevision(port, 7)
        assertFalse(
            "revision 不匹配要拒绝，并且什么都不写",
            runSuspend { port.appendFileWithRevisionCheck(kb, "memory/lessons.md", "过期写入", 3) }
        )
        assertEquals("", runSuspend { port.readFile(kb, "memory/lessons.md") })
        assertTrue(
            runSuspend { port.appendFileWithRevisionCheck(kb, "memory/lessons.md", "有效写入", 7) }
        )
        assertEquals("有效写入", runSuspend { port.readFile(kb, "memory/lessons.md") })
    }

    /** 复核 P0-03 的核心承诺：schema 过新的库读得到、写不进 */
    @Test
    fun `a library whose schema is newer than this build is readable but not writable`() {
        val port = newPort()
        runSuspend { port.writeFile(kb, "moment/recent.md", "未来版本写下的此刻\n") }
        markFutureSchema(port)

        assertEquals("只读不等于禁用", "未来版本写下的此刻\n", runSuspend { port.readFile(kb, "moment/recent.md") })

        runSuspend { port.writeFile(kb, "moment/recent.md", "v3 想覆盖未来结构") }
        runSuspend { port.appendFile(kb, "moment/recent.md", "追加也不行") }
        assertFalse(runSuspend { port.deleteFile(kb, "moment/recent.md") })
        runSuspend { port.setCurrentTopic(kb, "v3 想改话题") }
        runSuspend { port.rotateTopic(kb) }
        runSuspend { port.incrementTurnCountBy(kb, 9) }

        assertEquals(
            "整个库一个字节都不许被改写",
            "未来版本写下的此刻\n",
            runSuspend { port.readFile(kb, "moment/recent.md") }
        )
    }

    protected fun seedRevision(port: KnowledgePort, revision: Int) {
        when (port) {
            is InMemoryKnowledgePort -> port.seedRevision(kb, revision)
            else -> runSuspend {
                runSuspend { port.writeFile(kb, "memory/.revision", revision.toString()) }
            }
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
