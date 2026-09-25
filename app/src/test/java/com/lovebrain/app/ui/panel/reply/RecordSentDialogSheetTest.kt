package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
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
 * `记录实际发送`这一屏：**用户要点的那两颗出口，点得到吗？**
 *
 * 它是 §29 那套 `LbModalSheet` 的第一个"由页面自己拼内容"的用户界面
 * （`5d6b71a` 之后组件叫 `RecordSentFlowHost`，状态在 `RecordSentFlow` 手里）。
 * 之前它自己画遮罩与按钮，本机语义树量到的是 `28x19dp` 与 `96x19dp`
 * （还有一颗 360x1000dp、把标题当成自己名字的"整屏按钮"）——旧值留在 `SheetProbeTest` 的注释里。
 * 这几格钉的是改完之后的形状，并且**逐颗读 `boundsInRoot`**，不读源码里的数字。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordSentDialogSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    /**
     * 状态从"参数传进去"改成"持有者持有"之后，装配要和面板一致：
     * 先造 flow、`open(prefill)`（要 saving 就再 `beginSaving()`），再组合。
     * 这样这四格测的就是生产那一条路径，不是一个为测试留的旁门。
     *
     * ⚠ 顺序有讲究：**开状态要排在 `setContent` 之前**。
     * 反过来写（先组合、再 `flow.open()`、再推进一帧）时，`while saving` 那一格
     * 把 `autoAdvance` 关了，多出来的那一帧不会来，整棵浮层压根没进树——
     * 探针于是报"一个可点击节点都没测到"，看着像实现被删了，其实是夹具慢了一帧。
     */
    private fun mount(
        prefill: String = "",
        saving: Boolean = false,
        confirm: (String) -> Unit = {}
    ): RecordSentFlow {
        val flow = RecordSentFlow()
        flow.open(prefill = prefill)
        if (saving) flow.beginSaving()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                RecordSentFlowHost(
                    flow = flow,
                    onConfirm = { f -> confirm(f.draft.trim()) }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        return flow
    }

    /** 预填之后：两颗出口 + 那颗输入框都在、都有名字、都过下限，且**没有**整屏大的"按钮" */
    @Test
    fun `with a prefilled message both exits are present, labeled and big enough`() {
        mount(prefill = "周末那部电影我请")
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "记录实际发送")
        probe.assertAllActionableLabeled(rule, "记录实际发送")
        // 出口 = 会改变"写不写得进去"的那两颗；输入框也算可交互节点，但它不是出口
        val exits = targets.filter { it.label in setOf("取消", "确认已发送并记录") }
        assertEquals(
            "这颗浮层上的出口就该是取消与确认两颗：" + targets.joinToString { it.describe() },
            2, exits.size
        )
        exits.forEach {
            assertEquals("${it.label} 的高度得到 48dp（旧形状实量 19dp）", 48f, it.heightDp, 0.6f)
        }
        assertEquals("整棵树三个可交互节点都该有名字（输入框那颗以前是空的）",
            3, targets.count { it.labeled })
        assertTrue("整块遮罩不该再是一颗可点击节点：" + targets.joinToString { it.describe() },
            targets.none { it.widthDp > 320f })
    }

    /**
     * 旧形状里"没填内容"是 `onClick` 里的 `if (text.isNotBlank())`——按钮长得能点，点了没反应。
     * 现在只剩 `enabled` 一处判据：空文本时它是**灰着还在**。
     */
    @Test
    fun `an empty draft leaves the confirm present but disabled`() {
        mount(prefill = "")
        val targets = probe.actionableTargets(rule, "记录实际发送（空稿）")
        val confirm = targets.first { it.label == "确认已发送并记录" }
        assertTrue("空稿时确认必须带 Disabled 语义：" + confirm.describe(), confirm.disabled)
        assertEquals("灰着也得有 48dp 热区", 48f, confirm.heightDp, 0.6f)
    }

    /** 保存中：两个出口都灰、都不许靠点空白关闭（进度反馈留在正文那一行） */
    @Test
    fun `while saving the exits stay put but go inert`() {
        rule.mainClock.autoAdvance = false
        mount(prefill = "已发", saving = true)
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        val targets = probe.actionableTargets(rule, "记录实际发送（保存中）")
        assertTrue("保存中两颗出口都该带 Disabled：" + targets.joinToString { it.describe() },
            targets.all { it.disabled })
        assertTrue("「保存中…」要还在屏幕上", rule.onAllNodesWithText("保存中…")
            .fetchSemanticsNodes().isNotEmpty())
    }

    /** 确认把**当前正文**交出去（旧形状里这一步藏在 onClick 的 if 后面） */
    @Test
    fun `confirming hands over the trimmed draft`() {
        var got: String? = null
        mount(prefill = "  周末一起看  ") { got = it }
        rule.onAllNodesWithText("确认已发送并记录")[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("应交出去除首尾空白的正文", "周末一起看", got)
    }

}
