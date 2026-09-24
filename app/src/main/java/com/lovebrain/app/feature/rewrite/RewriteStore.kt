package com.lovebrain.app.feature.rewrite

import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.SchemeFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 改写这条链的状态持有者（§5.2 第 5 步：接管 `rewriteRequestId`、ledger、取消）。
 *
 * ## 搬之前这里是三本账
 * 1. [RewriteLedger]：每张卡片的状态 + 被改写掉的版本栈；
 * 2. `LoveBrainViewModel.rewriteRequestId`：手写的一个字符串，用来判"这次回调还算不算数"；
 * 3. `LoveBrainViewModel.rewriteContextId`：又一个字符串，绑的是"这一轮还在不在"。
 *
 * 2 和 3 谁都能写、没有归属、也没有一处能测它们的地方（要测就得开一整个 ViewModel）。
 * 现在合成一本：发起时冻结成 [Identity]，之后所有终态事件都必须把它带回来，
 * store 拿**自己手里那份在途身份**核对——对不上就是迟到的回调，整条丢弃。
 *
 * ## 三道核对分别在管什么（少一道都是不同的事故）
 * - `inFlight != identity`：我被更新的改写取代了 / 用户取消了 / 轮次已清空；
 * - [isCurrentRequest]：前台协调器已经换人（并行的另一条链拿了租约）；
 * - [isRoundAlive]：切了知识库、开了新一轮、结果被保存清空 —— 目标正文已经不是当初那份。
 *
 * 第三道要读的是 VM 的活状态，store 不伸手去拿（Law of Demeter），由 VM 以闭包注入。
 *
 * ## 撤销改成两步式
 * 旧写法是 `pop()` 先把历史弹掉，再去看"当前结果还在不在、identityKey 解不解得开"，
 * 任何一步失败就 `return`——**弹掉的那一版就永久丢了**，卡片还挂在 Done 上。
 * 可达性不高（轮次切换会连带清历史），但顺序是错的：现在是 `UndoRequested` 只 peek 并发
 * [Effect.RestoreVersion]，VM 真的应用了才回 `UndoCommitted`，store 这时才 pop。
 */
