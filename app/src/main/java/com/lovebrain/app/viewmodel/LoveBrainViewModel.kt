package com.lovebrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.GenerationTimeoutTier
import com.lovebrain.app.PanelBackdropOpacity
import com.lovebrain.app.data.CostScope
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.GenerationFingerprints
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.IntentPolicy
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.MemoryCorrectionPolicy
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.ReplyPatch
import com.lovebrain.app.domain.RewritePrompt
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.domain.port.KnowledgeRuntimePort
import com.lovebrain.app.domain.toIdentity
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.feature.feedback.CaseSaveOutcome
import com.lovebrain.app.feature.feedback.caseSaveNoticeText
import com.lovebrain.app.feature.feedback.recordDislikeCase
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.feature.mode.ModeController
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.feature.profile.ProfileReview
import com.lovebrain.app.feature.profile.ProfileUpdateController
import com.lovebrain.app.feature.roundcommit.ActualSentRecorder
import com.lovebrain.app.feature.roundcommit.ActualSentState
import com.lovebrain.app.feature.provider.ProviderTicketStore
import com.lovebrain.app.feature.roundstate.RoundScope
import com.lovebrain.app.feature.roundstate.RoundStateStore
import com.lovebrain.app.feature.stage.StageSuggestionStore
import com.lovebrain.app.feature.vector.VectorStore
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.CounselingEnded
import com.lovebrain.app.model.CounselingEvent
import com.lovebrain.app.model.CounselingStarted
import com.lovebrain.app.feature.reply.GenerationVersionId
import com.lovebrain.app.feature.reply.ReplyStore
import com.lovebrain.app.feature.reply.ReplyVersionStack
import com.lovebrain.app.feature.reply.RollbackOutcome
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.GenerationInput
import com.lovebrain.app.model.KnowledgeBase
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
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.model.ReplyCleared
import com.lovebrain.app.model.ReplyRequested
import com.lovebrain.app.model.ReplyStopped
import com.lovebrain.app.model.ReplyUiState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
/** 谈心记录标题截断：分析首行 */
private const val TITLE_FIRST_LINE_LIMIT = 60
/** 谈心记录标题截断：用户消息回退 */
private const val TITLE_FALLBACK_LIMIT = 40

/**
 * 军师核心 ViewModel v4（ 后为状态壳：生成逻辑下沉到 GenerationEngine）。
 *
 * 仓库这一格注入 [KnowledgeRuntimePort]：对话运行时对知识库要的那一族（在用库、文档读、
 * 画像/意图/纠正/咨询日志）——**不含** delete、setActive、create 与无校验覆盖，
 * 所以首页删不掉一本库、也盖不掉用户正在编辑的文件（见那颗端口的 KDoc）。
 */

