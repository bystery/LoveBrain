package com.lovebrain.app.model

/**
 * S2-01: 不可变生成输入边界——冻结一轮生成的全部输入。
 *
 * 替代散参传递（List<ChatMessage> + userHint + knowledgeBase + intentConfig + corrections + onlyThisRound）。
 * 进入 domain 后不再出现 ChatMessage.Role.IDEA——dialogue 只能是 PARTNER/USER。
 * ReplyDirective 独立，不能作为事实、topic、scene、ongoing 或 recent 证据。
 *
 * 冻结：requestId、KB identity/revision、有效 intent、correction revision、only-this-round、Provider identity。
 *
 * DialogueMessage 和 ReplyDirective 定义在 Dialogue.kt 中，此处不重复。
 */
data class GenerationInput(
    /** 唯一请求标识——同一 requestId 的事件才被 reducer 接受 */
    val requestId: String,
    /** 真实对话消息——只能是 PARTNER/USER，不含 IDEA */
    val dialogue: List<DialogueMessage>,
    /** 用户想法/意图提示（独立于对话，不混入 dialogue） */
    val replyDirective: ReplyDirective,
    /** 冻结的知识库身份 */
    val kbContext: KbContext?,
    /** 冻结的持续意图配置 */
    val intentConfig: IntentConfig,
    /** 冻结的记忆纠正及 revision */
    val corrections: Map<String, MemoryCorrection>,
    val correctionsRevision: Int,
    /** 仅看本轮开关 */
    val onlyThisRound: Boolean,
    /** 冻结的 Provider 身份（host hash，不含 Key） */
    val providerIdentity: ProviderIdentity?
)

/**
 * 冻结的知识库上下文——只携带 identity/revision，不携带可能漂移的实时状态。
 */
data class KbContext(
    val name: String,
    val stage: String,
    val profile: String,
    val revision: Int
)

/**
 * 冻结的 Provider 身份——只含 host hash 和 model，不含 API Key。
 */
data class ProviderIdentity(
    val hostHash: String,
    val model: String
)

/**
 * S2-01: 从 GenerationInput 转回 ChatMessage 列表的适配器。
 *
 * 用于 PromptBuilder 等仍接受 ChatMessage 的历史接口。
 * dialogue 中的 PARTNER/USER 转回 HER/ME，不产生 IDEA。
 */
fun GenerationInput.toChatMessages(): List<ChatMessage> =
    dialogue.map { msg ->
        ChatMessage(
            id = msg.id,
            role = when (msg.speaker) {
                DialogueSpeaker.PARTNER -> ChatMessage.Role.HER
                DialogueSpeaker.USER -> ChatMessage.Role.ME
            },
            content = msg.text,
            timestamp = msg.timestamp
        )
    }

/**
 * S2-01: 从 KbContext 重建 KnowledgeBase 对象。
 * KbContext 只冻结 identity/revision，重建的 KB 供 PromptBuilder 使用。
 */
fun KbContext.toKnowledgeBase(): KnowledgeBase = KnowledgeBase(
    name = name,
    displayName = name,
    stage = stage
)

/**
 * S2-01: 从 ChatMessage 列表构建 GenerationInput 的适配器。
 * UI 兼容 ChatMessage 只允许存在于单一 adapter；进入 domain 后不再出现 Role.IDEA。
 */
fun buildGenerationInput(
    requestId: String,
    messages: List<ChatMessage>,
    userHint: String,
    knowledgeBase: KnowledgeBase?,
    intentConfig: IntentConfig,
    corrections: Map<String, MemoryCorrection>,
    correctionsRevision: Int,
    onlyThisRound: Boolean,
    aggressive: Boolean,
    providerHostHash: String?,
    providerModel: String?
): GenerationInput {
    // S2-01: 分离对话与想法——dialogue 只含 PARTNER/USER，IDEA 进入 ReplyDirective
    val dialogue = messages
        .filter { it.role != ChatMessage.Role.IDEA }
        .map { msg ->
            DialogueMessage(
                id = msg.id,
                speaker = when (msg.role) {
                    ChatMessage.Role.HER -> DialogueSpeaker.PARTNER
                    else -> DialogueSpeaker.USER
                },
                text = msg.content,
                timestamp = msg.timestamp
            )
        }

    // IDEA 消息合并到 hint
    val ideaHint = messages
        .filter { it.role == ChatMessage.Role.IDEA }
        .joinToString("\n") { it.content }
        .trim()
    val combinedHint = if (ideaHint.isBlank()) userHint else if (userHint.isBlank()) ideaHint else "$userHint\n$ideaHint"

    return GenerationInput(
        requestId = requestId,
        dialogue = dialogue,
        replyDirective = ReplyDirective(text = combinedHint, aggressive = aggressive),
        kbContext = knowledgeBase?.let { kb ->
            KbContext(
                name = kb.name,
                stage = kb.stage,
                profile = "",
                // S2-01 审计修复: 使用 KnowledgeBase.turnCount 作为 revision 近似值，
                // 而非写死 0——turnCount 反映了 KB 的实际更新次数
                revision = kb.turnCount
            )
        },
        intentConfig = intentConfig,
        corrections = corrections,
        correctionsRevision = correctionsRevision,
        onlyThisRound = onlyThisRound,
        providerIdentity = if (providerHostHash != null && providerModel != null) {
            ProviderIdentity(hostHash = providerHostHash, model = providerModel)
        } else null
    )
}
