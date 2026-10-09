package com.lovebrain.app.ui.panel

import com.lovebrain.app.feature.composer.ComposerStore
import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.panel.counseling.CounselingPanel
import com.lovebrain.app.viewmodel.LoveBrainViewModel
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
 * 谈心的模板 chip 归进 `LbChip`（`Action` 一档）之后的读数。
 *
 * 这一族本来就没有"选中"：点一下是把那句模板填进输入框，所以它交的是
 * `Role.Button` 而**不是** `Role.Tab`，语义树里也不该出现 `Selected`——
 * 给一颗按钮写"已选中"是把读屏引向一个不存在的事实（这一条与页头那三档模式正好相反，
 * 那边是真的互斥单选）。
 *
 * 归并动了文字的位置，没动热区——上一轮（用户原话第 12 条"模板胶囊卡片太高"）在此基础上再收一层：
 * 模板那颗从 `LbChipStyles.neutral`（单层、可见胶囊被 `touchFloor` 撑到 48 见方、标签钉左上）
 * 换到面板紧凑胶囊那一档（可见胶囊 [AppDimens.CHIP_PANEL_HEIGHT_DP]=28、标签居中、
 * `layeredTouch=true` 分两层）。
 *
 * R12 / §12.1 修复：`touchFloor` 关掉，行高从 48 降到 28——"整行 48dp 仍占高"就是那一轮的读数。
 * TEAM_RULES §3：确实无法同时满足紧凑视觉和全局热区下限时，保留用户指定的紧凑视觉。
 *
 * 本轮（书 §9「谈心模板**整组**用同一紧凑档，消除父行多余高度，不只把外观压成小 pill；
 * 保留现有六个模板及点击填入功能，长模板可横滚」）钉的是这些能被量出来的事：
 * 1. **语义一字未改**：还是按钮、不播报选中、读得出自己；
 * 2. **标签回到中轴**：`TopStart` 的"贴顶 + 触底一大截空白"用几何判红（反向证人内建在断言里）；
 * 3. **整组同一档**：谈心这一页里的每一颗芯片（模板行那六颗 + 结果区那两颗动作）实测**同一个高度**、
 *    同一个中轴——那一档现在只有一处主人（`counselingCompactChipTier`），页面不再各抄一份 copy 链；
 *    漏抄一句"不垫下限"就回到 48 那一档，这一句当场红；
 * 4. **整行占位**（旧台账第 12 条判的就是这一轴，不是胶囊多高）：模板行在页面里真实吃掉的那一格
 *    不许比"胶囊带高 + 行自己的间距 token"更厚 ⇒ 外观压成小 pill、父行照旧占高那种修法红；
 * 5. **六条全在、而且真的能横滚**：一条不许少（折叠成两条那种"解决"红），
 *    视口装不下六条（`before.size < 6`），而一记横滑真的换掉了视口里摆着的那几颗
 *    （`horizontalScroll` 换成被裁的普通 Row 或换成换行，两种都红）。
 *
 * ⚠ 仪器边界（如实记着，别读成"可见 28 已经量过"）：语义树只暴露那颗**外层可点盒**（合并了文案）
 * 和未合并树里那一条**标签**（`LbChipTierTest` 用的是同一对锚点）。内层那颗 28 胶囊带的是
 * `clip/background/border`，没有语义槽，JVM 侧读不到它的矩形——"可见高度真的从 48 降到 28"这一半
 * 只能真机/截图验，本轮登记为**未验证-需真机**（详见交付台账）。
 *
 * ⚠ 这一行是 `horizontalScroll`：滚出视口的那几颗在语义树里被压成 `0x0` 或半截，
 * 那不是热区不达标。筛法与样本下限抄 `SuggestCounselingTargetsTest`
 * （排除项连同尺寸打进失败信息，筛到只剩一颗就是这格在自证）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CounselingTemplateChipTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /** 生产里那六条模板的头两条：锚点用界面词表（与这一族其它格同一个做法：按可见文案定位，不靠 tag） */
    private val firstTemplate = "她突然冷淡了怎么办"
    private val secondTemplate = "我们吵架了该谁先低头"

    /** §9「保留现有六个模板」那一句的名单：一条不许少、也不许多（生产词表在 `CounselingTemplateChips`） */
    private val allTemplates = listOf(
        firstTemplate,
        secondTemplate,
        "她说了这句话什么意思",
        "怎么判断她喜不喜欢我",
        "暧昧期怎么推进关系",
        "她嫌我不够浪漫"
    )

    /** 结果区那两颗动作（同一族、共用谈心那一档的主人）：有结果时才画 */
    private val followUpChip = "继续追问"
    private val clearChip = "清空重聊"

    /** 谈心 CTA 那一颗的文案前缀（草稿非空时它才可点，量得到的就是它那 40dp 的盒子） */
    private val ctaLabel = "开始谈心"

    /**
     * 谈心面板那份假 VM + **握在手里的两条流**。
     *
     * 为什么要连着流一起交出来：JVM 这边一棵组合只能 `setContent` 一次（本机记过的坑），
     * 而 §9 那句「整组用同一紧凑档」要在**同一棵树里**看两个状态——模板行那六颗只在
     * 「无结果」那一档画，结果区那两颗只在「有结果」那一档画。留着那两条 StateFlow，
     * 就能在同一次挂载里 `runOnIdle { result.value = … }` 把面板推到另一档，
     * 而不是起第二棵树各量各的（那种比法比的是两次挂载，不是一组芯片）。
     */
    private class FakeVm(
        val vm: LoveBrainViewModel,
        val draftFlow: MutableStateFlow<String>,
        val resultFlow: MutableStateFlow<String?>
    )

    private fun harness(draft: String = "", result: String? = null): FakeVm {
        val vm = mockk<LoveBrainViewModel>(relaxed = true)
        // 第5节第2条 第 6 步：VM 上那 10 条纯转发口已删，状态归 ComposerStore 自己
        val composer = mockk<ComposerStore>(relaxed = true)
        val draftFlow = MutableStateFlow(draft)
        val resultFlow = MutableStateFlow(result)
        every { vm.composer } returns composer
        every { composer.counselingDraft } returns draftFlow
        every { vm.counselingResult } returns resultFlow
        every { vm.counselingError } returns MutableStateFlow<String?>(null)
        every { vm.isCounseling } returns MutableStateFlow(false)
        every { vm.counselingStreaming } returns MutableStateFlow("")
        // 结果区那一档会 `remember { viewModel.loadCounselingHistory() }`（读盘），
        // 这里显式桩成空清单：relaxed 给集合类型的那份兜底不该被这格依赖
        every { vm.loadCounselingHistory() } returns emptyList()
        return FakeVm(vm, draftFlow, resultFlow)
    }

    private fun fakeVm(draft: String = "", result: String? = null): LoveBrainViewModel =
        harness(draft, result).vm

    private fun mount(vm: LoveBrainViewModel) {
        rule.setContent {
            UiMatrix(VIEWPORT_WIDTH_DP.toInt()).RenderIn(LocalDensity.current.density) {
                CounselingPanel(viewModel = vm, onFocusChange = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /**
     * 视口内**完整摆着**的那些颗：横滚容器把滚出去的读数压成 `0x0` 或半截，
     * 那种读数不能进尺寸判据（筛掉的连同尺寸打进失败信息，不默默丢）。
     */
    private fun laidChips(known: List<String>, screen: String): List<SemanticsProbe.Target> {
        val all = probe.actionableTargets(rule, screen)
        val hit = all.filter { it.label in known }
        val (visible, clipped) = hit.partition {
            it.widthDp > 0f && it.heightDp > 0f && it.leftDp >= 0f &&
                it.leftDp + it.widthDp <= VIEWPORT_WIDTH_DP - 0.5f
        }
        assertTrue(
            "$screen 视口内只量到 ${visible.size} 颗（整树命中的是 ${hit.size} 颗）——" +
                "少了就是这一行没画开，断言会在近乎空的上扫绿。裁掉的：" +
                clipped.joinToString { it.describe() },
            visible.isNotEmpty()
        )
        return visible
    }

    /** 同一份筛法只要名字：判"横滚前后视口里摆着哪几颗"换了没有 */
    private fun laidLabels(known: List<String>, screen: String): Set<String> =
        laidChips(known, screen).map { it.label }.toSet()

    /**
     * 一颗芯片自己的标签是不是落在它的热区盒中轴上（贴顶留一截空白就是原话第 12 条的可见成因）。
     * 标签从不合并树里读——分层内层那颗胶囊没有语义槽，读得到的只有它里面这条字。
     */
    private fun assertLabelCentered(chip: SemanticsProbe.Target, screen: String) {
        val inkNodes = rule.onAllNodes(hasText(chip.label), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(
            "${chip.label} 的标签节点应当唯一（不唯一 = 这一格没在判那颗 chip）",
            1,
            inkNodes.size
        )
        val ink = probe.of(inkNodes.single())
        val toTop = ink.topDp - chip.topDp
        val toBottom = (chip.topDp + chip.heightDp) - (ink.topDp + ink.heightDp)
        assertTrue(
            "$screen：${chip.label} 的标签没落在热区盒的中轴上（上隙 ${"%.1f".format(toTop)}dp / " +
                "下隙 ${"%.1f".format(toBottom)}dp）——贴顶留空白就是原话第 12 条那处过高。" +
                chip.describe() + " / " + ink.describe(),
            kotlin.math.abs(toTop - toBottom) <= CENTER_TOLERANCE_DP
        )
    }

    private fun chipsInViewport(): List<SemanticsProbe.Target> =
        // 只留完整落在挂载那一格视口里的那些：被滚动容器裁掉的读数不能拿来判尺寸
        laidChips(listOf(firstTemplate, secondTemplate), "谈心面板·模板 chip")

    /** `Action` 一档的语义：按钮、有名字、**不**播报选中 */
    @Test
    fun `template chips are buttons that invent no selection state`() {
        mount(fakeVm())
        chipsInViewport().forEach { chip ->
            assertEquals("${chip.label} 该报成按钮：" + chip.describe(), "Button", chip.role)
            assertEquals("${chip.label} 不该有一槽选中：" + chip.describe(), null, chip.selected)
            assertTrue("${chip.label} 不该是 toggle：" + chip.describe(), !chip.isToggle)
            assertTrue("${chip.label} 读得出自己", chip.labeled)
        }
    }

    /** R12/§12.1：行高从 48 降到 28，热区与可见胶囊同高（横滚行里宽度充足） */
    @Test
    fun `the template chips height matches the compact pill not the old touch floor`() {
        mount(fakeVm())
        chipsInViewport().forEach { chip ->
            assertTrue(
                "${chip.label} 的热区高度应 ≤ ${com.lovebrain.app.core.designsystem.AppDimens.CHIP_PANEL_HEIGHT_DP}dp（R12 收行占位）:" + chip.describe(),
                chip.heightDp <= com.lovebrain.app.core.designsystem.AppDimens.CHIP_PANEL_HEIGHT_DP + 1f
            )
        }
    }

    /**
     * 标签在可点盒的中轴上，而不是贴顶留一截空白（用户原话第 12 条"卡片太高"里能被几何看见的那一半）。
     *
     * 判法照 `LbChipTierTest`：外层那颗可点盒与未合并树里那条标签**各读各的**，
     * 比"标签上隙 == 下隙"。回退成 `LbChipStyles.neutral`（TopStart）时上隙塌到内边距、下隙撑到一大截，
     * 这一句当场红——反向证人内建在同一条断言里，不需要另注一件坏形状。
     */
    @Test
    fun `the template chip centers its label instead of pinning it to a full-height top edge`() {
        mount(fakeVm())
        // 外层可点盒（合并树里 label 就是这句模板，第一颗完整在视口内）
        val pill = probe.actionableTargets(rule, "谈心模板 chip·居中")
            .single { it.label == firstTemplate && it.widthDp > 0f && it.heightDp > 0f }
        assertLabelCentered(pill, "谈心模板 chip·居中")
        // R12：行高已收到 28，热区与可见同高；这里只判标签居中
        assertTrue(
            "居中之余高度不许回弹到 48（R12 已收行占位）：" + pill.describe(),
            pill.heightDp <= AppDimens.CHIP_PANEL_HEIGHT_DP + TIER_SPREAD_TOLERANCE_DP
        )
    }

    /** 点了真的要填进输入框——归并最怕"形状还在、结果没了" */
    @Test
    fun `tapping a template chip writes that template into the draft`() {
        val vm = fakeVm()
        mount(vm)
        rule.onAllNodes(hasText(secondTemplate))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        verify(exactly = 1) { vm.setCounselingDraft(secondTemplate) }
    }

    /**
     * §9「谈心模板**整组**用同一紧凑档」：谈心这一页里的每一颗芯片——模板行那六颗
     * **加上**结果区那两颗动作（「继续追问」「清空重聊」）——实测必须落在同一档上：
     * 同一个高度、同一个中轴。这一档只有一处主人（`counseling/CounselingTemplateChips.kt`
     * 的 `counselingCompactChipTier`），三处调用点不再各抄一份 copy 链。
     *
     * ⚠ 仪器边界（别读成"分层已经量到"）：语义树只暴露**外层那颗可点盒**（合并了文案），
     * 内层带底色/描边的胶囊没有语义槽，JVM 侧读不到它的矩形——"可见高度"那一半仍是
     * **未验证-需真机**（文件头那条同一口径）。这一格判的是外层盒的几何与中轴。
     *
     * 反例（坏实现）：任一调用点离开那颗主人、自己再抄一份档并抄漏一句
     * `touchFloor = false` ⇒ 那颗回到 48（`LbChip` 单层那条"取 pillHeight 与下限里更大的那颗"）
     * ⇒ 齐平判据与超档判据两处一起红；抄回 `neutral` 的 `TopStart` ⇒ 标签贴顶 ⇒ 中轴那句红。
     */
    @Test
    fun `every counseling chip on the page is measured on one and the same tier`() {
        // 一棵组合、两个档位：先把「无结果」那一档的模板行量下来，再把同一棵树推到
        // 「有结果」那一档去量结果区那两颗（`setContent` 在 JVM 这边只能来一次，见 [harness]）。
        val h = harness()
        mount(h.vm)
        // ⚠ 两档各自**当场**量标签中轴：模板那六颗只在"无结果"那一档在场（`CounselingPanel.kt:146`
        // 那道 `result == null` 的门），先把结果推进去再回头找它们的标签节点，读到的必然是 0——
        // 那是量具走错时序，不是模板没了。几何读数先留在树上，跨档比较放到最后一句（它只读已量到的数）。
        val templates = laidChips(allTemplates, "谈心面板·整组档·模板行")
        templates.forEach { assertLabelCentered(it, "谈心面板·整组档·模板行") }
        rule.runOnIdle { h.resultFlow.value = "军师：先把事实摆一摆，别急着追问。" }
        rule.waitForIdle()
        val actions = laidChips(listOf(followUpChip, clearChip), "谈心面板·整组档·结果区")
        actions.forEach { assertLabelCentered(it, "谈心面板·整组档·结果区") }
        val group = templates + actions
        assertTrue(
            "这一格只量到 ${group.size} 颗谈心芯片（模板行 ${templates.size} 颗 + 结果区 ${actions.size} 颗）——" +
                "少于 3 颗就是没扫到整组，判据会在近乎空的上样绿：" + group.joinToString { it.describe() },
            group.size >= 3
        )
        val tallest = group.maxOf { it.heightDp }
        group.forEach { chip ->
            assertTrue(
                "${chip.label} 与整组不同档：它 ${chip.heightDp}dp，整组最高 ${tallest}dp" +
                    "（谈心这一族必须同一颗主人给的档）：" + chip.describe(),
                kotlin.math.abs(chip.heightDp - tallest) <= TIER_SPREAD_TOLERANCE_DP
            )
            assertTrue(
                "${chip.label} 超出谈心那一档（${AppDimens.CHIP_PANEL_HEIGHT_DP}dp + " +
                    "${TIER_SPREAD_TOLERANCE_DP}dp），像是把全局 48 下限又垫回来了：" + chip.describe(),
                chip.heightDp <= AppDimens.CHIP_PANEL_HEIGHT_DP + TIER_SPREAD_TOLERANCE_DP
            )
        }
    }

    /**
     * 旧台账第 12 条判的是**整行占位**，不是胶囊多高，所以这里量的不是那颗胶囊，而是
     * 「模板行在页面里真实吃掉的那一格」= 上面那颗输入框的**底** → 下面那颗 CTA 的**顶**。
     * 这一格不许比"那一排胶囊自己的带高 + 行自己那两颗间距 token"更厚。
     *
     * 反例（就是"不只把外观压成小 pill"点名的那种修法）：胶囊照旧小（`pillHeight` 没动），
     * 但**父行**被加了一层 `heightIn(min = …)` / 竖内边距 / 权重 ⇒ 看上去是小 pill、
     * 整行照旧占高 ⇒ 差额那句红。反向证人是同一条断言里的 `footprint >= band`：
     * 父行给得比胶囊还矮就是不成立的画法（读数没量到东西），一样红。
     */
    @Test
    fun `the template row occupies the pill tier plus its own spacing tokens and nothing more`() {
        // 草稿非空才让 CTA 带上点击语义（空草稿时它只是画出来的，语义树里没有可点的那颗）
        mount(fakeVm(draft = "她最近回得很慢"))
        val all = probe.actionableTargets(rule, "谈心面板·模板行占位")
        val cta = all.first { it.label.startsWith(ctaLabel) }
        // 上一颗锚点用 `SetText` 那一槽认（BasicTextField 一定有它），不用"可点击"去认：
        // 那颗输入框在 1.6.8 的语义树里报不报 Click 是组件内部的事，押上去这格就成了看天吃饭
        val input = probe.of(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().single())
        val chips = laidChips(allTemplates, "谈心面板·模板行占位")
        val band = chips.maxOf { it.topDp + it.heightDp } - chips.minOf { it.topDp }
        val footprint = cta.topDp - (input.topDp + input.heightDp)
        assertTrue(
            "模板行吃掉的这一格（${footprint}dp）比那一排胶囊自己的带高（${band}dp）还矮——" +
                "读数没量到这一行，别把它读成「没占高」",
            footprint >= band
        )
        assertTrue(
            "模板行整行占位 ${footprint}dp、胶囊带高只有 ${band}dp，多出的 ${footprint - band}dp " +
                "就是父行的余高（上限 = 行自己的间距 token 合计 ${ROW_SPARE_DP}dp）——" +
                "把胶囊压成小 pill 不算修完，那一排吃掉的高度才算：" +
                chips.joinToString { it.describe() },
            footprint - band <= ROW_SPARE_DP
        )
    }

    /**
     * 六颗模板各自的读数签名（左沿 + 宽度，合并树里那一层）。
     *
     * ⚠ 这里**不筛**"完整可见"：JVM 侧滚动容器给的裁切模型有两种（半截的报交集、滚干净的报
     * `0x0 @(0,0)`，本机两种都见过），只比"完整可见的那几颗"会因注入行程刚好不够多露一颗而
     * 忽红忽绿。比每一颗自己的左沿与宽度就只依赖一件事——**这一行确实挪了**。
     */
    private fun templateSignature(known: List<String>, screen: String): Map<String, Pair<Float, Float>> {
        val hit = probe.actionableTargets(rule, screen).filter { it.label in known }
        assertEquals(
            "$screen：合并树里该有这一族全部 ${known.size} 颗（滚出视口的也该在树里），实到 ${hit.size} 颗：" +
                hit.joinToString { it.describe() },
            known.size,
            hit.size
        )
        return hit.associate { it.label to (it.leftDp to it.widthDp) }
    }

    /**
     * §9「保留现有六个模板及点击填入功能，长模板可横滚」两条一起判：
     * ① 六条**一条不许少**（滚出视口的那几条照样在树里，读数被压成 0x0 不等于没画）；
     * ② 这一行**装不进**视口，而且一记横滑真的把它挪动了。
     *
     * 反例：换成普通 `Row` 让六颗一次全摆下 ⇒ ②前半句红（视口里一次就摆下 6 颗）；
     * 反例：`horizontalScroll` 掉了 / 换成换行 ⇒ 横滑前后六颗的左沿与宽度一模一样 ⇒ 后半句红；
     * 反例：模板被折叠成两条（旧版就是这么"解决"过一次的）⇒ ①红。
     */
    @Test
    fun `all six templates exist and one horizontal swipe really scrolls that row`() {
        mount(fakeVm())
        allTemplates.forEach { label ->
            val nodes = rule.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(
                "「$label」必须在树里且只出现一次（六条模板一条不许少、也不许画两遍）",
                1,
                nodes.size
            )
        }
        val before = templateSignature(allTemplates, "谈心面板·横滚前")
        val laidBefore = laidLabels(allTemplates, "谈心面板·横滚前")
        assertTrue(
            "视口里一次就摆下了 ${laidBefore.size}/6 颗模板：这一行要么被换成了换行/全部挤进一排，" +
                "要么根本没在横滚——两种都不满足「长模板可横滚」",
            laidBefore.size < allTemplates.size
        )
        rule.onAllNodes(hasText(allTemplates.first()))[0].performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val after = templateSignature(allTemplates, "谈心面板·横滚后")
        assertTrue(
            "横滑之后六颗模板的左沿与宽度一个都没变（before=$before / after=$after）：" +
                "父行里的 `horizontalScroll` 不在了，长模板就只能被裁掉",
            before != after
        )
    }

    private companion object {
        /**
         * 标签上隙与下隙允许的差（dp）：居中都留 8 的余量给字体行盒那点非对称留白；
         * 回退成 `neutral` 的 TopStart 时这一差撑到 ~20dp（`LbChipTierTest` 里那格判的是
         * `toBottom - toTop >= 6f` 才算贴顶），8 这一档既能放过居中的抖动、又一定判红贴顶。
         */
        const val CENTER_TOLERANCE_DP = 8f

        /** 挂载那一格的视口宽（与 [mount] 里的 `UiMatrix(...)` 同一颗数，别在筛样处写第二份） */
        const val VIEWPORT_WIDTH_DP = 360f

        /**
         * 整组档位的抖动余量（dp）：同族各颗都由 [com.lovebrain.app.core.designsystem.AppDimens.CHIP_PANEL_HEIGHT_DP]
         * 钉死，读数只差在描边/亚像素上；1dp 放过抖动，而"漏抄一句下限"那种回弹是 20dp，一定红。
         */
        const val TIER_SPREAD_TOLERANCE_DP = 1f

        /**
         * 模板行允许的「整行占位 − 胶囊带高」上限：就是这一行自己花掉的那两颗 `Spacing.xs`
         * （行前 Spacer + 上面那条分隔线的槽）再加一档 `Spacing.sm` 的抖动余量——
         * **由间距 token 算出来**，不是抄一个实测数：谁给父行加一层高度，这一档就包不住它。
         */
        val ROW_SPARE_DP: Float = (Spacing.xs + Spacing.xs + Spacing.sm).value
    }
}
