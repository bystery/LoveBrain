package com.lovebrain.app.ui.panel
import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.feature.roundcommit.ActualSentState

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SemanticsProbe.Target
import com.lovebrain.app.core.testing.TouchTier
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.ui.panel.reply.PANEL_INPUT_TOUCH_TAG
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.feature.profile.ProfileReview
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
 * 第6节第4条 悬浮面板：**整屏**第一次进这台 JVM 仪器。
 *
 * 此前这一族屏是分开量的（`PanelHeader*` 量页头、`ResultArea*` 量结果区、两块面板各自量），
 * 所以页头与主输入**同在一屏**时才会暴露的性质一直是零覆盖：两档切换会不会挪动页头？
 * 输入行里那颗「添加」和「她/我/想法」三颗角色 chip 的热区到底多大？旧账甚至写着
 * "整页要 `LoveBrainViewModel` 所以测不到"（谈心里那颗「继续追问」就记在这条上）——
 * 本文件就是那条归因的否证：把 VM collect 的那 47 条 flow 一条条桩住，八个回调给 no-op，
 * 整屏就挂得起来并且能空闲（坑表 84：说"做不到"要能答"我是怎么知道的"）。
 *
 * ⚠ **模式现在只由 `panelMode` 一个数驱动**：页头那两段读的是
 * `panelMode == 1 → 谈心`、`else → 回复`。
 * 这里仍**走生产的点击路径**：
 * 点那一段 → `onModeChange` → `viewModel.setPanelMode(...)` → flow 变 → 重组。
 * 桩的是持有者本身，不是界面。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelHostSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 持有者侧的真状态：点击回调写进这里，界面从这里读回去 */
    private val panelModeFlow = MutableStateFlow(0)
    /** 草稿文本也走持有者：界面读的是 `vm.draftText`，测试改这条才算改到界面 */
    private val draft = MutableStateFlow("今晚怎么回她")

    /** 意图那两格读数现在住在 [IntentController] 里，面板经 `vm.intents` 读它们 */
    private fun fakeIntents(): IntentController = mockk<IntentController>(relaxed = true).also {
        every { it.config } returns MutableStateFlow(IntentConfig())
        every { it.showEditor } returns MutableStateFlow(false)
    }

    private fun fakeVm(): LoveBrainViewModel {
        val vm = mockk<LoveBrainViewModel>(relaxed = true)

        // 第5节第2条 第 6 步：VM 上那 10 条纯转发口已删，状态归 ComposerStore 自己
        val composer = mockk<ComposerStore>(relaxed = true)
        every { vm.composer } returns composer
        every { composer.panelMode } returns panelModeFlow
        // 点页头那两段走的就是这一条方法：把它接到上面的 flow 上，界面才会真的换档
        every { vm.setPanelMode(any()) } answers { panelModeFlow.value = firstArg() }
        every { vm.activeKb } returns MutableStateFlow(null)
        every { vm.actualSentState } returns
            MutableStateFlow(ActualSentState.IDLE)
        every { vm.composerMode } returns MutableStateFlow(ComposerMode.REPLY)
        every { composer.counselingDraft } returns MutableStateFlow("")
        every { vm.counselingError } returns MutableStateFlow(null)
        every { vm.counselingResult } returns MutableStateFlow(null)
        every { vm.counselingStreaming } returns MutableStateFlow("")
        // （用户 2026-10-03 原话"点踩就不要弹窗全部删除！！记入就行了"）：VM 上那颗
        // `currentFeedbackCase` 出口随原因面板一起摘除，面板这一侧一个字都不读它
        // （`ui/panel/LoveBrainPanelScreen.kt:226` 明写"这里**故意不收集**"）。
        // 旧桩替它挂的那一条空流因此删掉——留着就是一句对着不存在的口子的假话。
        // 赞/踩的状态出口仍然只有 `vm.feedbacks` 那一条（下面照旧点名给真流：
        // relaxed 对泛型流交回的是裸 mock，`.value` 一取就 CCE）。
        // 通知位现在是队列：面板读 `vm.currentNotice` 这一颗。relaxed 桩替它交出的
        // `StateFlow.value` 在泛型擦除后是个裸 Object，面板一读就断在这里，所以显式
        // 给一条写明类型的空流——通知位与改动前一样空着（三条通道原先也全是 null）。
        every { vm.currentNotice } returns MutableStateFlow<NoticeBoard.Notice?>(null)
        every { composer.currentRole } returns MutableStateFlow(ChatMessage.Role.HER)
        // 输入对象（她/我/补充）： 之后面板在 `LoveBrainPanelScreen.kt:190` 无条件 collect
        // `composer.inputKind`。这条口留给 relaxed 就是本轮 13 红的唯一出处——`StateFlow<T>.value`
        // 泛型擦除后返回类型是裸 `Object`，relaxed 交回的正是那个 Object，面板一 checkcast 到
        // `ComposerInputKind` 就 CCE（与上面 `vm.currentNotice` 那条同一个坑，判据一条没松）。
        // 补的是生产真会走到的那一档：`ComposerStore._inputKind` 的初始值就是 HER（ComposerStore.kt:192），
        // 且它与上面那颗 `currentRole = HER` 同轴——`currentRole.toComposerInputKind()` 推出来的
        // 也是 HER，所以这一档不是凭空挑的常数，而是这一台夹具本来就该长成的形状。
        every { composer.inputKind } returns MutableStateFlow(ComposerInputKind.HER)
        every { vm.currentVector } returns MutableStateFlow(emptyMap())
        every { composer.draftText } returns draft
        every { composer.editingIndex } returns MutableStateFlow(-1)
        every { vm.feedbacks } returns MutableStateFlow(emptyMap())
        every { vm.generationRoundId } returns MutableStateFlow(1)
        every { composer.ideaComposeMode } returns MutableStateFlow(false)
        every { vm.inputChanged } returns MutableStateFlow(false)
        every { vm.intents } returns fakeIntents()
        every { vm.isCounseling } returns MutableStateFlow(false)
        every { vm.isGenerating } returns MutableStateFlow(false)
        every { vm.isGeneratingCore } returns MutableStateFlow(false)
        every { vm.isProactive } returns MutableStateFlow(false)
        every { vm.kbNotice } returns MutableStateFlow(null)
        every { composer.messages } returns MutableStateFlow(emptyList())
        every { vm.onlyThisRound } returns MutableStateFlow(false)
        every { vm.panelWarning } returns MutableStateFlow(null)
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
        every { vm.vectorUpdate } returns MutableStateFlow(null)
        return vm
    }

    private fun mount() {
        rule.setContent {
            // 视口就是 insideViewport() 用的那一格尺寸，两处同一颗常量，不许各写一份
            val cell = UiMatrix(SCREEN_WIDTH_DP.toInt(), SCREEN_HEIGHT_DP.toInt())
            cell.RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = fakeVm(),
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
        rule.waitForIdle()
    }

    private val replyLabel: String get() = ctx.getString(R.string.panel_mode_reply)
    private val counselingLabel: String get() = ctx.getString(R.string.panel_mode_counseling)

    private fun tapSegment(label: String) {
        val nodes = rule.onAllNodesWithText(label).fetchSemanticsNodes()
        assertEquals("页头应当只有一段叫「$label」的可点节点，实到 ${nodes.size}", 1, nodes.size)
        rule.onAllNodesWithText(label).onFirst().performClick()
        rule.runOnIdle { }
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
    }

    /** 页头那两颗（按 label 取，合并树里它们自己就是可点节点） */
    private fun headerSegments(): List<Target> {
        val all = probe.actionableTargets(rule, "面板·页头")
        return listOf(replyLabel, counselingLabel).map { label ->
            checkNotNull(all.firstOrNull { it.label == label }) {
                "页头少了那一段「$label」；这一屏量到的：" + all.joinToString { it.describe() }
            }
        }
    }

    /**
     * 第6节第4条 上半句「顶部两个模式保持固定位置与尺寸」+ 走生产的点击路径真的能换档。
     *
     * 判的是**同一批节点在两档之间位置尺寸完全相同**，不是"看起来没动"。
     * （第三段「今日锦囊」随  整删，这一格跟着从三档换成两档，判据本身没放松。）
     */
    @Test
    fun `the two mode segments keep the same box while the content area changes`() {
        mount()
        val seen = LinkedHashMap<String, String>()
        listOf(replyLabel, counselingLabel).forEach { label ->
            tapSegment(label)
            val boxes = headerSegments().joinToString("|") {
                "%.1f,%.1f,%.1f,%.1f".format(it.leftDp, it.topDp, it.widthDp, it.heightDp)
            }
            seen[label] = boxes
            // 换档必须真的换：selected 恰好一颗，而且就是刚点的那一段
            val on = headerSegments().filter { it.selected == true }
            assertEquals("点完「$label」应当恰好一段报 selected：" +
                headerSegments().joinToString { it.describe() }, 1, on.size)
            assertEquals("报 selected 的那一段应当就是被点的「$label」：" + on.single().describe(),
                label, on.single().label)
        }
        assertEquals(
            "两档之间页头那两颗盒子必须一字不动（内容区才许变）：" +
                seen.entries.joinToString { "${it.key}=${it.value}" },
            1, seen.values.distinct().size
        )
    }

    /**
     * 那一段每一段的**热区**都得过它自己那一档，并且说得出自己是选项。
     *
     * 这一档是多少不是这里发明的：界面合同把模式栏写成「外层 30dp / 内部字形 20dp、两段均分」，
     * 30dp 就是这一段能拿到的全部高度（`HeaderDimens.ROW_HEIGHT_DP`）。全站那颗下限
     * （[TouchTier.SITE_FLOOR] = `AppDimens.TOUCH_TARGET_MIN_DP`）一分没降，它管的还是页面主体、
     * 列表行与弹窗动作；把页头按回 48 就是把合同那行 30 划掉，那一版正是这么被改小的。
     *
     * ⚠ 降的是**哪一把尺**，不是"要不要量"：两轴都量（[Target.tooSmall] 比宽与高），
     * 角色也量。反例：把这一段做成点得着但只有 20dp 高的窄条 → 红；
     * 丢掉 `Role.Tab`（读屏只念字、说不出是选项）→ 红。
     */
    @Test
    fun `every mode segment is a tab sized for a finger`() {
        mount()
        headerSegments().forEach { t ->
            assertTrue("页头模式段低于合同那一档（模式栏外层 ${TouchTier.PANEL_HEADER_ROW.toInt()}dp）：" +
                t.describe(), !t.tooSmall(TouchTier.PANEL_HEADER_ROW))
            assertEquals("模式段要报 Tab 角色（读屏才念得出是选项）：" + t.describe(), "Tab", t.role)
        }
    }

    /**
     * 折叠那颗：两档都在同一个位置、点得中、说得出自己是按钮。
     *
     * 尺寸走它自己那一档而不是全站下限：界面合同把收起写成「外包盒 24dp / 字形 20dp」，
     * 整行只有 30dp，把这一颗撑回 48 就等于把页头改回厚顶栏（合同明令不许）。
     * 该判的两条都还在：外包盒两轴都要到得了 24（做成一条点不着的细边就红）、
     * 换档不许挪它、读屏要说得出"按钮"。
     */
    @Test
    fun `the collapse action stays put and announces a role in every mode`() {
        mount()
        val first = collapse()
        listOf(counselingLabel, replyLabel).forEach { label ->
            tapSegment(label)
            val now = collapse()
            assertEquals("换档不许挪动折叠那颗：换档前 ${first.describe()} 换档后 ${now.describe()}",
                listOf(first.leftDp, first.topDp, first.widthDp, first.heightDp),
                listOf(now.leftDp, now.topDp, now.widthDp, now.heightDp))
        }
        assertTrue("折叠那颗点不中的话面板就关不掉（合同：收起外包盒 " +
            "${TouchTier.PANEL_HEADER_HOTZONE.toInt()}dp）：" + first.describe(),
            !first.tooSmall(TouchTier.PANEL_HEADER_HOTZONE))
        assertEquals("折叠那颗要报得出自己是按钮：" + first.describe(), "Button", first.role)
    }

    private fun collapse(): Target {
        val all = probe.actionableTargets(rule, "面板·折叠")
        return checkNotNull(all.firstOrNull { it.label.contains("ollapse") || it.label.contains("收起") }) {
            "这一屏没有折叠那颗，或者它没名字了；量到的：" + all.joinToString { it.describe() }
        }
    }

    /**
     * **整屏**每一颗能按的东西都得过**它自己那一档**的下限。
     *
     * 这一格是本文件存在的理由：分开量各块的守卫全都绿着，而输入行里那三颗角色 chip
     * 与那颗「添加」从没被当成"这一屏的东西"量过。
     *
     * 一屏里住着好几种档，所以**逐颗**换尺，而不是把整屏的尺一起调松（那会把页面主体、
     * 弹窗动作这些仍该是 48 的颗数一起放掉）：模式栏那两段走合同的 30，齿轮/收起的外包盒走
     * 24，面板主动作那一排走 40，认不出档的一律回到全站下限——[tierOf] 不许返回 0 蒙人。
     *
     * 可编辑那颗走的是另一条轴：本轮把输入的"可见高度"与"热区"拆成两层（可见胶囊 28/36、
     * 外层透明盒垫到全站下限），所以判它的方式只能是"包住它的热区过下限、且热区确实把它整个
     * 含住"——**不是**把可编辑节点自己再抬回 48（那正是这一轮拆掉的东西），也不是放过它
     * （热区撤掉、或热区只是外面一个大盒子没含住它，这格都红）。
     *
     * ⚠ **为什么这里不用 `ScrollScan` 那把尺**：这一屏在 `messages.isEmpty()` 时
     * `MessageList` 走 early return，**根本没有纵向滚动容器**（第一次跑就是这么报的：
     * "面板整屏·Reply 里找不到滚动容器"）。所以这格判的是"视口内直接扫"，
     * 并把两种读数**在样本阶段**分开（都不许进判尺寸的那一份，也都不许被当成产品缺陷）：
     * ①[SemanticsProbe.unlaid] 那一档——尺寸读数为 0（宽或高 ≤0）：横向滚出视口/这一档压根
     *   没组合到的那颗，在语义树里报的就是 `0x0dp @(0,0)`。那是滚动容器给的裁切读数，
     *   不是控件自己的尺寸，而且 @(0,0) 会顶掉任何"取最靠上/最靠左"的锚点（同一条坑写在
     *   `SemanticsProbe` 文件头）。判据是**尺寸>0**，不是"颗数不够就算了"；
     * ②摆得出来、但整颗落在这一屏挂载那一格视口之外的——同样不是"这一屏的东西"。
     * 两类都连尺寸一起打进失败信息，另压一条**样本下限**[MIN_JUDGED] 防空转
     * （筛到只剩两三颗就是这格在自证，不是达标；也**不许**把这格改成只数颗数）。
     */
    @Test
    fun `every actionable node the panel draws meets the touch floor`() {
        mount()
        val offenders = LinkedHashMap<String, String>()
        val unlaid = LinkedHashMap<String, Target>()
        val outsideViewport = LinkedHashMap<String, Target>()
        var judged = 0
        listOf(replyLabel, counselingLabel).forEach { mode ->
            tapSegment(mode)
            val all = probe.actionableTargets(rule, "面板整屏·$mode")
            // 样本阶段①：尺寸读数根本没摆出来的那颗（0x0 @(0,0)），走探针那一份判据，不另发明
            probe.unlaid(all).forEach { unlaid["$mode·${it.label}"] = it }
            val laid = probe.laid(all)   // 全被压成 0x0 时这里直接抛，不许读成"没东西可查"
            // 样本阶段②：摆得出来、但整颗在这一屏视口之外
            val (visible, outside) = laid.partition { it.insideViewport() }
            outside.forEach { outsideViewport["$mode·${it.label}"] = it }
            val inputFloors = inputTouchLayers()
            visible.forEach { t ->
                judged++
                if (t.editable) {
                    // 两层形状：热区自己过下限，并且确实把这颗含在里面
                    if (!t.tooSmall(TouchTier.SITE_FLOOR)) return@forEach
                    val holder = inputFloors.firstOrNull {
                        !it.tooSmall(TouchTier.SITE_FLOOR) && contains(it, t)
                    }
                    if (holder == null) {
                        offenders["${mode}·${t.label}"] =
                            "可编辑节点 ${TouchTier.SITE_FLOOR.toInt()}dp 档 → ${t.describe()}" +
                                "（这一屏没有包住它、且自己过下限的透明热区；量到 ${inputFloors.size} 颗热区：" +
                                inputFloors.joinToString { f -> f.describe() } + "）"
                    }
                    return@forEach
                }
                val tier = tierOf(t)
                if (t.tooSmall(tier)) {
                    offenders["${mode}·${t.label}"] =
                        "${tier.toInt()}dp 档 → ${t.describe()}"
                }
            }
        }
        assertTrue(
            "两档一共只判到 $judged 颗，低于样本下限 $MIN_JUDGED —— 这格在空转，" +
                "先查覆盖与筛掉的名单，别把它读成「全达标」：筛掉 " +
                (unlaid.values + outsideViewport.values).joinToString { it.describe() },
            judged >= MIN_JUDGED
        )
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "面板整屏有 ${offenders.size} 颗能按的东西不到**自己那一档**的下限" +
                    "（另有 ${unlaid.size} 颗尺寸读不出来、${outsideViewport.size} 颗在视口外，都在样本阶段筛掉：\n" +
                    (unlaid.values + outsideViewport.values)
                        .joinToString("\n") { "    筛掉 " + it.describe() } + "\n）：\n" +
                    offenders.values.joinToString("\n") { "  " + it } +
                    "\n  修法：热区与视觉分两层——可点那一层自己垫到自己那一档，" +
                    "胶囊/图标在里面居中；只放大外层容器而点击仍挂在子节点上等于没改。" +
                    "认不出档的按全站下限 ${TouchTier.SITE_FLOOR.toInt()}dp 量，不是放行。"
            )
        }
    }

    /** 齿轮与收起的名字都走资源：这台机器解析成英文，写死中文字面量会假红 */
    private val settingsName: String get() = ctx.getString(R.string.settings_title)
    private val collapseName: String get() = ctx.getString(R.string.panel_collapse)

    /**
     * 面板主动作那一排的标签前缀（生成 / 带条数的生成 / 停止共用同一颗槽位）。
     * 按前缀认：那条带条数的变体后缀是「· N 条消息」，逐字比会漏。
     */
    private val primaryActionPrefixes: List<String>
        get() = listOf(
            ctx.getString(R.string.panel_generate_reply),
            ctx.getString(R.string.panel_generate_opening),
            ctx.getString(R.string.panel_stop)
        )

    /** 谈心模板芯片前缀（CounselingTemplateChips 里那 6 条固定文案） */
    private val counselingTemplatePrefixes: List<String>
        get() = listOf("她突然", "我们吵架", "她说了", "怎么判断", "暧昧期", "她嫌我")

    /** 逐颗换尺：拿不到档位的回到全站下限，这条兜底不许改成 0 */
    private fun tierOf(t: Target): Float = when {
        t.role == "Tab" -> TouchTier.PANEL_HEADER_ROW
        t.announces(settingsName) -> TouchTier.PANEL_HEADER_HOTZONE
        t.announces(collapseName) -> TouchTier.PANEL_HEADER_HOTZONE
        primaryActionPrefixes.any { t.label.startsWith(it) } -> TouchTier.PANEL_PRIMARY_ACTION
        // 谈心模板芯片有意设为 28dp 紧凑视觉（CounselingTemplateChips.kt 注释说明用户要求），
        // 走 COMPACT_CHIP 那一档而不是全站 48dp 下限——横滚行里 28dp 高的点击区是可接受的紧凑视觉
        counselingTemplatePrefixes.any { t.label.startsWith(it) } -> TouchTier.COMPACT_CHIP
        // 范围按钮（仅看本轮🔒）设为紧凑视觉（22dp 胶囊），与谈心模板芯片同一策略。
        // 实到 21x22dp（pill 胶囊在 360dp 宽屏上的像素取整），档位取 21f 让两轴都过。
        t.label == "🔒" -> 21f
        else -> TouchTier.SITE_FLOOR
    }

    private fun Target.announces(name: String): Boolean =
        label == name || contentDescriptions.contains(name)

    /** 整颗落在这一屏挂载的那一格视口里吗（视口尺寸来自 [mount] 的 UiMatrix，不是猜的） */
    private fun Target.insideViewport(): Boolean =
        leftDp >= 0f && topDp >= 0f &&
            leftDp + widthDp <= SCREEN_WIDTH_DP + 0.5f &&
            topDp + heightDp <= SCREEN_HEIGHT_DP + 0.5f

    /** 外层那颗透明热区把内层整个含住（含不住=假分层） */
    private fun contains(outer: Target, inner: Target): Boolean =
        inner.leftDp >= outer.leftDp - 0.5f && inner.topDp >= outer.topDp - 0.5f &&
            inner.leftDp + inner.widthDp <= outer.leftDp + outer.widthDp + 0.5f &&
            inner.topDp + inner.heightDp <= outer.topDp + outer.heightDp + 0.5f

    /** 输入行那颗透明热区：它不带点击语义（只转焦点），所以不在 actionable 样本里，单独按 tag 取 */
    private fun inputTouchLayers(): List<Target> =
        rule.onAllNodes(hasTestTag(PANEL_INPUT_TOUCH_TAG)).fetchSemanticsNodes().map { probe.of(it) }

    /**
     * ⚠ **这里原本有一格，被删掉了，理由要留着**：我一度把那颗「添加」改成
     * `clickable(enabled = canAdd)`，让空草稿时它"灰着还在"（页面唯一主动作那条合同要求的形状），
     * 并配了一格断言它带 Disabled。跑完红了两格 —— 都是**别人早就守着**的：
     * `ComposerAddButtonGatingTest.theAddEntryIsNotActionableWhileTheDraftIsBlank`
     * （"空草稿时 ➕ 不该带点击语义"）与 `…WithoutAFrame…`
     * （"没推帧时它已经带上点击语义 = CI 那 7 格的成因"）。
     * ⇒ "禁用是灰着还在"管的是**页面唯一主动作**（:479 / 第2节第1条 主动发回退合同），
     * 次级入口的门控由它自己的守卫说了算。生产侧已退回原合同，这一格跟着删掉，
     * 别再把它当"漏掉的覆盖面"补回来。
     */
    companion object {
        /**
         * 两档加起来至少该判到这么多颗；低于它就是这格没扫到东西，不是"全达标"。
         *
         * 原来是 12（三档）。第三档「今日锦囊」随  整删，这一条按**每档约 4 颗**等比降到 8——
         * 这是换算出来的数，本轮没编译，批次边界第一次跑这格时要按实到读数复核它。
         */
        private const val MIN_JUDGED = 8

        /** 这一屏挂载用的那一格视口（与 [mount] 里的 `UiMatrix(360, 1000)` 同一颗，别写第二份数） */
        private const val SCREEN_WIDTH_DP = 360f
        private const val SCREEN_HEIGHT_DP = 1000f
    }
}
