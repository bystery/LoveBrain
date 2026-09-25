package com.lovebrain.app.ui.panel.reply

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import kotlinx.coroutines.delay

/**
 * "生成中"那句可见文案（阶段词 · 已用秒数 点击停止）。
 *
 * 上一格把它从按钮组件里搬出来，是因为按钮当时住在 `core/designsystem`：
 * 设计系统不该知道"生成"这件事，也不该持有 5s/15s 这套阶段规则——
 * 它只负责"生成中"那层壳（脉冲、进度环、点下去就是停止）。
 *
 * 计时原先挂在按钮的 `LaunchedEffect(Unit)` 上，也就是**只有按钮在组合里时才走表**；
 * 搬到这里语义不变：这个 composable 也只在生成中的那条分支里被调用，
 * 退出组合即失去 `remember`，下一次生成从 0 秒重新数。
 */
@Composable
internal fun generatingLabel(): String {
    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        elapsedSec = 0
        while (true) {
            delay(1000)
            elapsedSec++
        }
    }
    val phase = stringResource(generatingPhaseResFor(elapsedSec))
    return stringResource(R.string.panel_analysing_with_seconds, phase, elapsedSec)
}

/**
 * 阶段词的纯映射。原先这三行写在 composable 里 ⇒ 本机量不到，
 * 5s / 15s 两个边界只能等设备上有人盯着秒数看。抽成函数后它有一格一格的矩阵。
 */
internal fun generatingPhaseResFor(seconds: Int): Int = when {
    seconds < 5 -> R.string.panel_phase_analysing
    seconds < 15 -> R.string.panel_phase_drafting
    else -> R.string.panel_phase_deep_analysing
}
