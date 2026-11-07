package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 批量导入三处接线的守卫（外部审计 R1-1 / R1-2 / R1-3）：
 *
 * · R1-1 **陈旧解析结果**：粘贴原文 raw 与解析结果 parsed 分开保存——用户点解析后再
 *   编辑原文，parsed 若不失效，提交键仍按旧结果导入。修法是"原文变化即失效解析结果"
 *   （parsedRaw 记住解析时刻的原文，原文一变预览即收回、必须重新解析）。
 *   这一格钉的是：解析 → 改原文 → 预览收回、提交不再按旧结果走；重新解析后预览回来。
 * · R1-2 **无障碍长按无动作**：➕ 的读屏 `onLongClick` 语义旧写法只 `return true` 不调
 *   batchImport()——TalkBack 用户宣布动作成功、实际什么都没发生。这一格从语义树上
 *   直接调那份动作，验证它确实打开批量导入（与手势长按同一行为）。
 * · R1-3 **键盘/焦点接线缺口**：粘贴框的 BasicTextField 没接编辑意图上报，悬浮窗
 *   PASSIVE 态窗口 FLAG_NOT_FOCUSABLE、IME 永远弹不出来。这一格验证触碰粘贴框
 *   会触发 onInputIntent（宿主由此 PASSIVE→EDITING）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchImportWiringTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** 冒号行格式样本（BatchTranscriptParser 认的三种格式之一） */
    private val sample = "我：在吗\n她：好的"

    private var inputIntents = 0

    private fun mountSheet(onSubmit: (List<com.lovebrain.app.feature.composer.BatchImportDraft>) -> Unit = {}) {
        inputIntents = 0
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                BatchImportSheet(
                    visible = true,
                    onDismiss = {},
                    onSubmit = onSubmit,
                    onInputIntent = { inputIntents++ }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** R1-1：解析之后编辑原文，旧预览必须当场失效（不得按旧结果提交），重新解析才恢复 */
    @Test
    fun editingTheRawTextAfterParsingInvalidatesTheStaleParseResult() {
        mountSheet()

        rule.onNode(hasSetTextAction()).performTextInput(sample)
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).performClick()
        rule.mainClock.advanceTimeBy(16L)

        // 证人 ①：解析真的出了预览（逐条指定发言人的提示在场）
        assertEquals(
            "证人：解析后应看到预览（pick_speaker 提示在场），样本本身该解析出 2 条",
            1, rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_pick_speaker)))
                .fetchSemanticsNodes().size
        )
        // 证人 ②：chip 已切到「重新解析」档
        assertEquals(
            "证人：解析过一次后 chip 应显示「重新解析」",
            1, rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_reparse)))
                .fetchSemanticsNodes().size
        )

        // 审计 R1-1 的原场景：解析后再编辑原文
        rule.onNode(hasSetTextAction()).performTextInput("（补一句后来才想起的）")
        rule.mainClock.advanceTimeBy(16L)

        // 旧预览必须收回（parsed 作废），不是"预览还在、提交按旧结果"
        assertEquals(
            "原文变化后旧预览必须失效：pick_speaker 提示不该还在",
            0, rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_pick_speaker)))
                .fetchSemanticsNodes().size
        )
        // chip 回到「解析预览」档：用户必须重新点解析才能再拿到预览
        assertEquals(
            "原文变化后 chip 应回到「解析预览」",
            1, rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_parse)))
                .fetchSemanticsNodes().size
        )
        assertEquals(
            "原文变化后不应残留「重新解析」那颗",
            0, rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_reparse)))
                .fetchSemanticsNodes().size
        )

        // 重新解析同一版原文 → 预览恢复（失效是要求重新解析，不是永久锁死）
        rule.onNodeWithText(ctx.getString(R.string.batch_import_parse)).performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "重新解析后预览应恢复",
            1, rule.onAllNodes(hasText(ctx.getString(R.string.batch_import_pick_speaker)))
                .fetchSemanticsNodes().size
        )
    }

    private fun rawEditableCount(): Int =
        rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size

    /** R1-2：读屏的长按动作必须真的打开批量导入（与手势长按同一行为，不是只 return true） */
    @Test
    fun theAccessibilityLongClickActionActuallyOpensTheBatchImportSheet() {
        var imports = 0
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "",
                    currentRole = com.lovebrain.app.model.ChatMessage.Role.ME,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    onBatchImport = { imports++ }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        // 语义树里其他控件（文本选择等）也可能声明 OnLongClick；这里按 a11y 资源名认 ➕ 那颗
        val importLabel = ctx.getString(R.string.a11y_batch_import)
        val longClickNodes = allNodes().filter {
            it.config.getOrNull(SemanticsActions.OnLongClick)?.label == importLabel
        }
        assertEquals(
            "挂了 onBatchImport 时语义树上该恰有一颗名为「$importLabel」的长按节点（➕ 那颗），" +
                "实到 ${longClickNodes.size}",
            1, longClickNodes.size
        )
        val action = longClickNodes.single().config[SemanticsActions.OnLongClick]
        // 直接从语义树调那份动作——这正是 TalkBack 执行的路径；它必须真的开浮层
        val handled = action.action?.invoke()
        assertEquals("动作必须报告已处理（旧写法只 return true 不干活）", true, handled)
        assertEquals("语义长按必须触发批量导入入口", 1, imports)
    }

    /** R1-2 反向证人：没传 onBatchImport 时语义树上不该有批量导入那颗长按节点（不给没接线的屏留假入口） */
    @Test
    fun withoutBatchImportEntryThereIsNoLongClickSemantics() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "",
                    currentRole = com.lovebrain.app.model.ChatMessage.Role.ME,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val importLabel = ctx.getString(R.string.a11y_batch_import)
        assertEquals(
            "onBatchImport = null 时语义树上不许有批量导入的长按入口",
            0, allNodes().count { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == importLabel }
        )
    }

    /** R1-3：触碰粘贴框必须上报编辑意图（悬浮窗宿主由此把窗口切进 EDITING、IME 才能弹出） */
    @Test
    fun touchingThePasteFieldReportsAnInputIntent() {
        mountSheet()

        rule.onNode(hasSetTextAction()).performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(16L)

        assertTrue(
            "触碰粘贴框至少上报一次编辑意图（边沿触发：每次手势只报一次，但一次必须有）",
            inputIntents >= 1
        )

        // 第二次触碰再报一次：手势边界内不抖动（同一手势不重复上报），新手势再报
        val before = inputIntents
        rule.onNode(hasSetTextAction()).performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "再次触碰应再上报（窗口可能已被外部触碰退回 PASSIVE，每次进编辑都得报）",
            before + 1, inputIntents
        )
    }

    /** 语义树全量收集（isRoot 起步、按 children 走到底）——不依赖任何单一 matcher 的存在性 */
    private fun allNodes(): List<SemanticsNode> {
        val acc = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            acc += node
            node.children.forEach { walk(it) }
        }
        rule.onAllNodes(isRoot()).fetchSemanticsNodes().forEach { walk(it) }
        return acc
    }
}
