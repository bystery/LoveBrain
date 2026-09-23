package com.lovebrain.app.model

/**
 * S2-03: 领域事件——use case 与 ViewModel 之间唯一的通信协议。
 *
 * ## 这里解决的缺陷
 * 旧实现里 GenerationEngine 通过一个四十多个方法的 `Callbacks` 大接口反向写 ViewModel，
 * 而且回调**不带请求身份**：ViewModel 只能在回调到达那一刻读"当前 requestId"，
 * 于是被取代的旧请求的迟到 chunk/result 会被贴上新请求的 requestId，被 reducer 当成合法事件接受。
 *
 * 现在：
 * - Engine 是调用协程里的 suspend 函数，往 [emit] 通道发事件，不再启动第二个 Job、不再持有 scope；
 * - 每个事件自带产生时冻结的 requestId；
 * - ViewModel 只有一个写入口 [ReplyReducer.reduce]，身份不符的事件整条丢弃（连状态对象都不换）。
 *
 * 事件只承载 domain 数据。PanelState / isGenerating 这类 UI 语义由 reducer 派生，
 * Engine 不再直接命令 UI。
 */

/** 一条生成请求的事件基类 */
sealed interface GenerationEvent {
    /** 事件所属请求的身份，由 use case 在开始时冻结 */
    val requestId: String
}

// ════════════════════════════════════════════════════════════════
// 回复生成
// ════════════════════════════════════════════════════════════════

sealed interface ReplyEvent : GenerationEvent

/**
 * 用户已发起请求——进入准备阶段。
 *
 * 这是唯一"认领新身份"的事件：只有它允许把 [ReplyUiState.ownerRequestId] 换成一个新值，
 * 因此被取代的旧请求不可能再通过任何事件伪装成当前请求。
 */
data class ReplyRequested(override val requestId: String) : ReplyEvent

/** 请求进入流式阶段（准备完成、Provider 已接受连接） */
data class ReplyStarted(override val requestId: String) : ReplyEvent

/** 流式正文增量 */
data class ReplyChunk(override val requestId: String, val text: String) : ReplyEvent

/** 清空流式正文（retry / 参数降级前） */
data class ReplyChunkReset(override val requestId: String) : ReplyEvent

/** 流式方案卡快照 */
data class ReplySchemesArrived(
    override val requestId: String,
    val schemes: List<Scheme>
) : ReplyEvent

/** 清空流式方案卡（retry 前，防上一次 attempt 的半成品污染） */
data class ReplySchemesReset(override val requestId: String) : ReplyEvent

/** 本轮注入的记忆引用清单（UI 展示纠正入口） */
data class ReplyMemoryRefs(
    override val requestId: String,
    val refs: List<MemoryRef>
) : ReplyEvent

/** 来源别名 → 真实消息 ID 映射（TopicRecorder 来源校验用） */
data class ReplySourceAliasMap(
    override val requestId: String,
    val aliasMap: Map<String, String>
) : ReplyEvent

/** Provider usage 回报 */
data class ReplyUsage(
    override val requestId: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val costYuan: Double?
) : ReplyEvent

/** 首字耗时 */
data class ReplyFirstToken(override val requestId: String, val elapsedMs: Long) : ReplyEvent

/** 第一条可复制方案就绪耗时 */
data class ReplyFirstReplyReady(override val requestId: String, val elapsedMs: Long) : ReplyEvent

/** 终态：成功或带用户文案的失败 */
data class ReplyCompleted(
    override val requestId: String,
    val result: GenerateResult
) : ReplyEvent

/** 被用户停止 */
data class ReplyStopped(override val requestId: String) : ReplyEvent

/**
 * 用户显式结束本轮（nextRound / 清空结果）。
 *
 * 仍然带 requestId：只有与当前 owner 一致才生效，
 * 否则"上一轮的清空动作"会把刚发起的新请求抹掉。
 */
data class ReplyCleared(override val requestId: String) : ReplyEvent

