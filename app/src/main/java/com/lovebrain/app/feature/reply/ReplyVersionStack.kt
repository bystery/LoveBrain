package com.lovebrain.app.feature.reply

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** 一次成功生成的版本身份——每轮唯一，绑定点踩 / 发送 / 改写 */
data class GenerationVersionId(val value: String) {
    companion object {
        fun next(): GenerationVersionId = GenerationVersionId(UUID.randomUUID().toString())
    }
}

/**
 * 一轮成功生成的完整快照：**结果 + 上下文 + 版本身份一起走**。
 *
 * `context` 是泛型而不是某个具体类型，是为了让这个栈能独立被测：
 * 回退最容易出的错就是"结果翻回去了、上下文还停在被丢弃那一轮"——
 * 那种错配在真机上表现为"我回退了，但保存时写进了下一轮的库/消息"。
 * 三样东西装在同一个对象里，就没人能只翻一半。
 */
data class VersionSnapshot<Ctx, Res>(
    val versionId: GenerationVersionId,
    val result: Res,
    val context: Ctx,
    val kbName: String?,
    val createdAtMs: Long
)

/** 回退的两种结局。`Rejected` 不是一种失败，是"这一条本来就不该回退" */
sealed interface RollbackOutcome<out Ctx, out Res> {
    data class Applied<Ctx, Res>(
        val restored: VersionSnapshot<Ctx, Res>,
        val dropped: VersionSnapshot<Ctx, Res>
    ) : RollbackOutcome<Ctx, Res>

    /** KB 边界不符，或这一块库在当前栈里不足两条 */
    data object Rejected : RollbackOutcome<Nothing, Nothing>
}

/**
 * 回复的版本栈（复核 §5.2 第 6 步：从 ViewModel 里搬出来的第三块**行为**）。
 *
 * 它管四条判据，四条原来都摊在 ViewModel 里：
 * 1. **单向回退**（one-way undo）：`[v1,v2,v3]` 回退 → 删掉 v3、恢复 v2，栈变 `[v1,v2]`；
 *    之后基于 v2 生成 v4 → `[v1,v2,v4]`；再回退恢复 v2。**被丢弃的 v3 永不复活**——
 *    这条最早是用"再回退拿到的版本 id 不等于 v3"来证的（`GenerationRollbackTest`）。
 * 2. **按知识库分栈**：A 的历史高度不能给 B 用，回退也只删 A 那条；
 * 3. **KB 边界**：当前结果的上下文不属于激活库时一律拒绝（用户已经切到 B，就不许操作 A 的版本栈）；
 * 4. **上限**：session 内最多留 [capacity] 条，超了挤掉最旧的（防止长 session 无限增长）。
 *
 * 与搬之前一致的两点，写在这里免得被"顺手优化"：
 * · 这是**内存态**，杀进程即清——不声称跨重启的完整版本历史；
 * · 记快照只在"有本轮上下文"时发生；没有上下文时版本身份照旧前进，但**不写历史**
 *   （旧代码就是这个形状，`record(context = null)` 那条路径钉着它）。
 */
class ReplyVersionStack<Ctx, Res>(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val _snapshots = MutableStateFlow<List<VersionSnapshot<Ctx, Res>>>(emptyList())
    private val _currentVersionId = MutableStateFlow<GenerationVersionId?>(null)

    /** 只读视图：UI 与测试都从这里看，写只有下面几个方法 */
    val snapshots: StateFlow<List<VersionSnapshot<Ctx, Res>>> = _snapshots.asStateFlow()

    /** 当前活跃版本——最新一次成功生成的身份，用于绑定点踩 / 发送 / 改写 */
    val currentVersionId: StateFlow<GenerationVersionId?> = _currentVersionId.asStateFlow()

    val currentVersionIdNow: GenerationVersionId? get() = _currentVersionId.value
    val size: Int get() = _snapshots.value.size

    /** 这一块库在栈里占了几条（跨库隔离的判据就体现在这里，别用全表高度） */
    fun sizeFor(kbName: String?): Int = _snapshots.value.count { it.kbName == kbName }

    /**
     * 记一次成功：版本身份前进 + 写入这一条快照。
     *
     * 与 [advanceVersionOnly] 分成两个方法而不是"传个 null 进来"：旧代码里"有上下文才写历史"
     * 是两条路径，合成一个可空参数之后，Kotlin 对 `Ctx?` 的捕获会把列表类型投成
     * `VersionSnapshot<out Ctx, …>`，同一个栈反而装不进去——把分支交给调用点显式选，
     * 既保留旧形状也不跟类型系统打架。
     */
    fun record(
        versionId: GenerationVersionId,
        result: Res,
        context: Ctx,
        kbName: String?
    ) {
        _currentVersionId.value = versionId
        val snapshot = VersionSnapshot(
            versionId = versionId,
            result = result,
            context = context,
            kbName = kbName,
            createdAtMs = clock()
        )
        _snapshots.value = (_snapshots.value + snapshot).takeLast(capacity)
    }

    /** 没有本轮上下文的一次成功：只前进版本身份，**不写历史**（点踩/发送绑的是版本 id） */
    fun advanceVersionOnly(versionId: GenerationVersionId) {
        _currentVersionId.value = versionId
    }

    /** 能不能回退：KB 边界一致 + 这一块库在当前栈里至少两条 */
    fun canRollback(activeKbName: String?, contextKbName: String?): Boolean =
        selectPair(activeKbName, contextKbName) != null

    /**
     * 执行一次回退：删掉当前那条、恢复到前一条。
     *
     * 返回 [RollbackOutcome.Rejected] 时栈**一字未动**——拒绝也要是可预期的，
     * 不能出现"没回退但把栈改坏了"。
     */
    fun rollback(activeKbName: String?, contextKbName: String?): RollbackOutcome<Ctx, Res> {
        val (current, previous) = selectPair(activeKbName, contextKbName)
            ?: return RollbackOutcome.Rejected
        _snapshots.value = _snapshots.value.filterNot { it.versionId == current.versionId }
        _currentVersionId.value = previous.versionId
        return RollbackOutcome.Applied(restored = previous, dropped = current)
    }

    /** 新一轮开始时清空（搬之前由 VM 自己决定何时清，这里只交出这个动作） */
    fun clear() {
        _snapshots.value = emptyList()
        _currentVersionId.value = null
    }

    private fun selectPair(
        activeKbName: String?,
        contextKbName: String?
    ): Pair<VersionSnapshot<Ctx, Res>, VersionSnapshot<Ctx, Res>>? {
        // KB 边界：上下文的库与激活库不一致就什么都不做（含上下文为空的情况）
        if (contextKbName == null || contextKbName != activeKbName) return null
        val kbHistory = _snapshots.value.filter { it.kbName == activeKbName }
        if (kbHistory.size < 2) return null
        return kbHistory.last() to kbHistory[kbHistory.size - 2]
    }

    companion object {
        /** session 内最多留几条快照——与搬之前 VM 里那个 20 同一含义 */
        const val DEFAULT_CAPACITY = 20
    }
}
