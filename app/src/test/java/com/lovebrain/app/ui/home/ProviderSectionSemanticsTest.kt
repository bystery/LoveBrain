package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 供应商区（首页那一格）的四态与触摸区——§6.3 点名的四个目的地之一。
 *
 * 之前这一页本机测不到，是因为它收一个 `SetupViewModel`。这里用 mockk 把 VM 的三个
 * StateFlow 桩住就组合得起来：**"要 VM 才能测"不是测不了的正当理由**，
 * 页面与 VM 之间的合同就是那几条流。
 *
 * 断言只读语义树。折叠图标那句 `contentDescription = if (expanded) "收起" else "展开"`
 * 是内联中文——英文环境下 TalkBack 照样念中文，改之前实测就是这个值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProviderSectionSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun fakeVm(tickets: List<ProviderTicket>, active: ProviderTicket?, ready: Boolean): SetupViewModel {
        val vm = mockk<SetupViewModel>(relaxed = true)
        every { vm.tickets } returns MutableStateFlow(tickets)
        every { vm.activeTicket } returns MutableStateFlow(active)
        every { vm.providerReady } returns MutableStateFlow(ready)
        return vm
    }

    private fun mount(vm: SetupViewModel, matrix: UiMatrix = UiMatrix(360)) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                ProviderSection(viewModel = vm, onBack = {})
            }
        }
    }

    /**
     * 点"最大的那块"=供应商卡片。
     *
     * 别按 index 点：这一页第一个可点击节点是顶部的返回箭头（48x48），
     * 上一版本就是点了它才让三条断言全在"根本没展开"的状态上跑——假绿比红更糟。
     */
    private fun clickCard() {
        val nodes = rule.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "供应商区一个可点击节点都没有" }
        val widest = nodes.withIndex().maxByOrNull { (_, n) -> n.boundsInRoot.width }!!.index
        rule.onAllNodes(androidx.compose.ui.test.hasClickAction())[widest].performClick()
        rule.waitForIdle()
    }

    private fun expand(): SetupViewModel {
        val vm = fakeVm(
            tickets = listOf(
                ProviderTicket(id = "t1", name = "DeepSeek", baseUrl = "https://api.example", model = "r1"),
                ProviderTicket(id = "t2", name = "一个长得离谱的供应商名字用于压测", baseUrl = "https://api.example", model = "")
            ),
            active = null,
            ready = false
        )
        mount(vm)
        clickCard()
        return vm
    }

    @Test
    fun `expanding the card actually reveals the two tickets`() {
        expand()
        val names = rule.onAllNodes(androidx.compose.ui.test.hasText("DeepSeek")).fetchSemanticsNodes()
        assertTrue("点开卡片后没看到供应商条目，说明这一格根本没被展开：" +
            probe.actionableTargets(rule, "供应商区").joinToString { it.describe() }, names.isNotEmpty())
    }

    @Test
    fun `every control in the expanded provider list meets the 48dp floor`() {
        expand()
        probe.assertAllActionableMeetTouchFloor(rule, "供应商区（展开）")
    }

    @Test
    fun `every control in the expanded provider list is labeled`() {
        expand()
        probe.assertAllActionableLabeled(rule, "供应商区（展开）")
    }

    @Test
    fun `the chevron announces its state in the current language`() {
        mount(fakeVm(emptyList(), null, false))
        val before = probe.actionableTargets(rule, "供应商区（收起）")
            .maxByOrNull { it.widthDp }!!
        assertTrue(
            "卡片自身没有任何可读公告：" + before.describe(),
            before.contentDescriptions.isNotEmpty()
        )
        clickCard()
        val after = probe.actionableTargets(rule, "供应商区（展开）")
            .maxByOrNull { it.widthDp }!!
        assertTrue(
            "展开后卡片的公告必须换掉，而且是当前语言的词（收起态=${before.contentDescriptions}，" +
                "展开态=${after.contentDescriptions}）",
            after.contentDescriptions != before.contentDescriptions &&
                after.contentDescriptions.any { it.contains("Collapse", ignoreCase = true) }
        )
    }

    /** §6.3：没有供应商时不能只留一句话——空态要就地给出"添加"这个动作 */
    @Test
    fun `an empty provider list offers the add action from inside the empty state`() {
        mount(fakeVm(emptyList(), null, false))
        clickCard()
        val targets = probe.actionableTargets(rule, "供应商区（空态）")
        val addEntry = targets.filter { it.announced.contains("Add", ignoreCase = true) }
        assertEquals(
            "空态里应当有一个添加供应商的动作：" + targets.joinToString { it.describe() },
            true, addEntry.isNotEmpty()
        )
        assertEquals(
            "空态里只能有一个添加入口——页面下方再摆第二个一模一样的，" +
                "说明这两处是各写各的，不是同一个空态版式：" + targets.joinToString { it.describe() },
            1, addEntry.size
        )
        val tooSmall = addEntry.filter { it.tooSmall(probe.floorDp) }
        assertTrue("添加动作的热区不足 48dp：" + tooSmall.joinToString { it.describe() }, tooSmall.isEmpty())
    }
}
