package com.lovebrain.app.domain.port

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识端口的**合同**：谁实现 [KnowledgePort]，谁就必须给出同样的可观察行为。
 *
 * 复核 第4节 的 LSP 验收标准写的是"同一 contract test suite 对 production adapter 和 fake
 * 都通过"。这条不是形式主义：本轮之前，fake 只存在于各个测试自己现编的 mockk 桩里，
 * 生产语义（只读保护、越界路径、revision 校验）没有任何一个 fake 需要遵守，
 * 于是"UI 测试全绿"和"设备上的行为"是两件事。
 *
 * 两个实现各自跑一遍下面全部格子：
 *  - [FileBackedKnowledgePortContractTest]（已删）：真实 KnowledgeRepository + 临时目录
 *  - [InMemoryKnowledgePortContractTest]（已删） ：内存 fake
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

    /** 复核 核心承诺：schema 过新的库读得到、写不进 */
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

    /**
     * 边界②「写前先有快照」：整篇替换之前，原件必须已经落在那颗被交回的路径上。
     *
     * 反例（坏实现）：直接 `writeFile(目标, 整理后的那一篇)` 就返回成功——
     * 那么 `result.snapshotPath` 要么是 null 要么读回来是**整理后的**内容而不是原件，这一格当场红；
     *  第8节第2条 要的就是"备份原文件"在先，整理判据写错了也不该由用户的文件买单。
     */
    @Test
    fun `the tidy rewrite preserves the original at the snapshot path it hands back`() {
        val port = newPort()
        val original = "# [2026-09-17 10:00] 第1次提取\n\n## 做对的\n- 做对：先接情绪\n"
        runSuspend { port.writeFile(kb, "memory/lessons.md", original) }

        val result = runSuspend {
            port.readTidyAndReplaceWithRevisionCheck(kb, "memory/lessons.md", 0) { existing ->
                "$existing\n# [2026-09-18 09:00] 第2次提取\n\n## 踩过的坑\n- 踩坑：急着给建议\n"
            }
        }
        assertTrue("整理替换要落盘（结果=$result）", result is LessonsRewriteResult.Rewritten)
        val snapshotPath = (result as LessonsRewriteResult.Rewritten).snapshotPath
        assertTrue("非空原件必须有快照路径，实际=$snapshotPath", snapshotPath != null)
        assertEquals(
            "快照里必须是**整理前**的原件，逐字一字不差",
            original, runSuspend { port.readFile(kb, snapshotPath!!) }
        )
        assertTrue(
            "目标文件此时已经是替换后的那一篇（含第二批）",
            runSuspend { port.readFile(kb, "memory/lessons.md") }.contains("第2次提取")
        )
    }

    /**
     * 边界③「只在真变了才写」，同时是**可重入**的证据：第二趟进来一趟"逐字相同"的整理，
     * 一个字节都不许写——连快照都不许被覆盖。
     *
     * 为什么用快照当证人而不是文件的 lastModified（那种断言在 CI 上会漂）：
     * 只要实现肯重写一次，它就会拿"当前内容"盖掉那份原件快照，
     * 于是"原件还在快照里"这句立刻红——**重写一次就红，不重写就绿**，坏实现躲不掉。
     */
    @Test
    fun `a rewrite that changes nothing writes nothing and keeps the first snapshot`() {
        val port = newPort()
        val original = "# [2026-09-17 10:00] 第1次提取\n\n## 做对的\n- 做对：先接情绪\n"
        runSuspend { port.writeFile(kb, "memory/lessons.md", original) }

        val first = runSuspend {
            port.readTidyAndReplaceWithRevisionCheck(kb, "memory/lessons.md", 0) { existing ->
                existing.replace("第1次", "第 1 次")
            }
        }
        assertTrue("第一趟确实有改动，要落盘（结果=$first）", first is LessonsRewriteResult.Rewritten)
        val snapshotPath = (first as LessonsRewriteResult.Rewritten).snapshotPath!!
        val afterFirst = runSuspend { port.readFile(kb, "memory/lessons.md") }

        // 第二趟：整理结果与磁盘逐字相同 ⇒ NothingToWrite（compose 被调用过，但实现不许写）
        val second = runSuspend {
            port.readTidyAndReplaceWithRevisionCheck(kb, "memory/lessons.md", 0) { existing -> existing }
        }
        assertTrue(
            "逐字相同必须报「什么都不写」，实际：$second",
            second == LessonsRewriteResult.NothingToWrite
        )
        assertEquals("替换后的正文不许被那趟 no-op 改动", afterFirst, runSuspend { port.readFile(kb, "memory/lessons.md") })
        assertEquals(
            "no-op 那一趟不许盖掉原件快照（盖了就是说它写过盘）",
            original, runSuspend { port.readFile(kb, snapshotPath) }
        )

        // 第三趟：compose 直接交回 null（domain 说"这批不用写"）——同样一个字节都不动
        val third = runSuspend {
            port.readTidyAndReplaceWithRevisionCheck(kb, "memory/lessons.md", 0) { null }
        }
        assertTrue("compose=null 也必须是什么都不写，实际：$third", third == LessonsRewriteResult.NothingToWrite)
        assertEquals(afterFirst, runSuspend { port.readFile(kb, "memory/lessons.md") })
    }

    /**
     * 边界①的一部分（锁内那一段的第一道判定）：**revision 不符时连正文都不读**。
     *
     * 为什么判 "compose 一次都没被调用" 而不是只判"文件没变"：发号是从 compose 里数出来的，
     * 坏实现可以先读正文、先发号、再比 revision——那种顺序下"分配了编号却没落盘"就发生了
     * （第8节第3条 明令禁止）。把 compose 调用次数判成 0，就把那条窗口钉死：**检查在读之前**。
     */
    @Test
    fun `a stale revision refuses the whole transaction before the body is read`() {
        val port = newPort()
        seedRevision(port, 7)
        val original = "# [2026-09-17 10:00] 第1次提取\n\n## 做对的\n- 做对：先接情绪\n"
        runSuspend { port.writeFile(kb, "memory/lessons.md", original) }

        var composeCalls = 0
        val result = runSuspend {
            port.readTidyAndReplaceWithRevisionCheck(kb, "memory/lessons.md", 3) { existing ->
                composeCalls++
                "$existing\n第二批（过期调用不该写进来）"
            }
        }
        assertTrue("revision 不匹配必须整段收手，实际：$result", result is LessonsRewriteResult.RevisionChanged)
        assertEquals("过期调用不许读正文、不许发号（compose 被调用了几次）", 0, composeCalls)
        assertEquals(
            "一个字节都不许写：原件逐字仍在",
            original, runSuspend { port.readFile(kb, "memory/lessons.md") }
        )

        // 同一颗口、当前 revision：必须写得进去（防"把门全关死"那种假安全）
        val ok = runSuspend {
            port.readTidyAndReplaceWithRevisionCheck(kb, "memory/lessons.md", 7) { existing ->
                "$existing\n第二批（当前 revision 的合法调用）"
            }
        }
        assertTrue("当前 revision 的调用必须落盘，实际：$ok", ok is LessonsRewriteResult.Rewritten)
        assertTrue(
            "合法调用写进去的就是 compose 交回的那一篇",
            runSuspend { port.readFile(kb, "memory/lessons.md") }.contains("第二批（当前 revision 的合法调用）")
        )
    }

    /** schema 过新的库：这一次事务与别的写口一样被只读保护挡下，且不动快照 */
    @Test
    fun `the tidy rewrite is refused on a library whose schema is newer`() {
        val port = newPort()
        val original = "# [2026-09-17 10:00] 第1次提取\n\n## 做对的\n- 做对：先接情绪\n"
        runSuspend { port.writeFile(kb, "memory/lessons.md", original) }
        markFutureSchema(port)

        val result = runSuspend {
            port.readTidyAndReplaceWithRevisionCheck(kb, "memory/lessons.md", 0) { it + "\n只读库不该收到" }
        }
        assertTrue(
            "只读库必须挡在事务入口（这一支来自仓库的 transaction 入口判定），实际：$result",
            result is LessonsRewriteResult.RefusedNewerSchema
        )
        assertEquals("正文一个字都不许动", original, runSuspend { port.readFile(kb, "memory/lessons.md") })
        assertFalse(
            "被挡下的整理不许在库里留一份快照",
            runSuspend { port.readFile(kb, "memory/.lessons.pre-tidy.md") }.contains("第1次提取")
        )
    }

    /** 空文件没有原件可保：交回 null 快照路径，而不是"保了一份空快照"那种假读数 */
    @Test
    fun `rewriting an empty file hands back no snapshot path`() {
        val port = newPort()
        val result = runSuspend {
            port.readTidyAndReplaceWithRevisionCheck(kb, "memory/lessons.md", 0) { existing ->
                assertTrue("锁内读到的必须是空串，实际：$existing", existing.isEmpty())
                "# [2026-09-18 09:00] 第1次提取\n\n## 做对的\n- 做对：先接情绪\n"
            }
        }
        assertTrue("第一批要落盘，实际：$result", result is LessonsRewriteResult.Rewritten)
        assertNull(
            "空文件没有原件可保 ⇒ snapshotPath 必须是 null",
            (result as LessonsRewriteResult.Rewritten).snapshotPath
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
