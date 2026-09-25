package com.lovebrain.app.viewmodel

import android.content.Context
import android.content.res.AssetManager
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * §6.3 知识库页 Error 那一格的**来源**：列表读取失败必须被当成一次读失败记下来。
 *
 * 替换前 `loadState()` 里两处读取（`repo.listAll()` / `repo.getActive()`）没有任何接住异常的
 * 地方，而 `viewModelScope.launch` 也没人 catch——抛出后 `_state` 停在初始值，
 * 页面于是永远落在"第一次数据还没到"那一格（一直转圈）。真实触发点不是假设：
 * `getActive()` 要读 `EncryptedSharedPreferences` 的 `activeKbName`，Keystore 里的值解不开时
 * `getString` 会抛（`SecurePrefs` 只在**构造**时兜了降级路径，逐次读没有兜）。
 *
 * 这一组用例钉的五件事：
 * 1. 抛异常 → `loadFailed = true`，且同时 `loaded = true`（不许留在 Loading）；
 * 2. `listAll` 与 `getActive` 两处读取走同一条失败通道；
 * 3. 首读就失败 vs 曾经读到过：两种失败都不许被说成"一个库都没有"；
 * 4. 重试读成功 → 标记清掉；
 * 5. 取消不是失败（与建库事务同一判据）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KbListLoadFailureTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val kb = KnowledgeBase(name = "kb_a", displayName = "她", active = true)

    private fun repoStub(): KnowledgeRepository = mockk<KnowledgeRepository>(relaxed = true).also {
        coEvery { it.listAll() } returns listOf(kb)
        coEvery { it.getActive() } returns kb
    }

    private fun providerStub(): DeepSeekRepository = mockk<DeepSeekRepository>(relaxed = true)

    private fun contextStub(): Context = mockk<Context>(relaxed = true).also {
        every { it.assets } returns mockk<AssetManager>(relaxed = true)
    }

    private fun newVm(repo: KnowledgeRepository) =
        KnowledgeBaseViewModel(contextStub(), repo, providerStub(), dispatcher)

    /**
     * 让第 [failing] 里的序号那次 `getActive()` 抛，并把每次调用记进 [calls]。
     *
     * 没用 `throws e andThen kb`：`ThrowsAnswerOp` 上还能不能再链 `andThen` 我没在本机验过，
     * 显式计数器比赌 API 形状便宜。计数器同时用来证明"刷新几次就读几次"，
     * 不然重新桩一次就看不出重试到底有没有真的再读一遍。
     */
    private fun failActiveOn(
        repo: KnowledgeRepository,
        failing: Set<Int>,
        calls: IntArray
    ) {
        coEvery { repo.getActive() } answers {
            calls[0]++
            if (calls[0] in failing) throw IOException("keystore key cannot decrypt") else kb
        }
    }

    @Test
    fun `a throwing active-lookup is recorded as a failed load not as a spinner`() = runTest(dispatcher) {
        val repo = repoStub()
        coEvery { repo.getActive() } throws IOException("keystore key cannot decrypt")
        val vm = newVm(repo)

        vm.refresh()
        advanceUntilIdle()

        assertTrue("读失败却没被记下来：页面会一直转圈", vm.state.value.loadFailed)
        // 关键的一对：记失败的同时必须宣布"这一次读完了"，否则判据仍落在 Loading
        assertTrue("loadFailed 必须与 loaded 同真", vm.state.value.loaded)
    }

    @Test
    fun `a throwing list read is recorded the same way`() = runTest(dispatcher) {
        val repo = repoStub()
        coEvery { repo.listAll() } throws IOException("catalog root gone")
        val vm = newVm(repo)

        vm.refresh()
        advanceUntilIdle()

        assertTrue("listAll 抛出也要走同一条失败通道", vm.state.value.loadFailed)
        assertTrue(vm.state.value.loaded)
    }

    /** 第一次读就抛：半路读到的那份列表不留下（Error 那一格本来也不画列表），但必须标成失败 */
    @Test
    fun `a first read that fails leaves an empty list but flags it as failed`() = runTest(dispatcher) {
        val repo = repoStub()
        val calls = intArrayOf(0)
        failActiveOn(repo, failing = setOf(1), calls = calls)
        val vm = newVm(repo)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(0, vm.state.value.knowledgeBases.size)
        assertTrue(
            "空列表 + 没标记 = 页面会说「一个库都没有」，而真相是读不动",
            vm.state.value.loadFailed
        )
        assertEquals(1, calls[0])
    }

    /** 曾经读到过：残留列表留着，但整份快照被标成失败，UI 因此不会把它当新数据画出来 */
    @Test
    fun `the stale list is kept but flagged so the ui cannot draw it as fresh`() = runTest(dispatcher) {
        val repo = repoStub()
        val calls = intArrayOf(0)
        failActiveOn(repo, failing = setOf(2), calls = calls)
        val vm = newVm(repo)

        vm.refresh()
        advanceUntilIdle()
        assertFalse("起点要是一次成功的读取", vm.state.value.loadFailed)
        assertEquals(1, vm.state.value.knowledgeBases.size)

        vm.refresh()
        advanceUntilIdle()

        assertEquals("上一次读到的列表要被留着（失败时页面不该整页空掉）", 1, vm.state.value.knowledgeBases.size)
        assertTrue(
            "但整份快照必须标成读失败——四格判据据此把 Error 排在 Content 之前",
            vm.state.value.loadFailed
        )
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `a retry that succeeds clears the failure`() = runTest(dispatcher) {
        val repo = repoStub()
        val calls = intArrayOf(0)
        failActiveOn(repo, failing = setOf(1), calls = calls)
        val vm = newVm(repo)

        vm.refresh()
        advanceUntilIdle()
        assertTrue("先要真的进入失败态", vm.state.value.loadFailed)

        vm.refresh()
        advanceUntilIdle()

        assertFalse("重试读成功后失败标记没清：错误格会赖在页面上不走", vm.state.value.loadFailed)
        assertTrue(vm.state.value.loaded)
        assertEquals(listOf(kb), vm.state.value.knowledgeBases)
        assertEquals("kb_a", vm.state.value.activeName)
        assertEquals("重试必须真的又读了一次", 2, calls[0])
    }

    @Test
    fun `cancelling the read is not reported as a failure`() = runTest(dispatcher) {
        val repo = repoStub()
        coEvery { repo.getActive() } throws CancellationException("page destroyed")
        val vm = newVm(repo)

        vm.refresh()
        advanceUntilIdle()

        assertFalse("取消被算成了读失败——那会给用户一个根本没有错的错误页", vm.state.value.loadFailed)
        assertFalse("取消之后也不该宣布读完了", vm.state.value.loaded)
    }
}
