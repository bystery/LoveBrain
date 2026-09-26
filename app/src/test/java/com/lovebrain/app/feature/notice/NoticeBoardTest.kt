package com.lovebrain.app.feature.notice

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [NoticeBoard] 的判据（§5.2 第 6 步第二块搬出来的那一族）。
 *
 * 真正值得钉的只有一件事：**"换知识库该清哪几条"是这条屏幕行为的判据**，
 * 搬之前在 VM 里是三条各写一句、写在 `refreshKnowledgeBases` 中间——
 * 少写一条不会报错，只会让上一块库的回执赖在下一块库的屏幕上。
 * 这里把它钉成一条：`dismissVolatileNotices` 清回执与向量摘要，**不动面板级警告**。
 *
 * 为什么不放那条"警告为什么不跟着清"：那不是礼貌注释，是一条会被人改的行为约定，
 * 所以它由 [volatile reset deliberately keeps the panel warning] 这一格守着，改成一锅清就红。
 * 反过来"回执该跟着切库清"也不是想当然：它是搬之前 `refreshKnowledgeBases` 的实际行为，
 * 这里只是把它从"写在中间的一句"变成"有名字、有断言的一条"。
 */
class NoticeBoardTest {

    private fun NoticeBoard.all(): List<String?> = listOf(knowledge.value, warning.value, vector.value)

    @Test
    fun `three channels are independent`() = runTest {
        val board = NoticeBoard()
        board.show(NoticeBoard.Channel.Knowledge, "经验提取完成")
        assertEquals(listOf("经验提取完成", null, null), board.all())

        board.show(NoticeBoard.Channel.Vector, "亲密度 +2")
        assertEquals(listOf("经验提取完成", null, "亲密度 +2"), board.all())

        board.dismiss(NoticeBoard.Channel.Knowledge)
        assertEquals(listOf(null, null, "亲密度 +2"), board.all())
    }

    @Test
    fun `switching knowledge base clears the per-base notices`() = runTest {
        val board = NoticeBoard()
        board.show(NoticeBoard.Channel.Knowledge, "已记录实际发送的消息")
        board.show(NoticeBoard.Channel.Vector, "本轮重估 3 项")
        board.show(NoticeBoard.Channel.Warning, "未配置模型")

        board.dismissVolatileNotices()

        assertNull("上一块库的回执不得留在新库屏幕上", board.knowledge.value)
        assertNull("上一块库的向量重估摘要不得留在新库屏幕上", board.vector.value)
    }

    @Test
    fun `volatile reset deliberately keeps the panel warning`() = runTest {
        val board = NoticeBoard()
        board.show(NoticeBoard.Channel.Warning, "未配置模型")
        board.show(NoticeBoard.Channel.Knowledge, "画像已更新")

        board.dismissVolatileNotices()

        assertNotNull(
            "面板级警告说的是**这台设备**的配置状态，与切到哪块知识库无关；" +
                "把它跟着一起清会让人以为配置好了（这条区分是搬之前的实际行为）",
            board.warning.value
        )
        assertNull(board.knowledge.value)
    }
}
