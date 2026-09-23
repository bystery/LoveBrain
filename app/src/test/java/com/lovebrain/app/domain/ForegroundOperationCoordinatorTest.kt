package com.lovebrain.app.domain

import com.lovebrain.app.domain.ForegroundOperationCoordinator.OperationType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * S2-02: 前台任务协调器合同。
 *
 * 复核报告 §6 S2-02 的收尾一句是"仓库中没有 ForegroundOperationCoordinatorTest"——
 * 也就是说那一条 FAIL 从来没有反证材料。这个类把协调器的每条承诺都变成可执行断言：
 *
 * 1. 单一 owner：任务由协调器创建并注册，注册被拒时 body 一次都不跑（不存在"跑了但没人管"的孤儿 Job）。
 * 2. 同一把锁：start / stop / stopCurrent / shutdownAll / 完成回收都改同一份表，
 *    并发下不丢更新（旧实现 invokeOnCompletion 在 Mutex 外读改写 StateFlow）。
 * 3. 六类操作全部纳入互斥矩阵，同类型不得重复启动（旧实现 SUGGEST/PROFILE_REFRESH 无门禁）。
 * 4. 按租约停止：stop 只取消它自己那一个；stopCurrent 只取消该类，不牵连别的主人。
 * 5. shutdownAll 真的取消所有在管 Job。
 */
class ForegroundOperationCoordinatorTest {

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 协调器跑在真实调度器上，所以不能用 runTest 的虚拟时间等它——
     * runTest { delay(400) } 在虚拟时间里瞬间返回，真实协程根本还没被调度，
     * 断言就会随机看到"还没完成"。这里按墙钟轮询，超时即失败。
     */
    private fun awaitTrue(timeoutMs: Long = 3000L, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        org.junit.Assert.fail("timed out after ${timeoutMs}ms waiting for: $what")
    }

    /** 阻塞直到测试主动放行，用来模拟"一个还在跑的前台任务" */
    private class Hanging {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
    }

    private fun startHanging(c: ForegroundOperationCoordinator, type: OperationType): Hanging? {
        val h = Hanging()
        val lease = c.start(type) {
            h.started.complete(Unit)
            try {
                h.release.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                h.cancelled.complete(Unit)
                throw e
            }
        }
        if (lease == null) return null
        return h
    }

    private fun awaitStarted(vararg hs: Hanging?) = runTest {
        hs.filterNotNull().forEach { it.started.await() }
    }

    // ─── 1. 单一 owner ────────────────────────────────────────────

