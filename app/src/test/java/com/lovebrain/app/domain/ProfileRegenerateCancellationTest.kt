package com.lovebrain.app.domain

import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.RawGenerationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画像重新生成链路的取消合同（生产路径，不是替身）。
 *
 * 真实例化 [KnowledgeTriggerCoordinator]，只把 Provider/仓库换成 fake：
 * - generateRawWithMetadata 进入后 suspend（模拟模型请求进行中）
 * - collect 冷流 [KnowledgeTriggerCoordinator.profileRefreshEvents]，随后 cancel 收集方协程
 * - 断言：
 *   - 底层 suspend 真的收到取消
 *   - 后续 retry attempt 不再发生（含 retry delay 期间被取消）
 *   - 不产出 ProfileReady / Notice 事件
 *   - reflect_history 不被写
 *   - CancellationException 没被转成 EMPTY / PROVIDER_ERROR
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRegenerateCancellationTest {

    private class Harness(
        val coordinator: KnowledgeTriggerCoordinator,
        val knowledgeRepo: KnowledgeRepository,
        val deepSeekRepo: DeepSeekRepository
    )

    private fun harness(
        onGenerate: suspend () -> RawGenerationResult
    ): Harness {
        val knowledgeRepo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { knowledgeRepo.getCorrectionsRevision(any()) } returns 0
        val deepSeekRepo = mockk<DeepSeekRepository>()
        coEvery { deepSeekRepo.generateRawWithMetadata(any(), any()) } coAnswers { onGenerate() }
        val promptBuilder = mockk<PromptBuilder>(relaxed = true)
        coEvery { promptBuilder.buildReflectSystemPrompt() } returns "system"
        coEvery { promptBuilder.buildReflectUserPrompt(any()) } returns "user"
        return Harness(
            KnowledgeTriggerCoordinator(
                knowledgeRepo = knowledgeRepo,
                deepSeekRepo = deepSeekRepo,
                promptBuilder = promptBuilder,
                topicRecorder = mockk(relaxed = true)
            ),
            knowledgeRepo,
            deepSeekRepo
        )
    }

    @Test
    fun `cancellation propagates to the provider and emits no event`() = runTest {
        var providerEntered = false
        var providerCancelled = false
        var profileReady = false
        var notice = false

        val h = harness {
            providerEntered = true
            try {
                kotlinx.coroutines.delay(10_000)
                RawGenerationResult(content = "", finishReason = null)
            } catch (e: CancellationException) {
                providerCancelled = true
                throw e
            }
        }
        coEvery {
            h.knowledgeRepo.appendFileWithRevisionCheck(any(), any(), any(), any())
        } coAnswers {
            notice = true // 写历史也算"取消后仍在干活"
            true
        }

        val job = launch {
            try {
                h.coordinator.profileRefreshEvents("kb1").collect { event ->
                    when (event) {
                        is KnowledgeTriggerEvent.ProfileReady -> profileReady = true
                        is KnowledgeTriggerEvent.Notice -> notice = true
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                // 预期——上层 cancel 传播到底层
            }
        }

        while (!providerEntered) testScheduler.advanceTimeBy(1)
        job.cancel()
        try { job.join() } catch (_: Exception) {}

        assertTrue("Provider 协程应真的进入", providerEntered)
        assertTrue("Provider 协程应收到取消", providerCancelled)
        assertFalse("取消后不得产出画像建议", profileReady)
        assertFalse("取消后不得发提示事件", notice)
        coVerify(exactly = 1) { h.deepSeekRepo.generateRawWithMetadata(any(), any()) }
    }

    @Test
    fun `cancellation during the retry backoff does not start the next attempt`() = runTest {
        var attemptCount = 0
        var profileReady = false

        val h = harness {
            attemptCount++
            if (attemptCount == 1) {
                RawGenerationResult(content = "", finishReason = null) // EMPTY → 触发 retry
            } else {
                RawGenerationResult(content = "{}", finishReason = "stop") // 不该走到
            }
        }

        val job = launch {
            try {
                h.coordinator.profileRefreshEvents("kb1").collect { event ->
                    if (event is KnowledgeTriggerEvent.ProfileReady) profileReady = true
                }
            } catch (e: CancellationException) {
                // 预期
            }
        }

        while (attemptCount < 1) testScheduler.advanceTimeBy(1)
        testScheduler.advanceTimeBy(50)   // 进入 retry backoff（500ms × 1）
        job.cancel()
        try { job.join() } catch (_: Exception) {}

        assertEquals("只应执行第一次 attempt", 1, attemptCount)
        assertFalse("取消后不得产出画像建议", profileReady)
    }

    @Test
    fun `cancellation is never converted into a provider error event`() = runTest {
        var providerEntered = false
        var providerCancelled = false
        var notice = false
        var profileReady = false

        val h = harness {
            providerEntered = true
            try {
                kotlinx.coroutines.delay(10_000)
                RawGenerationResult(content = "", finishReason = null)
            } catch (e: CancellationException) {
                providerCancelled = true
                throw e // 必须 rethrow——不能被 catch(Exception) 当成请求失败
            }
        }

        val job = launch {
            try {
                h.coordinator.profileRefreshEvents("kb1").collect { event ->
                    when (event) {
                        is KnowledgeTriggerEvent.Notice -> notice = true
                        is KnowledgeTriggerEvent.ProfileReady -> profileReady = true
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                // 预期
            }
        }

        while (!providerEntered) testScheduler.advanceTimeBy(1)
        job.cancel()
        try { job.join() } catch (_: Exception) {}

        assertTrue(providerEntered)
        assertTrue("取消信号必须原样上抛", providerCancelled)
        assertFalse("取消不得被转成「本次生成失败」提示", notice)
        assertFalse(profileReady)
    }
}
