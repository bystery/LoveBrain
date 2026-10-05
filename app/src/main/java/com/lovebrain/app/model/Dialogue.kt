package com.lovebrain.app.model

import java.util.UUID

/**
 * 真实对话消息——只有 PARTNER 和 USER 两种角色。
 *
 * IDEA（想法）不再是 message role，改为 [ReplyDirective]。
 * 在类型系统层面，对话流水线不可能出现 IDEA。
 */
data class DialogueMessage(
    val id: String = UUID.randomUUID().toString(),
    val speaker: DialogueSpeaker,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 对话参与者——只有两种。
 *
 * PARTNER = 对方（她）
 * USER = 用户本人（我）
 */
enum class DialogueSpeaker {
    PARTNER,
    USER
}

/**
 * 回复指令（原 IDEA）——控制平面，不是消息。
 *
 * 它是用户对本轮生成方向的指导，不是对话内容。
 * 禁止进入 recent.md / scene fact / topic detection / ongoing extraction / fact evidence。
 *
 * @param text 指令文本
 * @param scope 作用域——恒为 CURRENT_GENERATION_ONLY，不在任何持久化中保留
 */
data class ReplyDirective(
    val text: String,
    val scope: DirectiveScope = DirectiveScope.CURRENT_GENERATION_ONLY,
    val aggressive: Boolean = false
)

/** 指令作用域 */
enum class DirectiveScope {
    CURRENT_GENERATION_ONLY
}

/**
 * 从 ChatMessage 列表中分离出对话消息和回复指令。
 *
 * HER → PARTNER, ME → USER, IDEA → ReplyDirective
 * 旧代码中的 ChatMessage.Role.IDEA 在此边界转换为 ReplyDirective，
 * 之后 domain pipeline 只接触 DialogueMessage。
 */
fun splitMessages(messages: List<ChatMessage>): Pair<List<DialogueMessage>, ReplyDirective?> {
    val dialogue = messages
        .filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
        .map { msg ->
            DialogueMessage(
                id = msg.id,
                speaker = when (msg.role) {
                    ChatMessage.Role.HER -> DialogueSpeaker.PARTNER
                    ChatMessage.Role.ME -> DialogueSpeaker.USER
                    else -> DialogueSpeaker.USER // 不应走到
                },
                text = msg.content,
                timestamp = msg.timestamp
            )
        }
    val directiveText = messages
        .filter { it.role == ChatMessage.Role.IDEA }
        .joinToString("\n") { it.content }
        .trim()
    val directive = if (directiveText.isNotBlank()) ReplyDirective(directiveText) else null
    return dialogue to directive
}

/**
 * 从 ChatMessage 列表提取 ReplyDirective（兼容旧接口）。
 * 供 ViewModel 中 collectIdeaHint 等旧入口使用。
 */
fun extractReplyDirective(messages: List<ChatMessage>): String =
    messages.filter { it.role == ChatMessage.Role.IDEA }
        .joinToString("\n") { it.content }
        .trim()
