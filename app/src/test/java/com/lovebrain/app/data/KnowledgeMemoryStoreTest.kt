package com.lovebrain.app.data

import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆格的单调 revision 与"一批写的顺序"。
 *
 * 仓库那侧已有的 `MemoryCorrectionTest` 走的是真文件 + 真公开 API，它管"整体行为没变"；
 * 这格管的是**这次搬进来的两条规则本身**：
 *  ① revision 只增不减，撤销之后新增不许复用旧号（R07 的原始缺陷就是靠剩余记录 max 推算，
 *     撤销后 revision 会倒退，后台防护因此漏判）；
 *  ② 纠正文件没写成时，绝不再写 revision——否则记录没变而版本跳了，
 *     防护会误判成"这一版已经处理过"。
 * 这两条都必须能被反例打破，所以 fake 里刻意把"第二次写"做成可失败、可记录顺序的。
 */
class KnowledgeMemoryStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** 每次写事务内的落盘顺序，形如 ["memory/corrections.json", "memory/.revision"] */
    private val writeLog = mutableListOf<String>()

    /** 事务列表：一个元素 = 一次写事务，元素内是这次事务写的文件（可含失败） */
    private val transactions = mutableListOf<MutableList<Pair<String, Boolean>>>()

    private var failCorrectionsWrite = false
    private var failRevisionWrite = false
    private var existing = true

    private fun store(
        files: MutableMap<String, String> = mutableMapOf()
    ) = KnowledgeMemoryStore(
        object : MemoryStorage {
            override fun note(message: String) { /* 本性格子不断日志 */ }
            override fun kbExists(kbName: String): Boolean = existing
            override fun timestamp(): String = "2026-09-25T02:20:00+08:00"
            override fun read(kbName: String, relativePath: String): String =
                files["$kbName/$relativePath"].orEmpty()

            override fun writeTransaction(kbName: String, block: MemoryTx.() -> Unit) {
                val batch = mutableListOf<Pair<String, Boolean>>()
                transactions += batch
                val tx = MemoryTx { relativePath, content ->
                    val ok = when {
                        relativePath.endsWith("corrections.json") && failCorrectionsWrite -> false
                        relativePath.endsWith(".revision") && failRevisionWrite -> false
                        else -> true
                    }
                    if (ok) files["$kbName/$relativePath"] = content
                    batch += relativePath to ok
                    writeLog += relativePath
                    ok
                }
                tx.block()
            }

            override fun decodeCorrections(text: String): List<MemoryCorrection>? =
                runCatching { json.decodeFromString(ListSerializer(MemoryCorrection.serializer()), text) }
                    .getOrNull()

            override fun encodeCorrections(corrections: List<MemoryCorrection>): String =
                json.encodeToString(ListSerializer(MemoryCorrection.serializer()), corrections)
        }
    )

    private fun seed(kbName: String, files: MutableMap<String, String>, revision: Int, ids: List<String>) {
        val records = ids.map { MemoryCorrection(memoryId = it, action = CorrectionAction.WRONG, revision = revision) }
        files["$kbName/${KnowledgeMemoryStore.CORRECTIONS_FILE}"] =
            json.encodeToString(ListSerializer(MemoryCorrection.serializer()), records)
        files["$kbName/${KnowledgeMemoryStore.MEMORY_REVISION_FILE}"] = revision.toString()
    }

    @Test
    fun revisionKeepsIncreasingAcrossSaveAndUndo() {
        val files = mutableMapOf<String, String>()
        seed("kb", files, revision = 7, ids = listOf("a", "b"))
        val s = store(files)

        assertTrue(s.save("kb", "c", CorrectionAction.WRONG, "补正"))
        assertEquals("新增应接在持久化的 7 之后 = 8", 8, s.revisionOf("kb"))

        assertTrue(s.undo("kb", "a"))
        assertEquals("撤销也递增，实到 ${s.revisionOf("kb")}", 9, s.revisionOf("kb"))

        assertTrue(s.save("kb", "d", CorrectionAction.MUTED, muteDuration = MuteDuration.TODAY))
        assertEquals("删过之后再新增不许复用旧号", 10, s.revisionOf("kb"))
        assertEquals("10", files["kb/${KnowledgeMemoryStore.MEMORY_REVISION_FILE}"])
    }

    /**
     * 一批写只能开一次事务，且顺序是先纠正记录、后 revision 标记。
     *
     * 不是洁癖：仓库里 `transactionUnlocked` 一次判定"这个库是不是只读"，
     * 拆成两次事务等于让第二批写绕过那次判定。
     */
    @Test
    fun correctionsFileMustBeWrittenBeforeTheRevisionMarker() {
        val files = mutableMapOf<String, String>()
        seed("kb", files, revision = 3, ids = listOf("a"))
        val s = store(files)

        s.save("kb", "z", CorrectionAction.WRONG, "顺序检查")

        assertEquals(
            "一次事务里的顺序：先纠正记录、后 revision 标记",
            listOf(KnowledgeMemoryStore.CORRECTIONS_FILE, KnowledgeMemoryStore.MEMORY_REVISION_FILE),
            writeLog.takeLast(2)
        )
        assertEquals("一批 = 一次写事务", 1, transactions.size)
    }

    @Test
    fun aRefusedCorrectionsWriteMustNotBumpTheRevision() {
        val files = mutableMapOf<String, String>()
        seed("kb", files, revision = 4, ids = listOf("a"))
        failCorrectionsWrite = true          // 模拟被只读保护挡下
        val s = store(files)

        assertFalse("写被挡下时必须如实报 false，不许报成功", s.save("kb", "z", CorrectionAction.WRONG))
        assertEquals("revision 一步都不许动", 4, s.revisionOf("kb"))
        assertEquals("被挡下这一批只试了一次写", 1, transactions.last().size)
        assertFalse("纠正记录也不许变", writeLog.contains(KnowledgeMemoryStore.MEMORY_REVISION_FILE))
        failCorrectionsWrite = false
    }

    /**
     * revision 写失败时：标记必须保持原值。
     *
     * ⚠ 这里**照抄改造前的语义**：`saveCorrection` 当年就以"纠正文件有没有写成"作为返回值，
     * 第二个写（revision 标记）失败时仍报 true。要不要收紧是另一件事（它会让"记下了纠正但
     * 版本没跳"变成可见的失败），本格只钉住一条：不许悄悄把 revision 跳上去，
     * 否则后台防护会以为这一版已经处理过。
     */
    @Test
    fun aRefusedRevisionWriteLeavesTheRevisionMarkerUnbumped() {
        val files = mutableMapOf<String, String>()
        seed("kb", files, revision = 4, ids = listOf("a"))
        failRevisionWrite = true
        val s = store(files)

        s.save("kb", "z", CorrectionAction.WRONG, "标记写失败")

        assertEquals("revision 标记保持原值", 4, s.revisionOf("kb"))
        assertEquals(
            "失败的那一批：先成功写了纠正、再尝试写 revision（顺序与旧实现一致）",
            listOf(
                KnowledgeMemoryStore.CORRECTIONS_FILE to true,
                KnowledgeMemoryStore.MEMORY_REVISION_FILE to false
            ),
            transactions.last()
        )
        failRevisionWrite = false
    }

    @Test
    fun aMissingLibraryNeverTouchesTheWriteChain() {
        existing = false
        val s = store()

        assertFalse("库不在时保存必须报 false", s.save("kb", "z", CorrectionAction.WRONG))
        assertFalse("库不在时撤销必须报 false", s.undo("kb", "z"))
        assertTrue("库不在了就不该开过任何写事务", transactions.isEmpty())
    }

    @Test
    fun aCorruptedCorrectionsFileReadsAsEmptyWithoutThrowing() {
        val files = mutableMapOf<String, String>()
        files["kb/${KnowledgeMemoryStore.CORRECTIONS_FILE}"] = "{这不是一个纠正数组"
        val s = store(files)

        assertTrue("坏 JSON 应读成空表", s.corrections("kb").isEmpty())
        assertEquals("revision 读不出数字时按 0 处理", 0, s.revisionOf("kb"))
    }

    @Test
    fun snapshotReturnsRecordsAndRevisionFromOneCall() {
        val files = mutableMapOf<String, String>()
        seed("kb", files, revision = 11, ids = listOf("a", "b"))

        val (records, revision) = store(files).correctionSnapshot("kb")

        assertEquals(2, records.size)
        assertEquals(11, revision)
        assertTrue("快照里每条都要带自己的 revision", records.values.all { it.revision == 11 })
    }
}
