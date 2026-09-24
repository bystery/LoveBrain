package com.lovebrain.app.viewmodel

import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 编辑位重算这条规则的**纯**用例（列表进、下标出，不碰 VM）。
 *
 * 与 `MessageEditingIndexInvariantTest` 的分工：那边穷举真实方法，证的是"三处调用点
 * 用完这条规则之后不变式成立"；这里钉的是规则本身的边界，出错时能直接指到一行判据。
 */
class MessageListEditingTest {

    private fun list(vararg ids: String) = ids.map { ChatMessage(id = it, role = ChatMessage.Role.HER, content = it) }

    @Test
    fun sameListKeepsTheIndex() {
        val l = list("a", "b", "c")
        assertEquals(1, MessageListEditing.reindex(l, l, 1))
    }

    @Test
    fun removingSomethingBeforeTheEditedOneMovesItLeft() {
        val before = list("a", "b", "c")
        val after = list("b", "c")
        assertEquals("编辑 c：前面少了一条，它就从 2 落到 1", 1, MessageListEditing.reindex(before, after, 2))
    }

    @Test
    fun removingTheEditedOneItselfGivesNoIndex() {
        val before = list("a", "b", "c")
        val after = list("a", "c")
        assertEquals(-1, MessageListEditing.reindex(before, after, 1))
    }

    /** 一次删多条（提交本轮会批量吞消息）——位移式推理最容易在这里错，身份式不会 */
    @Test
    fun removingSeveralBeforeTheEditedOneStillLandsRight() {
        val before = list("a", "b", "c", "d", "e")
        val after = list("d", "e")
        assertEquals(0, MessageListEditing.reindex(before, after, 3))
        assertEquals(-1, MessageListEditing.reindex(before, after, 2))
    }

    @Test
    fun reorderingFollowsTheMessageNotTheSlot() {
        val before = list("a", "b", "c")
        val after = list("b", "c", "a")   // 把 a 从 0 拖到 2
        assertEquals("编辑的是被拖那条：跟着走", 2, MessageListEditing.reindex(before, after, 0))
        assertEquals("编辑的是被挤前那条", 0, MessageListEditing.reindex(before, after, 1))
    }

    @Test
    fun noEditingIndexStaysNoEditingIndex() {
        val l = list("a", "b")
        assertEquals("原本没有编辑位，任何操作都不该造出一个", -1, MessageListEditing.reindex(l, l, -1))
    }

    /**
     * 越界的编辑位（悬空索引）一律判"没有编辑位"。
     *
     * 这一条是本轮换判据时顺带拧紧的：旧写法遇到悬空索引会原样留着，
     * 而输入框只看 `editingIndex >= 0` 就进编辑态——等于允许"在编辑一条不存在的消息"。
     */
    @Test
    fun aDanglingIndexIsTreatedAsNoEditing() {
        val l = list("a", "b")
        assertEquals(-1, MessageListEditing.reindex(l, l, 5))
        assertEquals(-1, MessageListEditing.reindex(l, emptyList(), 0))
    }
}
