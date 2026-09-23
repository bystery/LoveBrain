package com.lovebrain.app.data

import com.lovebrain.app.model.ProviderFailure
import com.lovebrain.app.model.ReplyFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 边界产物的两条历史回归锚。
 *
 * 1) 400 + thinking 族的兜底文案：早期写成"已自动降级"是失真描述（走到该分支时降级链已用尽），
 *    换成如实指引后经 internal 放开直测（先例 = HttpsTrustGuard，companion 成员无需构造 Repository）。
 * 2) 分类结果不得再把内部标记当文案外泄：旧实现用 "PARAM_UNSUPPORTED:" / "CONFIG_ERROR:" 前缀
 *    在字符串里夹带控制流信息，下游靠 startsWith 反推。现在控制流在 kind 上，message 只是给用户看的话。
 */
class DeepSeekRepositoryErrorMappingTest {

    @Test
    fun thinking_400_fallback_is_honest_guidance() {
        // raw 含 thinking 族关键词但不命中参数不支持族（无 unknown parameter/unsupported/invalid field）
        assertEquals(
            "模型不支持思考模式参数，请切换直出模式后重试",
            DeepSeekRepository.classifyApiError("Invalid value for thinking", code = 400).message
        )
    }

    @Test
    fun `classification result never carries an internal marker in the user copy`() {
        val samples = listOf(
            DeepSeekRepository.classifyApiError("Unknown parameter: 'thinking'", 400),
            DeepSeekRepository.classifyApiError("unsupported media type", 415),
            DeepSeekRepository.classifyApiError("invalid api key", 401),
            DeepSeekRepository.classifyApiError("insufficient_balance", 402),
            DeepSeekRepository.classifyApiError("", 0)
        )
        samples.forEach { failure ->
            listOf("PARAM_UNSUPPORTED", "CONFIG_ERROR").forEach { marker ->
                assertFalse(
                    "${failure.kind} 的展示文案里不该残留内部标记 $marker：${failure.message}",
                    failure.message.contains(marker)
                )
            }
        }
    }

    @Test
    fun `unknown provider failure keeps a fixed copy on the reply path`() {
        val failure = ProviderFailure(ReplyFailureKind.Unknown("raw english boom"))
        // 未归类的英文原文留在 kind 里供诊断，UI 拿到的是固定话术
        assertEquals("生成失败，请重试", failure.message)
        assertEquals("raw english boom", (failure.kind as ReplyFailureKind.Unknown).rawMessage)
    }
}
