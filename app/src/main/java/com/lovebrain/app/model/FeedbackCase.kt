package com.lovebrain.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * F02: 反馈案例——点踩时本地保存的反馈记录。
 *
 * 理解错误与表达偏好必须分开。允许一条反馈同时属于两类。
 * 不记录 API Key、认证头或敏感连接参数。
 * 快照是诊断资料，不能进入关系事实。
 */
@Serializable
data class FeedbackCase(
    val caseId: String,                    // 稳定唯一 ID
    val schemeIdentityKey: String,         // 候选版本身份 key (STYLE:B / DIRECTION:F)
    val candidateReply: String,            // 候选版本原文
    val categories: List<FeedbackCategory>, // 分类（可多选）
    val reasons: List<String>,             // 二级原因
    val userNote: String = "",             // 用户补充说明
    val betterVersion: String = "",        // 用户提供的更好版本
    // 最小相关输入快照
    val kbName: String = "",
    val role: String = "",                 // 角色上下文
    val ideaHint: String = "",             // 本轮想法
    val intentText: String = "",           // 启用中的意图
    val contextMode: String = "",          // 上下文模式
    val modelId: String = "",              // 模型/供应商标识
    val promptVersion: String = "",        // 提示词版本或哈希
    val timestamp: String = "",            // 时间
    // 用量（有则存）
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val costYuan: Double = 0.0,
    // 案例状态
    val status: CaseStatus = CaseStatus.PENDING
)

/**
 * F02: 反馈一级类别——理解错误与表达不喜欢不互斥。
 */
enum class FeedbackCategory {
    UNDERSTANDING_ERROR,   // 理解错误
    EXPRESSION_DISLIKE,    // 表达不喜欢
    OTHER                  // 其他/未分类
}

/**
 * F02: 案例状态。
 */
enum class CaseStatus {
    PENDING,       // 待分析
    CONCLUDED,     // 已有结论
    TO_VERIFY,     // 待验证
    VERIFIED       // 已验证
}

/**
 * F02: 理解错误二级原因。
 */
object UnderstandingReasons {
    val ALL = listOf(
        "角色错",
        "事实错",
        "时间错",
        "对象错",
        "误解本轮想法",
        "旧事重复",
        "擅自承诺"
    )
}

/**
 * F02: 表达不喜欢二级原因。
 */
object ExpressionDislikeReasons {
    val ALL = listOf(
        "太油",
        "太长",
        "太冷",
        "太文艺",
        "像客服",
        "反问过多",
        "不像我",
        "不喜欢的词"
    )
}
