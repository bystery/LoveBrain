package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 归档格（§5.3 第七格）的**格级**测试。
 *
 * 与 `ArchiveOperationStateTest` 的分工：那边在真文件系统上跑整个仓库，量的是
 * "rotate 一次之后磁盘上应该长成什么样"；这里量的是搬进来的四条规则本身——
 * 步骤判定、归档条目格式、两把时钟、状态没落盘要不要留痕。
 * 这四件事在仓库里时只能连着临时目录和互斥锁间接触到，拆出来的主要收益就是它们能单独测了。
 *
 * 夹具把落盘记账全留下来：谁在什么时候被写/追加/删、事务开了几次、meta 改了几回。
 */
class KnowledgeArchiveServiceTest {

    private class FakeArchiveStorage : ArchiveStorage {
        val files = mutableMapOf<String, MutableMap<String, String>>()
        val notes = mutableListOf<String>()
        var meta: KnowledgeBase? = KnowledgeBase(name = "kb", displayName = "kb", topicCount = 0)
        var transactions = 0
        var metaWrites = 0
        /** 状态文件被写了几次——幂等靠的就是"每做一步就落一次"，所以要能数 */
        var stateWrites = 0
        var refused = false
        private var tick = 0

        override fun note(message: String) {
            notes += message
        }

        override fun stamp(): String {
            tick += 1
            return "2026-09-25 %02d:00".format(10 + tick % 6)
        }

        override fun metaTimestamp(): String = "2026-09-25T12:00:00+08:00"

        override fun read(kbName: String, relativePath: String): String =
            files[kbName]?.get(relativePath) ?: ""

        override fun runTransaction(kbName: String, block: ArchiveTx.() -> Unit) {
            transactions += 1
            if (refused) return
            val tx = object : ArchiveTx {
                override fun write(relativePath: String, content: String): Boolean {
                    files.getOrPut(kbName) { mutableMapOf() }[relativePath] = content
                    if (relativePath == KnowledgeArchiveService.STATE_FILE) stateWrites += 1
                    return true
                }

                override fun append(relativePath: String, content: String): Boolean {
                    val dir = files.getOrPut(kbName) { mutableMapOf() }
                    dir[relativePath] = (dir[relativePath] ?: "") + content
                    return true
                }

                override fun delete(relativePath: String): Boolean {
                    val dir = files[kbName] ?: return false
                    if (!dir.containsKey(relativePath)) return false
                    dir.remove(relativePath)
                    return true
                }

                override fun updateMeta(transform: (KnowledgeBase) -> KnowledgeBase): Boolean {
                    val current = meta ?: return false
                    meta = transform(current)
                    metaWrites += 1
                    return true
                }
            }
            tx.block()
        }

        override fun encodeState(state: ArchiveOperationState): String =
            kotlinx.serialization.json.Json.encodeToString(ArchiveOperationState.serializer(), state)

        override fun decodeState(text: String): ArchiveOperationState? = runCatching {
            kotlinx.serialization.json.Json.decodeFromString(ArchiveOperationState.serializer(), text)
        }.getOrNull()
    }

    private lateinit var storage: FakeArchiveStorage
    private lateinit var svc: KnowledgeArchiveService
    private val kb = "kb"

    @Before
    fun setUp() {
        storage = FakeArchiveStorage()
        svc = KnowledgeArchiveService(storage)
        put("moment/topic.md", "- [2026-09-25 09:00] 正在聊：旧话题")
    }

    private fun put(path: String, content: String) {
        storage.files.getOrPut(kb) { mutableMapOf() }[path] = content
    }

    private fun get(path: String): String? = storage.files[kb]?.get(path)

    private fun seedConversation() {
        put("memory/raw_chat.md", "- [2026-09-25 10:00] 我：在吗")
        put("moment/recent.md", "- [2026-09-25 10:05] 她：在的")
        put("memory/raw_scene.md", "")
        put("moment/scene.md", "")
    }

