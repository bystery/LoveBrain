package com.lovebrain.app.model

import kotlinx.serialization.Serializable

/** 今日锦囊 · 单条推荐做法 */
@Serializable
data class SuggestTip(
    val slot: String = "",        // 时机（早上/她发朋友圈后/晚上10点后）
    val topic: String = "",       // 话题方向
    val example: String = "",     // 具体话术，口语
    val why: String = "",         // 为什么好
    val expected: String = ""     // 她可能的反应
)

/** 今日锦囊 · 邀约窗口 */
@Serializable
data class SuggestInvite(
    val signal: String = "",      // 判断依据
    val suggestion: String = ""   // 邀约话术（可为空=不适合邀约）
)

/** 今日锦囊（AI 生成，纯参考性，不写知识库） */
@Serializable
data class DailySuggestion(
    val stage: String = "",                 // 当前阶段名
    val goal: String = "",                  // 本阶段一句目标
    val tips: List<SuggestTip> = emptyList(),
    val invite: SuggestInvite? = null,
    val avoid: List<String> = emptyList()
)