class RewriteStore(
    private val isCurrentRequest: (String) -> Boolean = { true },
    private val isRoundAlive: (String) -> Boolean = { true },
    private val onEffect: (Effect) -> Unit = {}
) {

    /**
     * 发起时冻结的在途身份。
     *
     * [contextId] 是"这一轮"的指纹（知识库名 + 本轮消息集哈希），由 VM 算好交进来，
     * store 只负责在终态时问一句"它还活着吗"。
     */
    data class Identity(
        val requestId: String,
        val contextId: String,
        val identityKey: String,
        val option: String
    )

    data class UiState(
        /** identityKey（`SchemeIdentity.key`）→ 卡片改写状态；STYLE 与 DIRECTION 各自独立 */
        val cardStates: Map<String, RewriteState> = emptyMap(),
        /** 当前在途的那一次改写；没有改写 in-flight 时为 null */
        val inFlight: Identity? = null
    )

    sealed interface Intent {
        /** 发起一次改写：卡片进"改写中"，被替换的正文连同它当时的赞踩压进历史 */
        data class Begin(
            val identity: Identity,
            val previousReply: String,
            val previousFeedback: SchemeFeedback
        ) : Intent

        /** 拿到新正文 */
        data class Succeeded(val identity: Identity, val newReply: String) : Intent

        /** 模型返回空正文 */
        data class Emptied(val identity: Identity) : Intent

        /** 请求抛异常 */
        data class Failed(val identity: Identity) : Intent

        /** 用户点"取消" */
        data class RequestCancel(val identityKey: String) : Intent

        /** 用户点"撤销"——store 只 peek，动历史要等 [UndoCommitted] */
        data class UndoRequested(val identityKey: String) : Intent

        /** VM 确认已经按 [Effect.RestoreVersion] 把正文与反馈贴回去了 */
        data class UndoCommitted(val identityKey: String) : Intent

        /** 卡片收起——只允许收掉终态，改写进行中没有"收起"这回事 */
        data class Collapse(val identityKey: String) : Intent

        /** 新轮 / 切库 / 保存清空 / 版本回退：这一页整个翻掉 */
        data object RoundReset : Intent
    }

    sealed interface Effect {
        /** 把新正文贴到那张方案卡上（只替换目标卡，不许回写整个捕获的旧 response） */
        data class ApplyRewrite(val identityKey: String, val newReply: String) : Effect

        /** 新正文的赞踩独立从 NONE 开始，不自动继承旧版的赞 */
        data class ResetFeedback(val identityKey: String) : Effect

        /** 累计改写次数 +1（落 prefs 由 VM 做） */
        data object RewriteCounted : Effect

        /** 请外部取消在跑的那次改写的任务体 */
        data class StopRunningRewrite(val requestId: String) : Effect

        /** 撤销：把这一版正文和它当时的反馈贴回去 */
        data class RestoreVersion(
            val identityKey: String,
            val reply: String,
            val feedback: SchemeFeedback
        ) : Effect
    }

    private val ledger = RewriteLedger()
    private val _ui = MutableStateFlow(UiState())

    /** 与另外四条链同一个形状：状态是一条 StateFlow，写只能从 [accept] 进 */
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    val currentInFlight: Identity? get() = _ui.value.inFlight

    fun stateOf(identityKey: String): RewriteState? = _ui.value.cardStates[identityKey]

    /** 单测用：确认撤销的两步式协议没把历史提前吃掉 */
    internal fun historySize(identityKey: String): Int = ledger.historySize(identityKey)

    fun accept(intent: Intent) {
        when (intent) {
            is Intent.Begin -> begin(intent)

            is Intent.Succeeded -> if (claim(intent.identity)) {
                setState(intent.identity.identityKey, RewriteState.Done(intent.newReply))
                retire(intent.identity)
                onEffect(Effect.ApplyRewrite(intent.identity.identityKey, intent.newReply))
                onEffect(Effect.ResetFeedback(intent.identity.identityKey))
                onEffect(Effect.RewriteCounted)
            }

            is Intent.Emptied -> if (claim(intent.identity)) {
                setState(intent.identity.identityKey, RewriteState.Error("改写返回空结果"))
                retire(intent.identity)
            }

            is Intent.Failed -> if (claim(intent.identity)) {
                setState(intent.identity.identityKey, RewriteState.Error("改写失败，可重试"))
                retire(intent.identity)
            }

            is Intent.RequestCancel -> cancel(intent.identityKey)

            is Intent.UndoRequested -> {
                val version = ledger.peekLast(intent.identityKey) ?: return
                onEffect(Effect.RestoreVersion(intent.identityKey, version.reply, version.feedback))
            }

            is Intent.UndoCommitted -> {
                ledger.pop(intent.identityKey)
                setState(intent.identityKey, null)
            }

            is Intent.Collapse -> {
                val current = stateOf(intent.identityKey)
                if (current is RewriteState.Done || current is RewriteState.Error) {
                    setState(intent.identityKey, null)
                }
            }

            Intent.RoundReset -> {
                ledger.clearAll()
                _ui.value = UiState()
            }
        }
    }

    // ─── 内部 ────────────────────────────────────────────────────

    private fun begin(intent: Intent.Begin) {
        val id = intent.identity
        // 同一张卡上一轮还挂着终态时，历史先接着用，状态被"改写中"覆盖
        ledger.push(id.identityKey, RewriteLedger.Version(intent.previousReply, intent.previousFeedback))
        _ui.value = _ui.value.copy(inFlight = id)
        setState(id.identityKey, RewriteState.Loading(id.option))
    }

    /**
     * 这道终态事件还算不算数。
     *
     * 三道核对的顺序不能反：先看"还是不是我手里那一次"，再看外部两把活状态闸。
     * 收工（[retire]）由调用分支做——三种终态各自还要不要留着在途身份，是它们的事。
     */
    private fun claim(identity: Identity): Boolean {
        if (_ui.value.inFlight != identity) return false
        if (!isCurrentRequest(identity.requestId) || !isRoundAlive(identity.contextId)) {
            // 外部已经换人/换轮：这一次作废，卡片状态一并收走，不留永远转圈的 Loading
            retire(identity)
            setState(identity.identityKey, null)
            return false
        }
        return true
    }

    /** 这一次改写在册子上翻篇了：之后的同身份终态事件一律不算数（重复投递也不再发副作用） */
    private fun retire(identity: Identity) {
        if (_ui.value.inFlight == identity) _ui.value = _ui.value.copy(inFlight = null)
    }

    private fun cancel(identityKey: String) {
        val inFlight = _ui.value.inFlight
        if (inFlight != null && inFlight.identityKey == identityKey) {
            onEffect(Effect.StopRunningRewrite(inFlight.requestId))
            retire(inFlight)
        }
        // 取消只是不显示"改写中"，历史要留着——用户可能撤销的是它上一次成功的改写
        setState(identityKey, null)
    }

    private fun setState(identityKey: String, state: RewriteState?) {
        _ui.value = _ui.value.copy(
            cardStates = if (state == null) _ui.value.cardStates - identityKey
            else _ui.value.cardStates + (identityKey to state)
        )
    }
}
