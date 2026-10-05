package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.viewmodel.HomeKnowledgePresence
import com.lovebrain.app.viewmodel.HomeProbeOutcome
import com.lovebrain.app.viewmodel.HomeProviderRef
import kotlinx.coroutines.CompletableDeferred
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
 * 首页重做之后的**结构**守卫：这一屏只剩三格，而且只有黄灯那一档说话。
 *
 * 合同（第7节第1条 / PRODUCT_SPEC 第4节）从上到下写死：
 * ① 一条小状态卡（左灯、右 ▶/■）② 仅黄色时下面那一行小字 ③ 2×2 四入口。
 * 删掉的：价值说明那句、大军师介绍卡、当前供应商详情行、服务设置段（捕获开关与授权出口）、
 * 使用统计那一行、反馈/关于两行、About 入口、任何 `LbSection` 页段标题。
 *
 * 判据全部读语义树（不读源码、不数中文）：整屏可点节点数、四格是不是同一颗组件、
 * 那一行黄字的存在性与内容、段落顺序、热区。每格都写明什么坏实现会把它压红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenStructureTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private val clicked: MutableState<List<String>> = mutableStateOf(emptyList())
    private val overlay: MutableState<Boolean> = mutableStateOf(true)

    /**
     * 夹具也走 `MutableState`（照 `HomeScaffoldFrameSemanticsTest` 那一颗）：`rule.setContent` 每个
     * 用例只许调一次，所以"红档→绿档→黄档"这种换态不能重挂第二棵树，只能像这里一样漂
     * `harnessHolder.value`，让 HomeScreen 观察到新 vm 后在同一格里原地重算。
     */
    private val harnessHolder: MutableState<HomeStatusHarness> = mutableStateOf(HomeStatusHarness())

    /**
     * @param simulateStart 把平台那条链（权限→启服务）演成"服务真的起来了"，
     *        否则首页点击之后服务仍然不在跑，灯只能黄着——两种都是要测的形状。
     */
    private fun mount(
        harness: HomeStatusHarness,
        matrix: UiMatrix = UiMatrix(360, heightDp = 1000),
        simulateStart: Boolean = false
    ) {
        harnessHolder.value = harness
        rule.setContent {
            matrix.RenderIn(LocalDensity.current.density) {
                HomeScreen(
                    homeStatus = harnessHolder.value.vm,
                    onStartService = {
                        clicked.value = clicked.value + "start"
                        if (simulateStart) harnessHolder.value.service.markRunning()
                    },
                    onNavigateFeedback = { clicked.value = clicked.value + "feedback" },
                    onNavigateProviders = { clicked.value = clicked.value + "providers" },
                    onNavigateCaptureApps = { clicked.value = clicked.value + "capture" },
                    onBack = { clicked.value = clicked.value + "back" },
                    overlayGrantedOverride = overlay.value
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tagCount(tag: String): Int =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun nodesWithDescription(desc: String) =
        rule.onAllNodes(hasContentDescription(desc)).fetchSemanticsNodes()

    /** 黄字那一行的文本：读语义树（`Target.label` 取的就是屏幕上那串），不读源码 */
    private fun hintLines(): List<String> =
        rule.onAllNodesWithTag(LbHomeTags.SETUP_HINT, useUnmergedTree = true)
            .fetchSemanticsNodes().map { probe.of(it).label }

    // ═══════════ ① 只剩三格：一卡 + 四入口，别的都不许在 ═══════════

    /**
     * 反例：
     * - 首页又长回第五格（统计行 / 关于行 / 服务设置段任一）⇒ 可点节点数立刻超过 5；
     * - 四入口里有一格不是共用那颗卡 ⇒ [LbTags.ACTION_CARD] 少于 4；
     * - 又给某一格补了页段标题或统计格 ⇒ SECTION / METRIC_CELL 不再是 0。
     */
    @Test
    fun `home is exactly one status card plus four entries and nothing else`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)

        assertEquals("状态卡只有一颗", 1, tagCount(LbHomeTags.STATUS_CARD))
        assertEquals("四入口必须是同一颗 LbActionCard 的四格", 4, tagCount(LbTags.ACTION_CARD))
        assertEquals("黄字那一行在绿档不存在", 0, tagCount(LbHomeTags.SETUP_HINT))
        assertEquals("首页不许再有页段标题", 0, tagCount(LbTags.SECTION))
        assertEquals("首页不许再有设置行", 0, tagCount(LbTags.SETTING_ROW))
        assertEquals("首页不许再有统计格", 0, tagCount(LbTags.METRIC_CELL))

        val targets = probe.actionableTargets(rule, "首页")
        assertEquals(
            "整屏可点节点必须是 5 颗（1 控件 + 4 入口），实到：" + targets.joinToString(" | ") { it.describe() },
            5, targets.size
        )
        assertEquals("这一屏不许有开关（捕获那颗住在子页）", 0, targets.count { it.isToggle })
    }

    // ═══════════ ② 只有黄灯那一档下面有那一行 ═══════════

    /**
     * 三档各摆一次：红/绿没有那一行，黄有，而且念的就是真实缺项。
     * 反例：黄字常驻（红档也解释）⇒ 第一句红；黄字与缺项脱钩（灯黄着却念上一档的话）⇒ 最后一句红；
     * 配置不齐却还发微请求 ⇒ 那句 calls 红。
     */
    @Test
    fun `only the yellow lamp gets the hint line and it says what is missing`() {
        val stopped = HomeStatusHarness()
        overlay.value = false
        mount(stopped)
        assertEquals("红档没有常驻解释", 0, tagCount(LbHomeTags.SETUP_HINT))

        val ready = HomeStatusHarness()
        ready.parkReady()
        // 换态不重挂树：在同一格里漂 harnessHolder/overlay，让 HomeScreen 原地重算
        rule.runOnIdle {
            harnessHolder.value = ready
            overlay.value = true
        }
        rule.waitForIdle()
        assertEquals("绿档也无多余解释", 0, tagCount(LbHomeTags.SETUP_HINT))

        val yellow = HomeStatusHarness()
        yellow.provider.ref = null
        yellow.service.markRunning()
        rule.runOnIdle { harnessHolder.value = yellow }
        rule.waitForIdle()
        assertEquals("黄档才有那一行", 1, tagCount(LbHomeTags.SETUP_HINT))
        // 那一行是**当前真缺项**的合集，不是上一档的话——这里 harness 只钉了 provider=null +
        // 服务在跑，overlay/知识库/连接三项从没跑过（playClicked 才是探针的唯一入口），
        // 所以生产派生的缺项里除「未配置模型供应商」还会带那三样。牙齿在两处：
        // ① 「黄档才有那一行」= 1（红/绿不得留常驻解释），② 这里必须点到供应商（脱钩就红），
        // ③「配置不齐时不发微请求」= 0 次探针。多缺项的排版那一族由
        // `several missing steps stay one line with a short separator` 单独钉，不重复。
        assertTrue(
            "那一行要念到未配置模型供应商（黄字与缺项脱钩的坏实现会在这里红），实到 ${hintLines()}",
            hintLines().single().contains("未配置模型供应商")
        )
        assertEquals("配置不齐时不发微请求（不白烧一次调用）", 0, yellow.probe.calls)
    }

    /**
     * 多个缺项 = 一行短分隔，不是几段说明。
     * 反例：只念第一条（后面的步骤用户永远看不见）⇒ 第二条红；
     * 拆成几段/几个节点（用户说的那个"写一堆"形状）⇒ "只有一行"与"含分隔"红。
     */
    @Test
    fun `several missing steps stay one line with a short separator`() {
        val harness = HomeStatusHarness()
        harness.knowledge.presence = HomeKnowledgePresence.Missing
        harness.probe.outcome = HomeProbeOutcome.Unreachable
        harness.service.markRunning()
        overlay.value = false
        mount(harness)
        // 用户点了开始（权限没给那一档平台链会停在授权页，这里只把状态机推起来）
        rule.runOnIdle { harness.vm.playClicked(overlayGranted = false) }
        rule.waitForIdle()

        val lines = hintLines()
        assertEquals("多条缺项也只有一行，实到 $lines", 1, lines.size)
        val text = lines.first()
        assertTrue("黄字里要有短分隔，实到「$text」", text.contains(" · "))
        assertTrue("权限那条排在最前（用户第一件事就是它）：「$text」", text.startsWith("需要悬浮窗权限"))
        assertTrue("要念到建库这一步：「$text」", text.contains("请为当前对象建立知识库"))
        assertTrue("要念到连接这一步：「$text」", text.contains("连接失败，请检查Key或地址"))
    }

    // ═══════════ ③ 四入口：位置、顺序、各归各的出口 ═══════════

    /**
     * 2×2 的摆法与顺序由合同点名：知识库 / 已踩案例 / 消息捕获 / 模型供应商。
     * 反例：某一格被换成没要的入口 ⇒ tag 少一颗；顺序调了 ⇒ 顶边比较红；
     * 某一格自己占一整行 ⇒ "同高"那句红。
     */
    @Test
    fun `the four entries sit in the contracted two by two order`() {
        val harness = HomeStatusHarness()
        mount(harness)

        fun top(tag: String): Float =
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top

        val knowledge = top(LbHomeTags.ENTRY_KNOWLEDGE)
        val feedback = top(LbHomeTags.ENTRY_FEEDBACK)
        val capture = top(LbHomeTags.ENTRY_CAPTURE)
        val provider = top(LbHomeTags.ENTRY_PROVIDER)
        val card = top(LbHomeTags.STATUS_CARD)

        assertTrue("第一排两格必须同高：知识 ${knowledge.toInt()} / 已踩 ${feedback.toInt()}",
            kotlin.math.abs(knowledge - feedback) < 1f)
        assertTrue("第二排两格必须同高：捕获 ${capture.toInt()} / 供应商 ${provider.toInt()}",
            kotlin.math.abs(capture - provider) < 1f)
        assertTrue("状态卡在最上面，入口跟在下面", card < knowledge && knowledge < capture)
    }

    /**
     * 每一格点下去走的是自己那一条出口，不共用、也不落空。
     * 反例：四格接同一个 lambda ⇒ sink 序列对不上；某一格没接上 ⇒ 少一项。
     * 知识库那一格走的是另一个 Activity（`startActivity`），不会进 sink，
     * 所以它的出口在 `the knowledge entry opens the kb screen` 那一格单独判。
     */
    @Test
    fun `each entry fires its own callback from its own node`() {
        val harness = HomeStatusHarness()
        mount(harness)

        rule.onAllNodesWithTag(LbHomeTags.ENTRY_FEEDBACK)[0].performClick()
        rule.onAllNodesWithTag(LbHomeTags.ENTRY_CAPTURE)[0].performClick()
        rule.onAllNodesWithTag(LbHomeTags.ENTRY_PROVIDER)[0].performClick()
        rule.waitForIdle()
        assertEquals(listOf("feedback", "capture", "providers"), clicked.value)
    }

    /**
     * 知识库那一格的出口是"打开知识库页"，不是面板、也不是空转。
     * 判法：Robolectric 把 `startActivity` 记在 shadow 里，取到的目标类就是 KnowledgeBaseActivity。
     * 反例：那一格被接到 `onOpenPanel`（旧版"今日锦囊"那个形状）⇒ 取不到 Activity。
     */
    @Test
    fun `the knowledge entry opens the kb screen`() {
        val harness = HomeStatusHarness()
        mount(harness)
        rule.onAllNodesWithTag(LbHomeTags.ENTRY_KNOWLEDGE)[0].performClick()
        rule.waitForIdle()

        // `Shadows.shadowOf` 在 Robolectric 4.14.1 里没有收 `Context` 的那一支（javap 过一遍重载名单：
        // 这一族只有 Application / ContextWrapper / Activity 这些档位），而 `nextStartedActivity()`
        // 住在 ShadowContextWrapper 上、读的就是这条**应用级**的启动队列——首页那一格走的正是
        // `context.startActivity(Intent(context, KnowledgeBaseActivity::class.java))`。
        // 所以这里点名要 Application 那一档，不换成"随便找个 Context 塞进去"。
        val application: android.app.Application = ApplicationProvider.getApplicationContext()
        val shadow = org.robolectric.Shadows.shadowOf(application)
        val started = shadow.nextStartedActivity
        assertTrue("知识库那一格必须真的开出一页（实到 $started）", started != null)
        assertEquals(
            "开出去的那一页是知识库管理页",
            "com.lovebrain.app.ui.KnowledgeBaseActivity",
            started?.component?.className
        )
    }

    /**
     * 状态卡里只有右边那颗可点：灯是读数，不是第二个按钮。
     * 反例：给灯也挂 clickable（"一颗变两颗可点"那个形状）⇒ 那句红；
     * 控件没挂 Button 角色或没名字 ⇒ 后两句红（读屏到了这一格只剩"未命名"）。
     */
    @Test
    fun `the lamp is a reading not a second button`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)

        assertEquals("灯要念得出这一档", 1, nodesWithDescription(HOME_LAMP_READY).size)
        val clickableLamps = rule.onAllNodes(hasClickAction() and hasContentDescription(HOME_LAMP_READY))
            .fetchSemanticsNodes()
        assertEquals("灯不可点：它是读数，多一处出口就是旧版那个形状", 0, clickableLamps.size)

        val control = nodesWithDescription(HOME_A11Y_STOP)
        assertEquals("绿档那颗控件自己说出'停止军师服务'", 1, control.size)
        val node = probe.of(control.first())
        assertEquals("控件要报角色", "Button", node.role)
        assertTrue(
            "控件的热区两轴都要够全站那一档，实到 " + node.describe(),
            !node.tooSmall(TouchTier.SITE_FLOOR)
        )
    }

    /**
     * 灯与控件在**同一行**：左灯右控件，卡片仍是那"一条"。
     * 反例：把灯做成独占一行的大介绍卡（用户说的那一屏"写一堆"）⇒ 高度那句红；
     * 控件跑到卡片下面一行 ⇒ 垂直中心那句红。
     */
    @Test
    fun `lamp and control share one row of a short card`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)

        val card = rule.onAllNodesWithTag(LbHomeTags.STATUS_CARD).fetchSemanticsNodes().first()
        val control = nodesWithDescription(HOME_A11Y_STOP).first()
        val lamp = nodesWithDescription(HOME_LAMP_READY).first()
        val cardHeightDp = card.boundsInRoot.height / density

        val centerY = (control.boundsInRoot.top + control.boundsInRoot.bottom) / 2f
        val lampY = (lamp.boundsInRoot.top + lamp.boundsInRoot.bottom) / 2f
        assertTrue(
            "灯与控件要同一行（灯心 ${lampY.toInt()} / 控件心 ${centerY.toInt()}）",
            kotlin.math.abs(centerY - lampY) < card.boundsInRoot.height / 2f
        )
        assertTrue("灯在左、控件在右", lamp.boundsInRoot.left < control.boundsInRoot.left)
        assertTrue("状态卡要还是那一条，实到高 ${cardHeightDp.toInt()}dp", cardHeightDp <= 80f)
    }

    // ═══════════ ④ 灯/控件的动作语义（ 的验收形状）═══════════

    /**
     * 点 ▶ 同时踩下平台那条链与状态机；服务真起来了才可能绿。
     * 反例：只发探针不启服务 ⇒ sink 少一项；只启服务不推状态机 ⇒ 状态还停在红；
     * 服务起来了但探针没跑 ⇒ calls 不是 1。
     */
    @Test
    fun `play presses the platform chain and the state machine together`() {
        val harness = HomeStatusHarness()
        harness.service.running = false
        mount(harness, simulateStart = true)
        assertEquals("没点之前是红", AdvisorState.Stopped, harness.vm.status.value.state)

        rule.onAllNodesWithTag(LbHomeTags.CONTROL)[0].performClick()
        rule.waitForIdle()

        assertEquals(listOf("start"), clicked.value)
        assertEquals("平台链 + 探针 + 服务在跑 = 绿", AdvisorState.RunningReady, harness.vm.status.value.state)
        assertEquals(1, harness.probe.calls)
    }

    /** 服务没起来时（平台那条链失败的那一档），灯黄着并且说出为什么 */
    @Test
    fun `play with the service failing to come up says so`() {
        val harness = HomeStatusHarness()
        harness.service.running = false
        mount(harness, simulateStart = false)
        rule.onAllNodesWithTag(LbHomeTags.CONTROL)[0].performClick()
        rule.waitForIdle()

        assertEquals(AdvisorState.RunningNeedsSetup, harness.vm.status.value.state)
        assertEquals(listOf("军师服务没起来"), hintLines())
    }

    /**
     * 点 ■：取消进行中的检查、停服务、灯落回红；**迟到的响应不能再把它点亮**。
     * 反例：stop 不推 token / 不置 userStarted ⇒ 后面回来的那一次把灯点到绿。
     */
    @Test
    fun `square cancels the in-flight check and a late response cannot relight it`() {
        val harness = HomeStatusHarness()
        harness.probe.gate = CompletableDeferred()
        mount(harness, simulateStart = true)
        assertEquals("没点之前是红、画的是三角", AdvisorState.Stopped, harness.vm.status.value.state)

        rule.onAllNodesWithTag(LbHomeTags.CONTROL)[0].performClick()   // ▶
        rule.waitForIdle()
        assertEquals(AdvisorState.Checking, harness.vm.status.value.state)

        rule.onAllNodesWithTag(LbHomeTags.CONTROL)[0].performClick()   // ■（形状已经换成方块）
        rule.waitForIdle()
        assertEquals(AdvisorState.Stopped, harness.vm.status.value.state)
        assertEquals("点方块要真的停服务", 1, harness.service.stopCount)

        harness.probe.gate!!.complete(Unit)
        rule.waitForIdle()
        assertEquals("关掉了才回来的响应不能把灯再点亮", AdvisorState.Stopped, harness.vm.status.value.state)
        assertEquals(AdvisorLamp.Red, harness.vm.status.value.render().lamp)
        assertEquals(1, harness.probe.calls)
    }

    /**
     * 组合与重组不发微请求（合同原话："不许每次重组发微请求"）。
     * 换格靠 hoisted 状态触发重组——同一次 `setContent` 里翻两次，别在同一个用例里挂第二棵树。
     * 反例：把检查写进 `LaunchedEffect`/每次读 flows ⇒ 第二次重组后 calls 变 2。
     */
    @Test
    fun `recomposing home does not spend another request`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        assertEquals("装配那一次只发一个请求", 1, harness.probe.calls)

        repeat(3) {
            rule.runOnIdle { overlay.value = (it % 2 == 0) }
            rule.waitForIdle()
        }
        assertEquals(1, harness.probe.calls)
        assertEquals(AdvisorState.RunningReady, harness.vm.status.value.state)
    }

    /**
     * 换供应商 = 旧检查作废，首页不许还挂着绿。
     * 反例：结论不绑身份（只记"上次成功过"）⇒ 这一格红，而用户看到的绿是假的。
     */
    @Test
    fun `switching provider drops the stale green on home`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        assertEquals(AdvisorState.RunningReady, harness.vm.status.value.state)

        rule.runOnIdle { harness.provider.ref = HomeProviderRef("t2", "New", "new-model", hasKey = true) }
        rule.runOnIdle { harness.service.pulseNow() }   // 只重读事实：不因为换人就自己发一次请求
        rule.waitForIdle()

        assertEquals(
            "新供应商没被验过，绿必须撤掉", AdvisorState.RunningNeedsSetup, harness.vm.status.value.state
        )
        assertEquals(AdvisorLamp.Yellow, harness.vm.status.value.render().lamp)
        assertEquals("作废本身不许花钱：请求数还是那一次", 1, harness.probe.calls)
        assertTrue("黄字要指到连接这一步，实到 ${hintLines()}", hintLines().any { it.contains("连接") })
    }

    // ═══════════ ⑤ 热区：量具 + 哨兵（四宽 × 三字号）═══════════

    /**
     * 十二格各量一次整屏可点节点的热区，并钉死那颗控件自己两轴都够 48dp。
     * 这一格是"可见 20dp 字形 vs 热区 48dp"那条三轴纪律的读数：
     * 反例：控件外包盒被版式改动带小 ⇒ 每格都断言的那句红（不是只打印）。
     */
    @Test
    fun `the control keeps its hit area at every matrix cell`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        val cell = mutableStateOf(UiMatrix.FULL.first())
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                HomeScreen(
                    homeStatus = harness.vm,
                    onStartService = {}, onNavigateFeedback = {}, onNavigateProviders = {},
                    onNavigateCaptureApps = {}, onBack = {}, overlayGrantedOverride = true
                )
            }
        }
        rule.waitForIdle()

        val seen = mutableListOf<String>()
        val reported = mutableListOf<String>()
        UiMatrix.FULL.forEach { matrix ->
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            val targets = probe.at(TouchTier.SITE_FLOOR).actionableTargets(rule, matrix.id)
            seen += matrix.id
            targets.filter { it.tooSmall(TouchTier.SITE_FLOOR) }.forEach { reported += "${matrix.id}：${it.describe()}" }
            val control = nodesWithDescription(HOME_A11Y_STOP).first()
            val node = probe.of(control)
            assertFalse(
                "${matrix.id}：▶/■ 那颗的热区两轴都要够 48dp，实到 " + node.describe(),
                node.tooSmall(TouchTier.SITE_FLOOR)
            )
        }
        assertEquals("十二格都要量到（循环没跑就等于没测）", UiMatrix.FULL.size, seen.distinct().size)
        // 入口卡是共用组件那一颗：它的小图标不单独可点，整卡一处操作 ⇒ 每格都只有 5 颗
        assertEquals("每一格的可点节点都是 5 颗", 5, probe.actionableTargets(rule, "末格").size)
        assertTrue("有可点节点掉到 48dp 热区以下：\n  $reported", reported.isEmpty())
    }
}
