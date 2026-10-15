package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LB_SCREEN_HORIZONTAL_MARGIN
import com.lovebrain.app.core.designsystem.LbAsyncTags
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.ui.home.CaptureAppsScreen
import com.lovebrain.app.ui.home.ProviderSection
import com.lovebrain.app.ui.panel.OnboardingFlow
import com.lovebrain.app.viewmodel.SetupViewModel
import com.lovebrain.app.viewmodel.SetupViewModel.CaptureApp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 页面外框的结构门禁——`LbScreenScaffold` 负责四件事：页面背景、安全区、顶部栏、
 * **统一水平边距**；这一族只钉最后一件。
 *
 * 这一格只做一件事：**把"各页边距到底是多少"量出来**，而不是停在"各页仍各写自己的
 * `Box + background + padding`"那句定性判断上——方向没错，但没量过那几个 padding 具体差
 * 多少，而"统一水平边距"这一条是要能判达标/不达标的：量不出数，就永远算合格。
 *
 * ## 量法（读语义树，不是源码 grep）
 *
 * 从根往下走，取所有**宽到足以代表整行内容**的节点（宽度 > 槽位一半），
 * 取它们左边缘的**最大值** —— 那就是把内容往里推的那一档水平内边距。
 *
 * 为什么是最大而不是最小：第一版取最小，三格全报 **0dp**。因为最外层那个
 * `Box(fillMaxSize).background(SurfaceBase)` 本身就是"整行宽"的节点，左边缘自然是 0——
 * 量到的是**画布**，不是内容。改成取最大之后量的才是"被 padding 推进去的那一层"。
 *
 * 挂载槽统一 360dp 宽：同一把尺，各页才有可比性。
 *
 * ⚠ 一台仪器**一个用例只能 `setContent` 一次**（本仓库踩过三次的那条），
 * 所以"各页是否一致"不能写成"挂三页再比"，只能一页一格、
 * 每格都对着**同一个期望值**断言；哪一页跑偏就红在哪一格。
 *
 * ## 那颗锚点今天允许两种落位
 *
 * 语义树里没有普通布局容器，所以"这一页的水平边距"只能由**可点节点**报出来。
 * 一行如果把自己的横向内缩交给更里面那一层外框（组卡、白卡、列表框），这一页就没有任何可点节点
 * 坐在档本身那一格上——最靠左的那颗变成页头的返回钮，而返回钮自己带着页头那道缝
 * （`core/designsystem/LbTopBar.kt` 的 `TITLE_GAP_DP`，数从它那里现读，不抄在判据里）。
 * 两种落位都说明"外框那一档在生效"，所以判据读的是一个**区间**：档 ≤ 读数 ≤ 档 + 页头那道缝。
 * 比档还靠左 = 这一页没走脚手架那一档（或干脆顶到画布边）；比上限还靠右 = 整页被又埋了一层横向内缩。
 * 两头都由 `the margin judge still fails a page that lost its scaffold inset` 那一格注反例钉着，
 * 免得"放宽落位"这件事顺手把闸口本身拆掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenScaffoldFrameTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val slotWidthDp = 360f

    /** 读一句设计系统自己的档（见 [headerBackGapDp]）；与本仓其余源码级尺同一套定位写法 */
    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    /**
     * 页面自己的水平内边距 = **最左边那颗可点节点**的左边缘。
     *
     * 三个候选量法，前两个都被实测否掉了：
     * - 取所有"整行宽"节点的最小左边缘 ⇒ 三格全报 **0dp**。量到的是那个
     *   `Box(fillMaxSize).background(…)` 画布本身，不是内容。
     * - 排除左边缘为 0 的、再取最大 ⇒ 报 **74dp / 28dp**。原因是**语义树里根本没有
     *   普通布局容器**：`Row`/`Column` 不声明语义就不进树，所以"整行节点"实际是
     *   一些 Text，量到的是页面深处某个块的缩进，不是外框。
     * - ⇒ 用**可点节点**：页头那颗返回钮就落在页面被推进来的那个位置上，
     *   而且它是语义树里必然存在、边界清晰的一颗。
     *
     * 没有返回钮的页（首次引导）这一格量的是它最左的那颗按钮——
     * 所以量出来的数仍要一格一格对，不能拿"都是可点节点的最小左边缘"糊成一个定义。
     *
     * ⚠ 这颗锚点**不等于**内容那一格的左边缘：内容行的横向内缩现在归更里面那一层外框给
     * （行自己只垫纵向），于是"最左边那颗可点节点"可能落在页头返回那颗上，而它带着页头自己那道缝。
     * 合格区间见 [assertScaffoldMargin]，两头各有反例钉着。
     */
    private fun contentLeftInset(): Float {
        val probe = SemanticsProbe(density)
        // `laid` 那一层是反馈案例页逼出来的：那一页有 `horizontalScroll` 的芯片行，
        // 滚出视口的那颗在树里是 `0x0 @(0,0)`——不筛掉它，"最左边那颗可点节点的左边缘"
        // 就永远是 **0dp**，报出来的话是"这一页水平边距 0dp"，而它明明在 24 那一档。
        val targets = probe.laid(probe.actionableTargets(rule, "页面外框"))
        val leftMost = targets.minBy { it.leftDp }
        // 要说清量的是哪一颗，否则红了看不出是被哪个节点顶出来的数
        measuredAnchor = leftMost
        return leftMost.leftDp
    }

    private var measuredAnchor: SemanticsProbe.Target? = null

    private fun mount(content: @Composable () -> Unit) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) { content() }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /**
     * 等捕获页那一批 IO 读数落位（`LbAsyncState` 的 `LOADING` 节点消失）再往下量。
     * 本项目锁定的 Compose 版本里 `waitUntil` 不交回布尔，所以等完独立判一次：还在转圈就红在这里
     * （"批次没落"），而不是带着一屏半的布局去比边距、红成"这一页边距不对"的假话。
     * 判据与姊妹用例 `CaptureAppsScreenStatesTest.awaitCaptureBatch` 同一颗。
     */
    private fun awaitCaptureBatch() {
        rule.waitUntil(10_000L) {
            rule.onAllNodesWithTag(LbAsyncTags.LOADING).fetchSemanticsNodes().isEmpty()
        }
        val stillLoading = rule.onAllNodesWithTag(LbAsyncTags.LOADING).fetchSemanticsNodes().size
        assertTrue(
            "捕获页的 IO 批次在 10000ms 内没落位（转圈仍 $stillLoading 颗）：" +
                "这一格量的是整页外框，必须建立在「这一批已落」之上，不许带着 Loading 往下量",
            stillLoading == 0
        )
    }

    /** 与 `CaptureAppsScreenStatesTest` 同款：relaxed mock 扛不住泛型流，必须点名返回真流 */
    private fun captureVm(): SetupViewModel = mockk<SetupViewModel>(relaxed = true).also {
        every { it.captureAllowedPackages } returns MutableStateFlow(setOf("PACKET_ALFRED"))
        // CaptureAppsScreen 读 captureEnabled（StateFlow<Boolean>）：relaxed 桩对泛型流会返回
        // 一颗 mock 流，其 .value 是 Object，`by collectAsStateWithLifecycle()` 强转 Boolean 当场炸
        // （ClassCastException: Object cannot be cast to Boolean）。与 `PageHeaderConsistencyTest` 同一笔账，显式钉桩。
        every { it.captureEnabled } returns MutableStateFlow(false)
        every { it.selectableCaptureTargets(any()) } returns listOf(
            CaptureApp(packageName = "PACKET_ALFRED", displayName = "LABEL_ALFRED", secondRejected = false)
        )
    }

    /** 与 `ProviderSectionSemanticsTest` 同款 */
    private fun providerVm(): SetupViewModel = mockk<SetupViewModel>(relaxed = true).also {
        every { it.tickets } returns MutableStateFlow(emptyList<ProviderTicket>())
        every { it.activeTicket } returns MutableStateFlow<ProviderTicket?>(null)
        every { it.providerReady } returns MutableStateFlow(false)
    }

    /**
     * 页头那颗返回自己带的横向缝（`LbTopBar` 的 `TITLE_GAP_DP`）。
     * **数从主人那里现读**，不抄在判据里：页头哪天改了这两道缝，这几格跟着一起动，
     * 而不是靠有人记得回来改测试里的数。读不到就抛——"读不出数"绝不被读成"很干净"。
     */
    private val headerBackGapDp: Float
        get() {
            val file = File(appRoot, "core/designsystem/LbTopBar.kt")
            assertTrue(
                "$file 不在了——页头那颗返回的来路读不到，这一族四格全部失去意义",
                file.isFile
            )
            val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
            val gap = Regex("""\bTITLE_GAP_DP\s*=\s*(\d+)""").find(code)?.groupValues?.get(1)?.toFloatOrNull()
            return gap ?: error(
                "$file 里读不到 TITLE_GAP_DP = <数>——这把尺已经看不见页头那道缝，" +
                    "读不出数不许被读成\"这页边距没问题\""
            )
        }

    /** 锚点允许的两种落位（[assertScaffoldMargin]），容差沿用这一族一直用的那半像素 */
    private val anchorToleranceDp = 0.6f

    /**
     * 读数判定：**合格回 null，不合格回一句说得出坏在哪的话**。
     *
     * 判据本身抽成一颗纯函数，是因为认法换了之后，"这一格还拦不拦得住真丢了脚手架边距的页"
     * 必须能被注数试出来，而不是靠四页各自的绿来许愿（见
     * `the margin judge still fails a page that lost its scaffold inset`）。
     *
     * 两种落位都算"外框那一档在生效"：
     *  - **档本身**：这一页最左那颗可点节点直接坐在脚手架推进来的那条线上；
     *  - **档 + 页头那道缝**：这一页内容里的可点件都归更里面一层外框给内缩（行不再自带横向 padding），
     *    于是最靠左的那颗变成页头的返回钮——它照样由同一档管着，只是页头自己加了一道缝。
     * 比档还靠左 ⇒ 这一页没走脚手架那一档（或直接顶到画布边）；比上限还靠右 ⇒ 整页被又埋了一层。
     */
    private fun frameReadingFault(measuredLeftDp: Float): String? {
        val margin = ExpectedPageMarginDp
        val ceiling = margin + headerBackGapDp
        return when {
            measuredLeftDp < margin - anchorToleranceDp ->
                "比脚手架那一档 ${margin.toInt()}dp 还靠左（实到 ${measuredLeftDp.toInt()}dp）：" +
                    "这一页要么没走那一档、要么内容顶到了画布边"
            measuredLeftDp > ceiling + anchorToleranceDp ->
                "比 ${ceiling.toInt()}dp（档 ${margin.toInt()} + 页头那道缝 ${headerBackGapDp.toInt()}）" +
                    "还靠右（实到 ${measuredLeftDp.toInt()}dp）：整页被又埋了一层横向内缩"
            else -> null
        }
    }

    private fun assertScaffoldMargin(page: String, measured: Float) {
        val fault = frameReadingFault(measured)
        assertTrue(
            "$page 的水平边距：可点锚点应当坐在脚手架那一档 ${ExpectedPageMarginDp.toInt()}dp" +
                "（或页头返回那颗的 +${headerBackGapDp.toInt()}dp 落位）上，" +
                "实到 ${measured.toInt()}dp（量的是这颗节点：" + (measuredAnchor?.describe() ?: "没量到") + "）" +
                (fault?.let { " —— $it" } ?: "") +
                "：对不上说明这一页还在自己写外框，或被又埋了一层横向内缩",
            fault == null
        )
    }

    /** 走 `ScreenPage`（它已经是"半个脚手架"）的那一家 */
    @Test
    fun `the ScreenPage family insets content by the scaffold margin`() {
        mount { CaptureAppsScreen(viewModel = captureVm(), onBack = {}) }
        // 这一页的安装包枚举挂在 `LaunchedEffect` 的 `Dispatchers.IO` 上（页面因此有真正的
        // Loading 那一格），而 `rule.waitForIdle()` / 上面那 16ms 手动推帧**都不跟踪后台线程上的
        // 协程**——机器一忙，`contentLeftInset()` 就在批次落位前读语义树，量到的是半屏（页头还没
        // 和最左可点行对齐）。同族那颗 `CaptureAppsScreenStatesTest` 已在 CI 上为同一件事红过，
        // 用的是同一条确定性等待：等 `LbAsyncState` 的 `LOADING` 标签节点消失（批次落）再量。
        awaitCaptureBatch()
        assertScaffoldMargin("捕获范围页", contentLeftInset())
    }

    /** 自己拼 `Column + padding(horizontal = …)` 的那一家 */
    @Test
    fun `the hand-rolled provider page insets content by the scaffold margin`() {
        mount { ProviderSection(viewModel = providerVm(), onBack = {}) }
        assertScaffoldMargin("供应商页", contentLeftInset())
    }

    /** 又一整套根（首次引导）：它连 insets 都自己加了一遍 */
    @Test
    fun `the onboarding root insets content by the scaffold margin`() {
        mount { OnboardingFlow(onSkip = {}, onComplete = {}, onOpenSettings = {}) }
        assertScaffoldMargin("首次引导", contentLeftInset())
    }

    /**
     * 反馈案例页——这一页的水平边距**曾经是自己写的一档 `Spacing.lg` = 12dp**（当时量到 12）。
     * 现在它的外框交给脚手架，所以这一格留着的理由是"别再回到自己写一档"：12dp 那种写法
     * 已经被 [frameReadingFault] 里"自己写一档 12dp"那颗反例专门拦着，两句钉的是同一个坏形状
     * 的正面与反面。
     *
     * 期望值仍从脚手架那档读（[ExpectedPageMarginDp]），不在这格写 24——写死 24 就成了测试替
     * 脚手架许愿：那档哪天改成 20，这一格会红着说页面错了，而错的其实是还在照旧数读的尺。
     *
     * 到期日：这一页若整个改版、连"自己画过外框"这段形状都不再需要钉，这一格连同反例里
     * 那档 12dp 一起删掉，别让它变成常驻的第五次边距读数。
     */
    @Test
    fun `the feedback cases page insets content by the scaffold margin`() {
        val vm = mockk<SetupViewModel>(relaxed = true).also {
            every { it.feedbackCases } returns MutableStateFlow(
                listOf(
                    com.lovebrain.app.model.FeedbackCase(
                        caseId = "c1", schemeIdentityKey = "STYLE:B",
                        candidateReply = "那你想我怎么做",
                        categories = listOf(com.lovebrain.app.model.FeedbackCategory.UNDERSTANDING_ERROR),
                        reasons = listOf("角色错")
                    )
                )
            )
            every { it.feedbackLoading } returns MutableStateFlow(false)
            every { it.feedbackError } returns MutableStateFlow<String?>(null)
            every { it.exportState } returns MutableStateFlow<SetupViewModel.ExportState>(
                SetupViewModel.ExportState.Idle
            )
        }
        mount { com.lovebrain.app.ui.feedback.FeedbackCasesScreen(viewModel = vm, onBack = {}) }
        assertScaffoldMargin("反馈案例页", contentLeftInset())
    }

    /**
     * 换了认法，就得当场证明这把尺还拦得住"真的丢了脚手架边距的页"——这一格就是认法变更自己的反向证人。
     *
     * 判据是拿 [frameReadingFault] 本体跑的（四格真页走的同一颗函数），注的是**读数**：
     * - 该放的两颗必须放：正好坐在档上、以及档 + 页头那道缝（今天捕获页就是后一种）；
     * - 该拦的三颗必须拦，而且要说得出坏在哪：顶到画布边（0dp）、自己写一档 12dp 的旧写法、
     *   以及整页被又埋一层（超过上限）。
     * 顺带钉住"页头那道缝不许读成 0"——缝读成 0 的话这两种落位合成一种，判据自己就废了。
     */
    @Test
    fun `the margin judge still fails a page that lost its scaffold inset`() {
        val margin = ExpectedPageMarginDp
        val gap = headerBackGapDp
        assertTrue("页头那道缝读成 $gap ⇒ 区间塌成一个点，这一格已经没有可放的落位", gap > 0f)
        val ceiling = margin + gap

        assertNull("坐在档本身的那一颗必须判合格", frameReadingFault(margin))
        assertNull("坐在档 + 页头缝的那一颗（今天捕获页的返回钮）必须判合格", frameReadingFault(ceiling))
        assertNotNull("顶到画布边（0dp）必须拦下", frameReadingFault(0f))
        assertNotNull("自己写一档 12dp、没走脚手架的页必须拦下", frameReadingFault(margin - 12f))
        assertNotNull("比上限还埋深一档的页必须拦下", frameReadingFault(ceiling + 8f))
        listOf(0f, margin - 12f, ceiling + 8f).forEach { bad ->
            val fault = frameReadingFault(bad)
            assertTrue("拦下 $bad 那一步要说得出坏在哪，不许回一句空话：实到「$fault」", !fault.isNullOrBlank())
        }
    }

    companion object {
        /**
         * 期望的那一档水平边距——**读脚手架自己的常量**，不是测试里手写的数。
         *
         * 第一版这里写死 `24f`。写完 `LbScreenScaffold` 之后如果还留着 24f，
         * 那么"统一水平边距"就变成**我在测试里许愿**：哪天有人把脚手架那档改成 20，
         * 这几格会红着告诉他"页面错了"，而错的其实是尺子还在照着旧数读。
         * 更要紧的是另一头：像 `ProviderSection` 那样**自己拼** `padding(xxxl)` 的页，
         * 今天恰好也是 24dp，所以这三格**绿并不能证明它走了脚手架**——
         * 那件事由 `UiLayerDependencyContractTest` 里"页面外框只有一个所有者"那格判。
         * 两把尺各管各的，别拿这一把的绿去说那一把的话。
         */
        val ExpectedPageMarginDp: Float = LB_SCREEN_HORIZONTAL_MARGIN.value
    }
}
