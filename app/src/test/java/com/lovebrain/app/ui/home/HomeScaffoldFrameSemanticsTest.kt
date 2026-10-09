package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页外框那一格的守卫：24dp 那道水平边距**只从 `LbScreenScaffold` 来**，
 * 页面自己不许再补一遍（旧版首页曾在 scaffold 之外再写 `padding(horizontal = …)`，
 * 于是同一 App 里两种档）。
 *
 * 重做之后这一屏只剩三段：状态卡、（黄灯时）那一行小字、四入口两排。
 * 这一格量的是它们的左右边缘与"名字不重复播报"，段顺序与可点节点由
 * `HomeScreenStructureTest` 钉，不在这儿重复立一份判据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "zh-rCN-w360dp-h1000dp-normal-long-notround-any-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScaffoldFrameSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private val cell: MutableState<UiMatrix> = mutableStateOf(UiMatrix(360, heightDp = 1000))

    /**
     * 夹具也走 `MutableState`：`rule.setContent` 每个用例只许调一次，
     * 所以"绿档→黄档"这种换态不能重挂，只能像 `cell` 一样在同一格里漂 `harnessHolder.value`，
     * 让 HomeScreen 观察到新 vm 后原地重算。
     */
    private val harnessHolder: MutableState<HomeStatusHarness> = mutableStateOf(HomeStatusHarness())

    private fun mount(harness: HomeStatusHarness = HomeStatusHarness()) {
        harnessHolder.value = harness
        rule.setContent {
            cell.value.RenderIn(LocalDensity.current.density) {
                HomeScreen(
                    homeStatus = harnessHolder.value.vm,
                    onStartService = {},
                    onNavigateFeedback = {},
                    onNavigateProviders = {},
                    onNavigateCaptureApps = {},
                    onBack = {},
                    overlayGrantedOverride = true
                )
            }
        }
        rule.waitForIdle()
    }

    /** 三段各自的外盒：状态卡 + 四入口（黄字那一行在绿档不存在，另测） */
    private val segmentTags = listOf(
        LbHomeTags.STATUS_CARD,
        LbHomeTags.ENTRY_KNOWLEDGE,
        LbHomeTags.ENTRY_FEEDBACK,
        LbHomeTags.ENTRY_CAPTURE,
        LbHomeTags.ENTRY_PROVIDER
    )

    private fun edgesOf(tag: String): Pair<Float, Float> {
        val node = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
        val rect = node.boundsInRoot
        return rect.left / density to rect.right / density
    }

    /**
     * 左边缘等于那一道公共边距、右边缘不越过"宽 - 边距"，任何一段自己再垫一层都会当场红
     * （反例：状态卡外面再写一次 `padding(horizontal = 24)` ⇒ 左边变成 48dp）。
     * 四格入口里左列两格与状态卡同边，右列两格的右边同边。
     */
    @Test
    fun `every segment sits on the one scaffold margin`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        val margin = com.lovebrain.app.core.designsystem.LB_SCREEN_HORIZONTAL_MARGIN.value
        val width = cell.value.widthDp.toFloat()

        segmentTags.forEach { tag ->
            val (left, right) = edgesOf(tag)
            assertTrue(
                "$tag 越到了公共边距以左（left=${left.toInt()}dp，边距 ${margin.toInt()}dp）：" +
                    "页面自己又垫了一层",
                left >= margin - 1f
            )
            assertTrue("$tag 越到了右边距之外（right=${right.toInt()}dp / 槽宽 ${width.toInt()}dp）", right <= width - margin + 1f)
        }
        val (cardLeft, _) = edgesOf(LbHomeTags.STATUS_CARD)
        assertEquals("状态卡自己不再补水平边距", margin, cardLeft, 1f)
        val (knowledgeLeft, _) = edgesOf(LbHomeTags.ENTRY_KNOWLEDGE)
        assertEquals("第一格入口也贴那道边距", margin, knowledgeLeft, 1f)
    }

    /**
     * 段顺序从上到下：状态卡 →（黄灯才有）那一行 → 第一排 → 第二排。
     * 顺带量那一行的**左边缘**：本轮给它加了浅底容器，容器自己不许再补一道水平边距
     * （水平边距的唯一主人仍是 `LbScreenScaffold`；容器内距落在里侧，且与主卡的内容柱同一条线）。
     * 反例：把黄字挪到四入口下面 ⇒ "黄字在入口之前"那句红；
     * 反例：那一行的内距与主卡不同档（4dp 之差就是"两块不相干的字"）⇒ 对齐那句红；
     * 反例：给浅底容器外面再写一次 `padding(horizontal = …)` ⇒ 对齐与"边距只有一个主人"两句一起红；
     * 反例：容器干脆贴着屏幕左沿画（把 scaffold 那道边距吃掉）⇒ 同一句红。
     */
    @Test
    fun `the hint line lands between the card and the entries only when it exists`() {
        val ready = HomeStatusHarness()
        ready.parkReady()
        mount(ready)
        assertEquals("绿档没有那一行", 0, hintCount())
        val cardTopReady = topOf(LbHomeTags.STATUS_CARD)
        assertTrue("第一排在状态卡下面", topOf(LbHomeTags.ENTRY_KNOWLEDGE) > cardTopReady)

        val yellow = HomeStatusHarness()
        yellow.provider.ref = null
        yellow.service.markRunning()
        rule.runOnIdle { harnessHolder.value = yellow }
        rule.waitForIdle()
        assertEquals("黄档才有那一行", 1, hintCount())
        assertTrue("那一行在状态卡之后", topOf(LbHomeTags.SETUP_HINT) > topOf(LbHomeTags.STATUS_CARD))
        assertTrue("那一行在入口之前", topOf(LbHomeTags.SETUP_HINT) < topOf(LbHomeTags.ENTRY_KNOWLEDGE))

        // §5.1「调整对齐」：这一行是**主卡那一档状态**的说明，它的文字必须与主卡的内容柱同一条左边缘。
        // 数不写死、也不读源码里那个 padding 常量：对照物是同一棵树里量到的那颗灯（灯就是主卡内容柱的第一格）。
        // 反例：容器内距与主卡内距不同档（本轮改之前是 12 对 16，同一屏两块文字左边缘差 4dp）⇒ 红；
        // 反例：给浅底容器外面再写一道水平边距（旧版那"两层"形状）⇒ 红；
        // 反例：容器干脆贴着屏幕左沿画（把 scaffold 那道边距吃掉）⇒ 后两句一起红。
        val margin = com.lovebrain.app.core.designsystem.LB_SCREEN_HORIZONTAL_MARGIN.value
        val textLeft = rule.onAllNodesWithTag(LbHomeTags.SETUP_HINT, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.left / density
        val lampLeft = rule.onAllNodesWithTag(LbHomeTags.LAMP, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.left / density
        assertEquals(
            "缺项那一行的文字要与主卡的内容柱对齐：实到 行 ${textLeft.toInt()}dp / 灯 ${lampLeft.toInt()}dp",
            lampLeft, textLeft, 1f
        )
        assertTrue(
            "公共边距只有一个主人（边距 ${margin.toInt()}dp）：容器自己不许贴屏幕左沿画，实到 ${textLeft.toInt()}dp",
            textLeft > margin
        )
    }

    private fun hintCount(): Int =
        rule.onAllNodesWithTag(LbHomeTags.SETUP_HINT, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun topOf(tag: String): Float =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top / density

    /**
     * 读屏不重复念：这一屏每个可点节点自己说一次名字，卡片标题不与图标名字重复入账。
     * 反例：给整卡可点的入口又补一句 `contentDescription = 标题` ⇒ `isDuplicatedAnnouncement` 那句红；
     * 反例：黄字末尾那颗「去设置」自己说成空名（只画字不报名）⇒ `labeled` 那句红；
     * 反例：把那颗去处与四入口里的「模型供应商」并成一颗 ⇒ 名字撞一份，distinct 那句红。
     *
     * ⚠ 绿档 5 颗、黄档 6 颗是**两格**：多出来的那一颗只在黄灯（有缺项可指路）时存在，
     * 常驻一颗"去设置"就是旧版那行黄警告的另一种形状。
     */
    @Test
    fun `the screen announces each actionable node once`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        val targets = probe.actionableTargets(rule, "首页外框")
        assertEquals("绿档五颗可点节点", 5, targets.size)
        targets.forEach { assertTrue("每颗都要有名字：" + it.describe(), it.labeled) }
        val names = targets.map { it.announced }
        assertEquals("名字不许两颗撞一份（撞了就是要念两遍）：$names", names.size, names.distinct().size)

        val yellow = HomeStatusHarness()
        yellow.provider.ref = null
        yellow.service.markRunning()
        rule.runOnIdle { harnessHolder.value = yellow }
        rule.runOnIdle { yellow.vm.playClicked(overlayGranted = true) }
        rule.waitForIdle()
        val withWayOut = probe.actionableTargets(rule, "首页外框·黄档")
        assertEquals("黄档只多一颗去处，一共六颗：" + withWayOut.joinToString { it.describe() }, 6, withWayOut.size)
        withWayOut.forEach { assertTrue("每颗都要有名字：" + it.describe(), it.labeled) }
        withWayOut.forEach {
            assertTrue("这颗把名字念了两遍：" + it.describe(), !it.isDuplicatedAnnouncement)
        }
        assertEquals(
            "那颗去处屏幕上写的与读屏报的是同一串", 1,
            withWayOut.count { it.label == "去设置" }
        )
    }

    /**
     * 换槽位时边距仍然只有一份（最窄那一格是最容易把边距挤成两半的地方）。
     * 反例：把边距写死成 dp 数字的副本散在页面里 ⇒ 某一格里左边缘不再是 24dp。
     */
    @Test
    fun `the same margin still holds at the worst cell of the matrix`() {
        val harness = HomeStatusHarness()
        harness.parkReady()
        mount(harness)
        val margin = com.lovebrain.app.core.designsystem.LB_SCREEN_HORIZONTAL_MARGIN.value
        UiMatrix.EXTREMES.forEach { matrix ->
            rule.runOnIdle { cell.value = matrix }
            rule.waitForIdle()
            val (left, right) = edgesOf(LbHomeTags.STATUS_CARD)
            assertEquals("${matrix.id}：状态卡左边缘仍是公共边距", margin, left, 1f)
            assertTrue(
                "${matrix.id}：卡片右边没越出 ${matrix.widthDp}dp 槽的右边距（实到 ${right.toInt()}dp）",
                right <= matrix.widthDp - margin + 1f
            )
        }
        assertEquals("三格极值都要量到", 3, UiMatrix.EXTREMES.size)
    }
}
