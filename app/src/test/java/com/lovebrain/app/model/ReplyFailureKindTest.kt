package com.lovebrain.app.model

import org.junit.Assert.*
import org.junit.Test

/**
 * S1-03: ReplyFailureKind 错误映射 JVM 测试。
 *
 * 验证：
 * - fromException 正确映射常见异常类型
 * - fromErrorMessage 正确映射 CONFIG_ERROR / PARAM_UNSUPPORTED / 429 / 401 / 超时
 * - userMessage 不泄露路径/Provider/内部细节
 * - retryable 语义正确
 */
class ReplyFailureKindTest {

    // ═══ fromException ═══

    @Test
    fun `UnknownHostException maps to Network`() {
        val kind = ReplyFailureKind.fromException(java.net.UnknownHostException("host"))
        assertTrue(kind is ReplyFailureKind.Network)
    }

    @Test
    fun `SocketException maps to Network`() {
        val kind = ReplyFailureKind.fromException(java.net.SocketException("socket"))
        assertTrue(kind is ReplyFailureKind.Network)
    }

    @Test
    fun `ConnectException maps to Network`() {
        val kind = ReplyFailureKind.fromException(java.net.ConnectException("connect"))
        assertTrue(kind is ReplyFailureKind.Network)
    }

    @Test
    fun `SocketTimeoutException maps to Timeout`() {
        val kind = ReplyFailureKind.fromException(java.net.SocketTimeoutException("timeout"))
        assertTrue(kind is ReplyFailureKind.Timeout)
    }

    @Test
    fun `IOException maps to Network`() {
        val kind = ReplyFailureKind.fromException(java.io.IOException("io"))
        assertTrue(kind is ReplyFailureKind.Network)
    }

    @Test
    fun `RuntimeException maps to Unknown`() {
        val kind = ReplyFailureKind.fromException(RuntimeException("unexpected"))
        assertTrue(kind is ReplyFailureKind.Unknown)
        assertEquals("unexpected", (kind as ReplyFailureKind.Unknown).rawMessage)
    }

    // ═══ fromErrorMessage ═══

    @Test
    fun `CONFIG_ERROR with Key maps to Auth`() {
        val kind = ReplyFailureKind.fromErrorMessage("CONFIG_ERROR:API Key 缺失，请检查工单配置")
        assertTrue(kind is ReplyFailureKind.Auth)
    }

    @Test
    fun `CONFIG_ERROR with 地址 maps to ProviderMissing`() {
        val kind = ReplyFailureKind.fromErrorMessage("CONFIG_ERROR:接口地址未填写")
        assertTrue(kind is ReplyFailureKind.ProviderMissing)
    }

    @Test
    fun `CONFIG_ERROR generic maps to ProviderMissing`() {
        val kind = ReplyFailureKind.fromErrorMessage("CONFIG_ERROR:请先配置一个模型供应商")
        assertTrue(kind is ReplyFailureKind.ProviderMissing)
    }

    @Test
    fun `PARAM_UNSUPPORTED maps to ParamUnsupported`() {
        val kind = ReplyFailureKind.fromErrorMessage("PARAM_UNSUPPORTED:thinking")
        assertTrue(kind is ReplyFailureKind.ParamUnsupported)
    }

    @Test
    fun `429 in message maps to RateLimited`() {
        val kind = ReplyFailureKind.fromErrorMessage("请求过于频繁 429")
        assertTrue(kind is ReplyFailureKind.RateLimited)
    }

    @Test
    fun `401 in message maps to Auth`() {
        val kind = ReplyFailureKind.fromErrorMessage("401 Unauthorized")
        assertTrue(kind is ReplyFailureKind.Auth)
    }

    @Test
    fun `403 in message maps to Auth`() {
        val kind = ReplyFailureKind.fromErrorMessage("403 Forbidden")
        assertTrue(kind is ReplyFailureKind.Auth)
    }

    @Test
    fun `timeout in message maps to Timeout`() {
        val kind = ReplyFailureKind.fromErrorMessage("请求超时，请重试")
        assertTrue(kind is ReplyFailureKind.Timeout)
    }

    @Test
    fun `null message maps to Unknown`() {
        val kind = ReplyFailureKind.fromErrorMessage(null)
        assertTrue(kind is ReplyFailureKind.Unknown)
        assertNull((kind as ReplyFailureKind.Unknown).rawMessage)
    }

    @Test
    fun `unrecognized message maps to Unknown with rawMessage`() {
        val kind = ReplyFailureKind.fromErrorMessage("something weird happened")
        assertTrue(kind is ReplyFailureKind.Unknown)
        assertEquals("something weird happened", (kind as ReplyFailureKind.Unknown).rawMessage)
    }

    // ═══ userMessage 不泄露内部细节 ═══

    @Test
    fun `userMessage does not contain file paths`() {
        for (kind in listOf(
            ReplyFailureKind.ProviderMissing,
            ReplyFailureKind.Auth,
            ReplyFailureKind.Network,
            ReplyFailureKind.Timeout,
            ReplyFailureKind.RateLimited,
            ReplyFailureKind.PromptRead,
            ReplyFailureKind.Parse,
            ReplyFailureKind.Storage,
            ReplyFailureKind.ParamUnsupported,
            ReplyFailureKind.Unknown("/data/data/com.lovebrain.app/files/knowledge/test.md")
        )) {
            val msg = kind.userMessage
            assertFalse("userMessage should not contain file path: $msg",
                msg.contains("/data/") || msg.contains(".md"))
        }
    }

    @Test
    fun `Unknown userMessage does not leak rawMessage`() {
        val kind = ReplyFailureKind.Unknown("CONFIG_ERROR:/path/to/secret.key")
        val msg = kind.userMessage
        assertFalse("Unknown.userMessage should not leak rawMessage: $msg",
            msg.contains("/path/") || msg.contains(".key") || msg.contains("CONFIG_ERROR"))
    }

    // ═══ retryable ═══

    @Test
    fun `ProviderMissing and Auth are not retryable`() {
        assertFalse(ReplyFailureKind.ProviderMissing.retryable)
        assertFalse(ReplyFailureKind.Auth.retryable)
    }

    @Test
    fun `Network Timeout RateLimited Parse Storage ParamUnsupported are retryable`() {
        assertTrue(ReplyFailureKind.Network.retryable)
        assertTrue(ReplyFailureKind.Timeout.retryable)
        assertTrue(ReplyFailureKind.RateLimited.retryable)
        assertTrue(ReplyFailureKind.Parse.retryable)
        assertTrue(ReplyFailureKind.Storage.retryable)
        assertTrue(ReplyFailureKind.ParamUnsupported.retryable)
    }

    @Test
    fun `Unknown is retryable`() {
        assertTrue(ReplyFailureKind.Unknown("test").retryable)
    }
}
