package com.lovebrain.app.model

import org.junit.Assert.*
import org.junit.Test

/**
 * ReplyFailureKind 错误映射 JVM 测试。
 *
 * 验证：
 * - fromException 按**异常类型**映射（不解析 message 文本）
 * - 模型上不存在从字符串前缀反推类型的 API
 * - userMessage 不泄露路径/Provider/内部细节，且 kind 与文案一一对应
 * - retryable / isConfigProblem 语义正确
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

    // ═══ 错误类型不得再从字符串反推（S2-07）═══

    @Test
    fun `no message-parsing api exists on the failure model`() {
        val names = ReplyFailureKind::class.java.declaredMethods.map { it.name } +
            ReplyFailureKind.Companion::class.java.declaredMethods.map { it.name }
        listOf("fromErrorMessage", "fromMessage", "classifyByMessage", "parse").forEach { banned ->
            assertFalse(
                "$banned 不得回来：错误类型一旦能从字符串前缀反推，分类就会散落到每个消费点",
                names.contains(banned)
            )
        }
    }

    @Test
    fun `config problems are declared by kind not by prefix`() {
        assertTrue(ReplyFailureKind.ProviderMissing.isConfigProblem)
        assertTrue(ReplyFailureKind.Auth.isConfigProblem)
        assertTrue(ReplyFailureKind.InvalidAddress.isConfigProblem)
        listOf(
            ReplyFailureKind.Network,
            ReplyFailureKind.Timeout,
            ReplyFailureKind.RateLimited,
            ReplyFailureKind.InsufficientBalance,
            ReplyFailureKind.ContentFiltered,
            ReplyFailureKind.ContextTooLong,
            ReplyFailureKind.ServerBusy,
            ReplyFailureKind.ParamUnsupported,
            ReplyFailureKind.ProviderChanged,
            ReplyFailureKind.Parse,
            ReplyFailureKind.Storage,
            ReplyFailureKind.PromptRead,
            ReplyFailureKind.Unknown("boom")
        ).forEach { assertFalse("$it 不是配置问题", it.isConfigProblem) }
    }

    @Test
    fun `every kind has distinct non-empty copy`() {
        val all = listOf(
            ReplyFailureKind.ProviderMissing,
            ReplyFailureKind.InvalidAddress,
            ReplyFailureKind.Auth,
            ReplyFailureKind.Network,
            ReplyFailureKind.Timeout,
            ReplyFailureKind.RateLimited,
            ReplyFailureKind.InsufficientBalance,
            ReplyFailureKind.ContentFiltered,
            ReplyFailureKind.ContextTooLong,
            ReplyFailureKind.ServerBusy,
            ReplyFailureKind.PromptRead,
            ReplyFailureKind.Parse,
            ReplyFailureKind.Storage,
            ReplyFailureKind.ParamUnsupported,
            ReplyFailureKind.ProviderChanged,
            ReplyFailureKind.Unknown("x")
        )
        all.forEach { assertTrue("${it::class.simpleName} 必须有文案", it.userMessage.isNotBlank()) }
        assertEquals(
            "两种 kind 撞同一句话明着是漏了分支",
            all.size,
            all.map { it.userMessage }.distinct().size
        )
    }

    @Test
    fun `coroutine timeout exception is classified by type`() {
        // TimeoutCancellationException 的构造器是 internal，只能让真实超时产生它
        val caught: Throwable = try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(1L) { kotlinx.coroutines.delay(5_000L) }
            }
            AssertionError("expected withTimeout to fire")
        } catch (e: Throwable) {
            e
        }
        assertEquals(ReplyFailureKind.Timeout, ReplyFailureKind.fromException(caught))
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
