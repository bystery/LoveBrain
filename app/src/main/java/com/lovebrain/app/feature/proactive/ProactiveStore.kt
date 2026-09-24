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
 * 主动发（开场白）这条链的状态持有者（§5.2 第 3 步）。
 *
 * 接管的是 **options、错误、事件归约与"停止/收尾"语义**。§5.2 还列了"模式"，
 * 那一样**到现在仍没搬进来**，但挡路的原因已经换了一个：
 * 从前是 `ComposerMode` / `ResultMode` 长在 ViewModel 里、UI 与 androidTest 都按
 * 嵌套类型名引用，而棘轮里"feature 包不许 import viewmodel"那条规则挡住了这种写法；
 * 现在两个 enum 已经是 `model` 里的顶层类型（[com.lovebrain.app.model.ComposerMode]），
 * 引用点也全部改过 —— 搬"模式"已经没有机械障碍，剩下的是一次真正的状态所有权迁移：
 * 模式要变成 store 的 UiState 字段，而"什么时候退出"这条规则的归属要一起想清楚，
 * 不该塞进别的改动里顺手做一半。
 *
 * 在搬完之前，store 负责的是"模式之外"的全部主动发状态；VM 在收到
 * [Effect.FinishedWithResults] 时才决定要不要退出主动发模式，
 * 于是"生成成功后自动回普通模式"这条规则仍然只有一处实现。
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
         * "结束了且真拿到可展示的开场才退回普通回复"这条规则**在一次赋值里**做完——
         * 以前它跨两个所有者：store 报"有结果"，VM 再去改 `_composerMode`，
         * 于是同一瞬间能读出"模式已退、结果还没清"或反之。
         */
        val composerMode: ComposerMode = ComposerMode.REPLY
    )

    sealed interface Intent {
        /** Engine 事件流里的一个事件 */
        data class Apply(val event: ProactiveEvent) : Intent

        /** 没走到模型就失败（没有知识库等引导文案） */
        data class Fail(val message: String) : Intent

        /** 用户清掉结果与错误（面板上的"清空"）——模式保持 */
        data object Clear : Intent

        /**
         * 用户点蓝字：进 / 出主动发模式。
         *
         * 出去时结果一起清掉（原来的行为）；进来时不动任何东西——
         * "第一次点击只切换模式、不发网络请求"这条约束由调用方保证，这里也不碰网络。
         */
        data object ToggleComposer : Intent

        /** 真的要发起一次主动发生成了（VM 在拿到前台租约之前宣告） */
        data object EnterProactive : Intent

        /** 退出主动发模式，但**留着**已经拿到的开场（停止、或任务被协调器拒绝） */
        data object ExitProactive : Intent
    }

    sealed interface Effect {
        /**
         * 结果区该从"主动发那一类结果"退回普通回复。
         *
         * 触发时机有两种，语义相同：模式真的从 PROACTIVE 退回 REPLY；或主动发拿到了可展示的
         * 开场但模式已经被用户提前切走（这时只剩结果区要归位）。VM 是 `resultMode` 的唯一写者
         * （回复链也写它），所以这条只能是效果，不能让 store 直接改。
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
            Intent.Clear -> _ui.value = _ui.value.copy(options = emptyList(), error = null)
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

            is ProactiveEnded -> {
                // 什么也没生成出来时保留模式：用户刚点了"生成开场"却被打回普通模式，
                // 看起来像按钮没反应。只有真拿到可展示的开场才退。
                if (_ui.value.options.isEmpty()) return
                if (_ui.value.composerMode == ComposerMode.PROACTIVE) {
                    exitProactive(clearResults = false)
                } else {
                    // 模式已经被用户提前切走，只剩结果区那一半要归位（旧代码在这也是无条件退）
                    onEffect(Effect.ExitedProactiveMode)
                }
            }
        }
    }
}
