package com.lovebrain.app.feature.mode

import com.lovebrain.app.model.ResultMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 结果区模式 + 输入变化标记的持有者（复核 第5节第2条 第 6 步"VM 不再持有 feature 的内部状态"）。
 *
 * 接管两颗原本住在 [com.lovebrain.app.viewmodel.LoveBrainViewModel] 里的可写流：
 * - `_resultMode`：结果区当前展示哪一类结果（[ResultMode.REPLY] / [ResultMode.PROACTIVE]）。
 *   写者是两条链——回复（generate）与主动发（generateProactive / 退出模式时收到的那条效果），
 *   所以它留在本 feature 而不下沉进 ProactiveStore：那会让回复链反过来写主动发的状态。
 * - `_inputChanged`：stale 判定的结果标记。result 存在但 messages/ideaHint 与生成时快照不一致时为 true。
 *   写入口是 `checkInputChanged` 那条链，以及成功提交 / 停止生成时的复位。
 *
 * 两条状态都没有跨块协调——store 不需要回调出口，只收 [Intent] 翻状态；
 * 真正的 stale 判据（输入指纹比较）仍在 VM，因为它要读本轮上下文。
 */
class ModeController {

    private val _resultMode = MutableStateFlow(ResultMode.REPLY)
    val resultMode: StateFlow<ResultMode> = _resultMode.asStateFlow()

    private val _inputChanged = MutableStateFlow(false)
    val inputChanged: StateFlow<Boolean> = _inputChanged.asStateFlow()

    sealed interface Intent {
        /** 切换结果区展示哪一类结果（回复链 / 主动发链各自投） */
        data class SetResultMode(val mode: ResultMode) : Intent

        /** 标记 / 复位 stale（checkInputChanged 那条链投；成功提交 / 停止生成时复位为 false） */
        data class SetInputChanged(val changed: Boolean) : Intent
    }

    /** 唯一写入口 */
    fun accept(intent: Intent) {
        when (intent) {
            is Intent.SetResultMode -> _resultMode.value = intent.mode
            is Intent.SetInputChanged -> _inputChanged.value = intent.changed
        }
    }
}
