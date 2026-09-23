package com.lovebrain.app.ui.panel.reply

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * S1-01 / S1-03（审计 §2「没有真实测试覆盖点击 MessageList 空态蓝字只切模式」、
 * §8.1 第 4 条）：空态蓝字 = 主动发入口组件级契约。
 *
 * 组件级能证明的部分：
 * · 空态整棵树只有一个可点击语义节点，就是蓝字入口 —— 空态里没有、也不能有
 *   任何直接触发生成的入口；
 * · 点击只回调 onEmptyAction（生产里接的是 viewModel.toggleProactiveMode()），
 *   次数恰好为 1；
 * · 再加一条消息后空态入口消失。
 *
 * 「Engine/Provider 调用次数仍为 0」需要真链路计数器，
 * 见 com.lovebrain.app.service.OverlayGenerateSmokeTest 里用 fake Provider
 * requestCount 断言的同名用例。
 */
class MessageListEmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val emptyEntryText = "还没有聊天记录，点这里主动发一条"
    private val activeEntryText = "主动发模式已开启，点击关闭"

    private fun setList(
        messages: List<ChatMessage>,
        onEmptyAction: (() -> Unit)?,
        proactiveActive: Boolean = false
    ) {
        composeRule.setContent {
            MessageList(
                messages = messages,
                editingIndex = -1,
                onReorder = { _, _ -> },
                onEdit = { },
                onDelete = { },
                onEmptyAction = onEmptyAction,
                proactiveActive = proactiveActive
            )
        }
    }

    @Test
    fun emptyState_blueEntryIsTheOnlyClickableThingAndFiresExactlyOnce() {
        val emptyActionCalls = AtomicInteger(0)
        setList(messages = emptyList(), onEmptyAction = { emptyActionCalls.incrementAndGet() })

        composeRule.onNodeWithText(emptyEntryText).assertIsDisplayed()
        // 空态里不得出现任何生成入口（生成动作只属于 ReplyPrimaryActions）
        composeRule.onNodeWithText("生成回复").assertDoesNotExist()
        composeRule.onNodeWithText("生成开场").assertDoesNotExist()
        // 唯一可点击节点 = 蓝字入口
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)

        composeRule.onNodeWithText(emptyEntryText).performClick()

        assertEquals("空态蓝字只应回调一次模式切换", 1, emptyActionCalls.get())
    }

    @Test
    fun emptyState_activeModeCopyStillOnlyTogglesMode() {
        val emptyActionCalls = AtomicInteger(0)
        setList(
            messages = emptyList(),
            onEmptyAction = { emptyActionCalls.incrementAndGet() },
            proactiveActive = true
        )

        composeRule.onNodeWithText(activeEntryText).assertIsDisplayed()
        composeRule.onNodeWithText(emptyEntryText).assertDoesNotExist()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)

        composeRule.onNodeWithText(activeEntryText).performClick()

        assertEquals("再点一次仍只是关闭主动发模式，回调 1 次", 1, emptyActionCalls.get())
    }

    /** onEmptyAction 为 null（无入口宿主）时，空态文本不应是个能点的按钮 */
    @Test
    fun emptyState_withoutHostCallback_exposesNoClickAction() {
        setList(messages = emptyList(), onEmptyAction = null)

        composeRule.onNodeWithText(emptyEntryText).assertIsDisplayed()
        // clickable{} 恒挂，但回调为 null 时不得触达任何生成入口：
        // 整棵树的可点击节点只允许是入口本身，且点击不产生任何副作用
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onNodeWithText(emptyEntryText).performClick()
    }

    @Test
    fun withMessages_emptyEntryDisappearsAndEntryNeverFires() {
        val emptyActionCalls = AtomicInteger(0)
        setList(
            messages = listOf(ChatMessage(role = ChatMessage.Role.HER, content = "你最近是不是很忙")),
            onEmptyAction = { emptyActionCalls.incrementAndGet() }
        )

        composeRule.onNodeWithText("你最近是不是很忙").assertIsDisplayed()
        composeRule.onNodeWithText(emptyEntryText).assertDoesNotExist()
        assertEquals("有消息时不得触发主动发入口", 0, emptyActionCalls.get())
    }

    /** 审计 §2 反面护栏：一旦有人把主动发拆回「双半按钮」，此用例立即红 */
    @Test
    fun emptyState_entryIsPlainTextAffordance_notASplitGenerateButton() {
        val emptyActionCalls = AtomicInteger(0)
        setList(messages = emptyList(), onEmptyAction = { emptyActionCalls.incrementAndGet() })

        composeRule.onNodeWithText(emptyEntryText).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("主动发").assertDoesNotExist()
        composeRule.onNodeWithText("生成回复 · 0条消息").assertDoesNotExist()
    }
}
