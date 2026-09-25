package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyAnalysis
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.ui.panel.counseling.CounselingPanel
import com.lovebrain.app.ui.panel.reply.ResultArea
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
 * 错误档与未配置档——§6.3 点名的四态里"出事的那两格"，在这一族屏上**从来没挂起来过**。
 *
 * 之前只量过成功档（`ResultAreaTouchTargetsTest` 交的是 `GenerateResult.Success`，
 * 两块面板交的是"有建议 / 无错误"）。所以那两格守卫跑得再绿，
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

    private fun fakeVm(suggestError: String?, counselingError: String?): LoveBrainViewModel =
        mockk<LoveBrainViewModel>(relaxed = true).also { vm ->
            // 泛型流一条都不留给 relaxed（relaxed 交回泛型 mock，`.value` 一取就 ClassCastException）
            every { vm.suggestion } returns MutableStateFlow<DailySuggestion?>(null)
            every { vm.isSuggesting } returns MutableStateFlow(false)
            every { vm.currentVector } returns MutableStateFlow(emptyMap())
            every { vm.streamingTips } returns MutableStateFlow(emptyList())
            every { vm.suggestError } returns MutableStateFlow(suggestError)
            every { vm.intentConfig } returns MutableStateFlow(IntentConfig())
            every { vm.showIntentEditor } returns MutableStateFlow(false)
            every { vm.activeKb } returns MutableStateFlow(null)
            every { vm.counselingDraft } returns MutableStateFlow("")
            every { vm.counselingResult } returns MutableStateFlow<String?>(null)
            every { vm.counselingError } returns MutableStateFlow(counselingError)
            every { vm.isCounseling } returns MutableStateFlow(false)
            every { vm.counselingStreaming } returns MutableStateFlow("")
        }

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** 三颗重试的标签**都**走资源（本格把它们并成一条），锚点必须 `getString` 取 */
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

    private fun mountSuggest(error: String?) {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360, 1000).RenderIn(d) { SuggestPanel(viewModel = fakeVm(error, null)) }
        }
        rule.waitForIdle()
    }

    private fun mountCounseling(error: String?) {
        rule.setContent {
            val d = LocalDensity.current.density
            UiMatrix(360, 1000).RenderIn(d) {
                CounselingPanel(viewModel = fakeVm(null, error), onFocusChange = {})
            }
        }
        rule.waitForIdle()
    }

    /** 只看**完整落在视口里**的节点；排除项连尺寸一起打进信息，并压样本下限（同账本 §40/§44） */
    private fun assertFloorAndRoles(what: String, minSample: Int = 2) {
        val all = probe.actionableTargets(rule, what)
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

    @Test
    fun `the result error state keeps a pressable retry`() {
        mountResult(GenerateResult.Error("网络断了，这轮没生成成"), ready = true)
        // 错误档只剩这一颗动作：样本下限压到 1，但"没动作"本身要红（§6.3：错误态要就地给出口）
        val targets = probe.actionableTargets(rule, "结果区·错误档")
        assertEquals(
            "错误档就地就该给一颗重试出口（本机解析成 $retryLabel）：" + targets.joinToString { it.describe() },
            1, targets.count { it.label == retryLabel }
        )
        assertFloorAndRoles("结果区·错误档", minSample = 1)
    }

    @Test
    fun `the not-configured state gives one way into settings`() {
        mountResult(null, ready = false)
        val targets = probe.actionableTargets(rule, "结果区·未配置档")
        assertEquals(
            "未配置供应商这一档该且只该有一颗引导动作（§6.3：空态不做死路）：" +
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
    fun `the suggest panel error state keeps a pressable retry`() {
        mountSuggest("这轮没生成成：网络中断")
        val targets = probe.actionableTargets(rule, "锦囊·错误档")
        assertTrue(
            "锦囊错误档找不出重试出口（本机解析成 $retryLabel）：" + targets.joinToString { it.describe() },
            targets.any { it.label == retryLabel }
        )
        assertFloorAndRoles("锦囊·错误档")
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
     * 成功档那颗「⋯」工具入口：尺寸早就垫到 48dp，但**角色从没声明过**。
     *
     * 这一条是"同族缺陷要回扫"（坑表 96）查出来的：成功档上一格量过热区与名字，
     * 没量角色 ⇒ 语义树里它是 `role=无`，读屏念得出"⋯"、说不出它能点开菜单。
     */
    @Test
    fun `the result utility trigger announces that it opens a menu`() {
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
        // 锚点用它自己的 contentDescription（那条资源），不按"最左边/最靠上"猜——
        // 第一版按左边缘挑，挑到的是方案卡上那颗「复制」，格子红了但红错了对象。
        val menuDescription = ctx.getString(com.lovebrain.app.R.string.panel_result_menu)
        val trigger = probe.actionableTargets(rule, "结果区·工具入口")
            .firstOrNull { t -> t.contentDescriptions.any { it.contains(menuDescription, ignoreCase = true) } }
        assertTrue(
            "量不到那颗工具入口：" + probe.actionableTargets(rule, "结果区·工具入口").joinToString { it.describe() },
            trigger != null
        )
        assertEquals(
            "点开下拉菜单的那颗要报 DropdownList（不是「没有角色」）：" + trigger!!.describe(),
            "DropdownList", trigger.role
        )
    }
}
