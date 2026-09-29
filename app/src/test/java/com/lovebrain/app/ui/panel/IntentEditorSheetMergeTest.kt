package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
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
import java.io.File

/**
 * §6.1 归并的证据：`IntentEditorDialog` 那扇持续意图浮层。
 *
 * 归并之前它**已经**站在对的所有者上（面板跑在 overlay 窗口里，Material 的 `AlertDialog`
 * 会抛 `WindowManager.BadTokenException`，那正是 `LbModalSheet` 存在的理由），
 * 但壳里还自己排了一遍版面，而且**这一扇浮层没有标题**——
 * 树的第一格是一段说明文字，别的浮层第一格都是 `LbModalSheetTitle`。
 * 现在：壳只起浮层，标题抬进 `LbModalSheetTitle`、两颗出口走同一份 `LbDialogAction` 词表，
 * 标题那句话只是换了槽位，**一条文案都没新增**（账记在 `UiStringLiteralBudgetTest` 的两栏）。
 *
 * ⚠ 挂载口径：`LbModalSheet` **不是另一扇窗口**（同一棵 ComposeView 里自画的遮罩 + 卡片），
 * 所以这一整扇浮层在本机挂得上——照 `SheetProbeTest` / `RecordSentDialogSheetTest` 那一份挂法
 * （`UiMatrix(360).RenderIn` + 推进一帧）。账本 §45.1 那堵「`Dialog` 窗口 + 文本框永不空闲」的墙
 * 在这里不适用，因此这一格不需要像 `ProviderFormBody` 那样把内容搬出窗口才能量。
 * 浮层卡片自带 `verticalScroll`，底部的两颗出口按 `ScrollScan` 的口径滚过一遍取最大面积
 * （滚动容器会把没露面的那颗裁成小读数，坑表 85/90）。
 *
 * 三格各判一件事：① 标题读得到、且只有一份；② 「取消 / 保存」两颗出口还在、有名字、过 48dp；
 * ③ 结构上证壳真的只起一扇 `LbModalSheet`（这一半读结构，因为它判的是"没被搬坏"）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntentEditorSheetMergeTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val probe by lazy { SemanticsProbe(ctx.resources.displayMetrics.density) }

    /** 标题那句话的前半截——内联中文字面量（还没还债），所以本机两种语言下都是这个值 */
    private val titlePrefix = "设置一个持续的对话目标"

    private fun mount(text: String = "周末约她看电影", enabled: Boolean = true) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                IntentEditorDialog(
                    text = text,
                    enabled = enabled,
                    onSave = { _, _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun `the merged sheet still shows its title and shows it exactly once`() {
        mount()
        val nodes = rule.onAllNodes(hasText(titlePrefix, substring = true)).fetchSemanticsNodes()
        assertEquals(
            "浮层顶上那句话归并后仍要读得到，而且只有一份（两份就是各写各的）：" +
                "实到 ${nodes.size} 个节点",
            1, nodes.size
        )
        // 证人：这棵树确实是那扇浮层，不是空挂载——两颗出口都在树里
        assertTrue(
            "挂载证人：量不到任何文案节点说明浮层压根没起来",
            rule.onAllNodesWithText("有效期").fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun `cancel and save in the merged sheet are labeled and meet the touch floor`() {
        mount()
        val seen = ScrollScan(rule, probe).toBottom("持续意图浮层（归并后）")
        val missing = listOf("取消", "保存").filter { !seen.containsKey(it) }
        assertTrue(
            "两颗出口都得还在，没量到：$missing；实到：" + seen.keys.sorted(),
            missing.isEmpty()
        )
        val exits = listOf("取消", "保存").map { seen.getValue(it) }
        val unlabeled = exits.filter { !it.labeled }
        assertTrue("出口得有可读名字：" + unlabeled.joinToString { it.describe() }, unlabeled.isEmpty())
        val offenders = exits.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "归并用的就是 `LbModalSheetActions` 那颗动作行（它把 26dp / 22dp 的旧缺陷垫到了下限），" +
                "这里不达标说明出口没走共用形状：" + offenders.joinToString { it.describe() },
            offenders.isEmpty()
        )
        val noRole = exits.filter { it.role != "Button" }
        assertTrue("两颗出口都得报成按钮：" + noRole.joinToString { it.describe() }, noRole.isEmpty())
    }

    @Test
    fun `the shell only raises a LbModalSheet and draws nothing of its own`() {
        val file = File("src/main/java/com/lovebrain/app/ui/panel/SuggestPanel.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/SuggestPanel.kt")
        assertTrue("$file 不在了——这一格会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))

        val shell = code.substringAfter("internal fun IntentEditorDialog(")
            .substringBefore("\n@Composable")
        assertEquals(
            "壳里只该起一扇设计系统的浮层：" + shell,
            1, Regex("""LbModalSheet\(""").findAll(shell).count()
        )
        val tells = listOf("Box(", "Column(", "Row(", "Dialog(", ".background(", ".clickable", "BasicTextField")
            .filter { it in shell }
        assertTrue("壳里又自己画了东西：" + tells, tells.isEmpty())

        // 标题槽：整份文件只画一次，两颗出口也只在同一处
        assertEquals("标题槽全文件只该有一处", 1, Regex("LbModalSheetTitle\\(").findAll(code).count())
        assertEquals("动作行全文件只该有一处", 1, Regex("LbModalSheetActions\\(").findAll(code).count())
        assertEquals(
            "「" + titlePrefix + "」这句话只该存在一份（两份就是 §6.1 要收掉的那件事）",
            1, Regex(titlePrefix).findAll(code).count()
        )
    }
}
