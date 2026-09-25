package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SemanticsProbe.Target
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 新建知识库那张五步问卷（`OnboardingScreen`）——§6.5 :531/:532 在这一屏的第一次逐颗量。
 *
 * 这一屏以前没进过仪器。它不是浮层窗口（`ScreenPage` 只是普通外框），所以**不用抽组件也能挂**——
 * 门槛只是"从来没人点过它"。先做了一次一次性诊断
 * （已收档 `_temp/ZzOnboardingWalkTest.kt.retired-2026-09-26`）证明五题能靠 `performClick`
 * 答完、最后一档那颗「完成」在屏上，这一格才敢把最后一档写成守卫而不是欠账。
 *
 * ⚠ 锚点是**内联中文字面量**（「下一步」「建空档案」「＋ 补充其他情况」…）：
 * 这一屏的文案还没还债，所以判据稳；将来搬进资源时要回来改成资源驱动。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }
    private val scan by lazy { ScrollScan(rule, probe) }

    /**
     * 两颗主动作的名字**走资源**（就在这一格之内：它们从内联债搬进了 `values` + `values-en`），
     * 判据必须 `getString` 取——本机环境解析出英文，写死"下一步"会 0 命中。
     */
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val nextLabel: String get() = ctx.getString(com.lovebrain.app.R.string.kb_next_step)
    private val finishLabel: String get() = ctx.getString(com.lovebrain.app.R.string.kb_finish_profile)

    private fun mount() {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            UiMatrix(360, 1000).RenderIn(deviceDensity) {
                OnboardingScreen(
                    onDismiss = {}, onSkip = {}, onComplete = {}, onCancelGenerating = {}
                )
            }
        }
        rule.waitForIdle()
    }

    /** 只量首屏那一档（滚一遍，防有东西在折叠线以下） */
    private fun firstStep(): Map<String, Target> {
        mount()
        return scan.toBottom("OnboardingScreen")
    }

    private fun optionLabels(seen: Collection<Target>): List<String> =
        seen.filter { it.role == "RadioButton" || it.role == "Checkbox" }.map { it.label }

    /** 答掉当前这一题：点内容区里第一颗还没被选中的选项 */
    private fun answerCurrentStep() {
        val targets = probe.actionableTargets(rule, "问卷-找选项")
        val candidates = targets.filter {
            !it.disabled &&
                !it.label.contains(nextLabel) && !it.label.contains(finishLabel) &&
                !it.label.contains("建空档案") && !it.label.contains("Back") &&
                !it.label.contains("补充其他情况") && !it.label.contains("收起补充") &&
                (it.role == "RadioButton" || it.role == "Checkbox" || it.selected != null)
        }
        val pick = candidates.firstOrNull { it.selected != true } ?: candidates.firstOrNull()
        check(pick != null) {
            "这一档找不出可点的选项，量到：" + targets.joinToString { it.describe() }
        }
        rule.onAllNodes(hasClickAction() and hasText(pick.label)).onFirst().performClick()
        rule.waitForIdle()
    }

    /**
     * 把五步问卷**逐档走完**，每一档都滚一遍、把量到的节点**累计**进一张表。
     *
     * 为什么不能只测首屏：那颗「＋ 补充其他情况」是从第 2 步才出现的
     * （本机实扫：第 1 步那 8 类里根本没有它）。只扫首屏就会以为这一屏很干净——
     * 与横排卡片"首屏只露三张半"是同一类漏判，只不过滚动条换成了对话流程。
     *
     * 步数不写死：见到「完成」就停。上限只是防止点不动时无限打转，
     * **撞上上限直接判失败**——不许把"没走到"当成"这一档没问题"。
     */
    private fun walkAllSteps(): Map<String, Target> {
        mount()
        val all = LinkedHashMap<String, Target>()
        val maxRounds = 8
        var reachedFinish = false
        for (round in 1..maxRounds) {
            scan.toBottom("OnboardingScreen").forEach { (key, t) ->
                val cur = all[key]
                if (cur == null || cur.widthDp * cur.heightDp < t.widthDp * t.heightDp) all[key] = t
            }
            if (all.keys.any { it.contains(finishLabel) }) { reachedFinish = true; break }
            answerCurrentStep()
            val next = scan.toBottom("OnboardingScreen").values.firstOrNull { it.label.contains(nextLabel) }
            check(next != null && !next.disabled) {
                "第 $round 档答过题之后「下一步」还是按不动的：" + (next?.describe() ?: "树里已经没有下一步那颗") +
                    "；这一档量到：" + optionLabels(scan.toBottom("OnboardingScreen").values)
            }
            rule.onAllNodes(hasClickAction() and hasText(nextLabel)).onFirst().performClick()
            rule.waitForIdle()
        }
        assertTrue(
            "走了 $maxRounds 轮还没见到最后一档那颗「完成」（本机解析成 $finishLabel），" +
                "最后一档不能算判过（累计到：" + all.keys + "）",
            reachedFinish
        )
        return all
    }

    @Test
    fun `the wizard's first screen reports its options and both header actions`() {
        val seen = firstStep()
        val absent = listOf(
            "Back", "建空档案", "刚认识/刚加上好友", "有点暧昧/在拉扯",
            "已经在一起了", "闹矛盾了/僵住了", "快分了/已经分了", nextLabel
        ).filter { !seen.containsKey(it) }
        assertTrue("首屏该量到这 8 类，没量到：" + absent + "；实际：" + seen.keys.sorted(), absent.isEmpty())
    }

    @Test
    fun `every control across the five steps meets the 48dp floor`() {
        val seen = walkAllSteps()
        val offenders = seen.values.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "问卷（五档走完累计）有 ${offenders.size} 类可交互节点小于 ${probe.floorDp.toInt()}dp：\n" +
                offenders.joinToString("\n") { "  " + it.describe() },
            offenders.isEmpty()
        )
    }

    @Test
    fun `every non-editable control across the five steps announces a role`() {
        val seen = walkAllSteps()
        val missing = seen.values.filter { !it.editable && it.role == "无" }
        assertTrue(
            "问卷里有 ${missing.size} 类可交互节点没声明角色（读屏念得出字、说不出它是按钮）：\n" +
                missing.joinToString("\n") { "  " + it.describe() },
            missing.isEmpty()
        )
    }

    @Test
    fun `every control across the five steps can be named`() {
        val seen = walkAllSteps()
        val unlabeled = seen.values.filter { !it.labeled }
        assertTrue(
            "有 ${unlabeled.size} 类节点没名字，读屏只会念「按钮」：\n" +
                unlabeled.joinToString("\n") { "  " + it.describe() },
            unlabeled.isEmpty()
        )
    }

    /**
     * 最后一档那颗「完成」：够大、报得出角色、且**能按**。
     *
     * 它不在首屏——不答完五题就看不见。所以这条既是对话流程的证人，
     * 也是"这一档没被静默跳过"的证人（第三把尺 `containerColor = Primary` 的两处
     * 就在这里：「完成」与「下一步」）。
     */
    @Test
    fun `the finish action on the last step is a button and is pressable`() {
        val seen = walkAllSteps()
        val finish = seen.values.filter { it.label.contains(finishLabel) }
        assertEquals(
            "最后一档的主动作应当恰好一颗：" + seen.values.joinToString { it.describe() },
            1, finish.size
        )
        val t = finish.single()
        assertEquals("主动作得报成按钮：" + t.describe(), "Button", t.role)
        assertTrue("最后一档的主动作不能是灰的：" + t.describe(), !t.disabled)
        assertTrue("热区不到 ${probe.floorDp.toInt()}dp：" + t.describe(), !t.tooSmall(probe.floorDp))
    }

    /**
     * §6.5 :532 第二栏——每题那些选项得让读屏听得出"现在选的是哪一颗"。
     *
     * 本机实扫到的是 `role=RadioButton selected=false` / `role=Checkbox selected=false`，
     * 也就是说状态**有**在播报。这一格把它钉住：换成不带 `selected` 的实现要能红。
     */
    @Test
    fun `each option announces whether it is the current answer`() {
        val seen = firstStep()
        val options = seen.values.filter { it.role == "RadioButton" || it.role == "Checkbox" }
        assertTrue("第 1 步就该有选项，实际量到的角色：" + seen.values.map { it.role }.distinct(),
            options.isNotEmpty())
        probe.assertSelectableAnnounceState(options, "问卷第 1 步的选项")
    }

    /**
     * 答一题，「下一步」必须**从灰变能按**，那颗选项自己也得报成已选。
     *
     * 一条钉住两件事：禁用态不是装饰（点了真会解锁），选中态不是只给眼睛看的
     * （`selected` 会从 false 翻成 true）。上一格在表单那边学到的是同一课——
     * 只判"灰着还在"而不判"填齐能按"，把可用态写死成禁用也能全绿。
     */
    @Test
    fun `answering a step unlocks the forward action and marks the chosen option`() {
        val before = firstStep()
        val forward = before.getValue(nextLabel)
        assertTrue("没答题时「下一步」就该是灰的：" + forward.describe(), forward.disabled)
        val picked = before.values.first { it.role == "RadioButton" }
        assertEquals("点之前那颗选项不该已经报选中：" + picked.describe(), false, picked.selected)

        answerCurrentStep()

        val after = scan.toBottom("OnboardingScreen")
        val now = after.getValue(nextLabel)
        assertTrue("答过一次之后「下一步」必须能按（还是灰的说明禁用态是装饰）：" + now.describe(),
            !now.disabled)
        val chosen = after.getValue(picked.label)
        assertEquals("被选中的那颗得报 selected=true，读屏才念得出「已选中」：" + chosen.describe(),
            true, chosen.selected)
    }
}
