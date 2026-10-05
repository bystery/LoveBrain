package com.lovebrain.app.feature.stage

import com.lovebrain.app.model.StageSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 向量重估触发的阶段调整建议的持有者（复核 第5节第2条 第 6 步"VM 不再持有 feature 的内部状态"）。
 *
 * 接管 `_stageSuggestion`：后台引擎在五维重估后可能建议调整关系阶段，面板据此弹出
 * "确认后生效"的卡片。状态只有这一颗：有建议 / 无建议。
 *
 * "确认时要写哪块库、写哪个阶段标签"这条跨块协调留在 VM：store 不知道 `KnowledgeRuntimePort`
 * （`feature` 不许 import `data`，第5节第1条 包边界）。所以 [confirm] 这一步仍在 VM，
 * store 只管状态翻转；VM 确认成功后投 [Intent.Clear] 收尾。
 */
class StageSuggestionStore {

    private val _stageSuggestion = MutableStateFlow<StageSuggestion?>(null)
    val stageSuggestion: StateFlow<StageSuggestion?> = _stageSuggestion.asStateFlow()

    /** VM confirm 流程读取当前建议的同步快照 */
    val current: StageSuggestion? get() = _stageSuggestion.value

    sealed interface Intent {
        /** 后台引擎给出（或清掉）一条阶段建议 */
        data class Set(val suggestion: StageSuggestion?) : Intent

        /** 用户 dismiss / 确认成功后清空 */
        data object Clear : Intent
    }

    /** 唯一写入口 */
    fun accept(intent: Intent) {
        when (intent) {
            is Intent.Set -> _stageSuggestion.value = intent.suggestion
            Intent.Clear -> _stageSuggestion.value = null
        }
    }
}
