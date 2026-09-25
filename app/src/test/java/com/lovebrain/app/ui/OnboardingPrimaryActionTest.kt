package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.panel.OnboardingFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 :490「禁止创建只在一个页面看起来不一样的按钮/卡片」——**第一手证据是量出来的，
 * 不是在源码里数 `.background(Primary)` 数出来的**。
 *
 * 上一格收 `LbScreenScaffold` 时顺手量到首次引导那颗主按钮是 **328x34dp**，
 * 不到 §6.5 :531 的 48dp，当时只记了账没改。这一格把它量全：
 * 整页所有可交互节点过一遍热区下限。
 *
 * 为什么先做这一页而不是十处一起动：静态扫"自造品牌色按钮"这次实扫到
 * **20 处 / 9 个文件**（`_temp/primary_inventory.json`，账本 §38 有逐处判读），
 * 但**扫出来的位置不等于该搬的位置**——同一串 `.background(Primary)` 里
 * 有的是页面主动作（该走 `LbPrimaryButton`），有的是选中态 chip、
 * 有的是"生成中/停止"这种带状态的切换。哪一处算哪种，得一处一处读；
 * 而**能证明"这里确实坏了"的只有尺寸**。所以顺序是：量 → 修量到红的 →
 * 把判读记进账本，不拿 20 这个数字当战果。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingPrimaryActionTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private fun mount() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                OnboardingFlow(onSkip = {}, onComplete = {}, onOpenSettings = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 整页每一颗可交互节点都要够 48dp（§6.5 :531） */
    @Test
    fun `every control on the onboarding screen meets the touch floor`() {
        mount()
        probe.assertAllActionableMeetTouchFloor(rule, "首次引导")
    }

    /**
     * 反空跑：这一页至少要有**一颗主动作**在树里。
     * 上一格的判据只断言"全都 ≥48"，把整页按钮全删了它照样绿——所以要有一格钉住数量。
     */
    @Test
    fun `the onboarding screen keeps its step action`() {
        mount()
        val targets = probe.actionableTargets(rule, "首次引导")
        org.junit.Assert.assertTrue(
            "首次引导至少该有跳过 + 那一页的主动作，实到 ${targets.size}：" +
                targets.joinToString { it.describe() },
            targets.size >= 2
        )
    }
}
