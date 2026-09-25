package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "生成中"阶段词那条映射。
 *
 * 这段规则原先焊在按钮 composable 里（`elapsedSec < 5 -> … < 15 -> …`），
 * 于是它只能等设备上有人盯着秒数看——本机一格都量不到。
 * §6.1 把按钮的壳搬进设计系统时，它作为"什么时候说什么话"留在了 reply 层，
 * 并抽成纯函数，才有下面这格穷举。
 */
class GeneratingPhaseTest {

    private val analysing = R.string.panel_phase_analysing
    private val drafting = R.string.panel_phase_drafting
    private val deep = R.string.panel_phase_deep_analysing

    @Test
    fun `the two boundaries pick the phase and nothing else`() {
        // 穷举，不是抽样：边界两侧各取一点，再取一个远超边界的值
        val matrix = listOf(
            0 to analysing, 1 to analysing, 4 to analysing,
            5 to drafting, 9 to drafting, 14 to drafting,
            15 to deep, 60 to deep, 3600 to deep
        )
        matrix.forEach { (seconds, want) ->
            assertEquals("第 ${seconds}s 该报的阶段词", want, generatingPhaseResFor(seconds))
        }
    }

    /** 反空跑：三条分支必须真的都能被走到，否则上面那格是在比三个相同的数 */
    @Test
    fun `all three phase resources are actually reachable`() {
        val reached = (0..40).map { generatingPhaseResFor(it) }.toSet()
        assertEquals("三个档都该被 0..40 覆盖到：$reached", setOf(analysing, drafting, deep), reached)
    }
}
