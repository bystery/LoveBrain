package com.lovebrain.app.ui.panel

import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SemanticsProbe.Target
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.feature.profile.ProfileReview
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.ui.panel.counseling.CounselingPanel
import com.lovebrain.app.ui.panel.reply.ResultArea
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.UsageStats
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
 * 错误档与未配置档——第6节第3条 点名的四态里"出事的那两格"，在这一族屏上**从来没挂起来过**。
 *
 * 之前只量过成功档（`ResultAreaTouchTargetsTest` 交的是 `GenerateResult.Success`，
 * 面板那一侧交的是"无错误"——锦囊面板那一块连同它那一格已随  整删）。
 * 所以那两格守卫跑得再绿，
 * 说的也只是"成功时这屏没毛病"——出事时那一屏长什么样，一颗都没读过。
 *
 * ⚠ 先记一条用**读数**纠正过来的旧假设：拿 `Success` 去挂"未配置供应商"那一档，
 * 量到的仍是整排方案卡——`when` 里 `result is Success` 排在 `!providerReady` **前面**。
 * 未配置档要 `result = null` 才到得了（判"挂的是哪一档"也得有证人，见下面第一格）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelErrorStatesSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    /**
     * 意图那一段（配置 + 编辑器可见性 + 开在哪块库）现在住在 [IntentController] 里，
     * 面板经 `vm.intents` 读那两格——测试给它一颗替身，一条流仍不留给 relaxed。
     */
    private fun fakeIntents(
        config: IntentConfig = IntentConfig(),
        showEditor: Boolean = false
    ): IntentController = mockk<IntentController>(relaxed = true).also {
        every { it.config } returns MutableStateFlow(config)
        every { it.showEditor } returns MutableStateFlow(showEditor)
    }

    private fun fakeVm(counselingError: String?): LoveBrainViewModel =
        mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
            // 第5节第2条 第 6 步：VM 上那 10 条纯转发口已删，状态归 ComposerStore 自己
            val composer = mockk<ComposerStore>(relaxed = true)
            every { vm.composer } returns composer
            // 泛型流一条都不留给 relaxed（relaxed 交回泛型 mock，`.value` 一取就 ClassCastException）
            every { vm.currentVector } returns MutableStateFlow(emptyMap())
            every { vm.intents } returns fakeIntents()
            every { vm.activeKb } returns MutableStateFlow(null)
            every { composer.counselingDraft } returns MutableStateFlow("")
            every { vm.counselingResult } returns MutableStateFlow<String?>(null)
            every { vm.counselingError } returns MutableStateFlow(counselingError)
            every { vm.isCounseling } returns MutableStateFlow(false)
            every { vm.counselingStreaming } returns MutableStateFlow("")
        }

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** 三颗重试的标签**都**走资源（本轮把它们并成一条），锚点必须 `getString` 取 */
    private val retryLabel: String get() = ctx.getString(com.lovebrain.app.R.string.panel_retry_tap)

    private fun mountResult(result: GenerateResult?, ready: Boolean) {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360, 1000).RenderIn(d) {
                ResultArea(
                    result = result,
                    isGenerating = false,
                    streamingCoreText = "",
                    isGeneratingCore = false,
                    streamingSchemes = emptyList(),
                    feedbacks = emptyMap(),
                    onFeedback = { _, _ -> },
                    onCopyScheme = {},
                    onRetry = {},
                    providerReady = ready,
                    onOpenSettings = {}
                )
            }
        }
        rule.waitForIdle()
    }

    private fun mountCounseling(error: String?) {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360, 1000).RenderIn(d) {
                CounselingPanel(viewModel = fakeVm(error), onFocusChange = {})
            }
        }
        rule.waitForIdle()
    }

    /** 只看**完整落在视口里**的节点；排除项连尺寸一起打进信息，并压样本下限（同账本 第40节/第44节） */
    private fun assertFloorAndRoles(what: String, minSample: Int = 2, allowEmpty: Boolean = false) {
        val all = actionables(what, allowEmpty = allowEmpty)
        if (all.isEmpty()) {
            // 走到这一支的唯一条件是调用方**明写** allowEmpty（否则 actionables 就抛）：
            // 所以"没东西可查"不会静默绿。这一态没有对象可扫两轴与角色，
            // 而"零可点"这个读数本身由调用那一格判成断言（见 the result error state …）。
            return
        }
        val (reachable, excluded) = all.partition { t ->
            t.widthDp > 0f && t.heightDp > 0f && t.leftDp >= 0f &&
                t.leftDp + t.widthDp <= 360f - 0.5f
        }
        assertTrue(
            "$what 视口内只量到 ${reachable.size} 个（整树 ${all.size} 个）——" +
                "样本这么少，下面两条断言就是在空转：" + all.joinToString { it.describe() },
            reachable.size >= minSample
        )
        val offenders = reachable.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "$what 有 ${offenders.size}/${reachable.size} 个可交互节点小于 ${probe.floorDp.toInt()}dp：\n" +
                offenders.joinToString("\n") { "  " + it.describe() } +
                "\n  （另排除 ${excluded.size} 个被容器裁掉的：" +
                excluded.joinToString { it.describe() } + "）",
            offenders.isEmpty()
        )
        val noRole = reachable.filter { !it.editable && it.role == "无" }
        assertTrue(
            "$what 有 ${noRole.size}/${reachable.size} 个可交互节点没声明角色（读屏念得出字、说不出它是按钮）：\n" +
                noRole.joinToString("\n") { "  " + it.describe() },
            noRole.isEmpty()
        )
    }

    /**
     * 「可交互」的样本定义，逐条抄自探针那一颗：带点击动作、带切换状态，或**被禁用**的
     * 点击控件（`clickable(enabled = false)` 仍然是入口，跳过它就等于把"灰着的"与
     * "根本没画的"两种实现都判绿）。本文件唯一能改它的原因是"空集要不要抛"，
     * 不是"认哪些节点"——这两件事分开写，别把它们混成一把新尺。
     */
    private val actionableMatcher: SemanticsMatcher =
        hasClickAction() or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    /**
     * 与 [SemanticsProbe.actionableTargets] **同一份样本定义**：带点击动作、带切换状态，
     * 或**被禁用**的点击控件（`clickable(enabled = false)` 仍是入口，跳过它就把
     * "画了但灰着"与"根本没画"两种实现都判绿）。
     *
     * 只差一处，而且这一处是调用方自己签的：[allowEmpty]。探针那颗"空集就抛"对绝大多数屏
     * 是对的（空集=入口没了/点击语义没挂上），它本身没被改松，这里也没去共用它——
     * 错误档这一态本轮撤掉了重复出口（同一屏不许有两颗做同一件事的入口），"就地零可点"
     * 正是合同要的读数，拿一把"空集即抛"的尺去量它，红的是量具。
     *
     * ⚠ 两份定义有可能各自漂移，所以 `the not-configured state gives one way into settings`
     * 那一格拿**同一屏**把两份读数对照钉住：哪天探针的样本换了义，那一格先红。
     */
    private fun actionables(screen: String, allowEmpty: Boolean = false): List<Target> {
        val nodes = rule.onAllNodes(actionableMatcher).fetchSemanticsNodes()
        if (nodes.isEmpty()) {
            check(allowEmpty) {
                "$screen 里一个可点击/可切换节点都没测到，而这一格没写'这一态允许零可点'——" +
                    "要么入口被删了，要么点击语义没挂上，两种都是事故，不能因为'没东西可查'就绿过去。"
            }
            return emptyList()
        }
        return nodes.map { probe.of(it) }
    }

    @Test
    fun `the result error state does not carry a second primary action`() {
        mountResult(GenerateResult.Error("网络断了，这轮没生成成"), ready = true)
        // 判据从"就地必须有一颗重试"翻成"就地必须没有"：本轮合同要回复档的主动作
        // **只有一个插入槽**，无结果时槽上画的就是那颗「生成回复」，结果区里再放一颗重试
        // 等于同一屏两个入口做同一件事。把那颗按钮加回来，这一格就红。
        // （谈心那一档不同：它没有别的出口，那一格保持"必须有重试"不动。锦囊那一档连同那一格
        //  一起随  整删——这里不为它留一条"存在即可"的空判据。）
        // 这一态**整档零可点**：不只是"不许有那颗重试"，而是整棵树读不到资源里那句重试的
        // 名字——按钮、裸文字、灰着的任何一种形状加回来都红。
        val targets = actionables("结果区·错误档", allowEmpty = true)
        assertEquals(
            "回复档错误状态不许自带第二颗重试出口：" + targets.joinToString { it.describe() },
            0, targets.count { it.label == retryLabel }
        )
        assertTrue(
            "错误档整档该零可点（这一屏现在没有自己的动作，出口只有插入槽那一颗）：" +
                targets.joinToString { it.describe() },
            targets.isEmpty()
        )
        // 光判"可点样本里没有它"漏掉一种加回来的形状：把那句重试画成**不可点的裸文字**
        // 就躲过了上面两条。所以整棵树里读不到资源里那句重试的名字才算数。
        assertTrue(
            "错误档不许以任何形状画重试（按钮、裸文字、灰着的都算）：整棵树读到了「$retryLabel」",
            rule.onAllNodes(hasText(retryLabel)).fetchSemanticsNodes().isEmpty()
        )
        // 这一态现在没有动作，两轴/角色的扫描照跑（有节点就必须合格），
        // 只是这里显式允许"零颗"这一份读数——同一把尺，同一份样本定义。
        assertFloorAndRoles("结果区·错误档", minSample = 0, allowEmpty = true)
    }

    @Test
    fun `the not-configured state gives one way into settings`() {
        mountResult(null, ready = false)
        val targets = probe.actionableTargets(rule, "结果区·未配置档")
        // 两份样本定义的对照证人（见 actionables 的 KDoc）：同一屏、同一份节点，颗数必须一样。
        // 探针那份空集即抛、本地那份由调用方决定——所以这一格挑的是"确实有节点"的这一态。
        val local = actionables("结果区·未配置档")
        assertEquals(
            "本文件自带的那份样本定义与探针的不一致了（探针数到 ${targets.size}，" +
                "本地数到 ${local.size}）——两份得一起改，别只改一条",
            targets.size, local.size
        )
        assertEquals(
            "未配置供应商这一档该且只该有一颗引导动作（：空态不做死路）：" +
                targets.joinToString { it.describe() },
            1, targets.size
        )
        assertFloorAndRoles("结果区·未配置档", minSample = 1)
        // 只判"存在 + 够大 + 有角色"还不够：写死成 Disabled 的引导动作同样能过那三条。
        // 上一格在表单那边学到的是同一课（禁用量与可用量各要一格）——这次立刻又用上一次。
        val way = targets.single()
        assertTrue(
            "未配置档的出口必须真能按（灰着的引导等于没有引导）：" + way.describe(),
            !way.disabled
        )
    }

    @Test
    fun `the counseling error state keeps a pressable retry`() {
        mountCounseling("军师这轮没回上话")
        val targets = probe.actionableTargets(rule, "谈心·错误档")
        assertTrue(
            "谈心错误档找不出重试出口（本机解析成 $retryLabel）：" + targets.joinToString { it.describe() },
            targets.any { it.label == retryLabel }
        )
        assertFloorAndRoles("谈心·错误档")
    }

    /**
     * 结果区右上角那颗「⋯」总工具入口**已经不存在了**，这一格跟着换成新语义的两条判据。
     *
     * 原来这一格判的是"点开下拉菜单的那颗要报 `DropdownList`"——那颗触发器连同整条
     * `⋯` 通路都在这一轮删掉（改完不许改名成「更多」继续堆同样四项），继续留那条断言
     * 就等于判一件已经不存在的形状。换成两条：
     *
     * ① **不许有下拉式工具入口回来**：这一屏读不出 `DropdownList` 角色，
     *    也读不出「更多操作」那句资源名（资源还在，触发器不许在）。
     *    反例：有人把那颗 `⋯` 换个名字画回来——这一条当场红。
     * ② **删了入口不等于删了出口**：成功档仍然读得到「纠正记忆」那颗文字入口，
     *    它有角色（`Button`）、热区在全站下限之上。
     *    反例：把总工具菜单整片删光、忘了给纠正中心留那条文字入口——第二条红。
     *
     * ⚠ 「本轮参考」那条每卡下方的文字入口本轮**量不到**：`ResultArea` 至今没把
     *   `onReferenceClick` 传进卡片行（那颗形参在 `SchemeCard` 上一直走默认 null），
     *   面板也就翻不动那份清单。那一格等结果区接线补齐之后再立，写在这里是为了别让它
     *   被当成"已经判过了"。
     */
    @Test
    fun `the success state has no dropdown utility trigger but keeps its text entry`() {
        mountResult(
            GenerateResult.Success(
                LoveBrainResponse(
                    response = ReplySchemes(recommended = "一", badBoy = "二", playful = "三", warm = "四"),
                    directions = listOf("先问清楚"),
                    analysis = ReplyAnalysis()
                )
            ),
            ready = true
        )
        val targets = probe.actionableTargets(rule, "结果区·成功档")
        val menuDescription = ctx.getString(com.lovebrain.app.R.string.panel_result_menu)
        val dropdowns = targets.filter {
            it.role == "DropdownList" || it.contentDescriptions.any { d -> d.contains(menuDescription, ignoreCase = true) }
        }
        assertTrue(
            "「⋯」总工具入口已随这一轮删除，成功档上不许再长出下拉式工具入口（改名成「更多」也不行）：" +
                dropdowns.joinToString { it.describe() },
            dropdowns.isEmpty()
        )
        val correctionEntry = targets.filter { it.label.contains("纠正记忆") }
        assertEquals(
            "结果区下方该且只该有一条「纠正记忆」文字入口（ 表：记忆纠正中心走这条简洁入口）：" +
                targets.joinToString { it.describe() },
            1, correctionEntry.size
        )
        val entry = correctionEntry.single()
        assertEquals(
            "那条文字入口也得说得出自己是什么（读屏不能只念一串字）：" + entry.describe(),
            "Button", entry.role
        )
        assertTrue(
            "那条入口的热区低于全站下限：" + entry.describe(),
            !entry.tooSmall(probe.floorDp)
        )
    }

    // ═══════════ 通知位：整屏挂法（一次一条 + 等待期间不被后台倒计时用掉）═══════════

    /**
     * 挂整屏时才判得了的那两件事——组件那一层没有队列，队列在 `NoticeBoard` 与这张屏的接线上。
     *
     * 坏实现能打破什么：
     * · 面板若仍按三条通道各读各的（或按优先级 `when` 只藏后面的）⇒ 第一格红（同屏两条）；
     * · 面板若给"正在显示的那一条"起了表之后又把**排队里**那条一起计时/或在它上位之前就把它
     *   判过期 ⇒ 第二格红（等待的那条再也上不了屏）；
     * · 面板若给"需要确认、时限为 null"那一条也起了一张表 ⇒ 第三格红（话还没看完条子自己没了）。
     */
    private fun panelVmWithRealQueue(notices: NoticeBoard): LoveBrainViewModel =
        mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
            val composer = mockk<ComposerStore>(relaxed = true)
            every { vm.composer } returns composer
            every { composer.panelMode } returns MutableStateFlow(0)
            every { composer.messages } returns MutableStateFlow(emptyList())
            every { composer.draftText } returns MutableStateFlow("")
            every { composer.currentRole } returns MutableStateFlow(ChatMessage.Role.HER)
            // 输入对象（她/我/补充）：面板 `LoveBrainPanelScreen.kt:190` 无条件 collect 这一颗，
            // 漂给 relaxed 就是 `StateFlow<T>.value` 擦除后的裸 `Object`，一读就 CCE，整屏在
            // mount 那一帧断掉——本文件那 2 红与 `PanelHost`/`PanelUsageMetric`/`PanelMainInputMode`
            // 同族同因（上面 91 行那条注释写的正是这条规矩，这一格漏了一项）。
            // 取值 = 生产初值 HER（ComposerStore.kt:192），与上面 `currentRole = HER` 同轴。
            // 只补这一颗挂载整屏的夹具：上面 `fakeVm` 那一颗喂的是 `ResultArea`（形参直给，
            // 不读 VM 的 inputKind），它没有这一格就不该补。
            every { composer.inputKind } returns MutableStateFlow(ComposerInputKind.HER)
            every { composer.ideaComposeMode } returns MutableStateFlow(false)
            every { composer.editingIndex } returns MutableStateFlow(-1)
            every { composer.ideaHint() } returns ""
            every { vm.activeKb } returns MutableStateFlow(null)
            every { vm.composerMode } returns MutableStateFlow(ComposerMode.REPLY)
            every { composer.counselingDraft } returns MutableStateFlow("")
            every { vm.counselingError } returns MutableStateFlow(null)
            every { vm.counselingResult } returns MutableStateFlow<String?>(null)
            every { vm.counselingStreaming } returns MutableStateFlow("")
            // （用户 2026-10-03 原话"点踩就不要弹窗全部删除！！记入就行了"）：VM 上那颗
            // `currentFeedbackCase` 出口随原因面板一起摘除，面板这一侧一个字都不读它
            // （`ui/panel/LoveBrainPanelScreen.kt:226` 明写"这里**故意不收集**"）。旧桩替它挂的
            // 那一条空流删掉；这一格用的通知位是下面那颗 `vm.currentNotice`/真实队列，
            // 赞/踩的状态出口则仍然只有 `vm.feedbacks` 那一条。
            every { vm.currentVector } returns MutableStateFlow(emptyMap())
            every { vm.feedbacks } returns MutableStateFlow(emptyMap())
            every { vm.generationRoundId } returns MutableStateFlow(1)
            every { vm.inputChanged } returns MutableStateFlow(false)
            every { vm.intents } returns fakeIntents()
            every { vm.isCounseling } returns MutableStateFlow(false)
            every { vm.isGenerating } returns MutableStateFlow(false)
            every { vm.isGeneratingCore } returns MutableStateFlow(false)
            every { vm.isProactive } returns MutableStateFlow(false)
            every { vm.onlyThisRound } returns MutableStateFlow(false)
            every { vm.proactiveError } returns MutableStateFlow(null)
            every { vm.proactiveOptions } returns MutableStateFlow(emptyList())
            every { vm.profileRegenerating } returns MutableStateFlow(false)
            every { vm.profileReview } returns MutableStateFlow(ProfileReview())
            every { vm.providerReady } returns MutableStateFlow(true)
            every { vm.result } returns MutableStateFlow(null)
            every { vm.resultMode } returns MutableStateFlow(ResultMode.REPLY)
            every { vm.rewriteStates } returns MutableStateFlow(emptyMap())
            every { vm.stageSuggestion } returns MutableStateFlow(null)
            every { vm.streamingCoreText } returns MutableStateFlow("")
            every { vm.streamingSchemes } returns MutableStateFlow(emptyList())
            every { vm.usageStats } returns MutableStateFlow(UsageStats())
            every { vm.vectorDelta } returns MutableStateFlow(emptyMap())
            every { vm.panelBackdropOpacityPercent } returns 100
            // 通知这一族走**真队列**：面板读 `currentNotice`、结束它时回报 `endCurrentNotice()`，
            // 两边都接在同一块 NoticeBoard 上——这样判的才是那条接线，而不是一颗替身的读数。
            every { vm.currentNotice } returns notices.current
            every { vm.endCurrentNotice() } answers { notices.endCurrent() }
            every { vm.showPanelWarning(any()) } answers {
                notices.show(NoticeBoard.Channel.Warning, firstArg())
            }
        }

    /** 时钟不自己走：不然 `waitForIdle` 会把自动过期那一段一路喂完，量到的屏上根本没有条子 */
    private fun mountPanelWithQueue(notices: NoticeBoard) {
        rule.mainClock.autoAdvance = false
        val vm = panelVmWithRealQueue(notices)
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360, 1000).RenderIn(d) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = vm,
                        onInputFocusChange = { _, _ -> },
                        onInputIntent = { },
                        onClearComposeFocus = { },
                        onResize = { _, _ -> },
                        onMove = { _, _ -> },
                        onCopy = { },
                        onOpenAppPage = { },
                        onCollapse = { }
                    )
                }
            }
        }
        repeat(4) { rule.mainClock.advanceTimeBy(16L) }
        rule.waitForIdle()
    }

    private fun onScreen(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** 正在显示的那一条**不自己过期**（时限为 null = 要用户确认），排队那条 meanwhile 也不许被用掉 */
    @Test
    fun `a notice that needs confirmation outlasts the clock and hands the slot over intact`() {
        val notices = NoticeBoard()
        // 第一条：要用户确认（null = 不自动过期）；第二条：自带 1 秒时限，排在后面
        notices.show(NoticeBoard.Channel.Warning, "画像建议要确认的那一句", null)
        notices.show(NoticeBoard.Channel.Knowledge, "排队里的回执", 1_000L)
        mountPanelWithQueue(notices)

        // 把时钟远远喂过排队那条自己的 1 秒时限
        repeat(6) { rule.mainClock.advanceTimeBy(2_000L); rule.waitForIdle() }

        assertTrue(
            "正在显示的那一条被后台倒计时用掉了（时限为 null 的那条根本不该起表）",
            onScreen("画像建议要确认的那一句")
        )
        assertTrue(
            "排队里那条不许抢先上屏（一次展示一条，后来的不抢占正在读的）",
            !onScreen("排队里的回执")
        )

        // 第一条真正结束 → 下一条**在同一句里**顶上，而且它自己的时限从这一刻才开始算
        notices.endCurrent()
        repeat(4) { rule.mainClock.advanceTimeBy(16L); rule.waitForIdle() }
        assertTrue(
            "上一条结束之后，排队那条该立即顶上（它没有因为在后台数着数着而过期）：" +
                "当前条子=" + notices.current.value?.message,
            onScreen("排队里的回执")
        )
        assertTrue(
            "上一条结束之后它自己就该消失",
            !onScreen("画像建议要确认的那一句")
        )
    }

    /** 同一条通知位上，**永远只有一条**画面：两条通道都有内容时也不许多出一格 */
    @Test
    fun `two channels with content at the same time still paint exactly one banner`() {
        val notices = NoticeBoard()
        notices.show(NoticeBoard.Channel.Knowledge, "知识库已保存")
        notices.show(NoticeBoard.Channel.Vector, "五维关系有更新")
        mountPanelWithQueue(notices)
        val closeActions = probe.actionableTargets(rule, "面板·通知位")
            .count { it.label.contains(ctx.getString(com.lovebrain.app.R.string.a11y_close_notice)) }
        assertEquals(
            "通知位上一次只许一条：两条通道各有内容时屏上读到了 $closeActions 条关闭动作",
            1, closeActions
        )
        assertTrue(
            "排队里那条不该出现在这一屏上",
            !onScreen("五维关系有更新")
        )
    }
}
