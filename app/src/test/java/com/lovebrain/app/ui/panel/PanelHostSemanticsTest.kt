package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
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
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.ComposerMode
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ResultMode
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.ProfileReview
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
 * §6.4 悬浮面板：**整屏**第一次进这台 JVM 仪器。
 *
 * 此前这一族屏是分开量的（`PanelHeader*` 量页头、`ResultArea*` 量结果区、两块面板各自量），
 * 所以页头与主输入**同在一屏**时才会暴露的性质一直是零覆盖：三档切换会不会挪动页头？
 * 输入行里那颗「添加」和「她/我/想法」三颗角色 chip 的热区到底多大？旧账甚至写着
 * "整页要 `LoveBrainViewModel` 所以测不到"（谈心里那颗「继续追问」就记在这条上）——
 * 本文件就是那条归因的否证：把 VM collect 的那 47 条 flow 一条条桩住，八个回调给 no-op，
 * 整屏就挂得起来并且能空闲（坑表 84：说"做不到"要能答"我是怎么知道的"）。
 *
 * ⚠ **模式不是靠 `panelMode` 一个数驱动的**：页头那三段读的是
 * `panelMode == 1 → 谈心`、`showPlanPanel → 锦囊`、`else → 回复`（`PanelHeader:88-93`）。
 * 照直觉把 `panelMode` 摆成 0/1/2 会量到"第三档没切过去"——所以这里**走生产的点击路径**：
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
    private val showPlanPanelFlow = MutableStateFlow(false)

    private fun fakeVm(): LoveBrainViewModel {
        val vm = mockk<LoveBrainViewModel>(relaxed = true)
        every { vm.panelMode } returns panelModeFlow
        every { vm.showPlanPanel } returns showPlanPanelFlow
        // 点页头那三段走的就是这两个方法：把它们接到上面的 flow 上，界面才会真的换档
        every { vm.setPanelMode(any()) } answers { panelModeFlow.value = firstArg() }
        every { vm.openPlanPanel() } answers { showPlanPanelFlow.value = true }
        every { vm.dismissPlanPanel() } answers { showPlanPanelFlow.value = false }
        every { vm.activeKb } returns MutableStateFlow(null)
        every { vm.actualSentState } returns
            MutableStateFlow(LoveBrainViewModel.ActualSentState.IDLE)
        every { vm.composerMode } returns MutableStateFlow(ComposerMode.REPLY)
        every { vm.counselingDraft } returns MutableStateFlow("")
        every { vm.counselingError } returns MutableStateFlow(null)
        every { vm.counselingResult } returns MutableStateFlow(null)
        every { vm.counselingStreaming } returns MutableStateFlow("")
        every { vm.currentFeedbackCase } returns MutableStateFlow(null)
        every { vm.currentRole } returns MutableStateFlow(ChatMessage.Role.HER)
        every { vm.currentVector } returns MutableStateFlow(emptyMap())
        every { vm.draftText } returns draft
        every { vm.editingIndex } returns MutableStateFlow(-1)
        every { vm.feedbacks } returns MutableStateFlow(emptyMap())
        every { vm.generationRoundId } returns MutableStateFlow(1)
        every { vm.ideaComposeMode } returns MutableStateFlow(false)
        every { vm.inputChanged } returns MutableStateFlow(false)
        every { vm.intentConfig } returns MutableStateFlow(IntentConfig())
        every { vm.isCounseling } returns MutableStateFlow(false)
        every { vm.isGenerating } returns MutableStateFlow(false)
        every { vm.isGeneratingCore } returns MutableStateFlow(false)
        every { vm.isProactive } returns MutableStateFlow(false)
        every { vm.isSuggesting } returns MutableStateFlow(false)
        every { vm.kbNotice } returns MutableStateFlow(null)
        every { vm.messages } returns MutableStateFlow(emptyList())
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
        every { vm.showIntentEditor } returns MutableStateFlow(false)
        every { vm.stageSuggestion } returns MutableStateFlow(null)
        every { vm.streamingCoreText } returns MutableStateFlow("")
        every { vm.streamingSchemes } returns MutableStateFlow(emptyList())
        every { vm.streamingTips } returns MutableStateFlow(emptyList())
        every { vm.suggestError } returns MutableStateFlow(null)
        every { vm.suggestion } returns MutableStateFlow(null)
        every { vm.usageStats } returns MutableStateFlow(UsageStats())
        every { vm.vectorDelta } returns MutableStateFlow(emptyMap())
        every { vm.vectorUpdate } returns MutableStateFlow(null)
        return vm
    }

    private fun mount() {
        rule.setContent {
            UiMatrix(360, 1000).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    LoveBrainPanelScreen(
                        viewModel = fakeVm(),
                        onInputFocusChange = { _, _ -> },
                        onInputIntent = { },
                        onClearComposeFocus = { },
                        onResize = { _, _ -> },
                        onMove = { _, _ -> },
                        onCopy = { },
                        onOpenSettings = { },
                        onCollapse = { }
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private val replyLabel: String get() = ctx.getString(R.string.panel_mode_reply)
    private val suggestLabel: String get() = ctx.getString(R.string.panel_mode_suggest)
    private val counselingLabel: String get() = ctx.getString(R.string.panel_mode_counseling)

    private fun tapSegment(label: String) {
        val nodes = rule.onAllNodesWithText(label).fetchSemanticsNodes()
        assertEquals("页头应当只有一段叫「$label」的可点节点，实到 ${nodes.size}", 1, nodes.size)
        rule.onAllNodesWithText(label).onFirst().performClick()
        rule.runOnIdle { }
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
    }

    /** 页头那三颗（按 label 取，合并树里它们自己就是可点节点） */
    private fun headerSegments(): List<Target> {
        val all = probe.actionableTargets(rule, "面板·页头")
        return listOf(replyLabel, suggestLabel, counselingLabel).map { label ->
            checkNotNull(all.firstOrNull { it.label == label }) {
                "页头少了那一段「$label」；这一屏量到的：" + all.joinToString { it.describe() }
            }
        }
    }

    /**
     * §6.4 上半句「顶部三个模式保持固定位置与尺寸」+ 走生产的点击路径真的能换档。
     *
     * 判的是**同一批节点在三档之间位置尺寸完全相同**，不是"看起来没动"。
     */
    @Test
    fun `the three mode segments keep the same box while the content area changes`() {
        mount()
        val seen = LinkedHashMap<String, String>()
        listOf(replyLabel, suggestLabel, counselingLabel).forEach { label ->
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
            "三档之间页头那三颗盒子必须一字不动（内容区才许变）：" +
                seen.entries.joinToString { "${it.key}=${it.value}" },
            1, seen.values.distinct().size
        )
    }

    /** 那三段每一段的真实触摸区都得过下限，并且说得出自己是选项 */
    @Test
    fun `every mode segment is a tab sized for a finger`() {
        mount()
        headerSegments().forEach { t ->
            assertTrue("页头模式段低于 ${probe.floorDp.toInt()}dp：" + t.describe(),
                !t.tooSmall(probe.floorDp))
            assertEquals("模式段要报 Tab 角色（读屏才念得出是选项）：" + t.describe(), "Tab", t.role)
        }
    }

    /** 折叠那颗：三档都在同一个位置、点得中、说得出自己是按钮 */
    @Test
    fun `the collapse action stays put and announces a role in every mode`() {
        mount()
        val first = collapse()
        listOf(suggestLabel, counselingLabel, replyLabel).forEach { label ->
            tapSegment(label)
            val now = collapse()
            assertEquals("换档不许挪动折叠那颗：换档前 ${first.describe()} 换档后 ${now.describe()}",
                listOf(first.leftDp, first.topDp, first.widthDp, first.heightDp),
                listOf(now.leftDp, now.topDp, now.widthDp, now.heightDp))
        }
        assertTrue("折叠那颗点不中的话面板就关不掉：" + first.describe(),
            !first.tooSmall(probe.floorDp))
        assertEquals("折叠那颗要报得出自己是按钮：" + first.describe(), "Button", first.role)
    }

    private fun collapse(): Target {
        val all = probe.actionableTargets(rule, "面板·折叠")
        return checkNotNull(all.firstOrNull { it.label.contains("ollapse") || it.label.contains("收起") }) {
            "这一屏没有折叠那颗，或者它没名字了；量到的：" + all.joinToString { it.describe() }
        }
    }

    /**
     * §6.5 :531 ——**整屏**每一颗能按的东西都得过 48dp 见方。
     *
     * 这一格是本文件存在的理由：分开量各块的守卫全都绿着，而输入行里那三颗角色 chip
     * 与那颗「添加」从没被当成"这一屏的东西"量过。
     *
     * ⚠ **为什么这里不用 `ScrollScan` 那把尺**：这一屏在 `messages.isEmpty()` 时
     * `MessageList` 走 early return，**根本没有纵向滚动容器**（第一次跑就是这么报的：
     * "面板整屏·Reply 里找不到滚动容器"）。所以这格判的是"视口内直接扫"，
     * 并把两种读数分开：
     * ①`0x0dp @(0,0)`——横向滚出去的胶囊，裁切读数，筛掉但打进失败信息；
     * ②整颗不在 360x1000 视口里的——同样筛掉，另压一条**样本下限**防空转
     *    （筛到只剩两三颗就是这格在自证，不是达标）。
     */
    @Test
    fun `every actionable node the panel draws meets the touch floor`() {
        mount()
        val offenders = LinkedHashMap<String, Target>()
        val excluded = LinkedHashMap<String, Target>()
        var judged = 0
        listOf(replyLabel, suggestLabel, counselingLabel).forEach { label ->
            tapSegment(label)
            probe.actionableTargets(rule, "面板整屏·$label").forEach { t ->
                val inside = t.widthDp > 0f && t.heightDp > 0f &&
                    t.leftDp >= 0f && t.topDp >= 0f &&
                    t.leftDp + t.widthDp <= 360f + 0.5f && t.topDp + t.heightDp <= 1000f + 0.5f
                if (!inside) excluded[t.label] = t
                else {
                    judged++
                    if (t.tooSmall(probe.floorDp)) offenders[t.label] = t
                }
            }
        }
        assertTrue(
            "三档一共只判到 $judged 颗，低于样本下限 $MIN_JUDGED —— 这格在空转，" +
                "先查覆盖与筛掉的名单，别把它读成「全达标」：筛掉 " +
                excluded.values.joinToString { it.describe() },
            judged >= MIN_JUDGED
        )
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "面板整屏有 ${offenders.size} 颗能按的东西低于 ${probe.floorDp.toInt()}dp 见方" +
                    "（另有 ${excluded.size} 颗按读数筛掉：\n" +
                    excluded.values.joinToString("\n") { "    筛掉 " + it.describe() } + "\n）：\n" +
                    offenders.values.joinToString("\n") { "  " + it.describe() } +
                    "\n  修法：热区与视觉分两层——可点那一层自己垫到 48dp，" +
                    "胶囊/图标在里面居中；只放大外层容器而点击仍挂在子节点上等于没改。"
            )
        }
    }

    /**
     * ⚠ **这里原本有一格，被删掉了，理由要留着**：我一度把那颗「添加」改成
     * `clickable(enabled = canAdd)`，让空草稿时它"灰着还在"（页面唯一主动作那条合同要求的形状），
     * 并配了一格断言它带 Disabled。跑完红了两格 —— 都是**别人早就守着**的：
     * `ComposerAddButtonGatingTest.theAddEntryIsNotActionableWhileTheDraftIsBlank`
     * （"空草稿时 ➕ 不该带点击语义"）与 `…WithoutAFrame…`
     * （"没推帧时它已经带上点击语义 = CI 那 7 格的成因"）。
     * ⇒ "禁用是灰着还在"管的是**页面唯一主动作**（:479 / §2.1 主动发回退合同），
     * 次级入口的门控由它自己的守卫说了算。生产侧已退回原合同，这一格跟着删掉，
     * 别再把它当"漏掉的覆盖面"补回来。
     */
    companion object {
        /** 三档加起来至少该判到这么多颗；低于它就是这格没扫到东西，不是"全达标" */
        private const val MIN_JUDGED = 12
    }
}
