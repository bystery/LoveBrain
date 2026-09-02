package com.lovebrain.app.viewmodel

import android.util.Log
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.PromptBuilder.ConfigValidationResult
import com.lovebrain.app.domain.PromptBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * （ 回归， ；计 1 条用例）：谈心草稿防抖写盘行为锚定。
 *
 * 行为差异声明：进程被强杀最多丢最后 ≤600ms 输入；正常路径（关悬浮窗经 dispose flush /
 * 清空经先取消防抖）零丢失、无旧值复活。
 *
 * 纯 JVM 构造 LoveBrainViewModel：7 依赖全 mock + Dispatchers.setMain 接管 viewModelScope。
 * 三幕按序在同一调度器上推进：① 防抖合并 → ② dispose 同步 flush → ③ clear 防复活。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoveBrainViewModelDraftPersistTest {

    private lateinit var prefs: SecurePrefs

    @Before
    fun setUp() {
        // L.w 经 android.util.Log——静态 mock 兜底（本测试路径预期不触发）
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

    /** 构造 LoveBrainViewModel：init 读取面显式桩（合法配置，零警告，不触发 L） */
    private fun newViewModel(): LoveBrainViewModel {
        prefs = mockk(relaxed = true)
        every { prefs.thinkingMode } returns 0
        every { prefs.outputMode } returns 0
        every { prefs.panelMode } returns 0
        every { prefs.counselingDraft } returns ""
        every { prefs.loadCounselingResult() } returns null
        every { prefs.loadSuggestion() } returns null
        // ：init 新增今日花费读取面（relaxed 默认返 Object 会 CCE，显式桩 null）
        every { prefs.loadTodayCost() } returns null
        every { prefs.getWorkerTickets() } returns emptyList()
        every { prefs.activeTicketId } returns null
        val promptBuilder = mockk<PromptBuilder>()
        every { promptBuilder.validateConfig(any(), any()) } returns
            ConfigValidationResult(0, 0, emptyList())
        return LoveBrainViewModel(
            deepSeekRepo = mockk(relaxed = true),
            // relaxed：suspend 读取面（getActive 等）默认返回 null，无需显式桩
            knowledgeRepo = mockk(relaxed = true),
            promptBuilder = promptBuilder,
            topicRecorder = mockk(relaxed = true),
            securePrefs = prefs,
            triggerCoordinator = mockk(relaxed = true),
            generationEngine = mockk(relaxed = true)
        )
    }

    @Test
    fun draft_persist_debounce_flush_and_no_resurrection() = runTest {
        val vm = newViewModel()
        advanceUntilIdle() // init 内的 viewModelScope 作业排空

        // ── 幕①：连发 3 次击键 → 防抖合并，末值恰写盘 1 次 ──
        vm.setCounselingDraft("a")
        vm.setCounselingDraft("ab")
        vm.setCounselingDraft("abc")
        advanceTimeBy(599)
        // 防抖窗口未满：一次都不许落盘
        verify(exactly = 0) { prefs.counselingDraft = any() }
        advanceUntilIdle()
        // 窗口满：末值恰写 1 次（中间值 a/ab 被取消，不得写盘）
        verify(exactly = 1) { prefs.counselingDraft = "abc" }
        verify(exactly = 0) { prefs.counselingDraft = "a" }
        verify(exactly = 0) { prefs.counselingDraft = "ab" }

        // ── 幕②：dispose 同步 flush——窗口未满时关闭，最终值立即直写（零丢失） ──
        vm.setCounselingDraft("final")
        advanceTimeBy(300) // 窗口未满，防抖任务仍挂起
        verify(exactly = 0) { prefs.counselingDraft = "final" }
        vm.dispose()
        // 同步直写最终值，不等协程
        verify(exactly = 1) { prefs.counselingDraft = "final" }
        // 防抖尾已被取消：继续推进时间不得再写第二次
        advanceTimeBy(2000)
        advanceUntilIdle()
        verify(exactly = 1) { prefs.counselingDraft = "final" }

        // ── 幕③：防抖窗口未满即清空——旧值复活竞态锚定 ──
        // 击键后 <600ms 窗口内清空：挂起的防抖任务仍持有旧值；
        // clearCounselingAll 必须先取消它再写空，否则窗口走满后旧草稿会被写回。
        val vm2 = newViewModel()
        advanceUntilIdle()
        vm2.setCounselingDraft("old")
        advanceTimeBy(300) // 窗口未满：防抖任务挂起中，持有 "old"
        verify(exactly = 0) { prefs.counselingDraft = "old" }
        vm2.clearCounselingAll()
        // 写空恰 1 次（发生在取消防抖之后）
        verify(exactly = 1) { prefs.counselingDraft = "" }
        // 推进越过原窗口：被取消的旧值任务复活 0 次——删掉
        // clearCounselingAll 里的 draftPersistJob?.cancel() 该断言必红（判别力锚点）
        advanceTimeBy(2000)
        advanceUntilIdle()
        verify(exactly = 0) { prefs.counselingDraft = "old" }
        verify(exactly = 1) { prefs.counselingDraft = "" }
    }
}
