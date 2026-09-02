package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.model.ChatMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 *  契约守卫（  步骤 6 增补 4 条）：
 * 《想法》= 第三种消息（Role.IDEA）+ userHint 状态链路废除后的收集契约。
 *
 * ① 枚举 label = "想法"（MessageList chip / PromptBuilder 记录段渲染共用）；
 * ② 多条 IDEA 按序以 \n 拼接，空列表返回 ""（PromptBuilder isNotBlank 分支天然兜底）；
 * ③ HER/ME 消息不混入收集（只认 Role.IDEA）；
 * ④ 收集源 = 实时消息列表（无独立残留快照）：消息移除后 getUserHint 同步归空。
 *    （演进表原措辞 "nextRound 清空后返回 ''" 需预置 GenerateResult.Success 才能驱动 nextRound，
 *     纯 JVM 不可直接构造；④ 以等价契约替代——收集无残留快照正是"清空后必空"的根源，
 *     语义覆盖一致，偏差已在转交单向 checker 说明。）
 *
 * 纯 JVM 构造沿用 LoveBrainViewModelR5RegressionTest 先例：7 依赖全 mock + setMain 接管。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoveBrainViewModelIdeaHintTest {

    private lateinit var prefs: SecurePrefs

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    /** 构造 LoveBrainViewModel：init 读取面显式桩（同 R2RegressionTest/R5RegressionTest 先例） */
    private fun newViewModel(): LoveBrainViewModel {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            knowledgeRepo = mockk(relaxed = true),
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true)
        )
    }

    /** ①：枚举 label 契约（chip 与 prompt 记录段共用） */
    @Test
    fun idea_role_label_is_xiangfa() {
        assertEquals("想法", ChatMessage.Role.IDEA.label)
    }

    /** ②：多条 IDEA 按序 \n 拼接；空列表返回 "" */
    @Test
    fun idea_messages_join_with_newline_and_empty_when_none() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        assertEquals("", vm.getUserHint())

        vm.addMessage(ChatMessage.Role.IDEA, "别讲道理")
        vm.addMessage(ChatMessage.Role.IDEA, "先哄她")
        assertEquals("别讲道理\n先哄她", vm.getUserHint())
    }

    /** ③：HER/ME 消息不混入收集（只认 Role.IDEA） */
    @Test
    fun her_and_me_messages_not_collected() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.addMessage(ChatMessage.Role.HER, "今天真的好累啊")
        vm.addMessage(ChatMessage.Role.IDEA, "别讲道理，哄她")
        vm.addMessage(ChatMessage.Role.ME, "那早点休息吧")
        assertEquals("别讲道理，哄她", vm.getUserHint())
    }

    /** ④：收集源 = 实时消息列表，无独立残留快照（lifecycle 契约等价锁，见类 KDoc） */
    @Test
    fun idea_hint_follows_live_message_list() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.addMessage(ChatMessage.Role.IDEA, "哄她")
        assertEquals("哄她", vm.getUserHint())

        val ideaId = vm.messages.value.first { it.role == ChatMessage.Role.IDEA }.id
        vm.removeMessageById(ideaId)
        assertEquals("", vm.getUserHint())
    }
}
