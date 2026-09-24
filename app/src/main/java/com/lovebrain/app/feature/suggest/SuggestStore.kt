package com.lovebrain.app.feature.suggest

import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.model.SuggestEnded
import com.lovebrain.app.model.SuggestEvent
import com.lovebrain.app.model.SuggestFailed
import com.lovebrain.app.model.SuggestFirstToken
import com.lovebrain.app.model.SuggestResult
import com.lovebrain.app.model.SuggestStarted
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.model.SuggestTips
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 今日锦囊的状态持有者（独立复核 §5.2 迁移顺序的第 2 个）。
 *
 * 按 §5.2 的清单接管四样：SuggestRequestContext（改成 [Identity]）、缓存写入的
 * 身份配对、6–8 条校验结果落进状态的方式、以及流式 tips。
 * ViewModel 之后只做 facade：读 `uiState`、把 `Effect` 落成持久化与跨 feature 计数。
 *
 * 与 [com.lovebrain.app.feature.reply.ReplyStore] 同一套形状：Intent 进、Effect 出、
 * 副作用同步回调。这里额外说明**请求归属**为什么不在 store 里判：
 * "谁在跑"只有一本账，就是 ForegroundOperationCoordinator；store 通过 [isCurrentRequest]
 * 问一句，不自己再记一份 requestId。理由与 §2.1 前台操作单 owner 那条一致——
 * 两本账迟早不一致，不一致就会出现"停止按钮停不掉正在跑的任务"那类缺陷。
 */
class SuggestStore(
    private val isCurrentRequest: (String) -> Boolean = { true },
    private val onEffect: (Effect) -> Unit = {}
) {

    /**
     * 发起一次锦囊时要冻结的身份。
     *
     * 缓存的写入与命中判定都用**发起时**的 date / kbName / contextFingerprint /
     * promptVersion：跨午夜才完成的一轮必须写回发起那一天，
     * 不能回读实时 activeKb、也不能重算"今天"（这两条都是过去真出现过的缺陷）。
     */
    data class Identity(
        val requestId: String,
        val kbName: String,
        val kb: KnowledgeBase,
        val date: String,
        val contextFingerprint: String,
        val promptVersion: String
    )

    data class UiState(
        val suggestion: DailySuggestion? = null,
        val streamingTips: List<SuggestTip> = emptyList(),
        val error: String? = null,
        /** 在途请求的身份；没有请求时为 null。缓存写入只信它，不信实时状态 */
        val inFlight: Identity? = null
    )

    sealed interface Intent {
        /** 开始一次请求：冻结身份、清掉上一轮的结果与错误 */
        data class Begin(val identity: Identity) : Intent

        /** Engine 事件流里的一个事件 */
        data class Apply(val event: SuggestEvent) : Intent

        /** 缓存命中：直接出结果，不发请求，也不写回缓存 */
        data class ServeFromCache(val suggestion: DailySuggestion) : Intent

        /** 没走到模型就失败（没有知识库、弱网引导等） */
        data class Fail(val message: String) : Intent

        /** 手动停止：清掉流式半成品并给出"已停止" */
        data object StoppedByUser : Intent

        /** 请求收尾：只有还是当前在途身份时才清空它（避免误清下一个请求） */
        data class Settle(val requestId: String) : Intent
    }

    sealed interface Effect {
        /**
         * 一次成功结果要落进缓存。
         *
         * 带 date / kbName / fingerprint / promptVersion 是因为这些必须与**这次生成**配对；
         * 让调用方事后去读状态就会拿到下一轮的值。
         */
        data class Persist(
            val suggestion: DailySuggestion,
            val identity: Identity
        ) : Effect

        /** 首字耗时——它是 VM 里那个跨 feature 统计量的一部分，不属于锦囊状态 */
        data class FirstTokenObserved(val elapsedMs: Long) : Effect
    }

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    val currentSuggestion: DailySuggestion? get() = _ui.value.suggestion
    val inFlightIdentity: Identity? get() = _ui.value.inFlight

    fun accept(intent: Intent) {
        when (intent) {
            is Intent.Begin -> _ui.value = UiState(inFlight = intent.identity)

            is Intent.Apply -> onEvent(intent.event)

            is Intent.ServeFromCache ->
                _ui.value = _ui.value.copy(suggestion = intent.suggestion, streamingTips = emptyList(), error = null)

            is Intent.Fail -> _ui.value = _ui.value.copy(error = intent.message)

            Intent.StoppedByUser -> _ui.value = _ui.value.copy(
                streamingTips = emptyList(),
                error = "已手动停止"
            )

            is Intent.Settle -> {
                if (_ui.value.inFlight?.requestId == intent.requestId) {
                    _ui.value = _ui.value.copy(inFlight = null)
                }
            }
        }
    }

    private fun onEvent(event: SuggestEvent) {
        if (!isCurrentRequest(event.requestId)) return
        when (event) {
            is SuggestStarted -> _ui.value = _ui.value.copy(
                suggestion = null, streamingTips = emptyList(), error = null
            )

            is SuggestFirstToken -> onEffect(Effect.FirstTokenObserved(event.elapsedMs))

            // 只允许 tips 变长：Engine 每次回报的是"目前完整的清单"，
            // 晚到的短清单把已渲染的条目抹掉过一次（动画会倒退），这里挡住。
            is SuggestTips -> if (event.tips.size > _ui.value.streamingTips.size) {
                _ui.value = _ui.value.copy(streamingTips = event.tips)
            }

            is SuggestFailed -> _ui.value = _ui.value.copy(error = event.message)

            is SuggestResult -> {
                val suggestion = event.suggestion
                _ui.value = _ui.value.copy(suggestion = suggestion)
                val identity = _ui.value.inFlight
                if (suggestion != null && identity != null && identity.requestId == event.requestId) {
                    onEffect(Effect.Persist(suggestion, identity))
                }
            }

            is SuggestEnded -> _ui.value = _ui.value.copy(streamingTips = emptyList())
        }
    }
}
