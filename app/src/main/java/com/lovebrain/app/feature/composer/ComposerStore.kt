package com.lovebrain.app.feature.composer

import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.PanelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 输入区此刻在写**哪一种**内容（ 三轴之一）。
 *
 * 它和下面两件事是三回事，以前全被塞进同一颗 `currentRole` 里，于是长出用户报的那个 bug：
 * 选完《想法》再长按捕获一条消息，消息前面的标识还是《她》——因为"我在写什么"和
 * "自动捕获回来的那句属于谁"是同一条状态，谁后写谁赢。
 *
 * - [HER] / [ME]：手工补一条**真实聊天**里的话，落进消息列表，进 recent/画像事实的候选。
 * - [SUPPLEMENT]：本轮**军师备注**（原《想法》），落进 [ComposerStore.noteText]，
 *   只走 `GenerationInput.replyDirective` 那一条既有边界，绝不进消息列表。
 */
enum class ComposerInputKind {
    HER,
    ME,
    SUPPLEMENT;

    /**
     * 落进真实聊天集合时对应的那一方。
     *
     * [SUPPLEMENT] 给 `null`——**它没有对应方**。任何"备注就当是我说的话存进去"的写法
     * 都必须先绕过这个 null，那就是这条要禁掉的串义。
     */
    val realRole: ChatMessage.Role?
        get() = when (this) {
            HER -> ChatMessage.Role.HER
            ME -> ChatMessage.Role.ME
            SUPPLEMENT -> null
        }
}

/**
 * 此刻手指在改**哪一个对象**（ 三轴之三）。
 *
 * 判据是稳定身份，不是"上一个选的角色"：编辑一条真实消息要它的 [RealMessage.id]，
 * 编辑本轮备注是 [Note]，两者共用输入框但不共用落点。以前只有 `editingIndex` 一个下标，
 * 于是"下标 + 当前角色"这两个猜测拼出来的落点，会在列表被动过之后贴到别的消息上。
 */
sealed interface ComposerEditingTarget {
    /** 没有在编辑任何东西——输入框里那段是**新**内容 */
    data object None : ComposerEditingTarget

    /** 正在编辑某一条真实消息（按稳定 id 认，不按下标猜） */
    data class RealMessage(val id: String) : ComposerEditingTarget

    /** 正在编辑本轮军师备注的完整正文 */
    data object Note : ComposerEditingTarget
}

/**
 * 输入区与消息列表的状态持有者（复核 第5节第2条 第 6 步"VM 不再是状态壳"的第一块）。
 *
 * 它接管 VM 里那九颗各自可写的 `MutableStateFlow`：面板状态、消息列表、当前角色、
 * 编辑位、《想法》chip 态、两条草稿、输入/输出模式、计划面板。搬之前它们的写法是
 * "谁需要谁 `_messages.value = …`"，共 11 处；其中"改完列表要顺手修正编辑位"这件事
 * 在三个地方各抄了一遍（[MessageListEditing] 的 KDoc 记着这段历史）。
 *
 * 三条不变量，就是这个类存在的全部理由：
 * 1. **唯一写入漏斗**——外部只能 [accept] 意图，状态 flow 全是私有的；
 * 2. **编辑位与草稿的联动只在这里发生**（删掉/耗尽正在编辑的那条 → 编辑位归 -1 且草稿清空）；
 * 3. **`onContentChanged` 与状态写入同帧**——"内容变了要旧结果标 stale"这件事由调用方决定怎么做，
 *    但**什么时候该想这件事**由这里决定，漏一处就得到一个静默的旧结果。
 *
 * 为什么副作用是同步回调而不是一条 flow：VM 现在就是在 `setDraft` 那一刻同步跑
 * `checkInputChanged()` 的，既有测试与真机门禁证据都按这个时序写。改成异步会把
 * "喂完意图就断言状态"变成"还得等一次调度"，那是拿结构改进换一批时序漂移。
 *
 * 持久化不在这个类里：它只交出 [savePanelMode] / [persistCounselingDraft] / [saveOutputMode]
 * 三个回调，所以 store 不知道有 `SecurePrefs` 这回事（第5节第1条 的包边界：`feature` 不许 import `data`）。
 *
 * ##  之后：三轴各有主人，谁都不许替谁猜
 * | 轴 | 存在哪 | 谁能改它 |
 * | --- | --- | --- |
 * | 捕获角色（自动捕获的真实聊天是她还是我） | [_currentRole]，恒 ∈ {HER, ME} | 只有选她/我（[ComposerInputKind.HER] / [ComposerInputKind.ME]） |
 * | 输入对象（此刻输入框写的是她/我/补充） | [_inputKind] | 三颗 chip、[Intent.BeginNoteEdit] |
 * | 编辑对象（正在改哪条真实消息，还是本轮备注） | [_editingTarget] | 列表行、灰字行、提交/删除/重排后的收口 |
 *
 * `_ideaComposeMode` 与 `_editingIndex` 还在，但它们是**投影**不是账本：只由
 * [applyInputKind] / [applyEditingTarget] 这两个私有漏斗跟着真值写，别处一律读
 * （宿主 [com.lovebrain.app.ui.panel.LoveBrainPanelScreen] 与 `FloatingService` 的捕获链
 * 仍在读这两个旧名字，所以字段不许删；删了就等于把捕获链的读源换成没人维护的那一颗）。
 */
