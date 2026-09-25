package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule

import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
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
        // ⚠ relaxed 桩**不覆盖所有返回类型**：表单里读的两条流都是 `StateFlow<String?>` /
        // `StateFlow<Boolean>`，relaxed 交回的是"泛型 mock"，`.value` 一取就是
        // `ClassCastException: java.lang.Object cannot be cast to java.lang.String`，
        // 而且**栈顶指向被内联 lambda 的一个不存在的行号**（ProviderSection.kt:872，
        // 这文件只有 583 行），看着像生产代码坏了。第一版两格就红在这儿。
        // ⇒ 先查装配再查实现（坑表 75/73 那一族）；报错里的行号超出文件长度本身就是
        //   "这是夹具/内联的问题，不是那一行坏了"的信号。
        every { vm.formError } returns MutableStateFlow<String?>(null)
        every { vm.saving } returns MutableStateFlow(false)
        every { vm.getKeyMask(any()) } returns ""
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

    /**
     * 那颗"思考模式"开关，**直接挂载**量。
     *
     * 先记一条走不通的路（走不通的是夹具，不是生产）：本来的写法是
     * 展开卡片 → 点 `R.string.provider_add`（**文案走资源**，写死「添加供应商」在本机
     * en 环境下 matcher 直接 0 命中）→ 在表单里找 toggle。
     * 结果两件事挡住：① relaxed 的 `SetupViewModel` 对 `StateFlow<String?>` 交回泛型 mock，
     `.value` 一取就 `ClassCastException`，且栈顶是**内联 lambda 的行号**
     * （`ProviderSection.kt:872`，而该文件只有 583 行）——看着像生产坏了，其实是桩不对；
     * 补了 `formError`/`saving`/`getKeyMask` 三条桩之后 ②`AppNotIdleException`：
     * Compose 在 60 秒内不空闲。
     * ⇒ **这不是"已证明的生产缺陷"**：换成真实 VM 可能就会空闲。本格不再追这条路，
     * 改直接挂载那颗开关——:531 判的是那颗控件自己的边界，控件本体量得到就是证据。
     * （同一条路留下的账：`ProviderEditDialog` 在桩 VM 下不空闲，记进 §4 待查。）
     *
     * 而这一格的**正题**是：那颗开关的源码注释原来写着"扩大到 48×32，**满足** 48dp 无障碍下限"，
     * :531 要的是 `bounds ≥48×48` ⇒ 宽度到了、**高度 32 没到**，那句话是假的。
     * 上一格（`08b762d`）只把假话改成实话、把这处内联 `48.dp` 在棘轮里标成已知缺陷，**没量过**。
     */
    /**
     * 挂"思考模式"那一整行，**不是**光挂那颗开关。
     *
     * 差别很重要：直接 `MiniSwitch(label = ...)` 等于**测试自己把名字喂进去**，
     * 生产上调用点忘了起名也照样绿（恒绿假闸）。挂这一行，测的就是
     * "屏幕上那行字与开关的名字由同一处配对"这件事本身。
     */
    private fun mountSwitch(checked: Boolean) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(360).RenderIn(deviceDensity) {
                MiniSwitchRow(checked = checked, onCheckedChange = {})
            }
        }
    }

    @Test
    fun `the thinking-mode switch meets the 48dp floor on both edges`() {
        mountSwitch(checked = true)
        val toggles = probe.actionableTargets(rule, "MiniSwitch").filter { it.isToggle }
        assertEquals(
            "直接挂载就该正好量到那颗开关：" +
                probe.actionableTargets(rule, "MiniSwitch").joinToString { it.describe() },
            1, toggles.size
        )
        val t = toggles.single()
        assertTrue(
            "开关的热区小于 48×48dp（源码注释曾称『满足 48dp 下限』）：" + t.describe(),
            !t.tooSmall(probe.floorDp)
        )
    }

    /**
     * 那颗开关也得说得出自己是什么。
     *
     * "思考模式"那四个字是**旁边另一个节点**——眼睛看得见配对，读屏只念得出开关那颗。
     * 判据只看 toggle 自己的 `labeled`，不拿"这一屏里有没有出现过那几个字"当证据
     * （坑表 68 那一族：取第一个非空的判据会被旁边的标签蹭过去）。
     */
    /**
     * 那颗开关也得说得出自己是什么（`MiniSwitchRow` 那一格的第二条断言）。
     * 判据只看 toggle 自己的 `labeled`——"思考模式"那四个字在旁边另一个节点上，
     * 屏幕上看得见配对不等于读屏听得见（坑表 68 那一族）。
     */
    @Test
    fun `the thinking-mode switch names itself`() {
        mountSwitch(checked = false)
        val t = probe.actionableTargets(rule, "MiniSwitch").filter { it.isToggle }.single()
        assertTrue(
            "开关既没有 contentDescription 也没有文案，读屏只会念「开关」：" + t.describe(),
            t.labeled
        )
    }

    /**
     * ⚠ **这三格当时没留下**（本文件到此为止只是历史；现在的守卫在
     * `ProviderFormSemanticsTest`，八格，已经跑起来了）。
     * 当时写不下去的原因记在这里，别让下一个人重跑：
     * ①整张表单每个可交互节点过 48dp；②每个节点有名字；③那颗"保存"的几何与角色。
     *
     * **原因已经做实验定下来了，不是猜**：一次性诊断件（已收档到
     * `_temp/ZzProviderFormDumpTest.kt.retired-2026-09-26` 之前那一份
     * `ZzDialogTextFieldIdleProbeTest.kt.retired`）把**裸 `Dialog` + 一颗
     * `OutlinedTextField`** 单独挂起来，`waitForIdle()` 报
     * `Compose did not get idle after 1,013,194 attempts in 60 SECONDS`。
     * 也就是说：这台仪器里"Dialog 窗口 + 文本框焦点"永不空闲，
     * **`ProviderEditDialog` 无罪**——表单里没有回路、没有无限动画、
     * 状态头部 13 个 `remember` 也都在回调里赋值。
     *
     * 三条假设的死法都留证据，别当结论用：
     * - 光标闪烁：**不是**（`InputFieldLabelsTest`/`RecordSentDialogSheetTest` 挂着文本框是绿的，
     *   它们都不在 Dialog 窗口里）；
     * - 那两颗 spinner：**不是**（条件渲染，挂载时没画出来）；
     * - Dialog + 文本框这一组合：**是它**。
     *
     * ⇒ 出路也不是"继续调时钟"（`autoAdvance=false` 手动推帧试过，仍不空闲），
     *   而是**把表单内容抽成一颗可单挂的 internal 组件**——这一条已经在 `c202da2` 之后
     *   做完（`ProviderFormBody`，约 190 行的机械搬动），三格现在都在
     *   `ProviderFormSemanticsTest` 里，且各自过了变异反证。
     *   报错线索留一条通用的：**栈顶行号超出文件长度**
     *   （`ProviderSection.kt:872`，当时全文 583 行）＝是内联/夹具的问题，不是那一行坏了。
     */
}