    // ─────────────── 四步与幂等 ───────────────

    @Test
    fun rotateAppendsCountsClearsAndDropsTheStateFile() {
        seedConversation()

        svc.rotate(kb)

        val archive = get(KnowledgeArchiveService.ARCHIVE_FILE) ?: error("归档文件没写")
        assertTrue(
            "归档条目要带上前任话题名与时间戳标题：$archive",
            Regex("# \\[2026-09-25 1\\d:00] 旧话题").containsMatchIn(archive)
        )
        assertEquals("计数 +1 只走一次", 1, storage.meta?.topicCount ?: 0)
        for (path in KnowledgeArchiveService.SOURCE_FILES) {
            assertEquals("四个源文件都要清空：$path", "", get(path))
        }
        assertNull("四步都完成之后状态文件不该留着", get(KnowledgeArchiveService.STATE_FILE))
    }

    @Test
    fun aCompletedStateSkipsEveryStepButStillDropsItself() {
        seedConversation()
        val done = listOf(
            ArchiveStep.APPEND_ARCHIVE, ArchiveStep.INCREMENT_COUNT, ArchiveStep.CLEAR_SOURCES
        )
        put(
            KnowledgeArchiveService.STATE_FILE,
            storage.encodeState(
                ArchiveOperationState("op-1", kb, "2026-09-25 09:00", "旧话题", "h", done)
            )
        )
        put(KnowledgeArchiveService.ARCHIVE_FILE, "已经归过一次了\n")

        svc.rotate(kb)

        assertEquals("已完成就不该再动计数", 0, storage.meta?.topicCount ?: 0)
        assertEquals("归档文件不该被追加第二遍", "已经归过一次了\n", get(KnowledgeArchiveService.ARCHIVE_FILE))
        assertNull("但状态文件仍要收掉", get(KnowledgeArchiveService.STATE_FILE))
    }

    /** 部分完成（崩在第 2 步之后）：只补剩下的两步，不重做追加 */
    @Test
    fun recoveryRunsOnlyTheRemainingSteps() {
        seedConversation()
        put(
            KnowledgeArchiveService.STATE_FILE,
            storage.encodeState(
                ArchiveOperationState(
                    "op-2", kb, "2026-09-25 08:00", "上次的话题", "h",
                    listOf(ArchiveStep.APPEND_ARCHIVE)
                )
            )
        )
        put(KnowledgeArchiveService.ARCHIVE_FILE, "旧内容")

        svc.rotate(kb)

        assertEquals("不该再追加，但计数要补上", "旧内容", get(KnowledgeArchiveService.ARCHIVE_FILE))
        assertEquals(1, storage.meta?.topicCount ?: 0)
    }

    /** 恢复时**沿用状态里记的时间与旧话题**，不是重新取一次此刻 —— 否则同一个操作会出现两个标题 */
    @Test
    fun recoveryKeepsTheRecordedTimestampAndTopic() {
        seedConversation()
        put(
            KnowledgeArchiveService.STATE_FILE,
            storage.encodeState(
                ArchiveOperationState("op-3", kb, "2026-09-20 07:00", "那天的话题", "h", emptyList())
            )
        )

        svc.rotate(kb)

        val archive = get(KnowledgeArchiveService.ARCHIVE_FILE) ?: error("没归档")
        assertTrue("标题该用状态里那一刻：$archive", archive.contains("# [2026-09-20 07:00] 那天的话题"))
    }

    /**
     * 钉住一条既存行为：四个源文件全空时，不追加、不计数，但**仍然清空源文件**。
     *
     * "没内容就别动"看着更合理，但清空这一步对空文件本来就是幂等的，改判定要先想清楚
     * "只写了 topic.md 的情况算不算有内容"——那是独立一次决定，不在拆格这一笔里顺手改。
     */
    @Test
    fun emptySourcesStillGoThroughTheClearStep() {
        svc.rotate(kb)

        assertEquals("没内容不该计数", 0, storage.meta?.topicCount ?: 0)
        assertNull("没内容也不该凭空造归档文件", get(KnowledgeArchiveService.ARCHIVE_FILE))
        // 清空那一步照做：四个键都真的被写成了空串（对空文件是幂等的，但状态机得推进）
        for (path in KnowledgeArchiveService.SOURCE_FILES) {
            assertEquals("清空步骤要覆盖 $path", "", get(path))
        }
        assertEquals("只有创建 + 清空两步落状态", 2, storage.stateWrites)
    }

