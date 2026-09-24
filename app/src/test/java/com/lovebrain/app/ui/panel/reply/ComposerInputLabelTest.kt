package com.lovebrain.app.ui.panel.reply

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.common.CompactInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 输入框在语义树上到底有没有名字（§6.5 无障碍第②栏：读屏念不出这是什么输入框）。
 *
 * 缺陷的形状很具体：placeholder 是**兄弟节点**的一行 `Text`，只在草稿为空时画出来。
 * TalkBack 因此只报「编辑框」，念不到那句提示；用户敲进第一个字之后连那行字都不在树上了，
 * 这一颗于是彻底没有名字。`ComposerAddButtonGatingTest` 里 describe() 的那条兜底分支
 * 「（输入框：有 EditableText，但没有文案也没有 contentDescription）」量的就是它。
 *
 * 修法是给可编辑节点本身挂 `contentDescription = placeholder`。
 * 这里刻意**不断言中文原文**：面板上那三句提示目前仍是硬编码在 `ReplyInput` 里的字面量
 * （那笔账属于资源驱动那条，本轮不顺手改用户可见文案），所以钉的是三件语言无关的事实：
 * 有名字、名字随角色变、override 传进来就是什么。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerInputLabelTest {

    @get:Rule
    val rule = createComposeRule()

    private fun editable(): SemanticsNode =
        rule.onNode(hasSetTextAction())
            .fetchSemanticsNode("输入框不在语义树上（hasSetTextAction 找不到节点）")

    /** 读屏会念的那句：从语义树读，不看源码 */
    private fun spokenLabel(): String? =
        editable().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("+")

    private fun SemanticsMatcher.clickable(): SemanticsMatcher = this and hasClickAction()

    private fun mountReply(draft: MutableState<String>, role: MutableState<ChatMessage.Role>) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = draft.value,
                    currentRole = role.value,
                    editingIndex = -1,
                    onDraftChange = { draft.value = it },
                    onRoleChange = { role.value = it },
                    onAdd = {},
                    onFocusChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun theComposerInputCarriesASpokenLabelFromTheFirstFrame() {
        val draft = mutableStateOf("")
        val role = mutableStateOf(ChatMessage.Role.ME)
        mountReply(draft, role)

        val label = spokenLabel()
        assertTrue("输入框在语义树上没有名字（读屏只会念「编辑框」）：实到 $label", !label.isNullOrBlank())
    }

    /** 切角色之后名字要跟着换——三句提示各说不同的事，共用一句等于没说 */
    @Test
    fun theLabelFollowsTheSelectedRole() {
        val draft = mutableStateOf("")
        val role = mutableStateOf(ChatMessage.Role.ME)
        mountReply(draft, role)

        val meLabel = spokenLabel()
        rule.onNode(hasText("她").clickable()).performClick()
        rule.mainClock.advanceTimeBy(16L)
        val herLabel = spokenLabel()

        assertNotEquals(
            "切到《她》之后输入框的名字没变，读屏仍会念上一角色的提示",
            meLabel, herLabel
        )
        assertTrue("名字仍不许是空：$herLabel", !herLabel.isNullOrBlank())
    }

    /** 输入之后 placeholder 那行 Text 不再画——那时 contentDescription 是唯一的名字来源 */
    @Test
    fun theLabelSurvivesTheFirstCharacter() {
        val draft = mutableStateOf("")
        val role = mutableStateOf(ChatMessage.Role.ME)
        mountReply(draft, role)

        rule.onNode(hasSetTextAction()).performTextInput("在吗")
        rule.mainClock.advanceTimeBy(16L)

        val label = spokenLabel()
        assertTrue(
            "敲了字之后输入框反而没名字了（placeholder 那行已经不画了）：实到 $label",
            !label.isNullOrBlank()
        )
        assertEquals("输入本身要落到草稿里", "在吗", draft.value)
    }

    /** 主动发复用同一颗时传 placeholderOverride：名字就是那句 override，不能是回复态的默认 */
    @Test
    fun anOverridePlaceholderBecomesTheSpokenLabel() {
        val draft = mutableStateOf("")
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = draft.value,
                    currentRole = ChatMessage.Role.ME,
                    editingIndex = -1,
                    onDraftChange = { draft.value = it },
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    showRoleChips = false,
                    showAddButton = false,
                    placeholderOverride = "PROACTIVE_LABEL_SENTINEL"
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        assertEquals(
            "override 没传到语义上——两颗输入场景共用一个组件时，名字必须跟着场景走",
            "PROACTIVE_LABEL_SENTINEL", spokenLabel()
        )
    }

    /** 第二个实例：问卷页与供应商弹窗用的 CompactInput 同样有过这颗无名输入框 */
    @Test
    fun compactInputAlsoCarriesASpokenLabel() {
        val draft = mutableStateOf("")
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                CompactInput(
                    value = draft.value,
                    onValueChange = { draft.value = it },
                    placeholder = "COMPACT_LABEL_SENTINEL"
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        assertEquals("COMPACT_LABEL_SENTINEL", spokenLabel())
        // 视觉上那行提示照旧画着（本轮只加语义名字，不改视觉）
        rule.onNodeWithText("COMPACT_LABEL_SENTINEL").assertExists()
    }
}
