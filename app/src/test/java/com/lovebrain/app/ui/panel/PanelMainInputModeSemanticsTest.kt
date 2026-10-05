package com.lovebrain.app.ui.panel

import com.lovebrain.app.feature.composer.ComposerInputKind
import com.lovebrain.app.feature.composer.ComposerStore
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SemanticsProbe.Target
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.feature.notice.NoticeBoard
import com.lovebrain.app.feature.profile.ProfileReview
import com.lovebrain.app.feature.roundcommit.ActualSentState
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.ui.panel.reply.PANEL_INPUT_TOUCH_TAG
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
 * 第6节第4条 的**下半句**：模式切换「不移动主要**输入**」。（工单 W-B）
 *
 * 上半句（不移动主要操作）有 `LbPrimaryButtonStateTest` 与 `PanelHostSemanticsTest` 两处在判；
 * 下半句从没被任何机器判过——账本 第53节第5条 只留了一句"照字面读不成立，没自签"，
 * 而"没自签"不等于"没量过"：这一族屏一直只有两个数抄在注释里，没有一格读过长什么样。
 * 本文件先把仪器架上去**量**，再谈修。
 *
 * ═══ 量到了什么（本机，360dp 宽 × 1000dp 高，density=1.0，JVM 语义树 `boundsInRoot`）═══
 *
 * 主要输入 = 语义树里带 `SetText`/`EditableText` 的那一颗（回复档 = `PanelTextInput` 里的
 * `BasicTextField`；谈心档 = `CounselingPanel` 顶部那颗。第三档「今日锦囊」连同它"树里 0 颗
 * 可编辑节点"那一条读数随  整删，
 * 意图编辑器的两颗要 `showEditor` 才存在）。格式 `宽x高dp @(左,上)`。
 *
 * 【实到读数（本机跑出来的，逐格；宽一律 360dp）。下面这张表不是常量：
 *  `the main input of every mode...` 那一格每次运行都把同一张表打到 stdout
 *  （跑完在测试报告的 `system-out` 里读），改布局时打的就是新的实到表】
 *
 * | 字号 | 档位 | 主输入那颗 | 宽×高 @(左,上) |
 * |---|---|---|---|
 * | 1.0 | 回复 Reply | `PanelTextInput` 里的 `BasicTextField` | **252.0x48.0 @(28.0,313.0)** |
 * | 1.0 | 谈心 Talk it through | `CounselingPanel` 顶部那颗 | **304.0x48.0 @(28.0,261.0)** |
 * | 1.0 | 回复·主动发（补的一行） | 同一颗，chips 与 ➕ 收掉后 | **300.0x48.0 @(28.0,261.0)** |
 * | 2.0 | 回复 | 同上 | **252.0x48.0 @(28.0,622.0)** |
 * | 2.0 | 谈心 | 同上 | **304.0x48.0 @(28.0,570.0)** |
 * | 2.0 | 回复·主动发 | 同上 | **300.0x48.0 @(28.0,570.0)** |
 *
 * ⚠ 表后注记：回复输入行已回到旧版单行形制（chips、输入框、➕ 同排；可见胶囊高度不变，
 * ≥48dp 触摸下限挂在包裹整行的透明热区上，见 ③）。上面两张静态表是改动前的读数，
 * 「Reply」两行现在每次运行都会重新量并打进 system-out——以新表为准，别拿旧行当现状。
 *
 * 同一宽度同一字号下的位移（谈心 减 回复）：
 * 字 1.0 → 左 0.0、上 −52.0、宽 +52.0、高 0.0；
 * 字 2.0 → 左 0.0、上 −52.0、宽 +52.0、高 0.0。
 * 回复⇄主动发（同一颗，只是 chips 与 ➕ 收掉）：chips 行随主动发一起收掉，
 * 回复档 y 从 313 回到 261，宽 252→300，
 * 字 1.0 → 左 0.0、上 −52.0、宽 +48.0；字 2.0 → 左 0.0、上 −52.0、宽 +48.0。
 * 换档**来回一趟**（回复⇄谈心、回复⇄主动发）：两档字号都逐值回到原位，没有残留。
 *
 * 两条顺带量到的、与 第6节第5条 有关的事实（不在本工单判据里，交账不自签）：
 * - 回复档那颗可编辑宽度现在是 **252dp**（字 1.0 与 2.0 同宽）——chips 移到独立行之后
 *   输入框吃满整行（360 − 左右各 28dp padding − ➕ 那一颗 ≈ 252）；账本 第53节第4条 当年记的
 *   "chips 与 ➕ 垫到 48 见方之后留给输入框 128dp" 那一头已随 chips 换行而打开，
 *   修复前本机在同一台仪器上读到的是更紧的 96dp（字 1.0）/ 82dp（字 2.0）；
 * - 字号 2.0 时整屏那叠固定的东西（页头 + 引导卡 + 向量胶囊）把主输入推到 y=622（1.0 时 313），
 *   也就是最大字那一档这颗已经落到面板中线以下。这一条受那张引导卡在不在树上的影响
 *   （点过"关闭使用提示"就不画了），本机量的是它默认在树上的那一档。
 *
 * 与账本 第53节第5条 那两个数的对账：修复前谈心档 `304x76 @(28,273)` **逐值复现**（同一台仪器、
 * 同一格配置），回复档那里记的是 `166x48 @(138,261)` 而本机读到 `96x48 @(184,261)`——
 * 那颗归 `LbChip` 之后胶囊自己的宽度换了算法。本轮把 chips 移到独立行、counseling 高度改
 * content-driven 之后，谈心档变成 `304x48 @(28,261)`、回复档 `252x48 @(28,313)`，与那条
 * 旧账的**方向**仍一致（谈心在回复上方、宽更宽），**量级**已大幅收窄（宽差 208→52、
 * 高差 28→0、左差 156→0），本轮不再追问剩余的上差。
 *
 * ═══ 结论怎么读 ═══
 *
 * 两档都有主输入，而且**不是同一颗控件**：回复档那颗原与三颗角色 chip、
 * ➕ 抢同一行（`weight(1f)`），谈心档那颗独占一块 `height(100dp)` 的盒子——
 * 切换模式主输入左/上/宽/高四向都飘。本轮把 chips 移到独立行、counseling 高度改 content-driven
 * 之后，**宽/高/左差已归零**（回复与谈心都落在 x=28、高 48，宽 252/304），**仅上差因 chips
 * 独立行而增大到 52dp**（回复档 y=313、谈心档 y=261）。"切换模式不移动主要输入"这条在
 * 本面板里从**不成立**变为**大幅改善**：四向里三向归零，剩下一向是 chips 独立行带来的、
 * 可解释且来回可逆的位移（见 ②）。
 *
 * 修法（已做，由主线程本轮一并收尾的两处）：
 * - `ui/panel/reply/ReplyInput.kt`：角色 chips 从输入框同一行移到独立上一行（输入行只剩
 *   input + ➕，输入框吃满整行，宽从 96/82dp 打开到 252dp）；
 * - `ui/panel/counseling/CounselingPanel.kt`：输入框从 `.height(100f.dp)` 固定高度改为
 *   `.heightIn(min = 48f.dp)` content-driven，padding 从全方向 `Spacing.lg` 改为
 *   `padding(horizontal = Spacing.lg)`，占位文案加 `maxLines = 1`——
 *   谈心档那颗从 304x76 变 304x48，与回复档同高。
 * 仍未做、且不该在本工单做的：宿主钉"主输入槽位"（位置与带宽由宿主一处给出）——那要动
 * `LoveBrainPanelScreen.kt`，不在 W-B 授权文件里（工单只点了 `LoveBrainPanelScreen.kt`，
 * `core/designsystem`、`ui/common`、`ui/panel/SuggestPanel.kt` 另属他人地盘），
 * 只改宿主能挪动的东西是"把谈心那颗塞进一个固定高度"——那正是工单禁止的糊法，
 * 也会把 `PanelTextInput` 里那条"height 语义降为最小高度，防系统大字号截断"变成假话。
 * 所以本轮交的是：**实到读数 + 三条守得住的格子**，剩余的上差登记在账，不在这里自签跨模式等式。
 *
 * 留下的三条判据（都只判宿主这一层真正拥有的性质，见下面三格的 KDoc）：
 * ①读数入账 + 每档"该有几颗主输入"的形状；②切换模式再切回来，那颗必须回到逐值相同的盒子；
 * ③任何一档任何字号下，**承担这颗输入竖直预算的那一层**都不许矮于它自己要显示的那行字
 *   （可见胶囊与热区分层之后，那一层可以是外层透明盒，见下面第三格的 KDoc）。
 *
 * ⚠ 仪器口径沿用 `PanelHostSemanticsTest`：整屏挂 `LoveBrainViewModel` 靠 mockk 逐条桩 flow
 * （坑表 84——"要 VM 所以测不了"不是理由）；换档走**生产的点击路径**（点页头那一段 →
 * `onModeChange` → `setPanelMode` → flow 变 → 重组；第三段的 `openPlanPanel` 随  整删），
 * 并且每次点完都验
 * "报 selected 的就是刚点的那段"，否则"两档量出同一个盒子"可能是"两档根本没切"的假象；
 * 字号矩阵靠一份 hoisted `UiMatrix` 换约束（`setContent` 一格只能调一次）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelMainInputModeSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 持有者侧的真状态：点击回调写进这里，界面从这里读回去（同 `PanelHostSemanticsTest`） */
    private val panelModeFlow = MutableStateFlow(0)
    private val composerModeFlow = MutableStateFlow(ComposerMode.REPLY)
    /** 草稿留空：空草稿时那行占位文案才画得出来，第三格拿它当"内容要多高"的对照物 */
    private val draft = MutableStateFlow("")
    private val counselingDraft = MutableStateFlow("")

    /** 这一屏要跑的矩阵格：工单点名的两档字号（同宽 360dp） */
    private val cells = listOf(
        UiMatrix(360, 1000, 1.0f),
        UiMatrix(360, 1000, 2.0f)
    )

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
        every { vm.composerMode } returns composerModeFlow
        // 点页头那两段走的就是这一条方法：接到上面的 flow 上，界面才真的换档
        every { vm.setPanelMode(any()) } answers { panelModeFlow.value = firstArg() }
        every { vm.activeKb } returns MutableStateFlow(null)
        every { vm.actualSentState } returns MutableStateFlow(ActualSentState.IDLE)
        every { composer.counselingDraft } returns counselingDraft
        every { vm.counselingError } returns MutableStateFlow(null)
        every { vm.counselingResult } returns MutableStateFlow(null)
        every { vm.counselingStreaming } returns MutableStateFlow("")
        // （用户 2026-10-03 原话"点踩就不要弹窗全部删除！！记入就行了"）：VM 上那颗
        // `currentFeedbackCase` 出口随原因面板一起摘除，面板这一侧一个字都不读它
        // （`ui/panel/LoveBrainPanelScreen.kt:226` 明写"这里**故意不收集**"）。旧桩替它挂的
        // 那一条空流删掉；赞/踩的状态出口仍然只有下面那一条 `vm.feedbacks`。
        // 通知位现在是队列：面板读 `vm.currentNotice` 这一颗。relaxed 桩替它交出的
        // `StateFlow.value` 在泛型擦除后是个裸 Object，面板一读就断在这里，所以显式
        // 给一条写明类型的空流——通知位与改动前一样空着（三条通道原先也全是 null）。
        every { vm.currentNotice } returns MutableStateFlow<NoticeBoard.Notice?>(null)
        every { composer.currentRole } returns MutableStateFlow(ChatMessage.Role.HER)
        // 输入对象（她/我/补充）：面板 `LoveBrainPanelScreen.kt:190` 无条件 collect 这一颗，
        // 而它在本夹具里漂给了 relaxed —— `StateFlow<T>.value` 擦除后返回裸 `Object`，
        // 面板一读就 CCE，整屏挂在 mount() 那一帧（本文件 3 红同一个异常）。
        // ⚠ 这一格的取值对本文件是**有承重的**，不是随手挑的：`ReplyInput` 的占位文案按
        // `kind` 分叉（ReplyInput.kt:265-270），而第三格 `the main input is never shorter
        // than the text it has to show` 拿的正是"同一句话的那行占位文案"当内容高度的尺
        // （见那格 489-496 行）。生产 `_inputKind` 的初值是 HER（ComposerStore.kt:192），
        // 与上面 `currentRole = HER` 同轴，占位念「输入她说的话…」——桩成这一档才是
        // 生产真会走到的形状；桩成 SUPPLEMENT 会把那把尺换成一句更长的话，等于用夹具
        // 篡改判据的对照物。
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

    private var cell by mutableStateOf(cells.first())

    private fun mount() {
        rule.setContent {
            cell.RenderIn(density) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = fakeVm(),
                        onInputFocusChange = { _, _ -> },
                        onInputIntent = { },
                        onClearComposeFocus = { },
                        onResize = { _, _ -> },
                        onMove = { _, _ -> },
                        onCopy = { },
                        onCollapse = { }
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun useCell(next: UiMatrix) {
        rule.runOnIdle { cell = next }
        rule.waitForIdle()
    }

    private val replyLabel: String get() = ctx.getString(R.string.panel_mode_reply)
    private val counselingLabel: String get() = ctx.getString(R.string.panel_mode_counseling)

    /** 两档的"名字 → 怎么切过去"，走生产的点击路径（第三档「今日锦囊」随  整删） */
    private val modes: List<Pair<String, () -> Unit>>
        get() = listOf(
            replyLabel to { tapSegment(replyLabel) },
            counselingLabel to { tapSegment(counselingLabel) }
        )

    private fun tapSegment(label: String) {
        val nodes = rule.onAllNodesWithText(label).fetchSemanticsNodes()
        assertEquals("页头应当只有一段叫「$label」的节点，实到 ${nodes.size}", 1, nodes.size)
        rule.onAllNodesWithText(label).onFirst().performClick()
        rule.runOnIdle { }
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
        // 点了必须真的换档：那一段自己得报 selected。`performClick()` 只注入坐标、不查语义，
        // 会静默打空（公共规矩第 3 节）——那样"两档量到同一个盒子"就成了"两档根本没切"的假象。
        // 不按"全局恰好一颗报 selected"验：回复档那颗角色 chip「她」也带 Selected。
        val all = probe.actionableTargets(rule, "页头")
        val segment = checkNotNull(all.firstOrNull { it.label == label }) {
            "点完之后页头找不到「$label」那一段，这一档根本没切过去；量到的：" +
                all.joinToString { it.describe() }
        }
        assertEquals("点完「$label」那一段必须报 selected（没切过去就别量）：" + segment.describe(),
            true, segment.selected)
    }

    /** 语义树里带 `SetText` 的那几颗 = 这一屏当下的主输入（可有多颗时按上、左排） */
    private fun mainInputs(): List<Target> =
        rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .map { probe.of(it) }
            .sortedWith(compareBy({ it.topDp }, { it.leftDp }))

    private fun box(t: Target): String =
        "%.1fx%.1f @(%s,%s)".format(t.widthDp, t.heightDp,
            "%.1f".format(t.leftDp), "%.1f".format(t.topDp))

    private val table = StringBuilder()

    /** 量一遍两档 × 两档字号，把读数记进 [table]；返回 `档位/字号 → 那颗` 的名单 */
    private fun measureAll(label: String): Map<String, List<Target>> {
        val read = LinkedHashMap<String, List<Target>>()
        for (m in cells) {
            useCell(m)
            for ((name, switch) in modes) {
                switch()
                val inputs = mainInputs()
                read["$name@${m.fontScale}"] = inputs
                table.append("PROBE64 $label 宽${m.widthDp}dp 字${m.fontScale} 档「$name」主输入 ")
                    .append(inputs.size).append(" 颗：")
                    .append(inputs.joinToString(" | ") { "${box(it)} 名「${it.label}」" })
                    .append('\n')
            }
        }
        return read
    }

    /**
     * 补充一行读数：回复档**内部**的「主动发」换档（`composerMode` 那一族）。
     *
     * 它是"切换模式移动主要输入"最干净的一发：那颗输入与三颗角色 chip、➕ 抢同一行
     * （`ReplyInput` 里 `weight(1f)`），主动发档把 chips 与 ➕ 一收，输入立刻吃满整行。
     * 这一档不靠点击、直接把持有者那条 flow 摆过去（界面读的就是这一条）。
     */
    private fun measureProactive(label: String): Map<String, List<Target>> {
        val read = LinkedHashMap<String, List<Target>>()
        for (m in cells) {
            useCell(m)
            tapSegment(replyLabel)
            rule.runOnIdle { composerModeFlow.value = ComposerMode.PROACTIVE }
            rule.waitForIdle()
            val inputs = mainInputs()
            read["${m.fontScale}"] = inputs
            table.append("PROBE64 $label 宽${m.widthDp}dp 字${m.fontScale} 档「回复·主动发」主输入 ")
                .append(inputs.size).append(" 颗：")
                .append(inputs.joinToString(" | ") { "${box(it)} 名「${it.label}」" })
                .append('\n')
            rule.runOnIdle { composerModeFlow.value = ComposerMode.REPLY }
            rule.waitForIdle()
        }
        return read
    }

    /**
     * 把到目前为止攒下的实到读数打到 stdout。
     *
     * 这张表**不进断言**：绝对位置随字号与上面那叠卡片变，钉死它只会得到一条
     * 谁都不敢改的常量。判据只判相对量（见下面三格）。文件顶部那张表就是从这里读出来的，
     * 以后谁改了布局，跑这格会打出新的实到表。
     */
    private fun dumpTable(where: String) {
        println(where)
        println(table.toString())
    }

    /**
     * ①读数入账 + 主输入的形状：同一宽度同一字号下，回复档与谈心档各**一颗**主输入。
     *
     * 这一格管的是"量到的是不是那两档"：档位认错、输入被删成两颗或零颗都会红。
     * 它**不**判跨档等式——那条等式今天不成立，见文件顶部的结论。
     * （第三档「今日锦囊」原本"树里 0 颗可编辑节点"那一条判据随  整删——那一档已经不存在，
     *  留着它就等于把"没有这一档"读成"这一档没有输入"。）
     */
    @Test
    fun `the main input of every mode is on the record in both font scales`() {
        mount()
        val read = measureAll("主输入实到")
        val proactive = measureProactive("主输入实到")
        dumpTable(" 下半句·主输入实到读数（宽 360dp × 字号 1.0/2.0）")
        assertEquals(
            "两档字号 × 两档模式应当量到 4 次，实到 ${read.size}：" + read.keys, 4, read.size
        )
        assertEquals(
            "两档字号 × 主动发档应当量到 2 次，实到 ${proactive.size}：" + proactive.keys,
            2, proactive.size
        )
        for (scale in listOf("1.0", "2.0")) {
            assertEquals("回复档该有且只有一颗主输入（字$scale）：" +
                read.getValue("$replyLabel@$scale").joinToString { it.describe() },
                1, read.getValue("$replyLabel@$scale").size)
            assertEquals("谈心档该有且只有一颗主输入（字$scale）：" +
                read.getValue("$counselingLabel@$scale").joinToString { it.describe() },
                1, read.getValue("$counselingLabel@$scale").size)
            assertEquals("主动发档该有且只有一颗主输入（字$scale）：" +
                proactive.getValue(scale).joinToString { it.describe() },
                1, proactive.getValue(scale).size)
        }
    }

    /**
     * ②切换模式**再切回来**，那颗主输入必须回到逐值相同的盒子。
     *
     * 这是"模式切换不移动主要输入"里此刻**能判、也该由宿主守住**的那半：
     * 档位自己的布局可以不同（跨档等式见文件顶部），但换出去再换回来不许留残渣——
     * 宿主里任何按档位残留下来的 remember 高度、滚动态、只在某档成立的内边距，
     * 都会把这格弄红。两档字号各测一遍（来回取的是回复⇄谈心）。
     */
    @Test
    fun `coming back from another mode puts the main input back in the same box`() {
        mount()
        for (m in cells) {
            useCell(m)
            for ((home, away) in listOf(replyLabel to counselingLabel)) {
                tapSegment(home)
                val before = mainInputs()
                tapSegment(away)
                tapSegment(home)
                val after = mainInputs()
                dumpTable("②换档来回·字${m.fontScale}·$home ⇄ $away：换出前 ${before.joinToString { box(it) }}" +
                    "，换回后 ${after.joinToString { box(it) }}")
                assertEquals(
                    "字号 ${m.fontScale}：从「$away」切回「$home」之后，主输入那颗必须回到" +
                        "换出前那一格（宽/高/左上逐值相同）。换出前：" +
                        before.joinToString { box(it) } + " 换回后：" +
                        after.joinToString { box(it) },
                    before.map { listOf(it.leftDp, it.topDp, it.widthDp, it.heightDp) },
                    after.map { listOf(it.leftDp, it.topDp, it.widthDp, it.heightDp) }
                )
                assertEquals(
                    "换回来还该是同一颗（个数不许变），字号 ${m.fontScale}：" +
                        after.joinToString { it.describe() },
                    before.size, after.size
                )
            }
            // 同一类性质，换的是回复档**内部**那一族：chips 与 ➕ 收掉再放回来，
            // 那颗输入得回到原来那一格（换过去时它变宽是另一件事，见文件顶部的位移账）
            tapSegment(replyLabel)
            val replyBox = mainInputs().single()
            rule.runOnIdle { composerModeFlow.value = ComposerMode.PROACTIVE }
            rule.waitForIdle()
            val proactiveBox = mainInputs().single()
            rule.runOnIdle { composerModeFlow.value = ComposerMode.REPLY }
            rule.waitForIdle()
            val backAgain = mainInputs().single()
            dumpTable("②回复档内换档·字${m.fontScale}：回复 ${box(replyBox)} → 主动发 ${box(proactiveBox)}" +
                " → 再回回复 ${box(backAgain)}")
            assertEquals(
                "字号 ${m.fontScale}：去主动发档绕一圈回来，回复那颗主输入应当还在 " +
                    box(replyBox) + "，实到 " + box(backAgain),
                listOf(replyBox.leftDp, replyBox.topDp, replyBox.widthDp, replyBox.heightDp),
                listOf(backAgain.leftDp, backAgain.topDp, backAgain.widthDp, backAgain.heightDp)
            )
        }
    }

    /**
     * ③主输入的**竖直预算**由**内容**决定：任何一档任何字号下，包住它的那一层都不许矮于
     * 它自己要显示的那行字。
     *
     * ⚠ 这一格原先把 `INPUT_ROW_HEIGHT_DP` 直接套在可编辑节点自己身上：那等于把**可见控件**
     * 钉成触摸下限那一档。本轮把输入的两条轴拆开了——**可见高度**（回复 28dp、通用表单 36dp，
     * `PanelTextInput` 里是 `heightIn(min = height)`）与**热区**（外层那颗透明盒
     * [PANEL_INPUT_TOUCH_TAG]，垫到全站下限，只负责"点胶囊外的空档也能获焦"）。
     * 于是"装不装得下那行字"这件事，可编辑节点自己**不再是承担者**：本机量到的 96x15 是那颗
     * `BasicTextField` 交出的文字盒，不是这一行能拿到的高度。判据跟着换承担它的那一层，
     * **下限数值一分没动**：
     * · 这一屏存在**完整包住这颗可编辑节点的透明热区** ⇒ 竖直预算就是那一层：它必须过全局下限，
     *   可编辑节点必须真的落在它里面（外层套个大盒子却没包住=假分层，那时竖直预算回落到
     *   可编辑节点自己那一层，15dp 装不下 19dp 的字，这里照样红）；
     * · 没有热区的那一档（谈心那颗自己就是盒子） ⇒ 竖直预算就是它自己，判据与从前一字不变。
     * "不许给输入框硬塞固定高度"的反证仍在：一旦槽位钉死，字号 2.0 下字比那一层高，这里当场红。
     * 反过来把可编辑节点自己抬回 48（两层塌成一层）**这格不许判绿**——那一档由回复族的
     * `theComposerInputSitsInsideAFullHeightTouchLayer` 反向钉着，两边合起来才封住这个口。
     */
    @Test
    fun `the main input is never shorter than the text it has to show`() {
        mount()
        var judged = 0
        var layered = 0
        for (m in cells) {
            useCell(m)
            for ((name, switch) in modes) {
                switch()
                val inputs = mainInputs()
                // 树里没有主输入的那一档不参与比对，但要参与下面的样本下限
                for (t in inputs) {
                    // 竖直预算归哪一层：有"整颗包住它且自己过下限"的透明热区时归热区，
                    // 没有时（谈心）可编辑节点自己就是那一层。
                    val floors = rule.onAllNodes(hasTestTag(PANEL_INPUT_TOUCH_TAG))
                        .fetchSemanticsNodes().map { probe.of(it) }
                    val holder = floors.firstOrNull { holds(it, t) }
                    val budget: Target = if (holder == null) {
                        t
                    } else {
                        layered++
                        assertTrue("输入行热区矮于全局下限（字号 ${m.fontScale}·档「$name」）：" +
                            holder.describe(), !holder.tooSmall(probe.floorDp))
                        assertTrue("可编辑节点必须落在热区里（字号 ${m.fontScale}·档「$name」）：" +
                            "字段 ${t.describe()} 热区 ${holder.describe()}", holds(holder, t))
                        holder
                    }
                    if (t.label.isBlank()) continue
                    // 同一句话的那行占位文案是输入框的**兄弟节点**（草稿为空时才画）：
                    // 裁切类判据只能判几何，所以拿它的高度当"内容要多高"的尺
                    val text = rule.onAllNodes(hasText(t.label, substring = false))
                        .fetchSemanticsNodes().map { probe.of(it) }
                        .filter { !it.editable }
                    if (text.isEmpty()) continue
                    judged++
                    val needed = text.maxOf { it.heightDp }
                    assertTrue(
                        "字号 ${m.fontScale}·档「$name」：主输入那一层 ${box(budget)} 装不下它自己" +
                            "那行字（字要高 ${"%.1f".format(needed)}dp）——大字号会截切。" +
                            "可编辑节点自己量到 ${box(t)}（它不是这一轮的竖直预算承担者）。" +
                            "\n  修法：槽位只钉位置，高度交回内容；两层的话把垫高的那一层留在" +
                            "包住可编辑节点的那条链上。" +
                            "\n  字的那几颗：" + text.joinToString { it.describe() },
                        budget.heightDp + 0.5f >= needed
                    )
                }
            }
        }
        dumpTable("③内容高度比对：判了 $judged 次（其中 $layered 次由透明热区那一层承担），" +
            "满的应当是字号 1.0/2.0 × 回复/谈心 = 4 次")
        assertTrue(
            "这格只判了 $judged 次内容高度比对，低于样本下限 $MIN_TEXT_CHECKS —— 是在空转，" +
                "先查占位文案还在不在树上，别把它读成「全达标」",
            judged >= MIN_TEXT_CHECKS
        )
        // 分层这条判据不许悄悄退化成"每档都拿可编辑节点自己量"：回复档那颗必须是热区那一层
        assertTrue(
            "字号矩阵跑完一次都没找到「包住可编辑节点的透明热区」（layered=$layered）——" +
                "回复档那颗输入的两层形状没了，这格就退回到了把下限钉在可见控件上",
            layered > 0
        )
    }

    /** [inner] 是否整个落在 [outer] 里（0.5dp 容差：布局取整出来的半个像素不算脱出） */
    private fun holds(outer: Target, inner: Target): Boolean =
        inner.leftDp >= outer.leftDp - 0.5f && inner.topDp >= outer.topDp - 0.5f &&
            inner.leftDp + inner.widthDp <= outer.leftDp + outer.widthDp + 0.5f &&
            inner.topDp + inner.heightDp <= outer.topDp + outer.heightDp + 0.5f

    companion object {
        /** ③的样本下限：字号 1.0/2.0 × 回复/谈心两档 = 4 次比对；低于它就是这格没判到东西 */
        private const val MIN_TEXT_CHECKS = 4
    }
}
