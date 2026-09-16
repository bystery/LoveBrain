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
import com.lovebrain.app.model.StageSuggestion
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.util.Jsons
import com.lovebrain.app.util.L
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        val correctionsRevision: Int = 0   // F09: 冻结的纠正 revision（防迟到覆盖）
    )
    private var replyGenerationContext: ReplyGenerationContext? = null

    private val _streamingCoreText = MutableStateFlow("")
    val streamingCoreText: StateFlow<String> = _streamingCoreText.asStateFlow()

    /** 流式过程中已完整解析出的方案卡（逐张渲染，边收边出） */
    private val _streamingSchemes = MutableStateFlow<List<Scheme>>(emptyList())
    val streamingSchemes: StateFlow<List<Scheme>> = _streamingSchemes.asStateFlow()

    private val _activeKb = MutableStateFlow<KnowledgeBase?>(null)
    val activeKb: StateFlow<KnowledgeBase?> = _activeKb.asStateFlow()

    //  KBG-02：ProfileSuggestion 作为单一事实源，携带 originating kbName
    private val _profileSuggestion = MutableStateFlow<ProfileSuggestion?>(null)
    val profileSuggestion: StateFlow<ProfileSuggestion?> = _profileSuggestion.asStateFlow()

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

    /** P1-2：前台任务互斥——同时只运行一个回复/润色请求 */
    val isForegroundBusy: Boolean get() = _isGenerating.value || _isProactive.value

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
        if (_isGenerating.value || _isProactive.value) return

        // P1-2：设置结果模式
        _resultMode.value = ResultMode.REPLY

        // GEN-02：冻结快照 — 所有本轮上下文同源
        val snapshot = _messages.value.map { it.copy() }
        // F08: IDEA hint 疉入未提交草稿
        val userHint = collectIdeaHintWithDraft(snapshot)
        // GEN-02B：冻结 KB 快照 — AI prompt 和 nextRound 保存使用同一对象
        val kbSnapshot = _activeKb.value
        val kbName = kbSnapshot?.name

        // R08: 异步冻结持续意图和纠正快照，不阻塞主线程
        // 旧代码用 runBlocking 读盘，遇锁等待会卡 UI
        viewModelScope.launch {
            val intentSnapshot = kbName?.let { name ->
                runCatching { withContext(Dispatchers.IO) { knowledgeRepo.readIntent(name) } }.getOrNull()
            } ?: com.lovebrain.app.model.IntentConfig()

            val correctionsSnapshot = kbName?.let { name ->
                runCatching { withContext(Dispatchers.IO) { knowledgeRepo.readCorrections(name) } }.getOrNull()
            } ?: emptyMap()
            val correctionsRevision = kbName?.let { name ->
                runCatching { withContext(Dispatchers.IO) { knowledgeRepo.getCorrectionsRevision(name) } }.getOrNull()
            } ?: 0

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
        }
    }

    /**
     * GEN-01/GEN-02：停止生成 — 取消真正运行的 Job，清 context，但不清消息。
     */
    fun stopGeneration() {
        if (!_isGenerating.value) return
        L.w("user stopped generation")
        generateJob?.cancel()
        generateJob = null
        _isGenerating.value = false
        _isGeneratingCore.value = false
        _streamingCoreText.value = ""
        _streamingSchemes.value = emptyList()
        _panelState.value = PanelState.KEYBOARD
        // GEN-02：停止生成时清 context（本轮无成功结果），但消息本身不删
        replyGenerationContext = null
        if (_result.value == null) {
            _result.value = GenerateResult.Error("已手动停止生成")
        }
    }

    // ═══════════ 赞踩反馈 ═══════════

    fun setFeedback(tag: String, feedback: SchemeFeedback) {
        _feedbacks.value = _feedbacks.value.toMutableMap().apply {
            put(tag, if (this[tag] == feedback) SchemeFeedback.NONE else feedback)
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

        val likedSchemes = response.schemes
            .filter { _feedbacks.value[it.tag] == SchemeFeedback.LIKED }
            .sortedBy { "ABCD".indexOf(it.tag) }

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
                        likedSchemes = likedForRecording
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
        _panelState.value = PanelState.KEYBOARD
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
    fun confirmProfileUpdate() {
        val suggestion = _profileSuggestion.value ?: return
        val rawJson = suggestion.rawJson

        //  ：先解析后清卡——解析失败保留卡片 + 弱警告（可重试），成功才清卡写库
        val parsed = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(rawJson).jsonObject
        }.getOrNull()
        if (parsed == null) {
            showPanelWarning("建议解析失败，可重试或忽略")
            return
        }

        val kbName = suggestion.kbName

        viewModelScope.launch {
            //  KBG-01+KBG-02 交汇：如果建议所属 KB 已被删除 → 不写盘、不复活、清卡 + 提示
            val exists = withContext(Dispatchers.IO) {
                knowledgeRepo.listAll().any { it.name == kbName }
            }
            if (!exists) {
                _profileSuggestion.value = null
                showPanelWarning("原知识库已删除，这条画像建议已失效")
                return@launch
            }

            _profileSuggestion.value = null

            val meContent = parsed["me"]?.jsonPrimitive?.content
            val herContent = parsed["her"]?.jsonPrimitive?.content
            val warmthContent = parsed["warmth"]?.jsonPrimitive?.content
            val stageChanged = parsed["stage_changed"]?.jsonPrimitive?.boolean ?: false
            val newStage = parsed["new_stage"]?.jsonPrimitive?.content

            withContext(Dispatchers.IO) {
                if (!meContent.isNullOrBlank()) {
                    knowledgeRepo.writeFile(kbName, "understand/me.md", meContent)
                }
                if (!herContent.isNullOrBlank()) {
                    knowledgeRepo.writeFile(kbName, "understand/her.md", herContent)
                }
                if (!warmthContent.isNullOrBlank()) {
                    knowledgeRepo.writeFile(kbName, "understand/warmth.md", warmthContent)
                    //  KBG-03：画像确认时读取目标 KB 自己的 vector，不使用 UI 全局 _currentVector
                    val targetVector = knowledgeRepo.readVector(kbName)
                    if (targetVector.isNotEmpty()) {
                        knowledgeRepo.writeVector(kbName, targetVector)
                    }
                }
                if (stageChanged && !newStage.isNullOrBlank()) {
                    knowledgeRepo.updateStage(kbName, newStage)
                }
            }

            _kbNotice.value = "已更新知识库「$kbName」的画像"
            refreshKnowledgeBases()
        }
    }

    fun dismissProfileUpdate() {
        _profileSuggestion.value = null
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
        if (_isProactive.value || _isGenerating.value) return

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
    // - 当前角色是 IDEA 且草稿非空时，草稿加入本轮 hint
    // - 正在编辑已有 IDEA 时，用草稿替换对应内容（旧新不重复）
    // - HER/ME 未提交草稿不自动成为真实消息
    private fun collectIdeaHintWithDraft(messages: List<ChatMessage>): String {
        val addedIdeas = messages.filter { it.role == ChatMessage.Role.IDEA }
            .joinToString("\n") { it.content }
            .trim()

        // F08: 如果当前是 IDEA 模式且有未提交草稿，且正在编辑已有 IDEA，
        // 用草稿替换对应位置的旧 IDEA（避免旧新重复）
        val draft = _draftText.value.trim()
        if (draft.isBlank()) return addedIdeas

        val editingIdx = _editingIndex.value
        if (_ideaComposeMode.value && editingIdx >= 0 && editingIdx < messages.size) {
            // 正在编辑已有 IDEA：用草稿替换编辑位置的内容
            val ideaMessages = messages.filterIndexed { i, msg ->
                msg.role == ChatMessage.Role.IDEA && i != editingIdx
            }
            val replaced = ideaMessages.joinToString("\n") { it.content }.trim()
            return if (replaced.isBlank()) draft else "$replaced\n$draft"
        }

        // F08: 当前角色是 IDEA 且草稿非空但不是编辑已有 IDEA → 草稿是新的 IDEA
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
