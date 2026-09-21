package com.lovebrain.app.model

import com.lovebrain.app.model.ReplyDirective

/**
 * F09: 统一不可变生成输入——在 UI→domain 边界生成。
 *
 * 核心原则：
 * - 真实 dialogue 和控制信息（想法、意图、纠正）在进入业务链后分离。
 * - 来源查找、topic、scene、ongoing、recent 只接收 dialogue；控制信息不能作为事实证据。
 * - 无真实消息但有想法应进入明确的主动开场流程，不对空真实聊天硬做"回复对方"。
 *
 * UI 可继续用"她／我／想法"快速录入，进入业务链后才分离。
 */
data class GenerationInput(
    /** 真实对话消息——只含 HER 和 ME，不含 IDEA */
    val dialogue: List<DialogueMessage>,
    /** 用户本轮想法——控制信息，不作为事实证据 */
    val replyDirective: ReplyDirective? = null,
    /** 对象 ID（知识库名） */
    val kbName: String = "",
    /** 有效意图——冻结快照，不重新读取 */
    val effectiveIntent: IntentConfig = IntentConfig(),
    /** 记忆纠正——本轮应用的纠正 */
    val corrections: Map<String, MemoryCorrection> = emptyMap(),
    /** 请求 ID——用于幂等和取消 */
    val requestId: String = java.util.UUID.randomUUID().toString(),
    /** 是否仅看本轮——排除画像、记忆、场景、事项、意图、偏好 */
    val onlyThisRound: Boolean = false,
    /** 是否进攻模式 */
    val aggressive: Boolean = false
) {
    /**
     * F09: 从 ChatMessage 列表构建 GenerationInput。
     * 分离真实对话（HER/ME）和控制信息（IDEA）。
     */
    companion object {
        fun fromChatMessages(
            messages: List<ChatMessage>,
            userHint: String = "",
            kbName: String = "",
            effectiveIntent: IntentConfig = IntentConfig(),
            corrections: Map<String, MemoryCorrection> = emptyMap(),
            onlyThisRound: Boolean = false,
            aggressive: Boolean = false
        ): GenerationInput {
            // 分离真实对话和控制信息
            val realDialogue = messages
                .filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
                .map {
                    DialogueMessage(
                        id = it.id,
                        speaker = when (it.role) {
                            ChatMessage.Role.HER -> DialogueSpeaker.PARTNER
                            ChatMessage.Role.ME -> DialogueSpeaker.USER
                            else -> DialogueSpeaker.USER
                        },
                        text = it.content,
                        timestamp = it.timestamp
                    )
                }

            // 从 IDEA 消息中提取 replyDirective
            val ideaText = messages
                .filter { it.role == ChatMessage.Role.IDEA }
                .joinToString(" ") { it.content }
                .trim()
            val directive = if (ideaText.isNotBlank() || userHint.isNotBlank()) {
                ReplyDirective(text = userHint.ifBlank { ideaText })
            } else null

            return GenerationInput(
                dialogue = realDialogue,
                replyDirective = directive,
                kbName = kbName,
                effectiveIntent = effectiveIntent,
                corrections = corrections,
                onlyThisRound = onlyThisRound,
                aggressive = aggressive
            )
        }

        /**
         * F09: 从 GenerationInput 转回 ChatMessage 列表。
         * 用于兼容现有的 GenerationEngine.generate(messages: List<ChatMessage>) 接口。
         */
        fun toChatMessages(input: GenerationInput): List<ChatMessage> {
            val result = mutableListOf<ChatMessage>()
            for (msg in input.dialogue) {
                result.add(ChatMessage(
                    id = msg.id,
                    role = when (msg.speaker) {
                        DialogueSpeaker.PARTNER -> ChatMessage.Role.HER
                        DialogueSpeaker.USER -> ChatMessage.Role.ME
                    },
                    content = msg.text,
                    timestamp = msg.timestamp
                ))
            }
            // 想法作为 IDEA 消息添加
            input.replyDirective?.let { directive ->
                if (directive.text.isNotBlank()) {
                    result.add(ChatMessage(
                        role = ChatMessage.Role.IDEA,
                        content = directive.text
                    ))
                }
            }
            return result
        }
    }

    /**
     * F09: 转换为 ChatMessage 列表——兼容现有接口。
     */
    fun toChatMessages(): List<ChatMessage> = toChatMessages(this)

    /**
     * F09: 用户想法文本——优先 replyDirective，回退空。
     */
    fun userHint(): String = replyDirective?.text ?: ""

    /**
     * F09: 是否有真实对话消息。
     */
    fun hasDialogue(): Boolean = dialogue.isNotEmpty()

    /**
     * F09: 是否为主动开场场景——无真实对话但有想法或意图。
     */
    fun isProactiveOpener(): Boolean = dialogue.isEmpty() && (replyDirective != null || effectiveIntent.enabled)
}

/**
 * F09: 回复指令已在 Dialogue.kt 中定义（ReplyDirective），
 * 此处不再重复定义。GenerationInput 直接引用 Dialogue.kt 中的 ReplyDirective。
 */
