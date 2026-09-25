package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.model.UnderstandingReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.4 :523 第四刀——点踩原因面板（"反馈原因"是那份清单里最后一块）。
 *
 * **搬之前**这台仪器在同一块 360x900dp 挂载槽里量到（账本 §34 有原文），
 * 这一格的三条判据就是照着那三条欠账写的：
 *
 * | 改之前实量 | 这一格要求 |
 * |---|---|
 * | 16 颗可交互节点里 **14 颗不到 48dp**，清一色 19dp 高 | 每一颗都 ≥48dp |
 * | 10 颗 chip 的 selected / stateDescription / toggleable **三者全无**（"选中"只有 `✓ ` 字面量 + 底色） | chip 要在语义树上说出自己选没选 |
 * | 标题贴顶 **y = 8dp**，无遮罩 | 标题落在槽位中部（300–600dp） |
 *
 * 前两条是 §6.5 :531 / :532，第三条是 §6.4 :523 那句"拆成 …modal host"。
 * 挂载槽 900dp 高：让"居中"和"贴顶"差成几百 dp，不靠猜阈值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DislikeReasonPanelTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }
    private val density: Float get() = app.resources.displayMetrics.density

    private var lastSave: List<Any>? = null
    private var dismissed = 0

    private fun aCase(
        categories: List<FeedbackCategory>,
        reasons: List<String> = emptyList()
    ) = FeedbackCase(
        caseId = "case-1",
        schemeIdentityKey = "STYLE:B/DIRECTION:F",
        candidateReply = "那这周末带你去吃那家你提过的店",
        categories = categories,
        reasons = reasons
    )

    private fun mount(case: FeedbackCase?) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    DislikeReasonHost(
                        case = case,
                        onUpdateCase = { id, cats, reasons, note, better ->
                            lastSave = listOf(id, cats, reasons, note, better)
                        },
                        onNavigateToMessageEdit = {},
                        onNavigateToMemoryCorrection = {},
                        onDismiss = { dismissed++ }
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun titleTop(): Float? =
        rule.onAllNodes(hasText("这条回复哪里不满意？")).fetchSemanticsNodes()
            .firstOrNull()?.boundsInRoot?.let { it.top / density }

    /** 有案例才画得出来；「看得见」这件事本身也要一格守着（上一格那条判据的家族） */
    @Test
    fun `the title is drawn when a case exists`() {
        mount(aCase(listOf(FeedbackCategory.UNDERSTANDING_ERROR)))
        assertNotNull("有案例就该看得到标题", titleTop())
    }

    /** §6.5 :531——这一屏的可点东西**一颗都不许**低于 48dp（改之前 14/16 不达标） */
    @Test
    fun `every control in the dislike panel meets the touch floor`() {
        mount(aCase(listOf(FeedbackCategory.UNDERSTANDING_ERROR)))
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "点踩原因面板")
        // 反空跑：这把尺得真看得见那一群 chip，否则"全达标"可以是扫了个空集
        assertTrue(
            "该量到一级 chip + 二级原因 chip + 四颗动作，实到 ${targets.size}：" +
                targets.joinToString { it.describe() },
            targets.size >= 14
        )
    }

    /**
     * §6.5 :532——"选中"必须是语义树上的事实。
     *
     * 判据两头都要量：**选中的那颗**要报 On、**没选的那颗**要报 Off。
     * 只量一头的话，把 `value` 写死成 `true`（或写死成 `false`）都能过自己那一头。
     *
     * ⚠ 第一版这里是拿**中文标签的枚举**去筛 chip，结果漏掉了屏幕上正是选中态的
     * 那颗「✓ 理解错误」（`✓ ` 前缀只在选中时加），组里只剩 9 颗、断言红。
     * 换成按语义筛（`isToggle`）：要判的性质本身就是筛选条件，不再靠抄一遍文案。
     */
    @Test
    fun `chips announce whether they are selected`() {
        mount(aCase(listOf(FeedbackCategory.UNDERSTANDING_ERROR), listOf("角色错")))
        val targets = probe.actionableTargets(rule, "点踩原因面板")
        val group = targets.filter { it.isToggle }
        assertEquals(
            "三颗一级 + 七颗二级原因 chip 都该在组里，实到 ${group.size}：" +
                group.joinToString { it.describe() },
            3 + UnderstandingReasons.ALL.size, group.size
        )
        probe.assertSelectableAnnounceState(group, "点踩原因面板的选项组")

        val on = group.filter { it.toggleOn }
        assertEquals(
            "案例自带「理解错误」与二级「角色错」，该恰好两颗报选中态：" +
                group.joinToString { it.describe() },
            setOf("✓ 理解错误", "✓ 角色错"), on.map { it.label }.toSet()
        )
        assertTrue(
            "其余 ${group.size - on.size} 颗必须报未选中（把 value 写死的坏实现会红在这里）",
            group.size - on.size == group.size - 2
        )
    }

    /**
     * §6.4 :523——它是浮层，不是往页面里插一块。
     *
     * ⚠ 第一版写的是"标题落在 300–600dp"，那是照上一格那颗**很短的**「不对」浮层抄的阈值。
     * 这张表单高到 560dp 上限（`LbModalSheet` 自己那条 `heightIn(max=…)`），
     * 居中之后标题当然在 183dp——**红的不是实现，是我那条抄来的阈值**。
     *
     * 换成不依赖内容高度的几何判据：**居中的东西上下留白应当相等**。
     * 内联展开区（改之前的形状）上留白 8dp、下留白 ~595dp，一眼就被抓出来；
     * 而"把整块往下挪一百 dp"这种坏法照样红，固定阈值反而看不见。
     */
    @Test
    fun `the panel is vertically centered in the slot, not pinned to the top`() {
        mount(aCase(listOf(FeedbackCategory.UNDERSTANDING_ERROR)))
        val nodes = probe.actionableTargets(rule, "点踩原因面板")
        val topGap = nodes.minOf { it.topDp }
        val bottomEdge = nodes.maxOf { it.topDp + it.heightDp }
        val bottomGap = 900f - bottomEdge
        assertTrue(
            "上下留白应当接近相等（居中），实到上 ${topGap}dp / 下 ${bottomGap}dp。" +
                "改之前这块是内联展开区，上留白只有 8dp",
            kotlin.math.abs(topGap - bottomGap) < 60f
        )
        assertTrue(
            "上留白必须真的离开槽位顶部（居中判据的下半句），实到 ${topGap}dp",
            topGap > 100f
        )
    }

    /** 没有案例时不该画任何东西——显隐仍由 VM 那一侧决定，这里不另存一份 */
    @Test
    fun `with no case nothing is drawn`() {
        mount(null)
        assertEquals(
            "case 为 null 时树里不该有那颗标题", 0,
            rule.onAllNodes(hasText("这条回复哪里不满意？")).fetchSemanticsNodes().size
        )
        assertEquals("也不该有可点节点", 0,
            runCatching { probe.actionableTargets(rule, "空面板") }.getOrElse { emptyList() }.size)
    }

    /** 「跳过」关面板但**不提交**——点踩本身要留着，不强迫写作文 */
    @Test
    fun `skipping dismisses without saving`() {
        mount(aCase(emptyList()))
        rule.onAllNodes(hasText("跳过"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("跳过不该提交案例", null, lastSave)
        assertEquals("但该走一次 dismiss", 1, dismissed)
    }

    /** 「保存反馈」交回的是**当前草稿**，不是案例自带的旧值 */
    @Test
    fun `saving hands over the draft rather than the stored case`() {
        mount(aCase(listOf(FeedbackCategory.OTHER), emptyList()))
        rule.onAllNodes(hasText("表达不喜欢"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        rule.onAllNodes(hasText("保存反馈"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        val saved = lastSave
        assertNotNull("保存该回调一次", saved)
        @Suppress("UNCHECKED_CAST")
        val cats = saved!![1] as List<FeedbackCategory>
        assertEquals(
            "交回的必须是「自带的 OTHER 还在 + 刚点的表达不喜欢」，实到 $cats",
            listOf(FeedbackCategory.OTHER, FeedbackCategory.EXPRESSION_DISLIKE), cats
        )
        assertEquals("案例 id 要原样带回去", "case-1", saved[0])
    }

    /**
     * 点一颗原本**未选中**的 chip，语义要真的翻过来。
     *
     * ⚠ 这一格原本叫"a different case starts a different draft"——那是我起了个
     * 界面路径**构造不出来**的名字（本仪器一个测试只能 `setContent` 一次，
     * 换不了 caseId，所以"换一条案例"这件事它从头到尾没测到过）。
     * 名字与判据现在对齐：它测的是"Off → On"这一跳。
     */
    @Test
    fun `pressing an unselected chip flips its announced state`() {
        // 案例自带分类为空：否则「其他」一进来就是选中态（标签带 ✓），
        // "起始必须是 Off"那句前提根本不成立——第一版就是这么写错的。
        mount(aCase(emptyList()))
        // 找 chip 不能用"标签全等"：那颗 chip 一旦被点，标签就从「其他」变成「✓ 其他」，
        // 第二跳就查不到自己刚改过的东西了（第一版红在这里，报的还是"树里没有「其他」"，
        // 说的是个没发生过的理由）。判据取"去掉 ✓ 前缀之后相等"。
        fun chip(label: String): SemanticsProbe.Target =
            probe.actionableTargets(rule, "点踩原因面板")
                .firstOrNull { it.label.removePrefix("✓ ") == label }
                ?: throw AssertionError(
                    "树里没有「$label」这一颗；实到：" +
                        probe.actionableTargets(rule, "点踩原因面板")
                            .joinToString { it.describe() }
                )

        assertEquals("起始必须是未选中，否则这一格什么都没测到", "Off", chip("其他").toggleState)
        rule.onAllNodes(hasText("其他"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "点过之后语义要跟着翻，而且屏幕上那颗 chip 要开始带 ✓",
            "On", chip("其他").toggleState
        )
        assertEquals("选中态同时体现在文案上（眼睛和读屏说的是同一件事）", "✓ 其他", chip("其他").label)
    }

    private val SemanticsProbe.Target.toggleOn: Boolean
        get() = isToggle && toggleState?.contains("On", ignoreCase = true) == true &&
            toggleState != "Indeterminate"
}
