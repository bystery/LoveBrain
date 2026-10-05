package com.lovebrain.app.feature.proactive

import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.ProactiveEnded
import com.lovebrain.app.model.ProactiveEvent
import com.lovebrain.app.model.ProactiveFailed
import com.lovebrain.app.model.ProactiveFirstToken
import com.lovebrain.app.model.ProactiveOption
import com.lovebrain.app.model.ProactiveOptions
import com.lovebrain.app.model.ProactiveStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主动发（开场白）这条链的状态持有者（第5节第2条 第 3 步）。
 *
 * 接管 **options、错误、事件归约与"停止/收尾"语义**，以及输入区的 [ComposerMode]。
 * [com.lovebrain.app.model.ResultMode]（结果区当前展示哪一类结果）**不在这里**：
 * 回复链也写它，让它归 [com.lovebrain.app.feature.mode.ModeController]，本类只在
 * **用户明确要求离开**时经 [Effect.ExitedProactiveMode] 请调用方归位一次。
 *
 * ## 生成结束后结果必须留在页面上（）
 *
 * 以前的规则是"结束且真拿到可展示的开场 ⇒ 自动退回普通回复"。它把两件本来不相干的事
 * 绑在一起了：*生成结束了*（该停 loading）与*用户要看别的了*（该换结果区）。
 * 于是主动发生成完的那一瞬间，结果还活着、视图却已经被换走——用户看到的就是"结果消失"。
 *
 * 现在的规则只有一条出口：**明确动作**。[Intent.ToggleComposer]（用户点蓝字切回普通回复）、
 * [Intent.ExitProactive]（前台租约被拒，这条链压根没起跑）才会发 [Effect.ExitedProactiveMode]；
 * [ProactiveEnded] 不动开场、不动模式、也不发效果，所以开场白会一直留在原地直到用户自己走。
 * "开始一轮普通回复"那一侧本来就由回复链自己写 ResultMode.REPLY，不需要这里配合。
 *
 * loading 也不在这里：它由调用方从前台租约派生，[ProactiveEnded] 到不到都不影响它收尾。
 *
 * ## 失败、停止与迟到事件（第4节第1条第5条 的生产半边，三条各有自己的分支）
 *
 * 1. **失败后就地短提示、可再生成，不许把失败伪装成"没有结果"**——
 *    [ProactiveFailed] 只写 [UiState.error]，**绝不清 [UiState.options]**：超时或半途断流时，
 *    已经流出的那几条开场留在屏幕上，旁边就是那句短提示。另一半分在 [closeRun]：
 *    一轮跑完**既没有开场也没有失败文案**时补 [NO_OPENERS_NOTICE]。少了这一句，这一屏会落到
 *    面板那句"还没有开场，点击下方生成"上——**军师没交付**被说成**用户还没生成**，
 *    两种截然不同的事实共用同一个答案，用户既看不出这次失败了、也不知道该不该再点一次。
 *    再生成不需要这里配合：[ProactiveStarted] 一次清掉 error 与 options，失败后租约已回收，
 *    那颗「生成开场」一直按得下去。
 * 2. **点停止保留已输出内容**——停止那条链上的 [ProactiveEnded] 在这里不写开场、不写模式
 *    （[closeRun] 只在"什么都没有"时才动 error，已经留着的内容一个字都不减）。VM 是
 *    "先回收租约、再补发这条事件"，所以它通常在上那道归属门就被丢掉；即便被收下来也不减内容
 *    ——两条路都成立。
 * 3. **晚到事件按 requestId 丢弃、不复活已取消的任务**——[onEvent] 第一行那道 [isCurrentRequest]
 *    门盖住事件流上的**每一个**事件，也盖住 [closeRun]：任务被取消或已被新请求取代之后，
 *    旧请求的 options / failed / ended 一个字都写不进来，于是它既不能把内容重新贴回屏幕，
 *    也不能借"这次没交付"那句提示把自己说成一次失败。判据仍然只有"谁在跑"那一本账
 *    （调用方注入的闭包，真源是 [com.lovebrain.app.domain.ForegroundOperationCoordinator]），
 *    本类不另记第二份身份——第4节第1条第6条 要保住的 coordinator / 供应商冻结 / 取消重抛都在原处。
 */
