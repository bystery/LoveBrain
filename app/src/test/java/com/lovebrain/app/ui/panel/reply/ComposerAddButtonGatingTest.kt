package com.lovebrain.app.ui.panel.reply

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「输入 → 点➕」这一步在 CI 上为什么会静默失效。
 *
 * CI 里 OverlayGenerateSmokeTest 有 7 格死在同一句
 * `等待超时：点过➕之后 MessageList 要有这一条`。那三行长这样：
 *
 *     onNode(hasSetTextAction()).performTextInput(text)
 *     onNodeWithContentDescription("添加").performClick()
 *     pumpUntil(...) { vm.messages.value.isNotEmpty() }   // ← 超时在这里
 *
 * 而 mountPanel() 为了伺候生成中那条无限动画，把 `mainClock.autoAdvance` 关了。
 * 关掉之后**没有帧就没有重组**：performTextInput 写进状态的那句草稿还没变成
 * canAdd=true 的那一次重组，生产 ReplyInput 里的
 * `.then(if (canAdd) Modifier.clickable(...) else Modifier)` 这时仍然不带 clickable。
 * 更麻烦的是 performClick() 只做触摸注入，不检查节点有没有点击语义，
 * 所以这一拳既不抛异常也不报错——7 格于是全部卡在同一句超时上。
 *
 * 这条用例就是量它的尺：
 *  A 不推帧就点 → ➕ 当时确实不带点击语义，点击确实什么都没启动（复现 CI 症状）；
 *  B 推到它带上点击语义再点 → 一次点击恰好投递一条消息。
 * B 就是夹具该照的样子：等的是"这个入口真的能点"，不是"我猜它已经能点了"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerAddButtonGatingTest {

    @get:Rule
    val rule = createComposeRule()

    private var adds = 0
    private var draftAtAdd = ""

    /** 生产接线：草稿由宿主持有（真面板里是 ViewModel），➕ 读的就是这一份 */
    private fun mountComposer(autoAdvance: Boolean) {
        val draft: MutableState<String> = mutableStateOf("")
        adds = 0
        draftAtAdd = ""
        rule.mainClock.autoAdvance = autoAdvance
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = draft.value,
                    currentRole = ChatMessage.Role.ME,
                    editingIndex = -1,
                    onDraftChange = { draft.value = it },
                    onRoleChange = {},
                    onAdd = { adds++; draftAtAdd = draft.value },
                    onFocusChange = {}
                )
            }
        }
        // 与真面板 mountPanel() 一样：组合完先补一帧，让输入行本身出现在树上
        rule.mainClock.advanceTimeBy(16L)
    }

    /** ➕ 此刻到底点得动吗——读的是语义树，不是源码里的 if 条件 */
    private fun addButtonIsActionable(): Boolean =
        rule.onNodeWithContentDescription("添加")
            .fetchSemanticsNode("➕ 入口没在语义树里")
            .config
            .contains(SemanticsActions.OnClick)

    /** A：复现 CI 的静默失效——不推帧就点，什么都没启动 */
    @Test
    fun typingWithoutAFrameLeavesTheAddEntryUnclickableAndTheTapGoesNowhere() {
        mountComposer(autoAdvance = false)

        rule.onNode(hasSetTextAction()).performTextInput("在吗")

        assertFalse(
            "复现失败：没推帧的情况下 ➕ 已经带上点击语义了——" +
                "那 CI 那 7 格的成因就不是这条，得换判据重查",
            addButtonIsActionable()
        )
        rule.onNodeWithContentDescription("添加").performClick()
        rule.mainClock.advanceTimeBy(64L)

        assertEquals(
            "点击确实什么都没启动（这正是 CI 上「消息永远是空的」那一步）",
            0, adds
        )
    }

    /** 生产合同：草稿为空时 ➕ 本来就不该能点（置灰 = 不能点，不是消失） */
    @Test
    fun theAddEntryIsNotActionableWhileTheDraftIsBlank() {
        mountComposer(autoAdvance = true)
        rule.waitForIdle()

        assertFalse("空草稿时 ➕ 不该带点击语义", addButtonIsActionable())
    }

    /** B：夹具该照的样子——等到入口真的能点，再点，就恰好投递一条 */
    @Test
    fun pumpingUntilTheAddEntryIsActionableMakesExactlyOneMessageLand() {
        mountComposer(autoAdvance = false)

        rule.onNode(hasSetTextAction()).performTextInput("在吗")

        // 推进到"这个入口真的带点击语义"为止；推不到就直接红，不许闷头点下去
        var pumpedMs = 0L
        while (!addButtonIsActionable() && pumpedMs < 2_000L) {
            rule.mainClock.advanceTimeBy(16L)
            pumpedMs += 16
        }
        assertTrue("推了 ${pumpedMs}ms 帧仍未带上点击语义", addButtonIsActionable())

        rule.onNodeWithContentDescription("添加").performClick()
        rule.mainClock.advanceTimeBy(64L)

        assertEquals("一次点击应恰好投递一条消息", 1, adds)
        assertEquals("投递的必须是刚输入的那句草稿", "在吗", draftAtAdd)

        // 输入行这一排到底有几个东西点得动——逐个点名，别只写一个魔数：
        // 将来数目变了要一眼看出是"角色 chip 多了"还是"多了个没人管的入口"。
        val actionable = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val chips = actionable.filter { textOf(it) in setOf("她", "我", "想法") }
        val addEntries = actionable.filter { descOf(it) == "添加" }
        val editable = actionable.filter {
            it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText)
        }
        assertEquals("角色 chip 应恰好 3 个（她/我/想法）：" + actionable.map { describe(it) }, 3, chips.size)
        assertEquals("➕ 入口应恰好 1 个：" + actionable.map { describe(it) }, 1, addEntries.size)
        assertEquals(
            "除 3 chip + ➕ 之外，点得动的只应是输入框本身（它带 EditableText 语义）：" +
                actionable.map { describe(it) },
            actionable.size - 4, editable.size
        )
    }

    private fun textOf(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            ?.firstOrNull()?.text

    private fun descOf(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
            ?.joinToString("+")

    private fun describe(node: androidx.compose.ui.semantics.SemanticsNode): String =
        textOf(node) ?: descOf(node)
            ?: if (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText)) {
                "（输入框：有 EditableText，但没有文案也没有 contentDescription）"
            } else {
                "（既无文案也无 contentDescription）"
            }
}