// ════════════════════════════════════════════════════════════════
// 谈心 / 锦囊 / 主动开场
// ════════════════════════════════════════════════════════════════

sealed interface CounselingEvent : GenerationEvent

data class CounselingStarted(override val requestId: String) : CounselingEvent
data class CounselingFirstToken(override val requestId: String, val elapsedMs: Long) : CounselingEvent
data class CounselingChunk(override val requestId: String, val text: String) : CounselingEvent
data class CounselingResult(
    override val requestId: String,
    val replyText: String,
    val analysisText: String,
    val kbName: String?,
    /** 本轮倾诉原文——事件自带，落日志时不回读实时草稿 */
    val userMessage: String
) : CounselingEvent
data class CounselingFailed(override val requestId: String, val message: String) : CounselingEvent
data class CounselingEnded(override val requestId: String) : CounselingEvent

sealed interface SuggestEvent : GenerationEvent

data class SuggestStarted(override val requestId: String) : SuggestEvent
data class SuggestTips(
    override val requestId: String,
    val tips: List<SuggestTip>
) : SuggestEvent
data class SuggestResult(
    override val requestId: String,
    val suggestion: DailySuggestion?
) : SuggestEvent
data class SuggestFailed(override val requestId: String, val message: String) : SuggestEvent
data class SuggestFirstToken(override val requestId: String, val elapsedMs: Long) : SuggestEvent
data class SuggestEnded(override val requestId: String) : SuggestEvent

sealed interface ProactiveEvent : GenerationEvent

data class ProactiveStarted(override val requestId: String) : ProactiveEvent
data class ProactiveOptions(
    override val requestId: String,
    val options: List<ProactiveOption>
) : ProactiveEvent
data class ProactiveFailed(override val requestId: String, val message: String) : ProactiveEvent
data class ProactiveFirstToken(override val requestId: String, val elapsedMs: Long) : ProactiveEvent
data class ProactiveEnded(override val requestId: String) : ProactiveEvent

// ════════════════════════════════════════════════════════════════
// 回复状态机
// ════════════════════════════════════════════════════════════════

/**
 * 回复流程的完整 UI 状态。
 *
 * ViewModel 里这些字段不再有独立的 MutableStateFlow 可写——
 * 唯一写入口是 [ReplyReducer.reduce]。
 */
