package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.SuggestTip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 锦囊建议卡的折叠控件：48dp 热区 + **折叠状态要说得出来**（§6.5 第②栏的 stateDescription）。
 *
 * `SuggestTipCard` 原来是 private，为了能被测到改成 internal——和 `ReplyPrimaryActions`
 * 同一个先例：一个控件连被量的资格都没有，它的热区就是不可知的。
 *
 * 这格还是 `stateDescription` 第一次被断言：此前全仓只有两处用它（这里与谈心面板），
 * 没有任何测试守着，而且它写的是内联中文——**文案预算那把尺只认 `Text(` 与
 * `contentDescription =`，announce 给读屏的这句话它根本看不见**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestTipCardSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val tip = SuggestTip(
        id = "tip-1",
        timingCategory = "现在可用",
        action = "早上打个招呼",
        timing = "出门前那十分钟",
        example = "早，今天看起来会很忙，记得喝水。",
        reason = "让她先看到你记得她说过的事"
    )

    private fun mount(matrix: UiMatrix = UiMatrix(360)) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                SuggestTipCard(tip)
            }
        }
    }

    private fun header(): SemanticsProbe.Target {
        val targets = probe.actionableTargets(rule, "锦囊建议卡")
        assertEquals(
            "建议卡应当只有一个折叠入口：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        return targets.single()
    }

    /** §6.5 第①栏：那个入口的边界 ≥48dp */
    @Test
    fun `the tip fold control meets the 48dp floor`() {
        mount()
        probe.assertAllActionableMeetTouchFloor(rule, "锦囊建议卡")
    }

    /** §6.5 第②栏：读屏要听得出"现在收起/展开"，而且换状态时那句公告要跟着换 */
    @Test
    fun `the fold control announces collapsed then expanded`() {
        mount()
        val before = header()
        assertNotNull(
            "折叠控件没有 stateDescription，TalkBack 只会念标题、不会说它是收起的：" + before.describe(),
            before.stateDescription
        )
        assertEquals("收起态该念「已收起」", "Collapsed", before.stateDescription)

        rule.onAllNodes(androidx.compose.ui.test.hasClickAction())[0].performClick()
        rule.waitForIdle()
        assertEquals("点开之后公告必须变成「已展开」", "Expanded", header().stateDescription)
    }

    /** 点开确实多出内容——不然"公告变了"可以只是一句空转 */
    @Test
    fun `expanding reveals the example text`() {
        mount()
        val collapsedLabels = probe.actionableTargets(rule, "锦囊建议卡").map { it.label }
        rule.onAllNodes(androidx.compose.ui.test.hasClickAction())[0].performClick()
        rule.waitForIdle()
        // 生产把示例配文包在弯引号里（“…”），所以这里按子串匹配，不要假装整句相等
        val texts = rule.onAllNodes(
            androidx.compose.ui.test.hasText("早，今天看起来会很忙，记得喝水。", substring = true)
        ).fetchSemanticsNodes()
        assertTrue(
            "展开后示例配文没出现（收起态文案=$collapsedLabels）",
            texts.isNotEmpty()
        )
    }

    /** 最坏那一格：最窄 + 2.0 倍字，热区不缩、公告还在 */
    @Test
    fun `the fold control keeps its floor at 320dp with 2x font`() {
        mount(UiMatrix(320, fontScale = 2.0f))
        probe.assertAllActionableMeetTouchFloor(rule, "锦囊建议卡", "（320dp-font200）")
        assertEquals("Collapsed", header().stateDescription)
    }
}
