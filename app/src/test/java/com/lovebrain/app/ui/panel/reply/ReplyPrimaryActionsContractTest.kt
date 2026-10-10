package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbButtonHeightTier
import com.lovebrain.app.core.designsystem.LbTags
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ComposerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/**
 * 第2节第1条 那张"交互合同应永久钉死"的表，钉在能**真跑**的地方。
 *
 * 为什么在 JVM 再写一遍 androidTest 已经写过的场景：那批 instrumentation 目前有 19 条
 * 真失败、且要 CI 的 emulator 才跑得动，等于"合同没有守卫"。这条走 `testDebugUnitTest`，
 * CI 的 verify job 每次都跑，本机也能跑。两边都留着，不互相替换。
 *
 * 合同四行 + 一条通则：
 * 1. REPLY、无结果 → 全宽「生成回复 · N条消息」，N=0 时禁用（但仍在那儿，不是消失）
 * 2. REPLY、有结果 → 「重试」/「记入知识库」两颗，主动发不许占这个位置
 * 3. PROACTIVE、空闲 → 全宽「生成开场」
 * 4. 任一生成中 → 唯一停止入口
 *
 * **尺寸那一面按档量，不按全局下限量。** [probe] 是默认档那把尺（48dp 下限，组件自己的四态由
 * `LbPrimaryButtonStateTest` 钉着），[probeAt] 是某一档那把尺——两条边仍从 `boundsInRoot` 读，
 * 再把读到的可见高度对到档位上。于是三种坏实现都会红：
 * 热区从带 clickable 的那个节点上挪走（读到的是内容那 26dp）、档位填错（40 写成 48 或 36 写成 40）、
 * 以及把矮的那一档做成"外层垫高、点击仍挂在矮节点"（外层不算可交互节点，读出来的还是矮的那颗）。
 * 这一屏的两档比全局下限矮，是设计系统里明写的例外（理由写在 `LbButtonHeightTier` 与
 * `AppDimens.PANEL_*` 那两颗的说明里）；全局那颗下限本身没动，仍由
 * `the global touch floor itself is declared at least 48dp` 与 `LbPrimaryButtonStateTest` 钉着。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyPrimaryActionsContractTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /** 文案走资源取，不抄第二份某一种语言的字面量（本项目已收成单一中文资源） */
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val generateReplyLabel: String get() = ctx.getString(R.string.panel_generate_reply)
    private val generateReplyWithCount: (Int) -> String get() = { n -> ctx.getString(R.string.panel_generate_reply_with_count, n) }
    private val generateOpenerLabel: String get() = ctx.getString(R.string.panel_generate_opening)
    private val retryLabel: String get() = ctx.getString(R.string.panel_retry)
    private val saveToKbLabel: String get() = ctx.getString(R.string.panel_save_to_kb)

    /**
     * 某一**档**的尺：`SemanticsProbe` 的两条边仍然真读 `boundsInRoot`，这里只是把尺换成那一档的数。
     *
     * 下限本身没有被放宽——默认档那把尺（[probe]，48dp）仍在 `LbPrimaryButtonStateTest`、
     * `UiMatrixFullSweepTest` 那些格子上生效；这一格说的是"面板这一排落在具名档上"。
     */
    private fun probeAt(tier: LbButtonHeightTier) =
        SemanticsProbe(density, tier.minHeightDp.toFloat())

    /** 读到的可见高度必须正好是那一档：太矮 = 热区从 clickable 节点上掉了，太高 = 档没接上 */
    private fun assertTierHeight(tier: LbButtonHeightTier, target: SemanticsProbe.Target) = assertEquals(
        "这一档的可见高度应该是 ${tier.name} = ${tier.minHeightDp}dp，实测 " + target.describe(),
        tier.minHeightDp.toFloat(), target.heightDp, 0.6f
    )

    private val matrix = UiMatrix(360)

    private fun mount(
        composerMode: ComposerMode,
        isGenerating: Boolean = false,
        isProactive: Boolean = false,
        hasReplyResult: Boolean = false,
        messageCount: Int = 0
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                ReplyPrimaryActions(
                    composerMode = composerMode,
                    isGenerating = isGenerating,
                    isProactive = isProactive,
                    hasReplyResult = hasReplyResult,
                    messageCount = messageCount,
                    onGenerateReply = {},
                    onGenerateProactive = {},
                    onRetry = {},
                    onSaveToKb = {},
                    onStop = {}
                )
            }
        }
    }

    /** 合同第 1 行：REPLY 无结果 = 全宽「生成回复 · N条消息」，高度落在面板主动作那一档 */
    @Test
    fun `reply mode with no result shows one full width generate button carrying the count`() {
        mount(ComposerMode.REPLY, messageCount = 3, hasReplyResult = false)
        // 判的是 [LbButtonHeightTier.PanelPrimaryAction] 这一档（生成 / 生成中 / 停止共用那颗槽位）：
        // 两条边都得垫到这一档，且读到的可见高度正好是这一档，不是默认档的 48。
        val tier = LbButtonHeightTier.PanelPrimaryAction
        val targets = probeAt(tier).assertAllActionableMeetTouchFloor(rule, "主操作区")
        assertEquals(
            "无结果时主操作区只该有一颗全宽生成按钮：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        assertEquals(generateReplyWithCount(3), targets.single().label)
        assertTierHeight(tier, targets.single())
        assertTrue(
            "按钮应当铺满这一行的宽度（360dp 槽位），实测 " + targets.single().describe(),
            targets.single().widthDp > 300f
        )
    }

    /** 合同第 1 行的后半：N=0 时禁用——是"灰着不能点"，不是"没了" */
    @Test
    fun `with zero captured messages the generate button stays visible but disabled`() {
        mount(ComposerMode.REPLY, messageCount = 0, hasReplyResult = false)
        val targets = probe.actionableTargets(rule, "主操作区")
        assertEquals(
            "N=0 时按钮必须还画得出来（只是不能点），消失就不是同一份合同了：" +
                targets.joinToString { it.describe() },
            1, targets.size
        )
        assertEquals(generateReplyLabel, targets.single().label)
        assertTrue("N=0 时按钮必须带 disabled 语义：" + targets.single().describe(), targets.single().disabled)
    }

    /** 合同第 2 行：有结果 = 重试 / 记入知识库，主动发不占位；两颗都落在结果双动作那一档 */
    @Test
    fun `with a result the primary slot is retry and save-to-kb, never the opener`() {
        mount(ComposerMode.REPLY, hasReplyResult = true, messageCount = 2)
        // 判的是 [LbButtonHeightTier.PanelResultActionPair] 这一档（并列的两个出口，比主动作再轻一档）。
        // 尺仍然是 boundsInRoot：两颗都得自己垫到这一档，谁掉回内容高度就红。
        val tier = LbButtonHeightTier.PanelResultActionPair
        val targets = probeAt(tier).assertAllActionableMeetTouchFloor(rule, "主操作区")
        assertEquals(
            "有结果时应当恰好两颗：" + targets.joinToString { it.describe() },
            listOf(retryLabel, saveToKbLabel).sorted(),
            targets.map { it.label }.sorted()
        )
        targets.forEach { assertTierHeight(tier, it) }
        assertTrue(
            "主动发不得挤回这颗按钮的位置：" + targets.joinToString { it.describe() },
            targets.none { it.label == generateOpenerLabel }
        )
    }

    /** 合同第 3 行：PROACTIVE 空闲 = 全宽「生成开场」，与回复那一路同一档（同一颗槽位） */
    @Test
    fun `proactive idle shows one full width generate-opener button`() {
        mount(ComposerMode.PROACTIVE, messageCount = 0)
        val tier = LbButtonHeightTier.PanelPrimaryAction
        val targets = probeAt(tier).assertAllActionableMeetTouchFloor(rule, "主操作区")
        assertEquals(
            "PROACTIVE 空闲应当只有一颗：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        assertEquals(generateOpenerLabel, targets.single().label)
        assertTierHeight(tier, targets.single())
    }

    /**
     * 合同第 4 行：任一生成中 = 唯一停止入口。
     *
     * 生成中的按钮带无限脉冲动画，所以这一格把测试时钟改成手动推进——
     * 否则 `waitForIdle()` 等不到"空闲"那一刻（自动推进下动画永远不结束）。
     */
    @Test
    fun `while a reply is generating the only entry is stop`() {
        rule.mainClock.autoAdvance = false
        mount(ComposerMode.REPLY, isGenerating = true, messageCount = 2)
        assertSingleStopEntry("回复生成中")
    }

    /** 主动发生成中：走 STOP 那条分支（回复生成中走的是 LOADING 分支），两条都只能有一个入口 */
    @Test
    fun `while an opener is generating the only entry is stop`() {
        rule.mainClock.autoAdvance = false
        val generating = androidx.compose.runtime.mutableStateOf(false)
        val proactive = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                ReplyPrimaryActions(
                    composerMode = ComposerMode.REPLY,
                    isGenerating = generating.value,
                    isProactive = proactive.value,
                    hasReplyResult = false,
                    messageCount = 2,
                    onGenerateReply = {},
                    onGenerateProactive = {},
                    onRetry = {},
                    onSaveToKb = {},
                    onStop = {}
                )
            }
        }
        assertSingleStopEntry("主动发生成中")
    }

    private fun assertSingleStopEntry(whenLabel: String) {
        repeat(4) { rule.mainClock.advanceTimeByFrame() }
        val targets = probe.actionableTargets(rule, "主操作区")
        assertEquals(
            "$whenLabel 时只能有一个停止入口：" + targets.joinToString { it.describe() },
            1, targets.size
        )
    }

    // ═════════════ 生成中"选态"统一（request2 §二第一条 / §四"生成动画"） ═════════════

    /**
     * 只读**按钮状态**那一面的读数，把"选到 Loading"与"选回 Stop"分成两个值。
     *
     * 为什么用 `LbTags.PRIMARY_STOP` 而不是文字：`LbPrimaryButton` 里**只有** Loading 态把
     * 那颗停止锚点 tag 挂在带 clickable 的盒子上（见 `LbPrimaryButton.kt` 的 Loading 分支），
     * Stop / Idle / Disabled 三态都没有它。文字会变（Loading 带秒数计时、Stop 是裸「停止」），
     * tag 不会——所以这一颗粒量的是**选了哪个状态**，正是本轮要统一的那颗旋钮：
     * 把任一条生成流程改回 `LbButtonState.Stop`，这里立刻读到 `NotLoading`，格子当场红。
     */
    private enum class PrimaryButtonStateReading { Loading, NotLoading }

    private fun primaryButtonState(): PrimaryButtonStateReading {
        val actionables = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        check(actionables.size == 1) {
            "生成中这一排只该有一颗主动作，实测 ${actionables.size} 颗"
        }
        val hasLoadingStopAnchor =
            rule.onAllNodes(hasTestTag(LbTags.PRIMARY_STOP)).fetchSemanticsNodes().isNotEmpty()
        return if (hasLoadingStopAnchor) PrimaryButtonStateReading.Loading
        else PrimaryButtonStateReading.NotLoading
    }

    /** 与 [mount] 同一壳，只是把 onStop 接出来计数（用于"停止只投一次"那格） */
    private fun mountWithStop(
        composerMode: ComposerMode,
        isGenerating: Boolean = false,
        isProactive: Boolean = false,
        hasReplyResult: Boolean = false,
        messageCount: Int = 0,
        onStop: () -> Unit
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                ReplyPrimaryActions(
                    composerMode = composerMode,
                    isGenerating = isGenerating,
                    isProactive = isProactive,
                    hasReplyResult = hasReplyResult,
                    messageCount = messageCount,
                    onGenerateReply = {},
                    onGenerateProactive = {},
                    onRetry = {},
                    onSaveToKb = {},
                    onStop = onStop
                )
            }
        }
    }

    // ═══════════ ① 两条生成流程各一格，各自钉死同一个读数 ═══════════
    //
    // 为什么从"同一格里两条流程各挂一次、比两个读数"改成**一档一格**（2026-10-10）：
    // 本仓的 Compose 规则**一个用例只许 `setContent` 一次**，第二次直接
    // `IllegalStateException: Cannot call setContent twice per test!`（本仓既知坑，
    // `UiMatrix`/`RenderIn` 那一族与 `ProviderSectionSemanticsTest` 的"卸树再挂回"都用
    // 同一颗 `mutableStateOf` 开关绕开；但这里两条流程的差**只在入参**，用不着在同一格挂两次）。
    // 等值判据没有松成"存在即可"：两格各自把读数钉在**同一个常数** `Loading` 上，
    // 等值由"两格同值"传递；把任一条流程改回 `Stop` ⇒ 那一格读不到 Loading 的停止锚点，当场红。
    // 时钟照同文件 `while a reply is generating…` 与 androidTest `proactiveGenerating_…` 的先例：
    // 生成中那颗是 `rememberInfiniteTransition` 的**无限脉冲**，不关 `autoAdvance` 时
    // `waitForIdle()` 永远等不到空闲，所以关掉它再手动推几帧，让断言落在同一帧上。
    @Test
    fun `reply generating picks the unified loading button state`() {
        rule.mainClock.autoAdvance = false
        mount(ComposerMode.REPLY, isGenerating = true, messageCount = 2)
        repeat(4) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(
            "回复生成中应统一到 Loading（进度环+呼吸那颗停止锚点）",
            PrimaryButtonStateReading.Loading, primaryButtonState()
        )
    }

    @Test
    fun `proactive generating picks the same unified loading button state`() {
        rule.mainClock.autoAdvance = false
        mount(ComposerMode.PROACTIVE, isProactive = true)
        repeat(4) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(
            "主动开场生成中应统一到与回复那一路**同一个** Loading 外观（同一格判一份入参）",
            PrimaryButtonStateReading.Loading, primaryButtonState()
        )
    }

    /**
     * ② 停止交互仍只投一次：一次点击只发一记 onStop（回复那一路）。
     *
     * 这一格守的是"不许合并成一颗猜该停哪个的回调"这条边界的**组件侧**证据——`ReplyPrimaryActions`
     * 只交一颗 `onStop`，点一次恰好投一次，不多投、不双投。
     * 至于"到底停的是 stopProactive 还是 stopGeneration"由宿主路由（`LoveBrainPanelScreen`），
     * 不在这一格里判，也不许在这一格合成一颗会自己猜的回调。
     */
    @Test
    fun `tapping the reply generating button dispatches onStop exactly once`() {
        val replyStops = AtomicInteger(0)
        rule.mainClock.autoAdvance = false
        mountWithStop(ComposerMode.REPLY, isGenerating = true, messageCount = 2) {
            replyStops.incrementAndGet()
        }
        repeat(4) { rule.mainClock.advanceTimeByFrame() }
        rule.onNode(hasClickAction()).performClick()
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        assertEquals("回复生成中点一次只该投一次停止", 1, replyStops.get())
    }

    /** ② 的另一半：主动开场那一路同样只投一记（同一颗旋钮、同一条判据，见上面那格的注释） */
    @Test
    fun `tapping the proactive generating button dispatches onStop exactly once`() {
        val proactiveStops = AtomicInteger(0)
        rule.mainClock.autoAdvance = false
        mountWithStop(ComposerMode.PROACTIVE, isProactive = true) {
            proactiveStops.incrementAndGet()
        }
        repeat(4) { rule.mainClock.advanceTimeByFrame() }
        rule.onNode(hasClickAction()).performClick()
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        assertEquals("主动开场生成中点一次只该投一次停止", 1, proactiveStops.get())
    }

    /**
     * ③ 非生成中的两档外观不变（正向对照），**一档一格**：
     * 空闲「生成开场」与有结果「重试 / 记入知识库」都还是 Idle，没有 Loading 的停止锚点。
     * 把这两档也误接成 Loading 会在各自那一格红。
     */
    @Test
    fun `the proactive idle tier keeps its idle appearance`() {
        mount(ComposerMode.PROACTIVE, messageCount = 0)
        rule.waitForIdle()
        assertEquals(
            "PROACTIVE 空闲不该带 Loading 的停止锚点",
            0, rule.onAllNodes(hasTestTag(LbTags.PRIMARY_STOP)).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `the result action pair keeps its idle appearance`() {
        mount(ComposerMode.REPLY, hasReplyResult = true, messageCount = 2)
        rule.waitForIdle()
        assertEquals(
            "结果双出口（重试 / 记入知识库）不该带 Loading 的停止锚点",
            0, rule.onAllNodes(hasTestTag(LbTags.PRIMARY_STOP)).fetchSemanticsNodes().size
        )
    }
}