data class ReplyUiState(
    val request: ReplyRequestState = ReplyRequestState.Idle,
    /**
     * 当前被受理的请求身份。
     *
     * 与 [request] 分开保存，是为了修掉一个真实缺陷：
     * 旧实现在 Idle 时 `current.requestId == null`，于是"任意迟到的 Completed"
     * 都能通过 requestId 校验。Idle 之后这里仍保留刚刚结束那个请求的身份，
     * 同身份的后续迟到事件照样被拒。
     */
    val ownerRequestId: String? = null,
    val streamingCoreText: String = "",
    val streamingSchemes: List<Scheme> = emptyList(),
    val result: GenerateResult? = null,
    val memoryRefs: List<MemoryRef> = emptyList(),
    val sourceAliasMap: Map<String, String> = emptyMap(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val costYuan: Double? = null,
    val firstTokenMs: Long = 0L,
    val firstReplyMs: Long = 0L,
    /** 本轮生成开始时刻，用于派生"首条可复制回复耗时" */
    val startedAtMs: Long = 0L,
    /** 成功生成次数——只在 Completed(Success) 时递增 */
    val successRounds: Int = 0,
    val panelState: PanelState = PanelState.KEYBOARD
) {
    val isBusy: Boolean get() = request.isBusy
}

/**
 * S2-03: 回复 reducer——唯一状态入口。
 *
 * 规则：
 * 1. 身份不符 → 原样返回**同一个对象**，调用方据此知道事件被拒。
 * 2. Idle/终态下只接受 [ReplyStarted]；已结束请求的迟到事件一律丢弃。
 * 3. UI 派生态（panelState、是否生成中）在这里算，不由 Engine 直接命令。
 */
object ReplyReducer {

    fun reduce(current: ReplyUiState, event: ReplyEvent): ReplyUiState {
        // ── 认领新请求：唯一可以更换 owner 的入口 ──
        if (event is ReplyRequested) {
            return ReplyUiState(
                request = ReplyRequestState.Preparing(event.requestId),
                ownerRequestId = event.requestId,
                panelState = PanelState.AI_LOADING
            )
        }

        if (event is ReplyStarted) {
            val owner = current.ownerRequestId
            if (owner != event.requestId) return current
            if (current.request !is ReplyRequestState.Preparing) return current
            return current.copy(
                request = ReplyRequestState.Streaming(event.requestId),
                startedAtMs = if (current.startedAtMs > 0L) current.startedAtMs else System.currentTimeMillis()
            )
        }

        // 用户显式结束本轮：不要求任务在途，但身份必须对得上。
        // 成功后状态已回 Idle，若把 ReplyCleared 也压在 isBusy 门禁后面，
        // nextRound() 就永远清不掉上一轮结果。
        if (event is ReplyCleared) {
            return if (event.requestId == (current.ownerRequestId ?: "")) {
                ReplyUiState(panelState = current.panelState)
            } else current
        }

        // ── 其余事件：身份 + 活跃阶段双重门禁 ──
        val owner = current.ownerRequestId ?: return current
        if (event.requestId != owner) return current
        if (!current.request.isBusy) return current

        return when (event) {
            is ReplyChunk -> current.copy(
                streamingCoreText = current.streamingCoreText + event.text
            )
            is ReplyChunkReset -> current.copy(streamingCoreText = "")
            is ReplySchemesArrived -> {
                val firstReady =
                    if (current.firstReplyMs == 0L && current.startedAtMs > 0L &&
                        event.schemes.any { it.reply.isNotBlank() }
                    ) System.currentTimeMillis() - current.startedAtMs else current.firstReplyMs
                current.copy(streamingSchemes = event.schemes, firstReplyMs = firstReady)
            }
            is ReplySchemesReset -> current.copy(streamingSchemes = emptyList())
            is ReplyMemoryRefs -> current.copy(memoryRefs = event.refs)
            is ReplySourceAliasMap -> current.copy(sourceAliasMap = event.aliasMap)
            is ReplyUsage -> current.copy(
                promptTokens = event.promptTokens ?: current.promptTokens,
                completionTokens = event.completionTokens ?: current.completionTokens,
                costYuan = event.costYuan ?: current.costYuan
            )
            is ReplyFirstToken -> current.copy(
                firstTokenMs = if (current.firstTokenMs > 0L) current.firstTokenMs else event.elapsedMs
            )
            is ReplyFirstReplyReady -> current.copy(
                firstReplyMs = if (current.firstReplyMs > 0L) current.firstReplyMs else event.elapsedMs
            )
            is ReplyCompleted -> when (event.result) {
                is GenerateResult.Success -> current.copy(
                    request = ReplyRequestState.Idle,
                    result = event.result,
                    streamingCoreText = "",
                    successRounds = current.successRounds + 1,
                    panelState = PanelState.AI_RESULT
                )
                is GenerateResult.Error -> current.copy(
                    request = ReplyRequestState.RecoverableError(
                        message = event.result.message,
                        retryable = true
                    ),
                    result = event.result,
                    streamingCoreText = "",
                    streamingSchemes = emptyList(),
                    panelState = PanelState.AI_RESULT
                )
            }
            is ReplyStopped -> current.copy(
                request = ReplyRequestState.Idle,
                streamingCoreText = "",
                streamingSchemes = emptyList(),
                panelState = PanelState.KEYBOARD
            )
            // 用户显式结束本轮：只有身份对得上才清，避免"上一轮的清空"抹掉刚发起的新请求
            is ReplyCleared -> ReplyUiState(panelState = current.panelState)
            is ReplyRequested -> current
            is ReplyStarted -> current
        }
    }
}
