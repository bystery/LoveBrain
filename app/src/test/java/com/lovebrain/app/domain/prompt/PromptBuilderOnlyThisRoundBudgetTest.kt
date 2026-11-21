package com.lovebrain.app.domain.prompt

import com.lovebrain.app.AppConfig
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.model.ChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「仅看本轮」必须和正常回复共用同一个预算入口（硬帽 9000）。
 *
 * 这一支原先自己拼字符串：60 条 × 任意长度的对话没有任何上限，
 * 一条 12000 字的待回消息就能把整轮 prompt 顶穿。这里判的是**发给 provider 的那串文本**，
 * 不是源码里有没有调 `byBlocks`。
 */
class PromptBuilderOnlyThisRoundBudgetTest {

    private fun builder(): PromptBuilder =
        // 仅看本轮不读知识/资产；真有人加了读取，这颗一读就抛的假口当场炸
        PromptBuilder(
            PromptSourcePort { path -> error("仅看本轮不该读资产：$path") },
            InMemoryKnowledgePort()
        )

    @Test
    fun `sixty long messages are cut down to the hard cap and keep the latest round`() = runBlocking {
        val messages = (0 until AppConfig.REPLY_MAX_MESSAGES).map {
            ChatMessage(
                id = "m$it",
                role = if (it % 2 == 0) ChatMessage.Role.HER else ChatMessage.Role.ME,
                content = "第${it}条：" + "话".repeat(300)
            )
        }
        val result = builder().buildReplyUserPromptOnlyThisRound(messages, "备注：软一点")
        val prompt = result.prompt

        assertTrue("总长必须 ≤ 硬帽：实到 ${prompt.length}", prompt.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("围栏闭合", prompt.contains("</chat>"))
        assertTrue("最新一轮必须留着", prompt.contains("第${AppConfig.REPLY_MAX_MESSAGES - 1}条"))
        assertTrue("用户备注仍在", prompt.contains("备注：软一点"))
        assertTrue("模式说明仍在", prompt.contains("本轮仅看模式"))
        assertTrue("来源别名仍在", result.sourceAliasMap.isNotEmpty())
    }

    @Test
    fun `a single oversized pending message keeps its id and a readable text prefix`() = runBlocking {
        val messages = listOf(
            ChatMessage(id = "m0", role = ChatMessage.Role.HER, content = "在吗"),
            ChatMessage(id = "m1", role = ChatMessage.Role.ME, content = "长".repeat(12000))
        )
        val prompt = builder().buildReplyUserPromptOnlyThisRound(messages, "").prompt

        assertTrue("总长必须 ≤ 硬帽：实到 ${prompt.length}", prompt.length <= AppConfig.TOTAL_BUDGET)
        assertTrue("围栏闭合", prompt.contains("</chat>"))
        assertTrue("最后一条的来源 ID 必须还在", prompt.contains("\"id\":\"m1\""))
        assertTrue("正文要留可读前缀", prompt.contains("长".repeat(50)))
        assertFalse("不许整段换成占位句把待回消息弄丢", prompt.contains("对话记录因长度限制已省略"))
    }
}
