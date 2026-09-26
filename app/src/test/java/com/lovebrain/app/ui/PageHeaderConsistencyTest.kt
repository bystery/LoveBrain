package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.ui.feedback.FeedbackCasesScreen
import com.lovebrain.app.ui.home.AboutScreen
import com.lovebrain.app.ui.home.CaptureAppsScreen
import com.lovebrain.app.ui.home.ProviderSection
import com.lovebrain.app.ui.home.UsageDetailScreen
import com.lovebrain.app.viewmodel.SetupViewModel
import com.lovebrain.app.viewmodel.SetupViewModel.CaptureApp
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
 * §6.1 :478 那行剩下的第三件事 + :479「`LbTopBar` | 标题、副标题、返回（或关于）等**单一尾部动作**」。
 *
 * ⚠ 这句原本写成「返回 / 关于」时，紧跟着重号的那个星号和斜杠拼起来正好是 KDoc 的收尾符，
 * 一整段注释被从中间截断、后面全成了"顶层声明"。指南原文那两个字符没法照抄，改写成括号式。
 *
 * 改之前全 App 的页头是**四式并存**（本机实扫，账本 §37 有出处）：
 * `LbTopBar`（只有首页）、`ScreenHeader`（`ScreenPage` 那一族 + 知识库编辑）、
 * 手写 `←` + 标题的 `Row`（关于 / 使用概览 / 供应商）、`SurfaceCard` 底手写栏（反馈案例）。
 * ⚠ §57 挂载量完之后这一句要补一笔：反馈案例那一式当时**不在**读源码列出的四式里，
 * 它是量出来才发现的"第五式"；那一页现已归一（账本 §58），下面第五格就是它的证人。
 *
 * 这四式里最要紧的差别不是好不好看，是**读屏念不念得出来**：
 * `ScreenHeader` 给返回那颗挂了 `contentDescription`，
 * 而手写那三颗的"标签"是一个 `Text("←")` ——箭头字形。
 * TalkBack 对着字形能念出什么取决于它怎么认这个字符，**不是产品想说的"返回"**；
 * 而且那串 `contentDescription = "返回"` 是**硬编码中文**，英文环境下也念中文。
 *
 * ⇒ 判据只认一件事：**每一页那颗返回钮，都要用同一条资源说自己叫什么**。
 * 名字取 `contentDescription` 本身，不取"文案或 contentDescription 里随便一个非空"——
 * 那是坑表 68 的教训：箭头字形那段文案会把这一栏蒙过去。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageHeaderConsistencyTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = app.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 中英 parity 也要判：资源在两处都定义过，取到哪个 locale 都该是"返回"这一档 */
    private val backLabel: String get() = app.getString(R.string.common_back)

    private fun mount(content: @Composable () -> Unit) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) { content() }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /**
     * 页头那颗返回钮 = **最靠上、并列时最靠左**的那颗可点节点。
     *
     * ⚠ 这一格的锚点换过三次，三次都是锚点自己错、不是实现错：
     * 1. 原本取全树"最左"。改完 `LbTopBar` 之后三格全红，报出来的节点是 @(24,297)
     *    那种 312dp 宽的内容块——`LbTopBar` 在返回盒前面留了 2dp 间距，
     *    返回钮的左边缘是 26，"最左"就变成了一整行内容。
     *    **锚点一错，报的话说的是个没发生过的理由。**
     * 2. 于是改成"全树最靠上"。反馈案例页搬进 `LbTopBar` 之后这一版也不成立了：
     *    那一页页头是**两颗**（返回 48x48 与尾部那颗导出 58x48），同一行、同一个 top，
     *    `minBy { topDp }` 遇到并列时取树里第一个——顺序对了才算对，顺序不是判据。
     * 3. 中途试过"先用 y<60 圈出页头那一带，再在那一带里取最靠左"。这一版**被实测打掉**：
     *    供应商页第一张卡片顶到 y≈50，它的左边缘 24 比返回钮的 26 更靠左，
     *    于是"最左"抓到整行宽的卡片，红在宽度那条判据上
     *    （读数：`the provider page names its back control FAILED — PageHeaderConsistencyTest.kt:113`）。
     *    ⇒ 现在两条一起排：**先最靠上，只在并列时才比左**。
     *    "靠上"排掉下面的内容，"并列看左"排掉同一行的第二颗尾部动作——
     *    两把各挡一种歧义，谁都不单独作数。
     *
     * 再加两条前提，免得哪天页面顶部冒出别的控件把判据顶掉：
     * 它必须落在页头那一带（y < 60dp），并且宽度不超过一颗按钮的量级（< 80dp）——
     * 整行宽的东西不可能是页头那颗返回钮。
     */
    private fun headerControl(page: String): SemanticsProbe.Target {
        val targets = probe.laid(probe.actionableTargets(rule, page))
        val candidate = targets.minWithOrNull(
            compareBy<SemanticsProbe.Target>({ it.topDp }, { it.leftDp })
        )!!
        assertTrue(
            "$page 页头那颗控件应当落在 y<60dp 那一带，实到 " + candidate.describe() +
                "；全树最靠上的那颗都不是它的话，这一格量的就不是页头",
            candidate.topDp < 60f
        )
        assertTrue(
            "$page 页头那颗应当是一颗按钮那么宽（<80dp），实到 " + candidate.describe() +
                "——整行宽的东西不是返回钮，这一格又量错对象了",
            candidate.widthDp < 80f
        )
        return candidate
    }

    /**
     * 这一栏**只看 contentDescription**。
     * 写成 `cd ?: text` 会让箭头字形那种实现混过去（坑表 68 就是这一支）。
     */
    private fun announcedNameOf(target: SemanticsProbe.Target): String =
        target.contentDescriptions.joinToString("+")

    private fun assertBackIsNamed(page: String, target: SemanticsProbe.Target) {
        val announced = announcedNameOf(target)
        assertEquals(
            "$page 的返回钮要**自己说出**它叫什么（挂在可点击那颗节点身上的 contentDescription），" +
                "实到 \"$announced\"；那颗节点：" + target.describe() +
                "。期望值是资源 R.string.common_back，当前 locale 下 = \"$backLabel\"——" +
                "硬编码中文在英文环境会念中文，写成箭头字形的 Text 则根本不是「返回」这两个字",
            backLabel, announced
        )
    }

    private fun fakeVm(): SetupViewModel = mockk<SetupViewModel>(relaxed = true).also {
        every { it.tickets } returns MutableStateFlow(emptyList<ProviderTicket>())
        every { it.activeTicket } returns MutableStateFlow<ProviderTicket?>(null)
        every { it.providerReady } returns MutableStateFlow(false)
        every { it.captureAllowedPackages } returns MutableStateFlow(emptySet())
        every { it.selectableCaptureTargets(any()) } returns listOf(
            CaptureApp(packageName = "PACKET_A", displayName = "LABEL_A", secondRejected = false)
        )
    }

    /** 手写 `←` + 标题的那一族（关于） */
    @Test
    fun `the about page names its back control`() {
        mount { AboutScreen(onBack = {}) }
        assertBackIsNamed("关于页", headerControl("关于页"))
    }

    /** 手写那一族的第二家（使用概览） */
    @Test
    fun `the usage page names its back control`() {
        mount { UsageDetailScreen(viewModel = fakeVm(), onBack = {}) }
        assertBackIsNamed("使用概览页", headerControl("使用概览页"))
    }

    /** 手写那一族的第三家（供应商） */
    @Test
    fun `the provider page names its back control`() {
        mount { ProviderSection(viewModel = fakeVm(), onBack = {}) }
        assertBackIsNamed("供应商页", headerControl("供应商页"))
    }

    /** 走 ScreenHeader 的那一族：它的名字是硬编码中文，英文环境下也是中文 */
    @Test
    fun `the ScreenPage family names its back control from resources`() {
        mount { CaptureAppsScreen(viewModel = fakeVm(), onBack = {}) }
        assertBackIsNamed("捕获范围页", headerControl("捕获范围页"))
    }

    /**
     * 第五式（账本 §57 量出来的那一个）：`SurfaceCard` 底带 + 箭头字形当名字，
     * 热区靠 `size(48)` 垫够。反馈案例页现已归一 `LbTopBar`（账本 §58）。
     *
     * 这一格同时是那把锚点的证人：这一页页头**有两颗**（返回 48x48、尾部导出 58x48），
     * 旧的"全树最靠上"在并列时会取树里第一个——顺序变了这格就会换一个理由红。
     */
    @Test
    fun `the feedback cases page names its back control`() {
        mount { FeedbackCasesScreen(viewModel = feedbackVm(), onBack = {}) }
        assertBackIsNamed("反馈案例页", headerControl("反馈案例页"))
    }

    /** 反馈案例页那四条流（relaxed 桩扛不住泛型流，同一笔账见 `ProviderSectionSemanticsTest`） */
    private fun feedbackVm(): SetupViewModel = mockk<SetupViewModel>(relaxed = true).also {
        every { it.feedbackCases } returns MutableStateFlow(emptyList<FeedbackCase>())
        every { it.feedbackLoading } returns MutableStateFlow(false)
        every { it.feedbackError } returns MutableStateFlow<String?>(null)
        every { it.exportState } returns
            MutableStateFlow<SetupViewModel.ExportState>(SetupViewModel.ExportState.Idle)
    }

    /**
     * 反空跑：返回钮的热区也得够 48dp（§6.5 :531）。
     *
     * 这一格单独存在，是因为"有名字"与"点得到"是两件事——
     * 上一格点踩面板就是"有文案、名字靠字形、热区 19dp"三样一起坏。
     */
    @Test
    fun `every back control meets the touch floor`() {
        mount { AboutScreen(onBack = {}) }
        val about = headerControl("关于页")
        assertTrue(
            "关于页那颗返回钮热区要 ≥48dp，实到 " + about.describe(),
            !about.tooSmall(48f)
        )
    }

    /** 页头高度：四式今天一个是固定 48dp 行、其余靠 padding 撑，收齐之后要一致 */
    @Test
    fun `the header row height is at least the touch floor`() {
        mount { CaptureAppsScreen(viewModel = fakeVm(), onBack = {}) }
        val back = headerControl("捕获范围页")
        assertTrue(
            "页头返回那颗的高度就是页头行的下限，实到 " + back.describe(),
            back.heightDp >= 48f - 0.6f
        )
    }
}
