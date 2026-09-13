package com.lovebrain.app.util

/**
 * URL-01：OpenAI Chat Completions endpoint 候选生成器（纯函数，无 Android 依赖）。
 *
 * 用户可能填入：
 * - 根地址：https://api.deepseek.com
 * - /v1 地址：https://api.openai.com/v1
 * - /api/v1 地址：https://openrouter.ai/api/v1
 * - 完整 endpoint：https://xxx.com/v1/chat/completions
 *
 * 本工具根据输入生成有序候选列表，供 [com.lovebrain.app.data.DeepSeekRepository] 逐个探测。
 * 探测规则（在 DeepSeekRepository.testConnectionWithProbe 中实现）：
 * - 404 / 405 → 继续下一个候选
 * - 401 / 403 / 400 / 429 / 5xx / DNS / 超时 → 立即停止
 *
 * 候选规则：
 * 1. 已含 /chat/completions → 唯一候选
 * 2. 结尾是 /v1 / /api/v1 / /xxx/v1 → 只补 /chat/completions
 * 3. 其他普通地址 → 三个候选：{base}/chat/completions、{base}/v1/chat/completions、{base}/api/v1/chat/completions
 */
object OpenAiChatEndpointResolver {

    private const val CHAT_COMPLETIONS = "/chat/completions"

    /**
     * 根据用户输入生成有序候选 endpoint 列表。
     * 输入应为已经 trim + 去尾 / 的合法 http(s) URL。
     */
    fun candidates(rawInput: String): List<String> {
        val base = rawInput.trim().trimEnd('/')
        if (base.isBlank()) return emptyList()

        // 1. 已含 /chat/completions → 唯一候选
        if (base.endsWith(CHAT_COMPLETIONS, ignoreCase = true)) {
            return listOf(base)
        }

        // 2. 结尾是 /v1 或 /api/v1 或 /xxx/v1 → 只补 /chat/completions
        val path = runCatching { java.net.URI(base).path.orEmpty() }.getOrDefault("")
        if (path.isNotEmpty() && path.endsWith("/v1", ignoreCase = true)) {
            return listOf(base + CHAT_COMPLETIONS)
        }

        // 3. 普通地址 → 三个候选
        return listOf(
            base + CHAT_COMPLETIONS,
            base + "/v1" + CHAT_COMPLETIONS,
            base + "/api/v1" + CHAT_COMPLETIONS
        )
    }
}
