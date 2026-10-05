package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.AppDimens
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
 * 首页重做之后的**结构**守卫：这一屏只剩三格，而且只有黄灯那一档说话、说了还给得出去处。
 *
 * 合同（第7节第1条 / PRODUCT_SPEC 第4节 + 基线 v1 §4 的首页裁决 candidate-a）从上到下写死：
 * ① 一条 hero 状态卡（左灯 + 屏幕上的状态名、右 ▶/■，`Xl` 24 / 内 16 / 浮起 2）
 * ② 仅黄色时下面那一行小字（有浅底容器、`bodySmall` 12，末尾一颗现有档的可点去处）
 * ③ 2×2 四入口（`Lg` 16 / 内 12 / 无阴影，空的副标题槽不占位）
 * 删掉的：价值说明那句、大军师介绍卡、当前供应商详情行、服务设置段（捕获开关与授权出口）、
 * 使用统计那一行、反馈/关于两行、About 入口、任何 `LbSection` 页段标题。
 *
 * 判据全部读语义树（不读源码、不数中文）：整屏可点节点数、四格是不是同一颗组件、
 * 那一行黄字的存在性与内容、段落顺序、热区、卡高的算式。只有"两档圆角/阴影"那一格按来路判
 * （阴影与圆角在树上读不出来，而"两档塌回一档"正是最难查的那种绿），它同样写明反例。
 * 每格都写明什么坏实现会把它压红。
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
     *
     * ⚠ 这里的 5 颗是**绿档**那一格（没有黄字就没有去处）。黄档多出来的那一颗是缺项行末尾的
     * 「去设置」，由下面 `the missing line comes with a way out that spends nothing` 单独判 6 颗，
     * 两格合起来才是"一格不多、但该有去处的那一格不许少"。
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
     *
     * 本轮改了一处**所有者**：灯自己不再挂 `contentDescription`，同一句状态名改由旁边那颗
     * **可见文字**说（`render.lampDescription`）。所以这一格的定位方式跟着换成 tag——
     * 判据本身一个字没放松：
     * - 反例：给灯也挂 clickable（"一颗变两颗可点"那个形状）⇒ `clickableLamps` 那句红；
     * - 反例：控件没挂 Button 角色或没名字 ⇒ 后两句红（读屏到了这一格只剩"未命名"）；
     * - 反例：灯旁边那格又变回一块空的 `Box`（没有可见状态名）⇒ `visibleNames` 那句红；
     * - 反例：名字在灯与文字上各声明一遍（读屏念两遍）⇒ 最后一句红。
     */
    @Test
    fun `the lamp is a reading not a second button`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)

        assertEquals("灯仍在树上（它是读数，不是被删掉的那一格）", 1, tagCount(LbHomeTags.LAMP))
        assertEquals("灯不可点：它是读数，多一处出口就是旧版那个形状", 0,
            rule.onAllNodes(hasClickAction() and hasTestTag(LbHomeTags.LAMP)).fetchSemanticsNodes().size)

        val visibleNames = listOf(HOME_LAMP_STOPPED, HOME_LAMP_CHECKING, HOME_LAMP_READY, HOME_LAMP_NEEDS_SETUP)
            .filter { rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(
            "这一档的状态名必须**写在屏幕上**，而且只有一行（灯旁那一格不再是空 Box）：实到 $visibleNames",
            listOf(HOME_LAMP_READY), visibleNames
        )
        assertEquals(
            "同一个名字不许在屏幕与读屏上各说一遍（念两遍）", 0,
            nodesWithDescription(HOME_LAMP_READY).size
        )

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
     * 灯、状态名与控件在**同一行**：卡片仍是"一条"，高度由三轴那两数算出来。
     *
     * 这一格同时钉 §3.1 三轴分离与 §3.4 hero 那一档：卡高 = 热区 48 + 上下各 16 = **80dp**，
     * 一个数都不许多叠一层（旧版四边不等 16/8/8/8，卡高 64dp；hero 改成等距 16 之后是 80dp）。
     * 反例：
     * - 把灯做成独占一行的大介绍卡（用户说的那一屏"写一堆"）⇒ 高度那句当场红；
     * - 控件跑到卡片下面一行 ⇒ 垂直中心那句红；
     * - 给卡内再叠一层 `padding` 或把热区垫成 56+ ⇒ 高度不等于 80±1，红。
     */
    @Test
    fun `lamp and control share one row of a short card`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)

        val card = rule.onAllNodesWithTag(LbHomeTags.STATUS_CARD).fetchSemanticsNodes().first()
        val control = nodesWithDescription(HOME_A11Y_STOP).first()
        val lamp = rule.onAllNodesWithTag(LbHomeTags.LAMP, useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val cardHeightDp = card.boundsInRoot.height / density
        // 三轴那笔账：可见热区那一档 + 四边等距的内距，只有这两项进高度
        val expectedHeight = AppDimens.TOUCH_TARGET_MIN_DP + 2 * 16f   // 48 + 16 + 16 = 80dp

        val centerY = (control.boundsInRoot.top + control.boundsInRoot.bottom) / 2f
        val lampY = (lamp.boundsInRoot.top + lamp.boundsInRoot.bottom) / 2f
        assertTrue(
            "灯与控件要同一行（灯心 ${lampY.toInt()} / 控件心 ${centerY.toInt()}）",
            kotlin.math.abs(centerY - lampY) < card.boundsInRoot.height / 2f
        )
        assertTrue("灯在左、控件在右", lamp.boundsInRoot.left < control.boundsInRoot.left)
        assertTrue(
            "状态卡的高必须是『热区 48 + 上下各 16』那一算式，不多叠一层：实到 ${cardHeightDp.toInt()}dp / 应为 ${expectedHeight.toInt()}dp",
            kotlin.math.abs(cardHeightDp - expectedHeight) <= 1f
        )
        assertTrue("状态卡要还是那一条，不许长回旧介绍卡那一块（实到高 ${cardHeightDp.toInt()}dp）", cardHeightDp <= 88f)
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

    // ═══════════ ⑥ 第 6 条要的"精简但有完成度"：去处、层级、六档可辨 ═══════════

    /**
     * 黄字那一行旁边必须有一颗**能点的去处**，而且那颗去处只是导航。
     *
     * 这一格买的是"不许只给一句黄警告让用户自己摸索"那一半，同时守住探针那一半：
     * - 反例：黄字仍是裸 `Text`（没有出口）⇒ `SETUP_ACTION` 那句红；
     * - 反例：把出口写成页面自画的 `Box(size(48)).clickable`（§3.5 禁的第四种动作写法）
     *   ⇒ 语义树里那颗仍然可点、tag 也还在，但 `LbTextAction` 的 32dp 可见胶囊档会不在，
     *   这一格的可点节点数与下面那句"许多缺项里没有去处"一起对不上；
     * - 反例：那颗去处顺手调了 `onPlay`/`playClicked`（"点一下先烧一次 token"）⇒ 最后一句红；
     * - 反例：给"服务没起来""连接还没检查过"那一族硬造一颗去处 ⇒
     *   `several missing steps stay one line with a short separator` 那一格（没有 NoProvider）
     *   会在 `SETUP_ACTION` 上红——那里期望的是 0 颗。
     */
    @Test
    fun `the missing line comes with a way out that spends nothing`() {
        val harness = HomeStatusHarness()
        harness.provider.ref = null
        harness.service.markRunning()
        mount(harness)
        rule.runOnIdle { harness.vm.playClicked(overlayGranted = true) }
        rule.waitForIdle()

        val lines = hintLines()
        assertEquals("缺项仍然只有一行，实到 $lines", 1, lines.size)
        assertTrue("要念到真正缺的那一条，实到 $lines", lines.single().contains("未配置模型供应商"))
        assertEquals("那一行旁边要有一颗去处", 1, tagCount(LbHomeTags.SETUP_ACTION))
        assertEquals(
            "可点节点：1 控件 + 4 入口 + 1 去处 = 6 颗",
            6, probe.actionableTargets(rule, "黄档·缺供应商").size
        )

        rule.onAllNodesWithTag(LbHomeTags.SETUP_ACTION)[0].performClick()
        rule.waitForIdle()
        assertEquals("那颗去处走的就是「模型供应商」那一格的出口", listOf("providers"), clicked.value)
        assertEquals("去设置只是导航，一次请求都不许多发", 0, harness.probe.calls)
        assertEquals("点完去处，那一行还在（没被点掉就消失的假状态）", 1, tagCount(LbHomeTags.SETUP_HINT))
    }

    /**
     * 六档状态在屏幕上要**各说各话**——不许只有灯色在差别（色觉不友好的用户读不出）。
     *
     * 指纹 = 屏幕上的状态名 + 那一行缺项原文 + 有没有去处 + 那颗控件说的是开始还是停止。
     * 灯色本身在语义树里读不出来，那一半由 `AdvisorStatusTest` 按"三灯=三个语义色"逐格钉
     * （它不是本文件的判据，这里不重复立一份）。
     *
     * 反例：
     * - 黄字常驻（红档也解释）⇒ 停止那一格的红指纹多出文字，`distinct` 还是 6 但
     *   `expected` 那一张对不上；
     * - 三档黄灯共用一句写死的话（"还差设置"三格念同一句）⇒ `distinct` 掉到 4；
     * - 状态名那一格又空了（屏幕上没有可读文字）⇒ 本格在 fingerprint 里当场抛"必须恰好画出一行"；
     * - 编一档"看起来正常"的假状态凑数 ⇒ `AdvisorStatus.render()` 那侧没有这一档，
     *   而这里六档全是从真实派生物跑出来的（夹具只摆事实，不摆文案）。
     */
    @Test
    fun `the six states are told apart on the tree`() {
        val stopped = HomeStatusHarness()
        mount(stopped)
        val seen = mutableListOf(fingerprint("停止"))

        val checking = HomeStatusHarness()
        checking.probe.gate = CompletableDeferred()
        checking.service.markRunning()
        rule.runOnIdle {
            harnessHolder.value = checking
            checking.vm.playClicked(overlayGranted = true)
        }
        rule.waitForIdle()
        seen += fingerprint("检查中")
        // 那颗 gate 是为"停在检查中"这一档摆的；收个尾，别留一条永远悬着的协程
        checking.probe.gate?.complete(Unit)

        val noProvider = HomeStatusHarness()
        noProvider.provider.ref = null
        noProvider.service.markRunning()
        rule.runOnIdle { harnessHolder.value = noProvider }
        rule.runOnIdle { noProvider.vm.playClicked(overlayGranted = true) }
        rule.waitForIdle()
        seen += fingerprint("未配置")

        val noOverlay = HomeStatusHarness()
        noOverlay.service.markRunning()
        rule.runOnIdle {
            overlay.value = false
            harnessHolder.value = noOverlay
            noOverlay.vm.playClicked(overlayGranted = false)
        }
        rule.waitForIdle()
        seen += fingerprint("已配置未授权")

        val kbUnread = HomeStatusHarness()
        kbUnread.knowledge.presence = HomeKnowledgePresence.Unknown
        kbUnread.service.markRunning()
        rule.runOnIdle {
            overlay.value = true
            harnessHolder.value = kbUnread
            kbUnread.vm.playClicked(overlayGranted = true)
        }
        rule.waitForIdle()
        seen += fingerprint("知识库读取中")

        val ready = HomeStatusHarness()
        rule.runOnIdle { harnessHolder.value = ready }
        rule.runOnIdle { ready.parkReady() }
        rule.waitForIdle()
        seen += fingerprint("运行正常")

        assertEquals("六档要各说各话：\n  " + seen.joinToString("\n  "), 6, seen.distinct().size)
        assertTrue("停止那一格要说『军师未启动』并且那颗控件说的是开始：${seen[0]}",
            seen[0].contains("军师未启动") && seen[0].contains("▶开始"))
        assertTrue("检查中那一格念『正在检查…』：${seen[1]}", seen[1].contains("正在检查…"))
        assertTrue("缺供应商那一格自带去处：${seen[2]}",
            seen[2].contains("未配置模型供应商") && seen[2].contains("去处=1"))
        assertTrue("未授权那一格念权限那一条、且没有假去处：${seen[3]}",
            seen[3].contains("需要悬浮窗权限") && seen[3].contains("去处=0"))
        assertTrue("知识库读不到那一格不说成『没有』也不说成『有』：${seen[4]}",
            seen[4].contains("还没读到当前对象的知识库"))
        assertTrue("运行正常那一格无常驻解释、说的是停止：${seen[5]}",
            seen[5].contains("军师已就绪") && seen[5].contains("无") && seen[5].contains("■停止"))
    }

    /**
     * hero 与入口卡是**两档**（§3.4 描边管静息、阴影管浮起 + §3.3 卡内 16→12）。
     *
     * 这一格按来路判（读两颗文件的源码，注释先掩平）+ 按几何判（量入口卡的高）：
     * 阴影与圆角在语义树上读不出来，而"两档塌回一档"恰恰是那种绿得最难查的坏实现。
     * 反例：
     * - 两颗都写 `LoveBrainShape.lg`（层级塌了）⇒ hero 那两句红；
     * - hero 自己再叠一条 `.border(`（shadow + border 双叠）⇒ 那句反向断言红；
     * - 入口卡被顺手加上 `ELEVATION_DEFAULT` ⇒ 零阴影那句红；
     * - `GridCell` 的留白回到 16 ⇒ 档位表那句红；而 `Standalone` 若被一起改小（别的页面哪天要用）
     *   也当场红；
     * - 空的副标题槽又画回一颗 `Text` ⇒ 几何那四句一起红（卡高从 94dp 涨到 110dp）。
     */
    @Test
    fun `hero and entry cards are two tiers and the empty subtitle slot is gone`() {
        val hero = maskedCode("ui/home/HomeComponents.kt")
        val entry = maskedCode("core/designsystem/LbActionCard.kt")

        // ── hero：Xl 24 + 内 16 等距 + 浮起那一档，且不再自己描边
        assertTrue("hero 圆角没上 Xl 24", "shape = LoveBrainShape.xl," in hero)
        assertTrue("hero 没浮到 ELEVATION_DEFAULT 那一档", "defaultElevation = AppDimens.ELEVATION_DEFAULT_DP.dp" in hero)
        assertTrue("hero 内距不是四边等距 16", "padding(Spacing.xl)" in hero)
        assertTrue("hero 又回到四边不等（start/end/top/bottom 各写一个数）",
            !Regex("padding\\(\\s*start =").containsMatchIn(hero))
        assertTrue("hero 上 shadow + border 双叠（§3.4 明禁）", !(".border(" in hero))

        // ── 入口卡：Lg 16 + 内 12 + 零阴影（描边管静息）
        assertTrue("入口卡圆角不是 Lg 16", "shape = LoveBrainShape.lg" in entry)
        assertTrue("入口卡没显式写零阴影", "cardElevation(defaultElevation = 0.dp)" in entry)
        assertTrue("入口卡没留 1dp 描边", ".border(AppDimens.BORDER_WIDTH_DP.dp" in entry)
        assertTrue("GridCell 那一档的卡内留白没收到 12", "GridCell -> Spacing.lg" in entry)
        assertTrue("Standalone 那一档被首页顺手带小了（它今天 0 处调用，数值该留在 16）",
            "Standalone -> Spacing.xl" in entry)
        // 差值要在场：两颗同档就等于零层级
        assertTrue("hero 与入口卡用了同一档圆角，层级又塌回一格", "LoveBrainShape.xl" !in entry)

        // ── 几何那一半：算式**按现在的真实形状**重列（H1b 台账写的 98dp 是它自己算错的一格——
        //    方块与标题之间那道间距在生产里是 `Spacing.md`(8dp)，`LbActionCard.kt:159` 本轮
        //    一个字没动；台账把那一格当成了 `Spacing.lg`(12)）。
        //    真算式 = 内距12(`GridCell.contentPadding`) + 方块40(`GridCell.blockSize`)
        //             + 间距8(`Spacing.md`) + 标题22(`titleMedium.lineHeight`) + 内距12 = **94dp**。
        //    锚点那一侧不吃尺寸：`.coachAnchor(...)` 只有 `onGloballyPositioned`，体里没有尺寸字面量。
        //    空的副标题槽画回来 ⇒ 卡高 94 + 间距2(`Spacing.xs`) + 副标题行高14 = 110dp，
        //    下面四句一起红（高度、卡顶到标题的 60dp、标题下沿只剩 12dp 内距、标题行高 22dp）。
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        val card = rule.onAllNodesWithTag(LbHomeTags.ENTRY_KNOWLEDGE, useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val titles = rule.onAllNodes(hasText(HOME_ENTRY_KNOWLEDGE), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("标题这一行只许画出一颗字（副标题槽是空的，不许有第二行）：实到 ${titles.size}颗", 1, titles.size)
        val title = titles.first().boundsInRoot
        val cardBox = card.boundsInRoot
        fun toDp(px: Float): Float = px / density

        val heightDp = toDp(cardBox.height)
        val expected = 12f + 40f + 8f + 22f + 12f
        assertTrue(
            "入口卡高应正好是「内距12 + 方块40 + 间距8 + 标题22 + 内距12」= ${expected.toInt()}dp；" +
                "空副标题槽画回来会涨到 110dp（94+2+14）。实到 ${heightDp.toInt()}dp",
            kotlin.math.abs(heightDp - expected) <= 2f
        )
        // 卡顶→标题顶 = 内距12 + 方块40 + 间距8 = 60dp：方块那一档与它下面那道间距都在这里结账
        assertEquals(
            "卡顶到标题那一格之间该是「内距12 + 方块40 + 间距8」= 60dp",
            60f, toDp(title.top - cardBox.top), 1f
        )
        // 标题下沿到卡底只剩内距 12 ⇒ 标题就是最后一格，底下没占位的空槽
        assertEquals(
            "标题下面只剩 12dp 内距（空副标题槽回来会多出「间距2 + 行高14」）",
            12f, toDp(cardBox.bottom - title.bottom), 1f
        )
        assertEquals("标题那一行占的就是 titleMedium 的 22dp 行高", 22f, toDp(title.height), 1f)
    }

    // ═══════════ 本文件自己的两把小尺 ═══════════

    /**
     * 一屏当前状态的**可读指纹**：屏幕上画出的状态名 + 那一行缺项原文 + 去处颗数 + 控件说的是谁。
     *
     * 这里刻意去读**屏幕上那串字**（`hasText`），而不是读 `render()` 的产物：
     * 判据要的是"用户看得见这一档是谁"，派生物自己说什么不算数（引用生产常量当期望值
     * 等于让被测对象替测试出题，与 `HomeHeroActionTest` 那条纪律同一课）。
     */
    private fun fingerprint(where: String): String {
        val drawn = listOf(HOME_LAMP_STOPPED, HOME_LAMP_CHECKING, HOME_LAMP_READY, HOME_LAMP_NEEDS_SETUP)
            .filter { rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("$where 这一档：状态名必须恰好画出一行（灯旁那一格不许空、也不许两行）：实到 $drawn",
            1, drawn.size)
        val hints = hintLines()
        assertTrue("$where 那一行要么不存在、要么只有一行：实到 $hints", hints.size <= 1)
        val control = if (nodesWithDescription(HOME_A11Y_PLAY).isNotEmpty()) "▶开始" else "■停止"
        return "${drawn.single()} | ${hints.singleOrNull() ?: "无"} | 去处=${tagCount(LbHomeTags.SETUP_ACTION)} | $control"
    }

    /**
     * 按来路判时读源码：**注释先掩平**（`SourceScan.maskComments`）再把空白压成单空格。
     *
     * 掩注释这件事不是讲究：这些文件的说明里就写着 `LoveBrainShape.lg`、`.border(` 这些形状，
     * 不掩的尺会拿说明书当生产代码数（`UiStringLiteralBudgetTest` 那一格记的正是这一坑）。
     * 压空白是为了让"跨行写的那条链"还能被一句 `in` 读到，不改变判据的形状。
     */
    private fun maskedCode(relative: String): String {
        val root = java.io.File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: java.io.File("app/src/main/java/com/lovebrain/app")
        val file = java.io.File(root, relative)
        assertTrue("找不到 $file——这把尺接错了目录，恒绿不算数", file.isFile)
        return com.lovebrain.app.core.testing.SourceScan
            .maskComments(file.readText(Charsets.UTF_8))
            .replace(Regex("\\s+"), " ")
    }
}
