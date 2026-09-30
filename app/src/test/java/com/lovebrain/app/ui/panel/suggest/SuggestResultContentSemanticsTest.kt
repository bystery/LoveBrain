package com.lovebrain.app.ui.panel.suggest

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.feature.intent.IntentController
import com.lovebrain.app.model.DailyBriefUsage
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.SuggestInvite
import com.lovebrain.app.model.SuggestTip
import com.lovebrain.app.ui.panel.SuggestPanel
import com.lovebrain.app.viewmodel.LoveBrainViewModel
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
 * 锦囊**结果态内容块**搬进 `ui/panel/suggest/` 之后的语义树格子（工单 W-D）。
 *
 * 搬之前那七样内容（usage 条 / 阶段卡 / 分组表头 / 建议卡 / 邀约窗 / 避坑标题 / 避坑清单）
 * 里只有建议卡有格子（`ui/panel/SuggestTipCardSemanticsTest`），其余**一颗都没有**——
 * 也就是说"这次搬家把某一格搬空了"这种事当时根本量不出来。这一族格子补的就是那一段，
 * 判据是 §6.5 那三栏：**内容真在树上**、那颗可交互的要**有名字、有角色、够得着 48dp 下限**。
 *
 * ⚠ 最后两格挂的是**生产那一颗 `SuggestPanel`**：整页要 VM 才组合得起来，
 * 就按坑表那条办——`mockk` 把面板读的那几条 StateFlow 逐条桩住（形状抄
 * `ui/panel/SuggestCounselingTargetsTest` / `PanelErrorStatesSemanticsTest`，
 * 带泛型的流一条都不留给 relaxed），"要 VM 所以测不了"不成立。
 *
 * ⚠ 这些文案目前仍是**内联中文字面量**（这一族还没还债），所以本机英文环境下也是中文，
 * 判据可以写死；将来搬进 `res/values` 时要回来改成资源驱动（同 `OnboardingScreenSemanticsTest` 那句提醒）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestResultContentSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val density get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    private fun mount(content: @Composable () -> Unit) {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) { content() }
        }
        rule.waitForIdle()
    }

    private fun count(text: String, substring: Boolean = false): Int =
        rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().size

    private fun assertOnTree(what: String, texts: List<String>) {
        val missing = texts.filter { count(it) == 0 }
        assertTrue("$what 这些话没在语义树上（搬丢了）：$missing", missing.isEmpty())
    }

    // ────────────── 内容真在树上：逐颗挂 ──────────────

    @Test
    fun `the usage bar puts tokens cost elapsed and the partial flag on the tree`() {
        mount {
            SuggestUsageBar(
                usage = DailyBriefUsage(
                    promptTokens = 1200, completionTokens = 320,
                    costYuan = 0.0375, elapsedMs = 4200
                ),
                isPartial = true
            )
        }
        // 费用那条是 `"%.4f".format(...)` 拼出来的，测试用同一个表达式，不自己编第三种写法
        assertOnTree(
            "usage 条", listOf(
                "不完整", "↑1200  ↓320", "≈${"%.4f".format(0.0375)}元", "4s"
            )
        )
    }

    /** 反向证人：Provider 没回 usage 时那一档走的是"未知"，不是拿 0 冒充 */
    @Test
    fun `the usage bar says unknown when the provider returned nothing`() {
        mount { SuggestUsageBar(usage = null, isPartial = false) }
        assertOnTree("usage 条（空读数）", listOf("token 未知", "费用未知"))
        assertEquals("没有 partial 就不该出现「不完整」那枚标签", 0, count("不完整"))
    }

    @Test
    fun `the stage card names the stage the temperature and the goal`() {
        mount {
            SuggestStageCard(
                plan = DailySuggestion(stage = "回暖期", goal = "先把她说过的小事接住"),
                vectorMean = 0.42f
            )
        }
        assertOnTree("阶段卡", listOf("当前阶段", "回暖期", "关系温度 ${(0.42f * 100).toInt()}%", "本阶段目标", "先把她说过的小事接住"))
    }

    /** 反向证人：`suggest.md` 不强制输出 stage/goal，那一档这两行**不该画**，进度那行仍要在 */
    @Test
    fun `the stage card omits the optional lines when the plan has none`() {
        mount { SuggestStageCard(plan = DailySuggestion(), vectorMean = 0.5f) }
        assertEquals("stage 为空时不该画「当前阶段」那一行", 0, count("当前阶段"))
        assertEquals("goal 为空时不该画「本阶段目标」那一行", 0, count("本阶段目标"))
        assertOnTree("阶段卡（可选项都空）", listOf("关系温度 50%"))
    }

    /** 分组表头、邀约窗、避坑那两格一次挂全——它们是同一档结果的相邻几格 */
    @Test
    fun `the category header the invite window and the pitfall list all print their payload`() {
        mount {
            Column {
                TipCategoryHeader(category = "有机会再做")
                InviteSuggestionCard(
                    signal = "她主动提了两次周末",
                    suggestion = "周六下午那部新片，顺一句问要不要一起"
                )
                SuggestAvoidHeader()
                SuggestAvoidList(listOf("别在她明确说忙的时候连发三条", "别追问她同事的八卦"))
            }
        }
        assertOnTree(
            "结果态那三块", listOf(
                "有机会再做", "邀约窗口", "✓ 时机成熟", "依据：她主动提了两次周末",
                "周六下午那部新片，顺一句问要不要一起", "该阶段避坑",
                "别在她明确说忙的时候连发三条", "别追问她同事的八卦"
            )
        )
        assertEquals("✗ 那枚标记要跟着每一条", 2, count("✗"))
    }

    // ────────────── 整档：挂生产那一颗 SuggestPanel ──────────────

    private fun fakeIntents(): IntentController = mockk<IntentController>(relaxed = true).also {
        every { it.config } returns MutableStateFlow(IntentConfig())
        every { it.showEditor } returns MutableStateFlow(false)
    }

    /** 面板读的那几条流逐条桩住；`activeKb = null` ⇒ 标题行那颗意图 chip 不画，样本更干净 */
    private fun fakeVm(plan: DailySuggestion?): LoveBrainViewModel =
        mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
            every { vm.suggestion } returns MutableStateFlow(plan)
            every { vm.isSuggesting } returns MutableStateFlow(false)
            every { vm.currentVector } returns MutableStateFlow(mapOf("亲密" to 50, "信任" to 50))
            every { vm.streamingTips } returns MutableStateFlow(emptyList<SuggestTip>())
            every { vm.suggestError } returns MutableStateFlow<String?>(null)
            every { vm.intents } returns fakeIntents()
            every { vm.activeKb } returns MutableStateFlow(null)
        }

    private val plan = DailySuggestion(
        stage = "回暖期",
        goal = "先把她说过的小事接住",
        tips = listOf(
            SuggestTip(id = "t1", timingCategory = "现在可用", action = "晚上问她今天累不累", timing = "她下班后"),
            SuggestTip(id = "t2", timingCategory = "有机会再做", action = "约那部她提过的电影", timing = "聊到电影时")
        ),
        invite = SuggestInvite(signal = "她主动提了两次周末", suggestion = "周六下午那部，顺一句问"),
        avoid = listOf("别在她明确说忙的时候连发三条"),
        usage = DailyBriefUsage(promptTokens = 1200, completionTokens = 320, costYuan = 0.0375, elapsedMs = 4200),
        partial = false
    )

    private fun mountPanel(plan: DailySuggestion?) {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360).RenderIn(d) { SuggestPanel(viewModel = fakeVm(plan)) }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the panel's result state renders every cell of the moved block`() {
        mountPanel(plan)
        assertOnTree(
            "锦囊结果态（整档）", listOf(
                "今日锦囊", "重新生成", "↑1200  ↓320", "≈${"%.4f".format(0.0375)}元",
                "当前阶段", "回暖期", "关系温度 50%", "先把她说过的小事接住",
                "现在可用", "晚上问她今天累不累", "有机会再做", "约那部她提过的电影",
                "邀约窗口", "周六下午那部，顺一句问", "该阶段避坑", "别在她明确说忙的时候连发三条"
            )
        )
        // 证人：这一档确实挂在"有建议"那一格，不是挂到了空态/加载态上
        assertEquals("结果态不该再画那颗「生成锦囊」空态按钮", 0, count("生成锦囊"))
    }

    /**
     * 整档里那颗唯一的可交互内容控件（建议卡的折叠入口）：§6.5 三栏一起判。
     *
     * 用 `ScrollScan` 而不是裸读——`LazyColumn` 会把没露面的那颗裁成小读数（坑表 90）。
     */
    @Test
    fun `the fold entry inside the live result state is a named button at the floor`() {
        mountPanel(plan)
        val seen = ScrollScan(rule, probe).toBottom("锦囊结果态（整档）")
        val foldKeys = seen.keys.filter { it.contains("晚上问她今天累不累") || it.contains("约那部她提过的电影") }
        assertEquals(
            "两张建议卡各自要有一颗折叠入口，实到 $foldKeys（整树：" + seen.keys.sorted() + "）",
            2, foldKeys.size
        )
        val folds = foldKeys.map { seen.getValue(it) }
        val offenders = folds.filter { it.tooSmall(probe.floorDp) }
        assertTrue("折叠入口热区不到 ${probe.floorDp.toInt()}dp：" + offenders.joinToString { it.describe() }, offenders.isEmpty())
        val noRole = folds.filter { it.role != "Button" }
        assertTrue("折叠入口得报成按钮：" + noRole.joinToString { it.describe() }, noRole.isEmpty())
        val noName = folds.filter { !it.labeled }
        assertTrue("折叠入口要有可读名字：" + noName.joinToString { it.describe() }, noName.isEmpty())
        val silent = folds.filter { it.stateDescription.isNullOrBlank() }
        assertTrue("读屏要听得出这一条现在是收起的：" + silent.joinToString { it.describe() }, silent.isEmpty())
    }
}
