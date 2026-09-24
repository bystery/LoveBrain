package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ComposerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §2.1 那张"交互合同应永久钉死"的表，钉在能**真跑**的地方。
 *
 * 为什么在 JVM 再写一遍 androidTest 已经写过的场景：那批 instrumentation 目前有 19 条
 * 真失败、且要 CI 的 emulator 才跑得动，等于"合同没有守卫"。这条走 `testDebugUnitTest`，
 * CI 的 verify job 每次都跑，本机也能跑。两边都留着，不互相替换。
 *
 * 合同四行 + 一条通则：
 * 1. REPLY、无结果 → 全宽「生成回复 · N条消息」，N=0 时禁用（但仍在那儿，不是消失）
 * 2. REPLY、有结果 → 「重试」/「记入知识库」两颗，主动发不许占这个位置
 * 3. PROACTIVE、空闲 → 全宽「生成开场」
 * 4. 任一生成中 → 唯一停止入口
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyPrimaryActionsContractTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private val matrix = UiMatrix(360)

    private fun mount(
        composerMode: ComposerMode,
        isGenerating: Boolean = false,
        isProactive: Boolean = false,
        hasReplyResult: Boolean = false,
        messageCount: Int = 0
    ) {
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                ReplyPrimaryActions(
                    composerMode = composerMode,
                    isGenerating = isGenerating,
                    isProactive = isProactive,
                    hasReplyResult = hasReplyResult,
                    messageCount = messageCount,
                    onGenerateReply = {},
                    onGenerateProactive = {},
                    onRetry = {},
                    onSaveToKb = {},
                    onStop = {}
                )
            }
        }
    }

    /** 合同第 1 行：REPLY 无结果 = 全宽「生成回复 · N条消息」，且高度就是热区下限 */
    @Test
    fun `reply mode with no result shows one full width generate button carrying the count`() {
        mount(ComposerMode.REPLY, messageCount = 3, hasReplyResult = false)
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "主操作区")
        assertEquals(
            "无结果时主操作区只该有一颗全宽生成按钮：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        assertEquals("Generate reply · 3 messages", targets.single().label)
        assertTrue(
            "按钮应当铺满这一行的宽度（360dp 槽位），实测 " + targets.single().describe(),
            targets.single().widthDp > 300f
        )
    }

    /** 合同第 1 行的后半：N=0 时禁用——是"灰着不能点"，不是"没了" */
    @Test
    fun `with zero captured messages the generate button stays visible but disabled`() {
        mount(ComposerMode.REPLY, messageCount = 0, hasReplyResult = false)
        val targets = probe.actionableTargets(rule, "主操作区")
        assertEquals(
            "N=0 时按钮必须还画得出来（只是不能点），消失就不是同一份合同了：" +
                targets.joinToString { it.describe() },
            1, targets.size
        )
        assertEquals("Generate reply", targets.single().label)
        assertTrue("N=0 时按钮必须带 disabled 语义：" + targets.single().describe(), targets.single().disabled)
    }

    /** 合同第 2 行：有结果 = 重试 / 记入知识库，主动发不占位 */
    @Test
    fun `with a result the primary slot is retry and save-to-kb, never the opener`() {
        mount(ComposerMode.REPLY, hasReplyResult = true, messageCount = 2)
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "主操作区")
        assertEquals(
            "有结果时应当恰好两颗：" + targets.joinToString { it.describe() },
            listOf("Retry", "Save to knowledge base"),
            targets.map { it.label }.sorted()
        )
        assertTrue(
            "主动发不得挤回这颗按钮的位置：" + targets.joinToString { it.describe() },
            targets.none { it.label.contains("opener", ignoreCase = true) }
        )
    }

    /** 合同第 3 行：PROACTIVE 空闲 = 全宽「生成开场」 */
    @Test
    fun `proactive idle shows one full width generate-opener button`() {
        mount(ComposerMode.PROACTIVE, messageCount = 0)
        val targets = probe.assertAllActionableMeetTouchFloor(rule, "主操作区")
        assertEquals(
            "PROACTIVE 空闲应当只有一颗：" + targets.joinToString { it.describe() },
            1, targets.size
        )
        assertEquals("Generate opener", targets.single().label)
    }

    /**
     * 合同第 4 行：任一生成中 = 唯一停止入口。
     *
     * 生成中的按钮带无限脉冲动画，所以这一格把测试时钟改成手动推进——
     * 否则 `waitForIdle()` 等不到"空闲"那一刻（自动推进下动画永远不结束）。
     */
    @Test
    fun `while a reply is generating the only entry is stop`() {
        rule.mainClock.autoAdvance = false
        mount(ComposerMode.REPLY, isGenerating = true, messageCount = 2)
        assertSingleStopEntry("回复生成中")
    }

    /** 主动发生成中：走 STOP 那条分支（回复生成中走的是 LOADING 分支），两条都只能有一个入口 */
    @Test
    fun `while an opener is generating the only entry is stop`() {
        rule.mainClock.autoAdvance = false
        val generating = androidx.compose.runtime.mutableStateOf(false)
        val proactive = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                ReplyPrimaryActions(
                    composerMode = ComposerMode.REPLY,
                    isGenerating = generating.value,
                    isProactive = proactive.value,
                    hasReplyResult = false,
                    messageCount = 2,
                    onGenerateReply = {},
                    onGenerateProactive = {},
                    onRetry = {},
                    onSaveToKb = {},
                    onStop = {}
                )
            }
        }
        assertSingleStopEntry("主动发生成中")
    }

    private fun assertSingleStopEntry(whenLabel: String) {
        repeat(4) { rule.mainClock.advanceTimeByFrame() }
        val targets = probe.actionableTargets(rule, "主操作区")
        assertEquals(
            "$whenLabel 时只能有一个停止入口：" + targets.joinToString { it.describe() },
            1, targets.size
        )
    }
}