class LoveBrainViewModel(
    private val deepSeekRepo: DeepSeekRepository,
    private val knowledgeRepo: KnowledgeRuntimePort,
    private val promptBuilder: PromptBuilder,
    private val topicRecorder: TopicRecorder,
    private val securePrefs: SettingsStorePort,
    private val triggerCoordinator: KnowledgeTriggerCoordinator,
    private val generationEngine: GenerationEngine,
    // 统一前台任务协调器——管理所有 AI 前台流程的互斥和生命周期
    val operationCoordinator: ForegroundOperationCoordinator,
    // 反馈案例仓库——点踩时本地保存
    private val feedbackCaseRepository: com.lovebrain.app.data.FeedbackCaseRepository? = null,
    /**
     * 面板**背景层**浓度那一格的落盘口子（读 / 写，量纲是整数百分比）。
     *
     * 为什么是装配侧交进来的一对函数，而不是在这里直接写 `securePrefs.panelBackdropOpacityPercent`：
     * 盘上那一格（`PanelBackdropOpacity.PREF_KEY`）住在 `data/SecurePrefs` 这个**具体实现类**上，
     * 而这里的 [securePrefs] 是 `domain/port/SettingsStorePort` 端口视图、端口面没有这一员；
     * 页面层按具体类去点加密偏好正是 `PackageDependencyTest` 硬挡的那一条（0 违例门禁，不给基线）。
     *
     * 所以装配侧（`di/AppModule`）把 `SecurePrefs.panelBackdropOpacityPercent` 那一对接进来即可，
     * 一行、不新增第二本落盘账。**没接时这一退化成"读默认、写丢弃"**：
     * 读给 [PanelBackdropOpacity.DEFAULT_PERCENT]（= 100，面板与从前逐字同形），
     * 写不动任何持久状态也不会抛——设置页因此永远有东西可画，少接一行不会把面板画没。
     */
    private val readBackdropOpacityPercent: (() -> Int)? = null,
    private val writeBackdropOpacityPercent: ((Int) -> Unit)? = null
) : ViewModel() {

    /**
     * 赞/踩与点踩案例。状态与落盘都在 [FeedbackCaseController] 里，
     * ViewModel 只做一件事：在用户点击那一刻把方案正文和上下文冻成 [FeedbackCaseDraft]。
     */
    private val feedbackCases = FeedbackCaseController(feedbackCaseRepository)

    // ：点踩不弹窗 ⇒ 这一格不再向 UI 暴露"当前案例"那颗展示状态。
    // 建案例与落盘仍由 `FeedbackCaseController` 做（`setFeedback` 那条链一个字没动）；
    // 历史案例的读取走案例库本身，不经过这里。

    /**
     * 输入区与消息列表的状态持有者（复核 第5节第2条 第 6 步"VM 不再持有状态"的第一块）。
     *
     * 这里接管的是九颗原本各写各的 `MutableStateFlow`：面板状态、消息列表、当前角色、
     * 编辑位、《想法》chip 态、两条草稿、输入模式、输出模式、计划面板。
     * 这一家子**没有**留同名出口（第5节第2条 第 6 步"调用点迁完后删除 facade"）：面板与 Service
     * 直接读 `composer.panelState` 那十颗只读流，写法同下面 `intents` 那一族。
     * VM 这一侧只剩下面两条接线：
     *  - `onContentChanged`：内容一变就去判"旧结果是否 stale"，这条判据不能靠每个写的人记得调用；
     *  - 三个持久化回调：store 不知道有 `SecurePrefs` 这回事（第5节第1条：`feature` 不许 import `data`）。
     */
    val composer = ComposerStore(
        scope = viewModelScope,
        initialOutputMode = securePrefs.outputMode,
        onContentChanged = { markCurrentResultStaleIfNeeded() },
        savePanelMode = { securePrefs.panelMode = it },
        persistCounselingDraft = { securePrefs.counselingDraft = it },
        saveOutputMode = { securePrefs.outputMode = it },
        normalizeOutputMode = { mode ->
            // 直出/思考的合法组合规则住在 prompt 配置层，这里不复制一份，只把警告照原样落日志
            promptBuilder.validateConfig(securePrefs.thinkingMode, mode).let { r ->
                r.warnings.forEach { L.w("⚠️ $it") }
                r.outputMode
            }
        }
    )

    // ═══ 回复流程唯一状态源 ═══
    /**
     * 回复流程的全部状态住在 [ReplyStore]（复核 第5节第2条 的第 1 步）。
     *
     * 之前这里是六个各自独立的 MutableStateFlow（_result / _replyRequestState /
     * _streamingCoreText / _streamingSchemes / _generationRoundId / _currentVersionId），
     * 谁都能写；收成了单一 reducer 入口，这一轮把**状态持有者**也搬出 VM。
     * 下面暴露的 result / replyRequestState / streamingCoreText / … 全部从 store 的
     * uiState map 出去，VM 不再有可写的回复状态字段。
     *
     * store 的 Effect 用同步回调处理（不是 flow），理由写在 ReplyStore 的 KDoc 里：
     * 换成异步会把"喂完事件就断言状态"的既有测试语义改掉，而那批语义现在是
     * 真机门禁证据的来源，不该被一次结构改进顺手换掉。
     */
    private val replyStore = ReplyStore(
        scope = viewModelScope,
        // 这里不能引用下面那个 STREAMING_FLUSH_INTERVAL_MS：Kotlin 的属性是按声明顺序
        // 初始化的，前面的属性读后面的 val 会拿到 0，合并定时器就会退化成 delay(0) 空转。
        flushIntervalMs = ReplyStore.DEFAULT_FLUSH_INTERVAL_MS,
        // 搬家时被弄丢的那条被拒日志回到 VM 里写：store 只交出"这个事件不归它"的信号
        onStaleEvent = { event ->
            L.w("reply event ${event::class.simpleName} rejected (stale requestId ${event.requestId.take(8)})")
        }
    ) { effect -> onReplyEffect(effect) }

    /** 只读视图：VM 内部读状态、往外 map 都走这里，写只能进 replyStore.accept */
    private val replyUi get() = replyStore.uiState

    val result: StateFlow<GenerateResult?> = replyUi.map { it.result }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    /** 统一回复请求状态——单一事实源。替代分散的 isPreparing/isGenerating/isGeneratingCore。 */
    val replyRequestState: StateFlow<ReplyRequestState> = replyUi.map { it.request }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, ReplyRequestState.Idle)

    /** 派生属性——从唯一状态源派生，不再是独立可写状态 */
    val isGenerating: StateFlow<Boolean> = replyUi.map { it.isBusy }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    val isGeneratingCore: StateFlow<Boolean> = replyUi.map { it.isBusy }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    /** 稳定的轮次身份 + 仅看本轮开关的持有者（状态搬出 VM，复核 第5节第2条 第 6 步） */
    private val roundStateStore = RoundStateStore(
        onOnlyThisRoundChanged = { checkInputChanged() }
    )

    /** 稳定的轮次身份——只在真正完成一次新的整轮 generate 时变化。
     * 单条改写、undo、feedback 等原地操作不改变它。
     * ResultArea 的 viewMode 只以 round id 重置。 */
    val generationRoundId: StateFlow<Int> = roundStateStore.generationRoundId

    // ═══ 前台任务不在 ViewModel 里留 Job 字段 ═══
    // 旧实现在这里放 generateJob / counselingJob / proactiveJob，各自挂 invokeOnCompletion 清引用，
    // 于是"谁在跑"有两本账（Job 字段 + coordinator），且 stopGeneration 一次取消三类操作。
    // 现在只有 coordinator 一本账，停止按租约/按类型走。

    // ═══════════ 本轮生成上下文（不可变快照） ═══════════
    /**
     * 一轮 AI 生成 = 固定消息快照 + 固定知识库 + 固定 AI 回复 + 固定用户反馈。
     * 生成开始时建立，保存成功后才清除。停止生成时也清除（本轮无成功结果）。
     *
     * 扩展为真正的请求快照，冻结 kbName/kbId、messages、IDEA、
     * 持续意图 text/enabled/revision，以及未提交 IDEA 草稿。
     */
    private data class ReplyGenerationContext(
        val messages: List<ChatMessage>,
        val messageIds: Set<String>,
        val kbName: String?,
        val ideaHint: String,              // 冻结的 IDEA hint（含未提交草稿）
        val intentText: String,            // 冻结的持续意图文本
        val intentEnabled: Boolean,        // 冻结的持续意图启用状态
        val intentRevision: Int,           // 冻结的持续意图 revision（识别旧请求）
        val memoryRefs: List<com.lovebrain.app.model.MemoryRef> = emptyList(), // 冻结的 MemoryRef 清单
        val correctionsRevision: Int = 0,  // 冻结的纠正 revision（防迟到覆盖）
        val sourceAliasMap: Map<String, String> = emptyMap(), // B项修复：别名→实际消息ID映射
        val inputFingerprint: String = "", // 输入指纹——对 KB+消息正文+角色+顺序+IDEA+onlyThisRound+intent revision 做哈希
        val onlyThisRound: Boolean = false, // 冻结 onlyThisRound 状态
        // 生成本轮真正使用的 system prompt 资产指纹——点踩案例记的是它，不是 App 版本名。
        // 版本名没发就永远算不出"prompt 被改过"，硬编码字符串更会把诊断指向错误的 prompt。
        val promptVersion: String = ""
    )
    private var replyGenerationContext: ReplyGenerationContext? = null

    // ═══════════ 轮次级瞬时纠正（不持久化，nextRound/切库时清空） ═══════════
    /**
     * THIS_ROUND mute 的瞬时存储——只在当前轮次有效，不写入 corrections.json。
     * key = memoryId, value = MemoryCorrection(action=MUTED, muteDuration=THIS_ROUND)
     * 在 nextRound()、切库时清空。stopGeneration 不清——停止生成不等于结束当前工作轮。
     * 生成时与持久化 corrections 合并传入 PromptBuilder。 */
    private val roundCorrections = com.lovebrain.app.domain.RoundCorrectionStore()

    // ═══════════ 输入变化提示 + 生成历史 ═══════════

    /** 结果区模式 + 输入变化标记的持有者（状态搬出 VM，复核 第5节第2条 第 6 步） */
    private val modeController = ModeController()

    /** 输入已变化——result 存在但 messages/ideaHint 与生成时快照不一致 */
    val inputChanged: StateFlow<Boolean> = modeController.inputChanged

    /**
     * 回复的版本栈（复核 第5节第2条 第 6 步搬出来的第三块**行为**，主人是 [ReplyVersionStack]）。
     *
     * 四条判据都不在 VM 了：单向回退（被丢弃的版本永不复活）、按知识库分栈、KB 边界拒绝、
     * session 内上限。VM 只留"翻完之后要做的那几件事"（见 rollbackToPreviousGeneration）。
     * 结果 / 上下文 / 版本身份装在同一个快照里一起走，所以没人能只翻一半——
     * 以前最容易出的错就是"结果翻回去了、上下文还停在被丢弃那一轮"。
     */
    private val versions = ReplyVersionStack<ReplyGenerationContext, GenerateResult.Success>()

    /** 当前活跃版本 ID——最新成功生成的版本身份，用于绑定点踩 / 发送 / 改写 */
    val currentVersionId: StateFlow<GenerationVersionId?> = versions.currentVersionId

    /** session 内已记录的版本数（内存态，杀进程即清；不声称跨重启的完整版本历史） */
    val generationHistorySize: Int get() = versions.size

    /**
     * 流式正文 / 方案卡——从 [replyStore] 的 uiState 派生，不再有独立可写的 StateFlow。
     *
     * "别每个 token 全量重绘一次"不再靠 StringBuilder+定时器绕过状态源，
     * 而是 [ReplyStore] 内部把相邻的 chunk 合并成一个事件再归约。
     * 这样节流和"reducer 是唯一写入口"不再互相牺牲。
     */
    val streamingCoreText: StateFlow<String> = replyUi.map { it.streamingCoreText }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "")

    /** 流式过程中已完整解析出的方案卡（逐张渲染，边收边出） */
    val streamingSchemes: StateFlow<List<Scheme>> = replyUi.map { it.streamingSchemes }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    // 增量合并缓冲与它的定时器 job 已经搬进 ReplyStore；这里只留同一个节拍的引用，
    // 给下面那处"等一帧"的用处复用，避免同一个数字在两处各写一遍。
    private val STREAMING_FLUSH_INTERVAL_MS = ReplyStore.DEFAULT_FLUSH_INTERVAL_MS

    /** 读当前结果——所有内部读取走这里，不再有一个可写的 _result */
    private val replyResult: GenerateResult? get() = replyStore.currentResult

    /** 当前激活知识库——跨所有 feature 共享的唯一可变状态（回复/主动发/谈心/改写/意图/纠正/向量/画像/触发事件全部读它），无法移入单一 feature store。 */
    private val _activeKb = MutableStateFlow<KnowledgeBase?>(null)
    val activeKb: StateFlow<KnowledgeBase?> = _activeKb.asStateFlow()

    /**
     * 画像建议卡片：快照 + "确认"这一次尝试的全部判据，主人是 [ProfileUpdateController]
     * （复核 第5节第2条 第 6 步搬的第二块**行为**，原来那是本文件最大的单个成员、98 行）。
     *
     * VM 在这里只做两件事：交出"库在不在 / 纠正 revision / 原子事务"三个读数（都带 IO 调度，
     * 与搬之前一致），以及把警告/回执/"盘改了要刷新"接回各自主人。
     */
    private val profileUpdates = ProfileUpdateController(
        scope = viewModelScope,
        libraryExists = { kb -> withContext(Dispatchers.IO) { knowledgeRepo.listAll().any { it.name == kb } } },
        readCorrectionsRevision = { kb -> withContext(Dispatchers.IO) { knowledgeRepo.getCorrectionsRevision(kb) } },
        applyUpdate = { kb, payload, expectedRevision ->
            knowledgeRepo.applyProfileUpdateAtomically(
                kbName = kb,
                me = payload.me,
                her = payload.her,
                warmth = payload.warmth,
                stageChanged = payload.stageChanged,
                newStage = payload.newStage,
                expectedRevision = expectedRevision
            )
        },
        onApplied = { refreshKnowledgeBases() },
        onWarning = { msg -> showPanelWarning(msg) },
        onNotice = { msg -> notices.show(NoticeBoard.Channel.Knowledge, msg) },
        onError = { msg, e -> L.e(msg, e) },
        stopRegeneration = {
            operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.PROFILE_REFRESH)
        }
    )

    /** 卡片唯一真源（一份快照同帧带"建议 + 在不在确认"，判定住在 ProfileReview 里） */
    val profileReview: StateFlow<ProfileReview> = profileUpdates.review

    /**
     * 悬浮窗上那三条"会自己消失的话"：知识库后台操作的回执、面板级警告、五维重估摘要。
     * 持有者与"换知识库该清哪几条"这条判据都在 [NoticeBoard]，这里只留同名只读出口
     * （搬之前 VM 里有 10 处直写那第一格回执，而"切库要清"只写在其中一处）。
     */
    private val notices = NoticeBoard()

    val kbNotice: StateFlow<String?> = notices.knowledge
    val panelWarning: StateFlow<String?> = notices.warning
    val vectorUpdate: StateFlow<String?> = notices.vector

    /**
     * 通知位上**正在显示的那一条**（null = 通知位空着）——面板只读这一颗决定画什么。
     *
     * 三条通道的只读出口（上面那三员）继续留着：它们说的是"这条通道最近一次的文案"，
     * 排队判据、通道内容与它们都不是同一件事，且有用例直接读这三员。
     * 变的是**画哪一条**：以前三格各自起表、一屏能挤两条，现在一次只输出一条、其余排队，
     * 排队中的条目没有倒计时（时限只在被显示那一刻才盖上）。
     *
     * 计时由面板这一侧起：`delay(notice.remainingMillis(now))` 到点、或用户点原有的关闭 /
     * 需确认项被确认或忽略之后，回报 [endCurrentNotice]——下一条在同一句里顶上。
     */
    val currentNotice: StateFlow<NoticeBoard.Notice?> = notices.current

    /** 当前这条**真正结束**（自动过期到点 / 原有关闭 / 确认或忽略完成），下一条立即顶上。 */
    fun endCurrentNotice() { notices.endCurrent() }

    /**
     * 点名结束**这一条**：id 不是当前正在显示的那条就什么都不做。
     *
     * UI 上每个关闭都该走这一颗。老那条的回调晚到时（表已经跳到下一条），
     * 用它按通道/按"当前"关闭会把刚顶上来那条一起误杀——那是排队最容易坏的地方。
     */
    fun endCurrentNotice(id: Long) { notices.dismiss(id) }

    /**
     * 待确认的画像/阶段建议**入队**，队列只引用它的稳定身份。
     *
     * 建议正文仍由各自 Store 持有，确认与忽略也仍走原有那两条业务；
     * 这里加的只是"轮到谁出现在通知位上"。同一身份重复投递不换号、不排第二份。
     */
    fun showProfileSuggestion(key: String) {
        notices.showSuggestion(NoticeBoard.SuggestionKind.Profile, key)
    }

    fun showStageSuggestion(key: String) {
        notices.showSuggestion(NoticeBoard.SuggestionKind.Stage, key)
    }

    /**
     * Store 里那份建议没了（确认成功 / 忽略 / 切库清掉）：把队列里对应那一张摘掉，等待项随之顶上。
     *
     * key 交 null 表示"这一类现在没有了"，按类摘；交具体 key 表示"这一张被处理掉了"，
     * 只有身份对得上才摘——晚到的旧建议回调不能关掉新的那一张。
     */
    fun dismissProfileSuggestion(key: String? = null) {
        notices.dismissSuggestion(NoticeBoard.SuggestionKind.Profile, key)
    }

    fun dismissStageSuggestion(key: String? = null) {
        notices.dismissSuggestion(NoticeBoard.SuggestionKind.Stage, key)
    }

    /**
     * 把**这一句**从队列里摘掉（条件回到正常时收起那句提示）。
     *
     * 不按通道整条关闭：同一通道可能正排着另一句话，按通道关会把不相干的那条也带走。
     */
    fun dismissPanelNotice(msg: String) {
        notices.dismissMessage(NoticeBoard.Channel.Warning, msg)
    }

    fun dismissKbNotice() { notices.dismiss(NoticeBoard.Channel.Knowledge) } // facade — delegates to NoticeBoard
    fun showPanelWarning(msg: String) { notices.show(NoticeBoard.Channel.Warning, msg) } // facade — delegates to NoticeBoard

    /**
     * 成功回执走这一句，**不许借 [showPanelWarning]**：Warning 那一格画成黄色警告，
     * 而"成功了"画成警告是用户点名骂过的事（"每次记入知识库都一个特别难看的通知"）。
     * 落在 Knowledge 那一格 = 成功形态 + 到时自动消失，图标由组件加一次，
     * 所以这里的文案**不带** ✅（数据带一个、组件再画一个就是两个勾）。
     */
    fun showSuccessNotice(msg: String) { notices.show(NoticeBoard.Channel.Knowledge, msg) }
    fun dismissPanelWarning() { notices.dismiss(NoticeBoard.Channel.Warning) } // facade — delegates to NoticeBoard
    fun dismissVectorUpdate() { notices.dismiss(NoticeBoard.Channel.Vector) } // facade — delegates to NoticeBoard

    /** 向量重估触发的阶段调整建议的持有者（状态搬出 VM，复核 第5节第2条 第 6 步） */
    private val stageSuggestionStore = StageSuggestionStore()

    /** 向量重估触发的阶段调整建议（用户确认后生效） */
    val stageSuggestion: StateFlow<StageSuggestion?> = stageSuggestionStore.stageSuggestion
    fun dismissStageChange() { stageSuggestionStore.accept(StageSuggestionStore.Intent.Clear) } // facade — delegates to StageSuggestionStore
    fun confirmStageChange() {
        val s = stageSuggestionStore.current ?: return
        viewModelScope.launch {
            knowledgeRepo.updateStage(s.kbName, s.newStage)
            knowledgeRepo.updateWarmthStageLabel(s.kbName, s.newStage)
            stageSuggestionStore.accept(StageSuggestionStore.Intent.Clear)
        }
    }

    // ════════ -: 激活工单 + 模型选择 ═══════════

    /**
     * 激活工单 + 就绪位（复核 第2节第2条"就绪态下沉、面板不再本地计算"那半句的主人）。
     * 三条件判据只住在 [ProviderTicketStore.refresh] 一处；读配置靠三个注入的 lambda，
     * 因为 `feature` 不许 import `data`（第5节第1条 包边界，`PackageDependencyTest` 在看着）。
     */
    private val ticketStore = ProviderTicketStore(
        readTickets = { securePrefs.getWorkerTickets() },
        readActiveTicketId = { securePrefs.activeTicketId },
        readApiKey = { id -> securePrefs.getWorkerApiKey(id) }
    )

    val activeTicket: StateFlow<com.lovebrain.app.model.ProviderTicket?> = ticketStore.activeTicket
    val providerReady: StateFlow<Boolean> = ticketStore.ready

    /** 刷新激活工单（面板重新可见时调用，解决 Service 长生命周期下配置后不刷新问题） */
    fun refreshTicketState() {
        viewModelScope.launch { ticketStore.refresh() }
    }

    /**
     * 写**活动工单**的生成超时档位——齿轮设置页「高级设置」那一格用。
     *
     * 只写配置、**不走** `SetupViewModel.saveTicketWithProbe`：那条路每次都会先跑一遍
     * `testConnectionWithProbe`（真实网络探测），而用户在设置页只是把 60/120/180/300 四颗胶囊
     * 拨一格——探测既不是他要的动作，也不该在每次换档位时重来一遍（换档位是纯配置写）。
     *
     * 档位本身就只可能是 [GenerationTimeoutTier] 那四颗（枚举没有别的取值，界面上也没有输入框），
     * 落盘写的是档位秒数；读取侧那层回落（`ProviderConfigResolver` 走
     * `GenerationTimeoutTier.fromSecondsOrDefault`）照旧兜第二道，这里不重复一遍白名单判断。
     *
     * 档位挂在**每一张工单**上、不是全局值，所以没有活动工单时无事可做，只落一条日志。
     * 写完顺手刷新激活工单那份快照：设置页的选中态读的就是 [activeTicket]。
     */
    fun setActiveTicketGenerationTimeout(tier: GenerationTimeoutTier) {
        val activeId = securePrefs.activeTicketId
        val tickets = securePrefs.getWorkerTickets()
        if (activeId == null || tickets.none { it.id == activeId }) {
            L.w("生成超时档位未写入：没有活动工单")
            return
        }
        securePrefs.setWorkerTickets(
            tickets.map { if (it.id == activeId) it.copy(generateTimeoutSec = tier.seconds) else it }
        )
        refreshTicketState()
    }
    // ========================================================

    // ═══════════ 齿轮设置页：面板背景浓度（整数百分比）═══════════

    /**
     * 面板**背景层**浓度的整数百分比：`100` = 原本那一档（完全不透明，滑杆没动过的用户看到的
     * 面板与从前逐字相同），越小越透；合法域与刻度都由 [PanelBackdropOpacity] 负责（40..100、步长 5）。
     *
     * 量纲对外统一成整数，是因为设置页那颗滑杆给用户看的本来就是百分比这个整数刻度——
     * 读盘 → 画滑杆 → 回写必须逐字往返。画的那一步取 `PanelBackdropOpacity.alphaOf(这一员)`，
     * 需要"透明度"那个镜像数时取 `PanelBackdropOpacity.transparencyOf(这一员)`，别在界面上手算 `100 - x`。
     *
     * ⚠ 这个数**只**作用在面板底那一层颜色上。窗口那棵 `ComposeView.alpha` 归淡入淡出动画所有
     * （服务侧在 present/hide 路径上会把它复位），借那条通道画浓度会在第一次收起面板时把设置抹掉。
     *
     * 这里刻意**不**在 VM 里再存一份镜像流：盘上那一格是唯一真源（VM 的可写状态由
     * `ViewModelStateOwnershipTest` 逐颗登记，多加一颗就是往回搬）。宿主要把滑杆接成响应式的，
     * 就沿用设置组件自己那份拖动态，或在本帧重组时重读这一员。
     */
    val panelBackdropOpacityPercent: Int
        get() = PanelBackdropOpacity.snapPercent(readBackdropOpacityPercent?.invoke())

    /**
     * 写面板背景浓度：先过 [PanelBackdropOpacity.snapPercent] 再落盘——
     * 越界与刻度外的值都落回合法刻度（0 与负数不可能变成"面板看不见"），
     * 于是下一次 [panelBackdropOpacityPercent] 读回来的就是刚写进去的那个数，显示与盘上不分家。
     * 装配侧没交口子时这一笔是空写（读仍给默认 100%，见构造参数那格说明）。
     */
    fun setPanelBackdropOpacityPercent(percent: Int) {
        writeBackdropOpacityPercent?.invoke(PanelBackdropOpacity.snapPercent(percent))
    }
    // ========================================================

    /** 五维向量状态 + 重估变化量的持有者（状态搬出 VM，复核 第5节第2条 第 6 步） */
    private val vectorStore = VectorStore()

    /** 当前知识库的五维状态向量（供面板状态卡片展示） */
    val currentVector: StateFlow<Map<String, Int>> = vectorStore.currentVector

    /** 最近一次重估的五维变化量（新值 - 旧值，供卡片显示涨跌箭头） */
    val vectorDelta: StateFlow<Map<String, Int>> = vectorStore.vectorDelta

    val feedbacks: StateFlow<Map<String, SchemeFeedback>> = feedbackCases.feedbacks

    /** ═══════════ 花费/耗时展示（第2节第2条：九个 flow 并成一份快照 + 一个 reduce） ═══════════ */

    /**
     * 面板上那九个"用了多少"的数只有一个来源。
     *
     * 原先是九个 `MutableStateFlow` 加一个不在任何 flow 里的 `private var todayCostDate`
     * （跨天清零的第二份状态），十二处语句各改各的、其中五处还顺手把值抄回 `SecurePrefs`。
     * 现在：转移在 [UsageStats.reduce]（纯函数，JVM 直接测），状态持有在 [usageStatsStore]，
     * 落盘只在 [onPersist] 这一处。
     */
    private val usageStatsStore = UsageStatsStore(
        onPersist = { before, after, event ->
            if (after.totalGenerateCount != before.totalGenerateCount) {
                securePrefs.totalGenerateCount = after.totalGenerateCount
            }
            if (after.totalCostYuan != before.totalCostYuan) securePrefs.totalCostYuan = after.totalCostYuan
            if (after.totalCopyCount != before.totalCopyCount) securePrefs.totalCopyCount = after.totalCopyCount
            if (after.totalAdoptCount != before.totalAdoptCount) securePrefs.totalAdoptCount = after.totalAdoptCount
            if (after.totalRewriteCount != before.totalRewriteCount) {
                securePrefs.totalRewriteCount = after.totalRewriteCount
            }
            // 计费事件每次都存今日数（与改前一致：即便这一笔是 0 元也照存，不省那次写）
            if (event is UsageStats.Event.Costed) securePrefs.saveTodayCost(after.todayDate, after.todayCostYuan)
        }
    )
    val usageStats: StateFlow<UsageStats> = usageStatsStore.stats

    /**
     * 某类前台任务是否在跑——一律从 coordinator 派生，不再有独立可写 boolean。
     *
     * 「今日锦囊」（ 已按用户授权整删）曾经从这里派生 `isSuggesting`；锦囊那条链
     * （store / 状态出口 / 生成入口 / 缓存 / 停止 / 效果回执）已经整体退场，
     * 这一族出口只剩回复、谈心、主动发、画像刷新四类。
     */
    private fun busyOf(
        type: ForegroundOperationCoordinator.OperationType
    ): StateFlow<Boolean> = operationCoordinator.activeOperations
        .map { ops -> ops.any { it.type == type } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    /** ═══════════ 主动发起/润色 ═══════════ */

    /**
     * 主动发的 options / 错误 / 事件归约住在 [com.lovebrain.app.feature.proactive.ProactiveStore]
     * （第5节第2条 第 3 步）。归属判断仍转发给 coordinator——"谁在跑"只有一本账。
     */
    private val proactiveStore = com.lovebrain.app.feature.proactive.ProactiveStore(
        isCurrentRequest = { requestId ->
            ownsAndLog(ForegroundOperationCoordinator.OperationType.PROACTIVE, requestId, "proactive event")
        }
    ) { effect -> onProactiveEffect(effect) }

    val proactiveOptions: StateFlow<List<com.lovebrain.app.model.ProactiveOption>> =
        proactiveStore.uiState.map { it.options }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** 主动开场是否在生成——从 coordinator 派生。模式开关是 composerMode，不是这个 */
    val isProactive: StateFlow<Boolean> =
        busyOf(ForegroundOperationCoordinator.OperationType.PROACTIVE)

    val proactiveError: StateFlow<String?> =
        proactiveStore.uiState.map { it.error }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    /** 结果区当前展示哪一类结果（状态搬出 VM，复核 第5节第2条 第 6 步）。 */
    val resultMode: StateFlow<ResultMode> = modeController.resultMode

    /**
     * 输入区的模式归 [com.lovebrain.app.feature.proactive.ProactiveStore]
     * （第5节第2条 第 3 步清单里的最后一项，[com.lovebrain.app.model.ComposerMode] 搬到 model 之后才搬得动）。
     *
     * 以前这里是 `_composerMode` + 四处手写赋值（切换、发起、被拒、结束），
     * 而"结束了且真拿到可展示开场才退回普通回复"这条规则跨两个所有者：
     * store 报"有结果"，VM 再改自己的字段——同一瞬间可以读出"模式已退、结果还没清"。
     * 现在模式与 options 在同一个状态对象里，规则在一次赋值里做完，本类只转发只读视图。
     */
    val composerMode: StateFlow<ComposerMode> =
        proactiveStore.uiState.map { it.composerMode }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, ComposerMode.REPLY)

    /** 切换主动发模式——第一次点击只切换模式，不发网络请求。
     * 再次点击退出主动发模式回到普通回复（结果由 store 一起清）。 */
    fun toggleProactiveMode() {
        if (operationCoordinator.isBusy(ForegroundOperationCoordinator.OperationType.PROACTIVE) ||
            replyUi.value.isBusy) return
        proactiveStore.accept(com.lovebrain.app.feature.proactive.ProactiveStore.Intent.ToggleComposer)
    }

