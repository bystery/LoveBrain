package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 第6节第1条 第 6 行归并 `CorrectionRecordRow`（纠正中心里"一条记录 = 一行"）之后的读数账。
 *
 * 这一行有四种东西要说清楚，归并最容易弄丢后面两样：
 * ① 类型标签（`暂时别提·今天剩余` 这种，跟着 enum 走）；
 * ② 说的是哪条记忆（memoryId）；
 * ③ 补正内容（`→ …` 那一行，**没有补正时不该留一行空白**）；
 * ④ 尾部那颗「撤销」的可访问名与热区。
 *
 * ⚠ ③④ 两样各对应一发真实风险：
 *  - `LbSettingRow` 的说明槽要是无条件画，没有补正内容的那条就会多出一颗空文本节点
 *    （同一个仓库里 `LbSettingRowStateTest` 已经把"空白不许画出幽灵节点"写成规矩）；
 *  - 「撤销」原来由浮层动作词表画（48 **见方**），搬进行为的尾部槽之后，
 *    那一槽原来只垫高度——单看"还能点到"会漏，必须读两轴。
 *
 * 挂载沿用 `CorrectionCenterTest` 那块 360x900dp 的槽，判据全在语义树上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CorrectionRecordRowSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    private val muted = MemoryCorrection(
        memoryId = "mem-muted",
        action = CorrectionAction.MUTED,
        muteDuration = MuteDuration.TODAY,
    )
    private val wrong = MemoryCorrection(
        memoryId = "mem-wrong",
        action = CorrectionAction.WRONG,
        replacementText = "她把答辩改到下周了",
    )
    private val records = linkedMapOf(
        "mem-muted" to muted,
        "mem-wrong" to wrong,
    )

    private fun mount(corrections: Map<String, MemoryCorrection> = records) {
        val holder = CorrectionCenterHolder()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Box(modifier = Modifier.width(360.dp).height(900.dp)) {
                    CorrectionCenterHost(
                        holder = holder,
                        corrections = corrections,
                        onUndoCorrection = {}
                    )
                }
            }
        }
        holder.open()
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 语义树上"文本是空白"的节点数——幽灵行就是从这里现形的 */
    private fun blankTextNodeCount(): Int = rule.onAllNodes(
        SemanticsMatcher("节点带空白 Text") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.isBlank() } == true
        }
    ).fetchSemanticsNodes().size

    @Test
    fun `a record row still reads its tag, its memory and the replacement`() {
        mount()
        // ① 类型标签 + 时长跟着 enum 走
        rule.onNodeWithText("暂时别提·今天剩余").assertExists()
        rule.onNodeWithText("不对").assertExists()
        // ② 说的是哪条
        rule.onNodeWithText("mem-muted").assertExists()
        rule.onNodeWithText("mem-wrong").assertExists()
        // ③ 补正内容
        rule.onNodeWithText("→ 她把答辩改到下周了").assertExists()
    }

    /**
     * ④ 每一行尾部那颗撤销：读得出名字、报得出角色、两轴都够下限。
     *
     * 数量也要钉住：一条记录一颗。归并时要是有人在行里再叠一层点击，这里就涨。
     */
    @Test
    fun `each record row carries one named undo action that fills the floor`() {
        mount()
        val undos = probe.actionableTargets(rule, "纠正中心").filter { it.label == "撤销" }
        assertEquals(
            "两条记录该有两颗撤销，实到 ${undos.size}：" + undos.joinToString { it.describe() },
            2, undos.size
        )
        undos.forEach { t ->
            assertEquals("撤销得报得出按钮角色：" + t.describe(), "Button", t.role)
            assertTrue(
                "撤销的热区两轴都要 ≥${probe.floorDp.toInt()}dp：" + t.describe(),
                !t.tooSmall(probe.floorDp)
            )
        }
        // 退出入口还在（它和撤销共用同一颗动作实现，归并之后也该一起过尺）
        val close = probe.actionableTargets(rule, "纠正中心").filter { it.label == "关闭" }
        assertEquals("退出入口只该有一颗：" + close.joinToString { it.describe() }, 1, close.size)
    }

    /**
     * ③ 的反面：没有补正内容的那一条，不该在行里留一颗空文本节点。
     *
     * 这一格判的是"说明槽空白时到底画不画"——归并成统一行组件时最容易顺手改成"永远画"，
     * 于是列表里每条静音记录多出一行空白，而读屏什么也不念（看不见、也听不见）。
     */
    @Test
    fun `a record without replacement text leaves no blank line`() {
        mount()
        assertEquals(
            "有静音记录（无补正内容）时，树里不该有空白文本节点",
            0, blankTextNodeCount()
        )
        rule.onNodeWithText("暂时别提·今天剩余").assertExists()
    }

    /** 空中心仍然自己说话，而且这格不该长出任何行（反证：把空态判据弄坏就会在这里红） */
    @Test
    fun `an empty center draws no record rows at all`() {
        mount(emptyMap())
        rule.onNodeWithText("暂无纠正记录。在「本轮参考」中可对记忆发起纠正。").assertExists()
        val undos = probe.actionableTargets(rule, "空纠正中心").filter { it.label == "撤销" }
        assertEquals("空态不该有撤销：" + undos.joinToString { it.describe() }, 0, undos.size)
    }
}
