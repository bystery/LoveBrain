package com.lovebrain.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * URL-01：OpenAiChatEndpointResolver 候选生成规则测试。
 *
 * 验证：
 * - 已含 /chat/completions → 唯一候选
 * - /v1 / /api/v1 / /xxx/v1 → 只补 /chat/completions
 * - 普通地址 → 三个候选
 * - 尾部 / 被正确 trim
 */
class OpenAiChatEndpointResolverTest {

    // ═══ 已含 /chat/completions → 唯一候选 ═══

    @Test
    fun full_endpoint_returns_single_candidate() {
        val result = OpenAiChatEndpointResolver.candidates("https://xxx.com/v1/chat/completions")
        assertEquals(1, result.size)
        assertEquals("https://xxx.com/v1/chat/completions", result[0])
    }

    @Test
    fun full_endpoint_with_trailing_slash_returns_single_candidate() {
        val result = OpenAiChatEndpointResolver.candidates("https://xxx.com/v1/chat/completions/")
        assertEquals(1, result.size)
        assertEquals("https://xxx.com/v1/chat/completions", result[0])
    }

    // ═══ /v1 结尾 → 只补 /chat/completions ═══

    @Test
    fun v1_suffix_appends_only_chat_completions() {
        val result = OpenAiChatEndpointResolver.candidates("https://api.openai.com/v1")
        assertEquals(1, result.size)
        assertEquals("https://api.openai.com/v1/chat/completions", result[0])
    }

    @Test
    fun api_v1_suffix_appends_only_chat_completions() {
        val result = OpenAiChatEndpointResolver.candidates("https://openrouter.ai/api/v1")
        assertEquals(1, result.size)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", result[0])
    }

    @Test
    fun custom_v1_suffix_appends_only_chat_completions() {
        val result = OpenAiChatEndpointResolver.candidates("https://xxx.com/openai/v1")
        assertEquals(1, result.size)
        assertEquals("https://xxx.com/openai/v1/chat/completions", result[0])
    }

    // ═══ 普通地址 → 三个候选 ═══

    @Test
    fun deepseek_root_generates_three_candidates() {
        val result = OpenAiChatEndpointResolver.candidates("https://api.deepseek.com")
        assertEquals(3, result.size)
        assertEquals("https://api.deepseek.com/chat/completions", result[0])
        assertEquals("https://api.deepseek.com/v1/chat/completions", result[1])
        assertEquals("https://api.deepseek.com/api/v1/chat/completions", result[2])
    }

    @Test
    fun openai_root_generates_three_candidates() {
        val result = OpenAiChatEndpointResolver.candidates("https://api.openai.com")
        assertEquals(3, result.size)
        // OpenAI: 第一个 404，第二个成功
        assertEquals("https://api.openai.com/chat/completions", result[0])
        assertEquals("https://api.openai.com/v1/chat/completions", result[1])
        assertEquals("https://api.openai.com/api/v1/chat/completions", result[2])
    }

    @Test
    fun openrouter_root_generates_three_candidates() {
        val result = OpenAiChatEndpointResolver.candidates("https://openrouter.ai")
        assertEquals(3, result.size)
        // OpenRouter: 前两个 404，第三个成功
        assertEquals("https://openrouter.ai/chat/completions", result[0])
        assertEquals("https://openrouter.ai/v1/chat/completions", result[1])
        assertEquals("https://openrouter.ai/api/v1/chat/completions", result[2])
    }

    // ═══ 路径子目录 ═══

    @Test
    fun custom_path_generates_three_candidates() {
        val result = OpenAiChatEndpointResolver.candidates("https://xxx.com/openai")
        assertEquals(3, result.size)
        assertEquals("https://xxx.com/openai/chat/completions", result[0])
        assertEquals("https://xxx.com/openai/v1/chat/completions", result[1])
        assertEquals("https://xxx.com/openai/api/v1/chat/completions", result[2])
    }

    // ═══ trim / 尾部斜杠 ═══

    @Test
    fun trailing_slash_is_trimmed() {
        val result = OpenAiChatEndpointResolver.candidates("https://api.deepseek.com/")
        assertEquals(3, result.size)
        assertEquals("https://api.deepseek.com/chat/completions", result[0])
    }

    // ═══ 空输入 ═══

    @Test
    fun blank_input_returns_empty_list() {
        assertTrue(OpenAiChatEndpointResolver.candidates("").isEmpty())
        assertTrue(OpenAiChatEndpointResolver.candidates("   ").isEmpty())
    }

    // ═══ http:// loopback ═══

    @Test
    fun localhost_root_generates_three_candidates() {
        val result = OpenAiChatEndpointResolver.candidates("http://127.0.0.1:8080")
        assertEquals(3, result.size)
        assertEquals("http://127.0.0.1:8080/chat/completions", result[0])
        assertEquals("http://127.0.0.1:8080/v1/chat/completions", result[1])
        assertEquals("http://127.0.0.1:8080/api/v1/chat/completions", result[2])
    }
}
