package com.lovebrain.app.domain

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * S2-02: 前台任务协调器——所有前台 AI 流程唯一的任务真源。
 *
 * ## 这里解决的三个具体缺陷
 * 1. **单一 owner**：调用方不再"先 launch 再注册"。任务由协调器在锁内创建
 *    （[CoroutineStart.LAZY]，尚未启动），注册成功才 `start()`，被拒绝则直接 `cancel()`。
 *    因此不存在"注册失败但任务照跑、只是不受协调器管理"的孤儿 Job。
 * 2. **同一把锁**：start / stop / stopCurrent / shutdownAll / invokeOnCompletion
 *    全部经过 [lock] 读写同一份表。旧实现用 Mutex 保护 start，
 *    却在 Mutex 外直接读改写 StateFlow，并与非挂起的 stop 并发，会丢更新。
 *    这里用 [ReentrantLock]：非挂起路径（停止按钮、destroy）也能走同一条锁。
 * 3. **按 lease 停止**：[stop] 只取消传入租约对应的那一个 Job。
 *    UI 的"停止"按钮走 [stopCurrent]，只停它自己那一类，
 *    不再一次停掉 REPLY + PROACTIVE + REWRITE。
 *
 * ## 真源约束
 * ViewModel 不允许再持有 Job 字段或手写 `isActive` guard；
 * 是否忙碌、能否启动、按钮显示什么，一律从 [activeOperations] / [isBusy] 派生。
 *
 * 六类操作：Reply / Proactive / Rewrite / Counseling / Suggest / ProfileRefresh。
 */