    // ─────────────── 归档条目格式（抽出来的最大理由）───────────────

    @Test
    fun archiveEntryLayoutIsTestableWithoutTheWholeMachine() {
        val state = ArchiveOperationState("op", kb, "2026-09-25 10:00", "吵架之后", "hash")
        val entry = svc.archiveEntry(
            state,
            rawChat = "  我：对不起  ",
            recent = "她：嗯",
            rawScene = "- [2026-09-25 09:50] 她在生气",
            scene = ""
        )

        assertTrue(entry.startsWith("\n# [2026-09-25 10:00] 吵架之后\n\n"))
        assertTrue("状态变化段要带时间戳标题：$entry", entry.contains("## [2026-09-25 10:00] 状态变化\n"))
        assertTrue("对话记录段是三级标题：$entry", entry.contains("### [2026-09-25 10:00] 对话记录\n"))
        assertTrue("源内容两端空白要收掉：$entry", entry.contains("我：对不起\n"))
        assertTrue(entry.contains("她：嗯\n"))
        assertFalse("空白的 recent 不留空行尾巴", entry.endsWith("\n\n"))
    }

    @Test
    fun archiveCountingOnlyCountsSecondLevelEntries() {
        val content = "## [2026-01-01] 一条\n## [2026-01-02] 两条\n### [2026-01-03] 这条不算\n正文 ## [x] 不在行首也不算\n"
        assertEquals(2, svc.countArchiveEntries(content))
        assertEquals("空文本给 0", 0, svc.countArchiveEntries(""))
    }

    // ─────────────── 两把时钟与留痕 ───────────────

    /** kb.json 的 updatedAt 一直是 ISO，条目行/operationId 用 TimeFmt；合成一把会悄悄换掉落盘格式 */
    @Test
    fun theTwoClocksStayApart() {
        seedConversation()

        svc.rotate(kb)

        assertEquals("meta 用 ISO 那把尺", "2026-09-25T12:00:00+08:00", storage.meta?.updatedAt)
        val archive = get(KnowledgeArchiveService.ARCHIVE_FILE) ?: error("没归档")
        assertTrue(
            "归档标题用 TimeFmt 那一刻（不带 T 也不带时区）：$archive",
            Regex("# \\[2026-09-25 1\\d:00] ").containsMatchIn(archive) &&
                !archive.contains("2026-09-25T")
        )
    }

    /** 状态写不下去（只读保护等）必须留痕，不能静默 —— 与记忆格同一口径 */
    @Test
    fun aRefusedStateWriteLeavesATrace() {
        seedConversation()
        storage.refused = true

        svc.rotate(kb)

        assertTrue(
            "整个事务被挡下时要能查出来说过几次：${storage.notes}",
            storage.notes.any { it.contains("归档操作状态没落盘") }
        )
        assertEquals("被挡下时一个字节都不该落", null, get(KnowledgeArchiveService.ARCHIVE_FILE))
    }

    /** 每一步做完都要把状态落一次：幂等靠的就是这个，少写一次崩溃就会重做已完成的步骤 */
    @Test
    fun theStateFileIsRewrittenAfterEveryStep() {
        seedConversation()

        svc.rotate(kb)

        assertEquals("创建 1 次 + 三步各 1 次", 4, storage.stateWrites)
        assertNull("最后一次动作是把它删掉", get(KnowledgeArchiveService.STATE_FILE))
        assertEquals("delete 之后计数不再被改动", 1, storage.metaWrites)
    }
}
