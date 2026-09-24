package com.lovebrain.app.feature.rewrite

import com.lovebrain.app.model.SchemeFeedback

/**
 * 被改写掉的版本栈（从 `LoveBrainViewModel` 拆出，再随 [RewriteStore] 搬进 `feature/rewrite`）。
 *
 * ## 这里只管一件事：历史栈
 * 原先这个类同时拿着"每张卡片现在是什么状态"（Loading/Done/Error）和"每条方案被改写掉过
 * 哪些版本"。前者是 UI 状态、只有一个可写处才谈得上归约，后者是撤销用的账。
 * §5.2 第 5 步把 RewriteStore 立起来之后，卡片状态归 [RewriteStore.UiState]，
 * 这里只留版本栈——包括那条容易被写坏的细节：**弹空之后 key 必须整个消失**。
 * 留下 `key -> emptyList()` 会让"这条还有历史可撤"的判断永远为真。
 *
 * 结果正文仍在 `ReplyUiState` 里由 ViewModel 唯一写入——撤销改写时"恢复历史版本"和
 * "清状态"是两步，那部分留在调用方，不在这里加第二套真源。
 */
class RewriteLedger {

    /** 一个被改写掉的版本：正文 + 当时那条正文的赞/踩 */
    data class Version(val reply: String, val feedback: SchemeFeedback)

    private var history: Map<String, List<Version>> = emptyMap()

    /** 发起一次改写时压入：正文与反馈必须一起——否则撤销后正文回到旧版、赞踩却留在新版上 */
    fun push(identityKey: String, version: Version) {
        history = history + (identityKey to (history[identityKey] ?: emptyList()) + version)
    }

    /** 只读不收起——撤销的两步式协议先看这一眼，确认调用方真能应用再落刀 */
    fun peekLast(identityKey: String): Version? = history[identityKey]?.lastOrNull()

    /**
     * 弹出最近一个版本用于撤销；没有历史时返回 null（调用方据此不动结果）。
     *
     * 弹空后把该 key 整个从历史里去掉，不留 `key -> emptyList()` 这种"看着有内容"的条目。
     */
    fun pop(identityKey: String): Version? {
        val entries = history[identityKey] ?: return null
        if (entries.isEmpty()) return null
        val previous = entries.last()
        val rest = entries.dropLast(1)
        history = if (rest.isEmpty()) history - identityKey else history + (identityKey to rest)
        return previous
    }

    /** 该身份是否还留着可撤销的历史条目（与 [historySize] 的区别就在弹空后的那个 key） */
    fun hasHistory(identityKey: String): Boolean = history.containsKey(identityKey)

    /** 改写的次数——单测用它确认历史没被悄悄清空 */
    internal fun historySize(identityKey: String): Int = history[identityKey]?.size ?: 0

    /** 轮次提交、版本回退、切库：历史整体作废 */
    fun clearAll() {
        history = emptyMap()
    }
}