class ForegroundOperationCoordinator(
    private val scope: CoroutineScope
) {

    /** 前台操作类型 */
    enum class OperationType {
        REPLY, PROACTIVE, REWRITE, COUNSELING, SUGGEST, PROFILE_REFRESH
    }

    /**
     * 操作租约。[requestId] 把租约和一个具体的生成请求绑在一起，
     * 迟到的回调只有 requestId 仍与当前租约匹配才被接受。
     */
    data class Lease(
        val operationId: String,
        val type: OperationType,
        val requestId: String
    )

    /** 对外暴露的活跃操作快照（不含 Job，可安全跨线程读） */
    data class ActiveOperation(
        val type: OperationType,
        val operationId: String,
        val requestId: String
    )

    private inner class Record(val lease: Lease, val job: Job)

    /** 保护 [records] 与 [_activeOperations] 的唯一一把锁 */
    private val lock = ReentrantLock()

    /** operationId -> 记录；锁内读写，锁外绝不触碰 */
    private val records = LinkedHashMap<String, Record>()

    private val _activeOperations = MutableStateFlow<List<ActiveOperation>>(emptyList())

    /** 当前活跃操作快照——UI 与所有 guard 的唯一真源 */
    val activeOperations: StateFlow<List<ActiveOperation>> = _activeOperations.asStateFlow()

    /** 调用方被拒绝时拿到 null；此时任务从未启动，不留残留 */
    class Rejected(reason: String) : IllegalStateException(reason)

    /**
     * 互斥矩阵——哪些组合允许同时运行。
     *
     * - REPLY / PROACTIVE / REWRITE / COUNSELING 互为独占（都是前台主操作）
     * - SUGGEST 可与前台主操作并行，但同类型只允许一个
     * - PROFILE_REFRESH 可与任何并行，但同类型只允许一个
     */
    private val mutuallyExclusiveWith = mapOf(
        OperationType.REPLY to setOf(
            OperationType.REPLY, OperationType.PROACTIVE, OperationType.REWRITE, OperationType.COUNSELING
        ),
        OperationType.PROACTIVE to setOf(
            OperationType.REPLY, OperationType.PROACTIVE, OperationType.REWRITE, OperationType.COUNSELING
        ),
        OperationType.REWRITE to setOf(
            OperationType.REPLY, OperationType.PROACTIVE, OperationType.REWRITE, OperationType.COUNSELING
        ),
        OperationType.COUNSELING to setOf(
            OperationType.REPLY, OperationType.PROACTIVE, OperationType.REWRITE, OperationType.COUNSELING
        ),
        OperationType.SUGGEST to setOf(OperationType.SUGGEST),
        OperationType.PROFILE_REFRESH to setOf(OperationType.PROFILE_REFRESH)
    )

    /** 能否在现有活跃操作旁边启动 [type]——锁内调用 */
    private fun canAcceptLocked(type: OperationType): Boolean {
        val blocked = mutuallyExclusiveWith.getValue(type)
        return records.values.none { it.lease.type in blocked }
    }

    /** 锁内：把 [records] 快照发布到 StateFlow */
    private fun publishLocked() {
        _activeOperations.value = records.values.map { ActiveOperation(it.lease.type, it.lease.operationId, it.lease.requestId) }
    }

    /**
     * 创建并启动一个前台任务——注册与启动是一个动作，不会只成功一半。
     *
     * @param requestId 关联的生成请求身份；迟到的回调据此判别是否还属于当前任务
     * @return 租约；互斥拒绝时返回 null（[body] 从未执行）
     */
    fun start(
        type: OperationType,
        requestId: String = "",
        body: suspend (Lease) -> Unit
    ): Lease? {
        val lease = Lease(UUID.randomUUID().toString(), type, requestId)
        // LAZY：先建 Job 但不跑，注册成功后才启动；注册失败时它永远不会执行
        val job = scope.launch(start = CoroutineStart.LAZY) { body(lease) }

        val accepted = lock.withLock {
            if (!canAcceptLocked(type)) {
                null
            } else {
                records[lease.operationId] = Record(lease, job)
                // 认领请求身份：requestId 与租约的绑定和登记发生在同一次加锁里，
                // 调用方不需要再手动 bind，也就不会出现"表里有任务、没有身份"的半套状态。
                if (requestId.isNotBlank()) leaseByRequestId.putIfAbsent(requestId, lease)
                publishLocked()
                lease
            }
        }

        if (accepted == null) {
            // 未启动过的 LAZY Job，cancel 即可，body 一次都不会跑
            job.cancel()
            return null
        }

        // 完成清理必须在 start 之前挂好，避免"极短任务已完成后才注册回调"漏掉
        job.invokeOnCompletion {
            lock.withLock {
                if (records.remove(lease.operationId) != null) {
                    if (lease.requestId.isNotBlank() &&
                        leaseByRequestId[lease.requestId] == lease
                    ) leaseByRequestId.remove(lease.requestId)
                    publishLocked()
                }
            }
        }
        job.start()
        return accepted
    }

    /**
     * 停止这一个租约对应的任务。别的操作不受影响。
     *
     * @return true 说明确实是这个租约在跑并被停止
     */
    fun stop(lease: Lease): Boolean {
        val record = lock.withLock {
            val found = records.remove(lease.operationId)
            if (found != null) {
                if (found.lease.requestId.isNotBlank() &&
                    leaseByRequestId[found.lease.requestId] == found.lease
                ) leaseByRequestId.remove(found.lease.requestId)
                publishLocked()
            }
            found
        } ?: return false
        record.job.cancel()
        return true
    }

    /**
     * 停止某个类型的当前操作——UI"停止"按钮用这个。
     *
     * 只处理这一个类型；绝不同时取消其它 owner（旧 stopGeneration 一次停三类，
     * 会把并行的改写或锦囊一起杀掉）。
     */
    fun stopCurrent(type: OperationType): Boolean {
        val record = lock.withLock {
            val found = records.values.lastOrNull { it.lease.type == type }
            if (found != null) {
                records.remove(found.lease.operationId)
                if (found.lease.requestId.isNotBlank() &&
                    leaseByRequestId[found.lease.requestId] == found.lease
                ) leaseByRequestId.remove(found.lease.requestId)
                publishLocked()
            }
            found
        } ?: return false
        record.job.cancel()
        return true
    }

    /** 该租约是否仍是它那一类里当前被管理的那个（迟到回调判断用） */
    fun isCurrent(lease: Lease): Boolean = lock.withLock {
        records[lease.operationId]?.let { it.lease.requestId == lease.requestId } == true
    }

    /** 当前某类型的活跃操作（最多一个） */
    fun current(type: OperationType): ActiveOperation? = lock.withLock {
        records.values.lastOrNull { it.lease.type == type }?.lease?.let { ActiveOperation(it.type, it.operationId, it.requestId) }
    }

    /** 某类型是否正在运行——ViewModel 不得再自己记 boolean */
    fun isBusy(type: OperationType): Boolean = lock.withLock {
        records.values.any { it.lease.type == type }
    }

    /** 任一前台操作是否在运行 */
    val isAnyBusy: Boolean get() = lock.withLock { records.isNotEmpty() }

    /** 独占型前台操作（锦囊/画像刷新之外的那些）是否在运行——主按钮与输入区用它判断 */
    val isForegroundBusy: Boolean
        get() = lock.withLock {
            records.values.any {
                it.lease.type != OperationType.SUGGEST &&
                    it.lease.type != OperationType.PROFILE_REFRESH
            }
        }

    /**
     * 取消全部在管任务。Service destroy / ViewModel onCleared 必须走这里，
     * 而不是各自 cancel 手里的 Job 字段。
     */
    fun shutdownAll() {
        val toCancel = lock.withLock {
            val all = records.values.toList()
            records.clear()
            leaseByRequestId.clear()
            publishLocked()
            all
        }
        toCancel.forEach { it.job.cancel() }
    }

    /**
     * 生成请求身份与租约的绑定表——保证"一个 requestId 只有一个 owner"。
     *
     * 与 [records] 共用同一把锁，因此不会出现两本账不一致。
     */
    private val leaseByRequestId = HashMap<String, Lease>()

    /** 登记 requestId → 租约；已存在则返回原租约，不重复登记 */
    fun bindRequest(requestId: String, lease: Lease): Lease = lock.withLock {
        leaseByRequestId.putIfAbsent(requestId, lease) ?: lease
    }

    /** 查当前持有某 requestId 的租约 */
    fun leaseFor(requestId: String): Lease? = lock.withLock { leaseByRequestId[requestId] }

    /** 该 requestId 是否仍是它类型里的当前任务（迟到 callback 的准入判据） */
    fun ownsRequest(requestId: String): Boolean = lock.withLock {
        val lease = leaseByRequestId[requestId] ?: return@withLock false
        records[lease.operationId]?.lease == lease
    }

    /** 租约结束/替换时解除绑定 */
    fun unbindRequest(requestId: String, lease: Lease) {
        lock.withLock {
            if (leaseByRequestId[requestId] == lease) leaseByRequestId.remove(requestId)
        }
    }

    /** 只读快照，供断言与调试 */
    internal fun snapshot(): List<ActiveOperation> = lock.withLock {
        records.values.map { ActiveOperation(it.lease.type, it.lease.operationId, it.lease.requestId) }
    }

    companion object {
        /** 用于测试注入的时钟无关工具——保持 API 精简，不暴露可变集合 */
        internal fun frozenTypes(): Set<OperationType> =
            Collections.unmodifiableSet(OperationType.values().toSet())
    }
}
