package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.domain.port.InMemorySettingsStore
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 供应商区（首页那一格）的四态与触摸区——第6节第3条 点名的四个目的地之一。
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

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

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
                after.contentDescriptions.any { it.contains(ctx.getString(R.string.action_collapse)) }
        )
    }

    /** 第6节第3条：没有供应商时不能只留一句话——空态要就地给出"添加"这个动作 */
    @Test
    fun `an empty provider list offers the add action from inside the empty state`() {
        mount(fakeVm(emptyList(), null, false))
        clickCard()
        val targets = probe.actionableTargets(rule, "供应商区（空态）")
        val addEntry = targets.filter { it.announced.contains(ctx.getString(R.string.provider_add)) }
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
     * Q22「供应商区从子页回来不重读」的证人。
     *
     * 喂的坏样子（三步）：**列表已渲染 → 数据在别处变了 → 模拟返回**。
     * - "在别处变"走**真源**（[InMemorySettingsStore]，同一个 `SetupViewModel` 背后那份落盘），
     *   不是桩方法——所以重读实现本身（`SetupViewModel.refreshTicketsFromStore` 按盘上真值
     *   刷新三条流）也和接线一起被这一格判了；这也是本格用**真 VM** 而不是上面 [fakeVm] 的原因。
     * - "模拟返回"＝卸树再挂回：`SetupRoot` 的 `AnimatedContent` 换页离开/回来就是把这一屏
     *   整棵拔掉又重挂，重进组合时 `LaunchedEffect(Unit)` 那颗重读点必须再跑一次。
     *   受"setContent 一格一次"的既有坑约束，用一颗 `mounted` 布尔在**同一次** setContent 里
     *   完成拔/挂（仓里 Activity 重建那一族测不了的坑表另账）。
     *
     * 回退成什么就红：把 ProviderSection 里那两颗回来重读点（`LaunchedEffect(Unit)` /
     * ON_RESUME 观察者）删掉任何一颗相关接线，重挂后列表仍停在出发前快照，
     * "新名字"断言红、"旧名字不该还在"断言红。
     * ⚠ 与 K25 的边界：重读的是**列表流**；编辑浮层的未提交字段归 ProviderFormBody 的
     * rememberSaveable（证人 `ProviderFormSemanticsTest`），这一格不碰那半条。
     */
    @Test
    fun `returning to the provider section re-reads tickets that changed while away`() {
        val store = InMemorySettingsStore()
        store.setWorkerTickets(
            listOf(
                ProviderTicket(id = "t1", name = "离开前的名字", baseUrl = "https://api.example", model = "r1")
            )
        )
        val vm = SetupViewModel(store, mockk<DeepSeekRepository>(relaxed = true))
        var mounted by mutableStateOf(true)
        rule.setContent {
            if (mounted) ProviderSection(viewModel = vm, onBack = {})
        }
        rule.waitForIdle()
        clickCard()
        assertTrue(
            "先决条件没立住：离开前列表没渲染出旧名字（夹具问题，先查装配）",
            rule.onAllNodes(androidx.compose.ui.test.hasText("离开前的名字")).fetchSemanticsNodes().isNotEmpty()
        )

        // 数据在别处变了：同一份真源被另一个宿主写走，本 VM 的流毫不知情（还停在旧快照）
        store.setWorkerTickets(
            listOf(
                ProviderTicket(id = "t1", name = "别处保存的新名字", baseUrl = "https://api.example", model = "r1")
            )
        )

        // 模拟返回：离开目的地（整棵卸树）→ 回到这一屏（重新进组合）
        rule.runOnUiThread { mounted = false }
        rule.waitForIdle()
        rule.runOnUiThread { mounted = true }
        rule.waitForIdle()
        // 重挂后 [ProviderManageEntry] 的展开态归位成收起（它是一颗本地 remember）——
        // 不重新点开就断言，等于拿「没展开」去证「没重读」，红是夹具的不是生产的。
        clickCard()
        rule.waitForIdle()

        assertTrue(
            "回到供应商区必须重读落盘工单——列表要反映已保存的事实（Q22）",
            rule.onAllNodes(androidx.compose.ui.test.hasText("别处保存的新名字")).fetchSemanticsNodes().isNotEmpty()
        )
        assertFalse(
            "出发前的旧快照不许还挂在树上",
            rule.onAllNodes(androidx.compose.ui.test.hasText("离开前的名字")).fetchSemanticsNodes().isNotEmpty()
        )
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
     * ⇒ **这不是"已证明的生产缺陷"**：换成真实 VM 可能就会空闲。本轮不再追这条路，
     * 改直接挂载那颗开关——:531 判的是那颗控件自己的边界，控件本体量得到就是证据。
     * （同一条路留下的账：`ProviderEditDialog` 在桩 VM 下不空闲，记进 第4节 待查。）
     *
     * 而这一格的**正题**是：那颗开关的源码注释原来写着"扩大到 48×32，**满足** 48dp 无障碍下限"，
     * 531 要的是 `bounds ≥48×48` ⇒ 宽度到了、**高度 32 没到**，那句话是假的。
     * 上一格（`08b762d`）只把假话改成实话、把这处内联 `48.dp` 在棘轮里标成已知缺陷，**没量过**。
     */
    /**
     * 挂"思考模式"那一整行，**不是**光挂那颗开关。
     *
     * 差别很重要：直接 `MiniSwitch(label = ...)` 等于**测试自己把名字喂进去**，
     * 生产上调用点忘了起名也照样绿（恒绿假闸）。挂这一行，测的就是
     * "屏幕上那行字与开关的名字由同一处配对"这件事本身。
     *
     * ⚠ **2026-10-06 M2c（基线 §③-4 / D1 §② C5 的"热区买一次"）**：`MiniSwitchRow` 里那颗 toggle
     * 从"行尾一个 48×48 见方盒"改成了**整行可点**——`toggleable` + `Role.Switch` + `contentDescription`
     * 现在挂在这一行的 `Row` 自己身上（高垫到全局下限、宽铺满整行），行尾只剩 36×20 的**纯视觉**轨道。
     * 于是下面这两格的**判据一字没改，却仍是对的**：
     *  - "逐颗量到恰好一颗 toggle"——整行只有一个 `ToggleableState`（装饰轨道不挂语义），计数还是 1；
     *  - "两轴 ≥48"——现在量到的是整行那颗 toggle（宽 = 整行、高 = min 48），比从前那颗 48 见方盒还宽。
     * **回退成什么会红**：把 toggle 又塞回行尾一颗只垫高度的窄盒（宽 <48）→ `tooSmall` 那条红；
     *  把装饰轨道也做成可点 → 出现第二颗 toggle → `assertEquals(1, toggles.size)` 红。
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
     * - 光标闪烁：**不是**（`InputFieldLabelsTest`/`RecordSentDialogSheetTest`（已删） 挂着文本框是绿的，
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

    // ─────────────────────────────────────────────────────────────────────────────
    // request2 §一（P0 叠放缺陷）的结构性守卫。
    //
    // 根因：`AnimatedVisibility` 的 KDoc 明写「内容若发射多颗布局节点，按 Box 叠放」——
    // 展开态里的分隔线 / 供应商列表 / 「添加供应商」曾是它的三颗**直接子节点**，
    // 于是列表与添加入口叠在同一块位置。修法是补一颗真正的纵向 Column，让三者回到同一条排布流。
    //
    // 下面四格都**直接挂 `ProviderManageEntry`**（internal，同模块可见）而不是整页：
    // ①回调可以用录制 lambda 逐颗点验，不绕 `SetupViewModel` 的 mockk 桩（那串坑记在本文件上方）；
    // ②点「添加」不会开出 `ProviderEditDialog`——Dialog+文本框永不空闲那堵墙（账本 第45节第1条）
    //   不在这条路径上。既有格（`expanding…`/`an empty provider list…` 等）判据一字未动，
    // 这几格不替代它们，是补上「叠放」这一问：空态添加格数、48dp 下限、命名等旧判据继续由旧格把守。
    //
    // 定位把手：`ProviderExpandTags`（生产侧三颗 testTag，只增语义锚点、不改任何像素）。

    private class RecordedCallbacks {
        val activated = mutableListOf<String>()
        val edited = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var adds = 0
    }

    private val twoTickets = listOf(
        ProviderTicket(id = "t1", name = "DeepSeek", baseUrl = "https://api.example", model = "r1"),
        ProviderTicket(id = "t2", name = "一个长得离谱的供应商名字用于压测", baseUrl = "https://api.example", model = "")
    )

    private fun mountEntry(tickets: List<ProviderTicket>, cb: RecordedCallbacks) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(360).RenderIn(deviceDensity) {
                ProviderManageEntry(
                    tickets = tickets,
                    activeTicket = null,
                    providerReady = true,
                    onActivate = { cb.activated += it },
                    onEdit = { cb.edited += it.name },
                    onDelete = { cb.deleted += it.name },
                    onAdd = { cb.adds++ }
                )
            }
        }
        rule.waitForIdle()
    }

    /**
     * ⚠ **读法必须是未合并语义树**（`useUnmergedTree = true`，与仓里另一把 tag 尺
     * `DragHandleHitBandTest.rectOf` 的默认值一致）。原因（2026-10-10 实测，别再重踩）：
     * `ProviderExpandTags.DIVIDER` 与 `.LIST` 那两颗节点**身上只有 TestTag 这一个语义属性**
     * （一颗 `HorizontalDivider`、一颗空 `Column`），而 TestTag 那颗键的无障碍重要性是"不重要"
     * ⇒ 它们在**合并后的语义树**里根本不存在：外面那层卡片 `Modifier.clickable{ expanded = … }`
     * （`ProviderSection.kt:334`）会把这类不重要后代整个吞掉。
     * 三颗里只有 `ADD_ROW` 活下来——它挂在那颗 `Text` 上，同节点还带着 Text 与 OnClick，才是"重要"节点。
     * 于是原来的 `onAllNodes(hasTestTag(DIVIDER))`（默认走合并树）拿到**空列表**，
     * `.single()` 抛 `NoSuchElementException: List is empty`：
     * 红的是这台仪器读错了树，**不是**那三样没画。改读未合并树后实到
     * divider@53px / list@54px / add_row@167px 三个严格递增的顶 ⇒ 叠放那条缺陷已经是修好的。
     * 判据一寸没松：仍然要三颗都在、仍然比顶坐标严格递增、仍然要求添加顶不落在列表矩形之内。
     */
    private fun nodesOf(tag: String) =
        rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes()

    private fun topOf(tag: String): Float =
        nodesOf(tag).single().boundsInRoot.top

    /**
     * 格①（叠放的正判据）：展开态里 分隔线 / 列表 / 添加入口 三条**顶坐标严格递增**，
     * 且添加入口的顶不在列表柱的矩形之内（真上下排布，不是错开一点的叠放）。
     *
     * 喂成什么坏样子会红：
     * - 撤掉 AnimatedVisibility 里那颗 Column（回到原始缺陷）⇒ 三颗被 Box 叠放，
     *   三顶相等，第一条断言当场红；
     * - 换成 `Row` ⇒ 分隔线与列表顶仍相等，红；
     * - 把添加行用绝对定位/zIndex 浮上去 ⇒ 添加顶落进列表矩形，第二条断言红；
     * - 顺序写反（添加在前）⇒ 递增断言红。
     */
    @Test
    fun `expanded divider list and add row stack strictly top to bottom`() {
        val cb = RecordedCallbacks()
        mountEntry(twoTickets, cb)
        clickCard()

        val dividerTop = topOf(ProviderExpandTags.DIVIDER)
        val listTop = topOf(ProviderExpandTags.LIST)
        val listBottom = nodesOf(ProviderExpandTags.LIST).single().boundsInRoot.bottom
        val addTop = topOf(ProviderExpandTags.ADD_ROW)
        assertTrue(
            "展开态三颗节点必须严格上下排（叠放=修复前回退）：分隔线顶=$dividerTop，列表顶=$listTop，添加顶=$addTop",
            dividerTop < listTop && listTop < addTop
        )
        assertTrue(
            "添入口的顶不许落在列表柱矩形之内（那仍是叠放）：列表底=$listBottom，添加顶=$addTop",
            addTop >= listBottom - 0.5f
        )
    }

    /**
     * 格②（回归护栏）：`tickets` 为空时**不画底部添加入口**这件事不变——
     * 空态只留 `LbAsyncState` 就地那一颗添加动作，且点它走的仍是 `onAdd`。
     *
     * 喂成什么坏样子会红：删掉 `if (tickets.isNotEmpty())` 那道闸（底部入口在空态也画出来）
     * ⇒ ADD_ROW 那格出现，第一断言红；把空态动作的回调接错 ⇒ `adds` 不是 1，第三断言红。
     * （整页视角下"空态恰一个添加入口"仍由既有格 `an empty provider list offers…` 把守，两格互补不替代。）
     */
    @Test
    fun `empty list does not draw the bottom add row but keeps the empty state action`() {
        val cb = RecordedCallbacks()
        mountEntry(emptyList(), cb)
        clickCard()

        assertEquals(
            "空态不许再画底部添加行（那会叠出第二个一模一样的入口）",
            0, nodesOf(ProviderExpandTags.ADD_ROW).size
        )
        assertEquals("空态时内容列表柱不在树上", 0, nodesOf(ProviderExpandTags.LIST).size)
        val actions = rule.onAllNodes(hasTestTag(LbAsyncTags.ACTION)).fetchSemanticsNodes()
        assertEquals("空态应当恰有一颗就地添加动作：" + actions.size, 1, actions.size)
        rule.onAllNodes(hasTestTag(LbAsyncTags.ACTION))[0].performClick()
        assertEquals("点空态添加动作必须打到 onAdd", 1, cb.adds)
    }

    /**
     * 格③（业务回调一颗没丢）：选供应商 / 编辑 / 删除 / 添加，四颗都在搬家后的树上点得到、点得对。
     *
     * 喂成什么坏样子会红：搬家时漏接任何一条回调（比如列表行的 `onActivate` 被写死空 lambda、
     * 编辑那颗换了实参没接上）⇒ 对应清单为空或条数不对；
     * 添加入口被挪出展开区（折叠就能点到）⇒ 折叠态点不到、`adds` 为 0（由格④的节点账一起锁死）。
     * 行内动作按顶坐标排序取第几行，不赌语义树遍历顺序。
     */
    @Test
    fun `activate edit delete and add callbacks all still fire after the layout fix`() {
        val cb = RecordedCallbacks()
        mountEntry(twoTickets, cb)
        clickCard()

        fun orderByTop(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes()
            .let { nodes -> nodes.indices.sortedBy { nodes[it].boundsInRoot.top } }
            .map { rule.onAllNodes(hasText(text))[it] }

        // 选供应商：点第一行的名字（触点落进行柱，由那颗 fillMaxWidth 的行 clickable 收到）
        orderByTop("DeepSeek").first().performClick()
        rule.waitForIdle()
        assertEquals("点供应商行必须启用那张工单", listOf("t1"), cb.activated)

        // 编辑第二行 / 删除第一行：按顶坐标排序取第几行，不赌语义树遍历顺序
        orderByTop("编辑")[1].performClick()
        rule.waitForIdle()
        assertEquals("点第二行的编辑必须把那张工单交回 onEdit",
            listOf("一个长得离谱的供应商名字用于压测"), cb.edited)

        orderByTop("删除").first().performClick()
        rule.waitForIdle()
        assertEquals("点第一行的删除必须把那张工单交回 onDelete", listOf("DeepSeek"), cb.deleted)

        // 添加：展开区底部那一行
        rule.onAllNodes(hasTestTag(ProviderExpandTags.ADD_ROW)).onFirst().performClick()
        assertEquals("点「添加供应商」必须打到 onAdd", 1, cb.adds)
    }

    /**
     * 格④（折叠态不多出节点）：修复加的那颗 Column 与其三颗子节点都只在**展开时**存在；
     * 折叠态整棵树仍只有卡片那一颗可点节点，展开区一个节点都不许漏出来。
     *
     * 喂成什么坏样子会红：把 Column（或列表/添加行）挪到 `AnimatedVisibility` **外面**
     * ⇒ 折叠态就量得到 DIVIDER/LIST/ADD_ROW 三颗 tag，红；
     * 给卡片外面再裹一层可点的壳 ⇒ 可点节点 >1，红。
     * （真实触摸命中折叠卡片本身、展开/收起动画的观感，语义树验不了——见报告「只能真机验」。）
     */
    @Test
    fun `collapsed card exposes no expand region nodes`() {
        val cb = RecordedCallbacks()
        mountEntry(twoTickets, cb)

        assertEquals(
            "折叠态整棵树只许有卡片这一颗可点节点",
            1, rule.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().size
        )
        for (tag in listOf(ProviderExpandTags.DIVIDER, ProviderExpandTags.LIST, ProviderExpandTags.ADD_ROW)) {
            assertEquals("折叠态不许出现展开区节点：$tag", 0, nodesOf(tag).size)
        }
        assertTrue(
            "折叠态连供应商名字都不该在树上",
            rule.onAllNodes(hasText("DeepSeek")).fetchSemanticsNodes().isEmpty()
        )
    }
}
