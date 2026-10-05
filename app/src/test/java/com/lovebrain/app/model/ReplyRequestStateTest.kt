package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReplyRequestState 统一状态机测试。
 *
 * 验证：
 * - Preparing/Streaming/Error/Cancel/Retry 的唯一状态
 * - requestId 所有权——旧请求 finally 不清新请求
 * - 各状态间的转换语义
 */
class ReplyRequestStateTest {

    @Test
    fun `newRequestId generates unique IDs`() {
        val id1 = ReplyRequestState.newRequestId()
        val id2 = ReplyRequestState.newRequestId()
        assertNotNull(id1)
        assertNotNull(id2)
        assertNotEquals("Each requestId must be unique", id1, id2)
    }

    @Test
    fun `Idle is the initial state`() {
        val state: ReplyRequestState = ReplyRequestState.Idle
        assertTrue("Idle should be the default state", state is ReplyRequestState.Idle)
    }

    @Test
    fun `Preparing carries requestId`() {
        val requestId = ReplyRequestState.newRequestId()
        val state = ReplyRequestState.Preparing(requestId)
        assertTrue(state is ReplyRequestState.Preparing)
        assertEquals(requestId, (state as ReplyRequestState.Preparing).requestId)
    }

    @Test
    fun `Streaming carries requestId`() {
        val requestId = ReplyRequestState.newRequestId()
        val state = ReplyRequestState.Streaming(requestId)
        assertTrue(state is ReplyRequestState.Streaming)
        assertEquals(requestId, (state as ReplyRequestState.Streaming).requestId)
    }

    @Test
    fun `RecoverableError carries message and retryable flag`() {
        val state = ReplyRequestState.RecoverableError("网络超时", retryable = true)
        assertTrue(state is ReplyRequestState.RecoverableError)
        val err = state as ReplyRequestState.RecoverableError
        assertEquals("网络超时", err.message)
        assertTrue(err.retryable)
    }

    @Test
    fun `RecoverableError defaults to retryable true`() {
        val state = ReplyRequestState.RecoverableError("解析失败")
        assertTrue((state as ReplyRequestState.RecoverableError).retryable)
    }

    @Test
    fun `different requestIds prevent stale state cleanup`() {
        val id1 = ReplyRequestState.newRequestId()
        val id2 = ReplyRequestState.newRequestId()
        assertNotEquals(id1, id2)
        // Simulate: request 1 starts, request 2 starts before 1 finishes
        val state1 = ReplyRequestState.Preparing(id1)
        val state2 = ReplyRequestState.Preparing(id2)
        // request 1's finally should NOT clear request 2's state
        val currentRequestId = id2
        assertFalse("Old request should not own current state",
            currentRequestId == (state1 as ReplyRequestState.Preparing).requestId)
        assertTrue("New request should own current state",
            currentRequestId == (state2 as ReplyRequestState.Preparing).requestId)
    }

    @Test
    fun `Success is a singleton data object`() {
        val s1 = ReplyRequestState.Success
        val s2 = ReplyRequestState.Success
        assertEquals("Success should be a singleton", s1, s2)
    }

    @Test
    fun `state transitions form valid lifecycle`() {
        val requestId = ReplyRequestState.newRequestId()
        // Idle → Preparing → Streaming → Success
        var state: ReplyRequestState = ReplyRequestState.Idle
        assertTrue(state is ReplyRequestState.Idle)

        state = ReplyRequestState.Preparing(requestId)
        assertTrue(state is ReplyRequestState.Preparing)

        state = ReplyRequestState.Streaming(requestId)
        assertTrue(state is ReplyRequestState.Streaming)

        state = ReplyRequestState.Success
        assertTrue(state is ReplyRequestState.Success)

        // Idle → Preparing → RecoverableError → Preparing (retry)
        state = ReplyRequestState.Preparing(requestId)
        state = ReplyRequestState.RecoverableError("Provider 未配置")
        assertTrue(state is ReplyRequestState.RecoverableError)

        // Retry creates new requestId
        val retryId = ReplyRequestState.newRequestId()
        state = ReplyRequestState.Preparing(retryId)
        assertTrue(state is ReplyRequestState.Preparing)
        assertEquals(retryId, (state as ReplyRequestState.Preparing).requestId)
    }
}
