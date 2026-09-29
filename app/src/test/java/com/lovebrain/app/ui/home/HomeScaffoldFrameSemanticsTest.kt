package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LB_SCREEN_HORIZONTAL_MARGIN
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * §6.2 首页四段的**外框**换成 `LbScreenScaffold` 之后，用户从语义树里读到的东西。
 *
 * 换之前这一页自己拼外框（`Column.fillMaxSize().verticalScroll().padding(horizontal = Spacing.xxxl)`），
 * 于是"统一水平边距"在首页只是**恰好同数**，不是同一个所有者。这一组格子判的四件事全是量出来的：
 *
 * 1. 四段每一段的左边缘坐的是**脚手架自己那一个常量** [LB_SCREEN_HORIZONTAL_MARGIN]
 *    （不是测试里许愿的 24），右边那一轴同样量一次：`About` 那颗贴在 `槽宽 − 边距` 上；
 * 2. 换所有者没把四段的上下顺序动过一格（九颗锚点严格递增，一条压一条）；
 * 3. 两个快捷功能入口各是**一颗**可点节点：整卡那一颗、报成按钮、有名字、两轴都过
 *    [AppDimens.TOUCH_TARGET_MIN_DP]；而「快捷功能」那一段里可点节点的总数就是 2——
 *    第三条私有变体会在这里红；
 * 4. 整页滚一遍之后仍然：过热区下限、有可读名字、同一颗不把名字念两遍。
 *
 * 另外两格是**行为证人**：最坏那一档（320dp + 2 倍字）边距仍是同一个数、四段一个都没丢；
 * 以及每一段的出口仍然从自己那一格的节点上发回调（首页八个入口逐点个一遍）。
 *
 * ⚠ 刻意**没点**"知识库"那一颗：它的出口是 `context.startActivity(...)`，而本机从非 Activity
 * 的 context 起 Activity 这条路在全仓测试里没有任何先例（没有一处 `startActivity` 断言）。
 * 本格只证明它"整卡可点、有名字、过下限"，起没起对 Activity 属于 CANNOT-VERIFY，交设备侧。
 * 同理没点的还有捕获行尾部那颗"去授权"：它开的是一扇浮层，而这台仪器里
 * 「Dialog 窗口 + 文本框拿焦点」`waitForIdle` 永不返回（账本 §45.1）。
 * 本格挂的是**已授权**那一档，那颗出口压根不在树上。
 *
 * 与 `HomeScreenStructureTest` 的分工：那一格判"四段在不在、顺序对不对、主按钮唯不唯一"，
 * 本格判"外框的尺寸归谁、每段坐的哪一档、出口还接不接得上"。两把尺各看一件事。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScaffoldFrameSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 挂载槽宽（与 `ScreenScaffoldFrameTest` 同一档，各页才有可比性） */
    private val slotWidthDp = 360f

    /** 期望的那一档边距：**读脚手架自己的常量**，不在这里写 24 */
    private val marginDp: Float get() = LB_SCREEN_HORIZONTAL_MARGIN.value

    /** §6.5 :531 的下限：指回全站那一颗常量，不读探针的默认参数 */
    private val floorDp: Float get() = AppDimens.TOUCH_TARGET_MIN_DP.toFloat()

    /** 被排除掉的读数（0x0 那些）——必须看得见，拿过滤藏读数就是假绿另一种形状 */
    private var droppedReadings: List<SemanticsProbe.Target> = emptyList()

    /** 一个矩形（px：语义树 `boundsInRoot` 的原单位） */
    private data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val cx get() = (left + right) / 2f
        val cy get() = (top + bottom) / 2f
        val area get() = (right - left) * (bottom - top)
        val laid get() = right > left && bottom > top
        fun containsCenterOf(other: PxRect) =
            left <= other.cx && other.cx <= right && top <= other.cy && other.cy <= bottom
        fun contains(other: PxRect) =
            other.left >= left - 0.5f && other.top >= top - 0.5f &&
                other.right <= right + 0.5f && other.bottom <= bottom + 0.5f
        fun describe() = "(${left.toInt()},${top.toInt()})-(${right.toInt()},${bottom.toInt()})px"
    }

    /** 与 `HomeScreenStructureTest` 同款：那四条流必须显式桩（relaxed 扛不住泛型 StateFlow） */
    private fun vm(accessible: Boolean = true): SetupViewModel =
        mockk<SetupViewModel>(relaxed = true).also {
            every { it.activeTicket } returns MutableStateFlow(
                ProviderTicket(
                    name = "TICKET_NAME_SENTINEL",
                    baseUrl = "https://example.test/v1",
                    model = "MODEL_SENTINEL"
                )
            )
            every { it.providerReady } returns MutableStateFlow(true)
            every { it.captureEnabled } returns MutableStateFlow(true)
            every { it.captureAllowedPackages } returns MutableStateFlow(setOf("com.a", "com.b"))
            every { it.isCaptureServiceEnabled(any()) } returns accessible
        }

    /**
     * 挂整页。[calls] 是**出口证人**：每条回调往里面写自己的名字，
     * 于是"点的哪一格"与"响的哪一个出口"两边都对得上。
     */
    private fun mount(
        model: SetupViewModel,
        cell: UiMatrix = UiMatrix(360),
        calls: MutableList<String>? = null
    ) {
        val sink: (String) -> Unit = { calls?.add(it) }
        rule.setContent {
            cell.RenderIn(LocalDensity.current.density) {
                HomeScreen(
                    viewModel = model,
                    onStartService = { sink("start") },
                    onOpenPanel = { _, _ -> sink("panel") },
                    onTempHide = { sink("hide") },
                    onRestore = { sink("restore") },
                    onNavigateFeedback = { sink("feedback") },
                    onNavigateAbout = { sink("about") },
                    onNavigateProviders = { sink("providers") },
                    onNavigateUsage = { sink("usage") },
                    onNavigateCaptureApps = { sink("capture-apps") },
                    onBack = { sink("back") },
                    // 两个前提摆成"已授权 + 服务没跑"：主按钮走 Start 那一格，
                    // 右上角那颗隐藏钮不在树里（它由 `HomeScreenStructureTest` 四个组合逐一摆）
                    overlayGrantedOverride = true,
                    serviceRunningOverride = false
                )
            }
        }
        rule.waitForIdle()
    }

    // ═══════════ 量的手段：只有语义树读数，没有一处源码 grep ═══════════

    private fun rectOf(node: SemanticsNode) = node.boundsInRoot.let {
        PxRect(it.left, it.top, it.right, it.bottom)
    }

    private fun areaOf(node: SemanticsNode) = node.boundsInRoot.width * node.boundsInRoot.height

    private fun clickableNodes(): List<SemanticsNode> =
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    /**
     * 带着这个 tag 的锚点矩形，**按矩形去重**。
     *
     * 为什么去重：同一个 LayoutNode 在未合并树里可以出现两个带同一 tag 的节点
     * （`Modifier.clickable` 合并语义 + `Modifier.testTag` 不合并——`HomeScreenStructureTest` §25.4
     * 就是被这件事坑过一版），不去重就会把"两颗卡片"数成四颗。
     */
    private fun allAnchors(tag: String, what: String): List<PxRect> {
        val all = rule.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes().map(::rectOf).distinct()
        check(all.isNotEmpty()) { "$what（tag=$tag）一颗都没数到——这把尺在这一段是瞎的" }
        return all
    }

    /** 只留**真的摆出来了**的那些（`0x0 @(0,0)` 的左边缘是 0，混进来会读成"边距 0dp"） */
    private fun anchors(tag: String, what: String): List<PxRect> {
        val all = allAnchors(tag, what)
        val laid = all.filter { it.laid }
        assertTrue(
            "$what（tag=$tag）数到 ${all.size} 颗，全被压成 0x0，量不到边距：${all.map { it.describe() }}",
            laid.isNotEmpty()
        )
        return laid
    }

    private fun anchor(tag: String, what: String): PxRect = anchors(tag, what).minByOrNull { it.top }!!

    private fun sectionTops() = anchors(LbTags.SECTION, "分区标题").map { it.top / density }.distinct().sorted()

    private fun laidTargets(screen: String): List<SemanticsProbe.Target> {
        val all = probe.actionableTargets(rule, screen)
        droppedReadings = probe.unlaid(all)
        return probe.laid(all)
    }

    private fun describeHits(hits: List<Pair<SemanticsNode, SemanticsProbe.Target>>) =
        hits.joinToString { it.second.describe() }

    private fun clickNode(node: SemanticsNode, why: String): SemanticsNodeInteraction {
        val all = clickableNodes()
        val idx = all.indexOfFirst { it.id == node.id }
        assertTrue("$why：这颗节点不在可点集合里（id=${node.id}）——那点的就不是它", idx >= 0)
        return rule.onAllNodes(hasClickAction())[idx]
    }

    /**
     * 点「中心落在 [rect] 里」那几颗里**面积最大**的那一颗 = 这一格自己的容器
     * （整行、整卡、页头尾部那颗）。
     */
    private fun clickSelfNodeOf(rect: PxRect, why: String) {
        val hits = clickableNodes().map { it to probe.of(it) }
            .filter { (n, _) -> rect.containsCenterOf(rectOf(n)) }
        check(hits.isNotEmpty()) {
            "$why：${rect.describe()} 这个矩形里没有可点节点——那一格的出口没挂上点击语义"
        }
        val (node, target) = hits.maxByOrNull { areaOf(it.first) }!!
        clickNode(node, why).performClick()
        rule.waitForIdle()
        assertTrue("$why：点的应该是这一格的容器，实到 " + target.describe(), !target.tooSmall(floorDp))
    }

    /**
     * 点「完整嵌在 [rect] 里、又比 [rect] 小」那几颗里**面积最小**的一颗 = 那一格里的
     * **第二处**出口（行尾那颗弱动作）。
     *
     * 为什么要分两档：§6.2 第 4 段那两行各自挂着两个出口（整行一个、尾部一个）。
     * 只点面积最大那一颗，就等于从没量过尾部那颗。
     */
    private fun clickNestedNodeOf(rect: PxRect, why: String) {
        val hits = clickableNodes().map { it to probe.of(it) }.filter { (n, _) ->
            val r = rectOf(n)
            rect.contains(r) && r.area < rect.area - 1f
        }
        check(hits.isNotEmpty()) {
            "$why：${rect.describe()} 里没有嵌套的第二颗可点节点——尾部那颗出口丢了，或者整格被合成了一颗"
        }
        val (node, target) = hits.minByOrNull { areaOf(it.first) }!!
        assertTrue(
            "$why：尾部那颗得有可读名字，实到 " + target.describe(),
            target.label.isNotBlank() || target.contentDescriptions.isNotEmpty()
        )
        assertTrue("$why：尾部那颗得过下限，实到 " + target.describe(), !target.tooSmall(floorDp))
        clickNode(node, why).performClick()
        rule.waitForIdle()
    }

    /** 同时包住这些矩形的那一颗可点节点（面积最小 = 最贴近它们的主人） */
    private fun coveringNode(rects: List<PxRect>, why: String): SemanticsNode {
        val hits = clickableNodes().filter { n -> val r = rectOf(n); rects.all { r.containsCenterOf(it) } }
        check(hits.isNotEmpty()) { "$why：找不到同时包住 ${rects.map { it.describe() }} 的可点节点" }
        return hits.minByOrNull { areaOf(it) }!!
    }

    // ═══════════ 判据 ═══════════

    /** ① 外框那一档水平边距：四段共用脚手架那一个常量，左右两轴都量 */
    @Test
    fun `every segment sits on the scaffold margin and nothing adds a second inset`() {
        mount(vm())
        val laid = laidTargets("首页外框")
        val droppedNote = "排除掉的 0x0：" + droppedReadings.joinToString { it.describe() }

        // 最左与最右那两颗**可点**节点：外框把内容推进去多少，就看这两条边
        val leftMost = laid.minByOrNull { it.leftDp }!!
        val rightMost = laid.maxByOrNull { it.leftDp + it.widthDp }!!
        assertEquals(
            "整页的水平边距应当就是脚手架那一档 ${marginDp.toInt()}dp" +
                "（量的是：" + leftMost.describe() + "；$droppedNote）" +
                "——对不上说明这一页还在自己写外框",
            marginDp, leftMost.leftDp, 0.6f
        )
        assertEquals(
            "右边那一轴也得同一档（量的是：" + rightMost.describe() + "；$droppedNote）",
            slotWidthDp - marginDp, rightMost.leftDp + rightMost.widthDp, 0.6f
        )

        // 四段**各自**量一次左边缘：外框给的是同一个数，哪一段再自己垫一层就在这里红。
        // 统计那一格量的是**包住三颗格子的那张卡**（格子本身被卡自己的内边距推进去，那不是外框）
        val perSegment = listOf(
            "②主卡" to anchor(LbHomeTags.STATUS_CARD, "主卡"),
            "③快捷功能卡" to anchors(LbTags.ACTION_CARD, "快捷功能卡").minByOrNull { it.left }!!,
            "④设置行" to anchors(LbTags.SETTING_ROW, "设置行").minByOrNull { it.left }!!,
            "④使用概览那张卡" to rectOf(coveringNode(anchors(LbTags.METRIC_CELL, "统计格"), "统计卡"))
        )
        perSegment.forEach { (what, rect) ->
            assertEquals(
                "$what 的左边缘应当坐在脚手架那一档上，实到 ${(rect.left / density).toInt()}dp ${rect.describe()}",
                marginDp, rect.left / density, 0.6f
            )
        }
        // 第 1 段尾部那颗：右边那一轴同档
        val about = anchor(LbHomeTags.ABOUT, "页头 About")
        assertEquals(
            "About 那颗的右边缘应当贴在 槽宽−边距：${about.describe()}",
            slotWidthDp - marginDp, about.right / density, 0.6f
        )
    }

    /** ② 换所有者没动四段的上下顺序：九颗锚点严格递增 */
    @Test
    fun `the four segments keep one strict top-to-bottom order after the frame changed owner`() {
        mount(vm())
        val sections = sectionTops()
        assertEquals(
            "分区标题应当是 3 个（快捷功能 / 服务设置 / 使用概览），实到 $sections",
            3, sections.size
        )

        val tops = listOf(
            "①页头 About" to anchor(LbHomeTags.ABOUT, "About").top / density,
            "②主卡" to anchor(LbHomeTags.STATUS_CARD, "主卡").top / density,
            "②主卡里的主按钮" to anchor(LbHomeTags.PRIMARY_BUTTON, "主按钮").top / density,
            "③分区标题" to sections[0],
            "③快捷功能卡" to anchor(LbTags.ACTION_CARD, "快捷功能卡").top / density,
            "④分区标题（服务设置）" to sections[1],
            "④设置行" to anchor(LbTags.SETTING_ROW, "设置行").top / density,
            "④分区标题（使用概览）" to sections[2],
            "④统计三等分" to anchor(LbTags.METRIC_CELL, "统计格").top / density
        )
        val readout = tops.joinToString(" | ") { "${it.first}=${it.second.toInt()}" }
        tops.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "四段的顺序被换外框主人动过了：${a.first}(${a.second.toInt()}) 必须在 " +
                    "${b.first}(${b.second.toInt()}) 之上。整排读数：$readout",
                a.second < b.second
            )
        }
    }

    /** ③ 两个快捷功能入口：同一颗组件、各一颗可点、有名字、两轴过下限，整段不多一颗 */
    @Test
    fun `each quick action is one named actionable node at the touch floor on both axes`() {
        mount(vm())
        val cards = anchors(LbTags.ACTION_CARD, "快捷功能卡")
        assertEquals("快捷功能必须是两颗同一颗组件的卡片，实到 ${cards.size}", 2, cards.size)

        val targets = clickableNodes().map { it to probe.of(it) }
        cards.forEachIndexed { i, card ->
            val inside = targets.filter { (n, _) -> card.containsCenterOf(rectOf(n)) }
            assertEquals(
                "第 ${i + 1} 张快捷功能卡里只能有一颗可点节点（多出来就是有人在卡里又叠了一层点击，" +
                    "或者这一条根本不是那颗共用的卡片）：" + describeHits(inside),
                1, inside.size
            )
            val t = inside.single().second
            assertEquals("整卡一处操作得先报成按钮：" + t.describe(), "Button", t.role)
            assertTrue("入口得有可读名字：" + t.describe(), t.label.isNotBlank())
            assertTrue(
                "两轴都得过 ${floorDp.toInt()}dp（§6.5 :531）：" + t.describe(),
                !t.tooSmall(floorDp)
            )
            // 那颗可点节点**就是卡片自己**：同一颗矩形，不是卡里更深的一层
            assertEquals("可点节点的左边缘 = 卡片左边缘：" + t.describe(), card.left / density, t.leftDp, 0.6f)
            assertEquals("可点节点的顶边 = 卡片顶边：" + t.describe(), card.top / density, t.topDp, 0.6f)
        }

        // 「快捷功能」那一段里可点节点的总数：两颗，不多不少（第三条私有变体会在这里红）
        val sections = sectionTops()
        val inBand = targets.filter { (_, t) -> t.topDp > sections[0] && t.topDp < sections[1] }
        assertEquals(
            "这一段里可点节点必须恰好 2 颗：" + describeHits(inBand),
            2, inBand.size
        )
        // 两张卡并排两等分：同宽、同顶边（有人把第二条改成另一档形状也会在这里红）
        val rects = cards.sortedBy { it.left }
        assertEquals(
            "两张卡同宽：" + rects.map { it.describe() },
            rects[0].right - rects[0].left, rects[1].right - rects[1].left, 0.6f
        )
        assertEquals(
            "两张卡同顶边：" + rects.map { it.describe() },
            rects[0].top / density, rects[1].top / density, 0.6f
        )
    }

    /** ④ 整页滚一遍：下限、命名、不重念。滚动容器不在了，这一格会当场抛 */
    @Test
    fun `the scrolled home page meets the touch and naming floor and announces nothing twice`() {
        mount(vm())
        probe.assertAllActionableMeetTouchFloor(rule, "首页")
        probe.assertAllActionableLabeled(rule, "首页")
        probe.assertNoDuplicatedAnnouncement(rule, "首页")

        // 首屏之下的那些：[ScrollScan] 找不到滚动容器就抛——
        // 所以这一句同时是"外框换了主人，这一页仍然整页可滚"的行为证人
        val seen = ScrollScan(rule, probe).toBottom("首页")
        val offenders = seen.values.filter { it.tooSmall(floorDp) }
        assertTrue(
            "滚过一遍之后仍有可交互节点不到 ${floorDp.toInt()}dp：\n" +
                offenders.joinToString("\n  ") { "  " + it.describe() },
            offenders.isEmpty()
        )
        val unlabeled = seen.values.filter { !it.labeled }
        assertTrue(
            "读屏说不出名字的入口：\n" + unlabeled.joinToString("\n  ") { it.describe() },
            unlabeled.isEmpty()
        )
        val doubled = seen.values.filter { it.isDuplicatedAnnouncement }
        assertTrue(
            "把同一个名字念了两遍的入口：\n" + doubled.joinToString("\n  ") { it.describe() },
            doubled.isEmpty()
        )
        // 反空跑：这一格得真扫到八类以上出口，否则"全绿"只是没量到东西
        assertTrue(
            "整屏只量到 ${seen.size} 类节点 ⇒ 这格大概什么也没测到：" + seen.keys.sorted(),
            seen.size >= 8
        )
    }

    /** ⑤ 最坏那一档（320dp + 2 倍字）：摆得出来的段仍坐同一档，四段一颗都没丢 */
    @Test
    fun `the same margin still holds at the worst cell of the matrix`() {
        mount(vm(), UiMatrix(320, fontScale = 2.0f))
        val readout = mutableListOf<String>()
        var measuredRects = 0
        // 只有**容器级**的锚点才要求"整段最左缘正好等于脚手架那一档"；
        // `METRIC_CELL` 是卡里的小格，它天然在卡的 `Spacing.xl` 内边距之内（24 + 16 = 40dp），
        // 拿它去比 24 是断言写错了。对它只要求"不许跑到边距之左"。
        listOf(
            "②主卡" to LbHomeTags.STATUS_CARD,
            "③快捷功能卡" to LbTags.ACTION_CARD,
            "④设置行" to LbTags.SETTING_ROW,
            "④统计格" to LbTags.METRIC_CELL
        ).forEach { (what, tag) ->
            // 这一档**不要求每一段都摆得出来**：2 倍字下这一屏比视口长，滚出去那几颗在树里是
            // 0x0（[SemanticsProbe.unlaid] 就是为这件事造的）。量得到的必须对得上，
            // 量不到的要点名——两头都不能闷着。
            val rects = allAnchors(tag, what)
            val laid = rects.filter { it.laid }
            readout += "$what：量到 ${laid.size} 颗、0x0 ${rects.size - laid.size} 颗"
            if (laid.isEmpty()) return@forEach
            measuredRects += laid.size
            // 判"这一段贴着脚手架那一档"要看**整段的最左缘**，不是每颗各自的最左缘：
            // ③那一段是两张并排的卡，右边那张的左缘本来就在 166dp 处。
            // 第一版按"每一颗都等于 margin"断言，2 倍字那一档就被右边那张判红——
            // 那是断言写错了，不是页面错了（同族坑：判据取错所有者/取错节点）。
            // 真正要钉的两件事都还在：整段不许越过脚手架边（任何一颗左缘 < margin 就红），
            // 以及这一段的最左缘必须正好坐在那一档上。
            val leftmost = laid.minOf { it.left } / density
            if (tag == LbTags.METRIC_CELL) {
                assertTrue(
                    "$what 在 320dp + 2 倍字那一档跑到脚手架边距之左（卡内小格只要求这一条）：" +
                        laid.joinToString { it.describe() },
                    leftmost >= marginDp - 0.6f
                )
                return@forEach
            }
            assertEquals(
                "$what 在 320dp + 2 倍字那一档离开了脚手架那一档（整段最左缘）：" +
                    laid.joinToString { it.describe() },
                marginDp, leftmost, 0.6f
            )
            laid.forEach { r ->
                assertTrue(
                    "$what 在 320dp + 2 倍字那一档有一颗跑到脚手架边距之左：" + r.describe(),
                    r.left / density >= marginDp - 0.6f
                )
            }
        }
        assertTrue("四段一颗都没量到，这一格就是空的：$readout", measuredRects >= 1)
        // 四段仍然都在树里（"换外框"不许把哪一段弄丢）：`allAnchors` 数不到就自己抛，
        // 这里要点名的是**哪一段**，所以逐段各走一次
        listOf(
            "①页头 About" to LbHomeTags.ABOUT,
            "②主卡" to LbHomeTags.STATUS_CARD,
            "③快捷功能卡" to LbTags.ACTION_CARD,
            "④设置行" to LbTags.SETTING_ROW,
            "④统计格" to LbTags.METRIC_CELL
        ).forEach { (what, tag) -> allAnchors(tag, "$what（320dp + 2 倍字）") }
        // 分区标题：至少两颗摆得出来（三颗全在视口外时这一句会红，那就是尺没在量东西）
        val sections = allAnchors(LbTags.SECTION, "分区标题")
        assertTrue(
            "分区标题量得到的只有 ${sections.count { it.laid }} 颗（树里 ${sections.size} 颗矩形）：" +
                sections.map { it.describe() },
            sections.count { it.laid } >= 2
        )
    }

    /** ⑥ 每一段的出口仍然接在自己那一格的节点上（行为证人，不是结构判据） */
    @Test
    fun `each segment's entry still fires its own callback from its own node`() {
        val calls = mutableListOf<String>()
        val model = vm()
        mount(model, calls = calls)

        clickSelfNodeOf(anchor(LbHomeTags.ABOUT, "About"), "页头 About")
        clickSelfNodeOf(anchor(LbHomeTags.PRIMARY_BUTTON, "主按钮"), "主卡唯一主动作")
        val cards = anchors(LbTags.ACTION_CARD, "快捷功能卡").sortedBy { it.left }
        // 左边那张是「知识库」——它的出口起的是 Activity，本格不点（见文件头）；右边那张进反馈案例
        clickSelfNodeOf(cards[1], "反馈案例入口")
        val rows = anchors(LbTags.SETTING_ROW, "设置行").sortedBy { it.top }
        clickSelfNodeOf(rows[0], "模型供应商整行")
        clickNestedNodeOf(rows[0], "模型供应商尾部那颗")
        clickSelfNodeOf(rows[1], "消息捕获整行（开关）")
        clickNestedNodeOf(rows[1], "消息捕获尾部那颗")
        clickSelfNodeOf(
            rectOf(coveringNode(anchors(LbTags.METRIC_CELL, "统计格"), "使用概览那张卡")),
            "使用概览那张卡"
        )

        assertEquals(
            "八次点击各自打回自己那一个出口（顺序 = 点的顺序；" +
                "消息捕获整行那颗打的是开关，不进这个表）",
            listOf(
                "about", "start", "feedback",
                "providers", "providers",
                "capture-apps", "usage"
            ),
            calls
        )
        // 消息捕获那一行点下去是**开关**，不是导航：这件事也得分辨得出来
        verify(exactly = 1) { model.toggleCapture() }
    }
}
