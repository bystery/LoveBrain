package com.lovebrain.app.feature.reply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回复版本栈的四条判据（复核 §5.2 第 6 步搬出来的第三块行为；原来摊在 ViewModel 里）。
 *
 * 这里用 `String` 当上下文类型是故意的：栈不该知道"本轮上下文"长什么样，
 * 它只管"结果 + 上下文 + 版本身份一起走"。VM 那边接的是真的 `ReplyGenerationContext`，
 * 那条端到端的形状由 `GenerationRollbackTest` 在 VM 上继续钉着（它跑真方法，一字没改）。
 */
class ReplyVersionStackTest {

    // 结果类型也是泛型参数：栈只管"三条一起走"，不认识 GenerateResult 长什么样。
    // 端到端那层由 GenerationRollbackTest 在 VM 上继续钉（它跑真方法，一字没改）。
    private val res = "result"

    private var seq = 0
    private fun vid() = GenerationVersionId("v${++seq}")

    private fun stack(capacity: Int = ReplyVersionStack.DEFAULT_CAPACITY) =
        ReplyVersionStack<String, String>(capacity = capacity, clock = { 0L })

    /** 用 `kb` 当上下文内容：栈只关心"三条一起走"，上下文长什么样它不知道 */
    private fun ReplyVersionStack<String, String>.recordOn(kb: String) {
        record(versionId = vid(), result = res, context = kb, kbName = kb)
    }

    @Test
    fun `an empty stack refuses to roll back and reports nothing`() {
        val s = stack()
        assertEquals(0, s.size)
        assertNull(s.currentVersionId.value)
        assertFalse(s.canRollback(activeKbName = "A", contextKbName = "A"))
        assertTrue(s.rollback("A", "A") is RollbackOutcome.Rejected)
    }

    @Test
    fun `a single version cannot be rolled back`() {
        val s = stack()
        s.recordOn("A")
        assertEquals(1, s.size)
        assertFalse("一条历史没有'上一版'可回", s.canRollback("A", "A"))
    }

    @Test
    fun `rollback drops the current version and restores the previous one`() {
        val s = stack()
        val v1 = vid().also { s.record(it, res, "ctx-1", "A") }
        val v2 = vid().also { s.record(it, res, "ctx-2", "A") }
        val v3 = vid().also { s.record(it, res, "ctx-3", "A") }

        val out = s.rollback("A", "A") as RollbackOutcome.Applied<String, String>

        assertEquals("删的必须是当前那条，不是 previous", v3, out.dropped.versionId)
        assertEquals(v2, out.restored.versionId)
        assertEquals("恢复的不只结果，还有同一轮的上下文", "ctx-2", out.restored.context)
        assertEquals(listOf(v1, v2), s.snapshots.value.map { it.versionId })
        assertEquals("栈高与 currentVersionId 必须一起翻过去", v2, s.currentVersionId.value)
    }

    @Test
    fun `a discarded version never comes back after generating a new one`() {
        val s = stack()
        val v1 = vid().also { s.record(it, res, "ctx-1", "A") }
        val v2 = vid().also { s.record(it, res, "ctx-2", "A") }
        val v3 = vid().also { s.record(it, res, "ctx-3", "A") }
        s.rollback("A", "A")                       // [v1, v2]，current=v2
        val v4 = vid().also { s.record(it, res, "ctx-4", "A") }

        assertEquals(listOf(v1, v2, v4), s.snapshots.value.map { it.versionId })

        val out = s.rollback("A", "A") as RollbackOutcome.Applied<String, String>
        assertEquals(v4, out.dropped.versionId)
        assertEquals(v2, out.restored.versionId)
        assertTrue(
            "one-way undo：被丢弃的 v3 不许从任何位置复活",
            s.snapshots.value.none { it.versionId == v3 }
        )
    }

