package com.lovebrain.app.domain

import com.lovebrain.app.model.ChatMessage

/**
 * 单条改写的 prompt 拼装（从 `LoveBrainViewModel` 拆出）。
 *
 * 复核报告 §5/§6 对改写链路的要求是"请求只包含短固定规则、目标原回复、选中的操作与
 * 最少必要的本轮真实上下文；长知识库、完整分析、其他候选不随之发送"。
 * 这句话原先只能在真机抓包里被验证——因为拼装代码嵌在 2 900 行的 ViewModel 里，
 * JVM 侧根本调不到。搬成纯函数后，"发了什么"和"没发什么"可以直接断言。
 *
 * 这里不读文件也不碰仓库：表达偏好（`understand/style.md`）由调用方读好后传进来。
 */
object RewritePrompt {

    /** 改写用的固定短规则——每次请求都一样，不含任何用户数据 */
    val system: String = buildString {
        appendLine("你是恋爱沟通助手。用户想改写一条已有的回复。")
        appendLine("规则：")
        appendLine("1. 只输出改写后的回复正文，不输出任何分析、标签或格式说明")
        appendLine("2. 不能改人名、时间、约定和说话主体")
        appendLine("3. 不能添加未经证实的事实")
        appendLine("4. 不能把候选回复当作已发送消息")
        appendLine("5. 输出一个非空回复正文即可")
        appendLine("6. 如果提供了「我的表达偏好」，改写时尽量遵守该偏好")
    }

    /** 最近几条真实对话（IDEA 草稿不算对话） */
    fun recentChat(messages: List<ChatMessage>, limit: Int = RECENT_CHAT_LIMIT): String =
        messages.filter { it.role != ChatMessage.Role.IDEA }
            .takeLast(limit)
            .joinToString("\n") { msg -> "${roleLabel(msg.role)}：${msg.content}" }

    private fun roleLabel(role: ChatMessage.Role): String = when (role) {
        ChatMessage.Role.HER -> "她"
        ChatMessage.Role.ME -> "我"
        else -> role.label
    }

    /**
     * 组装改写请求。
     *
     * 任何一项为空白就整段省略——不是塞一行"（无）"占位：占位文字会被模型当成内容，
     * 也会让"最少必要上下文"这句话变成不可验证的形容词。
     */
    fun user(
        originalReply: String,
        instruction: String,
        recentChat: String,
        intentText: String?,
        ideaHint: String?,
        style: String?
    ): String = buildString {
        if (!style.isNullOrBlank()) {
            appendLine("我的表达偏好：")
            appendLine(style.trim())
            appendLine()
        }
        if (recentChat.isNotBlank()) {
            appendLine("最近对话：")
            appendLine(recentChat)
            appendLine()
        }
        if (!intentText.isNullOrBlank()) {
            appendLine("当前意图：$intentText")
            appendLine()
        }
        if (!ideaHint.isNullOrBlank()) {
            appendLine("想法备注：$ideaHint")
            appendLine()
        }
        appendLine("原回复：$originalReply")
        appendLine()
        append("改写要求：$instruction")
    }

    /** 送进模型的对话条数上限——超过就只保留最近这些条 */
    const val RECENT_CHAT_LIMIT = 6
}