class ComposerStore(
    private val scope: CoroutineScope,
    /** 谈心草稿防抖窗口。测试要能改小，否则每测一次防抖都要真等 600ms。 */
    private val draftDebounceMs: Long = DEFAULT_DRAFT_DEBOUNCE_MS,
    initialPanelMode: Int = 0,
    initialOutputMode: Int = 0,
    /** 消息/草稿内容变化——调用方借此判断"旧结果是否该标 stale"。同步调用，见类 KDoc。 */
    private val onContentChanged: () -> Unit = {},
    private val savePanelMode: (Int) -> Unit = {},
    private val persistCounselingDraft: (String) -> Unit = {},
    private val saveOutputMode: (Int) -> Unit = {},
    /**
     * 输出模式的校验出口——"直出/进攻"的合法组合规则住在 prompt 配置层，不在这里复制一份。
     * 默认恒等，供不需要校验的调用方（与测试）使用。
     */
    private val normalizeOutputMode: (Int) -> Int = { it }
) {

    /** 唯一入口：外部只能投意图。状态 flow 全是私有字段。 */
    sealed interface Intent {
        data class SetPanelState(val state: PanelState) : Intent
        data class SetDraft(val text: String) : Intent
        data class SetCounselingDraft(val text: String) : Intent
        data class SetPanelMode(val mode: Int) : Intent
        data class SetOutputMode(val mode: Int) : Intent

        /**
         * 旧调用点（宿主、捕获链）仍在投这一颗：`IDEA` 被读成"输入对象 = 补充"，
         * 且**不动捕获角色**。新代码请投 [SetInputKind]。
         */
        data class SetCurrentRole(val role: ChatMessage.Role) : Intent
        data class SetEditingIndex(val index: Int) : Intent

        /** 三轴分开的正解：只说"输入框现在写哪一种内容"，捕获角色跟着 HER/我走、补充不动它 */
        data class SetInputKind(val kind: ComposerInputKind) : Intent

        /** 点在消息卡下面那行灰字：把本轮备注的完整正文交回输入框继续改（ 的编辑入口） */
        data object BeginNoteEdit : Intent

        /** 换对象（切知识库）：备注按对象各存各的，A 对象的备注不会跟着去 B */
        data class SetSubject(val name: String?) : Intent

        /**
         * 提交本轮备注正文（新写的正解入口）。
         *
         * 与 [Intent.AddMessage] 交 `Role.IDEA` 那条旧通道等价——留着旧通道是因为宿主与
         * `FloatingService` 还在按 `composeRole` 投它；接线时换成这一颗就不再有人
         * 把"补充"当成一种消息角色往下传了。
         */
        data class SubmitNote(val text: String) : Intent

        /** 显式清掉本轮备注（"结束本轮"之外的清位，例如面板主动作废） */
        data object ClearNote : Intent

        data class AddMessage(val role: ChatMessage.Role, val content: String) : Intent
        data class UpdateMessage(val index: Int, val role: ChatMessage.Role, val content: String) : Intent
        data class RemoveMessage(val index: Int) : Intent
        data class RemoveMessageById(val id: String) : Intent
        data class ReorderMessages(val from: Int, val to: Int) : Intent

        /** 本轮已提交的消息从列表里消耗掉（下一步整轮换新的输入），同时修正编辑位 */
        data class ConsumeMessages(val ids: Set<String>) : Intent

        /** 进程内恢复：只改状态，**不写回持久层**（写回是用户动作的结果，不是启动的副作用） */
        data class RestorePanelMode(val mode: Int) : Intent
        data class RestoreCounselingDraft(val text: String) : Intent
        /** 启动时把"已按持久层校验过的"输出模式放进来——校验与落盘由调用方在它之前做完 */
        data class RestoreOutputMode(val mode: Int) : Intent

        /** 清空谈心草稿：状态与防抖尾一起处理，防"清空后旧草稿被防抖任务写回"那一类复活竞态 */
        data object ClearCounselingDraft : Intent
    }

    /**
     * 一次生成的冻结输入对（[freezeRoundInput] 的产物）：真实对话 + 军师备注原文。
     *
     * 两个字段必须成对出现、成对使用——它们是同一次读的快照。备注不是消息，所以它不在
     * [messages] 里；它自己占 [GenerationFingerprints][com.lovebrain.app.domain.GenerationFingerprints]
     * 的一维，所以"改了备注"永远是"这一轮的输入变了"，旧结果据此判过时。
     */
    data class FrozenRoundInput(
        /** 只含 HER/ME 的真实对话（含已按稳定 ID 落回去的编辑草稿） */
        val messages: List<ChatMessage>,
        /** 本轮军师备注原文（已提交的 + 补充档里未提交的草稿；空 = 这一轮没写备注） */
        val note: String
    )

    private val _panelState = MutableStateFlow(PanelState.KEYBOARD)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _currentRole = MutableStateFlow(ChatMessage.Role.HER)
    private val _editingIndex = MutableStateFlow(-1)
    private val _ideaComposeMode = MutableStateFlow(false)
    private val _draftText = MutableStateFlow("")
    private val _counselingDraft = MutableStateFlow("")
    private val _panelMode = MutableStateFlow(initialPanelMode)
    private val _outputMode = MutableStateFlow(initialOutputMode)

    // ──  新增的三轴真值（见类 KDoc 那张表） ────────────────────────────
    private val _inputKind = MutableStateFlow(ComposerInputKind.HER)
    private val _editingTarget = MutableStateFlow<ComposerEditingTarget>(ComposerEditingTarget.None)
    private val _subject = MutableStateFlow<String?>(null)

    /**
     * 本轮军师备注的正文（**当前对象**那一份）。
     *
     * 它是 `GenerationInput.replyDirective` 唯一的可写来源：不存在第二个"想法状态"和它并行
     * （第6节第1条 点名禁的就是那件事）。写它只有三条路：提交补充、编辑备注后提交、清位。
     */
    private val _replyNote = MutableStateFlow("")

    /** 备注按对象暂存：切到 B 不会看见 A 的备注，切回 A 又拿回来（第6节第3条 生命周期） */
    private val noteStash = linkedMapOf<String, String>()

    val panelState: StateFlow<PanelState> = _panelState.asStateFlow()
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** 捕获角色：自动捕获回来的真实聊天算她还是算我。恒 ∈ {HER, ME}，选《补充》不碰它。 */
    val currentRole: StateFlow<ChatMessage.Role> = _currentRole.asStateFlow()

    /** 编辑位（旧口径投影：真实消息在列表里的下标；备注/无 = -1） */
    val editingIndex: StateFlow<Int> = _editingIndex.asStateFlow()

    /** 输入对象（她/我/补充）——UI 的三颗 chip 该亮哪一颗读这一颗 */
    val inputKind: StateFlow<ComposerInputKind> = _inputKind.asStateFlow()

    /** 编辑对象（哪条真实消息 / 本轮备注 / 无） */
    val editingTarget: StateFlow<ComposerEditingTarget> = _editingTarget.asStateFlow()

    /** 已提交的本轮备注正文（不含输入框里还没提交的那半句） */
    val noteText: StateFlow<String> = _replyNote.asStateFlow()

    /** 旧口径投影：`inputKind == SUPPLEMENT`。只由 [applyInputKind] 写。 */
    val ideaComposeMode: StateFlow<Boolean> = _ideaComposeMode.asStateFlow()

    val draftText: StateFlow<String> = _draftText.asStateFlow()
    val counselingDraft: StateFlow<String> = _counselingDraft.asStateFlow()
    val panelMode: StateFlow<Int> = _panelMode.asStateFlow()
    val outputMode: StateFlow<Int> = _outputMode.asStateFlow()

    /** 谈心草稿防抖写盘任务（取消旧任务 + 延迟落盘，防每击键一次加密写盘） */
    private var draftPersistJob: Job? = null

    // ── 只读快照：内部读状态一律走这里，不存在第二个可写的账 ──────────────
    val messagesNow: List<ChatMessage> get() = _messages.value
    val draftTextNow: String get() = _draftText.value
    val counselingDraftNow: String get() = _counselingDraft.value
    val editingIndexNow: Int get() = _editingIndex.value
    val currentRoleNow: ChatMessage.Role get() = _currentRole.value
    val ideaComposeModeNow: Boolean get() = _ideaComposeMode.value
    val outputModeNow: Int get() = _outputMode.value
    val inputKindNow: ComposerInputKind get() = _inputKind.value
    val editingTargetNow: ComposerEditingTarget get() = _editingTarget.value
    val subjectNow: String? get() = _subject.value
    val noteTextNow: String get() = _replyNote.value

    fun accept(intent: Intent) {
        when (intent) {
            is Intent.SetPanelState -> _panelState.value = intent.state
            is Intent.SetDraft -> setDraft(intent.text)
            is Intent.SetCounselingDraft -> setCounselingDraft(intent.text)
            is Intent.SetPanelMode -> {
                // 值域只有一颗口径：0=回复、1=谈心，与下面 [Intent.RestorePanelMode] 那道 `in 0..1`
                // 门同一档。这里**必须**夹，而且要连进盘那半句一起夹——反例分两半：
                // 1. 直写 `intent.mode` ⇒ `panelMode` 这颗"哪一页在上面"的唯一真 owner 收到界外值。
                //    宿主那边 `currentPage = panelModeToPage(panelMode)` 把它归一回 0，页头那两段也归 0，
                //    于是**内存里的档与画出来的页不是同一颗数**：投 2 的调用方以为自己在谈心那一面，
                //    画面却是回复那一面（第二本账的形状，正是 第12节第2条 要禁的那一条）。而只要有任何一处
                //    把 mode 当页索引直用（`PanelPagePager` 的槽位循环是 `0 until pageCount`，
                //    currentPage=2 时 `index == currentPage` 永远不成立、两页都被 `(index - currentPage)`
                //    推出屏外），面板就画空页。
                // 2. 界外值经 `savePanelMode` 进盘 ⇒ 下次冷启动 `RestorePanelMode` 只收 0..1 把它丢掉，
                //    落盘的档与恢复出来的档静默分叉，没有任何一处会报错。
                // 夹法取"落回最近的那一档"：2 → 1（谈心）、-1 → 0（回复），与宿主页两段切换器
                // 能给出的取值一致；`FloatingService` 的 `EventBus.PanelRequest(mode)` 是外部可投的
                // 入口（首页旧卡片还留着  之前那一段的编号），所以这颗 reducer 不能信投来的值。
                val mode = intent.mode.coerceIn(0, 1)
                _panelMode.value = mode
                savePanelMode(mode)
            }
            is Intent.SetOutputMode -> {
                val mode = normalizeOutputMode(intent.mode)
                _outputMode.value = mode
                saveOutputMode(mode)
            }
            is Intent.SetCurrentRole ->
                // 兼容入口：旧宿主与 `FloatingService` 还在投这颗。
                // `IDEA` 只表示"输入对象 = 补充"，**不落捕获角色**——真实聊天捕获的角色
                // 恒 ∈ {HER, ME}，否则捕获回来的消息会被误标成上一条选中的那种内容。
                applyInputKind(intent.role.toInputKind())
            is Intent.SetInputKind -> applyInputKind(intent.kind)
            is Intent.SetEditingIndex -> setEditingIndexByIndex(intent.index)
            is Intent.BeginNoteEdit -> beginNoteEdit()
            is Intent.SetSubject -> switchSubject(intent.name)
            is Intent.SubmitNote -> if (intent.text.isNotBlank()) commitSupplement(intent.text.trim())
            Intent.ClearNote -> clearNote()
            is Intent.AddMessage -> addMessage(intent.role, intent.content)
            is Intent.UpdateMessage -> updateMessage(intent.index, intent.role, intent.content)
            is Intent.RemoveMessage -> removeMessage(intent.index)
            is Intent.RemoveMessageById -> removeMessageById(intent.id)
            is Intent.ReorderMessages -> reorderMessages(intent.from, intent.to)
            is Intent.ConsumeMessages -> consumeMessages(intent.ids)
            is Intent.RestorePanelMode -> if (intent.mode in 0..1) _panelMode.value = intent.mode
            is Intent.RestoreOutputMode -> _outputMode.value = intent.mode
            is Intent.RestoreCounselingDraft ->
                if (intent.text.isNotEmpty()) _counselingDraft.value = intent.text
            Intent.ClearCounselingDraft -> clearCounselingDraft()
        }
    }

    // ── 派生读取（原本在 VM 里的两个纯函数，输入全是这里的私有状态） ────────

    /**
     * 本轮**军师备注**的当前正文 = 已提交的那一份 + 输入框里还没提交的"补充"草稿。
     *
     * 两个用途，同一个真源，不存在第二本账：
     * 1. 生成时进 `GenerationInput.replyDirective`（[ideaHintOf]）；
     * 2. 消息卡下面那行灰字"我让军师注意：…"读的就是这一颗（）——灰字只是**展示**，
     *    它自己不保存任何东西，所以点它编辑完再回来，看到的必然就是会被发出去的那段正文。
     */
    fun ideaHint(): String = effectiveNoteText(_messages.value)

    /**
     * 给定一份消息列表（通常是本轮的冻结快照）算备注正文，草稿仍取**当前**那条。
     *
     * 这不是笔误：`generate()` 要的是"这一轮消息 + 用户此刻输入框里还没提交的那半句补充"，
     * 所以它传快照、但不传草稿。搬之前 `collectIdeaHintWithDraft(snapshot)` 就是这个形状。
     *
     * ⚠ **只有"补充"模式的未提交草稿会进这里**（ 修的就是这颗函数）。HER/ME 的草稿属于
     *   那条真实消息，走 [messageSnapshot] 落进列表，绝不借备注通道混进 replyDirective。
     */
    fun ideaHintOf(messages: List<ChatMessage>): String = effectiveNoteText(messages)

    /**
     * 构建本轮生成的冻结消息快照——深拷贝、**只留真实聊天（HER/ME）**，
     * 并把未提交的编辑草稿按**稳定消息 ID** 落进对应那一条、一次。
     *
     * 它是 [freezeRoundInput] 的一半，逐字同源：对话与备注必须出自**同一次读**，
     * 否则"发起生成时冻的那一份"和"事后判过时时现读的那一份"会算出两个数。
     *
     * 按 ID 而不是按下标写回：草稿应用期间列表可能已经被增删/拖动过，
     * 按下标会得到"我在改第 2 条，实际改到第 3 条"那种静默错配。
     *
     * 三处收口，都是  点名的：
     * - 编辑一条真实消息时，草稿**只**应用到那一条一次，不再同步拼进备注
     *   （以前 [ideaHintOf] 无条件拼草稿，于是"改她的一句话"顺手把那句话变成了军师备注）；
     * - 编辑中的那条按它**自己的**角色落回列表——即使此刻输入行亮着《补充》（宿主点气泡编辑
     *   并不会把 chip 切回她/我），也不因此跳过这一步；
     * - 备注本身永远不写进列表：没有编辑位、或编辑位是备注时，这里一个字都不动列表，
     *   所以备注不可能被重排进真实聊天顺序里。
     */
    fun messageSnapshot(): List<ChatMessage> = freezeRoundInput().messages

    /**
     * 本轮生成的**冻结输入对**：真实对话 + 备注原文，一次读算出两者（第6节第2条"生成启动时冻结备注"）。
     *
     * 为什么要有这颗，而不是让调用方各读一次 [messageSnapshot] 与 [ideaHintOf]：
     * 那两个出口各自现读状态，中间只要隔了一个挂起点（生成链里 `withContext(Dispatchers.IO)`
     * 读盘那类），拿到的就是"上一条消息 + 下一条备注"这种从未存在过的组合——
     * 发出去的 prompt 与事后判 stale 时比的指纹因此对不上，旧结果会被误判成"输入变了"，
     * 而真正变了的那一维反倒可能没被看见。这一颗把两个读数绑成一次读、一个不可变对象，
     * 冻结的责任回到持有状态的一方，而不是每个调用点各自记得先读后异步。
     *
     * [source] 是给过渡期那份"列表里还躺着 `Role.IDEA` 行"的历史数据用的：
     * 同一颗函数把残留行**同时**从对话里摘掉、折进备注正文，所以对话与备注两边
     * 得到的必然是同一个数（旧写法先摘行、再拿摘过的列表算备注，正文就在这一步静默丢掉）。
     */
    fun freezeRoundInput(source: List<ChatMessage> = _messages.value): FrozenRoundInput =
        FrozenRoundInput(
            messages = applyEditingDraft(realChatOf(source)),
            note = effectiveNoteText(source)
        )

    /**
     * 真实聊天集合的唯一派生口径：摘掉过渡期残留的 `Role.IDEA` 行、逐条深拷贝。
     *
     * 摘行不等于丢正文——正文由 [effectiveNoteText] 折进备注，两者必须在同一次读里配对，
     * 这就是 [freezeRoundInput] 存在的原因。
     */
    private fun realChatOf(messages: List<ChatMessage>): List<ChatMessage> =
        messages.filter { it.role != ChatMessage.Role.IDEA }.map { it.copy() }

    private fun applyEditingDraft(real: List<ChatMessage>): List<ChatMessage> {
        val editingDraft = _draftText.value.trim()
        val target = _editingTarget.value
        if (editingDraft.isBlank() || target !is ComposerEditingTarget.RealMessage) {
            return real
        }
        val idx = real.indexOfFirst { it.id == target.id }
        if (idx < 0) return real
        // 角色优先按输入对象给；《补充》档亮着时 `realRole` 是 null —— 那就用那一条自己的身份，
        // 绝不因为"chip 不是她/我"就跳过这一步（旧写法跳过 ⇒ 编辑中的那句既没进对话快照，
        // 又被 effectiveNoteText 并进备注，一次编辑同时得到两个落点）。
        // 也不许把真实消息的身份改成 IDEA：备注永远不是消息角色。
        val role = _inputKind.value.realRole
            ?: real[idx].role.takeUnless { it == ChatMessage.Role.IDEA }
            ?: return real
        return real.mapIndexed { i, msg -> if (i == idx) msg.copy(role = role, content = editingDraft) else msg }
    }

    /** 关闭时把防抖尾的草稿同步落盘（正常关闭零丢失），并停掉那个还没跑到的任务 */
    fun flushCounselingDraft() {
        draftPersistJob?.cancel()
        draftPersistJob = null
        persistCounselingDraft(_counselingDraft.value)
    }

    private fun setDraft(text: String) {
        _draftText.value = text
        // 草稿参与 messageSnapshot() 与 effectiveNoteText()，所以它一变，旧结果就该被判 stale
        onContentChanged()
    }

    private fun setCounselingDraft(text: String) {
        _counselingDraft.value = text
        draftPersistJob?.cancel()
        draftPersistJob = scope.launch {
            delay(draftDebounceMs)
            persistCounselingDraft(text)
        }
    }

    private fun clearCounselingDraft() {
        _counselingDraft.value = ""
        draftPersistJob?.cancel()
        draftPersistJob = null
    }

    private fun addMessage(role: ChatMessage.Role, content: String) {
        if (content.isBlank()) return
        if (role.toInputKind() == ComposerInputKind.SUPPLEMENT) {
            // 只有"补充"这一种**内容**会走到这里（宿主交来的 composeRole = IDEA）。
            // 自动捕获永远交 HER/ME 进来，所以它在《补充》亮着时也照样落进真实聊天——
            // 这一条就是用户报的"选完想法，下一条消息被标成上一个角色"的断点。
            commitSupplement(content.trim())
            return
        }
        _messages.value = _messages.value + ChatMessage(role = role, content = content.trim())
        onContentChanged()
    }

    /**
     * 提交一次编辑——**落点只认 [ComposerEditingTarget] 那个身份**，不认输入行此刻亮着哪颗 chip。
     *
     * 这是  三轴分工在这里唯一真正生效的地方。旧写法第一行就是
     * `if (role == IDEA || target is Note) commitSupplement(...)`，而宿主那颗 `role` 是
     * `composeRole`（= 《补充》亮着时为 IDEA）：面板点某条气泡的"编辑"只做
     * `setEditingIndex(index) + setDraft(msg.content)`，**不会**把 chip 从《补充》切回《她》。
     * 于是"选了补充再编辑她的一句话"这条真实可达的路径会把那句话写进备注、
     * 消息原文一个字没动（而且下一次算 directive 还多出一行）。
     * 现在：编辑对象是某条真实消息 → 就只改那一条，一次，且不借备注通道。
     */
    private fun updateMessage(index: Int, role: ChatMessage.Role, content: String) {
        if (content.isBlank()) return
        val list = _messages.value.toMutableList()
        // 落点按编辑对象的身份认，下标只是调用方传来的提示：
        // 列表在编辑期间被动过时，宁可什么都不改，也不把这段话贴到别的消息上。
        val target = _editingTarget.value
        if (target is ComposerEditingTarget.RealMessage) {
            val idx = list.indexOfFirst { it.id == target.id }
            if (idx < 0) return
            // 角色取列表里那一条自己的身份：编辑一句话是改正文，不是改说话人。
            // 《补充》那颗 chip（role=IDEA）在这里给不出角色——备注不是消息角色（第6节第2条）。
            val newRole = role.takeIf { it != ChatMessage.Role.IDEA } ?: list[idx].role
            list[idx] = ChatMessage(id = list[idx].id, role = newRole, content = content.trim())
            _messages.value = list
            applyEditingTarget(ComposerEditingTarget.None)
            // 草稿被这次提交**消费掉**了才清——留着它的下一次算备注会把同一句并进去
            // （宿主 onAdd 里那句 `setDraft("")` 是同一个动作的另一半，两边都做不会冲突）。
            // 传进来的正文与草稿不是同一句时不动输入框，避免核掉用户正在另起的那句话。
            if (_draftText.value.trim() == content.trim()) _draftText.value = ""
            onContentChanged()
            return
        }
        if (target is ComposerEditingTarget.Note || role.toInputKind() == ComposerInputKind.SUPPLEMENT) {
            // 编辑目标是本轮备注（或没有编辑位、调用方按旧角色把这一步标成了 IDEA）：改备注，
            // 反例：顺着 index 写回列表，用户点的是灰字，改掉的却是她的那句话。
            commitSupplement(content.trim())
            return
        }
        // 没有身份也没有 IDEA：旧下标调用点，按下标改那一条（越界什么都不动）
        val idx = index.takeIf { it in list.indices } ?: return
        list[idx] = ChatMessage(id = list[idx].id, role = role, content = content.trim())
        _messages.value = list
        onContentChanged()
    }

    private fun removeMessage(index: Int) {
        val before = _messages.value
        val after = before.filterIndexed { i, _ -> i != index }
        _messages.value = after
        // 编辑位按身份重算——旧写法只换列表不重算，于是"删掉一条"会把编辑镜像指到
        // 顶上来的那一条身上（ 要禁的就是这种"用上一个位置猜对象"）
        applyEditedList(before, after, clearDraftWhenEditingGone = false)
        onContentChanged()
    }

    /**
     * 按消息 id 删除（动画延迟回调里 index 会过期，id 是 data class 稳定值）。
     *
     * 编辑位怎么修正在这里只有一条路：[MessageListEditing.reindex] 按身份重算。
     * "编辑位没了就把草稿一起清空"是同一条判据的另一半——留着草稿，
     * 下一次编辑动作就会把它贴到别的消息上。
     */
    private fun removeMessageById(id: String) {
        val list = _messages.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val nextList = list.filterNot { it.id == id }
        _messages.value = nextList
        applyEditedList(list, nextList, clearDraftWhenEditingGone = true)
        onContentChanged()
    }

    private fun reorderMessages(from: Int, to: Int) {
        if (from == to) return
        val list = _messages.value
        if (from !in list.indices || to !in list.indices) return
        val moved = list.toMutableList()
        val item = moved.removeAt(from)
        moved.add(to, item)
        _messages.value = moved
        applyEditedList(list, moved, clearDraftWhenEditingGone = false)
        onContentChanged()
    }

    private fun consumeMessages(ids: Set<String>) {
        val oldList = _messages.value
        val newList = oldList.filterNot { it.id in ids }
        _messages.value = newList
        applyEditedList(oldList, newList, clearDraftWhenEditingGone = true)
        // 本轮成功收尾 → 备注按本轮生命周期一起清（第6节第3条）。失败的那一轮走不到这里，
        // 所以"失败保留备注"不需要第二条判据：这条路径本身就是"这轮已经交付了"。
        if (ids.isNotEmpty()) clearNoteStashForCurrentSubject()
        // 不调 onContentChanged：这是一次已提交轮次的收尾，VM 走的是"结果清空"那条路，
        // 不是"输入变了所以旧结果作废"。搬之前也是这个区别。
    }

    // ── 三轴的私有写入漏斗：真值只在这里变，投影（旧字段）也只在这里跟着写 ──────

    /**
     * 设定输入对象。
     *
     * 四条不许漂的规矩都收在这一个函数里：
     * 1. 选"补充"**不动捕获角色**（[ComposerInputKind.SUPPLEMENT] 给不出 `realRole`）——
     *    自动捕获回来的下一句仍然按她/我落，这就是用户报的"选完想法、下一条被标成上一个角色"；
     * 2. 从"补充"切回她/我：没提交的备注草稿折回**备注本体**，绝不留在输入框里冒充一条消息的话术
     *    （正在编辑某条真实消息时例外——那句草稿是那条消息的，跟备注无关）；
     * 3. 进"补充"时反过来：输入框里那段属于她/我的话术先归位到真实聊天（编辑中就写回那一条，
     *    否则追加一条），**不借备注通道混进去**（第6节第2条 明令的那条禁令）；
     * 4. 她 ↔ 我 互切不动草稿——那是同一种内容的换个说话人，不是换了一种内容。
     */
    private fun applyInputKind(kind: ComposerInputKind) {
        val previous = _inputKind.value
        _inputKind.value = kind
        // 投影：旧字段一次写齐，别处只读
        _ideaComposeMode.value = kind == ComposerInputKind.SUPPLEMENT
        kind.realRole?.let { _currentRole.value = it }

        if (previous == kind) return

        var contentChanged = false
        if (previous == ComposerInputKind.SUPPLEMENT && kind != ComposerInputKind.SUPPLEMENT &&
            _editingTarget.value !is ComposerEditingTarget.RealMessage
        ) {
            val draft = _draftText.value.trim()
            if (draft.isNotBlank()) {
                // 备注正文按"编辑中就整体替换、新写就追加一行"收口，然后清空输入框
                _replyNote.value = if (_editingTarget.value is ComposerEditingTarget.Note) {
                    draft
                } else {
                    mergeNote(_replyNote.value, draft)
                }
                _draftText.value = ""
                applyEditingTarget(ComposerEditingTarget.None)
                contentChanged = true
            }
        }
        if (kind == ComposerInputKind.SUPPLEMENT && previous != ComposerInputKind.SUPPLEMENT) {
            contentChanged = rehomeRealDraft(previous) || contentChanged
        }
        if (contentChanged) onContentChanged()
    }

    /**
     * 把输入框里那段**还没提交的她/我的话术**送回它自己的 lane。
     *
     * 反例（这一格就是为它写的）：用户在《她》里打了"今天真的好累"，没点＋就改选《补充》，
     * 若把这段草稿留在输入框，下一次算备注时会把它并进去 ⇒
     * 发给军师的那句话变成"我让军师注意：今天真的好累"，而真实对话里根本没有这句。
     */
    private fun rehomeRealDraft(previous: ComposerInputKind): Boolean {
        val role = previous.realRole ?: return false
        val target = _editingTarget.value
        val draft = _draftText.value.trim()
        if (draft.isEmpty()) {
            // 没有正文就只是让位：编辑对象换了，下标留着会把下一次写贴到别的消息上
            if (target is ComposerEditingTarget.RealMessage) applyEditingTarget(ComposerEditingTarget.None)
            return target is ComposerEditingTarget.RealMessage
        }
        if (target is ComposerEditingTarget.RealMessage) {
            val list = _messages.value.toMutableList()
            val idx = list.indexOfFirst { it.id == target.id }
            if (idx >= 0) {
                list[idx] = ChatMessage(id = target.id, role = role, content = draft)
                _messages.value = list
            }
        } else {
            _messages.value = _messages.value + ChatMessage(role = role, content = draft)
        }
        _draftText.value = ""
        applyEditingTarget(ComposerEditingTarget.None)
        return true
    }

    /** 点在灰字上：输入框接的是本轮备注的**完整正文**（展示省略过，编辑不省略） */
    private fun beginNoteEdit() {
        applyInputKind(ComposerInputKind.SUPPLEMENT)
        val current = effectiveNoteText(_messages.value)
        _draftText.value = current
        applyEditingTarget(ComposerEditingTarget.Note)
        onContentChanged()
    }

    /** 旧下标入口：把它翻译成身份，翻译不出来就是"没有在编辑任何东西" */
    private fun setEditingIndexByIndex(index: Int) {
        if (index < 0) {
            // -1 是"提交完了/退出编辑"的通用清位；正在编辑备注时不清，那条通道由 ClearNote/提交管
            if (_editingTarget.value !is ComposerEditingTarget.Note) {
                applyEditingTarget(ComposerEditingTarget.None)
            }
            return
        }
        val msg = _messages.value.getOrNull(index)
        if (msg == null) {
            // 下标翻译不出身份（越界＝悬空索引）＝"没有在编辑任何真实消息"：就地清位。
            // 悬空下标一旦留在账上，ReplyInput 只看 editingIndex>=0 就进编辑态，
            // 界面会显示"正在编辑一条不存在的消息"。正在编辑备注时不清，那条通道另管。
            if (_editingTarget.value !is ComposerEditingTarget.Note) {
                applyEditingTarget(ComposerEditingTarget.None)
            }
            return
        }
        applyEditingTarget(ComposerEditingTarget.RealMessage(msg.id))
    }

    private fun applyEditingTarget(target: ComposerEditingTarget) {
        _editingTarget.value = target
        _editingIndex.value = if (target is ComposerEditingTarget.RealMessage) {
            _messages.value.indexOfFirst { it.id == target.id }
        } else {
            -1
        }
    }

    /**
     * 三处列表改动共用的编辑位修正——同一判据只这一份实现。
     *
     * 两条判据分得很清：
     * - **正在编辑哪条真实消息**认身份（[ComposerEditingTarget.RealMessage.id]）：
     *   那条还在，就把镜像下标重算给它；那条没了，编辑位归 -1 并按 [clearDraftWhenEditingGone]
     *   决定草稿留不留。留着它，下一次编辑动作就会把这段话贴到别的消息上。
     * - **正在编辑本轮备注**时列表怎么变都不影响它——备注不在这棵树里，重排也碰不到它（第5节 手势那条禁令）。
     */
    private fun applyEditedList(
        before: List<ChatMessage>,
        after: List<ChatMessage>,
        clearDraftWhenEditingGone: Boolean
    ) {
        val target = _editingTarget.value
        if (target !is ComposerEditingTarget.RealMessage) return
        if (after.none { it.id == target.id }) {
            _editingTarget.value = ComposerEditingTarget.None
            _editingIndex.value = -1
            if (clearDraftWhenEditingGone) _draftText.value = ""
            return
        }
        val next = MessageListEditing.reindex(before, after, _editingIndex.value)
        if (next != _editingIndex.value) _editingIndex.value = next
    }

    /** 提交一条"补充"：落备注本体、清输入框、清编辑位；**不产生任何聊天消息** */
    private fun commitSupplement(text: String) {
        _replyNote.value = if (_editingTarget.value is ComposerEditingTarget.Note) {
            text
        } else {
            mergeNote(_replyNote.value, text)
        }
        _draftText.value = ""
        applyEditingTarget(ComposerEditingTarget.None)
        onContentChanged()
    }

    private fun clearNote() {
        if (_replyNote.value.isEmpty() && _draftText.value.isBlank() &&
            _editingTarget.value !is ComposerEditingTarget.Note
        ) {
            return
        }
        clearNoteStashForCurrentSubject()
        _draftText.value = ""
        if (_editingTarget.value is ComposerEditingTarget.Note) applyEditingTarget(ComposerEditingTarget.None)
        onContentChanged()
    }

    private fun clearNoteStashForCurrentSubject() {
        _replyNote.value = ""
        noteStash[subjectKey(_subject.value)] = ""
    }

    /**
     * 换对象（切知识库/切她）。
     *
     * 第6节第3条 那条禁令：切对象不许把 A 对象的备注带给 B 对象。这里按对象暂存再取回，
     * 于是 B 看到的是干净的一行（没有备注就**不显示**灰字），切回 A 时 A 那份还在。
     */
    private fun switchSubject(name: String?) {
        val oldKey = subjectKey(_subject.value)
        val newKey = subjectKey(name)
        if (oldKey == newKey) return
        noteStash[oldKey] = _replyNote.value
        _subject.value = name
        _replyNote.value = noteStash[newKey].orEmpty()
        // 输入框里若正写着上一个对象的备注，跟着让位——它不属于这一块库
        if (_editingTarget.value is ComposerEditingTarget.Note) {
            _draftText.value = ""
            applyEditingTarget(ComposerEditingTarget.None)
        }
        onContentChanged()
    }

    // ── 备注文本的唯一拼装点（别处不许再拼第二份） ─────────────────────────

    private fun effectiveNoteText(messages: List<ChatMessage>): String {
        val committed = mergeNote(_replyNote.value, legacyNoteLines(_messages.value, messages))
        val target = _editingTarget.value
        // 正在编辑某一条真实消息 ⇒ 输入框里那句是**那条消息的**，与备注无关（第6节第2条 明令）。
        // 这一条判据必须先看编辑对象、后看 chip：反例是"《补充》亮着时点她的一句话编辑"，
        // 旧写法只看 `inputKind == SUPPLEMENT` 就把那句话并进备注，
        // 于是"她说的话"变成"我让军师注意：她说的话"，同一次编辑还同时写进对话快照。
        if (target is ComposerEditingTarget.RealMessage) return committed
        if (_inputKind.value != ComposerInputKind.SUPPLEMENT) return committed
        return when (target) {
            // 点在灰字上进来的：输入框里那份**就是**备注的新全文——替换，不许再叠一份旧的
            ComposerEditingTarget.Note -> _draftText.value.trim().ifBlank { committed }
            // 新写的一句还没提交：并进去，一句都不丢（生成时冻结的就是此刻这一段）
            else -> mergeNote(committed, _draftText.value.trim())
        }
    }

    /**
     * 过渡期适配：历史列表里残留的 `Role.IDEA` 行折进备注。
     *
     * 只有 [ideaHintOf] 的调用方（例如外部交来一份手工构造的列表）可能走到这里；
     * 生产写入路径已经不再产生 IDEA 行。按正文去重，所以同一条备注**不可能被注入两次**——
     * 这条判据的可破点写在 `ComposerStoreAdvisorNoteTest` 的对应格子里。
     */
    private fun legacyNoteLines(vararg sources: List<ChatMessage>): String =
        sources.asSequence()
            .flatten()
            .filter { it.role == ChatMessage.Role.IDEA }
            .map { it.content.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")

    /**
     * 备注的唯一拼装：两段正文按行合并，**同一句补充只留一份**。
     *
     * 反例（这一格为它写）：已提交备注里躺着"别太刻意"，列表里又残留一条同文 IDEA 行，
     * [legacyNoteLines] 那份折进来时旧写法直接 `first + "\n" + second` ⇒
     * directive 里同一句提醒出现两遍，模型读到的是"用户强调过两次"。
     * 不同句的提醒仍是两行、顺序不变（"本轮一份备注，可多次编辑补充"那条要求靠它）。
     */
    private fun mergeNote(first: String, second: String): String {
        val lines = mutableListOf<String>()
        for (part in listOf(first, second)) {
            part.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                if (line !in lines) lines.add(line)
            }
        }
        return lines.joinToString("\n")
    }

    private fun subjectKey(name: String?): String = name ?: NO_SUBJECT_KEY

    companion object {
        /** 谈心草稿写盘防抖窗口——连续击键只在停顿后落盘一次（强杀最多丢 ≤600ms 输入） */
        const val DEFAULT_DRAFT_DEBOUNCE_MS = 600L

        /** 还没有对象身份时的暂存位（宿主接线 SetSubject 之前的默认） */
        private const val NO_SUBJECT_KEY = "\u0000no-subject"
    }
}

/**
 * 旧角色 → 输入对象（过渡期唯一转换点）。
 *
 * `Role.IDEA` 只读成"输入对象 = 补充"，**不是一种消息角色**；真实聊天集合永远只有 HER/ME。
 * UI 不许再把 IDEA 当可重排的聊天消息画（第6节第2条），它现在只剩下"这一颗 chip 亮着"这一个含义。
 */
internal fun ChatMessage.Role.toInputKind(): ComposerInputKind = when (this) {
    ChatMessage.Role.HER -> ComposerInputKind.HER
    ChatMessage.Role.ME -> ComposerInputKind.ME
    ChatMessage.Role.IDEA -> ComposerInputKind.SUPPLEMENT
}
