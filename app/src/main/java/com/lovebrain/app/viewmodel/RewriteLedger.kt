package com.lovebrain.app.viewmodel

import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.SchemeFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 单条改写的卡片状态与被改写版本历史（从 `LoveBrainViewModel` 拆出）。
 *
 * 只登记两件事：某条方案卡现在处于改写的哪个状态（加载中/完成/失败），
 * 以及每条方案被改写掉的历史版本（撤销时要连正文带反馈一起恢复）。
 * 结果正文仍在 `ReplyUiState` 里由 ViewModel 唯一写入——撤销改写时
 * "恢复历史版本"和"清状态"是两步，那部分顺序留在调用方，不在这里加第二套真源。
 */
class RewriteLedger {

    /** 一个被改写掉的版本：正文 + 当时那条正文的赞/踩 */
    data class Version(val reply: String, val feedback: SchemeFeedback)

    private val _states = MutableStateFlow<Map<String, RewriteState>>(emptyMap())

    /** identityKey（`SchemeIdentity.key`）→ 卡片改写状态；STYLE 与 DIRECTION 各自独立 */
    val states: StateFlow<Map<String, RewriteState>> = _states.asStateFlow()

    private val _history = MutableStateFlow<Map<String, List<Version>>>(emptyMap())

    fun stateOf(identityKey: String): RewriteState? = _states.value[identityKey]

    fun setState(identityKey: String, state: RewriteState) {
        _states.value = _states.value + (identityKey to state)
    }

    fun removeState(identityKey: String) {
        _states.value = _states.value - identityKey
    }

    /** 只读不收起——用于"完成态/失败态才允许收起"这类判断 */
    fun peekLast(identityKey: String): Version? = _history.value[identityKey]?.lastOrNull()

    /**
     * 发起一次改写：卡片进"改写中"，并把当前正文连同其反馈压进历史。
     *
     * 反馈必须一起压——否则撤销后正文回到旧版、赞踩却留在新版上。
     */
    fun begin(identityKey: String, option: String, previousReply: String, previousFeedback: SchemeFeedback) {
        setState(identityKey, RewriteState.Loading(option))
        val history = _history.value[identityKey] ?: emptyList()
        _history.value = _history.value + (identityKey to history + Version(previousReply, previousFeedback))
    }

    /**
     * 弹出最近一个版本用于撤销；没有历史时返回 null（调用方据此不动结果）。
     *
     * 弹空后把该 key 整个从历史里去掉，不留 `key -> emptyList()` 这种"看着有内容"的条目。
     */
    fun pop(identityKey: String): Version? {
        val history = _history.value[identityKey] ?: return null
        if (history.isEmpty()) return null
        val previous = history.last()
        val rest = history.dropLast(1)
        _history.value = if (rest.isEmpty()) {
            _history.value - identityKey
        } else {
            _history.value + (identityKey to rest)
        }
        return previous
    }

    /** 轮次提交与版本回退：状态与历史一起清空 */
    fun clearAll() {
        _states.value = emptyMap()
        _history.value = emptyMap()
    }

    /** 撤销改写的次数——单测用它确认历史没被悄悄清空 */
    internal fun historySize(identityKey: String): Int = _history.value[identityKey]?.size ?: 0

    /**
     * 该身份是否还留着可撤销的历史条目。
     *
     * 与 historySize 的区别就在这：弹空之后 key 本身也要消失，
     * 留下 `key -> emptyList()` 会让"这条有历史可撤"的判断一直为真。
     */
    internal fun hasHistory(identityKey: String): Boolean = _history.value.containsKey(identityKey)
}
