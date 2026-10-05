package com.lovebrain.app.model

/**
 * 画像事务写入的 typed result——替代模糊的 Boolean。
 *
 * 四种终态覆盖所有事务路径，调用方可据此给出精确的 UI 反馈：
 *
 * - [Success]：全部写入成功，事务提交。
 * - [PreconditionFailed]：KB 不存在、revision 冲突，或这个库处于只读保护——事务未执行任何写入。
 * - [RolledBack]：写入过程中 IO 失败，但 rollback 已完整恢复所有 backup 文件，
 *   数据回到事务开始前的一致状态。用户可安全重试。
 * - [RollbackFailed]：写入失败且 rollback 自身也失败——数据可能处于不一致状态。
 *   携带 [failedPaths] 供日志诊断；UI 必须告知用户"数据可能已损坏"，
 *   不可轻描淡写为"已恢复"。
 *
 * @param cause 原始异常（RolledBack / RollbackFailed 携带）
 * @param failedPaths rollback 失败的文件路径列表（仅 RollbackFailed 携带）
 */
sealed class ProfileTransactionResult {

    /** 事务成功提交 */
    data object Success : ProfileTransactionResult()

    /** 三种前置条件之一不满足——未执行任何写入（原因见 [PreconditionReason]） */
    data class PreconditionFailed(
        val reason: PreconditionReason
    ) : ProfileTransactionResult()

    /** 写入失败但 rollback 完整成功——数据已恢复到事务前状态 */
    data class RolledBack(
        val cause: Throwable
    ) : ProfileTransactionResult()

    /** 写入失败且 rollback 自身也失败——数据可能不一致 */
    data class RollbackFailed(
        val cause: Throwable,
        val failedPaths: List<String>
    ) : ProfileTransactionResult()
}

/** PreconditionFailed 的具体原因 */
enum class PreconditionReason {
    /** 目标知识库已不存在 */
    KB_NOT_FOUND,
    /** corrections revision 已变化（乐观锁冲突） */
    REVISION_CONFLICT,
    /**
     * 库的 schema 比本 App 还新 → 整个库处于只读保护。
     *
     * 与上面两条的区别必须留住：KB_NOT_FOUND / REVISION_CONFLICT 意味着**这条建议作废**；
     * LIBRARY_READ_ONLY 只意味着"这个 App 写不动这个库"，升级之后同一份建议仍然有效，
     * 所以 UI 不许把它当"已失效"清掉（`ProfileTransactionResultTest` 钉着这条）。
     */
    LIBRARY_READ_ONLY
}
