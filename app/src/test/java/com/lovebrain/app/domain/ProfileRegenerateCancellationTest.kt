package com.lovebrain.app.domain

import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.RawGenerationResult
import com.lovebrain.app.model.ProfileSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-2: Profile regenerate production-path cancellation test.
 *
 * 测试真正实例化 KnowledgeTriggerCoordinator，使用可控 fake/mock DeepSeekRepository：
 * - generateRawWithMetadata 启动后 suspend（模拟模型请求进行中）
 * - 调用真实 regenerateProfile()，随后 cancel 上层 Job
 * - 验证：
 *   - provider coroutine 收到 cancellation（suspend 被打断）
 *   - 后续 retry attempt 不发生
 *   - onProfileSuggestion 不发生
 *   - append reflect_history 不发生
 *   - CancellationException 没有被转 EMPTY 或 PROVIDER_ERROR
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRegenerateCancellationTest {

    @Test
    fun `regenerateProfile cancellation propagates to provider and does not invoke callbacks`() = runTest {
        // Track whether the provider suspend was actually entered and then cancelled
        var providerEntered = false
        var providerCancelled = false
        var profileSuggestionCalled = false
        var reflectHistoryAppended = false

        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        coEvery { knowledgeRepo.appendFileWithRevisionCheck(any(), any(), any(), any()) } coAnswers {
            reflectHistoryAppended = true
            true
        }

        val deepSeekRepo = mockk<DeepSeekRepository>()
        // generateRawWithMetadata 启动后 suspend 直到被 cancel
        coEvery { deepSeekRepo.generateRawWithMetadata(any(), any()) } coAnswers {
            providerEntered = true
            try {
                kotlinx.coroutines.delay(10000)
                // 如果 delay 没有被取消，返回一个有效结果（不应该走到这里）
                RawGenerationResult(content = "", finishReason = null)
            } catch (e: CancellationException) {
                providerCancelled = true
                throw e
            }
        }

        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        coEvery { promptBuilder.buildReflectSystemPrompt() } returns "system prompt"
        coEvery { promptBuilder.buildReflectUserPrompt(any()) } returns "user prompt"

        val callbacks = mockk<KnowledgeTriggerCoordinator.Callbacks>(relaxed = true)
        coEvery { callbacks.onProfileSuggestion(any()) } answers {
            profileSuggestionCalled = true
            Unit
        }

        val coordinator = KnowledgeTriggerCoordinator(
            knowledgeRepo = knowledgeRepo,
            deepSeekRepo = deepSeekRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true)
        )

        // 启动 regenerateProfile 在一个子协程中
        val job = launch {
            try {
                coordinator.regenerateProfile("kb1", callbacks)
            } catch (e: CancellationException) {
                // 预期——上层 cancel 传播到底层
            }
        }

        // 等待 provider 进入 suspend
        while (!providerEntered) {
            testScheduler.advanceTimeBy(1)
        }

        // 此时 provider 正在 delay(10000)——cancel 上层 Job
        job.cancel()

        // 等待取消传播完成
        try { job.join() } catch (_: Exception) {}

        // ═══ 验证 ═══

        assertTrue("Provider coroutine should have been entered", providerEntered)
        assertTrue("Provider coroutine should have received cancellation", providerCancelled)
        assertFalse("onProfileSuggestion should NOT be called after cancellation", profileSuggestionCalled)
        assertFalse("reflect_history should NOT be appended after cancellation", reflectHistoryAppended)

        // 验证 generateRawWithMetadata 只被调用了一次（第一次 attempt），
        // 取消后不应该有后续 retry attempt
        coVerify(exactly = 1) { deepSeekRepo.generateRawWithMetadata(any(), any()) }
    }

    @Test
    fun `regenerateProfile cancellation during retry delay does not start next attempt`() = runTest {
        var attemptCount = 0
        var profileSuggestionCalled = false

        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0

        val deepSeekRepo = mockk<DeepSeekRepository>()
        // 第一次 attempt 返回空（触发 EMPTY → retry delay），第二次 attempt 不应被到达
        coEvery { deepSeekRepo.generateRawWithMetadata(any(), any()) } coAnswers {
            attemptCount++
            if (attemptCount == 1) {
                // 返回空响应——触发 retry
                RawGenerationResult(content = "", finishReason = null)
            } else {
                // 不应该走到这里——cancel 应该在 retry delay 期间生效
                RawGenerationResult(content = "{}", finishReason = "stop")
            }
        }

        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        coEvery { promptBuilder.buildReflectSystemPrompt() } returns "system"
        coEvery { promptBuilder.buildReflectUserPrompt(any()) } returns "user"

        val callbacks = mockk<KnowledgeTriggerCoordinator.Callbacks>(relaxed = true)
        coEvery { callbacks.onProfileSuggestion(any()) } answers {
            profileSuggestionCalled = true
            Unit
        }

        val coordinator = KnowledgeTriggerCoordinator(
            knowledgeRepo = knowledgeRepo,
            deepSeekRepo = deepSeekRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true)
        )

        val job = launch {
            try {
                coordinator.regenerateProfile("kb1", callbacks)
            } catch (e: CancellationException) {
                // 预期
            }
        }

        // 等待第一次 attempt 完成（返回空）
        while (attemptCount < 1) {
            testScheduler.advanceTimeBy(1)
        }

        // 现在在 retry delay 中（RETRY_BACKOFF_MS * 1 = 500ms）
        // 在 delay 期间 cancel
        testScheduler.advanceTimeBy(50) // 进入 retry delay
        job.cancel()
        try { job.join() } catch (_: Exception) {}

        // 验证：只有第一次 attempt 被执行，第二次因 cancel 未启动
        assertEquals("Only first attempt should have executed", 1, attemptCount)
        assertFalse("onProfileSuggestion should NOT be called", profileSuggestionCalled)
    }

    @Test
    fun `regenerateProfile cancellation does not convert CancellationException to PROVIDER_ERROR`() = runTest {
        var providerEntered = false
        var providerCancelled = false
        var kbNoticeCalled = false
        var profileSuggestionCalled = false

        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0

        val deepSeekRepo = mockk<DeepSeekRepository>()
        coEvery { deepSeekRepo.generateRawWithMetadata(any(), any()) } coAnswers {
            providerEntered = true
            try {
                kotlinx.coroutines.delay(10000)
                RawGenerationResult(content = "", finishReason = null)
            } catch (e: CancellationException) {
                providerCancelled = true
                throw e // 必须 rethrow——不能被 catch(Exception) 吞掉
            }
        }

        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        coEvery { promptBuilder.buildReflectSystemPrompt() } returns "system"
        coEvery { promptBuilder.buildReflectUserPrompt(any()) } returns "user"

        val callbacks = object : KnowledgeTriggerCoordinator.Callbacks {
            override fun onVectorUpdated(kbName: String, newVector: Map<String, Int>, delta: Map<String, Int>) {}
            override fun onVectorUpdateNotice(kbName: String, summary: String) {}
            override fun onStageSuggestion(suggestion: com.lovebrain.app.model.StageSuggestion) {}
            override fun onKbNotice(notice: String) { kbNoticeCalled = true }
            override fun onProfileSuggestion(suggestion: ProfileSuggestion) { profileSuggestionCalled = true }
            override fun onCurrentVector(kbName: String, vector: Map<String, Int>) {}
        }

        val coordinator = KnowledgeTriggerCoordinator(
            knowledgeRepo = knowledgeRepo,
            deepSeekRepo = deepSeekRepo,
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true)
        )

        val job = launch {
            try {
                coordinator.regenerateProfile("kb1", callbacks)
            } catch (e: CancellationException) {
                // 预期
            }
        }

        while (!providerEntered) {
            testScheduler.advanceTimeBy(1)
        }

        job.cancel()
        try { job.join() } catch (_: Exception) {}

        assertTrue("Provider should have been entered", providerEntered)
        assertTrue("Provider should have received cancellation", providerCancelled)
        // CancellationException 不应被转为 EMPTY/PROVIDER_ERROR → 不应调用 onKbNotice
        assertFalse("onKbNotice should NOT be called (CancellationException was swallowed)", kbNoticeCalled)
        assertFalse("onProfileSuggestion should NOT be called", profileSuggestionCalled)
    }
}
