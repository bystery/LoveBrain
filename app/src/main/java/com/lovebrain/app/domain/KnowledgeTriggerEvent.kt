package com.lovebrain.app.domain

import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.StageSuggestion

/**
 * 后台引擎的一次性结果。
 *
 * 取代此前的 `Callbacks` 接口（6 个方法，由 ViewModel 实现、Coordinator 反向写它的 StateFlow）：
 * 那套写法让"谁拥有状态"变成两处——Coordinator 决定何时写、ViewModel 决定写什么，
 * 还要靠 originating kbName 参数在回调里补身份。事件带身份、由收集方唯一落状态，
 * 取消与重试也因此天然跟着收集方的协程走。
 */
sealed interface KnowledgeTriggerEvent {
    /** 五维向量已重估并写盘成功（delta 供 UI 显示涨跌） */
    data class VectorUpdated(
        val kbName: String,
        val newVector: Map<String, Int>,
        val delta: Map<String, Int>
    ) : KnowledgeTriggerEvent

    /** 五维变化摘要（只在真有维度变化时发出） */
    data class VectorSummary(val kbName: String, val summary: String) : KnowledgeTriggerEvent

    /** 阶段建议 */
    data class StageSuggested(val suggestion: StageSuggestion) : KnowledgeTriggerEvent

    /** 轻提示；失败也走这里，携带 originating kbName 供切库判定 */
    data class Notice(val kbName: String, val message: String) : KnowledgeTriggerEvent

    /** 画像更新建议（含失败态，display 已是成品文案） */
    data class ProfileReady(val suggestion: ProfileSuggestion) : KnowledgeTriggerEvent
}
