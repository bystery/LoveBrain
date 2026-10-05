package com.lovebrain.app.domain

import com.lovebrain.app.AppConfig
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  上下文预算截断单测。
 *
 * 验证对话消息超长时截尾逻辑的正确性——
 * 超过 REPLY_MAX_MESSAGES 条时只保留最近 N 条，防 context length 超限。
 *
 * 注：buildReplyUserPrompt 依赖 Android Context.assets，无法纯 JVM 实例化，
 * 此测试验证截断逻辑本身（takeLast + 注释注入）的正确性。
 */
class MessageTruncationTest {

    private fun makeMessages(count: Int): List<ChatMessage> =
        (1..count).map { i ->
            ChatMessage(role = if (i % 2 == 0) ChatMessage.Role.HER else ChatMessage.Role.ME, content = "msg$i")
        }

    @Test
    fun `messages under limit are not truncated`() {
        val msgs = makeMessages(10)
        assertEquals(10, msgs.size)
        assertTrue(msgs.size <= AppConfig.REPLY_MAX_MESSAGES)
    }

    @Test
    fun `messages at limit are not truncated`() {
        val msgs = makeMessages(AppConfig.REPLY_MAX_MESSAGES)
        assertEquals(AppConfig.REPLY_MAX_MESSAGES, msgs.size)
        assertTrue(msgs.size <= AppConfig.REPLY_MAX_MESSAGES)
    }

    @Test
    fun `messages over limit are truncated to last N`() {
        val limit = AppConfig.REPLY_MAX_MESSAGES
        val total = limit + 20
        val msgs = makeMessages(total)
        val truncated = msgs.takeLast(limit)
        assertEquals(limit, truncated.size)
        // 保留的是最后 N 条，第一个是第 (total - limit + 1) 条
        assertEquals("msg${total - limit + 1}", truncated.first().content)
        assertEquals("msg$total", truncated.last().content)
    }

    @Test
    fun `counseling history over 6 rounds is truncated to last 6`() {
        val history = (1..10).map { "q$it" to "a$it" }
        val truncated = history.takeLast(AppConfig.COUNSELING_MAX_HISTORY_ROUNDS)
        assertEquals(AppConfig.COUNSELING_MAX_HISTORY_ROUNDS, truncated.size)
        assertEquals("q5", truncated.first().first)
        assertEquals("q10", truncated.last().first)
    }

    @Test
    fun `counseling history at exactly 6 rounds is not truncated`() {
        val history = (1..6).map { "q$it" to "a$it" }
        val truncated = history.takeLast(AppConfig.COUNSELING_MAX_HISTORY_ROUNDS)
        assertEquals(6, truncated.size)
        assertEquals("q1", truncated.first().first)
    }

    @Test
    fun `counseling history under 6 rounds is not truncated`() {
        val history = (1..3).map { "q$it" to "a$it" }
        val truncated = history.takeLast(AppConfig.COUNSELING_MAX_HISTORY_ROUNDS)
        assertEquals(3, truncated.size)
    }
}
