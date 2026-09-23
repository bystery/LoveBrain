package com.lovebrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.data.CostScope
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.AssetRegistry
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.domain.toIdentity
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.CounselingChunk
import com.lovebrain.app.model.CounselingEnded
import com.lovebrain.app.model.CounselingEvent
import com.lovebrain.app.model.CounselingFailed
import com.lovebrain.app.model.CounselingFirstToken
import com.lovebrain.app.model.CounselingResult
import com.lovebrain.app.model.CounselingStarted
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.PanelState
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveEvent
import com.lovebrain.app.model.ProactiveFailed
import com.lovebrain.app.model.ProactiveFirstToken
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ProactiveStarted
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.ReplyChunk
import com.lovebrain.app.model.ReplyCompleted
import com.lovebrain.app.model.ReplyEvent
import com.lovebrain.app.model.ReplyCleared
import com.lovebrain.app.model.ReplyRequested
import com.lovebrain.app.model.ReplyStopped
import com.lovebrain.app.model.ReplyUiState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SuggestEnded
import com.lovebrain.app.model.SuggestEvent
import com.lovebrain.app.model.SuggestFailed
import com.lovebrain.app.model.SuggestFirstToken
import com.lovebrain.app.model.SuggestResult
import com.lovebrain.app.model.SuggestStarted
import com.lovebrain.app.model.SuggestTips
import com.lovebrain.app.model.ReplyFailureKind
import com.lovebrain.app.model.ReplyRequestState
import com.lovebrain.app.model.buildGenerationInput
import com.lovebrain.app.model.isBusy
import com.lovebrain.app.model.isPreparing
import com.lovebrain.app.model.isStreaming
import com.lovebrain.app.model.requestId
import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.ProfileTransactionResult
import com.lovebrain.app.model.ReplyReducer
import com.lovebrain.app.model.PreconditionReason
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.StageSuggestion
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.util.Jsons
import com.lovebrain.app.util.L
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
/** 谈心记录标题截断：分析首行 */
private const val TITLE_FIRST_LINE_LIMIT = 60
/** 谈心记录标题截断：用户消息回退 */
private const val TITLE_FALLBACK_LIMIT = 40

/** ：谈心草稿写盘防抖窗口——连续击键只在停顿后落盘一次（强杀最多丢 ≤600ms 输入；正常关闭经 dispose flush 零丢失） */
private const val COUNSELING_DRAFT_DEBOUNCE_MS = 600L


/**
 * 军师核心 ViewModel v4（ 后为状态壳：生成逻辑下沉到 GenerationEngine）。
 */

