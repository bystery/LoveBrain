package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.MemoryKind
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.MuteDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 本轮参考记忆的**纠正流程**：入口换了形状，锚点跟着换，行为一条没减。
 *
 * 现在这一行的形状：每条参考旁边常驻的只有那颗短词入口「修正记忆」，点开才出那几种原有说法
 * （这条不对 / 暂时别提 / 已结束 / 不是她的 / 撤销）；行上不铺四个常驻大按钮，菜单里
 * 也不给每项跟一行技术解释。旧形状是行尾一颗只有字形、读屏念不出用途的 `⋯`。
 * 浮层本体仍是**独立 state holder + 单一宿主**，没跟着入口一起动。
 *
 * 锚点从哪儿取，这一格里两种都有，规矩只有一条：**取屏幕上真的渲染出来的那一份**。
 * - 行尾那颗入口与菜单里的「暂时别提」——生产现在走 `stringResource`
 *   （`R.string.memory_fix_entry`、`R.string.memory_action_mute`），所以这两颗的锚点
 *   **也从同一份资源取**：这台机器的 JVM 默认解析成英文，写死中文会恒红，写死英文又把
 *   中文那份锁死。中英两边真的各有说法由 `MemoryRefsFeedTest` 那一格钉，不在这里重复。
 * - 两颗浮层标题、取消/确认——等  已把它们从内联中文接进 `stringResource`
 *   （`memory_mute_sheet_title`/`memory_mark_wrong_sheet_title`/复用 `a11y_action_cancel`
 *   /`a11y_action_confirm`），本机默认落英文 ⇒ 这几颗的锚点也从同一份资源取
 *   （`muteTitle`/`markWrongTitle`/`cancelLabel`/`confirmLabel`），与菜单五项同一口径。
 * - 三档时长——生产仍写在 `durationLabel()` 的 `when` 分支上（本轮没动），英文环境
 *   渲染出来的也是这几个中文字，所以钉这几个字才对。
 * - 输入框那句名字走 `R.string.memory_wrong_input_hint`（中英各一份，生产确实是
 *   `stringResource` 取的），所以那一颗的锚点必须从资源取，不许写死中文。
 *
 * 文案哪天整体搬进 res，前一组要跟着改成资源入口——那是**换锚点**，不是把判据放宽成
 * "有一颗按钮就行"。
 *
 * **2026-10-04 15:32 那一次全量单测这格 8 条红，逐条判的是"锚点读错"，生产一条没改判据**：
 * 上一手把菜单里那五项从 Kotlin 常量搬进 `stringResource`（`MemoryRefsFeed.kt` 的
 * `CorrectionDropdownItem(stringResource(R.string.memory_action_*))`），但这一颗的
 * `openMenuAndPick(...)` 九个调用点还钉着搬之前那份中文（`"不对"` / `"暂时别提"`）。
 * 红在 `openMenuAndPick` 里那句 `assertEquals(1, nodes.size)`，报的是**实到 0**，两条独立读数都对得上：
 * - 菜单五项的中文那份现在是「这条不对」（`values/strings.xml:297`），`hasText` 按**全等**取，
 *   所以「不对」这一支在**两种语言下都是 0**——`values-en` 那份是 "This one is wrong"；
 * - 「暂时别提」这一支在 `values/strings.xml:298` 里逐字没变，却在同一台机器上报 0
 *   ⇒ 解析出来的是英文那份（"Skip it for now"）。本机 Robolectric 默认落 en-US 这件事
 *   在本仓库早有别处实测（`LbDialogVisualBaselineTest:52`、
 *   `FeedbackCasesScreenVisualBaselineTest:56`、`CaptureAppsScreenStatesTest:277`）。
 * 反向证人是同一台仪器上**绿的** `MemoryRefsFeedTest`：它的锚点全从 `app.getString(...)` 取，
 * 生产的渲染也走同一颗资源 ⇒ 红的原因只能是锚点没跟着搬。
 * 修法是**换锚点**（`WRONG_ACTION` 本来就声明了、只是九个调用点没接上；`MUTE_ACTION`
 * 按同一口径新加一颗），`assertEquals(1, …)` 一条没松、一格没删。
 *
 * 三种坏实现今天照样红，而且红在各自的理由上：
 * - 把菜单五项改回内联中文常量（中英对照门禁那条老毛病）⇒ 英文环境下这两颗锚点解析不到
 *   屏幕上的字，`openMenuAndPick` 数到 0；
 * - 少画一项 / 同一项画两颗 ⇒ 同一个 `assertEquals(1, …)` 数到 0 或 2；
 * - 把这五项从 DropdownMenu 里挪走、退回行上常驻四个大按钮 ⇒ 红的是
 *   `the per row entry is one named button and nothing else stays resident`（常驻可点数从 1 涨到 5），
 *   这一格与下面那几格浮层判据互不替代。
 *
 * 搬宿主之前本机量到（同一台仪器、360dp 宽挂载槽，`MemoryRefItem` 自己在行里画浮层）：
 * 「标记为错误」那颗标题落在 **y = 139–161dp**，也就是浮层只铺满那一行的高度，
 * 行以外那一片还在遮罩下面露着——`LbModalSheet` 的 `fillMaxSize()` 铺的是**它的父容器**。
 * 挂载槽这里刻意用 900dp 高：把"盖住一行"和"盖住整块面板"差成几百 dp，
 * 不靠猜阈值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryCorrectionFlowTest {

    private companion object {
        /** 旧形状留下的那颗无名字形，留作反向证人（不许改名回来） */
        const val OLD_ANONYMOUS_GLYPH = "⋯"
    }

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }
    private val density: Float get() = app.resources.displayMetrics.density

    /**
     * 每条参考旁那颗常驻入口的名字。生产侧唯一的主人是 `MemoryRefsFeed.kt` 里那次
     * `stringResource(R.string.memory_fix_entry)`，所以锚点也从同一份资源取
     * （本机 JVM 解析成英文：写死中文会恒红，写死英文又会把中文那份锁死）。
     */
    private val CORRECT_ENTRY: String get() = app.getString(R.string.memory_fix_entry)

    /** 菜单里第一种说法（「这条不对」），走资源；它打开的仍是原来那颗"标错"浮层 */
    private val WRONG_ACTION: String get() = app.getString(R.string.memory_action_wrong)

    /** 菜单里第二种说法（「暂时别提」），同样走资源；它打开的是那颗暂停时长浮层 */
    private val MUTE_ACTION: String get() = app.getString(R.string.memory_action_mute)

    /** 输入框的读屏名字：这一句**走资源**，锚点也就必须走资源（英文环境拿到的是英文那份） */
    private val wrongInputHint: String get() = app.getString(R.string.memory_wrong_input_hint)

    /**
     * 两颗浮层标题、取消/确认：生产已从内联中文搬进 `stringResource`（第6节第4条 / ），
     * 本机 Robolectric 默认落英文，锚点也就跟着从同一份资源取——这是**换锚点**不是放宽判据
     * （见文件头账本：菜单五项搬进资源后没换锚点那一次，8 条集体报实到 0）。
     */
    private val muteTitle: String get() = app.getString(R.string.memory_mute_sheet_title)
    private val markWrongTitle: String get() = app.getString(R.string.memory_mark_wrong_sheet_title)
    private val cancelLabel: String get() = app.getString(R.string.a11y_action_cancel)
    private val confirmLabel: String get() = app.getString(R.string.a11y_action_confirm)

    private val ref = MemoryRef(
        id = "mem-1", kbId = "kb", kind = MemoryKind.SCENE,
        text = "她这周主要在赶毕业设计", sourcePath = "scene/x.md"
    )

    private var muteCalls: Pair<String, MuteDuration>? = null
    private var wrongCalls: Pair<String, String>? = null

    /** 面板怎么拼，这里就怎么拼：行 + 顶层宿主（360x900dp 槽位） */
    private fun mount(withHost: Boolean = true) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                val flow = rememberMemoryCorrectionFlow()
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    Column {
                        MemoryRefItem(
                            ref = ref,
                            onCorrection = { _, _, _, _ -> },
                            onUndoCorrection = {},
                            correctionFlow = flow
                        )
                    }
                    if (withHost) {
                        MemoryCorrectionFlowHost(
                            flow = flow,
                            onMute = { id, d -> muteCalls = id to d },
                            onWrong = { id, text -> wrongCalls = id to text }
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /**
     * 点开这一行那颗「纠正」。
     *
     * 锚点是**带点击动作的文字「纠正」**，不再是旧那颗 `⋯`：这一轮把每条的无名字形换成了
     * 两个字。`and hasClickAction()` 不是装饰——"名字挂在字上、点击仍挂在里面那颗小盒子"
     * 这一族缺陷在本仓库被量过很多次，只按文案取节点就看不见它。
     * 恰好一颗也是判据：同一条参考上冒出两颗入口（新旧并存）照样红。
     */
    private fun openRowCorrectionMenu() {
        val found = rule.onAllNodes(hasText(CORRECT_ENTRY) and hasClickAction())
        val count = found.fetchSemanticsNodes().size
        assertEquals(
            "每条参考旁边该恰好有一颗「$CORRECT_ENTRY」文字入口，实到 $count 颗",
            1, count
        )
        // 点击要走在交互句柄上（fetchSemanticsNodes 交回来的是节点，不是可交互对象）
        found[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun openMenuAndPick(item: String) {
        openRowCorrectionMenu()
        val nodes = rule.onAllNodes(hasText(item)).fetchSemanticsNodes()
        assertEquals("菜单里「$item」应当恰好一个，实到 ${nodes.size}", 1, nodes.size)
        rule.onAllNodes(hasText(item))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun titleTop(label: String): Float? {
        val nodes = rule.onAllNodes(hasText(label)).fetchSemanticsNodes()
        return nodes.firstOrNull()?.boundsInRoot?.let { it.top / density }
    }

    /**
     * 入口形状本身：**菜单没展开时这一行只有一颗常驻可点的东西**，它就是文字「纠正」、
     * 报得出按钮角色；旧那颗只有字形的 `⋯` 不许以任何名字留在行上。
     *
     * 为什么单立一格：另外几格判的是浮层里交出去什么（id / 草稿 / 时长），那几种坏实现
     * 对它们都是瞎的——把四种原有动作铺成四个常驻大按钮（这一格数到 5）、把「纠正」写成
     * 不可点的纯文字（数到 0，或点击挂在别处）、改名成 `⋯`/「更多操作」继续堆同样几项
     * （文案与角色那条红）。
     */
    @Test
    fun `the per row entry is one named button and nothing else stays resident`() {
        mount()
        val resident = probe.actionableTargets(rule, "本轮参考那一行")
        assertEquals(
            "菜单没展开时这一行常驻的可点入口只能有一颗（那几种动作不该铺成常驻按钮），实到：" +
                resident.joinToString { it.describe() },
            1, resident.size
        )
        val entry = resident.single()
        assertEquals(
            "入口的名字就是「$CORRECT_ENTRY」两个字，读屏念得出用途：" + entry.describe(),
            CORRECT_ENTRY, entry.label
        )
        assertEquals("入口要报出按钮角色：" + entry.describe(), "Button", entry.role)

        val glyphs = rule.onAllNodes(hasText(OLD_ANONYMOUS_GLYPH)).fetchSemanticsNodes()
        assertEquals(
            "行上不该再有旧那颗「$OLD_ANONYMOUS_GLYPH」字形（改名回来也一样），实到 ${glyphs.size} 颗",
            0, glyphs.size
        )
        val menuDescription = app.getString(R.string.panel_result_menu)
        assertTrue(
            "也不该再有拿「$menuDescription」当名字的菜单触发器：" +
                resident.joinToString { it.describe() },
            resident.none { t -> t.contentDescriptions.any { it.contains(menuDescription, ignoreCase = true) } }
        )
    }

    /** 浮层不再由那一行渲染：宿主缺席时，点了入口也不该在树里冒出一颗遮罩 */
    @Test
    fun `the row no longer draws its own sheet`() {
        mount(withHost = false)
        openMenuAndPick(WRONG_ACTION)
        assertNull(
            "只挂行、不挂宿主时不该出现「标记为错误」——" +
                "行里还留着浮层的话，这条会红",
            titleTop(markWrongTitle)
        )
    }

    /** 标题应当落在整块槽位的中部（改之前实测 y=139–161，只盖住那一行） */
    @Test
    fun `the sheet is centered on the whole slot, not on the row`() {
        mount()
        openMenuAndPick(WRONG_ACTION)
        val top = titleTop(markWrongTitle)
        assertNotNull("宿主挂上之后就该看得到浮层", top)
        assertTrue(
            "遮罩要盖得住整块面板：标题的 y 应当在槽位中部（900dp 高 ⇒ 300–600dp 之间），实到 ${top}dp。" +
                "改之前同一台仪器量到的是 139dp（只画在一行里）",
            top!! in 300f..600f
        )
    }

    /**
     * 一次只允许一颗——**这条只能对着持有者量**。
     *
     * 第一版只写了界面路径（先"暂时别提"、取消、再"不对"），于是  探针
     * （`requestWrong` 不再顶掉前一颗）照样绿：那格从头到尾没让两颗**同时**存在过。
     * 而界面上也确实走不到那一步——第一颗的遮罩已经把槽位吞掉，行尾那颗入口点不到。
     * ⇒ 持有者的不变量就对着持有者测，别假装界面能构造它。
     */
    @Test
    fun `the holder keeps at most one request live`() {
        val flow = MemoryCorrectionFlow()
        flow.requestMute("mem-A")
        assertEquals("先要暂停时长", "mem-A", flow.muteTargetId)
        assertNull("不该有第二颗", flow.wrongTargetId)

        flow.requestWrong("mem-B")
        assertNull("第二颗进来要把前一颗顶掉，不是叠两层遮罩", flow.muteTargetId)
        assertEquals("mem-B", flow.wrongTargetId)
        assertEquals("换目标时草稿要清空", "", flow.wrongDraft)

        flow.editWrongDraft("  半截话  ")
        flow.dismiss()
        assertNull("dismiss 之后哪颗都不该在", flow.wrongTargetId)
        assertEquals("dismiss 也要清掉草稿", "", flow.wrongDraft)
    }

    /** 一次只允许一颗：第二颗请求进来时顶掉前一颗，而不是叠两层遮罩 */
    @Test
    fun `a second request replaces the first sheet instead of stacking two`() {
        mount()
        openMenuAndPick(MUTE_ACTION)
        assertNotNull("先看到暂停时长", titleTop(muteTitle))
        val stacked = rule.onAllNodes(
            SemanticsMatcher("文案含「标记为错误」") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { markWrongTitle in it.text } ?: false
            }
        ).fetchSemanticsNodes().size
        assertEquals("点了「暂时别提」之后不该同时把「标记为错误」也画出来，实到 $stacked 颗", 0, stacked)

        // 关掉之后再点另一项——两档同时存在是不允许的
        rule.onNodeWithText(cancelLabel).performClick()
        rule.mainClock.advanceTimeBy(16L)
        openMenuAndPick(WRONG_ACTION)
        assertNotNull("该看到标记为错误", titleTop(markWrongTitle))
        assertNull("同时不该还留着暂停时长", titleTop(muteTitle))
    }

    /**
     * 确认把 id 与**去掉首尾空白的**草稿交出去；交完浮层必须关闭（不留下"看起来还开着"的状态）。
     *
     * 输入框按它自己的名字点名（那句走资源），不靠"树里第几颗"猜位置：整棵子树里能设文本的
     * 节点只该有一颗，多一颗（比如给菜单再补一个输入框）或少一颗都要红。
     */
    @Test
    fun `confirming hands over the id and the draft then closes`() {
        mount()
        openMenuAndPick(WRONG_ACTION)
        val fields = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals("浮层里该有一颗输入框，实到 ${fields.size} 颗", 1, fields.size)
        val named = fields.map { probe.of(it) }
        assertTrue(
            "那颗输入框要说得出自己是干什么的（名字正是生产 `stringResource` 取的那一句），实到 " +
                named.joinToString { it.describe() },
            named.any { it.contentDescriptions.contains(wrongInputHint) }
        )
        rule.onAllNodes(hasSetTextAction())[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        rule.onAllNodes(hasSetTextAction())[0]
            .performTextInput("  她说明天再答，不是答应约会  ")
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithText(confirmLabel).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("应交出这条记忆 id 与去掉首尾空白的正文",
            "mem-1" to "她说明天再答，不是答应约会", wrongCalls)
        assertNull("交完之后浮层要关上", titleTop(markWrongTitle))
    }

    /**
     * 暂停时长那颗：**三档都点得到且各交各的时长**。
     *
     * 第一版写成"每一档重新 mount 一次"，直接被仪器拦下来
     * （`Cannot call setContent twice per test!`——本仓库那台仪器的硬约束）。
     * 现在同一个组合里循环：开菜单 → 选档 → 交完浮层自己关 → 再开下一轮。
     * 标签取生产那一颗 `durationLabel(duration)`：它由 enum 派生，测试不该再抄一遍中文。
     */
    @Test
    fun `the mute sheet offers every duration and reports the chosen one`() {
        assertEquals("枚举三档，界面就该三档", 3, MuteDuration.entries.size)
        mount()
        MuteDuration.entries.forEach { duration ->
            muteCalls = null
            openMenuAndPick(MUTE_ACTION)
            val label = durationLabel(duration)
            rule.onNodeWithText(label).performClick()
            rule.mainClock.advanceTimeBy(16L)
            assertEquals("点「$label」应交回对应时长", "mem-1" to duration, muteCalls)
            assertNull("交完要关闭", titleTop(muteTitle))
        }
    }

    /** 反空跑之一：浮层里每个可交互节点都**有名字** */
    @Test
    fun `every actionable node in the flow is labeled`() {
        mount()
        openMenuAndPick(WRONG_ACTION)
        val targets = probe.assertAllActionableLabeled(rule, "记忆纠正浮层")
        assertTrue("至少该量到取消与确认：" + targets.joinToString { it.describe() },
            targets.size >= 2)
    }

    /**
     * 反空跑之二：浮层里每个可交互节点都**点得到**（全站那一档下限）。
     *
     * 与上一格分开写，是因为  探针（把行尾那颗入口退回 28dp 热区）红的是**尺寸**；
     * 两件事挤在一格里，报错格名会说出一个错的理由。
     * 这一格也是那条老缺陷的守卫：入口以前是 `Box(size = 28.dp).clickable{}`，实量
     * **28x28dp**。换了文字「纠正」之后判据一寸没松：两轴仍按同一把尺量。
     */
    @Test
    fun `every actionable node in the flow meets the touch floor`() {
        mount()
        openMenuAndPick(WRONG_ACTION)
        probe.assertAllActionableMeetTouchFloor(rule, "记忆纠正浮层")
        // 行尾那颗入口单独点名：它是本轮从无名 28dp 字形换成文字档的那一颗
        val targets = probe.actionableTargets(rule, "记忆纠正浮层")
        val entries = targets.filter { it.label == CORRECT_ENTRY }
        assertEquals(
            "浮层开着时行上那颗「$CORRECT_ENTRY」入口仍要读得到，实到 " +
                entries.size + " 颗（全树读数：" + targets.joinToString { it.describe() } + "）",
            1, entries.size
        )
        val entry = entries.single()
        assertEquals("入口的热区下限", probe.floorDp, entry.heightDp, 0.6f)
        assertTrue(
            "入口两轴都得够下限（只垫高度等于没垫）：" + entry.describe(),
            !entry.tooSmall(probe.floorDp)
        )
    }

    /**
     * 上面只量了「不对」那颗。这一格回扫**同一菜单的另一条分支**：
     * 「暂停时长」那颗浮层里的三档，走的是 `CorrectionSubmenuItem`——
     * 一个和取消/确认完全不同的实现，尺寸自然也可能是另一个数。
     *
     * 局部收紧时必须回扫同一个资源的其它出口，否则"修好了 48dp"只对了一半的浮层成立。
     */
    @Test
    fun `the duration menu items meet the touch floor too`() {
        mount()
        openMenuAndPick(MUTE_ACTION)
        val targets = probe.actionableTargets(rule, "暂停时长浮层")
            .filter { it.label in MuteDuration.entries.map { d -> durationLabel(d) } }
        assertEquals("三档都该在树里：" + targets.joinToString { it.describe() }, 3, targets.size)
        targets.forEach { t ->
            assertTrue(
                "「${t.label}」这一档的热区应当 ≥48dp，实到 " + t.describe() +
                    "（上一格只量了「不对」那颗，没量这条分支）",
                !t.tooSmall(48f)
            )
        }
    }
}
