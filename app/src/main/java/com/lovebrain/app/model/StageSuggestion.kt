package com.lovebrain.app.model

/** 向量重估触发的阶段调整建议 */
data class StageSuggestion(
    val kbName: String,
    val newStage: String,
    val reason: String
)
