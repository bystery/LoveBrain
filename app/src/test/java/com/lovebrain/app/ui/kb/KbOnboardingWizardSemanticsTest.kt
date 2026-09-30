package com.lovebrain.app.ui.kb

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SemanticsProbe.Target
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.domain.OnboardingBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 建库向导搬进 `ui/kb/` 之后的**内容格**：这一屏除了"控件点得中"，还得**真的把题面画在树上**。
 *
 * 与 `ui/OnboardingScreenSemanticsTest` 的分工要说清：那一格量的是**控件**
 * （逐档热区下限、角色、名字、选中态、禁用态能不能解锁），它挂的是 `UiMatrix(360)` 那一格；
 * 这一格量的是**内容**（题面、五个选项、进度那句、页头那颗）——而且专门站在
 * §6.5 点名的最坏那一格（**最窄 320dp + 2.0 倍字**）上量。
 * 为什么这一族要另开一格：搬家最怕的不是控件坏掉，是"整块搬走之后某一屏其实是空的"——
 * 只判控件的那几格在内容被搬丢时反而会红得含糊（找不到可交互节点才报"一个都没测到"）。
 *
 * ⚠ 锚点仍是**内联中文字面量**（「第 1 步 · 共 5 步」「建空档案」）：这一屏的文案还没还债。
 * 题面与选项从 `domain/OnboardingBank` 取，不在测试里重抄一份中文。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KbOnboardingWizardSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val density get() = ctx.resources.displayMetrics.density
    private val probe by lazy { SemanticsProbe(density) }

    /** 两颗主动作的名字走资源（本机解析成英文），其余文案这一屏还没还债 */
    private val nextLabel: String get() = ctx.getString(R.string.kb_next_step)

    private fun mount(matrix: UiMatrix) {
        rule.setContent {
            val d = LocalDensity.current.density
            matrix.RenderIn(d) {
                OnboardingScreen(
                    onDismiss = {}, onSkip = {}, onComplete = {}, onCancelGenerating = {}
                )
            }
        }
        rule.waitForIdle()
    }

    private fun count(text: String, substring: Boolean = false): Int =
        rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().size

    @Test
    fun `the first step really draws its question, its options and its progress line`() {
        mount(UiMatrix(320, fontScale = 2.0f, note = "最窄 + 最大字（最坏组合）"))
        val q1 = OnboardingBank.q1
        val missing = mutableListOf<String>()
        if (count(q1.title) != 1) missing += "题面「$q1.title」实到 ${count(q1.title)} 颗"
        q1.options.forEach { opt ->
            if (count(opt.text) != 1) missing += "选项「${opt.text}」实到 ${count(opt.text)} 颗"
        }
        if (count("第 1 步 · 共 5 步") != 1) missing += "进度那句实到 ${count("第 1 步 · 共 5 步")} 颗"
        assertTrue(
            "320dp + 2.0 倍字这一格里，向导第 1 步的内容必须逐句都在语义树上（少了就是搬家搬丢了，" +
                "多了就是同一句话画了两遍）：$missing",
            missing.isEmpty()
        )
        // 证人：这棵树确实是那张问卷，不是别的空屏——页头那颗收尾入口也在
        assertEquals("页头那颗「建空档案」应当恰好一颗", 1, count("建空档案"))
    }

    /**
     * §6.5 三栏在最坏那一格上一起判：这一屏的唯一主动作要**有名字、有角色、够得着下限**。
     *
     * 没答题时它是灰的——那是生产合同的一部分（`LbButtonState.Disabled` 那一档），
     * 这里顺手钉住：灰着≠没画、也≠点不动之后整颗消失。
     */
    @Test
    fun `the forward action on the worst cell is a named button at the floor`() {
        mount(UiMatrix(320, fontScale = 2.0f))
        val forward = probe.actionableTargets(rule, "向导（320dp-font200）")
            .filter { it.label.contains(nextLabel) }
        assertEquals(
            "这一屏的唯一主动作应当恰好一颗「$nextLabel」，实到 " +
                probe.actionableTargets(rule, "向导（320dp-font200）").joinToString { it.describe() },
            1, forward.size
        )
        val t = forward.single()
        assertEquals("主动作得报成按钮：" + t.describe(), "Button", t.role)
        assertTrue("主动作得有名字，读屏不能只念「按钮」：" + t.describe(), t.labeled)
        assertTrue(
            "热区低于 ${probe.floorDp.toInt()}dp 就是 §6.5 :531 那一栏的欠账：" + t.describe(),
            !t.tooSmall(probe.floorDp)
        )
        assertTrue("没答题时它应当是灰着还在，而不是消失：" + t.describe(), t.disabled)
    }

    /**
     * 标准那一格（360dp / 1.0 倍字）上，页头那颗次级出口也要**报得出角色、念得出名字**。
     *
     * 它是 `TextButton`，`contentDescription` 没有、靠文案当名字——所以判 `labeled`；
     * 而 M3 那颗 48dp 是装饰（坑表 :531 已经在这条链上撞过三次），必须自己读尺寸。
     */
    @Test
    fun `the header exit on the standard cell announces itself as a labeled button`() {
        mount(UiMatrix(360))
        val targets: List<Target> = probe.actionableTargets(rule, "向导（360dp-font100）")
        val skip = targets.filter { it.label.contains("建空档案") }
        assertEquals("页头那颗收尾入口应当恰好一颗：" + targets.joinToString { it.describe() }, 1, skip.size)
        val t = skip.single()
        assertEquals("它是一处操作，得报成按钮：" + t.describe(), "Button", t.role)
        assertTrue("读屏念不出它是什么：" + t.describe(), t.labeled)
        assertTrue("热区不到 ${probe.floorDp.toInt()}dp：" + t.describe(), !t.tooSmall(probe.floorDp))
    }
}
