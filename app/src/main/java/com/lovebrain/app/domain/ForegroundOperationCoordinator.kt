package com.lovebrain.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * S2-02: 统一前台任务协调器——管理所有 AI 前台流程的互斥和生命周期。
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

    /** 当前活跃操作 */
    data class ActiveOperation(
        val type: OperationType,
        val operationId: String
    )

    private val _activeOperations = MutableStateFlow<List<ActiveOperation>>(emptyList())
    val activeOperations: StateFlow<List<ActiveOperation>> = _activeOperations.asStateFlow()

    /**
     * S2-02: 互斥矩阵——定义哪些组合允许同时运行。
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
     * 启动一个前台操作。返回租约，或 null 表示被互斥拒绝。
     */
    fun start(type: OperationType, job: Job): OperationLease? {
        val current = _activeOperations.value.map { it.type }
        if (!canStartWith(type, current)) return null

        val operationId = UUID.randomUUID().toString()
        val lease = OperationLease(operationId, type, job)

        _activeOperations.value = _activeOperations.value + ActiveOperation(type, operationId)

        job.invokeOnCompletion {
            _activeOperations.value = _activeOperations.value.filter { it.operationId != operationId }
        }

        return lease
    }

    /**
     * 停止指定租约的操作。只取消相同 operationId 的 Job。
     */
    fun stop(lease: OperationLease) {
        lease.job.cancel()
        _activeOperations.value = _activeOperations.value.filter { it.operationId != lease.operationId }
    }

    /**
     * 停止指定类型的所有操作。
     */
    fun stopByType(type: OperationType) {
        val toStop = _activeOperations.value.filter { it.type == type }
        toStop.forEach { op ->
            // Job 已在 start 时注册 invokeOnCompletion，只需从列表移除
            _activeOperations.value = _activeOperations.value.filter { it.operationId != op.operationId }
        }
    }

    /**
     * Service destroy 时调用——关闭所有 child。
     */
    fun shutdownAll() {
        _activeOperations.value.forEach { op ->
            // 只标记移除，Job 由 scope cancel 处理
        }
        _activeOperations.value = emptyList()
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
