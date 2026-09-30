package com.lovebrain.app.feature.roundstate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轮次身份 + 仅看本轮开关的持有者（复核 §5.2 第 6 步"VM 不再持有 feature 的内部状态"）。
 *
 * 接管两颗原本住在 [com.lovebrain.app.viewmodel.LoveBrainViewModel] 里的可写流：
 * - `_generationRoundId`：稳定的轮次身份，只在真正完成一次新的整轮 generate 时变化
 *   （单条改写 / undo / feedback 等原地操作不改变它）。ResultArea 的 viewMode 只以它重置。
 * - `_onlyThisRound`：仅看本轮开关。开启后只携带通用生成规则、本轮真实消息和本轮想法，
 *   排除旧画像、关系阶段、历史对话、场景、事项、经验与持续意图。开关属于当前工作轮次；
 *   本轮重生成保留，开启新轮次或切档案后恢复默认。
 *
 * 为什么"切换 onlyThisRound 后旧结果立即 stale"这条跨块协调不在 store 里做：
 * 它要读 VM 的本轮上下文与输入指纹（生成链上的公共上游），store 不知道这些。
 * 所以切换走 [ToggleOnlyThisRound]，store 只负责翻转状态、再同步回调 [onOnlyThisRoundChanged]；
 * 复位（新轮次 / 切库）走 [SetOnlyThisRound]，不触发回调——那些路径各自有自己的 stale 判定。
 */
class RoundStateStore(
    /** 切换 onlyThisRound 后旧结果需立即 stale；这条跨块协调由调用方决定怎么做。同步调用。 */
    private val onOnlyThisRoundChanged: () -> Unit = {}
) {

    private val _generationRoundId = MutableStateFlow(0)
    val generationRoundId: StateFlow<Int> = _generationRoundId.asStateFlow()

    private val _onlyThisRound = MutableStateFlow(false)
    val onlyThisRound: StateFlow<Boolean> = _onlyThisRound.asStateFlow()

    /** VM 内部读取的同步快照（生成链上的指纹 / 输入冻结都要读它） */
    val onlyThisRoundNow: Boolean get() = _onlyThisRound.value

    sealed interface Intent {
        /** 完成一次新的整轮 generate 时递增（回退那一路也用它，不递减、不用 magic number） */
        data object BumpRoundId : Intent

        /** 复位仅看本轮开关（新轮次 / 切档案）；不触发 stale 判定 */
        data class SetOnlyThisRound(val value: Boolean) : Intent

        /** 切换仅看本轮开关；翻转后同步触发 [onOnlyThisRoundChanged] 让旧结果标 stale */
        data object ToggleOnlyThisRound : Intent
    }

    /** 唯一写入口 */
    fun accept(intent: Intent) {
        when (intent) {
            Intent.BumpRoundId -> _generationRoundId.value++
            is Intent.SetOnlyThisRound -> _onlyThisRound.value = intent.value
            Intent.ToggleOnlyThisRound -> {
                _onlyThisRound.value = !_onlyThisRound.value
                onOnlyThisRoundChanged()
            }
        }
    }
}
