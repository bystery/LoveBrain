package com.lovebrain.app.feature.proactive

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
        val error: String? = null
    )

    sealed interface Intent {
        /** Engine 事件流里的一个事件 */
        data class Apply(val event: ProactiveEvent) : Intent

        /** 没走到模型就失败（没有知识库等引导文案） */
        data class Fail(val message: String) : Intent

        /** 用户清掉结果与错误（面板上的"清空"） */
        data object Clear : Intent
    }

    sealed interface Effect {
        /**
         * 一次请求结束、且**确实产出了可展示的开场**。
         *
         * 旧实现是 reducer 里直接调 `exitProactiveMode()`——状态持有者顺手改了
         * UI 会话模式，等于两个所有者。现在只报"有结果、结束了"，
         * 要不要退出由 VM 决定，规则本身还是只有一条（见 [ProactiveEnded] 分支）。
         */
        data object FinishedWithResults : Effect

        /** 首字耗时：VM 里那个跨 feature 统计量 */
        data class FirstTokenObserved(val elapsedMs: Long) : Effect
    }

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    val currentOptions: List<ProactiveOption> get() = _ui.value.options

    fun accept(intent: Intent) {
        when (intent) {
            is Intent.Apply -> onEvent(intent.event)
            is Intent.Fail -> _ui.value = _ui.value.copy(error = intent.message)
            Intent.Clear -> _ui.value = UiState()
        }
    }

    private fun onEvent(event: ProactiveEvent) {
        if (!isCurrentRequest(event.requestId)) return
        when (event) {
            is ProactiveStarted -> _ui.value = UiState()

            is ProactiveFirstToken -> onEffect(Effect.FirstTokenObserved(event.elapsedMs))

            is ProactiveOptions -> _ui.value = _ui.value.copy(options = event.options)

            is ProactiveFailed -> _ui.value = _ui.value.copy(error = event.message)

            is ProactiveEnded -> {
                // 什么也没生成出来时保留模式：用户刚点了"生成开场"却被打回普通模式，
                // 看起来像按钮没反应。只有真拿到可展示的开场才通知退出。
                if (_ui.value.options.isNotEmpty()) onEffect(Effect.FinishedWithResults)
            }
        }
    }
}
