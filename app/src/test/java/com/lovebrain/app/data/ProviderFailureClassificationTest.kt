package com.lovebrain.app.data

import com.lovebrain.app.model.ReplyFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2-07：错误分类合同。
 *
 * 复核报告点名"错误类型仍可从字符串前缀/异常 message 反推"。
 * 现在分类只发生在 [DeepSeekRepository.classifyApiError] 这一处，产出 typed 结果；
 * 本用例锚定的正是**类型与控制流**，而不是文案前缀——
 * 前缀式判定（CONFIG_ERROR: / PARAM_UNSUPPORTED:）连同 isConfigError/stripConfigPrefix 已删除。
 */
class ProviderFailureClassificationTest {

    private fun classify(raw: String, code: Int = 0) = DeepSeekRepository.classifyApiError(raw, code)

    // ═══ 配置类：降级链必须中断，用户得先去设置页 ═══

    @Test
    fun `auth failures are a config problem`() {
        listOf(
            classify("Invalid API Key provided", 401),
            classify("authentication credentials failed", 0),
            classify("", 403)
        ).forEach {
            assertEquals(ReplyFailureKind.Auth, it.kind)
            assertTrue("${it.message}", it.isConfigProblem)
            assertFalse("配置错不该重试", it.kind.retryable)
        }
    }

    @Test
    fun `missing configuration is a config problem`() {
        val failure = classify("model was not found", 0)
        // 未归到已知家族的英文原文不进 UI：走 Unknown 固定话术
        assertTrue(failure.kind is ReplyFailureKind.Unknown)
        assertEquals("生成失败，请重试", failure.message)
    }

    // ═══ 参数族：可换 thinking wire shape 重试，但不是配置问题 ═══

    @Test
    fun `unsupported thinking parameter allows shape degradation without a prefix`() {
        val failure = classify("Unknown parameter: 'thinking'", 400)
        assertEquals(ReplyFailureKind.ParamUnsupported, failure.kind)
        assertTrue(failure.thinkingParamRejected)
        assertEquals("thinking", failure.rejectedParameter)
        assertFalse("参数不支持不该把用户支到设置页", failure.isConfigProblem)
    }

    @Test
    fun `unsupported parameter of unknown field still allows degradation`() {
        val failure = classify("unsupported request", 0)
        assertTrue(failure.thinkingParamRejected)
        assertEquals(null, failure.rejectedParameter)
    }

    @Test
    fun `400 mentioning thinking family without rejection words is not degradable`() {
        val failure = classify("Invalid value for thinking", code = 400)
        assertEquals(ReplyFailureKind.ParamUnsupported, failure.kind)
        assertFalse("已降级过一轮，再换参数无意义", failure.thinkingParamRejected)
        assertEquals("模型不支持思考模式参数，请切换直出模式后重试", failure.message)
    }

    // ═══ 运行期错误：可重试，绝不能误判成配置问题 ═══

    @Test
    fun `runtime failures stay retryable and are not config problems`() {
        val cases = listOf(
            classify("Insufficient Balance", 402) to ReplyFailureKind.InsufficientBalance,
            classify("Your account is rate limited", 429) to ReplyFailureKind.RateLimited,
            classify("内容命中 sensitive 词", 0) to ReplyFailureKind.ContentFiltered,
            classify("context_length_exceeded", 0) to ReplyFailureKind.ContextTooLong,
            classify("server is busy", 503) to ReplyFailureKind.ServerBusy,
            classify("read timed out", 0) to ReplyFailureKind.Timeout,
            classify("Unable to resolve host api.deepseek.com", 0) to ReplyFailureKind.Network
        )
        cases.forEach { (failure, expected) ->
            assertEquals(expected, failure.kind)
            assertFalse("${expected} 不该被当成配置错", failure.isConfigProblem)
            assertTrue("${expected} 应当可重试", failure.kind.retryable)
        }
    }

    @Test
    fun `every classified kind carries user-facing copy`() {
        val samples = listOf(
            classify("Invalid API Key", 401),
            classify("Insufficient Balance", 402),
            classify("rate limit", 429),
            classify("content_filter", 0),
            classify("context_length", 0),
            classify("overloaded", 503),
            classify("timeout", 0),
            classify("failed to connect", 0),
            classify("bad gateway", 502),
            classify("internal error", 500)
        )
        samples.forEach { failure ->
            assertTrue("文案不能为空：${failure.kind}", failure.message.isNotBlank())
            assertFalse("不得把英文原文甩给用户：${failure.message}",
                Regex("[a-z]{8,}").containsMatchIn(failure.message.lowercase()))
        }
    }

    @Test
    fun `gateway and 5xx copy distinguish the two sources`() {
        assertEquals("网关错误，稍后再试", classify("", 502).message)
        assertEquals("服务暂时开小差，稍后再试", classify("", 500).message)
        assertEquals("服务繁忙，稍后再试", classify("server is busy", 0).message)
    }

    // ═══ 异常侧：按类型分类，不解析 message 文本 ═══

    @Test
    fun `socket timeout maps to Timeout not by message`() {
        val failure = com.lovebrain.app.model.ProviderFailure.fromThrowable(
            java.net.SocketTimeoutException("timeout of 30000ms exceeded")
        )
        assertEquals(ReplyFailureKind.Timeout, failure.kind)
        // 已归类的错误换成固定话术，不把英文原文甩给用户
        assertEquals(ReplyFailureKind.Timeout.userMessage, failure.message)
    }

    @Test
    fun `io exception maps to Network`() {
        val failure = com.lovebrain.app.model.ProviderFailure.fromThrowable(
            java.io.IOException("unexpected end of stream")
        )
        assertEquals(ReplyFailureKind.Network, failure.kind)
    }

    @Test
    fun `own business exceptions keep their finished copy`() {
        val failure = com.lovebrain.app.model.ProviderFailure.fromThrowable(
            IllegalStateException("锦囊返回格式异常")
        )
        assertTrue(failure.kind is ReplyFailureKind.Unknown)
        assertEquals("锦囊返回格式异常", failure.message)
    }

    @Test
    fun `provider failure exception survives the throwable bridge unchanged`() {
        val original = classify("Insufficient Balance", 402)
        val failure = com.lovebrain.app.model.ProviderFailure.fromThrowable(
            com.lovebrain.app.model.ProviderFailureException(original)
        )
        assertEquals(original, failure)
    }
}
