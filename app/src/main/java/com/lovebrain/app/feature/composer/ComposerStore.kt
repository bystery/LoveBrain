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
 * 输入区与消息列表的状态持有者（复核 §5.2 第 6 步"VM 不再是状态壳"的第一块）。
 *
 * 它接管 VM 里那九颗各自可写的 `MutableStateFlow`：面板状态、消息列表、当前角色、
 * 编辑位、《想法》chip 态、两条草稿、输入/输出模式、计划面板。搬之前它们的写法是
 * "谁需要谁 `_messages.value = …`"，共 11 处；其中"改完列表要顺手修正编辑位"这件事
 * 在三个地方各抄了一遍（[MessageListEditing] 的 KDoc 记着这段历史）。
 *
 * 三条不变量，就是这个类存在的全部理由：
 * 1. **唯一写入漏斗**——外部只能 [accept] 意图，九颗 flow 全是私有的；
 * 2. **编辑位与草稿的联动只在这里发生**（删掉/耗尽正在编辑的那条 → 编辑位归 -1 且草稿清空）；
 * 3. **`onContentChanged` 与状态写入同帧**——"内容变了要旧结果标 stale"这件事由调用方决定怎么做，
 *    但**什么时候该想这件事**由这里决定，漏一处就得到一个静默的旧结果。
 *
 * 为什么副作用是同步回调而不是一条 flow：VM 现在就是在 `setDraft` 那一刻同步跑
 * `checkInputChanged()` 的，既有测试与真机门禁证据都按这个时序写。改成异步会把
 * "喂完意图就断言状态"变成"还得等一次调度"，那是拿结构改进换一批时序漂移。
 *
 * 持久化不在这个类里：它只交出 [savePanelMode] / [persistCounselingDraft] / [saveOutputMode]
 * 三个回调，所以 store 不知道有 `SecurePrefs` 这回事（§5.1 的包边界：`feature` 不许 import `data`）。
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

    /** 唯一入口：外部只能投意图。九颗状态 flow 全是私有字段。 */
    sealed interface Intent {
        data class SetPanelState(val state: PanelState) : Intent
        data class SetDraft(val text: String) : Intent
        data class SetCounselingDraft(val text: String) : Intent
        data class SetPanelMode(val mode: Int) : Intent
        data class SetOutputMode(val mode: Int) : Intent
        data class SetCurrentRole(val role: ChatMessage.Role) : Intent
        data class SetEditingIndex(val index: Int) : Intent
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

        data object ShowPlanPanel : Intent
        data object DismissPlanPanel : Intent
    }

    private val _panelState = MutableStateFlow(PanelState.KEYBOARD)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _currentRole = MutableStateFlow(ChatMessage.Role.HER)
    private val _editingIndex = MutableStateFlow(-1)
    private val _ideaComposeMode = MutableStateFlow(false)
    private val _draftText = MutableStateFlow("")
    private val _counselingDraft = MutableStateFlow("")
    private val _panelMode = MutableStateFlow(initialPanelMode)
    private val _outputMode = MutableStateFlow(initialOutputMode)
    private val _showPlanPanel = MutableStateFlow(false)

    val panelState: StateFlow<PanelState> = _panelState.asStateFlow()
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    val currentRole: StateFlow<ChatMessage.Role> = _currentRole.asStateFlow()
    val editingIndex: StateFlow<Int> = _editingIndex.asStateFlow()
    val ideaComposeMode: StateFlow<Boolean> = _ideaComposeMode.asStateFlow()
    val draftText: StateFlow<String> = _draftText.asStateFlow()
    val counselingDraft: StateFlow<String> = _counselingDraft.asStateFlow()
    val panelMode: StateFlow<Int> = _panelMode.asStateFlow()
    val outputMode: StateFlow<Int> = _outputMode.asStateFlow()
    val showPlanPanel: StateFlow<Boolean> = _showPlanPanel.asStateFlow()

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

    fun accept(intent: Intent) {
        when (intent) {
            is Intent.SetPanelState -> _panelState.value = intent.state
            is Intent.SetDraft -> setDraft(intent.text)
            is Intent.SetCounselingDraft -> setCounselingDraft(intent.text)
            is Intent.SetPanelMode -> {
                _panelMode.value = intent.mode
                savePanelMode(intent.mode)
            }
            is Intent.SetOutputMode -> {
                val mode = normalizeOutputMode(intent.mode)
                _outputMode.value = mode
                saveOutputMode(mode)
            }
            is Intent.SetCurrentRole ->
                // 捕获收口：`IDEA` 只进入《想法》chip 态，不落 currentRole——
                // 真实聊天捕获的角色恒 ∈ {HER, ME}，否则捕获回来的消息会被误标成想法。
                if (intent.role == ChatMessage.Role.IDEA) {
                    _ideaComposeMode.value = true
                } else {
                    _ideaComposeMode.value = false
                    _currentRole.value = intent.role
                }
            is Intent.SetEditingIndex -> _editingIndex.value = intent.index
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
            Intent.ShowPlanPanel -> _showPlanPanel.value = true
            Intent.DismissPlanPanel -> _showPlanPanel.value = false
        }
    }

    // ── 派生读取（原本在 VM 里的两个纯函数，输入全是这里的私有状态） ────────

    /** 当前本轮想法文本 = 已提交的 IDEA 消息 + 未提交草稿 */
    fun ideaHint(): String = ideaHintOf(_messages.value)

    /**
     * 给定一份消息列表（通常是本轮的冻结快照）算想法文本，草稿仍取**当前**那条。
     *
     * 这不是笔误：`generate()` 要的是"这一轮消息 + 用户此刻输入框里还没提交的想法"，
     * 所以它传快照、但不传草稿。搬之前 `collectIdeaHintWithDraft(snapshot)` 就是这个形状，
     * 保持原样，不在结构改动里顺手换语义。
     */
    fun ideaHintOf(messages: List<ChatMessage>): String {
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

    /**
     * 构建本轮生成的冻结消息快照——深拷贝，并把未提交的编辑草稿按**稳定消息 ID** 落进对应那条。
     *
     * 按 ID 而不是按下标写回：草稿应用期间列表可能已经被增删/拖动过，
     * 按下标会得到"我在改第 2 条，实际改到第 3 条"那种静默错配。
     */
    fun messageSnapshot(): List<ChatMessage> {
        val messages = _messages.value.map { it.copy() }
        val editingDraft = _draftText.value.trim()
        val editingIdx = _editingIndex.value
        if (editingDraft.isNotBlank() && editingIdx in messages.indices) {
            val targetId = messages[editingIdx].id
            val editingRole =
                if (_ideaComposeMode.value) ChatMessage.Role.IDEA else _currentRole.value
            return messages.mapIndexed { idx, msg ->
                if (idx == editingIdx && msg.id == targetId) msg.copy(role = editingRole, content = editingDraft)
                else msg
            }
        }
        return messages
    }

    /** 关闭时把防抖尾的草稿同步落盘（正常关闭零丢失），并停掉那个还没跑到的任务 */
    fun flushCounselingDraft() {
        draftPersistJob?.cancel()
        draftPersistJob = null
        persistCounselingDraft(_counselingDraft.value)
    }

    private fun setDraft(text: String) {
        _draftText.value = text
        // 草稿参与 messageSnapshot()，所以它一变，旧结果就该被判 stale
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
        _messages.value = _messages.value + ChatMessage(role = role, content = content.trim())
        onContentChanged()
    }

    private fun updateMessage(index: Int, role: ChatMessage.Role, content: String) {
        if (content.isBlank()) return
        val list = _messages.value.toMutableList()
        if (index !in list.indices) return
        list[index] = ChatMessage(id = list[index].id, role = role, content = content.trim())
        _messages.value = list
        onContentChanged()
    }

    private fun removeMessage(index: Int) {
        _messages.value = _messages.value.filterIndexed { i, _ -> i != index }
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
        applyEditedList(list, nextList, clearDraftWhenEditingGone = true)
        _messages.value = nextList
        onContentChanged()
    }

    private fun reorderMessages(from: Int, to: Int) {
        if (from == to) return
        val list = _messages.value
        if (from !in list.indices || to !in list.indices) return
        val moved = list.toMutableList()
        val item = moved.removeAt(from)
        moved.add(to, item)
        applyEditedList(list, moved, clearDraftWhenEditingGone = false)
        _messages.value = moved
        onContentChanged()
    }

    private fun consumeMessages(ids: Set<String>) {
        val oldList = _messages.value
        val newList = oldList.filterNot { it.id in ids }
        applyEditedList(oldList, newList, clearDraftWhenEditingGone = true)
        _messages.value = newList
        // 不调 onContentChanged：这是一次已提交轮次的收尾，VM 走的是"结果清空"那条路，
        // 不是"输入变了所以旧结果作废"。搬之前也是这个区别。
    }

    /** 三处列表改动共用的编辑位修正——同一判据只这一份实现 */
    private fun applyEditedList(before: List<ChatMessage>, after: List<ChatMessage>, clearDraftWhenEditingGone: Boolean) {
        val editing = _editingIndex.value
        val next = MessageListEditing.reindex(before, after, editing)
        if (next != editing) _editingIndex.value = next
        if (clearDraftWhenEditingGone && editing >= 0 && next < 0) _draftText.value = ""
    }

    companion object {
        /** 谈心草稿写盘防抖窗口——连续击键只在停顿后落盘一次（强杀最多丢 ≤600ms 输入） */
        const val DEFAULT_DRAFT_DEBOUNCE_MS = 600L
    }
}
