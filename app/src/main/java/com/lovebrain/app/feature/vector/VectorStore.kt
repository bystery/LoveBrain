package com.lovebrain.app.feature.vector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 五维向量状态 + 重估变化量的持有者（复核 §5.2 第 6 步"VM 不再持有 feature 的内部状态"）。
 *
 * 接管两颗原本住在 [com.lovebrain.app.viewmodel.LoveBrainViewModel] 里的可写流：
 * - `_currentVector`：当前知识库的五维状态向量（供面板状态卡片展示）。
 * - `_vectorDelta`：最近一次重估的五维变化量（新值 - 旧值，供卡片显示涨跌箭头）。
 *
 * 这两颗是"换库要一起复位"的一族：切到另一块库时 delta 清空、current 读新库的向量；
 * 当前库没有（删到空）时 current 也清空。判据原本散在 VM 的 [refreshKnowledgeBases] 与
 * [applyTriggerEvent] 两处，现在收进同一个 [accept] 漏斗。
 *
 * 串库保护（向量类事件只接受当前库的结果）仍在 VM：store 不知道"当前激活的是哪块库"，
 * 那是 VM 的中心读数（见 `_activeKb`，按 §5.2 留在 VM）。
 */
class VectorStore {

    private val _currentVector = MutableStateFlow<Map<String, Int>>(emptyMap())
    val currentVector: StateFlow<Map<String, Int>> = _currentVector.asStateFlow()

    private val _vectorDelta = MutableStateFlow<Map<String, Int>>(emptyMap())
    val vectorDelta: StateFlow<Map<String, Int>> = _vectorDelta.asStateFlow()

    sealed interface Intent {
        /** 一次向量重估：同时更新 current 与 delta（后台引擎结果） */
        data class UpdateVector(
            val vector: Map<String, Int>,
            val delta: Map<String, Int>
        ) : Intent

        /** 切到新库：current 读新库的向量（null 库传空映射） */
        data class SetCurrent(val vector: Map<String, Int>) : Intent

        /** 切库时清掉上一块库的瞬时 delta（current 由 [SetCurrent] 另外刷新） */
        data object ClearDelta : Intent
    }

    /** 唯一写入口 */
    fun accept(intent: Intent) {
        when (intent) {
            is Intent.UpdateVector -> {
                _currentVector.value = intent.vector
                _vectorDelta.value = intent.delta
            }
            is Intent.SetCurrent -> _currentVector.value = intent.vector
            Intent.ClearDelta -> _vectorDelta.value = emptyMap()
        }
    }
}
