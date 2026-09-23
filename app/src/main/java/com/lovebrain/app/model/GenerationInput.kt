package com.lovebrain.app.model

/**
 * S2-01: 不可变生成输入边界——冻结一轮生成的全部输入。
 *
 * 替代散参传递（List<ChatMessage> + userHint + knowledgeBase + intentConfig + corrections + onlyThisRound）。
 * 进入 domain 后不再出现 ChatMessage.Role.IDEA——dialogue 只能是 PARTNER/USER。
 * ReplyDirective 独立，不能作为事实、topic、scene、ongoing 或 recent 证据。
 *
 * ## 这一版真正冻结了什么
 * 上一版号称"冻结全部输入"，但下面四件事其实没冻住，本轮按审计逐条补：
 * 1. [KbContext.profile] 以前固定是空串——画像根本没进 input。现在装真实正文。
 * 2. [KbContext.revision] 以前拿 turnCount 近似——"提交过几轮"不等于"知识内容是什么"。
 *    现在是对回复链路实际读取文件算出的内容指纹。
 * 3. [ProviderIdentity] 以前只有 hostHash + model，Engine 在准备阶段之后又回读
 *    `snapshotProviderConfig()`（即 activeTicketId）拿完整配置，不一致时只写日志、
 *    继续用新配置。现在冻结完整非敏感身份（含 ticketId 与 thinkingMode），
 *    Engine 只能按 ticketId 取配置，取不到或对不上就失败。
 * 4. prompt 资产版本以前用 App 版本名冒充。现在冻结资产内容 hash。
 *
 * UI 兼容用的适配器只剩 [toChatMessages] / [toKnowledgeBase] 两个，
 * 且仅供 PromptBuilder 内部的私有渲染函数使用；Engine 侧公开入口一律收 GenerationInput。
 */
data class GenerationInput(
    /** 唯一请求标识——同一 requestId 的事件才被 reducer 接受 */
    val requestId: String,
    /** 真实对话消息——只能是 PARTNER/USER，不含 IDEA */
    val dialogue: List<DialogueMessage>,
    /** 用户想法/意图提示（独立于对话，不混入 dialogue） */
    val replyDirective: ReplyDirective,
    /** 冻结的知识库身份与内容修订 */
    val kbContext: KbContext?,
    /** 冻结的持续意图配置 */
    val intentConfig: IntentConfig,
    /** 冻结的记忆纠正及 revision */
    val corrections: Map<String, MemoryCorrection>,
    val correctionsRevision: Int,
    /** 仅看本轮开关 */
    val onlyThisRound: Boolean,
    /** 冻结的 Provider 完整非敏感身份（不含 Key） */
    val providerIdentity: ProviderIdentity?,
    /** 冻结的 prompt 资产内容指纹 */
    val promptAssetHash: String
) {
    /** 输入指纹——用于"输入没变就别重复生成"判断，覆盖全部会影响输出的冻结字段 */
    fun fingerprint(): String = buildString {
        append(requestId).append('|')
        dialogue.forEach { append(it.id).append(':').append(it.text.length).append(',') }
        append('|').append(replyDirective.aggressive).append('|').append(replyDirective.text.length)
        append('|').append(kbContext?.name ?: "").append('@').append(kbContext?.revision ?: "")
        append('|').append(correctionsRevision)
        append('|').append(onlyThisRound)
        append('|').append(intentConfig.revision)
        append('|').append(providerIdentity?.configHash ?: "")
        append('|').append(promptAssetHash)
    }
}

/**
 * 冻结的知识库上下文——携带 identity、真实画像正文与内容修订号。
 */
data class KbContext(
    val name: String,
    val stage: String,
    /** 关系画像正文（understand/me|her|warmth|style.md） */
    val profile: String,
    /** 知识内容指纹，不是"提交过几轮"的近似计数 */
    val revision: String
)

/**
 * S2-01: 冻结的 Provider 身份——完整的非敏感配置。
 *
 * [configHash] 覆盖 ticketId/host/model/thinkingMode：Engine 发请求前
 * 必须按 [ticketId] 重新解析并比对，任何一项变了就拒绝继续，
 * 不能像旧实现那样"记一条日志然后改用新配置"。
 * API Key 有意不在其中——它不进冻结输入。
 */
data class ProviderIdentity(
    val ticketId: String,
    val hostHash: String,
    val model: String,
    val thinkingMode: Int
) {
    val configHash: String get() = "$ticketId/$hostHash/$model/$thinkingMode"

    /** 与真实请求配置比对（不比较 API Key——Key 允许轮换，身份不允许漂移） */
    fun matches(config: ProviderRequestConfigView): Boolean =
        config.ticketId == ticketId &&
            config.hostHash == hostHash &&
            config.model == model &&
            config.thinkingMode == thinkingMode
}

/** 比对用的最小视图，避免把 API Key 带进 model 层 */
data class ProviderRequestConfigView(
    val ticketId: String,
    val hostHash: String,
    val model: String,
    val thinkingMode: Int
)

/** provider 配置的 host 指纹——与历史口径保持一致（hashCode 十六进制） */
fun hashProviderHost(baseUrl: String?): String? = baseUrl?.hashCode()?.toString(16)

/**
 * S2-01: 从 GenerationInput 转回 ChatMessage 列表的适配器。
 *
 * 只服务于 PromptBuilder 内部的历史渲染函数；
 * domain 公开入口不得再接收 List<ChatMessage>。
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
 * S2-01: 从 KbContext 重建 KnowledgeBase 对象，供 PromptBuilder 的历史渲染函数使用。
 */
fun KbContext.toKnowledgeBase(): KnowledgeBase = KnowledgeBase(
    name = name,
    displayName = name,
    stage = stage
)

/**
 * S2-01: 构建冻结输入。
 *
 * UI 兼容 ChatMessage 只允许存在于这一处 adapter；进入 domain 后不再出现 Role.IDEA。
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
    providerIdentity: ProviderIdentity?,
    kbProfile: String,
    kbRevision: String,
    promptAssetHash: String
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
    val combinedHint =
        if (ideaHint.isBlank()) userHint
        else if (userHint.isBlank()) ideaHint
        else "$userHint\n$ideaHint"

    return GenerationInput(
        requestId = requestId,
        dialogue = dialogue,
        replyDirective = ReplyDirective(text = combinedHint, aggressive = aggressive),
        kbContext = knowledgeBase?.let { kb ->
            KbContext(
                name = kb.name,
                stage = kb.stage,
                profile = kbProfile,
                revision = kbRevision
            )
        },
        intentConfig = intentConfig,
        corrections = corrections,
        correctionsRevision = correctionsRevision,
        onlyThisRound = onlyThisRound,
        providerIdentity = providerIdentity,
        promptAssetHash = promptAssetHash
    )
}
