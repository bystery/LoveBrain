package com.lovebrain.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 一条回复方案（UI 渲染用；tag/title 硬编码补，AI 只输出 reply 文本） */
@Serializable
data class Scheme(
    val tag: String = "",        // A / B / C / D
    val title: String = "",      // 稳妥 / 直球 / 俏皮 / 温柔（G 批改名：原 推荐/渣男/调皮/暖男）
    val reply: String = ""       // 话术原文
)

/** 新格式 response 块：4 种风格回复（recommended/bad_boy/playful/warm） */
@Serializable
data class ReplySchemes(
    val recommended: String = "",
    @SerialName("bad_boy") val badBoy: String = "",
    val playful: String = "",
    val warm: String = ""
) {
    /** 映射为 UI 用的 4 条 Scheme（过滤空回复） */
    fun toSchemes(): List<Scheme> = listOf(
        Scheme(tag = "A", title = "推荐", reply = recommended),
        Scheme(tag = "B", title = "清醒", reply = badBoy),
        Scheme(tag = "C", title = "俏皮", reply = playful),
        Scheme(tag = "D", title = "温柔", reply = warm)
    ).filter { it.reply.isNotBlank() }
}

/** 进行中事项（长持续时间话题追踪） */
@Serializable
data class OngoingItem(
    @SerialName("item") val name: String = "",  // 新格式字段名为 item；内部仍用 name
    val status: String = "",    // 新出现 / 进行中 / 已完成 / 已取消
    val state: String = ""      // 本轮最新状态节点（时间戳由代码打）
)

/** 新格式 analysis 块（话题/场景/事项记录，不含展示型分析） */
@Serializable
data class ReplyAnalysis(
    val topic_status: String = "same",   // same / drift / new
    val topic_label: String = "",        // 当前话题标签+场景状态
    val scene_facts: List<String> = emptyList(),  // 当前场景关键事实
    val ongoing: List<OngoingItem> = emptyList()  // 进行中事项（只报本轮有变化的）
)

/** DeepSeek 返回的完整结构（单次调用：response + analysis） */
@Serializable
data class LoveBrainResponse(
    val response: ReplySchemes = ReplySchemes(),
    val analysis: ReplyAnalysis = ReplyAnalysis()
) {
    /** UI 兼容访问器：4 条方案 */
    val schemes: List<Scheme> get() = response.toSchemes()
}

/** 面板状态机 */
enum class PanelState {
    KEYBOARD,          // S1 标准键盘
    AI_LOADING,    // S3 生成中
    AI_RESULT      // S4 方案展示
}

/** 生成结果（成功或失败） */
sealed class GenerateResult {
    data class Success(val response: LoveBrainResponse) : GenerateResult()
    data class Error(val message: String) : GenerateResult()
}

/** 流式生成事件 */
sealed class StreamEvent {
    /** 增量文本块 */
    data class Chunk(val text: String) : StreamEvent()
    /** 流完成，附带完整累积文本 */
    data class Complete(val fullText: String) : StreamEvent()
    /** 错误（含已累积的部分文本） */
    data class Error(val message: String, val partialText: String) : StreamEvent()
}

/** 用户对方案的反馈 */
enum class SchemeFeedback {
    NONE, LIKED, DISLIKED
}

/** 主动发起/润色：单条可直接发送的开场 */
@Serializable
data class ProactiveOption(
    val text: String = "",   // 可直接复制发送的消息
    val angle: String = ""   // 切入角度，一句话
)
