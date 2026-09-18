package com.lovebrain.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-6 回归测试：ProfileTransactionResult 类型完整性。
 *
 * 确保事务结果的四种终态能正确区分，
 * 调用方可据此给出精确的 UI 反馈。
 */
class ProfileTransactionResultTypeTest {

    @Test
    fun `Success is a data object`() {
        val result = ProfileTransactionResult.Success
        assertEquals("Success", result::class.simpleName)
    }

    @Test
    fun `PreconditionFailed carries reason`() {
        val result = ProfileTransactionResult.PreconditionFailed(PreconditionReason.KB_NOT_FOUND)
        assertEquals(PreconditionReason.KB_NOT_FOUND, result.reason)

        val result2 = ProfileTransactionResult.PreconditionFailed(PreconditionReason.REVISION_CONFLICT)
        assertEquals(PreconditionReason.REVISION_CONFLICT, result2.reason)
    }

    @Test
    fun `RolledBack carries cause`() {
        val cause = RuntimeException("disk full")
        val result = ProfileTransactionResult.RolledBack(cause)
        assertEquals(cause, result.cause)
        assertEquals("disk full", result.cause.message)
    }

    @Test
    fun `RollbackFailed carries cause and failed paths`() {
        val cause = RuntimeException("io error")
        val paths = listOf("understand/me.md", "understand/warmth.md")
        val result = ProfileTransactionResult.RollbackFailed(cause, paths)
        assertEquals(cause, result.cause)
        assertEquals(paths, result.failedPaths)
    }

    @Test
    fun `all four results are distinct types`() {
        val success = ProfileTransactionResult.Success
        val precondition = ProfileTransactionResult.PreconditionFailed(PreconditionReason.KB_NOT_FOUND)
        val rolledBack = ProfileTransactionResult.RolledBack(RuntimeException("e1"))
        val rollbackFailed = ProfileTransactionResult.RollbackFailed(RuntimeException("e2"), emptyList())

        assertTrue(success::class != precondition::class)
        assertTrue(success::class != rolledBack::class)
        assertTrue(success::class != rollbackFailed::class)
        assertTrue(precondition::class != rolledBack::class)
        assertTrue(precondition::class != rollbackFailed::class)
        assertTrue(rolledBack::class != rollbackFailed::class)
    }

    @Test
    fun `PreconditionReason has exactly 2 values`() {
        assertEquals(2, PreconditionReason.entries.size)
        assertEquals(PreconditionReason.KB_NOT_FOUND, PreconditionReason.entries[0])
        assertEquals(PreconditionReason.REVISION_CONFLICT, PreconditionReason.entries[1])
    }
}