class ProactiveStore(
    private val isCurrentRequest: (String) -> Boolean = { true },
    private val onEffect: (Effect) -> Unit = {}
) {

    data class UiState(
        val options: List<ProactiveOption> = emptyList(),
        val error: String? = null,
        /**
         * 输入区的模式。它和 [options] 住在同一个状态对象里，是为了让
         * "退出主动发时残留的开场一起清掉"这件事**在一次赋值里**做完——
         * 以前它跨两个所有者：store 报"要退了"，VM 再去改 `_composerMode`，
         * 于是同一瞬间能读出"模式已退、结果还没清"或反之。
         */
        val composerMode: ComposerMode = ComposerMode.REPLY
    )

    sealed interface Intent {
        /** Engine 事件流里的一个事件 */
        data class Apply(val event: ProactiveEvent) : Intent

        /** 没走到模型就失败（没有知识库等引导文案） */
        data class Fail(val message: String) : Intent

        // /：`Intent.Clear`（"用户清掉结果与错误、模式保持"）已退役——
        // main 里零调用点，而且它想干的每件事都已有**有主人的**出口在干：
        // 再生成 ⇒ `Apply(ProactiveStarted)` 一次清 options+error（KDoc 第4节第1条第5条 第一条），
        // 用户切走 ⇒ `ToggleComposer` 退出时连残留一起清。本工单建议的两条候选接法
        // （stopProactive / ExitProactive 之后投它）都被产品判据明令禁止：
        // 停止与租约被拒这两条链**保留已流出的开场**正是 第4节第1条第5条 第二条钉住的行为，
        // 各有一格测试（`finishing a run keeps the openers…` / `exiting without the toggle…`）。
        // 原文与摘走的代码登记在  归档区
        //  下的  proactive-clear 目录。

        /**
         * 用户点蓝字：进 / 出主动发模式。
         *
         * 出去时结果一起清掉（原来的行为）；进来时不动任何东西——
         * "第一次点击只切换模式、不发网络请求"这条约束由调用方保证，这里也不碰网络。
         */
        data object ToggleComposer : Intent

        /** 真的要发起一次主动发生成了（VM 在拿到前台租约之前宣告） */
        data object EnterProactive : Intent

        /**
         * 退出主动发模式，但**留着**已经拿到的开场。
         *
         * 现在的调用点是"前台租约被拒"那一条：这一轮压根没起跑，不该把 UI 留在
         * 一个空的主动发外壳上。手动停止走的是另一条——VM 发 [ProactiveEnded]，
         * 而那条事件在这里不写任何东西，所以停止之后屏幕上还是用户已经看见过的那几条。
         */
        data object ExitProactive : Intent
    }

    sealed interface Effect {
        /**
         * 结果区该从"主动发那一类结果"退回普通回复。
         *
         * 只有**明确动作**才发它：[Intent.ToggleComposer] 把模式从 PROACTIVE 切回 REPLY、
         * [Intent.ExitProactive] 退出（租约被拒）。生成结束本身**不发**——
         * "生成完了"不等于"用户要看普通回复"，把结果从屏幕上换走就是  要修的那件事。
         *
         * VM 是 `resultMode` 的唯一写者（回复链也写它），所以这条只能是效果，
         * 不能让 store 直接改。
         */
        data object ExitedProactiveMode : Effect

        /** 首字耗时：VM 里那个跨 feature 统计量 */
        data class FirstTokenObserved(val elapsedMs: Long) : Effect
    }

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    val currentOptions: List<ProactiveOption> get() = _ui.value.options
    val composerMode: ComposerMode get() = _ui.value.composerMode

    fun accept(intent: Intent) {
        when (intent) {
            is Intent.Apply -> onEvent(intent.event)
            is Intent.Fail -> _ui.value = _ui.value.copy(error = intent.message)
            Intent.ToggleComposer -> toggleComposer()
            Intent.EnterProactive -> _ui.value = _ui.value.copy(composerMode = ComposerMode.PROACTIVE)
            Intent.ExitProactive -> exitProactive(clearResults = false)
        }
    }

    private fun toggleComposer() {
        if (_ui.value.composerMode == ComposerMode.PROACTIVE) {
            // 回到普通回复时清残留结果（与旧 VM 的 toggleProactiveMode 同一条行为）
            exitProactive(clearResults = true)
        } else {
            _ui.value = _ui.value.copy(composerMode = ComposerMode.PROACTIVE)
        }
    }

    private fun exitProactive(clearResults: Boolean) {
        if (_ui.value.composerMode != ComposerMode.PROACTIVE) return
        _ui.value = _ui.value.copy(
            composerMode = ComposerMode.REPLY,
            options = if (clearResults) emptyList() else _ui.value.options,
            error = if (clearResults) null else _ui.value.error
        )
        onEffect(Effect.ExitedProactiveMode)
    }

    private fun onEvent(event: ProactiveEvent) {
        if (!isCurrentRequest(event.requestId)) return
        when (event) {
            // 新一轮开始：结果与错误清零，但**模式不动**——用户还在主动发入口里，
            // 把模式一起抹掉会让面板在生成刚开始时就跳回普通回复。
            is ProactiveStarted -> _ui.value = _ui.value.copy(options = emptyList(), error = null)

            is ProactiveFirstToken -> onEffect(Effect.FirstTokenObserved(event.elapsedMs))

            is ProactiveOptions -> _ui.value = _ui.value.copy(options = event.options)

            is ProactiveFailed -> _ui.value = _ui.value.copy(error = event.message)

            // 生成结束：开场与模式一个字都不动，只在这一轮真的什么都没交付时补一句短提示。
            is ProactiveEnded -> closeRun()
        }
    }

    /**
     * 收尾分支（第4节第1条第5条）：**留住已经拿到的东西**，只有什么都没拿到时才开口。
     *
     * 有开场 ⇒ 不动（：结束不是"用户要看普通回复"，看得见、点得着、能复制就是成功）；
     * 有失败文案 ⇒ 也不动（那句短提示已经在屏幕上了，别用它自己的话把自己盖掉）；
     * 两者都没有 ⇒ 写 [NO_OPENERS_NOTICE]。这一句是这条链上唯一还会被 [ProactiveEnded] 改写的字段，
     * 而且它只在**归属门后面**执行——已取消任务的迟到 ended 因此既补不出这句话，
     * 也不会被这句话复活。
     *
     * 这里**不发** [Effect.ExitedProactiveMode]，也不清 [UiState.options]：前者会把结果区换走
     * （就是  那次事故），后者会把停止之前已经流出的那几条抹掉（第4节第1条第5条 第二条）。
     */
    private fun closeRun() {
        val current = _ui.value
        if (current.options.isNotEmpty() || current.error != null) return
        _ui.value = current.copy(error = NO_OPENERS_NOTICE)
    }

    companion object {
        /**
         * 一轮主动发跑完、既没有可用开场也没有任何失败文案时的那句**就地短提示**（第4节第1条第5条）。
         *
         * 口径与 [ProactiveFailed] 那条一样：它是"这次失败了，可以再点一次生成"的说明，
         * 不是"这里还没有东西"的空态——后者由面板自己按 options 为空画，两者绝不能共用一句。
         *
         * ⚠ 用户可见文案，按工程纪律应进 strings 资源（中英文两份一起加）。
         *   本轮工单禁改资源，故这条文案与它的落点登记在
         *   ，由并结
         *   `UiStringLiteralBudgetTest` 那笔账。
         */
        const val NO_OPENERS_NOTICE = "这次没生成出能用的开场，可再点一次「生成开场」"
    }
}
