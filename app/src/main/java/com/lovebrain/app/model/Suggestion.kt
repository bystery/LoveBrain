package com.lovebrain.app.model

import kotlinx.serialization.Serializable

/**
 * 单条行动建议。
 *
 * 字段：id / timingCategory / action / timing / materialNeeded / example / reason。
 * 兼容旧字段（slot/topic/why/expected），旧缓存反序列化时不会崩溃。
 */
@Serializable
data class SuggestTip(
    val id: String = "",               // 唯一标识，如 "tip-1"
    val timingCategory: String = "",   // "现在可用" / "今天可准备" / "有机会再做"
    val action: String = "",           // 做什么（如"早上打个招呼"）
    val timing: String = "",           // 什么时候适合
    val materialNeeded: String = "",   // 需要的素材或前提（可为空）
    val example: String = "",          // 示例配文，点击展开才看到
    val reason: String = "",           // 为什么建议这个，一句话
    // 兼容旧字段（旧缓存反序列化时忽略）
    val slot: String = "",
    val topic: String = "",
    val why: String = "",
    val expected: String = ""
)

/** 今日锦囊 · 邀约窗口（保留但简化） */
@Serializable
data class SuggestInvite(
    val signal: String = "",
    val suggestion: String = ""
)

/**
 * 日常行动建议结果。
 * 不再强制 stage/goal/invite/avoid 结构，但保留字段兼容旧缓存。
 */
@Serializable
data class DailySuggestion(
    val stage: String = "",
    val goal: String = "",
    val tips: List<SuggestTip> = emptyList(),
    val invite: SuggestInvite? = null,
    val avoid: List<String> = emptyList(),
    /** 本次生成的 token 使用与估算费用（Provider 未返回时为 null） */
    val usage: DailyBriefUsage? = null,
    /** 结果是否不完整（输出少于 6 条、截断等） */
    val partial: Boolean = false
)

/**
 * 锦囊生成的成本可见性数据。
 * Provider 未返回 usage 时各字段为 null（禁止以 0 冒充已核实）。
 */
@Serializable
data class DailyBriefUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val costYuan: Double? = null,
    /** 生成耗时（毫秒） */
    val elapsedMs: Long? = null,
    /** 生成时间戳（ISO 格式字符串） */
    val generatedAt: String = ""
)
