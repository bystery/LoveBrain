package com.lovebrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _streamingCoreText = MutableStateFlow("")
    val streamingCoreText: StateFlow<String> = _streamingCoreText.asStateFlow()

    /** 流式过程中已完整解析出的方案卡（逐张渲染，边收边出） */
    private val _streamingSchemes = MutableStateFlow<List<Scheme>>(emptyList())
    val streamingSchemes: StateFlow<List<Scheme>> = _streamingSchemes.asStateFlow()

    private val _activeKb = MutableStateFlow<KnowledgeBase?>(null)
    val activeKb: StateFlow<KnowledgeBase?> = _activeKb.asStateFlow()

    private val _profileSuggestion = MutableStateFlow<String?>(null)
    val profileSuggestion: StateFlow<String?> = _profileSuggestion.asStateFlow()
    private var profileRawJson: String? = null

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
        refreshKnowledgeBases()
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
                    _todayCostYuan.value += ev.yuan
                    securePrefs.saveTodayCost(today, _todayCostYuan.value)
                    // ：后台引擎/建库的用量只计入今日累计，不刷新"本次"（仅面板四流程内刷新）
                    if (_isGenerating.value || _isCounseling.value || _isSuggesting.value || _isProactive.value) {
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

    fun generate() {
        generateJob = generationEngine.generate(viewModelScope, this)
    }

    fun stopGeneration() {
        if (!_isGenerating.value) return
        L.w("user stopped generation")
        generateJob?.cancel()
        generateJob = null
        _isGenerating.value = false
        _isGeneratingCore.value = false
        _streamingCoreText.value = ""
        _panelState.value = PanelState.KEYBOARD
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

    fun nextRound() {
        val response = (_result.value as? GenerateResult.Success)?.response ?: return
        if (recordingRound) return
        recordingRound = true
        val msgs = _messages.value
        val kb = _activeKb.value

        val likedSchemes = response.schemes
            .filter { _feedbacks.value[it.tag] == SchemeFeedback.LIKED }
            .sortedBy { "ABCD".indexOf(it.tag) }
        val selectedScheme = when {
            likedSchemes.size > 1 -> Scheme(
                tag = likedSchemes.joinToString("+") { it.tag },
                title = likedSchemes.joinToString("+") { "方案${it.tag}-${it.title}" },
                reply = likedSchemes.joinToString("\n") { it.reply }
            )
            likedSchemes.size == 1 -> likedSchemes[0]
            else -> response.schemes.firstOrNull { it.tag == "A" } ?: response.schemes.firstOrNull()
        }

        _messages.value = emptyList()
        _result.value = null
        _feedbacks.value = emptyMap()
        _panelState.value = PanelState.KEYBOARD

        if (kb != null && msgs.isNotEmpty() && selectedScheme != null) {
            val analysis = response.analysis
            viewModelScope.launch {
                try {
                    val t7Start = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        val topicRotated = topicRecorder.record(
                            kb, msgs, selectedScheme, analysis.topic_status, analysis.topic_label,
                            // （-⑨ 主控裁定）：userHint 实参传 ""——记录段已含《想法》行（msgs 经 role.label 渲染），
                            // 不再重复写"我的想法"段；domain 形参零触碰
                            analysis.scene_facts, "", analysis.ongoing
                        )
                        if (topicRotated) {
                            triggerCoordinator.checkTriggers(kb.name, viewModelScope, this@LoveBrainViewModel)
                        }
                    }
                    L.w("PERF t7 kb write done (${System.currentTimeMillis() - t7Start}ms)")
                    refreshKnowledgeBases()
                } catch (t: Throwable) {
                    L.e("nextRound record failed", t)
                } finally {
                    recordingRound = false
                }
            }
        } else {
            // ：未激活知识库时明示"未记入"，不让用户误以为对话已保存
            if (kb == null && msgs.isNotEmpty()) {
                showPanelWarning("未激活知识库，本轮对话未记入")
            }
            refreshKnowledgeBases()
            recordingRound = false
        }
    }

    fun copyScheme(scheme: Scheme): String {
        return scheme.reply
    }

    // ═══════════ KnowledgeTriggerCoordinator.Callbacks 实现 ═══════════

    override fun onVectorUpdated(newVector: Map<String, Int>, delta: Map<String, Int>) {
        _currentVector.value = newVector
        _vectorDelta.value = delta
    }

    override fun onVectorUpdateNotice(summary: String) {
        _vectorUpdate.value = summary
    }

    override fun onStageSuggestion(suggestion: StageSuggestion) {
        _stageSuggestion.value = suggestion
    }

    override fun onKbNotice(notice: String) {
        _kbNotice.value = notice
    }

    override fun onProfileSuggestion(display: String, rawJson: String) {
        _profileSuggestion.value = display.ifBlank { "画像更新建议已生成，点击确认写入。" }
        profileRawJson = rawJson
    }

    override fun onCurrentVector(vector: Map<String, Int>) {
        _currentVector.value = vector
    }

    fun confirmProfileUpdate() {
        val kb = _activeKb.value ?: return
        val rawJson = profileRawJson ?: return

        //  ：先解析后清卡——解析失败保留卡片 + 弱警告（可重试），成功才清卡写库
        val parsed = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(rawJson).jsonObject
        }.getOrNull()
        if (parsed == null) {
            showPanelWarning("建议解析失败，可重试或忽略")
            return
        }
        _profileSuggestion.value = null
        profileRawJson = null

        viewModelScope.launch {
            val meContent = parsed["me"]?.jsonPrimitive?.content
            val herContent = parsed["her"]?.jsonPrimitive?.content
            val warmthContent = parsed["warmth"]?.jsonPrimitive?.content
            val stageChanged = parsed["stage_changed"]?.jsonPrimitive?.boolean ?: false
            val newStage = parsed["new_stage"]?.jsonPrimitive?.content

            withContext(Dispatchers.IO) {
                if (!meContent.isNullOrBlank()) {
                    knowledgeRepo.writeFile(kb.name, "understand/me.md", meContent)
                }
                if (!herContent.isNullOrBlank()) {
                    knowledgeRepo.writeFile(kb.name, "understand/her.md", herContent)
                }
                if (!warmthContent.isNullOrBlank()) {
                    knowledgeRepo.writeFile(kb.name, "understand/warmth.md", warmthContent)
                    val currentVec = _currentVector.value
                    if (currentVec.isNotEmpty()) {
                        knowledgeRepo.writeVector(kb.name, currentVec)
                    }
                }
                if (stageChanged && !newStage.isNullOrBlank()) {
                    knowledgeRepo.updateStage(kb.name, newStage)
                }
            }

            _kbNotice.value = "画像已更新"
            refreshKnowledgeBases()
        }
    }

    fun dismissProfileUpdate() {
        _profileSuggestion.value = null
        profileRawJson = null
    }

    // ═══════════ 谈心模式（委托 GenerationEngine） ═══════════

    fun generateCounseling(userMessage: String) {
        counselingJob = generationEngine.generateCounseling(userMessage, viewModelScope, this)
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
                _activeKb.value = knowledgeRepo.getActive()
                _activeKb.value?.let {
                    knowledgeRepo.migrateIfNeeded(it.name)
                    _currentVector.value = knowledgeRepo.readVector(it.name)
                }
            }.onFailure { L.w("refreshKnowledgeBases failed: ${it::class.simpleName}") }
        }
    }

    // ═══════════ 今日锦囊（委托 GenerationEngine） ═══════════

    fun openPlanPanel() { _showPlanPanel.value = true }
    fun dismissPlanPanel() { _showPlanPanel.value = false }

    fun generateSuggest() {
        suggestJob = generationEngine.generateSuggest(viewModelScope, this)
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

    fun generateProactive(draft: String = "", scene: String = "") {
        proactiveJob = generationEngine.generateProactive(draft, scene, viewModelScope, this)
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
        if (schemes.size > _streamingSchemes.value.size) _streamingSchemes.value = schemes
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

    override fun onCounselingSaveLog(userMessage: String, replyText: String, analysisText: String) {
        viewModelScope.launch {
            saveCounselingLog(_activeKb.value?.name, userMessage, replyText, analysisText)
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
    // ：userHint 状态废除——生成时从消息列表收集《想法》消息（：收集读 :438 msgs 快照同源，
    // nextRound 清空后 getUserHint 返回 ""，lifecycle 契约由 LoveBrainViewModelIdeaHintTest ④ 锁定）
    private fun collectIdeaHint(): String =
        _messages.value.filter { it.role == ChatMessage.Role.IDEA }.joinToString("\n") { it.content }
    override fun getUserHint(): String = collectIdeaHint()
    override fun isGenerating(): Boolean = _isGenerating.value
    override fun isCounseling(): Boolean = _isCounseling.value
    override fun isSuggesting(): Boolean = _isSuggesting.value
    override fun isProactive(): Boolean = _isProactive.value
    override fun getOutputMode(): Int = _outputMode.value

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
