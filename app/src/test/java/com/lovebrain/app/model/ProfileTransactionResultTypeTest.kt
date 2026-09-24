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

    /**
     * 这条不是"顺手数个数"：新增一种失败原因必须是一次显式的决定——
     * 加进来就得同时回答"UI 拿它怎么办"（`ProfileTransactionResultTest` 三种各自一格）。
     *
     * `LIBRARY_READ_ONLY` 是本轮加的：只读库上画像事务以前会一路走到 `Success`。
     * 它与前两种的分工要留住——前两种=建议作废（清卡），这一种=这次写不动（留着卡）。
     */
    @Test
    fun `PreconditionReason has exactly 3 values`() {
        assertEquals(3, PreconditionReason.entries.size)
        assertEquals(PreconditionReason.KB_NOT_FOUND, PreconditionReason.entries[0])
        assertEquals(PreconditionReason.REVISION_CONFLICT, PreconditionReason.entries[1])
        assertEquals(PreconditionReason.LIBRARY_READ_ONLY, PreconditionReason.entries[2])
    }
}
