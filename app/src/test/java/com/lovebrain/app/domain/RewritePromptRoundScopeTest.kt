package com.lovebrain.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * 「仅看本轮」开着时，改写那一发**不许带**知识库的持久内容（指导书 第7节第3条：
 * 回复、主动发、改写三条链沿同一条范围规则，而不是只修其中一个 prompt）。
 *
 * 这一格为什么单独存在：2026-10-08 实测时回复与主动发两条链都已经读冻结的
 * `onlyThisRound`（`PromptBuilder.buildReplyUserPromptOnlyThisRound` 与
 * `GenerationEngine` 的主动发分支），**只有改写链整条不认识这颗开关**——
 * `LoveBrainViewModel.buildRewriteUserPrompt` 无条件读 `understand/style.md`
 * （点赞记进库的表达偏好）并把持续意图一起发出去。开关开着却把库里的持久内容
 * 发给模型，正是 第14节第2条 末句点名的「为提升个性化偷读历史」。
 *
 * 判据是「发了什么／没发什么」而不是「代码里有没有 if」：
 * - 开关开 → 偏好与意图都归 null，`RewritePrompt.user` 那一段整段省略（空白即省略是它自己的合同）；
 * - 开关关 → 两段原样带走，不能把关掉状态下的正常请求也饿掉；
 * - 坏实现（无视开关直接透传）在第一条红，第二条把它钉回反方向。
 */
class RewritePromptRoundScopeTest {

    private val style = "少用感叹号，别追问在干嘛"
    private val intent = "这周想把见面的事定下来"

    /** 开关开着：两段持久内容都不许出现在这一发请求里 */
    @org.junit.Test
    fun `round scope strips both the stored style and the ongoing intent`() {
        val (scopedStyle, scopedIntent) =
            RewritePrompt.styleAndIntentForScope(style, intent, onlyThisRound = true)
        assertNull("表达偏好是库里的持久件，仅看本轮不得偷读", scopedStyle)
        assertNull("持续意图同样是持久内容，不得从改写这一发绕进去", scopedIntent)

        val prompt = RewritePrompt.user(
            originalReply = "那周末再说",
            instruction = "短一点",
            recentChat = "她：周末有空吗\n我：应该有",
            intentText = scopedIntent,
            ideaHint = "她最近在准备考试",
            style = scopedStyle
        )
        assertEquals(
            "只留本轮真实对话与军师备注：$prompt",
            false,
            prompt.contains("我的表达偏好") || prompt.contains("当前意图")
        )
        assertEquals("本轮真实对话仍要在场", true, prompt.contains("她：周末有空吗"))
        assertEquals("军师备注仍要在场", true, prompt.contains("她最近在准备考试"))
    }

    /** 开关关着：两段照旧带上，别把正常轮的上下文一起饿掉 */
    @org.junit.Test
    fun `an ordinary round still carries the stored style and the ongoing intent`() {
        val (scopedStyle, scopedIntent) =
            RewritePrompt.styleAndIntentForScope(style, intent, onlyThisRound = false)
        assertEquals(style, scopedStyle)
        assertEquals(intent, scopedIntent)

        val prompt = RewritePrompt.user(
            originalReply = "那周末再说",
            instruction = "短一点",
            recentChat = "她：周末有空吗",
            intentText = scopedIntent,
            ideaHint = null,
            style = scopedStyle
        )
        assertEquals("偏好与意图都该出现", true, prompt.contains("我的表达偏好") && prompt.contains("当前意图"))
    }
}