class LoveBrainViewModel(
    private val deepSeekRepo: DeepSeekRepository,
    private val knowledgeRepo: KnowledgeRepository,
    private val promptBuilder: PromptBuilder,
    private val topicRecorder: TopicRecorder,
    private val securePrefs: SecurePrefs,
    private val triggerCoordinator: KnowledgeTriggerCoordinator,
    private val generationEngine: GenerationEngine,
    // S2-02: 统一前台任务协调器——管理所有 AI 前台流程的互斥和生命周期
    val operationCoordinator: ForegroundOperationCoordinator,
    // F02: 反馈案例仓库——点踩时本地保存
    private val feedbackCaseRepository: com.lovebrain.app.data.FeedbackCaseRepository? = null
) : ViewModel(), KnowledgeTriggerCoordinator.Callbacks {

    // F02/P1-A: 当前反馈案例——点踩时同步构造并暴露给 UI，消除"保存后再全量查询"竞态
    private val _currentFeedbackCase = MutableStateFlow<com.lovebrain.app.model.FeedbackCase?>(null)
    val currentFeedbackCase: StateFlow<com.lovebrain.app.model.FeedbackCase?> = _currentFeedbackCase.asStateFlow()

    private val _panelState = MutableStateFlow(PanelState.KEYBOARD)
    val panelState: StateFlow<PanelState> = _panelState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // ═══ S2-02/S2-03: 回复流程唯一状态源 ═══
    /**
     * 回复流程的全部状态。
     *
     * S2-03 之前这里是六个各自独立的 MutableStateFlow（_result / _replyRequestState /
     * _streamingCoreText / _streamingSchemes / _generationRoundId / _currentVersionId），
     * 谁都能写。现在唯一写入口是 [dispatchReply] → [ReplyReducer.reduce]。
     * 下面暴露的 result / replyRequestState / streamingCoreText / … 全部由这里 map 出去，
     * 不再是独立真源。
     */
    private val _replyUi = MutableStateFlow(ReplyUiState())

    val result: StateFlow<GenerateResult?> = _replyUi.map { it.result }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    /** 统一回复请求状态——单一事实源。替代分散的 isPreparing/isGenerating/isGeneratingCore。 */
    val replyRequestState: StateFlow<ReplyRequestState> = _replyUi.map { it.request }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, ReplyRequestState.Idle)

    /** 派生属性——从唯一状态源派生，不再是独立可写状态 */
    val isGenerating: StateFlow<Boolean> = _replyUi.map { it.isBusy }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    val isGeneratingCore: StateFlow<Boolean> = _replyUi.map { it.isBusy }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    /** P0-3: 稳定的轮次身份——只在真正完成一次新的整轮 generate 时变化。
     * 单条改写、undo、feedback 等原地操作不改变它。
     * ResultArea 的 viewMode 只以 round id 重置。 */
    private val _generationRoundId = MutableStateFlow(0)
    val generationRoundId: StateFlow<Int> = _generationRoundId.asStateFlow()

    // ═══ S2-02: 前台任务不在 ViewModel 里留 Job 字段 ═══
    // 旧实现在这里放 generateJob / counselingJob / suggestJob，各自挂 invokeOnCompletion 清引用，
    // 于是"谁在跑"有两本账（Job 字段 + coordinator），且 stopGeneration 一次取消三类操作。
    // 现在只有 coordinator 一本账，停止按租约/按类型走。

    // S1-04 审计修复：冻结锦囊请求身份——生成开始时冻结，完成时使用冻结值而非实时 _activeKb
    // 防止生成期间切 KB 导致缓存写入错误的 kbId
    private var suggestRequestKbName: String? = null
    private var suggestRequestContextFp: String = ""
    private var suggestRequestPromptVersion: String = ""
    // ═══════════ GEN-02：本轮生成上下文（不可变快照） ═══════════
    /**
     * 一轮 AI 生成 = 固定消息快照 + 固定知识库 + 固定 AI 回复 + 固定用户反馈。
     * 生成开始时建立，保存成功后才清除。停止生成时也清除（本轮无成功结果）。
     *
     * F07/F08: 扩展为真正的请求快照，冻结 kbName/kbId、messages、IDEA、
     * 持续意图 text/enabled/revision，以及未提交 IDEA 草稿。
     */
    private data class ReplyGenerationContext(
        val messages: List<ChatMessage>,
        val messageIds: Set<String>,
        val kbName: String?,
        val ideaHint: String,              // F08: 冻结的 IDEA hint（含未提交草稿）
        val intentText: String,            // F07: 冻结的持续意图文本
        val intentEnabled: Boolean,        // F07: 冻结的持续意图启用状态
        val intentRevision: Int,           // F07: 冻结的持续意图 revision（识别旧请求）
        val memoryRefs: List<com.lovebrain.app.model.MemoryRef> = emptyList(), // F09: 冻结的 MemoryRef 清单
        val correctionsRevision: Int = 0,  // F09: 冻结的纠正 revision（防迟到覆盖）
        val sourceAliasMap: Map<String, String> = emptyMap(), // B项修复：别名→实际消息ID映射
        val inputFingerprint: String = "", // F11: 输入指纹——对 KB+消息正文+角色+顺序+IDEA+onlyThisRound+intent revision 做哈希
        val onlyThisRound: Boolean = false // F16: 冻结 onlyThisRound 状态
    )
    private var replyGenerationContext: ReplyGenerationContext? = null

    // ═══════════ F04: 轮次级瞬时纠正（不持久化，nextRound/切库时清空） ═══════════
    /**
     * F04: THIS_ROUND mute 的瞬时存储——只在当前轮次有效，不写入 corrections.json。
     * key = memoryId, value = MemoryCorrection(action=MUTED, muteDuration=THIS_ROUND)
     * 在 nextRound()、切库时清空。stopGeneration 不清——停止生成不等于结束当前工作轮。
     * 生成时与持久化 corrections 合并传入 PromptBuilder。 */
    private val roundCorrections = mutableMapOf<String, com.lovebrain.app.model.MemoryCorrection>()

    // ═══════════ F11: 输入变化提示 + 生成历史 ═══════════

    /** F11: 输入已变化——result 存在但 messages/ideaHint 与生成时快照不一致 */
    private val _inputChanged = MutableStateFlow(false)
    val inputChanged: StateFlow<Boolean> = _inputChanged.asStateFlow()

    /** F11/P0-D: 生成版本 ID——每轮成功生成的唯一身份 */
    data class GenerationVersionId(val value: String) {
        companion object {
            fun next(): GenerationVersionId = GenerationVersionId(java.util.UUID.randomUUID().toString())
        }
    }

    /** F11/P0-D: 生成历史——每轮成功生成时保存的版本快照，携带完整版本身份
     * 注意：这是 session-only 内存历史，杀进程即消失。不声称跨重启完整版本历史。
     * P0-1: snapshot 保存完整 immutable ReplyGenerationContext，
     * rollback 时原子恢复 result + versionId + context，避免 result/context 错配。 */
    private data class GenerationSnapshot(
        val versionId: GenerationVersionId,
        val result: GenerateResult.Success,
        val context: ReplyGenerationContext,
        val kbName: String?,
        val createdAt: Long = System.currentTimeMillis()
    )
    /** F11/P0-D: session 生成历史——内存 StateFlow，杀进程即消失。不声称跨重启完整版本历史。
     * P0-1: 最多保留最近 20 个版本，防止长 session 无限增长。 */
    private val _generationHistory = MutableStateFlow<List<GenerationSnapshot>>(emptyList())
    val generationHistorySize: Int get() = _generationHistory.value.size
    private val MAX_HISTORY_SIZE = 20

    /** P0-D: 当前活跃版本 ID——最新成功生成的版本身份，用于绑定点踩/发送/改写 */
    private val _currentVersionId = MutableStateFlow<GenerationVersionId?>(null)
    val currentVersionId: StateFlow<GenerationVersionId?> = _currentVersionId.asStateFlow()

    /**
     * 流式正文 / 方案卡——从 [_replyUi] 派生，不再有独立可写的 StateFlow。
     *
     * P3-04 的"别每个 token 全量重绘一次"不再靠 StringBuilder+定时器绕过状态源，
     * 而是在 [dispatchReply] 里把相邻的 chunk 合并成一个事件再归约。
     * 这样节流和"reducer 是唯一写入口"不再互相牺牲。
     */
    val streamingCoreText: StateFlow<String> = _replyUi.map { it.streamingCoreText }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "")

    /** 流式过程中已完整解析出的方案卡（逐张渲染，边收边出） */
    val streamingSchemes: StateFlow<List<Scheme>> = _replyUi.map { it.streamingSchemes }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** 待合并的流式增量；只在 [dispatchReply] 与其触发的 flush 里读写 */
    private val pendingChunk = StringBuilder()
    private var chunkFlushJob: kotlinx.coroutines.Job? = null
    private val STREAMING_FLUSH_INTERVAL_MS = 50L

    /** 读当前结果——所有内部读取走这里，不再有一个可写的 _result */
    private val replyResult: GenerateResult? get() = _replyUi.value.result

    /**
     * 原地替换结果文本（单条改写 / undo / 版本回退用）。
     *
     * 这些操作不属于任何一次生成请求，所以它们不伪装成 request 事件走 reducer，
     * 但仍然只写 [_replyUi] 这一个状态对象——不存在第二本结果账。
     */
    private fun replaceReplyResult(result: GenerateResult) {
        _replyUi.value = _replyUi.value.copy(result = result)
    }

    /** 当前本轮想法文本（已提交 IDEA + 未提交草稿） */
    internal fun getUserHint(): String = collectIdeaHintWithDraft(_messages.value)

    /** IDEA 草稿 + 已提交 IDEA 合并成本轮想法文本 */
    private fun collectIdeaHintWithDraft(messages: List<ChatMessage>): String {
        val committed = messages.filter { it.role == ChatMessage.Role.IDEA }
            .joinToString("\n") { it.content }
            .trim()
        val draft = _draftText.value.trim()
        return when {
            committed.isNotBlank() && draft.isNotBlank() -> "$committed\n$draft"
            committed.isNotBlank() -> committed
            else -> draft
        }
    }

    private val _activeKb = MutableStateFlow<KnowledgeBase?>(null)
    val activeKb: StateFlow<KnowledgeBase?> = _activeKb.asStateFlow()

    //  KBG-02：ProfileSuggestion 作为单一事实源，携带 originating kbName
    private val _profileSuggestion = MutableStateFlow<ProfileSuggestion?>(null)
    val profileSuggestion: StateFlow<ProfileSuggestion?> = _profileSuggestion.asStateFlow()

    /** 画像确认提交中状态——提交期间禁用重复点击，幂等 */
    private val _isProfileConfirming = MutableStateFlow(false)
    val isProfileConfirming: StateFlow<Boolean> = _isProfileConfirming.asStateFlow()

    /** 知识库后台操作的临时提示（如经验提取完成），在悬浮窗内短暂展示 */
    private val _kbNotice = MutableStateFlow<String?>(null)
    val kbNotice: StateFlow<String?> = _kbNotice.asStateFlow()
    fun dismissKbNotice() { _kbNotice.value = null }

    /** ：面板级临时警告（未配置引导/未记入提示），悬浮窗内短暂展示 */
    private val _panelWarning = MutableStateFlow<String?>(null)
    val panelWarning: StateFlow<String?> = _panelWarning.asStateFlow()
    fun showPanelWarning(msg: String) { _panelWarning.value = msg }
    fun dismissPanelWarning() { _panelWarning.value = null }

    /** 五维向量最近一次重估的变化摘要（面板短暂展示） */
    private val _vectorUpdate = MutableStateFlow<String?>(null)
    val vectorUpdate: StateFlow<String?> = _vectorUpdate.asStateFlow()
    fun dismissVectorUpdate() { _vectorUpdate.value = null }

    /** 向量重估触发的阶段调整建议（用户确认后生效） */
    private val _stageSuggestion = MutableStateFlow<StageSuggestion?>(null)
    val stageSuggestion: StateFlow<StageSuggestion?> = _stageSuggestion.asStateFlow()
    fun dismissStageChange() { _stageSuggestion.value = null }
    fun confirmStageChange() {
        val s = _stageSuggestion.value ?: return
        viewModelScope.launch {
            knowledgeRepo.updateStage(s.kbName, s.newStage)
            knowledgeRepo.updateWarmthStageLabel(s.kbName, s.newStage)
            _stageSuggestion.value = null
        }
    }

    // ════════ -: 激活工单 + 模型选择 ═══════════
    
    /** 当前激活的工单 */
    private val _activeTicket = MutableStateFlow<com.lovebrain.app.model.ProviderTicket?>(null)
    val activeTicket: StateFlow<com.lovebrain.app.model.ProviderTicket?> = _activeTicket.asStateFlow()

    /**  ：供应商就绪态下沉（面板不再本地计算）：工单存在 && 模型非空 && Key 非空 */
    private val _providerReady = MutableStateFlow(false)
    val providerReady: StateFlow<Boolean> = _providerReady.asStateFlow()

    /** 刷新激活工单（面板重新可见时调用，解决 Service 长生命周期下配置后不刷新问题） */
    fun refreshTicketState() {
        viewModelScope.launch {
            val tickets = securePrefs.getWorkerTickets()
            val activeId = securePrefs.activeTicketId
            if (activeId != null && activeId.isNotEmpty()) {
                val ticket = tickets.find { it.id == activeId }
                if (ticket != null) {
                    _activeTicket.value = ticket
                    // ：就绪三条件（含 Key 非空）在 VM 统一判定，面板只订阅结果
                    _providerReady.value = ticket.model.isNotBlank() &&
                        !securePrefs.getWorkerApiKey(ticket.id).isNullOrBlank()
                    return@launch
                }
            }
            _activeTicket.value = null
            _providerReady.value = false
        }
    }
    // ========================================================

    /** 当前知识库的五维状态向量（供面板状态卡片展示） */
    private val _currentVector = MutableStateFlow<Map<String, Int>>(emptyMap())
    val currentVector: StateFlow<Map<String, Int>> = _currentVector.asStateFlow()

    /** 最近一次重估的五维变化量（新值 - 旧值，供卡片显示涨跌箭头） */
    private val _vectorDelta = MutableStateFlow<Map<String, Int>>(emptyMap())
    val vectorDelta: StateFlow<Map<String, Int>> = _vectorDelta.asStateFlow()

    private val _feedbacks = MutableStateFlow<Map<String, SchemeFeedback>>(emptyMap())
    val feedbacks: StateFlow<Map<String, SchemeFeedback>> = _feedbacks.asStateFlow()

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private val _counselingDraft = MutableStateFlow("")
    val counselingDraft: StateFlow<String> = _counselingDraft.asStateFlow()
    /** ：谈心草稿防抖写盘任务（取消旧任务 + 延迟 600ms 落盘，防每击键一次加密写盘） */
    private var draftPersistJob: kotlinx.coroutines.Job? = null

    private val _panelMode = MutableStateFlow(0) // 0=reply, 1=counseling
    val panelMode: StateFlow<Int> = _panelMode.asStateFlow()

    /** 输出模式二态（0=普通 1=进攻；悬浮窗切换，下次请求生效） */
    private val _outputMode = MutableStateFlow(securePrefs.outputMode)
    val outputMode: StateFlow<Int> = _outputMode.asStateFlow()

    fun setOutputMode(mode: Int) {
        // ：直出/思考悬浮窗切换面已移除（改工单级开关），思考值直读持久层参与校验
        val result = promptBuilder.validateConfig(securePrefs.thinkingMode, mode)
        result.warnings.forEach { L.w("⚠️ $it") }
        _outputMode.value = result.outputMode
        securePrefs.outputMode = result.outputMode
    }

    /** ═══════════ ：花费/耗时展示（VM 聚合） ═══════════ */

    /** 今日累计花费（元；跨天清零，持久层对账） */
    private val _todayCostYuan = MutableStateFlow(0.0)
    val todayCostYuan: StateFlow<Double> = _todayCostYuan.asStateFlow()

    /** 本次生成花费（元；null = 未计费，UI 占位"—"） */
    private val _lastCostYuan = MutableStateFlow<Double?>(null)
    val lastCostYuan: StateFlow<Double?> = _lastCostYuan.asStateFlow()

    /** 最近一次生成首字耗时（毫秒；0 = 尚未生成） */
    private val _lastResponseMs = MutableStateFlow(0L)
    val lastResponseMs: StateFlow<Long> = _lastResponseMs.asStateFlow()

    // F12: 性能统计
    /** 累计生成次数（跨重启持久化） */
    private val _totalGenerateCount = MutableStateFlow(0)
    val totalGenerateCount: StateFlow<Int> = _totalGenerateCount.asStateFlow()

    /** 累计花费（元；跨重启持久化） */
    private val _totalCostYuan = MutableStateFlow(0.0)
    val totalCostYuan: StateFlow<Double> = _totalCostYuan.asStateFlow()

    /** 今日花费的计费日期（跨零点滚动清零用） */
    private var todayCostDate: String = java.time.LocalDate.now().toString()

    // F12: 详细统计
    /** 累计复制次数 */
    private val _totalCopyCount = MutableStateFlow(0)
    val totalCopyCount: StateFlow<Int> = _totalCopyCount.asStateFlow()

    /** 累计采用次数（记录实际发送） */
    private val _totalAdoptCount = MutableStateFlow(0)
    val totalAdoptCount: StateFlow<Int> = _totalAdoptCount.asStateFlow()

    /** 累计改写次数 */
    private val _totalRewriteCount = MutableStateFlow(0)
    val totalRewriteCount: StateFlow<Int> = _totalRewriteCount.asStateFlow()

    /** 首条可复制回复耗时（毫秒；0 = 尚未生成）
     *  P1-H: 从生成开始到首张方案卡完整解析的真实耗时，不再近似等于首字耗时 */
    private val _firstReplyMs = MutableStateFlow(0L)
    val firstReplyMs: StateFlow<Long> = _firstReplyMs.asStateFlow()

    /** P1-H: 本轮生成开始时间戳（用于计算首条可复制回复耗时） */
    private var generateStartTimeMs: Long = 0L

    private val _currentRole = MutableStateFlow(ChatMessage.Role.HER)
    val currentRole: StateFlow<ChatMessage.Role> = _currentRole.asStateFlow()

    private val _editingIndex = MutableStateFlow(-1)
    val editingIndex: StateFlow<Int> = _editingIndex.asStateFlow()

    /** 输入行《想法》chip 态（：仅影响面板输入去向；捕获收口见 setCurrentRole） */
    private val _ideaComposeMode = MutableStateFlow(false)
    val ideaComposeMode: StateFlow<Boolean> = _ideaComposeMode.asStateFlow()

    /** 计划面板是否可见 */
    private val _showPlanPanel = MutableStateFlow(false)
    val showPlanPanel: StateFlow<Boolean> = _showPlanPanel.asStateFlow()

    /** ═══════════ 今日锦囊（AI 生成，参考性，不写知识库） ═══════════ */

    private val _suggestion = MutableStateFlow<com.lovebrain.app.model.DailySuggestion?>(null)
    val suggestion: StateFlow<com.lovebrain.app.model.DailySuggestion?> = _suggestion.asStateFlow()

    /** S2-02: 某类前台任务是否在跑——一律从 coordinator 派生，不再有独立可写 boolean */
    private fun busyOf(
        type: ForegroundOperationCoordinator.OperationType
    ): StateFlow<Boolean> = operationCoordinator.activeOperations
        .map { ops -> ops.any { it.type == type } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    val isSuggesting: StateFlow<Boolean> =
        busyOf(ForegroundOperationCoordinator.OperationType.SUGGEST)

    private val _streamingTips = MutableStateFlow<List<com.lovebrain.app.model.SuggestTip>>(emptyList())
    val streamingTips: StateFlow<List<com.lovebrain.app.model.SuggestTip>> = _streamingTips.asStateFlow()

    /** ：锦囊错误提示（无 KB 引导/弱网超时/解析失败） */
    private val _suggestError = MutableStateFlow<String?>(null)
    val suggestError: StateFlow<String?> = _suggestError.asStateFlow()

    /** ═══════════ 主动发起/润色 ═══════════ */
    private val _proactiveOptions = MutableStateFlow<List<com.lovebrain.app.model.ProactiveOption>>(emptyList())
    val proactiveOptions: StateFlow<List<com.lovebrain.app.model.ProactiveOption>> = _proactiveOptions.asStateFlow()

    /** S2-02: 主动开场是否在生成——从 coordinator 派生。模式开关是 composerMode，不是这个 */
    val isProactive: StateFlow<Boolean> =
        busyOf(ForegroundOperationCoordinator.OperationType.PROACTIVE)

    private val _proactiveError = MutableStateFlow<String?>(null)
    val proactiveError: StateFlow<String?> = _proactiveError.asStateFlow()

    private var proactiveJob: kotlinx.coroutines.Job? = null

    /** P1-2：结果模式——显式区分回复结果与主动发结果 */
    enum class ResultMode { REPLY, PROACTIVE }
    private val _resultMode = MutableStateFlow(ResultMode.REPLY)
    val resultMode: StateFlow<ResultMode> = _resultMode.asStateFlow()

    /** S1-01: Composer mode——UI 会话状态，单一事实源。
     * REPLY = 普通回复模式（默认）
     * PROACTIVE = 主动发模式（蓝字切换进入，不发网络请求）
     * 替代之前分散的 local inputMode / resultMode / isProactive 三个变量各自推断。 */
    enum class ComposerMode { REPLY, PROACTIVE }
    private val _composerMode = MutableStateFlow(ComposerMode.REPLY)
    val composerMode: StateFlow<ComposerMode> = _composerMode.asStateFlow()

    /** S1-01: 切换主动发模式——第一次点击只切换模式，不发网络请求。
     * 再次点击退出主动发模式回到普通回复。 */
    fun toggleProactiveMode() {
        if (operationCoordinator.isBusy(ForegroundOperationCoordinator.OperationType.PROACTIVE) ||
            _replyUi.value.isBusy) return
        _composerMode.value = if (_composerMode.value == ComposerMode.PROACTIVE) ComposerMode.REPLY else ComposerMode.PROACTIVE
        // 退出主动发时清残留结果
        if (_composerMode.value == ComposerMode.REPLY) {
            _resultMode.value = ResultMode.REPLY
            _proactiveOptions.value = emptyList()
            _proactiveError.value = null
        }
    }

    /** S1-01: 退出主动发模式（供生成成功后或取消时调用） */
    fun exitProactiveMode() {
        _composerMode.value = ComposerMode.REPLY
        _resultMode.value = ResultMode.REPLY
    }

/** S2-02: 前台任务互斥——从 operationCoordinator 派生，不再拼多个 boolean */
val isForegroundBusy: Boolean get() = operationCoordinator.isForegroundBusy

    // ═══════════ F10: 仅看本轮开关 ═══════════

    /** F10: 仅看本轮开关——默认关闭。开启后只携带通用生成规则、本轮真实消息和本轮想法。
     * 排除旧画像、关系阶段、历史对话、场景、事项、经验与持续意图。
     * 开关属于当前工作轮次；本轮重生成保留，开启新轮次或切档案后恢复默认。 */
    private val _onlyThisRound = MutableStateFlow(false)
    val onlyThisRound: StateFlow<Boolean> = _onlyThisRound.asStateFlow()

    fun toggleOnlyThisRound() {
        _onlyThisRound.value = !_onlyThisRound.value
        // P1-F: 切换 onlyThisRound 后已有旧结果立即 stale
        checkInputChanged()
    }

    fun setOnlyThisRound(value: Boolean) {
        _onlyThisRound.value = value
        // P1-F: 切换 onlyThisRound 后已有旧结果立即 stale
        checkInputChanged()
    }

    // ═══════════ F11: 输入变化提示 + 生成历史与版本回退 ═══════════

    /**
     * F10/F11: 统一标记当前结果为 stale（如果输入已变化）。
     * 在所有消息操作（增/删/改/重排/草稿/反馈）后调用。
     * 仅当存在已完成的生成结果时才实际检测。 */
    private fun markCurrentResultStaleIfNeeded() {
        if (replyResult != null && replyGenerationContext != null) {
            checkInputChanged()
        }
    }

    /**
     * F11: 检测输入是否已变化——使用真正的输入指纹比较。
     * 指纹覆盖：KB identity、消息正文/角色/顺序、IDEA、onlyThisRound、intent revision。
     * 以下任意变化都令当前旧结果 stale：
     * - 修改消息正文
     * - HER ↔ ME
     * - HER/ME ↔ IDEA
     * - 调整顺序
     * - 增加/删除消息
     * - 修改本轮想法
     * - 切换 onlyThisRound
     * - 当前有效持续意图变化（revision）
     * - 切换 KB
     */
    fun checkInputChanged() {
        val ctx = replyGenerationContext ?: return
        // guard: 只在有成功结果时才需要检测 stale
        replyResult as? GenerateResult.Success ?: return
        // P0-3 fix: 使用当前活跃 intent revision，而非生成时冻结的 ctx.intentRevision。
        // ctx.intentRevision 是生成时的快照值，如果用户随后修改了 intent，
        // 用旧 revision 自己跟自己比较当然发现不了变化。
        val currentFingerprint = computeInputFingerprint(
            _messages.value,
            collectIdeaHintWithDraft(_messages.value),
            _activeKb.value?.name,
            _onlyThisRound.value,
            _intentConfig.value.revision
        )
        _inputChanged.value = currentFingerprint != ctx.inputFingerprint
    }

    /**
     * F11: 计算输入指纹——纯函数，对顺序敏感。
     * P1-8: 使用 exact canonical representation（length-prefixed encoding），
     * 不再依赖 Java 32-bit hashCode()。覆盖：KB identity、有序消息(id+role+exact content)、
     * IDEA exact content、onlyThisRound、intent revision。
     */
    private fun computeInputFingerprint(
        messages: List<ChatMessage>,
        ideaHint: String,
        kbName: String?,
        onlyThisRound: Boolean,
        intentRevision: Int
    ): String {
        val sb = StringBuilder()
        // P1-8: length-prefixed canonical encoding — 不使用 hashCode()
        appendLengthPrefixed(sb, "kb", kbName ?: "")
        appendLengthPrefixed(sb, "otr", onlyThisRound.toString())
        appendLengthPrefixed(sb, "irev", intentRevision.toString())
        appendLengthPrefixed(sb, "idea", ideaHint.trim())
        sb.append("msgs=")
        for (msg in messages) {
            appendLengthPrefixed(sb, "m", msg.id)
            sb.append(msg.role.name).append(",")
            appendLengthPrefixed(sb, "c", msg.content)
            sb.append(";")
        }
        // F11: SHA-256 指纹——对 exact canonical representation 做哈希
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    /** P1-8: length-prefixed append — 防止不同字符串产生相同 canonical representation */
    private fun appendLengthPrefixed(sb: StringBuilder, key: String, value: String) {
        sb.append(key).append("(").append(value.length).append("):").append(value).append(";")
    }

    /**
     * F11: 回退到上一轮生成结果。
     * 正确的 one-way undo 语义：
     *   history = [v1, v2, v3], current = v3
     *   rollback → 删除 current(v3), 恢复 previous(v2)
     *   history => [v1, v2]
     * 之后基于 v2 生成 v4：
     *   history => [v1, v2, v4]
     * 再次 rollback → 删除 v4, 恢复 v2
     * 绝不会重新出现已放弃的 v3。
     *
     * P0-RC: KB 边界——只有当前 result context 与 active KB 一致时才允许 rollback。
     *   用户已切到 B 时，不允许操作 A 的 version stack。
     * P0-RC: rollback 后不无条件 _inputChanged=false——调用 checkInputChanged()
     *   让当前真实输入与 previous.context.inputFingerprint 比较。
     *   如果用户当前输入与旧版本不同，stale 必须 true。
     */
    fun rollbackToPreviousGeneration() {
        // P0-RC: KB 边界——result context 必须与 active KB 一致
        val activeKbName = _activeKb.value?.name
        val contextKbName = replyGenerationContext?.kbName
        if (contextKbName == null || contextKbName != activeKbName) return

        // P0-RC: 只筛选当前 KB 的 snapshots
        val kbHistory = _generationHistory.value.filter { it.kbName == activeKbName }
        if (kbHistory.size < 2) return

        val currentSnapshot = kbHistory.last()
        val previous = kbHistory[kbHistory.size - 2]

        // P0-RC: 删除 current snapshot（不是 previous），恢复 previous
        _generationHistory.value = _generationHistory.value.filterNot { it.versionId == currentSnapshot.versionId }

        // P0-RC: 原子恢复 result + versionId + context
        replaceReplyResult(previous.result)
        _currentVersionId.value = previous.versionId
        replyGenerationContext = previous.context
        _feedbacks.value = emptyMap()
        _rewriteStates.value = emptyMap()
        _rewriteHistory.value = emptyMap()

        // P0-RC: 重新计算 stale——当前输入可能与 previous context 不一致
        checkInputChanged()

        // P0-D: 生成新的 roundId 值以触发 viewMode 重置——不递减，不使用 magic number
        _generationRoundId.value = _generationRoundId.value + 1
    }

    /** F11: 是否可以回退到上一版本 — P0-RC: 以当前 active KB 为权限边界 */
    val canRollbackGeneration: Boolean
        get() {
            // P0-RC: result context 必须与 active KB 一致
            val activeKbName = _activeKb.value?.name
            val contextKbName = replyGenerationContext?.kbName
            if (contextKbName == null || contextKbName != activeKbName) return false
            return _generationHistory.value.count { it.kbName == activeKbName } >= 2
        }

    // ═══════════ 谈心模式 ═══════════
    private val _counselingResult = MutableStateFlow<String?>(null)
    val counselingResult: StateFlow<String?> = _counselingResult.asStateFlow()

    private val _counselingError = MutableStateFlow<String?>(null)
    val counselingError: StateFlow<String?> = _counselingError.asStateFlow()

    /** S2-02: 谈心是否在生成——从 coordinator 派生 */
    val isCounseling: StateFlow<Boolean> =
        busyOf(ForegroundOperationCoordinator.OperationType.COUNSELING)

    private val _counselingStreaming = MutableStateFlow("")
    val counselingStreaming: StateFlow<String> = _counselingStreaming.asStateFlow()

    // P3-04: 谈心流式节流已改由 applyCounselingEvent 的合并缓冲承担（见 S2-03 归约段）

    init {
        // F10: 确保至少有一个合法知识库（首次启动创建默认库，重复启动沿用，中断恢复补齐）
        viewModelScope.launch {
            try {
                knowledgeRepo.ensureInitialKnowledgeBase()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.w("ensureInitialKnowledgeBase failed: ${e::class.simpleName}")
            }
            refreshKnowledgeBases()
        }
        val persisted = promptBuilder.validateConfig(securePrefs.thinkingMode, securePrefs.outputMode)
        if (!persisted.isValid) {
            persisted.warnings.forEach { L.w("⚠️ 启动配置校验：$it") }
            _outputMode.value = persisted.outputMode
            securePrefs.thinkingMode = persisted.thinkingMode
            securePrefs.outputMode = persisted.outputMode
        }

        // ：今日花费载入（跨天清零）+ 订阅计费事件流（ 口径）
        val savedCost = securePrefs.loadTodayCost()
        _todayCostYuan.value = rollTodayCost(savedCost?.first, savedCost?.second, todayCostDate)
        // F12: 加载累计统计
        _totalGenerateCount.value = securePrefs.totalGenerateCount
        _totalCostYuan.value = securePrefs.totalCostYuan
        _totalCopyCount.value = securePrefs.totalCopyCount
        _totalAdoptCount.value = securePrefs.totalAdoptCount
        _totalRewriteCount.value = securePrefs.totalRewriteCount
        viewModelScope.launch {
            // SharedFlow 收集不应崩面板
            try {
                deepSeekRepo.costEvents.collect { ev ->
                    val today = java.time.LocalDate.now().toString()
                    if (today != todayCostDate) { todayCostDate = today; _todayCostYuan.value = 0.0 }
                    // PROV-03：今日累计包含全部可计费 AI 请求（前台 + 后台）
                    _todayCostYuan.value += ev.yuan
                    securePrefs.saveTodayCost(today, _todayCostYuan.value)
                    // F12: 累计总花费
                    _totalCostYuan.value += ev.yuan
                    securePrefs.totalCostYuan = _totalCostYuan.value
                    // PROV-03：本次费用只显示前台流式请求（FOREGROUND），不被后台 raw 污染
                    if (ev.scope == CostScope.FOREGROUND) {
                        _lastCostYuan.value = ev.yuan
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.w("计费事件收集异常：${e.javaClass.simpleName}")
            }
        }

        restoreState()
        
        // : 从 SecurePrefs 读取激活工单信息（面板每次可见时经 refreshTicketState 再刷新）
        refreshTicketState()
    }

    // ═══════════ UI 状态 setters ═══════════

    fun setPanelState(state: PanelState) { _panelState.value = state }
    fun setDraft(text: String) {
        _draftText.value = text
        // F10/F11: 草稿变化后标记旧结果为 stale（草稿参与 buildMessageSnapshot）
        markCurrentResultStaleIfNeeded()
    }
    fun setCounselingDraft(text: String) {
        _counselingDraft.value = text
        //  防抖：连击只落盘最后一次（行为差异：强杀最多丢 ≤600ms 输入；dispose 显式 flush 兜底）
        draftPersistJob?.cancel()
        draftPersistJob = viewModelScope.launch {
            delay(COUNSELING_DRAFT_DEBOUNCE_MS)
            securePrefs.counselingDraft = text
        }
    }
    fun setPanelMode(mode: Int) { _panelMode.value = mode; securePrefs.panelMode = mode }
    //  捕获收口：FloatingService 捕获链以 currentRole 落消息角色（本文件外零触碰），
    // _currentRole 恒 ∈ {HER, ME}——选《想法》只进入 ideaComposeMode，不落 _currentRole，真实聊天捕获永不误标 IDEA
    fun setCurrentRole(role: ChatMessage.Role) {
        if (role == ChatMessage.Role.IDEA) {
            _ideaComposeMode.value = true
        } else {
            _ideaComposeMode.value = false
            _currentRole.value = role
        }
    }
    fun setEditingIndex(index: Int) { _editingIndex.value = index }

    // ═══════════ 消息管理 ═══════════

    fun addMessage(role: ChatMessage.Role, content: String) {
        if (content.isBlank()) return
        _messages.value = _messages.value + ChatMessage(role = role, content = content.trim())
        // F10/F11: 消息变更后标记旧结果为 stale
        markCurrentResultStaleIfNeeded()
    }

    fun updateMessage(index: Int, role: ChatMessage.Role, content: String) {
        if (content.isBlank()) return
        val list = _messages.value.toMutableList()
        if (index !in list.indices) return
        list[index] = ChatMessage(id = list[index].id, role = role, content = content.trim())
        _messages.value = list
        // F10/F11: 消息变更后标记旧结果为 stale
        markCurrentResultStaleIfNeeded()
    }

    fun removeMessage(index: Int) {
        _messages.value = _messages.value.filterIndexed { i, _ -> i != index }
        // F10/F11: 消息变更后标记旧结果为 stale
        markCurrentResultStaleIfNeeded()
    }

    /**
     * ：按消息 id 删除（动画延迟回调里 index 会过期，id 是 data class 稳定值）。
     * ：editingIndex 修正下沉至 VM——VM 持数据真源，同帧连删串行执行永远看最新快照，
     * 消除 UI 侧依赖 composition 旧快照各自算 index 必错位的竞态。
     */
    fun removeMessageById(id: String) {
        val list = _messages.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val editing = _editingIndex.value
        if (editing >= 0) {
            if (index < editing) {
                _editingIndex.value = editing - 1
            } else if (index == editing) {
                _editingIndex.value = -1
                _draftText.value = ""
            }
        }
        _messages.value = list.filterNot { it.id == id }
        // F10/F11: 消息变更后标记旧结果为 stale
        markCurrentResultStaleIfNeeded()
    }

    /**
     * ：拖拽重排同步修正 editingIndex——与 removeMessageById 的  同源：
     * 修正下沉 VM（VM 持数据真源），不许 UI 侧依赖 composition 旧快照各自算 index。
     * 推演基于 removeAt(from)+add(to, item) 后的真实位置（editing 为搬移前下标）：
     * - 拖的就是编辑中消息（from == editing）→ 跟随到 to；
     * - 向下拖且越过编辑位（from < editing <= to）→ 编辑位被挤前一位，-1；
     * - 向上拖且越过编辑位（to <= editing < from）→ 编辑位被推后一位，+1。
     * 反例一（验证下边界含等号）：[A,B,C]，编辑 B（editing=1），A 拖到 C 之后
     * （from=0,to=2）→ [B,C,A]，B 落 0：1 in (0,2] → -1=0 正确；若写成 to > editing
     * 会漏掉 to==editing 的跨越（如 [A,edit,C] from=0,to=1 → [A,C,edit] 应 -1）。
     * 反例二（验证上边界含等号）：[X,edit,A,B]，editing=1，B 拖到 X 之后
     * （from=3,to=1）→ [X,B,edit,A]，edit 落 2：1 in [1,3) → +1=2 正确；若写成
     * to < editing 会漏掉 to==editing 的插入（item 插在编辑位前同样把编辑位后推）。
     */
    fun reorderMessages(from: Int, to: Int) {
        if (from == to) return
        val list = _messages.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val editing = _editingIndex.value
        if (editing >= 0) {
            _editingIndex.value = when {
                from == editing -> to
                from < editing && to >= editing -> editing - 1
                from > editing && to <= editing -> editing + 1
                else -> editing
            }
        }
        val item = list.removeAt(from)
        list.add(to, item)
        _messages.value = list
        // F10/F11: 消息变更后标记旧结果为 stale
        markCurrentResultStaleIfNeeded()
    }

    // ═══════════ 状态持久化 ═══════════

    private fun restoreState() {
        securePrefs.panelMode.let { if (it in 0..1) _panelMode.value = it }
        securePrefs.loadCounselingResult()?.let { if (it.isNotBlank()) _counselingResult.value = it }
        securePrefs.counselingDraft.takeIf { it.isNotEmpty() }?.let { _counselingDraft.value = it }
        // ：今日锦囊仅当天恢复（隔天不恢复旧锦囊）；消息/想法已改纯内存，杀进程即清
        securePrefs.loadSuggestion()?.let { (json, date) ->
            if (date == TimeFmt.today()) {
                runCatching {
                    Json.decodeFromString(serializer<com.lovebrain.app.model.DailySuggestion>(), json)
                }.onSuccess { _suggestion.value = it }
                    .onFailure { L.w("恢复今日锦囊失败：${it.javaClass.simpleName}") }
            }
        }
    }

    // ═══════════ 唯一消息快照构建入口 ═══════════

    /**
     * 构建本轮生成的冻结消息快照。
     * 供 chat、想法、来源映射及保存共用，确保所有路径使用同一份不可变快照。
     *
     * - 深拷贝当前消息列表，防止外部修改影响快照。
     * - 应用未提交的编辑草稿（角色 + 内容），防双身份问题（F08）。
     * - 按稳定消息ID操作，不依赖可能移动的下标。
     */
    private fun buildMessageSnapshot(): List<ChatMessage> {
        val messages = _messages.value.map { it.copy() }
        val editingDraft = _draftText.value.trim()
        val editingIdx = _editingIndex.value

        if (editingDraft.isNotBlank() && editingIdx in messages.indices) {
            val targetId = messages[editingIdx].id
            val editingRole = if (_ideaComposeMode.value) ChatMessage.Role.IDEA else _currentRole.value
            return messages.mapIndexed { idx, msg ->
                if (idx == editingIdx && msg.id == targetId) {
                    msg.copy(role = editingRole, content = editingDraft)
                } else {
                    msg
                }
            }
        }
        return messages
    }

    // ═══════════ 流式生成（委托 GenerationEngine） ═══════════

    /**
     * S2-01/S2-02/S2-03: 生成回复。
     *
     * 一个请求 = coordinator 注册的**一个**任务，覆盖"准备 → 网络 → 解析 → 发布"全程。
     * 旧写法是 ViewModel 先 launch 一个 prepJob，再把 scope 交给 Engine 让它 launch 第二个 Job，
     * 两个 owner 并存；停止时只能一次取消三类操作，误伤并行的改写和锦囊。
     *
     * 被互斥拒绝时 [ForegroundOperationCoordinator.start] 返回 null 且任务从未启动，
     * 因此这里不需要"先起再回滚"，也不会有不受管理的前台任务。
     */
    fun generate() {
        val snapshot = buildMessageSnapshot()
        // 生产前置条件：没有真实对话（HER/ME）不发起请求
        if (snapshot.none { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }) return

        // P1-2：设置结果模式
        _resultMode.value = ResultMode.REPLY

        val requestId = ReplyRequestState.newRequestId()
        val userHint = collectIdeaHintWithDraft(snapshot)

        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.REPLY,
            requestId
        ) { runReplyRequest(requestId, snapshot, userHint) }

        if (lease == null) {
            L.w("generate rejected: another foreground operation owns the slot")
        }
    }

    /**
     * 一次回复请求的完整生命周期——由 coordinator 拥有。
     *
     * 准备阶段的读盘/组 prompt 与流式收集都在这个协程里，所以取消它就等于取消整条链。
     */
    private suspend fun runReplyRequest(
        requestId: String,
        snapshot: List<ChatMessage>,
        userHint: String
    ) {
        // 先认领请求身份：reducer 只有收到 ReplyRequested 才会换 owner
        dispatchReply(ReplyRequested(requestId))
        try {
            currentCoroutineContext().ensureActive()
            val kbSnapshot = _activeKb.value
            val kbName = kbSnapshot?.name

            val intentSnapshot = kbName?.let { name ->
                try {
                    withContext(Dispatchers.IO) { knowledgeRepo.readIntent(name) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    L.w("readIntent failed: ${e.message}")
                    null
                }
            } ?: com.lovebrain.app.model.IntentConfig()

            val effectiveIntent = if (intentSnapshot.enabled &&
                intentSnapshot.status == com.lovebrain.app.model.IntentStatus.ACTIVE) {
                val today = com.lovebrain.app.util.TimeFmt.today()
                val shouldExpire = when (intentSnapshot.expiry) {
                    com.lovebrain.app.model.IntentExpiry.TODAY ->
                        intentSnapshot.expiryDate.isNotBlank() && intentSnapshot.expiryDate < today
                    com.lovebrain.app.model.IntentExpiry.DATE ->
                        intentSnapshot.expiryDate.isNotBlank() && intentSnapshot.expiryDate < today
                    com.lovebrain.app.model.IntentExpiry.UNTIL_DONE -> false
                }
                if (shouldExpire) intentSnapshot.copy(status = com.lovebrain.app.model.IntentStatus.EXPIRED)
                else intentSnapshot
            } else intentSnapshot

            val (correctionsSnapshot, correctionsRevision) = kbName?.let { name ->
                try {
                    withContext(Dispatchers.IO) { knowledgeRepo.readCorrectionsAndRevision(name) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    L.w("readCorrectionsAndRevision failed: ${e.message}")
                    null
                }
            } ?: (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)
            val mergedCorrections = correctionsSnapshot.toMutableMap().also { it.putAll(roundCorrections) }

            currentCoroutineContext().ensureActive()

            // S2-01: 构建不可变 GenerationInput——冻结本轮全部输入，
            // 含 KB 内容修订、画像正文、Provider 完整非敏感身份与 prompt 资产 hash
            val aggressive = _outputMode.value == 1
            val providerConfig = deepSeekRepo.snapshotProviderConfig()
            val input = buildGenerationInput(
                requestId = requestId,
                messages = snapshot,
                userHint = userHint,
                knowledgeBase = kbSnapshot,
                intentConfig = effectiveIntent,
                corrections = mergedCorrections,
                correctionsRevision = correctionsRevision,
                onlyThisRound = _onlyThisRound.value,
                aggressive = aggressive,
                providerIdentity = providerConfig?.toIdentity(),
                kbProfile = kbName?.let { runCatching { knowledgeRepo.readProfile(it) }.getOrDefault("") } ?: "",
                kbRevision = kbName?.let { runCatching { knowledgeRepo.contentRevision(it) }.getOrDefault("") } ?: "",
                promptAssetHash = promptBuilder.replyPromptAssetHash()
            )

            replyGenerationContext = ReplyGenerationContext(
                messages = snapshot,
                messageIds = snapshot.mapTo(mutableSetOf()) { it.id },
                kbName = kbName,
                ideaHint = userHint,
                intentText = effectiveIntent.text,
                intentEnabled = effectiveIntent.enabled,
                intentRevision = effectiveIntent.revision,
                correctionsRevision = correctionsRevision,
                inputFingerprint = computeInputFingerprint(
                    snapshot, userHint, kbName, _onlyThisRound.value, effectiveIntent.revision
                ),
                onlyThisRound = _onlyThisRound.value
            )

            // S2-03: Engine 只暴露事件流，本协程是它唯一的订阅者
            generationEngine.replyStream(input).collect { dispatchReply(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            dispatchReply(ReplyStopped(requestId))
            throw e
        } catch (e: Exception) {
            // S1-02: 准备阶段异常转为可恢复错误，不泄露路径/Provider/内部细节
            L.e("generate request failed", e)
            dispatchReply(
                ReplyCompleted(
                    requestId,
                    GenerateResult.Error(ReplyFailureKind.fromException(e).userMessage)
                )
            )
        }
    }

    // ═══════════ S2-03: 回复状态唯一写入口 ═══════════

    /** 待合并的流式增量所属请求 */
    private var pendingChunkRequestId: String? = null

    /**
     * S2-03: 回复状态的唯一写入口。
     *
     * - 身份不符的事件在这里被 reducer 拒掉，返回同一个状态对象，
     *   于是被取代的旧请求连"顺带写个流式文本"都做不到。
     * - 相邻的流式增量合并成一个事件再归约：P3-04 要的节流和"唯一入口"不再互相牺牲。
     *
     * 只在 viewModelScope（Main 分发器）上被调用——Engine 的事件流在主协程收集，
     * 所以合并缓冲是单线程访问，不需要额外加锁。
     */
    internal fun dispatchReply(event: ReplyEvent) {
        if (event is ReplyChunk) {
            if (pendingChunkRequestId != null && pendingChunkRequestId != event.requestId) {
                flushPendingChunk()
            }
            pendingChunkRequestId = event.requestId
            pendingChunk.append(event.text)
            scheduleChunkFlush()
            return
        }
        flushPendingChunk()
        applyReplyEvent(event)
    }

    private fun scheduleChunkFlush() {
        if (chunkFlushJob?.isActive == true) return
        chunkFlushJob = viewModelScope.launch {
            while (true) {
                delay(STREAMING_FLUSH_INTERVAL_MS)
                flushPendingChunk()
            }
        }
    }

    private fun flushPendingChunk() {
        val requestId = pendingChunkRequestId ?: return
        if (pendingChunk.isEmpty()) return
        val text = pendingChunk.toString()
        pendingChunk.setLength(0)
        applyReplyEvent(ReplyChunk(requestId, text))
    }

    /** 放弃未发布的增量（停止/换请求时） */
    private fun cancelPendingChunkFlush() {
        chunkFlushJob?.cancel()
        chunkFlushJob = null
        pendingChunkRequestId = null
        pendingChunk.setLength(0)
    }

    private fun applyReplyEvent(event: ReplyEvent) {
        val before = _replyUi.value
        val after = ReplyReducer.reduce(before, event)
        if (after === before) {
            L.w("S2-03: reply event ${event::class.simpleName} rejected (stale requestId ${event.requestId.take(8)})")
            return
        }
        _replyUi.value = after
        // 面板外壳状态：回复相关的迁移只由 reducer 结果驱动
        if (after.panelState != _panelState.value) _panelState.value = after.panelState
        onReplyUiCommitted(before, after)
    }

    /**
     * reducer 落定后的外部副作用（持久化、历史版本、耗时上报）。
     *
     * 只有归约被接受（状态对象换了）才执行，所以迟到的旧请求
     * 不可能递增 roundId、不可能写历史、也不可能改计数。
     */
    private fun onReplyUiCommitted(before: ReplyUiState, after: ReplyUiState) {
        if (after.result !== before.result && after.result is GenerateResult.Success) {
            _generationRoundId.value++
            val versionId = GenerationVersionId.next()
            _currentVersionId.value = versionId
            replyGenerationContext?.let { ctx ->
                val snapshot = GenerationSnapshot(
                    versionId = versionId,
                    result = after.result as GenerateResult.Success,
                    context = ctx.copy(
                        memoryRefs = after.memoryRefs,
                        sourceAliasMap = after.sourceAliasMap
                    ),
                    kbName = ctx.kbName
                )
                // P0-1: 限制 session history 最多 20 条
                _generationHistory.value = (_generationHistory.value + snapshot).takeLast(MAX_HISTORY_SIZE)
            }
            // F11: 生成成功后重置输入变化标记
            _inputChanged.value = false
            // F12: 递增累计生成次数
            _totalGenerateCount.value += 1
            securePrefs.totalGenerateCount = _totalGenerateCount.value
        }
        if (after.firstReplyMs > 0L && after.firstReplyMs != before.firstReplyMs) {
            _firstReplyMs.value = after.firstReplyMs
        }
        if (after.firstTokenMs > 0L && after.firstTokenMs != before.firstTokenMs) {
            _lastResponseMs.value = after.firstTokenMs
        }
    }

    /**
     * GEN-01/GEN-02 / S2-02：停止生成——只取消当前 REPLY owner。
     *
     * 旧实现在这里一次 stopByType(REPLY/PROACTIVE/REWRITE)，
     * 把并行的主动发和改写一起杀掉，破坏了 owner 隔离；
     * 主动发与改写各有自己的停止入口（[stopProactive] / [cancelRewrite]）。
     */
    fun stopGeneration() {
        val current = operationCoordinator.current(
            ForegroundOperationCoordinator.OperationType.REPLY
        )
        // 先取消任务，再落 Idle——避免任务在 Idle 之后又写入状态
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.REPLY)
        cancelPendingChunkFlush()
        current?.let { applyReplyEvent(ReplyStopped(it.requestId)) }
        // GEN-02：停止生成时清 context（本轮无成功结果），但消息本身不删
        replyGenerationContext = null
        // F04/P1-RC: stopGeneration 不清 roundCorrections——
        // 停止生成不等于结束当前工作轮。用户 mute → stop → retry 时，
        // 本轮 mute 应继续有效。roundCorrections 只在 nextRound / switch KB 时清。
        _inputChanged.value = false
    }

    // ═══════════ 赞踩反馈 ═══════════

    /** P0-1: setFeedback 使用 identityKey 区分 STYLE/DIRECTION
     * F02: 点踩时立即落本地反馈案例，不因点踩调用 AI
     * P1-A: 在调用时同步冻结快照，构造 case 并通过 currentFeedbackCase 暴露给 UI——消除"保存后再全量查询"竞态
     * F02-fix: 先计算 effectiveFeedback（toggle 后的实际值），再据此决定是否建/清 case。
     *  旧代码用传入参数 feedback 判断，第二次点踩取消时仍创建 case。 */
    fun setFeedback(identityKey: String, feedback: SchemeFeedback) {
        // P1-A: 同步冻结快照——防止异步保存期间 result/context 被清空
        val resultSnapshot = replyResult as? GenerateResult.Success
        val ctxSnapshot = replyGenerationContext
        val modelId = _activeTicket.value?.model ?: ""

        // F02-fix: 先算 toggle 后的实际值
        val effectiveFeedback = if (_feedbacks.value[identityKey] == feedback) SchemeFeedback.NONE else feedback

        _feedbacks.value = _feedbacks.value.toMutableMap().apply {
            put(identityKey, effectiveFeedback)
        }
        // P2-14: 删除 markCurrentResultStaleIfNeeded()——点赞/点踩不是 GenerationInput，
        // fingerprint 不包含 feedback，调用它是概念错误。
        // F02/P1-A: 使用 effectiveFeedback 决定行为——取消踩时 effectiveFeedback=NONE 不建 case
        if (effectiveFeedback == SchemeFeedback.DISLIKED && resultSnapshot != null && ctxSnapshot != null) {
            saveFeedbackCase(identityKey, resultSnapshot, ctxSnapshot, modelId)
        } else {
            // 非踩或取消踩时清除当前 case
            _currentFeedbackCase.value = null
        }
    }

    /** F02/P1-A: 点踩时保存最小反馈案例——使用调用方传入的冻结快照，不读实时状态。
     *  同步构造 case 并暴露给 _currentFeedbackCase，UI 直接消费，不需读全库猜最后一条 */
    private fun saveFeedbackCase(
        identityKey: String,
        result: GenerateResult.Success,
        ctx: ReplyGenerationContext,
        modelId: String
    ) {
        val repo = feedbackCaseRepository
        val response = result.response
        val identity = com.lovebrain.app.model.SchemeIdentity.fromKey(identityKey) ?: return
        val allSchemes = when (identity.source) {
            com.lovebrain.app.model.SchemeSource.STYLE -> response.schemes
            com.lovebrain.app.model.SchemeSource.DIRECTION -> response.directionSchemes
        }
        val scheme = allSchemes.find { it.tag == identity.tag } ?: return

        // F16: 冻结真实对话快照（含人物身份）
        val dialogueSnapshot = ctx.messages
            .filter { it.role == com.lovebrain.app.model.ChatMessage.Role.HER || it.role == com.lovebrain.app.model.ChatMessage.Role.ME }
            .map { msg ->
                com.lovebrain.app.model.DialogueSnapshotEntry(
                    speaker = if (msg.role == com.lovebrain.app.model.ChatMessage.Role.HER) "PARTNER" else "USER",
                    text = msg.content
                )
            }

        val case = com.lovebrain.app.model.FeedbackCase(
            caseId = java.util.UUID.randomUUID().toString(),
            schemeIdentityKey = identityKey,
            candidateReply = scheme.reply,
            categories = emptyList(),
            reasons = emptyList(),
            kbName = ctx.kbName ?: "",
            ideaHint = ctx.ideaHint,
            intentText = ctx.intentText.takeIf { ctx.intentEnabled } ?: "",
            modelId = modelId,
            timestamp = com.lovebrain.app.util.TimeFmt.now(),
            // F16: 完整诊断快照
            dialogueSnapshot = dialogueSnapshot,
            contextMode = if (ctx.onlyThisRound) "only-this-round" else "full",
            promptVersion = "v1.3.2",
            appVersion = com.lovebrain.app.BuildConfig.VERSION_NAME,
            buildType = com.lovebrain.app.BuildConfig.BUILD_TYPE,
            promptTokens = 0,  // F16: usage 不可用时默认 0（不把默认 0 当已核实成本）
            completionTokens = 0,
            costYuan = _lastCostYuan.value ?: 0.0
        )
        // P1-A: 同步暴露给 UI——消除竞态，UI 不需要异步全库读取
        _currentFeedbackCase.value = case
        if (repo != null) {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.save(case)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    L.w("saveFeedbackCase failed: ${e::class.simpleName}")
                }
            }
        }
    }

    /** F02: 更新反馈案例的分类、原因和补充说明 */
    fun updateFeedbackCase(
        caseId: String,
        categories: List<com.lovebrain.app.model.FeedbackCategory>,
        reasons: List<String>,
        userNote: String = "",
        betterVersion: String = ""
    ) {
        val repo = feedbackCaseRepository ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val all = repo.getAll()
                    val existing = all.find { it.caseId == caseId } ?: return@withContext
                    repo.save(
                        existing.copy(
                            categories = categories,
                            reasons = reasons,
                            userNote = userNote,
                            betterVersion = betterVersion
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.w("updateFeedbackCase failed: ${e::class.simpleName}")
            }
        }
    }

    /** F02: 获取全部反馈案例 */
    fun loadFeedbackCases(
        onResult: (List<com.lovebrain.app.model.FeedbackCase>) -> Unit
    ) {
        val repo = feedbackCaseRepository ?: run { onResult(emptyList()); return }
        viewModelScope.launch {
            val cases = withContext(Dispatchers.IO) { repo.getAll() }
            onResult(cases)
        }
    }

    /** F02/P1-A: 清除当前反馈案例（UI dismiss 时调用） */
    fun dismissFeedbackCase() {
        _currentFeedbackCase.value = null
    }

    /** F02: 导出反馈案例为 Markdown */
    fun exportFeedbackMarkdown(
        cases: List<com.lovebrain.app.model.FeedbackCase>,
        onResult: (String) -> Unit
    ) {
        val repo = feedbackCaseRepository ?: run { onResult(""); return }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { repo.exportMarkdown(cases) }
            onResult(text)
        }
    }

    /** F02: 导出反馈案例为 JSON */
    fun exportFeedbackJson(
        cases: List<com.lovebrain.app.model.FeedbackCase>,
        onResult: (String) -> Unit
    ) {
        val repo = feedbackCaseRepository ?: run { onResult(""); return }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { repo.exportJson(cases) }
            onResult(text)
        }
    }

    // ═══════════ 下一轮（存 KB + 清空） ═══════════

    private var recordingRound = false

    /**
     * GEN-03：提交顺序改为「先写盘成功 → 再提交 UI」。
     * 写盘失败时保留所有本轮数据（消息/结果/反馈/context），用户可重试。
     * GEN-02：保存时使用 replyGenerationContext 中的快照消息和 KB 名，不用实时 _messages/_activeKb。
     */
    fun nextRound() {
        val response = (replyResult as? GenerateResult.Success)?.response ?: return
        if (recordingRound) return

        // GEN-02：使用生成时绑定的 context，不用实时状态
        val context = replyGenerationContext ?: return

        recordingRound = true

        val kbName = context.kbName

        // P0-1: 使用 identity.key 查找反馈——STYLE 和 DIRECTION 互不干扰
        val likedStyleSchemes = response.schemes
            .filter { _feedbacks.value[it.identity.key] == SchemeFeedback.LIKED }
            .sortedBy { "ABCD".indexOf(it.tag) }

        // 方向回复的点赞也要保存——赞 F 真正保存 F 回复
        val likedDirectionSchemes = response.directionSchemes
            .filter { _feedbacks.value[it.identity.key] == SchemeFeedback.LIKED }

        val likedSchemes = likedStyleSchemes + likedDirectionSchemes

        // P0-2：点赞不等于发送。selectedScheme 恒为 null——
        // 用户没有"确认发送"操作，点赞只保存为偏好，不写入"实际对话"段。
        // TopicRecorder.record 收到 scheme=null 时不写"我（最终回复：…）"行。
        val selectedScheme: Scheme? = null
        val likedForRecording = likedSchemes // 保留全部点赞用于偏好记录

        // GEN-03：无 KB 时保持当前产品语义（可结束但提示未记入）
        if (kbName == null) {
            commitReplyRound(context.messageIds)
            showPanelWarning("未激活知识库，本轮对话未记入")
            replyGenerationContext = null
            recordingRound = false
            return
        }

        // P0-1：selectedScheme 可以为 null——用户没有点赞也不默认选 A。
        // 仍允许保存本轮输入消息（不依赖选中候选）。
        // 如果用户没有确认发送，提示"已保存对话，候选未作为已发送消息记录"。

        // GEN-03：先写盘，成功后才提交 UI
        val analysis = response.analysis
        val messagesSnapshot = context.messages
        val consumedIds = context.messageIds

        viewModelScope.launch {
            try {
                // GEN-02：按 context.kbName 查找 KB（生成时的 KB，非当前激活 KB）
                val kb = withContext(Dispatchers.IO) {
                    knowledgeRepo.listAll().firstOrNull { it.name == kbName }
                }
                if (kb == null) {
                    showPanelWarning("本轮保存失败：知识库已被删除，内容已保留，请重试")
                    return@launch
                }

                val t7Start = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    val topicRotated = topicRecorder.record(
                        kb, messagesSnapshot, selectedScheme, analysis.topic_status, analysis.topic_label,
                        analysis.scene_facts, "", analysis.ongoing,
                        likedSchemes = likedForRecording,
                        sourceAliasMap = context.sourceAliasMap
                    )
                    if (topicRotated) {
                        triggerCoordinator.checkTriggers(kb.name, viewModelScope, this@LoveBrainViewModel)
                    }
                }
                L.w("PERF t7 kb write done (${System.currentTimeMillis() - t7Start}ms)")

                // GEN-03：写盘成功 → 才提交 UI 状态
                commitReplyRound(consumedIds)
                replyGenerationContext = null
                // F04: 清空本轮瞬时纠正——新轮次不再受上一轮 THIS_ROUND mute 影响
                roundCorrections.clear()
                if (likedForRecording.isEmpty()) {
                    showPanelWarning("已保存对话，候选未作为已发送消息记录")
                } else {
                    showPanelWarning("已保存对话和偏好，候选未作为已发送消息记录")
                }
                refreshKnowledgeBases()
            } catch (t: Throwable) {
                L.e("nextRound record failed", t)
                // GEN-03：写盘失败 → 不清 messages/result/feedback/context，用户可重试
                showPanelWarning("本轮保存失败，内容已保留，请重试")
            } finally {
                recordingRound = false
            }
        }
    }

    /**
     * GEN-03：提交本轮 UI 状态 — 只删除本轮 snapshot 对应的消息（按 ID），不盲目清空全部。
     * 同时修正 editingIndex/draftText 防止指向已删除的位置。
     */
    private fun commitReplyRound(consumedMessageIds: Set<String>) {
        val oldList = _messages.value
        val newList = oldList.filterNot { it.id in consumedMessageIds }

        // 修正 editingIndex：如果编辑中的消息属于 consumedIds，清编辑态
        val editing = _editingIndex.value
        if (editing >= 0 && editing < oldList.size) {
            val editingMsg = oldList[editing]
            if (editingMsg.id in consumedMessageIds) {
                _editingIndex.value = -1
                _draftText.value = ""
            } else {
                // 编辑的消息不在 consumed 中，重算新 index
                val newIdx = newList.indexOfFirst { it.id == editingMsg.id }
                _editingIndex.value = newIdx
            }
        }

        _messages.value = newList
        _feedbacks.value = emptyMap()
        // S2-03: 结果/流式态的清空也走 reducer，不再各自写四个 StateFlow
        applyReplyEvent(ReplyCleared(_replyUi.value.ownerRequestId ?: ""))
        // 新轮次开始时清理改写状态和历史，作废旧改写请求
        _rewriteStates.value = emptyMap()
        _rewriteHistory.value = emptyMap()
        rewriteRequestId = null
        rewriteContextId = null
        // F10: 新轮次恢复仅看本轮开关为默认关闭
        _onlyThisRound.value = false
    }

    fun copyScheme(scheme: Scheme): String {
        // F12: 统计复制次数
        _totalCopyCount.value += 1
        securePrefs.totalCopyCount = _totalCopyCount.value
        return scheme.reply
    }

    // ═══════════ F03: 记录实际发送的版本 ═══════════

    /**
     * F03/P1-B: 记录用户确认已发送的版本。
     *
     * 用户自行确认发送，不代表应用检测到了发送行为。
     * 确认后写为"我"的真实消息，保存用户确认来源、关联候选版本（若有）、时间。
     * 同一轮只能有一份当前最终发送记录；同一 generation version 再次确认更新替换。
     *
     * P0-2: 删除同步返回 ActualSentResult.RECORDED——RECORDED 只在 Repository 确认写盘后产生。
     * 异步结果通过 actualSentState StateFlow 通知 UI。
     * P0-3: 不自动绑定第一张卡——linkedSchemeIdentityKey 必须由调用方明确传入，
     * 不再从 displaySchemes 自动推断。
     * P0-4: 同一 generationVersionId 的记录使用 upsert（替换），不 append 多条。
     */
    /** F03: 实际发送记录状态——异步写盘的真实 typed result */
    enum class ActualSentState { IDLE, RECORDED, KB_NOT_FOUND, NO_KB, IO_ERROR }
    private val _actualSentState = MutableStateFlow(ActualSentState.IDLE)
    val actualSentState: StateFlow<ActualSentState> = _actualSentState.asStateFlow()
    fun dismissActualSentState() { _actualSentState.value = ActualSentState.IDLE }

    fun recordActualSentMessage(
        sentText: String,
        linkedSchemeIdentityKey: String? = null
    ) {
        if (sentText.isBlank()) {
            _actualSentState.value = ActualSentState.IO_ERROR
            return
        }
        val ctx = replyGenerationContext ?: run {
            _actualSentState.value = ActualSentState.IO_ERROR
            return
        }
        val kbName = ctx.kbName ?: run {
            showPanelWarning("未激活知识库，无法记录已发送消息")
            _actualSentState.value = ActualSentState.NO_KB
            return
        }

        // P1-B: 冻结候选版本快照——绑定版本 ID 和候选正文
        val versionId = _currentVersionId.value
        val candidateReply = linkedSchemeIdentityKey?.let { key ->
            val result = replyResult as? GenerateResult.Success
            val identity = com.lovebrain.app.model.SchemeIdentity.fromKey(key)
            if (result != null && identity != null) {
                val allSchemes = when (identity.source) {
                    com.lovebrain.app.model.SchemeSource.STYLE -> result.response.schemes
                    com.lovebrain.app.model.SchemeSource.DIRECTION -> result.response.directionSchemes
                }
                allSchemes.find { it.tag == identity.tag }?.reply
            } else null
        }

        // P0-4: upsert by generationVersionId——同一 generation version 再次确认时替换旧记录
        val time = com.lovebrain.app.util.TimeFmt.now()
        val sentEntry = buildString {
            append("<!-- sent:").append(time)
                .append(" linked:").append(linkedSchemeIdentityKey ?: "null")
                .append(" version:").append(versionId?.value ?: "null")
                .append(" candidate:").append(candidateReply?.let { java.net.URLEncoder.encode(it, "UTF-8").take(200) } ?: "null")
                .append(" -->\n")
            append("我（确认已发送）：").append(sentText.trim()).append("\n")
        }
        // P0-4: 检查是否已有同一 generationVersionId 的记录
        val existingEntry = _actualSentEntries[versionId?.value]
        val isUpdate = existingEntry != null
        _actualSentState.value = ActualSentState.IDLE
        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    if (isUpdate) {
                        // 替换旧记录——先删旧 entry 再追加新的
                        knowledgeRepo.replaceActualSentRecord(kbName, existingEntry!!, sentEntry)
                    } else {
                        knowledgeRepo.appendActualSentRecord(kbName, sentEntry)
                    }
                }
                if (success) {
                    // 只有第一次确认才计 adopt；同一 version 更新不重复 +1
                    if (!isUpdate) {
                        _totalAdoptCount.value += 1
                        securePrefs.totalAdoptCount = _totalAdoptCount.value
                    }
                    _actualSentEntries[versionId?.value] = sentEntry
                    _actualSentState.value = ActualSentState.RECORDED
                    _kbNotice.value = if (isUpdate) "已更新实际发送记录" else "已记录实际发送的消息"
                } else {
                    _actualSentState.value = ActualSentState.KB_NOT_FOUND
                    showPanelWarning("本轮保存失败：知识库已被删除")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e("recordActualSentMessage failed", e)
                _actualSentState.value = ActualSentState.IO_ERROR
                showPanelWarning("记录发送失败，内容已保留，请重试")
            }
        }
    }

    /** P0-4: 跟踪当前 session 中已记录的 actual sent entries by versionId，用于 upsert 判断 */
    private val _actualSentEntries = mutableMapOf<String?, String>()

    // ═══════════ KnowledgeTriggerCoordinator.Callbacks 实现 ═══════════

    //  KBG-03：只让当前 KB 的 vector 回调更新 UI
    override fun onVectorUpdated(kbName: String, newVector: Map<String, Int>, delta: Map<String, Int>) {
        if (_activeKb.value?.name != kbName) return
        _currentVector.value = newVector
        _vectorDelta.value = delta
    }

    override fun onVectorUpdateNotice(kbName: String, summary: String) {
        if (_activeKb.value?.name != kbName) return
        _vectorUpdate.value = summary
    }

    override fun onStageSuggestion(suggestion: StageSuggestion) {
        _stageSuggestion.value = suggestion
    }

    override fun onKbNotice(notice: String) {
        _kbNotice.value = notice
    }

    //  KBG-02：画像建议绑定 originating KB，不丢弃身份
    override fun onProfileSuggestion(suggestion: ProfileSuggestion) {
        _profileSuggestion.value = suggestion
    }

    //  KBG-03：只让当前 KB 的 vector 回调更新 UI
    override fun onCurrentVector(kbName: String, vector: Map<String, Int>) {
        if (_activeKb.value?.name != kbName) return
        _currentVector.value = vector
    }

    //  KBG-02：确认画像时使用 suggestion.kbName，不使用 _activeKb
    // 使用统一的 ProfileUpdate payload，不重复解析 raw
    /**
     * 画像确认——委托 Repository 执行原子事务。
     *
     * P0-6: 事务结果以 typed [ProfileTransactionResult] 返回，
     * 不再用模糊 Boolean 表示所有失败情况。
     *
     * 事务在 Repository 的单次 fileMutex.withLock 中执行：
     * - 所有文件写入、向量同步、阶段更新、warmth 标签更新在同一锁内完成
     * - backup 覆盖所有实际会被修改的文件
     * - IO 失败必须抛出（strict 版本），不吞错误
     * - 任一步失败自动 rollback——rollback 成功/失败分别返回不同 typed result
     */
    fun confirmProfileUpdate() {
        val suggestion = _profileSuggestion.value ?: return

        if (_isProfileConfirming.value) return

        val payload = suggestion.profileUpdate
        if (payload == null || !payload.valid) {
            showPanelWarning("建议格式无效，请重新生成")
            _profileSuggestion.value = null
            return
        }

        val kbName = suggestion.kbName
        val suggestionId = suggestion.suggestionId

        viewModelScope.launch {
            _isProfileConfirming.value = true

            try {
                val exists = withContext(Dispatchers.IO) {
                    knowledgeRepo.listAll().any { it.name == kbName }
                }
                if (!exists) {
                    _profileSuggestion.value = null
                    showPanelWarning("原知识库已删除，这条画像建议已失效")
                    return@launch
                }

                val currentRev = withContext(Dispatchers.IO) {
                    knowledgeRepo.getCorrectionsRevision(kbName)
                }
                if (currentRev != suggestion.correctionsRevision) {
                    _profileSuggestion.value = null
                    showPanelWarning("资料已变化，请重新生成")
                    return@launch
                }

                // P0-6: 委托 Repository 执行原子事务——返回 typed result
                val result = knowledgeRepo.applyProfileUpdateAtomically(
                    kbName = kbName,
                    me = payload.me,
                    her = payload.her,
                    warmth = payload.warmth,
                    stageChanged = payload.stageChanged,
                    newStage = payload.newStage,
                    expectedRevision = suggestion.correctionsRevision
                )

                // P0-6: 按 typed result 分支给出精确反馈
                when (result) {
                    is ProfileTransactionResult.Success -> {
                        val current = _profileSuggestion.value
                        if (current != null && current.suggestionId == suggestionId) {
                            _profileSuggestion.value = null
                        }
                        _kbNotice.value = "画像已更新"
                        refreshKnowledgeBases()
                    }
                    is ProfileTransactionResult.PreconditionFailed -> {
                        // Repository 层的二次检查——VM 层已检查过，这是竞态兜底
                        _profileSuggestion.value = null
                        val msg = when (result.reason) {
                            PreconditionReason.KB_NOT_FOUND -> "原知识库已删除，这条画像建议已失效"
                            PreconditionReason.REVISION_CONFLICT -> "资料已变化，请重新生成"
                        }
                        showPanelWarning(msg)
                    }
                    is ProfileTransactionResult.RolledBack -> {
                        // 写入失败但 rollback 完整成功——数据已恢复，可安全重试
                        L.e("confirmProfileUpdate: transaction rolled back", result.cause)
                        showPanelWarning("画像写入失败，已恢复原数据，可重试")
                    }
                    is ProfileTransactionResult.RollbackFailed -> {
                        // 写入失败且 rollback 也失败——数据可能不一致，不可轻描淡写
                        L.e("confirmProfileUpdate: CRITICAL rollback failed for paths=${result.failedPaths}", result.cause)
                        showPanelWarning("画像写入失败且恢复异常，数据可能已损坏，请检查知识库")
                    }
                }
            } catch (e: Exception) {
                L.e("confirmProfileUpdate unexpected error", e)
                showPanelWarning("画像写入发生异常，请重试")
            } finally {
                _isProfileConfirming.value = false
            }
        }
    }

    fun dismissProfileUpdate() {
        // 取消正在进行的重新生成 job，使旧请求无效
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.PROFILE_REFRESH)
        profileRegenerationRequestId++
        _profileSuggestion.value = null
    }

    /**
     * 画像重新生成——原地显示 loading，卡片位置不变。
     *
     * 修复 P0-8：所有失败路径（EMPTY/PROVIDER_ERROR/EXCEPTION）都必须复位 loading。
     * 修复 P0-9：增加 request identity——dismiss 后旧请求回来不会复活卡片。
     */
    /** S2-02: 是否在重新生成画像——从 coordinator 派生，不再是独立 boolean */
    val profileRegenerating: StateFlow<Boolean> =
        busyOf(ForegroundOperationCoordinator.OperationType.PROFILE_REFRESH)


    /** 重新生成请求 ID——每次 dismiss 递增，旧请求回来时 requestId 不匹配则丢弃 */
    private var profileRegenerationRequestId: Int = 0

    fun regenerateProfileUpdate() {
        val suggestion = _profileSuggestion.value ?: return
        val kbName = suggestion.kbName

        // 取消上一次未完成的重新生成
        // P0-2 真正修复：取消 profileRegenerationJob 会传播到底层 coroutineScope child，
        // 真正取消模型请求、retry delay、reflect_history 写入
        // S2-02: 取消上一次未完成的重新生成——经历 coordinator，不再自己持 Job
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.PROFILE_REFRESH)
        val currentRequestId = ++profileRegenerationRequestId

        // P0-2 真正修复：regenerateProfile 是纯 suspend——不传 viewModelScope，
        // 在当前 launch 的协程内直接执行，cancel 会传播到底层所有子协程
        // S2-02: PROFILE_REFRESH 此前从未注册到协调器，现在进账本
        operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.PROFILE_REFRESH,
            currentRequestId.toString()
        ) {
            try {
                triggerCoordinator.regenerateProfile(kbName, object : KnowledgeTriggerCoordinator.Callbacks {
                    override fun onVectorUpdated(kbName: String, newVector: Map<String, Int>, delta: Map<String, Int>) {}
                    override fun onVectorUpdateNotice(kbName: String, summary: String) {}
                    override fun onStageSuggestion(suggestion: StageSuggestion) {}
                    override fun onKbNotice(notice: String) {
                        if (currentRequestId == profileRegenerationRequestId) {
                        }
                    }
                    override fun onProfileSuggestion(suggestion: ProfileSuggestion) {
                        if (currentRequestId == profileRegenerationRequestId) {
                            _profileSuggestion.value = suggestion
                        }
                    }
                    override fun onCurrentVector(kbName: String, vector: Map<String, Int>) {}
                })
            } catch (e: kotlinx.coroutines.CancellationException) {
                // dismiss cancel——不设 error，只复位 loading
                if (currentRequestId == profileRegenerationRequestId) {
                }
                throw e
            } catch (e: Exception) {
                L.e("regenerateProfileUpdate failed", e)
                if (currentRequestId == profileRegenerationRequestId) {
                }
            } finally {
                if (currentRequestId == profileRegenerationRequestId) {
                }
            }
        }
    }

    // ═══════════ 谈心模式（委托 GenerationEngine） ═══════════

    /**
     * S2-02: 谈心——coordinator 注册的唯一前台任务；不存在第二个 Job owner。
     * COUN-01：冻结 KB 快照传入 Engine，谈心期间切 KB 不影响 prompt 与日志目标。
     */
    fun generateCounseling(userMessage: String) {
        if (userMessage.isBlank()) return
        val kbSnapshot = _activeKb.value
        val requestId = ReplyRequestState.newRequestId()
        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.COUNSELING, requestId
        ) {
            applyCounselingEvent(CounselingStarted(requestId))
            try {
                generationEngine.counselingStream(requestId, userMessage, kbSnapshot)
                    .collect { applyCounselingEvent(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                applyCounselingEvent(CounselingEnded(requestId))
                throw e
            }
        }
        if (lease == null) L.w("counseling rejected: foreground slot taken")
    }

    fun stopCounseling() {
        val current = operationCoordinator.current(
            ForegroundOperationCoordinator.OperationType.COUNSELING
        ) ?: return
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.COUNSELING)
        cancelPendingCounselingFlush()
        applyCounselingEvent(CounselingEnded(current.requestId))
        L.w("user stopped counseling")
        if (_counselingResult.value == null) _counselingError.value = "已手动停止"
    }


    private suspend fun saveCounselingLog(
        kbName: String?,
        userMessage: String,
        replyText: String,
        analysisText: String
    ) {
        if (kbName == null || replyText.isBlank()) return
        val time = TimeFmt.now()

        val recordEntry = buildString {
            append("## [").append(time).append("] 谈心\n")
            append("我倾诉：").append(userMessage.trim()).append("\n")
            append("军师回复：").append(replyText.trim())
        }

        val analysisEntry = if (analysisText.isNotBlank()) {
            val lines = analysisText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val title = lines.firstOrNull()?.take(TITLE_FIRST_LINE_LIMIT) ?: userMessage.trim().take(TITLE_FALLBACK_LIMIT)
            val body = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""
            buildString {
                append("## [").append(time).append("] ").append(title).append("\n")
                if (body.isNotBlank()) append(body)
            }
        } else ""

        try {
            withContext(Dispatchers.IO) {
                knowledgeRepo.appendCounselingEntries(kbName, recordEntry, analysisEntry)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w("saveCounselingLog failed: ${e.javaClass.simpleName}")
        }
    }

    fun clearCounseling() {
        _counselingResult.value = null
        _counselingError.value = null
        securePrefs.clearCounselingResult()
    }

    fun clearCounselingAll() {
        _counselingResult.value = null
        _counselingError.value = null
        _counselingDraft.value = ""
        _counselingStreaming.value = ""
        // 先取消防抖尾再写空，防"清空后旧草稿被防抖任务写回"复活竞态
        draftPersistJob?.cancel()
        securePrefs.counselingDraft = ""
        securePrefs.clearCounselingHistory()
    }

    fun saveCounselingHistory(history: List<Pair<String, String>>) {
        val json = history.joinToString(",", "[", "]") { (q, a) ->
            "{\"q\":\"${Jsons.escapeJsonString(q)}\",\"a\":\"${Jsons.escapeJsonString(a)}\"}"
        }
        securePrefs.saveCounselingHistory(json)
    }

    fun loadCounselingHistory(): List<Pair<String, String>> {
        val json = securePrefs.loadCounselingHistory() ?: return emptyList()
        return runCatching {
            val result = mutableListOf<Pair<String, String>>()
            val regex = """"q":"((?:[^"\\]|\\.)*)"\s*,\s*"a":"((?:[^"\\]|\\.)*)"""".toRegex()
            regex.findAll(json).forEach { match ->
                val q = Jsons.unescapeJsonString(match.groupValues[1])
                val a = Jsons.unescapeJsonString(match.groupValues[2])
                result.add(q to a)
            }
            result
        }.getOrElse { emptyList() }
    }

    // ═══════════ 知识库管理 ═══════════

    fun refreshKnowledgeBases() {
        viewModelScope.launch {
            // P1-D: refreshIntentConfigForKb 现在是结构化 child（suspend），
            // 不再是 fire-and-forget sibling coroutine。
            // CancellationException 正常重抛；普通 IO 异常捕获不崩 scope。
            try {
                // KBUI-01：切库时清理上一 KB 的瞬时 vector UI（delta / update / notice）
                val oldKbName = _activeKb.value?.name
                val newKb = knowledgeRepo.getActive()
                if (oldKbName != newKb?.name) {
                    _vectorDelta.value = emptyMap()
                    _vectorUpdate.value = null
                    _kbNotice.value = null
                    // P1-F: 切库时复位仅看本轮开关——属于当前工作轮次
                    _onlyThisRound.value = false
                    // P1-D: 切库时清除旧 KB 的意图配置，防止旧意图泄漏到新 KB
                    _intentConfig.value = com.lovebrain.app.model.IntentConfig()
                    // F04: 切库时清空本轮瞬时纠正
                    roundCorrections.clear()
                }
                _activeKb.value = newKb
                newKb?.let {
                    knowledgeRepo.migrateIfNeeded(it.name)
                    // S2-04: WAL 崩溃恢复——检查未完成的 round commit 事务并 roll-forward
                    topicRecorder.recoverIfNeeded(it.name)
                    _currentVector.value = knowledgeRepo.readVector(it.name)
                    // P1-D: 结构化 child——在当前协程内直接 await，不再 fire-and-forget。
                    // refreshIntentConfigForKb 内部有 KB identity guard 保护 UI commit。
                    refreshIntentConfigForKb(it.name)
                }
                // CARRY-09：删除最后一个 KB 时 newKb==null，旧 _currentVector 未被清空
                if (newKb == null) {
                    _currentVector.value = emptyMap()
                }
                // P0-1: KB 切换后检测 stale——如果当前结果来自旧 KB，标记为 stale
                if (oldKbName != null && oldKbName != newKb?.name) {
                    markCurrentResultStaleIfNeeded()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.w("refreshKnowledgeBases failed: ${e::class.simpleName}")
            }
        }
    }

    // ═══════════ 今日锦囊（委托 GenerationEngine） ═══════════

    fun openPlanPanel() { _showPlanPanel.value = true }
    fun dismissPlanPanel() { _showPlanPanel.value = false }

    /** 一次锦囊请求冻结下来的身份——写缓存时用它，绝不回读实时 _activeKb */
    private data class SuggestRequestContext(
        val requestId: String,
        val kbName: String,
        val kb: KnowledgeBase,
        val date: String,
        val contextFingerprint: String,
        val promptVersion: String
    )

    /** 当前在途的锦囊请求上下文；任务结束时清空 */
    private var suggestContext: SuggestRequestContext? = null

    /** GEN-01：同步 guard 由 coordinator 承担——Engine reject → null → 旧任务保持。 */
    fun generateSuggest() {
        showTodaySuggestion()
    }

    /** S1-04: 展示今日锦囊——允许命中缓存（同日同KB同上下文不重复请求）。
     * 上下文指纹要读事项/偏好文件，所以在协程里算完再决定是否发请求。 */
    fun showTodaySuggestion() {
        if (operationCoordinator.isBusy(ForegroundOperationCoordinator.OperationType.SUGGEST)) return
        if (_activeKb.value == null) { applySuggestError("还没有知识库，请先到设置页创建"); return }
        viewModelScope.launch {
        val ctx = buildSuggestContext() ?: return@launch
        val cached = securePrefs.loadSuggestion()?.let { cache ->
            if (cache.date != ctx.date || cache.kbId != ctx.kbName ||
                cache.contextFingerprint != ctx.contextFingerprint ||
                cache.promptVersion != ctx.promptVersion
            ) return@let null
            try {
                Json.decodeFromString<com.lovebrain.app.model.DailySuggestion>(cache.json)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.w("SUGGEST cache parse failed: ${e.message}")
                null
            }
        }
        if (cached != null) {
            _suggestion.value = cached
            L.w("SUGGEST cache hit, skipping model request")
            return@launch
        }
        startSuggestGeneration(ctx)
        }
    }

    /** S1-04: 明确重新生成锦囊——绕过缓存，发起新的模型请求 */
    fun regenerateSuggestion() {
        if (operationCoordinator.isBusy(ForegroundOperationCoordinator.OperationType.SUGGEST)) return
        if (_activeKb.value == null) { applySuggestError("还没有知识库，请先到设置页创建"); return }
        viewModelScope.launch {
            val ctx = buildSuggestContext() ?: return@launch
            L.w("SUGGEST explicit regeneration, bypassing cache")
            startSuggestGeneration(ctx)
        }
    }

    /** S1-04 审计修复: 在发起之前把请求身份冻结下来 */
    private suspend fun buildSuggestContext(): SuggestRequestContext? {
        val kb = _activeKb.value ?: return null
        val today = TimeFmt.today()
        return SuggestRequestContext(
            requestId = ReplyRequestState.newRequestId(),
            kbName = kb.name,
            kb = kb,
            date = today,
            contextFingerprint = computeSuggestContextFingerprint(kb.name, today),
            promptVersion = currentPromptVersion()
        )
    }

    private fun startSuggestGeneration(ctx: SuggestRequestContext) {
        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.SUGGEST, ctx.requestId
        ) {
            suggestContext = ctx
            applySuggestEvent(SuggestStarted(ctx.requestId))
            try {
                generationEngine.suggestStream(ctx.requestId, ctx.kb).collect { applySuggestEvent(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                applySuggestEvent(SuggestEnded(ctx.requestId))
                throw e
            } finally {
                if (suggestContext?.requestId == ctx.requestId) suggestContext = null
            }
        }
        if (lease == null) L.w("suggest rejected: another suggest request is running")
    }

    /**
     * S1-04: prompt 版本 = 真正进入请求的 prompt 资产内容 hash。
     *
     * 以前这里返回 BuildConfig.VERSION_NAME：App 没发版就永远算不出"prompt 被改过"，
     * 于是改过 prompt 仍然命中旧缓存——用户看到的还是上一版建议。
     */
    private fun currentPromptVersion(): String = promptBuilder.assetHashOf(AssetRegistry.SUGGEST)

    /**
     * S1-04: 计算锦囊上下文指纹。
     *
     * 上一版的注释写着"覆盖事项 revision、表达偏好、温度与边界、prompt asset hash"，
     * 实际只拼了 kbId/date/stage/outputMode/thinkingMode/onlyThisRound/App版本名/host/model。
     * 现在覆盖真正会改变输出的每一项，并且把 prompt 版本换成资产内容 hash：
     * - KB 身份、日期、阶段
     * - 输出风格边界（outputMode）、思考模式、仅看本轮
     * - suggest.md 资产内容 hash（改 prompt 立刻不再命中旧缓存）
     * - Provider host + model
     * - 本轮真正注入的事项（plan.md 内容指纹）与表达偏好（style.md 内容指纹）
     *
     * 说明：本项目没有可调的采样温度设置（温度由 prompt/Provider 侧固定），
     * 因此这里不谎称覆盖"温度"。
     */
    private suspend fun computeSuggestContextFingerprint(kbId: String, today: String): String {
        val kb = _activeKb.value
        val stage = kb?.stage ?: ""
        val outputMode = _outputMode.value
        val thinkingMode = securePrefs.thinkingMode
        val onlyThisRound = _onlyThisRound.value
        val providerConfig = deepSeekRepo.snapshotProviderConfig()
        val providerHost = providerConfig?.baseUrl ?: ""
        val providerModel = providerConfig?.model ?: ""
        val assetHash = promptBuilder.assetHashOf(AssetRegistry.SUGGEST)
        val ongoingRevision = kbId.takeIf { it.isNotBlank() }
            ?.let { runCatching { knowledgeRepo.readFile(it, "moment/plan.md") }.getOrDefault("") } ?: ""
        val styleRevision = kbId.takeIf { it.isNotBlank() }
            ?.let { runCatching { knowledgeRepo.readFile(it, "understand/style.md") }.getOrDefault("") } ?: ""

        val fingerprintInput = buildString {
            append(kbId).append('|').append(today).append('|').append(stage).append('|')
            append(outputMode).append('|').append(thinkingMode).append('|').append(onlyThisRound).append('|')
            append(assetHash).append('|').append(providerHost).append('|').append(providerModel).append('|')
            append(ongoingRevision.sha256Prefix()).append('|').append(styleRevision.sha256Prefix())
        }
        return fingerprintInput.sha256Prefix(16)
    }

    /** 长文本取内容前缀指纹，避免把整篇文档拼进指纹输入 */
    private fun String.sha256Prefix(len: Int = 12): String {
        if (isEmpty()) return "-"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(toByteArray()).joinToString("") { "%02x".format(it) }.take(len)
    }

    private val kbName: String? get() = _activeKb.value?.name

    fun stopSuggest() {
        val current = operationCoordinator.current(
            ForegroundOperationCoordinator.OperationType.SUGGEST
        ) ?: return
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.SUGGEST)
        applySuggestEvent(SuggestEnded(current.requestId))
        _streamingTips.value = emptyList()
        _suggestError.value = "已手动停止"
        L.w("user stopped suggest")
    }

    // ═══════════ 主动发起/润色（委托 GenerationEngine） ═══════════

    /** S2-02: 主动发——coordinator 注册的唯一前台任务 */
    fun generateProactive(draft: String = "", scene: String = "") {
        // S1-01: 主动发生成入口——设置 composer mode 和 result mode
        _composerMode.value = ComposerMode.PROACTIVE
        _resultMode.value = ResultMode.PROACTIVE

        val requestId = ReplyRequestState.newRequestId()
        val kbSnapshot = _activeKb.value
        val messages = buildMessageSnapshot()
        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.PROACTIVE, requestId
        ) {
            applyProactiveEvent(ProactiveStarted(requestId))
            try {
                generationEngine.proactiveStream(requestId, draft, kbSnapshot, messages)
                    .collect { applyProactiveEvent(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                applyProactiveEvent(ProactiveEnded(requestId))
                throw e
            }
        }
        if (lease == null) {
            L.w("proactive rejected: foreground slot taken")
            // 被拒绝时不把 UI 留在"主动发"空壳上
            _composerMode.value = ComposerMode.REPLY
            _resultMode.value = ResultMode.REPLY
        }
    }

    fun stopProactive() {
        val current = operationCoordinator.current(
            ForegroundOperationCoordinator.OperationType.PROACTIVE
        ) ?: return
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.PROACTIVE)
        applyProactiveEvent(ProactiveEnded(current.requestId))
    }

    fun clearProactive() {
        _proactiveOptions.value = emptyList()
        _proactiveError.value = null
    }


    // ═══════════ S2-03: 谈心 / 锦囊 / 主动发的事件归约 ═══════════
    //
    // Engine 不再回调 ViewModel。这三个流程的状态各自只有一个 apply 入口，
    // 并且都先拿 coordinator 当前租约核对 requestId：被取代的旧请求的迟到事件整条丢弃。
    // 用户主动"停止"的状态清理放在 stopXxx() 里做——那是用户动作，不是旧请求的事件。

    /** 该 requestId 是否仍是 [type] 当前的主人 */
    private fun ownsOperation(
        type: ForegroundOperationCoordinator.OperationType,
        requestId: String
    ): Boolean = operationCoordinator.current(type)?.requestId == requestId

    // --- 谈心 ---

    private val counselingChunkPending = StringBuilder()
    private var counselingFlushJob: kotlinx.coroutines.Job? = null

    private fun applyCounselingEvent(event: CounselingEvent) {
        if (!ownsOperation(ForegroundOperationCoordinator.OperationType.COUNSELING, event.requestId)) {
            L.w("S2-03: counseling event ${event::class.simpleName} rejected (stale requestId)")
            return
        }
        when (event) {
            is CounselingStarted -> {
                _counselingResult.value = null
                _counselingError.value = null
                _counselingStreaming.value = ""
                counselingChunkPending.setLength(0)
            }
            is CounselingChunk -> {
                counselingChunkPending.append(event.text)
                scheduleCounselingFlush()
            }
            is CounselingFirstToken -> _lastResponseMs.value = event.elapsedMs
            is CounselingResult -> {
                flushPendingCounselingChunk()
                _counselingResult.value = event.replyText
                securePrefs.saveCounselingResult(event.replyText)
                // COUN-01：日志目标与倾诉文本都取事件里冻结的值，不回读实时状态
                viewModelScope.launch {
                    saveCounselingLog(event.kbName, event.userMessage, event.replyText, event.analysisText)
                }
            }
            is CounselingFailed -> {
                flushPendingCounselingChunk()
                _counselingError.value = event.message
            }
            is CounselingEnded -> {
                flushPendingCounselingChunk()
                _counselingStreaming.value = ""
            }
        }
    }

    private fun scheduleCounselingFlush() {
        if (counselingFlushJob?.isActive == true) return
        counselingFlushJob = viewModelScope.launch {
            while (true) {
                delay(STREAMING_FLUSH_INTERVAL_MS)
                flushPendingCounselingChunk()
            }
        }
    }

    private fun flushPendingCounselingChunk() {
        if (counselingChunkPending.isEmpty()) return
        val text = counselingChunkPending.toString()
        counselingChunkPending.setLength(0)
        _counselingStreaming.value = _counselingStreaming.value + text
    }

    private fun cancelPendingCounselingFlush() {
        counselingFlushJob?.cancel()
        counselingFlushJob = null
        counselingChunkPending.setLength(0)
    }

    // --- 锦囊 ---

    private fun applySuggestEvent(event: SuggestEvent) {
        if (!ownsOperation(ForegroundOperationCoordinator.OperationType.SUGGEST, event.requestId)) {
            L.w("S2-03: suggest event ${event::class.simpleName} rejected (stale requestId)")
            return
        }
        when (event) {
            is SuggestStarted -> {
                _suggestion.value = null
                _streamingTips.value = emptyList()
                _suggestError.value = null
            }
            is SuggestFirstToken -> _lastResponseMs.value = event.elapsedMs
            is SuggestTips -> if (event.tips.size > _streamingTips.value.size) _streamingTips.value = event.tips
            is SuggestFailed -> _suggestError.value = event.message
            is SuggestResult -> {
                _suggestion.value = event.suggestion
                // S1-04: 用发起时冻结的身份写缓存——跨午夜完成也写回发起那一天，
                // 不再回读实时 _activeKb、也不再重算"今天"
                val ctx = suggestContext
                if (event.suggestion != null && ctx != null && ctx.requestId == event.requestId) {
                    securePrefs.saveSuggestion(
                        Json.encodeToString(
                            serializer<com.lovebrain.app.model.DailySuggestion>(),
                            event.suggestion
                        ),
                        ctx.date,
                        ctx.kbName,
                        ctx.contextFingerprint,
                        ctx.promptVersion
                    )
                }
            }
            is SuggestEnded -> _streamingTips.value = emptyList()
        }
    }

    private fun applySuggestError(msg: String) {
        _suggestError.value = msg
    }

    // --- 主动发起 ---

    private fun applyProactiveEvent(event: ProactiveEvent) {
        if (!ownsOperation(ForegroundOperationCoordinator.OperationType.PROACTIVE, event.requestId)) {
            L.w("S2-03: proactive event ${event::class.simpleName} rejected (stale requestId)")
            return
        }
        when (event) {
            is ProactiveStarted -> {
                _proactiveOptions.value = emptyList()
                _proactiveError.value = null
            }
            is ProactiveFirstToken -> _lastResponseMs.value = event.elapsedMs
            is ProactiveOptions -> _proactiveOptions.value = event.options
            is ProactiveFailed -> _proactiveError.value = event.message
            is ProactiveEnded -> if (_proactiveOptions.value.isNotEmpty()) exitProactiveMode()
        }
    }

    // ═══════════ F07: 持续意图 UI 状态 ═══════════

    /** F07: 当前 KB 的持续意图配置（面板 chip 展示 + 编辑入口） */
    private val _intentConfig = MutableStateFlow(com.lovebrain.app.model.IntentConfig())
    val intentConfig: StateFlow<com.lovebrain.app.model.IntentConfig> = _intentConfig.asStateFlow()

    /** F07: 持续意图编辑面板可见性 */
    private val _showIntentEditor = MutableStateFlow(false)
    val showIntentEditor: StateFlow<Boolean> = _showIntentEditor.asStateFlow()

    /** F07/P1-D: 刷新持续意图配置（切库/面板可见时调用）
     *  F06: 自动检测到期——TODAY 跨日自动标记 EXPIRED，DATE 过期也标记。
     *  P1-D: 委托给 refreshIntentConfigForKb，绑定实际 KB 名防竞态。 */
    fun refreshIntentConfig() {
        val kbName = _activeKb.value?.name ?: return
        viewModelScope.launch {
            refreshIntentConfigForKb(kbName)
        }
    }

    /** P1-D: 绑定 KB 名刷新意图配置——suspend 函数，由调用方在结构化协程中 await。
     *  不再内部 viewModelScope.launch（fire-and-forget sibling），消除切库竞态。
     *  KB identity guard：commit UI 前验证当前 active KB 仍是目标 KB。
     *  CancellationException 正常重抛（协程取消）；IO 异常捕获不崩 scope。 */
    private suspend fun refreshIntentConfigForKb(kbName: String) {
        val config = try {
            knowledgeRepo.readIntent(kbName)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w("refreshIntentConfigForKb read failed: ${e::class.simpleName}")
            return
        }
        // F06: 自动到期检测
        val finalConfig = if (config.enabled && config.status == com.lovebrain.app.model.IntentStatus.ACTIVE) {
            val today = com.lovebrain.app.util.TimeFmt.today()
            val shouldExpire = when (config.expiry) {
                com.lovebrain.app.model.IntentExpiry.TODAY -> {
                    config.expiryDate.isNotBlank() && config.expiryDate < today
                }
                com.lovebrain.app.model.IntentExpiry.DATE -> {
                    config.expiryDate.isNotBlank() && config.expiryDate < today
                }
                com.lovebrain.app.model.IntentExpiry.UNTIL_DONE -> false
            }
            if (shouldExpire) {
                val updated = config.copy(status = com.lovebrain.app.model.IntentStatus.EXPIRED)
                try {
                    knowledgeRepo.saveIntent(
                        kbName, config.text, false,
                        config.expiry, config.expiryDate,
                        com.lovebrain.app.model.IntentStatus.EXPIRED
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    L.w("refreshIntentConfigForKb expire save failed: ${e::class.simpleName}")
                }
                updated
            } else {
                config
            }
        } else {
            config
        }
        // KB identity guard：只有当前 active KB 仍是目标 KB 时才更新 UI
        if (_activeKb.value?.name == kbName) {
            _intentConfig.value = finalConfig
        }
    }

    /** R08: 保存持续意图配置。绑定编辑时冻结的 KB，不读当前 active KB。
     *  持久化失败保留编辑状态并提示。旧请求因 revision 变化而作废。
     *  F06: 支持有效期和完成状态。
     *  P1-D: TODAY 时自动写 expiryDate=today()，不依赖 UI 填写。 */
    fun saveIntent(
        text: String,
        enabled: Boolean,
        expiry: com.lovebrain.app.model.IntentExpiry = com.lovebrain.app.model.IntentExpiry.UNTIL_DONE,
        expiryDate: String = "",
        status: com.lovebrain.app.model.IntentStatus = com.lovebrain.app.model.IntentStatus.ACTIVE
    ) {
        // R08: 绑定编辑器打开时的 KB，不读当前 active KB
        val kbName = intentEditorKbName ?: _activeKb.value?.name ?: return
        // P1-D: TODAY 类型自动写 expiryDate=today()，不依赖 UI 填写
        val effectiveExpiryDate = when (expiry) {
            com.lovebrain.app.model.IntentExpiry.TODAY -> com.lovebrain.app.util.TimeFmt.today()
            else -> expiryDate
        }
        // P1-RC: DATE 类型严格校验——blank / malformed / past 都必须拒绝
        if (expiry == com.lovebrain.app.model.IntentExpiry.DATE) {
            if (effectiveExpiryDate.isBlank()) {
                showPanelWarning("指定日期不能为空，请输入 yyyy-MM-dd 格式的日期")
                return
            }
            val parsed = runCatching { java.time.LocalDate.parse(effectiveExpiryDate) }.getOrNull()
            if (parsed == null) {
                showPanelWarning("日期格式无效，请使用 yyyy-MM-dd 格式")
                return
            }
            if (status == com.lovebrain.app.model.IntentStatus.ACTIVE && parsed.isBefore(java.time.LocalDate.now())) {
                showPanelWarning("过去日期不能以活跃状态保存")
                return
            }
        }
        viewModelScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    knowledgeRepo.saveIntent(kbName, text, enabled, expiry, effectiveExpiryDate, status)
                }
                // KB identity guard：只有当前 active KB 仍是保存目标的 KB 时才更新 UI
                if (_activeKb.value?.name == kbName) {
                    _intentConfig.value = updated
                    _kbNotice.value = if (enabled) "持续意图已开启" else "持续意图已关闭"
                    _showIntentEditor.value = false
                    markCurrentResultStaleIfNeeded()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                showPanelWarning("意图保存失败，内容已保留，请重试")
            }
        }
    }

    /** R08: 编辑器绑定创建时 KB，防切库写错人 */
    private var intentEditorKbName: String? = null

    fun openIntentEditor() {
        intentEditorKbName = _activeKb.value?.name
        _showIntentEditor.value = true
    }
    fun dismissIntentEditor() { _showIntentEditor.value = false }

    // ═══════════ F09: 记忆纠正（绑定生成时冻结的 KB） ═══════════

    /**
     * F09: 获取本轮注入的 MemoryRef 清单（供 UI 展示纠正入口）。
     * 返回生成时冻结的快照，不受后续切 KB 影响。
     */
    fun getCurrentMemoryRefs(): List<com.lovebrain.app.model.MemoryRef> {
        return replyGenerationContext?.memoryRefs ?: emptyList()
    }

    /**
     * F09: 对指定 memoryId 发起纠正操作。
     * 必须绑定生成时冻结的 KB（context.kbName），不读当前 active KB。
     * 纠正参与下一次 PromptBuilder 过滤；所有操作可撤销、重启有效、不调用模型。
     */
    fun applyMemoryCorrection(
        memoryId: String,
        action: com.lovebrain.app.model.CorrectionAction,
        replacementText: String = "",
        targetKbId: String = "",
        muteDuration: com.lovebrain.app.model.MuteDuration = com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
    ) {
        val ctx = replyGenerationContext ?: return
        val kbName = ctx.kbName ?: return
        // F04: THIS_ROUND mute 只存瞬时 map，不持久化——nextRound/切库自动清空
        if (action == com.lovebrain.app.model.CorrectionAction.MUTED &&
            muteDuration == com.lovebrain.app.model.MuteDuration.THIS_ROUND) {
            roundCorrections[memoryId] = com.lovebrain.app.model.MemoryCorrection(
                memoryId = memoryId,
                action = action,
                replacementText = replacementText,
                targetKbId = targetKbId,
                muteDuration = muteDuration,
                muteTimestamp = com.lovebrain.app.util.TimeFmt.now()
            )
            _kbNotice.value = "已暂停本轮提及，下次生成将过滤此条记忆"
            return
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                knowledgeRepo.saveCorrection(kbName, memoryId, action, replacementText, targetKbId, muteDuration)
            }
            if (success) {
                _kbNotice.value = when (action) {
                    com.lovebrain.app.model.CorrectionAction.WRONG -> "已标记为错误，下次生成将过滤此条记忆"
                    com.lovebrain.app.model.CorrectionAction.FINISHED -> "已标记为结束，不再作为活跃事项"
                    com.lovebrain.app.model.CorrectionAction.MUTED -> "已暂停提及，下次生成将过滤此条记忆"
                    com.lovebrain.app.model.CorrectionAction.WRONG_PERSON -> "已隔离，不再注入此条记忆"
                }
            } else {
                _kbNotice.value = "纠正保存失败，请重试"
            }
        }
    }

    /**
     * F04: 加载所有纠正记录——供纠正中心 UI 展示。
     * 绑定生成时冻结的 KB；无生成上下文时读当前 active KB。
     */
    fun loadAllCorrections(
        onResult: (Map<String, com.lovebrain.app.model.MemoryCorrection>) -> Unit
    ) {
        val kbName = replyGenerationContext?.kbName ?: _activeKb.value?.name ?: run {
            onResult(emptyMap())
            return
        }
        viewModelScope.launch {
            val corrections = withContext(Dispatchers.IO) {
                knowledgeRepo.readCorrections(kbName)
            }
            onResult(corrections)
        }
    }

    /**
     * F04: 撤销纠正——纠正中心使用，不依赖生成上下文。
     * 绑定生成时冻结的 KB；无生成上下文时读当前 active KB。
     * P0-6: 先检查 roundCorrections transient map——如果存在，立即撤销，不需要访问 Repository。
     */
    fun undoCorrectionFromCenter(memoryId: String) {
        // P0-6: 先检查 transient roundCorrections
        if (roundCorrections.containsKey(memoryId)) {
            roundCorrections.remove(memoryId)
            _kbNotice.value = "已撤销本轮暂停，该记忆恢复注入"
            return
        }
        val kbName = replyGenerationContext?.kbName ?: _activeKb.value?.name ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                knowledgeRepo.undoCorrection(kbName, memoryId)
            }
            if (success) {
                _kbNotice.value = "已撤销纠正，该记忆恢复可信注入"
            } else {
                _kbNotice.value = "撤销失败，请重试"
            }
        }
    }

    /**
     * F09: 撤销纠正 — 删除指定 memoryId 的纠正记录。
     * 撤销后该记忆恢复可信注入资格。绑定生成时冻结的 KB。
     * P0-6: 先检查 roundCorrections transient map——如果存在，立即撤销，不需要访问 Repository。
     */
    fun undoMemoryCorrection(memoryId: String) {
        // P0-6: 先检查 transient roundCorrections
        if (roundCorrections.containsKey(memoryId)) {
            roundCorrections.remove(memoryId)
            _kbNotice.value = "已撤销本轮暂停，该记忆恢复注入"
            return
        }
        val ctx = replyGenerationContext ?: return
        val kbName = ctx.kbName ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                knowledgeRepo.undoCorrection(kbName, memoryId)
            }
            if (success) {
                _kbNotice.value = "已撤销纠正，该记忆恢复可信注入"
            }
        }
    }

    // ═══════════ 单条改写（卡片内部展开 2×2 操作区） ═══════════

    /** DRY: 改写操作选项文案统一使用 RewriteCommand.PRESET_LABELS，不在 VM 重复定义 */
    val rewriteOptions: List<String> get() = com.lovebrain.app.model.RewriteCommand.PRESET_LABELS

    /** 单条改写状态：identityKey → 改写状态（P0-1: 使用 SchemeIdentity.key 区分 STYLE/DIRECTION） */
    private val _rewriteStates = MutableStateFlow<Map<String, RewriteState>>(emptyMap())
    val rewriteStates: StateFlow<Map<String, RewriteState>> = _rewriteStates.asStateFlow()

    /** 单条改写版本历史（用于撤销）—— P1-04: 保存正文+反馈，撤销时一并恢复 */
    private data class RewriteVersion(val reply: String, val feedback: SchemeFeedback)
    private val _rewriteHistory = MutableStateFlow<Map<String, List<RewriteVersion>>>(emptyMap())

    /** 改写任务 Job——与前台生成共用任务管理 */

    /** 改写请求 ID（锁定目标，防跨轮写入） */
    private var rewriteRequestId: String? = null

    /** 改写绑定轮次身份——新轮/切库/保存清空时作废旧改写请求 */
    private var rewriteContextId: String? = null

    /**
     * 对指定方案卡发起单条改写。
     *
     * P0-1: 使用 SchemeIdentity(source+tag) 区分 STYLE(A/B/C/D) 和 DIRECTION(F/E/X/S)。
     * STYLE 更新 ReplySchemes 对应正文；DIRECTION 更新 directions 对应 index。
     *
     * - 复用供应商配置冻结、HTTP 调用、取消、错误处理和 usage 统计。
     * - 禁止调用整轮 generate 然后取其中一条。
     * - 请求只包含短固定规则、目标原回复、选择的操作、最少必要的本轮真实上下文。
     * - 长知识库、完整分析、其他候选、四方向数组、事实提取和全量 JSON 不随之发送。
     * - 同一时间只运行一个前台生成/主动发/单条改写，共用现有任务管理。
     */
    fun rewriteScheme(source: com.lovebrain.app.model.SchemeSource, schemeTag: String, option: String) {
        // 查找 RewriteCommand——预设选项使用其 instruction；自定义走 rewriteSchemeCustom
        val command = com.lovebrain.app.model.RewriteCommand.fromLabel(option)
        if (command != null && command == com.lovebrain.app.model.RewriteCommand.CUSTOM) {
            // CUSTOM 不应直接调用 rewriteScheme——应由 UI 调 rewriteSchemeCustom
            return
        }
        // 使用 command.instruction 作为改写指令（比旧版只用 label 更精确）
        val instruction = command?.instruction ?: option
        rewriteSchemeInternal(source, schemeTag, instruction, option)
    }

    /**
     * 自定义改写——用户输入自由文字要求。
     * 如“保留第一句，第二句不要”。原句和自定义文字作为数据输入，
     * 不能借原句内的指令改变输出协议。
     */
    fun rewriteSchemeCustom(source: com.lovebrain.app.model.SchemeSource, schemeTag: String, customInstruction: String) {
        if (customInstruction.isBlank()) return
        rewriteSchemeInternal(source, schemeTag, customInstruction.trim(), "自定义")
    }

    /**
     * 统一改写内部实现——预设和自定义共用。
     * option 是 UI 展示文案，instruction 是发给模型的改写指令。
     */
    private fun rewriteSchemeInternal(
        source: com.lovebrain.app.model.SchemeSource,
        schemeTag: String,
        instruction: String,
        option: String
    ) {
        // 前台互斥：正在生成/主动发/改写中时拒绝
        if (_replyUi.value.isBusy ||
            operationCoordinator.isBusy(ForegroundOperationCoordinator.OperationType.PROACTIVE)) return

        // 首轮流式尚未完成时禁用改写
        if (_replyUi.value.isBusy) return

        val result = replyResult as? GenerateResult.Success ?: return
        val response = result.response
        // P0-1: 根据 source 查找目标 scheme——STYLE 从 schemes 找，DIRECTION 从 directionSchemes 找
        val allSchemes = when (source) {
            com.lovebrain.app.model.SchemeSource.STYLE -> response.schemes
            com.lovebrain.app.model.SchemeSource.DIRECTION -> response.directionSchemes
        }
        val scheme = allSchemes.find { it.tag == schemeTag && it.source == source } ?: return
        if (scheme.reply.isBlank()) return

        // P0-1: 使用 identity key 区分 STYLE 和 DIRECTION
        val identityKey = com.lovebrain.app.model.SchemeIdentity(source, schemeTag).key

        // 锁定目标
        val ctx = replyGenerationContext ?: return
        val requestId = java.util.UUID.randomUUID().toString()
        rewriteRequestId = requestId

        // 绑定轮次身份——新轮/切库/保存后旧改写不写入
        val contextId = (ctx.kbName ?: "") + "_" + ctx.messageIds.hashCode()
        rewriteContextId = contextId

        // 设置改写中状态
        _rewriteStates.value = _rewriteStates.value + (identityKey to RewriteState.Loading(option))

        // 保存当前版本到历史（用于撤销）—— P1-04: 保存正文+当前反馈
        val currentFeedback = _feedbacks.value[identityKey] ?: SchemeFeedback.NONE
        val currentHistory = _rewriteHistory.value[identityKey] ?: emptyList()
        _rewriteHistory.value = _rewriteHistory.value + (identityKey to currentHistory + RewriteVersion(scheme.reply, currentFeedback))

        // P1-04: 不再捕获 preRewriteFeedback 做后续清理——
        // 改写期间用户对旧文的反馈继续归旧版本；新版本独立 NONE。

        val ticket = _activeTicket.value
        val apiKey = ticket?.let { securePrefs.getWorkerApiKey(it.id) }

        // S2-02: 改写也进 coordinator 账本。旧实现只把 Job 存进 rewriteJob 字段，
        // 协调器完全不知道有这个任务，"六类前台操作统一协调"就是假的。
        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.REWRITE,
            requestId
        ) {
            try {
                // 构建短请求——只包含最少必要上下文
                val systemPrompt = buildRewriteSystemPrompt()
                val userPrompt = buildRewriteUserPrompt(
                    originalReply = scheme.reply,
                    option = instruction,
                    messages = ctx.messages,
                    intentText = ctx.intentText.takeIf { it.isNotBlank() && ctx.intentEnabled },
                    ideaHint = ctx.ideaHint.takeIf { it.isNotBlank() }
                )

                // 复用供应商配置冻结
                val config = if (ticket != null && !apiKey.isNullOrBlank() && ticket.model.isNotBlank()) {
                    com.lovebrain.app.data.ProviderRequestConfig(
                        ticketId = ticket.id,
                        apiKey = apiKey,
                        baseUrl = ticket.baseUrl,
                        model = ticket.model,
                        thinkingMode = ticket.thinkingMode ?: 0
                    )
                } else {
                    null
                }

                val raw = if (config != null) {
                    deepSeekRepo.generateRaw(config, systemPrompt, userPrompt)
                } else {
                    deepSeekRepo.generateRaw(systemPrompt, userPrompt)
                }

                // 校验请求身份——切库/新轮/清空后旧结果不写入
                if (rewriteRequestId != requestId) return@start
                // 校验轮次身份未变
                val currentContextId = (_activeKb.value?.name ?: "") + "_" + (replyGenerationContext?.messageIds?.hashCode() ?: 0)
                if (rewriteContextId != contextId || currentContextId != contextId) return@start

                val newReply = raw.trim()
                if (newReply.isBlank()) {
                    _rewriteStates.value = _rewriteStates.value + (identityKey to RewriteState.Error("改写返回空结果"))
                    return@start
                }

                // 只替换目标卡正文，不回写整个捕获的旧 response
                // P0-1: 根据 source 更新对应方案列表
                val currentResult = replyResult as? GenerateResult.Success ?: return@start
                val currentResponse = currentResult.response
                val updatedResponse = if (source == com.lovebrain.app.model.SchemeSource.STYLE) {
                    // STYLE: 更新 ReplySchemes
                    val updatedSchemes = currentResponse.schemes.map { s ->
                        if (s.tag == schemeTag && s.source == source) s.copy(reply = newReply) else s
                    }
                    currentResponse.copy(
                        response = com.lovebrain.app.model.ReplySchemes(
                            recommended = updatedSchemes.getOrNull(0)?.reply ?: "",
                            badBoy = updatedSchemes.getOrNull(1)?.reply ?: "",
                            playful = updatedSchemes.getOrNull(2)?.reply ?: "",
                            warm = updatedSchemes.getOrNull(3)?.reply ?: ""
                        )
                    )
                } else {
                    // DIRECTION: 更新 directions 数组对应 index
                    val dir = com.lovebrain.app.model.ReplyDirection.byTag(schemeTag)
                    if (dir != null) {
                        val updatedDirections = currentResponse.directions.toMutableList()
                        // 确保 directions 列表足够长
                        while (updatedDirections.size <= dir.index) {
                            updatedDirections.add(null)
                        }
                        updatedDirections[dir.index] = newReply
                        currentResponse.copy(directions = updatedDirections)
                    } else {
                        currentResponse
                    }
                }
                replaceReplyResult(GenerateResult.Success(updatedResponse))

                // P1-04: 改写成功后——新正文独立 NONE，不自动继承旧赞/踩
                // 旧赞保留在 rewriteHistory 中，撤销时恢复
                _feedbacks.value = _feedbacks.value.toMutableMap().apply {
                    put(identityKey, SchemeFeedback.NONE)
                }

                // P1-H: 递增累计改写次数
                _totalRewriteCount.value += 1
                securePrefs.totalRewriteCount = _totalRewriteCount.value

                // 清除改写状态，保留撤销入口
                _rewriteStates.value = _rewriteStates.value + (identityKey to RewriteState.Done(newReply))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e("rewriteScheme failed", e)
                if (rewriteRequestId == requestId) {
                    _rewriteStates.value = _rewriteStates.value + (identityKey to RewriteState.Error("改写失败，可重试"))
                }
            }
        }
    }

    /** 取消正在进行的改写 —— P0-1: 使用 identityKey */
    fun cancelRewrite(identityKey: String) {
        // S2-02: 只停 REWRITE 这一个 owner
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.REWRITE)
        rewriteRequestId = null
        _rewriteStates.value = _rewriteStates.value.filterKeys { it != identityKey }
    }

    /** 撤销改写——恢复到上一版本（含正文和反馈）—— P0-1: 使用 identityKey */
    fun undoRewrite(identityKey: String) {
        val history = _rewriteHistory.value[identityKey] ?: return
        if (history.isEmpty()) return
        val previousVersion = history.last()
        val updatedHistory = history.dropLast(1)

        _rewriteHistory.value = if (updatedHistory.isEmpty()) {
            _rewriteHistory.value - identityKey
        } else {
            _rewriteHistory.value + (identityKey to updatedHistory)
        }

        val result = replyResult as? GenerateResult.Success ?: return
        val response = result.response
        // P0-1: 从 identityKey 解析 source 和 tag
        val identity = com.lovebrain.app.model.SchemeIdentity.fromKey(identityKey) ?: return
        val updatedResponse = if (identity.source == com.lovebrain.app.model.SchemeSource.STYLE) {
            val updatedSchemes = response.schemes.map { s ->
                if (s.tag == identity.tag && s.source == identity.source) s.copy(reply = previousVersion.reply) else s
            }
            response.copy(
                response = com.lovebrain.app.model.ReplySchemes(
                    recommended = updatedSchemes.getOrNull(0)?.reply ?: "",
                    badBoy = updatedSchemes.getOrNull(1)?.reply ?: "",
                    playful = updatedSchemes.getOrNull(2)?.reply ?: "",
                    warm = updatedSchemes.getOrNull(3)?.reply ?: ""
                )
            )
        } else {
            val dir = com.lovebrain.app.model.ReplyDirection.byTag(identity.tag)
            if (dir != null) {
                val updatedDirections = response.directions.toMutableList()
                while (updatedDirections.size <= dir.index) {
                    updatedDirections.add(null)
                }
                updatedDirections[dir.index] = previousVersion.reply
                response.copy(directions = updatedDirections)
            } else {
                response
            }
        }
        replaceReplyResult(GenerateResult.Success(updatedResponse))
        // P1-04: 撤销时恢复旧版本的反馈，不只是正文
        _feedbacks.value = _feedbacks.value.toMutableMap().apply {
            put(identityKey, previousVersion.feedback)
        }
        _rewriteStates.value = _rewriteStates.value - identityKey
    }

    /** 清除改写状态（展开/收起时调用）—— P0-1: 使用 identityKey */
    fun clearRewriteState(identityKey: String) {
        val current = _rewriteStates.value[identityKey]
        if (current is RewriteState.Done || current is RewriteState.Error) {
            _rewriteStates.value = _rewriteStates.value - identityKey
        }
    }

    /** 构建改写系统提示——短固定规则 */
    private fun buildRewriteSystemPrompt(): String = buildString {
        appendLine("你是恋爱沟通助手。用户想改写一条已有的回复。")
        appendLine("规则：")
        appendLine("1. 只输出改写后的回复正文，不输出任何分析、标签或格式说明")
        appendLine("2. 不能改人名、时间、约定和说话主体")
        appendLine("3. 不能添加未经证实的事实")
        appendLine("4. 不能把候选回复当作已发送消息")
        appendLine("5. 输出一个非空回复正文即可")
        appendLine("6. 如果提供了「我的表达偏好」，改写时尽量遵守该偏好")
    }

    /** 构建改写用户提示——只包含最少必要上下文
     *  F05: 注入个人表达偏好（understand/style.md） */
    private suspend fun buildRewriteUserPrompt(
        originalReply: String,
        option: String,
        messages: List<ChatMessage>,
        intentText: String?,
        ideaHint: String?
    ): String = buildString {
        // F05: 个人表达偏好
        val kbName = replyGenerationContext?.kbName
        if (!kbName.isNullOrBlank()) {
            val style = knowledgeRepo.readFile(kbName, "understand/style.md")
            if (style.isNotBlank()) {
                appendLine("我的表达偏好：")
                appendLine(style.trim())
                appendLine()
            }
        }

        // 最近几条真实对话（不含想法）
        val recentChat = messages
            .filter { it.role != ChatMessage.Role.IDEA }
            .takeLast(6)
            .joinToString("\n") { msg ->
                val role = when (msg.role) {
                    ChatMessage.Role.HER -> "她"
                    ChatMessage.Role.ME -> "我"
                    else -> msg.role.label
                }
                "$role：${msg.content}"
            }
        if (recentChat.isNotBlank()) {
            appendLine("最近对话：")
            appendLine(recentChat)
            appendLine()
        }

        // 持续意图（仅在有且启用时）
        if (!intentText.isNullOrBlank()) {
            appendLine("当前意图：$intentText")
            appendLine()
        }

        // 想法（仅在有且非空时）
        if (!ideaHint.isNullOrBlank()) {
            appendLine("想法备注：$ideaHint")
            appendLine()
        }

        appendLine("原回复：$originalReply")
        appendLine()
        append("改写要求：$option")
    }

    // ═══════════ 生命周期清理 ═══════════

    /**
     * 显式 teardown：取消所有生成协程。
     * 由 [com.lovebrain.app.service.FloatingService.onDestroy] 调用，
     * 防止 Service 销毁后协程仍在运行导致泄漏。
     * 幂等——重复调用无副作用。
     * 注：完整泄漏验证需 LeakCanary 运行（AUTO_POLISH PHASE A 跟进）。
     */
    fun dispose() {
        // ：取消防抖尾 + 同步直写最终草稿（正常关闭悬浮窗零丢失）
        draftPersistJob?.cancel()
        securePrefs.counselingDraft = _counselingDraft.value
        // S2-02: 统一走 coordinator 关闭，不再逐个 cancel 手里的 Job 字段。
        // 旧写法会漏掉没被字段覆盖的任务（画像刷新、流式刷新定时器）。
        cancelPendingChunkFlush()
        cancelPendingCounselingFlush()
        operationCoordinator.shutdownAll()
    }

    companion object {
        /** ：金额格式化（固定三位小数 + 固定 Locale.US 小数点，防区域格式回归；展示条字号钉死） */
        // 花费保留三位小数
        internal fun formatYuan(yuan: Double): String = String.format(java.util.Locale.US, "%.3f", yuan)

        /**
         * ：今日花费跨天滚动——同日期保留存量，跨天（或无存档）清零。
         * 纯函数，单测覆盖（CostDisplayTest）。
         */
        internal fun rollTodayCost(savedDate: String?, savedYuan: Double?, todayDate: String): Double =
            if (savedDate == todayDate) (savedYuan ?: 0.0) else 0.0
    }
}
