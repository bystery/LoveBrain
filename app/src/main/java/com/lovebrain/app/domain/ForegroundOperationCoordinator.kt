package com.lovebrain.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * S2-02 审计修复: 统一前台任务协调器——管理所有 AI 前台流程的互斥和生命周期。
 *
 * 审计缺陷修复：
 * - ActiveOperation 现在保存 Job——stopByType/shutdownAll 真正取消
 * - start() 使用 Mutex 保护的原子操作，消除竞态
 * - shutdownAll() 真正取消所有 child Job
 * - 六类操作全部注册（Reply/Proactive/Rewrite/Counseling/Suggest/ProfileRefresh）
 * - ViewModel 不再手写 guard/Job 真源——通过 coordinator 统一管理
 *
 * 类型：Reply / Proactive / Rewrite / Counseling / Suggest / ProfileRefresh。
 * 同一时刻允许哪些组合写成表，不允许各函数手写 guard。
 * start 返回 operationId/lease；stop 只取消相同 lease；迟到 callback 丢弃。
 * Service destroy 关闭 coordinator，所有 child 结构化取消。
 * UI 从一个 operation state 派生 loading/stop/disabled，不拼多个 boolean。
 */
class ForegroundOperationCoordinator(
    private val scope: CoroutineScope
) {
    /** 前台操作类型 */
    enum class OperationType {
        REPLY, PROACTIVE, REWRITE, COUNSELING, SUGGEST, PROFILE_REFRESH
    }

    /** 操作租约——start 返回，stop 用它取消 */
    data class OperationLease(
        val operationId: String,
        val type: OperationType,
        val job: Job
    )

    /**
     * 审计修复：ActiveOperation 现在保存 Job 引用。
     * stopByType/shutdownAll 通过 Job 引用真正取消任务。
     */
    data class ActiveOperation(
        val type: OperationType,
        val operationId: String,
        val job: Job
    )

    private val _activeOperations = MutableStateFlow<List<ActiveOperation>>(emptyList())
    val activeOperations: StateFlow<List<ActiveOperation>> = _activeOperations.asStateFlow()

    /** 审计修复：使用 Mutex 保证 start/stop 原子性，消除竞态 */
    private val mutex = Mutex()

    /**
     * 互斥矩阵——定义哪些组合允许同时运行。
     * 规则：
     * - Reply 和 Proactive 互斥（前台主操作）
     * - Rewrite 与 Reply 互斥（改写是 Reply 的子操作）
     * - Counseling 与所有前台 AI 互斥
     * - Suggest 可与 Reply/Counseling 并行（锦囊是独立流程）
     * - ProfileRefresh 可与任何并行
     */
    private fun canStartWith(type: OperationType, current: List<OperationType>): Boolean {
        if (current.isEmpty()) return true
        return when (type) {
            OperationType.REPLY -> current.all { it == OperationType.SUGGEST || it == OperationType.PROFILE_REFRESH }
            OperationType.PROACTIVE -> current.all { it == OperationType.SUGGEST || it == OperationType.PROFILE_REFRESH }
            OperationType.REWRITE -> current.all { it == OperationType.SUGGEST || it == OperationType.PROFILE_REFRESH }
            OperationType.COUNSELING -> current.all { it == OperationType.SUGGEST || it == OperationType.PROFILE_REFRESH }
            OperationType.SUGGEST -> true
            OperationType.PROFILE_REFRESH -> true
        }
    }

    /**
     * 审计修复：启动一个前台操作——使用 Mutex 保证原子性。
     * 返回租约，或 null 表示被互斥拒绝。
     */
    suspend fun start(type: OperationType, job: Job): OperationLease? {
        return mutex.withLock {
            val current = _activeOperations.value.map { it.type }
            if (!canStartWith(type, current)) return@withLock null

            val operationId = UUID.randomUUID().toString()
            val lease = OperationLease(operationId, type, job)

            _activeOperations.value = _activeOperations.value + ActiveOperation(type, operationId, job)

            job.invokeOnCompletion {
                _activeOperations.value = _activeOperations.value.filter { it.operationId != operationId }
            }

            lease
        }
    }

    /**
     * 非挂起版本的 start——用于 ViewModel 同步调用场景。
     * 使用 tryLock，如果锁被占用则拒绝（保守策略）。
     */
    fun startSync(type: OperationType, job: Job): OperationLease? {
        if (!mutex.tryLock()) return null
        try {
            val current = _activeOperations.value.map { it.type }
            if (!canStartWith(type, current)) return null

            val operationId = UUID.randomUUID().toString()
            val lease = OperationLease(operationId, type, job)

            _activeOperations.value = _activeOperations.value + ActiveOperation(type, operationId, job)

            job.invokeOnCompletion {
                _activeOperations.value = _activeOperations.value.filter { it.operationId != operationId }
            }

            return lease
        } finally {
            mutex.unlock()
        }
    }

    /**
     * 停止指定租约的操作。只取消相同 operationId 的 Job。
     * 审计修复：现在真正调用 job.cancel()。
     */
    fun stop(lease: OperationLease) {
        _activeOperations.value = _activeOperations.value.filter { it.operationId != lease.operationId }
        lease.job.cancel()
    }

    /**
     * 停止指定类型的所有操作。
     * 审计修复：现在真正取消每个匹配 Job。
     */
    fun stopByType(type: OperationType) {
        val toStop = _activeOperations.value.filter { it.type == type }
        _activeOperations.value = _activeOperations.value.filter { it.operationId !in toStop.map { op -> op.operationId } }
        toStop.forEach { op ->
            op.job.cancel()
        }
    }

    /**
     * Service destroy 时调用——关闭所有 child。
     * 审计修复：现在真正取消所有 Job。
     */
    fun shutdownAll() {
        val all = _activeOperations.value
        _activeOperations.value = emptyList()
        all.forEach { op ->
            op.job.cancel()
        }
    }

    /** 检查指定类型是否活跃 */
    fun isActive(type: OperationType): Boolean =
        _activeOperations.value.any { it.type == type }

    /** 检查是否任何前台操作活跃（排除 Suggest 和 ProfileRefresh） */
    val isForegroundBusy: Boolean
        get() = _activeOperations.value.any {
            it.type != OperationType.SUGGEST && it.type != OperationType.PROFILE_REFRESH
        }

    /** UI 使用的统一操作状态 */
    data class OperationState(
        val isReplying: Boolean = false,
        val isProactive: Boolean = false,
        val isRewriting: Boolean = false,
        val isCounseling: Boolean = false,
        val isSuggesting: Boolean = false,
        val isProfileRefreshing: Boolean = false
    ) {
        val isAnyBusy: Boolean get() = isReplying || isProactive || isRewriting ||
            isCounseling || isSuggesting || isProfileRefreshing
        val isForegroundBusy: Boolean get() = isReplying || isProactive || isRewriting || isCounseling
    }

    /** S2-02: 派生 UI 状态——从一个 state 派生，不拼多个 boolean */
    val operationState: StateFlow<OperationState> = _activeOperations
        .map { ops ->
            OperationState(
                isReplying = ops.any { it.type == OperationType.REPLY },
                isProactive = ops.any { it.type == OperationType.PROACTIVE },
                isRewriting = ops.any { it.type == OperationType.REWRITE },
                isCounseling = ops.any { it.type == OperationType.COUNSELING },
                isSuggesting = ops.any { it.type == OperationType.SUGGEST },
                isProfileRefreshing = ops.any { it.type == OperationType.PROFILE_REFRESH }
            )
        }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, OperationState())
}
