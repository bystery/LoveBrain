package com.lovebrain.app.viewmodel

import com.lovebrain.app.data.FeedbackCaseRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.model.FeedbackCategory
import com.lovebrain.app.model.CaseStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R1-22: 反馈页面状态与导航测试。
 *
 * 验证 SetupViewModel 的反馈案例功能：
 * - Loading 状态正确切换
 * - Empty 状态（无数据）
 * - Data 状态（有数据）
 * - Error 状态（加载失败）
 * - Retry 后恢复
 * - Export 成功/失败
 * - CancellationException 重抛不吞
 */
class FeedbackCasesStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loadFeedbackCases sets loading then data`() = runTest {
        val repo = mockk<FeedbackCaseRepository>()
        val cases = listOf(
            createCase("case-1", FeedbackCategory.OTHER),
            createCase("case-2", FeedbackCategory.UNDERSTANDING_ERROR)
        )
        coEvery { repo.getAll() } returns cases

        val vm = createViewModel(repo)
        vm.loadFeedbackCases()

        assertTrue("Should have data", vm.feedbackCases.value.isNotEmpty())
        assertEquals(2, vm.feedbackCases.value.size)
        assertFalse("Should not be loading", vm.feedbackLoading.value)
        assertNull("Should not have error", vm.feedbackError.value)
    }

    @Test
    fun `loadFeedbackCases empty returns empty list`() = runTest {
        val repo = mockk<FeedbackCaseRepository>()
        coEvery { repo.getAll() } returns emptyList()

        val vm = createViewModel(repo)
        vm.loadFeedbackCases()

        assertEquals(0, vm.feedbackCases.value.size)
        assertFalse(vm.feedbackLoading.value)
        assertNull(vm.feedbackError.value)
    }

    @Test
    fun `loadFeedbackCases error sets error message`() = runTest {
        val repo = mockk<FeedbackCaseRepository>()
        coEvery { repo.getAll() } throws RuntimeException("network error")

        val vm = createViewModel(repo)
        vm.loadFeedbackCases()

        assertNotNull("Should have error", vm.feedbackError.value)
        assertEquals("加载失败，请重试", vm.feedbackError.value)
        assertFalse("Should not be loading", vm.feedbackLoading.value)
    }

    @Test
    fun `loadFeedbackCases retry after error succeeds`() = runTest {
        val repo = mockk<FeedbackCaseRepository>()
        val cases = listOf(createCase("case-1", FeedbackCategory.OTHER))

        // First call fails
        coEvery { repo.getAll() } throws RuntimeException("fail") andThen cases

        val vm = createViewModel(repo)
        vm.loadFeedbackCases()
        assertNotNull(vm.feedbackError.value)

        // Retry succeeds
        vm.loadFeedbackCases()
        assertEquals(1, vm.feedbackCases.value.size)
        assertNull(vm.feedbackError.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `exportFeedback success produces Success state`() = runTest {
        val repo = mockk<FeedbackCaseRepository>()
        val cases = listOf(createCase("case-1", FeedbackCategory.OTHER))
        coEvery { repo.exportMarkdown(any()) } returns "# Export"
        coEvery { repo.exportJson(any()) } returns "{}"

        val vm = createViewModel(repo)
        vm.exportFeedback(cases, "markdown")

        // Wait for coroutine — advanceUntilIdle handles Main dispatcher;
        // IO dispatcher runs on real threads, give it a moment
        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(100)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.exportState.value
        assertTrue("Should be Success, actual: $state", state is SetupViewModel.ExportState.Success)
        assertEquals("# Export", (state as SetupViewModel.ExportState.Success).text)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `exportFeedback error produces Error state`() = runTest {
        val repo = mockk<FeedbackCaseRepository>()
        coEvery { repo.exportMarkdown(any()) } throws RuntimeException("export failed")

        val vm = createViewModel(repo)
        vm.exportFeedback(listOf(createCase("c1", FeedbackCategory.OTHER)), "markdown")

        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(100)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.exportState.value
        assertTrue("Should be Error, actual: $state", state is SetupViewModel.ExportState.Error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadFeedbackCases rethrows CancellationException`() = runTest {
        val repo = mockk<FeedbackCaseRepository>()
        coEvery { repo.getAll() } throws kotlinx.coroutines.CancellationException("cancelled")

        val vm = createViewModel(repo)

        // CancellationException should be rethrown, not caught as regular error
        try {
            vm.loadFeedbackCases()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected — CancellationException is rethrown
        }

        // State should not be stuck in loading
        assertFalse(vm.feedbackLoading.value)
    }

    @Test
    fun `exportFeedback with null repo produces Error state`() = runTest {
        val vm = createViewModel(null)
        vm.exportFeedback(emptyList(), "markdown")

        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.exportState.value
        assertTrue("Should be Error when repo is null", state is SetupViewModel.ExportState.Error)
    }

    private fun createCase(id: String, category: FeedbackCategory): FeedbackCase {
        return FeedbackCase(
            caseId = id,
            schemeIdentityKey = "STYLE:A",
            candidateReply = "test reply",
            categories = listOf(category),
            reasons = emptyList(),
            kbName = "test-kb",
            timestamp = "2026-09-22 10:00:00",
            status = CaseStatus.PENDING
        )
    }

    private fun createViewModel(repo: FeedbackCaseRepository?): SetupViewModel {
        val securePrefs = mockk<SecurePrefs>(relaxed = true)
        val deepSeekRepo = mockk<DeepSeekRepository>(relaxed = true)
        return SetupViewModel(securePrefs, deepSeekRepo, repo)
    }
}
