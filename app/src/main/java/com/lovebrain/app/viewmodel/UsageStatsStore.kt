package com.lovebrain.app.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 面板那九个"用了多少"的状态持有者（复核 §5.2 第 6 步"VM 不再持有 feature 的内部状态"）。
 *
 * 接管 `_usageStats`：原先它是 [LoveBrainViewModel] 里的一颗 `MutableStateFlow<UsageStats>`，
 * 唯一写漏斗是 `applyUsage`（reduce 在 [UsageStats.reduce]，落盘在 VM 写 SecurePrefs）。
 * 现在状态搬到这里，VM 只做两件事：载入初始快照（init 那条第二条写路径）、把 [UsageStats.Event]
 * 投给 [accept]；落盘仍由 VM 经 [onPersist] 回调决定（`feature`/本 store 不碰 `data`）。
 *
 * 为什么这个 store 住在 `viewmodel` 包而不是 `feature/`：`UsageStats` 类型本身就在
 * `com.lovebrain.app.viewmodel`（它的 reduce 是 JVM 直测的纯函数，被同包的 `UsageStatsTest` 引用），
 * 而 `feature` 包不许 import `viewmodel`（[com.lovebrain.app.architecture.PackageDependencyTest] 管着）。
 * 把 `UsageStats` 搬到 `model` 会改包名、连带改一批同包测试的引用，不在本次范围；
 * 所以这个 store 留在 `viewmodel` 包与 `UsageStats` 同住——VM 仍然不再**持有**状态，
 * 只是状态的所有者与它的类型住在同一个包。
 *
 * 落盘口径与搬之前一字不差：哪些字段变了才写哪条 SecurePrefs，由 [onPersist] 在 VM 里复刻
 * （原 `applyUsage` 那段 if 链）。`event is Costed` 时今日数每次都存——与改前一致。
 */
class UsageStatsStore(
    /**
     * reduce 成功后的落盘出口（同步）。调用方据此决定写哪几条 SecurePrefs。
     * 收到的是 (before, after, event) 三元组，与原 `applyUsage` 的判据形状一致。
     */
    private val onPersist: (before: UsageStats, after: UsageStats, event: UsageStats.Event) -> Unit = { _, _, _ -> }
) {

    private val _stats = MutableStateFlow(UsageStats())
    val stats: StateFlow<UsageStats> = _stats.asStateFlow()

    /** VM 内部读取的同步快照（点踩案例要读 lastCostYuan） */
    val current: UsageStats get() = _stats.value

    /** init 载入初始快照——与 [accept] 同一个状态、同一条漏斗，只是不触发落盘 */
    fun load(initial: UsageStats) {
        _stats.value = initial
    }

    /** 唯一写入口：reduce + 落盘回调 */
    fun accept(event: UsageStats.Event) {
        val before = _stats.value
        val after = before.reduce(event)
        _stats.value = after
        onPersist(before, after, event)
    }
}
