package com.lovebrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.data.CostScope
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.PanelState
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.ProfileSuggestion
import com.lovebrain.app.model.ProfileTransactionResult
import com.lovebrain.app.model.PreconditionReason
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.StageSuggestion
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.util.Jsons
import com.lovebrain.app.util.L
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    private val generationEngine: GenerationEngine
) : ViewModel(), KnowledgeTriggerCoordinator.Callbacks, GenerationEngine.Callbacks {

    private val _panelState = MutableStateFlow(PanelState.KEYBOARD)
    val panelState: StateFlow<PanelState> = _panelState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _result = MutableStateFlow<GenerateResult?>(null)
    val result: StateFlow<GenerateResult?> = _result.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // D项修复：准备期状态——从 guard 通过到 Engine 启动之间
    private val _isPreparing = MutableStateFlow(false)

    private val _isGeneratingCore = MutableStateFlow(false)
    val isGeneratingCore: StateFlow<Boolean> = _isGeneratingCore.asStateFlow()

    // ═══ 可停止生成：持有 Job 供强行停止 ═══
    private var generateJob: kotlinx.coroutines.Job? = null
    private var counselingJob: kotlinx.coroutines.Job? = null
    private var suggestJob: kotlinx.coroutines.Job? = null

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
        val sourceAliasMap: Map<String, String> = emptyMap() // B项修复：别名→实际消息ID映射
    )
    private var replyGenerationContext: ReplyGenerationContext? = null

    private val _streamingCoreText = MutableStateFlow("")
    val streamingCoreText: StateFlow<String> = _streamingCoreText.asStateFlow()

    /** 流式过程中已完整解析出的方案卡（逐张渲染，边收边出） */
    private val _streamingSchemes = MutableStateFlow<List<Scheme>>(emptyList())
    val streamingSchemes: StateFlow<List<Scheme>> = _streamingSchemes.asStateFlow()

    /** P1-07: 流式四方向方案——独立于四风格，同时渲染 */
    private val _streamingDirectionSchemes = MutableStateFlow<List<Scheme>>(emptyList())
    val streamingDirectionSchemes: StateFlow<List<Scheme>> = _streamingDirectionSchemes.asStateFlow()

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

    /** 今日花费的计费日期（跨零点滚动清零用） */
    private var todayCostDate: String = java.time.LocalDate.now().toString()

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

    private val _isSuggesting = MutableStateFlow(false)
    val isSuggesting: StateFlow<Boolean> = _isSuggesting.asStateFlow()

    private val _streamingTips = MutableStateFlow<List<com.lovebrain.app.model.SuggestTip>>(emptyList())
    val streamingTips: StateFlow<List<com.lovebrain.app.model.SuggestTip>> = _streamingTips.asStateFlow()

    /** ：锦囊错误提示（无 KB 引导/弱网超时/解析失败） */
    private val _suggestError = MutableStateFlow<String?>(null)
    val suggestError: StateFlow<String?> = _suggestError.asStateFlow()

    /** ═══════════ 主动发起/润色 ═══════════ */
    private val _proactiveOptions = MutableStateFlow<List<com.lovebrain.app.model.ProactiveOption>>(emptyList())
    val proactiveOptions: StateFlow<List<com.lovebrain.app.model.ProactiveOption>> = _proactiveOptions.asStateFlow()

    private val _isProactive = MutableStateFlow(false)
    val isProactive: StateFlow<Boolean> = _isProactive.asStateFlow()

    private val _proactiveError = MutableStateFlow<String?>(null)
    val proactiveError: StateFlow<String?> = _proactiveError.asStateFlow()

    private var proactiveJob: kotlinx.coroutines.Job? = null

    /** P1-2：结果模式——显式区分回复结果与主动发结果 */
    enum class ResultMode { REPLY, PROACTIVE }
    private val _resultMode = MutableStateFlow(ResultMode.REPLY)
    val resultMode: StateFlow<ResultMode> = _resultMode.asStateFlow()

    /** P1-2：前台任务互斥——同时只运行一个回复/润色/改写请求
     *  D项修复：准备期也参与互斥
     *  阻断B修复：改写也纳入前台互斥 */
    val isForegroundBusy: Boolean get() = _isGenerating.value || _isProactive.value || _isPreparing.value || (rewriteJob?.isActive == true)

    // ═══════════ 谈心模式 ═══════════
    private val _counselingResult = MutableStateFlow<String?>(null)
    val counselingResult: StateFlow<String?> = _counselingResult.asStateFlow()

    private val _counselingError = MutableStateFlow<String?>(null)
    val counselingError: StateFlow<String?> = _counselingError.asStateFlow()

    private val _isCounseling = MutableStateFlow(false)
    val isCounseling: StateFlow<Boolean> = _isCounseling.asStateFlow()

    private val _counselingStreaming = MutableStateFlow("")
    val counselingStreaming: StateFlow<String> = _counselingStreaming.asStateFlow()

    init {
        // F10: 确保至少有一个合法知识库（首次启动创建默认库，重复启动沿用，中断恢复补齐）
        viewModelScope.launch {
            runCatching { knowledgeRepo.ensureInitialKnowledgeBase() }
                .onFailure { L.w("ensureInitialKnowledgeBase failed: ${it::class.simpleName}") }
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
        viewModelScope.launch {
            // 规则 11：后台收集 runCatching 兜底（SharedFlow 收集不应崩面板）
            runCatching {
                deepSeekRepo.costEvents.collect { ev ->
                    val today = java.time.LocalDate.now().toString()
                    if (today != todayCostDate) { todayCostDate = today; _todayCostYuan.value = 0.0 }
                    // PROV-03：今日累计包含全部可计费 AI 请求（前台 + 后台）
                    _todayCostYuan.value += ev.yuan
                    securePrefs.saveTodayCost(today, _todayCostYuan.value)
                    // PROV-03：本次费用只显示前台流式请求（FOREGROUND），不被后台 raw 污染
                    if (ev.scope == CostScope.FOREGROUND) {
                        _lastCostYuan.value = ev.yuan
                    }
                }
            }.onFailure { L.w("计费事件收集异常：${it.javaClass.simpleName}") }
        }

        restoreState()
        
        // : 从 SecurePrefs 读取激活工单信息（面板每次可见时经 refreshTicketState 再刷新）
        refreshTicketState()
    }

    // ═══════════ UI 状态 setters ═══════════

    fun setPanelState(state: PanelState) { _panelState.value = state }
    fun setDraft(text: String) { _draftText.value = text }
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
    }

    fun updateMessage(index: Int, role: ChatMessage.Role, content: String) {
        if (content.isBlank()) return
        val list = _messages.value.toMutableList()
        if (index !in list.indices) return
        list[index] = ChatMessage(id = list[index].id, role = role, content = content.trim())
        _messages.value = list
    }

    fun removeMessage(index: Int) {
        _messages.value = _messages.value.filterIndexed { i, _ -> i != index }
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
     * GEN-01：同步 guard — 正在生成时拒绝启动，绝不覆盖当前 Job 引用。
     * GEN-02：启动前冻结消息快照 + KB，建立 ReplyGenerationContext。
     * GEN-02B：冻结完整 KnowledgeBase 对象传入 Engine，AI prompt 与 nextRound 保存同源。
     * F07/F08: 冻结持续意图和 IDEA 草稿到快照，结果和保存始终使用同一快照。
     * Engine reject → null → 旧 Job 保持 + context 不保存。
     */
    fun generate() {
        // GEN-01 双层保护第一层：ViewModel guard
        // P1-2：前台任务互斥——正在主动发时也拒绝
        // D项修复：准备期也参与互斥——_isPreparing 防止准备期回复/主动发并发
        // 阻断B修复：正在改写时也拒绝
        if (_isGenerating.value || _isProactive.value || _isPreparing.value || (rewriteJob?.isActive == true)) return

        // P1-2：设置结果模式
        _resultMode.value = ResultMode.REPLY

        // D项修复：立即占用准备期所有权，防止准备期并发
        _isPreparing.value = true

        // GEN-02：冻结快照 — 所有本轮上下文同源
        // F08修复：应用编辑草稿的角色变更到统一快照，防双身份
        // 用户编辑消息改角色（如HER→IDEA）但没点保存就生成时，
        // 需要将草稿的角色和内容应用到快照中对应位置的消息
        // GEN-02：冻结快照 — 所有本轮上下文同源
        // 使用唯一快照构建入口，供 chat / 想法 / 来源映射 / 保存共用
        val snapshot = buildMessageSnapshot()
        // F08: IDEA hint 叠入未提交草稿（基于已修正的快照收集）
        val userHint = collectIdeaHintWithDraft(snapshot)
        // GEN-02B：冻结 KB 快照 — AI prompt 和 nextRound 保存使用同一对象
        val kbSnapshot = _activeKb.value
        val kbName = kbSnapshot?.name

        // R08: 异步冻结持续意图和纠正快照，不阻塞主线程
        // 旧代码用 runBlocking 读盘，遇锁等待会卡 UI
        // D项修复：准备期纳入请求生命周期——prepJob 可被 stopGeneration 取消
        val prepJob = viewModelScope.launch {
            // D项修复：检查是否已被取消（快速重复点击时旧请求可能已被新请求取代）
            ensureActive()

            val intentSnapshot = kbName?.let { name ->
                runCatching { withContext(Dispatchers.IO) { knowledgeRepo.readIntent(name) } }.getOrNull()
            } ?: com.lovebrain.app.model.IntentConfig()

            // R07: 原子读取纠正记录和 revision——消除读取纠正和读取 revision 之间的竞态窗口
            val (correctionsSnapshot, correctionsRevision) = kbName?.let { name ->
                runCatching { withContext(Dispatchers.IO) { knowledgeRepo.readCorrectionsAndRevision(name) } }.getOrNull()
            } ?: (emptyMap<String, com.lovebrain.app.model.MemoryCorrection>() to 0)

            // D项修复：再次检查是否已被取消
            ensureActive()

            // D项修复：准备完成，释放 _isPreparing，Engine 的 isGenerating 检查将通过
            _isPreparing.value = false

            // GEN-01 双层保护第二层：Engine 返回 null = reject，不覆盖旧 Job
            val job = generationEngine.generate(snapshot, userHint, kbSnapshot, viewModelScope, this@LoveBrainViewModel, intentSnapshot, correctionsSnapshot)
            if (job != null) {
                generateJob = job
                // GEN-02：context 必须和实际启动成功的 Job 绑定
                replyGenerationContext = ReplyGenerationContext(
                    messages = snapshot,
                    messageIds = snapshot.mapTo(mutableSetOf()) { it.id },
                    kbName = kbName,
                    ideaHint = userHint,
                    intentText = intentSnapshot.text,
                    intentEnabled = intentSnapshot.enabled,
                    intentRevision = intentSnapshot.revision,
                    correctionsRevision = correctionsRevision
                )
                // GEN-01：正常结束后清 Job 引用（identity guard 防止清掉后来的新 Job）
                job.invokeOnCompletion {
                    if (generateJob === job) {
                        generateJob = null
                    }
                }
            }
            // D项修复：如果 Engine reject 或抛异常，_isPreparing 已在上方释放
        }
        // D项修复：将 prepJob 赋给 generateJob，使 stopGeneration 能取消准备期
        generateJob = prepJob
        prepJob.invokeOnCompletion {
            if (generateJob === prepJob) {
                // prepJob 完成但 Engine 未接管 → 清理准备态
                _isPreparing.value = false
            }
        }
    }

    /**
     * GEN-01/GEN-02：停止生成 — 取消真正运行的 Job，清 context，但不清消息。
     */
    fun stopGeneration() {
        // D项修复：停止时也清理准备期状态和 prepJob
        _isPreparing.value = false
        // P1-05: 先取消改写——避免提前 return 导致单独改写时走不到取消代码
        rewriteJob?.cancel()
        rewriteJob = null
        rewriteRequestId = null
        rewriteContextId = null
        _rewriteStates.value = emptyMap()
        _rewriteHistory.value = emptyMap()
        if (!_isGenerating.value && generateJob == null) return
        L.w("user stopped generation")
        generateJob?.cancel()
        generateJob = null
        _isGenerating.value = false
        _isGeneratingCore.value = false
        _streamingCoreText.value = ""
        _streamingSchemes.value = emptyList()
        _streamingDirectionSchemes.value = emptyList()
        _panelState.value = PanelState.KEYBOARD
        // GEN-02：停止生成时清 context（本轮无成功结果），但消息本身不删
        replyGenerationContext = null
        if (_result.value == null) {
            _result.value = GenerateResult.Error("已手动停止生成")
        }
    }

    // ═══════════ 赞踩反馈 ═══════════

    /** P0-1: setFeedback 使用 identityKey 区分 STYLE/DIRECTION */
    fun setFeedback(identityKey: String, feedback: SchemeFeedback) {
        _feedbacks.value = _feedbacks.value.toMutableMap().apply {
            put(identityKey, if (this[identityKey] == feedback) SchemeFeedback.NONE else feedback)
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
        val response = (_result.value as? GenerateResult.Success)?.response ?: return
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
        val feedbackSnapshot = _feedbacks.value.toMap()
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
                // P0-2：提示用户候选未被当作已发送消息（selectedScheme 恒为 null）
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
        _result.value = null
        _feedbacks.value = emptyMap()
        _streamingCoreText.value = ""
        _streamingSchemes.value = emptyList()
        _streamingDirectionSchemes.value = emptyList()
        _panelState.value = PanelState.KEYBOARD
        // 阻断B修复：新轮开始时清理改写状态和历史，作废旧改写请求
        _rewriteStates.value = emptyMap()
        _rewriteHistory.value = emptyMap()
        rewriteRequestId = null
        rewriteContextId = null
    }

    fun copyScheme(scheme: Scheme): String {
        return scheme.reply
    }

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
        profileRegenerationJob?.cancel()
        profileRegenerationJob = null
        profileRegenerationRequestId++
        _profileRegenerating.value = false
        _profileSuggestion.value = null
    }

    /**
     * 画像重新生成——原地显示 loading，卡片位置不变。
     *
     * 修复 P0-8：所有失败路径（EMPTY/PROVIDER_ERROR/EXCEPTION）都必须复位 loading。
     * 修复 P0-9：增加 request identity——dismiss 后旧请求回来不会复活卡片。
     */
    private val _profileRegenerating = MutableStateFlow(false)
    val profileRegenerating: StateFlow<Boolean> = _profileRegenerating.asStateFlow()

    /** 重新生成的协程 job——dismiss 时 cancel */
    private var profileRegenerationJob: kotlinx.coroutines.Job? = null

    /** 重新生成请求 ID——每次 dismiss 递增，旧请求回来时 requestId 不匹配则丢弃 */
    private var profileRegenerationRequestId: Int = 0

    fun regenerateProfileUpdate() {
        val suggestion = _profileSuggestion.value ?: return
        val kbName = suggestion.kbName

        // 取消上一次未完成的重新生成
        profileRegenerationJob?.cancel()
        val currentRequestId = ++profileRegenerationRequestId

        _profileRegenerating.value = true
        // P0-2: ViewModel 持有真实生成 Job——Coordinator 提供 suspend operation，
        // 禁止 fire-and-forget 嵌套 launch。
        // profileRegenerating=true 从请求开始持续到真实任务 terminal state。
        profileRegenerationJob = viewModelScope.launch {
            try {
                // 直接 await suspend function——真正的模型请求在此协程内运行
                triggerCoordinator.regenerateProfile(kbName, viewModelScope, object : KnowledgeTriggerCoordinator.Callbacks {
                    override fun onVectorUpdated(kbName: String, newVector: Map<String, Int>, delta: Map<String, Int>) {}
                    override fun onVectorUpdateNotice(kbName: String, summary: String) {}
                    override fun onStageSuggestion(suggestion: StageSuggestion) {}
                    override fun onKbNotice(notice: String) {
                        // EMPTY / 失败路径——也要复位 loading（如果 requestId 匹配）
                        if (currentRequestId == profileRegenerationRequestId) {
                            _profileRegenerating.value = false
                        }
                    }
                    override fun onProfileSuggestion(suggestion: ProfileSuggestion) {
                        // 成功或失败建议——只有 requestId 匹配才更新 UI
                        if (currentRequestId == profileRegenerationRequestId) {
                            _profileSuggestion.value = suggestion
                            _profileRegenerating.value = false
                        }
                    }
                    override fun onCurrentVector(kbName: String, vector: Map<String, Int>) {}
                })
                // regenerateProfile 是 suspend——它会等到 generateReflectSuggestion 完成后才返回
            } catch (e: kotlinx.coroutines.CancellationException) {
                // dismiss cancel——不设 error，只复位 loading
                if (currentRequestId == profileRegenerationRequestId) {
                    _profileRegenerating.value = false
                }
                throw e
            } catch (e: Exception) {
                L.e("regenerateProfileUpdate failed", e)
                if (currentRequestId == profileRegenerationRequestId) {
                    _profileRegenerating.value = false
                }
            } finally {
                // P0-2: 只有 requestId 匹配时才复位——防止晚到 callback 复活
                if (currentRequestId == profileRegenerationRequestId) {
                    _profileRegenerating.value = false
                }
            }
        }
    }

    // ═══════════ 谈心模式（委托 GenerationEngine） ═══════════

    /**
     * GEN-01：同步 guard — 正在谈心时拒绝启动。Engine reject → null → 旧 Job 保持。
     * COUN-01：冻结 KB 快照传入 Engine，谈心期间切 KB 不影响 prompt 与日志目标。
     */
    fun generateCounseling(userMessage: String) {
        if (userMessage.isBlank()) return
        if (_isCounseling.value) return
        // COUN-01：冻结 KB 快照
        val kbSnapshot = _activeKb.value
        val job = generationEngine.generateCounseling(userMessage, kbSnapshot, viewModelScope, this)
        if (job != null) {
            counselingJob = job
            job.invokeOnCompletion {
                if (counselingJob === job) {
                    counselingJob = null
                }
            }
        }
    }

    fun stopCounseling() {
        if (!_isCounseling.value) return
        L.w("user stopped counseling")
        counselingJob?.cancel()
        counselingJob = null
        _isCounseling.value = false
        _counselingStreaming.value = ""
        if (_counselingResult.value == null) {
            _counselingError.value = "已手动停止"
        }
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

        runCatching {
            withContext(Dispatchers.IO) {
                knowledgeRepo.appendCounselingEntries(kbName, recordEntry, analysisEntry)
            }
        }.onFailure { L.w("saveCounselingLog failed: ${it.javaClass.simpleName}") }
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
        _isCounseling.value = false
        securePrefs.clearCounselingResult()
        // ：先取消防抖尾再写空，防"清空后旧草稿被防抖任务写回"复活竞态
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
            runCatching {
                // KBUI-01：切库时清理上一 KB 的瞬时 vector UI（delta / update / notice）
                val oldKbName = _activeKb.value?.name
                val newKb = knowledgeRepo.getActive()
                if (oldKbName != newKb?.name) {
                    _vectorDelta.value = emptyMap()
                    _vectorUpdate.value = null
                    _kbNotice.value = null
                }
                _activeKb.value = newKb
                newKb?.let {
                    knowledgeRepo.migrateIfNeeded(it.name)
                    _currentVector.value = knowledgeRepo.readVector(it.name)
                    // F07: 切库时刷新持续意图配置
                    _intentConfig.value = knowledgeRepo.readIntent(it.name)
                }
                // CARRY-09：删除最后一个 KB 时 newKb==null，旧 _currentVector 未被清空
                if (newKb == null) {
                    _currentVector.value = emptyMap()
                }
            }.onFailure { L.w("refreshKnowledgeBases failed: ${it::class.simpleName}") }
        }
    }

    // ═══════════ 今日锦囊（委托 GenerationEngine） ═══════════

    fun openPlanPanel() { _showPlanPanel.value = true }
    fun dismissPlanPanel() { _showPlanPanel.value = false }

    /** GEN-01：同步 guard — 正在生成锦囊时拒绝启动。Engine reject → null → 旧 Job 保持。 */
    fun generateSuggest() {
        if (_isSuggesting.value) return
        val job = generationEngine.generateSuggest(viewModelScope, this)
        if (job != null) {
            suggestJob = job
            job.invokeOnCompletion {
                if (suggestJob === job) {
                    suggestJob = null
                }
            }
        }
    }

    fun stopSuggest() {
        if (!_isSuggesting.value) return
        L.w("user stopped suggest")
        suggestJob?.cancel()
        suggestJob = null
        _isSuggesting.value = false
        _streamingTips.value = emptyList()
        // ：对齐谈心停止先例（_counselingError="已手动停止"）——复用既有 _suggestError 通道，零新状态
        _suggestError.value = "已手动停止"
    }

    // ═══════════ 主动发起/润色（委托 GenerationEngine） ═══════════

    /** GEN-01：同步 guard — 正在主动发时拒绝启动。Engine reject → null → 旧 Job 保持。 */
    /** P1-2：前台任务互斥——正在生成回复时也拒绝 */
    fun generateProactive(draft: String = "", scene: String = "") {
        // D项修复：准备期也参与互斥
        // 阻断B修复：正在改写时也拒绝
        if (_isProactive.value || _isGenerating.value || _isPreparing.value || (rewriteJob?.isActive == true)) return

        // P1-2：设置结果模式
        _resultMode.value = ResultMode.PROACTIVE

        val job = generationEngine.generateProactive(draft, scene, viewModelScope, this)
        if (job != null) {
            proactiveJob = job
            job.invokeOnCompletion {
                if (proactiveJob === job) {
                    proactiveJob = null
                }
            }
        }
    }

    fun stopProactive() {
        if (!_isProactive.value) return
        proactiveJob?.cancel()
        proactiveJob = null
        _isProactive.value = false
    }

    fun clearProactive() {
        _proactiveOptions.value = emptyList()
        _proactiveError.value = null
    }

    // ═══════════ GenerationEngine.Callbacks 实现 ═══════════

    /** ：首字耗时上报（四流程统一回调，展示条消费） */
    override fun onFirstToken(elapsedMs: Long) {
        _lastResponseMs.value = elapsedMs
    }

    // --- 回复生成 ---
    override fun onReplyStart() {
        _isGenerating.value = true
        _isGeneratingCore.value = true
        _panelState.value = PanelState.AI_LOADING
        _result.value = null
        _streamingCoreText.value = ""
        _streamingSchemes.value = emptyList()
        _streamingDirectionSchemes.value = emptyList()
        _feedbacks.value = emptyMap()
    }

    override fun onReplyStreamingCoreText(chunk: String) {
        _streamingCoreText.value += chunk
    }

    override fun onReplyStreamingSchemes(schemes: List<Scheme>) {
        // F09-7: 固定四方向后不再用 size 增长判断，改用内容差异（reply 变长时更新）
        val current = _streamingSchemes.value
        if (schemes.size > current.size) {
            _streamingSchemes.value = schemes
        } else if (schemes.size == current.size && schemes != current) {
            // 内容有变化（某方向从空变非空，或文本增长）
            _streamingSchemes.value = schemes
        }
    }

    /** GEN-04：retry 前清理上一次 attempt 的流式方案卡 */
    override fun onReplyStreamingSchemesReset() {
        _streamingSchemes.value = emptyList()
        _streamingDirectionSchemes.value = emptyList()
    }

    /** P1-07: 四方向独立回调 */
    override fun onReplyStreamingDirectionSchemes(schemes: List<Scheme>) {
        val current = _streamingDirectionSchemes.value
        if (schemes.size > current.size) {
            _streamingDirectionSchemes.value = schemes
        } else if (schemes.size == current.size && schemes != current) {
            _streamingDirectionSchemes.value = schemes
        }
    }

    override fun onReplyResult(result: GenerateResult) {
        _result.value = result
    }

    override fun onReplyPanelState(state: PanelState) {
        _panelState.value = state
    }

    override fun onReplyGenerating(isGenerating: Boolean, isGeneratingCore: Boolean) {
        _isGenerating.value = isGenerating
        _isGeneratingCore.value = isGeneratingCore
    }

    override fun onReplyStreamingCoreTextReset() {
        _streamingCoreText.value = ""
    }

    // F09: 回报本轮注入的 MemoryRef 清单 — 冻结到 ReplyGenerationContext
    override fun onReplyMemoryRefs(refs: List<com.lovebrain.app.model.MemoryRef>) {
        val ctx = replyGenerationContext
        if (ctx != null) {
            replyGenerationContext = ctx.copy(memoryRefs = refs)
        }
    }

    // B项修复：回报来源别名映射 — 冻结到 ReplyGenerationContext
    override fun onReplySourceAliasMap(aliasMap: Map<String, String>) {
        val ctx = replyGenerationContext
        if (ctx != null) {
            replyGenerationContext = ctx.copy(sourceAliasMap = aliasMap)
        }
    }

    // --- 谈心 ---
    override fun onCounselingStart() {
        _isCounseling.value = true
        _counselingResult.value = null
        _counselingError.value = null
        _counselingStreaming.value = ""
    }

    override fun onCounselingStreaming(chunk: String) {
        _counselingStreaming.value += chunk
    }

    override fun onCounselingResult(text: String) {
        _counselingResult.value = text
        securePrefs.saveCounselingResult(text)
    }

    override fun onCounselingError(error: String) {
        _counselingError.value = error
    }

    override fun onCounselingEnd() {
        _counselingStreaming.value = ""
        _isCounseling.value = false
    }

    // COUN-01：日志目标使用 Engine 传入的冻结 kbName，不读 _activeKb
    override fun onCounselingSaveLog(
        kbName: String?,
        userMessage: String,
        replyText: String,
        analysisText: String
    ) {
        viewModelScope.launch {
            saveCounselingLog(kbName, userMessage, replyText, analysisText)
        }
    }

    // --- 锦囊 ---
    override fun onSuggestStart() {
        _isSuggesting.value = true
        _suggestion.value = null
        _streamingTips.value = emptyList()
        _suggestError.value = null
    }

    override fun onSuggestStreamingTips(tips: List<com.lovebrain.app.model.SuggestTip>) {
        if (tips.size > _streamingTips.value.size) _streamingTips.value = tips
    }

    override fun onSuggestResult(suggestion: com.lovebrain.app.model.DailySuggestion?) {
        _suggestion.value = suggestion
        // ：锦囊生成成功 → 持久化（json + 当天日期），杀进程当天重开可恢复
        if (suggestion != null) {
            securePrefs.saveSuggestion(
                Json.encodeToString(serializer<com.lovebrain.app.model.DailySuggestion>(), suggestion),
                TimeFmt.today()
            )
        }
    }

    override fun onSuggestEnd() {
        _streamingTips.value = emptyList()
        _isSuggesting.value = false
    }

    override fun onSuggestLog(msg: String) {
        L.w(msg)
    }

    override fun onSuggestError(msg: String) {
        _suggestError.value = msg
    }

    // --- 主动发起 ---
    override fun onProactiveStart() {
        _isProactive.value = true
        _proactiveOptions.value = emptyList()
        _proactiveError.value = null
    }

    override fun onProactiveStreamingOptions(options: List<com.lovebrain.app.model.ProactiveOption>) {
        if (options.size > _proactiveOptions.value.size) _proactiveOptions.value = options
    }

    override fun onProactiveError(error: String) {
        _proactiveError.value = error
    }

    override fun onProactiveEnd() {
        if (_proactiveOptions.value.isEmpty() && _proactiveError.value == null) {
            _proactiveError.value = "未生成可用开场，请补充草稿或场景"
        }
        _isProactive.value = false
    }

    // --- 共用 ---
    override fun getActiveKb(): KnowledgeBase? = _activeKb.value
    override fun getMessages(): List<ChatMessage> = _messages.value
    // GEN-02：collectIdeaHint 改为纯函数，接受 messages 参数 — 同一快照同源收集，不读实时状态
    // （lifecycle 契约由 LoveBrainViewModelIdeaHintTest ④ 锁定，getUserHint 仍读实时列表保持兼容）
    private fun collectIdeaHint(messages: List<ChatMessage>): String =
        messages.filter { it.role == ChatMessage.Role.IDEA }.joinToString("\n") { it.content }
    private fun collectIdeaHint(): String = collectIdeaHint(_messages.value)
    override fun getUserHint(): String = collectIdeaHint()

    // F08: 收集 IDEA hint，包含未提交草稿。
    // F08修复：generate() 已将编辑草稿应用到快照，此函数只需从快照收集 IDEA 消息。
    // 但当 ideaComposeMode 为 true 且不是编辑已有消息时（新建 IDEA 草稿未提交），
    // 仍需将草稿作为新 IDEA 加入。
    // - HER/ME 未提交草稿不自动成为真实消息
    private fun collectIdeaHintWithDraft(messages: List<ChatMessage>): String {
        val addedIdeas = messages.filter { it.role == ChatMessage.Role.IDEA }
            .joinToString("\n") { it.content }
            .trim()

        // F08修复：如果编辑草稿已应用到快照（editingIdx >= 0），不需要再额外收集
        val editingIdx = _editingIndex.value
        if (editingIdx >= 0 && editingIdx < messages.size) {
            return addedIdeas
        }

        // F08: 当前角色是 IDEA 且草稿非空且不是编辑已有消息 → 草稿是新的 IDEA
        val draft = _draftText.value.trim()
        if (draft.isBlank()) return addedIdeas
        if (_ideaComposeMode.value) {
            return if (addedIdeas.isBlank()) draft else "$addedIdeas\n$draft"
        }

        // HER/ME 草稿不加入 IDEA hint
        return addedIdeas
    }
    override fun isGenerating(): Boolean = _isGenerating.value
    override fun isCounseling(): Boolean = _isCounseling.value
    override fun isSuggesting(): Boolean = _isSuggesting.value
    override fun isProactive(): Boolean = _isProactive.value
    override fun getOutputMode(): Int = _outputMode.value

    // ═══════════ F07: 持续意图 UI 状态 ═══════════

    /** F07: 当前 KB 的持续意图配置（面板 chip 展示 + 编辑入口） */
    private val _intentConfig = MutableStateFlow(com.lovebrain.app.model.IntentConfig())
    val intentConfig: StateFlow<com.lovebrain.app.model.IntentConfig> = _intentConfig.asStateFlow()

    /** F07: 持续意图编辑面板可见性 */
    private val _showIntentEditor = MutableStateFlow(false)
    val showIntentEditor: StateFlow<Boolean> = _showIntentEditor.asStateFlow()

    /** F07: 刷新持续意图配置（切库/面板可见时调用） */
    fun refreshIntentConfig() {
        val kbName = _activeKb.value?.name ?: return
        viewModelScope.launch {
            _intentConfig.value = withContext(Dispatchers.IO) {
                knowledgeRepo.readIntent(kbName)
            }
        }
    }

    /** R08: 保存持续意图配置。绑定编辑时冻结的 KB，不读当前 active KB。
     * 持久化失败保留编辑状态并提示。旧请求因 revision 变化而作废。 */
    fun saveIntent(text: String, enabled: Boolean) {
        // R08: 绑定编辑器打开时的 KB，不读当前 active KB
        val kbName = intentEditorKbName ?: _activeKb.value?.name ?: return
        viewModelScope.launch {
            runCatching {
                val updated = withContext(Dispatchers.IO) {
                    knowledgeRepo.saveIntent(kbName, text, enabled)
                }
                _intentConfig.value = updated
                _kbNotice.value = if (enabled) "持续意图已开启" else "持续意图已关闭"
                // R08: 保存成功后关闭编辑器
                _showIntentEditor.value = false
            }.onFailure {
                // R08: 持久化失败保留编辑状态并提示
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
        targetKbId: String = ""
    ) {
        val ctx = replyGenerationContext ?: return
        val kbName = ctx.kbName ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                knowledgeRepo.saveCorrection(kbName, memoryId, action, replacementText, targetKbId)
            }
            if (success) {
                _kbNotice.value = "已记录纠正，下次生成将过滤此条记忆"
            } else {
                _kbNotice.value = "纠正保存失败，请重试"
            }
        }
    }

    /**
     * F09: 撤销纠正 — 删除指定 memoryId 的纠正记录。
     * 撤销后该记忆恢复可信注入资格。绑定生成时冻结的 KB。
     */
    fun undoMemoryCorrection(memoryId: String) {
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

    /** DRY: 改写操作选项文案统一使用 RewriteCommand.ALL_LABELS，不在 VM 重复定义 */
    val rewriteOptions: List<String> get() = com.lovebrain.app.model.RewriteCommand.ALL_LABELS

    /** 单条改写状态：identityKey → 改写状态（P0-1: 使用 SchemeIdentity.key 区分 STYLE/DIRECTION） */
    private val _rewriteStates = MutableStateFlow<Map<String, RewriteState>>(emptyMap())
    val rewriteStates: StateFlow<Map<String, RewriteState>> = _rewriteStates.asStateFlow()

    /** 单条改写版本历史（用于撤销）—— P1-04: 保存正文+反馈，撤销时一并恢复 */
    private data class RewriteVersion(val reply: String, val feedback: SchemeFeedback)
    private val _rewriteHistory = MutableStateFlow<Map<String, List<RewriteVersion>>>(emptyMap())

    /** 改写任务 Job——与前台生成共用任务管理 */
    private var rewriteJob: kotlinx.coroutines.Job? = null

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
        // 前台互斥：正在生成/主动发/改写中时拒绝
        if (_isGenerating.value || _isProactive.value || _isPreparing.value) return
        if (rewriteJob?.isActive == true) return

        // 首轮流式尚未完成时禁用改写
        if (_isGeneratingCore.value) return

        val result = _result.value as? GenerateResult.Success ?: return
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

        // 阻断B修复：绑定轮次身份——新轮/切库/保存后旧改写不写入
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

        val kbSnapshot = _activeKb.value
        val ticket = _activeTicket.value
        val apiKey = ticket?.let { securePrefs.getWorkerApiKey(it.id) }

        rewriteJob = viewModelScope.launch {
            try {
                // 构建短请求——只包含最少必要上下文
                val systemPrompt = buildRewriteSystemPrompt()
                val userPrompt = buildRewriteUserPrompt(
                    originalReply = scheme.reply,
                    option = option,
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
                if (rewriteRequestId != requestId) return@launch
                // 阻断B修复：校验轮次身份未变
                val currentContextId = (_activeKb.value?.name ?: "") + "_" + (replyGenerationContext?.messageIds?.hashCode() ?: 0)
                if (rewriteContextId != contextId || currentContextId != contextId) return@launch

                val newReply = raw.trim()
                if (newReply.isBlank()) {
                    _rewriteStates.value = _rewriteStates.value + (identityKey to RewriteState.Error("改写返回空结果"))
                    return@launch
                }

                // 阻断B修复：只替换目标卡正文，不回写整个捕获的旧 response
                // P0-1: 根据 source 更新对应方案列表
                val currentResult = _result.value as? GenerateResult.Success ?: return@launch
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
                _result.value = GenerateResult.Success(updatedResponse)

                // P1-04: 改写成功后——新正文独立 NONE，不自动继承旧赞/踩
                // 旧赞保留在 rewriteHistory 中，撤销时恢复
                _feedbacks.value = _feedbacks.value.toMutableMap().apply {
                    put(identityKey, SchemeFeedback.NONE)
                }

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
        rewriteJob?.cancel()
        rewriteJob = null
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

        val result = _result.value as? GenerateResult.Success ?: return
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
        _result.value = GenerateResult.Success(updatedResponse)
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
    }

    /** 构建改写用户提示——只包含最少必要上下文 */
    private fun buildRewriteUserPrompt(
        originalReply: String,
        option: String,
        messages: List<ChatMessage>,
        intentText: String?,
        ideaHint: String?
    ): String = buildString {
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
        generateJob?.cancel()
        counselingJob?.cancel()
        suggestJob?.cancel()
        proactiveJob?.cancel()
        rewriteJob?.cancel()
    }

    companion object {
        /** ：金额格式化（固定三位小数 + 固定 Locale.US 小数点，防区域格式回归；展示条字号钉死） */
        // 主人纠正（2026-08-30）：今日/本次花费保留三位小数（原 Q6 两位小数口径作废，原话：保留三位小数）
        internal fun formatYuan(yuan: Double): String = String.format(java.util.Locale.US, "%.3f", yuan)

        /**
         * ：今日花费跨天滚动——同日期保留存量，跨天（或无存档）清零。
         * 纯函数，单测覆盖（CostDisplayTest）。
         */
        internal fun rollTodayCost(savedDate: String?, savedYuan: Double?, todayDate: String): Double =
            if (savedDate == todayDate) (savedYuan ?: 0.0) else 0.0
    }
}
