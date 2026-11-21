package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 来源别名必须按**出现序号**编号，不能按消息 id 反查。
 *
 * 历史数据里存在同一 id 的多条消息；旧实现用 `msgToAlias[msg.id]` 反查，
 * 后一条覆盖前一条，于是围栏里前一条整条消失、别名条数也对不上。
 */
class ChatTranscriptBlockDuplicateIdTest {

    @Test
    fun `duplicate message ids still render every message with distinct aliases`() {
        val rendered = ChatTranscriptBlock.render(
            listOf(
                ChatMessage(id = "dup", role = ChatMessage.Role.HER, content = "第一条"),
                ChatMessage(id = "dup", role = ChatMessage.Role.ME, content = "第二条")
            )
        )

        val lines = rendered.body.lines().filter { it.startsWith("{") }
        assertEquals("两条消息都要进围栏", 2, lines.size)
        assertTrue(lines[0].contains("\"id\":\"m0\""))
        assertTrue(lines[0].contains("第一条"))
        assertTrue(lines[1].contains("\"id\":\"m1\""))
        assertTrue(lines[1].contains("第二条"))
        assertEquals("两个别名都必须登记（都指向同一个真实 id）", mapOf("m0" to "dup", "m1" to "dup"),
            rendered.sourceAliasMap)
    }

    @Test
    fun `idea rows never consume an alias`() {
        val rendered = ChatTranscriptBlock.render(
            listOf(
                ChatMessage(id = "a", role = ChatMessage.Role.IDEA, content = "想法"),
                ChatMessage(id = "b", role = ChatMessage.Role.HER, content = "她的消息")
            )
        )
        assertEquals(mapOf("m0" to "b"), rendered.sourceAliasMap)
        assertTrue(rendered.body.contains("\"id\":\"m0\""))
    }
}