    @Test
    fun `each knowledge base gets its own stack height`() {
        val s = stack()
        s.recordOn("A")
        s.recordOn("A")
        s.recordOn("B")

        assertEquals("按库数：A 两条、B 一条", 2, s.sizeFor("A"))
        assertEquals(1, s.sizeFor("B"))
        assertTrue(s.canRollback("A", "A"))
        assertFalse("B 只有一条，A 的高度不能借给 B 用", s.canRollback("B", "B"))

        s.rollback("A", "A")
        assertEquals("在 A 上回退不许动到 B", 1, s.sizeFor("B"))
        assertEquals(1, s.sizeFor("A"))
    }

    @Test
    fun `a context from another library is refused and leaves the stack untouched`() {
        val s = stack()
        s.recordOn("A")
        s.recordOn("A")
        val before = s.snapshots.value

        val versionBefore = s.currentVersionId.value
        assertTrue(
            "用户已切到 B，就不许操作 A 的版本栈",
            s.rollback(activeKbName = "B", contextKbName = "A") is RollbackOutcome.Rejected
        )
        assertEquals("拒绝也要可预期：栈一字未动", before, s.snapshots.value)
        assertEquals("拒绝也不该改当前版本", versionBefore, s.currentVersionId.value)
    }

    @Test
    fun `a success without a context advances the version but writes no history`() {
        val s = stack()
        val id = vid()
        s.advanceVersionOnly(id)

        assertEquals("没有本轮上下文就没有可回退的快照", 0, s.size)
        assertEquals("但版本身份照旧前进（点踩/发送要绑的是它）", id, s.currentVersionId.value)
    }

    /**
     * KB 边界那一判的**判别力**格子。
     *
     * 第一版这里写成"A 有两条、B 一条，用 A 的上下文去 B 上回退"——结果把边界判据
     * `contextKbName != activeKbName` 整条删掉，这一格**照样全绿**：B 只有一条，
     * `size < 2` 那道普通闸门本来就会拒绝，边界那条等于没被观察到（探针 N3 就是这么露馅的）。
     * 现在让**被激活那块库自己也攒满两条**：删掉边界判据之后，它会拿 A 的上下文去翻 B 的栈，
     * 这一格当场红——这才是"用户已经切到 B，不许操作 A 的版本栈"真正防的那个事故。
     */
    @Test
    fun `a stale context cannot roll back the active library stack`() {
        val s = stack()
        s.recordOn("A")
        s.recordOn("A")
        val b1 = vid().also { s.record(it, res, "B-ctx-1", "B") }
        val b2 = vid().also { s.record(it, res, "B-ctx-2", "B") }
        val before = s.snapshots.value
        val versionBefore = s.currentVersionId.value

        assertTrue(
            "上下文还留在 A、激活库已经是 B：这次回退必须整个拒绝",
            s.rollback(activeKbName = "B", contextKbName = "A") is RollbackOutcome.Rejected
        )
        assertEquals("不许顺手把 B 的栈翻掉", before, s.snapshots.value)
        assertEquals(versionBefore, s.currentVersionId.value)
        assertEquals("B 那两条一条都不能少", listOf(b1, b2), s.snapshots.value.map { it.versionId }.takeLast(2))
        assertFalse("同一个理由，canRollback 也不许说可以", s.canRollback("B", "A"))
    }

    @Test
    fun `the oldest snapshot is pushed out past the capacity`() {
        val s = stack(capacity = 3)
        val ids = (1..5).map { vid().also { id -> s.record(id, res, "ctx", "A") } }

        assertEquals("上限不许被越过", 3, s.size)
        assertEquals("挤出去的是最旧那两条，留下的必须是最新的三条", ids.drop(2), s.snapshots.value.map { it.versionId })
        assertTrue("三条里当然还能回退一次", s.canRollback("A", "A"))

        val out = s.rollback("A", "A") as RollbackOutcome.Applied
        assertEquals("回退丢掉的仍是栈顶那条（不是被挤出去的旧条目）", ids[4], out.dropped.versionId)
        assertEquals(ids[3], out.restored.versionId)

        s.record(vid(), res, "ctx", "A")
        assertEquals(3, s.size)
        assertTrue("被丢弃的那条不许因为一次新记录又回来", s.snapshots.value.none { it.versionId == ids[4] })
    }
}
