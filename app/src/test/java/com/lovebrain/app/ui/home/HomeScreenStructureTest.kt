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
import com.lovebrain.app.R
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页重做之后的**结构**守卫：这一屏只剩四格，而且只有黄灯那一档说话、说了还给得出去处。
 *
 * 合同（第7节第1条 / PRODUCT_SPEC 第4节 + 基线 v1 §4 的首页裁决 candidate-a），
 * 再叠 M09 §5 与 M25 §五 取舍③（2026-10-10 新指导书）两条新决定，从上到下写死：
 * ① 一条 hero 状态卡（左灯 + 屏幕上的状态名、右 ▶/■，`Xl` 24 / 内 16 / `PrimaryLight` 浅底
 *    + `PrimarySubtle` 细描边 / **零阴影**——M09 §5 第 1 条把旧版那一级浅蓝层次买了回来，
 *    而"描边与阴影二选一"仍按 §3.4；启停那颗本体是白底 + 一圈 `Primary` 描边，第 2 条）
 * ② 仅黄色时下面那一行小字（有浅底容器、`bodySmall` 12，末尾一颗现有档的可点去处）
 * ③ 2×2 四入口（`Lg` 16 / 内 12 / 无阴影，每格一句 5–7 字的辅助描述——M09 §5 第 4 条）
 * ④ **一块累计使用小卡**（设计系统 `LbMetricGrid` 的 Card 档、四格读数、**不可点**、排在四入口之后）：
 *    这一格的存在本身就是 M25 §五 取舍③ 那句新决定——用户原话「首页可以重新加入累计使用面板……
 *    维持一块简洁的小卡，不恢复大而复杂的仪表盘」，它**替代**上一轮那条"首页不许再有统计格"的旧规。
 *    被替代的只是"不许有"，**上限是新决定自带的**，所以这里钉的是具体形状而不是"存在即可"：
 *    小卡恰 1 块、统计格恰 4 颗、整屏可点仍 5 颗（统计不是第五颗按钮）；数字来路与"不许写死"
 *    逐颗由 [HomeUsageCardTest] 判，本格只判结构与规模。
 * 删掉的：价值说明那句、大军师介绍卡、当前供应商详情行、服务设置段（捕获开关与授权出口）、
 * 反馈/关于两行、About 入口、任何 `LbSection` 页段标题。
 * （"使用统计"那一行上一轮随旧规一起删过，本轮由 §五 取舍③ 以④那一块**小卡**的形状请回来；
 *   请回来的只有那一块小卡——页段标题、旧统计行的其余部分与版本区/内部指标格仍按上面的名单不在场。）
 *
 * 判据全部读语义树（不读源码、不数中文）：整屏可点节点数、四格是不是同一颗组件、
 * 那一行黄字的存在性与内容、段落顺序、热区、卡高的算式。只有"两档颜色/描边/圆角"那一格按来路判
 * （色、边线与圆角在树上读不出来，而"两档塌回一档"正是最难查的那种绿），它同样写明反例
 * 与三把旧形状尺各自的替代关系。
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

    // ═══════════ ① 只剩"一卡 + 四入口 + 一块只读统计小卡"，别的都不许在 ═══════════

    /**
     * 反例：
     * - 首页又长回第五颗**可点**格（关于行 / 服务设置段 / 把统计小卡做成按钮）⇒ 可点节点数立刻超过 5；
     * - 四入口里有一格不是共用那颗卡 ⇒ [LbTags.ACTION_CARD] 少于 4；
     * - 又给某一格补了页段标题 ⇒ SECTION 不再是 0；设置行不再是 0。
     *
     * ⚠ 这里的 5 颗是**绿档**那一格（没有黄字就没有去处）。黄档多出来的那一颗是缺项行末尾的
     * 「去设置」，由下面 `the missing line comes with a way out that spends nothing` 单独判 6 颗，
     * 两格合起来才是"一格不多、但该有去处的那一格不许少"。
     *
     * ## M25 §五 取舍③ 的替代关系（旧判据钉的是什么 → 新判据钉的是什么）
     *
     * 旧规（`requests.md` §5「不增加无业务价值的统计仪表盘」+ 上一轮 K22「不恢复内部统计」）把这一格钉成
     * `assertEquals("首页不许再有统计格", 0, tagCount(LbTags.METRIC_CELL))`。2026-10-10 新指导书
     * §五 取舍③（活台账 M25）明确**替代**那条旧规："首页可以重新加入累计使用面板……维持一块简洁的小卡"。
     * ⇒ 那条 0 现在红的是"这块小卡没画回来"，方向反了。改钉成**具体形状**、不松成"存在即可"：
     * - 恰好**一块**统计小卡（`USAGE_CARD` 一颗，不许长出第二张统计卡）；
     * - 这块小卡**只有四格**读数（`METRIC_CELL` 恰 4——生成/复制/采纳/花费；多一格就是"又大又复杂"那版，红）；
     * - 这块小卡**不可点**（可点节点仍是 5 颗，统计没被做成第五颗按钮，也没并进主卡）。
     * 数字来源与"不许写死"由 `HomeUsageCardTest` 逐颗钉，本格只钉规模与"非可点"这两件结构事实。
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
        // M25 取舍③替代旧规（见上面替代关系）：旧钉 0 颗统计格，新钉"一块小卡、四格读数"。
        assertEquals("累计使用只许一块小卡（M25 取舍③：一块简洁小卡，不许长出第二张统计卡）",
            1, tagCount(LbHomeTags.USAGE_CARD))
        assertEquals("那一块小卡只许四格读数（生成/复制/采纳/花费；多一格就回到'大而复杂的仪表盘'）",
            4, tagCount(LbTags.METRIC_CELL))

        val targets = probe.actionableTargets(rule, "首页")
        assertEquals(
            "整屏可点节点必须是 5 颗（1 控件 + 4 入口；统计小卡只读、不是第五颗按钮），实到：" +
                targets.joinToString(" | ") { it.describe() },
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
     * 这一格同时钉 §3.1 三轴分离与 hero 那一档的内距：卡高 = 热区 48 + 上下各 16 = **80dp**，
     * 一个数都不许多叠一层（旧版四边不等 16/8/8/8，卡高 64dp；hero 改成等距 16 之后是 80dp）。
     * 反例：
     * - 把灯做成独占一行的大介绍卡（用户说的那一屏"写一堆"）⇒ 高度那句当场红；
     * - 控件跑到卡片下面一行 ⇒ 垂直中心那句红；
     * - 给卡内再叠一层 `padding` 或把热区垫成 56+ ⇒ 高度不等于 80±1，红；
     * - M09 §5 第 2 条给启停那颗补上"白底 + 一圈描边"的**按钮本体**之后，本体只到热区那一档：
     *   有人把本体写成 56dp、或为了"更显眼"在盒子里再垫一道 `padding` ⇒ 同一句 80±1 红
     *   （买的是"看得出来是一颗按钮"，不是把可见那一格撑大——那一半由 `HomeHeroActionTest` ≤56dp 钉）。
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

    /** 按下去之后新出现的读数：这一格判的是"那颗去处真的走得到自己那一格" */
    private data class WayOut(
        val word: String,
        val newClicks: List<String>,
        val startedActivity: String?,
        val extraRequests: Int,
        val extraReads: Int
    )

    /**
     * 指导书 §5.2 末段 + §2.2 第 5 条：被点名的那**五条**缺项，每一条在屏幕上都给得出**一颗**下一步，
     * 而且那颗按下去走到的是自己那一格——"组件存在"不等于"用户能用"，这里判的是按下去的读数。
     *
     * 改之前只有"缺供应商"那一格有出口，权限 / 没有库 / 读不到三格是一句只有描述没有动作的黄字
     * （§2.2 第 5 条点名的就是这个产品问题）。
     *
     * 五格共用一棵树（`setContent` 一个用例只许一次），换档靠漂 `harnessHolder` / `overlay`；
     * 每格各记自己那一份读数：导航 sink 的增量、开出去的 Activity、探针增量、知识库重读增量。
     *
     * 反例：
     * - 某一格只画字不给出口 ⇒ 那一格 `去处` 那句红（实到 0 颗）；
     * - 某一格跳错了门（权限跳到供应商、无库跳到捕获）⇒ 那一格的 sink / Activity 红；
     * - 「去授权」顺手把探针也发了（替用户花钱）⇒ 那一格 `extraRequests` 红；
     * - 「重试读库」被写成"再发一次请求"⇒ 同一句红；反过来它谁也没重读 ⇒ `extraReads` 红；
     * - 本次失败那一格不给重试（用户只能先 ■ 再 ▶）⇒ 那一格 `去处` 红；重试发两次 ⇒ `extraRequests` 红；
     * - 五格共用一句万能的「去设置」⇒ 三处 `assertNotEquals(settingsWord, …)` 红。
     */
    @Test
    fun `each named missing step offers its own executable next step`() {
        val application: android.app.Application = ApplicationProvider.getApplicationContext()
        val shadow = org.robolectric.Shadows.shadowOf(application)
        val settingsWord = app.getString(R.string.provider_open_settings)
        mount(HomeStatusHarness())   // 起点：红档那一格，树上还没有那一行

        /** 换到某一档缺项 → 数那颗去处 → 按下去 → 交回"按下去之后新出现的读数" */
        fun step(granted: Boolean, expectHint: String, setup: HomeStatusHarness.() -> Unit): WayOut {
            val harness = HomeStatusHarness()
            harness.service.markRunning()
            harness.setup()
            val clicksBefore = clicked.value.size
            rule.runOnIdle {
                overlay.value = granted
                harnessHolder.value = harness
                harness.vm.playClicked(overlayGranted = granted)
            }
            rule.waitForIdle()
            val hints = hintLines()
            assertEquals("那一行永远只有一行，实到 $hints", 1, hints.size)
            assertTrue("$expectHint：那一行要念到它，实到 ${hints.single()}", hints.single().contains(expectHint))
            val capsules = rule.onAllNodesWithTag(LbHomeTags.SETUP_ACTION, useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertEquals("$expectHint：那一行末尾至多一颗去处，实到 ${capsules.size}", 1, capsules.size)
            val word = probe.of(capsules.first()).label
            val callsBefore = harness.probe.calls
            val readsBefore = harness.knowledge.reads
            rule.onAllNodesWithTag(LbHomeTags.SETUP_ACTION)[0].performClick()
            rule.waitForIdle()
            return WayOut(
                word = word,
                newClicks = clicked.value.drop(clicksBefore),
                startedActivity = shadow.nextStartedActivity?.component?.className,
                extraRequests = harness.probe.calls - callsBefore,
                extraReads = harness.knowledge.reads - readsBefore
            )
        }

        val noProvider = step(granted = true, expectHint = "未配置模型供应商") { provider.ref = null }
        assertEquals("缺供应商那颗走的就是「模型供应商」那一格的出口", listOf("providers"), noProvider.newClicks)
        assertEquals("去设置只是导航，一次请求都不许多发", 0, noProvider.extraRequests)
        assertNull("导航到站内那一格不需要开 Activity", noProvider.startedActivity)

        val noOverlay = step(granted = false, expectHint = "需要悬浮窗权限") {}
        assertEquals("缺权限那颗走的是宿主那条平台链（只有它认得系统授权页与回来续跑）",
            listOf("start"), noOverlay.newClicks)
        assertEquals("授权这一颗不许顺手替用户发请求", 0, noOverlay.extraRequests)
        assertNotEquals("权限那一格的去处不是万能的『去设置』", settingsWord, noOverlay.word)

        val noKb = step(granted = true, expectHint = "请为当前对象建立知识库") {
            knowledge.presence = HomeKnowledgePresence.Missing
        }
        assertEquals("没有库那颗开出去的是知识库管理页（§5.2：知识库缺失去管理）",
            "com.lovebrain.app.ui.KnowledgeBaseActivity", noKb.startedActivity)
        assertEquals("开一页本身不发请求", 0, noKb.extraRequests)
        assertNotEquals("库那一格的去处不是万能的『去设置』", settingsWord, noKb.word)

        val unread = step(granted = true, expectHint = "还没读到当前对象的知识库") {
            knowledge.presence = HomeKnowledgePresence.Unknown
        }
        assertTrue("读不到那一颗要真的重读一次本地事实（增量 ${unread.extraReads}）", unread.extraReads >= 1)
        assertEquals("重读事实不等于再花一次钱（探针增量）", 0, unread.extraRequests)
        assertEquals("重读之后还是读不到就还是那一行，不许伪装成有库", 1, tagCount(LbHomeTags.SETUP_HINT))

        val failed = step(granted = true, expectHint = "连接失败") {
            probe.outcome = HomeProbeOutcome.Unreachable
        }
        assertEquals("本次请求失败的重试就挂在那颗请求的位置：再发**一次**", 1, failed.extraRequests)
        assertEquals("重试那颗不是导航，不该跳页", emptyList<String>(), failed.newClicks)
        assertTrue("失败那一格仍然说实话（旧结论没被抹成『还在检查』）：" + hintLines(),
            hintLines().single().contains("连接失败"))
    }

    // ═══════════ ⑥b §5.1 的四格构成与"主卡是焦点" ═══════════

    /**
     * §5.1 第一格「应用标识与必要标题」在场，而且它是**第一格**：
     * 页头交回设计系统那一颗（`LbTopBarLevel.Identity` 的注释点名的就是首页），页面不自画第二套行高。
     *
     * 反例：
     * - 标识那一格又没了（本轮改之前的形状：整屏从状态卡开始）⇒ 前两句红；
     * - 有人拿它当第五颗按钮（比如给标识加个"设置"入口）⇒ 最后一句可点节点数红；
     * - 标识跑到状态卡下面 ⇒ "在状态卡之上"那句红。
     */
    @Test
    fun `the app identity is the first block and adds no actionable node`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        assertEquals("应用标识那一格只有一颗", 1, tagCount(LbHomeTags.IDENTITY))
        val identityTop = rule.onAllNodesWithTag(LbHomeTags.IDENTITY, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.top
        val cardTop = rule.onAllNodesWithTag(LbHomeTags.STATUS_CARD, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.top
        assertTrue("标识要在状态卡之上（实到 标识 ${identityTop.toInt()}px / 主卡 ${cardTop.toInt()}px）", identityTop < cardTop)
        assertEquals(
            "标识只是标题，不是第五格可点：整屏仍是 1 控件 + 4 入口",
            5, probe.actionableTargets(rule, "首页·带标识").size
        )
    }

    /**
     * §5.1「主卡是视觉焦点，四入口整齐但不能与主卡同样抢眼」——这一条本轮按**字面量纲**判：
     * 改之前主卡那行状态名与入口卡那行标题是同一个 style 同一个字重（`titleMedium` + SemiBold），
     * 层级只剩阴影在扛，录下来的那张基线一眼读不出谁是主。
     *
     * 两个数都从**同一棵树本次量**（不写死 dp、也不读源码里的 style 名）：
     * 反例：有人把主卡那档改回与入口同阶 ⇒ 那句红；
     * 反例：有人靠"全局缩入口的字"买层级（§4.2 明禁）⇒ 入口标题那一行高会掉到本文件另一格
     *       `the hero card is light blue and the four entries carry their subtitle` 算式之外，那里先红。
     */
    @Test
    fun `the hero line out-types the entry titles`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        fun textHeightOf(text: String): Float =
            rule.onAllNodes(hasText(text), useUnmergedTree = true)
                .fetchSemanticsNodes().first().boundsInRoot.height / density
        val hero = textHeightOf(HOME_LAMP_READY)
        val entry = textHeightOf(HOME_ENTRY_KNOWLEDGE)
        assertTrue(
            "主卡那一行要比入口标题大一级（实到 主卡 ${hero.toInt()}dp / 入口 ${entry.toInt()}dp）：" +
                "两档同字阶就是 §5.1 说的「入口与主卡同样抢眼」",
            hero > entry
        )
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
        assertTrue("未授权那一格念权限那一条，并且那颗去处**是授权那一格的**（去处=1）：${seen[3]}",
            seen[3].contains("需要悬浮窗权限") && seen[3].contains("去处=1"))
        assertTrue("知识库读不到那一格不说成『没有』也不说成『有』：${seen[4]}",
            seen[4].contains("还没读到当前对象的知识库"))
        assertTrue("运行正常那一格无常驻解释、说的是停止：${seen[5]}",
            seen[5].contains("军师已就绪") && seen[5].contains("无") && seen[5].contains("■停止"))
    }

    /**
     * hero 与入口卡是**两档**（M09 §5 第 1/4 条把形状重立了一次）。
     *
     * 这一格按来路判（读两颗文件的源码，注释先掩平）+ 按几何判（量入口卡的高）：
     * 颜色、描边与高度在语义树上读不出来，而"两档塌回一档"恰恰是那种绿得最难查的坏实现。
     *
     * ## 三把尺各自的替代关系（旧格钉的是什么 → 新格钉的是什么）
     *
     * ① **hero 浮起那一档 → hero 浅蓝那一档**。
     *    旧格钉 `"defaultElevation = AppDimens.ELEVATION_DEFAULT_DP.dp" in hero`（阴影 2 是主卡唯一
     *    的层次手段）。M09 要的是旧版那一级**浅蓝层次**，而第 1 条同时明令"不要同时堆满
     *    阴影、描边和渐变"⇒ 这一格选了描边：新格钉
     *    `containerColor = PrimaryLight` + `border(BORDER_WIDTH_DP, PrimarySubtle, Xl)`
     *    + `defaultElevation = 0.dp`，**等值判据没有松成"存在即可"**（三颗令牌名与数都逐字钉），
     *    并且反过来钉 `"ELEVATION_DEFAULT_DP" !in hero`。
     * ② **禁 `.border(` → 禁"阴影与描边双叠"，并逐颗认出那两圈描边各自的主人**。
     *    旧格那句 `!(".border(" in hero)` 拦的是 shadow + border 双叠（§3.4）。需求现在要旧版那圈
     *    边线回来，所以"一颗都不许有"这条已经站不住；改成钉**具体形状**：主卡那一圈必须是
     *    `PrimarySubtle` + `Xl`，启停那颗的本体边线必须是 `Primary` + `Md`，
     *    同时钉住"阴影退到 0"与"渐变不许再叠第三层"。坏实现照样红：
     *    给主卡换回阴影（`ELEVATION_DEFAULT_DP` 又出现）⇒ ①那句红；
     *    把边线涂成 `Border` 灰、或圆角借入口那一档 `Lg` ⇒ ②的整串实参对不上；
     *    给主卡再加一层 `Brush.linearGradient` ⇒ 三样堆满那句红。
     * ③ **入口卡高 94dp（空副标题槽不占位）→ 110dp（副标题真画出来）**。
     *    旧格钉的是"空的副标题槽不许画回一颗 `Text`"，算式 12+40+8+22+12=94；
     *    M09 §5 第 4 条要的是**每格带一句简短辅助描述**，所以那一行现在是内容不是空槽，
     *    算式重列为 12+40+8+22+2+14+12=**110**（副标题= `Spacing.xs` 间距 + `labelSmall` 14 行高），
     *    反向判据同步换掉：旧格的红条件是"副标题槽画回来"，新格的红条件是
     *    **"副标题没画出来（掉回 94）"或"画了两行/画了第二颗标题"**。
     *    组件那一半（空串整槽不画）仍然成立，由 `subtitle.isNotEmpty()` 那颗件自己的判据守着，
     *    本格只判首页这一屏**确实交了四句真话**。
     *
     * 其余反例照旧咬得住：两颗都写 `LoveBrainShape.lg`（层级塌了）、
     * 入口卡被顺手加上 `ELEVATION_DEFAULT`（零阴影那句红）、
     * `GridCell` 的留白回到 16（档位表红）、`Standalone` 被一起改小（也红）。
     */
    @Test
    fun `the hero card is light blue and the four entries carry their subtitle`() {
        val hero = maskedCode("ui/home/HomeComponents.kt")
        val entry = maskedCode("core/designsystem/LbActionCard.kt")

        // ── hero：Xl 24 + 内 16 等距 + 旧版那一档浅蓝（浅底 + 细描边），阴影这一档退场
        assertTrue("hero 圆角没上 Xl 24", "shape = LoveBrainShape.xl," in hero)
        assertTrue("hero 内距不是四边等距 16", "padding(Spacing.xl)" in hero)
        assertTrue("hero 又回到四边不等（start/end/top/bottom 各写一个数）",
            !Regex("padding\\(\\s*start =").containsMatchIn(hero))
        assertTrue(
            "hero 的浅蓝底没走 M09 §5 第 1 条点名那颗既有令牌 PrimaryLight" +
                "（也不许是新增的一颗色：这里钉的是**整串实参**，换成 SurfaceCard/别的色都红）",
            "colors = CardDefaults.cardColors(containerColor = PrimaryLight)" in hero
        )
        assertTrue(
            "hero 没画上旧版那一圈 PrimarySubtle 细描边（宽/色/圆角三颗都钉：换成 Border 灰、" +
                "或把圆角借入口那一档 Lg 都红）",
            "border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.xl)" in hero
        )
        // ②的替代关系：旧格用"一颗 `.border(` 都不许有"挡双叠，本轮形状选了描边，
        // 于是双叠这一族改由**阴影那一半**来挡——两条一起钉才等价于原来那一条。
        assertTrue("hero 的阴影没退到 0（描边 + 阴影双叠正是 §3.4 明令禁止的那一种）",
            "cardElevation(defaultElevation = 0.dp)" in hero)
        assertTrue("hero 又把 ELEVATION_DEFAULT 那一档浮起借回来了（本轮选的是描边不是阴影）",
            "ELEVATION_DEFAULT_DP" !in hero)
        assertTrue("hero 在浅底与描边之外又叠了第三层手段（渐变）——M09 §5 第 1 条：三样不许堆满",
            "linearGradient" !in hero && "Brush" !in hero)

        // ── 启停那颗有按钮本体了（M09 §5 第 2 条）：白底 + 一圈 Primary 细描边，
        //    买的数是"看得见一颗能按的东西"，热区与字形那两轴一寸没动（由 HomeHeroActionTest 量）。
        assertTrue(
            "启停那颗没有可见本体（只剩一枚裸字形漂在卡上＝第一次打开的人得先破译状态灯）",
            "background(SurfaceCard, LoveBrainShape.md)" in hero &&
                "border(AppDimens.BORDER_WIDTH_DP.dp, Primary, LoveBrainShape.md)" in hero
        )
        // 本体不许涂品牌底：主卡自己已经是 PrimaryLight，品牌底叠品牌底是表面色那把尺的旧债形状。
        assertTrue(
            "启停那颗的本体涂了品牌色（浅蓝卡上一块浅蓝按钮＝两档塌成一档，而且长出一处自画品牌底）",
            !Regex("""\.background\(\s*(?:color\s*=\s*)?Primary""").containsMatchIn(hero)
        )

        // ── 入口卡：Lg 16 + 内 12 + 零阴影（描边管静息、白底压在浅蓝之外，与 hero 差一档）
        assertTrue("入口卡圆角不是 Lg 16", "shape = LoveBrainShape.lg" in entry)
        assertTrue("入口卡没显式写零阴影", "cardElevation(defaultElevation = 0.dp)" in entry)
        assertTrue("入口卡没留 1dp 描边", ".border(AppDimens.BORDER_WIDTH_DP.dp" in entry)
        assertTrue("GridCell 那一档的卡内留白没收到 12", "GridCell -> Spacing.lg" in entry)
        assertTrue("Standalone 那一档被首页顺手带小了（它今天 0 处调用，数值该留在 16）",
            "Standalone -> Spacing.xl" in entry)
        // 差值要在场：两颗同档就等于零层级
        assertTrue("hero 与入口卡用了同一档圆角，层级又塌回一格", "LoveBrainShape.xl" !in entry)
        // 入口卡的底**不许**跟着 hero 变浅蓝：hero 是这一屏唯一的浅蓝块（§5.1 主卡是视觉焦点）
        assertTrue("入口卡跟着 hero 一起涂浅蓝（两档塌成一档，主卡不再是唯一的焦点）",
            "containerColor = SurfaceCard" in entry)

        // ── 几何那一半：算式按 M09 之后的真实形状重列（见上面替代关系 ③）。
        //    真算式 = 内距12(`GridCell.contentPadding`) + 方块40(`GridCell.blockSize`)
        //             + 间距8(`Spacing.md`) + 标题22(`titleMedium.lineHeight`)
        //             + 间距2(`Spacing.xs`) + 副标题14(`labelSmall.lineHeight`) + 内距12 = **110dp**。
        //    锚点那一侧不吃尺寸：`.coachAnchor(...)` 只有 `onGloballyPositioned`，体里没有尺寸字面量。
        //    坏实现怎么红：副标题没交进来（那一格空着）⇒ 卡高掉回 94dp，四句一起红；
        //    副标题写成两行 / 或把标题也抬到两行 ⇒ 卡高超过 112dp，同样红。
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        val card = rule.onAllNodesWithTag(LbHomeTags.ENTRY_KNOWLEDGE, useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val titles = rule.onAllNodes(hasText(HOME_ENTRY_KNOWLEDGE), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val subs = rule.onAllNodes(hasText(HOME_ENTRY_KNOWLEDGE_SUB), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("标题那一行只许画出一颗字（副标题是另一颗、另一档，不许与标题撞名）：实到 ${titles.size}颗",
            1, titles.size)
        assertEquals("入口的辅助描述必须真画到屏幕上（M09 §5 第 4 条；没交 subtitle 就一颗都量不到）：实到 ${subs.size}颗",
            1, subs.size)
        val title = titles.first().boundsInRoot
        val subtitle = subs.first().boundsInRoot
        val cardBox = card.boundsInRoot
        fun toDp(px: Float): Float = px / density

        val heightDp = toDp(cardBox.height)
        val expected = 12f + 40f + 8f + 22f + 2f + 14f + 12f
        assertTrue(
            "入口卡高应正好是「内距12 + 方块40 + 间距8 + 标题22 + 间距2 + 副标题14 + 内距12」= " +
                "${expected.toInt()}dp；副标题那一行没画出来会掉回 94dp（旧形状）。实到 ${heightDp.toInt()}dp",
            kotlin.math.abs(heightDp - expected) <= 2f
        )
        // 卡顶→标题顶 = 内距12 + 方块40 + 间距8 = 60dp：方块那一档与它下面那道间距都在这里结账
        assertEquals(
            "卡顶到标题那一格之间该是「内距12 + 方块40 + 间距8」= 60dp",
            60f, toDp(title.top - cardBox.top), 1f
        )
        // 标题下面只剩「间距2 + 副标题14 + 内距12」= 28dp ⇒ 副标题就是最后一格，底下没占位的空槽
        assertEquals(
            "标题下面该是「间距2 + 副标题14 + 内距12」= 28dp（副标题掉回来会缩成 12dp，多画一行会涨过 28）",
            28f, toDp(cardBox.bottom - title.bottom), 1f
        )
        assertEquals("标题那一行占的就是 titleMedium 的 22dp 行高", 22f, toDp(title.height), 1f)
        assertEquals("辅助描述那一行占的就是 labelSmall 的 14dp 行高", 14f, toDp(subtitle.height), 1f)
        assertTrue("辅助描述必须在标题下面（画到方块那一格、或与标题并排都不是一行辅助描述）",
            subtitle.top >= title.bottom)
    }

    /**
     * 主页那三段之间的**垂直节奏**（M09 §5 第 3 条：收紧主页大区块间距，优先试 `Spacing.xl`
     * 一类**已有**的档，和 1.3.1 对照，**不改全局 `Spacing` 数值**）。
     *
     * 两半各判一件事：①按来路判（读 `HomeScreen.kt` 的源码，注释先掩平）；②按几何判
     * （绿档真量"主卡底边 → 第一排入口顶边"，数从 `Spacing` 主人那里现读，不写死）。
     *
     * 替代关系：**这一格是新立的**。M09 之前没有任何一把尺看过段间距——上一批把它从 16 抬到 24
     * 时一格都没红，那才是这条债的真实成因（"没人量的维度"不是"已经对的维度"）。
     *
     * **M25 §五 取舍③ 之后这一格多管一段**：首页底部新加的那一块累计使用小卡是这一屏的**第四格**，
     * 于是"从上到下"多出一条段间关系（四入口那一块 → 统计小卡）。旧规（K22「不恢复内部统计」）
     * 里根本没有这一段可量，所以这不是把旧判据改松，而是**按新增事实补一段**：
     * 新增的那一格**不许自己发明一道间距**，必须走同一档 `Spacing.xl`，而且必须是最后一格。
     * 反例：
     * - 段间距换回 `Spacing.xxxl`（24）⇒ ①"该走 xl 那一档"与 `Spacing.xxxl` 不许在场两句一起红，
     *   ②也会量出 24 ≠ 16；
     * - 三档差值被压平（入口两排之间那道 12、或卡与缺项那道 8 被顺手改掉）⇒ 那两句红；
     * - 统计小卡插在入口两排之间、或与主卡贴在一起 ⇒ "它是最后一格"与"入口底边→小卡顶边 = xl"两句红；
     * - 小卡那一格外面又套一层 `padding`/`Spacer` 自己造一道间距 ⇒ 那句 xl 量出 16+something，红；
     * - 页面自己再拼一道水平边距（把 `LbScreenScaffold` 那唯一主人分成两个）⇒ 最后那句红；
     * - 有人去动 `Spacing.kt` 那颗 `xl` 的数（需求明令不许）⇒ 由
     *   `core/designsystem/UiBaselineRegressionTest` 里 `assertEquals(16.dp, Spacing.xl)` 那一格先红，
     *   本格②读的是同一个主人所以**不会**跟着把红洗掉——两把尺各管各的，别把两个数合成一个。
     */
    @Test
    fun `the home blocks vertical rhythm is the tightened xl tier and nothing else`() {
        val screen = maskedCode("ui/home/HomeScreen.kt")
        assertTrue(
            "段与段之间没走 `Spacing.xl`(16) 那一档（M09 §5 第 3 条要买的就是这一处收紧）",
            "verticalArrangement = Arrangement.spacedBy(Spacing.xl)" in screen
        )
        assertTrue(
            "页面又用回 24 那一档了（本轮收紧的就是它；水平那 24 的唯一主人是 LB_SCREEN_HORIZONTAL_MARGIN）",
            "Spacing.xxxl" !in screen
        )
        assertTrue("入口两排之间那道 12 没了（三档塌成一档就读不出层级）",
            "Arrangement.spacedBy(Spacing.lg)" in screen)
        assertTrue("主卡与它那一行缺项之间那道 8 没了（缺项就不像属于主卡那一档状态）",
            "Arrangement.spacedBy(Spacing.md)" in screen)
        assertTrue("水平边距那个唯一主人不在场了", "LB_SCREEN_HORIZONTAL_MARGIN" in screen)
        assertTrue("页面自己又拼了一道水平边距（边距长出第二个主人）",
            !Regex("""padding\(\s*horizontal\s*=""").containsMatchIn(screen))

        // 几何那一半：绿档没有那一行黄字，所以主卡底边到入口顶边量到的就是**纯段间距**
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        val cardBottom = rule.onAllNodesWithTag(LbHomeTags.STATUS_CARD, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.bottom / density
        val entryTop = rule.onAllNodesWithTag(LbHomeTags.ENTRY_KNOWLEDGE, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.top / density
        val xl = com.lovebrain.app.core.designsystem.Spacing.xl.value
        assertEquals(
            "主卡底边到第一排入口顶边应正好是段间距那一档（$xl dp，从 `Spacing` 主人现读）：" +
                "实到 ${(entryTop - cardBottom).toInt()}dp",
            xl.toDouble(), (entryTop - cardBottom).toDouble(), 1.0
        )

        // ── M25 §五 取舍③ 新加的那一格：同一段间距档位，而且它是这一屏最后一格 ──
        // 数不写死：读的仍是 `Spacing` 那颗主人（上面那颗 xl），所以这一句不会替谁背书一个像素数。
        val lastEntryRowBottom = listOf(LbHomeTags.ENTRY_CAPTURE, LbHomeTags.ENTRY_PROVIDER)
            .flatMap { rule.onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes() }
            .maxOf { it.boundsInRoot.bottom } / density
        val usage = rule.onAllNodesWithTag(LbHomeTags.USAGE_CARD, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        assertTrue(
            "累计使用那一块必须排在四入口**之后**（它是读数不是目的地，不许插进入口网格、" +
                "也不许抬到主卡上面）：入口底边 ${lastEntryRowBottom.toInt()}dp / 小卡顶边 ${(usage.top / density).toInt()}dp",
            usage.top / density > lastEntryRowBottom
        )
        assertEquals(
            "四入口那一块到统计小卡之间也必须是段间距那一档（$xl dp）——新增的一格不许自己发明一道间距：" +
                "实到 ${((usage.top / density) - lastEntryRowBottom).toInt()}dp",
            xl.toDouble(), (usage.top / density - lastEntryRowBottom).toDouble(), 1.0
        )
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