/** 前台任务互斥——从 operationCoordinator 派生，不再拼多个 boolean */
val isForegroundBusy: Boolean get() = operationCoordinator.isForegroundBusy

    // ═══════════ 仅看本轮开关 ═══════════

    /** 仅看本轮开关——默认关闭。开启后只携带通用生成规则、本轮真实消息和本轮想法。
     * 排除旧画像、关系阶段、历史对话、场景、事项、经验与持续意图。
     * 开关属于当前工作轮次；本轮重生成保留，开启新轮次或切档案后恢复默认。
     * 状态在 [roundStateStore]，VM 只留只读出口与切换入口。 */
    val onlyThisRound: StateFlow<Boolean> = roundStateStore.onlyThisRound

    fun toggleOnlyThisRound() {
        // 切换走 store：翻转状态后 store 同步回调 checkInputChanged()，让旧结果标 stale
        roundStateStore.accept(RoundStateStore.Intent.ToggleOnlyThisRound)
    }

    // ═══════════ 输入变化提示 + 生成历史与版本回退 ═══════════

    /**
     * 统一标记当前结果为 stale（如果输入已变化）。
     * 在所有消息操作（增/删/改/重排/草稿/反馈）后调用。
     * 仅当存在已完成的生成结果时才实际检测。 */
    private fun markCurrentResultStaleIfNeeded() {
        if (replyResult != null && replyGenerationContext != null) {
            checkInputChanged()
        }
    }

    /**
     * 检测输入是否已变化——使用真正的输入指纹比较。
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
        // fix: 使用当前活跃 intent revision，而非生成时冻结的 ctx.intentRevision。
        // ctx.intentRevision 是生成时的快照值，如果用户随后修改了 intent，
        // 用旧 revision 自己跟自己比较当然发现不了变化。
        val currentFingerprint = computeInputFingerprint(
            composer.messagesNow,
            composer.ideaHint(),
            _activeKb.value?.name,
            // 这一格取"长期开关 ∨ 那一次请求实际用的范围"，而不是只看开关：
            // 长按那一条产出的结果冻的是 ctx.onlyThisRound=true，而开关仍是关闭（长按不拨开关），
            // 只读开关就会把刚发出去的那条结果判成"输入变了"、立刻盖上 stale——
            // 判据必须与 [runReplyRequest] 冻结指纹时用的是同一条，否则两边永远算不出同一个数。
            roundStateStore.onlyThisRoundNow || ctx.onlyThisRound,
            intents.currentRevision()
        )
        modeController.accept(ModeController.Intent.SetInputChanged(currentFingerprint != ctx.inputFingerprint))
    }

    /**
     * 本轮输入指纹——算法在 `GenerationFingerprints.inputOf`：
     * KB identity、有序消息(id+role+exact content)、想法原文、onlyThisRound、意图 revision。
     * 不含温度等生成参数，所以它说的是"输入变没变"，不是"这次会不会生成出一样的话"。
     */
    private fun computeInputFingerprint(
        messages: List<ChatMessage>,
        ideaHint: String,
        kbName: String?,
        onlyThisRound: Boolean,
        intentRevision: Int
    ): String =
        GenerationFingerprints.inputOf(messages, ideaHint, kbName, onlyThisRound, intentRevision)

    /**
     * 回退到生成结果。
     * 正确的 one-way undo 语义：
     *   history = [v1, v2, v3], current = v3
     *   rollback → 删除 current(v3), 恢复 previous(v2)
     *   history => [v1, v2]
     * 之后基于 v2 生成 v4：
     *   history => [v1, v2, v4]
     * 再次 rollback → 删除 v4, 恢复 v2
     * 绝不会重新出现已放弃的 v3。
     *
     * KB 边界——只有当前 result context 与 active KB 一致时才允许 rollback。
     *   用户已切到 B 时，不允许操作 A 的 version stack。
     * rollback 后不无条件 _inputChanged=false——调用 checkInputChanged()
     *   让当前真实输入与 previous.context.inputFingerprint 比较。
     *   如果用户当前输入与旧版本不同，stale 必须 true。
     */
    fun rollbackToPreviousGeneration() {
        // 谁能回退、回退到哪、栈怎么改，都在 ReplyVersionStack 那一处（含 KB 边界与"按库分栈"）
        val out = versions.rollback(
            activeKbName = _activeKb.value?.name,
            contextKbName = replyGenerationContext?.kbName
        )
        if (out !is RollbackOutcome.Applied) return
        val previous = out.restored

        // 原子恢复 result + versionId + context（三者来自同一个快照，不会错配）
        replyStore.accept(ReplyStore.Intent.ReplaceResult(previous.result))
        replyGenerationContext = previous.context
        feedbackCases.clearFeedbacks()
        // 版本栈已经翻过去了，在飞的改写目标也就不存在了：一起作废
        rewriteStore.accept(com.lovebrain.app.feature.rewrite.RewriteStore.Intent.RoundReset)

        // 重新计算 stale——当前输入可能与 previous context 不一致
        checkInputChanged()

        // 生成新的 roundId 值以触发 viewMode 重置——不递减，不使用 magic number
        roundStateStore.accept(RoundStateStore.Intent.BumpRoundId)
    }

    /** 是否可以回退到上一版本 — 以当前 active KB 为权限边界 */
    /** 判据本身在栈里（KB 边界 + 这一块库至少两条）；这里只把两个读数递过去 */
    val canRollbackGeneration: Boolean
        get() = versions.canRollback(
            activeKbName = _activeKb.value?.name,
            contextKbName = replyGenerationContext?.kbName
        )

    // ═══════════ 谈心模式 ═══════════
    /**
     * 谈心的流式正文、结果、错误与"日志命令"的触发住在
     * [com.lovebrain.app.feature.counseling.CounselingStore]（第5节第2条 第 4 步）。
     *
     * 谈心**草稿**（[composer] 的 `counselingDraft`）仍在这一家子：它是用户输入 + SecurePrefs 的防抖落盘，
     * 不是这条生成链的状态；混进 store 只会让 store 再去碰 prefs。
     */
    private val counselingStore = com.lovebrain.app.feature.counseling.CounselingStore(
        scope = viewModelScope,
        isCurrentRequest = { requestId ->
            // 归约搬进 store 时这道闸只剩布尔判断，日志差点跟着丢掉；在唯一的注入点补回来
            ownsAndLog(ForegroundOperationCoordinator.OperationType.COUNSELING, requestId, "counseling event")
        },
        // STREAMING_FLUSH_INTERVAL_MS 在本文件更靠前的位置声明（grep 得到），
        // 所以这里读到的是真值不是 0——replyStore 那处就因为这个顺序问题只能用自己的默认常量。
        flushIntervalMs = STREAMING_FLUSH_INTERVAL_MS
    ) { effect -> onCounselingEffect(effect) }

    val counselingResult: StateFlow<String?> =
        counselingStore.uiState.map { it.result }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    val counselingError: StateFlow<String?> =
        counselingStore.uiState.map { it.error }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    /** 谈心是否在生成——从 coordinator 派生 */
    val isCounseling: StateFlow<Boolean> =
        busyOf(ForegroundOperationCoordinator.OperationType.COUNSELING)

    val counselingStreaming: StateFlow<String> =
        counselingStore.uiState.map { it.streaming }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "")

    init {
        // 确保至少有一个合法知识库（首次启动创建默认库，重复启动沿用，中断恢复补齐）
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
            composer.accept(ComposerStore.Intent.RestoreOutputMode(persisted.outputMode))
            securePrefs.thinkingMode = persisted.thinkingMode
            securePrefs.outputMode = persisted.outputMode
        }

        // 今日花费载入（跨天清零）+ 订阅计费事件流（ 口径）
        val savedCost = securePrefs.loadTodayCost()
        usageStatsStore.load(UsageStats.loaded(
            today = java.time.LocalDate.now().toString(),
            savedTodayCost = savedCost,
            totalGenerateCount = securePrefs.totalGenerateCount,
            totalCostYuan = securePrefs.totalCostYuan,
            totalCopyCount = securePrefs.totalCopyCount,
            totalAdoptCount = securePrefs.totalAdoptCount,
            totalRewriteCount = securePrefs.totalRewriteCount
        ))
        viewModelScope.launch {
            // SharedFlow 收集不应崩面板
            try {
                deepSeekRepo.costEvents.collect { ev ->
                    usageStatsStore.accept(
                        UsageStats.Event.Costed(
                            today = java.time.LocalDate.now().toString(),
                            yuan = ev.yuan,
                            // 本次费用只显示前台流式请求（FOREGROUND），不被后台 raw 污染
                            foreground = ev.scope == CostScope.FOREGROUND
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.w("计费事件收集异常：${e.javaClass.simpleName}")
            }
        }

        restoreState()

        //  从 SecurePrefs 读取激活工单信息（面板每次可见时经 refreshTicketState 再刷新）
        refreshTicketState()
    }

    // ═══════════ UI 状态 setters ═══════════

    fun setDraft(text: String) { composer.accept(ComposerStore.Intent.SetDraft(text)) } // facade — delegates to ComposerStore
    fun setCounselingDraft(text: String) { // facade — delegates to ComposerStore
        composer.accept(ComposerStore.Intent.SetCounselingDraft(text))
    }
    fun setPanelMode(mode: Int) { composer.accept(ComposerStore.Intent.SetPanelMode(mode)) } // facade — delegates to ComposerStore
    //  捕获收口：FloatingService 捕获链以 currentRole 落消息角色（本文件外零触碰），
    // currentRole 恒 ∈ {HER, ME}——选《想法》只进入 ideaComposeMode，不落 currentRole，真实聊天捕获永不误标 IDEA
    fun setCurrentRole(role: ChatMessage.Role) { // facade — delegates to ComposerStore
        composer.accept(ComposerStore.Intent.SetCurrentRole(role))
    }
    fun setEditingIndex(index: Int) { composer.accept(ComposerStore.Intent.SetEditingIndex(index)) } // facade — delegates to ComposerStore

    // ═══════════ 消息管理：状态与"改完之后编辑位该落到哪"都住在 [ComposerStore] ═══════════

    fun addMessage(role: ChatMessage.Role, content: String) { // facade — delegates to ComposerStore
        composer.accept(ComposerStore.Intent.AddMessage(role, content))
    }

    fun updateMessage(index: Int, role: ChatMessage.Role, content: String) { // facade — delegates to ComposerStore
        composer.accept(ComposerStore.Intent.UpdateMessage(index, role, content))
    }

    fun removeMessage(index: Int) { // facade — delegates to ComposerStore
        composer.accept(ComposerStore.Intent.RemoveMessage(index))
    }

    /**
     * 按消息 id 删除（动画延迟回调里 index 会过期，id 是 data class 稳定值）。
     *
     * 为什么修正编辑位这件事不在这里做：它是"列表变了"的后果，不是"谁调用"的后果。
     * 删、拖、提交本轮三处都要求同一条判据，写在调用方便会变成三份各抄一份的算术
     * （以前就是，见 [MessageListEditing.reindex] 的 KDoc）。
     */
    fun removeMessageById(id: String) { // facade — delegates to ComposerStore
        composer.accept(ComposerStore.Intent.RemoveMessageById(id))
    }

    /**
     * 拖拽重排。编辑位与列表的联动同 [removeMessageById]：一次列表改动，一条判据。
     * 穷举矩阵 `MessageEditingIndexInvariantTest` 在每个 (长度, 编辑位, from, to) 组合上跑真方法。
     */
    fun reorderMessages(from: Int, to: Int) { // facade — delegates to ComposerStore
        composer.accept(ComposerStore.Intent.ReorderMessages(from, to))
    }

    // ═══════════ 状态持久化 ═══════════

    private fun restoreState() {
        securePrefs.panelMode.let { composer.accept(ComposerStore.Intent.RestorePanelMode(it)) }
        securePrefs.loadCounselingResult()?.let {
            if (it.isNotBlank()) {
                counselingStore.accept(
                    com.lovebrain.app.feature.counseling.CounselingStore.Intent.Restore(it)
                )
            }
        }
        securePrefs.counselingDraft.let { composer.accept(ComposerStore.Intent.RestoreCounselingDraft(it)) }
        // 「今日锦囊」的当天缓存恢复随  一起退场（消息/想法本来就是纯内存，杀进程即清）。
        // SecurePrefs 那对口子（loadSuggestion / saveSuggestion）的删除记在 P2，本类已不再调用。
    }

    // ═══════════ 流式生成（委托 GenerationEngine） ═══════════

    /**
     * 生成回复。
     *
     * 一个请求 = coordinator 注册的**一个**任务，覆盖"准备 → 网络 → 解析 → 发布"全程。
     * 旧写法是 ViewModel 先 launch 一个 prepJob，再把 scope 交给 Engine 让它 launch 第二个 Job，
     * 两个 owner 并存；停止时只能一次取消三类操作，误伤并行的改写和锦囊。
     *
     * 被互斥拒绝时 [ForegroundOperationCoordinator.start] 返回 null 且任务从未启动，
     * 因此这里不需要"先起再回滚"，也不会有不受管理的前台任务。
     *
     * [roundScope] 是这一次请求要带多大范围的上下文，**默认跟着长期开关**——
     * 其余 `generate()` 调用点（重试、面板那几处）因此一个字都不用改、行为逐字不变。
     * 长按入口交 [RoundScope.CurrentRoundOnly]：只把"仅看本轮"用在**发出去的这一次请求**上，
     * 不拨长期开关、也不留任何待花费的标记，所以下一次普通点击仍按默认范围走。
     * 值随请求参数进生成链、随请求结束消失，这就是"长按不变成永久设置"那条禁令的写法。
     */
    fun generate(roundScope: RoundScope = RoundScope.FollowSwitch) {
        val snapshot = composer.messageSnapshot()
        // 生产前置条件：没有真实对话（HER/ME）不发起请求
        if (snapshot.none { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }) return

        // 设置结果模式
        modeController.accept(ModeController.Intent.SetResultMode(ResultMode.REPLY))

        val requestId = ReplyRequestState.newRequestId()
        val userHint = composer.ideaHintOf(snapshot)

        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.REPLY,
            requestId
        ) { runReplyRequest(requestId, snapshot, userHint, roundScope) }

        if (lease == null) {
            L.w("generate rejected: another foreground operation owns the slot")
        }
    }

    /**
     * 「重试」＝按上一条结果**当时冻结的上下文范围**再发一次，而不是去读长期开关的实时值。
     *
     * 长按那一条把"只看本轮"冻在 [replyGenerationContext] 里，界面上没有任何开关反映它；
     * 重试若走默认范围，用户点的是同一张卡，拿到的却是另一套依据的结果。
     * 没有冻结上下文（首条结果还没产出）时按默认范围发起，与旧调用点逐字同构。
     */
    fun retryCurrentReply() {
        generate(
            if (replyGenerationContext?.onlyThisRound == true) {
                RoundScope.CurrentRoundOnly
            } else {
                RoundScope.FollowSwitch
            }
        )
    }

    /**
     * 一次回复请求的完整生命周期——由 coordinator 拥有。
     *
     * 准备阶段的读盘/组 prompt 与流式收集都在这个协程里，所以取消它就等于取消整条链。
     *
     * [roundScope] 只在这一次请求里有效：真正的布尔由
     * [RoundStateStore.resolveOnlyThisRound] 在这里算出（长期开关 ∨ 这一次点名只读本轮），
     * 之后随冻结的上下文一起走，**不**写回开关。
     */
    private suspend fun runReplyRequest(
        requestId: String,
        snapshot: List<ChatMessage>,
        userHint: String,
        roundScope: RoundScope = RoundScope.FollowSwitch
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

            // 到期只有一把尺（IntentPolicy）：这条规则此前在「发起生成」和「面板刷新」
            // 两处各写了一遍，改一处漏一处
            val effectiveIntent = if (IntentPolicy.shouldAutoExpire(intentSnapshot, com.lovebrain.app.util.TimeFmt.today())) {
                intentSnapshot.copy(status = com.lovebrain.app.model.IntentStatus.EXPIRED)
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
            val mergedCorrections = correctionsSnapshot.toMutableMap().also { it.putAll(roundCorrections.snapshot()) }

            currentCoroutineContext().ensureActive()

            // 构建不可变 GenerationInput——冻结本轮全部输入，
            // 含 KB 内容修订、画像正文、Provider 完整非敏感身份与 prompt 资产 hash
            val aggressive = composer.outputModeNow == 1
            // 这一次请求实际读多大范围的上下文：纯读、不写任何状态（长按不拨开关）。
            // 下面三处（生成输入 / 冻结指纹 / 冻结上下文）共用这一颗，判据只算一次——
            // 三处各自现读开关的话，长按那一条会产出"输入按只读本轮、指纹却按开关"的错配。
            val onlyThisRound = roundStateStore.resolveOnlyThisRound(roundScope)
            val providerConfig = deepSeekRepo.snapshotProviderConfig()
            val input = buildGenerationInput(
                requestId = requestId,
                messages = snapshot,
                userHint = userHint,
                knowledgeBase = kbSnapshot,
                intentConfig = effectiveIntent,
                corrections = mergedCorrections,
                correctionsRevision = correctionsRevision,
                onlyThisRound = onlyThisRound,
                aggressive = aggressive,
                providerIdentity = providerConfig?.toIdentity(),
                kbProfile = kbName?.let { readOrNull("") { knowledgeRepo.readProfile(it) } } ?: "",
                kbRevision = kbName?.let { readOrNull("") { knowledgeRepo.contentRevision(it) } } ?: "",
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
                    snapshot, userHint, kbName, onlyThisRound, effectiveIntent.revision
                ),
                onlyThisRound = onlyThisRound,
                promptVersion = promptBuilder.replyPromptAssetHash()
            )

            // Engine 只暴露事件流，本协程是它唯一的订阅者
            generationEngine.replyStream(input).collect { dispatchReply(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            dispatchReply(ReplyStopped(requestId))
            throw e
        } catch (e: Exception) {
            // 准备阶段异常转为可恢复错误，不泄露路径/Provider/内部细节
            L.e("generate request failed", e)
            dispatchReply(
                ReplyCompleted(
                    requestId,
                    GenerateResult.Error(ReplyFailureKind.fromException(e).userMessage)
                )
            )
        }
    }

    // ═══════════ 回复状态唯一写入口 ═══════════

    /**
     * 回复流程的唯一写入口——现在只是把意图投给 [replyStore]。
     *
     * 事件身份校验、相邻增量合并、以及"被拒的事件不发副作用"三件事都在 store 里；
     * 名字与 internal 可见性保留，是因为有一批用例就是按"喂一个事件"来驱动的，
     * 换名字会让这轮重构混进无关的测试改动。
     */
    internal fun dispatchReply(event: ReplyEvent) {
        replyStore.accept(ReplyStore.Intent.Apply(event))
    }

    /**
     * ReplyStore 归约成功之后，需要**别人**做的事：面板外壳状态、历史版本快照、
     * 跨轮计数与耗时上报。
     *
     * 只有归约被接受（状态对象换了）才会收到 Effect，所以迟到的旧请求
     * 不可能递增 roundId、不可能写历史、也不可能改计数——这条不变量从 VM 里的
     * 一段 if 变成了端口上的类型：store 不给，VM 就没有机会做。
     *
     * 用同步回调而不是 SharedFlow：这些写入要和归约落在同一帧里，
     * 现有那批"喂完事件就断言状态"的用例才有确定性（理由另见 ReplyStore 的 KDoc）。
     */
    private fun onReplyEffect(effect: ReplyStore.Effect) {
        when (effect) {
            is ReplyStore.Effect.PanelStateChanged ->
                composer.accept(ComposerStore.Intent.SetPanelState(effect.panelState))

            is ReplyStore.Effect.SuccessCommitted -> {
                roundStateStore.accept(RoundStateStore.Intent.BumpRoundId)
                val versionId = GenerationVersionId.next()
                // 有本轮上下文才写历史：记的是"结果 + 上下文 + 版本身份"三条一起，
                // 上限（session 内最多留几条）由栈负责，搬之前是那个 20
                val ctx = replyGenerationContext
                if (ctx == null) {
                    versions.advanceVersionOnly(versionId)
                } else {
                    versions.record(
                        versionId = versionId,
                        result = effect.result,
                        context = ctx.copy(
                            memoryRefs = effect.memoryRefs,
                            sourceAliasMap = effect.sourceAliasMap
                        ),
                        kbName = ctx.kbName
                    )
                }
                // 生成成功后重置输入变化标记
                modeController.accept(ModeController.Intent.SetInputChanged(false))
                // 递增累计生成次数
                usageStatsStore.accept(UsageStats.Event.Generated)
            }

            is ReplyStore.Effect.TimingSampled -> {
                usageStatsStore.accept(UsageStats.Event.Timed(effect.firstReplyMs, effect.firstTokenMs))
            }
        }
    }

    /**
     * 停止生成——只取消当前 REPLY owner。
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
        replyStore.accept(ReplyStore.Intent.DiscardPendingChunks)
        current?.let { dispatchReply(ReplyStopped(it.requestId)) }
        // 停止生成时清 context（本轮无成功结果），但消息本身不删
        replyGenerationContext = null
        // /: stopGeneration 不清 roundCorrections——
        // 停止生成不等于结束当前工作轮。用户 mute → stop → retry 时，
        // 本轮 mute 应继续有效。roundCorrections 只在 nextRound / switch KB 时清。
        modeController.accept(ModeController.Intent.SetInputChanged(false))
    }

    // ═══════════ 赞踩反馈 ═══════════

    /**
     * 赞/踩切换——identityKey 区分 STYLE 与 DIRECTION，互不干扰。
     *
     * 点踩不调用 AI，只落一份本地可诊断案例。切换判定与案例构造在 [FeedbackCaseController]；
     * 异步落盘仍由本 ViewModel 发起（唯一异步 owner），这里额外做两件事：
     * · **在点击当刻**把素材冻成 [FeedbackCaseDraft]，免得异步保存期间结果被换掉；
     * · 落盘走 [com.lovebrain.app.feature.feedback.recordDislikeCase]，
     *   界面上那句回执**只跟着它交回的 [com.lovebrain.app.feature.feedback.CaseSaveOutcome] 走**——
     *   写进盘了才说"已记录"，写不进就说"记入失败"（ 之前这里是 launch 之后不管结果）。
     */
    fun setFeedback(identityKey: String, feedback: SchemeFeedback) {
        val created = feedbackCases.toggle(identityKey, feedback, freezeDislikeDraft(identityKey))
            ?: return
        viewModelScope.launch {
            val outcome = recordDislikeCase(
                incoming = created,
                // 查重用的库快照由案例仓库的唯一主人（控制器）读，这一格自己不摸盘
                existing = feedbackCases.existingCases(),
                persist = { case ->
                    // persistCase 的返回值必须被读掉：false = 一个字都没写进盘。
                    // 把它抛出去，recordDislikeCase 才有 Failed 这一格可报——
                    // 再往下就是"存盘失败也照样像成功了"那条老路。
                    if (!feedbackCases.persistCase(case)) {
                        throw IllegalStateException("feedback case was not written")
                    }
                }
            )
            reportDislikeCaseSave(outcome)
        }
    }

    /**
     * 落盘结果 → 屏幕上那一句话。三条通道各走各的格，不混：
     *
     * · [CaseSaveOutcome.Recorded] / [CaseSaveOutcome.AlreadyRecorded] → 绿色成功格
     *   （[showSuccessNotice]，3 秒自动消失）。`AlreadyRecorded` 也算这一句：它说的是"这一条在库里"
     *   这个状态，不是"刚刚新增了一条"——反复踩同一条方案不涨记录数，正是本轮要的。
     * · [CaseSaveOutcome.Failed] → 既有的警告格（[showPanelWarning]），并且**什么都不回滚**：
     *   用户刚点的那一下踩、屏上的候选与回复全部留着（原话："不许清掉用户刚点的赞踩、不许清回复"）。
     *
     * 文案是盘上已有的两条资源（`R.string.notice_recorded` / `notice_record_failed`，中英各一份），
     * 不新开 key、也不在这里再抄一份中文。取不到就**不投通知**、只记一条 error 日志——
     * 宁可不说话，也不在英文环境下念一句写死的中文，更不肯因为取不到就改口报成功。
     *
     * 上下文从**已经启动**的 Koin 容器里取：`KoinPlatform.getKoin().getOrNull(Context::class)`。
     * 这条口子不是新发明——`Koin.get/getOrNull(类型)` 的字节码就是
     * `scopeRegistry.rootScope.get(类型, null, null)`，而 `di/AppModule` 今天那句
     * `KnowledgeRepository(…, androidContext(), …)` 走的正是同一个 root scope 的同一次解析，
     * 所以生产里解析得到；容器没起来（JVM 用例、装配之前）拿不到 ⇒ 两手空空、不猜文案。
     * 成因注记：这条"取不到就不投、绝不猜"的规矩不是在这里新发明的——
     * `HomeStatusViewModel` 早年也有一颗同形状的服务定位器取口（`homeKnowledgePortFromDi()`，
     * 该取口已随服务定位器一起退役、改成了构造注入，指针留在这里只会变成死引用，故只留成因）：
     * 它当初要防的就是"在容器缺席的环境里假装解析成功、拿一份可能压根不存在的文案糊到屏幕上"。
     * 更干净的写法是构造注入（`SetupViewModel` 今天就是 `androidContext()` 注进 VM 的），
     * 但那要动 `di/AppModule`，不是本轮能动的文件 ⇒ 写进报告交回。
     */
    private fun reportDislikeCaseSave(outcome: CaseSaveOutcome) {
        // 文案与 `res` 里那两条逐字同形（`notice_recorded` = 「已记录」、
        // `notice_record_failed` = 「记入失败，请重试」）。VM 里没有 Context，
        // 本仓这 13 处通知文案一直走"VM 内字面量"这一条既有欠账（英文环境念中文），
        // 不在本轮里顺手改成注入 —— 那要动 di/AppModule 与全部调用点，属另一格清理。
        val recorded = "已记录"
        val failed = "记入失败，请重试"
        when (outcome) {
            CaseSaveOutcome.Recorded, is CaseSaveOutcome.AlreadyRecorded ->
                showSuccessNotice(caseSaveNoticeText(outcome, recorded, failed))
            CaseSaveOutcome.Failed ->
                showPanelWarning(caseSaveNoticeText(outcome, recorded, failed))
        }
    }

    /**
     * 冻结点踩素材。
     *
     * 返回 null 表示此刻采不出案例（还没有结果、上下文已清、方案已不在结果里）——
     * 控制器据此只更新赞/踩状态，不建案例。
     */
    private fun freezeDislikeDraft(identityKey: String): FeedbackCaseDraft? {
        val result = replyResult as? GenerateResult.Success ?: return null
        val ctx = replyGenerationContext ?: return null
        val identity = com.lovebrain.app.model.SchemeIdentity.fromKey(identityKey) ?: return null
        val schemeText = ReplyPatch.textOf(result.response, identity) ?: return null
        return FeedbackCaseDraft(
            schemeReply = schemeText,
            kbName = ctx.kbName ?: "",
            ideaHint = ctx.ideaHint,
            intentText = ctx.intentText.takeIf { ctx.intentEnabled } ?: "",
            dialogue = ctx.messages
                .filter { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
                .map { msg ->
                    com.lovebrain.app.model.DialogueSnapshotEntry(
                        speaker = if (msg.role == ChatMessage.Role.HER) "PARTNER" else "USER",
                        text = msg.content
                    )
                },
            contextMode = if (ctx.onlyThisRound) "only-this-round" else "full",
            // 去重要的是"哪一次生成"，所以哈希后面钉上本轮的版本身份（每轮 mint 一颗新 UUID，
            // 见 onReplyEffect 的 SuccessCommitted 与 GenerationVersionId.next()）。
            // 只交哈希 = 跨轮、改写之后都不变，新一轮里踩的另一条会被当成重复吞掉。
            promptVersion = "${ctx.promptVersion}#${currentGenerationToken()}",
            modelId = ticketStore.activeTicketNow?.model ?: "",
            costYuan = usageStatsStore.current.lastCostYuan ?: 0.0
        )
    }

    /**
     * 本轮生成的身份——[GenerationVersionId]，一次成功生成一颗、`UUID.randomUUID()` 现生成。
     *
     * 取到不了的那一条（今天走不到：能摆出 Success 结果的路都先在 `Effect.SuccessCommitted`
     * 里 mint 过版本身份，`versions` 也没有任何清空入口在 VM 里被调用过）退化成**一次点击现造一颗**，
     * 让这一条不参与去重。方向是刻意的：本轮要修的病是"记录被当成重复吞掉"，
     * 身份缺失时宁可多记一条，也不许再吞。
     */
    private fun currentGenerationToken(): String =
        versions.currentVersionIdNow?.value ?: java.util.UUID.randomUUID().toString()

    // ：`updateFeedbackCase` / `dismissFeedbackCase` 这两颗 UI 控制口随原因面板一起摘除
    //（没有面板就没有"改分类 / 改原因 / 收起"这回事）。建案例与落盘仍在 `FeedbackCaseController`，
    // 历史案例的读取走案例库本身。

    // ═══════════ 下一轮（存 KB + 清空） ═══════════

    /** nextRound 重入保护——仅在 nextRound() 内读写，但它编排 7+ feature store（composer/feedback/replyStore/roundState/roundCorrections/topicRecorder/triggerCoordinator），无法移入单一 store。 */
    private var recordingRound = false
    /* private val _probeFakeInBlock = MutableStateFlow(0)
       private var probeFakeInBlockVar = 0 */
    // private val _probeFakeInLine = MutableSharedFlow<Int>()

    /**
     * 提交顺序改为「先写盘成功 → 再提交 UI」。
     * 写盘失败时保留所有本轮数据（消息/结果/反馈/context），用户可重试。
     * 保存时使用 replyGenerationContext 中的快照消息和 KB 名，不用实时消息列表与实时激活库。
     */
    fun nextRound() {
        val response = (replyResult as? GenerateResult.Success)?.response ?: return
        if (recordingRound) return

        // 使用生成时绑定的 context，不用实时状态
        val context = replyGenerationContext ?: return

        recordingRound = true

        val kbName = context.kbName

        // 使用 identity.key 查找反馈——STYLE 和 DIRECTION 互不干扰
        val likedStyleSchemes = response.schemes
            .filter { feedbackCases.feedbackFor(it.identity.key) == SchemeFeedback.LIKED }
            .sortedBy { "ABCD".indexOf(it.tag) }

        // 方向回复的点赞也要保存——赞 F 真正保存 F 回复
        val likedDirectionSchemes = response.directionSchemes
            .filter { feedbackCases.feedbackFor(it.identity.key) == SchemeFeedback.LIKED }

        val likedSchemes = likedStyleSchemes + likedDirectionSchemes

        // 点赞不等于发送。selectedScheme 恒为 null——
        // 用户没有"确认发送"操作，点赞只保存为偏好，不写入"实际对话"段。
        // TopicRecorder.record 收到 scheme=null 时不写"我（最终回复：…）"行。
        val selectedScheme: Scheme? = null
        val likedForRecording = likedSchemes // 保留全部点赞用于偏好记录

        // 无 KB 时保持当前产品语义（可结束但提示未记入）
        if (kbName == null) {
            commitReplyRound(context.messageIds)
            showPanelWarning("未激活知识库，本轮对话未记入")
            replyGenerationContext = null
            recordingRound = false
            return
        }

        // selectedScheme 可以为 null——用户没有点赞也不默认选 A。
        // 仍允许保存本轮输入消息（不依赖选中候选）。
        // 如果用户没有确认发送，提示"已保存对话，候选未作为已发送消息记录"。

        // 先写盘，成功后才提交 UI
        val analysis = response.analysis
        val messagesSnapshot = context.messages
        val consumedIds = context.messageIds

        viewModelScope.launch {
            try {
                // 按 context.kbName 查找 KB（生成时的 KB，非当前激活 KB）
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
                        // 后台三引擎不占用前台租约；收集方（本 VM 的 viewModelScope）就是这段工作的 owner
                        viewModelScope.launch {
                            triggerCoordinator.triggerEvents(kb.name)
                                .collect { applyTriggerEvent(it) }
                        }
                    }
                }
                L.w("PERF t7 kb write done (${System.currentTimeMillis() - t7Start}ms)")

                // 写盘成功 → 才提交 UI 状态
                commitReplyRound(consumedIds)
                replyGenerationContext = null
                // 清空本轮瞬时纠正——新轮次不再受 THIS_ROUND mute 影响
                roundCorrections.clear()
                // 写盘成功 → 报一句成。用户 2026-10-03 原话："每次记入知识库都一个特别难看的通知，
                // 你直接说一个 ✅已记入 不就行了吗"。那句"候选未作为已发送消息记录"是内部语义，
                // 不是用户需要知道的下一步 ⇒ 不再展示；后台那条区分（点赞/采用/记入三条机制、
                // ActualSentRecorder 那条链）一个字没动。
                showSuccessNotice("已记入")
                refreshKnowledgeBases()
            } catch (t: kotlinx.coroutines.CancellationException) {
                // 取消信号不写盘、不报"保存失败"：交给上层协程处理
                throw t
            } catch (t: Throwable) {
                L.e("nextRound record failed", t)
                // 写盘失败 → 不清 messages/result/feedback/context，用户可重试
                showPanelWarning("本轮保存失败，内容已保留，请重试")
            } finally {
                recordingRound = false
            }
        }
    }

    /**
     * 提交本轮 UI 状态 — 只删除本轮 snapshot 对应的消息（按 ID），不盲目清空全部。
     * 同时修正 editingIndex/draftText 防止指向已删除的位置。
     */
    private fun commitReplyRound(consumedMessageIds: Set<String>) {
        // 消息列表、编辑位与草稿的联动全部在 ComposerStore 那一处（三处列表改动共用同一条判据）
        composer.accept(ComposerStore.Intent.ConsumeMessages(consumedMessageIds))
        feedbackCases.clearFeedbacks()
        // 结果/流式态的清空也走 reducer，不再各自写四个 StateFlow
        dispatchReply(ReplyCleared(replyUi.value.ownerRequestId ?: ""))
        // /（  N6）：用户手动结束本轮的收尾处投 ClearNote。
        // 上面 ConsumeMessages 只在这一轮真的消费了消息时顺手清备注暂存位（ids 非空才有动作），
        // 而"备注不该留到下一轮"不该依赖那次顺手的巧合：清场后备注还挂着，
        // 下一轮会把给军师的提醒再发一遍。放在 ReplyCleared 之后，
        // 结果已空 ⇒ ClearNote 的 onContentChanged 回调撞上 markCurrentResultStaleIfNeeded
        // 的"有结果才判 stale"守卫，不会在收尾之后误弹一句"输入已变化"。
        composer.accept(ComposerStore.Intent.ClearNote)
        // 新轮次开始时清理改写状态和历史，作废旧改写请求
        rewriteStore.accept(com.lovebrain.app.feature.rewrite.RewriteStore.Intent.RoundReset)
        // 新轮次恢复仅看本轮开关为默认关闭
        roundStateStore.accept(RoundStateStore.Intent.SetOnlyThisRound(false))
    }

    fun copyScheme(scheme: Scheme): String {
        // 统计复制次数
        usageStatsStore.accept(UsageStats.Event.Copied)
        return scheme.reply
    }

    // ═══════════ 记录实际发送的版本 ═══════════

    /**
     * /: 记录用户确认已发送的版本。
     *
     * 用户自行确认发送，不代表应用检测到了发送行为。
     * 确认后写为"我"的真实消息，保存用户确认来源、关联候选版本（若有）、时间。
     * 同一轮只能有一份当前最终发送记录；同一 generation version 再次确认更新替换。
     *
     * 删除同步返回 ActualSentResult.RECORDED——RECORDED 只在 Repository 确认写盘后产生。
     * 异步结果通过 actualSentState StateFlow 通知 UI。
     * 不自动绑定第一张卡——linkedSchemeIdentityKey 必须由调用方明确传入，
     * 不再从 displaySchemes 自动推断。
     * 同一 generationVersionId 的记录使用 upsert（替换），不 append 多条。
     */
    /**
     * "我确认这条真的发出去了"这一族的**行为与状态**都住在 [ActualSentRecorder]
     * （复核 第5节第2条 第 6 步：这一块搬的是行为，不是几颗字段）。
     *
     * VM 在这里只剩三件事，每件都是"别人家的知识"：交出本轮上下文的三个读数、
     * 把落盘转给仓库（含 IO 调度）、把回执/面板警告/adopt 计数接回它们各自的主人。
     * 原来这里还写着 upsert 身份、五种结果的分岔、"每次尝试先回 IDLE"那条防死路判据——
     * 那些判据现在只有一处，并且能在 JVM 上直接测（`ActualSentRecorderTest`）。
     */
    private val actualSent = ActualSentRecorder(
        scope = viewModelScope,
        readContext = {
            replyGenerationContext?.let { ActualSentRecorder.AttemptContext(it.kbName) }
        },
        readVersionKey = { versions.currentVersionIdNow?.value },
        readCandidateText = { key ->
            key?.let {
                val result = replyResult as? GenerateResult.Success
                val identity = com.lovebrain.app.model.SchemeIdentity.fromKey(it)
                if (result != null && identity != null) ReplyPatch.textOf(result.response, identity) else null
            }
        },
        writeRecord = { kbName, entry, replaces ->
            withContext(Dispatchers.IO) {
                if (replaces != null) {
                    // 替换旧记录——先删旧 entry 再追加新的（仓库那边是两步，这里只表达"替换"）
                    knowledgeRepo.replaceActualSentRecord(kbName, replaces, entry)
                } else {
                    knowledgeRepo.appendActualSentRecord(kbName, entry)
                }
            }
        },
        onWarning = { msg -> showPanelWarning(msg) },
        onNotice = { msg -> notices.show(NoticeBoard.Channel.Knowledge, msg) },
        onAdopted = { usageStatsStore.accept(UsageStats.Event.Adopted) },
        onError = { e -> L.e("recordActualSentMessage failed", e) }
    )

    /** 五种结果的原样转发：面板订阅它、并靠"一次跳变"解除「保存中」 */
    val actualSentState: StateFlow<ActualSentState> = actualSent.state

    fun dismissActualSentState() = actualSent.dismiss() // facade — delegates to ActualSentRecorder

    /** 返回这一次尝试的任务句柄；语义与 `ActualSentRecorder.record` 一致（取消要看得见） */
    fun recordActualSentMessage(
        sentText: String,
        linkedSchemeIdentityKey: String? = null
    ): kotlinx.coroutines.Job = actualSent.record(sentText, linkedSchemeIdentityKey)

    // ═══════════ KnowledgeTriggerCoordinator 事件的唯一落点 ═══════════

    /**
     * 后台引擎结果只有这一处写状态。
     *
     * 旧写法是 Coordinator 拿着本类实现的 6 方法 Callbacks 反向写 StateFlow，
     * "谁拥有状态"分散在两个类里，还要靠 originating kbName 参数在回调里补身份。
     * 现在事件自带 kbName，向量类事件仍只接受当前库的结果（防串库）。
     */
    internal fun applyTriggerEvent(event: com.lovebrain.app.domain.KnowledgeTriggerEvent) {
        when (event) {
            is com.lovebrain.app.domain.KnowledgeTriggerEvent.VectorUpdated -> {
                if (_activeKb.value?.name != event.kbName) return
                vectorStore.accept(VectorStore.Intent.UpdateVector(event.newVector, event.delta))
            }
            is com.lovebrain.app.domain.KnowledgeTriggerEvent.VectorSummary -> {
                if (_activeKb.value?.name != event.kbName) return
                notices.show(NoticeBoard.Channel.Vector, event.summary)
            }
            is com.lovebrain.app.domain.KnowledgeTriggerEvent.StageSuggested ->
                stageSuggestionStore.accept(StageSuggestionStore.Intent.Set(event.suggestion))
            is com.lovebrain.app.domain.KnowledgeTriggerEvent.Notice ->
                notices.show(NoticeBoard.Channel.Knowledge, event.message)
            is com.lovebrain.app.domain.KnowledgeTriggerEvent.ProfileReady ->
                // 画像建议绑定 originating kbName，确认时也用 suggestion.kbName 而不是 _activeKb
                profileUpdates.accept(ProfileReview.Event.Arrived(event.suggestion))
        }
    }

    //  确认画像时使用 suggestion.kbName，不使用 _activeKb
    // 使用统一的 ProfileUpdate payload，不重复解析 raw
    /**
     * 画像确认——委托 Repository 执行原子事务。
     *
     * 事务结果以 typed [ProfileTransactionResult] 返回，
     * 不再用模糊 Boolean 表示所有失败情况。
     *
     * 事务在 Repository 的单次 fileMutex.withLock 中执行：
     * - 所有文件写入、向量同步、阶段更新、warmth 标签更新在同一锁内完成
     * - backup 覆盖所有实际会被修改的文件
     * - IO 失败必须抛出（strict 版本），不吞错误
     * - 任一步失败自动 rollback——rollback 成功/失败分别返回不同 typed result
     */
    /**
     * 确认这张画像建议卡。判据与分支都在 [ProfileUpdateController.confirm] 里——
     * 那里每一条分支都是一个用户可见差别（三种前置条件失败的话不一样，
     * 只读保护那一支**不清卡**；回滚成功与回滚失败的措辞也不能合并）。
     */
    fun confirmProfileUpdate() = profileUpdates.confirm() // facade — delegates to ProfileUpdateController

    fun dismissProfileUpdate() = profileUpdates.dismiss() // facade — delegates to ProfileUpdateController

    /**
     * 画像重新生成——原地显示 loading，卡片位置不变。
     *
     * 所有失败路径（EMPTY/PROVIDER_ERROR/EXCEPTION）都必须复位 loading。
     * dismiss 后旧请求回来不得复活卡片——之后这条不再靠自增计数器手写 guard，
     * 而是由 coordinator 的租约身份判定：PROFILE_REFRESH 同类只允许一个在跑，
     * 迟到回调只有仍持有租约才被接受。
     */
    val profileRegenerating: StateFlow<Boolean> =
        busyOf(ForegroundOperationCoordinator.OperationType.PROFILE_REFRESH)

    /** 画像重新生成——见下方 regenerateProfileUpdate 的 说明 */
    fun regenerateProfileUpdate() {
        val suggestion = profileUpdates.review.value.suggestion ?: return
        val kbName = suggestion.kbName

        // 取消上一次未完成的重新生成（同类去重由 coordinator 保证，这里只是显式让位）
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.PROFILE_REFRESH)
        val requestId = ReplyRequestState.newRequestId()

        // 冷流：不传 scope，在本协程内直接 collect，
        // 取消会传播到底层模型请求、retry delay、reflect_history 写入
        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.PROFILE_REFRESH,
            requestId
        ) {
            try {
                triggerCoordinator.profileRefreshEvents(kbName).collect { event ->
                    // 迟到事件只有仍持有租约才被接受
                    if (!operationCoordinator.ownsRequest(requestId)) return@collect
                    when (event) {
                        is com.lovebrain.app.domain.KnowledgeTriggerEvent.Notice ->
                            // 重新生成失败：清掉旧建议，让"重新生成"按钮回到可点状态
                            profileUpdates.accept(ProfileReview.Event.Dismissed)
                        is com.lovebrain.app.domain.KnowledgeTriggerEvent.ProfileReady ->
                            profileUpdates.accept(ProfileReview.Event.Arrived(event.suggestion))
                        else -> Unit
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // dismiss 取消——loading 由 profileRegenerating 派生，自动归位
                throw e
            } catch (e: Exception) {
                L.e("regenerateProfileUpdate failed", e)
            }
        }
        if (lease == null) L.w("profile refresh rejected: one is already running")
    }

    // ═══════════ 谈心模式（委托 GenerationEngine） ═══════════

    /**
     * 谈心——coordinator 注册的唯一前台任务；不存在第二个 Job owner。
     * 冻结 KB 快照传入 Engine，谈心期间切 KB 不影响 prompt 与日志目标。
     */
    fun generateCounseling(userMessage: String) {
        if (userMessage.isBlank()) return
        val kbSnapshot = _activeKb.value
        val requestId = ReplyRequestState.newRequestId()
        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.COUNSELING, requestId
        ) {
            counselingStore.accept(com.lovebrain.app.feature.counseling.CounselingStore.Intent.Apply(CounselingStarted(requestId)))
            try {
                generationEngine.counselingStream(requestId, userMessage, kbSnapshot)
                    .collect { counselingStore.accept(com.lovebrain.app.feature.counseling.CounselingStore.Intent.Apply(it)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                counselingStore.accept(com.lovebrain.app.feature.counseling.CounselingStore.Intent.Apply(CounselingEnded(requestId)))
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
        counselingStore.accept(com.lovebrain.app.feature.counseling.CounselingStore.Intent.DiscardPendingChunks)
        counselingStore.accept(com.lovebrain.app.feature.counseling.CounselingStore.Intent.Apply(CounselingEnded(current.requestId)))
        L.w("user stopped counseling")
        if (counselingStore.currentResult == null) {
            counselingStore.accept(
                com.lovebrain.app.feature.counseling.CounselingStore.Intent.Fail("已手动停止")
            )
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

    fun clearCounselingAll() {
        counselingStore.accept(com.lovebrain.app.feature.counseling.CounselingStore.Intent.Clear)
        // ClearCounselingDraft 内部就是"先置空、再取消防抖尾"这个顺序——
        // 反过来会留下"清空后旧草稿被防抖任务写回"的复活竞态
        composer.accept(ComposerStore.Intent.ClearCounselingDraft)
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
            // intents.refreshForKb 现在是结构化 child（suspend），
            // 不再是 fire-and-forget sibling coroutine。
            // CancellationException 正常重抛；普通 IO 异常捕获不崩 scope。
            try {
                // KBUI-01：切库时清理上一 KB 的瞬时 vector UI（delta / update / notice）
                val oldKbName = _activeKb.value?.name
                val newKb = knowledgeRepo.getActive()
                if (oldKbName != newKb?.name) {
                    vectorStore.accept(VectorStore.Intent.ClearDelta)
                    // 上一块库的瞬时 UI（重估摘要 + 后台回执）整族清掉；面板级警告不属于这一族，
                    // 它说的是这台设备的配置状态，与切到哪块库无关——判据写在 NoticeBoard 里
                    notices.dismissVolatileNotices()
                    // 切库时复位仅看本轮开关——属于当前工作轮次
                    roundStateStore.accept(RoundStateStore.Intent.SetOnlyThisRound(false))
                    // 切库时清除旧 KB 的意图配置，防止旧意图泄漏到新 KB
                    intents.resetForKbSwitch()
                    // 切库时清空本轮瞬时纠正
                    roundCorrections.clear()
                }
                _activeKb.value = newKb
                // 等 的对象隔离：备注是按"当前对象"暂存的一份补充，切对象必须换账本。
                // 不投这一颗的话 `_subject` 恒为 null ⇒ 所有库共用同一个暂存位，
                // A 对象写的"我让军师注意…"会跟着带到 B 对象那屏（用户没要求的串味）。
                composer.accept(ComposerStore.Intent.SetSubject(newKb?.name))
                newKb?.let {
                    knowledgeRepo.migrateIfNeeded(it.name)
                    // WAL 崩溃恢复——检查未完成的 round commit 事务并 roll-forward
                    topicRecorder.recoverIfNeeded(it.name)
                    vectorStore.accept(VectorStore.Intent.SetCurrent(knowledgeRepo.readVector(it.name)))
                    // 结构化 child——在当前协程内直接 await，不再 fire-and-forget。
                    // intents.refreshForKb 内部有 KB identity guard 保护 UI commit。
                    intents.refreshForKb(it.name)
                }
                // CARRY-09：删除最后一个 KB 时 newKb==null，旧 currentVector 未被清空
                if (newKb == null) {
                    vectorStore.accept(VectorStore.Intent.SetCurrent(emptyMap()))
                }
                // KB 切换后检测 stale——如果当前结果来自旧 KB，标记为 stale
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

    // ═══════════ 主动发起/润色（委托 GenerationEngine） ═══════════

    /**
     * 主动发——coordinator 注册的唯一前台任务。
     *
     * /：「仅看本轮」与「军师备注」现在也接进这一条链，判据与回复分支同语义、
     * 同一个真源（回复那一路见 [runReplyRequest] 的 `roundStateStore.resolveOnlyThisRound(roundScope)`）：
     * 两颗值都在**生成开始时冻结一次**——不在事件流里现读。流里现读的话，
     * 生成期间拨开关/提交备注会半路改写一次已经发出去的请求该带的上下文范围，
     * 那一轮的 prompt 就成了"前半段按旧范围、后半段按新范围"的错配（ 的病根之一）。
     * 引擎形参（`proactiveStream` 的 `onlyThisRound` / `advisorNote`）早已备好并透传给
     * `buildProactiveUserPrompt`，缺的只是调用点把冻结值递进去。
     */
    fun generateProactive(draft: String = "", scene: String = "") {
        // 主动发生成入口——模式由 store 持有，结果区那一半仍归本类
        proactiveStore.accept(com.lovebrain.app.feature.proactive.ProactiveStore.Intent.EnterProactive)
        modeController.accept(ModeController.Intent.SetResultMode(ResultMode.PROACTIVE))

        val requestId = ReplyRequestState.newRequestId()
        val kbSnapshot = _activeKb.value
        val messages = composer.messageSnapshot()
        // ──  冻结点（发起这一次请求时读一次，之后本轮不再回读）──
        // 主动发入口目前没有长按变体，所以走 resolveOnlyThisRound 的默认档 FollowSwitch；
        // 用这颗函数而不是裸读 onlyThisRoundNow，是为了和回复分支共用同一颗"本轮范围"判据。
        val onlyThisRound = roundStateStore.resolveOnlyThisRound()
        // 备注真源是 ComposerStore.noteTextNow（本轮已提交的军师备注），与草稿一样按发起时刻冻结
        val advisorNote = composer.noteTextNow
        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.PROACTIVE, requestId
        ) {
            proactiveStore.accept(com.lovebrain.app.feature.proactive.ProactiveStore.Intent.Apply(ProactiveStarted(requestId)))
            try {
                generationEngine.proactiveStream(requestId, draft, kbSnapshot, messages, onlyThisRound, advisorNote)
                    .collect { proactiveStore.accept(com.lovebrain.app.feature.proactive.ProactiveStore.Intent.Apply(it)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                proactiveStore.accept(com.lovebrain.app.feature.proactive.ProactiveStore.Intent.Apply(ProactiveEnded(requestId)))
                throw e
            } catch (t: Throwable) {
                // /：非取消异常必须有个 UI 落点。此前 try 只 catch 取消，
                // 引擎冷流启动/组 prompt 抛出的异常会穿过这里，Fail 一颗不投，
                // 面板永远挂在"军师正在找切入点"上——补上 catch Throwable → Fail
                // （取消已在上面原样重抛，不会掉进这一格，第4节第1条第6条 的取消语义不动）。
                // 文案口径与回复分支同一族：ReplyFailureKind 的 userMessage，不泄露内部细节。
                L.e("generateProactive failed: ${t::class.simpleName}", t)
                proactiveStore.accept(
                    com.lovebrain.app.feature.proactive.ProactiveStore.Intent.Fail(
                        ReplyFailureKind.fromException(t).userMessage
                    )
                )
            }
        }
        if (lease == null) {
            L.w("proactive rejected: foreground slot taken")
            // 被拒绝时不把 UI 留在"主动发"空壳上（模式退回，结果区跟着归位）
            proactiveStore.accept(com.lovebrain.app.feature.proactive.ProactiveStore.Intent.ExitProactive)
        }
    }

    fun stopProactive() {
        val current = operationCoordinator.current(
            ForegroundOperationCoordinator.OperationType.PROACTIVE
        ) ?: return
        operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.PROACTIVE)
        proactiveStore.accept(com.lovebrain.app.feature.proactive.ProactiveStore.Intent.Apply(ProactiveEnded(current.requestId)))
    }

    // ═══════════ 谈心 / 主动发的事件归约 ═══════════
    //
    // Engine 不再回调 ViewModel。这三个流程的状态各自只有一个 apply 入口，
    // 并且都先拿 coordinator 当前租约核对 requestId：被取代的旧请求的迟到事件整条丢弃。
    // 用户主动"停止"的状态清理放在 stopXxx() 里做——那是用户动作，不是旧请求的事件。

    /** 该 requestId 是否仍是 [type] 当前的主人 */
    private fun ownsOperation(
        type: ForegroundOperationCoordinator.OperationType,
        requestId: String
    ): Boolean = operationCoordinator.current(type)?.requestId == requestId

    /**
     * 租约核对 + 一条被拒日志。
     *
     * 五条链的归属核对都以闭包注进 store，store 只拿到布尔；日志归本类写，
     * 这样 store 依旧不知道有日志这回事，而搬家时被弄丢的
     * `… rejected (stale requestId)` 也不会再随着下一次搬迁消失。
     */
    private fun ownsAndLog(
        type: ForegroundOperationCoordinator.OperationType,
        requestId: String,
        what: String
    ): Boolean = ownsOperation(type, requestId).also {
        if (!it) L.w("$what rejected (stale requestId=$requestId, another $type owns the slot)")
    }

    // --- 谈心 ---

    /**
     * CounselingStore 交出来的两件事：结果落盘 + 写 counseling_log.md，以及首字耗时。
     *
     * 参数全部来自事件里冻结的值（kbName / 倾诉文本 / 回复 / 分析），
     * 这里不回读 _activeKb 或草稿：生成期间切库、改草稿时，日志仍记在这轮真正归属处。
     * 这条以前只是注释，现在由 CounselingStoreTest 的
     * `persist effect carries the identity frozen in the event` 钉住。
     */
    private fun onCounselingEffect(effect: com.lovebrain.app.feature.counseling.CounselingStore.Effect) {
        when (effect) {
            is com.lovebrain.app.feature.counseling.CounselingStore.Effect.PersistResult -> {
                securePrefs.saveCounselingResult(effect.replyText)
                viewModelScope.launch {
                    saveCounselingLog(
                        effect.kbName, effect.userMessage, effect.replyText, effect.analysisText
                    )
                }
            }

            is com.lovebrain.app.feature.counseling.CounselingStore.Effect.FirstTokenObserved ->
                usageStatsStore.accept(UsageStats.Event.Timed(firstTokenMs = effect.elapsedMs))
        }
    }

    // --- 主动发起 ---

    /**
     * ProactiveStore 归约之后要**别人**做的事：跨 feature 的耗时统计，以及
     * "真拿到可展示的开场之后要不要退出主动发模式"。
     *
     * 旧实现是 reducer 里直接调"退出主动发模式"那个成员：状态持有者顺手改了 UI 会话状态，
     * 两个所有者。现在模式归 store、结果区模式归本类，两边各写各的：
     * "结束且有结果才退出"这条规则本身仍只有一处实现。
     */
    private fun onProactiveEffect(effect: com.lovebrain.app.feature.proactive.ProactiveStore.Effect) {
        when (effect) {
            com.lovebrain.app.feature.proactive.ProactiveStore.Effect.ExitedProactiveMode ->
                modeController.accept(ModeController.Intent.SetResultMode(ResultMode.REPLY))
            is com.lovebrain.app.feature.proactive.ProactiveStore.Effect.FirstTokenObserved ->
                usageStatsStore.accept(UsageStats.Event.Timed(firstTokenMs = effect.elapsedMs))
        }
    }

    // ═══════════ 持续意图（读取 / 到期改写 / 保存 / 编辑器绑定） ═══════════

    /**
     * 持续意图那一段行为的主人：状态与判据一起离开 ViewModel，这里不再持有 feature 的内部状态。
     *
     * 这一家子**没有**留同名出口：屏上那份配置、编辑器可见性、"编辑器开在哪块库"三条账，
     * 连同到期改写、KB 身份守卫、"绑定打开那一刻的库"三条判据一起走（[IntentController]）。
     * 面板从此读 `intents.config`、写 `intents.save(...)`——留一颗同名只读出口就是
     * "搬字段不减行"的复现，这次不重复它。
     *
     * VM 这一侧只剩两处**跨块协调**的窄口子：stale 判定要的意图 revision、
     * 切库时的复位与刷新；仓库读写仍由这里注入（`feature` 不许 import `data`）。
     */
    val intents = IntentController(
        scope = viewModelScope,
        readActiveKbName = { _activeKb.value?.name },
        readIntent = { kb -> knowledgeRepo.readIntent(kb) },
        writeIntent = { kb, text, enabled, expiry, expiryDate, status ->
            // 仓库那一侧自己 withContext(IO)，这里不再套第二层（与搬之前的线程形状一致）
            knowledgeRepo.saveIntent(kb, text, enabled, expiry, expiryDate, status)
        },
        onSaved = { markCurrentResultStaleIfNeeded() },
        onWarning = { msg -> showPanelWarning(msg) },
        onNotice = { msg -> notices.show(NoticeBoard.Channel.Knowledge, msg) },
        onLog = { msg -> L.w(msg) }
    )

    // ═══════════ 记忆纠正（绑定生成时冻结的 KB） ═══════════

    /**
     * 获取本轮注入的 MemoryRef 清单（供 UI 展示纠正入口）。
     * 返回生成时冻结的快照，不受后续切 KB 影响。
     */
    fun getCurrentMemoryRefs(): List<com.lovebrain.app.model.MemoryRef> {
        return replyGenerationContext?.memoryRefs ?: emptyList()
    }

    /**
     * 这一份结果**生成当时**是不是「仅看本轮」——取自生成开始时冻结的快照
     * （`ReplyGenerationContext.onlyThisRound`，:1046 那次冻），**不是**界面上那颗实时开关：
     * 开关是这一轮之后才拨的，拿实时值接显示侧会让真用过的引用凭空消失（ ）。
     */
    internal val replyResultOnlyThisRound: Boolean
        get() = replyGenerationContext?.onlyThisRound == true

    /**
     * 对指定 memoryId 发起纠正操作。
     *
     * 必须绑定生成时冻结的 KB（context.kbName），不读当前 active KB——否则切库之后
     * 一次点击会把纠正写到另一个人的库上。纠正参与下一次 PromptBuilder 过滤；
     * 所有操作可撤销、重启有效、不调用模型。
     *
     * 动作与文案的对应关系在 `MemoryCorrectionPolicy`，判定（哪种算本轮瞬时）也在那里。
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
        // THIS_ROUND mute 只存瞬时存储，不持久化——nextRound/切库自动清空
        if (MemoryCorrectionPolicy.isTransientRoundMute(action, muteDuration)) {
            roundCorrections.put(
                MemoryCorrectionPolicy.transientMute(
                    memoryId = memoryId,
                    replacementText = replacementText,
                    targetKbId = targetKbId,
                    timestamp = com.lovebrain.app.util.TimeFmt.now()
                )
            )
            notices.show(NoticeBoard.Channel.Knowledge, MemoryCorrectionPolicy.roundMuteApplied())
            return
        }
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    knowledgeRepo.saveCorrection(kbName, memoryId, action, replacementText, targetKbId, muteDuration)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 写盘抛异常不该把进程带下去：它和"返回 false"是同一种用户可见结果
                L.w("saveCorrection threw: ${e::class.simpleName}")
                false
            }
            notices.show(
                NoticeBoard.Channel.Knowledge,
                if (ok) MemoryCorrectionPolicy.applied(action) else MemoryCorrectionPolicy.applyFailed()
            )
        }
    }

    /**
     * 加载所有纠正记录——供纠正中心 UI 展示。
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

    /** 撤销纠正——纠正中心入口。没有生成上下文时允许回落到当前激活库 */
    fun undoCorrectionFromCenter(memoryId: String) = undoCorrection(memoryId, allowActiveKbFallback = true)

    /**
     * 撤销纠正——方案卡片入口。
     * 卡片属于某一轮生成，只能撤那一轮绑定的 KB，不回落到"当前激活库"。
     */
    fun undoMemoryCorrection(memoryId: String) = undoCorrection(memoryId, allowActiveKbFallback = false)

    /**
     * 两条入口共用一份实现：先看本轮瞬时 mute（不碰磁盘），否则撤持久化纠正。
     *
     * 失败一律给提示——过去只有纠正中心那条路有失败提示，卡片那条路静默，
     * 同一次失败在两个地方看得见不一样。
     */
    private fun undoCorrection(memoryId: String, allowActiveKbFallback: Boolean) {
        if (roundCorrections.remove(memoryId)) {
            notices.show(NoticeBoard.Channel.Knowledge, MemoryCorrectionPolicy.roundMuteUndone())
            return
        }
        val kbName = replyGenerationContext?.kbName
            ?: (if (allowActiveKbFallback) _activeKb.value?.name else null)
            ?: return
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { knowledgeRepo.undoCorrection(kbName, memoryId) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.w("undoCorrection threw: ${e::class.simpleName}")
                false
            }
            notices.show(
                NoticeBoard.Channel.Knowledge,
                if (ok) MemoryCorrectionPolicy.undone() else MemoryCorrectionPolicy.undoFailed()
            )
        }
    }

    // ═══════════ 单条改写（卡片内部展开 2×2 操作区） ═══════════

    /**
     * 改写链的三样东西（卡片状态 / 版本历史 / "这次回调还算不算数"）住在
     * [com.lovebrain.app.feature.rewrite.RewriteStore]（第5节第2条 第 5 步）。
     *
     * 原来这里是三本账：`RewriteLedger` 一份状态、`rewriteRequestId` 一个字符串、
     * `rewriteContextId` 又一个字符串，两个字符串谁都能写、也没有一处能单独测。
     * 现在发起时冻结成 `Identity`，终态事件必须把它带回来由 store 核对。
     *
     * `isRoundAlive` 要读的是本类的活状态（当前知识库 + 本轮消息集），
     * store 不伸手来拿——由这里注入一个"这个指纹还作数吗"的判断。
     */
    private val rewriteStore = com.lovebrain.app.feature.rewrite.RewriteStore(
        isCurrentRequest = { requestId ->
            ownsAndLog(ForegroundOperationCoordinator.OperationType.REWRITE, requestId, "rewrite result")
        },
        isRoundAlive = { contextId -> rewriteContextIdOfNow() == contextId }
    ) { effect -> onRewriteEffect(effect) }

    val rewriteStates: StateFlow<Map<String, RewriteState>> =
        rewriteStore.uiState.map { it.cardStates }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyMap())

    /** 这一轮改写要绑上去的轮次指纹——知识库名 + 本轮消息集 */
    private fun rewriteContextIdOfNow(): String =
        (_activeKb.value?.name ?: "") + "_" + (replyGenerationContext?.messageIds?.hashCode() ?: 0)

    /**
     * 对指定方案卡发起单条改写。
     *
     * 使用 SchemeIdentity(source+tag) 区分 STYLE(A/B/C/D) 和 DIRECTION(F/E/X/S)。
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
        if (replyUi.value.isBusy ||
            operationCoordinator.isBusy(ForegroundOperationCoordinator.OperationType.PROACTIVE)) return

        // 首轮流式尚未完成时禁用改写
        if (replyUi.value.isBusy) return

        val result = replyResult as? GenerateResult.Success ?: return
        val response = result.response
        // 身份键区分 STYLE 与 DIRECTION；目标正文只从 ReplyPatch 取一次
        val identity = com.lovebrain.app.model.SchemeIdentity(source, schemeTag)
        val identityKey = identity.key
        val targetReply = ReplyPatch.textOf(response, identity) ?: return
        if (targetReply.isBlank()) return

        // 锁定目标 + 绑轮次身份：发起时一次冻结，之后只带回来给 store 核对
        val ctx = replyGenerationContext ?: return
        val rewriteIdentity = com.lovebrain.app.feature.rewrite.RewriteStore.Identity(
            requestId = java.util.UUID.randomUUID().toString(),
            contextId = rewriteContextIdOfNow(),
            identityKey = identityKey,
            option = option
        )
        // 被替换那一版的赞/踩在点击这一刻就取定：撤销时要连正文带反馈一起回来，
        // 不能等任务体跑起来再看——那期间用户可能已经改了对旧文的反馈。
        val previousFeedback = feedbackCases.feedbackFor(identityKey)

        // 不再捕获 preRewriteFeedback 做后续清理——
        // 改写期间用户对旧文的反馈继续归旧版本；新版本独立 NONE。

        val ticket = ticketStore.activeTicketNow
        val apiKey = ticket?.let { securePrefs.getWorkerApiKey(it.id) }

        // 改写也进 coordinator 账本。旧实现只把 Job 存进 rewriteJob 字段，
        // 协调器完全不知道有这个任务，"六类前台操作统一协调"就是假的。
        val lease = operationCoordinator.start(
            ForegroundOperationCoordinator.OperationType.REWRITE,
            rewriteIdentity.requestId
        ) {
            // "改写中"与历史压栈放在任务体里、不在发起前做：
            // 旧写法先 begin 再 start，而协调器**会**拒绝（前台槽位被占、或第二次点击同一类），
            // 被拒的那次 body 一次都不跑，于是卡片永久转圈、在途身份也被一个不存在的请求占着。
            rewriteStore.accept(
                com.lovebrain.app.feature.rewrite.RewriteStore.Intent.Begin(
                    identity = rewriteIdentity,
                    previousReply = targetReply,
                    previousFeedback = previousFeedback
                )
            )
            try {
                // 构建短请求——只包含最少必要上下文
                val systemPrompt = buildRewriteSystemPrompt()
                val userPrompt = buildRewriteUserPrompt(
                    originalReply = targetReply,
                    option = instruction,
                    messages = ctx.messages,
                    intentText = ctx.intentText.takeIf { it.isNotBlank() && ctx.intentEnabled },
                    ideaHint = ctx.ideaHint.takeIf { it.isNotBlank() }
                )

                // 复用供应商配置冻结
                val config = if (ticket != null && !apiKey.isNullOrBlank() && ticket.model.isNotBlank()) {
                    com.lovebrain.app.model.ProviderRequestConfig(
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

                // 三道身份核对整个交回 store：被取代 / 协调器换人 / 轮次已翻页。
                // 空正文与异常同样走 store，"这句文案归谁"不再靠这里手写 setState。
                val newReply = raw.trim()
                if (newReply.isBlank()) {
                    rewriteStore.accept(
                        com.lovebrain.app.feature.rewrite.RewriteStore.Intent.Emptied(rewriteIdentity)
                    )
                } else {
                    rewriteStore.accept(
                        com.lovebrain.app.feature.rewrite.RewriteStore.Intent.Succeeded(
                            identity = rewriteIdentity, newReply = newReply
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e("rewriteScheme failed", e)
                rewriteStore.accept(
                    com.lovebrain.app.feature.rewrite.RewriteStore.Intent.Failed(rewriteIdentity)
                )
            }
        }
        if (lease == null) L.w("rewrite rejected: foreground slot taken")
    }

    /**
     * RewriteStore 交出来的动作：贴正文、清反馈、计一次数、停任务、贴回旧版本。
     *
     * 只替换目标卡正文，不回写整个捕获的旧 response——改写期间其他卡可能已经变了。
     * 新正文的赞踩独立从 NONE 开始，旧赞留在历史里，撤销时一起回来。
     *
     * internal 不为了好看：这条"store 交出来 → VM 落到结果上"的接缝是搬家时新长出来的，
     * 全仓只有这里做这件事，而把它跑通本来要真机点一次改写。测试直接喂 Effect 钉住落地结果。
     */
    internal fun onRewriteEffect(effect: com.lovebrain.app.feature.rewrite.RewriteStore.Effect) {
        when (effect) {
            is com.lovebrain.app.feature.rewrite.RewriteStore.Effect.ApplyRewrite -> {
                val identity = com.lovebrain.app.model.SchemeIdentity.fromKey(effect.identityKey) ?: return
                val current = replyResult as? GenerateResult.Success ?: run {
                    // 走到这里说明身份核对后结果又被换掉了：不贴正文，也不装作贴了
                    L.w("rewrite result dropped: no live result to patch")
                    return
                }
                replyStore.accept(ReplyStore.Intent.ReplaceResult(
                    GenerateResult.Success(
                        ReplyPatch.withText(current.response, identity, effect.newReply)
                    )
                ))
            }

            is com.lovebrain.app.feature.rewrite.RewriteStore.Effect.ResetFeedback ->
                feedbackCases.putFeedback(effect.identityKey, SchemeFeedback.NONE)

            com.lovebrain.app.feature.rewrite.RewriteStore.Effect.RewriteCounted -> {
                usageStatsStore.accept(UsageStats.Event.Rewritten)
            }

            is com.lovebrain.app.feature.rewrite.RewriteStore.Effect.StopRunningRewrite -> {
                val current = operationCoordinator.current(
                    ForegroundOperationCoordinator.OperationType.REWRITE
                )
                // 只停它自己那一个：卡片上点"取消"不该把别的在跑任务一起杀掉
                if (current?.requestId == effect.requestId) {
                    operationCoordinator.stopCurrent(ForegroundOperationCoordinator.OperationType.REWRITE)
                }
            }

            is com.lovebrain.app.feature.rewrite.RewriteStore.Effect.RestoreVersion -> {
                val identity = com.lovebrain.app.model.SchemeIdentity.fromKey(effect.identityKey) ?: return
                val current = replyResult as? GenerateResult.Success ?: return
                replyStore.accept(ReplyStore.Intent.ReplaceResult(
                    GenerateResult.Success(
                        ReplyPatch.withText(current.response, identity, effect.reply)
                    )
                ))
                // 撤销时恢复旧版本的反馈，不只是正文
                feedbackCases.putFeedback(effect.identityKey, effect.feedback)
                // 到这一步才算真撤销：store 这时才把那一版从历史里丢掉并清掉卡片状态
                rewriteStore.accept(
                    com.lovebrain.app.feature.rewrite.RewriteStore.Intent.UndoCommitted(effect.identityKey)
                )
            }
        }
    }

    /** 取消正在进行的改写 —— 使用 identityKey */
    fun cancelRewrite(identityKey: String) { // facade — delegates to RewriteStore
        rewriteStore.accept(
            com.lovebrain.app.feature.rewrite.RewriteStore.Intent.RequestCancel(identityKey)
        )
    }

    /**
     * 撤销改写——恢复到上一版本（含正文和反馈）—— 使用 identityKey。
     *
     * 两步式：store 只 peek 并发 [com.lovebrain.app.feature.rewrite.RewriteStore.Effect.RestoreVersion]，
     * 这里真的贴回去了才回 `UndoCommitted`。旧写法先 `pop()` 再检查"结果还在不在、
     * key 解不解得开"，任何一步失败就直接 return——**弹掉的那一版永久丢了**，卡片还挂着 Done。
     */
    fun undoRewrite(identityKey: String) { // facade — delegates to RewriteStore
        rewriteStore.accept(
            com.lovebrain.app.feature.rewrite.RewriteStore.Intent.UndoRequested(identityKey)
        )
    }

    /** 清除改写状态（展开/收起时调用）—— 使用 identityKey */
    fun clearRewriteState(identityKey: String) { // facade — delegates to RewriteStore
        rewriteStore.accept(
            com.lovebrain.app.feature.rewrite.RewriteStore.Intent.Collapse(identityKey)
        )
    }

    /** 改写系统提示——固定短规则，正文在 `RewritePrompt`（可离线单测） */
    private fun buildRewriteSystemPrompt(): String = RewritePrompt.system

    /**
     * 组装改写请求：表达偏好来自知识库，其余是本轮冻结上下文。
     *
     * 拼装规则本身在纯函数 `RewritePrompt` 里（可离线单测）；这里只负责读 style.md。
     */
    private suspend fun buildRewriteUserPrompt(
        originalReply: String,
        option: String,
        messages: List<ChatMessage>,
        intentText: String?,
        ideaHint: String?
    ): String {
        val kbName = replyGenerationContext?.kbName
        val style = if (kbName.isNullOrBlank()) "" else knowledgeRepo.readFile(kbName, "understand/style.md")
        return RewritePrompt.user(
            originalReply = originalReply,
            instruction = option,
            recentChat = RewritePrompt.recentChat(messages),
            intentText = intentText,
            ideaHint = ideaHint,
            style = style
        )
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
        // 取消防抖尾 + 同步直写最终草稿（正常关闭悬浮窗零丢失）
        composer.flushCounselingDraft()
        // 统一走 coordinator 关闭，不再逐个 cancel 手里的 Job 字段。
        // 旧写法会漏掉没被字段覆盖的任务（画像刷新、流式刷新定时器）。
        replyStore.accept(ReplyStore.Intent.DiscardPendingChunks)
        counselingStore.accept(com.lovebrain.app.feature.counseling.CounselingStore.Intent.DiscardPendingChunks)
        operationCoordinator.shutdownAll()
    }

    /**
     * 冻结生成输入时的"可选读取"。
     *
     * 读失败按缺项继续（这几项缺失只让 prompt 降级，不该让整次生成失败），
     * 但**取消信号必须原样上抛**。此前直接 `runCatching { 挂起读 }`，
     * 会把"用户点了停止 / 页面已销毁"当成"读取失败"吞掉，
     * 正是全仓取消信号审计（scripts/audit_cancellation.py）点名的形态。
     */
    private suspend fun <T> readOrNull(fallback: T, read: suspend () -> T): T =
        try {
            read()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback
        }

    companion object {
        /** 金额格式化（固定三位小数 + 固定 Locale.US 小数点，防区域格式回归；展示条字号钉死） */
        // 花费保留三位小数
        internal fun formatYuan(yuan: Double): String = String.format(java.util.Locale.US, "%.3f", yuan)
        // 今日花费的跨天滚动不再住在这里：它是 UsageStats.loaded 的一条判据，
        // 与"今日那一格"同属一份状态（原先这里一个函数、类里一个 todayCostDate var，两把尺）
    }
}
