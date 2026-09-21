package com.lovebrain.app.model

import kotlinx.serialization.Serializable

/**
 * F18: 轻量日常行动建议——单条建议。
 *
 * 替代旧 SuggestTip 的五字段结构（slot/topic/example/why/expected），
 * 改为更简洁的 action/timing/materialNeeded/example/reason。
 * 兼容旧字段（旧缓存反序列化时不会崩溃）。
 */
@Serializable
data class SuggestTip(
    val action: String = "",          // F18: 做什么（如"早上打个招呼"）
    val timing: String = "",          // F18: 什么时候适合
    val materialNeeded: String = "",  // F18: 需要的素材或前提（可为空）
    val example: String = "",         // 示例配文，点击展开才看到
    val reason: String = "",           // F18: 为什么建议这个，一句话
    // 兼容旧字段（旧缓存反序列化时忽略）
    val slot: String = "",
    val topic: String = "",
    val why: String = "",
    val expected: String = ""
)

/** 今日锦囊 · 邀约窗口（F18: 保留但简化） */
@Serializable
data class SuggestInvite(
    val signal: String = "",
    val suggestion: String = ""
)

/**
 * F18: 日常行动建议——最多 3 条轻量建议。
 * 不再强制 stage/goal/invite/avoid 结构，但保留字段兼容旧缓存。
 */
@Serializable
data class DailySuggestion(
    val stage: String = "",
    val goal: String = "",
    val tips: List<SuggestTip> = emptyList(),
    val invite: SuggestInvite? = null,
    val avoid: List<String> = emptyList()
)
