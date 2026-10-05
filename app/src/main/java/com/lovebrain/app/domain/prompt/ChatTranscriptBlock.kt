package com.lovebrain.app.domain.prompt

import com.lovebrain.app.AppConfig
import com.lovebrain.app.model.ChatMessage

/**
 * 本轮对话记录围栏（自 `domain/PromptBuilder.kt` 的「构建对话记录区块」行为块搬出）。
 *
 * 这一块是 prompt 的**表示层**，职责是把本轮真实消息渲染成 `<chat>` 围栏里的对话记录，
 * 并交回「模型能引用的来源别名」。它不读知识库、不读资产、不读时钟，也不持有任何状态，
 * 因此是纯函数——搬运前后对同一批输入必须给出逐字相同的 header/body/sourceAliasMap
 * （字节证据见 `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 围栏内是**不可信第三方文本**，所以正文走 JSON 序列化转义，不用字符串拼接；
 *   旧格式 `[m0] PARTNER: 正文` 在正文含换行、`[m1] USER:`、`</chat>` 时会造成表示层歧义，
 *   预算裁剪又用 takeLast，可能切掉来源与 speaker。
 * - 角色是数据里的人物身份（PARTNER=对方、USER=用户本人），不机械映射成 API 的
 *   assistant/system 消息角色。
 * - 想法行（IDEA）不是对话记录，不进围栏；别名 m0/m1… 只对进围栏的消息编号。
 * - 超过 [AppConfig.REPLY_MAX_MESSAGES] 条时掐尾保留最近的那批，并在正文顶部留注记；
 *   编号在掐尾后从 m0 重新起。
 * - `sourceAliasMap` 是 TopicRecorder 做来源校验的输入，只登记**最终真的发出去**的消息；
 *   预算以完整消息裁剪，不切半个 JSON 对象。
 */
object ChatTranscriptBlock {

    /** 渲染结果：段标题、围栏正文、别名→实际消息 ID 的映射 */
    data class Rendered(
        val header: String,
        val body: String,
        val sourceAliasMap: Map<String, String>
    )

    /**
     * 构建对话记录区块并附带来源别名映射。
     * 使用 JSON 序列化替代字符串拼接，保护角色边界。
     *
     * 每条消息渲染为 JSON 行——`{"id":"m0","speaker":"PARTNER","text":"..."}`，
     * 正文通过序列化转义——防止换行、特殊字符、注入攻击。
     */
    fun render(messages: List<ChatMessage>): Rendered {
        val chatHeader = "# 本次对话记录\n" +
            "（按时间顺序。speaker 定义：PARTNER=对方，USER=用户本人。每条消息的 id 为来源ID，模型在 scene_facts 的 source_ids 中使用。）\n\n"
        val chatMessages = messages.filter { it.role != ChatMessage.Role.IDEA }
        val effectiveMessages = if (chatMessages.size > AppConfig.REPLY_MAX_MESSAGES) {
            chatMessages.takeLast(AppConfig.REPLY_MAX_MESSAGES)
        } else {
            chatMessages
        }
        // 统一编号 m0, m1, m2...
        val sourceAliasMap = mutableMapOf<String, String>()
        val msgToAlias = mutableMapOf<String, String>()
        var msgIdx = 0
        for (msg in effectiveMessages) {
            val alias = when (msg.role) {
                ChatMessage.Role.HER -> "m$msgIdx".also { msgIdx++ }
                ChatMessage.Role.ME -> "m$msgIdx".also { msgIdx++ }
                else -> continue
            }
            sourceAliasMap[alias] = msg.id
            msgToAlias[msg.id] = alias
        }
        // 使用 JSON 数组格式——每条消息是独立 JSON 对象，正文通过序列化转义
        val chatBody = StringBuilder("<chat>\n")
        if (chatMessages.size > AppConfig.REPLY_MAX_MESSAGES) {
            chatBody.append("（注：对话记录超过 ${AppConfig.REPLY_MAX_MESSAGES} 条，仅保留最近 ${AppConfig.REPLY_MAX_MESSAGES} 条）\n\n")
        }
        // 每条消息渲染为 JSON 行——`{"id":"m0","speaker":"PARTNER","text":"..."}`
        for (msg in effectiveMessages) {
            val alias = msgToAlias[msg.id] ?: continue
            val speakerLabel = when (msg.role) {
                ChatMessage.Role.HER -> "PARTNER"
                ChatMessage.Role.ME -> "USER"
                else -> continue
            }
            // 使用 JSON 序列化转义正文——防止换行、特殊字符、注入攻击
            val escapedText = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<String>(),
                msg.content
            )
            chatBody.append("{\"id\":\"").append(alias).append("\",\"speaker\":\"")
                .append(speakerLabel).append("\",\"text\":").append(escapedText).append("}\n")
        }
        chatBody.append("</chat>\n")
        return Rendered(chatHeader, chatBody.toString(), sourceAliasMap)
    }
}
