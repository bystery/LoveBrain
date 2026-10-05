package com.lovebrain.app.viewmodel

import com.lovebrain.app.domain.KnowledgeTriggerEvent
import com.lovebrain.app.model.ProfileSuggestion

/**
 * 测试侧的"后台引擎结果投喂口"。
 *
 * 生产上这些结果只有一个入口：`LoveBrainViewModel.applyTriggerEvent(KnowledgeTriggerEvent)`，
 * 由收集 `KnowledgeTriggerCoordinator` 冷流的协程调用。
 * 用例不应该去调生产回调接口（那个接口已经删了），但也不该在 23 处断言前面各拼一遍事件对象——
 * 所以这里放一层薄夹具：名字说清是"喂事件"，映射关系只写一次。
 */

internal fun LoveBrainViewModel.feedProfileSuggestion(suggestion: ProfileSuggestion) =
    applyTriggerEvent(KnowledgeTriggerEvent.ProfileReady(suggestion))

internal fun LoveBrainViewModel.feedVectorUpdated(
    kbName: String,
    newVector: Map<String, Int>,
    delta: Map<String, Int>
) = applyTriggerEvent(KnowledgeTriggerEvent.VectorUpdated(kbName, newVector, delta))

/** 只更新当前向量的旧路径已合并进 VectorUpdated（delta 传空图即为"只同步值"） */
internal fun LoveBrainViewModel.feedCurrentVector(kbName: String, vector: Map<String, Int>) =
    applyTriggerEvent(KnowledgeTriggerEvent.VectorUpdated(kbName, vector, emptyMap()))

internal fun LoveBrainViewModel.feedVectorSummary(kbName: String, summary: String) =
    applyTriggerEvent(KnowledgeTriggerEvent.VectorSummary(kbName, summary))

internal fun LoveBrainViewModel.feedKbNotice(kbName: String = "", message: String) =
    applyTriggerEvent(KnowledgeTriggerEvent.Notice(kbName, message))
