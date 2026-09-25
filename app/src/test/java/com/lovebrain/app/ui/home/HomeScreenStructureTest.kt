package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbStatusTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.service.FloatingService
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * §6.2 首页四段结构的第一条**自动**守卫（此前这一条完全靠人眼看）。
 *
 * 指导书那四段是逐条判的，不是"页面能画出来"：
 * 1. 顶部：LoveBrain + 一句价值说明 + 右侧 About；
 * 2. 军师状态主卡：badge、简短说明、**唯一**主按钮，隐藏图标**只在可隐藏时**出现于**右上角**；
 * 3. 快捷功能：知识库与反馈案例用同一颗卡片组件；
 * 4. 设置与使用概览：两行设置行 + 统计三等分。
 * 另有一句负向的：「不要把 Provider 编辑器、反馈案例列表、捕获 App 清单展开在首页」。
 *
 * 锚点用 tag 不用中文（见 `LbHomeTags` 的注释）；位置判据读 `boundsInRoot`——
 * "右上角"、"三等分"这两句话本来就只能用坐标来判。
 *
 * 两个入口（`overlayGrantedOverride` / `serviceRunningOverride`）默认值就是原来的读法，
 * 这里把它们摆成四种组合，是为了第 2 段那句"只在可隐藏时"——不摆就没人能证明它。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenStructureTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private val granted: MutableState<Boolean> = mutableStateOf(true)
    private val running: MutableState<Boolean> = mutableStateOf(true)

    @After
    fun tearDown() {
        // 进程内单例不许漏到下一个用例
        FloatingService.setWindowState(FloatingService.WindowState.STOPPED)
    }

    private fun vm(accessible: Boolean): SetupViewModel =
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
            every { it.captureAllowedPackages } returns MutableStateFlow(setOf("com.a"))
            every { it.isCaptureServiceEnabled(any()) } returns accessible
        }

    private fun mount(accessible: Boolean = true) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                HomeScreen(
                    viewModel = vm(accessible),
                    onStartService = {}, onOpenPanel = { _, _ -> }, onTempHide = {},
                    onRestore = {}, onNavigateFeedback = {}, onNavigateAbout = {},
                    onNavigateProviders = {}, onNavigateUsage = {}, onNavigateCaptureApps = {},
                    onBack = {},
                    overlayGrantedOverride = granted.value,
                    serviceRunningOverride = running.value
                )
            }
        }
        rule.waitForIdle()
    }

    /** 统一走未合并树：tag 打在哪个节点上，就一定在那一个节点查得到，不受父容器合并语义影响 */
    private fun tagCount(tag: String) =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun top(tag: String): Float = topLevel(tag).boundsInRoot.top / density

    /** 取顶层那一个：合并树里它就是那个带 tag 的节点；未合并树里带同名 tag 的父节点排在最前 */
    private fun topLevel(tag: String) =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()

    private fun setCombo(ov: Boolean, sv: Boolean, window: FloatingService.WindowState) {
        granted.value = ov
        running.value = sv
        FloatingService.setWindowState(window)
        rule.runOnIdle { }
        rule.waitForIdle()
    }

    /** ① + ④：四段的**上下顺序**与数量 */
    @Test
    fun `the four segments sit in the order the guide fixes`() {
        mount()
        // 未合并树里父链会带着同一个 tag 重复出现，所以按**不同的 top 坐标**数分区，
        // 而不是数节点个数——"有几个分区标题"这件事本来就是位置事实
        val sections = rule.onAllNodesWithTag(LbHomeTags.SECTION, useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot.top }.distinct().size
        assertEquals("分区标题应当是 3 个（快捷功能 / 服务设置 / 使用概览），实到 $sections", 3, sections)

        val card = top(LbHomeTags.STATUS_CARD)
        val firstSection = rule.onAllNodesWithTag(LbHomeTags.SECTION)
            .fetchSemanticsNodes().minOf { it.boundsInRoot.top / density }
        val secondSection = rule.onAllNodesWithTag(LbHomeTags.SETTING_ROW, useUnmergedTree = true)
            .fetchSemanticsNodes().minOf { it.boundsInRoot.top / density }
        val metric = top(LbHomeTags.METRIC_CELL)

        assertTrue("主卡要在第一个分区之上：主卡 $card，分区标题 $firstSection", card < firstSection)
        assertTrue("快捷功能卡片在服务设置之上", firstSection < secondSection)
        assertTrue("统计在最末（使用概览段）", secondSection < metric)
        // 顶部段：About 那颗在页头里（HomeTopBar 自带 48dp 热区），量得到就说明第一段没被搬走
        assertEquals("首页顶部那颗 About 入口", 1, tagCount(LbHomeTags.ABOUT))
        assertTrue("About 必须在主卡之上（第一段没被搬走）",
            top(LbHomeTags.ABOUT) < top(LbHomeTags.STATUS_CARD))
    }

    /** ②：唯一主按钮；隐藏图标只在可隐藏时出现，且真的在卡片右上角 */
    @Test
    fun `the status card holds exactly one primary button and a conditional hide entry`() {
        mount()
        assertEquals("状态卡里的主按钮必须唯一", 1, tagCount(LbHomeTags.PRIMARY_BUTTON))
        assertEquals("服务在跑但窗口是 STOPPED：不该给隐藏入口", 0, tagCount(LbHomeTags.HIDE_BUTTON))

        // 只有"球或面板可见"才算可隐藏（§6.2 那句"只在可隐藏时"）
        setCombo(true, true, FloatingService.WindowState.VISIBLE_BUBBLE)
        assertEquals("可隐藏时该出现右上角那颗", 1, tagCount(LbHomeTags.HIDE_BUTTON))
        val hide = topLevel(LbHomeTags.HIDE_BUTTON)
        val card = topLevel(LbHomeTags.STATUS_CARD).boundsInRoot
        val d = density
        assertTrue(
            "隐藏图标必须在卡片右上角：hide=(${hide.boundsInRoot.left / d},${hide.boundsInRoot.top / d}) " +
                "card=(${card.left / d},${card.top / d},${card.right / d})",
            hide.boundsInRoot.left / d > card.right / d - 80 &&
                hide.boundsInRoot.top / d < card.top / d + 60
        )

        setCombo(true, true, FloatingService.WindowState.TEMP_HIDDEN)
        assertEquals("已经隐藏了就不该再有隐藏入口", 0, tagCount(LbHomeTags.HIDE_BUTTON))

        setCombo(true, false, FloatingService.WindowState.STOPPED)
        assertEquals("服务没跑：隐藏入口不该在", 0, tagCount(LbHomeTags.HIDE_BUTTON))
        assertEquals("四段结构不随状态消失——主按钮仍然唯一", 1, tagCount(LbHomeTags.PRIMARY_BUTTON))
    }

    /** ③：两个快捷功能入口是同一颗组件（同一 tag），都可点 */
    @Test
    fun `quick actions are two of the same card and both are actionable`() {
        mount()
        val all = rule.onAllNodesWithTag(LbHomeTags.ACTION_CARD).fetchSemanticsNodes()
        assertEquals("快捷功能必须是 2 张同颗组件的卡片，实到 ${all.size}", 2, all.size)
        // 可点性读语义树上的 OnClick **动作**（SemanticsActions，不是 Properties）
        val clickable = all.count { it.config.contains(SemanticsActions.OnClick) }
        assertEquals("两张卡片都要整卡可点，实到 $clickable", 2, clickable)
    }

    /** ④：两行设置项 + 统计**三等分**（等分就用坐标判） */
    @Test
    fun `settings hold two rows and the metrics split the row into three equal cells`() {
        mount()
        assertEquals("模型供应商 + 消息捕获两行", 2, tagCount(LbHomeTags.SETTING_ROW))

        val cells = rule.onAllNodesWithTag(LbHomeTags.METRIC_CELL, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("统计必须是三格", 3, cells.size)
        val widths = cells.map { it.boundsInRoot.width / density }
        val spread = widths.max() - widths.min()
        assertTrue("三等分不是修辞：实测宽度 $widths，差 ${spread}dp", spread < 1.5f)
    }

    /** 负向那句：不把 Provider 编辑器 / 反馈列表 / 捕获清单展开在首页 */
    @Test
    fun `nothing that belongs to a sub-screen is expanded on home`() {
        mount()
        // 捕获 App 清单的标志是勾选框；Provider 编辑器与反馈列表的标志是可输入控件
        val checkboxes = rule.onAllNodes(
            SemanticsMatcher("带 Checkbox 角色") { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox
            }
        ).fetchSemanticsNodes().size
        assertEquals("首页不该出现捕获清单的勾选框", 0, checkboxes)
        assertEquals("首页不该出现展开的编辑器输入框", 0,
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        // 反向确认这把尺看得见东西：整页是可滚的，节点数不会太小
        assertTrue("整棵树只有几个节点 ⇒ 这格大概什么也没测到",
            rule.onAllNodes(SemanticsMatcher("任何节点") { true }).fetchSemanticsNodes().size > 20)
    }
}