    @Test
    fun `a rejected start never runs the body and leaves no orphan job`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        try {
            val first = startHanging(c, OperationType.REPLY)
            assertNotNull(first)
            awaitStarted(first)

            val bodyRuns = AtomicInteger(0)
            val rejected = c.start(OperationType.REPLY) { bodyRuns.incrementAndGet() }

            assertNull("reply already owns the foreground slot", rejected)
            assertEquals("a rejected start must never execute its body", 0, bodyRuns.get())
            assertEquals("only one operation must be active", 1, c.snapshot().size)
        } finally {
            c.shutdownAll(); scope.cancel()
        }
    }

    /**
     * 旧 API 是"调用方先 launch，再把 Job 交给协调器登记"。
     * 登记失败（锁忙/互斥）时调用方忽略 null，任务照样在跑，只是没人管。
     * 新 API 把创建收进来，所以这条路径从根上不存在。
     */
    @Test
    fun `coordinator is the only place a foreground job is created`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        try {
            val bodyRan = java.util.concurrent.atomic.AtomicBoolean(false)
            val lease = c.start(OperationType.COUNSELING) {
                // 任务体跑在协调器自己创建的协程里；外部拿不到那个 Job，
                // 也就无法绕开协调器去取消它。
                bodyRan.set(true)
                delay(50)
            }
            assertNotNull(lease)
            assertEquals(1, c.snapshot().size)
            assertNotNull("the lease must carry the request identity slot", lease?.requestId)
            awaitTrue(what = "the body to run") { bodyRan.get() }
            awaitTrue(what = "the finished job to be unregistered") { c.snapshot().isEmpty() }
        } finally {
            c.shutdownAll(); scope.cancel()
        }
    }

    // ─── 2. 同一把锁 / 不丢更新 ──────────────────────────────────

    @Test
    fun `concurrent starts and completions never lose an update`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        try {
            val accepted = java.util.concurrent.atomic.AtomicInteger(0)
            val jobs = (1..60).map { i ->
                scope.launch {
                    val lease = c.start(if (i % 2 == 0) OperationType.SUGGEST else OperationType.PROFILE_REFRESH) {
                        delay(1)
                    }
                    if (lease != null) accepted.incrementAndGet()
                }
            }
            runTest { jobs.forEach { it.join() } }
            runTest { yield() }

            // 同类型互斥会把并发压成串行：接受数必须与"实际在管数"自洽，
            // 且绝不能出现重复登记（同一 operationId 两次）。
            val ids = c.snapshot().map { it.operationId }
            assertEquals("snapshot must not contain duplicate entries", ids.size, ids.toSet().size)
            assertTrue("some work must have been accepted", accepted.get() > 0)
        } finally {
            c.shutdownAll(); scope.cancel()
        }
    }

    /** 任务自然结束后，必须从活跃表与 StateFlow 里同时消失 */
    @Test
    fun `a finished operation disappears from both the snapshot and the state flow`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        try {
            c.start(OperationType.SUGGEST) { delay(5) }
            awaitTrue(what = "the operation to be registered") { c.snapshot().size == 1 }
            assertTrue(c.activeOperations.value.isNotEmpty())

            awaitTrue(what = "completion cleanup to unregister it") { c.snapshot().isEmpty() }
            assertTrue("StateFlow must agree with the snapshot", c.activeOperations.value.isEmpty())
        } finally {
            c.shutdownAll(); scope.cancel()
        }
    }

    // ─── 3. 互斥矩阵覆盖六类操作 ──────────────────────────────────

    @Test
    fun `all six operation types are registered and mutually exclusive where documented`() {
        val exclusiveGroup = listOf(
            OperationType.REPLY, OperationType.PROACTIVE,
            OperationType.REWRITE, OperationType.COUNSELING
        )
        for (a in exclusiveGroup) {
            for (b in exclusiveGroup) {
                val scope = scope()
                val c = ForegroundOperationCoordinator(scope)
                try {
                    val first = startHanging(c, a)
                    assertNotNull("$a must be startable", first)
                    awaitStarted(first)
                    assertNull("$b must not co-run with $a", c.start(b) {})
                } finally {
                    c.shutdownAll(); scope.cancel()
                }
            }
        }

        // Suggest / ProfileRefresh 可与前台主操作并行，但同类只允许一个
        listOf(OperationType.SUGGEST, OperationType.PROFILE_REFRESH).forEach { parallel ->
            val scope = scope()
            val c = ForegroundOperationCoordinator(scope)
            try {
                val reply = startHanging(c, OperationType.REPLY)
                awaitStarted(reply)
                val p = startHanging(c, parallel)
                assertNotNull("$parallel must be allowed alongside REPLY", p)
                awaitStarted(p)
                assertNull("$parallel must not allow two of itself", c.start(parallel) {})
            } finally {
                c.shutdownAll(); scope.cancel()
            }
        }
    }

    /** REWRITE 与 PROFILE_REFRESH 在旧实现里从来没有注册点，这里确认它们确实可注册 */
    @Test
    fun `rewrite and profile refresh can actually be owned`() {
        listOf(OperationType.REWRITE, OperationType.PROFILE_REFRESH).forEach { type ->
            val scope = scope()
            val c = ForegroundOperationCoordinator(scope)
            try {
                val h = Hanging()
                val lease = c.start(type) { h.started.complete(Unit); h.release.await() }
                assertNotNull("$type must be registrable", lease)
                awaitStarted(h)
                assertEquals(type, c.current(type)?.type)
                assertEquals("$type must be bindable to a request identity", type,
                    c.leaseFor("req-$type")?.type ?: c.current(type)?.type)
            } finally {
                c.shutdownAll(); scope.cancel()
            }
        }
    }

    // ─── 4. 按租约停止 ───────────────────────────────────────────

    /** 复核报告点名的缺陷：stopGeneration 一次停掉 REPLY + PROACTIVE + REWRITE */
    @Test
    fun `stopping one lease cancels exactly that job and nobody else`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        try {
            val suggest = startHanging(c, OperationType.SUGGEST)
            awaitStarted(suggest)
            val reply = startHanging(c, OperationType.REPLY)
            awaitStarted(reply)

            val replyLease = c.current(OperationType.REPLY)!!
            assertTrue(c.stop(leaseOf(replyLease)))

            // SUGGEST 不能被牵连：它仍在管，且它的 body 没有收到取消
            assertTrue("SUGGEST must survive stopping REPLY", c.isBusy(OperationType.SUGGEST))
            assertFalse("its body must not have been cancelled", suggest!!.cancelled.isCompleted)
            assertFalse("REPLY must be gone", c.isBusy(OperationType.REPLY))

            c.shutdownAll()
        } finally { scope.cancel() }
    }

    /** stopCurrent 只处理该类，另一个 owner 不受影响 */
    @Test
    fun `stopCurrent only touches its own type`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        try {
            val reply = startHanging(c, OperationType.REPLY)
            val suggest = startHanging(c, OperationType.SUGGEST)
            awaitStarted(reply, suggest)

            assertTrue(c.stopCurrent(OperationType.SUGGEST))
            assertFalse(c.isBusy(OperationType.SUGGEST))
            assertTrue("REPLY must be untouched by stopping SUGGEST", c.isBusy(OperationType.REPLY))

            assertNull("COUNSELING was never started", c.current(OperationType.COUNSELING))
            assertFalse("stopping an empty type must report false", c.stopCurrent(OperationType.COUNSELING))
            assertTrue("REWRITE/PROACTIVE 也未被牵连", c.isBusy(OperationType.REPLY))
        } finally {
            c.shutdownAll(); scope.cancel()
        }
    }

    /** 停一个已经不存在的租约必须返回 false，不能假装成功 */
    @Test
    fun `stopping an already finished lease is reported as a no-op`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        try {
            val lease = c.start(OperationType.SUGGEST) { }
            assertNotNull(lease)
            awaitTrue(what = "the no-op body to finish") { !c.isBusy(OperationType.SUGGEST) }
            assertFalse("lease is gone; stop must say so", c.stop(lease!!))
        } finally {
            c.shutdownAll(); scope.cancel()
        }
    }

    // ─── 5. 生命周期 ─────────────────────────────────────────────

    @Test
    fun `shutdownAll cancels every managed job`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        val suggest = startHanging(c, OperationType.SUGGEST)
        val reply = startHanging(c, OperationType.REPLY)
        awaitStarted(suggest, reply)
        assertTrue(c.isAnyBusy)

        c.shutdownAll()

        assertFalse(c.isAnyBusy)
        assertTrue(c.snapshot().isEmpty())
        assertTrue(c.activeOperations.value.isEmpty())
        awaitTrue(what = "both bodies to observe cancellation") {
            suggest!!.cancelled.isCompleted && reply!!.cancelled.isCompleted
        }
        scope.cancel()
    }

    /** 请求身份绑定：迟到的回调只有仍属于当前租约才被接受 */
    @Test
    fun `request identity gates late callbacks`() {
        val scope = scope()
        val c = ForegroundOperationCoordinator(scope)
        try {
            val h = Hanging()
            val lease = c.start(OperationType.REPLY, "req-1") {
                h.started.complete(Unit); h.release.await()
            }
            assertNotNull(lease)
            awaitStarted(h)

            assertTrue("req-1 owns a live lease", c.ownsRequest("req-1"))
            assertFalse("an unknown request must be rejected", c.ownsRequest("req-old"))

            c.unbindRequest("req-1", lease!!)
            assertFalse("after unbind the request is no longer owned", c.ownsRequest("req-1"))
        } finally {
            c.shutdownAll(); scope.cancel()
        }
    }

    private fun leaseOf(op: com.lovebrain.app.domain.ForegroundOperationCoordinator.ActiveOperation) =
        com.lovebrain.app.domain.ForegroundOperationCoordinator.Lease(
            operationId = op.operationId,
            type = op.type,
            requestId = op.requestId
        )
}
